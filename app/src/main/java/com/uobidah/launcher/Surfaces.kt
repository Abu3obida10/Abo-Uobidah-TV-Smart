package com.uobidah.launcher

import android.graphics.Color
import com.uobidah.launcher.core.Contrast

/** ألوان أسطح شاشات الإعدادات: داكنة للسمات الداكنة وفاتحة للسمات الفاتحة (بدل ألوان داكنة ثابتة). */
object Surfaces {
    private fun light(t: UTheme) = Themes.isLight(t)

    fun top(t: UTheme): Int =
        if (light(t)) Ui.over(Color.argb(240, 255, 255, 255), t.bgBottom) else Ui.over(Color.argb(155, 5, 13, 27), t.bgBottom)

    fun topBorder(t: UTheme): Int =
        if (light(t)) Ui.over(Ui.withAlpha(t.accent, 90), t.bgBottom) else Ui.over(Color.argb(70, 110, 160, 255), t.bgBottom)

    fun sectionA(t: UTheme): Int =
        if (light(t)) Ui.over(Color.argb(245, 255, 255, 255), t.bgBottom) else Ui.over(Color.argb(220, 9, 22, 43), t.bgBottom)

    fun sectionB(t: UTheme): Int =
        if (light(t)) Ui.over(Color.argb(230, 247, 250, 254), t.bgBottom) else Ui.over(Color.argb(185, 5, 11, 24), t.bgBottom)

    fun row(t: UTheme): Int =
        if (light(t)) Ui.over(Color.argb(255, 236, 241, 248), t.bgBottom) else Ui.over(Color.argb(105, 17, 34, 59), t.bgBottom)

    fun rowBorder(t: UTheme): Int =
        if (light(t)) Ui.over(Ui.withAlpha(t.accent, 55), t.bgBottom) else Ui.over(Color.argb(35, 255, 255, 255), t.bgBottom)

    // ---- أسطح مركز التحكم: كلها مشتقة من ألوان السمة (فاتحة/داكنة) بدل الأزرق الثابت ----
    fun panel(t: UTheme): Int =
        if (light(t)) Ui.over(Color.argb(238, 255, 255, 255), t.bgBottom) else Ui.over(Color.argb(150, 0, 0, 0), t.bgBottom)

    fun edge(t: UTheme): Int = Ui.over(Ui.withAlpha(t.accent, if (light(t)) 110 else 95), t.bgBottom)

    fun idleItem(t: UTheme): Int =
        if (light(t)) Ui.over(Ui.withAlpha(t.accent, 22), panel(t)) else Ui.over(Color.argb(24, 255, 255, 255), panel(t))

    /** خلفية العنصر المحدد: لون التمييز مع ضمان تباين كافٍ لنصه (onAccent). */
    fun activeItem(t: UTheme): Int =
        Contrast.fitBackground(Ui.over(Ui.withAlpha(t.accent, 240), t.bgBottom), t.onAccent)

    /** لون التمييز كنص أو أيقونة فوق خلفية معيّنة، مع تغميق/تفتيح عند الحاجة لتفادي الباهت. */
    fun accentOn(t: UTheme, bg: Int): Int = Contrast.ensure(t.accent, bg)

    /** عنوان القسم وأسهمه فوق بطاقة القسم. */
    fun accentText(t: UTheme): Int = accentOn(t, sectionA(t))

    /** نصوص قابلة للقراءة بعد ضبط التباين على خلفية العنصر الفعلية. */
    fun primaryText(t: UTheme, bg: Int): Int = Contrast.ensure(t.text, bg)
    fun secondaryText(t: UTheme, bg: Int): Int = Contrast.ensure(t.dim, bg)

    /** حبتا «مفعّل/متوقف»: ألوان معتمة ثابتة بنص أبيض واضح في السمات الفاتحة والداكنة. */
    val pillOn: Int = Color.rgb(4, 120, 87)
    val pillOff: Int = Color.rgb(71, 85, 105)

    fun heroA(t: UTheme): Int = Ui.over(Ui.withAlpha(t.accent, if (light(t)) 70 else 85), t.bgBottom)
    fun heroB(t: UTheme): Int = Ui.over(Ui.withAlpha(t.accent, if (light(t)) 28 else 30), t.bgBottom)

    fun note(t: UTheme): Int =
        if (light(t)) Ui.over(Ui.withAlpha(t.accent, 38), t.bgBottom) else Ui.over(Color.argb(65, 65, 130, 220), t.bgBottom)
}

/** عشرة تخطيطات لشاشات الإعدادات والقوائم (SettingsBase): عرض المحتوى، الكثافة، شكل رأس القسم، الفتح الكامل. */
class ScreenLayoutSpec(
    val nameAr: String,
    val descAr: String,
    val sideDp: Float,
    val gapDp: Float,
    val radiusDp: Float,
    /** 0 شريط ملوّن، 1 شارة، 2 خط سفلي، 3 عنوان فقط */
    val headStyle: Int,
    val openAll: Boolean
)

object ScreenLayouts {
    val all: List<ScreenLayoutSpec> = listOf(
        ScreenLayoutSpec("قياسي", "عرض كامل وأقسام قابلة للطي (الافتراضي)", 46f, 12f, 25f, 0, false),
        ScreenLayoutSpec("مريح", "مسافات أكبر بين الأقسام", 46f, 24f, 25f, 0, false),
        ScreenLayoutSpec("مضغوط", "مسافات صغيرة لعرض أكثر في الشاشة", 30f, 6f, 18f, 0, false),
        ScreenLayoutSpec("عمود مركزي", "المحتوى في عمود وسط الشاشة", 300f, 12f, 25f, 0, false),
        ScreenLayoutSpec("عمود ضيق", "عمود ضيق مع عناوين على شكل شارة", 460f, 14f, 30f, 1, false),
        ScreenLayoutSpec("شارات", "عنوان كل قسم داخل شارة ملوّنة", 46f, 12f, 25f, 1, false),
        ScreenLayoutSpec("خط سفلي", "عنوان القسم يعلوه خط ملوّن", 46f, 12f, 20f, 2, false),
        ScreenLayoutSpec("بسيط", "عناوين فقط بزوايا أقل استدارة", 46f, 12f, 14f, 3, false),
        ScreenLayoutSpec("كل الأقسام مفتوحة", "كل الأقسام تظهر مفتوحة معًا", 46f, 10f, 25f, 0, true),
        ScreenLayoutSpec("بطاقات كبيرة", "بطاقات عريضة بزوايا كبيرة", 70f, 20f, 42f, 1, false)
    )

    fun get(i: Int): ScreenLayoutSpec = all[i.coerceIn(0, all.size - 1)]
}
