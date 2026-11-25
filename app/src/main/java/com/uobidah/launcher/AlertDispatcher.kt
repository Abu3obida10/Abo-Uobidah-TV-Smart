package com.uobidah.launcher

import android.content.Context
import com.uobidah.launcher.core.AlertGate
import com.uobidah.launcher.core.AlertType

/**
 * نقطة واحدة لكل التنبيهات (تطبيقات، مباريات، صلاة): تحسم السياسة، تمنع التكرار والتداخل،
 * ثم تعرض الجزيرة وتشغّل الصوت. كل الاستدعاءات القديمة المباشرة لـ IslandController/NotificationSound
 * في المسارات الآلية أصبحت تمر من هنا.
 */
object AlertDispatcher {
    private val gate = AlertGate()

    /** @param externalSound true إن كان الصوت يُشغَّل من مكان آخر (مثل أذان الصلاة) فلا نكرره هنا. */
    fun dispatch(
        ctx: Context, type: AlertType, pkg: String?, key: String,
        title: String, message: String, icon: String, externalSound: Boolean = false,
        eventSoundRes: Int = 0
    ) {
        val app = ctx.applicationContext
        val prefs = Prefs(app)
        val r = prefs.notificationPolicy().resolve(type, pkg)
        // مفاتيح الفئات القديمة تبقى مفاتيح رئيسية لكل فئة في شاشة الجزيرة.
        val categoryIsland = when (type) {
            AlertType.APP -> prefs.islandAppAlerts
            AlertType.MATCH_START, AlertType.MATCH_GOAL, AlertType.MATCH_END -> prefs.islandMatchAlerts
            AlertType.ADHAN, AlertType.IQAMA -> prefs.islandAdhanAlerts
            AlertType.SYSTEM -> true
        }
        val categorySound = when (type) {
            AlertType.MATCH_START, AlertType.MATCH_GOAL, AlertType.MATCH_END -> prefs.matchSoundEnabled
            else -> true
        }
        // المفتاح العام للصوت يغلب أي قاعدة تطبيق/نوع.
        val wantsSound = r.playSound && categorySound && prefs.alertSoundEnabled && !externalSound
        // صوت حدث المباراة (هدف/صافرة): له مفتاحه الخاص لكنه يحترم مفتاح الصوت العام وطبقة السياسة
        val eventWants = eventSoundRes != 0 && prefs.matchEventSounds && r.playSound && r.volume > 0 && prefs.alertSoundEnabled && !externalSound
        val adm = gate.admit(type.key + "|" + key, wantsSound || eventWants, System.currentTimeMillis())
        if (!adm.show) return
        if (r.showInIsland && categoryIsland && prefs.islandEnabled) {
            IslandController.show(app, title, message, icon, r.durationSec, r.position)
        }
        if (eventWants && adm.sound) {
            NotificationSound.playRaw(app, eventSoundRes, r.volume)
            if (r.vibrate) NotificationSound.playResolved(app, r, false)
        } else if (adm.sound || r.vibrate) {
            NotificationSound.playResolved(app, r, adm.sound)
        }
    }
}
