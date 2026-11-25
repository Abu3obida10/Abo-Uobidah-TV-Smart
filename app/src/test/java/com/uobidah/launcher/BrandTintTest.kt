package com.uobidah.launcher
import android.graphics.Color
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w1280dp-h720dp-hdpi")
class BrandTintTest {
    @Test fun renderTilesLightDarkMulticolorFocus() {
        val ctx = org.robolectric.RuntimeEnvironment.getApplication()
        val t = Themes.all[0]
        val row = android.widget.LinearLayout(ctx); row.setBackgroundColor(t.bgBottom); row.setPadding(30, 30, 30, 30)
        fun icon(kind: Int): android.graphics.drawable.Drawable {
            val b = android.graphics.Bitmap.createBitmap(96, 96, android.graphics.Bitmap.Config.ARGB_8888)
            val c = android.graphics.Canvas(b); val p = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)
            when (kind) {
                0 -> { p.color = Color.rgb(229, 9, 20); c.drawRoundRect(8f, 8f, 88f, 88f, 18f, 18f, p) }
                1 -> { p.color = Color.WHITE; c.drawRoundRect(8f, 8f, 88f, 88f, 18f, 18f, p) }
                2 -> { p.color = Color.rgb(255, 235, 59); c.drawRoundRect(8f, 8f, 88f, 88f, 18f, 18f, p) }
                3 -> { p.color = Color.rgb(66, 133, 244); c.drawRect(8f, 8f, 48f, 88f, p); p.color = Color.rgb(234, 67, 53); c.drawRect(48f, 8f, 88f, 88f, p) }
                else -> { p.color = Color.rgb(20, 20, 20); c.drawRoundRect(8f, 8f, 88f, 88f, 18f, 18f, p) }
            }
            return android.graphics.drawable.BitmapDrawable(ctx.resources, b)
        }
        val names = listOf("Netflix", "White", "Yellow", "Multi", "Dark")
        for ((i, n) in names.withIndex()) {
            val tile = android.widget.LinearLayout(ctx); tile.orientation = android.widget.LinearLayout.VERTICAL; tile.gravity = android.view.Gravity.CENTER
            tile.setPadding(20, 20, 20, 20)
            val ic = icon(i)
            Ui.focusable(tile, t, 28f, BrandTint.fillFor(ic, t.card, t.text))
            val iv = android.widget.ImageView(ctx); iv.setImageDrawable(ic); tile.addView(iv, android.widget.LinearLayout.LayoutParams(140, 140))
            tile.addView(Ui.label(ctx, n, 24f, t.text, true))
            row.addView(tile, android.widget.LinearLayout.LayoutParams(260, 260).apply { setMargins(10, 10, 10, 10) })
            if (i == 3) tile.isFocusableInTouchMode = true
            if (i == 3) tile.requestFocus()
        }
        row.measure(android.view.View.MeasureSpec.makeMeasureSpec(1500, android.view.View.MeasureSpec.EXACTLY), android.view.View.MeasureSpec.makeMeasureSpec(330, android.view.View.MeasureSpec.EXACTLY))
        row.layout(0, 0, 1500, 330)
        val out = android.graphics.Bitmap.createBitmap(1500, 330, android.graphics.Bitmap.Config.ARGB_8888); row.draw(android.graphics.Canvas(out))
        java.io.File("/tmp/abo/shots6").mkdirs()
        java.io.FileOutputStream("/tmp/abo/shots6/brand_tiles.png").use { out.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
    }
    private fun solid(c: Int) = IntArray(576) { c }
    private val card = Color.rgb(22, 30, 48)
    private val text = Color.WHITE
    @Test fun redIconGetsRedTintWithReadableLabel() {
        val f = BrandTint.safeFill(BrandTint.dominant(solid(Color.rgb(229, 9, 20))), card, text)
        assertTrue(Color.red(f) > Color.blue(f)); assertNotEquals(card, f)
        assertTrue(BrandTint.contrast(f, text) >= 4.5)
    }
    @Test fun lightYellowStillReadable() {
        val f = BrandTint.safeFill(BrandTint.dominant(solid(Color.rgb(255, 235, 59))), card, text)
        assertTrue(BrandTint.contrast(f, text) >= 4.5)
    }
    @Test fun whiteAndBlackAndTransparentFallBackToCard() {
        assertEquals(card, BrandTint.safeFill(BrandTint.dominant(solid(Color.WHITE)), card, text))
        assertEquals(card, BrandTint.safeFill(BrandTint.dominant(solid(Color.BLACK)), card, text))
        assertEquals(card, BrandTint.safeFill(BrandTint.dominant(solid(Color.TRANSPARENT)), card, text))
    }
    @Test fun multicolorMixesAndStaysReadable() {
        val px = IntArray(576) { if (it % 2 == 0) Color.rgb(66, 133, 244) else Color.rgb(234, 67, 53) }
        val f = BrandTint.safeFill(BrandTint.dominant(px), card, text)
        assertTrue(BrandTint.contrast(f, text) >= 4.5)
    }
    @Test fun darkLabelOnLightCardAlsoReadable() {
        val lightCard = Color.rgb(240, 242, 248)
        val f = BrandTint.safeFill(BrandTint.dominant(solid(Color.rgb(30, 90, 220))), lightCard, Color.rgb(15, 20, 30))
        assertTrue(BrandTint.contrast(f, Color.rgb(15, 20, 30)) >= 4.5 || f == lightCard)
    }
    /**
     * ألوان تقريبية لأيقونات تطبيقات معروفة (فاتحة وغامقة ومتعددة الألوان) على كل سمة في التطبيق.
     * هذه لوحات ألوان مكتوبة يدويًا وليست أيقونات APK حقيقية؛ الاختبار بأيقونات الجهاز الفعلية ما زال ناقصًا.
     */
    @Test fun brandPalettesStayReadableOnEveryTheme() {
        val palettes = mapOf(
            "netflix-red" to intArrayOf(Color.rgb(229, 9, 20)),
            "spotify-green" to intArrayOf(Color.rgb(30, 215, 96)),
            "snapchat-yellow" to intArrayOf(Color.rgb(255, 252, 0)),
            "youtube-red-white" to intArrayOf(Color.rgb(255, 0, 0), Color.WHITE),
            "google-multi" to intArrayOf(Color.rgb(66, 133, 244), Color.rgb(234, 67, 53), Color.rgb(251, 188, 5), Color.rgb(52, 168, 83)),
            "navy-dark" to intArrayOf(Color.rgb(10, 20, 60)),
            "white-only" to intArrayOf(Color.WHITE),
            "black-only" to intArrayOf(Color.BLACK)
        )
        var checked = 0
        for (t in Themes.all) {
            val opaque = Color.rgb(Color.red(t.card), Color.green(t.card), Color.blue(t.card))
            for ((name, colors) in palettes) {
                val px = IntArray(576) { colors[it % colors.size] }
                val f = BrandTint.safeFill(BrandTint.dominant(px), opaque, t.text)
                assertTrue("$name على ${t.name}: التباين ${BrandTint.contrast(f, t.text)}",
                    f == opaque || BrandTint.contrast(f, t.text) >= 4.5)
                checked++
            }
        }
        assertTrue(checked >= palettes.size)
    }

    /**
     * لوحات ألوان حقيقية مستخرجة من 46 أيقونة خدمة (soft وsolid) من حزمة minimal-tv-icons (MIT):
     * الملف يحوي ملخص ألوان فقط، وليس الصور. هذا أقرب لأيقونات حقيقية من اللوحات اليدوية، لكنه ما زال
     * ليس أيقونات التطبيقات المثبتة فعليًا على التلفزيون.
     */
    @Test fun brandPalettesFromMinimalTvIconsStayReadable() {
        val stream = javaClass.classLoader!!.getResourceAsStream("brand_palettes_minimal_tv_icons.csv")
        assertTrue("fixture missing", stream != null)
        val lines = stream!!.bufferedReader(Charsets.UTF_8).readLines().filter { it.isNotBlank() }
        assertTrue("expected 92 icons (46 x 2 styles)", lines.size == 92)
        var checked = 0
        for (line in lines) {
            val parts = line.split(",")
            val name = parts[0] + "/" + parts[1]
            val px = IntArray(576)
            var filled = 0
            for (entry in parts[2].split(";")) {
                val (key, shareText) = entry.split(":")
                assertTrue("$name: مفتاح لون غير صالح '$key' (المتوقع hex من 6 خانات أو transparent)",
                    key == "transparent" || Regex("[0-9a-f]{6}").matches(key))
                val count = Math.round(shareText.toDouble() * 576).toInt().coerceAtMost(576 - filled)
                val color = if (key == "transparent") 0 else (0xFF000000.toInt() or key.toInt(16))
                for (k in 0 until count) px[filled + k] = color
                filled += count
            }
            val dom = BrandTint.dominant(px)
            for (t in Themes.all) {
                val opaque = Color.rgb(Color.red(t.card), Color.green(t.card), Color.blue(t.card))
                val f = BrandTint.safeFill(dom, opaque, t.text)
                assertTrue("$name على ${t.name}: التباين ${BrandTint.contrast(f, t.text)}",
                    f == opaque || BrandTint.contrast(f, t.text) >= 4.5)
                checked++
            }
        }
        assertTrue(checked >= lines.size)
    }
}
