package com.uobidah.launcher.core

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MatchIdentityTest {
    @Test fun recognizesArabicAndEnglishAliasesForTrackedClubs() {
        assertTrue(MatchIdentity.sameFixture("ريال مدريد", "بي إس في آيندهوفن", "Real Madrid CF", "PSV Eindhoven"))
        assertTrue(MatchIdentity.sameFixture("الأهلي المصري", "مانشستر يونايتد", "Al Ahly", "Manchester United"))
    }

    @Test fun requiresBothTeamsNotJustTheTrackedClub() {
        assertFalse(MatchIdentity.sameFixture("Real Madrid", "PSV Eindhoven", "Real Madrid", "Liverpool"))
    }

    @Test fun rejectsReversedHomeAwayOrder() {
        assertFalse(MatchIdentity.sameFixture("Real Madrid", "PSV Eindhoven", "PSV Eindhoven", "Real Madrid"))
    }

    @Test fun normalizesCommonClubSuffixesAndAbbreviations() {
        assertTrue(MatchIdentity.sameFixture("Manchester United", "Real Madrid", "Man Utd", "Real Madrid CF"))
    }
}
