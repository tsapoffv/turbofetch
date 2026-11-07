package com.turbofetch.data.resolver.soundcloud

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
import java.net.URLEncoder

/**
 * SoundCloud: треки.
 *
 * Важное: **прогрессивного MP3 больше нет**. В августе 2026 SoundCloud
 * убрал его из API, весь каталог переведён на AAC. Теперь трек приходит
 * как подписанный HLS-плейлист: `init.mp4` плюс десятки сегментов `.m4s`.
 *
 * Цепочка:
 * 1. `client_id` — из JS-бандлов сайта (см. [SoundCloudClientId]).
 * 2. `resolve` → метаданные и список `media.transcodings`.
 * 3. Выбор пресета: `aac_160k`, при его отсутствии `aac_96k`.
 *    `abr_*` пропускаем (битый), `snipped` — это превью, не трек.
 * 4. `stream/hls` → подписанная ссылка на m3u8.
 *
 * Сегменты CDN отдаются без единого заголовка — авторизует только подпись
 * в ссылке. Подпись живёт около двух часов.
 */
class SoundCloudResolver(
    private val client: OkHttpClient = HttpClientFactory.create(),
    private val clientIds: SoundCloudClientId = SoundCloudClientId(client),
) : VideoResolver {

    override val platform: Source = Source.SOUNDCLOUD

    override suspend fun resolve(url: String): Result<MediaInfo> = withContext(Dispatchers.IO) {
        runCatching {
            val clientId = clientIds.obtain()
            com.turbofetch.core.Log.i("SoundCloud: разбираю трек")
            val meta = resolveWithClient(url, clientId)
            com.turbofetch.core.Log.i("SoundCloud: трек разобран")

            val transcodings = meta["media"]
                ?.jsonObject?.get("transcodings")
                ?.jsonArray.orEmpty()

            val candidates = transcodings
                .mapNotNull { it.jsonObject }
                .filter { it["preset"]?.jsonPrimitive?.content != null }
                .filter { it["snipped"]?.jsonPrimitive?.content != "true" }
                // abr-потоки отдают пустой объект — их пропускаем.
                .filterNot { it["preset"]?.jsonPrimitive?.content?.startsWith("abr") == true }
                .filter { it["url"]?.jsonPrimitive?.content?.isNotBlank() == true }

            if (candidates.isEmpty()) {
                error("У трека нет доступных аудиопотоков")
            }

            // Лучший пресет — с самым высоким битрейтом.
            val best = candidates.maxByOrNull { transcoding ->
                bitrateOf(transcoding["preset"]?.jsonPrimitive?.content.orEmpty())
            } ?: error("У трека нет доступных аудиопотоков")

            val preset = best["preset"]?.jsonPrimitive?.content.orEmpty()
            val mediaUrl = best["url"]?.jsonPrimitive?.content.orEmpty()
            val mimeType = best["format"]?.jsonObject
                ?.get("mime_type")?.jsonPrimitive?.content.orEmpty()
            val bitrate = bitrateOf(preset)

            val playlist = fetchPlaylist(mediaUrl, clientId)

            val format = MediaFormat(
                id = preset,
                label = "$bitrate кбит/с",
                bitrate = bitrate * 1000,
                mimeType = mimeType.ifBlank { "audio/mp4" },
                hasVideo = false,
                hasAudio = true,
                url = playlist,
                kind = com.turbofetch.domain.model.FormatKind.HLS_PLAYLIST,
            )

            MediaInfo(
                source = Source.SOUNDCLOUD,
                title = meta["title"]?.jsonPrimitive?.content.orEmpty().ifBlank { "SoundCloud" },
                author = meta["user"]?.jsonObject
                    ?.get("username")?.jsonPrimitive?.content,
                durationSeconds = meta["duration"]?.jsonPrimitive?.content
                    ?.toLongOrNull()?.let { (it / 1000).toInt() },
                thumbnailUrl = meta["artwork_url"]?.jsonPrimitive?.content,
                formats = listOf(format),
            )
        }
    }

    /** `resolve` с данным client_id. */
    private fun resolveWithClient(url: String, clientId: String): JsonObject {
        val encoded = URLEncoder.encode(url, "UTF-8")
        val request = Request.Builder()
            .url("$API/resolve?url=$encoded&client_id=$clientId")
            .header("User-Agent", USER_AGENT)
            .build()

        client.newCall(request).execute().use { response ->
            if (response.code == 401 || response.code == 403) {
                // client_id устарел или его троттлят: выбрасываем кэш,
                // чтобы следующая попытка добыла свежий.
                clientIds.invalidate()
                error("SoundCloud отклонил client_id (HTTP ${response.code})")
            }
            if (!response.isSuccessful) {
                error("SoundCloud вернул HTTP ${response.code}")
            }
            val body = response.body?.string().orEmpty()
            return runCatching { json.parseToJsonElement(body).jsonObject }
                .getOrElse { error("Ответ SoundCloud не является JSON") }
        }
    }

    /** Подписанная ссылка на m3u8. */
    private fun fetchPlaylist(mediaUrl: String, clientId: String): String {
        val separator = if ("?" in mediaUrl) "&" else "?"
        val request = Request.Builder()
            .url("$mediaUrl${separator}client_id=$clientId")
            .header("User-Agent", USER_AGENT)
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                error("SoundCloud вернул HTTP ${response.code} на поток")
            }
            val body = response.body?.string().orEmpty()
            val url = runCatching {
                json.parseToJsonElement(body).jsonObject["url"]?.jsonPrimitive?.content
            }.getOrNull()
            return url?.takeIf { it.isNotBlank() }
                ?: error("SoundCloud не отдал ссылку на поток")
        }
    }

    private companion object {
        const val API = "https://api-v2.soundcloud.com"

        const val USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
                "(KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36"

        val json = Json { ignoreUnknownKeys = true }

        /** Килобиты в секунду из пресета вида `aac_160k`. */
        fun bitrateOf(preset: String): Int =
            Regex("\\d+").find(preset)?.value?.toIntOrNull() ?: 0
    }
}
