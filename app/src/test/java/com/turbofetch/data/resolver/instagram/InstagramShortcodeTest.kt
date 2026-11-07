package com.turbofetch.data.resolver.instagram

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Shortcode → числовой id.
 *
 * Значения взяты с живых страниц Instagram (7 ноября 2026): id публикации
 * лежит в JSON под ключом `pk`, а сам shortcode — это он же, но в base64.
 */
class InstagramShortcodeTest {

    @Test
    fun `переводит shortcode в id публикации`() {
        assertEquals(2913440072144448240L, InstagramShortcode.toMediaId("Chunk8-jurw"))
        assertEquals(482584233761418119L, InstagramShortcode.toMediaId("aye83DjauH"))
        assertEquals(1455920561485265648L, InstagramShortcode.toMediaId("BQ0eAlwhDrw"))
    }

    @Test
    fun `пустой или кривой shortcode не даёт id`() {
        assertEquals(-1L, InstagramShortcode.toMediaId(""))
        assertEquals(-1L, InstagramShortcode.toMediaId("не-корректно"))
        assertEquals(-1L, InstagramShortcode.toMediaId("with space"))
    }

    @Test
    fun `id помещается в Long`() {
        val id = InstagramShortcode.toMediaId("DQpBZN3vJqz")
        assertTrue("id получился отрицательным: $id", id > 0)
    }
}
