package com.uobidah.launcher.core

/** A goal incident with the side credited by the provider's event feed. */
object TimelineIntegrity {
    data class GoalIncident(val kind: String, val home: Boolean)

    /**
     * An event feed is displayed as a complete match timeline only when its goal tally
     * exactly reconciles with the match score. Own goals are credited to the opposite side.
     */
    fun isConsistent(
        incidents: List<GoalIncident>,
        homeScore: Int?,
        awayScore: Int?
    ): Boolean {
        if (homeScore == null || awayScore == null || homeScore !in 0..20 || awayScore !in 0..20) return false
        if (incidents.isEmpty()) return homeScore == 0 && awayScore == 0
        var homeGoals = 0
        var awayGoals = 0
        for (incident in incidents) {
            val kind = TimelineKind.classify(incident.kind)
            if (!kind.isGoal) continue
            val creditedHome = if (kind == TimelineKind.OWN_GOAL) !incident.home else incident.home
            if (creditedHome) homeGoals++ else awayGoals++
        }
        // For live matches incidents are cumulative too. A partial / stale provider feed
        // must not be shown as the complete event history next to a different score.
        return homeGoals == homeScore && awayGoals == awayScore
    }
}
