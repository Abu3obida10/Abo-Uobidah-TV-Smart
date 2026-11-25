package com.uobidah.launcher

import com.uobidah.launcher.core.MatchStatusNormalizer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MatchTest {
    private val now = 1_000_000_000_000L
    private fun m(start: Long, hs: Int? = null, aw: Int? = null, st: String = "") =
        Match("1", "A", "B", start, "L", hs, aw, st)

    @Test fun liveWhenStartedRecently() = assertTrue(m(now - 30 * 60_000L, 1, 0, "1H").live(now))
    @Test fun notLiveBeforeStart() = assertFalse(m(now + 60_000L).live(now))
    @Test fun finishedByStatus() = assertTrue(m(now - 60 * 60_000L, 2, 1, "Match Finished").finished(now))
    @Test fun notLiveWhenFinished() = assertFalse(m(now - 60 * 60_000L, 2, 1, "FT").live(now))
    @Test fun notLiveAfterWindow() = assertFalse(m(now - 200 * 60_000L, 1, 0, "1H").live(now))
    @Test fun noStatusIsNotAssumedLive() = assertFalse(m(now - 30 * 60_000L, 1, 0, "").live(now))
    @Test fun progressCanMarkLive() = assertTrue(Match("2", "A", "B", now - 20 * 60_000L, "L", 0, 0, "", "23").live(now))
    @Test fun descriptiveProgressIsNotLive() = assertFalse(Match("3", "A", "B", now - 20 * 60_000L, "L", 0, 0, "", "Not Started").live(now))
    @Test fun dashProgressIsNotLive() = assertFalse(Match("4", "A", "B", now - 20 * 60_000L, "L", 0, 0, "", "-").live(now))
    @Test fun stoppageProgressCanMarkLive() = assertTrue(Match("5", "A", "B", now - 20 * 60_000L, "L", 0, 0, "", "45+2'").live(now))
    @Test fun cachedLiveStatusIsNotShownAsLive() = assertFalse(Match("6", "A", "B", now - 20 * 60_000L, "L", 0, 0, "1H", "23", fromCache = true).live(now))
    @Test fun cachedLiveStatusCannotBeMisfiledAsUpcoming() {
        val stale = Match("6b", "A", "B", now + 60 * 60_000L, "L", 0, 0, "1H", "23", fromCache = true)
        assertFalse(stale.upcoming(now))
        assertEquals(MatchState.UNKNOWN, stale.state(now))
    }
    @Test fun explicitScheduledStatusOverridesAmbiguousNumericProgress() {
        val scheduled = Match("6c", "A", "B", now - 20 * 60_000L, "L", 0, 0, "Scheduled", "23")
        assertFalse(scheduled.live(now))
    }
    @Test fun postponedIsNotFinished() {
        val x = m(now + 3_600_000L, null, null, "Postponed")
        assertFalse(x.finished(now)); assertEquals(MatchState.POSTPONED, x.state(now))
    }
    @Test fun canceledState() = assertEquals(MatchState.CANCELED, m(now - 3_600_000L, null, null, "Cancelled").state(now))
    @Test fun canceledMatchIsNotACompletedPastResult() {
        val canceled = m(now - 3_600_000L, null, null, "Cancelled")
        assertFalse(canceled.previous(now))
        assertFalse(canceled.finished(now))
    }
    @Test fun upcomingZeroZeroIsNeverAVisibleResult() {
        val upcoming = m(now + 60 * 60_000L, 0, 0, "Scheduled")
        assertEquals(MatchState.UPCOMING, upcoming.state(now))
        assertFalse(upcoming.hasDisplayableScore(now))
    }

    @Test fun liveAndFinishedZeroScoresRemainValidResults() {
        assertTrue(m(now - 20 * 60_000L, 0, 0, "1H").hasDisplayableScore(now))
        assertTrue(m(now - 60 * 60_000L, 0, 0, "FT").hasDisplayableScore(now))
    }

    @Test fun unknownPastScoreIsNotDisplayedAsAResult() {
        assertFalse(m(now - 5 * 60 * 60_000L, 2, 1, "").hasDisplayableScore(now))
    }

    @Test fun finishedState() = assertEquals(MatchState.FINISHED, m(now - 3_600_000L, 2, 1, "FT").state(now))
    @Test fun liveState() = assertEquals(MatchState.LIVE, m(now - 30 * 60_000L, 1, 0, "1H").state(now))
    @Test fun upcomingState() = assertEquals(MatchState.UPCOMING, m(now + 3_600_000L, null, null, "Not Started").state(now))
    @Test fun espnArabicFinishedStateIsFinalImmediately() {
        val match = m(now - 65 * 60_000L, 2, 1, "انتهت")
        assertTrue(match.finished(now))
        assertEquals(MatchState.FINISHED, match.state(now))
    }
    @Test fun hamzaVariantFinishedStatusIsRecognised() {
        assertEquals(MatchState.FINISHED, m(now - 65 * 60_000L, 2, 1, "إنتهت").state(now))
    }
    @Test fun explicitArabicFinalMarkersAreRecognised() {
        assertEquals(MatchState.FINISHED, m(now - 65 * 60_000L, 2, 1, "نهائي").state(now))
        assertEquals(MatchState.FINISHED, m(now - 125 * 60_000L, 2, 1, "بركلات الترجيح").state(now))
    }
    @Test fun diacriticsAndTatweelDoNotHideFinishedStatus() {
        assertEquals(MatchState.FINISHED, m(now - 65 * 60_000L, 2, 1, "اِنْتَـهَتْ").state(now))
    }
    @Test fun arabicCancellationWithHamzaIsRecognised() {
        assertEquals(MatchState.CANCELED, m(now - 60_000L, null, null, "أُلغِيَت").state(now))
    }
    @Test fun arabicPostponedAndCancelledStatesAreRecognised() {
        assertEquals(MatchState.POSTPONED, m(now + 60_000L, null, null, "مؤجلة").state(now))
        assertEquals(MatchState.CANCELED, m(now - 60_000L, null, null, "ملغاة").state(now))
    }
    @Test fun exactClubIdsAreConfigured() {
        assertTrue(MatchRepo.teams.any { it.query == "Al Ahly" && it.id == "138995" })
        assertTrue(MatchRepo.teams.any { it.query == "Manchester United" && it.id == "133612" })
        assertTrue(MatchRepo.teams.any { it.query == "Real Madrid" && it.id == "133738" })
    }

    @Test fun elapsedTimeAndExistingScoreDoNotProveMatchFinished() {
        val old = m(now - 5 * 60 * 60_000L, 2, 1, "")
        assertFalse(old.finished(now))
        assertEquals(MatchState.UNKNOWN, old.state(now))
        assertFalse(old.previous(now))
        assertFalse(old.upcoming(now))
    }

    @Test fun pastMatchWithoutStatusIsUnknownNotUpcoming() {
        val unknown = m(now - 4 * 60 * 60_000L)
        assertEquals(MatchState.UNKNOWN, unknown.state(now))
        assertFalse(unknown.upcoming(now))
    }

    @Test fun futureMatchWithoutStatusIsUpcoming() {
        val future = m(now + 4 * 60 * 60_000L)
        assertEquals(MatchState.UPCOMING, future.state(now))
        assertTrue(future.upcoming(now))
    }

    @Test fun dateOnlyOrMissingKickoffDoesNotCreateALiveOrPastClassification() {
        val unknown = m(0L, 1, 0, "")
        assertEquals(MatchState.UNKNOWN, unknown.state(now))
        assertFalse(unknown.live(now))
        assertFalse(unknown.previous(now))
    }

    @Test fun postponedShortCodeIsPstNotAmbiguousPost() {
        val postponed = m(now - 2 * 60 * 60_000L, 0, 0, "PST")
        assertFalse(postponed.finished(now))
        assertEquals(MatchState.POSTPONED, postponed.state(now))
        val ambiguous = m(now - 2 * 60 * 60_000L, 0, 0, "POST")
        assertEquals(MatchState.UNKNOWN, ambiguous.state(now))
        val genericNormalized = MatchStatusNormalizer.canonicalize("POST", "generic-feed")
        assertEquals(MatchState.UNKNOWN, m(now - 2 * 60 * 60_000L, 0, 0, genericNormalized).state(now))
        val espnNormalized = MatchStatusNormalizer.canonicalize("POST", "espn")
        assertEquals(MatchState.FINISHED, m(now - 2 * 60 * 60_000L, 0, 0, espnNormalized).state(now))
    }

    @Test fun suspendedStateIsNotFinished() {
        val suspended = m(now - 30 * 60_000L, 1, 0, "Suspended")
        assertFalse(suspended.finished(now))
        assertEquals(MatchState.SUSPENDED, suspended.state(now))
    }

    @Test fun secondaryFeedCannotDowngradeAnExplicitFinalStatus() {
        val primary = m(now - 60 * 60_000L, 2, 1, "FT")
        val staleSecondary = m(now - 60 * 60_000L, 2, 1, "1H")
        assertEquals(MatchState.FINISHED, primary.withLive(staleSecondary).state(now))
    }

    @Test fun secondaryExplicitLiveStateCanUpgradeAScheduledPrimaryWithoutScoreChange() {
        val primary = m(now - 20 * 60_000L, 0, 0, "Scheduled")
        val secondary = m(now - 20 * 60_000L, 0, 0, "1H")
        assertEquals(MatchState.LIVE, primary.withLive(secondary).state(now))
    }

}
