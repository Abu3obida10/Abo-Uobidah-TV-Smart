package com.uobidah.launcher

import android.view.View
import android.graphics.Color
import android.content.Intent
import android.graphics.Rect
import android.view.ViewGroup
import android.widget.TextView
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * بنية R8 (DUSK_BOARD) وR9 (DASHBOARD) بالقياس الفعلي بعد measure/layout على 1920×1080، لا بقراءة الكود:
 * R8: كبسولة رأس زجاجية، ثلاث بطاقات في صف واحد بقمة موحّدة ومتجاورة بلا تداخل، وصف التطبيقات تحتها داخل حاوية زجاجية واحدة.
 * R9: بطاقة الساعة يسار بطاقة الطقس/الصلاة في صف واحد بقمة موحّدة وبلا تداخل.
 * هذا يثبت التوزيع البنيوي فقط؛ لا يثبت التطابق البصري (الألوان، الصور، الخط) ولا يغني عن لقطة من جهاز.
 * (مصدري: لم يُشغَّل في بيئة هذه الجلسة لعدم توفر Gradle/SDK.)
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w1280dp-h720dp-hdpi")
class ReferenceLayoutStructureTest {
    private fun walk(v: View, f: (View) -> Unit) { f(v); if (v is ViewGroup) for (i in 0 until v.childCount) walk(v.getChildAt(i), f) }
    private fun find(root: View, tag: String): View? { var r: View? = null; walk(root) { if (it.tag == tag && r == null) r = it }; return r }
    private fun countTag(root: View, tag: String): Int { var n = 0; walk(root) { if (it.tag == tag) n++ }; return n }
    private fun findAll(root: View, tag: String): List<View> {
        val found = ArrayList<View>()
        walk(root) { if (it.tag == tag) found.add(it) }
        return found
    }
    private fun assertFullyVisible(view: View, label: String) {
        val visible = Rect()
        assertTrue("$label غير ظاهر داخل الشاشة", view.getGlobalVisibleRect(visible))
        assertEquals("$label مقصوص أفقيًا", view.width, visible.width())
        assertEquals("$label مقصوص رأسيًا", view.height, visible.height())
    }
    private fun assertNoKidsOrGames(root: View) {
        val visibleText = StringBuilder()
        walk(root) { v ->
            if (v.visibility == View.VISIBLE && v is TextView) visibleText.append(v.text).append(' ')
            if (v.visibility == View.VISIBLE) v.contentDescription?.let { visibleText.append(it).append(' ') }
        }
        val text = visibleText.toString().lowercase(java.util.Locale.ROOT)
        assertTrue("يجب ألا تظهر أقسام أطفال أو ألعاب في تخطيطات التلفزيون: $text",
            listOf("أطفال", "الأطفال", "ألعاب", "الألعاب", "kids", "games").none { text.contains(it) })
    }
    private fun top(v: View, root: View): Int { val a = IntArray(2); val b = IntArray(2); v.getLocationInWindow(a); root.getLocationInWindow(b); return a[1] - b[1] }
    private fun left(v: View, root: View): Int { val a = IntArray(2); val b = IntArray(2); v.getLocationInWindow(a); root.getLocationInWindow(b); return a[0] - b[0] }
    private fun setField(target: Any, name: String, value: Any?) {
        val field = target.javaClass.getDeclaredField(name)
        field.isAccessible = true
        field.set(target, value)
    }
    private fun invoke(target: Any, name: String, parameterTypes: Array<out Class<*>>, vararg args: Any?) {
        val method = target.javaClass.getDeclaredMethod(name, *parameterTypes)
        method.isAccessible = true
        method.invoke(target, *args)
    }
    private fun relayout(root: View) {
        root.measure(
            View.MeasureSpec.makeMeasureSpec(1920, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY)
        )
        root.layout(0, 0, 1920, 1080)
        Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
    }
    private fun utTextColorIsOpaque(root: View): Boolean {
        var found = false
        var opaque = true
        walk(root) { view ->
            if (view is TextView && view.visibility == View.VISIBLE && view.text.isNotBlank()) {
                found = true
                if (Color.alpha(view.currentTextColor) != 255) opaque = false
            }
        }
        return found && opaque
    }

    private fun prayerArabicLabelForTest(key: String): String = when (key) {
        "Fajr" -> "الفجر"
        "Dhuhr" -> "الظهر"
        "Asr" -> "العصر"
        "Maghrib" -> "المغرب"
        "Isha" -> "العشاء"
        else -> key
    }

    private fun assertDirectChildrenFit(card: ViewGroup, label: String) {
        for (i in 0 until card.childCount) {
            val child = card.getChildAt(i)
            if (child.visibility != View.VISIBLE) continue
            val childName = child.tag ?: child.javaClass.simpleName
            assertTrue("$label: العنصر $childName مقصوص من اليسار", child.left >= card.paddingLeft - 2)
            assertTrue("$label: العنصر $childName مقصوص من اليمين", child.right <= card.width - card.paddingRight + 2)
            assertTrue("$label: العنصر $childName مقصوص من الأعلى", child.top >= card.paddingTop - 2)
            assertTrue(
                "$label: العنصر $childName مقصوص أسفل البطاقة (${child.bottom} > ${card.height - card.paddingBottom})",
                child.bottom <= card.height - card.paddingBottom + 2
            )
        }
    }

    private fun launch(template: com.uobidah.launcher.core.HomeLayoutTemplate, favoritesSessionToken: String? = null, onActivityCreated: (MainLauncherActivity) -> Unit = {}): ViewGroup {
        val ctx = org.robolectric.RuntimeEnvironment.getApplication()
        val spec = com.uobidah.launcher.core.HomeLayouts.all.first { it.template == template }
        val pr = Prefs(ctx)
        pr.layoutIndex = spec.index; pr.showPrayerCard = true; pr.showWeatherCard = true; pr.showClockCard = true; pr.showPinnedApps = false
        pr.showMatchFavorites = false // يجب أن يفرض قالب R9 إظهار قسمه دون الاعتماد على هذا المفتاح
        pr.matchFavoriteTeams = emptyList() // لا نطلق طلبات مباريات حقيقية في اختبار الهيكل.
        pr.showWifiButton = true; pr.showHdmiButton = true; pr.showSearchButton = true; pr.showNotificationButton = true
        pr.duskScene = false // خلفية R8 يجب أن تظهر من اختيار التصميم نفسه، لا من هذا المفتاح الاختياري
        pr.mosqueScene = template == com.uobidah.launcher.core.HomeLayoutTemplate.DUSK_BOARD // اختبر أولوية R8 على مشهد آخر
        pr.themeIndex = if (template == com.uobidah.launcher.core.HomeLayoutTemplate.DUSK_BOARD) 10 else 0 // R8 على ثيم فاتح، وR9 على ثيم داكن
        pr.pinnedInit = true; pr.askedPerms = true; pr.homeCleanV1 = true; pr.proHomeDesignV1 = true
        val launchIntent = Intent(ctx, MainLauncherActivity::class.java).apply {
            favoritesSessionToken?.let { putExtra(LauncherTestHooks.FAVORITES_SESSION_EXTRA, it) }
        }
        val c = Robolectric.buildActivity(MainLauncherActivity::class.java, launchIntent).create().start().resume().visible()
        Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
        onActivityCreated(c.get())
        val root = c.get().window.decorView as ViewGroup
        repeat(2) {
            root.measure(View.MeasureSpec.makeMeasureSpec(1920, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY))
            root.layout(0, 0, 1920, 1080)
            Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
        }
        return root
    }

    private fun assertRowSideBySide(root: View, row: ViewGroup, what: String) {
        val kids = (0 until row.childCount).map { row.getChildAt(it) }.filter { it.visibility == View.VISIBLE }
        assertTrue("$what: يلزم عنصران ظاهران على الأقل (وُجد ${kids.size})", kids.size >= 2)
        val tops = kids.map { top(it, root) }
        assertTrue("$what: قمم العناصر غير موحّدة $tops", (tops.maxOrNull()!! - tops.minOrNull()!!) <= 3)
        val lefts = kids.map { left(it, root) }
        assertEquals("$what: ترتيب العناصر من اليسار لليمين مضطرب $lefts", lefts.sorted(), lefts)
        for (i in 0 until kids.size - 1) {
            val rightEdge = left(kids[i], root) + kids[i].width
            assertTrue("$what: تداخل بين ${i + 1} و${i + 2} ($rightEdge > ${left(kids[i + 1], root)})", rightEdge <= left(kids[i + 1], root) + 1)
        }
        kids.forEach { assertTrue("$what: عنصر بلا مساحة", it.width > 0 && it.height > 0) }
        val last = kids.last()
        assertTrue("$what: عنصر يخرج من الشاشة (يمين=${left(last, root) + last.width})", left(last, root) + last.width <= 1920)
    }

    @Test fun r8DuskBoardHasGlassPillThreeCardRowAndGlassAppsBox() {
        val root = launch(com.uobidah.launcher.core.HomeLayoutTemplate.DUSK_BOARD)
        val pill = find(root, "r8-header-pill"); assertNotNull("كبسولة الرأس الزجاجية غير موجودة", pill)
        assertNotNull("خلفية الكبسولة", pill!!.background)
        val triple = find(root, "r8-triple") as? ViewGroup; assertNotNull("صف البطاقات الثلاث غير موجود", triple)
        assertRowSideBySide(root, triple!!, "R8 البطاقات الثلاث")
        val apps = find(root, "r8-apps-glass") ?: throw AssertionError("حاوية التطبيقات الزجاجية غير موجودة")
        assertNotNull("خلفية حاوية التطبيقات", apps.background)
        val appsGlass = apps.background as? android.graphics.drawable.GradientDrawable
        assertNotNull("حاوية التطبيقات يجب أن تستخدم سطحًا زجاجيًا قابلًا لمرور الخلفية", appsGlass)
        val appsGlassColor = appsGlass!!.color?.defaultColor ?: error("R8 apps glass fill color missing")
        assertTrue("شفافية R8 يجب أن تسمح بظهور الغروب مع بقاء طبقة مقروءة: alpha=${Color.alpha(appsGlassColor)}",
            Color.alpha(appsGlassColor) in 165..180)
        assertTrue("نصوص R8 يجب أن تبقى معتمة ومقروءة فوق الزجاج", utTextColorIsOpaque(apps!!))
        assertTrue("صف التطبيقات يجب أن يكون تحت البطاقات الثلاث", top(apps, root) >= top(triple, root) + triple.height - 2)
        assertEquals("R8 يجب أن يعرض ثلاث بطاقات فقط", 3, triple.childCount)
        val cardTags = (0 until triple.childCount).map { triple.getChildAt(it).tag }
        assertTrue("بطاقة البث غير موجودة", "r8-live-card" in cardTags)
        assertTrue("بطاقة المباراة الواحدة غير موجودة", "r8-single-match-card" in cardTags)
        assertTrue("بطاقة الصلاة غير موجودة", "r8-prayer-card" in cardTags)
        val heights = (0 until triple.childCount).map { triple.getChildAt(it).height }
        assertTrue("ارتفاعات البطاقات الثلاث يجب أن تكون متساوية: $heights", heights.maxOrNull()!! - heights.minOrNull()!! <= 2)
        for (tag in listOf("r8-live-card", "r8-single-match-card", "r8-prayer-card")) {
            val card = find(root, tag) as ViewGroup
            assertTrue("بطاقة $tag يجب أن تكون ذات ارتفاع قابل للعرض", card.height >= 220)
            for (i in 0 until card.childCount) {
                val child = card.getChildAt(i)
                if (child.visibility == View.VISIBLE) {
                    assertTrue("محتوى $tag يتجاوز الحافة السفلية للبطاقة", child.bottom <= card.height - card.paddingBottom + 2)
                }
            }
        }
        val sceneRoot = find(root, "r8-home-root")
        assertNotNull("جذر تصميم R8 غير موجود", sceneRoot)
        assertTrue("خلفية الغروب يجب أن تظهر تلقائيًا مع التصميم 24 حتى duskScene=false", sceneRoot!!.background is DuskSceneBg)
        val appSection = find(root, "r8-apps-section"); assertNotNull("قسم التطبيقات المثبتة موجود بعد صف البطاقات", appSection)
        assertEquals("صف التطبيقات يظل ظاهرًا حتى لو كان الإخفاء العام مضبوطًا", View.VISIBLE, appSection!!.visibility)
        val appVisible = Rect()
        assertTrue("صف التطبيقات يجب أن يدخل مساحة العرض الأساسية من دون التمرير الأولي", appSection.getGlobalVisibleRect(appVisible) && appVisible.height() > 0)
        assertNotNull("قائمة جانبية واحدة فقط", find(root, "home-side-rail"))
        assertEquals("لا تضف شريط تنقل سفليًا", 1, countTag(root, "home-side-rail"))
        assertEquals("لا يوجد شريط سفلي في R8", 0, countTag(root, "home-bottom-nav"))
        assertNoKidsOrGames(root)
        assertTrue("الكبسولة فوق البطاقات", top(pill, root) + pill.height <= top(triple, root))
        // R8: التحية والمدينة الكبيرة والتاريخ وكبسولة الطقس/الصلاة في كتلة يمين تحت الكبسولة، لا داخلها
        val info = find(root, "r8-info-block") as? ViewGroup; assertNotNull("كتلة المعلومات اليمنى غير موجودة", info)
        assertTrue("كتلة المعلومات تحت الكبسولة", top(info!!, root) >= top(pill, root) + pill.height - 2)
        assertTrue("كتلة المعلومات فوق البطاقات الثلاث", top(info, root) + info.height <= top(triple, root) + 2)
        var inPill = false; walk(pill) { if (it.tag == "r8-city-big") inPill = true }
        assertTrue("اسم المدينة الكبير يجب ألا يكون داخل الكبسولة", !inPill)
        val city = find(root, "r8-city-big") as android.widget.TextView
        assertEquals("اسم المدينة الكبير يطابق مدينة الإعدادات", Prefs(org.robolectric.RuntimeEnvironment.getApplication()).cityName, city.text.toString())
        assertTrue("اسم المدينة على يمين الشاشة", left(city, root) + city.width / 2 > 960)
        val strip = find(root, "r8-weather-prayer-strip") as? android.widget.TextView
        assertNotNull("شريط الطقس والصلاة تحت اسم المدينة غير موجود", strip)
        assertEquals("الشريط يجب أن يظل ظاهرًا أثناء جلب البيانات", View.VISIBLE, strip!!.visibility)
        assertTrue("شريط الطقس والصلاة يجب أن يأتي أسفل اسم المدينة", top(strip, root) >= top(city, root) + city.height)
        val rail = find(root, "home-side-rail")!!
        val appRight = left(appSection, root) + appSection.width
        assertTrue("صف التطبيقات يجب ألا يدخل أسفل القائمة الجانبية", appRight <= left(rail, root))
    }

    @Test fun r9DashboardClockWeatherPrayerShareOneRowInOrder() {
        val allowFavoritesLoad = CountDownLatch(1)
        val favoritesLoadEntered = CountDownLatch(1)
        val favoritesCallbackQueued = CountDownLatch(1)
        val favoritesRendered = CountDownLatch(1)
        val favoritesSessionToken = "r9-structure-${System.nanoTime()}"
        LauncherTestHooks.favoritesSessionToken = favoritesSessionToken
        LauncherTestHooks.beforeFavoritesLoad = { activity ->
            if (activity.intent.getStringExtra(LauncherTestHooks.FAVORITES_SESSION_EXTRA) == favoritesSessionToken) {
                favoritesLoadEntered.countDown()
                check(allowFavoritesLoad.await(30, TimeUnit.SECONDS)) { "Timed out waiting to release R9 favourites fixture" }
            }
        }
        LauncherTestHooks.afterFavoritesCallbackQueued = { activity ->
            if (activity.intent.getStringExtra(LauncherTestHooks.FAVORITES_SESSION_EXTRA) == favoritesSessionToken) favoritesCallbackQueued.countDown()
        }
        LauncherTestHooks.afterFavoritesRendered = { activity ->
            if (activity.intent.getStringExtra(LauncherTestHooks.FAVORITES_SESSION_EXTRA) == favoritesSessionToken) favoritesRendered.countDown()
        }
        try {
        var launchedActivity: MainLauncherActivity? = null
        val root = launch(com.uobidah.launcher.core.HomeLayoutTemplate.DASHBOARD, favoritesSessionToken) { launchedActivity = it }
        assertTrue(
            "لم تدخل مهمة تحميل المفضلة قبل فحص حالة التحميل؛ لا يمكن افتراض وجود placeholder قبل بدء العمل",
            favoritesLoadEntered.await(30, TimeUnit.SECONDS)
        )
        val row = find(root, "r9-cards-row") as? ViewGroup
            ?: throw AssertionError("صف بطاقات R9 غير موجود")
        assertNotNull("بطاقة الساعة غير موجودة", find(root, "r9-clock-card"))
        assertTrue("بطاقة الساعة أول عنصر يسارًا", row.getChildAt(0).tag == "r9-clock-card")
        assertRowSideBySide(root, row, "R9 الساعة/الطقس/الصلاة")
        val moon = find(root, "r9-moon"); assertNotNull("قمر بطاقة الطقس غير موجود", moon)
        assertTrue("القمر بلا مساحة", moon!!.width > 0 && moon.height > 0)
        var moonInClock = false; walk(find(root, "r9-clock-card")!!) { if (it.tag == "r9-moon") moonInClock = true }
        assertTrue("القمر يجب ألا يكون في بطاقة الساعة", !moonInClock)
        val brand = find(root, "r9-brand-header") as? ViewGroup
        assertNotNull("رأس العلامة R9 غير موجود", brand)
        assertTrue("ترويسة العلامة يجب أن تسبق صف البطاقات", top(brand!!, root) < top(row, root))
        val logo = find(root, "r9-brand-logo") as? android.widget.ImageView
        assertNotNull("الشعار الدائري غير موجود", logo)
        assertNotNull("الشعار يجب أن يستخدم أيقونة التطبيق الفعلية", logo!!.drawable)
        val installedAppIcon = logo.context.packageManager.getApplicationInfo(logo.context.packageName, 0).icon
        assertEquals("مورد الشعار يجب أن يطابق أيقونة التطبيق المعلنة في Manifest", R.drawable.ic_launcher, installedAppIcon)
        assertTrue("Drawable الشعار يجب أن يحمل محتوى مرئيًا", logo.drawable!!.intrinsicWidth > 0 && logo.drawable!!.intrinsicHeight > 0)
        assertTrue("الشعار الدائري بلا قياس", logo.width >= 60 && logo.height >= 60)
        assertNotNull("أزرار الهيدر المعنونة غير موجودة", find(root, "r9-header-actions"))
        for (tag in listOf("r9-action-network", "r9-action-input", "r9-action-search", "r9-action-notifications")) {
            assertNotNull("زر الهيدر أو اسمه غير ظاهر: $tag", find(root, tag))
        }
        val apps = find(root, "r9-apps-section") ?: throw AssertionError("قسم التطبيقات المثبتة غير موجود")
        assertTrue("قسم التطبيقات يجب أن يأتي بعد صف الساعة/الطقس/الصلاة", top(apps, root) >= top(row, root) + row.height - 2)
        val appGrid = find(root, "r9-apps-grid") as? ViewGroup
        assertNotNull("شبكة تطبيقات R9 غير موجودة", appGrid)
        assertEquals("شبكة التطبيقات تظل ظاهرة حتى عند إيقاف الإظهار العام", View.VISIBLE, appGrid!!.visibility)
        assertTrue("شبكة التطبيقات يجب أن تستخدم بلاطات قصيرة قابلة للعرض", appGrid.childCount >= 1)
        val dashboardTiles = findAll(root, "r9-app-tile") + findAll(root, "r9-add-app-tile")
        assertTrue("يجب أن توجد بلاطات تطبيقات قابلة للتركيز", dashboardTiles.isNotEmpty())
        dashboardTiles.forEach { tile ->
            assertTrue("ارتفاع بلاطة R9 يجب أن يستوعب الأيقونة والاسم: ${tile.height}", tile.height >= 128)
        }
        findAll(root, "r9-app-icon").forEach { icon ->
            assertTrue("أيقونة تطبيق R9 صغيرة أو مقصوصة: ${icon.width}x${icon.height}", icon.width >= 64 && icon.height >= 64)
        }
        assertTrue("صف بطاقات R9 قصير ويقص التوقعات أو الصلاة: ${row.height}", row.height >= 370)
        assertTrue("صف بطاقات R9 يجب أن يبقى ضمن الارتفاع المصمم", row.height <= 410)
        assertTrue("شبكة التطبيقات يجب أن تأتي بعد صف الساعة/الطقس/الصلاة", top(appGrid, root) >= top(row, root) + row.height - 2)
        assertNotNull("قسم المباريات المفضلة لا يجوز أن يعتمد على مفتاح إظهار قديم", find(root, "r9-favorites-section"))
        val favoriteContent = find(root, "r9-favorites-content")
        assertNotNull("محتوى المباريات المفضلة غير موجود", favoriteContent)
        assertTrue("قسم المباريات المفضلة يجب أن يأتي بعد التطبيقات", top(favoriteContent!!, root) >= top(apps, root) + apps.height - 2)

        // Feed long but realistic Arabic values through the actual weather/prayer views. The
        // assertions target clipping/layout, not whether a live provider happens to return data.
        val activity = launchedActivity ?: throw AssertionError("لم يُحتفظ بمرجع Activity الذي أنشأ DecorView")
        val fixedWeather = Weather(
            temp = 23.0, feels = 22.0, code = 2, wind = 14.0, humidity = 73,
            isDay = true, hi = 29.0, lo = 18.0,
            days = (0..4).map { DayForecast(LocalDate.of(2026, 11, 25).plusDays(it.toLong()), 29.0 - it, 18.0 + it, 2) },
            timezone = "Africa/Cairo"
        )
        setField(activity, "weather", fixedWeather)
        setField(activity, "weatherUpdatedAt", System.currentTimeMillis())
        invoke(activity, "renderWeather", emptyArray())
        invoke(activity, "renderForecast", arrayOf(Weather::class.java), fixedWeather)
        val city = find(root, "r9-weather-city") as? TextView
        assertNotNull("اسم المدينة في بطاقة الطقس غير موجود", city)
        assertTrue("خط المدينة R9 صغير عن المقاس المطلوب", city!!.textSize >= Ui.p(root.context, 22f) - 1f)
        val condition = find(root, "r9-weather-condition") as? TextView
        assertNotNull("وصف حالة الطقس غير موجود", condition)
        assertTrue("خط حالة الطقس R9 صغير عن المقاس المطلوب", condition!!.textSize >= Ui.p(root.context, 20f) - 1f)
        val detail = find(root, "r9-weather-detail") as? TextView
        assertNotNull("تفاصيل الطقس غير موجودة", detail)
        assertTrue("خط تفاصيل الطقس R9 صغير عن المقاس المطلوب", detail!!.textSize >= Ui.p(root.context, 15f) - 1f)
        val forecast = find(root, "r9-weather-forecast") as? ViewGroup
        assertNotNull("صف توقعات الطقس غير موجود", forecast)
        val forecastRowView = forecast ?: throw AssertionError("صف توقعات الطقس غير موجود")
        assertEquals("يجب عرض أيام التوقعات الخمسة", 5, forecastRowView.childCount)

        val fixedToday = linkedMapOf(
            "Fajr" to "05:00", "Dhuhr" to "12:00", "Asr" to "15:30",
            "Maghrib" to "18:00", "Isha" to "23:59"
        )
        setField(activity, "todayTimes", fixedToday)
        setField(activity, "tomorrowTimes", mapOf("Fajr" to "05:01", "Dhuhr" to "12:01", "Asr" to "15:31", "Maghrib" to "18:01", "Isha" to "23:58"))
        invoke(activity, "renderCountdown", arrayOf(LocalDateTime::class.java), LocalDateTime.of(2026, 11, 25, 16, 45))
        relayout(root)

        val weatherCard = find(root, "r9-weather-card") as? ViewGroup
            ?: throw AssertionError("بطاقة الطقس R9 غير موجودة")
        assertDirectChildrenFit(weatherCard, "بطاقة الطقس R9")
        assertFullyVisible(weatherCard, "بطاقة الطقس R9")
        val forecastRow = forecast ?: throw AssertionError("صف توقعات الطقس R9 غير موجود")
        assertFullyVisible(forecastRow, "صف توقعات الطقس R9")
        listOf("r9-weather-city", "r9-weather-temperature", "r9-weather-condition", "r9-weather-detail").forEach { tag ->
            val textView = find(root, tag) ?: throw AssertionError("عنصر الطقس $tag غير موجود")
            assertFullyVisible(textView, "عنصر الطقس $tag")
        }
        val forecastCells = (0 until forecastRow.childCount).map { forecastRow.getChildAt(it) as ViewGroup }
        forecastCells.forEachIndexed { index, cell ->
            assertTrue("خلية توقعات اليوم ${index + 1} بلا قياس", cell.width > 0 && cell.height > 0)
            val dayName = find(cell, "r9-weather-forecast-day-name") as? TextView
                ?: throw AssertionError("اسم يوم التوقعات ${index + 1} غير موجود")
            assertTrue("خط اسم اليوم R9 صغير عن المقاس المطلوب", dayName.textSize >= Ui.p(root.context, 15f) - 1f)
            assertEquals("الوصف الكامل لاسم اليوم يجب أن يبقى متاحًا", dayName.text.toString(), dayName.contentDescription.toString())
            val forecastCondition = find(cell, "r9-weather-forecast-condition") as? TextView
                ?: throw AssertionError("وصف حالة اليوم ${index + 1} غير موجود")
            assertTrue("خط وصف التوقعات R9 صغير عن المقاس المطلوب", forecastCondition.textSize >= Ui.p(root.context, 18f) - 1f)
            val forecastRange = find(cell, "r9-weather-forecast-range") as? TextView
                ?: throw AssertionError("مدى الحرارة لليوم ${index + 1} غير موجود")
            assertTrue("خط درجات التوقعات R9 صغير عن المقاس المطلوب", forecastRange.textSize >= Ui.p(root.context, 17f) - 1f)
            val forecastVisible = Rect()
            assertTrue("خلية توقعات اليوم ${index + 1} غير ظاهرة بالكامل", cell.getGlobalVisibleRect(forecastVisible))
            assertEquals("عرض خلية توقعات اليوم ${index + 1} مقصوص", cell.width, forecastVisible.width())
            for (j in 0 until cell.childCount) {
                val label = cell.getChildAt(j)
                assertTrue("نص توقعات اليوم ${index + 1} خارج الخلية", label.bottom <= cell.height + 2)
                val labelVisible = Rect()
                assertTrue("نص توقعات اليوم ${index + 1} غير مرئي بالكامل", label.getGlobalVisibleRect(labelVisible))
                assertEquals("نص توقعات اليوم ${index + 1} مقصوص رأسيًا", label.height, labelVisible.height())
            }
        }
        // Exercise compact and long Arabic values through the same measured view tree.
        val textVariants = listOf(
            Triple("القاهرة", "صحو", "العظمى ٢٥° والصغرى ١٧°"),
            Triple("مدينة سيدي جابر - محافظة الإسكندرية", "غائم جزئيًا مع احتمال أمطار خفيفة", "العظمى ٢٩° | الصغرى ١٨° | الرطوبة ٧٣٪"),
            Triple("مدينة سيدي جابر - حي شرق - محافظة الإسكندرية - جمهورية مصر العربية", "غائم جزئيًا مع احتمال سقوط أمطار خفيفة ورياح متقطعة طوال اليوم", "العظمى ٢٩° | الصغرى ١٨° | الرطوبة ٧٣٪ | سرعة الرياح ١٤ كم/س | آخر تحديث منذ ٣ دقائق")
        )
        textVariants.forEachIndexed { index, (cityText, conditionText, detailText) ->
            city!!.text = "📍 $cityText"
            condition!!.text = conditionText
            detail!!.text = detailText
            city.contentDescription = cityText
            condition.contentDescription = conditionText
            detail.contentDescription = detailText
            forecastCells.forEachIndexed { cellIndex, cell ->
                val forecastCondition = find(cell, "r9-weather-forecast-condition") as? TextView
                    ?: throw AssertionError("وصف حالة الطقس لليوم ${cellIndex + 1} غير موجود")
                forecastCondition.text = when (index) {
                    0 -> "صحو"
                    1 -> "غائم جزئيًا مع احتمال أمطار خفيفة"
                    else -> "غائم جزئيًا مع احتمال سقوط أمطار خفيفة ورياح متقطعة طوال اليوم"
                }
                forecastCondition.contentDescription = forecastCondition.text
            }
            relayout(root)
            assertDirectChildrenFit(weatherCard, "بطاقة الطقس R9 مع نصوص متغيرة $index")
            assertTrue("صف التوقعات اختفى مع طول النص $index", forecastRowView.getGlobalVisibleRect(Rect()))
            forecastCells.forEachIndexed { cellIndex, cell ->
                val visible = Rect()
                assertTrue("إحدى خلايا التوقعات اختفت مع طول النص $index", cell.getGlobalVisibleRect(visible))
                assertEquals("إحدى خلايا التوقعات قُصت أفقيًا مع طول النص $index", cell.width, visible.width())
                val forecastCondition = find(cell, "r9-weather-forecast-condition") as? TextView
                    ?: throw AssertionError("وصف حالة الطقس لليوم ${cellIndex + 1} غير موجود")
                val conditionVisible = Rect()
                assertTrue("وصف توقعات اليوم ${cellIndex + 1} غير ظاهر مع طول النص $index", forecastCondition.getGlobalVisibleRect(conditionVisible))
                assertEquals("وصف توقعات اليوم ${cellIndex + 1} مقصوص رأسيًا", forecastCondition.height, conditionVisible.height())
                assertTrue("وصف توقعات اليوم ${cellIndex + 1} تجاوز عدد أسطره", forecastCondition.maxLines in 1..2)
            }
        }
        val prayerCard = find(root, "r9-prayer-card") as? ViewGroup
            ?: throw AssertionError("بطاقة مواقيت الصلاة R9 غير موجودة")
        assertFullyVisible(prayerCard, "بطاقة مواقيت الصلاة R9")
        val prayerGrid = find(root, "r9-prayer-grid") as? ViewGroup
            ?: throw AssertionError("شبكة مواقيت الصلاة R9 غير موجودة")
        assertFullyVisible(prayerGrid, "شبكة مواقيت الصلاة R9")
        val prayerKeys = listOf("Fajr", "Dhuhr", "Asr", "Maghrib", "Isha")
        val prayerTimeVariants = listOf("٥:٠٧ ص", "١٢:٤٥ م", "١٥:٣٠", "٦:٠٠ مساءً", "١١:٥٩ مساءً")
        prayerKeys.forEachIndexed { index, key ->
            val cell = find(root, "r9-prayer-cell-$key") as? ViewGroup
            assertNotNull("وقت صلاة $key غير معروض", cell)
            val prayerName = find(root, "r9-prayer-name-$key") as? TextView
                ?: throw AssertionError("اسم الصلاة $key غير موجود")
            assertTrue("خط اسم الصلاة $key صغير عن المقاس المطلوب", prayerName.textSize >= Ui.p(root.context, 18f) - 1f)
            assertEquals("اسم الصلاة الكامل يجب أن يبقى متاحًا", prayerArabicLabelForTest(key), prayerName.contentDescription.toString())
            val time = find(root, "r9-prayer-time-$key") as? TextView
            assertNotNull("قيمة وقت صلاة $key غير موجودة", time)
            assertTrue("خط وقت الصلاة $key صغير عن المقاس المطلوب", time!!.textSize >= Ui.p(root.context, 24f) - 1f)
            val prayerSub = find(root, "r9-prayer-sub-$key") as? TextView
                ?: throw AssertionError("الوصف الفرعي للصلاة $key غير موجود")
            assertTrue("خط وصف الصلاة $key صغير عن المقاس المطلوب", prayerSub.textSize >= Ui.p(root.context, 13f) - 1f)
            time.text = prayerTimeVariants[index]
            time.contentDescription = "${prayerArabicLabelForTest(key)} ${prayerTimeVariants[index]}"
            assertTrue("وقت صلاة $key يجب أن يبقى في سطر واحد", time.maxLines == 1)
            assertTrue(
                "وقت صلاة $key أطول من مساحة النص ويُقص قبل عرضه كاملًا: text=${time.text}, measured=${time.paint.measureText(time.text.toString())}, width=${time.width}",
                time.paint.measureText(time.text.toString()) <= time.width.toFloat() + 1f
            )
            val timeRect = Rect()
            assertTrue("نص وقت صلاة $key غير ظاهر بالكامل", time.getGlobalVisibleRect(timeRect))
            assertEquals("نص وقت صلاة $key مقصوص رأسيًا", time.height, timeRect.height())
            val cellRect = Rect()
            assertTrue("خلية صلاة $key غير ظاهرة", cell!!.getGlobalVisibleRect(cellRect))
            assertEquals("خلية صلاة $key مقصوصة أفقيًا", cell.width, cellRect.width())
            assertEquals("خلية صلاة $key مقصوصة رأسيًا", cell.height, cellRect.height())
        }
        relayout(root)
        val progress = find(root, "r9-prayer-progress")
        assertNotNull("شريط تقدم الصلاة غير موجود", progress)
        assertTrue("شريط تقدم الصلاة بلا مساحة", progress!!.width > 0 && progress.height > 0)
        assertFullyVisible(progress, "شريط تقدم الصلاة R9")
        val progressFill = find(root, "r9-prayer-progress-fill") ?: throw AssertionError("الجزء المكتمل من شريط تقدم الصلاة غير موجود")
        val progressRest = find(root, "r9-prayer-progress-rest") ?: throw AssertionError("الجزء المتبقي من شريط تقدم الصلاة غير موجود")
        assertTrue("الجزء المكتمل من شريط الصلاة بلا مساحة مرئية", progressFill.width > 0 && progressFill.height > 0)
        assertTrue("الجزء المتبقي من شريط الصلاة بلا مساحة مرئية", progressRest.width > 0 && progressRest.height > 0)
        val prayerNext = find(root, "r9-prayer-next") ?: throw AssertionError("نص الصلاة القادمة غير ظاهر")
        val prayerCountdown = find(root, "r9-prayer-countdown") ?: throw AssertionError("العد التنازلي للصلاة غير ظاهر")
        assertFullyVisible(prayerNext, "اسم الصلاة القادمة R9")
        assertFullyVisible(prayerCountdown, "العد التنازلي للصلاة R9")
        val progressFooter = find(root, "r9-prayer-progress-footer") ?: throw AssertionError("تذييل تقدم الصلاة غير موجود")
        assertFullyVisible(progressFooter, "تذييل تقدم الصلاة R9")
        assertDirectChildrenFit(prayerCard!!, "بطاقة الصلاة R9")

        val favRow = find(root, "r9-favorites-row")
        assertNotNull("صف المباريات المفضلة غير مبني", favRow)
        assertEquals("صف المباريات المفضلة لا يجوز إخفاؤه", View.VISIBLE, favRow!!.visibility)
        val loading = find(root, "r9-favorites-loading")
            ?: throw AssertionError("حالة تحميل المفضلة اختفت قبل انتهاء المهمة؛ تم استبدالها مبكرًا أو لم تبدأ عملية الجلب")
        assertEquals("حالة التحميل يجب أن تكون مرئية أثناء انتظار المزوّد", View.VISIBLE, loading.visibility)
        assertTrue("placeholder التحميل يجب أن يكون داخل صف المفضلة نفسه", loading.parent === favRow)
        assertEquals("لا يجوز أن تنفذ واجهة المفضلة قبل إطلاق بوابة التحميل", 1L, favoritesRendered.count)
        assertEquals("لا يجوز وضع callback الاكتمال في الطابور قبل انتهاء قراءة البيانات", 1L, favoritesCallbackQueued.count)
        allowFavoritesLoad.countDown()
        assertTrue("مهمة المفضلة لم تضع callback اكتمالها على واجهة المستخدم", favoritesCallbackQueued.await(30, TimeUnit.SECONDS))
        Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
        assertEquals("callback المفضلة يجب أن يكتمل بعد تصريف طابور الواجهة", 0L, favoritesRendered.count)
        assertNull("يجب إزالة حالة التحميل بعد اكتمال جلب المفضلة ورسم الحالة النهائية", find(root, "r9-favorites-loading"))
        assertEquals("يجب أن يعرض الصف حالة فارغة/نهائية بعد انتهاء التحميل", 1, (find(root, "r9-favorites-row") as ViewGroup).childCount)
        assertNotNull("قائمة R9 الجانبية الواحدة غير موجودة", find(root, "home-side-rail"))
        assertEquals("يجب ألا يتكرر شريط جانبي", 1, countTag(root, "home-side-rail"))
        assertEquals("لا تضف شريط تنقل سفليًا", 0, countTag(root, "home-bottom-nav"))
        assertNoKidsOrGames(root)
        } finally {
            allowFavoritesLoad.countDown()
            LauncherTestHooks.clearFavoritesSession()
        }
    }
}
