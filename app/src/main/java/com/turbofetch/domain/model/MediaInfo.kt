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
    /**
     * Треки альбома или плейлиста.
     *
     * Непустой список означает, что ссылка ведёт не на один файл, а на
     * коллекцию: сначала юзер выбирает нужные треки, потом они становятся
     * отдельными задачами.
     */
    val collectionTracks: List<CollectionTrack> = emptyList(),
)
