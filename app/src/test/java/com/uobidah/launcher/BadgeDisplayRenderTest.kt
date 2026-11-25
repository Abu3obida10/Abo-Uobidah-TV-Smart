package com.uobidah.launcher
import android.graphics.*
import android.view.View
import android.widget.LinearLayout
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest

/** Display-slot proof with a locally generated test crest seeded in the disk cache (no network, test data). */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w1280dp-h720dp-hdpi")
class BadgeDisplayRenderTest {
    @Test fun badgeShowsSeededImage() {
        val ctx = org.robolectric.RuntimeEnvironment.getApplication()
        val url = "https://raw.githubusercontent.com/test/crest-a.png"
        val dir = File(ctx.filesDir, "uobidah_club_logos").apply { mkdirs() }
        val name = MessageDigest.getInstance("SHA-256").digest(url.toByteArray()).joinToString("") { "%02x".format(it.toInt() and 0xff) }
        val b = Bitmap.createBitmap(200, 200, Bitmap.Config.ARGB_8888)
        val c = Canvas(b); val p = Paint(Paint.ANTI_ALIAS_FLAG)
        p.color = Color.rgb(200, 30, 40); c.drawCircle(100f, 100f, 95f, p)
        p.color = Color.WHITE; c.drawRect(85f, 30f, 115f, 170f, p); c.drawRect(30f, 85f, 170f, 115f, p)
        FileOutputStream(File(dir, name + ".img")).use { b.compress(Bitmap.CompressFormat.PNG, 100, it) }
        val row = LinearLayout(ctx); row.setBackgroundColor(Color.rgb(20, 28, 44)); row.setPadding(40, 40, 40, 40)
        val withBadge = TeamBadgeUi.create(ctx, "الأهلي", url, 96, Color.CYAN)
        val without = TeamBadgeUi.create(ctx, "بيراميدز", "", 96, Color.CYAN)
        row.addView(withBadge); row.addView(without)
        val frame = withBadge as android.widget.FrameLayout
        for (k in 0 until 20) { Thread.sleep(100); org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle() }
        Thread.sleep(700); org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
        row.measure(View.MeasureSpec.makeMeasureSpec(800, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(300, View.MeasureSpec.EXACTLY))
        row.layout(0, 0, 800, 300)
        val out = Bitmap.createBitmap(800, 300, Bitmap.Config.ARGB_8888); row.draw(Canvas(out))
        File("/tmp/abo/shots6").mkdirs()
        FileOutputStream("/tmp/abo/shots6/badge_display.png").use { out.compress(Bitmap.CompressFormat.PNG, 100, it) }
        val img = frame.getChildAt(1) as android.widget.ImageView
        File("/tmp/abo/shots6/badge_display.txt").writeText("imageDrawable=${img.drawable != null} alpha=${img.alpha} fallbackVisibility=${frame.getChildAt(0).visibility}")
        assertNotNull(img.drawable); assertEquals(View.GONE, frame.getChildAt(0).visibility)
    }
}
