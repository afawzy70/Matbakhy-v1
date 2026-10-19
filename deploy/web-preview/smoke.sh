#!/bin/sh
# HTTPS smoke test of the web preview from an allow-listed machine:  sh smoke.sh <preview-domain>
# Read-only: no account, order or upload is created. Covers the customer web, the operations web, the API, authentication and the
# boundary of privileged operations. The simulated order and deposit flow is the browser smoke (apps/operations-web/e2e-staging).
#   SMOKE_OPS=skip         do not request ops. (run from a machine outside the staff class)
#   SMOKE_CURL_OPTS=-k     local rehearsal only (internal certificate authority)
set -eu
d="${1:?usage: smoke.sh <preview-domain>}"
opts="${SMOKE_CURL_OPTS:-}"
fail() { echo "FAIL: $*" >&2; exit 1; }
check() { echo "ok: $*"; }
# Retries cover the seconds after a start, when the proxy or the identity provider may still be settling.
get() { curl -fsS --retry 6 --retry-delay 5 --retry-all-errors $opts "$@"; }
code() { curl -s $opts -o /dev/null -w '%{http_code}' "$@"; }
unauthenticated() { c=$(code "$1"); case "$c" in 401|403) ;; *) fail "$1 answered $c without a session (expected 401 or 403)";; esac; }

# Customer web: the app, its security headers and the demo/test notice in the build.
page=$(get "https://app.$d/")
echo "$page" | grep -q '<div id="root">' || fail "app.$d does not serve the web app"
headers=$(curl -sI $opts "https://app.$d/")
for h in 'strict-transport-security' 'x-content-type-options: nosniff' 'x-frame-options: deny'; do
  echo "$headers" | tr 'A-Z' 'a-z' | grep -q "$h" || fail "app.$d lacks the header $h"
done
script=$(echo "$page" | grep -o '/assets/[^"]*\.js' | head -1)
[ -n "$script" ] || fail "no script found in the customer web"
get "https://app.$d$script" | grep -q 'بيئة تجريبية' || fail "the customer web build lacks the demo environment notice"
check "customer web served over HTTPS with security headers and the demo notice"

# Operations web (the staff class; skip when running from outside it).
if [ "${SMOKE_OPS:-}" = skip ]; then
  [ "$(code "https://ops.$d/")" = 403 ] || fail "ops.$d must refuse a client outside the staff class"
  check "ops.$d refuses this machine (staff class only)"
else
  get "https://ops.$d/" | grep -q '<div id="root">' || fail "ops.$d does not serve the web app"
  ops_script=$(get "https://ops.$d/" | grep -o '/assets/[^"]*\.js' | head -1)
  get "https://ops.$d$ops_script" | grep -q 'بيئة تجريبية' || fail "the operations web build lacks the demo environment notice"
  check "operations web served over HTTPS with the demo notice"
fi

# API: public configuration says what environment this is; discovery works.
config=$(get "https://api.$d/api/v1/configuration/public")
echo "$config" | grep -q '"orderingAvailable"' || fail "API-001 not reachable"
echo "$config" | grep -q '"environment":{"name":"PREVIEW","demo":true}' || fail "API-001 does not report the PREVIEW demo environment: $config"
check "API reachable; public configuration reports the PREVIEW demo environment"
get -H 'Accept-Language: en' "https://api.$d/api/v1/meals?limit=5" | grep -q '"items"' || fail "discovery not reachable"
check "discovery served"

# Privileged operations need a session; nothing sensitive is published.
unauthenticated "https://api.$d/api/v1/me"
unauthenticated "https://api.$d/api/v1/ops/cases"
unauthenticated "https://api.$d/api/v1/ops/payments?state=DISPUTED"
unauthenticated "https://api.$d/api/v1/cook/dashboard"
check "profile, staff and kitchen APIs refuse a request without a session"
[ "$(code "https://api.$d/actuator/health")" = 404 ] || fail "actuator must not be published"
[ "$(code "https://api.$d/actuator/prometheus")" = 404 ] || fail "metrics must not be published"
check "actuator and metrics not published"

# Authentication: the matbakhy realm only.
issuer=$(get "https://id.$d/realms/matbakhy/.well-known/openid-configuration" | sed -n 's/.*"issuer":"\([^"]*\)".*/\1/p')
[ "$issuer" = "https://id.$d/realms/matbakhy" ] || fail "identity issuer is '$issuer'"
check "identity issuer https://id.$d/realms/matbakhy"
for p in /admin/ /realms/master /realms/master/protocol/openid-connect/auth; do
  [ "$(code "https://id.$d$p")" = 404 ] || fail "id.$d$p must not be published"
done
check "Keycloak admin console and master realm not published"
# Keycloak admin console on its own host (Preview only; ./admin-console.sh enable opens it). Closed = the whole host answers 404; open = the
# console page is served, Admin REST needs a token, and nothing but the console's paths exists. Either way nothing else is reachable. A host
# whose certificate is still being issued (000) is a warning here, not a failure of the release: `admin-console.sh enable` verifies it for real.
kc_code() { # the first answer other than "no connection", for up to 90 seconds (a new host's certificate is issued on first start)
  i=0
  while :; do
    c=$(code "$1")
    [ "$c" != 000 ] && break
    i=$((i + 1)); [ "$i" -ge 18 ] && break
    sleep 5
  done
  echo "$c"
}
kc_state=$(kc_code "https://kc-admin.$d/admin/master/console/")
case "$kc_state" in
  200)
    [ "$(code "https://kc-admin.$d/admin/realms")" = 401 ] || fail "kc-admin.$d Admin REST must answer 401 without a token"
    check "Keycloak admin console served on kc-admin.$d; Admin REST refuses a request without a token" ;;
  404) check "Keycloak admin console switch is closed (kc-admin.$d answers 404)" ;;
  000) echo "WARN: kc-admin.$d is not reachable yet (certificate pending?); ./deploy.sh admin-console enable verifies it" ;;
  *) fail "kc-admin.$d must serve the console or answer 404, got $kc_state" ;;
esac
if [ "$kc_state" != 000 ]; then
  for p in /realms/matbakhy/.well-known/openid-configuration /health /metrics; do
    [ "$(code "https://kc-admin.$d$p")" = 404 ] || fail "kc-admin.$d$p must not be published"
  done
  check "kc-admin does not serve the matbakhy realm, health or metrics"
fi
loc=$(curl -s -o /dev/null -D - $opts "https://api.$d/oauth2/authorization/keycloak?returnTo=https://app.$d/" | tr -d '\r' | sed -n 's/^[Ll]ocation: //p')
case "$loc" in "https://id.$d/realms/matbakhy/protocol/openid-connect/auth?"*"redirect_uri=https://api.$d/login/oauth2/code/keycloak"*) ;; *) fail "unexpected sign-in redirect: $loc";; esac
check "sign-in redirects to the identity provider and back to the API over HTTPS"
echo "PASS: web preview smoke test for $d"
