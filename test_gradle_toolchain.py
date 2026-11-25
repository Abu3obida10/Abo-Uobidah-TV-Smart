from pathlib import Path
import re

ROOT = Path(__file__).resolve().parent
root_gradle = (ROOT / "build.gradle").read_text(encoding="utf-8")
wrapper = (ROOT / "gradle/wrapper/gradle-wrapper.properties").read_text(encoding="utf-8")
app_gradle = (ROOT / "app/build.gradle").read_text(encoding="utf-8")

def test_toolchain_versions_are_the_validated_pair():
    assert re.search(r"com\.android\.application' version '8\.6\.1'", root_gradle)
    assert re.search(r"org\.jetbrains\.kotlin\.android' version '2\.1\.21'", root_gradle)
    assert "gradle-8.7-bin.zip" in wrapper
    assert "JavaVersion.VERSION_17" in app_gradle
    assert re.search(r"compileSdk\s+35", app_gradle)

def test_workmanager_dependency_matches_declared_sdk_floor():
    assert "androidx.work:work-runtime-ktx:2.12.0" in app_gradle
    assert re.search(r"minSdk\s+26", app_gradle)
    assert re.search(r"compileSdk\s+35", app_gradle)


def test_repository_fallback_is_configured_without_disabling_tls_validation():
    settings = (ROOT / "settings.gradle").read_text(encoding="utf-8")
    props = (ROOT / "gradle.properties").read_text(encoding="utf-8")
    assert settings.count("https://repo1.maven.org/maven2/") == 2
    assert "systemProp.https.protocols=TLSv1.2" in props
    assert "systemProp.jdk.tls.client.protocols=TLSv1.2" in props
    assert "allowInsecureProtocol" not in settings
