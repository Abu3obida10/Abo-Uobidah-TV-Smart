package com.uobidah.launcher

import android.content.Context
import org.json.JSONObject
import java.io.File
import java.net.URLEncoder
import java.util.Locale

/**
 * شعارات الأندية من مستودعات GitHub العامة: لا تُضمَّن في التطبيق؛ يحمّل التطبيق فهرس الملفات مرة (يتجدد أسبوعياً)
 * ثم يسحب شعار كل فريق عند الحاجة ويحفظه على الجهاز (Img يخزّنه في مجلد دائم) فيبقى ظاهراً حتى بدون إنترنت.
 * لا نخمّن مسارات: الفهرس يُبنى من شجرة الملفات الفعلية لكل مستودع.
 */
object FootballLogos {
    private val REPOS = listOf(
        "Leo4815162342/football-logos",
        "JoseArroyave/football-logos",
        "sportlogos/football.db.logos"
    )
    private const val INDEX_FILE = "football_logos_index_v1.txt"
    private const val MAX_AGE_MS = 7L * 24 * 3600_000L
    private const val RETRY_MS = 30L * 60_000L

    private class Entry(val repo: Int, val path: String, val score: Int)

    @Volatile private var index: Map<String, Entry>? = null
    @Volatile private var lastTry = 0L
    private val lock = Any()

    fun norm(s: String): String = s.lowercase(Locale.ROOT)
        .replace(Regex("[\\u064B-\\u065F\\u0670]"), "")
        .replace('أ', 'ا').replace('إ', 'ا').replace('آ', 'ا').replace('ى', 'ي').replace('ة', 'ه')
        .replace(Regex("[^\\p{L}\\p{N}]"), "")

    private val NOISE = listOf("footballclub", "futbolclub", "fc", "cf", "sc", "afc", "ac", "club", "football", "futebol")

    private fun core(s: String): String {
        var n = norm(s)
        for (w in NOISE) {
            if (n.length > w.length + 3 && (n.startsWith(w) || n.endsWith(w))) n = if (n.startsWith(w)) n.removePrefix(w) else n.removeSuffix(w)
        }
        return n
    }

    /** كلمات عربية شائعة لأندية معروفة -> مفتاح بحث إنجليزي (الفهرس بأسماء إنجليزية). */
    private val AR: Map<String, String> = mapOf(
        "الاهلي" to "alahly", "الاهلي المصري" to "alahly", "الزمالك" to "zamalek", "بيراميدز" to "pyramids", "سيراميكا كليوباترا" to "ceramica",
        "المصري" to "almasry", "الاسماعيلي" to "ismaily", "انبي" to "enppi", "بتروجيت" to "petrojet", "الاتحاد السكندري" to "itthad",
        "الهلال" to "alhilal", "النصر" to "alnassr", "الاتحاد" to "alittihad", "الشباب" to "alshabab", "الاهلي السعودي" to "alahlisaudi",
        "ريال مدريد" to "realmadrid", "برشلونه" to "barcelona", "اتلتيكو مدريد" to "atleticomadrid", "اشبيليه" to "sevilla", "فالنسيا" to "valencia",
        "ريال سوسيداد" to "realsociedad", "فياريال" to "villarreal", "اتلتيك بلباو" to "athleticbilbao", "ريال بيتيس" to "realbetis",
        "مانشستر يونايتد" to "manchesterunited", "مان يونايتد" to "manchesterunited", "مانشستر سيتي" to "manchestercity", "ليفربول" to "liverpool",
        "ارسنال" to "arsenal", "تشيلسي" to "chelsea", "توتنهام" to "tottenham", "نيوكاسل" to "newcastle", "استون فيلا" to "astonvilla",
        "وست هام" to "westham", "برايتون" to "brighton", "ايفرتون" to "everton", "ليستر سيتي" to "leicester", "كريستال بالاس" to "crystalpalace",
        "يوفنتوس" to "juventus", "ميلان" to "acmilan", "انتر ميلان" to "inter", "الانتر" to "inter", "نابولي" to "napoli", "روما" to "roma",
        "لاتسيو" to "lazio", "اتالانتا" to "atalanta", "فيورنتينا" to "fiorentina",
        "بايرن ميونخ" to "bayernmunchen", "بروسيا دورتموند" to "dortmund", "باير ليفركوزن" to "leverkusen", "لايبزيج" to "leipzig",
        "باريس سان جيرمان" to "parissaintgermain", "مارسيليا" to "marseille", "ليون" to "lyon", "موناكو" to "monaco", "ليل" to "lille",
        "بنفيكا" to "benfica", "بورتو" to "porto", "سبورتينج لشبونه" to "sporting", "اياكس" to "ajax", "ايندهوفن" to "psv",
        "غلطه سراي" to "galatasaray", "فنربخشه" to "fenerbahce", "بشكتاش" to "besiktas"
    )

    private fun arabicKey(name: String): String? {
        val n = norm(name)
        if (n.isEmpty() || n.none { it in '\u0600'..'\u06FF' }) return null
        for ((k, v) in AR) if (norm(k) == n) return v
        for ((k, v) in AR) { val nk = norm(k); if (n.contains(nk) || nk.contains(n)) return v }
        return null
    }

    // ---------- الفهرس ----------
    private fun indexFile(ctx: Context) = File(ctx.filesDir, INDEX_FILE)

    private fun load(ctx: Context): Map<String, Entry>? {
        val f = indexFile(ctx)
        if (!f.isFile) return null
        return try {
            val m = HashMap<String, Entry>()
            f.forEachLine { line ->
                val p = line.split('\t')
                if (p.size == 4) m[p[0]] = Entry(p[1].toInt(), p[2], p[3].toInt())
            }
            if (m.isEmpty()) null else m
        } catch (_: Exception) { null }
    }

    private fun sizeHint(path: String): Int {
        val m = Regex("(\\d{2,4})x\\d{2,4}").find(path) ?: Regex("[-_@](\\d{3,4})(?:[^\\dx]|$)").find(path) ?: return 0
        return m.groupValues[1].toIntOrNull() ?: 0
    }

    private fun stemKey(path: String): List<String> {
        val parts = path.split('/')
        var stem = parts.last().substringBeforeLast('.')
        stem = stem.replace(Regex("\\.football-logos\\.cc$"), "")
            .replace(Regex("[-_@]\\d{2,4}x\\d{2,4}$"), "").replace(Regex("@\\d+x$"), "")
            .replace(Regex("[-_]?(logo|badge|crest|emblem)s?$", RegexOption.IGNORE_CASE), "")
        val generic = stem.isBlank() || stem.lowercase(Locale.ROOT) in setOf("logo", "badge", "crest", "main", "default", "icon", "emblem")
        val keys = ArrayList<String>()
        if (!generic) keys.add(stem)
        if (parts.size >= 2) keys.add(parts[parts.size - 2])
        return keys.map { core(it) }.filter { it.length >= 3 }.distinct()
    }

    private fun buildFromTree(repoIdx: Int, json: String, into: HashMap<String, Entry>) {
        val tree = JSONObject(json).optJSONArray("tree") ?: return
        for (i in 0 until tree.length()) {
            val o = tree.optJSONObject(i) ?: continue
            if (o.optString("type") != "blob") continue
            val path = o.optString("path")
            if (!path.lowercase(Locale.ROOT).endsWith(".png")) continue
            val sz = sizeHint(path)
            // نفضّل مقاساً قريباً من 512 (حاد وغير ضخم)؛ غير المعروف يأخذ نقطة وسط
            val score = if (sz > 0) 1000 - Math.abs(sz - 512) else 400
            for (k in stemKey(path)) {
                val cur = into[k]
                if (cur == null || score > cur.score || (score == cur.score && path.length < cur.path.length)) {
                    into[k] = Entry(repoIdx, path, score)
                }
            }
        }
    }

    /** يُستدعى من خيط خلفي فقط. */
    private fun ensureIndex(ctx: Context): Map<String, Entry>? {
        index?.let { if (System.currentTimeMillis() - indexFile(ctx).lastModified() < MAX_AGE_MS || System.currentTimeMillis() - lastTry < RETRY_MS) return it }
        synchronized(lock) {
            val f = indexFile(ctx)
            val fresh = f.isFile && System.currentTimeMillis() - f.lastModified() < MAX_AGE_MS
            if (index == null) index = load(ctx)
            if (fresh && index != null) return index
            if (System.currentTimeMillis() - lastTry < RETRY_MS) return index
            lastTry = System.currentTimeMillis()
            val merged = HashMap<String, Entry>()
            var ok = 0
            // مستودعات أخيرة أولاً حتى يغلب الأول (Leo) عند التعارض
            for (ri in REPOS.indices.reversed()) {
                try {
                    val json = Net.getString("https://api.github.com/repos/" + REPOS[ri] + "/git/trees/HEAD?recursive=1", 25000)
                    buildFromTree(ri, json, merged)
                    ok++
                } catch (_: Exception) { }
            }
            if (ok > 0 && merged.isNotEmpty()) {
                try {
                    val tmp = File(ctx.filesDir, INDEX_FILE + ".tmp")
                    tmp.bufferedWriter().use { w -> for ((k, e) in merged) w.write(k + "\t" + e.repo + "\t" + e.path + "\t" + e.score + "\n") }
                    if (!tmp.renameTo(f)) tmp.delete()
                } catch (_: Exception) { }
                index = merged
            }
            return index
        }
    }

    private fun rawUrl(e: Entry): String {
        val enc = e.path.split('/').joinToString("/") { URLEncoder.encode(it, "UTF-8").replace("+", "%20") }
        return "https://raw.githubusercontent.com/" + REPOS[e.repo] + "/HEAD/" + enc
    }

    /** رابط شعار الفريق من المستودعات، أو "" إن لم يوجد أو لم يتوفر الفهرس بعد. خيط خلفي فقط. */
    fun urlFor(ctx: Context, teamName: String): String {
        if (teamName.isBlank()) return ""
        val idx = try { ensureIndex(ctx.applicationContext) } catch (_: Exception) { null } ?: return ""
        val raw = arabicKey(teamName) ?: teamName
        val key = core(raw)
        if (key.length < 3) return ""
        idx[key]?.let { return rawUrl(it) }
        // مطابقة جزئية آمنة: أقرب مفتاح يحتوي الاسم أو يحتويه الاسم (طول ≥ 4) بأقل فرق في الطول
        var best: Entry? = null
        var bestDiff = Int.MAX_VALUE
        if (key.length >= 4) {
            for ((k, e) in idx) {
                if (k.length < 4) continue
                if (k.contains(key) || key.contains(k)) {
                    val d = Math.abs(k.length - key.length)
                    if (d < bestDiff) { bestDiff = d; best = e }
                }
            }
        }
        return if (best != null && bestDiff <= 6) rawUrl(best) else ""
    }
}
