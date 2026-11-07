package com.turbofetch.data.resolver.spotify

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Сопоставление трека Spotify с кандидатами YouTube.
 *
 * Офлайн-тест: сетевых запросов нет.
 */
class TrackMatcherTest {

    private fun candidate(
        title: String,
        uploader: String = "Various Artists",
        duration: Long = 214,
    ) = TrackCandidate(title, uploader, duration, "https://youtu.be/x")

    @Test
    fun `точное совпадение названия и артиста`() {
        val score = TrackMatcher.score(
            spotifyTitle = "Flickermood",
            spotifyArtist = "Forss",
            spotifyDurationSeconds = 214,
            candidate = candidate("Flickermood", uploader = "Forss - Topic"),
        )
        assertTrue("score=$score", score > 0.9)
    }

    @Test
    fun `служебные слова не мешают совпадению`() {
        val score = TrackMatcher.score(
            spotifyTitle = "Flickermood",
            spotifyArtist = "Forss",
            spotifyDurationSeconds = 214,
            candidate = candidate("Flickermood (Official Video)", uploader = "Forss"),
        )
        assertTrue("score=$score", score > 0.85)
    }

    @Test
    fun `другая длительность отсекается`() {
        val score = TrackMatcher.score(
            spotifyTitle = "Flickermood",
            spotifyArtist = "Forss",
            spotifyDurationSeconds = 214,
            candidate = candidate("Flickermood", uploader = "Forss", duration = 300),
        )
        assertEquals(0.0, score, 0.0001)
    }

    @Test
    fun `чужая песня не подходит`() {
        val score = TrackMatcher.score(
            spotifyTitle = "Flickermood",
            spotifyArtist = "Forss",
            spotifyDurationSeconds = 214,
            candidate = candidate("Completely Different Song", uploader = "Someone Else"),
        )
        assertTrue("score=$score", score < TrackMatcher.MIN_SCORE)
    }

    @Test
    fun `best выбирает наиболее похожего`() {
        val candidates = listOf(
            candidate("Flickermood (Lyrics)", uploader = "Random Channel", duration = 214),
            candidate("Flickermood", uploader = "Forss - Topic", duration = 214),
            candidate("Flickermood Remix 2 Hours", uploader = "DJ", duration = 400),
        )
        val best = TrackMatcher.best("Flickermood", "Forss", 214, candidates)
        assertNotNull(best)
        assertEquals("Forss - Topic", best!!.second.uploader)
    }

    @Test
    fun `best возвращает null когда совпадений нет`() {
        val candidates = listOf(candidate("Nothing Alike", uploader = "Other", duration = 100))
        assertNull(TrackMatcher.best("Flickermood", "Forss", 214, candidates))
    }

    @Test
    fun `нормализация убирает пунктуацию, регистр и служебные слова`() {
        assertEquals("flickermood", TrackMatcher.normalise("Flickermood!"))
        assertEquals("flickermood", TrackMatcher.normalise("Flickermood (Official Video)"))
    }

    @Test
    fun `пустое название не ломает подбор`() {
        val score = TrackMatcher.score(
            spotifyTitle = "",
            spotifyArtist = "",
            spotifyDurationSeconds = null,
            candidate = candidate(""),
        )
        assertTrue(score >= 0.0)
    }
}
