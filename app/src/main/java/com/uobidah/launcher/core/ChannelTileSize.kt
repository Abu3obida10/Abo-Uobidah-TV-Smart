package com.uobidah.launcher.core

/**
 * مقاسات بطاقات القنوات المفضلة بنسب المرجع R3 (لقطة 1264×790): البطاقة 120×120 تقريبًا، الفاصل 19.
 * تُحسب من أبعاد الشاشة الفعلية بالبكسل فتطابق المرجع على أي دقة/كثافة بدل ثوابت dp.
 * تعيد (عرض، ارتفاع، فاصل) بالبكسل.
 */
object ChannelTileSize {
    const val W_RATIO = 0.095f
    const val H_RATIO = 0.152f
    const val GAP_RATIO = 0.015f

    fun of(screenWidthPx: Int, screenHeightPx: Int): Triple<Int, Int, Int> {
        val w = (screenWidthPx * W_RATIO).toInt().coerceAtLeast(1)
        // قياس R3 (1264×790): البطاقات 120×120 مربعة تمامًا (العرض 0.095 من عرض اللقطة)؛ فالارتفاع = العرض على أي شاشة.
        // H_RATIO يبقى للتوثيق فقط (0.152 × 790 = 120)، ولا يُستعمل لأن شاشة التلفزيون 16:9 أعرض من لقطة المرجع 1.6:1.
        val h = w
        val gap = (screenWidthPx * GAP_RATIO).toInt()
        return Triple(w, h, gap)
    }
}
