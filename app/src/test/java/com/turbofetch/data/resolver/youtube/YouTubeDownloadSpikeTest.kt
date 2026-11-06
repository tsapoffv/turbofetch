package com.turbofetch.data.resolver.youtube

import com.turbofetch.data.remote.HttpClientFactory
import kotlinx.coroutines.runBlocking
import okhttp3.Request
import org.junit.Assert.assertEquals
import org.junit.Assume.assumeTrue
import org.junit.Test

/**
 * Живая проверка скачивания потоков YouTube: видео-only и аудио — отдельно.
 * Показывает, можно ли получить 1080p без ffmpeg и какие заголовки нужны CDN.
 *
 * Запуск: `./gradlew testDebugUnitTest --tests '*YouTubeDownloadSpikeTest' -Dturbofetch.network=true`
 */
class YouTubeDownloadSpikeTest {

    @Test
    fun `качает video-only и audio потоки YouTube`() = runBlocking {
        assumeTrue("Нужен доступ в интернет", System.getProperty("turbofetch.network") == "true")

        val resolver = YouTubeResolver()
        val info = resolver.resolve("https://www.youtube.com/watch?v=dQw4w9WgXcQ").getOrThrow()

        val client = HttpClientFactory.create()
        val videoOnly = info.formats.firstOrNull { it.id.startsWith("vonly") && it.height == 1080 }
        val audio = info.formats.firstOrNull { it.id.startsWith("audio") }
        val muxed = info.formats.firstOrNull { it.id.startsWith("muxed") }

        listOfNotNull(videoOnly, audio, muxed).forEach { format ->
            // Просим только начало потока: полностью качать 80 МБ в тесте незачем.
            val request = Request.Builder()
                .url(format.url!!)
                .header("Range", "bytes=0-65535")
                .build()
            client.newCall(request).execute().use { response ->
                val bytes = response.body?.bytes()?.size ?: 0
                println(
                    "=== ${format.id} (${format.label}) → HTTP ${response.code}, " +
                        "прочитано $bytes байт, content-type=${response.body?.contentType()}, " +
                        "всего=${response.header("Content-Range") ?: response.header("Content-Length")}",
                )
                assertEquals(206, response.code)
            }
        }
    }
}
