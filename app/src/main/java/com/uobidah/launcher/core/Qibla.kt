package com.uobidah.launcher.core

import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

/** اتجاه القبلة من إحداثيات المدينة (دائرة عظمى نحو الكعبة). منطق بحت قابل للاختبار على JVM. */
object Qibla {
    const val KAABA_LAT = 21.422487
    const val KAABA_LON = 39.826206

    /** الاتجاه بالدرجات من الشمال الحقيقي باتجاه عقارب الساعة (0..360)، أو null لإحداثيات غير صالحة. */
    fun bearing(lat: Double, lon: Double): Double? {
        if (!lat.isFinite() || !lon.isFinite() || lat !in -90.0..90.0 || lon !in -180.0..180.0) return null
        val p1 = Math.toRadians(lat)
        val p2 = Math.toRadians(KAABA_LAT)
        val dl = Math.toRadians(KAABA_LON - lon)
        val y = sin(dl) * cos(p2)
        val x = cos(p1) * sin(p2) - sin(p1) * cos(p2) * cos(dl)
        if (x == 0.0 && y == 0.0) return null // الموقع نفسه
        return (Math.toDegrees(atan2(y, x)) + 360.0) % 360.0
    }

    private val names = listOf("شمال", "شمال شرق", "شرق", "جنوب شرق", "جنوب", "جنوب غرب", "غرب", "شمال غرب")

    fun compassNameAr(deg: Double): String = names[(((deg % 360.0) + 360.0) % 360.0 / 45.0 + 0.5).toInt() % 8]

    /** نص عرض مثل «القبلة ١٣٦° جنوب شرق». */
    fun label(lat: Double, lon: Double): String? {
        val b = bearing(lat, lon) ?: return null
        val n = Math.round(b).toInt() % 360
        val digits = n.toString().map { '٠' + (it - '0') }.joinToString("")
        return "القبلة $digits° ${compassNameAr(b)}"
    }
}
