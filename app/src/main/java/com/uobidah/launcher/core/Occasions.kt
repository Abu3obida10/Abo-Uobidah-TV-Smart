package com.uobidah.launcher.core

import java.time.LocalDate
import java.time.chrono.HijrahDate
import java.time.temporal.ChronoField
import java.time.temporal.ChronoUnit

/**
 * مناسبات هجرية قادمة (مرجع R6) محسوبة بتقويم أم القرى في java.time.
 * التواريخ تقديرية: الإعلان الرسمي (رؤية الهلال/دار الإفتاء) قد يختلف يومًا، ويُكتب ذلك في الواجهة.
 */
object Occasions {
    data class Item(val nameAr: String, val date: LocalDate, val daysLeft: Long)

    private val defs = listOf(
        Triple("رمضان", 9, 1),
        Triple("عيد الفطر", 10, 1),
        Triple("عيد الأضحى", 12, 10)
    )

    const val DISCLAIMER_AR = "التواريخ تقديرية بتقويم أم القرى لحين إعلان الجهة الرسمية في بلدك."

    fun upcoming(today: LocalDate): List<Item> {
        val hy = try { HijrahDate.from(today).get(ChronoField.YEAR) } catch (_: Exception) { return emptyList() }
        return defs.mapNotNull { (name, month, day) ->
            val d = next(hy, month, day, today) ?: return@mapNotNull null
            Item(name, d, ChronoUnit.DAYS.between(today, d))
        }
    }

    private fun next(hijriYear: Int, month: Int, day: Int, today: LocalDate): LocalDate? {
        for (y in hijriYear..hijriYear + 1) {
            val d = try { LocalDate.from(HijrahDate.of(y, month, day)) } catch (_: Exception) { null } ?: continue
            if (!d.isBefore(today)) return d
        }
        return null
    }
}
