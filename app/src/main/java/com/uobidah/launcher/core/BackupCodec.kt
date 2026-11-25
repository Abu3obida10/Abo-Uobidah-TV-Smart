package com.uobidah.launcher.core

/**
 * ترميز النسخ الاحتياطي. القيم تُحفظ بأنواعها الأصلية (s/i/l/f/b/set).
 * لا تدخل في النسخة أبداً: مفاتيح API والأسرار، وذاكرة التخزين المؤقت للمباريات/الشعارات.
 */
object BackupCodec {
    const val SCHEMA = "uobidah-backup"
    const val VERSION = 1

    private val EXCLUDED_PREFIXES = listOf(
        "secret_", "apikey", "api_key", "matches_", "team_badge_", "teamid_", "match_source_", "asked_perms"
    )
    private val EXCLUDED_EXACT = setOf("bg_version", "last_wall_day", "setup_prompt_ms")

    fun isExcluded(key: String): Boolean {
        val k = key.lowercase()
        return key in EXCLUDED_EXACT || EXCLUDED_PREFIXES.any { k.startsWith(it) } ||
            k.contains("token") || k.contains("password") || k.contains("secret")
    }

    fun export(all: Map<String, Any?>, appVersion: String, nowMs: Long): String {
        val out = LinkedHashMap<String, Any?>()
        for ((k, v) in all.toSortedMap()) {
            if (isExcluded(k)) continue
            val enc: Map<String, Any?> = when (v) {
                is String -> mapOf("t" to "s", "v" to v)
                is Int -> mapOf("t" to "i", "v" to v.toLong())
                is Long -> mapOf("t" to "l", "v" to v)
                is Float -> mapOf("t" to "f", "v" to v.toDouble())
                is Boolean -> mapOf("t" to "b", "v" to v)
                is Set<*> -> mapOf("t" to "set", "v" to v.map { it.toString() }.sorted())
                else -> continue
            }
            out[k] = enc
        }
        return MiniJson.stringify(linkedMapOf(
            "schema" to SCHEMA, "version" to VERSION.toLong(), "app" to appVersion,
            "createdAt" to nowMs, "values" to out
        ))
    }

    class Entry(val key: String, val type: String, val value: Any)
    class ImportResult(val entries: List<Entry>, val skipped: List<String>, val error: String?) {
        val ok get() = error == null
    }

    /** يتحقق من الملف كاملاً قبل أي تطبيق؛ أي خطأ بنيوي = رفض كامل بلا تغيير. */
    fun parse(text: String): ImportResult {
        val root = try { MiniJson.parseObject(text) } catch (e: Exception) {
            return ImportResult(emptyList(), emptyList(), "الملف ليس نسخة احتياطية صالحة (JSON تالف)")
        }
        if (root["schema"] != SCHEMA) return ImportResult(emptyList(), emptyList(), "الملف ليس نسخة احتياطية من هذا التطبيق")
        val ver = (root["version"] as? Number)?.toInt() ?: return ImportResult(emptyList(), emptyList(), "إصدار النسخة غير معروف")
        if (ver > VERSION) return ImportResult(emptyList(), emptyList(), "النسخة أحدث ($ver) من الإصدار المدعوم ($VERSION)")
        val values = root["values"] as? Map<*, *> ?: return ImportResult(emptyList(), emptyList(), "قسم القيم مفقود")
        val ok = ArrayList<Entry>(); val skipped = ArrayList<String>()
        for ((k, raw) in values) {
            val key = k.toString()
            val m = raw as? Map<*, *>
            if (isExcluded(key) || m == null) { skipped.add(key); continue }
            val v = m["v"]
            val e: Entry? = when (m["t"]) {
                "s" -> (v as? String)?.let { Entry(key, "s", it) }
                "i" -> (v as? Number)?.toLong()?.takeIf { it in Int.MIN_VALUE..Int.MAX_VALUE }?.let { Entry(key, "i", it.toInt()) }
                "l" -> (v as? Number)?.let { Entry(key, "l", it.toLong()) }
                "f" -> (v as? Number)?.let { Entry(key, "f", it.toFloat()) }
                "b" -> (v as? Boolean)?.let { Entry(key, "b", it) }
                "set" -> (v as? List<*>)?.takeIf { l -> l.all { it is String } }?.let { Entry(key, "set", it.map { s -> s as String }.toSet()) }
                else -> null
            }
            if (e == null) skipped.add(key) else ok.add(e)
        }
        return ImportResult(ok, skipped, null)
    }
}
