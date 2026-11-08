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

/**
 * Что сделать с файлом после скачивания.
 *
 * Нужен для гифок: площадка отдаёт анимацию обычным mp4, и брать её «как
 * есть» — значит отдать пользователю файл, который половина плееров не
 * показывает. Поэтому предлагаем два варианта: настоящий GIF и mp4 без
 * звука.
 */
@Serializable
enum class PostProcess {
    /** Ничего: файл сохраняется как скачан. */
    NONE,

    /** Перекодировать в анимированный GIF через ffmpeg. */
    TO_GIF,
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
    /** Что сделать с файлом после скачивания. */
    val postProcess: PostProcess = PostProcess.NONE,
) {
    val isAudioOnly: Boolean get() = hasAudio && !hasVideo
}
