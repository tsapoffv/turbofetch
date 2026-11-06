package com.turbofetch.data.resolver

import com.turbofetch.data.remote.HttpClientFactory
import com.turbofetch.domain.link.LinkParser
import com.turbofetch.domain.model.MediaFormat
import com.turbofetch.domain.model.MediaInfo
import com.turbofetch.domain.model.Source
import com.turbofetch.domain.resolver.VideoResolver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * Резолвер X (Twitter).
 *
 * Используется недокументированный эндпоинт `cdn.syndication.twimg.com/tweet-result`,
 * который отдаёт готовый JSON с вариантами видео. Он не требует авторизации:
 * токен в ссылке сервером не проверяется, но мы считаем его по канонической
 * формуле на случай, если проверка появится.
 *
 * Устойчивость: у yt-dlp этот путь чинили дважды за 2025 год и ни разу за 2026 —
 * самый стабильный из трёх площадок. Но он недокументирован, поэтому слой
 * намеренно изолирован в одном классе: если endpoints умрёт, меняем здесь.
 */
class XResolver(
    private val client: OkHttpClient = HttpClientFactory.create(),
) : VideoResolver {

    override val platform: Source = Source.X

    override suspend fun resolve(url: String): Result<MediaInfo> = withContext(Dispatchers.IO) {
        runCatching {
            val id = LinkParser.parse(url)?.id?.takeIf { it.isNotBlank() }
                ?: error("Не удалось извлечь идентификатор твита")

            val request = Request.Builder()
                .url("$ENDPOINT?id=$id&token=${tokenFor(id)}")
                .header("User-Agent", API_UA)
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    error("X вернул HTTP ${response.code}")
                }
                val body = response.body?.string().orEmpty()
                parse(body)
            }
        }
    }

    private fun parse(body: String): MediaInfo {
        val json = runCatching { json.parseToJsonElement(body).jsonObject }.getOrNull()
            ?: error("Ответ X не является JSON")

        if (json.containsKey("tombstone")) {
            error("Твит недоступен (удалён или закрыт)")
        }

        val mediaDetails = json["mediaDetails"]?.jsonArray.orEmpty()
        val videoEntry = mediaDetails
            .mapNotNull { it.jsonObject["video_info"]?.jsonObject }
            .firstOrNull()
            ?: error("В твите нет видео")

        val variants = videoEntry["variants"]?.jsonArray.orEmpty()
        val formats = variants.mapNotNull { variant ->
            val obj = variant.jsonObject
            val link = obj["url"]?.jsonPrimitive?.contentOrNullSafe() ?: return@mapNotNull null
            val contentType = obj["contentType"]?.jsonPrimitive?.contentOrNullSafe().orEmpty()
            val bitrate = obj["bitrate"]?.jsonPrimitive?.intOrNullSafe() ?: 0

            val isHls = contentType.contains("mpegURL")
            MediaFormat(
                id = "$bitrate-$contentType",
                label = if (isHls) "HLS (лучшее)" else "${bitrate / 1000} kbps",
                bitrate = bitrate,
                mimeType = contentType.ifBlank { "video/mp4" },
                url = link,
            )
        }.sortedByDescending { it.bitrate }

        if (formats.isEmpty()) {
            error("В твите нет вариантов видео")
        }

        val text = json["text"]?.jsonPrimitive?.contentOrNullSafe().orEmpty()
        val author = json["user"]?.jsonObject
            ?.get("name")
            ?.jsonPrimitive
            ?.contentOrNullSafe()
        val thumbnail = mediaDetails
            .firstNotNullOfOrNull { it.jsonObject["media_url_https"]?.jsonPrimitive?.contentOrNullSafe() }

        return MediaInfo(
            source = Source.X,
            title = text.ifBlank { "Пост X" },
            author = author,
            durationSeconds = null,
            thumbnailUrl = thumbnail,
            formats = formats,
        )
    }

    /** Канонический токен: `((id / 1e15) * PI).toString(36)` без нулей и точки. */
    private fun tokenFor(id: String): String {
        val numeric = id.toLongOrNull()?.toDouble() ?: return "a"
        val scaled = numeric / 1e15 * Math.PI
        return scaled.toBase36().replace("0", "").replace(".", "").ifBlank { "a" }
    }

    private fun Double.toBase36(): String {
        val digits = "0123456789abcdefghijklmnopqrstuvwxyz"
        val integerPart = kotlin.math.floor(this).toLong()
        var fraction = this - integerPart

        val integerString = buildString {
            var n = integerPart
            if (n == 0L) append('0')
            while (n > 0) {
                append(digits[(n % 36).toInt()])
                n /= 36
            }
            reverse()
        }

        if (fraction <= 0.0) return integerString

        val fractionString = buildString {
            repeat(20) {
                fraction *= 36
                val digit = kotlin.math.floor(fraction).toInt().coerceIn(0, 35)
                append(digits[digit])
                fraction -= digit
                if (fraction <= 0.0) return@buildString
            }
        }
        return "$integerString.$fractionString"
    }

    private companion object {
        const val ENDPOINT = "https://cdn.syndication.twimg.com/tweet-result"
        const val API_UA = "Dalvik/2.1.0 (Linux; U; Android 13)"
        val json = Json { ignoreUnknownKeys = true }
    }
}

private fun kotlinx.serialization.json.JsonPrimitive.contentOrNullSafe(): String? =
    if (this is kotlinx.serialization.json.JsonNull) null else content

private fun kotlinx.serialization.json.JsonPrimitive.intOrNullSafe(): Int? = content.toIntOrNull()
