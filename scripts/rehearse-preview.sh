#!/usr/bin/env bash
# LOCAL REHEARSAL of the Hetzner staging stack on THIS machine (owner direction 2026-10-14). It is not a deployment, touches no
# server and no cloud resource, and is not evidence about the Hetzner host: it only shows how the package (deploy/web-preview,
# compose.4gb.yaml) behaves when started here. Synthetic data only; secrets are generated into a temp directory.
#
#   scripts/rehearse-preview.sh build      API image, both web builds, front image (local tags; no registry, no push)
#   scripts/rehearse-preview.sh up         copy the package, create .env, start the 4 GB stack, buckets, seed, status
#   scripts/rehearse-preview.sh smoke      read-only smoke (deploy.sh smoke) and the browser smoke with a simulated deposit order
#   scripts/rehearse-preview.sh load       60 s of read traffic from 10 clients with a backup running at the same time; peak memory
#   scripts/rehearse-preview.sh persist    down (volumes kept), up, API killed: data, buckets and sign-in survive
#   scripts/rehearse-preview.sh rollback   an older API image on the newer schema, then back
#   scripts/rehearse-preview.sh e2e        E2E window: guarded reset to an empty database, the six browser workflows through the fixture
#                                          channel against the https stack, then the catalogue is seeded again
#   scripts/rehearse-preview.sh down       remove the stack and its volumes
#   scripts/rehearse-preview.sh all        down, build, up, smoke, load, persist, rollback, e2e (results in $OUT)
# Needs: Docker, Node (pnpm), Chromium (E2E_CHROMIUM or /opt/pw-browsers), root or REHEARSAL_HOSTS=skip with your own hosts entries for
# app./ops./api./id./media.mb.localhost, and images already on the machine (nothing is pulled except by Docker itself).
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
W="${REHEARSAL_DIR:-/tmp/mb-rehearsal}"
OUT="${REHEARSAL_OUT:-$W/out}"
D="$W/deploy/web-preview"
DOMAIN=mb.localhost
export PATH="/opt/node24/bin:$PATH"
info() { echo "== $*"; }
die() { echo "rehearse: $*" >&2; exit 1; }
mkdir -p "$OUT"

# One compose call for the whole rehearsal: the package's own files, the 4 GB override, the generated .env.
dc() { (cd "$D" && set -a && . ./.env && set +a && docker compose --env-file .env -f compose.yaml -f compose.4gb.yaml "$@"); }
deploy() { (cd "$D" && sh deploy.sh "$@"); }
env_value() { (cd "$D" && set -a && . ./.env && set +a && eval "echo \${$1}"); }
chromium() { echo "${E2E_CHROMIUM:-$(ls -d /opt/pw-browsers/chromium-*/chrome-linux*/chrome 2>/dev/null | head -1)}"; }
healthy_api() {
  for _ in $(seq 1 120); do
    [ "$(docker inspect -f '{{.State.Health.Status}}' "$(dc ps -q api)" 2>/dev/null)" = healthy ] && return 0
    sleep 3
  done
  dc logs --tail 60 api; return 1
}

build() {
  info "API jar and image"
  (cd "$ROOT/backend" && ./mvnw -B -o -q -DskipTests package)
  docker build -q -t mb-rehearsal-api:head -f "$ROOT/backend/Dockerfile" "$ROOT/backend" >/dev/null
  info "web builds (demo notice built in) and front image"
  for app in customer-web operations-web; do
    (cd "$ROOT" && VITE_DEMO_NOTICE=true VITE_API_BASE_URL="https://api.$DOMAIN" pnpm --filter "@matbakhy/$app" build >/dev/null)
  done
  rm -rf "$W/front"; mkdir -p "$W/front"
  cp -r "$ROOT/apps/customer-web/dist" "$W/front/customer-web"; cp -r "$ROOT/apps/operations-web/dist" "$W/front/operations-web"
  cp "$ROOT/deploy/web-preview/Caddyfile" "$W/front/Caddyfile"
  cat > "$W/front/Dockerfile" <<'DOCKER'
# REHEARSAL front image: the contents of deploy/web-preview/front.Dockerfile, built from the host build. Local use only.
FROM caddy:2-alpine
COPY Caddyfile /etc/caddy/Caddyfile
COPY customer-web /srv/customer-web
COPY operations-web /srv/operations-web
DOCKER
  docker build -q -t mb-rehearsal-front:head "$W/front" >/dev/null
}

up() {
  info "package copy and .env in $W"
  rm -rf "$W/deploy" "$W/scripts" "$W/infra"; mkdir -p "$W/deploy" "$W/scripts" "$W/infra/db" "$W/backups" "$W/qa"
  cp -r "$ROOT/deploy/web-preview" "$W/deploy/web-preview"
  cp "$ROOT/scripts/db-backup.sh" "$ROOT/scripts/db-restore.sh" "$W/scripts/"; cp "$ROOT/infra/db/reconcile.sql" "$W/infra/db/"
  (cd "$D" && sh deploy.sh init-secrets >/dev/null)
  python3 - "$D/.env" "$(git -C "$ROOT" rev-parse HEAD)" "$W" <<'PY'
import re, sys, pathlib
path, sha, work = sys.argv[1:4]
p = pathlib.Path(path); s = p.read_text()
def setv(k, v):
    global s
    line = f'{k}="{v}"'
    s = re.sub(rf'^{k}=.*$', line, s, flags=re.M) if re.search(rf'^{k}=', s, re.M) else s + "\n" + line + "\n"
for k, v in {"RELEASE_COMMIT": sha, "API_IMAGE": "mb-rehearsal-api:head", "FRONT_IMAGE": "mb-rehearsal-front:head", "PREVIEW_DOMAIN": "mb.localhost",
             "ACME_EMAIL": "rehearsal@example.test", "ALLOWED_CLIENT_IPS": "127.0.0.1/32 ::1/128 172.16.0.0/12 10.0.0.0/8 192.168.0.0/16",
             "BACKUP_DIR": f"{work}/backups", "GENERATED_DIR": f"{work}/generated", "QA_ARTIFACT_DIR": f"{work}/qa", "COMPOSE_OVERRIDE": "compose.4gb.yaml"}.items():
    setv(k, v)
p.write_text(s)
PY
  if ! grep -q "app.$DOMAIN" /etc/hosts; then
    [ "${REHEARSAL_HOSTS:-}" = skip ] || echo "127.0.0.1 app.$DOMAIN ops.$DOMAIN api.$DOMAIN id.$DOMAIN media.$DOMAIN" >> /etc/hosts
  fi
  deploy render
  info "start (4 GB profile)"
  local t0=$SECONDS
  dc up -d --no-build 2>&1 | tail -3
  healthy_api
  info "stack healthy after $((SECONDS - t0)) s"
  dc exec -T storage sh -c 'printf "s3.bucket.create -name matbakhy-public\ns3.bucket.create -name matbakhy-private\n" | weed shell' >/dev/null 2>&1 || true
  deploy seed >/dev/null
  deploy status | tee "$OUT/status.txt"
}

smoke() {
  info "read-only smoke (deploy.sh smoke)"
  (cd "$D" && set -a && . ./.env && set +a && SMOKE_CURL_OPTS=-k sh smoke.sh "$DOMAIN") 2>&1 | tee "$OUT/smoke.txt"
  info "browser smoke with a simulated deposit order"
  (cd "$ROOT/apps/operations-web" && STAGING_APP_URL="https://app.$DOMAIN" STAGING_OPS_URL="https://ops.$DOMAIN" STAGING_API_URL="https://api.$DOMAIN" \
     STAGING_USER_PASSWORD="$(env_value PREVIEW_USER_PASSWORD)" STAGING_IGNORE_HTTPS_ERRORS=1 E2E_CHROMIUM="$(chromium)" E2E_OUTPUT_DIR="$OUT/browser-smoke" \
     pnpm exec playwright test -c playwright.staging.config.ts) 2>&1 | tee "$OUT/browser-smoke.txt"
}

sample_stats() { # background sampler: name,usage / limit every 3 s into $1
  while true; do docker stats --no-stream --format '{{.Name}},{{.MemUsage}}' >> "$1" 2>/dev/null || true; sleep 3; done
}

load() {
  info "load: 10 clients, 60 s, with a database and storage backup running at the same time"
  : > "$OUT/stats.csv"
  sample_stats "$OUT/stats.csv" & local sampler=$!
  (deploy backup > "$OUT/backup-during-load.txt" 2>&1; echo "backup exit $?" >> "$OUT/backup-during-load.txt") &
  local backup=$!
  python3 "$ROOT/scripts/rehearsal/load.py" "$DOMAIN" 60 10 | tee "$OUT/load.json" || echo "load reported errors" | tee -a "$OUT/load.json"
  wait "$backup" || true
  sleep 4; kill "$sampler" 2>/dev/null || true
  python3 "$ROOT/scripts/rehearsal/peaks.py" "$OUT/stats.csv" | tee "$OUT/peaks.txt"
  info "restarts and out-of-memory kills"
  for c in $(dc ps -q); do docker inspect -f '{{.Name}} oom={{.State.OOMKilled}} restarts={{.RestartCount}}' "$c"; done | sed 's#^/##' | tee "$OUT/oom.txt"
  tail -3 "$OUT/backup-during-load.txt"
}

persist() {
  info "persistence: down (volumes kept), up, then the API container is killed"
  local before after t0
  before=$(dc exec -T postgres psql -U matbakhy -d matbakhy -At -c "SELECT count(*) FROM customer_order")
  dc down 2>&1 | tail -1
  t0=$SECONDS; dc up -d --no-build 2>&1 | tail -1; healthy_api
  info "back after $((SECONDS - t0)) s"
  after=$(dc exec -T postgres psql -U matbakhy -d matbakhy -At -c "SELECT count(*) FROM customer_order")
  echo "orders before=$before after=$after" | tee "$OUT/persist.txt"
  [ "$before" = "$after" ] || die "orders did not survive"
  dc exec -T storage sh -c 'echo s3.bucket.list | weed shell' 2>/dev/null | grep -E "matbakhy-(public|private)" | tee -a "$OUT/persist.txt"
  (cd "$D" && set -a && . ./.env && set +a && SMOKE_CURL_OPTS=-k sh smoke.sh "$DOMAIN") 2>&1 | tail -2 | tee -a "$OUT/persist.txt"
  info "kill the API container; the restart policy must bring it back"
  t0=$SECONDS; docker kill "$(dc ps -q api)" >/dev/null; sleep 5; healthy_api
  echo "api killed and healthy again after $((SECONDS - t0)) s" | tee -a "$OUT/persist.txt"
}

rollback() {
  local old="${ROLLBACK_IMAGE:-matbakhy-preview-api:obs}"
  docker image inspect "$old" >/dev/null 2>&1 || die "older image $old is not on this machine (set ROLLBACK_IMAGE)"
  info "an older API image ($old) on the current schema"
  echo "schema before: $(deploy status | grep 'schema version')" | tee "$OUT/rollback.txt"
  (cd "$D" && set -a && . ./.env && set +a && API_IMAGE="$old" docker compose --env-file .env -f compose.yaml -f compose.4gb.yaml up -d --no-build --force-recreate api 2>&1 | tail -1)
  if healthy_api; then
    echo "older image: healthy" | tee -a "$OUT/rollback.txt"
  else
    echo "older image: NOT healthy" | tee -a "$OUT/rollback.txt"
  fi
  dc logs api 2>&1 | grep -iE "flyway|Schema .* version|migrat" | head -5 | cut -c1-260 | tee -a "$OUT/rollback.txt" || true
  for p in /api/v1/configuration/public "/api/v1/meals?limit=3" /api/v1/zones; do
    echo "older image GET $p -> $(curl -sk -o /dev/null -w '%{http_code}' "https://api.$DOMAIN$p")" | tee -a "$OUT/rollback.txt"
  done
  info "back to the current image"
  dc up -d --no-build --force-recreate api 2>&1 | tail -1; healthy_api
  (cd "$D" && set -a && . ./.env && set +a && SMOKE_CURL_OPTS=-k sh smoke.sh "$DOMAIN") 2>&1 | tail -1 | tee -a "$OUT/rollback.txt"
}

e2e() {
  info "E2E window (rehearsal of the guarded procedure)"
  : > "$D/.e2e-enabled"
  (cd "$D" && set -a && . ./.env && set +a && E2E_RESET_CONFIRM="$DOMAIN" sh deploy.sh e2e-reset) 2>&1 | tee "$OUT/e2e-reset.txt"
  local prefix="docker compose --env-file $D/.env -f $D/compose.yaml -f $D/compose.4gb.yaml exec -T postgres psql -U matbakhy -d matbakhy"
  local password users
  password="$(env_value PREVIEW_USER_PASSWORD)"
  users='{"cook-a":{"username":"preview-cook-1","subject":"7d3f0a52-1c0e-4b8e-9a3e-0000000000a1"},"customer-a":{"username":"preview-customer-1","subject":"7d3f0a52-1c0e-4b8e-9a3e-000000000c01"},"customer-b":{"username":"preview-customer-2","subject":"7d3f0a52-1c0e-4b8e-9a3e-000000000c02"},"ops-a":{"username":"preview-ops-1","subject":"7d3f0a52-1c0e-4b8e-9a3e-0000000000f1"}}'
  local rc=0
  (cd "$ROOT/apps/operations-web" && E2E_API_URL="https://api.$DOMAIN" E2E_OPS_URL="https://ops.$DOMAIN" E2E_CUSTOMER_URL="https://app.$DOMAIN" \
     E2E_USER_PASSWORD="$password" E2E_USERS="$users" E2E_IGNORE_HTTPS_ERRORS=1 E2E_CHROMIUM="$(chromium)" E2E_OUTPUT_DIR="$OUT/e2e-results" \
     E2E_FIXTURE_CMD="[\"sh\",\"$D/e2e-fixture.sh\"]" E2E_PSQL_PREFIX="$prefix" E2E_ENABLED_MARKER="$D/.e2e-enabled" \
     pnpm exec playwright test) 2>&1 | tee "$OUT/e2e.txt" || rc=$?
  info "end of the window: marker removed, catalogue seeded again"
  rm -f "$D/.e2e-enabled"
  deploy seed >/dev/null 2>&1 || echo "seed after the E2E window failed" | tee -a "$OUT/e2e.txt"
  return $rc
}

down() { [ -d "$D" ] && dc down --volumes --remove-orphans 2>&1 | tail -1 || true; }

case "${1:-}" in
  build) build ;;
  up) up ;;
  smoke) smoke ;;
  load) load ;;
  persist) persist ;;
  rollback) rollback ;;
  e2e) e2e ;;
  down) down ;;
  all) down; build; up; smoke; load; persist; rollback; e2e ;;
  *) sed -n '2,19p' "$0"; exit 2 ;;
esac
