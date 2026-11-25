package com.uobidah.launcher.core

/**
 * عرض كروت المباريات داخل لوحة ضيقة (تصميم14 «المباريات أولاً»: لوحة المباريات بجانب جدول الدوري).
 * القاعدة: تظهر بطاقتان كاملتان على الأقل حين يتسع العرض، ويظهر جزء من التالية (PEEK) كدليل على وجود المزيد،
 * بدل بطاقة واحدة تملأ اللوحة وتُخفي الباقي عند حدّ لوحة الدوري. كل القيم بالبكسل.
 */
object MatchPaneCards {
    /** نسبة الظاهر من البطاقة التالية عندما يزيد العدد عن المتسع. */
    const val PEEK = 0.18f

    /** كم بطاقة كاملة تتسع بعرض لا يقل عن [minCardPx] مع الفواصل. دائمًا ≥ 1. */
    fun slots(usablePx: Int, gapPx: Int, minCardPx: Int): Int =
        ((usablePx + gapPx) / (minCardPx + gapPx).coerceAtLeast(1)).coerceAtLeast(1)

    fun width(containerPx: Int, rowPaddingPx: Int, gapPx: Int, count: Int, minCardPx: Int, floorPx: Int): Int {
        val usable = containerPx - rowPaddingPx
        val s = slots(usable, gapPx, minCardPx)
        val w = if (count <= s) (usable - gapPx * (count.coerceAtLeast(1) - 1)) / count.coerceAtLeast(1)
                else ((usable - gapPx * s) / (s + PEEK)).toInt()
        return w.coerceAtLeast(floorPx)
    }

    /** عدد البطاقات الكاملة الظاهرة فعلًا بالعرض المحسوب (للاختبار). */
    fun fullyVisible(containerPx: Int, rowPaddingPx: Int, gapPx: Int, cardPx: Int): Int =
        (((containerPx - rowPaddingPx) + gapPx) / (cardPx + gapPx).coerceAtLeast(1))
}
