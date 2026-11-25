package com.uobidah.launcher

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** يشغّل خدمة الأذان والجزيرة بعد الإقلاع/التحديث، ويفتح اللانشر إن لم يكن الافتراضي. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val a = intent?.action ?: return
        val ok = a == Intent.ACTION_BOOT_COMPLETED ||
            a == Intent.ACTION_MY_PACKAGE_REPLACED ||
            a == "android.intent.action.QUICKBOOT_POWERON" ||
            a == "com.htc.intent.action.QUICKBOOT_POWERON"
        if (!ok) return
        DefaultLauncher.ensureHomeService(context)
        try {
            context.startForegroundService(Intent(context, DynamicIslandOverlayService::class.java))
        } catch (_: Exception) {
        }
        // مع إذن العرض فوق التطبيقات يسمح النظام بفتح نشاط من الخلفية
        if (!DefaultLauncher.isDefaultHome(context) && DefaultLauncher.hasOverlay(context)) {
            try {
                context.startActivity(
                    Intent(context, MainLauncherActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            } catch (_: Exception) {
            }
        }
    }
}
