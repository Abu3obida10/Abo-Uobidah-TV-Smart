package com.uobidah.launcher

import android.content.Context
import com.uobidah.launcher.core.SourceStatus
import com.uobidah.launcher.core.CityClock
import com.uobidah.launcher.core.SportsDbUrl
import com.uobidah.launcher.core.MatchScoreReconciler
import org.json.JSONObject
import org.json.JSONArray
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneOffset
import java.time.ZoneId
import java.time.chrono.HijrahDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoField
import java.util.Date
import java.util.Locale

private fun enc(s: String): String = URLEncoder.encode(s, "UTF-8")

// ======================= التاريخ =======================
object DateUtil {
    private val hijriMonths = arrayOf(
        "محرم", "صفر", "ربيع الأول", "ربيع الآخر", "جمادى الأولى", "جمادى الآخرة",
        "رجب", "شعبان", "رمضان", "شوال", "ذو القعدة", "ذو الحجة"
    )

    fun arNum(n: Int): String {
        val sb = StringBuilder()
        for (ch in n.toString()) {
            if (ch in '0'..'9') sb.append('٠' + (ch - '0')) else sb.append(ch)
        }
        return sb.toString()
    }

    fun hijri(offsetDays: Int): String = hijri(offsetDays, LocalDate.now())

    fun hijri(offsetDays: Int, baseDate: LocalDate): String {
        return try {
            val h = HijrahDate.from(baseDate.plusDays(offsetDays.toLong()))
            val day = h.get(ChronoField.DAY_OF_MONTH)
            val month = h.get(ChronoField.MONTH_OF_YEAR)
            val year = h.get(ChronoField.YEAR)
            arNum(day) + " " + hijriMonths[month - 1] + " " + arNum(year) + " هـ"
        } catch (e: Exception) {
            ""
        }
    }

    /** سطر هجري مختصر للشاشات الأفقية الضيقة؛ لا يقسم اسم الشهر حرفيًا. */
    fun hijriCompact(offsetDays: Int, baseDate: LocalDate): String {
        val shortMonths = arrayOf("محرم", "صفر", "ربيع١", "ربيع٢", "جم١", "جم٢", "رجب", "شعبان", "رمضان", "شوال", "قعدة", "حجة")
        return try {
            val h = HijrahDate.from(baseDate.plusDays(offsetDays.toLong()))
            val day = h.get(ChronoField.DAY_OF_MONTH)
            val month = h.get(ChronoField.MONTH_OF_YEAR)
            val year = h.get(ChronoField.YEAR)
            arNum(day) + " " + shortMonths[month - 1] + " " + arNum(year) + "هـ"
        } catch (_: Exception) { "" }
    }

    /** تاريخ مدني قصير مناسب لترويسة Android TV ذات المساحة المحدودة. */
    fun gregorianCompact(date: LocalDate): String {
        val months = arrayOf("يناير", "فبراير", "مارس", "أبريل", "مايو", "يونيو", "يوليو", "أغسطس", "سبتمبر", "أكتوبر", "نوفمبر", "ديسمبر")
        return arNum(date.dayOfMonth) + " " + months[date.monthValue - 1]
    }

    fun gregorian(): String = gregorian(LocalDate.now())

    fun gregorian(date: LocalDate): String =
        date.format(DateTimeFormatter.ofPattern("EEEE d MMMM yyyy", Locale("ar", "EG")))

    /** Format a match timestamp in the currently selected city timezone, not the device's default zone. */
    fun formatInstant(epochMs: Long, timezone: String, pattern: String, locale: Locale = Locale("ar", "EG")): String {
        val zone = try { ZoneId.of(timezone) } catch (_: Exception) { ZoneId.of("Africa/Cairo") }
        return try {
            java.time.Instant.ofEpochMilli(epochMs).atZone(zone).format(DateTimeFormatter.ofPattern(pattern, locale))
        } catch (_: Exception) {
            ""
        }
    }
}

// ======================= الطقس =======================
class City(val name: String, val lat: Double, val lon: Double, val sub: String, val timezone: String)

class DayForecast(val date: LocalDate, val hi: Double, val lo: Double, val code: Int)

class Weather(
    val temp: Double,
    val feels: Double,
    val code: Int,
    val wind: Double,
    val humidity: Int,
    val isDay: Boolean,
    val hi: Double,
    val lo: Double,
    val days: List<DayForecast> = emptyList(),
    val timezone: String = "Africa/Cairo"
)

object WeatherRepo {

    /** بحث عن أي مدينة/قرية/مركز بالعربية أو الإنجليزية. */
    fun search(q: String): List<City> {
        val query = q.trim().take(80)
        require(query.isNotEmpty()) { "empty city query" }
        val out = ArrayList<City>()
        val seen = HashSet<String>()
        val langs = listOf("ar", "en")
        for (lang in langs) {
            val url = "https://geocoding-api.open-meteo.com/v1/search?name=" + enc(query) +
                "&count=15&language=" + lang + "&format=json"
            val arr = JSONObject(Net.getString(url)).optJSONArray("results")
            if (arr != null && arr.length() > 0) {
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    val lat = o.optDouble("latitude", Double.NaN)
                    val lon = o.optDouble("longitude", Double.NaN)
                    if (!lat.isFinite() || !lon.isFinite() || lat !in -90.0..90.0 || lon !in -180.0..180.0) continue
                    val dedupe = "${String.format(Locale.US, "%.4f", lat)}|${String.format(Locale.US, "%.4f", lon)}"
                    if (!seen.add(dedupe)) continue
                    val parts = ArrayList<String>()
                    val a1 = o.optString("admin1", "")
                    val a2 = o.optString("admin2", "")
                    val co = o.optString("country", "")
                    if (a2.isNotEmpty()) parts.add(a2)
                    if (a1.isNotEmpty() && a1 != a2) parts.add(a1)
                    if (co.isNotEmpty()) parts.add(co)
                    out.add(City(o.optString("name", query), lat, lon, parts.joinToString("، "), o.optString("timezone", "").take(80)))
                }
                return out
            }
        }
        return out
    }

    fun fetch(lat: Double, lon: Double): Weather {
        require(lat.isFinite() && lat in -90.0..90.0 && lon.isFinite() && lon in -180.0..180.0) { "invalid coordinates" }
        val url = "https://api.open-meteo.com/v1/forecast?latitude=$lat&longitude=$lon" +
            "&current=temperature_2m,apparent_temperature,relative_humidity_2m,weather_code,wind_speed_10m,is_day" +
            "&daily=temperature_2m_max,temperature_2m_min,weather_code&timezone=auto&forecast_days=6"
        val j = JSONObject(Net.getString(url))
        val cur = j.getJSONObject("current")
        val timezone = j.optString("timezone", "Africa/Cairo").take(80).ifBlank { "Africa/Cairo" }
        val daily = j.optJSONObject("daily")
        var hi = Double.NaN
        var lo = Double.NaN
        val days = ArrayList<DayForecast>()
        if (daily != null) {
            val mx = daily.optJSONArray("temperature_2m_max")
            val mn = daily.optJSONArray("temperature_2m_min")
            val wc = daily.optJSONArray("weather_code")
            val dt = daily.optJSONArray("time")
            if (mx != null) hi = mx.optDouble(0)
            if (mn != null) lo = mn.optDouble(0)
            if (mx != null && mn != null && dt != null) {
                // اليوم الأول هو اليوم الحالي، نعرض الأيام الخمسة التالية
                for (n in 1 until minOf(mx.length(), mn.length(), dt.length())) {
                    try {
                        days.add(DayForecast(LocalDate.parse(dt.getString(n)), mx.optDouble(n), mn.optDouble(n), wc?.optInt(n) ?: 0))
                    } catch (e: Exception) {
                    }
                }
            }
        }
        return Weather(
            cur.getDouble("temperature_2m"),
            cur.optDouble("apparent_temperature"),
            cur.optInt("weather_code"),
            cur.optDouble("wind_speed_10m"),
            cur.optInt("relative_humidity_2m"),
            cur.optInt("is_day", 1) == 1,
            hi,
            lo,
            days,
            timezone
        )
    }

    private fun cacheKey(lat: Double, lon: Double): String =
        "weather_cache_v1_" + String.format(Locale.US, "%.4f_%.4f", lat, lon).replace('-', 'm').replace('.', '_')

    /** آخر قراءة ناجحة، حتى 48 ساعة، لعرضها فوراً عند فتح التطبيق أو انقطاع الشبكة. */
    fun cached(prefs: Prefs, lat: Double, lon: Double, maxAgeMs: Long = 48L * 60L * 60L * 1000L): Pair<Weather, Long>? {
        return try {
            val raw = prefs.sp.getString(cacheKey(lat, lon), null) ?: return null
            val root = JSONObject(raw)
            val savedAt = root.optLong("updatedAt", 0L)
            val age = System.currentTimeMillis() - savedAt
            if (savedAt <= 0L || age < 0L || age > maxAgeMs) return null
            val days = ArrayList<DayForecast>()
            val arr = root.optJSONArray("days")
            if (arr != null) for (i in 0 until arr.length()) {
                val item = arr.optJSONObject(i) ?: continue
                val date = runCatching { LocalDate.parse(item.optString("date")) }.getOrNull() ?: continue
                days.add(DayForecast(date, item.optDouble("hi", Double.NaN), item.optDouble("lo", Double.NaN), item.optInt("code", 0)))
            }
            val w = Weather(
                root.optDouble("temp"), root.optDouble("feels"), root.optInt("code"),
                root.optDouble("wind"), root.optInt("humidity"), root.optInt("isDay", 1) == 1,
                root.optDouble("hi"), root.optDouble("lo"), days,
                root.optString("timezone", "Africa/Cairo")
            )
            if (!w.temp.isFinite() || !w.feels.isFinite()) null else Pair(w, savedAt)
        } catch (_: Exception) { null }
    }

    fun saveCache(prefs: Prefs, lat: Double, lon: Double, w: Weather, updatedAt: Long) {
        try {
            val days = JSONArray()
            for (d in w.days.take(7)) days.put(JSONObject().put("date", d.date.toString()).put("hi", d.hi).put("lo", d.lo).put("code", d.code))
            val root = JSONObject()
                .put("updatedAt", updatedAt).put("temp", w.temp).put("feels", w.feels)
                .put("code", w.code).put("wind", w.wind).put("humidity", w.humidity)
                .put("isDay", if (w.isDay) 1 else 0).put("hi", w.hi).put("lo", w.lo)
                .put("timezone", w.timezone).put("days", days)
            prefs.sp.edit().putString(cacheKey(lat, lon), root.toString()).apply()
        } catch (_: Exception) { }
    }

    /** (رمز، وصف عربي) */
    fun describe(code: Int, day: Boolean): Pair<String, String> {
        return when (code) {
            0 -> Pair(if (day) "☀️" else "🌙", "صافٍ")
            1 -> Pair(if (day) "🌤️" else "🌙", "غالباً صافٍ")
            2 -> Pair("⛅", "غائم جزئياً")
            3 -> Pair("☁️", "غائم")
            45, 48 -> Pair("🌫️", "ضباب")
            51, 53, 55, 56, 57 -> Pair("🌦️", "رذاذ")
            61, 63, 65, 66, 67 -> Pair("🌧️", "مطر")
            71, 73, 75, 77 -> Pair("❄️", "ثلج")
            80, 81, 82 -> Pair("🌧️", "زخات مطر")
            85, 86 -> Pair("🌨️", "زخات ثلج")
            95, 96, 99 -> Pair("⛈️", "عواصف رعدية")
            else -> Pair("🌡️", "—")
        }
    }
}

// ======================= الصلاة =======================
object PrayerRepo {
    val keys: List<String> = listOf("Fajr", "Sunrise", "Dhuhr", "Asr", "Maghrib", "Isha")
    val adhanKeys: List<String> = listOf("Fajr", "Dhuhr", "Asr", "Maghrib", "Isha")
    val arabic: Map<String, String> = mapOf(
        "Fajr" to "الفجر", "Sunrise" to "الشروق", "Dhuhr" to "الظهر",
        "Asr" to "العصر", "Maghrib" to "المغرب", "Isha" to "العشاء"
    )
    val methods: List<Pair<Int, String>> = listOf(
        Pair(5, "الهيئة المصرية العامة للمساحة"),
        Pair(4, "أم القرى (مكة)"),
        Pair(3, "رابطة العالم الإسلامي"),
        Pair(2, "أمريكا الشمالية"),
        Pair(1, "جامعة كراتشي"),
        Pair(8, "منطقة الخليج"),
        Pair(9, "الكويت"),
        Pair(10, "قطر")
    )

    private fun loadCache(prefs: Prefs): JSONObject {
        val raw = prefs.sp.getString("prayer_cache", null) ?: return JSONObject()
        return try { JSONObject(raw) } catch (e: Exception) { JSONObject() }
    }

    private fun saveCache(prefs: Prefs, cache: JSONObject, today: LocalDate) {
        val limit = today.minusDays(1).toString()
        val ks = ArrayList<String>()
        val it = cache.keys()
        while (it.hasNext()) ks.add(it.next())
        for (k in ks) {
            if (k.substringBefore("|") < limit) cache.remove(k)
        }
        prefs.sp.edit().putString("prayer_cache", cache.toString()).apply()
    }

    private fun toMap(o: JSONObject): Map<String, String> {
        val m = LinkedHashMap<String, String>()
        for (k in keys) m[k] = o.optString(k, "")
        return m
    }

    /** يجلب مواقيت يوم معيّن (من الكاش أو AlAdhan). استدعِه من خيط خلفي. */
    fun timesFor(ctx: Context, prefs: Prefs, date: LocalDate): Map<String, String>? {
        // اقرأ إعدادات المدينة مرة واحدة؛ قد يختار المستخدم مدينة أخرى أثناء انتظار الشبكة.
        val lat = prefs.cityLat
        val lon = prefs.cityLon
        val method = prefs.prayerMethod
        val key = date.toString() + "|" + lat + "|" + lon + "|" + method
        val cache = loadCache(prefs)
        val hit = cache.optJSONObject(key)
        if (hit != null) {
            val cachedZone = hit.optString("_timezone", "").trim()
            val validZone = runCatching { ZoneId.of(cachedZone).id }.getOrNull()
            if (validZone != null && prefs.cityLat == lat && prefs.cityLon == lon && prefs.prayerMethod == method) {
                prefs.cityTimezone = validZone
            }
            return toMap(hit)
        }
        return try {
            val url = "https://api.aladhan.com/v1/timings/" +
                date.format(DateTimeFormatter.ofPattern("dd-MM-yyyy")) +
                "?latitude=" + lat + "&longitude=" + lon +
                "&method=" + method
            val root = JSONObject(Net.getString(url))
            val data = root.getJSONObject("data")
            val meta = data.optJSONObject("meta")
            val tz = meta?.optString("timezone", "")?.trim().orEmpty()
            val validZone = if (tz.isNotBlank()) runCatching { ZoneId.of(tz).id }.getOrNull() else null
            if (validZone != null && prefs.cityLat == lat && prefs.cityLon == lon && prefs.prayerMethod == method) {
                prefs.cityTimezone = validZone
            }
            val t = data.getJSONObject("timings")
            val o = JSONObject()
            for (k in keys) {
                val rawTime = t.optString(k, "").trim()
                val parsed = parse(rawTime)
                if (parsed != null) o.put(k, parsed.toString())
            }
            if (keys.any { !o.has(it) }) return null
            if (validZone != null) o.put("_timezone", validZone)
            cache.put(key, o)
            saveCache(prefs, cache, date)
            toMap(o)
        } catch (e: Exception) {
            null
        }
    }

    /** وقت API قد يتبعه اختصار المنطقة الزمنية بين قوسين؛ نقرأ HH:mm فقط. */
    fun parse(s: String): LocalTime? {
        val m = Regex("""^\s*(\d{1,2}):(\d{2})""").find(s) ?: return null
        val hour = m.groupValues[1].toIntOrNull() ?: return null
        val minute = m.groupValues[2].toIntOrNull() ?: return null
        if (hour !in 0..23 || minute !in 0..59) return null
        return runCatching { LocalTime.of(hour, minute) }.getOrNull()
    }

    fun localNow(prefs: Prefs): LocalDateTime = CityClock.localNow(prefs.cityTimezone)

    /** الصلاة القادمة (بدون الشروق). */
    fun next(today: Map<String, String>?, tomorrow: Map<String, String>?, now: LocalDateTime): Pair<String, LocalDateTime>? {
        if (today != null) {
            for (k in adhanKeys) {
                val lt = parse(today[k] ?: "") ?: continue
                val t = LocalDateTime.of(now.toLocalDate(), lt)
                if (t.isAfter(now)) return Pair(k, t)
            }
        }
        if (tomorrow != null) {
            val lt = parse(tomorrow["Fajr"] ?: "")
            if (lt != null) return Pair("Fajr", LocalDateTime.of(now.toLocalDate().plusDays(1), lt))
        }
        return null
    }

    fun countdown(from: LocalDateTime, to: LocalDateTime): String {
        val total = Duration.between(from, to).seconds
        val s = if (total < 0) 0L else total
        val h = s / 3600
        val m = (s % 3600) / 60
        val sec = s % 60
        return String.format(Locale.US, "%02d:%02d:%02d", h, m, sec)
    }
}

// ======================= المباريات (TheSportsDB) =======================
class Match(
    val id: String,
    val home: String,
    val away: String,
    val startMs: Long,
    val league: String,
    val homeScore: Int?,
    val awayScore: Int?,
    val status: String = "",
    val progress: String = "",
    val venue: String = "",
    val homeBadge: String = "",
    val awayBadge: String = "",
    val homeTeamId: String = "",
    val awayTeamId: String = "",
    val fromCache: Boolean = false,
    /** معرف المصدر الأصلي؛ يُستخدم لاختيار رابط التفاصيل الصحيح ومنع خلط المعرّفات بين المزودين. */
    val source: String = "thesportsdb",
    /** رابط التفاصيل الأصلي للمصادر الخارجية، يُحفظ مع الكاش حتى يفتح اللقاء الصحيح. */
    val externalUrl: String = ""
) {
    /** لا تُصنّف المباراة منتهية بالوقت أو وجود نتيجة فقط؛ يلزم تصريح صريح من المصدر. */
    fun finished(now: Long): Boolean = com.uobidah.launcher.core.MatchStatusNormalizer.isFinished(status)

    /** التأجيل والإلغاء والإيقاف حالات مستقلة ولا تعني اكتمال المباراة. */
    fun postponed(): Boolean = com.uobidah.launcher.core.MatchStatusNormalizer.isPostponed(status)
    fun canceled(): Boolean = com.uobidah.launcher.core.MatchStatusNormalizer.isCanceled(status)
    fun suspended(): Boolean = com.uobidah.launcher.core.MatchStatusNormalizer.isSuspended(status)

    /** هل هي قادمة بالفعل؟ لا نعيد مباراة ماضية مجهولة الحالة إلى صف القادمة. */
    fun upcoming(now: Long): Boolean {
        if (finished(now) || canceled() || postponed() || suspended() ||
            com.uobidah.launcher.core.MatchStatusNormalizer.isLive(status) || live(now)) return false
        if (startMs > 0L) return now < startMs
        return com.uobidah.launcher.core.MatchStatusNormalizer.isScheduled(status)
    }

    /** «نتائج سابقة» تعني مباراة انتهت فعلاً؛ الإلغاء والتأجيل والإيقاف حالات مستقلة. */
    fun previous(now: Long): Boolean = finished(now)

    /** نفس المباراة (المعرّف والأسماء كما هي) بنتيجة/حالة من المصدر المقارن دون إسقاط حقائق معروفة. */
    fun withLive(s: Match): Match {
        val keepTerminal = finished(0L) && !s.finished(0L)
        val keepExceptional = (canceled() || postponed() || suspended()) &&
            !(s.canceled() || s.postponed() || s.suspended() || s.finished(0L))
        fun statusPriority(value: String): Int = when {
            com.uobidah.launcher.core.MatchStatusNormalizer.isFinished(value) -> 5
            com.uobidah.launcher.core.MatchStatusNormalizer.isCanceled(value) ||
                com.uobidah.launcher.core.MatchStatusNormalizer.isPostponed(value) ||
                com.uobidah.launcher.core.MatchStatusNormalizer.isSuspended(value) -> 4
            com.uobidah.launcher.core.MatchStatusNormalizer.isLive(value) -> 3
            com.uobidah.launcher.core.MatchStatusNormalizer.isScheduled(value) -> 2
            value.isBlank() -> 0
            else -> 1
        }
        val keepExistingStatus = keepTerminal || keepExceptional || statusPriority(status) > statusPriority(s.status)
        val chosenStatus = if (keepExistingStatus || s.status.isBlank()) status else s.status
        val mergedScore = if (keepTerminal) homeScore to awayScore else
            com.uobidah.launcher.core.MatchScoreReconciler.mergeLiveScore(homeScore, awayScore, s.homeScore, s.awayScore)
        return Match(
            id, home, away, startMs, league,
            mergedScore.first, mergedScore.second, chosenStatus,
            if (keepTerminal || keepExceptional) progress else s.progress.ifBlank { progress },
            venue, homeBadge, awayBadge, homeTeamId, awayTeamId, fromCache, source, externalUrl
        )
    }

    /** Accept a final score from a higher-trust provider only for the identical fixture. */
    fun withVerifiedFinal(other: Match): Match {
        if (!com.uobidah.launcher.core.MatchScoreReconciler.isSameFixtureCandidate(
                home, away, startMs, other.home, other.away, other.startMs
            ) || !other.finished(0L) || other.homeScore !in 0..20 || other.awayScore !in 0..20) return this
        return Match(
            id, home, away, startMs, league.ifBlank { other.league }, other.homeScore, other.awayScore,
            "finished", "", venue.ifBlank { other.venue },
            homeBadge.ifBlank { other.homeBadge }, awayBadge.ifBlank { other.awayBadge },
            homeTeamId.ifBlank { other.homeTeamId }, awayTeamId.ifBlank { other.awayTeamId },
            fromCache, source, externalUrl
        )
    }

    /** نافذة تحديث قصيرة حول وقت المباراة، وليست دليلاً على أنها انتهت أو بدأت. */
    fun inPlayWindow(now: Long): Boolean =
        startMs > 0L && now >= startMs - 5 * 60_000L && now - startMs <= 170 * 60_000L &&
            !finished(now) && !canceled() && !postponed() && !suspended()

    /**
     * وجود رقمين وحده لا يثبت أن هناك نتيجة: بعض المصادر ترسل 0-0 للمباراة القادمة.
     * لا تُعرض النتيجة إلا مع حالة صريحة جارية أو منتهية، وبزوج كامل ضمن النطاق المعقول.
     */
    fun hasDisplayableScore(now: Long): Boolean {
        val matchState = state(now)
        return (matchState == MatchState.LIVE || matchState == MatchState.FINISHED) &&
            homeScore in 0..20 && awayScore in 0..20
    }

    /** الحالة المجهولة مميزة عن القادمة؛ لا نستنتج نتيجة المباراة من الساعة أو الأهداف. */
    fun state(now: Long): MatchState = when {
        canceled() -> MatchState.CANCELED
        postponed() -> MatchState.POSTPONED
        suspended() -> MatchState.SUSPENDED
        finished(now) -> MatchState.FINISHED
        live(now) -> MatchState.LIVE
        upcoming(now) -> MatchState.UPCOMING
        else -> MatchState.UNKNOWN
    }

    /** لا نعرض كلمة مباشر اعتماداً على وقت البداية وحده؛ نحتاج حالة/دقيقة موثوقة من المصدر. */
    fun live(now: Long): Boolean {
        // الكاش وحده لا يكفي لعرض «مباشر»؛ قد تكون المباراة انتهت أثناء انقطاع الشبكة.
        if (fromCache || startMs <= 0L || now < startMs || now - startMs > 170 * 60_000L ||
            finished(now) || canceled() || postponed() || suspended()) return false
        if (com.uobidah.launcher.core.MatchStatusNormalizer.isLive(status)) return true
        // الحالة المجدولة الصريحة أقوى من رقم غامض قد يكون نصاً متبقياً من الواجهة.
        if (com.uobidah.launcher.core.MatchStatusNormalizer.isScheduled(status)) return false
        // بعض المصادر لا ترسل الحالة لكنها ترسل دقيقة لعب. نقبلها فقط في نافذة زمنية معقولة.
        return progress.trim().matches(Regex("\\d{1,3}(?:\\+\\d{1,2})?['’]?$"))
    }

}

enum class MatchState(val labelAr: String) {
    UPCOMING("قادمة"), LIVE("جارية"), FINISHED("منتهية"), POSTPONED("مؤجلة"),
    CANCELED("ملغاة"), SUSPENDED("متوقفة مؤقتًا"), UNKNOWN("حالة غير مؤكدة")
}

class LineupPlayer(
    val name: String,
    val position: String,
    val home: Boolean,
    val sub: Boolean,
    val number: String,
    val captain: Boolean = false
)

data class SofaScoreLineupData(
    val eventId: String,
    val confirmed: Boolean?,
    val homeFormation: String,
    val awayFormation: String,
    val players: List<LineupPlayer>
)

data class SofaScoreEventInfo(
    val eventId: String,
    val tournament: String = "",
    val round: String = "",
    val venue: String = "",
    val city: String = "",
    val referee: String = "",
    val attendance: String = "",
    val homeManager: String = "",
    val awayManager: String = ""
)
class TimelineEvent(val minute: String, val kind: String, val player: String, val home: Boolean)

class TeamDef(val arabic: String, val query: String, val country: String, val id: String)

object MatchRepo {
    // المفتاح العام الرسمي 123، أو مفتاح premium يدخله المستخدم (مخزّن مشفراً) — انظر SportsDbUrl.
    @Volatile private var BASE = SportsDbUrl.base(null)
    @Volatile private var activeKey: String = ""
    private const val NO_BADGE = "__no_badge__"

    /** يضبط عنوان المصدر من مفتاح المستخدم المخزن. يُستدعى عند فتح أي شاشة وقبل كل جلب مجدول. */
    fun configure(prefs: Prefs) {
        val k = prefs.matchApiKey
        activeKey = k
        BASE = SportsDbUrl.base(k)
    }

    fun status(prefs: Prefs): SourceStatus = prefs.matchSourceStatus

    /** الفريق المتابَع المقابل لأسماء مباراة (عربية أو إنجليزية)، أو null. */
    fun defForNames(prefs: Prefs, home: String, away: String): TeamDef? {
        val id = knownIdForName(home).ifBlank { knownIdForName(away) }
        return allTeams(prefs).firstOrNull { it.id == id }
    }

    /**
     * وقت المباراة فقط: نقارن المصدر الأساسي بمصدر آخر ونأخذ الأحدث (الأعلى نتيجة، أو المنتهية).
     * الأهداف لا تنقص عادةً، فمصدر متأخر لا يُرجع النتيجة للوراء. تبقى هوية المباراة من المصدر الأساسي حتى لا تتكرر التنبيهات.
     */
    private fun crossCheck(prefs: Prefs, def: TeamDef?, primary: List<Match>, used: String, now: Long): List<Match> {
        // Cross-check live fixtures when multi-source is enabled. A finished score is
        // always verified against SofaScore when possible, even if optional live
        // cross-checking is disabled: a wrong cached final score must not persist.
        val hasFinishedToVerify = primary.any { it.finished(now) && it.source != "sofascore" }
        if (def == null || (!prefs.matchMultiSource && !hasFinishedToVerify) ||
            primary.none { it.inPlayWindow(now) || it.finished(now) }) return primary
        var result = primary
        for (other in listOf("ysscores", "sofascore")) {
            if (other == used || (!prefs.matchMultiSource && other != "sofascore")) continue
            val extra = try {
                if (other == "ysscores") YallaScoresProvider.fetchTeam(def, prefs.cityTimezone) else SofaScoreProvider.fetchTeam(prefs, def)
            } catch (_: Exception) { continue }
            result = result.map { m ->
                val s = extra.firstOrNull {
                    MatchScoreReconciler.isSameFixtureCandidate(m.home, m.away, m.startMs, it.home, it.away, it.startMs)
                } ?: return@map m
                if (s.finished(now) && s.homeScore in 0..20 && s.awayScore in 0..20) {
                    // SofaScore is the strongest independent feed here; YSscores can
                    // repair a TheSportsDB result, but must not overwrite SofaScore.
                    val canUse = other == "sofascore" || (other == "ysscores" && m.source != "sofascore")
                    if (canUse) return@map m.withVerifiedFinal(s)
                }
                if (!m.inPlayWindow(now)) return@map m
                if (m.finished(now) && !s.finished(now)) return@map m
                val scoreAdvances = MatchScoreReconciler.shouldAcceptLiveScore(
                    m.homeScore, m.awayScore, s.homeScore, s.awayScore
                )
                val statusAdvances =
                    (s.finished(now) && !m.finished(now)) ||
                    (s.live(now) && !m.live(now)) ||
                    (s.postponed() && !m.postponed()) ||
                    (s.canceled() && !m.canceled()) ||
                    (s.suspended() && !m.suspended())
                if (scoreAdvances || statusAdvances) m.withLive(s) else m
            }
        }
        return result
    }

    private const val HOT_REUSE_MS = 12_000L

    /** يعيد مباريات الفريق المحفوظة محلياً فوراً، دون أي اتصال بالشبكة. */
    fun cachedForTeam(prefs: Prefs, teamId: String): List<Match> =
        (readMatchCache(prefs, cacheKey("next", teamId)) + readMatchCache(prefs, cacheKey("last", teamId)))
            .distinctBy { it.id }

    /** هل لهذا الفريق مباراة داخل نافذة اللعب الآن (من الكاش المحفوظ فقط، دون شبكة)؟ */
    fun isHot(prefs: Prefs, teamId: String, now: Long = System.currentTimeMillis()): Boolean =
        readMatchCache(prefs, cacheKey("next", teamId)).any { it.inPlayWindow(now) } ||
            readMatchCache(prefs, cacheKey("last", teamId)).any { it.inPlayWindow(now) }

    /** يجلب أحدث نسخة من مباراة معروفة بمعرفها (للتحديث اللحظي داخل شاشة التفاصيل). */
    fun findFresh(prefs: Prefs, matchId: String, home: String, away: String): Match? {
        if (matchId.isBlank()) return null
        val hay = (home + " " + away).lowercase(Locale.ROOT)
        val candidates = allTeams(prefs).filter {
            hay.contains(it.query.lowercase(Locale.ROOT)) || hay.contains(it.arabic.lowercase(Locale.ROOT)) ||
                (it.query == "Manchester United" && hay.contains("manchester")) || (it.query == "Al Ahly" && hay.contains("الاهلي")) ||
                (it.query == "Al Ahly" && hay.contains("الأهلي"))
        }.ifEmpty { allTeams(prefs).filter { prefs.matchFavoriteTeams.contains(it.id) } }
        for (def in candidates) {
            val all = try { next(prefs, def.id) + last(prefs, def.id) } catch (_: Exception) { emptyList() }
            all.firstOrNull { it.id == matchId }?.let { return it }
        }
        return null
    }

    /**
     * Resolve details only when BOTH teams, their home/away orientation, kickoff and
     * any available score agree. A popular club may play several fixtures within a day;
     * matching on one tracked club alone is unsafe and was the cause of borrowed events.
     */
    fun resolveEventId(prefs: Prefs, home: String, away: String, startMs: Long, hs: Int, aw: Int): String {
        configure(prefs)
        if (startMs <= 0L || home.isBlank() || away.isBlank()) return ""
        val teamId = knownIdForName(home).ifBlank { knownIdForName(away) }
        if (teamId.isBlank()) return ""
        val candidates = ArrayList<Match>()
        for ((endpoint, field) in listOf("eventslast.php?id=" to "results", "eventsnext.php?id=" to "events")) {
            val l = try { list(BASE + endpoint + enc(teamId), field) } catch (_: Exception) { emptyList() }
            candidates.addAll(l)
        }
        return candidates.asSequence()
            // Team order + kickoff identify the fixture. A different score is a data conflict,
            // not proof of a different match; the detail screen reconciles the final score.
            .filter { MatchScoreReconciler.isSameFixtureCandidate(home, away, startMs, it.home, it.away, it.startMs) }
            .minByOrNull { kotlin.math.abs(it.startMs - startMs) }
            ?.id.orEmpty()
    }


    private fun markOk(prefs: Prefs) { prefs.matchSourceStatus = prefs.matchSourceStatus.afterSuccess(System.currentTimeMillis()) }
    private fun markFail(prefs: Prefs, e: Exception, hasCache: Boolean) {
        prefs.matchSourceStatus = prefs.matchSourceStatus.afterFailure(
            System.currentTimeMillis(), SourceStatus.safeDetail(e.message ?: e.javaClass.simpleName, activeKey), hasCache)
    }

    /** الأندية الثلاثة المدمجة + الأندية التي أضافها المستخدم. */
    fun allTeams(prefs: Prefs): List<TeamDef> =
        teams + prefs.customClubs.map { TeamDef(it.nameAr, it.nameEn, "", it.id) }

    class ClubHit(val id: String, val name: String, val league: String, val country: String)

    /** بحث نادٍ بالاسم عبر searchteams.php الموثّق؛ يُرجع كرة القدم فقط. يرمي استثناء عند فشل الشبكة. */
    fun searchClubs(prefs: Prefs, name: String): List<ClubHit> {
        configure(prefs)
        val arr = JSONObject(Net.getString(BASE + "searchteams.php?t=" + enc(name.trim()))).optJSONArray("teams") ?: return emptyList()
        val out = ArrayList<ClubHit>()
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            if (!nz(o, "strSport").equals("Soccer", true)) continue
            val id = nz(o, "idTeam"); val n = nz(o, "strTeam")
            if (id.isNotBlank() && n.isNotBlank()) out.add(ClubHit(id, n, nz(o, "strLeague"), nz(o, "strCountry")))
        }
        return out
    }

    // IDs verified against the clubs' TheSportsDB team profiles.
    val teams: List<TeamDef> = listOf(
        TeamDef("الأهلي المصري", "Al Ahly", "Egypt", "138995"),
        TeamDef("مانشستر يونايتد", "Manchester United", "England", "133612"),
        TeamDef("ريال مدريد", "Real Madrid", "Spain", "133738")
    )

    /** معرّفات الفرق ثابتة لتجاوز حد البحث النصي في المفتاح المجاني. */
    fun teamId(prefs: Prefs, def: TeamDef): String {
        prefs.sp.edit().putString("teamid_" + def.query, def.id).apply()
        return def.id
    }

    private fun validImageUrl(value: String): Boolean =
        value.startsWith("https://", true) || value.startsWith("http://", true)

    /** يسترجع شعار الفريق من سجل الفريق عند غيابه من بيانات المباراة، ثم يحفظ رابطه محلياً. */
    fun teamBadge(prefs: Prefs, teamId: String): String {
        if (teamId.isBlank()) return ""
        val key = "team_badge_url_" + teamId
        val retryKey = "team_badge_retry_" + teamId
        val cached = prefs.sp.getString(key, null)
        if (!cached.isNullOrBlank() && cached != NO_BADGE) return cached
        // لا نخزن فشل الشبكة إلى الأبد؛ نعيد المحاولة بعد ساعة فقط لتجنب طلبات متكررة.
        val retryAt = prefs.sp.getLong(retryKey, 0L)
        if (cached == NO_BADGE && System.currentTimeMillis() < retryAt) return ""
        return try {
            val arr = JSONObject(Net.getString(BASE + "lookupteam.php?id=" + enc(teamId))).optJSONArray("teams")
            val team = arr?.optJSONObject(0)
            val candidates = listOf(
                team?.optString("strBadge", "") ?: "",
                team?.optString("strTeamBadge", "") ?: ""
            )
            val result = candidates.firstOrNull { validImageUrl(it) }.orEmpty()
            val editor = prefs.sp.edit()
            if (result.isNotEmpty()) {
                editor.putString(key, result).remove(retryKey)
            } else {
                editor.putString(key, NO_BADGE).putLong(retryKey, System.currentTimeMillis() + 60 * 60_000L)
            }
            editor.apply()
            result
        } catch (_: Exception) {
            // فشل الاتصال مؤقت: لا نحفظه كغياب دائم للشعار.
            prefs.sp.edit().putLong(retryKey, System.currentTimeMillis() + 10 * 60_000L).apply()
            ""
        }
    }

    /** بحث شعار بالاسم (TheSportsDB) مع كاش دائم للنجاح وإعادة محاولة بعد 6 ساعات للفشل. */
    fun badgeByName(prefs: Prefs, name: String): String {
        val clean = name.trim()
        if (clean.length < 3) return ""
        val norm = clean.lowercase(Locale.ROOT).filter { it.isLetterOrDigit() }
        if (norm.length < 3) return ""
        val key = "badge_name_" + norm.take(60)
        val retryKey = "badge_name_retry_" + norm.take(60)
        val cached = prefs.sp.getString(key, null)
        if (!cached.isNullOrBlank() && cached != NO_BADGE) return cached
        if (System.currentTimeMillis() < prefs.sp.getLong(retryKey, 0L)) return ""
        return try {
            configure(prefs)
            val arr = JSONObject(Net.getString(BASE + "searchteams.php?t=" + enc(clean), 9000)).optJSONArray("teams")
            var result = ""
            if (arr != null) {
                for (i in 0 until arr.length()) {
                    val o = arr.optJSONObject(i) ?: continue
                    if (!nz(o, "strSport").equals("Soccer", true)) continue
                    val tn = nz(o, "strTeam").lowercase(Locale.ROOT).filter { it.isLetterOrDigit() }
                    if (tn.isEmpty() || !(tn == norm || tn.contains(norm) || norm.contains(tn))) continue
                    val b = listOf(nz(o, "strBadge"), nz(o, "strTeamBadge")).firstOrNull { validImageUrl(it) }.orEmpty()
                    if (b.isNotEmpty()) { result = b; break }
                }
            }
            val ed = prefs.sp.edit()
            if (result.isNotEmpty()) ed.putString(key, result).remove(retryKey)
            else ed.putString(key, NO_BADGE).putLong(retryKey, System.currentTimeMillis() + 6 * 3600_000L)
            ed.apply()
            result
        } catch (_: Exception) {
            prefs.sp.edit().putLong(retryKey, System.currentTimeMillis() + 10 * 60_000L).apply()
            ""
        }
    }

    /**
     * يكمّل شعارات طرفي المباراة الناقصة: من معرّف الفريق ثم من الاسم. يعمل على خيط خلفي فقط.
     * budget: عدد طلبات البحث بالاسم المسموحة في هذه الدفعة (الباقي يُكمَّل في الفتحة التالية من الكاش).
     */
    fun fillBadges(match0: Match, prefs: Prefs, budget: IntArray): Match {
        val match = withRepoLogos(match0, prefs)
        if (validImageUrl(match.homeBadge) && validImageUrl(match.awayBadge)) return match
        fun one(current: String, id: String, name: String): String {
            if (validImageUrl(current)) return current
            val byId = if (id.isNotBlank()) teamBadge(prefs, id) else ""
            if (byId.isNotEmpty()) return byId
            val cached = prefs.sp.getString("badge_name_" + name.trim().lowercase(Locale.ROOT).filter { it.isLetterOrDigit() }.take(60), null)
            if (!cached.isNullOrBlank() && cached != NO_BADGE) return cached
            if (budget[0] <= 0) return ""
            budget[0]--
            return badgeByName(prefs, name)
        }
        // معرّفات المزودات الأخرى ليست معرّفات TheSportsDB؛ نستعمل معرّف المباراة فقط لمصدرها، وإلا الأندية المعروفة بالاسم
        val tsdb = match.source == "thesportsdb"
        val h = one(match.homeBadge, (if (tsdb) match.homeTeamId else "").ifBlank { knownIdForName(match.home) }, match.home)
        val a = one(match.awayBadge, (if (tsdb) match.awayTeamId else "").ifBlank { knownIdForName(match.away) }, match.away)
        if (h == match.homeBadge && a == match.awayBadge) return match
        return Match(
            match.id, match.home, match.away, match.startMs, match.league,
            match.homeScore, match.awayScore, match.status, match.progress, match.venue,
            h, a, match.homeTeamId, match.awayTeamId, match.fromCache, match.source, match.externalUrl
        )
    }

    private fun knownIdForName(name: String): String {
        val n = name.trim().lowercase(Locale.ROOT)
            .replace('أ', 'ا').replace('إ', 'ا').replace('آ', 'ا')
        if (n.isEmpty()) return ""
        val def = teams.firstOrNull { n == it.query.lowercase(Locale.ROOT) || n == it.arabic.lowercase(Locale.ROOT).replace('أ', 'ا') }
        if (def != null) return def.id
        return when {
            n.contains("al ahly") || n.contains("al-ahly") || n == "الاهلي" || n.startsWith("الاهلي ") && !n.contains("سعود") && !n.contains("جده") -> "138995"
            n.contains("manchester united") || n.contains("مانشستر يونايتد") || n.contains("مان يونايتد") -> "133612"
            n.contains("real madrid") || n.contains("ريال مدريد") -> "133738"
            else -> ""
        }
    }

    /**
     * شعارات المستودعات العامة (تُسحب من النت وتُحفظ محلياً) لها الأولوية على روابط المزوّد لأنها كاملة وعالية الدقة.
     * إن لم يتوفر الفهرس/النادي تبقى شارة المزوّد كما هي. خيط خلفي فقط.
     */
    fun withRepoLogos(match: Match, prefs: Prefs): Match {
        val ctx = prefs.context
        val h = try { FootballLogos.urlFor(ctx, match.home) } catch (_: Exception) { "" }
        val a = try { FootballLogos.urlFor(ctx, match.away) } catch (_: Exception) { "" }
        if (h.isEmpty() && a.isEmpty()) return match
        return Match(
            match.id, match.home, match.away, match.startMs, match.league,
            match.homeScore, match.awayScore, match.status, match.progress, match.venue,
            h.ifEmpty { match.homeBadge }, a.ifEmpty { match.awayBadge }, match.homeTeamId, match.awayTeamId,
            match.fromCache, match.source, match.externalUrl
        )
    }

    /** يضمن وجود شارات للطرفين حتى لو لم يرسلها endpoint المباراة. */
    fun withResolvedBadges(match0: Match, prefs: Prefs): Match {
        val match = withRepoLogos(match0, prefs)
        val homeId = match.homeTeamId.ifBlank { knownIdForName(match.home) }
        val awayId = match.awayTeamId.ifBlank { knownIdForName(match.away) }
        val homeBadge = if (validImageUrl(match.homeBadge)) match.homeBadge else teamBadge(prefs, homeId)
        val awayBadge = if (validImageUrl(match.awayBadge)) match.awayBadge else teamBadge(prefs, awayId)
        return Match(
            match.id, match.home, match.away, match.startMs, match.league,
            match.homeScore, match.awayScore, match.status, match.progress, match.venue,
            homeBadge, awayBadge, homeId, awayId, match.fromCache, match.source, match.externalUrl
        )
    }

    private fun parseTime(o: JSONObject): Long {
        val ts = o.optString("strTimestamp", "")
        if (ts.isNotBlank() && ts != "null") {
            try { return java.time.OffsetDateTime.parse(ts).toInstant().toEpochMilli() } catch (_: Exception) { }
            try { return java.time.Instant.parse(ts).toEpochMilli() } catch (_: Exception) { }
            try {
                val normalized = ts.replace(" ", "T").take(19)
                return LocalDateTime.parse(normalized).toInstant(ZoneOffset.UTC).toEpochMilli()
            } catch (_: Exception) { }
        }
        try {
            val d = o.optString("dateEvent", "")
            val t = o.optString("strTime", "")
            if (d.length >= 10 && d != "null" && t.length >= 5 && t != "null") {
                val clock = if (t.length >= 8) t.take(8) else t.take(5) + ":00"
                return LocalDateTime.parse(d.take(10) + "T" + clock)
                    .toInstant(ZoneOffset.UTC).toEpochMilli()
            }
        } catch (_: Exception) { }
        return 0L
    }

    private fun nz(o: JSONObject, k: String): String {
        val v = o.optString(k, "")
        return if (v == "null") "" else v
    }

    private fun score(o: JSONObject, key: String): Int? {
        val raw = nz(o, key).trim()
        if (raw.isEmpty() || raw.equals("null", true)) return null
        return raw.toIntOrNull()?.takeIf { it in 0..20 }
    }

    private fun toMatch(o: JSONObject): Match = Match(
        nz(o, "idEvent"),
        nz(o, "strHomeTeam"),
        nz(o, "strAwayTeam"),
        parseTime(o),
        nz(o, "strLeague"),
        score(o, "intHomeScore"),
        score(o, "intAwayScore"),
        nz(o, "strStatus"),
        nz(o, "strProgress"),
        nz(o, "strVenue"),
        nz(o, "strHomeTeamBadge"),
        nz(o, "strAwayTeamBadge"),
        nz(o, "idHomeTeam"),
        nz(o, "idAwayTeam")
    )

    private fun list(url: String, field: String): List<Match> {
        val root = JSONObject(Net.getString(url))
        val arr = root.optJSONArray(field) ?: return emptyList()
        val out = ArrayList<Match>()
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val m = toMatch(o)
            if (m.id.isNotBlank() && (m.home.isNotBlank() || m.away.isNotBlank())) out.add(m)
        }
        return out
    }

    private fun cacheKey(kind: String, teamId: String) = "matches_${kind}_$teamId"

    private fun fromJson(o: JSONObject): Match = Match(
        o.optString("id"), o.optString("home"), o.optString("away"), o.optLong("startMs"),
        o.optString("league"), if (o.has("homeScore") && !o.isNull("homeScore")) o.optInt("homeScore").takeIf { it in 0..20 } else null,
        if (o.has("awayScore") && !o.isNull("awayScore")) o.optInt("awayScore").takeIf { it in 0..20 } else null,
        o.optString("status"), o.optString("progress"), o.optString("venue"), o.optString("homeBadge"),
        o.optString("awayBadge"), o.optString("homeTeamId"), o.optString("awayTeamId"), true,
        o.optString("source", "thesportsdb"), o.optString("externalUrl", "")
    )

    private fun saveMatchCache(prefs: Prefs, key: String, items: List<Match>) {
        try {
            val arr = JSONArray()
            for (m in items.take(12)) {
                val o = JSONObject()
                    .put("id", m.id).put("home", m.home).put("away", m.away)
                    .put("startMs", m.startMs).put("league", m.league)
                    .put("status", m.status).put("progress", m.progress).put("venue", m.venue)
                    .put("homeBadge", m.homeBadge).put("awayBadge", m.awayBadge)
                    .put("homeTeamId", m.homeTeamId).put("awayTeamId", m.awayTeamId).put("source", m.source)
                    .put("externalUrl", m.externalUrl)
                if (m.homeScore != null) o.put("homeScore", m.homeScore) else o.put("homeScore", JSONObject.NULL)
                if (m.awayScore != null) o.put("awayScore", m.awayScore) else o.put("awayScore", JSONObject.NULL)
                arr.put(o)
            }
            prefs.sp.edit().putString(key, arr.toString()).apply()
        } catch (_: Exception) { }
    }

    private fun readMatchCache(prefs: Prefs, key: String): List<Match> {
        return try {
            val arr = JSONArray(prefs.sp.getString(key, "[]") ?: "[]")
            (0 until arr.length()).mapNotNull { i ->
                val o = arr.optJSONObject(i) ?: return@mapNotNull null
                val m = fromJson(o)
                if (m.id.isBlank()) null else m
            }
        } catch (_: Exception) { emptyList() }
    }

    private fun mergeAndCache(prefs: Prefs, kind: String, teamId: String, fresh: List<Match>): List<Match> {
        val key = cacheKey(kind, teamId)
        val cached = readMatchCache(prefs, key)
        val byId = LinkedHashMap<String, Match>()
        cached.forEach { byId[it.id] = it }
        for (m in fresh) {
            val old = byId[m.id]
            val mergedScore = if (old == null) m.homeScore to m.awayScore else if (m.finished(System.currentTimeMillis())) {
                if (m.homeScore in 0..20 && m.awayScore in 0..20) m.homeScore to m.awayScore
                else old.homeScore to old.awayScore
            } else com.uobidah.launcher.core.MatchScoreReconciler.mergeLiveScore(
                old.homeScore, old.awayScore, m.homeScore, m.awayScore
            )
            byId[m.id] = if (old == null) m else Match(
                m.id,
                m.home.ifBlank { old.home }, m.away.ifBlank { old.away },
                if (m.startMs > 0L) m.startMs else old.startMs,
                m.league.ifBlank { old.league }, mergedScore.first, mergedScore.second,
                m.status.ifBlank { old.status }, m.progress.ifBlank { old.progress }, m.venue.ifBlank { old.venue },
                m.homeBadge.ifBlank { old.homeBadge }, m.awayBadge.ifBlank { old.awayBadge },
                m.homeTeamId.ifBlank { old.homeTeamId }, m.awayTeamId.ifBlank { old.awayTeamId },
                m.status.isBlank() && m.progress.isBlank() &&
                    ((m.homeScore == null && old.homeScore != null) || (m.awayScore == null && old.awayScore != null)),
                m.source.ifBlank { old.source }, m.externalUrl.ifBlank { old.externalUrl }
            )
        }
        val now = System.currentTimeMillis()
        // Drop stale bucket entries; elapsed time and score alone never move a match to "last".
        val all = byId.values.filter { match ->
            if (kind == "next") match.upcoming(now) || match.live(now) else match.previous(now)
        }
        val sorted = if (kind == "next") all.sortedBy { if (it.startMs > 0L) it.startMs else Long.MAX_VALUE }
            else all.sortedByDescending { it.startMs }
        saveMatchCache(prefs, key, sorted)
        return sorted
    }

    /** يستخدم قائمة المصادر حسب الأولوية، وينتقل تلقائياً للمصدر التالي عند الفشل أو غياب تغطية النادي. */
    fun next(prefs: Prefs, teamId: String): List<Match> = fetchForTeam(prefs, teamId, "next")

    fun last(prefs: Prefs, teamId: String): List<Match> = fetchForTeam(prefs, teamId, "last")

    private fun filterBucket(items: List<Match>, kind: String, now: Long): List<Match> = items.filter { match ->
        if (kind == "next") match.upcoming(now) || match.live(now) else match.previous(now)
    }

    private fun fetchForTeam(prefs: Prefs, teamId: String, kind: String): List<Match> {
        configure(prefs)
        val cacheKey = cacheKey(kind, teamId)
        val now = System.currentTimeMillis()
        val cached = filterBucket(readMatchCache(prefs, cacheKey), kind, now)
        val fetchedAtKey = "match_fetch_at_${kind}_${teamId}"
        val fetchedAt = prefs.sp.getLong(fetchedAtKey, 0L)
        // Reuse a recent result across Activities and the always-on service.
        // Empty results get a shorter hold below to avoid repeated failing requests.
        // وقت المباراة: كاش قصير جداً حتى يصل الهدف خلال ثوانٍ؛ خارج وقت اللعب يبقى الكاش الطويل لتخفيف الحمل.
        val hot = isHot(prefs, teamId, now)
        val reuseMs = if (hot) HOT_REUSE_MS else 120_000L
        if (fetchedAt > 0L && now - fetchedAt in 0..reuseMs && cached.isNotEmpty()) {
            if (kind == "last") {
                val def = allTeams(prefs).firstOrNull { it.id == teamId }
                return crossCheck(prefs, def, cached, cached.firstOrNull()?.source.orEmpty(), now)
            }
            return cached
        }
        if (fetchedAt > 0L && now - fetchedAt in 0..30_000L && cached.isEmpty()) return emptyList()
        val def = allTeams(prefs).firstOrNull { it.id == teamId }
        val attempts = MatchProviders.ordered(prefs)
        var lastError: Exception? = null
        for (provider in attempts) {
            try {
                val all = when (provider) {
                    "thesportsdb" -> {
                        val endpoint = if (kind == "next") "eventsnext.php?id=" else "eventslast.php?id="
                        list(BASE + endpoint + enc(teamId), if (kind == "next") "events" else "results")
                    }
                    "ysscores" -> YallaScoresProvider.fetchTeam(
                        def ?: throw IllegalArgumentException("النادي غير معروف في قائمة المتابعة"), prefs.cityTimezone
                    )
                    "sofascore" -> SofaScoreProvider.fetchTeam(
                        prefs, def ?: throw IllegalArgumentException("النادي غير معروف في قائمة المتابعة")
                    )
                    "espn" -> EspnSoccerProvider.fetchTeam(
                        def ?: throw IllegalArgumentException("النادي غير معروف في قائمة المتابعة"), prefs.cityTimezone
                    )
                    else -> MatchProviders.fetch(prefs, provider)
                }
                val related = if (provider == "thesportsdb" || provider == "ysscores" || provider == "sofascore" || provider == "espn") {
                    if (provider == "espn" && def != null) all.filter {
                        val wanted = def.query.lowercase(Locale.ROOT).filter { ch -> ch.isLetterOrDigit() }
                        val h = it.home.lowercase(Locale.ROOT).filter { ch -> ch.isLetterOrDigit() }
                        val a = it.away.lowercase(Locale.ROOT).filter { ch -> ch.isLetterOrDigit() }
                        wanted.isBlank() || h.contains(wanted) || a.contains(wanted) || wanted.contains(h) || wanted.contains(a)
                    } else all
                } else all.filter { MatchProviders.belongsTo(it, def) }
                val selected = if (kind == "next") {
                    related.filter { it.upcoming(now) || it.live(now) }
                        .sortedBy { if (it.startMs > 0L) it.startMs else Long.MAX_VALUE }
                } else {
                    related.filter { it.previous(now) }
                        .sortedByDescending { it.startMs }
                }
                // صفحة الفريق الصالحة التي لا تحتوي على مباريات في هذا القسم تعني غالباً
                // عدم وجود مباراة قادمة/سابقة، ولا ينبغي تشغيل سلسلة مصادر أخرى بلا داعٍ.
                if (selected.isEmpty() && provider == "ysscores" && related.isNotEmpty()) {
                    prefs.lastMatchProviderId = provider
                    markOk(prefs)
                    prefs.sp.edit().putLong(fetchedAtKey, now).apply()
                    return filterBucket(cached, kind, now)
                }
                if (selected.isEmpty()) {
                    lastError = IllegalStateException("المصدر لا يغطي النادي أو لا توجد مباريات في الفترة المطلوبة: ${MatchProviders.label(provider)}")
                    if (!prefs.matchAutoFailover) break
                    continue
                }
                prefs.lastMatchProviderId = provider
                markOk(prefs)
                val checked = if ((kind == "next" && hot) || kind == "last") crossCheck(prefs, def, selected, provider, now) else selected
                val result = mergeAndCache(prefs, kind, teamId, checked)
                prefs.sp.edit().putLong(fetchedAtKey, now).apply()
                return result
            } catch (e: Exception) {
                lastError = e
                if (!prefs.matchAutoFailover) break
            }
        }
        markFail(prefs, lastError ?: IllegalStateException("لا توجد مصادر متاحة"), cached.isNotEmpty())
        // Short negative cache: avoids retrying every team/provider on every screen redraw.
        prefs.sp.edit().putLong(fetchedAtKey, now).apply()
        return filterBucket(cached, kind, now)
    }

    fun sourceLabel(prefs: Prefs): String = MatchProviders.label(prefs.lastMatchProviderId.ifBlank { prefs.matchProviderId })

    fun event(id: String): Match? {
        if (id.startsWith("sofascore|") || id.startsWith("ysscores|") || id.startsWith("livescorefootball|") || id.startsWith("footylive|") || id.startsWith("flashscore|") || id.startsWith("arabfootball|") || id.startsWith("livescoreswebapp|") || id.startsWith("espn|")) return null
        val l = list(BASE + "lookupevent.php?id=" + enc(id), "events")
        return l.firstOrNull()
    }

    /** التشكيلة من المصدر؛ قد لا تتوفر لكل المباريات في الخطة المجانية. */
    fun lineup(eventId: String): List<LineupPlayer> {
        if (eventId.contains("|")) return emptyList()
        val arr = JSONObject(Net.getString(BASE + "lookuplineup.php?id=" + enc(eventId))).optJSONArray("lineup") ?: return emptyList()
        val out = ArrayList<LineupPlayer>()
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            val name = nz(o, "strPlayer")
            if (name.isEmpty()) continue
            out.add(LineupPlayer(name, nz(o, "strPosition"), nz(o, "strHome").equals("Yes", true), nz(o, "strSubstitute").equals("Yes", true), nz(o, "intSquadNumber")))
        }
        return out
    }

    /** أحداث المباراة (أهداف، بطاقات، تبديلات) إن وفرها المصدر. */
    fun timeline(eventId: String): List<TimelineEvent> {
        if (eventId.contains("|")) return emptyList()
        val arr = JSONObject(Net.getString(BASE + "lookuptimeline.php?id=" + enc(eventId))).optJSONArray("timeline") ?: return emptyList()
        val out = ArrayList<TimelineEvent>()
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            out.add(TimelineEvent(nz(o, "intTime"), nz(o, "strTimeline"), nz(o, "strPlayer"), nz(o, "strHome").equals("Yes", true)))
        }
        return out
    }
}
