package com.uobidah.launcher

import android.app.Activity
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.drawable.BitmapDrawable
import android.view.View
import android.view.ViewGroup
import android.view.KeyEvent
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.TextView
import com.uobidah.launcher.core.FavoriteEntry
import com.uobidah.launcher.core.FavoriteList
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Ignore
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.io.FileOutputStream
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Produces labelled 1920x1080 Robolectric simulations of the real Activity view hierarchy.
 * Fixed fixtures deliberately populate match, prayer, weather and installed-app UI pipelines.
 * These are test simulations, never TV/device screenshots or live-provider verification.
 */
@RunWith(org.robolectric.RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w1280dp-h720dp-hdpi")
class ReferenceLayoutRenderTest {
    private data class FixtureApp(val pkg: String, val label: String, val iconPath: String)

    private val fixtureApps = listOf(
        FixtureApp("com.uobidah.preview.video", "مشغل الفيديو", "/sample_icons/icon_512_amber.png"),
        FixtureApp("com.uobidah.preview.media", "الوسائط", "/sample_icons/icon_192_blue.png"),
        FixtureApp("com.uobidah.preview.files", "الملفات", "/sample_icons/icon_96_green.png")
    )

    private fun walk(view: View, visit: (View) -> Unit) {
        visit(view)
        if (view is ViewGroup) for (i in 0 until view.childCount) walk(view.getChildAt(i), visit)
    }

    private fun find(root: View, tag: String): View? {
        var found: View? = null
        walk(root) { if (found == null && it.tag == tag) found = it }
        return found
    }

    private fun findAll(root: View, tag: String): List<View> {
        val found = ArrayList<View>()
        walk(root) { if (it.tag == tag) found.add(it) }
        return found
    }

    private fun assertVisibleInScreenshot(root: View, view: View, label: String, minVisibleHeight: Int = 1) {
        val visible = Rect()
        assertTrue("$label خارج مساحة لقطة 1920×1080", view.getGlobalVisibleRect(visible))
        assertTrue("$label ظاهر جزئيًا فقط: ${visible.width()}×${visible.height()}",
            visible.width() > 0 && visible.height() >= minVisibleHeight)
    }

    private fun collectText(root: View): String {
        val out = StringBuilder()
        walk(root) { view ->
            if (view is TextView) out.append(view.text).append(' ')
            view.contentDescription?.let { out.append(it).append(' ') }
        }
        return out.toString()
    }

    private fun installFixtureApps(ctx: android.content.Context, prefs: Prefs) {
        val shadowPm = Shadows.shadowOf(ctx.packageManager)
        fixtureApps.forEach { fixture ->
            val appInfo = ApplicationInfo().apply {
                packageName = fixture.pkg
                nonLocalizedLabel = fixture.label
                name = fixture.label
                enabled = true
                flags = ApplicationInfo.FLAG_INSTALLED
            }
            val packageInfo = PackageInfo().apply {
                packageName = fixture.pkg
                applicationInfo = appInfo
                versionName = "1.0-fixture"
            }
            shadowPm.installPackage(packageInfo)
            val bitmap = javaClass.getResourceAsStream(fixture.iconPath)?.use { BitmapFactory.decodeStream(it) }
                ?: error("Missing deterministic test app icon fixture: ${fixture.iconPath}")
            shadowPm.setApplicationIcon(fixture.pkg, BitmapDrawable(ctx.resources, bitmap))
        }
        prefs.saveFavorites(FavoriteList(fixtureApps.map { FavoriteEntry(it.pkg, label = it.label) }))
        // Empty favourites suppress real provider traffic; fixture matches are injected after the
        // Activity has finished its empty-state callbacks. This keeps the screenshots deterministic.
        prefs.matchFavoriteTeams = emptyList()
    }

    private fun setField(target: Any, name: String, value: Any?) {
        val field = target.javaClass.getDeclaredField(name)
        field.isAccessible = true
        field.set(target, value)
    }

    private fun invoke(target: Any, name: String, vararg args: Any?) {
        val signature = when (name) {
            "renderCountdown", "renderHeaderInfo" -> arrayOf(LocalDateTime::class.java)
            "renderWeather" -> emptyArray()
            "renderFavorites" -> arrayOf(List::class.java, Boolean::class.javaPrimitiveType!!)
            else -> error("Unexpected fixture method: $name")
        }
        val method = target.javaClass.getDeclaredMethod(name, *signature)
        method.isAccessible = true
        method.invoke(target, *args)
    }

    private data class MatchFixtures(
        val ahly: Match,
        val madrid: Match,
        val united: Match,
        val rows: List<Triple<TeamDef, String, Match?>>
    )

    private fun matchFixtures(): MatchFixtures {
        // Static dates in Africa/Cairo keep both visible status and screenshot labels deterministic.
        fun epoch(year: Int, month: Int, day: Int, hour: Int): Long = LocalDateTime.of(year, month, day, hour, 0)
            .atZone(ZoneId.of("Africa/Cairo")).toInstant().toEpochMilli()
        val ahly = Match(
            "preview-ahly-final", "الأهلي", "بيراميدز", epoch(2026, 11, 24, 20),
            "الدوري المصري", 2, 1, status = "finished", venue = "استاد القاهرة",
            homeTeamId = "138995", awayTeamId = "egy-preview", source = "fixture"
        )
        val madrid = Match(
            "preview-madrid-next", "ريال مدريد", "إشبيلية", epoch(2030, 11, 26, 21),
            "الدوري الإسباني", null, null, status = "scheduled", venue = "سانتياغو برنابيو",
            homeTeamId = "133612", awayTeamId = "esp-preview", source = "fixture"
        )
        val united = Match(
            "preview-united-final", "مانشستر يونايتد", "توتنهام", epoch(2026, 11, 23, 20),
            "الدوري الإنجليزي", 1, 0, status = "finished", venue = "أولد ترافورد",
            homeTeamId = "133738", awayTeamId = "eng-preview", source = "fixture"
        )
        val rows = listOf(
            Triple(TeamDef("الأهلي", "Al Ahly", "Egypt", "138995"), "", ahly),
            Triple(TeamDef("ريال مدريد", "Real Madrid", "Spain", "133612"), "", madrid),
            Triple(TeamDef("مانشستر يونايتد", "Manchester United", "England", "133738"), "", united)
        )
        return MatchFixtures(ahly, madrid, united, rows)
    }

    private fun applyFixedFixtures(activity: MainLauncherActivity, layoutIndex: Int) {
        val prefs = Prefs(activity)
        prefs.cityName = "القاهرة"
        prefs.cityLat = 30.0444
        prefs.cityLon = 31.2357
        prefs.cityTimezone = "Africa/Cairo"
        prefs.layoutIndex = layoutIndex

        val fixedWeather = Weather(
            temp = 23.0, feels = 22.0, code = 1, wind = 12.0, humidity = 45,
            isDay = true, hi = 25.0, lo = 17.0,
            days = (0..4).map { offset ->
                DayForecast(
                    LocalDate.of(2026, 11, 25).plusDays(offset.toLong()),
                    25.0 - offset, 17.0 + offset, listOf(1, 2, 3, 45, 61)[offset]
                )
            },
            timezone = "Africa/Cairo"
        )
        val fixedToday = linkedMapOf(
            "Fajr" to "05:00", "Sunrise" to "06:20", "Dhuhr" to "12:00",
            "Asr" to "15:30", "Maghrib" to "18:00", "Isha" to "23:59"
        )
        val fixedTomorrow = mapOf("Fajr" to "05:01", "Sunrise" to "06:21", "Dhuhr" to "12:01", "Asr" to "15:31", "Maghrib" to "18:01", "Isha" to "23:58")
        val fixedNow = LocalDateTime.of(2026, 11, 25, 16, 45)

        // Invalidate any startup requests so their asynchronous empty/network callbacks cannot
        // overwrite the deterministic test fixture values after capture.
        setField(activity, "weatherRequestId", 10_000L)
        setField(activity, "prayerRequestId", 10_000L)
        setField(activity, "weatherLoading", false)
        setField(activity, "prayerLoading", false)
        setField(activity, "weatherRetryAt", Long.MAX_VALUE)
        setField(activity, "prayerRetryAt", Long.MAX_VALUE)
        setField(activity, "weatherUpdatedAt", System.currentTimeMillis())
        setField(activity, "weather", fixedWeather)
        setField(activity, "todayTimes", fixedToday)
        setField(activity, "tomorrowTimes", fixedTomorrow)
        setField(activity, "shownPrayerDate", LocalDate.of(2026, 11, 25))
        invoke(activity, "renderWeather")
        invoke(activity, "renderCountdown", fixedNow)
        invoke(activity, "renderHeaderInfo", fixedNow)

        val matches = matchFixtures()
        if (layoutIndex == 24) {
            val slot = find(activity.window.decorView, "r8-match-slot") as? FrameLayout
                ?: error("R8 match fixture slot not found")
            slot.removeAllViews()
            val card = MatchCards.build(
                activity, prefs,
                MatchCardModel("last", "آخر مباراة منتهية", matches.ahly, "الأهلي", "", true, "Africa/Cairo"),
                compact = true
            ).apply { tag = "r8-preview-match-card" }
            slot.addView(card, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        } else {
            invoke(activity, "renderFavorites", matches.rows, false)
        }
    }

    private fun awaitEmptyProviderCallbacks(activity: MainLauncherActivity, layoutIndex: Int) {
        // A FIFO worker barrier runs after every provider task submitted during Activity startup.
        // Its UI completion is posted after those tasks have posted their own UI callbacks, so
        // draining the main looper once gives a deterministic happens-before relationship.
        val workerBarrier = CountDownLatch(1)
        val uiCompletion = CountDownLatch(1)
        val accepted = Net.afterPendingBgTasks(
            onUiComplete = { uiCompletion.countDown() },
            onWorkerBarrier = { workerBarrier.countDown() }
        )
        assertTrue("تعذر إدراج حاجز اكتمال مهام مزودي البيانات؛ طابور الخلفية ممتلئ", accepted)
        assertTrue(
            "لم تكتمل مهام مزودي البيانات قبل حاجز FIFO للتصميم $layoutIndex",
            workerBarrier.await(30, TimeUnit.SECONDS)
        )
        Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
        assertEquals(
            "يجب أن يكتمل callback تحديث الواجهة بعد callbacks المزودين وقبل حقن بيانات الاختبار",
            0L,
            uiCompletion.count
        )
        // A final idle drains any UI callbacks queued by provider callbacks before the fixture is applied.
        Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
        val root = activity.window.decorView
        assertNotNull("Activity يجب أن تظل صالحة بعد اكتمال callbacks", root)
        val stillLoading = if (layoutIndex == 24) {
            collectText(find(root, "r8-match-slot") ?: root).contains("جاري تحميل أقرب مباراة")
        } else {
            find(root, "r9-favorites-loading") != null
        }
        assertFalse("يجب أن يكون تحديث واجهة حالة المزود قد اكتمل قبل حقن fixtures للتصميم $layoutIndex", stillLoading)
    }

    private fun assertFixtureContent(root: View, layoutIndex: Int) {
        val text = collectText(root)
        assertTrue("بيانات الطقس الثابتة غير ظاهرة", text.contains("23°"))
        if (layoutIndex == 25) {
            val forecast = find(root, "r9-weather-forecast") as? ViewGroup
                ?: throw AssertionError("صف توقعات الطقس غير موجود في صورة R9")
            assertEquals("يجب أن تحتوي صورة R9 على خمسة أيام توقعات", 5, forecast.childCount)
            assertTrue("يجب أن تكون خلايا الأيام الخمسة ظاهرة في صورة R9",
                (0 until forecast.childCount).all { forecast.getChildAt(it).visibility == View.VISIBLE && forecast.getChildAt(it).width > 0 })
            assertTrue("كل مواقيت الصلاة الخمسة يجب أن تظهر في صورة R9",
                listOf("Fajr", "Dhuhr", "Asr", "Maghrib", "Isha").all { prayer ->
                    val time = find(root, "r9-prayer-time-$prayer") as? TextView
                    time != null && time.visibility == View.VISIBLE && time.text.isNotBlank()
                })
            assertTrue("شريط تقدم الصلاة يجب أن يظهر بالكامل في صورة R9", find(root, "r9-prayer-progress")?.visibility == View.VISIBLE)
        }
        assertTrue("بيانات الصلاة الثابتة غير ظاهرة", text.contains("المتبقي على أذان"))
        assertTrue("العد التنازلي الثابت للصلاة غير ظاهر", text.contains("01:15:00"))
        assertTrue("اسم تطبيق الاختبار الأول غير ظاهر", text.contains("مشغل الفيديو"))
        assertTrue("اسم تطبيق الاختبار الثاني غير ظاهر", text.contains("الوسائط"))
        assertTrue("اسم تطبيق الاختبار الثالث غير ظاهر", text.contains("الملفات"))
        val appIcons = countTag(root, "r9-app-icon")
        if (layoutIndex == 24) {
            assertTrue("بيانات مباراة الأهلي/بيراميدز الثابتة غير ظاهرة في R8", text.contains("الأهلي") && text.contains("بيراميدز"))
            assertNotNull("بطاقة المباراة ذات البيانات الثابتة غير ظاهرة في R8", find(root, "r8-preview-match-card"))
            val pinnedIcons = findAll(root, "pinned-app-icon")
            assertTrue("يجب أن تحتوي بلاطات R8 على أيقونات تطبيقات اختبار فعلية", pinnedIcons.size >= 3)
            pinnedIcons.take(3).forEachIndexed { index, icon ->
                assertVisibleInScreenshot(root, icon, "أيقونة التطبيق المثبت ${index + 1} في R8", 24)
            }
            assertVisibleInScreenshot(root, find(root, "r8-preview-match-card")!!, "بطاقة المباراة في R8", 80)
        } else {
            assertTrue("بيانات المباريات الثابتة غير ظاهرة في R9", text.contains("الأهلي") && text.contains("ريال مدريد") && text.contains("مانشستر يونايتد"))
            val row = find(root, "r9-favorites-row") as? ViewGroup
            assertNotNull("صف المباريات الثابتة غير ظاهر في R9", row)
            assertTrue("يجب عرض ثلاث مباريات ثابتة في R9", row!!.childCount >= 3)
            assertTrue("بلاطات التطبيقات المثبتة لا تظهر كعناصر فعلية", countTag(root, "r9-app-tile") >= 3)
            assertTrue("أيقونات التطبيقات المثبتة لا تُحمّل من PackageManager", appIcons >= 3)
            findAll(root, "r9-app-tile").take(3).forEachIndexed { index, tile ->
                assertTrue("ارتفاع بلاطة التطبيق ${index + 1} لا يكفي لعرض الأيقونة والاسم", tile.height >= 128)
                assertVisibleInScreenshot(root, tile, "بلاطة التطبيق المثبت ${index + 1} في R9", 48)
            }
            findAll(root, "r9-app-icon").take(3).forEachIndexed { index, icon ->
                assertTrue("أيقونة تطبيق الاختبار ${index + 1} ليست بالحجم المتوقع", icon.width >= 64 && icon.height >= 64)
            }
            assertVisibleInScreenshot(root, row!!, "صف المباريات المفضلة في R9", 60)
            val logo = find(root, "r9-brand-logo") as? ImageView
            assertNotNull("شعار R9 غير موجود في لقطة الاختبار", logo?.drawable)
        }
    }

    private fun countTag(root: View, tag: String): Int {
        var count = 0
        walk(root) { if (it.tag == tag) count++ }
        return count
    }

    private fun render(layoutIndex: Int, fileName: String) {
        val ctx = org.robolectric.RuntimeEnvironment.getApplication()
        val prefs = Prefs(ctx)
        prefs.layoutIndex = layoutIndex
        prefs.showClockCard = true
        prefs.showWeatherCard = true
        prefs.showPrayerCard = true
        prefs.showPinnedApps = true
        prefs.favoriteAppShowNames = true
        prefs.showMatchFavorites = false
        prefs.showWifiButton = true
        prefs.showHdmiButton = true
        prefs.showSearchButton = true
        prefs.showNotificationButton = true
        prefs.duskScene = false
        prefs.pinnedInit = true
        prefs.askedPerms = true
        prefs.homeCleanV1 = true
        prefs.proHomeDesignV1 = true
        installFixtureApps(ctx, prefs)

        val controller = Robolectric.buildActivity(MainLauncherActivity::class.java).create().start().resume().visible()
        val activity: Activity = controller.get()
        awaitEmptyProviderCallbacks(activity as MainLauncherActivity, layoutIndex)
        applyFixedFixtures(activity as MainLauncherActivity, layoutIndex)
        val root = activity.window.decorView
        root.measure(
            View.MeasureSpec.makeMeasureSpec(1920, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY)
        )
        root.layout(0, 0, 1920, 1080)
        Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
        assertFixtureContent(root, layoutIndex)

        val bitmap = Bitmap.createBitmap(1920, 1080, Bitmap.Config.ARGB_8888)
        root.draw(Canvas(bitmap))
        val canvas = Canvas(bitmap)
        if (layoutIndex == 25) {
            val logo = find(root, "r9-brand-logo") as? ImageView
                ?: error("R9 brand logo missing from rendered fixture screenshot")
            check(logo.drawable != null) { "R9 brand logo drawable is null in rendered fixture screenshot" }
            val loc = IntArray(2)
            val rootLoc = IntArray(2)
            logo.getLocationOnScreen(loc)
            root.getLocationOnScreen(rootLoc)
            val x0 = (loc[0] - rootLoc[0]).coerceAtLeast(0)
            val y0 = (loc[1] - rootLoc[1]).coerceAtLeast(0)
            var goldPixels = 0
            for (y in y0 until minOf(y0 + logo.height, bitmap.height)) {
                for (x in x0 until minOf(x0 + logo.width, bitmap.width)) {
                    val color = bitmap.getPixel(x, y)
                    val red = Color.red(color); val green = Color.green(color); val blue = Color.blue(color)
                    if (red > 180 && green > 125 && green < 225 && blue < 120) goldPixels++
                }
            }
            check(goldPixels > 4) { "R9 logo drawable did not paint its distinctive gold vector mark into the screenshot" }
        }
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        p.color = 0xE607111F.toInt()
        canvas.drawRect(0f, 1024f, 1920f, 1080f, p)
        p.color = Color.WHITE
        p.textSize = 23f
        p.typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD)
        canvas.drawText("ROBOLECTRIC SIMULATION • FIXED TEST DATA • NOT A TV DEVICE SCREENSHOT", 24f, 1062f, p)
        val out = File("build/test-artifacts/reference").apply { mkdirs() }
        val output = File(out, fileName)
        FileOutputStream(output).use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        println("Robolectric simulation written: ${output.absolutePath}")
        bitmap.recycle()
        controller.pause().stop().destroy()
    }

    @Ignore("Old multi-layout system retired in v1.0.52 rebuild; new home has its own geometry tests")

    @Test fun renderDesign24AsLabeledSimulation() = render(24, "DESIGN24_R8_ROBOLECTRIC_SIMULATION_1920x1080.png")
    @Ignore("Old multi-layout system retired in v1.0.52 rebuild; new home has its own geometry tests")
    @Test fun renderDesign25AsLabeledSimulation() = render(25, "DESIGN25_R9_ROBOLECTRIC_SIMULATION_1920x1080.png")

    @Ignore("Old multi-layout system retired in v1.0.52 rebuild; new home has its own geometry tests")

    @Test fun r9DpadFocusScrollsPeekedFavoriteMatchFullyIntoView() {
        val ctx = org.robolectric.RuntimeEnvironment.getApplication()
        val prefs = Prefs(ctx)
        prefs.layoutIndex = 25
        prefs.showClockCard = true
        prefs.showWeatherCard = true
        prefs.showPrayerCard = true
        prefs.showPinnedApps = true
        prefs.showMatchFavorites = false
        prefs.showWifiButton = true
        prefs.showHdmiButton = true
        prefs.showSearchButton = true
        prefs.showNotificationButton = true
        prefs.duskScene = false
        prefs.pinnedInit = true
        prefs.askedPerms = true
        prefs.homeCleanV1 = true
        prefs.proHomeDesignV1 = true
        installFixtureApps(ctx, prefs)

        val controller = Robolectric.buildActivity(MainLauncherActivity::class.java).create().start().resume().visible()
        val activity = controller.get()
        try {
            awaitEmptyProviderCallbacks(activity, 25)
            applyFixedFixtures(activity, 25)
            val root = activity.window.decorView as ViewGroup
            repeat(2) {
                root.measure(
                    View.MeasureSpec.makeMeasureSpec(1920, View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY)
                )
                root.layout(0, 0, 1920, 1080)
                Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
            }

            val scroll = find(root, "r9-favorites-scroll") as? HorizontalScrollView
                ?: error("R9 favourites horizontal scroller missing")
            val second = find(root, "r9-favorite-match-card-1")
                ?: error("Second fixed R9 favourite match missing")
            val third = find(root, "r9-favorite-match-card-2")
                ?: error("Third fixed R9 favourite match missing")
            val before = Rect()
            val scrollBefore = Rect()
            assertTrue("نافذة تمرير المباريات غير ظاهرة", scroll.getGlobalVisibleRect(scrollBefore))
            assertTrue("البطاقة الثالثة يجب أن تظهر جزئيًا عند حافة شريط التمرير قبل التركيز", third.getGlobalVisibleRect(before))
            assertTrue("لم يتم إنشاء حالة peek جزئي للبطاقة الثالثة: ${before.width()} / ${third.width}",
                before.width() in 1 until third.width)
            assertTrue("يجب أن تكون حافة البطاقة الثالثة المقتطعة بمحاذاة نافذة التمرير (peek مقصود)",
                before.right >= scrollBefore.right - 3)

            assertTrue("البطاقة الثانية غير مرتبطة بنافذة Activity", second.isAttachedToWindow)
            assertTrue("البطاقة الثانية غير ظاهرة في الشجرة", second.isShown && second.visibility == View.VISIBLE)
            assertTrue("البطاقة الثانية ليست هدف تركيز", second.isFocusable)
            assertTrue("بطاقة المباريات يجب أن تقبل تركيز الريموت حتى إذا كانت بيئة المضيف في touch mode", second.isFocusableInTouchMode)
            val secondVisible = Rect()
            assertTrue("البطاقة الثانية لا تملك مساحة مرئية قبل طلب التركيز", second.getGlobalVisibleRect(secondVisible))
            assertEquals("البطاقة الثانية يجب أن تكون كاملة قبل بدء التنقل", second.width, secondVisible.width())
            assertTrue("البطاقة الثانية يجب أن تقبل تركيز الريموت", second.requestFocus())
            assertTrue("البطاقة الثانية لم تحصل على حالة التركيز بعد requestFocus", second.hasFocus())
            assertEquals("اتجاه يمين D-pad يجب أن ينتقل إلى البطاقة الثالثة", third, second.focusSearch(View.FOCUS_RIGHT))
            activity.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_RIGHT))
            activity.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_DPAD_RIGHT))
            Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()

            assertTrue("ضغط يمين الريموت لم يركز البطاقة الثالثة", third.hasFocus())
            val after = Rect()
            val scrollAfter = Rect()
            assertTrue("البطاقة الثالثة اختفت بعد التركيز", third.getGlobalVisibleRect(after))
            assertTrue("نافذة التمرير اختفت بعد التركيز", scroll.getGlobalVisibleRect(scrollAfter))
            assertEquals("بعد التركيز يجب أن تكون البطاقة كاملة داخل نافذة التمرير", third.width, after.width())
            assertTrue("الحافة اليسرى للبطاقة المركّز عليها ما زالت خارج نافذة التمرير", after.left >= scrollAfter.left - 1)
            assertTrue("الحافة اليمنى للبطاقة المركّز عليها ما زالت خارج نافذة التمرير", after.right <= scrollAfter.right + 1)
            assertTrue("يجب أن يتحرك شريط التمرير لإظهار البطاقة المركّز عليها", scroll.scrollX > 0)
            assertTrue("البطاقة المركّز عليها يجب أن تحتفظ بإطار التركيز", third.drawableState.contains(android.R.attr.state_focused))
            assertTrue("حاوية التمرير يجب أن تقصّ الـpeek داخل نافذتها فقط", scroll.clipChildren && scroll.clipToPadding)
            assertFalse("صف البطاقات لا يجب أن يقص إطار التركيز داخل كل بطاقة", (third.parent as ViewGroup).clipChildren)
            assertTrue("يجب ترك هامش داخلي لإطار التركيز بعيداً عن حافة القص", after.left > scrollAfter.left && after.right < scrollAfter.right)
        } finally {
            controller.pause().stop().destroy()
        }
    }
}
