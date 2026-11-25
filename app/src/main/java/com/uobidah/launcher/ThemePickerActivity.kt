package com.uobidah.launcher

import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView

/**
 * معرض السمات العادية والأندية: معاينة حية بألوان السمة المختارة (بطاقات وزر تحديد وشريط سفلي)،
 * ولا يتغير شيء قبل الضغط على «تطبيق». الرجوع للسمة السابقة متاح، والاختيار محفوظ في Prefs.
 */
class ThemePickerActivity : SettingsBase() {
    private var pending = 0
    private lateinit var previewHolder: FrameLayout
    private lateinit var info: TextView
    private lateinit var matchPreviewRow: LinearLayout
    private lateinit var paletteGridHost: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        pending = prefs.themeIndex.coerceIn(0, Themes.all.size - 1)
        setupScreen("◐ معرض السمات")
        note("اختر سمة لتظهر معاينتها فورًا، ثم اضغط «تطبيق». لا تتغير الشاشة قبل التطبيق.")

        section("المعاينة")
        previewHolder = FrameLayout(this)
        val host = activeSection ?: body
        host.addView(previewHolder, Ui.lpm(Ui.MATCH, px(360f), 0f, 0, px(6f), 0, px(6f)))
        info = Ui.label(this, "", 22f, ut.dim)
        host.addView(info, Ui.lp(Ui.MATCH, Ui.WRAP))
        renderPreview()

        val normal = Themes.paletteIndices
        section("15 لوحة ألوان رئيسية")
        note("اختر لوحة ألوان كاملة؛ الخلفية والبطاقات والنصوص ولون التحديد تتغير معًا، مع مراعاة وضوح النص على الخلفية.")
        paletteGridHost = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; clipChildren = false; clipToPadding = false }
        (activeSection ?: body).addView(paletteGridHost, Ui.lpm(Ui.MATCH, Ui.WRAP, 0f, 0, px(4f), 0, px(10f)))
        renderPaletteGrid(normal)
        val clubs = Themes.clubIndices
        if (clubs.isNotEmpty()) {
            section("سمات الأندية")
            chips("theme_club", clubs.map { Themes.all[it].club?.ar ?: Themes.all[it].name }, clubs.indexOf(pending)) {
                pending = clubs[it]
                renderPreview()
            }
        }

        section("تصميم البطاقات (" + CardDesigns.COUNT + ")")
        note(CardDesigns.descriptions[prefs.cardDesign.coerceIn(0, CardDesigns.COUNT - 1)])
        chips("card_design", CardDesigns.names, prefs.cardDesign.coerceIn(0, CardDesigns.COUNT - 1)) {
            prefs.cardDesign = it
            toast("تم تطبيق تصميم: " + CardDesigns.names[it])
            rebuild("")
        }

        section("🎨 بطاقات المباريات")
        note("ثلاثة اختيارات مستقلة: التصميم × الألوان × التخطيط. كل لوحة ألوان مضبوطة معًا لتتناسق مع بعضها، وتنطبق على الشاشة الرئيسية وشاشة المباريات.")
        matchPreviewRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            clipChildren = false
            clipToPadding = false
        }
        (activeSection ?: body).addView(matchPreviewRow, Ui.lpm(Ui.MATCH, px(280f), 0f, 0, px(6f), 0, px(8f)))
        renderMatchPreview()
        val mcDesign = prefs.matchCardDesign.coerceIn(0, MatchCardDesigns.count - 1)
        note("التصميم: " + MatchCardDesigns.descriptions[mcDesign])
        chips("mc_design", MatchCardDesigns.names, mcDesign) {
            prefs.matchCardDesign = it
            toast("تم تطبيق تصميم: " + MatchCardDesigns.names[it])
            rebuild("")
        }
        val mcPalette = prefs.matchCardPalette.coerceIn(0, MatchCardPalettes.all.size - 1)
        note("الألوان: " + MatchCardPalettes.names[mcPalette])
        chips("mc_palette", MatchCardPalettes.names, mcPalette) {
            prefs.matchCardPalette = it
            toast("تم تطبيق ألوان: " + MatchCardPalettes.names[it])
            rebuild("")
        }
        val mcLayout = prefs.matchCardLayout.coerceIn(0, MatchCardLayouts.count - 1)
        note("التخطيط: " + MatchCardLayouts.descriptions[mcLayout])
        chips("mc_layout", MatchCardLayouts.names, mcLayout) {
            prefs.matchCardLayout = it
            toast("تم تطبيق تخطيط: " + MatchCardLayouts.names[it])
            rebuild("")
        }

        section("تخطيط شاشات الإعدادات (" + ScreenLayouts.all.size + ")")
        note(ScreenLayouts.get(prefs.screenLayout).descAr)
        chips("screen_layout", ScreenLayouts.all.map { it.nameAr }, prefs.screenLayout.coerceIn(0, ScreenLayouts.all.size - 1)) {
            prefs.screenLayout = it
            toast("تم تطبيق تخطيط: " + ScreenLayouts.all[it].nameAr)
            rebuild("")
        }

        section("الإجراءات")
        action("✅ تطبيق السمة المعروضة") {
            if (pending == prefs.themeIndex) {
                toast("هذه السمة مطبّقة بالفعل")
            } else {
                prefs.setThemeWithHistory(pending)
                toast("تم تطبيق: " + Themes.all[pending].name)
                rebuild("")
            }
        }
        action("↩ الرجوع للسمة السابقة") {
            val prev = prefs.previousThemeIndex
            if (prev < 0 || prev >= Themes.all.size) {
                toast("لا توجد سمة سابقة للرجوع إليها")
            } else {
                prefs.setThemeWithHistory(prev)
                toast("تم الرجوع إلى: " + Themes.all[prev].name)
                rebuild("")
            }
        }
        finishScreen()
    }

    /** شبكة مرئية لجميع لوحات الألوان الخمس عشرة بدل إخفائها داخل قائمة منسدلة. */
    private fun renderPaletteGrid(indices: List<Int>) {
        paletteGridHost.removeAllViews()
        indices.chunked(5).forEach { group ->
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                layoutDirection = android.view.View.LAYOUT_DIRECTION_LTR
                clipChildren = false
                clipToPadding = false
            }
            group.forEachIndexed { col, index ->
                val t = Themes.all[index]
                val selected = pending == index
                val card = LinearLayout(this).apply {
                    orientation = LinearLayout.VERTICAL
                    gravity = Gravity.CENTER
                    setPadding(px(10f), px(10f), px(10f), px(10f))
                    minimumHeight = px(94f)
                    isFocusable = true
                    isClickable = true
                    background = android.graphics.drawable.StateListDrawable().apply {
                        addState(intArrayOf(android.R.attr.state_focused), Ui.round(Ui.over(t.cardFocus, t.bgBottom), px(14f).toFloat(), px(4f), t.accent))
                        addState(intArrayOf(), Ui.round(Ui.over(t.card, t.bgBottom), px(14f).toFloat(), px(if (selected) 4f else 1f), if (selected) t.accent else t.border))
                    }
                }
                val swatches = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER }
                listOf(t.bgTop, t.bgBottom, t.cardFocus, t.accent).forEach { color ->
                    val swatch = View(this).apply { background = Ui.round(color, px(6f).toFloat(), px(1f), Ui.withAlpha(t.text, 55)) }
                    swatches.addView(swatch, Ui.lpm(px(25f), px(20f), 0f, px(3f), 0, px(3f), 0))
                }
                card.addView(swatches, Ui.lp(Ui.MATCH, Ui.WRAP))
                val label = Ui.label(this, t.name + if (selected) "  ✓" else "", 18f, t.text, true)
                label.gravity = Gravity.CENTER
                label.maxLines = 1
                label.ellipsize = android.text.TextUtils.TruncateAt.END
                card.addView(label, Ui.lpm(Ui.MATCH, Ui.WRAP, 0f, 0, px(7f), 0, 0))
                card.setOnClickListener {
                    pending = index
                    renderPreview()
                    renderPaletteGrid(indices)
                }
                row.addView(card, Ui.lpm(0, Ui.WRAP, 1f, 0, 0, if (col == group.lastIndex) 0 else px(8f), 0))
            }
            paletteGridHost.addView(row, Ui.lpm(Ui.MATCH, Ui.WRAP, 0f, 0, 0, 0, px(8f)))
        }
    }

    /** معاينة حية ببيانات تجريبية: مباراة جارية وأخرى قادمة. */
    private fun renderMatchPreview() {
        val now = System.currentTimeMillis()
        val live = Match(
            id = "demo_live", home = "الأهلي", away = "الزمالك", startMs = now - 67 * 60_000L,
            league = "الدوري المصري الممتاز", homeScore = 2, awayScore = 1, status = "2H", progress = "67'",
            venue = "ستاد القاهرة"
        )
        val next = Match(
            id = "demo_next", home = "بيراميدز", away = "الأهلي", startMs = now + 5 * 3_600_000L,
            league = "كأس مصر", homeScore = null, awayScore = null, venue = "ستاد السلام"
        )
        val tz = prefs.cityTimezone
        matchPreviewRow.removeAllViews()
        val a = MatchCards.build(this, prefs, MatchCardModel("live", "المباراة الحالية", live, "الأهلي", "", true, tz), false)
        val b = MatchCards.build(this, prefs, MatchCardModel("next", "المباراة القادمة", next, "الأهلي", "", true, tz), false)
        matchPreviewRow.addView(a, Ui.lpm(0, Ui.MATCH, 1f, 0, 0, px(12f), 0))
        matchPreviewRow.addView(b, Ui.lpm(0, Ui.MATCH, 1f, 0, 0, 0, 0))
    }

    private fun renderPreview() {
        val t = Themes.all[pending]
        previewHolder.removeAllViews()
        val frame = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(px(18f), px(14f), px(18f), px(14f))
            background = ThemeBg(t)
        }
        // شريط علوي: شعار النادي (إن وُجد) + عنوان
        val top = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        val res = t.club?.let { ClubLogos.res(it.art) } ?: 0
        if (res != 0) {
            val iv = ImageView(this).apply { setImageResource(res); scaleType = ImageView.ScaleType.FIT_CENTER }
            top.addView(iv, Ui.lpm(px(56f), px(56f), 0f, 0, 0, px(12f), 0))
        }
        top.addView(Ui.label(this, "Abo Uobidah TV", 22f, t.text, true), Ui.lp(0, Ui.WRAP, 1f))
        frame.addView(top, Ui.lpm(Ui.MATCH, Ui.WRAP, 0f, 0, 0, 0, px(10f)))
        // بطاقتان
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        for (k in 0 until 3) {
            val card = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(px(12f), px(10f), px(12f), px(10f))
                background = Ui.round(if (k == 0) t.cardFocus else t.card, px(14f).toFloat(), px(if (k == 0) 3f else 1f), if (k == 0) t.accent else t.border)
            }
            card.addView(Ui.label(this, listOf("الساعة", "الطقس", "الصلاة")[k], 20f, t.text, true))
            card.addView(Ui.label(this, listOf("10:30", "24°", "العصر 3:15")[k], 18f, t.dim))
            row.addView(card, Ui.lpm(0, Ui.WRAP, 1f, 0, 0, if (k == 2) 0 else px(10f), 0))
        }
        frame.addView(row, Ui.lpm(Ui.MATCH, Ui.WRAP, 0f, 0, 0, 0, px(10f)))
        // زر تحديد
        val btn = Ui.label(this, "عنصر محدد", 20f, t.onAccent, true).apply {
            gravity = Gravity.CENTER
            setPadding(px(18f), px(8f), px(18f), px(8f))
            background = Ui.round(t.accent, px(14f).toFloat())
        }
        frame.addView(btn, Ui.lpm(Ui.WRAP, Ui.WRAP, 0f, 0, 0, 0, 0))
        frame.addView(android.view.View(this), Ui.lp(Ui.MATCH, 0, 1f))
        // شريط سفلي
        val dock = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(px(14f), px(8f), px(14f), px(8f))
            background = Ui.round(t.dock, px(18f).toFloat(), px(1f), t.dockBorder)
        }
        for (k in 0 until 5) {
            val dot = android.view.View(this).apply { background = Ui.round(if (k == 1) t.accent else t.dim, px(10f).toFloat()) }
            dock.addView(dot, Ui.lpm(px(if (k == 1) 34f else 22f), px(22f), 0f, px(8f), 0, px(8f), 0))
        }
        frame.addView(dock, Ui.lpm(Ui.WRAP, Ui.WRAP, 0f, 0, 0, 0, 0).also { it.gravity = Gravity.CENTER_HORIZONTAL })
        previewHolder.addView(frame, FrameLayout.LayoutParams(Ui.MATCH, Ui.MATCH))
        val tag = if (pending == prefs.themeIndex) "  ✓ مطبّقة حاليًا" else ""
        info.text = t.name + tag + (if (Themes.isLight(t)) " — سمة فاتحة" else " — سمة داكنة")
    }
}
