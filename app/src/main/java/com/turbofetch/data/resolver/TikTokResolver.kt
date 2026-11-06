package com.turbofetch.data.resolver

import com.turbofetch.data.remote.HttpClientFactory
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
 * Резолвер TikTok.
 *
 * Как работает (проверено вживую 2026-11-07):
 * 1. Запрашиваем страницу ролика мобильным User-Agent'ом. В ответе есть
 *    `<script id="api-data">` с готовым JSON — никакой подписи и JS-челленджа
 *    в этом пути нет.
 * 2. Из `videoDetail.itemInfo.itemStruct.video` берём `playAddr` — прямую ссылку.
 * 3. Качать `playAddr` можно **только** с cookie страницы и заголовком
 *    `Referer: https://www.tiktok.com/`, иначе CDN отвечает 403.
 */
class TikTokResolver(
    private val client: OkHttpClient = HttpClientFactory.create(),
) : VideoResolver {

    override val platform: Source = Source.TIKTOK

    override suspend fun resolve(url: String): Result<MediaInfo> = withContext(Dispatchers.IO) {
        runCatching {
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", HttpClientFactory.MOBILE_UA)
                .header("Accept-Language", "en-US,en;q=0.9")
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    error("TikTok вернул HTTP ${response.code}")
                }
                val body = response.body?.string().orEmpty()
                parse(body)
            }
        }
    }

    private fun parse(html: String): MediaInfo {
        val json = extractApiData(html)
            ?: error("На странице нет блока api-data — TikTok, вероятно, поменял разметку")

        val item = json["videoDetail"]
            ?.jsonObject
            ?.get("itemInfo")
            ?.jsonObject
            ?.get("itemStruct")
            ?.jsonObject
            ?: error("В ответе нет itemStruct")

        val video = item["video"]?.jsonObject ?: error("В ответе нет блока video")

        val title = item["desc"]?.jsonPrimitive?.contentOrNull().orEmpty()
        val author = item["author"]?.jsonObject
            ?.get("nickname")
            ?.jsonPrimitive
            ?.contentOrNull()
        val duration = video["duration"]?.jsonPrimitive?.intOrNull()
        val cover = video["cover"]?.jsonPrimitive?.contentOrNull()
        val playAddr = video["playAddr"]?.jsonPrimitive?.contentOrNull()
            ?: error("В ответе нет playAddr")

        val headers = mapOf("Referer" to "https://www.tiktok.com/")

        val formats = buildFormats(video, playAddr, headers)
        com.turbofetch.core.Log.i("TikTok разобран: «${title.take(40)}», форматов ${formats.size}")

        return MediaInfo(
            source = Source.TIKTOK,
            title = title.ifBlank { "TikTok" },
            author = author,
            durationSeconds = duration,
            thumbnailUrl = cover,
            formats = formats,
        )
    }

    /**
     * В мобильной вёрстке `bitrateInfo` обычно отсутствует — тогда отдаём один
     * вариант. Если вдруг появится лестница качеств, строим по ней.
     */
    private fun buildFormats(
        video: JsonObject,
        playAddr: String,
        headers: Map<String, String>,
    ): List<MediaFormat> {
        val ladder = video["bitrateInfo"]?.jsonArray
        if (ladder.isNullOrEmpty()) {
            return listOf(
                MediaFormat(
                    id = "0",
                    label = "Оригинал",
                    mimeType = "video/mp4",
                    url = playAddr,
                    headers = headers,
                ),
            )
        }

        return ladder.mapIndexedNotNull { index, element ->
            val bitrate = element.jsonObject["Bitrate"]?.jsonPrimitive?.intOrNull() ?: 0
            val gearName = element.jsonObject["GearName"]?.jsonPrimitive?.contentOrNull().orEmpty()
            val url = element.jsonObject["PlayAddr"]
                ?.jsonObject
                ?.get("UrlList")
                ?.jsonArray
                ?.firstOrNull()
                ?.jsonPrimitive
                ?.contentOrNull()
                ?: return@mapIndexedNotNull null

            MediaFormat(
                id = "$index",
                label = gearName.ifBlank { "Вариант ${index + 1}" },
                bitrate = bitrate,
                mimeType = "video/mp4",
                url = url,
                headers = headers,
            )
        }.ifEmpty {
            listOf(
                MediaFormat(
                    id = "0",
                    label = "Оригинал",
                    mimeType = "video/mp4",
                    url = playAddr,
                    headers = headers,
                ),
            )
        }
    }

    private fun extractApiData(html: String): JsonObject? {
        val match = API_DATA_REGEX.find(html) ?: return null
        return runCatching {
            json.parseToJsonElement(match.groupValues[1]).jsonObject
        }.getOrNull()
    }

    private companion object {
        val json = Json { ignoreUnknownKeys = true }
        val API_DATA_REGEX =
            Regex("""<script id="api-data"[^>]*>(.*?)</script>""", RegexOption.DOT_MATCHES_ALL)
    }
}

private fun kotlinx.serialization.json.JsonPrimitive.contentOrNull(): String? =
    if (this is kotlinx.serialization.json.JsonNull) null else content

private fun kotlinx.serialization.json.JsonPrimitive.intOrNull(): Int? = content.toIntOrNull()
