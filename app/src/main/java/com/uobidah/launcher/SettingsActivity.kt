package com.uobidah.launcher

import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.uobidah.launcher.core.Contrast

/** لوحة إعدادات احترافية موحدة، مقسمة إلى فئات واضحة ومناسبة للتحكم بالريموت. */
class SettingsActivity : BaseActivity() {
    override val restoreGenericViewState: Boolean get() = false

    private data class Category(val id: String, val icon: String, val title: String, val subtitle: String)

    private val categories = listOf(
        Category("home", "⌂", "الرئيسية", "البطاقات والتطبيقات والتخطيط"),
        Category("appearance", "✦", "المظهر", "السمات والخلفيات والحركة"),
        Category("prayer", "۞", "الوقت والصلاة", "المدينة والمواقيت والأذان"),
        Category("matches", "⚽", "المباريات", "الأندية والمصادر والتنبيهات"),
        Category("alerts", "♧", "الإشعارات والجزيرة", "الإشعارات والأصوات والجزيرة"),
        Category("apps", "▦", "التطبيقات والمحتوى", "التطبيقات والتلفزيون والراديو والقرآن"),
        Category("permissions", "✓", "الصلاحيات والتفعيل", "الشاشة الرئيسية والأذونات"),
        Category("system", "⚙", "النظام", "الأداء والنسخ الاحتياطي"),
        Category("about", "ⓘ", "حول التطبيق", "الإصدار والمعلومات")
    )

    private lateinit var content: LinearLayout
    private lateinit var menu: LinearLayout
    private lateinit var menuScroll: ScrollView
    private lateinit var contentScroll: ScrollView
    private var selectedId = "home"
    private val categoryHistory = ArrayList<String>()
    private val categoryScrollKey get() = "settings_hub_scroll_$selectedId"
    private val menuScrollKey = "settings_hub_menu_scroll"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        selectedId = (savedInstanceState?.getString("settings_hub_category")
            ?: intent.getStringExtra("category")
            ?: prefs.sp.getString("settings_hub_category", "home") ?: "home")
            .takeIf { id -> categories.any { it.id == id } } ?: "home"
        savedInstanceState?.getStringArrayList("settings_hub_history")?.let { saved ->
            categoryHistory.clear()
            categoryHistory.addAll(saved.filter { id -> categories.any { it.id == id } })
        }
        buildShell()
        renderCategory(selectedId)
        restoreCategoryScroll()
        restoreMenuScroll()
    }

    override fun onRestoreInstanceState(savedInstanceState: Bundle) {
        super.onRestoreInstanceState(savedInstanceState)
        restoreCategoryScroll()
        restoreMenuScroll()
    }

    private fun restoreMenuScroll() {
        if (!::menuScroll.isInitialized || !::menu.isInitialized) return
        val requested = prefs.sp.getInt(menuScrollKey, 0).coerceAtLeast(0)
        menuScroll.post {
            val maxY = (menu.height - menuScroll.height).coerceAtLeast(0)
            menuScroll.scrollTo(0, requested.coerceAtMost(maxY))
        }
    }

    private fun buildShell() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutDirection = View.LAYOUT_DIRECTION_RTL
            background = bgGradient()
            setPadding(px(24f), px(20f), px(24f), px(20f))
            clipChildren = false
            clipToPadding = false
        }

        // شريط الفئات على اليمين، ثابت أثناء التمرير.
        val side = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(px(18f), px(18f), px(18f), px(18f))
            background = Ui.round(Surfaces.panel(ut), px(28f).toFloat(), px(2f), Surfaces.edge(ut))
        }
        val brand = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER; setPadding(0, 0, 0, px(18f)) }
        val logo = Ui.label(this, "A", 58f, Contrast.ensure(ut.onAccent, ut.accent), true).apply {
            gravity = Gravity.CENTER
            background = GradientDrawable(GradientDrawable.Orientation.TL_BR, intArrayOf(ut.accent, Ui.over(Ui.withAlpha(Color.BLACK, 70), ut.accent))).apply {
                shape = GradientDrawable.OVAL
                setStroke(px(2f), Ui.withAlpha(ut.text, 120))
            }
        }
        brand.addView(logo, Ui.lp(px(88f), px(88f)))
        brand.addView(Ui.label(this, "Abo Uobidah TV", 25f, ut.text, true), Ui.lpm(Ui.WRAP, Ui.WRAP, 0f, 0, px(10f), 0, 0))
        brand.addView(Ui.label(this, "مركز التحكم", 18f, Surfaces.secondaryText(ut, Surfaces.panel(ut))), Ui.lpm(Ui.WRAP, Ui.WRAP, 0f, 0, px(3f), 0, 0))
        side.addView(brand)

        menuScroll = ScrollView(this).apply { isVerticalScrollBarEnabled = false }
        menu = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        menuScroll.addView(menu, ViewGroup.LayoutParams(Ui.MATCH, Ui.WRAP))
        side.addView(menuScroll, Ui.lp(Ui.MATCH, 0, 1f))
        val back = Ui.button(this, ut, "‹  رجوع", 21f) { onBackPressed() }
        side.addView(back, Ui.lpm(Ui.MATCH, Ui.WRAP, 0f, 0, px(12f), 0, 0))
        root.addView(side, Ui.lpm(px(320f), Ui.MATCH, 0f, 0, 0, px(18f), 0))

        val right = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; clipChildren = true; clipToPadding = false }
        val heading = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(px(28f), px(20f), px(28f), px(20f))
            background = Ui.round(Surfaces.panel(ut), px(25f).toFloat(), px(2f), Surfaces.edge(ut))
        }
        val titleBox = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        titleBox.addView(Ui.label(this, "الإعدادات", 45f, Surfaces.primaryText(ut, Surfaces.panel(ut)), true))
        titleBox.addView(Ui.label(this, "إدارة متكاملة لكل وظائف Abo Uobidah TV", 21f, Surfaces.secondaryText(ut, Surfaces.panel(ut))))
        titleBox.getChildAt(1)?.let { (it as? TextView)?.setTextColor(Surfaces.secondaryText(ut, Surfaces.panel(ut))) }
        heading.addView(titleBox, Ui.lp(0, Ui.WRAP, 1f))
        val version = runCatching { packageManager.getPackageInfo(packageName, 0).versionName ?: "" }.getOrDefault("")
        val pillBg = Ui.over(Ui.withAlpha(ut.accent, 45), Surfaces.panel(ut))
        val pill = Ui.label(this, "الإصدار $version", 19f, Contrast.ensure(ut.accent, pillBg), true).apply {
            gravity = Gravity.CENTER
            setPadding(px(18f), px(12f), px(18f), px(12f))
            background = Ui.round(Ui.withAlpha(ut.accent, 45), px(40f).toFloat(), px(1f), Ui.withAlpha(ut.accent, 140))
        }
        heading.addView(pill)
        right.addView(heading, Ui.lpm(Ui.MATCH, Ui.WRAP, 0f, 0, 0, 0, px(16f)))

        contentScroll = ScrollView(this).apply { isVerticalScrollBarEnabled = false; clipToPadding = true; clipChildren = true }
        content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(px(3f), px(3f), px(8f), px(24f))
            clipChildren = false
            clipToPadding = false
        }
        contentScroll.addView(content, ViewGroup.LayoutParams(Ui.MATCH, Ui.WRAP))
        right.addView(contentScroll, Ui.lp(Ui.MATCH, 0, 1f))
        root.addView(right, Ui.lp(0, Ui.MATCH, 1f))
        show(root)
        rebuildMenu()
    }

    private fun saveHubState() {
        if (!::contentScroll.isInitialized || !::menuScroll.isInitialized) return
        prefs.sp.edit()
            .putInt(categoryScrollKey, contentScroll.scrollY.coerceAtLeast(0))
            .putInt(menuScrollKey, menuScroll.scrollY.coerceAtLeast(0))
            .putString("settings_hub_category", selectedId)
            .apply()
    }

    /** يستعيد موضع فئة الإعدادات الحالية فقط، مع منع Android من تثبيت قيمة خارج مدى المحتوى. */
    private fun restoreCategoryScroll() {
        if (!::contentScroll.isInitialized) return
        val requested = prefs.sp.getInt(categoryScrollKey, 0).coerceAtLeast(0)
        contentScroll.post {
            val maxY = (content.height - contentScroll.height).coerceAtLeast(0)
            contentScroll.scrollTo(0, requested.coerceAtMost(maxY))
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putString("settings_hub_category", selectedId)
        outState.putStringArrayList("settings_hub_history", ArrayList(categoryHistory))
        saveHubState()
        super.onSaveInstanceState(outState)
    }

    /** الرجوع بين فئات لوحة الإعدادات أولاً، ثم الرجوع إلى النشاط الذي فتح الإعدادات. */
    @Suppress("OVERRIDE_DEPRECATION", "DEPRECATION")
    override fun onBackPressed() {
        if (categoryHistory.isNotEmpty()) {
            saveHubState()
            selectedId = categoryHistory.removeAt(categoryHistory.lastIndex)
            prefs.sp.edit().putString("settings_hub_category", selectedId).apply()
            rebuildMenu(restoreSelectedFocus = true)
            renderCategory(selectedId)
            restoreCategoryScroll()
        } else {
            super.onBackPressed()
        }
    }

    override fun onPause() {
        saveHubState()
        super.onPause()
    }

    /** كان recreate() بعد تغيير المدينة/السمة يعيد المستخدم إلى تبويب «الرئيسية»؛ نحفظ التبويب الحالي أولاً. */
    override fun recreate() {
        saveHubState()
        intent.putExtra("category", selectedId)
        super.recreate()
    }

    private fun rebuildMenu(restoreSelectedFocus: Boolean = false) {
        // إزالة صفوف القائمة وإعادة إنشائها كانت تجعل ScrollView يقفز إلى البداية/النهاية.
        val requestedMenuY = if (::menuScroll.isInitialized) menuScroll.scrollY.coerceAtLeast(0)
            else prefs.sp.getInt(menuScrollKey, 0).coerceAtLeast(0)
        menu.removeAllViews()
        categories.forEach { category ->
            val active = selectedId == category.id
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(px(14f), px(14f), px(14f), px(14f))
                background = if (active) GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,
                    intArrayOf(Surfaces.activeItem(ut), Ui.over(Ui.withAlpha(if (com.uobidah.launcher.core.Contrast.luminance(ut.onAccent) > 0.5) Color.BLACK else Color.WHITE, 45), Surfaces.activeItem(ut)))).apply {
                    cornerRadius = px(17f).toFloat(); setStroke(px(2f), Ui.withAlpha(ut.text, 90))
                } else Ui.round(Surfaces.idleItem(ut), px(17f).toFloat())
            }
            val icon = Ui.label(this, category.icon, 32f, if (active) ut.onAccent else Surfaces.accentOn(ut, Surfaces.idleItem(ut)), true).apply { gravity = Gravity.CENTER }
            row.addView(icon, Ui.lpm(px(48f), px(48f), 0f, 0, 0, px(12f), 0))
            val labels = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
            labels.addView(Ui.label(this, category.title, 22f,
                if (active) Contrast.ensure(ut.onAccent, Surfaces.activeItem(ut))
                else Surfaces.primaryText(ut, Surfaces.idleItem(ut)), true))
            labels.addView(Ui.label(this, category.subtitle, 16f,
                if (active) Contrast.ensure(Ui.withAlpha(ut.onAccent, 225), Surfaces.activeItem(ut))
                else Surfaces.secondaryText(ut, Surfaces.idleItem(ut))))
            row.addView(labels, Ui.lp(0, Ui.WRAP, 1f))
            Ui.focusable(row, ut, 17f, if (active) Surfaces.activeItem(ut) else Surfaces.idleItem(ut))
            row.isClickable = true
            row.tag = "settings_category:${category.id}"
            row.setOnClickListener {
                if (selectedId == category.id) return@setOnClickListener
                val menuHadFocus = row.hasFocus()
                saveHubState()
                categoryHistory.add(selectedId)
                selectedId = category.id
                prefs.sp.edit().putString("settings_hub_category", selectedId).apply()
                rebuildMenu(restoreSelectedFocus = menuHadFocus)
                renderCategory(category.id)
                restoreCategoryScroll()
            }
            menu.addView(row, Ui.lpm(Ui.MATCH, Ui.WRAP, 0f, 0, px(5f), 0, px(5f)))
        }
        menuScroll.post {
            val maxY = (menu.height - menuScroll.height).coerceAtLeast(0)
            menuScroll.scrollTo(0, requestedMenuY.coerceAtMost(maxY))
            if (restoreSelectedFocus) menu.findViewWithTag<View>("settings_category:$selectedId")?.requestFocus()
        }
    }

    private fun renderCategory(id: String) {
        // لا نسمح لموضع التمرير الخاص بالفئة السابقة بالظهور أثناء بناء الفئة الجديدة.
        contentScroll.scrollTo(0, 0)
        content.removeAllViews()
        currentGroup = null
        groups.clear()
        val category = categories.firstOrNull { it.id == id } ?: categories.first()
        val hero = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(px(30f), px(25f), px(30f), px(25f))
            background = GradientDrawable(GradientDrawable.Orientation.TL_BR,
                intArrayOf(Surfaces.heroA(ut), Surfaces.heroB(ut))).apply {
                cornerRadius = px(24f).toFloat()
                setStroke(px(2f), Ui.withAlpha(ut.accent, 105))
            }
        }
        hero.addView(Ui.label(this, category.icon + "  " + category.title, 37f, Surfaces.primaryText(ut, Surfaces.heroA(ut)), true))
        hero.addView(Ui.label(this, category.subtitle, 21f, Surfaces.secondaryText(ut, Surfaces.heroB(ut))), Ui.lpm(Ui.WRAP, Ui.WRAP, 0f, 0, px(8f), 0, 0))
        content.addView(hero, Ui.lpm(Ui.MATCH, Ui.WRAP, 0f, 0, 0, 0, px(17f)))
        when (id) {
            "home" -> homeSettings()
            "appearance" -> appearanceSettings()
            "prayer" -> prayerSettings()
            "matches" -> matchSettings()
            "alerts" -> alertSettings()
            "apps" -> appSettings()
            "permissions" -> permissionSettings()
            "system" -> systemSettings()
            "about" -> aboutSettings()
        }
        // فتح المجموعة المحفوظة، أو المجموعة الوحيدة إن لم يوجد غيرها.
        val remembered = prefs.sp.getString("settings_hub_open_$id", "")
        val target = groups.firstOrNull { it.title == remembered } ?: if (groups.size == 1) groups[0] else null
        target?.let { setOpen(it, true) }
    }

    private class GroupRef(val title: String, val inner: LinearLayout, val arrow: TextView)

    private val groups = ArrayList<GroupRef>()

    private fun setOpen(g: GroupRef, open: Boolean) {
        g.inner.visibility = if (open) View.VISIBLE else View.GONE
        g.arrow.text = if (open) "▲" else "▼"
    }

    /** كل مجموعة قائمة منسدلة مستقلة: عنوانها ظاهر فقط، والضغط يفتحها ويغلق غيرها. */
    private fun group(title: String, subtitle: String = "") {
        val block = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(px(20f), px(10f), px(20f), px(12f))
            background = Ui.round(Surfaces.sectionA(ut), px(22f).toFloat(), px(2f), Ui.withAlpha(ut.accent, 70))
        }
        val head = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(px(12f), px(13f), px(12f), px(13f))
        }
        head.addView(Ui.label(this, title, 28f, Surfaces.accentText(ut), true), Ui.lp(0, Ui.WRAP, 1f))
        val arrow = Ui.label(this, "▼", 24f, Surfaces.accentOn(ut, Surfaces.row(ut)), true)
        head.addView(arrow)
        Ui.focusable(head, ut, 16f, Surfaces.row(ut))
        head.isClickable = true
        val inner = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            visibility = View.GONE
        }
        if (subtitle.isNotBlank()) inner.addView(Ui.label(this, subtitle, 18f, Surfaces.secondaryText(ut, Surfaces.sectionA(ut))), Ui.lpm(Ui.WRAP, Ui.WRAP, 0f, px(12f), px(4f), 0, px(8f)))
        block.addView(head, Ui.lp(Ui.MATCH, Ui.WRAP))
        block.addView(inner, Ui.lp(Ui.MATCH, Ui.WRAP))
        content.addView(block, Ui.lpm(Ui.MATCH, Ui.WRAP, 0f, 0, px(2f), 0, px(13f)))
        val ref = GroupRef(title, inner, arrow)
        groups.add(ref)
        head.setOnClickListener {
            val oldScrollY = contentScroll.scrollY
            val blockTop = block.top
            val nowOpen = inner.visibility != View.VISIBLE
            groups.forEach { setOpen(it, false) }
            setOpen(ref, nowOpen)
            prefs.sp.edit().putString("settings_hub_open_$selectedId", if (nowOpen) title else "").apply()
            contentScroll.post {
                val maxY = (content.height - contentScroll.height).coerceAtLeast(0)
                val wanted = if (!nowOpen && oldScrollY > maxY) blockTop else oldScrollY
                contentScroll.scrollTo(0, wanted.coerceIn(0, maxY))
            }
        }
        currentGroup = inner
    }

    private var currentGroup: LinearLayout? = null

    private fun setting(title: String, description: String, icon: String, click: () -> Unit) {
        val parent = currentGroup ?: content
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(px(16f), px(14f), px(16f), px(14f))
            background = Ui.round(Surfaces.row(ut), px(16f).toFloat(), px(1f), Surfaces.rowBorder(ut))
        }
        val badgeBg = Ui.over(Ui.withAlpha(ut.accent, 65), Surfaces.row(ut))
        val badge = Ui.label(this, icon, 27f, Surfaces.accentOn(ut, badgeBg), true).apply { gravity = Gravity.CENTER }
        badge.background = Ui.round(badgeBg, px(13f).toFloat())
        row.addView(badge, Ui.lpm(px(58f), px(58f), 0f, 0, 0, px(14f), 0))
        val texts = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        texts.addView(Ui.label(this, title, 23f, Surfaces.primaryText(ut, Surfaces.row(ut)), true))
        texts.addView(Ui.label(this, description, 17f, Surfaces.secondaryText(ut, Surfaces.row(ut))))
        row.addView(texts, Ui.lp(0, Ui.WRAP, 1f))
        row.addView(Ui.label(this, "‹", 32f, Surfaces.accentOn(ut, Surfaces.row(ut)), true))
        Ui.focusable(row, ut, 16f, Surfaces.row(ut))
        row.isClickable = true
        row.setOnClickListener { click() }
        parent.addView(row, Ui.lpm(Ui.MATCH, Ui.WRAP, 0f, 0, px(5f), 0, px(5f)))
    }

    private fun toggle(title: String, description: String, value: () -> Boolean, change: (Boolean) -> Unit) {
        val parent = currentGroup ?: content
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(px(16f), px(14f), px(16f), px(14f))
            background = Ui.round(Surfaces.row(ut), px(16f).toFloat(), px(1f), Surfaces.rowBorder(ut))
        }
        val texts = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        texts.addView(Ui.label(this, title, 22f, Surfaces.primaryText(ut, Surfaces.row(ut)), true))
        texts.addView(Ui.label(this, description, 16f, Surfaces.secondaryText(ut, Surfaces.row(ut))))
        row.addView(texts, Ui.lp(0, Ui.WRAP, 1f))
        val switch = Ui.label(this, "", 18f, Color.WHITE, true).apply {
            gravity = Gravity.CENTER
            minWidth = px(120f)
            setPadding(px(14f), px(11f), px(14f), px(11f))
        }
        fun paint() {
            val checked = value()
            switch.text = if (checked) "● مفعّل" else "○ متوقف"
            switch.background = Ui.round(if (checked) 0xFF087E68.toInt() else 0xFF3D4A60.toInt(), px(28f).toFloat())
        }
        paint()
        row.addView(switch)
        Ui.focusable(row, ut, 16f, Surfaces.row(ut))
        row.isClickable = true
        row.setOnClickListener { change(!value()); paint() }
        parent.addView(row, Ui.lpm(Ui.MATCH, Ui.WRAP, 0f, 0, px(5f), 0, px(5f)))
    }

    private fun homeSettings() {
        group("البطاقات والقائمة الجانبية", "ما يظهر على الشاشة الرئيسية")
        toggle("إظهار الساعة والتاريخ", "عرض الساعة والتاريخ", { prefs.showClockCard }, { prefs.showClockCard = it })
        toggle("إظهار الطقس", "عرض الطقس للمدينة المختارة", { prefs.showWeatherCard }, { prefs.showWeatherCard = it })
        toggle("إظهار مواقيت الصلاة", "الصلوات والعد التنازلي", { prefs.showPrayerCard }, { prefs.showPrayerCard = it })
        toggle("إظهار التطبيقات المثبتة", "صف التطبيقات على الرئيسية", { prefs.showPinnedApps }, { prefs.showPinnedApps = it })
        toggle("إظهار المباريات المفضلة", "المباريات المختارة", { prefs.showMatchFavorites }, { prefs.showMatchFavorites = it })
        toggle("إظهار الملاحظات", "قائمة مهام قصيرة على الرئيسية", { prefs.showNotesCard }, { prefs.showNotesCard = it })
        setting("إدارة الملاحظات", "إضافة وتعديل وحذف الملاحظات", "📝") { startActivity(Intent(this, NotesActivity::class.java)) }
        toggle("إظهار المناسبات الهجرية", "رمضان والعيدان (تقديرية بتقويم أم القرى)", { prefs.showOccasions }, { prefs.showOccasions = it })
        toggle("خلفية المسجد عند الغروب", "تظهر عند عدم وجود خلفية مخصصة", { prefs.mosqueScene }, { prefs.mosqueScene = it })
        toggle("خلفية غروب الجبال", "مشهد أصلي مرسوم بالكود؛ تظهر عند عدم وجود خلفية مخصصة", { prefs.duskScene }, { prefs.duskScene = it })
        setting("أزرار وأقسام الشاشة", "إظهار وإخفاء الأزرار والأقسام الأخرى", "▦") { startActivity(Intent(this, HomeCustomizationActivity::class.java)) }
        group("التطبيقات على الرئيسية", "اختيار التطبيقات وترتيبها")
        setting("اختيار التطبيقات", "ضغطة واحدة على التطبيق لإضافته أو إزالته", "+") { startActivity(Intent(this, AppsActivity::class.java).putExtra("mode", "pin")) }
        setting("ترتيب المفضلة وتسميتها", "ترتيب وتغيير أسماء وإزالة التطبيقات", "★") { startActivity(Intent(this, FavoriteAppsSettingsActivity::class.java)) }
        group("التخطيط", "شكل ترتيب الشاشة الرئيسية")
        setting("تخطيطات الشاشة", "اختر تخطيطًا فيُطبَّق فورًا على الرئيسية", "▤") { startActivity(Intent(this, LayoutPickerActivity::class.java)) }
    }

    private fun appearanceSettings() {
        group("السمات والألوان", "الهوية البصرية")
        setting("معرض السمات", "${Themes.normalIndices.size} سمة مع معاينة، السمة الحالية: ${Themes.get(prefs.themeIndex).name}", "◐") { startActivity(Intent(this, ThemePickerActivity::class.java)) }
        setting("ثيمات الأندية", "الأهلي ومانشستر يونايتد وريال مدريد", "♛") { startActivity(Intent(this, ClubThemesActivity::class.java)) }
        group("تصميم الشاشة الرئيسية", "شكل البطاقات والقائمة الجانبية")
        setting("تصميم البطاقات", cardStyles[prefs.cardStyle.coerceIn(0, cardStyles.lastIndex)], "▭") {
            pickFrom("تصميم البطاقات", cardStyles, prefs.cardStyle) { prefs.cardStyle = it }
        }
        group("الخلفية", "")
        setting("خلفيات الشاشة", "اختيار خلفية أو رابط صورة أو تحديثها", "▧") { startActivity(Intent(this, WallpaperActivity::class.java)) }
        group("الحركة والتاريخ")
        toggle("تشغيل الحركات والانتقالات", "أوقفها لتحسين الأداء", { prefs.animationsEnabled }, { prefs.animationsEnabled = it; Motion.enter(content, this) })
        toggle("عرض التاريخ الهجري", "بجوار الميلادي", { prefs.showHijriDate }, { prefs.showHijriDate = it })
    }

    private val cardStyles = listOf("أساسي", "سطح ثابت", "ملوّن بلون السمة", "بطاقة مصمتة بإطار")

    private fun pickFrom(title: String, options: List<String>, current: Int, onPick: (Int) -> Unit) {
        val list = android.widget.ListView(this)
        list.divider = null
        list.selector = Ui.selector(this, ut)
        val ad = Ui.listAdapter(this, ut)
        for ((i, o) in options.withIndex()) ad.add((if (i == current) "●  " else "○  ") + o)
        list.adapter = ad
        var dlg: android.app.Dialog? = null
        list.setOnItemClickListener { _, _, pos, _ ->
            dlg?.dismiss()
            if (pos in options.indices) { onPick(pos); toast("تم التطبيق: " + options[pos]); recreate() }
        }
        val box = LinearLayout(this)
        box.orientation = LinearLayout.VERTICAL
        box.addView(list, Ui.lp(Ui.MATCH, px(360f)))
        dlg = Ui.modal(this, ut, title, box, Btn("إغلاق") {})
    }

    private fun prayerSettings() {
        group("المدينة", "تؤثر على الطقس والمواقيت والتوقيت")
        setting("اختيار المدينة", "بحث عربي أو إنجليزي", "⌖") { CityPicker.show(this, prefs, ut) { toast("تم تحديث المدينة: ${prefs.cityName}"); recreate() } }
        group("المواقيت")
        setting("إعدادات الصلاة", "طريقة الحساب والمذهب والتنبيه لكل صلاة", "۞") { startActivity(Intent(this, PrayerSettingsActivity::class.java)) }
        group("الأذان والأصوات")
        toggle("تشغيل الأذان", "تشغيل الأذان تلقائيًا عند دخول الوقت", { prefs.adhanEnabled }, { prefs.adhanEnabled = it })
        setting("أصوات الأذان والإقامة والتنبيه", "صوت عام أو لصلاة محددة", "♫") { startActivity(Intent(this, PrayerSettingsActivity::class.java).putExtra("global", true)) }
    }

    private fun matchSettings() {
        group("الفرق والمصادر")
        setting("الأندية والمصادر", "إدارة الفرق ومصادر النتائج", "⚽") { startActivity(Intent(this, MatchPreferencesActivity::class.java)) }
        toggle("التحويل التلقائي بين المصادر", "استخدام مصدر بديل عند تعذر الأساسي", { prefs.matchAutoFailover }, { prefs.matchAutoFailover = it })
        toggle("مصدر ثانٍ للمقارنة (SofaScore)", "يقارن النتيجة وقت المباراة ويجلب المسجلين والبطاقات", { prefs.matchMultiSource }, { prefs.matchMultiSource = it })
        group("التنبيهات")
        toggle("صوت تنبيهات المباريات", "عند بدء المباراة أو الهدف", { prefs.matchSoundEnabled }, { prefs.matchSoundEnabled = it })
    }

    private fun alertSettings() {
        group("مركز الإشعارات")
        setting("مركز الإشعارات", "أنواع الإشعارات ومدتها", "♧") { startActivity(Intent(this, NotificationCenterActivity::class.java)) }
        setting("إشعارات التطبيقات", "السماح أو الكتم لكل تطبيق", "▣") { startActivity(Intent(this, AppsNotificationSettingsActivity::class.java)) }
        group("الجزيرة الديناميكية")
        toggle("تشغيل الجزيرة", "التنبيهات العائمة فوق التطبيقات", { prefs.islandEnabled }, { prefs.islandEnabled = it })
        setting("إعدادات الجزيرة", "الشكل والحجم واللون والموقع", "◉") { startActivity(Intent(this, IslandSettingsActivity::class.java)) }
        group("الأصوات")
        setting("أصوات التنبيهات", "أصوات الإشعارات والمباريات", "♫") { startActivity(Intent(this, AlertSoundSettingsActivity::class.java)) }
    }

    private fun appSettings() {
        group("التطبيقات")
        setting("كل التطبيقات", "عرض التطبيقات وتشغيلها", "▦") { startActivity(Intent(this, AppsActivity::class.java)) }
        group("التلفزيون والوسائط")
        setting("مداخل التلفزيون", "HDMI و AV", "▣") { startActivity(Intent(this, TvInputsActivity::class.java)) }
        setting("الإذاعات والمحطات", "إضافة وإدارة المحطات", "♫") { startActivity(Intent(this, RadioActivity::class.java)) }
        setting("البث المباشر IPTV", "القنوات والقوائم", "▶") { startActivity(Intent(this, IptvProActivity::class.java)) }
        setting("لوحة المباريات", "المباشرة والقادمة والسابقة", "▤") { startActivity(Intent(this, MatchesActivity::class.java)) }
        group("الإسلاميات")
        setting("القرآن الكريم", "قراءة واستماع ومتابعة", "☪") { startActivity(Intent(this, QuranActivity::class.java)) }
        setting("أذكار الصباح والمساء", "عدّاد لكل ذكر", "✦") { startActivity(Intent(this, AdhkarActivity::class.java)) }
    }

    private fun permissionSettings() {
        group("الشاشة الرئيسية الافتراضية", "جعل Abo Uobidah TV هو الرئيسية وزر Home")
        setting("مركز التفعيل", "حالة كل صلاحية وتفعيلها بالترتيب", "✓") { startActivity(Intent(this, SetupActivity::class.java).putExtra("chain", true)) }
        setting("تشخيص زر Home", "فحص حالة Home وخدمة الريموت", "⌂") { startActivity(Intent(this, HomeControlActivity::class.java)) }
        group("الأذونات")
        setting("الظهور فوق التطبيقات", "للجزيرة وتنبيهات الأذان", "↗") { DefaultLauncher.requestOverlay(this) }
        setting("الوصول للإشعارات", "لعرض إشعارات التطبيقات في الجزيرة", "↗") { DefaultLauncher.requestListener(this) }
    }

    private fun systemSettings() {
        group("الأداء")
        setting("تسريع وأداء الجهاز", "الذاكرة وحدود الأداء", "ϟ") { startActivity(Intent(this, PerformanceSettingsActivity::class.java)) }
        group("النسخ الاحتياطي")
        setting("النسخ الاحتياطي والاستعادة", "حفظ الإعدادات واستعادتها وإعادة الضبط", "⇧") { startActivity(Intent(this, BackupActivity::class.java)) }
    }

    private fun aboutSettings() {
        group("Abo Uobidah TV", "معلومات التطبيق")
        val version = runCatching { packageManager.getPackageInfo(packageName, 0).versionName ?: "غير معروف" }.getOrDefault("غير معروف")
        setting("الإصدار", "${version}  •  Android TV / ARM", "ⓘ") { toast("Abo Uobidah TV $version") }
        setting("التحديثات التلقائية", "اكتشاف الإصدارات الجديدة وتنزيلها عبر الإنترنت دون فلاشة", "⬆") {
            startActivity(Intent(this, UpdateActivity::class.java))
        }
        noteCard("تنبيه: بعض الخدمات مثل بيانات المباريات والطقس والبث تحتاج اتصال إنترنت، وبعض ميزات التحكم بزر Home تعتمد على صلاحيات نظام التلفزيون والشركة المصنّعة.")
    }

    private fun noteCard(text: String) {
        val card = Ui.label(this, "ⓘ  $text", 19f, Surfaces.secondaryText(ut, Surfaces.note(ut))).apply {
            setPadding(px(22f), px(18f), px(22f), px(18f))
            background = Ui.round(Surfaces.note(ut), px(17f).toFloat())
        }
        content.addView(card, Ui.lpm(Ui.MATCH, Ui.WRAP, 0f, 0, px(4f), 0, px(12f)))
    }
}
