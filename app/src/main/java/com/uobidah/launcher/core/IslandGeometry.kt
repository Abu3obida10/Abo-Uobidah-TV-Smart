package com.uobidah.launcher.core

import kotlin.math.roundToInt

/** Geometry for a TV overlay. Offsets are persisted in dp; WindowManager coordinates are px. */
data class IslandPoint(val xPx: Int, val yPx: Int)

data class IslandOffsetLimits(val xDp: Int, val yDp: Int)

object IslandGeometry {
    const val TOP_CENTER = 0
    const val TOP_RIGHT = 1
    const val TOP_LEFT = 2
    const val BOTTOM_CENTER = 3
    const val CENTER = 4

    fun dpToPx(dp: Int, density: Float): Int = (dp * density.coerceAtLeast(0.1f)).roundToInt()

    /**
     * Returns a safe top-left position for the measured overlay.
     * Custom offsets are relative to the screen centre and are clamped so the whole
     * overlay remains visible inside the configured safe margin.
     */
    fun place(
        screenWidthPx: Int,
        screenHeightPx: Int,
        viewWidthPx: Int,
        viewHeightPx: Int,
        position: Int,
        custom: Boolean,
        offsetXDp: Int,
        offsetYDp: Int,
        density: Float,
        marginDp: Int = 24
    ): IslandPoint {
        val sw = screenWidthPx.coerceAtLeast(1)
        val sh = screenHeightPx.coerceAtLeast(1)
        val vw = viewWidthPx.coerceIn(1, sw)
        val vh = viewHeightPx.coerceIn(1, sh)
        val margin = dpToPx(marginDp, density).coerceAtMost(minOf(sw, sh) / 4)
        val maxLeft = (sw - vw - margin).coerceAtLeast(margin)
        val maxTop = (sh - vh - margin).coerceAtLeast(margin)

        val raw = if (custom) {
            val cx = (sw - vw) / 2f + dpToPx(offsetXDp, density)
            val cy = (sh - vh) / 2f + dpToPx(offsetYDp, density)
            IslandPoint(cx.roundToInt(), cy.roundToInt())
        } else {
            when (position) {
                TOP_RIGHT -> IslandPoint(sw - vw - margin, margin)
                TOP_LEFT -> IslandPoint(margin, margin)
                BOTTOM_CENTER -> IslandPoint((sw - vw) / 2, sh - vh - margin)
                CENTER -> IslandPoint((sw - vw) / 2, (sh - vh) / 2)
                else -> IslandPoint((sw - vw) / 2, margin)
            }
        }
        return IslandPoint(
            raw.xPx.coerceIn(margin.coerceAtMost(maxLeft), maxLeft),
            raw.yPx.coerceIn(margin.coerceAtMost(maxTop), maxTop)
        )
    }

    /** Largest free movement from the centre while retaining the complete overlay on screen. */
    fun limits(
        screenWidthPx: Int,
        screenHeightPx: Int,
        viewWidthPx: Int,
        viewHeightPx: Int,
        density: Float,
        marginDp: Int = 24
    ): IslandOffsetLimits {
        val margin = dpToPx(marginDp, density)
        val x = ((screenWidthPx - viewWidthPx) / 2f - margin).coerceAtLeast(0f) / density.coerceAtLeast(0.1f)
        val y = ((screenHeightPx - viewHeightPx) / 2f - margin).coerceAtLeast(0f) / density.coerceAtLeast(0.1f)
        return IslandOffsetLimits(x.roundToInt(), y.roundToInt())
    }
}
