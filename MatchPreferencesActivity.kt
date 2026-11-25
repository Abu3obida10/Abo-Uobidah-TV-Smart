package com.uobidah.launcher

import android.content.Intent
import android.os.Bundle
import android.widget.LinearLayout
import com.uobidah.launcher.core.ClubRegistry

/** الأندية المفضلة + إضافة/حذف أندية + مصدر البيانات ومفتاح API وحالة الاتصال. */
class MatchPreferencesActivity : SettingsBase() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setupScreen("⚽ إعدادات الأندية والمباريات")
        note("فعّل الأندية التي تريد متابعتها في بطاقة المباريات الرئيسية وتنبيهات ما قبل المباراة والأهداف.")
        toggle("تشغيل صوت تنبيهات المباريات", { prefs.matchSoundEnabled }, { prefs.matchSoundEnabled = it })
        toggle("عرض تنبيهات المباريات داخل الجزيرة الديناميكية", { prefs.islandMatchAlerts }, { prefs.islandMatchAlerts = it })
        toggle("🔊 صوت الهدف وصافرة الإنذار والطرد", { prefs.matchEventSounds }, { prefs.matchEventSounds = it })
        chips("goal_sound", listOf("🥅 هدف في الشبكة + جمهور", "📯 بوق الملعب"), prefs.matchGoalSound) { index ->
            prefs.matchGoalSound = index
            rebuild("goal_sound:$index")
        }
        action("▶ تجربة صوت الهدف") { NotificationSound.playRaw(this, NotificationSound.goalRes(prefs), prefs.alertSoundVolume) }
        action("▶ تجربة صافرة الإنذار والطرد") { NotificationSound.playRaw(this, R.raw.ref_whistle, prefs.alertSoundVolume) }

        action("🎨 تصميم وألوان وتخطيط بطاقات المباريات") { startActivity(Intent(this, ThemePickerActivity::class.java)) }
        action("🌐 فتح مركز Yalla Shoot الرسمي للنتائج والتحليل") { startActivity(Intent(this, YallaScoresActivity::class.java)) }

        section("🏟️ الأندية")
        for (team in MatchRepo.allTeams(prefs)) {
            toggle(team.arabic, { prefs.matchFavoriteTeams.contains(team.id) }, { enabled ->
                val selected = prefs.matchFavoriteTeams.toMutableList()
                if (enabled && !selected.contains(team.id)) selected.add(team.id)
                if (!enabled) selected.remove(team.id)
                prefs.matchFavoriteTeams = selected
            })
        }
        action("➕ إضافة نادٍ (بحث بالاسم في المصدر)") { addClub() }
        for (c in prefs.customClubs) {
            action("🗑 حذف النادي المضاف: " + c.nameAr) {
                prefs.customClubs = ClubRegistry.remove(prefs.customClubs, c.id)
                prefs.matchFavoriteTeams = prefs.matchFavoriteTeams.filterNot { it == c.id }
                rebuild("")
            }
        }

        section("📡 مصدر النتائج المباشرة")
        note("YSscores / يلا شووت هو المصدر الأساسي الافتراضي لبطاقات الأهلي وريال مدريد ومانشستر يونايتد. تُقرأ بطاقات المباريات العامة من صفحات الأندية وتُعرض داخل البرنامج مع كاش قصير وتحديث تلقائي؛ لا نستخدم API خاصاً أو نتجاوز تسجيل الدخول أو الحماية. لأن الموقع لا ينشر API عاماً موثقاً، قد يحتاج موصل الصفحات لتحديث إذا غيّر الموقع تصميمه.")
        note("اختر المصدر الأساسي. عند تفعيل التحويل التلقائي يجرب التطبيق المصادر المهيأة بالترتيب حتى يجد بيانات للفريق المختار. لا تُعرض البيانات المحفوظة على أنها مباشرة.")
        val providers = listOf(
            "ysscores" to "YSscores / يلا شووت",
            "livescorefootball" to "LiveScoreFootball",
            "arabfootball" to "Arab Football",
            "footylive" to "FootyLive",
            "flashscore" to "Flashscore Scraper",
            "livescoreswebapp" to "LiveScoresWebApp",
            "thesportsdb" to "TheSportsDB"
        )
        chips("match_provider", providers.map { it.second }, providers.indexOfFirst { it.first == prefs.matchProviderId }.coerceAtLeast(0)) { index ->
            prefs.matchProviderId = providers[index].first
            rebuild("match_provider:$index")
        }
        toggle("التحويل التلقائي للمصدر البديل عند التعذر أو غياب الفريق", { prefs.matchAutoFailover }, { prefs.matchAutoFailover = it })
        toggle("مصدر ثانٍ للمقارنة وقت المباراة وجلب المسجلين (SofaScore)", { prefs.matchMultiSource }, { prefs.matchMultiSource = it })
        note("المصدر المستخدم آخر مرة: " + MatchRepo.sourceLabel(prefs) + " — " + MatchRepo.status(prefs).describeAr(System.currentTimeMillis()))
        action("🔄 اختبار المصدر المحدد والمصادر البديلة") { testConnection() }

        section("🌍 عناوين خوادم المشاريع")
        note("هذه المستودعات ليست كلها خدمات عامة جاهزة للتطبيق. الخوادم المحلية يجب أن تعمل على الكمبيوتر أو الخادم نفسه، وتدخل عنوان IP الذي يمكن للتلفزيون الوصول إليه، وليس localhost.")
        action("⚽ LiveScoreFootball — خدمة عامة (إنجلترا وإسبانيا مبدئياً)") { toast("المصدر العام مهيأ تلقائياً ولا يحتاج عنوان خادم") }
        action("🟠 FootyLive — عنوان نشر الخادم /api/matches") { editEndpoint("FootyLive", prefs.footyLiveBaseUrl, { prefs.footyLiveBaseUrl = it }) }
        action("🟢 Arab Football Unified API — عنوان الخادم (المنفذ 8100)") { editEndpoint("Arab Football Unified API", prefs.arabFootballBaseUrl, { prefs.arabFootballBaseUrl = it }) }
        action("🔵 Flashscore Scraper — عنوان خادم Python /matches") { editEndpoint("Flashscore Scraper", prefs.flashscoreBaseUrl, { prefs.flashscoreBaseUrl = it }) }
        action("🟣 LiveScoresWebApp / RapidAPI — عنوان API") { editEndpoint("LiveScoresWebApp / RapidAPI", prefs.liveScoresBaseUrl, { prefs.liveScoresBaseUrl = it }) }
        action("🔑 إدخال/تغيير مفتاح RapidAPI") { editRapidKey() }
        note("LiveScoresWebApp واجهة تعتمد على RapidAPI وليست مزود بيانات مجانيًا مستقلاً؛ يلزم عنوان مضيف ومفتاح API من حسابك.")
        note("Arab Football: شغّل make pull-db ثم make serve في الكمبيوتر/الخادم؛ اكتب مثلاً http://192.168.1.20:8100. نقطة القراءة /v1/matches تقبل from وto. النسخة المنشورة تُحدّث شهرياً، أما بيانات المصدر المتصل باستمرار فتحتاج تشغيل خادمك على مخزنه المتجدد.")
        note("FootyLive: شغّل نسخة Next.js على الكمبيوتر أو خادم، واكتب عنوانها مثل http://192.168.1.20:3000؛ يستخدم التطبيق /api/matches. Flashscore Scraper: شغّل python run.py واكتب عنوان الكمبيوتر مثل http://192.168.1.20:8000؛ يستخدم /matches?sport=football. راجع شروط الموقع ومصدر البيانات قبل الاستخدام.")
        note("YSscores يدعم الربط المباشر حالياً للأهلي وريال مدريد ومانشستر يونايتد. الأندية المضافة يدوياً التي لا يوجد لها ربط معروف تنتقل تلقائياً للمصدر البديل. التحديث السريع يكون تقريباً كل 45 ثانية للبيانات الحية، مع استخدام الكاش لتقليل الطلبات.")
        note("TheSportsDB باقٍ كخيار احتياطي قديم. الخطة المجانية محدودة وقد لا توفر النتائج الحية أو كل مباريات الفرق.")
        note("مفتاح TheSportsDB: " + (if (prefs.matchApiKey.isEmpty()) "غير مُدخل — يُستخدم المفتاح العام" else "مُدخل ومخزّن مشفراً (" + SecretStore.mask(prefs.matchApiKey) + ")"))
        action("🔑 إدخال/تغيير مفتاح TheSportsDB premium") { editKey() }
        if (prefs.matchApiKey.isNotEmpty()) action("🗑 حذف مفتاح TheSportsDB") { prefs.matchApiKey = ""; MatchRepo.configure(prefs); rebuild("") }
        finishScreen()
    }

    private fun editEndpoint(title: String, current: String, save: (String) -> Unit) {
        val input = Ui.edit(this, ut, "https://server.example أو http://192.168.1.20:8000")
        input.setText(current)
        Ui.modal(this, ut, "إعداد $title", input,
            Btn("✔ حفظ") {
                val value = input.text.toString().trim().trimEnd('/')
                if (value.isNotEmpty() && !(value.startsWith("http://", true) || value.startsWith("https://", true))) {
                    toast("العنوان لازم يبدأ بـ http:// أو https://")
                } else {
                    save(value)
                    toast(if (value.isBlank()) "تم مسح عنوان الخادم" else "تم حفظ عنوان الخادم")
                    rebuild("")
                }
            }, Btn("إلغاء") {})
    }

    private fun editRapidKey() {
        val input = Ui.edit(this, ut, "RapidAPI key")
        input.setText(prefs.liveScoresRapidApiKey)
        Ui.modal(this, ut, "مفتاح RapidAPI", input,
            Btn("✔ حفظ") {
                val value = input.text.toString().trim()
                if (value.length < 8) toast("المفتاح غير مكتمل")
                else if (prefs.liveScoresRapidApiKey == value || runCatching { prefs.liveScoresRapidApiKey = value; prefs.liveScoresRapidApiKey == value }.getOrDefault(false)) {
                    toast("تم حفظ المفتاح مشفراً")
                    rebuild("")
                } else toast("تعذر حفظ المفتاح في Android Keystore")
            }, Btn("إلغاء") {})
    }

    private fun testConnection() {
        toast("جارٍ اختبار الاتصال...")
        Net.bg {
            val team = MatchRepo.allTeams(prefs).firstOrNull { it.id == prefs.matchFavoriteTeams.firstOrNull() } ?: MatchRepo.allTeams(prefs).first()
            MatchRepo.next(prefs, team.id)
            Net.ui {
                val st = MatchRepo.status(prefs)
                val message = if (st.state == com.uobidah.launcher.core.ConnState.OK) "المصدر يعمل: " + MatchRepo.sourceLabel(prefs)
                    else "تعذر جلب مباريات هذا الفريق: " + st.describeAr(System.currentTimeMillis())
                toast(message)
                rebuild("")
            }
        }
    }

    private fun editKey() {
        val input = Ui.edit(this, ut, "مفتاح API")
        Ui.modal(this, ut, "🔑 مفتاح TheSportsDB premium", input,
            Btn("✔ حفظ") {
                val v = input.text.toString().trim()
                if (v.isEmpty() || !v.all { it.isLetterOrDigit() || it == '_' || it == '-' }) {
                    toast("المفتاح غير صالح")
                } else {
                    prefs.matchApiKey = v
                    if (prefs.matchApiKey == v) { toast("تم حفظ المفتاح مشفراً"); MatchRepo.configure(prefs); rebuild("") }
                    else toast("تعذر التخزين الآمن على هذا الجهاز؛ لم يُحفظ المفتاح")
                }
            },
            Btn("إلغاء") {})
    }

    private fun addClub() {
        val input = Ui.edit(this, ut, "اسم النادي بالإنجليزية، مثل Arsenal")
        Ui.modal(this, ut, "➕ إضافة نادٍ", input,
            Btn("🔍 بحث") {
                val q = input.text.toString().trim()
                if (q.length < 3) {
                    toast("اكتب 3 أحرف على الأقل")
                } else {
                    toast("جارٍ البحث...")
                    Net.bg {
                        val res = try { MatchRepo.searchClubs(prefs, q) } catch (_: Exception) { null }
                        Net.ui {
                            when {
                                res == null -> toast("تعذر الاتصال بالمصدر")
                                res.isEmpty() -> toast("لا نتائج لهذا الاسم في المصدر")
                                else -> pickClub(res.take(8))
                            }
                        }
                    }
                }
            },
            Btn("إلغاء") {})
    }

    private fun pickClub(hits: List<MatchRepo.ClubHit>) {
        val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        var dialog: android.app.Dialog? = null
        for (h in hits) {
            col.addView(Ui.button(this, ut, h.name + " — " + h.league + (if (h.country.isNotEmpty()) " (" + h.country + ")" else ""), 22f) {
                when (val r = ClubRegistry.add(prefs.customClubs, h.id, h.name, h.name)) {
                    is ClubRegistry.AddResult.Added -> {
                        prefs.customClubs = r.list
                        prefs.matchFavoriteTeams = prefs.matchFavoriteTeams + h.id
                        toast("تمت إضافة " + h.name)
                        dialog?.dismiss()
                        rebuild("")
                    }
                    is ClubRegistry.AddResult.Rejected -> toast(r.reason)
                }
            }, Ui.lpm(Ui.MATCH, Ui.WRAP, 0f, 0, 0, 0, px(8f)))
        }
        dialog = Ui.modal(this, ut, "اختر النادي", col, Btn("إغلاق") {})
    }
}
