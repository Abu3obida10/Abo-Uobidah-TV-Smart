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
}
