package com.uobidah.launcher.core

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TimelineIntegrityTest {
    @Test fun acceptsTheActualFiveOneHomeWinGoalTally() {
        // Regression fixture for the reported Real Madrid 5-1 PSV display error:
        // PSV scores once, then the home side scores five times.
        val events = listOf(
            TimelineIntegrity.GoalIncident("Penalty Goal", home = false),
            TimelineIntegrity.GoalIncident("Goal", home = true),
            TimelineIntegrity.GoalIncident("Goal", home = true),
            TimelineIntegrity.GoalIncident("Goal", home = true),
            TimelineIntegrity.GoalIncident("Goal", home = true),
            TimelineIntegrity.GoalIncident("Goal", home = true)
        )
        assertTrue(TimelineIntegrity.isConsistent(events, 5, 1))
        assertFalse(TimelineIntegrity.isConsistent(events, 1, 5))
    }

    @Test fun rejectsIncompleteEventsBesideFiveOneScore() {
        val events = listOf(TimelineIntegrity.GoalIncident("Goal", home = false))
        assertFalse(TimelineIntegrity.isConsistent(events, 1, 5))
    }

    @Test fun rejectsEventsFromDifferentMatchWithTooManyGoals() {
        val events = listOf(
            TimelineIntegrity.GoalIncident("Goal", home = false),
            TimelineIntegrity.GoalIncident("Goal", home = false),
            TimelineIntegrity.GoalIncident("Goal", home = false)
        )
        assertFalse(TimelineIntegrity.isConsistent(events, 0, 1))
    }

    @Test fun creditsOwnGoalToOppositeSide() {
        val events = listOf(TimelineIntegrity.GoalIncident("Own Goal", home = false))
        assertTrue(TimelineIntegrity.isConsistent(events, 1, 0))
        assertFalse(TimelineIntegrity.isConsistent(events, 0, 1))
    }

    @Test fun doesNotAcceptEventsWhenScoreIsMissing() {
        val events = listOf(TimelineIntegrity.GoalIncident("Goal", home = true))
        assertFalse(TimelineIntegrity.isConsistent(events, null, null))
    }
}
