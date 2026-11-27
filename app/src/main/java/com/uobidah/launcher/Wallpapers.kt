package com.uobidah.launcher

import android.content.Context
import android.graphics.Bitmap
import java.io.File
import java.io.FileOutputStream
import java.net.URLEncoder
import java.security.MessageDigest
import java.time.LocalDate
import org.json.JSONObject

/** صور الخلفية محفوظة في مكتبة مستقلة لكل نادٍ، مع ملف نشط واحد قابل للتبديل. */
object Wallpapers {
    val categories: List<Pair<String, String>> = listOf(
        Pair("🌿 طبيعة", "nature landscape wallpaper 4k"),
        Pair("🕌 مساجد", "beautiful mosque islamic architecture wallpaper"),
        Pair("☁️ سماء", "sky clouds sunset wallpaper"),
        Pair("🌌 ليل", "night sky stars wallpaper"),
        Pair("🦅 الأهلي • الملعب", "Al Ahly SC stadium red crowd wallpaper 4k"),
        Pair("🦅 الأهلي • احتفالات", "Al Ahly SC champions celebration wallpaper 4k"),
        Pair("🦅 الأهلي • أحمر", "Al Ahly red black football wallpaper 4k"),
        Pair("😈 يونايتد • أولد ترافورد", "Manchester United Old Trafford stadium wallpaper 4k"),
        Pair("😈 يونايتد • الشعار", "Manchester United red black gold wallpaper 4k"),
        Pair("😈 يونايتد • الجمهور", "Manchester United fans stadium wallpaper 4k"),
        Pair("👑 ريال • البرنابيو", "Real Madrid Santiago Bernabeu stadium wallpaper 4k"),
        Pair("👑 ريال • الملكي", "Real Madrid white gold football wallpaper 4k"),
        Pair("👑 ريال • الأبطال", "Real Madrid champions celebration wallpaper 4k")
    )

    private val idRegex = Regex("""i\.pinimg\.com/(?:\d+x|originals)/([0-9a-f]{2}/[0-9a-f]{2}/[0-9a-f]{2}/[0-9a-f]{32}\.(?:jpg|jpeg|png))""")

    /** نطاق المكتبة: لا تتشارك الأندية الخلفيات ولا يؤثر تغيير أحدها على الآخر. */
    fun scope(prefs: Prefs): String = when (Themes.get(prefs.themeIndex).club?.art) {
        1 -> "club_ahly"
        2 -> "club_united"
        3 -> "club_madrid"
        else -> "general"
    }

    private fun scopeDir(ctx: Context, scope: String): File = File(ctx.filesDir, "wallpaper_library/$scope")

    /** الملف النشط للثيم الحالي، مع ترحيل خلفية النسخ السابقة للنطاق العام فقط. */
    fun activeFile(ctx: Context, prefs: Prefs): File {
        val key = scope(prefs)
        val dir = scopeDir(ctx, key)
        val name = prefs.activeWallpaperName(key)
        if (name.isNotBlank() && File(name).name == name) {
            val selected = File(dir, name)
            if (selected.isFile && selected.length() > 0L) return selected
        }
        val legacy = bgFile(ctx)
        if (key == "general" && legacy.isFile && legacy.length() > 0L) return legacy
        return File(dir, "active.jpg") // غير موجود في البداية؛ لا يسبب خطأ في القراءة.
    }

    /** المسار القديم يُحتفظ به للترحيل من 5.x. */
    fun bgFile(ctx: Context): File = File(ctx.filesDir, "launcher_bg.jpg")

    fun gallery(ctx: Context, prefs: Prefs): List<File> {
        val dir = scopeDir(ctx, scope(prefs))
        return dir.listFiles()?.filter { it.isFile && it.extension.lowercase() in setOf("jpg", "jpeg", "png", "webp") && it.length() > 0L }
            ?.sortedByDescending { it.lastModified() } ?: emptyList()
    }

    fun activate(ctx: Context, prefs: Prefs, file: File): Boolean {
        return try {
            val dir = scopeDir(ctx, scope(prefs)).canonicalFile
            val target = file.canonicalFile
            if (target.parentFile != dir || !target.isFile || target.length() <= 0L) return false
            prefs.setActiveWallpaperName(scope(prefs), target.name)
            prefs.bgVersion = System.currentTimeMillis()
            true
        } catch (_: Exception) { false }
    }

    /** اعتماد الصورة: رابط الصورة ← «المصوّر • Pexels» (يُعرض بعد التعيين؛ شرط استخدام Pexels). */
    private val credits = java.util.concurrent.ConcurrentHashMap<String, String>()
    fun creditFor(url: String): String? = credits[url]

    /** يحلل رد Pexels: https من images.pexels.com فقط، بلا تكرار، ويسجّل المصوّر. */
    internal fun parsePexelsImageUrls(json: String): List<String> {
        val photos = JSONObject(json).optJSONArray("photos") ?: return emptyList()
        val urls = LinkedHashSet<String>()
        for (i in 0 until photos.length()) {
            if (urls.size >= 24) break
            val p = photos.optJSONObject(i) ?: continue
            val url = p.optJSONObject("src")?.optString("large2x", "").orEmpty()
            if (!url.startsWith("https://images.pexels.com/")) continue
            if (urls.add(url)) {
                val who = p.optString("photographer", "").trim()
                credits[url] = if (who.isNotEmpty()) "$who • Pexels" else "Pexels"
            }
        }
        return urls.toList()
    }

    /** بحث Pexels بالمفتاح؛ يرجع قائمة فارغة عند غياب المفتاح أو أي فشل (فيستمر البحث في المصادر الأخرى). */
    fun searchPexels(apiKey: String, query: String): List<String> {
        if (apiKey.isBlank()) return emptyList()
        return try {
            val url = "https://api.pexels.com/v1/search?query=" + URLEncoder.encode(query, "UTF-8") +
                "&orientation=landscape&size=large&per_page=24"
            val body = String(Net.getBytes(url, 8000, maxBytes = 3 * 1024 * 1024, headers = mapOf("Authorization" to apiKey.trim())), Charsets.UTF_8)
            parsePexelsImageUrls(body)
        } catch (_: Exception) { emptyList() }
    }

    /** يبحث في Pexels إن وُجد مفتاح، ثم Wikimedia Commons (لا يحتاج جلسة Pinterest)، ثم Pinterest كاحتياط. */
    fun search(query: String, pexelsKey: String = ""): List<String> {
        val clean = query.trim().take(120)
        if (clean.isEmpty()) return emptyList()
        val pexels = searchPexels(pexelsKey, clean)
        if (pexels.isNotEmpty()) return pexels
        val q = URLEncoder.encode(clean, "UTF-8")
        val commonsUrl = "https://commons.wikimedia.org/w/api.php?action=query&generator=search&gsrsearch=" +
            URLEncoder.encode("filetype:bitmap $clean", "UTF-8") +
            "&gsrnamespace=6&gsrlimit=30&prop=imageinfo&iiprop=url&iiurlwidth=1600&format=json"
        val commons = try {
            val body = String(Net.getBytes(commonsUrl, 8000, maxBytes = 3 * 1024 * 1024), Charsets.UTF_8)
            parseCommonsImageUrls(body)
        } catch (_: Exception) { emptyList() }
        if (commons.isNotEmpty()) return commons.take(24)

        val seen = LinkedHashSet<String>()
        try {
            val html = String(Net.getBytes("https://www.pinterest.com/search/pins/?q=$q&rs=typed", 6000, maxBytes = 2 * 1024 * 1024), Charsets.UTF_8)
                .replace("\\u002F", "/").replace("\\/", "/")
            for (m in idRegex.findAll(html)) {
                seen.add(m.groupValues[1])
                if (seen.size >= 24) break
            }
        } catch (_: Exception) { }
        return seen.toList()
    }

    internal fun parseCommonsImageUrls(json: String): List<String> {
        val pages = JSONObject(json).optJSONObject("query")?.optJSONObject("pages") ?: return emptyList()
        val urls = LinkedHashSet<String>()
        val keys = pages.keys()
        while (keys.hasNext() && urls.size < 24) {
            val page = pages.optJSONObject(keys.next()) ?: continue
            val info = page.optJSONArray("imageinfo")?.optJSONObject(0) ?: continue
            val url = info.optString("thumburl", info.optString("url", ""))
            val cleanUrl = url.substringBefore('?').lowercase()
            val trustedHost = url.startsWith("https://upload.wikimedia.org/", true) ||
                url.startsWith("https://thumb.wikimedia.org/", true) ||
                url.startsWith("https://commons.wikimedia.org/", true)
            if ((cleanUrl.endsWith(".jpg") || cleanUrl.endsWith(".jpeg") || cleanUrl.endsWith(".png") || cleanUrl.endsWith(".webp")) && trustedHost) urls.add(url)
        }
        return urls.toList()
    }

    fun thumb(path: String): String = if (path.startsWith("https://") || path.startsWith("http://")) path else "https://i.pinimg.com/474x/$path"
    fun candidates(path: String): List<String> = if (path.startsWith("https://") || path.startsWith("http://")) listOf(path) else listOf(
        "https://i.pinimg.com/originals/$path",
        "https://i.pinimg.com/736x/$path"
    )

    /** يحوّل رابط صورة أو رابط Pin إلى روابط صور محتملة. */
    fun resolveInput(input: String): List<String> {
        val u = input.trim()
        if (!(u.startsWith("https://", true) || u.startsWith("http://", true))) return emptyList()
        val lower = u.lowercase()
        val isImage = lower.contains("pinimg.com") || lower.substringBefore("?").let {
            it.endsWith(".jpg") || it.endsWith(".jpeg") || it.endsWith(".png") || it.endsWith(".webp")
        }
        if (isImage) return listOf(u)
        if (lower.contains("pinterest.") || lower.contains("pin.it")) {
            return try {
                val html = String(Net.getBytes(u, 20000, maxBytes = 4 * 1024 * 1024), Charsets.UTF_8)
                val og = Regex("""property="og:image"[^>]*content="([^"]+)"""").find(html)
                    ?: Regex("""content="([^"]+)"[^>]*property="og:image"""").find(html)
                if (og != null) {
                    val img = og.groupValues[1].replace("&amp;", "&")
                    val m = idRegex.find(img)
                    if (m != null) candidates(m.groupValues[1]) else listOf(img)
                } else emptyList()
            } catch (_: Exception) { emptyList() }
        }
        return listOf(u)
    }

    /** يحفظ الصورة الجديدة كخيار مستقل ثم يجعلها الخلفية النشطة للنطاق الحالي. */
    fun saveFromUrls(ctx: Context, prefs: Prefs, urls: List<String>): Boolean {
        val key = scope(prefs)
        val dir = scopeDir(ctx, key)
        if (!dir.exists() && !dir.mkdirs()) return false
        for (u in urls) {
            try {
                val bytes = Net.getBytes(u, 25000, maxBytes = 20 * 1024 * 1024)
                if (bytes.isEmpty()) continue
                val bmp = Img.decode(bytes, 1920, 1080) ?: continue
                val digest = MessageDigest.getInstance("SHA-256").digest(bytes)
                    .take(5).joinToString("") { "%02x".format(it.toInt() and 0xff) }
                val name = "wall_${System.currentTimeMillis()}_$digest.jpg"
                val target = File(dir, name)
                val tmp = File(dir, name + ".tmp")
                val written = try {
                    FileOutputStream(tmp).use { out -> bmp.compress(Bitmap.CompressFormat.JPEG, 91, out) }
                } finally {
                    if (!bmp.isRecycled) bmp.recycle()
                }
                if (!written || !tmp.renameTo(target)) {
                    tmp.delete()
                    continue
                }
                prefs.setActiveWallpaperName(key, name)
                prefs.bgVersion = System.currentTimeMillis()
                trimGallery(dir, name, 18)
                return true
            } catch (_: Exception) { }
        }
        return false
    }

    private fun trimGallery(dir: File, activeName: String, keep: Int) {
        try {
            val files = dir.listFiles()?.filter { it.isFile && it.extension == "jpg" }?.sortedByDescending { it.lastModified() } ?: return
            var count = 0
            for (file in files) {
                if (file.name == activeName) continue
                count++
                if (count >= keep) file.delete()
            }
        } catch (_: Exception) { }
    }

    /** إزالة الخلفية من الشاشة دون حذف الصور المحفوظة في مكتبة النادي. */
    fun clear(ctx: Context, prefs: Prefs) {
        val key = scope(prefs)
        prefs.setActiveWallpaperName(key, "")
        if (key == "general") bgFile(ctx).delete()
        prefs.bgVersion = System.currentTimeMillis()
    }

    /** تحديث يومي مستقل لكل نادي، مع حد زمني للمحاولات الفاشلة كي لا نكرر طلبات الشبكة. */
    fun dailyUpdate(ctx: Context, prefs: Prefs) {
        val key = scope(prefs)
        val today = LocalDate.now().toString()
        if (prefs.lastWallpaperDay(key) == today) return
        val now = System.currentTimeMillis()
        if (now - prefs.lastWallpaperAttempt(key) < 4L * 60L * 60L * 1000L) return
        prefs.setLastWallpaperAttempt(key, now)
        val storedQuery = prefs.wallpaperQuery(key)
        val q = if (storedQuery.isNotEmpty()) storedQuery else when (key) {
            "club_ahly" -> "Al Ahly SC stadium red celebration wallpaper 4k"
            "club_united" -> "Manchester United Old Trafford night stadium wallpaper 4k"
            "club_madrid" -> "Real Madrid Bernabeu white gold wallpaper 4k"
            else -> categories.take(4).random().second
        }
        // لا بحث نصي مخصص من المستخدم ← كتالوج launcher_wallpapers.json أولًا (Wallhaven ثم الاحتياطي)، ثم المصادر الأخرى.
        val fromCatalog = if (storedQuery.isEmpty()) try { WallpaperCatalog.candidatesForScope(ctx, key) } catch (_: Exception) { emptyList() } else emptyList()
        val res = if (fromCatalog.isNotEmpty()) fromCatalog else try { search(q, prefs.pexelsApiKey) } catch (_: Exception) { emptyList() }
        if (res.isEmpty()) return
        val pick = res.random()
        if (saveFromUrls(ctx, prefs, candidates(pick))) {
            prefs.setLastWallpaperDay(key, today)
        }
    }
}
