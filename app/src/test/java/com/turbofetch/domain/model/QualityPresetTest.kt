package com.turbofetch.domain.model

import org.junit.Assert.assertEquals
import org.junit.Test

class QualityPresetTest {

    private val formats = listOf(
        MediaFormat("2160", "2160p", height = 2160),
        MediaFormat("1080", "1080p", height = 1080),
        MediaFormat("720", "720p", height = 720),
        MediaFormat("360", "360p", height = 360),
    )

    @Test
    fun `пресет Лучшее берёт максимум`() {
        assertEquals("2160", QualityPreset.pick(formats, QualityPreset.BEST)?.id)
    }

    @Test
    fun `пресет 720 берёт ровно 720`() {
        assertEquals("720", QualityPreset.pick(formats, QualityPreset.Q720)?.id)
    }

    @Test
    fun `когда точного качества нет берётся ближайшее поменьше`() {
        val without720 = listOf(
            MediaFormat("1080", "1080p", height = 1080),
            MediaFormat("360", "360p", height = 360),
        )

        assertEquals("360", QualityPreset.pick(without720, QualityPreset.Q720)?.id)
    }

    @Test
    fun `когда ничего не подходит берётся минимальное`() {
        val only4k = listOf(MediaFormat("2160", "2160p", height = 2160))

        assertEquals("2160", QualityPreset.pick(only4k, QualityPreset.Q360)?.id)
    }

    @Test
    fun `аудиоформаты не попадают в выбор видео`() {
        val withAudio = formats + MediaFormat("140", "audio", hasVideo = false)

        assertEquals("2160", QualityPreset.pick(withAudio, QualityPreset.BEST)?.id)
    }
}
