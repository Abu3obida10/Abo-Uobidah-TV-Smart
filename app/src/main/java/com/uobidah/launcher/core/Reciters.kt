package com.uobidah.launcher.core

/**
 * نماذج وتحليل بيانات القراء من واجهة mp3quran.net (v3). لا اعتماد على Android كي تُختبر على JVM.
 * لا توجد أي بيانات مدمجة: القراء يأتون من المصدر الرسمي فقط، وتُحذف التكرارات بالمعرّف.
 */
class Moshaf(
    val id: Int,
    val name: String,
    val server: String,
    val surahs: List<Int>,
    val riwayaName: String
) {
    fun has(surah: Int) = surah in surahs

    /** رابط ملف السورة: {server}{NNN}.mp3 — الصيغة الموثقة في واجهة المصدر. */
    fun urlFor(surah: Int): String {
        require(surah in 1..114) { "رقم السورة خارج النطاق" }
        val base = if (server.endsWith("/")) server else "$server/"
        return base + surah.toString().padStart(3, '0') + ".mp3"
    }
}

class Reciter(
    val id: Int,
    val name: String,
    val letter: String,
    /** رابط صورة إن أرسله المصدر؛ فارغ إن لم يتوفر (لا نخترع صورًا). */
    val photo: String,
    val moshafs: List<Moshaf>
) {
    val riwayat: List<String> get() = moshafs.map { it.name }.distinct()
    val primary: Moshaf? get() = moshafs.firstOrNull()
}

object ReciterParser {
    @Suppress("UNCHECKED_CAST")
    fun parseReciters(json: String): List<Reciter> {
        val root = MiniJson.parseObject(json)
        val arr = root["reciters"] as? List<Any?> ?: return emptyList()
        data class MutableReciter(val id: Int, val name: String, val letter: String, var photo: String,
                                  val moshafs: LinkedHashMap<Int, Moshaf>)
        val byId = LinkedHashMap<Int, MutableReciter>()
        for (x in arr) {
            val o = x as? Map<String, Any?> ?: continue
            val id = num(o["id"]) ?: continue
            val name = (o["name"] as? String)?.trim().orEmpty()
            if (name.isEmpty()) continue
            val photo = listOf("image", "photo", "img", "picture", "avatar")
                .firstNotNullOfOrNull { (o[it] as? String)?.trim()?.takeIf(::isSafeHttpsUrl) } ?: ""
            val validMoshafs = LinkedHashMap<Int, Moshaf>()
            for (m in (o["moshaf"] as? List<Any?>) ?: emptyList()) {
                val mo = m as? Map<String, Any?> ?: continue
                val mid = num(mo["id"]) ?: continue
                val server = (mo["server"] as? String)?.trim().orEmpty()
                if (!isSafeHttpsUrl(server)) continue
                val list = parseSurahList(((mo["surah_list"] ?: mo["surahs"]) as? String).orEmpty())
                if (list.isEmpty()) continue
                val moshafName = (mo["name"] as? String)?.trim().orEmpty()
                validMoshafs.putIfAbsent(mid, Moshaf(mid, moshafName, server, list, moshafName))
            }
            // لا نعتبر المعرّف مستخدماً إلا بعد وجود اسم ومصحف صالح؛ ويمكن دمج المصاحف
            // عند ورود سجل آخر لنفس القارئ بدلاً من إسقاط بيانات الروايات الصحيحة.
            if (validMoshafs.isEmpty()) continue
            val existing = byId[id]
            if (existing == null) {
                byId[id] = MutableReciter(id, name, (o["letter"] as? String).orEmpty(), photo, validMoshafs)
            } else {
                validMoshafs.forEach { (moshafId, moshaf) -> existing.moshafs.putIfAbsent(moshafId, moshaf) }
                if (existing.photo.isBlank() && photo.isNotBlank()) existing.photo = photo
            }
        }
        return byId.values.map { r ->
            Reciter(r.id, r.name, r.letter, r.photo, r.moshafs.values.toList())
        }
    }

    private fun isSafeHttpsUrl(value: String): Boolean {
        if (value.isBlank()) return false
        return try {
            val uri = java.net.URI(value)
            uri.scheme.equals("https", ignoreCase = true) && !uri.host.isNullOrBlank() && uri.userInfo == null
        } catch (_: Exception) { false }
    }

    fun parseSurahList(s: String): List<Int> =
        s.split(',').mapNotNull { it.trim().toIntOrNull() }.filter { it in 1..114 }.distinct().sorted()

    private fun num(v: Any?): Int? = when (v) {
        is Long -> v.toInt()
        is Double -> v.toInt()
        is Int -> v
        is String -> v.trim().toIntOrNull()
        else -> null
    }

    /** أسماء السور من /suwar: id → name. */
    @Suppress("UNCHECKED_CAST")
    fun parseSuwar(json: String): Map<Int, String> {
        val arr = MiniJson.parseObject(json)["suwar"] as? List<Any?> ?: return emptyMap()
        val m = LinkedHashMap<Int, String>()
        for (x in arr) {
            val o = x as? Map<String, Any?> ?: continue
            val id = num(o["id"]) ?: continue
            val n = (o["name"] as? String)?.trim().orEmpty()
            if (n.isNotEmpty() && id in 1..114) m[id] = n
        }
        return m
    }
}

object ArabicSearch {
    private val tashkeel = Regex("[\\u0610-\\u061A\\u064B-\\u065F\\u0670\\u06D6-\\u06ED\\u0640]")

    /** تطبيع للبحث: بلا تشكيل ولا تطويل، توحيد الألف والياء والتاء المربوطة، أحرف صغيرة. */
    fun norm(s: String): String = s.lowercase()
        .replace(tashkeel, "")
        .replace('أ', 'ا').replace('إ', 'ا').replace('آ', 'ا').replace('ٱ', 'ا')
        .replace('ى', 'ي').replace('ة', 'ه').replace('ؤ', 'و').replace('ئ', 'ي')
        .trim()

    fun matches(query: String, vararg fields: String): Boolean {
        val q = norm(query)
        if (q.isEmpty()) return true
        val hay = fields.joinToString(" ") { norm(it) }
        return q.split(' ').filter { it.isNotEmpty() }.all { hay.contains(it) }
    }
}

object ReciterFilter {
    fun filter(all: List<Reciter>, query: String, riwaya: String, favoritesOnly: Boolean, favorites: Set<Int>): List<Reciter> =
        all.filter { r ->
            (!favoritesOnly || r.id in favorites) &&
                (riwaya.isEmpty() || r.riwayat.any { it == riwaya }) &&
                ArabicSearch.matches(query, r.name, r.riwayat.joinToString(" "))
        }

    fun allRiwayat(all: List<Reciter>): List<String> =
        all.flatMap { it.riwayat }.filter { it.isNotBlank() }.distinct().sorted()
}

/** حالة آخر استماع: تُحفظ كسطر واحد. */
class LastListen(val reciterId: Int, val moshafId: Int, val surah: Int, val positionMs: Long) {
    fun encode() = "$reciterId|$moshafId|$surah|$positionMs"
    companion object {
        fun decode(s: String): LastListen? {
            val p = s.split('|')
            if (p.size != 4) return null
            val a = p[0].toIntOrNull() ?: return null
            val b = p[1].toIntOrNull() ?: return null
            val c = p[2].toIntOrNull() ?: return null
            val d = p[3].toLongOrNull() ?: return null
            return if (c in 1..114 && d >= 0) LastListen(a, b, c, d) else null
        }
    }
}

/** تسلسل الصفحات المفتوحة: يدعم الرجوع خطوة بخطوة ويعرف متى لا توجد صفحة سابقة. */
class NavStack {
    private val items = ArrayList<String>()
    val size: Int get() = items.size
    fun push(id: String) { items.add(id) }
    /** يزيل أقرب ظهور للصفحة من النهاية (صفحة أُغلقت). */
    fun remove(id: String) { val i = items.lastIndexOf(id); if (i >= 0) items.removeAt(i) }
    fun hasPrevious(): Boolean = items.size > 1
    fun top(): String? = items.lastOrNull()
    fun snapshot(): List<String> = items.toList()
}

/** عدد أعمدة شبكة البطاقات حسب عرض الشاشة بوحدة dp: بطاقات واسعة، وعمود واحد في وضع القائمة أو الشاشات الضيقة. */
object CardGrid {
    fun span(widthDp: Int, grid: Boolean): Int = when {
        !grid -> 1
        widthDp >= 1400 -> 3
        widthDp >= 820 -> 2
        else -> 1
    }

    /** أعمدة شبكة التطبيقات حسب العرض بوحدة dp: خلية لا تقل عن 170dp، بين عمودين و7 أعمدة. */
    fun appColumns(widthDp: Int): Int = (widthDp / 170).coerceIn(2, 7)

    /** عدد بطاقات المباريات الظاهرة في شريط الشاشة الرئيسية: أقل بطاقات = أعرض وأوضح. */
    fun homeVisible(widthDp: Int): Int = when {
        widthDp >= 1500 -> 3
        widthDp >= 700 -> 2
        else -> 1
    }

    /** عرض البطاقة بالبكسل بعد خصم الحشو والفواصل. */
    fun cardWidth(totalPx: Int, span: Int, gapPx: Int): Int =
        if (span <= 0) totalPx else (totalPx - gapPx * (span + 1)) / span
}
