package com.turbofetch.data.resolver.instagram

import com.turbofetch.data.remote.HttpClientFactory
import com.turbofetch.domain.link.LinkParser
import com.turbofetch.domain.model.MediaFormat
import com.turbofetch.domain.model.MediaInfo
import com.turbofetch.domain.model.Source
import com.turbofetch.domain.resolver.VideoResolver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * Instagram: видео из публичных публикаций и Reels.
 *
 * Ключевая хитрость — **User-Agent бота**. С обычным браузерным Instagram
 * отдаёт пустую оболочку приложения (500 КБ HTML без единого видео), а с
 * ботом — полную страницу с JSON-данными. Проверено 7 ноября 2026:
 * разница 816 КБ с данными против 498 КБ пустоты.
 *
 * Логина не нужно: публичные посты, Reels и карусели доступны анонимно.
 * Stories и приватные аккаунты — нет, их Instagram не отдаёт анониму.
 *
 * Скачивание CDN не требует заголовков: подпись внутри ссылки самодостаточна
 * (в отличие от TikTok, где нужен `Referer` и cookie).
 */
class InstagramResolver(
    private val client: OkHttpClient = HttpClientFactory.create(),
) : VideoResolver {

    override val platform: Source = Source.INSTAGRAM

    override suspend fun resolve(url: String): Result<MediaInfo> = withContext(Dispatchers.IO) {
        runCatching {
            val shortcode = LinkParser.parse(url)?.id?.takeIf { it.isNotBlank() }
                ?: error("Не удалось извлечь код публикации Instagram")

            val mediaId = InstagramShortcode.toMediaId(shortcode)
            if (mediaId < 0) error("Ссылка Instagram выглядит повреждённой")

            // Канонический путь: /share/p/ и /reels/ отдают пустую оболочку,
            // а /p/ — всегда полные данные.
            val request = Request.Builder()
                .url("$BASE_URL/p/$shortcode/")
                .header("User-Agent", CRAWLER_UA)
                .header("Accept-Language", "en-US,en;q=0.9")
                .build()

            val html = client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    error("Instagram вернул HTTP ${response.code}")
                }
                response.body?.string().orEmpty()
            }

            val post = InstagramCrawler.parse(html, mediaId) ?: error(NOTHING_FOUND)

            buildInfo(post)
        }
    }

    private fun buildInfo(post: InstagramCrawler.Post): MediaInfo {
        val videos = post.items.filter { it.progressive != null || it.dash != null }
        if (videos.isEmpty()) {
            error("В публикации нет видео — только фото")
        }

        // У карусели берём первый видео-элемент: остальные элементы —
        // отдельные публикации, для них нужны отдельные ссылки.
        val item = videos.first()

        val formats = buildList {
            item.dash?.let { dash ->
                add(
                    MediaFormat(
                        id = "dash",
                        label = "${dash.quality}p · лучшее",
                        height = dash.quality,
                        bitrate = dash.bitrate,
                        mimeType = "video/mp4",
                        hasVideo = true,
                        hasAudio = item.hasAudio,
                        url = dash.url,
                    ),
                )
            }
            item.progressive?.let { progressive ->
                // У progressive-файла в JSON нет разрешения — оно известно
                // только из DASH-манифеста (ролик-то один и тот же).
                val quality = progressive.quality.takeIf { it > 0 }
                    ?: item.dash?.quality
                    ?: 0
                add(
                    MediaFormat(
                        id = "progressive",
                        label = if (quality > 0) "${quality}p" else "оригинал",
                        height = quality,
                        bitrate = progressive.bitrate,
                        mimeType = "video/mp4",
                        hasVideo = true,
                        hasAudio = item.hasAudio,
                        url = progressive.url,
                    ),
                )
            }
        }

        return MediaInfo(
            source = Source.INSTAGRAM,
            title = post.items.first().code.ifBlank { "Instagram" },
            author = null,
            durationSeconds = item.durationSeconds,
            thumbnailUrl = item.thumbnailUrl,
            formats = formats,
        )
    }

    private companion object {
        const val BASE_URL = "https://www.instagram.com"

        /**
         * Бот, а не браузер: с ним Instagram отдаёт данные.
         */
        const val CRAWLER_UA = "Googlebot/2.1 (+http://www.google.com/bot.html)"

        /**
         * Один ответ на все три случая: удалено, закрыто, не существует.
         *
         * Instagram отдаёт для них одинаковую пустую оболочку, поэтому
         * правду сказать нельзя — говорим «не нашли».
         */
        const val NOTHING_FOUND =
            "Instagram не отдал данных: публикация удалена, закрыта " +
                "или доступна только авторизованным"
    }
}
