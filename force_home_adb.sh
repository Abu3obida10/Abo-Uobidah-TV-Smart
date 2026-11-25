#!/usr/bin/env bash
# Optional HOME setup for Linux/macOS hosts; safe: never disables the stock launcher.
set -euo pipefail
PKG='com.uobidah.launcher'
HOMEC="$PKG/.MainLauncherActivity"

if ! command -v adb >/dev/null 2>&1; then
  echo '[FAILED] adb not found. Install Android SDK Platform-Tools.' >&2
  exit 1
fi
if [[ -n "${1:-}" && "$1" != --* ]]; then
  adb connect "$1"
  sleep 2
fi
DEVICES=()
while IFS= read -r device; do
  [[ -n "$device" ]] && DEVICES+=("$device")
done < <(adb devices | awk 'NR > 1 && $2 == "device" {print $1}')
if ((${#DEVICES[@]} == 0)); then
  echo '[FAILED] No authorized ADB device. Enable debugging and accept the RSA prompt.' >&2
  exit 1
elif ((${#DEVICES[@]} == 1)); then
  SERIAL="${DEVICES[0]}"
else
  echo 'More than one device is connected:'
  select SERIAL in "${DEVICES[@]}"; do
    [[ -n "${SERIAL:-}" ]] && break
  done
fi
sh() { echo "> adb -s $SERIAL shell $*"; adb -s "$SERIAL" shell "$@"; }
if ! adb -s "$SERIAL" shell pm path "$PKG" | grep -q '^package:'; then
  echo "[FAILED] $PKG is not installed on $SERIAL. Install the APK first." >&2
  exit 1
fi
API="$(adb -s "$SERIAL" shell getprop ro.build.version.sdk | tr -d '\r')"
if [[ "${API:-0}" -ge 33 ]]; then sh pm grant "$PKG" android.permission.POST_NOTIFICATIONS || echo '[WARNING] Notification permission was not granted.'; fi
sh pm grant "$PKG" android.permission.ACCESS_FINE_LOCATION || echo '[WARNING] Location permission was not granted.'
sh appops set "$PKG" SYSTEM_ALERT_WINDOW allow || echo '[WARNING] Overlay permission was not granted.'
sh pm grant "$PKG" android.permission.WRITE_SECURE_SETTINGS || echo '[WARNING] Secure settings permission was not granted.'
SVC="$PKG/$PKG.HomeKeyOverrideService"
CUR="$(adb -s "$SERIAL" shell settings get secure enabled_accessibility_services | tr -d '\r')"
if [[ "$CUR" == null || -z "$CUR" ]]; then NEW="$SVC"
elif [[ ":$CUR:" == *":$SVC:"* ]]; then NEW="$CUR"
else NEW="$CUR:$SVC"
fi
sh settings put secure enabled_accessibility_services "$NEW"
sh settings put secure accessibility_enabled 1
sh cmd package set-home-activity "$HOMEC" || echo '[WARNING] set-home-activity is not supported by this firmware.'
sh cmd role add-role-holder --user 0 android.app.role.HOME "$PKG" || echo '[WARNING] HOME role shell command is not supported by this firmware.'
sh am start -a android.intent.action.MAIN -c android.intent.category.HOME
RESOLVED="$(adb -s "$SERIAL" shell cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME | tr -d '\r')"
if [[ "$RESOLVED" == *"$PKG"* ]]; then
  echo '[OK] Abo Uobidah TV is resolved as the default HOME app.'
else
  echo '[NOTICE] Select Abo Uobidah TV in the TV Default Home settings. The stock launcher remains installed and enabled.'
fi
