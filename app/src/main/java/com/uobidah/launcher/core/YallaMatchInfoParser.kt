package com.uobidah.launcher.core

/**
 * يقرأ قسم «معلومات اللقاء» من صفحة المباراة العامة في YSscores (عناصر match-info-item: title + content).
 * يعيد فقط ما يرد في الصفحة؛ لا يخمّن أي قيمة، والمفتاح الغائب يعني «غير متاح من المصدر».
 */
object YallaMatchInfoParser {
    private val ITEM = Regex("""<div class="match-info-item[^"]*">""")
    private val TITLE = Regex("""(?s)<div class="title">(.*?)</div>""")
    private val CONTENT_START = Regex("""<div class="content[^"]*">""")
    private val TAG = Regex("""(?s)<[^>]*>""")
    private val SPACE = Regex("""\s+""")

    fun parse(html: String): Map<String, String> {
        val starts = ITEM.findAll(html).map { it.range.first }.toList()
        if (starts.isEmpty()) return emptyMap()
        val out = LinkedHashMap<String, String>()
        for ((i, s) in starts.withIndex()) {
            val end = if (i + 1 < starts.size) starts[i + 1] else minOf(html.length, s + 4000)
            val chunk = html.substring(s, end)
            val title = TITLE.find(chunk)?.groupValues?.get(1)?.let { clean(it) }.orEmpty()
            if (title.isBlank() || out.containsKey(title)) continue
            val cs = CONTENT_START.find(chunk, TITLE.find(chunk)?.range?.last ?: 0) ?: continue
            var body = chunk.substring(cs.range.last + 1)
            // أوّل قيمة فقط: نتوقف عند زر «المزيد» أو الطاقم المخفي أو إغلاق الحاوية.
            val cut = listOf("<button", "<div id=", "</div>").map { body.indexOf(it) }.filter { it >= 0 }.minOrNull()
            if (cut != null) body = body.substring(0, cut)
            val value = clean(body)
            if (value.isNotBlank() && value != "المزيد") out[title] = value
        }
        return out
    }

    private fun clean(raw: String): String =
        TAG.replace(raw, " ").replace("&amp;", "&").replace("&nbsp;", " ").replace("&#039;", "'").replace("&quot;", "\"")
            .let { SPACE.replace(it, " ").trim() }

    /** مفاتيح العرض بالترتيب المرجعي مع اسم المفتاح كما يرد في الصفحة. */
    val KEYS = listOf("الجولة", "ملعب المباراة", "القناة", "المعلق", "الحكم")
}
