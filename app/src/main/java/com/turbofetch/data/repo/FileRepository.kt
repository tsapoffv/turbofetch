package com.turbofetch.data.repo

import com.turbofetch.data.db.FileDao
import com.turbofetch.data.db.toDomain
import com.turbofetch.data.db.toEntity
import com.turbofetch.domain.model.DownloadedFile
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Хранилище уже скачанных файлов.
 *
 * Отдельно от [TaskRepository]: очередь живёт «одним днём», а скачанные
 * файлы — постоянная история, к которой можно вернуться.
 */
class FileRepository(private val dao: FileDao) {

    fun observeAll(): Flow<List<DownloadedFile>> =
        dao.observeAll().map { list -> list.map { it.toDomain() } }

    suspend fun get(id: String): DownloadedFile? = dao.get(id)?.toDomain()

    suspend fun upsert(file: DownloadedFile) = dao.upsert(file.toEntity())

    /** Убрать запись из списка. Сам файл с диска удаляет вызывающий код. */
    suspend fun delete(id: String) = dao.delete(id)

    /** Убрать все записи разом. */
    suspend fun clear() = dao.clear()

    suspend fun count(): Int = dao.count()
}
