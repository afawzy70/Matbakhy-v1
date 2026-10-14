#!/usr/bin/env bash
# Runs named backend test classes (plus the build they need) and fails unless every named class
# actually ran at least one test with no failures or errors. A renamed or filtered-out class is a
# failure, never a silent pass. Usage: scripts/check_backend_suite.sh ClassA ClassB ...
set -euo pipefail
[ "$#" -gt 0 ] || { echo "usage: $0 TestClass..." >&2; exit 2; }
root="$(cd "$(dirname "$0")/.." && pwd)"
cd "$root/backend"
classes="$(IFS=,; echo "$*")"
find . -path '*/target/surefire-reports' -type d -prune -exec rm -rf {} +
./mvnw -B -ntp verify -Dtest="$classes" -Dsurefire.failIfNoSpecifiedTests=false
status=0
for c in "$@"; do
  report="$(find . -path '*/target/surefire-reports/*' -name "TEST-*.${c}.xml" | head -1)"
  if [ -z "$report" ]; then
    echo "FAIL: $c did not run" >&2; status=1; continue
  fi
  head="$(grep -m1 -o '<testsuite [^>]*>' "$report")"
  tests="$(sed -E 's/.* tests="([0-9]+)".*/\1/' <<<"$head")"
  bad="$(sed -E 's/.* failures="([0-9]+)".*/\1/' <<<"$head")$(sed -E 's/.* errors="([0-9]+)".*/\1/' <<<"$head")"
  if [ "$tests" -lt 1 ] || [ "$bad" != "00" ]; then
    echo "FAIL: $c tests=$tests failures/errors=$bad" >&2; status=1
  else
    echo "PASS: $c ($tests tests)"
  fi
done
exit $status
