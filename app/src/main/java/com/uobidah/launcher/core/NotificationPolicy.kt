package com.uobidah.launcher.core

/** أنواع التنبيهات التي يعرضها اللانشر. لكل نوع إعداد مستقل. */
enum class AlertType(val key: String, val labelAr: String) {
    APP("app", "إشعارات التطبيقات"),
    MATCH_START("match_start", "بداية مباراة"),
    MATCH_GOAL("match_goal", "هدف"),
    MATCH_END("match_end", "نهاية مباراة"),
    ADHAN("adhan", "الأذان"),
    IQAMA("iqama", "الإقامة / التذكير بالصلاة"),
    SYSTEM("system", "تنبيهات النظام");

    companion object { fun fromKey(k: String): AlertType? = values().firstOrNull { it.key == k } }
}

object SoundUri {
    const val INHERIT = "inherit"   // يرث من المستوى الأعلى
    const val DEFAULT = ""          // نغمة إشعارات النظام
    const val SILENT = "silent"     // بلا صوت
}

/** قيمة null أو -1 أو "inherit" تعني: ورّث من المستوى الأعلى. */
data class AlertRule(
    val showInIsland: Boolean? = null,
    val soundEnabled: Boolean? = null,
    val soundUri: String = SoundUri.INHERIT,
    val volume: Int = -1,
    val vibrate: Boolean? = null,
    val durationSec: Int = -1,
    val position: Int = -1
) {
    fun toMap(): Map<String, Any?> {
        val m = LinkedHashMap<String, Any?>()
        showInIsland?.let { m["island"] = it }
        soundEnabled?.let { m["sound"] = it }
        if (soundUri != SoundUri.INHERIT) m["uri"] = soundUri
        if (volume >= 0) m["vol"] = volume
        vibrate?.let { m["vib"] = it }
        if (durationSec >= 0) m["dur"] = durationSec
        if (position >= 0) m["pos"] = position
        return m
    }

    companion object {
        fun fromMap(m: Map<*, *>): AlertRule = AlertRule(
            m["island"] as? Boolean, m["sound"] as? Boolean,
            (m["uri"] as? String) ?: SoundUri.INHERIT,
            ((m["vol"] as? Number)?.toInt() ?: -1).let { if (it < 0) -1 else it.coerceAtMost(100) },
            m["vib"] as? Boolean,
            ((m["dur"] as? Number)?.toInt() ?: -1).let { if (it < 0) -1 else it.coerceIn(2, 60) },
            ((m["pos"] as? Number)?.toInt() ?: -1).let { if (it < 0) -1 else it.coerceAtMost(4) }
        )
    }
}

/** النتيجة النهائية بعد دمج: التطبيق ثم النوع ثم الإعداد العام. */
data class ResolvedAlert(
    val showInIsland: Boolean,
    val playSound: Boolean,
    val soundUri: String,
    val volume: Int,
    val vibrate: Boolean,
    val durationSec: Int,
    val position: Int
)

/**
 * سياسة الإشعارات: عام ← نوع ← تطبيق (الأخير يفوز).
 * التطبيق يؤثر فقط على AlertType.APP.
 */
class NotificationPolicy(
    val global: AlertRule = DEFAULT_GLOBAL,
    val perType: Map<AlertType, AlertRule> = emptyMap(),
    val perApp: Map<String, AlertRule> = emptyMap(),
    /** مفتاح رئيسي: لو false لا يظهر ولا يُسمع أي شيء. */
    val masterEnabled: Boolean = true
) {
    fun resolve(type: AlertType, pkg: String? = null): ResolvedAlert {
        val layers = ArrayList<AlertRule>(3)
        layers.add(global)
        perType[type]?.let { layers.add(it) }
        if (type == AlertType.APP && pkg != null) perApp[pkg]?.let { layers.add(it) }
        fun <T> pick(get: (AlertRule) -> T?, base: T): T {
            var out = base
            for (l in layers) get(l)?.let { out = it }
            return out
        }
        val island = pick({ it.showInIsland }, true)
        val sound = pick({ it.soundEnabled }, true)
        val uri = pick({ it.soundUri.takeIf { u -> u != SoundUri.INHERIT } }, SoundUri.DEFAULT)
        val vol = pick({ it.volume.takeIf { v -> v >= 0 } }, 80)
        val vib = pick({ it.vibrate }, false)
        val dur = pick({ it.durationSec.takeIf { v -> v >= 0 } }, 8)
        val pos = pick({ it.position.takeIf { v -> v >= 0 } }, 0)
        val audible = masterEnabled && sound && vol > 0 && uri != SoundUri.SILENT
        return ResolvedAlert(masterEnabled && island, audible, uri, vol, masterEnabled && vib, dur, pos)
    }

    fun withApp(pkg: String, rule: AlertRule) = copy(perApp = perApp + (pkg to rule))
    fun withoutApp(pkg: String) = copy(perApp = perApp - pkg)
    fun withType(t: AlertType, rule: AlertRule) = copy(perType = perType + (t to rule))
    fun withGlobal(rule: AlertRule) = copy(global = rule)
    fun withMaster(on: Boolean) = copy(masterEnabled = on)

    private fun copy(
        global: AlertRule = this.global, perType: Map<AlertType, AlertRule> = this.perType,
        perApp: Map<String, AlertRule> = this.perApp, masterEnabled: Boolean = this.masterEnabled
    ) = NotificationPolicy(global, perType, perApp, masterEnabled)

    fun toJson(): String = MiniJson.stringify(linkedMapOf(
        "v" to 1L,
        "master" to masterEnabled,
        "global" to global.toMap(),
        "types" to perType.entries.associate { it.key.key to it.value.toMap() },
        "apps" to perApp.mapValues { it.value.toMap() }
    ))

    companion object {
        val DEFAULT_GLOBAL = AlertRule(true, true, SoundUri.DEFAULT, 80, false, 8, 0)

        /** يعيد سياسة افتراضية عند أي JSON تالف بدل الانهيار. */
        fun fromJson(text: String?): NotificationPolicy {
            if (text.isNullOrBlank()) return NotificationPolicy()
            return try {
                val o = MiniJson.parseObject(text)
                val g = (o["global"] as? Map<*, *>)?.let { AlertRule.fromMap(it) } ?: DEFAULT_GLOBAL
                val types = LinkedHashMap<AlertType, AlertRule>()
                (o["types"] as? Map<*, *>)?.forEach { (k, v) ->
                    val t = AlertType.fromKey(k.toString()); if (t != null && v is Map<*, *>) types[t] = AlertRule.fromMap(v)
                }
                val apps = LinkedHashMap<String, AlertRule>()
                (o["apps"] as? Map<*, *>)?.forEach { (k, v) -> if (v is Map<*, *>) apps[k.toString()] = AlertRule.fromMap(v) }
                NotificationPolicy(g, types, apps, (o["master"] as? Boolean) ?: true)
            } catch (_: Exception) { NotificationPolicy() }
        }

        /**
         * ترحيل الإعدادات القديمة (قبل 5.4) دون فقدان اختيارات المستخدم.
         * التطبيقات في النظام القديم «اشتراك صريح»: لا يظهر ولا يصدر صوتاً إلا ما اختاره المستخدم،
         * لذلك نوع APP افتراضيه إخفاء/كتم وكل تطبيق مختار يحصل على قاعدة تفتحه.
         */
        fun fromLegacy(
            soundEnabled: Boolean, soundUri: String, volume: Int, vibrate: Boolean,
            allowedApps: List<String>, soundApps: List<String>
        ): NotificationPolicy {
            var p = NotificationPolicy(AlertRule(null, soundEnabled, soundUri, volume.coerceIn(0, 100), vibrate, 8, 0))
            p = p.withType(AlertType.APP, AlertRule(showInIsland = false, soundEnabled = false))
            for (pkg in (allowedApps + soundApps).toSet()) {
                p = p.withApp(pkg, AlertRule(showInIsland = allowedApps.contains(pkg), soundEnabled = soundApps.contains(pkg)))
            }
            return p
        }
    }
}

/**
 * يمنع التكرار والتداخل:
 *  - نفس المفتاح خلال dedupWindowMs يُتجاهل بالكامل (تحديثات إشعار متكررة).
 *  - لا يُسمح بصوتين أقرب من minSoundGapMs (يظهر الإشعار بلا صوت).
 */
class AlertGate(private val dedupWindowMs: Long = 4_000L, private val minSoundGapMs: Long = 1_200L) {
    data class Admission(val show: Boolean, val sound: Boolean)

    private val seen = LinkedHashMap<String, Long>()
    private var lastSoundAt = Long.MIN_VALUE / 2

    @Synchronized fun admit(key: String, wantsSound: Boolean, now: Long): Admission {
        val it = seen.entries.iterator()
        while (it.hasNext()) if (now - it.next().value > dedupWindowMs * 4) it.remove()
        val prev = seen[key]
        if (prev != null && now - prev in 0..dedupWindowMs) return Admission(false, false)
        seen[key] = now
        if (seen.size > 200) seen.remove(seen.keys.first())
        val sound = wantsSound && now - lastSoundAt >= minSoundGapMs
        if (sound) lastSoundAt = now
        return Admission(true, sound)
    }

    @Synchronized fun reset() { seen.clear(); lastSoundAt = Long.MIN_VALUE / 2 }
}
