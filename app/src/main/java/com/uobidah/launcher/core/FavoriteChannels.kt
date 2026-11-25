package com.uobidah.launcher.core

/**
 * تخزين قنوات IPTV المفضلة مع الشعار (مرجع R3).
 * الصيغة المخزنة سطر واحد: «الاسم||الرابط||رابط الشعار». السطور القديمة «الاسم||الرابط» تُقرأ بلا فقد وبلا شعار،
 * فلا حاجة لترحيل منفصل ولا يضيع شيء من مفضلة المستخدم. الاسم لا يحتوي «||» أبدًا (يُستبدل بمسافة عند الكتابة).
 */
data class FavoriteChannel(val name: String, val url: String, val logo: String = "")

object FavoriteChannels {
    private const val SEP = "||"
    const val MAX_LOGO = 500

    fun cleanLogo(logo: String?): String {
        val t = logo.orEmpty().trim()
        return if (t.length <= MAX_LOGO && (t.startsWith("https://", true) || t.startsWith("http://", true)) && !t.contains(SEP)) t else ""
    }

    fun parse(entry: String): FavoriteChannel? {
        val parts = entry.split(SEP, limit = 3)
        if (parts.size < 2) return null
        val url = parts[1].trim()
        if (url.isBlank()) return null
        return FavoriteChannel(parts[0].trim().ifBlank { url }, url, cleanLogo(parts.getOrNull(2)))
    }

    fun encode(c: FavoriteChannel): String {
        val name = c.name.replace(SEP, " ").trim()
        val logo = cleanLogo(c.logo)
        return if (logo.isEmpty()) "$name$SEP${c.url}" else "$name$SEP${c.url}$SEP$logo"
    }

    fun parseAll(entries: List<String>): List<FavoriteChannel> = entries.mapNotNull(::parse)

    /** يضيف القناة إن لم تكن موجودة، ويحذفها إن كانت موجودة (حسب الرابط). added=true عند الإضافة. */
    fun toggle(entries: List<String>, channel: FavoriteChannel): Pair<List<String>, Boolean> {
        val idx = entries.indexOfFirst { parse(it)?.url == channel.url }
        return if (idx >= 0) entries.filterIndexed { i, _ -> i != idx } to false
        else entries + encode(channel) to true
    }

    /**
     * يكمل شعارات المفضلة الناقصة من قنوات قائمة محمّلة (مطابقة بالرابط فقط). يعيد null إن لم يتغير شيء،
     * كي لا تُكتب التفضيلات بلا داعٍ. لا يستبدل شعارًا موجودًا.
     */
    fun backfillLogos(entries: List<String>, logoByUrl: Map<String, String>): List<String>? {
        var changed = false
        val out = entries.map { e ->
            val f = parse(e)
            if (f == null || f.logo.isNotEmpty()) e
            else {
                val logo = cleanLogo(logoByUrl[f.url])
                if (logo.isEmpty()) e else { changed = true; encode(f.copy(logo = logo)) }
            }
        }
        return if (changed) out else null
    }
}
