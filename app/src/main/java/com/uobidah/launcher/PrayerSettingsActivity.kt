package com.uobidah.launcher

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.media.MediaPlayer
import android.net.Uri
import android.os.Bundle
import java.io.File

/**
 * إعدادات الصلاة.
 * بدون extra "prayer": قائمة الصلوات الخمس. ومع extra: شاشة تنبيهات تلك الصلاة
 * (قبل الصلاة • الأذان • الإقامة) مع الرسالة والصوت لكل حدث.
 */
class PrayerSettingsActivity : SettingsBase() {

    private var prayer: String? = null
    private var testPlayer: MediaPlayer? = null
    private var pendingEv = ""
    private var pendingTarget: String? = null   // null = الصوت العام
    private var globalMode = false

    private val preOptions = listOf(5, 10, 15, 20, 30, 45)
    private val iqamaOptions = listOf(5, 10, 15, 20, 25, 30)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prayer = intent.getStringExtra("prayer")
        globalMode = intent.getBooleanExtra("global", false)
        val p = prayer
        if (globalMode) buildGlobal()
        else if (p == null || !PrayerRepo.adhanKeys.contains(p)) buildList() else buildDetail(p)
    }

    override fun onResume() {
        super.onResume()
    }

    override fun onDestroy() {
        stopTest()
        super.onDestroy()
    }

    private fun stopTest() {
        try {
            testPlayer?.release()
        } catch (e: Exception) {
        }
        testPlayer = null
    }

    // ------------------------------------------------------------------
    private fun buildList() {
        setupScreen("🕌 إعدادات الصلوات")
        com.uobidah.launcher.core.Qibla.label(prefs.cityLat, prefs.cityLon)?.let { note("🧭 $it (من الشمال الحقيقي باتجاه عقارب الساعة، حسب مدينتك المضبوطة)") }
        note("اختر صلاة لضبط تنبيه «قبل الصلاة» و«الأذان» و«الإقامة» ولكل واحد صوته ورسالته.")
        for (k in PrayerRepo.adhanKeys) {
            val name = PrayerRepo.arabic[k] ?: k
            action("🕌 " + name + "   —   " + PrayerAlerts.summary(prefs, k)) {
                val i = Intent(this, PrayerSettingsActivity::class.java)
                i.putExtra("prayer", k)
                startActivity(i)
            }
        }
        section("🔊 عام")
        action("🎵 الأصوات العامة (الأذان • الإقامة • قبل الصلاة) لكل الصلوات") {
            val i = Intent(this, PrayerSettingsActivity::class.java)
            i.putExtra("global", true)
            startActivity(i)
        }
        toggle("تشغيل صوت الأذان تلقائياً (المفتاح العام لكل الصلوات)", { prefs.adhanEnabled }, { prefs.adhanEnabled = it })
        toggle("إظهار تنبيهات الصلاة في الجزيرة الديناميكية", { prefs.islandAdhanAlerts }, { prefs.islandAdhanAlerts = it })
        finishScreen()
    }

    // ------------------------------------------------------------------
    private fun buildDetail(k: String) {
        val name = PrayerRepo.arabic[k] ?: k
        setupScreen("🕌 صلاة $name")
        for (ev in PrayerAlerts.events) {
            section(PrayerAlerts.eventTitle(ev))
            toggle("تفعيل هذا التنبيه", { PrayerAlerts.enabled(prefs, k, ev) }, { PrayerAlerts.setEnabled(prefs, k, ev, it) })

            if (ev == "pre" || ev == "iqama") {
                val opts = if (ev == "pre") preOptions else iqamaOptions
                val cur = PrayerAlerts.minutes(prefs, k, ev)
                note(if (ev == "pre") "قبل موعد الصلاة بـ:" else "بعد الأذان بـ:")
                chips("min_$ev", opts.map { "$it دقيقة" }, opts.indexOf(cur)) {
                    PrayerAlerts.setMinutes(prefs, k, ev, opts[it])
                    rebuild("min_$ev:$it")
                }
            }

            note("الرسالة: " + PrayerAlerts.message(prefs, k, ev))
            action("✏️ تعديل الرسالة") { editMessage(k, ev) }

            note("الصوت: " + PrayerAlerts.audioLabel(prefs, k, ev))
            val kinds = listOf("global", "default", "url", "file", "none")
            val names = listOf("العام", "الافتراضي", "رابط mp3", "ملف من الجهاز", "بدون صوت")
            chips("akind_$ev", names, kinds.indexOf(PrayerAlerts.ownKind(prefs, k, ev)).coerceAtLeast(0)) {
                when (kinds[it]) {
                    "url" -> editUrl(k, ev)
                    "file" -> pickFile(k, ev)
                    else -> {
                        PrayerAlerts.setAudio(prefs, k, ev, kinds[it], "")
                        rebuild("akind_$ev:$it")
                    }
                }
            }
            action("🔊 تجربة الصوت والتنبيه") { test(k, ev) }
        }
        action("↩️ استرجاع الإعدادات الافتراضية لهذه الصلاة") { reset(k) }
        finishScreen()
    }

    // ------------------------------------------------------------------
    private fun buildGlobal() {
        setupScreen("🎵 الأصوات العامة للصلوات")
        note("اختر صوتاً واحداً لكل نوع تنبيه ويُطبَّق على كل الصلوات التي لا تملك صوتاً مخصصاً. يمكنك بعدها تخصيص صلاة واحدة (مثلاً المغرب) من شاشتها دون التأثير على البقية.")
        for (ev in PrayerAlerts.events) {
            section(PrayerAlerts.eventTitle(ev))
            note("الصوت العام الحالي: " + PrayerAlerts.globalLabel(prefs, ev))
            val custom = PrayerRepo.adhanKeys.filter { !PrayerAlerts.usesGlobal(prefs, it, ev) }
            note(if (custom.isEmpty()) "كل الصلوات تستخدم الصوت العام."
                 else "صلوات بصوت مخصص: " + custom.joinToString("، ") { PrayerRepo.arabic[it] ?: it })
            val kinds = listOf("default", "url", "file", "none")
            val names = listOf("الافتراضي", "رابط mp3", "ملف من الجهاز", "بدون صوت")
            chips("gkind_$ev", names, kinds.indexOf(PrayerAlerts.globalKind(prefs, ev)).coerceAtLeast(0)) {
                when (kinds[it]) {
                    "url" -> editUrl(null, ev)
                    "file" -> pickFile(null, ev)
                    else -> {
                        PrayerAlerts.setGlobalAudio(prefs, ev, kinds[it], "")
                        rebuild("gkind_$ev:$it")
                    }
                }
            }
            val levels = listOf(0, 25, 50, 75, 100)
            note("مستوى الصوت:")
            chips("gvol_$ev", levels.map { "$it%" }, levels.indexOf(PrayerAlerts.globalVolume(prefs, ev)).coerceAtLeast(0)) {
                PrayerAlerts.setGlobalVolume(prefs, ev, levels[it])
                rebuild("gvol_$ev:$it")
            }
            action("▶ تجربة الصوت العام") {
                stopTest()
                testPlayer = PrayerAlerts.preview(this, prefs, ev, PrayerAlerts.globalKind(prefs, ev), PrayerAlerts.globalValue(prefs, ev))
                if (testPlayer == null && PrayerAlerts.globalKind(prefs, ev) != "none") toast("تعذر تشغيل الصوت")
            }
            action("✅ تطبيق الصوت العام على كل الصلوات") {
                PrayerAlerts.applyGlobalToAll(prefs, ev)
                toast("كل الصلوات تستخدم الآن الصوت العام لـ " + PrayerAlerts.eventTitle(ev))
                rebuild("")
            }
            action("↩️ استعادة الإعدادات العامة لهذا النوع") {
                PrayerAlerts.resetGlobal(prefs, ev)
                toast("أُعيد الصوت العام للافتراضي")
                rebuild("")
            }
        }
        finishScreen()
    }

    private fun editMessage(k: String, ev: String) {
        val input = Ui.edit(this, ut, PrayerAlerts.defaultMessage(k, ev))
        input.setText(PrayerAlerts.message(prefs, k, ev))
        Ui.modal(this, ut, "✏️ رسالة التنبيه", input,
            Btn("✔ حفظ") {
                PrayerAlerts.setMessage(prefs, k, ev, input.text.toString())
                rebuild("")
            },
            Btn("الرسالة الافتراضية") {
                PrayerAlerts.setMessage(prefs, k, ev, "")
                rebuild("")
            },
            Btn("إلغاء") {})
    }

    /** target = رمز الصلاة، أو null للصوت العام. */
    private fun editUrl(target: String?, ev: String, initial: String? = null) {
        val input = Ui.edit(this, ut, "https://example.com/sound.mp3")
        val old = if (target == null) PrayerAlerts.globalValue(prefs, ev) else PrayerAlerts.audioValue(prefs, target, ev)
        val start = initial ?: old
        if (start.startsWith("http")) input.setText(start)
        Ui.modal(this, ut, "🔗 رابط الصوت (mp3 مباشر)", input,
            Btn("✔ حفظ") {
                val v = input.text.toString().trim()
                if (v.startsWith("http")) {
                    if (target == null) PrayerAlerts.setGlobalAudio(prefs, ev, "url", v)
                    else PrayerAlerts.setAudio(prefs, target, ev, "url", v)
                    toast("تم حفظ رابط الصوت")
                } else {
                    toast("الرابط غير صحيح، يجب أن يبدأ بـ http")
                }
                rebuild("")
            },
            Btn("▶ معاينة") {
                val v = input.text.toString().trim()
                if (v.startsWith("http")) {
                    stopTest()
                    testPlayer = PrayerAlerts.preview(this, prefs, ev, "url", v)
                    if (testPlayer == null) toast("تعذر تشغيل الرابط")
                } else toast("الرابط غير صحيح")
                editUrl(target, ev, v)
            },
            Btn("إلغاء") { rebuild("") })
    }

    private fun pickFile(target: String?, ev: String) {
        pendingEv = ev
        pendingTarget = target
        try {
            val i = Intent(Intent.ACTION_OPEN_DOCUMENT)
            i.addCategory(Intent.CATEGORY_OPENABLE)
            i.type = "audio/*"
            startActivityForResult(i, 41)
        } catch (e: ActivityNotFoundException) {
            toast("جهازك لا يحتوي على مستعرض ملفات. استخدم «رابط mp3» بدلاً من ذلك")
        } catch (e: Exception) {
            toast("تعذر فتح مستعرض الملفات")
        }
    }

    @Suppress("OVERRIDE_DEPRECATION", "DEPRECATION")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != 41 || resultCode != Activity.RESULT_OK) return
        val uri: Uri = data?.data ?: return
        val ev = pendingEv
        val target = pendingTarget
        val tag = target ?: "global"
        val app = applicationContext
        toast("جاري نسخ الملف...")
        Net.bg {
            var ok = false
            val staging = File(PrayerAlerts.audioDir(app), "pending_" + tag + "_" + ev + ".audio")
            try {
                app.contentResolver.openInputStream(uri)?.use { input ->
                    staging.outputStream().use { input.copyTo(it) }
                }
                ok = staging.exists() && staging.length() > 0
            } catch (e: Exception) {
                ok = false
            }
            Net.ui {
                if (!ok) {
                    toast("تعذر قراءة الملف، جرّب ملفاً آخر أو رابطاً")
                    rebuild("")
                } else {
                    // معاينة قبل الحفظ: يُشغَّل الملف الآن ولا يُعتمد إلا بالضغط على «حفظ»
                    stopTest()
                    testPlayer = PrayerAlerts.preview(this, prefs, ev, "file", staging.absolutePath)
                    val msg = Ui.label(this, "يجري تشغيل الملف الآن للمعاينة. هل تريد اعتماده؟", 26f, ut.text)
                    Ui.modal(this, ut, "🔊 معاينة الملف الصوتي", msg,
                        Btn("✔ حفظ") {
                            stopTest()
                            val finalFile = File(PrayerAlerts.audioDir(app), tag + "_" + ev + ".audio")
                            try {
                                staging.copyTo(finalFile, overwrite = true)
                                staging.delete()
                                if (target == null) PrayerAlerts.setGlobalAudio(prefs, ev, "file", finalFile.absolutePath)
                                else PrayerAlerts.setAudio(prefs, target, ev, "file", finalFile.absolutePath)
                                toast("تم حفظ الملف الصوتي")
                            } catch (e: Exception) {
                                toast("تعذر حفظ الملف")
                            }
                            rebuild("")
                        },
                        Btn("إلغاء") {
                            stopTest()
                            staging.delete()
                            rebuild("")
                        })
                }
            }
        }
    }

    private fun test(k: String, ev: String) {
        stopTest()
        IslandController.show(this, "🕌 " + PrayerAlerts.message(prefs, k, ev), "هكذا يظهر التنبيه", "🕌")
        testPlayer = PrayerAlerts.play(this, prefs, k, ev)
    }

    private fun reset(k: String) {
        val ed = prefs.sp.edit()
        for (key in prefs.sp.all.keys) {
            if (key.startsWith("pa_" + k + "_")) ed.remove(key)
        }
        ed.apply()
        toast("تم استرجاع الإعدادات الافتراضية")
        rebuild("")
    }
}
