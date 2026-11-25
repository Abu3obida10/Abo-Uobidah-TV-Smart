package com.uobidah.launcher.core
import org.junit.Assert.assertTrue
import org.junit.Test
class HeaderProbeTest {
    @Test fun headerWithAttributesAccepted() {
        assertTrue(M3uParser.hasM3uHeader("#EXTM3U x-tvg-url=\"http://example.invalid/epg.xml\"\n#EXTINF:-1,A\nhttp://example.invalid/a.m3u8\n"))
    }
    @Test fun headerlessListWithEntriesParses() {
        assertTrue(M3uParser.parse("#EXTINF:-1,A\nhttp://example.invalid/a.m3u8\n").isNotEmpty())
    }
}
