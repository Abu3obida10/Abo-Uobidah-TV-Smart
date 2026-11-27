package com.uobidah.launcher

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@org.junit.runner.RunWith(org.robolectric.RobolectricTestRunner::class)
@org.robolectric.annotation.Config(sdk = [34])
class WallpapersTest {
    @Test fun parsesCommonsImageUrlsWithoutNetwork() {
        val json = """{"query":{"pages":{"1":{"imageinfo":[{"thumburl":"https://upload.wikimedia.org/a.jpg?x=1"}]},"2":{"imageinfo":[{"url":"https://upload.wikimedia.org/b.png"}]},"3":{"imageinfo":[{"url":"https://example.com/not-an-image.txt"}]}}}}}"""
        val urls = Wallpapers.parseCommonsImageUrls(json)
        assertEquals(2, urls.size)
        assertTrue(urls[0].endsWith("a.jpg?x=1"))
        assertTrue(urls[1].endsWith("b.png"))
    }

    @Test fun acceptsCommonsThumbHostWhichServesAllApiThumbs() {
        // رد Commons الحقيقي يضع المصغّرات على thumb.wikimedia.org؛ رفضه كان يصفّر كل نتائج البحث.
        val json = """{"query":{"pages":{"1":{"imageinfo":[{"thumburl":"https://thumb.wikimedia.org/wikipedia/commons/thumb/9/97/File.jpeg/1920px-File.jpeg","url":"https://upload.wikimedia.org/wikipedia/commons/9/97/File.jpeg"}]},"2":{"imageinfo":[{"thumburl":"https://evil.example.org/x.jpg"}]}}}}}"""
        val urls = Wallpapers.parseCommonsImageUrls(json)
        assertEquals(1, urls.size)
        assertTrue(urls[0].startsWith("https://thumb.wikimedia.org/"))
    }
}
