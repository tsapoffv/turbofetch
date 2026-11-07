package com.turbofetch.domain.model

import kotlinx.serialization.Serializable

/**
 * Трек внутри альбома или плейлиста.
 *
 * У Spotify альбом — это набор треков, и у каждого трека есть свой
 * идентификатор: по нему трек потом ищется на YouTube.
 */
@Serializable
data class CollectionTrack(
    /** Идентификатор трека на площадке (без префикса `spotify:track:`). */
    val id: String,
    val title: String,
    val artist: String,
    val durationSeconds: Int,
    /** Отмечен ли трек для скачивания. По умолчанию — да: выбран весь альбом. */
    val selected: Boolean = true,
    /**
     * Обложка трека.
     *
     * В списке треков площадки обложек нет — она берётся с их собственных
     * страниц. Для плейлиста это три обложки в ряд на карточке.
     */
    val thumbUrl: String? = null,
    /**
     * Готовая ссылка на трек.
     *
     * У SoundCloud она есть прямо в API (`permalink_url`), у Spotify
     * собирается из идентификатора.
     */
    val url: String? = null,
)
