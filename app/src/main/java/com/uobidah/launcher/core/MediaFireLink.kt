package com.uobidah.launcher.core

import java.net.URI

/**
 * MediaFire share links (www.mediafire.com/file/...) are HTML landing pages, not the file itself.
 * This reads the page's own direct-download button so a pasted share link can be imported once.
 * Only https download*.mediafire.com targets are accepted; nothing is stored here.
 */
object MediaFireLink {
    fun isLandingPage(url: String): Boolean = try {
        val u = URI(url.trim())
        u.scheme.equals("https", true) && u.host.equals("www.mediafire.com", true) && u.path.orEmpty().startsWith("/file/")
    } catch (_: Exception) { false }

    private val BUTTON = Regex("""(?is)<a\b[^>]*\bid\s*=\s*["']downloadButton["'][^>]*>""")
    private val HREF = Regex("""(?is)\bhref\s*=\s*["'](https://download[0-9]*\.mediafire\.com/[^"'\s<>]+)["']""")

    /** Direct file URL from the landing page HTML, or null when the page has none (removed/limited file). */
    fun directUrl(html: String): String? {
        val tag = BUTTON.find(html)?.value ?: return null
        val href = HREF.find(tag)?.groupValues?.get(1)?.replace("&amp;", "&") ?: return null
        return try {
            val u = URI(href)
            if (u.scheme.equals("https", true) && u.host.orEmpty().lowercase().let { it.startsWith("download") && it.endsWith(".mediafire.com") }) href else null
        } catch (_: Exception) { null }
    }
}
