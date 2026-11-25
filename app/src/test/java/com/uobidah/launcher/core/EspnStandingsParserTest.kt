package com.uobidah.launcher.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * الـfixture بصيغة ESPN المعروفة (rank داخل stats، note كائن، الـentries أبجدية).
 * ملاحظة صريحة: الملف أُعيد بناؤه بحسب بنية الرد، وليس لقطة حيّة مأخوذة من الشبكة.
 * يجب استبداله بلقطة حقيقية من الجهاز عند أول اتصال (انظر tools/field_tests).
 */
class EspnStandingsParserTest {
    private val json = javaClass.classLoader!!.getResourceAsStream("espn_standings_eng1_shape.json")!!
        .readBytes().toString(Charsets.UTF_8)

    @Test fun ranksComeFromStatsNotFromEntryLevel() {
        val rows = EspnStandingsParser.parse(json)
        assertEquals(9, rows.size)
        assertTrue("لا مركز صفر", rows.none { it.rank == 0 })
    }

    @Test fun orderFollowsCompetitionRankNotAlphabet() {
        val rows = EspnStandingsParser.parse(json)
        assertEquals(listOf(1, 2, 3, 4, 5, 7, 8, 9, 20), rows.map { it.rank })
        assertEquals("Arsenal", rows.first().team)
        assertEquals("Manchester City", rows[1].team)
        assertEquals("Wolverhampton Wanderers", rows.last().team)
    }

    @Test fun pointsAndGoalDifferenceAreRead() {
        val ars = EspnStandingsParser.parse(json).first { it.teamId == "359" }
        assertEquals(29, ars.points)
        assertEquals(12, ars.played)
        assertEquals(26, ars.goalsFor)
        assertEquals(8, ars.goalsAgainst)
        assertEquals(18, ars.goalDiff)
        assertEquals(9, ars.wins); assertEquals(2, ars.draws); assertEquals(1, ars.losses)
        assertEquals("Champions League", ars.note)
    }

    @Test fun negativeGoalDifferenceKeepsItsSign() {
        val wolves = EspnStandingsParser.parse(json).first { it.teamId == "371" }
        assertEquals(-23, wolves.goalDiff)
    }

    @Test fun missingRankFallsBackToPointsThenGdAndNumbersRows() {
        fun e(id: String, n: String, pts: Int, gd: Int) =
            """{"team":{"id":"$id","displayName":"$n"},"stats":[{"name":"points","value":$pts},{"name":"pointDifferential","value":$gd},{"name":"gamesPlayed","value":10}]}"""
        val j = """{"standings":{"entries":[${e("1","A",20,5)},${e("2","B",25,1)},${e("3","C",20,9)}]}}"""
        val rows = EspnStandingsParser.parse(j)
        assertEquals(listOf("B", "C", "A"), rows.map { it.team })
        assertEquals(listOf(1, 2, 3), rows.map { it.rank })
    }

    @Test fun noEntriesIsEmptyNotFake() = assertTrue(EspnStandingsParser.parse("""{"children":[]}""").isEmpty())

    @Test(expected = MiniJson.JsonException::class)
    fun garbageThrowsSoUiShowsFailure() { EspnStandingsParser.parse("<html>blocked</html>") }
}
