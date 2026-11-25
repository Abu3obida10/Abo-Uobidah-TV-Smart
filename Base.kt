package com.uobidah.launcher

import android.app.Activity
import android.app.Dialog
import android.content.Intent
import android.content.ComponentName
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.Parcelable
import android.view.View
import android.widget.BaseAdapter
import android.widget.AbsListView
import android.widget.EditText
import android.widget.HorizontalScrollView
import android.widget.ScrollView
import android.widget.CompoundButton
import androidx.recyclerview.widget.RecyclerView
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.TextView
import android.widget.Toast

/** أساس كل الشاشات: ثيم + RTL + خط + وضع ملء الشاشة. */
open class BaseActivity : Activity() {
    /** الشاشات الفرعية معتمة بالكامل؛ الرئيسية فقط تعرض الخلفية والزخرفة. */
    protected open val solidScreens: Boolean get() = true

    /** بعض الشاشات الديناميكية تحفظ موضعها وتركيزها بمفاتيح دلالية لا بمسارات View. */
    protected open val restoreGenericViewState: Boolean get() = true

    lateinit var prefs: Prefs
    lateinit var ut: UTheme

    companion object {
        /** تسلسل الصفحات المفتوحة (الرجوع خطوة بخطوة). */
        val nav = com.uobidah.launcher.core.NavStack()
    }

    private val navId: String = javaClass.simpleName + "@" + System.identityHashCode(this)
    private var restoreState: Bundle? = null
    private var currentContent: View? = null
    private var restoredContentState = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        nav.push(navId)
        restoreState = savedInstanceState
        prefs = Prefs(this)
        // Persistent periodic update checks are scheduled once and survive process/device restarts.
        runCatching {
            UpdateScheduler.schedule(applicationContext)
            UpdateScheduler.checkOnLaunch(applicationContext)
        }
        MatchRepo.configure(prefs)
        FocusStyle.color = prefs.focusColor
        ut = Themes.get(prefs.themeIndex).let { if (solidScreens) Themes.solid(it) else it }
        Fonts.current = Fonts.typeface(prefs.fontIndex)
        window.decorView.layoutDirection = View.LAYOUT_DIRECTION_RTL
    }

    fun px(v: Float): Int = Ui.p(this, v)

    /** خلفية الثيم (تدرج + زخرفة النادي إن كان الثيم نادياً). */
    fun bgGradient(): Drawable =
        if (solidScreens) GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, intArrayOf(ut.bgTop, ut.bgBottom))
        else ThemeBg(ut)

    fun show(content: View) {
        content.layoutDirection = View.LAYOUT_DIRECTION_RTL
        currentContent = content
        setContentView(content)
        Motion.enter(content, this)
        if (!restoredContentState) {
            restoredContentState = true
            if (restoreGenericViewState) restoreViewState(content)
        }
    }

    /**
     * يعتمد الرجوع على مكدس Android الحقيقي للـ Activity بدلاً من قائمة عامة قديمة.
     * هذا يعيد المستخدم إلى آخر شاشة فتحها فعلياً، حتى مع فتح عدة صفحات أو إعادة إنشاء شاشة.
     */
    @Suppress("OVERRIDE_DEPRECATION", "DEPRECATION")
    override fun onBackPressed() {
        if (!isTaskRoot) super.onBackPressed() else goHome()
    }

    override fun onDestroy() {
        nav.remove(navId)
        super.onDestroy()
    }

    // ---------- حفظ واستعادة حالة واجهة الشاشة ----------
    // المسارات مبنية على ترتيب العناصر داخل الشجرة، لذلك لا نحتاج إلى IDs ثابتة لكل View ديناميكية.
    private fun pathTo(root: View, target: View, path: String = "0"): String? {
        if (root === target) return path
        if (root is android.view.ViewGroup) {
            for (i in 0 until root.childCount) {
                pathTo(root.getChildAt(i), target, "$path/$i")?.let { return it }
            }
        }
        return null
    }

    private fun viewAtPath(root: View, path: String): View? {
        if (path == "0" || path.isBlank()) return root
        var current: View = root
        val parts = path.split('/').drop(1)
        for (part in parts) {
            val index = part.toIntOrNull() ?: return null
            val group = current as? android.view.ViewGroup ?: return null
            if (index !in 0 until group.childCount) return null
            current = group.getChildAt(index)
        }
        return current
    }

    private fun walkViews(root: View, path: String = "0", action: (View, String) -> Unit) {
        action(root, path)
        if (root is android.view.ViewGroup) {
            for (i in 0 until root.childCount) walkViews(root.getChildAt(i), "$path/$i", action)
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        // Specify the View type explicitly: Android's generic findViewById<T>()
        // cannot be inferred reliably from the nullable/Elvis chain on all Kotlin versions.
        val root: View = currentContent ?: (
            window.decorView.findViewById<View>(android.R.id.content) as? android.view.ViewGroup
        )?.getChildAt(0) ?: return
        try {
            val records = ArrayList<Pair<String, View>>()
            walkViews(root) { view, path ->
                if (view is ScrollView || view is HorizontalScrollView || view is AbsListView || view is RecyclerView || view is EditText || view is CompoundButton) {
                    records += path to view
                }
            }
            outState.putInt("ui_records", records.size)
            records.forEachIndexed { index, (path, view) ->
                val key = "ui_$index"
                outState.putString("${key}_path", path)
                when (view) {
                    is ScrollView -> { outState.putString("${key}_kind", "scroll"); outState.putInt("${key}_y", view.scrollY) }
                    is HorizontalScrollView -> { outState.putString("${key}_kind", "hscroll"); outState.putInt("${key}_x", view.scrollX) }
                    is AbsListView -> {
                        outState.putString("${key}_kind", "list")
                        outState.putInt("${key}_position", view.firstVisiblePosition)
                        outState.putInt("${key}_top", view.getChildAt(0)?.top ?: 0)
                    }
                    is RecyclerView -> {
                        outState.putString("${key}_kind", "recycler")
                        view.layoutManager?.onSaveInstanceState()?.let { outState.putParcelable("${key}_layout", it) }
                    }
                    is EditText -> {
                        outState.putString("${key}_kind", "edit")
                        outState.putString("${key}_text", view.text?.toString() ?: "")
                        outState.putInt("${key}_selection_start", view.selectionStart.coerceAtLeast(0))
                        outState.putInt("${key}_selection_end", view.selectionEnd.coerceAtLeast(0))
                    }
                    is CompoundButton -> { outState.putString("${key}_kind", "checked"); outState.putBoolean("${key}_checked", view.isChecked) }
                }
            }
            val focus = currentFocus
            if (focus != null) pathTo(root, focus)?.let { outState.putString("ui_focus_path", it) }
        } catch (_: Exception) { }
    }

    private fun restoreViewState(content: View) {
        val state = restoreState ?: return
        restoreState = null
        content.post {
            try {
                val count = state.getInt("ui_records", 0).coerceIn(0, 300)
                for (index in 0 until count) {
                    val key = "ui_$index"
                    val path = state.getString("${key}_path") ?: continue
                    val view = viewAtPath(content, path) ?: continue
                    when (state.getString("${key}_kind")) {
                        "scroll" -> if (view is ScrollView) view.scrollTo(0, state.getInt("${key}_y", 0))
                        "hscroll" -> if (view is HorizontalScrollView) view.scrollTo(state.getInt("${key}_x", 0), 0)
                        "list" -> if (view is AbsListView) {
                            val position = state.getInt("${key}_position", 0)
                            val top = state.getInt("${key}_top", 0)
                            view.setSelectionFromTop(position, top)
                        }
                        "recycler" -> if (view is RecyclerView) {
                            val saved = state.getParcelable<Parcelable>("${key}_layout")
                            if (saved != null) view.post { view.layoutManager?.onRestoreInstanceState(saved) }
                        }
                        "edit" -> if (view is EditText) {
                            val text = state.getString("${key}_text") ?: ""
                            if (view.text.toString() != text) view.setText(text)
                            val start = state.getInt("${key}_selection_start", text.length).coerceIn(0, text.length)
                            val end = state.getInt("${key}_selection_end", start).coerceIn(0, text.length)
                            view.setSelection(minOf(start, end), maxOf(start, end))
                        }
                        "checked" -> if (view is CompoundButton) view.isChecked = state.getBoolean("${key}_checked", view.isChecked)
                    }
                }
                state.getString("ui_focus_path")?.let { viewAtPath(content, it)?.requestFocus() }
            } catch (_: Exception) { }
        }
    }

    fun goHome() {
        try {
            startActivity(
                Intent(this, MainLauncherActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            )
        } catch (_: Exception) {
        }
        finish()
    }

    fun toast(msg: String) {
        Toast.makeText(this, Icons.plain(msg), Toast.LENGTH_SHORT).show()
    }

    @Suppress("DEPRECATION")
    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
                View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or
                View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
                View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                View.SYSTEM_UI_FLAG_FULLSCREEN or
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
        }
    }

    /** عنوان شاشة موحّد مع زر رجوع. */
    fun header(title: String): LinearLayout {
        val row = LinearLayout(this)
        row.orientation = LinearLayout.HORIZONTAL
        row.gravity = android.view.Gravity.CENTER_VERTICAL
        val back = Ui.button(this, ut, "➜  رجوع", 28f) { onBackPressed() }
        val tv = Ui.label(this, title, 52f, ut.text, true)
        row.addView(tv, Ui.lp(0, Ui.WRAP, 1f))
        row.addView(back)
        return row
    }
}

/** أدوات التطبيقات المثبتة. */
object AppUtil {
    /**
     * Builds an explicit launch intent for a previously discovered launcher Activity.
     * This avoids depending on package-level resolution choosing the same activity
     * that was displayed to the user (especially on Android TV packages exposing
     * both LEANBACK_LAUNCHER and LAUNCHER entry points).
     */
    fun launchIntent(pm: PackageManager, pkg: String, activityName: String? = null): Intent? {
        if (pkg.isBlank()) return null
        if (!activityName.isNullOrBlank()) {
            return try {
                val component = ComponentName(pkg, activityName)
                val info = pm.getActivityInfo(component, 0)
                if (!info.enabled || !info.exported || !info.applicationInfo.enabled) return null
                Intent(Intent.ACTION_MAIN)
                    .setComponent(component)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
            } catch (_: Exception) {
                null
            }
        }

        // Prefer the Android TV entry point, then the regular Android launcher entry.
        val i = try {
            pm.getLeanbackLaunchIntentForPackage(pkg) ?: pm.getLaunchIntentForPackage(pkg)
        } catch (_: Exception) {
            null
        }
        return i?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
    }

    fun label(pm: PackageManager, pkg: String): String = try {
        pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
    } catch (_: Exception) {
        pkg
    }

    private val iconStates = HashMap<String, Drawable.ConstantState?>()

    /** أيقونة/بانر تطبيق من كاش في الذاكرة: تحميلها من النظام في كل تحديث للرئيسية كان يبطّئ الشاشة. */
    fun cachedIcon(pm: PackageManager, pkg: String, preferBanner: Boolean): Drawable? {
        val key = (if (preferBanner) "b:" else "i:") + pkg
        synchronized(iconStates) {
            if (iconStates.containsKey(key)) {
                val st = iconStates[key]
                val d = try { st?.newDrawable()?.mutate() } catch (_: Exception) { null }
                if (d != null) return d
                iconStates.remove(key)
            }
        }
        val d: Drawable? = try {
            (if (preferBanner) pm.getApplicationBanner(pkg) else null) ?: appIcon(pm, pkg)
        } catch (_: Exception) { appIcon(pm, pkg) }
        synchronized(iconStates) {
            if (iconStates.size > 160) iconStates.clear()
            iconStates[key] = d?.constantState
        }
        return d
    }

    fun appIcon(pm: PackageManager, pkg: String): Drawable? = try {
        pm.getApplicationIcon(pkg)
    } catch (_: Exception) {
        try { pm.getDefaultActivityIcon() } catch (_: Exception) { null }
    }
}

/** نافذة البحث عن مدينة (عربي/English) لأي مدينة أو قرية في العالم. */
object CityPicker {
    fun show(act: Activity, prefs: Prefs, t: UTheme, onDone: () -> Unit) {
        val box = LinearLayout(act)
        box.orientation = LinearLayout.VERTICAL
        val hint = Ui.label(act, "اكتب اسم المدينة أو القرية أو المركز (عربي أو English) ثم اضغط بحث", 24f, t.dim)
        val input = Ui.edit(act, t, "مثال: المنصورة / Paris / سيدي بشر")
        val status = Ui.label(act, " ", 24f, t.dim)
        val list = ListView(act)
        val adapter = Ui.listAdapter(act, t)
        list.adapter = adapter
        list.divider = null
        list.selector = Ui.selector(act, t)
        val found = ArrayList<City>()
        var dlg: Dialog? = null
        var searchRequestId = 0L

        val doSearch = {
            val q = input.text.toString().trim()
            if (q.isNotEmpty()) {
                val requestId = ++searchRequestId
                status.text = "جاري البحث..."
                Net.bg {
                    try {
                        val r = WeatherRepo.search(q)
                        Net.ui {
                            if (requestId != searchRequestId) return@ui
                            found.clear()
                            found.addAll(r)
                            adapter.clear()
                            for (c in r) adapter.add(c.name + (if (c.sub.isNotEmpty()) "  —  " + c.sub else ""))
                            adapter.notifyDataSetChanged()
                            status.text = if (r.isEmpty()) "لا توجد نتائج" else "اختر من النتائج:"
                        }
                    } catch (e: Exception) {
                        Net.ui {
                            if (requestId == searchRequestId) status.text = "تعذر البحث، تأكد من الاتصال بالإنترنت"
                        }
                    }
                }
            }
        }

        input.setOnEditorActionListener { _, _, _ ->
            doSearch()
            true
        }
        val searchBtn = Ui.button(act, t, "🔍 بحث", 28f) { doSearch() }

        list.setOnItemClickListener { _, _, pos, _ ->
            if (pos >= 0 && pos < found.size) {
                val c = found[pos]
                prefs.cityName = c.name
                prefs.cityLat = c.lat
                prefs.cityLon = c.lon
                prefs.cityTimezone = c.timezone.ifBlank { "Africa/Cairo" }
                dlg?.dismiss()
                onDone()
            }
        }

        box.addView(hint)
        val row = LinearLayout(act)
        row.orientation = LinearLayout.HORIZONTAL
        row.addView(input, Ui.lpm(0, Ui.WRAP, 1f, 0, Ui.p(act, 12f), Ui.p(act, 14f), Ui.p(act, 12f)))
        row.addView(searchBtn)
        box.addView(row)
        box.addView(status)
        box.addView(list, Ui.lp(Ui.MATCH, Ui.p(act, 380f)))

        dlg = Ui.modal(act, t, "🌍 اختيار المدينة (الطقس والصلاة)", box, Btn("إغلاق") {})
    }
}

/** قائمة متعددة الاختيار بتصميم اللانشر. */
object MultiPicker {
    class Item(val label: String, val key: String)

    fun show(act: Activity, t: UTheme, title: String, items: List<Item>, selected: List<String>, onDone: (List<String>) -> Unit) {
        val chosen = HashSet<String>(selected)
        val list = ListView(act)
        list.divider = null
        list.selector = Ui.selector(act, t)
        val adapter = object : BaseAdapter() {
            override fun getCount(): Int = items.size
            override fun getItem(position: Int): Any = items[position]
            override fun getItemId(position: Int): Long = position.toLong()
            override fun getView(position: Int, convertView: View?, parent: android.view.ViewGroup?): View {
                val item = items[position]
                val tv = Ui.label(act, (if (chosen.contains(item.key)) "☑  " else "☐  ") + item.label, 30f, t.text)
                tv.setPadding(Ui.p(act, 24f), Ui.p(act, 16f), Ui.p(act, 24f), Ui.p(act, 16f))
                return tv
            }
        }
        list.adapter = adapter
        list.setOnItemClickListener { _, _, pos, _ ->
            val k = items[pos].key
            if (chosen.contains(k)) chosen.remove(k) else chosen.add(k)
            adapter.notifyDataSetChanged()
        }
        val box = LinearLayout(act)
        box.orientation = LinearLayout.VERTICAL
        box.addView(list, Ui.lp(Ui.MATCH, Ui.p(act, 520f)))
        Ui.modal(act, t, title, box,
            Btn("✔ حفظ") { onDone(items.map { it.key }.filter { chosen.contains(it) }) },
            Btn("إلغاء") {})
    }
}
