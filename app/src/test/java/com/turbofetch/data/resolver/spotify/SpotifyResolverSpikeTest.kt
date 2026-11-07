package com.turbofetch.data.resolver.spotify

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.util.concurrent.TimeUnit

/**
 * Спайк-тест Spotify: метаданные из embed-страницы.
 *
 * Сеть нужна явно: `./gradlew :app:testDebugUnitTest -Dturbofetch.network=true`.
 */
class SpotifyResolverSpikeTest {

    private val network = System.getProperty("turbofetch.network") == "true"

    @Test
    fun `читает метаданные трека`() = runBlocking {
        assumeTrue("Нужен доступ в интернет", network)

        val client = okhttp3.OkHttpClient.Builder()
            .callTimeout(30, TimeUnit.SECONDS)
            .build()

        val track = SpotifyMetadata.fetch(client, "4cOdK2wGLETKBW3PvgPWqT")

        assertEquals("Never Gonna Give You Up", track.title)
        assertEquals("Rick Astley", track.artist)
        assertEquals(213, track.durationSeconds)
        assertTrue("обложка не найдена", !track.coverUrl.isNullOrBlank())
    }

    @Test
    fun `находит трек на YouTube по метаданным Spotify`() = runBlocking {
        assumeTrue("Нужен доступ в интернет", network)

        val resolver = SpotifyResolver()
        val result = resolver.resolve("https://open.spotify.com/track/4cOdK2wGLETKBW3PvgPWqT")

        result.fold(
            onSuccess = { info ->
                assertEquals("Never Gonna Give You Up", info.title)
                assertTrue("форматов нет", info.formats.isNotEmpty())
                assertTrue(
                    "это не аудиопоток: ${info.formats.first()}",
                    info.formats.none { it.hasVideo },
                )
                info.formats.forEach { format ->
                    println("=== ФОРМАТ: ${format.id} ${format.label} ${format.bitrate}")
                }
            },
            onFailure = { throw it },
        )
    }
}
