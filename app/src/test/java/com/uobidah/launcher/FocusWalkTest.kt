package com.uobidah.launcher
import android.view.View
import android.view.ViewGroup
import org.junit.Assert
import org.junit.Ignore
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** TEST DATA ONLY (fixture apps). Software D-pad walk via focusSearch; not TV evidence. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w960dp-h540dp-xhdpi")
class FocusWalkTest {
    private fun focusables(v: View, out: MutableList<View>) {
        if (v.visibility != View.VISIBLE) return
        if (v.isFocusable && v !is android.widget.ScrollView && v !is android.widget.HorizontalScrollView && (v.isClickable || v.isLongClickable) && v.isShown && v.width > 0 && v.height > 0) out.add(v)
        if (v is ViewGroup) for (i in 0 until v.childCount) focusables(v.getChildAt(i), out)
    }
    @Ignore("Old multi-layout system retired in v1.0.52 rebuild; new home has its own geometry tests")
    @Test fun everyFocusableReachableByDpadOnAllLayouts() {
        val ctx = org.robolectric.RuntimeEnvironment.getApplication()
        val pm = ctx.packageManager
        var fl = com.uobidah.launcher.core.FavoriteList()
        for (i in 0 until 13) {
            val pi = android.content.pm.PackageInfo(); pi.packageName = "fixture.app$i"
            val ai = android.content.pm.ApplicationInfo(); ai.packageName = pi.packageName; ai.nonLocalizedLabel = "Fixture $i"
            pi.applicationInfo = ai
            org.robolectric.Shadows.shadowOf(pm).installPackage(pi)
            fl = fl.add(pi.packageName)
        }
        val report = StringBuilder()
        val bad = ArrayList<String>()
        for (spec in com.uobidah.launcher.core.HomeLayouts.all) {
            val pr = Prefs(ctx)
            pr.saveFavorites(fl); pr.layoutIndex = spec.index; pr.showPinnedApps = true
            val c = Robolectric.buildActivity(MainLauncherActivity::class.java).create().start().resume().visible()
            org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
            val root = c.get().window.decorView as ViewGroup
            root.measure(View.MeasureSpec.makeMeasureSpec(1920, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY))
            root.layout(0, 0, 1920, 1080)
            try {
                val f = View::class.java.getDeclaredField("mAttachInfo"); f.isAccessible = true
                val ai = f.get(root); val m = ai.javaClass.getDeclaredField("mInTouchMode"); m.isAccessible = true; m.setBoolean(ai, false)
            } catch (t: Throwable) { report.append("touchmode set failed: $t\n") }
            val all = ArrayList<View>(); focusables(root, all)
            val seen = HashSet<View>()
            val queue = ArrayDeque<View>()
            all.firstOrNull()?.let { queue.add(it); seen.add(it) }
            while (queue.isNotEmpty()) {
                val v = queue.removeFirst()
                for (d in intArrayOf(View.FOCUS_UP, View.FOCUS_DOWN, View.FOCUS_LEFT, View.FOCUS_RIGHT)) {
                    val n = v.focusSearch(d)
                    if (n != null && n.isFocusable && seen.add(n)) queue.add(n)
                }
            }
            val missing0 = all.filter { it !in seen }
            // real key dispatch: from a reached sibling in the same parent, press DPAD_RIGHT/LEFT repeatedly
            // real key dispatch walk: DOWN x40 then RIGHT x15 on each row start, from the first reached focusable
            val visited = HashSet<View>()
            fun press(key: Int) {
                root.dispatchKeyEvent(android.view.KeyEvent(android.view.KeyEvent.ACTION_DOWN, key))
                root.dispatchKeyEvent(android.view.KeyEvent(android.view.KeyEvent.ACTION_UP, key))
                root.findFocus()?.let { visited.add(it) }
            }
            all.firstOrNull()?.requestFocus()
            for (n in 0 until 45) {
                press(android.view.KeyEvent.KEYCODE_DPAD_DOWN)
                val here = root.findFocus()
                var base = here
                for (r in 0 until 12) press(android.view.KeyEvent.KEYCODE_DPAD_RIGHT)
                for (r in 0 until 12) press(android.view.KeyEvent.KEYCODE_DPAD_LEFT)
                base?.requestFocus()
            }
            val missing = missing0.filter { it !in visited }
            report.append("   dispatch-visited=${visited.size}\n")
            report.append("layout ${spec.index}: focusables=${all.size} reached=${seen.size} unreachable=${missing.size}\n")
            for (m in missing) { var chain = ""; var p: android.view.ViewParent? = m.parent; var k = 0; while (p != null && k < 6) { chain += p.javaClass.simpleName + (if (p is View) "[" + p.width + "x" + p.height + (if (p.visibility != View.VISIBLE) " GONE" else "") + "]" else "") + "<"; p = p.parent; k++ }; report.append("   chain=" + chain + " kids=" + (m as? ViewGroup)?.let { g -> (0 until g.childCount).joinToString(",") { g.getChildAt(it).javaClass.simpleName + ":" + ((g.getChildAt(it) as? android.widget.TextView)?.text ?: "") } } + " size=" + m.width + "x" + m.height + " enabled=" + m.isEnabled + " focusable=" + m.isFocusable + " inTouch=" + m.isFocusableInTouchMode + "\n") }
            fun rect(v: View): android.graphics.Rect { val r = android.graphics.Rect(0, 0, v.width, v.height); root.offsetDescendantRectToMyCoords(v, r); return r }
            for (m in missing.take(1)) {
                report.append("   missing rect=" + rect(m) + "\n")
                for (v in seen.sortedBy { rect(it).top }) { val r = rect(v); if (kotlin.math.abs(r.top - rect(m).top) < 700) report.append("      reached " + v.javaClass.simpleName + " " + r + " " + ((v as? android.widget.TextView)?.text ?: "") + "\n") }
            }
            for (m in missing) report.append("   REALLY unreachable: parent=${m.parent?.javaClass?.simpleName} desc=${m.contentDescription} tag=${m.tag} kids=${(m as? ViewGroup)?.childCount}  texts=${((m as? ViewGroup)?.let { g -> (0 until g.childCount).map { c -> (g.getChildAt(c) as? android.widget.TextView)?.text } })}   ${m.javaClass.simpleName} ${(m as? android.widget.TextView)?.text ?: (m as? ViewGroup)?.let { g -> (0 until g.childCount).map { (g.getChildAt(it) as? android.widget.TextView)?.text }.filterNotNull().joinToString("/") }} @${m.left},${m.top}\n")
            if (missing.isNotEmpty()) bad.add("layout ${spec.index}: ${missing.size}")
            c.pause().stop().destroy()
        }
        java.io.File("/tmp/abo").mkdirs(); java.io.File("/tmp/abo/focus.txt").writeText(report.toString())
        Assert.assertTrue("unreachable focusables: $bad", bad.isEmpty())
    }
}
