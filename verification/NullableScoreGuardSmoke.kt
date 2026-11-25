private fun evaluateScoreChange(currentHome: Int?, currentAway: Int?, previousRaw: String?): String {
    val currentHomeScore: Int? = currentHome
    val currentAwayScore: Int? = currentAway
    if (currentHomeScore == null || currentAwayScore == null) return "UNKNOWN_CURRENT"

    val previousParts = previousRaw?.split("-", limit = 2).orEmpty()
    val previousHomeScore: Int? = previousParts.getOrNull(0)?.toIntOrNull()
    val previousAwayScore: Int? = previousParts.getOrNull(1)?.toIntOrNull()
    if (previousHomeScore == null || previousAwayScore == null) return "UNKNOWN_PREVIOUS"

    return when {
        currentHomeScore + currentAwayScore < previousHomeScore + previousAwayScore -> "VAR"
        currentHomeScore > previousHomeScore && currentAwayScore == previousAwayScore -> "HOME_GOAL"
        currentAwayScore > previousAwayScore && currentHomeScore == previousHomeScore -> "AWAY_GOAL"
        currentHomeScore == previousHomeScore && currentAwayScore == previousAwayScore -> "UNCHANGED"
        else -> "GOAL_OR_CORRECTION_UNRESOLVED"
    }
}

fun main() {
    check(evaluateScoreChange(null, 0, "0-0") == "UNKNOWN_CURRENT")
    check(evaluateScoreChange(0, null, "0-0") == "UNKNOWN_CURRENT")
    check(evaluateScoreChange(1, 0, null) == "UNKNOWN_PREVIOUS")
    check(evaluateScoreChange(1, 0, "bad") == "UNKNOWN_PREVIOUS")
    check(evaluateScoreChange(1, 0, "0-0") == "HOME_GOAL")
    check(evaluateScoreChange(0, 1, "0-0") == "AWAY_GOAL")
    check(evaluateScoreChange(0, 0, "1-0") == "VAR")
    check(evaluateScoreChange(1, 0, "1-0") == "UNCHANGED")
    println("Nullable score guard smoke: 8/8 passed")
}
