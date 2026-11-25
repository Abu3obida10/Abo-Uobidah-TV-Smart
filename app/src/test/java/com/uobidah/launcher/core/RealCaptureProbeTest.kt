package com.uobidah.launcher.core

import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.time.LocalDate

/**
 * كان يقرأ /tmp/ys.html و/tmp/rm.html ويكتب إلى /tmp/abo، وكلاهما غير موجود في بيئة بناء نظيفة.
 * صار يقرأ لقطتين حقيقيتين مرفقتين في src/test/resources (yalla_real_*_page.html، التقطتا 2026-11-24)
 * وبه تحققات فعلية بدل «dump» فقط؛ والتشخيص يُكتب تحت build/test-artifacts (بعد mkdirs) لا /tmp.
 */
class RealCaptureProbeTest {
    private fun load(n: String): String {
        val s = javaClass.classLoader!!.getResourceAsStream(n)
        assertNotNull("fixture مفقود من src/test/resources: $n", s)
        return s!!.readBytes().toString(Charsets.UTF_8)
    }

    @Test fun bundledCapturesParseWithoutAnyExternalFile() {
        val sb = StringBuilder()
        for (f in listOf("yalla_real_manutd_page.html", "yalla_real_realmadrid_page.html")) {
            val l = YallaScoresHtmlParser.parseTeamPage(load(f), "Africa/Cairo", LocalDate.of(2026, 11, 25))
            sb.append(f).append(" n=").append(l.size).append('\n')
            for (m in l.take(40)) sb.append("${m.home}|${m.away}|${m.homeScore}-${m.awayScore}|${m.status}|${m.progress}|${m.startMs}\n")
            assertTrue("$f: لم تُستخرج أي مباراة", l.isNotEmpty())
            for (m in l) {
                assertTrue("$f: اسم فريق فارغ", m.home.isNotBlank() && m.away.isNotBlank())
                if (m.status == "scheduled") assertTrue("$f ${m.home}: موعد انطلاق غير صالح", m.startMs > 0L)
            }
        }
        val dir = File("build/test-artifacts").apply { mkdirs() }
        File(dir, "probe-real.txt").writeText(sb.toString())
    }
}
