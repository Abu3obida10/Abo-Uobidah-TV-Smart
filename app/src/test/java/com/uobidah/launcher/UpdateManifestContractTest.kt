package com.uobidah.launcher

import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Exact release contract: tag v<versionName>, update.json fields, APK asset inside the same repo. Synthetic data. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class UpdateManifestContractTest {
    private val repo = "Owner/Repo"
    private val sha = "a".repeat(64)
    private fun json(tag: String = "v1.0.45", name: String = "1.0.45", code: Int = 67, pkg: String = "com.uobidah.launcher",
                     apk: String = "https://github.com/Owner/Repo/releases/download/v1.0.45/Abo-Uobidah-TV-67.apk", hash: String = sha) =
        """{"packageName":"$pkg","versionName":"$name","versionCode":$code,"tag":"$tag","apkUrl":"$apk","sha256":"$hash","releaseNotes":"n"}"""

    @Test fun acceptsMatchingManifest() {
        val m = UpdateManager.parseManifest(json(), "v1.0.45", "", repo)
        assertEquals("1.0.45", m.versionName); assertEquals(67L, m.versionCode)
    }
    @Test fun rejectsTagMismatch() {
        // المطابق مقبول: tag في المانيفست = وسم GitHub
        assertEquals("1.0.45", UpdateManager.parseManifest(json(tag = "v1.0.45"), "v1.0.45", "", repo).versionName)
        // وسم GitHub مختلف عن tag المانيفست => رفض بسبب الوسم تحديدًا (لا بسبب versionName)
        val e1 = try { UpdateManager.parseManifest(json(tag = "v1.0.45"), "v1.0.46", "", repo); null } catch (e: SecurityException) { e }
        assertNotNull("وسم GitHub v1.0.46 مع tag=v1.0.45 في المانيفست لازم يُرفض", e1)
        assertTrue("الرفض لازم يكون بسبب وسم الإصدار", e1!!.message!!.contains("وسم الإصدار"))
        // العكس: tag في المانيفست مختلف عن وسم GitHub
        val e2 = try { UpdateManager.parseManifest(json(tag = "v1.0.46"), "v1.0.45", "", repo); null } catch (e: SecurityException) { e }
        assertNotNull("tag=v1.0.46 في المانيفست مع وسم GitHub v1.0.45 لازم يُرفض", e2)
        assertTrue(e2!!.message!!.contains("وسم الإصدار"))
    }
    @Test fun rejectsVersionNameNotMatchingTag() { try { UpdateManager.parseManifest(json(name = "1.0.43"), "v1.0.45", "", repo); fail() } catch (_: SecurityException) {} }
    @Test fun rejectsForeignPackage() { try { UpdateManager.parseManifest(json(pkg = "x.y"), "v1.0.45", "", repo); fail() } catch (_: SecurityException) {} }
    @Test fun rejectsApkOutsideRepoOrBadHash() {
        try { UpdateManager.parseManifest(json(apk = "https://github.com/Other/Repo/releases/download/v1.0.45/a.apk"), "v1.0.45", "", repo); fail() } catch (_: SecurityException) {}
        try { UpdateManager.parseManifest(json(hash = "zz"), "v1.0.45", "", repo); fail() } catch (_: SecurityException) {}
    }
    @Test fun newerOnlyWhenVersionCodeHigher() {
        assertTrue(com.uobidah.launcher.core.UpdatePolicy.isNewer(67, 66))
        assertFalse(com.uobidah.launcher.core.UpdatePolicy.isNewer(65, 65))
    }
}
