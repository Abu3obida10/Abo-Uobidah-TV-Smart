package com.uobidah.launcher

import com.uobidah.launcher.core.MatchStatusNormalizer

import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * طبقة موحّدة لمصادر النتائج. لا تُعامل المشاريع المرجعية كأنها API مستضافة:
 * FootyLive وFlashscore وArab Football تحتاج خدمة منشورة/محلية يحدد عنوانها المستخدم.
 */
object MatchProviders {
    private val feedCache = HashMap<String, Pair<Long, List<Match>>>()
    private val feedCacheLock = Any()

    private val labels = linkedMapOf(
        "ysscores" to "YSscores / يلا شووت (مباريات الأندية المفضلة)",
        "sofascore" to "SofaScore (واجهة عامة)",
        "livescorefootball" to "LiveScoreFootball",
        "footylive" to "FootyLive",
        "arabfootball" to "Arab Football Unified API",
        "flashscore" to "Flashscore Scraper",
        "livescoreswebapp" to "LiveScoresWebApp / RapidAPI",
        "thesportsdb" to "TheSportsDB (احتياطي قديم)",
        "espn" to "ESPN Soccer (fallback)"
    )

    fun label(id: String): String = labels[id] ?: "مصدر غير معروف"

    fun ordered(prefs: Prefs): List<String> {
        val selected = prefs.matchProviderId.takeIf { labels.containsKey(it) } ?: "livescorefootball"
        val order = listOf(selected, "ysscores", "sofascore", "espn", "footylive", "arabfootball", "livescorefootball", "livescoreswebapp", "flashscore", "thesportsdb").distinct()
        return order.filter { id ->
            when (id) {
                "ysscores", "livescorefootball", "thesportsdb", "espn" -> true
                "sofascore" -> prefs.matchMultiSource
                "arabfootball" -> prefs.arabFootballBaseUrl.isNotBlank()
                "footylive" -> prefs.footyLiveBaseUrl.isNotBlank()
                "flashscore" -> prefs.flashscoreBaseUrl.isNotBlank()
                "livescoreswebapp" -> prefs.liveScoresBaseUrl.isNotBlank() && prefs.liveScoresRapidApiKey.isNotBlank()
                else -> false
            }
        }.let { if (prefs.matchAutoFailover) it else it.take(1) }
    }

    fun fetch(prefs: Prefs, provider: String): List<Match> {
        val cacheKey = provider + "|" + when (provider) {
            "footylive" -> prefs.footyLiveBaseUrl
            "arabfootball" -> prefs.arabFootballBaseUrl
            "flashscore" -> prefs.flashscoreBaseUrl
            "livescoreswebapp" -> prefs.liveScoresBaseUrl
            else -> "public"
        }
        val now = System.currentTimeMillis()
        synchronized(feedCacheLock) {
            val hit = feedCache[cacheKey]
            if (hit != null && now - hit.first in 0..25_000L) return hit.second
        }
        val fresh = when (provider) {
            "livescorefootball" -> fetchLiveScoreFootball()
            "footylive" -> fetchFootyLive(prefs.footyLiveBaseUrl)
            "arabfootball" -> fetchArabFootball(prefs.arabFootballBaseUrl)
            "flashscore" -> fetchFlashscore(prefs.flashscoreBaseUrl)
            "livescoreswebapp" -> fetchRapidApi(prefs.liveScoresBaseUrl, prefs.liveScoresRapidApiKey)
            else -> throw IllegalArgumentException("مصدر غير مدعوم")
        }.distinctBy { it.id }
        synchronized(feedCacheLock) { feedCache[cacheKey] = now to fresh }
        return fresh
    }

    /** الخدمة الرسمية الموثقة للمشروع تعرض England/Spain كبداية، لذلك لا ندّعي تغطية مصر أو كل العرب. */
    private fun fetchLiveScoreFootball(): List<Match> {
        val today = LocalDate.now(ZoneOffset.UTC)
        val from = today.minusDays(7).format(DateTimeFormatter.BASIC_ISO_DATE)
        val to = today.plusDays(7).format(DateTimeFormatter.BASIC_ISO_DATE)
        val out = ArrayList<Match>()
        for (league in listOf("eng.1", "esp.1")) {
            val url = "https://worldcup26.ir/get/soccer/$league/fixtures?status=all&from=$from&to=$to&limit=200"
            out.addAll(parseResponse(NetJson.get(url), "livescorefootball", league))
        }
        if (out.isEmpty()) throw IllegalStateException("واجهة LiveScoreFootball لم تُرجع مباريات ضمن الفترة")
        return out
    }

    private fun fetchFootyLive(base: String): List<Match> {
        val url = normalizeBase(base) + "/api/matches"
        val out = parseResponse(NetJson.get(url), "footylive", "")
        if (out.isEmpty()) throw IllegalStateException("FootyLive لم يُرجع مباريات؛ تأكد من تشغيل نسخة الخادم")
        return out
    }

    private fun fetchArabFootball(base: String): List<Match> {
        // المستودع يشحن خادماً محلياً للقراءة على :8100؛ ليس خدمة عامة مستضافة.
        val today = LocalDate.now(ZoneOffset.UTC)
        val from = today.minusDays(7).format(DateTimeFormatter.ISO_LOCAL_DATE)
        val to = today.plusDays(7).format(DateTimeFormatter.ISO_LOCAL_DATE)
        val url = normalizeBase(base) + "/v1/matches?from=$from&to=$to"
        val out = parseResponse(NetJson.get(url), "arabfootball", "")
        if (out.isEmpty()) throw IllegalStateException("خادم Arab Football لم يُرجع مباريات قابلة للقراءة")
        return out
    }

    private fun fetchFlashscore(base: String): List<Match> {
        val url = normalizeBase(base) + "/matches?sport=football"
        val out = parseResponse(NetJson.get(url), "flashscore", "")
        if (out.isEmpty()) throw IllegalStateException("Flashscore Scraper لم يُرجع مباريات؛ تأكد من تشغيل الخادم على الشبكة")
        return out
    }

    private fun fetchRapidApi(base: String, key: String): List<Match> {
        val url = normalizeBase(base) + "/matches"
        val host = runCatching { java.net.URI.create(normalizeBase(base)).host }.getOrDefault("")
        if (host.isBlank()) throw IllegalArgumentException("عنوان RapidAPI غير صالح")
        val out = parseResponse(NetJson.get(url, mapOf("x-rapidapi-key" to key, "x-rapidapi-host" to host)), "livescoreswebapp", "")
        if (out.isEmpty()) throw IllegalStateException("RapidAPI لم يُرجع مباريات")
        return out
    }

    private fun normalizeBase(raw: String): String {
        val value = raw.trim().trimEnd('/')
        require(value.startsWith("https://", true) || value.startsWith("http://", true)) { "أدخل عنوان خادم يبدأ بـ http:// أو https://" }
        require(!value.contains(" ")) { "عنوان الخادم غير صالح" }
        return value
    }

    private fun parseResponse(json: String, provider: String, leagueHint: String): List<Match> {
        val clean = json.trim()
        require(clean.isNotEmpty()) { "استجابة المصدر فارغة" }
        val root: Any = if (clean.startsWith("[")) JSONArray(clean) else JSONObject(clean)
        val candidates = ArrayList<JSONObject>()
        fun walk(value: Any?, depth: Int) {
            if (depth > 8 || value == null || value == JSONObject.NULL) return
            when (value) {
                is JSONObject -> {
                    if (looksLikeMatch(value)) {
                        candidates.add(value)
                        return
                    }
                    val keys = value.keys()
                    while (keys.hasNext()) {
                        val key = keys.next()
                        val child = value.opt(key)
                        if (child is JSONObject || child is JSONArray) walk(child, depth + 1)
                    }
                }
                is JSONArray -> for (i in 0 until value.length()) walk(value.opt(i), depth + 1)
            }
        }
        walk(root, 0)
        return candidates.mapNotNull { parseMatch(it, provider, leagueHint) }.distinctBy { it.id }
    }

    private fun looksLikeMatch(o: JSONObject): Boolean {
        val hasDateOrId = o.has("id") || o.has("eventId") || o.has("uid") || o.has("date") || o.has("start_time") || o.has("strTimestamp")
        val directTeams = (o.has("home") || o.has("homeTeam") || o.has("home_team") || o.has("home_entity") || o.has("strHomeTeam")) &&
            (o.has("away") || o.has("awayTeam") || o.has("away_team") || o.has("away_entity") || o.has("strAwayTeam"))
        val hasCompetitors = o.optJSONArray("competitions")?.optJSONObject(0)?.optJSONArray("competitors") != null
        return hasDateOrId && (directTeams || hasCompetitors)
    }

    private data class TeamFields(val name: String, val id: String, val badge: String, val score: Int?)

    private fun parseMatch(o: JSONObject, provider: String, leagueHint: String): Match? {
        val firstCompetition = o.optJSONArray("competitions")?.optJSONObject(0)
        val competitors = firstCompetition?.optJSONArray("competitors")
        var home = TeamFields("", "", "", null)
        var away = TeamFields("", "", "", null)
        if (competitors != null) {
            for (i in 0 until competitors.length()) {
                val item = competitors.optJSONObject(i) ?: continue
                val side = item.optString("homeAway", item.optString("side", "")).lowercase(Locale.ROOT)
                val teamObj = item.optJSONObject("team") ?: item
                val t = TeamFields(
                    firstText(teamObj, "displayName", "name", "shortDisplayName", "name_en", "name_ar", "team_name", "strTeam"),
                    firstText(teamObj, "id", "teamId", "source_id", "idTeam"),
                    firstText(teamObj, "logo", "badge", "crest", "image", "strBadge"),
                    intValue(item.opt("score"))
                )
                if (side == "home" || side == "h") home = t else if (side == "away" || side == "a") away = t
            }
        }
        if (home.name.isBlank()) home = teamField(o.opt("home") ?: o.opt("homeTeam") ?: o.opt("home_team") ?: o.opt("home_entity") ?: o.opt("strHomeTeam"), o, true)
        if (away.name.isBlank()) away = teamField(o.opt("away") ?: o.opt("awayTeam") ?: o.opt("away_team") ?: o.opt("away_entity") ?: o.opt("strAwayTeam"), o, false)
        if (home.name.isBlank() || away.name.isBlank()) return null

        val id = firstText(o, "id", "eventId", "event_id", "matchId", "match_id", "uid", "idEvent", "source_id")
        if (id.isBlank()) return null
        val league = firstText(o, "league", "competition", "competition_name", "tournament", "note", "strLeague").ifBlank {
            firstText(firstCompetition ?: JSONObject(), "name", "displayName", "leagueName").ifBlank { leagueHint }
        }
        val statusObj = o.optJSONObject("status") ?: o.optJSONObject("state")
        val rawStatus = if (statusObj != null) {
            firstText(statusObj, "description", "shortDetail", "short_detail", "detail", "state", "name", "type")
        } else firstText(o, "status", "state", "strStatus", "match_status")
        val status = MatchStatusNormalizer.canonicalize(rawStatus, provider)
        val progress = if (statusObj != null) firstText(statusObj, "clock", "displayClock", "detail", "shortDetail", "short_detail")
            else firstText(o, "progress", "minute", "elapsed", "strProgress")
        val start = parseDate(firstText(o, "date", "start_time", "startTime", "kickoff", "kickoff_utc", "utcDate", "start_date", "startDate", "strTimestamp", "dateEvent"))
        val homeScore = home.score ?: firstInt(o, "home_score", "homeScore", "score_home", "intHomeScore")
        val awayScore = away.score ?: firstInt(o, "away_score", "awayScore", "score_away", "intAwayScore")
        val venueObj = o.optJSONObject("venue") ?: o.optJSONObject("gameInfo")
        val venue = if (venueObj != null) firstText(venueObj, "fullName", "name", "venue", "stadium") else firstText(o, "venue", "stadium", "strVenue")
        val leagueSlug = firstText(o, "league_slug", "leagueSlug").ifBlank { leagueHint }
        val safeId = "$provider|${leagueSlug.ifBlank { "all" }}|$id"
        return Match(
            safeId, home.name, away.name, start, league, homeScore, awayScore,
            status, progress, venue, home.badge, away.badge, home.id, away.id,
            false, provider
        )
    }

    private fun teamField(raw: Any?, parent: JSONObject, home: Boolean): TeamFields {
        val team = raw as? JSONObject
        if (team != null) return TeamFields(
            firstText(team, "displayName", "name", "name_ar", "name_en", "shortName", "strTeam"),
            firstText(team, "id", "teamId", "idTeam", "source_id"),
            firstText(team, "logo", "badge", "crest", "image", "strBadge"),
            intValue(team.opt("score"))
        )
        val name = (raw as? String).orEmpty().ifBlank {
            firstText(parent,
                if (home) "home_name" else "away_name",
                if (home) "home_team_name" else "away_team_name",
                if (home) "homeTeamName" else "awayTeamName",
                if (home) "home_name_ar" else "away_name_ar",
                if (home) "home_name_en" else "away_name_en")
        }
        val id = firstText(parent, if (home) "homeTeamId" else "awayTeamId", if (home) "home_entity_id" else "away_entity_id", if (home) "home_id" else "away_id", if (home) "idHomeTeam" else "idAwayTeam")
        val badge = firstText(parent, if (home) "home_logo" else "away_logo", if (home) "homeBadge" else "awayBadge", if (home) "home_crest" else "away_crest", if (home) "strHomeTeamBadge" else "strAwayTeamBadge")
        val score = firstInt(parent, if (home) "home_score" else "away_score", if (home) "homeScore" else "awayScore", if (home) "intHomeScore" else "intAwayScore")
        return TeamFields(name, id, badge, score)
    }

    private fun firstText(o: JSONObject, vararg keys: String): String {
        for (key in keys) {
            val v = o.opt(key)
            val s = when (v) {
                is String -> v
                is Number -> v.toString()
                is JSONObject -> firstText(v, "displayName", "name", "name_ar", "name_en", "shortName", "short_name", "value", "detail")
                else -> ""
            }.trim()
            if (s.isNotBlank() && s != "null") return s
        }
        return ""
    }

    private fun firstInt(o: JSONObject, vararg keys: String): Int? {
        for (key in keys) intValue(o.opt(key))?.let { return it }
        return null
    }

    private fun intValue(v: Any?): Int? {
        val parsed = when (v) {
            is Number -> v.toInt()
            is String -> v.trim().toIntOrNull()
            is JSONObject -> firstInt(v, "value", "score")
            else -> null
        }
        return parsed?.takeIf { it in 0..20 }
    }

    private fun parseDate(raw: String): Long {
        if (raw.isBlank()) return 0L
        raw.trim().toLongOrNull()?.let { numeric ->
            if (numeric > 10_000_000_000L) return numeric
            if (numeric > 1_000_000_000L) return numeric * 1000L
        }
        return try { java.time.Instant.parse(raw).toEpochMilli() } catch (_: Exception) {
            try { java.time.OffsetDateTime.parse(raw).toInstant().toEpochMilli() } catch (_: Exception) {
                try { java.time.LocalDateTime.parse(raw.replace(" ", "T").take(19)).toInstant(ZoneOffset.UTC).toEpochMilli() } catch (_: Exception) {
                    // Date-only values do not contain a kickoff time; never fabricate midnight.
                    try { LocalDate.parse(raw.take(10)); 0L } catch (_: Exception) { 0L }
                }
            }
        }
    }

    fun belongsTo(match: Match, def: TeamDef?): Boolean {
        if (def == null) return false
        val expected = listOf(def.arabic, def.query).map(::normalizeName).filter { it.isNotBlank() }
        val observed = listOf(match.home, match.away).map(::normalizeName)
        return observed.any { found -> expected.any { want -> found == want || (want.length >= 5 && found.contains(want)) || (found.length >= 5 && want.contains(found)) } }
    }

    private fun normalizeName(value: String): String = value.lowercase(Locale.ROOT)
        .replace(Regex("[\\u064B-\\u065F\\u0670]"), "")
        .replace('أ', 'ا').replace('إ', 'ا').replace('آ', 'ا').replace('ى', 'ي')
        .replace("club", "").replace("fc", "").replace("sc", "")
        .replace(Regex("[^\\p{L}\\p{N}]"), "")
}

/** HTTP helper يضيف رؤوس RapidAPI اختيارياً ويحترم حدود حجم الرد. */
private object NetJson {
    fun get(url: String, headers: Map<String, String> = emptyMap()): String =
        String(Net.getBytes(url, 12_000, Net.UA_BROWSER, null, 5 * 1024 * 1024, headers), Charsets.UTF_8)
}
