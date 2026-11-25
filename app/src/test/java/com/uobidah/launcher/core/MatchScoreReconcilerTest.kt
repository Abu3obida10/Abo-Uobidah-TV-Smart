package com.uobidah.launcher.core

import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MatchScoreReconcilerTest {
    private val kickoff = 1_800_000_000_000L

    @Test fun onlyMatchesSameTeamsInSameHomeAwayOrderAndNearKickoff() {
        assertTrue(MatchScoreReconciler.isSameFixtureCandidate(
            "Real Madrid", "PSV Eindhoven", kickoff,
            "Real Madrid CF", "PSV", kickoff + 30 * 60_000L
        ))
        assertFalse(MatchScoreReconciler.isSameFixtureCandidate(
            "Real Madrid", "PSV Eindhoven", kickoff,
            "PSV", "Real Madrid", kickoff
        ))
        assertFalse(MatchScoreReconciler.isSameFixtureCandidate(
            "Real Madrid", "PSV Eindhoven", kickoff,
            "Real Madrid", "Liverpool", kickoff
        ))
        assertFalse(MatchScoreReconciler.isSameFixtureCandidate(
            "Real Madrid", "PSV Eindhoven", kickoff,
            "Real Madrid", "PSV", kickoff + 3 * 60 * 60_000L
        ))
    }

    @Test fun verifiedFinalSecondaryCorrectsReversedPrimaryScore() {
        assertTrue(MatchScoreReconciler.shouldUseFinishedSecondary(1, 5, true, 5, 1, true))
    }

    @Test fun verifiedFinalSecondaryFillsMissingPrimaryScore() {
        assertTrue(MatchScoreReconciler.shouldUseFinishedSecondary(null, null, true, 5, 1, true))
    }

    @Test fun matchingFinalScoreIsNotReplaced() {
        assertFalse(MatchScoreReconciler.shouldUseFinishedSecondary(5, 1, true, 5, 1, true))
    }

    @Test fun liveSecondaryCannotLowerEitherSideEvenWhenTotalGoalsIncrease() {
        assertFalse(MatchScoreReconciler.shouldAcceptLiveScore(2, 1, 1, 3))
        assertFalse(MatchScoreReconciler.shouldAcceptLiveScore(2, 1, 2, 0))
        assertTrue(MatchScoreReconciler.shouldAcceptLiveScore(2, 1, 2, 2))
    }

    @Test fun liveScoreMergeKeepsPairTogetherWhenIncomingDataIsPartial() {
        assertEquals(2 to 1, MatchScoreReconciler.mergeLiveScore(2, 1, 3, null))
        assertEquals(3 to 2, MatchScoreReconciler.mergeLiveScore(2, 1, 3, 2))
        assertEquals(2 to 1, MatchScoreReconciler.mergeLiveScore(2, 1, 1, 3))
        assertEquals(null to null, MatchScoreReconciler.mergeLiveScore(null, null, 0, null))
    }

    @Test fun incompleteOrLiveSecondaryNeverReplacesScore() {
        assertFalse(MatchScoreReconciler.shouldUseFinishedSecondary(1, 5, true, 5, 1, false))
        assertFalse(MatchScoreReconciler.shouldUseFinishedSecondary(1, 5, true, 21, 1, true))
    }
}
