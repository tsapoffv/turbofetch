package com.turbofetch.domain.engine

import com.turbofetch.domain.link.LinkParser
import com.turbofetch.domain.model.MediaFormat
import com.turbofetch.domain.model.MediaInfo
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import java.io.File

/**
 * Имитация движка для этапа 1: реально пишет файл и честно отдаёт прогресс,
 * чтобы состояния UI (прогресс, ошибка, отмена) можно было проектировать и проверять.
 * Настоящий резолвер подключается на этапе 2 за тем же интерфейсом.
 */
class FakeEngine(
    private val delayPerStepMs: Long = 120,
    private val bytesTotal: Long = 2 * 1024 * 1024,
) : DownloadEngine {

    override suspend fun probe(url: String): Result<MediaInfo> {
        val parsed = LinkParser.parse(url)
            ?: return Result.failure(IllegalArgumentException("Не похоже на ссылку на видео"))
        delay(delayPerStepMs * 2)
        return Result.success(
            MediaInfo(
                source = parsed.source,
                title = "Демо-ролик ${parsed.id.take(8)}",
                author = "TurboFetch",
                durationSeconds = 42,
                thumbnailUrl = null,
                formats = listOf(
                    MediaFormat("2160", "2160p", height = 2160, approxBytes = 18_000_000),
                    MediaFormat("1080", "1080p", height = 1080, approxBytes = 8_000_000),
                    MediaFormat("720", "720p", height = 720, approxBytes = 4_000_000),
                    MediaFormat("480", "480p", height = 480, approxBytes = 2_000_000),
                    MediaFormat("360", "360p", height = 360, approxBytes = 1_000_000),
                    MediaFormat("140", "audio", height = 0, hasVideo = false, approxBytes = 700_000),
                ),
            ),
        )
    }

    override fun download(request: DownloadRequest): Flow<DownloadProgress> = flow {
        emit(DownloadProgress.Preparing)

        val total = bytesTotal
        request.outputFile.parentFile?.mkdirs()

        val written = request.outputFile.outputStream().use { out ->
            val chunk = ByteArray(64 * 1024)
            var written = 0L
            val steps = 20
            repeat(steps) { step ->
                val toWrite = minOf(chunk.size.toLong(), total - written).toInt()
                out.write(chunk, 0, toWrite)
                written += toWrite
                emit(DownloadProgress.Running(written, total))
                delay(delayPerStepMs)
            }
            written
        }

        emit(DownloadProgress.Finished(request.outputFile, written))
    }
}
