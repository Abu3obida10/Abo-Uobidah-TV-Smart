package com.uobidah.launcher.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AdhkarQuranTest {
    @Test fun listsNotEmptyAndRepeatsPositive() {
        assertTrue(AdhkarData.morning.size >= 10 && AdhkarData.evening.size >= 10)
        assertTrue((AdhkarData.morning + AdhkarData.evening).all { it.repeat >= 1 && it.text.isNotBlank() && it.source.isNotBlank() })
    }
    @Test fun morningEveningDiffer() = assertFalse(AdhkarData.morning[4].text == AdhkarData.evening[4].text)
    @Test fun hourSplit() { assertTrue(AdhkarData.isMorningHour(6)); assertFalse(AdhkarData.isMorningHour(15)); assertFalse(AdhkarData.isMorningHour(22)) }
    @Test fun arabicDigits() = assertEquals("١٢٣", AdhkarData.arabicDigits(123))
    @Test fun countsRoundTrip() = assertEquals("1,0,3", AdhkarData.encodeCounts(AdhkarData.decodeCounts("1,0,3", 3)))
    @Test fun countsHandleBadInput() {
        assertEquals("0,0", AdhkarData.encodeCounts(AdhkarData.decodeCounts("x,-4", 2)))
        assertEquals(4, AdhkarData.decodeCounts(null, 4).size)
        assertEquals(2, AdhkarData.decodeCounts("1,2,3,4", 2).size)
    }
    @Test fun audioUrlPadsNumber() = assertTrue(QuranText.audioUrl(7).endsWith("/007.mp3"))
    @Test fun parseSurahs() {
        val j = """{"data":[{"number":2,"name":"ب","numberOfAyahs":286,"revelationType":"Medinan"},{"number":1,"name":"أ","numberOfAyahs":7,"revelationType":"Meccan"}]}"""
        val l = QuranText.parseSurahs(j)
        assertEquals(listOf(1, 2), l.map { it.number })
        assertTrue(l[0].meccan); assertFalse(l[1].meccan)
    }
    @Test fun parseAyahsStripsBasmalaOnlyOutsideFatihaAndTawba() {
        val b = "بِسْمِ ٱللَّهِ ٱلرَّحْمَـٰنِ ٱلرَّحِيمِ "
        val j2 = """{"data":{"ayahs":[{"numberInSurah":1,"text":"${b}الٓمٓ"},{"numberInSurah":2,"text":"ذَٰلِكَ"}]}}"""
        assertEquals("الٓمٓ", QuranText.parseAyahs(j2, 2)[0].text)
        val j1 = """{"data":{"ayahs":[{"numberInSurah":1,"text":"${b.trim()}"}]}}"""
        assertEquals(b.trim(), QuranText.parseAyahs(j1, 1)[0].text)
    }
    @Test fun malformedJsonDoesNotInventData() {
        assertTrue(QuranText.parseSurahs("""{"data":5}""").isEmpty())
        assertTrue(QuranText.parseAyahs("""{"x":1}""", 3).isEmpty())
    }
}
