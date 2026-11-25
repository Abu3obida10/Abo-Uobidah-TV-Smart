package com.uobidah.launcher.core

/**
 * آيات تُعرض في الواجهة. النص منقول حرفيًا من المصحف (الرسم العثماني، رواية حفص)،
 * وليس مولّدًا. المصدر المرجعي للتحقق: assabile.com/read-quran/surah-an-nisa-4/aya-103
 * (نص مطابق لـ Tanzil Uthmani). لا تعدّل النص إلا بعد مطابقته بمصحف موثوق؛
 * يحمي QuranVersesTest الحروف والتشكيل من أي تغيير عرضي.
 */
data class QuranVerse(val text: String, val surahAr: String, val surahNumber: Int, val ayah: Int, val note: String)

object QuranVerses {
    /** النساء: 103 — الجزء الأخير من الآية: «إِنَّ ٱلصَّلَوٰةَ كَانَتْ عَلَى ٱلْمُؤْمِنِينَ كِتَٰبًا مَّوْقُوتًا». */
    val PRAYER_TIMES = QuranVerse(
        text = "إِنَّ ٱلصَّلَوٰةَ كَانَتْ عَلَى ٱلْمُؤْمِنِينَ كِتَٰبًا مَّوْقُوتًا",
        surahAr = "النساء",
        surahNumber = 4,
        ayah = 103,
        note = "جزء من الآية"
    )

    private val INDIC = "٠١٢٣٤٥٦٧٨٩"

    /** «النساء ١٠٣» بأرقام هندية كما في التصميم المرجعي. */
    fun reference(v: QuranVerse): String = v.surahAr + " " + v.ayah.toString().map { INDIC[it - '0'] }.joinToString("")
}

/** نص حديث موثّق (لا يُولَّد). */
data class HadithExcerpt(val text: String, val source: String, val note: String)

object Hadiths {
    /**
     * مطلع دعاء النور من حديث ابن عباس في صحيح مسلم (كتاب صلاة المسافرين، برقم 763؛ مرجع الكتاب 6/225).
     * تحققت من هذا المقطع حرفيًا من sunnah.com/muslim:763j. بقية الدعاء لم تُنقل لأن أداة التحقق
     * تقصر الاقتباس على 125 حرفًا؛ لا تُمدَّد هذه العبارة بنص غير مطابَق لمصدر موثوق.
     */
    val NOOR_DUA_OPENING = HadithExcerpt(
        text = "اللَّهُمَّ اجْعَلْ لِي فِي قَلْبِي نُورًا وَفِي لِسَانِي نُورًا وَفِي سَمْعِي نُورًا وَفِي بَصَرِي نُورًا",
        source = "صحيح مسلم ٧٦٣",
        note = "مطلع الدعاء"
    )
}
