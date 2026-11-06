package com.turbofetch.domain.model

/** Состояние задачи в очереди. */
enum class TaskStatus {
    /** Ссылка принята, метаданные ещё не получены. */
    PENDING,

    /** Идёт разбор ссылки (probe). */
    PROBING,

    /** Метаданные получены, ждёт запуска. */
    READY,

    /** Задача поставлена в очередь WorkManager. */
    QUEUED,

    /** Качается. */
    RUNNING,

    /** Файл записан. */
    COMPLETED,

    /** Упало с ошибкой. */
    FAILED,

    /** Отменено юзером. */
    CANCELLED,
    ;

    val isTerminal: Boolean get() = this == COMPLETED || this == FAILED || this == CANCELLED
    val isActive: Boolean get() = this == PROBING || this == RUNNING
}

/** Одна задача очереди: ссылка + выбранные параметры + состояние. */
data class DownloadTask(
    val id: String,
    val url: String,
    val source: Source,
    val title: String? = null,
    val author: String? = null,
    val thumbnailUrl: String? = null,
    val durationSeconds: Int? = null,
    val availableFormats: List<MediaFormat> = emptyList(),
    val selectedFormat: MediaFormat? = null,
    val audioOnly: Boolean = false,
    val customName: String? = null,
    val status: TaskStatus = TaskStatus.PENDING,
    val progress: Float = 0f,
    val errorMessage: String? = null,
    val outputUri: String? = null,
    val createdAt: Long = 0L,
) {
    /** Что показывать в списке, пока метаданных нет. */
    val displayTitle: String get() = title ?: url
}
