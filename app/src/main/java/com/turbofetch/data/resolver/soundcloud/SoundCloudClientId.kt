package com.turbofetch.data.resolver.soundcloud

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * `client_id` для API SoundCloud.
 *
 * Официальный API требует Artist Pro (платную подписку) и запрещает
 * встраивать секрет в приложение, поэтому используется тот самый id, что
 * лежит в JS-бандлах сайта. Именно так делает yt-dlp.
 *
 * Значение общее у всех, кто скрейпит SoundCloud, поэтому иногда его
 * троттлят. Отсюда две меры: кэш в памяти и повторная добыча, если
 * api-v2 ответил 401 или 403.
 */
class SoundCloudClientId(
    private val client: OkHttpClient,
) {

    private var cached: String? = null

    /** Кэшированный id — без сети. */
    fun peek(): String? = cached

    /**
     * Добывает id: сначала пробуем кэш, потом идём на сайт.
     *
     * @param force переизвлечь, даже если кэш есть.
     */
    suspend fun obtain(force: Boolean = false): String = withContext(Dispatchers.IO) {
        if (!force) {
            cached?.let { return@withContext it }
        }

        com.turbofetch.core.Log.i("SoundCloud: ищу client_id")
        val html = fetchText(HOMEPAGE)
        val found = CLIENT_ID.find(html)?.groupValues?.get(1)
        if (found != null) {
            cached = found
            return@withContext found
        }

        // В самой странице id может не быть — он внутри JS-бандлов.
        // Ссылки на них лежат прямо в HTML (не в атрибутах script),
        // поэтому ищем их по всему тексту страницы.
        val bundles = bundleUrls(html)
        com.turbofetch.core.Log.i("SoundCloud: бандлов ${bundles.size}")
        for (src in bundles) {
            val js = runCatching { fetchText(src) }.getOrNull() ?: continue
            val id = CLIENT_ID.find(js)?.groupValues?.get(1)
            if (id != null) {
                cached = id
                com.turbofetch.core.Log.i("SoundCloud: client_id найден")
                return@withContext id
            }
        }

        error("Не удалось получить client_id SoundCloud")
    }

    /**
     * Список JS-бандлов SoundCloud со страницы.
     *
     * Сначала ассеты с `a-v2.sndcdn.com` — в них лежит `client_id`.
     * Потом все прочие скрипты — на случай, если CDN поменяется.
     */
    private fun bundleUrls(html: String): List<String> {
        val assets = ASSET_BUNDLE.findAll(html).map { it.value }.distinct().toList()
        val scripts = SCRIPT_SRC.findAll(html)
            .map { it.groupValues[1] }
            .filter { it.startsWith("http") }
            .distinct()
            .toList()
        return assets + scripts.filterNot { it in assets }
    }

    /** Забыть кэш — например, после 401. */
    fun invalidate() {
        cached = null
    }

    private fun fetchText(url: String): String {
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", USER_AGENT)
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) error("SoundCloud вернул HTTP ${response.code}")
            return response.body?.string().orEmpty()
        }
    }

    private companion object {
        const val HOMEPAGE = "https://soundcloud.com/"

        /**
         * Именно настольный User-Agent.
         *
         * С мобильным SoundCloud отдаёт новый сайт на Next.js, где таких
         * бандлов нет вовсе и `client_id` не найти. Проверено 7 ноября 2026.
         */
        const val USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
                "(KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36"

        val CLIENT_ID = Regex("""client_id\s*:\s*"([0-9a-zA-Z]{32})"""")

        /** Ссылки на ассеты CDN прямо в тексте страницы. */
        val ASSET_BUNDLE = Regex("""https://a-v2\.sndcdn\.com/assets/[^"'\\\s]+\.js""")

        val SCRIPT_SRC = Regex("""<script[^>]+src="([^"]+\.js[^"]*)"""")
    }
}
