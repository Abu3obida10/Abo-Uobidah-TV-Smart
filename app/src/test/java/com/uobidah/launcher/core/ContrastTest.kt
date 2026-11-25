package com.uobidah.launcher.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ContrastTest {
    private val white = 0xFFFFFFFF.toInt()
    private val black = 0xFF000000.toInt()

    @Test fun ratioOfBlackOnWhiteIs21() {
        assertEquals(21.0, Contrast.ratio(black, white), 0.01)
        assertEquals(1.0, Contrast.ratio(white, white), 0.001)
    }

    @Test fun onColorKeepsPreferredWhenReadable() {
        val navy = 0xFF1E3A8A.toInt()
        assertEquals(white, Contrast.onColor(navy, white))
    }

    @Test fun onColorSwitchesAwayFromWhiteOnGold() {
        val gold = 0xFFFBBF24.toInt()
        val out = Contrast.onColor(gold, white)
        assertTrue(out != white)
        assertTrue(Contrast.ratio(out, gold) >= 4.5)
    }

    @Test fun ensureDarkensAccentOnWhite() {
        val mint = 0xFF059669.toInt()
        val card = 0xFFFBFDFE.toInt()
        val out = Contrast.ensure(mint, card)
        assertTrue(Contrast.ratio(out, card) >= 4.5)
    }

    @Test fun ensureLightensAccentOnDark() {
        val darkBlue = 0xFF1D4ED8.toInt()
        val bg = 0xFF050B18.toInt()
        assertTrue(Contrast.ratio(Contrast.ensure(darkBlue, bg), bg) >= 4.5)
    }

    @Test fun fitBackgroundReachesTarget() {
        val pink = 0xFFDB2777.toInt()
        val fitted = Contrast.fitBackground(pink, white)
        assertTrue(Contrast.ratio(white, fitted) >= 4.5)
    }

    @Test fun unchangedWhenAlreadyFine() {
        val bg = 0xFF111111.toInt()
        assertEquals(white, Contrast.ensure(white, bg))
        assertEquals(bg, Contrast.fitBackground(bg, white))
    }
}
