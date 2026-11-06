package com.turbofetch.data.resolver.youtube

import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request as OkHttpRequest
import okhttp3.RequestBody.Companion.toRequestBody
import org.schabi.newpipe.extractor.downloader.Downloader
import org.schabi.newpipe.extractor.downloader.Request as NpRequest
import org.schabi.newpipe.extractor.downloader.Response as NpResponse

/**
 * Мост между OkHttp и загрузчиком NewPipeExtractor.
 *
 * Экстрактор сам решает, что и куда запрашивать (страница ролика, InnerTube-API,
 * счётчики просмотров), мы только выполняем HTTP-запросы и отдаём ответ.
 */
class NewPipeDownloader(
    private val client: OkHttpClient,
) : Downloader() {

    override fun execute(request: NpRequest): NpResponse {
        val builder = OkHttpRequest.Builder().url(request.url())

        request.headers().forEach { (name, values) ->
            if (values.isNotEmpty()) {
                builder.header(name, values.joinToString(", "))
            }
        }

        val body = request.dataToSend()
        val call = when (request.httpMethod().uppercase()) {
            "POST" -> builder.post(
                (body ?: ByteArray(0)).toRequestBody(
                    request.headers()["Content-Type"]?.firstOrNull()?.toMediaTypeOrNull(),
                ),
            ).build()

            "HEAD" -> builder.head().build()
            else -> builder.get().build()
        }

        return client.newCall(call).execute().use { response ->
            NpResponse(
                response.code,
                response.message,
                response.headers.toMultimap(),
                response.body?.string().orEmpty(),
                response.request.url.toString(),
            )
        }
    }
}
