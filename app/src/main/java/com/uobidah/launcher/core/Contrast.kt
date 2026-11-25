package com.uobidah.launcher.core

import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/** حساب تباين الألوان (WCAG) دون اعتماد على Android كي يُختبر على JVM. الألوان ARGB معتمة. */
object Contrast {
    const val MIN_TEXT = 4.5
    private const val WHITE = -1               // 0xFFFFFFFF
    private const val DARK_ON = -16772582      // 0xFF04121A

    private fun ch(c: Int, shift: Int) = (c shr shift) and 0xFF
    private fun lin(v: Int): Double {
        val s = v / 255.0
        return if (s <= 0.03928) s / 12.92 else ((s + 0.055) / 1.055).pow(2.4)
    }

    fun luminance(c: Int): Double = 0.2126 * lin(ch(c, 16)) + 0.7152 * lin(ch(c, 8)) + 0.0722 * lin(ch(c, 0))

    fun ratio(a: Int, b: Int): Double {
        val x = luminance(a); val y = luminance(b)
        return (max(x, y) + 0.05) / (min(x, y) + 0.05)
    }

    private fun mix(c: Int, target: Int, f: Double): Int {
        fun m(v: Int) = (v + (target - v) * f).toInt().coerceIn(0, 255)
        return (0xFF shl 24) or (m(ch(c, 16)) shl 16) or (m(ch(c, 8)) shl 8) or m(ch(c, 0))
    }

    /** نص فوق خلفية: المفضّل إن كان واضحًا، وإلا الأبيض أو الأسود الداكن أيهما أوضح. */
    fun onColor(bg: Int, preferred: Int, min: Double = MIN_TEXT): Int {
        if (ratio(preferred, bg) >= min) return preferred
        return if (ratio(WHITE, bg) >= ratio(DARK_ON, bg)) WHITE else DARK_ON
    }

    /** يغمّق أو يفتّح لون نص فوق خلفية حتى يبلغ التباين المطلوب (يحافظ على الدرجة قدر الإمكان). */
    fun ensure(fg: Int, bg: Int, min: Double = MIN_TEXT): Int {
        if (ratio(fg, bg) >= min) return fg
        val target = if (luminance(bg) > 0.5) 0 else 255
        var c = fg
        for (i in 1..20) {
            c = mix(fg, target, i * 0.05)
            if (ratio(c, bg) >= min) return c
        }
        return c
    }

    /** يعدّل الخلفية (مثل عنصر محدد بلون التمييز) كي يبلغ نصّها التباين المطلوب. */
    fun fitBackground(bg: Int, fg: Int, min: Double = MIN_TEXT): Int {
        if (ratio(fg, bg) >= min) return bg
        val target = if (luminance(fg) > 0.5) 0 else 255
        var c = bg
        for (i in 1..20) {
            c = mix(bg, target, i * 0.05)
            if (ratio(fg, c) >= min) return c
        }
        return c
    }
}
