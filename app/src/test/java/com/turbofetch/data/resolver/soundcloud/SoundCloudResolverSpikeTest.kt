package com.turbofetch.data.resolver.soundcloud

import com.turbofetch.domain.model.FormatKind
import com.turbofetch.domain.model.Source
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

/**
 * Живая проверка резолвера SoundCloud.
 *
 * Сеть включается явно: `./gradlew testDebugUnitTest -Dturbofetch.network=true`.
 */
class SoundCloudResolverSpikeTest {

    @Test
    fun `разбирает трек SoundCloud`() = runBlocking {
        assumeTrue("Нужен доступ в интернет", System.getProperty("turbofetch.network") == "true")

        val resolver = SoundCloudResolver()
        val result = resolver.resolve("https://soundcloud.com/forss/flickermood")
        val info = result.getOrElse { error("Не удалось разобрать: $it") }

        println("=== ТРЕК: ${info.title}")
        println("=== АВТОР: ${info.author}")
        println("=== ДЛИТЕЛЬНОСТЬ: ${info.durationSeconds}")
        info.formats.forEach { format ->
            println("    id=${format.id} label=${format.label} mime=${format.mimeType}")
            println("    url=${format.url?.take(110)}")
        }

        assertEquals(Source.SOUNDCLOUD, info.source)
        assertTrue("Форматов быть не должно", info.formats.isNotEmpty())
        val format = info.formats.first()
        assertEquals(FormatKind.HLS_PLAYLIST, format.kind)
        assertTrue("Это должен быть звук", format.hasAudio && !format.hasVideo)
        assertTrue(
            "Пресет должен быть aac",
            format.id.startsWith("aac_"),
        )
        assertTrue(
            "Плейлист должен лежать на CDN SoundCloud",
            format.url?.contains("playback.media-streaming.soundcloud.cloud") == true,
        )
    }

    @Test
    fun `abr-потоки не выбираются`() = runBlocking {
        assumeTrue("Нужен доступ в интернет", System.getProperty("turbofetch.network") == "true")

        val resolver = SoundCloudResolver()
        val info = resolver.resolve("https://soundcloud.com/forss/flickermood")
            .getOrElse { error("Не удалось разобрать: $it") }

        assertTrue(
            "Выбран битый abr-поток",
            info.formats.none { it.id.startsWith("abr") },
        )
    }
}
