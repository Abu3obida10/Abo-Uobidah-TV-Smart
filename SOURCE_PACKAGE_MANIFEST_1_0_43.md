# Source package manifest — Abo Uobidah TV 1.0.43-merge21

- Base: `merge21 r2`, with integrated IPTV/matches repairs.
- `applicationId`: `com.uobidah.launcher` (unchanged for update compatibility).
- `appVersionName`: `1.0.43-merge21` in `gradle.properties`.
- `appVersionCode`: `65` in `gradle.properties`.
- Gradle build and safe installer read the same central version properties.
- Package contents: Android app source, Gradle wrapper, current test/audit scripts, build/install scripts, and current release documentation.
- Historical per-release reports and transient Python caches are excluded. Compatibility tests and in-app migration logic are retained.
- No APK is included: the active environment lacks an installed Gradle 8.7 distribution and Android SDK Platform 35.
