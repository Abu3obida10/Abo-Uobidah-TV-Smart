package com.uobidah.launcher

import android.view.View
import android.view.ViewGroup
import android.widget.HorizontalScrollView
import android.widget.TextView
import org.junit.Assert.assertEquals
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
 * تصميم الصلاة (R2): أول 4 بطاقات مفضلة تتسع كاملة داخل عرض الصف المقاس فعلًا، وأسماؤها غير مقصوصة،
 * وزر «تنظيم» لا يتداخل مع عنوان «المفضلة». بيانات الاختبار تطبيقات وهمية (fixture)، وليست لقطة تلفزيون.
 * (مصدري: لم يُشغَّل في بيئة هذه الجلسة لعدم توفر Gradle/SDK.)
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w1280dp-h720dp-hdpi")
class ArchFavoritesFitTest {
    private fun walk(v: View, f: (View) -> Unit) { f(v); if (v is ViewGroup) for (i in 0 until v.childCount) walk(v.getChildAt(i), f) }

    @Ignore("Old multi-layout system retired in v1.0.52 rebuild; new home has its own geometry tests")

    @Test fun fourFavoriteTilesAreFullyInsideTheMeasuredRowAndNamesAreNotClipped() {
        val ctx = org.robolectric.RuntimeEnvironment.getApplication()
        val pm = ctx.packageManager
        var fl = com.uobidah.launcher.core.FavoriteList()
        val names = listOf("يوتيوب", "شاهد", "Claude Code", "BrowseHere", "Netflix", "Plex")
        names.forEachIndexed { i, n ->
            val pi = android.content.pm.PackageInfo(); pi.packageName = "fixture.arch$i"
            val ai = android.content.pm.ApplicationInfo(); ai.packageName = pi.packageName; ai.nonLocalizedLabel = n
            pi.applicationInfo = ai
            Shadows.shadowOf(pm).installPackage(pi)
            fl = fl.add(pi.packageName)
        }
        val spec = com.uobidah.launcher.core.HomeLayouts.all.first { it.template == com.uobidah.launcher.core.HomeLayoutTemplate.PRAYER_HERO }
        val pr = Prefs(ctx)
        pr.saveFavorites(fl); pr.layoutIndex = spec.index; pr.showPinnedApps = true; pr.showPrayerCard = true
        // حالة ما بعد أول تشغيل (المسار الحقيقي للمستخدم): لا ترحيلات ولا شاشة تفعيل تتدخل في الإعدادات
        pr.pinnedInit = true; pr.askedPerms = true; pr.homeCleanV1 = true; pr.proHomeDesignV1 = true
        val c = Robolectric.buildActivity(MainLauncherActivity::class.java).create().start().resume().visible()
        Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
        assertEquals("المفضلة المحفوظة يجب ألا تُمسح أثناء بدء الشاشة", names.size, pr.favorites().visible().size)
        assertEquals("تخطيط الصلاة يجب أن يبقى هو المطبّق", com.uobidah.launcher.core.HomeLayoutTemplate.PRAYER_HERO, com.uobidah.launcher.core.HomeLayouts.get(pr.layoutIndex).template)
        val root = c.get().window.decorView as ViewGroup
        fun relayout() {
            root.measure(View.MeasureSpec.makeMeasureSpec(1920, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY))
            root.layout(0, 0, 1920, 1080)
            Shadows.shadowOf(android.os.Looper.getMainLooper()).idle() // يشغّل sv.post { apply() }
        }
        relayout(); relayout() // الأولى تقيس، الثانية بعد تطبيق العرض المقيس

        val tiles = ArrayList<View>()
        walk(root) { if (it.tag == "arch-fav") tiles.add(it) }
        assertTrue("لم تُبنَ بطاقات مفضلة بوسم arch-fav (عددها ${tiles.size})", tiles.size >= 4)
        val row = tiles[0].parent as ViewGroup
        val sv = row.parent as HorizontalScrollView
        val avail = sv.width - sv.paddingLeft - sv.paddingRight
        assertTrue("عرض الصف لم يُقَس", avail > 0)
        for ((i, t) in tiles.take(4).withIndex()) {
            assertTrue("البطاقة ${i + 1}: يمين=${t.right} أكبر من عرض الصف=$avail (مقصوصة)", t.left >= 0 && t.right <= avail)
            assertTrue("البطاقة ${i + 1} بلا عرض", t.width > 0)
            walk(t) { v ->
                if (v is TextView && v.visibility == View.VISIBLE && v.text.isNotEmpty()) {
                    val lay = v.layout
                    if (lay != null) for (ln in 0 until lay.lineCount) assertEquals("نص البطاقة ${i + 1} مقصوص بـ… : '${v.text}'", 0, lay.getEllipsisCount(ln))
                }
            }
        }
        val w0 = tiles[0].width
        assertTrue("البطاقات الأربع بعرض متساوٍ", tiles.take(4).all { it.width == w0 })

        // زر «تنظيم» لا يتداخل مع عنوان «المفضلة»
        var title: TextView? = null; var manage: TextView? = null
        walk(root) { v ->
            if (v is TextView) {
                if (v.text.toString() == "المفضلة") title = v
                if (v.contentDescription?.toString() == "تنظيم التطبيقات") manage = v
            }
        }
        assertTrue("عنوان المفضلة غير موجود", title != null); assertTrue("زر التنظيم غير موجود", manage != null)
        val tl = IntArray(2); val ml = IntArray(2)
        title!!.getLocationInWindow(tl); manage!!.getLocationInWindow(ml)
        val titleL = tl[0]; val titleR = tl[0] + title!!.width
        val mL = ml[0]; val mR = ml[0] + manage!!.width
        assertTrue("زر التنظيم [$mL,$mR] يتداخل مع العنوان [$titleL,$titleR]", mR <= titleL || mL >= titleR)
        val tCenter = tl[1] + title!!.height / 2; val mCenter = ml[1] + manage!!.height / 2
        assertTrue("زر التنظيم غير محاذٍ لوسط العنوان (فرق ${Math.abs(tCenter - mCenter)}px)", Math.abs(tCenter - mCenter) <= 12)
    }
}
