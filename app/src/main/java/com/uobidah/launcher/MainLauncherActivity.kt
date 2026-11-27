package com.uobidah.launcher

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Rect
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.StateListDrawable
import android.view.ViewOutlineProvider
import android.graphics.drawable.Drawable
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.util.TypedValue
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.TextStyle
import java.util.Locale
import com.uobidah.launcher.core.HomeLayoutTemplate

class MainLauncherActivity : BaseActivity() {
    override val solidScreens: Boolean get() = false

    private val handler = Handler(Looper.getMainLooper())

    private lateinit var tvTime: TextView
    private lateinit var tvAmPm: TextView
    private lateinit var tvDate: TextView
    private lateinit var tvHijri: TextView
    private lateinit var tvCity: TextView
    private lateinit var tvTemp: TextView
    private lateinit var tvWDesc: TextView
    private lateinit var tvWDetail: TextView
    private lateinit var tvPNext: TextView
    private lateinit var tvPCount: TextView
    private var tvPAdhan: TextView? = null
    private var tvPQibla: TextView? = null
    private lateinit var tvWifi: TextView
    private lateinit var pinnedRow: LinearLayout
    private lateinit var weatherCard: LinearLayout
    private lateinit var prayerCard: LinearLayout
    private var forecastRow: LinearLayout? = null
    private var favVertical = false
    private var progFill: View? = null
    private var progRest: View? = null
    private var favRow: LinearLayout? = null
    private var favHostListening = false
    private var lastFavRender: Pair<List<Triple<TeamDef, String, Match?>>, Boolean>? = null
    private var favLoadedAt = 0L
    private val prayerNames = LinkedHashMap<String, TextView>()
    private val prayerTimes = LinkedHashMap<String, TextView>()
    private val prayerSubs = LinkedHashMap<String, TextView>()

    private var weather: Weather? = null
    private var todayTimes: Map<String, String>? = null
    private var tomorrowTimes: Map<String, String>? = null
    private var shownDate: LocalDate? = null
    private var lastWeather = 0L
    private var weatherRetryAt = 0L
    private var weatherLoading = false
    private var weatherRequestId = 0L
    private var weatherUpdatedAt = 0L
    private var prayerRequestId = 0L
    private var prayerLoading = false
    private var prayerRetryAt = 0L
    private var prayerRequestedDate: LocalDate? = null
    private var shownPrayerDate: LocalDate? = null
    private var tickCount = 0
    private var lastClockText = ""
    private var lastHotPrayer = ""
    private var sig = ""
    private var cityKey = ""

    private val ticker = object : Runnable {
        override fun run() {
            tick()
            handler.postDelayed(this, 1000)
        }
    }

    private fun currentSig(): String = listOf(
        prefs.themeIndex, prefs.layoutIndex, prefs.cardStyle, prefs.dockStyle, prefs.fontIndex, prefs.bgVersion, prefs.focusColor,
        prefs.showClockCard, prefs.showWeatherCard, prefs.showPrayerCard, prefs.showPinnedApps,
        prefs.showMatchFavorites, prefs.showHomeDock, prefs.showWifiButton, prefs.showHdmiButton,
        prefs.showBoosterButton, prefs.showRadioButton, prefs.showSearchButton,
        prefs.showNotificationButton, prefs.showSettingsButton, prefs.showBranding,
        prefs.showTvInputTile, prefs.showClubShortcutTile, prefs.prayerCardOpensSettings, prefs.dockMatches,
        prefs.dockApps, prefs.dockRadio, prefs.dockVideo, prefs.dockWallpapers, prefs.dockSettings,
        prefs.favoriteAppShowNames, prefs.favoriteAppIconSize, prefs.favoriteAppTileWidth,
        prefs.showHijriDate, prefs.use24HourClock, prefs.weatherRefreshMinutes, prefs.animationsEnabled, prefs.matchSoundEnabled,
        prefs.matchFavoriteTeams.joinToString(","), prefs.matchProviderId, prefs.matchAutoFailover,
        prefs.footyLiveBaseUrl, prefs.arabFootballBaseUrl, prefs.flashscoreBaseUrl,
        prefs.matchCardDesign, prefs.matchCardPalette, prefs.matchCardLayout,
        prefs.liveScoresBaseUrl, prefs.liveScoresRapidApiKey.hashCode(), prefs.matchApiKey.hashCode(), prefs.lowMemoryBackground, prefs.favorites().toJson()
    ).joinToString("|")

    private fun currentCityKey(): String = prefs.cityName + "|" + prefs.cityLat + "|" + prefs.cityLon + "|" + prefs.cityTimezone + "|" + prefs.prayerMethod

    /** مرة واحدة: شاشة رئيسية نظيفة (بطاقات فقط). يمكن إعادة الأقسام من الإعدادات > الرئيسية. */
    private fun migrateCleanHome() {
        if (prefs.homeCleanV1) return
        prefs.homeCleanV1 = true
        prefs.showMatchFavorites = false
        prefs.showPinnedApps = false
    }

    /** ترحيل واجهة TV مرة واحدة: إظهار المفضلة المحفوظة مع اعتماد القائمة الجانبية فقط. */
    private fun migrateProHome() {
        if (prefs.proHomeDesignV1) return
        prefs.proHomeDesignV1 = true
        prefs.showPinnedApps = true
        prefs.showHomeDock = false
        prefs.favoriteAppShowNames = true
        prefs.favoriteAppTileWidth = maxOf(prefs.favoriteAppTileWidth, 230)
        prefs.favoriteAppIconSize = maxOf(prefs.favoriteAppIconSize, 92)
    }

    // كل قوالب الشاشة الرئيسية الجديدة تستخدم بنية التلفزيون الموحدة والقائمة الجانبية.
    private fun isProHomeLayout(): Boolean = true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        migrateCleanHome()
        migrateProHome()
        sig = currentSig()
        cityKey = currentCityKey()
        HomeWatchdogWorker.schedule(this)
        buildUi()
        renderWeatherCache()
        firstRunSetup()
        startCore()
        weatherCard.post {
            val target = listOfNotNull(
                if (prefs.showWeatherCard) weatherCard else null,
                if (prefs.showClockCard) tvTime else null,
                if (prefs.showPrayerCard) tvPNext else null
            ).firstOrNull { it.visibility == View.VISIBLE && it.isShown }
            if (target != null) target.requestFocus() else focusFirstVisible()
        }
    }

    override fun onResume() {
        super.onResume()
        if (currentSig() != sig) {
            recreate()
            return
        }
        refreshPinned()
        if (prefs.showMatchFavorites && favRow != null && System.currentTimeMillis() - favLoadedAt > prefs.favoritesRefreshMinutes * 60_000L) loadFavorites()
        if (currentCityKey() != cityKey) {
            tvCity.text = "📍 " + prefs.cityName
            tvBigCity?.text = prefs.cityName
            onSelectedCityChanged()
        }
        updateWifi()
        handler.removeCallbacks(ticker)
        handler.post(ticker)
        startCore()
        DefaultLauncher.ensureHomeService(this)
        DefaultLauncher.enforce(this, prefs)
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        // الضغط على Home أثناء وجود اللانشر: ارجع للتركيز على أول بطاقة
        setIntent(intent)
        focusFirstVisible()
    }

    /** لا تفترض وجود بطاقة الطقس؛ اختر أول عنصر ظاهر وقابل للتركيز في التخطيط الحالي. */
    private fun focusFirstVisible() {
        val content = window.decorView.findViewById<ViewGroup>(android.R.id.content) ?: return
        val candidates = ArrayList<View>()
        content.addFocusables(candidates, View.FOCUS_FORWARD, View.FOCUSABLES_ALL)
        val target = candidates.firstOrNull { it.isShown && it.visibility == View.VISIBLE && it.isEnabled }
        target?.requestFocus()
    }

    override fun onPause() {
        handler.removeCallbacks(ticker)
        super.onPause()
    }

    @Suppress("OVERRIDE_DEPRECATION", "DEPRECATION")
    override fun onBackPressed() {
        // اللانشر لا يُغلق بزر الرجوع
    }

    // =====================================================================
    // بناء الواجهة
    // =====================================================================
    private fun buildUi() {
        val root = FrameLayout(this)
        val homeTemplate = com.uobidah.launcher.core.HomeLayouts.get(prefs.layoutIndex).template
        val hasCustomWallpaper = Wallpapers.activeFile(this, prefs).let { it.isFile && it.length() > 0L }
        // R8 (التخطيط 24) يفرض مشهد الغروب مهما كان الثيم أو وضع الشاشة المصمتة؛ كان اختفاؤه
        // سببه اشتراط ثيم داكن وعدم وجود ثيم نادٍ/شاشة مصمتة، إضافةً إلى مفتاح اختياري مغلق.
        // الخلفية المخصصة للمستخدم تظل أعلى أولوية ولا نستبدل اختياره.
        val r8OwnsBackground = homeTemplate == HomeLayoutTemplate.DUSK_BOARD && !hasCustomWallpaper
        val nightScene = !r8OwnsBackground && isProHomeLayout() && homeTemplate == HomeLayoutTemplate.CHANNELS_FIRST &&
            !Themes.isLight(ut) && ut.club == null && !solidScreens && !hasCustomWallpaper
        val mosqueScene = !r8OwnsBackground && prefs.mosqueScene && !nightScene && !Themes.isLight(ut) && ut.club == null && !solidScreens && !hasCustomWallpaper
        val duskScene = r8OwnsBackground || (prefs.duskScene &&
            !nightScene && !mosqueScene && !Themes.isLight(ut) && ut.club == null && !solidScreens && !hasCustomWallpaper)
        root.tag = when (homeTemplate) {
            HomeLayoutTemplate.DUSK_BOARD -> "r8-home-root"
            HomeLayoutTemplate.DASHBOARD -> "r9-home-root"
            else -> "home-root"
        }
        root.background = if (nightScene) NightSceneBg() else if (mosqueScene) MosqueSceneBg() else if (duskScene) DuskSceneBg() else bgGradient()
        root.isLongClickable = true
        root.setOnLongClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
            true
        }

        val bgFile = Wallpapers.activeFile(this, prefs)
        var backgroundView: ImageView? = null
        if (bgFile.isFile && bgFile.length() > 0L) {
            val iv = ImageView(this)
            iv.scaleType = ImageView.ScaleType.CENTER_CROP
            backgroundView = iv
            root.addView(iv, FrameLayout.LayoutParams(Ui.MATCH, Ui.MATCH))
            val scrim = View(this)
            scrim.setBackgroundColor(Ui.withAlpha(ut.bgBottom, if (Themes.isLight(ut)) 42 else 150))
            root.addView(scrim, FrameLayout.LayoutParams(Ui.MATCH, Ui.MATCH))
            if (ut.club != null) {
                // زخرفة النادي فوق الخلفية المخصصة
                val art = View(this)
                art.background = ThemeBg(ut, false)
                root.addView(art, FrameLayout.LayoutParams(Ui.MATCH, Ui.MATCH))
            }
        }

        val col = when {
            isProHomeLayout() -> buildProHomeColumn()
            prefs.layoutIndex == 4 -> buildTvColumn()
            else -> buildClassicColumn()
        }

        root.addView(col, FrameLayout.LayoutParams(Ui.MATCH, Ui.MATCH))
        show(root)
        backgroundView?.let { image ->
            val target = if (prefs.lowMemoryBackground) 960 else 1920
            Img.loadFile(bgFile, image, target, target * 9 / 16)
        }
    }

    /** شاشة رئيسية جديدة مستوحاة من مراجع Android TV: وقت وتاريخ، أدوات سريعة، بطاقات عريضة، تطبيقات فعلية وشريط تنقل. */
    private fun buildProHomeColumn(): LinearLayout {
        val spec = com.uobidah.launcher.core.HomeLayouts.get(prefs.layoutIndex)
        val template = spec.template
        val compact = template in setOf(
            HomeLayoutTemplate.COMPACT,
            HomeLayoutTemplate.APP_GRID,
            HomeLayoutTemplate.CHANNELS_FIRST,
            HomeLayoutTemplate.MATCH_CENTER,
            HomeLayoutTemplate.PRAYER_LONG
        )
        val screen = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutDirection = View.LAYOUT_DIRECTION_LTR
            clipChildren = false
            clipToPadding = false
            setBackgroundColor(Color.TRANSPARENT)
        }
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            clipChildren = false
            clipToPadding = false
            setBackgroundColor(Color.TRANSPARENT)
            setPadding(px(if (compact) 24f else 38f), px(if (compact) 12f else 20f), px(if (compact) 24f else 38f), px(14f))
        }
        screen.addView(content, Ui.lp(0, Ui.MATCH, 1f))

        // الرأس ثابت؛ التمرير يقع على محتوى كل قالب فقط، وليس على القائمة الجانبية.
        content.addView(buildProTopBar(spec), Ui.lpm(Ui.MATCH, Ui.WRAP, 0f, 0, 0, 0, px(if (compact) 8f else 14f)))
        val scroll = android.widget.ScrollView(this).apply {
            setBackgroundColor(Color.TRANSPARENT)
            isVerticalScrollBarEnabled = false
            isFillViewport = true
            clipChildren = true
            // scrolled content must be cut at the scroll area, otherwise it slides over the fixed header
            clipToPadding = true
            overScrollMode = View.OVER_SCROLL_NEVER
            descendantFocusability = ViewGroup.FOCUS_AFTER_DESCENDANTS
            setPadding(0, px(8f), 0, px(8f))
        }
        val body = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.TRANSPARENT)
            clipChildren = false
            clipToPadding = false
            descendantFocusability = ViewGroup.FOCUS_AFTER_DESCENDANTS
        }
        // حتى القوالب السينمائية/الشبكية التي لا تعرض بطاقات تبني عناصر الوقت والطقس والصلاة
        // المخفية؛ بقية دورة التحديث تعتمد على هذه المراجع ولا يجوز تركها غير مهيأة.
        if (spec.cards.isEmpty()) buildProInfoCards(spec)
        scroll.addView(body, FrameLayout.LayoutParams(Ui.MATCH, Ui.WRAP))
        content.addView(scroll, Ui.lp(Ui.MATCH, 0, 1f))

        fun addInfo(
            vertical: Boolean = false,
            heightDp: Int = spec.cardsHeightDp.takeIf { it > 0 } ?: 270,
            cardOrder: List<com.uobidah.launcher.core.HomeCard>? = null
        ) {
            val panel = buildProInfoCards(spec, vertical, cardOrder)
            if (panel.visibility == View.GONE) return
            val containsPrayer = prefs.showPrayerCard && spec.cards.any { it.first == com.uobidah.launcher.core.HomeCard.PRAYER }
            val containsWeather = prefs.showWeatherCard && spec.cards.any { it.first == com.uobidah.launcher.core.HomeCard.WEATHER } &&
                (cardOrder == null || com.uobidah.launcher.core.HomeCard.WEATHER in cardOrder)
            // the weather card needs room for temperature, condition, detail line and 5-day forecast; a shorter box cut them off
            val resolvedHeight = if (containsPrayer) maxOf(heightDp, 390) else if (containsWeather) maxOf(heightDp, 440) else heightDp
            val lp = if (vertical) Ui.lpm(Ui.MATCH, Ui.WRAP, 0f, 0, px(7f), 0, px(12f))
                else Ui.lpm(Ui.MATCH, px(resolvedHeight.toFloat()), 0f, 0, px(7f), 0, px(14f))
            body.addView(panel, lp)
        }
        fun addAppsRow() {
            body.addView(buildProAppsSection(), Ui.lpm(Ui.MATCH, Ui.WRAP, 0f, 0, px(4f), 0, px(12f)))
        }
        fun addAppsGrid(columns: Int = 5) {
            body.addView(buildAppGridSection(columns), Ui.lpm(Ui.MATCH, Ui.WRAP, 0f, 0, px(4f), 0, px(12f)))
        }
        fun addMatches(force: Boolean = false) {
            val view = buildProMatchSection(force)
            if (view.visibility != View.GONE) body.addView(view, Ui.lpm(Ui.MATCH, Ui.WRAP, 0f, 0, px(3f), 0, px(10f)))
        }
        fun addChannels() {
            body.addView(buildChannelsSection(), Ui.lpm(Ui.MATCH, Ui.WRAP, 0f, 0, px(4f), 0, px(12f)))
        }
        fun addSplit(
            left: View,
            right: View,
            leftWeight: Float = 1f,
            rightWeight: Float = 1f,
            heightDp: Int = 500
        ) {
            val split = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                layoutDirection = View.LAYOUT_DIRECTION_LTR
                clipChildren = false
                clipToPadding = false
            }
            // heightDp حدٌّ أدنى فقط: لو احتاج المحتوى (شبكة تطبيقات طويلة، تقويم 6 أسابيع) أكثر منه يتمدد الصف
            // وتتولى شاشة الصفحة التمرير، بدل قص ما بعد الارتفاع الثابت.
            split.minimumHeight = px(heightDp.toFloat())
            split.addView(left, Ui.lpm(0, Ui.MATCH, leftWeight, 0, 0, px(10f), 0))
            split.addView(right, Ui.lpm(0, Ui.MATCH, rightWeight, 0, 0, 0, 0))
            body.addView(split, Ui.lpm(Ui.MATCH, Ui.WRAP, 0f, 0, px(5f), 0, px(14f)))
        }
        /** يبني بطاقتي الطقس والصلاة مرة واحدة ثم يفصل البطاقتين دون إعادة إنشاء مراجع التحديث. */
        fun addInfoSpotlight(weatherWeight0: Float, prayerWeight0: Float, heightDp: Int) {
            val narrowScreen = true
            val weatherWeight = if (narrowScreen) 1f else weatherWeight0
            val prayerWeight = if (narrowScreen) 1.1f else prayerWeight0
            buildProInfoCards(spec, vertical = false)
            val weatherView = weatherCard
            val prayerView = prayerCard
            // Detach both existing cards; keep the exact instances already bound to weather/prayer updates.
            (weatherView.parent as? ViewGroup)?.removeView(weatherView)
            (prayerView.parent as? ViewGroup)?.removeView(prayerView)
            val weatherPane = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                addView(weatherView, Ui.lp(Ui.MATCH, Ui.MATCH))
            }
            val prayerPane = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                addView(prayerView, Ui.lp(Ui.MATCH, Ui.MATCH))
            }
            val showWeather = prefs.showWeatherCard && spec.cards.any { it.first == com.uobidah.launcher.core.HomeCard.WEATHER }
            val showPrayer = prefs.showPrayerCard && spec.cards.any { it.first == com.uobidah.launcher.core.HomeCard.PRAYER }
            when {
                showWeather && showPrayer -> addSplit(weatherPane, prayerPane, weatherWeight, prayerWeight, heightDp)
                showWeather -> body.addView(weatherView, Ui.lpm(Ui.MATCH, px(heightDp.toFloat()), 0f, 0, px(5f), 0, px(14f)))
                showPrayer -> body.addView(prayerView, Ui.lpm(Ui.MATCH, px(heightDp.toFloat()), 0f, 0, px(5f), 0, px(14f)))
            }
        }

        archHighlight = false
        when (template) {
            HomeLayoutTemplate.BALANCED -> {
                addInfo(heightDp = 285)
                addAppsRow()
                addMatches()
            }
            HomeLayoutTemplate.CLOCK_HERO -> {
                addAppsGrid(4)
                addInfo(vertical = true)
                addMatches()
            }
            HomeLayoutTemplate.PRAYER_HERO -> {
                archPrayer = true
                archHighlight = true
                prayerGridView = null
                buildProInfoCards(spec, vertical = false, cardOrder = listOf(com.uobidah.launcher.core.HomeCard.PRAYER))
                archPrayer = false
                val arch = prayerCard
                val cells = prayerGridView
                (arch.parent as? ViewGroup)?.removeView(arch)
                (cells?.parent as? ViewGroup)?.removeView(cells)
                if (cells != null && prefs.showPrayerCard) {
                    arch.isFocusable = false; arch.isClickable = false; arch.setOnFocusChangeListener(null)
                    val big = px(230f).toFloat(); val sm = px(26f).toFloat()
                    arch.background = GradientDrawable().apply {
                        setColor(Ui.over(ut.cardFocus, ut.bgBottom))
                        cornerRadii = floatArrayOf(big, big, big, big, sm, sm, sm, sm)
                        setStroke(px(3f), Ui.withAlpha(ut.text, 200))
                    }
                    arch.gravity = Gravity.CENTER_HORIZONTAL or Gravity.CENTER_VERTICAL
                    val left = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; clipChildren = true; clipToPadding = true } // apps row scrolls inside this pane and never draws under the arch
                    left.addView(Ui.label(this, "مواقيت الصلاة", 30f, ut.text, true), Ui.lpm(Ui.MATCH, Ui.WRAP, 0f, 0, 0, 0, px(10f)))
                    left.addView(cells, Ui.lpm(Ui.MATCH, Ui.WRAP, 0f, 0, 0, 0, 0))
                    // مرجع R2: فاصل ثابت صغير بين الصلاة والمفضلة (≈4% من الارتفاع)، والفائض المرن يذهب للأسفل
                    // (بعد المفضلة ثم بعد «المزيد») بدل فراغ كبير بين الصلاة والمفضلة.
                    left.addView(buildProAppsSection("المفضلة", "", compact = true), Ui.lpm(Ui.MATCH, Ui.WRAP, 0f, 0, px(24f), 0, 0))
                    left.addView(View(this), Ui.lp(Ui.MATCH, 0, 0.6f))
                    left.addView(buildMoreRow(), Ui.lpm(Ui.MATCH, Ui.WRAP, 0f, 0, px(8f), 0, 0))
                    left.addView(View(this), Ui.lp(Ui.MATCH, 0, 1f))
                    val screenDp = resources.displayMetrics.heightPixels / resources.displayMetrics.density
                    addSplit(left, arch, 1.7f, 1f, maxOf(640, (screenDp * 0.68f).toInt()))
                } else {
                    addInfo(heightDp = maxOf(390, spec.cardsHeightDp), cardOrder = listOf(com.uobidah.launcher.core.HomeCard.PRAYER))
                    addAppsRow()
                }
            }
            HomeLayoutTemplate.APP_FIRST -> {
                addAppsGrid(5)
                addInfo(heightDp = 250)
                addMatches()
            }
            HomeLayoutTemplate.TV_REFERENCE -> {
                val info = buildProInfoCards(spec, vertical = true)
                val appGrid = buildAppGridSection(4)
                addSplit(appGrid, info, 1.35f, 0.9f, 800)
                addMatches()
            }
            HomeLayoutTemplate.APP_GRID_FIRST -> {
                addAppsGrid(4)
                addInfo(heightDp = 175, cardOrder = listOf(com.uobidah.launcher.core.HomeCard.WEATHER))
            }
            HomeLayoutTemplate.CLOCK_ONLY -> {
                // two columns: tall app grid on one side, live matches column on the other
                val apps = buildAppGridSection(3)
                val matches = buildProMatchSection(forceVisible = true, vertical = true)
                addSplit(apps, matches, 1.1f, 0.9f, 720)
                if (prefs.iptvFavorites.isNotEmpty()) addChannels()
            }
            HomeLayoutTemplate.WEATHER_PRAYER_STACK -> {
                addInfo(vertical = true)
                addAppsGrid(3)
            }
            HomeLayoutTemplate.CINEMA -> {
                addChannels()
                addAppsGrid(4)
            }
            HomeLayoutTemplate.CARDS_BOTTOM -> {
                addAppsRow()
                addMatches()
                addInfo(heightDp = 255)
            }
            HomeLayoutTemplate.CLOCK_APPS_SPLIT -> {
                val info = buildProInfoCards(spec, vertical = true)
                val apps = buildAppGridSection(3)
                addSplit(info, apps, 0.9f, 1.3f, 800)
                addMatches()
            }
            HomeLayoutTemplate.CARD_SHOWCASE -> {
                addInfoSpotlight(1f, 1f, maxOf(410, spec.cardsHeightDp))
                val leftStack = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; clipChildren = false }
                leftStack.addView(buildCalendarCard(), Ui.lpm(Ui.MATCH, Ui.WRAP, 0f, 0, 0, 0, px(8f)))
                val notes = buildNotesCard()
                if (notes.visibility != View.GONE) leftStack.addView(notes, Ui.lpm(Ui.MATCH, Ui.WRAP, 0f, 0, 0, 0, 0))
                addSplit(leftStack, buildAppGridSection(3), 0.8f, 1.3f, 400)
            }
            HomeLayoutTemplate.COMPACT -> {
                addAppsGrid(6)
                addInfo(heightDp = 160)
            }
            HomeLayoutTemplate.WEATHER_HERO -> {
                addInfoSpotlight(1.75f, 0.75f, maxOf(400, spec.cardsHeightDp))
                addAppsRow()
            }
            HomeLayoutTemplate.MATCHES_FIRST -> {
                // مرجع R7: بطاقة المباراة بجانب جدول الدوري بدل صف واحد للمباريات.
                addSplit(buildProMatchSection(forceVisible = true), buildStandingsCard(), 1.75f, 1f, 400)
                addAppsGrid(5)
                addInfo(heightDp = 195, cardOrder = listOf(com.uobidah.launcher.core.HomeCard.WEATHER))
            }
            HomeLayoutTemplate.PRAYER_MINIMAL -> {
                addInfo(vertical = true, heightDp = 300, cardOrder = listOf(com.uobidah.launcher.core.HomeCard.PRAYER))
                buildOccasionsRow().let { if (it.visibility != View.GONE) body.addView(it, Ui.lpm(Ui.MATCH, Ui.WRAP, 0f, 0, px(2f), 0, 0)) }
                addAppsGrid(5)
            }
            HomeLayoutTemplate.CHANNELS_RACK -> {
                addChannels()
                addAppsRow()
                addInfo(heightDp = 220)
            }
            HomeLayoutTemplate.SPLIT_COLUMNS -> {
                // matches column beside the weather/prayer stack, then apps and channels full width
                val matchPane = buildProMatchSection(forceVisible = true, vertical = true)
                val infoPane = buildProInfoCards(spec, vertical = true)
                addSplit(matchPane, infoPane, 1.1f, 0.9f, 800)
                addAppsGrid(6)
                if (prefs.iptvFavorites.isNotEmpty()) addChannels()
            }
            HomeLayoutTemplate.SPORTS_BOARD -> {
                val matches = buildProMatchSection(forceVisible = true)
                val apps = buildAppGridSection(3)
                addSplit(matches, apps, 1.25f, 0.75f, 520)
                addChannels()
            }
            HomeLayoutTemplate.FAVORITES_GRID -> {
                addAppsGrid(4)
                addMatches(force = true)
            }
            HomeLayoutTemplate.APP_GRID -> addAppsGrid(6)
            HomeLayoutTemplate.CHANNELS_FIRST -> {
                addChannels()
                addAppsGrid(5)
            }
            HomeLayoutTemplate.MATCH_CENTER -> {
                addMatches(force = true)
                addInfo(heightDp = 190, cardOrder = listOf(com.uobidah.launcher.core.HomeCard.WEATHER))
                addAppsRow()
            }
            HomeLayoutTemplate.PRAYER_LONG -> {
                addInfo(heightDp = maxOf(450, spec.cardsHeightDp), cardOrder = listOf(com.uobidah.launcher.core.HomeCard.PRAYER))
                buildOccasionsRow().let { if (it.visibility != View.GONE) body.addView(it, Ui.lpm(Ui.MATCH, Ui.WRAP, 0f, 0, px(2f), 0, 0)) }
                body.addView(buildDuaBanner(), Ui.lpm(Ui.MATCH, Ui.WRAP, 0f, 0, px(2f), 0, px(12f)))
                addAppsGrid(5)
            }
            HomeLayoutTemplate.DUSK_BOARD -> {
                // R8: ثلاث بطاقات متساوية الارتفاع (بث مباشر | مباراة واحدة | الصلاة القادمة).
                // نبني بطاقتي المعلومات مرة واحدة حتى تبقى مراجع التحديث (الطقس/الصلاة) صحيحة.
                buildProInfoCards(spec, vertical = false, cardOrder = listOf(com.uobidah.launcher.core.HomeCard.PRAYER))
                val prayerView = buildUpcomingPrayerSpotlight()
                val tripleHeight = px(270f)
                val triple = LinearLayout(this).apply {
                    orientation = LinearLayout.HORIZONTAL
                    layoutDirection = View.LAYOUT_DIRECTION_LTR
                    clipChildren = false
                    clipToPadding = false
                    minimumHeight = tripleHeight
                    tag = "r8-triple"
                }
                triple.addView(buildLiveTvCard(), Ui.lpm(0, tripleHeight, 1f, 0, 0, px(10f), 0))
                triple.addView(buildSingleMatchSpotlightCard(), Ui.lpm(0, tripleHeight, 1f, 0, 0, px(10f), 0))
                if (prefs.showPrayerCard) {
                    prayerView.tag = "r8-prayer-card"
                    triple.addView(prayerView, Ui.lpm(0, tripleHeight, 1f, 0, 0, 0, 0))
                } else {
                    val hiddenPrayer = buildEmptyPrayerSpotlight()
                    triple.addView(hiddenPrayer, Ui.lpm(0, tripleHeight, 1f, 0, 0, 0, 0))
                }
                triple.tag = "r8-triple"
                body.addView(triple, Ui.lpm(Ui.MATCH, tripleHeight, 0f, 0, px(5f), 0, px(10f)))
                // R8: شريط التطبيقات داخل المساحة الأساسية مباشرة؛ التمرير الرأسي يبقى على محتوى الصفحة.
                val appsBox = LinearLayout(this).apply {
                    orientation = LinearLayout.VERTICAL
                    clipChildren = false
                    clipToPadding = false
                    setPadding(px(18f), px(10f), px(18f), px(10f))
                    background = Ui.round(
                        Ui.withAlpha(Ui.over(ut.card, ut.bgBottom), 174),
                        px(26f * ut.radiusScale).toFloat(), px(2f), Ui.withAlpha(ut.accent, 132)
                    )
                    tag = "r8-apps-glass"
                }
                val appsSection = buildProAppsSection(compact = true)
                appsSection.tag = "r8-apps-section"
                appsBox.addView(appsSection, Ui.lp(Ui.MATCH, Ui.WRAP))
                body.addView(appsBox, Ui.lpm(Ui.MATCH, Ui.WRAP, 0f, 0, px(4f), 0, px(10f)))
            }
            HomeLayoutTemplate.DASHBOARD -> {
                // R9: ترويسة العلامة (الاسم + الإصدار)، ثم الطقس والصلاة، ثم بلاطات التطبيقات والمباريات.
                body.addView(buildBrandHeader(), Ui.lpm(Ui.MATCH, Ui.WRAP, 0f, 0, 0, 0, px(10f)))
                val infoPanel = buildProInfoCards(spec)
                val clockCard = LinearLayout(this).apply {
                    orientation = LinearLayout.VERTICAL
                    gravity = Gravity.CENTER
                    layoutDirection = View.LAYOUT_DIRECTION_LTR
                    setPadding(px(24f), px(18f), px(24f), px(18f))
                    tag = "r9-clock-card"
                }
                dashClockBox?.let { clockCard.addView(it, Ui.lp(Ui.WRAP, Ui.WRAP)) }
                // R9: القمر انتقل إلى بطاقة الطقس (كما في المرجع)؛ بطاقة الساعة ساعة وتاريخ فقط
                applyProSurface(clockCard, 0)
                clockCard.isFocusable = false; clockCard.isClickable = false
                clockCard.visibility = if (prefs.showClockCard) View.VISIBLE else View.GONE
                val cardsRow = LinearLayout(this).apply {
                    orientation = LinearLayout.HORIZONTAL
                    layoutDirection = View.LAYOUT_DIRECTION_LTR
                    clipChildren = false
                    clipToPadding = false
                    tag = "r9-cards-row"
                }
                val panelShown = infoPanel.visibility != View.GONE
                cardsRow.addView(clockCard, Ui.lpm(0, Ui.MATCH, 1f, 0, 0, if (panelShown) px(14f) else 0, 0))
                if (panelShown) cardsRow.addView(infoPanel, Ui.lpm(0, Ui.MATCH, 2.4f, 0, 0, 0, 0))
                if (!panelShown && !prefs.showClockCard) cardsRow.visibility = View.GONE
                // R9 must expose the complete five-day forecast and all five prayer cells plus
                // the countdown/progress bar. The body remains a ScrollView, so increasing this
                // row never clips the applications or favourites below it.
                body.addView(cardsRow, Ui.lpm(Ui.MATCH, px(390f), 0f, 0, px(7f), 0, px(10f)))
                body.addView(buildDashboardAppsSection(), Ui.lpm(Ui.MATCH, Ui.WRAP, 0f, 0, px(4f), 0, px(8f)))
                val favoritesContent = buildProMatchSection(forceVisible = true)
                val favorites = LinearLayout(this).apply {
                    orientation = LinearLayout.VERTICAL
                    clipChildren = false
                    clipToPadding = false
                    tag = "r9-favorites-section"
                }
                favorites.addView(favoritesContent, Ui.lp(Ui.MATCH, Ui.WRAP))
                body.addView(favorites, Ui.lpm(Ui.MATCH, Ui.WRAP, 0f, 0, px(3f), 0, px(12f)))
            }
        }

        // قائمة جانبية واحدة فقط في كل التخطيطات؛ لا يوجد شريط تنقل سفلي مكرر.
        screen.addView(buildHomeSideRail(), Ui.lpm(px(196f), Ui.MATCH, 0f, px(8f), px(16f), px(12f), px(16f)))
        return screen
    }

    /** شبكة تطبيقات R9: بلاطات موحّدة قصيرة بأيقونات الحزم الفعلية وأسمائها، مثل مرجع الشاشة. */
    private fun buildDashboardAppsSection(): View {
        val section = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            clipChildren = false
            clipToPadding = false
            tag = "r9-apps-section"
            // هذا القالب يقتضي ظهور صف التطبيقات؛ إعداد الإخفاء العام لا يلغي جزء التصميم نفسه.
            visibility = View.VISIBLE
        }
        val heading = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutDirection = View.LAYOUT_DIRECTION_RTL
        }
        heading.addView(Ui.label(this, "▦ التطبيقات المثبتة", 24f, ut.text, true), Ui.lp(0, Ui.WRAP, 1f))
        heading.addView(Ui.button(this, ut, "إدارة التطبيقات", 16f) {
            startActivity(Intent(this, FavoriteAppsSettingsActivity::class.java))
        })
        section.addView(heading, Ui.lpm(Ui.MATCH, Ui.WRAP, 0f, 0, 0, 0, px(5f)))
        val grid = buildAppGridSection(6, compactDashboard = true)
        grid.tag = "r9-apps-grid"
        section.addView(grid, Ui.lp(Ui.MATCH, Ui.WRAP))
        return section
    }

    /** ترويسة العلامة (مرجع R9): اسم التطبيق وسطر فرعي ورقم الإصدار الفعلي من الحزمة المثبتة. */
    private fun buildBrandHeader(): View {
        val version = try { packageManager.getPackageInfo(packageName, 0).versionName ?: "" } catch (_: Exception) { "" }
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutDirection = View.LAYOUT_DIRECTION_LTR
            gravity = Gravity.CENTER_VERTICAL
            clipChildren = false
            clipToPadding = false
            setPadding(px(8f), px(4f), px(8f), px(4f))
            tag = "r9-brand-header"
        }
        // R9: أزرار دائرية مع أسماء ظاهرة، لا أيقونات غير معنونة.
        row.addView(buildDashboardHeaderActions(), Ui.lp(Ui.WRAP, Ui.WRAP, 0f))
        row.addView(View(this), Ui.lp(0, 1, 1f))
        val brand = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutDirection = View.LAYOUT_DIRECTION_LTR
            gravity = Gravity.CENTER_VERTICAL
            clipChildren = false
        }
        val names = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.END; layoutDirection = View.LAYOUT_DIRECTION_LTR }
        names.addView(Ui.label(this, "Abo Uobidah", 38f, ut.text, true).apply { tag = "r9-brand-name" })
        names.addView(Ui.label(this, "Smart Launcher", 20f, ut.dim))
        brand.addView(names, Ui.lpm(Ui.WRAP, Ui.WRAP, 0f, 0, 0, px(12f), 0))
        val mark = ImageView(this).apply {
            tag = "r9-brand-logo"
            scaleType = ImageView.ScaleType.CENTER_CROP
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Ui.over(ut.cardFocus, ut.bgBottom))
                setStroke(px(2.5f), ut.accent)
            }
            // Use the same packaged vector resource registered in AndroidManifest.xml.
            // PackageManager.getApplicationIcon() may return null without throwing on some
            // preview/OEM package-manager implementations, leaving the R9 brand circle empty.
            setImageResource(R.drawable.ic_launcher)
            clipToOutline = true
            contentDescription = "شعار Abo Uobidah"
        }
        brand.addView(mark, Ui.lpm(px(76f), px(76f), 0f, 0, 0, 0, 0))
        row.addView(brand, Ui.lpm(Ui.WRAP, Ui.WRAP, 0f, 0, 0, px(12f), 0))
        if (version.isNotBlank()) {
            val chip = Ui.label(this, version, 22f, ut.text, true).apply {
                tag = "r9-version-chip"
                setPadding(px(18f), px(8f), px(18f), px(8f))
                background = Ui.round(Ui.withAlpha(ut.text, 24), px(20f).toFloat(), px(1f), Ui.withAlpha(ut.text, 46))
            }
            row.addView(chip, Ui.lpm(Ui.WRAP, Ui.WRAP, 0f, 0, 0, 0, 0))
        }
        return row
    }

    private fun buildDashboardHeaderActions(): View {
        val actions = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutDirection = View.LAYOUT_DIRECTION_LTR
            gravity = Gravity.CENTER_VERTICAL
            clipChildren = false
            clipToPadding = false
            tag = "r9-header-actions"
        }
        // أسماء الأزرار الأربعة جزء من مرجع R9، لذلك لا تخفيها إعدادات الهيدر العامة في هذا القالب.
        actions.addView(dashboardHeaderAction("◉", "الشبكة", "r9-action-network") { openWifi() }, Ui.lpm(Ui.WRAP, Ui.WRAP, 0f, 0, 0, px(5f), 0))
        actions.addView(dashboardHeaderAction("▣", "المدخل", "r9-action-input") { openHdmi() }, Ui.lpm(Ui.WRAP, Ui.WRAP, 0f, 0, 0, px(5f), 0))
        actions.addView(dashboardHeaderAction("⌕", "بحث", "r9-action-search") { openAppSearch() }, Ui.lpm(Ui.WRAP, Ui.WRAP, 0f, 0, 0, px(5f), 0))
        actions.addView(dashboardHeaderAction("♧", "التنبيهات", "r9-action-notifications") { startActivity(Intent(this, NotificationCenterActivity::class.java)) })
        return actions
    }

    private fun dashboardHeaderAction(icon: String, label: String, tagValue: String, action: () -> Unit): View {
        val item = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            isFocusable = true
            isClickable = true
            tag = tagValue
            setPadding(px(5f), px(3f), px(5f), px(3f))
            setOnClickListener { action() }
        }
        val glyph = Ui.label(this, icon, 31f, ut.text, true).apply {
            gravity = Gravity.CENTER
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Ui.withAlpha(ut.text, 24))
                setStroke(px(1.5f), Ui.withAlpha(ut.text, 72))
            }
        }
        item.addView(glyph, Ui.lp(px(54f), px(54f)))
        item.addView(Ui.label(this, label, 15f, ut.text, true).apply {
            gravity = Gravity.CENTER
            maxLines = 1
            includeFontPadding = false
            contentDescription = label
        }, Ui.lpm(Ui.WRAP, Ui.WRAP, 0f, 0, px(4f), 0, 0))
        Ui.focusable(item, ut, 18f)
        return item
    }

    /** بطاقة «البث المباشر» (مرجع R8): تفتح شاشة IPTV؛ لا تعرض اسم قناة أو شعارًا غير موجود فعليًا. */
    private fun buildLiveTvCard(): View {
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_VERTICAL
            layoutDirection = View.LAYOUT_DIRECTION_RTL
            setPadding(px(24f), px(20f), px(24f), px(20f))
            contentDescription = "البث المباشر"
            tag = "r8-live-card"
        }
        val glyph = Ui.label(this, "▣", 62f, ut.accent, true).apply {
            gravity = Gravity.CENTER
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Ui.withAlpha(ut.accent, 32))
                setStroke(px(2f), Ui.withAlpha(ut.accent, 110))
            }
        }
        card.addView(glyph, Ui.lpm(px(98f), px(98f), 0f, 0, 0, 0, px(10f)))
        card.addView(Ui.label(this, "● بث مباشر", 22f, ut.accent, true), Ui.lpm(Ui.WRAP, Ui.WRAP, 0f, 0, 0, 0, px(8f)))
        card.addView(Ui.label(this, "القنوات والقوائم", 31f, ut.text, true), Ui.lpm(Ui.WRAP, Ui.WRAP, 0f, 0, 0, 0, px(8f)))
        card.addView(Ui.label(this, "اضغط OK للمشاهدة", 19f, ut.text))
        applyProSurface(card, 0)
        card.setOnClickListener {
            try { startActivity(Intent(this, IptvProActivity::class.java)) } catch (_: Exception) { toast("تعذر فتح البث المباشر") }
        }
        return card
    }

    /** R8: بطاقة واحدة فقط؛ تختار المباشرة أولًا ثم أقرب قادمة ثم أحدث منتهية من الأندية التي اختارها المستخدم. */
    private fun buildSingleMatchSpotlightCard(): View {
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutDirection = View.LAYOUT_DIRECTION_RTL
            setPadding(px(14f), px(12f), px(14f), px(12f))
            clipChildren = false
            clipToPadding = false
            tag = "r8-single-match-card"
        }
        applyProSurface(box, 0)
        val heading = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        heading.addView(Ui.label(this, "⚽ مباراة واحدة", 25f, ut.text, true), Ui.lp(0, Ui.WRAP, 1f))
        heading.addView(Ui.button(this, ut, "عرض الكل", 15f) { startActivity(Intent(this, MatchesActivity::class.java)) })
        box.addView(heading, Ui.lpm(Ui.MATCH, Ui.WRAP, 0f, 0, 0, 0, px(8f)))
        val slot = FrameLayout(this).apply { tag = "r8-match-slot" }
        slot.addView(Ui.label(this, "جاري تحميل أقرب مباراة للأندية المفضلة…", 18f, ut.dim).apply { gravity = Gravity.CENTER },
            FrameLayout.LayoutParams(Ui.MATCH, Ui.MATCH))
        box.addView(slot, Ui.lp(Ui.MATCH, 0, 1f))
        val footer = Ui.label(this, "الأهلي  •  ريال مدريد  •  مانشستر يونايتد", 14f, ut.dim).apply { gravity = Gravity.CENTER; maxLines = 1 }
        box.addView(footer, Ui.lpm(Ui.MATCH, Ui.WRAP, 0f, 0, px(5f), 0, 0))
        Net.bg {
            val result = try {
                val selectedIds = prefs.matchFavoriteTeams.toSet()
                val selectedTeams = MatchRepo.allTeams(prefs).filter { it.id in selectedIds }
                val now = System.currentTimeMillis()
                val all = ArrayList<Pair<TeamDef, Match>>()
                selectedTeams.forEach { team ->
                    val id = MatchRepo.teamId(prefs, team)
                    (MatchRepo.next(prefs, id) + MatchRepo.last(prefs, id)).forEach { match -> all.add(team to match) }
                }
                val distinct = all.distinctBy { it.second.id }
                distinct.sortedWith(compareBy<Pair<TeamDef, Match>> {
                    val m = it.second
                    when { m.live(now) -> 0; m.upcoming(now) -> 1; m.previous(now) -> 2; else -> 3 }
                }.thenBy {
                    val m = it.second
                    when { m.upcoming(now) -> if (m.startMs > 0) m.startMs else Long.MAX_VALUE; m.previous(now) -> -m.startMs; else -> 0L }
                }).firstOrNull()
            } catch (_: Exception) { null }
            Net.ui {
                slot.removeAllViews()
                if (result == null) {
                    val empty = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER }
                    empty.addView(Ui.label(this, "لا توجد مباراة مؤكدة حاليًا", 20f, ut.dim, true).apply { gravity = Gravity.CENTER })
                    empty.addView(Ui.button(this, ut, "إعداد الأندية المفضلة", 16f) {
                        startActivity(Intent(this, MatchPreferencesActivity::class.java))
                    }, Ui.lpm(Ui.WRAP, Ui.WRAP, 0f, 0, px(8f), 0, 0))
                    slot.addView(empty, FrameLayout.LayoutParams(Ui.MATCH, Ui.MATCH))
                } else {
                    val (team, raw) = result
                    val match = if (raw.source != "ysscores") MatchRepo.withResolvedBadges(raw, prefs) else MatchRepo.withRepoLogos(raw, prefs)
                    val now = System.currentTimeMillis()
                    val kind = when { match.live(now) -> "live"; match.upcoming(now) -> "next"; match.previous(now) -> "last"; else -> "unknown" }
                    val title = when (kind) { "live" -> "المباراة الحالية"; "last" -> "آخر مباراة منتهية"; "next" -> "أقرب مباراة قادمة"; else -> "حالة غير مؤكدة" }
                    val badge = when (team.id) { match.homeTeamId -> match.homeBadge; match.awayTeamId -> match.awayBadge; else -> "" }
                    val card = MatchCards.build(this, prefs,
                        MatchCardModel(kind, title, match, team.arabic, badge, true, prefs.cityTimezone), compact = false)
                    card.tag = "r8-featured-match"
                    card.setOnClickListener {
                        startActivity(Intent(this, MatchDetailActivity::class.java)
                            .putExtra("id", match.id).putExtra("source", match.source)
                            .putExtra("home", match.home).putExtra("away", match.away)
                            .putExtra("homeBadge", match.homeBadge).putExtra("awayBadge", match.awayBadge)
                            .putExtra("league", match.league).putExtra("venue", match.venue)
                            .putExtra("startMs", match.startMs).putExtra("hs", match.homeScore ?: -1)
                            .putExtra("as", match.awayScore ?: -1).putExtra("status", match.status)
                            .putExtra("progress", match.progress).putExtra("externalUrl", match.externalUrl)
                            .putExtra("kind", kind))
                    }
                    slot.addView(card, FrameLayout.LayoutParams(Ui.MATCH, Ui.MATCH))
                }
            }
        }
        return box
    }

    /** بطاقة صلاة R8 مختصرة: رسم مسجد أصلي + اسم الصلاة والعدّ التنازلي الحيّين من PrayerRepo. */
    private fun buildUpcomingPrayerSpotlight(): View {
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            layoutDirection = View.LAYOUT_DIRECTION_RTL
            setPadding(px(12f), px(10f), px(12f), px(10f))
            clipChildren = false
            clipToPadding = false
            tag = "r8-prayer-card"
        }
        applyProSurface(box, 1)
        box.addView(buildPrayerArtwork(), Ui.lpm(Ui.MATCH, px(76f), 0f, 0, 0, 0, px(4f)))
        box.addView(Ui.label(this, "الصلاة القادمة", 20f, ut.text, true).apply {
            gravity = Gravity.CENTER
            maxLines = 1
        }, Ui.lp(Ui.MATCH, Ui.WRAP))

        // هذان الحقلان هما نفس حقلي PrayerRepo اللذين يحدّثهما ticker كل ثانية؛ ننقلهما
        // من بطاقة الجدول الكبيرة إلى بطاقة R8 المصغّرة كي لا تظهر مواقيت قديمة أو ثابتة.
        (tvPNext.parent as? ViewGroup)?.removeView(tvPNext)
        (tvPCount.parent as? ViewGroup)?.removeView(tvPCount)
        tvPNext.textSize = 23f
        tvPNext.setTextColor(Color.parseColor("#E8C98A"))
        tvPNext.gravity = Gravity.CENTER
        tvPNext.maxLines = 1
        tvPCount.textSize = 34f
        tvPCount.gravity = Gravity.CENTER
        box.addView(tvPNext, Ui.lpm(Ui.MATCH, Ui.WRAP, 0f, 0, px(2f), 0, 0))
        box.addView(tvPCount, Ui.lpm(Ui.MATCH, Ui.WRAP, 0f, 0, px(1f), 0, 0))
        box.addView(Ui.button(this, ut, "مواقيت اليوم والقبلة", 15f) {
            startActivity(Intent(this, PrayerSettingsActivity::class.java))
        }, Ui.lpm(Ui.WRAP, Ui.WRAP, 0f, 0, px(4f), 0, 0))
        box.setOnClickListener { startActivity(Intent(this, PrayerSettingsActivity::class.java)) }
        box.contentDescription = "بطاقة الصلاة القادمة والوقت المتبقي للأذان"
        return box
    }

    /** مشهد مسجد مبسّط مرسوم محليًا؛ لا يستخدم صورًا خارجية أو أصولًا غير مرخّصة. */
    private fun buildPrayerArtwork(): View = object : View(this) {
        private val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)
        private val dome = android.graphics.Path()
        override fun onDraw(canvas: android.graphics.Canvas) {
            super.onDraw(canvas)
            val w = width.toFloat().coerceAtLeast(1f)
            val h = height.toFloat().coerceAtLeast(1f)
            val r = px(14f).toFloat()
            paint.shader = android.graphics.LinearGradient(0f, 0f, w, h,
                intArrayOf(Color.rgb(30, 48, 82), Color.rgb(92, 74, 116), Color.rgb(213, 143, 122)),
                null, android.graphics.Shader.TileMode.CLAMP)
            canvas.drawRoundRect(0f, 0f, w, h, r, r, paint)
            paint.shader = null
            // هلال صغير ونجوم
            paint.color = Color.rgb(255, 225, 166)
            canvas.drawCircle(w * 0.78f, h * 0.27f, h * 0.15f, paint)
            paint.color = Color.rgb(73, 67, 105)
            canvas.drawCircle(w * 0.83f, h * 0.22f, h * 0.14f, paint)
            paint.color = Color.argb(210, 255, 245, 218)
            canvas.drawCircle(w * 0.65f, h * 0.20f, h * 0.018f, paint)
            canvas.drawCircle(w * 0.90f, h * 0.48f, h * 0.015f, paint)
            // قاعدة المسجد
            val floor = h * 0.78f
            paint.color = Color.rgb(26, 35, 62)
            canvas.drawRect(w * 0.08f, floor, w * 0.92f, h * 0.93f, paint)
            // جسم المسجد
            canvas.drawRect(w * 0.26f, h * 0.56f, w * 0.74f, floor, paint)
            // القبة الرئيسية
            dome.reset()
            dome.moveTo(w * 0.28f, h * 0.57f)
            dome.quadTo(w * 0.50f, h * 0.10f, w * 0.72f, h * 0.57f)
            dome.close()
            paint.color = Color.rgb(36, 48, 80)
            canvas.drawPath(dome, paint)
            paint.color = Color.rgb(232, 201, 138)
            canvas.drawLine(w * 0.50f, h * 0.11f, w * 0.50f, h * 0.02f, paint.apply { strokeWidth = px(1.5f).toFloat() })
            // مئذنتان مع شرفتين
            paint.color = Color.rgb(29, 39, 68)
            canvas.drawRoundRect(w * 0.16f, h * 0.30f, w * 0.22f, floor, px(3f).toFloat(), px(3f).toFloat(), paint)
            canvas.drawRoundRect(w * 0.78f, h * 0.30f, w * 0.84f, floor, px(3f).toFloat(), px(3f).toFloat(), paint)
            paint.color = Color.rgb(232, 201, 138)
            canvas.drawRect(w * 0.14f, h * 0.43f, w * 0.24f, h * 0.46f, paint)
            canvas.drawRect(w * 0.76f, h * 0.43f, w * 0.86f, h * 0.46f, paint)
            // محراب المدخل
            paint.color = Color.rgb(13, 22, 43)
            canvas.drawRoundRect(w * 0.44f, h * 0.67f, w * 0.56f, floor, px(12f).toFloat(), px(12f).toFloat(), paint)
            // خط أفقي ذهبي هادئ
            paint.color = Color.argb(180, 232, 201, 138)
            canvas.drawRect(w * 0.10f, h * 0.92f, w * 0.90f, h * 0.94f, paint)
        }
    }

    private fun buildEmptyPrayerSpotlight(): View {
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            tag = "r8-prayer-card"
            setPadding(px(18f), px(16f), px(18f), px(16f))
        }
        applyProSurface(box, 1)
        box.addView(Ui.label(this, "🕌 مواقيت الصلاة", 24f, ut.text, true).apply { gravity = Gravity.CENTER })
        box.addView(Ui.label(this, "فعّل بطاقة الصلاة من تخصيص الشاشة", 18f, ut.dim).apply { gravity = Gravity.CENTER },
            Ui.lpm(Ui.MATCH, Ui.WRAP, 0f, 0, px(8f), 0, 0))
        return box
    }

    /** قائمة جانبية ثابتة في جميع التخطيطات، قابلة للتركيز والتحكم بالريموت. */
    private fun buildHomeSideRail(): View {
        val rail = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(px(7f), px(10f), px(7f), px(10f))
            background = Ui.round(ut.card, px(24f).toFloat(), px(2f), ut.border)
            clipChildren = false
            tag = "home-side-rail"
        }
        data class NavItem(val icon: String, val label: String, val action: () -> Unit)
        // القائمة الجانبية هي وسيلة التنقل الوحيدة في كل التخطيطات.
        // كل عنصر يفتح قسمًا واحدًا واضحًا؛ لا نكرر IPTV في زرين مختلفين.
        val items = listOf(
            NavItem("⌂", "الرئيسية") { focusFirstVisible() },
            NavItem("▦", "التطبيقات") { startActivity(Intent(this, AppsActivity::class.java)) },
            NavItem("▶", "IPTV") { startActivity(Intent(this, IptvProActivity::class.java).putExtra(IptvProActivity.EXTRA_OPEN_CHOOSER, true)) },
            NavItem("⚽", "المباريات") { startActivity(Intent(this, MatchesActivity::class.java)) },
            NavItem("📖", "القرآن") { openQuranHub() },
            NavItem("🖼", "الخلفيات") { startActivity(Intent(this, WallpaperActivity::class.java)) },
            NavItem("⚙", "الإعدادات") { startActivity(Intent(this, SettingsActivity::class.java)) }
        )
        val compactRail = false
        items.forEachIndexed { index, item ->
            val button = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                layoutDirection = View.LAYOUT_DIRECTION_RTL
                gravity = Gravity.CENTER_VERTICAL or Gravity.START
                isFocusable = true
                isClickable = true
                setPadding(px(12f), px(4f), px(12f), px(4f))
                background = Ui.focusBg(this@MainLauncherActivity, ut, px(15f).toFloat(), if (index == 0) Ui.withAlpha(ut.accent, 32) else ut.card)
                setOnClickListener { item.action() }
            }
            button.addView(Ui.label(this, item.icon, if (compactRail) 24f else 28f, if (index == 0) ut.accent else ut.text, true).apply {
                gravity = Gravity.CENTER; textAlignment = View.TEXT_ALIGNMENT_CENTER; includeFontPadding = false
            }, Ui.lpm(Ui.WRAP, Ui.WRAP, 0f, 0, 0, px(10f), 0))
            button.addView(android.widget.TextView(this).apply {
                text = item.label
                setTextSize(TypedValue.COMPLEX_UNIT_PX, Ui.p(this@MainLauncherActivity, if (compactRail) 14f else 17f).toFloat())
                setTextColor(ut.text)
                typeface = if (index == 0) android.graphics.Typeface.create(Fonts.current, android.graphics.Typeface.BOLD) else Fonts.current
                gravity = Gravity.CENTER_VERTICAL or Gravity.START
                textAlignment = View.TEXT_ALIGNMENT_VIEW_START
                maxLines = 1
                includeFontPadding = true
            }, Ui.lp(0, Ui.WRAP, 1f))
            rail.addView(button, Ui.lpm(Ui.MATCH, 0, 1f, 0, px(3f), 0, px(3f)))
        }
        return rail
    }

    private var gridBox: LinearLayout? = null
    private var gridColumns: Int = 6
    private var gridCompactDashboard: Boolean = false

    /** شبكة تطبيقات حقيقية؛ عدد الأعمدة يتغير حسب القالب ولا يعاد استخدام تخطيط الصف الأفقي. */
    private fun buildAppGridSection(columns: Int = 6, compactDashboard: Boolean = false): View {
        gridColumns = columns.coerceIn(3, 7)
        gridCompactDashboard = compactDashboard
        val alwaysShowApps = com.uobidah.launcher.core.HomeLayouts.get(prefs.layoutIndex).template in
            setOf(HomeLayoutTemplate.DUSK_BOARD, HomeLayoutTemplate.DASHBOARD)
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            clipChildren = false
            clipToPadding = false
            layoutDirection = View.LAYOUT_DIRECTION_LTR
            visibility = if (prefs.showPinnedApps || alwaysShowApps) View.VISIBLE else View.GONE
        }
        gridBox = box
        refreshGrid()
        return box
    }

    private fun refreshGrid() {
        val box = gridBox ?: return
        box.removeAllViews()
        val pm = packageManager
        val perRow = gridColumns
        val items = ArrayList<View>()
        for (fav in prefs.favorites().visible()) {
            val pkg = fav.pkg
            val installed = try { pm.getApplicationInfo(pkg, 0); true } catch (_: Exception) { false }
            if (!installed) continue
            val icon: Drawable? = AppUtil.cachedIcon(pm, pkg, true)
            val label = fav.displayName(AppUtil.label(pm, pkg))
            val click = {
                val launch = AppUtil.launchIntent(pm, pkg)
                if (launch == null) toast("لا توجد واجهة تشغيل لهذا التطبيق")
                else try { startActivity(launch) } catch (_: Exception) { toast("تعذر فتح التطبيق") }
            }
            items.add(if (gridCompactDashboard) dashboardAppTile(icon, label, click) else
                tile(icon, "📦", label, click, { startActivity(Intent(this, FavoriteAppsSettingsActivity::class.java)) }, showLabel = true, favoriteIcon = false))
        }
        val addClick = { val i = Intent(this, AppsActivity::class.java); i.putExtra("mode", "pin"); startActivity(i) }
        items.add(if (gridCompactDashboard) dashboardAppTile(null, "إضافة تطبيق", addClick, addTile = true) else
            tile(null, "➕", "إضافة تطبيق", addClick, null))
        val rowHeight = px(if (gridCompactDashboard) 128f else 150f)
        items.chunked(perRow).forEach { rowItems ->
            val r = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; clipChildren = false; layoutDirection = View.LAYOUT_DIRECTION_LTR }
            for (k in 0 until perRow) {
                val v = rowItems.getOrNull(k)
                if (v != null) r.addView(v, Ui.lpm(0, rowHeight, 1f, px(5f), 0, px(5f), 0))
                else r.addView(View(this), Ui.lpm(0, rowHeight, 1f, px(5f), 0, px(5f), 0))
            }
            box.addView(r, Ui.lpm(Ui.MATCH, Ui.WRAP, 0f, 0, 0, 0, px(if (gridCompactDashboard) 5f else 10f)))
        }
    }

    private fun dashboardAppTile(icon: Drawable?, label: String, onClick: () -> Unit, addTile: Boolean = false): View {
        val item = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(px(5f), px(5f), px(5f), px(5f))
            tag = if (addTile) "r9-add-app-tile" else "r9-app-tile"
            contentDescription = label
            Ui.focusable(this, ut, 15f, if (icon != null) BrandTint.fillFor(icon, ut.card, ut.text) else ut.card)
            isClickable = true
            setOnClickListener { onClick() }
        }
        if (icon != null) {
            val iv = ImageView(this).apply {
                tag = "r9-app-icon"
                setImageDrawable(icon)
                scaleType = ImageView.ScaleType.FIT_CENTER
                contentDescription = label
            }
            val iconSize = if (prefs.layoutIndex == 25) 68f else 48f
            item.addView(iv, Ui.lp(px(iconSize), px(iconSize)))
        } else {
            val iconSize = if (prefs.layoutIndex == 25) 68f else 48f
            item.addView(Ui.label(this, if (addTile) "+" else "▦", 42f, ut.accent, true).apply { gravity = Gravity.CENTER }, Ui.lp(px(iconSize), px(iconSize)))
        }
        item.addView(Ui.label(this, label, 16f, ut.text, true).apply {
            gravity = Gravity.CENTER
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
            includeFontPadding = false
        }, Ui.lpm(Ui.MATCH, Ui.WRAP, 0f, 0, px(3f), 0, 0))
        return item
    }

    /**
     * كرت قناة ممتد (مرجع R3): الشعار يملأ الكرت، والاسم في شريط سفلي. لا شعار أو تعذّر تحميله → حرف الاسم الأول
     * كبديل واضح (لا صورة مولّدة). الضغط يشغّل القناة مباشرة.
     */
    private fun channelCard(f: com.uobidah.launcher.core.FavoriteChannel, tileW: Int = px(250f), tileH: Int = px(140f)): View {
        val frame = FrameLayout(this)
        // مرجع R3: بطاقة فاتحة شبه معتمة بزوايا ≈13٪ والشعار يملأ وسطها؛ لا شريط اسم فوق شعار محمّل (الاسم في contentDescription).
        val lightTile = Color.argb(235, 232, 230, 244)
        val designUnit = maxOf(resources.displayMetrics.widthPixels, resources.displayMetrics.heightPixels) / 1920f // بكسل لكل وحدة تصميم (Ui.p)
        Ui.focusable(frame, ut, (tileW * 0.13f / designUnit).coerceIn(16f, 30f), lightTile)
        frame.isClickable = true
        frame.setOnClickListener {
            try {
                startActivity(Intent(this, IptvProActivity::class.java)
                    .putExtra(IptvProActivity.EXTRA_FAVORITE_URL, f.url)
                    .putExtra(IptvProActivity.EXTRA_FAVORITE_NAME, f.name))
            } catch (_: Exception) { toast("تعذر فتح القناة") }
        }
        frame.contentDescription = f.name
        frame.tag = "channel-card"
        val fallback = Ui.label(this, f.name.trim().firstOrNull()?.toString() ?: "📺", 54f, Color.rgb(40, 40, 64), true).apply { gravity = Gravity.CENTER; tag = "channel-fallback" }
        frame.addView(fallback, FrameLayout.LayoutParams(Ui.MATCH, Ui.MATCH))
        val bar = Ui.label(this, f.name, 19f, Color.WHITE, true).apply {
            tag = "channel-name-bar"
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
            gravity = Gravity.CENTER
            setPadding(px(10f), px(6f), px(10f), px(6f))
            background = Ui.round(Color.argb(150, 0, 0, 0), px(14f).toFloat())
        }
        if (f.logo.isNotEmpty()) {
            val iv = ImageView(this).apply { tag = "channel-logo"; scaleType = ImageView.ScaleType.FIT_CENTER; setPadding(px(14f), px(14f), px(14f), px(14f)) }
            frame.addView(iv, FrameLayout.LayoutParams(Ui.MATCH, Ui.MATCH))
            // الشعار المحمَّل يملأ البطاقة كما في R3؛ إن لم يُحمَّل يبقى الحرف الاحتياطي وشريط الاسم ظاهرَين.
            Img.load(this, f.logo, iv, tileW, tileH) { fallback.visibility = View.GONE; bar.visibility = View.GONE }
        }
        frame.addView(bar, FrameLayout.LayoutParams(Ui.MATCH, Ui.WRAP, Gravity.BOTTOM).apply { setMargins(px(6f), 0, px(6f), px(6f)) })
        return frame
    }

    /** صف قنوات البث المفضلة (من مفضلة IPTV) يفتح شاشة البث المباشر. */
    private fun buildChannelsSection(): View {
        val section = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; clipChildren = false; clipToPadding = false }
        section.addView(Ui.label(this, "⭐ القنوات المفضلة", 27f, ut.text, true), Ui.lpm(Ui.MATCH, Ui.WRAP, 0f, 0, 0, 0, px(6f)))
        val scroll = HorizontalScrollView(this).apply { isHorizontalScrollBarEnabled = false; clipChildren = false; clipToPadding = false }
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; layoutDirection = View.LAYOUT_DIRECTION_LTR; clipChildren = false }
        val favs = com.uobidah.launcher.core.FavoriteChannels.parseAll(prefs.iptvFavorites)
        val open = { try { startActivity(Intent(this, IptvProActivity::class.java)) } catch (_: Exception) { toast("تعذر فتح البث المباشر") } }
        // مرجع R3 بالقياس: البطاقة ≈ 9.5–10٪ من عرض الشاشة و≈ 15٪ من ارتفاعها، والفاصل ≈ 1.5٪، وتاسع/عاشر بطاقة تظهر مقصوصة كدعوة للتمرير.
        val (tileW, tileH, gapW) = com.uobidah.launcher.core.ChannelTileSize.of(resources.displayMetrics.widthPixels, resources.displayMetrics.heightPixels)
        for (f in favs) {
            row.addView(channelCard(f, tileW, tileH), Ui.lpm(tileW, tileH, 0f, 0, 0, gapW, 0))
        }
        row.addView(tile(null, "➕", if (favs.isEmpty()) "أضف قنوات مفضلة من البث المباشر" else "كل القنوات", { open() }, null), Ui.lpm(if (favs.isEmpty()) tileW * 3 else (tileW * 1.4f).toInt(), tileH, 0f, 0, 0, 0, 0))
        scroll.addView(row, FrameLayout.LayoutParams(Ui.WRAP, Ui.WRAP))
        section.addView(scroll, Ui.lp(Ui.MATCH, Ui.WRAP))
        return section
    }

    private fun buildProTopBar(spec: com.uobidah.launcher.core.HomeLayoutSpec): View {
        val compact = spec.template in setOf(
            HomeLayoutTemplate.COMPACT,
            HomeLayoutTemplate.APP_GRID,
            HomeLayoutTemplate.CHANNELS_FIRST,
            HomeLayoutTemplate.MATCH_CENTER,
            HomeLayoutTemplate.PRAYER_LONG
        )
        val clockHero = spec.template in setOf(HomeLayoutTemplate.CLOCK_HERO, HomeLayoutTemplate.CLOCK_ONLY, HomeLayoutTemplate.CINEMA)
        val stackClock = clockHero
        // R8/R9: رأس عائم بلا صندوق مصمت خلفه
        val floating = spec.template == HomeLayoutTemplate.DUSK_BOARD || spec.template == HomeLayoutTemplate.DASHBOARD
        val shell = LinearLayout(this).apply {
            orientation = if (stackClock) LinearLayout.VERTICAL else LinearLayout.HORIZONTAL
            layoutDirection = View.LAYOUT_DIRECTION_LTR
            gravity = if (stackClock) Gravity.CENTER_HORIZONTAL else Gravity.CENTER_VERTICAL
            clipChildren = false
            clipToPadding = false
            setPadding(px(if (compact) 16f else 22f), px(if (compact) 5f else if (clockHero) 20f else 13f), px(if (compact) 16f else 22f), px(if (compact) 5f else if (clockHero) 20f else 13f))
            if (!floating) background = Ui.round(Ui.over(ut.dock, ut.bgBottom), px(26f).toFloat(), px(1.5f), ut.dockBorder)
            else if (spec.template == HomeLayoutTemplate.DUSK_BOARD) { tag = "r8-header-pill"; background = Ui.round(Ui.withAlpha(ut.text, 30), px(48f).toFloat(), px(1.5f), Ui.withAlpha(ut.text, 80)) } // R8: كبسولة زجاجية
        }
        val clockBox = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = if (spec.template == HomeLayoutTemplate.CLOCK_HERO || spec.template == HomeLayoutTemplate.CLOCK_ONLY) Gravity.CENTER else Gravity.CENTER_VERTICAL
        }
        val hour = PrayerRepo.localNow(prefs).hour
        val greeting = when (hour) { in 4..11 -> "صباح الخير"; in 12..17 -> "مساء الخير"; else -> "ليلة هادئة" }
        val greetingView: TextView? = if (!compact) Ui.label(this, greeting, 21f, ut.accent, true) else null
        if (greetingView != null) clockBox.addView(greetingView, Ui.lpm(Ui.WRAP, Ui.WRAP, 0f, 0, 0, 0, px(2f)))
        val timeRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.BOTTOM; layoutDirection = View.LAYOUT_DIRECTION_LTR }
        val clockWeight = spec.cards.firstOrNull { it.first == com.uobidah.launcher.core.HomeCard.CLOCK }?.second ?: 1f
        val timeSize = when {
            compact -> 38f
            spec.template == HomeLayoutTemplate.CLOCK_HERO -> 102f
            spec.template == HomeLayoutTemplate.CLOCK_ONLY -> 112f
            spec.template == HomeLayoutTemplate.CINEMA -> 88f
            else -> (66f + (clockWeight - 1f).coerceIn(-0.5f, 1f) * 18f).coerceIn(56f, 84f)
        }
        tvTime = Ui.label(this, "--:--", timeSize, ut.text, true).apply { isSingleLine = true; setHorizontallyScrolling(false) }
        tvAmPm = Ui.label(this, "", 27f, ut.accent, true)
        timeRow.addView(tvTime)
        timeRow.addView(tvAmPm, Ui.lpm(Ui.WRAP, Ui.WRAP, 0f, px(9f), 0, 0, px(9f)))
        timeRow.visibility = if (prefs.showClockCard) View.VISIBLE else View.GONE
        clockBox.addView(timeRow)
        val dateRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutDirection = View.LAYOUT_DIRECTION_RTL
            clipChildren = true
            clipToPadding = true
        }
        tvDate = Ui.label(this, "", if (compact) 21f else 23f, ut.text).apply {
            maxLines = 1
            isSingleLine = true
            ellipsize = android.text.TextUtils.TruncateAt.END
            includeFontPadding = false
            breakStrategy = android.text.Layout.BREAK_STRATEGY_SIMPLE
            hyphenationFrequency = android.text.Layout.HYPHENATION_FREQUENCY_NONE
        }
        tvHijri = Ui.label(this, "", if (compact) 21f else 21f, ut.accent, true).apply {
            maxLines = 1
            isSingleLine = true
            ellipsize = android.text.TextUtils.TruncateAt.END
            includeFontPadding = false
            breakStrategy = android.text.Layout.BREAK_STRATEGY_SIMPLE
            hyphenationFrequency = android.text.Layout.HYPHENATION_FREQUENCY_NONE
            visibility = if (prefs.showHijriDate) View.VISIBLE else View.GONE
        }
        if (compact) {
            // الهيدر المضغوط: الساعة ثم كتلة تاريخ من سطرين (ميلادي كامل بالسنة + هجري كامل بلا اختصار اسم الشهر)،
            // بدل حشر التاريخين في سطر واحد فيُقصّ الهجري وتسقط السنة الميلادية.
            clockBox.removeView(timeRow)
            val compactRow = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                layoutDirection = View.LAYOUT_DIRECTION_LTR
            }
            compactRow.addView(timeRow, Ui.lpm(Ui.WRAP, Ui.WRAP, 0f, 0, 0, px(20f), 0))
            val dateBlock = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER_VERTICAL
                layoutDirection = View.LAYOUT_DIRECTION_RTL
            }
            tvDate.textSize = 19f
            tvHijri.textSize = 19f
            dateBlock.addView(tvDate, Ui.lp(Ui.MATCH, Ui.WRAP))
            dateBlock.addView(tvHijri, Ui.lpm(Ui.MATCH, Ui.WRAP, 0f, 0, px(2f), 0, 0))
            compactRow.addView(dateBlock, Ui.lp(0, Ui.WRAP, 1f))
            clockBox.addView(compactRow, Ui.lpm(Ui.MATCH, Ui.WRAP, 0f, 0, px(2f), 0, 0))
        } else {
            dateRow.addView(tvDate)
            dateRow.addView(tvHijri, Ui.lpm(Ui.WRAP, Ui.WRAP, 0f, px(14f), 0, 0, 0))
            clockBox.addView(dateRow, Ui.lpm(Ui.WRAP, Ui.WRAP, 0f, 0, px(4f), 0, 0))
        }
        // صف واحد يجمع الطقس والصلاة القادمة تحت الساعة والتاريخ (مرجع R8)؛ القوالب المضغوطة تبقى منخفضة الارتفاع.
        tvHeaderInfo = null
        if (!compact) {
            val chip = Ui.label(this, "", 20f, ut.text).apply {
                tag = if (spec.template == HomeLayoutTemplate.DUSK_BOARD) "r8-weather-prayer-strip" else null
                maxLines = 2
                includeFontPadding = false
                layoutDirection = View.LAYOUT_DIRECTION_RTL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(px(14f), px(7f), px(14f), px(7f))
                background = Ui.round(Ui.withAlpha(ut.text, 24), px(18f).toFloat(), px(1f), Ui.withAlpha(ut.text, 46))
                visibility = View.GONE
            }
            tvHeaderInfo = chip
            clockBox.addView(chip, Ui.lpm(Ui.WRAP, Ui.WRAP, 0f, 0, px(8f), 0, 0))
        }
        dashClockBox = null
        if (spec.template == HomeLayoutTemplate.DASHBOARD) {
            // R9: الساعة والتاريخ بطاقة مستقلة بين بطاقتي الطقس والصلاة؛ تُركَّب في فرع DASHBOARD لا هنا
            dashClockBox = clockBox
            return View(this).apply { visibility = View.GONE }
        }
        tvBigCity = null
        if (spec.template == HomeLayoutTemplate.DUSK_BOARD && !compact) {
            // R8: الكبسولة تضم الساعة والأزرار وسرعة الشبكة فقط؛ التحية واسم المدينة الكبير والتاريخ وكبسولة الطقس/الصلاة
            // في كتلة على اليمين تحتها (لا داخلها).
            greetingView?.let { clockBox.removeView(it) }
            clockBox.removeView(dateRow)
            tvHeaderInfo?.let { clockBox.removeView(it) }
            shell.addView(clockBox, Ui.lp(Ui.WRAP, Ui.WRAP, 0f))
            tvNetSpeed = null
            shell.addView(buildHeaderActions(showSpeed = true), Ui.lpm(Ui.WRAP, Ui.WRAP, 0f, px(16f), 0, 0, 0))
            val info = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                layoutDirection = View.LAYOUT_DIRECTION_LTR
                gravity = Gravity.END
                clipChildren = false
                tag = "r8-info-block"
            }
            greetingView?.let { info.addView(it, Ui.lpm(Ui.WRAP, Ui.WRAP, 0f, 0, 0, 0, px(2f))) }
            val big = Ui.label(this, prefs.cityName, 64f, ut.text, true).apply { tag = "r8-city-big"; isSingleLine = true; ellipsize = android.text.TextUtils.TruncateAt.END }
            tvBigCity = big
            info.addView(big, Ui.lpm(Ui.WRAP, Ui.WRAP, 0f, 0, 0, 0, px(4f)))
            info.addView(dateRow, Ui.lpm(Ui.WRAP, Ui.WRAP, 0f, 0, 0, 0, px(8f)))
            tvHeaderInfo?.let { info.addView(it, Ui.lp(Ui.WRAP, Ui.WRAP, 0f)) }
            val outer = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; layoutDirection = View.LAYOUT_DIRECTION_LTR; clipChildren = false; clipToPadding = false }
            outer.addView(shell, LinearLayout.LayoutParams(Ui.WRAP, Ui.WRAP).apply { gravity = Gravity.CENTER_HORIZONTAL })
            outer.addView(info, Ui.lpm(Ui.MATCH, Ui.WRAP, 0f, px(16f), px(8f), px(16f), 0))
            return outer
        }
        if (stackClock) shell.addView(clockBox, Ui.lp(Ui.MATCH, Ui.WRAP, 0f)) else shell.addView(clockBox, Ui.lp(0, Ui.WRAP, 1f))

        tvNetSpeed = null
        // R9: أزرار الأيقونات بعناوينها تنتقل إلى ترويسة العلامة (يسار) بدل الشريط العلوي، فلا تُبنى هنا مرتين
        if (spec.template != HomeLayoutTemplate.DASHBOARD) {
            val actions = buildHeaderActions(showSpeed = spec.template == HomeLayoutTemplate.DUSK_BOARD)
            if (stackClock) shell.addView(actions, Ui.lpm(Ui.WRAP, Ui.WRAP, 0f, 0, px(8f), 0, 0)) else shell.addView(actions, Ui.lp(Ui.WRAP, Ui.WRAP))
        }
        return shell
    }


    private var dashClockBox: View? = null
    private var tvBigCity: TextView? = null // R8: اسم المدينة الكبير؛ يتحدّث مع تغيّر المدينة
    private var tvNetSpeed: TextView? = null
    private var lastRxBytes = -1L
    private var lastRxAt = 0L

    /** أزرار الرأس (واي فاي، مدخل، بحث، تنبيهات) + سرعة الشبكة اختياريًا؛ تُستخدم في الشريط العلوي أو في ترويسة R9. */
    private fun buildHeaderActions(showSpeed: Boolean = false): LinearLayout {
        val actions = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutDirection = View.LAYOUT_DIRECTION_LTR
            gravity = Gravity.CENTER_VERTICAL
            clipChildren = false
            clipToPadding = false
        }
        if (prefs.showWifiButton) {
            tvWifi = Ui.button(this, ut, "📶 اتصال", 21f) { openWifi() }
            tvWifi.setPadding(px(14f), px(12f), px(14f), px(12f))
            tvWifi.maxWidth = px(210f)
            Ui.ellipsize(tvWifi)
            actions.addView(tvWifi, Ui.lpm(Ui.WRAP, Ui.WRAP, 0f, 0, 0, px(8f), 0))
        }
        if (prefs.showHdmiButton) actions.addView(topIcon("▣", "المدخل") { openHdmi() }, Ui.lpm(Ui.WRAP, Ui.WRAP, 0f, 0, 0, px(7f), 0))
        if (prefs.showSearchButton) actions.addView(topIcon("⌕", "بحث") { openAppSearch() }, Ui.lpm(Ui.WRAP, Ui.WRAP, 0f, 0, 0, px(7f), 0))
        if (prefs.showNotificationButton) actions.addView(topIcon("🔔", "التنبيهات") { startActivity(Intent(this, NotificationCenterActivity::class.java)) }, Ui.lpm(Ui.WRAP, Ui.WRAP, 0f, 0, 0, px(7f), 0))
        if (showSpeed) {
            val chip = Ui.label(this, "⇣ —", 20f, ut.text).apply {
                setPadding(px(14f), px(8f), px(14f), px(8f))
                background = Ui.round(Ui.withAlpha(ut.text, 24), px(18f).toFloat(), px(1f), Ui.withAlpha(ut.text, 46))
                contentDescription = "سرعة الشبكة"
            }
            tvNetSpeed = chip
            actions.addView(chip, 0, Ui.lpm(Ui.WRAP, Ui.WRAP, 0f, 0, 0, px(10f), 0))
        }
        return actions
    }

    /** سرعة التنزيل الفعلية من عدّاد النظام؛ لا تعرض رقمًا إن لم يدعمه الجهاز. */
    private fun updateNetSpeed() {
        val chip = tvNetSpeed ?: return
        val rx = android.net.TrafficStats.getTotalRxBytes()
        val nowMs = android.os.SystemClock.elapsedRealtime()
        if (rx < 0L) { chip.text = "⇣ —"; return }
        if (lastRxBytes >= 0L && nowMs > lastRxAt) {
            val bps = (rx - lastRxBytes).coerceAtLeast(0L) * 1000.0 / (nowMs - lastRxAt)
            chip.text = if (bps >= 1048576.0) String.format(Locale.US, "⇣ %.1f MB/s", bps / 1048576.0)
                else String.format(Locale.US, "⇣ %d KB/s", (bps / 1024.0).toLong())
        }
        lastRxBytes = rx; lastRxAt = nowMs
    }

    private var tvHeaderInfo: TextView? = null

    /** يحدّث صف الرأس الموحد: الطقس الحالي + الصلاة القادمة ووقتها والمتبقي. لا يعرض شيئًا لم تصل بياناته. */
    private fun renderHeaderInfo(now: LocalDateTime) {
        val chip = tvHeaderInfo ?: return
        val parts = ArrayList<String>(2)
        weather?.let { w ->
            val d = WeatherRepo.describe(w.code, w.isDay)
            parts.add(d.first + " " + Math.round(w.temp) + "° " + d.second)
        }
        PrayerRepo.next(todayTimes, tomorrowTimes, now)?.let { nx ->
            val at = nx.second
            val h = if (at.hour % 12 == 0) 12 else at.hour % 12
            val clock = String.format(Locale.US, "%d:%02d", h, at.minute) + if (at.hour < 12) " ص" else " م"
            parts.add("🕌 " + prayerArabicLabel(nx.first) + " " + clock + " · بعد " + PrayerRepo.countdown(now, at))
        }
        val duskTemplate = com.uobidah.launcher.core.HomeLayouts.get(prefs.layoutIndex).template == HomeLayoutTemplate.DUSK_BOARD
        if (parts.isEmpty() && !duskTemplate) { chip.visibility = View.GONE; return }
        if (parts.isEmpty()) {
            // يظل الشريط تحت اسم المدينة ظاهرًا في R8 أثناء التحميل، دون وضع أرقام/حالة مختلقة.
            parts.add("🌤 الطقس قيد التحديث")
            parts.add("🕌 مواقيت الصلاة قيد التحديث")
        } else if (duskTemplate) {
            if (weather == null) parts.add(0, "🌤 الطقس قيد التحديث")
            if (PrayerRepo.next(todayTimes, tomorrowTimes, now) == null) parts.add("🕌 مواقيت الصلاة قيد التحديث")
        }
        chip.text = parts.joinToString("   |   ")
        chip.visibility = View.VISIBLE
    }

    private fun buildProInfoCards(
        spec: com.uobidah.launcher.core.HomeLayoutSpec,
        vertical: Boolean = false,
        cardOrder: List<com.uobidah.launcher.core.HomeCard>? = null
    ): View {
        val weather = buildTvWeather()
        val prayer = buildTvPrayer()
        applyProSurface(weather, 0)
        applyProSurface(prayer, 1)
        // the prayer card has no action of its own; a focusable card swallowed D-pad focus before the per-prayer cells inside it
        prayer.isFocusable = false; prayer.isClickable = false
        val showWeather = prefs.showWeatherCard && spec.cards.any { it.first == com.uobidah.launcher.core.HomeCard.WEATHER }
        val showPrayer = prefs.showPrayerCard && spec.cards.any { it.first == com.uobidah.launcher.core.HomeCard.PRAYER }
        weather.visibility = if (showWeather) View.VISIBLE else View.GONE
        prayer.visibility = if (showPrayer) View.VISIBLE else View.GONE
        val panel = LinearLayout(this).apply {
            orientation = if (vertical) LinearLayout.VERTICAL else LinearLayout.HORIZONTAL
            layoutDirection = if (vertical) View.LAYOUT_DIRECTION_RTL else View.LAYOUT_DIRECTION_LTR
            clipChildren = false
            clipToPadding = false
            setPadding(px(4f), px(4f), px(4f), px(4f))
        }
        val orderedCards = cardOrder?.mapNotNull { kind -> spec.cards.firstOrNull { it.first == kind } } ?: spec.cards
        val visible = orderedCards.filter { entry ->
            when (entry.first) {
                com.uobidah.launcher.core.HomeCard.WEATHER -> showWeather
                com.uobidah.launcher.core.HomeCard.PRAYER -> showPrayer
                com.uobidah.launcher.core.HomeCard.CLOCK -> false // الساعة في الرأس الثابت ولا نكررها كبطاقة ثانية.
            }
        }
        visible.forEachIndexed { i, entry ->
            val view = if (entry.first == com.uobidah.launcher.core.HomeCard.WEATHER) weather else prayer
            val requestedCardHeight = if (spec.cardsHeightDp > 0)
                (spec.cardsHeightDp / visible.size.coerceAtLeast(1)).coerceIn(210, 440).toFloat()
            else 250f
            // Prayer mini-cards are arranged in two readable columns, so give them enough height
            // for three rows instead of squeezing Arabic names and times into a thin strip.
            val dashboard = spec.template == HomeLayoutTemplate.DASHBOARD
            val cardHeight = when {
                dashboard -> maxOf(390f, requestedCardHeight)
                entry.first == com.uobidah.launcher.core.HomeCard.PRAYER -> maxOf(390f, requestedCardHeight)
                vertical -> maxOf(380f, requestedCardHeight)
                else -> requestedCardHeight
            }
            val lp = if (vertical) {
                Ui.lpm(Ui.MATCH, px(cardHeight), 0f,
                    0, 0, 0, if (i == visible.lastIndex) 0 else px(12f))
            } else {
                Ui.lpm(0, Ui.MATCH, entry.second.coerceAtLeast(0.5f), 0, 0, if (i == visible.lastIndex) 0 else px(14f), 0)
            }
            panel.addView(view, lp)
        }
        panel.visibility = if (visible.isEmpty()) View.GONE else View.VISIBLE
        if (!prefs.showClockCard && !prefs.showWeatherCard && !prefs.showPrayerCard) panel.visibility = View.GONE
        return panel
    }

    /** أسطح البطاقات مشتقة من لوحة الألوان المختارة؛ لا توجد ألوان زرقاء ثابتة تكسر السمات الفاتحة. */
    /** قوالب R8/R9 تستخدم بطاقات زجاجية شبه شفافة بدل الأسطح المصمتة. */
    private fun isGlassTemplate(): Boolean {
        val t = com.uobidah.launcher.core.HomeLayouts.get(prefs.layoutIndex).template
        return t == HomeLayoutTemplate.DUSK_BOARD || t == HomeLayoutTemplate.DASHBOARD
    }

    private fun applyProSurface(view: View, variant: Int) {
        val base = if (variant == 0) Ui.over(ut.card, ut.bgBottom) else Ui.over(ut.cardFocus, ut.bgBottom)
        val top = Ui.over(Ui.withAlpha(ut.accent, if (variant == 0) 22 else 34), base)
        val template = com.uobidah.launcher.core.HomeLayouts.get(prefs.layoutIndex).template
        val normal: GradientDrawable = if (isGlassTemplate()) {
            // R8 needs a lighter frosted-glass veil so the dusk illustration remains visible.
            // R9 stays more opaque because its weather/prayer text is denser and information-first.
            val glassAlpha = if (template == HomeLayoutTemplate.DUSK_BOARD) 166 else 232
            val borderAlpha = if (template == HomeLayoutTemplate.DUSK_BOARD) 184 else 150
            Ui.round(Ui.withAlpha(base, glassAlpha), px(26f * ut.radiusScale).toFloat(), px(2f), Ui.withAlpha(ut.accent, borderAlpha))
        } else {
            GradientDrawable(GradientDrawable.Orientation.TL_BR, intArrayOf(top, base)).apply {
                cornerRadius = px(26f * ut.radiusScale).toFloat()
                setStroke(px(1.5f), Ui.withAlpha(ut.text, 46))
            }
        }
        val focused = GradientDrawable(GradientDrawable.Orientation.TL_BR, intArrayOf(Ui.over(ut.cardFocus, ut.bgBottom), base)).apply {
            cornerRadius = px(26f * ut.radiusScale).toFloat()
            setStroke(px(4f), ut.accent)
        }
        view.isFocusable = true
        view.isClickable = true
        view.setOnFocusChangeListener { target, hasFocus ->
            if (Motion.enabled(this)) {
                val scale = if (hasFocus) 1.018f else 1f
                target.animate().cancel()
                target.animate().scaleX(scale).scaleY(scale).setDuration(100L).start()
            } else {
                target.scaleX = 1f
                target.scaleY = 1f
            }
        }
        view.background = StateListDrawable().apply {
            addState(intArrayOf(android.R.attr.state_focused), focused)
            addState(intArrayOf(), normal)
        }
    }

    private fun buildProAppsSection(title: String = "التطبيقات", subtitle: String = "تطبيقاتك المفضلة — اختر أي تطبيق لفتحه", compact: Boolean = false): View {
        val section = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; clipChildren = false; clipToPadding = false }
        val alwaysShowApps = com.uobidah.launcher.core.HomeLayouts.get(prefs.layoutIndex).template in
            setOf(HomeLayoutTemplate.DUSK_BOARD, HomeLayoutTemplate.DASHBOARD)
        section.visibility = if (prefs.showPinnedApps || alwaysShowApps) View.VISIBLE else View.GONE
        val heading = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        val titleBox = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        // compact (تصميم الصلاة، مرجع R2): العنوان وحده بلا عنوان فرعي، والزر صغير في الطرف المقابل ومحاذٍ رأسيًا
        // لوسط العنوان بدل أن يزاحمه؛ الاتجاه RTL صريح: العنوان يمينًا والزر يسارًا.
        if (compact) heading.layoutDirection = View.LAYOUT_DIRECTION_RTL
        titleBox.addView(Ui.label(this, title, if (compact) 26f else 29f, ut.text, true))
        if (!compact && subtitle.isNotEmpty()) titleBox.addView(Ui.label(this, subtitle, 18f, ut.dim))
        heading.addView(titleBox, Ui.lp(0, Ui.WRAP, 1f))
        val manage = Ui.button(this, ut, if (compact) "تنظيم  ✎" else "تنظيم التطبيقات  ✎", if (compact) 16f else 19f) { startActivity(Intent(this, FavoriteAppsSettingsActivity::class.java)) }
        if (compact) manage.setPadding(px(18f), px(7f), px(18f), px(7f))
        manage.contentDescription = "تنظيم التطبيقات"
        heading.addView(manage, Ui.lp(Ui.WRAP, Ui.WRAP))
        section.addView(heading, Ui.lpm(Ui.MATCH, Ui.WRAP, 0f, 0, 0, 0, px(5f)))
        val apps = buildPinnedSection(true)
        section.addView(apps, Ui.lp(Ui.MATCH, Ui.WRAP))
        return section
    }

    /** صف «المزيد» في تصميم الصلاة: كل التطبيقات (مع العدد) وإضافة تطبيق، وكلاهما قابل للتحديد بالريموت. */
    private fun buildMoreRow(): View {
        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; clipChildren = false; clipToPadding = false }
        box.addView(Ui.label(this, "المزيد", 24f, ut.text, true), Ui.lpm(Ui.MATCH, Ui.WRAP, 0f, 0, px(4f), 0, px(4f)))
        val count = try {
            packageManager.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER), 0).size +
                packageManager.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0).size
        } catch (_: Exception) { 0 }
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; clipChildren = false; layoutDirection = View.LAYOUT_DIRECTION_RTL }
        val allLabel = if (count > 0) "كل التطبيقات  ($count)" else "كل التطبيقات"
        row.addView(Ui.button(this, ut, allLabel, 20f) { startActivity(Intent(this, AppsActivity::class.java)) }, Ui.lpm(Ui.WRAP, Ui.WRAP, 0f, 0, 0, px(10f), 0))
        row.addView(Ui.button(this, ut, "إضافة تطبيق  ➕", 20f) {
            startActivity(Intent(this, AppsActivity::class.java).putExtra("mode", "pin"))
        })
        box.addView(row)
        return box
    }

    private var standingsCache: List<EspnSoccerProvider.Standing>? = null
    private var standingsAt = 0L

    /**
     * جدول الدوري الإنجليزي (مرجع R7) من ESPN. لا تُعرض أرقام غير مجلوبة: حالة التحميل والفشل نصوص صريحة،
     * ويُكتب مصدر البيانات ووقت التحديث تحت الجدول.
     */
    private fun buildStandingsCard(league: String = "eng.1"): View {
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(px(18f), px(12f), px(18f), px(12f))
            clipChildren = false
            layoutDirection = View.LAYOUT_DIRECTION_RTL
        }
        applyProSurface(card, 1)
        card.isFocusable = false; card.isClickable = false
        card.addView(Ui.label(this, "ترتيب الدوري الإنجليزي الممتاز", 24f, ut.text, true), Ui.lpm(Ui.MATCH, Ui.WRAP, 0f, 0, 0, 0, px(6f)))
        val body = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        card.addView(body, Ui.lp(Ui.MATCH, Ui.WRAP))
        val footer = Ui.label(this, "", 15f, ut.dim)
        card.addView(footer, Ui.lpm(Ui.MATCH, Ui.WRAP, 0f, 0, px(6f), 0, px(4f)))
        card.addView(Ui.button(this, ut, "الجدول كاملًا", 18f) {
            startActivity(Intent(this, MatchesActivity::class.java).putExtra("panel_mode", 1))
        })
        fun row(rank: String, team: String, played: String, pts: String, bold: Boolean, fav: Boolean = false) {
            val r = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; layoutDirection = View.LAYOUT_DIRECTION_RTL; gravity = Gravity.CENTER_VERTICAL }
            // تمييز فريقك (مرجع R7): خلفية ذهبية شفافة وخط عريض.
            if (fav) { r.background = Ui.round(Ui.withAlpha(Color.parseColor("#E8C98A"), 90), px(10f).toFloat()); r.setPadding(px(6f), px(2f), px(6f), px(2f)) }
            val c = if (bold) ut.dim else ut.text
            val b = bold || fav
            r.addView(Ui.label(this, rank, 19f, c, b).apply { gravity = Gravity.CENTER }, Ui.lp(px(34f), Ui.WRAP))
            r.addView(Ui.label(this, team, 20f, c, b).apply { maxLines = 2; layoutDirection = View.LAYOUT_DIRECTION_LTR; gravity = Gravity.START }, Ui.lp(0, Ui.WRAP, 1f))
            r.addView(Ui.label(this, played, 19f, c, b).apply { gravity = Gravity.CENTER }, Ui.lp(px(46f), Ui.WRAP))
            r.addView(Ui.label(this, pts, 19f, c, true).apply { gravity = Gravity.CENTER }, Ui.lp(px(52f), Ui.WRAP))
            body.addView(r, Ui.lpm(Ui.MATCH, Ui.WRAP, 0f, 0, px(3f), 0, px(3f)))
        }
        val formRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; layoutDirection = View.LAYOUT_DIRECTION_RTL; gravity = Gravity.CENTER_VERTICAL }
        card.addView(formRow, 2, Ui.lpm(Ui.MATCH, Ui.WRAP, 0f, 0, px(2f), 0, px(6f)))
        var formShownFor = ""
        fun showForm(teamName: String, results: List<com.uobidah.launcher.core.FormResult>) {
            formRow.removeAllViews()
            if (results.isEmpty()) return
            formRow.addView(Ui.label(this, "فورمة $teamName", 17f, ut.dim, true), Ui.lpm(Ui.WRAP, Ui.WRAP, 0f, 0, 0, px(10f), 0))
            results.forEach { fr ->
                val col = when (fr) { com.uobidah.launcher.core.FormResult.W -> "#22C55E"; com.uobidah.launcher.core.FormResult.D -> "#F59E0B"; else -> "#EF4444" }
                formRow.addView(Ui.label(this, fr.letterAr, 16f, Color.WHITE, true).apply {
                    gravity = Gravity.CENTER
                    background = Ui.round(Color.parseColor(col), px(8f).toFloat())
                }, Ui.lpm(px(30f), px(28f), 0f, px(3f), 0, px(3f), 0))
            }
        }
        fun render(list: List<EspnSoccerProvider.Standing>, at: Long) {
            body.removeAllViews()
            row("#", "الفريق", "لعب", "نقاط", true)
            val favIds = prefs.matchFavoriteTeams.toSet()
            val shown = com.uobidah.launcher.core.StandingsWindow.indices(list.size, { list[it].teamId in favIds })
            shown.forEachIndexed { pos, i ->
                if (com.uobidah.launcher.core.StandingsWindow.hasGapBefore(shown, pos)) {
                    body.addView(Ui.label(this, "⋮", 16f, ut.dim).apply { gravity = Gravity.CENTER }, Ui.lp(Ui.MATCH, Ui.WRAP))
                }
                val st = list[i]
                row(DateUtil.arNum(st.rank), st.team, DateUtil.arNum(st.played), DateUtil.arNum(st.points), false, st.teamId in favIds)
            }
            footer.text = "المصدر: ESPN · آخر تحديث " + java.text.SimpleDateFormat("HH:mm", Locale.US).format(java.util.Date(at))
            // فورمة أول فريق مفضل موجود في هذا الجدول (تُجلب مرة وتُخزَّن 30 دقيقة).
            val favTeam = list.firstOrNull { it.teamId in favIds }
            if (favTeam != null && formShownFor != favTeam.teamId) {
                formShownFor = favTeam.teamId
                Net.bg {
                    val f = try { EspnSoccerProvider.teamForm(league, favTeam.teamId) } catch (_: Exception) { emptyList() }
                    Net.ui { showForm(favTeam.team, f) }
                }
            }
        }
        val cached = standingsCache
        if (cached != null && System.currentTimeMillis() - standingsAt < 30 * 60_000L) {
            render(cached, standingsAt)
        } else {
            footer.text = "جاري تحميل الترتيب..."
            Net.bg {
                val result = try { EspnSoccerProvider.standings(league) } catch (_: Exception) { null }
                Net.ui {
                    if (result.isNullOrEmpty()) {
                        footer.text = "تعذر جلب الترتيب الآن — لا توجد أرقام معروضة"
                    } else {
                        standingsCache = result
                        standingsAt = System.currentTimeMillis()
                        render(result, standingsAt)
                    }
                }
            }
        }
        return card
    }

    /** بطاقة تقويم الشهر الحالي (مرجع R4): اليوم مميز، وكل الأرقام والأسماء عربية كاملة بلا قص. */
    private fun buildCalendarCard(): View {
        val today = PrayerRepo.localNow(prefs).toLocalDate()
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(px(20f), px(14f), px(20f), px(14f))
            clipChildren = false
            layoutDirection = View.LAYOUT_DIRECTION_RTL
        }
        applyProSurface(card, 0)
        card.isFocusable = false; card.isClickable = false
        card.addView(Ui.label(this, "📅 " + com.uobidah.launcher.core.CalendarGrid.monthsAr[today.monthValue - 1] + " " + DateUtil.arNum(today.year), 26f, ut.text, true),
            Ui.lpm(Ui.MATCH, Ui.WRAP, 0f, 0, 0, 0, px(6f)))
        val head = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; layoutDirection = View.LAYOUT_DIRECTION_RTL }
        com.uobidah.launcher.core.CalendarGrid.weekdayInitialsAr.forEach { d ->
            head.addView(Ui.label(this, d, 18f, ut.dim, true).apply { gravity = Gravity.CENTER }, Ui.lp(0, Ui.WRAP, 1f))
        }
        card.addView(head, Ui.lp(Ui.MATCH, Ui.WRAP))
        com.uobidah.launcher.core.CalendarGrid.weeks(today.year, today.monthValue).forEach { week ->
            val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; layoutDirection = View.LAYOUT_DIRECTION_RTL }
            week.forEach { day ->
                val isToday = day == today.dayOfMonth
                val cell = Ui.label(this, if (day == null) "" else DateUtil.arNum(day), 20f, if (isToday) ut.onAccent else ut.text, isToday).apply {
                    gravity = Gravity.CENTER
                    maxLines = 1
                    includeFontPadding = false
                    if (isToday) background = Ui.round(ut.accent, px(14f).toFloat())
                }
                row.addView(cell, Ui.lpm(0, px(38f), 1f, px(2f), px(2f), px(2f), px(2f)))
            }
            card.addView(row, Ui.lp(Ui.MATCH, Ui.WRAP))
        }
        return card
    }

    /** ودجت الملاحظات (مرجع R4): مربعات اختيار؛ الضغط على ملاحظة يبدّل «تم»، والزر يفتح الإدارة. */
    private fun buildNotesCard(): View {
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(px(20f), px(12f), px(20f), px(12f))
            clipChildren = false
            layoutDirection = View.LAYOUT_DIRECTION_RTL
        }
        if (!prefs.showNotesCard) { card.visibility = View.GONE; return card }
        applyProSurface(card, 1)
        card.isFocusable = false; card.isClickable = false
        card.addView(Ui.label(this, "📝 الملاحظات", 24f, ut.text, true), Ui.lpm(Ui.MATCH, Ui.WRAP, 0f, 0, 0, 0, px(4f)))
        val listBox = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        card.addView(listBox, Ui.lp(Ui.MATCH, Ui.WRAP))
        fun render() {
            listBox.removeAllViews()
            val all = com.uobidah.launcher.core.Notes.decode(prefs.notesJson)
            if (all.isEmpty()) {
                listBox.addView(Ui.label(this, "لا توجد ملاحظات — اضغط «إدارة» للإضافة", 18f, ut.dim), Ui.lp(Ui.MATCH, Ui.WRAP))
                return
            }
            com.uobidah.launcher.core.Notes.visible(all, 4).forEach { (idx, n) ->
                val row = Ui.label(this, (if (n.done) "☑  " else "☐  ") + n.text, 20f, if (n.done) ut.dim else ut.text).apply {
                    maxLines = 1
                    ellipsize = android.text.TextUtils.TruncateAt.END
                    if (n.done) paintFlags = paintFlags or android.graphics.Paint.STRIKE_THRU_TEXT_FLAG
                    setPadding(px(8f), px(6f), px(8f), px(6f))
                }
                Ui.focusable(row, ut, 12f)
                row.setOnClickListener {
                    val cur = com.uobidah.launcher.core.Notes.decode(prefs.notesJson)
                    prefs.notesJson = com.uobidah.launcher.core.Notes.encode(com.uobidah.launcher.core.Notes.toggle(cur, idx))
                    render()
                }
                listBox.addView(row, Ui.lpm(Ui.MATCH, Ui.WRAP, 0f, 0, px(2f), 0, px(2f)))
            }
        }
        render()
        card.addView(Ui.button(this, ut, "إدارة", 16f) { startActivity(Intent(this, NotesActivity::class.java)) }, Ui.lpm(Ui.WRAP, Ui.WRAP, 0f, 0, px(6f), 0, 0))
        return card
    }

    /** شبكة المناسبات (مرجع R6): التاريخ الهجري اليوم + رمضان + العيدان، مع تنبيه أن التواريخ تقديرية. */
    private fun buildOccasionsRow(): View {
        val wrap = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; layoutDirection = View.LAYOUT_DIRECTION_RTL }
        if (!prefs.showOccasions) { wrap.visibility = View.GONE; return wrap }
        val today = PrayerRepo.localNow(prefs).toLocalDate()
        val items = com.uobidah.launcher.core.Occasions.upcoming(today)
        if (items.isEmpty()) { wrap.visibility = View.GONE; return wrap }
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; layoutDirection = View.LAYOUT_DIRECTION_RTL }
        fun tile(title: String, value: String, sub: String) {
            val t = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(px(16f), px(10f), px(16f), px(10f))
            }
            applyProSurface(t, 0)
            t.isFocusable = false; t.isClickable = false
            t.addView(Ui.label(this, title, 19f, ut.dim, true).apply { maxLines = 1 })
            t.addView(Ui.label(this, value, 21f, ut.text, true).apply { maxLines = 1 }, Ui.lpm(Ui.MATCH, Ui.WRAP, 0f, 0, px(2f), 0, 0))
            if (sub.isNotEmpty()) t.addView(Ui.label(this, sub, 17f, Color.parseColor("#E8C98A")).apply { maxLines = 1 })
            row.addView(t, Ui.lpm(0, Ui.WRAP, 1f, px(5f), 0, px(5f), 0))
        }
        tile("التقويم الهجري", DateUtil.hijri(0, today), "")
        items.forEach {
            val d = DateUtil.arNum(it.date.dayOfMonth) + " " + com.uobidah.launcher.core.CalendarGrid.monthsAr[it.date.monthValue - 1] + " " + DateUtil.arNum(it.date.year)
            val left = if (it.daysLeft == 0L) "اليوم" else "بعد " + DateUtil.arNum(it.daysLeft.toInt()) + " يومًا"
            tile(it.nameAr, d, left)
        }
        wrap.addView(row, Ui.lp(Ui.MATCH, Ui.WRAP))
        wrap.addView(Ui.label(this, com.uobidah.launcher.core.Occasions.DISCLAIMER_AR, 15f, ut.dim).apply { gravity = Gravity.CENTER },
            Ui.lpm(Ui.MATCH, Ui.WRAP, 0f, 0, px(4f), 0, px(8f)))
        return wrap
    }

    /** شريط دعاء موثّق (مرجع R6). النص من ملف الأحاديث الموثّقة بلا توليد، مع مصدره. */
    private fun buildDuaBanner(): View {
        val dua = com.uobidah.launcher.core.Hadiths.NOOR_DUA_OPENING
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(px(22f), px(12f), px(22f), px(12f))
            layoutDirection = View.LAYOUT_DIRECTION_RTL
        }
        applyProSurface(box, 1)
        box.isFocusable = false; box.isClickable = false
        box.addView(Ui.label(this, "دعاء — " + dua.note, 18f, ut.dim).apply { gravity = Gravity.CENTER })
        box.addView(Ui.label(this, dua.text, 26f, Color.parseColor("#E8C98A")).apply {
            gravity = Gravity.CENTER
            setLineSpacing(0f, 1.25f)
        }, Ui.lpm(Ui.MATCH, Ui.WRAP, 0f, 0, px(4f), 0, px(2f)))
        box.addView(Ui.label(this, dua.source, 16f, ut.dim).apply { gravity = Gravity.CENTER })
        return box
    }

    private fun buildProMatchSection(forceVisible: Boolean = false, vertical: Boolean = false): View {
        val section = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            clipChildren = false
            clipToPadding = false
            if (com.uobidah.launcher.core.HomeLayouts.get(prefs.layoutIndex).template == HomeLayoutTemplate.DASHBOARD) tag = "r9-favorites-content"
        }
        section.visibility = if (prefs.showMatchFavorites || forceVisible) View.VISIBLE else View.GONE
        val heading = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        heading.addView(Ui.label(this, "⭐ الفرق والمباريات المفضلة", 25f, ut.text, true), Ui.lp(0, Ui.WRAP, 1f))
        heading.addView(Ui.button(this, ut, "▦ جدول الدوري", 17f) {
            startActivity(Intent(this, MatchesActivity::class.java).putExtra("panel_mode", 1))
        }, Ui.lpm(Ui.WRAP, Ui.WRAP, 0f, 0, 0, px(8f), 0))
        heading.addView(Ui.button(this, ut, "عرض الكل", 18f) { startActivity(Intent(this, MatchesActivity::class.java)) })
        section.addView(heading)
        section.addView(buildFavSection(forceVisible, vertical), Ui.lp(Ui.MATCH, Ui.WRAP))
        return section
    }

    private fun buildClassicColumn(): LinearLayout {
        val col = LinearLayout(this)
        col.orientation = LinearLayout.VERTICAL
        col.clipChildren = false
        col.clipToPadding = false
        col.setPadding(px(56f), px(32f), px(56f), px(26f))

        val spec = com.uobidah.launcher.core.HomeLayouts.get(prefs.layoutIndex)
        // تُبنى كل البطاقات دائمًا (حقول lateinit تُحدَّث من tick) ثم يُضاف منها ما يحدده التخطيط فقط.
        val cardsView = buildCards(spec)
        for (sec in spec.order) {
            when (sec) {
                com.uobidah.launcher.core.HomeSection.TOP ->
                    col.addView(buildTopBar(), Ui.lp(Ui.MATCH, Ui.WRAP))
                com.uobidah.launcher.core.HomeSection.CARDS -> if (spec.cards.isNotEmpty()) {
                    val lp = if (spec.cardsHeightDp > 0) Ui.lpm(Ui.MATCH, px(spec.cardsHeightDp.toFloat()), 0f, 0, px(18f), 0, px(14f))
                        else Ui.lpm(Ui.MATCH, 0, 1f, 0, px(18f), 0, px(14f))
                    col.addView(cardsView, lp)
                }
                com.uobidah.launcher.core.HomeSection.PINNED ->
                    col.addView(buildPinnedSection(), Ui.lp(Ui.MATCH, Ui.WRAP))
                com.uobidah.launcher.core.HomeSection.FAV ->
                    col.addView(buildFavSection(), Ui.lpm(Ui.MATCH, Ui.WRAP, 0f, 0, px(2f), 0, px(8f)))
                com.uobidah.launcher.core.HomeSection.DOCK ->
                    col.addView(buildDock(), Ui.lpm(Ui.WRAP, Ui.WRAP, 0f, 0, px(14f), 0, 0).also { it.gravity = Gravity.CENTER_HORIZONTAL })
                com.uobidah.launcher.core.HomeSection.SPACE ->
                    col.addView(View(this), Ui.lp(Ui.MATCH, 0, 1f))
                com.uobidah.launcher.core.HomeSection.GRID ->
                    col.addView(buildAppGridSection(), Ui.lpm(Ui.MATCH, Ui.WRAP, 0f, 0, px(3f), 0, px(7f)))
                com.uobidah.launcher.core.HomeSection.CHANNELS ->
                    col.addView(buildChannelsSection(), Ui.lpm(Ui.MATCH, Ui.WRAP, 0f, 0, px(3f), 0, px(7f)))
            }
        }

        return col
    }

    private fun buildTopBar(): View {
        val top = LinearLayout(this)
        top.orientation = LinearLayout.HORIZONTAL
        top.gravity = Gravity.CENTER_VERTICAL
        top.clipChildren = false

        val logo = LinearLayout(this)
        logo.orientation = LinearLayout.VERTICAL
        logo.addView(Ui.label(this, "Abo Uobidah TV", 54f, ut.accent, true))
        logo.addView(Ui.label(this, "Smart Launcher  •  عُبيدالله لانشر", 24f, ut.dim))
        logo.visibility = if (prefs.showBranding) View.VISIBLE else View.GONE
        val club = ut.club
        if (club != null && prefs.showBranding) {
            logo.addView(
                Ui.label(this, "🏆 " + club.emoji + " " + club.ar + "  •  " + club.motto, 24f, club.secondary, true),
                Ui.lpm(Ui.WRAP, Ui.WRAP, 0f, 0, px(4f), 0, 0)
            )
        }
        val classicClubLogo = club?.let { ClubLogos.res(it.art) } ?: 0
        if (classicClubLogo != 0 && prefs.showBranding) {
            val iv = ImageView(this)
            iv.setImageResource(classicClubLogo)
            iv.scaleType = ImageView.ScaleType.FIT_CENTER
            top.addView(iv, Ui.lpm(px(92f), px(92f), 0f, 0, 0, px(16f), 0))
        }
        top.addView(logo, Ui.lp(0, Ui.WRAP, 1f))

        val gap = px(16f)
        if (prefs.showWifiButton) {
            tvWifi = Ui.button(this, ut, "📶 ...", 26f) { openWifi() }
            Ui.ellipsize(tvWifi)
            top.addView(tvWifi, Ui.lpm(Ui.WRAP, Ui.WRAP, 0f, 0, 0, gap, 0))
        }
        if (prefs.showHdmiButton) top.addView(Ui.button(this, ut, "🔌 HDMI", 26f) { openHdmi() }, Ui.lpm(Ui.WRAP, Ui.WRAP, 0f, 0, 0, gap, 0))
        if (prefs.showBoosterButton) top.addView(Ui.button(this, ut, "🚀 معزز عُبيدة", 26f) { runBooster() }, Ui.lpm(Ui.WRAP, Ui.WRAP, 0f, 0, 0, gap, 0))
        if (prefs.showSearchButton) top.addView(Ui.button(this, ut, "🔍 البحث", 26f) {
            openAppSearch()
        }, Ui.lpm(Ui.WRAP, Ui.WRAP, 0f, 0, 0, gap, 0))
        if (prefs.showNotificationButton) top.addView(Ui.button(this, ut, "🔔 الإشعارات", 26f) {
            startActivity(Intent(this, NotificationCenterActivity::class.java))
        }, Ui.lpm(Ui.WRAP, Ui.WRAP, 0f, 0, 0, gap, 0))
        return top
    }

    private fun cardEnabled(kind: com.uobidah.launcher.core.HomeCard): Boolean = when (kind) {
        com.uobidah.launcher.core.HomeCard.CLOCK -> prefs.showClockCard
        com.uobidah.launcher.core.HomeCard.WEATHER -> prefs.showWeatherCard
        com.uobidah.launcher.core.HomeCard.PRAYER -> prefs.showPrayerCard
    }

    /** تصاميم بطاقات مصمتة؛ لا تُستخدم خلفيات زجاجية شفافة في عناصر الشاشة. */
    private fun applyCardStyle(v: View, radius: Float = 30f) {
        val style = prefs.cardStyle
        if (style == 0) return
        val r = px(radius).toFloat()
        fun shape(fill: Int, border: Int, bw: Int) = GradientDrawable().apply {
            cornerRadius = r
            setColor(fill)
            setStroke(bw, border)
        }
        val normal: android.graphics.drawable.Drawable = when (style) {
            1 -> shape(Ui.over(ut.card, ut.bgBottom), ut.border, px(2f))
            2 -> GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                intArrayOf(ut.accent, ut.cardFocus)
            ).apply { cornerRadius = r }
            else -> shape(Ui.over(ut.cardFocus, ut.bgBottom), ut.accent, px(3f))
        }
        val focused = shape(Ui.over(ut.accent, ut.cardFocus), ut.accent, px(4f))
        v.background = StateListDrawable().apply {
            addState(intArrayOf(android.R.attr.state_focused), focused)
            addState(intArrayOf(), normal)
        }
    }

    private fun addCard(row: LinearLayout, card: View, weight: Float, last: Boolean) {
        row.addView(card, Ui.lpm(0, Ui.MATCH, weight, 0, 0, if (last) 0 else px(22f), 0))
    }

    private fun buildCards(spec: com.uobidah.launcher.core.HomeLayoutSpec): View {
        val clock = buildClockCard()
        val weatherC = buildWeatherCard()
        val prayer = buildPrayerCard()
        val row = LinearLayout(this)
        row.orientation = LinearLayout.HORIZONTAL
        row.layoutDirection = View.LAYOUT_DIRECTION_LTR
        row.clipChildren = false
        row.clipToPadding = false
        row.setPadding(px(8f), px(8f), px(8f), px(8f))
        applyCardStyle(clock); applyCardStyle(weatherC); applyCardStyle(prayer)
        clock.visibility = if (prefs.showClockCard) View.VISIBLE else View.GONE
        weatherC.visibility = if (prefs.showWeatherCard) View.VISIBLE else View.GONE
        prayer.visibility = if (prefs.showPrayerCard) View.VISIBLE else View.GONE
        val byKind = mapOf(
            com.uobidah.launcher.core.HomeCard.CLOCK to clock,
            com.uobidah.launcher.core.HomeCard.WEATHER to weatherC,
            com.uobidah.launcher.core.HomeCard.PRAYER to prayer
        )
        spec.cards.forEachIndexed { i, entry ->
            addCard(row, byKind.getValue(entry.first), entry.second, i == spec.cards.lastIndex)
        }
        if (spec.cards.isNotEmpty() && spec.cards.none { cardEnabled(it.first) }) {
            row.addView(Ui.label(this, "افتح تخصيص الشاشة لإظهار البطاقات التي تريدها", 28f, ut.dim))
        }
        return row
    }

    private fun cardBox(): LinearLayout {
        val c = LinearLayout(this)
        c.orientation = LinearLayout.VERTICAL
        c.gravity = Gravity.CENTER_VERTICAL
        c.setPadding(px(34f), px(24f), px(34f), px(24f))
        return c
    }

    private fun buildClockCard(timeSize: Float = 140f): View {
        val c = cardBox()
        Ui.staticCard(c, ut, 34f)
        val row = LinearLayout(this)
        row.orientation = LinearLayout.HORIZONTAL
        row.gravity = Gravity.BOTTOM
        tvTime = Ui.label(this, "--:--", timeSize, ut.text, true)
        tvAmPm = Ui.label(this, "", 44f, ut.accent, true)
        row.addView(tvTime)
        row.addView(tvAmPm, Ui.lpm(Ui.WRAP, Ui.WRAP, 0f, px(14f), 0, 0, px(14f)))
        c.addView(row)
        tvDate = Ui.label(this, "", 34f, ut.text)
        tvHijri = Ui.label(this, "", 32f, ut.accent, true)
        tvHijri.visibility = if (prefs.showHijriDate) View.VISIBLE else View.GONE
        c.addView(tvDate, Ui.lpm(Ui.WRAP, Ui.WRAP, 0f, 0, px(12f), 0, 0))
        c.addView(tvHijri, Ui.lpm(Ui.WRAP, Ui.WRAP, 0f, 0, px(6f), 0, 0))
        return c
    }

    private fun buildWeatherCard(): View {
        val c = cardBox()
        Ui.focusable(c, ut, 34f)
        c.isClickable = true
        c.setOnClickListener {
            CityPicker.show(this, prefs, ut) {
                tvCity.text = "📍 " + prefs.cityName
                onSelectedCityChanged()
                tvWDesc.text = if (weather == null) "جاري التحميل..." else tvWDesc.text
            }
        }
        tvCity = Ui.label(this, "📍 " + prefs.cityName, 34f, ut.accent, true)
        Ui.ellipsize(tvCity)
        tvTemp = Ui.label(this, "--°", 110f, ut.text, true)
        tvWDesc = Ui.label(this, "جاري التحميل...", 36f, ut.text)
        tvWDetail = Ui.label(this, "", 25f, ut.dim)
        val hint = Ui.label(this, "اضغط لتغيير المدينة", 22f, ut.dim)
        c.addView(tvCity)
        c.addView(tvTemp, Ui.lpm(Ui.WRAP, Ui.WRAP, 0f, 0, px(6f), 0, 0))
        c.addView(tvWDesc)
        c.addView(tvWDetail, Ui.lpm(Ui.WRAP, Ui.WRAP, 0f, 0, px(8f), 0, 0))
        c.addView(hint, Ui.lpm(Ui.WRAP, Ui.WRAP, 0f, 0, px(8f), 0, 0))
        c.setOnLongClickListener { startActivity(Intent(this, HomeCustomizationActivity::class.java)); true }
        weatherCard = c
        return c
    }

    private fun buildPrayerCard(): View {
        val c = cardBox()
        Ui.focusable(c, ut, 34f)
        c.isClickable = true
        c.setOnClickListener {
            if (prefs.prayerCardOpensSettings) {
                startActivity(Intent(this, PrayerSettingsActivity::class.java))
            } else {
                prefs.adhanEnabled = !prefs.adhanEnabled
                toast(if (prefs.adhanEnabled) "🔔 تم تفعيل تشغيل الأذان تلقائياً" else "🔕 تم إيقاف تشغيل الأذان")
            }
        }
        c.setOnLongClickListener { startActivity(Intent(this, PrayerSettingsActivity::class.java)); true }
        c.addView(Ui.label(this, "🕌 مواقيت الصلاة", 28f, ut.dim, true))
        tvPNext = Ui.label(this, "الصلاة القادمة", 36f, ut.accent, true)
        tvPCount = Ui.label(this, "--:--:--", 66f, ut.text, true)
        c.addView(tvPNext, Ui.lpm(Ui.WRAP, Ui.WRAP, 0f, 0, px(6f), 0, 0))
        c.addView(tvPCount, Ui.lpm(Ui.WRAP, Ui.WRAP, 0f, 0, px(4f), 0, px(14f)))

        val strip = LinearLayout(this)
        strip.orientation = LinearLayout.HORIZONTAL
        for (k in PrayerRepo.keys) {
            val cell = LinearLayout(this)
            cell.orientation = LinearLayout.VERTICAL
            cell.gravity = Gravity.CENTER_HORIZONTAL
            val n = Ui.label(this, prayerArabicLabel(k), 23f, ut.dim)
            val t = Ui.label(this, "--:--", 26f, ut.text, true)
            cell.setPadding(px(4f), px(8f), px(4f), px(8f))
            if (k != "Sunrise") openPrayerSettingsOnClick(cell, k, 16f)
            cell.addView(n)
            cell.addView(t, Ui.lpm(Ui.WRAP, Ui.WRAP, 0f, 0, px(4f), 0, 0))
            prayerNames[k] = n
            prayerTimes[k] = t
            strip.addView(cell, Ui.lp(0, Ui.WRAP, 1f))
        }
        c.addView(strip, Ui.lp(Ui.MATCH, Ui.WRAP))
        return c
    }

    private fun buildPinnedSection(tv: Boolean = false): View {
        val box = LinearLayout(this)
        box.orientation = LinearLayout.VERTICAL
        box.clipChildren = false
        val alwaysShowApps = com.uobidah.launcher.core.HomeLayouts.get(prefs.layoutIndex).template in
            setOf(HomeLayoutTemplate.DUSK_BOARD, HomeLayoutTemplate.DASHBOARD)
        box.visibility = if (prefs.showPinnedApps || alwaysShowApps) View.VISIBLE else View.GONE
        val scroll = HorizontalScrollView(this)
        scroll.isHorizontalScrollBarEnabled = false
        scroll.clipChildren = false
        scroll.clipToPadding = false
        scroll.setPadding(0, px(14f), 0, px(14f))
        pinnedRow = LinearLayout(this)
        pinnedRow.orientation = LinearLayout.HORIZONTAL
        pinnedRow.layoutDirection = View.LAYOUT_DIRECTION_LTR
        pinnedRow.clipChildren = false
        scroll.addView(pinnedRow, FrameLayout.LayoutParams(Ui.WRAP, Ui.WRAP))
        box.addView(scroll, Ui.lp(Ui.MATCH, Ui.WRAP))
        return box
    }

    private fun buildDock(): View = buildDockStyled(false)

    private fun dockItemList(): List<Triple<Int, String, String>> {
        // LinearLayout أفقي داخل واجهة RTL يبدأ من اليمين؛ نعكس ترتيب الإنشاء
        // ليظهر بصريًا من اليسار إلى اليمين: الرئيسية ← ... ← الإعدادات.
        val items = mutableListOf<Triple<Int, String, String>>()
        if (prefs.dockSettings) items.add(Triple(6, "⚙️", "الإعدادات"))
        if (prefs.dockVideo) items.add(Triple(4, "🎬", "IPTV"))
        if (prefs.dockApps) items.add(Triple(2, "🧩", "تطبيقات"))
        if (prefs.dockMatches) items.add(Triple(1, "⚽", "مباريات"))
        if (prefs.dockWallpapers) items.add(Triple(5, "🖼️", "الخلفيات"))
        if (prefs.dockRadio) items.add(Triple(3, "📻", "قرآن وإذاعة"))
        items.add(Triple(0, "🏠", "الرئيسية"))
        return items
    }

    /** الشريط السفلي بأربعة تصاميم: 0 أساسي، 1 أيقونات فقط، 2 شريط ملتصق، 3 زجاجي. */
    /** نص بسطر واحد يصغّر نفسه بدل أن ينكسر داخل الكلمة العربية. */
    private fun fitOneLine(t: TextView, maxSp: Float): TextView {
        t.maxLines = 1
        t.setHorizontallyScrolling(false)
        t.setAutoSizeTextTypeUniformWithConfiguration(px(11f), px(maxSp), px(1f), android.util.TypedValue.COMPLEX_UNIT_PX)
        return t
    }

    private fun buildDockStyled(tv: Boolean): View {
        val style = prefs.dockStyle
        val dock = LinearLayout(this)
        dock.orientation = LinearLayout.HORIZONTAL
        dock.gravity = Gravity.CENTER
        dock.clipChildren = false
        dock.visibility = if (prefs.showHomeDock) View.VISIBLE else View.GONE
        when (style) {
            2 -> {
                dock.setPadding(px(14f), px(10f), px(14f), px(10f))
                val c = px(30f).toFloat()
                dock.background = GradientDrawable().apply {
                    setColor(ut.dock)
                    cornerRadii = floatArrayOf(c, c, c, c, 0f, 0f, 0f, 0f)
                    setStroke(px(2f), ut.dockBorder)
                }
            }
            3 -> {
                dock.setPadding(px(12f), px(8f), px(12f), px(8f))
                dock.background = Ui.round(Color.argb(60, 255, 255, 255), px(60f).toFloat(), px(1f), Color.argb(90, 255, 255, 255))
            }
            else -> {
                dock.setPadding(px(if (tv) 10f else 14f), px(if (tv) 7f else 10f), px(if (tv) 10f else 14f), px(if (tv) 7f else 10f))
                dock.background = Ui.round(ut.dock, px((if (tv) 40f else 52f) * ut.radiusScale).toFloat(), px(2f), ut.dockBorder)
            }
        }
        val dockItems = dockItemList()
        for ((id, emoji, label) in dockItems) {
            val item = LinearLayout(this)
            item.gravity = Gravity.CENTER
            val horizontal = style == 3 || (style == 0 && tv)
            if (horizontal) {
                item.orientation = LinearLayout.HORIZONTAL
                item.layoutDirection = View.LAYOUT_DIRECTION_LTR // الأيقونة على يسار النص
                item.setPadding(px(10f), px(8f), px(10f), px(8f))
                item.addView(Ui.label(this, emoji, 32f, ut.text))
                item.addView(fitOneLine(Ui.label(this, label, 24f, ut.text, true), 24f), Ui.lpm(0, Ui.WRAP, 1f, px(10f), 0, 0, 0))
            } else {
                item.orientation = LinearLayout.VERTICAL
                item.setPadding(px(14f), px(8f), px(14f), px(8f))
                item.addView(Ui.label(this, emoji, if (style == 1) 48f else if (style == 2) 36f else 40f, ut.text))
                if (style != 1) {
                    val lb = Ui.label(this, label, if (style == 2) 20f else 22f, ut.text, true)
                    lb.gravity = Gravity.CENTER
                    fitOneLine(lb, if (style == 2) 20f else 22f)
                    item.addView(lb, Ui.lpm(Ui.MATCH, Ui.WRAP, 0f, 0, px(2f), 0, 0))
                }
            }
            val highlight = id == 0 && (style == 2 || style == 3 || (style == 0 && tv))
            Ui.focusable(item, ut, 30f, if (highlight) Ui.withAlpha(ut.accent, 150) else Color.TRANSPARENT)
            item.isClickable = true
            item.setOnClickListener { onDock(id) }
            val lp = if (tv) Ui.lpm(0, Ui.WRAP, 1f, px(3f), 0, px(3f), 0)
                else Ui.lpm(px(if (style == 1) 110f else if (style == 0) 188f else 160f), Ui.WRAP, 0f, px(4f), 0, px(4f), 0)
            dock.addView(item, lp)
        }
        return dock
    }

    /** القرآن والأذكار والإذاعة: نافذة اختيار بدل فتح الإذاعة مباشرة. */
    private fun openQuranHub() {
        val box = LinearLayout(this)
        box.orientation = LinearLayout.VERTICAL
        box.addView(Ui.label(this, "اختر ما تريد فتحه", 26f, ut.dim))
        Ui.modal(this, ut, "📖 القرآن والأذكار والإذاعة", box,
            Btn("📖 القرآن") { startActivity(Intent(this, QuranActivity::class.java)) },
            Btn("☀ أذكار الصباح") { startActivity(Intent(this, AdhkarActivity::class.java).putExtra("kind", "morning")) },
            Btn("🌙 أذكار المساء") { startActivity(Intent(this, AdhkarActivity::class.java).putExtra("kind", "evening")) },
            Btn("📻 الإذاعة") { startActivity(Intent(this, RadioActivity::class.java)) },
            Btn("إغلاق") {})
    }

    private fun onDock(i: Int) {
        when (i) {
            0 -> (if (prefs.showWeatherCard) weatherCard else if (prefs.showClockCard) tvTime else if (prefs.showPrayerCard) tvPNext else null)?.requestFocus()
            1 -> startActivity(Intent(this, MatchesActivity::class.java))
            2 -> startActivity(Intent(this, AppsActivity::class.java))
            3 -> openQuranHub()
            4 -> startActivity(Intent(this, IptvProActivity::class.java))
            5 -> startActivity(Intent(this, WallpaperActivity::class.java))
            else -> startActivity(Intent(this, SettingsActivity::class.java))
        }
    }


    // =====================================================================
    // تصميم أبو عبيدة (TV) — التخطيط الخامس
    // =====================================================================
    private fun openPrayerSettingsOnClick(cell: View, key: String, radius: Float) {
        Ui.focusable(cell, ut, radius, Color.TRANSPARENT)
        cell.isClickable = true
        cell.setOnClickListener {
            val i = Intent(this, PrayerSettingsActivity::class.java)
            i.putExtra("prayer", key)
            startActivity(i)
        }
    }

    private fun buildTvColumn(): LinearLayout {
        val col = LinearLayout(this)
        col.orientation = LinearLayout.VERTICAL
        col.clipChildren = false
        col.clipToPadding = false
        col.setPadding(px(44f), px(22f), px(44f), px(18f))
        col.addView(buildTvTopBar(), Ui.lp(Ui.MATCH, Ui.WRAP))
        col.addView(buildTvCards(), Ui.lpm(Ui.MATCH, 0, 1f, 0, px(10f), 0, px(6f)))
        col.addView(buildPinnedSection(true), Ui.lp(Ui.MATCH, Ui.WRAP))
        col.addView(buildFavSection(), Ui.lpm(Ui.MATCH, Ui.WRAP, 0f, 0, px(2f), 0, px(8f)))
        col.addView(buildTvDock(), Ui.lp(Ui.MATCH, Ui.WRAP))
        return col
    }

    private fun versionName(): String = try {
        packageManager.getPackageInfo(packageName, 0).versionName ?: ""
    } catch (e: Exception) {
        ""
    }

    private fun topIcon(emoji: String, label: String, onClick: () -> Unit): View {
        // أيقونات دائرية زجاجية صغيرة مطابقة لترتيب أدوات المرجع البصري.
        val item = LinearLayout(this)
        item.orientation = LinearLayout.VERTICAL
        item.gravity = Gravity.CENTER
        item.setPadding(px(10f), px(5f), px(10f), px(5f))
        val icon = Ui.label(this, emoji, 34f, ut.text, true)
        icon.gravity = Gravity.CENTER
        val circle = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(Color.argb(42, 90, 120, 165))
            setStroke(px(2f), Ui.withAlpha(ut.text, 70))
        }
        icon.background = circle
        item.addView(icon, Ui.lp(px(58f), px(58f)))
        val lb = Ui.label(this, label, 19f, ut.dim)
        lb.gravity = Gravity.CENTER
        item.addView(lb, Ui.lpm(Ui.WRAP, Ui.WRAP, 0f, 0, px(3f), 0, 0))
        Ui.focusable(item, ut, 28f)
        item.isClickable = true
        item.setOnClickListener { onClick() }
        Motion.enter(item, this)
        return item
    }

    private fun buildTvTopBar(): View {
        val top = LinearLayout(this)
        top.orientation = LinearLayout.HORIZONTAL
        top.gravity = Gravity.CENTER_VERTICAL
        top.clipChildren = false
        top.clipToPadding = false
        top.setPadding(px(6f), px(6f), px(6f), px(6f))

        // الشعار (أقصى اليمين)
        val logo = ImageView(this)
        val clubLogo = ut.club?.let { ClubLogos.res(it.art) } ?: 0
        if (clubLogo != 0) {
            // شعار النادي كاملًا: FIT_CENTER بدون قص دائري ولا تمدد.
            logo.setImageResource(clubLogo)
            logo.scaleType = ImageView.ScaleType.FIT_CENTER
            logo.adjustViewBounds = true
            top.addView(logo, Ui.lpm(px(96f), px(96f), 0f, 0, 0, px(16f), 0))
        } else {
            // الأيقونة كاملة: بدون قص دائري (كان يقصّ الحواف ويبدو متآكلاً)
            logo.setImageResource(R.drawable.ic_launcher)
            logo.scaleType = ImageView.ScaleType.FIT_CENTER
            logo.adjustViewBounds = true
            top.addView(logo, Ui.lpm(px(92f), px(92f), 0f, 0, 0, px(16f), 0))
        }
        // دخول متحرك للشعار: تكبير مع ارتداد خفيف
        if (Motion.enabled(this)) {
            logo.alpha = 0f
            logo.scaleX = 0.6f
            logo.scaleY = 0.6f
            logo.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(520L)
                .setInterpolator(android.view.animation.OvershootInterpolator(1.6f)).start()
        }

        logo.visibility = if (prefs.showBranding) View.VISIBLE else View.GONE
        val ver = Ui.label(this, versionName(), 22f, ut.text, true)
        ver.setPadding(px(22f), px(8f), px(22f), px(8f))
        ver.background = Ui.round(ut.card, px(30f).toFloat(), px(2f), ut.border)
        ver.visibility = if (prefs.showBranding) View.VISIBLE else View.GONE
        top.addView(ver, Ui.lpm(Ui.WRAP, Ui.WRAP, 0f, 0, 0, px(18f), 0))

        val titles = LinearLayout(this)
        titles.visibility = if (prefs.showBranding) View.VISIBLE else View.GONE
        titles.orientation = LinearLayout.VERTICAL
        titles.addView(Ui.label(this, "Abo Uobidah TV", 52f, ut.text, true))
        titles.addView(Ui.label(this, "Smart Launcher", 24f, ut.dim))
        val club = ut.club
        if (club != null) {
            titles.addView(
                Ui.label(this, "🏆 " + club.emoji + " " + club.ar, 22f, club.secondary, true),
                Ui.lpm(Ui.WRAP, Ui.WRAP, 0f, 0, px(2f), 0, 0)
            )
        }
        top.addView(titles, Ui.lp(0, Ui.WRAP, 1f))

        // مرجع التلفزيون يحتوي أربعة اختصارات دائرية فقط؛ تبقى Wi-Fi وHDMI والراديو
        // متاحة من إعداداتها/الشريط السفلي حتى لا تزاحم الهوية أعلى الشاشة.
        if (prefs.showSearchButton) top.addView(topIcon("🔍", "بحث") { openAppSearch() })
        if (prefs.showBoosterButton) top.addView(topIcon("🚀", "تسريع الجهاز") { runBooster() })
        if (prefs.showHdmiButton) top.addView(topIcon("🔌", "HDMI") { openHdmi() })
        if (prefs.showNotificationButton) top.addView(topIcon("🔔", "الإشعارات") { startActivity(Intent(this, NotificationCenterActivity::class.java)) })
        return top
    }

    /** خلفيات متدرجة ذات حواف مضيئة مثل مرجع الشاشة، مع حالة تركيز مستقلة للريموت. */
    private fun tvSurface(view: View, colors: IntArray, stroke: Int) {
        fun shape(border: Int, width: Int): GradientDrawable = GradientDrawable(
            GradientDrawable.Orientation.TL_BR, colors
        ).apply {
            cornerRadius = px(28f).toFloat()
            setStroke(width, border)
        }
        val states = StateListDrawable().apply {
            addState(intArrayOf(android.R.attr.state_focused), shape(ut.accent, px(4f)))
            addState(intArrayOf(), shape(stroke, px(1f)))
        }
        view.background = states
    }

    private fun buildTvCards(): View {
        val row = LinearLayout(this)
        row.orientation = LinearLayout.HORIZONTAL
        row.layoutDirection = View.LAYOUT_DIRECTION_LTR
        row.clipChildren = false
        row.clipToPadding = false
        row.setPadding(px(6f), px(6f), px(6f), px(6f))
        val clock = buildClockCard(112f)
        val weather = buildTvWeather()
        val prayer = buildTvPrayer()
        tvSurface(clock, intArrayOf(Color.rgb(8, 18, 42), Color.rgb(4, 8, 25)), Color.rgb(105, 151, 255))
        tvSurface(weather, intArrayOf(Color.rgb(8, 28, 58), Color.rgb(3, 11, 28)), Color.rgb(80, 126, 185))
        tvSurface(prayer, intArrayOf(Color.rgb(5, 18, 37), Color.rgb(2, 9, 22)), Color.rgb(28, 190, 112))
        applyCardStyle(clock); applyCardStyle(weather); applyCardStyle(prayer)
        clock.visibility = if (prefs.showClockCard) View.VISIBLE else View.GONE
        weather.visibility = if (prefs.showWeatherCard) View.VISIBLE else View.GONE
        prayer.visibility = if (prefs.showPrayerCard) View.VISIBLE else View.GONE
        addCard(row, clock, 1f, false)
        addCard(row, weather, 1.4f, false)
        addCard(row, prayer, 1.45f, true)
        if (!prefs.showClockCard && !prefs.showWeatherCard && !prefs.showPrayerCard) {
            row.addView(Ui.label(this, "افتح تخصيص الشاشة لإظهار البطاقات التي تريدها", 28f, ut.dim))
        }
        return row
    }

    private fun buildTvWeather(): View {
        val c = cardBox()
        val dashboard = com.uobidah.launcher.core.HomeLayouts.get(prefs.layoutIndex).template == HomeLayoutTemplate.DASHBOARD
        c.tag = if (dashboard) "r9-weather-card" else null
        c.setPadding(
            px(if (dashboard) 18f else 30f), px(if (dashboard) 10f else 18f),
            px(if (dashboard) 18f else 30f), px(if (dashboard) 10f else 18f)
        )
        Ui.focusable(c, ut, 30f)
        c.isClickable = true
        c.setOnClickListener {
            CityPicker.show(this, prefs, ut) {
                tvCity.text = "📍 " + prefs.cityName
                onSelectedCityChanged()
                tvWDesc.text = if (weather == null) "جاري التحميل..." else tvWDesc.text
            }
        }
        tvCity = Ui.label(this, "📍 " + prefs.cityName, if (dashboard) 22f else 29f, ut.accent, true).apply {
            tag = if (dashboard) "r9-weather-city" else null
            isSingleLine = false
            maxLines = 2
            ellipsize = android.text.TextUtils.TruncateAt.END
            includeFontPadding = false
        }
        tvTemp = Ui.label(this, "--°", if (dashboard) 62f else 88f, ut.text, true).apply {
            tag = if (dashboard) "r9-weather-temperature" else null
            maxLines = 1
        }
        tvWDesc = Ui.label(this, "جاري التحميل...", if (dashboard) 20f else 30f, ut.text).apply {
            tag = if (dashboard) "r9-weather-condition" else null
            isSingleLine = false
            maxLines = 2
            ellipsize = android.text.TextUtils.TruncateAt.END
            includeFontPadding = false
        }
        tvWDetail = Ui.label(this, "", if (dashboard) 15f else 21f, ut.dim).apply {
            tag = if (dashboard) "r9-weather-detail" else null
            isSingleLine = false
            maxLines = 2
            ellipsize = android.text.TextUtils.TruncateAt.END
            includeFontPadding = false
            contentDescription = "تفاصيل الطقس: العظمى والصغرى والرطوبة وآخر تحديث"
        }
        if (com.uobidah.launcher.core.HomeLayouts.get(prefs.layoutIndex).template == HomeLayoutTemplate.DASHBOARD) {
            // R9: قمر بالطور الفعلي في أعلى بطاقة الطقس بجانب اسم المدينة (رسم هندسي، ليس صورة فوتوغرافية)
            val moonAge = com.uobidah.launcher.core.MoonPhase.ageDays(System.currentTimeMillis())
            val moon = ImageView(this).apply {
                tag = "r9-moon"
                setImageDrawable(MoonDrawable(moonAge))
                contentDescription = com.uobidah.launcher.core.MoonPhase.nameAr(moonAge) + " " + com.uobidah.launcher.core.MoonPhase.illuminationPercent(moonAge) + "٪"
            }
            val top = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; layoutDirection = View.LAYOUT_DIRECTION_LTR; gravity = Gravity.CENTER_VERTICAL }
            top.addView(tvCity, Ui.lp(0, Ui.WRAP, 1f))
            top.addView(moon, Ui.lpm(px(if (dashboard) 60f else 84f), px(if (dashboard) 60f else 84f), 0f, px(10f), 0, 0, 0))
            c.addView(top, Ui.lpm(Ui.MATCH, Ui.WRAP, 0f, 0, 0, 0, 0))
        } else c.addView(tvCity, Ui.lpm(Ui.MATCH, Ui.WRAP, 0f, 0, 0, 0, 0))
        // Stack condition and detail below the main temperature. This gives Arabic text
        // the card's full width instead of forcing it beside an oversized temperature figure.
        val mid = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_VERTICAL }
        mid.addView(tvTemp, Ui.lpm(Ui.MATCH, Ui.WRAP, 0f, 0, px(2f), 0, 0))
        mid.addView(tvWDesc, Ui.lpm(Ui.MATCH, Ui.WRAP, 0f, 0, px(2f), 0, 0))
        c.addView(mid, Ui.lpm(Ui.MATCH, Ui.WRAP, 0f, 0, px(2f), 0, 0))
        c.addView(tvWDetail, Ui.lpm(Ui.MATCH, Ui.WRAP, 0f, 0, px(6f), 0, px(10f)))
        val fr = LinearLayout(this)
        fr.orientation = LinearLayout.HORIZONTAL
        fr.layoutDirection = View.LAYOUT_DIRECTION_LTR
        fr.layoutDirection = View.LAYOUT_DIRECTION_RTL
        fr.tag = if (dashboard) "r9-weather-forecast" else null
        forecastRow = fr
        c.addView(fr, Ui.lp(Ui.MATCH, Ui.WRAP))
        weatherCard = c
        return c
    }

    private fun renderForecast(w: Weather) {
        val fr = forecastRow ?: return
        fr.removeAllViews()
        for (d in w.days.take(5)) {
            val cell = LinearLayout(this)
            cell.orientation = LinearLayout.VERTICAL
            cell.gravity = Gravity.CENTER_HORIZONTAL
            if (com.uobidah.launcher.core.HomeLayouts.get(prefs.layoutIndex).template == HomeLayoutTemplate.DASHBOARD) {
                cell.tag = "r9-weather-forecast-day"
                cell.clipChildren = false
                cell.clipToPadding = false
            }
            val dashboardForecast = cell.tag == "r9-weather-forecast-day"
            val forecastTextWidth = if (dashboardForecast) Ui.MATCH else Ui.WRAP
            val dn = d.date.dayOfWeek.getDisplayName(TextStyle.FULL, Locale("ar"))
            cell.addView(Ui.label(
                this, dn,
                if (dashboardForecast) 15f else 21f,
                ut.dim
            ).apply {
                if (dashboardForecast) tag = "r9-weather-forecast-day-name"
                maxLines = if (dashboardForecast) 2 else 1
                ellipsize = android.text.TextUtils.TruncateAt.END
                gravity = Gravity.CENTER
                includeFontPadding = false
                contentDescription = dn
            }, Ui.lpm(forecastTextWidth, Ui.WRAP, 0f, 0, 0, 0, 0))
            cell.addView(
                Ui.label(
                    this, WeatherRepo.describe(d.code, true).first,
                    if (dashboardForecast) 18f else 30f,
                    ut.text
                ).apply {
                    if (dashboardForecast) tag = "r9-weather-forecast-condition"
                    maxLines = if (dashboardForecast) 2 else 1
                    ellipsize = android.text.TextUtils.TruncateAt.END
                    gravity = Gravity.CENTER
                    includeFontPadding = false
                    contentDescription = WeatherRepo.describe(d.code, true).first
                },
                Ui.lpm(forecastTextWidth, Ui.WRAP, 0f, 0, px(if (dashboardForecast) 2f else 4f), 0, 0)
            )
            val range = Math.round(d.hi).toString() + "/" + Math.round(d.lo) + "°"
            cell.addView(Ui.label(
                this, range,
                if (dashboardForecast) 17f else 22f,
                ut.text, true
            ).apply {
                if (dashboardForecast) tag = "r9-weather-forecast-range"
                maxLines = 1
                gravity = Gravity.CENTER
                includeFontPadding = false
            }, Ui.lpm(forecastTextWidth, Ui.WRAP, 0f, 0, px(1f), 0, 0))
            fr.addView(cell, Ui.lp(0, Ui.WRAP, 1f))
        }
    }

    private fun prayerIcon(k: String): String = when (k) {
        "Fajr" -> "🌄"
        "Dhuhr" -> "☀️"
        "Asr" -> "🌤️"
        "Maghrib" -> "🌇"
        else -> "🌙"
    }

    private fun prayerSub(k: String): String = when (k) {
        "Fajr" -> "صباحاً"
        "Dhuhr" -> if (PrayerRepo.localNow(prefs).dayOfWeek == java.time.DayOfWeek.FRIDAY) "جمعة" else "ظهراً"
        "Asr" -> "عصراً"
        else -> "مساءً"
    }

    private var archPrayer = false
    private var archHighlight = false
    private var prayerGridView: View? = null

    private fun buildTvPrayer(): View {
        val c = cardBox()
        val dashboard = com.uobidah.launcher.core.HomeLayouts.get(prefs.layoutIndex).template == HomeLayoutTemplate.DASHBOARD
        val narrow = false
        c.tag = if (dashboard) "r9-prayer-card" else null
        c.setPadding(
            px(if (dashboard) 18f else if (narrow) 14f else 26f),
            px(if (dashboard) 10f else 14f),
            px(if (dashboard) 18f else if (narrow) 14f else 26f),
            px(if (dashboard) 10f else 14f)
        )
        Ui.staticCard(c, ut, 30f)
        val head = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        head.addView(Ui.label(this, "🕌 مواقيت الصلاة", if (dashboard) 22f else 28f, ut.text, true), Ui.lpm(Ui.MATCH, Ui.WRAP, 0f, 0, 0, 0, 0))
        head.addView(Ui.label(this, "اضغط على أي صلاة لضبط تنبيهاتها", 18f, ut.dim).apply {
            isSingleLine = dashboard
            maxLines = if (dashboard) 1 else 2
            if (dashboard) {
                textSize = 14f
                ellipsize = android.text.TextUtils.TruncateAt.END
            }
            includeFontPadding = false
            contentDescription = "اضغط على أي صلاة لضبط تنبيهاتها"
        }, Ui.lpm(Ui.MATCH, Ui.WRAP, 0f, 0, px(2f), 0, 0))
        c.addView(head)

        // Two real columns of prayer cards. Each cell has its own full text area,
        // rather than five narrow columns that force Arabic names and times to clip.
        val prayerGrid = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            clipChildren = false
            clipToPadding = false
            tag = if (dashboard) "r9-prayer-grid" else null
        }
        PrayerRepo.adhanKeys.toList().chunked(if (archPrayer) 3 else 2).forEachIndexed { rowIndex, keys ->
            val gridRow = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                layoutDirection = View.LAYOUT_DIRECTION_RTL
                clipChildren = false
                clipToPadding = false
                tag = if (dashboard) "r9-prayer-grid-row" else null
            }
            keys.forEach { k ->
                val cell = LinearLayout(this).apply {
                    orientation = LinearLayout.HORIZONTAL
                    layoutDirection = View.LAYOUT_DIRECTION_RTL
                    gravity = Gravity.CENTER_VERTICAL
                    setPadding(
                        px(if (dashboard) 7f else if (narrow) 5f else 10f),
                        px(if (dashboard) 1f else 6f),
                        px(if (dashboard) 7f else if (narrow) 5f else 10f),
                        px(if (dashboard) 1f else 6f)
                    )
                    background = Ui.round(Ui.over(ut.cardFocus, ut.bgBottom), px(17f).toFloat(), px(1f), ut.border)
                    clipChildren = false
                    tag = if (dashboard) "r9-prayer-cell-$k" else null
                }
                val icon = Ui.label(this, prayerIcon(k), if (dashboard) 22f else if (narrow) 20f else 28f, ut.text, true).apply { gravity = Gravity.CENTER }
                cell.addView(icon, Ui.lpm(
                    px(if (dashboard) 30f else if (narrow) 28f else 42f), Ui.MATCH, 0f, 0, 0,
                    px(if (dashboard) 4f else if (narrow) 4f else 8f), 0
                ))
                val textColumn = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_VERTICAL }
                val n = Ui.label(this, prayerArabicLabel(k), if (dashboard) 18f else if (narrow) 15f else 20f, ut.dim, true).apply {
                    tag = if (dashboard) "r9-prayer-name-$k" else null
                    maxLines = 1
                    ellipsize = android.text.TextUtils.TruncateAt.END
                    includeFontPadding = false
                    contentDescription = prayerArabicLabel(k)
                }
                val t = Ui.label(this, "--:--", if (dashboard) 24f else if (narrow) 21f else 29f, ut.text, true).apply {
                    tag = if (dashboard) "r9-prayer-time-$k" else null
                    maxLines = 1
                    ellipsize = android.text.TextUtils.TruncateAt.END
                    includeFontPadding = false
                    contentDescription = prayerArabicLabel(k)
                }
                val sub = Ui.label(this, prayerSub(k), if (dashboard) 13f else 15f, ut.dim).apply {
                    tag = if (dashboard) "r9-prayer-sub-$k" else null
                    maxLines = 1
                    ellipsize = android.text.TextUtils.TruncateAt.END
                    includeFontPadding = false
                }
                textColumn.addView(n, Ui.lpm(Ui.MATCH, Ui.WRAP, 0f, 0, 0, 0, 0))
                textColumn.addView(t, Ui.lpm(Ui.MATCH, Ui.WRAP, 0f, 0, px(1f), 0, 0))
                textColumn.addView(sub, Ui.lpm(Ui.MATCH, Ui.WRAP, 0f, 0, px(1f), 0, 0))
                cell.addView(textColumn, Ui.lp(0, Ui.WRAP, 1f))
                prayerNames[k] = n
                prayerSubs[k] = sub
                prayerTimes[k] = t
                openPrayerSettingsOnClick(cell, k, 17f)
                gridRow.addView(cell, Ui.lpm(0, px(if (dashboard) 68f else 66f), 1f, px(4f), 0, px(4f), 0))
            }
            if (keys.size == 1) gridRow.addView(View(this), Ui.lpm(0, px(if (dashboard) 68f else 66f), 1f, px(4f), 0, px(4f), 0))
            prayerGrid.addView(gridRow, Ui.lpm(Ui.MATCH, Ui.WRAP, 0f, 0, 0, 0, px(if (rowIndex == 2) 0f else if (dashboard) 4f else 5f)))
        }
        if (archPrayer) {
            // mihrab-style countdown column (reference: arch card with next prayer and countdown); the cells go to the left pane
            prayerGridView = prayerGrid
            c.removeAllViews()
            c.gravity = Gravity.CENTER_HORIZONTAL
            c.setPadding(px(26f), px(70f), px(26f), px(24f))
            val big = px(170f).toFloat()
            c.background = GradientDrawable().apply {
                setColor(Ui.over(ut.cardFocus, ut.bgBottom))
                cornerRadii = floatArrayOf(big, big, big, big, px(26f).toFloat(), px(26f).toFloat(), px(26f).toFloat(), px(26f).toFloat())
                setStroke(px(3f), Ui.withAlpha(ut.text, 200))
            }
            c.addView(Ui.label(this, "🕌 مواقيت الصلاة", 24f, ut.dim, true).apply { gravity = Gravity.CENTER })
            tvPNext = Ui.label(this, "الصلاة القادمة", 34f, Color.parseColor("#E8C98A"), true).apply { gravity = Gravity.CENTER }
            tvPCount = Ui.label(this, "--:--:--", 60f, ut.text, true).apply { gravity = Gravity.CENTER }
            c.addView(tvPNext, Ui.lpm(Ui.MATCH, Ui.WRAP, 0f, 0, px(18f), 0, px(6f)))
            c.addView(tvPCount, Ui.lpm(Ui.MATCH, Ui.WRAP, 0f, 0, px(6f), 0, px(14f)))
            // سطر الأذان (مرجع R2): «الأذان ٣:٥١ م، المدينة»؛ يُملأ من renderCountdown بالوقت الفعلي فقط.
            tvPAdhan = Ui.label(this, "", 20f, ut.dim).apply { gravity = Gravity.CENTER; maxLines = 1 }
            c.addView(tvPAdhan, Ui.lpm(Ui.MATCH, Ui.WRAP, 0f, 0, 0, 0, px(4f)))
            tvPQibla = Ui.label(this, com.uobidah.launcher.core.Qibla.label(prefs.cityLat, prefs.cityLon).orEmpty(), 18f, Color.parseColor("#E8C98A")).apply { gravity = Gravity.CENTER; maxLines = 1 }
            c.addView(tvPQibla, Ui.lpm(Ui.MATCH, Ui.WRAP, 0f, 0, 0, 0, px(10f)))
            val bar2 = LinearLayout(this)
            bar2.orientation = LinearLayout.HORIZONTAL
            bar2.background = Ui.round(Ui.withAlpha(ut.text, 40), px(5f).toFloat())
            val fill2 = View(this); fill2.background = Ui.round(Ui.over(ut.accent, ut.bgBottom), px(5f).toFloat())
            val rest2 = View(this)
            bar2.addView(fill2, Ui.lp(0, px(7f), 0.001f)); bar2.addView(rest2, Ui.lp(0, px(7f), 1f))
            progFill = fill2; progRest = rest2
            c.addView(bar2, Ui.lpm(Ui.MATCH, px(7f), 0f, px(20f), px(8f), px(20f), 0))
            // آية موثّقة حرفيًا (ملف الآيات في core) بدل أي نص مولّد، ثم زر مواقيت اليوم كما في المرجع R2.
            val verse = com.uobidah.launcher.core.QuranVerses.PRAYER_TIMES
            val verseView = Ui.label(this, verse.text, 24f, Color.parseColor("#E8C98A")).apply {
                gravity = Gravity.CENTER
                maxLines = 3
                setLineSpacing(0f, 1.25f)
                layoutDirection = View.LAYOUT_DIRECTION_RTL
            }
            c.addView(verseView, Ui.lpm(Ui.MATCH, Ui.WRAP, 0f, px(8f), px(14f), px(8f), 0))
            c.addView(Ui.label(this, com.uobidah.launcher.core.QuranVerses.reference(verse), 16f, ut.dim).apply { gravity = Gravity.CENTER },
                Ui.lpm(Ui.MATCH, Ui.WRAP, 0f, 0, px(2f), 0, px(8f)))
            c.addView(Ui.button(this, ut, "مواقيت اليوم والقبلة", 20f) {
                startActivity(Intent(this, PrayerSettingsActivity::class.java))
            }, Ui.lpm(Ui.MATCH, Ui.WRAP, 0f, px(10f), px(6f), px(10f), 0))
            prayerCard = c
            return c
        }
        c.addView(prayerGrid, Ui.lpm(Ui.MATCH, Ui.WRAP, 0f, 0, px(8f), 0, px(8f)))

        val bar = LinearLayout(this)
        bar.orientation = LinearLayout.HORIZONTAL
        bar.background = Ui.round(Ui.withAlpha(ut.text, 40), px(5f).toFloat())
        if (dashboard) bar.tag = "r9-prayer-progress"
        val fill = View(this)
        fill.background = Ui.round(Color.parseColor("#22C55E"), px(5f).toFloat())
        if (dashboard) fill.tag = "r9-prayer-progress-fill"
        val rest = View(this)
        if (dashboard) rest.tag = "r9-prayer-progress-rest"
        bar.addView(fill, Ui.lp(0, px(7f), 0.001f))
        bar.addView(rest, Ui.lp(0, px(7f), 1f))
        progFill = fill
        progRest = rest
        c.addView(bar, Ui.lp(Ui.MATCH, px(if (dashboard) 6f else 7f)))

        val foot = LinearLayout(this)
        foot.orientation = LinearLayout.HORIZONTAL
        foot.gravity = Gravity.CENTER_VERTICAL
        tvPNext = Ui.label(this, "الصلاة القادمة", if (dashboard) 21f else 27f, ut.text).apply {
            if (dashboard) tag = "r9-prayer-next"
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
        }
        tvPCount = Ui.label(this, "--:--:--", if (dashboard) 27f else 34f, Color.parseColor("#22C55E"), true).apply {
            if (dashboard) tag = "r9-prayer-countdown"
            maxLines = 1
        }
        foot.addView(tvPNext, Ui.lp(0, Ui.WRAP, 1f))
        foot.addView(tvPCount, Ui.lpm(Ui.WRAP, Ui.WRAP, 0f, px(20f), 0, 0, 0))
        if (dashboard) foot.tag = "r9-prayer-progress-footer"
        c.addView(foot, Ui.lpm(Ui.MATCH, Ui.WRAP, 0f, 0, px(if (dashboard) 6f else 10f), 0, 0))
        prayerCard = c
        return c
    }

    /** نسبة الوقت المنقضي بين الصلاة السابقة والقادمة. */
    private fun updateProgress(now: LocalDateTime, next: LocalDateTime) {
        val fill = progFill ?: return
        val rest = progRest ?: return
        val t = todayTimes ?: return
        var prev: LocalDateTime? = null
        for (k in PrayerRepo.adhanKeys) {
            val lt = PrayerRepo.parse(t[k] ?: "") ?: continue
            val dt = LocalDateTime.of(now.toLocalDate(), lt)
            if (!dt.isAfter(now)) prev = dt
        }
        if (prev == null) {
            val isha = PrayerRepo.parse(t["Isha"] ?: "") ?: return
            prev = LocalDateTime.of(now.toLocalDate().minusDays(1), isha)
        }
        val total = java.time.Duration.between(prev, next).seconds.toFloat()
        val done = java.time.Duration.between(prev, now).seconds.toFloat()
        var p = if (total <= 0f) 0f else done / total
        if (p < 0.001f) p = 0.001f
        if (p > 0.999f) p = 0.999f
        (fill.layoutParams as LinearLayout.LayoutParams).weight = p
        (rest.layoutParams as LinearLayout.LayoutParams).weight = 1f - p
        fill.requestLayout()
    }

    private fun buildFavSection(forceVisible: Boolean = false, vertical: Boolean = false): View {
        favVertical = vertical
        val box = LinearLayout(this)
        box.orientation = LinearLayout.VERTICAL
        box.clipChildren = false
        box.visibility = if (prefs.showMatchFavorites || forceVisible) View.VISIBLE else View.GONE
        val row = LinearLayout(this)
        row.orientation = LinearLayout.HORIZONTAL
        row.layoutDirection = View.LAYOUT_DIRECTION_LTR
        row.clipChildren = false
        row.clipToPadding = false
        row.setPadding(px(6f), px(10f), px(6f), px(6f))
        favRow = row
        row.tag = if (com.uobidah.launcher.core.HomeLayouts.get(prefs.layoutIndex).template == HomeLayoutTemplate.DASHBOARD) "r9-favorites-row" else null
        if (com.uobidah.launcher.core.HomeLayouts.get(prefs.layoutIndex).template == HomeLayoutTemplate.DASHBOARD) {
            row.minimumHeight = px(142f)
            row.addView(Ui.label(this, "جاري تحميل مباريات الأندية المفضلة…", 20f, ut.dim).apply {
                tag = "r9-favorites-loading"
                gravity = Gravity.CENTER_VERTICAL
            }, Ui.lpm(Ui.WRAP, Ui.MATCH, 0f, 0, 0, 0, 0))
        }
        if (vertical) {
            row.orientation = LinearLayout.VERTICAL
            row.layoutDirection = View.LAYOUT_DIRECTION_LTR
            box.addView(row, Ui.lp(Ui.MATCH, Ui.WRAP))
            if (prefs.showMatchFavorites || forceVisible) loadFavorites()
            return box
        }
        val favoritesScroll = HorizontalScrollView(this)
        favoritesScroll.isHorizontalScrollBarEnabled = false
        favoritesScroll.overScrollMode = View.OVER_SCROLL_NEVER
        // Clip the horizontal carousel to its viewport so the third card has a real,
        // measurable peek at the edge; D-pad focus scrolls that card fully into view.
        favoritesScroll.clipChildren = true
        favoritesScroll.clipToPadding = true
        if (com.uobidah.launcher.core.HomeLayouts.get(prefs.layoutIndex).template == HomeLayoutTemplate.DASHBOARD) {
            favoritesScroll.tag = "r9-favorites-scroll"
            favoritesScroll.isFocusable = false
            favoritesScroll.descendantFocusability = ViewGroup.FOCUS_AFTER_DESCENDANTS
        }
        favoritesScroll.addView(row, FrameLayout.LayoutParams(Ui.WRAP, Ui.WRAP))
        box.addView(favoritesScroll, Ui.lp(Ui.MATCH, Ui.WRAP))
        if (prefs.showMatchFavorites || forceVisible) loadFavorites()
        return box
    }

    private fun loadFavorites() {
        favLoadedAt = System.currentTimeMillis()
        Net.bg {
            // Robolectric can hold this one repository operation so the loading state is
            // asserted before the real provider completion replaces it.
            LauncherTestHooks.beforeLoad(this)
            val result = try {
                val res = ArrayList<Triple<TeamDef, String, Match?>>()
                val selectedTeams = prefs.matchFavoriteTeams
                val selected = MatchRepo.allTeams(prefs).filter { selectedTeams.contains(it.id) }
                for (def in selected) {
                    var badge = ""
                    var m: Match? = null
                    try {
                        val id = MatchRepo.teamId(prefs, def)
                        // Reuse the provider's match-page cache; avoid additional logo API calls on Home.
                        badge = prefs.sp.getString("team_badge_url_" + id, "").orEmpty()
                        val now = System.currentTimeMillis()
                        val candidates = MatchRepo.next(prefs, id) + MatchRepo.last(prefs, id)
                        m = candidates.firstOrNull { it.upcoming(now) }
                            ?: candidates.firstOrNull { it.live(now) }
                            ?: candidates.firstOrNull { it.previous(now) }
                        if (m != null) m = if (m!!.source != "ysscores") MatchRepo.withResolvedBadges(m!!, prefs) else MatchRepo.withRepoLogos(m!!, prefs)
                        if (badge.isBlank() && m != null) {
                            badge = when {
                                m!!.home.contains(def.query, true) || m!!.home.contains(def.arabic, true) -> m!!.homeBadge
                                m!!.away.contains(def.query, true) || m!!.away.contains(def.arabic, true) -> m!!.awayBadge
                                else -> ""
                            }
                        }
                    } catch (_: Exception) { }
                    if (badge.isBlank() && m != null) {
                        badge = when (def.id) {
                            m.homeTeamId -> m.homeBadge
                            m.awayTeamId -> m.awayBadge
                            else -> ""
                        }
                    }
                    res.add(Triple(def, badge, m))
                }
                Pair(res, false)
            } catch (_: Exception) {
                Pair(emptyList<Triple<TeamDef, String, Match?>>(), true)
            }
            Net.ui {
                renderFavorites(result.first, result.second)
                LauncherTestHooks.rendered(this)
            }
            LauncherTestHooks.callbackQueued(this)
        }
    }

    private fun dayWord(ms: Long): String {
        val zone = try { java.time.ZoneId.of(prefs.cityTimezone) } catch (_: Exception) { java.time.ZoneId.of("Africa/Cairo") }
        val d = java.time.Instant.ofEpochMilli(ms).atZone(zone).toLocalDate()
        val today = PrayerRepo.localNow(prefs).toLocalDate()
        return when {
            d == today -> "اليوم"
            d == today.plusDays(1) -> "غداً"
            else -> d.dayOfWeek.getDisplayName(TextStyle.FULL, Locale("ar"))
        }
    }

    private fun teamCol(name: String, badge: String): View {
        val col = LinearLayout(this)
        col.orientation = LinearLayout.VERTICAL
        col.gravity = Gravity.CENTER_HORIZONTAL
        col.addView(TeamBadgeUi.create(this, name, badge, 66, ut.accent), Ui.lp(px(66f), px(66f)))
        val nm = Ui.label(this, name, 20f, ut.text)
        nm.gravity = Gravity.CENTER
        Ui.ellipsize(nm)
        col.addView(nm, Ui.lpm(px(112f), Ui.WRAP, 0f, 0, px(4f), 0, 0))
        return col
    }

    private fun renderFavorites(res: List<Triple<TeamDef, String, Match?>>, loadFailed: Boolean = false) {
        val row = favRow ?: return
        row.removeAllViews()
        if (res.isEmpty()) {
            val message = if (loadFailed) "تعذر تحميل المباريات الآن — اضغط لإعادة المحاولة" else "لم تختر أندية مفضلة بعد — افتح تخصيص الأندية لاختيار الفرق"
            val empty = Ui.label(this, message, 22f, ut.dim)
            empty.isFocusable = true
            empty.isClickable = true
            empty.setOnClickListener { loadFavorites() }
            row.addView(empty, Ui.lpm(Ui.MATCH, Ui.WRAP, 0f, 0, px(12f), 0, 0))
        }
        val horizontalPadding = if (isProHomeLayout()) px(44f * 2f) else px(56f * 2f)
        val cardGaps = px(18f * 2f)
        val widthDp = (resources.displayMetrics.widthPixels / resources.displayMetrics.density).toInt()
        val visibleCards = com.uobidah.launcher.core.CardGrid.homeVisible(widthDp)
        // داخل تقسيم (مباراة + جدول) الحاوية أضيق من الشاشة؛ قياس البطاقة على عرض الشاشة كان يطلّع بطاقة نصفها خارج الحد.
        val containerPx = (row.parent as? View)?.width?.takeIf { it > 0 }
        val dashboard = com.uobidah.launcher.core.HomeLayouts.get(prefs.layoutIndex).template == HomeLayoutTemplate.DASHBOARD
        val splitNarrow = containerPx != null && containerPx < (resources.displayMetrics.widthPixels - horizontalPadding) * 0.8f
        val cardWidth = if (dashboard && containerPx != null) {
            // Dashboard cards use the actual carousel viewport (after the side rail), not the
            // full device width. This preserves two complete cards plus a deliberate third peek.
            com.uobidah.launcher.core.MatchPaneCards.width(containerPx, px(12f), px(18f), res.size, px(620f), px(420f))
        } else if (splitNarrow) {
            // بطاقتان كاملتان + جزء ظاهر من الثالثة (دليل على التمرير) بدل بطاقة واحدة تُخفي الباقي عند حد لوحة الدوري.
            com.uobidah.launcher.core.MatchPaneCards.width(containerPx!!, px(12f), px(18f), res.size, px(440f), px(260f))
        } else {
            ((resources.displayMetrics.widthPixels - horizontalPadding - cardGaps - px(12f)) / visibleCards).coerceAtLeast(px(340f))
        }
        lastFavRender = res to loadFailed
        (row.parent as? View)?.let { host ->
            if (!favHostListening) {
                favHostListening = true
                var lastW = host.width
                host.addOnLayoutChangeListener { v, l, _, r, _, _, _, _, _ ->
                    val w = r - l
                    if (w > 0 && Math.abs(w - lastW) > px(8f)) { lastW = w; v.post { lastFavRender?.let { renderFavorites(it.first, it.second) } } }
                }
            }
        }
        for ((idx, pr) in res.withIndex()) {
            val def = pr.first
            val badge = pr.second
            val m = pr.third
            val eventNow = System.currentTimeMillis()
            val kind = when {
                m == null -> "next"
                m.upcoming(eventNow) -> "next"
                m.live(eventNow) -> "live"
                m.previous(eventNow) -> "last"
                else -> "unknown"
            }
            val title = when (kind) {
                "live" -> "المباراة الحالية"
                "last" -> "المباراة السابقة"
                "unknown" -> "حالة المباراة غير مؤكدة"
                else -> "المباراة القادمة"
            }
            val card = MatchCards.build(
                this, prefs,
                MatchCardModel(kind, title, m, def.arabic, badge, true, prefs.cityTimezone),
                compact = true
            )
            val dashboardFavorites = com.uobidah.launcher.core.HomeLayouts.get(prefs.layoutIndex).template == HomeLayoutTemplate.DASHBOARD
            if (dashboardFavorites) {
                card.tag = "r9-favorite-match-card-$idx"
                card.contentDescription = "$title: ${m?.home ?: def.arabic} ${m?.homeScore?.toString().orEmpty()} - ${m?.awayScore?.toString().orEmpty()} ${m?.away.orEmpty()}"
            }
            card.isClickable = true
            if (com.uobidah.launcher.core.HomeLayouts.get(prefs.layoutIndex).template == HomeLayoutTemplate.DASHBOARD) {
                // Treat each match card as a single remote-focus target. TV launchers can be
                // hosted in touch mode by some OEMs and Robolectric; focus must still land on
                // the card instead of failing before D-pad navigation begins.
                card.isFocusable = true
                card.isFocusableInTouchMode = true
                card.descendantFocusability = ViewGroup.FOCUS_BLOCK_DESCENDANTS
                card.clipChildren = false
                card.clipToPadding = false
            }
            card.setOnClickListener {
                if (m == null) {
                    startActivity(Intent(this, MatchesActivity::class.java))
                } else {
                    val i = Intent(this, MatchDetailActivity::class.java)
                    i.putExtra("id", m.id)
                    i.putExtra("source", m.source)
                    i.putExtra("home", m.home)
                    i.putExtra("away", m.away)
                    i.putExtra("homeBadge", m.homeBadge)
                    i.putExtra("awayBadge", m.awayBadge)
                    i.putExtra("league", m.league)
                    i.putExtra("venue", m.venue)
                    i.putExtra("startMs", m.startMs)
                    i.putExtra("hs", m.homeScore ?: -1)
                    i.putExtra("as", m.awayScore ?: -1)
                    i.putExtra("status", m.status)
                    i.putExtra("progress", m.progress)
                    i.putExtra("externalUrl", m.externalUrl)
                    i.putExtra("kind", kind)
                    startActivity(i)
                }
            }
            if (dashboardFavorites) {
                card.setOnFocusChangeListener { focused, hasFocus ->
                    if (hasFocus) {
                        // D-pad focus must reveal the entire focused match, even when its first
                        // frame was intentionally peeking from the right edge of the carousel.
                        focused.post {
                            val scroller = focused.parent?.parent as? HorizontalScrollView ?: return@post
                            val bounds = Rect(0, 0, focused.width, focused.height)
                            scroller.offsetDescendantRectToMyCoords(focused, bounds)
                            // Keep a small inner gutter so the focused state stroke/halo is not
                            // clipped by the HorizontalScrollView viewport after focus scrolling.
                            val focusGutter = px(10f)
                            val visibleLeft = scroller.scrollX + scroller.paddingLeft + focusGutter
                            val visibleRight = scroller.scrollX + scroller.width - scroller.paddingRight - focusGutter
                            val target = when {
                                bounds.right > visibleRight -> bounds.right - scroller.width + scroller.paddingRight + focusGutter
                                bounds.left < visibleLeft -> bounds.left - scroller.paddingLeft - focusGutter
                                else -> scroller.scrollX
                            }
                            val contentWidth = (row.width - scroller.width).coerceAtLeast(0)
                            scroller.scrollTo(target.coerceIn(0, contentWidth), 0)
                        }
                    }
                }
            }
            Motion.enter(card, this)
            val cardHeight = if (com.uobidah.launcher.core.HomeLayouts.get(prefs.layoutIndex).template == HomeLayoutTemplate.DASHBOARD) px(142f) else px(184f)
            if (favVertical) row.addView(card, Ui.lpm(Ui.MATCH, cardHeight, 0f, 0, 0, 0, if (idx == res.size - 1) 0 else px(14f)))
            else row.addView(card, Ui.lpm(cardWidth, cardHeight, 0f, 0, 0, if (idx == res.size - 1) 0 else px(18f), 0))
        }
    }

    private fun buildTvDock(): View = buildDockStyled(true)

    // =====================================================================
    // التطبيقات المثبتة
    // =====================================================================
    private fun firstRunSetup() {
        if (!prefs.pinnedInit) {
            val wanted = listOf(
                "com.google.android.youtube.tv", "com.netflix.ninja", "net.mbc.shahid",
                "com.amazon.amazonvideo.livingroom", "com.google.android.youtube.tvmusic",
                "com.android.vending", "com.android.tv.settings", "org.xbmc.kodi"
            )
            val pm = packageManager
            val found = ArrayList<String>()
            for (p in wanted) {
                if (AppUtil.launchIntent(pm, p) != null) found.add(p)
            }
            // لا تكتب قائمة افتراضية فوق مفضلة محفوظة أصلًا (كانت تُفرَّغ إذا لم يوجد أي تطبيق من القائمة الافتراضية).
            if (prefs.favorites().entries.isEmpty()) prefs.pinnedApps = found
            prefs.pinnedInit = true
        }
        if (!prefs.askedPerms) {
            prefs.askedPerms = true
            prefs.setupPromptMs = System.currentTimeMillis()
            // أول تشغيل: شاشة التفعيل تطلب كل الأذونات والتعيين كافتراضي بالترتيب
            try {
                startActivity(Intent(this, SetupActivity::class.java).putExtra("chain", true))
            } catch (e: Exception) {
            }
        }
    }

    private fun tile(icon: Drawable?, emoji: String, label: String, onClick: () -> Unit, onLong: (() -> Unit)?, showLabel: Boolean = true, favoriteIcon: Boolean = false, sizeFactor: Float = 1f): View {
        val t = LinearLayout(this)
        t.orientation = LinearLayout.VERTICAL
        t.gravity = Gravity.CENTER
        t.setPadding(px(10f), px(12f), px(10f), px(12f))
        Ui.focusable(t, ut, 28f, BrandTint.fillFor(icon, ut.card, ut.text))
        t.isClickable = true
        t.setOnClickListener { onClick() }
        if (onLong != null) {
            t.setOnLongClickListener {
                onLong()
                true
            }
        }
        // مرجع R10: بانر التطبيق العريض (يحمل شعاره واسمه) يملأ البطاقة بدل أيقونة صغيرة مع عنوان؛ الاسم الكامل يبقى في contentDescription.
        val isBanner = icon != null && !favoriteIcon && icon.intrinsicHeight > 0 && icon.intrinsicWidth.toFloat() / icon.intrinsicHeight >= 1.5f
        if (isBanner) {
            t.setPadding(px(6f), px(6f), px(6f), px(6f))
            t.contentDescription = label
            val iv = ImageView(this)
            iv.setImageDrawable(icon)
            iv.scaleType = ImageView.ScaleType.FIT_CENTER
            iv.adjustViewBounds = true
            t.addView(iv, Ui.lp(Ui.MATCH, 0, 1f))
        } else if (icon != null) {
            val iv = ImageView(this)
            iv.setImageDrawable(icon)
            iv.scaleType = ImageView.ScaleType.FIT_CENTER
            if (favoriteIcon) iv.tag = "pinned-app-icon"
            val isz = if (favoriteIcon) px(prefs.favoriteAppIconSize * sizeFactor) else if (isProHomeLayout()) px(78f) else px(92f)
            t.addView(iv, Ui.lp(isz, isz))
        } else {
            t.addView(Ui.label(this, emoji, (if (isProHomeLayout()) 58f else 70f) * (if (favoriteIcon) sizeFactor else 1f), ut.text))
        }
        val lb = Ui.label(this, label, 24f, ut.text, true).apply {
            // Narrow screens get two readable lines and automatic type reduction rather than
            // clipping the app name to a single line. The full label remains available to accessibility.
            isSingleLine = false
            maxLines = if (label.trim().contains(" ")) 2 else 1
            ellipsize = android.text.TextUtils.TruncateAt.END
            gravity = Gravity.CENTER
            includeFontPadding = false
            contentDescription = label
            if (Build.VERSION.SDK_INT >= 26) {
                setAutoSizeTextTypeUniformWithConfiguration(
                    px(15f), px(24f), px(1f), TypedValue.COMPLEX_UNIT_PX
                )
            }
        }
        lb.visibility = if (showLabel && !isBanner) View.VISIBLE else View.GONE
        t.addView(lb, Ui.lpm(Ui.MATCH, Ui.WRAP, 0f, 0, px(8f), 0, 0))
        return t
    }

    private fun refreshPinned() {
        refreshGrid()
        if (!::pinnedRow.isInitialized) return
        pinnedRow.removeAllViews()
        val pm = packageManager
        val gap = px(18f)
        val w = px(230f)
        val h = px(if (isProHomeLayout()) 150f else 170f)
        // مرجع R2: في تصميم الصلاة صف المفضلة 4 بطاقات تتسع كاملة داخل العمود الأيسر بلا قص عند الحافة.
        val archFit = com.uobidah.launcher.core.HomeLayouts.get(prefs.layoutIndex).template == HomeLayoutTemplate.PRAYER_HERO && prefs.showPrayerCard
        // تقدير أولي فقط؛ يُستبدل بالعرض المقيس فعليًا للـHorizontalScrollView عند أول layout (fitArchFavorites).
        val archTileW = if (archFit) com.uobidah.launcher.core.ArchTileSize.width(resources.displayMetrics.widthPixels, px(112f), px(10f), gap, 4) else 0
        var archIndex = 0

        // الصف في المرجع يسير بصريًا من اليسار إلى اليمين، وزر الإضافة آخر بطاقة يمينًا.
        for (fav in prefs.favorites().visible()) {
            val pkg = fav.pkg
            val installed = try { pm.getApplicationInfo(pkg, 0); true } catch (_: Exception) { false }
            if (!installed) continue
            val customEmoji = if (fav.iconKey.startsWith("emoji:")) fav.iconKey.removePrefix("emoji:").trim() else ""
            val icon: Drawable? = if (customEmoji.isNotEmpty()) null else AppUtil.cachedIcon(pm, pkg, true)
            val showName = prefs.favoriteAppShowNames && fav.showName
            val f = fav.size.factor
            val appTileHeight = maxOf(h, px((prefs.favoriteAppIconSize * f + if (showName) 60 else 32)))
            val tileLp = Ui.lpm(if (archTileW > 0) archTileW else px(prefs.favoriteAppTileWidth * f), appTileHeight, 0f, 0, 0, gap, 0)
            if (archFit) { archIndex++; if (archIndex % 4 == 0) tileLp.marginEnd = 0 } // لا هامش بعد آخر بطاقة من الأربع
            pinnedRow.addView(
                tile(icon, if (customEmoji.isNotEmpty()) customEmoji else "📦", fav.displayName(AppUtil.label(pm, pkg)),
                    {
                        val launch = AppUtil.launchIntent(pm, pkg)
                        if (launch == null) toast("لا توجد واجهة تشغيل لهذا التطبيق")
                        else try { startActivity(launch) } catch (_: Exception) { toast("تعذر فتح التطبيق") }
                    },
                    { startActivity(Intent(this, FavoriteAppsSettingsActivity::class.java)) },
                    showLabel = showName, favoriteIcon = true, sizeFactor = f),
                tileLp
            )
            pinnedRow.getChildAt(pinnedRow.childCount - 1).tag = if (archFit) "arch-fav" else null
        }
        if (archFit) fitArchFavorites(gap)

        // في تخطيط التلفزيون المرجعي لا نضيف بطاقات جانبية تزاحم صف التطبيقات.
        // تظل مداخل التلفزيون وإعدادات النادي موجودة في الإعدادات وشاشة المباريات.
        if (!isProHomeLayout()) {
            val club = ut.club
            if (club != null && prefs.showClubShortcutTile) {
                pinnedRow.addView(
                    tile(null, club.emoji, "مباريات " + club.ar,
                        { startActivity(Intent(this, MatchesActivity::class.java)) }, null),
                    Ui.lpm(w, h, 0f, 0, 0, gap, 0)
                )
            }
            if (prefs.showTvInputTile) {
                pinnedRow.addView(
                    tile(null, "📺", "مداخل التلفزيون",
                        { startActivity(Intent(this, TvInputsActivity::class.java)) }, null),
                    Ui.lpm(w, h, 0f, 0, 0, gap, 0)
                )
            }
        }

        if (!archFit) pinnedRow.addView(
            tile(null, "➕", "إضافة تطبيق",
                {
                    val i = Intent(this, AppsActivity::class.java)
                    i.putExtra("mode", "pin")
                    startActivity(i)
                }, null),
            Ui.lpm(w, h, 0f, 0, 0, 0, 0)
        )
    }

    /**
     * تصميم الصلاة (R2): يضبط عرض بطاقات المفضلة على العرض المتاح فعلًا للصف بعد القياس، لا على تقدير من حشوات مفترضة.
     * يُطبَّق عند كل تغيّر في عرض الـHorizontalScrollView، ولا يغيّر شيئًا إن كان العرض مطابقًا (لا حلقة layout).
     */
    private fun fitArchFavorites(gap: Int) {
        val sv = pinnedRow.parent as? HorizontalScrollView ?: return
        val apply = {
            val avail = sv.width - sv.paddingLeft - sv.paddingRight
            if (avail > 0) {
                val target = com.uobidah.launcher.core.ArchTileSize.fit(avail, gap, 4)
                var changed = false
                for (i in 0 until pinnedRow.childCount) {
                    val c = pinnedRow.getChildAt(i)
                    if (c.tag != "arch-fav") continue
                    val lp = c.layoutParams as LinearLayout.LayoutParams
                    if (lp.width != target) { lp.width = target; changed = true }
                }
                if (changed) pinnedRow.requestLayout()
            }
        }
        if (sv.tag != "arch-fit") { // مستمع واحد لكل HorizontalScrollView (يُعاد بناء الصف مع كل تحديث)
            sv.tag = "arch-fit"
            sv.addOnLayoutChangeListener { _, l, _, r, _, oldL, _, oldR, _ -> if (r - l != oldR - oldL) sv.post { apply() } }
        }
        sv.post { apply() }
    }

    // =====================================================================
    // التحديث الدوري
    // =====================================================================
    private fun tick() {
        val now = PrayerRepo.localNow(prefs)
        val h12 = if (now.hour % 12 == 0) 12 else now.hour % 12
        val displayHour = if (prefs.use24HourClock) now.hour else h12
        // الساعة بدقائق فقط: لا تعيد كتابة النص (وإعادة قياس الواجهة) كل ثانية بلا تغيير.
        val clockText = String.format(Locale.US, "%02d:%02d", displayHour, now.minute)
        if (clockText != lastClockText) {
            lastClockText = clockText
            tvTime.text = clockText
            tvAmPm.text = if (prefs.use24HourClock) "" else if (now.hour < 12) "ص" else "م"
        }

        if (shownDate != now.toLocalDate()) {
            shownDate = now.toLocalDate()
            val fullGregorian = DateUtil.gregorian(now.toLocalDate())
            val fullHijri = DateUtil.hijri(prefs.hijriOffset, now.toLocalDate())
            // التاريخ كاملًا في كل القوالب (بما فيها المضغوطة): اليوم + الشهر + السنة ميلاديًا، واليوم + الشهر + السنة هجريًا.
            tvDate.text = fullGregorian
            tvHijri.text = fullHijri
            tvDate.contentDescription = fullGregorian
            tvHijri.contentDescription = fullHijri
            tvHijri.visibility = if (prefs.showHijriDate) View.VISIBLE else View.GONE
        }
        val prayerNow = PrayerRepo.localNow(prefs)
        if (shownPrayerDate != prayerNow.toLocalDate()) {
            shownPrayerDate = prayerNow.toLocalDate()
            for ((key, label) in prayerNames) label.text = prayerArabicLabel(key)
            for ((key, label) in prayerSubs) label.text = prayerSub(key)
            loadPrayer(prayerNow.toLocalDate())
        }
        renderCountdown(prayerNow)
        renderHeaderInfo(prayerNow)

        tickCount++
        if (tickCount % 10 == 0) updateWifi()
        if (tickCount % 2 == 0) updateNetSpeed()
        val wallNow = System.currentTimeMillis()
        if (prefs.showWeatherCard && !weatherLoading && wallNow >= weatherRetryAt &&
            (weatherRetryAt > 0L || weather == null || wallNow - lastWeather >= prefs.weatherRefreshMinutes * 60_000L)) fetchWeather()
        if (!prayerLoading && prayerRetryAt > 0L && wallNow >= prayerRetryAt) {
            val retryDate = PrayerRepo.localNow(prefs).toLocalDate()
            shownPrayerDate = retryDate
            loadPrayer(retryDate)
        }
    }

    /** يبطل أي طلبات للمدينة السابقة قبل عرض بيانات المدينة الجديدة. */
    private fun onSelectedCityChanged() {
        cityKey = currentCityKey()
        weatherRequestId++
        weatherLoading = false
        weather = null
        lastWeather = 0L
        weatherRetryAt = 0L
        weatherUpdatedAt = 0L
        todayTimes = null
        tomorrowTimes = null
        prayerRequestId++
        prayerLoading = false
        prayerRetryAt = 0L
        prayerRequestedDate = null
        for (key in PrayerRepo.keys) prayerTimes[key]?.text = "--:--"
        renderWeatherCache()
        shownPrayerDate = PrayerRepo.localNow(prefs).toLocalDate()
        loadPrayer(shownPrayerDate!!)
    }

    private fun renderWeatherCache() {
        val cached = WeatherRepo.cached(prefs, prefs.cityLat, prefs.cityLon) ?: return
        weather = cached.first
        weatherUpdatedAt = cached.second
        val zone = runCatching { java.time.ZoneId.of(cached.first.timezone) }.getOrNull()
        if (zone != null && prefs.cityTimezone != zone.id) {
            prefs.cityTimezone = zone.id
            cityKey = currentCityKey()
        }
        renderWeather()
    }

    private fun fetchWeather() {
        if (weatherLoading) return
        weatherLoading = true
        lastWeather = System.currentTimeMillis()
        val lat = prefs.cityLat
        val lon = prefs.cityLon
        val requestId = ++weatherRequestId
        val requestCity = "${prefs.cityName}|$lat|$lon"
        Net.bg {
            try {
                val w = WeatherRepo.fetch(lat, lon)
                val fetchedAt = System.currentTimeMillis()
                WeatherRepo.saveCache(prefs, lat, lon, w, fetchedAt)
                Net.ui {
                    if (requestId != weatherRequestId || requestCity != "${prefs.cityName}|${prefs.cityLat}|${prefs.cityLon}") return@ui
                    weatherLoading = false
                    weather = w
                    weatherUpdatedAt = fetchedAt
                    weatherRetryAt = 0L
                    val zone = runCatching { java.time.ZoneId.of(w.timezone) }.getOrNull()
                    if (zone != null && prefs.cityTimezone != zone.id) {
                        prefs.cityTimezone = zone.id
                        cityKey = currentCityKey()
                        shownPrayerDate = PrayerRepo.localNow(prefs).toLocalDate()
                        loadPrayer(shownPrayerDate!!)
                    }
                    renderWeather()
                }
            } catch (e: Exception) {
                Net.ui {
                    if (requestId != weatherRequestId || requestCity != "${prefs.cityName}|${prefs.cityLat}|${prefs.cityLon}") return@ui
                    weatherLoading = false
                    weatherRetryAt = System.currentTimeMillis() + 5 * 60_000L
                    if (weather == null) tvWDesc.text = "تعذر جلب الطقس — ستتم إعادة المحاولة"
                }
            }
        }
    }

    private fun renderWeather() {
        val w = weather ?: return
        val d = WeatherRepo.describe(w.code, w.isDay)
        tvTemp.text = Math.round(w.temp).toString() + "°"
        tvWDesc.text = d.first + "  " + d.second
        if (isProHomeLayout()) {
            val tvSb = StringBuilder()
            if (!w.hi.isNaN() && !w.lo.isNaN()) {
                tvSb.append("العظمى ").append(Math.round(w.hi)).append("°  |  الصغرى ").append(Math.round(w.lo)).append("°")
            }
            tvSb.append("  •  رطوبة ").append(w.humidity).append("%")
            if (weatherUpdatedAt > 0L) {
                val ageMinutes = ((System.currentTimeMillis() - weatherUpdatedAt).coerceAtLeast(0L) / 60_000L).toInt()
                tvSb.append("  •  آخر تحديث منذ ").append(DateUtil.arNum(ageMinutes)).append(" د")
            }
            tvWDetail.text = tvSb.toString()
            renderForecast(w)
            return
        }
        val sb = StringBuilder()
        sb.append("الإحساس ").append(Math.round(w.feels)).append("°  •  رطوبة ").append(w.humidity).append("%")
        sb.append("\nرياح ").append(Math.round(w.wind)).append(" كم/س")
        if (!w.hi.isNaN() && !w.lo.isNaN()) {
            sb.append("  •  ↑").append(Math.round(w.hi)).append("°  ↓").append(Math.round(w.lo)).append("°")
        }
        if (weatherUpdatedAt > 0L) {
            val ageMinutes = ((System.currentTimeMillis() - weatherUpdatedAt).coerceAtLeast(0L) / 60_000L).toInt()
            sb.append("\nآخر تحديث منذ ").append(DateUtil.arNum(ageMinutes)).append(" دقيقة")
        }
        tvWDetail.text = sb.toString()
    }

    private fun loadPrayer(date: LocalDate = PrayerRepo.localNow(prefs).toLocalDate()) {
        if (prayerLoading && prayerRequestedDate == date) return
        prayerLoading = true
        prayerRequestedDate = date
        val app = applicationContext
        val requestId = ++prayerRequestId
        val requestCity = "${prefs.cityLat}|${prefs.cityLon}|${prefs.prayerMethod}"
        Net.bg {
            val t = PrayerRepo.timesFor(app, prefs, date)
            val tm = PrayerRepo.timesFor(app, prefs, date.plusDays(1))
            Net.ui {
                val activeCity = "${prefs.cityLat}|${prefs.cityLon}|${prefs.prayerMethod}"
                if (requestId != prayerRequestId || requestCity != activeCity) return@ui
                prayerLoading = false
                val localNow = PrayerRepo.localNow(prefs)
                cityKey = currentCityKey()
                if (localNow.toLocalDate() != date) {
                    shownPrayerDate = localNow.toLocalDate()
                    prayerRetryAt = 0L
                    loadPrayer(localNow.toLocalDate())
                    return@ui
                }
                todayTimes = t
                tomorrowTimes = tm
                prayerRetryAt = if (t == null || tm == null) System.currentTimeMillis() + 5 * 60_000L else 0L
                for (k in PrayerRepo.keys) prayerTimes[k]?.text = formatTime(t?.get(k))
                renderCountdown(localNow)
            }
        }
    }

    private fun formatTime(s: String?): String {
        if (s == null || s.length < 5) return "--:--"
        val lt = PrayerRepo.parse(s) ?: return s
        val h = if (lt.hour % 12 == 0) 12 else lt.hour % 12
        return String.format(Locale.US, "%d:%02d", h, lt.minute)
    }

    /** «الأذان ٣:٥١ م، القاهرة» بأرقام عربية وص/م؛ اسم المدينة من الإعدادات فقط. */
    private fun adhanLine(t: LocalDateTime): String {
        val h = if (t.hour % 12 == 0) 12 else t.hour % 12
        val digits = String.format(Locale.US, "%d:%02d", h, t.minute).map { if (it in '0'..'9') '٠' + (it - '0') else it }.joinToString("")
        val city = prefs.cityName.trim()
        return "الأذان " + digits + " " + (if (t.hour < 12) "ص" else "م") + (if (city.isNotEmpty()) "، $city" else "")
    }

    private fun renderCountdown(now: LocalDateTime) {
        val nx = PrayerRepo.next(todayTimes, tomorrowTimes, now)
        if (nx == null) {
            tvPNext.text = "تعذر جلب المواقيت"
            tvPCount.text = "--:--:--"
            return
        }
        tvPNext.text = if (isProHomeLayout()) {
            "المتبقي على أذان " + prayerArabicLabel(nx.first)
        } else {
            "الصلاة القادمة: " + prayerArabicLabel(nx.first)
        }
        updateProgress(now, nx.second)
        tvPCount.text = PrayerRepo.countdown(now, nx.second)
        tvPAdhan?.text = adhanLine(nx.second)
        tvPQibla?.text = com.uobidah.launcher.core.Qibla.label(prefs.cityLat, prefs.cityLon).orEmpty()
        // إعادة تلوين خلايا المواقيت وإنشاء خلفيات جديدة كل ثانية كان يجهد خيط الواجهة
        // على أجهزة التلفزيون الضعيفة؛ لا نعيد الرسم إلا عندما تتغير الصلاة الحالية.
        if (nx.first != lastHotPrayer) {
            lastHotPrayer = nx.first
            for (k in PrayerRepo.keys) {
                val hot = k == nx.first
                val gold = Color.parseColor("#E8C98A")
                val hotColor = if (archHighlight) gold else ut.accent
                prayerNames[k]?.setTextColor(if (hot) hotColor else ut.dim)
                prayerTimes[k]?.setTextColor(if (hot) hotColor else ut.text)
                if (archHighlight) {
                    val cell = prayerNames[k]?.parent?.parent as? View
                    cell?.background = Ui.round(Ui.over(ut.cardFocus, ut.bgBottom), px(17f).toFloat(), if (hot) px(3f) else px(1f), if (hot) gold else ut.border)
                }
            }
        }
    }

    // =====================================================================
    // الواي فاي والمعزز
    // =====================================================================
    private var lastWifiText = ""

    @Suppress("DEPRECATION")
    private fun updateWifi() {
        // قراءة حالة الاتصال/الواي فاي تنادى على النظام عبر IPC وقد تحجب خيط الواجهة على
        // بعض أجهزة التلفزيون؛ انقلها للخلفية ولا ترسل للواجهة إلا النص النهائي.
        val app = applicationContext
        Net.bg {
            var txt = "📶 غير متصل"
            try {
                val cm = app.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
                val caps = cm.getNetworkCapabilities(cm.activeNetwork)
                if (caps != null) {
                    if (caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) {
                        val wm = app.getSystemService(Context.WIFI_SERVICE) as WifiManager
                        var ssid = wm.connectionInfo?.ssid ?: ""
                        ssid = ssid.trim('"')
                        txt = if (ssid.isEmpty() || ssid == "<unknown ssid>") "📶 واي فاي متصل" else "📶 $ssid"
                    } else if (caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)) {
                        txt = "🔌 إيثرنت"
                    } else {
                        txt = "🌐 متصل"
                    }
                }
            } catch (e: Exception) {
            }
            Net.ui {
                if (isFinishing || isDestroyed) return@ui
                if (txt != lastWifiText) {
                    lastWifiText = txt
                    if (::tvWifi.isInitialized) tvWifi.text = txt
                }
            }
        }
    }

    /** يفتح شاشة المداخل المخصصة مباشرة؛ لا يحوّل المستخدم إلى إعدادات Android العامة. */
    private fun openHdmi() {
        try {
            startActivity(TvInputsActivity.directInputsIntent(this))
        } catch (e: Exception) {
            toast("تعذر فتح قائمة مداخل التلفزيون: ${e.message ?: e.javaClass.simpleName}")
        }
    }

    private fun openWifi() {
        try {
            startActivity(Intent(Settings.ACTION_WIFI_SETTINGS))
        } catch (e: Exception) {
            try {
                startActivity(Intent(Settings.ACTION_SETTINGS))
            } catch (e2: Exception) {
                toast("تعذر فتح إعدادات الواي فاي")
            }
        }
    }

    private fun runBooster() {
        toast("🔎 جاري فحص الذاكرة...")
        val app = applicationContext
        Net.bg {
            val released = Img.clearMemoryCache()
            System.gc()
            val mb = SystemTools.availableMemoryMb(app)
            Net.ui {
                toast(
                    if (mb > 0) "تم تنظيف كاش صور اللانشر ($released صورة) • الذاكرة المتاحة الآن: $mb م.ب. لا يسمح Android للتطبيق بإيقاف تطبيقات النظام."
                    else "تم تنظيف كاش صور اللانشر • تعذر قراءة الذاكرة المتاحة"
                )
            }
        }
    }

    private fun openAppSearch() {
        startActivity(Intent(this, AppsActivity::class.java).putExtra("mode", "search"))
    }

    /** اسم الظهر يتحول إلى الجمعة حسب التاريخ المحلي للمدينة المختارة. */
    private fun prayerArabicLabel(key: String): String {
        if (key == "Dhuhr" && PrayerRepo.localNow(prefs).dayOfWeek == java.time.DayOfWeek.FRIDAY) return "الجمعة"
        return PrayerRepo.arabic[key] ?: key
    }

    private fun startCore() {
        try {
            startForegroundService(Intent(this, DynamicIslandOverlayService::class.java))
        } catch (e: Exception) {
        }
    }

    @Suppress("OVERRIDE_DEPRECATION", "DEPRECATION")
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        updateWifi()
    }
}
