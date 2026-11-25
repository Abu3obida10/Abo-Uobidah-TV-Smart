package com.uobidah.launcher

import android.app.Activity
import android.content.ComponentName
import android.content.Intent
import android.net.Uri
import android.provider.Settings

/**
 * فتح إذن «الظهور فوق التطبيقات» بأكثر من مسار، لأن بعض شاشات Android TV (CVTE وغيرها)
 * لا تحتوي صفحة ACTION_MANAGE_OVERLAY_PERMISSION الرسمية.
 */
object OverlayPermission {

    fun adbCommand(pkg: String): String = "adb shell appops set $pkg SYSTEM_ALERT_WINDOW allow"

    /** يسمح للانشر بتفعيل خدمة إمكانية الدخول (مسار الجزيرة البديل) بنفسه دون صفحة إعدادات. */
    fun adbSecureCommand(pkg: String): String = "adb shell pm grant $pkg android.permission.WRITE_SECURE_SETTINGS"

    /** @return اسم المسار الذي فُتح، أو null إذا فشلت كل المسارات. */
    fun open(act: Activity): String? {
        val pkg = act.packageName
        val uri = Uri.parse("package:$pkg")
        val candidates = listOf(
            "صفحة الإذن الرسمية" to Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, uri),
            "قائمة التطبيقات ذات الإذن" to Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION),
            "شاشة الإعدادات المخصصة" to Intent().setComponent(
                ComponentName("com.android.settings", "com.android.settings.Settings\$AppDrawOverlaySettingsActivity")
            ).setData(uri)
        )
        for ((name, intent) in candidates) {
            if (intent.resolveActivity(act.packageManager) == null) continue
            val ok = try {
                act.startActivity(intent)
                true
            } catch (_: Exception) {
                false
            }
            if (ok) return name
        }
        return null
    }
}
