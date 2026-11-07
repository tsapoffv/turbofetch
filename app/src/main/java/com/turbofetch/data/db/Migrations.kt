package com.turbofetch.data.db

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Миграции базы.
 *
 * Раньше здесь стоял `fallbackToDestructiveMigration()`: при смене схемы
 * база пересоздавалась целиком, и очередь загрузок пропадала. Теперь
 * схема меняется на месте — очередь и настройки переживают обновление.
 */
object Migrations {

    /** 2 → 3: появилась таблица скачанных файлов. */
    val MIGRATION_2_3 = object : Migration(2, 3) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `files` (
                    `id` TEXT NOT NULL,
                    `taskId` TEXT,
                    `title` TEXT NOT NULL,
                    `fileName` TEXT NOT NULL,
                    `uri` TEXT NOT NULL,
                    `mimeType` TEXT NOT NULL,
                    `sizeBytes` INTEGER NOT NULL,
                    `source` TEXT NOT NULL,
                    `thumbnailUrl` TEXT,
                    `createdAt` INTEGER NOT NULL,
                    PRIMARY KEY(`id`)
                )
                """.trimIndent(),
            )
        }
    }

    /** Все миграции по порядку. */
    val ALL: Array<Migration> = arrayOf(MIGRATION_2_3)
}
