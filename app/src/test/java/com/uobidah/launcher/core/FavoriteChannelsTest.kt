package com.uobidah.launcher.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FavoriteChannelsTest {
    @Test fun legacyTwoPartEntryStillReads() {
        val f = FavoriteChannels.parse("MBC 1||http://x.test/a.m3u8")!!
        assertEquals(FavoriteChannel("MBC 1", "http://x.test/a.m3u8", ""), f)
    }
    @Test fun roundTripWithLogo() {
        val c = FavoriteChannel("بي إن", "https://s.test/1.m3u8", "https://l.test/bein.png")
        assertEquals(c, FavoriteChannels.parse(FavoriteChannels.encode(c)))
    }
    @Test fun separatorInNameIsNeutralised() {
        val e = FavoriteChannels.encode(FavoriteChannel("A||B", "https://s.test/1", ""))
        assertEquals("A B||https://s.test/1", e)
    }
    @Test fun nonHttpLogoDropped() {
        assertEquals("", FavoriteChannels.cleanLogo("javascript:alert(1)"))
        assertEquals("", FavoriteChannels.cleanLogo("file:///sdcard/x.png"))
        assertEquals("", FavoriteChannels.cleanLogo("https://x/" + "a".repeat(600)))
    }
    @Test fun malformedEntriesSkipped() {
        assertNull(FavoriteChannels.parse("no separator"))
        assertNull(FavoriteChannels.parse("name||   "))
        assertEquals(1, FavoriteChannels.parseAll(listOf("bad", "n||https://u")).size)
    }
    @Test fun toggleAddsThenRemovesByUrl() {
        val c = FavoriteChannel("X", "https://u/1", "https://l/1.png")
        val (a, added) = FavoriteChannels.toggle(emptyList(), c)
        assertTrue(added && a.size == 1)
        val (b, added2) = FavoriteChannels.toggle(a, c.copy(name = "other name"))
        assertTrue(!added2 && b.isEmpty())
    }
    @Test fun backfillFillsOnlyMissingAndReportsNoChange() {
        val entries = listOf("A||https://u/1", "B||https://u/2||https://l/keep.png", "C||https://u/3")
        val out = FavoriteChannels.backfillLogos(entries, mapOf("https://u/1" to "https://l/a.png", "https://u/2" to "https://l/other.png"))!!
        assertEquals("A||https://u/1||https://l/a.png", out[0])
        assertEquals(entries[1], out[1])
        assertEquals(entries[2], out[2])
        assertNull(FavoriteChannels.backfillLogos(out, mapOf("https://u/1" to "https://l/zzz.png")))
    }
}
