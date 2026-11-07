package com.turbofetch.domain.link

import com.turbofetch.domain.model.Source

/** Разобранная ссылка: сама ссылка + площадка + идентификатор ролика. */
data class ParsedLink(
    val url: String,
    val source: Source,
    val id: String,
)

/**
 * Разбирает то, что прилетело из «Поделиться»: чистую ссылку или целый текст
 * вроде «Смотри https://youtu.be/abc — прикольно».
 */
object LinkParser {

    private val URL_REGEX = Regex("https?://\\S+")

    fun parse(raw: String): ParsedLink? {
        val url = extractUrl(raw) ?: return null
        val uri = runCatching { java.net.URI(url) }.getOrNull() ?: return null
        val host = uri.host?.lowercase() ?: return null
        val source = Source.fromHost(host)
        if (source == Source.UNKNOWN) return null
        val id = extractId(source, host, uri.path ?: "", uri.query)
        return ParsedLink(url = url, source = source, id = id)
    }

    private fun extractUrl(raw: String): String? {
        val trimmed = raw.trim()
        if (trimmed.startsWith("http", ignoreCase = true)) {
            // Отрезаем хвост, если юзер прислал ссылку вместе с текстом.
            return trimmed.split(Regex("\\s")).first().trimEnd('.', ',', ')', ']', '"', '\'')
        }
        return URL_REGEX.find(trimmed)?.value?.trimEnd('.', ',', ')', ']', '"', '\'')
    }

    private fun extractId(source: Source, host: String, path: String, query: String?): String {
        val segments = path.split('/').filter { it.isNotEmpty() }
        return when (source) {
            Source.YOUTUBE -> when {
                host.contains("youtu.be") -> segments.firstOrNull().orEmpty()
                segments.contains("shorts") -> segments.getOrNull(segments.indexOf("shorts") + 1).orEmpty()
                segments.contains("embed") -> segments.getOrNull(segments.indexOf("embed") + 1).orEmpty()
                else -> query?.split('&')
                    ?.firstOrNull { it.startsWith("v=") }
                    ?.removePrefix("v=")
                    ?: segments.lastOrNull().orEmpty()
            }

            Source.TIKTOK -> segments.lastOrNull().orEmpty()

            Source.X -> when {
                segments.contains("status") -> segments.getOrNull(segments.indexOf("status") + 1).orEmpty()
                else -> segments.lastOrNull().orEmpty()
            }

            // Instagram: shortcode — последний значимый сегмент пути.
            // Пути бывают /p/, /reel/, /reels/, /tv/, /share/p/ — форма
            // разная, а код всегда один и тот же. Резолвер потом строит
            // канонический URL /p/<code>/ сам.
            Source.INSTAGRAM -> segments.lastOrNull().orEmpty()

            // SoundCloud: /<юзер>/<слаг трека> — слаг и есть id.
            Source.SOUNDCLOUD -> segments.lastOrNull().orEmpty()
            // Spotify: идентификатор трека — последний значимый сегмент пути.
            Source.SPOTIFY -> segments.lastOrNull().orEmpty()

            Source.UNKNOWN -> ""
        }
    }
}
