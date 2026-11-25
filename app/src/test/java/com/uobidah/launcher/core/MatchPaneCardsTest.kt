package com.uobidah.launcher.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MatchPaneCardsTest {
    // لوحة المباريات في تصميم14 على شاشة 1920: (1920-112-10) * 1.75/2.75 ≈ 1144
    private val pane = ((1920 - 112 - 10) * 1.75f / 2.75f).toInt()

    @Test fun fourMatchesShowTwoFullCardsAndAPeekOfTheThird() {
        val w = MatchPaneCards.width(pane, 12, 18, count = 4, minCardPx = 440, floorPx = 260)
        val usable = pane - 12
        assertTrue("البطاقتان الأولى والثانية كاملتان", 2 * w + 18 * 2 <= usable)
        val thirdVisible = usable - (2 * w + 2 * 18)
        assertTrue("جزء من الثالثة ظاهر (>0) ولا يكتمل (<w): $thirdVisible من $w", thirdVisible in 1 until w)
        assertTrue("البطاقة لا تقل عن الحد الأدنى للقراءة", w >= 440)
    }
    @Test fun twoMatchesSplitThePaneEvenlyWithNoPeek() {
        val w = MatchPaneCards.width(pane, 12, 18, count = 2, minCardPx = 440, floorPx = 260)
        assertEquals(((pane - 12) - 18) / 2, w)
    }
    @Test fun oneMatchFillsThePaneAndNarrowPaneFallsBackToOneSlotWithPeek() {
        assertEquals(pane - 12, MatchPaneCards.width(pane, 12, 18, count = 1, minCardPx = 440, floorPx = 260))
        val narrow = MatchPaneCards.width(600, 12, 18, count = 3, minCardPx = 440, floorPx = 260)
        assertEquals(1, MatchPaneCards.slots(600 - 12, 18, 440))
        assertTrue(narrow >= 260 && narrow < 600 - 12)
    }
}
