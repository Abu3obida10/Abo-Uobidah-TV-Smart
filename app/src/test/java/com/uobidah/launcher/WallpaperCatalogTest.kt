package com.uobidah.launcher

import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** كتالوج الخلفيات المضمَّن: يقرأ الملف الحقيقي من assets، بلا شبكة (الجلب مُحاكى). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class WallpaperCatalogTest {
    private val ctx get() = org.robolectric.RuntimeEnvironment.getApplication()

    @Test fun bundledFileLoadsFiveCategories() {
        val ids = WallpaperCatalog.load(ctx).map { it.id }
        assertEquals(listOf("islamic", "nature", "al_ahly", "real_madrid", "man_united"), ids)
    }

    @Test fun placeholderLinksAreDroppedAndRealOnesKept() {
        val ahly = WallpaperCatalog.load(ctx).first { it.id == "al_ahly" }
        assertTrue("روابط your-github-or-r2-link.com لازم تتشال", ahly.fallbacks.none { it.contains("your-") })
        assertEquals(3, ahly.fallbacks.size)
        assertTrue("الأهلي على صور ويكيميديا الموثّقة (وولهيفن لا يملك نتائج للأهلي)",
            ahly.fallbacks.all { it.startsWith("https://upload.wikimedia.org/") })
        val madrid = WallpaperCatalog.load(ctx).first { it.id == "real_madrid" }
        assertEquals(3, madrid.fallbacks.size)
        assertTrue(madrid.fallbacks.all { it.startsWith("https://w.wallhaven.cc/full/") })
    }

    @Test fun rejectsForeignHostsAndInsecureLinks() {
        assertFalse(WallpaperCatalog.safeImage("http://w.wallhaven.cc/full/aa/wallhaven-aaaaaa.jpg"))
        assertFalse(WallpaperCatalog.safeImage("https://evil.example.org/full/aa/wallhaven-aaaaaa.jpg"))
        assertFalse(WallpaperCatalog.safeImage("https://w.wallhaven.cc.evil.org/a.jpg"))
        assertFalse(WallpaperCatalog.safeImage("https://upload.wikimedia.org.evil.org/a.jpg"))
        assertTrue(WallpaperCatalog.safeImage("https://upload.wikimedia.org/wikipedia/commons/7/7e/Al_Ahly_WE_stadium.jpg"))
        assertFalse(WallpaperCatalog.safeApi("https://wallhaven.cc/other/path"))
        assertTrue(WallpaperCatalog.safeApi("https://wallhaven.cc/api/v1/search?q=nature"))
    }

    @Test fun parsesWallhavenResponseAndSkipsUntrustedPaths() {
        val json = """{"data":[{"path":"https://w.wallhaven.cc/full/ab/wallhaven-abcdef.jpg"},
            {"path":"https://other.site/x.jpg"},{"path":"https://w.wallhaven.cc/full/ab/wallhaven-abcdef.jpg"},
            {"path":"https://w.wallhaven.cc/full/cd/wallhaven-cdefgh.png"}]}"""
        assertEquals(2, WallpaperCatalog.parseWallhavenResponse(json).size)
    }

    @Test fun apiResultWinsThenFallbackOnFailure() {
        val api = """{"data":[{"path":"https://w.wallhaven.cc/full/ab/wallhaven-abcdef.jpg"}]}"""
        val ok = WallpaperCatalog.candidatesForScope(ctx, "club_madrid") { api }
        assertEquals(listOf("https://w.wallhaven.cc/full/ab/wallhaven-abcdef.jpg"), ok)
        val fb = WallpaperCatalog.candidatesForScope(ctx, "club_madrid") { throw java.io.IOException("offline") }
        assertEquals(3, fb.size)
        val ahly = WallpaperCatalog.candidatesForScope(ctx, "club_ahly") { throw java.io.IOException("offline") }
        assertEquals("الأهلي بلا API صالح (وولهيفن بلا نتائج) لكن باحتياطي ويكيميديا", 3, ahly.size)
    }

    @Test fun scopeMapsToClubCategory() {
        assertEquals(listOf("al_ahly"), WallpaperCatalog.idsForScope("club_ahly"))
        assertEquals(listOf("man_united"), WallpaperCatalog.idsForScope("club_united"))
        assertTrue("islamic" in WallpaperCatalog.idsForScope("general"))
    }
}
