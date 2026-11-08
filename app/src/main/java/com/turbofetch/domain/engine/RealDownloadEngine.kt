package com.turbofetch.domain.engine

import com.turbofetch.data.remote.HttpClientFactory
import com.turbofetch.domain.link.LinkParser
import com.turbofetch.domain.model.FormatKind
import com.turbofetch.domain.model.MediaInfo
import com.turbofetch.domain.model.Source
import com.turbofetch.domain.resolver.VideoResolver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException

/**
 * Настоящий движок: разбор ссылки — через резолвер площадки, скачивание —
 * напрямую с CDN.
 *
 * Важно: байты файла идут **мимо любого сервера**, прямо с CDN площадки.
 * Никакой прокси, который режет скорость.
 */
class RealDownloadEngine(
    private val resolvers: Map<Source, VideoResolver>,
    private val client: OkHttpClient = HttpClientFactory.create(),
    private val hlsDownloader: HlsDownloader = HlsDownloader(client),
) : DownloadEngine {

    override suspend fun probe(url: String): Result<MediaInfo> {
        val source = LinkParser.parse(url)?.source
            ?: return Result.failure(IllegalArgumentException("Не удалось определить площадку"))

        val resolver = resolvers[source]
            ?: return Result.failure(
                UnsupportedOperationException("Площадка ${source.name} пока не поддерживается"),
            )

        return resolver.resolve(url)
    }

    override fun download(request: DownloadRequest): Flow<DownloadProgress> = flow {
        com.turbofetch.core.Log.i(
            "Скачиваю: kind=${request.format.kind} url=${request.format.url?.take(60)}",
        )
        emit(DownloadProgress.Preparing)

        val directUrl = request.format.url
        if (directUrl.isNullOrBlank()) {
            emit(DownloadProgress.Failed("У выбранного формата нет прямой ссылки"))
            return@flow
        }

        // Плейлист — это не файл, а десятки сегментов: качать его одним
        // запросом нельзя. Так отдаёт SoundCloud.
        if (request.format.kind == FormatKind.HLS_PLAYLIST) {
            com.turbofetch.core.Log.i("HLS-плейлист: $directUrl")
            hlsDownloader.download(directUrl, request.outputFile).collect { progress ->
                emit(progress)
            }
            return@flow
        }

        // Докачка: если файл уже частично скачан, просим продолжить с нужного места.
        val alreadyHave = request.outputFile.takeIf { it.exists() }?.length() ?: 0L

        val builder = Request.Builder().url(directUrl)
        if (alreadyHave > 0) {
            builder.header("Range", "bytes=$alreadyHave-")
        }
        (request.headers + request.format.headers).forEach { (name, value) ->
            builder.header(name, value)
        }

        try {
            com.turbofetch.core.Log.i(
                "GET $directUrl" + if (alreadyHave > 0) " (с $alreadyHave)" else "",
            )
            client.newCall(builder.build()).execute().use { response ->
                com.turbofetch.core.Log.i(
                    "HTTP ${response.code}, тело ${response.body?.contentLength()} байт",
                )

                if (!response.isSuccessful) {
                    val detail = runCatching { response.body?.string()?.take(300) }.getOrNull()
                    com.turbofetch.core.Log.e("HTTP ${response.code}: $detail")
                    emit(
                        DownloadProgress.Failed(
                            message = "Сервер вернул HTTP ${response.code}",
                            // 5xx и 408/429 — попробуем позже, 4xx — нет.
                            retryable = response.code >= 500 ||
                                response.code == 408 ||
                                response.code == 429,
                        ),
                    )
                    return@flow
                }

                val body = response.body
                if (body == null) {
                    emit(DownloadProgress.Failed("Пустой ответ"))
                    return@flow
                }

                // 206 = сервер продолжил с нашего места. 200 = сервер начал заново.
                val resuming = alreadyHave > 0 && response.code == 206
                if (!resuming && alreadyHave > 0) {
                    com.turbofetch.core.Log.i("Сервер не поддержал докачку, начинаем заново")
                }

                val total = body.contentLength()
                    .takeIf { it > 0 }
                    ?.plus(if (resuming) alreadyHave else 0L)

                request.outputFile.parentFile?.mkdirs()
                var written = if (resuming) alreadyHave else 0L

                body.byteStream().use { input ->
                    // В режиме докачки дописываем в конец, иначе — перезаписываем.
                    // File.outputStream() всегда открывает файл на перезапись и
                    // обрезал его, из-за чего докачанный файл получался битым.
                    java.io.FileOutputStream(request.outputFile, resuming).use { output ->
                        // Буфер побольше: 256 КБ — это вчетверо меньше вызовов
                        // write(), а на телефоне каждый вызов — это запись
                        // через FUSE в MediaStore.
                        val buffer = ByteArray(256 * 1024)
                        // Прогресс отдаём не каждый чанк, а когда изменился
                        // целый процент (и не чаще, чем раз в 100 мс).
                        val throttle = ProgressThrottle()
                        while (true) {
                            val read = input.read(buffer)
                            if (read < 0) break
                            output.write(buffer, 0, read)
                            written += read
                            if (throttle.shouldEmit(written, total)) {
                                emit(DownloadProgress.Running(written, total))
                            }
                        }
                    }
                }

                emit(DownloadProgress.Finished(request.outputFile, written))
            }
        } catch (io: IOException) {
            // Обрыв связи — попытку стоит повторить, файл докачается с места обрыва.
            emit(DownloadProgress.Failed(io.message ?: "Ошибка сети", io, retryable = true))
        }
    }.flowOn(Dispatchers.IO)
}
