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
 * مشهد غروب بنفسجي بجبال وأشجار صنوبر وطيور، مرسوم بالكود بالكامل (أصلي، ليس صورة ولا نسخة من المرجع R8).
 * يقترب من المرجع في الألوان والتكوين العام فقط: سماء بنفسجية تتدرج إلى وردي/برتقالي عند الأفق، ثلاث طبقات جبال،
 * شجرتا صنوبر على الجانبين. الاختلاف عن المرجع: لا ضباب فوتوغرافي ولا تفاصيل غابة ولا انعكاس.
 * لا شبكة ولا ملفات، seed ثابت فلا يتغير الشكل بين التشغيلات، ولا يرسم شيئًا متحركًا.
 */
class DuskSceneBg : Drawable() {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        color = Color.parseColor("#2A1A45")
    }
    private val farRidge = Path()
    private val midRidge = Path()
    private val nearRidge = Path()
    private val treePath = Path()
    private var skyShader: Shader? = null
    private var glowShader: Shader? = null
    private var birds = FloatArray(0)   // x, y, حجم (نسب من 0 إلى 1)
    private var stars = FloatArray(0)   // x, y, نصف قطر، شفافية

    override fun onBoundsChange(b: Rect) {
        super.onBoundsChange(b)
        val w = b.width().toFloat().coerceAtLeast(1f)
        val h = b.height().toFloat().coerceAtLeast(1f)
        skyShader = LinearGradient(0f, 0f, 0f, h * 0.78f,
            intArrayOf(Color.parseColor("#241A52"), Color.parseColor("#5B3A8C"), Color.parseColor("#B4608F"), Color.parseColor("#F0A08E")),
            floatArrayOf(0f, 0.38f, 0.72f, 1f), Shader.TileMode.CLAMP)
        glowShader = RadialGradient(w * 0.5f, h * 0.74f, w * 0.55f,
            intArrayOf(Color.argb(150, 255, 190, 150), Color.argb(50, 240, 130, 160), Color.TRANSPARENT),
            floatArrayOf(0f, 0.5f, 1f), Shader.TileMode.CLAMP)
        val rnd = Random(20261126L)
        stars = FloatArray(60 * 4)
        for (i in 0 until 60) {
            stars[i * 4] = rnd.nextFloat()
            stars[i * 4 + 1] = rnd.nextFloat() * 0.3f
            stars[i * 4 + 2] = 0.5f + rnd.nextFloat() * 0.9f
            stars[i * 4 + 3] = 0.2f + rnd.nextFloat() * 0.4f
        }
        birds = FloatArray(9 * 3)
        for (i in 0 until 9) {
            birds[i * 3] = 0.30f + rnd.nextFloat() * 0.22f
            birds[i * 3 + 1] = 0.40f + rnd.nextFloat() * 0.12f
            birds[i * 3 + 2] = 0.7f + rnd.nextFloat() * 0.6f
        }
        buildRidge(farRidge, w, h, 0.58f, 0.66f, 0.10f, rnd)
        buildRidge(midRidge, w, h, 0.68f, 0.76f, 0.12f, rnd)
        buildRidge(nearRidge, w, h, 0.82f, 0.88f, 0.14f, rnd)
    }

    private fun buildRidge(p: Path, w: Float, h: Float, topFrac: Float, lowFrac: Float, step: Float, rnd: Random) {
        p.reset()
        p.moveTo(0f, h)
        p.lineTo(0f, h * (topFrac + lowFrac) / 2f)
        var x = 0f
        var up = true
        while (x < w) {
            x += w * (step * 0.6f + rnd.nextFloat() * step * 0.8f)
            val frac = if (up) topFrac + rnd.nextFloat() * (lowFrac - topFrac) * 0.4f
                       else lowFrac - rnd.nextFloat() * (lowFrac - topFrac) * 0.4f
            p.lineTo(x.coerceAtMost(w), h * frac)
            up = !up
        }
        p.lineTo(w, h)
        p.close()
    }

    override fun draw(c: Canvas) {
        val b = bounds
        if (b.width() <= 0 || b.height() <= 0) return
        val w = b.width().toFloat()
        val h = b.height().toFloat()
        val unit = w / 1280f
        paint.shader = skyShader; c.drawRect(0f, 0f, w, h, paint)
        paint.shader = glowShader; c.drawRect(0f, 0f, w, h, paint)
        paint.shader = null
        paint.color = Color.WHITE
        var i = 0
        while (i < stars.size) {
            paint.alpha = (stars[i + 3] * 255).toInt()
            c.drawCircle(stars[i] * w, stars[i + 1] * h, stars[i + 2] * unit, paint)
            i += 4
        }
        paint.alpha = 255
        strokePaint.strokeWidth = (1.6f * unit).coerceAtLeast(1f)
        i = 0
        while (i < birds.size) {
            drawBird(c, birds[i] * w, birds[i + 1] * h, 7f * unit * birds[i + 2])
            i += 3
        }
        paint.color = Color.parseColor("#7C5A9E"); c.drawPath(farRidge, paint)
        paint.color = Color.parseColor("#4B3478"); c.drawPath(midRidge, paint)
        drawPine(c, w * 0.10f, h * 0.84f, h * 0.46f, "#1E1240")
        drawPine(c, w * 0.92f, h * 0.80f, h * 0.38f, "#1E1240")
        paint.color = Color.parseColor("#251748"); c.drawPath(nearRidge, paint)
        drawPine(c, w * 0.03f, h * 0.95f, h * 0.30f, "#150C30")
        drawPine(c, w * 0.97f, h * 0.95f, h * 0.24f, "#150C30")
    }

    private fun drawBird(c: Canvas, x: Float, y: Float, s: Float) {
        treePath.reset()
        treePath.moveTo(x - s, y - s * 0.4f)
        treePath.quadTo(x - s * 0.4f, y - s * 0.9f, x, y)
        treePath.quadTo(x + s * 0.4f, y - s * 0.9f, x + s, y - s * 0.4f)
        c.drawPath(treePath, strokePaint)
    }

    /** شجرة صنوبر: جذع وثلاث طبقات مثلثة متناقصة العرض. */
    private fun drawPine(c: Canvas, cx: Float, base: Float, height: Float, color: String) {
        paint.color = Color.parseColor(color)
        paint.style = Paint.Style.FILL
        val trunkW = height * 0.035f
        c.drawRect(cx - trunkW, base - height * 0.12f, cx + trunkW, base, paint)
        var layerTop = base - height
        val layerH = height * 0.42f
        for (k in 0 until 3) {
            val halfW = height * (0.13f + 0.07f * k)
            val top = layerTop + k * height * 0.22f
            treePath.reset()
            treePath.moveTo(cx, top)
            treePath.lineTo(cx - halfW, top + layerH)
            treePath.lineTo(cx + halfW, top + layerH)
            treePath.close()
            c.drawPath(treePath, paint)
        }
    }

    override fun setAlpha(alpha: Int) { paint.alpha = alpha }
    override fun setColorFilter(cf: ColorFilter?) { paint.colorFilter = cf }
    @Deprecated("Deprecated in Java") override fun getOpacity(): Int = PixelFormat.OPAQUE
}
