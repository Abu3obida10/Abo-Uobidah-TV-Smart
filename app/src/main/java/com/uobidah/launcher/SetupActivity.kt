package com.uobidah.launcher

import android.os.Bundle

/** شاشة التفعيل الإلزامي: حالة كل صلاحية + زر «تفعيل الكل بالترتيب». */
class SetupActivity : SettingsBase() {

    private var chain = false
    private val tried = HashSet<String>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setupScreen("🛡️ تفعيل اللانشر بالكامل")
        chain = intent?.getBooleanExtra("chain", false) == true
    }

    override fun onResume() {
        super.onResume()
        prefs.setupPromptMs = System.currentTimeMillis()
        render()
        if (chain) continueChain()
    }

    private fun render() {
        body.removeAllViews()
        val items = DefaultLauncher.items(this)
        val criticalOk = items.filter { it.critical }.all { it.granted }
        section(if (criticalOk) "✅ الأساسيات مفعّلة" else "⚠️ الأساسيات غير مكتملة")
        note("أندرويد يطلب موافقتك مرة واحدة على كل بند. اضغط «تفعيل الكل» وسأفتح لك كل شاشة بالترتيب.")
        action("⚡ تفعيل الكل بالترتيب") {
            tried.clear()
            chain = true
            continueChain()
        }
        for (item in items) {
            action((if (item.granted) "✅ " else "❌ ") + item.title + (if (item.critical) "  (أساسي)" else "")) {
                DefaultLauncher.request(this, item.id)
            }
            note(item.hint)
        }
        section("🔒 بدون أي ضغطة")
        note("شغّل FORCE_HOME_ADB.bat من الكمبيوتر لتفعيل كل ما سبق عبر ADB دفعة واحدة.")
        action("✔ تم") { finish() }
    }

    private fun continueChain() {
        val next = DefaultLauncher.items(this).firstOrNull { it.id != "location" && !it.granted && tried.add(it.id) }
        if (next == null) {
            chain = false
            if (DefaultLauncher.criticalOk(this)) {
                toast("✅ تم تفعيل الأساسيات")
                finish()
            }
            return
        }
        DefaultLauncher.request(this, next.id)
    }

    @Suppress("OVERRIDE_DEPRECATION", "DEPRECATION")
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
    }
}
