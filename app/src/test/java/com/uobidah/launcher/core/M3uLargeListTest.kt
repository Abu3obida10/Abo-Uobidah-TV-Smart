package com.uobidah.launcher.core

import org.junit.Assert.*
import org.junit.Test

/** SYNTHETIC generated data (no real playlist): scale behavior above the old 100k limit. */
class M3uLargeListTest {
    private fun gen(n: Int): String {
        val sb = StringBuilder("#EXTM3U x-tvg-url=\"https://example.invalid/epg.xml\"\n")
        for (i in 0 until n) sb.append("#EXTINF:-1 tvg-id=\"id$i\" group-title=\"G${i % 143}\",Channel $i\nhttps://example.invalid/stream/$i.m3u8\n")
        return sb.toString()
    }
    @Test fun parsesOneHundredFortyThousandEntriesWhenLimitAllows() {
        val list = M3uParser.parse(java.io.StringReader(gen(140_000)), 250_000)
        assertEquals(140_000, list.size)
        assertEquals(143, list.map { it.group }.toSet().size)
    }
    @Test fun defaultLimitStillRejectsAbove100k() {
        try { M3uParser.parse(java.io.StringReader(gen(100_050))); fail("expected limit") } catch (e: IllegalArgumentException) { assertTrue(e.message!!.contains("too many")) }
    }
}
