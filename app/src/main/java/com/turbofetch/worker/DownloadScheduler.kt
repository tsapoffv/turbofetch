package com.turbofetch.worker

import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf

/** Ставит и снимает загрузки в очереди WorkManager. */
class DownloadScheduler(private val workManager: WorkManager) {

    fun enqueue(taskId: String, wifiOnly: Boolean = false) {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(
                if (wifiOnly) NetworkType.UNMETERED else NetworkType.CONNECTED,
            )
            .build()

        val request = OneTimeWorkRequestBuilder<DownloadWorker>()
            .setInputData(workDataOf(DownloadWorker.KEY_TASK_ID to taskId))
            .setConstraints(constraints)
            .addTag(TAG_DOWNLOAD)
            .build()

        workManager.enqueueUniqueWork(
            uniqueName(taskId),
            ExistingWorkPolicy.REPLACE,
            request,
        )
    }

    fun cancel(taskId: String) {
        workManager.cancelUniqueWork(uniqueName(taskId))
    }

    private fun uniqueName(taskId: String) = "download-$taskId"

    companion object {
        const val TAG_DOWNLOAD = "download"
    }
}
