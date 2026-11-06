package com.turbofetch.data.repo

import com.turbofetch.data.db.TaskDao
import com.turbofetch.data.db.toDomain
import com.turbofetch.data.db.toEntity
import com.turbofetch.domain.model.DownloadTask
import com.turbofetch.domain.model.TaskStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** Единственная точка доступа к задачам для UI и воркеров. */
class TaskRepository(private val dao: TaskDao) {

    fun observeAll(): Flow<List<DownloadTask>> =
        dao.observeAll().map { list -> list.map { it.toDomain() } }

    fun observe(id: String): Flow<DownloadTask?> =
        dao.observe(id).map { it?.toDomain() }

    suspend fun get(id: String): DownloadTask? = dao.get(id)?.toDomain()

    suspend fun upsert(task: DownloadTask) = dao.upsert(task.toEntity())

    suspend fun delete(id: String) = dao.delete(id)

    suspend fun clear() = dao.clear()

    suspend fun updateStatus(id: String, status: TaskStatus, error: String? = null) {
        val current = dao.get(id) ?: return
        dao.update(
            current.copy(
                status = status.name,
                errorMessage = error,
                progress = if (status == TaskStatus.PENDING) 0f else current.progress,
            ),
        )
    }

    suspend fun updateProgress(id: String, progress: Float) {
        val current = dao.get(id) ?: return
        dao.update(current.copy(progress = progress.coerceIn(0f, 1f)))
    }

    suspend fun updateOutput(id: String, uri: String) {
        val current = dao.get(id) ?: return
        dao.update(current.copy(outputUri = uri, status = TaskStatus.COMPLETED.name, progress = 1f))
    }

    /**
     * Пометить задачу на повтор: прямая ссылка протухла, но частично скачанный
     * файл сохраняем — следующая попытка продолжит его с места обрыва.
     */
    suspend fun markNeedsRefresh(id: String, message: String?) {
        val current = dao.get(id) ?: return
        dao.update(
            current.copy(
                status = TaskStatus.QUEUED.name,
                errorMessage = message,
                needsRefresh = true,
            ),
        )
    }

    /** Обновить позицию задачи в очереди. */
    suspend fun updateOrder(id: String, order: Int) {
        val current = dao.get(id) ?: return
        dao.update(current.copy(order = order))
    }

    /**
     * Переписать порядок очереди целиком: [ids] — новый порядок сверху вниз.
     */
    suspend fun reorder(ids: List<String>) {
        ids.forEachIndexed { index, id ->
            updateOrder(id, index)
        }
    }
}
