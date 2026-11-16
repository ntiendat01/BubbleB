#!/usr/bin/env bash
# M1 device/emulator gate helper (TASKS.md M1 gate). TEST HARNESS ONLY — run against a test device
# with synthetic content. It is NOT a user instruction for bypassing Android restricted settings.
#
#   1. 20 accessibility service toggles via `settings put secure` (adb shell user).
#   2. 20 overlay attach/detach cycles by switching between our app (bubble hidden: OWN_APP) and Home.
#   3. Checks for FATAL EXCEPTION / ANR of our package and that the overlay is gone when disabled.
#
# Prereqs: one device in `adb devices`, debug APK installed, consent accepted once in the app.
# Usage: scripts/m1_device_check.sh
set -euo pipefail

PKG=com.example.contextbubble
SVC="$PKG/$PKG.service.BubbleAccessibilityService"
CYCLES=${CYCLES:-20}
fail=0

adb get-state >/dev/null
echo "Device: $(adb shell getprop ro.product.model | tr -d '\r') / SDK $(adb shell getprop ro.build.version.sdk | tr -d '\r')"
prev_services=$(adb shell settings get secure enabled_accessibility_services | tr -d '\r')
adb logcat -c

# Overlay window title is exactly "ContextBubble" (activity windows use the lowercase package name).
overlay_present() { adb shell dumpsys window windows | grep -cE 'Window\{[0-9a-f]+ u[0-9]+ ContextBubble\}' || true; }

enable_service() {
  adb shell settings put secure enabled_accessibility_services "$SVC"
  adb shell settings put secure accessibility_enabled 1
}
disable_service() {
  adb shell settings delete secure enabled_accessibility_services >/dev/null
}

echo "== $CYCLES service toggles =="
for i in $(seq 1 "$CYCLES"); do
  enable_service; sleep 2
  adb shell input keyevent KEYCODE_HOME; sleep 1
  disable_service; sleep 2
  if [ "$(overlay_present)" != "0" ]; then echo "FAIL: overlay still present after disable (cycle $i)"; fail=1; fi
done

echo "== $CYCLES overlay attach/detach cycles =="
enable_service; sleep 2
for i in $(seq 1 "$CYCLES"); do
  adb shell monkey -p "$PKG" -c android.intent.category.LAUNCHER 1 >/dev/null 2>&1; sleep 1.5
  adb shell input keyevent KEYCODE_HOME; sleep 1.5
done
echo "Overlay windows after returning Home: $(overlay_present) (expected 1 if consent given + bubble on)"
disable_service; sleep 2
[ "$(overlay_present)" = "0" ] || { echo "FAIL: overlay present after final disable"; fail=1; }

echo "== Crash / ANR scan =="
if adb logcat -d | grep -E "FATAL EXCEPTION|ANR in $PKG" | grep -q .; then
  adb logcat -d | grep -A5 -E "FATAL EXCEPTION|ANR in $PKG" | grep -E "$PKG|Exception" | head -20
  fail=1
else
  echo "No FATAL EXCEPTION / ANR for $PKG"
fi
echo "Stage log (no content): "; adb logcat -d -s CB/Service:I CB/Overlay:I | tail -10

# Restore previous accessibility services setting.
if [ -n "$prev_services" ] && [ "$prev_services" != "null" ]; then
  adb shell settings put secure enabled_accessibility_services "$prev_services"
fi
[ "$fail" = "0" ] && echo "M1 device check: PASS" || echo "M1 device check: FAIL"
exit $fail
