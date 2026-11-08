package com.turbofetch.data.media

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Проверки фильтра для GIF.
 *
 * Тут нет самого ffmpeg — проверяются правила, по которым он собирается.
 * Поведение (длительность анимации) проверялось на живой гифке из X.
 */
class GifFilterTest {

    @Test
    fun `кадры отбираются по времени, а не по номеру`() {
        val filter = GifFilter.build()
        assertTrue(
            "кадры должны отбираться по времени (prev_selected_t)",
            filter.contains("prev_selected_t"),
        )
        assertTrue(filter.contains("t-prev_selected_t"))
    }

    @Test
    fun `метки времени не переписываются`() {
        // setpts=N/(fps*TB) переписывает метки времени отобранных кадров:
        // анимация игралась бы вдвое быстрее исходника. Его быть не должно.
        val filter = GifFilter.build()
        assertFalse("setpts ломает длительность анимации", filter.contains("setpts"))
    }

    @Test
    fun `порог с запасом относительно точного 1 в fps`() {
        // При точном пороге 1/15 часть кадров терялась бы из-за округления.
        assertTrue(GifFilter.threshold(15) < 1.0 / 15)
        assertTrue(GifFilter.threshold(15) > 1.0 / 20)
    }

    @Test
    fun `в графе есть уменьшение кадра и палитра`() {
        val filter = GifFilter.build(width = 320)
        assertTrue(filter.contains("scale='min(320,iw)':-2"))
        assertTrue(filter.contains("palettegen=max_colors=128"))
        assertTrue(filter.contains("paletteuse=dither=bayer"))
    }
}
