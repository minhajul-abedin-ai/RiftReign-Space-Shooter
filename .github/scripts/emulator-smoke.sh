#!/usr/bin/env bash
set -euo pipefail

APK="$(find app/build/outputs/apk/launch/debug -maxdepth 1 -type f -name '*.apk' -print -quit)"
if [[ -z "$APK" ]]; then
  echo "No launchDebug APK found; available output files:" >&2
  find app/build/outputs -maxdepth 6 -type f >&2 || true
  exit 1
fi

# Boot-complete can precede package-manager readiness on slow hosted emulators.
# Bound the wait and preserve diagnostics for failures rather than assuming readiness.
package_ready=0
for attempt in {1..12}; do
  if adb shell pm path android 2>/dev/null | grep -q '^package:'; then
    package_ready=1
    break
  fi
  echo "Waiting for Android package manager (attempt ${attempt}/12)" >&2
  sleep 5
done
if (( package_ready == 0 )); then
  echo "Android package manager unavailable after bounded readiness checks" >&2
  adb shell getprop sys.boot_completed >&2 || true
  adb shell service list >&2 || true
  exit 1
fi

# One controlled retry accommodates transient package-service disconnects.
if ! adb install -r "$APK"; then
  echo "First APK install failed; checking package service before one retry" >&2
  adb shell service list >&2 || true
  sleep 5
  adb install -r "$APK"
fi
# Fresh emulator images show a first-use immersive-mode confirmation above
# full-screen games. Mark it acknowledged in this disposable CI emulator before
# launching the app, rather than attempting unreliable taps/key events later.
# Read back the value to surface unsupported or denied settings writes.
adb shell settings put secure immersive_mode_confirmations confirmed
immersive_confirmation_setting="$(adb shell settings get secure immersive_mode_confirmations | tr -d '\r')"
if [[ "$immersive_confirmation_setting" != "confirmed" ]]; then
  echo "Could not acknowledge immersive-mode confirmation: $immersive_confirmation_setting" >&2
  exit 1
fi
adb logcat -c
# Hosted emulators sometimes report a launch timeout even after APK installation.
# Preserve evidence on this early failure path (before the gameplay checks run).
if ! adb shell am start -W -n com.minhajul.riftreign/.MainActivity | tee launch-result.txt; then
  echo "adb activity launch command failed" >&2
  adb shell dumpsys activity activities > activity_state.txt 2>&1 || true
  adb logcat -d -s AndroidRuntime:E > runtime-errors.txt 2>&1 || true
  adb exec-out screencap -p > riftreign-boot.png 2>/dev/null || true
  exit 1
fi
if ! grep -q '^Status: ok' launch-result.txt; then
  echo "Android did not report a successful activity launch; retaining activity, crash and screen diagnostics" >&2
  adb shell dumpsys activity activities > activity_state.txt 2>&1 || true
  adb logcat -d -s AndroidRuntime:E > runtime-errors.txt 2>&1 || true
  adb exec-out screencap -p > riftreign-boot.png 2>/dev/null || true
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
# wm size reports physical portrait dimensions even after the game rotates to
# landscape. Read the actual rendered PNG dimensions instead, so adb input tap
# uses coordinates in the current display orientation.
physical_size="$(adb shell wm size | tr -d '\r' | sed -n 's/^Physical size: //p' | head -n1)"
adb exec-out screencap -p > riftreign-boot.png
expected_png_signature='89504e470d0a1a0a'
actual_png_signature="$(od -An -tx1 -N8 riftreign-boot.png | tr -d '[:space:]')"
if [[ "$actual_png_signature" != "$expected_png_signature" ]]; then
  echo "Pre-tap screenshot is not a PNG" >&2
  exit 1
fi
# PNG IHDR stores width and height as unsigned big-endian 32-bit integers.
read -r w1 w2 w3 w4 h1 h2 h3 h4 <<< "$(od -An -tu1 -j16 -N8 riftreign-boot.png)"
screen_width=$(( (w1 << 24) | (w2 << 16) | (w3 << 8) | w4 ))
screen_height=$(( (h1 << 24) | (h2 << 16) | (h3 << 8) | h4 ))
if (( screen_width < 100 || screen_height < 100 )); then
  echo "Invalid screenshot dimensions: ${screen_width}x${screen_height}" >&2
  exit 1
fi
size="${screen_width}x${screen_height}"
# Fresh Android emulators may display a system-owned immersive-mode dialog
# over the game. A PLAY tap cannot reach the game until it is dismissed.
adb shell dumpsys window > window_state.txt
if grep -Eq 'mCurrentFocus=.*ImmersiveModeConfirmation' window_state.txt; then
  echo "Dismissing Android immersive-mode confirmation before PLAY" >&2
  # Avoid guessing a dialog button's screen coordinates. Android's focused
  # confirmation button accepts ENTER; retain evidence if it does not.
  adb shell input keyevent 66
  sleep 2
  adb shell dumpsys window > window_state.txt
  if grep -Eq 'mCurrentFocus=.*ImmersiveModeConfirmation' window_state.txt; then
    echo "Immersive-mode confirmation still focused after ENTER" >&2
    adb exec-out screencap -p > riftreign-post-tap.png 2>/dev/null || true
    adb shell dumpsys window > window-post-tap.txt 2>&1 || true
    exit 1
  fi
  adb exec-out screencap -p > riftreign-boot.png
fi
printf 'Physical size: %s\nRendered size: %s\nTap coordinates: %s,%s\n' "$physical_size" "$size" "$((screen_width * 72 / 100))" "$((screen_height * 245 / 1000))" > tap-diagnostics.txt
adb shell input tap "$((screen_width * 72 / 100))" "$((screen_height * 245 / 1000))"
sleep 3
# Capture post-input evidence before assertions so failures retain the actual UI state.
adb exec-out screencap -p > riftreign-post-tap.png 2>/dev/null || true
adb shell dumpsys window > window-post-tap.txt 2>&1 || true
adb logcat -d -s RiftReignSmoke:I > gameplay-transition.txt
adb logcat -d -s AndroidRuntime:E > runtime-errors.txt
if ! grep -q 'GAMEPLAY_STARTED' gameplay-transition.txt; then
  echo "PLAY tap did not trigger startGame() state transition" >&2
  adb shell dumpsys activity activities >> activity_state.txt || true
  adb logcat -d -t 300 >> tap-diagnostics.txt || true
  exit 1
fi
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
