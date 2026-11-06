package com.turbofetch.data.repo

import com.turbofetch.data.db.SettingsDao
import com.turbofetch.data.db.SettingsEntity
import com.turbofetch.data.db.toDomain
import com.turbofetch.domain.model.AppSettings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class SettingsRepository(private val dao: SettingsDao) {

    fun observe(): Flow<AppSettings> =
        dao.observe(SettingsEntity.SINGLETON_ID).map { it?.toDomain() ?: AppSettings() }

    suspend fun current(): AppSettings =
        dao.get(SettingsEntity.SINGLETON_ID)?.toDomain() ?: AppSettings()

    suspend fun save(settings: AppSettings) {
        dao.upsert(SettingsEntity.from(settings))
    }
}
