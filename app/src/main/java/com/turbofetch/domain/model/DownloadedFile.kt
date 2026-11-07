package com.turbofetch.domain.model

/**
 * Уже скачанный файл.
 *
 * Отдельная сущность от [DownloadTask]: задача живёт в очереди и может
 * быть удалена, а файл остаётся на диске. Список скачанного хранится
 * отдельно, чтобы пользователь мог вернуться к файлу позже или удалить
 * его из галереи.
 */
data class DownloadedFile(
    val id: String,
    /** Задача, из которой получился файл. Может быть `null`, если задачу удалили. */
    val taskId: String?,
    val title: String,
    val fileName: String,
    /** URI файла в MediaStore. */
    val uri: String,
    val mimeType: String,
    val sizeBytes: Long,
    val source: Source,
    val thumbnailUrl: String?,
    val createdAt: Long,
) {
    /** Размер файла для показа: «12,4 МБ». */
    val sizeLabel: String
        get() = when {
            sizeBytes >= 1024L * 1024 * 1024 ->
                String.format("%.1f ГБ", sizeBytes / (1024.0 * 1024 * 1024))
            sizeBytes >= 1024L * 1024 ->
                String.format("%.1f МБ", sizeBytes / (1024.0 * 1024))
            sizeBytes >= 1024L -> String.format("%.1f КБ", sizeBytes / 1024.0)
            else -> "$sizeBytes Б"
        }
}
