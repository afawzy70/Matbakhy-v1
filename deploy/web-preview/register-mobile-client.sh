#!/bin/sh
# Registers (idempotently) the iOS beta OIDC client `matbakhy-mobile` in the EXISTING preview realm (`--import-realm` skips an existing
# realm, so the entry in identity/matbakhy-realm.preview.json only reaches a fresh install). Public client, authorization code + PKCE S256,
# exactly one redirect URI (matbakhy-beta://auth/callback), the API audience mapper and the amr mapper (D18 step-up). No secret exists.
#
#   ./register-mobile-client.sh status | apply | rollback
#
# Uses the same in-container kcadm wrapper as migrate-mfa.sh (admin password never on a host command line). Takes about a minute.
# Apply only after the D18 realm migration (./migrate-mfa.sh apply) if mobile users should be able to step up.
set -eu
cd "$(dirname "$0")"
ENV_FILE=.env
die() { echo "register-mobile-client: $*" >&2; exit 1; }
[ -f "$ENV_FILE" ] || die "$ENV_FILE missing (run from the server's deploy/web-preview directory)"
set -a; . "./$ENV_FILE"; set +a
: "${KEYCLOAK_ADMIN_USER:?}" "${KEYCLOAK_ADMIN_PASSWORD:?}"
REALM=matbakhy
CLIENT=matbakhy-mobile
REDIRECT="matbakhy-beta://auth/callback"
CFG=/tmp/kcadm-mobile.cfg
export ADMIN_USER="$KEYCLOAK_ADMIN_USER" ADMIN_PASSWORD="$KEYCLOAK_ADMIN_PASSWORD" ENV_FILE
KC=./kcadm-in-container.sh
trap '$KC cleanup "$CFG" >/dev/null 2>&1 || true' EXIT
$KC config credentials --config "$CFG" >/dev/null 2>&1 || die "cannot sign in to the master realm"
id="$($KC get clients -r "$REALM" -q "clientId=$CLIENT" --fields id --format csv --noquotes --config "$CFG" | head -1)"

case "${1:-}" in
  status)
    if [ -z "$id" ]; then echo "client.$CLIENT=absent"; else
      echo "client.$CLIENT=present"
      $KC get "clients/$id" -r "$REALM" --fields redirectUris,publicClient --config "$CFG"
    fi ;;
  apply)
    body="{\"clientId\":\"$CLIENT\",\"name\":\"Matbakhy customer app, iOS beta (public client, PKCE)\",\"protocol\":\"openid-connect\",\"publicClient\":true,\"standardFlowEnabled\":true,\"directAccessGrantsEnabled\":false,\"implicitFlowEnabled\":false,\"redirectUris\":[\"$REDIRECT\"],\"attributes\":{\"pkce.code.challenge.method\":\"S256\",\"acr.loa.map\":\"{\\\"mfa\\\": \\\"2\\\"}\"}}"
    if [ -z "$id" ]; then
      printf '%s' "$body" | $KC create clients -r "$REALM" -f - --config "$CFG" >/dev/null
      id="$($KC get clients -r "$REALM" -q "clientId=$CLIENT" --fields id --format csv --noquotes --config "$CFG" | head -1)"
      echo "client $CLIENT: created"
    else
      printf '%s' "$body" | $KC update "clients/$id" -r "$REALM" -f - --config "$CFG" >/dev/null
      echo "client $CLIENT: present, settings re-applied"
    fi
    names="$($KC get "clients/$id/protocol-mappers/models" -r "$REALM" --fields name --format csv --noquotes --config "$CFG")"
    echo "$names" | grep -Fxq api-audience || $KC create "clients/$id/protocol-mappers/models" -r "$REALM" -s name=api-audience -s protocol=openid-connect \
      -s protocolMapper=oidc-audience-mapper -s 'config."included.custom.audience"=matbakhy-api' -s 'config."access.token.claim"=true' -s 'config."id.token.claim"=false' --config "$CFG" >/dev/null
    echo "$names" | grep -Fxq amr || $KC create "clients/$id/protocol-mappers/models" -r "$REALM" -s name=amr -s protocol=openid-connect \
      -s protocolMapper=oidc-amr-mapper -s 'config."access.token.claim"=true' -s 'config."id.token.claim"=true' --config "$CFG" >/dev/null
    echo "mappers: api-audience and amr in place" ;;
  rollback)
    if [ -n "$id" ]; then $KC delete "clients/$id" -r "$REALM" --config "$CFG" >/dev/null; echo "client $CLIENT: removed"; else echo "client $CLIENT: not present"; fi ;;
  *) die "usage: ./register-mobile-client.sh status | apply | rollback" ;;
esac
