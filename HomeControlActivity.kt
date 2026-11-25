package com.uobidah.launcher

import android.accessibilityservice.AccessibilityServiceInfo
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.view.accessibility.AccessibilityManager

/** لوحة تفعيل Home، مع فحص حالة Home الافتراضي وخدمات CVTE وإمكانية الوصول. */
class HomeControlActivity : SettingsBase() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setupScreen("جعل Abo Uobidah TV الشاشة الرئيسية")
        render()
    }

    override fun onResume() {
        super.onResume()
        render()
    }

    private fun render() {
        // setupScreen() initializes the inherited protected body in onCreate()
        // before render() is first called. A lateinit property reference to a
        // superclass backing field is not accessible from this subclass.
        body.removeAllViews()

        val isDefault = DefaultLauncher.isDefaultHome(this)
        val ourService = DefaultLauncher.isHomeKeyServiceEnabled(this)
        val cvteService = findTvHomeOverrideService(this)
        val cvteEnabled = cvteService?.let { DefaultLauncher.isAccessibilityComponentEnabled(this, it) } == true

        section(if (isDefault) "✅ Abo Uobidah TV هو Home الافتراضي" else "⚠️ Abo Uobidah TV ليس Home الافتراضي")
        note(if (isDefault) {
            "أندرويد يحل طلب HOME إلى Abo Uobidah TV حاليًا. هذا لا يمنع خدمة CVTE من اعتراض زر الريموت وإطلاق واجهتها الخاصة."
        } else {
            "عيّن Abo Uobidah TV من شاشة Android الرسمية. لا يستطيع التطبيق اختيار نفسه بصمت دون موافقة النظام."
        })
        action("🏠 تعيين Abo Uobidah TV كتطبيق الشاشة الرئيسية الافتراضي") {
            DefaultLauncher.requestHome(this)
        }
        action("▶ فتح Abo Uobidah TV الآن") {
            startActivity(Intent(this, MainLauncherActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP))
        }

        section("التحكم بزر Home في تلفزيون CVTE")
        when {
            cvteService == null -> note("لم أستطع اكتشاف خدمة باسم TvHomeOverrideService من قائمة خدمات إمكانية الدخول المثبتة. افتح إعدادات إمكانية الدخول وابحث عنها يدويًا إذا كانت ظاهرة في التلفزيون.")
            cvteEnabled -> note("خدمة CVTE المكتشفة مفعّلة: ${cvteService.flattenToShortString()}")
            else -> note("تم اكتشاف خدمة CVTE: ${cvteService.flattenToShortString()} لكنها غير مفعّلة. افتح إعدادات إمكانية الدخول وفعّلها يدويًا إذا كانت هذه هي خدمة الشركة المقصودة.")
        }
        action(if (cvteEnabled) "✅ مراجعة خدمة TvHomeOverrideService في إعدادات إمكانية الدخول" else "♿ فتح إعداد TvHomeOverrideService") {
            openAccessibilitySettings(cvteService)
        }

        section("خدمة Abo Uobidah TV الاختيارية لاعتراض Home")
        note("خدمة Abo Uobidah TV Home Control تعمل بطريقتين: تلتقط زر Home قبل النظام وتفتح Abo Uobidah TV، وتعيد اللانشر تلقائيًا إذا ظهر لانشر CVTE رغم ذلك. فعّلها من إمكانية الدخول (أو تلقائيًا عبر ADB) ثم اختبر الريموت.")
        action(if (ourService) "✅ خدمة Abo Uobidah TV مفعّلة — فتح إعداداتها" else "♿ تفعيل خدمة Abo Uobidah TV Home Control") {
            openAccessibilitySettings(ComponentName(this, HomeKeyOverrideService::class.java))
        }
        val lastCaptured = getSharedPreferences("home_control", MODE_PRIVATE).getLong("last_home_key_captured_ms", 0L)
        note(if (lastCaptured > 0L) {
            "آخر ضغطة Home استقبلتها خدمة Abo Uobidah TV: ${android.text.format.DateFormat.getTimeFormat(this).format(java.util.Date(lastCaptured))}. جرّب زر Home مرة أخرى وتحقق هل يتغير الوقت."
        } else {
            "لم تسجل خدمة Abo Uobidah TV أي ضغطة Home حتى الآن. إذا لم يتغير هذا السطر بعد ضغط الريموت، فالنظام على الأرجح يعترض الزر قبل وصوله لخدمة إمكانية الدخول."
        })
        toggle("اعتراض زر Home من الريموت", { prefs.homeKeyCapture }, { prefs.homeKeyCapture = it })
        toggle("إعادة Abo Uobidah TV تلقائيًا إذا ظهر لانشر CVTE", { prefs.homeRedirectVendor }, { prefs.homeRedirectVendor = it })
        if (!DefaultLauncher.canWriteSecure(this)) {
            note("للتفعيل التلقائي بدون ضغطات: شغّل FORCE_HOME_ADB من الكمبيوتر مرة واحدة، فيمنح اللانشر إذن WRITE_SECURE_SETTINGS ويفعّل الخدمة ويحافظ عليها بعد كل إقلاع.")
        }
        action("🔄 إعادة فحص الحالة") { render() }

        section("معلومة مهمة")
        note("لا يوجد API عادي يسمح للتطبيق بإجبار نظام التلفزيون على تجاهل سياسة الشركة أو تعطيل لانشر النظام. تعيين Home يضبط الاختيار الرسمي فقط؛ أما تجاوز اعتراض CVTE فيتوقف على دعم Firmware لخدمة الشركة أو خدمة إمكانية الدخول. لا تعطّل LauncherMars أو خدمات النظام عشوائيًا.")
        finishScreen()
    }

    private fun openAccessibilitySettings(component: ComponentName? = null) {
        // بعض نسخ Android TV تدعم صفحة تفاصيل خدمة بعينها؛ هذا المسار مخفي/اختياري
        // في AOSP ولذلك نستخدم صفحة إمكانية الدخول العامة كمسار احتياطي دائم.
        if (component != null) {
            val detail = Intent("android.settings.ACCESSIBILITY_DETAILS_SETTINGS").apply {
                putExtra("android.intent.extra.COMPONENT_NAME", component)
            }
            val opened = runCatching {
                if (packageManager.resolveActivity(detail, 0) != null) {
                    startActivity(detail)
                    true
                } else false
            }.getOrDefault(false)
            if (opened) return
        }
        val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
        runCatching { startActivity(intent) }.onFailure {
            runCatching { startActivity(Intent(Settings.ACTION_SETTINGS)) }
                .onFailure { toast("افتح إعدادات التلفزيون ← إمكانية الدخول يدويًا") }
        }
    }

    private fun findTvHomeOverrideService(context: Context): ComponentName? = runCatching {
        val manager = context.getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager
        manager.installedAccessibilityServiceList.firstNotNullOfOrNull { info: AccessibilityServiceInfo ->
            val si = info.resolveInfo?.serviceInfo ?: return@firstNotNullOfOrNull null
            val label = runCatching { si.loadLabel(context.packageManager)?.toString().orEmpty() }.getOrDefault("")
            val description = runCatching { info.loadDescription(context.packageManager)?.toString().orEmpty() }.getOrDefault("")
            val candidates = listOf(si.name, si.packageName, label, description)
            if (candidates.any { it.contains("TvHomeOverrideService", ignoreCase = true) }) {
                ComponentName(si.packageName, si.name)
            } else null
        }
    }.getOrNull()
}
