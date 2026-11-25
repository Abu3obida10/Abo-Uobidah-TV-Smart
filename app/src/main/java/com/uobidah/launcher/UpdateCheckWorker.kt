package com.uobidah.launcher

import android.content.Context
import androidx.work.Worker
import androidx.work.WorkerParameters

class UpdateCheckWorker(context: Context, params: WorkerParameters) : Worker(context, params) {
    override fun doWork(): Result {
        return when (val result = UpdateManager.checkAndDownload(applicationContext)) {
            is UpdateCheckResult.Ready -> {
                UpdateManager.notifyReady(applicationContext, result.versionName, result.versionCode)
                Result.success()
            }
            is UpdateCheckResult.Failed -> Result.retry()
            is UpdateCheckResult.NeedsRepository,
            is UpdateCheckResult.Current -> Result.success()
        }
    }
}
