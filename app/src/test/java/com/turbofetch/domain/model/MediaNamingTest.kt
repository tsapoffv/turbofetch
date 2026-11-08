package com.turbofetch.domain.model

import com.turbofetch.domain.model.MediaFormat
import com.turbofetch.domain.model.PostProcess
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Правила контейнера.
 *
 * Проверяем главное: YouTube-аудио по-прежнему mp3, а площадка, которая
 * сама отдаёт звук, сохраняется как есть — без лишнего перекодирования.
 */
class MediaNamingTest {

    private val videoFormat = MediaFormat(
        id = "720",
        label = "720p",
        height = 720,
        hasVideo = true,
        hasAudio = true,
    )

    private val audioFormat = MediaFormat(
        id = "aac_160k",
        label = "160 кбит/с",
        mimeType = "audio/mp4",
        hasVideo = false,
        hasAudio = true,
    )

    @Test
    fun `источник без видео считается аудио-источником`() {
        assertTrue(MediaNaming.isAudioOnlySource(listOf(audioFormat)))
        assertFalse(MediaNaming.isAudioOnlySource(listOf(videoFormat)))
        assertFalse(MediaNaming.isAudioOnlySource(listOf(videoFormat, audioFormat)))
        assertFalse(MediaNaming.isAudioOnlySource(emptyList()))
    }

    @Test
    fun `аудио-источник без просьбы о mp3 сохраняется как m4a`() {
        val container = MediaNaming.container(listOf(audioFormat), wantMp3 = false)

        assertEquals(MediaNaming.Container.M4A, container)
        assertEquals("m4a", MediaNaming.extension(container))
        assertEquals("audio/mp4", MediaNaming.mimeType(container))
        assertTrue(MediaNaming.isAudio(container))
    }

    @Test
    fun `аудио-источник с просьбой о mp3 конвертируется`() {
        val container = MediaNaming.container(listOf(audioFormat), wantMp3 = true)

        assertEquals(MediaNaming.Container.MP3, container)
        assertEquals("mp3", MediaNaming.extension(container))
        assertEquals("audio/mpeg", MediaNaming.mimeType(container))
    }

    @Test
    fun `YouTube-аудио остаётся mp3`() {
        // У YouTube есть и видеоформаты, но юзер попросил только аудио.
        val container = MediaNaming.container(listOf(videoFormat), wantMp3 = true)

        assertEquals(MediaNaming.Container.MP3, container)
    }

    @Test
    fun `видео остаётся видео`() {
        val container = MediaNaming.container(listOf(videoFormat), wantMp3 = false)

        assertEquals(MediaNaming.Container.VIDEO, container)
        assertEquals("mp4", MediaNaming.extension(container))
        assertFalse(MediaNaming.isAudio(container))
    }

    @Test
    fun `гифка сохраняется как gif`() {
        val gif = MediaFormat(
            id = "gif",
            label = "GIF (анимация)",
            mimeType = "image/gif",
            hasVideo = true,
            hasAudio = false,
            postProcess = PostProcess.TO_GIF,
        )
        val mp4 = MediaFormat(id = "mp4", label = "MP4 без звука", mimeType = "video/mp4")

        assertEquals(
            MediaNaming.Container.GIF,
            MediaNaming.container(listOf(gif, mp4), wantMp3 = false, selected = gif),
        )
        assertEquals(
            MediaNaming.Container.VIDEO,
            MediaNaming.container(listOf(gif, mp4), wantMp3 = false, selected = mp4),
        )
        assertEquals("gif", MediaNaming.extension(MediaNaming.Container.GIF))
        assertEquals("image/gif", MediaNaming.mimeType(MediaNaming.Container.GIF))
        assertTrue(MediaNaming.isImage(MediaNaming.Container.GIF))
        assertTrue(!MediaNaming.isAudio(MediaNaming.Container.GIF))
    }

    @Test
    fun `mp4 гифки не считается аудио`() {
        val formats = listOf(
            MediaFormat(id = "gif", label = "GIF", mimeType = "image/gif", postProcess = PostProcess.TO_GIF),
            MediaFormat(id = "mp4", label = "MP4", mimeType = "video/mp4"),
        )
        assertTrue(!MediaNaming.isAudioOnlySource(formats))
    }
}
