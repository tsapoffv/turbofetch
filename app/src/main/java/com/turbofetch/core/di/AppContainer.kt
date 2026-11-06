package com.turbofetch.core.di

import android.content.Context
import androidx.work.WorkManager
import com.turbofetch.data.db.TurboFetchDatabase
import com.turbofetch.data.remote.HttpClientFactory
import com.turbofetch.data.repo.SettingsRepository
import com.turbofetch.data.repo.TaskRepository
import com.turbofetch.data.resolver.TikTokResolver
import com.turbofetch.data.resolver.XResolver
import com.turbofetch.data.storage.MediaStoreWriter
import com.turbofetch.domain.engine.DownloadEngine
import com.turbofetch.domain.engine.RealDownloadEngine
import com.turbofetch.domain.model.Source
import com.turbofetch.domain.resolver.VideoResolver

/**
 * Ручной контейнер зависимостей: проект маленький, DI-фреймворк был бы лишним.
 * Здесь же — единственное место, где выбирается реализация движка.
 */
class AppContainer(context: Context) {

    private val appContext = context.applicationContext

    private val database: TurboFetchDatabase by lazy { TurboFetchDatabase.get(appContext) }

    val taskRepository: TaskRepository by lazy { TaskRepository(database.taskDao()) }

    val settingsRepository: SettingsRepository by lazy { SettingsRepository(database.settingsDao()) }

    val mediaStoreWriter: MediaStoreWriter by lazy { MediaStoreWriter(appContext) }

    val workManager: WorkManager by lazy { WorkManager.getInstance(appContext) }

    /**
     * Один HTTP-клиент на всё приложение: у него общее хранилище cookie,
     * а TikTok отдаёт файл по CDN только с cookie того же клиента.
     */
    private val httpClient by lazy { HttpClientFactory.create() }

    private val resolvers: Map<Source, VideoResolver> by lazy {
        mapOf<Source, VideoResolver>(
            Source.TIKTOK to TikTokResolver(httpClient),
            Source.X to XResolver(httpClient),
        )
    }

    /** Настоящий движок: резолв по площадке + скачивание напрямую с CDN. */
    val downloadEngine: DownloadEngine by lazy { RealDownloadEngine(resolvers, httpClient) }
}
