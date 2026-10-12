#!/usr/bin/env bash
# MB-031 / TC-048 on a real Android emulator (CI only): the release-variant app signs in through the system browser
# (Custom Tabs, PKCE) against the local Keycloak and API, with synthetic users only. Checks:
#   1. customer-a signs in; the session survives an app restart (OS secure storage).
#   2. sign-out leaves nothing: after a restart the app is signed out.
#   3. the next sign-in shows the login form again (no silent re-login as A), customer-b signs in, A's name is gone.
#   4. "Switch account" from B also demands a fresh login; cancelling leaves the app signed out with B's data gone.
# Expects: emulator running with the app installed (launch-android.sh), stack up (scripts/dev.sh up), USER_PASSWORD.
set -euo pipefail
PKG=com.matbakhy.customer.dev
export PATH="$ANDROID_HOME/platform-tools:$PATH"
: "${USER_PASSWORD:?synthetic user password}"
OUT="${RUNNER_TEMP:-/tmp}/mb031-roundtrip"
mkdir -p "$OUT"
UI="$OUT/ui.xml"
STEP=0

echo "browsers: $(adb shell pm list packages | tr -d '\r' | grep -iE 'chrome|browser' | tr '\n' ' ')"
# Skip Chrome's first-run screens (emulator images allow adb root; best effort).
adb root >/dev/null 2>&1 && sleep 3 && adb wait-for-device || true
adb shell 'echo "chrome --disable-fre --no-default-browser-check --no-first-run" > /data/local/tmp/chrome-command-line' || true
adb shell 'cp /data/local/tmp/chrome-command-line /data/local/chrome-command-line 2>/dev/null; chmod 644 /data/local/tmp/chrome-command-line' || true
adb shell am force-stop com.android.chrome || true
# Port forwards come after `adb root`, which restarts adbd and drops them (native run 12).
adb reverse tcp:8080 tcp:8080 >/dev/null
adb reverse tcp:8180 tcp:8180 >/dev/null
adb reverse --list
adb shell "curl -s -o /dev/null -w 'device -> identity: %{http_code}\n' http://localhost:8180/realms/matbakhy/.well-known/openid-configuration" || true
adb logcat -c

dump() {
  adb shell uiautomator dump /sdcard/ui.xml >/dev/null 2>&1 || true
  adb exec-out cat /sdcard/ui.xml > "$UI" 2>/dev/null || : > "$UI"
}
# Prints "x y" of the first node whose attributes match the regex.
center_of() {
  python3 - "$UI" "$1" <<'P'
import re, sys
xml = open(sys.argv[1], encoding="utf-8", errors="replace").read()
for node in re.findall(r"<node [^>]*>", xml):
    if re.search(sys.argv[2], node):
        m = re.search(r'bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"', node)
        if m:
            x1, y1, x2, y2 = map(int, m.groups())
            print((x1 + x2) // 2, (y1 + y2) // 2)
            break
P
}
texts() { grep -o 'text="[^"]*"' "$UI" | sort -u | tr '\n' ' '; echo; }
shot() {
  STEP=$((STEP + 1))
  adb exec-out screencap -p > "$OUT/$STEP-$1.png"
  echo "screenshot $STEP-$1: $(sha256sum < "$OUT/$STEP-$1.png" | cut -c1-16)"
}
fail() {
  dump; echo "FAIL: $*"; echo "visible: $(texts)"; shot failure
  echo "app log:"; adb logcat -d -s ReactNativeJS:V ReactNative:W | tail -40
  exit 1
}
# wait_for <regex> <seconds> [dismiss-regex]: polls the screen; taps anything matching dismiss-regex meanwhile.
wait_for() {
  local deadline=$((SECONDS + $2)) pos
  while [ "$SECONDS" -lt "$deadline" ]; do
    dump
    pos=$(center_of "$1")
    if [ -n "$pos" ]; then echo "$pos"; return 0; fi
    if [ -n "${3:-}" ]; then
      pos=$(center_of "$3")
      [ -n "$pos" ] && adb shell input tap $pos
    fi
    sleep 2
  done
  return 1
}
tap() { local pos; pos=$(wait_for "$1" "${2:-60}") || fail "not found: $1"; adb shell input tap $pos; }
absent() { dump; if [ -n "$(center_of "$1")" ]; then fail "unexpectedly visible: $1"; fi; }
relaunch() {
  adb shell am force-stop "$PKG"
  adb shell am start -W -n "$PKG/.MainActivity" >/dev/null
}

# Typing right after the tap can lose the first keystrokes (native run 13 typed "ustomer-a"): wait for focus, check
# the field's value and retype once after clearing it.
type_into() {
  local attempt
  for attempt in 1 2; do
    tap "$1"; sleep 2
    adb shell input keyevent KEYCODE_MOVE_END
    for _ in $(seq 1 40); do adb shell input keyevent KEYCODE_DEL; done
    adb shell input text "$2"; sleep 1
    dump
    if grep -q "text=\"$2\"" "$UI"; then return 0; fi
    echo "field value not confirmed on attempt $attempt"
  done
  fail "could not type into $1"
}

CHROME_NOISE='text="(Use without an account|Accept & continue|No thanks|No, thanks|Got it|Continue|Not now)"'
USERNAME_FIELD='resource-id="username"|hint="Username or email"|text="Username or email"'
login_as() {
  wait_for "$USERNAME_FIELD" 120 "$CHROME_NOISE" >/dev/null || fail "login form did not appear (fresh login was not requested?)"
  shot "login-form-$1"
  type_into "$USERNAME_FIELD" "$1"
  tap 'resource-id="password"|hint="Password"|text="Password"'; sleep 2; adb shell input text "$USER_PASSWORD"
  tap 'resource-id="kc-login"|text="Sign In"'
}

echo "== 1. customer-a signs in through the system browser"
relaunch
tap 'text="Sign in"' 60
login_as customer-a
wait_for 'text="Hello [^"]*SyntheticA"' 90 >/dev/null || fail "customer-a not signed in"
shot signed-in-a
relaunch
wait_for 'text="Hello [^"]*SyntheticA"' 60 >/dev/null || fail "session not restored from secure storage after restart"
echo "ok: signed in as customer-a; session restored after restart"

echo "== 2. sign-out removes the session"
tap 'text="Sign out"'
wait_for 'text="Sign in"' 60 >/dev/null || fail "not signed out"
relaunch
wait_for 'text="Sign in"' 60 >/dev/null || fail "app not signed out after restart"
absent 'SyntheticA'
shot signed-out-after-restart
echo "ok: after sign-out and restart the app is signed out and shows nothing of customer-a"

echo "== 3. next sign-in demands a fresh login; customer-b signs in"
tap 'text="Sign in"'
login_as customer-b
wait_for 'text="Hello [^"]*SyntheticB"' 90 >/dev/null || fail "customer-b not signed in"
absent 'SyntheticA'
shot signed-in-b
echo "ok: login form shown again; signed in as customer-b; nothing of customer-a visible"

echo "== 4. switch account also demands a fresh login; cancelling leaves the app signed out"
tap 'text="Switch account"'
wait_for "$USERNAME_FIELD" 120 "$CHROME_NOISE" >/dev/null || fail "switch account did not show the login form"
shot switch-login-form
adb shell input keyevent 4
wait_for 'text="Sign in"' 60 >/dev/null || fail "app not signed out after cancelled switch"
absent 'SyntheticB'
relaunch
wait_for 'text="Sign in"' 60 >/dev/null || fail "app not signed out after cancelled switch and restart"
absent 'SyntheticB'
shot cancelled-switch
if adb logcat -d | grep -E "FATAL EXCEPTION" ; then fail "fatal exception during the round trip"; fi
echo "PASS: Android system-browser PKCE round trip; secure-storage restore; sign-out and account switch leave no previous-user session or data"
