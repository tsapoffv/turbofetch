package com.turbofetch.ui.files

import android.app.Application
import androidx.compose.runtime.Stable
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.turbofetch.TurboFetchApp
import com.turbofetch.domain.model.DownloadedFile
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Список уже скачанных файлов.
 *
 * Умеет убирать записи по одной и очищать историю разом. Файлы при этом
 * остаются на диске: история — это журнал, а не кладбище.
 */
/** @Stable — см. пояснение в `QueueViewModel`. */
@Stable
class FilesViewModel(application: Application) : AndroidViewModel(application) {

    private val container = (application as TurboFetchApp).container
    private val repository = container.fileRepository

    val files: StateFlow<List<DownloadedFile>> = repository.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _messages = MutableStateFlow<String?>(null)
    val messages: StateFlow<String?> = _messages.asStateFlow()

    /**
     * Убрать запись из истории.
     *
     * Удаляется только строка в списке — сам файл остаётся на диске
     * и в галерее. История и галерея — разные вещи.
     */
    fun delete(file: DownloadedFile) {
        viewModelScope.launch {
            repository.delete(file.id)
            _messages.value = "Запись «${file.fileName}» убрана из истории"
        }
    }

    /** Очистить историю разом. Файлы на диске остаются. */
    fun deleteAll() {
        viewModelScope.launch {
            val count = files.value.size
            if (count == 0) return@launch

            repository.clear()
            _messages.value = "История очищена: записей $count"
        }
    }

    /** Ссылка на источник скопирована в буфер обмена. */
    fun notifyLinkCopied() {
        _messages.value = "Ссылка скопирована"
    }

    fun consumeMessage() {
        _messages.value = null
    }
}
