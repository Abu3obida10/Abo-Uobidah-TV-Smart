package com.uobidah.launcher.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CalendarGridTest {
    @Test fun november2026StartsOnSundayAndHasFiveWeeks() {
        val w = CalendarGrid.weeks(2026, 11) // 1 نوفمبر 2026 = أحد
        assertEquals(5, w.size)
        assertEquals(1, w[0][0]); assertEquals(30, w[4][1]); assertNull(w[4][2])
        w.forEach { assertEquals(7, it.size) }
    }
    @Test fun leapFebruaryAndLeadingBlanks() {
        val w = CalendarGrid.weeks(2024, 2) // 1 فبراير 2024 = خميس
        assertNull(w[0][0]); assertEquals(1, w[0][4])
        assertEquals(29, w.flatten().filterNotNull().max())
    }
    @Test fun fourFiveAndSixRowMonthsKeepEveryDay() {
        val four = CalendarGrid.weeks(2026, 2)   // 1 فبراير 2026 = أحد، 28 يومًا → 4 صفوف
        val five = CalendarGrid.weeks(2026, 11)  // 5 صفوف
        val six = CalendarGrid.weeks(2026, 8)    // 1 أغسطس 2026 = سبت، 31 يومًا → 6 صفوف
        assertEquals(4, four.size); assertEquals(5, five.size); assertEquals(6, six.size)
        assertEquals((1..28).toList(), four.flatten().filterNotNull())
        assertEquals((1..30).toList(), five.flatten().filterNotNull())
        assertEquals((1..31).toList(), six.flatten().filterNotNull())
        // 1 أغسطس 2026 = سبت (عمود 6)، فالصف السادس يبدأ بـ30 ثم 31 ثم فراغات (محقَّق بـcalendar في بايثون)
        assertEquals(30, six[5][0]); assertEquals(31, six[5][1])
        assertNull(six[5][2]); assertEquals(7, six[5].size)
    }
    @Test fun todayAppearsExactlyOnceInItsWeek() {
        // 25 نوفمبر 2026 (أربعاء) في الصف الرابع، العمود الرابع (أحد=0)
        val w = CalendarGrid.weeks(2026, 11)
        val hits = w.withIndex().flatMap { (r, row) -> row.withIndex().filter { it.value == 25 }.map { r to it.index } }
        assertEquals(listOf(3 to 3), hits)
    }
    @Test fun noorDuaOpeningIsPinned() {
        assertEquals("اللَّهُمَّ اجْعَلْ لِي فِي قَلْبِي نُورًا وَفِي لِسَانِي نُورًا وَفِي سَمْعِي نُورًا وَفِي بَصَرِي نُورًا", Hadiths.NOOR_DUA_OPENING.text)
    }
}
