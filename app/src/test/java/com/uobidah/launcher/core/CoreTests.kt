package com.uobidah.launcher.core

import org.junit.Assert.*
import org.junit.Test

class MiniJsonTest {
    @Test fun roundTrip() {
        val m = linkedMapOf<String, Any?>("a" to "x\"y\n\u0001ع", "b" to listOf(1L, 2.5, true, null), "c" to mapOf("d" to false))
        val back = MiniJson.parseObject(MiniJson.stringify(m))
        assertEquals("x\"y\n\u0001ع", back["a"])
        assertEquals(listOf(1L, 2.5, true, null), back["b"])
        assertEquals(false, (back["c"] as Map<*, *>)["d"])
    }
    @Test fun rejectsGarbage() {
        for (bad in listOf("", "{", "[1,", "{\"a\":}", "{\"a\":1} x", "nul", "\"abc"))
            assertThrows(MiniJson.JsonException::class.java) { MiniJson.parse(bad) }
    }
}

class NotificationPolicyTest {
    @Test fun defaultsAreAudibleAndVisible() {
        val r = NotificationPolicy().resolve(AlertType.APP, "x")
        assertTrue(r.showInIsland); assertTrue(r.playSound); assertEquals(80, r.volume)
    }
    @Test fun appOverridesTypeOverridesGlobal() {
        val p = NotificationPolicy()
            .withType(AlertType.APP, AlertRule(volume = 50, durationSec = 12))
            .withApp("wa", AlertRule(volume = 20, soundUri = "content://x"))
        val wa = p.resolve(AlertType.APP, "wa")
        assertEquals(20, wa.volume); assertEquals(12, wa.durationSec); assertEquals("content://x", wa.soundUri)
        val other = p.resolve(AlertType.APP, "other")
        assertEquals(50, other.volume); assertEquals(SoundUri.DEFAULT, other.soundUri)
    }
    @Test fun appRuleDoesNotAffectOtherTypes() {
        val p = NotificationPolicy().withApp("wa", AlertRule(soundEnabled = false))
        assertTrue(p.resolve(AlertType.MATCH_GOAL, "wa").playSound)
        assertFalse(p.resolve(AlertType.APP, "wa").playSound)
    }
    @Test fun silentOrZeroVolumeIsNotAudible() {
        assertFalse(NotificationPolicy().withGlobal(AlertRule(soundUri = SoundUri.SILENT)).resolve(AlertType.ADHAN).playSound)
        assertFalse(NotificationPolicy().withType(AlertType.MATCH_END, AlertRule(volume = 0)).resolve(AlertType.MATCH_END).playSound)
    }
    @Test fun masterOffSilencesEverything() {
        val r = NotificationPolicy().withMaster(false).resolve(AlertType.ADHAN)
        assertFalse(r.showInIsland); assertFalse(r.playSound); assertFalse(r.vibrate)
    }
    @Test fun jsonRoundTripKeepsAllLayers() {
        val p = NotificationPolicy().withType(AlertType.MATCH_GOAL, AlertRule(soundUri = "u", volume = 100, position = 3))
            .withApp("a.b", AlertRule(showInIsland = false, vibrate = true, durationSec = 30))
        val q = NotificationPolicy.fromJson(p.toJson())
        assertEquals(p.resolve(AlertType.MATCH_GOAL), q.resolve(AlertType.MATCH_GOAL))
        assertEquals(p.resolve(AlertType.APP, "a.b"), q.resolve(AlertType.APP, "a.b"))
        assertFalse(q.resolve(AlertType.APP, "a.b").showInIsland)
    }
    @Test fun corruptJsonFallsBackToDefaults() {
        val q = NotificationPolicy.fromJson("{not json")
        assertTrue(q.resolve(AlertType.APP, "x").playSound)
    }
    @Test fun outOfRangeValuesAreClamped() {
        val q = NotificationPolicy.fromJson("""{"global":{"vol":900,"dur":9999,"pos":77}}""").resolve(AlertType.SYSTEM)
        assertEquals(100, q.volume); assertEquals(60, q.durationSec); assertEquals(4, q.position)
    }
    @Test fun legacyMigrationKeepsOptInSemantics() {
        val p = NotificationPolicy.fromLegacy(true, "silent", 60, true, listOf("a"), listOf("b"))
        assertTrue(p.resolve(AlertType.APP, "a").showInIsland)
        assertFalse(p.resolve(AlertType.APP, "a").playSound)      // صوت a غير مفعّل سابقاً (وكذلك uri=silent)
        assertFalse(p.resolve(AlertType.APP, "b").showInIsland)
        assertFalse(p.resolve(AlertType.APP, "unlisted").showInIsland)
        assertFalse(p.resolve(AlertType.APP, "unlisted").playSound)
        assertEquals(60, p.resolve(AlertType.ADHAN).volume)
        assertTrue(p.resolve(AlertType.ADHAN).showInIsland)       // الأنواع الأخرى غير متأثرة
    }
    @Test fun legacySoundAppPlaysWhenToneNotSilent() {
        val p = NotificationPolicy.fromLegacy(true, "", 80, false, emptyList(), listOf("s"))
        assertTrue(p.resolve(AlertType.APP, "s").playSound)
        assertFalse(p.resolve(AlertType.APP, "s").showInIsland)
    }
}

class AlertGateTest {
    @Test fun duplicateKeyWithinWindowIsDropped() {
        val g = AlertGate(4000, 1200)
        assertTrue(g.admit("k", true, 1000).show)
        assertFalse(g.admit("k", true, 2000).show)
        assertTrue(g.admit("k", true, 6000).show)
    }
    @Test fun soundsNeverOverlap() {
        val g = AlertGate(4000, 1200)
        val a = g.admit("a", true, 10_000); val b = g.admit("b", true, 10_500); val c = g.admit("c", true, 11_300)
        assertTrue(a.sound); assertTrue(b.show); assertFalse(b.sound); assertTrue(c.sound)
    }
    @Test fun silentEventDoesNotConsumeSoundSlot() {
        val g = AlertGate()
        g.admit("a", false, 5000)
        assertTrue(g.admit("b", true, 5001).sound)
    }
    @Test fun firstEventAtTimeZeroIsAdmitted() = assertTrue(AlertGate().admit("x", true, 0).sound)
}

class FavoriteListTest {
    private fun three() = FavoriteList().add("a").add("b").add("c")
    @Test fun addIgnoresDuplicatesAndBlank() { assertEquals(3, three().add("a").add("").size) }
    @Test fun removeOnlyRemovesFromList() { assertEquals(listOf("a", "c"), three().remove("b").entries.map { it.pkg }) }
    @Test fun moveUpDownAndBounds() {
        assertEquals(listOf("b", "a", "c"), three().move("b", -1).entries.map { it.pkg })
        assertEquals(listOf("a", "c", "b"), three().move("b", 1).entries.map { it.pkg })
        assertEquals(listOf("a", "b", "c"), three().move("a", -1).entries.map { it.pkg })
        assertEquals(listOf("a", "b", "c"), three().move("c", 1).entries.map { it.pkg })
    }
    @Test fun moveToIndex() { assertEquals(listOf("c", "a", "b"), three().moveTo("c", 0).entries.map { it.pkg }) }
    @Test fun renameIconSizeShowName() {
        val l = three().rename("a", "  واتساب ").setIcon("a", "emoji:💬").setSize("a", TileSize.LARGE).setShowName("a", false)
        val e = l.get("a")!!
        assertEquals("واتساب", e.displayName("WhatsApp")); assertEquals("emoji:💬", e.iconKey)
        assertEquals(TileSize.LARGE, e.size); assertFalse(e.showName)
        assertEquals("B", l.get("b")!!.displayName("B"))
    }
    @Test fun hideKeepsEntryButRemovesFromVisible() {
        val l = three().setHidden("b", true)
        assertEquals(3, l.size); assertEquals(listOf("a", "c"), l.visible().map { it.pkg }); assertTrue("b" in l)
    }
    @Test fun operationsOnUnknownPackageAreNoOps() {
        val l = three(); assertSame(l, l.rename("zzz", "x")); assertSame(l, l.move("zzz", 1)); assertSame(l, l.moveTo("zzz", 0))
    }
    @Test fun pruneDropsOnlyUninstalled() {
        val l = three().setHidden("a", true).prune { it != "c" }
        assertEquals(listOf("a", "b"), l.entries.map { it.pkg })
    }
    @Test fun jsonRoundTrip() {
        val l = three().rename("a", "x").setSize("b", TileSize.SMALL).setHidden("c", true).setIcon("c", "k")
        val q = FavoriteList.fromJson(l.toJson())
        assertEquals(l.entries, q.entries)
    }
    @Test fun corruptJsonGivesEmptyList() { assertEquals(0, FavoriteList.fromJson("[[[").size) }
    @Test fun duplicatePackagesInJsonAreCollapsed() {
        assertEquals(1, FavoriteList.fromJson("""{"items":[{"pkg":"a"},{"pkg":"a"},{"pkg":""},5]}""").size)
    }
    @Test fun legacyMigration() {
        val l = FavoriteList.fromLegacy(listOf("a", "b"), listOf("a||اسم||مزدوج"), false)
        assertEquals("اسم||مزدوج", l.get("a")!!.label); assertEquals("", l.get("b")!!.label); assertFalse(l.get("a")!!.showName)
    }
}

class BackupCodecTest {
    private val prefs = mapOf<String, Any?>(
        "theme" to 3, "wall_query" to "sea", "island_on" to true, "bg_version" to 5L, "ratio" to 1.5f,
        "allowed_apps" to setOf("a", "b"), "secret_match_key" to "TOPSECRET", "api_key_x" to "K",
        "matches_next_1" to "[...]", "team_badge_url_1" to "http://x", "weird" to Any()
    )
    @Test fun exportNeverContainsSecretsOrCaches() {
        val j = BackupCodec.export(prefs, "test-fixture", 1L)
        for (s in listOf("TOPSECRET", "secret_", "api_key", "matches_next", "team_badge", "bg_version")) assertFalse(s, j.contains(s))
        assertTrue(j.contains("theme"))
    }
    @Test fun roundTripPreservesTypes() {
        val r = BackupCodec.parse(BackupCodec.export(prefs, "test-fixture", 1L))
        assertTrue(r.ok)
        val m = r.entries.associate { it.key to it.value }
        assertEquals(3, m["theme"]); assertEquals("sea", m["wall_query"]); assertEquals(true, m["island_on"])
        assertEquals(1.5f, m["ratio"]); assertEquals(setOf("a", "b"), m["allowed_apps"])
        assertFalse(m.containsKey("weird"))
    }
    @Test fun tamperedFileCannotInjectSecrets() {
        val evil = """{"schema":"uobidah-backup","version":1,"values":{"secret_match_key":{"t":"s","v":"X"},"theme":{"t":"i","v":2}}}"""
        val r = BackupCodec.parse(evil)
        assertEquals(listOf("theme"), r.entries.map { it.key }); assertEquals(listOf("secret_match_key"), r.skipped)
    }
    @Test fun badFilesAreRejectedWholesale() {
        assertFalse(BackupCodec.parse("not json").ok)
        assertFalse(BackupCodec.parse("""{"schema":"other","version":1,"values":{}}""").ok)
        assertFalse(BackupCodec.parse("""{"schema":"uobidah-backup","version":99,"values":{}}""").ok)
        assertFalse(BackupCodec.parse("""{"schema":"uobidah-backup","version":1}""").ok)
    }
    @Test fun wrongTypedValuesAreSkippedNotApplied() {
        val r = BackupCodec.parse("""{"schema":"uobidah-backup","version":1,"values":{"a":{"t":"i","v":"str"},"b":{"t":"i","v":99999999999},"c":{"t":"set","v":[1]},"d":{"t":"b","v":true}}}""")
        assertEquals(listOf("d"), r.entries.map { it.key }); assertEquals(3, r.skipped.size)
    }
}

class MatchSourceTest {
    @Test fun builtInClubsAreTheThreeRequestedWithRealIds() {
        val ids = ClubRegistry.BUILT_IN.associate { it.nameEn to it.id }
        assertEquals("138995", ids["Al Ahly"]); assertEquals("133612", ids["Manchester United"]); assertEquals("133738", ids["Real Madrid"])
    }
    @Test fun addClubValidation() {
        val a = ClubRegistry.add(emptyList(), "133604", "", "Arsenal") as ClubRegistry.AddResult.Added
        assertEquals("Arsenal", a.list[0].nameAr)
        assertTrue(ClubRegistry.add(a.list, "133604", "x", "x") is ClubRegistry.AddResult.Rejected)
        assertTrue(ClubRegistry.add(emptyList(), "138995", "x", "x") is ClubRegistry.AddResult.Rejected)
        assertTrue(ClubRegistry.add(emptyList(), "12a", "x", "x") is ClubRegistry.AddResult.Rejected)
        assertTrue(ClubRegistry.add(emptyList(), "1", "x", " ") is ClubRegistry.AddResult.Rejected)
    }
    @Test fun builtInCannotBeRemovedAndCustomJsonRoundTrips() {
        val c = (ClubRegistry.add(emptyList(), "1", "ن", "N") as ClubRegistry.AddResult.Added).list
        assertEquals(c, ClubRegistry.remove(c, "999")); assertEquals(0, ClubRegistry.remove(c, "1").size)
        assertEquals(c, ClubRegistry.fromJson(ClubRegistry.toJson(c)))
        assertEquals(3, ClubRegistry.all(emptyList()).size)
        assertEquals(0, ClubRegistry.fromJson("""[{"id":"138995","en":"dup"}]""").size)
    }
    @Test fun statusNeverClaimsUpdateTimeWithoutSuccess() {
        val s = SourceStatus()
        assertEquals("لم يتم الاتصال بالمصدر بعد", s.describeAr(1000))
        val f = s.afterFailure(5000, "HTTP 429", false)
        assertEquals(ConnState.FAILED, f.state); assertEquals(0L, f.lastSuccessMs); assertTrue(f.describeAr(6000).contains("لا توجد بيانات محفوظة"))
    }
    @Test fun statusTransitionsAndAgo() {
        val ok = SourceStatus().afterSuccess(1_000_000)
        assertTrue(ok.describeAr(1_000_000 + 5 * 60_000).contains("قبل 5 دقيقة"))
        val c = ok.afterFailure(2_000_000, "timeout", true)
        assertEquals(ConnState.CACHED, c.state); assertEquals(1_000_000L, c.lastSuccessMs)
        assertEquals(c, SourceStatus.fromJson(c.toJson()))
        assertEquals(SourceStatus(), SourceStatus.fromJson("junk"))
    }
    @Test fun secretIsScrubbedFromErrorText() { assertEquals("url/***/x", SourceStatus.safeDetail("url/KEY123/x", "KEY123")) }
    @Test fun sportsDbUrlUsesPublicKeyUnlessSafeUserKey() {
        assertTrue(SportsDbUrl.base(null).contains("/json/123/"))
        assertTrue(SportsDbUrl.base("abc_12-Z").contains("/json/abc_12-Z/"))
        assertTrue(SportsDbUrl.base("a/../b").contains("/json/123/"))
        assertTrue(SportsDbUrl.base("  ").contains("/json/123/"))
    }
}

class PerfLimitsTest {
    @Test fun keepsAllowedValues() {
        for (v in PerfLimits.POLL_SECONDS) assertEquals(v, PerfLimits.nearest(v, PerfLimits.POLL_SECONDS))
    }
    @Test fun snapsOutOfRangeValues() {
        assertEquals(10, PerfLimits.nearest(-5, PerfLimits.POLL_SECONDS))
        assertEquals(60, PerfLimits.nearest(9999, PerfLimits.POLL_SECONDS))
        assertEquals(300, PerfLimits.nearest(250, PerfLimits.MATCH_CHECK_SECONDS))
    }
    @Test fun matchCheckSupportsLowNetworkLoadIntervals() {
        assertTrue(PerfLimits.MATCH_CHECK_SECONDS.contains(300))
        assertTrue(PerfLimits.MATCH_CHECK_SECONDS.contains(600))
    }
}

class CityClockTest {
    @Test fun convertsInstantIntoSelectedCityZone() {
        val now = CityClock.localNow("Asia/Tokyo", java.time.Instant.parse("2026-01-01T12:00:00Z"))
        assertEquals(java.time.LocalDateTime.parse("2026-01-01T21:00:00"), now)
    }
    @Test fun cityZoneCanRollIntoTheNextLocalDate() {
        val now = CityClock.localNow("Asia/Kolkata", java.time.Instant.parse("2026-01-01T18:30:00Z"))
        assertEquals(java.time.LocalDate.parse("2026-01-02"), now.toLocalDate())
    }
    @Test fun invalidZoneUsesTheProvidedFallback() {
        val now = CityClock.localNow("not/a-real-zone", java.time.Instant.parse("2026-01-01T12:00:00Z"), java.time.ZoneOffset.UTC)
        assertEquals(java.time.LocalDateTime.parse("2026-01-01T12:00:00"), now)
    }
    @Test fun invalidZoneDefaultsToCairoInsteadOfDeviceTimezone() {
        val now = CityClock.localNow("not/a-real-zone", java.time.Instant.parse("2026-01-01T12:00:00Z"))
        assertEquals(java.time.LocalDateTime.parse("2026-01-01T14:00:00"), now)
    }
}


class IslandGeometryTest {
    @Test fun defaultTopCenterIsActuallyAtTopNotBottom() {
        val p = IslandGeometry.place(1920, 1080, 520, 110, IslandGeometry.TOP_CENTER, false, 0, 0, 1f)
        assertEquals(700, p.xPx)
        assertEquals(24, p.yPx)
    }

    @Test fun bottomCenterUsesBottomAnchorOnlyWhenExplicitlySelected() {
        val p = IslandGeometry.place(1920, 1080, 520, 110, IslandGeometry.BOTTOM_CENTER, false, 0, 0, 1f)
        assertEquals(946, p.yPx)
    }

    @Test fun customNegativeVerticalOffsetRaisesTheIsland() {
        val base = IslandGeometry.place(1920, 1080, 520, 110, IslandGeometry.CENTER, true, 0, 0, 2f)
        val raised = IslandGeometry.place(1920, 1080, 520, 110, IslandGeometry.CENTER, true, 0, -80, 2f)
        assertEquals(80 * 2, base.yPx - raised.yPx)
    }

    @Test fun customHorizontalOffsetMovesTheIslandAndIsDensityAware() {
        val base = IslandGeometry.place(1920, 1080, 520, 110, IslandGeometry.CENTER, true, 0, 0, 1.5f)
        val right = IslandGeometry.place(1920, 1080, 520, 110, IslandGeometry.CENTER, true, 60, 0, 1.5f)
        assertEquals(90, right.xPx - base.xPx)
    }

    @Test fun placementNeverLetsTheOverlayLeaveTheSafeScreenArea() {
        val p = IslandGeometry.place(1280, 720, 1100, 600, IslandGeometry.CENTER, true, 9999, -9999, 1f, 20)
        assertTrue(p.xPx >= 20)
        assertTrue(p.yPx >= 20)
        assertTrue(p.xPx + 1100 <= 1280 - 20)
        assertTrue(p.yPx + 600 <= 720 - 20)
    }

    @Test fun moveLimitsAreCalculatedFromMeasuredOverlaySize() {
        val limits = IslandGeometry.limits(1920, 1080, 800, 160, 2f, 24)
        assertEquals(256, limits.xDp)
        assertEquals(206, limits.yDp)
    }
}
