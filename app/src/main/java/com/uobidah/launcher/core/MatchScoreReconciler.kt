package com.uobidah.launcher.core

/** Cross-provider checks for the exact same fixture before accepting a verified final score. */
object MatchScoreReconciler {
    const val MAX_KICKOFF_DELTA_MS: Long = 2L * 60L * 60L * 1000L

    fun isSameFixtureCandidate(
        home: String,
        away: String,
        startMs: Long,
        candidateHome: String,
        candidateAway: String,
        candidateStartMs: Long
    ): Boolean {
        if (startMs <= 0L || candidateStartMs <= 0L) return false
        if (kotlin.math.abs(candidateStartMs - startMs) > MAX_KICKOFF_DELTA_MS) return false
        return MatchIdentity.sameFixture(home, away, candidateHome, candidateAway)
    }

    /** Secondary live scores may only advance both sides; one side going backwards is a conflict. */
    fun shouldAcceptLiveScore(
        currentHome: Int?,
        currentAway: Int?,
        incomingHome: Int?,
        incomingAway: Int?
    ): Boolean {
        if (incomingHome !in 0..20 || incomingAway !in 0..20) return false
        if (currentHome !in 0..20 || currentAway !in 0..20) return true
        return incomingHome!! >= currentHome!! && incomingAway!! >= currentAway!! &&
            (incomingHome != currentHome || incomingAway != currentAway)
    }

    /** Keep scores as an atomic pair; never combine one side from each provider response. */
    fun mergeLiveScore(
        currentHome: Int?,
        currentAway: Int?,
        incomingHome: Int?,
        incomingAway: Int?
    ): Pair<Int?, Int?> {
        if (incomingHome in 0..20 && incomingAway in 0..20) {
            if (currentHome in 0..20 && currentAway in 0..20 &&
                (incomingHome!! < currentHome!! || incomingAway!! < currentAway!!)) {
                return currentHome to currentAway
            }
            return incomingHome to incomingAway
        }
        if (currentHome in 0..20 && currentAway in 0..20) return currentHome to currentAway
        return null to null
    }

    /** SofaScore's explicit finished result may replace a conflicting or incomplete primary score. */
    fun shouldUseFinishedSecondary(
        primaryHome: Int?,
        primaryAway: Int?,
        primaryFinished: Boolean,
        secondaryHome: Int?,
        secondaryAway: Int?,
        secondaryFinished: Boolean
    ): Boolean {
        if (!secondaryFinished || secondaryHome !in 0..20 || secondaryAway !in 0..20) return false
        if (primaryHome !in 0..20 || primaryAway !in 0..20) return true
        return !primaryFinished || primaryHome != secondaryHome || primaryAway != secondaryAway
    }
}
