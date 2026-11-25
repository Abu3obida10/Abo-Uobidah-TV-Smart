package com.uobidah.launcher

import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.StateListDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import com.uobidah.launcher.core.HomeLayoutSpec
import com.uobidah.launcher.core.HomeLayoutTemplate
import com.uobidah.launcher.core.HomeLayouts

/** معرض 21 تخطيطًا، بمعاينات مبنية على القالب الفعلي لكل شاشة. */
class LayoutPickerActivity : SettingsBase() {
    private var pending = HomeLayouts.TV_INDEX
    private lateinit var gridHost: LinearLayout
    private lateinit var previewHolder: FrameLayout
    private lateinit var info: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        pending = if (HomeLayouts.isValid(prefs.layoutIndex)) prefs.layoutIndex else HomeLayouts.TV_INDEX
        if (!HomeLayouts.isFeatured(pending)) pending = HomeLayouts.TV_INDEX
        setupScreen("▤ تخطيطات الشاشة الرئيسية")
        note("21 تصميمًا مختلفًا فعليًا في توزيع الساعة والبطاقات والتطبيقات والقنوات والمباريات. كل تصميم يستخدم القائمة الجانبية نفسها فقط، بدون شريط تنقل سفلي مكرر.")

        section("معرض التخطيطات — 21 تصميمًا")
        gridHost = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            clipChildren = false
            clipToPadding = false
        }
        (activeSection ?: body).addView(gridHost, Ui.lpm(Ui.MATCH, Ui.WRAP, 0f, 0, px(4f), 0, px(10f)))
        renderGrid()

        section("معاينة توزيع التخطيط")
        previewHolder = FrameLayout(this)
        (activeSection ?: body).addView(previewHolder, Ui.lpm(Ui.MATCH, px(320f), 0f, 0, px(6f), 0, px(6f)))
        info = Ui.label(this, "", 22f, ut.dim)
        (activeSection ?: body).addView(info, Ui.lp(Ui.MATCH, Ui.WRAP))
        renderPreview()

        section("الإجراءات")
        action("✅ تطبيق التخطيط المختار") { applyPending() }
        action("↩ الرجوع إلى التخطيط السابق") { revert() }
        action("🏠 التخطيط الافتراضي — مرجع التلفزيون") { pending = HomeLayouts.TV_INDEX; renderGrid(); renderPreview() }
        finishScreen()
    }

    private fun layoutCard(spec: HomeLayoutSpec, selected: Boolean): View {
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(px(10f), px(9f), px(10f), px(9f))
            minimumHeight = px(156f)
            clipChildren = false
            clipToPadding = false
            isFocusable = true
            isClickable = true
            background = StateListDrawable().apply {
                addState(intArrayOf(android.R.attr.state_focused), Ui.round(Ui.over(ut.cardFocus, ut.bgBottom), px(16f).toFloat(), px(4f), ut.accent))
                addState(intArrayOf(), Ui.round(Ui.over(if (selected) ut.cardFocus else ut.card, ut.bgBottom), px(16f).toFloat(), px(if (selected) 4f else 1f), if (selected) ut.accent else ut.border))
            }
        }
        card.addView(buildMockup(spec, large = false), Ui.lpm(Ui.MATCH, px(86f), 0f, 0, 0, 0, px(7f)))
        val title = Ui.label(this, spec.nameAr + if (selected) "  ✓" else "", 19f, ut.text, true)
        title.gravity = Gravity.CENTER
        title.maxLines = 1
        title.isSingleLine = true
        title.ellipsize = android.text.TextUtils.TruncateAt.END
        card.addView(title, Ui.lp(Ui.MATCH, Ui.WRAP))
        card.setOnClickListener {
            pending = spec.index
            renderGrid()
            renderPreview()
        }
        return card
    }

    private fun miniBlock(height: Int, accent: Boolean = false, large: Boolean = false): View {
        val h = if (large) height else (height * 0.34f).toInt().coerceAtLeast(3)
        val color = if (accent) ut.cardFocus else ut.card
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(if (large) px(8f) else px(2f), if (large) px(7f) else px(2f), if (large) px(8f) else px(2f), if (large) px(7f) else px(2f))
            background = Ui.round(Ui.over(color, ut.bgBottom), if (large) px(10f).toFloat() else px(4f).toFloat(), px(1f), Ui.withAlpha(ut.border, 120))
            addView(View(this@LayoutPickerActivity).apply { background = Ui.round(if (accent) ut.accent else ut.text, px(3f).toFloat()) }, Ui.lp(Ui.MATCH, h.coerceAtLeast(3)))
            if (large) addView(View(this@LayoutPickerActivity).apply { background = Ui.round(Ui.withAlpha(ut.text, 80), px(3f).toFloat()) }, Ui.lpm(Ui.MATCH, px(6f), 0f, 0, 0, 0, px(5f)))
        }
    }

    private fun blockRow(count: Int, height: Int, large: Boolean, accentIndex: Int = 0): LinearLayout {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutDirection = View.LAYOUT_DIRECTION_LTR
            clipChildren = false
            clipToPadding = false
        }
        val itemHeight = if (large) px(height.toFloat()) else px((height * 0.34f).toInt().coerceAtLeast(3).toFloat())
        repeat(count.coerceAtLeast(1)) { index ->
            row.addView(miniBlock(height, index == accentIndex, large), Ui.lpm(0, itemHeight, 1f, 0, 0, if (index == count - 1) 0 else px(if (large) 7f else 3f), 0))
        }
        return row
    }

    private fun gridMockup(columns: Int, rows: Int, large: Boolean): View {
        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; clipChildren = false; clipToPadding = false }
        repeat(rows) {
            val rowHeight = if (large) px(46f) else px(5f)
            box.addView(blockRow(columns, if (large) 46 else 12, large, it % columns), Ui.lpm(Ui.MATCH, rowHeight, 0f, 0, 0, 0, px(if (large) 7f else 3f)))
        }
        return box
    }

    private fun buildMockup(spec: HomeLayoutSpec, large: Boolean): View {
        val template = spec.template
        val shell = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutDirection = View.LAYOUT_DIRECTION_LTR
            clipChildren = false
            clipToPadding = false
            setPadding(if (large) px(9f) else px(3f), if (large) px(8f) else px(3f), if (large) px(7f) else px(3f), if (large) px(8f) else px(3f))
            background = GradientDrawable(GradientDrawable.Orientation.TL_BR, intArrayOf(ut.bgTop, ut.bgBottom)).apply {
                cornerRadius = px(if (large) 16f else 8f).toFloat()
                setStroke(px(1f), Ui.withAlpha(ut.accent, 120))
            }
        }
        val content = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; clipChildren = false; clipToPadding = false }
        val nav = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(if (large) px(4f) else px(2f), if (large) px(5f) else px(2f), if (large) px(4f) else px(2f), if (large) px(5f) else px(2f))
            background = Ui.round(ut.card, px(if (large) 9f else 4f).toFloat())
        }
        repeat(7) { idx ->
            nav.addView(View(this).apply { background = Ui.round(if (idx == 0) ut.accent else Ui.withAlpha(ut.text, 95), px(if (large) 5f else 2f).toFloat()) }, Ui.lp(if (large) px(12f) else px(5f), if (large) px(12f) else px(5f)))
            if (idx != 6) nav.addView(View(this), Ui.lp(1, if (large) px(7f) else px(2f)))
        }
        val headerHeight = when {
            template == HomeLayoutTemplate.CLOCK_ONLY -> if (large) 54 else 16
            template == HomeLayoutTemplate.CLOCK_HERO -> if (large) 50 else 15
            template == HomeLayoutTemplate.COMPACT || template == HomeLayoutTemplate.APP_GRID || template == HomeLayoutTemplate.CHANNELS_FIRST || template == HomeLayoutTemplate.MATCH_CENTER || template == HomeLayoutTemplate.PRAYER_LONG -> if (large) 19 else 7
            else -> if (large) 29 else 9
        }
        content.addView(miniBlock(headerHeight, accent = true, large = large), Ui.lpm(Ui.MATCH, px(headerHeight.toFloat()), 0f, 0, 0, 0, px(if (large) 8f else 3f)))

        fun addGrid(columns: Int = 5, rows: Int = 2) {
            content.addView(gridMockup(columns, rows, large), Ui.lpm(Ui.MATCH, Ui.WRAP, 0f, 0, 0, 0, px(if (large) 7f else 3f)))
        }
        fun addInfo(vertical: Boolean = false, one: Boolean = false, height: Int = if (large) 58 else 16) {
            val count = if (one) 1 else 2
            if (vertical) {
                val stack = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
                repeat(count) { idx -> stack.addView(miniBlock(height, idx == 0, large), Ui.lpm(Ui.MATCH, px(height.toFloat()), 0f, 0, 0, 0, if (idx == count - 1) 0 else px(if (large) 7f else 3f))) }
                content.addView(stack, Ui.lpm(Ui.MATCH, Ui.WRAP, 0f, 0, 0, 0, px(if (large) 7f else 3f)))
            } else {
                val rowHeight = if (large) px(height.toFloat()) else px((height * 0.34f).toInt().coerceAtLeast(3).toFloat())
                content.addView(blockRow(count, height, large), Ui.lpm(Ui.MATCH, rowHeight, 0f, 0, 0, 0, px(if (large) 7f else 3f)))
            }
        }
        fun addAppsStrip() { content.addView(blockRow(5, if (large) 35 else 10, large, 1), Ui.lpm(Ui.MATCH, if (large) px(35f) else px(10f), 0f, 0, 0, 0, px(if (large) 7f else 3f))) }
        fun addMatches() { content.addView(blockRow(3, if (large) 30 else 9, large, 2), Ui.lpm(Ui.MATCH, if (large) px(30f) else px(9f), 0f, 0, 0, 0, px(if (large) 7f else 3f))) }
        fun addChannels() { content.addView(blockRow(4, if (large) 42 else 13, large, 3), Ui.lpm(Ui.MATCH, if (large) px(42f) else px(13f), 0f, 0, 0, 0, px(if (large) 8f else 3f))) }
        fun split(leftGrid: Boolean) {
            val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; layoutDirection = View.LAYOUT_DIRECTION_LTR }
            val left = if (leftGrid) gridMockup(4, 3, large) else LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }.also { box ->
                repeat(2) { box.addView(miniBlock(if (large) 65 else 18, it == 0, large), Ui.lpm(Ui.MATCH, px(if (large) 65f else 18f), 0f, 0, 0, 0, px(if (large) 6f else 2f))) }
            }
            val right = if (leftGrid) LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }.also { box ->
                repeat(2) { box.addView(miniBlock(if (large) 65 else 18, it == 0, large), Ui.lpm(Ui.MATCH, px(if (large) 65f else 18f), 0f, 0, 0, 0, px(if (large) 6f else 2f))) }
            } else gridMockup(4, 3, large)
            row.addView(left, Ui.lpm(0, Ui.MATCH, 1.1f, 0, 0, px(if (large) 7f else 3f), 0))
            row.addView(right, Ui.lpm(0, Ui.MATCH, 0.9f, 0, 0, 0, 0))
            content.addView(row, Ui.lpm(Ui.MATCH, if (large) px(210f) else px(44f), 0f, 0, 0, 0, px(if (large) 7f else 3f)))
        }
        fun addSpotlight(dominantWeather: Boolean) {
            val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; layoutDirection = View.LAYOUT_DIRECTION_LTR }
            val weather = miniBlock(if (large) 132 else 40, true, large)
            val prayer = miniBlock(if (large) 132 else 40, false, large)
            row.addView(weather, Ui.lpm(0, Ui.MATCH, if (dominantWeather) 1.75f else 1f, 0, 0, px(if (large) 8f else 3f), 0))
            row.addView(prayer, Ui.lpm(0, Ui.MATCH, if (dominantWeather) 0.75f else 1f, 0, 0, 0, 0))
            content.addView(row, Ui.lpm(Ui.MATCH, if (large) px(155f) else px(30f), 0f, 0, 0, 0, px(if (large) 8f else 3f)))
        }
        fun splitPanes(left: View, right: View, leftWeight: Float = 1.1f, rightWeight: Float = 0.9f) {
            val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; layoutDirection = View.LAYOUT_DIRECTION_LTR; clipChildren = false }
            row.addView(left, Ui.lpm(0, Ui.MATCH, leftWeight, 0, 0, px(if (large) 7f else 3f), 0))
            row.addView(right, Ui.lpm(0, Ui.MATCH, rightWeight, 0, 0, 0, 0))
            content.addView(row, Ui.lpm(Ui.MATCH, if (large) px(210f) else px(44f), 0f, 0, 0, 0, px(if (large) 7f else 3f)))
        }
        fun infoStack(): View = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(miniBlock(if (large) 68 else 18, true, large), Ui.lpm(Ui.MATCH, px(if (large) 68f else 18f), 0f, 0, 0, 0, px(if (large) 6f else 2f)))
            addView(miniBlock(if (large) 68 else 18, false, large), Ui.lpm(Ui.MATCH, px(if (large) 68f else 18f), 0f, 0, 0, 0, 0))
        }
        fun appStripPanel(): View = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(miniBlock(if (large) 18 else 6, true, large), Ui.lpm(Ui.MATCH, px(if (large) 18f else 6f), 0f, 0, 0, 0, px(if (large) 8f else 2f)))
            addView(blockRow(4, if (large) 48 else 15, large, 1), Ui.lp(Ui.MATCH, if (large) px(48f) else px(15f)))
        }
        fun channelPanel(): View = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(miniBlock(if (large) 18 else 6, true, large), Ui.lpm(Ui.MATCH, px(if (large) 18f else 6f), 0f, 0, 0, 0, px(if (large) 7f else 2f)))
            addView(blockRow(4, if (large) 60 else 18, large, 3), Ui.lp(Ui.MATCH, if (large) px(60f) else px(18f)))
        }
        fun matchPanel(): View = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(miniBlock(if (large) 20 else 7, true, large), Ui.lpm(Ui.MATCH, px(if (large) 20f else 7f), 0f, 0, 0, 0, px(if (large) 7f else 2f)))
            addView(blockRow(3, if (large) 68 else 20, large, 2), Ui.lp(Ui.MATCH, if (large) px(68f) else px(20f)))
        }

        when (template) {
            HomeLayoutTemplate.BALANCED -> { addInfo(); addAppsStrip(); addMatches() }
            HomeLayoutTemplate.CLOCK_HERO -> { addGrid(4, 2); addInfo(vertical = true, height = if (large) 46 else 14); addMatches() }
            HomeLayoutTemplate.PRAYER_HERO -> { addInfo(one = true, height = if (large) 115 else 31); addAppsStrip() }
            HomeLayoutTemplate.APP_FIRST -> { addGrid(5, 2); addInfo(); addMatches() }
            HomeLayoutTemplate.TV_REFERENCE -> { split(leftGrid = true); addMatches() }
            HomeLayoutTemplate.APP_GRID_FIRST -> { addGrid(4, 3); addInfo(one = true, height = if (large) 35 else 11) }
            HomeLayoutTemplate.CLOCK_ONLY -> { addAppsStrip(); addChannels() }
            HomeLayoutTemplate.WEATHER_PRAYER_STACK -> { addInfo(vertical = true, height = if (large) 52 else 16); addGrid(3, 2) }
            HomeLayoutTemplate.CINEMA -> { addChannels(); addGrid(4, 3) }
            HomeLayoutTemplate.CARDS_BOTTOM -> { addAppsStrip(); addMatches(); addInfo() }
            HomeLayoutTemplate.CLOCK_APPS_SPLIT -> splitPanes(infoStack(), appStripPanel(), 0.9f, 1.3f)
            HomeLayoutTemplate.CARD_SHOWCASE -> { addSpotlight(dominantWeather = false); addGrid(4, 2) }
            HomeLayoutTemplate.COMPACT -> { addGrid(6, 3); addInfo(height = if (large) 22 else 7) }
            HomeLayoutTemplate.WEATHER_HERO -> { addSpotlight(dominantWeather = true); addAppsStrip() }
            HomeLayoutTemplate.MATCHES_FIRST -> { addMatches(); addGrid(5, 2); addInfo(one = true, height = if (large) 35 else 10) }
            HomeLayoutTemplate.PRAYER_MINIMAL -> { addInfo(one = true, vertical = true, height = if (large) 80 else 23); addGrid(5, 2) }
            HomeLayoutTemplate.CHANNELS_RACK -> { addChannels(); addAppsStrip(); addInfo() }
            HomeLayoutTemplate.SPLIT_COLUMNS -> { splitPanes(channelPanel(), infoStack(), 1.2f, 0.8f); addGrid(4, 2) }
            HomeLayoutTemplate.SPORTS_BOARD -> { splitPanes(matchPanel(), gridMockup(3, 3, large), 1.25f, 0.75f); addChannels() }
            HomeLayoutTemplate.FAVORITES_GRID -> { addGrid(4, 2); addMatches() }
            HomeLayoutTemplate.APP_GRID -> addGrid(6, 3)
            HomeLayoutTemplate.CHANNELS_FIRST -> { addChannels(); addGrid(5, 3) }
            HomeLayoutTemplate.MATCH_CENTER -> { addMatches(); addInfo(one = true, height = if (large) 35 else 10); addAppsStrip() }
            HomeLayoutTemplate.PRAYER_LONG -> { addInfo(one = true, height = if (large) 125 else 34); addGrid(5, 2) }
            HomeLayoutTemplate.DASHBOARD -> { addInfo(); addGrid(5, 2); addMatches() }
            HomeLayoutTemplate.DUSK_BOARD -> { splitPanes(infoStack(), matchPanel(), 1f, 1.25f); addAppsStrip() }
        }

        shell.addView(content, Ui.lp(0, Ui.MATCH, 1f))
        shell.addView(nav, Ui.lpm(if (large) px(30f) else px(11f), Ui.MATCH, 0f, px(if (large) 6f else 2f), 0, 0, 0))
        return shell
    }

    private fun renderGrid() {
        if (!::gridHost.isInitialized) return
        gridHost.removeAllViews()
        HomeLayouts.featured.chunked(3).forEach { group ->
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                layoutDirection = View.LAYOUT_DIRECTION_LTR
                clipChildren = false
                clipToPadding = false
            }
            group.forEachIndexed { index, spec ->
                row.addView(layoutCard(spec, spec.index == pending), Ui.lpm(0, Ui.WRAP, 1f, 0, 0, if (index == group.lastIndex) 0 else px(9f), 0))
            }
            while (row.childCount < 3) row.addView(View(this), Ui.lpm(0, 1, 1f, 0, 0, 0, 0))
            gridHost.addView(row, Ui.lpm(Ui.MATCH, Ui.WRAP, 0f, 0, 0, 0, px(8f)))
        }
    }

    private fun renderPreview() {
        if (!::previewHolder.isInitialized) return
        val spec = HomeLayouts.get(pending)
        previewHolder.removeAllViews()
        previewHolder.addView(buildMockup(spec, large = true), FrameLayout.LayoutParams(Ui.MATCH, Ui.MATCH))
        info.text = spec.nameAr + " — " + spec.descAr + if (pending == prefs.layoutIndex) "  •  مطبّق حاليًا" else "  •  غير مطبّق بعد"
    }

    private fun applyPending() {
        if (pending == prefs.layoutIndex) {
            toast("هذا التخطيط مطبّق بالفعل")
            return
        }
        prefs.previousLayoutIndex = prefs.layoutIndex
        prefs.layoutIndex = pending
        toast("تم تطبيق: " + HomeLayouts.get(pending).nameAr)
        finish()
    }

    private fun revert() {
        val prev = prefs.previousLayoutIndex
        if (prev < 0 || !HomeLayouts.isFeatured(prev)) {
            toast("لا يوجد تخطيط سابق ضمن التخطيطات الرئيسية")
            return
        }
        val cur = prefs.layoutIndex
        prefs.layoutIndex = prev
        prefs.previousLayoutIndex = cur
        pending = prev
        toast("تم الرجوع إلى: " + HomeLayouts.get(prev).nameAr)
        finish()
    }
}
