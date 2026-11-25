package com.uobidah.launcher

import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView
import android.graphics.Typeface
import android.util.TypedValue
import android.widget.Toast
import com.uobidah.launcher.core.IslandGeometry
import kotlin.math.min

/**
 * A professionally styled, measured Android-TV overlay. When overlay permission is missing,
 * Android Toasts are deliberately NOT presented as the island (Toast is always bottom-centred).
 */
object IslandController {
    private val handler = Handler(Looper.getMainLooper())
    private var current: View? = null
    private var currentWm: WindowManager? = null
    private var hideTask: Runnable? = null
    private var lastPermissionNoticeAt = 0L

    /** نتيجة آخر محاولة عرض: تُستخدم لتشخيص صريح بدل رسائل نجاح كاذبة. */
    class Status(val ok: Boolean, val mode: String, val message: String, val atMs: Long = System.currentTimeMillis())

    @Volatile var lastStatus: Status? = null
        private set

    /** إذن «الظهور فوق التطبيقات» الرسمي. */
    fun hasOverlayPermission(ctx: Context): Boolean {
        val official = try { Settings.canDrawOverlays(ctx) } catch (_: Exception) { false }
        if (official) return true
        // بعض الـ firmware (TV) ترجع canDrawOverlays=false رغم أن الإذن ممنوح عبر appops؛ نتحقق من الـ AppOp مباشرة.
        return try {
            val ops = ctx.getSystemService(Context.APP_OPS_SERVICE) as android.app.AppOpsManager
            @Suppress("DEPRECATION")
            ops.checkOpNoThrow("android:system_alert_window", android.os.Process.myUid(), ctx.packageName) ==
                android.app.AppOpsManager.MODE_ALLOWED
        } catch (_: Exception) { false }
    }

    /** خدمة إمكانية الدخول المفعّلة تتيح نافذة TYPE_ACCESSIBILITY_OVERLAY بدون إذن الظهور فوق التطبيقات. */
    fun hasAccessibilityOverlay(): Boolean = HomeKeyOverrideService.instance != null

    fun canDraw(ctx: Context): Boolean = hasOverlayPermission(ctx) || hasAccessibilityOverlay()

    /** وصف الوضع الحالي للمستخدم. */
    fun modeLabel(ctx: Context): String = when {
        hasOverlayPermission(ctx) -> "الظهور فوق التطبيقات (رسمي)"
        hasAccessibilityOverlay() -> "عبر خدمة إمكانية الدخول (Abo Uobidah TV Home Control)"
        else -> "غير متاح"
    }

    private fun report(ok: Boolean, mode: String, msg: String) {
        lastStatus = Status(ok, mode, msg)
    }

    fun show(
        ctx: Context,
        title: String,
        message: String,
        icon: String = "✦",
        durationSec: Int = -1,
        position: Int = -1,
        force: Boolean = false
    ) {
        val app = ctx.applicationContext
        handler.post { showNow(app, title, message, icon, durationSec, position, force) }
    }

    private fun dp(ctx: Context, value: Float): Int = Ui.p(ctx, value)

    private fun mix(a: Int, b: Int, amount: Float): Int {
        val t = amount.coerceIn(0f, 1f)
        return Color.rgb(
            (Color.red(a) * (1f - t) + Color.red(b) * t).toInt(),
            (Color.green(a) * (1f - t) + Color.green(b) * t).toInt(),
            (Color.blue(a) * (1f - t) + Color.blue(b) * t).toInt()
        )
    }

    /** يبني واجهة الجزيرة نفسها؛ تُستخدم للنافذة العائمة وللمعاينة الحية داخل الإعدادات. */
    internal fun buildBox(
        ctx: Context,
        prefs: Prefs,
        title: String,
        message: String,
        icon: String,
        maxWidthDp: Int? = null
    ): LinearLayout {
        Fonts.current = Fonts.typeface(prefs.fontIndex)

        val shape = prefs.islandShape.coerceIn(0, 9)
        val size = prefs.islandSize.coerceIn(0, 2)
        val titleSize = floatArrayOf(21f, 25f, 29f)[size]
        val msgSize = floatArrayOf(15f, 18f, 21f)[size]
        val verticalTitleSize = floatArrayOf(19f, 22f, 25f)[size]
        val verticalMsgSize = floatArrayOf(14f, 16f, 18f)[size]
        val accent = mix(prefs.islandColor, Color.rgb(90, 190, 255), 0.34f)
        val radius = when (shape) {
            0, 3 -> dp(ctx, 100f).toFloat()
            1, 5 -> dp(ctx, 28f).toFloat()
            2, 9 -> dp(ctx, 20f).toFloat()
            4, 7 -> dp(ctx, 34f).toFloat()
            6 -> dp(ctx, 18f).toFloat()
            else -> dp(ctx, 42f).toFloat()
        }
        val fullWidth = shape in setOf(2, 5, 9)
        val vertical = shape == 4 || shape == 7
        val horizontalPad = if (vertical) 18f else if (shape == 3) 14f else 22f
        val availableWidthDp = (maxWidthDp ?: ((ctx.resources.displayMetrics.widthPixels /
            ctx.resources.displayMetrics.density.coerceAtLeast(0.1f)).toInt() - 48)).coerceAtLeast(1)
        // Reserve room for the icon and the NOW chip first. On narrow TV/preview sizes,
        // shrink the text column instead of pushing the chip outside the measured island.
        val fixedHorizontalDp = if (vertical) horizontalPad * 2f
            else horizontalPad * 2f + 54f + 16f + 116f + 14f
        val adaptiveTextMaxWidthDp = (availableWidthDp - fixedHorizontalDp.toInt())
            .coerceIn(if (vertical) 64 else 32, 430)

        val box = LinearLayout(ctx).apply {
            orientation = if (vertical) LinearLayout.VERTICAL else LinearLayout.HORIZONTAL
            gravity = if (vertical) Gravity.CENTER_HORIZONTAL else Gravity.CENTER_VERTICAL
            layoutDirection = View.LAYOUT_DIRECTION_RTL
            val verticalPad = if (vertical) 10f else if (shape == 3) 8f else if (shape == 9) 18f else 14f
            setPadding(dp(ctx, horizontalPad), dp(ctx, verticalPad), dp(ctx, horizontalPad), dp(ctx, verticalPad))
            elevation = dp(ctx, 22f).toFloat()
            clipToOutline = true
            contentDescription = "$title. $message"
        }

        val base = GradientDrawable(
            GradientDrawable.Orientation.TL_BR,
            intArrayOf(
                mix(prefs.islandColor, Color.WHITE, 0.13f),
                prefs.islandColor,
                mix(prefs.islandColor, Color.BLACK, 0.42f)
            )
        ).apply {
            cornerRadius = radius
            setStroke(dp(ctx, if (shape == 6 || shape == 8) 2.5f else 1.5f), Color.argb(if (shape == 6) 230 else 145, Color.red(accent), Color.green(accent), Color.blue(accent)))
        }
        val glow = GradientDrawable().apply {
            cornerRadius = radius + dp(ctx, 4f)
            setColor(Color.argb(42, Color.red(accent), Color.green(accent), Color.blue(accent)))
        }
        val layers = LayerDrawable(arrayOf(glow, base))
        val inset = dp(ctx, 3f)
        layers.setLayerInset(1, inset, inset, inset, inset)
        box.background = layers

        // Icon badge.
        val iconBadge = LinearLayout(ctx).apply {
            gravity = Gravity.CENTER
            background = GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                intArrayOf(mix(accent, Color.WHITE, 0.22f), mix(accent, Color.BLACK, 0.20f))
            ).apply {
                cornerRadius = if (shape == 0 || shape == 3) dp(ctx, 100f).toFloat() else dp(ctx, 16f).toFloat()
                setStroke(dp(ctx, 1f), Color.argb(100, 255, 255, 255))
            }
        }
        val iconView = Ui.label(ctx, icon, floatArrayOf(27f, 32f, 37f)[size], Color.WHITE, true).apply {
            gravity = Gravity.CENTER
            maxLines = 1
        }
        val iconSize = if (vertical) 48f else 54f
        iconBadge.addView(iconView, LinearLayout.LayoutParams(dp(ctx, iconSize), dp(ctx, iconSize)))
        box.addView(iconBadge, LinearLayout.LayoutParams(dp(ctx, iconSize), dp(ctx, iconSize)).apply {
            if (vertical) bottomMargin = dp(ctx, 8f) else marginEnd = dp(ctx, 16f)
        })

        val col = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_VERTICAL
            layoutDirection = View.LAYOUT_DIRECTION_RTL
        }
        val titleView = Ui.label(ctx, title, if (vertical) verticalTitleSize else titleSize, Color.WHITE, true).apply {
            maxLines = if (vertical) 3 else 1
            ellipsize = android.text.TextUtils.TruncateAt.END
            breakStrategy = android.text.Layout.BREAK_STRATEGY_SIMPLE
            hyphenationFrequency = android.text.Layout.HYPHENATION_FREQUENCY_NONE
        }
        val messageView = Ui.label(ctx, message, if (vertical) verticalMsgSize else msgSize, Color.argb(226, 242, 247, 255)).apply {
            maxLines = if (vertical) 8 else 2
            ellipsize = android.text.TextUtils.TruncateAt.END
            breakStrategy = android.text.Layout.BREAK_STRATEGY_SIMPLE
            hyphenationFrequency = android.text.Layout.HYPHENATION_FREQUENCY_NONE
            if (message.isBlank()) visibility = View.GONE
        }
        if (!fullWidth) {
            titleView.maxWidth = dp(ctx, adaptiveTextMaxWidthDp.toFloat())
            messageView.maxWidth = dp(ctx, adaptiveTextMaxWidthDp.toFloat())
        }
        val textWidth = if (fullWidth || vertical) ViewGroup.LayoutParams.MATCH_PARENT else ViewGroup.LayoutParams.WRAP_CONTENT
        if (vertical) col.gravity = Gravity.CENTER
        col.addView(titleView, LinearLayout.LayoutParams(textWidth, ViewGroup.LayoutParams.WRAP_CONTENT))
        col.addView(messageView, LinearLayout.LayoutParams(textWidth, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            topMargin = dp(ctx, 5f)
        })
        val textParams = when {
            fullWidth && !vertical -> LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            vertical -> LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            else -> LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        }
        if (vertical) textParams.bottomMargin = dp(ctx, 6f)
        box.addView(col, textParams)

        // A small, non-intrusive "now" chip gives the overlay a designed status hierarchy.
        val status = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            // Stable child ordering across RTL firmware: readable Arabic label + green live dot.
            layoutDirection = View.LAYOUT_DIRECTION_LTR
            minimumWidth = dp(ctx, if (vertical) 74f else 78f)
            minimumHeight = dp(ctx, 30f)
            background = GradientDrawable().apply {
                cornerRadius = dp(ctx, 30f).toFloat()
                setColor(Color.argb(56, 74, 222, 128))
                setStroke(dp(ctx, 1f), Color.argb(150, 74, 222, 128))
            }
            setPadding(dp(ctx, 10f), dp(ctx, if (vertical) 5f else 6f), dp(ctx, 10f), dp(ctx, if (vertical) 5f else 6f))
            contentDescription = "الحالة الآن"
        }
        val nowLabel = TextView(ctx).apply {
            text = "الآن"
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_PX, dp(ctx, if (vertical) 14f else 15f).toFloat())
            typeface = Typeface.create("sans-serif", Typeface.BOLD)
            gravity = Gravity.CENTER
            textDirection = View.TEXT_DIRECTION_RTL
            maxLines = 1
            isSingleLine = true
            includeFontPadding = false
            minWidth = dp(ctx, 31f)
            contentDescription = "الآن"
        }
        status.addView(nowLabel, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(ctx, 22f)))
        val dot = View(ctx).apply {
            background = GradientDrawable().apply {
                // Explicit receiver: avoid resolving `shape` against the enclosing
                // Island/view scope instead of GradientDrawable's Kotlin property.
                this.shape = GradientDrawable.OVAL
                setColor(0xFF4ADE80.toInt())
            }
            contentDescription = "مباشر"
        }
        status.addView(dot, LinearLayout.LayoutParams(dp(ctx, 8f), dp(ctx, 8f)).apply { marginStart = dp(ctx, 5f) })
        box.addView(status, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            if (vertical) gravity = Gravity.CENTER else marginStart = dp(ctx, 14f)
        })
        return box
    }

    /** معاينة حية تُضاف داخل صفحة الإعدادات بدون الحاجة لأي إذن. */
    fun buildPreview(ctx: Context, title: String, message: String, icon: String, maxWidthDp: Int? = null): View =
        buildBox(ctx, Prefs(ctx), title, message, icon, maxWidthDp)

    private fun showPermissionHint(ctx: Context) {
        val now = System.currentTimeMillis()
        if (now - lastPermissionNoticeAt < 30_000L) return
        lastPermissionNoticeAt = now
        Toast.makeText(
            ctx,
            "الجزيرة تحتاج إذن «الظهور فوق التطبيقات». افتح إعدادات الجزيرة وفعّل الإذن حتى تظهر بتصميمها ومكانها الصحيح.",
            Toast.LENGTH_LONG
        ).show()
    }

    private fun showNow(ctx: Context, title: String, message: String, icon: String, durationSec: Int, position: Int, force: Boolean) {
        val prefs = Prefs(ctx)
        if (!prefs.islandEnabled && !force) {
            report(false, "-", "الجزيرة معطّلة من الإعدادات (فعّل «تفعيل الجزيرة الديناميكية»).")
            return
        }
        if (!canDraw(ctx)) {
            removeNow(ctx)
            report(false, "-", "لا يوجد إذن الظهور فوق التطبيقات ولا خدمة إمكانية الدخول مفعّلة.")
            showPermissionHint(ctx)
            return
        }
        val viaAccessibility = !hasOverlayPermission(ctx)
        val service = HomeKeyOverrideService.instance

        removeNow(ctx)
        val shape = prefs.islandShape.coerceIn(0, 9)

        val wm = (if (viaAccessibility && service != null) service.getSystemService(Context.WINDOW_SERVICE)
            else ctx.getSystemService(Context.WINDOW_SERVICE)) as WindowManager
        val metrics = ctx.resources.displayMetrics
        val screenW = metrics.widthPixels.coerceAtLeast(1)
        val screenH = metrics.heightPixels.coerceAtLeast(1)
        val margin = dp(ctx, 24f)
        val availableW = (screenW - margin * 2).coerceAtLeast(1)
        val availableWidthDp = (availableW / metrics.density.coerceAtLeast(0.1f)).toInt().coerceAtLeast(1)
        val box = buildBox(ctx, prefs, title, message, icon, availableWidthDp)
        val barWidth = if (shape == 2 || shape == 5 || shape == 9) availableW else min(availableW, dp(ctx, 860f))
        val widthSpec = if (shape == 2 || shape == 5 || shape == 9) View.MeasureSpec.makeMeasureSpec(barWidth, View.MeasureSpec.EXACTLY)
            else if (shape == 4 || shape == 7) View.MeasureSpec.makeMeasureSpec(min(availableW, dp(ctx, if (shape == 4) 340f else 390f)), View.MeasureSpec.EXACTLY)
            else View.MeasureSpec.makeMeasureSpec(availableW, View.MeasureSpec.AT_MOST)
        box.measure(widthSpec, View.MeasureSpec.makeMeasureSpec((screenH - margin * 2).coerceAtLeast(1), View.MeasureSpec.AT_MOST))
        val measuredW = box.measuredWidth.coerceIn(1, availableW)
        val measuredH = box.measuredHeight.coerceIn(1, (screenH - margin * 2).coerceAtLeast(1))
        val requestedPosition = if (position in 0..4) position else prefs.islandPos
        val point = IslandGeometry.place(
            screenW, screenH, measuredW, measuredH,
            requestedPosition, prefs.islandCustomPosition,
            prefs.islandOffsetX, prefs.islandOffsetY,
            metrics.density, 24
        )

        val lp = WindowManager.LayoutParams(
            measuredW,
            measuredH,
            if (viaAccessibility) WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY else WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.LEFT
            x = point.xPx
            y = point.yPx
            windowAnimations = 0
        }

        try {
            wm.addView(box, lp)
            current = box
            currentWm = wm
            report(true, if (viaAccessibility) "accessibility" else "overlay",
                "ظهرت الجزيرة عبر " + (if (viaAccessibility) "خدمة إمكانية الدخول." else "إذن الظهور فوق التطبيقات."))
            val opacity = prefs.islandOpacity.coerceIn(25, 100) / 100f
            if (Motion.enabled(ctx)) {
                val fromY = when {
                    prefs.islandCustomPosition -> if (prefs.islandOffsetY < 0) -dp(ctx, 28f).toFloat() else dp(ctx, 28f).toFloat()
                    requestedPosition == IslandGeometry.BOTTOM_CENTER -> dp(ctx, 28f).toFloat()
                    requestedPosition == IslandGeometry.CENTER -> dp(ctx, 10f).toFloat()
                    else -> -dp(ctx, 28f).toFloat()
                }
                box.alpha = 0f
                box.translationY = fromY
                box.scaleX = 0.965f
                box.scaleY = 0.965f
                box.animate().alpha(opacity).translationY(0f).scaleX(1f).scaleY(1f).setDuration(260L).start()
            } else {
                box.alpha = opacity
                box.translationY = 0f
                box.scaleX = 1f
                box.scaleY = 1f
            }
        } catch (error: Exception) {
            current = null
            report(false, if (viaAccessibility) "accessibility" else "overlay",
                "رفض النظام إنشاء نافذة الجزيرة: ${error.javaClass.simpleName}. قد يقيّد الـ Firmware النوافذ العائمة.")
            Toast.makeText(ctx, "تعذر إنشاء نافذة الجزيرة. (${error.javaClass.simpleName})", Toast.LENGTH_LONG).show()
            return
        }

        val task = Runnable {
            if (current === box) {
                if (Motion.enabled(ctx)) {
                    val outY = if (requestedPosition == IslandGeometry.BOTTOM_CENTER) dp(ctx, 18f).toFloat() else -dp(ctx, 18f).toFloat()
                    box.animate().alpha(0f).translationY(outY).setDuration(170L).withEndAction {
                        if (current === box) removeNow(ctx)
                    }.start()
                } else removeNow(ctx)
            }
        }
        hideTask = task
        val lifeMs = (if (durationSec > 0) durationSec else prefs.islandDuration).coerceIn(2, 60) * 1000L
        handler.postDelayed(task, lifeMs)
        // شبكة أمان: لو لم يعمل أنيميشن الإخفاء (أنيميشن النظام معطّل أو تجمّد) تُحذف النافذة بالقوة
        handler.postDelayed({ if (current === box) removeNow(ctx) }, lifeMs + 1500L)
    }

    /** إزالة فورية لأي نافذة جزيرة عالقة (تُستدعى أيضاً عند إيقاف الخدمة). */
    fun dismiss(ctx: Context) {
        handler.post { removeNow(ctx.applicationContext) }
    }

    private fun removeNow(ctx: Context) {
        hideTask?.let { handler.removeCallbacks(it) }
        hideTask = null
        val old = current
        val oldWm = currentWm
        current = null
        currentWm = null
        if (old != null) {
            try { old.animate().cancel() } catch (_: Exception) { }
            try { old.alpha = 0f } catch (_: Exception) { }
            // نجرّب النافذة الأصلية ثم الأخرى؛ بدون هذا قد تبقى نافذة شفافة عالقة فوق الشاشة
            val candidates = ArrayList<WindowManager>()
            if (oldWm != null) candidates.add(oldWm)
            try { candidates.add(ctx.getSystemService(Context.WINDOW_SERVICE) as WindowManager) } catch (_: Exception) { }
            try {
                val svc = HomeKeyOverrideService.instance
                if (svc != null) candidates.add(svc.getSystemService(Context.WINDOW_SERVICE) as WindowManager)
            } catch (_: Exception) { }
            for (wm in candidates) {
                try { wm.removeViewImmediate(old); break } catch (_: Exception) { }
            }
        }
    }
}
