package com.uobidah.launcher

import android.app.AlertDialog
import android.content.Context
import android.media.AudioManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.uobidah.launcher.core.ArabicSearch
import com.uobidah.launcher.core.Moshaf
import com.uobidah.launcher.core.Reciter
import java.util.Locale

/** سور قارئ واحد: بحث، تشغيل متتابع، مفضلة، تنزيل للاستماع دون إنترنت، وشريط تحكم بالتشغيل. */
class ReciterDetailActivity : BaseActivity() {
    private lateinit var qp: QuranPrefs
    private var reciter: Reciter? = null
    private var moshaf: Moshaf? = null
    private var query = ""
    private var shown: List<Int> = emptyList()
    private lateinit var rv: RecyclerView
    private lateinit var lm: LinearLayoutManager
    private lateinit var moshafBtn: TextView
    private lateinit var favBtn: TextView
    private lateinit var info: TextView
    private lateinit var holder: LinearLayout
    private lateinit var nowTitle: TextView
    private lateinit var nowTime: TextView
    private lateinit var playBtn: TextView
    private lateinit var repeatBtn: TextView
    private lateinit var playerBar: LinearLayout
    private val handler = Handler(Looper.getMainLooper())
    private val adapter = SurahAdapter()
    private var lastShownSurah = -2

    private val poll = object : Runnable {
        override fun run() {
            updateBar()
            handler.postDelayed(this, 1000)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        qp = QuranPrefs(prefs)
        holder = LinearLayout(this)
        holder.orientation = LinearLayout.VERTICAL
        holder.background = bgGradient()
        holder.setPadding(px(56f), px(26f), px(56f), px(20f))
        show(holder)
        val id = intent.getIntExtra("reciterId", 0)
        if (QuranRepo.reciters.isNotEmpty()) build(id)
        else {
            holder.addView(header("🎧 القرّاء"))
            holder.addView(Ui.label(this, "جارٍ التحميل...", 26f, ut.dim))
            QuranRepo.load(this, false) { ok, msg ->
                if (QuranRepo.reciters.isNotEmpty() && reciter == null) { holder.removeAllViews(); build(id) }
                else if (!ok) toast(msg)
            }
        }
    }

    private fun build(id: Int) {
        val r = QuranRepo.byId(id)
        if (r == null) { holder.addView(header("🎧 القرّاء")); holder.addView(Ui.label(this, "القارئ غير موجود", 26f, ut.dim)); return }
        reciter = r
        moshaf = r.moshafs.firstOrNull { it.id == qp.lastMoshaf(r.id) } ?: r.primary
        holder.addView(header(r.name))

        val top = LinearLayout(this)
        top.orientation = LinearLayout.HORIZONTAL
        top.gravity = Gravity.CENTER_VERTICAL
        top.clipChildren = false
        top.addView(TeamBadgeUi.create(this, r.name, r.photo, 96, ut.accent), Ui.lp(px(96f), px(96f)))
        val col = LinearLayout(this)
        col.orientation = LinearLayout.VERTICAL
        info = Ui.label(this, "", 21f, ut.dim)
        col.addView(info)
        top.addView(col, Ui.lpm(0, Ui.WRAP, 1f, px(18f), 0, px(12f), 0))
        moshafBtn = Ui.button(this, ut, "", 22f) { pickMoshaf() }
        moshafBtn.visibility = if (r.moshafs.size > 1) View.VISIBLE else View.GONE
        top.addView(moshafBtn, Ui.lpm(Ui.WRAP, Ui.WRAP, 0f, 0, 0, px(10f), 0))
        favBtn = Ui.button(this, ut, "", 24f) { qp.toggleFavReciter(r.id); updateTop() }
        top.addView(favBtn, Ui.lpm(Ui.WRAP, Ui.WRAP, 0f, 0, 0, px(10f), 0))
        top.addView(Ui.button(this, ut, "▶ تشغيل الكل", 24f) { moshaf?.let { m -> QuranPlayback.start(this, r, m, m.surahs.first()) } })
        holder.addView(top, Ui.lpm(Ui.MATCH, Ui.WRAP, 0f, 0, px(8f), 0, px(8f)))

        val search = Ui.edit(this, ut, "ابحث باسم السورة أو رقمها...")
        search.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun afterTextChanged(s: Editable?) { query = s?.toString().orEmpty(); refreshList() }
        })
        holder.addView(search, Ui.lpm(Ui.MATCH, Ui.WRAP, 0f, 0, 0, 0, px(5f)))
        holder.addView(Ui.label(this,
            "تنبيه: لا تتوفر بيانات ترخيص منفصلة لكل تسجيل عبر الواجهة؛ تابع التنزيل فقط بعد التأكد من سماح شروط المصدر بذلك، ولا تعِد توزيع الملفات دون إذن.",
            16f, ut.dim), Ui.lpm(Ui.MATCH, Ui.WRAP, 0f, px(4f), 0, 0, px(8f)))

        rv = RecyclerView(this)
        lm = LinearLayoutManager(this)
        rv.layoutManager = lm
        rv.clipChildren = false
        rv.clipToPadding = false
        rv.adapter = adapter
        holder.addView(rv, Ui.lp(Ui.MATCH, 0, 1f))

        playerBar = LinearLayout(this)
        playerBar.orientation = LinearLayout.HORIZONTAL
        playerBar.gravity = Gravity.CENTER_VERTICAL
        playerBar.setPadding(px(18f), px(10f), px(18f), px(10f))
        playerBar.background = Ui.round(Ui.withAlpha(ut.accent, 40), px(22f).toFloat(), px(2f), Ui.withAlpha(ut.accent, 140))
        val titles = LinearLayout(this)
        titles.orientation = LinearLayout.VERTICAL
        nowTitle = Ui.label(this, "", 24f, ut.text, true); Ui.ellipsize(nowTitle)
        nowTime = Ui.label(this, "", 18f, ut.dim)
        titles.addView(nowTitle); titles.addView(nowTime)
        playerBar.addView(titles, Ui.lpm(0, Ui.WRAP, 1f, 0, 0, px(12f), 0))
        fun ctl(t: String, size: Float = 26f, f: () -> Unit): TextView =
            Ui.button(this, ut, t, size) { f() }.also { b -> b.setPadding(px(18f), px(10f), px(18f), px(10f)); playerBar.addView(b, Ui.lpm(Ui.WRAP, Ui.WRAP, 0f, px(4f), 0, px(4f), 0)) }
        ctl("⏮") { QuranAudioService.command(this, QuranAudioService.ACTION_PREV) }
        playBtn = ctl("⏯") { QuranAudioService.command(this, QuranAudioService.ACTION_TOGGLE) }
        ctl("⏭") { QuranAudioService.command(this, QuranAudioService.ACTION_NEXT) }
        repeatBtn = ctl("🔁", 22f) { QuranAudioService.command(this, QuranAudioService.ACTION_REPEAT) }
        ctl("🔉", 22f) { vol(-1) }
        ctl("🔊", 22f) { vol(1) }
        ctl("⏹", 22f) { QuranAudioService.command(this, QuranAudioService.ACTION_STOP) }
        playerBar.visibility = View.GONE
        holder.addView(playerBar, Ui.lpm(Ui.MATCH, Ui.WRAP, 0f, 0, px(8f), 0, 0))

        updateTop()
        refreshList()
        // العودة لنفس موضع السور
        val pos = prefs.sp.getInt("quran_detail_pos_" + r.id, 0)
        rv.post { lm.scrollToPositionWithOffset(pos.coerceIn(0, (shown.size - 1).coerceAtLeast(0)), 0) }
    }

    /** يضيف السورة لقائمة موجودة أو لقائمة جديدة. */
    private fun addToPlaylist(r: Reciter, m: Moshaf, s: Int) {
        val names = qp.playlistNames()
        val opts = names + "＋ قائمة جديدة"
        AlertDialog.Builder(this).setTitle("إضافة «" + QuranRepo.surahName(s) + "» إلى قائمة")
            .setItems(opts.toTypedArray()) { _, i ->
                if (i < names.size) {
                    toast(if (qp.addToPlaylist(names[i], r.id, m.id, s)) "تمت الإضافة إلى " + names[i] else "موجودة بالفعل في " + names[i])
                } else {
                    val et = android.widget.EditText(this)
                    et.hint = "اسم القائمة"
                    AlertDialog.Builder(this).setTitle("قائمة جديدة").setView(et)
                        .setPositiveButton("إنشاء") { _, _ ->
                            val n = et.text.toString().trim()
                            if (n.isNotEmpty()) { qp.createPlaylist(n); qp.addToPlaylist(n, r.id, m.id, s); toast("تمت الإضافة إلى $n") }
                        }.setNegativeButton("إلغاء", null).show()
                }
            }.show()
    }

    private fun vol(dir: Int) {
        val am = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        am.adjustStreamVolume(AudioManager.STREAM_MUSIC, if (dir > 0) AudioManager.ADJUST_RAISE else AudioManager.ADJUST_LOWER, AudioManager.FLAG_SHOW_UI)
    }

    private fun updateTop() {
        val r = reciter ?: return
        val m = moshaf ?: return
        favBtn.text = if (qp.favReciters().contains(r.id)) "♥ في المفضلة" else "♡ للمفضلة"
        moshafBtn.text = "📖 " + m.name.take(26)
        info.text = m.name + "  •  " + m.surahs.size + " سورة متاحة"
    }

    private fun pickMoshaf() {
        val r = reciter ?: return
        AlertDialog.Builder(this).setTitle("اختر الرواية")
            .setItems(r.moshafs.map { it.name + " (" + it.surahs.size + ")" }.toTypedArray()) { _, i ->
                moshaf = r.moshafs[i]
                qp.setLastMoshaf(r.id, r.moshafs[i].id)
                updateTop(); refreshList()
            }.show()
    }

    private fun refreshList() {
        val m = moshaf ?: return
        shown = m.surahs.filter { s ->
            ArabicSearch.matches(query, QuranRepo.surahName(s), s.toString())
        }
        adapter.notifyDataSetChanged()
    }

    private fun fmt(ms: Long): String {
        val s = (ms / 1000).toInt()
        return String.format(Locale.US, "%d:%02d", s / 60, s % 60)
    }

    private fun updateBar() {
        val r = reciter ?: return
        val svc = QuranAudioService
        if (!svc.active) {
            if (playerBar.visibility != View.GONE) playerBar.visibility = View.GONE
            return
        }
        playerBar.visibility = View.VISIBLE
        nowTitle.text = (if (svc.surahName.isNotEmpty()) svc.surahName else "—") + "  •  " + svc.reciterName
        nowTime.text = if (svc.error.isNotEmpty()) svc.error else if (svc.loading) "جارٍ التحميل..." else fmt(svc.positionMs) + " / " + fmt(svc.durationMs)
        playBtn.text = if (svc.playing) "⏸" else "▶"
        repeatBtn.text = when (svc.repeatMode) { 1 -> "🔂 سورة"; 2 -> "🔁 الكل"; else -> "🔁" }
        // نحدّث القائمة فقط عند تغيّر السورة الحالية (تحديث كل ثانية يضيّع التركيز بالريموت)
        val key = if (svc.reciterId == r.id) svc.surah else -1
        if (key != lastShownSurah) { lastShownSurah = key; adapter.notifyDataSetChanged() }
    }

    override fun onResume() {
        super.onResume()
        handler.removeCallbacks(poll)
        handler.post(poll)
    }

    override fun onPause() {
        handler.removeCallbacks(poll)
        if (::lm.isInitialized) reciter?.let { prefs.sp.edit().putInt("quran_detail_pos_" + it.id, lm.findFirstVisibleItemPosition().coerceAtLeast(0)).apply() }
        super.onPause()
    }

    private inner class VH(val row: LinearLayout) : RecyclerView.ViewHolder(row)

    private inner class SurahAdapter : RecyclerView.Adapter<VH>() {
        override fun getItemCount() = shown.size

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val c = LinearLayout(this@ReciterDetailActivity)
            c.layoutParams = RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            return VH(c)
        }

        override fun onBindViewHolder(h: VH, position: Int) {
            val r = reciter ?: return
            val m = moshaf ?: return
            val s = shown[position]
            val row = h.row
            row.removeAllViews()
            row.orientation = LinearLayout.HORIZONTAL
            row.gravity = Gravity.CENTER_VERTICAL
            row.setPadding(px(22f), px(14f), px(22f), px(14f))
            val lp = row.layoutParams as RecyclerView.LayoutParams
            lp.setMargins(px(6f), px(6f), px(6f), px(6f))
            row.layoutParams = lp
            val current = QuranAudioService.active && QuranAudioService.reciterId == r.id && QuranAudioService.moshafId == m.id && QuranAudioService.surah == s
            Ui.focusable(row, ut, 22f, if (current) Ui.withAlpha(ut.accent, 70) else ut.card)
            row.isClickable = true
            row.setOnClickListener { QuranPlayback.start(this@ReciterDetailActivity, r, m, s) }

            val num = Ui.label(this@ReciterDetailActivity, s.toString(), 24f, ut.onAccent, true)
            num.gravity = Gravity.CENTER
            num.background = Ui.round(ut.accent, px(14f).toFloat())
            row.addView(num, Ui.lp(px(64f), px(54f)))
            val name = Ui.label(this@ReciterDetailActivity, (if (current) "▶ " else "") + QuranRepo.surahName(s), 28f, ut.text, true)
            Ui.ellipsize(name)
            row.addView(name, Ui.lpm(0, Ui.WRAP, 1f, px(18f), 0, px(12f), 0))

            val dl = QuranDownloads.has(this@ReciterDetailActivity, r.id, m.id, s)
            val running = QuranDownloader.isRunning(r.id, m.id, s)
            val dlText = when { dl -> "✔ منزّلة"; running -> "⏳ " + QuranDownloader.progress(r.id, m.id, s).coerceAtLeast(0) + "%"; else -> "⬇ تنزيل" }
            val dlBtn = Ui.button(this@ReciterDetailActivity, ut, dlText, 20f) {
                if (dl) {
                    AlertDialog.Builder(this@ReciterDetailActivity).setTitle("حذف التنزيل؟")
                        .setPositiveButton("حذف") { _, _ -> QuranDownloader.delete(this@ReciterDetailActivity, r.id, m.id, s); notifyDataSetChanged() }
                        .setNegativeButton("إلغاء", null).show()
                } else if (!running) {
                    AlertDialog.Builder(this@ReciterDetailActivity)
                        .setTitle("التأكد من حق التنزيل")
                        .setMessage("واجهة MP3 Quran لا ترسل بيان ترخيص منفصلاً لكل ملف صوتي. لا تتابع إلا بعد التأكد من أن شروط المصدر تسمح بتنزيل هذا التسجيل للاستماع دون إنترنت. لا تستخدم الملف لإعادة التوزيع أو النشر دون إذن.")
                        .setPositiveButton("متابعة بعد التحقق") { _, _ ->
                            QuranDownloader.start(this@ReciterDetailActivity, r, m, s) { ok, msg ->
                                if (!isDestroyed) { if (!ok) toast("فشل التنزيل: $msg"); notifyDataSetChanged() }
                            }
                            notifyDataSetChanged()
                        }
                        .setNegativeButton("إلغاء", null)
                        .show()
                }
            }
            val fav = qp.isFavSurah(r.id, m.id, s)
            val favB = Ui.button(this@ReciterDetailActivity, ut, if (fav) "♥" else "♡", 24f) {
                qp.toggleFavSurah(r.id, m.id, s); notifyItemChanged(h.bindingAdapterPosition)
            }
            val plB = Ui.button(this@ReciterDetailActivity, ut, "＋", 24f) { addToPlaylist(r, m, s) }
            row.addView(dlBtn, Ui.lpm(Ui.WRAP, Ui.WRAP, 0f, 0, 0, px(10f), 0))
            row.addView(plB, Ui.lpm(Ui.WRAP, Ui.WRAP, 0f, 0, 0, px(10f), 0))
            row.addView(favB)
        }
    }
}
