#!/bin/sh
# Operator script for the Matbakhy web preview (run on the server from the copied deploy/web-preview directory).
#   ./deploy.sh init-secrets   create .env from .env.example with generated secrets (refuses to overwrite)
#   ./deploy.sh render         write the realm and storage configuration (with secrets) into GENERATED_DIR only
#   ./deploy.sh up             verify pinned images and secrets, back up the database, pull, start, wait for health
#   ./deploy.sh seed           load the synthetic catalogue and verify the synthetic customers' phones
#   ./deploy.sh smoke          HTTPS smoke test from the server
#   ./deploy.sh backup         PostgreSQL dump (checksum + manifest) and an image-storage archive
#   ./deploy.sh restore-db F   restore a dump into an empty database and reconcile it (scripts/db-restore.sh)
# Nothing here builds images, creates cloud resources or changes DNS.
set -eu
cd "$(dirname "$0")"
ENV_FILE=.env
compose() { docker compose --env-file "$ENV_FILE" -f compose.yaml "$@"; }
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
  grep -q 'REPLACE_ME\|REPLACE_WITH_DIGEST\|example.invalid' "$ENV_FILE" && die "$ENV_FILE still has placeholders"
  case "$API_IMAGE" in *@sha256:*) ;; *) die "API_IMAGE must be pinned by digest (built at RELEASE_COMMIT)";; esac
  case "$FRONT_IMAGE" in *@sha256:*) ;; *) die "FRONT_IMAGE must be pinned by digest (built at RELEASE_COMMIT)";; esac
  echo "$RELEASE_COMMIT" | grep -Eq '^[0-9a-f]{40}$' || die "RELEASE_COMMIT must be a full commit SHA"
}

render() {
  # Secrets go only into files under GENERATED_DIR (outside Git), readable by the container users.
  mkdir -p "$GENERATED_DIR/identity" "$GENERATED_DIR/storage"
  sed -e "s|\${OIDC_WEB_CLIENT_SECRET}|$OIDC_WEB_CLIENT_SECRET|" -e "s|\${PREVIEW_DOMAIN}|$PREVIEW_DOMAIN|g" \
      -e "s|\${PREVIEW_USER_PASSWORD}|$PREVIEW_USER_PASSWORD|g" identity/matbakhy-realm.preview.json > "$GENERATED_DIR/identity/matbakhy-realm.json"
  sed -e "s|\${STORAGE_ACCESS_KEY}|$STORAGE_ACCESS_KEY|" -e "s|\${STORAGE_SECRET_KEY}|$STORAGE_SECRET_KEY|" \
      storage/s3.json.template > "$GENERATED_DIR/storage/s3.json"
  chmod 755 "$GENERATED_DIR" "$GENERATED_DIR/identity" "$GENERATED_DIR/storage"
  chmod 644 "$GENERATED_DIR/identity/matbakhy-realm.json" "$GENERATED_DIR/storage/s3.json"
}

wait_healthy() {
  i=0
  until [ "$(docker inspect -f '{{.State.Health.Status}}' "$(compose ps -q api)" 2>/dev/null)" = healthy ]; do
    i=$((i + 1)); [ "$i" -gt 90 ] && { compose logs --tail 80 api identity; die "API did not become healthy"; }
    sleep 5
  done
}

up() {
  check
  render
  if [ -n "$(compose ps -q postgres 2>/dev/null)" ]; then
    echo "pre-deploy database backup"; backup_db
  fi
  compose pull
  compose up -d
  wait_healthy
  # Buckets are idempotent; the public bucket is readable anonymously (s3.json), the private one is not.
  compose exec -T storage sh -c 'printf "s3.bucket.create -name matbakhy-public\ns3.bucket.create -name matbakhy-private\n" | weed shell' >/dev/null 2>&1 || true
  echo "up: $RELEASE_COMMIT"
}

seed() {
  load
  compose exec -T postgres psql -U matbakhy -d matbakhy -v ON_ERROR_STOP=1 -v issuer="https://id.$PREVIEW_DOMAIN/realms/matbakhy" < seed-preview.sql
}

backup_db() {
  stamp=$(date -u +%Y%m%dT%H%M%SZ)
  compose exec -T -e PGDATABASE=matbakhy -e PGUSER=matbakhy postgres sh -s "/backups/db-$stamp" < ../../scripts/db-backup.sh
  backup_metric
}

# Only after a successful dump (set -e): the time of the last good backup, for the BackupStale alert (MB-039).
backup_metric() {
  mkdir -p "$BACKUP_DIR/metrics"
  printf '# HELP matbakhy_backup_last_success_timestamp_seconds Last successful database backup (deploy.sh).\n# TYPE matbakhy_backup_last_success_timestamp_seconds gauge\nmatbakhy_backup_last_success_timestamp_seconds %s\n' \
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
  *) sed -n '2,11p' "$0"; exit 2 ;;
esac
