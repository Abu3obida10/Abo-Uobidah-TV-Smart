#!/usr/bin/env python3
"""Static integrity checks for the Android TV launcher source package."""
from pathlib import Path
import re
import sys
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parent
SRC = ROOT / "app/src/main/java"
RES = ROOT / "app/src/main/res"
ANDROID = "{http://schemas.android.com/apk/res/android}"
checks: list[tuple[str, bool, str]] = []

def add(name: str, ok: bool, detail: str = "") -> None:
    checks.append((name, bool(ok), detail))

manifest_path = ROOT / "app/src/main/AndroidManifest.xml"
manifest_text = manifest_path.read_text(encoding="utf-8")
manifest = ET.parse(manifest_path).getroot()
source_files = list(SRC.rglob("*.kt"))
source_text = "\n".join(p.read_text(encoding="utf-8") for p in source_files)
classes = set(re.findall(r"\b(?:class|object)\s+(\w+)", source_text))

components: list[tuple[str, str]] = []
for tag in ("activity", "service", "receiver", "provider"):
    for node in manifest.findall(f".//{tag}"):
        name = node.get(ANDROID + "name")
        if name:
            components.append((tag, name))
missing_components = [
    (tag, name) for tag, name in components
    if name.rsplit(".", 1)[-1] not in classes and name.rsplit(".", 1)[-1] != "FileProvider"
]
add("Manifest components resolve to source classes", not missing_components, str(missing_components))

intent_classes: set[str] = set()
for file in source_files:
    txt = file.read_text(encoding="utf-8")
    intent_classes.update(re.findall(r"Intent\([^\n]*?,\s*(\w+)::class\.java\)", txt))
undeclared = [
    name for name in sorted(intent_classes)
    if f'android:name=".{name}"' not in manifest_text
]
add("Explicit in-app activity intents are declared in the manifest", not undeclared, str(undeclared))

resource_refs = re.findall(r"@([a-z]+)/([A-Za-z0-9_.]+)", manifest_text)
resource_errors: list[tuple[str, str]] = []
for typ, name in resource_refs:
    if typ in ("string", "style", "color", "integer", "bool", "dimen"):
        found = False
        for values_file in (RES / "values").glob("*.xml"):
            doc = ET.parse(values_file).getroot()
            if any(node.get("name") == name for node in doc.iter()):
                found = True
                break
    elif typ in ("drawable", "mipmap", "xml"):
        found = any((RES / typ).glob(name + ".*"))
    else:
        found = True  # platform resource or a type not needed by the manifest check
    if not found:
        resource_errors.append((typ, name))
add("Manifest resource references exist", not resource_errors, str(resource_errors))

# Android TV launcher must be discoverable by both HOME and Leanback launcher intents.
add("Main activity has HOME intent filter", "android.intent.category.HOME" in manifest_text)
add("Main activity has Leanback launcher intent filter", "android.intent.category.LEANBACK_LAUNCHER" in manifest_text)

# Keep stock launcher safety: shipped helper scripts must not disable/uninstall CVTE.
destructive: list[str] = []
for file in ROOT.rglob("*"):
    if file.is_file() and file.suffix.lower() in (".ps1", ".bat", ".sh"):
        text = file.read_text(encoding="utf-8", errors="ignore")
        if re.search(r"\bpm\s+disable(?:-user)?\b|\bpm\s+uninstall\b|\badb\s+uninstall\b", text, re.I):
            destructive.append(str(file.relative_to(ROOT)))
add("No helper script disables or uninstalls the manufacturer launcher", not destructive, str(destructive))

build = (ROOT / "app/build.gradle").read_text(encoding="utf-8")
version_props = (ROOT / "gradle.properties").read_text(encoding="utf-8")
add("Release identity has one central version source consumed by Gradle", "appVersionName=1.0.52" in version_props and "appVersionCode=73" in version_props and 'providers.gradleProperty("appVersionName")' in build and 'providers.gradleProperty("appVersionCode")' in build)
add("WorkManager 2.12.0 metadata requirements are met", "androidx.work:work-runtime-ktx:2.12.0" in build and "compileSdk 35" in build and "com.android.application' version '8.6.1'" in (ROOT / "build.gradle").read_text(encoding="utf-8"))
ys_provider = (SRC / "com/uobidah/launcher/YallaScoresProvider.kt").read_text(encoding="utf-8")
add("BuildConfig is explicitly generated and YSscores User-Agent follows current app version", "buildConfig true" in build and "BuildConfig.VERSION_NAME" in ys_provider and "1.0.42-merge21" not in ys_provider)
strings = (RES / "values/strings.xml").read_text(encoding="utf-8")
settings_gradle = (ROOT / "settings.gradle").read_text(encoding="utf-8")
add("Android launcher label is Abo Uobidah TV", '<string name="app_name">Abo Uobidah TV</string>' in strings)
add("Existing applicationId remains stable for upgrade compatibility", 'applicationId "com.uobidah.launcher"' in build)
add("Gradle root project name matches product name", 'rootProject.name = "AboUobidahTV"' in settings_gradle)
add("Release variant does not reuse the debug signing key", "signingConfig signingConfigs.debug" not in build)
add("Legacy Android manifest package attribute removed", 'package="com.uobidah.launcher"' not in manifest_text)
add("Legacy visible app title removed from active source", ("Uobidah" + " Smart Launcher") not in source_text and ("Uobidah" + " Smart Launcher") not in strings)

props = (ROOT / "gradle.properties").read_text(encoding="utf-8")
ps = (ROOT / "AUTO_BUILD_INSTALL.ps1").read_text(encoding="utf-8-sig")
installer = (ROOT / "INSTALL_APK.ps1").read_text(encoding="utf-8") if (ROOT / "INSTALL_APK.ps1").exists() else ""
add("Build script saves Gradle test output", "GRADLE_TEST_LOG.txt" in ps)
add("Windows PowerShell 5.1 native stderr is captured safely", "function Invoke-NativeCapture" in ps and "$ErrorActionPreference = 'Continue'" in ps and "$LASTEXITCODE = -1" in ps)
add("Gradle tests and APK build use cmd.exe process capture with the true exit code", "System.Diagnostics.ProcessStartInfo" in ps and "$code = [int]$process.ExitCode" in ps and "Invoke-GradleLogged 'testDebugUnitTest'" in ps and "Invoke-GradleLogged 'assembleDebug'" in ps)
installer_capture = "function Invoke-NativeCapture" in installer and "$nativeExitCode = [int]$LASTEXITCODE" in installer
add("APK installer protects PowerShell 5.1 from native stderr termination", installer_capture)
home_setup = (ROOT / "FORCE_HOME_ADB.ps1").read_text(encoding="utf-8")
add("HOME setup protects PowerShell 5.1 from native stderr termination", "function Invoke-NativeCapture" in home_setup and "$nativeExitCode = [int]$LASTEXITCODE" in home_setup)
add("Build script saves Gradle APK build output", "GRADLE_BUILD_LOG.txt" in ps)
add("Build script reports Gradle root-cause lines", all(x in ps for x in ("What went wrong:", "Caused by:", "Execution failed for task")))
add("Gradle worker execution is limited", "org.gradle.workers.max=2" in props and "--max-workers=2" in ps)
add("Kotlin compiler uses in-process strategy", "kotlin.compiler.execution.strategy=in-process" in props)
root_gradle_text = (ROOT / "build.gradle").read_text(encoding="utf-8")
wrapper_text = (ROOT / "gradle/wrapper/gradle-wrapper.properties").read_text(encoding="utf-8")
add("Kotlin Gradle plugin is compatible with Gradle 8.7 and AGP 8.6.1", "org.jetbrains.kotlin.android' version '2.1.21'" in root_gradle_text and "com.android.application' version '8.6.1'" in root_gradle_text and "gradle-8.7-bin.zip" in wrapper_text)
publisher = (ROOT / "PUBLISH_RELEASE_ONLY.ps1").read_text(encoding="utf-8-sig")
add("GitHub publisher is release-only and never pushes source", "gh release create" in publisher and "git push" not in publisher.lower() and "git add" not in publisher.lower())
add("GitHub publisher uses exact fixed asset name", "$apkAsset = 'Abo-Uobidah-TV.apk'" in publisher and "$releaseApkPath $manifestPath" in publisher)
add("GitHub publisher writes update manifest without UTF-8 BOM", "UTF8Encoding($false)" in publisher and "WriteAllText($manifestPath" in publisher)
add("No GitHub Actions workflow requires uploading source", not (ROOT / ".github/workflows/build.yml").exists())

# Regression guard for the compiler failure reported on Windows: a subclass
# cannot inspect the backing field of a protected lateinit property declared
# in SettingsBase with ::body.isInitialized. The view is initialized by
# setupScreen() in HomeControlActivity.onCreate() before render() is called.
home_control_source = (SRC / "com/uobidah/launcher/HomeControlActivity.kt").read_text(encoding="utf-8")
add(
    "Home control avoids illegal inherited lateinit backing-field access",
    "::body.isInitialized" not in home_control_source and "body.removeAllViews()" in home_control_source,
)

installer_bat = ROOT / "INSTALL_APK.bat"
add("Safe standalone APK installer exists", bool(installer) and installer_bat.exists())
add("APK installer checks signature compatibility before in-place update", "$oldDigest -ne $newDigest" in installer and "Get-SignerDigest" in installer)
add("APK installer never performs automatic uninstall", "adb uninstall" not in installer.lower())
add("APK installer blocks version downgrades", "INSTALL_FAILED_VERSION_DOWNGRADE" in installer)
add("APK installer reads the central Gradle release identity", "gradle.properties" in installer and "appVersionName" in installer and "appVersionCode" in installer and "$ExpectedVersionCode = [int]$VersionCodeMatch.Groups[1].Value" in installer)
publisher = (ROOT / "PUBLISH_RELEASE_ONLY.ps1").read_text(encoding="utf-8-sig")
add("Release publisher reads the same central version and rejects manual mismatches", "gradle.properties" in publisher and "appVersionName" in publisher and "appVersionCode" in publisher and "VersionCode المطلوب لا يطابق رقم البناء المركزي" in publisher)
add("APK installer checks multiple devices before choosing target", "Multiple authorized devices" in installer and "Device number" in installer)
add("SDK command-line tools are only downloaded when SDK packages are missing", "A complete local SDK can still build offline" in (ROOT / "AUTO_BUILD_INSTALL.ps1").read_text(encoding="utf-8-sig"))
add("GitHub-managed build uses the central monotonically increasing release identity", "appVersionName=1.0.52" in version_props and "appVersionCode=73" in version_props and 'providers.gradleProperty("appVersionName")' in build)
add("Legacy HOME setup entry point delegates to the checked implementation", "FORCE_HOME_ADB.ps1" in (ROOT / "SET_UOBIDAH_HOME_SAFE.ps1").read_text(encoding="utf-8"))
add("Shell HOME helper checks ADB device and installed package", "No authorized ADB device" in (ROOT / "force_home_adb.sh").read_text(encoding="utf-8") and "pm path" in (ROOT / "force_home_adb.sh").read_text(encoding="utf-8"))

# YSscores native live-feed integration regression guards.
ys_provider = (SRC / "com/uobidah/launcher/YallaScoresProvider.kt").read_text(encoding="utf-8")
ys_parser = (SRC / "com/uobidah/launcher/core/YallaScoresHtmlParser.kt").read_text(encoding="utf-8")
match_repo = (SRC / "com/uobidah/launcher/Repos.kt").read_text(encoding="utf-8")
match_detail = (SRC / "com/uobidah/launcher/MatchDetailActivity.kt").read_text(encoding="utf-8")
matches_screen = (SRC / "com/uobidah/launcher/MatchesActivity.kt").read_text(encoding="utf-8")
island_service = (SRC / "com/uobidah/launcher/DynamicIslandOverlayService.kt").read_text(encoding="utf-8")
add("YSscores adapter reads public team pages over HTTPS with response-size and timeout limits", '"https://www.ysscores.com"' in ys_provider and "timeoutMs = 7_000" in ys_provider and "MAX_PAGE_BYTES" in ys_provider)
add("YSscores native parser extracts fixture links, marked scores, explicit statuses and source timezone", all(x in ys_parser for x in ("ar/match", "extractScoreFromCard(datedCard ?: singleCard ?: anchor)", "statusOf(cardNode, allText)", "detectSourceZone", "home !in 0..20", "data-home-score", "data-score")))
add("YSscores provider resolves the three built-in favourite clubs", all(x in ys_provider for x in ("101745/Al-Ahly-FC", "8633/Real-Madrid", "10260/Manchester-United")))
add("YSscores logos are accepted only from its public HTTPS image hosts", "extractTeamBadge" in ys_provider and "imgs.ysscores.com" in ys_parser and 'uri.scheme == "https"' in ys_parser)
add("Match repository routes YSscores results through normal next/last cache and fallback", '"ysscores" -> YallaScoresProvider.fetchTeam' in match_repo and '"externalUrl"' in match_repo and "mergeAndCache" in match_repo)
add("Finished scores are checked against SofaScore even when optional multi-source mode is disabled", "hasFinishedToVerify" in match_repo and 'other != "sofascore"' in match_repo and "state == MatchState.FINISHED" in match_detail)
add("Native match screens pass the exact YSscores match URL to match details", 'putExtra("externalUrl", match.externalUrl)' in matches_screen and 'putExtra("externalUrl", m.externalUrl)' in source_text and 'YallaScoresActivity.EXTRA_URL, externalUrl' in match_detail)
add("Dynamic Island compares YSscores live feed scores for goal notifications", 'm.source == "thesportsdb") MatchRepo.event(m.id) else m' in island_service and '60_000L' in island_service)
add("Parser fixtures cover finished/live/Arabic-digit/zone/logo cases", (ROOT / "app/src/test/java/com/uobidah/launcher/core/YallaScoresHtmlParserTest.kt").exists())
add("Network queue exposes a non-blocking submission result for loading screens", "fun submitBg(block: () -> Unit): Boolean" in (SRC / "com/uobidah/launcher/Net.kt").read_text(encoding="utf-8"))
add("YSscores duplicate team downloads are deduplicated and transient failures are throttled", "ConcurrentHashMap<String, Any>" in ys_provider and "failureCache" in ys_provider and "30_000L" in ys_provider)
add("YSscores screen loads global daily fixtures and favourite teams in background", "fetchAllDay(date)" in (SRC / "com/uobidah/launcher/YallaScoresActivity.kt").read_text(encoding="utf-8") and "Net.submitBg" in (SRC / "com/uobidah/launcher/YallaScoresActivity.kt").read_text(encoding="utf-8"))
add("YSscores team page failure falls back to SofaScore", "SofaScoreProvider.fetchTeam(prefs, team)" in (SRC / "com/uobidah/launcher/YallaScoresActivity.kt").read_text(encoding="utf-8"))
add("SofaScore global fixtures are cached and parsed", "scheduled-events/$date" in (SRC / "com/uobidah/launcher/SofaScoreProvider.kt").read_text(encoding="utf-8") and "all-day|$date" in (SRC / "com/uobidah/launcher/SofaScoreProvider.kt").read_text(encoding="utf-8"))
add("Phone match screen uses vertical scrolling for all match sections", "val verticalScroll = android.widget.ScrollView(this)" in matches_screen and "rowHeight = px(176f)" in matches_screen)
add("Match screen uses YSscores as a supported-club fallback when configured sources return empty", "if (nextList.isEmpty() && lastList.isEmpty())" in matches_screen and "YallaScoresProvider.fetchTeam(def, prefs.cityTimezone)" in matches_screen)
add("Background prayer service defaults to a lower polling frequency", 'i("perf_poll_sec", 60)' in (SRC / "com/uobidah/launcher/Prefs.kt").read_text(encoding="utf-8"))
add("Match screen loads tracked clubs concurrently rather than serially", "val completed = AtomicInteger(0)" in matches_screen and "Net.submitBg" in matches_screen)
add("Match list does not fetch remote opponent crests for every card", "MatchRepo.next(prefs, id).map" not in matches_screen and "MatchRepo.last(prefs, id).map" not in matches_screen)

m3u_parser = (SRC / "com/uobidah/launcher/core/M3uParser.kt").read_text(encoding="utf-8")
iptv_screen = (SRC / "com/uobidah/launcher/IptvProActivity.kt").read_text(encoding="utf-8")
add("M3U parser handles BOM, CRLF, metadata ordering, safe HTTP(S) URLs and duplicate suppression", all(x in m3u_parser for x in ("removePrefix(\"\\uFEFF\")", "firstUnquotedComma", "validHttpUri", "putIfAbsent")))
add("Remote IPTV lists are streamed, header-checked and parsed before being saved", "اختبر الرابط واحفظه" in iptv_screen and "Net.downloadToFile(u, download" in iptv_screen and iptv_screen.index("M3uParser.hasM3uHeader(download)", iptv_screen.index("private fun addM3uDialog")) < iptv_screen.index("M3u.parse(download)", iptv_screen.index("private fun addM3uDialog")) < iptv_screen.index("prefs.iptvPlaylists = cur", iptv_screen.index("private fun addM3uDialog")))
add("IPTV cache uses SHA-256 and validates legacy migration before promotion", "MessageDigest.getInstance(\"SHA-256\")" in iptv_screen and "legacy.copyTo(temp" in iptv_screen and "parseValidPlaylistFile(legacy)" in iptv_screen)
add("IPTV preserves last known-good cache and writes replacement through unique temporary and backup files", "readValidPlaylist(file)" in iptv_screen and "target.name + \".tmp-\"" in iptv_screen and "target.name + \".bak-\"" in iptv_screen)
add("IPTV cache restores the newest parseable backup and never deletes the last good copy on failed recovery", "damaged-" in iptv_screen and "parseValidPlaylistFile(backup)" in iptv_screen and "Do not erase the only known-good copy" in iptv_screen)
add("Match details open the in-app IPTV chooser with the actual fixture context", "EXTRA_OPEN_CHOOSER" in match_detail and "EXTRA_MATCH_LABEL" in match_detail and "your-iptv-server.com" not in source_text)
add("IPTV match launch prioritises live channels and displays the selected fixture", "matchContextLabel" in iptv_screen and "if (matchContextLabel.isNotBlank()) typeFilter = 1" in iptv_screen and "تم اختيار القنوات المباشرة تلقائياً" in iptv_screen)
add("Local M3U import validates content and installs the file atomically", "Net.submitBg" in iptv_screen and "temp.renameTo(target)" in iptv_screen and "الملف ليس قائمة M3U صحيحة" in iptv_screen)
add("IPTV reports partial refresh failures and continues using valid downloaded data", "usedFallbackCache" in iptv_screen and "cacheWriteFailure" in iptv_screen and "تعذر تحديث بعض المصادر" in iptv_screen)
add("IPTV latest-request gate prevents stale tab loads from repainting newer selections", "playlistLoadGate" in iptv_screen and "playlistLoadGate.isCurrent(requestToken)" in iptv_screen and "runIfCurrent(requestToken)" in iptv_screen)

epg_screen = (SRC / "com/uobidah/launcher/Epg.kt").read_text(encoding="utf-8")
stream_resolver = (SRC / "com/uobidah/launcher/core/IptvStreamTypeResolver.kt").read_text(encoding="utf-8")
match_reconciler = (SRC / "com/uobidah/launcher/core/MatchScoreReconciler.kt").read_text(encoding="utf-8")
add("M3U keeps distinct channel labels/groups that share a transport URL while collapsing identical rows", "boundedTvgId" in m3u_parser and "boundedGroup" in m3u_parser and "boundedName" in m3u_parser and "joinToString(\"\\u001f\")" in m3u_parser)
add("IPTV cache commits are latest-write-wins per playlist URL, independently of tab selection", "playlistCacheWriteGates" in iptv_screen and "cacheWriteGate(file)" in iptv_screen and "writeGate.runIfCurrent(requestToken)" in iptv_screen)
add("IPTV explicitly recognises HLS manifests hidden behind query-based provider URLs", "IptvStreamTypeResolver.isHls(ch.url)" in iptv_screen and "MimeTypes.APPLICATION_M3U8" in iptv_screen and "output|format|container|type" in stream_resolver)
add("IPTV player reports network, HTTP, manifest and decoder failures separately", all(x in iptv_screen for x in ("ERROR_CODE_IO_NETWORK_CONNECTION_FAILED", "ERROR_CODE_IO_BAD_HTTP_STATUS", "ERROR_CODE_PARSING_MANIFEST_MALFORMED", "ERROR_CODE_DECODING_FAILED")))
add("EPG is streamed to disk and gzip expansion is bounded including skipped bytes", "Net.downloadToFile" in epg_screen and "MAX_EXPANDED_BYTES" in epg_screen and "LimitedInputStream" in epg_screen and "override fun skip" in epg_screen)
add("EPG cache uses SHA-256, atomic promotion and crash recovery", "MessageDigest.getInstance(\"SHA-256\")" in epg_screen and "promoteCache" in epg_screen and "readCacheWithRecovery" in epg_screen and ".bak-" in epg_screen)
add("EPG late responses cannot overwrite newer URL selections", "loadGeneration" in epg_screen and "loadGeneration.get() != token" in epg_screen)
add("Live score reconciliation rejects per-team regressions and merges complete score pairs atomically", "shouldAcceptLiveScore" in match_reconciler and "mergeLiveScore" in match_reconciler and "MatchScoreReconciler.shouldAcceptLiveScore" in (SRC / "com/uobidah/launcher/Repos.kt").read_text(encoding="utf-8"))

match_detail_screen = (SRC / "com/uobidah/launcher/MatchDetailActivity.kt").read_text(encoding="utf-8")
add("Live match details refresh ESPN and YSscores from their own providers using exact fixture matching", "MatchRefreshFeed.ESPN" in match_detail_screen and "EspnSoccerProvider.fetchTeam" in match_detail_screen and "MatchRefreshFeed.YSSCORES" in match_detail_screen and "YallaScoresProvider.fetchTeam" in match_detail_screen)
add("M3U line reader bounds allocation before skipping malformed overlong lines", "BoundedLineReader" in m3u_parser and "input.readLine(MAX_LINE_CHARS)" in m3u_parser)
add("M3U UI uses the same composite row identity as the parser instead of collapsing channels by URL", "fun channelIdentity(channel: Channel)" in iptv_screen and "distinctBy(::channelIdentity)" in iptv_screen and "M3uParser.identity" in iptv_screen)
add("Stale IPTV tab requests finish their source cache writes while only UI painting is gated", "Continue finishing each playlist's own cache" in iptv_screen and "writeGate.runIfCurrent(requestToken)" in iptv_screen and "if (!playlistLoadGate.isCurrent(requestToken)) break" not in iptv_screen)
add("EPG rejects truncated programme-limit documents and incomplete XMLTV roots", "while (event != org.xmlpull.v1.XmlPullParser.END_DOCUMENT)" in epg_screen and "rootClosed" in epg_screen and "XMLTV exceeds the supported programme limit" in epg_screen)
add("EPG parser joins split title and display-name text and validates timestamps fully", "displayBuilder.append" in epg_screen and "titleBuilder.append" in epg_screen and "position.index == timestampText.length" in epg_screen)

passed = sum(1 for _, ok, _ in checks if ok)
for name, ok, detail in checks:
    print(("PASS" if ok else "FAIL") + " " + name + (f" — {detail}" if detail else ""))

print(f"\nProject integrity audit: {passed}/{len(checks)} passed, {len(checks)-passed} failed")
if passed != len(checks):
    sys.exit(1)
