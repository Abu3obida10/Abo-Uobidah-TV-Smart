package com.uobidah.launcher

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.RadialGradient
import android.graphics.Rect
import android.graphics.Shader
import android.graphics.Typeface
import android.graphics.drawable.Drawable

/**
 * خلفية الثيم: تدرج لوني + (لثيمات الأندية) زخرفة مرسومة بالكود بدون أي شعارات رسمية.
 *  - الأهلي: خطوط مائلة + شريط ثلاثي (أبيض/أسود/أحمر) + علامة مائية AL AHLY
 *  - مانشستر يونايتد: شريط ذهبي علوي + دائرة وخط منتصف الملعب + علامة مائية UNITED
 *  - ريال مدريد: خطوط رأسية رفيعة + شريط ذهبي + توهج ذهبي + علامة مائية HALA MADRID
 */
class ThemeBg(private val t: UTheme, private val withGradient: Boolean = true) : Drawable() {

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var shader: Shader? = null

    internal var rasterBuildCount: Int = 0
        private set
    internal var rasterReuseCount: Int = 0
        private set
    internal var rasterBuildNanos: Long = 0L
        private set
    internal var rasterBlitNanos: Long = 0L
        private set

    override fun onBoundsChange(bounds: Rect) {
        super.onBoundsChange(bounds)
        val scale = if (bounds.width() > 1400) 0.5f else 1f
        val requiredW = (bounds.width() * scale).toInt().coerceAtLeast(1)
        val requiredH = (bounds.height() * scale).toInt().coerceAtLeast(1)
        // Position-only changes do not alter the cached pixels; invalidate only on size changes.
        if (cache != null && (cacheW != requiredW || cacheH != requiredH)) {
            discardRasterCache()
        }
        shader = LinearGradient(
            0f, 0f, 0f, bounds.height().toFloat(),
            t.bgTop, t.bgBottom, Shader.TileMode.CLAMP
        )
    }

    private fun a(color: Int, alpha: Int): Int = Ui.withAlpha(color, alpha)

    private var cache: android.graphics.Bitmap? = null
    private var cacheW = 0
    private var cacheH = 0
    private var cacheSourceW = 0
    private var cacheSourceH = 0
    private val blit = Paint(Paint.FILTER_BITMAP_FLAG)

    /** Release stale raster memory before replacing a cache entry. */
    private fun discardRasterCache() {
        cache?.let { old -> if (!old.isRecycled) old.recycle() }
        cache = null
        cacheW = 0
        cacheH = 0
        cacheSourceW = 0
        cacheSourceH = 0
    }

    /** يرسم الخلفية مرة واحدة في صورة مخزّنة ثم يعيد استخدامها، فلا تُعاد التدرجات والزخارف في كل إطار (سبب بطء الثيمات). */
    override fun draw(canvas: Canvas) {
        val b = bounds
        if (b.width() <= 0 || b.height() <= 0) return
        val k = if (b.width() > 1400) 0.5f else 1f
        val cw = (b.width() * k).toInt().coerceAtLeast(1)
        val ch = (b.height() * k).toInt().coerceAtLeast(1)
        var bmp = cache
        if (bmp == null || cacheW != cw || cacheH != ch || cacheSourceW != b.width() || cacheSourceH != b.height()) {
            // Source dimensions can change while downscaling still yields the same raster
            // dimensions. Discard the old bitmap explicitly to avoid retaining stale pixels.
            if (bmp != null) discardRasterCache()
            val buildStarted = System.nanoTime()
            bmp = try { android.graphics.Bitmap.createBitmap(cw, ch, android.graphics.Bitmap.Config.ARGB_8888) } catch (e: Throwable) { null }
            if (bmp == null) { rasterBuildNanos += System.nanoTime() - buildStarted; drawContent(canvas, b); return }
            val c = Canvas(bmp)
            c.scale(k, k)
            // Render in local 0..width/height coordinates. Never mutate Drawable bounds
            // from inside draw(): doing so can invalidate a view-owned cache unexpectedly.
            drawContent(c, Rect(0, 0, b.width(), b.height()))
            cache = bmp
            cacheW = cw
            cacheH = ch
            cacheSourceW = b.width()
            cacheSourceH = b.height()
            rasterBuildCount++
            rasterBuildNanos += System.nanoTime() - buildStarted
        } else {
            rasterReuseCount++
        }
        val blitStarted = System.nanoTime()
        canvas.drawBitmap(bmp, null, b, blit)
        rasterBlitNanos += System.nanoTime() - blitStarted
    }

    private fun drawContent(canvas: Canvas, b: Rect) {
        if (b.width() <= 0 || b.height() <= 0) return
        val w = b.width().toFloat()
        val h = b.height().toFloat()
        val left = b.left.toFloat()
        val top = b.top.toFloat()
        val s = w / 1920f

        canvas.save()
        canvas.clipRect(b)
        canvas.translate(left, top)

        if (withGradient) {
            paint.reset()
            paint.isAntiAlias = true
            paint.shader = shader
            canvas.drawRect(0f, 0f, w, h, paint)
            paint.shader = null
            // إضاءة محيطية هادئة مستوحاة من مرجع التصميم، من دون صور ثقيلة أو مؤثرات مستمرة.
            glow(canvas, w * 0.88f, h * 0.10f, w * 0.34f, t.accent, 34)
            glow(canvas, w * 0.10f, h * 0.82f, w * 0.28f, t.accent, 19)
            drawAmbientStars(canvas, w, h, s)
        }

        val club = t.club
        if (club != null) {
            when (club.art) {
                1 -> drawAhly(canvas, w, h, s, club)
                2 -> drawUnited(canvas, w, h, s, club)
                else -> drawMadrid(canvas, w, h, s, club)
            }
        }
        canvas.restore()
    }

    private fun watermark(canvas: Canvas, text: String, color: Int, alpha: Int, size: Float, x: Float, y: Float) {
        paint.reset()
        paint.isAntiAlias = true
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD_ITALIC)
        paint.textSize = size
        paint.color = a(color, alpha)
        canvas.drawText(text, x, y, paint)
    }

    private fun glow(canvas: Canvas, cx: Float, cy: Float, r: Float, color: Int, alpha: Int) {
        paint.reset()
        paint.isAntiAlias = true
        paint.shader = RadialGradient(cx, cy, r, a(color, alpha), a(color, 0), Shader.TileMode.CLAMP)
        canvas.drawCircle(cx, cy, r, paint)
        paint.shader = null
    }

    /** نقاط ضوء ثابتة منخفضة الشفافية تمنح الخلفية عمقاً ليلياً دون صور أو حمل رسومي ثقيل. */
    private fun drawAmbientStars(canvas: Canvas, w: Float, h: Float, scale: Float) {
        val points = floatArrayOf(
            .05f,.12f, .11f,.29f, .18f,.08f, .23f,.41f, .29f,.18f, .34f,.72f,
            .40f,.30f, .46f,.12f, .52f,.56f, .58f,.22f, .64f,.82f, .70f,.38f,
            .76f,.14f, .82f,.60f, .88f,.31f, .94f,.76f, .15f,.87f, .27f,.59f,
            .37f,.91f, .49f,.89f, .61f,.08f, .73f,.92f, .91f,.10f, .97f,.45f
        )
        paint.reset()
        paint.isAntiAlias = true
        for (i in points.indices step 2) {
            val x = points[i] * w
            val y = points[i + 1] * h
            val alpha = 12 + ((i / 2) % 4) * 5
            paint.color = Color.argb(alpha, 190, 215, 255)
            val radius = (1.1f + ((i / 2) % 3) * .55f) * scale.coerceAtLeast(.65f)
            canvas.drawCircle(x, y, radius, paint)
        }
    }

    // ---------------- الأهلي ----------------
    private fun drawAhly(c: Canvas, w: Float, h: Float, s: Float, club: Club) {
        glow(c, w * 0.85f, 0f, w * 0.55f, Color.WHITE, 40)
        paint.reset()
        paint.isAntiAlias = true
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 26f * s
        paint.color = a(Color.WHITE, 15)
        val gap = 150f * s
        var x = -h
        while (x < w) {
            c.drawLine(x, h, x + h, 0f, paint)
            x += gap
        }
        watermark(c, "AL AHLY", Color.WHITE, 16, h * 0.30f, w * 0.03f, h * 0.90f)
        // شريط ثلاثي الألوان أسفل الشاشة
        paint.reset()
        val bar = 12f * s
        val seg = w / 3f
        paint.style = Paint.Style.FILL
        paint.color = Color.WHITE
        c.drawRect(0f, h - bar, seg, h, paint)
        paint.color = Color.parseColor("#111111")
        c.drawRect(seg, h - bar, seg * 2f, h, paint)
        paint.color = Color.parseColor("#E30613")
        c.drawRect(seg * 2f, h - bar, w, h, paint)
    }

    // ---------------- مانشستر يونايتد ----------------
    private fun drawUnited(c: Canvas, w: Float, h: Float, s: Float, club: Club) {
        glow(c, w * 0.2f, h * 0.1f, w * 0.5f, Color.parseColor("#DA291C"), 90)
        paint.reset()
        paint.isAntiAlias = true
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 5f * s
        paint.color = a(Color.WHITE, 22)
        c.drawCircle(w * 0.78f, h * 0.55f, h * 0.45f, paint)
        c.drawLine(w * 0.78f, 0f, w * 0.78f, h, paint)
        c.drawRect(w * 0.55f, h * 0.15f, w, h * 0.95f, paint)
        watermark(c, "UNITED", club.secondary, 20, h * 0.30f, w * 0.03f, h * 0.90f)
        // شريط ذهبي علوي + شريط أحمر سفلي
        paint.reset()
        paint.style = Paint.Style.FILL
        paint.color = club.secondary
        c.drawRect(0f, 0f, w, 8f * s, paint)
        paint.color = Color.parseColor("#DA291C")
        c.drawRect(0f, h - 10f * s, w, h, paint)
    }

    // ---------------- ريال مدريد ----------------
    private fun drawMadrid(c: Canvas, w: Float, h: Float, s: Float, club: Club) {
        glow(c, w * 0.5f, 0f, w * 0.6f, club.secondary, 55)
        paint.reset()
        paint.isAntiAlias = true
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 3f * s
        paint.color = a(Color.WHITE, 14)
        val gap = 38f * s
        var x = 0f
        while (x < w) {
            c.drawLine(x, 0f, x, h, paint)
            x += gap
        }
        watermark(c, "HALA MADRID", club.secondary, 20, h * 0.20f, w * 0.03f, h * 0.92f)
        paint.reset()
        paint.style = Paint.Style.FILL
        paint.color = club.secondary
        c.drawRect(0f, 0f, w, 7f * s, paint)
        paint.color = a(Color.WHITE, 200)
        c.drawRect(0f, 7f * s, w, 10f * s, paint)
        paint.color = club.secondary
        c.drawRect(0f, h - 7f * s, w, h, paint)
    }

    override fun setAlpha(alpha: Int) {}
    override fun setColorFilter(colorFilter: ColorFilter?) {}

    @Suppress("OVERRIDE_DEPRECATION", "DEPRECATION")
    override fun getOpacity(): Int = PixelFormat.OPAQUE
}
