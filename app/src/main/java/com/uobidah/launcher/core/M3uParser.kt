package com.uobidah.launcher.core

import java.io.BufferedReader
import java.io.File
import java.io.Reader
import java.io.StringReader
import java.net.URI
import java.util.Locale

 data class M3uEntry(
    val name: String,
    val url: String,
    val group: String = "",
    val tvgId: String = "",
    val logo: String = ""
)

/**
 * Streaming M3U/M3U8 reader. It supports UTF-8 BOM, CRLF, attributes in any order,
 * quoted commas and EXTGRP, while avoiding a second in-memory copy of large playlists.
 */
object M3uParser {
    private val attribute = Regex("(?i)([a-z0-9_-]+)\\s*=\\s*(?:\"([^\"]*)\"|'([^']*)'|([^\\s,]+))")
    private const val MAX_LINE_CHARS = 16_384
    private const val MAX_NAME_CHARS = 220
    private const val MAX_GROUP_CHARS = 120
    private const val MAX_TVG_ID_CHARS = 180
    private const val MAX_LOGO_CHARS = 500

    fun parse(raw: String, maxEntries: Int = 100_000): List<M3uEntry> =
        parse(StringReader(raw), maxEntries)

    fun parse(reader: Reader, maxEntries: Int = 100_000): List<M3uEntry> {
        require(maxEntries in 1..300_000) { "invalid entry limit" }
        // BufferedReader.readLine() allocates the whole line before a length check.
        // A hostile/broken provider could therefore turn one 64 MB line into a RAM spike.
        val input = BoundedLineReader(reader)
        val out = LinkedHashMap<String, M3uEntry>()
        var pendingName = ""
        var pendingGroup = ""
        var pendingTvgId = ""
        var pendingLogo = ""
        while (true) {
            val sourceLine = input.readLine(MAX_LINE_CHARS) ?: break
            // Overlong lines are fully drained but never materialised in memory.
            if (sourceLine.tooLong) {
                pendingName = ""; pendingGroup = ""; pendingTvgId = ""; pendingLogo = ""
                continue
            }
            val line = sourceLine.text.trim().removePrefix("\uFEFF").replace("\u0000", "")
            if (line.isEmpty()) continue
            when {
                line.startsWith("#EXTINF", ignoreCase = true) -> {
                    val comma = firstUnquotedComma(line)
                    val metadata = if (comma >= 0) line.substring(0, comma) else line
                    pendingName = if (comma >= 0) line.substring(comma + 1).trim() else ""
                    val attrs = LinkedHashMap<String, String>()
                    for (m in attribute.findAll(metadata)) {
                        val value = listOf(m.groupValues[2], m.groupValues[3], m.groupValues[4])
                            .firstOrNull { it.isNotEmpty() }.orEmpty()
                        attrs[m.groupValues[1].lowercase(Locale.ROOT)] = value
                    }
                    if (pendingName.isBlank()) pendingName = attrs["tvg-name"].orEmpty()
                    pendingGroup = attrs["group-title"].orEmpty()
                    pendingTvgId = attrs["tvg-id"].orEmpty()
                    pendingLogo = attrs["tvg-logo"].orEmpty().trim().takeIf { it.length <= MAX_LOGO_CHARS && (it.startsWith("https://", true) || it.startsWith("http://", true)) }.orEmpty()
                }
                line.startsWith("#EXTGRP:", ignoreCase = true) -> {
                    pendingGroup = line.substringAfter(':').trim().ifBlank { pendingGroup }
                }
                line.startsWith("#") -> Unit
                else -> {
                    val uri = validHttpUri(line)
                    if (uri == null) {
                        pendingName = ""; pendingGroup = ""; pendingTvgId = ""; pendingLogo = ""
                        continue
                    }
                    val url = uri.toASCIIString()
                    val name = pendingName.ifBlank { uri.path?.substringAfterLast('/')?.ifBlank { null } ?: url }
                    val boundedName = name.take(MAX_NAME_CHARS)
                    val boundedGroup = pendingGroup.take(MAX_GROUP_CHARS)
                    val boundedTvgId = pendingTvgId.take(MAX_TVG_ID_CHARS)
                    // The same URL may intentionally label two channels/languages/groups.
                    // Collapse only an identical row, not every reused stream endpoint.
                    val entry = M3uEntry(boundedName, url, boundedGroup, boundedTvgId, pendingLogo)
                    out.putIfAbsent(identity(entry), entry)
                    require(out.size <= maxEntries) { "playlist contains too many channels (limit: $maxEntries)" }
                    pendingName = ""; pendingGroup = ""; pendingTvgId = ""; pendingLogo = ""
                }
            }
        }
        return out.values.toList()
    }

    /** Stable row identity shared by parsing and the UI; URL alone is not a channel identity. */
    fun identity(entry: M3uEntry): String = identity(entry.url, entry.tvgId, entry.group, entry.name)

    fun identity(url: String, tvgId: String = "", group: String = "", name: String = ""): String =
        listOf(url.trim(), tvgId.trim().lowercase(Locale.ROOT), group.trim().lowercase(Locale.ROOT), name.trim().lowercase(Locale.ROOT))
            .joinToString("\u001f")

    fun hasM3uHeader(raw: String): Boolean = StringReader(raw).use { hasM3uHeader(it) }

    fun hasM3uHeader(file: File): Boolean = file.bufferedReader(Charsets.UTF_8).use { hasM3uHeader(it) }

    private fun hasM3uHeader(reader: Reader): Boolean {
        val input = BoundedLineReader(reader)
        while (true) {
            val line = input.readLine(MAX_LINE_CHARS) ?: return false
            if (line.tooLong) return false
            val candidate = line.text.trim().removePrefix("\uFEFF").trim()
            if (candidate.isNotBlank()) {
                // IPTV providers append EPG/refresh metadata using spaces, commas,
                // semicolons or pipes, e.g. #EXTM3U,url-tvg="...". Accept these
                // suffixes while rejecting lookalikes such as #EXTM3Ubroken.
                return Regex("(?i)^#EXTM3U(?:$|[\\s,;|].*)$").matches(candidate)
            }
        }
    }

    private data class BoundedLine(val text: String, val tooLong: Boolean)

    /** Reads at most maxChars into a StringBuilder and drains excess characters safely. */
    private class BoundedLineReader(reader: Reader) {
        private val input = if (reader is BufferedReader) reader else reader.buffered()
        private val chunk = CharArray(8192)
        private var position = 0
        private var limit = 0
        private var pushed = -2

        private fun nextChar(): Int {
            if (pushed != -2) return pushed.also { pushed = -2 }
            if (position >= limit) {
                limit = input.read(chunk)
                position = 0
                if (limit < 0) return -1
            }
            return chunk[position++].code
        }

        fun readLine(maxChars: Int): BoundedLine? {
            val value = StringBuilder(minOf(256, maxChars))
            var sawAny = false
            var tooLong = false
            while (true) {
                val current = nextChar()
                if (current < 0) {
                    if (!sawAny) return null
                    return BoundedLine(value.toString(), tooLong)
                }
                sawAny = true
                when (current.toChar()) {
                    '\n' -> return BoundedLine(value.toString(), tooLong)
                    '\r' -> {
                        val following = nextChar()
                        if (following >= 0 && following != '\n'.code) pushed = following
                        return BoundedLine(value.toString(), tooLong)
                    }
                    else -> if (value.length < maxChars) value.append(current.toChar()) else tooLong = true
                }
            }
        }
    }

    private fun firstUnquotedComma(value: String): Int {
        var quote: Char? = null
        for (i in value.indices) {
            val c = value[i]
            if (quote == null && (c == '\'' || c == '"')) quote = c
            else if (quote == c) quote = null
            else if (quote == null && c == ',') return i
        }
        return -1
    }

    private fun validHttpUri(value: String): URI? {
        if (value.length > 8_192) return null
        val uri = runCatching { URI(value.trim()) }.getOrNull() ?: return null
        val scheme = uri.scheme?.lowercase(Locale.ROOT)
        if (scheme != "http" && scheme != "https") return null
        if (uri.host.isNullOrBlank() || uri.rawUserInfo != null || uri.port !in -1..65535) return null
        return uri
    }
}
