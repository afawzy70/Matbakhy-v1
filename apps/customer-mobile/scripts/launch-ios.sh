#!/usr/bin/env bash
# MB-031 native launch evidence (CI only): boots an iOS simulator, installs the unsigned Release simulator build (JS
# bundle embedded), launches it and checks that the app process stays alive with no crash report.
set -euo pipefail
APP="${1:?app path}"
BUNDLE=com.matbakhy.customer.dev
UDID=$(xcrun simctl list devices available -j | python3 -c "
import json,sys
d=json.load(sys.stdin)['devices']
c=[x for rt,ds in d.items() if 'iOS' in rt for x in ds if x['name'].startswith('iPhone')]
print(c[-1]['udid'] if c else '')")
[ -n "$UDID" ] || { echo "no iPhone simulator available"; exit 1; }
xcrun simctl list devices available | grep "$UDID"
xcrun simctl boot "$UDID" || true
xcrun simctl bootstatus "$UDID" -b
xcrun simctl install "$UDID" "$APP"
before=$(ls ~/Library/Logs/DiagnosticReports 2>/dev/null | wc -l)
xcrun simctl launch "$UDID" "$BUNDLE"
sleep 25
xcrun simctl io "$UDID" screenshot "$RUNNER_TEMP/ios-launch.png"
shasum -a 256 "$RUNNER_TEMP/ios-launch.png"
RUNNING=$(xcrun simctl spawn "$UDID" launchctl list | grep "$BUNDLE" || true)
echo "launchctl: ${RUNNING:-none}"
after=$(ls ~/Library/Logs/DiagnosticReports 2>/dev/null | wc -l)
if [ "$after" -gt "$before" ] && ls -t ~/Library/Logs/DiagnosticReports | head -3 | grep -i matbakhy; then
  echo "FAIL: crash report after launch"; exit 1
fi
[ -n "$RUNNING" ] || { echo "FAIL: app process not running"; exit 1; }
echo "PASS: iOS release simulator build launched and stayed running; no crash report"
