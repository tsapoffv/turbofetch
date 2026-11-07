package com.turbofetch.data.resolver.youtube

import okhttp3.OkHttpClient
import org.schabi.newpipe.extractor.NewPipe
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Инициализация NewPipeExtractor — ровно один раз за процесс.
 *
 * `NewPipe.init(...)` — глобальный статик: повторный вызов пересоздаёт
 * загрузчик, а из двух резолверов (YouTube и Spotify) они могли вызвать его
 * одновременно — это гонка.
 *
 * Поэтому инициализация идемпотентна и защищена флагом: первый вызов
 * создаёт загрузчик, остальные — no-op.
 */
object NewPipeInitializer {

    private val initialized = AtomicBoolean(false)

    /** Инициализировать экстрактор, если ещё не инициализирован. */
    fun ensure(client: OkHttpClient) {
        if (initialized.compareAndSet(false, true)) {
            NewPipe.init(NewPipeDownloader(client))
        }
    }
}
