package com.uobidah.launcher

import android.content.Context
import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.drawable.Drawable
import android.widget.TextView

/**
 * نظام أيقونات خطية (Vector) مرسومة بالكود بدل الإيموجي. شبكة 24×24 وسُمك خط ثابت وأطراف مستديرة.
 * الأيقونات تأخذ لون النص تلقائيًا. مصدر البيانات: IconData (مولَّد من work/icons_def.py).
 */
object Icons {
    private class Part(val fill: Boolean, val path: Path, val circles: List<FloatArray>)

    private val cache = HashMap<String, List<Part>>()

    private fun parsePart(src: String): Part {
        var s = src
        var fill = false
        if (s.startsWith("f:")) { fill = true; s = s.substring(2) }
        val t = s.trim().split(" ").filter { it.isNotEmpty() }
        val path = Path()
        val circles = ArrayList<FloatArray>()
        var x = 0f
        var y = 0f
        var sx = 0f
        var sy = 0f
        var i = 0
        while (i < t.size) {
            when (t[i]) {
                "M" -> { x = t[i + 1].toFloat(); y = t[i + 2].toFloat(); sx = x; sy = y; path.moveTo(x, y); i += 3 }
                "L" -> { x = t[i + 1].toFloat(); y = t[i + 2].toFloat(); path.lineTo(x, y); i += 3 }
                "H" -> { x = t[i + 1].toFloat(); path.lineTo(x, y); i += 2 }
                "V" -> { y = t[i + 1].toFloat(); path.lineTo(x, y); i += 2 }
                "C" -> {
                    path.cubicTo(t[i + 1].toFloat(), t[i + 2].toFloat(), t[i + 3].toFloat(), t[i + 4].toFloat(), t[i + 5].toFloat(), t[i + 6].toFloat())
                    x = t[i + 5].toFloat(); y = t[i + 6].toFloat(); i += 7
                }
                "Z" -> { path.close(); x = sx; y = sy; i += 1 }
                "O" -> { circles.add(floatArrayOf(t[i + 1].toFloat(), t[i + 2].toFloat(), t[i + 3].toFloat())); i += 4 }
                else -> i += 1
            }
        }
        return Part(fill, path, circles)
    }

    private fun parts(key: String): List<Part> {
        cache[key]?.let { return it }
        val src = IconData.map[key] ?: IconData.map.getValue("info")
        val list = src.map { parsePart(it) }
        cache[key] = list
        return list
    }

    fun has(key: String): Boolean = IconData.map.containsKey(key)

    /** أيقونة جاهزة كـ Drawable بحجم مربع sizePx ولون واحد. */
    fun drawable(key: String, color: Int, sizePx: Int): Drawable {
        val d = VectorIcon(parts(key), color)
        d.setBounds(0, 0, sizePx, sizePx)
        return d
    }

    /** يغيّر لون أيقونة موجودة بدون إنشاء Drawable جديد؛ true إن كانت أيقونة Vector. */
    fun recolor(d: Drawable?, color: Int): Boolean {
        val v = d as? VectorIcon ?: return false
        v.setIconColor(color)
        return true
    }

    private class VectorIcon(private val parts: List<Part>, color: Int) : Drawable() {
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = color
            strokeWidth = 1.9f
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }

        fun setIconColor(c: Int) {
            if (paint.color != c) { paint.color = c; invalidateSelf() }
        }

        override fun draw(canvas: Canvas) {
            val b = bounds
            if (b.isEmpty) return
            val sc = b.width() / 24f
            // سماكة الخط تتبع حجم الأيقونة: بدون هذا كانت الأيقونات الكبيرة ترسم بخط 1.9px ثابت
            // فتبدو باهتة على التلفزيون (الكانفس متدرج لكن سماكة القلم لا ترث التدرج تلقائيًا).
            paint.strokeWidth = 1.9f * sc
            canvas.save()
            canvas.translate(b.left.toFloat(), b.top.toFloat())
            canvas.scale(sc, sc)
            for (p in parts) {
                paint.style = if (p.fill) Paint.Style.FILL else Paint.Style.STROKE
                canvas.drawPath(p.path, paint)
                for (o in p.circles) canvas.drawCircle(o[0], o[1], o[2], paint)
            }
            canvas.restore()
        }

        override fun setAlpha(alpha: Int) { paint.alpha = alpha; invalidateSelf() }
        override fun setColorFilter(colorFilter: ColorFilter?) { paint.colorFilter = colorFilter; invalidateSelf() }
        @Suppress("OVERRIDE_DEPRECATION")
        override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
        override fun getIntrinsicWidth(): Int = bounds.width()
        override fun getIntrinsicHeight(): Int = bounds.height()
    }

    // ---------- تحويل الإيموجي إلى أيقونات ----------

    class Split(val key: String?, val rest: String)

    private fun isPictograph(cp: Int): Boolean =
        (cp in 0x1F300..0x1FAFF) || (cp in 0x2600..0x27BF) || cp == 0x2B50 || cp == 0x2B1B || cp == 0x2B1C ||
            (cp in 0x2B05..0x2B07) || cp == 0x2B06 || cp == 0xFE0F || cp == 0x200D || (cp in 0x23E9..0x23F3)

    private fun stripPictographs(s: String): String {
        var any = false
        var i = 0
        while (i < s.length) {
            val cp = s.codePointAt(i)
            if (isPictograph(cp)) { any = true; break }
            i += Character.charCount(cp)
        }
        if (!any) return s
        val sb = StringBuilder()
        i = 0
        while (i < s.length) {
            val cp = s.codePointAt(i)
            if (!isPictograph(cp)) sb.appendCodePoint(cp)
            i += Character.charCount(cp)
        }
        return sb.toString().trim()
    }

    /** يفصل الرمز الأول إن كان إيموجي/رمزًا معروفًا إلى مفتاح أيقونة، ويحذف باقي الإيموجي من النص. null = لا تغيير. */
    fun split(s: String): Split? {
        var i = 0
        while (i < s.length && s[i] == ' ') i++
        if (i >= s.length) return null
        val cp = s.codePointAt(i)
        val g = String(Character.toChars(cp))
        val key = IconData.glyphs[g]
        val leading = key != null || isPictograph(cp)
        var rest = s
        if (leading) {
            var j = i + Character.charCount(cp)
            while (j < s.length && (s[j] == '\uFE0F' || s[j] == ' ' || s[j] == '\u200D')) j++
            rest = s.substring(j)
        }
        val cleaned = stripPictographs(rest)
        if (!leading && cleaned == s) return null
        return Split(if (leading) key else null, cleaned)
    }

    /** تنظيف نص Toast من الإيموجي. */
    fun plain(s: String): String = split(s)?.rest ?: s
}

/**
 * TextView يحوّل أول رمز/إيموجي في النص إلى أيقونة Vector في بداية النص (يمين النص في RTL)،
 * ويحذف باقي الإيموجي. يتبع لون النص وحجمه تلقائيًا. النصوص المنسّقة (Spannable) لا تُمسّ.
 */
class IconTextView(context: Context) : TextView(context) {
    private var iconKey: String? = null
    private var busy = false
    private var iconDrawable: Drawable? = null
    private var shownKey: String? = null
    private var shownSize = 0
    private var shownPadding = -1
    private var lastRaw: String? = null

    private fun clearIcon() {
        iconKey = null
        if (iconDrawable != null) {
            iconDrawable = null
            shownKey = null
            shownPadding = -1
            setCompoundDrawablesRelative(null, null, null, null)
        }
    }

    override fun setText(text: CharSequence?, type: TextView.BufferType?) {
        if (busy || text == null || text !is String) {
            if (!busy) { lastRaw = null; if (iconKey != null) clearIcon() }
            super.setText(text, type)
            return
        }
        // نفس النص تماماً (مثل ساعة تُحدَّث كل ثانية): لا إعادة قياس ولا إعادة رسم
        if (text == lastRaw) return
        lastRaw = text
        val sp = Icons.split(text)
        if (sp == null) {
            if (iconKey != null) clearIcon()
            super.setText(text, type)
            return
        }
        busy = true
        super.setText(sp.rest, type)
        busy = false
        iconKey = sp.key
        applyIcon()
    }

    private fun applyIcon() {
        val key = iconKey
        if (key == null) { clearIcon(); return }
        val sz = (textSize * 1.15f).toInt().coerceAtLeast(14)
        val cur = iconDrawable
        // نفس الأيقونة والحجم: نغيّر اللون فقط (بدون بناء Drawable جديد ولا requestLayout)
        if (cur != null && shownKey == key && shownSize == sz && Icons.recolor(cur, currentTextColor)) {
            val pad = if (text.isNullOrEmpty()) 0 else (sz * 0.38f).toInt()
            if (pad != shownPadding) { shownPadding = pad; compoundDrawablePadding = pad }
            return
        }
        val d = Icons.drawable(key, currentTextColor, sz)
        iconDrawable = d
        shownKey = key
        shownSize = sz
        setCompoundDrawablesRelative(d, null, null, null)
        val pad = if (text.isNullOrEmpty()) 0 else (sz * 0.38f).toInt()
        shownPadding = pad
        compoundDrawablePadding = pad
    }

    override fun setTextColor(color: Int) {
        super.setTextColor(color)
        if (iconKey != null) applyIcon()
    }

    override fun setTextSize(unit: Int, size: Float) {
        super.setTextSize(unit, size)
        if (iconKey != null) applyIcon()
    }
}

/** مولَّد تلقائيًا من work/icons_def.py — لا تعدّله يدويًا. */
object IconData {
    val map: Map<String, List<String>> = mapOf(        "home" to listOf("M 3 11 L 12 3 L 21 11", "M 5.5 9.5 V 20 H 18.5 V 9.5", "M 10 20 V 14 H 14 V 20"),
        "search" to listOf("O 11 11 6.5", "M 16 16 L 21 21"),
        "settings" to listOf("M 10.29 4.59 L 10.5 2.52 L 13.5 2.52 L 13.71 4.59 L 16.03 5.55 L 17.64 4.23 L 19.77 6.36 L 18.45 7.97 L 19.41 10.29 L 21.48 10.5 L 21.48 13.5 L 19.41 13.71 L 18.45 16.03 L 19.77 17.64 L 17.64 19.77 L 16.03 18.45 L 13.71 19.41 L 13.5 21.48 L 10.5 21.48 L 10.29 19.41 L 7.97 18.45 L 6.36 19.77 L 4.23 17.64 L 5.55 16.03 L 4.59 13.71 L 2.52 13.5 L 2.52 10.5 L 4.59 10.29 L 5.55 7.97 L 4.23 6.36 L 6.36 4.23 L 7.97 5.55 Z", "O 12 12 3"),
        "bell" to listOf("M 6 16 V 11 C 6 7.7 8.7 5 12 5 C 15.3 5 18 7.7 18 11 V 16 L 20 18 H 4 Z", "M 10 21 H 14", "M 12 3 V 5"),
        "bell_off" to listOf("M 6 16 V 11 C 6 7.7 8.7 5 12 5 C 15.3 5 18 7.7 18 11 V 16 L 20 18 H 4 Z", "M 10 21 H 14", "M 4 3.5 L 20 20.5"),
        "wifi" to listOf("M 2.5 9 C 8 4.5 16 4.5 21.5 9", "M 5.5 12.5 C 9 9.5 15 9.5 18.5 12.5", "M 8.7 16 C 10.5 14.5 13.5 14.5 15.3 16", "f:O 12 19.5 1.3"),
        "plug" to listOf("M 9 3 V 8", "M 15 3 V 8", "M 6 8 H 18 V 12 C 18 15.3 15.3 18 12 18 C 8.7 18 6 15.3 6 12 Z", "M 12 18 V 21"),
        "rocket" to listOf("M 12 2 C 15.5 4.5 17 8.5 16 14 H 8 C 7 8.5 8.5 4.5 12 2 Z", "O 12 9 1.6", "M 8 14 L 5 17 L 5 20 L 9 18", "M 16 14 L 19 17 L 19 20 L 15 18", "M 10 18 L 12 22 L 14 18"),
        "radio" to listOf("M 4 9 H 20 V 20 H 4 Z", "M 7 9 L 17 3", "O 9.5 14.5 2.5", "M 15 13 H 17.5", "M 15 16.5 H 17.5"),
        "book" to listOf("M 12 6.5 C 10 5 7 4.5 3.5 5 V 18.5 C 7 18 10 18.5 12 20 C 14 18.5 17 18 20.5 18.5 V 5 C 17 4.5 14 5 12 6.5 Z", "M 12 6.5 V 20"),
        "sun" to listOf("O 12 12 4", "M 18.8 12 L 21.3 12 M 16.81 16.81 L 18.58 18.58 M 12 18.8 L 12 21.3 M 7.19 16.81 L 5.42 18.58 M 5.2 12 L 2.7 12 M 7.19 7.19 L 5.42 5.42 M 12 5.2 L 12 2.7 M 16.81 7.19 L 18.58 5.42"),
        "moon" to listOf("M 20 14.5 C 19 15 17.8 15.3 16.5 15.3 C 12 15.3 8.7 11.8 8.7 7.5 C 8.7 6 9.1 4.7 9.8 3.5 C 6.3 4.6 4 8 4 11.8 C 4 16.8 8 20.5 13 20.5 C 16.2 20.5 19 18.8 20.5 16 C 20.5 15.4 20.3 14.9 20 14.5 Z"),
        "mosque" to listOf("M 5 20 V 13.5 C 5 10 8 7.5 12 7.5 C 16 7.5 19 10 19 13.5 V 20 Z", "M 12 7.5 V 4.5", "M 10.5 3 C 10.9 3.8 11.6 4.2 12.5 4.2 C 11.8 4.9 11 5.3 10.2 5.3", "M 2.5 20 H 21.5", "M 10 20 V 16.5 C 10 14.8 14 14.8 14 16.5 V 20", "M 2.5 20 V 11 L 3.7 8.5 L 4.9 11 V 20", "M 19.1 20 V 11 L 20.3 8.5 L 21.5 11 V 20"),
        "pin" to listOf("M 12 21 C 12 21 5 14.5 5 9.5 C 5 5.6 8.1 3 12 3 C 15.9 3 19 5.6 19 9.5 C 19 14.5 12 21 12 21 Z", "O 12 9.5 2.5"),
        "trophy" to listOf("M 7 4 H 17 V 10 C 17 12.8 14.8 15 12 15 C 9.2 15 7 12.8 7 10 Z", "M 7 6 H 4 C 4 9 5.5 10.5 7 10.5", "M 17 6 H 20 C 20 9 18.5 10.5 17 10.5", "M 12 15 V 18", "M 8 21 H 16 L 15 18 H 9 Z"),
        "tv" to listOf("M 3 5.5 H 21 V 17 H 3 Z", "M 8 21 H 16", "M 12 17 V 21"),
        "film" to listOf("M 3 5 H 21 V 19 H 3 Z", "M 8 5 V 19", "M 16 5 V 19", "M 3 9.5 H 8", "M 3 14.5 H 8", "M 16 9.5 H 21", "M 16 14.5 H 21"),
        "gamepad" to listOf("M 6 8 H 18 C 20.2 8 21.5 10 21.7 12.5 L 22 16 C 22.1 17.5 21 18.5 19.8 18.5 C 18.8 18.5 18 18 17.4 17 L 16.2 15 H 7.8 L 6.6 17 C 6 18 5.2 18.5 4.2 18.5 C 3 18.5 1.9 17.5 2 16 L 2.3 12.5 C 2.5 10 3.8 8 6 8 Z", "M 7 10.5 V 13.5", "M 5.5 12 H 8.5", "f:O 15.5 11 0.9", "f:O 18 13 0.9"),
        "apps" to listOf("M 4 4 H 10 V 10 H 4 Z", "M 14 4 H 20 V 10 H 14 Z", "M 4 14 H 10 V 20 H 4 Z", "M 14 14 H 20 V 20 H 14 Z"),
        "ball" to listOf("O 12 12 9", "M 12 8 L 15.3 10.4 L 14 14.2 H 10 L 8.7 10.4 Z", "M 12 8 V 3.2", "M 15.3 10.4 L 20 9", "M 14 14.2 L 17 18", "M 10 14.2 L 7 18", "M 8.7 10.4 L 4 9"),
        "image" to listOf("M 3 5 H 21 V 19 H 3 Z", "O 8.5 10 1.8", "M 3 17 L 9 12 L 13 16 L 16 13 L 21 18"),
        "smile" to listOf("O 12 12 9", "M 8 14 C 9 16 10.5 17 12 17 C 13.5 17 15 16 16 14", "f:O 9 9.5 1", "f:O 15 9.5 1"),
        "check" to listOf("M 4 12.5 L 9.5 18 L 20 6.5"),
        "close" to listOf("M 5 5 L 19 19", "M 19 5 L 5 19"),
        "arrow_right" to listOf("M 4 12 H 20", "M 14 6 L 20 12 L 14 18"),
        "arrow_left" to listOf("M 20 12 H 4", "M 10 6 L 4 12 L 10 18"),
        "chevron_down" to listOf("M 6 9 L 12 15 L 18 9"),
        "chevron_up" to listOf("M 6 15 L 12 9 L 18 15"),
        "dot_on" to listOf("O 12 12 8", "f:O 12 12 4"),
        "dot_off" to listOf("O 12 12 8"),
        "box_on" to listOf("M 4 4 H 20 V 20 H 4 Z", "M 7.5 12.5 L 10.7 15.7 L 16.5 9"),
        "box_off" to listOf("M 4 4 H 20 V 20 H 4 Z"),
        "clock" to listOf("O 12 12 9", "M 12 7 V 12 L 15.5 14"),
        "cloud" to listOf("M 7 18 C 4.2 18 2.5 16 2.5 13.7 C 2.5 11.5 4.2 9.8 6.3 9.6 C 7 6.8 9.3 5 12 5 C 15 5 17.2 7.1 17.6 9.8 C 20 10 21.5 11.8 21.5 14 C 21.5 16.3 19.8 18 17.5 18 Z"),
        "play" to listOf("f:M 7 4 L 20 12 L 7 20 Z"),
        "pause" to listOf("M 8 5 V 19", "M 16 5 V 19"),
        "star" to listOf("M 12 2.5 L 14.47 8.6 L 21.04 9.06 L 15.99 13.3 L 17.58 19.69 L 12 16.2 L 6.42 19.69 L 8.01 13.3 L 2.96 9.06 L 9.53 8.6 Z"),
        "heart" to listOf("M 12 20.5 C 4 14.5 3 10.5 3 8.5 C 3 5.8 5 4 7.3 4 C 9.3 4 11 5.2 12 7 C 13 5.2 14.7 4 16.7 4 C 19 4 21 5.8 21 8.5 C 21 10.5 20 14.5 12 20.5 Z"),
        "palette" to listOf("M 12 3 C 7 3 3 7 3 12 C 3 17 7 21 12 21 C 13.5 21 14 20 14 19 C 14 18 13.3 17.5 13.3 16.5 C 13.3 15.4 14.2 14.8 15.2 14.8 H 17 C 19.2 14.8 21 13.2 21 11 C 21 6.5 17 3 12 3 Z", "f:O 7.5 11 1", "f:O 10 7.5 1", "f:O 14.5 7.5 1", "f:O 17 11 1"),
        "layout" to listOf("M 3 4 H 21 V 20 H 3 Z", "M 3 9.5 H 21", "M 9.5 9.5 V 20"),
        "download" to listOf("M 12 3 V 15", "M 7 10.5 L 12 15.5 L 17 10.5", "M 4 20 H 20"),
        "volume" to listOf("M 4 9.5 H 8 L 13 5 V 19 L 8 14.5 H 4 Z", "M 16.5 9 C 17.8 10.5 17.8 13.5 16.5 15", "M 19 6.5 C 21.5 9.5 21.5 14.5 19 17.5"),
        "shield" to listOf("M 12 3 L 20 6 V 12 C 20 16.5 16.5 19.8 12 21 C 7.5 19.8 4 16.5 4 12 V 6 Z", "M 8.5 12 L 11 14.5 L 15.5 9.5"),
        "undo" to listOf("M 9 7 L 4 12 L 9 17", "M 4 12 H 15 C 18 12 20.5 14.2 20.5 17 V 18"),
        "refresh" to listOf("M 20 12 C 20 16.4 16.4 20 12 20 C 9.2 20 6.8 18.6 5.4 16.5", "M 4 12 C 4 7.6 7.6 4 12 4 C 14.8 4 17.2 5.4 18.6 7.5", "M 19 3 V 8 H 14", "M 5 21 V 16 H 10"),
        "beads" to listOf("f:O 12 3.2 1.5", "f:O 16.37 4.79 1.5", "f:O 18.7 8.82 1.5", "f:O 17.89 13.4 1.5", "f:O 14.33 16.39 1.5", "f:O 9.67 16.39 1.5", "f:O 6.11 13.4 1.5", "f:O 5.3 8.82 1.5", "f:O 7.63 4.79 1.5", "M 12 17 V 21.5"),
        "list" to listOf("M 8 6 H 21", "M 8 12 H 21", "M 8 18 H 21", "f:O 4 6 1.2", "f:O 4 12 1.2", "f:O 4 18 1.2"),
        "island" to listOf("M 8 8 H 16 C 18.2 8 20 9.8 20 12 C 20 14.2 18.2 16 16 16 H 8 C 5.8 16 4 14.2 4 12 C 4 9.8 5.8 8 8 8 Z", "f:O 16.5 12 1.2"),
        "info" to listOf("O 12 12 9", "M 12 11 V 16", "f:O 12 8 0.9"),
        "warning" to listOf("M 12 3.5 L 21.5 20 H 2.5 Z", "M 12 10 V 14", "f:O 12 17 0.9"),
        "bolt" to listOf("M 13 2 L 4 14 H 11 L 10 22 L 20 9.5 H 13 Z"),
        "compass" to listOf("O 12 12 9", "M 15.5 8.5 L 13.5 13.5 L 8.5 15.5 L 10.5 10.5 Z"),
        "calendar" to listOf("M 4 5.5 H 20 V 20 H 4 Z", "M 4 10 H 20", "M 8 3 V 7", "M 16 3 V 7"),
        "mic" to listOf("M 9 4 C 9 2.9 9.9 2 11 2 H 13 C 14.1 2 15 2.9 15 4 V 11 C 15 12.1 14.1 13 13 13 H 11 C 9.9 13 9 12.1 9 11 Z", "M 5.5 11 C 5.5 14.6 8.4 17.5 12 17.5 C 15.6 17.5 18.5 14.6 18.5 11", "M 12 17.5 V 21", "M 8.5 21 H 15.5"),
        "person" to listOf("O 12 8 3.6", "M 4.5 20.5 C 4.5 16.5 7.8 14 12 14 C 16.2 14 19.5 16.5 19.5 20.5"),
        "lock" to listOf("M 5 11 H 19 V 21 H 5 Z", "M 8 11 V 7.5 C 8 5.3 9.8 3.5 12 3.5 C 14.2 3.5 16 5.3 16 7.5 V 11"),
        "external" to listOf("M 14 4 H 20 V 10", "M 20 4 L 11 13", "M 18 14 V 20 H 4 V 6 H 10"),
        "plus" to listOf("M 12 5 V 19", "M 5 12 H 19"),
        "tune" to listOf("M 4 7 H 20", "M 4 17 H 20", "f:O 9 7 2.2", "f:O 15 17 2.2"),
        "flag" to listOf("M 5 21 V 4", "M 5 4 H 18 L 15 8.5 L 18 13 H 5"),
        "pencil" to listOf("M 4 20 L 5 15 L 16 4 L 20 8 L 9 19 Z", "M 14 6 L 18 10"),
        "trash" to listOf("M 4 7 H 20", "M 9 7 V 4 H 15 V 7", "M 6 7 L 7 20 H 17 L 18 7", "M 10 11 V 16", "M 14 11 V 16"),
        "pushpin" to listOf("M 9 3 H 15 L 14.5 9 L 18 13 H 6 L 9.5 9 Z", "M 12 13 V 21"),
        "folder" to listOf("M 3 6 H 9.5 L 11.5 8.5 H 21 V 19 H 3 Z"),
        "volume_off" to listOf("M 4 9.5 H 8 L 13 5 V 19 L 8 14.5 H 4 Z", "M 17 9.5 L 22 14.5", "M 22 9.5 L 17 14.5"),
        "stop" to listOf("f:M 6 6 H 18 V 18 H 6 Z"),
        "music" to listOf("M 9 18 V 5 L 20 3 V 16", "O 6.5 18 2.5", "O 17.5 16 2.5"),
        "globe" to listOf("O 12 12 9", "M 3 12 H 21", "M 12 3 C 8 7 8 17 12 21", "M 12 3 C 16 7 16 17 12 21"),
        "link" to listOf("M 10 14 C 8 12 8 9.5 10 7.5 L 12.5 5 C 14.5 3 17.5 3 19 4.5 C 20.5 6 20.5 9 18.5 11 L 17 12.5", "M 14 10 C 16 12 16 14.5 14 16.5 L 11.5 19 C 9.5 21 6.5 21 5 19.5 C 3.5 18 3.5 15 5.5 13 L 7 11.5"),
        "bookmark" to listOf("M 6 3 H 18 V 21 L 12 16.5 L 6 21 Z"),
        "accessibility" to listOf("O 12 4.5 1.8", "M 4 8.5 H 20", "M 12 8.5 V 14", "M 12 14 L 8.5 21", "M 12 14 L 15.5 21"),
        "rain" to listOf("M 7 15 C 4.2 15 2.5 13 2.5 10.7 C 2.5 8.5 4.2 6.8 6.3 6.6 C 7 3.8 9.3 2 12 2 C 15 2 17.2 4.1 17.6 6.8 C 20 7 21.5 8.8 21.5 11 C 21.5 13.3 19.8 15 17.5 15 Z", "M 8 18 L 7 21", "M 12 18 L 11 21", "M 16 18 L 15 21"),
        "battery" to listOf("M 3 8 H 19 V 16 H 3 Z", "M 21 10.5 V 13.5", "M 6 11 V 13")
    )

    val glyphs: Map<String, String> = mapOf(        "📍" to "pin",
        "🏆" to "trophy",
        "📶" to "wifi",
        "🔌" to "plug",
        "🚀" to "rocket",
        "📻" to "radio",
        "🔍" to "search",
        "🔎" to "search",
        "🔔" to "bell",
        "🔕" to "bell_off",
        "⚙" to "settings",
        "🕌" to "mosque",
        "☪" to "mosque",
        "📖" to "book",
        "📚" to "book",
        "☀" to "sun",
        "🌅" to "sun",
        "🌄" to "sun",
        "🌇" to "sun",
        "🌙" to "moon",
        "🌌" to "moon",
        "😊" to "smile",
        "🎬" to "film",
        "🎮" to "gamepad",
        "🧩" to "apps",
        "▦" to "apps",
        "▣" to "tv"
        "⌕" to "search"
        "♧" to "bell",
        "▧" to "apps",
        "🔲" to "apps",
        "⬜" to "apps",
        "◻" to "apps",
        "🪟" to "apps",
        "📦" to "apps",
        "⚽" to "ball",
        "🏟" to "ball",
        "🥅" to "ball",
        "🖼" to "image",
        "🏠" to "home",
        "⌂" to "home",
        "✅" to "check",
        "✔" to "check",
        "✓" to "check",
        "▶" to "play",
        "⏸" to "pause",
        "⏹" to "stop",
        "■" to "stop",
        "⭐" to "star",
        "★" to "star",
        "✦" to "star",
        "✨" to "star",
        "←" to "arrow_left",
        "⬅" to "arrow_left",
        "→" to "arrow_right",
        "➡" to "arrow_right",
        "➜" to "arrow_right",
        "↗" to "external",
        "↑" to "chevron_up",
        "⬆" to "chevron_up",
        "⇧" to "chevron_up",
        "🔝" to "chevron_up",
        "↓" to "chevron_down",
        "⬇" to "chevron_down",
        "▲" to "chevron_up",
        "▼" to "chevron_down",
        "●" to "dot_on",
        "◉" to "dot_on",
        "○" to "dot_off",
        "☑" to "box_on",
        "☐" to "box_off",
        "▤" to "layout",
        "◐" to "palette",
        "➕" to "plus",
        "🔊" to "volume",
        "🔉" to "volume",
        "🔇" to "volume_off",
        "🎚" to "tune",
        "✏" to "pencil",
        "🗑" to "trash",
        "📌" to "pushpin",
        "🔖" to "bookmark",
        "🗂" to "folder",
        "📁" to "folder",
        "📋" to "list",
        "⚠" to "warning",
        "⛔" to "warning",
        "❌" to "close",
        "✕" to "close",
        "↩" to "undo",
        "↪" to "undo",
        "↺" to "refresh",
        "↻" to "refresh",
        "🔄" to "refresh",
        "🔁" to "refresh",
        "♻" to "refresh",
        "📺" to "tv",
        "🎵" to "music",
        "♫" to "music",
        "♧" to "music",
        "♿" to "accessibility",
        "🌐" to "globe",
        "🌍" to "globe",
        "🔗" to "link",
        "🔑" to "lock",
        "🔐" to "lock",
        "🔒" to "lock",
        "🛡" to "shield",
        "💾" to "download",
        "⚡" to "bolt",
        "🧭" to "compass",
        "⌖" to "compass",
        "⏱" to "clock",
        "⏳" to "clock",
        "🕘" to "clock",
        "🕰" to "clock",
        "⏰" to "clock",
        "☁" to "cloud",
        "🌫" to "cloud",
        "🌤" to "cloud",
        "⛅" to "cloud",
        "🌦" to "cloud",
        "🌧" to "rain",
        "⛈" to "rain",
        "🌨" to "rain",
        "❄" to "rain",
        "🔋" to "battery",
        "📿" to "beads",
        "🙈" to "info",
        "💬" to "info",
        "📣" to "bell",
        "📡" to "wifi",
        "👁" to "info",
        "🌡" to "info",
        "🪪" to "person",
        "🏝" to "image",
        "🧪" to "info",
        "💊" to "info",
        "🛒" to "apps",
        "📐" to "layout"
    )
}
