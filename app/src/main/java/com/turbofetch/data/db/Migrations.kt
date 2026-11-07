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

    /** 3 → 4: в настройках появились тема оформления и тёмный режим. */
    val MIGRATION_3_4 = object : Migration(3, 4) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "ALTER TABLE `settings` ADD COLUMN `theme` TEXT NOT NULL DEFAULT 'WARM_PAPER'",
            )
            db.execSQL(
                "ALTER TABLE `settings` ADD COLUMN `darkMode` INTEGER NOT NULL DEFAULT 0",
            )
        }
    }

    /** 4 → 5: настройка «закрывать сразу после «Поделиться». */
    val MIGRATION_4_5 = object : Migration(4, 5) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "ALTER TABLE `settings` ADD COLUMN `closeOnShare` INTEGER NOT NULL DEFAULT 0",
            )
        }
    }

    /** 5 → 6: треки альбома/плейлиста и исходная ссылка файла. */
    val MIGRATION_5_6 = object : Migration(5, 6) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "ALTER TABLE `tasks` ADD COLUMN `collectionTracks` TEXT NOT NULL DEFAULT '[]'",
            )
            db.execSQL(
                "ALTER TABLE `tasks` ADD COLUMN `parentTitle` TEXT",
            )
            db.execSQL(
                "ALTER TABLE `files` ADD COLUMN `sourceUrl` TEXT",
            )
        }
    }

    /** Все миграции по порядку. */
    val ALL: Array<Migration> = arrayOf(
        MIGRATION_2_3,
        MIGRATION_3_4,
        MIGRATION_4_5,
        MIGRATION_5_6,
    )
}
