package com.uobidah.launcher

import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
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

    @Before fun initWorkManager() {
        // HomeWatchdogWorker.schedule يعمل في onCreate؛ WorkManager غير مهيأ افتراضيًا في بيئة Robolectric
        val ctx = org.robolectric.RuntimeEnvironment.getApplication()
        try {
            androidx.work.WorkManager.initialize(ctx, androidx.work.Configuration.Builder().build())
        } catch (_: IllegalStateException) {
            // مهيأ مسبقًا في اختبار سابق
        }
    }

    private fun v0(v: MainLauncherActivity) = v.window.decorView

    private fun findTag(v: View, tag: String): View? {
        if (v.tag == tag) return v
        if (v is ViewGroup) for (i in 0 until v.childCount) {
            val r = findTag(v.getChildAt(i), tag)
            if (r != null) return r
        }
        return null
    }

    private val overflowViolations = mutableListOf<String>()

    private fun assertInsideParent(v: View, path: String = "") {
        val p = v.parent as? View
        if (p != null) {
            if (v.top < -1 || v.left < -1 || v.bottom > p.height + 1 || v.right > p.width + 1) {
                overflowViolations.add("$path/${v.tag} [${v.left},${v.top} ${v.right}x${v.bottom}] in parent ${p.tag} [${p.width}x${p.height}]")
            }
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

    private fun renderAndCheck(w: Int, h: Int, density: Float = 1f) {
        val dm = org.robolectric.RuntimeEnvironment.getApplication().resources.displayMetrics
        val oldD = dm.density; val oldSd = dm.scaledDensity; val oldDpi = dm.densityDpi
        val oldW = dm.widthPixels; val oldH = dm.heightPixels
        dm.density = density; dm.scaledDensity = density; dm.densityDpi = (160 * density).toInt()
        dm.widthPixels = w; dm.heightPixels = h
        try {
        val controller = Robolectric.buildActivity(MainLauncherActivity::class.java).create().start().resume().visible()
        org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
        // بطاقة المباراة تُملأ على خيط خلفي (Net.bg): انتظر البطاقة أو حالة الفراغ حتى تكون الهندسة حتمية
        val waitUntil = System.currentTimeMillis() + 4000
        while (findTag(v0(v = controller.get()), "r8-featured-match") == null && findTag(v0(controller.get()), "r8-featured-empty") == null && System.currentTimeMillis() < waitUntil) {
            Thread.sleep(40)
            org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
        }
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
        overflowViolations.clear()
        assertInsideParent(v)
        assertTrue("overflow:\n" + overflowViolations.joinToString("\n"), overflowViolations.isEmpty())
        assertTextFits(v)
        controller.pause().stop().destroy()
        } finally {
            dm.density = oldD; dm.scaledDensity = oldSd; dm.densityDpi = oldDpi
            dm.widthPixels = oldW; dm.heightPixels = oldH
        }
    }

    /** مقاس جهاز المستخدم الفعلي من لقطات الشاشة المرسلة. */
    @Test fun auroraAtUserDeviceResolution() = renderAndCheck(2560, 1183)

    @Test fun auroraAt1080p() = renderAndCheck(1920, 1080)

    @Test fun auroraAt800p() = renderAndCheck(1280, 800)

    /** تلفاز المستخدم JVC: ‏1920x1080 بكثافة tvdpi ‏(213dpi) — هدف الأداء الأساسي لكل إصدار. */
    @Test fun auroraAtHisJvcTv() = renderAndCheck(1920, 1080, 1.33125f)

    /** جهاز يبلّغ كثافة xhdpi على لوحة 1080p: سقف مقياس أورورا يمنع فيض الأقسام خارج الشاشة. */
    @Test fun auroraAtMisreportedXhdpiTv() = renderAndCheck(1920, 1080, 2f)
}
