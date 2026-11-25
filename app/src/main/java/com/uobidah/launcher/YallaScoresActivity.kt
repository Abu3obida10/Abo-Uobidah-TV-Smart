package com.uobidah.launcher

import android.content.Intent
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import java.util.Locale
import java.util.Collections
import java.util.concurrent.atomic.AtomicInteger
import java.time.LocalDate

/**
 * واجهة يلا شووت الأصلية داخل التطبيق.
 * تعرض بيانات بطاقات المباريات العامة التي يقرأها الموصل دون تشغيل صفحة ويب ثقيلة
 * أو سكربتات/إعلانات/متتبعات خارجية قد تستنزف ذاكرة أجهزة Android TV الضعيفة.
 */
class YallaScoresActivity : BaseActivity() {
    private data class Entry(val team: TeamDef, val match: Match)

    private lateinit var list: LinearLayout
    private lateinit var status: TextView
    private lateinit var filters: LinearLayout
    private var selectedTeamId: String = "all"
    private var selectedTeams: List<TeamDef> = emptyList()
    private var loading = false
    private var loadToken = 0
    private var requestedMatchId = ""
    private var currentEntries: List<Entry> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val defs = MatchRepo.allTeams(prefs)
        val favouriteIds = prefs.matchFavoriteTeams.toSet()
        selectedTeams = defs.filter { favouriteIds.contains(it.id) }.ifEmpty { defs.take(3) }

        val requestedTeam = intent.getStringExtra(EXTRA_TEAM).orEmpty()
        selectedTeamId = teamIdFromInput(requestedTeam).takeIf { id -> selectedTeams.any { it.id == id } } ?: "all"
        requestedMatchId = Regex("/match/(\\d+)/", RegexOption.IGNORE_CASE)
            .find(intent.getStringExtra(EXTRA_URL).orEmpty())?.groupValues?.getOrNull(1).orEmpty()

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = bgGradient()
            setPadding(px(18f), px(12f), px(18f), px(12f))
        }

        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutDirection = View.LAYOUT_DIRECTION_LTR
        }
        val titleBox = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_VERTICAL
        }
        titleBox.addView(Ui.label(this, "⚽ يلا شووت", 27f, ut.text, true))
        titleBox.addView(Ui.label(this, "كل مباريات أمس واليوم وغداً مع النتائج وتفاصيل الأحداث", 13f, ut.dim))
        header.addView(titleBox, Ui.lp(0, Ui.WRAP, 1f))
        val refresh = actionButton("↻ تحديث", true) { loadMatches(true) }
        header.addView(refresh, Ui.lpm(Ui.WRAP, px(44f), 0f, 0, 0, px(8f), 0))
        val back = actionButton("‹ رجوع", false) { finish() }
        header.addView(back, Ui.lpm(Ui.WRAP, px(44f), 0f, 0, 0, 0, 0))
        root.addView(header, Ui.lpm(Ui.MATCH, Ui.WRAP, 0f, 0, 0, 0, px(8f)))

        val filterScroll = HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
            overScrollMode = View.OVER_SCROLL_NEVER
        }
        filters = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        filterScroll.addView(filters)
        root.addView(filterScroll, Ui.lpm(Ui.MATCH, px(46f), 0f, 0, 0, 0, px(6f)))
        buildFilters()

        status = Ui.label(this, "جارٍ تحميل نتائج الفرق المفضلة...", 14f, ut.dim, true)
        status.gravity = Gravity.CENTER_VERTICAL or Gravity.END
        root.addView(status, Ui.lpm(Ui.MATCH, px(28f), 0f, 0, 0, 0, px(5f)))

        val scroller = ScrollView(this).apply {
            isFillViewport = false
            isVerticalScrollBarEnabled = true
            overScrollMode = View.OVER_SCROLL_IF_CONTENT_SCROLLS
        }
        list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; clipChildren = false }
        scroller.addView(list)
        root.addView(scroller, Ui.lp(Ui.MATCH, 0, 1f))
        show(root)
        loadMatches(false)
    }

    private fun teamIdFromInput(raw: String): String = when (raw.lowercase(Locale.ROOT)) {
        "ahly", "alahly", "101745", "138995" -> "138995"
        "realmadrid", "real-madrid", "8633", "133738" -> "133738"
        "manutd", "manchesterunited", "10260", "133612" -> "133612"
        else -> ""
    }

    private fun buildFilters() {
        filters.removeAllViews()
        filters.addView(filterButton("كل المباريات", selectedTeamId == "all") {
            selectedTeamId = "all"
            buildFilters()
            if (loading) renderSelection("يعرض البيانات المتاحة أثناء تحديث المباريات...") else loadMatches(false)
        }, Ui.lpm(Ui.WRAP, Ui.MATCH, 0f, 0, 0, px(7f), 0))
        for (team in selectedTeams) {
            filters.addView(filterButton(team.arabic, selectedTeamId == team.id) {
                selectedTeamId = team.id
                buildFilters()
                if (loading) renderSelection("يعرض بيانات الفريق المحفوظة أثناء التحديث...") else loadMatches(false)
            }, Ui.lpm(Ui.WRAP, Ui.MATCH, 0f, 0, 0, px(7f), 0))
        }
    }

    private fun filterButton(label: String, selected: Boolean, click: () -> Unit): TextView {
        val tv = Ui.label(this, label, 15f, if (selected) ut.onAccent else ut.text, true)
        tv.gravity = Gravity.CENTER
        tv.setPadding(px(14f), 0, px(14f), 0)
        tv.background = Ui.round(if (selected) ut.accent else Surfaces.panel(ut), px(14f).toFloat(), px(1f), if (selected) ut.accent else Surfaces.edge(ut))
        Ui.focusable(tv, ut, 14f, if (selected) ut.accent else Surfaces.panel(ut))
        tv.setOnClickListener { click() }
        return tv
    }

    private fun actionButton(label: String, primary: Boolean, click: () -> Unit): TextView {
        val tv = filterButton(label, primary, click)
        tv.textSize = 15f
        return tv
    }

    private fun renderSelection(message: String) {
        val selected = selectedTeams.firstOrNull { it.id == selectedTeamId }
        val visible = if (selectedTeamId == "all") currentEntries else currentEntries.filter {
            it.team.id == selectedTeamId || (selected != null && MatchProviders.belongsTo(it.match, selected))
        }
        render(visible, message)
    }

    private fun loadMatches(manual: Boolean) {
        if (loading) {
            renderSelection("يعرض آخر بيانات متاحة؛ التحديث الحالي مستمر في الخلفية...")
            return
        }
        val targets = if (selectedTeamId == "all") selectedTeams else selectedTeams.filter { it.id == selectedTeamId }

        // اعرض كاش الأندية فوراً؛ لا تترك الشاشة فارغة أثناء جلب الجدول العام.
        val cached = targets.flatMap { team -> MatchRepo.cachedForTeam(prefs, team.id).map { Entry(team, it) } }
            .distinctBy { it.match.id }
        if (cached.isNotEmpty()) {
            currentEntries = cached
            renderSelection("عرض آخر نتائج محفوظة محلياً — جارٍ تحديث جدول المباريات...")
        }

        loading = true
        status.text = if (manual) "جارٍ تحديث جدول المباريات والنتائج..." else "جارٍ تحميل مباريات أمس واليوم وغداً..."
        val token = ++loadToken
        val accumulated = Collections.synchronizedList(ArrayList<Entry>())
        val errors = Collections.synchronizedList(ArrayList<String>())
        val failedIds = Collections.synchronizedList(ArrayList<String>())
        val globalTeam = TeamDef("كل البطولات", "Football", "", "all")
        val dates = listOf(LocalDate.now().minusDays(1), LocalDate.now(), LocalDate.now().plusDays(1))
        val remaining = AtomicInteger(targets.size + dates.size)

        fun finishOne() {
            if (remaining.decrementAndGet() != 0) return
            val remoteItems = synchronized(accumulated) { accumulated.distinctBy { it.match.id } }
            val failed = synchronized(failedIds) { failedIds.toSet() }
            // عند فشل المصدرين لفريق، احتفظ بنتائجه المحفوظة بدلاً من مسحها.
            val fallbackItems = cached.filter { failed.contains(it.team.id) }
            val finalItems = (remoteItems + fallbackItems).distinctBy { it.match.id }
            val failedSources = synchronized(errors) { errors.toList() }
            Net.ui {
                if (isFinishing || isDestroyed || token != loadToken) return@ui
                loading = false
                currentEntries = finalItems
                val liveCount = finalItems.count { it.match.live(System.currentTimeMillis()) }
                val message = when {
                    finalItems.isNotEmpty() -> "SofaScore + YSscores • ${finalItems.size} مباراة • $liveCount جارية" +
                        if (failedSources.isNotEmpty()) " • تعذر تحديث بعض المصادر" else ""
                    failedSources.isNotEmpty() -> "تعذر جلب المباريات من المصادر المتاحة. تحقق من الإنترنت ثم أعد المحاولة."
                    else -> "لا توجد مباريات متاحة في الفترة المطلوبة حالياً."
                }
                renderSelection(message)
            }
        }

        // جدول عام لكل البطولات؛ ثلاث مهام مستقلة لا تحجب واجهة التطبيق.
        for (date in dates) {
            val accepted = Net.submitBg {
                try {
                    SofaScoreProvider.fetchAllDay(date).forEach { accumulated.add(Entry(globalTeam, it)) }
                } catch (_: Exception) { errors.add("جدول $date") }
                finally { finishOne() }
            }
            if (!accepted) { errors.add("جدول $date"); finishOne() }
        }

        // YSscores أولاً للأندية المعروفة، ثم SofaScore تلقائياً عند الحجب أو تغير HTML.
        for (team in targets) {
            val accepted = Net.submitBg {
                try {
                    val matches = try {
                        YallaScoresProvider.fetchTeam(team, prefs.cityTimezone)
                    } catch (_: Exception) {
                        SofaScoreProvider.fetchTeam(prefs, team)
                    }
                    matches.forEach { accumulated.add(Entry(team, it)) }
                } catch (_: Exception) {
                    errors.add(team.arabic)
                    failedIds.add(team.id)
                } finally { finishOne() }
            }
            if (!accepted) { errors.add(team.arabic); failedIds.add(team.id); finishOne() }
        }
    }

    private fun render(items: List<Entry>, message: String) {
        if (!::list.isInitialized) return
        list.removeAllViews()
        status.text = message
        if (items.isEmpty()) {
            val empty = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                setPadding(px(20f), px(44f), px(20f), px(44f))
            }
            empty.addView(Ui.label(this, "⚽ لا توجد بيانات مباريات الآن", 22f, ut.text, true))
            empty.addView(Ui.label(this, "الشاشة تعرض النتائج داخل التطبيق بدون تحميل موقع ويب ثقيل.", 14f, ut.dim))
            empty.addView(Ui.button(this, ut, "↻ إعادة المحاولة", 16f) { loadMatches(true) }, Ui.lpm(Ui.WRAP, Ui.WRAP, 0f, 0, px(16f), 0, 0))
            list.addView(empty, Ui.lp(Ui.MATCH, Ui.WRAP))
            return
        }
        val now = System.currentTimeMillis()
        val prioritized = items.distinctBy { it.match.id }.sortedWith(compareBy<Entry> {
            when {
                requestedMatchId.isNotBlank() && (it.match.id.endsWith(requestedMatchId) || it.match.externalUrl.contains("/match/$requestedMatchId/")) -> 0
                it.match.upcoming(now) -> 1
                it.match.live(now) -> 2
                it.match.finished(now) -> 3
                it.match.canceled() || it.match.postponed() || it.match.suspended() -> 4
                else -> 5
            }
        }.thenBy { if (it.match.startMs > 0L) it.match.startMs else Long.MAX_VALUE })
        // Match sections follow the requested order: upcoming, live, previous results.
        // Postponed/canceled/suspended and unconfirmed matches must never be mislabeled as results.
        val groups = listOf(
            Triple("🟢 مباريات قادمة", "next", prioritized.filter { it.match.upcoming(now) }),
            Triple("🔴 مباريات جارية", "live", prioritized.filter { it.match.live(now) }),
            Triple("⚪ نتائج سابقة", "last", prioritized.filter { it.match.previous(now) }),
            Triple("⏸ حالات مؤجلة أو ملغاة أو متوقفة", "status", prioritized.filter {
                it.match.canceled() || it.match.postponed() || it.match.suspended()
            }),
            Triple("؟ مباريات حالتها غير مؤكدة", "unknown", prioritized.filter { it.match.state(now) == MatchState.UNKNOWN })
        )
        for ((heading, kind, entries) in groups) {
            if (entries.isEmpty()) continue
            list.addView(Ui.label(this, heading, 19f, ut.accent, true), Ui.lpm(Ui.WRAP, Ui.WRAP, 0f, 0, px(12f), 0, px(7f)))
            for (entry in entries.take(10)) {
                val card = MatchCards.build(
                    this, prefs,
                    MatchCardModel(kind, heading, entry.match, entry.team.arabic,
                        if (entry.match.homeBadge.isNotBlank()) entry.match.homeBadge else entry.match.awayBadge,
                        true, prefs.cityTimezone),
                    compact = true
                )
                card.isClickable = true
                card.setOnClickListener { openMatch(entry.match, kind) }
                list.addView(card, Ui.lpm(Ui.MATCH, Ui.WRAP, 0f, 0, 0, 0, px(9f)))
            }
        }
        if (list.childCount == 0) list.addView(Ui.label(this, "لا توجد مباريات قابلة للعرض.", 16f, ut.dim))
    }

    private fun openMatch(match: Match, kind: String) {
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

    override fun onDestroy() {
        loadToken++
        loading = false
        super.onDestroy()
    }

    companion object {
        const val EXTRA_TEAM = "ysscores_team"
        const val EXTRA_URL = "ysscores_url"

        fun teamIntent(activity: android.content.Context, team: String): Intent =
            Intent(activity, YallaScoresActivity::class.java).putExtra(EXTRA_TEAM, team)
    }
}
