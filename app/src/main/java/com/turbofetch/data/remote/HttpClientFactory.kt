package com.turbofetch.data.remote

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
        .followRedirects(true)
        .followSslRedirects(true)
        .build()
}
