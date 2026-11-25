package com.uobidah.launcher.core

import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

/** تحويل اللحظة الحالية إلى وقت المدينة المختارة، مع منطقة احتياطية صالحة. */
object CityClock {
    private val defaultFallback: ZoneId = ZoneId.of("Africa/Cairo")

    fun zone(zoneId: String, fallback: ZoneId = defaultFallback): ZoneId =
        runCatching { ZoneId.of(zoneId.trim()) }.getOrElse { fallback }

    fun localNow(
        zoneId: String,
        instant: Instant = Instant.now(),
        fallback: ZoneId = defaultFallback
    ): LocalDateTime = LocalDateTime.ofInstant(instant, zone(zoneId, fallback))
}
