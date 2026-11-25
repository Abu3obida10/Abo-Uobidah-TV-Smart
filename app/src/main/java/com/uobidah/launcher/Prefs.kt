package com.uobidah.launcher

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import com.uobidah.launcher.core.AlertRule
import com.uobidah.launcher.core.AlertType
import com.uobidah.launcher.core.FavoriteList
import com.uobidah.launcher.core.NotificationPolicy
import com.uobidah.launcher.core.SoundUri
import com.uobidah.launcher.core.SourceStatus
import com.uobidah.launcher.core.TrackedClub
import com.uobidah.launcher.core.ClubRegistry
import com.uobidah.launcher.core.BackupCodec
import com.uobidah.launcher.core.PerfLimits

/** كل إعدادات اللانشر في مكان واحد. */
class Prefs(context: Context) {

    private val appCtx: Context = context.applicationContext

    /** سياق التطبيق (لاستخدامات الخلفية مثل تحميل الشعارات). */
    val context: Context get() = appCtx

    val sp: SharedPreferences =
        context.applicationContext.getSharedPreferences("uobidah_prefs", Context.MODE_PRIVATE)

    private fun s(k: String, d: String): String = sp.getString(k, d) ?: d
    private fun putS(k: String, v: String) { sp.edit().putString(k, v).apply() }
    private fun i(k: String, d: Int): Int = sp.getInt(k, d)
    private fun putI(k: String, v: Int) { sp.edit().putInt(k, v).apply() }
    private fun b(k: String, d: Boolean): Boolean = sp.getBoolean(k, d)
    private fun putB(k: String, v: Boolean) { sp.edit().putBoolean(k, v).apply() }

    private fun readList(k: String): List<String> {
        val raw = sp.getString(k, null) ?: return emptyList()
        return try {
            val a = JSONArray(raw)
            val out = ArrayList<String>()
            for (n in 0 until a.length()) out.add(a.getString(n))
            out
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun writeList(k: String, v: List<String>) {
        val a = JSONArray()
        for (x in v) a.put(x)
        sp.edit().putString(k, a.toString()).apply()
    }

    // ---------- التحديثات التلقائية ----------
    var updateRepository: String
        get() = s("update_repository", "Abu3obida10/Abo-Uobidah-TV-Smart")
        set(value) { putS("update_repository", value.trim().take(180)) }

    var testChannel: Boolean
        get() = b("update_test_channel", false)
        set(value) { putB("update_test_channel", value) }

    var autoUpdatesEnabled: Boolean
        get() = b("auto_updates_enabled", true)
        set(value) { putB("auto_updates_enabled", value) }

    var updateReadyPath: String
        get() = s("update_ready_path", "")
        set(value) { putS("update_ready_path", value.take(1024)) }

    var updateReadyVersionName: String
        get() = s("update_ready_version_name", "")
        set(value) { putS("update_ready_version_name", value.take(80)) }

    var updateReadyVersionCode: Long
        get() = s("update_ready_version_code", "0").toLongOrNull() ?: 0L
        set(value) { putS("update_ready_version_code", value.coerceAtLeast(0L).toString()) }

    var updateReadySha256: String
        get() = s("update_ready_sha256", "")
        set(value) { putS("update_ready_sha256", value.take(64)) }

    var updateReadyNotes: String
        get() = s("update_ready_notes", "")
        set(value) { putS("update_ready_notes", value.take(4000)) }

    var updateLastNotifiedCode: Long
        get() = s("update_last_notified_code", "0").toLongOrNull() ?: 0L
        set(value) { putS("update_last_notified_code", value.coerceAtLeast(0L).toString()) }

    var updateLastCheckAt: Long
        get() = s("update_last_check_at", "0").toLongOrNull() ?: 0L
        set(value) { putS("update_last_check_at", value.coerceAtLeast(0L).toString()) }

    // ---------- المظهر ----------
    var themeIndex: Int
        get() = i("theme", 0)
        set(v) { putI("theme", v) }

    /** السمة السابقة للرجوع إليها (-1 = لا يوجد). */
    var previousThemeIndex: Int
        get() = i("theme_prev", -1)
        set(v) { putI("theme_prev", v) }

    /** يغيّر السمة ويحفظ السابقة للرجوع. */
    fun setThemeWithHistory(index: Int) {
        val cur = themeIndex
        if (index == cur) return
        previousThemeIndex = cur
        themeIndex = index
    }

    var layoutIndex: Int
        get() = i("layout", 4)
        set(v) { putI("layout", v) }

    /** ترحيل لمرة واحدة إلى الواجهة التلفزيونية الاحترافية مع الاحتفاظ بقائمة التطبيقات المحفوظة. */
    var proHomeDesignV1: Boolean
        get() = b("pro_home_design_v1", false)
        set(v) { putB("pro_home_design_v1", v) }

    /** التخطيط الذي كان مطبقًا قبل آخر تغيير (-1 = لا يوجد)، لزر «الرجوع للتخطيط السابق». */
    var previousLayoutIndex: Int
        get() = i("layout_prev", -1)
        set(v) { putI("layout_prev", v) }

    /** تصميم البطاقات والأزرار (0..9) — انظر CardDesigns. */
    var cardDesign: Int
        get() = i("card_design", 0)
        set(v) { putI("card_design", v) }

    /** بطاقات المباريات: تصميم السطح (0..9) — انظر MatchCardDesigns. */
    var matchCardDesign: Int
        get() = i("match_card_design", 0)
        set(v) { putI("match_card_design", v) }

    /** بطاقات المباريات: لوحة الألوان (0..9) — انظر MatchCardPalettes. */
    var matchCardPalette: Int
        get() = i("match_card_palette", 0)
        set(v) { putI("match_card_palette", v) }

    /** بطاقات المباريات: تخطيط المحتوى (0..9) — انظر MatchCardLayouts. */
    var matchCardLayout: Int
        get() = i("match_card_layout", 0)
        set(v) { putI("match_card_layout", v) }

    /** تخطيط شاشات الإعدادات (0..9) — انظر ScreenLayouts. */
    var screenLayout: Int
        get() = i("screen_layout", 0)
        set(v) { putI("screen_layout", v) }

    var fontIndex: Int
        get() = i("font", 0)
        set(v) { putI("font", v) }

    // ---------- نظام تحديد العناصر (مستقل عن لون الثيم) ----------
    var focusColor: Int
        get() = i("focus_color", 0xFF22D3EE.toInt())
        set(v) { putI("focus_color", v) }

    // ---------- الموقع (الطقس + الصلاة) ----------
    var cityName: String
        get() = s("city_name", "القاهرة")
        set(v) { putS("city_name", v) }

    var cityLat: Double
        get() = s("city_lat", "30.0444").toDoubleOrNull() ?: 30.0444
        set(v) { putS("city_lat", v.toString()) }

    var cityLon: Double
        get() = s("city_lon", "31.2357").toDoubleOrNull() ?: 31.2357
        set(v) { putS("city_lon", v.toString()) }

    /** المنطقة الزمنية للمدينة المختارة؛ مستقلة عن ساعة الجهاز. */
    var cityTimezone: String
        get() = s("city_timezone", "Africa/Cairo").ifBlank { "Africa/Cairo" }
        set(v) {
            val clean = v.trim().take(80)
            putS("city_timezone", clean.ifBlank { "Africa/Cairo" })
        }

    // ---------- التاريخ والصلاة ----------
    var hijriOffset: Int
        get() = i("hijri_offset", 0)
        set(v) { putI("hijri_offset", v) }

    var adhanEnabled: Boolean
        get() = b("adhan_on", true)
        set(v) { putB("adhan_on", v) }

    var adhanUrl: String
        get() = s("adhan_url", "https://www.islamcan.com/audio/adhan/azan2.mp3")
        set(v) { putS("adhan_url", v) }

    var prayerMethod: Int
        get() = i("prayer_method", 5)
        set(v) { putI("prayer_method", v) }

    // ---------- الجزيرة الديناميكية ----------
    var islandEnabled: Boolean
        get() = b("island_on", true)
        set(v) { putB("island_on", v) }

    /** 0–9: عشرة تخطيطات مستقلة للجزيرة الديناميكية. */
    var islandShape: Int
        get() = i("island_shape", 0)
        set(v) { putI("island_shape", v) }

    /** 0 صغير، 1 متوسط، 2 كبير */
    var islandSize: Int
        get() = i("island_size", 1)
        set(v) { putI("island_size", v) }

    var islandColor: Int
        get() = i("island_color", 0xFF0F172A.toInt())
        set(v) { putI("island_color", v) }

    /** 0 أعلى وسط، 1 أعلى يمين، 2 أعلى يسار، 3 أسفل وسط، 4 وسط الشاشة */
    var islandPos: Int
        get() = i("island_pos", 0)
        set(v) { putI("island_pos", v) }

    /** موضع حر بالنسبة إلى مركز الشاشة، بوحدة dp. */
    var islandOffsetX: Int
        get() = i("island_offset_x", 0)
        set(v) { putI("island_offset_x", v) }

    var islandOffsetY: Int
        get() = i("island_offset_y", 0)
        set(v) { putI("island_offset_y", v) }

    var islandCustomPosition: Boolean
        get() = b("island_custom_position", false)
        set(v) { putB("island_custom_position", v) }

    /** شفافية خلفية الجزيرة كنسبة مئوية. */
    var islandOpacity: Int
        get() = i("island_opacity", 95).coerceIn(25, 100)
        set(v) { putI("island_opacity", v.coerceIn(25, 100)) }

    var islandDuration: Int
        get() = i("island_duration", 8)
        set(v) { putI("island_duration", v) }

    var islandAdhanAlerts: Boolean
        get() = b("island_adhan", true)
        set(v) { putB("island_adhan", v) }

    var islandMatchAlerts: Boolean
        get() = b("island_match", true)
        set(v) { putB("island_match", v) }

    var islandAppAlerts: Boolean
        get() = b("island_apps", true)
        set(v) { putB("island_apps", v) }

    /** التطبيقات التي تظهر إشعاراتها في الجزيرة؛ مشتقة من سياسة الإشعارات (المصدر الوحيد). */
    var allowedApps: List<String>
        get() = if (sp.contains("notif_policy_v1")) notificationPolicy().perApp.filter { it.value.showInIsland == true }.keys.toList()
                else readList("allowed_apps")
        set(v) {
            val p = notificationPolicy()
            val chosen = v.toSet()
            var np = p
            for (pkg in (p.perApp.keys + chosen)) np = np.withApp(pkg, (np.perApp[pkg] ?: AlertRule()).copy(showInIsland = chosen.contains(pkg)))
            saveNotificationPolicy(np)
        }
    var matchFavoriteTeams: List<String>
        get() = if (sp.contains("match_favorite_teams")) readList("match_favorite_teams") else listOf("138995", "133612", "133738")
        set(v) { writeList("match_favorite_teams", v.distinct()) }

    /** League selected in the match standings screen. */
    var matchStandingsLeague: String
        get() = s("match_standings_league", "eng.1").takeIf { it in setOf("egy.1", "eng.1", "esp.1") } ?: "eng.1"
        set(v) { putS("match_standings_league", v.takeIf { it in setOf("egy.1", "eng.1", "esp.1") } ?: "eng.1") }

    var mutedNotificationApps: List<String>
        get() = readList("muted_notification_apps")
        set(v) { writeList("muted_notification_apps", v.distinct()) }

    /** التطبيقات التي يُسمح لها بإصدار صوت؛ مشتقة من سياسة الإشعارات. */
    var notificationSoundApps: List<String>
        get() = notificationPolicy().perApp.filter { it.value.soundEnabled == true }.keys.toList()
        set(v) {
            val p = notificationPolicy()
            val chosen = v.toSet()
            var np = p
            for (pkg in (p.perApp.keys + chosen)) np = np.withApp(pkg, (np.perApp[pkg] ?: AlertRule()).copy(soundEnabled = chosen.contains(pkg)))
            saveNotificationPolicy(np)
        }

    /** صوت الهدف: 0 = ملف الهدف (الشبكة + الجمهور)، 1 = بوق الملعب المولَّد. */
    var matchGoalSound: Int
        get() = i("match_goal_sound", 0).coerceIn(0, 1)
        set(v) { putI("match_goal_sound", v.coerceIn(0, 1)) }

    /** أصوات أحداث المباراة: صوت للهدف وصافرة للإنذار والطرد (مفتاحها مستقل عن صوت التنبيه العام للمباريات). */
    var matchEventSounds: Boolean
        get() = b("match_event_sounds", true)
        set(v) { putB("match_event_sounds", v) }

    /** صوت تنبيهات المباريات مستقل عن إظهارها بصريًا في الجزيرة. */
    var matchSoundEnabled: Boolean
        get() = b("match_sound_on", false)
        set(v) { putB("match_sound_on", v) }

    // ---------- التطبيقات المثبتة ----------
    /**
     * كل الحزم في المفضلة (بما فيها المخفية). المصدر الوحيد للحقيقة هو favorites_v1؛
     * المفتاح القديم pinned_apps يُقرأ مرة واحدة فقط للترحيل.
     */
    var pinnedApps: List<String>
        get() = favorites().entries.map { it.pkg }
        set(v) {
            val old = favorites()
            var next = FavoriteList()
            for (pkg in v.distinct()) next = FavoriteList(next.entries + (old.get(pkg) ?: com.uobidah.launcher.core.FavoriteEntry(pkg)))
            saveFavorites(next)
        }

    fun favorites(): FavoriteList {
        val raw = sp.getString("favorites_v1", null)
        if (raw != null) return FavoriteList.fromJson(raw)
        return FavoriteList.fromLegacy(readList("pinned_apps"), readList("favorite_app_labels"), b("favorite_app_names", true))
    }

    fun saveFavorites(list: FavoriteList) { putS("favorites_v1", list.toJson()) }

    // ---------- سياسة الإشعارات ----------
    /**
     * الطبقة العامة (تشغيل/نغمة/مستوى/اهتزاز) مأخوذة دائماً من المفاتيح العامة الحالية،
     * أما قواعد الأنواع والتطبيقات فمن notif_policy_v1 (وتُرحَّل من القوائم القديمة عند أول استخدام).
     */
    fun notificationPolicy(): NotificationPolicy {
        val raw = sp.getString("notif_policy_v1", null)
        val stored = if (raw != null) NotificationPolicy.fromJson(raw) else NotificationPolicy.fromLegacy(
            alertSoundEnabled, alertSoundUri, alertSoundVolume, alertVibrationEnabled,
            readList("allowed_apps"),
            if (sp.contains("notification_sound_apps")) readList("notification_sound_apps")
            else readList("allowed_apps").filterNot { readList("muted_notification_apps").contains(it) }
        )
        val g = AlertRule(null, alertSoundEnabled, if (alertSoundUri.isEmpty()) SoundUri.DEFAULT else alertSoundUri,
            alertSoundVolume, alertVibrationEnabled, islandDuration.coerceIn(2, 60), islandPos)
        return stored.withGlobal(g)
    }

    fun saveNotificationPolicy(p: NotificationPolicy) { putS("notif_policy_v1", p.toJson()) }

    fun updateAppRule(pkg: String, f: (AlertRule) -> AlertRule) {
        val p = notificationPolicy()
        saveNotificationPolicy(p.withApp(pkg, f(p.perApp[pkg] ?: AlertRule())))
    }

    fun updateTypeRule(t: AlertType, f: (AlertRule) -> AlertRule) {
        val p = notificationPolicy()
        saveNotificationPolicy(p.withType(t, f(p.perType[t] ?: AlertRule())))
    }

    // ---------- الأندية المضافة ومصدر المباريات ----------
    var customClubs: List<TrackedClub>
        get() = ClubRegistry.fromJson(sp.getString("custom_clubs", null))
        set(v) { putS("custom_clubs", ClubRegistry.toJson(v)) }

    var matchSourceStatus: SourceStatus
        get() = SourceStatus.fromJson(sp.getString("match_source_status", null))
        set(v) { putS("match_source_status", v.toJson()) }

    /** مصدر النتائج المفضل، مع التحويل التلقائي عند انقطاعه أو عدم تغطيته للفريق. */
    var matchProviderId: String
        get() = s("match_provider_id", "ysscores")
        set(v) { putS("match_provider_id", v) }

    /** مصدر ثانٍ (SofaScore) للمقارنة مع YSscores وقت المباراة ولجلب أحداث المباراة. */
    var matchMultiSource: Boolean
        get() = b("match_multi_source", true)
        set(v) { putB("match_multi_source", v) }

    var matchAutoFailover: Boolean
        get() = b("match_auto_failover", true)
        set(v) { putB("match_auto_failover", v) }

    /** قواعد عناوين الخوادم الخارجية. لا تُفعّل إلا إذا أدخل المستخدم عنوان خادمه بنفسه. */
    var footyLiveBaseUrl: String
        get() = s("match_footylive_url", "")
        set(v) { putS("match_footylive_url", v.trim().trimEnd('/')) }

    var arabFootballBaseUrl: String
        get() = s("match_arabfootball_url", "")
        set(v) { putS("match_arabfootball_url", v.trim().trimEnd('/')) }

    var flashscoreBaseUrl: String
        get() = s("match_flashscore_url", "")
        set(v) { putS("match_flashscore_url", v.trim().trimEnd('/')) }

    var liveScoresBaseUrl: String
        get() = s("match_livescores_url", "")
        set(v) { putS("match_livescores_url", v.trim().trimEnd('/')) }

    /** مفتاح RapidAPI اختياري ويُحفظ في Android Keystore فقط. */
    var liveScoresRapidApiKey: String
        get() = SecretStore.get(appCtx, "livescores_rapidapi_key").orEmpty()
        set(v) { if (v.isBlank()) SecretStore.remove(appCtx, "livescores_rapidapi_key") else SecretStore.put(appCtx, "livescores_rapidapi_key", v.trim()) }

    /** مفتاح Pexels API اختياري للخلفيات؛ يُحفظ في Android Keystore فقط ولا يُضمَّن في الـAPK. */
    var pexelsApiKey: String
        get() = SecretStore.get(appCtx, "pexels_api_key").orEmpty()
        set(v) { if (v.isBlank()) SecretStore.remove(appCtx, "pexels_api_key") else SecretStore.put(appCtx, "pexels_api_key", v.trim()) }

    var lastMatchProviderId: String
        get() = s("match_last_provider", "")
        set(v) { putS("match_last_provider", v) }

    /** مفتاح مزود المباريات القديم TheSportsDB (اختياري) مخزّن مشفّراً في Android Keystore. */
    var matchApiKey: String
        get() = SecretStore.get(appCtx, "match_api_key").orEmpty()
        set(v) { if (v.isBlank()) SecretStore.remove(appCtx, "match_api_key") else SecretStore.put(appCtx, "match_api_key", v.trim()) }

    // ---------- نسخ احتياطي ----------
    fun backupSnapshot(): Map<String, Any?> = sp.all

    /** يطبّق نتيجة BackupCodec.parse المتحقَّق منها دفعة واحدة. */
    fun applyBackup(entries: List<BackupCodec.Entry>) {
        val e = sp.edit()
        for (x in entries) when (x.type) {
            "s" -> e.putString(x.key, x.value as String)
            "i" -> e.putInt(x.key, x.value as Int)
            "l" -> e.putLong(x.key, x.value as Long)
            "f" -> e.putFloat(x.key, x.value as Float)
            "b" -> e.putBoolean(x.key, x.value as Boolean)
            "set" -> {
                @Suppress("UNCHECKED_CAST")
                e.putStringSet(x.key, (x.value as Set<String>))
            }
        }
        e.commit()
    }

    var pinnedInit: Boolean
        get() = b("pinned_init", false)
        set(v) { putB("pinned_init", v) }

    var askedPerms: Boolean
        get() = b("asked_perms", false)
        set(v) { putB("asked_perms", v) }

    /** فرض ظهور شاشة التفعيل عند فتح اللانشر إن لم يكن افتراضيًا. */
    var enforceDefaultHome: Boolean
        get() = b("enforce_home", true)
        set(v) { putB("enforce_home", v) }

    var setupPromptMs: Long
        get() = sp.getLong("setup_prompt_ms", 0L)
        set(v) { sp.edit().putLong("setup_prompt_ms", v).apply() }


    // ---------- تخصيص مكونات الشاشة الرئيسية ----------
    var showClockCard: Boolean
        get() = b("home_clock", true)
        set(v) { putB("home_clock", v) }
    var showHijriDate: Boolean
        get() = b("home_hijri", true)
        set(v) { putB("home_hijri", v) }
    var use24HourClock: Boolean
        get() = b("clock_24h", false)
        set(v) { putB("clock_24h", v) }
    var weatherRefreshMinutes: Int
        get() = i("weather_refresh_min", 15).coerceIn(5, 60)
        set(v) { putI("weather_refresh_min", v.coerceIn(5, 60)) }
    var animationsEnabled: Boolean
        get() = b("animations_enabled", true)
        set(v) { putB("animations_enabled", v) }
    var showWeatherCard: Boolean
        get() = b("home_weather", true)
        set(v) { putB("home_weather", v) }
    var showPrayerCard: Boolean
        get() = b("home_prayer", true)
        set(v) { putB("home_prayer", v) }
    var showPinnedApps: Boolean
        get() = b("home_pinned", true)
        set(v) { putB("home_pinned", v) }
    /** ودجت الملاحظات (R4): JSON من core.Notes. */
    var notesJson: String
        get() = s("home_notes_v1", "")
        set(v) { putS("home_notes_v1", v) }
    var showNotesCard: Boolean
        get() = b("home_notes_card", true)
        set(v) { putB("home_notes_card", v) }
    /** شبكة المناسبات الهجرية (R6). */
    var showOccasions: Boolean
        get() = b("home_occasions", true)
        set(v) { putB("home_occasions", v) }
    /** خلفية «المسجد عند الغروب» (R6) عند عدم وجود خلفية مخصصة. */
    var mosqueScene: Boolean
        get() = b("home_mosque_scene", false)
        set(v) { putB("home_mosque_scene", v) }
    /** خلفية «غروب الجبال» الأصلية (مرجع R8، مرسومة بالكود) عند عدم وجود خلفية مخصصة. */
    var duskScene: Boolean
        get() = b("home_dusk_scene", false)
        set(v) { putB("home_dusk_scene", v) }
    var showMatchFavorites: Boolean
        get() = b("home_match_favorites", true)
        set(v) { putB("home_match_favorites", v) }
    /** تصميم بطاقات الرئيسية: 0 أساسي، 1 زجاجي، 2 ملوّن، 3 مفرغ. */
    var cardStyle: Int
        get() = i("card_style", 0)
        set(v) { putI("card_style", v) }
    /** تصميم الشريط السفلي: 0 أساسي، 1 أيقونات فقط، 2 شريط ملتصق، 3 زجاجي. */
    var dockStyle: Int
        get() = i("dock_style", 0)
        set(v) { putI("dock_style", v) }
    var homeCleanV1: Boolean
        get() = b("home_clean_v1", false)
        set(v) { putB("home_clean_v1", v) }
    var showHomeDock: Boolean
        get() = b("home_dock", true)
        set(v) { putB("home_dock", v) }
    var showWifiButton: Boolean
        get() = b("top_wifi", true)
        set(v) { putB("top_wifi", v) }
    var showHdmiButton: Boolean
        get() = b("top_hdmi", true)
        set(v) { putB("top_hdmi", v) }
    var showBoosterButton: Boolean
        get() = b("top_booster", true)
        set(v) { putB("top_booster", v) }
    var showRadioButton: Boolean
        get() = b("top_radio", true)
        set(v) { putB("top_radio", v) }
    var showSearchButton: Boolean
        get() = b("top_search", true)
        set(v) { putB("top_search", v) }
    var showNotificationButton: Boolean
        get() = b("top_notifications", true)
        set(v) { putB("top_notifications", v) }
    var showSettingsButton: Boolean
        get() = b("top_settings", true)
        set(v) { putB("top_settings", v) }
    var showBranding: Boolean
        get() = b("home_branding", true)
        set(v) { putB("home_branding", v) }
    var showTvInputTile: Boolean
        get() = b("home_tv_input_tile", true)
        set(v) { putB("home_tv_input_tile", v) }
    var showClubShortcutTile: Boolean
        get() = b("home_club_shortcut_tile", true)
        set(v) { putB("home_club_shortcut_tile", v) }
    var prayerCardOpensSettings: Boolean
        get() = b("prayer_card_settings", true)
        set(v) { putB("prayer_card_settings", v) }
    var dockMatches: Boolean
        get() = b("dock_matches", true)
        set(v) { putB("dock_matches", v) }
    var dockApps: Boolean
        get() = b("dock_apps", true)
        set(v) { putB("dock_apps", v) }
    var dockRadio: Boolean
        get() = b("dock_radio", false)
        set(v) { putB("dock_radio", v) }
    var dockVideo: Boolean
        get() = b("dock_video", true)
        set(v) { putB("dock_video", v) }
    var dockWallpapers: Boolean
        get() = b("dock_wallpapers", false)
        set(v) { putB("dock_wallpapers", v) }
    var dockSettings: Boolean
        get() = b("dock_settings", true)
        set(v) { putB("dock_settings", v) }
    var dockGames: Boolean
        get() = b("dock_games", true)
        set(v) { putB("dock_games", v) }
    var dockKids: Boolean
        get() = b("dock_kids", true)
        set(v) { putB("dock_kids", v) }
    var homeKeyCapture: Boolean
        get() = b("home_key_capture", true)
        set(v) { putB("home_key_capture", v) }
    var homeRedirectVendor: Boolean
        get() = b("home_redirect_vendor", true)
        set(v) { putB("home_redirect_vendor", v) }
    var favoriteAppShowNames: Boolean
        get() = b("favorite_app_names", true)
        set(v) { putB("favorite_app_names", v) }
    var favoriteAppTileWidth: Int
        get() = i("favorite_tile_width", 230).coerceIn(150, 320)
        set(v) { putI("favorite_tile_width", v.coerceIn(150, 320)) }
    var favoriteAppIconSize: Int
        get() = i("favorite_icon_size", 92).coerceIn(48, 144)
        set(v) { putI("favorite_icon_size", v.coerceIn(48, 144)) }

    /** حقلان مفصولان بـ ||: اسم الحزمة ثم الاسم المخصص. */
    var favoriteAppLabels: List<String>
        get() = readList("favorite_app_labels")
        set(v) { writeList("favorite_app_labels", v) }

    fun favoriteLabel(pkg: String, fallback: String): String =
        favorites().get(pkg)?.displayName(fallback) ?: fallback

    fun setFavoriteLabel(pkg: String, label: String) { saveFavorites(favorites().rename(pkg, label)) }

    // ---------- صوت إشعارات التطبيقات والمباريات ----------
    var alertSoundEnabled: Boolean
        get() = b("alert_sound_on", true)
        set(v) { putB("alert_sound_on", v) }
    var alertSoundUri: String
        get() = s("alert_sound_uri", "")
        set(v) { putS("alert_sound_uri", v) }
    var alertSoundVolume: Int
        get() = i("alert_sound_volume", 80).coerceIn(0, 100)
        set(v) { putI("alert_sound_volume", v.coerceIn(0, 100)) }
    var alertVibrationEnabled: Boolean
        get() = b("alert_vibration", false)
        set(v) { putB("alert_vibration", v) }

    // ---------- الخلفية ----------
    var bgVersion: Long
        get() = sp.getLong("bg_version", 0L)
        set(v) { sp.edit().putLong("bg_version", v).apply() }

    var autoWallpaper: Boolean
        get() = b("auto_wall", false)
        set(v) { putB("auto_wall", v) }

    var lastWallpaperDay: String
        get() = s("last_wall_day", "")
        set(v) { putS("last_wall_day", v) }

    var wallpaperQuery: String
        get() = s("wall_query", "")
        set(v) { putS("wall_query", v) }

    fun wallpaperQuery(scope: String): String = s("wall_query_$scope", if (scope == "general") wallpaperQuery else "")
    fun setWallpaperQuery(scope: String, query: String) {
        val clean = query.trim().take(120)
        putS("wall_query_$scope", clean)
        if (scope == "general") wallpaperQuery = clean
    }
    fun activeWallpaperName(scope: String): String = s("wall_active_$scope", "")
    fun setActiveWallpaperName(scope: String, name: String) { putS("wall_active_$scope", name) }
    fun lastWallpaperDay(scope: String): String = s("last_wall_day_$scope", if (scope == "general") lastWallpaperDay else "")
    fun setLastWallpaperDay(scope: String, day: String) {
        putS("last_wall_day_$scope", day)
        if (scope == "general") lastWallpaperDay = day
    }
    fun lastWallpaperAttempt(scope: String): Long = sp.getLong("last_wall_attempt_$scope", 0L)
    fun setLastWallpaperAttempt(scope: String, at: Long) { sp.edit().putLong("last_wall_attempt_$scope", at).apply() }

    // ---------- IPTV والإذاعة ----------
    /** قوائم مسماة، كل واحدة تظهر كتبويب خاص بها في البث المباشر. الصيغة: الاسم||الرابط */
    var iptvPlaylists: List<String>
        get() = readList("iptv_playlists")
        set(v) { writeList("iptv_playlists", v) }

    /** تم مسح قوائم IPTV القديمة بعد التحديث إلى المصادر الثلاثة الجديدة. */
    var iptvResetV2: Boolean
        get() = sp.getBoolean("iptv_reset_v2", false)
        set(v) { sp.edit().putBoolean("iptv_reset_v2", v).apply() }

    /** القنوات المفضلة في البث المباشر. الصيغة: الاسم||الرابط */
    var iptvFavorites: List<String>
        get() = readList("iptv_favorites")
        set(v) { writeList("iptv_favorites", v) }

    /** مفاتيح تنبيهات الصلاة التي أُطلقت (تبقى بعد إعادة تشغيل الخدمة/الجهاز لمنع التكرار). */
    var prayerFired: List<String>
        get() = readList("prayer_fired")
        set(v) { writeList("prayer_fired", v) }

    /** سجل المشاهدة (الأحدث أولاً، حتى 50). الصيغة: الاسم||الرابط */
    var iptvHistory: List<String>
        get() = readList("iptv_history")
        set(v) { writeList("iptv_history", v) }

    var iptvLastTab: String
        get() = s("iptv_last_tab", "")
        set(v) { putS("iptv_last_tab", v) }

    /** القائمة التي تُفتح أولاً في البث المباشر (فارغ = آخر قائمة استُخدمت). */
    /** رابط دليل برامج XMLTV (اختياري، يدعم .gz). */
    var iptvEpgUrl: String
        get() = s("iptv_epg_url", "")
        set(v) { putS("iptv_epg_url", v.trim().take(500)) }

    var iptvDefaultTab: String
        get() = s("iptv_default_tab", "")
        set(v) { putS("iptv_default_tab", v) }

    var customM3u: List<String>
        get() = readList("custom_m3u")
        set(v) { writeList("custom_m3u", v) }

    /** كل عنصر بصيغة: الاسم||الرابط */
    var customStations: List<String>
        get() = readList("custom_stations")
        set(v) { writeList("custom_stations", v) }

    var radioFeaturedOnly: Boolean
        get() = b("radio_featured_only", true)
        set(v) { putB("radio_featured_only", v) }

    // ---------- الأداء ----------
    var servicePollSeconds: Int
        get() = PerfLimits.nearest(i("perf_poll_sec", 60), PerfLimits.POLL_SECONDS)
        set(v) { putI("perf_poll_sec", PerfLimits.nearest(v, PerfLimits.POLL_SECONDS)) }
    var matchCheckSeconds: Int
        get() = PerfLimits.nearest(i("perf_match_check_sec", 300), PerfLimits.MATCH_CHECK_SECONDS)
        set(v) { putI("perf_match_check_sec", PerfLimits.nearest(v, PerfLimits.MATCH_CHECK_SECONDS)) }
    var matchCacheMinutes: Int
        get() = PerfLimits.nearest(i("perf_match_cache_min", 30), PerfLimits.MATCH_CACHE_MINUTES)
        set(v) { putI("perf_match_cache_min", PerfLimits.nearest(v, PerfLimits.MATCH_CACHE_MINUTES)) }
    var favoritesRefreshMinutes: Int
        get() = PerfLimits.nearest(i("perf_fav_refresh_min", 10), PerfLimits.FAVORITES_REFRESH_MINUTES)
        set(v) { putI("perf_fav_refresh_min", PerfLimits.nearest(v, PerfLimits.FAVORITES_REFRESH_MINUTES)) }
    var lowMemoryBackground: Boolean
        get() = b("perf_low_mem_bg", true)
        set(v) { putB("perf_low_mem_bg", v) }

    // ---------- مداخل التلفزيون ----------
    var tvApp: String
        get() = s("tv_app", "")
        set(v) { putS("tv_app", v) }

    var tvFullScreen: Boolean
        get() = b("tv_full", true)
        set(v) { putB("tv_full", v) }
}
