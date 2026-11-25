package com.uobidah.launcher.core

import org.junit.Assert.*
import org.junit.Test

/** Fixtures are real public ysscores team pages captured on 2026-11-24 (test data, not current scores). */
class YallaRealCaptureTest {
    private fun load(n: String) = javaClass.classLoader!!.getResourceAsStream(n)!!.readBytes().toString(Charsets.UTF_8)
    /** مخرجات تشخيصية تحت build/test-artifacts (تُنشأ عند الحاجة)؛ لا يُكتب شيء في /tmp الذي قد لا يوجد. */
    private fun diag(name: String, text: String) {
        java.io.File("build/test-artifacts").apply { mkdirs() }.let { java.io.File(it, name).writeText(text) }
    }

    @Test fun upcomingRowsAreScheduledWithKickoffAndNeverFakeScores() {
        for (f in listOf("yalla_real_realmadrid_page.html", "yalla_real_manutd_page.html")) {
            val list = YallaScoresHtmlParser.parseTeamPage(load(f), "Africa/Cairo")
            val up = list.filter { it.status == "scheduled" }
            assertTrue("$f has upcoming rows", up.size >= 4)
            for (m in up) { assertNull(m.homeScore); assertNull(m.awayScore); assertTrue(m.startMs > 0L) }
        }
    }

    @Test fun liveRowKeepsZeroOneAndFinishedRowsKeepScores() {
        val list = YallaScoresHtmlParser.parseTeamPage(load("yalla_real_realmadrid_page.html"), "Africa/Cairo")
        val live = list.first { it.status == "in progress" }
        assertEquals(0, live.homeScore); assertEquals(1, live.awayScore)
        val celta = list.first { it.away.contains("Celta", true) || it.home.contains("Celta", true) }
        assertEquals("finished", celta.status)
        assertEquals(2, celta.homeScore ?: celta.awayScore); assertEquals(5, list.count { it.status == "finished" })
    }


    @Test fun contradictoryResponsiveStatusesSuppressScoreAndDoNotPromoteOneCopy() {
        val html = """<div class="day-group"><h3>الثلاثاء 24 نوفمبر 2026</h3><div class="row">
            <a class="ajax-match-item" data-status="2" href="/ar/match/60105/Real-Madrid-vs-PSV"><span class="first-team-result">0</span><span class="second-team-result">1</span></a>
            <a class="ajax-match-item" data-status="4" href="/ar/match/60105/Real-Madrid-vs-PSV"><span class="first-team-result">0</span><span class="second-team-result">1</span></a>
        </div></div>"""
        val match = YallaRealPageParser.parseTeamPage(html, "Africa/Cairo").single()
        assertEquals("", match.status)
        assertNull(match.homeScore)
        assertNull(match.awayScore)
    }

    @Test fun realPagesYieldTeamBadgeUrlsOnlyFromTheYsscoresHosts() {
        for (f in listOf("yalla_real_realmadrid_page.html", "yalla_real_manutd_page.html")) {
            val html = load(f)
            val list = YallaScoresHtmlParser.parseTeamPage(html, "Africa/Cairo")
            val names = list.flatMap { listOf(it.home, it.away) }.distinct()
            val map = YallaScoresHtmlParser.extractBadgeMap(html, names)
            diag("badge_$f.txt", "ALLNAMES=" + names.joinToString(" ; ") + "\nnames=" + names.size + " badges=" + map.size + "\n" + map.entries.joinToString("\n") { it.key + " -> " + it.value })
            assertTrue("$f: expected at least one team badge from the real capture", map.isNotEmpty())
            for (u in map.values) assertTrue("badge host must be ysscores: $u", u.startsWith("https://imgs.ysscores.com/") || u.startsWith("https://www.ysscores.com/") || u.startsWith("https://ysscores.com/"))
        }
    }

    @Test fun everyRealRowCarriesDistinctHomeAndAwayBadgesFromYsscores() {
        for (f in listOf("yalla_real_realmadrid_page.html", "yalla_real_manutd_page.html")) {
            val list = YallaScoresHtmlParser.parseTeamPage(load(f), "Africa/Cairo")
            diag("rowbadges_$f.txt", list.joinToString("\n") { it.home + " | " + it.homeBadge + " || " + it.away + " | " + it.awayBadge })
            for (m in list) {
                assertTrue("$f ${m.home}: home badge missing", m.homeBadge.startsWith("https://imgs.ysscores.com/"))
                assertTrue("$f ${m.away}: away badge missing", m.awayBadge.startsWith("https://imgs.ysscores.com/"))
                assertTrue("$f ${m.id}: home and away badge must differ", m.homeBadge != m.awayBadge)
            }
        }
    }
}
