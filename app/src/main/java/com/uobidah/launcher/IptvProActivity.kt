package com.uobidah.launcher

import android.os.Bundle
import android.content.Intent
import android.net.Uri
import android.text.Editable
import android.text.TextWatcher
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.HorizontalScrollView
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.ScrollView
import android.widget.TextView
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import androidx.media3.ui.AspectRatioFrameLayout
import com.uobidah.launcher.core.IptvContentClassifier
import java.io.File
import java.io.Reader
import java.io.BufferedReader
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.concurrent.ConcurrentHashMap

class Channel(val name: String, val url: String, val group: String, val tvgId: String = "", val logo: String = "")

/** تبويب واحد في البث المباشر: عنوان + روابط/ملفات (file:المسار) قوائمه. */
class IptvTab(val title: String, val urls: List<String>, val emptyHint: String = "", val favorites: Boolean = false, val history: Boolean = false, val userPlaylist: Boolean = false)

object M3u {
    fun parse(text: String): List<Channel> = parse(java.io.StringReader(text))
    fun parse(reader: Reader): List<Channel> = com.uobidah.launcher.core.M3uParser.parse(reader, 250_000).map {
        Channel(it.name, it.url, it.group, it.tvgId, it.logo)
    }
    fun parse(file: File): List<Channel> = file.bufferedReader(Charsets.UTF_8).use { parse(it) }
}

/** IPTV: M3U / HLS / HTTP عبر Media3 ExoPlayer مع كاش محلي. */
class IptvProActivity : BaseActivity() {

    companion object {
        private const val MAX_PLAYLIST_BYTES = 64L * 1024L * 1024L
        private const val MAX_VISIBLE_CHANNELS = 15_000
        private val playlistCacheLocks = ConcurrentHashMap<String, Any>()
        private val playlistCacheWriteGates = ConcurrentHashMap<String, com.uobidah.launcher.core.LatestRequestGate>()
        const val EXTRA_FAVORITE_URL = "favorite_url"
        const val EXTRA_OPEN_CHOOSER = "open_iptv_playlist_chooser"
        const val EXTRA_MATCH_LABEL = "iptv_match_label"
        const val EXTRA_FAVORITE_NAME = "favorite_name"
    }

    private var pendingFavoriteUrl: String = ""
    private var pendingFavoriteName: String = ""
    private var matchContextLabel: String = ""

    /**
     * مصادر مدمجة بطلب المستخدم (روابط عامة من القائمة التي أرسلها ومن المستودعين المحددين). تبويب مستقل لكل مصدر.
     * أُزيلت المصادر المدمجة القديمة (فهرس الدول وفهرس اللغات). قوائم المستخدم المحفوظة لا تُحذف.
     */
    private val sources = listOf(
        Pair("⚽ كرة القدم مباشر", "https://raw.githubusercontent.com/iqbal8846/sports-iptv/main/output/football.m3u"),
        Pair("⚽ قنوات كرة القدم", "https://raw.githubusercontent.com/VNN2020/iptv-playlists/HEAD/football-soccer.m3u"),
        Pair("⚽ دوريات أوروبا", "https://raw.githubusercontent.com/VNN2020/iptv-playlists/HEAD/soccer-epl-european.m3u"),
        Pair("🏅 رياضة (iptv-org)", "https://iptv-org.github.io/iptv/categories/sports.m3u"),
        Pair("🏅 رياضة (maximmur)", "https://raw.githubusercontent.com/maximmur/iptvlist/cur/sport.m3u8"),
        Pair("🇪🇬 مصر", "https://iptv-org.github.io/iptv/countries/eg.m3u"),
        Pair("🌍 العالم العربي", "https://iptv-org.github.io/iptv/regions/arab.m3u"),
        Pair("🗣 العربية", "https://iptv-org.github.io/iptv/languages/ara.m3u"),
        Pair("🇹🇷 تركيا", "https://iptv-org.github.io/iptv/countries/tr.m3u"),
        Pair("🎬 أفلام", "https://iptv-org.github.io/iptv/categories/movies.m3u"),
        Pair("📺 مسلسلات", "https://iptv-org.github.io/iptv/categories/series.m3u"),
        Pair("🎭 ترفيه", "https://iptv-org.github.io/iptv/categories/entertainment.m3u"),
        Pair("📰 عام", "https://iptv-org.github.io/iptv/categories/general.m3u"),
        Pair("🌐 Free-TV", "https://raw.githubusercontent.com/Free-TV/IPTV/master/playlist.m3u8"),
        Pair("📺 Samsung TV Plus", "https://raw.githubusercontent.com/BuddyChewChew/app-m3u-generator/main/playlists/samsungtvplus_all.m3u"),
        Pair("📺 Pluto TV", "https://raw.githubusercontent.com/BuddyChewChew/app-m3u-generator/main/playlists/plutotv_all.m3u"),
        Pair("📺 Plex", "https://raw.githubusercontent.com/BuddyChewChew/app-m3u-generator/main/playlists/plex_all.m3u"),
        Pair("📺 Roku", "https://raw.githubusercontent.com/BuddyChewChew/app-m3u-generator/main/playlists/roku_all.m3u"),
        Pair("📺 Tubi", "https://raw.githubusercontent.com/BuddyChewChew/app-m3u-generator/main/playlists/tubi_all.m3u"),
        Pair("📺 Xumo", "https://raw.githubusercontent.com/BuddyChewChew/xumo-playlist-generator/main/playlists/xumo_playlist.m3u"),
        Pair("🗂 كل التصنيفات (كبير)", "https://iptv-org.github.io/iptv/index.category.m3u")
    )

    private var tab = 0
    private var allChannels: List<Channel> = emptyList()
    private val shown = ArrayList<Channel>()
    private var query = ""
    private var playingIndex = -1
    private var playingUrl = ""
    private var groupFilter = ""
    private lateinit var groupBtn: TextView
    private var typeFilter = 0 // 0 الكل، 1 قنوات، 2 أفلام، 3 مسلسلات، 4 فيديوهات غير مصنفة
    private lateinit var typeBtn: TextView
    private var fullscreen = false

    private var player: ExoPlayer? = null
    private lateinit var playerView: PlayerView
    private lateinit var listPanel: LinearLayout
    private lateinit var controls: LinearLayout
    private lateinit var list: ListView
    private lateinit var adapter: android.widget.ArrayAdapter<String>
    private lateinit var tvStatus: TextView
    private lateinit var tvCount: TextView
    private val tabViews = ArrayList<TextView>()
    private lateinit var tabsRow: LinearLayout
    private lateinit var rootLayout: LinearLayout
    private lateinit var contentRow: LinearLayout
    private lateinit var playlistBar: HorizontalScrollView
    private lateinit var headRow: LinearLayout
    private var chooserOverlay: View? = null
    private lateinit var playerContainer: LinearLayout
    private var tabs: List<IptvTab> = emptyList()
    private val importM3uRequest = 704
    private var tabRestored = false
    private val filterHandler = android.os.Handler(android.os.Looper.getMainLooper())
    // Each tab/refresh gets a monotonically increasing token. A slower, older network
    // request can never repaint the currently selected tab after a newer request wins.
    private val playlistLoadGate = com.uobidah.launcher.core.LatestRequestGate()
    private val filterTask = Runnable { applyFilter() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        rootLayout = LinearLayout(this)
        rootLayout.orientation = LinearLayout.VERTICAL
        rootLayout.background = bgGradient()
        rootLayout.setPadding(px(40f), px(28f), px(40f), px(24f))

        // ---------- لوحة القنوات ----------
        listPanel = LinearLayout(this)
        listPanel.orientation = LinearLayout.VERTICAL
        val head = LinearLayout(this)
        head.orientation = LinearLayout.HORIZONTAL
        head.gravity = android.view.Gravity.CENTER_VERTICAL
        head.addView(Ui.label(this, "🎬 البث المباشر", 42f, ut.text, true), Ui.lp(0, Ui.WRAP, 1f))
        head.addView(Ui.button(this, ut, "➜ رجوع", 24f) { finish() })
        headRow = head
        rootLayout.addView(head, Ui.lpm(Ui.MATCH, Ui.WRAP, 0f, 0, 0, 0, px(12f)))

        // شريط قوائم التشغيل هو نقطة البداية ويمتد بعرض الشاشة؛ كل قائمة مستقلة باسمها.
        tabsRow = LinearLayout(this)
        tabsRow.orientation = LinearLayout.HORIZONTAL
        tabsRow.clipChildren = false
        tabsRow.setPadding(px(6f), px(6f), px(6f), px(6f))
        playlistBar = HorizontalScrollView(this)
        playlistBar.isHorizontalScrollBarEnabled = false
        playlistBar.clipChildren = false
        playlistBar.clipToPadding = false
        playlistBar.overScrollMode = View.OVER_SCROLL_NEVER
        playlistBar.addView(tabsRow, FrameLayout.LayoutParams(Ui.WRAP, Ui.WRAP))
        rebuildTabs()
        pendingFavoriteUrl = intent.getStringExtra(EXTRA_FAVORITE_URL).orEmpty().take(4096)
        pendingFavoriteName = intent.getStringExtra(EXTRA_FAVORITE_NAME).orEmpty().take(160)
        matchContextLabel = intent.getStringExtra(EXTRA_MATCH_LABEL).orEmpty()
            .replace(Regex("[\\r\\n\\u0000-\\u001F]"), " ").trim().take(180)
        if (matchContextLabel.isNotBlank()) typeFilter = 1
        if (pendingFavoriteUrl.isNotBlank()) {
            val favoritesIndex = tabs.indexOfFirst { it.favorites }
            if (favoritesIndex >= 0) tab = favoritesIndex
            for (k in tabViews.indices) Ui.styleChip(tabViews[k], ut, k == tab)
        }
        rootLayout.addView(playlistBar, Ui.lpm(Ui.MATCH, Ui.WRAP, 0f, 0, 0, 0, px(12f)))
        if (matchContextLabel.isNotBlank()) {
            rootLayout.addView(Ui.label(this,
                "⚽ المباراة: $matchContextLabel  •  تم اختيار القنوات المباشرة تلقائياً",
                19f, ut.accent, true), Ui.lpm(Ui.MATCH, Ui.WRAP, 0f, 0, 0, 0, px(8f)))
        }

        val search = Ui.edit(this, ut, if (matchContextLabel.isBlank()) "ابحث عن قناة..." else "ابحث عن القناة الناقلة أو اسم الدوري...")
        search.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, st: Int, c: Int, a: Int) {}
            override fun onTextChanged(s: CharSequence?, st: Int, b: Int, c: Int) {}
            override fun afterTextChanged(s: Editable?) {
                query = s?.toString()?.trim() ?: ""
                // تأخير بسيط: لا نعيد الفلترة مع كل حرف
                filterHandler.removeCallbacks(filterTask)
                filterHandler.postDelayed(filterTask, 220L)
            }
        })
        listPanel.addView(search, Ui.lpm(Ui.MATCH, Ui.WRAP, 0f, 0, 0, 0, px(8f)))
        groupBtn = Ui.button(this, ut, "🗂 التصنيف", 20f) { pickGroup() }
        typeBtn = Ui.button(this, ut, "🎞 النوع: الكل", 20f) { pickType() }
        val filtersRow = LinearLayout(this)
        filtersRow.orientation = LinearLayout.HORIZONTAL
        typeBtn.maxLines = 1; groupBtn.maxLines = 1
        typeBtn.ellipsize = android.text.TextUtils.TruncateAt.END; groupBtn.ellipsize = android.text.TextUtils.TruncateAt.END
        filtersRow.addView(typeBtn, Ui.lpm(0, Ui.WRAP, 1f, 0, 0, px(10f), 0))
        filtersRow.addView(groupBtn, Ui.lp(0, Ui.WRAP, 1f))
        listPanel.addView(filtersRow, Ui.lpm(Ui.MATCH, Ui.WRAP, 0f, 0, 0, 0, px(8f)))
        tvCount = Ui.label(this, "", 22f, ut.dim)
        listPanel.addView(tvCount, Ui.lpm(Ui.WRAP, Ui.WRAP, 0f, 0, 0, 0, px(6f)))

        list = ListView(this)
        list.divider = null
        list.selector = Ui.selector(this, ut)
        adapter = Ui.listAdapter(this, ut)
        list.adapter = adapter
        list.setOnItemClickListener { _, _, pos, _ ->
            if (pos >= 0 && pos < shown.size) {
                if (pos == playingIndex && player != null) setFull(true) else play(pos)
            }
        }
        list.setOnItemLongClickListener { _, _, pos, _ ->
            if (pos >= 0 && pos < shown.size) toggleFavorite(shown[pos])
            true
        }
        listPanel.addView(list, Ui.lp(Ui.MATCH, 0, 1f))

        // ---------- مساحة المحتوى: اختيار القنوات بجوار مشغل الفيديو ----------
        contentRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        contentRow.addView(listPanel, Ui.lpm(0, Ui.MATCH, 1f, 0, 0, px(26f), 0))

        // ---------- لوحة المشغل ----------
        val right = LinearLayout(this)
        playerContainer = right
        right.orientation = LinearLayout.VERTICAL
        val frame = FrameLayout(this)
        frame.setBackgroundColor(android.graphics.Color.BLACK)
        playerView = PlayerView(this)
        playerView.useController = false
        playerView.isFocusable = true
        playerView.isClickable = true
        playerView.setOnClickListener { setFull(!fullscreen) }
        frame.addView(playerView, FrameLayout.LayoutParams(Ui.MATCH, Ui.MATCH))
        tvStatus = Ui.label(this, "اختر قناة من القائمة", 30f, ut.text, true)
        tvStatus.setBackgroundColor(Ui.withAlpha(android.graphics.Color.BLACK, 140))
        tvStatus.setPadding(px(18f), px(8f), px(18f), px(8f))
        val slp = FrameLayout.LayoutParams(Ui.WRAP, Ui.WRAP)
        slp.gravity = android.view.Gravity.TOP or android.view.Gravity.START
        slp.setMargins(px(16f), px(16f), px(16f), px(16f))
        frame.addView(tvStatus, slp)
        right.addView(frame, Ui.lp(Ui.MATCH, 0, 1f))

        controls = LinearLayout(this)
        controls.orientation = LinearLayout.VERTICAL
        fun ctl(row: LinearLayout, label: String, act: () -> Unit) {
            row.addView(Ui.button(this, ut, label, 17f) { act() }.apply { setPadding(px(4f), px(14f), px(4f), px(14f)); maxLines = 2 }, Ui.lpm(0, Ui.MATCH, 1f, px(4f), 0, px(4f), 0))
        }
        val row1 = LinearLayout(this); row1.orientation = LinearLayout.HORIZONTAL
        val row2 = LinearLayout(this); row2.orientation = LinearLayout.HORIZONTAL
        val row3 = LinearLayout(this); row3.orientation = LinearLayout.HORIZONTAL
        ctl(row1, "⛶ ملء الشاشة") { setFull(true) }
        ctl(row1, "📚 القوائم") { chooseList() }
        ctl(row1, "➕ M3U") { addM3uDialog() }
        ctl(row2, "📂 ملف") { importM3uFile() }
        ctl(row2, "🔑 Xtream") { addXtreamDialog() }
        ctl(row2, "📅 EPG") { epgDialog() }
        ctl(row2, "📌 افتراضية") { toggleDefault(tab) }
        ctl(row3, "🗑 قوائمي") { manageLinks() }
        ctl(row3, "✏ تعديل") { editPlaylist() }
        ctl(row3, "📻 راديو") {
            try { startActivity(android.content.Intent(this, RadioActivity::class.java)) } catch (e: Exception) { toast("تعذر فتح الراديو") }
        }
        ctl(row3, "🔄 تحديث") { loadTab(true) }
        controls.addView(row1, Ui.lpm(Ui.MATCH, Ui.WRAP, 0f, 0, 0, 0, px(6f)))
        controls.addView(row2, Ui.lpm(Ui.MATCH, Ui.WRAP, 0f, 0, 0, 0, px(6f)))
        controls.addView(row3, Ui.lpm(Ui.MATCH, Ui.WRAP, 0f, 0, 0, 0, 0))
        right.addView(controls, Ui.lpm(Ui.MATCH, Ui.WRAP, 0f, 0, px(10f), 0, 0))
        contentRow.addView(right, Ui.lp(0, Ui.MATCH, 1.5f))
        rootLayout.addView(contentRow, Ui.lp(Ui.MATCH, 0, 1f))

        show(rootLayout)
        list.requestFocus()
        loadEpg(false)

        val p = runCatching { PlayerFactory.create(this) }.getOrNull()
        p?.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                when (playbackState) {
                    Player.STATE_BUFFERING -> tvStatus.text = "⏳ جاري التحميل..."
                    Player.STATE_READY -> tvStatus.text = if (playingIndex >= 0 && playingIndex < shown.size) "▶ " + epgStatus(shown[playingIndex]) else ""
                    else -> {}
                }
            }

            override fun onPlayerError(error: PlaybackException) {
                val reason = when (error.errorCode) {
                    PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED,
                    PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT -> "تعذر الاتصال بالمصدر أو انتهت المهلة"
                    PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS -> "المصدر رفض التشغيل (HTTP)"
                    PlaybackException.ERROR_CODE_PARSING_CONTAINER_MALFORMED,
                    PlaybackException.ERROR_CODE_PARSING_MANIFEST_MALFORMED -> "صيغة البث غير مدعومة أو رابط القناة غير صحيح"
                    PlaybackException.ERROR_CODE_DECODING_FAILED -> "ترميز الفيديو/الصوت غير مدعوم على الجهاز"
                    else -> error.errorCodeName
                }
                tvStatus.text = "⚠ $reason — جرّب قناة أخرى أو حدّث القائمة"
            }
        })
        if (p != null) {
            playerView.player = p
            player = p
        } else {
            tvStatus.text = "⚠ تعذر تهيئة مشغل الفيديو على هذا الجهاز"
        }

        // الاختيار الصريح هو نقطة البداية دائماً: لا نفتح قائمة عشوائية ولا ندمج ملفات المستخدم.
        if (intent.getBooleanExtra(EXTRA_OPEN_CHOOSER, false) || (pendingFavoriteUrl.isBlank() && prefs.iptvDefaultTab.isEmpty())) {
            tvStatus.text = "اختر قائمة التشغيل لعرض محتواها"
            window.decorView.post { chooseList() }
        } else loadTab(false)
    }

    /** شاشة اختيار قائمة مستقلة داخل نفس النشاط؛ لا Popup/Dialog على Android TV. */
    private fun chooseList() {
        val order = tabs.indices.filter { !tabs[it].favorites && !tabs[it].history }
            .sortedWith(compareBy({ if (tabs[it].userPlaylist) 0 else 1 }, { tabs[it].title }))
        val overlay = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = android.view.Gravity.CENTER
            setPadding(px(72f), px(40f), px(72f), px(40f))
            setBackgroundColor(ut.bgBottom)
            isFocusableInTouchMode = true
        }
        overlay.addView(Ui.label(this, "📚 اختر قائمة التشغيل", 42f, ut.text, true), Ui.lpm(Ui.MATCH, Ui.WRAP, 0f, 0, 0, 0, px(10f)))
        val chooserHint = if (matchContextLabel.isBlank())
            "كل ملف أو قائمة مستقلة؛ بعدها اختر القنوات أو المحتوى من داخل القائمة"
        else "اختيار قائمة لمشاهدة: $matchContextLabel — ستظهر القنوات المباشرة أولاً"
        overlay.addView(Ui.label(this, chooserHint, 22f, ut.dim), Ui.lpm(Ui.MATCH, Ui.WRAP, 0f, 0, 0, 0, px(22f)))
        val sc = ScrollView(this).apply { isVerticalScrollBarEnabled = false; isFillViewport = true }
        val grid = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; gravity = android.view.Gravity.CENTER_HORIZONTAL }
        for (pos in order.indices) {
            val idx = order[pos]
            val t = tabs[idx]
            val label = (if (idx == tab) "✓ " else "") + t.title
            val b = Ui.button(this, ut, label, 28f) {
                selectTab(idx)
                closeListChooser()
                playerView.requestFocus()
            }
            b.isFocusable = true
            b.setOnFocusChangeListener { v, has -> if (has) v.scaleX = 1.02f else v.scaleX = 1f }
            grid.addView(b, Ui.lpm(Ui.MATCH, px(72f), 0f, 0, 0, 0, px(12f)))
        }
        if (order.isEmpty()) {
            grid.addView(Ui.label(this, "لا توجد قوائم بعد. أضف قائمة M3U أو استورد ملفاً من جهازك.", 24f, ut.dim),
                Ui.lpm(Ui.MATCH, Ui.WRAP, 0f, 0, 0, 0, px(18f)))
            grid.addView(Ui.button(this, ut, "➕ إضافة رابط M3U", 26f) {
                closeListChooser()
                addM3uDialog()
            }, Ui.lpm(Ui.MATCH, px(72f), 0f, 0, 0, 0, px(10f)))
            grid.addView(Ui.button(this, ut, "📂 استيراد ملف M3U", 26f) {
                closeListChooser()
                importM3uFile()
            }, Ui.lpm(Ui.MATCH, px(72f), 0f, 0, 0, 0, px(10f)))
        }
        sc.addView(grid, Ui.lp(Ui.MATCH, Ui.WRAP))
        overlay.addView(sc, Ui.lp(Ui.MATCH, 0, 1f))
        rootLayout.addView(overlay, Ui.lp(Ui.MATCH, 0, 1f))
        chooserOverlay = overlay
        headRow.visibility = View.GONE
        playlistBar.visibility = View.GONE
        contentRow.visibility = View.GONE
        listPanel.visibility = View.GONE
        overlay.post {
            var first: View? = null
            fun find(v: View) {
                if (first != null) return
                if (v.isFocusable) { first = v; return }
                if (v is ViewGroup) for (i in 0 until v.childCount) find(v.getChildAt(i))
            }
            find(grid)
            first?.requestFocus()
        }
    }

    private fun closeListChooser() {
        chooserOverlay?.let { rootLayout.removeView(it) }
        chooserOverlay = null
        headRow.visibility = View.VISIBLE
        playlistBar.visibility = View.VISIBLE
        contentRow.visibility = View.VISIBLE
        listPanel.visibility = View.VISIBLE
    }

    /** مجلد ملفات M3U المحلية: كل ملف فيه يصبح قائمة مستقلة (يُدفع بـ adb push). */
    private fun playlistsDir(): File? = try {
        getExternalFilesDir("playlists")?.also { if (!it.exists()) it.mkdirs() }
    } catch (e: Exception) {
        null
    }

    private fun buildTabs(): List<IptvTab> {
        val out = ArrayList<IptvTab>()
        // قوائم المستخدم أولاً؛ كل ملف أو رابط يبقى تبويباً مستقلاً دون دمج تصنيفاته مع قوائم أخرى.
        val files = playlistsDir()?.listFiles()
            ?.filter { it.isFile && (it.name.endsWith(".m3u", true) || it.name.endsWith(".m3u8", true)) }
            ?.sortedBy { it.name.lowercase() } ?: emptyList()
        for (f in files) out.add(IptvTab("📁 " + f.nameWithoutExtension, listOf("file:" + f.absolutePath), "الملف فارغ أو لا يحتوي عناوين بث صالحة", userPlaylist = true))
        for (e in prefs.iptvPlaylists) {
            val name = e.substringBefore("||").ifBlank { "قائمة" }
            val url = e.substringAfter("||", "")
            if (url.isNotBlank()) out.add(IptvTab("🔖 " + name, listOf(url), userPlaylist = true))
        }
        prefs.customM3u.forEachIndexed { index, url ->
            if (url.startsWith("http://") || url.startsWith("https://")) {
                val host = runCatching { android.net.Uri.parse(url).host }.getOrNull().orEmpty()
                val label = host.ifBlank { "مصدر ${index + 1}" }
                out.add(IptvTab("➕ " + label, listOf(url), userPlaylist = true))
            }
        }
        out.add(IptvTab("⭐ المفضلة", emptyList(), "اضغط مطولاً على أي قناة لإضافتها إلى المفضلة", true))
        out.add(IptvTab("🕘 السجل", emptyList(), "لم تشاهد أي قناة بعد", false, true))
        for (s in sources) out.add(IptvTab(s.first, listOf(s.second)))
        return out
    }

    private fun rebuildTabs() {
        val previouslySelectedTitle = tabs.getOrNull(tab)?.title.orEmpty()
        tabs = buildTabs()
        if (!tabRestored) {
            tabRestored = true
            val def = if (prefs.iptvDefaultTab.isNotEmpty()) tabs.indexOfFirst { it.title == prefs.iptvDefaultTab } else -1
            val saved = if (def >= 0) def else tabs.indexOfFirst { it.title == prefs.iptvLastTab }
            val firstUserList = tabs.indexOfFirst { it.userPlaylist }
            tab = if (saved >= 0) saved else if (firstUserList >= 0) firstUserList else 2.coerceAtMost(tabs.size - 1)
        } else {
            val preserved = tabs.indexOfFirst { it.title == previouslySelectedTitle }
            if (preserved >= 0) tab = preserved
            else if (tab >= tabs.size) tab = 0
        }
        if (tab >= tabs.size) tab = 0
        tabViews.clear()
        tabsRow.removeAllViews()
        for (i in tabs.indices) {
            val c = Ui.chip(this, ut, (if (tabs[i].title == prefs.iptvDefaultTab) "📌 " else "") + tabs[i].title, i == tab, 22f) { selectTab(i) }
            c.setOnLongClickListener { toggleDefault(i); true }
            c.setPadding(px(16f), px(12f), px(16f), px(12f))
            tabViews.add(c)
            tabsRow.addView(c, Ui.lpm(Ui.WRAP, Ui.WRAP, 0f, 0, 0, px(8f), 0))
        }
    }

    private fun selectTab(i: Int) {
        if (i < 0 || i >= tabs.size) return
        val changed = tab != i
        tab = i
        groupFilter = ""
        prefs.iptvLastTab = tabs[i].title
        for (k in tabViews.indices) Ui.styleChip(tabViews[k], ut, k == i)
        if (changed) {
            // Never leave the previous tab's channels visible under the newly selected tab.
            allChannels = emptyList()
            shown.clear()
            playingIndex = -1
            adapter.clear()
            adapter.notifyDataSetChanged()
            tvCount.text = "جاري تحميل ${tabs[i].title}..."
        }
        loadTab(false)
    }

    // ---------- الكاش والتحميل ----------
    private fun channelIdentity(channel: Channel): String = com.uobidah.launcher.core.M3uParser.identity(
        channel.url, channel.tvgId, channel.group, channel.name
    )

    private fun cacheLock(file: File): Any = playlistCacheLocks.computeIfAbsent(file.absolutePath) { Any() }
    private fun cacheWriteGate(file: File): com.uobidah.launcher.core.LatestRequestGate =
        playlistCacheWriteGates.computeIfAbsent(file.absolutePath) { com.uobidah.launcher.core.LatestRequestGate() }

    private fun cacheFor(url: String): File {
        val digest = java.security.MessageDigest.getInstance("SHA-256").digest(url.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it.toInt() and 0xff) }
        val strong = File(filesDir, "m3u_$digest.m3u")
        val legacy = File(filesDir, "m3u_" + Integer.toHexString(url.hashCode()) + ".m3u")
        synchronized(cacheLock(strong)) {
            // Migrate only a playlist that has already passed parsing; never promote a
            // truncated/HTML legacy cache into the new cache namespace.
            if (!strong.exists() && parseValidPlaylistFile(legacy) != null) {
                val temp = File(filesDir, strong.name + ".migrate-" + java.lang.Long.toHexString(System.nanoTime()))
                try {
                    legacy.copyTo(temp, overwrite = true)
                    if (parseValidPlaylistFile(temp) != null && temp.renameTo(strong)) legacy.delete()
                } finally { temp.delete() }
            }
        }
        return strong
    }

    private fun parseValidPlaylistFile(file: File): List<Channel>? = runCatching {
        if (!file.isFile || file.length() !in 1..MAX_PLAYLIST_BYTES) return@runCatching null
        require(com.uobidah.launcher.core.M3uParser.hasM3uHeader(file))
        M3u.parse(file).takeIf { it.isNotEmpty() }
    }.getOrNull()

    /**
     * Read a cache entry, recovering the newest parseable backup after a process/device
     * interruption. Backup files are retained unless the restored target is verified.
     */
    private fun readValidPlaylist(file: File): List<Channel>? {
        parseValidPlaylistFile(file)?.let { return it }
        val parent = file.parentFile ?: return null
        synchronized(cacheLock(file)) {
            parseValidPlaylistFile(file)?.let { return it }
            val candidates = parent.listFiles { _, name -> name.startsWith(file.name + ".bak-") }
                ?.sortedByDescending { it.lastModified() }.orEmpty()
            for (backup in candidates) {
                if (parseValidPlaylistFile(backup) == null) continue
                val restore = File(parent, file.name + ".restore-" + java.lang.Long.toHexString(System.nanoTime()))
                val damaged = File(parent, file.name + ".damaged-" + java.lang.Long.toHexString(System.nanoTime()))
                try {
                    backup.copyTo(restore, overwrite = true)
                    if (file.exists() && !file.renameTo(damaged)) continue
                    if (!restore.renameTo(file)) {
                        if (damaged.exists() && !file.exists()) damaged.renameTo(file)
                        continue
                    }
                    val restored = parseValidPlaylistFile(file)
                    if (restored != null) {
                        backup.delete()
                        damaged.delete()
                        return restored
                    }
                    file.delete()
                    if (damaged.exists()) damaged.renameTo(file)
                } catch (_: Exception) {
                    if (damaged.exists() && !file.exists()) damaged.renameTo(file)
                } finally {
                    restore.delete()
                }
            }
        }
        return null
    }

    /** Atomic file-to-file promotion; a failed restore leaves the backup for next launch. */
    private fun writePlaylistCacheSafely(
        target: File,
        source: File,
        expectedCount: Int,
        writeGate: com.uobidah.launcher.core.LatestRequestGate,
        requestToken: Long
    ) {
        synchronized(cacheLock(target)) {
            require(source.isFile && source.length() in 1..MAX_PLAYLIST_BYTES) { "ملف القائمة غير صالح أو أكبر من 64 ميجابايت" }
            val dir = target.parentFile ?: throw IllegalStateException("مجلد الكاش غير متاح")
            if (!dir.exists() && !dir.mkdirs()) throw IllegalStateException("تعذر إنشاء مجلد الكاش")
            val nonce = java.lang.Long.toHexString(System.nanoTime())
            val temp = File(dir, target.name + ".tmp-" + nonce)
            val backup = File(dir, target.name + ".bak-" + nonce)
            var committed = false
            try {
                source.inputStream().buffered().use { input ->
                    FileOutputStream(temp).use { output ->
                        input.copyTo(output, 32 * 1024)
                        output.fd.sync()
                    }
                }
                val verified = parseValidPlaylistFile(temp) ?: throw IllegalArgumentException("القائمة المؤقتة غير صالحة")
                require(verified.size == expectedCount) { "تغير عدد القنوات أثناء حفظ القائمة" }
                // Cache writes are gated per URL, independently of which tab is visible.
                // This lets an old tab populate its own cache without repainting the UI,
                // while a slower response for the same URL can never overwrite a newer one.
                val didCommit = writeGate.runIfCurrent(requestToken) {
                    if (target.exists() && !target.renameTo(backup)) throw IllegalStateException("تعذر تأمين نسخة الكاش السابقة")
                    if (!temp.renameTo(target)) throw IllegalStateException("تعذر تثبيت نسخة الكاش الجديدة")
                    require(target.isFile && target.length() == source.length()) {
                        "تعذر التحقق من الملف المثبت"
                    }
                    committed = true
                }
                require(didCommit) { "stale playlist request" }
            } catch (error: Exception) {
                if (backup.exists()) {
                    if (target.exists()) target.delete()
                    if (!target.exists()) backup.renameTo(target)
                } else if (!committed && target.exists() && parseValidPlaylistFile(target) == null) {
                    target.delete()
                }
                throw error
            } finally {
                temp.delete()
                // Do not erase the only known-good copy when restoration failed.
                if (committed && target.isFile && parseValidPlaylistFile(target) != null) backup.delete()
            }
        }
    }

    private fun copyBounded(input: java.io.InputStream, target: File, maxBytes: Long = MAX_PLAYLIST_BYTES): Long {
        var total = 0L
        FileOutputStream(target).use { output ->
            val buffer = ByteArray(32 * 1024)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                total += read
                require(total <= maxBytes) { "حجم القائمة أكبر من الحد الآمن (64 ميجابايت)" }
                output.write(buffer, 0, read)
            }
            output.fd.sync()
        }
        return total
    }

    private fun playlistErrorText(error: Throwable): String {
        val message = error.message.orEmpty()
        return when {
            message.contains("64 MB", true) || message.contains("64 ميجابايت") -> "القائمة أكبر من الحد الآمن 64 ميجابايت؛ استخدم قائمة مقسمة حسب الدولة أو التصنيف."
            message.contains("too many channels", true) -> "القائمة تحتوي على قنوات أكثر من الحد المدعوم (250 ألف قناة)."
            message.contains("HTTP 401") || message.contains("HTTP 403") -> "المصدر رفض الوصول؛ تحقق من بيانات الدخول أو صلاحية الرابط."
            message.contains("HTTP 404") -> "الرابط غير موجود (404)."
            message.contains("timeout", true) || message.contains("timed out", true) -> "انتهت مهلة الاتصال قبل اكتمال تنزيل القائمة."
            message.contains("M3U", true) || message.contains("playlist", true) -> message
            message.isBlank() -> "تعذر قراءة القائمة؛ تحقق من الرابط والإنترنت."
            else -> message.take(180)
        }
    }

    private fun urlsForTab(): List<String> = tabs.getOrNull(tab)?.urls ?: emptyList()

    private fun isLocal(u: String): Boolean = u.startsWith("file:")

    private fun loadTab(force: Boolean) {
        val requestToken = playlistLoadGate.begin()
        if (tabs.getOrNull(tab)?.favorites == true) {
            val favs = ArrayList<Channel>()
            for (f in com.uobidah.launcher.core.FavoriteChannels.parseAll(prefs.iptvFavorites)) {
                favs.add(Channel(f.name, f.url, "⭐", "", f.logo))
            }
            allChannels = favs
            val hint = tabs[tab].emptyHint
            applyFilter {
                if (favs.isNotEmpty()) playPendingFavoriteWhenReady() else tvCount.text = hint
            }
            return
        }
        if (tabs.getOrNull(tab)?.history == true) {
            val hs = ArrayList<Channel>()
            for (e in prefs.iptvHistory) {
                val u = e.substringAfter("||", "")
                if (u.isNotBlank()) hs.add(Channel(e.substringBefore("||").ifBlank { u }, u, "🕘"))
            }
            allChannels = hs
            applyFilter { if (hs.isEmpty()) tvCount.text = tabs[tab].emptyHint }
            return
        }
        val urls = urlsForTab()
        val myTab = tab
        tvCount.text = "جاري التحميل..."
        if (urls.isEmpty()) {
            allChannels = emptyList()
            val hint = tabs.getOrNull(tab)?.emptyHint?.ifBlank { "لا توجد قوائم" } ?: "لا توجد قوائم"
            applyFilter { tvCount.text = hint }
            return
        }
        val queued = Net.submitBg {
            val cached = ArrayList<Channel>()
            val fresh = ArrayList<Channel>()
            var hadNetworkFailure = false
            var usedFallbackCache = false
            var cacheWriteFailure = false
            var lastFailureDetail = ""
            for (u in urls) {
                // Continue finishing each playlist's own cache even if the user switches tabs.
                // playlistLoadGate is intentionally only a UI-paint guard; cache writes use
                // their own per-URL generation gate below.
                val selectedForUi = playlistLoadGate.isCurrent(requestToken)
                if (isLocal(u)) {
                    val localFile = File(u.removePrefix("file:"))
                    val local = readValidPlaylist(localFile).orEmpty()
                    if (selectedForUi) fresh.addAll(local)
                    if (local.isEmpty() && selectedForUi) lastFailureDetail = "الملف فارغ أو ترويسة M3U غير صحيحة"
                    continue
                }
                val file = cacheFor(u)
                val goodCache = readValidPlaylist(file)
                if (goodCache != null && selectedForUi) cached.addAll(goodCache)
                val stale = force || goodCache == null || System.currentTimeMillis() - file.lastModified() > 6 * 3600_000L
                if (stale) {
                    val writeGate = cacheWriteGate(file)
                    val cacheToken = writeGate.begin()
                    val download = File(cacheDir, file.name + ".download-" + java.lang.Long.toHexString(System.nanoTime()) + ".tmp")
                    try {
                        Net.downloadToFile(u, download, timeoutMs = 45_000, maxBytes = MAX_PLAYLIST_BYTES)
                        require(com.uobidah.launcher.core.M3uParser.hasM3uHeader(download)) { "الرابط لا يعيد ترويسة M3U صحيحة" }
                        val parsed = M3u.parse(download)
                        require(parsed.isNotEmpty()) { "القائمة لا تحتوي قنوات صالحة" }
                        // Keep parsed data only for the still-selected screen. The cache
                        // update proceeds regardless, with latest-write-wins per source URL.
                        if (playlistLoadGate.isCurrent(requestToken)) fresh.addAll(parsed)
                        runCatching {
                            writePlaylistCacheSafely(file, download, parsed.size, writeGate, cacheToken)
                        }.onFailure { error ->
                            if (error.message != "stale playlist request") cacheWriteFailure = true
                        }
                    } catch (error: Exception) {
                        hadNetworkFailure = true
                        lastFailureDetail = playlistErrorText(error)
                        if (goodCache != null && playlistLoadGate.isCurrent(requestToken)) {
                            fresh.addAll(goodCache)
                            usedFallbackCache = true
                        }
                    } finally {
                        download.delete()
                    }
                } else {
                    fresh.addAll(goodCache.orEmpty())
                }
            }
            val shownData = if (fresh.isNotEmpty()) fresh.distinctBy(::channelIdentity)
                else cached.distinctBy(::channelIdentity)
            Net.ui {
                if (playlistLoadGate.isCurrent(requestToken) && myTab == tab && !isFinishing && !isDestroyed) {
                    if (shownData.isNotEmpty()) {
                        allChannels = shownData
                        backfillFavoriteLogos(shownData)
                        val notices = ArrayList<String>()
                        if (hadNetworkFailure) notices.add(if (usedFallbackCache) "استخدمنا النسخة المحفوظة لبعض القوائم" else "تعذر تحديث بعض المصادر")
                        if (cacheWriteFailure) notices.add("تعذر حفظ نسخة محلية")
                        applyFilter {
                            if (notices.isNotEmpty()) tvCount.text = "${shownData.size} قناة • " + notices.joinToString(" • ")
                        }
                    } else {
                        allChannels = emptyList()
                        val failureText = if (urls.all { isLocal(it) }) "لا توجد قنوات صالحة في الملف" else
                            "تعذر تحميل قائمة صالحة؛ " + lastFailureDetail.ifBlank { "راجع الرابط والإنترنت" }
                        applyFilter { tvCount.text = failureText }
                    }
                }
            }
        }
        if (!queued && playlistLoadGate.isCurrent(requestToken)) tvCount.text = "التطبيق مشغول الآن؛ حاول تحديث القائمة بعد قليل"
    }

    private fun toggleFavorite(ch: Channel) {
        val (next, added) = com.uobidah.launcher.core.FavoriteChannels.toggle(
            prefs.iptvFavorites, com.uobidah.launcher.core.FavoriteChannel(ch.name, ch.url, ch.logo))
        toast(if (added) "⭐ أُضيفت إلى المفضلة" else "تمت الإزالة من المفضلة")
        prefs.iptvFavorites = next
        if (tabs.getOrNull(tab)?.favorites == true) loadTab(false)
    }

    /** يكمل شعارات المفضلة القديمة (المخزّنة بلا شعار) من أي قائمة محمّلة فيها نفس الرابط. */
    private fun backfillFavoriteLogos(channels: List<Channel>) {
        val favs = prefs.iptvFavorites
        if (favs.isEmpty()) return
        val byUrl = HashMap<String, String>()
        for (c in channels) if (c.logo.isNotEmpty()) byUrl.putIfAbsent(c.url, c.logo)
        if (byUrl.isEmpty()) return
        com.uobidah.launcher.core.FavoriteChannels.backfillLogos(favs, byUrl)?.let { prefs.iptvFavorites = it }
    }

    private fun playPendingFavoriteWhenReady() {
        val url = pendingFavoriteUrl
        if (url.isBlank()) return
        val index = shown.indexOfFirst { it.url == url }
        if (index < 0) {
            tvStatus.text = "القناة المفضلة لم تعد ضمن القائمة المحفوظة: " + pendingFavoriteName
            pendingFavoriteUrl = ""
            return
        }
        pendingFavoriteUrl = ""
        window.decorView.post {
            if (isFinishing || isDestroyed) return@post
            play(index)
            setFull(true)
        }
    }

    // كل تشغيل فلترة يحمل رقمًا متزايدًا؛ نتيجة قديمة لا ترسم فوق نتيجة أحدث.
    private val filterGate = com.uobidah.launcher.core.LatestRequestGate()

    /**
     * الفلترة وبناء صفوف القائمة كانا يعملان على خيط الواجهة، فيتجمد التلفزيون ثوانيَ (أو يظهر
     * «التطبيق لا يستجيب») مع ملف قنوات كبير يضم عشرات الآلاف. الآن يُحسب كل شيء في الخلفية
     * ولا تلمس الواجهة إلا النتيجة الجاهزة.
     */
    private fun applyFilter(onDone: (() -> Unit)? = null) {
        val source = allChannels
        val q = query
        val gf = groupFilter
        val tf = typeFilter
        val urlPlaying = playingUrl
        val token = filterGate.begin()
        Net.bg {
            val filtered = ArrayList<Channel>()
            var matched = 0
            for (c in source) {
                if (gf.isNotEmpty() && c.group != gf) continue
                if (tf != 0 && kindOf(c) != tf) continue
                if (q.isEmpty() || c.name.contains(q, true) || c.group.contains(q, true)) {
                    matched++
                    if (filtered.size < MAX_VISIBLE_CHANNELS) filtered.add(c)
                }
            }
            // Keep all channels in memory for search, but cap adapter rows for low-memory TVs.
            val rows = ArrayList<String>(filtered.size)
            for (c in filtered) rows.add(epgRow(c))
            val newPlayingIndex = if (urlPlaying.isEmpty()) -1 else filtered.indexOfFirst { it.url == urlPlaying }
            Net.ui {
                if (!filterGate.isCurrent(token) || isFinishing || isDestroyed) return@ui
                shown.clear()
                shown.addAll(filtered)
                playingIndex = newPlayingIndex
                adapter.setNotifyOnChange(false)
                adapter.clear()
                adapter.addAll(rows)
                adapter.notifyDataSetChanged()
                typeBtn.text = "🎞 النوع: " + typeLabels[typeFilter]
                groupBtn.text = "🗂 " + (if (groupFilter.isEmpty()) "التصنيف" else groupFilter)
                tvCount.text = if (matched > shown.size) "عرض ${shown.size} من $matched قناة؛ استخدم البحث لتضييق النتائج" else "$matched قناة"
                onDone?.invoke()
            }
        }
    }

    /** اختيار عنصر واحد من قائمة (أزرار قابلة للتمرير والتركيز بالريموت). */
    private fun pickOne(title: String, labels: List<String>, onPick: (Int) -> Unit) {
        val sv = object : ScrollView(this) {
            override fun onMeasure(w: Int, h: Int) {
                super.onMeasure(w, View.MeasureSpec.makeMeasureSpec(px(520f), View.MeasureSpec.AT_MOST))
            }
        }
        sv.isVerticalScrollBarEnabled = false
        val col = LinearLayout(this)
        col.orientation = LinearLayout.VERTICAL
        var dlg: android.app.Dialog? = null
        for (i in labels.indices) {
            col.addView(Ui.button(this, ut, labels[i], 24f) { dlg?.dismiss(); onPick(i) },
                Ui.lpm(Ui.MATCH, Ui.WRAP, 0f, 0, 0, 0, px(8f)))
        }
        sv.addView(col)
        dlg = Ui.modal(this, ut, title, sv, Btn("إلغاء") {})
    }

    private val typeLabels = listOf("الكل", "قنوات مباشرة", "أفلام", "مسلسلات", "VOD غير مصنف")

    private fun kindOf(c: Channel): Int = IptvContentClassifier.classify(c.name, c.url, c.group)

    private fun pickType() {
        pickOne("🎞 نوع المحتوى", typeLabels) { i ->
            typeFilter = i
            groupFilter = ""
            applyFilter()
        }
    }

    private fun pickGroup() {
        val source = allChannels
        Net.bg {
            val groups = source.map { it.group }.filter { it.isNotBlank() }.distinct().sorted()
            Net.ui {
                if (isFinishing || isDestroyed) return@ui
                if (groups.isEmpty()) { toast("لا توجد تصنيفات في هذه القائمة"); return@ui }
                val labels = ArrayList<String>()
                labels.add("الكل")
                labels.addAll(groups)
                pickOne("🗂 اختر التصنيف", labels) { i ->
                    groupFilter = if (i == 0) "" else labels[i]
                    applyFilter()
                }
            }
        }
    }

    private fun editPlaylist() {
        val lists = prefs.iptvPlaylists
        if (lists.isEmpty()) { toast("لا توجد قوائم مضافة للتعديل (➕ قائمة M3U)"); return }
        pickOne("✏ اختر القائمة", lists.map { it.substringBefore("||") }) { idx ->
            val old = lists[idx]
            val box = LinearLayout(this)
            box.orientation = LinearLayout.VERTICAL
            val name = Ui.edit(this, ut, "اسم القائمة")
            val url = Ui.edit(this, ut, "https://example.com/list.m3u")
            name.setText(old.substringBefore("||"))
            url.setText(old.substringAfter("||", ""))
            box.addView(name, Ui.lpm(Ui.MATCH, Ui.WRAP, 0f, 0, 0, 0, px(10f)))
            box.addView(url)
            Ui.modal(this, ut, "✏ تعديل القائمة", box,
                Btn("✔ حفظ") {
                    val u = url.text.toString().trim()
                    val n = name.text.toString().trim().replace("||", " ").ifBlank { "قائمة" }
                    if (u.startsWith("http://") || u.startsWith("https://")) {
                        val cur = ArrayList<String>(lists)
                        cur[idx] = n + "||" + u
                        prefs.iptvPlaylists = cur
                        if (u != old.substringAfter("||", "")) cacheFor(old.substringAfter("||", "")).delete()
                        rebuildTabs()
                        val ti = tabs.indexOfFirst { it.title == "🔖 " + n }
                        selectTab(if (ti >= 0) ti else 0)
                        toast("تم حفظ التعديل")
                    } else {
                        toast("الرابط غير صحيح — لم يُحفظ التعديل")
                    }
                },
                Btn("إلغاء") {})
        }
    }

    // ---------- التشغيل ----------
    private fun play(index: Int) {
        if (index < 0 || index >= shown.size) return
        val ch = shown[index]
        playingIndex = index
        playingUrl = ch.url
        recordHistory(ch)
        tvStatus.text = "⏳ " + ch.name
        try {
            val p = player ?: run {
                tvStatus.text = "⚠ مشغل الفيديو غير متاح؛ أعد فتح شاشة البث"
                return
            }
            val mediaItem = MediaItem.Builder().setUri(ch.url).apply {
                if (com.uobidah.launcher.core.IptvStreamTypeResolver.isHls(ch.url)) {
                    setMimeType(androidx.media3.common.MimeTypes.APPLICATION_M3U8)
                }
            }.build()
            p.setMediaItem(mediaItem)
            p.prepare()
            p.playWhenReady = true
        } catch (e: Exception) {
            tvStatus.text = "⚠ تعذر تشغيل القناة: " + (e.message ?: "خطأ غير معروف").take(100)
        }
    }

    private fun recordHistory(ch: Channel) {
        val cur = ArrayList<String>(prefs.iptvHistory)
        cur.removeAll { it.substringAfter("||", "") == ch.url }
        cur.add(0, ch.name.replace("||", " ") + "||" + ch.url)
        prefs.iptvHistory = cur.take(50)
    }

    @UnstableApi
    @Suppress("DEPRECATION")
    private fun setFull(on: Boolean) {
        fullscreen = on
        listPanel.visibility = if (on) View.GONE else View.VISIBLE
        playlistBar.visibility = if (on) View.GONE else View.VISIBLE
        headRow.visibility = if (on) View.GONE else View.VISIBLE
        controls.visibility = if (on) View.GONE else View.VISIBLE
        if (on) {
            // املأ الشاشة مع تكبير متناسب لإزالة الأشرطة السوداء؛ قد يُقص جزء بسيط من أطراف الفيديو.
            playerView.setResizeMode(AspectRatioFrameLayout.RESIZE_MODE_ZOOM)
            // إزالة الهوامش الخارجية نهائياً؛ المشغل يأخذ مساحة الشاشة كلها.
            rootLayout.setPadding(0, 0, 0, 0)
            rootLayout.setBackgroundColor(android.graphics.Color.BLACK)
            contentRow.layoutParams = LinearLayout.LayoutParams(Ui.MATCH, 0, 1f)
            playerContainer.layoutParams = LinearLayout.LayoutParams(Ui.MATCH, Ui.MATCH, 0f)
            window.addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN)
            window.decorView.systemUiVisibility = (View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_FULLSCREEN
                or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY)
            playerView.requestFocus()
        } else {
            playerView.setResizeMode(AspectRatioFrameLayout.RESIZE_MODE_FIT)
            rootLayout.setPadding(px(40f), px(28f), px(40f), px(24f))
            rootLayout.background = bgGradient()
            contentRow.layoutParams = LinearLayout.LayoutParams(Ui.MATCH, 0, 1f)
            playerContainer.layoutParams = LinearLayout.LayoutParams(0, Ui.MATCH, 1.5f)
            window.clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN)
            window.decorView.systemUiVisibility = (View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_FULLSCREEN
                or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY)
            list.requestFocus()
        }
        rootLayout.requestLayout()
        contentRow.requestLayout()
    }

    private fun importM3uFile() {
        val pick = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "*/*"
            putExtra(Intent.EXTRA_MIME_TYPES, arrayOf(
                "audio/x-mpegurl", "application/x-mpegURL", "application/vnd.apple.mpegurl", "text/plain"
            ))
        }
        try { startActivityForResult(pick, importM3uRequest) }
        catch (_: Exception) { toast("تعذر فتح مدير الملفات على هذا الجهاز") }
    }

    @Suppress("OVERRIDE_DEPRECATION", "DEPRECATION")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != importM3uRequest || resultCode != RESULT_OK) return
        val uri = data?.data ?: return
        val queued = Net.submitBg {
            val result = runCatching {
                val dir = playlistsDir() ?: throw IllegalArgumentException("مجلد القوائم غير متاح")
                val staging = File(dir, "import-${java.lang.Long.toHexString(System.nanoTime())}.tmp")
                try {
                    contentResolver.openInputStream(uri)?.use { input -> copyBounded(input, staging) }
                        ?: throw IllegalArgumentException("تعذر قراءة الملف")
                    require(com.uobidah.launcher.core.M3uParser.hasM3uHeader(staging)) { "الملف ليس قائمة M3U صحيحة" }
                    val channels = M3u.parse(staging)
                    require(channels.isNotEmpty()) { "القائمة لا تحتوي روابط قنوات صالحة" }
                    val display = runCatching { contentResolver.query(uri, null, null, null, null)?.use { c ->
                        val index = c.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                        if (index >= 0 && c.moveToFirst()) c.getString(index) else null
                    } }.getOrNull()
                    val rawName = (display ?: "قائمة-${System.currentTimeMillis()}.m3u").substringBeforeLast('.')
                    val safeName = rawName.replace(Regex("[^\\p{L}\\p{N} _.-]"), "_").trim().ifBlank { "قائمة" }
                    var target = File(dir, "$safeName.m3u")
                    var n = 2
                    while (target.exists()) { target = File(dir, "$safeName-$n.m3u"); n++ }
                    require(staging.renameTo(target)) { "تعذر تثبيت ملف القائمة المستورد" }
                    target.nameWithoutExtension to channels.size
                } finally {
                    staging.delete()
                }
            }
            Net.ui {
                result.onSuccess { (name, count) ->
                    rebuildTabs()
                    val index = tabs.indexOfFirst { it.title == "📁 $name" }
                    if (index >= 0) selectTab(index)
                    toast("تم استيراد «$name» — $count قناة في قائمة مستقلة")
                }.onFailure { toast("تعذر استيراد القائمة: ${playlistErrorText(it)}") }
            }
        }
        if (!queued) toast("التطبيق مشغول الآن؛ لم يتم استيراد الملف. حاول مرة أخرى بعد قليل.")
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (fullscreen) {
            when (keyCode) {
                KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_CHANNEL_UP -> {
                    if (playingIndex > 0) play(playingIndex - 1)
                    return true
                }
                KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent.KEYCODE_CHANNEL_DOWN -> {
                    if (playingIndex >= 0 && playingIndex < shown.size - 1) play(playingIndex + 1)
                    return true
                }
            }
        }
        return super.onKeyDown(keyCode, event)
    }

    @Suppress("OVERRIDE_DEPRECATION", "DEPRECATION")
    override fun onBackPressed() {
        when {
            chooserOverlay != null -> {
                closeListChooser()
                playerView.requestFocus()
            }
            fullscreen -> setFull(false)
            else -> super.onBackPressed()
        }
    }

    // ---------- دليل البرامج (EPG) ----------
    private fun epgRow(c: Channel): String {
        if (!Epg.hasData()) return c.name
        val now = Epg.nowNext(c.tvgId, c.name).first
        return if (now.isNullOrBlank()) c.name else c.name + "  •  " + now
    }

    private fun epgStatus(c: Channel): String {
        if (!Epg.hasData()) return c.name
        val (now, next) = Epg.nowNext(c.tvgId, c.name)
        var s = c.name
        if (!now.isNullOrBlank()) s += " — الآن: " + now
        if (!next.isNullOrBlank()) s += " • بعده: " + next
        return s
    }

    private fun loadEpg(force: Boolean, report: Boolean = false) {
        val url = prefs.iptvEpgUrl
        if (url.isBlank()) return
        Net.bg {
            val ok = Epg.load(applicationContext, url, force)
            Net.ui {
                if (ok) applyFilter()
                if (report) toast(if (ok) "تم تحميل دليل البرامج (" + Epg.channelCount() + " قناة)" else "تعذر تحميل دليل البرامج، تأكد من الرابط")
            }
        }
    }

    private fun epgDialog() {
        val box = LinearLayout(this)
        box.orientation = LinearLayout.VERTICAL
        box.addView(Ui.label(this, "الصق رابط دليل برامج XMLTV الخاص بك (يدعم .gz). تظهر «الآن / بعده» للقنوات التي يطابق معرّفها أو اسمها الدليل.", 24f, ut.dim),
            Ui.lpm(Ui.MATCH, Ui.WRAP, 0f, 0, 0, 0, px(12f)))
        val url = Ui.edit(this, ut, "https://example.com/epg.xml.gz")
        url.setText(prefs.iptvEpgUrl)
        box.addView(url)
        Ui.modal(this, ut, "📅 دليل البرامج (EPG)", box,
            Btn("✔ حفظ وتحميل") {
                val u = url.text.toString().trim()
                if (u.isEmpty()) {
                    prefs.iptvEpgUrl = ""
                    Epg.clear()
                    applyFilter()
                    toast("تم إيقاف دليل البرامج")
                } else if (u.startsWith("http://") || u.startsWith("https://")) {
                    prefs.iptvEpgUrl = u
                    toast("جاري تحميل الدليل...")
                    loadEpg(true, true)
                } else {
                    toast("الرابط غير صحيح")
                }
            },
            Btn("إلغاء") {})
    }

    // ---------- القائمة الافتراضية ----------
    private fun toggleDefault(i: Int) {
        val title = tabs.getOrNull(i)?.title ?: return
        if (prefs.iptvDefaultTab == title) {
            prefs.iptvDefaultTab = ""
            toast("أُلغيت القائمة الافتراضية: ستُفتح آخر قائمة استخدمتها")
        } else {
            prefs.iptvDefaultTab = title
            toast("📌 «" + title + "» ستُفتح أولاً في كل مرة")
        }
        rebuildTabs()
    }

    // ---------- حساب Xtream Codes ----------
    private fun addXtreamDialog() {
        val box = LinearLayout(this)
        box.orientation = LinearLayout.VERTICAL
        box.addView(Ui.label(this, "أدخل بيانات اشتراكك الخاص (السيرفر واسم المستخدم وكلمة المرور) وسيظهر كتبويب.", 24f, ut.dim),
            Ui.lpm(Ui.MATCH, Ui.WRAP, 0f, 0, 0, 0, px(12f)))
        val name = Ui.edit(this, ut, "اسم القائمة")
        val host = Ui.edit(this, ut, "السيرفر مثال: http://example.com:8080")
        val user = Ui.edit(this, ut, "اسم المستخدم")
        val pass = Ui.edit(this, ut, "كلمة المرور")
        box.addView(name, Ui.lpm(Ui.MATCH, Ui.WRAP, 0f, 0, 0, 0, px(10f)))
        box.addView(host, Ui.lpm(Ui.MATCH, Ui.WRAP, 0f, 0, 0, 0, px(10f)))
        box.addView(user, Ui.lpm(Ui.MATCH, Ui.WRAP, 0f, 0, 0, 0, px(10f)))
        box.addView(pass)
        Ui.modal(this, ut, "🔑 إضافة حساب Xtream", box,
            Btn("✔ حفظ") {
                var h = host.text.toString().trim().trimEnd('/')
                val u = user.text.toString().trim()
                val p = pass.text.toString().trim()
                if (h.isNotEmpty() && !h.startsWith("http://") && !h.startsWith("https://")) h = "http://" + h
                if (h.length > 8 && u.isNotEmpty() && p.isNotEmpty()) {
                    val enc = { s: String -> java.net.URLEncoder.encode(s, "UTF-8") }
                    val link = h + "/get.php?username=" + enc(u) + "&password=" + enc(p) + "&type=m3u_plus&output=m3u8"
                    val n = name.text.toString().trim().replace("||", " ").ifBlank { "Xtream " + (prefs.iptvPlaylists.size + 1) }
                    val cur = ArrayList<String>(prefs.iptvPlaylists)
                    if (cur.none { it.substringAfter("||", "") == link }) cur.add(n + "||" + link)
                    prefs.iptvPlaylists = cur
                    rebuildTabs()
                    val idx = tabs.indexOfFirst { it.title == "🔖 " + n }
                    selectTab(if (idx >= 0) idx else 0)
                    toast("تمت إضافة الحساب")
                } else {
                    toast("أكمل السيرفر واسم المستخدم وكلمة المرور")
                }
            },
            Btn("إلغاء") {})
    }

    // ---------- روابط M3U خارجية ----------
    private fun addM3uDialog() {
        val box = LinearLayout(this)
        box.orientation = LinearLayout.VERTICAL
        box.addView(Ui.label(this, "اختبر الرابط أولاً: لن تُحفظ القائمة إلا بعد استجابة صحيحة وقراءة قناة واحدة على الأقل. لا يتم حفظ أي رابط تجريبي.", 24f, ut.dim),
            Ui.lpm(Ui.MATCH, Ui.WRAP, 0f, 0, 0, 0, px(12f)))
        val name = Ui.edit(this, ut, "اسم القائمة (مثال: رياضة)")
        val url = Ui.edit(this, ut, "https://example.com/list.m3u")
        box.addView(name, Ui.lpm(Ui.MATCH, Ui.WRAP, 0f, 0, 0, 0, px(10f)))
        box.addView(url)
        Ui.modal(this, ut, "➕ إضافة قائمة M3U", box,
            Btn("اختبر الرابط واحفظه") {
                val u = url.text.toString().trim()
                val n = name.text.toString().trim().replace("||", " ").ifBlank { "قائمة " + (prefs.iptvPlaylists.size + 1) }
                if (!(u.startsWith("http://", true) || u.startsWith("https://", true))) {
                    toast("الرابط يجب أن يبدأ بـ http:// أو https://")
                    return@Btn
                }
                toast("جاري اختبار الرابط وقراءة القنوات...")
                val uiToken = playlistLoadGate.begin()
                val queued = Net.submitBg {
                    val download = File(cacheDir, "m3u-test-${java.lang.Long.toHexString(System.nanoTime())}.tmp")
                    val result = runCatching {
                        Net.downloadToFile(u, download, timeoutMs = 45_000, maxBytes = MAX_PLAYLIST_BYTES)
                        require(com.uobidah.launcher.core.M3uParser.hasM3uHeader(download)) { "الرابط لا يعيد قائمة M3U" }
                        val channels = M3u.parse(download)
                        require(channels.isNotEmpty()) { "لم يتم العثور على قنوات صالحة" }
                        channels
                    }
                    Net.ui {
                        if (isFinishing || isDestroyed) { download.delete(); return@ui }
                        result.onSuccess { channels ->
                            val cur = ArrayList<String>(prefs.iptvPlaylists)
                            if (cur.none { it.substringAfter("||", "") == u }) cur.add(n + "||" + u)
                            prefs.iptvPlaylists = cur
                            val file = cacheFor(u)
                            val writeGate = cacheWriteGate(file)
                            val cacheToken = writeGate.begin()
                            val cacheSaved = runCatching {
                                writePlaylistCacheSafely(file, download, channels.size, writeGate, cacheToken)
                            }.isSuccess
                            if (playlistLoadGate.isCurrent(uiToken)) {
                                rebuildTabs()
                                val idx = tabs.indexOfFirst { it.title == "🔖 " + n || it.urls.contains(u) }
                                selectTab(if (idx >= 0) idx else 0)
                                toast(if (cacheSaved) "نجح الاختبار: ${channels.size} قناة. تم حفظ القائمة والكاش."
                                    else "نجح الاختبار: ${channels.size} قناة. حُفظ الرابط وسيُعاد تنزيل القائمة عند فتحها.")
                            } else {
                                toast("نجح الاختبار وحُفظ الرابط (${channels.size} قناة). لم نغيّر التبويب لأنك انتقلت إلى قائمة أخرى.")
                            }
                        }.onFailure {
                            if (playlistLoadGate.isCurrent(uiToken)) toast("فشل اختبار القائمة: ${playlistErrorText(it)}")
                        }
                        download.delete()
                    }
                }
                if (!queued) {
                    if (playlistLoadGate.isCurrent(uiToken)) {
                        toast("التطبيق مشغول الآن؛ لم يتم حفظ الرابط")
                        loadTab(false)
                    } else toast("التطبيق مشغول الآن؛ لم يتم حفظ الرابط")
                }
            },
            Btn("إلغاء") {})
    }

    private fun manageLinks() {
        val lists = prefs.iptvPlaylists
        val legacy = prefs.customM3u
        if (lists.isEmpty() && legacy.isEmpty()) {
            toast("لا توجد قوائم محفوظة (ملفات 📁 تُحذف من مجلد playlists)")
            return
        }
        val items = ArrayList<MultiPicker.Item>()
        for (e in lists) items.add(MultiPicker.Item("🔖 " + e.substringBefore("||"), "p:" + e))
        for (u in legacy) items.add(MultiPicker.Item("➕ " + u, "l:" + u))
        MultiPicker.show(this, ut, "🗑 حدّد القوائم التي تريد حذفها", items, emptyList()) { del ->
            val delP = del.filter { it.startsWith("p:") }.map { it.removePrefix("p:") }
            val delL = del.filter { it.startsWith("l:") }.map { it.removePrefix("l:") }
            prefs.iptvPlaylists = lists.filter { !delP.contains(it) }
            prefs.customM3u = legacy.filter { !delL.contains(it) }
            for (e in delP) cacheFor(e.substringAfter("||", "")).delete()
            for (u in delL) cacheFor(u).delete()
            rebuildTabs()
            loadTab(false)
        }
    }

    override fun onStop() {
        try { player?.pause() } catch (e: Exception) { }
        super.onStop()
    }

    override fun onDestroy() {
        playlistLoadGate.invalidate()
        filterHandler.removeCallbacks(filterTask)
        try {
            playerView.player = null
            player?.release()
        } catch (e: Exception) {
        }
        player = null
        super.onDestroy()
    }
}
