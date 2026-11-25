package com.uobidah.launcher

import android.os.Bundle
import android.text.InputType
import android.widget.EditText
import android.widget.TextView

/** شاشة إدارة التحديثات التلقائية لمستودع GitHub العام. */
class UpdateActivity : SettingsBase() {
    private lateinit var repositoryInput: EditText
    private lateinit var statusText: TextView
    private var checking = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setupScreen("التحديثات التلقائية")
        section("مصدر التحديث")
        note("أدخل اسم مستودع GitHub العام مرة واحدة بصيغة username/repository. بعد نشر إصدار جديد في Releases سيكتشفه التطبيق وينزّله تلقائياً عند توفر الإنترنت.")
        repositoryInput = EditText(this).apply {
            setText(prefs.updateRepository)
            hint = "username/repository"
            textSize = 23f
            setSingleLine(true)
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
            setTextColor(ut.text)
            setHintTextColor(ut.dim)
            setPadding(px(20f), px(15f), px(20f), px(15f))
            background = Ui.round(Surfaces.row(ut), px(16f).toFloat(), px(2f), Surfaces.rowBorder(ut))
        }
        (activeSection ?: body).addView(repositoryInput, Ui.lpm(Ui.MATCH, px(72f), 0f, 0, px(8f), 0, px(8f)))
        action("حفظ مصدر التحديث") { saveRepository() }

        section("الفحص التلقائي")
        toggle("الفحص التلقائي كل 6 ساعات", { prefs.autoUpdatesEnabled }, { enabled ->
            prefs.autoUpdatesEnabled = enabled
            if (enabled) UpdateScheduler.schedule(this) else UpdateScheduler.cancel(this)
            toast(if (enabled) "تم تفعيل الفحص التلقائي" else "تم إيقاف الفحص التلقائي")
        })
        note("يعمل الفحص في الخلفية عند توفر الإنترنت. عند العثور على تحديث، ينزّل التطبيق ملف APK ويتحقق من بصمته وشهادة توقيعه ثم يعرض إشعاراً.")

        section("قناة الاختبار")
        toggle("استقبال النسخ التجريبية (اختياري)", { prefs.testChannel }, { enabled ->
            prefs.testChannel = enabled
            toast(if (enabled) "تم تفعيل قناة الاختبار" else "رجعنا للإصدارات المستقرة فقط")
        })
        note("افتراضياً يتجاهل التطبيق النسخ التجريبية. عند التفعيل يعرض أحدث نسخة تجريبية أو مستقرة بنفس شروط التوقيع ورقم الإصدار.")

        section("حالة التحديث")
        statusText = Ui.label(this, currentStatus(), 21f, Surfaces.primaryText(ut, Surfaces.row(ut)), true).apply {
            setPadding(px(18f), px(18f), px(18f), px(18f))
            background = Ui.round(Surfaces.note(ut), px(16f).toFloat())
        }
        (activeSection ?: body).addView(statusText, Ui.lpm(Ui.MATCH, Ui.WRAP, 0f, 0, px(5f), 0, px(8f)))
        action("فحص الآن وتنزيل التحديث إن وُجد") { checkNow() }
        action("تثبيت التحديث الذي تم تنزيله") { installReady() }
        action("فتح إعداد إذن تثبيت التطبيقات") { openInstallPermission() }
        finishScreen()
        UpdateScheduler.schedule(this)
        requestNotificationPermissionIfNeeded()
    }

    override fun onResume() {
        super.onResume()
        if (::statusText.isInitialized) {
            statusText.text = currentStatus()
            val ready = UpdateManager.readyApk(this)
            if (ready != null) UpdateManager.notifyReady(this, prefs.updateReadyVersionName, prefs.updateReadyVersionCode)
        }
    }

    private fun saveRepository() {
        val normalized = UpdateManager.normalizeRepository(repositoryInput.text?.toString().orEmpty())
        if (normalized == null) {
            toast("اكتب المستودع بهذا الشكل: username/repository")
            repositoryInput.requestFocus()
            return
        }
        prefs.updateRepository = normalized
        UpdateScheduler.schedule(this)
        toast("تم حفظ مصدر التحديث: $normalized")
        checkNow()
    }

    private fun checkNow() {
        if (checking) return
        val repo = UpdateManager.normalizeRepository(prefs.updateRepository)
        if (repo == null) {
            statusText.text = "لم يتم تحديد مستودع التحديث بعد. اكتب username/repository واحفظه أولاً."
            repositoryInput.requestFocus()
            return
        }
        checking = true
        statusText.text = "جارٍ الاتصال بـ GitHub وفحص أحدث إصدار..."
        Net.bg {
            val result = UpdateManager.checkAndDownload(applicationContext, force = true)
            Net.ui {
                if (isFinishing || isDestroyed) return@ui
                checking = false
                statusText.text = when (result) {
                    is UpdateCheckResult.Ready -> "✅ الإصدار ${result.versionName} تم تنزيله والتحقق منه. اختر «تثبيت التحديث» لإكمال التحديث من خلال نافذة Android."
                    is UpdateCheckResult.Current -> "✅ التطبيق محدّث بالفعل. الإصدار الحالي ${result.versionName}."
                    is UpdateCheckResult.NeedsRepository -> "لم يتم إعداد مستودع GitHub."
                    is UpdateCheckResult.Failed -> "تعذر الفحص: ${result.message}"
                }
                if (result is UpdateCheckResult.Ready) UpdateManager.notifyReady(this, result.versionName, result.versionCode)
            }
        }
    }

    private fun installReady() {
        val file = UpdateManager.readyApk(this)
        if (file == null) {
            toast("لا يوجد تحديث صالح جاهز للتثبيت. افحص التحديثات أولاً.")
            return
        }
        try {
            val launched = UpdateManager.launchInstaller(this)
            if (!launched) toast("اسمح للتطبيق بتثبيت التطبيقات، ثم ارجع واضغط تثبيت مرة أخرى.")
        } catch (e: Exception) {
            toast("تعذر فتح مثبت Android: ${e.message ?: "خطأ غير معروف"}")
        }
    }

    private fun openInstallPermission() {
        if (android.os.Build.VERSION.SDK_INT >= 26) {
            try {
                startActivity(android.content.Intent(android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES)
                    .setData(android.net.Uri.parse("package:$packageName")))
            } catch (_: Exception) {
                startActivity(android.content.Intent(android.provider.Settings.ACTION_SECURITY_SETTINGS))
            }
        }
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (android.os.Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 19021)
        }
    }

    private fun currentStatus(): String {
        val current = UpdateManager.currentVersionName(this)
        val ready = UpdateManager.readyApk(this)
        return buildString {
            append("الإصدار المثبت: ").append(current)
            append("\nمستودع التحديث: ").append(prefs.updateRepository.ifBlank { "غير محدد" })
            append("\nالفحص التلقائي: ").append(if (prefs.autoUpdatesEnabled) "مفعّل" else "متوقف")
            if (ready != null) append("\n\nتحديث جاهز: ").append(prefs.updateReadyVersionName)
            else append("\n\nلا يوجد تحديث تم تنزيله حالياً.")
        }
    }
}
