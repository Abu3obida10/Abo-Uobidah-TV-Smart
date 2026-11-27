package com.uobidah.launcher

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import java.util.concurrent.TimeUnit

/**
 * تحديث خلفي مهذّب لمباريات الأندية المفضلة: كل 6 ساعات على شبكة متصلة،
 * يملأ الكاش المحلي حتى تظهر بطاقة المباراة والإشعارات فورًا وتعمل دون اتصال.
 * أثناء المباريات الجارية يحدّث كل ساعة (الحد الأدنى الدوري لنظام WorkManager)،
 * والتحديث الحي الكثيف يحدث فقط أثناء فتح شاشة المباريات أو تفاصيل لقاء.
 */
class MatchRefreshWorker(context: Context, params: WorkerParameters) : Worker(context, params) {
    override fun doWork(): Result {
        val app = applicationContext
        return try {
            val prefs = Prefs(app)
            val teams = MatchRepo.allTeams(prefs)
            for (team in teams) {
                runCatching { MatchRepo.next(prefs, team.id) }
                runCatching { MatchRepo.last(prefs, team.id) }
            }
            Result.success()
        } catch (_: Exception) {
            Result.retry()
        }
    }

    companion object {
        private const val PERIODIC = "match_refresh_periodic"

        fun schedule(app: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()
            val request = PeriodicWorkRequestBuilder<MatchRefreshWorker>(6, TimeUnit.HOURS)
                .setConstraints(constraints)
                .build()
            WorkManager.getInstance(app).enqueueUniquePeriodicWork(
                PERIODIC, ExistingPeriodicWorkPolicy.KEEP, request
            )
        }
    }
}
