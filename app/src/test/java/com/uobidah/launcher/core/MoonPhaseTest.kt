package com.uobidah.launcher.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneOffset

class MoonPhaseTest {
    private fun ms(y: Int, m: Int, d: Int) = LocalDateTime.of(y, m, d, 12, 0).toInstant(ZoneOffset.UTC).toEpochMilli()

    // القيم المرجعية حُسبت بنفس الصيغة في بايثون؛ المحاق 9 نوفمبر 2026 والبدر 24 نوفمبر 2026 (دقة نحو نصف يوم).
    @Test fun newMoonNovember2026IsDark() {
        val age = MoonPhase.ageDays(ms(2026, 11, 9))
        assertTrue("age=$age", MoonPhase.illuminationPercent(age) <= 5)
        assertEquals("محاق", MoonPhase.nameAr(age))
    }
    @Test fun fullMoonNovember2026IsBright() {
        val age = MoonPhase.ageDays(ms(2026, 11, 24))
        assertTrue("age=$age", MoonPhase.illuminationPercent(age) >= 97)
        assertEquals("بدر", MoonPhase.nameAr(age))
    }
    @Test fun ageStaysInsideOneSynodicMonth() {
        for (day in 0 until 400) {
            val a = MoonPhase.ageDays(ms(2026, 1, 1) + day * 86_400_000L)
            assertTrue(a >= 0.0 && a < MoonPhase.SYNODIC_DAYS)
        }
    }
}
