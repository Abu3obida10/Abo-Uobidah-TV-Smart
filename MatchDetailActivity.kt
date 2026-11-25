package com.uobidah.launcher

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.uobidah.launcher.core.Contrast
import com.uobidah.launcher.core.TimelineKind
import com.uobidah.launcher.core.TimelineIntegrity
import com.uobidah.launcher.core.MatchScoreReconciler
import java.util.Locale

/** تفاصيل مباراة: النتيجة والموعد والملعب + التشكيلة + أحداث المباراة (إن وفّرها المصدر). */
class MatchDetailActivity : BaseActivity() {

    private val phoneLayout: Boolean
        get() = resources.displayMetrics.widthPixels / resources.displayMetrics.density < 600f
    private lateinit var body: LinearLayout
    private lateinit var status: TextView
    private lateinit var scoreTv: TextView
    private lateinit var pillTv: TextView
    private lateinit var outcomeTv: TextView
    private val handler = Handler(Looper.getMainLooper())
    private var liveRefreshing = false
    private var detailId = ""
    private var detailHome = ""
    private var detailAway = ""
    private var detailStartMs = 0L
    private val infoValueViews = HashMap<String, TextView>()

    /** يملأ صفوف «معلومات اللقاء» من الصفحة العامة للمباراة في YSscores؛ ما لا تعيده الصفحة يبقى «غير متاح». */
    private fun loadYallaInfo(url: String) {
        val u = runCatching { android.net.Uri.parse(url) }.getOrNull() ?: return
        val host = u.host.orEmpty().lowercase()
        if (u.scheme != "https" || !(host == "ysscores.com" || host == "www.ysscores.com") || !(u.path.orEmpty().contains("/match/"))) return
        Net.submitBg {
            val info = try {
                com.uobidah.launcher.core.YallaMatchInfoParser.parse(String(Net.getBytes(url, timeoutMs = 12_000, maxBytes = 3 * 1024 * 1024), Charsets.UTF_8))
            } catch (_: Exception) { emptyMap() }
            Net.ui {
                if (isFinishing || isDestroyed) return@ui
                for ((k, v) in info) {
                    // وقت/تاريخ الصفحة يتبعان منطقة زمنية للزائر غير معروفة؛ نعرض فقط موعدنا نحن المحسوب بمنطقة المستخدم.
                    if (k == "وقت المباراة" || k == "تاريخ المباراة") continue
                    val label = when (k) { "القناة" -> "القناة الناقلة"; "الحكم" -> "حكم المباراة"; else -> k }
                    val tv = infoValueViews[label] ?: continue
                    tv.text = v; tv.setTextColor(ut.text)
                }
            }
        }
    }

    /** يكمّل بيانات SofaScore الناقصة فقط؛ لا يستبدل تفاصيل YSscores إن كانت متاحة. */
    private fun applySofaEventInfo(info: SofaScoreEventInfo?) {
        if (info == null) return
        val values = linkedMapOf(
            "البطولة" to info.tournament,
            "الجولة" to info.round,
            "ملعب المباراة" to listOf(info.venue, info.city).filter { it.isNotBlank() }.joinToString("، "),
            "حكم المباراة" to info.referee,
            "الحضور" to info.attendance,
            "مدرب صاحب الأرض" to info.homeManager,
            "مدرب الضيف" to info.awayManager
        )
        for ((label, value) in values) {
            if (value.isBlank()) continue
            val tv = infoValueViews[label] ?: continue
            if (tv.text.isNullOrBlank() || tv.text.toString() == "غير متاح من المصدر") {
                tv.text = value
                tv.setTextColor(ut.text)
            }
        }
    }

    private var resolvedEventId = ""
    private var detailSource = "thesportsdb"
    private var detailHomeScore: Int? = null
    private var detailAwayScore: Int? = null
    private var timelineWarning = ""
    private var lastKnownScore = ""
    private var usedSofaLive = false
    private var refreshing = false
    private var shownSig = ""
    private var storedLineup: List<LineupPlayer> = emptyList()
    private var storedLineupInfo: SofaScoreLineupData? = null
    private var storedLineupSource = "TheSportsDB"
    private var storedTimeline: List<TimelineEvent> = emptyList()
    private var storedSource = "TheSportsDB"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val id = intent.getStringExtra("id") ?: ""
        val source = intent.getStringExtra("source") ?: "thesportsdb"
        val home = intent.getStringExtra("home") ?: ""
        val away = intent.getStringExtra("away") ?: ""
        val homeBadge = intent.getStringExtra("homeBadge") ?: ""
        val awayBadge = intent.getStringExtra("awayBadge") ?: ""
        val league = intent.getStringExtra("league") ?: ""
        val venue = intent.getStringExtra("venue") ?: ""
        val startMs = intent.getLongExtra("startMs", 0L)
        val hs = intent.getIntExtra("hs", -1)
        val aw = intent.getIntExtra("as", -1)
        val statusRaw = intent.getStringExtra("status") ?: ""
        val progressRaw = intent.getStringExtra("progress") ?: ""
        val externalUrl = intent.getStringExtra("externalUrl") ?: ""
        val nowMs = System.currentTimeMillis()
        val matchAtOpen = Match(id, home, away, startMs, league, if (hs >= 0) hs else null, if (aw >= 0) aw else null, statusRaw, progressRaw)
        val state = matchAtOpen.state(nowMs)
        val initialScoreIsValid = matchAtOpen.hasDisplayableScore(nowMs)
        detailHomeScore = if (initialScoreIsValid) hs else null
        detailAwayScore = if (initialScoreIsValid) aw else null
        val stateColor = Contrast.ensure(MatchCards.statePalette(MatchCardPalettes.get(prefs.matchCardPalette), state), ut.bgTop)

        val root = LinearLayout(this)
        root.orientation = LinearLayout.VERTICAL
        root.background = bgGradient()
        root.setPadding(px(if (phoneLayout) 12f else 56f), px(if (phoneLayout) 10f else 28f), px(if (phoneLayout) 12f else 56f), px(if (phoneLayout) 10f else 20f))
        root.addView(header("⚽ تفاصيل المباراة"), Ui.lp(Ui.MATCH, Ui.WRAP))

        val top = LinearLayout(this)
        // On phones, keep the two clubs side-by-side and put the score in its own full-width
        // row. This prevents long Arabic/English team names from squeezing or flipping the score.
        top.orientation = if (phoneLayout) LinearLayout.VERTICAL else LinearLayout.HORIZONTAL
        top.gravity = Gravity.CENTER
        Ui.staticCard(top, ut, 30f)
        top.setPadding(px(if (phoneLayout) 10f else 24f), px(if (phoneLayout) 10f else 14f), px(if (phoneLayout) 10f else 24f), px(if (phoneLayout) 10f else 14f))
        val mid = LinearLayout(this)
        mid.orientation = LinearLayout.VERTICAL
        mid.gravity = Gravity.CENTER_HORIZONTAL
        val pill = Ui.label(this, "● " + state.labelAr + (if (state == MatchState.LIVE && progressRaw.isNotBlank()) "  " + progressRaw else ""), if (phoneLayout) 12f else 24f, stateColor, true)
        pillTv = pill
        pill.gravity = Gravity.CENTER
        pill.textAlignment = View.TEXT_ALIGNMENT_CENTER
        pill.setPadding(px(if (phoneLayout) 8f else 16f), px(4f), px(if (phoneLayout) 8f else 16f), px(4f))
        pill.background = Ui.round(Ui.withAlpha(stateColor, 45), px(10f).toFloat(), px(2f), stateColor)
        mid.addView(pill, Ui.lpm(Ui.WRAP, Ui.WRAP, 0f, 0, 0, 0, px(5f)))
        mid.addView(Ui.label(this, when (state) {
            MatchState.FINISHED -> "النتيجة النهائية"
            MatchState.LIVE -> "النتيجة المباشرة"
            MatchState.UPCOMING -> "موعد المباراة"
            else -> "النتيجة غير معتمدة"
        }, if (phoneLayout) 11f else 19f, ut.dim, true),
            Ui.lpm(Ui.WRAP, Ui.WRAP, 0f, 0, 0, 0, px(1f)))
        val scoreText = if (initialScoreIsValid) "$hs  –  $aw" else if (state == MatchState.UPCOMING) "VS" else "—"
        scoreTv = Ui.label(this, scoreText, if (phoneLayout) 40f else 64f, ut.text, true)
        scoreTv.gravity = Gravity.CENTER
        scoreTv.textAlignment = View.TEXT_ALIGNMENT_CENTER
        scoreTv.textDirection = View.TEXT_DIRECTION_LTR
        scoreTv.isSingleLine = true
        mid.addView(scoreTv)
        lastKnownScore = scoreText
        outcomeTv = Ui.label(this, if (initialScoreIsValid) outcomeLine(home, away, hs, aw, state) else "", if (phoneLayout) 12f else 22f, ut.text, true)
        outcomeTv.gravity = Gravity.CENTER
        outcomeTv.textAlignment = View.TEXT_ALIGNMENT_CENTER
        outcomeTv.visibility = if (outcomeTv.text.isNullOrBlank()) View.GONE else View.VISIBLE
        mid.addView(outcomeTv)
        if (startMs > 0) {
            mid.addView(Ui.label(this, DateUtil.formatInstant(startMs, prefs.cityTimezone, "EEEE d MMMM yyyy  •  HH:mm", Locale("ar", "EG")), if (phoneLayout) 10f else 24f, ut.dim))
        }
        val info = league + (if (venue.isNotEmpty()) "  •  🏟 $venue" else "")
        mid.addView(Ui.label(this, info, if (phoneLayout) 10f else 22f, ut.accent, true), Ui.lpm(Ui.WRAP, Ui.WRAP, 0f, 0, px(3f), 0, 0))
        if (phoneLayout) {
            val clubs = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER
            }
            clubs.addView(team(home, homeBadge), Ui.lp(0, Ui.WRAP, 1f))
            clubs.addView(team(away, awayBadge), Ui.lp(0, Ui.WRAP, 1f))
            top.addView(clubs, Ui.lpm(Ui.MATCH, Ui.WRAP, 0f, 0, 0, 0, px(8f)))
            top.addView(mid, Ui.lp(Ui.MATCH, Ui.WRAP))
        } else {
            top.orientation = LinearLayout.HORIZONTAL
            top.addView(team(home, homeBadge), Ui.lp(0, Ui.WRAP, 1f))
            top.addView(mid, Ui.lpm(0, Ui.WRAP, 1.45f, 0, px(3f), 0, px(3f)))
            top.addView(team(away, awayBadge), Ui.lp(0, Ui.WRAP, 1f))
        }
        root.addView(top, Ui.lpm(Ui.MATCH, Ui.WRAP, 0f, 0, px(14f), 0, px(10f)))

        val yallaButton = Ui.label(this, "⚽ فتح نتائج الفريق في يلا شووت داخل التطبيق", if (phoneLayout) 13f else 20f, ut.text, true)
        yallaButton.gravity = Gravity.CENTER
        yallaButton.setPadding(px(16f), px(10f), px(16f), px(10f))
        yallaButton.background = Ui.round(Surfaces.panel(ut), px(14f).toFloat(), px(1f), Surfaces.edge(ut))
        Ui.focusable(yallaButton, ut, 14f, Surfaces.panel(ut))
        yallaButton.isClickable = true
        yallaButton.setOnClickListener {
            if (source == "ysscores" && externalUrl.isNotBlank()) {
                startActivity(Intent(this, YallaScoresActivity::class.java).putExtra(YallaScoresActivity.EXTRA_URL, externalUrl))
            } else {
                val haystack = (home + " " + away).lowercase(Locale.ROOT)
                val team = when {
                    haystack.contains("ahly") || haystack.contains("اهلي") || haystack.contains("الأهلي") -> "ahly"
                    haystack.contains("real madrid") || haystack.contains("ريال") -> "realmadrid"
                    haystack.contains("manchester united") || haystack.contains("مانشستر") -> "manutd"
                    else -> ""
                }
                startActivity(if (team.isBlank()) Intent(this, YallaScoresActivity::class.java) else YallaScoresActivity.teamIntent(this, team))
            }
        }
        root.addView(yallaButton, Ui.lpm(Ui.MATCH, Ui.WRAP, 0f, 0, 0, 0, px(8f)))

        // Port the module's "watch live" action through the app's own IPTV player.
        // No fabricated provider URLs or undocumented external-app intents are used.
        val watchIptv = Ui.button(this, ut, "📺 فتح قوائم IPTV لاختيار القناة الرياضية", if (phoneLayout) 14f else 22f) {
            startActivity(Intent(this, IptvProActivity::class.java)
                .putExtra(IptvProActivity.EXTRA_OPEN_CHOOSER, true)
                .putExtra(IptvProActivity.EXTRA_MATCH_LABEL, "$home × $away"))
        }
        watchIptv.isFocusable = true
        watchIptv.isClickable = true
        root.addView(watchIptv, Ui.lpm(Ui.MATCH, Ui.WRAP, 0f, 0, 0, 0, px(10f)))

        status = Ui.label(this, "جاري تحميل التشكيلة...", if (phoneLayout) 14f else 24f, ut.dim)
        root.addView(status, Ui.lpm(Ui.WRAP, Ui.WRAP, 0f, 0, 0, 0, px(8f)))

        val sv = ScrollView(this)
        sv.isVerticalScrollBarEnabled = false
        sv.clipToPadding = false
        sv.isFocusable = true
        body = LinearLayout(this)
        body.orientation = LinearLayout.VERTICAL
        val column = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        // معلومات اللقاء (مثل مرجع المستخدم): تُعرض فقط القيم التي يعيدها المصدر؛ والباقي «غير متاح من المصدر».
        run {
            val tz = prefs.cityTimezone
            val timeTxt = if (startMs > 0) DateUtil.formatInstant(startMs, tz, "HH:mm", Locale("ar", "EG")) else ""
            val dateTxt = if (startMs > 0) DateUtil.formatInstant(startMs, tz, "EEEE d MMMM yyyy", Locale("ar", "EG")) else ""
            val rows = listOf(
                "البطولة" to league, "الجولة" to "", "ملعب المباراة" to venue, "القناة الناقلة" to "",
                "المعلق" to "", "حكم المباراة" to "", "الحضور" to "",
                "مدرب صاحب الأرض" to "", "مدرب الضيف" to "",
                "وقت المباراة" to timeTxt, "تاريخ المباراة" to dateTxt
            )
            column.addView(Ui.label(this, "معلومات اللقاء", if (phoneLayout) 16f else 30f, ut.dim, true), Ui.lpm(Ui.WRAP, Ui.WRAP, 0f, 0, px(4f), 0, px(8f)))
            infoValueViews.clear()
            for ((k, v) in rows) {
                val row = LinearLayout(this).apply {
                    orientation = LinearLayout.HORIZONTAL
                    layoutDirection = View.LAYOUT_DIRECTION_RTL
                    gravity = Gravity.CENTER_VERTICAL
                    setPadding(px(22f), px(12f), px(22f), px(12f))
                    background = Ui.round(Ui.over(ut.dock, ut.bgBottom), px(16f).toFloat(), px(1f), ut.dockBorder)
                }
                row.addView(Ui.label(this, k, if (phoneLayout) 14f else 26f, ut.dim, true), Ui.lp(0, Ui.WRAP, 1f))
                val has = v.isNotBlank()
                val valueTv = Ui.label(this, if (has) v else "غير متاح من المصدر", if (phoneLayout) 14f else 26f, if (has) ut.text else ut.dim, has)
                infoValueViews[k] = valueTv
                row.addView(valueTv, Ui.lp(0, Ui.WRAP, 1.4f))
                column.addView(row, Ui.lpm(Ui.MATCH, Ui.WRAP, 0f, 0, 0, 0, px(8f)))
            }
        }
        column.addView(body, Ui.lp(Ui.MATCH, Ui.WRAP))
        sv.addView(column)
        root.addView(sv, Ui.lp(Ui.MATCH, 0, 1f))
        show(root)
        sv.post { sv.requestFocus() }

        if (id.isEmpty()) {
            status.text = "لا توجد تفاصيل لهذه المباراة"
            return
        }
        if (source == "ysscores" && externalUrl.isNotBlank()) loadYallaInfo(externalUrl)
        detailId = id
        detailSource = source
        detailHome = home
        detailAway = away
        detailStartMs = startMs
        val isTsdb = source == "thesportsdb" && !id.contains("|")
        val detailTimeout = Runnable {
            if (!isFinishing && !isDestroyed && !refreshing && status.text?.toString()?.contains("جاري تحميل", true) == true) {
                status.text = "انتهى وقت الانتظار. النتيجة الأساسية ظاهرة بالأعلى؛ اضغط رجوع ثم افتح التفاصيل لاحقاً لإعادة المحاولة."
            }
        }
        handler.postDelayed(detailTimeout, 10_000L)
        val queued = Net.submitBg {
          try {
            val def = MatchRepo.defForNames(prefs, home, away)
            // Read the same fixture from SofaScore even when the main score came from
            // TheSportsDB/YSscores/ESPN. This is the independent result + event fallback.
            // A final score is always independently verified when possible; the
            // optional multi-source setting controls live cross-checks, not final-score safety.
            // Detail view always attempts an independent lookup for the same followed club.
            // The multi-source preference controls background score polling, not on-demand
            // verification and lineup retrieval when the user opens a fixture.
            val sofaMatch = if (def != null && startMs > 0L) {
                runCatching {
                    SofaScoreProvider.fetchTeam(prefs, def).asSequence()
                        .filter { MatchScoreReconciler.isSameFixtureCandidate(home, away, startMs, it.home, it.away, it.startMs) }
                        .minByOrNull { kotlin.math.abs(it.startMs - startMs) }
                }.getOrNull()
            } else null

            val sofaEventId = when {
                source == "sofascore" -> id
                sofaMatch != null -> sofaMatch.id
                else -> ""
            }
            val sofaEventInfo = if (sofaEventId.isNotBlank() && source != "ysscores") {
                try { SofaScoreProvider.eventInfo(sofaEventId, home, away, startMs) } catch (_: Exception) { null }
            } else null
            var tsdbId = when {
                isTsdb -> id
                source != "sofascore" && startMs > 0L -> try {
                    MatchRepo.resolveEventId(prefs, home, away, startMs, hs, aw)
                } catch (_: Exception) { "" }
                else -> ""
            }

            var chosenHomeScore = hs.takeIf { initialScoreIsValid }
            var chosenAwayScore = aw.takeIf { initialScoreIsValid }
            var chosenState = state
            var correctedFromSofa = false
            if (sofaMatch != null) {
                val sofaState = sofaMatch.state(System.currentTimeMillis())
                if (sofaState == MatchState.FINISHED && MatchScoreReconciler.shouldUseFinishedSecondary(
                        chosenHomeScore, chosenAwayScore, state == MatchState.FINISHED,
                        sofaMatch.homeScore, sofaMatch.awayScore, true
                    )) {
                    chosenHomeScore = sofaMatch.homeScore
                    chosenAwayScore = sofaMatch.awayScore
                    chosenState = MatchState.FINISHED
                    correctedFromSofa = true
                } else if (sofaState == MatchState.LIVE && state != MatchState.FINISHED &&
                    state != MatchState.CANCELED && state != MatchState.POSTPONED && state != MatchState.SUSPENDED &&
                    MatchScoreReconciler.shouldAcceptLiveScore(
                        chosenHomeScore, chosenAwayScore, sofaMatch.homeScore, sofaMatch.awayScore
                    )) {
                    chosenHomeScore = sofaMatch.homeScore
                    chosenAwayScore = sofaMatch.awayScore
                    chosenState = MatchState.LIVE
                }
            }
            // A score without a verified live/final state is not a result. Keep the pair atomic.
            if (chosenState != MatchState.LIVE && chosenState != MatchState.FINISHED) {
                chosenHomeScore = null
                chosenAwayScore = null
            }

            val sofaLineupInfo = if (sofaEventId.isNotBlank()) {
                try { SofaScoreProvider.lineups(sofaEventId) } catch (_: Exception) { null }
            } else null
            var lineup: List<LineupPlayer> = sofaLineupInfo?.players.orEmpty()
            var lineupLabel = if (sofaLineupInfo != null) "SofaScore" else "TheSportsDB"
            // TheSportsDB is a fallback source, but does not communicate official/confirmed status.
            if (lineup.isEmpty() && tsdbId.isNotBlank()) {
                lineup = try { MatchRepo.lineup(tsdbId) } catch (_: Exception) { emptyList() }
                if (lineup.isNotEmpty()) lineupLabel = "TheSportsDB (حالة الاعتماد غير موضحة)"
            }

            // Prefer the strict same-fixture SofaScore incident feed, then use the
            // original provider as a fallback. Never merge feeds from different matches.
            val timelineCandidates = ArrayList<Pair<String, List<TimelineEvent>>>()
            if (sofaEventId.isNotBlank()) {
                val events = try { SofaScoreProvider.incidents(sofaEventId) } catch (_: Exception) { emptyList() }
                if (events.isNotEmpty()) timelineCandidates.add("SofaScore" to events)
            }
            if (source == "espn") {
                val events = try { EspnSoccerProvider.timeline(id) } catch (_: Exception) { emptyList() }
                if (events.isNotEmpty()) timelineCandidates.add("ESPN Soccer" to events)
            }
            if (tsdbId.isNotBlank()) {
                val events = try { MatchRepo.timeline(tsdbId) } catch (_: Exception) { emptyList() }
                if (events.isNotEmpty()) timelineCandidates.add("TheSportsDB" to events)
            }
            val validTimeline = timelineCandidates.firstOrNull { (_, events) ->
                TimelineIntegrity.isConsistent(
                    events.map { TimelineIntegrity.GoalIncident(it.kind, it.home) },
                    chosenHomeScore, chosenAwayScore
                )
            }
            val timeline = validTimeline?.second.orEmpty()
            val rejectedTimeline = timelineCandidates.isNotEmpty() && validTimeline == null
            val selectedTimelineSource = validTimeline?.first.orEmpty()
            val selectedEventId = when {
                selectedTimelineSource == "SofaScore" -> sofaEventId
                source == "sofascore" -> sofaEventId
                sofaMatch != null -> sofaEventId
                selectedTimelineSource == "ESPN Soccer" -> id
                selectedTimelineSource == "TheSportsDB" -> tsdbId
                source == "espn" -> id
                else -> tsdbId
            }

            Net.ui {
                if (isFinishing || isDestroyed) return@ui
                try {
                handler.removeCallbacks(detailTimeout)
                applySofaEventInfo(sofaEventInfo)
                val chosenHasScore = (chosenState == MatchState.LIVE || chosenState == MatchState.FINISHED) &&
                    chosenHomeScore in 0..20 && chosenAwayScore in 0..20
                detailHomeScore = if (chosenHasScore) chosenHomeScore else null
                detailAwayScore = if (chosenHasScore) chosenAwayScore else null
                val score = if (chosenHasScore) "$chosenHomeScore  –  $chosenAwayScore" else if (chosenState == MatchState.UPCOMING) "VS" else "—"
                if (score != lastKnownScore) {
                    lastKnownScore = score
                    scoreTv.text = score
                    scoreTv.textDirection = View.TEXT_DIRECTION_LTR
                    scoreTv.textAlignment = View.TEXT_ALIGNMENT_CENTER
                }
                outcomeTv.text = if (chosenHasScore) outcomeLine(home, away, chosenHomeScore, chosenAwayScore, chosenState) else ""
                outcomeTv.visibility = if (outcomeTv.text.isNullOrBlank()) View.GONE else View.VISIBLE
                run {
                    val color = Contrast.ensure(MatchCards.statePalette(MatchCardPalettes.get(prefs.matchCardPalette), chosenState), ut.bgTop)
                    pillTv.text = "● " + chosenState.labelAr + (if (chosenState == MatchState.LIVE && sofaMatch?.progress.orEmpty().isNotBlank()) "  " + sofaMatch?.progress else "")
                    pillTv.setTextColor(color)
                }
                resolvedEventId = selectedEventId
                usedSofaLive = sofaEventId.isNotBlank() && selectedEventId == sofaEventId
                timelineWarning = when {
                    correctedFromSofa -> "تمت مراجعة النتيجة واعتماد نتيجة SofaScore للمباراة المطابقة: $chosenHomeScore – $chosenAwayScore."
                    rejectedTimeline -> "تم إخفاء الأحداث لأن أهداف المصدر لا تساوي النتيجة النهائية؛ لن نعرض أحداثًا ناقصة أو تخص مباراة أخرى."
                    else -> ""
                }
                if (lineup.isEmpty() && timeline.isEmpty()) {
                    val base = (if (source == "ysscores") "المصدر: YSscores • " else "المصدر: " + MatchProviders.label(source) + " • ") +
                        MatchRepo.status(prefs).describeAr(System.currentTimeMillis())
                    status.text = base + "\n" + (timelineWarning.ifBlank { "لم تتوفر أحداث إضافية مكتملة وموثقة لهذه المباراة. الحالة والنتيجة أعلاه من المصدر المذكور فقط." })
                } else {
                    storedLineup = lineup
                    storedLineupInfo = sofaLineupInfo
                    storedLineupSource = lineupLabel
                    storedTimeline = timeline
                    storedSource = when {
                        selectedTimelineSource == "SofaScore" -> "SofaScore (تطابق الفريقين والموعد)"
                        selectedTimelineSource == "ESPN Soccer" -> "ESPN Soccer"
                        selectedTimelineSource == "TheSportsDB" -> "TheSportsDB (تطابق الفريقين والموعد)"
                        source == "espn" -> "ESPN Soccer"
                        source == "sofascore" -> "SofaScore"
                        else -> MatchProviders.label(source)
                    }
                    shownSig = sigOf(timeline)
                    render(home, away, lineup, timeline, storedSource, sofaLineupInfo, lineupLabel)
                }
                } catch (_: Throwable) {
                    status.text = "تعذر عرض تفاصيل هذه المباراة الآن. جرّب لاحقاً."
                }
            }
          } catch (_: Throwable) {
            Net.ui { if (!isFinishing && !isDestroyed) { handler.removeCallbacks(detailTimeout); status.text = "تعذر تحميل التفاصيل الآن. تأكد من الإنترنت وجرّب لاحقاً." } }
          }
        }
        if (!queued) {
            handler.removeCallbacks(detailTimeout)
            status.text = "التطبيق مشغول الآن، وأعد فتح التفاصيل لاحقاً إذا لم تظهر الأحداث."
        }
        if (state == MatchState.LIVE || (startMs > 0L && nowMs in (startMs - 5 * 60_000L)..(startMs + 170 * 60_000L) &&
                state != MatchState.FINISHED && state != MatchState.CANCELED && state != MatchState.POSTPONED && state != MatchState.SUSPENDED)) {
            liveRefreshing = true
            scheduleLiveRefresh()
        }
        return
    }

    private fun findSofaFixture(home: String, away: String, startMs: Long): Match? {
        if (startMs <= 0L) return null
        val def = MatchRepo.defForNames(prefs, home, away) ?: return null
        return SofaScoreProvider.fetchTeam(prefs, def).asSequence()
            .filter { MatchScoreReconciler.isSameFixtureCandidate(home, away, startMs, it.home, it.away, it.startMs) }
            .minByOrNull { kotlin.math.abs(it.startMs - startMs) }
    }

    private fun scheduleLiveRefresh() {
        handler.removeCallbacks(liveTick)
        if (liveRefreshing) handler.postDelayed(liveTick, 15_000L)
    }

    private fun sigOf(timeline: List<TimelineEvent>): String =
        timeline.size.toString() + "|" + timeline.joinToString(";") { it.minute + "," + it.kind + "," + it.player }

    private val liveTick = object : Runnable {
        override fun run() {
            if (refreshing) { scheduleLiveRefresh(); return }
            refreshing = true
            val id = detailId
            val home = detailHome
            val away = detailAway
            val eventId = resolvedEventId
            val queued = Net.submitBg {
                var fresh: Match? = null
                var timeline: List<TimelineEvent> = emptyList()
                try {
                    fresh = when (com.uobidah.launcher.core.MatchRefreshPlan.forDetailSource(detailSource, usedSofaLive)) {
                        com.uobidah.launcher.core.MatchRefreshFeed.SOFASCORE ->
                            try { findSofaFixture(home, away, detailStartMs) } catch (_: Exception) { null }
                        com.uobidah.launcher.core.MatchRefreshFeed.ESPN -> try {
                            val def = MatchRepo.defForNames(prefs, home, away)
                            def?.let { EspnSoccerProvider.fetchTeam(it, prefs.cityTimezone) }
                                ?.filter { MatchScoreReconciler.isSameFixtureCandidate(home, away, detailStartMs, it.home, it.away, it.startMs) }
                                ?.minByOrNull { kotlin.math.abs(it.startMs - detailStartMs) }
                        } catch (_: Exception) { null }
                        com.uobidah.launcher.core.MatchRefreshFeed.YSSCORES -> try {
                            val def = MatchRepo.defForNames(prefs, home, away)
                            def?.let { YallaScoresProvider.fetchTeam(it, prefs.cityTimezone) }
                                ?.filter { MatchScoreReconciler.isSameFixtureCandidate(home, away, detailStartMs, it.home, it.away, it.startMs) }
                                ?.minByOrNull { kotlin.math.abs(it.startMs - detailStartMs) }
                        } catch (_: Exception) { null }
                        com.uobidah.launcher.core.MatchRefreshFeed.MATCH_REPOSITORY ->
                            try { MatchRepo.findFresh(prefs, id, home, away) } catch (_: Exception) { null }
                    }
                    if (eventId.isNotEmpty()) {
                        try {
                            timeline = when {
                                usedSofaLive -> SofaScoreProvider.incidents(eventId)
                                detailSource == "espn" -> EspnSoccerProvider.timeline(eventId)
                                else -> MatchRepo.timeline(eventId)
                            }
                        } catch (_: Exception) { }
                    }
                } catch (_: Throwable) { }
                val freshFinal = fresh
                val timelineFinal = timeline
                Net.ui {
                    refreshing = false
                    if (isFinishing || isDestroyed) return@ui
                    try {
                        if (freshFinal != null) {
                            val h = freshFinal.homeScore
                            val a = freshFinal.awayScore
                            val st = freshFinal.state(System.currentTimeMillis())
                            if (freshFinal.hasDisplayableScore(System.currentTimeMillis())) {
                                detailHomeScore = h
                                detailAwayScore = a
                                val txt = "$h  –  $a"
                                if (txt != lastKnownScore) {
                                    lastKnownScore = txt
                                    scoreTv.text = txt
                                    scoreTv.textDirection = View.TEXT_DIRECTION_LTR
                                }
                                outcomeTv.text = outcomeLine(home, away, h, a, st)
                                outcomeTv.visibility = if (outcomeTv.text.isNullOrBlank()) View.GONE else View.VISIBLE
                            } else {
                                detailHomeScore = null
                                detailAwayScore = null
                                val placeholder = if (st == MatchState.UPCOMING) "VS" else "—"
                                if (placeholder != lastKnownScore) {
                                    lastKnownScore = placeholder
                                    scoreTv.text = placeholder
                                }
                                outcomeTv.text = ""
                                outcomeTv.visibility = View.GONE
                            }
                            val color = Contrast.ensure(MatchCards.statePalette(MatchCardPalettes.get(prefs.matchCardPalette), st), ut.bgTop)
                            pillTv.text = "● " + st.labelAr + (if (st == MatchState.LIVE && freshFinal.progress.isNotBlank()) "  " + freshFinal.progress else "")
                            pillTv.setTextColor(color)
                            if (st == MatchState.FINISHED || st == MatchState.CANCELED || st == MatchState.POSTPONED || st == MatchState.SUSPENDED) liveRefreshing = false
                        }
                        // لا نعيد بناء الشاشة إلا إذا تغيّرت الأحداث فعلاً (كان إعادة البناء كل 15 ثانية سبب التهنيج وكان يمحو التشكيلة)
                        if (timelineFinal.isNotEmpty()) {
                            val safeTimeline = TimelineIntegrity.isConsistent(
                                timelineFinal.map { TimelineIntegrity.GoalIncident(it.kind, it.home) },
                                detailHomeScore, detailAwayScore
                            )
                            if (safeTimeline) {
                                val sig = sigOf(timelineFinal)
                                if (sig != shownSig) {
                                    shownSig = sig
                                    storedTimeline = timelineFinal
                                    timelineWarning = ""
                                    render(home, away, storedLineup, timelineFinal, storedSource, storedLineupInfo, storedLineupSource)
                                }
                            } else {
                                timelineWarning = "تم إخفاء سجل الأحداث بعد فشل مطابقته مع النتيجة الحالية."
                                storedTimeline = emptyList()
                                body.removeAllViews()
                                if (storedLineup.isNotEmpty()) render(home, away, storedLineup, emptyList(), storedSource, storedLineupInfo, storedLineupSource)
                                status.text = timelineWarning
                            }
                        }
                    } catch (_: Throwable) { }
                    scheduleLiveRefresh()
                }
            }
            if (!queued) { refreshing = false; scheduleLiveRefresh() }
        }
    }

    override fun onDestroy() {
        liveRefreshing = false
        handler.removeCallbacks(liveTick)
        super.onDestroy()
    }

    override fun onPause() {
        handler.removeCallbacks(liveTick)
        super.onPause()
    }

    override fun onResume() {
        super.onResume()
        if (liveRefreshing) scheduleLiveRefresh()
    }

    /** جملة تلخّص ما حدث دون اختلاق: تعتمد على النتيجة وحالة المباراة فقط. */
    private fun outcomeLine(home: String, away: String, h: Int?, a: Int?, st: MatchState): String {
        if (h == null || a == null) return ""
        return when (st) {
            MatchState.FINISHED -> when {
                h > a -> "انتهت بفوز $home ($h - $a)"
                a > h -> "انتهت بفوز $away ($a - $h)"
                else -> "انتهت بالتعادل $h - $a"
            }
            MatchState.LIVE -> when {
                h > a -> "$home متقدم $h - $a"
                a > h -> "$away متقدم $a - $h"
                else -> "تعادل مؤقت $h - $a"
            }
            else -> ""
        }
    }

    private fun team(name: String, badge: String): View {
        val c = LinearLayout(this)
        c.orientation = LinearLayout.VERTICAL
        c.gravity = Gravity.CENTER_HORIZONTAL
        val size = if (phoneLayout) 54 else 110
        c.addView(TeamBadgeUi.create(this, name, badge, size, ut.accent), Ui.lp(px(size.toFloat()), px(size.toFloat())))
        val nm = Ui.label(this, name, if (phoneLayout) 13f else 28f, ut.text, true)
        nm.gravity = Gravity.CENTER
        c.addView(nm, Ui.lpm(Ui.WRAP, Ui.WRAP, 0f, 0, px(6f), 0, 0))
        return c
    }

    private fun render(
        home: String,
        away: String,
        lineup: List<LineupPlayer>,
        timeline: List<TimelineEvent>,
        sourceLabel: String = "TheSportsDB",
        lineupInfo: SofaScoreLineupData? = null,
        lineupSource: String = "TheSportsDB"
    ) {
        body.removeAllViews()
        if (lineup.isEmpty() && timeline.isEmpty()) {
            status.text = "التشكيلة وأحداث المباراة التفصيلية غير متاحة من المصدر الحالي لهذه المباراة. لا نعرض بيانات غير موثقة."
            return
        }
        status.text = "مصدر الأحداث: $sourceLabel • مصدر التشكيلة: $lineupSource • " + MatchRepo.status(prefs).describeAr(System.currentTimeMillis()) +
            (if (timelineWarning.isNotBlank()) "\n$timelineWarning" else "")
        val sorted = timeline.sortedBy { TimelineKind.minuteKey(it.minute) }.take(MAX_EVENTS)
        val goals = sorted.filter { TimelineKind.classify(it.kind).isGoal }
        // سطور كثيرة في TextView واحد بدل مئات الـ Views: أخف بكثير على أجهزة التلفاز الضعيفة
        if (timeline.isNotEmpty()) {
            body.addView(Ui.label(this, "⚽ المسجلون", 34f, ut.accent, true), Ui.lpm(Ui.WRAP, Ui.WRAP, 0f, 0, px(6f), 0, px(8f)))
            if (goals.isEmpty()) {
                body.addView(Ui.label(this, "لم يوفّر المصدر أهدافاً لهذه المباراة", 24f, ut.dim))
            } else {
                body.addView(Ui.label(this, goals.joinToString("\n") { eventLine(it, home, away) }, 26f, ut.text, true), Ui.lpm(Ui.WRAP, Ui.WRAP, 0f, 0, px(4f), 0, px(4f)))
            }
        }
        if (lineup.isNotEmpty()) {
            val lineupHeading = when (lineupInfo?.confirmed) {
                true -> "📋 التشكيلة الرسمية المؤكدة"
                false -> "📋 التشكيلة المتوقعة — غير مؤكدة"
                null -> "📋 قائمة اللاعبين — حالة الاعتماد غير موضحة من المصدر"
            }
            body.addView(Ui.label(this, lineupHeading, if (phoneLayout) 19f else 34f, ut.accent, true), Ui.lpm(Ui.WRAP, Ui.WRAP, 0f, 0, px(18f), 0, px(8f)))
            val row = LinearLayout(this)
            row.orientation = LinearLayout.HORIZONTAL
            row.addView(lineupColumn(home, lineupInfo?.homeFormation.orEmpty(), lineup.filter { it.home }), Ui.lpm(0, Ui.WRAP, 1f, 0, 0, px(16f), 0))
            row.addView(lineupColumn(away, lineupInfo?.awayFormation.orEmpty(), lineup.filter { !it.home }), Ui.lp(0, Ui.WRAP, 1f))
            body.addView(row, Ui.lp(Ui.MATCH, Ui.WRAP))
        }
        if (timeline.isNotEmpty()) {
            body.addView(Ui.label(this, "⏱ كل أحداث المباراة (أهداف • بطاقات • تبديلات)", 34f, ut.accent, true), Ui.lpm(Ui.WRAP, Ui.WRAP, 0f, 0, px(18f), 0, px(8f)))
            body.addView(Ui.label(this, sorted.joinToString("\n") { eventLine(it, home, away) }, 25f, ut.text), Ui.lpm(Ui.WRAP, Ui.WRAP, 0f, 0, px(4f), 0, px(4f)))
        }
    }

    private fun eventLine(e: TimelineEvent, home: String, away: String): String {
        val k = TimelineKind.classify(e.kind)
        val label = if (k == TimelineKind.OTHER) e.kind else k.labelAr
        val minute = if (e.minute.isNotEmpty()) e.minute + "'  " else ""
        return minute + k.icon + " " + label + "  " + e.player + "  (" + (if (e.home) home else away) + ")"
    }

    private fun lineupColumn(title: String, formation: String, players: List<LineupPlayer>): View {
        val c = LinearLayout(this)
        c.orientation = LinearLayout.VERTICAL
        Ui.staticCard(c, ut, 26f)
        c.setPadding(px(22f), px(16f), px(22f), px(16f))
        c.addView(Ui.label(this, title, if (phoneLayout) 15f else 28f, ut.text, true))
        if (formation.isNotBlank()) c.addView(Ui.label(this, "الخطة: $formation", if (phoneLayout) 12f else 22f, ut.accent, true), Ui.lpm(Ui.WRAP, Ui.WRAP, 0f, 0, px(3f), 0, px(4f)))
        val starters = players.filter { !it.sub }
        val subs = players.filter { it.sub }
        if (starters.isEmpty() && subs.isEmpty()) {
            c.addView(Ui.label(this, "غير متاحة", 24f, ut.dim), Ui.lpm(Ui.WRAP, Ui.WRAP, 0f, 0, px(8f), 0, 0))
        }
        if (starters.isNotEmpty()) c.addView(Ui.label(this, starters.joinToString("\n") { playerText(it) }, 24f, ut.text), Ui.lpm(Ui.WRAP, Ui.WRAP, 0f, 0, px(5f), 0, 0))
        if (subs.isNotEmpty()) {
            c.addView(Ui.label(this, "البدلاء", 24f, ut.accent, true), Ui.lpm(Ui.WRAP, Ui.WRAP, 0f, 0, px(12f), 0, 0))
            c.addView(Ui.label(this, subs.joinToString("\n") { playerText(it) }, 24f, ut.text), Ui.lpm(Ui.WRAP, Ui.WRAP, 0f, 0, px(5f), 0, 0))
        }
        return c
    }

    private fun playerText(p: LineupPlayer): String {
        val num = if (p.number.isNotEmpty()) p.number + "  " else ""
        val pos = if (p.position.isNotEmpty()) "   (" + p.position + ")" else ""
        val captain = if (p.captain) "  (القائد)" else ""
        return num + p.name + pos + captain
    }

    companion object { private const val MAX_EVENTS = 120 }
}
