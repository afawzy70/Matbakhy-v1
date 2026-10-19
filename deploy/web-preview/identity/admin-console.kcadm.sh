#!/bin/sh
# Hardening of the Keycloak MASTER realm for the public Preview admin console (owner approval 2026-10-19, Preview only).
# Runs where kcadm.sh is available: inside the Keycloak container (deploy/web-preview/admin-console.sh wraps it) or against a throwaway
# local Keycloak for rehearsal. The matbakhy realm, its users, clients and sessions are never touched.
#
#   admin-console.kcadm.sh status      read-only: what is already in place
#   admin-console.kcadm.sh harden      idempotent: operations client, master frontend URL, brute-force protection, browser flow with a
#                                      REQUIRED one-time password, password grant switched off for every master client
#   admin-console.kcadm.sh rollback    rebinds the previous master browser flow, removes the flow, re-enables the admin-cli password grant
#   admin-console.kcadm.sh fixture-create | fixture-delete   synthetic read-only verification administrators (see below)
#   admin-console.kcadm.sh admin-create | admin-delete NAME   a real administrator whose password and one-time password come from the server
#
# Why a separate operations client: once every administrator needs a one-time password, kcadm can no longer sign in with a user and
# password (and the password grant is exactly the path an Internet attacker would use to skip the second factor). Operator scripts sign
# in as the service account of the confidential master client "matbakhy-ops-cli" instead; its secret stays in the server's .env.
#
# Environment: ADMIN_USER, ADMIN_PASSWORD (first run only, while the password grant still exists), OPS_CLIENT_SECRET (harden),
# PREVIEW_DOMAIN (harden: the master realm frontend URL is https://kc-admin.$PREVIEW_DOMAIN), KCADM (default /opt/keycloak/bin/kcadm.sh),
# KC_SERVER (default http://localhost:8080), PREVIOUS_FLOW (rollback; default "browser"). Secrets are read from the environment or standard
# input only and never printed.
set -eu

REALM=master
KCADM="${KCADM:-/opt/keycloak/bin/kcadm.sh}"
SERVER="${KC_SERVER:-http://localhost:8080}"
CFG="${KCADM_CONFIG:-/tmp/kcadm-admin-console.$$}"
TOP="matbakhy master browser"
FORMS="matbakhy master browser forms"
OPS_CLIENT="matbakhy-ops-cli"
FIXTURE_PREFIX="verify-admin-"

die() { echo "admin-console: $*" >&2; exit 1; }
kc() { "$KCADM" "$@" --config "$CFG"; }
kc_body() { "$KCADM" "$@" -f - --config "$CFG"; }
enc() { printf '%s' "$1" | sed 's/ /%20/g'; }
cleanup() { if [ "${KCADM_REMOTE:-0}" = 1 ]; then "$KCADM" cleanup "$CFG" >/dev/null 2>&1 || true; else rm -f "$CFG"; fi; }
trap cleanup EXIT

login() {
  # The operations client first; the bootstrap administrator's password only while that client cannot sign in yet (first run).
  # kcadm reads the secrets from KC_CLI_CLIENT_SECRET / KC_CLI_PASSWORD, so they never appear on a command line (ps shows container processes).
  if [ "${KCADM_REMOTE:-0}" = 1 ]; then
    "$KCADM" config credentials --config "$CFG" >/dev/null 2>&1 \
      || die "cannot sign in to the master realm (operations client not yet usable and no valid administrator credentials)"
  else
    if [ -n "${OPS_CLIENT_SECRET:-}" ] && KC_CLI_CLIENT_SECRET="$OPS_CLIENT_SECRET" "$KCADM" config credentials --config "$CFG" --server "$SERVER" --realm master --client "$OPS_CLIENT" >/dev/null 2>&1; then
      return 0
    fi
    [ -n "${ADMIN_USER:-}" ] && [ -n "${ADMIN_PASSWORD:-}" ] || die "no usable credentials (OPS_CLIENT_SECRET, or ADMIN_USER and ADMIN_PASSWORD)"
    KC_CLI_PASSWORD="$ADMIN_PASSWORD" "$KCADM" config credentials --config "$CFG" --server "$SERVER" --realm master --user "$ADMIN_USER" >/dev/null 2>&1 \
      || die "cannot sign in to the master realm"
  fi
}

flow_id() { kc get authentication/flows -r "$REALM" --fields id,alias --format csv --noquotes | awk -F, -v a="$1" '$2 == a { print $1; exit }'; }
flow_exists() { [ -n "$(flow_id "$1")" ]; }
bound_flow() { kc get "realms/$REALM" --fields browserFlow --format csv --noquotes; }
client_uuid() { kc get clients -r "$REALM" -q "clientId=$1" --fields id --format csv --noquotes | head -1; }
realm_value() { kc get "realms/$REALM" --fields "$1" --format csv --noquotes; }
enc_exec() { kc get "authentication/flows/$(enc "$1")/executions" -r "$REALM" --fields "$2" --format csv --noquotes; }
exec_id_by_provider() { enc_exec "$1" id,providerId | awk -F, -v p="$2" '$2 == p { print $1; exit }'; }
exec_id_by_flow() { enc_exec "$1" id,displayName | awk -F, -v p="$2" '$2 == p { print $1; exit }'; }
set_requirement() { kc update "authentication/flows/$(enc "$1")/executions" -r "$REALM" -b "{\"id\":\"$2\",\"requirement\":\"$3\"}" >/dev/null; }

# Complete = the tree exists and the password and one-time password executions are both REQUIRED.
flow_complete() {
  flow_exists "$TOP" || return 1
  [ -n "$(exec_id_by_flow "$TOP" "$FORMS")" ] || return 1
  enc_exec "$FORMS" providerId,requirement | grep -Fxq 'auth-username-password-form,REQUIRED' || return 1
  enc_exec "$FORMS" providerId,requirement | grep -Fxq 'auth-otp-form,REQUIRED' || return 1
  enc_exec "$TOP" providerId,requirement | grep -Fxq 'auth-cookie,ALTERNATIVE' || return 1
}

build_flow() {
  kc create authentication/flows -r "$REALM" -s alias="$TOP" -s providerId=basic-flow -s topLevel=true -s builtIn=false \
    -s description="Master realm browser flow: password AND a required one-time password for every administrator (public Preview admin console)" >/dev/null
  kc create "authentication/flows/$(enc "$TOP")/executions/execution" -r "$REALM" -s provider=auth-cookie >/dev/null
  kc create "authentication/flows/$(enc "$TOP")/executions/flow" -r "$REALM" -s alias="$FORMS" -s type=basic-flow >/dev/null
  kc create "authentication/flows/$(enc "$FORMS")/executions/execution" -r "$REALM" -s provider=auth-username-password-form >/dev/null
  kc create "authentication/flows/$(enc "$FORMS")/executions/execution" -r "$REALM" -s provider=auth-otp-form >/dev/null
  set_requirement "$TOP" "$(exec_id_by_provider "$TOP" auth-cookie)" ALTERNATIVE
  set_requirement "$TOP" "$(exec_id_by_flow "$TOP" "$FORMS")" ALTERNATIVE
  set_requirement "$FORMS" "$(exec_id_by_provider "$FORMS" auth-username-password-form)" REQUIRED
  set_requirement "$FORMS" "$(exec_id_by_provider "$FORMS" auth-otp-form)" REQUIRED
}

delete_flow() {
  id="$(flow_id "$TOP")"
  if [ -n "$id" ]; then kc delete "authentication/flows/$id" -r "$REALM" >/dev/null; fi
}

# Clients of the master realm that still accept the password grant (one "clientId" per line).
password_grant_clients() {
  kc get clients -r "$REALM" --fields id,clientId,directAccessGrantsEnabled --format csv --noquotes | awk -F, '$3 == "true" { print $2 }'
}

# One line per master user. Only an ENABLED user with the realm role "admin" and an OTP credential can sign in to the console and manage it
# (an account without an OTP cannot sign in: the public page offers no enrolment; the read-only verification users are not administrators).
admin_accounts() {
  kc get users -r "$REALM" --fields id,username --format csv --noquotes </dev/null | while IFS=, read -r id name; do
    [ -n "$id" ] || continue
    case "$name" in service-account-*) continue ;; esac
    if [ "$(kc get "users/$id" -r "$REALM" --fields enabled --format csv --noquotes </dev/null | head -1)" != true ]; then
      echo "admin.$name=disabled"; continue
    fi
    if ! kc get "users/$id/role-mappings/realm" -r "$REALM" --fields name --format csv --noquotes </dev/null | grep -Fxq admin; then
      echo "admin.$name=not-an-administrator"; continue
    fi
    if kc get "users/$id/credentials" -r "$REALM" --fields type --format csv --noquotes </dev/null | grep -Fxq otp; then
      echo "admin.$name=otp"
    else
      echo "admin.$name=no-otp(cannot-sign-in)"
    fi
  done
}

status() {
  login
  echo "realm=$REALM"
  echo "browserFlow=$(bound_flow)"
  if flow_complete; then echo "flow=complete"; elif flow_exists "$TOP"; then echo "flow=incomplete"; else echo "flow=absent"; fi
  if [ -n "$(client_uuid "$OPS_CLIENT")" ]; then echo "opsClient=present"; else echo "opsClient=absent"; fi
  pg="$(password_grant_clients | tr '\n' ' ' | sed 's/ $//')"
  echo "passwordGrantClients=${pg:-none}"
  echo "frontendUrl=$(kc get "realms/$REALM" --fields 'attributes(frontendUrl)' --format csv --noquotes | head -1)"
  echo "bruteForceProtected=$(realm_value bruteForceProtected)"
  echo "registrationAllowed=$(realm_value registrationAllowed)"
  echo "resetPasswordAllowed=$(realm_value resetPasswordAllowed)"
  accounts="$(admin_accounts)"
  [ -z "$accounts" ] || echo "$accounts"
  echo "signInCapableAdministrators=$(printf '%s\n' "$accounts" | grep -c '=otp$' || true)"
}

harden() {
  [ -n "${OPS_CLIENT_SECRET:-}" ] || die "OPS_CLIENT_SECRET is required"
  [ -n "${PREVIEW_DOMAIN:-}" ] || die "PREVIEW_DOMAIN is required"
  login
  prev="$(bound_flow)"
  echo "previousBrowserFlow=$prev"

  # 1. Operations client first, and proven usable before anything that would lock the administrator's password out of kcadm.
  oid="$(client_uuid "$OPS_CLIENT")"
  if [ -z "$oid" ]; then
    printf '{"clientId":"%s","protocol":"openid-connect","publicClient":false,"serviceAccountsEnabled":true,"standardFlowEnabled":false,"directAccessGrantsEnabled":false,"implicitFlowEnabled":false,"secret":"%s","description":"Operator tooling on the server (kcadm). Never reachable with a password; secret only in the server .env"}' \
      "$OPS_CLIENT" "$OPS_CLIENT_SECRET" | kc_body create clients -r "$REALM" >/dev/null
    echo "client $OPS_CLIENT: created"
  else
    printf '{"secret":"%s"}' "$OPS_CLIENT_SECRET" | kc_body update "clients/$oid" -r "$REALM" >/dev/null
    echo "client $OPS_CLIENT: present, secret set to the configured value"
  fi
  kc add-roles -r "$REALM" --uusername "service-account-$OPS_CLIENT" --rolename admin >/dev/null
  if [ "${KCADM_REMOTE:-0}" = 1 ]; then
    "$KCADM" config credentials-client --config "$CFG.ops" >/dev/null 2>&1 && "$KCADM" cleanup "$CFG.ops" >/dev/null 2>&1 \
      || die "the operations client cannot sign in; the password grant was NOT switched off"
  else
    KC_CLI_CLIENT_SECRET="$OPS_CLIENT_SECRET" "$KCADM" config credentials --config "$CFG.ops" --server "$SERVER" --realm master --client "$OPS_CLIENT" >/dev/null 2>&1 \
      || die "the operations client cannot sign in; the password grant was NOT switched off"
    rm -f "$CFG.ops"
  fi
  echo "client $OPS_CLIENT: sign-in proven"

  # 2. Realm: the master realm lives on the dedicated host (issuer, forms and cookies stay on kc-admin.*, never on the customer host),
  #    failed sign-ins are throttled, and no self-service path exists besides the browser flow.
  kc update "realms/$REALM" -s "attributes.frontendUrl=https://kc-admin.$PREVIEW_DOMAIN" -s bruteForceProtected=true -s permanentLockout=false \
    -s failureFactor=5 -s waitIncrementSeconds=60 -s maxFailureWaitSeconds=900 -s maxDeltaTimeSeconds=43200 -s minimumQuickLoginWaitSeconds=60 \
    -s registrationAllowed=false -s resetPasswordAllowed=false -s rememberMe=false >/dev/null
  echo "realm settings: frontend URL, brute-force protection, no registration or reset-password"
  # The frontend URL changed this realm's token issuer: sign in again so the session below carries the new one.
  login

  # 3. Browser flow with a REQUIRED one-time password; bound only when the tree is verified complete.
  if flow_complete; then
    echo "flow: already complete, left as is"
  else
    if flow_exists "$TOP"; then
      [ "$(bound_flow)" != "$TOP" ] || die "the master flow is bound but incomplete; run rollback first, then harden again"
      echo "flow: removing an incomplete earlier attempt"
      delete_flow
    fi
    build_flow
    flow_complete || die "the flow tree is not complete after building it; the browser flow was NOT changed"
    echo "flow: created and verified"
  fi
  # Drift is read BEFORE anything is changed. When there is any (first run, or something was re-opened since), the completion marker is
  # cleared first and set again only after the sessions and tokens issued before the hardening were revoked, so an interrupted run can never
  # leave password-only sessions behind. A clean re-run (nothing to change) revokes nothing, so the console's users stay signed in.
  changed=0
  [ "$(kc get "realms/$REALM" --fields 'attributes(matbakhyHardened)' --format csv --noquotes </dev/null | head -1)" = yes ] || changed=1
  [ "$(bound_flow)" = "$TOP" ] || changed=1
  [ "$(kc get authentication/required-actions/CONFIGURE_TOTP -r "$REALM" --fields enabled --format csv --noquotes </dev/null | head -1)" = false ] || changed=1
  [ -z "$(password_grant_clients)" ] || changed=1
  overrides="$(kc get clients -r "$REALM" --fields id,authenticationFlowBindingOverrides </dev/null)" || die "cannot read the master clients"
  [ -z "$(printf '%s\n' "$overrides" | awk '/"id" :/ { gsub(/[",]/, "", $3); id = $3 } /"authenticationFlowBindingOverrides" :/ { if ($0 !~ /\{ *\}/) print id }')" ] || changed=1
  if [ "$changed" = 1 ]; then kc update "realms/$REALM" -s attributes.matbakhyHardened=no >/dev/null; fi
  if [ "$(bound_flow)" != "$TOP" ]; then
    kc update "realms/$REALM" -s browserFlow="$TOP" >/dev/null
    echo "browserFlow: bound to '$TOP'"
  else
    echo "browserFlow: already '$TOP'"
  fi

  # 4. Nobody can enrol a one-time password over the Internet: with the required action disabled, an account that has no OTP cannot sign
  #    in at all (so knowing a password is never enough, not even for the first sign-in). Administrators get their OTP from the server
  #    (admin-create), never from the public page.
  kc update authentication/required-actions/CONFIGURE_TOTP -r "$REALM" -s enabled=false -s defaultAction=false >/dev/null
  echo "required action CONFIGURE_TOTP: disabled (no public enrolment)"

  # 5. No master client may issue tokens for a user and password alone (the OTP lives in the browser flow only), and no client may bind
  #    another browser or direct-grant flow that would skip it.
  for c in $(password_grant_clients); do
    cid="$(client_uuid "$c")"
    kc update "clients/$cid" -r "$REALM" -s directAccessGrantsEnabled=false >/dev/null
    echo "client $c: password grant switched off"
  done
  [ -z "$(password_grant_clients)" ] || die "a master client still accepts the password grant"
  # Only clients that have an override are touched (Keycloak refuses to update some built-in clients).
  overrides="$(kc get clients -r "$REALM" --fields id,authenticationFlowBindingOverrides </dev/null)" || die "cannot read the master clients"
  for cid in $(printf '%s\n' "$overrides" | awk '/"id" :/ { gsub(/[",]/, "", $3); id = $3 } /"authenticationFlowBindingOverrides" :/ { if ($0 !~ /\{ *\}/) print id }'); do
    kc update "clients/$cid" -r "$REALM" -s 'authenticationFlowBindingOverrides={}' >/dev/null </dev/null
    echo "client $cid: flow override removed"
  done
  overrides="$(kc get clients -r "$REALM" --fields clientId,authenticationFlowBindingOverrides </dev/null)" || die "cannot read the master clients"
  if printf '%s\n' "$overrides" | grep -Eq '"(browser|direct_grant)"'; then
    die "a master client still overrides the browser or direct-grant flow"
  fi
  echo "master clients: no flow overrides"

  # 6. Sessions and tokens that were issued before the hardening (for example while a rollback had password-only sign-in) are revoked, so a
  #    cookie or refresh token from that time cannot skip the second factor. Only when something actually changed (not on every release).
  if [ "$changed" = 1 ]; then
    kc update "realms/$REALM" -s notBefore="$(date +%s)" >/dev/null
    login # the token used so far predates the new not-before time
    kc create logout-all -r "$REALM" >/dev/null || die "cannot end the sessions that predate the hardening"
    echo "sessions and tokens issued before the hardening: revoked"
    login # ending every session also ended this run's own
  fi
  kc update "realms/$REALM" -s attributes.matbakhyHardened=yes >/dev/null
  echo "hardened"
}

rollback() {
  login
  target="${PREVIOUS_FLOW:-browser}"
  flow_exists "$target" || die "flow '$target' to bind again does not exist"
  kc update "realms/$REALM" -s browserFlow="$target" -s attributes.matbakhyHardened=no >/dev/null
  echo "browserFlow: bound back to '$target'"
  delete_flow
  echo "flow '$TOP': removed"
  kc update authentication/required-actions/CONFIGURE_TOTP -r "$REALM" -s enabled=true >/dev/null
  echo "required action CONFIGURE_TOTP: enabled again (Keycloak default)"
  cid="$(client_uuid admin-cli)"
  [ -z "$cid" ] || { kc update "clients/$cid" -r "$REALM" -s directAccessGrantsEnabled=true >/dev/null; echo "client admin-cli: password grant re-enabled"; }
  if [ "${PURGE:-0}" = "1" ]; then
    # The frontend URL goes first and the operations client last: the client's own token stops working once it is deleted.
    kc update "realms/$REALM" -s 'attributes.frontendUrl=' >/dev/null
    echo "frontend URL cleared"
    login
    oid="$(client_uuid "$OPS_CLIENT")"
    [ -z "$oid" ] || { kc delete "clients/$oid" -r "$REALM" >/dev/null; echo "client $OPS_CLIENT: removed"; }
  fi
}

# Synthetic verification administrators (admin-console.sh verify): users with a random password (one of them also a random TOTP secret) and READ-ONLY
# rights on the matbakhy realm, created from JSON bodies (one per line) on standard input for one run, and always removed afterwards. Only names
# starting "verify-admin-" are removed.
fixture_create() {
  bodies="$(cat)" # read before login: the sign-in call must not consume the request body
  [ -n "$bodies" ] || die "fixture-create needs the user representations on standard input"
  login
  while IFS= read -r body; do
    [ -n "$body" ] || continue
    name="$(printf '%s' "$body" | sed -n 's/.*"username": *"\(verify-admin-[0-9a-f]*\)".*/\1/p' | head -1)"
    [ -n "$name" ] || die "the fixture username must start with $FIXTURE_PREFIX"
    printf '%s' "$body" | kc_body create users -r "$REALM" -i >/dev/null || die "cannot create the synthetic verification administrator"
    kc add-roles -r "$REALM" --uusername "$name" --cclientid matbakhy-realm \
      --rolename view-realm --rolename view-users --rolename view-clients --rolename query-users --rolename query-clients --rolename query-groups >/dev/null </dev/null \
      || die "cannot give the verification administrator its read-only roles"
    # The console's first call (whoami) needs some right on the realm the administrator signs in to; view-realm is read-only.
    kc add-roles -r "$REALM" --uusername "$name" --cclientid master-realm --rolename view-realm >/dev/null </dev/null \
      || die "cannot give the verification administrator its read-only sign-in role"
  done <<BODIES
$bodies
BODIES
  echo "fixture administrators: created"
}

# A real administrator for a person (admin-console.sh create-admin): password and TOTP are generated on the server and handed over in a private file;
# the account is created with both credentials, so no enrolment ever happens on the public page. Refuses an existing name.
admin_create() {
  body="$(cat)"
  [ -n "$body" ] || die "admin-create needs the user representation on standard input"
  name="$(printf '%s' "$body" | sed -n 's/.*"username": *"\([a-z][a-z0-9-]*\)".*/\1/p' | head -1)"
  case "$name" in ''|"$FIXTURE_PREFIX"*) die "invalid administrator name" ;; esac
  login
  if [ -n "$(kc get users -r "$REALM" -q "username=$name" -q exact=true --fields id --format csv --noquotes </dev/null | head -1)" ]; then
    die "the administrator '$name' already exists (use REPLACE=1 ./admin-console.sh create-admin $name to replace it)"
  fi
  printf '%s' "$body" | kc_body create users -r "$REALM" -i >/dev/null || die "cannot create the administrator"
  if ! kc add-roles -r "$REALM" --uusername "$name" --rolename admin >/dev/null </dev/null; then
    uid="$(kc get users -r "$REALM" -q "username=$name" -q exact=true --fields id --format csv --noquotes </dev/null | head -1)"
    [ -z "$uid" ] || kc delete "users/$uid" -r "$REALM" </dev/null >/dev/null || true
    die "cannot give the administrator the admin role; the partly created account was removed"
  fi
  echo "administrator '$name': created with a password and a one-time password (both from the private file)"
}

admin_delete() { # one administrator by exact name (REPLACE=1); never the fixtures
  name="$1"
  case "$name" in ''|"$FIXTURE_PREFIX"*) die "invalid administrator name" ;; esac
  login
  id="$(kc get users -r "$REALM" -q "username=$name" -q exact=true --fields id --format csv --noquotes </dev/null | head -1)"
  [ -z "$id" ] || { kc delete "users/$id" -r "$REALM" </dev/null >/dev/null; echo "administrator '$name': removed"; }
}

fixture_delete() {
  login
  rows="$(kc get users -r "$REALM" -q "search=$FIXTURE_PREFIX" --fields id,username --format csv --noquotes)" || die "cannot list users for the fixture cleanup"
  failed=0
  while IFS=, read -r id name; do
    [ -n "$id" ] || continue
    case "$name" in
      "$FIXTURE_PREFIX"*)
        if kc delete "users/$id" -r "$REALM" </dev/null >/dev/null; then echo "fixture administrator $name: removed"; else echo "fixture administrator $name: NOT removed" >&2; failed=1; fi ;;
    esac
  done <<ROWS
$rows
ROWS
  [ "$failed" -eq 0 ] || die "fixture cleanup incomplete: users named $FIXTURE_PREFIX* remain"
}

case "${1:-}" in
  status) status ;;
  harden) harden ;;
  rollback) rollback ;;
  fixture-create) fixture_create ;;
  fixture-delete) fixture_delete ;;
  admin-create) admin_create ;;
  admin-delete) admin_delete "${2:-}" ;;
  *) die "usage: $0 status | harden | rollback | fixture-create | fixture-delete | admin-create | admin-delete NAME" ;;
esac
