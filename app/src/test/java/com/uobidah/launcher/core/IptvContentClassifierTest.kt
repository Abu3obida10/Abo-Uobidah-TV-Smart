package com.uobidah.launcher.core

import org.junit.Assert.assertEquals
import org.junit.Test

class IptvContentClassifierTest {
    @Test fun xtreamSeriesPathIsSeries() = assertEquals(3, IptvContentClassifier.classify("Episode", "https://host/series/u/p/4", ""))
    @Test fun xtreamMoviePathIsMovie() = assertEquals(2, IptvContentClassifier.classify("Title", "https://host/movie/u/p/4", ""))
    @Test fun episodePatternIsSeries() = assertEquals(3, IptvContentClassifier.classify("Show S02E08", "https://host/123", ""))
    @Test fun genericMp4IsNotAssumedToBeMovie() = assertEquals(4, IptvContentClassifier.classify("clip.mp4", "https://host/file.mp4", ""))
    @Test fun vodGroupIsUnclassifiedRatherThanMovie() = assertEquals(4, IptvContentClassifier.classify("Untitled", "https://host/x", "VOD"))
    @Test fun explicitMovieLabelWins() = assertEquals(2, IptvContentClassifier.classify("Action Film", "https://host/x", "Movies"))
    @Test fun liveNewsChannelRemainsLive() = assertEquals(1, IptvContentClassifier.classify("News 24", "https://host/live.m3u8", "News"))
    @Test fun sportsChannelHasItsOwnCategory() = assertEquals(5, IptvContentClassifier.classify("beIN Sports 1", "https://host/live.m3u8", "Sports"))
    @Test fun sportsMovieRemainsAMovie() = assertEquals(2, IptvContentClassifier.classify("Football Movie", "https://host/movie/u/p/1", "Sports Movies"))
    @Test fun arabicEpisodeTermsAreRecognised() = assertEquals(3, IptvContentClassifier.classify("الحلقة ١", "https://host/x", "مسلسلات"))
}
