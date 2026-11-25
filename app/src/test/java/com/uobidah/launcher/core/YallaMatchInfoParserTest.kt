package com.uobidah.launcher.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class YallaMatchInfoParserTest {
    // Fixture: excerpt of a public ysscores.com match page, captured 2026-11-25 (test data, not a current result).
    private val html = javaClass.classLoader!!.getResourceAsStream("yalla_match_info_fixture.html")!!.readBytes().toString(Charsets.UTF_8)

    @Test fun readsRealInfoRowsOnly() {
        val m = YallaMatchInfoParser.parse(html)
        assertEquals("الجولة 5", m["الجولة"])
        assertTrue(m["ملعب المباراة"].orEmpty().contains("برنابيو"))
        assertEquals("بي إن سبورت 1", m["القناة"])
        assertEquals("عامر الخوذيري", m["المعلق"])
        assertEquals("إسبن إسكاس", m["الحكم"])
    }
    @Test fun noMoreButtonOrCrewInValues() {
        val m = YallaMatchInfoParser.parse(html)
        for ((_, v) in m) { assertFalse(v, v.contains("المزيد")); assertFalse(v, v.contains("<")) }
        assertFalse(m["الحكم"].orEmpty().contains("مساعد"))
    }
    @Test fun missingOrEmptyPageGivesNothing() {
        assertTrue(YallaMatchInfoParser.parse("").isEmpty())
        assertTrue(YallaMatchInfoParser.parse("<html>لا شيء</html>").isEmpty())
    }
}
