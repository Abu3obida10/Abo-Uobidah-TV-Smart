package com.uobidah.launcher

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
import android.view.Gravity

/**
 * عشرة تصاميم للبطاقات والأزرار تُطبَّق على كل التطبيق من نقطة واحدة (Ui.focusBg / Ui.staticCard).
 * التصميم محفوظ في Prefs.cardDesign، وإطار التركيز (الهالة) يبقى كما هو في كل التصاميم.
 */
object CardDesigns {
    const val COUNT = 10

    val names: List<String> = listOf(
        "زجاجي", "صلب", "إطار", "متدرج", "نيون", "مسطح", "ناعم", "حاد", "شريط جانبي", "دائري"
    )

    val descriptions: List<String> = listOf(
        "تدرج خفيف مع حد رفيع (الافتراضي)",
        "لون معتم كامل بدون شفافية",
        "بدون تعبئة وبحد واضح",
        "تدرج بلون التمييز",
        "خلفية داكنة وحد متوهج بلون التمييز",
        "تعبئة فقط بدون حدود",
        "زوايا أكثر استدارة وبدون حدود",
        "زوايا حادة وحد رفيع",
        "شريط ملوّن في بداية البطاقة",
        "زوايا دائرية كبيرة"
    )

    fun index(ctx: Context): Int = Prefs(ctx).cardDesign.coerceIn(0, COUNT - 1)

    /** نصف قطر الزوايا بعد تطبيق التصميم. */
    fun radius(design: Int, r: Float): Float = when (design) {
        6 -> r * 1.6f
        7 -> r * 0.3f
        9 -> r * 2.6f
        else -> r
    }

    private fun solidOf(fill: Int, t: UTheme): Int =
        if (Color.alpha(fill) == 0) Color.TRANSPARENT else Ui.over(fill, t.bgBottom)

    /** يدمج لوناً شبه شفاف فوق سطح معتم حتى لا تظهر الخلفية من خلف البطاقة. */
    private fun blend(c: Int, solid: Int): Int = if (Color.alpha(solid) == 0) c else Ui.over(c, solid)

    fun normal(ctx: Context, t: UTheme, radiusPx: Float, fill: Int, design: Int): Drawable {
        val bw = Ui.p(ctx, 1.5f)
        // كل ثيمات التطبيق تستخدم لون بطاقة بشفافية 80%؛ نثبّت اللون معتماً فوق الخلفية فلا تتغير ألوان البطاقات حسب الصورة خلفها
        val solid = solidOf(fill, t)
        return when (design) {
            1 -> GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                setColor(solidOf(fill, t))
                cornerRadius = radiusPx
                setStroke(Ui.p(ctx, 1f), t.border)
            }
            2 -> GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                setColor(Color.TRANSPARENT)
                cornerRadius = radiusPx
                setStroke(Ui.p(ctx, 2f), Ui.withAlpha(t.text, 110))
            }
            3 -> GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                intArrayOf(blend(Ui.withAlpha(t.accent, 120), solid), solid)
            ).apply {
                cornerRadius = radiusPx
                setStroke(bw, Ui.withAlpha(t.accent, 100))
            }
            4 -> GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                setColor(Ui.over(Color.argb(150, 0, 0, 0), t.bgBottom))
                cornerRadius = radiusPx
                setStroke(Ui.p(ctx, 2.5f), t.accent)
            }
            5 -> GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                setColor(solidOf(fill, t))
                cornerRadius = radiusPx
            }
            6 -> GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                intArrayOf(blend(Ui.withAlpha(Color.WHITE, 30), solid), solid)
            ).apply { cornerRadius = radiusPx }
            7 -> GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                intArrayOf(blend(Ui.withAlpha(t.cardFocus, 72), solid), solid)
            ).apply {
                cornerRadius = radiusPx
                setStroke(bw, t.border)
            }
            8 -> {
                val base = GradientDrawable().apply {
                    shape = GradientDrawable.RECTANGLE
                    setColor(solidOf(fill, t))
                    cornerRadius = radiusPx
                }
                val bar = GradientDrawable().apply {
                    shape = GradientDrawable.RECTANGLE
                    setColor(t.accent)
                    cornerRadius = radiusPx / 2f
                }
                val layers = LayerDrawable(arrayOf<Drawable>(base, bar))
                layers.setLayerWidth(1, Ui.p(ctx, 7f))
                layers.setLayerGravity(1, Gravity.START or Gravity.FILL_VERTICAL)
                layers
            }
            9 -> GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                intArrayOf(blend(Ui.withAlpha(t.cardFocus, 72), solid), solid)
            ).apply {
                cornerRadius = radiusPx
                setStroke(bw, t.border)
            }
            else -> GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                intArrayOf(blend(Ui.withAlpha(t.cardFocus, 72), solid), solid)
            ).apply {
                cornerRadius = radiusPx
                setStroke(bw, t.border)
            }
        }
    }
}
