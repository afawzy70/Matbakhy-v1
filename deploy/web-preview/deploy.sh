#!/bin/sh
# Operator script for the Matbakhy web preview (run on the server from the copied deploy/web-preview directory).
#   ./deploy.sh init-secrets   create .env from .env.example with generated secrets (refuses to overwrite)
#   ./deploy.sh render         write the realm and storage configuration (with secrets) into GENERATED_DIR only
#   ./deploy.sh up             verify pinned images and secrets, back up the database, pull, start, wait for health
#   ./deploy.sh seed           load the synthetic catalogue and verify the synthetic customers' phones
#   ./deploy.sh smoke          HTTPS smoke test from the server
#   ./deploy.sh backup         PostgreSQL dump (checksum + manifest), verified by a restore into a scratch database, and an image-storage archive
#   ./deploy.sh restore-db F   restore a dump into an empty database and reconcile it (scripts/db-restore.sh)
#   ./deploy.sh prune-qa [--apply]   list (or, with --apply, delete) QA artifacts older than QA_ARTIFACT_RETENTION_DAYS
#   ./deploy.sh status         running images, database schema version, last releases (read-only)
#   ./deploy.sh rollback-plan [N]   what rolling back to the release N entries before the last one needs (prints only; changes nothing)
#   ./deploy.sh admin-console CMD   public Keycloak admin console (Preview only): enable | verify | status | disable | rollback (admin-console.sh)
#   ./deploy.sh e2e-reset      E2E window only: verified backup, then recreate the empty application database (guarded)
# Nothing here builds images, creates cloud resources or changes DNS.
set -eu
cd "$(dirname "$0")"
ENV_FILE=.env
# COMPOSE_OVERRIDE (from .env) adds a sizing file such as compose.4gb.yaml.
compose() { docker compose --env-file "$ENV_FILE" -f compose.yaml ${COMPOSE_OVERRIDE:+-f "$COMPOSE_OVERRIDE"} "$@"; }
die() { echo "deploy: $*" >&2; exit 1; }
load() {
  [ -f "$ENV_FILE" ] || die "$ENV_FILE missing; run ./deploy.sh init-secrets and fill in the owner inputs"
  set -a; . "./$ENV_FILE"; set +a
}

init_secrets() {
  [ -f "$ENV_FILE" ] && die "$ENV_FILE exists; refusing to overwrite"
  hex() { openssl rand -hex 32; }
  b64() { openssl rand -base64 32; }
  umask 077
  sed -e "s|^POSTGRES_PASSWORD=.*|POSTGRES_PASSWORD=$(hex)|" \
      -e "s|^KEYCLOAK_DB_PASSWORD=.*|KEYCLOAK_DB_PASSWORD=$(hex)|" \
      -e "s|^APP_DB_PASSWORD=.*|APP_DB_PASSWORD=$(hex)|" \
      -e "s|^GRAFANA_ADMIN_PASSWORD=.*|GRAFANA_ADMIN_PASSWORD=$(hex)|" \
      -e "s|^KEYCLOAK_ADMIN_PASSWORD=.*|KEYCLOAK_ADMIN_PASSWORD=$(hex)|" \
      -e "s|^OIDC_WEB_CLIENT_SECRET=.*|OIDC_WEB_CLIENT_SECRET=$(hex)|" \
      -e "s|^OIDC_ADMIN_CLIENT_SECRET=.*|OIDC_ADMIN_CLIENT_SECRET=$(hex)|" \
      -e "s|^KEYCLOAK_OPS_CLIENT_SECRET=.*|KEYCLOAK_OPS_CLIENT_SECRET=$(hex)|" \
      -e "s|^PREVIEW_USER_PASSWORD=.*|PREVIEW_USER_PASSWORD=$(openssl rand -hex 10)|" \
      -e "s|^PHONE_CODE_PEPPER=.*|PHONE_CODE_PEPPER=$(hex)|" \
      -e "s|^HANDOVER_HASH_KEY=.*|HANDOVER_HASH_KEY=$(hex)|" \
      -e "s|^ACTIVITY_HASH_KEY=.*|ACTIVITY_HASH_KEY=$(hex)|" \
      -e "s|^HANDOVER_ENCRYPTION_KEY=.*|HANDOVER_ENCRYPTION_KEY=$(b64)|" \
      -e "s|^PUSH_TOKEN_HASH_KEY=.*|PUSH_TOKEN_HASH_KEY=$(hex)|" \
      -e "s|^PUSH_TOKEN_ENCRYPTION_KEY=.*|PUSH_TOKEN_ENCRYPTION_KEY=$(b64)|" \
      -e "s|^STORAGE_ACCESS_KEY=.*|STORAGE_ACCESS_KEY=$(openssl rand -hex 10)|" \
      -e "s|^STORAGE_SECRET_KEY=.*|STORAGE_SECRET_KEY=$(hex)|" \
      .env.example > "$ENV_FILE"
  echo "wrote $ENV_FILE (mode 600); now set RELEASE_COMMIT, API_IMAGE, FRONT_IMAGE, PREVIEW_DOMAIN, ACME_EMAIL, ALLOWED_CLIENT_IPS"
}

check() {
  load
  # Comment lines are skipped: the example's own comments mention the REPLACE_ME marker.
  grep -v '^[[:space:]]*#' "$ENV_FILE" | grep -q 'REPLACE_ME\|REPLACE_WITH_DIGEST\|example.invalid' && die "$ENV_FILE still has placeholders"
  case "$API_IMAGE" in *@sha256:*) ;; *) die "API_IMAGE must be pinned by digest (built at RELEASE_COMMIT)";; esac
  case "$FRONT_IMAGE" in *@sha256:*) ;; *) die "FRONT_IMAGE must be pinned by digest (built at RELEASE_COMMIT)";; esac
  echo "$RELEASE_COMMIT" | grep -Eq '^[0-9a-f]{40}$' || die "RELEASE_COMMIT must be a full commit SHA"
}

render() {
  # Secrets go only into files under GENERATED_DIR (outside Git), readable by the container users.
  # Private before anything secret is written: owner-only directory and a restrictive umask for the files (SECR4-003).
  mkdir -p "$GENERATED_DIR/identity" "$GENERATED_DIR/storage"
  chmod 700 "$GENERATED_DIR"
  umask 077
  # D18: servers initialised before the admin service-account client existed get its secret added once (mode 600 file kept).
  if ! grep -q '^OIDC_ADMIN_CLIENT_SECRET=' "$ENV_FILE"; then
    OIDC_ADMIN_CLIENT_SECRET="$(openssl rand -hex 32)"
    printf 'OIDC_ADMIN_CLIENT_SECRET=%s\n' "$OIDC_ADMIN_CLIENT_SECRET" >> "$ENV_FILE"
  fi
  # Admin console: the master-realm operations client secret is added once to an older .env (same mode 600 file).
  if ! grep -q '^KEYCLOAK_OPS_CLIENT_SECRET=' "$ENV_FILE"; then
    KEYCLOAK_OPS_CLIENT_SECRET="$(openssl rand -hex 32)"
    printf 'KEYCLOAK_OPS_CLIENT_SECRET=%s\n' "$KEYCLOAK_OPS_CLIENT_SECRET" >> "$ENV_FILE"
  fi
  # Switch directory of the public admin console (mounted read-only into the proxy); empty means the console is closed.
  mkdir -p "$GENERATED_DIR/flags"
  chmod 755 "$GENERATED_DIR/flags"
  sed -e "s|\${OIDC_WEB_CLIENT_SECRET}|$OIDC_WEB_CLIENT_SECRET|" -e "s|\${OIDC_ADMIN_CLIENT_SECRET}|$OIDC_ADMIN_CLIENT_SECRET|" -e "s|\${PREVIEW_DOMAIN}|$PREVIEW_DOMAIN|g" \
      -e "s|\${PREVIEW_USER_PASSWORD}|$PREVIEW_USER_PASSWORD|g" identity/matbakhy-realm.preview.json > "$GENERATED_DIR/identity/matbakhy-realm.json"
  sed -e "s|\${STORAGE_ACCESS_KEY}|$STORAGE_ACCESS_KEY|" -e "s|\${STORAGE_SECRET_KEY}|$STORAGE_SECRET_KEY|" \
      storage/s3.json.template > "$GENERATED_DIR/storage/s3.json"
  # The files must be readable by the container users (mounted read-only), but the parent directory is owner-only so no
  # other host user can reach them by path (SECR-004). Docker bind mounts are resolved by the daemon as root.
  chmod 700 "$GENERATED_DIR"
  chmod 755 "$GENERATED_DIR/identity" "$GENERATED_DIR/storage"
  chmod 644 "$GENERATED_DIR/identity/matbakhy-realm.json" "$GENERATED_DIR/storage/s3.json"
}

wait_healthy() {
  i=0
  until [ "$(docker inspect -f '{{.State.Health.Status}}' "$(compose ps -q api)" 2>/dev/null)" = healthy ]; do
    i=$((i + 1)); [ "$i" -gt 90 ] && { compose logs --tail 80 api identity; die "API did not become healthy"; }
    sleep 5
  done
}

# Database schema version (highest applied Flyway migration), 0 when there is no database yet.
schema_version() {
  v=$(compose exec -T postgres psql -U matbakhy -d matbakhy -At \
        -c "SELECT COALESCE(max(version::int), 0) FROM flyway_schema_history WHERE success AND version IS NOT NULL" 2>/dev/null | tr -d '[:space:]') || v=0
  case "$v" in ''|*[!0-9]*) v=0 ;; esac
  echo "$v"
}

# One line per successful deploy: when, which commit and image digests, schema before and after, and the pre-deploy backup.
# It is what rollback-plan reads. It holds no secret.
release_log() { echo "${RELEASE_LOG:-${BACKUP_DIR:-/srv/matbakhy-backups}/releases.log}"; }
record_release() {
  mkdir -p "$(dirname "$(release_log)")"
  printf '%s\t%s\t%s\t%s\t%s\t%s\t%s\n' "$(date -u +%Y-%m-%dT%H:%M:%SZ)" "$RELEASE_COMMIT" "$API_IMAGE" "$FRONT_IMAGE" \
    "$1" "$2" "${3:--}" >> "$(release_log)"
}

up() {
  # One writer at a time on this server (the admin console and the owner E2E provisioning take the same lock).
  mkdir -p "${BACKUP_DIR:-/srv/matbakhy-backups}"
  exec 9>"${BACKUP_DIR:-/srv/matbakhy-backups}/.preview-mutation.lock"
  command -v flock >/dev/null 2>&1 && { flock -n 9 || die "another preview change holds the lock"; }
  check
  render
  LAST_BACKUP_DIR=""
  schema_before=0
  if [ -n "$(compose ps -q postgres 2>/dev/null)" ]; then
    echo "pre-deploy database backup"; backup_db
    schema_before=$(schema_version)
  fi
  # Images are pinned by digest, so only missing ones are pulled (an image already present is identical by definition).
  compose pull --policy missing
  compose up -d
  wait_healthy
  # Buckets are idempotent; the public bucket is readable anonymously (s3.json), the private one is not.
  compose exec -T storage sh -c 'printf "s3.bucket.create -name matbakhy-public\ns3.bucket.create -name matbakhy-private\n" | weed shell' >/dev/null 2>&1 || true
  record_release "$schema_before" "$(schema_version)" "${LAST_BACKUP_DIR:--}"
  echo "up: $RELEASE_COMMIT (schema $schema_before -> $(schema_version))"
}

status() {
  load
  echo "== containers"; compose ps --format 'table {{.Service}}\t{{.Status}}\t{{.Image}}' || true
  echo "== schema version: $(schema_version)"
  echo "== configured: RELEASE_COMMIT=$RELEASE_COMMIT"
  echo "   API_IMAGE=$API_IMAGE"
  echo "   FRONT_IMAGE=$FRONT_IMAGE"
  echo "== last releases ($(release_log))"
  if [ -f "$(release_log)" ]; then tail -n 5 "$(release_log)" | awk -F'\t' '{printf "   %s  %s  schema %s -> %s  backup %s\n", $1, substr($2,1,12), $5, $6, $7}'; else echo "   none recorded"; fi
}

# Read-only. Tells the operator what a rollback to the release N entries before the last one involves. Rolling back
# images is safe without touching data only when the database schema has not moved since that release or every
# migration since then is expand-only (CI enforces the annotation: scripts/check_migration_compat.py).
rollback_plan() {
  load
  n=${1:-1}
  case "$n" in ''|*[!0-9]*|0) die "usage: ./deploy.sh rollback-plan [N]  (N >= 1)" ;; esac
  log=$(release_log)
  [ -f "$log" ] || die "no release log at $log: nothing to roll back to"
  total=$(wc -l < "$log" | tr -d ' ')
  [ "$total" -gt "$n" ] || die "only $total release(s) recorded; cannot go back $n"
  target=$(sed -n "$((total - n))p" "$log")
  later=$(sed -n "$((total - n + 1))p" "$log")
  t_commit=$(printf '%s' "$target" | cut -f2); t_api=$(printf '%s' "$target" | cut -f3); t_front=$(printf '%s' "$target" | cut -f4)
  t_schema=$(printf '%s' "$target" | cut -f6); t_when=$(printf '%s' "$target" | cut -f1)
  first_backup=$(printf '%s' "$later" | cut -f7)
  current=$(schema_version)
  echo "target release: $t_commit (deployed $t_when, schema $t_schema)"
  echo "current schema: $current"
  echo "put these three values into .env:"
  echo "  RELEASE_COMMIT=$t_commit"
  echo "  API_IMAGE=$t_api"
  echo "  FRONT_IMAGE=$t_front"
  if [ "$current" = "$t_schema" ]; then
    echo "verdict: the schema has not moved since that release. Image-only rollback: edit .env, then ./deploy.sh up. Data is kept."
  else
    echo "verdict: the schema moved from $t_schema to $current since that release (migrations V$((t_schema + 1)) to V$current)."
    echo "  Image-only rollback is safe ONLY if every one of those migrations is expand-only (check the release notes; CI refuses a"
    echo "  breaking migration without its rollback-compat annotation). Rehearse on a copy first."
    echo "  Otherwise restore the backup taken before the first deploy that applied them: $first_backup"
    echo "  (./deploy.sh restore-db <dump> restores into matbakhy_restore and reconciles it; data written since that backup is LOST,"
    echo "  and the application database must be pointed at the restored one by the owner)."
  fi
}

# E2E window only (owner decision 2026-10-14). The browser suite expects an empty application database. This wipes ALL
# application data (the identity provider keeps its users). It refuses unless: the host has the e2e marker file, the customer
# hosts are not public, and the operator typed the preview domain as confirmation. A verified backup is taken first.
e2e_reset() {
  load
  marker=${E2E_ENABLED_MARKER:-./.e2e-enabled}
  [ -f "$marker" ] || die "e2e is not enabled on this host (no $marker); create it deliberately for an E2E window"
  case " ${CUSTOMER_ALLOWED_IPS:-} " in *" 0.0.0.0/0 "*|*" ::/0 "*) die "customer hosts are public (CUSTOMER_ALLOWED_IPS): narrow them to the staff list before an E2E reset" ;; esac
  [ "${E2E_RESET_CONFIRM:-}" = "$PREVIEW_DOMAIN" ] || die "set E2E_RESET_CONFIRM=$PREVIEW_DOMAIN to confirm that all application data is wiped"
  echo "pre-reset database backup"; backup_db
  compose stop api
  compose exec -T postgres psql -U matbakhy -d postgres -v ON_ERROR_STOP=1 -c "DROP DATABASE IF EXISTS matbakhy WITH (FORCE)" -c "CREATE DATABASE matbakhy OWNER matbakhy"
  compose up -d api
  wait_healthy
  echo "e2e-reset: empty application database (schema $(schema_version)); run ./deploy.sh seed after the E2E window to restore the catalogue"
}

seed() {
  load
  compose exec -T postgres psql -U matbakhy -d matbakhy -v ON_ERROR_STOP=1 -v issuer="https://id.$PREVIEW_DOMAIN/realms/matbakhy" < seed-preview.sql
}

backup_db() {
  stamp=$(date -u +%Y%m%dT%H%M%SZ)
  LAST_BACKUP_DIR="/backups/db-$stamp"
  compose exec -T -e PGDATABASE=matbakhy -e PGUSER=matbakhy postgres sh -s "/backups/db-$stamp" < ../../scripts/db-backup.sh
  verify_backup "/backups/db-$stamp"
  backup_metric
}

# BR-057 / TC-057: a backup counts only after it has been restored into an empty scratch database and reconciled
# (scripts/db-restore.sh). With set -e a failed restore stops here, so the metric below is never written for it.
verify_backup() {
  dump=$(compose exec -T postgres sh -c "ls $1/*.dump" | tail -1 | tr -d '\r')
  [ -n "$dump" ] || die "no dump in $1"
  compose exec -T postgres dropdb -U matbakhy --if-exists matbakhy_restore_check
  compose exec -T -e PGUSER=matbakhy -e RESTORE_CONFIRM_TARGET=matbakhy_restore_check postgres \
    sh -s "$dump" matbakhy_restore_check /reconcile.sql < ../../scripts/db-restore.sh
  compose exec -T postgres dropdb -U matbakhy matbakhy_restore_check
  echo "backup verified: $dump restored and reconciled"
}

# Only after a verified backup (set -e): the time of the last good backup, for the BackupStale alert (MB-039).
backup_metric() {
  mkdir -p "$BACKUP_DIR/metrics"
  printf '# HELP matbakhy_backup_last_success_timestamp_seconds Last database backup restored and reconciled (deploy.sh).\n# TYPE matbakhy_backup_last_success_timestamp_seconds gauge\nmatbakhy_backup_last_success_timestamp_seconds %s\n' \
    "$(date -u +%s)" > "$BACKUP_DIR/metrics/matbakhy_backup.prom.tmp"
  mv "$BACKUP_DIR/metrics/matbakhy_backup.prom.tmp" "$BACKUP_DIR/metrics/matbakhy_backup.prom"
}

backup() {
  load
  backup_db
  stamp=$(date -u +%Y%m%dT%H%M%SZ)
  # A consistent copy of the image storage needs a short stop of the storage service (uploads pause for a minute).
  compose stop storage
  compose run --rm --no-deps -v "$BACKUP_DIR:/backups" --entrypoint tar storage czf "/backups/storage-$stamp.tgz" -C /data .
  compose start storage
  echo "backups in $BACKUP_DIR"
}

restore_db() {
  load
  [ -n "${1:-}" ] || die "usage: ./deploy.sh restore-db /backups/db-<stamp>/matbakhy-<stamp>.dump"
  compose exec -T -e PGUSER=matbakhy -e RESTORE_CONFIRM_TARGET=matbakhy_restore postgres sh -s "$1" matbakhy_restore /reconcile.sql < ../../scripts/db-restore.sh
}

case "${1:-}" in
  init-secrets) init_secrets ;;
  render) load; render ;;
  up) up ;;
  seed) seed ;;
  smoke) load; sh ./smoke.sh "$PREVIEW_DOMAIN" ;;
  backup) backup ;;
  restore-db) shift; restore_db "${1:-}" ;;
  prune-qa) shift; load; sh ./prune-qa-artifacts.sh "$@" ;;
  status) status ;;
  rollback-plan) shift; rollback_plan "${1:-}" ;;
  e2e-reset) e2e_reset ;;
  admin-console) shift; load; sh ./admin-console.sh "$@" ;;
  *) sed -n '2,16p' "$0"; exit 2 ;;
esac
