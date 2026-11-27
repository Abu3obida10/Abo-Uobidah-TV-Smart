package com.uobidah.launcher.v2

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
import android.graphics.drawable.StateListDrawable
import android.view.Gravity
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.uobidah.launcher.Icons
import com.uobidah.launcher.Motion
import com.uobidah.launcher.Ui

/**
 * نظام تصميم الإصدار 2 (إعادة بناء من الصفر — لا يعتمد على مكوّنات الواجهة القديمة).
 *
 * مبادئ ثابتة:
 * - داكن أولاً: لوحة ليلية عميقة تناسب التلفزيون والمشاهدة من بعيد، وتبقى مقروءة فوق أي خلفية.
 * - القياس بالعرض المرجعي 1920 عبر Ui.p (نفس نموذج التطبيق كله) — لا قياس بالكثافة أبدًا.
 * - لا رموز نصية/إيموجي في الواجهة إطلاقًا: كل أيقونة Vector من Icons (خطوط أجهزة التلفزيون ناقصة).
 * - تركيز الريموت واضح دائمًا: إطار ذهبي + هالة خفيفة + تكبير 2% (بدون ظلال، فهي مكلفة على Mali-G31).
 * - ميزانية أداء جهاز 1 جيجا: لا Blur حقيقي، لا طبقات Software، التدرجات كلها GPU.
 */
object V2Colors {
    val bgTop = Color.parseColor("#0D1524")
    val bgBottom = Color.parseColor("#06090F")
    val glowCool = Color.parseColor("#1E3D63")   // توهج أورورا بارد (أعلى الشاشة)
    val glowWarm = Color.parseColor("#4A3418")   // توهج دافئ قرب الأفق
    val card = Color.parseColor("#151D2E")       // قاعدة الزجاج الداكن
    val cardHigh = Color.parseColor("#1C2637")   // زجاج أعلى عند التركيز
    val text = Color.parseColor("#F2F5FA")
    val dim = Color.parseColor("#97A3BA")
    val accent = Color.parseColor("#E8C98A")     // الذهبي الأساسي (هوية الصلاة/المدينة)
    val accent2 = Color.parseColor("#7BD5C8")    // تركواز ثانوي (حيّ/رياضي)
    val live = Color.parseColor("#E5484D")
    val ok = Color.parseColor("#4CC38A")
}

/** مقاسات النص المرجعية (تُمرَّر عبر V2.p — تتدرج مع عرض الشاشة). */
object V2Type {
    const val DISPLAY = 62f   // اسم المدينة / الأرقام الكبيرة
    const val HEADLINE = 32f  // عناوين البطاقات
    const val TITLE = 24f     // عناوين الأقسام
    const val BODY = 20f      // نص عادي
    const val CAPTION = 16f   // نص ثانوي
}

object V2Space {
    const val XS = 6f
    const val S = 10f
    const val M = 16f
    const val L = 24f
    const val XL = 40f
    const val EDGE = 56f      // هامش حواف الشاشة (آمن من Overscan)
}

object V2 {
    fun p(ctx: Context, v: Float): Int = Ui.p(ctx, v)

    fun label(ctx: Context, text: String, size: Float, color: Int, bold: Boolean = false): TextView =
        Ui.label(ctx, text, size, color, bold)

    /** خلفية الشاشة: تدرج ليلي + توهجّان شعاعيان خفيفان (GradientDrawable فقط — رسم GPU رخيص). */
    fun background(): android.graphics.drawable.Drawable {
        val base = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, intArrayOf(V2Colors.bgTop, V2Colors.bgBottom))
        val cool = GradientDrawable().apply {
            gradientType = GradientDrawable.RADIAL_GRADIENT
            setGradientCenter(0.22f, 0.06f)
            gradientRadius = 720f
            colors = intArrayOf(Ui.withAlpha(V2Colors.glowCool, 110), Color.TRANSPARENT)
        }
        val warm = GradientDrawable().apply {
            gradientType = GradientDrawable.RADIAL_GRADIENT
            setGradientCenter(0.82f, 1.0f)
            gradientRadius = 900f
            colors = intArrayOf(Ui.withAlpha(V2Colors.glowWarm, 90), Color.TRANSPARENT)
        }
        return LayerDrawable(arrayOf(base, cool, warm))
    }

    private fun glassNormal(ctx: Context, radiusPx: Float): GradientDrawable =
        GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, intArrayOf(
            Ui.withAlpha(Ui.over(Ui.withAlpha(Color.WHITE, 14), V2Colors.card), 225),
            Ui.withAlpha(V2Colors.card, 235)
        )).apply {
            cornerRadius = radiusPx
            setStroke(p(ctx, 1.2f), Ui.withAlpha(Color.WHITE, 22))
        }

    private fun glassFocused(ctx: Context, radiusPx: Float): android.graphics.drawable.Drawable {
        val halo = Ui.round(Ui.withAlpha(V2Colors.accent, 70), radiusPx + p(ctx, 5f))
        val inner = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, intArrayOf(
            Ui.over(Ui.withAlpha(Color.WHITE, 20), V2Colors.cardHigh), V2Colors.cardHigh
        )).apply {
            cornerRadius = radiusPx
            setStroke(p(ctx, 3.5f), V2Colors.accent)
        }
        val layers = LayerDrawable(arrayOf(halo, inner))
        val inset = p(ctx, 5f)
        layers.setLayerInset(1, inset, inset, inset, inset)
        return layers
    }

    /** سطح زجاجي داكن قياسي مع حالة تركيز واضحة (إطار ذهبي + هالة). */
    fun glass(view: View, radiusDp: Float = 26f, focusable: Boolean = true, onFocusScale: Boolean = true) {
        val ctx = view.context
        val r = p(ctx, radiusDp).toFloat()
        view.background = StateListDrawable().apply {
            addState(intArrayOf(android.R.attr.state_focused), glassFocused(ctx, r))
            addState(intArrayOf(), glassNormal(ctx, r))
        }
        if (!focusable) return
        view.isFocusable = true
        view.setOnFocusChangeListener { target, has ->
            target.animate().cancel()
            val scale = if (has && onFocusScale && Motion.enabled(ctx)) 1.02f else 1f
            target.animate().scaleX(scale).scaleY(scale).setDuration(100L).start()
        }
    }

    /** زر أيقونة دائري (Vector فقط) — الوحدة الأساسية لشريط الإجراءات. */
    fun iconBtn(ctx: Context, iconKey: String, desc: String, onClick: () -> Unit): View {
        val size = p(ctx, 56f)
        val b = ImageView(ctx)
        b.setImageDrawable(Icons.drawable(iconKey, V2Colors.text, p(ctx, 26f)))
        b.scaleType = ImageView.ScaleType.CENTER
        b.contentDescription = desc
        glass(b, 28f)
        b.isClickable = true
        b.setOnClickListener { onClick() }
        b.layoutParams = LinearLayout.LayoutParams(size, size)
        return b
    }

    /**
     * بلاطة قسم/تطبيق عمودية: أيقونة داخل دائرة زجاجية + عنوان + سطر ثانوي اختياري.
     * تعرض أيقونة تطبيق حقيقية (Drawable) أو أيقونة نظام بمفتاح Icons.
     */
    fun tile(ctx: Context, icon: android.graphics.drawable.Drawable?, iconKey: String, label: String, sub: String? = null, onClick: () -> Unit): View {
        val box = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(p(ctx, V2Space.M), p(ctx, V2Space.M), p(ctx, V2Space.M), p(ctx, V2Space.S))
            contentDescription = label
            isClickable = true
            setOnClickListener { onClick() }
        }
        glass(box, 24f)
        val circle = ImageView(ctx)
        val d = icon ?: Icons.drawable(iconKey, V2Colors.accent, p(ctx, 40f))
        circle.setImageDrawable(d)
        circle.scaleType = ImageView.ScaleType.CENTER
        circle.background = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(Ui.withAlpha(Color.WHITE, 12))
        }
        box.addView(circle, Ui.lp(p(ctx, 76f), p(ctx, 76f)))
        box.addView(label(ctx, label, V2Type.BODY, V2Colors.text, true).apply {
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
            gravity = Gravity.CENTER
        }, Ui.lpm(Ui.WRAP, Ui.WRAP, 0f, 0, p(ctx, V2Space.S), 0, 0))
        if (sub != null) {
            box.addView(label(ctx, sub, V2Type.CAPTION, V2Colors.dim).apply {
                maxLines = 1
                ellipsize = android.text.TextUtils.TruncateAt.END
                gravity = Gravity.CENTER
            }, Ui.lpm(Ui.WRAP, Ui.WRAP, 0f, 0, p(ctx, V2Space.XS), 0, 0))
        }
        return box
    }

    /** عنوان قسم مع إجراء اختياري على الجهة المقابلة («المباريات — الكل ›»). */
    fun sectionHeader(ctx: Context, title: String, actionText: String? = null, onAction: (() -> Unit)? = null): View {
        val row = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutDirection = View.LAYOUT_DIRECTION_RTL
            gravity = Gravity.CENTER_VERTICAL
        }
        val bar = View(ctx)
        bar.setBackgroundColor(V2Colors.accent)
        row.addView(bar, Ui.lpm(p(ctx, 5f), p(ctx, 22f), 0f, 0, 0, p(ctx, V2Space.S), 0))
        row.addView(label(ctx, title, V2Type.TITLE, V2Colors.text, true), Ui.lp(0, Ui.WRAP, 1f))
        if (actionText != null && onAction != null) {
            val act = label(ctx, actionText, V2Type.BODY, V2Colors.accent, true)
            act.contentDescription = actionText
            act.setPadding(p(ctx, V2Space.S), p(ctx, V2Space.XS), p(ctx, V2Space.S), p(ctx, V2Space.XS))
            glass(act, 16f)
            act.isClickable = true
            act.setOnClickListener { onAction() }
            row.addView(act, Ui.lpm(Ui.WRAP, Ui.WRAP, 0f, p(ctx, V2Space.M), 0, 0, 0))
        }
        return row
    }

    /** شارة حالة صغيرة (قادمة/جارية/مباشر…) — نص عربي فقط على كبسولة ملوّنة. */
    fun badge(ctx: Context, text: String, color: Int): TextView {
        val b = label(ctx, text, V2Type.CAPTION, Color.WHITE, true)
        b.setPadding(p(ctx, V2Space.S), p(ctx, V2Space.XS), p(ctx, V2Space.S), p(ctx, V2Space.XS))
        b.background = Ui.round(Ui.withAlpha(color, 200), p(ctx, 14f).toFloat())
        b.maxLines = 1
        return b
    }
}
