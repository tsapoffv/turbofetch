package com.turbofetch.domain.engine

import com.turbofetch.data.remote.HttpClientFactory
import com.turbofetch.domain.link.LinkParser
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
        emit(DownloadProgress.Preparing)

        val directUrl = request.format.url
        if (directUrl.isNullOrBlank()) {
            emit(DownloadProgress.Failed("У выбранного формата нет прямой ссылки"))
            return@flow
        }

        val builder = Request.Builder().url(directUrl)
        (request.headers + request.format.headers).forEach { (name, value) ->
            builder.header(name, value)
        }

        try {
            com.turbofetch.core.Log.i("GET $directUrl")
            client.newCall(builder.build()).execute().use { response ->
                com.turbofetch.core.Log.i("HTTP ${response.code}, тело ${response.body?.contentLength()} байт")
                if (!response.isSuccessful) {
                    val detail = runCatching { response.body?.string()?.take(300) }.getOrNull()
                    com.turbofetch.core.Log.e("HTTP ${response.code}: $detail")
                    emit(DownloadProgress.Failed("Сервер вернул HTTP ${response.code}"))
                    return@flow
                }

                val body = response.body
                if (body == null) {
                    emit(DownloadProgress.Failed("Пустой ответ"))
                    return@flow
                }

                val total = body.contentLength().takeIf { it > 0 }
                request.outputFile.parentFile?.mkdirs()
                var written = 0L

                body.byteStream().use { input ->
                    request.outputFile.outputStream().use { output ->
                        val buffer = ByteArray(64 * 1024)
                        while (true) {
                            val read = input.read(buffer)
                            if (read < 0) break
                            output.write(buffer, 0, read)
                            written += read
                            emit(DownloadProgress.Running(written, total))
                        }
                    }
                }

                emit(DownloadProgress.Finished(request.outputFile, written))
            }
        } catch (io: IOException) {
            emit(DownloadProgress.Failed(io.message ?: "Ошибка сети", io))
        }
    }.flowOn(Dispatchers.IO)
}
