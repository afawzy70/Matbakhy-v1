#!/usr/bin/env bash
# Clean seeded stack required; re-executes every applicable automated web/API journey.
set -euo pipefail
cd "$(dirname "$0")/../.."
export PATH=/workspace/qa-tools/node-v24.21.0-linux-x64/bin:/workspace/qa-tools/pnpm/node_modules/.bin:$PATH
RUN=verification/runs/20261013-IQA-d877a15/evidence
[ ! -e "$RUN/results.json" ] || { echo 'Refusing FULL REGRESSION against earlier results; create a fresh isolated DB/seed and archive attempt.' >&2; exit 2; }
COUNT=$(docker compose --env-file /workspace/qa-tools/regression-d877a15/stack/.env -f infra/compose.yaml -f /workspace/qa-tools/regression-d877a15/stack/override.yaml exec -T postgres psql -U matbakhy -d matbakhy -tA -c 'SELECT count(*) FROM customer_order')
[ "$COUNT" = 0 ] || { echo 'Refusing non-clean order fixture for full campaign' >&2; exit 2; }
phase() {
 local label="$1";shift
 if "$@" > "/workspace/qa-tools/regression-full-$label.log" 2>&1; then echo "$label executed";else echo "$label reported failures/blockers; preserved log";fi
}
phase 1 env IQA_EXPANDED=1 node qa/regression-d877a15/run-web.cjs
phase 2 env IQA_CASES=S-DRAFT-CLOSE,S-SUPPORT,C-TRUST,C-STALE,C-RECOVERY,S-FINANCE node qa/regression-d877a15/expanded.cjs
phase 3 env IQA_CASES=C-DELIVERY,S-CONFIG,S-ONBOARD,W-A11Y node qa/regression-d877a15/more.cjs
phase 4 env IQA_CASES=C-CAPACITY-STALE,S-MODERATE node qa/regression-d877a15/final-segments.cjs
phase 5 env IQA_CASES=C-AFTER-ACCEPT,S-SETTINGS node qa/regression-d877a15/remaining.cjs
phase 6 node qa/regression-d877a15/finance-final.cjs
phase 7 node qa/regression-d877a15/finance-synthetic.cjs
phase 8 node qa/regression-d877a15/support-final.cjs
phase 9 node qa/regression-d877a15/security-final.cjs
phase 10 node qa/regression-d877a15/pricing.cjs
phase 11 node qa/regression-d877a15/audit.cjs
phase 12 node qa/regression-d877a15/additional.cjs
phase 13 env IQA_LAYOUT_FULL=1 node qa/regression-d877a15/layout.cjs
python3 qa/regression-d877a15/validate-run.py
