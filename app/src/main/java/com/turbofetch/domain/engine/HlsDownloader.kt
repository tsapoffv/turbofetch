package com.turbofetch.domain.engine

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withTimeout
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream

/**
 * Скачивание HLS-плейлиста (fMP4).
 *
 * Так отдаёт SoundCloud: подписанный m3u8, за ним `init.mp4` и десятки
 * сегментов `.m4s`. Склеив их байтами, получаем корректный фрагментированный
 * MP4 — без перекодирования.
 *
 * Отдельный загрузчик нужен потому, что обычный движок умеет только один
 * GET одного URL, а здесь запросов много.
 */
class HlsDownloader(
    private val client: OkHttpClient,
) {

    /**
     * Скачивает плейлист в файл.
     *
     * Файл перезаписывается: докачать HLS по кускам нельзя — при обрыве
     * непонятно, на каком сегменте остановились, поэтому проще начать заново.
     */
    fun download(playlistUrl: String, outputFile: File): Flow<DownloadProgress> = flow {
        val collector = this
        emit(DownloadProgress.Preparing)

        com.turbofetch.core.Log.i("HLS: качаю плейлист")
        val playlist = parsePlaylist(fetchText(playlistUrl))
        com.turbofetch.core.Log.i(
            "HLS: init=${playlist.initSegment != null} сегментов=${playlist.segments.size}",
        )
        val initUrl = playlist.initSegment
        val segments = playlist.segments

        if (segments.isEmpty()) {
            emit(DownloadProgress.Failed("Плейлист пустой — сегментов нет", retryable = true))
            return@flow
        }

        // Размеры нужны для честного процента: без них полоса стояла бы
        // на нуле. HEAD-запросы лёгкие, сегментов обычно двадцать.
        val initSize = initUrl?.let { contentLength(it) } ?: 0L
        val segmentSizes = ArrayList<Long>(segments.size)
        for (segment in segments) {
            segmentSizes += contentLength(segment) ?: 0L
        }
        val total = initSize + segmentSizes.sum()

        com.turbofetch.core.Log.i("HLS: размеры известны, total=$total")
        var written = 0L
        outputFile.parentFile?.mkdirs()
        outputFile.delete()

        // Один троттлинг на весь плейлист: иначе каждый сегмент начинал бы
        // счёт заново и отдавал своё первое событие прогресса.
        val throttle = ProgressThrottle()

        suspend fun append(url: String) = withTimeout(SEGMENT_TIMEOUT_MS) {
            val request = Request.Builder().url(url).build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    error("CDN вернул HTTP ${response.code}")
                }
                val body = response.body ?: error("Пустой ответ CDN")
                body.byteStream().use { input ->
                    FileOutputStream(outputFile, true).use { output ->
                        val buffer = ByteArray(256 * 1024)
                        while (true) {
                            val read = input.read(buffer)
                            if (read < 0) break
                            output.write(buffer, 0, read)
                            written += read
                            val size = total.takeIf { it > 0 }
                            if (throttle.shouldEmit(written, size)) {
                                collector.emit(DownloadProgress.Running(written, size))
                            }
                        }
                    }
                }
            }
        }

        try {
            initUrl?.let { append(it) }
            for ((index, segment) in segments.withIndex()) {
                append(segment)
                if (index % 5 == 0) {
                    com.turbofetch.core.Log.i("HLS: сегмент $index из ${segments.size}")
                }
            }
        } catch (e: Exception) {
            emit(
                DownloadProgress.Failed(
                    message = e.message ?: "Не удалось скачать плейлист",
                    cause = e,
                    retryable = true,
                ),
            )
            return@flow
        }

        emit(DownloadProgress.Finished(outputFile, written))
    }.flowOn(Dispatchers.IO)

    /** Разобранный m3u8. */
    private data class Playlist(
        val initSegment: String?,
        val segments: List<String>,
    )

    private fun parsePlaylist(text: String): Playlist {
        val init = INIT.find(text)?.groupValues?.get(1)?.let { absolutize(it) }
        val segments = text.lineSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() && !it.startsWith("#") }
            .filter { it.contains(".m4s") || it.contains(".mp4") }
            .map { absolutize(it) }
            // init-сегмент идёт отдельной строкой в манифесте, но на всякий
            // случай убираем его из списка сегментов.
            .filter { it != init }
            .toList()
        return Playlist(initSegment = init, segments = segments)
    }

    private fun absolutize(url: String): String {
        if (url.startsWith("http")) return url
        return "${BASE_URL.trimEnd('/')}/$url"
    }

    /**
     * Чтение плейлиста.
     *
     * Свой таймаут: OkHttp ждёт ответа 30 секунд, но если сервер держит
     * соединение открытым и не отдаёт конец тела, `.string()` может
     * висеть бесконечно. Потолок здесь обязателен.
     */
    private suspend fun fetchText(url: String): String = withTimeout(TEXT_TIMEOUT_MS) {
        val request = Request.Builder().url(url).build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) error("Плейлист вернул HTTP ${response.code}")
            val text = response.body?.string().orEmpty()
            com.turbofetch.core.Log.i("HLS: плейлист ${text.length} символов")
            text
        }
    }

    /** Размер файла: HEAD-запросом. */
    private fun contentLength(url: String): Long? = runCatching {
        val head = Request.Builder().url(url).head().build()
        client.newCall(head).execute().use { response ->
            response.header("Content-Length")?.toLongOrNull()
        }
    }.getOrNull()

    private companion object {
        val INIT = Regex("""#EXT-X-MAP:URI="([^"]+)"""")

        /** Корень для относительных ссылок сегментов. */
        const val BASE_URL = "https://playback.media-streaming.soundcloud.cloud"

        /** Потолок на чтение плейлиста, миллисекунды. */
        const val TEXT_TIMEOUT_MS = 30_000L

        /** Потолок на один сегмент, миллисекунды. */
        const val SEGMENT_TIMEOUT_MS = 60_000L
    }
}
