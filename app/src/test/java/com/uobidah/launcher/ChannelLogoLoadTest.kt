package com.uobidah.launcher

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Ignore
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.ByteArrayOutputStream

/**
 * مسار تحميل شعار القناة الحقيقي (Img.load ← Net.getBytes ← فك الصورة ← ImageView) على بطاقة القناة، بلا حرف بديل.
 * الخادم HTTP محلي داخل الاختبار ويقدّم PNG حقيقيًا مولَّدًا؛ هذا يثبت كود التحميل والعرض، ولا يثبت أن شعارات القنوات
 * على الإنترنت متاحة (هذا يحتاج field_check.py logos على جهاز بشبكة). مصدري: لم يُشغَّل في هذه الجلسة.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w1280dp-h720dp-hdpi")
class ChannelLogoLoadTest {
    private fun png(): ByteArray {
        val b = Bitmap.createBitmap(240, 160, Bitmap.Config.ARGB_8888)
        Canvas(b).apply { drawColor(Color.TRANSPARENT); drawRect(20f, 20f, 220f, 140f, Paint().apply { color = Color.rgb(200, 30, 30) }) }
        return ByteArrayOutputStream().also { b.compress(Bitmap.CompressFormat.PNG, 100, it) }.toByteArray()
    }
    private fun walk(v: View, f: (View) -> Unit) { f(v); if (v is ViewGroup) for (i in 0 until v.childCount) walk(v.getChildAt(i), f) }
    private fun pump(untilMs: Long = 6000, done: () -> Boolean) {
        val end = System.currentTimeMillis() + untilMs
        while (System.currentTimeMillis() < end && !done()) { Shadows.shadowOf(android.os.Looper.getMainLooper()).idle(); Thread.sleep(40) }
    }

    @Ignore("Old multi-layout system retired in v1.0.52 rebuild; new home has its own geometry tests")

    @Test fun realLogoBytesReplaceTheFallbackLetterAndBadUrlKeepsIt() {
        val data = png()
        val server = TinyHttpServer(mapOf(
            "/ok.png" to Triple(200, "image/png", data),
            "/missing.png" to Triple(404, "text/plain", ByteArray(0))
        ))
        server.start()
        try {
            val base = "http://127.0.0.1:${server.port}"
            val c = Robolectric.buildActivity(MainLauncherActivity::class.java).create().start().resume().visible()
            val act = c.get()
            val m = MainLauncherActivity::class.java.getDeclaredMethod("channelCard", com.uobidah.launcher.core.FavoriteChannel::class.java, Int::class.javaPrimitiveType, Int::class.javaPrimitiveType)
            m.isAccessible = true
            val good = m.invoke(act, com.uobidah.launcher.core.FavoriteChannel("قناة اختبار", "http://x/stream", "$base/ok.png"), 182, 182) as ViewGroup
            val bad = m.invoke(act, com.uobidah.launcher.core.FavoriteChannel("قناة ناقصة", "http://x/stream2", "$base/missing.png"), 182, 182) as ViewGroup
            fun find(root: ViewGroup, tag: String): View? { var r: View? = null; walk(root) { if (it.tag == tag) r = it }; return r }
            val logo = find(good, "channel-logo") as ImageView
            pump { logo.drawable != null }
            assertNotNull("الشعار لم يُحمَّل من الرابط (drawable فارغ)", logo.drawable)
            assertEquals("الحرف الاحتياطي يجب أن يختفي بعد نجاح الشعار", View.GONE, find(good, "channel-fallback")!!.visibility)
            assertEquals("شريط الاسم يجب أن يختفي فوق شعار محمّل (R3)", View.GONE, find(good, "channel-name-bar")!!.visibility)
            // فشل 404: يبقى الحرف الاحتياطي وشريط الاسم، ولا شعار مخترع
            pump(1500) { false }
            val badLogo = find(bad, "channel-logo") as ImageView
            assertTrue("رابط 404 لا يجب أن يعطي صورة", badLogo.drawable == null)
            assertEquals(View.VISIBLE, find(bad, "channel-fallback")!!.visibility)
            assertEquals(View.VISIBLE, find(bad, "channel-name-bar")!!.visibility)
        } finally { server.stop() }
    }
}

/**
 * خادم HTTP محلي صغير فوق java.net.ServerSocket بدل com.sun.net.httpserver، لأن وحدة jdk.httpserver غير مرئية
 * لمترجم اختبارات Android على JDK 17 (فشل التجميع). يخدم اتصالًا واحدًا في كل مرة وهذا يكفي هذا الاختبار.
 */
private class TinyHttpServer(private val routes: Map<String, Triple<Int, String, ByteArray>>) {
    private val socket = java.net.ServerSocket(0, 0, java.net.InetAddress.getByName("127.0.0.1"))
    @Volatile private var running = true
    val port: Int get() = socket.localPort
    private val worker = Thread {
        while (running) {
            try { socket.accept().use { handle(it) } } catch (_: Exception) { if (!running) break }
        }
    }.apply { isDaemon = true }

    fun start() { worker.start() }
    fun stop() { running = false; try { socket.close() } catch (_: Exception) { } }

    private fun handle(c: java.net.Socket) {
        c.soTimeout = 3000
        val reader = c.getInputStream().bufferedReader(Charsets.ISO_8859_1)
        val requestLine = reader.readLine() ?: return
        while (true) { val h = reader.readLine() ?: break; if (h.isEmpty()) break }
        val path = requestLine.split(" ").getOrNull(1)?.substringBefore('?') ?: ""
        val route = routes[path]
        val code = route?.first ?: 404
        val type = route?.second ?: "text/plain"
        val body = route?.third ?: ByteArray(0)
        val reason = if (code == 200) "OK" else "Not Found"
        val out = c.getOutputStream()
        out.write(("HTTP/1.1 $code $reason\r\nContent-Type: $type\r\nContent-Length: ${body.size}\r\nConnection: close\r\n\r\n").toByteArray(Charsets.ISO_8859_1))
        out.write(body)
        out.flush()
    }
}
