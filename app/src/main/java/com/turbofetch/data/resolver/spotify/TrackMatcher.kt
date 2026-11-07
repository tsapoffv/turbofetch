package com.turbofetch.data.resolver.spotify

import kotlin.math.abs
import kotlin.math.max

/**
 * Кандидат на трек, найденный на YouTube.
 *
 * Заполняется из результатов поиска, поэтому длительность может быть
 * неизвестна (-1).
 */
data class TrackCandidate(
    val title: String,
    val uploader: String,
    val durationSeconds: Long,
    val url: String,
)

/**
 * Сопоставление трека Spotify с найденными на YouTube роликами.
 *
 * Spotify отдаёт точные метаданные: название, артиста и длительность.
 * На YouTube же в названии живут «(Official Video)», «Lyrics», «Remix» и
 * прочий мусор, а длительность часто на пару секунд отличается. Поэтому
 * сравниваем по **нормализованным** названиям, а длительность используем
 * как жёсткий фильтр: расхождение больше 20 секунд — это другой трек.
 *
 * Чистые функции: тестируются офлайн, без сети.
 */
object TrackMatcher {

    /** Допустимое расхождение длительности, секунды. */
    const val DURATION_TOLERANCE_SEC = 20

    /** Минимальный балл, при котором кандидат считается подходящим. */
    const val MIN_SCORE = 0.55

    /**
     * Балл кандидата: чем выше, тем лучше совпадение.
     *
     * @param spotifyDurationSeconds длительность трека в Spotify, null если
     *   площадка её не отдала.
     */
    fun score(
        spotifyTitle: String,
        spotifyArtist: String,
        spotifyDurationSeconds: Int?,
        candidate: TrackCandidate,
    ): Double {
        val titleScore = similarity(normalise(spotifyTitle), normalise(candidate.title))
        val artistScore = artistMatch(spotifyArtist, candidate)

        var score = titleScore * 0.6 + artistScore * 0.4

        // Длительность — фильтр, а не только вес: «Another One Bites The Dust»
        // и ремикс той же песни для Spotify — разные треки.
        if (spotifyDurationSeconds != null && candidate.durationSeconds > 0) {
            val delta = abs(candidate.durationSeconds - spotifyDurationSeconds)
            if (delta > DURATION_TOLERANCE_SEC) return 0.0
            score += 0.1 * (1.0 - delta.toDouble() / DURATION_TOLERANCE_SEC)
        }

        // Официальные записи приоритетнее: у каналов-дистрибьюторов стоит
        // метка «Topic» или «VEVO».
        val uploader = candidate.uploader.lowercase()
        if (uploader.contains("topic") || uploader.contains("vevo") ||
            uploader.contains("official")
        ) {
            score += 0.15
        }

        return score.coerceIn(0.0, 1.0)
    }

    /**
     * Лучший кандидат из списка.
     *
     * @return пара «балл — кандидат» либо null, если ничего не подошло.
     */
    fun best(
        spotifyTitle: String,
        spotifyArtist: String,
        spotifyDurationSeconds: Int?,
        candidates: List<TrackCandidate>,
    ): Pair<Double, TrackCandidate>? {
        val scored = candidates.mapNotNull { candidate ->
            val value = score(spotifyTitle, spotifyArtist, spotifyDurationSeconds, candidate)
            if (value <= 0.0) null else value to candidate
        }
        val best = scored.maxByOrNull { it.first } ?: return null
        return if (best.first >= MIN_SCORE) best else null
    }

    /**
     * Совпадение названий.
     *
     * Сначала пробуем точное равенство нормализованных строк, потом —
     * долю общих слов. Полное совпадение важнее: у ремиксов названия
     * отличаются одним словом, и такой «частичный» матч здесь не годится.
     */
    private fun similarity(a: String, b: String): Double {
        if (a.isEmpty() || b.isEmpty()) return 0.0
        if (a == b) return 1.0

        val wordsA = a.split(' ').filter { it.isNotEmpty() }.toSet()
        val wordsB = b.split(' ').filter { it.isNotEmpty() }.toSet()
        if (wordsA.isEmpty() || wordsB.isEmpty()) return 0.0

        val common = wordsA.intersect(wordsB).size
        val union = max(wordsA.size, wordsB.size)
        return common.toDouble() / union
    }

    /** Совпадение по артисту: ищем артиста в названии или в имени канала. */
    private fun artistMatch(spotifyArtist: String, candidate: TrackCandidate): Double {
        val artist = normalise(spotifyArtist)
        if (artist.isEmpty()) return 0.5

        val title = normalise(candidate.title)
        val uploader = normalise(candidate.uploader)

        if (title.contains(artist) || artist.contains(title)) return 1.0
        if (uploader.contains(artist) || artist.contains(uploader)) return 1.0

        // Артист мог написан иначе («The Beatles» против «Beatles»).
        val artistWords = artist.split(' ').filter { it.length > 2 }.toSet()
        if (artistWords.isNotEmpty()) {
            val haystack = (title + ' ' + uploader).split(' ').toSet()
            val hits = artistWords.count { it in haystack }
            if (hits > 0) return hits.toDouble() / artistWords.size
        }
        return 0.0
    }

    /**
     * Приводит строку к сравнимому виду: нижний регистр, без пунктуации и
     * без служебных слов вроде «feat», «official video», «remix».
     *
     * Служебные слова выбрасываем целиком: иначе «Song (Official Video)» и
     * «Song (Lyrics)» перестали бы быть одним и тем же треком.
     */
    fun normalise(raw: String): String {
        val lowered = raw.lowercase()
        val cleaned = lowered.replace(Regex("[^\\p{L}\\p{N}]+"), " ")
        return cleaned.split(' ')
            .filter { it.isNotEmpty() && it !in NOISE_WORDS }
            .joinToString(" ")
    }

    /** Слова, которые ничего не различают между треками. */
    private val NOISE_WORDS = setOf(
        "feat", "ft", "featuring", "official", "video", "audio", "lyrics", "lyric",
        "remix", "version", "music", "hd", "hq", "mv", "clip", "full",
    )
}
