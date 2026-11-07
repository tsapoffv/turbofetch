package com.turbofetch.data.resolver.youtube

import com.turbofetch.data.remote.HttpClientFactory
import com.turbofetch.domain.link.LinkParser
import com.turbofetch.domain.model.MediaFormat
import com.turbofetch.domain.model.MediaInfo
import com.turbofetch.domain.model.Source
import com.turbofetch.domain.resolver.VideoResolver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.stream.StreamInfo

/**
 * Резолвер YouTube на базе NewPipeExtractor.
 *
 * Извлечение целиком на устройстве: ни Python, ни JS-рантайм, ни сервер.
 *
 * Про качество: без PO-токена YouTube отдаёт только муксированные потоки
 * (видео+звук в одном файле), и их выборка обычно ограничена 360p.
 * Потоки `videoOnly` (1080p и выше) — это DASH без звука: чтобы получить такой
 * файл, видео и аудио надо склеить, а для склейки нужен ffmpeg.
 */
class YouTubeResolver(
    private val client: OkHttpClient = HttpClientFactory.create(),
) : VideoResolver {

    override val platform: Source = Source.YOUTUBE

    init {
        NewPipeInitializer.ensure(client)
    }

    override suspend fun resolve(url: String): Result<MediaInfo> = withContext(Dispatchers.IO) {
        runCatching {
            val id = LinkParser.parse(url)?.id?.takeIf { it.isNotBlank() }
                ?: error("Не удалось извлечь идентификатор ролика")

            // Экстрактору нужен сам URL: по нему он строит link handler
            // (короткие ссылки youtu.be он тоже понимает).
            val info = StreamInfo.getInfo(ServiceList.YouTube, url)

            val muxed = info.videoStreams.orEmpty().map { stream ->
                MediaFormat(
                    id = "muxed-${stream.itag}",
                    label = stream.resolution ?: stream.quality ?: "видео",
                    height = stream.resolution?.substringBefore('p')?.toIntOrNull() ?: 0,
                    bitrate = stream.bitrate,
                    mimeType = stream.format?.mimeType.orEmpty(),
                    url = stream.url,
                    hasAudio = true,
                    hasVideo = true,
                )
            }

            // Оставляем только MP4: склейка идёт через MediaMuxer, а он умеет
            // только контейнер MPEG-4. WebM/VP9 в MP4 не записать.
            val videoOnly = info.videoOnlyStreams.orEmpty()
                .filter { it.format?.mimeType?.contains("mp4") == true }
                .map { stream ->
                    MediaFormat(
                        id = "vonly-${stream.itag}",
                        label = stream.resolution ?: stream.quality ?: "видео",
                        height = stream.resolution?.substringBefore('p')?.toIntOrNull() ?: 0,
                        bitrate = stream.bitrate,
                        mimeType = stream.format?.mimeType.orEmpty(),
                        url = stream.url,
                        hasAudio = false,
                        hasVideo = true,
                    )
                }

            val audio = info.audioStreams.orEmpty()
                .filter { it.format?.mimeType?.contains("mp4") == true }
                .map { stream ->
                    MediaFormat(
                        id = "audio-${stream.itag}",
                        label = "аудио ${stream.bitrate / 1000} кбит/с",
                        bitrate = stream.bitrate,
                        mimeType = stream.format?.mimeType.orEmpty(),
                        url = stream.url,
                        hasAudio = true,
                        hasVideo = false,
                    )
                }

            val formats = muxed + videoOnly + audio
            if (formats.isEmpty()) {
                error("YouTube не вернул ни одного формата")
            }

            com.turbofetch.core.Log.i(
                "YouTube «${info.name}»: муксированных ${muxed.size}, " +
                    "video-only ${videoOnly.size}, аудио ${audio.size}",
            )

            MediaInfo(
                source = Source.YOUTUBE,
                title = info.name ?: "YouTube",
                author = info.uploaderName,
                durationSeconds = info.duration.toInt().takeIf { it > 0 },
                thumbnailUrl = info.thumbnails?.maxByOrNull { it.height }?.url,
                formats = formats,
            )
        }
    }
}
