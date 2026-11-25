package com.uobidah.launcher

import android.content.Intent
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.ScrollView
import com.uobidah.launcher.core.FavoriteEntry
import com.uobidah.launcher.core.FavoriteList
import com.uobidah.launcher.core.TileSize

/**
 * مدير المفضلة: إضافة، حذف من القائمة (دون إلغاء تثبيت التطبيق)، إعادة ترتيب، اسم، أيقونة (إيموجي بديل)،
 * حجم، إظهار الاسم، وإخفاء من الشاشة الرئيسية. كل تعديل يُحفظ فوراً.
 */
class FavoriteAppsSettingsActivity : BaseActivity() {
    private lateinit var body: LinearLayout
    private val emojiChoices = listOf("⭐", "🎬", "📺", "🎮", "🎵", "💬", "🌐", "📁", "🛒", "⚙️", "⚽", "🕌")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        build()
    }

    override fun onResume() {
        super.onResume()
        if (::body.isInitialized) build()
    }

    private fun mutate(f: (FavoriteList) -> FavoriteList) {
        prefs.saveFavorites(f(prefs.favorites()))
        build()
    }

    private fun build() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = bgGradient()
            setPadding(px(48f), px(26f), px(48f), px(20f))
        }
        root.addView(header("⭐ إدارة التطبيقات المفضلة"), Ui.lp(Ui.MATCH, Ui.WRAP))
        root.addView(Ui.label(this, "رتّب واضبط كل اختصار. «إزالة» و«إخفاء» لا يحذفان التطبيق من الجهاز.", 24f, ut.dim), Ui.lpm(Ui.MATCH, Ui.WRAP, 0f, 0, px(8f), 0, px(12f)))
        val buttons = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        buttons.addView(Ui.button(this, ut, "➕ إضافة تطبيقات", 26f) {
            startActivity(Intent(this@FavoriteAppsSettingsActivity, AppsActivity::class.java).putExtra("mode", "pin"))
        }, Ui.lpm(Ui.WRAP, Ui.WRAP, 0f, 0, 0, px(12f), 0))
        buttons.addView(Ui.button(this, ut, "↺ تحديث القائمة", 26f) { build() })
        root.addView(buttons)
        val scroll = ScrollView(this)
        body = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        scroll.addView(body, ViewGroup.LayoutParams(Ui.MATCH, Ui.WRAP))
        root.addView(scroll, Ui.lp(Ui.MATCH, 0, 1f))

        val pm = packageManager
        val saved = prefs.favorites()
        val pruned = saved.prune { AppUtil.launchIntent(pm, it) != null }
        if (pruned.size != saved.size) prefs.saveFavorites(pruned)
        val list = pruned
        if (list.size == 0) {
            body.addView(Ui.label(this, "لا توجد تطبيقات مفضلة بعد. اضغط «إضافة تطبيقات».", 26f, ut.dim), Ui.lpm(Ui.MATCH, Ui.WRAP, 0f, 0, px(20f), 0, 0))
        }
        for ((index, e) in list.entries.withIndex()) {
            val appName = AppUtil.label(pm, e.pkg)
            val card = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(px(12f), px(10f), px(12f), px(10f))
                background = Ui.round(ut.card, px(22f).toFloat(), px(1.5f), ut.border)
            }
            val iconTxt = if (e.iconKey.startsWith("emoji:")) e.iconKey.removePrefix("emoji:") + " " else ""
            val state = (if (e.hidden) "  🙈 مخفي" else "") + "  •  " + e.size.labelAr + (if (e.showName) "" else "  •  بلا اسم")
            card.addView(Ui.label(this, "${index + 1}. $iconTxt${e.displayName(appName)}$state", 26f, if (e.hidden) ut.dim else ut.text, true))
            val controls = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
            fun add(text: String, w: Int = Ui.WRAP, onClick: () -> Unit) =
                controls.addView(Ui.button(this, ut, text, 20f, onClick), Ui.lpm(w, Ui.WRAP, 0f, 0, px(6f), px(6f), 0))
            add("✏️ الاسم") { rename(e, appName) }
            add("🖼 الأيقونة") { pickIcon(e) }
            add("📐 " + e.size.labelAr) { mutate { l -> l.setSize(e.pkg, TileSize.values()[(e.size.ordinal + 1) % TileSize.values().size]) } }
            add(if (e.showName) "🔤 الاسم ظاهر" else "🔤 الاسم مخفي") { mutate { l -> l.setShowName(e.pkg, !e.showName) } }
            add(if (e.hidden) "👁 إظهار" else "🙈 إخفاء") { mutate { l -> l.setHidden(e.pkg, !e.hidden) } }
            add("↑", px(62f)) { mutate { l -> l.move(e.pkg, -1) } }
            add("↓", px(62f)) { mutate { l -> l.move(e.pkg, 1) } }
            add("إزالة") { mutate { l -> l.remove(e.pkg) } }
            val hs = HorizontalScrollView(this).apply { isHorizontalScrollBarEnabled = false; clipChildren = false }
            hs.addView(controls)
            card.addView(hs, Ui.lpm(Ui.MATCH, Ui.WRAP, 0f, 0, px(6f), 0, 0))
            body.addView(card, Ui.lpm(Ui.MATCH, Ui.WRAP, 0f, 0, 0, 0, px(8f)))
        }
        show(root)
    }

    private fun rename(e: FavoriteEntry, appName: String) {
        val input = Ui.edit(this, ut, appName)
        input.setText(e.label)
        Ui.modal(this, ut, "✏️ اسم اختصار التطبيق", input,
            Btn("✔ حفظ") { mutate { it.rename(e.pkg, input.text.toString()) } },
            Btn("استخدام اسم التطبيق") { mutate { it.rename(e.pkg, "") } },
            Btn("إلغاء") {})
    }

    private fun pickIcon(e: FavoriteEntry) {
        val grid = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        var d: android.app.Dialog? = null
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        for (em in emojiChoices) {
            row.addView(Ui.button(this, ut, em, 30f) {
                mutate { it.setIcon(e.pkg, "emoji:$em") }; d?.dismiss()
            }, Ui.lpm(Ui.WRAP, Ui.WRAP, 0f, 0, 0, px(8f), 0))
        }
        val hs = HorizontalScrollView(this).apply { isHorizontalScrollBarEnabled = false }
        hs.addView(row)
        grid.addView(hs)
        grid.addView(Ui.label(this, "الأيقونة البديلة تظهر بدل أيقونة التطبيق في الشاشة الرئيسية.", 21f, ut.dim))
        d = Ui.modal(this, ut, "🖼 أيقونة الاختصار", grid,
            Btn("↺ أيقونة التطبيق الأصلية") { mutate { it.setIcon(e.pkg, "") } },
            Btn("إغلاق") {})
    }
}
