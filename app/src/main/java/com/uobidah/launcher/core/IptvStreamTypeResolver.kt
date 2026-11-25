package com.uobidah.launcher.core

/** Detect HLS even when providers hide the manifest behind get.php/query URLs. */
object IptvStreamTypeResolver {
    fun isHls(rawUrl: String): Boolean {
        val url = rawUrl.trim().lowercase()
        if (!url.startsWith("http://") && !url.startsWith("https://")) return false
        val withoutFragment = url.substringBefore('#')
        val path = withoutFragment.substringBefore('?').trimEnd('/')
        if (path.endsWith(".m3u8")) return true
        val query = withoutFragment.substringAfter('?', "")
        return Regex("(?:^|&)(?:output|format|container|type)=(?:m3u8|hls|m3u_plus)(?:&|$)")
            .containsMatchIn(query)
    }
}
