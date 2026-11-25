package com.uobidah.launcher.core

/** نادٍ متتبَّع. id هو معرّف TheSportsDB الحقيقي للفريق؛ لا نخترع معرفات. */
data class TrackedClub(val id: String, val nameAr: String, val nameEn: String, val builtIn: Boolean = false)

object ClubRegistry {
    val BUILT_IN = listOf(
        TrackedClub("138995", "الأهلي المصري", "Al Ahly", true),
        TrackedClub("133612", "مانشستر يونايتد", "Manchester United", true),
        TrackedClub("133738", "ريال مدريد", "Real Madrid", true)
    )

    sealed class AddResult {
        data class Added(val list: List<TrackedClub>) : AddResult()
        data class Rejected(val reason: String) : AddResult()
    }

    fun add(current: List<TrackedClub>, id: String, nameAr: String, nameEn: String): AddResult {
        val cleanId = id.trim()
        if (cleanId.isEmpty() || !cleanId.all { it.isDigit() } || cleanId.length > 10) return AddResult.Rejected("معرّف النادي يجب أن يكون رقماً")
        val en = nameEn.trim()
        if (en.isEmpty()) return AddResult.Rejected("اسم النادي مطلوب")
        if ((BUILT_IN + current).any { it.id == cleanId }) return AddResult.Rejected("النادي مضاف مسبقاً")
        if (current.size >= 20) return AddResult.Rejected("الحد الأقصى 20 نادياً مضافاً")
        return AddResult.Added(current + TrackedClub(cleanId, nameAr.trim().ifEmpty { en }, en))
    }

    fun remove(current: List<TrackedClub>, id: String): List<TrackedClub> = current.filterNot { it.id == id && !it.builtIn }

    fun all(custom: List<TrackedClub>): List<TrackedClub> = BUILT_IN + custom

    fun toJson(custom: List<TrackedClub>) = MiniJson.stringify(custom.filter { !it.builtIn }.map {
        linkedMapOf("id" to it.id, "ar" to it.nameAr, "en" to it.nameEn)
    })

    fun fromJson(text: String?): List<TrackedClub> {
        if (text.isNullOrBlank()) return emptyList()
        return try {
            (MiniJson.parse(text) as? List<*>)?.mapNotNull {
                val m = it as? Map<*, *> ?: return@mapNotNull null
                val id = (m["id"] as? String) ?: return@mapNotNull null
                val en = (m["en"] as? String) ?: return@mapNotNull null
                if (id.isEmpty() || !id.all { c -> c.isDigit() } || BUILT_IN.any { b -> b.id == id }) null
                else TrackedClub(id, (m["ar"] as? String) ?: en, en)
            }.orEmpty().distinctBy { it.id }
        } catch (_: Exception) { emptyList() }
    }
}

enum class ConnState { NEVER, OK, CACHED, FAILED }

/** حالة الاتصال بمصدر البيانات؛ تُحفظ ولا تُخترع: لا تحديث ناجح = لا وقت تحديث. */
data class SourceStatus(
    val state: ConnState = ConnState.NEVER,
    val lastSuccessMs: Long = 0L,
    val lastAttemptMs: Long = 0L,
    val detail: String = ""
) {
    fun afterSuccess(now: Long) = SourceStatus(ConnState.OK, now, now, "")
    fun afterFailure(now: Long, detail: String, hasCache: Boolean) =
        SourceStatus(if (hasCache) ConnState.CACHED else ConnState.FAILED, lastSuccessMs, now, detail)

    fun describeAr(now: Long): String = when (state) {
        ConnState.NEVER -> "لم يتم الاتصال بالمصدر بعد"
        ConnState.OK -> "متصل • آخر تحديث ناجح ${ago(now - lastSuccessMs)}"
        ConnState.CACHED -> "بلا اتصال • بيانات محفوظة" + (if (lastSuccessMs > 0) " من ${ago(now - lastSuccessMs)}" else "") + detailSuffix()
        ConnState.FAILED -> "تعذر الاتصال" + detailSuffix() + " • لا توجد بيانات محفوظة"
    }

    private fun detailSuffix() = if (detail.isBlank()) "" else " ($detail)"

    fun toJson() = MiniJson.stringify(linkedMapOf("s" to state.name, "ok" to lastSuccessMs, "at" to lastAttemptMs, "d" to detail))

    companion object {
        fun ago(deltaMs: Long): String {
            val s = (deltaMs.coerceAtLeast(0)) / 1000
            return when {
                s < 60 -> "قبل لحظات"
                s < 3600 -> "قبل ${s / 60} دقيقة"
                s < 86400 -> "قبل ${s / 3600} ساعة"
                else -> "قبل ${s / 86400} يوم"
            }
        }

        fun fromJson(text: String?): SourceStatus {
            if (text.isNullOrBlank()) return SourceStatus()
            return try {
                val m = MiniJson.parseObject(text)
                SourceStatus(
                    runCatching { ConnState.valueOf(m["s"] as String) }.getOrDefault(ConnState.NEVER),
                    (m["ok"] as? Number)?.toLong() ?: 0L, (m["at"] as? Number)?.toLong() ?: 0L, (m["d"] as? String) ?: ""
                )
            } catch (_: Exception) { SourceStatus() }
        }

        /** رسالة آمنة من استثناء الشبكة دون تسريب المفتاح من الرابط. */
        fun safeDetail(msg: String?, secret: String?): String {
            var s = msg.orEmpty()
            if (!secret.isNullOrBlank()) s = s.replace(secret, "***")
            return s.take(80)
        }
    }
}

/** بناء رابط TheSportsDB: المفتاح العام 123 أو مفتاح premium الذي يدخله المستخدم. */
object SportsDbUrl {
    const val PUBLIC_KEY = "123"
    fun base(userKey: String?): String {
        val k = userKey?.trim().orEmpty()
        val safe = if (k.isNotEmpty() && k.all { it.isLetterOrDigit() || it == '_' || it == '-' }) k else PUBLIC_KEY
        return "https://www.thesportsdb.com/api/v1/json/$safe/"
    }
}
