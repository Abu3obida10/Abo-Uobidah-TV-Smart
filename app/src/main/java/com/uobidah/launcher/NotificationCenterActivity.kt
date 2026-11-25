package com.uobidah.launcher

import android.content.Intent
import android.media.RingtoneManager
import android.net.Uri
import android.os.Bundle
import com.uobidah.launcher.core.AlertRule
import com.uobidah.launcher.core.AlertType
import com.uobidah.launcher.core.SoundUri

/**
 * مركز الإشعارات: لكل نوع (تطبيقات، بداية/هدف/نهاية مباراة، أذان، إقامة، نظام) —
 * عرض في الجزيرة، تشغيل الصوت، النغمة، مستوى الصوت، المدة، الموضع، الاهتزاز، مع معاينة فورية.
 * القيم «افتراضي» = ترث من الإعداد العام. إعدادات كل تطبيق في شاشة «إعدادات كل تطبيق».
 */
class NotificationCenterActivity : SettingsBase() {
    private val pickTone = 81
    private var toneTarget: AlertType? = null

    private val volumes = listOf(-1, 0, 25, 50, 75, 100)
    private val durations = listOf(-1, 4, 8, 12, 20, 30)
    private val positions = listOf(-1, 0, 1, 2, 3, 4)
    private val posNames = listOf("افتراضي", "أعلى وسط", "أعلى يمين", "أعلى يسار", "أسفل وسط", "وسط الشاشة")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setupScreen("🔔 مركز الإشعارات والأصوات")
        val policy = prefs.notificationPolicy()
        toggle("تشغيل كل التنبيهات (مفتاح رئيسي)", { prefs.notificationPolicy().masterEnabled },
            { prefs.saveNotificationPolicy(prefs.notificationPolicy().withMaster(it)) })
        action("🔊 الإعداد العام: الصوت العام ومستواه ونغمته الافتراضية") {
            startActivity(Intent(this, AlertSoundSettingsActivity::class.java))
        }
        action("🔔 إعدادات كل تطبيق على حدة") { startActivity(Intent(this, AppsNotificationSettingsActivity::class.java)) }
        note("الأولوية: إعداد التطبيق ثم إعداد النوع ثم العام. «افتراضي» يعني الوراثة. لا يتكرر نفس التنبيه خلال ثوانٍ ولا يتداخل صوتان.")

        for (t in AlertType.values()) {
            if (t == AlertType.APP) continue // إعدادات التطبيقات في شاشتها (نوع APP نفسه يُعرض أدناه كقاعدة افتراضية للتطبيقات)
            typeSection(t, policy.perType[t] ?: AlertRule())
        }
        typeSection(AlertType.APP, policy.perType[AlertType.APP] ?: AlertRule(), appNote = true)
        finishScreen()
    }

    private fun typeSection(t: AlertType, rule: AlertRule, appNote: Boolean = false) {
        section(t.labelAr)
        if (appNote) note("هذه القاعدة الافتراضية لأي تطبيق لم تضبط له قاعدة خاصة. الوضع المبدئي: إخفاء وكتم حتى تختار التطبيقات.")
        val r = prefs.notificationPolicy().resolve(t)
        note("الحالي فعلياً: " + (if (r.showInIsland) "يظهر" else "مخفي") + " • " + (if (r.playSound) "صوت ${r.volume}%" else "بلا صوت") +
            " • " + r.durationSec + " ث • " + posNames[(r.position + 1).coerceIn(1, posNames.size - 1)])
        triToggle("عرض في الجزيرة", rule.showInIsland) { v -> prefs.updateTypeRule(t) { it.copy(showInIsland = v) }; rebuild("") }
        triToggle("تشغيل الصوت", rule.soundEnabled) { v -> prefs.updateTypeRule(t) { it.copy(soundEnabled = v) }; rebuild("") }
        triToggle("اهتزاز", rule.vibrate) { v -> prefs.updateTypeRule(t) { it.copy(vibrate = v) }; rebuild("") }
        chips("vol_" + t.key, volumes.map { if (it < 0) "صوت: افتراضي" else "$it%" }, volumes.indexOf(rule.volume).coerceAtLeast(0)) { i ->
            prefs.updateTypeRule(t) { it.copy(volume = volumes[i]) }; rebuild("vol_${t.key}:$i")
        }
        chips("dur_" + t.key, durations.map { if (it < 0) "مدة: افتراضي" else "$it ث" }, durations.indexOf(rule.durationSec).coerceAtLeast(0)) { i ->
            prefs.updateTypeRule(t) { it.copy(durationSec = durations[i]) }; rebuild("dur_${t.key}:$i")
        }
        chips("pos_" + t.key, posNames, positions.indexOf(rule.position).coerceAtLeast(0)) { i ->
            prefs.updateTypeRule(t) { it.copy(position = positions[i]) }; rebuild("pos_${t.key}:$i")
        }
        note("النغمة: " + when (rule.soundUri) { SoundUri.INHERIT -> "افتراضي (العامة)"; SoundUri.SILENT -> "بدون صوت"; SoundUri.DEFAULT -> "نغمة النظام"; else -> "مخصصة" })
        action("🎵 اختيار نغمة") { chooseTone(t, rule) }
        action("↺ النغمة الافتراضية (وراثة)") { prefs.updateTypeRule(t) { it.copy(soundUri = SoundUri.INHERIT) }; rebuild("") }
        action("▶ معاينة الآن") { preview(t) }
        action("⟲ إعادة ضبط هذا النوع") { prefs.updateTypeRule(t) { AlertRule() }; rebuild("") }
    }

    /** ثلاث حالات: افتراضي ← تشغيل ← إيقاف. */
    private fun triToggle(label: String, v: Boolean?, set: (Boolean?) -> Unit) {
        val txt = when (v) { null -> "◻ $label: افتراضي"; true -> "✅ $label: تشغيل"; false -> "⛔ $label: إيقاف" }
        action(txt) { set(when (v) { null -> true; true -> false; false -> null }) }
    }

    private fun preview(t: AlertType) {
        val r = prefs.notificationPolicy().resolve(t)
        IslandController.show(this, "معاينة: " + t.labelAr, "هكذا يظهر هذا النوع", "🔔", r.durationSec, r.position)
        NotificationSound.playResolved(this, r, true)
        if (!r.playSound) toast("الصوت متوقف لهذا النوع حالياً (مفتاح عام/صوت/مستوى/نغمة)")
    }

    private fun chooseTone(t: AlertType, rule: AlertRule) {
        toneTarget = t
        try {
            val i = Intent(RingtoneManager.ACTION_RINGTONE_PICKER).apply {
                putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_NOTIFICATION)
                putExtra(RingtoneManager.EXTRA_RINGTONE_TITLE, t.labelAr)
                putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true)
                putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, true)
            }
            startActivityForResult(i, pickTone)
        } catch (_: Exception) {
            toast("تعذر فتح اختيار النغمة على هذا الجهاز")
        }
    }

    @Suppress("DEPRECATION")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        val t = toneTarget ?: return
        if (requestCode != pickTone || resultCode != RESULT_OK) return
        val uri = data?.getParcelableExtra<Uri>(RingtoneManager.EXTRA_RINGTONE_PICKED_URI)
        prefs.updateTypeRule(t) { it.copy(soundUri = uri?.toString() ?: SoundUri.SILENT) }
        toast("تم حفظ النغمة")
        rebuild("")
    }
}
