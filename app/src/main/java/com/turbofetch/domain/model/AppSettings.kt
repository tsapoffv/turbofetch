package com.turbofetch.domain.model

/** Пресет качества, применяемый к новым задачам. */
enum class QualityPreset(val height: Int, val label: String) {
    BEST(0, "Лучшее"),
    Q1080(1080, "1080p"),
    Q720(720, "720p"),
    Q480(480, "480p"),
    Q360(360, "360p"),
    ;

    companion object {
        /** Подобрать формат под пресет: сначала точное совпадение, иначе ближайший поменьше. */
        fun pick(formats: List<MediaFormat>, preset: QualityPreset): MediaFormat? {
            val video = formats.filter { it.hasVideo }
            if (video.isEmpty()) return formats.firstOrNull()
            if (preset == BEST) return video.maxByOrNull { it.height }
            return video.filter { it.height <= preset.height }
                .maxByOrNull { it.height }
                ?: video.minByOrNull { it.height }
        }
    }
}

/** Пользовательские настройки приложения. */
data class AppSettings(
    val defaultQuality: QualityPreset = QualityPreset.BEST,
    val defaultAudioOnly: Boolean = false,
    val fileNameTemplate: String = DEFAULT_TEMPLATE,
    val openAfterDownload: Boolean = true,
    val downloadOverWifiOnly: Boolean = false,
) {
    companion object {
        const val DEFAULT_TEMPLATE = "{title}_{quality}.{ext}"
    }
}
