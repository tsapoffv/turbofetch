package com.turbofetch.data.storage

import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import com.turbofetch.core.Log
import java.io.File
import java.nio.ByteBuffer

/**
 * Склеивает видео и звук в один MP4 без перекодирования.
 *
 * Зачем: YouTube отдаёт видео и звук отдельными потоками (DASH). Файл с одним
 * только видео не воспроизводится — нужен общий контейнер.
 *
 * Это ровно то, что делает `ffmpeg -c copy`, но на штатных API Android:
 * `MediaExtractor` читает дорожки из исходников, `MediaMuxer` собирает их
 * в новый контейнер. Код не перекодируется, качество не теряется,
 * дополнительных зависимостей нет.
 */
class MediaMuxerMerger {

    /**
     * @param videoFile файл с видеодорожкой (и, возможно, звуком)
     * @param audioFile файл с аудиодорожкой; если `null` — звук уже внутри videoFile
     * @param outputFile результат
     */
    fun merge(videoFile: File, audioFile: File?, outputFile: File) {
        val videoExtractor = MediaExtractor()
        val audioExtractor = MediaExtractor()
        val muxer = MediaMuxer(outputFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)

        try {
            videoExtractor.setDataSource(videoFile.absolutePath)
            val videoTrack = videoExtractor.findVideoTrack()
                ?: error("В видеофайле нет видеодорожки")

            val audioFromVideo = audioFile == null
            val audioTrack: Int
            if (audioFromVideo) {
                audioTrack = videoExtractor.findAudioTrack()
                    ?: error("В видеофайле нет аудиодорожки")
            } else {
                audioExtractor.setDataSource(audioFile.absolutePath)
                audioTrack = audioExtractor.findAudioTrack()
                    ?: error("В аудиофайле нет аудиодорожки")
            }

            val muxVideoTrack = muxer.addTrack(videoExtractor.getTrackFormat(videoTrack))
            val muxAudioTrack = muxer.addTrack(
                if (audioFromVideo) videoExtractor.getTrackFormat(audioTrack)
                else audioExtractor.getTrackFormat(audioTrack),
            )

            muxer.start()

            copySamples(videoExtractor, videoTrack, muxer, muxVideoTrack)

            val source = if (audioFromVideo) videoExtractor else audioExtractor
            copySamples(source, audioTrack, muxer, muxAudioTrack)
        } finally {
            runCatching { muxer.stop() }
            muxer.release()
            videoExtractor.release()
            audioExtractor.release()
        }

        Log.i("Склейка готова: ${outputFile.name}, ${outputFile.length()} байт")
    }

    /** Ищет первую дорожку нужного типа. */
    private fun MediaExtractor.findVideoTrack(): Int? =
        findTrack { mime -> mime.startsWith("video/") }

    private fun MediaExtractor.findAudioTrack(): Int? =
        findTrack { mime -> mime.startsWith("audio/") }

    private inline fun MediaExtractor.findTrack(predicate: (String) -> Boolean): Int? {
        for (index in 0 until trackCount) {
            val mime = getTrackFormat(index).getString(MediaFormat.KEY_MIME).orEmpty()
            if (predicate(mime)) return index
        }
        return null
    }

    /** Переписывает сэмплы одной дорожки в контейнер, ничего не перекодируя. */
    private fun copySamples(
        extractor: MediaExtractor,
        trackIndex: Int,
        muxer: MediaMuxer,
        muxTrack: Int,
    ) {
        extractor.selectTrack(trackIndex)
        val buffer = ByteBuffer.allocate(MAX_SAMPLE_SIZE)
        val info = MediaCodec.BufferInfo()

        while (true) {
            val size = extractor.readSampleData(buffer, 0)
            if (size < 0) break

            info.offset = 0
            info.size = size
            info.presentationTimeUs = extractor.sampleTime
            info.flags = extractor.sampleFlags

            muxer.writeSampleData(muxTrack, buffer, info)
            extractor.advance()
        }
        extractor.unselectTrack(trackIndex)
    }

    private companion object {
        /** Запас на размер сэмпла: 4K-кадр в худшем случае около 10 МБ. */
        const val MAX_SAMPLE_SIZE = 12 * 1024 * 1024
    }
}
