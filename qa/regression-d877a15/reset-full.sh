#!/usr/bin/env bash
# Only the known dedicated IQA project is touched. Preserve previous attempts and private credentials.
set -euo pipefail
cd "$(dirname "$0")/../.."
: "${IQA_ATTEMPT:?set next unique attempt number (e.g. 3)}"
export MATBAKHY_GENERATED_DIR=/workspace/qa-tools/regression-d877a15/stack/generated
if [ -f /workspace/qa-tools/regression-d877a15/stack/.env ]; then
 python3 - <<'PY'
from pathlib import Path
v=dict(l.split('=',1) for l in Path('/workspace/qa-tools/regression-d877a15/stack/.env').read_text().splitlines())
assert v['COMPOSE_PROJECT_NAME']=='matbakhy-regression-d877a15','refuse shared project'
PY
 docker compose --env-file /workspace/qa-tools/regression-d877a15/stack/.env -f infra/compose.yaml -f /workspace/qa-tools/regression-d877a15/stack/override.yaml down --volumes > "/workspace/qa-tools/regression-reset-$IQA_ATTEMPT.log" 2>&1
fi
python3 - <<'PY'
from pathlib import Path
import os
n=os.environ['IQA_ATTEMPT'];r=Path('verification/runs/20261013-IQA-d877a15')
if (r/'evidence').exists(): (r/'evidence').rename(r/('attempt'+str(int(n)-1)))
s=Path('/workspace/qa-tools/regression-d877a15')
if s.exists():s.rename('/workspace/qa-tools/regression-d877a15-attempt'+str(int(n)-1))
PY
python3 qa/regression-d877a15/setup.py
docker compose --env-file /workspace/qa-tools/regression-d877a15/stack/.env -f infra/compose.yaml -f /workspace/qa-tools/regression-d877a15/stack/override.yaml up -d --wait --wait-timeout 300 > "/workspace/qa-tools/regression-stack-$IQA_ATTEMPT.log" 2>&1
python3 qa/regression-d877a15/seed.py
node qa/regression-d877a15/browser-ready.cjs
bash qa/regression-d877a15/run-full.sh
