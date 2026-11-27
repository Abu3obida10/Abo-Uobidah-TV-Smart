package com.uobidah.launcher

import android.graphics.Rect
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.widget.HorizontalScrollView
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Ignore
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * تصميم14 «المباريات أولاً»: يثبت بضغط D-pad (يمين) أن الريموت يوصّل من بطاقة المباراة الأولى إلى كل مباراة تالية
 * وأن البطاقة المركَّزة تظهر كاملة داخل نافذة التمرير، وأن الثانية كاملة والثالثة ظاهرة جزئيًا عند الراحة.
 * بيانات المباريات اصطناعية (fixture). محاكاة Robolectric وليست جهاز تلفزيون. مصدري: لم يُشغَّل في هذه الجلسة.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w1280dp-h720dp-hdpi")
class MatchesFirstNavigationTest {
    private fun walk(v: View, f: (View) -> Unit) { f(v); if (v is ViewGroup) for (i in 0 until v.childCount) walk(v.getChildAt(i), f) }

    @Ignore("Old multi-layout system retired in v1.0.52 rebuild; new home has its own geometry tests")

    @Test fun dpadRightReachesEveryNextMatchAndShowsItFully() {
        val ctx = org.robolectric.RuntimeEnvironment.getApplication()
        val spec = com.uobidah.launcher.core.HomeLayouts.all.first { it.template == com.uobidah.launcher.core.HomeLayoutTemplate.MATCHES_FIRST }
        val pr = Prefs(ctx); pr.layoutIndex = spec.index
        val c = Robolectric.buildActivity(MainLauncherActivity::class.java).create().start().resume().visible()
        val act = c.get()
        // دع طلب الشبكة الخلفي (بلا شبكة هنا) ينتهي قبل حقن البيانات حتى لا يمسح صفّنا بعدها.
        repeat(20) { Shadows.shadowOf(android.os.Looper.getMainLooper()).idle(); Thread.sleep(50) }

        val now = System.currentTimeMillis()
        val teams = listOf("ريال مدريد" to "Real Madrid", "برشلونة" to "Barcelona", "مانشستر يونايتد" to "Manchester United", "ليفربول" to "Liverpool")
        val res = teams.mapIndexed { i, t ->
            Triple(TeamDef(t.first, t.second, "X", "id$i"), "",
                Match("m$i", t.first, "منافس $i", now + (i + 1) * 86_400_000L, "الدوري", null, null, "scheduled"))
        }
        val m = MainLauncherActivity::class.java.getDeclaredMethod("renderFavorites", List::class.java, Boolean::class.javaPrimitiveType)
        m.isAccessible = true; m.invoke(act, res, false)

        val root = act.window.decorView as ViewGroup
        fun relayout() {
            root.measure(View.MeasureSpec.makeMeasureSpec(1920, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY))
            root.layout(0, 0, 1920, 1080)
            Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
        }
        relayout(); relayout() // الثانية: بعد إعادة الرسم الناتجة عن تغيّر عرض الحاوية
        m.invoke(act, res, false); relayout()
        try {
            val f = View::class.java.getDeclaredField("mAttachInfo"); f.isAccessible = true
            val ai = f.get(root); val mt = ai.javaClass.getDeclaredField("mInTouchMode"); mt.isAccessible = true; mt.setBoolean(ai, false)
        } catch (_: Throwable) { }

        // بطاقات المباريات = أبناء صف المفضلة (الحقل favRow)
        val fld = MainLauncherActivity::class.java.getDeclaredField("favRow"); fld.isAccessible = true
        val row = fld.get(act) as ViewGroup
        val sv = row.parent as HorizontalScrollView
        assertEquals("عدد كروت المباريات", 4, row.childCount)
        val cards = (0 until row.childCount).map { row.getChildAt(it) }
        assertTrue("عرض نافذة التمرير لم يُقَس", sv.width > 0)

        // 1) عند الراحة: الأولى والثانية كاملتان، والثالثة ظاهرة جزئيًا فقط
        fun visibleWidth(v: View) = (minOf(v.right, sv.scrollX + sv.width) - maxOf(v.left, sv.scrollX)).coerceAtLeast(0)
        assertTrue("الأولى كاملة", visibleWidth(cards[0]) == cards[0].width)
        assertTrue("الثانية كاملة (يمين=${cards[1].right} من ${sv.width})", visibleWidth(cards[1]) == cards[1].width)
        val third = visibleWidth(cards[2])
        assertTrue("الثالثة ظاهرة جزئيًا دليلًا على المزيد: $third من ${cards[2].width}", third > 0 && third < cards[2].width)

        // 2) ريموت: يمين بالترتيب؛ كل بطاقة مركَّزة تصير كاملة الظهور
        fun focusRight(): View? {
            val cur = root.findFocus() ?: return null
            root.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_RIGHT))
            root.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_DPAD_RIGHT))
            if (root.findFocus() === cur) { // نفس ما يفعله ViewRootImpl عند عدم استهلاك المفتاح
                val next = cur.focusSearch(View.FOCUS_RIGHT)
                if (next != null && next !== cur) { val r = Rect(0, 0, next.width, next.height); next.requestFocus(View.FOCUS_RIGHT, r) }
            }
            relayout()
            return root.findFocus()
        }
        assertTrue("تعذر تركيز أول بطاقة", cards[0].requestFocus())
        assertEquals("الصف عند البداية قبل أي ضغط", 0, sv.scrollX)
        var prevScroll = sv.scrollX
        for (i in 1 until cards.size) {
            val f = focusRight()
            assertNotNull("لا تركيز بعد الضغط $i", f)
            var holder: View? = f; while (holder != null && holder.parent !== row) holder = holder.parent as? View
            assertTrue("بعد الضغط $i التركيز ليس على بطاقة المباراة ${i + 1} (الحالي=${holder?.let { row.indexOfChild(it) }})", holder === cards[i])
            assertTrue("المباراة ${i + 1} غير كاملة الظهور: ظاهر ${visibleWidth(cards[i])} من ${cards[i].width} (scrollX=${sv.scrollX})",
                visibleWidth(cards[i]) == cards[i].width)
            if (i >= 2) { // الثالثة وما بعدها كانت مقصوصة/خارج النافذة: التنقل لازم يحرّك الصف فعلًا لا أن يعتمد على الشكل الثابت
                assertTrue("الضغط $i إلى المباراة ${i + 1} لم يحرّك الصف (scrollX=${sv.scrollX}، السابق=$prevScroll)", sv.scrollX > prevScroll)
            }
            assertTrue("الصف لا يتراجع أثناء التقدم يمينًا", sv.scrollX >= prevScroll)
            prevScroll = sv.scrollX
        }
        assertTrue("بعد الوصول لآخر مباراة يجب أن يكون الصف قد تحرّك", sv.scrollX > 0)
    }
}
