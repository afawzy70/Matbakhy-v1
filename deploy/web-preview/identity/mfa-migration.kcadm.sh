#!/bin/sh
# D18 MFA migration of an EXISTING Keycloak realm (the preview realm). Runs where kcadm.sh is available: inside the Keycloak
# container (deploy/web-preview/migrate-mfa.sh wraps it) or against a throwaway local Keycloak for rehearsal.
#
#   mfa-migration.kcadm.sh status      read-only: what is already in place
#   mfa-migration.kcadm.sh apply       idempotent: flows, authentication levels, mappers, admin service account
#   mfa-migration.kcadm.sh rollback    rebinds the previous browser flow and removes what apply created (see below)
#
# Environment: ADMIN_USER, ADMIN_PASSWORD (a master-realm admin), ADMIN_CLIENT_SECRET (apply: secret of matbakhy-admin),
# REALM (default matbakhy), KCADM (default /opt/keycloak/bin/kcadm.sh), KC_SERVER (default http://localhost:8080),
# PREVIOUS_FLOW (rollback: flow to bind again, default the one recorded by apply or "browser"), PURGE=1 (rollback: also remove
# the matbakhy-admin client and the amr mappers / acr map). Secrets are read from the environment only and never printed.
# Existing users, credentials, sessions, clients and the built-in "browser" flow are not modified or deleted.
set -eu

REALM="${REALM:-matbakhy}"
KCADM="${KCADM:-/opt/keycloak/bin/kcadm.sh}"
SERVER="${KC_SERVER:-http://localhost:8080}"
CFG="${KCADM_CONFIG:-/tmp/kcadm-mfa.$$}"
TOP="matbakhy browser"
FORMS="matbakhy browser forms"
L1="matbakhy level 1"
L2="matbakhy level 2"
LOA_MAP='{"mfa":"2"}'
ADMIN_CLIENT="matbakhy-admin"

die() { echo "mfa-migration: $*" >&2; exit 1; }
kc() { "$KCADM" "$@" --config "$CFG"; }
enc() { printf '%s' "$1" | sed 's/ /%20/g'; }
cleanup() { if [ "${KCADM_REMOTE:-0}" = 1 ]; then "$KCADM" cleanup "$CFG" >/dev/null 2>&1 || true; else rm -f "$CFG"; fi; }
trap cleanup EXIT
# Request bodies that hold a secret are sent on standard input, never as command-line arguments.
kc_body() { # "$@" = kcadm arguments; the JSON body comes from standard input
  "$KCADM" "$@" -f - --config "$CFG"
}

login() {
  [ -n "${ADMIN_USER:-}" ] && [ -n "${ADMIN_PASSWORD:-}" ] || die "ADMIN_USER and ADMIN_PASSWORD are required"
  if [ "${KCADM_REMOTE:-0}" = 1 ]; then
    # The in-container wrapper signs in with ADMIN_USER / ADMIN_PASSWORD taken from its own environment (not from arguments).
    "$KCADM" config credentials --config "$CFG" >/dev/null 2>&1 || die "cannot sign in to the master realm (wrong admin credentials or Keycloak not ready)"
  else
    "$KCADM" config credentials --config "$CFG" --server "$SERVER" --realm master --user "$ADMIN_USER" --password "$ADMIN_PASSWORD" >/dev/null 2>&1 \
      || die "cannot sign in to the master realm (wrong admin credentials or Keycloak not ready)"
  fi
  kc get "realms/$REALM" --fields realm >/dev/null 2>&1 || die "realm $REALM does not exist"
}

flow_id() { kc get authentication/flows -r "$REALM" --fields id,alias --format csv --noquotes | awk -F, -v a="$1" '$2 == a { print $1; exit }'; }
flow_exists() { [ -n "$(flow_id "$1")" ]; }
bound_flow() { kc get "realms/$REALM" --fields browserFlow --format csv --noquotes; }
client_id() { kc get clients -r "$REALM" -q "clientId=$1" --fields id --format csv --noquotes | head -1; }

# GET authentication/flows lists top-level flows only, so the sub flows are looked up through their parents.
child_flow_exists() { [ -n "$(kc get "authentication/flows/$(enc "$1")/executions" -r "$REALM" --fields id,displayName --format csv --noquotes | awk -F, -v p="$2" '$2 == p { print $1; exit }')" ]; }

# Complete = the whole tree exists and the password and OTP executions carry their configuration.
flow_complete() {
  flow_exists "$TOP" && child_flow_exists "$TOP" "$FORMS" && child_flow_exists "$FORMS" "$L1" && child_flow_exists "$FORMS" "$L2" || return 1
  kc get "authentication/flows/$(enc "$L2")/executions" -r "$REALM" --fields providerId,authenticationConfig --format csv --noquotes \
    | grep -Eq '^auth-otp-form,[0-9a-f-]{36}$' || return 1
  kc get "authentication/flows/$(enc "$L1")/executions" -r "$REALM" --fields providerId,authenticationConfig --format csv --noquotes \
    | grep -Eq '^auth-username-password-form,[0-9a-f-]{36}$' || return 1
}

# Deleting the top-level flow removes its sub flows with it.
delete_flows() {
  id="$(flow_id "$TOP")"
  if [ -n "$id" ]; then kc delete "authentication/flows/$id" -r "$REALM" >/dev/null; fi
}

set_requirement() { # flow alias, execution id, requirement
  kc update "authentication/flows/$(enc "$1")/executions" -r "$REALM" -b "{\"id\":\"$2\",\"requirement\":\"$3\"}" >/dev/null
}

exec_id_by_provider() { # flow alias, provider id
  kc get "authentication/flows/$(enc "$1")/executions" -r "$REALM" --fields id,providerId --format csv --noquotes | awk -F, -v p="$2" '$2 == p { print $1; exit }'
}

exec_id_by_flow() { # parent alias, child alias
  kc get "authentication/flows/$(enc "$1")/executions" -r "$REALM" --fields id,displayName --format csv --noquotes | awk -F, -v p="$2" '$2 == p { print $1; exit }'
}

add_config() { # execution id, alias, key=value pairs...
  eid="$1"; calias="$2"; shift 2
  body="{\"alias\":\"$calias\",\"config\":{"
  sep=""
  for kv in "$@"; do body="$body$sep\"${kv%%=*}\":\"${kv#*=}\""; sep=","; done
  kc create "authentication/executions/$eid/config" -r "$REALM" -b "$body}}" >/dev/null
}

build_flows() {
  kc create authentication/flows -r "$REALM" -s alias="$TOP" -s providerId=basic-flow -s topLevel=true -s builtIn=false \
    -s description="Browser flow with level-of-authentication step-up: level 1 password; level 2 adds OTP when the client asks for acr mfa/2" >/dev/null
  kc create "authentication/flows/$(enc "$TOP")/executions/execution" -r "$REALM" -s provider=auth-cookie >/dev/null
  kc create "authentication/flows/$(enc "$TOP")/executions/flow" -r "$REALM" -s alias="$FORMS" -s type=basic-flow >/dev/null
  kc create "authentication/flows/$(enc "$FORMS")/executions/flow" -r "$REALM" -s alias="$L1" -s type=basic-flow >/dev/null
  kc create "authentication/flows/$(enc "$FORMS")/executions/flow" -r "$REALM" -s alias="$L2" -s type=basic-flow >/dev/null
  kc create "authentication/flows/$(enc "$L1")/executions/execution" -r "$REALM" -s provider=conditional-level-of-authentication >/dev/null
  kc create "authentication/flows/$(enc "$L1")/executions/execution" -r "$REALM" -s provider=auth-username-password-form >/dev/null
  kc create "authentication/flows/$(enc "$L2")/executions/execution" -r "$REALM" -s provider=conditional-level-of-authentication >/dev/null
  kc create "authentication/flows/$(enc "$L2")/executions/execution" -r "$REALM" -s provider=auth-otp-form >/dev/null

  set_requirement "$TOP" "$(exec_id_by_provider "$TOP" auth-cookie)" ALTERNATIVE
  set_requirement "$TOP" "$(exec_id_by_flow "$TOP" "$FORMS")" ALTERNATIVE
  set_requirement "$FORMS" "$(exec_id_by_flow "$FORMS" "$L1")" CONDITIONAL
  set_requirement "$FORMS" "$(exec_id_by_flow "$FORMS" "$L2")" CONDITIONAL
  c1="$(exec_id_by_provider "$L1" conditional-level-of-authentication)"; p1="$(exec_id_by_provider "$L1" auth-username-password-form)"
  c2="$(exec_id_by_provider "$L2" conditional-level-of-authentication)"; o2="$(exec_id_by_provider "$L2" auth-otp-form)"
  set_requirement "$L1" "$c1" REQUIRED; set_requirement "$L1" "$p1" REQUIRED
  set_requirement "$L2" "$c2" REQUIRED; set_requirement "$L2" "$o2" REQUIRED
  add_config "$c1" matbakhy-loa-1 loa-condition-level=1 loa-max-age=36000 loa-store-in-user-session=true
  add_config "$c2" matbakhy-loa-2 loa-condition-level=2 loa-max-age=0 loa-store-in-user-session=true
  add_config "$p1" matbakhy-pwd-amr default.reference.value=pwd default.reference.maxAge=0
  add_config "$o2" matbakhy-otp-amr default.reference.value=otp default.reference.maxAge=0
}

set_acr_map() { # realms/<realm> or clients/<id>
  kc update "$1" -r "$REALM" -s "attributes.\"acr.loa.map\"=$(printf '%s' "$LOA_MAP" | sed 's/"/\\"/g; s/^/"/; s/$/"/')" >/dev/null
}

ensure_amr_mapper() { # client uuid
  if ! kc get "clients/$1/protocol-mappers/models" -r "$REALM" --fields name --format csv --noquotes | grep -Fxq amr; then
    kc create "clients/$1/protocol-mappers/models" -r "$REALM" -s name=amr -s protocol=openid-connect -s protocolMapper=oidc-amr-mapper \
      -s 'config."access.token.claim"=true' -s 'config."id.token.claim"=true' >/dev/null
  fi
}

status() {
  login
  echo "realm=$REALM"
  echo "browserFlow=$(bound_flow)"
  if flow_complete; then echo "flows=complete"; elif flow_exists "$TOP"; then echo "flows=incomplete"; else echo "flows=absent"; fi
  for c in matbakhy-web matbakhy-mobile; do
    id="$(client_id $c)"
    if [ -z "$id" ]; then echo "client.$c=absent"; continue; fi
    m=no; kc get "clients/$id/protocol-mappers/models" -r "$REALM" --fields name --format csv --noquotes | grep -Fxq amr && m=yes
    a=no; kc get "clients/$id" -r "$REALM" --fields 'attributes(acr.loa.map)' --format csv --noquotes | grep -q mfa && a=yes
    echo "client.$c=present amrMapper=$m acrMap=$a"
  done
  if [ -n "$(client_id "$ADMIN_CLIENT")" ]; then echo "client.$ADMIN_CLIENT=present"; else echo "client.$ADMIN_CLIENT=absent"; fi
}

apply() {
  [ -n "${ADMIN_CLIENT_SECRET:-}" ] || die "ADMIN_CLIENT_SECRET is required (the value of OIDC_ADMIN_CLIENT_SECRET)"
  login
  prev="$(bound_flow)"
  echo "previousBrowserFlow=$prev"
  if flow_complete; then
    echo "flows: already complete, left as is"
  else
    if flow_exists "$TOP"; then
      [ "$(bound_flow)" != "$TOP" ] || die "the matbakhy flow is bound but incomplete; run rollback first, then apply again"
      echo "flows: removing an incomplete earlier attempt"
      delete_flows
    fi
    build_flows
    flow_complete || die "the flow tree is not complete after building it; the browser flow was NOT changed"
    echo "flows: created and verified"
  fi
  set_acr_map "realms/$REALM"
  for c in matbakhy-web matbakhy-mobile; do
    id="$(client_id $c)"
    [ -n "$id" ] || { echo "client $c not present, skipped"; continue; }
    set_acr_map "clients/$id"
    ensure_amr_mapper "$id"
  done
  aid="$(client_id "$ADMIN_CLIENT")"
  if [ -z "$aid" ]; then
    printf '{"clientId":"%s","protocol":"openid-connect","publicClient":false,"serviceAccountsEnabled":true,"standardFlowEnabled":false,"directAccessGrantsEnabled":false,"implicitFlowEnabled":false,"secret":"%s","description":"Service account used only by the API for the audited MFA reset and to end sessions (never public)"}' \
      "$ADMIN_CLIENT" "$ADMIN_CLIENT_SECRET" | kc_body create clients -r "$REALM" >/dev/null
    echo "client $ADMIN_CLIENT: created"
    echo "createdAdminClient=1"
  else
    printf '{"secret":"%s"}' "$ADMIN_CLIENT_SECRET" | kc_body update "clients/$aid" -r "$REALM" >/dev/null
    echo "client $ADMIN_CLIENT: present, secret set to the configured value"
  fi
  kc add-roles -r "$REALM" --uusername "service-account-$ADMIN_CLIENT" --cclientid realm-management --rolename view-users --rolename manage-users >/dev/null
  # Bind last: nothing changes for users until the flow is complete and its configuration exists.
  if [ "$(bound_flow)" != "$TOP" ]; then
    kc update "realms/$REALM" -s browserFlow="$TOP" >/dev/null
    echo "browserFlow: bound to '$TOP'"
  else
    echo "browserFlow: already '$TOP'"
  fi
}

rollback() {
  login
  target="${PREVIOUS_FLOW:-browser}"
  flow_exists "$target" || die "flow '$target' to bind again does not exist"
  kc update "realms/$REALM" -s browserFlow="$target" >/dev/null
  echo "browserFlow: bound back to '$target'"
  delete_flows
  echo "matbakhy flows: removed"
  if [ "${PURGE:-0}" = "1" ]; then
    aid="$(client_id "$ADMIN_CLIENT")"; [ -z "$aid" ] || { kc delete "clients/$aid" -r "$REALM" >/dev/null; echo "client $ADMIN_CLIENT: removed"; }
    for c in matbakhy-web matbakhy-mobile; do
      id="$(client_id $c)"; [ -n "$id" ] || continue
      mid="$(kc get "clients/$id/protocol-mappers/models" -r "$REALM" --fields id,name --format csv --noquotes | awk -F, '$2 == "amr" { print $1; exit }')"
      [ -z "$mid" ] || { kc delete "clients/$id/protocol-mappers/models/$mid" -r "$REALM" >/dev/null; echo "client $c: amr mapper removed"; }
      kc update "clients/$id" -r "$REALM" -s 'attributes."acr.loa.map"=' >/dev/null
    done
    kc update "realms/$REALM" -s 'attributes."acr.loa.map"=' >/dev/null
  fi
}

# Synthetic verification user (migrate-mfa.sh verify): created from a JSON body on standard input (it carries a random password and a
# random TOTP secret generated for this run), and always removed afterwards. Only usernames starting with "verify-mfa-" are ever deleted.
FIXTURE_PREFIX="verify-mfa-"

fixture_create() {
  body="$(cat)" # read before login: the sign-in call must not consume the request body
  [ -n "$body" ] || die "fixture-create needs the user representation on standard input"
  login
  printf '%s' "$body" | kc_body create users -r "$REALM" -i >/dev/null || die "cannot create the synthetic verification user"
  echo "fixture user: created"
}

fixture_delete() {
  login
  kc get users -r "$REALM" -q "search=$FIXTURE_PREFIX" --fields id,username --format csv --noquotes | while IFS=, read -r id name; do
    case "$name" in "$FIXTURE_PREFIX"*) kc delete "users/$id" -r "$REALM" >/dev/null && echo "fixture user $name: removed" ;; esac
  done
}

case "${1:-}" in
  status) status ;;
  apply) apply ;;
  rollback) rollback ;;
  fixture-create) fixture_create ;;
  fixture-delete) fixture_delete ;;
  *) die "usage: $0 status | apply | rollback | fixture-create | fixture-delete" ;;
esac
