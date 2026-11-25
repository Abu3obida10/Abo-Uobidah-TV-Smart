package com.uobidah.launcher.core

import java.text.Normalizer
import java.util.Locale

/** Strict fixture identity checks used before borrowing events/lineups from another provider. */
object MatchIdentity {
    private val aliases = mapOf(
        "alahly" to setOf("alahly", "alahlysc", "alahlycairo", "alahlifc", "alahli", "الاهلي", "الاهلى", "الاهليالمصري", "الناديالاهلي"),
        "realmadrid" to setOf("realmadrid", "realmadridcf", "realmadridclubdefutbol", "ريالمدريد", "الريال"),
        "manchesterunited" to setOf("manchesterunited", "manutd", "manunited", "manchesterutd", "manchesterunitedfc", "مانشستريونايتد", "مانشستريونايتدالانجليزي", "مانيوونايتد"),
        "psveindhoven" to setOf("psv", "psveindhoven", "psveindhovenfc", "eindhoven", "بياسفي", "بياسفيايندهوفن", "ايندهوفن"),
        "barcelona" to setOf("barcelona", "fcbarcelona", "barcelonafc", "برشلونة"),
        "arsenal" to setOf("arsenal", "arsenalfc", "ارسنال"),
        "liverpool" to setOf("liverpool", "liverpoolfc", "ليفربول"),
        "chelsea" to setOf("chelsea", "chelseafc", "تشيلسي"),
        "manchestercity" to setOf("manchestercity", "mancity", "manchestercityfc", "مانشسترسيتي"),
        "tottenham" to setOf("tottenham", "tottenhamhotspur", "spurs", "توتنهام"),
        "juventus" to setOf("juventus", "juventusfc", "يوفنتوس"),
        "acmilan" to setOf("acmilan", "milan", "acmilanfc", "ميلان"),
        "inter" to setOf("inter", "intermilan", "internazionale", "انتر", "انترميلان"),
        "bayernmunich" to setOf("bayernmunich", "bayern", "fcbayern", "بايرنميونخ", "بايرنميونيخ"),
        "parissaintgermain" to setOf("parissaintgermain", "psg", "parisfc", "باريسسانجيرمان", "باريسسانجرمان")
    ).flatMap { (canonical, values) -> values.map { normalizeRaw(it) to canonical } }.toMap()

    fun normalizeTeam(value: String): String {
        val raw = normalizeRaw(value)
        aliases[raw]?.let { return it }
        var key = raw
        val suffixes = listOf("footballclub", "footballclub", "clubdefutbol", "club", "football", "fc", "cf", "sc", "afc")
        var changed: Boolean
        do {
            changed = false
            for (suffix in suffixes) {
                if (key.endsWith(suffix) && key.length > suffix.length + 2) {
                    key = key.removeSuffix(suffix)
                    changed = true
                }
            }
        } while (changed)
        val prefixes = listOf("footballclub", "club", "fc", "sc", "afc")
        for (prefix in prefixes) if (key.startsWith(prefix) && key.length > prefix.length + 2) key = key.removePrefix(prefix)
        return aliases[key] ?: key
    }

    /** Both sides and their home/away orientation must match; reversed fixtures are rejected. */
    fun sameFixture(expectedHome: String, expectedAway: String, actualHome: String, actualAway: String): Boolean {
        if (expectedHome.isBlank() || expectedAway.isBlank() || actualHome.isBlank() || actualAway.isBlank()) return false
        val eh = normalizeTeam(expectedHome)
        val ea = normalizeTeam(expectedAway)
        val ah = normalizeTeam(actualHome)
        val aa = normalizeTeam(actualAway)
        return eh.length >= 3 && ea.length >= 3 && eh == ah && ea == aa
    }

    private fun normalizeRaw(value: String): String {
        val folded = Normalizer.normalize(value.lowercase(Locale.ROOT), Normalizer.Form.NFD)
            .replace(Regex("\\p{M}+"), "")
            .replace('أ', 'ا').replace('إ', 'ا').replace('آ', 'ا').replace('ى', 'ي')
        return folded.replace(Regex("[^\\p{L}\\p{N}]"), "")
            .removePrefix("the")
    }
}
