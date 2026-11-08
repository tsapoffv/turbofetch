package com.turbofetch.domain.engine

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Проверки троттлинга прогресса.
 *
 * Главное, что тут проверяется: событий прогресса должно быть мало, но
 * ни одно значимое изменение не должно потеряться. Раньше событие
 * отдавалось на каждый прочитанный 64 КБ — это сотни записей в базу в
 * секунду, и из-за них интерфейс вставал при десятке загрузок.
 */
class ProgressThrottleTest {

    /** Часы, которые двигаются только по запросу. */
    private class Clock(var now: Long = 0L) {
        fun advance(ms: Long) {
            now += ms
        }
    }

    @Test
    fun `первое событие проходит`() {
        val clock = Clock()
        val throttle = ProgressThrottle(now = { clock.now })
        assertTrue(throttle.shouldEmit(bytesRead = 1024, totalBytes = 100_000))
    }

    @Test
    fun `события чаще чем раз в 100 мс не проходят`() {
        val clock = Clock()
        val throttle = ProgressThrottle(now = { clock.now })
        assertTrue(throttle.shouldEmit(1_000, 100_000))
        // Процент изменился, но прошло меньше 100 мс — событие пропускаем.
        clock.advance(50)
        assertFalse(throttle.shouldEmit(2_000, 100_000))
        // Прошло достаточно — событие проходит.
        clock.advance(60)
        assertTrue(throttle.shouldEmit(3_000, 100_000))
    }

    @Test
    fun `процент не изменился — событие не проходит даже через время`() {
        val clock = Clock()
        val throttle = ProgressThrottle(now = { clock.now })
        assertTrue(throttle.shouldEmit(1_000, 100_000))
        // Тот же процент (10 %): интерфейсу показывать нечего.
        clock.advance(500)
        assertFalse(throttle.shouldEmit(1_050, 100_000))
    }

    @Test
    fun `пропущенный процент не теряется`() {
        val clock = Clock()
        val throttle = ProgressThrottle(now = { clock.now })
        assertTrue(throttle.shouldEmit(1_000, 100_000))
        clock.advance(10)
        // Новый процент, но рано: событие пропускаем.
        assertFalse(throttle.shouldEmit(5_000, 100_000))
        // Через 100 мс приходит уже следующий процент — он и показывается.
        clock.advance(100)
        assertTrue(throttle.shouldEmit(6_000, 100_000))
    }

    @Test
    fun `последний процент проходит всегда`() {
        val clock = Clock()
        val throttle = ProgressThrottle(now = { clock.now })
        assertTrue(throttle.shouldEmit(1_000, 100_000))
        // Только что отдали событие — но 100 % показывать надо.
        clock.advance(1)
        assertTrue(throttle.shouldEmit(100_000, 100_000))
    }

    @Test
    fun `без известного размера событий нет`() {
        val clock = Clock()
        val throttle = ProgressThrottle(now = { clock.now })
        assertFalse(throttle.shouldEmit(1_000, null))
        clock.advance(1_000)
        assertFalse(throttle.shouldEmit(2_000, null))
        assertFalse(throttle.shouldEmit(3_000, 0L))
    }

    @Test
    fun `за секунду событий не больше десятка`() {
        val clock = Clock()
        val throttle = ProgressThrottle(now = { clock.now })
        var events = 0
        // Файл на 4 МБ со скоростью 4 МБ/с, читаем по 64 КБ — как в движке.
        var read = 0L
        while (read < 4L * 1024 * 1024) {
            read += 64 * 1024
            clock.advance(16)
            if (throttle.shouldEmit(read, 4L * 1024 * 1024)) events++
        }
        // Без троттлинга было бы 64 события в секунду.
        assertTrue("событий слишком много: $events", events <= 12)
        assertTrue("событий слишком мало: $events", events >= 5)
    }
}
