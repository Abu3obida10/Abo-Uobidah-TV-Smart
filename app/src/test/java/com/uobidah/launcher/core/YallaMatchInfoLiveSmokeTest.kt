package com.uobidah.launcher.core

import org.junit.Assume
import org.junit.Test
import java.io.File

/** Optional smoke check: parses a freshly captured public match page when /tmp/ys_match.html exists. Skipped otherwise. */
class YallaMatchInfoLiveSmokeTest {
    @Test fun parsesCapturedLivePage() {
        val f = File("/tmp/ys_match.html")
        Assume.assumeTrue(f.exists())
        val info = YallaMatchInfoParser.parse(f.readText())
        File("/tmp/abo/live_info.txt").writeText(info.entries.joinToString("\n") { it.key + " = " + it.value })
        org.junit.Assert.assertTrue("no info rows parsed", info.isNotEmpty())
    }
}
