package com.uobidah.launcher

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.provider.Settings
import com.uobidah.launcher.core.IslandGeometry
import kotlin.math.min

/** إعدادات الجزيرة الديناميكية: الشكل، الحجم، اللون، مكان الظهور، المدة، التطبيقات المسموحة، وزر التجربة. */
class IslandSettingsActivity : SettingsBase() {

    private val colors = listOf(
        Pair("كحلي", 0xFF0F172A.toInt()),
        Pair("زمردي", 0xFF064E3B.toInt()),
        Pair("قرمزي", 0xFF881337.toInt()),
        Pair("بنفسجي", 0xFF4C1D95.toInt()),
        Pair("سماوي", 0xFF0E7490.toInt()),
        Pair("أزرق ملكي", 0xFF1D4ED8.toInt()),
        Pair("بني", 0xFF7C2D12.toInt()),
        Pair("أسود", 0xFF000000.toInt()),
        Pair("رمادي فحمي", 0xFF27272A.toInt()),
        Pair("أخضر ليلي", 0xFF14532D.toInt())
    )
    private val shapes = listOf(
        "💊 كبسولة", "▢ مستطيل ناعم", "▬ شريط عريض", "◖ كبسولة صغيرة", "▣ بطاقة رأسية",
        "▰ لافتة كاملة", "◇ إطار مضيء", "▤ بطاقة مكدسة", "◩ مربع دائري", "▰ شريط سفلي عريض"
    )
    private val sizes = listOf("صغير", "متوسط", "كبير")
    private val positions = listOf("أعلى الوسط", "أعلى اليمين", "أعلى اليسار", "أسفل الوسط", "وسط الشاشة")
    private val durations = listOf(3, 5, 8, 12, 20, 30)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setupScreen("🏝️ الجزيرة الديناميكية")
        lastCanDraw = IslandController.canDraw(this)

        toggle("تفعيل الجزيرة الديناميكية", { prefs.islandEnabled }, { prefs.islandEnabled = it })
        section("حالة التشغيل")
        val hasPerm = IslandController.hasOverlayPermission(this)
        val viaA11y = IslandController.hasAccessibilityOverlay()
        note(when {
            hasPerm -> "✅ إذن الظهور فوق التطبيقات مفعّل. الجزيرة ستظهر فوق أي تطبيق."
            viaA11y -> "✅ الجزيرة تعمل عبر خدمة Abo Uobidah TV Home Control (إمكانية الدخول) حتى بدون إذن الظهور فوق التطبيقات."
            else -> "⚠ الجزيرة لن تظهر فوق التطبيقات الآن: لا يوجد إذن الظهور فوق التطبيقات ولا خدمة إمكانية الدخول مفعّلة. استخدم أحد الحلين بالأسفل."
        })
        IslandController.lastStatus?.let { st ->
            note((if (st.ok) "آخر محاولة عرض نجحت: " else "آخر محاولة عرض فشلت: ") + st.message)
        }
        action("🔐 فتح إذن الظهور فوق التطبيقات") { requestOverlayPermission() }
        action("♿ حل بديل: تفعيل خدمة Abo Uobidah TV Home Control") {
            if (!DefaultLauncher.ensureHomeService(this)) DefaultLauncher.request(this, "home_accessibility")
            else { toast("تم كتابة تفعيل الخدمة، جارٍ التحقق من اتصالها..."); recheckSoon() }
        }
        action("🔄 تحديث حالة الإذن") { rebuild("") }
        action("🧪 تجربة فورية فوق الشاشة") { preview() }

        section("معاينة حية (داخل الإعدادات)")
        note("هذه هي الجزيرة بنفس الشكل والحجم واللون والشفافية المختارة. تتغير فورًا عند تغيير أي إعداد، وتعمل حتى لو لم يُمنح الإذن بعد.")
        val demo = android.widget.FrameLayout(this).apply {
            setPadding(Ui.p(this@IslandSettingsActivity, 10f), Ui.p(this@IslandSettingsActivity, 14f), Ui.p(this@IslandSettingsActivity, 10f), Ui.p(this@IslandSettingsActivity, 14f))
            val density = resources.displayMetrics.density.coerceAtLeast(0.1f)
            val screenWidthDp = (resources.displayMetrics.widthPixels / density).toInt()
            val sidePadding = ScreenLayouts.get(prefs.screenLayout).sideDp
            // Available width inside SettingsBase's side padding, body padding and preview frame.
            val previewWidthDp = (screenWidthDp - sidePadding * 2f - 36f).toInt().coerceAtLeast(240)
            val island = IslandController.buildPreview(
                this@IslandSettingsActivity,
                "Abo Uobidah TV • الجزيرة الديناميكية",
                "معاينة التصميم — التنبيهات والمباريات والصلاة",
                "✦",
                previewWidthDp
            )
            island.alpha = prefs.islandOpacity.coerceIn(25, 100) / 100f
            addView(island, android.widget.FrameLayout.LayoutParams(
                if (prefs.islandShape in setOf(2, 5, 9)) android.view.ViewGroup.LayoutParams.MATCH_PARENT else android.view.ViewGroup.LayoutParams.WRAP_CONTENT,
                android.view.ViewGroup.LayoutParams.WRAP_CONTENT,
                android.view.Gravity.CENTER
            ))
        }
        (activeSection ?: body).addView(demo, Ui.lp(Ui.MATCH, Ui.WRAP))

        section("الشكل")
        chips("shape", shapes, prefs.islandShape) {
            prefs.islandShape = it
            preview()
            rebuild("shape:$it")
        }

        section("الحجم")
        chips("size", sizes, prefs.islandSize) {
            prefs.islandSize = it
            preview()
            rebuild("size:$it")
        }

        section("اللون")
        var colorIdx = colors.indexOfFirst { it.second == prefs.islandColor }
        if (colorIdx < 0) colorIdx = 0
        chips("color", colors.map { dotted(lighten(it.second), it.first) }, colorIdx) {
            prefs.islandColor = colors[it].second
            preview()
            rebuild("color:$it")
        }

        section("مكان الظهور")
        chips("pos", positions, prefs.islandPos) {
            prefs.islandPos = it
            prefs.islandCustomPosition = false
            preview()
            rebuild("pos:$it")
        }

        section("الموضع الدقيق بالريموت")
        note("الموضع المحفوظ: ${positionLabel()}. استخدم الأسهم لتحريك المعاينة 48dp في كل مرة. يتم حفظ الموضع واستخدامه مع تنبيهات الصلاة والمباريات والتطبيقات.")
        action("⬆ تحريك لأعلى") { moveIsland(0, -48) }
        action("⬇ تحريك لأسفل") { moveIsland(0, 48) }
        action("⬅ تحريك لليسار") { moveIsland(-48, 0) }
        action("➡ تحريك لليمين") { moveIsland(48, 0) }
        action("✨ إعادة للوضع الاحترافي: أعلى منتصف الشاشة") {
            prefs.islandPos = IslandGeometry.TOP_CENTER
            prefs.islandOffsetX = 0
            prefs.islandOffsetY = 0
            prefs.islandCustomPosition = false
            preview()
            toast("تم ضبط الجزيرة أعلى منتصف الشاشة")
            rebuild("")
        }
        action("↺ توسيط الجزيرة") {
            prefs.islandOffsetX = 0
            prefs.islandOffsetY = 0
            prefs.islandCustomPosition = true
            preview()
            toast("تم توسيط الجزيرة")
        }

        section("الشفافية")
        val opacityLevels = listOf(40, 55, 70, 85, 100)
        chips("opacity", opacityLevels.map { "$it%" }, opacityLevels.indexOf(prefs.islandOpacity).coerceAtLeast(0)) {
            prefs.islandOpacity = opacityLevels[it]
            preview()
            rebuild("opacity:$it")
        }

        section("مدة البقاء (بالثواني)")
        var durIdx = durations.indexOf(prefs.islandDuration)
        if (durIdx < 0) durIdx = 2
        chips("dur", durations.map { "$it ث" }, durIdx) {
            prefs.islandDuration = durations[it]
            preview()
            rebuild("dur:$it")
        }

        section("ما الذي يظهر في الجزيرة؟")
        toggle("تنبيهات الأذان والصلاة", { prefs.islandAdhanAlerts }, { prefs.islandAdhanAlerts = it })
        toggle("مواعيد وأهداف مباريات الأهلي ومانشستر يونايتد وريال مدريد", { prefs.islandMatchAlerts }, { prefs.islandMatchAlerts = it })
        toggle("إشعارات التطبيقات المختارة", { prefs.islandAppAlerts }, { prefs.islandAppAlerts = it })

        section("التطبيقات المسموح لها بإرسال الإشعارات")
        note("المختارة الآن: " + prefs.allowedApps.size + " تطبيق")
        action("📋 اختيار التطبيقات") { pickApps() }
        action("⚙ إعدادات كل تطبيق (إظهار الإشعار وكتم الصوت بشكل مستقل)") {
            startActivity(Intent(this, AppsNotificationSettingsActivity::class.java))
        }
        action("🔔 السماح بالوصول للإشعارات (مطلوب مرة واحدة)") {
            try {
                startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
            } catch (e: Exception) {
                toast("فعّله عبر ADB: adb shell cmd notification allow_listener $packageName/$packageName.IslandNotificationListener")
            }
        }

        finishScreen()
        if (intent.getBooleanExtra("help", false)) {
            intent.removeExtra("help")
            window.decorView.post { showAdbHelp() }
        }
    }

    private fun moveIsland(dx: Int, dy: Int) {
        val metrics = resources.displayMetrics
        val marginPx = Ui.p(this, 24f)
        val widthPx = (metrics.widthPixels - marginPx * 2).coerceAtLeast(1)
        val targetWidth = when (prefs.islandShape) {
            2, 5, 9 -> widthPx
            1, 6, 8 -> min(widthPx, Ui.p(this, 640f))
            4, 7 -> min(widthPx, Ui.p(this, 520f))
            else -> min(widthPx, Ui.p(this, 560f))
        }
        val targetHeight = min(metrics.heightPixels, Ui.p(this, 140f))
        val limits = IslandGeometry.limits(
            metrics.widthPixels, metrics.heightPixels, targetWidth, targetHeight,
            metrics.density, 24
        )
        prefs.islandOffsetX = (prefs.islandOffsetX + dx).coerceIn(-limits.xDp, limits.xDp)
        prefs.islandOffsetY = (prefs.islandOffsetY + dy).coerceIn(-limits.yDp, limits.yDp)
        prefs.islandCustomPosition = true
        preview()
        toast("تم تحريك الجزيرة. الإزاحة: ${prefs.islandOffsetX} أفقيًا، ${prefs.islandOffsetY} رأسيًا")
        rebuild("")
    }

    private fun positionLabel(): String = when {
        prefs.islandCustomPosition -> "موضع حر (${prefs.islandOffsetX}, ${prefs.islandOffsetY})"
        else -> positions.getOrNull(prefs.islandPos) ?: "أعلى الوسط"
    }

    private fun requestOverlayPermission() {
        if (IslandController.hasOverlayPermission(this)) {
            toast("✅ إذن الظهور فوق التطبيقات مفعّل بالفعل")
            return
        }
        val opened = OverlayPermission.open(this)
        if (opened == null) {
            showAdbHelp()
        } else {
            toast("فُتح: $opened — فعّل Abo Uobidah TV ثم ارجع بزر الرجوع")
        }
    }

    /** لا توجد صفحة إذن في هذا النظام: اعرض الحلول الفعلية بدل رسالة عامة. */
    private fun showAdbHelp() {
        val tv = Ui.label(
            this,
            "هذا التلفزيون لا يعرض صفحة «الظهور فوق التطبيقات».\n\n" +
                "الحل 1 (من الكمبيوتر، مرة واحدة):\n" + OverlayPermission.adbCommand(packageName) + "\n\n" +
                "الحل 2 (من الكمبيوتر، مرة واحدة): امنح اللانشر صلاحية تفعيل الخدمة البديلة بنفسه:\n" +
                OverlayPermission.adbSecureCommand(packageName) + "\n" +
                "ثم اضغط «تفعيل الخدمة البديلة» وستعرض الجزيرة عبر خدمة إمكانية الدخول بدون إذن الظهور.\n\n" +
                "إن كانت صفحة إمكانية الدخول متاحة على تلفزيونك فعّل Abo Uobidah TV Home Control منها مباشرة.",
            22f, ut.text
        )
        Ui.modal(this, ut, "🪟 تفعيل الظهور فوق التطبيقات", tv,
            Btn("♿ تفعيل الخدمة البديلة") {
                if (!DefaultLauncher.ensureHomeService(this)) DefaultLauncher.request(this, "home_accessibility")
                recheckSoon()
            },
            Btn("إغلاق") {})
    }

    override fun onResume() {
        super.onResume()
        val now = IslandController.canDraw(this)
        if (now != lastCanDraw) {
            lastCanDraw = now
            toast(if (now) "✅ الجزيرة جاهزة الآن (" + IslandController.modeLabel(this) + ")" else "⚠ الجزيرة ما زالت غير متاحة")
            rebuild("")
        }
    }

    private var lastCanDraw: Boolean = false

    /** الخدمة تتصل بعد تفعيلها بثوانٍ؛ نتحقق بعد مهلة ونعرض النتيجة الفعلية فقط (لا نجاح مفترض). */
    private fun recheckSoon() {
        window.decorView.postDelayed({
            if (isFinishing || isDestroyed) return@postDelayed
            val ready = IslandController.canDraw(this)
            lastCanDraw = ready
            toast(if (ready) "✅ الجزيرة جاهزة: " + IslandController.modeLabel(this)
                else "❌ الخدمة لم تتصل بعد. إن لم تعمل بعد دقيقة فنفّذ أمر ADB الظاهر في «فتح إذن الظهور».")
            rebuild("")
        }, 2000L)
    }

    private fun lighten(c: Int): Int {
        // لتظهر النقطة الداكنة على خلفية داكنة
        val r = (Color.red(c) + 90).coerceAtMost(255)
        val g = (Color.green(c) + 90).coerceAtMost(255)
        val b = (Color.blue(c) + 90).coerceAtMost(255)
        return Color.rgb(r, g, b)
    }

    private fun preview() {
        if (!IslandController.canDraw(this)) {
            toast("لن تظهر الجزيرة فوق الشاشة قبل تفعيل أحد الحلين. المعاينة الحية بالأعلى تعمل دائمًا.")
            requestOverlayPermission()
            return
        }
        IslandController.show(this, "Abo Uobidah TV • الجزيرة الديناميكية", "معاينة التصميم — التنبيهات والمباريات والصلاة", "✦", 12, force = true)
        // النتيجة الفعلية تُعرض بعد المحاولة: لا رسالة نجاح قبل التحقق.
        window.decorView.postDelayed({
            val st = IslandController.lastStatus
            if (st != null) toast((if (st.ok) "✅ " else "❌ ") + st.message)
        }, 700L)
    }

    private fun pickApps() {
        val pm = packageManager
        val seen = HashSet<String>()
        val items = ArrayList<MultiPicker.Item>()
        val cats = listOf(Intent.CATEGORY_LEANBACK_LAUNCHER, Intent.CATEGORY_LAUNCHER)
        for (cat in cats) {
            val q = Intent(Intent.ACTION_MAIN)
            q.addCategory(cat)
            for (ri in pm.queryIntentActivities(q, 0)) {
                val pkg = ri.activityInfo.packageName
                if (pkg == packageName || !seen.add(pkg)) continue
                val label = try { ri.loadLabel(pm).toString() } catch (e: Exception) { pkg }
                items.add(MultiPicker.Item(label, pkg))
            }
        }
        items.sortBy { it.label.lowercase() }
        MultiPicker.show(this, ut, "📋 التطبيقات المسموح لها", items, prefs.allowedApps) {
            prefs.allowedApps = it
            toast("تم حفظ " + it.size + " تطبيق")
            rebuild("")
        }
    }
}
