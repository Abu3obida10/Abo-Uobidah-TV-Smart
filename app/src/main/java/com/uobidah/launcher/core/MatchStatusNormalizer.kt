package com.uobidah.launcher.core

import java.util.Locale

/**
 * Centralizes status interpretation across data providers.
 * A score, kickoff time, or elapsed clock is never terminal-state evidence.
 */
object MatchStatusNormalizer {
    private val finishedStatuses = setOf(
        "ft", "full time", "fulltime", "aet", "after extra time", "after penalties",
        "finished", "match finished", "match ended", "ended", "final", "complete", "completed",
        "انتهت", "انتهت المباراة", "انتهى", "نهاية المباراة", "اكتملت المباراة", "مكتملة", "مكتمل",
        "نهائي", "نهائية", "بعد الوقت الاضافي", "بعد الوقت الإضافي", "بركلات الترجيح", "بعد ركلات الترجيح"
    )
    private val postponedStatuses = setOf("pst", "postponed", "postponement", "delayed", "مؤجل", "مؤجلة", "تاجل", "تأجل")
    private val canceledStatuses = setOf("cancelled", "canceled", "cancel", "canc", "abandoned", "abd", "ملغاة", "ملغي", "ملغى", "الغيت", "الغي")
    private val suspendedStatuses = setOf("suspended", "suspend", "interrupted", "متوقفة", "متوقفه", "معلقة", "توقف مؤقت")
    private val liveStatuses = setOf(
        "live", "in progress", "inprogress", "in play", "playing", "1h", "2h", "ht", "et", "pen",
        "first half", "second half", "half time", "halftime", "extra time", "penalties",
        "مباشر", "جارية", "جاري", "الشوط الاول", "الشوط الثاني", "استراحة بين الشوطين", "نهاية الشوط الاول"
    )
    private val scheduledStatuses = setOf("scheduled", "not started", "pre", "قادمة", "قادم", "لم تبدأ", "لم يبدأ", "قبل البداية")
    // These words can also label a cup/final stage, so they are only terminal
    // when the provider gives them as the whole status value.
    private val exactOnlyFinishedStatuses = setOf("final", "نهائي", "نهائية")
    private val negativeFinishedPhrases = listOf(
        "not finished", "not yet finished", "unfinished", "not completed", "not ended", "not full time", "not final", "match not over",
        "لم تنته", "لم ينته", "لم تنتهي", "لم ينتهي", "لم تكتمل", "لم يكتمل", "لم تنته المباراة", "لم ينته المباراة"
    )

    fun normalize(value: String): String = value.trim().lowercase(Locale.ROOT)
        .replace(Regex("[\\u064B-\\u065F\\u0670\\u0640]"), "")
        .replace('أ', 'ا').replace('إ', 'ا').replace('آ', 'ا').replace('ٱ', 'ا')
        .replace('_', ' ').replace('-', ' ').replace(Regex("\\s+"), " ")

    /** Explicit terminal status only. Negative formulations override any matching word. */
    fun isFinished(value: String): Boolean {
        val status = normalize(value)
        if (status.isBlank() || negativeFinishedPhrases.any { status.contains(it) }) return false
        if (status in exactOnlyFinishedStatuses) return true
        return containsMarker(status, finishedStatuses - exactOnlyFinishedStatuses)
    }

    fun isPostponed(value: String): Boolean {
        val status = normalize(value)
        if (status.isBlank() || status.contains("not postponed") || status.contains("not delayed") ||
            status.contains("غير مؤجل") || status.contains("غير مؤجلة")) return false
        return status in postponedStatuses || status.contains("postpon") || status.contains("delayed") ||
            status.contains("مؤجل") || status.contains("تاجل")
    }

    fun isCanceled(value: String): Boolean {
        val status = normalize(value)
        if (status.isBlank() || status.contains("not cancelled") || status.contains("not canceled")) return false
        return containsMarker(status, canceledStatuses) || status.contains("ملغ") || status.contains("الغيت") || status.contains("الغي")
    }

    fun isSuspended(value: String): Boolean {
        val status = normalize(value)
        if (status.isBlank() || status.contains("not suspended") || status.contains("not interrupted") ||
            status.contains("غير متوقف") || status.contains("غير متوقفة")) return false
        return containsMarker(status, suspendedStatuses) || status.contains("توقف مؤقت") || status.contains("متوقف") || status.contains("متوقفة")
    }

    fun isLive(value: String): Boolean {
        val status = normalize(value)
        if (status.isBlank() || status.contains("not live") || status.contains("not in progress") ||
            status.contains("غير مباشر") || status.contains("ليست مباشرة") ||
            isFinished(status) || isPostponed(status) || isCanceled(status) || isSuspended(status)) return false
        return containsMarker(status, liveStatuses)
    }

    fun isScheduled(value: String): Boolean {
        val status = normalize(value)
        if (status.isBlank() || isFinished(status) || isPostponed(status) || isCanceled(status) || isSuspended(status) || isLive(status)) return false
        return containsMarker(status, scheduledStatuses)
    }

    /**
     * Converts provider-specific shorthand into a canonical internal state.
     * ESPN's `post` means the event is post-game; `pst` is the postponement code.
     */
    fun canonicalize(value: String, provider: String = ""): String {
        val raw = value.trim()
        val status = normalize(raw)
        if (status.isBlank()) return ""
        if (provider.equals("espn", true)) {
            when (status) {
                "post" -> return "finished"
                "pre" -> return "scheduled"
                "in" -> return "in progress"
            }
        }
        if (isPostponed(status)) return "postponed"
        if (isCanceled(status)) return "canceled"
        if (isSuspended(status)) return "suspended"
        if (isFinished(status)) return "finished"
        if (isLive(status)) return "in progress"
        if (isScheduled(status)) return "scheduled"
        // `post` is not globally portable: ESPN uses it for post-game, while other feeds
        // may use it differently. Only canonicalize it when the provider is known.
        return raw
    }

    private fun containsMarker(value: String, markers: Set<String>): Boolean {
        for (marker in markers) {
            if (marker.any { !it.isLetterOrDigit() }) {
                if (value.contains(marker)) return true
            } else {
                val pattern = Regex("(^|[^\\p{L}\\p{N}])" + Regex.escape(marker) + "($|[^\\p{L}\\p{N}])")
                if (pattern.containsMatchIn(value)) return true
            }
        }
        return false
    }
}
