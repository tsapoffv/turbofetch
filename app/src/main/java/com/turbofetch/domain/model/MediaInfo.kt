package com.turbofetch.domain.model

import kotlinx.serialization.Serializable

/** Метаданные, полученные при разборе ссылки (probe). */
@Serializable
data class MediaInfo(
    val source: Source,
    val title: String,
    val author: String? = null,
    val durationSeconds: Int? = null,
    val thumbnailUrl: String? = null,
    val formats: List<MediaFormat> = emptyList(),
)
