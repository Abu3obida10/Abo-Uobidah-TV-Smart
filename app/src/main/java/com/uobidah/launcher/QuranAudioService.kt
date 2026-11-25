package com.uobidah.launcher

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.drawable.Icon
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.uobidah.launcher.core.LastListen
import java.io.File

/** مواضع ملفات السور المنزّلة (للاستماع دون إنترنت). */
object QuranDownloads {
    fun dir(ctx: Context): File =
        (ctx.getExternalFilesDir("quran_audio") ?: File(ctx.filesDir, "quran_audio")).also { it.mkdirs() }

    fun file(ctx: Context, reciterId: Int, moshafId: Int, surah: Int): File =
        File(File(dir(ctx), "${reciterId}_$moshafId").also { it.mkdirs() }, surah.toString().padStart(3, '0') + ".mp3")

    fun has(ctx: Context, reciterId: Int, moshafId: Int, surah: Int): Boolean =
        file(ctx, reciterId, moshafId, surah).let { it.exists() && it.length() > 1024 }
}

/**
 * تشغيل سور القراء في الخلفية: قائمة تشغيل متتابعة وانتقال تلقائي للسورة التالية،
 * تكرار (بلا/سورة/الكل)، إيقاف مؤقت/استئناف، وحفظ آخر موضع استماع كل 5 ثوانٍ.
 */
class QuranAudioService : Service() {
    private var player: ExoPlayer? = null
    private val handler = Handler(Looper.getMainLooper())
    private var surahs: IntArray = IntArray(0)
    private var names: Array<String> = emptyArray()
    private var rids: IntArray = IntArray(0)
    private var mids: IntArray = IntArray(0)
    private var rnames: Array<String> = emptyArray()
    private var tick = 0

    companion object {
        const val ACTION_PLAY_QUEUE = "com.uobidah.launcher.QURAN_PLAY_QUEUE"
        const val ACTION_TOGGLE = "com.uobidah.launcher.QURAN_TOGGLE"
        const val ACTION_NEXT = "com.uobidah.launcher.QURAN_NEXT"
        const val ACTION_PREV = "com.uobidah.launcher.QURAN_PREV"
        const val ACTION_STOP = "com.uobidah.launcher.QURAN_STOP"
        const val ACTION_REPEAT = "com.uobidah.launcher.QURAN_REPEAT"
        const val ACTION_SEEK = "com.uobidah.launcher.QURAN_SEEK"
        const val KEY_LAST = "quran_last_listen"

        @Volatile var active = false
        @Volatile var reciterId = 0
        @Volatile var moshafId = 0
        @Volatile var reciterName = ""
        @Volatile var surah = 0
        @Volatile var surahName = ""
        @Volatile var playing = false
        @Volatile var loading = false
        @Volatile var positionMs = 0L
        @Volatile var durationMs = 0L
        @Volatile var repeatMode = 0      // 0 بلا تكرار، 1 سورة واحدة، 2 الكل
        @Volatile var error = ""

        fun playQueue(
            ctx: Context, reciterId: Int, moshafId: Int, reciterName: String,
            surahs: IntArray, names: Array<String>, uris: Array<String>, startIndex: Int, startPosMs: Long,
            rids: IntArray? = null, mids: IntArray? = null, rnames: Array<String>? = null
        ) {
            val i = Intent(ctx, QuranAudioService::class.java).setAction(ACTION_PLAY_QUEUE)
                .putExtra("reciterId", reciterId).putExtra("moshafId", moshafId)
                .putExtra("reciterName", reciterName).putExtra("surahs", surahs)
                .putExtra("names", names).putExtra("uris", uris)
                .putExtra("start", startIndex).putExtra("pos", startPosMs)
            // قوائم التشغيل المختلطة: لكل عنصر قارئه ومصحفه
            if (rids != null && mids != null && rnames != null) {
                i.putExtra("rids", rids).putExtra("mids", mids).putExtra("rnames", rnames)
            }
            ctx.startForegroundService(i)
        }

        fun command(ctx: Context, action: String, extra: Long = -1L) {
            if (!active && action != ACTION_STOP) return
            val i = Intent(ctx, QuranAudioService::class.java).setAction(action)
            if (extra >= 0) i.putExtra("value", extra)
            ctx.startService(i)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_PLAY_QUEUE -> startQueue(intent)
            ACTION_TOGGLE -> player?.let { if (it.isPlaying) it.pause() else it.play(); refresh() }
            ACTION_NEXT -> player?.let { if (it.hasNextMediaItem()) it.seekToNextMediaItem() }
            ACTION_PREV -> player?.let {
                if (it.currentPosition > 4000 || !it.hasPreviousMediaItem()) it.seekTo(0) else it.seekToPreviousMediaItem()
            }
            ACTION_REPEAT -> player?.let {
                repeatMode = (repeatMode + 1) % 3
                it.repeatMode = when (repeatMode) { 1 -> Player.REPEAT_MODE_ONE; 2 -> Player.REPEAT_MODE_ALL; else -> Player.REPEAT_MODE_OFF }
            }
            ACTION_SEEK -> player?.seekTo(intent.getLongExtra("value", 0L))
            ACTION_STOP -> { saveNow(); shutdown(); return START_NOT_STICKY }
        }
        return START_NOT_STICKY
    }

    private fun startQueue(i: Intent) {
        reciterId = i.getIntExtra("reciterId", 0)
        moshafId = i.getIntExtra("moshafId", 0)
        reciterName = i.getStringExtra("reciterName") ?: ""
        surahs = i.getIntArrayExtra("surahs") ?: IntArray(0)
        names = i.getStringArrayExtra("names") ?: emptyArray()
        val uris = i.getStringArrayExtra("uris") ?: emptyArray()
        val start = i.getIntExtra("start", 0).coerceIn(0, (surahs.size - 1).coerceAtLeast(0))
        val pos = i.getLongExtra("pos", 0L)
        rids = i.getIntArrayExtra("rids")?.takeIf { it.size == surahs.size } ?: IntArray(surahs.size) { reciterId }
        mids = i.getIntArrayExtra("mids")?.takeIf { it.size == surahs.size } ?: IntArray(surahs.size) { moshafId }
        rnames = i.getStringArrayExtra("rnames")?.takeIf { it.size == surahs.size } ?: Array(surahs.size) { reciterName }
        if (surahs.isEmpty() || uris.size != surahs.size) { shutdown(); return }
        try { goForeground() } catch (_: Exception) { stopSelf(); return }
        active = true
        error = ""
        var p = player
        if (p == null) {
            p = PlayerFactory.create(this)
            p.setAudioAttributes(
                AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_SPEECH).build(), true
            )
            p.setHandleAudioBecomingNoisy(true)
            // يبقي المعالج والشبكة مستيقظين أثناء التشغيل بالخلفية وإطفاء الشاشة (إذن WAKE_LOCK موجود في المانيفست)
            p.setWakeMode(C.WAKE_MODE_NETWORK)
            p.addListener(listener)
            player = p
        }
        p.repeatMode = when (repeatMode) { 1 -> Player.REPEAT_MODE_ONE; 2 -> Player.REPEAT_MODE_ALL; else -> Player.REPEAT_MODE_OFF }
        p.setMediaItems(uris.map { MediaItem.fromUri(Uri.parse(it)) }, start, pos)
        p.prepare()
        p.playWhenReady = true
        syncCurrent()
        handler.removeCallbacks(ticker)
        handler.post(ticker)
    }

    private val listener = object : Player.Listener {
        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) { syncCurrent(); saveNow() }
        override fun onPlaybackStateChanged(state: Int) {
            loading = state == Player.STATE_BUFFERING
            if (state == Player.STATE_ENDED) {
                // انتهت القائمة: نحفظ بداية آخر سورة ونُنهي الخدمة
                prefsOrNull()?.sp?.edit()?.putString(KEY_LAST, LastListen(reciterId, moshafId, surah, 0).encode())?.apply()
                shutdown()
            } else refresh()
        }
        override fun onIsPlayingChanged(isPlaying: Boolean) { playing = isPlaying; refresh() }
        override fun onPlayerError(e: PlaybackException) {
            error = "تعذر تشغيل السورة (" + e.errorCodeName + ")"
            val p = player ?: return
            if (p.hasNextMediaItem()) { p.seekToNextMediaItem(); p.prepare(); p.play() } else { refresh() }
        }
    }

    private fun prefsOrNull(): Prefs? = try { Prefs(this) } catch (_: Exception) { null }

    private fun syncCurrent() {
        val p = player ?: return
        val idx = p.currentMediaItemIndex.coerceIn(0, (surahs.size - 1).coerceAtLeast(0))
        surah = surahs.getOrElse(idx) { 0 }
        surahName = names.getOrElse(idx) { "" }
        reciterId = rids.getOrElse(idx) { reciterId }
        moshafId = mids.getOrElse(idx) { moshafId }
        reciterName = rnames.getOrElse(idx) { reciterName }
        durationMs = if (p.duration == C.TIME_UNSET) 0L else p.duration
        refresh()
    }

    private val ticker = object : Runnable {
        override fun run() {
            val p = player ?: return
            positionMs = p.currentPosition
            durationMs = if (p.duration == C.TIME_UNSET) durationMs else p.duration
            playing = p.isPlaying
            tick++
            if (tick % 5 == 0) saveNow()
            handler.postDelayed(this, 1000)
        }
    }

    private fun saveNow() {
        val p = player ?: return
        if (surah in 1..114) prefsOrNull()?.sp?.edit()
            ?.putString(KEY_LAST, LastListen(reciterId, moshafId, surah, p.currentPosition.coerceAtLeast(0)).encode())?.apply()
    }

    private fun pending(action: String, code: Int): PendingIntent =
        PendingIntent.getService(this, code, Intent(this, QuranAudioService::class.java).setAction(action),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)

    private fun buildNotification(): Notification {
        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(NotificationChannel("uobidah_quran", "تلاوات القرآن", NotificationManager.IMPORTANCE_LOW))
        fun act(title: String, a: String, c: Int) =
            Notification.Action.Builder(Icon.createWithResource(this, R.drawable.ic_launcher), title, pending(a, c)).build()
        return Notification.Builder(this, "uobidah_quran")
            .setContentTitle(if (surahName.isNotEmpty()) "🎧 $surahName" else "🎧 القرآن الكريم")
            .setContentText(reciterName)
            .setSmallIcon(R.drawable.ic_launcher)
            .setOngoing(true)
            .addAction(act("السابقة", ACTION_PREV, 1))
            .addAction(act(if (playing) "إيقاف مؤقت" else "تشغيل", ACTION_TOGGLE, 2))
            .addAction(act("التالية", ACTION_NEXT, 3))
            .addAction(act("إنهاء", ACTION_STOP, 4))
            .build()
    }

    private fun goForeground() {
        val n = buildNotification()
        if (Build.VERSION.SDK_INT >= 29) startForeground(3, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
        else startForeground(3, n)
    }

    private fun refresh() {
        try {
            (getSystemService(NOTIFICATION_SERVICE) as NotificationManager).notify(3, buildNotification())
        } catch (_: Exception) { }
    }

    private fun shutdown() {
        handler.removeCallbacks(ticker)
        try { player?.release() } catch (_: Exception) { }
        player = null
        active = false
        playing = false
        loading = false
        surah = 0
        surahName = ""
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        handler.removeCallbacks(ticker)
        try { player?.release() } catch (_: Exception) { }
        player = null
        active = false
        playing = false
        super.onDestroy()
    }
}
