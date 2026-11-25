package com.uobidah.launcher.core

/**
 * يختار الصفوف المعروضة في بطاقة الترتيب الصغيرة (مرجع R7): الأوائل ثم فريقك مع جاره قبله وبعده،
 * بدل عرض أول ٦ فقط فيختفي فريقك إن كان في وسط الجدول. يعيد فهارس مرتّبة بلا تكرار.
 */
object StandingsWindow {
    fun indices(size: Int, isFavorite: (Int) -> Boolean, top: Int = 3, neighbors: Int = 1): List<Int> {
        if (size <= 0) return emptyList()
        val out = sortedSetOf<Int>()
        for (i in 0 until minOf(top, size)) out.add(i)
        for (i in 0 until size) if (isFavorite(i)) {
            for (j in (i - neighbors)..(i + neighbors)) if (j in 0 until size) out.add(j)
        }
        return out.toList()
    }

    /** هل يوجد فاصل «…» بين فهرسين متتاليين في العرض؟ */
    fun hasGapBefore(indices: List<Int>, position: Int): Boolean =
        position > 0 && indices[position] - indices[position - 1] > 1
}
