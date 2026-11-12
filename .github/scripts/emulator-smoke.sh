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
# A package can appear in the activity history even when its screen is not foreground.
# Require MainActivity to be the top-resumed activity, not merely mentioned.
if ! grep -Eq 'topResumedActivity=.*com\.minhajul\.riftreign/\.MainActivity' activity_state.txt; then
  echo "Game MainActivity is not top-resumed in Android activity dump" >&2
  exit 1
fi
# The PLAY hitbox is defined in RiftReignGameView.menuPlayRect() as
# x=60%-84%, y=20.5%-28.5% of the full-screen view.
# Use the center and confirm startGame() emitted its explicit state-transition marker.
size="$(adb shell wm size | tr -d '\r' | sed -n 's/^Physical size: //p' | head -n1)"
if [[ ! "$size" =~ ^([0-9]+)x([0-9]+)$ ]]; then
  echo "Cannot determine emulator display dimensions: $size" >&2
  exit 1
fi
screen_width="${BASH_REMATCH[1]}"
screen_height="${BASH_REMATCH[2]}"
adb shell input tap "$((screen_width * 72 / 100))" "$((screen_height * 245 / 1000))"
sleep 3
adb logcat -d -s RiftReignSmoke:I > gameplay-transition.txt
if ! grep -q 'GAMEPLAY_STARTED' gameplay-transition.txt; then
  echo "PLAY tap did not trigger startGame() state transition" >&2
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
