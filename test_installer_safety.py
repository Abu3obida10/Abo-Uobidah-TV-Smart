from pathlib import Path

root = Path(__file__).resolve().parent
installer = (root / "INSTALL_APK.ps1").read_text(encoding="utf-8")
build = (root / "AUTO_BUILD_INSTALL.ps1").read_text(encoding="utf-8-sig")
app_gradle = (root / "app/build.gradle").read_text(encoding="utf-8")
checks = {
    "release version is defined centrally and Gradle consumes it": "appVersionName=1.0.48" in (root / "gradle.properties").read_text(encoding="utf-8") and "appVersionCode=69" in (root / "gradle.properties").read_text(encoding="utf-8") and 'providers.gradleProperty("appVersionName")' in app_gradle and 'providers.gradleProperty("appVersionCode")' in app_gradle,
    "standalone installer reads the same central release identity": "gradle.properties" in installer and "appVersionName" in installer and "appVersionCode" in installer and "$ExpectedVersionCode = [int]$VersionCodeMatch.Groups[1].Value" in installer,
    "installer validates application ID": "com.uobidah.launcher" in installer and "Wrong application ID" in installer,
    "installer validates APK package and version": "aapt dump badging" in installer and "Unexpected APK version" in installer,
    "installer verifies APK signing certificate": "verify --verbose --print-certs" in installer,
    "installer compares old and new signing certificates": "$oldDigest -ne $newDigest" in installer,
    "signature conflict never triggers auto uninstall": "adb uninstall" not in installer.lower() and "will never do that automatically" in installer,
    "installer blocks version downgrades": "INSTALL_FAILED_VERSION_DOWNGRADE" in installer and "versionCode" in installer,
    "installer handles insufficient storage": "INSTALL_FAILED_INSUFFICIENT_STORAGE" in installer,
    "installer handles user-restricted installs": "INSTALL_FAILED_USER_RESTRICTED" in installer,
    "installer diagnoses ABI and older Android conflicts": "INSTALL_FAILED_NO_MATCHING_ABIS" in installer and "INSTALL_FAILED_OLDER_SDK" in installer,
    "installer accepts custom Android SDK roots": "[string]$SdkRoot" in installer,
    "installer requires explicit target when multiple devices are connected": "Multiple authorized devices" in installer and "Device number" in installer,
    "installer checks Android API minSdk": "deviceSdk -lt 26" in installer,
    "installer verifies package after installation": "post-install verification failed" in installer and "pm path" in installer,
    "installer makes HOME setup optional": "Set Abo Uobidah TV as the default HOME launcher" in installer,
    "installer confirms app package after installing": "post-install verification failed" in installer and "pm path" in installer,
    "build reuses a complete SDK without requiring sdkmanager": "A complete local SDK can still build offline" in build,
    "sdkmanager download only happens after required SDK files are missing": build.index("if (-not $sdkManager)") > build.index("if ($sdkMissing.Count -eq 0)"),
    "build delegates to safe installer": "INSTALL_APK.ps1" in build and "Safe ADB installation" in build,
    "standalone installer entry point exists": (root / "INSTALL_APK.bat").exists(),
    "installer captures native stderr safely in Windows PowerShell 5.1": "function Invoke-NativeCapture" in installer and "$ErrorActionPreference = 'Continue'" in installer and "$LASTEXITCODE = -1" in installer,
    "HOME setup captures native ADB stderr safely": "function Invoke-NativeCapture" in (root / "FORCE_HOME_ADB.ps1").read_text(encoding="utf-8") and "$LASTEXITCODE = -1" in (root / "FORCE_HOME_ADB.ps1").read_text(encoding="utf-8"),
}
def test_all_static_checks_pass():
    failures = [name for name, ok in checks.items() if not ok]
    assert not failures, "Static audit failures: " + "; ".join(failures)


if __name__ == "__main__":
    for name, ok in checks.items():
        print(("PASS" if ok else "FAIL") + " " + name)
    print(f"\nAudit tests: {sum(checks.values())}/{len(checks)} passed")
    raise SystemExit(0 if all(checks.values()) else 1)
