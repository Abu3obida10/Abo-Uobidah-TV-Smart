package com.uobidah.launcher

import com.uobidah.launcher.core.TimelineKind
import com.uobidah.launcher.core.MatchStatusNormalizer
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

/**
 * Fallback مباشر بلا مفتاح لمباريات كرة القدم وجدول الدوري.
 * يستخدم ESPN Site API للنتائج/التفاصيل وCore API للجدول عند توفره.
 */
object EspnSoccerProvider {
    private data class TimelineCacheEntry(val savedAt: Long, val events: List<TimelineEvent>)
    private val timelineCache = ConcurrentHashMap<String, TimelineCacheEntry>()
    private val timelineLocks = ConcurrentHashMap<String, Any>()

    data class Standing(
        val rank: Int,
        val team: String,
        val teamId: String,
        val played: Int,
        val wins: Int,
        val draws: Int,
        val losses: Int,
        val goalsFor: Int,
        val goalsAgainst: Int,
        val goalDiff: Int,
        val points: Int,
        val note: String = ""
    )

    private fun leagueFor(def: TeamDef): String? = when {
        def.country.equals("England", true) -> "eng.1"
        def.country.equals("Spain", true) -> "esp.1"
        def.country.equals("Egypt", true) -> "egy.1"
        else -> null
    }

    fun leagueForCountry(country: String): String? = when {
        country.equals("England", true) -> "eng.1"
        country.equals("Spain", true) -> "esp.1"
        country.equals("Egypt", true) -> "egy.1"
        else -> null
    }

    fun leagueLabel(league: String): String = when (league) {
        "egy.1" -> "الدوري المصري (تغطية المصدر غير مؤكدة)"
        "eng.1" -> "الدوري الإنجليزي"
        "esp.1" -> "الدوري الإسباني"
        else -> league
    }

    fun fetchTeam(def: TeamDef, timezone: String): List<Match> {
        val league = leagueFor(def) ?: return emptyList()
        val zone = try { ZoneId.of(timezone) } catch (_: Exception) { ZoneId.of("Africa/Cairo") }
        val today = LocalDate.now(zone)
        val from = today.minusDays(35).format(DateTimeFormatter.BASIC_ISO_DATE)
        val to = today.plusDays(21).format(DateTimeFormatter.BASIC_ISO_DATE)
        val url = "https://site.api.espn.com/apis/site/v2/sports/soccer/$league/scoreboard?dates=$from-$to&limit=500"
        val root = JSONObject(Net.getString(url, 15000))
        val events = root.optJSONArray("events") ?: JSONArray()
        val team = normalize(def.query)
        val out = ArrayList<Match>()
        for (i in 0 until events.length()) {
            val e = events.optJSONObject(i) ?: continue
            val c = e.optJSONArray("competitions")?.optJSONObject(0) ?: continue
            val competitors = c.optJSONArray("competitors") ?: continue
            var home: JSONObject? = null
            var away: JSONObject? = null
            for (j in 0 until competitors.length()) {
                val item = competitors.optJSONObject(j) ?: continue
                when (item.optString("homeAway").lowercase(Locale.ROOT)) {
                    "home" -> home = item
                    "away" -> away = item
                }
            }
            if (home == null || away == null) continue
            val homeTeam = home!!.optJSONObject("team") ?: continue
            val awayTeam = away!!.optJSONObject("team") ?: continue
            val homeName = homeTeam.optString("displayName", homeTeam.optString("name"))
            val awayName = awayTeam.optString("displayName", awayTeam.optString("name"))
            val both = normalize(homeName) + " " + normalize(awayName)
            if (!both.contains(team)) continue
            val start = e.optString("date").let { runCatching { Instant.parse(it).toEpochMilli() }.getOrDefault(0L) }
            val statusObj = c.optJSONObject("status") ?: e.optJSONObject("status")
            val statusType = statusObj?.optJSONObject("type")
            val state = statusType?.optString("state", "") ?: ""
            val detail = statusType?.optString("detail", "") ?: ""
            val explicitlyCompleted = statusType?.optBoolean("completed", false) == true
            val homeScore = home!!.optString("score").toIntOrNull()?.takeIf { it in 0..20 }
            val awayScore = away!!.optString("score").toIntOrNull()?.takeIf { it in 0..20 }
            val canonicalStatus = when {
                explicitlyCompleted -> "finished"
                state.isNotBlank() -> MatchStatusNormalizer.canonicalize(state, "espn")
                else -> MatchStatusNormalizer.canonicalize(detail, "espn")
            }
            val venue = c.optJSONObject("venue")?.optString("fullName").orEmpty()
            val links = e.optJSONArray("links")
            val external = links?.let { arr ->
                (0 until arr.length()).firstNotNullOfOrNull { idx -> arr.optJSONObject(idx)?.optString("href").takeIf { !it.isNullOrBlank() } }
            }.orEmpty()
            val homeBadge = homeTeam.optJSONArray("logos")?.optJSONObject(0)?.optString("href").orEmpty()
            val awayBadge = awayTeam.optJSONArray("logos")?.optJSONObject(0)?.optString("href").orEmpty()
            val id = "espn|$league|${e.optString("id")}".trimEnd('|')
            out.add(
                Match(
                    id = id,
                    home = homeName,
                    away = awayName,
                    startMs = start,
                    league = e.optJSONObject("season")?.optString("displayName")?.ifBlank { null } ?: leagueLabel(league),
                    homeScore = homeScore,
                    awayScore = awayScore,
                    status = canonicalStatus,
                    progress = detail,
                    venue = venue,
                    homeBadge = homeBadge,
                    awayBadge = awayBadge,
                    homeTeamId = homeTeam.optString("id"),
                    awayTeamId = awayTeam.optString("id"),
                    fromCache = false,
                    source = "espn",
                    externalUrl = external
                )
            )
        }
        return out.distinctBy { it.id }.sortedBy { it.startMs }
    }

    private val formCache = java.util.concurrent.ConcurrentHashMap<String, Pair<Long, List<com.uobidah.launcher.core.FormResult>>>()

    /**
     * آخر نتائج الفريق (شارة الفورمة R7) من جدول مبارياته في ESPN. نتيجة فارغة = لا شارة (لا أرقام مولّدة).
     * ملاحظة: بنية الاستجابة لم تُتحقق على شبكة حية في هذه البيئة؛ المحلل مغطى باختبار على عينة بنفس الشكل المتوقع.
     */
    fun teamForm(league: String, teamId: String): List<com.uobidah.launcher.core.FormResult> {
        if (teamId.isBlank()) return emptyList()
        val key = "$league|$teamId"
        val now = System.currentTimeMillis()
        formCache[key]?.let { (at, v) -> if (now - at in 0..(30 * 60_000L)) return v }
        val url = "https://site.api.espn.com/apis/site/v2/sports/soccer/$league/teams/$teamId/schedule"
        val result = com.uobidah.launcher.core.TeamForm.parse(Net.getString(url, 15000), teamId)
        if (result.isNotEmpty()) formCache[key] = now to result
        return result
    }

    fun standings(league: String): List<Standing> {
        val url = "https://site.api.espn.com/apis/v2/sports/soccer/$league/standings"
        return com.uobidah.launcher.core.EspnStandingsParser.parse(Net.getString(url, 15000)).map {
            Standing(it.rank, it.team, it.teamId, it.played, it.wins, it.draws, it.losses,
                it.goalsFor, it.goalsAgainst, it.goalDiff, it.points, it.note)
        }
    }

    fun timeline(sourceId: String): List<TimelineEvent> {
        val now = System.currentTimeMillis()
        val cached = timelineCache[sourceId]
        val ttl = if (cached?.events?.isNotEmpty() == true) 25_000L else 8_000L
        if (cached != null && now - cached.savedAt in 0..ttl) return cached.events
        val lock = timelineLocks.computeIfAbsent(sourceId) { Any() }
        synchronized(lock) {
            val fresh = timelineCache[sourceId]
            val freshTtl = if (fresh?.events?.isNotEmpty() == true) 25_000L else 8_000L
            if (fresh != null && System.currentTimeMillis() - fresh.savedAt in 0..freshTtl) return fresh.events
            val result = fetchTimeline(sourceId)
            timelineCache[sourceId] = TimelineCacheEntry(System.currentTimeMillis(), result)
            return result
        }
    }

    private fun fetchTimeline(sourceId: String): List<TimelineEvent> {
        val parts = sourceId.split('|')
        if (parts.size < 3 || parts[0] != "espn") return emptyList()
        val league = parts[1]
        val eventId = parts[2]

        fun parsePlays(arr: JSONArray?): List<TimelineEvent> {
            if (arr == null) return emptyList()
            val out = ArrayList<TimelineEvent>()
            for (i in 0 until arr.length()) {
                val p = arr.optJSONObject(i) ?: continue
                // ESPN summary and Core API use slightly different field names.
                val typeObject = p.optJSONObject("type")
                val type = typeObject?.optString("text")?.ifBlank { null }
                    ?: typeObject?.optString("name")?.ifBlank { null }
                    ?: p.optString("typeText").ifBlank { p.optString("type_text") }
                val text = p.optString("text").ifBlank { p.optString("shortText") }
                val joined = "$type $text"
                val kind = when {
                    type.contains("goal", true) || text.contains("goal", true) || joined.contains("هدف") || p.optBoolean("scoringPlay") -> TimelineKind.GOAL.name
                    type.contains("yellow", true) || text.contains("yellow", true) || joined.contains("بطاقة صفراء") -> TimelineKind.YELLOW.name
                    type.contains("red", true) || text.contains("red", true) || joined.contains("بطاقة حمراء") -> TimelineKind.RED.name
                    type.contains("sub", true) || text.contains("substitution", true) || joined.contains("تبديل") -> TimelineKind.SUBSTITUTION.name
                    else -> "event"
                }
                val clock = p.optJSONObject("clock")?.optString("displayValue")
                    ?.ifBlank { p.optString("clock_display_value") }
                    ?: p.optString("clock_display_value")
                val minute = clock.ifBlank {
                    p.optJSONObject("period")?.optString("displayValue")
                        ?.ifBlank { p.optString("period_display_value") }.orEmpty()
                }
                val athlete = p.optJSONObject("athlete")?.optString("displayName").orEmpty()
                val team = p.optJSONObject("team")
                val side = p.optString("homeAway", team?.optString("homeAway").orEmpty())
                val home = side.equals("home", true) || team?.optString("homeAway").equals("home", true)
                val player = if (athlete.isNotBlank()) athlete else text.ifBlank { type }
                if (player.isNotBlank()) out.add(TimelineEvent(minute, kind, player, home))
            }
            return out
        }

        // The site summary is the lightest endpoint and often returns embedded plays.
        val summary = runCatching {
            JSONObject(Net.getString("https://site.api.espn.com/apis/site/v2/sports/soccer/$league/summary?event=$eventId", 5000))
        }.getOrNull()
        if (summary != null) {
            for (key in listOf("plays", "scoringPlays", "commentary", "keyEvents")) {
                val parsed = parsePlays(summary.optJSONArray(key))
                if (parsed.isNotEmpty()) return parsed
            }
        }

        // Soccer Core API exposes the competition play feed when a summary omits embedded events.
        // It is a single bounded request; unresolved $ref-only collections are deliberately not fanned out.
        val core = runCatching {
            JSONObject(Net.getString(
                "https://sports.core.api.espn.com/v2/sports/soccer/leagues/$league/events/$eventId/competitions/$eventId/plays?limit=100",
                5000
            ))
        }.getOrNull()
        val corePlays = parsePlays(core?.optJSONArray("items") ?: core?.optJSONArray("plays"))
        if (corePlays.isNotEmpty()) return corePlays

        // Last fallback: ESPN CDN game package. It may embed commentary/plays in a nested structure.
        val cdn = runCatching {
            JSONObject(Net.getString("https://cdn.espn.com/core/soccer/game?xhr=1&gameId=$eventId", 5000))
        }.getOrNull() ?: return emptyList()
        fun findPlayArrays(node: Any?): List<TimelineEvent> {
            when (node) {
                is JSONObject -> {
                    val keys = node.keys()
                    while (keys.hasNext()) {
                        val key = keys.next()
                        val value = node.opt(key)
                        if (key.equals("plays", true) || key.equals("scoringPlays", true) || key.equals("keyEvents", true) || key.equals("playByPlay", true)) {
                            val parsed = parsePlays(value as? JSONArray)
                            if (parsed.isNotEmpty()) return parsed
                        }
                        val nested = findPlayArrays(value)
                        if (nested.isNotEmpty()) return nested
                    }
                }
                is JSONArray -> for (i in 0 until node.length()) {
                    val nested = findPlayArrays(node.opt(i))
                    if (nested.isNotEmpty()) return nested
                }
            }
            return emptyList()
        }
        return findPlayArrays(cdn)
    }

    private fun normalize(value: String): String = value.lowercase(Locale.ROOT)
        .replace('أ', 'ا').replace('إ', 'ا').replace('آ', 'ا')
        .filter { it.isLetterOrDigit() }
}
