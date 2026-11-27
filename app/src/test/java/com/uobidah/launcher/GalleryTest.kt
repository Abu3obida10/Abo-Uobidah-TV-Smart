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
@Config(sdk = [34], qualifiers = "w960dp-h540dp-xhdpi")
class GalleryTest {
    private fun shot(name: String, a: Activity, scrollY: Int = 0) {
        val v = a.window.decorView
        v.measure(View.MeasureSpec.makeMeasureSpec(1920, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY))
        v.layout(0, 0, 1920, 1080)
        val bmp = Bitmap.createBitmap(1920, 1080, Bitmap.Config.ARGB_8888)
        v.draw(Canvas(bmp))
        File("/tmp/abo/t960").mkdirs()
        FileOutputStream(File("/tmp/abo/t960/$name.png")).use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
    @Ignore("Old multi-layout system retired in v1.0.52 rebuild; new home has its own geometry tests")
    @Test fun galleryAll() {
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
        for (spec in com.uobidah.launcher.core.HomeLayouts.all) {
            val idx = spec.index
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
            } catch (t: Throwable) { }
            shot("gal_" + (if (idx < 10) "0" else "") + idx, c.get())
            c.pause().stop().destroy()
        }
    }
    @Ignore("Old multi-layout system retired in v1.0.52 rebuild; new home has its own geometry tests")
    @Test fun harnessFacts() {
        val ctx = org.robolectric.RuntimeEnvironment.getApplication()
        val c = Robolectric.buildActivity(MainLauncherActivity::class.java).create().start().resume().visible()
        val a = c.get()
        val cfg = a.resources.configuration; val dm = a.resources.displayMetrics
        val v = a.window.decorView
        v.measure(View.MeasureSpec.makeMeasureSpec(1920, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY))
        v.layout(0, 0, 1920, 1080)
        java.io.File("/tmp/abo").mkdirs(); java.io.File("/tmp/abo/facts.txt").writeText("screenWidthDp=${cfg.screenWidthDp} screenHeightDp=${cfg.screenHeightDp} densityDpi=${cfg.densityDpi} dm.density=${dm.density} scaledDensity=${dm.scaledDensity} dm.w=${dm.widthPixels} dm.h=${dm.heightPixels} fontScale=${cfg.fontScale} root=${v.width}x${v.height} Ui.p(24)=${Ui.p(a, 24f)} Ui.p(1)=${Ui.p(a,1f)}")
        c.pause().stop().destroy()
    }
}
