package com.uobidah.launcher.core

/**
 * محلّل جدول الدوري من ESPN (apis/v2/sports/soccer/<league>/standings).
 *
 * في رد ESPN الفعلي: كل `entries[i]` فيه `team` و`stats[]` و`note` (كائن وليس نصًا).
 * المركز **ليس** حقلًا على مستوى الـentry، بل عنصر داخل `stats` اسمه `rank`
 * (وأحيانًا `note.rank` للمنطقة المؤهلة). والـentries نفسها تأتي بترتيب غير مضمون (غالبًا أبجدي)،
 * فلا نعتمد على ترتيب الوصول أبدًا.
 */
object EspnStandingsParser {
    data class Row(
        val rank: Int,
        val team: String,
        val teamId: String,
        val played: Int,
        val wins: Int,
        val draws: Int,
        val losses: Int,
        val goalsFor: Int,
        val goalsAgainst: Int,
        val goalDiff: Int,
        val points: Int,
        val note: String = ""
    )

    /** يرمي [MiniJson.JsonException] إن لم يكن النص JSON سليمًا؛ ويعيد قائمة فارغة إن لم توجد صفوف. */
    fun parse(json: String): List<Row> {
        val out = ArrayList<Row>()
        collect(MiniJson.parse(json), out)
        // نفس الفريق قد يظهر في أكثر من مجموعة/مرحلة؛ نبقي أول ظهور.
        val unique = out.distinctBy { it.teamId.ifBlank { it.team } }
        return order(unique)
    }

    /** الترتيب الصحيح: المركز الصريح من ESPN، ثم النقاط ثم فارق الأهداف ثم الأهداف له ثم الاسم. */
    fun order(rows: List<Row>): List<Row> {
        val hasRealRank = rows.isNotEmpty() && rows.all { it.rank > 0 }
        val sorted = if (hasRealRank) {
            rows.sortedWith(compareBy<Row> { it.rank }.thenByDescending { it.points }.thenBy { it.team })
        } else {
            rows.sortedWith(
                compareByDescending<Row> { it.points }
                    .thenByDescending { it.goalDiff }
                    .thenByDescending { it.goalsFor }
                    .thenBy { it.team }
            )
        }
        // عند غياب المركز من المصدر نرقّم من الترتيب المحسوب (نفس قواعد الجدول) بدل عرض أصفار.
        return if (hasRealRank) sorted else sorted.mapIndexed { i, r -> r.copy(rank = i + 1) }
    }

    @Suppress("UNCHECKED_CAST")
    private fun collect(node: Any?, out: MutableList<Row>) {
        when (node) {
            is Map<*, *> -> {
                val m = node as Map<String, Any?>
                (m["entries"] as? List<Any?>)?.forEach { e -> parseEntry(e as? Map<String, Any?>)?.let(out::add) }
                for ((_, v) in m) if (v is Map<*, *> || v is List<*>) collect(v, out)
            }
            is List<*> -> node.forEach { collect(it, out) }
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun parseEntry(o: Map<String, Any?>?): Row? {
        o ?: return null
        val team = o["team"] as? Map<String, Any?> ?: return null
        val stats = o["stats"] as? List<Any?> ?: return null
        val byName = HashMap<String, Double>()
        for (s in stats) {
            val sm = s as? Map<String, Any?> ?: continue
            val name = (sm["name"] as? String)?.lowercase() ?: continue
            val v = toDouble(sm["value"]) ?: toDouble(sm["displayValue"]) ?: continue
            byName.putIfAbsent(name, v)
        }
        fun n(name: String) = byName[name.lowercase()]?.toInt() ?: 0
        val noteObj = o["note"] as? Map<String, Any?>
        val rank = byName["rank"]?.toInt()?.takeIf { it > 0 }
            ?: toDouble(noteObj?.get("rank"))?.toInt()?.takeIf { it > 0 }
            ?: 0
        val name = (team["displayName"] as? String) ?: (team["name"] as? String) ?: return null
        val teamId = (team["id"] as? String) ?: (team["id"]?.toString() ?: "")
        return Row(
            rank = rank,
            team = name,
            teamId = teamId,
            played = n("gamesPlayed"),
            wins = n("wins"),
            draws = n("ties").takeIf { byName.containsKey("ties") } ?: n("draws"),
            losses = n("losses"),
            goalsFor = n("pointsFor"),
            goalsAgainst = n("pointsAgainst"),
            goalDiff = n("pointDifferential"),
            points = n("points"),
            note = (noteObj?.get("description") as? String) ?: (o["note"] as? String) ?: ""
        )
    }

    private fun toDouble(v: Any?): Double? = when (v) {
        is Number -> v.toDouble()
        is String -> v.trim().removePrefix("+").toDoubleOrNull()
        else -> null
    }
}
