package com.uobidah.launcher

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ApplicationInfo
import android.content.pm.ResolveInfo
import android.graphics.drawable.Drawable
import android.os.Build
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.EditText
import android.widget.GridView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView

class AppItem(
    val label: String,
    val pkg: String,
    val activityName: String,
    val icon: Drawable?,
    val isLeanback: Boolean,
    val isGame: Boolean = false
)

/**
 * Installed application catalogue.
 * browse: tap launches (or pins/unpins while «إضافة للرئيسية» is on).
 * pin: tap pins/unpins.
 * pick: returns the selected package to the calling screen.
 */
class AppsActivity : BaseActivity() {

    private val KIDS_TOKENS = listOf(
        "kids", "kid", "child", "toon", "cartoon", "baby", "spacetoon", "cartoonito",
        "أطفال", "اطفال", "طفل", "كرتون", "سبيستون", "طيور", "بيبي"
    )

    private var mode = "browse"
    private val allApps = ArrayList<AppItem>()
    private val apps = ArrayList<AppItem>()
    private lateinit var grid: GridView
    private lateinit var adapter: BaseAdapter
    private lateinit var status: TextView
    private lateinit var search: EditText
    private var receiverRegistered = false
    private var pinning = false
    private var pinBtn: TextView? = null

    private class Cell(val iv: ImageView, val lb: TextView)

    private val packagesChanged = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            // Package install/remove/update can change launcher activities and icons.
            loadApps()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        mode = intent.getStringExtra("mode") ?: "browse"

        val root = LinearLayout(this)
        root.orientation = LinearLayout.VERTICAL
        root.background = bgGradient()
        root.setPadding(px(40f), px(18f), px(40f), px(12f))

        val title = when (mode) {
            "pin" -> "➕ اختر التطبيقات التي تظهر في الرئيسية"
            "pick" -> "📺 اختر التطبيق"
            "search" -> "🔍 البحث عن تطبيق"
            "games" -> "🎮 الألعاب"
            "kids" -> "😊 الأطفال"
            else -> "🧩 كل التطبيقات"
        }
        // شريط علوي واحد مدمج ليترك أكبر مساحة ممكنة لشبكة التطبيقات.
        val top = LinearLayout(this)
        top.orientation = LinearLayout.HORIZONTAL
        top.gravity = Gravity.CENTER_VERTICAL
        top.addView(Ui.label(this, title, 36f, ut.text, true), Ui.lp(0, Ui.WRAP, 1f))
        if (mode != "pick" && mode != "search") {
            if (mode != "pin") {
                val pb = Ui.button(this, ut, "📌 إضافة للرئيسية: متوقف", 20f) { togglePinning() }
                pinBtn = pb
                top.addView(pb, Ui.lpm(Ui.WRAP, Ui.WRAP, 0f, px(12f), 0, 0, 0))
            }
            top.addView(Ui.button(this, ut, "⭐ المفضلة", 20f) {
                startActivity(Intent(this, FavoriteAppsSettingsActivity::class.java))
            }, Ui.lpm(Ui.WRAP, Ui.WRAP, 0f, px(12f), 0, 0, 0))
        }
        top.addView(Ui.button(this, ut, if (mode == "pin") "✔ تم" else "➜ رجوع", 20f) { finish() },
            Ui.lpm(Ui.WRAP, Ui.WRAP, 0f, px(12f), 0, 0, 0))
        root.addView(top, Ui.lp(Ui.MATCH, Ui.WRAP))

        status = Ui.label(this, "جارٍ فحص التطبيقات المثبتة...", 20f, ut.dim)
        root.addView(status, Ui.lpm(Ui.WRAP, Ui.WRAP, 0f, 0, px(6f), 0, px(6f)))

        search = Ui.edit(this, ut, "ابحث باسم التطبيق")
        root.addView(search, Ui.lpm(Ui.MATCH, Ui.WRAP, 0f, 0, 0, 0, px(6f)))
        search.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = filterApps()
            override fun afterTextChanged(s: Editable?) = Unit
        })

        grid = GridView(this)
        grid.numColumns = com.uobidah.launcher.core.CardGrid.appColumns((resources.displayMetrics.widthPixels / resources.displayMetrics.density).toInt())
        grid.stretchMode = GridView.STRETCH_COLUMN_WIDTH
        grid.verticalSpacing = px(10f)
        grid.horizontalSpacing = px(10f)
        grid.selector = Ui.selector(this, ut)
        grid.clipToPadding = false
        grid.setPadding(px(8f), px(8f), px(8f), px(16f))

        adapter = object : BaseAdapter() {
            override fun getCount(): Int = apps.size
            override fun getItem(position: Int): Any = apps[position]
            override fun getItemId(position: Int): Long = apps[position].pkg.hashCode().toLong()

            override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
                val a = apps[position]
                val pinned = prefs.pinnedApps.contains(a.pkg)
                var cell = convertView as? LinearLayout
                val holder: Cell
                if (cell == null) {
                    // إعادة استخدام الخلايا بدل بناء خلية جديدة لكل تطبيق (كان سبب البطء والتقطيع).
                    cell = LinearLayout(this@AppsActivity)
                    cell.orientation = LinearLayout.VERTICAL
                    cell.gravity = Gravity.CENTER
                    cell.isFocusable = true
                    cell.isClickable = true
                    cell.setPadding(px(6f), px(10f), px(6f), px(10f))
                    val iv = ImageView(this@AppsActivity)
                    iv.scaleType = ImageView.ScaleType.FIT_CENTER
                    cell.addView(iv, Ui.lp(px(88f), px(88f)))
                    val lb = Ui.label(this@AppsActivity, "", 21f, ut.text, true)
                    Ui.ellipsize(lb)
                    lb.gravity = Gravity.CENTER
                    cell.addView(lb, Ui.lpm(Ui.MATCH, Ui.WRAP, 0f, 0, px(6f), 0, 0))
                    Ui.focusable(cell, ut, 22f)
                    holder = Cell(iv, lb)
                    cell.tag = holder
                } else {
                    holder = cell.tag as Cell
                }
                holder.iv.setImageDrawable(a.icon ?: AppUtil.appIcon(packageManager, a.pkg))
                holder.lb.text = (if (pinned && mode != "pick") "📌 " else "") + a.label
                // ضغطة واحدة فقط (لا ضغط مطوّل): الإضافة للرئيسية تتم بنفس الضغطة في وضع الإضافة.
                cell.setOnClickListener { onTap(a) }
                return cell
            }
        }
        grid.adapter = adapter
        // الضغط والضغط المطوّل مربوطان داخل getView (انظر التعليق هناك).

        root.addView(grid, Ui.lp(Ui.MATCH, 0, 1f))
        if (mode != "search" && mode != "pick") {
            val saved = prefs.sp.getString("apps_query", "").orEmpty()
            if (saved.isNotEmpty()) search.setText(saved)
        }
        show(root)
        if (mode == "search") {
            search.post {
                search.requestFocus()
                try {
                    val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager
                    imm.showSoftInput(search, android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT)
                } catch (_: Exception) { }
            }
        } else {
            grid.requestFocus()
        }
        loadApps()
    }

    override fun onResume() {
        super.onResume()
        registerPackageReceiver()
    }

    override fun onPause() {
        if (mode != "search" && mode != "pick" && ::search.isInitialized) {
            prefs.sp.edit().putString("apps_query", search.text?.toString().orEmpty()).apply()
        }
        unregisterPackageReceiver()
        super.onPause()
    }

    override fun onDestroy() {
        unregisterPackageReceiver()
        super.onDestroy()
    }

    @Suppress("DEPRECATION")
    private fun registerPackageReceiver() {
        if (receiverRegistered) return
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED)
            addAction(Intent.ACTION_PACKAGE_REMOVED)
            addAction(Intent.ACTION_PACKAGE_CHANGED)
            addAction(Intent.ACTION_PACKAGE_REPLACED)
            addDataScheme("package")
        }
        try {
            if (Build.VERSION.SDK_INT >= 33) {
                registerReceiver(packagesChanged, filter, Context.RECEIVER_EXPORTED)
            } else {
                registerReceiver(packagesChanged, filter)
            }
            receiverRegistered = true
        } catch (_: Exception) {
            receiverRegistered = false
        }
    }

    private fun unregisterPackageReceiver() {
        if (!receiverRegistered) return
        try { unregisterReceiver(packagesChanged) } catch (_: Exception) { }
        receiverRegistered = false
    }

    /** Query TV launch activities first so a package's Leanback entry is preferred. */
    private fun loadApps() {
        status.text = "جارٍ تحديث قائمة التطبيقات..."
        val pm = packageManager
        Net.bg {
            val byPackage = LinkedHashMap<String, AppItem>()
            val categories = listOf(Intent.CATEGORY_LEANBACK_LAUNCHER, Intent.CATEGORY_LAUNCHER)
            for (category in categories) {
                val query = Intent(Intent.ACTION_MAIN).addCategory(category)
                val resolved: List<ResolveInfo> = try { pm.queryIntentActivities(query, 0) } catch (_: Exception) { emptyList() }
                for (ri in resolved) {
                    val ai = ri.activityInfo ?: continue
                    val pkg = ai.packageName ?: continue
                    if (pkg == packageName || !ai.enabled || !ai.exported || !ai.applicationInfo.enabled) continue
                    val isLeanback = category == Intent.CATEGORY_LEANBACK_LAUNCHER
                    val existing = byPackage[pkg]
                    if (existing != null && (existing.isLeanback || !isLeanback)) continue

                    val label = try { ri.loadLabel(pm)?.toString()?.takeIf { it.isNotBlank() } ?: pkg } catch (_: Exception) { pkg }
                    val icon: Drawable? = try {
                        ri.loadIcon(pm)
                    } catch (_: Exception) {
                        AppUtil.appIcon(pm, pkg)
                    }
                    val game = try {
                        val info = ai.applicationInfo
                        info.category == ApplicationInfo.CATEGORY_GAME || (info.flags and ApplicationInfo.FLAG_IS_GAME) != 0
                    } catch (_: Exception) { false }
                    byPackage[pkg] = AppItem(label, pkg, ai.name, icon, isLeanback, game)
                }
            }
            val result = byPackage.values.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.label })
            Net.ui {
                if (isFinishing || isDestroyed) return@ui
                allApps.clear()
                allApps.addAll(result)
                filterApps()
            }
        }
    }

    private fun filterApps() {
        if (!::search.isInitialized || !::adapter.isInitialized) return
        val query = search.text?.toString()?.trim()?.lowercase().orEmpty()
        apps.clear()
        val scoped = when (mode) {
            "games" -> allApps.filter { it.isGame }
            "kids" -> allApps.filter { isKidsApp(it) }
            else -> allApps
        }
        if (query.isEmpty()) {
            apps.addAll(scoped)
        } else {
            apps.addAll(scoped.filter { it.label.lowercase().contains(query) || it.pkg.lowercase().contains(query) })
        }
        adapter.notifyDataSetChanged()
        status.text = when {
            mode == "games" && apps.isEmpty() && query.isEmpty() -> "لا توجد ألعاب مثبتة على الجهاز."
            mode == "kids" && apps.isEmpty() && query.isEmpty() -> "لا توجد تطبيقات أطفال مثبتة (مثل YouTube Kids أو سبيستون)."
            allApps.isEmpty() -> "لم يتم العثور على تطبيقات لها واجهة تشغيل قابلة للفتح. تحقق من صلاحية ظهور التطبيقات في النظام."
            apps.isEmpty() -> "لا توجد تطبيقات تطابق البحث."
            mode == "pin" || pinning -> "${apps.size} تطبيق  •  ضغطة واحدة = إضافة أو إزالة من الرئيسية  •  📌 = ظاهر في الرئيسية"
            else -> "${apps.size} تطبيق  •  ضغطة = فتح  •  لإضافة تطبيق للرئيسية فعّل «إضافة للرئيسية»"
        }
    }

    private fun isKidsApp(a: AppItem): Boolean {
        val hay = (a.pkg + " " + a.label).lowercase()
        return KIDS_TOKENS.any { hay.contains(it) }
    }

    private fun onTap(a: AppItem) {
        when (mode) {
            "pick" -> {
                val result = Intent()
                result.putExtra("pkg", a.pkg)
                result.putExtra("activity", a.activityName)
                setResult(RESULT_OK, result)
                finish()
            }
            "pin" -> togglePin(a)
            else -> if (pinning) togglePin(a) else {
                val launch = AppUtil.launchIntent(packageManager, a.pkg, a.activityName)
                if (launch == null) {
                    toast("لم يعد نشاط التشغيل متاحًا. حدّث القائمة أو أعد تثبيت التطبيق.")
                    loadApps()
                    return
                }
                try {
                    startActivity(launch)
                } catch (_: android.content.ActivityNotFoundException) {
                    toast("لم يعد التطبيق يوفّر واجهة التشغيل المحددة.")
                    loadApps()
                } catch (_: SecurityException) {
                    toast("رفض النظام تشغيل هذا النشاط بسبب قيود الصلاحيات.")
                } catch (_: Exception) {
                    toast("تعذر تشغيل التطبيق على هذا النظام.")
                }
            }
        }
    }

    private fun togglePinning() {
        pinning = !pinning
        pinBtn?.text = if (pinning) "📌 إضافة للرئيسية: مفعّل" else "📌 إضافة للرئيسية: متوقف"
        filterApps()
    }

    private fun togglePin(a: AppItem) {
        val current = ArrayList<String>(prefs.pinnedApps)
        if (current.contains(a.pkg)) {
            current.remove(a.pkg)
            toast("تمت إزالة ${a.label} من الرئيسية")
        } else {
            current.add(a.pkg)
            toast("تم تثبيت ${a.label} في الرئيسية")
        }
        prefs.pinnedApps = current
        adapter.notifyDataSetChanged()
    }
}
