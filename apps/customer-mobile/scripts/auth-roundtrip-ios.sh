#!/usr/bin/env bash
# MB-031 / TC-048 on the iOS simulator (CI only): runs the XCUITest round trip in e2e-ios against the customer app
# already installed and launched by launch-ios.sh, with the identity provider and API from native-stack-macos.sh.
set -euo pipefail
HERE="$(cd "$(dirname "$0")/.." && pwd)"
set -a; . "${RUNNER_TEMP:?}/stack.env"; set +a
UDID=$(xcrun simctl list devices booted -j | python3 -c "
import json,sys
d=json.load(sys.stdin)['devices']
c=[x for ds in d.values() for x in ds if x['name'].startswith('iPhone')]
print(c[0]['udid'] if c else '')")
[ -n "$UDID" ] || { echo "no booted iPhone simulator"; exit 1; }
brew install xcodegen >/dev/null
cd "$HERE/e2e-ios"
xcodegen generate --quiet
# Variables prefixed TEST_RUNNER_ reach the UI-test runner with the prefix removed.
export TEST_RUNNER_MB031_USER_PASSWORD="$MB031_USER_PASSWORD"
set +e
xcodebuild test -project MB031NativeChecks.xcodeproj -scheme SignInUITests -destination "id=$UDID" \
  -resultBundlePath "$RUNNER_TEMP/mb031-ios.xcresult" 2>&1 | tee "$RUNNER_TEMP/mb031-ios-test.log" \
  | grep -E "Test Case|error:|XCTAssert|failed|passed|\*\* TEST" 
status=${PIPESTATUS[0]}
set -e
if [ "$status" -ne 0 ]; then
  echo "FAIL: iOS sign-in round trip (xcodebuild exit $status)"; tail -60 "$RUNNER_TEMP/mb031-ios-test.log"
  echo "--- identity log"; tail -30 "$RUNNER_TEMP/keycloak.log"; echo "--- api log"; tail -30 "$RUNNER_TEMP/api.log"
  exit 1
fi
echo "PASS: iOS system-browser PKCE round trip; secure-storage restore; sign-out and account switch leave no previous-user session or data"
