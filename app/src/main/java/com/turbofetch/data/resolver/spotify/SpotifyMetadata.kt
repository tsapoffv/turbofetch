package com.turbofetch.data.resolver.spotify

import com.turbofetch.domain.model.CollectionTrack
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.util.Base64
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * Общие мелочи разбора страниц Spotify.
 *
 * И embed-плеер, и обычная страница трека, и страницы альбома с плейлистом
 * устроены одинаково: HTML с одним блоком JSON. Вынесено отдельно, чтобы
 * разбор трека и разбор коллекции не расходились.
 */
internal object SpotifyPage {

    /**
     * Настольный User-Agent.
     *
     * С мобильным Spotify отдаёт другую страницу, где данных меньше.
     */
    const val USER_AGENT =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
            "(KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36"

    val json = Json { ignoreUnknownKeys = true; isLenient = true }

    /** Скрипт с JSON внутри embed-страницы. */
    val SCRIPT_JSON = Regex(
        """<script[^>]*id="(__NEXT_DATA__|__PRELOADED_STATE__)"[^>]*>(.*?)</script>""",
        setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE),
    )

    /** Скрипт с initialState обычной страницы трека (base64). */
    val INITIAL_STATE = Regex(
        """<script[^>]*id="initialState"[^>]*>(.*?)</script>""",
        setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE),
    )

    val OG_IMAGE = Regex("""<meta[^>]+property="og:image"[^>]+content="([^"]+)"""")

    /** Кусок JSON прямо в тексте страницы. */
    val JSON_FRAGMENT = Regex("""\{[^{}]*"name"\s*:\s*"[^"]*"[^{}]*\}""")

    /** Скачать страницу. */
    fun fetchText(
        client: OkHttpClient,
        url: String,
        userAgent: String = USER_AGENT,
    ): String {
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", userAgent)
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) error("Spotify вернул HTTP ${response.code}")
            return response.body?.string().orEmpty()
        }
    }

    /** Разбор `<script id="__NEXT_DATA__">`. */
    fun parseNextData(html: String): JsonObject? {
        val match = SCRIPT_JSON.find(html) ?: return null
        val payload = match.groupValues[2].trim()
        if (payload.isEmpty()) return null
        return runCatching { json.parseToJsonElement(payload).jsonObject }.getOrNull()
    }

    /** Объект `props.pageProps.state.data.entity` — трек или коллекция. */
    fun entityOf(tree: JsonObject): JsonObject? =
        tree["props"]?.jsonObject?.get("pageProps")?.jsonObject
            ?.get("state")?.jsonObject?.get("data")?.jsonObject
            ?.get("entity")?.jsonObject

    /**
     * Обложка.
     *
     * В embed-странице она лежит в `visualIdentity.image[]` (варианты
     * 64/300/640 пикселей — берём самый большой), у плейлиста — в
     * `coverArt.sources[]`, в старой разметке — в `images[]`.
     */
    fun coverOf(node: JsonObject): String? {
        node["visualIdentity"]?.let { identity ->
            val images = runCatching { identity.jsonObject["image"]?.jsonArray }.getOrNull()
            val best = images
                ?.mapNotNull { image ->
                    val url = (image.jsonObject["url"] as? JsonPrimitive)?.content
                    val size = (image.jsonObject["maxHeight"] as? JsonPrimitive)
                        ?.content?.toIntOrNull() ?: 0
                    url?.let { size to it }
                }
                ?.maxByOrNull { it.first }
            if (best != null) return best.second
        }
        node["coverArt"]?.let { coverArt ->
            val sources = runCatching { coverArt.jsonObject["sources"]?.jsonArray }.getOrNull()
            val url = sources?.firstNotNullOfOrNull { source ->
                (source.jsonObject["url"] as? JsonPrimitive)?.content
            }
            if (url != null) return url
        }
        node["images"]?.let { images ->
            val array = runCatching { images.jsonArray }.getOrNull() ?: return null
            val url = array.firstNotNullOfOrNull { image ->
                (image.jsonObject["url"] as? JsonPrimitive)?.content
            }
            if (url != null) return url
        }
        node["album"]?.let { album ->
            if (album is JsonObject) return coverOf(album)
        }
        return null
    }
}

/** Метаданные трека Spotify. */
data class SpotifyTrack(
    val id: String,
    val title: String,
    val artist: String,
    val album: String?,
    val durationSeconds: Int?,
    val coverUrl: String?,
)

/**
 * Метаданные Spotify без авторизации.
 *
 * **Аудио Spotify недоступно** — оно зашифровано (Widevine), и обычный
 * HTTP-запрос получает только шифротекст. Но метаданные площадка отдаёт
 * открыто, и их достаточно, чтобы найти трек на YouTube.
 *
 * Источники (берём первый, кто ответил):
 *
 * 1. `open.spotify.com/embed/track/<id>` — страница с JSON внутри;
 * 2. `open.spotify.com/track/<id>` — но только с небраузерным User-Agent;
 * 3. `open.spotify.com/oembed?url=...` — простое JSON: название и обложка.
 */
object SpotifyMetadata {

    /**
     * Достаёт метаданные трека.
     *
     * @throws IllegalStateException если ни один источник не дал данных.
     */
    suspend fun fetch(client: OkHttpClient, trackId: String): SpotifyTrack {
        val fromEmbed = runCatching { fetchFromEmbed(client, trackId) }.getOrNull()
        val fromPage = if (fromEmbed == null) {
            runCatching { fetchFromTrackPage(client, trackId) }.getOrNull()
        } else {
            null
        }
        val fromOembed = runCatching { fetchFromOembed(client, trackId) }.getOrNull()

        val track = fromEmbed ?: fromPage ?: fromOembed
            ?: error(
                "Spotify не отдал метаданные трека: возможно, он удалён, " +
                    "недоступен в вашем регионе или это не ссылка на трек",
            )

        // Дополняем то, чего не хватило в основном источнике.
        return track.copy(
            title = track.title.ifBlank { fromOembed?.title.orEmpty() },
            artist = track.artist.ifBlank { fromOembed?.artist.orEmpty() },
            coverUrl = track.coverUrl ?: fromOembed?.coverUrl,
            durationSeconds = track.durationSeconds ?: fromOembed?.durationSeconds,
        )
    }

    /** Метаданные со страницы плеера. */
    private fun fetchFromEmbed(client: OkHttpClient, trackId: String): SpotifyTrack? {
        val html = SpotifyPage.fetchText(client, "https://open.spotify.com/embed/track/$trackId")
        val tree = SpotifyPage.parseNextData(html)
        val entity = tree?.let { SpotifyPage.entityOf(it) }
        if (entity != null) {
            val title = stringOf(entity, "name") ?: stringOf(entity, "title")
            if (!title.isNullOrBlank()) {
                return SpotifyTrack(
                    id = trackId,
                    title = title,
                    artist = artistOf(entity).orEmpty(),
                    album = albumOf(entity),
                    durationSeconds = durationOf(entity)?.let { (it / 1000).toInt() },
                    coverUrl = SpotifyPage.coverOf(entity) ?: ogImage(html),
                )
            }
        }
        // Вне скриптов могли остаться куски JSON — попробуем найти объект
        // прямо в тексте страницы.
        val loose = findTrackInText(html)
        return loose?.copy(
            id = trackId,
            coverUrl = loose.coverUrl ?: ogImage(html),
        )
    }

    /**
     * Запасной источник: обычная страница трека.
     *
     * Там данные лежат в `<script id="initialState">` — это base64, внутри
     * JSON с объектом трека по ключу `spotify:track:<id>`.
     *
     * Страница отдаёт данные только небраузерным клиентам: с обычным
     * Chrome User-Agent приходит оболочка плеера без метаданных, поэтому
     * запрашиваем заведомо «небраузерным» агентом.
     */
    private fun fetchFromTrackPage(client: OkHttpClient, trackId: String): SpotifyTrack? {
        val html = SpotifyPage.fetchText(
            client,
            "https://open.spotify.com/track/$trackId",
            userAgent = "curl/8.5.0",
        )
        val encoded = SpotifyPage.INITIAL_STATE.find(html)?.groupValues?.get(1)?.trim().orEmpty()
        if (encoded.isEmpty()) return null

        val decoded = runCatching {
            String(Base64.getDecoder().decode(encoded), Charsets.UTF_8)
        }.getOrNull() ?: return null

        val tree = runCatching {
            SpotifyPage.json.parseToJsonElement(decoded)
        }.getOrNull() ?: return null
        val entity = findByKey(tree, "spotify:track:$trackId") ?: return null

        val name = stringOf(entity, "name") ?: return null
        return SpotifyTrack(
            id = trackId,
            title = name,
            artist = artistOf(entity).orEmpty(),
            album = albumOf(entity),
            durationSeconds = durationOf(entity)?.let { (it / 1000).toInt() },
            coverUrl = SpotifyPage.coverOf(entity),
        )
    }

    /** Метаданные через oEmbed. */
    private fun fetchFromOembed(
        client: OkHttpClient,
        trackId: String,
    ): SpotifyTrack? {
        val url = "https://open.spotify.com/oembed?url=" +
            "https%3A%2F%2Fopen.spotify.com%2Ftrack%2F$trackId"
        val body = SpotifyPage.fetchText(client, url)
        val root = runCatching {
            SpotifyPage.json.parseToJsonElement(body).jsonObject
        }.getOrNull() ?: return null

        val title = root["title"]?.jsonPrimitive?.content.orEmpty()
        if (title.isBlank()) return null

        // В oEmbed название обычно вида «Трек · Артист».
        val parts = title.split(" · ")
        return SpotifyTrack(
            id = trackId,
            title = parts.firstOrNull().orEmpty().ifBlank { title },
            artist = parts.drop(1).joinToString(" · "),
            album = null,
            durationSeconds = null,
            coverUrl = root["thumbnail_url"]?.jsonPrimitive?.content,
        )
    }

    /**
     * Рекурсивный поиск объекта трека в JSON-дереве.
     *
     * Признак трека: есть строковое имя и что-то из «duration»/«artists».
     */
    private fun findTrack(node: kotlinx.serialization.json.JsonElement): SpotifyTrack? {
        when (node) {
            is JsonObject -> {
                val title = stringOf(node, "name") ?: stringOf(node, "title")
                val duration = durationOf(node)
                val artists = artistOf(node)
                if (title != null && (duration != null || artists != null)) {
                    return SpotifyTrack(
                        id = stringOf(node, "uri")?.substringAfterLast(':').orEmpty(),
                        title = title,
                        artist = artists.orEmpty(),
                        album = albumOf(node),
                        durationSeconds = duration?.let { (it / 1000).toInt() },
                        coverUrl = SpotifyPage.coverOf(node),
                    )
                }
                node.values.forEach { value ->
                    findTrack(value)?.let { return it }
                }
            }

            is JsonArray -> {
                node.forEach { element ->
                    findTrack(element)?.let { return it }
                }
            }

            else -> Unit
        }
        return null
    }

    /** Тот же поиск, но по сырому тексту страницы. */
    private fun findTrackInText(html: String): SpotifyTrack? {
        for (fragment in SpotifyPage.JSON_FRAGMENT.findAll(html)) {
            val tree = runCatching {
                SpotifyPage.json.parseToJsonElement(fragment.value)
            }.getOrNull() ?: continue
            findTrack(tree)?.let { return it }
        }
        return null
    }

    /** Ищет объект по ключу в JSON-дереве (например, `spotify:track:<id>`). */
    private fun findByKey(
        node: kotlinx.serialization.json.JsonElement,
        key: String,
    ): JsonObject? {
        when (node) {
            is JsonObject -> {
                node[key]?.let { value ->
                    if (value is JsonObject) return value
                }
                node.values.forEach { value ->
                    findByKey(value, key)?.let { return it }
                }
            }

            is JsonArray -> {
                node.forEach { element ->
                    findByKey(element, key)?.let { return it }
                }
            }

            else -> Unit
        }
        return null
    }

    private fun stringOf(node: JsonObject, key: String): String? =
        (node[key] as? JsonPrimitive)?.takeIf { it.isString }?.content

    private fun longOf(node: JsonObject, key: String): Long? =
        (node[key] as? JsonPrimitive)?.content?.toLongOrNull()

    /**
     * Длительность в миллисекундах.
     *
     * В embed это число, в initialState — объект `{totalMilliseconds: N}`.
     */
    private fun durationOf(node: JsonObject): Long? {
        longOf(node, "duration")?.let { return it }
        val duration = node["duration"] as? JsonObject ?: return null
        return longOf(duration, "totalMilliseconds")
    }

    /** Имя артиста: массив объектов с `name` либо строка. */
    private fun artistOf(node: JsonObject): String? {
        val artists = node["artists"] ?: return null
        (artists as? JsonArray)?.let { array ->
            val names = array.mapNotNull { item ->
                when (item) {
                    is JsonPrimitive -> item.content
                    is JsonObject -> (item["name"] as? JsonPrimitive)?.content
                    else -> null
                }
            }.filter { it.isNotBlank() }
            if (names.isNotEmpty()) return names.joinToString(", ")
        }
        if (artists is JsonPrimitive) return artists.content

        // В initialState (обычная страница трека) артисты лежат иначе:
        // firstArtist.items[0].profile.name и otherArtist.items[].
        node["firstArtist"]?.let { first ->
            val name = runCatching {
                first.jsonObject["items"]?.jsonArray?.firstNotNullOfOrNull { item ->
                    (item.jsonObject["profile"]?.jsonObject?.get("name") as? JsonPrimitive)?.content
                }
            }.getOrNull()
            if (!name.isNullOrBlank()) return name
        }
        node["otherArtist"]?.let { other ->
            val name = runCatching {
                other.jsonObject["items"]?.jsonArray?.firstNotNullOfOrNull { item ->
                    (item.jsonObject["profile"]?.jsonObject?.get("name") as? JsonPrimitive)?.content
                }
            }.getOrNull()
            if (!name.isNullOrBlank()) return name
        }
        return null
    }

    private fun albumOf(node: JsonObject): String? {
        val album = node["album"] ?: node["albumOfTrack"] ?: return null
        if (album is JsonPrimitive) return album.content
        if (album is JsonObject) {
            return (album["name"] as? JsonPrimitive)?.content
        }
        return null
    }

    /**
     * Обложка трека.
     *
     * В списке плейлиста обложек нет — площадка отдаёт их только на
     * странице самого трека. Здесь она и берётся.
     */
    suspend fun trackCover(client: OkHttpClient, trackId: String): String? = runCatching {
        val html = SpotifyPage.fetchText(client, "https://open.spotify.com/embed/track/$trackId")
        SpotifyPage.parseNextData(html)
            ?.let { SpotifyPage.entityOf(it) }
            ?.let { SpotifyPage.coverOf(it) }
    }.onFailure { error ->
        com.turbofetch.core.Log.e("Обложка трека $trackId: ${error.message}")
    }.getOrNull()

    /** Картинка из og:image — запасной вариант. */
    private fun ogImage(html: String): String? =
        SpotifyPage.OG_IMAGE.find(html)?.groupValues?.get(1)?.takeIf { it.isNotBlank() }
}
