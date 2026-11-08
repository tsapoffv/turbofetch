package com.turbofetch

import android.app.Activity
import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import android.os.Bundle
import com.turbofetch.core.di.AppContainer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.SupervisorJob

class TurboFetchApp : Application() {

    lateinit var container: AppContainer
        private set

    /**
     * Приложение сейчас на экране? Нужно, чтобы открыть готовый файл сразу:
     * из фонового сервиса Android всё равно не разрешает запустить activity.
     */
    @Volatile
    var isForeground: Boolean = false
        private set

    private var startedActivities = 0

    /**
     * Scope уровня приложения.
     *
     * Нужен для работы, которая должна пережить закрытие activity: при
     * «Поделиться» приложение может закрыться сразу, а ссылка — всё
     * равно должна попасть в очередь и разобраться.
     */
    val appScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        createNotificationChannels()
        registerActivityLifecycleCallbacks()
        warmUpResolvers()
    }

    /**
     * Прогрев резолверов в фоне.
     *
     * Инициализация NewPipeExtractor занимает десятки миллисекунд, и если
     * она случится прямо во время первого разбора ссылки — интерфейс
     * получит лишний кадр задержки. Поэтому делаем это заранее, на
     * фоновом потоке: к моменту, когда юзер вставит ссылку, всё уже готово.
     */
    private fun warmUpResolvers() {
        appScope.launch(Dispatchers.IO) {
            runCatching {
                com.turbofetch.data.resolver.youtube.NewPipeInitializer.ensure(container.httpClient)
            }.onFailure {
                com.turbofetch.core.Log.e("Не удалось прогреть резолверы", it)
            }
        }
    }

    private fun registerActivityLifecycleCallbacks() {
        registerActivityLifecycleCallbacks(object : ActivityLifecycleCallbacks {
            override fun onActivityStarted(activity: Activity) {
                startedActivities++
                isForeground = startedActivities > 0
            }

            override fun onActivityStopped(activity: Activity) {
                startedActivities = (startedActivities - 1).coerceAtLeast(0)
                isForeground = startedActivities > 0
            }

            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit
            override fun onActivityResumed(activity: Activity) = Unit
            override fun onActivityPaused(activity: Activity) = Unit
            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
            override fun onActivityDestroyed(activity: Activity) = Unit
        })
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = getSystemService(NotificationManager::class.java) ?: return
        val channel = NotificationChannel(
            CHANNEL_DOWNLOADS,
            "Загрузки",
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = "Уведомления о ходе скачивания"
            setShowBadge(false)
        }
        manager.createNotificationChannel(channel)
    }

    companion object {
        const val CHANNEL_DOWNLOADS = "downloads"
    }
}
