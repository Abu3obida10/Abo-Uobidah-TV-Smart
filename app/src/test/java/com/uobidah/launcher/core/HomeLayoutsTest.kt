package com.uobidah.launcher.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeLayoutsTest {
    @Test fun exposesTwentyOneDistinctSelectableTemplates() {
        assertEquals(26, HomeLayouts.all.size)
        assertEquals(HomeLayouts.all.size, HomeLayouts.all.map { it.index }.toSet().size)
        assertEquals(21, HomeLayouts.featured.size)
        assertEquals(21, HomeLayouts.featuredIndices.size)
        assertTrue(HomeLayouts.featured.any { it.index == HomeLayouts.TV_INDEX })
        assertEquals(21, HomeLayouts.featured.map { it.template }.toSet().size)
    }

    @Test fun everyLayoutUsesTheSingleSidebarAndNoBottomDock() {
        for (layout in HomeLayouts.all) {
            assertTrue("${layout.nameAr} missing top bar", HomeSection.TOP in layout.order)
            assertFalse("${layout.nameAr} must not render a bottom dock", HomeSection.DOCK in layout.order)
            assertEquals("${layout.nameAr} has duplicate sections", layout.order.size, layout.order.toSet().size)
            assertTrue("${layout.nameAr} has unknown sections", layout.order.all { it in HomeSection.values() })
        }
    }

    @Test fun specializedTemplatesHaveTheirRequiredContent() {
        assertTrue(HomeSection.GRID in HomeLayouts.get(20).order)
        assertTrue(HomeSection.CHANNELS in HomeLayouts.get(21).order)
        assertTrue(HomeSection.FAV in HomeLayouts.get(22).order)
        assertEquals(listOf(HomeCard.PRAYER), HomeLayouts.get(2).cards.map { it.first })
        assertEquals(listOf(HomeCard.WEATHER, HomeCard.PRAYER), HomeLayouts.get(11).cards.map { it.first })
        assertEquals(listOf(HomeCard.WEATHER, HomeCard.PRAYER), HomeLayouts.get(13).cards.map { it.first })
        assertTrue(HomeSection.CHANNELS in HomeLayouts.get(17).order && HomeSection.GRID in HomeLayouts.get(17).order)
        assertTrue(HomeSection.FAV in HomeLayouts.get(18).order && HomeSection.CHANNELS in HomeLayouts.get(18).order)
        assertEquals(listOf(HomeCard.WEATHER), HomeLayouts.get(22).cards.map { it.first })
        assertEquals(listOf(HomeCard.PRAYER), HomeLayouts.get(23).cards.map { it.first })
        assertEquals(HomeLayoutTemplate.TV_REFERENCE, HomeLayouts.get(HomeLayouts.TV_INDEX).template)
        // R8: بطاقة صلاة + مباريات + تطبيقات، وبطاقة البث المباشر تُبنى في الواجهة وليست قسمًا مستقلًا.
        val dusk = HomeLayouts.get(24)
        assertEquals(HomeLayoutTemplate.DUSK_BOARD, dusk.template)
        assertEquals(listOf(HomeCard.PRAYER), dusk.cards.map { it.first })
        assertTrue(HomeSection.FAV in dusk.order && HomeSection.PINNED in dusk.order)
        // R9: بطاقتا طقس وصلاة مع شبكة تطبيقات ومباريات.
        val board = HomeLayouts.get(25)
        assertEquals(HomeLayoutTemplate.DASHBOARD, board.template)
        assertEquals(listOf(HomeCard.WEATHER, HomeCard.PRAYER), board.cards.map { it.first })
        assertTrue(HomeSection.GRID in board.order && HomeSection.FAV in board.order)
    }

    @Test fun everyFeaturedLayoutHasAnActualDifferentComposition() {
        val templates = HomeLayouts.featured.map { it.template }
        assertEquals("Featured templates must not repeat", templates.size, templates.toSet().size)
        val contentShapes = HomeLayouts.featured.map { layout ->
            layout.order.joinToString() + "|" + layout.cards.map { it.first }.joinToString() + "|" + layout.cardsHeightDp + "|" + layout.template
        }
        assertEquals("Featured compositions must be unique", contentShapes.size, contentShapes.toSet().size)
        assertTrue(HomeLayouts.featured.all { it.order.none { section -> section == HomeSection.DOCK } })
    }

    @Test fun unknownIndexFallsBackToTvLayout() {
        assertEquals(HomeLayouts.TV_INDEX, HomeLayouts.get(999).index)
        assertEquals(HomeLayouts.TV_INDEX, HomeLayouts.get(-1).index)
    }
}
