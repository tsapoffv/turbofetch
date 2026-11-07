package com.turbofetch.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

@Database(
    entities = [TaskEntity::class, SettingsEntity::class, FileEntity::class],
    version = 5,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class TurboFetchDatabase : RoomDatabase() {

    abstract fun taskDao(): TaskDao

    abstract fun settingsDao(): SettingsDao

    abstract fun fileDao(): FileDao

    companion object {
        @Volatile
        private var instance: TurboFetchDatabase? = null

        fun get(context: Context): TurboFetchDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    TurboFetchDatabase::class.java,
                    "turbofetch.db",
                )
                    // Миграции на месте: очередь загрузок и настройки
                    // переживают обновление приложения.
                    .addMigrations(*Migrations.ALL)
                    .build().also { instance = it }
            }
    }
}
