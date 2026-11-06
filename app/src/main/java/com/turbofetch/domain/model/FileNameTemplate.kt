package com.turbofetch.domain.model

/**
 * Шаблон имени файла. Поддерживаемые плейсхолдеры:
 * `{title}`, `{author}`, `{quality}`, `{ext}`, `{date}`, `{id}`.
 */
object FileNameTemplate {

    fun render(
        template: String,
        title: String?,
        author: String?,
        quality: String?,
        extension: String,
        id: String,
        timestamp: Long = System.currentTimeMillis(),
    ): String {
        val date = java.time.LocalDate
            .ofInstant(java.time.Instant.ofEpochMilli(timestamp), java.time.ZoneId.systemDefault())
            .toString()

        val safeTitle = sanitize(title ?: id)
        val safeAuthor = sanitize(author.orEmpty())
        val safeQuality = sanitize(quality ?: "")

        val raw = template
            .replace("{title}", safeTitle)
            .replace("{author}", safeAuthor)
            .replace("{quality}", safeQuality)
            .replace("{ext}", extension)
            .replace("{date}", date)
            .replace("{id}", id)

        // Убираем пустые хвосты вида "title_.mp4" и слеши, которые сломают путь.
        val cleaned = raw
            .replace(Regex("_{2,}"), "_")
            .replace(Regex("([._])+\\."), ".")
            .trim('_', '.', ' ')

        return cleaned.ifBlank { "$safeTitle.$extension" }
    }

    /** Убирает символы, запрещённые в именах файлов на Android. */
    fun sanitize(raw: String): String = raw
        .replace(Regex("[\\\\/:*?\"<>|\\u0000-\\u001F]"), "_")
        .replace(Regex("\\s+"), " ")
        .trim()
        .take(120)
}
