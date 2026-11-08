package com.turbofetch.data.resolver

import com.turbofetch.domain.model.PostProcess
import com.turbofetch.domain.model.Source
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

/**
 * Живая проверка резолвера X.
 *
 * Сеть включается явно: `./gradlew testDebugUnitTest -Dturbofetch.network=true`.
 */
class XResolverSpikeTest {

    @Test
    fun `разбирает пост с гифкой`() = runBlocking {
        assumeTrue("Нужен доступ в интернет", System.getProperty("turbofetch.network") == "true")

        val resolver = XResolver()
        val result = resolver.resolve("https://x.com/GIPHY/status/1044814076071817221")
        val info = result.getOrElse { error("Не удалось разобрать: $it") }

        info.formats.forEach { format ->
            println(
                "=== ФОРМАТ: ${format.id} | ${format.label} | mime=${format.mimeType} | " +
                    "postProcess=${format.postProcess} | url=${format.url?.take(60)}",
            )
        }

        assertEquals(Source.X, info.source)
        assertTrue("форматов нет", info.formats.isNotEmpty())

        // У GIF-поста площадка отдаёт mp4-конверт, но мы предлагаем
        // пользователю и настоящий GIF, и mp4 без звука.
        val gif = info.formats.firstOrNull { it.postProcess == PostProcess.TO_GIF }
        assertNotNull("варианта с GIF нет", gif)
        assertEquals("image/gif", gif!!.mimeType)
        assertTrue(
            "варианта mp4 без звука нет",
            info.formats.any { it.mimeType == "video/mp4" },
        )
    }
}
