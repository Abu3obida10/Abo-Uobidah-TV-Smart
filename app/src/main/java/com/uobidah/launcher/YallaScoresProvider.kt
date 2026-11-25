package com.uobidah.launcher

import com.uobidah.launcher.core.YallaScoresHtmlParser
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap

/**
 * Public YSscores team-page adapter. It reads only visible public match cards and applies
 * a short cache. The site does not publish a documented public JSON API, so this adapter
 * is intentionally isolated and can fall back to the other configured providers.
 */
object YallaScoresProvider {
    private const val BASE = "https://www.ysscores.com"
    private const val CACHE_MS = 180_000L
    private const val HOT_CACHE_MS = 12_000L
    private const val MAX_PAGE_BYTES = 2 * 1024 * 1024
    private val USER_AGENT = "AboUobidahTV/${BuildConfig.VERSION_NAME} (Android TV; public sports results client)"

    private val teamPages = mapOf(
        "138995" to "101745/Al-Ahly-FC",
        "133612" to "10260/Manchester-United",
        "133738" to "8633/Real-Madrid"
    )
    private val memoryCache = HashMap<String, Pair<Long, List<Match>>>()
    private val failureCache = HashMap<String, Long>()
    private val teamLocks = ConcurrentHashMap<String, Any>()

    fun fetchTeam(def: TeamDef, timezone: String): List<Match> {
        val page = teamPages[def.id] ?: resolveByName(def)
            ?: throw IOException("صفحة YSscores غير معرّفة لهذا النادي؛ استخدم أحد المصادر البديلة أو أضف ربط النادي")
        val cacheKey = def.id + "|" + page
        // Multiple screens and the background service can request the same team at once.
        // Serialize only that team's fetch so duplicate page downloads do not pile up.
        val teamLock = teamLocks.computeIfAbsent(cacheKey) { Any() }
        synchronized(teamLock) {
            val now = System.currentTimeMillis()
            synchronized(memoryCache) {
                val cached = memoryCache[cacheKey]
                // وقت المباراة: نسخة عمرها 12 ثانية فقط حتى يظهر الهدف فوراً؛ خارج اللعب يبقى 3 دقائق.
                val ttl = if (cached != null && cached.second.any { it.inPlayWindow(now) }) HOT_CACHE_MS else CACHE_MS
                if (cached != null && now - cached.first in 0..ttl) return cached.second
                val failedAt = failureCache[cacheKey] ?: 0L
                if (failedAt > 0L && now - failedAt in 0..30_000L) {
                    throw IOException("تعذر الاتصال بصفحة الفريق مؤخرًا؛ أعد المحاولة بعد قليل")
                }
            }

            try {
                val url = "$BASE/ar/team/$page"
                val html = String(
                    Net.getBytes(
                        url = url,
                        timeoutMs = 7_000,
                        ua = USER_AGENT,
                        referer = "$BASE/ar/home",
                        maxBytes = MAX_PAGE_BYTES,
                        headers = mapOf("Accept" to "text/html,application/xhtml+xml")
                    ),
                    Charsets.UTF_8
                )
                if (html.contains("captcha", true) || html.contains("access denied", true)) {
                    throw IOException("الموقع لم يسمح بتحميل صفحة الفريق الآن")
                }
                val parsed = YallaScoresHtmlParser.parseTeamPage(html, timezone)
                val aliases = when (def.id) {
                    "138995" -> listOf("الأهلي", "Al Ahly", "Al-Ahly")
                    "133612" -> listOf("مانشستر يونايتد", "مان يونايتد", "Manchester United")
                    "133738" -> listOf("ريال مدريد", "Real Madrid")
                    else -> listOf(def.arabic, def.query)
                }
                val focalBadge = YallaScoresHtmlParser.extractTeamBadge(html, aliases)
                val badgeMap = try { YallaScoresHtmlParser.extractBadgeMap(html, parsed.flatMap { listOf(it.home, it.away) }.distinct()) } catch (_: Exception) { emptyMap() }
                val data = parsed.map { p ->
                    val favoriteIsHome = sameClub(p.home, def)
                    val favoriteIsAway = sameClub(p.away, def)
                    Match(
                        id = "ysscores|${p.id}",
                        home = p.home,
                        away = p.away,
                        startMs = p.startMs,
                        league = p.league,
                        homeScore = p.homeScore,
                        awayScore = p.awayScore,
                        status = p.status,
                        progress = p.progress,
                        venue = p.venue,
                        homeBadge = p.homeBadge.ifBlank { badgeMap[p.home] ?: if (favoriteIsHome) focalBadge else "" },
                        awayBadge = p.awayBadge.ifBlank { badgeMap[p.away] ?: if (favoriteIsAway) focalBadge else "" },
                        homeTeamId = "",
                        awayTeamId = "",
                        fromCache = false,
                        source = "ysscores",
                        externalUrl = p.url
                    )
                }.filter { MatchProviders.belongsTo(it, def) }
                    .distinctBy { it.id }

                if (data.isEmpty()) throw IOException("لم أستطع قراءة بطاقات مباريات النادي من صفحة YSscores")
                synchronized(memoryCache) {
                    memoryCache[cacheKey] = now to data
                    failureCache.remove(cacheKey)
                }
                return data
            } catch (e: Exception) {
                synchronized(memoryCache) { failureCache[cacheKey] = System.currentTimeMillis() }
                throw e
            }
        }
    }

    private fun sameClub(observed: String, def: TeamDef): Boolean {
        fun norm(value: String) = value.lowercase().replace(Regex("[\u064B-\u065F\u0670]"), "")
            .replace('أ', 'ا').replace('إ', 'ا').replace('آ', 'ا')
            .replace(Regex("[^\\p{L}\\p{N}]"), "")
        val name = norm(observed)
        return name.isNotBlank() && listOf(def.arabic, def.query).any { name == norm(it) || name.contains(norm(it)) || norm(it).contains(name) }
    }

    private fun resolveByName(def: TeamDef): String? {
        val name = (def.query + " " + def.arabic).lowercase()
            .replace(Regex("[^\\p{L}\\p{N}]"), "")
        return when {
            name.contains("alahly") || name.contains("الاهلي") -> "101745/Al-Ahly-FC"
            name.contains("realmadrid") || name.contains("ريالمدريد") -> "8633/Real-Madrid"
            name.contains("manchesterunited") || name.contains("مانشستريونايتد") -> "10260/Manchester-United"
            else -> null
        }
    }

    fun clearCache() = synchronized(memoryCache) { memoryCache.clear() }
}
