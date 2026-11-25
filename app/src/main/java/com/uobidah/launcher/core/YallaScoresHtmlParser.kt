package com.uobidah.launcher.core

import java.net.URLDecoder
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.util.Locale

/** Parsed public fixture from a YSscores team page. No private endpoint or login is used. */
data class YallaParsedMatch(
    val id: String,
    val url: String,
    val home: String,
    val away: String,
    val startMs: Long,
    val league: String,
    val homeScore: Int?,
    val awayScore: Int?,
    val status: String,
    val progress: String,
    val venue: String = "",
    val homeBadge: String = "",
    val awayBadge: String = ""
)

/**
 * Small tolerant HTML reader for the public server-rendered fixture cards.
 * It intentionally reads ordinary anchors and visible card text only; it does not
 * execute page scripts, call hidden endpoints, bypass challenges, or use account data.
 */
object YallaScoresHtmlParser {
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
        /** Count distinct fixtures, not responsive/mobile duplicate anchors for one fixture. */
        fun matchAnchorCount(): Int = descendants("a")
            .mapNotNull { MATCH_PATH.find(it.attrs["href"].orEmpty())?.groupValues?.getOrNull(1) }
            .distinct()
            .count()
    }

    private val MATCH_PATH = Regex("(?i)(?:^|/)ar/match/(\\d+)(?:/|$)")
    private val DATE_ISO = Regex("(?<!\\d)(\\d{4})[-/](\\d{1,2})[-/](\\d{1,2})(?!\\d)")
    private val DATE_DMY = Regex("(?<!\\d)(\\d{1,2})[-/](\\d{1,2})[-/](\\d{4})(?!\\d)")
    private val TIME = Regex("(?i)(?<!\\d)([01]?\\d|2[0-3])[:.]([0-5]\\d)\\s*(صباحاً|صباحا|مساءً|مساء|ص|م|AM|PM)?")
    private val MARKED_SCORE = Regex("(?<![\\d:])(\\d{1,2})\\s*[-–—:]\\s*(\\d{1,2})(?![\\d:])")
    private val MINUTE = Regex("(?iu)(?<!\\d)(\\d{1,3}(?:\\s*\\+\\s*\\d{1,2})?)\\s*(?:[’'′´]|د(?:قيقة)?|min(?:ute)?\\b)")
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
        "psv" to "PSV Eindhoven", "psveindhoven" to "PSV Eindhoven", "psveindhovenfc" to "PSV Eindhoven",
        "eindhoven" to "PSV Eindhoven",
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

    fun parseTeamPage(
        html: String,
        timezone: String = "Africa/Cairo",
        referenceDate: LocalDate? = null
    ): List<YallaParsedMatch> {
        require(html.isNotBlank()) { "صفحة المصدر فارغة" }
        // Real ysscores pages use ajax-match-item rows with data-status and span.date headings;
        // read those with the structure-aware reader first, then fall back to the generic reader.
        if (html.contains("ajax-match-item")) {
            val real = runCatching { YallaRealPageParser.parseTeamPage(html, timezone) }.getOrDefault(emptyList())
            if (real.isNotEmpty()) return real
        }
        // صفحة YSscores تعرض أوقات المباريات وفق المنطقة الزمنية المختارة في الموقع.
        // نقرأ المنطقة الظاهرة من الصفحة ثم نحوّل إلى UTC؛ وإلا نستخدم منطقة التطبيق.
        val sourceZone = detectSourceZone(html, timezone)
        // A few fixture pages omit the year in day headings. Anchor those dates to
        // the local source day (injectable in tests) and select the nearest year.
        val dateReference = referenceDate ?: LocalDate.now(sourceZone)
        val root = parseDocument(html)
        val anchors = root.descendants("a").filter { MATCH_PATH.containsMatchIn(it.attrs["href"].orEmpty()) }
        val out = LinkedHashMap<String, YallaParsedMatch>()
        for (anchor in anchors) {
            val href = anchor.attrs["href"].orEmpty()
            val pathMatch = MATCH_PATH.find(href) ?: continue
            val id = pathMatch.groupValues[1]
            if (id.isBlank() || out.containsKey(id)) continue
            val slug = href.substringAfter(pathMatch.value).substringBefore('?').substringBefore('#').trim('/')
            val decodedSlug = decode(slug)
            val sides = Regex("(?i)-vs-").split(decodedSlug, limit = 2)
            if (sides.size != 2) continue
            val home = teamName(sides[0])
            val away = teamName(sides[1])
            if (home.isBlank() || away.isBlank()) continue

            val ancestors = generateSequence(anchor.parent) { it.parent }.take(7).toList()
            val singleCard = ancestors.firstOrNull { it.matchAnchorCount() == 1 }
            val datedCard = ancestors.firstOrNull {
                it.matchAnchorCount() == 1 && dateFromNode(it, dateReference) != null
            }
            val contextNode = datedCard ?: singleCard ?: anchor
            val context = contextNode.text()
            val linkText = anchor.text()
            val allText = "$context $linkText"
            // Date headers often sit immediately before a group of match cards rather
            // than inside each card. Search preceding siblings on the anchor's DOM path,
            // while refusing to inherit dates from previous match cards.
            val dayHeaderDate = previousDayHeaderDate(anchor, dateReference)
            val date = dateFromNode(contextNode, dateReference)
                ?: parseDate(linkText, dateReference, allowYearless = true)
                ?: dayHeaderDate
            val cardNode = datedCard ?: singleCard ?: anchor
            val time = parseTimeFromCard(cardNode)
            // A date without a confirmed kickoff time is not midnight. Keep the start unknown
            // rather than shifting the fixture into the wrong day/bucket.
            val startMs = if (date != null && time != null) {
                runCatching { LocalDateTime.of(date, time).atZone(sourceZone).toInstant().toEpochMilli() }
                    .getOrDefault(0L)
            } else 0L
            // النتيجة والحالة قد تظهران خارج رابط الفريق داخل نفس بطاقة المباراة.
            val normalizedText = normalizeDigits(allText)
            val status = statusOf(cardNode, allText)
            val progress = MINUTE.find(normalizedText)?.groupValues?.getOrNull(1)?.replace(Regex("\\s+"), "") .orEmpty()
                .ifBlank { when {
                    MatchStatusNormalizer.normalize(allText).contains("نهاية الشوط الاول") -> "استراحة بين الشوطين"
                    MatchStatusNormalizer.normalize(allText).contains("الشوط الاول") -> "الشوط الأول"
                    MatchStatusNormalizer.normalize(allText).contains("الشوط الثاني") -> "الشوط الثاني"
                    else -> ""
                } }
            val score = extractScoreFromCard(datedCard ?: singleCard ?: anchor)
            val league = ancestors.asSequence().flatMap { it.descendants("a").asSequence() }
                .firstOrNull { it.attrs["href"].orEmpty().contains("/championship/") }
                ?.text().orEmpty()
            val absolute = if (href.startsWith("http://") || href.startsWith("https://")) href
                else "https://www.ysscores.com" + (if (href.startsWith("/")) href else "/$href")
            out[id] = YallaParsedMatch(
                id = id,
                url = absolute,
                home = home,
                away = away,
                startMs = startMs,
                league = league,
                homeScore = score?.home,
                awayScore = score?.away,
                status = status,
                progress = progress
            )
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

    private fun parseDate(
        text: String,
        referenceDate: LocalDate,
        allowYearless: Boolean = false
    ): LocalDate? {
        val normalized = normalizeDigits(text)
        DATE_ISO.find(normalized)?.let { m ->
            val y = m.groupValues[1].toIntOrNull() ?: return@let
            val mo = m.groupValues[2].toIntOrNull() ?: return@let
            val d = m.groupValues[3].toIntOrNull() ?: return@let
            runCatching { return LocalDate.of(y, mo, d) }
        }
        DATE_DMY.find(normalized)?.let { m ->
            val a = m.groupValues[1].toIntOrNull() ?: return@let
            val b = m.groupValues[2].toIntOrNull() ?: return@let
            val y = m.groupValues[3].toIntOrNull() ?: return@let
            // Arabic regional pages are normally D/M/Y. When one component exceeds 12,
            // infer the order unambiguously (supports MM/DD/YYYY feeds too).
            val (d, mo) = when {
                a > 12 && b in 1..12 -> a to b
                b > 12 && a in 1..12 -> b to a
                else -> a to b
            }
            runCatching { return LocalDate.of(y, mo, d) }
        }

        // Arabic/English day headings frequently omit the year, and English headings
        // often use ordinals (Tuesday, November 24th). Accept both D-Month and Month-D.
        val monthDmy = Regex("(?iu)(?<!\\d)(\\d{1,2})(?:st|nd|rd|th)?\\s+(?:من\\s+)?([\\p{L}]+)(?:\\s*,?\\s*(\\d{4}))?(?!\\d)")
        val monthMdy = Regex("(?iu)([\\p{L}]+)\\s+(\\d{1,2})(?:st|nd|rd|th)?(?:,?\\s*(\\d{4}))?")
        for ((pattern, dayFirst) in listOf(monthDmy to true, monthMdy to false)) {
            pattern.find(normalized)?.let { m ->
                val day = (if (dayFirst) m.groupValues[1] else m.groupValues[2]).toIntOrNull() ?: return@let
                val monthName = if (dayFirst) m.groupValues[2] else m.groupValues[1]
                val yearText = m.groupValues[3]
                val month = monthNumber(monthName) ?: return@let
                val explicitYear = yearText.toIntOrNull()
                if (explicitYear == null && !allowYearless) return@let
                val date = if (explicitYear != null) {
                    runCatching { LocalDate.of(explicitYear, month, day) }.getOrNull()
                } else nearestYearDate(month, day, referenceDate)
                if (date != null) return date
            }
        }

        if (allowYearless) {
            // Compact Arabic day headings such as "24/11" or "24-11" use D/M order.
            val short = Regex("(?<!\\d)(\\d{1,2})[-/](\\d{1,2})(?![-/]\\d)").find(normalized)
            if (short != null) {
                val day = short.groupValues[1].toIntOrNull() ?: return null
                val month = short.groupValues[2].toIntOrNull() ?: return null
                return nearestYearDate(month, day, referenceDate)
            }
            val label = MatchStatusNormalizer.normalize(normalized)
            when {
                Regex("(^|[^\\p{L}])(?:اليوم|today)([^\\p{L}]|$)").containsMatchIn(label) -> return referenceDate
                Regex("(^|[^\\p{L}])(?:غدا|غداً|tomorrow)([^\\p{L}]|$)").containsMatchIn(label) -> return referenceDate.plusDays(1)
                Regex("(^|[^\\p{L}])(?:امس|أمس|yesterday)([^\\p{L}]|$)").containsMatchIn(label) -> return referenceDate.minusDays(1)
            }
        }
        return null
    }

    private fun nearestYearDate(month: Int, day: Int, referenceDate: LocalDate): LocalDate? {
        if (month !in 1..12 || day !in 1..31) return null
        return (referenceDate.year - 1..referenceDate.year + 1)
            .mapNotNull { year -> runCatching { LocalDate.of(year, month, day) }.getOrNull() }
            .minByOrNull { kotlin.math.abs(java.time.temporal.ChronoUnit.DAYS.between(referenceDate, it)) }
    }

    /**
     * Finds the nearest date-bearing sibling before a fixture on its DOM ancestry path.
     * A previous fixture card is never treated as a day heading, preventing date leakage
     * when pages contain multiple day groups.
     */
    private fun previousDayHeaderDate(anchor: Node, referenceDate: LocalDate): LocalDate? {
        var branch: Node = anchor
        var parent = anchor.parent
        var depth = 0
        while (parent != null && depth < 12) {
            val branchIndex = parent.parts.indexOfFirst { it === branch }
            if (branchIndex >= 0) {
                for (i in branchIndex - 1 downTo 0) {
                    when (val sibling = parent.parts[i]) {
                        is Node -> findDateInsideHeader(sibling, referenceDate)?.let { return it }
                        is String -> parseDate(sibling, referenceDate, allowYearless = true)?.let { return it }
                    }
                }
            }
            branch = parent
            parent = parent.parent
            depth++
        }
        return null
    }

    private fun dateFromNode(node: Node, referenceDate: LocalDate): LocalDate? {
        val declared = listOf(
            "data-date", "datetime", "content", "title", "data-day", "data-match-date",
            "data-kickoff-date", "date-time"
        )
        for (key in declared) {
            val value = node.attrs[key].orEmpty()
            if (value.isNotBlank()) parseDate(value, referenceDate, allowYearless = true)?.let { return it }
        }
        return parseDate(node.text(), referenceDate, allowYearless = true)
    }

    /**
     * Search a preceding wrapper for an actual date heading even when the wrapper also
     * contains navigation/duplicate match links. Never parse dates from a fixture card:
     * those are handled through their own single-fixture ancestor.
     */
    private fun findDateInsideHeader(node: Node, referenceDate: LocalDate): LocalDate? {
        // Never borrow a date from a preceding day container that already owns fixtures.
        // Header wrappers may contain ordinary navigation links, but no match-page anchors.
        if (node.matchAnchorCount() > 0) return null
        val candidates = listOf(node) + node.descendants().asReversed()
        for (candidate in candidates) {
            if (candidate.matchAnchorCount() > 0) continue
            val marker = listOf("class", "id", "data-testid", "data-role", "aria-label")
                .joinToString(" ") { candidate.attrs[it].orEmpty() }.lowercase(Locale.ROOT)
            val isHeading = candidate.tag in setOf("h1", "h2", "h3", "h4", "h5", "h6", "time") ||
                Regex("(^|[^a-z])(?:day|date|heading|header|title)([^a-z]|$)").containsMatchIn(marker) ||
                marker.contains("اليوم") || marker.contains("التاريخ")
            val hasExplicitDate = listOf("data-date", "datetime", "data-day", "data-match-date", "data-kickoff-date")
                .any { !candidate.attrs[it].isNullOrBlank() }
            if (!isHeading && !hasExplicitDate && candidate !== node) continue
            dateFromNode(candidate, referenceDate)?.let { return it }
        }
        return null
    }

    private fun monthNumber(raw: String): Int? {
        val name = MatchStatusNormalizer.normalize(raw).replace("أ", "ا").replace("إ", "ا")
        val arabic = mapOf(
            "يناير" to 1, "فبراير" to 2, "مارس" to 3, "ابريل" to 4, "مايو" to 5, "يونيو" to 6,
            "يوليو" to 7, "اغسطس" to 8, "سبتمبر" to 9, "اكتوبر" to 10, "نوفمبر" to 11, "ديسمبر" to 12
        )
        val english = mapOf(
            "january" to 1, "jan" to 1, "february" to 2, "feb" to 2, "march" to 3, "mar" to 3,
            "april" to 4, "apr" to 4, "may" to 5, "june" to 6, "jun" to 6,
            "july" to 7, "jul" to 7, "august" to 8, "aug" to 8, "september" to 9, "sep" to 9, "sept" to 9,
            "october" to 10, "oct" to 10, "november" to 11, "nov" to 11, "december" to 12, "dec" to 12
        )
        return arabic[name] ?: english[name]
    }

    /** Prefer a DOM-declared kickoff/time field; remove marked score nodes before using visible fallback text. */
    private fun parseTimeFromCard(card: Node): LocalTime? {
        val nodes = listOf(card) + card.descendants()
        for (node in nodes) {
            val marker = listOf("class", "id", "data-testid", "itemprop", "data-role")
                .joinToString(" ") { node.attrs[it].orEmpty() }.lowercase(Locale.ROOT)
            val isTimeNode = Regex("(^|[^a-z])(?:time|kickoff|match-time|start-time)([^a-z]|$)").containsMatchIn(marker) || node.tag == "time"
            if (!isTimeNode) continue
            val raw = node.attrs["datetime"] ?: node.attrs["data-time"] ?: node.attrs["content"] ?: node.text()
            parseTime(raw)?.let { return it }
        }

        // Avoid reading 2:1 / 1:0 score-shaped fragments as 02:01 / 01:00 kickoffs.
        val textWithoutScoreNodes = textWithoutMarkedScoreNodes(card)
        val normalized = normalizeDigits(textWithoutScoreNodes)
        val candidate = TIME.find(normalized)
        if (candidate != null && candidate.groupValues[3].isBlank()) {
            val hour = candidate.groupValues[1].toIntOrNull() ?: -1
            val minute = candidate.groupValues[2].toIntOrNull() ?: -1
            if (hour in 0..3 && minute in 0..20) return null
        }
        return parseTime(textWithoutScoreNodes)
    }

    private fun textWithoutMarkedScoreNodes(node: Node): String {
        val marker = listOf("class", "id", "data-testid", "itemprop", "aria-label", "data-role")
            .joinToString(" ") { node.attrs[it].orEmpty() }.lowercase(Locale.ROOT)
        val tokens = marker.split(Regex("[^a-z0-9\\p{L}]+" )).filter { it.isNotBlank() }
        val isScore = (tokens.any { it in setOf("score", "result", "goals", "matchscore", "scorevalue", "scoreboard") } ||
            marker.contains("نتيجة") || marker.contains("الاهداف") || marker.contains("الأهداف")) &&
            tokens.none { it in setOf("card", "container", "row", "list") }
        if (isScore) return " "
        return node.parts.joinToString(" ") { part -> if (part is String) part else textWithoutMarkedScoreNodes(part as Node) }
    }

    private fun parseTime(text: String): LocalTime? {
        val normalized = normalizeDigits(text)
        val m = TIME.find(normalized) ?: return null
        var hour = m.groupValues[1].toIntOrNull() ?: return null
        val minute = m.groupValues[2].toIntOrNull() ?: return null
        val marker = MatchStatusNormalizer.normalize(m.groupValues[3]).uppercase(Locale.ROOT)
        val isPm = marker in setOf("م", "مساء", "PM")
        val isAm = marker in setOf("ص", "صباحا", "AM")
        if (isPm && hour < 12) hour += 12
        if (isAm && hour == 12) hour = 0
        return runCatching { LocalTime.of(hour, minute) }.getOrNull()
    }

    /** Scores are read only from score/result markup or explicit score attributes. */
    private fun extractScoreFromCard(card: Node): MatchScore? {
        val nodes = listOf(card) + card.descendants()

        fun valid(value: String?): Int? = value?.trim()?.toIntOrNull()?.takeIf { it in 0..20 }
        val homeKeys = listOf("data-home-score", "data-homescore", "data-score-home", "home_score")
        val awayKeys = listOf("data-away-score", "data-awayscore", "data-score-away", "away_score")
        val homeAttr = nodes.firstNotNullOfOrNull { node -> homeKeys.firstNotNullOfOrNull { valid(node.attrs[it]) } }
        val awayAttr = nodes.firstNotNullOfOrNull { node -> awayKeys.firstNotNullOfOrNull { valid(node.attrs[it]) } }
        if (homeAttr != null && awayAttr != null) return MatchScore(homeAttr, awayAttr)

        // Combined values are accepted only when the HTML explicitly labels the attribute as a score/result.
        val combinedKeys = listOf("data-score", "data-result", "data-match-score", "data-full-score", "data-ft-score")
        for (node in nodes) for (key in combinedKeys) {
            parseMarkedScoreText(node.attrs[key].orEmpty())?.let { return it }
        }

        // Some sites expose the two sides as separate semantic nodes (home-score / away-score).
        fun sideValue(wanted: Regex): Int? = nodes.firstNotNullOfOrNull { node ->
            val marker = listOf("class", "id", "data-testid", "itemprop", "data-role")
                .joinToString(" ") { node.attrs[it].orEmpty() }.lowercase(Locale.ROOT)
            if (!wanted.containsMatchIn(marker)) null
            else valid(node.attrs["data-value"] ?: node.attrs["content"] ?: node.text())
        }
        val homeNode = sideValue(Regex("(?:^|[\\s_-])(?:home|host)[_-]?(?:score|goals?)(?:$|[\\s_-])|(?:^|[\\s_-])score[_-]home(?:$|[\\s_-])"))
        val awayNode = sideValue(Regex("(?:^|[\\s_-])(?:away|guest)[_-]?(?:score|goals?)(?:$|[\\s_-])|(?:^|[\\s_-])score[_-]away(?:$|[\\s_-])"))
        if (homeNode != null && awayNode != null) return MatchScore(homeNode, awayNode)

        // A combined visible score is safe only inside an explicitly marked score/result element.
        val marked = nodes.filter { node ->
            val marker = listOf("class", "id", "data-testid", "itemprop", "aria-label", "data-role")
                .joinToString(" ") { node.attrs[it].orEmpty() }.lowercase(Locale.ROOT)
            val tokens = marker.split(Regex("[^a-z0-9\\p{L}]+" )).filter { it.isNotBlank() }
            (tokens.any { it in setOf("score", "result", "goals", "matchscore", "scorevalue", "scoreboard") } ||
                marker.contains("نتيجة") || marker.contains("الاهداف") || marker.contains("الأهداف")) &&
                tokens.none { it in setOf("card", "container", "row", "list") }
        }
        for (node in marked) {
            parseMarkedScoreText(node.text())?.let { return it }
            // Some providers render 0 and 1 in separate child spans without a dash.
            // Accept that only when this explicitly score-labelled node has exactly
            // two numeric leaves; never scan arbitrary card statistics.
            val nums = node.descendants().filter { it.parts.none { part -> part is Node } }
                .mapNotNull { valid(it.attrs["data-value"] ?: it.attrs["content"] ?: it.text()) }
            if (nums.size == 2) return MatchScore(nums[0], nums[1])
        }
        return null
    }

    private fun parseMarkedScoreText(raw: String): MatchScore? {
        val normalized = normalizeDigits(raw)
        val m = MARKED_SCORE.find(normalized) ?: return null
        val home = m.groupValues[1].toIntOrNull() ?: return null
        val away = m.groupValues[2].toIntOrNull() ?: return null
        if (home !in 0..20 || away !in 0..20) return null
        return MatchScore(home, away)
    }

    private data class MatchScore(val home: Int, val away: Int)

    private fun statusOf(card: Node, text: String): String {
        // Prefer fields explicitly declared by the source as status/state. This prevents
        // words such as "Final" in a cup/competition title from ending a fixture.
        val nodes = listOf(card) + card.descendants()
        for (node in nodes) {
            val marker = listOf("class", "id", "data-testid", "itemprop", "data-role")
                .joinToString(" ") { node.attrs[it].orEmpty() }.lowercase(Locale.ROOT)
            val hasStatusMarker = marker.split(Regex("[^a-z0-9\\p{L}]+"))
                .any { it in setOf("status", "state", "matchstatus", "fixturestatus", "eventstatus") }
            val raw = node.attrs["data-status"] ?: node.attrs["data-state"] ?: node.attrs["data-match-status"]
                ?: if (hasStatusMarker) node.text() else ""
            canonicalStatus(raw).takeIf { it.isNotBlank() }?.let { return it }
        }
        return canonicalStatus(text)
    }

    private fun canonicalStatus(text: String): String = when {
        MatchStatusNormalizer.isPostponed(text) -> "postponed"
        MatchStatusNormalizer.isCanceled(text) -> "canceled"
        MatchStatusNormalizer.isSuspended(text) -> "suspended"
        MatchStatusNormalizer.isFinished(text) -> "finished"
        MatchStatusNormalizer.isLive(text) -> "in progress"
        MatchStatusNormalizer.isScheduled(text) -> "scheduled"
        else -> ""
    }


}
