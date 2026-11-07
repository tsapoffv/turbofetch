package com.turbofetch.data.resolver.instagram

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Разбор страницы Instagram без сети.
 *
 * Фикстура повторяет живую страницу: JSON лежит в блоках `<script data-sjs>`,
 * и в них же — публикации соседних постов того же аккаунта. Якорь по `pk`
 * обязателен, иначе разберётся чужой ролик.
 */
class InstagramCrawlerTest {

    private val ourPk = 2913440072144448240L

    @Test
    fun `находит нужную публикацию среди чужих`() {
        val post = InstagramCrawler.parse(PAGE, ourPk)

        assertNotNull("Публикация не найдена", post)
        assertEquals("Chunk8-jurw", post!!.code)
        assertEquals(1, post.items.size)
        assertTrue("Видео должно быть найдено", post.hasVideo)
    }

    @Test
    fun `достаёт progressive-ссылку`() {
        val item = InstagramCrawler.parse(PAGE, ourPk)!!.items.first()

        assertNotNull(item.progressive)
        assertTrue(
            "Ссылка не на CDN",
            item.progressive!!.url.contains("scontent.cdninstagram.com"),
        )
    }

    @Test
    fun `берёт лучшее представление из DASH и распаковывает амперсанд`() {
        val item = InstagramCrawler.parse(PAGE, ourPk)!!.items.first()

        assertNotNull("DASH не разобран", item.dash)
        val dash = item.dash!!
        assertEquals("Лучшее представление — 720×1280", 720, dash.quality)
        assertTrue("В URL осталось &amp;", !dash.url.contains("&amp;"))
        assertTrue("Должны быть оба параметра", dash.url.contains("a=1") && dash.url.contains("b=2"))
    }

    @Test
    fun `видит обложку и понимает отсутствие звука`() {
        val item = InstagramCrawler.parse(PAGE, ourPk)!!.items.first()

        assertTrue(item.thumbnailUrl!!.contains("thumb.jpg"))
        assertFalse("Звука у этой публикации нет", item.hasAudio)
    }

    @Test
    fun `пустая страница не даёт публикации`() {
        assertNull(InstagramCrawler.parse("<html><body>оболочка</body></html>", ourPk))
    }

    @Test
    fun `чужой pk не подходит`() {
        assertNull(InstagramCrawler.parse(PAGE, 42L))
    }

    @Test
    fun `длительность берётся из efg`() {
        // efg = base64 от {"duration_s":4}
        val url = "https://scontent.cdninstagram.com/v.mp4?efg=eyJ2ZW5jb2RlX3RhZyI6Inhwdl9wcm9ncmVzc2l2ZS5JTlNUQUdSQU0uQ0xJUFMuQzMuNDgwLmRhc2hfYmFzZWxpbmVfMl92MSIsInhwdl9hc3NldF9pZCI6MzcyMDk3NzA4NDI0NDE4LCJhc3NldF9hZ2VfZGF5cyI6MTUzMywidmlfdXNlY2FzZV9pZCI6MTAwOTksImR1cmF0aW9uX3MiOjQsInVybGdlbl9zb3VyY2UiOiJ3d3cifQ%3D%3D"
        assertEquals(4, InstagramCrawler.durationOf(url))
    }

    private companion object {
        val PAGE = """
            <html><body>
            <script type="text/javascript" data-sjs>
            {"__bbox":{"result":{"data":{"xig_polaris_media":{"pk":"111","code":"neighbor",
            "if_not_gated_logged_out":{"pk":"111","video_versions":[
            {"url":"https://scontent.cdninstagram.com/other.mp4","type":101}]}}}}}}
            </script>
            <script type="text/javascript" data-sjs>
            {"__bbox":{"result":{"data":{"xig_polaris_media":{"pk":"2913440072144448240",
            "code":"Chunk8-jurw","has_audio":false,
            "video_versions":[{"url":"https://scontent.cdninstagram.com/right.mp4?efg=abc","type":101,"bitrate":1476686}],
            "image_versions2":{"candidates":[{"url":"https://scontent.cdninstagram.com/thumb.jpg"}]},
            "video_dash_manifest":"<?xml version=\"1.0\"?><MPD><Representation width=\"720\" height=\"1280\" bandwidth=\"3000000\"><BaseURL>https://scontent.cdninstagram.com/dash.mp4?a=1&amp;b=2</BaseURL></Representation><Representation width=\"480\" height=\"854\" bandwidth=\"500000\"><BaseURL>https://scontent.cdninstagram.com/dash-low.mp4</BaseURL></Representation></MPD>"}}}}}
            </script>
            </body></html>
        """.trimIndent()
    }
}
