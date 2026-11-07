package com.turbofetch.domain.link

import com.turbofetch.domain.model.Source
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LinkParserTest {

    @Test
    fun `разбирает обычную ссылку YouTube`() {
        val parsed = LinkParser.parse("https://www.youtube.com/watch?v=dQw4w9WgXcQ")

        assertEquals(Source.YOUTUBE, parsed?.source)
        assertEquals("dQw4w9WgXcQ", parsed?.id)
    }

    @Test
    fun `разбирает короткую ссылку youtu_be`() {
        val parsed = LinkParser.parse("https://youtu.be/abc123XYZ")

        assertEquals(Source.YOUTUBE, parsed?.source)
        assertEquals("abc123XYZ", parsed?.id)
    }

    @Test
    fun `разбирает ссылку на Shorts`() {
        val parsed = LinkParser.parse("https://www.youtube.com/shorts/shortsId42")

        assertEquals(Source.YOUTUBE, parsed?.source)
        assertEquals("shortsId42", parsed?.id)
    }

    @Test
    fun `разбирает ссылку TikTok`() {
        val parsed = LinkParser.parse("https://www.tiktok.com/@patroxofficial/video/6742501081818877190")

        assertEquals(Source.TIKTOK, parsed?.source)
        assertEquals("6742501081818877190", parsed?.id)
    }

    @Test
    fun `разбирает ссылку X`() {
        val parsed = LinkParser.parse("https://x.com/user/status/2052129757098295732")

        assertEquals(Source.X, parsed?.source)
        assertEquals("2052129757098295732", parsed?.id)
    }

    @Test
    fun `разбирает ссылку Instagram`() {
        val parsed = LinkParser.parse("https://www.instagram.com/p/Chunk8-jurw/")

        assertEquals(Source.INSTAGRAM, parsed?.source)
        assertEquals("Chunk8-jurw", parsed?.id)
    }

    @Test
    fun `разбирает ссылку на рилс Instagram`() {
        val parsed = LinkParser.parse("https://www.instagram.com/reel/Chunk8-jurw/")

        assertEquals(Source.INSTAGRAM, parsed?.source)
        assertEquals("Chunk8-jurw", parsed?.id)
    }

    @Test
    fun `разбирает короткую ссылку Instagram`() {
        val parsed = LinkParser.parse("https://instagr.am/p/Chunk8-jurw/")

        assertEquals(Source.INSTAGRAM, parsed?.source)
        assertEquals("Chunk8-jurw", parsed?.id)
    }

    @Test
    fun `разбирает ссылку SoundCloud`() {
        val parsed = LinkParser.parse("https://soundcloud.com/forss/flickermood")

        assertEquals(Source.SOUNDCLOUD, parsed?.source)
        assertEquals("flickermood", parsed?.id)
    }

    @Test
    fun `разбирает ссылку Spotify`() {
        val parsed = LinkParser.parse(
            "https://open.spotify.com/track/4cOdK2wGLETKBW3PvgPWqT",
        )

        assertEquals(Source.SPOTIFY, parsed?.source)
        assertEquals("4cOdK2wGLETKBW3PvgPWqT", parsed?.id)
    }

    @Test
    fun `разбирает ссылку Spotify с параметрами и локализацией`() {
        val parsed = LinkParser.parse(
            "https://open.spotify.com/intl-ru/track/4cOdK2wGLETKBW3PvgPWqT?si=abc123",
        )

        assertEquals(Source.SPOTIFY, parsed?.source)
        assertEquals("4cOdK2wGLETKBW3PvgPWqT", parsed?.id)
    }

    @Test
    fun `находит ссылку внутри текста`() {
        val parsed = LinkParser.parse("Смотри это https://youtu.be/xyz987 - прикольно")

        assertEquals(Source.YOUTUBE, parsed?.source)
        assertEquals("xyz987", parsed?.id)
    }

    @Test
    fun `неизвестная площадка не распознаётся`() {
        assertNull(LinkParser.parse("https://example.com/video/123"))
    }

    @Test
    fun `мусор не распознаётся`() {
        assertNull(LinkParser.parse("просто слова без ссылки"))
    }

    @Test
    fun `хвост после ссылки отбрасывается`() {
        val parsed = LinkParser.parse("https://youtu.be/qqq111")

        assertEquals("qqq111", parsed?.id)
    }
}
