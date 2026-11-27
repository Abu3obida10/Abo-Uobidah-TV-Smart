package com.uobidah.launcher.v2

import android.app.Activity
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import com.uobidah.launcher.Ui
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * نظام تصميم v2: تصيير حقيقي لشاشة عيّنة (خلفية + عنوان قسم + بلاطات + أزرار أيقونات + شارات)
 * بمقاسات أجهزة فعلية، منها جهاز المستخدم JVC (1920x1080 tvdpi). يؤكد: لا قصّ خارج الحدود،
 * لا تداخل بين العناصر، لا نص مقصوص رأسيًا، ولا TextView يحمل رموز يونيكود غير عربية/أساسية.
 */
abstract class V2DesignSystemRenderTestBase {

    private fun findTag(v: View, tag: String): View? {
        if (v.tag == tag) return v
        if (v is ViewGroup) for (i in 0 until v.childCount) {
            val r = findTag(v.getChildAt(i), tag)
            if (r != null) return r
        }
        return null
    }

    private val problems = mutableListOf<String>()

    private fun assertInsideParent(v: View, path: String = "") {
        val p = v.parent as? View
        if (p != null && (v.top < -1 || v.left < -1 || v.bottom > p.height + 1 || v.right > p.width + 1)) {
            problems.add("overflow $path/${v.tag} [${v.left},${v.top} ${v.right}x${v.bottom}] in ${p.tag} [${p.width}x${p.height}]")
        }
        if (v is ViewGroup) for (i in 0 until v.childCount) assertInsideParent(v.getChildAt(i), "$path/${v.tag}")
    }

    private fun assertTextFits(v: View) {
        if (v is TextView && v.visibility == View.VISIBLE && v.text.isNotEmpty()) {
            val fm = v.paint.getFontMetricsInt(null)
            val avail = v.height - v.paddingTop - v.paddingBottom
            assertTrue("text clipped in tag=${v.tag}: font=$fm avail=$avail text=${v.text}", avail <= 0 || fm <= avail + 2)
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
                assertTrue("$label: children $i/$j overlap", !(a.left < b.right && b.left < a.right && a.top < b.bottom && b.top < a.bottom))
            }
        }
    }

    /** حارس النظام الجديد: لا رموز يونيكود زخرفية/إيموجي في أي نص (خطوط التلفزيون ناقصة). */
    private fun assertNoGlyphs(v: View) {
        if (v is TextView) {
            val s = v.text?.toString() ?: ""
            val bad = s.filter { it.code > 0x2000 && it !in '؀'..'ۿ' && it != '—' && it != '•' && it != '…' }
            assertTrue("glyph text in v2 (use Icons vectors instead): tag=${v.tag} text=$s", bad.isEmpty())
        }
        if (v is ViewGroup) for (i in 0 until v.childCount) assertNoGlyphs(v.getChildAt(i))
    }

    private fun sampleScreen(a: Activity): View {
        val root = FrameLayout(a)
        root.background = V2.background()
        root.tag = "v2-root"
        val col = LinearLayout(a).apply {
            orientation = LinearLayout.VERTICAL
            layoutDirection = View.LAYOUT_DIRECTION_RTL
            setPadding(V2.p(a, V2Space.EDGE), V2.p(a, V2Space.L), V2.p(a, V2Space.EDGE), V2.p(a, V2Space.L))
            tag = "v2-sample"
        }
        col.addView(V2.sectionHeader(a, "المباريات", "الكل") {}, Ui.lp(Ui.MATCH, Ui.WRAP))
        val tiles = LinearLayout(a).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutDirection = View.LAYOUT_DIRECTION_LTR
            tag = "v2-tiles-row"
        }
        tiles.addView(V2.tile(a, null, "apps", "التطبيقات", "24 تطبيقًا") {}, Ui.lpm(0, Ui.WRAP, 1f, 0, V2.p(a, V2Space.M), V2.p(a, V2Space.S), 0))
        tiles.addView(V2.tile(a, null, "trophy", "المباريات", "الأهلي") {}, Ui.lpm(0, Ui.WRAP, 1f, 0, V2.p(a, V2Space.M), V2.p(a, V2Space.S), 0))
        tiles.addView(V2.tile(a, null, "mosque", "الصلاة", "الجمعة 11:45") {}, Ui.lpm(0, Ui.WRAP, 1f, 0, V2.p(a, V2Space.M), V2.p(a, V2Space.S), 0))
        tiles.addView(V2.tile(a, null, "tv", "البث المباشر") {}, Ui.lpm(0, Ui.WRAP, 1f, 0, V2.p(a, V2Space.M), 0, 0))
        col.addView(tiles, Ui.lp(Ui.MATCH, Ui.WRAP))
        val actions = LinearLayout(a).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutDirection = View.LAYOUT_DIRECTION_LTR
            tag = "v2-actions-row"
        }
        actions.addView(V2.iconBtn(a, "wifi", "الشبكة") {})
        actions.addView(V2.iconBtn(a, "search", "بحث") {})
        actions.addView(V2.iconBtn(a, "image", "الخلفيات") {})
        actions.addView(V2.iconBtn(a, "settings", "الإعدادات") {})
        col.addView(actions, Ui.lpm(Ui.WRAP, Ui.WRAP, 0f, 0, V2.p(a, V2Space.L), 0, 0))
        val badges = LinearLayout(a).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutDirection = View.LAYOUT_DIRECTION_LTR
            tag = "v2-badges-row"
        }
        badges.addView(V2.badge(a, "جارية", V2Colors.live), Ui.lpm(Ui.WRAP, Ui.WRAP, 0f, 0, 0, V2.p(a, V2Space.S), 0))
        badges.addView(V2.badge(a, "قادمة", V2Colors.accent2), Ui.lpm(Ui.WRAP, Ui.WRAP, 0f, 0, 0, V2.p(a, V2Space.S), 0))
        badges.addView(V2.badge(a, "مباشر", V2Colors.ok), Ui.lpm(Ui.WRAP, Ui.WRAP, 0f, 0, 0, 0, 0))
        col.addView(badges, Ui.lpm(Ui.WRAP, Ui.WRAP, 0f, 0, V2.p(a, V2Space.M), 0, 0))
        root.addView(col, FrameLayout.LayoutParams(Ui.MATCH, Ui.MATCH))
        return root
    }

    private fun renderAndCheck() {
        val controller = Robolectric.buildActivity(Activity::class.java).create().start().resume().visible()
        val a = controller.get()
        val root = sampleScreen(a)
        a.setContentView(root)
        val v = a.window.decorView
        val w = v.resources.displayMetrics.widthPixels
        val h = v.resources.displayMetrics.heightPixels
        v.measure(View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(h, View.MeasureSpec.EXACTLY))
        v.layout(0, 0, w, h)
        v.draw(Canvas(Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)))
        assertNotNull("root missing", findTag(v, "v2-root"))
        val tiles = findTag(v, "v2-tiles-row") as ViewGroup
        val actions = findTag(v, "v2-actions-row") as ViewGroup
        val badges = findTag(v, "v2-badges-row") as ViewGroup
        overlapFree(tiles, "tiles")
        overlapFree(actions, "actions")
        overlapFree(badges, "badges")
        problems.clear()
        assertInsideParent(v)
        assertTrue("overflow:\n" + problems.joinToString("\n"), problems.isEmpty())
        assertTextFits(v)
        assertNoGlyphs(v)
        controller.pause().stop().destroy()
    }

    @Test fun designSystemRendersClean() = renderAndCheck()
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w1920dp-h1080dp-land-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class V2Render1080pTest : V2DesignSystemRenderTestBase()

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w1442dp-h811dp-land-tvdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class V2RenderHisJvcTvTest : V2DesignSystemRenderTestBase()

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w1280dp-h800dp-land-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class V2Render800pTest : V2DesignSystemRenderTestBase()

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w1280dp-h591dp-land-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class V2RenderUserDeviceTest : V2DesignSystemRenderTestBase()
