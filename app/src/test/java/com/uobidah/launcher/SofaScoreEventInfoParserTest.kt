package com.uobidah.launcher

import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.Instant

@RunWith(RobolectricTestRunner::class)
class SofaScoreEventInfoParserTest {
    private val startMs = Instant.parse("2026-11-24T19:00:00Z").toEpochMilli()
    private val json = """{
      "event": {
        "id": 987654,
        "startTimestamp": 1795546800,
        "homeTeam": {"name":"Real Madrid"},
        "awayTeam": {"name":"PSV"},
        "tournament": {"name":"UEFA Champions League"},
        "roundInfo": {"round":5},
        "venue": {"name":"Santiago Bernabéu","city":"Madrid"},
        "referee": {"name":"Test Referee"},
        "attendance": 75000,
        "managers": {"home":{"name":"Home Manager"},"away":{"name":"Away Manager"}}
      }
    }"""

    @Test fun extractsOnlyValuesReturnedForTheExactFixture() {
        val info = SofaScoreEventInfoParser.parse(json, "sofascore|987654", "Real Madrid", "PSV", startMs)!!
        assertEquals("UEFA Champions League", info.tournament)
        assertEquals("الجولة 5", info.round)
        assertEquals("Santiago Bernabéu", info.venue)
        assertEquals("Madrid", info.city)
        assertEquals("Test Referee", info.referee)
        assertEquals("75000", info.attendance)
        assertEquals("Home Manager", info.homeManager)
        assertEquals("Away Manager", info.awayManager)
    }

    @Test fun rejectsAnotherEventEvenIfItsMetadataLooksValid() {
        assertNull(SofaScoreEventInfoParser.parse(json, "987655", "Real Madrid", "PSV", startMs))
    }

    @Test fun rejectsDifferentTeamsOrKickoffInsteadOfBorrowingTheirVenueAndReferee() {
        assertNull(SofaScoreEventInfoParser.parse(json, "987654", "Real Madrid", "Barcelona", startMs))
        assertNull(SofaScoreEventInfoParser.parse(json, "987654", "Real Madrid", "PSV", startMs + 6 * 60 * 60_000L))
    }

    @Test fun rejectsIncompleteEventWithoutIdentityAndKickoff() {
        assertNull(SofaScoreEventInfoParser.parse("{}", "987654", "Real Madrid", "PSV", startMs))
    }
}
