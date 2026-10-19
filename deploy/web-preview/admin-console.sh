#!/bin/sh
# Public Keycloak admin console of the Preview (owner approval 2026-10-19: Preview only, never production).
# The proxy serves https://kc-admin.<preview domain> only while the switch file exists; this script creates the switch ONLY after the master
# realm is hardened and the console has passed a live verification, and removes it again on any failure (fail closed).
#
#   ./admin-console.sh enable     backup, harden the master realm, open the switch, verify through the public URL (closes it again on failure)
#   ./admin-console.sh verify     live checks through the public URL with a synthetic read-only administrator (created for the run, removed after)
#   ./admin-console.sh status     read-only: realm hardening, the switch, and how many users have a one-time password enrolled
#   ./admin-console.sh disable    closes kc-admin.* at once (the realm keeps its hardening)
#   ./admin-console.sh rollback   closes it, rebinds the previous master browser flow, re-enables the admin-cli password grant (PURGE=1 also removes
#                                 the operations client and the master frontend URL)
#   ./admin-console.sh create-admin NAME   a real administrator: password and one-time password generated on the server into a private file
#                                 (mode 600, under generated/admin-console/); the public page never enrols anyone
#   ./admin-console.sh fixture-clean   removes synthetic verify-admin-* users left behind by an interrupted run
#
# Also reachable as ./deploy.sh admin-console <command>. Needs the server's .env and Docker. The matbakhy realm, its users and credentials are
# never changed; the Keycloak database is dumped first. No secret is printed. See docs/operations/14-keycloak-admin-console.md.
set -eu
cd "$(dirname "$0")"
ENV_FILE=.env
compose() { docker compose --env-file "$ENV_FILE" -f compose.yaml ${COMPOSE_OVERRIDE:+-f "$COMPOSE_OVERRIDE"} "$@"; }
die() { echo "admin-console: $*" >&2; exit 1; }
[ -f "$ENV_FILE" ] || die "$ENV_FILE missing (run from the server's deploy/web-preview directory)"
set -a; . "./$ENV_FILE"; set +a
: "${PREVIEW_DOMAIN:?PREVIEW_DOMAIN missing in .env}" "${KEYCLOAK_ADMIN_USER:?}" "${KEYCLOAK_ADMIN_PASSWORD:?}"
GENERATED_DIR="${GENERATED_DIR:-/srv/matbakhy-preview/generated}"
FLAG="$GENERATED_DIR/flags/admin-console-public"
BACKUP_DIR="${BACKUP_DIR:-/srv/matbakhy-backups}"
KCDIR="$BACKUP_DIR/keycloak"
STATE="$KCDIR/admin-console-state.env"
SCRIPT=identity/admin-console.kcadm.sh
. ./identity/mfa-state.sh

ensure_ops_secret() {
  # deploy.sh render adds it to an older .env; do the same here so the script also works on its own. The new file is created private from the
  # start (umask 077, unique name) and replaces .env only when completely written.
  if [ -z "${KEYCLOAK_OPS_CLIENT_SECRET:-}" ] || [ "$KEYCLOAK_OPS_CLIENT_SECRET" = REPLACE_ME ]; then
    secret="$(openssl rand -hex 32)" && [ -n "$secret" ] || die "cannot generate the operations client secret"
    tmp="$(umask 077; mktemp "$ENV_FILE.XXXXXX")" || die "cannot create a private temporary file next to $ENV_FILE"
    trap 'rm -f "$tmp"' EXIT
    { grep -v '^KEYCLOAK_OPS_CLIENT_SECRET=' "$ENV_FILE" || true; printf 'KEYCLOAK_OPS_CLIENT_SECRET=%s\n' "$secret"; } > "$tmp" || die "cannot write $tmp"
    chmod 600 "$tmp" && mv "$tmp" "$ENV_FILE" || die "cannot replace $ENV_FILE"
    trap - EXIT
    KEYCLOAK_OPS_CLIENT_SECRET="$secret"
    export KEYCLOAK_OPS_CLIENT_SECRET
  fi
}

run_kc() { # $1 = status | harden | rollback | fixture-create | fixture-delete | admin-create | admin-delete NAME
  ADMIN_USER="$KEYCLOAK_ADMIN_USER" ADMIN_PASSWORD="$KEYCLOAK_ADMIN_PASSWORD" OPS_CLIENT_SECRET="${KEYCLOAK_OPS_CLIENT_SECRET:-}" \
  PREVIEW_DOMAIN="$PREVIEW_DOMAIN" KCADM=./kcadm-in-container.sh KCADM_REMOTE=1 KCADM_CONFIG=/tmp/kcadm-admin-console.cfg ENV_FILE="$ENV_FILE" \
  PREVIOUS_FLOW="${PREVIOUS_FLOW:-}" PURGE="${PURGE:-0}" sh "$SCRIPT" "$@"
}

open_switch() { mkdir -p "$(dirname "$FLAG")"; : > "$FLAG"; chmod 644 "$FLAG"; }
close_switch() { rm -f "$FLAG"; }

backup() {
  umask 077
  mkdir -p "$KCDIR"
  stamp="$(date -u +%Y%m%dT%H%M%SZ)"
  echo "== Keycloak database dump (identity store; keep private)"
  compose exec -T postgres pg_dump -U "${POSTGRES_USER:-matbakhy}" -Fc keycloak > "$KCDIR/keycloak-db-$stamp.dump"
  [ -s "$KCDIR/keycloak-db-$stamp.dump" ] || die "empty Keycloak database dump"
  prev="$(run_kc status 2>/dev/null | sed -n 's/^browserFlow=//p')"
  [ -n "$prev" ] || die "cannot read the currently bound master browser flow"
  # The rollback baseline is the flow bound BEFORE the first hardening; a repeated enable keeps it.
  if [ "$prev" = "matbakhy master browser" ] && [ -f "$STATE" ]; then
    old="$(state_flow "$STATE" 2>/dev/null || true)"
    [ -z "$old" ] || prev="$old"
  fi
  state_write "$STATE" "$stamp" "$prev" || die "cannot write the state file (unexpected flow name or stamp)"
  echo "backup written under $KCDIR (state: $STATE)"
}

verify() {
  # A synthetic read-only administrator with a random password and TOTP secret exists only for this run (fixture-delete only ever removes
  # names starting "verify-admin-"). Without it the authenticated checks are NOT_RUN and the exit code is 3, never PASS.
  fxdir="$(mktemp -d)"
  chmod 700 "$fxdir"
  fx_cleanup() {
    cleanup_rc=0
    run_kc fixture-delete >/dev/null 2>&1 || { cleanup_rc=1; echo "WARNING: synthetic users named verify-admin-* may remain; run ./admin-console.sh fixture-clean" >&2; }
    rm -rf "$fxdir"
    return "$cleanup_rc"
  }
  trap fx_cleanup EXIT
  trap 'exit 130' INT TERM
  fixture=""
  if python3 identity/verify-admin-console.py fixture-body "$fxdir" | run_kc fixture-create >/dev/null; then
    fixture="$fxdir/fixture.json"
  else
    echo "WARNING: the synthetic verification administrator could not be created; authenticated checks will be NOT_RUN" >&2
  fi
  rc=0
  PREVIEW_DOMAIN="$PREVIEW_DOMAIN" VERIFY_ADMIN_FIXTURE_FILE="$fixture" python3 identity/verify-admin-console.py || rc=$?
  trap - EXIT INT TERM
  fx_cleanup || { [ "$rc" -ne 0 ] || rc=1; }
  return "$rc"
}

case "${1:-}" in
  enable)
    ensure_ops_secret
    close_switch
    backup
    run_kc harden
    # From here until the verification passed, ANY exit (failure, Ctrl-C, SIGTERM, a dropped SSH session) closes the switch again.
    enabled=0
    trap 'rc=$?; [ "$enabled" = 1 ] || close_switch; exit "$rc"' EXIT
    trap 'exit 129' HUP
    trap 'exit 130' INT
    trap 'exit 143' TERM
    open_switch
    echo "== live verification through https://kc-admin.$PREVIEW_DOMAIN"
    rc=0
    (verify) || rc=$?
    if [ "$rc" -ne 0 ]; then
      close_switch
      echo "admin-console: verification did not pass (exit $rc); the console is CLOSED again. Nothing is claimed as working." >&2
      exit "$rc"
    fi
    enabled=1
    echo "OPEN: https://kc-admin.$PREVIEW_DOMAIN (verified)"
    echo "== administrators who can sign in (an account without a one-time password cannot; there is no public enrolment)"
    run_kc status | grep -E '^(admin\.|signInCapableAdministrators=)' || true
    echo "If signInCapableAdministrators=0, create one on this server: ./deploy.sh admin-console create-admin <name>"
    ;;
  verify) verify ;;
  status)
    run_kc status
    if [ -f "$FLAG" ]; then echo "switch=open (kc-admin.$PREVIEW_DOMAIN is served)"; else echo "switch=closed (kc-admin.$PREVIEW_DOMAIN answers 404)"; fi
    ;;
  create-admin)
    name="${2:-}"
    [ -n "$name" ] || die "usage: ./admin-console.sh create-admin <name>   (REPLACE=1 replaces an existing administrator)"
    dir="$GENERATED_DIR/admin-console"
    (umask 077; mkdir -p "$dir"); chmod 700 "$dir"
    if [ "${REPLACE:-0}" = 1 ]; then
      # Replacing deletes the account first (an OTP credential cannot be changed in place), so never do it to the last administrator who can sign in.
      others="$(run_kc status | grep -E '^admin\..*=otp$' | grep -vc "^admin\.$name=" || true)"
      [ "${others:-0}" -ge 1 ] || [ "${ALLOW_LAST_ADMIN:-0}" = 1 ] \
        || die "refusing to replace '$name': no other administrator can sign in (create another first, or ALLOW_LAST_ADMIN=1 knowing the console is unusable until this finishes)"
    fi
    body="$(python3 identity/provision-admin.py new-admin "$dir" "$name")" || die "cannot generate the administrator (an old $dir/$name.private may exist: remove it after checking it is no longer needed, or use REPLACE=1)"
    if [ "${REPLACE:-0}" = 1 ]; then run_kc admin-delete "$name"; fi
    if ! printf '%s' "$body" | run_kc admin-create; then
      rm -f "$dir/$name.private"
      die "the administrator was not created (nothing was kept)"
    fi
    echo "Private file (mode 600): $dir/$name.private - read it on the server, set up the authenticator app with the key, sign in at"
    echo "https://kc-admin.$PREVIEW_DOMAIN, then delete the file. Do not paste its contents into chats, tickets or logs."
    ;;
  disable) close_switch; echo "closed: kc-admin.$PREVIEW_DOMAIN answers 404 again" ;;
  fixture-clean) run_kc fixture-delete ;;
  rollback)
    close_switch
    if [ -z "${PREVIOUS_FLOW:-}" ]; then
      [ -f "$STATE" ] || die "no state file $STATE: pass PREVIOUS_FLOW='<flow name>' explicitly (the built-in flow is 'browser')"
      PREVIOUS_FLOW="$(state_flow "$STATE")" || die "state file $STATE is not valid; pass PREVIOUS_FLOW explicitly"
    fi
    PREVIOUS_FLOW="$PREVIOUS_FLOW" run_kc rollback
    echo "Rolled back and closed. Users and the matbakhy realm were not touched; a full restore uses the dump named in $STATE."
    ;;
  *) die "usage: ./admin-console.sh enable | verify | status | disable | rollback | create-admin <name> | fixture-clean" ;;
esac
