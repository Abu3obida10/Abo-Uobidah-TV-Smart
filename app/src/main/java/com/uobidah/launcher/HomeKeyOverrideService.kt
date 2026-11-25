package com.uobidah.launcher

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Intent
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent

/**
 * خدمة زر Home. تعمل بطريقتين معًا لأن بعض الـ Firmware (CVTE/MT5862) يعترض Home:
 *  1) اعتراض مباشر لمفتاح HOME قبل النظام (onKeyEvent) وفتح Abo Uobidah TV فورًا.
 *  2) شبكة أمان: إذا ظهر لانشر الشركة في المقدمة (com.cvte.tv.launcher.mars HomeActivity)
 *     تُعاد Abo Uobidah TV تلقائيًا، حتى لو لم يصل مفتاح Home للخدمة أصلًا.
 * لا تعطّل ولا تحذف أي شيء من تطبيقات الشركة.
 */
class HomeKeyOverrideService : AccessibilityService() {

    private var lastLaunchMs = 0L
    private var windowStart = 0L
    private var windowCount = 0
    private var backoffUntil = 0L

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        runCatching {
            val info = serviceInfo
            if (info != null) {
                info.flags = info.flags or AccessibilityServiceInfo.FLAG_REQUEST_FILTER_KEY_EVENTS
                serviceInfo = info
            }
        }
        getSharedPreferences(STATE, MODE_PRIVATE).edit()
            .putLong("service_connected_ms", System.currentTimeMillis()).apply()
    }

    override fun onKeyEvent(event: KeyEvent): Boolean {
        if (event.keyCode != KeyEvent.KEYCODE_HOME) return false
        if (!Prefs(this).homeKeyCapture) return false

        if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0) {
            getSharedPreferences(STATE, MODE_PRIVATE).edit()
                .putLong("last_home_key_captured_ms", System.currentTimeMillis()).apply()
            openUobidah(force = true)
        }
        // نستهلك الضغط والإفلات معًا حتى لا يعالجهما النظام أو لانشر الشركة.
        return true
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null || event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val pkg = event.packageName?.toString() ?: return
        if (pkg == packageName) return
        if (!VENDOR_LAUNCHERS.contains(pkg)) return
        val cls = event.className?.toString().orEmpty()
        // نعيد التوجيه فقط عند ظهور شاشة Home الخاصة بالشركة، وليس نوافذ المصادر/الإعدادات.
        if (cls.isNotEmpty() && !cls.endsWith("HomeActivity") && !cls.contains("Launcher", ignoreCase = true)) return
        if (!Prefs(this).homeRedirectVendor) return
        openUobidah(force = false)
    }

    private fun openUobidah(force: Boolean) {
        val now = System.currentTimeMillis()
        if (!force) {
            if (now < backoffUntil) return
            if (now - lastLaunchMs < 1200L) return
            // حماية من الدخول في حلقة صراع مع لانشر الشركة
            if (now - windowStart > 6000L) { windowStart = now; windowCount = 0 }
            windowCount++
            if (windowCount > 4) { backoffUntil = now + 30_000L; return }
        }
        lastLaunchMs = now
        val launch = Intent(this, MainLauncherActivity::class.java).apply {
            action = Intent.ACTION_MAIN
            addCategory(Intent.CATEGORY_HOME)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        }
        val ok = runCatching { startActivity(launch) }.isSuccess
        if (ok && !force) {
            getSharedPreferences(STATE, MODE_PRIVATE).edit()
                .putLong("last_redirect_ms", now).apply()
        }
    }

    override fun onInterrupt() = Unit

    override fun onUnbind(intent: Intent?): Boolean {
        try { IslandController.dismiss(applicationContext) } catch (e: Exception) { }
        if (instance === this) instance = null
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        try { IslandController.dismiss(applicationContext) } catch (e: Exception) { }
        if (instance === this) instance = null
        super.onDestroy()
    }

    companion object {
        @Volatile var instance: HomeKeyOverrideService? = null
        const val STATE = "home_control"
        val VENDOR_LAUNCHERS = setOf(
            "com.cvte.tv.launcher.mars",
            "com.cvte.tv.launcher"
        )
    }
}
