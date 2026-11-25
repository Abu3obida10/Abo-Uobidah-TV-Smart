package com.uobidah.launcher

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** يتحقق من بكسلات القمر المرسوم: محاق داكن، بدر مضاء، تربيع أول يضيء اليمين فقط، تربيع أخير يضيء اليسار فقط. (لم يُشغَّل في بيئة الكتابة.) */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class MoonDrawableTest {
    private fun render(age: Double): Bitmap {
        val b = Bitmap.createBitmap(200, 200, Bitmap.Config.ARGB_8888)
        MoonDrawable(age).apply { setBounds(0, 0, 200, 200) }.draw(Canvas(b))
        return b
    }
    private fun lum(c: Int) = (Color.red(c) + Color.green(c) + Color.blue(c)) / 3
    private val S = com.uobidah.launcher.core.MoonPhase.SYNODIC_DAYS

    @Test fun newMoonIsDarkAtCenter() { assertTrue(lum(render(0.0).getPixel(100, 100)) < 90) }
    @Test fun fullMoonIsLitAtCenterAndBothSides() {
        val b = render(S / 2)
        assertTrue(lum(b.getPixel(100, 100)) > 180); assertTrue(lum(b.getPixel(40, 100)) > 180); assertTrue(lum(b.getPixel(160, 100)) > 180)
    }
    @Test fun firstQuarterLightsRightSideOnly() {
        val b = render(S / 4)
        assertTrue("اليمين مضاء", lum(b.getPixel(150, 100)) > 180); assertTrue("اليسار معتم", lum(b.getPixel(50, 100)) < 90)
    }
    @Test fun lastQuarterLightsLeftSideOnly() {
        val b = render(S * 3 / 4)
        assertTrue("اليسار مضاء", lum(b.getPixel(50, 100)) > 180); assertTrue("اليمين معتم", lum(b.getPixel(150, 100)) < 90)
    }
}
