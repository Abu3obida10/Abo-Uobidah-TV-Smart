package com.uobidah.launcher

import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.text.SpannableString
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

/**
 * أساس موحد للشاشات الفرعية: خلفية زجاجية، عناوين واضحة، مجموعات إعدادات
 * منفصلة، وصف مختصر، وعناصر كبيرة قابلة للتحكم بالريموت.
 */
open class SettingsBase : BaseActivity() {
    override val restoreGenericViewState: Boolean get() = false

    private val stateKey: String get() = "settings_state_${javaClass.name}"
    private val openSectionKey: String get() = "settings_open_${javaClass.name}"
    private var keepRequestedFocus = false

    protected lateinit var scroll: ScrollView
    protected lateinit var body: LinearLayout
    protected var activeSection: LinearLayout? = null
    private var sectionTitle = ""

    protected fun setupScreen(title: String) {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = bgGradient()
            setPadding(px(ScreenLayouts.get(prefs.screenLayout).sideDp), px(26f), px(ScreenLayouts.get(prefs.screenLayout).sideDp), px(24f))
            clipChildren = true
            clipToPadding = false
        }

        val top = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(px(22f), px(16f), px(22f), px(16f))
            background = Ui.round(Surfaces.top(ut), px(26f).toFloat(), px(2f), Surfaces.topBorder(ut))
        }
        val titles = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        titles.addView(Ui.label(this, title, 43f, ut.text, true))
        titles.addView(Ui.label(this, "مركز التحكم  •  إعدادات واضحة ومنظمة", 21f, Surfaces.secondaryText(ut, Surfaces.top(ut))))
        top.addView(titles, Ui.lp(0, Ui.WRAP, 1f))
        val back = Ui.button(this, ut, "رجوع  ‹", 25f) { onBackPressed() }
        top.addView(back)
        root.addView(top, Ui.lpm(Ui.MATCH, Ui.WRAP, 0f, 0, 0, 0, px(20f)))

        scroll = ScrollView(this).apply {
            isVerticalScrollBarEnabled = false
            clipToPadding = true
            clipChildren = true
            overScrollMode = View.OVER_SCROLL_IF_CONTENT_SCROLLS
        }
        body = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            clipChildren = false
            clipToPadding = false
            setPadding(px(8f), px(8f), px(8f), px(22f))
        }
        scroll.addView(body, ViewGroup.LayoutParams(Ui.MATCH, Ui.WRAP))
        root.addView(scroll, Ui.lp(Ui.MATCH, 0, 1f))
        show(root)
    }


    /** كل العناصر القابلة للتركيز والظاهرة داخل الصفحة بترتيب العرض من فوق لتحت. */
    private fun visibleFocusables(): List<View> {
        val out = ArrayList<View>()
        fun walk(v: View) {
            if (v.visibility != View.VISIBLE) return
            if (v.isFocusable && v.isEnabled) out.add(v)
            if (v is ViewGroup) for (i in 0 until v.childCount) walk(v.getChildAt(i))
        }
        if (::body.isInitialized) walk(body)
        return out
    }

    /**
     * تنقل رأسي حتمي: السهم لأسفل يذهب إلى العنصر التالي مباشرة، ولأعلى إلى السابق،
     * بلا قفز إلى أول الصفحة أو آخرها. عند الحدّ لا يلتف التركيز.
     */
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        val code = event.keyCode
        if ((code == KeyEvent.KEYCODE_DPAD_DOWN || code == KeyEvent.KEYCODE_DPAD_UP) && ::scroll.isInitialized) {
            val cur = currentFocus
            if (cur != null && event.action == KeyEvent.ACTION_DOWN) {
                val list = visibleFocusables()
                val i = list.indexOf(cur)
                if (i >= 0) {
                    val j = if (code == KeyEvent.KEYCODE_DPAD_DOWN) i + 1 else i - 1
                    if (j in list.indices) {
                        val target = list[j]
                        target.requestFocus()
                        val r = android.graphics.Rect()
                        target.getDrawingRect(r)
                        scroll.offsetDescendantRectToMyCoords(target, r)
                        val pad = px(60f)
                        val top = scroll.scrollY
                        val bottom = top + scroll.height
                        if (r.top - pad < top) scroll.smoothScrollTo(0, (r.top - pad).coerceAtLeast(0))
                        else if (r.bottom + pad > bottom) scroll.smoothScrollTo(0, r.bottom + pad - scroll.height)
                    }
                    return true
                }
            } else if (cur != null && event.action == KeyEvent.ACTION_UP && visibleFocusables().contains(cur)) {
                return true
            }
        }
        return super.dispatchKeyEvent(event)
    }

    protected fun finishScreen() {
        sectionRefs.forEach { it.count.text = it.inner.childCount.toString() }
        restoreSettingsViewport()
    }

    /** Android may restore an old ScrollView hierarchy position after onCreate during Activity recreation.
     *  Reapply our semantic position after the framework restoration so a changed control cannot jump
     *  to the top or bottom of a long settings page. */
    override fun onRestoreInstanceState(savedInstanceState: android.os.Bundle) {
        super.onRestoreInstanceState(savedInstanceState)
        if (::scroll.isInitialized) restoreSettingsViewport()
    }

    private fun restoreSettingsViewport() {
        if (!::scroll.isInitialized || !::body.isInitialized) return
        val y = prefs.sp.getInt("${stateKey}_scroll_y", 0).coerceAtLeast(0)
        val tag = prefs.sp.getString("${stateKey}_focus_tag", "") ?: ""
        scroll.post {
            val maxY = (body.height - scroll.height).coerceAtLeast(0)
            scroll.scrollTo(0, y.coerceAtMost(maxY))
            if (tag.isNotEmpty()) body.findViewWithTag<View>(tag)?.requestFocus()
        }
    }

    /** يحفظ موضع الصفحة والتركيز قبل إعادة بناء الشاشة بعد تغيير إعداد. */
    protected fun rebuild(focusTag: String) {
        prefs.sp.edit()
            .putInt("${stateKey}_scroll_y", scroll.scrollY)
            .putString("${stateKey}_focus_tag", focusTag)
            .apply()
        keepRequestedFocus = true
        recreate()
    }

    override fun onPause() {
        if (::scroll.isInitialized) {
            val edit = prefs.sp.edit().putInt("${stateKey}_scroll_y", scroll.scrollY)
            if (!keepRequestedFocus) {
                val tag = currentFocus?.tag?.toString().orEmpty()
                if (tag.isNotEmpty()) edit.putString("${stateKey}_focus_tag", tag)
            }
            edit.apply()
        }
        super.onPause()
    }

    private class SectionRef(val inner: LinearLayout, val arrow: TextView, val count: TextView)

    private val sectionRefs = ArrayList<SectionRef>()

    private fun applyOpen(ref: SectionRef, open: Boolean) {
        ref.inner.visibility = if (open) View.VISIBLE else View.GONE
        ref.arrow.text = if (open) "▲" else "▼"
    }

    /** كل قسم بطاقة قابلة للطي (قائمة منسدلة): يظهر عنوانه فقط، والضغط عليه يفتح محتواه ويغلق غيره. */
    protected fun section(title: String) {
        if (body.childCount == 0) sectionRefs.clear()
        sectionTitle = title
        val sl = ScreenLayouts.get(prefs.screenLayout)
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(px(22f), px(10f), px(22f), px(12f))
            background = GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                intArrayOf(Surfaces.sectionA(ut), Surfaces.sectionB(ut))
            ).apply {
                cornerRadius = px(sl.radiusDp).toFloat()
                setStroke(px(2f), Color.argb(65, Color.red(ut.accent), Color.green(ut.accent), Color.blue(ut.accent)))
            }
            clipChildren = false
            clipToPadding = false
        }
        val head = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(px(12f), px(13f), px(12f), px(13f))
        }
        if (sl.headStyle == 0) {
            head.addView(View(this).apply { background = Ui.round(ut.accent, px(4f).toFloat()) }, Ui.lpm(px(7f), px(34f), 0f, 0, 0, px(14f), 0))
        }
        val titleView = Ui.label(this, title, 29f, Surfaces.accentText(ut), true)
        if (sl.headStyle == 1) {
            titleView.setPadding(px(20f), px(6f), px(20f), px(6f))
            titleView.background = Ui.round(Ui.withAlpha(ut.accent, 45), px(30f).toFloat())
            head.addView(titleView, Ui.lp(Ui.WRAP, Ui.WRAP))
            head.addView(View(this), Ui.lp(0, 1, 1f))
        } else {
            head.addView(titleView, Ui.lp(0, Ui.WRAP, 1f))
        }
        val count = Ui.label(this, "", 19f, Surfaces.secondaryText(ut, Surfaces.row(ut)), true).apply {
            setPadding(px(14f), px(4f), px(14f), px(4f))
            background = Ui.round(Surfaces.row(ut), px(20f).toFloat(), px(1f), Surfaces.rowBorder(ut))
        }
        head.addView(count, Ui.lpm(Ui.WRAP, Ui.WRAP, 0f, 0, 0, px(12f), 0))
        val arrow = Ui.label(this, "▼", 24f, Surfaces.accentText(ut), true)
        head.addView(arrow)
        Ui.focusable(head, ut, 17f, Surfaces.row(ut))
        head.isClickable = true
        val inner = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            clipChildren = false
            clipToPadding = false
        }
        card.addView(head, Ui.lp(Ui.MATCH, Ui.WRAP))
        if (sl.headStyle == 2) {
            card.addView(View(this).apply { background = Ui.round(Ui.withAlpha(ut.accent, 150), px(2f).toFloat()) }, Ui.lpm(Ui.MATCH, px(3f), 0f, px(12f), 0, px(12f), px(4f)))
        }
        card.addView(inner, Ui.lp(Ui.MATCH, Ui.WRAP))
        body.addView(card, Ui.lpm(Ui.MATCH, Ui.WRAP, 0f, 0, px(8f), 0, px(sl.gapDp)))

        val idx = sectionRefs.size
        val remembered = prefs.sp.getInt(openSectionKey, 0)
        val ref = SectionRef(inner, arrow, count)
        sectionRefs.add(ref)
        applyOpen(ref, if (sl.openAll) true else remembered == idx)
        head.tag = "section:${javaClass.name}:$idx"
        head.setOnClickListener {
            val nowOpen = inner.visibility != View.VISIBLE
            if (!sl.openAll) sectionRefs.forEach { applyOpen(it, false) }
            applyOpen(ref, nowOpen)
            prefs.sp.edit().putInt(openSectionKey, if (nowOpen) idx else -1).apply()
        }
        activeSection = inner
    }

    protected fun note(text: String) {
        val target = activeSection ?: body
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(px(17f), px(14f), px(17f), px(14f))
            background = Ui.round(Surfaces.note(ut), px(15f).toFloat())
        }
        box.addView(Ui.label(this, "ⓘ", 23f, Surfaces.accentOn(ut, Surfaces.note(ut)), true), Ui.lpm(Ui.WRAP, Ui.WRAP, 0f, 0, 0, px(12f), 0))
        box.addView(Ui.label(this, text, 20f, Surfaces.secondaryText(ut, Surfaces.note(ut))), Ui.lp(0, Ui.WRAP, 1f))
        target.addView(box, Ui.lpm(Ui.MATCH, Ui.WRAP, 0f, 0, px(7f), 0, px(7f)))
    }

    /** قائمة منسدلة: صف واحد يعرض الاختيار الحالي، والضغط عليه يفتح قائمة للاختيار. */
    protected fun chips(tag: String, options: List<CharSequence>, selected: Int, onPick: (Int) -> Unit) {
        val sel = selected.coerceIn(0, (options.size - 1).coerceAtLeast(0))
        val current = options.getOrNull(sel)?.toString() ?: ""
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(px(20f), px(16f), px(20f), px(16f))
            minimumHeight = px(72f)
            isFocusable = true
            isClickable = true
        }
        val tv = Ui.label(this, current, 24f, Surfaces.primaryText(ut, Surfaces.row(ut)), true)
        tv.maxLines = 1
        tv.ellipsize = android.text.TextUtils.TruncateAt.END
        row.addView(tv, Ui.lp(0, Ui.WRAP, 1f))
        row.addView(Ui.label(this, "▼", 24f, Surfaces.accentOn(ut, Surfaces.row(ut)), true))
        Ui.focusable(row, ut, 17f, Surfaces.row(ut))
        row.tag = "$tag:$sel"
        row.setOnClickListener { showDropdown(options, sel, onPick) }
        (activeSection ?: body).addView(row, Ui.lpm(Ui.MATCH, Ui.WRAP, 0f, 0, px(6f), 0, px(6f)))
    }

    private fun showDropdown(options: List<CharSequence>, selected: Int, onPick: (Int) -> Unit) {
        val list = android.widget.ListView(this)
        list.divider = null
        list.selector = Ui.selector(this, ut)
        val ad = Ui.listAdapter(this, ut)
        for ((i, o) in options.withIndex()) ad.add((if (i == selected) "●  " else "○  ") + o.toString())
        list.adapter = ad
        list.setSelection(selected)
        var dlg: android.app.Dialog? = null
        list.setOnItemClickListener { _, _, pos, _ ->
            dlg?.dismiss()
            if (pos in options.indices) onPick(pos)
        }
        val box = LinearLayout(this)
        box.orientation = LinearLayout.VERTICAL
        box.addView(list, Ui.lp(Ui.MATCH, px(460f)))
        dlg = Ui.modal(this, ut, if (sectionTitle.isBlank()) "اختر من القائمة" else sectionTitle, box, Btn("إغلاق") {})
    }

    protected fun dotted(color: Int, name: String): CharSequence {
        val s = SpannableString("● $name")
        s.setSpan(ForegroundColorSpan(color), 0, 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        return s
    }

    protected fun toggle(label: String, get: () -> Boolean, set: (Boolean) -> Unit) {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            minimumHeight = px(72f)
            setPadding(px(18f), px(15f), px(18f), px(15f))
            background = Ui.round(Surfaces.row(ut), px(17f).toFloat(), px(1f), Surfaces.rowBorder(ut))
            isFocusable = true
            isClickable = true
        }
        val title = Ui.label(this, label, 24f, Surfaces.primaryText(ut, Surfaces.row(ut)), true)
        row.addView(title, Ui.lp(0, Ui.WRAP, 1f))
        val state = Ui.label(this, "", 21f, Color.WHITE, true).apply {
            gravity = Gravity.CENTER
            minWidth = px(115f)
            setPadding(px(16f), px(10f), px(16f), px(10f))
        }
        fun paint() {
            val enabled = get()
            state.text = if (enabled) "● مفعّل" else "○ متوقف"
            state.background = Ui.round(if (enabled) Surfaces.pillOn else Surfaces.pillOff, px(30f).toFloat())
        }
        paint()
        row.addView(state)
        Ui.focusable(row, ut, 17f, Surfaces.row(ut))
        row.setOnClickListener { set(!get()); paint() }
        (activeSection ?: body).addView(row, Ui.lpm(Ui.MATCH, Ui.WRAP, 0f, 0, px(6f), 0, px(6f)))
    }

    protected fun action(text: String, onClick: () -> Unit) {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            minimumHeight = px(72f)
            setPadding(px(20f), px(17f), px(20f), px(17f))
            background = Ui.round(Surfaces.row(ut), px(17f).toFloat(), px(1f), Surfaces.rowBorder(ut))
            isFocusable = true
            isClickable = true
        }
        val title = Ui.label(this, text, 24f, Surfaces.primaryText(ut, Surfaces.row(ut)), true)
        title.maxLines = 2
        row.addView(title, Ui.lp(0, Ui.WRAP, 1f))
        row.addView(Ui.label(this, "‹", 34f, Surfaces.accentOn(ut, Surfaces.row(ut)), true))
        Ui.focusable(row, ut, 17f, Surfaces.row(ut))
        row.setOnClickListener { onClick() }
        (activeSection ?: body).addView(row, Ui.lpm(Ui.MATCH, Ui.WRAP, 0f, 0, px(6f), 0, px(6f)))
    }
}
