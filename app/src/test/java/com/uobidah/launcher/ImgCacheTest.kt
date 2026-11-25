package com.uobidah.launcher

import android.graphics.Bitmap
import android.graphics.Canvas
import android.widget.ImageView
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[34])
class ImgCacheTest {
    /** الاستدعاء الراجع يُرسل لخيط الواجهة؛ نفرغ الـLooper أثناء الانتظار بدل حجب الخيط الوحيد. */
    private fun awaitIdle(l: CountDownLatch, ms: Long): Boolean {
        val end = System.currentTimeMillis() + ms
        while (System.currentTimeMillis() < end) {
            org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
            if (l.await(20, TimeUnit.MILLISECONDS)) return true
        }
        return false
    }
    @Test fun repeatedThemeBackgroundUsesMemoryCache() {
        val ctx = RuntimeEnvironment.getApplication()
        val dir = File(ctx.cacheDir, "img-test").apply { mkdirs() }
        val file = File(dir, "bg.png")
        val bmp = Bitmap.createBitmap(320, 180, Bitmap.Config.ARGB_8888)
        Canvas(bmp).drawColor(0xFF123456.toInt())
        FileOutputStream(file).use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        Img.clearMemoryCache()
        val before = Img.fileCacheStats().first
        val v1 = ImageView(ctx)
        val l1 = CountDownLatch(1)
        Img.loadFile(file, v1, 320, 180) { l1.countDown() }
        assertTrue(awaitIdle(l1, 3000L))
        val afterFirst = Img.fileCacheStats().first
        val v2 = ImageView(ctx)
        val l2 = CountDownLatch(1)
        Img.loadFile(file, v2, 320, 180) { l2.countDown() }
        assertTrue(awaitIdle(l2, 1000))
        val stats = Img.fileCacheStats()
        assertTrue(stats.first == afterFirst && afterFirst >= before + 1)
        assertTrue(stats.second >= 1)
    }
}
