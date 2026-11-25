package com.uobidah.launcher

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory

/** يبني ExoPlayer يدعم HLS و HTTP/HTTPS وتحويلات http→https. */
object PlayerFactory {
    fun create(ctx: Context): ExoPlayer {
        val http = DefaultHttpDataSource.Factory()
            .setAllowCrossProtocolRedirects(true)
            .setConnectTimeoutMs(15000)
            .setReadTimeoutMs(20000)
            .setUserAgent("Mozilla/5.0 (Linux; Android 11; TV) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/110.0 Safari/537.36")
        val ds = DefaultDataSource.Factory(ctx, http)
        return ExoPlayer.Builder(ctx)
            .setMediaSourceFactory(DefaultMediaSourceFactory(ds))
            .build()
    }
}

/** تشغيل إذاعات القرآن في الخلفية. */
class RadioService : Service() {

    private var player: ExoPlayer? = null

    companion object {
        const val ACTION_PLAY = "com.uobidah.launcher.RADIO_PLAY"
        const val ACTION_STOP = "com.uobidah.launcher.RADIO_STOP"
        @Volatile var currentName: String = ""
        @Volatile var state: String = ""   // "", "loading", "playing", "error"

        fun play(ctx: Context, name: String, url: String) {
            val i = Intent(ctx, RadioService::class.java)
            i.action = ACTION_PLAY
            i.putExtra("name", name)
            i.putExtra("url", url)
            ctx.startForegroundService(i)
        }

        fun stop(ctx: Context) {
            val i = Intent(ctx, RadioService::class.java)
            i.action = ACTION_STOP
            ctx.startService(i)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            releasePlayer()
            currentName = ""
            state = ""
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return START_NOT_STICKY
        }
        val name = intent?.getStringExtra("name") ?: ""
        val url = intent?.getStringExtra("url") ?: ""
        if (url.isEmpty()) {
            try { goForeground(name) } catch (e: Exception) { }
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return START_NOT_STICKY
        }
        try {
            goForeground(name)
        } catch (e: Exception) {
            stopSelf()
            return START_NOT_STICKY
        }
        currentName = name
        state = "loading"
        try {
            var p = player
            if (p == null) {
                p = PlayerFactory.create(this)
                p.setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(C.USAGE_MEDIA)
                        .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                        .build(),
                    true
                )
                p.addListener(object : Player.Listener {
                    override fun onPlaybackStateChanged(playbackState: Int) {
                        if (playbackState == Player.STATE_READY) state = "playing"
                        else if (playbackState == Player.STATE_BUFFERING) state = "loading"
                    }

                    override fun onPlayerError(error: PlaybackException) {
                        state = "error"
                    }
                })
                player = p
            }
            p.setMediaItem(MediaItem.fromUri(url))
            p.prepare()
            p.playWhenReady = true
        } catch (e: Exception) {
            state = "error"
        }
        return START_NOT_STICKY
    }

    private fun goForeground(name: String) {
        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(NotificationChannel("uobidah_radio", "إذاعة القرآن", NotificationManager.IMPORTANCE_LOW))
        val n = Notification.Builder(this, "uobidah_radio")
            .setContentTitle("📻 $name")
            .setContentText("إذاعة تعمل في الخلفية")
            .setSmallIcon(R.drawable.ic_launcher)
            .setOngoing(true)
            .build()
        if (Build.VERSION.SDK_INT >= 29) {
            startForeground(2, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
        } else {
            startForeground(2, n)
        }
    }

    private fun releasePlayer() {
        try {
            player?.release()
        } catch (e: Exception) {
        }
        player = null
    }

    override fun onDestroy() {
        releasePlayer()
        state = ""
        currentName = ""
        super.onDestroy()
    }
}
