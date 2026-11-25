package com.uobidah.launcher.core

/** نتيجة مباراة واحدة من منظور الفريق: W فوز، D تعادل، L خسارة. */
enum class FormResult(val letterAr: String) { W("ف"), D("ت"), L("خ") }

/**
 * شارة الفورمة (مرجع R7): آخر نتائج الفريق من جدول مبارياته. لا يُولَّد شيء؛ إن لم تتوفر مباريات منتهية بنتيجة
 * واضحة تُعاد قائمة فارغة ولا تُعرض الشارة.
 * الصيغة المتوقعة (ESPN site API schedule): events[].competitions[0].{date, status.type.completed, competitors[]{id, team.id, score}}.
 * النتيجة تُحسب من الأهداف الفعلية لا من حقل winner كي يصح التعادل.
 */
object TeamForm {
    @Suppress("UNCHECKED_CAST")
    fun parse(json: String, teamId: String, limit: Int = 5): List<FormResult> {
        val root = try { MiniJson.parseObject(json) } catch (_: Exception) { return emptyList() }
        val events = root["events"] as? List<Any?> ?: return emptyList()
        data class Played(val date: String, val result: FormResult)
        val played = ArrayList<Played>()
        for (e in events) {
            val comp = ((e as? Map<String, Any?>)?.get("competitions") as? List<Any?>)?.firstOrNull() as? Map<String, Any?> ?: continue
            val status = (comp["status"] as? Map<String, Any?>)?.get("type") as? Map<String, Any?>
            if (status?.get("completed") != true) continue
            val comps = comp["competitors"] as? List<Any?> ?: continue
            var mine: Int? = null
            var theirs: Int? = null
            for (c in comps) {
                val m = c as? Map<String, Any?> ?: continue
                val id = ((m["team"] as? Map<String, Any?>)?.get("id") ?: m["id"])?.toString().orEmpty()
                val score = scoreOf(m["score"]) ?: continue
                if (id == teamId) mine = score else theirs = score
            }
            if (mine == null || theirs == null) continue
            val r = when { mine > theirs -> FormResult.W; mine == theirs -> FormResult.D; else -> FormResult.L }
            played.add(Played(comp["date"]?.toString().orEmpty(), r))
        }
        // الأحدث آخر العرض كما في المرجع: نأخذ آخر limit مباريات بترتيب التاريخ تصاعديًا.
        return played.sortedBy { it.date }.takeLast(limit).map { it.result }
    }

    private fun scoreOf(v: Any?): Int? = when (v) {
        is Map<*, *> -> (v["value"] ?: v["displayValue"])?.let { scoreOf(it) }
        is Number -> v.toInt()
        is String -> v.trim().toDoubleOrNull()?.toInt()
        else -> null
    }
}
