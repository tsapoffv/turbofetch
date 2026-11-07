package com.turbofetch.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.turbofetch.domain.model.AppSettings
import com.turbofetch.domain.model.AppTheme
import com.turbofetch.domain.model.QualityPreset

@Entity(tableName = "settings")
data class SettingsEntity(
    @PrimaryKey val id: Int = SINGLETON_ID,
    val defaultQuality: String,
    val defaultAudioOnly: Boolean,
    val fileNameTemplate: String,
    val openAfterDownload: Boolean,
    val downloadOverWifiOnly: Boolean,
    /** Тема оформления. */
    val theme: String,
    /** Тёмный вариант темы. */
    val darkMode: Boolean,
) {
    companion object {
        const val SINGLETON_ID = 1

        fun from(settings: AppSettings) = SettingsEntity(
            defaultQuality = settings.defaultQuality.name,
            defaultAudioOnly = settings.defaultAudioOnly,
            fileNameTemplate = settings.fileNameTemplate,
            openAfterDownload = settings.openAfterDownload,
            downloadOverWifiOnly = settings.downloadOverWifiOnly,
            theme = settings.theme.name,
            darkMode = settings.darkMode,
        )
    }
}

fun SettingsEntity.toDomain(): AppSettings = AppSettings(
    defaultQuality = runCatching { QualityPreset.valueOf(defaultQuality) }
        .getOrDefault(QualityPreset.BEST),
    defaultAudioOnly = defaultAudioOnly,
    fileNameTemplate = fileNameTemplate,
    openAfterDownload = openAfterDownload,
    downloadOverWifiOnly = downloadOverWifiOnly,
    theme = runCatching { AppTheme.valueOf(theme) }.getOrDefault(AppTheme.WARM_PAPER),
    darkMode = darkMode,
)
