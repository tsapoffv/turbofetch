package com.turbofetch.data.repo

import com.turbofetch.data.db.TaskDao
import com.turbofetch.data.db.toDomain
import com.turbofetch.data.db.toEntity
import com.turbofetch.domain.model.DownloadTask
import com.turbofetch.domain.model.TaskStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

/** Единственная точка доступа к задачам для UI и воркеров. */
class TaskRepository(private val dao: TaskDao) {

    /**
     * Прогресс загрузок — в памяти, а не в базе.
     *
     * Раньше прогресс писался в SQLite на каждый прочитанный чанк (64 КБ).
     * Одна такая запись — это SELECT строки задачи плюс UPDATE всех её
     * колонок, включая JSON со списком форматов (десятки килобайт). Любая
     * запись в таблицу `tasks` инвалидирует подписку UI, и весь список
     * задач перечитывается и разбирается заново — при десяти загрузках
     * это сотни полных перечитываний в секунду, и интерфейс встаёт.
     *
     * Теперь прогресс живёт в оперативной памяти и доезжает до интерфейса
     * отдельным потоком. В базу он не пишется: при перезапуске процесса
     * загрузка всё равно начинается заново или докачивается, а полоса
     * прогресса для прерванной задачи ничего не значит.
     */
    private val liveProgress = MutableStateFlow<Map<String, Float>>(emptyMap())

    /** Текущий прогресс всех идущих загрузок: id задачи → доля от 0 до 1. */
    fun observeProgress(): StateFlow<Map<String, Float>> = liveProgress.asStateFlow()

    fun observeAll(): Flow<List<DownloadTask>> =
        dao.observeAll().map { list -> list.map { it.toDomain() } }

    fun observe(id: String): Flow<DownloadTask?> =
        dao.observe(id).map { it?.toDomain() }

    suspend fun get(id: String): DownloadTask? = dao.get(id)?.toDomain()

    suspend fun upsert(task: DownloadTask) = dao.upsert(task.toEntity())

    suspend fun delete(id: String) {
        dao.delete(id)
        liveProgress.update { it - id }
    }

    suspend fun clear() {
        dao.clear()
        liveProgress.value = emptyMap()
    }

    suspend fun updateStatus(id: String, status: TaskStatus, error: String? = null) {
        val current = dao.get(id) ?: return
        dao.update(
            current.copy(
                status = status.name,
                errorMessage = error,
                progress = if (status == TaskStatus.PENDING) 0f else current.progress,
            ),
        )
        // Задача ушла из состояния «качается» — её прогресс больше не нужен.
        if (status == TaskStatus.PENDING || status.isTerminal) {
            liveProgress.update { it - id }
        }
    }

    /**
     * Обновить прогресс загрузки.
     *
     * Пишет только в память: см. комментарий к [liveProgress].
     */
    suspend fun updateProgress(id: String, progress: Float) {
        val value = progress.coerceIn(0f, 1f)
        liveProgress.update { current ->
            if (current[id] == value) current else current + (id to value)
        }
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
