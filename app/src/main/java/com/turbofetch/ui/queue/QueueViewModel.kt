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
import com.turbofetch.domain.model.DownloadTask
import com.turbofetch.domain.model.MediaFormat
import com.turbofetch.domain.model.QualityPreset
import com.turbofetch.domain.model.TaskStatus
import com.turbofetch.worker.DownloadScheduler
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.UUID

class QueueViewModel(application: Application) : AndroidViewModel(application) {

    private val container = (application as TurboFetchApp).container
    private val repository = container.taskRepository
    private val settingsRepository = container.settingsRepository
    private val engine = container.downloadEngine
    private val scheduler = DownloadScheduler(container.workManager)

    val tasks: StateFlow<List<DownloadTask>> = repository.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val settings = settingsRepository.observe()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AppSettings())

    private val _messages = MutableStateFlow<String?>(null)
    val messages: StateFlow<String?> = _messages.asStateFlow()

    /** Принимает ссылку из «Поделиться» или из поля ввода. */
    fun addLink(raw: String) {
        viewModelScope.launch {
            val parsed = LinkParser.parse(raw)
            if (parsed == null) {
                _messages.value = "Не удалось распознать ссылку"
                return@launch
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
                val picked = if (settings.defaultAudioOnly) {
                    audioStreams.maxByOrNull { it.bitrate }
                } else {
                    QualityPreset.pick(info.formats, settings.defaultQuality)
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
