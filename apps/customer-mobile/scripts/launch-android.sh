#!/usr/bin/env bash
# MB-031 native launch evidence (CI only): boots an Android emulator, installs the release-variant APK (JS bundle
# embedded, debug-keystore signed, not a store build), launches it and checks that the app process is alive, that
# the signed-out home with the sign-in action is on screen and that no fatal exception was logged.
set -euo pipefail
APK="${1:?apk path}"
PKG=com.matbakhy.customer.dev
API_LEVEL=35
IMAGE="system-images;android-${API_LEVEL};google_apis;x86_64"
export PATH="$ANDROID_HOME/cmdline-tools/latest/bin:$ANDROID_HOME/emulator:$ANDROID_HOME/platform-tools:$PATH"

yes | sdkmanager --licenses >/dev/null 2>&1 || true
sdkmanager "emulator" "platform-tools" "$IMAGE" >/dev/null
echo no | avdmanager create avd -n mb031 -k "$IMAGE" --force >/dev/null
emulator -avd mb031 -no-window -no-audio -no-boot-anim -no-snapshot -gpu swiftshader_indirect >"$RUNNER_TEMP/emulator.log" 2>&1 &
adb wait-for-device
for _ in $(seq 1 120); do
  [ "$(adb shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')" = "1" ] && break
  sleep 5
done
[ "$(adb shell getprop sys.boot_completed | tr -d '\r')" = "1" ] || { echo "emulator did not boot"; tail -50 "$RUNNER_TEMP/emulator.log"; exit 1; }
adb shell getprop ro.build.version.release
adb install -r "$APK"
adb logcat -c
adb shell am start -W -n "$PKG/.MainActivity"
sleep 25
PID=$(adb shell pidof "$PKG" | tr -d '\r' || true)
echo "app pid: ${PID:-none}"
adb shell uiautomator dump /sdcard/ui.xml >/dev/null
adb pull /sdcard/ui.xml "$RUNNER_TEMP/ui.xml" >/dev/null
echo "visible texts:"; grep -o 'text="[^"]*"' "$RUNNER_TEMP/ui.xml" | sort -u
adb exec-out screencap -p > "$RUNNER_TEMP/android-launch.png"
sha256sum "$RUNNER_TEMP/android-launch.png"
if adb logcat -d | grep -E "FATAL EXCEPTION|ReactNativeJS.*(Error|Exception)" ; then
  echo "FAIL: fatal error after launch"; exit 1
fi
[ -n "$PID" ] || { echo "FAIL: app process not running"; exit 1; }
grep -qE 'text="(Sign in|تسجيل الدخول)"' "$RUNNER_TEMP/ui.xml" || { echo "FAIL: sign-in action not visible"; exit 1; }
echo "PASS: Android release-variant build launched; signed-out home with sign-in visible; no fatal errors"
