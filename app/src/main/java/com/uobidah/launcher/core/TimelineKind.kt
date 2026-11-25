package com.uobidah.launcher.core

/** تصنيف أحداث المباراة القادمة من المصدر (نصوص إنجليزية) إلى أنواع معروفة بالعربية. لا نخترع حدثاً: غير المعروف يُعرض بنصه الأصلي. */
enum class TimelineKind(val icon: String, val labelAr: String) {
    GOAL("⚽", "هدف"),
    OWN_GOAL("⚽", "هدف عكسي"),
    PENALTY_GOAL("⚽", "هدف من ركلة جزاء"),
    YELLOW("🟨", "بطاقة صفراء"),
    RED("🟥", "بطاقة حمراء"),
    SUBSTITUTION("🔁", "تبديل"),
    OTHER("•", "");

    val isGoal: Boolean get() = this == GOAL || this == OWN_GOAL || this == PENALTY_GOAL

    companion object {
        fun classify(raw: String): TimelineKind {
            val s = raw.trim().lowercase().replace('_', ' ').replace('-', ' ')
            return when {
                s.isEmpty() -> OTHER
                s.contains("own goal") -> OWN_GOAL
                s.contains("missed") || s.contains("saved") || s.contains("disallow") || s.contains("cancel") -> OTHER
                s.contains("penalty") && s.contains("goal") -> PENALTY_GOAL
                s.contains("goal") -> GOAL
                s.contains("second yellow") || s.contains("yellow red") || s.contains("red") -> RED
                s.contains("yellow") -> YELLOW
                s.contains("subst") || s == "sub" -> SUBSTITUTION
                else -> OTHER
            }
        }

        /** رقم الدقيقة للترتيب (45+2 → 45). */
        fun minuteKey(m: String): Int = m.trim().takeWhile { it.isDigit() }.toIntOrNull() ?: Int.MAX_VALUE
    }
}
