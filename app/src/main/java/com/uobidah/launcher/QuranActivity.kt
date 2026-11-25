package com.uobidah.launcher

import android.media.AudioAttributes
import android.media.MediaPlayer
import android.os.Bundle
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.ScrollView
import android.widget.TextView
import com.uobidah.launcher.core.AdhkarData
import com.uobidah.launcher.core.QuranText
import com.uobidah.launcher.core.SurahInfo
import java.io.File

/**
 * القرآن الكريم: قائمة السور ← قراءة السورة بالرسم العثماني ← استماع (ملف سورة كامل).
 * النص يُجلب من alquran.cloud ويُخزَّن محلياً بعد أول فتح لكل سورة ليعمل دون إنترنت لاحقاً.
 * لا يُعرض أي نص غير مجلوب من المصدر؛ عند الفشل تظهر رسالة صريحة.
 */
class QuranActivity : BaseActivity() {
    private var surahs: List<SurahInfo> = emptyList()
    private var listRoot: View? = null
    private var inReader = false
    private var player: MediaPlayer? = null
    private var playingSurah = 0
    private var currentSurah = 0
    private var fontPx = 44f
    private lateinit var status: TextView
    private lateinit var listView: ListView
    private lateinit var resumeBtn: TextView

    private fun cacheDir2(): File = File(filesDir, "quran").also { it.mkdirs() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        fontPx = prefs.sp.getFloat("quran_font", 44f).coerceIn(30f, 72f)
        buildList()
        loadSurahs()
    }

    private fun buildList() {
        val root = LinearLayout(this)
        root.orientation = LinearLayout.VERTICAL
        root.background = bgGradient()
        root.setPadding(px(60f), px(30f), px(60f), px(28f))
        root.addView(header("📖 القرآن الكريم"))
        root.addView(Ui.button(this, ut, "🎧 القرّاء — استماع متواصل بصوت القراء", 26f) {
            startActivity(android.content.Intent(this, RecitersActivity::class.java))
        }, Ui.lpm(Ui.WRAP, Ui.WRAP, 0f, 0, px(8f), 0, 0))
        status = Ui.label(this, "جاري تحميل فهرس السور...", 26f, ut.dim)
        root.addView(status, Ui.lpm(Ui.MATCH, Ui.WRAP, 0f, 0, px(10f), 0, px(10f)))
        resumeBtn = Ui.button(this, ut, "↪ متابعة القراءة", 28f) {
            val n = prefs.sp.getInt("quran_last_surah", 0)
            if (n in 1..114) openSurah(n)
        }
        resumeBtn.visibility = View.GONE
        root.addView(resumeBtn, Ui.lpm(Ui.WRAP, Ui.WRAP, 0f, 0, 0, 0, px(10f)))
        listView = ListView(this)
        listView.divider = null
        listView.selector = Ui.selector(this, ut)
        listView.setOnItemClickListener { _, _, pos, _ -> if (pos in surahs.indices) openSurah(surahs[pos].number) }
        root.addView(listView, Ui.lp(Ui.MATCH, 0, 1f))
        root.layoutDirection = View.LAYOUT_DIRECTION_RTL
        listRoot = root
        show(root)
    }

    private fun loadSurahs() {
        val f = File(cacheDir2(), "surahs.json")
        if (f.exists()) {
            try {
                val cached = QuranText.parseSurahs(f.readText(Charsets.UTF_8))
                if (cached.size == 114) { applySurahs(cached); return }
            } catch (_: Exception) { f.delete() }
        }
        Net.bg {
            try {
                val body = Net.getString(QuranText.LIST_URL, 20000)
                val list = QuranText.parseSurahs(body)
                if (list.size != 114) throw IllegalStateException("bad list")
                f.writeText(body, Charsets.UTF_8)
                Net.ui { if (!isFinishing) applySurahs(list) }
            } catch (e: Exception) {
                Net.ui { if (!isFinishing) status.text = "تعذر تحميل فهرس السور. تأكد من الإنترنت ثم أعد فتح الشاشة. (يُخزَّن الفهرس بعد أول نجاح)" }
            }
        }
    }

    private fun applySurahs(list: List<SurahInfo>) {
        surahs = list
        val ad = Ui.listAdapter(this, ut)
        for (s in list) {
            ad.add(AdhkarData.arabicDigits(s.number) + "  •  " + s.nameAr + "   (" + AdhkarData.arabicDigits(s.ayahCount) + " آية • " + (if (s.meccan) "مكية" else "مدنية") + ")")
        }
        listView.adapter = ad
        status.text = "اختر سورة للقراءة. الصوت: " + QuranText.AUDIO_READER
        val last = prefs.sp.getInt("quran_last_surah", 0)
        if (last in 1..114) {
            resumeBtn.text = "↪ متابعة القراءة: " + list[last - 1].nameAr
            resumeBtn.visibility = View.VISIBLE
        }
        listView.requestFocus()
    }

    private fun openSurah(n: Int) {
        val f = File(cacheDir2(), "s$n.json")
        if (f.exists()) {
            try {
                val ayahs = QuranText.parseAyahs(f.readText(Charsets.UTF_8), n)
                if (ayahs.isNotEmpty()) { showReader(n, ayahs); return }
            } catch (_: Exception) { f.delete() }
        }
        status.text = "جاري تحميل السورة..."
        Net.bg {
            try {
                val body = Net.getString(QuranText.surahUrl(n), 25000)
                val ayahs = QuranText.parseAyahs(body, n)
                if (ayahs.isEmpty()) throw IllegalStateException("empty")
                f.writeText(body, Charsets.UTF_8)
                Net.ui { if (!isFinishing) showReader(n, ayahs) }
            } catch (e: Exception) {
                Net.ui { if (!isFinishing) { status.text = "تعذر تحميل السورة. تأكد من الإنترنت وحاول مجدداً."; toast("تعذر تحميل السورة") } }
            }
        }
    }

    private fun showReader(n: Int, ayahs: List<com.uobidah.launcher.core.AyahItem>) {
        inReader = true
        currentSurah = n
        prefs.sp.edit().putInt("quran_last_surah", n).apply()
        val info = surahs.firstOrNull { it.number == n }
        val root = LinearLayout(this)
        root.orientation = LinearLayout.VERTICAL
        root.background = bgGradient()
        root.setPadding(px(60f), px(26f), px(60f), px(24f))

        val top = LinearLayout(this)
        top.orientation = LinearLayout.HORIZONTAL
        top.gravity = Gravity.CENTER_VERTICAL
        top.addView(Ui.label(this, (info?.nameAr ?: ("سورة " + AdhkarData.arabicDigits(n))), 46f, ut.text, true), Ui.lp(0, Ui.WRAP, 1f))
        top.addView(Ui.button(this, ut, "➜ الفهرس", 26f) { backToList() })
        root.addView(top, Ui.lpm(Ui.MATCH, Ui.WRAP, 0f, 0, 0, 0, px(10f)))

        val scroll = ScrollView(this)
        scroll.isVerticalScrollBarEnabled = false
        scroll.isFocusable = true
        scroll.isFocusableInTouchMode = false
        val tv = Ui.label(this, buildText(n, ayahs), 44f, ut.text, true)
        tv.setTextSize(TypedValue.COMPLEX_UNIT_PX, px(fontPx).toFloat())
        tv.setLineSpacing(0f, 1.7f)
        tv.gravity = Gravity.CENTER_HORIZONTAL
        tv.setPadding(px(30f), px(20f), px(30f), px(30f))
        scroll.addView(tv, LinearLayout.LayoutParams(Ui.MATCH, Ui.WRAP))
        Ui.focusable(scroll, ut, 28f, Color0)
        root.addView(scroll, Ui.lp(Ui.MATCH, 0, 1f))

        val bar = LinearLayout(this)
        bar.orientation = LinearLayout.HORIZONTAL
        bar.gravity = Gravity.CENTER
        val playBtn = Ui.button(this, ut, if (playingSurah == n) "⏹ إيقاف الصوت" else "▶ استماع", 26f) {}
        playBtn.setOnClickListener {
            if (playingSurah == n) { stopAudio(); playBtn.text = "▶ استماع" }
            else { playAudio(n, playBtn) }
        }
        bar.addView(playBtn, Ui.lpm(Ui.WRAP, Ui.WRAP, 0f, px(8f), px(12f), px(8f), 0))
        bar.addView(Ui.button(this, ut, "A+", 26f) { setFont(tv, fontPx + 4f) }, Ui.lpm(Ui.WRAP, Ui.WRAP, 0f, px(8f), px(12f), px(8f), 0))
        bar.addView(Ui.button(this, ut, "A−", 26f) { setFont(tv, fontPx - 4f) }, Ui.lpm(Ui.WRAP, Ui.WRAP, 0f, px(8f), px(12f), px(8f), 0))
        if (n > 1) bar.addView(Ui.button(this, ut, "› السورة السابقة", 26f) { stopAudio(); openSurah(n - 1) }, Ui.lpm(Ui.WRAP, Ui.WRAP, 0f, px(8f), px(12f), px(8f), 0))
        if (n < 114) bar.addView(Ui.button(this, ut, "السورة التالية ‹", 26f) { stopAudio(); openSurah(n + 1) }, Ui.lpm(Ui.WRAP, Ui.WRAP, 0f, px(8f), px(12f), px(8f), 0))
        root.addView(bar)
        show(root)
        scroll.requestFocus()
    }

    private val Color0 = android.graphics.Color.TRANSPARENT

    private fun buildText(n: Int, ayahs: List<com.uobidah.launcher.core.AyahItem>): String {
        val sb = StringBuilder()
        if (QuranText.showBasmalaHeader(n)) sb.append(QuranText.BASMALA).append("\n\n")
        for (a in ayahs) sb.append(a.text).append(" ﴿").append(AdhkarData.arabicDigits(a.number)).append("﴾ ")
        return sb.toString().trim()
    }

    private fun setFont(tv: TextView, v: Float) {
        fontPx = v.coerceIn(30f, 72f)
        prefs.sp.edit().putFloat("quran_font", fontPx).apply()
        tv.setTextSize(TypedValue.COMPLEX_UNIT_PX, px(fontPx).toFloat())
    }

    private fun playAudio(n: Int, btn: TextView) {
        stopAudio()
        try {
            val mp = MediaPlayer()
            mp.setAudioAttributes(AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build())
            mp.setDataSource(QuranText.audioUrl(n))
            mp.setOnPreparedListener { it.start(); btn.text = "⏹ إيقاف الصوت" }
            mp.setOnCompletionListener { stopAudio(); btn.text = "▶ استماع" }
            mp.setOnErrorListener { _, _, _ ->
                stopAudio(); btn.text = "▶ استماع"
                toast("تعذر تشغيل الصوت: الرابط أو الاتصال غير متاح حالياً")
                true
            }
            player = mp
            playingSurah = n
            btn.text = "… جاري التحميل"
            mp.prepareAsync()
        } catch (e: Exception) {
            stopAudio()
            toast("تعذر تشغيل الصوت")
        }
    }

    private fun stopAudio() {
        try { player?.release() } catch (_: Exception) {}
        player = null
        playingSurah = 0
    }

    private fun backToList() {
        stopAudio()
        inReader = false
        listRoot?.let { show(it) }
        listView.requestFocus()
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (inReader) backToList() else super.onBackPressed()
    }

    override fun onDestroy() {
        stopAudio()
        super.onDestroy()
    }
}
