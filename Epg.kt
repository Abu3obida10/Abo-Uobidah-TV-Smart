package com.uobidah.launcher

import android.content.Context
import android.util.Xml
import java.io.BufferedInputStream
import java.io.File
import java.io.FileInputStream
import java.io.FilterInputStream
import java.io.IOException
import java.io.InputStream
import java.text.SimpleDateFormat
import java.text.ParsePosition
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.ConcurrentHashMap
import java.util.zip.GZIPInputStream

/** دليل برامج XMLTV (اختياري) لعرض «الآن / بعده» للقنوات. */
object Epg {
    class Prog(val start: Long, val stop: Long, val title: String)

    private data class Snapshot(
        val byId: Map<String, List<Prog>>,
        val nameToId: Map<String, String>
    )

    @Volatile private var byId: Map<String, List<Prog>> = emptyMap()
    @Volatile private var nameToId: Map<String, String> = emptyMap()
    @Volatile private var activeUrl: String = ""
    private val loadGeneration = AtomicLong(0L)
    private val publishLock = Any()
    private val cacheLocks = ConcurrentHashMap<String, Any>()

    private const val MAX_RAW_BYTES = 24L * 1024L * 1024L
    private const val MAX_EXPANDED_BYTES = 96L * 1024L * 1024L
    private const val MAX_PROGRAMMES = 150_000
    private const val WINDOW_MS = 24 * 3600_000L

    fun hasData(): Boolean = byId.isNotEmpty()
    fun channelCount(): Int = byId.size

    /** Clear stale schedule data when the user disables EPG or changes its source. */
    fun clear() {
        loadGeneration.incrementAndGet()
        synchronized(publishLock) {
            activeUrl = ""
            byId = emptyMap()
            nameToId = emptyMap()
        }
    }

    fun norm(s: String): String = s.lowercase(Locale.ROOT).filter { it.isLetterOrDigit() }

    private fun cacheFile(ctx: Context, url: String): File {
        val digest = java.security.MessageDigest.getInstance("SHA-256")
            .digest(url.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it.toInt() and 0xff) }
        return File(ctx.filesDir, "epg_$digest.xmltv")
    }

    private fun legacyCacheFile(ctx: Context, url: String): File =
        File(ctx.filesDir, "epg_" + Integer.toHexString(url.hashCode()) + ".bin")

    /**
     * Background-only load. XMLTV is streamed from disk (including gzip) with a hard
     * expanded-size ceiling. Only the most recently requested URL may replace live EPG data.
     */
    fun load(ctx: Context, url: String, force: Boolean): Boolean {
        if (!(url.startsWith("http://", true) || url.startsWith("https://", true))) return false
        val token = loadGeneration.incrementAndGet()
        synchronized(publishLock) {
            if (activeUrl != url) {
                activeUrl = url
                byId = emptyMap()
                nameToId = emptyMap()
            }
        }
        val target = cacheFile(ctx, url)
        val legacy = legacyCacheFile(ctx, url)

        var cachedSnapshot = readCacheWithRecovery(target)
        // Migrate old cache naming only after parsing it successfully.
        if (cachedSnapshot == null && !target.exists() && legacy.isFile) {
            val migrated = File(ctx.filesDir, target.name + ".migrate-" + token)
            try {
                val snapshot = parseFile(legacy)
                synchronized(publishLock) {
                    if (loadGeneration.get() == token && legacy.copyTo(migrated, overwrite = true).isFile && migrated.renameTo(target)) {
                        cachedSnapshot = snapshot
                    }
                }
            } catch (_: Exception) {
                // Leave the legacy file untouched if it cannot be parsed safely.
            } finally {
                migrated.delete()
            }
        }

        val cacheAgeMs = System.currentTimeMillis() - target.lastModified()
        val cacheFresh = cachedSnapshot != null && target.isFile && cacheAgeMs in 0 until 6 * 3600_000L
        if (cacheFresh && !force) return publish(token, cachedSnapshot!!)

        val temp = File(ctx.cacheDir, "epg-${java.lang.Long.toHexString(System.nanoTime())}.download")
        try {
            Net.downloadToFile(url, temp, timeoutMs = 45_000, maxBytes = MAX_RAW_BYTES)
            val snapshot = parseFile(temp) // reject malformed/non-XMLTV feeds before promotion
            synchronized(publishLock) {
                if (loadGeneration.get() != token) return false
                promoteCache(target, temp)
                byId = snapshot.byId
                nameToId = snapshot.nameToId
            }
            return true
        } catch (_: Exception) {
            // Keep the last good cache and current in-memory EPG when the provider fails.
            val snapshot = readCacheWithRecovery(target) ?: cachedSnapshot
            if (snapshot != null) return publish(token, snapshot)
            return false
        } finally {
            temp.delete()
        }
    }

    /** Recover a backup left by a process kill between the two atomic rename operations. */
    private fun readCacheWithRecovery(target: File): Snapshot? {
        val parent = target.parentFile ?: return null
        val lock = cacheLocks.computeIfAbsent(target.absolutePath) { Any() }
        synchronized(lock) {
            val primary = try { if (target.isFile) parseFile(target) else null } catch (_: Exception) { null }
            if (primary != null) {
                parent.listFiles { _, name -> name.startsWith(target.name + ".bak-") }?.forEach { it.delete() }
                return primary
            }
            val backups = parent.listFiles { _, name -> name.startsWith(target.name + ".bak-") }
                ?.sortedByDescending { it.lastModified() }.orEmpty()
            for (backup in backups) {
                try { parseFile(backup) } catch (_: Exception) { continue }
                val restore = File(parent, target.name + ".restore-" + java.lang.Long.toHexString(System.nanoTime()))
                val damaged = File(parent, target.name + ".damaged-" + java.lang.Long.toHexString(System.nanoTime()))
                try {
                    backup.copyTo(restore, overwrite = true)
                    if (target.exists() && !target.renameTo(damaged)) continue
                    if (!restore.renameTo(target)) {
                        if (damaged.exists() && !target.exists()) damaged.renameTo(target)
                        continue
                    }
                    val verified = try { parseFile(target) } catch (_: Exception) { null }
                    if (verified != null) {
                        backup.delete()
                        damaged.delete()
                        return verified
                    }
                    target.delete()
                    if (damaged.exists()) damaged.renameTo(target)
                } catch (_: Exception) {
                    if (damaged.exists() && !target.exists()) damaged.renameTo(target)
                } finally {
                    restore.delete()
                }
            }
            return null
        }
    }

    private fun publish(token: Long, snapshot: Snapshot): Boolean = synchronized(publishLock) {
        if (loadGeneration.get() != token) return@synchronized false
        byId = snapshot.byId
        nameToId = snapshot.nameToId
        true
    }

    /** Replace the on-disk cache without deleting the last known-good file on failure. */
    private fun promoteCache(target: File, temp: File) {
        val parent = target.parentFile ?: throw IOException("EPG cache directory unavailable")
        if (!parent.exists() && !parent.mkdirs()) throw IOException("cannot create EPG cache directory")
        val lock = cacheLocks.computeIfAbsent(target.absolutePath) { Any() }
        synchronized(lock) {
            val backup = File(parent, target.name + ".bak-" + java.lang.Long.toHexString(System.nanoTime()))
            val hadOld = target.exists()
            if (hadOld && !target.renameTo(backup)) throw IOException("cannot protect previous EPG cache")
            if (!temp.renameTo(target)) {
                if (hadOld && backup.exists()) backup.renameTo(target)
                throw IOException("cannot install new EPG cache")
            }
            if (!target.isFile || target.length() <= 0L) {
                target.delete()
                if (hadOld && backup.exists()) backup.renameTo(target)
                throw IOException("installed EPG cache failed verification")
            }
            backup.delete()
        }
    }

    private class LimitedInputStream(input: InputStream, private val limit: Long) : FilterInputStream(input) {
        private var total = 0L
        private fun account(count: Int): Int {
            if (count > 0) {
                total += count
                if (total > limit) throw IOException("XMLTV expanded data exceeds safety limit")
            }
            return count
        }
        override fun read(): Int = super.read().also { if (it >= 0) account(1) }
        override fun read(buffer: ByteArray, offset: Int, length: Int): Int = super.read(buffer, offset, length).let(::account)
        override fun skip(count: Long): Long {
            val skipped = super.skip(count)
            if (skipped > 0L) {
                total += skipped
                if (total > limit) throw IOException("XMLTV expanded data exceeds safety limit")
            }
            return skipped
        }
        override fun markSupported(): Boolean = false
        override fun mark(readLimit: Int) = Unit
        override fun reset() { throw IOException("XMLTV stream does not support reset") }
    }

    private fun parseFile(file: File): Snapshot {
        if (!file.isFile || file.length() !in 1..MAX_RAW_BYTES) throw IOException("invalid XMLTV file size")
        val buffered = BufferedInputStream(FileInputStream(file))
        buffered.mark(2)
        val first = buffered.read()
        val second = buffered.read()
        buffered.reset()
        val decoded = if (first == 0x1f && second == 0x8b) GZIPInputStream(buffered) else buffered
        val limited = LimitedInputStream(decoded, MAX_EXPANDED_BYTES)
        limited.use { input ->
            val parser = Xml.newPullParser()
            parser.setInput(input, null)
            val progs = HashMap<String, ArrayList<Prog>>()
            val names = HashMap<String, String>()
            val now = System.currentTimeMillis()
            val fmtZ = SimpleDateFormat("yyyyMMddHHmmss Z", Locale.US).apply { isLenient = false }
            val fmtU = SimpleDateFormat("yyyyMMddHHmmss", Locale.US).apply {
                isLenient = false
                timeZone = TimeZone.getTimeZone("UTC")
            }
            fun ts(raw: String?): Long {
                if (raw.isNullOrBlank()) return 0L
                val value = raw.trim()
                return try {
                    val formatter = if (value.contains(' ')) fmtZ else fmtU
                    val timestampText = if (value.contains(' ')) value else value.take(14)
                    val position = ParsePosition(0)
                    val parsed = formatter.parse(timestampText, position)
                    if (parsed != null && position.index == timestampText.length && position.errorIndex < 0) parsed.time else 0L
                } catch (_: Exception) { 0L }
            }

            var event = parser.eventType
            var rootSeen = false
            var rootClosed = false
            var curChannel = ""
            var inDisplay = false
            var inTitle = false
            val displayBuilder = StringBuilder()
            val titleBuilder = StringBuilder()
            var pStart = 0L
            var pStop = 0L
            var pChan = ""
            var title = ""
            var seenProgrammeElements = 0
            var count = 0
            while (event != org.xmlpull.v1.XmlPullParser.END_DOCUMENT) {
                when (event) {
                    org.xmlpull.v1.XmlPullParser.START_TAG -> {
                        if (!rootSeen) {
                            if (parser.name != "tv") throw IOException("not an XMLTV document")
                            rootSeen = true
                        }
                        when (parser.name) {
                            "channel" -> curChannel = parser.getAttributeValue(null, "id") ?: ""
                            "display-name" -> {
                                inDisplay = true
                                displayBuilder.setLength(0)
                            }
                            "programme" -> {
                                seenProgrammeElements++
                                if (seenProgrammeElements > MAX_PROGRAMMES * 2) {
                                    throw IOException("XMLTV contains too many programme elements")
                                }
                                pStart = ts(parser.getAttributeValue(null, "start"))
                                pStop = ts(parser.getAttributeValue(null, "stop"))
                                pChan = parser.getAttributeValue(null, "channel") ?: ""
                                title = ""
                                titleBuilder.setLength(0)
                            }
                            "title" -> {
                                inTitle = true
                                titleBuilder.setLength(0)
                            }
                        }
                    }
                    org.xmlpull.v1.XmlPullParser.TEXT,
                    org.xmlpull.v1.XmlPullParser.CDSECT,
                    org.xmlpull.v1.XmlPullParser.ENTITY_REF -> {
                        val chunk = parser.text.orEmpty()
                        if (inDisplay && curChannel.isNotEmpty() && displayBuilder.length < 220) {
                            displayBuilder.append(chunk.take(220 - displayBuilder.length))
                        }
                        if (inTitle && titleBuilder.length < 400) {
                            titleBuilder.append(chunk.take(400 - titleBuilder.length))
                        }
                    }
                    org.xmlpull.v1.XmlPullParser.END_TAG -> when (parser.name) {
                        "display-name" -> {
                            val key = norm(displayBuilder.toString())
                            if (key.isNotBlank() && curChannel.isNotBlank()) names[key] = curChannel
                            inDisplay = false
                        }
                        "title" -> {
                            if (title.isBlank()) title = titleBuilder.toString().trim().take(400)
                            inTitle = false
                        }
                        "channel" -> curChannel = ""
                        "programme" -> {
                            if (pChan.isNotBlank() && title.isNotBlank() && pStop > now && pStart < now + WINDOW_MS && pStop > pStart) {
                                if (count >= MAX_PROGRAMMES) throw IOException("XMLTV exceeds the supported programme limit")
                                progs.getOrPut(pChan) { ArrayList() }.add(Prog(pStart, pStop, title))
                                count++
                            }
                        }
                        "tv" -> if (parser.name.equals("tv", true)) rootClosed = true
                    }
                }
                event = parser.next()
            }
            if (!rootSeen || !rootClosed) throw IOException("incomplete XMLTV document")
            for (programmes in progs.values) programmes.sortBy { it.start }
            return Snapshot(progs, names)
        }
    }

    /** (الآن، بعده) لقناة بمعرّف tvg-id أو باسمها. */
    fun nowNext(tvgId: String, name: String): Pair<String?, String?> {
        val map = byId
        if (map.isEmpty()) return Pair(null, null)
        val id = if (tvgId.isNotEmpty() && map.containsKey(tvgId)) tvgId else nameToId[norm(name)] ?: return Pair(null, null)
        val list = map[id] ?: return Pair(null, null)
        val now = System.currentTimeMillis()
        val cur = list.firstOrNull { it.start <= now && now < it.stop }
        val next = list.firstOrNull { it.start >= (cur?.stop ?: now) && it.start > now }
        return Pair(cur?.title, next?.title)
    }
}
