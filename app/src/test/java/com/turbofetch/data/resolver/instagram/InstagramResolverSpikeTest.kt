package com.turbofetch.data.resolver.instagram

import com.turbofetch.domain.model.Source
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

/**
 * Живая проверка резолвера Instagram.
 *
 * Сеть включается явно: `./gradlew testDebugUnitTest -Dturbofetch.network=true`.
 */
class InstagramResolverSpikeTest {

    @Test
    fun `разбирает публичный рилс Instagram`() = runBlocking {
        assumeTrue("Нужен доступ в интернет", System.getProperty("turbofetch.network") == "true")

        val resolver = InstagramResolver()
        val result = resolver.resolve("https://www.instagram.com/p/Chunk8-jurw/")
        val info = result.getOrElse { error("Не удалось разобрать: $it") }

        println("=== РОЛИК: ${info.title}")
        println("=== ДЛИТЕЛЬНОСТЬ: ${info.durationSeconds}")
        info.formats.forEach { format ->
            println("    id=${format.id} label=${format.label} ${format.height}p url=${format.url?.take(90)}")
        }

        assertEquals(Source.INSTAGRAM, info.source)
        assertTrue("Форматов быть не должно", info.formats.isNotEmpty())
        assertTrue(
            "Ссылка должна вести на CDN Instagram",
            info.formats.all { it.url?.contains("cdninstagram.com") == true },
        )
    }

    @Test
    fun `DASH-формат ведёт на CDN и без экранирования амперсанда`() = runBlocking {
        assumeTrue("Нужен доступ в интернет", System.getProperty("turbofetch.network") == "true")

        val resolver = InstagramResolver()
        val info = resolver.resolve("https://www.instagram.com/p/Chunk8-jurw/")
            .getOrElse { error("Не удалось разобрать: $it") }

        val dash = info.formats.firstOrNull { it.id == "dash" }
        assertTrue("DASH-формата нет", dash != null)
        val url = dash!!.url.orEmpty()
        assertTrue("В URL осталось &amp;", !url.contains("&amp;"))
        assertTrue("Ссылка не на CDN", url.contains("cdninstagram.com"))
    }
}
