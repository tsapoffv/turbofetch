package com.turbofetch.data.resolver.youtube

import com.turbofetch.domain.model.Source
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

/**
 * Живая проверка резолвера YouTube. Требует сеть, поэтому по умолчанию пропускается.
 * Запуск: `./gradlew testDebugUnitTest --tests '*YouTubeResolverSpikeTest' -Dturbofetch.network=true`
 */
class YouTubeResolverSpikeTest {

    @Test
    fun `разбирает настоящий ролик YouTube`() = runBlocking {
        assumeTrue("Нужен доступ в интернет", System.getProperty("turbofetch.network") == "true")

        val resolver = YouTubeResolver()
        val result = resolver.resolve("https://www.youtube.com/watch?v=dQw4w9WgXcQ")

        val info = result.getOrElse { error -> error("Не удалось разобрать: $error") }

        println("=== РОЛИК: ${info.title}")
        println("=== АВТОР: ${info.author}, длительность ${info.durationSeconds} с")
        println("=== ПРЕВЬЮ: ${info.thumbnailUrl}")
        println("=== ФОРМАТОВ: ${info.formats.size}")
        info.formats.forEach { format ->
            println(
                "    id=${format.id}  label=${format.label}  h=${format.height}  " +
                    "mime=${format.mimeType}  url=${format.url?.take(70)}",
            )
        }

        assertEquals(Source.YOUTUBE, info.source)
        assertTrue("Форматов быть не должно", info.formats.isNotEmpty())
    }
}
