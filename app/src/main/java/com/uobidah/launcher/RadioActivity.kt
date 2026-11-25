package com.uobidah.launcher

import android.app.Dialog
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.TextWatcher
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.TextView
import org.json.JSONObject
import java.io.File

class Station(val name: String, val url: String)

/** إذاعات القرآن الكريم والسنة والفتاوى (MP3Quran API) — تعمل في الخلفية. */
class RadioActivity : BaseActivity() {

    private val all = ArrayList<Station>()
    private val shown = ArrayList<Station>()
    private var featuredOnly = true
    private var query = ""
    private lateinit var list: ListView
    private lateinit var adapter: android.widget.ArrayAdapter<String>
    private lateinit var tvNow: TextView
    private lateinit var tvStatus: TextView
    private lateinit var chipFeatured: TextView
    private lateinit var chipAll: TextView
    private val handler = Handler(Looper.getMainLooper())

    private val keywords = listOf("مكة", "المكرمة", "القاهرة", "باز", "عثيمين", "فتاوى", "السنة", "الحرم", "المسجد")

    private val poll = object : Runnable {
        override fun run() {
            val n = RadioService.currentName
            tvNow.text = if (n.isEmpty()) "لا توجد إذاعة تعمل الآن" else "🔊 $n"
            tvStatus.text = when (RadioService.state) {
                "loading" -> "جاري الاتصال بالبث..."
                "playing" -> "يعمل الآن (يستمر في الخلفية)"
                "error" -> "تعذر تشغيل هذه الإذاعة، جرّب غيرها"
                else -> ""
            }
            handler.postDelayed(this, 1000)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = LinearLayout(this)
        root.orientation = LinearLayout.VERTICAL
        root.background = bgGradient()
        root.setPadding(px(56f), px(32f), px(56f), px(24f))
        root.addView(header("📻 إذاعات القرآن والسنة"), Ui.lp(Ui.MATCH, Ui.WRAP))

        val body = LinearLayout(this)
        body.orientation = LinearLayout.HORIZONTAL

        // العمود الأيمن: القائمة
        val left = LinearLayout(this)
        left.orientation = LinearLayout.VERTICAL
        val chips = LinearLayout(this)
        chips.orientation = LinearLayout.HORIZONTAL
        featuredOnly = prefs.radioFeaturedOnly
        chipFeatured = Ui.chip(this, ut, "⭐ المختارة", featuredOnly, 26f) {
            featuredOnly = true
            prefs.radioFeaturedOnly = true
            refreshChips()
            applyFilter()
        }
        chipAll = Ui.chip(this, ut, "📚 الكل", !featuredOnly, 26f) {
            featuredOnly = false
            prefs.radioFeaturedOnly = false
            refreshChips()
            applyFilter()
        }
        chips.addView(chipFeatured, Ui.lpm(Ui.WRAP, Ui.WRAP, 0f, 0, 0, px(12f), 0))
        chips.addView(chipAll)
        left.addView(chips, Ui.lpm(Ui.WRAP, Ui.WRAP, 0f, 0, px(12f), 0, px(10f)))
        val search = Ui.edit(this, ut, "ابحث عن إذاعة...")
        search.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, st: Int, c: Int, a: Int) {}
            override fun onTextChanged(s: CharSequence?, st: Int, b: Int, c: Int) {}
            override fun afterTextChanged(s: Editable?) {
                query = s?.toString()?.trim() ?: ""
                applyFilter()
            }
        })
        left.addView(search, Ui.lpm(Ui.MATCH, Ui.WRAP, 0f, 0, 0, 0, px(10f)))

        list = ListView(this)
        list.divider = null
        list.selector = Ui.selector(this, ut)
        adapter = Ui.listAdapter(this, ut)
        list.adapter = adapter
        list.setOnItemClickListener { _, _, pos, _ ->
            if (pos >= 0 && pos < shown.size) {
                val st = shown[pos]
                RadioService.play(this, st.name, st.url)
            }
        }
        list.setOnItemLongClickListener { _, _, pos, _ ->
            if (pos in shown.indices && shown[pos].name.startsWith("⭐")) editCustomStation(shown[pos])
            else toast("المحطات الأصلية تُدار من مصدرها. يمكنك إضافة محطة خاصة بك وإدارتها بالضغط المطوّل.")
            true
        }
        left.addView(list, Ui.lp(Ui.MATCH, 0, 1f))
        body.addView(left, Ui.lpm(0, Ui.MATCH, 1.3f, 0, 0, px(30f), 0))

        // العمود الأيسر: الحالة والتحكم
        val right = LinearLayout(this)
        right.orientation = LinearLayout.VERTICAL
        Ui.staticCard(right, ut, 34f)
        right.setPadding(px(36f), px(30f), px(36f), px(30f))
        right.addView(Ui.label(this, "الآن", 28f, ut.dim, true))
        tvNow = Ui.label(this, "", 40f, ut.accent, true)
        tvStatus = Ui.label(this, "", 26f, ut.text)
        right.addView(tvNow, Ui.lpm(Ui.MATCH, Ui.WRAP, 0f, 0, px(8f), 0, px(8f)))
        right.addView(tvStatus, Ui.lpm(Ui.MATCH, Ui.WRAP, 0f, 0, 0, 0, px(24f)))
        right.addView(Ui.button(this, ut, "⏹ إيقاف الإذاعة", 28f) { RadioService.stop(this) }, Ui.lpm(Ui.MATCH, Ui.WRAP, 0f, 0, 0, 0, px(14f)))
        right.addView(Ui.button(this, ut, "➕ إضافة إذاعة برابط", 28f) { addStationDialog() }, Ui.lpm(Ui.MATCH, Ui.WRAP, 0f, 0, 0, 0, px(14f)))
        right.addView(Ui.button(this, ut, "🔄 تحديث القائمة", 28f) { loadStations(true) }, Ui.lpm(Ui.MATCH, Ui.WRAP, 0f, 0, 0, 0, px(14f)))
        right.addView(Ui.label(this, "تستمر الإذاعة بالعمل حتى لو خرجت من هذه الشاشة أو فتحت تطبيقاً آخر.", 22f, ut.dim))
        body.addView(right, Ui.lp(0, Ui.MATCH, 1f))

        root.addView(body, Ui.lp(Ui.MATCH, 0, 1f))
        show(root)
        list.requestFocus()
        loadStations(false)
    }

    override fun onResume() {
        super.onResume()
        handler.post(poll)
    }

    override fun onPause() {
        handler.removeCallbacks(poll)
        super.onPause()
    }

    private fun refreshChips() {
        Ui.styleChip(chipFeatured, ut, featuredOnly)
        Ui.styleChip(chipAll, ut, !featuredOnly)
    }

    private fun cacheFile(): File = File(filesDir, "radios.json")

    private fun parse(text: String): List<Station> {
        val out = ArrayList<Station>()
        val arr = JSONObject(text).optJSONArray("radios") ?: return out
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            val n = o.optString("name", "").trim()
            val u = o.optString("url", "").trim()
            if (n.isNotEmpty() && u.startsWith("http")) out.add(Station(n, u))
        }
        return out
    }

    private fun loadStations(force: Boolean) {
        val f = cacheFile()
        Net.bg {
            val loaded = ArrayList<Station>()
            // 1) محطات المستخدم
            for (c in prefs.customStations) {
                val parts = c.split("||")
                if (parts.size == 2) loaded.add(Station("⭐ " + parts[0], parts[1]))
            }
            // 2) إذاعة القرآن الكريم من القاهرة (احتياطي دائم)
            loaded.add(Station("إذاعة القرآن الكريم من القاهرة", "https://stream.radiojar.com/8s5u5tpdtwzuv"))
            // 3) الكاش ثم الشبكة
            var text: String? = null
            if (f.exists()) {
                try { text = f.readText() } catch (e: Exception) { }
            }
            val stale = !f.exists() || force || System.currentTimeMillis() - f.lastModified() > 12 * 3600_000L
            if (text != null) {
                try { loaded.addAll(parse(text)) } catch (e: Exception) { }
            }
            Net.ui {
                all.clear()
                all.addAll(loaded)
                applyFilter()
            }
            if (stale) {
                try {
                    val fresh = Net.getString("https://www.mp3quran.net/api/v3/radios?language=ar")
                    val parsed = parse(fresh)
                    if (parsed.isNotEmpty()) {
                        f.writeText(fresh)
                        val merged = ArrayList<Station>()
                        for (c in prefs.customStations) {
                            val parts = c.split("||")
                            if (parts.size == 2) merged.add(Station("⭐ " + parts[0], parts[1]))
                        }
                        merged.add(Station("إذاعة القرآن الكريم من القاهرة", "https://stream.radiojar.com/8s5u5tpdtwzuv"))
                        merged.addAll(parsed)
                        Net.ui {
                            all.clear()
                            all.addAll(merged)
                            applyFilter()
                        }
                    }
                } catch (e: Exception) {
                    Net.ui { if (all.size <= 1) toast("تعذر تحديث قائمة الإذاعات، تأكد من الإنترنت") }
                }
            }
        }
    }

    private fun applyFilter() {
        shown.clear()
        for (s in all) {
            val isCustom = s.name.startsWith("⭐")
            if (query.isNotEmpty()) {
                if (s.name.contains(query, true)) shown.add(s)
            } else if (!featuredOnly || isCustom || keywords.any { s.name.contains(it) } || s.name.contains("القاهرة")) {
                shown.add(s)
            }
        }
        adapter.clear()
        for (s in shown) adapter.add(s.name)
        adapter.notifyDataSetChanged()
    }

    private fun editCustomStation(station: Station) {
        val saved = prefs.customStations.firstOrNull { it.substringAfter("||", "") == station.url } ?: return
        val name = Ui.edit(this, ut, "اسم الإذاعة")
        name.setText(saved.substringBefore("||"))
        val url = Ui.edit(this, ut, "رابط البث http://...")
        url.setText(station.url)
        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        box.addView(name, Ui.lpm(Ui.MATCH, Ui.WRAP, 0f, 0, 0, 0, px(10f)))
        box.addView(url)
        Ui.modal(this, ut, "⚙ تعديل الإذاعة المخصصة", box,
            Btn("✔ حفظ") {
                val n = name.text.toString().trim()
                val u = url.text.toString().trim()
                if (n.isEmpty() || !(u.startsWith("http://") || u.startsWith("https://"))) {
                    toast("أدخل اسماً ورابط بث صحيحاً")
                } else {
                    val rows = prefs.customStations.filterNot { it == saved }.toMutableList()
                    if (rows.any { it.substringAfter("||", "") == u }) {
                        toast("هذا الرابط محفوظ بالفعل")
                    } else {
                        rows.add(n + "||" + u)
                        prefs.customStations = rows
                        loadStations(false)
                        toast("تم تحديث الإذاعة")
                    }
                }
            },
            Btn("🗑 حذف") {
                prefs.customStations = prefs.customStations.filterNot { it == saved }
                loadStations(false)
                toast("تم حذف الإذاعة المخصصة")
            },
            Btn("إلغاء") {})
    }

    private fun addStationDialog() {
        val box = LinearLayout(this)
        box.orientation = LinearLayout.VERTICAL
        val name = Ui.edit(this, ut, "اسم الإذاعة")
        val url = Ui.edit(this, ut, "رابط البث http://...")
        box.addView(name, Ui.lpm(Ui.MATCH, Ui.WRAP, 0f, 0, 0, 0, px(12f)))
        box.addView(url)
        var d: Dialog? = null
        d = Ui.modal(this, ut, "➕ إضافة إذاعة", box,
            Btn("✔ حفظ") {
                val n = name.text.toString().trim()
                val u = url.text.toString().trim()
                if (n.isNotEmpty() && u.startsWith("http")) {
                    val cur = ArrayList<String>(prefs.customStations)
                    if (cur.any { it.substringAfter("||", "") == u }) {
                        toast("هذا الرابط محفوظ بالفعل")
                    } else {
                        cur.add(n + "||" + u)
                        prefs.customStations = cur
                        loadStations(false)
                    }
                } else {
                    toast("أدخل اسماً ورابطاً صحيحاً يبدأ بـ http")
                }
            },
            Btn("إلغاء") {})
    }
}
