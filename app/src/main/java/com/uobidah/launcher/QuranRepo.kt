package com.uobidah.launcher

import android.content.Context
import android.net.Uri
import com.uobidah.launcher.core.LastListen
import com.uobidah.launcher.core.Moshaf
import com.uobidah.launcher.core.Reciter
import com.uobidah.launcher.core.ReciterParser
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors

/** قراء mp3quran.net من الواجهة الرسمية (v3)، مع كاش محلي. لا بيانات مدمجة. */
object QuranRepo {
    @Volatile var reciters: List<Reciter> = emptyList()
    @Volatile var suwar: Map<Int, String> = emptyMap()

    private const val RECITERS_URL = "https://www.mp3quran.net/api/v3/reciters?language=ar"
    private const val SUWAR_URL = "https://www.mp3quran.net/api/v3/suwar?language=ar"
    private const val STALE_MS = 24L * 3600_000L

    private fun dir(ctx: Context) = File(ctx.filesDir, "quran").also { it.mkdirs() }

    fun surahName(n: Int): String = suwar[n] ?: "سورة $n"
    fun byId(id: Int): Reciter? = reciters.firstOrNull { it.id == id }

    /**
     * onState(ok, message): يُستدعى على خيط الواجهة. أول استدعاء من الكاش إن وُجد، ثم بعد التحديث من الشبكة.
     */
    fun load(ctx: Context, force: Boolean, onState: (Boolean, String) -> Unit) {
        val app = ctx.applicationContext
        Net.bg {
            val rf = File(dir(app), "reciters_ar.json")
            val sf = File(dir(app), "suwar_ar.json")
            var haveCache = false
            try {
                if (rf.exists()) {
                    val l = ReciterParser.parseReciters(rf.readText(Charsets.UTF_8))
                    if (l.isNotEmpty()) { reciters = l; haveCache = true }
                }
                if (sf.exists()) {
                    val m = ReciterParser.parseSuwar(sf.readText(Charsets.UTF_8))
                    if (m.isNotEmpty()) suwar = m
                }
            } catch (_: Exception) { }
            if (haveCache) Net.ui { onState(true, "cache") }
            val stale = !rf.exists() || force || System.currentTimeMillis() - rf.lastModified() > STALE_MS || suwar.isEmpty()
            if (!stale) return@bg
            try {
                val rj = Net.getString(RECITERS_URL, 45000)
                val list = ReciterParser.parseReciters(rj)
                if (list.isEmpty()) throw IllegalStateException("قائمة القراء فارغة من المصدر")
                val sj = Net.getString(SUWAR_URL, 30000)
                val sm = ReciterParser.parseSuwar(sj)
                rf.writeText(rj, Charsets.UTF_8)
                if (sm.isNotEmpty()) sf.writeText(sj, Charsets.UTF_8)
                reciters = list
                if (sm.isNotEmpty()) suwar = sm
                Net.ui { onState(true, "network") }
            } catch (e: Exception) {
                val msg = "تعذر تحديث القراء من mp3quran.net: " + (e.message ?: e.javaClass.simpleName)
                Net.ui { onState(haveCache, msg) }
            }
        }
    }
}

/** تفضيلات القراء: المفضلة، حالة البحث والفلاتر وطريقة العرض، وآخر موضع في القائمة. */
class QuranPrefs(private val prefs: Prefs) {
    private val sp get() = prefs.sp

    fun favReciters(): Set<Int> =
        sp.getString("quran_fav_reciters", "")!!.split(',').mapNotNull { it.toIntOrNull() }.toSet()

    fun toggleFavReciter(id: Int): Boolean {
        val s = favReciters().toMutableSet()
        val now = if (s.contains(id)) { s.remove(id); false } else { s.add(id); true }
        sp.edit().putString("quran_fav_reciters", s.joinToString(",")).apply()
        return now
    }

    private fun key(r: Int, m: Int, s: Int) = "$r:$m:$s"
    fun favSurahs(): Set<String> =
        sp.getString("quran_fav_surahs", "")!!.split(',').filter { it.isNotBlank() }.toSet()

    fun isFavSurah(r: Int, m: Int, s: Int) = favSurahs().contains(key(r, m, s))
    fun toggleFavSurah(r: Int, m: Int, s: Int): Boolean {
        val set = favSurahs().toMutableSet()
        val k = key(r, m, s)
        val now = if (set.contains(k)) { set.remove(k); false } else { set.add(k); true }
        sp.edit().putString("quran_fav_surahs", set.joinToString(",")).apply()
        return now
    }

    var query: String
        get() = sp.getString("quran_q", "") ?: ""
        set(v) { sp.edit().putString("quran_q", v).apply() }
    var riwaya: String
        get() = sp.getString("quran_riwaya", "") ?: ""
        set(v) { sp.edit().putString("quran_riwaya", v).apply() }
    var favOnly: Boolean
        get() = sp.getBoolean("quran_fav_only", false)
        set(v) { sp.edit().putBoolean("quran_fav_only", v).apply() }
    var grid: Boolean
        get() = sp.getBoolean("quran_grid", true)
        set(v) { sp.edit().putBoolean("quran_grid", v).apply() }
    var listPos: Int
        get() = sp.getInt("quran_list_pos", 0)
        set(v) { sp.edit().putInt("quran_list_pos", v).apply() }
    var listFocus: Int
        get() = sp.getInt("quran_list_focus", -1)
        set(v) { sp.edit().putInt("quran_list_focus", v).apply() }
    // ---------- قوائم التشغيل المسماة: الاسم -> عناصر "قارئ:مصحف:سورة" ----------
    @Suppress("UNCHECKED_CAST")
    private fun readLists(): LinkedHashMap<String, MutableList<String>> {
        val out = LinkedHashMap<String, MutableList<String>>()
        try {
            val raw = sp.getString("quran_playlists", "") ?: ""
            if (raw.isBlank()) return out
            for ((k, v) in com.uobidah.launcher.core.MiniJson.parseObject(raw)) {
                out[k] = ((v as? List<Any?>) ?: emptyList()).mapNotNull { it as? String }.toMutableList()
            }
        } catch (_: Exception) { }
        return out
    }

    private fun writeLists(m: Map<String, List<String>>) {
        sp.edit().putString("quran_playlists", com.uobidah.launcher.core.MiniJson.stringify(m)).apply()
    }

    fun playlistNames(): List<String> = readLists().keys.toList()
    fun playlistItems(name: String): List<String> = readLists()[name] ?: emptyList()
    fun createPlaylist(name: String) {
        val n = name.trim().take(40)
        if (n.isEmpty()) return
        val m = readLists(); if (!m.containsKey(n)) { m[n] = ArrayList(); writeLists(m) }
    }
    fun deletePlaylist(name: String) { val m = readLists(); m.remove(name); writeLists(m) }
    fun addToPlaylist(name: String, r: Int, mo: Int, s: Int): Boolean {
        val m = readLists(); val l = m.getOrPut(name) { ArrayList() }
        val k = "$r:$mo:$s"
        if (l.contains(k)) return false
        l.add(k); writeLists(m); return true
    }
    fun removeFromPlaylist(name: String, key: String) {
        val m = readLists(); m[name]?.remove(key); writeLists(m)
    }

    fun lastListen(): LastListen? = LastListen.decode(sp.getString(QuranAudioService.KEY_LAST, "") ?: "")
    fun lastMoshaf(reciterId: Int): Int = sp.getInt("quran_moshaf_$reciterId", 0)
    fun setLastMoshaf(reciterId: Int, moshafId: Int) { sp.edit().putInt("quran_moshaf_$reciterId", moshafId).apply() }
}

object QuranPlayback {
    /** يبدأ قائمة تشغيل من السورة المطلوبة حتى آخر سورة متاحة للمصحف، ويفضّل الملفات المنزّلة. */
    fun start(ctx: Context, r: Reciter, m: Moshaf, startSurah: Int, posMs: Long = 0L) {
        val list = m.surahs
        val idx = list.indexOf(startSurah).coerceAtLeast(0)
        val uris = list.map { s ->
            if (QuranDownloads.has(ctx, r.id, m.id, s)) Uri.fromFile(QuranDownloads.file(ctx, r.id, m.id, s)).toString()
            else m.urlFor(s)
        }
        QuranAudioService.playQueue(
            ctx, r.id, m.id, r.name, list.toIntArray(),
            list.map { QuranRepo.surahName(it) }.toTypedArray(), uris.toTypedArray(), idx, posMs
        )
    }
}

/** عنصر في قائمة تشغيل: قارئ + مصحف + سورة. */
class PlaylistEntry(val reciter: Reciter, val moshaf: Moshaf, val surah: Int, val key: String)

object QuranPlaylists {
    /** يحوّل مفاتيح القائمة إلى عناصر فعلية؛ يتجاهل ما لم يعد موجودًا في بيانات المصدر. */
    fun resolve(keys: List<String>): List<PlaylistEntry> = keys.mapNotNull { k ->
        val p = k.split(':')
        if (p.size != 3) return@mapNotNull null
        val r = QuranRepo.byId(p[0].toIntOrNull() ?: return@mapNotNull null) ?: return@mapNotNull null
        val m = r.moshafs.firstOrNull { it.id == p[1].toIntOrNull() } ?: return@mapNotNull null
        val s = p[2].toIntOrNull() ?: return@mapNotNull null
        if (!m.has(s)) null else PlaylistEntry(r, m, s, k)
    }

    fun play(ctx: Context, items: List<PlaylistEntry>, startIndex: Int) {
        if (items.isEmpty()) return
        val uris = items.map {
            if (QuranDownloads.has(ctx, it.reciter.id, it.moshaf.id, it.surah))
                Uri.fromFile(QuranDownloads.file(ctx, it.reciter.id, it.moshaf.id, it.surah)).toString()
            else it.moshaf.urlFor(it.surah)
        }
        QuranAudioService.playQueue(
            ctx, items[0].reciter.id, items[0].moshaf.id, items[0].reciter.name,
            items.map { it.surah }.toIntArray(), items.map { QuranRepo.surahName(it.surah) }.toTypedArray(),
            uris.toTypedArray(), startIndex.coerceIn(0, items.size - 1), 0L,
            items.map { it.reciter.id }.toIntArray(), items.map { it.moshaf.id }.toIntArray(),
            items.map { it.reciter.name }.toTypedArray()
        )
    }
}

/** تنزيل سور للاستماع دون إنترنت (للاستخدام الشخصي وفق شروط المصدر). البث متدفق دون تحميل الملف كله في الذاكرة. */
object QuranDownloader {
    private val pool = Executors.newFixedThreadPool(2)
    private val running = java.util.concurrent.ConcurrentHashMap<String, Int>()

    private fun k(r: Int, m: Int, s: Int) = "$r:$m:$s"
    fun isRunning(r: Int, m: Int, s: Int) = running.containsKey(k(r, m, s))
    fun progress(r: Int, m: Int, s: Int): Int = running[k(r, m, s)] ?: -1

    fun start(ctx: Context, r: Reciter, m: Moshaf, surah: Int, done: (Boolean, String) -> Unit) {
        val key = k(r.id, m.id, surah)
        if (running.putIfAbsent(key, 0) != null) return
        val app = ctx.applicationContext
        pool.execute {
            var ok = false
            var msg = ""
            val target = QuranDownloads.file(app, r.id, m.id, surah)
            val part = File(target.parentFile, target.name + ".part")
            try {
                val conn = URL(m.urlFor(surah)).openConnection() as HttpURLConnection
                conn.connectTimeout = 20000
                conn.readTimeout = 30000
                conn.instanceFollowRedirects = true
                if (conn.responseCode !in 200..299) throw IllegalStateException("HTTP " + conn.responseCode)
                val total = conn.contentLengthLong
                conn.inputStream.use { ins ->
                    FileOutputStream(part).use { out ->
                        val buf = ByteArray(64 * 1024)
                        var got = 0L
                        while (true) {
                            val n = ins.read(buf)
                            if (n < 0) break
                            out.write(buf, 0, n)
                            got += n
                            if (total > 0) running[key] = (got * 100 / total).toInt().coerceIn(0, 99)
                        }
                    }
                }
                if (part.length() < 1024) throw IllegalStateException("ملف غير صالح")
                if (target.exists()) target.delete()
                ok = part.renameTo(target)
                if (!ok) msg = "تعذر حفظ الملف"
            } catch (e: Exception) {
                part.delete()
                msg = e.message ?: e.javaClass.simpleName
            }
            running.remove(key)
            Net.ui { done(ok, msg) }
        }
    }

    fun delete(ctx: Context, r: Int, m: Int, s: Int) { QuranDownloads.file(ctx, r, m, s).delete() }
}
