package com.uobidah.launcher

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class WallpaperSearchTest {
    @Test fun commonsParserKeepsOnlyHttpsImageFilesAndDeduplicates() {
        val json = """{"query":{"pages":{
          "1":{"imageinfo":[{"thumburl":"https://upload.wikimedia.org/a.jpg?width=800"}]},
          "2":{"imageinfo":[{"thumburl":"http://upload.wikimedia.org/b.jpg"}]},
          "3":{"imageinfo":[{"thumburl":"https://upload.wikimedia.org/a.jpg?width=800"}]},
          "4":{"imageinfo":[{"thumburl":"https://upload.wikimedia.org/c.pdf"}]},
          "5":{"imageinfo":[{"url":"https://upload.wikimedia.org/d.webp"}]},
          "6":{"imageinfo":[{"url":"https://malicious.example/evil.png"}]}
        }}}"""
        val urls = Wallpapers.parseCommonsImageUrls(json)
        assertEquals(2, urls.size)
        assertTrue(urls.any { it.startsWith("https://") && it.contains("a.jpg") })
        assertTrue(urls.any { it.startsWith("https://") && it.endsWith("d.webp") })
    }

    @Test fun commonsParserReturnsEmptyForMalformedOrMissingPayload() {
        assertTrue(Wallpapers.parseCommonsImageUrls("{}").isEmpty())
        assertTrue(runCatching { Wallpapers.parseCommonsImageUrls("not-json") }.isFailure)
    }

    @Test fun pexelsParserKeepsOnlyPexelsHttpsImagesAndRecordsPhotographer() {
        val json = """{"photos":[
          {"photographer":"Sara A","src":{"large2x":"https://images.pexels.com/photos/1/a.jpeg?w=1880"}},
          {"photographer":"Dup","src":{"large2x":"https://images.pexels.com/photos/1/a.jpeg?w=1880"}},
          {"photographer":"Evil","src":{"large2x":"https://malicious.example/x.jpg"}},
          {"photographer":"Plain","src":{"large2x":"http://images.pexels.com/photos/2/b.jpeg"}},
          {"photographer":"","src":{"large2x":"https://images.pexels.com/photos/3/c.jpeg"}}
        ]}"""
        val urls = Wallpapers.parsePexelsImageUrls(json)
        assertEquals(2, urls.size)
        assertEquals("Sara A • Pexels", Wallpapers.creditFor(urls[0]))
        assertEquals("Pexels", Wallpapers.creditFor(urls[1]))
    }

    @Test fun pexelsSearchWithoutKeyDoesNothing() {
        assertTrue(Wallpapers.searchPexels("", "nature").isEmpty())
        assertTrue(Wallpapers.parsePexelsImageUrls("{}").isEmpty())
    }
}
