package com.uobidah.launcher

import android.app.AlertDialog
import android.os.Bundle
import android.view.Gravity
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView

/** قوائم التشغيل المسماة: إنشاء، تشغيل الكل، حذف، وعرض عناصر كل قائمة (قارئ • سورة) مع التشغيل من عنصر معيّن. */
class PlaylistsActivity : BaseActivity() {
    private lateinit var qp: QuranPrefs
    private lateinit var body: LinearLayout
    private var openList: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        qp = QuranPrefs(prefs)
        openList = savedInstanceState?.getString("open") ?: intent.getStringExtra("open")
        val root = LinearLayout(this)
        root.orientation = LinearLayout.VERTICAL
        root.background = bgGradient()
        root.setPadding(px(56f), px(26f), px(56f), px(20f))
        root.addView(header("🎵 قوائم التشغيل"))
        val sc = ScrollView(this)
        sc.isVerticalScrollBarEnabled = false
        sc.clipChildren = true
        sc.clipToPadding = false
        body = LinearLayout(this)
        body.orientation = LinearLayout.VERTICAL
        body.clipChildren = false
        sc.addView(body, android.view.ViewGroup.LayoutParams(Ui.MATCH, Ui.WRAP))
        root.addView(sc, Ui.lp(Ui.MATCH, 0, 1f))
        show(root)
        render()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        openList?.let { outState.putString("open", it) }
    }

    /** الرجوع من داخل قائمة يعود لقائمة القوائم أولاً. */
    @Suppress("OVERRIDE_DEPRECATION", "DEPRECATION")
    override fun onBackPressed() {
        if (openList != null) { openList = null; render() } else super.onBackPressed()
    }

    private fun row(): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setPadding(px(24f), px(18f), px(24f), px(18f))
    }

    private fun render() {
        body.removeAllViews()
        val open = openList
        if (open == null) renderLists() else renderItems(open)
    }

    private fun renderLists() {
        body.addView(Ui.button(this, ut, "＋ قائمة جديدة", 26f) { newListDialog { render() } },
            Ui.lpm(Ui.WRAP, Ui.WRAP, 0f, 0, px(8f), 0, px(10f)))
        val names = qp.playlistNames()
        if (names.isEmpty()) {
            body.addView(Ui.label(this, "لا توجد قوائم بعد. أضف سورة من صفحة أي قارئ بزر ＋", 24f, ut.dim))
            return
        }
        for (n in names) {
            val items = QuranPlaylists.resolve(qp.playlistItems(n))
            val r = row()
            Ui.focusable(r, ut, 24f)
            r.isClickable = true
            r.setOnClickListener { openList = n; render() }
            r.addView(Ui.label(this, "🎵 $n", 28f, ut.text, true), Ui.lp(0, Ui.WRAP, 1f))
            r.addView(Ui.label(this, "${items.size} سورة", 21f, ut.dim), Ui.lpm(Ui.WRAP, Ui.WRAP, 0f, px(12f), 0, px(12f), 0))
            r.addView(Ui.button(this, ut, "▶ تشغيل", 22f) {
                if (items.isEmpty()) toast("القائمة فارغة") else QuranPlaylists.play(this, items, 0)
            }, Ui.lpm(Ui.WRAP, Ui.WRAP, 0f, 0, 0, px(10f), 0))
            r.addView(Ui.button(this, ut, "🗑", 22f) {
                AlertDialog.Builder(this).setTitle("حذف القائمة «$n»؟")
                    .setPositiveButton("حذف") { _, _ -> qp.deletePlaylist(n); render() }
                    .setNegativeButton("إلغاء", null).show()
            })
            body.addView(r, Ui.lpm(Ui.MATCH, Ui.WRAP, 0f, px(4f), px(7f), px(4f), px(7f)))
        }
    }

    private fun renderItems(name: String) {
        val keys = qp.playlistItems(name)
        val items = QuranPlaylists.resolve(keys)
        body.addView(Ui.label(this, "🎵 $name  •  ${items.size} سورة", 30f, ut.accent, true), Ui.lpm(Ui.WRAP, Ui.WRAP, 0f, px(4f), px(6f), 0, px(8f)))
        body.addView(Ui.button(this, ut, "▶ تشغيل الكل", 26f) {
            if (items.isEmpty()) toast("القائمة فارغة") else QuranPlaylists.play(this, items, 0)
        }, Ui.lpm(Ui.WRAP, Ui.WRAP, 0f, 0, 0, 0, px(10f)))
        if (items.isEmpty()) body.addView(Ui.label(this, "لا عناصر صالحة في هذه القائمة", 24f, ut.dim))
        items.forEachIndexed { idx, e ->
            val r = row()
            Ui.focusable(r, ut, 22f)
            r.isClickable = true
            r.setOnClickListener { QuranPlaylists.play(this, items, idx) }
            r.addView(Ui.label(this, "${idx + 1}", 22f, ut.dim, true), Ui.lpm(px(48f), Ui.WRAP, 0f, 0, 0, px(10f), 0))
            val t = LinearLayout(this); t.orientation = LinearLayout.VERTICAL
            t.addView(Ui.label(this, QuranRepo.surahName(e.surah), 27f, ut.text, true))
            t.addView(Ui.label(this, e.reciter.name + "  •  " + e.moshaf.name, 19f, ut.dim))
            r.addView(t, Ui.lp(0, Ui.WRAP, 1f))
            r.addView(Ui.button(this, ut, "✕", 22f) { qp.removeFromPlaylist(name, e.key); render() })
            body.addView(r, Ui.lpm(Ui.MATCH, Ui.WRAP, 0f, px(4f), px(6f), px(4f), px(6f)))
        }
    }

    private fun newListDialog(done: (String) -> Unit) {
        val et = EditText(this)
        et.hint = "اسم القائمة"
        AlertDialog.Builder(this).setTitle("قائمة جديدة").setView(et)
            .setPositiveButton("إنشاء") { _, _ ->
                val n = et.text.toString().trim()
                if (n.isNotEmpty()) { qp.createPlaylist(n); done(n) }
            }.setNegativeButton("إلغاء", null).show()
    }
}
