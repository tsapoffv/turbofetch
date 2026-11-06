package com.turbofetch.data.remote

import okhttp3.Cookie
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SimpleCookieJarTest {

    private val jar = SimpleCookieJar()
    private val pageUrl = "https://www.tiktok.com/@user/video/123".toHttpUrl()
    private val cdnUrl = "https://v16-webapp-prime.tiktok.com/video/tos/abc".toHttpUrl()

    private fun cookie(setCookie: String): Cookie =
        requireNotNull(Cookie.parse(pageUrl, setCookie)) { "Не удалось разобрать cookie" }

    @Test
    fun `cookie с доменом tiktok com уходит на CDN`() {
        jar.saveFromResponse(
            pageUrl,
            listOf(cookie("ttwid=abc; Domain=.tiktok.com; Path=/")),
        )

        val forCdn = jar.loadForRequest(cdnUrl)

        assertEquals(1, forCdn.size)
        assertEquals("ttwid", forCdn.first().name)
    }

    @Test
    fun `cookie без атрибута Domain остаётся только на своём хосте`() {
        jar.saveFromResponse(
            pageUrl,
            listOf(cookie("msToken=xyz; Path=/")),
        )

        assertEquals(1, jar.loadForRequest(pageUrl).size)
        assertTrue(jar.loadForRequest(cdnUrl).isEmpty())
    }

    @Test
    fun `повторный Set-Cookie заменяет cookie а не добавляет дубль`() {
        jar.saveFromResponse(
            pageUrl,
            listOf(cookie("ttwid=first; Domain=.tiktok.com; Path=/")),
        )
        jar.saveFromResponse(
            pageUrl,
            listOf(cookie("ttwid=second; Domain=.tiktok.com; Path=/")),
        )

        val cookies = jar.loadForRequest(cdnUrl)

        assertEquals(1, cookies.size)
        assertEquals("second", cookies.first().value)
    }

    @Test
    fun `cookie не уходит на чужой домен`() {
        jar.saveFromResponse(
            pageUrl,
            listOf(cookie("ttwid=abc; Domain=.tiktok.com; Path=/")),
        )

        val foreign = "https://example.org/anything".toHttpUrl()
        assertTrue(jar.loadForRequest(foreign).isEmpty())
    }
}
