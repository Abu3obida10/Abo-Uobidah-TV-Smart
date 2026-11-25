package com.uobidah.launcher

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.PowerManager

/** يستقبل منبّه الصلاة، يُبقي المعالج مستيقظاً لحظات، ويوقظ الخدمة لتنفيذ التنبيه فوراً. */
class PrayerWakeReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        try {
            val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
            pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "uobidah:prayer").acquire(90_000L)
        } catch (_: Exception) {
        }
        try {
            context.startForegroundService(Intent(context, DynamicIslandOverlayService::class.java).putExtra("wake", true))
        } catch (_: Exception) {
        }
    }
}
