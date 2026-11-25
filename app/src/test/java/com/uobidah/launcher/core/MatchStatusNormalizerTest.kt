package com.uobidah.launcher.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MatchStatusNormalizerTest {
    @Test fun normalizesArabicAlefAndHamza() = assertEquals("انتهت", MatchStatusNormalizer.normalize("إنتهت"))
    @Test fun removesArabicDiacriticsAndTatweel() = assertEquals("انتهت", MatchStatusNormalizer.normalize("اِنْتَـهَتْ"))
    @Test fun normalizesCancellationWithDiacritics() = assertEquals("الغيت", MatchStatusNormalizer.normalize("أُلغِيَت"))
    @Test fun normalizesSpacingAndSeparators() = assertEquals("full time", MatchStatusNormalizer.normalize(" Full-Time "))

    @Test fun explicitFinishedStatusesAreAccepted() {
        assertTrue(MatchStatusNormalizer.isFinished("FT"))
        assertTrue(MatchStatusNormalizer.isFinished("Match Finished"))
        assertTrue(MatchStatusNormalizer.isFinished("إنتهت المباراة"))
        assertTrue(MatchStatusNormalizer.isFinished("Full Time"))
        assertTrue(MatchStatusNormalizer.isFinished("AET"))
        assertTrue(MatchStatusNormalizer.isFinished("نهائي"))
        assertTrue(MatchStatusNormalizer.isFinished("بركلات الترجيح"))
        assertFalse(MatchStatusNormalizer.isFinished("FA Cup Final"))
        assertFalse(MatchStatusNormalizer.isFinished("نهائي كأس مصر"))
    }

    @Test fun scoreOrElapsedMinuteIsNotAStatus() {
        assertFalse(MatchStatusNormalizer.isFinished(""))
        assertFalse(MatchStatusNormalizer.isFinished("45'"))
        assertFalse(MatchStatusNormalizer.isFinished("2 - 1"))
    }

    @Test fun negativeEnglishAndArabicFinishedTextIsRejected() {
        assertFalse(MatchStatusNormalizer.isFinished("Not Finished"))
        assertFalse(MatchStatusNormalizer.isFinished("Not Yet Finished"))
        assertFalse(MatchStatusNormalizer.isFinished("Match Not Finished"))
        assertFalse(MatchStatusNormalizer.isFinished("لم تنته المباراة"))
        assertFalse(MatchStatusNormalizer.isFinished("لم ينته اللقاء"))
    }

    @Test fun pstIsPostponedButPostIsProviderSpecific() {
        assertTrue(MatchStatusNormalizer.isPostponed("PST"))
        assertFalse(MatchStatusNormalizer.isFinished("PST"))
        assertFalse(MatchStatusNormalizer.isPostponed("POST"))
        assertEquals("finished", MatchStatusNormalizer.canonicalize("POST", "espn"))
        assertEquals("postponed", MatchStatusNormalizer.canonicalize("PST"))
    }

    @Test fun postponedAndCanceledAreDistinct() {
        assertTrue(MatchStatusNormalizer.isPostponed("Postponed"))
        assertTrue(MatchStatusNormalizer.isCanceled("Cancelled"))
        assertFalse(MatchStatusNormalizer.isFinished("Postponed"))
        assertFalse(MatchStatusNormalizer.isFinished("Cancelled"))
    }

    @Test fun halfTimeIsLiveNotFinished() {
        assertTrue(MatchStatusNormalizer.isLive("Half Time"))
        assertTrue(MatchStatusNormalizer.isLive("الشوط الأول"))
        assertFalse(MatchStatusNormalizer.isFinished("Half Time"))
    }

    @Test fun suspendedIsSeparateFromFinishedAndCanceled() {
        assertTrue(MatchStatusNormalizer.isSuspended("Suspended"))
        assertFalse(MatchStatusNormalizer.isFinished("Suspended"))
        assertFalse(MatchStatusNormalizer.isCanceled("Suspended"))
    }


    @Test fun providerPostCodesAreCanonicalizedWithoutConfusingPst() {
        assertEquals("finished", MatchStatusNormalizer.canonicalize("POST", "espn"))
        assertEquals("POST", MatchStatusNormalizer.canonicalize("POST"))
        assertEquals("postponed", MatchStatusNormalizer.canonicalize("PST"))
        assertFalse(MatchStatusNormalizer.isFinished("Not Final"))
        assertFalse(MatchStatusNormalizer.isFinished("Not Full Time"))
    }
    @Test fun negativeExceptionalAndLivePhrasesAreNotAccepted() {
        assertFalse(MatchStatusNormalizer.isPostponed("Not Postponed"))
        assertFalse(MatchStatusNormalizer.isSuspended("Not Suspended"))
        assertFalse(MatchStatusNormalizer.isLive("Not Live"))
    }

    @Test fun genericPostCodeRemainsUnknownWithoutProviderContract() {
        assertEquals("POST", MatchStatusNormalizer.canonicalize("POST", "generic-feed"))
        assertFalse(MatchStatusNormalizer.isFinished("POST"))
        assertTrue(MatchStatusNormalizer.isFinished(MatchStatusNormalizer.canonicalize("POST", "espn")))
    }

}
