package com.uobidah.launcher

import com.uobidah.launcher.core.AlertType
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.os.Build
import android.os.IBinder
import java.time.LocalDate
import java.time.LocalTime
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit

/**
 * خدمة تعمل في الخلفية دائماً:
 *  - تنبيهات الأذان وتشغيله تلقائياً عبر الجزيرة الديناميكية
 *  - تنبيهات مباريات الأهلي وريال مدريد (قبل البداية + تغيّر النتيجة)
 *  - تحديث الخلفية يومياً
 */
class DynamicIslandOverlayService : Service() {

    private var exec: ScheduledExecutorService? = null
    private val fired = HashSet<String>()
    private val lastScore = HashMap<String, String>()
    private val lastPhaseOf = HashMap<String, String>()
    private val lastCardCheck = HashMap<String, Long>()
    private val cardsSeen = HashMap<String, HashSet<String>>()
    private val cardSofaId = HashMap<String, Pair<Long, String>>()
    private val nextCache = HashMap<String, Pair<Long, List<Match>>>()
    private var lastMatchCheck = 0L
    private var player: MediaPlayer? = null
    private var lastHomeNag = 0L
    private var prayerRetryAt = 0L
    private var prayerRetryDate: LocalDate? = null
    private var prayerRetryCityKey = ""
    private var lastWakeAt = 0L

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        try {
            startAsForeground()
        } catch (e: Exception) {
            stopSelf()
            return
        }
        val ex = Executors.newSingleThreadScheduledExecutor()
        exec = ex
        val pollSeconds = Prefs(this).servicePollSeconds.toLong()
        ex.scheduleWithFixedDelay({
            try {
                tick()
            } catch (e: Throwable) {
            }
        }, 3, pollSeconds, TimeUnit.SECONDS)
        // حلقة سريعة تعمل فقط وقت وجود مباراة جارية: فحص كل 15 ثانية لإظهار الهدف فوراً.
        // نفس الخيط الواحد، فلا تتعارض مع الدورة العادية ولا تُشغَّل خارج وقت اللعب.
        ex.scheduleWithFixedDelay({
            try {
                fastMatchTick()
            } catch (e: Throwable) {
            }
        }, 15, 15, TimeUnit.SECONDS)
    }

    private fun fastMatchTick() {
        val prefs = Prefs(this)
        if (!prefs.islandMatchAlerts && !prefs.matchSoundEnabled) return
        val now = System.currentTimeMillis()
        val hot = nextCache.values.any { entry -> entry.second.any { it.inPlayWindow(now) } }
        if (!hot) return
        lastMatchCheck = now
        checkMatches(prefs)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // startForegroundService يتطلب استدعاء startForeground في كل مرة تُبدأ فيها الخدمة
        try {
            startAsForeground()
        } catch (e: Exception) {
        }
        // إيقاظ من AlarmManager: نفحص الأذان فوراً بدل انتظار الدورة التالية
        if (intent?.getBooleanExtra("wake", false) == true) {
            exec?.execute {
                try { tick() } catch (e: Throwable) { }
            }
        }
        return START_STICKY
    }

    private fun startAsForeground() {
        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        val ch = NotificationChannel("uobidah_core", "خدمة Abo Uobidah TV", NotificationManager.IMPORTANCE_LOW)
        nm.createNotificationChannel(ch)
        val n = Notification.Builder(this, "uobidah_core")
            .setContentTitle("Abo Uobidah TV")
            .setContentText("الجزيرة الديناميكية والأذان تعمل في الخلفية")
            .setSmallIcon(R.drawable.ic_launcher)
            .setOngoing(true)
            .build()
        if (Build.VERSION.SDK_INT >= 29) {
            startForeground(1, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
        } else {
            startForeground(1, n)
        }
    }

    private fun tick() {
        val prefs = Prefs(this)
        checkPrayer(prefs)
        checkAdhkarReminder(prefs)
        checkDefaultHome(prefs)
        val now = System.currentTimeMillis()
        if (now - lastMatchCheck > prefs.matchCheckSeconds * 1000L) {
            lastMatchCheck = now
            checkMatches(prefs)
        }
        if (prefs.autoWallpaper) {
            try {
                Wallpapers.dailyUpdate(this, prefs)
            } catch (e: Exception) {
            }
        }
    }

    /**
     * تذكير أذكار الصباح/المساء عبر الجزيرة (مرة يومياً لكل منهما). الساعة بتوقيت المدينة المختارة.
     * مفاتيح الإطلاق تبدأ بـ adhkar_<اليوم> فيحذفها AdhkarActivity تلقائياً في اليوم التالي.
     */
    private fun checkAdhkarReminder(prefs: Prefs) {
        val sp = prefs.sp
        if (!sp.getBoolean("adhkar_remind", false)) return
        val hour = PrayerRepo.localNow(prefs).hour
        val day = java.text.SimpleDateFormat("yyyyMMdd", java.util.Locale.US).format(java.util.Date())
        val mh = sp.getInt("adhkar_morning_hour", 6).coerceIn(0, 22)
        val eh = sp.getInt("adhkar_evening_hour", 17).coerceIn(0, 22)
        val due = when (hour) {
            in mh..(mh + 1) -> "fm"
            in eh..(eh + 1) -> "fe"
            else -> return
        }
        // الساعة التي تقع في كلا النطاقين (إن تداخلا) تُعامل كصباح أولاً.
        val key = "adhkar_${day}_$due"
        if (sp.getBoolean(key, false)) return
        sp.edit().putBoolean(key, true).apply()
        if (due == "fm") {
            AlertDispatcher.dispatch(this, AlertType.SYSTEM, null, key, "☀ أذكار الصباح", "حان وقت أذكار الصباح — من «قرآن وإذاعة» ثم الأذكار", "☀")
        } else {
            AlertDispatcher.dispatch(this, AlertType.SYSTEM, null, key, "🌙 أذكار المساء", "حان وقت أذكار المساء — من «قرآن وإذاعة» ثم الأذكار", "🌙")
        }
    }

    /** إن لم يعد اللانشر افتراضيًا: إشعار عاجل كل 30 دقيقة يفتح شاشة التفعيل. */
    private fun checkDefaultHome(prefs: Prefs) {
        if (!prefs.enforceDefaultHome) return
        val now = System.currentTimeMillis()
        if (now - lastHomeNag < 30 * 60_000L) return
        if (DefaultLauncher.isDefaultHome(this)) return
        lastHomeNag = now
        try {
            val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
            nm.createNotificationChannel(NotificationChannel("uobidah_setup", "تفعيل اللانشر", NotificationManager.IMPORTANCE_HIGH))
            val i = Intent(this, SetupActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            val pi = android.app.PendingIntent.getActivity(
                this, 2, i, android.app.PendingIntent.FLAG_IMMUTABLE or android.app.PendingIntent.FLAG_UPDATE_CURRENT
            )
            val n = Notification.Builder(this, "uobidah_setup")
                .setContentTitle("اللانشر ليس التطبيق الافتراضي")
                .setContentText("اضغط لتعيين عُبيدالله لانشر كشاشة رئيسية")
                .setSmallIcon(R.drawable.ic_launcher)
                .setAutoCancel(true)
                .setContentIntent(pi)
                .build()
            nm.notify(2, n)
        } catch (e: Exception) {
        }
    }

    // ---------------- الأذان والتنبيهات ----------------
    private fun checkPrayer(prefs: Prefs) {
        var localNow = PrayerRepo.localNow(prefs)
        var today = localNow.toLocalDate()
        val cityKey = "${prefs.cityLat}|${prefs.cityLon}|${prefs.prayerMethod}|${prefs.cityTimezone}"
        if (cityKey != prayerRetryCityKey || today != prayerRetryDate) {
            prayerRetryCityKey = cityKey
            prayerRetryDate = today
            prayerRetryAt = 0L
        }
        if (System.currentTimeMillis() < prayerRetryAt) return
        var times = PrayerRepo.timesFor(this, prefs, today)
        if (times == null) {
            prayerRetryAt = System.currentTimeMillis() + 5 * 60_000L
            return
        }
        // قد يتعرّف مزود المواقيت على المنطقة الزمنية في أول طلب؛ أعد حساب اليوم قبل فحص الحدث.
        localNow = PrayerRepo.localNow(prefs)
        if (localNow.toLocalDate() != today) {
            today = localNow.toLocalDate()
            prayerRetryDate = today
            times = PrayerRepo.timesFor(this, prefs, today)
            if (times == null) {
                prayerRetryAt = System.currentTimeMillis() + 5 * 60_000L
                return
            }
            localNow = PrayerRepo.localNow(prefs)
        }
        prayerRetryAt = 0L
        val nowSec = localNow.toLocalTime().toSecondOfDay()
        var nextDelta = -1
        for (k in PrayerRepo.keys) {
            val lt = PrayerRepo.parse(times[k] ?: "") ?: continue
            val base = lt.toSecondOfDay()
            if (k == "Sunrise") {
                val diff = nowSec - base
                if (diff in 0..90 && markFired(prefs, today.toString() + "_" + k)) onSunrise(prefs)
                continue
            }
            for (ev in PrayerAlerts.events) {
                if (!PrayerAlerts.enabled(prefs, k, ev)) continue
                val mins = PrayerAlerts.minutes(prefs, k, ev)
                val target = when (ev) {
                    "pre" -> base - mins * 60
                    "iqama" -> base + mins * 60
                    else -> base
                }
                val diff = nowSec - target
                if (diff in 0..90 && markFired(prefs, today.toString() + "_" + k + "_" + ev)) {
                    onPrayerEvent(prefs, k, ev)
                }
                if (diff < 0 && (nextDelta < 0 || -diff < nextDelta)) nextDelta = -diff
            }
        }
        scheduleWake(nextDelta)
        if (fired.size > 300) fired.removeAll { !it.startsWith(today.toString()) && !it.startsWith("pre_") }
    }

    /** يسجّل المفتاح بشكل دائم؛ false إن أُطلق سابقاً (حتى لو أُعيد تشغيل الخدمة أو الجهاز). */
    @Synchronized private fun markFired(prefs: Prefs, key: String): Boolean {
        val cur = prefs.prayerFired
        if (cur.contains(key)) return false
        prefs.prayerFired = (cur + key).takeLast(60)
        return true
    }

    /**
     * منبّه نظامي (setAlarmClock لا يحتاج إذن SCHEDULE_EXACT_ALARM) يوقظ الجهاز عند أقرب تنبيه صلاة،
     * لأن مؤقّت الخدمة وحده يتوقف أثناء السكون العميق.
     */
    private fun scheduleWake(deltaSec: Int) {
        if (deltaSec < 0) return
        val at = System.currentTimeMillis() + deltaSec * 1000L
        if (Math.abs(at - lastWakeAt) < 5000L) return
        try {
            val am = getSystemService(ALARM_SERVICE) as android.app.AlarmManager
            val flags = android.app.PendingIntent.FLAG_IMMUTABLE or android.app.PendingIntent.FLAG_UPDATE_CURRENT
            val wake = android.app.PendingIntent.getBroadcast(this, 77, Intent(this, PrayerWakeReceiver::class.java), flags)
            val show = android.app.PendingIntent.getActivity(this, 78, Intent(this, MainLauncherActivity::class.java), flags)
            am.setAlarmClock(android.app.AlarmManager.AlarmClockInfo(at, show), wake)
            lastWakeAt = at
        } catch (e: Exception) {
        }
    }

    private fun onSunrise(prefs: Prefs) {
        AlertDispatcher.dispatch(this, AlertType.ADHAN, null, "sunrise_" + PrayerRepo.localNow(prefs).toLocalDate(), "🌅 وقت الشروق", "بارك الله لكم في يومكم", "🌅", externalSound = true)
    }

    private fun onPrayerEvent(prefs: Prefs, key: String, ev: String) {
        val msg = PrayerAlerts.message(prefs, key, ev)
        run {
            val sub = when (ev) {
                "pre" -> "استعد للصلاة"
                "at" -> "حان وقت الصلاة، أرحنا بها يا بلال"
                else -> "سوّوا صفوفكم"
            }
            // صوت الأذان يُشغَّل أدناه عبر PrayerAlerts؛ الموزّع هنا للعرض البصري فقط.
            AlertDispatcher.dispatch(this, if (ev == "iqama" || ev == "pre") AlertType.IQAMA else AlertType.ADHAN, null,
                key + "_" + ev + "_" + PrayerRepo.localNow(prefs).toLocalDate(), "🕌 $msg", sub, if (ev == "iqama") "🔔" else "🕌", externalSound = true)
        }
        // المفتاح العام «تشغيل الأذان تلقائياً» يتحكم في كل الأصوات
        if (prefs.adhanEnabled) {
            Net.ui {
                try {
                    player?.release()
                } catch (e: Exception) {
                }
                player = PrayerAlerts.play(this, prefs, key, ev)
            }
        }
    }

    // ---------------- المباريات ----------------
    private fun checkMatches(prefs: Prefs) {
        if (!prefs.islandMatchAlerts && !prefs.matchSoundEnabled) return
        val now = System.currentTimeMillis()
        for (def in MatchRepo.allTeams(prefs).filter { prefs.matchFavoriteTeams.contains(it.id) }) {
            try {
                val id = MatchRepo.teamId(prefs, def)
                val cached = nextCache[id]
                var list: List<Match>
                val refreshWindow = when {
                    cached?.second?.any { it.inPlayWindow(now) } == true -> 12_000L
                    prefs.matchProviderId == "ysscores" || cached?.second?.any { it.source == "ysscores" } == true -> 60_000L
                    else -> prefs.matchCacheMinutes * 60_000L
                }
                if (cached == null || now - cached.first > refreshWindow) {
                    list = MatchRepo.next(prefs, id)
                    nextCache[id] = Pair(now, list)
                } else {
                    list = cached.second
                }
                for (m in list) {
                    if (m.startMs <= 0L) continue
                    val untilStart = m.startMs - now
                    // تذكير قبل المباراة بـ 15 دقيقة
                    if (untilStart in 0..(15 * 60_000L)) {
                        if (fired.add("pre_" + m.id)) {
                            val mins = (untilStart / 60_000L).toInt()
                            AlertDispatcher.dispatch(
                                this, AlertType.MATCH_START, null, "pre_" + m.id,
                                "⚽ " + m.home + " × " + m.away,
                                if (mins <= 1) "المباراة تبدأ الآن" else "تبدأ بعد $mins دقيقة",
                                "⚽"
                            )
                        }
                    }
                    // متابعة الأهداف أثناء المباراة
                    if (untilStart <= 5 * 60_000L && untilStart >= -(170 * 60_000L)) {
                        val live = if (m.source == "thesportsdb") MatchRepo.event(m.id) else m
                        if (live != null && live.hasDisplayableScore(now)) {
                            // لا تعتمد على hasDisplayableScore لإثبات smart-cast؛ حقول النتيجة Int?.
                            // عند غياب أي طرف لا تُنشأ نتيجة بديلة ولا يصدر تنبيه هدف/VAR.
                            val currentHomeScore: Int? = live.homeScore
                            val currentAwayScore: Int? = live.awayScore
                            if (currentHomeScore != null && currentAwayScore != null) {
                                val score = "$currentHomeScore-$currentAwayScore"
                                val old = lastScore[m.id]
                                val minute = live.progress.trim().takeIf { it.isNotEmpty() && it.any { c -> c.isDigit() } }
                                if (old == null) {
                                    lastScore[m.id] = score
                                } else if (old != score) {
                                    // حدّث الأساس الحالي، لكن لا تقارن/تنبه إن كانت اللقطة السابقة تالفة أو ناقصة.
                                    val previousParts = old.split("-", limit = 2)
                                    val previousHomeScore: Int? = previousParts.getOrNull(0)?.toIntOrNull()
                                    val previousAwayScore: Int? = previousParts.getOrNull(1)?.toIntOrNull()
                                    lastScore[m.id] = score
                                    if (previousHomeScore != null && previousAwayScore != null) {
                                        val line = live.home + " " + currentHomeScore + " - " + currentAwayScore + " " + live.away
                                        val suffix = if (minute != null) "  •  الدقيقة " + minute else ""
                                        when {
                                            currentHomeScore + currentAwayScore < previousHomeScore + previousAwayScore -> AlertDispatcher.dispatch(
                                                this, AlertType.MATCH_GOAL, null, "var_" + m.id + "_" + score,
                                                "🚫 تم تعديل النتيجة (إلغاء هدف)", line + suffix, "🚫"
                                            )
                                            else -> {
                                                val scorer = when {
                                                    currentHomeScore > previousHomeScore && currentAwayScore == previousAwayScore -> live.home
                                                    currentAwayScore > previousAwayScore && currentHomeScore == previousHomeScore -> live.away
                                                    else -> ""
                                                }
                                                AlertDispatcher.dispatch(
                                                    this, AlertType.MATCH_GOAL, null, "goal_" + m.id + "_" + score,
                                                    if (scorer.isNotEmpty()) "⚽ هدف لـ $scorer!" else "⚽ هدف!",
                                                    line + suffix, "🥅", eventSoundRes = NotificationSound.goalRes(prefs)
                                                )
                                            }
                                        }
                                    }
                                }
                                checkCards(prefs, m, live, currentHomeScore, currentAwayScore, now)

                                // تغيّر الحالة: نهاية الشوط الأول / بداية الشوط الثاني.
                                val phase = live.status.trim().lowercase(java.util.Locale.ROOT)
                                val lastPhase = lastPhaseOf[m.id]
                                if (lastPhase == null) {
                                    lastPhaseOf[m.id] = phase
                                } else if (lastPhase != phase) {
                                    lastPhaseOf[m.id] = phase
                                    val line = live.home + " " + currentHomeScore + " - " + currentAwayScore + " " + live.away
                                    if (phase == "ht" || phase == "half time" || phase.contains("half time")) {
                                        AlertDispatcher.dispatch(this, AlertType.MATCH_START, null, "ht_" + m.id, "⏸ نهاية الشوط الأول", line, "⏸")
                                    }
                                }
                            }
                        }
                    }
                }
                // نهاية المباراة: تظهر في قائمة «السابقة» بعدما كانت تُتابَع مباشرةً
                val finishedHit = if (lastScore.isEmpty()) null else try { MatchRepo.last(prefs, id).firstOrNull { it.finished(now) && lastScore.containsKey(it.id) } } catch (e: Exception) { null }
                if (finishedHit != null && finishedHit.hasDisplayableScore(now)) {
                    // نتيجة النهاية nullable أيضًا؛ لا نرسل إشعار نهاية بفوز/تعادل غير معروف.
                    val finalHomeScore: Int? = finishedHit.homeScore
                    val finalAwayScore: Int? = finishedHit.awayScore
                    if (finalHomeScore != null && finalAwayScore != null && fired.add("end_" + finishedHit.id)) {
                        val result = when {
                            finalHomeScore > finalAwayScore -> "فوز " + finishedHit.home
                            finalAwayScore > finalHomeScore -> "فوز " + finishedHit.away
                            else -> "تعادل"
                        }
                        AlertDispatcher.dispatch(
                            this, AlertType.MATCH_END, null, "end_" + finishedHit.id,
                            "🏁 انتهت المباراة • $result",
                            finishedHit.home + " " + finalHomeScore + " - " + finalAwayScore + " " + finishedHit.away, "🏁"
                        )
                        lastScore.remove(finishedHit.id)
                        lastPhaseOf.remove(finishedHit.id)
                        lastCardCheck.remove(finishedHit.id)
                        cardsSeen.remove(finishedHit.id)
                        cardSofaId.remove(finishedHit.id)
                    }
                }
            } catch (e: Exception) {
            }
        }
    }

    /**
     * إنذار/طرد: نقرأ أحداث المباراة الجارية كل 30 ثانية (SofaScore أو TheSportsDB) ونُنبّه عند ظهور بطاقة جديدة.
     * أول قراءة لكل مباراة تُسجَّل صامتة حتى لا نُنبّه ببطاقات قديمة.
     */
    private fun checkCards(prefs: Prefs, m: Match, live: Match, hs: Int, aw: Int, now: Long) {
        try {
            if (now - (lastCardCheck[m.id] ?: 0L) < 30_000L) return
            lastCardCheck[m.id] = now
            val events: List<TimelineEvent> = when (m.source) {
                "sofascore" -> SofaScoreProvider.incidents(m.id)
                "thesportsdb" -> MatchRepo.timeline(m.id)
                else -> {
                    if (!prefs.matchMultiSource) return
                    val cached = cardSofaId[m.id]
                    val sid = if (cached != null && (cached.second.isNotEmpty() || now - cached.first < 5 * 60_000L)) cached.second else {
                        val r = try { SofaScoreProvider.resolveEventId(prefs, m.home, m.away, m.startMs, hs, aw) } catch (_: Exception) { "" }
                        cardSofaId[m.id] = Pair(now, r)
                        r
                    }
                    if (sid.isEmpty()) return
                    SofaScoreProvider.incidents(sid)
                }
            }
            val cards = events.filter {
                val k = com.uobidah.launcher.core.TimelineKind.classify(it.kind)
                k == com.uobidah.launcher.core.TimelineKind.YELLOW || k == com.uobidah.launcher.core.TimelineKind.RED
            }
            val seen = cardsSeen[m.id]
            val keys = cards.associateBy { it.minute + "|" + it.kind + "|" + it.player + "|" + it.home }
            if (seen == null) {
                cardsSeen[m.id] = HashSet(keys.keys)
                return
            }
            for ((key, e) in keys) {
                if (!seen.add(key)) continue
                val red = com.uobidah.launcher.core.TimelineKind.classify(e.kind) == com.uobidah.launcher.core.TimelineKind.RED
                val team = if (e.home) live.home else live.away
                val minute = if (e.minute.isNotBlank()) "  •  الدقيقة " + e.minute else ""
                val who = if (e.player.isNotBlank()) e.player + " (" + team + ")" else team
                AlertDispatcher.dispatch(
                    this, AlertType.MATCH_START, null, "card_" + m.id + "_" + key,
                    if (red) "🟥 طرد! بطاقة حمراء" else "🟨 إنذار! بطاقة صفراء",
                    who + minute + "\n" + live.home + " " + hs + " - " + aw + " " + live.away,
                    if (red) "🟥" else "🟨",
                    eventSoundRes = R.raw.ref_whistle
                )
            }
        } catch (_: Exception) { }
    }

    override fun onDestroy() {
        try { IslandController.dismiss(applicationContext) } catch (e: Exception) { }
        try {
            exec?.shutdownNow()
            player?.release()
        } catch (e: Exception) {
        }
        super.onDestroy()
    }
}
