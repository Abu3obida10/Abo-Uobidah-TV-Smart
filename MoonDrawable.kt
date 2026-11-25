package com.uobidah.launcher

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.RectF
import android.graphics.drawable.Drawable
import com.uobidah.launcher.core.MoonPhase
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos

/**
 * قمر مرسوم بالطور الفعلي (من [MoonPhase.ageDays]) لبطاقة الطقس في R9. رسم هندسي (نصف مضاء + قطع ناقص للفاصل)، وليس صورة فوتوغرافية.
 * الهلال المتزايد يُضاء من اليمين والمتناقص من اليسار (نصف الكرة الشمالي).
 */
class MoonDrawable(private val ageDays: Double) : Drawable() {
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)

    override fun draw(canvas: Canvas) {
        val b = bounds
        val r = minOf(b.width(), b.height()) / 2f
        if (r <= 0f) return
        val cx = b.exactCenterX(); val cy = b.exactCenterY()
        val dark = Color.rgb(38, 43, 56); val lit = Color.rgb(232, 229, 218)
        val phase = ageDays / MoonPhase.SYNODIC_DAYS
        val c = cos(2 * PI * phase).toFloat() // 1 محاق ... -1 بدر
        val save = canvas.save()
        canvas.clipPath(Path().apply { addCircle(cx, cy, r, Path.Direction.CW) })
        p.style = Paint.Style.FILL
        p.color = dark
        canvas.drawRect(cx - r, cy - r, cx + r, cy + r, p)
        p.color = lit
        if (phase < 0.5) canvas.drawRect(cx, cy - r, cx + r, cy + r, p) else canvas.drawRect(cx - r, cy - r, cx, cy + r, p)
        val rx = r * abs(c)
        p.color = if (c > 0) dark else lit
        canvas.drawOval(RectF(cx - rx, cy - r, cx + rx, cy + r), p)
        canvas.restoreToCount(save)
        p.style = Paint.Style.STROKE
        p.strokeWidth = maxOf(1f, r * 0.03f)
        p.color = Color.argb(90, 255, 255, 255)
        canvas.drawCircle(cx, cy, r - p.strokeWidth / 2f, p)
    }

    override fun setAlpha(alpha: Int) { p.alpha = alpha }
    override fun setColorFilter(colorFilter: ColorFilter?) { p.colorFilter = colorFilter }
    @Deprecated("Deprecated in Java")
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
}
