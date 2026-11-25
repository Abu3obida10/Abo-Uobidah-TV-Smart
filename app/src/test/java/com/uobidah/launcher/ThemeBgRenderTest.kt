package com.uobidah.launcher

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Rect
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ThemeBgRenderTest {
    @Test fun rasterCacheIsReusedUntilBoundsChange() {
        val theme = Themes.get(0)
        val drawable = ThemeBg(theme, true)
        val originalBounds = Rect(10, 20, 1930, 1100)
        drawable.bounds = originalBounds
        val bitmap = Bitmap.createBitmap(2000, 1200, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        drawable.draw(canvas)
        repeat(60) { drawable.draw(canvas) }
        assertEquals("drawing must not mutate Drawable bounds", originalBounds, drawable.bounds)
        assertEquals(1, drawable.rasterBuildCount)
        assertTrue(drawable.rasterReuseCount >= 60)
        assertTrue(drawable.rasterBuildNanos > 0L)
        assertTrue(drawable.rasterBlitNanos > 0L)
        drawable.bounds = Rect(20, 30, 1940, 1110)
        drawable.draw(canvas)
        assertEquals("same dimensions at a new position should reuse the cached raster", 1, drawable.rasterBuildCount)
        drawable.bounds = Rect(0, 0, 1280, 720)
        drawable.draw(canvas)
        assertEquals(2, drawable.rasterBuildCount)

        // At half scale, 1920x1080 and 1921x1081 can quantize to the same
        // 960x540 raster. The source-size key must still force a fresh render.
        drawable.bounds = Rect(0, 0, 1920, 1080)
        drawable.draw(canvas)
        assertEquals(3, drawable.rasterBuildCount)
        val cacheField = ThemeBg::class.java.getDeclaredField("cache").apply { isAccessible = true }
        val cachedBeforeResize = cacheField.get(drawable) as Bitmap
        drawable.bounds = Rect(0, 0, 1921, 1081)
        drawable.draw(canvas)
        assertEquals(4, drawable.rasterBuildCount)
        assertTrue("source-size invalidation must recycle the old bitmap", cachedBeforeResize.isRecycled)
        val report = "draws=65\nbuilds=${drawable.rasterBuildCount}\nhits=${drawable.rasterReuseCount}\nbuildNanos=${drawable.rasterBuildNanos}\nblitNanos=${drawable.rasterBlitNanos}\n"
        val out = java.io.File("/tmp/abo/theme_bg_benchmark.txt"); out.parentFile?.mkdirs(); out.writeText(report)
        bitmap.recycle()
    }
}
