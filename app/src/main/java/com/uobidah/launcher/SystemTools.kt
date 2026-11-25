package com.uobidah.launcher

import android.app.ActivityManager
import android.content.Context

/** أدوات معلومات الذاكرة التي يستدعيها المستخدم يدويًا من داخل اللانشر. */
object SystemTools {
    /**
     * يعيد مقدار الذاكرة المتاحة حاليًا بالميغابايت.
     * لا يقتل تطبيقات أخرى ولا يدّعي تحرير ذاكرة لا يمكن قياسها بدقة.
     */
    fun availableMemoryMb(context: Context): Long {
        return try {
            val manager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            val info = ActivityManager.MemoryInfo()
            manager.getMemoryInfo(info)
            info.availMem / (1024L * 1024L)
        } catch (_: Exception) {
            0L
        }
    }
}
