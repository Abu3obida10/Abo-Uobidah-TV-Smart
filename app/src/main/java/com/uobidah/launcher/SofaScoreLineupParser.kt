package com.uobidah.launcher

import org.json.JSONArray
import org.json.JSONObject

/**
 * Strict parser for SofaScore's event lineups response.
 * It never converts an absent/partial lineup into an official one and rejects a response
 * that explicitly belongs to another event.
 */
internal object SofaScoreLineupParser {
    fun parse(json: String, requestedEventId: String): SofaScoreLineupData? {
        val requested = requestedEventId.removePrefix("sofascore|").trim()
        if (requested.isEmpty() || !requested.all { it.isDigit() }) return null
        val root = runCatching { JSONObject(json) }.getOrNull() ?: return null

        val returnedId = root.optJSONObject("event")?.optLong("id", 0L)
            ?: root.optLong("eventId", 0L)
        if (returnedId > 0L && returnedId.toString() != requested) return null

        val home = root.optJSONObject("home") ?: return null
        val away = root.optJSONObject("away") ?: return null
        val homePlayers = parseSide(home, true)
        val awayPlayers = parseSide(away, false)
        // Require player entries on both sides. A one-sided payload is not a complete lineup.
        if (homePlayers.isEmpty() || awayPlayers.isEmpty()) return null

        val confirmed = if (root.has("confirmed") && !root.isNull("confirmed")) root.optBoolean("confirmed") else null
        return SofaScoreLineupData(
            eventId = requested,
            confirmed = confirmed,
            homeFormation = clean(home.optString("formation")),
            awayFormation = clean(away.optString("formation")),
            players = homePlayers + awayPlayers
        )
    }

    private fun parseSide(side: JSONObject, isHome: Boolean): List<LineupPlayer> {
        val array: JSONArray = side.optJSONArray("players") ?: return emptyList()
        val result = ArrayList<LineupPlayer>(array.length())
        val seen = HashSet<String>()
        for (i in 0 until array.length()) {
            val wrapper = array.optJSONObject(i) ?: continue
            val player = wrapper.optJSONObject("player") ?: wrapper
            val name = clean(player.optString("name")).ifBlank { clean(wrapper.optString("name")) }
            if (name.isBlank() || name.equals("null", true)) continue
            val number = firstText(
                wrapper.optString("shirt_number"), wrapper.optString("shirtNumber"),
                wrapper.optString("jerseyNumber"), player.optString("jersey_number"),
                player.optString("jerseyNumber"), wrapper.optString("jersey_number"), wrapper.optString("number")
            )
            val identity = (number + "|" + name).lowercase()
            if (!seen.add(identity)) continue
            val position = firstText(
                wrapper.optString("position"), player.optString("position"),
                player.optString("position_name")
            )
            result.add(
                LineupPlayer(
                    name = name,
                    position = position,
                    home = isHome,
                    sub = wrapper.optBoolean("substitute", player.optBoolean("substitute", false)),
                    number = number,
                    captain = wrapper.optBoolean("captain", player.optBoolean("captain", false))
                )
            )
        }
        return result
    }

    private fun firstText(vararg values: String): String =
        values.asSequence().map(::clean).firstOrNull { it.isNotBlank() && !it.equals("null", true) }.orEmpty()

    private fun clean(value: String?): String = value.orEmpty().trim().take(100)
}
