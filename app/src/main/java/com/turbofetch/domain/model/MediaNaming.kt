package com.turbofetch.domain.model

/**
 * Правила именования и типа файла.
 *
 * Появились потому, что площадки начали отдавать готовый звук сами
 * (SoundCloud — AAC в HLS), и старая логика «mp3 на всё» начала врать:
 * аудиофайл с расширением `.mp4` в папке `Movies/`.
 *
 * Правило простое:
 * - YouTube-аудио по-прежнему конвертируется в mp3 — так привычнее.
 * - Площадка, которая сама отдаёт звук (SoundCloud), сохраняется как есть:
 *   `.m4a`, AAC, без перекодирования.
 * - mp3 из такого источника — только по явному выбору юзера.
 */
object MediaNaming {

    /** Какой контейнер получается на выходе. */
    enum class Container {
        /** Видео с картинкой (и звуком, если он был). */
        VIDEO,

        /** Звук AAC в контейнере m4a — то, что отдала площадка. */
        M4A,

        /** Звук mp3 — результат конвертации через ffmpeg. */
        MP3,

        /** Анимированный GIF — результат конвертации гифки через ffmpeg. */
        GIF,
    }

    /**
     * В источнике нет ни одного видеопотока — значит, это музыка.
     *
     * Считаем по форматам: у SoundCloud они все аудио, у YouTube есть
     * и видео, и звук, поэтому YouTube сюда не попадает.
     */
    fun isAudioOnlySource(formats: List<MediaFormat>): Boolean =
        formats.isNotEmpty() && formats.none { it.hasVideo }

    /**
     * Контейнер по умолчанию для набора форматов.
     *
     * @param wantMp3 юзер явно попросил только аудио — тогда mp3.
     * @param selected формат, который юзер выбрал. Он важнее списка: у
     *   гифки из X два варианта (GIF и mp4 без звука), и контейнер зависит
     *   от того, какой выбран.
     */
    fun container(
        formats: List<MediaFormat>,
        wantMp3: Boolean,
        selected: MediaFormat? = null,
    ): Container = when {
        wantMp3 -> Container.MP3
        // Выбранный формат с пометкой «перекодировать в GIF» — это гифка,
        // даже если площадка отдала её mp4-конвертом.
        selected?.postProcess == PostProcess.TO_GIF -> Container.GIF
        selected?.mimeType == "image/gif" -> Container.GIF
        isAudioOnlySource(formats) -> Container.M4A
        else -> Container.VIDEO
    }

    /** Расширение файла. */
    fun extension(container: Container): String = when (container) {
        Container.VIDEO -> "mp4"
        Container.M4A -> "m4a"
        Container.MP3 -> "mp3"
        Container.GIF -> "gif"
    }

    /** MIME-тип для MediaStore. */
    fun mimeType(container: Container, sourceMimeType: String = ""): String = when (container) {
        Container.VIDEO -> sourceMimeType.takeIf { it.isNotBlank() } ?: "video/mp4"
        Container.M4A -> "audio/mp4"
        Container.MP3 -> "audio/mpeg"
        Container.GIF -> "image/gif"
    }

    /** Файл уходит в папку «Музыка», а не «Видео». */
    fun isAudio(container: Container): Boolean =
        container == Container.M4A || container == Container.MP3

    /** Файл — картинка: уходит в «Фото», а не в «Видео». */
    fun isImage(container: Container): Boolean = container == Container.GIF
}
