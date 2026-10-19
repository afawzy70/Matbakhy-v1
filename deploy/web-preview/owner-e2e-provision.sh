#!/bin/sh
# Owner E2E fixtures on the shared PREVIEW (owner-approved, non-destructive, never production). Run on the server in deploy/web-preview by a user with Docker:
#   ./owner-e2e-provision.sh plan      read-only: release, existing fixture users, what would be created
#   ./owner-e2e-provision.sh apply     verified backup, Keycloak partial import (existing users are skipped), SQL seeds, slot refresh, read-only reconciliation
# Every step is idempotent and additive: nothing is deleted or overwritten. Passwords are generated into GENERATED_DIR/owner-e2e/<run> (directory 700,
# files 600), never printed, never committed. Do not run it at the same time as admin-console.sh or any other Keycloak/database change.
set +x
set -eu
cd "$(dirname "$0")"
ENV_FILE=.env
compose() { docker compose --env-file "$ENV_FILE" -f compose.yaml ${COMPOSE_OVERRIDE:+-f "$COMPOSE_OVERRIDE"} "$@"; }
die() { echo "owner-e2e-provision: $*" >&2; exit 1; }
[ -f "$ENV_FILE" ] || die "$ENV_FILE missing"
set -a; . "./$ENV_FILE"; set +a
: "${PREVIEW_DOMAIN:?}" "${RELEASE_COMMIT:?}"
GENERATED_DIR="${GENERATED_DIR:-/srv/matbakhy-preview/generated}"
BACKUP_DIR="${BACKUP_DIR:-/srv/matbakhy-backups}"
# One writer at a time on this server (deployments and the admin console take the same lock in their own scripts).
LOCK="$BACKUP_DIR/.preview-mutation.lock"
mkdir -p "$BACKUP_DIR"
exec 9>"$LOCK"
command -v flock >/dev/null 2>&1 && { flock -n 9 || die "another preview change holds $LOCK"; }
ISSUER="https://id.$PREVIEW_DOMAIN/realms/matbakhy"
psql_app() { compose exec -T postgres psql -U matbakhy -d matbakhy -v ON_ERROR_STOP=1 -v issuer="$ISSUER" "$@"; }
PSQL_CMD="docker compose --env-file $ENV_FILE -f compose.yaml ${COMPOSE_OVERRIDE:+-f $COMPOSE_OVERRIDE} exec -T postgres psql -U matbakhy -d matbakhy"
export PSQL_CMD
case "${1:-}" in
  plan)
    echo "release $RELEASE_COMMIT (schema $(compose exec -T postgres psql -U matbakhy -d matbakhy -At -c "SELECT max(version::int) FROM flyway_schema_history WHERE success AND version IS NOT NULL"))"
    python3 ../../scripts/owner_e2e_fixtures.py check
    python3 ../../scripts/owner_e2e_fixtures.py plan
    echo "existing application users:"; compose exec -T postgres psql -U matbakhy -d matbakhy -At -c "SELECT display_name, issuer FROM app_user ORDER BY created_at" 2>/dev/null || true
    ;;
  apply)
    # Preview only: the API container must report APP_ENV=PREVIEW and the public configuration must say PREVIEW/demo.
    [ "$(compose exec -T api printenv APP_ENV | tr -d '\r\n')" = PREVIEW ] || die "the API is not APP_ENV=PREVIEW: refusing (never production)"
    python3 ../../scripts/owner_e2e_fixtures.py check || die "fixture manifest and SQL disagree"
    schema="$(compose exec -T postgres psql -U matbakhy -d matbakhy -At -c "SELECT max(version::int) FROM flyway_schema_history WHERE success AND version IS NOT NULL")"
    [ "${schema:-0}" -ge 31 ] || die "schema $schema is older than V31 (MFA policy); deploy the current release first"
    echo "== verified backup (application database and storage) and Keycloak database dump"
    ./deploy.sh backup
    umask 077
    mkdir -p "$BACKUP_DIR/keycloak"
    kcdump="$BACKUP_DIR/keycloak/keycloak-db-e2e-$(date -u +%Y%m%dT%H%M%SZ).dump"
    compose exec -T postgres pg_dump -U "${POSTGRES_USER:-matbakhy}" -Fc keycloak > "$kcdump"
    [ -s "$kcdump" ] || die "empty Keycloak database dump"
    run="$(date -u +%Y%m%dT%H%M%SZ)"; dir="$GENERATED_DIR/owner-e2e/$run"
    echo "== credentials -> $dir (not printed)"
    mkdir -p "$GENERATED_DIR/owner-e2e"; chmod 700 "$GENERATED_DIR/owner-e2e"
    python3 ../../scripts/owner_e2e_fixtures.py render --out "$dir"
    echo "== Keycloak users (partial import keeps the fixed ids; existing users are skipped and keep their passwords)"
    cfg="/tmp/kcadm-e2e.$$.cfg"
    trap './kcadm-in-container.sh cleanup "$cfg" >/dev/null 2>&1 || true' EXIT
    ADMIN_USER="$KEYCLOAK_ADMIN_USER" ADMIN_PASSWORD="$KEYCLOAK_ADMIN_PASSWORD" ./kcadm-in-container.sh config credentials --config "$cfg" >/dev/null \
      || die "cannot sign in to Keycloak (operations client)"
    ./kcadm-in-container.sh create partialImport -r matbakhy -f - --config "$cfg" < "$dir/partial-import.json" > "$dir/import-result.json" 2>"$dir/import-error.txt" \
      || die "the Keycloak import failed (details in $dir/import-error.txt, mode 600; they may name accounts)"
    ./kcadm-in-container.sh get users -r matbakhy --fields id,username -q max=1000 --config "$cfg" > "$dir/users.json" || die "cannot list the Keycloak users"
    python3 ../../scripts/owner_e2e_fixtures.py finalize --out "$dir" --result "$dir/import-result.json" --users "$dir/users.json" \
      || die "a fixture user has a different Keycloak id than the SQL seed expects (an older account with the same username?); nothing was written to PostgreSQL"
    echo; echo "== application rows, grants, slots"
    psql_app < seed-owner-e2e.sql
    psql_app < seed-owner-e2e-mfa.sql
    psql_app < refresh-slots.sql
    echo "== read-only reconciliation"
    python3 ../../scripts/owner_e2e_reconcile.py
    echo "done. Passwords of newly added users: $dir/credentials.private.json (mode 600). MFA policies were not touched."
    ;;
  *) die "usage: ./owner-e2e-provision.sh plan | apply" ;;
esac
