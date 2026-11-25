package com.uobidah.launcher.core

import org.junit.Assert.*
import org.junit.Test

/** Synthetic landing-page snippets (no real file or account data). */
class MediaFireLinkTest {
    @Test fun recognizesShareLinkOnly() {
        assertTrue(MediaFireLink.isLandingPage("https://www.mediafire.com/file/abc123/list.m3u/file"))
        assertFalse(MediaFireLink.isLandingPage("http://www.mediafire.com/file/abc123/list.m3u/file"))
        assertFalse(MediaFireLink.isLandingPage("https://example.com/file/abc123/list.m3u"))
        assertFalse(MediaFireLink.isLandingPage("https://download1.mediafire.com/x/list.m3u"))
    }
    @Test fun readsDirectUrlFromDownloadButton() {
        val html = """<div><a class="input popsok" aria-label="Download file" href="https://download1234.mediafire.com/k/abc/list.m3u" id="downloadButton" rel="nofollow">Download</a></div>"""
        assertEquals("https://download1234.mediafire.com/k/abc/list.m3u", MediaFireLink.directUrl(html))
    }
    @Test fun rejectsForeignOrMissingTargets() {
        assertNull(MediaFireLink.directUrl("""<a id="downloadButton" href="https://evil.example/x.m3u">d</a>"""))
        assertNull(MediaFireLink.directUrl("""<a id="downloadButton" href="http://download1.mediafire.com/x.m3u">d</a>"""))
        assertNull(MediaFireLink.directUrl("<html>file removed</html>"))
    }
}
