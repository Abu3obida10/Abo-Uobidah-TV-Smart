package com.uobidah.launcher.core

/**
 * عرض بطاقة المفضلة في تصميم الصلاة (مرجع R2): 4 بطاقات تتسع كاملة داخل العمود الأيسر بلا قص.
 * كل القيم بالبكسل. الفواصل بين البطاقات فقط (count-1)؛ آخر بطاقة بلا هامش لاحق.
 */
object ArchTileSize {
    /** عرض العمود الأيسر المتوقع قبل القياس: يطابق addSplit (أوزان 1.7 : 1 بعد خصم فاصل الـsplit من العرض كله). */
    fun estimatedPane(screenPx: Int, sidePaddingTotalPx: Int, splitGapPx: Int): Int =
        (((screenPx - sidePaddingTotalPx - splitGapPx) * 1.7f) / 2.7f).toInt()

    /** العرض الفعلي المتاح (بعد قياس الـHorizontalScrollView) → عرض البطاقة التي تتسع [count] منها مع فواصلها. */
    fun fit(availablePx: Int, tileGapPx: Int, count: Int, minPx: Int = 1): Int {
        if (count <= 0) return 0
        return ((availablePx - tileGapPx * (count - 1)) / count).coerceAtLeast(minPx)
    }

    /** تقدير أولي قبل أول قياس فعلي؛ يُستبدل بـ[fit] عند أول onLayout. */
    fun width(screenPx: Int, sidePaddingTotalPx: Int, splitGapPx: Int, tileGapPx: Int, count: Int, minPx: Int = 1): Int =
        fit(estimatedPane(screenPx, sidePaddingTotalPx, splitGapPx), tileGapPx, count, minPx)
}
