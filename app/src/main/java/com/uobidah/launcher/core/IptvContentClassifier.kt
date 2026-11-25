package com.uobidah.launcher.core

/**
 * Conservative IPTV classifier.
 * 1 = live channel, 2 = movie, 3 = series/episode, 4 = unclassified VOD, 5 = live sports.
 * File extensions and the generic word VOD are never treated as proof that an item is a movie.
 */
object IptvContentClassifier {
    fun classify(name: String, url: String, group: String): Int {
        val u = url.lowercase()
        val g = "$group $name".lowercase()
        if (u.contains("/series/")) return 3
        if (u.contains("/movie/")) return 2

        val seriesTerms = listOf(
            "series", "tv series", "episode", "season", "مسلسل", "مسلسلات",
            "حلقة", "حلقات", "موسم", "dizi"
        )
        if (seriesTerms.any(g::contains) || Regex("""(?i)\bS\d{1,2}\s*E\d{1,3}\b""").containsMatchIn(g)) return 3

        val movieTerms = listOf("movie", "film", "cinema", "فيلم", "افلام", "أفلام", "سينما")
        if (movieTerms.any(g::contains)) return 2

        val vodTerms = listOf("vod", "video on demand", "عند الطلب", "مشاهدة", "مكتبة الفيديو")
        val mediaExtensions = listOf(".mp4", ".mkv", ".avi", ".mov", ".ts")
        if (vodTerms.any(g::contains) || mediaExtensions.any { u.substringBefore('?').endsWith(it) }) return 4

        // فصل القنوات الرياضية الحية عن بقية القنوات؛ لا يصنّف مسار الفيلم/المسلسل كرياضة.
        val sportTerms = listOf(
            "sport", "sports", "football", "soccer", "espn", "bein sport", "sky sport",
            "رياضة", "رياضي", "رياضية", "كورة", "كرة القدم", "الكرة", "مباريات", "الدوري"
        )
        if (sportTerms.any(g::contains)) return 5
        return 1
    }
}
