#!/usr/bin/env bash
set -euo pipefail

APK="$(find app/build/outputs/apk/launch/debug -maxdepth 1 -type f -name '*.apk' -print -quit)"
if [[ -z "$APK" ]]; then
  echo "No launchDebug APK found; available output files:" >&2
  find app/build/outputs -maxdepth 6 -type f >&2 || true
  exit 1
fi

adb install -r "$APK"
adb logcat -c
adb shell am start -W -n com.minhajul.riftreign/.MainActivity | tee launch-result.txt
if ! grep -q '^Status: ok' launch-result.txt; then
  echo "Android did not report a successful activity launch" >&2
  exit 1
fi
sleep 15
adb shell dumpsys activity activities > activity_state.txt
if ! grep -q 'com.minhajul.riftreign' activity_state.txt; then
  echo "Game activity not visible in Android activity dump" >&2
  exit 1
fi
adb logcat -d -s AndroidRuntime:E > runtime-errors.txt
if grep -q 'FATAL EXCEPTION' runtime-errors.txt; then
  cat runtime-errors.txt >&2
  exit 1
fi
# Stream PNG directly to the runner: scoped storage can deny /sdcard writes.
adb exec-out screencap -p > riftreign-boot.png
test -s riftreign-boot.png

# Reject nonempty adb error output masquerading as screenshot evidence.
expected_png_signature='89504e470d0a1a0a'
actual_png_signature="$(od -An -tx1 -N8 riftreign-boot.png | tr -d '[:space:]')"
if [[ "$actual_png_signature" != "$expected_png_signature" ]]; then
  echo "Emulator screenshot is not a PNG (invalid signature)" >&2
  exit 1
fi
