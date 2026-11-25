package com.uobidah.launcher

import android.content.Intent
import android.os.Bundle

/** لوحة التحكم التفصيلية بعناصر الشاشة الرئيسية. كل مفتاح مرتبط بعنصر فعلي ويُحفظ فورًا. */
class HomeCustomizationActivity : SettingsBase() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setupScreen("🧩 تخصيص الشاشة الرئيسية")
        note("اختر ما يظهر على الشاشة. التغييرات تُحفظ تلقائيًا وتُطبّق عند الرجوع للرئيسية.")

        section("🪪 الهوية والعناصر الثابتة")
        toggle("إظهار شعار واسم عُبيدالله لانشر", { prefs.showBranding }, { prefs.showBranding = it })
        if (prefs.layoutIndex != 4) {
            toggle("إظهار اختصار مداخل التلفزيون في التطبيقات المثبتة", { prefs.showTvInputTile }, { prefs.showTvInputTile = it })
            toggle("إظهار اختصار مباريات النادي حسب الثيم", { prefs.showClubShortcutTile }, { prefs.showClubShortcutTile = it })
        } else {
            note("في تصميم التلفزيون المرجعي يظل صف التطبيقات مخصصًا للتطبيقات المثبتة وزر إضافة تطبيق. مداخل التلفزيون من إعدادات التلفزيون، ومباريات النادي من القائمة الجانبية.")
        }

        section("🕰️ البطاقات الرئيسية")
        toggle("إظهار بطاقة الساعة والتاريخ", { prefs.showClockCard }, { prefs.showClockCard = it })
        toggle("إظهار التاريخ الهجري", { prefs.showHijriDate }, { prefs.showHijriDate = it })
        toggle("استخدام نظام 24 ساعة", { prefs.use24HourClock }, { prefs.use24HourClock = it })
        toggle("إظهار بطاقة الطقس", { prefs.showWeatherCard }, { prefs.showWeatherCard = it })
        section("🌦️ تحديث الطقس")
        val weatherIntervals = listOf(5, 10, 15, 30, 45, 60)
        chips("weather_refresh", weatherIntervals.map { "$it دقيقة" }, weatherIntervals.indexOf(prefs.weatherRefreshMinutes).coerceAtLeast(0)) {
            prefs.weatherRefreshMinutes = weatherIntervals[it]
            rebuild("weather_refresh:$it")
        }
        toggle("إظهار بطاقة مواقيت الصلاة والعدّ التنازلي", { prefs.showPrayerCard }, { prefs.showPrayerCard = it })
        toggle("فتح إعدادات الصلاة عند الضغط على بطاقة الصلاة (بدلاً من التبديل السريع للأذان)", { prefs.prayerCardOpensSettings }, { prefs.prayerCardOpensSettings = it })
        toggle("إظهار قسم التطبيقات المثبتة", { prefs.showPinnedApps }, { prefs.showPinnedApps = it })
        toggle("إظهار مباريات الأندية المفضلة", { prefs.showMatchFavorites }, { prefs.showMatchFavorites = it })

        section("🔝 أزرار الشريط العلوي")
        toggle("زر تسريع/تنظيف كاش اللانشر", { prefs.showBoosterButton }, { prefs.showBoosterButton = it })
        toggle("زر البحث عن التطبيقات", { prefs.showSearchButton }, { prefs.showSearchButton = it })
        toggle("زر الإشعارات", { prefs.showNotificationButton }, { prefs.showNotificationButton = it })
        toggle("زر الإعدادات", { prefs.showSettingsButton }, { prefs.showSettingsButton = it })
        if (prefs.layoutIndex != 4) {
            note("في التخطيطات الأخرى تظهر أيضًا اختصارات Wi-Fi وHDMI وإذاعات القرآن.")
            toggle("زر حالة Wi-Fi", { prefs.showWifiButton }, { prefs.showWifiButton = it })
            toggle("زر HDMI ومداخل التلفزيون", { prefs.showHdmiButton }, { prefs.showHdmiButton = it })
            toggle("زر إذاعات القرآن", { prefs.showRadioButton }, { prefs.showRadioButton = it })
        } else {
            note("القائمة الجانبية هي وسيلة التنقل الوحيدة. الشريط العلوي مخصص لاختصارات الاتصال والبحث والتنبيهات والإعدادات.")
        }

        section("📌 التطبيقات المفضلة")
        action("⭐ إدارة التطبيقات المفضلة وترتيبها وأسمائها") {
            startActivity(Intent(this, FavoriteAppsSettingsActivity::class.java))
        }
        toggle("إظهار أسماء التطبيقات أسفل الأيقونات", { prefs.favoriteAppShowNames }, { prefs.favoriteAppShowNames = it })
        note("عرض الأيقونات: ${prefs.favoriteAppIconSize}dp — عرض بطاقة التطبيق: ${prefs.favoriteAppTileWidth}dp")
        val iconSizes = listOf(56, 68, 80, 92, 104, 120, 136)
        chips("fav_icon_size", iconSizes.map { "$it" }, iconSizes.indexOf(prefs.favoriteAppIconSize).coerceAtLeast(0)) {
            prefs.favoriteAppIconSize = iconSizes[it]
            rebuild("fav_icon_size:$it")
        }
        val widths = listOf(160, 180, 200, 230, 260, 290, 320)
        chips("fav_width", widths.map { "$it" }, widths.indexOf(prefs.favoriteAppTileWidth).coerceAtLeast(0)) {
            prefs.favoriteAppTileWidth = widths[it]
            rebuild("fav_width:$it")
        }

        section("↩️ استعادة")
        action("استعادة جميع عناصر الشاشة الرئيسية للوضع الافتراضي") {
            prefs.showClockCard = true; prefs.showWeatherCard = true; prefs.showPrayerCard = true
            prefs.showPinnedApps = true; prefs.showMatchFavorites = true; prefs.showHomeDock = false
            prefs.showWifiButton = true; prefs.showHdmiButton = true; prefs.showBoosterButton = true
            prefs.showRadioButton = true; prefs.showSearchButton = true; prefs.showNotificationButton = true
            prefs.showSettingsButton = true; prefs.showBranding = true; prefs.showTvInputTile = true
            prefs.showClubShortcutTile = true; prefs.prayerCardOpensSettings = true
            prefs.dockMatches = false; prefs.dockApps = false
            prefs.dockRadio = false; prefs.dockVideo = false; prefs.dockWallpapers = false; prefs.dockSettings = false
            prefs.dockGames = false; prefs.dockKids = false
            prefs.favoriteAppShowNames = true; prefs.favoriteAppIconSize = 92; prefs.favoriteAppTileWidth = 230
            prefs.showHijriDate = true; prefs.use24HourClock = false; prefs.weatherRefreshMinutes = 15
            toast("تمت استعادة إعدادات الشاشة الرئيسية")
            rebuild("")
        }
        finishScreen()
    }
}
