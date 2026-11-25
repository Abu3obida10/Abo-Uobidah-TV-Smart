package com.uobidah.launcher

import android.content.Intent
import android.content.pm.ResolveInfo
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ScrollView

/** إعدادات مستقلة لكل تطبيق: إظهاره في الجزيرة وتشغيل صوت إشعاره أو إيقافه. */
class AppsNotificationSettingsActivity : BaseActivity() {
    private lateinit var body: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        build()
    }

    override fun onResume() {
        super.onResume()
        if (::body.isInitialized) build()
    }

    private fun build() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = bgGradient()
            setPadding(px(48f), px(26f), px(48f), px(20f))
        }
        root.addView(header("🔔 إعدادات إشعارات كل تطبيق"), Ui.lp(Ui.MATCH, Ui.WRAP))
        root.addView(Ui.label(this, "لكل تطبيق تحكم مستقل: عرض الإشعار في الجزيرة، وتشغيل صوته أو إيقافه. إعداد الصوت العام ومستوى الصوت من شاشة صوت الإشعارات.", 24f, ut.dim), Ui.lpm(Ui.MATCH, Ui.WRAP, 0f, 0, px(8f), 0, px(12f)))
        root.addView(Ui.button(this, ut, "🔔 مركز الإشعارات: إعدادات كل نوع (مباريات، أذان، …)", 22f) {
            startActivity(Intent(this, NotificationCenterActivity::class.java))
        }, Ui.lpm(Ui.WRAP, Ui.WRAP, 0f, 0, 0, 0, px(8f)))
        root.addView(Ui.button(this, ut, if (prefs.alertSoundEnabled) "🔊 الصوت العام يعمل — إدارة الصوت" else "🔇 الصوت العام متوقف — إدارة الصوت", 22f) {
            startActivity(Intent(this, AlertSoundSettingsActivity::class.java))
        }, Ui.lpm(Ui.WRAP, Ui.WRAP, 0f, 0, 0, 0, px(12f)))
        val scroll = ScrollView(this)
        body = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        scroll.addView(body, ViewGroup.LayoutParams(Ui.MATCH, Ui.WRAP))
        root.addView(scroll, Ui.lp(Ui.MATCH, 0, 1f))
        val pm = packageManager
        val found = LinkedHashMap<String, String>()
        for (cat in listOf(Intent.CATEGORY_LEANBACK_LAUNCHER, Intent.CATEGORY_LAUNCHER)) {
            val q = Intent(Intent.ACTION_MAIN).addCategory(cat)
            val rows: List<ResolveInfo> = try { pm.queryIntentActivities(q, 0) } catch (_: Exception) { emptyList() }
            for (ri in rows) {
                val pkg = ri.activityInfo?.packageName ?: continue
                if (pkg == packageName || found.containsKey(pkg)) continue
                found[pkg] = try { ri.loadLabel(pm).toString() } catch (_: Exception) { pkg }
            }
        }
        val sortedApps = found.toList().sortedBy { it.second.lowercase() }
        if (sortedApps.isEmpty()) {
            body.addView(Ui.label(this, "لم يعثر النظام على تطبيقات قابلة للتشغيل.", 25f, ut.dim), Ui.lpm(Ui.MATCH, Ui.WRAP, 0f, 0, px(20f), 0, 0))
        }
        for ((pkg, label) in sortedApps) {
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(px(12f), px(10f), px(12f), px(10f))
                background = Ui.round(ut.card, px(20f).toFloat(), px(1.5f), ut.border)
            }
            row.addView(Ui.label(this, label, 24f, ut.text, true), Ui.lp(0, Ui.WRAP, 1f))
            val inIsland = prefs.allowedApps.contains(pkg)
            row.addView(Ui.button(this, ut, if (inIsland) "✅ في الجزيرة" else "⬜ إخفاء", 19f) {
                val list = prefs.allowedApps.toMutableList()
                if (list.contains(pkg)) list.remove(pkg) else list.add(pkg)
                prefs.allowedApps = list
                build()
            }, Ui.lpm(Ui.WRAP, Ui.WRAP, 0f, 0, 0, px(6f), 0))
            val soundOn = prefs.notificationSoundApps.contains(pkg)
            row.addView(Ui.button(this, ut, if (soundOn) "🔊 الصوت يعمل" else "🔇 الصوت متوقف", 19f) {
                val list = prefs.notificationSoundApps.toMutableList()
                if (list.contains(pkg)) list.remove(pkg) else list.add(pkg)
                prefs.notificationSoundApps = list
                build()
            }, Ui.lpm(Ui.WRAP, Ui.WRAP, 0f, 0, 0, px(6f), 0))
            // مستوى صوت خاص بالتطبيق: افتراضي ← 25 ← 50 ← 75 ← 100
            val levels = listOf(-1, 25, 50, 75, 100)
            val curVol = prefs.notificationPolicy().perApp[pkg]?.volume ?: -1
            row.addView(Ui.button(this, ut, "🎚 " + (if (curVol < 0) "افتراضي" else "$curVol%"), 19f) {
                val next = levels[(levels.indexOf(curVol).coerceAtLeast(0) + 1) % levels.size]
                prefs.updateAppRule(pkg) { it.copy(volume = next) }
                build()
            }, Ui.lpm(Ui.WRAP, Ui.WRAP, 0f, 0, 0, px(6f), 0))
            row.addView(Ui.button(this, ut, "▶", 19f) {
                val r = prefs.notificationPolicy().resolve(com.uobidah.launcher.core.AlertType.APP, pkg)
                NotificationSound.playResolved(this, r, true)
                if (!r.playSound) toast("صوت هذا التطبيق متوقف حالياً")
            })
            body.addView(row, Ui.lpm(Ui.MATCH, Ui.WRAP, 0f, 0, 0, 0, px(8f)))
        }
        show(root)
    }
}
