package com.uobidah.launcher

import android.app.Activity
import android.content.Context
import android.graphics.Color
import android.media.tv.TvContract
import android.media.tv.TvInputManager
import android.media.tv.TvView
import android.os.Bundle
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView

/**
 * عرض مدخل HDMI/AV مباشرة داخل اللانشر عبر TvView (واجهة Android TV العامة).
 * لا يعتمد على تطبيق تلفزيون خارجي. الحالات التي لا تنجح (مدخل غير متصل، قيد من الشركة المصنّعة)
 * تُعرض بنصها الحقيقي بدل رسالة عامة. زر الرجوع يغلق المدخل ويعود للانشر.
 */
class TvPassthroughActivity : Activity() {

    companion object {
        const val EXTRA_INPUT_ID = "input_id"
        const val EXTRA_NAME = "name"
    }

    private var tvView: TvView? = null
    private lateinit var status: TextView
    private var inputId: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        inputId = intent.getStringExtra(EXTRA_INPUT_ID) ?: ""
        val name = intent.getStringExtra(EXTRA_NAME) ?: inputId

        val root = FrameLayout(this)
        root.setBackgroundColor(Color.BLACK)

        val tv = TvView(this)
        tv.layoutParams = FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        root.addView(tv)
        tvView = tv

        status = TextView(this)
        status.setTextColor(Color.WHITE)
        status.textSize = 22f
        status.gravity = Gravity.CENTER
        status.setBackgroundColor(0x99000000.toInt())
        status.setPadding(40, 24, 40, 24)
        status.text = "جارٍ فتح $name …"
        root.addView(status, FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER))
        setContentView(root)

        tv.setCallback(object : TvView.TvInputCallback() {
            override fun onConnectionFailed(id: String?) {
                show("تعذر الاتصال بالمدخل ($id). النظام رفض الاتصال بخدمة المدخل من تطبيق خارجي، وقد يتطلب تكاملاً خاصاً بالشركة المصنّعة.")
            }

            override fun onDisconnected(id: String?) {
                show("انقطع الاتصال بخدمة المدخل.")
            }

            override fun onVideoAvailable(id: String?) {
                status.visibility = View.GONE
            }

            override fun onVideoUnavailable(id: String?, reason: Int) {
                val why = when (reason) {
                    TvInputManager.VIDEO_UNAVAILABLE_REASON_TUNING -> "جارٍ الضبط…"
                    TvInputManager.VIDEO_UNAVAILABLE_REASON_BUFFERING -> "جارٍ التحميل…"
                    TvInputManager.VIDEO_UNAVAILABLE_REASON_WEAK_SIGNAL -> "لا توجد إشارة على هذا المدخل (تحقق من الكابل والجهاز المتصل)."
                    else -> "لا يوجد فيديو متاح على هذا المدخل (رمز $reason)."
                }
                show(why)
            }
        })
    }

    private fun show(msg: String) {
        status.text = msg
        status.visibility = View.VISIBLE
    }

    override fun onStart() {
        super.onStart()
        val tv = tvView ?: return
        if (inputId.isEmpty()) {
            show("لم يُحدَّد مدخل.")
            return
        }
        try {
            tv.tune(inputId, TvContract.buildChannelUriForPassthroughInput(inputId))
        } catch (e: Exception) {
            show("فشل ضبط المدخل: " + (e.message ?: e.javaClass.simpleName))
        }
    }

    override fun onStop() {
        try { tvView?.reset() } catch (_: Exception) {}
        super.onStop()
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            finish()
            return true
        }
        return super.onKeyDown(keyCode, event)
    }
}
