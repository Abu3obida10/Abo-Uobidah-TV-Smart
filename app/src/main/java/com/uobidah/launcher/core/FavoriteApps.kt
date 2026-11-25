package com.uobidah.launcher.core

/** factor يُضرب في المقاس العام للبطاقة والأيقونة (متوسط = المقاس العام كما هو). */
enum class TileSize(val key: String, val labelAr: String, val factor: Float) {
    SMALL("s", "صغير", 0.75f),
    MEDIUM("m", "متوسط", 1f),
    LARGE("l", "كبير", 1.3f);

    companion object { fun fromKey(k: String?) = values().firstOrNull { it.key == k } ?: MEDIUM }
}

/**
 * اختصار مفضّل. label فارغ = اسم التطبيق الأصلي. iconKey فارغ = أيقونة التطبيق الأصلية،
 * وإلا فهو معرّف أيقونة بديلة يحدده الواجهة (مثلاً emoji أو اسم ملف داخلي).
 * hidden = مخفي من الشاشة الرئيسية لكنه يبقى في القائمة والتطبيق نفسه مثبّت.
 */
data class FavoriteEntry(
    val pkg: String,
    val label: String = "",
    val iconKey: String = "",
    val size: TileSize = TileSize.MEDIUM,
    val showName: Boolean = true,
    val hidden: Boolean = false
) {
    fun displayName(appName: String): String = label.ifBlank { appName }
}

/** قائمة غير قابلة للتعديل؛ كل عملية ترجع قائمة جديدة. */
class FavoriteList(val entries: List<FavoriteEntry> = emptyList()) {
    val size get() = entries.size
    fun visible(): List<FavoriteEntry> = entries.filter { !it.hidden }
    fun indexOf(pkg: String) = entries.indexOfFirst { it.pkg == pkg }
    operator fun contains(pkg: String) = indexOf(pkg) >= 0
    fun get(pkg: String): FavoriteEntry? = entries.firstOrNull { it.pkg == pkg }

    fun add(pkg: String): FavoriteList =
        if (pkg.isBlank() || pkg in this) this else FavoriteList(entries + FavoriteEntry(pkg))

    fun remove(pkg: String) = FavoriteList(entries.filterNot { it.pkg == pkg })

    /** delta سالب = للأعلى. يرجع نفس القائمة إن كان الحد قد وُصل. */
    fun move(pkg: String, delta: Int): FavoriteList {
        val from = indexOf(pkg); val to = from + delta
        if (from < 0 || to !in entries.indices || delta == 0) return this
        val l = entries.toMutableList(); val e = l.removeAt(from); l.add(to, e)
        return FavoriteList(l)
    }

    fun moveTo(pkg: String, index: Int): FavoriteList {
        val from = indexOf(pkg)
        if (from < 0) return this
        val l = entries.toMutableList(); val e = l.removeAt(from)
        l.add(index.coerceIn(0, l.size), e)
        return FavoriteList(l)
    }

    private fun update(pkg: String, f: (FavoriteEntry) -> FavoriteEntry) =
        if (pkg !in this) this else FavoriteList(entries.map { if (it.pkg == pkg) f(it) else it })

    fun rename(pkg: String, label: String) = update(pkg) { it.copy(label = label.trim().take(40)) }
    fun setIcon(pkg: String, iconKey: String) = update(pkg) { it.copy(iconKey = iconKey.trim()) }
    fun setSize(pkg: String, size: TileSize) = update(pkg) { it.copy(size = size) }
    fun setShowName(pkg: String, show: Boolean) = update(pkg) { it.copy(showName = show) }
    fun setHidden(pkg: String, hidden: Boolean) = update(pkg) { it.copy(hidden = hidden) }

    /** يحذف الحزم غير المثبّتة فقط (لا يحذف المخفية المثبّتة). */
    fun prune(installed: (String) -> Boolean) = FavoriteList(entries.filter { installed(it.pkg) })

    fun toJson(): String = MiniJson.stringify(linkedMapOf("v" to 1L, "items" to entries.map {
        linkedMapOf("pkg" to it.pkg, "label" to it.label, "icon" to it.iconKey,
            "size" to it.size.key, "name" to it.showName, "hidden" to it.hidden)
    }))

    companion object {
        fun fromJson(text: String?): FavoriteList {
            if (text.isNullOrBlank()) return FavoriteList()
            return try {
                val items = MiniJson.parseObject(text)["items"] as? List<*> ?: return FavoriteList()
                val seen = HashSet<String>()
                FavoriteList(items.mapNotNull { x ->
                    val m = x as? Map<*, *> ?: return@mapNotNull null
                    val pkg = (m["pkg"] as? String)?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
                    if (!seen.add(pkg)) return@mapNotNull null
                    FavoriteEntry(pkg, (m["label"] as? String) ?: "", (m["icon"] as? String) ?: "",
                        TileSize.fromKey(m["size"] as? String), (m["name"] as? Boolean) ?: true, (m["hidden"] as? Boolean) ?: false)
                })
            } catch (_: Exception) { FavoriteList() }
        }

        /** ترحيل الصيغة القديمة: قائمة حزم + تسميات بصيغة pkg||label. */
        fun fromLegacy(pinned: List<String>, labels: List<String>, showNames: Boolean): FavoriteList {
            val map = HashMap<String, String>()
            for (row in labels) { val i = row.indexOf("||"); if (i > 0) map[row.substring(0, i)] = row.substring(i + 2) }
            var l = FavoriteList()
            for (p in pinned) l = l.add(p).rename(p, map[p] ?: "").setShowName(p, showNames)
            return l
        }
    }
}
