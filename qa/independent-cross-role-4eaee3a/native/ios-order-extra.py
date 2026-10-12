"""Generate a QA-only copy of the documented ordering harness, extending its live API lifetime."""
from pathlib import Path
import os
root=Path(os.environ['GITHUB_WORKSPACE'])
s=(root/'apps/customer-mobile/scripts/order-roundtrip-ios.sh').read_text()
s=s.replace('HERE="$(cd "$(dirname "$0")/.." && pwd)"','HERE="${GITHUB_WORKSPACE:?}/apps/customer-mobile"')
s+='''
echo "== independent iOS basket/lifecycle/language checks, real API still alive"
xcodebuild test-without-building -project MB031NativeChecks.xcodeproj -scheme SignInUITests -destination "id=$UDID" \\
  -only-testing:SignInUITests/IndependentQATests/testIndependentBasketPersistenceLanguageAndEditing \\
  -resultBundlePath "$RUNNER_TEMP/independent-ios.xcresult" 2>&1 | tee "$RUNNER_TEMP/independent-ios.txt" | grep -E 'Test Case|error:|IQA PASS|IQA FAIL'
[ "$(customer_orders)" = "1" ] || { echo "IQA FAIL: independent navigation changed order count"; exit 1; }
'''
(root/'iqa-order-ios.sh').write_text(s)
