package com.uobidah.launcher

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.io.FileOutputStream

/**
 * اختبار رسم DuskSceneBg.
 * سبب الفشل السابق (سطر 29 «opaque»): الصنف لم يكن عليه @GraphicsMode(NATIVE) بينما كل اختبارات الرسم الأخرى عليها؛
 * بوضع LEGACY في Robolectric لا تُرسَم بكسلات فعلية فيرجع getPixel القيمة 0 (alpha=0). كود DuskSceneBg يرسم سماء معتمة.
 * canaryHarnessRasterizesPixels يفصل خلل البيئة عن خلل الرسم: لو فشل فالمشكلة في الإعداد لا في الخلفية.
 * عند نجاح التشغيل تُكتب صور PNG ناتجة من كود التطبيق في app/build/test-artifacts/ (انظر PROGRESS9_STATUS_AR.md).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class SceneBgRenderTest {
    private val sentinel = 0xFFFF00FF.toInt() // ماجنتا لا تظهر في ألوان المشهد

    private fun render(w: Int, h: Int, prefill: Int = 0): Bitmap {
        val d = DuskSceneBg()
        d.bounds = Rect(0, 0, w, h)
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        if (prefill != 0) bmp.eraseColor(prefill)
        d.draw(Canvas(bmp))
        return bmp
    }

    @Test fun canaryHarnessRasterizesPixels() {
        val bmp = Bitmap.createBitmap(8, 8, Bitmap.Config.ARGB_8888)
        Canvas(bmp).drawRect(0f, 0f, 8f, 8f, Paint().apply { color = Color.RED })
        assertTrue("بيئة الاختبار لا ترسم بكسلات (تحقق من GraphicsMode.NATIVE) — ليست مشكلة DuskSceneBg",
            bmp.getPixel(4, 4) == Color.RED)
    }

    @Test fun duskSceneDrawsOpaqueAndVariedPixelsAtTvSizes() {
        for ((w, h) in listOf(1280 to 720, 1920 to 1080)) {
            // الخلفية مملوءة بماجنتا: لو بقي أي بكسل ماجنتا فالرسم لم يغطِّ المساحة؛ ولو alpha<255 فالرسم شفاف.
            val bmp = render(w, h, sentinel)
            var uncovered = 0; var translucent = 0
            for (yy in 0 until 12) for (xx in 0 until 16) {
                val p = bmp.getPixel((xx * (w - 1)) / 15, (yy * (h - 1)) / 11)
                if (p == sentinel) uncovered++
                if ((p ushr 24) != 255) translucent++
            }
            assertTrue("$w×$h: $uncovered بكسل لم يُرسم (بقي لون الخلفية الاختباري)", uncovered == 0)
            assertTrue("$w×$h: $translucent بكسل غير معتم (alpha<255)", translucent == 0)
            val top = bmp.getPixel(w / 2, 2)
            val horizon = bmp.getPixel(w / 2, (h * 0.55f).toInt())
            assertTrue("sky must change from top to horizon", top != horizon)
            val dir = File("build/test-artifacts").apply { mkdirs() }
            FileOutputStream(File(dir, "DuskSceneBg_${w}x$h.png")).use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
    }

    @Test fun duskSceneSurvivesTinyAndEmptyBounds() {
        render(1, 1)
        val d = DuskSceneBg()
        d.bounds = Rect(0, 0, 0, 0)
        d.draw(Canvas(Bitmap.createBitmap(4, 4, Bitmap.Config.ARGB_8888)))
    }
}
