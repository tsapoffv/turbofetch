package com.turbofetch.data.remote

import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl

/**
 * Простейшее хранилище cookie в памяти.
 *
 * Нужно для TikTok: CDN отдаёт 403, если в запросе нет cookie, выданных страницей,
 * и заголовка `Referer`. Переживать перезапуск приложения им не нужно.
 *
 * Важно: cookie хранятся **общим списком**, а не «по хосту ответа». Cookie с
 * `Domain=.tiktok.com` должна уйти и на `www.tiktok.com`, и на
 * `v16-webapp-prime.tiktok.com`. Проверку соответствия запросу делает
 * сам OkHttp (`Cookie.matches`), он же учитывает Domain, Path и Secure.
 */
class SimpleCookieJar : CookieJar {

    private val store = mutableListOf<Cookie>()

    @Synchronized
    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        if (cookies.isEmpty()) return
        cookies.forEach { cookie ->
            store.removeAll {
                it.name == cookie.name && it.domain == cookie.domain && it.path == cookie.path
            }
            store += cookie
        }
    }

    @Synchronized
    override fun loadForRequest(url: HttpUrl): List<Cookie> {
        val now = System.currentTimeMillis()
        store.removeAll { it.expiresAt < now }
        return store.filter { it.matches(url) }
    }
}
