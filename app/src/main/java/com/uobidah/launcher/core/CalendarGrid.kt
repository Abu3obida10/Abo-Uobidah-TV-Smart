package com.uobidah.launcher.core

import java.time.LocalDate

/** شبكة شهر ميلادي تبدأ بالأحد (مرجع R4). منطق بحت قابل للاختبار بلا Android. */
object CalendarGrid {
    val monthsAr = listOf("يناير", "فبراير", "مارس", "أبريل", "مايو", "يونيو", "يوليو", "أغسطس", "سبتمبر", "أكتوبر", "نوفمبر", "ديسمبر")
    /** رؤوس الأيام بدءًا من الأحد. */
    val weekdayInitialsAr = listOf("ح", "ن", "ث", "ر", "خ", "ج", "س")

    /** أسابيع الشهر؛ الخلايا الفارغة قبل اليوم الأول وبعد الأخير null. كل أسبوع 7 خلايا. */
    fun weeks(year: Int, month: Int): List<List<Int?>> {
        val first = LocalDate.of(year, month, 1)
        val lead = first.dayOfWeek.value % 7 // الأحد=0 … السبت=6
        val days = first.lengthOfMonth()
        val cells = ArrayList<Int?>()
        repeat(lead) { cells.add(null) }
        for (d in 1..days) cells.add(d)
        while (cells.size % 7 != 0) cells.add(null)
        return cells.chunked(7)
    }
}
