package com.uobidah.launcher

import android.app.AlertDialog
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.uobidah.launcher.core.Reciter
import com.uobidah.launcher.core.ReciterFilter

/** مكتبة القراء: بطاقات واسعة (شبكة/قائمة)، بحث بالاسم والرواية، تصفية بالرواية، المفضلة، ومتابعة آخر استماع. */
class RecitersActivity : BaseActivity() {
    private lateinit var qp: QuranPrefs
    private lateinit var rv: RecyclerView
    private lateinit var grid: GridLayoutManager
    private lateinit var count: TextView
    private lateinit var riwayaBtn: TextView
    private lateinit var favBtn: TextView
    private lateinit var viewBtn: TextView
    private lateinit var resumeBtn: TextView
    private lateinit var search: android.widget.EditText
    private var shown: List<Reciter> = emptyList()
    private val adapter = CardAdapter()
    private var restored = false
    private var statusMsg = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        qp = QuranPrefs(prefs)
        val root = LinearLayout(this)
        root.orientation = LinearLayout.VERTICAL
        root.background = bgGradient()
        root.setPadding(px(56f), px(26f), px(56f), px(22f))
        root.addView(header("🎧 القرّاء"))

        val bar = LinearLayout(this)
        bar.orientation = LinearLayout.HORIZONTAL
        bar.gravity = Gravity.CENTER_VERTICAL
        bar.clipChildren = false
        search = Ui.edit(this, ut, "ابحث باسم القارئ أو الرواية...")
        search.setText(qp.query)
        search.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun afterTextChanged(s: Editable?) { qp.query = s?.toString().orEmpty(); applyFilter(false) }
        })
        bar.addView(search, Ui.lp(0, Ui.WRAP, 1f))
        riwayaBtn = Ui.button(this, ut, "", 22f) { pickRiwaya() }
        favBtn = Ui.button(this, ut, "", 22f) { qp.favOnly = !qp.favOnly; updateButtons(); applyFilter(false) }
        viewBtn = Ui.button(this, ut, "", 22f) { qp.grid = !qp.grid; updateButtons(); applyLayout(); rv.adapter = adapter }
        resumeBtn = Ui.button(this, ut, "↪ متابعة الاستماع", 22f) { resumeLast() }
        val listsBtn = Ui.button(this, ut, "🎵 قوائمي", 22f) { startActivity(Intent(this, PlaylistsActivity::class.java)) }
        for (b in listOf(riwayaBtn, favBtn, viewBtn, listsBtn, resumeBtn)) bar.addView(b, Ui.lpm(Ui.WRAP, Ui.WRAP, 0f, px(10f), 0, 0, 0))
        root.addView(bar, Ui.lpm(Ui.MATCH, Ui.WRAP, 0f, 0, px(12f), 0, px(8f)))

        count = Ui.label(this, "جارٍ تحميل القراء من mp3quran.net ...", 21f, ut.dim)
        root.addView(count, Ui.lpm(Ui.MATCH, Ui.WRAP, 0f, px(6f), 0, 0, px(6f)))

        rv = RecyclerView(this)
        rv.clipChildren = false
        rv.clipToPadding = false
        rv.setItemViewCacheSize(12)
        grid = GridLayoutManager(this, 3)
        rv.layoutManager = grid
        rv.adapter = adapter
        root.addView(rv, Ui.lp(Ui.MATCH, 0, 1f))
        show(root)
        applyLayout()
        updateButtons()

        QuranRepo.load(this, false) { ok, msg ->
            statusMsg = if (msg == "cache" || msg == "network") "" else msg
            if (ok || QuranRepo.reciters.isNotEmpty()) applyFilter(true) else count.text = msg
        }
    }

    private fun applyLayout() {
        val dm = resources.displayMetrics
        val span = com.uobidah.launcher.core.CardGrid.span((dm.widthPixels / dm.density).toInt(), qp.grid)
        grid.spanCount = span
    }

    private fun updateButtons() {
        val r = qp.riwaya
        riwayaBtn.text = "🗂 الرواية: " + (if (r.isEmpty()) "الكل" else r.take(22))
        favBtn.text = if (qp.favOnly) "★ المفضلة فقط" else "☆ كل القراء"
        viewBtn.text = if (qp.grid) "☰ قائمة" else "▦ شبكة"
        resumeBtn.visibility = if (qp.lastListen() != null) View.VISIBLE else View.GONE
    }

    private fun pickRiwaya() {
        val all = ReciterFilter.allRiwayat(QuranRepo.reciters)
        val opts = listOf("الكل") + all
        AlertDialog.Builder(this).setTitle("اختر الرواية")
            .setItems(opts.toTypedArray()) { _, i ->
                qp.riwaya = if (i == 0) "" else all[i - 1]
                updateButtons(); applyFilter(false)
            }.show()
    }

    private fun applyFilter(restore: Boolean) {
        val all = QuranRepo.reciters
        shown = ReciterFilter.filter(all, qp.query, qp.riwaya, qp.favOnly, qp.favReciters())
        adapter.notifyDataSetChanged()
        count.text = (if (all.isEmpty()) "لا توجد بيانات" else "${shown.size} من ${all.size} قارئ") +
            (if (statusMsg.isNotEmpty()) "  •  $statusMsg" else "")
        updateButtons()
        if (restore && !restored && shown.isNotEmpty()) {
            restored = true
            val pos = qp.listPos.coerceIn(0, shown.size - 1)
            rv.post {
                grid.scrollToPositionWithOffset(pos, 0)
                val f = qp.listFocus
                if (f in shown.indices) rv.postDelayed({ rv.findViewHolderForAdapterPosition(f)?.itemView?.requestFocus() }, 150)
            }
        }
    }

    private fun resumeLast() {
        val l = qp.lastListen() ?: return
        val r = QuranRepo.byId(l.reciterId) ?: return toast("القارئ غير متاح في القائمة الحالية")
        val m = r.moshafs.firstOrNull { it.id == l.moshafId } ?: r.primary ?: return
        QuranPlayback.start(this, r, m, l.surah, l.positionMs)
        startActivity(Intent(this, ReciterDetailActivity::class.java).putExtra("reciterId", r.id))
    }

    override fun onPause() {
        qp.listPos = grid.findFirstVisibleItemPosition().coerceAtLeast(0)
        val fv = currentFocus
        qp.listFocus = if (fv != null) rv.findContainingViewHolder(fv)?.bindingAdapterPosition ?: -1 else -1
        super.onPause()
    }

    override fun onResume() {
        super.onResume()
        updateButtons()
        adapter.notifyDataSetChanged()
    }

    private fun playReciter(r: Reciter) {
        val l = qp.lastListen()
        val m = r.moshafs.firstOrNull { it.id == qp.lastMoshaf(r.id) } ?: r.primary ?: return
        val sameReciter = l != null && l.reciterId == r.id && l.moshafId == m.id && m.has(l.surah)
        QuranPlayback.start(this, r, m, if (sameReciter) l!!.surah else m.surahs.first(), if (sameReciter) l!!.positionMs else 0L)
        toast("▶ " + r.name)
    }

    private fun open(r: Reciter) {
        startActivity(Intent(this, ReciterDetailActivity::class.java).putExtra("reciterId", r.id))
    }

    private inner class VH(val card: LinearLayout) : RecyclerView.ViewHolder(card)

    private inner class CardAdapter : RecyclerView.Adapter<VH>() {
        override fun getItemCount() = shown.size

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val c = LinearLayout(this@RecitersActivity)
            c.layoutParams = RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            return VH(c)
        }

        override fun onBindViewHolder(h: VH, position: Int) {
            val r = shown[position]
            val list = !qp.grid
            val c = h.card
            c.removeAllViews()
            c.orientation = if (list) LinearLayout.HORIZONTAL else LinearLayout.VERTICAL
            c.gravity = if (list) Gravity.CENTER_VERTICAL else Gravity.CENTER_HORIZONTAL
            c.setPadding(px(22f), px(18f), px(22f), px(18f))
            val lp = c.layoutParams as RecyclerView.LayoutParams
            lp.setMargins(px(10f), px(10f), px(10f), px(10f))
            c.layoutParams = lp
            Ui.focusable(c, ut, 26f)
            c.isClickable = true
            c.setOnClickListener { open(r) }

            val size = if (list) 96 else 128
            c.addView(TeamBadgeUi.create(this@RecitersActivity, r.name, r.photo, size, ut.accent), Ui.lp(px(size.toFloat()), px(size.toFloat())))

            val texts = LinearLayout(this@RecitersActivity)
            texts.orientation = LinearLayout.VERTICAL
            texts.gravity = if (list) Gravity.START or Gravity.CENTER_VERTICAL else Gravity.CENTER_HORIZONTAL
            val name = Ui.label(this@RecitersActivity, r.name, 27f, ut.text, true)
            name.gravity = if (list) Gravity.START else Gravity.CENTER
            name.maxLines = 2
            texts.addView(name)
            val rw = r.riwayat.firstOrNull().orEmpty()
            val more = if (r.riwayat.size > 1) "  (+${r.riwayat.size - 1})" else ""
            val sub = Ui.label(this@RecitersActivity, rw + more, 19f, ut.dim)
            sub.gravity = if (list) Gravity.START else Gravity.CENTER
            sub.maxLines = 2
            texts.addView(sub, Ui.lpm(Ui.MATCH, Ui.WRAP, 0f, 0, px(4f), 0, 0))
            val avail = Ui.label(this@RecitersActivity, "${r.primary?.surahs?.size ?: 0} سورة", 17f, ut.accent)
            avail.gravity = if (list) Gravity.START else Gravity.CENTER
            texts.addView(avail, Ui.lpm(Ui.MATCH, Ui.WRAP, 0f, 0, px(2f), 0, 0))
            if (list) c.addView(texts, Ui.lpm(0, Ui.WRAP, 1f, px(20f), 0, px(16f), 0))
            else c.addView(texts, Ui.lpm(Ui.MATCH, Ui.WRAP, 0f, 0, px(10f), 0, px(10f)))

            val actions = LinearLayout(this@RecitersActivity)
            actions.orientation = LinearLayout.HORIZONTAL
            actions.gravity = Gravity.CENTER
            val play = Ui.button(this@RecitersActivity, ut, "▶ تشغيل", 22f) { playReciter(r) }
            val fav = Ui.button(this@RecitersActivity, ut, if (qp.favReciters().contains(r.id)) "♥" else "♡", 26f) {
                qp.toggleFavReciter(r.id)
                if (qp.favOnly) applyFilter(false) else notifyItemChanged(h.bindingAdapterPosition)
            }
            actions.addView(play, Ui.lpm(Ui.WRAP, Ui.WRAP, 0f, 0, 0, px(10f), 0))
            actions.addView(fav)
            c.addView(actions, if (list) Ui.lp(Ui.WRAP, Ui.WRAP) else Ui.lpm(Ui.WRAP, Ui.WRAP, 0f, 0, px(4f), 0, 0))
        }
    }
}
