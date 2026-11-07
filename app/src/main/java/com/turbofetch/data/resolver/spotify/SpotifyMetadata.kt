package com.turbofetch.data.resolver.spotify

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
 * 2. `open.spotify.com/oembed?url=...` — простой JSON: название и обложка.
 *
 * Разбор embed-страницы сделан рекурсивным поиском по JSON-дереву, а не
 * по жёстким путям: разметка страницы меняется, и зацепка за конкретный
 * путь ломается первая.
 */
object SpotifyMetadata {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    /** Скрипты, внутри которых может лежать JSON с данными трека. */
    /** Скрипт с initialState обычной страницы трека (base64). */
    private val INITIAL_STATE = Regex(
        """<script[^>]*id="initialState"[^>]*>(.*?)</script>""",
        setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE),
    )

    private val SCRIPT_JSON = Regex(
        """<script[^>]*id="(__NEXT_DATA__|__PRELOADED_STATE__)"[^>]*>(.*?)</script>""",
        setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE),
    )

    /**
     * Достаёт метаданные трека.
     *
     * @throws IllegalStateException если ни один источник не дал данных.
     */
    suspend fun fetch(client: OkHttpClient, trackId: String): SpotifyTrack {
        // Порядок источников: embed (полные данные) → обычная страница
        // трека → oEmbed (только название и обложка).
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
        val html = fetchText(
            client,
            "https://open.spotify.com/track/$trackId",
            userAgent = "curl/8.5.0",
        )
        val encoded = INITIAL_STATE.find(html)?.groupValues?.get(1)?.trim().orEmpty()
        if (encoded.isEmpty()) return null

        val decoded = runCatching {
            String(Base64.getDecoder().decode(encoded), Charsets.UTF_8)
        }.getOrNull() ?: return null

        val tree = runCatching { json.parseToJsonElement(decoded) }.getOrNull() ?: return null
        val entity = findByKey(tree, "spotify:track:$trackId") ?: return null

        val name = stringOf(entity, "name") ?: return null
        return SpotifyTrack(
            id = trackId,
            title = name,
            artist = artistOf(entity).orEmpty(),
            album = albumOf(entity),
            durationSeconds = durationOf(entity)?.let { (it / 1000).toInt() },
            coverUrl = coverOf(entity),
        )
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

    /** Метаданные со страницы плеера. */
    private fun fetchFromEmbed(client: OkHttpClient, trackId: String): SpotifyTrack? {
        val html = fetchText(client, "https://open.spotify.com/embed/track/$trackId")
        for (match in SCRIPT_JSON.findAll(html)) {
            val payload = match.groupValues[2].trim()
            if (payload.isEmpty()) continue
            val tree = runCatching { json.parseToJsonElement(payload) }.getOrNull() ?: continue
            val found = findTrack(tree) ?: continue
            if (found.title.isNotBlank()) {
                return found.copy(
                    id = trackId,
                    coverUrl = found.coverUrl ?: ogImage(html),
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

    /** Метаданные через oEmbed. */
    private fun fetchFromOembed(
        client: OkHttpClient,
        trackId: String,
    ): SpotifyTrack? {
        val url = "https://open.spotify.com/oembed?url=" +
            "https%3A%2F%2Fopen.spotify.com%2Ftrack%2F$trackId"
        val body = fetchText(client, url)
        val root = runCatching { json.parseToJsonElement(body).jsonObject }.getOrNull() ?: return null

        val title = root["title"]?.jsonPrimitive?.content.orEmpty()
        if (title.isBlank()) return null

        // В oEmbed название обычно вида «Трек · Артист».
        val parts = title.split(" · ")
        return SpotifyTrack(
            id = trackId,
            title = parts.firstOrNull().orEmpty().ifBlank { title },
            artist = parts.drop(1).joinToString(" · ").ifBlank { "" },
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
                        coverUrl = coverOf(node),
                    )
                }
                // Дальше ищем глубже.
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
        for (fragment in JSON_FRAGMENT.findAll(html)) {
            val tree = runCatching { json.parseToJsonElement(fragment.value) }.getOrNull() ?: continue
            findTrack(tree)?.let { return it }
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
     * Обложка.
     *
     * В embed-странице она лежит в `visualIdentity.image[]` (там есть
     * варианты 64/300/640 пикселей — берём самый большой), в старой
     * разметке — в `coverArt.sources[]` или `images[]`.
     */
    private fun coverOf(node: JsonObject): String? {
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
        return coverOfLegacy(node)
    }

    /** Обложка в старой разметке. */
    private fun coverOfLegacy(node: JsonObject): String? {
        node["coverArt"]?.let { coverArt ->
            val sources = coverArt.jsonObject["sources"]?.jsonArray
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

    /** Картинка из og:image — запасной вариант. */
    private fun ogImage(html: String): String? =
        OG_IMAGE.find(html)?.groupValues?.get(1)?.takeIf { it.isNotBlank() }

    private fun fetchText(
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

    /**
     * Настольный User-Agent.
     *
     * С мобильным Spotify отдаёт другую страницу, где данных меньше.
     */
    private const val USER_AGENT =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
            "(KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36"

    private val OG_IMAGE = Regex("""<meta[^>]+property="og:image"[^>]+content="([^"]+)"""")

    /** Кусок JSON прямо в тексте страницы. */
    private val JSON_FRAGMENT = Regex("""\{[^{}]*"name"\s*:\s*"[^"]*"[^{}]*\}""")
}
