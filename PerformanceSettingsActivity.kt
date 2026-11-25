package com.uobidah.launcher

import android.os.Bundle
import com.uobidah.launcher.core.PerfLimits

/** إعدادات الأداء والإذاعة: كل قيمة تُحفظ فورًا وتقرؤها الخدمة والشاشة الرئيسية فعليًا. */
class PerformanceSettingsActivity : SettingsBase() {

    private fun picker(tag: String, title: String, unit: String, values: List<Int>, current: Int, set: (Int) -> Unit) {
        section(title)
        chips(tag, values.map { "$it $unit" }, values.indexOf(current).coerceAtLeast(0)) {
            set(values[it])
            rebuild("$tag:$it")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setupScreen("⚡ الأداء والإذاعة")
        note("قيم أقل = تحديث أسرع واستهلاك أكبر. تُطبَّق الفترات على الخدمة عند إعادة تشغيلها (إعادة تشغيل الجهاز أو اللانشر).")

        picker("poll", "⏱️ فحص الأذان والتنبيهات في الخلفية", "ثانية", PerfLimits.POLL_SECONDS, prefs.servicePollSeconds) { prefs.servicePollSeconds = it }
        picker("mcheck", "⚽ فحص المباريات والأهداف", "ثانية", PerfLimits.MATCH_CHECK_SECONDS, prefs.matchCheckSeconds) { prefs.matchCheckSeconds = it }
        picker("mcache", "🗂️ مدة حفظ جدول المباريات قبل إعادة جلبه", "دقيقة", PerfLimits.MATCH_CACHE_MINUTES, prefs.matchCacheMinutes) { prefs.matchCacheMinutes = it }
        picker("favref", "⭐ تحديث بطاقات المباريات في الرئيسية", "دقيقة", PerfLimits.FAVORITES_REFRESH_MINUTES, prefs.favoritesRefreshMinutes) { prefs.favoritesRefreshMinutes = it }

        section("🖼️ الذاكرة والخلفية")
        toggle("خلفية اقتصادية (960×540) لتقليل استهلاك الذاكرة", { prefs.lowMemoryBackground }, { prefs.lowMemoryBackground = it })
        toggle("تفعيل حركات الانتقال والتركيز السلسة", { prefs.animationsEnabled }, { prefs.animationsEnabled = it })
        note("تحترم الحركة أيضًا إعداد تقليل الحركة في Android. إيقافها يقلل المؤثرات البصرية على الأجهزة الضعيفة.")
        toggle("تحديث الخلفية تلقائيًا كل يوم", { prefs.autoWallpaper }, { prefs.autoWallpaper = it })
        action("🔎 فحص الذاكرة المتاحة الآن") {
            val app = applicationContext
            Net.bg {
                val mb = SystemTools.availableMemoryMb(app)
                Net.ui { toast(if (mb > 0) "الذاكرة المتاحة حاليًا: $mb م.ب" else "تعذر قراءة حالة الذاكرة") }
            }
        }

        section("📻 الإذاعة")
        toggle("عرض المحطات المميزة فقط", { prefs.radioFeaturedOnly }, { prefs.radioFeaturedOnly = it })

        section("↩️ استعادة")
        action("استعادة قيم الأداء الافتراضية") {
            prefs.servicePollSeconds = 60; prefs.matchCheckSeconds = 600; prefs.matchCacheMinutes = 30
            prefs.favoritesRefreshMinutes = 10; prefs.lowMemoryBackground = true
            prefs.animationsEnabled = true
            toast("تمت استعادة قيم الأداء")
            rebuild("")
        }
        finishScreen()
    }
}
