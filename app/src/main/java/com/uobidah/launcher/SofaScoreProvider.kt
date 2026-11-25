package com.uobidah.launcher

import org.json.JSONObject
import java.io.IOException
import java.net.URLEncoder
import java.util.Locale
import java.time.LocalDate

/**
 * مصدر ثانٍ لنتائج الأندية المفضلة عبر الواجهة العامة لـ SofaScore (غير رسمية وبلا مفتاح).
 * يُستخدم كمصدر بديل وللمقارنة مع YSscores وقت المباراة، ويوفّر أحداث المباراة (المسجلون والبطاقات والتبديلات).
 * أي فشل أو تغيير في شكل الاستجابة يرمي استثناءً فتُهمل النتيجة ولا تُعرض بيانات مختلقة.
 */
object SofaScoreProvider {
    private const val BASE = "https://api.sofascore.com/api/v1"
    private const val COLD_MS = 120_000L
    private const val HOT_MS = 12_000L

    private val cache = HashMap<String, Pair<Long, List<Match>>>()
    private val lineupCache = HashMap<String, Pair<Long, SofaScoreLineupData>>()
    private val eventInfoCache = HashMap<String, Pair<Long, SofaScoreEventInfo>>()
    private val lineupFailedAt = HashMap<String, Long>()
    private val eventInfoFailedAt = HashMap<String, Long>()
    private val failedAt = HashMap<String, Long>()
    private val lock = Any()

    private fun get(url: String): String = String(
        Net.getBytes(url, 6_000, Net.UA_BROWSER, "https://www.sofascore.com/", 3 * 1024 * 1024, mapOf("Accept" to "application/json")),
        Charsets.UTF_8
    )

    /**
     * جدول كرة القدم العام لكل البطولات في تاريخ محدد؛ لا يعتمد على قائمة الأندية المفضلة.
     * تُحفظ النتيجة في الذاكرة لفترة قصيرة لتقليل الطلبات المتكررة على أجهزة التلفزيون.
     */
    fun fetchAllDay(date: LocalDate): List<Match> {
        val key = "all-day|$date"
        val now = System.currentTimeMillis()
        synchronized(lock) {
            val cached = cache[key]
            if (cached != null && now - cached.first in 0..45_000L) return cached.second
            val failed = failedAt[key] ?: 0L
            if (failed > 0L && now - failed in 0..20_000L) throw IOException("تعذر الاتصال بجدول المباريات مؤخرًا")
        }
        try {
            val root = JSONObject(get("$BASE/sport/football/scheduled-events/$date"))
            val events = root.optJSONArray("events") ?: throw IOException("استجابة جدول المباريات غير مكتملة")
            val result = ArrayList<Match>(events.length())
            for (i in 0 until events.length()) {
                val event = events.optJSONObject(i) ?: continue
                parseEvent(event, now)?.let { result.add(it) }
            }
            val unique = result.distinctBy { it.id }.sortedBy { it.startMs }
            if (unique.isEmpty()) throw IOException("لم يعرض المصدر مباريات قابلة للقراءة لهذا التاريخ")
            synchronized(lock) { cache[key] = now to unique; failedAt.remove(key) }
            return unique
        } catch (e: Exception) {
            synchronized(lock) { failedAt[key] = System.currentTimeMillis() }
            throw e
        }
    }

    /** معرّف الفريق في SofaScore: يُبحث عنه بالاسم الإنجليزي (والدولة إن وُجدت) ثم يُحفظ. */
    private fun teamId(prefs: Prefs, def: TeamDef): String {
        val key = "sofascore_team_" + def.id
        val saved = prefs.sp.getString(key, null)
        if (!saved.isNullOrBlank()) return saved
        val q = URLEncoder.encode(def.query, "UTF-8")
        val arr = JSONObject(get("$BASE/search/all?q=$q")).optJSONArray("results")
            ?: throw IOException("لم يُرجع SofaScore نتائج بحث")
        val want = def.query.trim().lowercase(Locale.ROOT)
        for (i in 0 until arr.length()) {
            val r = arr.optJSONObject(i) ?: continue
            if (r.optString("type") != "team") continue
            val e = r.optJSONObject("entity") ?: continue
            if (!e.optJSONObject("sport")?.optString("slug").equals("football", true)) continue
            if (e.optString("name").trim().lowercase(Locale.ROOT) != want) continue
            if (def.country.isNotBlank()) {
                val c = e.optJSONObject("country")?.optString("name").orEmpty()
                if (!c.equals(def.country, true)) continue
            }
            val id = e.optLong("id", 0L)
            if (id > 0L) {
                prefs.sp.edit().putString(key, id.toString()).apply()
                return id.toString()
            }
        }
        throw IOException("لم أجد النادي في SofaScore")
    }

    fun fetchTeam(prefs: Prefs, def: TeamDef): List<Match> {
        val now = System.currentTimeMillis()
        val ck = def.id
        synchronized(lock) {
            val c = cache[ck]
            val ttl = if (c != null && c.second.any { it.inPlayWindow(now) }) HOT_MS else COLD_MS
            if (c != null && now - c.first in 0..ttl) return c.second
            val f = failedAt[ck] ?: 0L
            if (f > 0L && now - f in 0..30_000L) throw IOException("تعذر الاتصال بـ SofaScore مؤخراً")
        }
        try {
            val tid = teamId(prefs, def)
            val out = LinkedHashMap<String, Match>()
            for (path in listOf("last/0", "next/0")) {
                val arr = JSONObject(get("$BASE/team/$tid/events/$path")).optJSONArray("events") ?: continue
                for (i in 0 until arr.length()) {
                    val m = parseEvent(arr.optJSONObject(i) ?: continue, now) ?: continue
                    out[m.id] = m
                }
            }
            if (out.isEmpty()) throw IOException("لم يُرجع SofaScore مباريات لهذا النادي")
            val list = out.values.toList()
            synchronized(lock) {
                cache[ck] = now to list
                failedAt.remove(ck)
            }
            return list
        } catch (e: Exception) {
            synchronized(lock) { failedAt[ck] = System.currentTimeMillis() }
            throw e
        }
    }

    private fun scoreOf(o: JSONObject, key: String): Int? {
        val s = o.optJSONObject(key) ?: return null
        if (!s.has("current") || s.isNull("current")) return null
        return s.optInt("current").takeIf { it in 0..20 }
    }

    private fun minuteText(status: String, elapsed: Int): String {
        val total = when (status) {
            "first half" -> elapsed + 1
            "second half" -> 45 + elapsed + 1
            "extra time" -> 90 + elapsed + 1
            else -> return ""
        }
        return when {
            status == "first half" && total > 45 -> "45+" + (total - 45) + "'"
            status == "second half" && total > 90 -> "90+" + (total - 90) + "'"
            else -> total.toString() + "'"
        }
    }

    private fun sofaCrest(teamId: Long): String =
        if (teamId > 0L) "https://api.sofascore.app/api/v1/team/$teamId/image" else ""

    private fun parseEvent(o: JSONObject, now: Long): Match? {
        val id = o.optLong("id", 0L)
        if (id <= 0L) return null
        val home = o.optJSONObject("homeTeam")?.optString("name").orEmpty()
        val away = o.optJSONObject("awayTeam")?.optString("name").orEmpty()
        if (home.isBlank() || away.isBlank()) return null
        val start = o.optLong("startTimestamp", 0L) * 1000L
        val st = o.optJSONObject("status")
        val type = st?.optString("type").orEmpty().lowercase(Locale.ROOT)
        val desc = st?.optString("description").orEmpty().lowercase(Locale.ROOT)
        val status = when (type) {
            "finished" -> "finished"
            "canceled", "cancelled" -> "canceled"
            "postponed" -> "postponed"
            "interrupted", "suspended" -> "suspended"
            "inprogress" -> when {
                desc.contains("1st") -> "first half"
                desc.contains("2nd") -> "second half"
                desc.contains("halftime") || desc == "ht" -> "half time"
                desc.contains("extra") -> "extra time"
                desc.contains("penalt") -> "penalties"
                else -> "in progress"
            }
            else -> ""
        }
        var progress = ""
        val periodStart = o.optJSONObject("time")?.optLong("currentPeriodStartTimestamp", 0L) ?: 0L
        if (type == "inprogress" && periodStart > 0L) {
            progress = minuteText(status, ((now / 1000L - periodStart) / 60L).toInt().coerceAtLeast(0))
        }
        return Match(
            id = "sofascore|$id",
            home = home,
            away = away,
            startMs = start,
            league = o.optJSONObject("tournament")?.optString("name").orEmpty(),
            homeScore = scoreOf(o, "homeScore"),
            awayScore = scoreOf(o, "awayScore"),
            status = status,
            progress = progress,
            homeBadge = sofaCrest(o.optJSONObject("homeTeam")?.optLong("id", 0L) ?: 0L),
            awayBadge = sofaCrest(o.optJSONObject("awayTeam")?.optLong("id", 0L) ?: 0L),
            source = "sofascore"
        )
    }

    /**
     * التشكيلة والخطة من نفس معرّف المباراة الذي تم التحقق منه بالأندية وموعد البداية.
     * confirmed=false تعني متوقعة فقط؛ لا ترفعها الشاشة إلى «رسمية».
     */
    fun lineups(eventId: String): SofaScoreLineupData? {
        val id = eventId.removePrefix("sofascore|").trim()
        require(id.isNotEmpty() && id.all { it.isDigit() }) { "معرّف مباراة غير صالح" }
        val now = System.currentTimeMillis()
        synchronized(lock) {
            val cached = lineupCache[id]
            if (cached != null && now - cached.first in 0..600_000L) return cached.second
            val failed = lineupFailedAt[id] ?: 0L
            if (failed > 0L && now - failed in 0..90_000L) return null
        }
        val parsed = try {
            SofaScoreLineupParser.parse(get("$BASE/event/$id/lineups"), id)
        } catch (e: Exception) {
            synchronized(lock) { lineupFailedAt[id] = System.currentTimeMillis() }
            throw e
        }
        synchronized(lock) {
            if (parsed == null) lineupFailedAt[id] = now
            else { lineupCache[id] = now to parsed; lineupFailedAt.remove(id) }
        }
        return parsed
    }

    /**
     * معلومات الملعب والحكم والجولة والحضور والمدربين، بعد مطابقة المعرّف
     * والفريقين وموعد البداية مع المباراة المفتوحة. الحقول الناقصة تظل فارغة.
     */
    fun eventInfo(eventId: String, home: String, away: String, startMs: Long): SofaScoreEventInfo? {
        val id = eventId.removePrefix("sofascore|").trim()
        require(id.isNotEmpty() && id.all { it.isDigit() }) { "معرّف مباراة غير صالح" }
        if (home.isBlank() || away.isBlank() || startMs <= 0L) return null
        val now = System.currentTimeMillis()
        synchronized(lock) {
            val cached = eventInfoCache[id]
            if (cached != null && now - cached.first in 0..600_000L) return cached.second
            val failed = eventInfoFailedAt[id] ?: 0L
            if (failed > 0L && now - failed in 0..180_000L) return null
        }
        val parsed = try {
            SofaScoreEventInfoParser.parse(get("$BASE/event/$id"), id, home, away, startMs)
        } catch (e: Exception) {
            synchronized(lock) { eventInfoFailedAt[id] = System.currentTimeMillis() }
            throw e
        }
        synchronized(lock) {
            if (parsed == null) eventInfoFailedAt[id] = now
            else { eventInfoCache[id] = now to parsed; eventInfoFailedAt.remove(id) }
        }
        return parsed
    }

    /** أحداث المباراة: أهداف وبطاقات وتبديلات كما يعرضها المصدر. */
    fun incidents(eventId: String): List<TimelineEvent> {
        val id = eventId.removePrefix("sofascore|")
        require(id.isNotEmpty() && id.all { it.isDigit() }) { "معرّف مباراة غير صالح" }
        val arr = JSONObject(get("$BASE/event/$id/incidents")).optJSONArray("incidents") ?: return emptyList()
        val out = ArrayList<TimelineEvent>()
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val cls = o.optString("incidentClass")
            val t = o.optInt("time", -1)
            val added = o.optInt("addedTime", 0)
            val minute = if (t < 0) "" else if (added > 0) "$t+$added" else t.toString()
            val isHome = o.optBoolean("isHome", false)
            val player = o.optJSONObject("player")?.optString("name").orEmpty()
            when (o.optString("incidentType")) {
                "goal" -> out.add(TimelineEvent(minute, when (cls) { "ownGoal" -> "own goal"; "penalty" -> "penalty goal"; else -> "goal" }, player, isHome))
                "card" -> out.add(TimelineEvent(minute, when (cls) { "red" -> "red card"; "yellowRed" -> "second yellow"; else -> "yellow card" }, player, isHome))
                "substitution" -> {
                    val inn = o.optJSONObject("playerIn")?.optString("name").orEmpty()
                    val outP = o.optJSONObject("playerOut")?.optString("name").orEmpty()
                    if (inn.isNotBlank() || outP.isNotBlank()) out.add(TimelineEvent(minute, "substitution", "$inn ⇄ $outP", isHome))
                }
            }
        }
        return out
    }

    /** Strictly resolve the same fixture, not merely another game involving the same club. */
    fun resolveEventId(prefs: Prefs, home: String, away: String, startMs: Long, hs: Int, aw: Int): String {
        if (startMs <= 0L || home.isBlank() || away.isBlank()) return ""
        val def = MatchRepo.defForNames(prefs, home, away) ?: return ""
        return fetchTeam(prefs, def).asSequence()
            // Never borrow from another fixture, but do not discard the right fixture merely
            // because the primary provider reported a conflicting final score.
            .filter { com.uobidah.launcher.core.MatchScoreReconciler.isSameFixtureCandidate(home, away, startMs, it.home, it.away, it.startMs) }
            .minByOrNull { kotlin.math.abs(it.startMs - startMs) }
            ?.id.orEmpty()
    }
}
