package com.uobidah.launcher

import android.app.Activity
import android.app.Dialog
import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
import android.graphics.drawable.StateListDrawable
import android.text.InputType
import android.text.TextUtils
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.provider.Settings
import com.uobidah.launcher.core.Contrast

/** هوية نادٍ كاملة (ألوان ثانوية + زخرفة + شعار نصي + مباريات). بدون استخدام شعارات رسمية. */
class Club(
    val en: String,
    val ar: String,
    val motto: String,
    val emoji: String,
    /** 1 = الأهلي (خطوط مائلة) ، 2 = يونايتد (ملعب + شريط ذهبي) ، 3 = ريال (خطوط رأسية + ذهبي) */
    val art: Int,
    val secondary: Int,
    val island: Int,
    /** اسم الفريق في TheSportsDB */
    val team: String,
    val wallQuery: String
)

/** ثيم ألوان. */
class UTheme(
    val name: String,
    val bgTop: Int,
    val bgBottom: Int,
    val card: Int,
    val cardFocus: Int,
    val accent: Int,
    textColor: Int,
    dimColor: Int,
    val border: Int = Color.argb(30, 255, 255, 255),
    val dock: Int = Color.argb(38, 255, 255, 255),
    val dockBorder: Int = Color.argb(70, 255, 255, 255),
    onAccent: Int = Color.WHITE,
    val radiusScale: Float = 1f,
    val club: Club? = null
) {
    /** النصوص الثانوية والرئيسية تُضبط تلقائيًا لتباين لا يقل عن WCAG AA فوق سطح البطاقة. */
    private val textSurface: Int = Ui.over(card, bgBottom)
    val text: Int = Contrast.ensure(textColor, textSurface)
    val dim: Int = Contrast.ensure(dimColor, textSurface)
    /** لون النص فوق لون التمييز: تباين لا يقل عن 4.5:1 متى أمكن. */
    val onAccent: Int = Contrast.onColor(accent, onAccent)
}

object Themes {
    private fun c(s: String): Int = Color.parseColor(s)

    /** قديم: أول ثيم نادٍ. لا تعتمد عليه؛ استخدم normalIndices/clubIndices. */
    const val CLUB_START = 7

    val all: List<UTheme> = listOf(
        UTheme("ليل أزرق فاخر", c("#07101F"), c("#02050C"), c("#CC0A1424"), c("#E6152F58"), c("#3B82F6"), c("#F8FAFC"), c("#A8B4C7"),
            border = c("#463B82F6"), dock = c("#A607101D"), dockBorder = c("#653B82F6")),
        UTheme("زمردي", c("#052E2B"), c("#021412"), c("#CC0B3D38"), c("#E6125E56"), c("#34D399"), c("#F0FDF4"), c("#8FB9AE")),
        UTheme("ذهبي ملكي", c("#2A1F05"), c("#120C02"), c("#CC3D2D0A"), c("#E6574010"), c("#FBBF24"), c("#FFFBEB"), c("#C9B27A")),
        UTheme("بنفسجي", c("#1E1038"), c("#0C0618"), c("#CC2E1A57"), c("#E6432680"), c("#C084FC"), c("#FAF5FF"), c("#B39DD6")),
        UTheme("ياقوتي", c("#3A0B14"), c("#1A0509"), c("#CC561324"), c("#E67A1B33"), c("#FB7185"), c("#FFF1F2"), c("#D6A0A8")),
        UTheme("سماوي محيط", c("#07313F"), c("#031820"), c("#CC0C4A5E"), c("#E6126985"), c("#22D3EE"), c("#ECFEFF"), c("#8CC4CF")),
        UTheme("فحمي برتقالي", c("#1F2937"), c("#0B0F17"), c("#CC2B3544"), c("#E63B4A5E"), c("#F97316"), c("#F9FAFB"), c("#9CA3AF")),

        // ===================== كأس الأندية =====================
        // 7) الأهلي: أحمر ناري + أبيض + أسود، خطوط مائلة وشريط ثلاثي الألوان
        UTheme(
            "الأهلي", c("#9A0B1D"), c("#2B0309"), c("#CC140205"), c("#E62E0409"),
            c("#FFFFFF"), c("#FFF5F5"), c("#F2B3B9"),
            border = c("#55FFFFFF"), dock = c("#A6000000"), dockBorder = c("#80FFFFFF"),
            onAccent = c("#1A0206"), radiusScale = 1f,
            club = Club("AL AHLY", "النادي الأهلي", "القلعة الحمراء", "🦅", 1,
                c("#FFFFFF"), c("#7F0F1C"), "Al Ahly", "Al Ahly SC wallpaper 4k")
        ),
        // 8) مانشستر يونايتد: أسود + أحمر شيطاني + ذهبي، زوايا حادة وخطوط ملعب
        UTheme(
            "مانشستر يونايتد", c("#1C0000"), c("#000000"), c("#CCDA291C"), c("#E6F23A2D"),
            c("#FBE122"), c("#FFFFFF"), c("#F6C2BD"),
            border = c("#66FBE122"), dock = c("#B3DA291C"), dockBorder = c("#99FBE122"),
            onAccent = c("#1A0000"), radiusScale = 0.5f,
            club = Club("MANCHESTER UNITED", "مانشستر يونايتد", "الشياطين الحمر", "😈", 2,
                c("#FBE122"), c("#8A1008"), "Manchester United", "Manchester United Old Trafford wallpaper 4k")
        ),
        // 9) ريال مدريد: أزرق ملكي + أبيض + ذهبي، خطوط رأسية وحواف ناعمة
        UTheme(
            "ريال مدريد", c("#0B3A82"), c("#030B1F"), c("#CC0A1F4A"), c("#E6133A80"),
            c("#F6C445"), c("#FFFFFF"), c("#A8BCE0"),
            border = c("#66F6C445"), dock = c("#990A1F4A"), dockBorder = c("#99F6C445"),
            onAccent = c("#FFFFFF"), radiusScale = 1.3f,
            club = Club("REAL MADRID", "ريال مدريد", "الملكي • Hala Madrid", "👑", 3,
                c("#F6C445"), c("#0B3A82"), "Real Madrid", "Real Madrid Bernabeu wallpaper 4k")
        ),

        // ===================== سمات عصرية (تُضاف بعد الأندية كي لا تتغير فهارس السمات المحفوظة) =====================
        UTheme("ثلج فاتح", c("#F4F7FB"), c("#DCE4F0"), c("#E6FFFFFF"), c("#FFFFFFFF"), c("#2563EB"), c("#0F172A"), c("#475569"),
            border = c("#332563EB"), dock = c("#CCFFFFFF"), dockBorder = c("#662563EB"), onAccent = c("#FFFFFF")),
        UTheme("رمل صحراوي فاتح", c("#FBF3E4"), c("#EBD9B4"), c("#E6FFFFFF"), c("#FFFFFFFF"), c("#B45309"), c("#2B1B07"), c("#6B5A3A"),
            border = c("#33B45309"), dock = c("#CCFFFFFF"), dockBorder = c("#66B45309"), onAccent = c("#FFFFFF")),
        UTheme("نعناع فاتح", c("#EEF9F3"), c("#CFE9DB"), c("#E6FFFFFF"), c("#FFFFFFFF"), c("#059669"), c("#0B2A1F"), c("#3F6B5A"),
            border = c("#33059669"), dock = c("#CCFFFFFF"), dockBorder = c("#66059669"), onAccent = c("#FFFFFF")),
        UTheme("وردي فاتح", c("#FFF1F5"), c("#F9D5E0"), c("#E6FFFFFF"), c("#FFFFFFFF"), c("#DB2777"), c("#3B0A24"), c("#7A3A57"),
            border = c("#33DB2777"), dock = c("#CCFFFFFF"), dockBorder = c("#66DB2777"), onAccent = c("#FFFFFF")),
        UTheme("أسود OLED", c("#000000"), c("#000000"), c("#CC111111"), c("#E61C1C1E"), c("#22D3EE"), c("#F5F5F5"), c("#9CA3AF"),
            border = c("#3322D3EE"), dock = c("#A6000000"), dockBorder = c("#6622D3EE"), onAccent = c("#04121A")),
        UTheme("غروب", c("#4A1942"), c("#14081A"), c("#CC2E1034"), c("#E6552064"), c("#FB923C"), c("#FFF7ED"), c("#D2A9B8"),
            border = c("#33FB923C"), dock = c("#A614081A"), dockBorder = c("#66FB923C"), onAccent = c("#04121A")),
        UTheme("نيون سايبر", c("#0D0221"), c("#05010F"), c("#CC1A0838"), c("#E6290F57"), c("#00F5D4"), c("#F5F3FF"), c("#A99CD0"),
            border = c("#3300F5D4"), dock = c("#A605010F"), dockBorder = c("#6600F5D4"), onAccent = c("#04121A")),
        UTheme("غابة", c("#0F2A17"), c("#06130A"), c("#CC123220"), c("#E61B4B2F"), c("#84CC16"), c("#F7FEE7"), c("#9DB59A"),
            border = c("#3384CC16"), dock = c("#A606130A"), dockBorder = c("#6684CC16"), onAccent = c("#04121A")),
        UTheme("جليد أزرق", c("#0B2540"), c("#04101F"), c("#CC0E2E52"), c("#E6164470"), c("#7DD3FC"), c("#F0F9FF"), c("#8FB2CC"),
            border = c("#337DD3FC"), dock = c("#A604101F"), dockBorder = c("#667DD3FC"), onAccent = c("#04121A")),
        UTheme("كربون رمادي", c("#1C1C1E"), c("#0A0A0B"), c("#CC2A2A2D"), c("#E63A3A3E"), c("#E5E7EB"), c("#FAFAFA"), c("#9A9AA0"),
            border = c("#33E5E7EB"), dock = c("#A60A0A0B"), dockBorder = c("#66E5E7EB"), onAccent = c("#04121A")),
        UTheme("فجر برتقالي", c("#3A1F0E"), c("#140A04"), c("#CC4A2810"), c("#E6673A18"), c("#FDBA74"), c("#FFF7ED"), c("#CDA98A"),
            border = c("#33FDBA74"), dock = c("#A6140A04"), dockBorder = c("#66FDBA74"), onAccent = c("#04121A")),
        UTheme("تركوازي عميق", c("#06343A"), c("#021518"), c("#CC0A4850"), c("#E60F6570"), c("#2DD4BF"), c("#F0FDFA"), c("#8DBDB8"),
            border = c("#332DD4BF"), dock = c("#A6021518"), dockBorder = c("#662DD4BF"), onAccent = c("#04121A")),
        UTheme("لؤلؤي فاتح", c("#FAFAF9"), c("#E7E5E4"), c("#E6FFFFFF"), c("#FFFFFFFF"), c("#0F766E"), c("#1C1917"), c("#57534E"),
            border = c("#330F766E"), dock = c("#CCFFFFFF"), dockBorder = c("#660F766E"), onAccent = c("#FFFFFF")),
        UTheme("سماء صافية فاتح", c("#F0F9FF"), c("#BAE6FD"), c("#E6FFFFFF"), c("#FFFFFFFF"), c("#0284C7"), c("#0C2A3D"), c("#3F6577"),
            border = c("#330284C7"), dock = c("#CCFFFFFF"), dockBorder = c("#660284C7"), onAccent = c("#FFFFFF")),
        UTheme("ليموني فاتح", c("#FEFCE8"), c("#FEF08A"), c("#E6FFFFFF"), c("#FFFFFFFF"), c("#A16207"), c("#2A2305"), c("#6B5E20"),
            border = c("#33A16207"), dock = c("#CCFFFFFF"), dockBorder = c("#66A16207"), onAccent = c("#FFFFFF")),
        UTheme("لافندر فاتح", c("#F5F3FF"), c("#DDD6FE"), c("#E6FFFFFF"), c("#FFFFFFFF"), c("#7C3AED"), c("#1E1442"), c("#5B4F85"),
            border = c("#337C3AED"), dock = c("#CCFFFFFF"), dockBorder = c("#667C3AED"), onAccent = c("#FFFFFF")),
        UTheme("خوخي فاتح", c("#FFF7ED"), c("#FED7AA"), c("#E6FFFFFF"), c("#FFFFFFFF"), c("#EA580C"), c("#3B1A05"), c("#7C4A2A"),
            border = c("#33EA580C"), dock = c("#CCFFFFFF"), dockBorder = c("#66EA580C"), onAccent = c("#FFFFFF")),
        UTheme("رمادي فاتح", c("#F3F4F6"), c("#D1D5DB"), c("#E6FFFFFF"), c("#FFFFFFFF"), c("#374151"), c("#111827"), c("#4B5563"),
            border = c("#33374151"), dock = c("#CCFFFFFF"), dockBorder = c("#66374151"), onAccent = c("#FFFFFF"))
    )

    /** 15 لوحة ألوان رئيسية متناسقة؛ تظل سمات الأندية القديمة متاحة في قسم منفصل. */
    val normalIndices: List<Int> by lazy { all.indices.filter { all[it].club == null } }
    val paletteIndices: List<Int> = listOf(0, 1, 2, 3, 4, 5, 6, 10, 11, 12, 13, 14, 18, 22, 25)
    val clubIndices: List<Int> by lazy { all.indices.filter { all[it].club != null } }

    /** السمة فاتحة إن كان نصها داكناً. */
    fun isLight(t: UTheme): Boolean {
        val lum = (Color.red(t.text) * 299 + Color.green(t.text) * 587 + Color.blue(t.text) * 114) / 1000
        return lum < 110
    }

    /** نسخة معتمة بالكامل من السمة لشاشات الإعدادات: لا شفافية تُظهر ما خلفها. */
    fun solid(t: UTheme): UTheme {
        val bottom = Ui.over(t.bgBottom, Color.BLACK)
        return UTheme(t.name, Ui.over(t.bgTop, Color.BLACK), bottom, Ui.over(t.card, bottom), Ui.over(t.cardFocus, bottom),
            t.accent, t.text, t.dim, t.border, Ui.over(t.dock, bottom), t.dockBorder, t.onAccent, t.radiusScale, t.club)
    }

    fun get(i: Int): UTheme = all[if (i < 0 || i >= all.size) 0 else i]
}

object Fonts {
    val names: List<String> = listOf("افتراضي", "خفيف", "متوسط", "مضغوط", "نسخ (سيريف)", "عريض")

    /** الخط الحالي، يُحدَّث عند فتح أي شاشة. */
    var current: Typeface = Typeface.DEFAULT

    fun typeface(i: Int): Typeface = when (i) {
        1 -> Typeface.create("sans-serif-light", Typeface.NORMAL)
        2 -> Typeface.create("sans-serif-medium", Typeface.NORMAL)
        3 -> Typeface.create("sans-serif-condensed", Typeface.NORMAL)
        4 -> Typeface.create("serif", Typeface.NORMAL)
        5 -> Typeface.create("sans-serif-black", Typeface.NORMAL)
        else -> Typeface.DEFAULT
    }
}

class Btn(val text: String, val fn: () -> Unit)

/** Persisted focus accent, intentionally independent from the selected theme. */
object FocusStyle {
    @Volatile var color: Int = Color.rgb(34, 211, 238)
}

/** حركة خفيفة تحترم إعداد المستخدم ومقياس الحركة في نظام Android. */
object Motion {
    fun enabled(context: Context): Boolean {
        val app = context.applicationContext
        val userEnabled = app.getSharedPreferences("uobidah_prefs", Context.MODE_PRIVATE)
            .getBoolean("animations_enabled", true)
        if (!userEnabled) return false
        return try {
            Settings.Global.getFloat(app.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) > 0.01f
        } catch (_: Exception) {
            true
        }
    }

    fun enter(view: View, context: Context) {
        if (!enabled(context)) return
        view.alpha = 0.55f
        view.animate().cancel()
        view.animate().alpha(1f).setDuration(110L).start()
    }
}

object Ui {
    const val MATCH = ViewGroup.LayoutParams.MATCH_PARENT
    const val WRAP = ViewGroup.LayoutParams.WRAP_CONTENT

    /** وحدة مرجعها شاشة 1920 بكسل عرضاً. */
    fun p(ctx: Context, v: Float): Int {
        val dm = ctx.resources.displayMetrics
        val w = if (dm.widthPixels > dm.heightPixels) dm.widthPixels else dm.heightPixels
        return (v * w / 1920f + 0.5f).toInt()
    }

    /** يدمج لوناً شبه شفاف فوق خلفية معتمة ويُرجع لوناً معتماً، فلا يظهر ما خلفه. */
    fun over(c: Int, base: Int): Int {
        val a = Color.alpha(c) / 255f
        return Color.rgb(
            (Color.red(c) * a + Color.red(base) * (1f - a)).toInt(),
            (Color.green(c) * a + Color.green(base) * (1f - a)).toInt(),
            (Color.blue(c) * a + Color.blue(base) * (1f - a)).toInt()
        )
    }

    fun withAlpha(c: Int, a: Int): Int = Color.argb(a, Color.red(c), Color.green(c), Color.blue(c))

    fun round(color: Int, radius: Float, strokeW: Int = 0, strokeC: Int = 0): GradientDrawable {
        val g = GradientDrawable()
        g.shape = GradientDrawable.RECTANGLE
        g.setColor(color)
        g.cornerRadius = radius
        if (strokeW > 0) g.setStroke(strokeW, strokeC)
        return g
    }

    fun lp(w: Int, h: Int, weight: Float = 0f): LinearLayout.LayoutParams =
        LinearLayout.LayoutParams(w, h, weight)

    fun lpm(w: Int, h: Int, weight: Float, start: Int, top: Int, end: Int, bottom: Int): LinearLayout.LayoutParams {
        val l = LinearLayout.LayoutParams(w, h, weight)
        l.marginStart = start
        l.marginEnd = end
        l.topMargin = top
        l.bottomMargin = bottom
        return l
    }

    /** خلفية بطاقة قابلة للتركيز مع إطار متوهج عند التحديد بالريموت. */
    fun focusBg(ctx: Context, t: UTheme, radiusPx: Float, fill: Int): StateListDrawable {
        val focusColor = FocusStyle.color
        val design = CardDesigns.index(ctx)
        val rad = CardDesigns.radius(design, radiusPx)
        val halo = round(withAlpha(focusColor, 82), rad + p(ctx, 5f))
        val inner = round(over(t.cardFocus, t.bgBottom), rad, p(ctx, 4f), focusColor)
        val layers = LayerDrawable(arrayOf<Drawable>(halo, inner))
        val inset = p(ctx, 5f)
        layers.setLayerInset(1, inset, inset, inset, inset)
        val normal = CardDesigns.normal(ctx, t, rad, fill, design)
        val sl = StateListDrawable()
        sl.addState(intArrayOf(android.R.attr.state_focused), layers)
        sl.addState(intArrayOf(), normal)
        return sl
    }

    fun focusable(v: View, t: UTheme, radius: Float = 28f, fill: Int = t.card) {
        val ctx = v.context
        v.isFocusable = true
        v.background = focusBg(ctx, t, p(ctx, radius * t.radiusScale).toFloat(), fill)
        v.setOnFocusChangeListener { view, has ->
            // حركة خفيفة: تكبير بسيط بدون ظل (الظل مكلف على أجهزة التلفزيون الضعيفة).
            if (Motion.enabled(ctx)) {
                val sc = if (has) 1.02f else 1f
                view.animate().cancel()
                view.animate().scaleX(sc).scaleY(sc).setDuration(90L).start()
            } else {
                view.animate().cancel()
                view.scaleX = 1f
                view.scaleY = 1f
            }
        }
    }

    fun staticCard(v: View, t: UTheme, radius: Float = 28f) {
        val ctx = v.context
        val design = CardDesigns.index(ctx)
        val rad = CardDesigns.radius(design, p(ctx, radius * t.radiusScale).toFloat())
        v.background = CardDesigns.normal(ctx, t, rad, t.card, design)
    }

    fun label(ctx: Context, text: String, size: Float, color: Int, bold: Boolean = false): TextView {
        val tv = IconTextView(ctx)
        tv.setTextSize(TypedValue.COMPLEX_UNIT_PX, p(ctx, size).toFloat())
        tv.setTextColor(color)
        tv.text = text
        tv.setTextColor(color)
        tv.typeface = if (bold) Typeface.create(Fonts.current, Typeface.BOLD) else Fonts.current
        tv.includeFontPadding = false
        return tv
    }

    fun button(ctx: Context, t: UTheme, text: String, size: Float = 30f, onClick: () -> Unit): TextView {
        val b = label(ctx, text, size, t.text, true)
        b.gravity = Gravity.CENTER
        b.setPadding(p(ctx, 34f), p(ctx, 18f), p(ctx, 34f), p(ctx, 18f))
        focusable(b, t, 26f, t.card)
        b.isClickable = true
        b.setOnClickListener { onClick() }
        return b
    }

    /** زر اختيار (مظلَّل إن كان محدداً). */
    fun styleChip(tv: TextView, t: UTheme, selected: Boolean) {
        focusable(tv, t, 26f, if (selected) withAlpha(t.accent, 150) else t.card)
        tv.setTextColor(if (selected) t.onAccent else t.text)
    }

    fun chip(ctx: Context, t: UTheme, text: String, selected: Boolean, size: Float = 28f, onClick: () -> Unit): TextView {
        val b = button(ctx, t, text, size, onClick)
        styleChip(b, t, selected)
        return b
    }

    fun edit(ctx: Context, t: UTheme, hintText: String): EditText {
        val e = EditText(ctx)
        e.hint = hintText
        e.setHintTextColor(t.dim)
        e.setTextColor(t.text)
        e.setTextSize(TypedValue.COMPLEX_UNIT_PX, p(ctx, 30f).toFloat())
        e.typeface = Fonts.current
        e.setSingleLine(true)
        e.imeOptions = EditorInfo.IME_ACTION_SEARCH
        e.inputType = InputType.TYPE_CLASS_TEXT
        e.setPadding(p(ctx, 26f), p(ctx, 16f), p(ctx, 26f), p(ctx, 16f))
        e.gravity = Gravity.CENTER_VERTICAL or Gravity.START
        focusable(e, t, 22f, withAlpha(Color.BLACK, 110))
        return e
    }

    fun ellipsize(tv: TextView) {
        tv.maxLines = 1
        tv.ellipsize = TextUtils.TruncateAt.END
    }

    fun selector(ctx: Context, t: UTheme): Drawable =
        round(withAlpha(t.accent, 90), p(ctx, 18f).toFloat(), p(ctx, 3f), t.accent)

    fun listAdapter(ctx: Context, t: UTheme): ArrayAdapter<String> {
        return object : ArrayAdapter<String>(ctx, android.R.layout.simple_list_item_1, ArrayList<String>()) {
            override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                val v = super.getView(position, convertView, parent) as TextView
                v.setTextColor(t.text)
                v.setTextSize(TypedValue.COMPLEX_UNIT_PX, p(ctx, 30f).toFloat())
                v.typeface = Fonts.current
                v.minHeight = 0
                v.setPadding(p(ctx, 24f), p(ctx, 16f), p(ctx, 24f), p(ctx, 16f))
                v.gravity = Gravity.CENTER_VERTICAL or Gravity.START
                return v
            }
        }
    }

    /** نافذة منبثقة بتصميم اللانشر. */
    fun modal(act: Activity, t: UTheme, title: String, content: View, vararg buttons: Btn): Dialog {
        val d = Dialog(act, android.R.style.Theme_DeviceDefault_Dialog_NoActionBar)
        val panel = LinearLayout(act)
        panel.orientation = LinearLayout.VERTICAL
        panel.layoutDirection = View.LAYOUT_DIRECTION_RTL
        panel.setPadding(p(act, 44f), p(act, 34f), p(act, 44f), p(act, 30f))
        panel.background = round(t.bgBottom, p(act, 36f).toFloat(), p(act, 3f), t.accent)
        panel.addView(label(act, title, 38f, t.accent, true))
        panel.addView(content, lpm(MATCH, WRAP, 0f, 0, p(act, 20f), 0, p(act, 22f)))
        val row = LinearLayout(act)
        row.orientation = LinearLayout.HORIZONTAL
        for (b in buttons) {
            val btn = button(act, t, b.text, 28f) {
                d.dismiss()
                b.fn()
            }
            row.addView(btn, lpm(WRAP, WRAP, 0f, 0, 0, p(act, 16f), 0))
        }
        panel.addView(row)
        d.setContentView(panel)
        val w = d.window
        if (w != null) {
            w.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            w.setLayout(p(act, 1200f), WRAP)
        }
        d.show()
        return d
    }
}
