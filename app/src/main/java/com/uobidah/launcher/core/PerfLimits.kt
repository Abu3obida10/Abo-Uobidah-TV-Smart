package com.uobidah.launcher.core

/** قيم الأداء المسموحة؛ أي قيمة محفوظة خارجها (نسخة قديمة/ملف ملغوم) تُرجَع إلى أقرب قيمة مسموحة. */
object PerfLimits {
    val POLL_SECONDS = listOf(10, 20, 30, 60)
    val MATCH_CHECK_SECONDS = listOf(120, 300, 600, 900)
    val MATCH_CACHE_MINUTES = listOf(10, 30, 60, 120)
    val FAVORITES_REFRESH_MINUTES = listOf(5, 10, 30, 60)

    fun nearest(value: Int, allowed: List<Int>): Int = allowed.minByOrNull { Math.abs(it - value) } ?: value
}
