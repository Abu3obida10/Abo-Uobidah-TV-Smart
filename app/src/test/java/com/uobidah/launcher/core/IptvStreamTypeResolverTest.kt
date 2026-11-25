package com.uobidah.launcher.core

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class IptvStreamTypeResolverTest {
    @Test fun detectsHlsFileWithQueryAndFragment() {
        assertTrue(IptvStreamTypeResolver.isHls("https://cdn.example/live/channel.m3u8?token=abc#t=4"))
    }

    @Test fun detectsHlsBehindXtreamStyleQuery() {
        assertTrue(IptvStreamTypeResolver.isHls("https://tv.example/get.php?username=u&password=p&output=m3u8"))
        assertTrue(IptvStreamTypeResolver.isHls("http://tv.example/stream?type=m3u_plus&username=u"))
    }

    @Test fun doesNotClassifyProgressiveStreamsAsHls() {
        assertFalse(IptvStreamTypeResolver.isHls("https://cdn.example/live/123.ts"))
        assertFalse(IptvStreamTypeResolver.isHls("https://cdn.example/video.mp4"))
        assertFalse(IptvStreamTypeResolver.isHls("file:///storage/playlist.m3u8"))
    }
}
