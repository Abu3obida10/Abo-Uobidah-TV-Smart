package com.uobidah.launcher

import org.json.JSONObject
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * مزوّد FotMob: واجهة JSON عامة وسريعة (تُقاس عادة بأقل من 100ms بعد الإحماء).
 * يوفّر مباريات اليوم لكل الدوريات (ومنها الدوري المصري id=519)، الشعارات،
 * التشكيلات بإحداثيات الملعب، وأحداث اللقاء. يُستخدم كمصدر رئيسي مع ESPN احتياطياً.
 * البيانات لعرض شخصي داخل اللانشر مع إشارة «البيانات من FotMob» على الشاشة.
 */
object FotMobProvider {
    const val SOURCE_ID = "fotmob"
    const val LEAGUE_EGYPT_PL = 519

    private val cairo: ZoneId = ZoneId.of("Africa/Cairo")

    fun crestUrl(teamId: String): String =
        "https://images.fotmob.com/image_resources/logo/teamlogo/$teamId.png"

    fun playerImageUrl(playerId: String): String =
        "https://images.fotmob.com/image_resources/playerimages/$playerId.png"

    /** أسماء الدوريات الكبرى بالعربية؛ يُميّز «Premier League» الإنجليزي عن المصري برمز الدولة. */
    fun leagueArabic(name: String, ccode: String): String {
        if (ccode.equals("EGY", true)) return when (name) {
            "Premier League" -> "الدوري المصري"
            "Egypt Cup" -> "كأس مصر"
            "League Cup" -> "كأس الرابطة المصرية"
            "Super Cup" -> "السوبر المصري"
            else -> name
        }
        return when (name) {
            "Premier League" -> "الدوري الإنجليزي"
            "LaLiga" -> "الدوري الإسباني"
            "Serie A" -> "الدوري الإيطالي"
            "Bundesliga" -> "الدوري الألماني"
            "Ligue 1" -> "الدوري الفرنسي"
            "Champions League" -> "دوري أبطال أوروبا"
            "Europa League" -> "الدوري الأوروبي"
            "Conference League" -> "دوري المؤتمر الأوروبي"
            "CAF Champions League" -> "دوري أبطال أفريقيا"
            "CAF Confederation Cup" -> "كأس الكونفدرالية الأفريقية"
            "World Cup" -> "كأس العالم"
            "Saudi Pro League" -> "الدوري السعودي"
            else -> name
        }
    }

    /** مباريات نافذة 3 أيام (أمس/اليوم/غدًا بتوقيت القاهرة) لتغطية «القادمة» و«السابقة». */
    fun fixtures(day: LocalDate = LocalDate.now(cairo)): List<Match> {
        val out = LinkedHashMap<Long, Match>()
        var failures = 0
        for (offset in -1L..1L) {
            try {
                for (m in fixturesDay(day.plusDays(offset))) out[m.id.removePrefix("fm-").toLong()] = m
            } catch (_: Exception) { failures++ }
        }
        if (out.isEmpty() && failures > 0) throw IllegalStateException("FotMob لم يُرجع مباريات للنافذة")
        return out.values.toList()
    }

    /** مباريات يوم واحد بتوقيت القاهرة. */
    private fun fixturesDay(day: LocalDate): List<Match> {
        val d = day.format(DateTimeFormatter.BASIC_ISO_DATE)
        val url = "https://www.fotmob.com/api/data/matches?date=$d&timezone=Africa%2FCairo&ccode3=EGY&includeNextDayLateNight=true"
        return parseMatches(JSONObject(NetJson2.get(url)))
    }

    internal fun parseMatches(root: JSONObject): List<Match> {
        val leagues = root.optJSONArray("leagues") ?: throw IllegalStateException("FotMob: لا توجد دوريات في الرد")
        val out = ArrayList<Match>()
        for (i in 0 until leagues.length()) {
            val lg = leagues.getJSONObject(i)
            val lgName = leagueArabic(lg.optString("name"), lg.optString("ccode"))
            val matches = lg.optJSONArray("matches") ?: continue
            for (j in 0 until matches.length()) {
                val m = matches.getJSONObject(j)
                val id = m.optLong("id", 0L)
                if (id == 0L) continue
                val home = m.optJSONObject("home") ?: continue
                val away = m.optJSONObject("away") ?: continue
                val status = m.optJSONObject("status")
                val utc = status?.optString("utcTime").orEmpty()
                val startMs = try { Instant.parse(utc).toEpochMilli() } catch (_: Exception) { 0L }
                val cancelled = status?.optBoolean("cancelled", false) == true
                val finished = status?.optBoolean("finished", false) == true
                val started = status?.optBoolean("started", false) == true
                val reason = status?.optJSONObject("reason")?.optString("short").orEmpty()
                val liveShort = status?.optJSONObject("liveTime")?.optString("short").orEmpty()
                val st = when {
                    cancelled -> "cancelled"
                    finished -> "FT"
                    started -> "live"
                    reason.equals("Postponed", true) -> "postponed"
                    else -> ""
                }
                val progress = if (started && !finished) liveShort.ifBlank { reason } else ""
                val hs = if (home.has("score")) home.optInt("score") else null
                val as_ = if (away.has("score")) away.optInt("score") else null
                val homeId = home.optLong("id", 0L).takeIf { it != 0L }?.toString().orEmpty()
                val awayId = away.optLong("id", 0L).takeIf { it != 0L }?.toString().orEmpty()
                out.add(
                    Match(
                        id = "fm-$id",
                        home = home.optString("name"),
                        away = away.optString("name"),
                        startMs = startMs,
                        league = lgName,
                        homeScore = if (started || finished) hs else null,
                        awayScore = if (started || finished) as_ else null,
                        status = st,
                        progress = progress,
                        homeBadge = if (homeId.isNotBlank()) crestUrl(homeId) else "",
                        awayBadge = if (awayId.isNotBlank()) crestUrl(awayId) else "",
                        homeTeamId = homeId,
                        awayTeamId = awayId,
                        source = SOURCE_ID,
                        externalUrl = "https://www.fotmob.com/matches/x/#$id"
                    )
                )
            }
        }
        if (out.isEmpty()) throw IllegalStateException("FotMob لم يُرجع مباريات لهذا اليوم")
        return out
    }

    /** لاعب في التشكيلة مع موضعه على الملعب (كسور 0..1 من أعلى اليسار في العرض الرأسي للملعب). */
    class PitchPlayer(
        val name: String,
        val shirt: String,
        val playerId: String,
        val x: Float,
        val y: Float,
        val home: Boolean,
        val sub: Boolean
    )

    class TeamLineup(
        val formation: String,
        val starters: List<PitchPlayer>,
        val subs: List<PitchPlayer>
    )

    class MatchFacts(
        val homeFormation: String,
        val awayFormation: String,
        val players: List<PitchPlayer>,
        val timeline: List<TimelineEvent>,
        val homeScore: Int?,
        val awayScore: Int?,
        val statusShort: String
    )

    /** تفاصيل لقاء: تشكيلة بإحداثيات ملعب + أحداث. */
    fun matchFacts(matchId: String): MatchFacts {
        val id = matchId.removePrefix("fm-")
        return parseFacts(JSONObject(NetJson2.get("https://www.fotmob.com/api/data/matchDetails?matchId=$id")))
    }

    internal fun parseFacts(root: JSONObject): MatchFacts {
        val content = root.optJSONObject("content") ?: throw IllegalStateException("FotMob: لا محتوى")
        val lineup = content.optJSONObject("lineup")
        val players = ArrayList<PitchPlayer>()
        var homeFormation = ""
        var awayFormation = ""
        if (lineup != null) {
            for ((side, homeFlag) in listOf("homeTeam" to true, "awayTeam" to false)) {
                val team = lineup.optJSONObject(side) ?: continue
                if (homeFlag) homeFormation = team.optString("formation") else awayFormation = team.optString("formation")
                val starters = team.optJSONArray("starters")
                if (starters != null) {
                    for (k in 0 until starters.length()) {
                        val p = starters.getJSONObject(k)
                        val lay = p.optJSONObject("verticalLayout") ?: continue
                        players.add(
                            PitchPlayer(
                                name = p.optString("name"),
                                shirt = p.optString("shirtNumber"),
                                playerId = p.optLong("id", 0L).toString(),
                                x = lay.optDouble("x", 0.5).toFloat(),
                                y = lay.optDouble("y", 0.5).toFloat(),
                                home = homeFlag,
                                sub = false
                            )
                        )
                    }
                }
                val subs = team.optJSONArray("subs")
                if (subs != null) {
                    for (k in 0 until subs.length()) {
                        val p = subs.getJSONObject(k)
                        players.add(
                            PitchPlayer(
                                name = p.optString("name"),
                                shirt = p.optString("shirtNumber"),
                                playerId = p.optLong("id", 0L).toString(),
                                x = 0f, y = 0f,
                                home = homeFlag,
                                sub = true
                            )
                        )
                    }
                }
            }
        }
        val events = ArrayList<TimelineEvent>()
        val matchFacts = content.optJSONObject("matchFacts")
        val evArr = matchFacts?.optJSONArray("events")
        if (evArr != null) {
            for (k in 0 until evArr.length()) {
                val e = evArr.getJSONObject(k)
                val type = e.optString("type")
                if (type != "Goal" && type != "Card" && type != "Substitution") continue
                val minute = e.optInt("time", 0)
                val desc = when (type) {
                    "Goal" -> "هدف"
                    "Card" -> if (e.optString("card").contains("Red", true)) "بطاقة حمراء" else "بطاقة صفراء"
                    else -> "تبديل"
                }
                val playerName = (e.optJSONObject("player") ?: e.optJSONObject("swap") ?: JSONObject()).optString("name")
                val isHome = e.optBoolean("isHome", true)
                events.add(TimelineEvent(minute.toString(), desc, playerName, isHome))
            }
        }
        val general = root.optJSONObject("general")
        val statusShort = general?.optJSONObject("status")?.optString("shortName")
            ?: general?.optString("statusShort") ?: ""
        val hs = general?.optInt("homeScore", -1)?.takeIf { it >= 0 }
        val as_ = general?.optInt("awayScore", -1)?.takeIf { it >= 0 }
        return MatchFacts(homeFormation, awayFormation, players, events, hs, as_, statusShort)
    }
}

/** جسر صغير لإعادة استخدام قناة الشبكة الموحدة مع رؤوس المتصفح. */
private object NetJson2 {
    fun get(url: String): String =
        String(Net.getBytes(url, 12_000, Net.UA_BROWSER, null, 5 * 1024 * 1024, emptyMap()), Charsets.UTF_8)
}
