package com.uobidah.launcher.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.StringReader

class M3uParserTest {
    @Test fun acceptsBomCrlfArabicAndAttributesInDifferentOrder() {
        val raw = "\uFEFF" + listOf(
            "#EXTM3U",
            "#EXTINF:-1 tvg-id='news.ar' group-title='أخبار',قناة الأخبار",
            "https://cdn.example/live/news.m3u8"
        ).joinToString("\r\n")
        assertTrue(M3uParser.hasM3uHeader(raw))
        val item = M3uParser.parse(raw).single()
        assertEquals("قناة الأخبار", item.name)
        assertEquals("أخبار", item.group)
        assertEquals("news.ar", item.tvgId)
    }

    @Test fun commasInsideQuotedAttributesDoNotTruncateChannelName() {
        val raw = """#EXTM3U
#EXTINF:-1 tvg-name="Channel, HD" group-title="Sports, Live",Channel Name, Extra
https://cdn.example/sport.m3u8
"""
        val item = M3uParser.parse(raw).single()
        assertEquals("Channel Name, Extra", item.name)
        assertEquals("Sports, Live", item.group)
    }

    @Test fun skipsUnsafeSchemesAndDeduplicatesIdenticalRows() {
        val raw = """#EXTM3U
#EXTINF:-1,First
javascript:alert(1)
#EXTINF:-1,Good
https://cdn.example/a.m3u8
#EXTINF:-1,Good
https://cdn.example/a.m3u8
#EXTINF:-1,FTP
ftp://cdn.example/b.ts
"""
        val entries = M3uParser.parse(raw)
        assertEquals(1, entries.size)
        assertEquals("Good", entries.single().name)
    }

    @Test fun preservesDistinctChannelLabelsThatShareOneStreamUrl() {
        val raw = """#EXTM3U
#EXTINF:-1 tvg-id="news.en" group-title="News",News English
https://cdn.example/shared.m3u8
#EXTINF:-1 tvg-id="news.ar" group-title="أخبار",News Arabic
https://cdn.example/shared.m3u8
#EXTINF:-1 tvg-id="news.ar" group-title="أخبار",News Arabic
https://cdn.example/shared.m3u8
"""
        val parsed = M3uParser.parse(raw)
        assertEquals(2, parsed.size)
        assertEquals(setOf("News English", "News Arabic"), parsed.map { it.name }.toSet())
    }

    @Test fun uiIdentityPreservesDifferentChannelsThatReuseTheSameUrl() {
        val english = M3uEntry("News English", "https://cdn.example/shared.m3u8", "News", "news.en")
        val arabic = M3uEntry("News Arabic", "https://cdn.example/shared.m3u8", "أخبار", "news.ar")
        val exactDuplicate = M3uEntry("News Arabic", "https://cdn.example/shared.m3u8", "أخبار", "news.ar")
        val visible = listOf(english, arabic, exactDuplicate).distinctBy { M3uParser.identity(it) }
        assertEquals(2, visible.size)
        assertTrue(M3uParser.identity(english) != M3uParser.identity(arabic))
    }

    @Test fun identityDoesNotLowercaseCaseSensitiveUrlPaths() {
        val lowerPath = M3uEntry("Channel", "https://cdn.example/Live/News.m3u8", "News", "news")
        val upperPath = M3uEntry("Channel", "https://cdn.example/live/news.m3u8", "News", "news")
        assertTrue(M3uParser.identity(lowerPath) != M3uParser.identity(upperPath))
    }

    @Test fun supportsExtgrpAndUrlFallbackName() {
        val raw = """#EXTM3U
#EXTGRP:رياضة
https://cdn.example/channel-one.m3u8?token=x
"""
        val item = M3uParser.parse(raw).single()
        assertEquals("channel-one.m3u8", item.name)
        assertEquals("رياضة", item.group)
    }

    @Test fun enforcesEntryLimit() {
        val raw = """#EXTM3U
#EXTINF:-1,A
https://a.example/1
#EXTINF:-1,B
https://a.example/2
"""
        assertTrue(runCatching { M3uParser.parse(raw, 1) }.isFailure)
    }


    @Test fun streamsPlaylistsLargerThanTheFormerEightMegabyteLimit() {
        val raw = StringBuilder(10 * 1024 * 1024).append("#EXTM3U\n")
        val longPath = "x".repeat(720)
        repeat(12_000) { index ->
            raw.append("#EXTINF:-1,Channel ").append(index).append('\n')
                .append("https://cdn.example/").append(index).append('/').append(longPath).append(".m3u8\n")
        }
        assertTrue(raw.length > 8 * 1024 * 1024)
        val entries = M3uParser.parse(StringReader(raw.toString()))
        assertEquals(12_000, entries.size)
    }


    @Test fun acceptsExtm3uHeaderWithEpgAttributesOnTheFirstLine() {
        val raw = """#EXTM3U url-tvg="https://epg.example/guide.xml" x-tvg-url="https://epg.example/extra.xml"
#EXTINF:-1 tvg-id="news" group-title="News",News Channel
https://cdn.example/news.m3u8
"""
        assertTrue(M3uParser.hasM3uHeader(raw))
        assertEquals("News Channel", M3uParser.parse(raw).single().name)
    }

    @Test fun acceptsCommaAndSemicolonMetadataAfterExtm3uHeader() {
        val raw = """#EXTM3U, url-tvg="https://epg.example/guide.xml"; x-tvg-url="https://epg.example/extra.xml"|refresh=3600
#EXTINF:-1,News Channel
https://cdn.example/news.m3u8
"""
        assertTrue(M3uParser.hasM3uHeader(raw))
        assertEquals("News Channel", M3uParser.parse(raw).single().name)
    }

    @Test fun acceptsExtm3uHeaderWithTabSeparatedMetadataButRejectsSuffixWithoutDelimiter() {
        assertTrue(M3uParser.hasM3uHeader("#EXTM3U\turl-tvg=guide.xml\n#EXTINF:-1,A\nhttps://a.example/a.m3u8"))
        assertFalse(M3uParser.hasM3uHeader("#EXTM3Uversion=2\n#EXTINF:-1,A\nhttps://a.example/a.m3u8"))
    }

    @Test fun rejectsStringsThatOnlyStartLikeAnExtm3uHeader() {
        assertFalse(M3uParser.hasM3uHeader("#EXTM3Ubroken url-tvg=bad"))
        assertFalse(M3uParser.hasM3uHeader("#EXTM3Ufoo"))
    }

    @Test fun overlongMetadataAndUrlLinesAreDrainedWithoutBreakingFollowingEntries() {
        val raw = buildString {
            append("#EXTM3U\n")
            append("#EXTINF:-1,This entry must be discarded\n")
            append("#").append("x".repeat(20_000)).append('\n')
            append("https://cdn.example/" ).append("p".repeat(9_000)).append(".m3u8\n")
            append("#EXTINF:-1,Valid Channel\n")
            append("https://cdn.example/valid.m3u8\n")
        }
        val parsed = M3uParser.parse(StringReader(raw))
        assertEquals(1, parsed.size)
        assertEquals("Valid Channel", parsed.single().name)
        assertTrue(M3uParser.hasM3uHeader(raw))
    }

    @Test fun acceptsCarriageReturnOnlyLineSeparators() {
        val raw = "#EXTM3U\r#EXTINF:-1,Retro\rhttps://cdn.example/retro.m3u8\r"
        assertTrue(M3uParser.hasM3uHeader(raw))
        assertEquals("Retro", M3uParser.parse(raw).single().name)
    }

    @Test fun headerMustBeTheFirstNonBlankLine() {
        assertFalse(M3uParser.hasM3uHeader("<html>not a playlist</html>"))
        assertTrue(M3uParser.hasM3uHeader("\n\uFEFF#EXTM3U\n"))
    }
}

class M3uLogoTest {
    @Test fun tvgLogoIsParsedAndValidated() {
        val text = "#EXTM3U\n#EXTINF:-1 tvg-id=\"a\" tvg-logo=\"https://l.test/a.png\" group-title=\"G\",Chan A\nhttps://s.test/a.m3u8\n" +
            "#EXTINF:-1 tvg-logo=\"javascript:x\",Chan B\nhttps://s.test/b.m3u8\n" +
            "#EXTINF:-1,Chan C\nhttps://s.test/c.m3u8\n"
        val out = M3uParser.parse(java.io.StringReader(text), 100)
        assertEquals(listOf("https://l.test/a.png", "", ""), out.map { it.logo })
    }
}
