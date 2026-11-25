# Abo Uobidah TV — 1.0.43-merge21

Android TV launcher source package. Stable application ID: `com.uobidah.launcher`. The release identity is defined once in `gradle.properties` (`appVersionName`, `appVersionCode`) and consumed by Gradle and the safe installer.

This source keeps the `merge21` launcher UI and integrates the compatible matches/IPTV module features. It includes M3U/M3U8 parsing and pre-save validation, safe playlist cache handling, match-context IPTV entry, and stronger match result/date/status parsing. Kids and Games remain removed. The three requested clubs remain Al Ahly, Real Madrid, and Manchester United.

## Validation

See `TEST_RESULTS_1_0_43_AR.md` for the current source test results and explicit limits. Source checks are not a substitute for a successful Android Gradle build or a real Android TV test.

## Build requirements

- JDK 17
- Gradle Wrapper 8.7 (provided)
- Android SDK Platform 35
- Android Build Tools 34.0.0

No APK is included unless a build is successfully produced and verified. The original release signing key is required for an in-place update of an installed Release build.
