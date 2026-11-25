package com.uobidah.launcher

import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.uobidah.launcher.core.AdhkarData
import com.uobidah.launcher.core.Dhikr
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * أذكار الصباح والمساء: ذكر في كل شاشة، الضغط على البطاقة (OK بالريموت) يزيد العدّاد،
 * وعند اكتمال العدد ينتقل تلقائياً للتالي. التقدّم محفوظ لليوم الحالي فقط.
 */
class AdhkarActivity : BaseActivity() {
    private var morning = true
    private var index = 0
    private var counts = IntArray(0)
    private var items: List<Dhikr> = emptyList()
    private val handler = Handler(Looper.getMainLooper())

    private lateinit var tabMorning: TextView
    private lateinit var tabEvening: TextView
    private lateinit var tvProgress: TextView
    private lateinit var tvText: TextView
    private lateinit var tvSource: TextView
    private lateinit var tvNote: TextView
    private lateinit var tvCounter: TextView
    private lateinit var card: LinearLayout
    private lateinit var textScroll: ScrollView
    private lateinit var remindBtn: TextView
    private lateinit var mHourBtn: TextView
    private lateinit var eHourBtn: TextView

    private fun dayKey(): String = SimpleDateFormat("yyyyMMdd", Locale.US).format(Date())
    private fun storeKey(m: Boolean) = "adhkar_" + dayKey() + "_" + (if (m) "m" else "e")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val kind = intent.getStringExtra("kind")
        morning = when (kind) {
            "morning" -> true
            "evening" -> false
            else -> AdhkarData.isMorningHour(Calendar.getInstance().get(Calendar.HOUR_OF_DAY))
        }
        purgeOldDays()
        buildUi()
        load(morning)
    }

    /** يحذف عدّادات الأيام السابقة حتى لا تتراكم المفاتيح. */
    private fun purgeOldDays() {
        val today = dayKey()
        val ed = prefs.sp.edit()
        var changed = false
        for (k in prefs.sp.all.keys) {
            if (k.startsWith("adhkar_") && !k.startsWith("adhkar_$today")) { ed.remove(k); changed = true }
        }
        if (changed) ed.apply()
    }

    private fun buildUi() {
        val root = LinearLayout(this)
        root.orientation = LinearLayout.VERTICAL
        root.background = bgGradient()
        root.setPadding(px(60f), px(30f), px(60f), px(28f))

        root.addView(header("📿 الأذكار"))

        val tabs = LinearLayout(this)
        tabs.orientation = LinearLayout.HORIZONTAL
        tabs.gravity = Gravity.CENTER
        tabMorning = Ui.chip(this, ut, "☀ أذكار الصباح", morning, 30f) { load(true) }
        tabEvening = Ui.chip(this, ut, "🌙 أذكار المساء", !morning, 30f) { load(false) }
        tabs.addView(tabMorning, Ui.lpm(Ui.WRAP, Ui.WRAP, 0f, px(10f), px(14f), px(10f), px(10f)))
        tabs.addView(tabEvening, Ui.lpm(Ui.WRAP, Ui.WRAP, 0f, px(10f), px(14f), px(10f), px(10f)))
        root.addView(tabs)

        tvProgress = Ui.label(this, " ", 26f, ut.dim)
        tvProgress.gravity = Gravity.CENTER
        root.addView(tvProgress, Ui.lpm(Ui.MATCH, Ui.WRAP, 0f, 0, 0, 0, px(10f)))

        card = LinearLayout(this)
        card.orientation = LinearLayout.VERTICAL
        card.setPadding(px(40f), px(28f), px(40f), px(24f))
        Ui.focusable(card, ut, 36f, ut.card)
        card.isClickable = true
        card.setOnClickListener { tap() }

        textScroll = ScrollView(this)
        textScroll.isVerticalScrollBarEnabled = false
        tvText = Ui.label(this, " ", 40f, ut.text, true)
        tvText.gravity = Gravity.CENTER
        tvText.setLineSpacing(0f, 1.45f)
        textScroll.addView(tvText, LinearLayout.LayoutParams(Ui.MATCH, Ui.WRAP))
        card.addView(textScroll, Ui.lp(Ui.MATCH, 0, 1f))

        tvSource = Ui.label(this, " ", 22f, ut.accent)
        tvSource.gravity = Gravity.CENTER
        card.addView(tvSource, Ui.lpm(Ui.MATCH, Ui.WRAP, 0f, 0, px(10f), 0, 0))
        tvNote = Ui.label(this, " ", 21f, ut.dim)
        tvNote.gravity = Gravity.CENTER
        tvNote.maxLines = 2
        tvNote.ellipsize = TextUtils.TruncateAt.END
        card.addView(tvNote, Ui.lpm(Ui.MATCH, Ui.WRAP, 0f, 0, px(4f), 0, 0))
        tvCounter = Ui.label(this, " ", 46f, ut.accent, true)
        tvCounter.gravity = Gravity.CENTER
        card.addView(tvCounter, Ui.lpm(Ui.MATCH, Ui.WRAP, 0f, 0, px(10f), 0, 0))
        root.addView(card, Ui.lp(Ui.MATCH, 0, 1f))

        val nav = LinearLayout(this)
        nav.orientation = LinearLayout.HORIZONTAL
        nav.gravity = Gravity.CENTER
        nav.addView(Ui.button(this, ut, "السابق ›", 26f) { go(index - 1) }, Ui.lpm(Ui.WRAP, Ui.WRAP, 0f, px(10f), px(14f), px(10f), 0))
        nav.addView(Ui.button(this, ut, "↺ إعادة هذا الذكر", 26f) { resetCurrent() }, Ui.lpm(Ui.WRAP, Ui.WRAP, 0f, px(10f), px(14f), px(10f), 0))
        nav.addView(Ui.button(this, ut, "⟲ بدء من جديد", 26f) { resetAll() }, Ui.lpm(Ui.WRAP, Ui.WRAP, 0f, px(10f), px(14f), px(10f), 0))
        nav.addView(Ui.button(this, ut, "‹ التالي", 26f) { go(index + 1) }, Ui.lpm(Ui.WRAP, Ui.WRAP, 0f, px(10f), px(14f), px(10f), 0))
        root.addView(nav)

        val rem = LinearLayout(this)
        rem.orientation = LinearLayout.HORIZONTAL
        rem.gravity = Gravity.CENTER
        remindBtn = Ui.button(this, ut, "", 24f) { toggleRemind() }
        mHourBtn = Ui.button(this, ut, "", 24f) { cycleHour("adhkar_morning_hour", 6, 4, 9) }
        eHourBtn = Ui.button(this, ut, "", 24f) { cycleHour("adhkar_evening_hour", 17, 15, 20) }
        rem.addView(remindBtn, Ui.lpm(Ui.WRAP, Ui.WRAP, 0f, px(10f), px(12f), px(10f), 0))
        rem.addView(mHourBtn, Ui.lpm(Ui.WRAP, Ui.WRAP, 0f, px(10f), px(12f), px(10f), 0))
        rem.addView(eHourBtn, Ui.lpm(Ui.WRAP, Ui.WRAP, 0f, px(10f), px(12f), px(10f), 0))
        root.addView(rem)
        updateRemind()

        show(root)
        card.requestFocus()
    }

    private fun load(m: Boolean) {
        morning = m
        items = AdhkarData.list(m)
        counts = AdhkarData.decodeCounts(prefs.sp.getString(storeKey(m), null), items.size)
        // نبدأ من أول ذكر لم يكتمل
        var first = 0
        while (first < items.size && counts[first] >= items[first].repeat) first++
        index = if (first >= items.size) 0 else first
        Ui.styleChip(tabMorning, ut, m)
        Ui.styleChip(tabEvening, ut, !m)
        render()
    }

    private fun save() {
        prefs.sp.edit().putString(storeKey(morning), AdhkarData.encodeCounts(counts)).apply()
    }

    private fun render() {
        if (items.isEmpty()) return
        index = index.coerceIn(0, items.size - 1)
        val d = items[index]
        val done = counts.indices.count { counts[it] >= items[it].repeat }
        tvProgress.text = "الذكر " + AdhkarData.arabicDigits(index + 1) + " من " + AdhkarData.arabicDigits(items.size) +
            "  •  المكتمل: " + AdhkarData.arabicDigits(done)
        tvText.text = d.text
        tvText.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, px(if (d.text.length > 260) 32f else 40f).toFloat())
        textScroll.scrollTo(0, 0)
        tvSource.text = d.source
        tvNote.text = d.note
        tvNote.visibility = if (d.note.isEmpty()) View.GONE else View.VISIBLE
        val c = counts[index]
        tvCounter.text = if (c >= d.repeat) "✔ اكتمل" else
            AdhkarData.arabicDigits(c) + " / " + AdhkarData.arabicDigits(d.repeat) + "   (اضغط OK للعدّ)"
        tvCounter.setTextColor(if (c >= d.repeat) Color.rgb(74, 222, 128) else ut.accent)
    }

    private fun tap() {
        if (items.isEmpty()) return
        val d = items[index]
        if (counts[index] >= d.repeat) { go(index + 1); return }
        counts[index]++
        save()
        render()
        if (counts[index] >= d.repeat) {
            if (index < items.size - 1) handler.postDelayed({ if (!isFinishing) go(index + 1) }, 350L)
            else if (counts.indices.all { counts[it] >= items[it].repeat }) toast("تقبّل الله منك — أتممت الأذكار")
        }
    }

    private fun updateRemind() {
        val on = prefs.sp.getBoolean("adhkar_remind", false)
        remindBtn.text = if (on) "🔔 التذكير في الجزيرة: مفعّل" else "🔕 التذكير في الجزيرة: متوقف"
        mHourBtn.text = "☀ الصباح: " + AdhkarData.arabicDigits(prefs.sp.getInt("adhkar_morning_hour", 6)) + ":٠٠"
        eHourBtn.text = "🌙 المساء: " + AdhkarData.arabicDigits(prefs.sp.getInt("adhkar_evening_hour", 17)) + ":٠٠"
    }

    private fun toggleRemind() {
        val on = !prefs.sp.getBoolean("adhkar_remind", false)
        prefs.sp.edit().putBoolean("adhkar_remind", on).apply()
        updateRemind()
        if (on && !prefs.islandEnabled) toast("التذكير مفعّل لكن الجزيرة الديناميكية معطّلة — فعّلها من إعدادات الجزيرة ليظهر التنبيه")
        else if (on && !IslandController.canDraw(this)) toast("التذكير مفعّل لكن لا يوجد إذن الظهور فوق التطبيقات — راجع إعدادات الجزيرة")
    }

    private fun cycleHour(key: String, def: Int, min: Int, max: Int) {
        val cur = prefs.sp.getInt(key, def)
        val next = if (cur >= max || cur < min) min else cur + 1
        prefs.sp.edit().putInt(key, next).apply()
        updateRemind()
    }

    private fun go(i: Int) {
        if (i < 0 || i >= items.size) return
        index = i
        render()
        card.requestFocus()
    }

    private fun resetCurrent() { if (items.isNotEmpty()) { counts[index] = 0; save(); render() } }
    private fun resetAll() { counts = IntArray(items.size); index = 0; save(); render() }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }
}
