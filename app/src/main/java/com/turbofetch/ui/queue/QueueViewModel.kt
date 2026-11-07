package com.turbofetch.ui.queue

import android.app.Application
import android.content.Intent
import android.net.Uri
import androidx.core.net.toUri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.turbofetch.TurboFetchApp
import com.turbofetch.domain.link.LinkParser
import com.turbofetch.domain.model.AppSettings
import com.turbofetch.data.resolver.spotify.SpotifyMetadata
import com.turbofetch.domain.model.CollectionTrack
import com.turbofetch.domain.model.DownloadTask
import com.turbofetch.domain.model.MediaFormat
import com.turbofetch.domain.model.MediaNaming
import com.turbofetch.domain.model.QualityPreset
import com.turbofetch.domain.model.Source
import com.turbofetch.domain.model.TaskStatus
import com.turbofetch.worker.DownloadScheduler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

class QueueViewModel(application: Application) : AndroidViewModel(application) {

    private val container = (application as TurboFetchApp).container
    private val repository = container.taskRepository
    private val settingsRepository = container.settingsRepository
    private val engine = container.downloadEngine
    private val scheduler = DownloadScheduler(container.workManager)

    /** Scope приложения: работа переживает закрытие activity. */
    private val appScope = (application as TurboFetchApp).appScope

    val tasks: StateFlow<List<DownloadTask>> = repository.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val settings = settingsRepository.observe()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AppSettings())

    /** Задачи, для которых сейчас идёт догрузка обложек. */
    private val loadingCovers = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()

    private val _messages = MutableStateFlow<String?>(null)
    val messages: StateFlow<String?> = _messages.asStateFlow()

    /** Принимает ссылку из «Поделиться» или из поля ввода. */
    fun addLink(raw: String) {
        viewModelScope.launch {
            addLinkInternal(raw)
        }
    }

    /**
     * То же, но работа не зависит от activity.
     *
     * Нужно для режима «закрываться сразу»: окно приложения закрывается,
     * а ссылка всё равно должна попасть в очередь и разобраться. Обычный
     * [viewModelScope] при этом отменяется вместе с activity.
     */
    fun addLinkDetached(raw: String) {
        appScope.launch {
            addLinkInternal(raw)
        }
    }

    private suspend fun addLinkInternal(raw: String) {
        val parsed = LinkParser.parse(raw)
        if (parsed == null) {
            _messages.value = "Не удалось распознать ссылку"
            return
        }

        val current = settingsRepository.current()
        val task = DownloadTask(
            id = UUID.randomUUID().toString(),
            url = parsed.url,
            source = parsed.source,
            createdAt = System.currentTimeMillis(),
            audioOnly = current.defaultAudioOnly,
        )
        repository.upsert(task)
        probe(task)
    }

    /**
     * Отмечает трек в альбоме или плейлисте.
     *
     * Отметки живут в самой задаче: можно свернуть карточку, вернуться
     * позже и продолжить выбор.
     */
    fun toggleCollectionTrack(taskId: String, trackId: String) {
        viewModelScope.launch {
            val task = repository.get(taskId) ?: return@launch
            val updated = task.collectionTracks.map { track ->
                if (track.id == trackId) track.copy(selected = !track.selected) else track
            }
            repository.upsert(task.copy(collectionTracks = updated))
        }
    }

    /**
     * Разворачивает альбом или плейлист в отдельные задачи.
     *
     * Каждый выбранный трек становится своей задачей — со своим прогрессом
     * и возможностью отменить его по отдельности. В названии задачи
     * остаётся пометка, из какой коллекции трек: так видно, что он часть
     * альбома, а не самостоятельная закачка.
     */
    fun expandCollection(taskId: String, selectedIds: Set<String>) {
        viewModelScope.launch {
            val task = repository.get(taskId) ?: return@launch
            val selected = task.collectionTracks.filter { it.id in selectedIds }
            if (selected.isEmpty()) {
                _messages.value = "Не выбран ни один трек"
                return@launch
            }

            val settings = settingsRepository.current()
            selected.forEach { track ->
                val child = DownloadTask(
                    id = UUID.randomUUID().toString(),
                    url = trackUrl(task.source, track),
                    source = task.source,
                    createdAt = System.currentTimeMillis(),
                    audioOnly = settings.defaultAudioOnly,
                    parentTitle = task.title,
                )
                repository.upsert(child)
                probe(child)
            }

            repository.delete(taskId)
            _messages.value = "В очередь добавлено треков: ${selected.size}"
        }
    }

    /** Ссылка на трек коллекции: у SoundCloud она готовая, у Spotify — по id. */
    private fun trackUrl(source: Source, track: CollectionTrack): String =
        track.url ?: when (source) {
            Source.SPOTIFY -> "https://open.spotify.com/track/${track.id}"
            else -> track.id
        }

    /**
     * Догружает обложки треков, которых не было в списке площадки.
     *
     * У Spotify в списке плейлиста обложек нет: площадка отдаёт их только
     * на странице трека. Запрашиваем по одной и обновляем задачу — карточка
     * подхватит обновление сама.
     */
    fun loadMissingCovers(taskId: String) {
        // Догрузка должна идти один раз: карточка перерисовывается при
        // каждом обновлении задачи, а запросов здесь десятки.
        if (!loadingCovers.add(taskId)) return
        viewModelScope.launch {
            try {
                // Сеть — только в фоне: запросов тут десятки, и на главном
                // потоке они бы заморозили интерфейс.
                withContext(Dispatchers.IO) { loadMissingCoversInner(taskId) }
            } finally {
                loadingCovers.remove(taskId)
            }
        }
    }

    private suspend fun loadMissingCoversInner(taskId: String) {
            val task = repository.get(taskId) ?: return
            if (task.source != Source.SPOTIFY) return

            var tracks = task.collectionTracks
            val pending = tracks.filter { it.thumbUrl.isNullOrBlank() }
            if (pending.isEmpty()) return

            com.turbofetch.core.Log.i(
                "Обложки: догружаю ${pending.size} для «${task.title}»",
            )
            var done = 0
            for (track in pending) {
                val cover = SpotifyMetadata.trackCover(container.httpClient, track.id)
                if (cover != null) {
                    tracks = tracks.map {
                        if (it.id == track.id) it.copy(thumbUrl = cover) else it
                    }
                }
                done++
                // Обновляем задачу пачками: список на экране подхватывает
                // обложки по мере появления, а не после всех запросов.
                if (done % 5 == 0) {
                    repository.upsert(task.copy(collectionTracks = tracks))
                }
            }
            repository.upsert(task.copy(collectionTracks = tracks))
    }

    /** Запрашивает метаданные и список форматов. */
    private suspend fun probe(task: DownloadTask) {
        repository.updateStatus(task.id, TaskStatus.PROBING)
        com.turbofetch.core.Log.i("Probe: ${task.url}")
        val result = engine.probe(task.url)
        result.fold(
            onSuccess = { info ->
                val settings = settingsRepository.current()
                val audioStreams = info.formats.filter { it.hasAudio && !it.hasVideo }
                val picked = when {
                    // Площадка отдаёт только звук (SoundCloud, Spotify):
                    // видеоформатов нет, и пресет качества тут не о чем —
                    // берём лучший по битрейту.
                    settings.defaultAudioOnly ||
                        MediaNaming.isAudioOnlySource(info.formats) ->
                        audioStreams.maxByOrNull { it.bitrate }

                    else -> QualityPreset.pick(info.formats, settings.defaultQuality)
                }

                // Если выбранное видео идёт без звука (DASH), сразу подбираем
                // отдельную аудиодорожку — их потом склеим.
                val pairedAudio = picked
                    ?.takeIf { !it.hasAudio }
                    ?.let { audioStreams.maxByOrNull { stream -> stream.bitrate } }

                com.turbofetch.core.Log.i(
                    "Probe ok: форматов ${info.formats.size}, выбран ${picked?.label}" +
                        (pairedAudio?.let { " + звук ${it.label}" } ?: ""),
                )

                repository.upsert(
                    task.copy(
                        title = info.title,
                        author = info.author,
                        thumbnailUrl = info.thumbnailUrl,
                        durationSeconds = info.durationSeconds,
                        availableFormats = info.formats,
                        selectedFormat = picked ?: info.formats.firstOrNull(),
                        audioFormat = pairedAudio,
                        audioOnly = settings.defaultAudioOnly,
                        status = TaskStatus.READY,
                        // Альбом или плейлист: сохраняем список треков,
                        // чтобы карточка могла показать их с выбором.
                        collectionTracks = info.collectionTracks,
                    ),
                )
            },
            onFailure = { error ->
                com.turbofetch.core.Log.e("Probe провалился: ${error.message}", error)
                repository.updateStatus(task.id, TaskStatus.FAILED, error.message)
            },
        )
    }

    fun saveTask(updated: DownloadTask) {
        viewModelScope.launch { repository.upsert(updated) }
    }

    fun download(taskId: String) {
        viewModelScope.launch {
            val task = repository.get(taskId) ?: return@launch
            if (task.status.isActive) return@launch
            repository.updateStatus(taskId, TaskStatus.QUEUED)
            scheduler.enqueue(taskId, settingsRepository.current().downloadOverWifiOnly)
        }
    }

    fun downloadAll() {
        viewModelScope.launch {
            val wifiOnly = settingsRepository.current().downloadOverWifiOnly
            tasks.value
                .filter { it.status == TaskStatus.READY || it.status == TaskStatus.PENDING }
                // Коллекцию (альбом или плейлист) качать нельзя: сначала
                // юзер выбирает треки, и они становятся отдельными задачами.
                .filterNot { it.isCollection }
                .forEach { task ->
                    repository.updateStatus(task.id, TaskStatus.QUEUED)
                    scheduler.enqueue(task.id, wifiOnly)
                }
        }
    }

    fun cancel(taskId: String) {
        viewModelScope.launch {
            scheduler.cancel(taskId)
            repository.updateStatus(taskId, TaskStatus.CANCELLED, "Отменено")
        }
    }

    fun delete(taskId: String) {
        viewModelScope.launch {
            scheduler.cancel(taskId)
            repository.delete(taskId)
        }
    }

    fun retry(taskId: String) {
        viewModelScope.launch {
            repository.updateStatus(taskId, TaskStatus.READY)
        }
    }

    /** Открыть готовый файл системным плеером. */
    fun openOutput(task: DownloadTask): Boolean {
        val uriString = task.outputUri ?: return false
        val uri = runCatching { uriString.toUri() }.getOrNull() ?: return false
        val mime = if (task.audioOnly) "audio/*" else "video/*"
        val intent = Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, mime)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return runCatching {
            getApplication<Application>().startActivity(intent)
            true
        }.getOrDefault(false)
    }

    /**
     * Очистить очередь разом.
     *
     * Задания в работе не трогаем — их ещё качает воркер, и простое
     * удаление оставило бы загрузки без записи.
     */
    fun clearQueue() {
        viewModelScope.launch {
            val active = tasks.value.filter { it.status.isActive }
            active.forEach { scheduler.cancel(it.id) }

            repository.clear()
            _messages.value = if (active.isEmpty()) {
                "Очередь очищена"
            } else {
                "Очередь очищена, загрузок отменено: ${active.size}"
            }
        }
    }

    fun consumeMessage() {
        _messages.value = null
    }

    /**
     * Переставить задачу в очереди. Порядок важен для пакетной скачки:
     * файллы качаются в том порядке, в котором они стоят в списке.
     */
    fun moveTask(from: Int, to: Int) {
        viewModelScope.launch {
            val current = tasks.value.toMutableList()
            if (from !in current.indices || to !in current.indices) return@launch
            val moved = current.removeAt(from)
            current.add(to, moved)
            repository.reorder(current.map { it.id })
        }
    }

    fun formatFor(task: DownloadTask): MediaFormat? = task.selectedFormat
}
