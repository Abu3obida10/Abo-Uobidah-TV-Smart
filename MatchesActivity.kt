package com.uobidah.launcher

import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.StateListDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.widget.FrameLayout
import android.view.View
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.TextView
import com.uobidah.launcher.core.Contrast
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

/** لوحة نتائج تلفزيونية واسعة: صف للقادمة، صف للمباشرة، وصف للنتائج السابقة. */
class MatchesActivity : BaseActivity() {
    private class TeamData(
        val def: TeamDef,
        val badge: String,
        val next: Match?,
        val live: Match?,
        val last: Match?,
        val ok: Boolean
    )

    private val handler = Handler(Looper.getMainLooper())
    private lateinit var rows: LinearLayout
    private lateinit var status: TextView
    private lateinit var tvTime: TextView
    private lateinit var tvDate: TextView
    private lateinit var tvPlace: TextView
    private lateinit var tvWeather: TextView
    private var anyLive = false
    private var loading = false
    private val phoneLayout: Boolean
        get() = resources.displayMetrics.widthPixels / resources.displayMetrics.density < 600f
    private var weatherSuffix = ""
    private var dataSnapshot: List<TeamData> = emptyList()
    private var renderedSig = ""
    private var animateCards = true
    private var panelMode = 0 // 0 مباريات، 1 جدول الدوري
    private var standingsLeague = "eng.1"
    private var standingsLoading = false
    private val lastLiveScores = HashMap<String, String>()
    private val accents = listOf(
        Color.rgb(239, 68, 68), Color.rgb(59, 130, 246), Color.rgb(226, 232, 240),
        Color.rgb(168, 85, 247), Color.rgb(16, 185, 129), Color.rgb(245, 158, 11)
    )

    private val clock = object : Runnable {
        override fun run() {
            val n = PrayerRepo.localNow(prefs)
            tvTime.text = String.format(Locale.US, "%02d:%02d", n.hour, n.minute)
            tvDate.text = DateUtil.gregorian(n.toLocalDate())
            handler.postDelayed(this, 20_000)
        }
    }

    private val autoRefresh = object : Runnable {
        override fun run() {
            load(true, false)
            val now = System.currentTimeMillis()
            val nearKickoff = dataSnapshot.any { team ->
                val kick = team.next?.startMs ?: 0L
                kick > 0L && kick - now in 0..(15 * 60_000L)
            }
            handler.postDelayed(this, if (anyLive || nearKickoff) 45_000L else 5 * 60_000L)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        panelMode = intent.getIntExtra("panel_mode", 0).coerceIn(0, 1)
        standingsLeague = prefs.matchStandingsLeague
        val root = LinearLayout(this)
        root.orientation = if (phoneLayout) LinearLayout.VERTICAL else LinearLayout.HORIZONTAL
        root.background = bgGradient()
        val pad = if (phoneLayout) 10f else 24f
        root.setPadding(px(pad), px(if (phoneLayout) 8f else 18f), px(pad), px(if (phoneLayout) 8f else 18f))
        if (phoneLayout) {
            root.addView(buildContent(), Ui.lp(Ui.MATCH, 0, 1f))
            root.addView(buildSidebar(), Ui.lpm(Ui.MATCH, px(62f), 0f, 0, px(7f), 0, 0))
        } else {
            root.addView(buildContent(), Ui.lp(0, Ui.MATCH, 1f))
            root.addView(buildSidebar(), Ui.lpm(px(132f), Ui.MATCH, 0f, px(18f), 0, 0, 0))
        }
        show(root)
        load(false, true)
    }

    override fun onResume() {
        super.onResume()
        if (::rows.isInitialized && panelMode == 1) loadStandings(false)
        else if (::rows.isInitialized && dataSnapshot.isNotEmpty()) { animateCards = false; render() }
        handler.removeCallbacks(clock)
        handler.post(clock)
        handler.removeCallbacks(autoRefresh)
        handler.postDelayed(autoRefresh, if (anyLive) 45_000L else 90_000L)
    }

    override fun onPause() {
        handler.removeCallbacks(clock)
        handler.removeCallbacks(autoRefresh)
        super.onPause()
    }

    private fun buildContent(): View {
        val col = LinearLayout(this)
        col.orientation = LinearLayout.VERTICAL
        col.clipChildren = false
        col.clipToPadding = false

        val top = LinearLayout(this)
        top.orientation = LinearLayout.HORIZONTAL
        top.layoutDirection = View.LAYOUT_DIRECTION_LTR
        top.gravity = Gravity.CENTER_VERTICAL
        top.setPadding(px(8f), 0, px(8f), px(8f))

        val clockBox = LinearLayout(this)
        clockBox.orientation = LinearLayout.VERTICAL
        clockBox.gravity = Gravity.START
        tvTime = Ui.label(this, "--:--", if (phoneLayout) 28f else 44f, ut.text, true)
        tvDate = Ui.label(this, "", if (phoneLayout) 11f else 21f, ut.dim)
        clockBox.addView(tvTime)
        clockBox.addView(tvDate)
        top.addView(clockBox, Ui.lp(0, Ui.WRAP, 1f))

        val title = LinearLayout(this)
        title.orientation = LinearLayout.VERTICAL
        title.gravity = Gravity.CENTER
        title.addView(Ui.label(this, "المباريات", if (phoneLayout) 23f else 45f, ut.text, true))
        title.addView(Ui.label(this, "تابع مباريات فرقك المفضلة", if (phoneLayout) 10f else 21f, ut.dim))
        top.addView(title, Ui.lp(0, Ui.WRAP, 1.5f))

        val placeBox = LinearLayout(this)
        placeBox.orientation = LinearLayout.HORIZONTAL
        placeBox.gravity = Gravity.CENTER_VERTICAL
        tvPlace = Ui.label(this, "📍 " + prefs.cityName, if (phoneLayout) 11f else 23f, ut.text, true)
        Ui.ellipsize(tvPlace)
        tvWeather = Ui.label(this, "☀ --°", if (phoneLayout) 12f else 22f, ut.text)
        placeBox.addView(tvPlace, Ui.lp(0, Ui.WRAP, 1f))
        placeBox.addView(tvWeather)
        top.addView(placeBox, Ui.lp(0, Ui.WRAP, 1f))
        col.addView(top, Ui.lpm(Ui.MATCH, Ui.WRAP, 0f, 0, 0, 0, px(8f)))
        col.addView(buildModeTabs(), Ui.lpm(Ui.MATCH, Ui.WRAP, 0f, 0, 0, 0, px(8f)))

        rows = LinearLayout(this)
        rows.orientation = LinearLayout.VERTICAL
        rows.clipChildren = false
        rows.clipToPadding = false
        if (phoneLayout) {
            val verticalScroll = android.widget.ScrollView(this).apply {
                isFillViewport = true
                isVerticalScrollBarEnabled = true
                overScrollMode = View.OVER_SCROLL_IF_CONTENT_SCROLLS
                addView(rows, Ui.lp(Ui.MATCH, Ui.WRAP))
            }
            col.addView(verticalScroll, Ui.lp(Ui.MATCH, 0, 1f))
        } else {
            col.addView(rows, Ui.lp(Ui.MATCH, 0, 1f))
        }

        val footer = LinearLayout(this)
        footer.orientation = LinearLayout.HORIZONTAL
        footer.layoutDirection = View.LAYOUT_DIRECTION_LTR
        footer.gravity = Gravity.CENTER_VERTICAL
        footer.setPadding(px(8f), px(8f), px(8f), 0)
        footer.addView(Ui.label(this, "▦ قادمة", if (phoneLayout) 10f else 18f, Contrast.ensure(MatchCardPalettes.get(prefs.matchCardPalette).up, ut.bgBottom)), Ui.lpm(Ui.WRAP, Ui.WRAP, 0f, 0, 0, px(22f), 0))
        footer.addView(Ui.label(this, "● جارية", if (phoneLayout) 10f else 18f, Contrast.ensure(MatchCardPalettes.get(prefs.matchCardPalette).live, ut.bgBottom)), Ui.lpm(Ui.WRAP, Ui.WRAP, 0f, 0, 0, px(22f), 0))
        footer.addView(Ui.label(this, "■ منتهية", if (phoneLayout) 10f else 18f, Contrast.ensure(MatchCardPalettes.get(prefs.matchCardPalette).fin, ut.bgBottom)), Ui.lpm(Ui.WRAP, Ui.WRAP, 0f, 0, 0, px(22f), 0))
        status = Ui.label(this, "↻ تحديث النتائج • جارٍ التحميل...", if (phoneLayout) 10f else 17f, ut.dim, true)
        status.gravity = Gravity.END or Gravity.CENTER_VERTICAL
        status.isFocusable = true
        status.isClickable = true
        status.setOnClickListener { load(false, true) }
        footer.addView(status, Ui.lp(0, Ui.WRAP, 1f))
        col.addView(footer, Ui.lpm(Ui.MATCH, Ui.WRAP, 0f, 0, px(6f), 0, 0))
        return col
    }

    private fun sideItem(emoji: String, label: String, selected: Boolean, onClick: () -> Unit): TextView {
        val phone = phoneLayout
        val tv = Ui.label(this, if (phone) "$emoji $label" else "$emoji\n$label", if (phone) 11f else 20f, ut.text, selected)
        tv.gravity = Gravity.CENTER
        tv.setPadding(px(if (phone) 7f else 6f), px(if (phone) 4f else 10f), px(if (phone) 7f else 6f), px(if (phone) 4f else 10f))
        Ui.focusable(tv, ut, 14f, if (selected) Ui.withAlpha(ut.accent, 80) else Color.TRANSPARENT)
        tv.isClickable = true
        tv.setOnClickListener { onClick() }
        return tv
    }

    private fun buildSidebar(): View {
        val phone = phoneLayout
        val bar = LinearLayout(this)
        bar.orientation = if (phone) LinearLayout.HORIZONTAL else LinearLayout.VERTICAL
        bar.gravity = Gravity.CENTER
        bar.setPadding(px(5f), px(4f), px(5f), px(4f))
        bar.clipChildren = false
        bar.background = Ui.round(Surfaces.panel(ut), px(18f).toFloat(), px(1f), Surfaces.edge(ut))
        fun add(emoji: String, label: String, selected: Boolean = false, click: () -> Unit) {
            val item = sideItem(emoji, label, selected, click)
            if (phone) bar.addView(item, Ui.lpm(px(82f), Ui.MATCH, 0f, px(2f), 0, px(2f), 0))
            else bar.addView(item, Ui.lpm(Ui.MATCH, 0, 1f, 0, px(3f), 0, px(3f)))
        }
        add("⌂", "الرئيسية") { finish() }
        add("⚽", "المباريات", true) { load(false, true) }
        add("🌐", "يلا شووت") { startActivity(Intent(this, YallaScoresActivity::class.java)) }
        add("▦", "التطبيقات") { startActivity(Intent(this, AppsActivity::class.java)) }
        add("▶", "الفيديو") { startActivity(Intent(this, IptvProActivity::class.java)) }
        add("♧", "الإشعارات") { startActivity(Intent(this, NotificationCenterActivity::class.java)) }
        add("⚙", "الإعدادات") { startActivity(Intent(this, SettingsActivity::class.java)) }
        if (!phone) return bar
        return HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
            overScrollMode = View.OVER_SCROLL_NEVER
            addView(bar, Ui.lp(Ui.WRAP, Ui.MATCH))
        }
    }

    private var loadStartedAt = 0L

    private fun load(silent: Boolean, refreshWeather: Boolean = false) {
        if (panelMode == 1) { loadStandings(silent); return }
        // حارس: لو تعلّق تحميل سابق أكثر من 30 ثانية لا نترك الشاشة عالقة على loading=true.
        if (loading && System.currentTimeMillis() - loadStartedAt < 30_000L) return
        loading = true
        loadStartedAt = System.currentTimeMillis()
        if (!silent) status.text = "جارٍ جلب بيانات المصادر..."

        val selected = MatchRepo.allTeams(prefs).filter { prefs.matchFavoriteTeams.contains(it.id) }
        val taskCount = selected.size + if (refreshWeather) 1 else 0
        if (taskCount == 0) {
            loading = false
            dataSnapshot = emptyList()
            render()
            return
        }

        val results = ConcurrentHashMap<String, TeamData>()
        val matchFailed = AtomicBoolean(false)
        val temperature = AtomicReference("")
        val completed = AtomicInteger(0)

        fun finishTask() {
            if (completed.incrementAndGet() != taskCount) return
            val out = selected.mapNotNull { results[it.id] }
            val failed = matchFailed.get() || (selected.isNotEmpty() && out.none { it.ok })
            val temp = temperature.get()
            Net.ui {
                if (isFinishing || isDestroyed) return@ui
                loading = false
                dataSnapshot = out
                anyLive = out.any { it.live != null }
                if (temp.isNotBlank()) tvWeather.text = temp
                tvPlace.text = "📍 " + prefs.cityName
                // اكتشاف الهدف: مقارنة نتيجة المباريات الجارية بآخر نتيجة معروفة
                var goalNote = ""
                for (td in out) {
                    val lm = td.live ?: continue
                    val h = lm.homeScore ?: continue
                    val a = lm.awayScore ?: continue
                    val now = "$h-$a"
                    val before = lastLiveScores.put(lm.id, now)
                    if (before != null && before != now) goalNote = "⚽ تغيّرت النتيجة: " + lm.home + " " + h + " - " + a + " " + lm.away
                }
                // إعادة البناء فقط عند تغيّر البيانات فعلاً؛ التحديث الصامت بلا تغيير لا يعيد رسم البطاقات (يمنع التهنيج)
                val newSig = out.joinToString("#") { td ->
                    listOf(td.live, td.next, td.last).joinToString(",") { m ->
                        if (m == null) "-" else m.id + ":" + m.homeScore + ":" + m.awayScore + ":" + m.status + ":" + m.progress + ":" + m.startMs
                    } + ":" + td.badge + ":" + td.ok
                }
                val changed = newSig != renderedSig || rows.childCount == 0
                if (changed || !silent) {
                    renderedSig = newSig
                    animateCards = !silent
                    render()
                } else {
                    val st = MatchRepo.status(prefs)
                    val src = MatchRepo.sourceLabel(prefs)
                    status.text = "↻ تحديث • $src • " + st.describeAr(System.currentTimeMillis())
                }
                if (goalNote.isNotEmpty()) {
                    status.text = goalNote
                    android.widget.Toast.makeText(this, goalNote, android.widget.Toast.LENGTH_LONG).show()
                }
                if (failed) status.text = "تعذر الوصول لمصدر النتائج لبعض الفرق • تحقق من الإنترنت أو المصدر ثم اضغط ↻ تحديث"
            }
        }

        // Fetch each tracked club independently. The previous serial loop multiplied
        // network timeouts by the number of clubs (up to ~27 seconds on a weak phone).
        for (def in selected) {
            val accepted = Net.submitBg {
                try {
                    val data = fetchTeam(def)
                    results[def.id] = data
                    if (!data.ok) matchFailed.set(true)
                } catch (_: Exception) {
                    matchFailed.set(true)
                } finally {
                    finishTask()
                }
            }
            if (!accepted) {
                matchFailed.set(true)
                finishTask()
            }
        }

        if (refreshWeather) {
            val accepted = Net.submitBg {
                try {
                    val weather = WeatherRepo.fetch(prefs.cityLat, prefs.cityLon)
                    temperature.set(WeatherRepo.describe(weather.code, weather.isDay).first + " " + Math.round(weather.temp) + "°C")
                } catch (_: Exception) {
                    // Weather is optional and must not hide the match data result.
                } finally {
                    finishTask()
                }
            }
            if (!accepted) finishTask()
        }
    }

    private fun fetchTeam(def: TeamDef): TeamData {
        return try {
            val id = MatchRepo.teamId(prefs, def)
            val now = System.currentTimeMillis()
            // Keep match list rendering network-light. Missing team crests use the local
            // placeholder; do not issue a logo API request for every opponent on every card.
            var nextList = MatchRepo.next(prefs, id)
            var lastList = MatchRepo.last(prefs, id)
            // Always make one low-cost YSscores fallback for supported clubs when configured
            // providers return nothing; this avoids empty phone screens caused by a stale source choice.
            if (nextList.isEmpty() && lastList.isEmpty()) {
                val nowFallback = System.currentTimeMillis()
                val fallback = YallaScoresProvider.fetchTeam(def, prefs.cityTimezone)
                nextList = fallback.filter { it.upcoming(nowFallback) || it.live(nowFallback) }
                    .sortedBy { if (it.startMs > 0L) it.startMs else Long.MAX_VALUE }
                lastList = fallback.filter { it.previous(nowFallback) }
                    .sortedByDescending { it.startMs }
            }
            fun isTeamName(value: String): Boolean {
                val found = value.lowercase(Locale.ROOT).replace(Regex("[^\\p{L}\\p{N}]"), "")
                return listOf(def.arabic, def.query).any { wanted ->
                    val target = wanted.lowercase(Locale.ROOT).replace(Regex("[^\\p{L}\\p{N}]"), "")
                    target.isNotBlank() && (found == target || found.contains(target) || target.contains(found))
                }
            }
            val badge = (nextList + lastList).firstNotNullOfOrNull { m ->
                when {
                    isTeamName(m.home) -> m.homeBadge.takeIf { it.isNotBlank() }
                    isTeamName(m.away) -> m.awayBadge.takeIf { it.isNotBlank() }
                    else -> null
                }
            }.orEmpty()
            val live0 = (nextList + lastList).distinctBy { it.id }.firstOrNull { it.live(now) }
            val next0 = nextList.firstOrNull { it.id != live0?.id && it.upcoming(now) }
            val last0 = lastList.firstOrNull { it.id != live0?.id }
            // تكميل شعارات الخصم للبطاقات الظاهرة فقط (الجارية/القادمة/الأخيرة) وبعدد طلبات محدود؛ يعمل في الخلفية والباقي من الكاش
            val budget = intArrayOf(4)
            val live = live0?.let { try { MatchRepo.fillBadges(it, prefs, budget) } catch (_: Exception) { it } }
            val next = next0?.let { try { MatchRepo.fillBadges(it, prefs, budget) } catch (_: Exception) { it } }
            val last = last0?.let { try { MatchRepo.fillBadges(it, prefs, budget) } catch (_: Exception) { it } }
            val repoBadge = try { FootballLogos.urlFor(prefs.context, def.query) } catch (_: Exception) { "" }
            TeamData(def, repoBadge.ifEmpty { badge }, next, live, last, nextList.isNotEmpty() || lastList.isNotEmpty())
        } catch (_: Exception) {
            TeamData(def, "", null, null, null, false)
        }
    }

    private fun render() {
        if (panelMode == 1) { renderStandingsMessage("اختر جدول الدوري لعرض الترتيب") ; return }
        rows.removeAllViews()
        val selected = dataSnapshot.take(6)
        if (selected.isEmpty()) {
            val empty = LinearLayout(this)
            empty.orientation = LinearLayout.VERTICAL
            empty.gravity = Gravity.CENTER
            empty.addView(Ui.label(this, "لم تختر فرقاً للمتابعة بعد", if (phoneLayout) 22f else 32f, ut.text, true))
            empty.addView(Ui.label(this, "افتح إعدادات الأندية واختر الفرق التي تريدها", if (phoneLayout) 15f else 22f, ut.dim))
            empty.addView(Ui.button(this, ut, "⚽ اختيار الأندية والفرق", if (phoneLayout) 17f else 25f) {
                startActivity(Intent(this, MatchPreferencesActivity::class.java))
            }, Ui.lpm(Ui.WRAP, Ui.WRAP, 0f, 0, px(18f), 0, 0))
            rows.addView(empty, Ui.lp(Ui.MATCH, Ui.MATCH))
            status.text = "اختر أنديتك المفضلة من الإعدادات"
            return
        }
        rows.addView(buildTeamStrip(selected), Ui.lpm(Ui.MATCH, px(if (phoneLayout) 64f else 74f), 0f, 0, 0, 0, px(8f)))
        if (phoneLayout) {
            val rowHeight = px(176f)
            rows.addView(buildMatchRow("next", "المباراة القادمة", selected), Ui.lpm(Ui.MATCH, rowHeight, 0f, 0, 0, 0, px(8f)))
            rows.addView(buildMatchRow("live", "المباراة الحالية", selected), Ui.lpm(Ui.MATCH, rowHeight, 0f, 0, 0, 0, px(8f)))
            rows.addView(buildMatchRow("last", "المباراة السابقة", selected), Ui.lpm(Ui.MATCH, rowHeight, 0f, 0, 0, 0, px(8f)))
        } else {
            rows.addView(buildMatchRow("next", "المباراة القادمة", selected), Ui.lp(Ui.MATCH, 0, 1f))
            rows.addView(buildMatchRow("live", "المباراة الحالية", selected), Ui.lpm(Ui.MATCH, 0, 1f, 0, px(8f), 0, px(8f)))
            rows.addView(buildMatchRow("last", "المباراة السابقة", selected), Ui.lp(Ui.MATCH, 0, 1f))
        }

        val st = MatchRepo.status(prefs)
        val src = MatchRepo.sourceLabel(prefs)
        val dataCount = selected.count { it.ok }
        status.text = when {
            dataCount == 0 -> "↻ تحديث • تعذر تحديث النتائج • $src • ${st.describeAr(System.currentTimeMillis())}"
            else -> "↻ تحديث • $src • بيانات متاحة $dataCount/${selected.size} • ${st.describeAr(System.currentTimeMillis())}"
        }
    }

    private fun buildModeTabs(): View {
        val box = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER; layoutDirection = View.LAYOUT_DIRECTION_LTR }
        val matches = Ui.chip(this, ut, "⚽ المباريات", panelMode == 0, if (phoneLayout) 15f else 22f) {
            selectPanelMode(0)
            load(false, false)
        }
        val table = Ui.chip(this, ut, "▦ جدول الدوري", panelMode == 1, if (phoneLayout) 15f else 22f) {
            selectPanelMode(1)
            loadStandings(false)
        }
        box.addView(matches, Ui.lpm(Ui.WRAP, Ui.WRAP, 0f, 0, 0, px(8f), 0))
        box.addView(table, Ui.lpm(Ui.WRAP, Ui.WRAP, 0f, 0, 0, 0, 0))
        viewTabs = listOf(matches, table)
        return box
    }

    private var viewTabs: List<TextView> = emptyList()

    private fun selectPanelMode(mode: Int) {
        panelMode = mode.coerceIn(0, 1)
        viewTabs.forEachIndexed { index, view -> Ui.styleChip(view, ut, index == panelMode) }
    }

    private fun loadStandings(silent: Boolean) {
        if (standingsLoading) return
        standingsLoading = true
        if (!silent) status.text = "جاري تحميل جدول ${EspnSoccerProvider.leagueLabel(standingsLeague)}..."
        rows.removeAllViews()
        rows.addView(Ui.label(this, "⏳ جاري جلب جدول الدوري...", if (phoneLayout) 16f else 25f, ut.dim), Ui.lpm(Ui.MATCH, Ui.WRAP, 0f, 0, 0, 0, px(12f)))
        val league = standingsLeague
        val accepted = Net.submitBg {
            val result = runCatching { EspnSoccerProvider.standings(league) }
            Net.ui {
                standingsLoading = false
                if (isFinishing || isDestroyed) return@ui
                if (panelMode != 1 || league != standingsLeague) return@ui
                // فشل الشبكة/القراءة يختلف عن «لا بيانات»: لا نعرض جدولًا شكله مؤكد في الحالتين.
                result.fold(
                    onSuccess = { renderStandings(it) },
                    onFailure = { rows.removeAllViews(); renderStandingsMessage("تعذر تحميل جدول ${EspnSoccerProvider.leagueLabel(league)}. تحقق من الاتصال وأعد المحاولة.") }
                )
            }
        }
        if (!accepted) {
            standingsLoading = false
            renderStandingsMessage("تعذر بدء تحميل جدول الدوري الآن")
        }
    }

    private fun renderStandings(items: List<EspnSoccerProvider.Standing>) {
        rows.removeAllViews()
        rows.addView(buildLeagueSelector(), Ui.lpm(Ui.MATCH, Ui.WRAP, 0f, 0, 0, 0, px(10f)))
        if (items.isEmpty()) {
            renderStandingsMessage("المصدر لم يُرجع ترتيبًا لـ ${EspnSoccerProvider.leagueLabel(standingsLeague)} حاليًا (قد لا تكون المسابقة مغطاة).")
            return
        }
        val head = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(px(12f), px(10f), px(12f), px(10f))
            background = Ui.round(Surfaces.panel(ut), px(14f).toFloat(), px(1f), Surfaces.edge(ut))
        }
        val headers = listOf("#" to 0.42f, "الفريق" to 1.9f, "لعب" to .55f, "ف" to .5f, "ت" to .5f, "خ" to .5f, "له" to .55f, "عليه" to .65f, "+/-" to .7f, "ن" to .65f)
        for ((h,w) in headers) head.addView(Ui.label(this, h, if(phoneLayout) 11f else 16f, ut.dim, true), Ui.lp(0, Ui.WRAP, w))
        rows.addView(head, Ui.lpm(Ui.MATCH, Ui.WRAP, 0f, 0, 0, 0, px(6f)))
        items.take(20).forEachIndexed { idx, st ->
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                layoutDirection = View.LAYOUT_DIRECTION_LTR
                setPadding(px(12f), px(10f), px(12f), px(10f))
                background = if (st.teamId in prefs.matchFavoriteTeams)
                    Ui.round(Ui.withAlpha(Color.parseColor("#E8C98A"), 90), px(12f).toFloat(), px(2f), Color.parseColor("#E8C98A"))
                else Ui.round(if (idx % 2 == 0) Surfaces.panel(ut) else ut.card, px(12f).toFloat(), px(1f), Ui.withAlpha(ut.accent, 38))
                isFocusable = true
                isClickable = true
            }
            val cells = listOf(st.rank.toString(), st.team, st.played.toString(), st.wins.toString(), st.draws.toString(), st.losses.toString(), st.goalsFor.toString(), st.goalsAgainst.toString(), st.goalDiff.toString(), st.points.toString())
            cells.forEachIndexed { i, v ->
                val tv = Ui.label(this, v, if(phoneLayout) 12f else 18f, if (i == 9) ut.accent else ut.text, i == 1 || i == 9)
                tv.gravity = Gravity.CENTER_VERTICAL
                tv.maxLines = 1
                tv.ellipsize = android.text.TextUtils.TruncateAt.END
                val weight = headers[i].second
                row.addView(tv, Ui.lp(0, Ui.WRAP, weight))
            }
            Ui.focusable(row, ut, 12f)
            rows.addView(row, Ui.lpm(Ui.MATCH, Ui.WRAP, 0f, 0, 0, 0, px(4f)))
        }
        status.text = "▦ ${EspnSoccerProvider.leagueLabel(standingsLeague)} • ${items.size} فريقاً • مصدر ESPN البديل"
    }

    private fun buildLeagueSelector(): View {
        val box = HorizontalScrollView(this).apply { isHorizontalScrollBarEnabled = false; overScrollMode = View.OVER_SCROLL_NEVER }
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; layoutDirection = View.LAYOUT_DIRECTION_LTR; gravity = Gravity.CENTER }
        // ESPN Egypt slug is retained as an experimental selector; its coverage is not verified.
        val leagues = listOf("egy.1" to "المصري", "eng.1" to "الإنجليزي", "esp.1" to "الإسباني")
        for ((key,label) in leagues) {
            val displayLabel = if (key == "egy.1") "المصري*" else label
            val b = Ui.chip(this, ut, displayLabel, key == standingsLeague, if(phoneLayout) 14f else 19f) {
                standingsLeague = key
                prefs.matchStandingsLeague = key
                loadStandings(false)
            }
            row.addView(b, Ui.lpm(Ui.WRAP, Ui.WRAP, 0f, 0, 0, px(8f), 0))
        }
        box.addView(row, Ui.lp(Ui.WRAP, Ui.WRAP))
        return box
    }

    private fun renderStandingsMessage(message: String) {
        rows.removeAllViews()
        rows.gravity = Gravity.CENTER
        rows.addView(buildLeagueSelector(), Ui.lpm(Ui.MATCH, Ui.WRAP, 0f, 0, 0, 0, px(10f)))
        rows.addView(Ui.label(this, "▦ جدول الدوري", if(phoneLayout) 22f else 34f, ut.text, true), Ui.lpm(Ui.WRAP, Ui.WRAP, 0f, 0, 0, 0, px(10f)))
        rows.addView(Ui.label(this, message, if(phoneLayout) 15f else 22f, ut.dim), Ui.lpm(Ui.WRAP, Ui.WRAP, 0f, 0, 0, 0, px(12f)))
        rows.gravity = Gravity.NO_GRAVITY
        status.text = message
    }

    private fun buildTeamStrip(data: List<TeamData>): View {
        val scroller = HorizontalScrollView(this)
        scroller.isHorizontalScrollBarEnabled = false
        scroller.overScrollMode = View.OVER_SCROLL_NEVER
        val strip = LinearLayout(this)
        strip.orientation = LinearLayout.HORIZONTAL
        strip.layoutDirection = View.LAYOUT_DIRECTION_LTR
        strip.gravity = Gravity.CENTER
        strip.background = Ui.round(Surfaces.panel(ut), px(18f).toFloat(), px(1f), Ui.withAlpha(ut.accent, 70))
        data.forEachIndexed { index, td ->
            val cell = LinearLayout(this)
            cell.orientation = LinearLayout.HORIZONTAL
            cell.gravity = Gravity.CENTER
            cell.addView(TeamBadgeUi.create(this, td.def.arabic, td.badge, 44, MatchCardPalettes.get(prefs.matchCardPalette).accent), Ui.lpm(px(44f), px(44f), 0f, 0, 0, px(10f), 0))
            val title = Ui.label(this, td.def.arabic, 23f, ut.text, true)
            title.gravity = Gravity.CENTER
            cell.addView(title)
            strip.addView(cell, Ui.lpm(matchCardWidthPx(), Ui.MATCH, 0f, px(6f), 0, px(6f), 0))
        }
        scroller.addView(strip, FrameLayout.LayoutParams(Ui.WRAP, Ui.MATCH))
        return scroller
    }

    private fun buildMatchRow(kind: String, title: String, teams: List<TeamData>): View {
        val scroller = HorizontalScrollView(this)
        scroller.isHorizontalScrollBarEnabled = false
        scroller.overScrollMode = View.OVER_SCROLL_NEVER
        val row = LinearLayout(this)
        row.orientation = LinearLayout.HORIZONTAL
        row.layoutDirection = View.LAYOUT_DIRECTION_LTR
        row.clipChildren = false
        row.clipToPadding = false
        teams.forEachIndexed { index, td ->
            val match = when (kind) { "next" -> td.next; "live" -> td.live; else -> td.last }
            val cell = matchCard(kind, title, match, td)
            row.addView(cell, Ui.lpm(matchCardWidthPx(), Ui.MATCH, 0f, 0, 0, if (index == teams.lastIndex) 0 else px(20f), 0))
        }
        scroller.addView(row, FrameLayout.LayoutParams(Ui.WRAP, Ui.MATCH))
        return scroller
    }

    /** ثلاث بطاقات ظاهرة على العرض المرجعي، مع حد أدنى يمنع تزاحم أسماء الفرق على الشاشات الأصغر. */
    private fun matchCardWidthPx(): Int {
        val screen = resources.displayMetrics.widthPixels
        if (phoneLayout) return (screen - px(28f)).coerceAtLeast(px(270f))
        val sidebarAndGap = px(150f)
        val rootInsets = px(48f)
        val rowGaps = px(36f)
        // بطاقات أعرض: ~2.6 بطاقة ظاهرة والباقي بالتمرير الأفقي، بحد أدنى 340 وحدة
        return ((screen - sidebarAndGap - rootInsets - rowGaps) * 10 / 26).coerceAtLeast(px(340f))
    }

    private fun matchCard(kind: String, title: String, match: Match?, teamData: TeamData): View {
        val card = MatchCards.build(
            this, prefs,
            MatchCardModel(kind, title, match, teamData.def.arabic, teamData.badge, teamData.ok, prefs.cityTimezone),
            compact = phoneLayout
        )
        card.elevation = px(if (kind == "live") 6f else 2f).toFloat()
        card.isClickable = match != null
        if (match != null) card.setOnClickListener {
            val i = Intent(this, MatchDetailActivity::class.java)
            i.putExtra("id", match.id)
            i.putExtra("source", match.source)
            i.putExtra("home", match.home)
            i.putExtra("away", match.away)
            i.putExtra("homeBadge", match.homeBadge)
            i.putExtra("awayBadge", match.awayBadge)
            i.putExtra("league", match.league)
            i.putExtra("venue", match.venue)
            i.putExtra("startMs", match.startMs)
            i.putExtra("hs", match.homeScore ?: -1)
            i.putExtra("as", match.awayScore ?: -1)
            i.putExtra("status", match.status)
            i.putExtra("progress", match.progress)
            i.putExtra("externalUrl", match.externalUrl)
            i.putExtra("kind", kind)
            startActivity(i)
        }
        if (animateCards) Motion.enter(card, this)
        return card
    }

    private fun tint(accent: Int): UTheme = UTheme(
        ut.name, ut.bgTop, ut.bgBottom, ut.card, ut.cardFocus, accent, ut.text, ut.dim,
        border = Ui.withAlpha(accent, 210), dock = ut.dock, dockBorder = ut.dockBorder,
        onAccent = ut.onAccent, radiusScale = ut.radiusScale, club = ut.club
    )
}
