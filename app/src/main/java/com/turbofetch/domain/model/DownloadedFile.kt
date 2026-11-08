package com.turbofetch.domain.model

import androidx.compose.runtime.Immutable

/**
 * Уже скачанный файл.
 *
 * Отдельная сущность от [DownloadTask]: задача живёт в очереди и может
 * быть удалена, а файл остаётся на диске. Список скачанного хранится
 * отдельно, чтобы пользователь мог вернуться к файлу позже или удалить
 * его из галереи.
 */
@Immutable
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
    /**
     * Исходная ссылка на площадке.
     *
     * Хранится рядом с файлом, а не берётся из задачи: задача после
     * скачивания удаляется из очереди, и ссылка была бы потеряна. По ней
     * юзер может открыть источник в браузере или скопировать ссылку.
     */
    val sourceUrl: String? = null,
) {
    /**
     * Размер файла для показа: «12,4 МБ».
     *
     * Форматируем вручную, а не через `String.format`: тот создаёт
     * `Formatter` и ходит в локаль на каждый вызов, а геттер дёргается
     * при каждой перерисовке строки списка.
     */
    val sizeLabel: String
        get() {
            val (value, unit) = when {
                sizeBytes >= 1024L * 1024 * 1024 ->
                    sizeBytes / (1024.0 * 1024 * 1024) to "ГБ"
                sizeBytes >= 1024L * 1024 ->
                    sizeBytes / (1024.0 * 1024) to "МБ"
                sizeBytes >= 1024L -> sizeBytes / 1024.0 to "КБ"
                else -> return "$sizeBytes Б"
            }
            val tenths = Math.round(value * 10) / 10.0
            // Запятая вместо точки — так размер читается по-русски.
            return "${tenths.toString().replace('.', ',')} $unit"
        }
}
