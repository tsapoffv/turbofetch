package com.turbofetch.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

@Database(
    entities = [TaskEntity::class, SettingsEntity::class],
    version = 1,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class TurboFetchDatabase : RoomDatabase() {

    abstract fun taskDao(): TaskDao

    abstract fun settingsDao(): SettingsDao

    companion object {
        @Volatile
        private var instance: TurboFetchDatabase? = null

        fun get(context: Context): TurboFetchDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    TurboFetchDatabase::class.java,
                    "turbofetch.db",
                ).build().also { instance = it }
            }
    }
}
