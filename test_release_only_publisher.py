from pathlib import Path

ROOT = Path(__file__).resolve().parent
publisher = (ROOT / "PUBLISH_RELEASE_ONLY.ps1").read_text(encoding="utf-8-sig")
wrapper = (ROOT / "UPLOAD_TO_GITHUB.ps1").read_text(encoding="utf-8-sig")
batch = (ROOT / "UPLOAD_TO_GITHUB.bat").read_text(encoding="utf-8-sig")

def test_release_only_uploads_only_the_two_required_assets():
    assert "$apkAsset = 'Abo-Uobidah-TV.apk'" in publisher
    assert "gh release create $tag $releaseApkPath $manifestPath" in publisher
    assert "$manifestPath = Join-Path $releaseTempDir 'update.json'" in publisher
    assert "No source project will be uploaded" in batch
    assert "git push" not in publisher.lower()
    assert "git add" not in publisher.lower()
    assert "gh release create" in publisher

def test_manifest_is_written_without_utf8_bom():
    assert "UTF8Encoding($false)" in publisher
    assert "WriteAllText($manifestPath" in publisher

def test_release_publisher_checks_apk_identity_and_signature():
    assert "com.uobidah.launcher" in publisher
    assert "apksigner verify --verbose --print-certs" in publisher
    assert "dump badging" in publisher
    assert "ExpectedSignerSha256" in publisher
    assert "gradle.properties" in publisher
    assert "appVersionName" in publisher and "appVersionCode" in publisher
    assert "VersionCode المطلوب لا يطابق رقم البناء المركزي" in publisher
    assert "180MB" in publisher

def test_legacy_upload_entrypoint_delegates_to_release_only_tool():
    assert "PUBLISH_RELEASE_ONLY.ps1" in wrapper
    assert "UPLOAD Abo Uobidah TV Android TV launcher source" not in wrapper
