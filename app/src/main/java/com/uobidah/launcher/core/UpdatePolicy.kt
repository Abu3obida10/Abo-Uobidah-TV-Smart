package com.uobidah.launcher.core

/** Pure validation rules shared by the updater and unit tests. */
object UpdatePolicy {
    private val repoPattern = Regex("^[A-Za-z0-9_.-]{1,39}/[A-Za-z0-9_.-]{1,100}$")
    private val sha256Pattern = Regex("^[a-fA-F0-9]{64}$")

    fun normalizeRepository(raw: String): String? {
        val trimmed = raw.trim()
        var value = trimmed.replace(Regex("(?i)^https?://github\\.com/"), "").trim('/')
        if (value.endsWith(".git", ignoreCase = true)) value = value.dropLast(4)
        if (!repoPattern.matches(value)) return null
        if (value.contains("..") || value.startsWith(".") || value.endsWith(".")) return null
        return value
    }

    fun isNewer(remoteVersionCode: Long, installedVersionCode: Long): Boolean =
        remoteVersionCode > installedVersionCode

    fun isValidSha256(value: String): Boolean = sha256Pattern.matches(value.trim())

    /** Exact HTTPS hosts needed for GitHub's API and public Release asset CDN. */
    fun isTrustedGitHubHost(host: String): Boolean = host.trim().lowercase() in setOf(
        "github.com",
        "api.github.com",
        "release-assets.githubusercontent.com",
        "objects.githubusercontent.com"
    )

    /** The release manifest may only point to an APK asset belonging to this exact public repo. */
    fun isAllowedReleaseAsset(url: String, repository: String): Boolean {
        return try {
            val parsed = java.net.URI(url)
            parsed.scheme.equals("https", ignoreCase = true) &&
                parsed.host.equals("github.com", ignoreCase = true) &&
                (parsed.port == -1 || parsed.port == 443) &&
                parsed.path.startsWith("/$repository/releases/download/", ignoreCase = true) &&
                parsed.path.endsWith(".apk", ignoreCase = true) &&
                parsed.userInfo == null && parsed.query == null && parsed.fragment == null
        } catch (_: Exception) {
            false
        }
    }
    /** Stable channel rejects drafts/prereleases; test channel accepts published stable or prerelease releases. */
    fun releaseAllowed(draft: Boolean, prerelease: Boolean, testChannel: Boolean): Boolean =
        !draft && (testChannel || !prerelease)

}
