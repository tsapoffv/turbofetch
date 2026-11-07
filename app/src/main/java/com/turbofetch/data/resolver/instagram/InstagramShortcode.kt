package com.turbofetch.data.resolver.instagram

/**
 * Shortcode Instagram → числовой id публикации.
 *
 * Shortcode — это base64 от id в алфавите Instagram (`A-Za-z0-9-_`).
 * Обратное преобразование нужно, чтобы опереться на `pk` при разборе
 * страницы: в ней лежит около тридцати чужих публикаций того же аккаунта,
 * и без якоря мы рискуем разобрать чужой ролик.
 *
 * Проверено 7 ноября 2026: `Chunk8-jurw` → `2913440072144448240`.
 */
object InstagramShortcode {

    private const val ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_"

    /**
     * Числовой id публикации.
     *
     * @return id или -1, если в shortcode есть символ вне алфавита.
     */
    fun toMediaId(shortcode: String): Long {
        if (shortcode.isEmpty()) return -1
        var id = 0L
        for (ch in shortcode) {
            val index = ALPHABET.indexOf(ch)
            if (index < 0) return -1
            id = id * 64 + index
        }
        return id
    }
}
