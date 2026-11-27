package com.uobidah.launcher
import android.app.Activity
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import android.widget.TextView
import org.junit.Ignore
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.io.FileOutputStream

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w1280dp-h720dp-hdpi")
class ScreenRenderTest {
    private fun shot(name: String, a: Activity, scrollY: Int = 0) {
        val v = a.window.decorView
        v.measure(View.MeasureSpec.makeMeasureSpec(1920, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY))
        v.layout(0, 0, 1920, 1080)
        val bmp = Bitmap.createBitmap(1920, 1080, Bitmap.Config.ARGB_8888)
        v.draw(Canvas(bmp))
        File("/tmp/abo/shots6").mkdirs()
        FileOutputStream(File("/tmp/abo/shots6/$name.png")).use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
    private fun findText(v: View): String {
        val sb = StringBuilder()
        if (v is android.widget.TextView && v.text.isNotBlank() && (v.text.contains("جاري") || v.text.contains("مشغول") || v.text.contains("تعذر") || v.text.contains("المصدر") || v.text.contains("انتهى"))) sb.append(v.text).append('\n')
        if (v is android.view.ViewGroup) for (i in 0 until v.childCount) sb.append(findText(v.getChildAt(i)))
        return sb.toString()
    }
    private fun findScroll(v: View): android.widget.ScrollView? {
        if (v is android.widget.ScrollView) return v
        if (v is android.view.ViewGroup) for (i in 0 until v.childCount) { val r = findScroll(v.getChildAt(i)); if (r != null) return r }
        return null
    }
    private fun containsView(root: View, target: View): Boolean {
        if (root === target) return true
        if (root is android.view.ViewGroup) for (i in 0 until root.childCount) {
            if (containsView(root.getChildAt(i), target)) return true
        }
        return false
    }
    private fun <T : Activity> go(cls: Class<T>, name: String) {
        val controller = Robolectric.buildActivity(cls).create().start().resume().visible()
        org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
        val activity = controller.get()
        shot(name, activity)
        val sc = findScroll(activity.window.decorView)
        if (sc != null) {
            File("/tmp/abo/shots6/$name.bounds").writeText("scroll top=${sc.top} bottom=${sc.bottom} h=${sc.height} parentTop=${(sc.parent as View).top}")
            sc.scrollTo(0, 300)
            shot(name + "_scrolled300", activity)
        }
        controller.pause().stop().destroy()
    }
    @Test fun matchDetail() {
        val i = android.content.Intent(org.robolectric.RuntimeEnvironment.getApplication(), MatchDetailActivity::class.java)
            .putExtra("id", "t1").putExtra("source", "ysscores").putExtra("home", "الأهلي").putExtra("away", "بيراميدز")
            .putExtra("league", "الدوري المصري").putExtra("venue", "ستاد القاهرة (بيانات اختبار)")
            .putExtra("startMs", System.currentTimeMillis() + 3600_000L)
        val c = Robolectric.buildActivity(MatchDetailActivity::class.java, i).create().start().resume().visible()
        org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
        for (k in 0 until 20) { Thread.sleep(250); org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle() }
        File("/tmp/abo/shots6/detail_status.txt").writeText(findText(c.get().window.decorView))
        shot("detail_top", c.get())
        findScroll(c.get().window.decorView)?.scrollTo(0, 100000)
        shot("detail_bottom", c.get())
        c.pause().stop().destroy()
    }
    @Test fun iptvTabs() {
        val ctx = org.robolectric.RuntimeEnvironment.getApplication()
        val pr = Prefs(ctx)
        pr.iptvPlaylists = listOf("قائمة المنزل||http://a.example/1.m3u", "Sports HD||http://a.example/2.m3u")
        pr.iptvDefaultTab = "Sports HD"
        val c = Robolectric.buildActivity(IptvProActivity::class.java).create().start().resume().visible()
        org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
        val act = c.get()
        shot("iptv_tabs_start", act)
        val bar = IptvProActivity::class.java.getDeclaredField("playlistBar").apply { isAccessible = true }.get(act) as android.widget.HorizontalScrollView
        val row = IptvProActivity::class.java.getDeclaredField("tabsRow").apply { isAccessible = true }.get(act) as android.widget.LinearLayout
        val titles = (0 until row.childCount).map { (row.getChildAt(it) as? android.widget.TextView)?.text.toString() }
        File("/tmp/abo/shots6/iptv_tabs.txt").writeText("count=" + titles.size + "\n" + titles.joinToString("\n"))
        File("/tmp/abo/shots6/iptv_scroll.txt").writeText("scrollX=" + bar.scrollX + " childW=" + row.width + " barW=" + bar.width + " rtl=" + (bar.layoutDirection == View.LAYOUT_DIRECTION_RTL))
        bar.scrollTo(0, 0)
        shot("iptv_tabs_s0", act)
        bar.scrollTo(row.width - bar.width, 0)
        shot("iptv_tabs_s1", act)
        bar.scrollTo(-(row.width - bar.width), 0)
        shot("iptv_tabs_s2", act)
        c.pause().stop().destroy()
    }
    @Test fun chooser() {
        val ctx = org.robolectric.RuntimeEnvironment.getApplication()
        val pr = Prefs(ctx)
        pr.iptvPlaylists = listOf("قائمة المنزل||http://a.example/1.m3u", "Sports HD||http://a.example/2.m3u", "أفلام ومسلسلات||http://a.example/3.m3u")
        pr.iptvDefaultTab = ""
        val c = Robolectric.buildActivity(IptvProActivity::class.java).create().start().resume().visible()
        org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
        val root = c.get().window.decorView
        val chooser = findByText(root, "اختر قائمة التشغيل")
        org.junit.Assert.assertNotNull("fullscreen IPTV list chooser must be in the activity, not a Dialog", chooser)
        val contentField = IptvProActivity::class.java.getDeclaredField("contentRow").apply { isAccessible = true }
        val contentRow = contentField.get(c.get()) as View
        org.junit.Assert.assertEquals("chooser must fully replace the IPTV split player view", View.GONE, contentRow.visibility)
        for (name in listOf("قائمة المنزل", "Sports HD", "أفلام ومسلسلات")) {
            org.junit.Assert.assertNotNull("playlist is missing from chooser: $name", findByText(root, name))
        }
        val v = root
        v.measure(View.MeasureSpec.makeMeasureSpec(1920, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY))
        v.layout(0, 0, 1920, 1080)
        val bmp = Bitmap.createBitmap(1920, 1080, Bitmap.Config.ARGB_8888)
        v.draw(Canvas(bmp))
        File("/tmp/abo/shots6").mkdirs()
        FileOutputStream(File("/tmp/abo/shots6/chooser.png")).use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bmp.recycle()
        c.pause().stop().destroy()
    }
    private fun findByText(v: View, text: String): View? {
        if (v is android.widget.TextView && v.text?.toString()?.contains(text) == true) return v
        if (v is android.view.ViewGroup) for (i in 0 until v.childCount) findByText(v.getChildAt(i), text)?.let { return it }
        return null
    }

    @Test fun playlistShortcutAlwaysOpensIptvChooserEvenWhenDefaultExists() {
        val ctx = org.robolectric.RuntimeEnvironment.getApplication()
        val pr = Prefs(ctx)
        pr.iptvPlaylists = listOf("قائمتي||http://example.test/list.m3u")
        pr.iptvDefaultTab = "🔖 قائمتي"
        val intent = android.content.Intent(ctx, IptvProActivity::class.java)
            .putExtra(IptvProActivity.EXTRA_OPEN_CHOOSER, true)
        val controller = Robolectric.buildActivity(IptvProActivity::class.java, intent).create().start().resume().visible()
        org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
        val root = controller.get().window.decorView
        org.junit.Assert.assertNotNull("IPTV playlist shortcut must open its own in-app chooser", findByText(root, "اختر قائمة التشغيل"))
        org.junit.Assert.assertNotNull("saved IPTV playlist must be listed", findByText(root, "قائمتي"))
        controller.pause().stop().destroy()
    }

    @Ignore("Old multi-layout system retired in v1.0.52 rebuild; new home has its own geometry tests")

    @Test fun homes() {
        val ctx = org.robolectric.RuntimeEnvironment.getApplication()
        for (idx in listOf(0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20, 21, 22, 23)) {
            Prefs(ctx).layoutIndex = idx
            Prefs(ctx).iptvFavorites = listOf("beIN Sports 1||http://a.example/1.m3u8", "MBC 1||http://a.example/2.m3u8", "الجزيرة||http://a.example/3.m3u8")
            go(MainLauncherActivity::class.java, "home_" + idx)
        }
    }
    /** بيانات اختبار وهمية (FIXTURE) لإثبات التخطيط بمحتوى حقيقي الشكل؛ ليست بيانات حقيقية. */
    @Ignore("Old multi-layout system retired in v1.0.52 rebuild; new home has its own geometry tests")
    @Test fun homesFixture() {
        val ctx = org.robolectric.RuntimeEnvironment.getApplication()
        val pm = ctx.packageManager
        val names = listOf("YouTube","Netflix","Shahid","Spotify","Kodi","Chrome","Files","Games","Prime Video","Disney+","Weather","Camera","Calculator")
        var fl = com.uobidah.launcher.core.FavoriteList()
        for ((i, n) in names.withIndex()) {
            val pi = android.content.pm.PackageInfo(); pi.packageName = "fixture.app$i"
            val ai = android.content.pm.ApplicationInfo(); ai.packageName = pi.packageName; ai.nonLocalizedLabel = n
            pi.applicationInfo = ai
            org.robolectric.Shadows.shadowOf(pm).installPackage(pi)
            fl = fl.add(pi.packageName)
        }
        for (idx in listOf(0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20, 21, 22, 23)) {
            val pr = Prefs(ctx)
            pr.saveFavorites(fl); pr.layoutIndex = idx; pr.showMatchFavorites = true; pr.showPinnedApps = true; pr.showHijriDate = true
            pr.iptvFavorites = listOf("beIN Sports 1||http://a/1","MBC 1||http://a/2","الجزيرة||http://a/3","Abu Dhabi Sports||http://a/4","Dubai TV||http://a/5")
            val c = Robolectric.buildActivity(MainLauncherActivity::class.java).create().start().resume().visible()
            org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
            try {
                val act = c.get()
                val f = act.javaClass.getDeclaredMethod("renderFavorites", List::class.java, Boolean::class.javaPrimitiveType); f.isAccessible = true
                val now = System.currentTimeMillis()
                val res = listOf(
                    Triple(TeamDef("الأهلي","Al Ahly","Egypt","1"), "", Match("f1","الأهلي","بيراميدز", now + 3600_000, "الدوري المصري", null, null)),
                    Triple(TeamDef("الزمالك","Zamalek","Egypt","2"), "", Match("f2","الزمالك","المصري", now - 3600_000, "الدوري المصري", 2, 1, "FT")),
                    Triple(TeamDef("ريال مدريد","Real Madrid","Spain","3"), "", Match("f3","ريال مدريد","برشلونة", now + 86400_000, "الدوري الإسباني", null, null)))
                f.invoke(act, res, false)
            } catch (t: Throwable) { throw AssertionError("Could not inject fixture matches for layout $idx", t) }
            // let the 110 ms card fade-in finish; otherwise the render captures cards at 55% alpha
            org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(600))
            val activity = c.get()
            shot("fx_" + idx, activity)
            if (idx == 6 || idx == 9) {
                val fr = MainLauncherActivity::class.java.getDeclaredField("favRow").apply { isAccessible = true }.get(activity) as android.widget.LinearLayout
                val target = fr.getChildAt(1)
                target.isFocusableInTouchMode = true; val ok = target.requestFocus()
                org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(400))
                File("/tmp/abo/shots6/fx_" + idx + "_focus.txt").writeText("cards=" + fr.childCount + " focusable=" + target.isFocusable + " focusedNow=" + target.isFocused + " requested=" + ok)
                shot("fx_" + idx + "_focus", activity)
            }
            val sc = findScroll(activity.window.decorView)
            val timeField = MainLauncherActivity::class.java.getDeclaredField("tvTime").apply { isAccessible = true }
            val timeView = timeField.get(activity) as View
            org.junit.Assert.assertNotNull("layout $idx did not build its top clock", timeView)
            org.junit.Assert.assertNotNull("layout $idx must have the shared scrolling content", sc)
            org.junit.Assert.assertFalse("layout $idx must keep the top bar outside the scrolling body", containsView(sc!!, timeView))
            val navLabel = findByText(activity.window.decorView, "الإعدادات")
            org.junit.Assert.assertNotNull("layout $idx must show the single side navigation rail", navLabel)
            org.junit.Assert.assertNull("layout $idx must not show a bottom navigation dock", findByText(activity.window.decorView, "تبويب الأطفال"))
            if (idx in listOf(12, 15, 16, 17, 18, 19, 20, 21, 22, 23)) {
                val hijriField = MainLauncherActivity::class.java.getDeclaredField("tvHijri").apply { isAccessible = true }
                val hijriView = hijriField.get(activity) as android.widget.TextView
                org.junit.Assert.assertTrue("layout $idx Hijri label must be single-line", hijriView.isSingleLine && hijriView.maxLines == 1)
                org.junit.Assert.assertEquals("layout $idx Hijri date must not wrap", android.text.TextUtils.TruncateAt.END, hijriView.ellipsize)
            }
            if (sc != null) { sc.scrollTo(0, 100000); shot("fx_" + idx + "_scrolled", activity) }
            c.pause().stop().destroy()
        }
    }
    @Test fun layoutpicker() { go(LayoutPickerActivity::class.java, "picker") }

    @Test fun sportsLayoutPreviewIncludesMatchCardsAndStandingsCue() {
        val controller = Robolectric.buildActivity(LayoutPickerActivity::class.java).create().start().resume().visible()
        org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
        val activity = controller.get()
        val pendingField = LayoutPickerActivity::class.java.getDeclaredField("pending").apply { isAccessible = true }
        pendingField.setInt(activity, 22)
        val render = LayoutPickerActivity::class.java.getDeclaredMethod("renderPreview").apply { isAccessible = true }
        render.invoke(activity)
        val root = activity.window.decorView
        org.junit.Assert.assertNotNull("sports layout preview must show its title and purpose", findByText(root, "مركز المباريات"))
        org.junit.Assert.assertNotNull("sports layout preview must show the side rail mockup", findByText(root, "الإجراءات"))
        controller.pause().stop().destroy()
    }

    @Test fun screens() {
        go(ThemePickerActivity::class.java, "themes")
        go(IslandSettingsActivity::class.java, "island_settings")
        go(IptvProActivity::class.java, "iptv")
        go(MatchDetailActivity::class.java, "match_detail")
    }
    @Ignore("Old multi-layout system retired in v1.0.52 rebuild; new home has its own geometry tests")
    @Test fun appNamesCanUseTwoLinesOnNarrowTiles() {
        val controller = Robolectric.buildActivity(MainLauncherActivity::class.java).create().start().resume().visible()
        org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
        val activity = controller.get()
        val method = MainLauncherActivity::class.java.getDeclaredMethod(
            "tile",
            android.graphics.drawable.Drawable::class.java,
            String::class.java,
            String::class.java,
            kotlin.jvm.functions.Function0::class.java,
            kotlin.jvm.functions.Function0::class.java,
            Boolean::class.javaPrimitiveType!!,
            Boolean::class.javaPrimitiveType!!,
            Float::class.javaPrimitiveType!!
        ).apply { isAccessible = true }
        val click: () -> Unit = {}
        val tile = method.invoke(activity, null, "📦", "Amazon Prime Video", click, null, true, true, 1f) as View
        fun findLabel(v: View): TextView? {
            if (v is TextView && v.text?.toString() == "Amazon Prime Video") return v
            if (v is android.view.ViewGroup) for (i in 0 until v.childCount) findLabel(v.getChildAt(i))?.let { return it }
            return null
        }
        val label = findLabel(tile)
        org.junit.Assert.assertNotNull("app tile must keep its label", label)
        org.junit.Assert.assertEquals("app labels must be allowed two lines", 2, label!!.maxLines)
        org.junit.Assert.assertEquals("full app name must remain accessible", "Amazon Prime Video", label.contentDescription)
        org.junit.Assert.assertEquals(android.text.TextUtils.TruncateAt.END, label.ellipsize)
        controller.pause().stop().destroy()
    }

}
