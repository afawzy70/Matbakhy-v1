#!/usr/bin/env bash
# Kane CLI QA pilot: isolated throwaway Matbakhy stack with synthetic data. Local container only.
#   qa-stack.sh up    <mvp-worktree>   start a separate compose project (own DB volume, alternate ports), customer web, seed
#   qa-stack.sh seed  <mvp-worktree>   re-apply the synthetic fixture (known baseline)
#   qa-stack.sh check <mvp-worktree>   fixture sanity check over the public API (setup verification, not a test case)
#   qa-stack.sh down  <mvp-worktree>   stop web, remove containers and the data volume
# Reuses the implementation's scripts/dev.sh (doctor/up) with a QA env file; does not modify the worktree's tracked files.
# Requires Node from <mvp-worktree>/.node-version on PATH, JDK 21, Docker with compose v2.
set -euo pipefail
set +x

HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
CMD="${1:-}"; MVP="${2:-}"
[ -n "$CMD" ] && [ -d "$MVP/scripts" ] || { sed -n '2,8p' "$0"; exit 2; }
MVP="$(cd "$MVP" && pwd)"
STATE="${QA_STATE_DIR:-$HOME/.matbakhy-kane-qa}"   # outside every repository; holds generated local-only secrets
ENV_FILE="$STATE/.env"
export MATBAKHY_ENV_FILE="$ENV_FILE" MATBAKHY_GENERATED_DIR="$STATE/generated"
WEB_PORT="${QA_WEB_PORT:-5173}"

die() { echo "ERROR: $*" >&2; exit 1; }

make_env() {
  mkdir -p "$STATE"; chmod 700 "$STATE"
  [ -f "$ENV_FILE" ] && return
  sed -e "s/^COMPOSE_PROJECT_NAME=.*/COMPOSE_PROJECT_NAME=matbakhy-kane-qa/" \
      -e "s/^POSTGRES_PASSWORD=.*/POSTGRES_PASSWORD=$(openssl rand -hex 16)/" \
      -e "s/^KEYCLOAK_ADMIN_PASSWORD=.*/KEYCLOAK_ADMIN_PASSWORD=$(openssl rand -hex 16)/" \
      -e "s/^OIDC_WEB_CLIENT_SECRET=.*/OIDC_WEB_CLIENT_SECRET=$(openssl rand -hex 24)/" \
      -e "s/^DEV_USER_PASSWORD=.*/DEV_USER_PASSWORD=$(openssl rand -hex 12)/" \
      -e "s/^POSTGRES_PORT=.*/POSTGRES_PORT=25432/" -e "s/^IDENTITY_PORT=.*/IDENTITY_PORT=28180/" \
      -e "s/^OBJECT_STORAGE_PORT=.*/OBJECT_STORAGE_PORT=29090/" -e "s/^MAIL_UI_PORT=.*/MAIL_UI_PORT=28025/" \
      -e "s/^MAIL_SMTP_PORT=.*/MAIL_SMTP_PORT=21025/" -e "s/^API_PORT=.*/API_PORT=28080/" \
      "$MVP/infra/.env.example" > "$ENV_FILE"
  chmod 600 "$ENV_FILE"
}

load_env() { set -a; . "$ENV_FILE"; set +a; }
compose() { docker compose --env-file "$ENV_FILE" -f "$MVP/infra/compose.yaml" "$@"; }
psql_q() { compose exec -T postgres psql -U "$POSTGRES_USER" -d "$POSTGRES_DB" -v ON_ERROR_STOP=1 "$@"; }

seed() {
  load_env
  psql_q -q < "$HERE/fixtures/seed-discovery.sql"
  echo "seeded synthetic fixture"
}

check() {
  load_env
  local api="http://127.0.0.1:${API_PORT}" out
  out="$(curl -fsS "$api/api/v1/meals?zoneId=00000000-0000-4000-8000-00000000a001&limit=50" -H 'Accept-Language: en')" \
    || die "discovery request failed"
  for m in "QA Molokhia" "QA Fatta" "QA Mahshi"; do echo "$out" | grep -q "\"$m\"" || die "fixture meal $m not listed"; done
  echo "$out" | grep -q '"QA Paused Meal"' && die "paused fixture meal is listed"
  echo "fixture check OK (setup verification only)"
}

up() {
  make_env
  (cd "$MVP" && scripts/dev.sh up)
  load_env
  echo "-- starting customer web on http://localhost:${WEB_PORT}"
  (cd "$MVP/apps/customer-web" && VITE_API_BASE_URL="http://localhost:${API_PORT}" nohup pnpm exec vite --port "$WEB_PORT" --strictPort \
     >"$STATE/customer-web.log" 2>&1 & echo $! > "$STATE/customer-web.pid")
  for _ in $(seq 1 60); do curl -fs -o /dev/null "http://localhost:${WEB_PORT}/" && break; sleep 1; done
  curl -fsS -o /dev/null "http://localhost:${WEB_PORT}/" || { tail -30 "$STATE/customer-web.log"; die "customer web did not start"; }
  seed
  check
}

down() {
  [ -f "$STATE/customer-web.pid" ] && kill "$(cat "$STATE/customer-web.pid")" 2>/dev/null || true
  pkill -f "vite --port $WEB_PORT" 2>/dev/null || true
  [ -f "$ENV_FILE" ] && compose down --volumes || true
  rm -rf "$STATE"
}

case "$CMD" in
  up) up ;;
  seed) seed ;;
  check) check ;;
  down) down ;;
  *) sed -n '2,8p' "$0"; exit 2 ;;
esac
