package com.uobidah.launcher

import android.Manifest
import android.app.Activity
import android.app.role.RoleManager
import android.content.Context
import android.content.ComponentName
import android.provider.Settings
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import androidx.core.app.NotificationManagerCompat

/**
 * مركز التفعيل الإلزامي: يفحص حالة كل صلاحية يحتاجها اللانشر ويطلبها بأقوى طريقة يسمح بها النظام.
 * ملاحظة: أندرويد لا يسمح لأي تطبيق بتعيين نفسه افتراضيًا بصمت؛ لذلك نفتح نافذة/شاشة النظام الرسمية
 * ونكمل بقية الخطوات تلقائيًا، وأوامر ADB في FORCE_HOME_ADB تفعل كل شيء بدون أي ضغطة.
 */
object DefaultLauncher {

    // كان الفاصل 3 دقائق فتقتحم شاشة التفعيل استخدام اللانشر عشرات المرات في الساعة.
    // ساعة كاملة تكفي للتذكير دون مقاطعة المشاهدة، والتفعيل اليدوي متاح دائمًا من الإعدادات.
    const val PROMPT_GAP_MS = 60 * 60_000L
    const val REQ_ROLE = 12
    const val REQ_RUNTIME = 11

    class Item(val id: String, val title: String, val hint: String, val critical: Boolean, val granted: Boolean)

    fun isDefaultHome(ctx: Context): Boolean = try {
        val i = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        val ri = ctx.packageManager.resolveActivity(i, PackageManager.MATCH_DEFAULT_ONLY)
        ri?.activityInfo?.packageName == ctx.packageName
    } catch (_: Exception) {
        false
    }

    fun isHomeKeyServiceEnabled(ctx: Context): Boolean = isAccessibilityComponentEnabled(
        ctx, ComponentName(ctx, HomeKeyOverrideService::class.java)
    )

    fun isAccessibilityComponentEnabled(ctx: Context, component: ComponentName): Boolean {
        return try {
            val enabled = Settings.Secure.getString(ctx.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES).orEmpty()
            enabled.split(':').any { raw -> ComponentName.unflattenFromString(raw) == component }
        } catch (_: Exception) {
            false
        }
    }

    fun hasOverlay(ctx: Context): Boolean = IslandController.canDraw(ctx)

    fun hasNotifPermission(ctx: Context): Boolean =
        Build.VERSION.SDK_INT < 33 ||
            ctx.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    fun hasLocation(ctx: Context): Boolean =
        ctx.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED

    fun listenerEnabled(ctx: Context): Boolean = try {
        NotificationManagerCompat.getEnabledListenerPackages(ctx).contains(ctx.packageName)
    } catch (_: Exception) {
        false
    }

    fun batteryExempt(ctx: Context): Boolean = try {
        (ctx.getSystemService(Context.POWER_SERVICE) as PowerManager).isIgnoringBatteryOptimizations(ctx.packageName)
    } catch (_: Exception) {
        false
    }

    /** الأساسيات: افتراضي + العرض فوق التطبيقات. */
    fun criticalOk(ctx: Context): Boolean = isDefaultHome(ctx) && hasOverlay(ctx) && isHomeKeyServiceEnabled(ctx)

    fun canWriteSecure(ctx: Context): Boolean =
        ctx.checkSelfPermission("android.permission.WRITE_SECURE_SETTINGS") == PackageManager.PERMISSION_GRANTED

    /**
     * يفعّل خدمة Abo Uobidah TV لزر Home تلقائيًا بدون أي ضغطة، إذا مُنح التطبيق الإذن مرة واحدة عبر ADB:
     * adb shell pm grant com.uobidah.launcher android.permission.WRITE_SECURE_SETTINGS
     * بدون الإذن يرجع false ويُفتح مسار التفعيل اليدوي.
     */
    fun ensureHomeService(ctx: Context): Boolean {
        if (isHomeKeyServiceEnabled(ctx)) return true
        if (!canWriteSecure(ctx)) return false
        return try {
            val component = ComponentName(ctx, HomeKeyOverrideService::class.java)
            val cr = ctx.contentResolver
            val current = Settings.Secure.getString(cr, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES).orEmpty()
            val parts = current.split(':').filter { it.isNotBlank() }
            val already = parts.any { ComponentName.unflattenFromString(it) == component }
            if (!already) {
                Settings.Secure.putString(
                    cr, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
                    (parts + component.flattenToString()).joinToString(":")
                )
            }
            Settings.Secure.putInt(cr, Settings.Secure.ACCESSIBILITY_ENABLED, 1)
            true
        } catch (_: Exception) {
            false
        }
    }

    fun items(ctx: Context): List<Item> = listOf(
        Item("overlay", "🪟 العرض فوق كل التطبيقات",
            "للجزيرة الديناميكية وتنبيهات الأذان والمباريات فوق أي تطبيق.", true, hasOverlay(ctx)),
        Item("home", "🏠 تعيين اللانشر كتطبيق الشاشة الرئيسية الافتراضي",
            "يجعل زر Home في الريموت يفتح اللانشر دائمًا وبعد كل تشغيل.", true, isDefaultHome(ctx)),
        Item("home_accessibility", "♿ خدمة Abo Uobidah TV Home Control (إمكانية الدخول)",
            "تجبر النظام على فتح Abo Uobidah TV عند الضغط على زر Home، وتعيد اللانشر تلقائيًا إذا ظهر لانشر CVTE.", true, isHomeKeyServiceEnabled(ctx)),
        Item("listener", "🔔 الوصول إلى الإشعارات",
            "لعرض إشعارات التطبيقات داخل الجزيرة.", false, listenerEnabled(ctx)),
        Item("notif", "📣 إذن عرض الإشعارات",
            "لإشعار الخدمة وتنبيه عدم كون اللانشر افتراضيًا (أندرويد 13+).", false, hasNotifPermission(ctx)),
        Item("battery", "🔋 استثناء من توفير البطارية",
            "كي لا يوقف النظام خدمة الأذان في الخلفية.", false, batteryExempt(ctx)),
        Item("location", "📍 إذن الموقع",
            "لعرض اسم شبكة الواي فاي فقط.", false, hasLocation(ctx))
    )

    fun request(act: Activity, id: String) {
        when (id) {
            "home" -> requestHome(act)
            "home_accessibility" -> if (!ensureHomeService(act)) tryStart(act, Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            "overlay" -> requestOverlay(act)
            "listener" -> requestListener(act)
            "notif" -> if (Build.VERSION.SDK_INT >= 33) {
                try {
                    act.requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), REQ_RUNTIME)
                } catch (_: Exception) {
                }
            }
            "battery" -> requestBattery(act)
            "location" -> try {
                act.requestPermissions(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION), REQ_RUNTIME)
            } catch (_: Exception) {
            }
        }
    }

import android.Manifest
import android.app.Activity
import android.app.role.RoleManager
import android.content.Context
import android.content.ComponentName
import android.provider.Settings
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import androidx.core.app.NotificationManagerCompat

/**
 * مركز التفعيل الإلزامي: يفحص حالة كل صلاحية يحتاجها اللانشر ويطلبها بأقوى طريقة يسمح بها النظام.
 * ملاحظة: أندرويد لا يسمح لأي تطبيق بتعيين نفسه افتراضيًا بصمت؛ لذلك نفتح نافذة/شاشة النظام الرسمية
 * ونكمل بقية الخطوات تلقائيًا، وأوامر ADB في FORCE_HOME_ADB تفعل كل شيء بدون أي ضغطة.
 */
object DefaultLauncher {

    // كان الفاصل 3 دقائق فتقتحم شاشة التفعيل استخدام اللانشر عشرات المرات في الساعة.
    // ساعة كاملة تكفي للتذكير دون مقاطعة المشاهدة، والتفعيل اليدوي متاح دائمًا من الإعدادات.
    const val PROMPT_GAP_MS = 60 * 60_000L
    const val REQ_ROLE = 12
    const val REQ_RUNTIME = 11

    class Item(val id: String, val title: String, val hint: String, val critical: Boolean, val granted: Boolean)

    fun isDefaultHome(ctx: Context): Boolean = try {
        val i = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        val ri = ctx.packageManager.resolveActivity(i, PackageManager.MATCH_DEFAULT_ONLY)
        ri?.activityInfo?.packageName == ctx.packageName
    } catch (_: Exception) {
        false
    }

    fun isHomeKeyServiceEnabled(ctx: Context): Boolean = isAccessibilityComponentEnabled(
        ctx, ComponentName(ctx, HomeKeyOverrideService::class.java)
    )

    fun isAccessibilityComponentEnabled(ctx: Context, component: ComponentName): Boolean {
        return try {
            val enabled = Settings.Secure.getString(ctx.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES).orEmpty()
            enabled.split(':').any { raw -> ComponentName.unflattenFromString(raw) == component }
        } catch (_: Exception) {
            false
        }
    }

    fun hasOverlay(ctx: Context): Boolean = IslandController.canDraw(ctx)

    fun hasNotifPermission(ctx: Context): Boolean =
        Build.VERSION.SDK_INT < 33 ||
            ctx.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    fun hasLocation(ctx: Context): Boolean =
        ctx.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED

    fun listenerEnabled(ctx: Context): Boolean = try {
        NotificationManagerCompat.getEnabledListenerPackages(ctx).contains(ctx.packageName)
    } catch (_: Exception) {
        false
    }

    fun batteryExempt(ctx: Context): Boolean = try {
        (ctx.getSystemService(Context.POWER_SERVICE) as PowerManager).isIgnoringBatteryOptimizations(ctx.packageName)
    } catch (_: Exception) {
        false
    }

    /** الأساسيات: افتراضي + العرض فوق التطبيقات. */
    fun criticalOk(ctx: Context): Boolean = isDefaultHome(ctx) && hasOverlay(ctx) && isHomeKeyServiceEnabled(ctx)

    fun canWriteSecure(ctx: Context): Boolean =
        ctx.checkSelfPermission("android.permission.WRITE_SECURE_SETTINGS") == PackageManager.PERMISSION_GRANTED

    /**
     * يفعّل خدمة Abo Uobidah TV لزر Home تلقائيًا بدون أي ضغطة، إذا مُنح التطبيق الإذن مرة واحدة عبر ADB:
     * adb shell pm grant com.uobidah.launcher android.permission.WRITE_SECURE_SETTINGS
     * بدون الإذن يرجع false ويُفتح مسار التفعيل اليدوي.
     */
    fun ensureHomeService(ctx: Context): Boolean {
        if (isHomeKeyServiceEnabled(ctx)) return true
        if (!canWriteSecure(ctx)) return false
        return try {
            val component = ComponentName(ctx, HomeKeyOverrideService::class.java)
            val cr = ctx.contentResolver
            val current = Settings.Secure.getString(cr, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES).orEmpty()
            val parts = current.split(':').filter { it.isNotBlank() }
            val already = parts.any { ComponentName.unflattenFromString(it) == component }
            if (!already) {
                Settings.Secure.putString(
                    cr, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
                    (parts + component.flattenToString()).joinToString(":")
                )
            }
            Settings.Secure.putInt(cr, Settings.Secure.ACCESSIBILITY_ENABLED, 1)
            true
        } catch (_: Exception) {
            false
        }
    }

    fun items(ctx: Context): List<Item> = listOf(
        Item("overlay", "🪟 العرض فوق كل التطبيقات",
            "للجزيرة الديناميكية وتنبيهات الأذان والمباريات فوق أي تطبيق.", true, hasOverlay(ctx)),
        Item("home", "🏠 تعيين اللانشر كتطبيق الشاشة الرئيسية الافتراضي",
            "يجعل زر Home في الريموت يفتح اللانشر دائمًا وبعد كل تشغيل.", true, isDefaultHome(ctx)),
        Item("home_accessibility", "♿ خدمة Abo Uobidah TV Home Control (إمكانية الدخول)",
            "تجبر النظام على فتح Abo Uobidah TV عند الضغط على زر Home، وتعيد اللانشر تلقائيًا إذا ظهر لانشر CVTE.", true, isHomeKeyServiceEnabled(ctx)),
        Item("listener", "🔔 الوصول إلى الإشعارات",
            "لعرض إشعارات التطبيقات داخل الجزيرة.", false, listenerEnabled(ctx)),
        Item("notif", "📣 إذن عرض الإشعارات",
            "لإشعار الخدمة وتنبيه عدم كون اللانشر افتراضيًا (أندرويد 13+).", false, hasNotifPermission(ctx)),
        Item("battery", "🔋 استثناء من توفير البطارية",
            "كي لا يوقف النظام خدمة الأذان في الخلفية.", false, batteryExempt(ctx)),
        Item("location", "📍 إذن الموقع",
            "لعرض اسم شبكة الواي فاي فقط.", false, hasLocation(ctx))
    )

    fun request(act: Activity, id: String) {
        when (id) {
            "home" -> requestHome(act)
            "home_accessibility" -> if (!ensureHomeService(act)) tryStart(act, Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            "overlay" -> requestOverlay(act)
            "listener" -> requestListener(act)
            "notif" -> if (Build.VERSION.SDK_INT >= 33) {
                try {
                    act.requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), REQ_RUNTIME)
                } catch (_: Exception) {
                }
            }
            "battery" -> requestBattery(act)
            "location" -> try {
                act.requestPermissions(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION), REQ_RUNTIME)
            } catch (_: Exception) {
            }
        }
    }

    private fun tryStart(act: Activity, i: Intent): Boolean = try {
        act.startActivity(i)
        true
    } catch (_: Exception) {
        false
    }

    fun requestHome(act: Activity) {
        // 1) نافذة الدور الرسمية (أندرويد 10+) إن كانت متاحة على الجهاز
        if (Build.VERSION.SDK_INT >= 29) {
            try {
                val rm = act.getSystemService(RoleManager::class.java)
                if (rm != null && rm.isRoleAvailable(RoleManager.ROLE_HOME)) {
                    if (rm.isRoleHeld(RoleManager.ROLE_HOME)) return
                    act.startActivityForResult(rm.createRequestRoleIntent(RoleManager.ROLE_HOME), REQ_ROLE)
                    return
                }
            } catch (_: Exception) {
            }
        }
        // 2) شاشة اختيار تطبيق الشاشة الرئيسية
        if (tryStart(act, Intent(Settings.ACTION_HOME_SETTINGS))) return
        // 3) إطلاق HOME فيظهر منتقي النظام إن لم يوجد افتراضي
        if (tryStart(act, Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))) return
        // 4) التطبيقات الافتراضية ثم الإعدادات العامة
        if (tryStart(act, Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS))) return
        tryStart(act, Intent(Settings.ACTION_SETTINGS))
    }

    fun requestOverlay(act: Activity) {
        // صفحات الإذن الخاصة بالظهور فوق التطبيقات فقط. لا نفتح معلومات التطبيق أو الإعدادات العامة
        // لأنها كانت تعرض إذن الموقع بدل الإذن المطلوب.
        if (OverlayPermission.open(act) != null) return
        try {
            act.startActivity(Intent(act, IslandSettingsActivity::class.java).putExtra("help", true))
        } catch (_: Exception) {
        }
    }

    fun requestListener(act: Activity) {
        if (tryStart(act, Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))) return
        tryStart(act, Intent("android.settings.NOTIFICATION_LISTENER_SETTINGS"))
    }

    fun requestBattery(act: Activity) {
        val uri = Uri.parse("package:" + act.packageName)
        if (tryStart(act, Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, uri))) return
        tryStart(act, Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
    }

    /** يعرض شاشة التفعيل إن كان أحد الأساسيات ناقصًا (بفاصل ساعة كي لا تتكرر المقاطعة أثناء الاستخدام). */
    fun enforce(act: Activity, prefs: Prefs) {
        if (!prefs.enforceDefaultHome) return
        if (act is SetupActivity) return
        if (criticalOk(act)) return
        val now = System.currentTimeMillis()
        if (now - prefs.setupPromptMs < PROMPT_GAP_MS) return
        prefs.setupPromptMs = now
        try {
            act.startActivity(Intent(act, SetupActivity::class.java).putExtra("chain", !hasOverlay(act)))
        } catch (_: Exception) {
        }
    }
}
