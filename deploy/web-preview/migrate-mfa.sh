#!/bin/sh
# D18 migration of the EXISTING preview Keycloak realm (config-as-code in identity/mfa-migration.kcadm.sh).
# `--import-realm` skips a realm that already exists, so the new browser flow, authentication levels, amr mappers and the
# admin service account are applied here, idempotently, with a backup first and a rollback.
#
#   ./migrate-mfa.sh backup     verified database backup (deploy.sh backup) + realm export (no users) + state file
#   ./migrate-mfa.sh status     read-only: what is already in place (safe at any time)
#   ./migrate-mfa.sh apply      backup, then apply, then verify; binds the new browser flow LAST
#   ./migrate-mfa.sh verify     checks from this host: normal sign-in has no OTP, step-up asks for OTP/enrolment, the redirect URIs
#                               are registered, the admin console stays unpublished, the admin client authenticates
#   ./migrate-mfa.sh rollback   rebinds the previous browser flow and removes the flows (PURGE=1 also removes the admin client/mappers)
#
# Needs: the server's deploy/web-preview directory with .env, Docker. Nothing here touches users, credentials, sessions or data.
# The Keycloak admin console is never exposed: all calls go through `docker compose exec` into the container.
set -eu
cd "$(dirname "$0")"
ENV_FILE=.env
compose() { docker compose --env-file "$ENV_FILE" -f compose.yaml ${COMPOSE_OVERRIDE:+-f "$COMPOSE_OVERRIDE"} "$@"; }
die() { echo "migrate-mfa: $*" >&2; exit 1; }
[ -f "$ENV_FILE" ] || die "$ENV_FILE missing (run from the server's deploy/web-preview directory)"
set -a; . "./$ENV_FILE"; set +a
: "${PREVIEW_DOMAIN:?PREVIEW_DOMAIN missing in .env}" "${KEYCLOAK_ADMIN_USER:?}" "${KEYCLOAK_ADMIN_PASSWORD:?}"
BACKUP_DIR="${BACKUP_DIR:-/srv/matbakhy-backups}"
KCDIR="$BACKUP_DIR/keycloak"
STATE="$KCDIR/mfa-migration-state.env"
MIGRATION=identity/mfa-migration.kcadm.sh
. ./identity/mfa-state.sh
REALM=matbakhy

run_migration() { # $1 = status | apply | rollback
  ADMIN_USER="$KEYCLOAK_ADMIN_USER" ADMIN_PASSWORD="$KEYCLOAK_ADMIN_PASSWORD" ADMIN_CLIENT_SECRET="${OIDC_ADMIN_CLIENT_SECRET:-}" \
  REALM="$REALM" KCADM=./kcadm-in-container.sh KCADM_REMOTE=1 KCADM_CONFIG=/tmp/kcadm-mfa.cfg ENV_FILE="$ENV_FILE" \
  PREVIOUS_FLOW="${PREVIOUS_FLOW:-}" PURGE="${PURGE:-0}" sh "$MIGRATION" "$1"
}

backup() {
  umask 077
  mkdir -p "$KCDIR"
  stamp="$(date -u +%Y%m%dT%H%M%SZ)"
  echo "== verified database backup (PostgreSQL dump restored into a scratch database, storage archive)"
  ./deploy.sh backup
  echo "== Keycloak database dump (identity store, includes users and credentials; keep private)"
  compose exec -T postgres pg_dump -U "${POSTGRES_USER:-matbakhy}" -Fc keycloak > "$KCDIR/keycloak-db-$stamp.dump"
  [ -s "$KCDIR/keycloak-db-$stamp.dump" ] || die "empty Keycloak database dump"
  echo "== realm export without users (flows, clients, mappers, roles)"
  ADMIN_USER="$KEYCLOAK_ADMIN_USER" ADMIN_PASSWORD="$KEYCLOAK_ADMIN_PASSWORD" KCADM=./kcadm-in-container.sh KCADM_REMOTE=1 ENV_FILE="$ENV_FILE" \
    sh -c '"$KCADM" config credentials --config /tmp/kcadm-mfa.cfg >/dev/null && "$KCADM" create partial-export -r matbakhy -s exportClients=true -s exportGroupsAndRoles=true --config /tmp/kcadm-mfa.cfg -o; "$KCADM" cleanup /tmp/kcadm-mfa.cfg' \
    > "$KCDIR/realm-before-$stamp.json" 2>/dev/null
  [ -s "$KCDIR/realm-before-$stamp.json" ] || die "empty realm export"
  prev="$(run_migration status 2>/dev/null | sed -n 's/^browserFlow=//p')"
  [ -n "$prev" ] || die "cannot read the currently bound browser flow"
  # The rollback baseline is the flow bound BEFORE the first apply. A repeated apply (when the matbakhy flow is already bound) keeps it.
  if [ "$prev" = "matbakhy browser" ] && [ -f "$STATE" ]; then
    old="$(state_flow "$STATE" 2>/dev/null || true)"
    [ -z "$old" ] || prev="$old"
  fi
  state_write "$STATE" "$stamp" "$prev" || die "cannot write the migration state (unexpected flow name or stamp)"
  echo "backup written under $KCDIR (state: $STATE)"
}

verify() {
  # Shell + python3 (stdlib) from this host through the public proxy, like a browser; no secret is printed.
  # A synthetic user with a random password and TOTP secret is created for the run and always removed (fixture-delete only ever
  # removes names starting "verify-mfa-"); with it the check obtains real tokens and calls the real API. Without it the assurance part
  # is reported NOT_RUN and the exit code is 3, never PASS.
  fxdir="$(mktemp -d)"
  chmod 700 "$fxdir"
  fx_cleanup() {
    cleanup_rc=0
    run_migration fixture-delete >/dev/null 2>&1 || { cleanup_rc=1; echo "WARNING: synthetic users named verify-mfa-* may remain; run ./migrate-mfa.sh fixture-clean" >&2; }
    rm -rf "$fxdir"
    return "$cleanup_rc"
  }
  trap fx_cleanup EXIT
  trap 'exit 130' INT TERM
  fixture=""
  if python3 identity/verify-mfa-flow.py fixture-body "$fxdir" | run_migration fixture-create >/dev/null; then
    fixture="$fxdir/fixture.json"
  else
    echo "WARNING: the synthetic verification user could not be created; assurance checks will be NOT_RUN" >&2
  fi
  rc=0
  PREVIEW_DOMAIN="$PREVIEW_DOMAIN" PREVIEW_USER_PASSWORD="${PREVIEW_USER_PASSWORD:-}" OIDC_ADMIN_CLIENT_SECRET="${OIDC_ADMIN_CLIENT_SECRET:-}" \
    OIDC_WEB_CLIENT_SECRET="${OIDC_WEB_CLIENT_SECRET:-}" VERIFY_FIXTURE_FILE="$fixture" python3 identity/verify-mfa-flow.py || rc=$?
  trap - EXIT INT TERM
  fx_cleanup || { [ "$rc" -ne 0 ] || rc=1; }
  return "$rc"
}

case "${1:-}" in
  backup) backup ;;
  status) run_migration status ;;
  apply)
    [ -n "${OIDC_ADMIN_CLIENT_SECRET:-}" ] || die "OIDC_ADMIN_CLIENT_SECRET missing in .env (run ./deploy.sh render once; it adds it)"
    backup
    run_migration apply
    echo "== verification"
    rc=0
    verify || rc=$?
    [ "$rc" -eq 0 ] || [ "$rc" -eq 3 ] || die "verification FAILED (exit $rc): consider ./migrate-mfa.sh rollback"
    if [ "$rc" -eq 3 ]; then
      echo "WARNING: applied, but authentication assurance is NOT proven (verification incomplete). Do not treat this as PASS." >&2
      exit 3
    fi
    ;;
  verify) verify ;;
  fixture-clean) run_migration fixture-delete ;;
  rollback)
    # The state file is parsed as data (never sourced); an explicit PREVIOUS_FLOW from the operator wins, an unreadable file is refused.
    if [ -z "${PREVIOUS_FLOW:-}" ]; then
      [ -f "$STATE" ] || die "no state file $STATE: pass PREVIOUS_FLOW='<flow name>' explicitly (the built-in flow is 'browser')"
      PREVIOUS_FLOW="$(state_flow "$STATE")" || die "state file $STATE is not valid; pass PREVIOUS_FLOW explicitly"
    fi
    PREVIOUS_FLOW="$PREVIOUS_FLOW" run_migration rollback
    echo "Rolled back the browser flow. A full restore of the identity store, if ever needed, uses the dump named in $STATE (pg_restore into an empty keycloak database while identity is stopped)."
    ;;
  *) die "usage: ./migrate-mfa.sh backup | status | apply | verify | rollback | fixture-clean" ;;
esac
