package com.uobidah.launcher

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import com.uobidah.launcher.core.AlertType
import com.uobidah.launcher.core.ResolvedAlert
import com.uobidah.launcher.core.SoundUri

/** صوت موحّد لإشعارات التطبيقات والمباريات، مستقل عن ملفات أصوات الأذان. */
object NotificationSound {
    @Volatile private var player: MediaPlayer? = null

    /** متوافق مع الاستدعاءات القديمة: يحسم القرار من السياسة الحالية. */
    fun play(context: Context, prefs: Prefs, sourcePackage: String? = null) {
        val type = if (sourcePackage == null) AlertType.SYSTEM else AlertType.APP
        playResolved(context, prefs.notificationPolicy().resolve(type, sourcePackage), true)
    }

    /** ملف صوت الهدف المختار. */
    fun goalRes(prefs: Prefs): Int = if (prefs.matchGoalSound == 1) R.raw.goal_horn else R.raw.goal_net

    /** يشغّل ملف صوت من res/raw (صوت الهدف / صافرة الحكم) بمستوى الصوت المختار. */
    @Synchronized fun playRaw(context: Context, resId: Int, volumePercent: Int) {
        if (resId == 0 || volumePercent <= 0) return
        val app = context.applicationContext
        try {
            player?.release()
            val mp = MediaPlayer()
            player = mp
            mp.setAudioAttributes(AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_NOTIFICATION_EVENT)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build())
            val afd = app.resources.openRawResourceFd(resId)
            try { mp.setDataSource(afd.fileDescriptor, afd.startOffset, afd.length) } finally { afd.close() }
            val v = (volumePercent.coerceIn(0, 100)) / 100f
            mp.setVolume(v, v)
            mp.setOnPreparedListener { it.start() }
            mp.setOnCompletionListener { synchronized(this) { if (player === it) player = null }; it.release() }
            mp.setOnErrorListener { m, _, _ -> synchronized(this) { if (player === m) player = null }; m.release(); true }
            mp.prepareAsync()
        } catch (_: Exception) {
            try { player?.release() } catch (_: Exception) { }
            player = null
        }
    }

    /** يشغّل نتيجة سياسة محسومة. playSound=false يبقي الاهتزاز فقط إن كان مفعلاً. */
    @Synchronized fun playResolved(context: Context, r: ResolvedAlert, allowSound: Boolean) {
        val app = context.applicationContext
        val uri = when (r.soundUri) {
            SoundUri.SILENT -> null
            SoundUri.DEFAULT -> RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            else -> try { Uri.parse(r.soundUri) } catch (_: Exception) { RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION) }
        }
        if (allowSound && r.playSound && uri != null && r.volume > 0) {
            try {
                player?.release()
                val mp = MediaPlayer()
                player = mp
                mp.setAudioAttributes(AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_NOTIFICATION_EVENT)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build())
                mp.setDataSource(app, uri)
                val v = r.volume / 100f
                mp.setVolume(v, v)
                mp.setOnPreparedListener { it.start() }
                mp.setOnCompletionListener { synchronized(this) { if (player === it) player = null }; it.release() }
                mp.setOnErrorListener { m, _, _ -> synchronized(this) { if (player === m) player = null }; m.release(); true }
                mp.prepareAsync()
            } catch (_: Exception) {
                try { player?.release() } catch (_: Exception) { }
                player = null
            }
        }
        if (r.vibrate) {
            try {
                val vibrator = app.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
                if (vibrator.hasVibrator()) {
                    if (Build.VERSION.SDK_INT >= 26) vibrator.vibrate(VibrationEffect.createOneShot(180, VibrationEffect.DEFAULT_AMPLITUDE))
                    else {
                        @Suppress("DEPRECATION")
                        vibrator.vibrate(180)
                    }
                }
            } catch (_: Exception) { }
        }
    }
}
