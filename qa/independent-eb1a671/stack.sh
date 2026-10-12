#!/usr/bin/env bash
# Only the independent QA project's resources. No global Docker reset/cleanup.
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
STATE=/workspace/qa-tools/stack
APP=eb1a6717b3304d3a02e6a46d06c77ed5d43ac144
compose() {
  rg -q '^MATBAKHY_ENV_MARKER=local$' "$STATE/.env"
  rg -q '^COMPOSE_PROJECT_NAME=matbakhy-iqa-eb1a671$' "$STATE/.env"
  MATBAKHY_GENERATED_DIR="$STATE/generated" BUILDX_CONFIG=/workspace/qa-tools/buildx \
    docker compose --env-file "$STATE/.env" -f "$ROOT/infra/compose.yaml" -f "$STATE/override.yaml" "$@"
}
case "${1:-}" in
  up)
    # Refuse altered product input; all QA additions are outside product paths.
    git -C "$ROOT" diff --exit-code "$APP" -- backend apps packages contracts infra
    test -f "$STATE/.env" || python3 "$ROOT/qa/independent-eb1a671/setup.py"
    umask 022
    (cd "$ROOT/backend" && ./mvnw -B -ntp -DskipTests package)
    chmod 644 "$ROOT/backend/app/target/matbakhy-app-0.1.0-SNAPSHOT.jar"
    compose up --build --detach --wait --wait-timeout 180
    curl -fsS http://localhost:28080/actuator/health
    ;;
  seed) python3 "$ROOT/qa/independent-eb1a671/seed.py" ;;
  web) cd "$ROOT/apps/customer-web"; VITE_API_BASE_URL=http://localhost:28080 exec pnpm exec vite --host 127.0.0.1 --port 25173 --strictPort ;;
  test)
    cd "$ROOT"
    # Expected known FAIL is not hidden; continue to collect supplementary evidence.
    rc=0
    node qa/independent-eb1a671/run.cjs || rc=$?
    node qa/independent-eb1a671/reproduce.cjs
    python3 qa/independent-eb1a671/review_results.py
    exit "$rc"
    ;;
  down) compose down --volumes ;;
  *) echo 'Usage: stack.sh up|seed|web|test|down'; exit 2 ;;
esac
