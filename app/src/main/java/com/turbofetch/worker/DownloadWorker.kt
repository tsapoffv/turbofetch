package com.turbofetch.worker

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.turbofetch.R
import com.turbofetch.TurboFetchApp
import com.turbofetch.data.repo.TaskRepository
import com.turbofetch.domain.engine.DownloadEngine
import com.turbofetch.domain.engine.DownloadProgress
import com.turbofetch.domain.engine.DownloadRequest
import com.turbofetch.domain.model.DownloadedFile
import com.turbofetch.domain.model.FileNameTemplate
import com.turbofetch.domain.model.FormatKind
import com.turbofetch.domain.model.MediaFormat
import com.turbofetch.domain.model.MediaNaming
import com.turbofetch.domain.model.TaskStatus
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Качает одну задачу в фоне.
 *
 * На Android 14+ работает как foreground-сервис типа `dataSync` — иначе система
 * убьёт процесс при сворачивании приложения. Лимит `dataSync` — 6 часов в сутки,
 * по его истечении приходит `onTimeout()`, который мы перехватываем.
 *
 * Одновременно работают не больше [MAX_PARALLEL_DOWNLOADS] загрузок: мобильный
 * интернет от этого не задыхается, а пользователь не видит десяток уведомлений.
 */
class DownloadWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    private val container get() = (applicationContext as TurboFetchApp).container

    override suspend fun doWork(): Result {
        val taskId = inputData.getString(KEY_TASK_ID) ?: return Result.failure()
        return try {
            runTask(taskId)
        } catch (cancellation: CancellationException) {
            // Система остановила воркер: сработал лимит foreground-сервиса
            // типа dataSync, снесли приложение или отменили работу. Задача не
            // должна остаться в статусе RUNNING — пользователь видел бы
            // вечную загрузку, которую нельзя ни отменить, ни повторить.
            //
            // Отдельный onTimeout()/onStopped() переопределить нельзя:
            // у CoroutineWorker onStopped объявлен final, а onTimeout в
            // WorkManager 2.12 не существует. Поэтому ловим отмену здесь.
            runCatching {
                container.taskRepository.updateStatus(
                    taskId,
                    TaskStatus.CANCELLED,
                    "Загрузка прервана",
                )
            }
            throw cancellation
        }
    }

    private suspend fun runTask(taskId: String): Result {
        val repository: TaskRepository = container.taskRepository
        val engine: DownloadEngine = container.downloadEngine

        val task = repository.get(taskId) ?: return Result.failure()

        com.turbofetch.core.Log.i("Воркер стартовал для задачи $taskId: ${task.url}")
        setForeground(createForegroundInfo(taskId, task.displayTitle))
        com.turbofetch.core.Log.i("Foreground-уведомление выставлено")

        // 1. Разбор ссылки, если метаданных нет или прямая ссылка протухла.
        var current = task
        val needsProbe = current.selectedFormat?.url.isNullOrBlank() || current.needsRefresh
        if (needsProbe) {
            repository.updateStatus(taskId, TaskStatus.PROBING)
            com.turbofetch.core.Log.i("Разбираю ссылку заново: ${current.url}")
            val probe = engine.probe(current.url)
            val info = probe.getOrElse { error ->
                repository.updateStatus(taskId, TaskStatus.FAILED, error.message)
                return Result.failure()
            }
            // Сохраняем выбор юзера: новый разбор даёт свежие ссылки с теми же itag.
            val previouslyChosen = current.selectedFormat
            val previouslyChosenAudio = current.audioFormat
            val refreshed = info.formats.firstOrNull { it.id == previouslyChosen?.id }
                ?: info.formats.firstOrNull { it.label == previouslyChosen?.label }
                ?: current.selectedFormat
                ?: info.formats.firstOrNull()

            val refreshedAudio = previouslyChosenAudio?.let { old ->
                info.formats.firstOrNull { it.id == old.id }
            }

            // Если площадка отдала другой поток, недокачанный файл относится к
            // старому: докачка дописала бы его байты к новому потоку, и файл
            // получился бы битым. Поэтому при смене потока недокачанное
            // удаляем и начинаем этот файл с нуля.
            dropStalePartials(
                taskId = taskId,
                videoChanged = previouslyChosen?.id != null &&
                    refreshed?.id != null &&
                    previouslyChosen.id != refreshed.id,
                audioChanged = previouslyChosenAudio?.id != null &&
                    refreshedAudio?.id != null &&
                    previouslyChosenAudio.id != refreshedAudio.id,
            )

            current = current.copy(
                title = info.title,
                author = info.author,
                thumbnailUrl = info.thumbnailUrl,
                durationSeconds = info.durationSeconds,
                availableFormats = info.formats,
                selectedFormat = refreshed,
                audioFormat = refreshedAudio,
                needsRefresh = false,
            )
            repository.upsert(current)
            com.turbofetch.core.Log.i("Ссылка разобрана заново, форматов ${info.formats.size}")
        }

        val videoFormat = current.selectedFormat ?: current.availableFormats.firstOrNull()
        if (videoFormat == null) {
            repository.updateStatus(taskId, TaskStatus.FAILED, "Нет доступных форматов")
            return Result.failure()
        }

        val wantsAudioOnly = current.audioOnly

        // Кгда просят только аудио, качать надо аудиопоток, а не видео:
        // либо выбранный формат сам аудио, либо парный аудиопоток.
        // Если аудиопотока нет — звук вытащим из видео через ffmpeg.
        val audioOnlyFormat: MediaFormat? = if (wantsAudioOnly) {
            when {
                videoFormat.hasAudio && !videoFormat.hasVideo -> videoFormat
                current.audioFormat != null -> current.audioFormat
                else -> null
            }
        } else {
            null
        }

        // Видео без звука (DASH) требует отдельной аудиодорожки — их потом склеим.
        val needsSeparateAudio = !wantsAudioOnly && !videoFormat.hasAudio
        val audioFormat: MediaFormat? = if (needsSeparateAudio) {
            current.audioFormat
                ?: current.availableFormats.firstOrNull { it.hasAudio && !it.hasVideo }
        } else {
            null
        }

        // Что качаем первым файлом.
        val primaryFormat = audioOnlyFormat ?: videoFormat

        // Контейнер выбирается по правилам MediaNaming: площадка, которая
        // сама отдаёт звук (SoundCloud), сохраняется как есть — m4a;
        // mp3 появляется только когда юзер явно попросил только аудио;
        // GIF — когда выбран формат с пометкой TO_GIF.
        //
        // Считаем его здесь, до имени: от контейнера зависит и расширение,
        // и токен качества («GIF» вместо «GIF (анимация)»).
        val outputContainer = MediaNaming.container(
            formats = current.availableFormats,
            wantMp3 = wantsAudioOnly,
            selected = videoFormat,
        )

        // Токен качества для имени файла.
        //
        // У площадок, которые отдают только звук (SoundCloud), в имени
        // полезнее битрейт, а не слово «audio»: «Flickermood_160 кбит_с.mp3»
        // читается лучше, чем «Flickermood_audio.mp3». Для YouTube, где звук
        // вытаскивается из видеопотока, слово «audio» остаётся — там своего
        // аудиопотока с подписью нет.
        val qualityToken = when {
            // Гифка: в имени достаточно слова «GIF», а не «GIF (анимация)».
            outputContainer == MediaNaming.Container.GIF -> "GIF"
            MediaNaming.isAudioOnlySource(current.availableFormats) ->
                // У YouTube подпись аудиопотока начинается со слова «аудио» —
                // в имени оно лишнее: «Трек_130 кбит_с.m4a» читается лучше.
                qualityLabel(primaryFormat).removePrefix("аудио ")
            wantsAudioOnly -> "audio"
            else -> qualityLabel(videoFormat)
        }

        com.turbofetch.core.Log.i(
            when {
                wantsAudioOnly && audioOnlyFormat != null ->
                    "Только аудио: ${audioOnlyFormat.label}"
                wantsAudioOnly -> "Только аудио: звук вытащим из ${videoFormat.label}"
                else -> "Видео: ${videoFormat.label}" + when {
                    audioFormat != null -> " + аудио: ${audioFormat.label}"
                    // Instagram сам вырезает музыку из части роликов —
                    // говорим об этом честно, а не «со звуком».
                    !videoFormat.hasAudio -> " (без звука)"
                    else -> " (со звуком)"
                }
            } + ", заголовки=${primaryFormat.headers.keys}",
        )

        // 2. Имена файлов.
        val extension = when {
            // Гифка: площадка отдала mp4-конверт, а сохраняем мы GIF.
            outputContainer == MediaNaming.Container.GIF -> "gif"
            needsSeparateAudio -> "mp4"
            else -> MediaNaming.extension(outputContainer)
        }
        // Своё имя из шторки редактирования важнее шаблона: пользователь ввёл
        // его руками. Если его нет — собираем имя по шаблону настроек.
        val fileName = current.customName?.takeIf { it.isNotBlank() }
            ?.let { custom ->
                val safe = FileNameTemplate.sanitize(custom)
                if (safe.isBlank()) FileNameTemplate.render(
                    template = container.settingsRepository.current().fileNameTemplate,
                    title = current.title,
                    author = current.author,
                    quality = qualityToken,
                    extension = extension,
                    id = current.id,
                ) else if (safe.endsWith(".$extension", ignoreCase = true)) {
                    safe
                } else {
                    "$safe.$extension"
                }
            }
            ?: FileNameTemplate.render(
                template = container.settingsRepository.current().fileNameTemplate,
                title = current.title,
                author = current.author,
                quality = qualityToken,
                extension = extension,
                id = current.id,
            )

        val workDir = File(applicationContext.cacheDir, "downloads").apply { mkdirs() }
        // Промежуточный файл всегда с расширением ИСХОДНОГО потока, а не
        // итогового контейнера: иначе при конвертации в mp3 вход и выход
        // ffmpeg совпадут («Output same as Input»).
        val intermediateExtension = when {
            primaryFormat.kind == FormatKind.HLS_PLAYLIST -> "m4a"
            primaryFormat.mimeType.contains("webm") -> "webm"
            else -> "mp4"
        }
        val videoFile = File(workDir, "$taskId.$intermediateExtension")
        val audioFile = audioFormat?.let { File(workDir, "$taskId.audio") }

        repository.updateStatus(taskId, TaskStatus.RUNNING)

        var failure: DownloadProgress.Failed? = null

        try {
            failure = performDownloads(
                engine = engine,
                url = current.url,
                videoFormat = primaryFormat,
                videoFile = videoFile,
                audioFormat = audioFormat,
                audioFile = audioFile,
                onProgress = { repository.updateProgress(taskId, it) },
            )
        } catch (cancellation: CancellationException) {
            // Отмена долетает сюда только если случилась до обёртки doWork;
            // обычный путь перехватывается в doWork.
            repository.updateStatus(taskId, TaskStatus.CANCELLED, "Загрузка прервана")
            throw cancellation
        }

        failure?.let { error ->
            return if (error.retryable) {
                com.turbofetch.core.Log.w("Повтор после ошибки: ${error.message}")
                // Прямая ссылка подписана и протухает — перед повтором разберём заново.
                // Частично скачанные файлы сохраняем: докачка продолжит их с места обрыва.
                repository.markNeedsRefresh(taskId, error.message)
                Result.retry()
            } else {
                repository.updateStatus(taskId, TaskStatus.FAILED, error.message)
                // Повторять не будем — недокачанный файл больше никому
                // не нужен, и кэш приложения не должен расти от брошенных загрузок.
                deleteTaskFiles(taskId)
                Result.failure()
            }
        }

        // 3. Склейка видео и звука.
        //
        // Склейка и перекодирование — тяжёлая работа: они читают и пишут
        // файлы и запускают ffmpeg, который занимает целое ядро. Поэтому
        // они идут под отдельным семафором и на IO-потоке: раньше десяток
        // задач мог одновременно запустить десяток ffmpeg, и главный поток
        // интерфейса оставался без квантов.
        parallelPostProcessing.acquire()
        val mergedFile: File
        val finalFile: File
        try {
            mergedFile = if (audioFile != null) {
                val merged = File(workDir, "$taskId.merged.mp4")
                try {
                    withContext(Dispatchers.IO) {
                        container.mediaMuxerMerger.merge(videoFile, audioFile, merged)
                    }
                } catch (error: Exception) {
                    com.turbofetch.core.Log.e("Не удалось склеить", error)
                    repository.updateStatus(
                        taskId,
                        TaskStatus.FAILED,
                        "Не удалось склеить видео со звуком: ${error.message}",
                    )
                    return Result.failure()
                }
                merged
            } else {
                videoFile
            }

            // 4. Если просили только аудио — перекодируем в mp3.
            //
            // Для источника, который сам отдаёт звук (SoundCloud), файл уже
            // готов — m4a кладём как есть, без потери качества.
            finalFile = if (outputContainer == MediaNaming.Container.GIF) {
                // Гифка из X: площадка отдала mp4-конверт, а юзеру нужна
                // настоящая анимация — перекодируем через ffmpeg.
                if (!container.ffmpegRunner.isAvailable) {
                    repository.updateStatus(
                        taskId,
                        TaskStatus.FAILED,
                        "GIF на этом устройстве недоступен: ffmpeg собран только под " +
                            "arm64. Оставьте MP4 без звука — он играется везде.",
                    )
                    return Result.failure()
                }
                val gif = File(workDir, "$taskId.gif")
                val conversion = container.ffmpegRunner.convertToGif(mergedFile, gif)
                if (conversion.isFailure) {
                    val reason = conversion.exceptionOrNull()?.message ?: "неизвестная ошибка"
                    repository.updateStatus(taskId, TaskStatus.FAILED, "Не удалось сделать GIF: $reason")
                    return Result.failure()
                }
                gif
            } else if (outputContainer == MediaNaming.Container.MP3) {
                if (!container.ffmpegRunner.isAvailable) {
                    repository.updateStatus(
                        taskId,
                        TaskStatus.FAILED,
                        "mp3 на этом устройстве недоступен: ffmpeg собран только под " +
                            "arm64. Оставьте M4A — он играется везде.",
                    )
                    return Result.failure()
                }
                val mp3 = File(workDir, "$taskId.mp3")
                val conversion = container.ffmpegRunner.convertToMp3(mergedFile, mp3)
                if (conversion.isFailure) {
                    val reason = conversion.exceptionOrNull()?.message ?: "неизвестная ошибка"
                    repository.updateStatus(taskId, TaskStatus.FAILED, "Не удалось сделать mp3: $reason")
                    return Result.failure()
                }
                mp3
            } else {
                mergedFile
            }

        } finally {
            // Слот пост-обработки освобождается даже при ошибке: иначе
            // после нескольких неудач семафор «залипнет» и новые задачи
            // не смогут скачаться.
            parallelPostProcessing.release()
        }

        // 5. Готовый файл — в галерею.
        val mimeType = MediaNaming.mimeType(outputContainer, videoFormat.mimeType)
        val uri = container.mediaStoreWriter.save(
            source = finalFile,
            displayName = fileName,
            mimeType = mimeType,
            isAudio = MediaNaming.isAudio(outputContainer),
            isImage = MediaNaming.isImage(outputContainer),
        )
        repository.updateOutput(taskId, uri)

        // Готовый файл попадает в историю скачанного — к нему можно
        // вернуться позже, открыть или удалить.
        container.fileRepository.upsert(
            DownloadedFile(
                id = taskId,
                taskId = taskId,
                title = current.title ?: current.url,
                fileName = fileName,
                uri = uri,
                mimeType = mimeType,
                sizeBytes = finalFile.length(),
                source = current.source,
                thumbnailUrl = current.thumbnailUrl,
                createdAt = System.currentTimeMillis(),
                // Ссылка на источник: задача сейчас же удалится из очереди,
                // а в истории файл должен остаться кликабельным.
                sourceUrl = current.url,
            ),
        )

        // Задача свою работу сделала: убираем её из очереди. Очередь —
        // только для тех, что ещё предстоит скачать; всё скачанное живёт
        // во вкладке «Скачанное».
        repository.delete(taskId)

        videoFile.delete()
        audioFile?.delete()
        mergedFile.takeIf { it != videoFile }?.delete()
        finalFile.takeIf { it != mergedFile }?.delete()

        // 5. Открыть файл, если так просил юзер.
        if (container.settingsRepository.current().openAfterDownload) {
            openWhenReady(taskId, uri, mimeType, fileName)
        }

        return Result.success(workDataOf(KEY_OUTPUT_URI to uri))
    }

    /**
     * Скачивает видео и, если нужно, отдельный аудиофайл.
     * Возвращает ошибку или `null`, если всё прошло.
     */
    private suspend fun performDownloads(
        engine: DownloadEngine,
        url: String,
        videoFormat: MediaFormat,
        videoFile: File,
        audioFormat: MediaFormat?,
        audioFile: File?,
        onProgress: suspend (Float) -> Unit,
    ): DownloadProgress.Failed? {
        // withPermit принимает не-suspend лямбду, поэтому слот берём вручную.
        parallelDownloads.acquire()
        try {
            val videoOutcome = downloadFile(
                engine = engine,
                url = url,
                format = videoFormat,
                outputFile = videoFile,
                from = 0f,
                to = if (audioFormat != null) 0.9f else 1f,
                onProgress = onProgress,
            )
            if (videoOutcome != null) return videoOutcome

            if (audioFormat != null && audioFile != null) {
                return downloadFile(
                    engine = engine,
                    url = url,
                    format = audioFormat,
                    outputFile = audioFile,
                    from = 0.9f,
                    to = 1f,
                    onProgress = onProgress,
                )
            }
            return null
        } finally {
            // Освобождаем слот даже при отмене: иначе лимит параллельных
            // загрузок со временем «залипнет».
            withContext(NonCancellable) { parallelDownloads.release() }
        }
    }

    /**
     * Скачивает один файл. Возвращает ошибку или `null`, если всё прошло.
     * Прогресс пересчитывается в диапазон [from, to], чтобы два файла
     * (видео и звук) давали один общий процент.
     */
    private suspend fun downloadFile(
        engine: DownloadEngine,
        url: String,
        format: MediaFormat,
        outputFile: File,
        from: Float,
        to: Float,
        onProgress: suspend (Float) -> Unit,
    ): DownloadProgress.Failed? {
        var failure: DownloadProgress.Failed? = null
        engine.download(
            DownloadRequest(
                url = url,
                format = format,
                audioOnly = false,
                outputFile = outputFile,
            ),
        ).collect { progress ->
            when (progress) {
                is DownloadProgress.Preparing -> Unit
                is DownloadProgress.Running -> onProgress(from + (to - from) * progress.percent)
                is DownloadProgress.Finished -> Unit
                is DownloadProgress.Failed -> failure = progress
            }
        }
        return failure
    }

    /**
     * Открывает готовый файл системным плеером.
     *
     * Если приложение сейчас на экране — запускаем плеер сразу. Если в фоне,
     * Android всё равно не даст открыть activity из фонового сервиса, поэтому
     * показываем уведомление с кнопкой «Открыть».
     */
    private fun openWhenReady(taskId: String, uri: String, mimeType: String, fileName: String) {
        val app = applicationContext as TurboFetchApp
        val intent = Intent(Intent.ACTION_VIEW)
            .setDataAndType(android.net.Uri.parse(uri), mimeType)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)

        if (app.isForeground) {
            runCatching { app.startActivity(intent) }
                .onFailure { postReadyNotification(taskId, uri, mimeType, fileName) }
        } else {
            postReadyNotification(taskId, uri, mimeType, fileName)
        }
    }

    private fun postReadyNotification(taskId: String, uri: String, mimeType: String, fileName: String) {
        val intent = Intent(Intent.ACTION_VIEW)
            .setDataAndType(android.net.Uri.parse(uri), mimeType)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        val pendingIntent = PendingIntent.getActivity(
            applicationContext,
            uri.hashCode(),
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        val notification = NotificationCompat.Builder(applicationContext, TurboFetchApp.CHANNEL_DOWNLOADS)
            .setContentTitle("Файл готов")
            .setContentText(fileName)
            .setSmallIcon(R.drawable.ic_stat_download)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        runCatching {
            androidx.core.app.NotificationManagerCompat.from(applicationContext)
                .notify(readyNotificationId(taskId), notification)
        }
    }

    /**
     * Уведомление о загрузке.
     *
     * ID — производный от задачи: у всех воркеров был один и тот же ID
     * 4711, поэтому десяток одновременных загрузок затирали уведомления
     * друг друга, а завершение одной снимало уведомление у остальных.
     */
    private fun createForegroundInfo(taskId: String, title: String): ForegroundInfo {
        val notification = NotificationCompat.Builder(applicationContext, TurboFetchApp.CHANNEL_DOWNLOADS)
            .setContentTitle("Скачивание")
            .setContentText(title)
            .setSmallIcon(R.drawable.ic_stat_download)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setProgress(0, 0, true)
            .build()

        val id = notificationId(taskId)
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(id, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            ForegroundInfo(id, notification)
        }
    }

    /**
     * Короткое имя качества для файла: «720p», а не «720p · лучшее».
     */
    private fun qualityLabel(format: MediaFormat): String =
        if (format.height > 0) "${format.height}p" else format.label

    /**
     * Удаляет все промежуточные файлы задачи.
     *
     * Их названия — «$taskId.mp4», «$taskId.m4a», «$taskId.audio» и
     * так далее, поэтому файлы ищутся по префиксу.
     */
    private fun deleteTaskFiles(taskId: String) {
        val downloads = File(applicationContext.cacheDir, "downloads")
        val files = downloads.listFiles { file -> file.name.startsWith("$taskId.") }
            ?: return
        for (file in files) {
            if (file.delete()) {
                com.turbofetch.core.Log.i("Удалил промежуточный ${file.name}")
            }
        }
    }

    /**
     * Удаляет недокачанные файлы, если после повторного разбора ссылки
     * площадка отдала другой поток. Иначе байты старого потока допишутся к
     * новому и получится битый файл.
     */
    private fun dropStalePartials(taskId: String, videoChanged: Boolean, audioChanged: Boolean) {
        if (!videoChanged && !audioChanged) return
        // Удаляем всё, что принадлежит задаче: промежуточные файлы
        // называются «$taskId.mp4», «$taskId.m4a», «$taskId.audio» и так
        // далее. Раньше здесь искался файл «$taskId.video», которого не
        // бывает, — недокачанный поток оставался на диске, и при следующей
        // попытке докачка дописывала байты старого потока к новому.
        val downloads = File(applicationContext.cacheDir, "downloads")
        val stale = downloads.listFiles { file -> file.name.startsWith("$taskId.") }
            ?: return
        for (file in stale) {
            if (file.delete()) {
                com.turbofetch.core.Log.w("Поток сменился, удалил недокачанный ${file.name}")
            }
        }
    }

    companion object {
        const val KEY_TASK_ID = "task_id"
        const val KEY_OUTPUT_URI = "output_uri"
        /** База ID уведомлений: дальше добавляется хэш задачи. */
        private const val NOTIFICATION_ID_BASE = 4711
        private const val READY_NOTIFICATION_ID_BASE = 5711

        /** Сколько загрузок может идти одновременно. */
        const val MAX_PARALLEL_DOWNLOADS = 3

        /**
         * Сколько задач одновременно могут проходить пост-обработку:
         * склейку видео со звуком, перекодирование в mp3 или GIF.
         *
         * Это работа на полную мощность процессора: ffmpeg занимает целое
         * ядро и сотни мегабайт памяти. Без ограничения десяток задач,
         * дошедших до конца одновременно, запускали десяток ffmpeg — и
         * интерфейс залипал, потому что главному потоку не доставалось
         * квантов.
         */
        const val MAX_PARALLEL_POST_PROCESSING = 2

        /**
         * Семафор общий для всех воркеров в процессе — иначе каждый экземпляр
         * ограничил бы себя отдельно и лимит не работал бы.
         */
        private val parallelDownloads = Semaphore(MAX_PARALLEL_DOWNLOADS)

        /** Семафор пост-обработки — тоже общий на процесс. */
        private val parallelPostProcessing = Semaphore(MAX_PARALLEL_POST_PROCESSING)

        /** Свой ID уведомления на каждую задачу. */
        private fun notificationId(taskId: String): Int =
            NOTIFICATION_ID_BASE + (taskId.hashCode() and 0x0FFF)

        private fun readyNotificationId(taskId: String): Int =
            READY_NOTIFICATION_ID_BASE + (taskId.hashCode() and 0x0FFF)
    }
}
