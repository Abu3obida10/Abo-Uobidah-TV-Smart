package com.uobidah.launcher

import android.content.Context
import android.content.Intent
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import java.util.concurrent.TimeUnit

/**
 * حارس دوري لصفة «الشاشة الرئيسية»: يعمل حتى واللانشر مغلق، على عكس فحص شاشة التفعيل
 * الذي لا يشتغل إلا عند فتح اللانشر (فلو استعاد لانشر الشركة الصدارة والمستخدم داخل
 * تطبيق آخر لن يلاحظ أحد). مهمتان:
 *  1) خدمة زر Home تتوقف كثيرًا على أجهزة CVTE؛ نعيد تفعيلها بصمت إن مُنحنا
 *     WRITE_SECURE_SETTINGS مرة واحدة عبر ADB.
 *  2) إن فقد التطبيق صفة الافتراضي نفتح شاشة التفعيل (مسار ROLE_HOME الرسمي) بمعدل
 *     PROMPT_GAP_MS كحد أقصى، فقط مع إذن العرض فوق التطبيقات الذي يسمح بالفتح من الخلفية.
 * ما لا يستطيعه أندرويد: لا يمكن لأي تطبيق تعيين نفسه افتراضيًا بصمت؛ الاختيار اليدوي
 * من نافذة النظام أو أمر ADB `cmd package set-home-activity` هما الطريقان الوحيدان.
 */
class HomeWatchdogWorker(context: Context, params: WorkerParameters) : Worker(context, params) {
    override fun doWork(): Result {
        val app = applicationContext
        if (!DefaultLauncher.isHomeKeyServiceEnabled(app)) {
            DefaultLauncher.ensureHomeService(app)
        }
        if (!DefaultLauncher.isDefaultHome(app)) {
            val prefs = Prefs(app)
            val now = System.currentTimeMillis()
            if (now - prefs.setupPromptMs >= DefaultLauncher.PROMPT_GAP_MS && DefaultLauncher.hasOverlay(app)) {
                prefs.setupPromptMs = now
                runCatching {
                    app.startActivity(
                        Intent(app, SetupActivity::class.java)
                            .putExtra("chain", true)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    )
                }
            }
        }
        return Result.success()
    }

    companion object {
        private const val PERIODIC = "home_watchdog_periodic"

        fun schedule(app: Context) {
            val request = PeriodicWorkRequestBuilder<HomeWatchdogWorker>(15, TimeUnit.MINUTES).build()
            WorkManager.getInstance(app).enqueueUniquePeriodicWork(
                PERIODIC, ExistingPeriodicWorkPolicy.KEEP, request
            )
        }
    }
}
