package com.uobidah.launcher

import android.content.Context
import org.json.JSONObject

/**
 * كتالوج الخلفيات المضمَّن (assets/launcher_wallpapers.json).
 * - يُحمَّل ويُتحقق منه محليًا؛ أي رابط غير https أو من مضيف غير مسموح أو placeholder يُتجاهل.
 * - logo_url لا يُستخدم: روابط الشعارات في الملف تعطي 404 (تخطيط الريبو مختلف)، والتطبيق له شعاراته المدمجة.
 */
object WallpaperCatalog {
    data class Category(val id: String, val title: String, val apiSource: String?, val fallbacks: List<String>)

    private val apiHosts = setOf("wallhaven.cc")
    private val imageHosts = setOf("w.wallhaven.cc", "upload.wikimedia.org", "thumb.wikimedia.org")
    private val placeholderMarkers = listOf("your-", "example.", "placeholder")

    @Volatile private var cached: List<Category>? = null

    private fun hostOf(url: String): String? = try {
        val u = java.net.URI(url)
        if (u.scheme.equals("https", true)) u.host?.lowercase() else null
    } catch (_: Exception) { null }

    private fun isPlaceholder(url: String) = placeholderMarkers.any { url.lowercase().contains(it) }

    internal fun safeApi(url: String): Boolean {
        val h = hostOf(url) ?: return false
        return !isPlaceholder(url) && h in apiHosts && url.substringAfter(h).startsWith("/api/v1/")
    }

    internal fun safeImage(url: String): Boolean {
        val h = hostOf(url) ?: return false
        val path = url.substringBefore('?').lowercase()
        return !isPlaceholder(url) && h in imageHosts && (path.endsWith(".jpg") || path.endsWith(".jpeg") || path.endsWith(".png"))
    }

    internal fun parse(json: String): List<Category> {
        val arr = JSONObject(json).optJSONArray("categories") ?: return emptyList()
        val out = ArrayList<Category>()
        val seen = HashSet<String>()
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val id = o.optString("id").trim()
            if (id.isEmpty() || !seen.add(id)) continue
            val api = o.optString("api_source").takeIf { it.isNotBlank() && safeApi(it) }
            val fb = LinkedHashSet<String>()
            val fa = o.optJSONArray("fallback_wallpapers")
            if (fa != null) for (k in 0 until fa.length()) { val u = fa.optString(k); if (safeImage(u)) fb.add(u) }
            out.add(Category(id, o.optString("title", id), api, fb.toList()))
        }
        return out
    }

    /** نتيجة Wallhaven: data[].path فقط من w.wallhaven.cc، بلا تكرار، حتى 24. */
    internal fun parseWallhavenResponse(json: String): List<String> {
        val data = JSONObject(json).optJSONArray("data") ?: return emptyList()
        val urls = LinkedHashSet<String>()
        for (i in 0 until data.length()) {
            if (urls.size >= 24) break
            val p = data.optJSONObject(i)?.optString("path").orEmpty()
            if (safeImage(p)) urls.add(p)
        }
        return urls.toList()
    }

    fun load(ctx: Context): List<Category> {
        cached?.let { return it }
        val list = try {
            ctx.assets.open("launcher_wallpapers.json").use { parse(String(it.readBytes(), Charsets.UTF_8)) }
        } catch (_: Exception) { emptyList() }
        cached = list
        return list
    }

    /** نطاق المكتبة (Wallpapers.scope) ← معرّف الفئة في الملف. */
    fun idsForScope(scope: String): List<String> = when (scope) {
        "club_ahly" -> listOf("al_ahly")
        "club_madrid" -> listOf("real_madrid")
        "club_united" -> listOf("man_united")
        else -> listOf("islamic", "nature")
    }

    /** روابط مرشَّحة للنطاق: Wallhaven API أولًا ثم القائمة الاحتياطية. تُرجع فارغة عند أي فشل. */
    fun candidatesForScope(ctx: Context, scope: String, fetch: (String) -> String = { u ->
        String(Net.getBytes(u, 8000, maxBytes = 2 * 1024 * 1024), Charsets.UTF_8)
    }): List<String> {
        val cats = load(ctx)
        val out = ArrayList<String>()
        for (id in idsForScope(scope).shuffled()) {
            val c = cats.firstOrNull { it.id == id } ?: continue
            val api = c.apiSource
            if (api != null) {
                try { out.addAll(parseWallhavenResponse(fetch(api))) } catch (_: Exception) { }
            }
            if (out.isEmpty()) out.addAll(c.fallbacks)
            if (out.isNotEmpty()) break
        }
        return out
    }
}
