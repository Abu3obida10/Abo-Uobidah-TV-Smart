package com.uobidah.launcher
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import android.view.ViewGroup
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.io.FileOutputStream

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w1920dp-h1080dp-xhdpi")
class IslandRenderTest {
    @Test fun renderAllStyles() {
        val ctx = RuntimeEnvironment.getApplication()
        val prefs = Prefs(ctx)
        val out = File("/tmp/abo/shots2"); out.mkdirs()
        val log = StringBuilder()
        val samples = listOf(
            Triple("Goal! Al Ahly", "Al Ahly 2 - 1 Pyramids", "G"),
            Triple("هدف للأهلي في مباراة القمة الكبيرة اليوم", "الأهلي ٢ - ١ بيراميدز - الدقيقة ٧٨ من الشوط الثاني في استاد القاهرة الدولي", "⚽"),
            Triple("تنبيه مهم للغاية: مباراة الأهلي والزمالك", "هذه رسالة طويلة لاختبار التفاف النص العربي في الجزيرة الديناميكية مع اسم البطولة والملعب وتفاصيل الحدث كاملة، نختبر أن آخر سطر لا يختفي وأن زر الآن يظل ظاهرًا في النمط الرأسي حتى مع محتوى طويل جدًا ومتعدد الجمل.", "⚽"))
        for ((si, smp) in samples.withIndex()) for (style in 0..9) {
            prefs.islandShape = style
            prefs.islandColor = 0xFF4C1D95.toInt()
            val box = IslandController.buildBox(ctx, prefs, smp.first, smp.second, smp.third)
            val fw = style in setOf(2,5,9); val vt = style in setOf(4,7)
            val density = ctx.resources.displayMetrics.density
            val dpPx: (Int) -> Int = { (it * density).toInt().coerceAtLeast(1) }
            val ws = if (fw) View.MeasureSpec.makeMeasureSpec(dpPx(936), View.MeasureSpec.EXACTLY) else if (vt) View.MeasureSpec.makeMeasureSpec(dpPx(if (style==4) 340 else 390), View.MeasureSpec.EXACTLY) else View.MeasureSpec.makeMeasureSpec(dpPx(645), View.MeasureSpec.AT_MOST)
            box.measure(ws, View.MeasureSpec.makeMeasureSpec(dpPx(600), View.MeasureSpec.AT_MOST))
            box.layout(0, 0, box.measuredWidth, box.measuredHeight)
            fun findNow(v: View): android.widget.TextView? {
                if (v is android.widget.TextView && v.text?.toString() == "الآن") return v
                if (v is android.view.ViewGroup) for (i in 0 until v.childCount) findNow(v.getChildAt(i))?.let { return it }
                return null
            }
            val nowLabel = findNow(box)
            org.junit.Assert.assertNotNull("island style $style must render the Arabic NOW text", nowLabel)
            org.junit.Assert.assertEquals(View.VISIBLE, nowLabel!!.visibility)
            org.junit.Assert.assertTrue("island style $style collapsed the NOW label", nowLabel.measuredWidth >= dpPx(28) && nowLabel.measuredHeight > 0)
            org.junit.Assert.assertTrue("island style $style wraps the NOW label", nowLabel.lineCount == 1)
            if (vt) {
                org.junit.Assert.assertTrue("vertical island style $style exceeds 600dp: ${box.measuredHeight}", box.measuredHeight <= dpPx(600))
                fun findText(v: View, text: String): android.widget.TextView? {
                    if (v is android.widget.TextView && v.text?.toString() == text) return v
                    if (v is android.view.ViewGroup) for (i in 0 until v.childCount) findText(v.getChildAt(i), text)?.let { return it }
                    return null
                }
                val nowChip = findText(box, "الآن")
                org.junit.Assert.assertNotNull("vertical island style $style lost the NOW chip", nowChip)
                org.junit.Assert.assertTrue("vertical island style $style clipped the NOW chip", nowChip!!.measuredWidth > 0 && nowChip.measuredHeight > 0)
                val messageView = findText(box, smp.second)
                org.junit.Assert.assertNotNull("vertical island style $style omitted the long message", messageView)
                val layout = messageView!!.layout
                if (layout != null && layout.lineCount > 0) {
                    org.junit.Assert.assertEquals("vertical island style $style ellipsized the long message", 0, layout.getEllipsisCount(layout.lineCount - 1))
                }
            }
            log.append("s$si style $style ${box.measuredWidth}x${box.measuredHeight}\n")
            val bmp = Bitmap.createBitmap(1920, box.measuredHeight + 40, Bitmap.Config.ARGB_8888)
            val c = Canvas(bmp); c.drawColor(0xFF223344.toInt()); c.translate(20f, 20f); box.draw(c)
            FileOutputStream(File(out, "island_s${si}_$style.png")).use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
        File(out, "sizes.txt").writeText(log.toString())
    }
    @Test fun nowChipStaysInsideNarrowSettingsPreview() {
        val ctx = RuntimeEnvironment.getApplication()
        val prefs = Prefs(ctx)
        prefs.islandShape = 0
        val density = ctx.resources.displayMetrics.density
        val widthPx = (360f * density).toInt()
        val heightPx = (220f * density).toInt()
        val frame = android.widget.FrameLayout(ctx)
        val island = IslandController.buildPreview(
            ctx, "Abo Uobidah TV • الجزيرة الديناميكية",
            "معاينة التصميم — التنبيهات والمباريات والصلاة", "✦", 360
        )
        frame.addView(island, android.widget.FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT,
            android.view.Gravity.CENTER
        ))
        frame.measure(
            View.MeasureSpec.makeMeasureSpec(widthPx, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(heightPx, View.MeasureSpec.EXACTLY)
        )
        frame.layout(0, 0, widthPx, heightPx)
        fun findNow(v: View): android.widget.TextView? {
            if (v is android.widget.TextView && v.text?.toString() == "الآن") return v
            if (v is android.view.ViewGroup) for (i in 0 until v.childCount) findNow(v.getChildAt(i))?.let { return it }
            return null
        }
        val now = findNow(island)
        org.junit.Assert.assertNotNull("narrow settings preview must retain the NOW chip", now)
        val bounds = android.graphics.Rect(0, 0, now!!.width, now.height)
        run { var v: View = now; while (v !== island) { bounds.offset(v.left, v.top); v = v.parent as View } }
        org.junit.Assert.assertTrue("NOW chip must remain fully inside the narrow preview", bounds.left >= 0 && bounds.right <= island.width)
        org.junit.Assert.assertTrue("NOW label must remain visible", now.width > 0 && now.height > 0)
    }

}
