#!/bin/sh
# Owner E2E fixtures on the shared PREVIEW (owner-approved, non-destructive, never production). Run on the server in deploy/web-preview by a user with Docker:
#   ./owner-e2e-provision.sh plan      read-only: release, existing fixture users, what would be created
#   ./owner-e2e-provision.sh apply     verified backup, Keycloak partial import (existing users are skipped), SQL seeds, slot refresh, read-only reconciliation
# Every step is idempotent and additive: nothing is deleted or overwritten. Passwords are generated into GENERATED_DIR/owner-e2e/<run> (directory 700,
# files 600), never printed, never committed. Do not run it at the same time as admin-console.sh or any other Keycloak/database change.
set -eu
cd "$(dirname "$0")"
ENV_FILE=.env
compose() { docker compose --env-file "$ENV_FILE" -f compose.yaml ${COMPOSE_OVERRIDE:+-f "$COMPOSE_OVERRIDE"} "$@"; }
die() { echo "owner-e2e-provision: $*" >&2; exit 1; }
[ -f "$ENV_FILE" ] || die "$ENV_FILE missing"
set -a; . "./$ENV_FILE"; set +a
: "${PREVIEW_DOMAIN:?}" "${RELEASE_COMMIT:?}"
GENERATED_DIR="${GENERATED_DIR:-/srv/matbakhy-preview/generated}"
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
    python3 ../../scripts/owner_e2e_fixtures.py check || die "fixture manifest and SQL disagree"
    schema="$(compose exec -T postgres psql -U matbakhy -d matbakhy -At -c "SELECT max(version::int) FROM flyway_schema_history WHERE success AND version IS NOT NULL")"
    [ "${schema:-0}" -ge 31 ] || die "schema $schema is older than V31 (MFA policy); deploy the current release first"
    echo "== verified backup (database and storage)"; ./deploy.sh backup
    run="$(date -u +%Y%m%dT%H%M%SZ)"; dir="$GENERATED_DIR/owner-e2e/$run"
    echo "== credentials -> $dir (not printed)"
    (umask 077; mkdir -p "$GENERATED_DIR/owner-e2e"); chmod 700 "$GENERATED_DIR/owner-e2e"
    python3 ../../scripts/owner_e2e_fixtures.py render --out "$dir"
    echo "== Keycloak users (partial import keeps the fixed ids; existing users are skipped)"
    cfg=/tmp/kcadm-e2e.cfg
    ADMIN_USER="$KEYCLOAK_ADMIN_USER" ADMIN_PASSWORD="$KEYCLOAK_ADMIN_PASSWORD" ./kcadm-in-container.sh config credentials --config "$cfg" >/dev/null
    ./kcadm-in-container.sh create partialImport -r matbakhy -f - --config "$cfg" < "$dir/partial-import.json" | sed 's/"value" *: *"[^"]*"/"value":"<hidden>"/g' | head -c 2000 || true
    ./kcadm-in-container.sh cleanup "$cfg"
    echo; echo "== application rows, grants, slots"
    psql_app < seed-owner-e2e.sql
    psql_app < seed-owner-e2e-mfa.sql
    psql_app < refresh-slots.sql
    echo "== read-only reconciliation"
    python3 ../../scripts/owner_e2e_reconcile.py
    echo "done. Passwords: $dir/credentials.private.json (mode 600). MFA policies were not touched."
    ;;
  *) die "usage: ./owner-e2e-provision.sh plan | apply" ;;
esac
