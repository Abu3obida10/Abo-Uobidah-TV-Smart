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

/**
 * خلفية «المسجد عند الغروب» (مرجع R6) مرسومة بالكود: سماء غروب، شمس متوهجة، مسجد بقبة ومئذنتين وقباب صغيرة،
 * وانعكاس على الماء. لا صور ولا شبكة، ثابتة بلا حركة. الأشكال سيلويت داكن كي يبقى النص فوقها مقروءًا.
 * ملاحظة صادقة: هذا رسم تخطيطي وليس صورة فوتوغرافية كالمرجع؛ يمكن استبداله بصورة من «الخلفيات».
 */
class MosqueSceneBg : Drawable() {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var sky: Shader? = null
    private var sun: Shader? = null
    private var water: Shader? = null
    private val silhouette = Path()
    private val windows = Path()

    override fun onBoundsChange(b: Rect) {
        super.onBoundsChange(b)
        val w = b.width().toFloat().coerceAtLeast(1f)
        val h = b.height().toFloat().coerceAtLeast(1f)
        sky = LinearGradient(0f, 0f, 0f, h * 0.78f,
            intArrayOf(Color.parseColor("#2B2F77"), Color.parseColor("#7A3E8E"), Color.parseColor("#E2704F"), Color.parseColor("#F6B15A")),
            floatArrayOf(0f, 0.42f, 0.78f, 1f), Shader.TileMode.CLAMP)
        sun = RadialGradient(w * 0.72f, h * 0.76f, w * 0.34f,
            intArrayOf(Color.argb(235, 255, 226, 150), Color.argb(110, 255, 160, 80), Color.TRANSPARENT), floatArrayOf(0f, 0.38f, 1f), Shader.TileMode.CLAMP)
        water = LinearGradient(0f, h * 0.78f, 0f, h,
            intArrayOf(Color.parseColor("#C9703F"), Color.parseColor("#4A2A55"), Color.parseColor("#1B1840")), floatArrayOf(0f, 0.5f, 1f), Shader.TileMode.CLAMP)
        buildShapes(w, h)
    }

    private fun dome(p: Path, cx: Float, base: Float, r: Float, rise: Float) {
        p.moveTo(cx - r, base)
        p.cubicTo(cx - r, base - rise * 0.75f, cx - r * 0.35f, base - rise * 0.95f, cx, base - rise)
        p.cubicTo(cx + r * 0.35f, base - rise * 0.95f, cx + r, base - rise * 0.75f, cx + r, base)
        p.close()
    }

    private fun minaret(p: Path, cx: Float, base: Float, halfW: Float, height: Float) {
        p.addRect(cx - halfW, base - height, cx + halfW, base, Path.Direction.CW)
        p.addRect(cx - halfW * 1.6f, base - height, cx + halfW * 1.6f, base - height + halfW * 0.7f, Path.Direction.CW) // شرفة
        val top = base - height - halfW * 4.2f
        p.moveTo(cx - halfW * 1.2f, base - height)
        p.cubicTo(cx - halfW * 1.2f, base - height - halfW * 2f, cx - halfW * 0.3f, top + halfW, cx, top)
        p.cubicTo(cx + halfW * 0.3f, top + halfW, cx + halfW * 1.2f, base - height - halfW * 2f, cx + halfW * 1.2f, base - height)
        p.close()
        p.addCircle(cx, top - halfW * 0.6f, halfW * 0.45f, Path.Direction.CW) // رأس الهلال مبسّط
    }

    private fun buildShapes(w: Float, h: Float) {
        silhouette.reset(); windows.reset()
        val base = h * 0.80f
        val u = w / 1280f
        // مبنى القاعدة
        silhouette.addRect(w * 0.20f, base - h * 0.10f, w * 0.62f, base, Path.Direction.CW)
        // القبة الكبرى وطبلتها
        silhouette.addRect(w * 0.34f, base - h * 0.17f, w * 0.48f, base - h * 0.10f, Path.Direction.CW)
        dome(silhouette, w * 0.41f, base - h * 0.17f, w * 0.075f, h * 0.17f)
        // قباب جانبية
        dome(silhouette, w * 0.26f, base - h * 0.10f, w * 0.035f, h * 0.07f)
        dome(silhouette, w * 0.56f, base - h * 0.10f, w * 0.035f, h * 0.07f)
        // مئذنتان
        minaret(silhouette, w * 0.17f, base, 9f * u, h * 0.42f)
        minaret(silhouette, w * 0.65f, base, 9f * u, h * 0.42f)
        minaret(silhouette, w * 0.12f, base, 6f * u, h * 0.28f)
        // قوس البوابة ونوافذ مضيئة
        val gw = w * 0.025f
        windows.addRect(w * 0.41f - gw, base - h * 0.075f, w * 0.41f + gw, base, Path.Direction.CW)
        var x = w * 0.24f
        while (x < w * 0.60f) {
            if (kotlin.math.abs(x - w * 0.41f) > w * 0.04f) windows.addRect(x, base - h * 0.065f, x + 10f * u, base - h * 0.03f, Path.Direction.CW)
            x += w * 0.035f
        }
    }

    override fun draw(c: Canvas) {
        val b = bounds
        if (b.width() <= 0 || b.height() <= 0) return
        val w = b.width().toFloat()
        val h = b.height().toFloat()
        paint.style = Paint.Style.FILL
        paint.shader = sky; c.drawRect(0f, 0f, w, h * 0.80f, paint)
        paint.shader = sun; c.drawRect(0f, 0f, w, h, paint)
        paint.shader = water; c.drawRect(0f, h * 0.80f, w, h, paint)
        paint.shader = null
        // انعكاس المسجد على الماء (باهت ومقلوب)
        c.save(); c.scale(1f, -1f, 0f, h * 0.80f)
        paint.color = Color.argb(70, 20, 10, 40); c.drawPath(silhouette, paint)
        c.restore()
        paint.color = Color.parseColor("#1A1030"); c.drawPath(silhouette, paint)
        paint.color = Color.argb(235, 255, 200, 110); c.drawPath(windows, paint)
        // هلال فوق القبة
        val cx = w * 0.41f; val cy = h * 0.80f - h * 0.17f - h * 0.17f - h * 0.03f
        paint.color = Color.parseColor("#F4D58A"); c.drawCircle(cx, cy, h * 0.022f, paint)
        paint.color = Color.parseColor("#7A3E8E"); c.drawCircle(cx + h * 0.011f, cy - h * 0.004f, h * 0.019f, paint)
    }

    override fun setAlpha(alpha: Int) { paint.alpha = alpha }
    override fun setColorFilter(cf: ColorFilter?) { paint.colorFilter = cf }
    @Deprecated("Deprecated in Java") override fun getOpacity(): Int = PixelFormat.OPAQUE
}
