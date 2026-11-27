package com.uobidah.launcher

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

// Robolectric: org.json الحقيقي (نسخة JVM من android.jar ترفع not-mocked)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class FotMobProviderTest {

    private val dayJson = """{"leagues":[
      {"name":"Premier League","ccode":"EGY","id":519,"matches":[
        {"id":4600001,"home":{"name":"Al Ahly","id":8455,"score":2},"away":{"name":"Zamalek","id":8457,"score":1},
         "status":{"utcTime":"2026-11-27T18:00:00.000Z","finished":true,"started":true,"cancelled":false,"scoreStr":"2 - 1"}},
        {"id":4600002,"home":{"name":"Pyramids FC","id":9991},"away":{"name":"Ismaily","id":9992},
         "status":{"utcTime":"2026-11-28T17:00:00.000Z","finished":false,"started":false,"cancelled":false}}]},
      {"name":"Premier League","ccode":"ENG","id":47,"matches":[
        {"id":5795533,"home":{"name":"Nottingham Forest","id":10203},"away":{"name":"Chelsea","id":8455},
         "status":{"utcTime":"2026-11-27T20:00:00.000Z","finished":false,"started":true,"cancelled":false,"liveTime":{"short":"67'"}}}]}]}"""

    @Test fun parsesFixturesWithArabicLeaguesCrestsAndStates() {
        val matches = FotMobProvider.parseMatches(JSONObject(dayJson))
        assertEquals(3, matches.size)
        val ahly = matches[0]
        assertEquals("الدوري المصري", ahly.league)
        assertEquals("FT", ahly.status)
        assertEquals(2, ahly.homeScore)
        assertEquals("https://images.fotmob.com/image_resources/logo/teamlogo/8455.png", ahly.homeBadge)
        assertEquals("fotmob", ahly.source)
        assertTrue(ahly.startMs > 0L)
        val epl = matches[2]
        assertEquals("الدوري الإنجليزي", epl.league)
        assertEquals("live", epl.status)
        assertEquals("67'", epl.progress)
        assertEquals(null, epl.homeScore)
        val upcoming = matches[1]
        assertEquals("", upcoming.status)
        assertEquals(null, upcoming.awayScore)
    }

    private val detailsJson = """{"content":{"lineup":{
      "homeTeam":{"id":1,"name":"Home","formation":"4-3-3","starters":[
        {"id":181069,"name":"Matz Sels","shirtNumber":"26","verticalLayout":{"x":0.5,"y":0.1}}],
        "subs":[{"id":7,"name":"Bench Man","shirtNumber":"9"}]},
      "awayTeam":{"id":2,"name":"Away","formation":"4-4-2","starters":[
        {"id":55,"name":"Away Keeper","shirtNumber":"1","verticalLayout":{"x":0.5,"y":0.9}}],"subs":[]}},
      "matchFacts":{"events":[{"type":"Goal","time":23,"player":{"name":"Scorer"},"isHome":true},
        {"type":"Card","time":40,"card":"Yellow","player":{"name":"Booked"},"isHome":false}]}},
      "general":{"status":{"shortName":"2nd half"},"homeScore":1,"awayScore":0}}"""

    @Test fun parsesFormationPitchCoordsAndTimeline() {
        val facts = FotMobProvider.parseFacts(JSONObject(detailsJson))
        assertEquals("4-3-3", facts.homeFormation)
        assertEquals("4-4-2", facts.awayFormation)
        val keeper = facts.players.first { it.name == "Matz Sels" }
        assertTrue(keeper.home)
        assertEquals(0.5f, keeper.x, 0.001f)
        assertEquals(0.1f, keeper.y, 0.001f)
        assertTrue(facts.players.first { it.name == "Bench Man" }.sub)
        assertEquals(2, facts.timeline.size)
        assertEquals("هدف", facts.timeline[0].kind)
        assertEquals("23", facts.timeline[0].minute)
        assertEquals(1, facts.homeScore)
    }
}
