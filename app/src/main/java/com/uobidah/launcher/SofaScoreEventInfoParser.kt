package com.uobidah.launcher

import org.json.JSONObject

/** Parses supplemental details only for the exact SofaScore event that was requested. */
internal object SofaScoreEventInfoParser {
    fun parse(
        json: String,
        requestedEventId: String,
        expectedHome: String,
        expectedAway: String,
        expectedStartMs: Long
    ): SofaScoreEventInfo? {
        val requested = requestedEventId.removePrefix("sofascore|").trim()
        if (requested.isEmpty() || !requested.all { it.isDigit() }) return null
        if (expectedHome.isBlank() || expectedAway.isBlank() || expectedStartMs <= 0L) return null
        val root = runCatching { JSONObject(json) }.getOrNull() ?: return null
        val event = root.optJSONObject("event") ?: return null
        val returnedId = event.optLong("id", 0L)
        if (returnedId <= 0L || returnedId.toString() != requested) return null

        val home = event.optJSONObject("homeTeam")?.optString("name").orEmpty()
        val away = event.optJSONObject("awayTeam")?.optString("name").orEmpty()
        val startMs = event.optLong("startTimestamp", 0L).takeIf { it > 0L }?.times(1000L) ?: 0L
        if (home.isBlank() || away.isBlank() || startMs <= 0L ||
            !com.uobidah.launcher.core.MatchScoreReconciler.isSameFixtureCandidate(
                expectedHome, expectedAway, expectedStartMs, home, away, startMs
            )) return null

        val venueObj = event.optJSONObject("venue")
        val refereeValue = event.opt("referee")
        val referee = when (refereeValue) {
            is JSONObject -> clean(refereeValue.optString("name"))
            is String -> clean(refereeValue)
            else -> ""
        }
        val managers = event.optJSONObject("managers")
        val roundInfo = event.optJSONObject("roundInfo")
        val roundText = clean(roundInfo?.optString("name").orEmpty()).ifBlank {
            val round = roundInfo?.optInt("round", -1) ?: event.optInt("round", -1)
            if (round > 0) "الجولة $round" else clean(event.optString("roundName"))
        }
        val attendance = event.optInt("attendance", 0).takeIf { it > 0 }?.toString().orEmpty()
        return SofaScoreEventInfo(
            eventId = requested,
            tournament = clean(event.optJSONObject("tournament")?.optString("name").orEmpty()),
            round = roundText,
            venue = clean(venueObj?.optString("name").orEmpty()),
            city = clean(venueObj?.optString("city").orEmpty()),
            referee = referee,
            attendance = attendance,
            homeManager = clean(managers?.optJSONObject("home")?.optString("name").orEmpty()),
            awayManager = clean(managers?.optJSONObject("away")?.optString("name").orEmpty())
        )
    }

    private fun clean(value: String): String = value.trim().take(140).takeUnless { it.equals("null", true) } ?: ""
}
