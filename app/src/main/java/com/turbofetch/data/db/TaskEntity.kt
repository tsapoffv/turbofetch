package com.turbofetch.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.turbofetch.domain.model.DownloadTask
import com.turbofetch.domain.model.MediaFormat
import com.turbofetch.domain.model.Source
import com.turbofetch.domain.model.TaskStatus

@Entity(tableName = "tasks")
data class TaskEntity(
    @PrimaryKey val id: String,
    val url: String,
    val source: String,
    val title: String?,
    val author: String?,
    val thumbnailUrl: String?,
    val durationSeconds: Int?,
    val availableFormats: List<MediaFormat>,
    val selectedFormat: MediaFormat?,
    val audioFormat: MediaFormat?,
    val audioOnly: Boolean,
    val customName: String?,
    val status: String,
    val progress: Float,
    val errorMessage: String?,
    val outputUri: String?,
    val createdAt: Long,
    /** Позиция в очереди. Колонка названа `position`, потому что `order` — зарезервированное слово SQL. */
    @ColumnInfo(name = "position")
    val order: Int,
    val needsRefresh: Boolean,
)

fun TaskEntity.toDomain(): DownloadTask = DownloadTask(
    id = id,
    url = url,
    source = runCatching { Source.valueOf(source) }.getOrDefault(Source.UNKNOWN),
    title = title,
    author = author,
    thumbnailUrl = thumbnailUrl,
    durationSeconds = durationSeconds,
    availableFormats = availableFormats,
    selectedFormat = selectedFormat,
    audioFormat = audioFormat,
    audioOnly = audioOnly,
    customName = customName,
    status = runCatching { TaskStatus.valueOf(status) }.getOrDefault(TaskStatus.PENDING),
    progress = progress,
    errorMessage = errorMessage,
    outputUri = outputUri,
    createdAt = createdAt,
    order = order,
    needsRefresh = needsRefresh,
)

fun DownloadTask.toEntity(): TaskEntity = TaskEntity(
    id = id,
    url = url,
    source = source.name,
    title = title,
    author = author,
    thumbnailUrl = thumbnailUrl,
    durationSeconds = durationSeconds,
    availableFormats = availableFormats,
    selectedFormat = selectedFormat,
    audioFormat = audioFormat,
    audioOnly = audioOnly,
    customName = customName,
    status = status.name,
    progress = progress,
    errorMessage = errorMessage,
    outputUri = outputUri,
    createdAt = createdAt,
    order = order,
    needsRefresh = needsRefresh,
)
