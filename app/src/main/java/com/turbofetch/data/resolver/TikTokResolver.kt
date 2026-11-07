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
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * Резолвер TikTok.
 *
 * Как работает (проверено вживую 2026-11-07):
 *
 * 1. Запрашиваем страницу ролика мобильным User-Agent'ом. Десктопный UA
 *    упирается в WAF-челлендж (`SlardarWAF`), мобильный — отдаёт разметку
 *    с готовыми данными, без подписи и JS-челленджа.
 *
 * 2. **Разметка у TikTok бывает двух видов, и это главная причина старой
 *    поломки.** Раньше данные лежали в `<script id="api-data">`, но сейчас
 *    TikTok отдаёт эту вёрстку только для «чистых» ссылок вида
 *    `www.tiktok.com/@user/video/123`. Короткие ссылки `vm.tiktok.com/XXXX/`
 *    редиректят на `www.tiktok.com/@user/video/123?_r=1&_t=...`, и вот для
 *    такого URL (из-за параметров `_r` / `_t`) TikTok отдаёт **другую**
 *    вёрстку: `api-data` там нет вообще, вместо него лежит
 *    `<script id="__UNIVERSAL_DATA_FOR_REHYDRATION__">`, где ролик находится
 *    в `__DEFAULT_SCOPE__["webapp.reflow.video.detail"].itemInfo.itemStruct`.
 *    Поэтому резолвер разбирает **оба** формата: сначала UNIVERSAL, а если
 *    он не дал ролика — старый `api-data` как запасной путь.
 *
 * 3. Из `itemStruct` берём `video.playAddr` — прямую ссылку на файл.
 *
 * 4. Качать `playAddr` можно **только** с заголовком
 *    `Referer: https://www.tiktok.com/` и cookie `tt_chain_token`, выданной
 *    именно тем же запросом страницы: подпись в ссылке привязана к этой
 *    cookie, иначе CDN (Akamai) отвечает 403. Cookie кладём в заголовки
 *    формата, чтобы ссылка оставалась рабочей и после перезапуска приложения
 *    (хранилище cookie живёт в памяти и при перезапуске обнуляется).
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
        val item = extractItem(html)
            ?: error(
                "TikTok не отдал данных о ролике — скорее всего, видео удалено " +
                    "или недоступно",
            )

        val video = item["video"]?.asJsonObjectOrNull() ?: error("В ответе нет блока video")

        val title = item["desc"]?.jsonPrimitive?.contentOrNull().orEmpty()
        val author = item["author"]?.asJsonObjectOrNull()
            ?.get("nickname")
            ?.jsonPrimitive
            ?.contentOrNull()
        val duration = video["duration"]?.jsonPrimitive?.intOrNull()
        val cover = video["cover"]?.jsonPrimitive?.contentOrNull()
            ?: video["originCover"]?.jsonPrimitive?.contentOrNull()
        val playAddr = video["playAddr"]?.jsonPrimitive?.contentOrNull()
            ?: error("В ответе нет playAddr")

        val headers = cdnHeaders(playAddr)

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
     * Достаёт `itemStruct` ролика из страницы.
     *
     * Сначала пробуем новую вёрстку (`__UNIVERSAL_DATA_FOR_REHYDRATION__`) —
     * именно она приходит по коротким ссылкам `vm.tiktok.com`. Если там ролика
     * нет, откатываемся на старую схему `api-data`.
     */
    private fun extractItem(html: String): JsonObject? {
        extractFromUniversal(html)?.let { return it }
        return extractFromApiData(html)
    }

    /** Новая вёрстка: `__DEFAULT_SCOPE__["webapp.reflow.video.detail"].itemInfo.itemStruct`. */
    private fun extractFromUniversal(html: String): JsonObject? {
        val root = extractScriptObject(html, UNIVERSAL_SCRIPT_ID) ?: return null
        val scope = root["__DEFAULT_SCOPE__"]?.asJsonObjectOrNull() ?: return null

        // Сначала пробуем ожидаемый ключ, потом — любой другой скоуп с роликом:
        // TikTok периодически переименовывает их.
        itemStructOf(scope[VIDEO_DETAIL_SCOPE_KEY]?.asJsonObjectOrNull())?.let { return it }
        for ((key, value) in scope) {
            if (!key.contains(VIDEO_DETAIL_SCOPE_KEY)) continue
            itemStructOf(value?.asJsonObjectOrNull())?.let { return it }
        }
        return null
    }

    /** Старая вёрстка: `api-data` → `videoDetail.itemInfo.itemStruct`. */
    private fun extractFromApiData(html: String): JsonObject? {
        val root = extractScriptObject(html, API_DATA_SCRIPT_ID) ?: return null
        return root["videoDetail"]?.asJsonObjectOrNull()
            ?.get("itemInfo")?.asJsonObjectOrNull()
            ?.get("itemStruct")?.asJsonObjectOrNull()
    }

    /** `itemInfo.itemStruct` внутри объекта скоупа. */
    private fun itemStructOf(scopeValue: JsonObject?): JsonObject? =
        scopeValue?.get("itemInfo")?.asJsonObjectOrNull()
            ?.get("itemStruct")?.asJsonObjectOrNull()

    /**
     * Заголовки для запроса к CDN.
     *
     * `Referer` обязателен — без него Akamai отвечает 403. Cookie нужна
     * та же, что пришла со страницы: подпись в `playAddr` привязана к
     * `tt_chain_token` этой сессии. Кладём cookie в заголовки формата, чтобы
     * ссылка работала и после перезапуска приложения (в памяти cookie-jar
     * при перезапуске пустой).
     */
    private fun cdnHeaders(playAddr: String): Map<String, String> {
        val headers = mutableMapOf(REFERER_HEADER to TIKTOK_REFERER)
        val cdnUrl = playAddr.toHttpUrlOrNull()
        if (cdnUrl != null) {
            val cookies = runCatching { client.cookieJar.loadForRequest(cdnUrl) }.getOrNull()
            if (!cookies.isNullOrEmpty()) {
                headers[COOKIE_HEADER] = cookies.joinToString("; ") { "${it.name}=${it.value}" }
            }
        }
        return headers
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

    /** Содержимое `<script id="...">`, разобранное как JSON-объект. */
    private fun extractScriptObject(html: String, id: String): JsonObject? {
        val body = extractScript(html, id) ?: return null
        return runCatching { json.parseToJsonElement(body).asJsonObjectOrNull() }.getOrNull()
    }

    private fun extractScript(html: String, id: String): String? {
        for (match in SCRIPT_REGEX.findAll(html)) {
            if (match.groupValues[1] == id) return match.groupValues[2]
        }
        return null
    }

    private companion object {
        val json = Json { ignoreUnknownKeys = true }

        const val API_DATA_SCRIPT_ID = "api-data"
        const val UNIVERSAL_SCRIPT_ID = "__UNIVERSAL_DATA_FOR_REHYDRATION__"
        const val VIDEO_DETAIL_SCOPE_KEY = "webapp.reflow.video.detail"

        const val REFERER_HEADER = "Referer"
        const val COOKIE_HEADER = "Cookie"
        const val TIKTOK_REFERER = "https://www.tiktok.com/"

        /** `<script id="...">содержимое</script>` — первая группа: id, вторая: тело. */
        val SCRIPT_REGEX = Regex(
            """<script id="([^"]+)"[^>]*>(.*?)</script>""",
            RegexOption.DOT_MATCHES_ALL,
        )
    }
}

/** Объект безопасно читаем как JSON-объект (а не падаем на `jsonObject`). */
private fun kotlinx.serialization.json.JsonElement?.asJsonObjectOrNull(): JsonObject? =
    this as? JsonObject

private fun kotlinx.serialization.json.JsonPrimitive.contentOrNull(): String? =
    if (this is kotlinx.serialization.json.JsonNull) null else content

private fun kotlinx.serialization.json.JsonPrimitive.intOrNull(): Int? = content.toIntOrNull()
