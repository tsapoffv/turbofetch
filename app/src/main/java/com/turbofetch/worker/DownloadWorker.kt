package com.turbofetch.worker

import android.content.Context
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
import com.turbofetch.domain.model.FileNameTemplate
import com.turbofetch.domain.model.TaskStatus
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.collect
import java.io.File

/**
 * Качает одну задачу в фоне.
 *
 * На Android 14+ работает как foreground-сервис типа `dataSync` — иначе система
 * убьёт процесс при сворачивании приложения. Лимит `dataSync` — 6 часов в сутки,
 * по его истечении приходит `onTimeout()`, который мы перехватываем.
 */
class DownloadWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    private val container get() = (applicationContext as TurboFetchApp).container

    override suspend fun doWork(): Result {
        val taskId = inputData.getString(KEY_TASK_ID) ?: return Result.failure()
        val repository: TaskRepository = container.taskRepository
        val engine: DownloadEngine = container.downloadEngine

        val task = repository.get(taskId) ?: return Result.failure()

        com.turbofetch.core.Log.i("Воркер стартовал для задачи $taskId: ${task.url}")
        setForeground(createForegroundInfo(task.displayTitle))
        com.turbofetch.core.Log.i("Foreground-уведомление выставлено")

        // 1. Разбор ссылки, если метаданных ещё нет.
        var current = task
        if (current.availableFormats.isEmpty()) {
            repository.updateStatus(taskId, TaskStatus.PROBING)
            val probe = engine.probe(current.url)
            val info = probe.getOrElse { error ->
                repository.updateStatus(taskId, TaskStatus.FAILED, error.message)
                return Result.failure()
            }
            current = current.copy(
                title = info.title,
                author = info.author,
                thumbnailUrl = info.thumbnailUrl,
                durationSeconds = info.durationSeconds,
                availableFormats = info.formats,
                selectedFormat = current.selectedFormat ?: info.formats.firstOrNull(),
            )
            repository.upsert(current)
        }

        val format = current.selectedFormat ?: current.availableFormats.firstOrNull()
        if (format == null) {
            repository.updateStatus(taskId, TaskStatus.FAILED, "Нет доступных форматов")
            return Result.failure()
        }
        com.turbofetch.core.Log.i(
            "Формат: ${format.label}, url=${format.url?.take(60)}, заголовки=${format.headers.keys}",
        )

        // 2. Скачивание во временный файл.
        val extension = extensionFor(current.audioOnly, format.mimeType)
        val fileName = FileNameTemplate.render(
            template = container.settingsRepository.current().fileNameTemplate,
            title = current.title,
            author = current.author,
            quality = if (current.audioOnly) "audio" else format.label,
            extension = extension,
            id = current.id,
        )
        val tempFile = File(applicationContext.cacheDir, "downloads/$taskId.$extension")
        tempFile.parentFile?.mkdirs()

        repository.updateStatus(taskId, TaskStatus.RUNNING)

        var failureMessage: String? = null
        try {
            engine.download(
                DownloadRequest(
                    url = current.url,
                    format = format,
                    audioOnly = current.audioOnly,
                    outputFile = tempFile,
                ),
            ).collect { progress ->
                when (progress) {
                    is DownloadProgress.Preparing -> Unit

                    is DownloadProgress.Running ->
                        repository.updateProgress(taskId, progress.percent).also {
                            if (progress.bytesRead % (256 * 1024) < 64 * 1024) {
                                com.turbofetch.core.Log.i(
                                    "Прогресс ${progress.bytesRead}/${progress.totalBytes}",
                                )
                            }
                        }

                    is DownloadProgress.Finished -> {
                        val uri = container.mediaStoreWriter.save(
                            source = progress.file,
                            displayName = fileName,
                            mimeType = mimeTypeFor(current.audioOnly, format.mimeType),
                            isAudio = current.audioOnly,
                        )
                        repository.updateOutput(taskId, uri)
                        tempFile.delete()
                    }

                    is DownloadProgress.Failed -> failureMessage = progress.message
                }
            }
        } catch (cancellation: CancellationException) {
            // Отмена или срабатывание лимита foreground-сервиса.
            repository.updateStatus(taskId, TaskStatus.CANCELLED, "Загрузка прервана")
            throw cancellation
        }

        failureMessage?.let { message ->
            repository.updateStatus(taskId, TaskStatus.FAILED, message)
            return Result.failure()
        }

        return Result.success(workDataOf(KEY_OUTPUT_URI to repository.get(taskId)?.outputUri))
    }

    private fun createForegroundInfo(title: String): ForegroundInfo {
        val notification = NotificationCompat.Builder(applicationContext, TurboFetchApp.CHANNEL_DOWNLOADS)
            .setContentTitle("Скачивание")
            .setContentText(title)
            .setSmallIcon(R.drawable.ic_stat_download)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setProgress(0, 0, true)
            .build()

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            ForegroundInfo(NOTIFICATION_ID, notification)
        }
    }

    private fun extensionFor(audioOnly: Boolean, mimeType: String): String = when {
        audioOnly -> "mp3"
        mimeType.contains("webm") -> "webm"
        mimeType.contains("mp4") -> "mp4"
        else -> "mp4"
    }

    private fun mimeTypeFor(audioOnly: Boolean, mimeType: String): String = when {
        audioOnly -> "audio/mpeg"
        mimeType.isNotBlank() -> mimeType
        else -> "video/mp4"
    }

    companion object {
        const val KEY_TASK_ID = "task_id"
        const val KEY_OUTPUT_URI = "output_uri"
        private const val NOTIFICATION_ID = 4711
    }
}
