package com.turbofetch.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FileNameTemplateTest {

    @Test
    fun `подставляет все плейсхолдеры`() {
        val result = FileNameTemplate.render(
            template = "{title}_{author}_{quality}.{ext}",
            title = "Ролик",
            author = "Автор",
            quality = "1080p",
            extension = "mp4",
            id = "abc",
        )

        assertEquals("Ролик_Автор_1080p.mp4", result)
    }

    @Test
    fun `убирает недопустимые символы из имени`() {
        val result = FileNameTemplate.render(
            template = "{title}.{ext}",
            title = "a/b:c*d?e",
            author = null,
            quality = null,
            extension = "mp4",
            id = "id",
        )

        assertFalse(result.contains('/'))
        assertFalse(result.contains(':'))
        assertFalse(result.contains('*'))
        assertEquals("a_b_c_d_e.mp4", result)
    }

    @Test
    fun `пустой шаблон даёт имя из заголовка`() {
        val result = FileNameTemplate.render(
            template = "",
            title = "Название",
            author = null,
            quality = null,
            extension = "mp4",
            id = "id",
        )

        assertEquals("Название.mp4", result)
    }

    @Test
    fun `убирает висячее подчёркивание перед расширением`() {
        val result = FileNameTemplate.render(
            template = "{title}_{quality}.{ext}",
            title = "Ролик",
            author = null,
            quality = "",
            extension = "mp4",
            id = "id",
        )

        assertFalse(result.contains("_."))
        assertTrue(result.endsWith(".mp4"))
    }

    @Test
    fun `подставляет дату`() {
        val result = FileNameTemplate.render(
            template = "{date}_{id}.{ext}",
            title = null,
            author = null,
            quality = null,
            extension = "mp4",
            id = "zzz",
            timestamp = 1_700_000_000_000L,
        )

        assertTrue(result.contains("2023"))
        assertTrue(result.endsWith("_zzz.mp4"))
    }
}
