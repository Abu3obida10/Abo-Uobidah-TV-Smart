package com.uobidah.launcher.core

import org.junit.Assert.assertEquals
import org.junit.Test

class MatchRefreshPlanTest {
    @Test fun prefersSofaScoreWhenItsIncidentsWereSelected() {
        assertEquals(MatchRefreshFeed.SOFASCORE, MatchRefreshPlan.forDetailSource("espn", true))
        assertEquals(MatchRefreshFeed.SOFASCORE, MatchRefreshPlan.forDetailSource("ysscores", true))
    }

    @Test fun refreshesEspnScoresFromEspnInsteadOfReturningNoFreshScore() {
        assertEquals(MatchRefreshFeed.ESPN, MatchRefreshPlan.forDetailSource("espn", false))
    }

    @Test fun refreshesYallaScoresFromItsOwnAdapter() {
        assertEquals(MatchRefreshFeed.YSSCORES, MatchRefreshPlan.forDetailSource("ysscores", false))
    }

    @Test fun keepsRepositoryRefreshForTheSportsDbAndConfiguredAdapters() {
        assertEquals(MatchRefreshFeed.MATCH_REPOSITORY, MatchRefreshPlan.forDetailSource("thesportsdb", false))
        assertEquals(MatchRefreshFeed.MATCH_REPOSITORY, MatchRefreshPlan.forDetailSource("custom", false))
    }
}
