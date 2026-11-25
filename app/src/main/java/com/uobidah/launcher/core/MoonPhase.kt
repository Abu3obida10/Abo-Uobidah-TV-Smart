package com.uobidah.launcher.core

import kotlin.math.PI
import kotlin.math.cos

/** طور القمر التقريبي (متوسط الشهر الاقتراني). دقته نحو نصف يوم؛ مناسب للعرض لا للرصد. منطق بحت بلا Android. */
object MoonPhase {
    const val SYNODIC_DAYS = 29.530588853
    private const val EPOCH_JD = 2451550.1 // محاق 6 يناير 2000 ~18:14 UTC

    /** عمر القمر بالأيام منذ آخر محاق، من وقت يونكس بالميلي ثانية (UTC). */
    fun ageDays(epochMs: Long): Double {
        val jd = epochMs / 86_400_000.0 + 2_440_587.5
        val a = (jd - EPOCH_JD) % SYNODIC_DAYS
        return if (a < 0) a + SYNODIC_DAYS else a
    }

    /** نسبة الإضاءة 0..100. */
    fun illuminationPercent(ageDays: Double): Int =
        Math.round((1 - cos(2 * PI * ageDays / SYNODIC_DAYS)) / 2 * 100).toInt()

    fun nameAr(ageDays: Double): String = when {
        ageDays < 1.85 -> "محاق"
        ageDays < 5.54 -> "هلال متزايد"
        ageDays < 9.23 -> "تربيع أول"
        ageDays < 12.91 -> "أحدب متزايد"
        ageDays < 16.61 -> "بدر"
        ageDays < 20.30 -> "أحدب متناقص"
        ageDays < 23.99 -> "تربيع أخير"
        ageDays < 27.68 -> "هلال متناقص"
        else -> "محاق"
    }
}
