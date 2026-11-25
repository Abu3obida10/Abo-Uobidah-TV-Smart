package com.uobidah.launcher.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class QiblaFormStandingsTest {
    @Test fun qiblaCairoIsSouthEast() {
        val b = Qibla.bearing(30.0444, 31.2357)!!
        assertTrue("cairo bearing $b", b in 130.0..140.0)
        assertEquals("جنوب شرق", Qibla.compassNameAr(b))
    }
    @Test fun qiblaLondonIsSouthEast() {
        val b = Qibla.bearing(51.5074, -0.1278)!!
        assertTrue("london bearing $b", b in 115.0..125.0)
    }
    @Test fun qiblaInvalidInput() {
        assertNull(Qibla.bearing(Double.NaN, 10.0))
        assertNull(Qibla.bearing(95.0, 10.0))
        assertNull(Qibla.bearing(Qibla.KAABA_LAT, Qibla.KAABA_LON))
    }
    @Test fun qiblaLabelUsesArabicDigits() {
        val l = Qibla.label(30.0444, 31.2357)!!
        assertTrue(l, l.startsWith("القبلة ١") && l.contains("°"))
    }

    @Test fun windowKeepsTopAndFavoriteNeighbors() {
        // فريقك في الترتيب 18 (فهرس 17) من 20 → 0,1,2 ثم 16,17,18
        assertEquals(listOf(0, 1, 2, 16, 17, 18), StandingsWindow.indices(20, { it == 17 }))
    }
    @Test fun windowMergesWhenFavoriteIsNearTop() {
        assertEquals(listOf(0, 1, 2, 3), StandingsWindow.indices(20, { it == 2 }))
    }
    @Test fun windowNoFavoriteJustTop() = assertEquals(listOf(0, 1, 2), StandingsWindow.indices(20, { false }))
    @Test fun windowEmptyAndGap() {
        assertEquals(emptyList<Int>(), StandingsWindow.indices(0, { true }))
        val idx = StandingsWindow.indices(20, { it == 17 })
        assertTrue(StandingsWindow.hasGapBefore(idx, 3))
        assertTrue(!StandingsWindow.hasGapBefore(idx, 1))
    }

    private fun ev(date: String, done: Boolean, homeId: String, hs: Int, awayId: String, a: Int) =
        """{"competitions":[{"date":"$date","status":{"type":{"completed":$done}},"competitors":[
        {"team":{"id":"$homeId"},"score":{"value":$hs}},{"team":{"id":"$awayId"},"score":{"value":$a}}]}]}"""

    @Test fun formFromScores() {
        val json = """{"events":[
            ${ev("2026-11-01T15:00Z", true, "1", 2, "9", 0)},
            ${ev("2026-11-08T15:00Z", true, "9", 1, "1", 1)},
            ${ev("2026-11-15T15:00Z", true, "1", 0, "7", 3)},
            ${ev("2026-11-22T15:00Z", false, "1", 0, "5", 0)}
        ]}"""
        assertEquals(listOf(FormResult.W, FormResult.D, FormResult.L), TeamForm.parse(json, "1"))
    }
    @Test fun formKeepsLastFiveOnly() {
        val evs = (1..7).joinToString(",") { ev("2026-10-0${it}T15:00Z", true, "1", it, "2", 0) }
        assertEquals(5, TeamForm.parse("""{"events":[$evs]}""", "1").size)
    }
    @Test fun formBadJsonIsEmptyNotFake() {
        assertEquals(emptyList<FormResult>(), TeamForm.parse("not json", "1"))
        assertEquals(emptyList<FormResult>(), TeamForm.parse("""{"events":[]}""", "1"))
    }
    @Test fun qiblaBearingsMatchIndependentReferenceCities() {
        // قيم حسبتها بمعادلة الدائرة العظمى خارج التطبيق وتتفق مع القيم المنشورة (من الشمال الحقيقي).
        assertEquals(58.5, Qibla.bearing(40.7128, -74.0060)!!, 0.5)   // نيويورك
        assertEquals(295.2, Qibla.bearing(-6.2088, 106.8456)!!, 0.5)  // جاكرتا
        assertEquals(277.5, Qibla.bearing(-33.8688, 151.2093)!!, 0.5) // سيدني
        assertEquals(243.8, Qibla.bearing(24.7136, 46.6753)!!, 0.5)   // الرياض
    }
}
