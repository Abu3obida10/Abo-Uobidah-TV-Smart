package com.uobidah.launcher.core

/** Selects the live score feed independently from the feed chosen for match incidents. */
enum class MatchRefreshFeed { SOFASCORE, ESPN, YSSCORES, MATCH_REPOSITORY }

object MatchRefreshPlan {
    /**
     * If the initial details screen used SofaScore incidents, keep score and incidents
     * on the same provider. Otherwise refresh from the match's original provider whenever
     * that provider has a dedicated adapter, rather than looking up its ID in another feed.
     */
    fun forDetailSource(detailSource: String, usedSofaScoreTimeline: Boolean): MatchRefreshFeed {
        if (usedSofaScoreTimeline || detailSource.equals("sofascore", true)) return MatchRefreshFeed.SOFASCORE
        return when (detailSource.lowercase()) {
            "espn" -> MatchRefreshFeed.ESPN
            "ysscores" -> MatchRefreshFeed.YSSCORES
            else -> MatchRefreshFeed.MATCH_REPOSITORY
        }
    }
}
