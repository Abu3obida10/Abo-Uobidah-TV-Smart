from pathlib import Path

root = Path(__file__).resolve().parent
script = (root / "AUTO_BUILD_INSTALL.ps1").read_text(encoding="utf-8-sig")
bat = (root / "START_HERE_BUILD_INSTALL.bat").read_text(encoding="utf-8-sig")
checks = {
    "installed SDK is checked before sdkmanager is invoked": "$sdkMissing.Count -eq 0" in script,
    "offline path skips repository metadata refresh": "Skipping sdkmanager network update" in script,
    "required compile SDK platform is checked": "platforms\\android-35\\android.jar" in script,
    "required build-tools binaries are checked": all(x in script for x in ["aapt2.exe", "aidl.exe", "d8.bat", "zipalign.exe", "apksigner.bat"]),
    "sdkmanager output is captured separately": "SDK_MANAGER_LOG.txt" in script,
    "SDK platform-tools is optional for compiling": "Platform-tools are optional for compilation" in script,
    "launcher version and code are defined centrally and consumed by Gradle": "appVersionName=1.0.48" in (root / "gradle.properties").read_text(encoding="utf-8") and "appVersionCode=69" in (root / "gradle.properties").read_text(encoding="utf-8") and 'providers.gradleProperty("appVersionName")' in (root / "app/build.gradle").read_text(encoding="utf-8") and 'providers.gradleProperty("appVersionCode")' in (root / "app/build.gradle").read_text(encoding="utf-8") and "buildConfig true" in (root / "app/build.gradle").read_text(encoding="utf-8"),
    "installer explains offline SDK reuse": "installed SDK packages are reused offline" in bat,
    "sdkmanager is not downloaded when the SDK is complete": "A complete local SDK can still build offline" in script,
    "safe installer is called after APK build": "INSTALL_APK.ps1" in script,
    "APK build succeeds without a connected device": "$readyDevices.Count -eq 0" in script and "No installation was attempted" in script,
    "SDK discovery checks the common Android Studio SDK location": "Android\\Sdk" in script and "complete installed SDK" in script,
    "Gradle batch is launched through cmd.exe without PowerShell stderr conversion": "System.Diagnostics.ProcessStartInfo" in script and "$psi.FileName" in script,
    "Gradle wrapper exit code is read from the operating-system process": "$code = [int]$process.ExitCode" in script,
    "Gradle batch forwards the exact ERRORLEVEL": "exit /b %GRADLE_EXIT_CODE%" in script and 'set "GRADLE_EXIT_CODE=%ERRORLEVEL%"' in script,
    "a failed test task stops before assembleDebug": "failed with actual process exit code $code" in script and script.index("Invoke-GradleLogged 'testDebugUnitTest'") < script.index("Invoke-GradleLogged 'assembleDebug'"),
    "TLS 1.2 fallback and explicit TLS diagnosis are configured": "TLSv1.2" in (root / "gradle.properties").read_text(encoding="utf-8") and "[NETWORK/TLS]" in script,
    "APK build task runs after unit tests and is separately logged": "Invoke-GradleLogged 'testDebugUnitTest'" in script and "Invoke-GradleLogged 'assembleDebug'" in script and "GRADLE_BUILD_LOG.txt" in script,
}
def test_all_static_checks_pass():
    failures = [name for name, ok in checks.items() if not ok]
    assert not failures, "Static audit failures: " + "; ".join(failures)


if __name__ == "__main__":
    for name, ok in checks.items():
        print(("PASS" if ok else "FAIL") + " " + name)
    print(f"\nAudit tests: {sum(checks.values())}/{len(checks)} passed")
    raise SystemExit(0 if all(checks.values()) else 1)


def test_release_signing_guard_does_not_block_debug_or_unit_tests():
    gradle = (Path(__file__).parent / "app/build.gradle").read_text(encoding="utf-8")
    assert "if (releaseSigningReady) signingConfig signingConfigs.release" in gradle
    assert "task.name in ['preReleaseBuild', 'validateSigningRelease', 'packageRelease', 'packageReleaseBundle', 'bundleRelease']" in gradle
    assert "Debug builds and unit tests do not require the release key." in gradle
    assert "throw new GradleException('Release signing is not configured." in gradle
    assert "if (!hasSigning)" not in gradle
