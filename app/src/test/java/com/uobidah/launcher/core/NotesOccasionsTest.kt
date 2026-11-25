package com.uobidah.launcher.core

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NotesOccasionsTest {
    @Test fun occasionsAreFutureOrTodayOrderedByDateAndDaysAreConsistent() {
        val today = LocalDate.of(2026, 11, 25)
        val items = Occasions.upcoming(today)
        assertEquals(3, items.size)
        items.forEach {
            assertTrue(!it.date.isBefore(today))
            assertEquals(java.time.temporal.ChronoUnit.DAYS.between(today, it.date), it.daysLeft)
        }
        // أقرب رمضان بعد نوفمبر 2026 يقع في فبراير 2027 تقريبًا (تقدير أم القرى)، لا في 2026.
        val ramadan = items.first { it.nameAr == "رمضان" }
        assertTrue(ramadan.date.year == 2027 && ramadan.date.monthValue in 1..3)
        assertTrue(items.first { it.nameAr == "عيد الفطر" }.date.isAfter(ramadan.date))
    }
    @Test fun occasionsDisclaimerStatesDatesAreEstimates() = assertTrue(Occasions.DISCLAIMER_AR.contains("تقديرية"))
    @Test fun notesRoundTrip() {
        val l = listOf(Note("قائمة المشاهدة", false), Note("تجديد شاهد", true))
        assertEquals(l, Notes.decode(Notes.encode(l)))
    }
    @Test fun notesBadJsonIsEmpty() {
        assertEquals(emptyList<Note>(), Notes.decode("{oops"))
        assertEquals(emptyList<Note>(), Notes.decode(null))
        assertEquals(emptyList<Note>(), Notes.decode("""[{"t":"  ","d":true}]"""))
    }
    @Test fun addRejectsBlankAndFull() {
        assertNull(Notes.add(emptyList(), "   "))
        val full = (1..Notes.MAX_NOTES).map { Note("n$it") }
        assertNull(Notes.add(full, "زيادة"))
        assertEquals(1, Notes.add(emptyList(), "  أ   ب ")!!.size)
        assertEquals("أ ب", Notes.add(emptyList(), "  أ   ب ")!![0].text)
    }
    @Test fun textIsCapped() = assertEquals(Notes.MAX_TEXT, Notes.clean("x".repeat(500)).length)
    @Test fun toggleRenameRemoveIgnoreBadIndex() {
        val l = listOf(Note("a"), Note("b"))
        assertTrue(Notes.toggle(l, 1)[1].done)
        assertEquals(l, Notes.toggle(l, 5))
        assertEquals("c", Notes.rename(l, 0, "c")[0].text)
        assertEquals(l, Notes.rename(l, 0, " "))
        assertEquals(listOf(Note("b")), Notes.remove(l, 0))
        assertEquals(l, Notes.remove(l, -1))
    }
    @Test fun visibleUndoneFirstKeepsOriginalIndex() {
        val l = listOf(Note("a", true), Note("b"), Note("c", true), Note("d"))
        assertEquals(listOf(1, 3, 0), Notes.visible(l, 3).map { it.first })
    }

    @Test fun occasionsAreFutureAndOrderedByNameDefs() {
        val today = LocalDate.of(2026, 11, 25)
        val items = Occasions.upcoming(today)
        assertEquals(listOf("رمضان", "عيد الفطر", "عيد الأضحى"), items.map { it.nameAr })
        assertTrue(items.all { !it.date.isBefore(today) && it.daysLeft >= 0 })
        // رمضان 1448 هـ يقع في 2027 ميلادي
        assertEquals(2027, items[0].date.year)
        assertTrue(items[0].date.isBefore(items[1].date) && items[1].date.isBefore(items[2].date))
    }
    @Test fun occasionsRollToNextHijriYearAfterEid() {
        val items = Occasions.upcoming(LocalDate.of(2027, 6, 1)) // بعد عيد الأضحى 1447 → الأحداث للسنة التالية
        assertTrue(items.all { it.date.isAfter(LocalDate.of(2027, 6, 1)) })
    }
}
