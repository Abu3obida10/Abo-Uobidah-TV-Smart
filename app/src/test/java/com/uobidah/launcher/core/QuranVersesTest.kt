package com.uobidah.launcher.core

import org.junit.Assert.assertEquals
import org.junit.Test

class QuranVersesTest {
    @Test fun prayerVerseIsVerbatimUthmaniText() {
        // مقارنة بالنص المنسوخ من المصدر المرجعي بالتشكيل الكامل (النساء 4:103، آخر الآية).
        val expected = "إِنَّ ٱلصَّلَوٰةَ كَانَتْ عَلَى ٱلْمُؤْمِنِينَ كِتَٰبًا مَّوْقُوتًا"
        assertEquals(expected, QuranVerses.PRAYER_TIMES.text)
        assertEquals(4, QuranVerses.PRAYER_TIMES.surahNumber)
        assertEquals(103, QuranVerses.PRAYER_TIMES.ayah)
    }

    @Test fun referenceUsesIndicDigits() {
        assertEquals("النساء ١٠٣", QuranVerses.reference(QuranVerses.PRAYER_TIMES))
    }
}
