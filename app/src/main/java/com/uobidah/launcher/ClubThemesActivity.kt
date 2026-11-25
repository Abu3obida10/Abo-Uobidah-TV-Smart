package com.uobidah.launcher

import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.widget.ImageView
import android.widget.LinearLayout

/** صفحة ثيمات الأندية بشعارات كاملة (FIT_CENTER بلا قص). تطبيق فوري مع إمكانية الرجوع للثيم الافتراضي. */
class ClubThemesActivity : SettingsBase() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setupScreen("♛ ثيمات الأندية")
        val clubs = Themes.all.withIndex().filter { it.value.club != null }
        if (clubs.isEmpty()) {
            note("لا توجد ثيمات أندية في هذا الإصدار.")
            finishScreen()
            return
        }
        val current = Themes.get(prefs.themeIndex)
        note("الثيم الحالي: " + (current.club?.let { it.emoji + " " + it.ar } ?: current.name) +
            ". اختر نادياً لتطبيق شعاره وألوانه وخلفيته فورًا.")
        section("اختر النادي")
        for (entry in clubs) {
            val club = entry.value.club
            val selected = prefs.themeIndex == entry.index
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(px(20f), px(14f), px(20f), px(14f))
                background = Ui.round(Color.argb(105, 17, 34, 59), px(17f).toFloat(), px(1f), Color.argb(35, 255, 255, 255))
                isFocusable = true
                isClickable = true
            }
            val res = club?.let { ClubLogos.res(it.art) } ?: 0
            if (res != 0) {
                val iv = ImageView(this).apply {
                    setImageResource(res)
                    scaleType = ImageView.ScaleType.FIT_CENTER
                }
                row.addView(iv, Ui.lpm(px(110f), px(110f), 0f, 0, 0, px(20f), 0))
            }
            val text = (if (selected) "✓ " else "") + (club?.ar ?: entry.value.name) +
                (if (club != null && club.motto.isNotBlank()) "\n" + club.motto else "")
            val label = Ui.label(this, text, 26f, ut.text, true)
            label.maxLines = 3
            row.addView(label, Ui.lp(0, Ui.WRAP, 1f))
            Ui.focusable(row, ut, 17f, Color.argb(105, 17, 34, 59))
            row.setOnClickListener {
                prefs.setThemeWithHistory(entry.index)
                toast("تم تطبيق ثيم " + (club?.ar ?: entry.value.name))
                rebuild("")
            }
            (activeSection ?: body).addView(row, Ui.lpm(Ui.MATCH, Ui.WRAP, 0f, 0, px(6f), 0, px(6f)))
        }
        section("العودة")
        action("↩ الرجوع للسمة السابقة") {
            val prev = prefs.previousThemeIndex
            if (prev < 0 || prev >= Themes.all.size) {
                toast("لا توجد سمة سابقة")
            } else {
                prefs.setThemeWithHistory(prev)
                toast("تم الرجوع للسمة السابقة")
                rebuild("")
            }
        }
        action("↩ الرجوع للثيم الافتراضي (ليل أزرق فاخر)") {
            prefs.setThemeWithHistory(0)
            toast("تمت استعادة الثيم الافتراضي")
            rebuild("")
        }
        finishScreen()
    }
}
