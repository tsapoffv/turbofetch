package com.turbofetch.core.di

import android.content.Context
import androidx.work.WorkManager
import com.turbofetch.data.db.TurboFetchDatabase
import com.turbofetch.data.media.FfmpegRunner
import com.turbofetch.data.remote.HttpClientFactory
import com.turbofetch.data.repo.FileRepository
import com.turbofetch.data.repo.SettingsRepository
import com.turbofetch.data.repo.TaskRepository
import com.turbofetch.data.resolver.TikTokResolver
import com.turbofetch.data.resolver.instagram.InstagramResolver
import com.turbofetch.data.resolver.soundcloud.SoundCloudResolver
import com.turbofetch.data.resolver.spotify.SpotifyResolver
import com.turbofetch.data.resolver.XResolver
import com.turbofetch.data.resolver.youtube.YouTubeResolver
import com.turbofetch.data.storage.MediaStoreWriter
import com.turbofetch.data.storage.MediaMuxerMerger
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

    /** История уже скачанных файлов. */
    val fileRepository: FileRepository by lazy { FileRepository(database.fileDao()) }

    val mediaStoreWriter: MediaStoreWriter by lazy { MediaStoreWriter(appContext) }

    /** Склейка видео и звука в один MP4 (штатные MediaExtractor/MediaMuxer). */
    val mediaMuxerMerger: MediaMuxerMerger by lazy { MediaMuxerMerger() }

    /** ffmpeg — нужен только для перекодирования в mp3. */
    val ffmpegRunner: FfmpegRunner by lazy { FfmpegRunner(appContext) }

    val workManager: WorkManager by lazy { WorkManager.getInstance(appContext) }

    /**
     * Один HTTP-клиент на всё приложение: у него общее хранилище cookie,
     * а TikTok отдаёт файл по CDN только с cookie того же клиента.
     */
    /** Общий HTTP-клиент: резолверы и догрузка обложек используют один. */
    val httpClient by lazy { HttpClientFactory.create() }

    private val resolvers: Map<Source, VideoResolver> by lazy {
        mapOf<Source, VideoResolver>(
            Source.TIKTOK to TikTokResolver(httpClient),
            Source.X to XResolver(httpClient),
            Source.YOUTUBE to YouTubeResolver(httpClient),
            Source.INSTAGRAM to InstagramResolver(httpClient),
            Source.SOUNDCLOUD to SoundCloudResolver(httpClient),
            Source.SPOTIFY to SpotifyResolver(httpClient),
        )
    }

    /** Настоящий движок: резолв по площадке + скачивание напрямую с CDN. */
    val downloadEngine: DownloadEngine by lazy { RealDownloadEngine(resolvers, httpClient) }
}
