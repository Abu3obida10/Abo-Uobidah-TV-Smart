package com.uobidah.launcher

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.RingtoneManager
import java.io.File

/**
 * إعدادات التنبيه لكل صلاة على حدة، ولكل حدث من ثلاثة:
 *  pre   = قبل الصلاة (بعدد دقائق)
 *  at    = وقت الصلاة (الأذان)
 *  iqama = الإقامة (بعد الأذان بعدد دقائق)
 * ولكل حدث: تفعيل، دقائق، رسالة مخصصة، ومصدر صوت (افتراضي / رابط / ملف / بدون).
 */
object PrayerAlerts {

    val events: List<String> = listOf("pre", "at", "iqama")

    fun eventTitle(ev: String): String = when (ev) {
        "pre" -> "⏰ قبل الصلاة"
        "at" -> "🕌 وقت الصلاة (الأذان)"
        else -> "🔔 الإقامة"
    }

    private fun k(prayer: String, ev: String, field: String) = "pa_" + prayer + "_" + ev + "_" + field

    fun enabled(p: Prefs, prayer: String, ev: String): Boolean =
        p.sp.getBoolean(k(prayer, ev, "on"), ev == "at")

    fun setEnabled(p: Prefs, prayer: String, ev: String, v: Boolean) {
        p.sp.edit().putBoolean(k(prayer, ev, "on"), v).apply()
    }

    fun defaultMinutes(prayer: String, ev: String): Int = when (ev) {
        "pre" -> 15
        "iqama" -> when (prayer) {
            "Fajr" -> 25
            "Maghrib" -> 10
            else -> 20
        }
        else -> 0
    }

    fun minutes(p: Prefs, prayer: String, ev: String): Int =
        p.sp.getInt(k(prayer, ev, "min"), defaultMinutes(prayer, ev))

    fun setMinutes(p: Prefs, prayer: String, ev: String, v: Int) {
        p.sp.edit().putInt(k(prayer, ev, "min"), v).apply()
    }

    // ---------- الصوت العام لكل نوع حدث (pre | at | iqama) ----------
    private fun gk(ev: String, field: String) = "pa_global_" + ev + "_" + field

    /** default | url | file | none */
    fun globalKind(p: Prefs, ev: String): String = p.sp.getString(gk(ev, "akind"), "default") ?: "default"
    fun globalValue(p: Prefs, ev: String): String = p.sp.getString(gk(ev, "aval"), "") ?: ""
    fun globalVolume(p: Prefs, ev: String): Int = p.sp.getInt(gk(ev, "vol"), 100).coerceIn(0, 100)

    fun setGlobalAudio(p: Prefs, ev: String, kind: String, value: String) {
        p.sp.edit().putString(gk(ev, "akind"), kind).putString(gk(ev, "aval"), value).apply()
    }

    fun setGlobalVolume(p: Prefs, ev: String, v: Int) {
        p.sp.edit().putInt(gk(ev, "vol"), v.coerceIn(0, 100)).apply()
    }

    /** يعيد الصوت العام لهذا الحدث إلى الافتراضي (لا يمس الصلوات المخصصة). */
    fun resetGlobal(p: Prefs, ev: String) {
        p.sp.edit().remove(gk(ev, "akind")).remove(gk(ev, "aval")).remove(gk(ev, "vol")).apply()
    }

    /** يلغي تخصيص كل الصلوات لهذا الحدث فتعود كلها لاستخدام الصوت العام. */
    fun applyGlobalToAll(p: Prefs, ev: String) {
        val ed = p.sp.edit()
        for (prayer in PrayerRepo.adhanKeys) ed.remove(k(prayer, ev, "akind")).remove(k(prayer, ev, "aval"))
        ed.apply()
    }

    // ---------- صوت كل صلاة ----------
    /** global (يرث العام) | default | url | file | none — الصوت المخزّن لهذه الصلاة فقط. */
    fun ownKind(p: Prefs, prayer: String, ev: String): String =
        p.sp.getString(k(prayer, ev, "akind"), null) ?: "global"

    fun usesGlobal(p: Prefs, prayer: String, ev: String): Boolean = ownKind(p, prayer, ev) == "global"

    /** النوع الفعلي بعد الوراثة. */
    fun audioKind(p: Prefs, prayer: String, ev: String): String {
        val own = ownKind(p, prayer, ev)
        return if (own == "global") globalKind(p, ev) else own
    }

    fun audioValue(p: Prefs, prayer: String, ev: String): String =
        if (usesGlobal(p, prayer, ev)) globalValue(p, ev) else (p.sp.getString(k(prayer, ev, "aval"), "") ?: "")

    fun setAudio(p: Prefs, prayer: String, ev: String, kind: String, value: String) {
        if (kind == "global") {
            p.sp.edit().remove(k(prayer, ev, "akind")).remove(k(prayer, ev, "aval")).apply()
        } else {
            p.sp.edit().putString(k(prayer, ev, "akind"), kind).putString(k(prayer, ev, "aval"), value).apply()
        }
    }

    fun defaultMessage(prayer: String, ev: String): String {
        val n = PrayerRepo.arabic[prayer] ?: prayer
        return when (ev) {
            "pre" -> "اقتربت صلاة $n"
            "at" -> "حان موعد صلاة $n"
            else -> "حان وقت إقامة صلاة $n"
        }
    }

    fun message(p: Prefs, prayer: String, ev: String): String {
        val custom = p.sp.getString(k(prayer, ev, "msg"), "") ?: ""
        return if (custom.isNotBlank()) custom else defaultMessage(prayer, ev)
    }

    fun setMessage(p: Prefs, prayer: String, ev: String, v: String) {
        p.sp.edit().putString(k(prayer, ev, "msg"), v.trim()).apply()
    }

    fun audioDir(ctx: Context): File {
        val d = File(ctx.filesDir, "prayer_audio")
        if (!d.exists()) d.mkdirs()
        return d
    }

    private fun describe(kind: String, value: String, ev: String): String = when (kind) {
        "none" -> "بدون صوت"
        "url" -> "رابط: " + value.take(40)
        "file" -> "ملف: " + File(value).name
        else -> if (ev == "at") "صوت الأذان الافتراضي" else "نغمة التنبيه الافتراضية"
    }

    fun globalLabel(p: Prefs, ev: String): String = describe(globalKind(p, ev), globalValue(p, ev), ev)

    fun audioLabel(p: Prefs, prayer: String, ev: String): String =
        if (usesGlobal(p, prayer, ev)) "يستخدم الصوت العام (" + globalLabel(p, ev) + ")"
        else "صوت مخصص لهذه الصلاة: " + describe(audioKind(p, prayer, ev), audioValue(p, prayer, ev), ev)

    /** ملخص قصير لشاشة قائمة الصلوات. */
    fun summary(p: Prefs, prayer: String): String {
        val parts = ArrayList<String>()
        if (enabled(p, prayer, "pre")) parts.add("قبل " + minutes(p, prayer, "pre") + "د")
        if (enabled(p, prayer, "at")) parts.add("الأذان")
        if (enabled(p, prayer, "iqama")) parts.add("إقامة +" + minutes(p, prayer, "iqama") + "د")
        return if (parts.isEmpty()) "كل التنبيهات متوقفة" else parts.joinToString("  •  ")
    }

    /**
     * يشغّل صوت الحدث. استدعِها من الخيط الرئيسي. يعيد MediaPlayer ليتحكم المستدعي في إيقافه،
     * أو null إن كان الحدث بدون صوت أو فشل التشغيل (ويُشغَّل صوت بديل عند الفشل).
     */
    fun play(ctx: Context, p: Prefs, prayer: String, ev: String): MediaPlayer? {
        val kind = audioKind(p, prayer, ev)
        if (kind == "none") return null
        val mp = MediaPlayer()
        try {
            mp.setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build()
            )
            val value = audioValue(p, prayer, ev)
            when {
                kind == "url" && value.startsWith("http") -> mp.setDataSource(value)
                kind == "file" && File(value).exists() -> mp.setDataSource(value)
                ev == "at" -> {
                    // الأولوية: ملف مدمج res/raw/adhan.mp3 إن وُجد، ثم رابط الأذان من الإعدادات
                    val rawId = ctx.resources.getIdentifier("adhan", "raw", ctx.packageName)
                    if (rawId != 0) {
                        val afd = ctx.resources.openRawResourceFd(rawId)
                        mp.setDataSource(afd.fileDescriptor, afd.startOffset, afd.length)
                        afd.close()
                    } else {
                        mp.setDataSource(p.adhanUrl)
                    }
                }
                else -> mp.setDataSource(ctx, RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION))
            }
            val vol = globalVolume(p, ev) / 100f
            mp.setVolume(vol, vol)
            mp.setOnPreparedListener { it.start() }
            mp.setOnCompletionListener {
                try {
                    it.release()
                } catch (e: Exception) {
                }
            }
            mp.setOnErrorListener { m, _, _ ->
                try {
                    m.release()
                } catch (e: Exception) {
                }
                fallback(ctx)
                true
            }
            mp.prepareAsync()
            return mp
        } catch (e: Exception) {
            try {
                mp.release()
            } catch (e2: Exception) {
            }
            fallback(ctx)
            return null
        }
    }

    fun fallback(ctx: Context) {
        try {
            val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            RingtoneManager.getRingtone(ctx, uri).play()
        } catch (e: Exception) {
        }
    }

    /** معاينة مصدر صوت لم يُحفظ بعد (رابط أو ملف). تُرجع المشغّل أو null عند الفشل. */
    fun preview(ctx: Context, p: Prefs, ev: String, kind: String, value: String): MediaPlayer? {
        if (kind == "none") return null
        val mp = MediaPlayer()
        return try {
            mp.setAudioAttributes(AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build())
            when {
                kind == "url" && value.startsWith("http") -> mp.setDataSource(value)
                kind == "file" && File(value).exists() -> mp.setDataSource(value)
                else -> mp.setDataSource(ctx, RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION))
            }
            val vol = globalVolume(p, ev) / 100f
            mp.setVolume(vol, vol)
            mp.setOnPreparedListener { it.start() }
            mp.setOnErrorListener { m, _, _ -> try { m.release() } catch (_: Exception) {}; true }
            mp.prepareAsync()
            mp
        } catch (e: Exception) {
            try { mp.release() } catch (_: Exception) {}
            null
        }
    }
}
