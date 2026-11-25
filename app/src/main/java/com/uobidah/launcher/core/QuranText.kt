package com.uobidah.launcher.core

class SurahInfo(val number: Int, val nameAr: String, val ayahCount: Int, val meccan: Boolean)
class AyahItem(val number: Int, val text: String)

/**
 * مصدر نص القرآن: واجهة alquran.cloud (رواية حفص، الرسم العثماني) — لا يُضمَّن نص من الذاكرة.
 * الصوت: ملف سورة كامل من mp3quran.net. كلا الرابطين قابل للتغيير من هنا فقط.
 */
object QuranText {
    const val LIST_URL = "https://api.alquran.cloud/v1/surah"
    const val AUDIO_BASE = "https://server8.mp3quran.net/afs/"
    const val AUDIO_READER = "مشاري العفاسي"

    fun surahUrl(n: Int): String = "https://api.alquran.cloud/v1/surah/$n/quran-uthmani"
    fun audioUrl(n: Int): String = AUDIO_BASE + n.toString().padStart(3, '0') + ".mp3"

    fun parseSurahs(json: String): List<SurahInfo> {
        val root = MiniJson.parseObject(json)
        val data = root["data"] as? List<*> ?: return emptyList()
        val out = ArrayList<SurahInfo>()
        for (item in data) {
            val m = item as? Map<*, *> ?: continue
            val n = (m["number"] as? Number)?.toInt() ?: continue
            val name = m["name"] as? String ?: continue
            val cnt = (m["numberOfAyahs"] as? Number)?.toInt() ?: continue
            val meccan = (m["revelationType"] as? String).equals("Meccan", ignoreCase = true)
            out.add(SurahInfo(n, name, cnt, meccan))
        }
        return out.sortedBy { it.number }
    }

    fun parseAyahs(json: String, surah: Int): List<AyahItem> {
        val root = MiniJson.parseObject(json)
        val data = root["data"] as? Map<*, *> ?: return emptyList()
        val ayahs = data["ayahs"] as? List<*> ?: return emptyList()
        val out = ArrayList<AyahItem>()
        for (a in ayahs) {
            val m = a as? Map<*, *> ?: continue
            val n = (m["numberInSurah"] as? Number)?.toInt() ?: continue
            val t = m["text"] as? String ?: continue
            out.add(AyahItem(n, if (n == 1) stripBasmala(surah, t) else t))
        }
        return out
    }

    private fun bare(s: String): String = buildString {
        for (c in s) {
            val code = c.code
            val mark = code in 0x064B..0x065F || code == 0x0670 || code in 0x06D6..0x06ED || code == 0x0640
            if (!mark) append(c)
        }
    }

    /** المصدر يضيف البسملة إلى أول آية في كل سورة عدا الفاتحة (حيث هي آية) والتوبة. نعرضها كترويسة بدل ذلك. */
    fun stripBasmala(surah: Int, text: String): String {
        if (surah == 1 || surah == 9) return text
        val words = text.trim().split(Regex("\\s+"))
        if (words.size > 4 && bare(words[0]) == "بسم") return words.drop(4).joinToString(" ")
        return text
    }

    fun showBasmalaHeader(surah: Int): Boolean = surah != 1 && surah != 9

    const val BASMALA = "بِسْمِ اللَّهِ الرَّحْمَٰنِ الرَّحِيمِ"
}
