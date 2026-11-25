package com.uobidah.launcher

import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class SofaScoreLineupParserTest {
    private val fullOfficial = """{
      "event":{"id":987654},"confirmed":true,
      "home":{"formation":"4-3-3","players":[
        {"player":{"name":"Home Keeper","position":"G","jersey_number":1},"position":"G","substitute":false,"captain":true},
        {"player":{"name":"Home Forward","position":"F","jersey_number":9},"position":"F","substitute":false}
      ]},
      "away":{"formation":"4-2-3-1","players":[
        {"player":{"name":"Away Keeper","position":"G","jersey_number":1},"position":"G","substitute":false},
        {"player":{"name":"Away Sub","position":"M","jersey_number":18},"position":"M","jerseyNumber":"18","substitute":true}
      ]}
    }"""

    @Test fun parsesBothTeamsFormationNumbersSubsAndCaptainFromSameEvent() {
        val parsed = SofaScoreLineupParser.parse(fullOfficial, "sofascore|987654")!!
        assertEquals("987654", parsed.eventId)
        assertEquals(true, parsed.confirmed)
        assertEquals("4-3-3", parsed.homeFormation)
        assertEquals("4-2-3-1", parsed.awayFormation)
        assertEquals(2, parsed.players.count { it.home })
        assertEquals(2, parsed.players.count { !it.home })
        assertTrue(parsed.players.first { it.name == "Home Keeper" }.captain)
        assertTrue(parsed.players.first { it.name == "Away Sub" }.sub)
        assertEquals("18", parsed.players.first { it.name == "Away Sub" }.number)
    }

    @Test fun unconfirmedLineupStaysExplicitlyUnconfirmed() {
        val json = fullOfficial.replace("\"confirmed\":true", "\"confirmed\":false")
        assertEquals(false, SofaScoreLineupParser.parse(json, "987654")?.confirmed)
    }

    @Test fun missingConfirmationFlagIsNotAssumedOfficial() {
        val json = fullOfficial.replace("\"confirmed\":true,", "")
        assertNull(SofaScoreLineupParser.parse(json, "987654")?.confirmed)
    }

    @Test fun rejectsResponseForDifferentEvent() {
        assertNull(SofaScoreLineupParser.parse(fullOfficial, "987655"))
    }

    @Test fun rejectsOneSidedOrEmptyLineup() {
        val onlyHome = fullOfficial.substringBefore("\"away\":") + "\"away\":{\"formation\":\"4-2-3-1\",\"players\":[]}}"
        assertNull(SofaScoreLineupParser.parse(onlyHome, "987654"))
    }

    @Test fun rejectsInvalidEventIdentifier() {
        assertNull(SofaScoreLineupParser.parse(fullOfficial, "987654x"))
    }
}
