package com.uobidah.launcher.core

import org.junit.Assert.*
import org.junit.Test

class UpdatePolicyTest {
    @Test fun acceptsOwnerRepoAndGithubUrl() {
        assertEquals("owner/repository", UpdatePolicy.normalizeRepository("owner/repository"))
        assertEquals("owner/repository", UpdatePolicy.normalizeRepository("https://github.com/owner/repository/"))
        assertEquals("owner/repository", UpdatePolicy.normalizeRepository("HTTPS://GITHUB.COM/owner/repository"))
        assertEquals("owner/repository", UpdatePolicy.normalizeRepository("https://github.com/owner/repository.git"))
    }

    @Test fun rejectsUnsafeOrMalformedRepository() {
        listOf("", "owner", "owner/repo/extra", "https://evil.example/a/b", "../repo", "owner/../repo", "owner repo").forEach {
            assertTrue("should reject $it", UpdatePolicy.normalizeRepository(it) == null)
        }
    }

    @Test fun onlyHigherVersionCodeIsAnUpdate() {
        assertTrue(UpdatePolicy.isNewer(35, 34))
        assertFalse(UpdatePolicy.isNewer(34, 34))
        assertFalse(UpdatePolicy.isNewer(33, 34))
    }

    @Test fun validatesSha256Shape() {
        assertTrue(UpdatePolicy.isValidSha256("a".repeat(64)))
        assertFalse(UpdatePolicy.isValidSha256("a".repeat(63)))
        assertFalse(UpdatePolicy.isValidSha256("z".repeat(64)))
    }

    @Test fun assetMustBelongToConfiguredGithubRepository() {
        assertTrue(UpdatePolicy.isAllowedReleaseAsset(
            "https://github.com/owner/repo/releases/download/fixture-tag/Abo-Uobidah-TV.apk", "owner/repo"))
        assertTrue(UpdatePolicy.isAllowedReleaseAsset(
            "https://github.com/OWNER/REPO/releases/download/fixture-tag/Abo-Uobidah-TV.apk", "owner/repo"))
        assertFalse(UpdatePolicy.isAllowedReleaseAsset(
            "https://evil.example/Abo-Uobidah-TV.apk", "owner/repo"))
        assertFalse(UpdatePolicy.isAllowedReleaseAsset(
            "http://github.com/owner/repo/releases/download/fixture-tag/Abo-Uobidah-TV.apk", "owner/repo"))
        assertFalse(UpdatePolicy.isAllowedReleaseAsset(
            "https://github.com/other/repo/releases/download/fixture-tag/Abo-Uobidah-TV.apk", "owner/repo"))
        assertFalse(UpdatePolicy.isAllowedReleaseAsset(
            "https://github.com:8443/owner/repo/releases/download/fixture-tag/Abo-Uobidah-TV.apk", "owner/repo"))
        assertFalse(UpdatePolicy.isAllowedReleaseAsset(
            "https://github.com/owner/repo/releases/download/fixture-tag/Abo-Uobidah-TV.apk?redirect=evil", "owner/repo"))
    }
    @Test fun stableAndTestChannelsAreSeparated() {
        assertTrue(UpdatePolicy.releaseAllowed(false, false, false))
        assertFalse(UpdatePolicy.releaseAllowed(false, true, false))
        assertFalse(UpdatePolicy.releaseAllowed(true, false, false))
        assertTrue(UpdatePolicy.releaseAllowed(false, true, true))
        assertFalse(UpdatePolicy.releaseAllowed(true, true, true))
    }

    @Test fun githubHostAllowlistAcceptsOnlyRequiredHosts() {
        assertTrue(UpdatePolicy.isTrustedGitHubHost("github.com"))
        assertTrue(UpdatePolicy.isTrustedGitHubHost("API.GITHUB.COM"))
        assertTrue(UpdatePolicy.isTrustedGitHubHost("release-assets.githubusercontent.com"))
        assertTrue(UpdatePolicy.isTrustedGitHubHost("objects.githubusercontent.com"))
        assertFalse(UpdatePolicy.isTrustedGitHubHost("evil.githubusercontent.com"))
        assertFalse(UpdatePolicy.isTrustedGitHubHost("github.com.attacker.example"))
        assertFalse(UpdatePolicy.isTrustedGitHubHost("raw.githubusercontent.com"))
    }

}
