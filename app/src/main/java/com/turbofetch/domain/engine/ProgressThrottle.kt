package com.turbofetch.domain.engine

/**
 * Ограничитель частоты событий прогресса.
 *
 * Скачивание идёт чанками по 64 КБ, и на каждом чанке движок отдавал
 * событие прогресса. Дальше по цепочке это событие доходило до SQLite,
 * где переписывалась **вся строка задачи** вместе с JSON-списком
 * форматов, а потом ещё и перечитывалась вся таблица — и интерфейс
 * перерисовывался целиком.
 *
 * При 4 МБ/с это 64 события в секунду на файл, при трёх параллельных
 * загрузках — около 200. При этом интерфейс показывает целый процент:
 * из 800 событий на файл полезны 100, остальные 700 меняли ровно
 * ничего, но каждое стоило транзакции и перерисовки.
 *
 * Здесь события пропускаются, если с прошлого прошло меньше
 * [minIntervalMs] **и** целый процент не изменился. Последнее событие
 * (процент 100) проходит всегда — иначе полоса остановится на 99 %.
 *
 * @param minIntervalMs минимальный интервал между событиями, мс
 * @param now функция «текущее время» — подменяется в тестах
 */
class ProgressThrottle(
    private val minIntervalMs: Long = 100L,
    private val now: () -> Long = { System.currentTimeMillis() },
) {

    private var emitted = false
    private var lastEmitAt = 0L
    private var lastPercent = -1

    /**
     * Пора ли отдавать событие прогресса.
     *
     * @param bytesRead сколько байт уже скачано
     * @param totalBytes размер файла, если известен
     */
    fun shouldEmit(bytesRead: Long, totalBytes: Long?): Boolean {
        val percent = percentOf(bytesRead, totalBytes)
        val known = percent >= 0

        // Размер неизвестен — двигаться нечему, процент всё равно 0.
        if (!known) return false

        val isLast = percent >= 100
        if (!isLast && percent == lastPercent) return false

        val nowMs = now()
        if (!isLast && emitted && nowMs - lastEmitAt < minIntervalMs) {
            // Слишком рано: запоминаем новый процент, но событие не отдаём.
            // Иначе следующий тик сравнивался бы со старым и «пропускал»
            // бы уже изменившийся процент.
            lastPercent = percent
            return false
        }

        emitted = true
        lastEmitAt = nowMs
        lastPercent = percent
        return true
    }

    /** Целый процент, -1 — размер неизвестен. */
    private fun percentOf(bytesRead: Long, totalBytes: Long?): Int {
        val total = totalBytes ?: return -1
        if (total <= 0) return -1
        return ((bytesRead.toDouble() / total) * 100).toInt().coerceIn(0, 100)
    }
}
