package com.turbofetch.data.resolver

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Живой тест резолвера TikTok. Включается флагом:
 * ./gradlew testDebugUnitTest -Dturbofetch.network=true
 */
class TikTokResolverSpikeTest {

    @Test
    fun `резолвер TikTok достаёт ролик`() = runBlocking {
        if (System.getProperty("turbofetch.network") != "true") return@runBlocking

        val resolver = TikTokResolver()
        val result = resolver.resolve("https://www.tiktok.com/@scout2015/video/6718335390845095173")

        println("TikTok: $result")
        assertTrue("Ролик не разобран: $result", result.isSuccess)
        val info = result.getOrThrow()
        assertTrue("Нет прямой ссылки", info.formats.isNotEmpty())
        assertTrue("Нет заголовков для CDN", info.formats.first().headers.isNotEmpty())
        assertTrue(
            "playAddr не найден",
            info.formats.first().url!!.contains("tiktok"),
        )
    }
}
