package com.uobidah.launcher.core

import java.net.URLDecoder
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.util.Locale

/**
 * Small tolerant HTML reader for the public server-rendered fixture cards.
 * It intentionally reads ordinary anchors and visible card text only; it does not
 * execute page scripts, call hidden endpoints, bypass challenges, or use account data.
 */
/** Reader for the real ysscores server-rendered row markup (ajax-match-item, data-status, span.date headings). */
internal object YallaRealPageParser {
    private class Node(
        val tag: String,
        val attrs: Map<String, String>,
        val parent: Node?
    ) {
        val parts = ArrayList<Any>()
        fun text(): String = parts.joinToString(" ") { part ->
            when (part) { is String -> part; is Node -> part.text(); else -> "" }
        }.replace(Regex("\\s+"), " ").trim()
        fun descendants(tagName: String? = null): List<Node> {
            val out = ArrayList<Node>()
            for (part in parts) if (part is Node) {
                if (tagName == null || part.tag.equals(tagName, true)) out.add(part)
                out.addAll(part.descendants(tagName))
            }
            return out
        }
        fun matchAnchorCount(): Int = descendants("a").count { MATCH_PATH.containsMatchIn(it.attrs["href"].orEmpty()) }
    }

    private val MATCH_PATH = Regex("(?i)(?:^|/)ar/match/(\\d+)(?:/|$)")
    private val DATE_ISO = Regex("(?<!\\d)(\\d{4})[-/](\\d{1,2})[-/](\\d{1,2})(?!\\d)")
    private val DATE_DMY = Regex("(?<!\\d)(\\d{1,2})[-/](\\d{1,2})[-/](\\d{4})(?!\\d)")
    private val TIME = Regex("(?i)(?<!\\d)([01]?\\d|2[0-3]):([0-5]\\d)\\s*(صباحاً|صباحا|مساءً|مساء|ص|م|AM|PM)?")
    private val SCORE = Regex("(?<!\\d)(\\d{1,2})\\s*[-–:]\\s*(\\d{1,2})(?!\\d)")
    private val MINUTE = Regex("(?<!\\d)(\\d{1,3}(?:\\+\\d{1,2})?)[’']")
    private val IANA_ZONE = Regex("(?:Africa|Asia|Europe|America|Australia|Pacific|Atlantic|Indian|Etc)/[A-Za-z0-9_+-]+")
    private val ARABIC_DIGITS = mapOf('٠' to '0', '١' to '1', '٢' to '2', '٣' to '3', '٤' to '4', '٥' to '5', '٦' to '6', '٧' to '7', '٨' to '8', '٩' to '9')
    private val MONTHS = mapOf(
        "يناير" to 1, "فبراير" to 2, "مارس" to 3, "ابريل" to 4, "أبريل" to 4,
        "مايو" to 5, "يونيو" to 6, "يوليو" to 7, "اغسطس" to 8, "أغسطس" to 8,
        "سبتمبر" to 9, "اكتوبر" to 10, "أكتوبر" to 10, "نوفمبر" to 11, "ديسمبر" to 12
    )
    private val TEAM_NAMES = mapOf(
        "alahly" to "الأهلي المصري", "alahlyfc" to "الأهلي المصري", "alahlifc" to "الأهلي المصري",
        "alahlysc" to "الأهلي المصري", "alahlieg" to "الأهلي المصري", "alahli" to "الأهلي المصري",
        "realmadrid" to "ريال مدريد", "real" to "ريال مدريد",
        "manchesterunited" to "مانشستر يونايتد", "manutd" to "مانشستر يونايتد", "manunited" to "مانشستر يونايتد",
        "liverpool" to "ليفربول", "liverpoolfc" to "ليفربول",
        "barcelona" to "برشلونة", "fcbarcelona" to "برشلونة",
        "arsenal" to "أرسنال", "chelsea" to "تشيلسي", "manchestercity" to "مانشستر سيتي",
        "pyramids" to "بيراميدز", "pyramidsfc" to "بيراميدز", "zamalek" to "الزمالك",
        "alittihad" to "الاتحاد", "alittihadfc" to "الاتحاد", "alhilal" to "الهلال"
    )

    /** يقرأ شعار النادي الظاهر في صفحة الفريق من الصورة العامة المعلنة في HTML. */
    fun extractTeamBadge(html: String, aliases: List<String>): String {
        if (html.isBlank() || aliases.isEmpty()) return ""
        val root = parseDocument(html)
        val wanted = aliases.map(::normaliseVisibleName).filter { it.length >= 3 }
        for (img in root.descendants("img")) {
            val alt = normaliseVisibleName(img.attrs["alt"].orEmpty() + " " + img.attrs["title"].orEmpty())
            if (alt.isBlank() || wanted.none { alt.contains(it) || it.contains(alt) }) continue
            val raw = listOf(img.attrs["data-src"], img.attrs["data-original"], img.attrs["src"])
                .firstOrNull { !it.isNullOrBlank() && !it.startsWith("data:", true) }.orEmpty()
            val absolute = when {
                raw.startsWith("https://", true) -> raw
                raw.startsWith("//") -> "https:$raw"
                raw.startsWith("/") -> "https://www.ysscores.com$raw"
                else -> ""
            }
            val uri = runCatching { java.net.URI(absolute) }.getOrNull() ?: continue
            val host = uri.host?.lowercase(Locale.ROOT).orEmpty()
            if (uri.scheme == "https" && (host == "imgs.ysscores.com" || host == "www.ysscores.com" || host == "ysscores.com")) {
                return absolute
            }
        }
        return ""
    }

    /** شعارات كل الفرق الظاهرة في الصفحة (وليس النادي الأساسي فقط): اسم الفريق -> رابط الشعار. يحلّل الصفحة مرة واحدة. */
    fun extractBadgeMap(html: String, names: Collection<String>): Map<String, String> {
        if (html.isBlank() || names.isEmpty()) return emptyMap()
        val root = parseDocument(html)
        val found = ArrayList<Pair<String, String>>()
        for (img in root.descendants("img")) {
            val alt = normaliseVisibleName(img.attrs["alt"].orEmpty() + " " + img.attrs["title"].orEmpty())
            if (alt.isBlank()) continue
            val raw = listOf(img.attrs["data-src"], img.attrs["data-original"], img.attrs["src"])
                .firstOrNull { !it.isNullOrBlank() && !it.startsWith("data:", true) }.orEmpty()
            val absolute = when {
                raw.startsWith("https://", true) -> raw
                raw.startsWith("//") -> "https:$raw"
                raw.startsWith("/") -> "https://www.ysscores.com$raw"
                else -> ""
            }
            val uri = runCatching { java.net.URI(absolute) }.getOrNull() ?: continue
            val host = uri.host?.lowercase(Locale.ROOT).orEmpty()
            if (uri.scheme == "https" && (host == "imgs.ysscores.com" || host == "www.ysscores.com" || host == "ysscores.com")) {
                found.add(alt to absolute)
            }
        }
        val out = HashMap<String, String>()
        for (name in names) {
            val n = normaliseVisibleName(name)
            if (n.length < 3) continue
            val hit = found.firstOrNull { it.first.contains(n) || n.contains(it.first) } ?: continue
            out[name] = hit.second
        }
        return out
    }

    private fun normaliseVisibleName(value: String): String = value.lowercase(Locale.ROOT)
        .replace(Regex("[\u064B-\u065F\u0670]"), "")
        .replace('أ', 'ا').replace('إ', 'ا').replace('آ', 'ا')
        .replace(Regex("[^\\p{L}\\p{N}]"), "")

    fun parseTeamPage(html: String, timezone: String = "Africa/Cairo"): List<YallaParsedMatch> {
        require(html.isNotBlank()) { "صفحة المصدر فارغة" }
        // صفحة YSscores تعرض أوقات المباريات وفق المنطقة الزمنية المختارة في الموقع.
        // نقرأ المنطقة الظاهرة من الصفحة ثم نحوّل إلى UTC؛ وإلا نستخدم منطقة التطبيق.
        val sourceZone = detectSourceZone(html, timezone)
        val root = parseDocument(html)
        val anchors = root.descendants("a").filter { MATCH_PATH.containsMatchIn(it.attrs["href"].orEmpty()) }
        val out = LinkedHashMap<String, YallaParsedMatch>()
        // The live page puts one date header ("span.date") before a group of match rows; the
        // header is a sibling, not an ancestor, so remember the latest header date in document order.
        val headerDate = java.util.IdentityHashMap<Node, LocalDate>()
        var currentHeader: LocalDate? = null
        for (n in root.descendants()) {
            if (n.tag.equals("span", true) && n.attrs["class"].orEmpty().split(' ').contains("date")) {
                parseDate(n.text())?.let { currentHeader = it }
            } else if (n.tag.equals("a", true) && MATCH_PATH.containsMatchIn(n.attrs["href"].orEmpty())) {
                currentHeader?.let { headerDate[n] = it }
            }
        }
        for (anchor in anchors) {
            val href = anchor.attrs["href"].orEmpty()
            val pathMatch = MATCH_PATH.find(href) ?: continue
            val id = pathMatch.groupValues[1]
            if (id.isBlank()) continue
            val slug = href.substringAfter(pathMatch.value).substringBefore('?').substringBefore('#').trim('/')
            val decodedSlug = decode(slug)
            val sides = Regex("(?i)-vs-").split(decodedSlug, limit = 2)
            if (sides.size != 2) continue
            val home = teamName(sides[0])
            val away = teamName(sides[1])
            if (home.isBlank() || away.isBlank()) continue

            val ancestors = generateSequence(anchor.parent) { it.parent }.take(7).toList()
            val singleCard = ancestors.firstOrNull { it.matchAnchorCount() == 1 }
            val datedCard = ancestors.firstOrNull { it.matchAnchorCount() == 1 && parseDate(it.text()) != null }
            val context = (datedCard ?: singleCard ?: anchor).text()
            val linkText = anchor.text()
            val allText = "$context $linkText"
            val date = parseDate(context) ?: parseDate(linkText) ?: headerDate[anchor]
            val anchorNodes = anchor.descendants()
            val minutesAttr = anchorNodes.firstNotNullOfOrNull { n -> n.attrs["data-minutes"]?.trim()?.takeIf { it.matches(Regex("\\d{1,3}")) } }
            // A live row has a match clock ("45:00", "+00:00") instead of a kickoff time: never read it as kickoff.
            val time = if (minutesAttr != null) null else parseTime(allText)
            // A calendar date without an actual kickoff time is not a timestamp.
            // Never fabricate 00:00: it corrupts sorting and cross-provider fixture matching.
            val startMs = if (date != null && time != null && minutesAttr == null) {
                runCatching { LocalDateTime.of(date, time).atZone(sourceZone).toInstant().toEpochMilli() }
                    .getOrDefault(0L)
            } else 0L
            // النتيجة والحالة قد تظهران خارج رابط الفريق داخل نفس بطاقة المباراة.
            val normalizedText = normalizeDigits(allText)
            val scoreText = normalizedText.replace(DATE_ISO, " ").replace(DATE_DMY, " ").replace(TIME, " ")
            // Live rows carry the match clock ("45:00") between the two results; never read it as a score.
            val clockless = scoreText.replace(Regex("(?<!\\d)\\d{2,3}:\\d{2}(?!\\d)"), " ")
            // Prefer the structured result cells of the live/finished row over free text.
            fun cellInt(token: String): Int? = anchorNodes.firstOrNull { n -> n.attrs["class"].orEmpty().split(' ').contains(token) }
                ?.text()?.let { normalizeDigits(it).trim() }?.takeIf { it.matches(Regex("\\d{1,2}")) }?.toIntOrNull()
            val cellHome = cellInt("first-team-result")
            val cellAway = cellInt("second-team-result")
            val score = SCORE.find(clockless)
            // لا نخمن معنى أكواد لم تظهر في عينة المصدر؛ المعاني المثبتة هنا فقط 0 و2 و4.
            // النص المرئي يظل احتياطاً لبقية الحالات.
            val sourceStatus = (listOf(anchor) + ancestors).firstNotNullOfOrNull { node ->
                when (node.attrs["data-status"]?.trim()?.lowercase(Locale.ROOT)) {
                    "0", "coming", "scheduled" -> "scheduled"
                    "2", "live", "inprogress", "in progress" -> "in progress"
                    "4", "end", "ended", "finished" -> "finished"
                    else -> null
                }
            }
            val status = sourceStatus ?: statusOf(allText)
            // النتائج زوج واحد فقط. إذا أعاد المصدر خلية واحدة لا نكمل الخانة الأخرى من نص عام.
            val scorePair = when {
                cellHome != null && cellAway != null -> cellHome to cellAway
                cellHome == null && cellAway == null && score != null ->
                    score.groupValues[1].toIntOrNull()?.let { h -> score.groupValues[2].toIntOrNull()?.let { a -> h to a } }
                else -> null
            }
            val safeScorePair = if (status == "scheduled") null else scorePair
            val progress = MINUTE.find(normalizedText)?.groupValues?.getOrNull(1).orEmpty()
                .ifBlank { when {
                    allText.contains("الشوط الأول") -> "الشوط الأول"
                    allText.contains("الشوط الثاني") -> "الشوط الثاني"
                    allText.contains("نهاية الشوط الأول") -> "استراحة بين الشوطين"
                    else -> ""
                } }
            val league = ancestors.asSequence().flatMap { it.descendants("a").asSequence() }
                .firstOrNull { it.attrs["href"].orEmpty().contains("/championship/") }
                ?.text().orEmpty()
            val absolute = if (href.startsWith("http://") || href.startsWith("https://")) href
                else "https://www.ysscores.com" + (if (href.startsWith("/")) href else "/$href")
            // Team logos sit inside the row link, home first then away (same order as the URL slug).
            val rowBadges = anchorNodes.filter { it.tag.equals("img", true) }.mapNotNull { img ->
                val raw = listOf(img.attrs["data-src"], img.attrs["data-original"], img.attrs["src"])
                    .firstOrNull { !it.isNullOrBlank() && !it.startsWith("data:", true) }.orEmpty()
                val abs = when { raw.startsWith("https://", true) -> raw; raw.startsWith("//") -> "https:$raw"; else -> "" }
                val host = runCatching { java.net.URI(abs).host?.lowercase(Locale.ROOT).orEmpty() }.getOrDefault("")
                abs.takeIf { it.isNotEmpty() && (host == "imgs.ysscores.com" || host == "www.ysscores.com" || host == "ysscores.com") }
            }
            val rowHomeBadge = if (rowBadges.size >= 2) rowBadges[0] else ""
            val rowAwayBadge = if (rowBadges.size >= 2) rowBadges[1] else ""
            val parsed = YallaParsedMatch(
                id = id,
                url = absolute,
                home = home,
                away = away,
                startMs = startMs,
                league = league,
                homeScore = safeScorePair?.first,
                awayScore = safeScorePair?.second,
                status = status,
                progress = if (progress.isBlank() && minutesAttr != null) "$minutesAttr'" else progress,
                homeBadge = rowHomeBadge,
                awayBadge = rowAwayBadge
            )
            val previous = out[id]
            if (previous == null) {
                out[id] = parsed
            } else {
                // Responsive desktop/mobile copies may share an ID. Fill missing fields only;
                // if two copies disagree on a complete score pair, suppress the score.
                val sameTeams = normaliseVisibleName(previous.home) == normaliseVisibleName(parsed.home) &&
                    normaliseVisibleName(previous.away) == normaliseVisibleName(parsed.away)
                if (!sameTeams) continue
                val oldPair = if (previous.homeScore in 0..20 && previous.awayScore in 0..20) previous.homeScore!! to previous.awayScore!! else null
                val newPair = if (parsed.homeScore in 0..20 && parsed.awayScore in 0..20) parsed.homeScore!! to parsed.awayScore!! else null
                val statusConflict = previous.status.isNotBlank() && parsed.status.isNotBlank() && previous.status != parsed.status
                val mergedPair = when {
                    statusConflict -> null
                    oldPair == null -> newPair
                    newPair == null || newPair == oldPair -> oldPair
                    else -> null
                }
                val oldStart = previous.startMs.takeIf { it > 0L }
                val newStart = parsed.startMs.takeIf { it > 0L }
                val mergedStart = when {
                    oldStart == null -> newStart ?: 0L
                    newStart == null || kotlin.math.abs(newStart - oldStart) <= 2 * 60 * 60_000L -> oldStart
                    else -> 0L
                }
                // Conflicting copies of one fixture must not let a stronger-looking state win.
                // Keep the known state only when the other copy is blank or agrees; otherwise
                // mark it unknown and let the primary feed resolve it on a later refresh.
                val mergedStatus = when {
                    previous.status.isBlank() -> parsed.status
                    parsed.status.isBlank() || parsed.status == previous.status -> previous.status
                    else -> ""
                }
                out[id] = previous.copy(
                    startMs = mergedStart,
                    league = previous.league.ifBlank { parsed.league },
                    homeScore = mergedPair?.first,
                    awayScore = mergedPair?.second,
                    status = mergedStatus,
                    progress = previous.progress.ifBlank { parsed.progress },
                    homeBadge = previous.homeBadge.ifBlank { parsed.homeBadge },
                    awayBadge = previous.awayBadge.ifBlank { parsed.awayBadge }
                )
            }
        }
        return out.values.toList()
    }


    private fun detectSourceZone(html: String, fallback: String): ZoneId {
        val pageText = html.replace(Regex("(?is)<script\\b[^>]*>.*?</script>"), " ")
            .replace(Regex("(?is)<style\\b[^>]*>.*?</style>"), " ")
        val visible = clean(pageText)
        val candidate = IANA_ZONE.find(visible)?.value
        return runCatching { ZoneId.of(candidate ?: fallback) }
            .getOrElse { runCatching { ZoneId.of(fallback) }.getOrDefault(ZoneId.of("Africa/Cairo")) }
    }

    private fun parseDocument(raw: String): Node {
        val html = raw.replace(Regex("(?is)<script\\b[^>]*>.*?</script>"), " ")
            .replace(Regex("(?is)<style\\b[^>]*>.*?</style>"), " ")
        val root = Node("root", emptyMap(), null)
        val stack = ArrayList<Node>()
        stack.add(root)
        val token = Regex("(?is)<!--.*?-->|<![^>]*>|</?[a-zA-Z][^>]*>|[^<]+")
        val voidTags = setOf("area", "base", "br", "col", "embed", "hr", "img", "input", "link", "meta", "param", "source", "track", "wbr")
        for (match in token.findAll(html)) {
            val value = match.value
            if (value.startsWith("<!--") || value.startsWith("<!")) continue
            if (value.startsWith("</")) {
                val name = Regex("</\\s*([A-Za-z0-9]+)").find(value)?.groupValues?.getOrNull(1) ?: continue
                val index = stack.indexOfLast { it.tag.equals(name, true) }
                if (index > 0) while (stack.size > index) stack.removeAt(stack.lastIndex)
            } else if (value.startsWith("<")) {
                val tag = Regex("<\\s*([A-Za-z0-9]+)").find(value)?.groupValues?.getOrNull(1)?.lowercase(Locale.ROOT) ?: continue
                val attrs = parseAttributes(value)
                val node = Node(tag, attrs, stack.last())
                stack.last().parts.add(node)
                if (tag !in voidTags && !value.endsWith("/>")) stack.add(node)
            } else {
                stack.last().parts.add(decodeEntities(value))
            }
        }
        return root
    }

    private fun parseAttributes(tag: String): Map<String, String> {
        val out = LinkedHashMap<String, String>()
        val regex = Regex("(?is)([a-z_:][a-z0-9_:.\\-]*)\\s*=\\s*(?:\"([^\"]*)\"|'([^']*)'|([^\\s>]+))")
        for (m in regex.findAll(tag)) {
            val key = m.groupValues[1].lowercase(Locale.ROOT)
            val value = listOf(m.groupValues[2], m.groupValues[3], m.groupValues[4]).firstOrNull { it.isNotEmpty() }.orEmpty()
            out[key] = decodeEntities(value)
        }
        return out
    }

    private fun decodeEntities(value: String): String {
        val named = mapOf("amp" to "&", "quot" to "\"", "apos" to "'", "lt" to "<", "gt" to ">", "nbsp" to " ", "ndash" to "–", "mdash" to "—", "rsquo" to "’", "lsquo" to "‘", "rdquo" to "”", "ldquo" to "“")
        return Regex("&(#x[0-9a-fA-F]+|#\\d+|[A-Za-z]+);").replace(value) { m ->
            val key = m.groupValues[1]
            when {
                key.startsWith("#x", true) -> key.drop(2).toIntOrNull(16)?.takeIf { it in 0..0x10FFFF && it !in 0xD800..0xDFFF }?.let { String(Character.toChars(it)) } ?: m.value
                key.startsWith("#") -> key.drop(1).toIntOrNull()?.takeIf { it in 0..0x10FFFF && it !in 0xD800..0xDFFF }?.let { String(Character.toChars(it)) } ?: m.value
                else -> named[key]?.toString() ?: m.value
            }
        }
    }

    private fun clean(raw: String): String = decodeEntities(raw)
        .replace(Regex("(?is)<br\\s*/?>"), " ")
        .replace(Regex("(?is)</(?:div|p|li|h[1-6]|section|article|tr|td|th|header|footer)>"), " ")
        .replace(Regex("(?is)<[^>]+>"), " ")
        .replace(Regex("\\s+"), " ").trim()

    private fun decode(value: String): String = runCatching { URLDecoder.decode(value.replace("+", "%2B"), "UTF-8") }.getOrDefault(value)

    private fun teamName(raw: String): String {
        val decoded = decode(raw).replace('-', ' ').replace('_', ' ').trim()
        val key = normalizeTeam(decoded)
        TEAM_NAMES[key]?.let { return it }
        return decoded.split(Regex("\\s+")).filter { it.isNotBlank() }.joinToString(" ") { word ->
            word.lowercase(Locale.ROOT).replaceFirstChar { it.titlecase(Locale.ROOT) }
        }
    }

    private fun normalizeTeam(value: String): String = value.lowercase(Locale.ROOT)
        .replace(Regex("[^a-z0-9]"), "")
        .removeSuffix("footballclub").removeSuffix("football").removeSuffix("club").removeSuffix("fc").removeSuffix("sc")

    private fun normalizeDigits(value: String): String = buildString(value.length) {
        for (c in value) append(ARABIC_DIGITS[c] ?: c)
    }

    private fun parseDate(text: String): LocalDate? {
        val normalized = normalizeDigits(text)
        DATE_ISO.find(normalized)?.let { m ->
            val y = m.groupValues[1].toIntOrNull() ?: return@let
            val mo = m.groupValues[2].toIntOrNull() ?: return@let
            val d = m.groupValues[3].toIntOrNull() ?: return@let
            runCatching { return LocalDate.of(y, mo, d) }
        }
        DATE_DMY.find(normalized)?.let { m ->
            val d = m.groupValues[1].toIntOrNull() ?: return@let
            val mo = m.groupValues[2].toIntOrNull() ?: return@let
            val y = m.groupValues[3].toIntOrNull() ?: return@let
            runCatching { return LocalDate.of(y, mo, d) }
        }
        val monthRegex = Regex("(?iu)(\\d{1,2})\\s+([\\p{L}]+)\\s+(\\d{4})")
        monthRegex.find(normalized)?.let { m ->
            val d = m.groupValues[1].toIntOrNull() ?: return@let
            val monthName = m.groupValues[2]
            val month = MONTHS[monthName] ?: MONTHS.entries.firstOrNull { it.key.equals(monthName, true) }?.value ?: return@let
            val year = m.groupValues[3].toIntOrNull() ?: return@let
            runCatching { return LocalDate.of(year, month, d) }
        }
        return null
    }

    private fun parseTime(text: String): LocalTime? {
        val normalized = normalizeDigits(text)
        val m = TIME.find(normalized) ?: return null
        var hour = m.groupValues[1].toIntOrNull() ?: return null
        val minute = m.groupValues[2].toIntOrNull() ?: return null
        val marker = m.groupValues[3].uppercase(Locale.ROOT)
        val isPm = marker in setOf("م", "مساء", "مساءً", "PM")
        val isAm = marker in setOf("ص", "صباحا", "صباحاً", "AM")
        if (isPm && hour < 12) hour += 12
        if (isAm && hour == 12) hour = 0
        return runCatching { LocalTime.of(hour, minute) }.getOrNull()
    }

    private fun statusOf(text: String): String {
        val t = text.lowercase(Locale.ROOT)
        return when {
            listOf("تأجل", "مؤجل", "postponed", "delayed").any { t.contains(it) } -> "postponed"
            listOf("ملغاة", "ألغيت", "cancelled", "canceled", "abandoned").any { t.contains(it) } -> "canceled"
            listOf("انتهت المباراة", "انتهت", "full time", "finished", "match ended").any { t.contains(it) } -> "finished"
            listOf("مباشر", "جارية", "جاري", "الشوط الأول", "الشوط الثاني", "in progress", "live", "half time").any { t.contains(it) } -> "in progress"
            else -> "scheduled"
        }
    }
}
