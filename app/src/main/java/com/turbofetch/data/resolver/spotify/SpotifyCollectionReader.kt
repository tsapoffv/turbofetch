package com.turbofetch.data.resolver.spotify

import com.turbofetch.domain.model.CollectionTrack
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient

/** Тип коллекции Spotify: альбом или плейлист. */
enum class SpotifyCollectionKind(val path: String) {
    ALBUM("album"),
    PLAYLIST("playlist"),
}

/** Альбом или плейлист Spotify: набор треков. */
data class SpotifyCollection(
    val kind: SpotifyCollectionKind,
    val name: String,
    /** Артист альбома или владелец плейлиста. */
    val subtitle: String?,
    val coverUrl: String?,
    val tracks: List<CollectionTrack>,
    /**
     * Список обрезан: площадка отдала только первые 50 треков плейлиста,
     * пагинации в embed-странице нет.
     */
    val truncated: Boolean,
)

/**
 * Чтение альбомов и плейлистов Spotify.
 *
 * Список треков лежит в `trackList` embed-страницы: у альбома их 10–20,
 * у плейлиста — первые 50 (дальше Spotify без пагинации не отдаёт).
 */
object SpotifyCollectionReader {

    /** Читает альбом или плейлист. */
    suspend fun read(
        client: OkHttpClient,
        id: String,
        kind: SpotifyCollectionKind,
    ): SpotifyCollection {
        val html = SpotifyPage.fetchText(client, "https://open.spotify.com/embed/${kind.path}/$id")
        val tree = SpotifyPage.parseNextData(html)
            ?: error("Spotify не отдал данные: альбом или плейлист не найден")
        val entity = SpotifyPage.entityOf(tree)
            ?: error("Spotify не отдал список треков: альбом или плейлист не найден")

        val rawTracks = entity["trackList"]?.jsonArray.orEmpty()
        val tracks = rawTracks.mapNotNull { item ->
            val obj = item.jsonObject
            val trackId = obj["uri"]?.jsonPrimitive?.content
                ?.substringAfter("spotify:track:")
                ?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
            CollectionTrack(
                id = trackId,
                title = obj["title"]?.jsonPrimitive?.content.orEmpty(),
                artist = obj["subtitle"]?.jsonPrimitive?.content.orEmpty(),
                durationSeconds = (obj["duration"]?.jsonPrimitive?.content?.toLongOrNull()
                    ?: 0L).toInt() / 1000,
            )
        }

        val cover = SpotifyPage.coverOf(entity)

        // У треков в списке обложки нет — берём её с их собственных страниц.
        // Три запроса по 10 КБ — цена трёх обложек в карточке.
        val withCovers = if (kind == SpotifyCollectionKind.PLAYLIST) {
            tracks.take(3).map { track ->
                track.copy(thumbUrl = SpotifyMetadata.trackCover(client, track.id) ?: cover)
            } + tracks.drop(3)
        } else {
            tracks
        }

        return SpotifyCollection(
            kind = kind,
            name = entity["name"]?.jsonPrimitive?.content.orEmpty(),
            subtitle = entity["subtitle"]?.jsonPrimitive?.content,
            coverUrl = cover,
            tracks = withCovers,
            truncated = kind == SpotifyCollectionKind.PLAYLIST && tracks.size >= 50,
        )
    }

}
