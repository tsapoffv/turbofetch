package com.turbofetch.domain.engine

import com.turbofetch.domain.model.MediaFormat
import com.turbofetch.domain.model.MediaInfo
import kotlinx.coroutines.flow.Flow
import java.io.File

/** Что именно скачиваем. */
data class DownloadRequest(
    val url: String,
    val format: MediaFormat,
    val audioOnly: Boolean,
    /** Куда движок пишет промежуточный файл (кэш приложения). */
    val outputFile: File,
    /** Заголовки, которые требует CDN площадки. */
    val headers: Map<String, String> = emptyMap(),
)

/** Ход скачивания. */
sealed interface DownloadProgress {
    data object Preparing : DownloadProgress

    data class Running(
        val bytesRead: Long,
        val totalBytes: Long?,
    ) : DownloadProgress {
        val percent: Float
            get() = totalBytes?.takeIf { it > 0 }
                ?.let { (bytesRead.toFloat() / it).coerceIn(0f, 1f) }
                ?: 0f
    }

    data class Finished(val file: File, val bytes: Long) : DownloadProgress

    data class Failed(val message: String, val cause: Throwable? = null) : DownloadProgress
}

/**
 * Шов между UI и способом скачивания.
 *
 * Реализации: `FakeEngine` (этап 1, имитация) и в этапе 2 — отдельные резолверы
 * под YouTube / TikTok / X, скрытые за этим же интерфейсом.
 */
interface DownloadEngine {

    /** Разобрать ссылку и узнать доступные форматы. */
    suspend fun probe(url: String): Result<MediaInfo>

    /** Скачать файл, отдавая ход процесса. */
    fun download(request: DownloadRequest): Flow<DownloadProgress>
}
