package com.turbofetch.domain.model

import kotlinx.serialization.Serializable

/**
 * Один доступный вариант файла для скачивания.
 *
 * Идентификатор площадочный: у YouTube это itag, у TikTok/X — индекс в лестнице качеств.
 * [label] — то, что показываем юзеру («1080p», «720p», «audio»).
 */
/**
 * Что лежит по ссылке формата.
 *
 * [FILE] — обычный файл, качается одним запросом.
 * [HLS_PLAYLIST] — подписанный m3u8-плейлист: за ним не файл, а
 * последовательность сегментов (так отдаёт SoundCloud).
 */
@Serializable
enum class FormatKind {
    FILE,
    HLS_PLAYLIST,
}

@Serializable
data class MediaFormat(
    val id: String,
    val label: String,
    val height: Int = 0,
    val bitrate: Int = 0,
    val mimeType: String = "",
    val hasVideo: Boolean = true,
    val hasAudio: Boolean = true,
    val approxBytes: Long = 0,
    /**
     * Прямая ссылка на файл. У большинства площадок она подписана и живёт недолго,
     * поэтому перед скачиванием её стоит обновить повторным `resolve`.
     */
    val url: String? = null,
    /** Заголовки, обязательные при обращении к CDN (например, `Referer` у TikTok). */
    val headers: Map<String, String> = emptyMap(),
    /** Что лежит по ссылке: файл или плейлист. */
    val kind: FormatKind = FormatKind.FILE,
) {
    val isAudioOnly: Boolean get() = hasAudio && !hasVideo
}
