package com.turbofetch.data.db

import androidx.room.TypeConverter
import com.turbofetch.domain.model.CollectionTrack
import com.turbofetch.domain.model.MediaFormat
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/** Форматы храним в Room как JSON — список структур без вложенных таблиц. */
class Converters {

    @TypeConverter
    fun formatsToJson(value: List<MediaFormat>): String = json.encodeToString(value)

    @TypeConverter
    fun jsonToFormats(value: String): List<MediaFormat> =
        runCatching { json.decodeFromString<List<MediaFormat>>(value) }.getOrDefault(emptyList())

    @TypeConverter
    fun collectionTracksToJson(value: List<CollectionTrack>): String =
        json.encodeToString(value)

    @TypeConverter
    fun jsonToCollectionTracks(value: String): List<CollectionTrack> =
        runCatching { json.decodeFromString<List<CollectionTrack>>(value) }.getOrDefault(emptyList())

    @TypeConverter
    fun formatToJson(value: MediaFormat?): String? = value?.let { json.encodeToString(it) }

    @TypeConverter
    fun jsonToFormat(value: String?): MediaFormat? = value?.let {
        runCatching { json.decodeFromString<MediaFormat>(it) }.getOrNull()
    }

    private companion object {
        val json = Json { ignoreUnknownKeys = true }
    }
}
