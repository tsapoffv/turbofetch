package com.turbofetch.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.turbofetch.domain.model.DownloadedFile
import com.turbofetch.domain.model.Source

/**
 * Уже скачанный файл — запись в базе.
 *
 * Отдельная таблица от `tasks`: задачу очереди можно удалить, а файл
 * остаётся на диске и должен быть виден в списке скачанного.
 */
@Entity(tableName = "files")
data class FileEntity(
    @PrimaryKey val id: String,
    val taskId: String?,
    val title: String,
    val fileName: String,
    val uri: String,
    val mimeType: String,
    val sizeBytes: Long,
    val source: String,
    val thumbnailUrl: String?,
    val createdAt: Long,
    /** Исходная ссылка на площадке — чтобы её можно было скопировать. */
    val sourceUrl: String?,
)

fun FileEntity.toDomain(): DownloadedFile = DownloadedFile(
    id = id,
    taskId = taskId,
    title = title,
    fileName = fileName,
    uri = uri,
    mimeType = mimeType,
    sizeBytes = sizeBytes,
    source = runCatching { Source.valueOf(source) }.getOrDefault(Source.UNKNOWN),
    thumbnailUrl = thumbnailUrl,
    createdAt = createdAt,
    sourceUrl = sourceUrl,
)

fun DownloadedFile.toEntity(): FileEntity = FileEntity(
    id = id,
    taskId = taskId,
    title = title,
    fileName = fileName,
    uri = uri,
    mimeType = mimeType,
    sizeBytes = sizeBytes,
    source = source.name,
    thumbnailUrl = thumbnailUrl,
    createdAt = createdAt,
    sourceUrl = sourceUrl,
)
