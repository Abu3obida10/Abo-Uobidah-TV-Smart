package com.uobidah.launcher

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.drawable.Drawable

/** Brand-tinted tile fill taken from an app icon. The label color is always kept readable (contrast >= 4.5). */
object BrandTint {
    /** Saturation-weighted average color of the icon, or null for white/black/grey icons. */
    fun dominant(pixels: IntArray): Int? {
        var r = 0.0; var g = 0.0; var b = 0.0; var wsum = 0.0
        val hsv = FloatArray(3)
        for (p in pixels) {
            val a = Color.alpha(p)
            if (a < 128) continue
            Color.colorToHSV(p, hsv)
            val w = (hsv[1] * hsv[2]).toDouble()
            if (hsv[1] < 0.25f || hsv[2] < 0.2f) continue
            r += Color.red(p) * w; g += Color.green(p) * w; b += Color.blue(p) * w; wsum += w
        }
        if (wsum < pixels.size * 0.03) return null
        return Color.rgb((r / wsum).toInt(), (g / wsum).toInt(), (b / wsum).toInt())
    }

    private fun lum(c: Int): Double {
        fun ch(v: Int): Double { val s = v / 255.0; return if (s <= 0.03928) s / 12.92 else Math.pow((s + 0.055) / 1.055, 2.4) }
        return 0.2126 * ch(Color.red(c)) + 0.7152 * ch(Color.green(c)) + 0.0722 * ch(Color.blue(c))
    }
    fun contrast(a: Int, b: Int): Double {
        val x = lum(a); val y = lum(b)
        return (maxOf(x, y) + 0.05) / (minOf(x, y) + 0.05)
    }
    private fun mix(a: Int, b: Int, t: Float): Int = Color.rgb(
        (Color.red(a) * (1 - t) + Color.red(b) * t).toInt(),
        (Color.green(a) * (1 - t) + Color.green(b) * t).toInt(),
        (Color.blue(a) * (1 - t) + Color.blue(b) * t).toInt())

    /** Fill = brand color blended toward the card color until the label color has contrast >= 4.5; else the plain card color. */
    fun safeFill(dominant: Int?, card: Int, label: Int): Int {
        if (dominant == null) return card
        var t = 0.0f
        var c = mix(dominant, card, 0.15f)
        while (contrast(c, label) < 4.5 && t < 1f) { t += 0.1f; c = mix(dominant, card, (0.15f + t).coerceAtMost(1f)) }
        return if (contrast(c, label) >= 4.5) c else card
    }

    fun fillFor(icon: Drawable?, card: Int, label: Int): Int {
        if (icon == null) return card
        return try {
            val bmp = Bitmap.createBitmap(24, 24, Bitmap.Config.ARGB_8888)
            val c = Canvas(bmp)
            val old = icon.bounds
            icon.setBounds(0, 0, 24, 24); icon.draw(c); icon.bounds = old
            val px = IntArray(24 * 24); bmp.getPixels(px, 0, 24, 0, 0, 24, 24)
            val opaque = Color.rgb(Color.red(card), Color.green(card), Color.blue(card))
            val f = safeFill(dominant(px), opaque, label)
            if (f == opaque) card else f
        } catch (_: Throwable) { card }
    }
}
