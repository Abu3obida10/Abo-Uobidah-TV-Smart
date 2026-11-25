package com.uobidah.launcher

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.RadialGradient
import android.graphics.Rect
import android.graphics.Shader
import android.graphics.drawable.Drawable
import java.util.Random

/**
 * مشهد ليلي مرسوم بالكود (مرجع R3/R4): سماء متدرجة، مجرّة مائلة، نجوم ثابتة، سلسلتا جبال، وطبقا استقبال.
 * لا يحتاج أي صورة أو شبكة، ثابت (seed ثابت) فلا يتغير شكله بين التشغيلات، ولا يرسم شيئًا متحركًا.
 */
class NightSceneBg : Drawable() {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val starPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
    private var stars = FloatArray(0)      // x, y, radius, alpha (نسب من 0 إلى 1)
    private val farRidge = Path()
    private val nearRidge = Path()
    private var skyShader: Shader? = null
    private var galaxyShader: Shader? = null
    private var glowShader: Shader? = null

    override fun onBoundsChange(b: Rect) {
        super.onBoundsChange(b)
        val w = b.width().toFloat().coerceAtLeast(1f)
        val h = b.height().toFloat().coerceAtLeast(1f)
        skyShader = LinearGradient(0f, 0f, 0f, h,
            intArrayOf(Color.parseColor("#050A1E"), Color.parseColor("#0B1B4D"), Color.parseColor("#2A1B5E"), Color.parseColor("#4A1F55")),
            floatArrayOf(0f, 0.45f, 0.78f, 1f), Shader.TileMode.CLAMP)
        galaxyShader = RadialGradient(w * 0.5f, h * 0.28f, w * 0.42f,
            intArrayOf(Color.argb(120, 120, 150, 255), Color.argb(50, 90, 110, 220), Color.TRANSPARENT),
            floatArrayOf(0f, 0.5f, 1f), Shader.TileMode.CLAMP)
        glowShader = RadialGradient(w * 0.82f, h * 0.92f, w * 0.5f,
            intArrayOf(Color.argb(110, 236, 72, 153), Color.TRANSPARENT), null, Shader.TileMode.CLAMP)
        val rnd = Random(20261125L)
        stars = FloatArray(150 * 4)
        for (i in 0 until 150) {
            stars[i * 4] = rnd.nextFloat()
            stars[i * 4 + 1] = rnd.nextFloat() * 0.7f
            stars[i * 4 + 2] = 0.6f + rnd.nextFloat() * 1.6f
            stars[i * 4 + 3] = 0.25f + rnd.nextFloat() * 0.65f
        }
        farRidge.reset(); nearRidge.reset()
        farRidge.moveTo(0f, h)
        farRidge.lineTo(0f, h * 0.80f)
        var x = 0f
        var up = false
        while (x < w) {
            x += w * (0.07f + rnd.nextFloat() * 0.06f)
            farRidge.lineTo(x, h * (if (up) 0.72f + rnd.nextFloat() * 0.05f else 0.80f + rnd.nextFloat() * 0.05f))
            up = !up
        }
        farRidge.lineTo(w, h); farRidge.close()
        nearRidge.moveTo(0f, h)
        nearRidge.lineTo(0f, h * 0.90f)
        x = 0f; up = true
        while (x < w) {
            x += w * (0.09f + rnd.nextFloat() * 0.08f)
            nearRidge.lineTo(x, h * (if (up) 0.84f + rnd.nextFloat() * 0.04f else 0.92f + rnd.nextFloat() * 0.03f))
            up = !up
        }
        nearRidge.lineTo(w, h); nearRidge.close()
    }

    override fun draw(c: Canvas) {
        val b = bounds
        if (b.width() <= 0 || b.height() <= 0) return
        val w = b.width().toFloat()
        val h = b.height().toFloat()
        paint.shader = skyShader; c.drawRect(0f, 0f, w, h, paint)
        c.save(); c.rotate(-18f, w * 0.5f, h * 0.28f)
        paint.shader = galaxyShader; c.drawRect(-w * 0.2f, h * 0.05f, w * 1.2f, h * 0.55f, paint)
        c.restore()
        paint.shader = glowShader; c.drawRect(0f, 0f, w, h, paint)
        paint.shader = null
        val unit = w / 1280f
        var i = 0
        while (i < stars.size) {
            starPaint.alpha = (stars[i + 3] * 255).toInt()
            c.drawCircle(stars[i] * w, stars[i + 1] * h, stars[i + 2] * unit, starPaint)
            i += 4
        }
        paint.color = Color.parseColor("#0A1030"); c.drawPath(farRidge, paint)
        drawDish(c, w * 0.80f, h * 0.76f, h * 0.10f)
        drawDish(c, w * 0.67f, h * 0.80f, h * 0.07f)
        paint.color = Color.parseColor("#04061A"); c.drawPath(nearRidge, paint)
    }

    private fun drawDish(c: Canvas, cx: Float, base: Float, size: Float) {
        paint.color = Color.parseColor("#03050F")
        paint.strokeWidth = size * 0.07f
        paint.style = Paint.Style.STROKE
        c.drawLine(cx, base, cx, base - size * 0.6f, paint)
        paint.style = Paint.Style.FILL
        c.save(); c.rotate(-35f, cx, base - size * 0.6f)
        c.drawOval(cx - size * 0.55f, base - size * 0.78f, cx + size * 0.55f, base - size * 0.42f, paint)
        c.restore()
        c.drawCircle(cx + size * 0.3f, base - size * 1.0f, size * 0.05f, paint)
    }

    override fun setAlpha(alpha: Int) { paint.alpha = alpha }
    override fun setColorFilter(cf: ColorFilter?) { paint.colorFilter = cf }
    @Deprecated("Deprecated in Java") override fun getOpacity(): Int = PixelFormat.OPAQUE
}
