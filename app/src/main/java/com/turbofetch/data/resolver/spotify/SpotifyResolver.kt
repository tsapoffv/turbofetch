package com.turbofetch.data.resolver.spotify

import com.turbofetch.data.remote.HttpClientFactory
import com.turbofetch.domain.link.LinkParser
import com.turbofetch.domain.model.MediaFormat
import com.turbofetch.domain.model.MediaInfo
import com.turbofetch.domain.model.Source
import com.turbofetch.data.resolver.youtube.NewPipeInitializer
import com.turbofetch.domain.resolver.VideoResolver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.stream.StreamInfoItem

/**
 * Spotify: метаданные трека + сам трек с YouTube.
 *
 * Почему так: **сам Spotify отдаёт только зашифрованное аудио** (Widevine
 * DRM), вытащить из него играбельный файл обычным HTTP-запросом нельзя.
 * Зато Spotify отдаёт точные метаданные — название, артиста, длительность,
 * обложку. По ним трек ищется на YouTube, и уже оттуда берётся готовый
 * аудиопоток.
 *
 * Честность важнее удобства: если на YouTube трек не найден или найдено
 * несколько разных, в приложении будет сказано об этом, а не выдан
 * случайный ролик.
 *
 * Юридическая сторона: аудио берётся с YouTube, а не из Spotify, поэтому
 * ответственность площадки-источника не меняется.
 */
class SpotifyResolver(
    private val client: OkHttpClient = HttpClientFactory.create(),
) : VideoResolver {

    override val platform: Source = Source.SPOTIFY

    init {
        // Поиск трека идёт через NewPipeExtractor — он должен быть готов
        // заранее, независимо от порядка создания резолверов.
        NewPipeInitializer.ensure(client)
    }

    override suspend fun resolve(url: String): Result<MediaInfo> = withContext(Dispatchers.IO) {
        runCatching {
            val trackId = LinkParser.parse(url)?.id?.takeIf { it.isNotBlank() }
                ?: error("Не удалось извлечь идентификатор трека")

            // Альбом и плейлист — это не файл, а набор треков. Их разбираем
            // отдельно: возвращаем список, а юзер выберет нужное.
            collectionKindOf(url)?.let { kind ->
                val collection = SpotifyCollectionReader.read(client, trackId, kind)
                com.turbofetch.core.Log.i(
                    "Spotify: ${kind.name.lowercase()} «${collection.name}», " +
                        "треков ${collection.tracks.size}",
                )
                return@runCatching MediaInfo(
                    source = Source.SPOTIFY,
                    title = collection.name,
                    author = collection.subtitle,
                    durationSeconds = collection.tracks.sumOf { it.durationSeconds },
                    thumbnailUrl = collection.coverUrl,
                    formats = emptyList(),
                    collectionTracks = collection.tracks,
                )
            }

            val meta = SpotifyMetadata.fetch(client, trackId)
            com.turbofetch.core.Log.i(
                "Spotify: ${meta.artist} — ${meta.title} (${meta.durationSeconds ?: -1} с)",
            )

            val candidates = searchOnYouTube("${meta.artist} ${meta.title}")
            if (candidates.isEmpty()) {
                error("На YouTube ничего не нашлось по запросу «${meta.artist} ${meta.title}»")
            }

            val match = TrackMatcher.best(
                spotifyTitle = meta.title,
                spotifyArtist = meta.artist,
                spotifyDurationSeconds = meta.durationSeconds,
                candidates = candidates,
            ) ?: error(
                "На YouTube нет подходящего трека: «${meta.title}» не совпало ни с одним " +
                    "из ${candidates.size} найденных роликов",
            )

            com.turbofetch.core.Log.i(
                "Spotify: совпадение «${match.second.title}» от «${match.second.uploader}», " +
                    "балл ${"%.2f".format(match.first)}",
            )

            val streamInfo = StreamInfo.getInfo(ServiceList.YouTube, match.second.url)
            val audio = streamInfo.audioStreams.orEmpty()
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

            if (audio.isEmpty()) {
                error("У найденного ролика на YouTube нет аудиопотока")
            }

            MediaInfo(
                source = Source.SPOTIFY,
                title = meta.title,
                author = meta.artist,
                durationSeconds = meta.durationSeconds,
                thumbnailUrl = meta.coverUrl,
                formats = audio,
            )
        }
    }

    /** Вид коллекции по ссылке: альбом или плейлист. */
    private fun collectionKindOf(url: String): SpotifyCollectionKind? = when {
        url.contains("/album/") -> SpotifyCollectionKind.ALBUM
        url.contains("/playlist/") -> SpotifyCollectionKind.PLAYLIST
        else -> null
    }

    /**
     * Поиск на YouTube.
     *
     * Берём первые страницы результатов и оставляем только видео: каналы
     * и плейлисты треками не являются.
     */
    private fun searchOnYouTube(query: String): List<TrackCandidate> {
        val extractor = ServiceList.YouTube.getSearchExtractor(query)
        // Поиск YouTube сначала тянет «initial data» — без этого шага
        // getInitialPage() падает с «initialData is null».
        extractor.onFetchPage(NewPipe.getDownloader())
        val page = extractor.initialPage
        return page.items
            .filterIsInstance<StreamInfoItem>()
            .map { item ->
                TrackCandidate(
                    title = item.name.orEmpty(),
                    uploader = item.uploaderName.orEmpty(),
                    durationSeconds = item.duration,
                    url = item.url.orEmpty(),
                )
            }
            .filter { it.url.isNotBlank() }
    }
}
