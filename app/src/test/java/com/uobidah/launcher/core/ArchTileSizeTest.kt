package com.uobidah.launcher.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ArchTileSizeTest {
    @Test fun fourTilesPlusThreeGapsFitExactlyInsideAnyMeasuredWidth() {
        for (avail in listOf(900, 1064, 1100, 1269, 1500)) {
            val w = ArchTileSize.fit(avail, 18, 4)
            val used = w * 4 + 18 * 3
            assertTrue("avail=$avail used=$used", used <= avail)
            assertTrue("avail=$avail wastes more than 3px: ${avail - used}", avail - used < 4)
        }
    }
    @Test fun estimateMatchesAddSplitWeightMathAndNeverExceedsPane() {
        // 1920 عرض، حشوة جانبية 112، فاصل split 10: العمود الأيسر = (1920-112-10)*1.7/2.7
        val pane = ArchTileSize.estimatedPane(1920, 112, 10)
        assertEquals(((1920 - 112 - 10) * 1.7f / 2.7f).toInt(), pane)
        val w = ArchTileSize.width(1920, 112, 10, 18, 4)
        assertTrue(w * 4 + 18 * 3 <= pane)
    }
    @Test fun zeroCountIsZeroAndTinyPaneKeepsMinimum() {
        assertEquals(0, ArchTileSize.fit(1000, 18, 0))
        assertEquals(40, ArchTileSize.fit(10, 18, 4, minPx = 40))
    }
}
