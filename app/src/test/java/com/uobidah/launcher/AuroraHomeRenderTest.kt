package com.uobidah.launcher

import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * واجهة أورورا: تصيير حقيقي بالمقاسات الفعلية (منها مقاس جهاز المستخدم 2560x1183)
 * مع تأكيدات هندسية: لا قصّ، لا تداخل بين البطاقات أو بلاطات الدوك، ولا نص مقصوص رأسيًا.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class AuroraHomeRenderTest {

    private fun findTag(v: View, tag: String): View? {
        if (v.tag == tag) return v
        if (v is ViewGroup) for (i in 0 until v.childCount) {
            val r = findTag(v.getChildAt(i), tag)
            if (r != null) return r
        }
        return null
    }

    private fun assertInsideParent(v: View, path: String = "") {
        val p = v.parent as? View
        if (p != null) {
            assertTrue("$path/${v.tag}: top ${v.top} < 0", v.top >= -1)
            assertTrue("$path/${v.tag}: left ${v.left} < 0", v.left >= -1)
            assertTrue("$path/${v.tag}: bottom ${v.bottom} > parent height ${p.height}", v.bottom <= p.height + 1)
            assertTrue("$path/${v.tag}: right ${v.right} > parent width ${p.width}", v.right <= p.width + 1)
        }
        if (v is ViewGroup) for (i in 0 until v.childCount) assertInsideParent(v.getChildAt(i), "$path/${v.tag}")
    }

    private fun assertTextFits(v: View) {
        if (v is TextView && v.visibility == View.VISIBLE && v.text.isNotEmpty()) {
            val fm = v.paint.getFontMetricsInt(null)
            val avail = v.height - v.paddingTop - v.paddingBottom
            assertTrue(
                "text clipped vertically in tag=${v.tag}: fontMetrics=$fm available=$avail text=${v.text}",
                avail <= 0 || fm <= avail + 2
            )
        }
        if (v is ViewGroup) for (i in 0 until v.childCount) assertTextFits(v.getChildAt(i))
    }

    private fun overlapFree(parent: ViewGroup, label: String) {
        for (i in 0 until parent.childCount) {
            val a = parent.getChildAt(i)
            if (a.visibility != View.VISIBLE) continue
            for (j in i + 1 until parent.childCount) {
                val b = parent.getChildAt(j)
                if (b.visibility != View.VISIBLE) continue
                val overlap = a.left < b.right && b.left < a.right && a.top < b.bottom && b.top < a.bottom
                assertTrue("$label: children $i and $j overlap", !overlap)
            }
        }
    }

    private fun renderAndCheck(w: Int, h: Int) {
        val controller = Robolectric.buildActivity(MainLauncherActivity::class.java).create().start().resume().visible()
        org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
        val a = controller.get()
        val v = a.window.decorView
        v.measure(
            View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(h, View.MeasureSpec.EXACTLY)
        )
        v.layout(0, 0, w, h)
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        v.draw(Canvas(bmp))

        val content = v.findViewById<ViewGroup>(android.R.id.content)
        assertEquals("aurora-home-root", content.getChildAt(0)?.tag)
        val pill = findTag(v, "aurora-status-pill")
        val city = findTag(v, "aurora-city-block")
        val infoChip = findTag(v, "aurora-header-info")
        val cards = findTag(v, "aurora-cards-row")
        val dock = findTag(v, "aurora-dock")
        assertNotNull("status pill missing", pill)
        assertNotNull("city block missing", city)
        assertNotNull("header info chip missing", infoChip)
        assertNotNull("cards row missing", cards)
        assertNotNull("dock missing", dock)
        assertEquals("cards row must hold exactly 3 cards", 3, (cards as ViewGroup).childCount)
        overlapFree(cards as ViewGroup, "cards")
        overlapFree(dock as ViewGroup, "dock")
        assertInsideParent(v)
        assertTextFits(v)
        controller.pause().stop().destroy()
    }

    /** مقاس جهاز المستخدم الفعلي من لقطات الشاشة المرسلة. */
    @Test fun auroraAtUserDeviceResolution() = renderAndCheck(2560, 1183)

    @Test fun auroraAt1080p() = renderAndCheck(1920, 1080)

    @Test fun auroraAt800p() = renderAndCheck(1280, 800)
}
