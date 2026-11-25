package com.uobidah.launcher

import android.content.Context
import android.content.Intent
import android.media.tv.TvContract
import android.media.tv.TvInputInfo
import android.media.tv.TvInputManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

/** مداخل الشاشة (HDMI / AV) وحالة الرسيفر. */
class TvInputsActivity : BaseActivity() {

    companion object {
        const val EXTRA_OPEN_HDMI_DIRECT = "open_hdmi_direct"

        /** Explicit launcher route: open the app's own TV-input screen and let it try passthrough. */
        fun directInputsIntent(context: Context): Intent =
            Intent(context, TvInputsActivity::class.java)
                .putExtra(EXTRA_OPEN_HDMI_DIRECT, true)
    }

    private lateinit var tm: TvInputManager
    private lateinit var list: LinearLayout
    private lateinit var summary: TextView
    private lateinit var tvAppBtn: TextView
    private lateinit var fullBtn: TextView
    private val handler = Handler(Looper.getMainLooper())

    private val poll = object : Runnable {
        override fun run() {
            render()
            handler.postDelayed(this, 3000)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        tm = getSystemService(Context.TV_INPUT_SERVICE) as TvInputManager

        val root = LinearLayout(this)
        root.orientation = LinearLayout.VERTICAL
        root.background = bgGradient()
        root.setPadding(px(56f), px(32f), px(56f), px(24f))
        root.addView(header("📺 مداخل التلفزيون وحالة الرسيفر"), Ui.lp(Ui.MATCH, Ui.WRAP))

        summary = Ui.label(this, "", 32f, ut.accent, true)
        root.addView(summary, Ui.lpm(Ui.MATCH, Ui.WRAP, 0f, 0, px(14f), 0, px(14f)))

        val bar = LinearLayout(this)
        bar.orientation = LinearLayout.HORIZONTAL
        tvAppBtn = Ui.button(this, ut, "", 24f) { chooseTvApp() }
        fullBtn = Ui.button(this, ut, "", 24f) {
            prefs.tvFullScreen = !prefs.tvFullScreen
            updateButtons()
        }
        bar.addView(tvAppBtn, Ui.lpm(Ui.WRAP, Ui.WRAP, 0f, 0, 0, px(14f), 0))
        bar.addView(fullBtn, Ui.lpm(Ui.WRAP, Ui.WRAP, 0f, 0, 0, px(14f), 0))
        bar.addView(Ui.button(this, ut, "▶ فتح تطبيق التلفزيون", 24f) { openTvApp() })
        bar.addView(Ui.button(this, ut, "⚙ إعدادات مداخل التلفاز", 24f) { openTvSystemSettings() })
        root.addView(bar, Ui.lpm(Ui.WRAP, Ui.WRAP, 0f, 0, 0, 0, px(14f)))

        val sv = ScrollView(this)
        sv.clipToPadding = false
        sv.setPadding(px(8f), px(8f), px(8f), px(8f))
        list = LinearLayout(this)
        list.orientation = LinearLayout.VERTICAL
        sv.addView(list)
        root.addView(sv, Ui.lp(Ui.MATCH, 0, 1f))
        show(root)
        updateButtons()
        if (intent.getBooleanExtra(EXTRA_OPEN_HDMI_DIRECT, false)) {
            root.post { openTvOrInputsDirectly() }
        }
    }

    override fun onResume() {
        super.onResume()
        handler.post(poll)
    }

    override fun onPause() {
        handler.removeCallbacks(poll)
        super.onPause()
    }

    private fun updateButtons() {
        val pkg = prefs.tvApp
        tvAppBtn.text = "📺 تطبيق التلفزيون الأساسي: " + (if (pkg.isEmpty()) "غير محدد" else AppUtil.label(packageManager, pkg))
        fullBtn.text = if (prefs.tvFullScreen) "⛶ ملء الشاشة: مفعّل" else "⛶ ملء الشاشة: متوقف"
    }

    private fun typeName(t: Int): String = when (t) {
        TvInputInfo.TYPE_HDMI -> "HDMI"
        TvInputInfo.TYPE_COMPOSITE -> "AV (كومبوزيت)"
        TvInputInfo.TYPE_COMPONENT -> "Component"
        TvInputInfo.TYPE_SVIDEO -> "S-Video"
        TvInputInfo.TYPE_SCART -> "SCART"
        TvInputInfo.TYPE_VGA -> "VGA"
        TvInputInfo.TYPE_DVI -> "DVI"
        TvInputInfo.TYPE_DISPLAY_PORT -> "DisplayPort"
        TvInputInfo.TYPE_TUNER -> "هوائي / رسيفر مدمج"
        else -> "مدخل"
    }

    private fun stateText(s: Int): String = when (s) {
        TvInputManager.INPUT_STATE_CONNECTED -> "✅ متصل ونشط"
        TvInputManager.INPUT_STATE_CONNECTED_STANDBY -> "🟡 متصل (وضع الاستعداد)"
        else -> "⚪ غير متصل"
    }

    private fun render() {
        list.removeAllViews()
        val inputs: List<TvInputInfo> = try {
            tm.tvInputList
        } catch (e: Exception) {
            emptyList()
        }
        var hw = 0
        var activeCount = 0
        var standby = 0
        for (info in inputs) {
            if (!info.isPassthroughInput && info.type != TvInputInfo.TYPE_TUNER) continue
            hw++
            val st = try { tm.getInputState(info.id) } catch (e: Exception) { TvInputManager.INPUT_STATE_DISCONNECTED }
            if (st == TvInputManager.INPUT_STATE_CONNECTED) activeCount++
            if (st == TvInputManager.INPUT_STATE_CONNECTED_STANDBY) standby++

            val custom = try { info.loadCustomLabel(this)?.toString() } catch (e: Exception) { null }
            val label = try { info.loadLabel(this)?.toString() } catch (e: Exception) { null }
            val name = if (!custom.isNullOrEmpty()) custom else if (!label.isNullOrEmpty()) label else typeName(info.type)

            val row = LinearLayout(this)
            row.orientation = LinearLayout.VERTICAL
            row.setPadding(px(34f), px(22f), px(34f), px(22f))
            Ui.focusable(row, ut, 28f)
            row.isClickable = true
            row.setOnClickListener { launchInput(info) }
            row.addView(Ui.label(this, name + "   (" + typeName(info.type) + ")", 34f, ut.text, true))
            row.addView(Ui.label(this, stateText(st), 28f, ut.dim), Ui.lpm(Ui.WRAP, Ui.WRAP, 0f, 0, px(6f), 0, 0))
            list.addView(row, Ui.lpm(Ui.MATCH, Ui.WRAP, 0f, 0, 0, 0, px(14f)))
        }
        summary.text = when {
            hw == 0 -> "لم يتم العثور على مداخل (قد لا يدعم هذا الجهاز TvInputManager)"
            activeCount > 0 -> "✅ الرسيفر متصل ونشط (" + activeCount + " مدخل نشط)"
            standby > 0 -> "🟡 الرسيفر متصل لكنه في وضع الاستعداد"
            else -> "⚪ لا يوجد رسيفر متصل حالياً"
        }
    }

    /** يفتح URI القياسي لمدخل passthrough أولاً؛ ثم تطبيق التلفزيون المحفوظ، ثم مشغل TvView الاحتياطي. */
    private fun launchInput(info: TvInputInfo) {
        val custom = try { info.loadCustomLabel(this)?.toString() } catch (e: Exception) { null }
        val label = try { info.loadLabel(this)?.toString() } catch (e: Exception) { null }
        val name = if (!custom.isNullOrEmpty()) custom else if (!label.isNullOrEmpty()) label else typeName(info.type)
        val uri = try { TvContract.buildChannelUriForPassthroughInput(info.id) } catch (_: Exception) { null }
        if (uri != null) {
            try {
                val viewIntent = Intent(Intent.ACTION_VIEW, uri)
                if (viewIntent.resolveActivity(packageManager) != null) {
                    startActivity(viewIntent)
                    if (prefs.tvFullScreen) finish()
                    return
                }
            } catch (_: Exception) { }
        }
        // بعض الشركات تمنع تطبيقات الطرف الثالث من فتح URI؛ جرّب تطبيق التلفزيون الذي اختاره المستخدم سابقاً.
        val pkg = prefs.tvApp
        if (pkg.isNotBlank()) {
            val appIntent = AppUtil.launchIntent(packageManager, pkg)
            if (appIntent != null) {
                try { startActivity(appIntent); if (prefs.tvFullScreen) finish(); return } catch (_: Exception) { }
            }
        }
        // آخر محاولة داخلية، وتظهر رسالة دقيقة عند عدم سماح نظام الشركة بالتشغيل.
        try {
            startActivity(Intent(this, TvPassthroughActivity::class.java)
                .putExtra(TvPassthroughActivity.EXTRA_INPUT_ID, info.id)
                .putExtra(TvPassthroughActivity.EXTRA_NAME, name))
        } catch (e: Exception) {
            toast("هذا التلفزيون لا يسمح للتطبيقات الخارجية بفتح مدخل HDMI مباشرة: ${e.message ?: e.javaClass.simpleName}")
        }
    }

    /** يفضّل HDMI المتصل/الاستعداد أولاً، ثم تطبيق التلفزيون المحفوظ؛ لا يفتح مدخلاً مفصولاً. */
    private fun openTvOrInputsDirectly() {
        val savedTvIntent = prefs.tvApp.takeIf { it.isNotBlank() }
            ?.let { AppUtil.launchIntent(packageManager, it) }

        val hdmiInputs = try {
            tm.tvInputList.filter { info -> info.type == TvInputInfo.TYPE_HDMI && info.isPassthroughInput }
        } catch (_: Exception) { emptyList() }
        val inputStates = hdmiInputs.map { info ->
            info to runCatching { tm.getInputState(info.id) }
                .getOrDefault(TvInputManager.INPUT_STATE_DISCONNECTED)
        }

        when (TvInputRoutingPolicy.selectDirectRoute(
            savedTvIntent != null,
            inputStates.map { it.second },
            TvInputManager.INPUT_STATE_CONNECTED,
            TvInputManager.INPUT_STATE_CONNECTED_STANDBY
        )) {
            TvInputRoutingPolicy.DirectRoute.CONNECTED_HDMI,
            TvInputRoutingPolicy.DirectRoute.INPUTS_LIST -> Unit
            TvInputRoutingPolicy.DirectRoute.SAVED_TV_APP -> {
                try {
                    startActivity(savedTvIntent!!)
                    if (prefs.tvFullScreen) finish()
                    return
                } catch (_: Exception) {
                    // The saved application can disappear after resolution. Keep the user
                    // in this inputs screen rather than selecting a disconnected HDMI input.
                }
            }
        }

        val connectedHdmi = inputStates.firstOrNull {
            TvInputRoutingPolicy.isDirectlyAvailableInputState(
                it.second,
                TvInputManager.INPUT_STATE_CONNECTED,
                TvInputManager.INPUT_STATE_CONNECTED_STANDBY
            )
        }?.first
        if (connectedHdmi != null) {
            launchInput(connectedHdmi)
            return
        }

        // Never auto-open a disconnected HDMI entry. Leave the dedicated list available so the
        // user can inspect AV/tuner entries or reconnect the cable before selecting an input.
        if (!::list.isInitialized || list.childCount == 0) render()
        toast("لم يتم العثور على مدخل HDMI متصل؛ يمكنك اختيار مدخل آخر من قائمة المداخل.")
    }

    /** بعض الشركات توفر Action خاصاً بإعدادات مداخل التلفاز، لكنه ليس موحداً بين أجهزة Android TV. */
    private fun openTvSystemSettings() {
        val actions = listOf(
            "android.settings.TV_INPUT_SETTINGS",
            "android.settings.TV_SETTINGS",
            "com.google.android.tv.settings.action.TV_INPUTS"
        )
        for (action in actions) {
            try {
                val intent = Intent(action)
                if (intent.resolveActivity(packageManager) != null) {
                    startActivity(intent)
                    return
                }
            } catch (_: Exception) { }
        }
        // لا نستخدم ACTION_SETTINGS العام لأنه لا يضمن فتح إعدادات HDMI. نعرض قائمة المداخل كبديل صريح.
        toast("هذا النظام لا يعلن اختصاراً عاماً لإعدادات HDMI؛ اختر المدخل من قائمة المداخل أدناه.")
        if (!::list.isInitialized || list.childCount == 0) render()
    }

    private fun chooseTvApp() {
        val i = Intent(this, AppsActivity::class.java)
        i.putExtra("mode", "pick")
        @Suppress("DEPRECATION")
        startActivityForResult(i, 21)
    }

    @Suppress("OVERRIDE_DEPRECATION", "DEPRECATION")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 21 && resultCode == RESULT_OK) {
            val pkg = data?.getStringExtra("pkg") ?: ""
            if (pkg.isNotEmpty()) {
                prefs.tvApp = pkg
                updateButtons()
            }
        }
    }

    private fun openTvApp() {
        val pkg = prefs.tvApp
        if (pkg.isEmpty()) {
            openTvOrInputsDirectly()
            return
        }
        val i = AppUtil.launchIntent(packageManager, pkg)
        if (i == null) {
            prefs.tvApp = ""
            updateButtons()
            openTvOrInputsDirectly()
            return
        }
        try {
            startActivity(i)
            if (prefs.tvFullScreen) finish()
        } catch (e: Exception) {
            toast("تعذر فتح التطبيق")
        }
    }
}
