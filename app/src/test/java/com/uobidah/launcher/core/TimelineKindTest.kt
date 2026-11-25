package com.uobidah.launcher.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TimelineKindTest {
    @Test fun goal() = assertEquals(TimelineKind.GOAL, TimelineKind.classify("Goal"))
    @Test fun ownGoal() = assertEquals(TimelineKind.OWN_GOAL, TimelineKind.classify("Own Goal"))
    @Test fun penaltyGoal() = assertEquals(TimelineKind.PENALTY_GOAL, TimelineKind.classify("Penalty Goal"))
    @Test fun missedPenaltyIsNotGoal() = assertEquals(TimelineKind.OTHER, TimelineKind.classify("Penalty Missed"))
    @Test fun yellow() = assertEquals(TimelineKind.YELLOW, TimelineKind.classify("Yellow Card"))
    @Test fun secondYellowIsRed() = assertEquals(TimelineKind.RED, TimelineKind.classify("Second Yellow Card"))
    @Test fun red() = assertEquals(TimelineKind.RED, TimelineKind.classify("Red Card"))
    @Test fun sub() = assertEquals(TimelineKind.SUBSTITUTION, TimelineKind.classify("subst"))
    @Test fun unknownStaysOther() = assertEquals(TimelineKind.OTHER, TimelineKind.classify("VAR check"))
    @Test fun minuteKey() { assertEquals(45, TimelineKind.minuteKey("45+2")); assertTrue(TimelineKind.minuteKey("") > 1000) }
}
