package com.uobidah.launcher

import android.content.Intent
import android.media.RingtoneManager
import android.net.Uri
import android.os.Bundle

/** تحكم كامل بصوت إشعارات التطبيقات ونتائج المباريات. */
class AlertSoundSettingsActivity : SettingsBase() {
    private val requestTone = 72

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setupScreen("🔊 إعدادات صوت الإشعارات")
        toggle("تشغيل صوت إشعارات التطبيقات والمباريات", { prefs.alertSoundEnabled }, { prefs.alertSoundEnabled = it })
        toggle("الاهتزاز عند وصول التنبيه (إن كان الجهاز يدعمه)", { prefs.alertVibrationEnabled }, { prefs.alertVibrationEnabled = it })
        section("🎵 نغمة الإشعار")
        note("المصدر الحالي: " + when (prefs.alertSoundUri) { "silent" -> "بدون صوت"; "" -> "نغمة إشعارات النظام"; else -> "نغمة مخصصة" })
        action("اختيار نغمة من أصوات الجهاز") { chooseTone() }
        action("استخدام نغمة النظام الافتراضية") { prefs.alertSoundUri = ""; rebuild("") }
        action("بدون صوت") { prefs.alertSoundUri = "silent"; rebuild("") }
        section("🔉 مستوى الصوت")
        val levels = listOf(0, 20, 40, 60, 80, 100)
        chips("volume", levels.map { "$it%" }, levels.indexOf(prefs.alertSoundVolume).coerceAtLeast(0)) {
            prefs.alertSoundVolume = levels[it]
            rebuild("volume:$it")
        }
        action("▶ تجربة صوت الإشعار") {
            if (!prefs.alertSoundEnabled || prefs.alertSoundVolume == 0 || prefs.alertSoundUri == "silent") {
                toast("فعّل الصوت واختر نغمة ومستوى صوت أكبر من صفر أولاً")
            } else {
                NotificationSound.play(this, prefs)
                toast("تم طلب تشغيل نغمة الاختبار")
            }
        }
        note("هذا الصوت مخصص لإشعارات التطبيقات والمباريات. لكل صلاة إعداد مستقل لصوت الأذان والتنبيه والإقامة من قسم إعدادات الصلاة.")
        finishScreen()
    }

    private fun chooseTone() {
        try {
            val i = Intent(RingtoneManager.ACTION_RINGTONE_PICKER).apply {
                putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_NOTIFICATION)
                putExtra(RingtoneManager.EXTRA_RINGTONE_TITLE, "اختر صوت الإشعار")
                putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true)
                putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, true)
                putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI,
                    when (prefs.alertSoundUri) {
                        "silent" -> null
                        "" -> RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
                        else -> Uri.parse(prefs.alertSoundUri)
                    })
            }
            startActivityForResult(i, requestTone)
        } catch (_: Exception) {
            toast("تعذر فتح اختيار النغمة على هذا الجهاز")
        }
    }

    @Suppress("DEPRECATION")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != requestTone || resultCode != RESULT_OK) return
        val uri = data?.getParcelableExtra<Uri>(RingtoneManager.EXTRA_RINGTONE_PICKED_URI)
        prefs.alertSoundUri = uri?.toString() ?: "silent"
        toast("تم حفظ نغمة الإشعار")
        rebuild("")
    }
}
