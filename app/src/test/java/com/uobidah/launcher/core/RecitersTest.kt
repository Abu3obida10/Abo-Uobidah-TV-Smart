package com.uobidah.launcher.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.assertThrows
import org.junit.Test

class RecitersTest {
    private val json = """
    {"reciters":[
      {"id":1,"name":"إبراهيم الأخضر","letter":"إ","moshaf":[
        {"id":10,"name":"حفص عن عاصم - مرتل","server":"https://server6.mp3quran.net/akdr/","surah_total":3,"surah_list":"1,2,114"}]},
      {"id":1,"name":"مكرر بنفس المعرف","letter":"م","moshaf":[{"id":99,"name":"x","server":"https://s/","surah_list":"1"}]},
      {"id":2,"name":"قارئ بلا ملفات","letter":"ق","moshaf":[]},
      {"id":3,"name":"أحمد العجمي","letter":"أ","image":"https://img/x.jpg","moshaf":[
        {"id":30,"name":"ورش عن نافع - مرتل","server":"https://s3/ajm","surah_list":"1,3,300,abc"}]}
    ]}"""

    @Test fun dedupesByIdAndDropsEmpty() {
        val r = ReciterParser.parseReciters(json)
        assertEquals(listOf(1, 3), r.map { it.id })
    }

    @Test fun photoOnlyWhenProvided() {
        val r = ReciterParser.parseReciters(json)
        assertEquals("", r[0].photo)
        assertEquals("https://img/x.jpg", r[1].photo)
    }

    @Test fun surahListSanitised() {
        val r = ReciterParser.parseReciters(json)
        assertEquals(listOf(1, 3), r[1].primary!!.surahs)
        assertEquals(listOf(1, 2, 114), r[0].primary!!.surahs)
    }

    @Test fun urlBuilderPadsAndHandlesSlash() {
        val r = ReciterParser.parseReciters(json)
        assertEquals("https://server6.mp3quran.net/akdr/002.mp3", r[0].primary!!.urlFor(2))
        assertEquals("https://s3/ajm/003.mp3", r[1].primary!!.urlFor(3))
    }

    @Test fun arabicSearchIgnoresTashkeelAndAlefForms() {
        assertTrue(ArabicSearch.matches("ابراهيم", "إبراهيم الأخضر"))
        assertTrue(ArabicSearch.matches("الاخضر ابراهيم", "إبراهيم الأخضر"))
        assertTrue(ArabicSearch.matches("حفص", "حَفْص عن عاصم"))
        assertFalse(ArabicSearch.matches("ورش", "حفص عن عاصم"))
    }

    @Test fun filterByRiwayaFavoritesAndQuery() {
        val r = ReciterParser.parseReciters(json)
        assertEquals(listOf(3), ReciterFilter.filter(r, "", "ورش عن نافع - مرتل", false, emptySet()).map { it.id })
        assertEquals(listOf(1), ReciterFilter.filter(r, "", "", true, setOf(1)).map { it.id })
        assertEquals(listOf(3), ReciterFilter.filter(r, "العجمي", "", false, emptySet()).map { it.id })
    }

    @Test fun lastListenRoundTrip() {
        val l = LastListen.decode(LastListen(5, 7, 18, 123456L).encode())!!
        assertEquals(18, l.surah); assertEquals(123456L, l.positionMs)
        assertNull(LastListen.decode("bad"))
        assertNull(LastListen.decode("1|2|200|0"))
    }

    @Test fun mergesMoshafsAcrossDuplicateReciterIds() {
        val payload = """{"reciters":[
          {"id":7,"name":"قارئ واحد","moshaf":[{"id":1,"name":"حفص","server":"https://a.example/","surah_list":"1,2"}]},
          {"id":7,"name":"قارئ واحد","moshaf":[{"id":2,"name":"ورش","server":"https://b.example/","surah_list":"3,4"}]}
        ]}"""
        val reciters = ReciterParser.parseReciters(payload)
        assertEquals(1, reciters.size)
        assertEquals(listOf(1, 2), reciters.single().moshafs.map { it.id })
        assertEquals(listOf("حفص", "ورش"), reciters.single().riwayat)
    }

    @Test fun skipsUnsafeAudioAndPhotoUrls() {
        val payload = """{"reciters":[
          {"id":8,"name":"قارئ آمن","image":"http://img.example/a.jpg","moshaf":[
            {"id":1,"name":"غير آمن","server":"http://audio.example/","surah_list":"1"},
            {"id":2,"name":"آمن","server":"https://audio.example/","surah_list":"1"}]},
          {"id":9,"name":"قارئ غير آمن","moshaf":[{"id":1,"name":"x","server":"https://user:pass@audio.example/","surah_list":"1"}]}
        ]}"""
        val reciters = ReciterParser.parseReciters(payload)
        assertEquals(1, reciters.size)
        assertEquals("", reciters.single().photo)
        assertEquals(2, reciters.single().primary!!.id)
    }

    @Test fun surahUrlRejectsInvalidNumber() {
        val m = ReciterParser.parseReciters(json).first().primary!!
        assertThrows(IllegalArgumentException::class.java) { m.urlFor(115) }
    }

    @Test fun suwarParserKeepsOnlyValidIdsAndNames() {
        val suwar = ReciterParser.parseSuwar("""{"suwar":[{"id":1,"name":"الفاتحة"},{"id":115,"name":"خارج النطاق"},{"id":2,"name":""}]}""")
        assertEquals(mapOf(1 to "الفاتحة"), suwar)
    }

    @Test fun navStackStepByStep() {
        val n = NavStack()
        n.push("Main"); n.push("Settings"); n.push("Theme")
        assertTrue(n.hasPrevious()); assertEquals("Theme", n.top())
        n.remove("Theme"); assertEquals("Settings", n.top())
        n.remove("Settings"); assertFalse(n.hasPrevious()); assertEquals("Main", n.top())
    }
}

class CardGridTest {
    @Test fun spansByScreenWidth() {
        assertEquals(1, CardGrid.span(600, true))     // هاتف/شاشة ضيقة
        assertEquals(2, CardGrid.span(960, true))     // تلفزيون 1080p (320dpi)
        assertEquals(2, CardGrid.span(1280, true))    // تلفزيون 720p (160dpi)
        assertEquals(3, CardGrid.span(1920, true))    // 1080p بكثافة 160dpi أو شاشة كبيرة
        assertEquals(1, CardGrid.span(1920, false))   // وضع القائمة
    }

    @Test fun cardsNeverNarrowerThanMinimum() {
        for (w in listOf(960, 1280, 1920)) {
            val span = CardGrid.span(w, true)
            assertTrue(CardGrid.cardWidth(w, span, 20) >= 280)
        }
    }

    @Test fun homeVisibleCardsByWidth() {
        assertEquals(1, CardGrid.homeVisible(600).toLong())
        assertEquals(2, CardGrid.homeVisible(960).toLong())
        assertEquals(3, CardGrid.homeVisible(1920).toLong())
    }

    @Test fun appColumnsByWidth() {
        assertEquals(2, CardGrid.appColumns(300).toLong())
        assertEquals(5, CardGrid.appColumns(900).toLong())
        assertEquals(7, CardGrid.appColumns(1920).toLong())
    }
}
