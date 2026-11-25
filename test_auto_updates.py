from pathlib import Path

root = Path(__file__).resolve().parent
manager = (root / "app/src/main/java/com/uobidah/launcher/UpdateManager.kt").read_text(encoding="utf-8")
worker = (root / "app/src/main/java/com/uobidah/launcher/UpdateCheckWorker.kt").read_text(encoding="utf-8")
activity = (root / "app/src/main/java/com/uobidah/launcher/UpdateActivity.kt").read_text(encoding="utf-8")
manifest = (root / "app/src/main/AndroidManifest.xml").read_text(encoding="utf-8")
gradle = (root / "app/build.gradle").read_text(encoding="utf-8")
policy = (root / "app/src/main/java/com/uobidah/launcher/core/UpdatePolicy.kt").read_text(encoding="utf-8")
prefs = (root / "app/src/main/java/com/uobidah/launcher/Prefs.kt").read_text(encoding="utf-8")
publisher = (root / "PUBLISH_RELEASE_ONLY.ps1").read_text(encoding="utf-8-sig")
checks = {
    "periodic checks run every 6 hours": "PeriodicWorkRequestBuilder<UpdateCheckWorker>(6, TimeUnit.HOURS)" in manager,
    "default GitHub repository is preconfigured": 'get() = s("update_repository", "Abu3obida10/Abo-Uobidah-TV-Smart")' in prefs,
    "app launch triggers a throttled update check": "fun checkOnLaunch(context: Context)" in manager and "LAUNCH_CHECK_MIN_AGE_MS" in manager,
    "last-check timestamp is only recorded after a successful manifest/result": "val manifest = fetchLatestManifest(repository, prefs.testChannel)" in manager and "prefs.updateLastCheckAt = System.currentTimeMillis()" in manager,
    "checks require network connectivity": "setRequiredNetworkType(NetworkType.CONNECTED)" in manager,
    "GitHub latest release API is used": "api.github.com/repos/$repository/releases/latest" in manager,
    "only release-specific GitHub APK assets are accepted": "isAllowedReleaseAsset(apkUrl, repository)" in manager,
    "APK SHA-256 is checked": "actualHash.equals(manifest.sha256" in manager,
    "cached APK SHA-256 is rechecked before install": 'sha256(file).equals(storedHash' in manager,
    "redirects use exact GitHub host allowlist": "UpdatePolicy.isTrustedGitHubHost(host)" in manager and "release-assets.githubusercontent.com" in policy,
    "nonstandard HTTPS ports are rejected": "current.port != 443" in manager,
    "GitHub .git repository URLs are normalized": 'value.endsWith(".git", ignoreCase = true)' in policy,
    "APK package and version are checked": "archive.packageName != context.packageName" in manager and "apkCode != expectedVersionCode" in manager,
    "installed signing certificate is compared": "currentSigners != apkSigners" in manager,
    "installer uses FileProvider": "FileProvider.getUriForFile" in manager,
    "settings screen is registered": 'android:name=".UpdateActivity"' in manifest,
    "FileProvider is registered": "androidx.core.content.FileProvider" in manifest and "@xml/update_file_paths" in manifest,
    "install permission is declared": "android.permission.REQUEST_INSTALL_PACKAGES" in manifest,
    "WorkManager dependency is included": "androidx.work:work-runtime-ktx:2.12.0" in gradle,
    "release publisher uploads only the named APK and manifest": "gh release create $tag $releaseApkPath $manifestPath" in publisher and "$apkAsset = 'Abo-Uobidah-TV.apk'" in publisher,
    "update screen reachable from About settings": "UpdateActivity::class.java" in (root / "app/src/main/java/com/uobidah/launcher/SettingsActivity.kt").read_text(encoding="utf-8"),
    "startup calls the automatic update scheduler": "UpdateScheduler.checkOnLaunch(applicationContext)" in (root / "app/src/main/java/com/uobidah/launcher/Base.kt").read_text(encoding="utf-8"),
}
def test_all_static_checks_pass():
    failures = [name for name, ok in checks.items() if not ok]
    assert not failures, "Static audit failures: " + "; ".join(failures)


if __name__ == "__main__":
    for name, ok in checks.items():
        print(("PASS" if ok else "FAIL") + " " + name)
    print(f"\nAudit tests: {sum(checks.values())}/{len(checks)} passed")
    raise SystemExit(0 if all(checks.values()) else 1)
