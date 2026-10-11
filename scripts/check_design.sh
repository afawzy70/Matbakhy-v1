#!/usr/bin/env bash
# P00-CHECK-2 (MB-003/MB-004): design tokens tests, brand specimen render with Arabic font load
# assertion, and every prototype journey in Arabic/English at phone/desktop widths.
# Optional first argument: output directory to keep screenshots (default: temporary, removed).
# Visual approval itself is recorded separately in docs/design/approval.md and is not checked here.
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
OUT="${1:-}"
if [ -z "$OUT" ]; then OUT="$(mktemp -d)"; trap 'rm -rf "$OUT"' EXIT; fi
OUT="$(cd "$(dirname "$OUT")" && pwd)/$(basename "$OUT")"
cd "$ROOT"
echo "== design token tests"
pnpm --filter @matbakhy/design-tokens test
echo "== brand specimens"
pnpm --filter @matbakhy/design-tokens specimen "$OUT/specimen"
echo "== prototype journeys"
pnpm --filter @matbakhy/design-tokens ux:journeys "$OUT/ux"
grep -q '"status": "PENDING"\|\*\*PENDING\*\*' docs/design/approval.md && echo "visual approval: PENDING (recorded separately)"
echo "PASS: design checks"
