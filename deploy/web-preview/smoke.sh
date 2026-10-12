#!/bin/sh
# HTTPS smoke test of the web preview from an allow-listed machine:  sh smoke.sh <preview-domain>
# Read-only: no account, order or upload is created.
set -eu
d="${1:?usage: smoke.sh <preview-domain>}"
fail() { echo "FAIL: $*" >&2; exit 1; }
check() { echo "ok: $*"; }
for h in app ops; do
  curl -fsS "https://$h.$d/" | grep -q '<div id="root">' || fail "$h.$d does not serve the web app"
  check "$h.$d serves the web app over HTTPS"
done
curl -fsS "https://api.$d/api/v1/configuration/public" | grep -q '"orderingAvailable"' || fail "API-001 not reachable"
check "API reachable; public configuration served"
curl -fsS -H 'Accept-Language: en' "https://api.$d/api/v1/meals?limit=5" | grep -q '"items"' || fail "discovery not reachable"
check "discovery served"
[ "$(curl -s -o /dev/null -w '%{http_code}' "https://api.$d/actuator/health")" = 404 ] || fail "actuator must not be published"
check "actuator not published"
issuer=$(curl -fsS "https://id.$d/realms/matbakhy/.well-known/openid-configuration" | sed -n 's/.*"issuer":"\([^"]*\)".*/\1/p')
[ "$issuer" = "https://id.$d/realms/matbakhy" ] || fail "identity issuer is '$issuer'"
check "identity issuer https://id.$d/realms/matbakhy"
[ "$(curl -s -o /dev/null -w '%{http_code}' "https://id.$d/admin/")" = 404 ] || fail "Keycloak admin console must not be published"
check "identity admin console not published"
loc=$(curl -s -o /dev/null -D - "https://api.$d/oauth2/authorization/keycloak?returnTo=https://app.$d/" | tr -d '\r' | sed -n 's/^[Ll]ocation: //p')
case "$loc" in "https://id.$d/realms/matbakhy/protocol/openid-connect/auth?"*"redirect_uri=https://api.$d/login/oauth2/code/keycloak"*) ;; *) fail "unexpected sign-in redirect: $loc";; esac
check "sign-in redirects to the identity provider and back to the API over HTTPS"
echo "PASS: web preview smoke test for $d"
