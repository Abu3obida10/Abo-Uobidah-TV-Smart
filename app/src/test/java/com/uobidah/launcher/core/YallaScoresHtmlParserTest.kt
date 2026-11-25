package com.uobidah.launcher.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

class YallaScoresHtmlParserTest {
    @Test fun parsesPublicFinishedFixtureAndDoesNotTreatDateAsScore() {
        val html = """
            <html><body>
              <script>window.pref = "Africa/Cairo";</script>
              <div class="site-settings">منطقتك الزمنية Asia/Riyadh</div>
              <div class="fixture-card">
                <span>الاثنين 23-11-2026 09:00 م</span>
                <a href="/ar/championship/58553/Egyptian-League">الدوري المصري</a>
                <a href="/ar/match/60001/Al-Ahly-FC-vs-Pyramids-FC">
                  <span>الأهلي</span><b>بيراميدز</b>
                </a>
                <span class="score">2 - 0</span>
                <span>انتهت المباراة</span>
              </div>
            </body></html>
        """.trimIndent()

        val match = YallaScoresHtmlParser.parseTeamPage(html, "Africa/Cairo").single()
        assertEquals("60001", match.id)
        assertEquals("الأهلي المصري", match.home)
        assertEquals("بيراميدز", match.away)
        assertEquals(2, match.homeScore)
        assertEquals(0, match.awayScore)
        assertEquals("finished", match.status)
        assertEquals("الدوري المصري", match.league)
        assertTrue(match.url.startsWith("https://www.ysscores.com/ar/match/60001/"))
        assertTrue(match.startMs > 0L)
        // الموقع يعرض الوقت في الرياض؛ المعروض 21:00 يعني 18:00 UTC.
        assertEquals(Instant.parse("2026-11-23T18:00:00Z").toEpochMilli(), match.startMs)
    }

    @Test fun parsesArabicDigitsAndLiveMinute() {
        val html = """
            <div class="fixture-card">
              <span>٢٤-١١-٢٠٢٦ ١٠:٣٠ م</span>
              <a href="/ar/match/60002/Real-Madrid-vs-PSV">ريال مدريد ضد آيندهوفن</a>
              <span>مباشر</span><span>٦٧’</span><span class="score">١ - ١</span>
            </div>
        """.trimIndent()

        val match = YallaScoresHtmlParser.parseTeamPage(html, "Africa/Cairo").single()
        assertEquals("ريال مدريد", match.home)
        assertEquals(1, match.homeScore)
        assertEquals(1, match.awayScore)
        assertEquals("in progress", match.status)
        assertEquals("67", match.progress)
        assertTrue(match.startMs > 0L)
    }


    @Test fun getsRealMadridPsvZeroOneFromCardWhenDateLivesInTheDayHeading() {
        val html = """<div class="day-group">
            <h3 class="day-title">الثلاثاء 24 نوفمبر 2026</h3>
            <div class="fixture-card">
                <span class="match-time">21:00</span>
                <a href="/ar/match/60030/Real-Madrid-vs-PSV">ريال مدريد ضد آيندهوفن</a>
                <div class="match-result"><span>0</span><span> - </span><span>1</span></div>
                <span class="match-status">انتهت المباراة</span>
            </div>
        </div>"""

        val match = YallaScoresHtmlParser.parseTeamPage(
            html, "Africa/Cairo", LocalDate.of(2026, 11, 25)
        ).single()
        assertEquals("ريال مدريد", match.home)
        assertEquals("PSV Eindhoven", match.away)
        assertEquals(0, match.homeScore)
        assertEquals(1, match.awayScore)
        assertEquals("finished", match.status)
        assertEquals(Instant.parse("2026-11-24T19:00:00Z").toEpochMilli(), match.startMs)
        assertTrue(MatchScoreReconciler.isSameFixtureCandidate(
            match.home, match.away, match.startMs,
            "Real Madrid CF", "PSV", match.startMs + 30 * 60_000L
        ))
    }

    @Test fun duplicateResponsiveAnchorsForOneFixtureKeepTheScoreAndKickoff() {
        val html = """<section class="day-group">
            <div class="date-header"><h3>الثلاثاء 24 نوفمبر 2026</h3><a href="/ar/calendar">عرض اليوم</a></div>
            <div class="match-card">
                <a class="desktop-match" href="/ar/match/60090/Real-Madrid-vs-PSV">ريال مدريد PSV</a>
                <a class="mobile-match" href="/ar/match/60090/Real-Madrid-vs-PSV">ريال مدريد PSV</a>
                <span class="match-time">21:00</span>
                <div class="match-result"><span>0</span><span>–</span><span>1</span></div>
                <span class="match-status">انتهت المباراة</span>
            </div>
        </section>"""
        val match = YallaScoresHtmlParser.parseTeamPage(
            html, "Africa/Cairo", LocalDate.of(2026, 11, 25)
        ).single()
        assertEquals(0, match.homeScore)
        assertEquals(1, match.awayScore)
        assertEquals("finished", match.status)
        assertEquals(Instant.parse("2026-11-24T19:00:00Z").toEpochMilli(), match.startMs)
    }


    @Test fun readsZeroOneFromScoreboardWithSeparateNumericLeaves() {
        val html = """<div class="fixture-card" data-date="2026-11-24">
            <span class="kickoff-time">21:00</span>
            <a href="/ar/match/60093/Real-Madrid-vs-PSV">ريال مدريد PSV</a>
            <div class="match-scoreboard"><span>0</span><span>1</span></div>
            <span class="fixture-status">انتهت المباراة</span>
        </div>"""
        val match = YallaScoresHtmlParser.parseTeamPage(
            html, "Africa/Cairo", LocalDate.of(2026, 11, 25)
        ).single()
        assertEquals(0, match.homeScore)
        assertEquals(1, match.awayScore)
        assertEquals(Instant.parse("2026-11-24T19:00:00Z").toEpochMilli(), match.startMs)
    }

    @Test fun readsOrdinalEnglishDateFromWrappedDayHeading() {
        val html = """<div class="fixtures-day">
            <div class="calendar-heading"><h4>Tuesday, November 24th, 2026 — Fixtures</h4></div>
            <div class="fixtures-list">
                <div class="fixture-card"><span class="match-time">20:15</span>
                    <a href="/ar/match/60091/Real-Madrid-vs-PSV">Real Madrid PSV</a>
                    <span class="score">0 - 1</span>
                </div>
                <div class="fixture-card"><span class="match-time">22:00</span>
                    <a href="/ar/match/60092/Real-Madrid-vs-PSV">Real Madrid PSV</a>
                    <span class="score">1 - 0</span>
                </div>
            </div>
        </div>"""
        val matches = YallaScoresHtmlParser.parseTeamPage(
            html, "Africa/Cairo", LocalDate.of(2026, 11, 25)
        )
        assertEquals(2, matches.size)
        assertEquals(Instant.parse("2026-11-24T18:15:00Z").toEpochMilli(), matches.first { it.id == "60091" }.startMs)
        assertEquals(Instant.parse("2026-11-24T20:00:00Z").toEpochMilli(), matches.first { it.id == "60092" }.startMs)
    }

    @Test fun readsArabicDayHeadingWithoutYearUsingNearestLocalYear() {
        val html = """<section class="day-group">
            <h2 class="matches-day">الثلاثاء، 24 نوفمبر</h2>
            <div class="fixture-card">
                <span class="kickoff-time">20:30</span>
                <a href="/ar/match/60031/Real-Madrid-vs-PSV">ريال مدريد آيندهوفن</a>
                <span class="score">0 - 1</span>
            </div>
        </section>"""
        val match = YallaScoresHtmlParser.parseTeamPage(
            html, "Africa/Cairo", LocalDate.of(2026, 11, 25)
        ).single()
        assertEquals(Instant.parse("2026-11-24T18:30:00Z").toEpochMilli(), match.startMs)
        assertEquals(0, match.homeScore)
        assertEquals(1, match.awayScore)
    }


    @Test fun readsPlainTextDayHeaderBeforeTheFixtureCard() {
        val html = """<div class="day-group">
            الثلاثاء 24 نوفمبر 2026
            <div class="fixture-card">
                <span class="match-time">21:00</span>
                <a href="/ar/match/60034/Real-Madrid-vs-PSV">ريال مدريد PSV</a>
                <span class="score">0 - 1</span>
            </div>
        </div>"""
        val match = YallaScoresHtmlParser.parseTeamPage(
            html, "Africa/Cairo", LocalDate.of(2026, 11, 25)
        ).single()
        assertEquals(Instant.parse("2026-11-24T19:00:00Z").toEpochMilli(), match.startMs)
    }

    @Test fun doesNotBorrowTheDateFromAPreviousDayGroup() {
        val html = """<div class="day-group">
            <h3 class="day-title">الاثنين 23 نوفمبر 2026</h3>
            <div class="fixture-card">
                <a href="/ar/match/60035/Real-Madrid-vs-PSV">مباراة سابقة</a>
            </div>
        </div>
        <div class="day-group">
            <div class="fixture-card">
                <span class="match-time">21:00</span>
                <a href="/ar/match/60036/Real-Madrid-vs-PSV">مباراة بدون عنوان يوم</a>
            </div>
        </div>"""
        val matches = YallaScoresHtmlParser.parseTeamPage(
            html, "Africa/Cairo", LocalDate.of(2026, 11, 25)
        )
        assertEquals(2, matches.size)
        assertEquals(0L, matches.first { it.id == "60036" }.startMs)
    }

    @Test fun readsRelativeDayHeadingsForTodayAndTomorrow() {
        val todayHtml = """<div class="date-header">مباريات اليوم</div>
            <div class="fixture-card"><span class="match-time">18:00</span>
            <a href="/ar/match/60032/Real-Madrid-vs-PSV">ريال مدريد PSV</a></div>"""
        val tomorrowHtml = """<div class="date-header">مباريات غداً</div>
            <div class="fixture-card"><span class="match-time">18:00</span>
            <a href="/ar/match/60033/Real-Madrid-vs-PSV">ريال مدريد PSV</a></div>"""
        val reference = LocalDate.of(2026, 11, 25)
        val today = YallaScoresHtmlParser.parseTeamPage(todayHtml, "Africa/Cairo", reference).single()
        val tomorrow = YallaScoresHtmlParser.parseTeamPage(tomorrowHtml, "Africa/Cairo", reference).single()
        assertEquals(Instant.parse("2026-11-25T16:00:00Z").toEpochMilli(), today.startMs)
        assertEquals(Instant.parse("2026-11-26T16:00:00Z").toEpochMilli(), tomorrow.startMs)
    }

    @Test fun skipsLinksThatAreNotMatchPagesAndMalformedSlugs() {
        val html = """
            <a href="/ar/team/101745/Al-Ahly-FC">الأهلي</a>
            <a href="/ar/match/60003/unknown-match-slug">مباراة غير معروفة</a>
            <a href="/ar/match/60004/Real-Madrid-vs-PSV">ريال مدريد</a>
        """.trimIndent()

        val result = YallaScoresHtmlParser.parseTeamPage(html)
        assertEquals(1, result.size)
        assertEquals("60004", result.single().id)
    }

    @Test fun extractsOnlySafePublicTeamLogoWithMatchingAlternativeText() {
        val html = """
            <div><img alt="شعار الأهلي" src="https://imgs.ysscores.com/teams/150/al-ahly.png"></div>
            <div><img alt="Real Madrid" src="https://evil.example/real.png"></div>
        """.trimIndent()
        assertEquals(
            "https://imgs.ysscores.com/teams/150/al-ahly.png",
            YallaScoresHtmlParser.extractTeamBadge(html, listOf("الأهلي"))
        )
        assertEquals("", YallaScoresHtmlParser.extractTeamBadge(html, listOf("ريال مدريد")))
    }

    @Test fun cancelledAndPostponedStatusesAreRecognized() {
        val postponed = "<div>25-11-2026 <a href='/ar/match/60005/Al-Ahly-FC-vs-Zamalek'>الأهلي الزمالك مؤجلة</a></div>"
        val canceled = "<div>25-11-2026 <a href='/ar/match/60006/Real-Madrid-vs-PSV'>ريال مدريد آيندهوفن ملغاة</a></div>"
        assertEquals("postponed", YallaScoresHtmlParser.parseTeamPage(postponed).single().status)
        assertEquals("canceled", YallaScoresHtmlParser.parseTeamPage(canceled).single().status)
    }

    @Test fun rejectsImpossibleZeroToFortyFiveScoreAndKeepsTheRealScore() {
        val html = """<div class="fixture-card">
            <span>24-11-2026 08:30 PM</span>
            <a href="/ar/match/60007/Al-Ahly-FC-vs-Real-Madrid">الأهلي ريال مدريد</a>
            <span>0–45</span><span>مباشر 67’</span><span class="score">1 - 0</span>
        </div>"""
        val match = YallaScoresHtmlParser.parseTeamPage(html).single()
        assertEquals(1, match.homeScore)
        assertEquals(0, match.awayScore)
        assertEquals("67", match.progress)
    }

    @Test fun doesNotTreatMinuteRangeAsFootballScoreWhenNoScoreExists() {
        val html = """<div class="fixture-card">
            <span>24-11-2026</span>
            <a href="/ar/match/60008/Real-Madrid-vs-PSV">ريال مدريد آيندهوفن</a>
            <span>0–45</span><span>الشوط الأول</span>
        </div>"""
        val match = YallaScoresHtmlParser.parseTeamPage(html).single()
        assertEquals(null, match.homeScore)
        assertEquals(null, match.awayScore)
        assertEquals("in progress", match.status)
    }

    @Test fun parsesUsSlashDateAndDottedPmTime() {
        val html = """<div class="fixture-card">
            <span>11/24/2026 09.15 PM</span>
            <a href="/ar/match/60009/Al-Ahly-FC-vs-Pyramids-FC">الأهلي بيراميدز</a>
            <span class="score">1 - 1</span>
        </div>"""
        val match = YallaScoresHtmlParser.parseTeamPage(html, "Africa/Cairo").single()
        assertTrue(match.startMs > 0L)
        assertEquals(Instant.parse("2026-11-24T19:15:00Z").toEpochMilli(), match.startMs)
    }

    @Test fun parsesEnglishMonthNamesAndHamzaFinishedStatus() {
        val html = """<div class="fixture-card">
            <span>November 24, 2026 8:00 PM</span>
            <a href="/ar/match/60010/Real-Madrid-vs-PSV">ريال مدريد آيندهوفن</a>
            <span>إنتهت المباراة</span><span class="score">2 - 1</span>
        </div>"""
        val match = YallaScoresHtmlParser.parseTeamPage(html, "Africa/Cairo").single()
        assertEquals("finished", match.status)
        assertEquals(2, match.homeScore)
        assertEquals(1, match.awayScore)
        assertTrue(match.startMs > 0L)
    }

    @Test fun stoppageMinuteWithAddedTimeIsNormalized() {
        val html = """<div class="fixture-card">
            <span>24-11-2026 09:00 PM</span>
            <a href="/ar/match/60011/Real-Madrid-vs-PSV">ريال مدريد آيندهوفن</a>
            <span>مباشر</span><span>45+2’</span><span class="score">0 - 0</span>
        </div>"""
        val match = YallaScoresHtmlParser.parseTeamPage(html).single()
        assertEquals("45+2", match.progress)
    }


    @Test fun unmarkedNumericPairIsNeverTreatedAsScore() {
        val html = """<div class="fixture-card">
            <span>24-11-2026 08:30 PM</span>
            <a href="/ar/match/60014/Al-Ahly-FC-vs-Real-Madrid">الأهلي ريال مدريد</a>
            <span>إحصائية 1 - 0 في آخر خمس مباريات</span>
        </div>"""
        val match = YallaScoresHtmlParser.parseTeamPage(html).single()
        assertEquals(null, match.homeScore)
        assertEquals(null, match.awayScore)
    }

    @Test fun fortyFiveMinutesAndClockAreNeverAcceptedAsScore() {
        val html = """<div class="fixture-card">
            <span>24-11-2026 08:30 PM</span>
            <a href="/ar/match/60015/Al-Ahly-FC-vs-Real-Madrid">الأهلي ريال مدريد</a>
            <span>45:00</span><span>الشوط الأول</span>
        </div>"""
        val match = YallaScoresHtmlParser.parseTeamPage(html).single()
        assertEquals(null, match.homeScore)
        assertEquals(null, match.awayScore)
        assertEquals("in progress", match.status)
    }

    @Test fun aColonScoreLikeFragmentCannotBecomeKickoffTime() {
        val html = """<div class="fixture-card">
            <span>24-11-2026</span>
            <a href="/ar/match/60016/Al-Ahly-FC-vs-Real-Madrid">الأهلي ريال مدريد</a>
            <span>2:1</span>
        </div>"""
        val match = YallaScoresHtmlParser.parseTeamPage(html).single()
        assertEquals(null, match.homeScore)
        assertEquals(null, match.awayScore)
        assertEquals(0L, match.startMs)
    }

    @Test fun explicitlyMarkedColonScoreIsAcceptedAsScoreNotKickoff() {
        val html = """<div class="fixture-card">
            <span>24-11-2026</span>
            <a href="/ar/match/60020/Al-Ahly-FC-vs-Real-Madrid">الأهلي ريال مدريد</a>
            <span class="score">2:1</span>
        </div>"""
        val match = YallaScoresHtmlParser.parseTeamPage(html).single()
        assertEquals(2, match.homeScore)
        assertEquals(1, match.awayScore)
        assertEquals(0L, match.startMs)
    }

    @Test fun explicitScoreAttributeIsAcceptedWithoutScanningCardNumbers() {
        val html = """<div class="fixture-card" data-home-score="3" data-away-score="2">
            <span>24-11-2026 08:30 PM</span>
            <a href="/ar/match/60017/Al-Ahly-FC-vs-Real-Madrid">الأهلي ريال مدريد</a>
            <span>45:00</span><span>إحصائية 7 - 4</span>
        </div>"""
        val match = YallaScoresHtmlParser.parseTeamPage(html).single()
        assertEquals(3, match.homeScore)
        assertEquals(2, match.awayScore)
    }

    @Test fun negatedFinishedStatusIsNotAccepted() {
        val english = "<div>24-11-2026 <a href='/ar/match/60018/Real-Madrid-vs-PSV'>Real Madrid PSV</a> Not Finished</div>"
        val arabic = "<div>24-11-2026 <a href='/ar/match/60019/Real-Madrid-vs-PSV'>Real Madrid PSV</a> لم تنته المباراة</div>"
        assertEquals("", YallaScoresHtmlParser.parseTeamPage(english).single().status)
        assertEquals("", YallaScoresHtmlParser.parseTeamPage(arabic).single().status)
    }



    @Test fun competitionTitleContainingFinalDoesNotEndTheMatch() {
        val html = """<div class="fixture-card">
            <span>24-11-2026 08:30 PM</span>
            <a href="/ar/championship/100/FA-Cup-Final">FA Cup Final</a>
            <a href="/ar/match/60021/Al-Ahly-FC-vs-Real-Madrid">الأهلي ريال مدريد</a>
        </div>"""
        val match = YallaScoresHtmlParser.parseTeamPage(html).single()
        assertEquals("", match.status)
    }

    @Test fun explicitlyMarkedFinalStatusIsAccepted() {
        val html = """<div class="fixture-card">
            <span>24-11-2026 08:30 PM</span>
            <a href="/ar/match/60022/Al-Ahly-FC-vs-Real-Madrid">الأهلي ريال مدريد</a>
            <span class="match-status">Final</span>
        </div>"""
        val match = YallaScoresHtmlParser.parseTeamPage(html).single()
        assertEquals("finished", match.status)
    }
    @Test fun realYallaStatusCodesAndStructuredScoresAreKeptAsAnAtomicPair() {
        val html = """<div class="day-group">
            <span class="date">الثلاثاء 24 نوفمبر 2026</span>
            <a class="ajax-match-item" data-status="2" href="/ar/match/60101/Real-Madrid-vs-PSV">
                <span class="first-team-result">0</span><span class="clock" data-minutes="45">45:00</span><span class="second-team-result">1</span>
                <span>مباشر</span>
            </a>
            <a class="ajax-match-item" data-status="0" href="/ar/match/60102/Real-Madrid-vs-PSV">
                <span class="first-team-result">0</span><span class="second-team-result">0</span><span>غداً 21:00</span>
            </a>
            <a class="ajax-match-item" data-status="4" href="/ar/match/60103/Real-Madrid-vs-PSV">
                <span class="first-team-result">0</span><span class="second-team-result">1</span><span>انتهت</span>
            </a>
        </div>"""
        val parsed = YallaScoresHtmlParser.parseTeamPage(html, "Africa/Cairo", LocalDate.of(2026, 11, 25)).associateBy { it.id }
        assertEquals("in progress", parsed.getValue("60101").status)
        assertEquals(0, parsed.getValue("60101").homeScore)
        assertEquals(1, parsed.getValue("60101").awayScore)
        assertEquals("scheduled", parsed.getValue("60102").status)
        assertEquals(null, parsed.getValue("60102").homeScore)
        assertEquals(null, parsed.getValue("60102").awayScore)
        assertEquals("finished", parsed.getValue("60103").status)
        assertEquals(0, parsed.getValue("60103").homeScore)
        assertEquals(1, parsed.getValue("60103").awayScore)
    }

    @Test fun conflictingDuplicateResponsiveScorePairsAreSuppressedInsteadOfArbitrarilyChosen() {
        val html = """<div class="day-group">
            <h3>الثلاثاء 24 نوفمبر 2026</h3>
            <div class="match-row">
                <a class="ajax-match-item" data-status="4" href="/ar/match/60104/Real-Madrid-vs-PSV"><span class="first-team-result">0</span><span class="second-team-result">1</span></a>
                <a class="ajax-match-item" data-status="4" href="/ar/match/60104/Real-Madrid-vs-PSV"><span class="first-team-result">1</span><span class="second-team-result">0</span></a>
            </div>
        </div>"""
        val matches = YallaScoresHtmlParser.parseTeamPage(html, "Africa/Cairo", LocalDate.of(2026, 11, 25))
        assertEquals(1, matches.size)
        assertEquals(null, matches.single().homeScore)
        assertEquals(null, matches.single().awayScore)
    }

}
