package com.uobidah.launcher.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ChannelTileSizeTest {
    @Test fun tilesAreNearSquareLikeR3At1080p() {
        val (w, h, gap) = ChannelTileSize.of(1920, 1080)
        assertEquals(182, w); assertEquals("مربعة كما في R3 (120×120)", w, h); assertEquals(28, gap)
    }
    @Test fun ratiosScaleWithScreen() {
        val a = ChannelTileSize.of(1280, 720); val b = ChannelTileSize.of(2560, 1440)
        assertTrue(Math.abs(a.first * 2 - b.first) <= 2)
    }
    @Test fun aboutNineTilesFitLikeReference() {
        val (w, _, gap) = ChannelTileSize.of(1920, 1080)
        val visible = (1920 * (1 - 0.043f)) / (w + gap)
        assertTrue(visible in 8.5f..9.5f)
    }
}
