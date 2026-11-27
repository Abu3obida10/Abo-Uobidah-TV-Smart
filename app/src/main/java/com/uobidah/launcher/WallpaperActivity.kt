package com.uobidah.launcher

import android.os.Bundle
import android.app.Dialog
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.GridView
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView

/**
 * شاشة الخلفيات: بحث في Pinterest حسب القسم أو بكلمات حرة، أو لصق رابط صورة / رابط Pin،
 * مع تفعيل التحديث اليومي التلقائي.
 */
class WallpaperActivity : BaseActivity() {

    private val results = ArrayList<String>()
    private lateinit var adapter: BaseAdapter
    private lateinit var grid: GridView
    private lateinit var status: TextView
    private lateinit var searchInput: EditText
    private lateinit var linkInput: EditText
    private var searchRequestId = 0L
    private var saving = false

    private val blockedWords = listOf(
        "sex", "nude", "naked", "bikini", "porn", "nsfw", "erotic", "lingerie",
        "عاري", "عارية", "اباح", "إباح", "جنس", "بكيني"
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val root = LinearLayout(this)
        root.orientation = LinearLayout.VERTICAL
        root.background = bgGradient()
        root.setPadding(px(56f), px(32f), px(56f), px(20f))
        val clubName = ut.club?.ar ?: "الخلفيات العامة"
        val appVer = try { packageManager.getPackageInfo(packageName, 0).versionName ?: "" } catch (_: Exception) { "" }
        root.addView(header("🖼️ خلفيات الشاشة • $clubName" + (if (appVer.isNotBlank()) " • v$appVer" else "")), Ui.lp(Ui.MATCH, Ui.WRAP))

        // أقسام جاهزة
        val cats = LinearLayout(this)
        cats.orientation = LinearLayout.HORIZONTAL
        cats.clipChildren = false
        cats.clipToPadding = false
        cats.setPadding(px(8f), px(8f), px(8f), px(8f))
        for (c in Wallpapers.categories) {
            val chip = Ui.chip(this, ut, c.first, false, 26f) { runSearch(c.second) }
            cats.addView(chip, Ui.lpm(Ui.WRAP, Ui.WRAP, 0f, 0, 0, px(14f), 0))
        }
        val savedBtn = Ui.chip(this, ut, "🗂 المحفوظة (${Wallpapers.gallery(this, prefs).size})", false, 26f) {
            showSavedGallery()
        }
        cats.addView(savedBtn, Ui.lpm(Ui.WRAP, Ui.WRAP, 0f, 0, 0, px(14f), 0))
        val clear = Ui.chip(this, ut, "🗑 إزالة الخلفية الحالية", false, 26f) {
            Wallpapers.clear(this, prefs)
            toast("تمت إزالة الخلفية الحالية مع الاحتفاظ بالصور المحفوظة")
            finish()
        }
        cats.addView(clear, Ui.lpm(Ui.WRAP, Ui.WRAP, 0f, 0, 0, px(14f), 0))
        val autoBtn = Ui.chip(this, ut, "", prefs.autoWallpaper, 26f) { }
        autoBtn.text = autoText()
        autoBtn.setOnClickListener {
            prefs.autoWallpaper = !prefs.autoWallpaper
            autoBtn.text = autoText()
            Ui.styleChip(autoBtn, ut, prefs.autoWallpaper)
            if (prefs.autoWallpaper) {
                prefs.setLastWallpaperDay(Wallpapers.scope(prefs), "")
                prefs.setLastWallpaperAttempt(Wallpapers.scope(prefs), 0L)
                val app = applicationContext
                Net.bg { Wallpapers.dailyUpdate(app, prefs) }
                toast("سيتم تغيير الخلفية تلقائياً كل يوم")
            }
        }
        cats.addView(autoBtn, Ui.lpm(Ui.WRAP, Ui.WRAP, 0f, 0, 0, px(14f), 0))
        val keyBtn = Ui.chip(this, ut, if (prefs.pexelsApiKey.isNotBlank()) "🔑 مفتاح Pexels ✓" else "🔑 مفتاح Pexels", false, 26f) { }
        keyBtn.setOnClickListener { askPexelsKey(keyBtn) }
        cats.addView(keyBtn)
        val hs = HorizontalScrollView(this)
        hs.isHorizontalScrollBarEnabled = false
        hs.clipChildren = false
        hs.clipToPadding = false
        hs.addView(cats, ViewGroup.LayoutParams(Ui.WRAP, Ui.WRAP))
        root.addView(hs, Ui.lp(Ui.MATCH, Ui.WRAP))

        // بحث حر
        searchInput = Ui.edit(this, ut, "ابحث عن خلفية في Wikimedia Commons ثم Pinterest: طبيعة، مساجد، سماء ...")
        searchInput.setText(prefs.wallpaperQuery(Wallpapers.scope(prefs)))
        searchInput.setOnEditorActionListener { _, _, _ ->
            runSearch(searchInput.text.toString())
            true
        }
        val row1 = LinearLayout(this)
        row1.orientation = LinearLayout.HORIZONTAL
        row1.gravity = android.view.Gravity.CENTER_VERTICAL
        row1.addView(searchInput, Ui.lpm(0, Ui.WRAP, 1f, 0, 0, px(14f), 0))
        row1.addView(Ui.button(this, ut, "🔍 بحث", 26f) { runSearch(searchInput.text.toString()) })
        root.addView(row1, Ui.lpm(Ui.MATCH, Ui.WRAP, 0f, 0, px(10f), 0, 0))

        // لصق رابط
        linkInput = Ui.edit(this, ut, "أو الصق رابط صورة مباشر / رابط Pin من Pinterest")
        linkInput.setOnEditorActionListener { _, _, _ ->
            applyLink()
            true
        }
        val row2 = LinearLayout(this)
        row2.orientation = LinearLayout.HORIZONTAL
        row2.gravity = android.view.Gravity.CENTER_VERTICAL
        row2.addView(linkInput, Ui.lpm(0, Ui.WRAP, 1f, 0, 0, px(14f), 0))
        row2.addView(Ui.button(this, ut, "✔ تعيين", 26f) { applyLink() })
        root.addView(row2, Ui.lpm(Ui.MATCH, Ui.WRAP, 0f, 0, px(10f), 0, 0))

        status = Ui.label(this, "اختر قسماً أو ابحث، ثم اضغط على صورة لتعيينها خلفية للشاشة الرئيسية", 24f, ut.dim)
        root.addView(status, Ui.lpm(Ui.WRAP, Ui.WRAP, 0f, 0, px(12f), 0, px(10f)))

        grid = GridView(this)
        grid.numColumns = 4
        grid.stretchMode = GridView.STRETCH_COLUMN_WIDTH
        grid.verticalSpacing = px(16f)
        grid.horizontalSpacing = px(16f)
        grid.selector = Ui.selector(this, ut)
        grid.clipToPadding = false
        grid.setPadding(px(8f), px(8f), px(8f), px(8f))
        adapter = object : BaseAdapter() {
            override fun getCount(): Int = results.size
            override fun getItem(position: Int): Any = results[position]
            override fun getItemId(position: Int): Long = position.toLong()
            override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
                val cell = (convertView as? FrameLayout) ?: FrameLayout(this@WallpaperActivity).apply {
                    setPadding(px(6f), px(6f), px(6f), px(6f))
                    addView(ImageView(this@WallpaperActivity), FrameLayout.LayoutParams(Ui.MATCH, px(230f)))
                    addView(Ui.label(this@WallpaperActivity, "✕ تعذر التحميل", 18f, ut.dim, true).apply {
                        gravity = android.view.Gravity.CENTER
                        visibility = View.GONE
                    }, FrameLayout.LayoutParams(Ui.MATCH, px(230f)))
                }
                val iv = cell.getChildAt(0) as ImageView
                val err = cell.getChildAt(1) as TextView
                err.visibility = View.GONE
                iv.scaleType = ImageView.ScaleType.CENTER_CROP
                iv.setBackgroundColor(ut.card)
                Img.load(this@WallpaperActivity, Wallpapers.thumb(results[position]), iv, 474, 266, onError = { err.visibility = View.VISIBLE })
                return cell
            }
        }
        grid.adapter = adapter
        grid.setOnItemClickListener { _, _, pos, _ ->
            if (pos >= 0 && pos < results.size) setWallpaper(Wallpapers.candidates(results[pos]))
        }
        root.addView(grid, Ui.lp(Ui.MATCH, 0, 1f))
        show(root)
    }

    private fun showSavedGallery() {
        val files = Wallpapers.gallery(this, prefs)
        if (files.isEmpty()) {
            toast("لا توجد خلفيات محفوظة لهذا الثيم بعد")
            return
        }
        val gallery = GridView(this)
        gallery.numColumns = 3
        gallery.horizontalSpacing = px(12f)
        gallery.verticalSpacing = px(12f)
        gallery.stretchMode = GridView.STRETCH_COLUMN_WIDTH
        gallery.selector = Ui.selector(this, ut)
        gallery.adapter = object : BaseAdapter() {
            override fun getCount(): Int = files.size
            override fun getItem(position: Int): Any = files[position]
            override fun getItemId(position: Int): Long = files[position].lastModified()
            override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
                val frame = (convertView as? FrameLayout) ?: FrameLayout(this@WallpaperActivity).apply {
                    setPadding(px(5f), px(5f), px(5f), px(5f))
                    addView(ImageView(this@WallpaperActivity), FrameLayout.LayoutParams(Ui.MATCH, px(210f)))
                }
                val image = frame.getChildAt(0) as ImageView
                image.scaleType = ImageView.ScaleType.CENTER_CROP
                image.setBackgroundColor(ut.card)
                Img.loadFile(files[position], image, 520, 300)
                return frame
            }
        }
        val dialog = Dialog(this, android.R.style.Theme_DeviceDefault_Dialog_NoActionBar)
        dialog.setContentView(gallery)
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        dialog.show()
        dialog.window?.setLayout(px(1350f), px(760f))
        gallery.setOnItemClickListener { _, _, position, _ ->
            if (Wallpapers.activate(this, prefs, files[position])) {
                dialog.dismiss()
                toast("تم تفعيل الخلفية المحفوظة")
                finish()
            } else {
                toast("تعذر فتح هذه الخلفية")
            }
        }
    }

    private fun autoText(): String =
        (if (prefs.autoWallpaper) "✅ " else "⬜ ") + "تحديث يومي تلقائي"

    private fun isBlocked(q: String): Boolean {
        val l = q.lowercase()
        for (w in blockedWords) if (l.contains(w)) return true
        return false
    }

    private fun runSearch(q: String) {
        val query = q.trim()
        if (query.isEmpty()) return
        if (isBlocked(query)) {
            toast("هذا البحث غير مناسب. جرّب: طبيعة، مساجد، سماء، ليل")
            return
        }
        prefs.setWallpaperQuery(Wallpapers.scope(prefs), query)
        val requestId = ++searchRequestId
        status.text = "جاري البحث في Wallhaven ثم Wikimedia Commons ثم Pinterest كاحتياط..."
        val queued = Net.submitBg {
            var outcome = Wallpapers.SearchOutcome(emptyList(), "", "")
            try {
                outcome = Wallpapers.searchDetailed(query, prefs.pexelsApiKey)
            } catch (e: Exception) {
            }
            val res = outcome.urls
            val diag = outcome.diagnostics
            val source = outcome.source
            Net.ui {
                if (requestId != searchRequestId || isFinishing) return@ui
                results.clear()
                results.addAll(res)
                adapter.notifyDataSetChanged()
                if (res.isEmpty()) {
                    status.text = "لم تظهر نتائج. جرّب عبارة أخرى أو الصق رابط صورة مباشر." + (if (diag.isNotEmpty()) " [$diag]" else "")
                } else {
                    status.text = "اضغط على صورة لتعيينها خلفية  (" + res.size + " صورة" + (if (source.isNotEmpty()) " • المصدر: $source" else "") + ")"
                    grid.requestFocus()
                }
            }
        }
        if (!queued && !isFinishing) status.text = "التطبيق مشغول الآن؛ أغلق عملية أخرى ثم أعد البحث."
    }

    private fun applyLink() {
        if (saving) return
        val text = linkInput.text.toString().trim()
        if (!text.startsWith("http")) {
            toast("الصق رابطاً صحيحاً يبدأ بـ http")
            return
        }
        saving = true
        status.text = "جاري تحميل الصورة..."
        Net.bg {
            val urls = try { Wallpapers.resolveInput(text) } catch (_: Exception) { emptyList() }
            doSave(urls)
        }
    }

    private fun setWallpaper(urls: List<String>) {
        if (saving) return
        saving = true
        status.text = "جاري تحميل الصورة بدقة عالية..."
        Net.bg { doSave(urls) }
    }

    private fun askPexelsKey(btn: TextView) {
        val input = EditText(this)
        input.hint = "مفتاح Pexels API (اتركه فارغًا للحذف)"
        input.setSingleLine(true)
        android.app.AlertDialog.Builder(this)
            .setTitle("مفتاح Pexels")
            .setMessage("يُحفظ مشفّرًا على الجهاز ولا يُرسل إلا إلى api.pexels.com. احصل عليه مجانًا من pexels.com/api")
            .setView(input)
            .setPositiveButton("حفظ") { _, _ ->
                prefs.pexelsApiKey = input.text.toString()
                btn.text = if (prefs.pexelsApiKey.isNotBlank()) "🔑 مفتاح Pexels ✓" else "🔑 مفتاح Pexels"
                toast(if (prefs.pexelsApiKey.isNotBlank()) "تم حفظ المفتاح" else "تم حذف المفتاح")
            }
            .setNegativeButton("إلغاء", null)
            .show()
    }

    private fun doSave(urls: List<String>) {
        var ok = false
        try {
            ok = urls.isNotEmpty() && Wallpapers.saveFromUrls(applicationContext, prefs, urls)
        } catch (e: Exception) {
        }
        Net.ui {
            saving = false
            if (ok) {
                val credit = urls.firstNotNullOfOrNull { Wallpapers.creditFor(it) }
                status.text = "✔ تم حفظ الخلفية وتفعيلها لهذا الثيم." + (if (credit != null) " الصورة: $credit." else "") + " يمكنك حفظ صور أخرى والرجوع إليها من المحفوظة."
                toast("تم حفظ الخلفية لهذا النادي")
            } else {
                status.text = "تعذر تحميل الصورة. جرّب صورة أخرى أو تأكد من الاتصال."
            }
        }
    }
}
