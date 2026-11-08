package com.turbofetch.data.remote

import okhttp3.ConnectionPool
import okhttp3.Dispatcher
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

/** Общий HTTP-клиент приложения. */
object HttpClientFactory {

    /**
     * Мобильный User-Agent: TikTok отдаёт разметку с готовыми данными именно
     * мобильным запросам, десктопный упирается в JS-челлендж.
     */
    const val MOBILE_UA =
        "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 " +
            "(KHTML, like Gecko) Chrome/131.0.0.0 Mobile Safari/537.36"

    fun create(): OkHttpClient = OkHttpClient.Builder()
        .cookieJar(SimpleCookieJar())
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        // Верхняя граница на весь запрос: пока файл качается, он не
        // прерывается, но зависшая передача не может длиться вечно.
        .callTimeout(30, TimeUnit.MINUTES)
        // По умолчанию OkHttp разрешает 5 одновременных запросов к одному
        // хосту. При десяти загрузках с одного CDN (YouTube, SoundCloud)
        // остальные запросы вставали в очередь и выглядели «зависшими».
        .dispatcher(
            Dispatcher().apply {
                maxRequests = 24
                maxRequestsPerHost = 8
            },
        )
        .connectionPool(ConnectionPool(8, 5, TimeUnit.MINUTES))
        .followRedirects(true)
        .followSslRedirects(true)
        .build()
}
