#!/usr/bin/env bash
# MB-032 on the iOS simulator (CI only), after auth-roundtrip-ios.sh: seeds synthetic kitchens, then runs the ordering
# UI tests in phases, changing the network between them: a slow proxy holds the order response back while the app is
# terminated (TC-049), the API is stopped for the offline reopen, then restarted for recovery. The database must hold
# exactly one order for the customer, with the total the app shows (TC-132).
set -euo pipefail
HERE="$(cd "$(dirname "$0")/.." && pwd)"
ROOT="$(cd "$HERE/../.." && pwd)"
set -a; . "${RUNNER_TEMP:?}/stack.env"; set +a
PG="$(brew --prefix postgresql@16)/bin"
psql_q() { "$PG/psql" -h localhost -U matbakhy -d matbakhy -tA -v ON_ERROR_STOP=1 "$@"; }
UDID=$(xcrun simctl list devices booted -j | python3 -c "
import json,sys
d=json.load(sys.stdin)['devices']
c=[x for ds in d.values() for x in ds if x['name'].startswith('iPhone')]
print(c[0]['udid'] if c else '')")
export TEST_RUNNER_MB031_USER_PASSWORD="$MB031_USER_PASSWORD"
cd "$HERE/e2e-ios"
[ -d MB031NativeChecks.xcodeproj ] || xcodegen generate --quiet

phase() {
  local name="$1" status
  set +e
  xcodebuild test-without-building -project MB031NativeChecks.xcodeproj -scheme SignInUITests -destination "id=$UDID" \
    -only-testing:"SignInUITests/OrderingTests/$name" -resultBundlePath "$RUNNER_TEMP/mb032-$name.xcresult" 2>&1 \
    | tee "$RUNNER_TEMP/mb032-$name.log" | grep -E "Test Case|error:|MB032-" 
  status=${PIPESTATUS[0]}
  set -e
  if [ "$status" -ne 0 ]; then
    echo "FAIL: $name (xcodebuild exit $status)"
    sed -n '/MB031-HIERARCHY-BEGIN/,/MB031-HIERARCHY-END/p' "$RUNNER_TEMP/mb032-$name.log" | head -120
    exit 1
  fi
}

restart_api() {
  APP_ENV=LOCAL SPRING_PROFILES_ACTIVE=dev DATABASE_URL=jdbc:postgresql://localhost:5432/matbakhy DATABASE_USERNAME=matbakhy \
    OIDC_ISSUER=http://localhost:8180/realms/matbakhy OIDC_WEB_CLIENT_SECRET="$(openssl rand -hex 24)" WEB_POST_LOGIN_REDIRECT=http://localhost:5173/ \
    OBJECT_STORAGE_ENDPOINT=http://localhost:9090 OBJECT_STORAGE_CLIENT_ENDPOINT=http://localhost:9090 \
    nohup java -jar "$(ls "$ROOT"/backend/app/target/*.jar | grep -v plain | head -1)" --server.port="$1" > "$RUNNER_TEMP/api-$1.log" 2>&1 &
  echo $!
}
wait_up() { for _ in $(seq 1 90); do curl -fs "http://localhost:$1/actuator/health" | grep -q '"status":"UP"' && return 0; sleep 2; done; tail -40 "$RUNNER_TEMP/api-$1.log"; return 1; }
customer_orders() { psql_q -c "SELECT count(*) FROM customer_order o JOIN app_user u ON u.id = o.customer_id WHERE u.phone_e164 = '+201005550002'"; }

echo "== seed synthetic kitchens and meals"
psql_q < "$HERE/e2e-native/seed.sql" >/dev/null
psql_q -c "UPDATE app_user SET phone_e164 = '+201005550002', phone_verified_at = now() WHERE display_name LIKE '%SyntheticA%'" >/dev/null

echo "== the API moves to 8090; port 8080 becomes a proxy that holds responses back for 8 seconds"
export JAVA_HOME="${JAVA_HOME_21_arm64:-${JAVA_HOME_21_X64:?}}"; export PATH="$JAVA_HOME/bin:$PATH"
pkill -f 'matbakhy-app-.*\.jar' || true
sleep 3
API=$(restart_api 8090); wait_up 8090
python3 "$HERE/e2e-native/slow_proxy.py" 8080 8090 0 > "$RUNNER_TEMP/proxy.log" 2>&1 &
PROXY=$!
trap 'kill $PROXY $API 2>/dev/null || true' EXIT
sleep 1

echo "== phase A: basket replacement, sign-in, quote; the response is held back while the app is terminated"
# XCUITest cannot signal the script mid-test, so the whole phase runs behind a delay that outlasts the 3 seconds
# between sending the order and terminating the app (the identity provider is not behind the proxy).
kill $PROXY; python3 "$HERE/e2e-native/slow_proxy.py" 8080 8090 4 > "$RUNNER_TEMP/proxy.log" 2>&1 & PROXY=$!
sleep 1
phase testA_ReplacementPromptAndSubmitThenKill
sleep 10
COUNT=$(customer_orders); echo "orders in the database after termination: $COUNT"
[ "$COUNT" = "1" ] || { echo "FAIL: expected exactly one created order, found $COUNT"; exit 1; }

echo "== phase B: API unreachable"
kill $PROXY 2>/dev/null || true
phase testB_ReopenedOfflineSaysUnconfirmed

echo "== phase C: API reachable again"
python3 "$HERE/e2e-native/slow_proxy.py" 8080 8090 0 > "$RUNNER_TEMP/proxy.log" 2>&1 & PROXY=$!
sleep 1
phase testC_BackOnlineRecoversTheOneOrder
COUNT=$(customer_orders)
[ "$COUNT" = "1" ] || { echo "FAIL: duplicate order: $COUNT"; exit 1; }
NUMBER=$(grep -o 'MB032-ORDER-NUMBER [^ ]*' "$RUNNER_TEMP/mb032-testC_BackOnlineRecoversTheOneOrder.log" | head -1 | cut -d' ' -f2)
SHOWN=$(grep -o 'MB032-ORDER-TOTAL .*' "$RUNNER_TEMP/mb032-testC_BackOnlineRecoversTheOneOrder.log" | head -1)
DB=$(psql_q -c "SELECT total_minor FROM customer_order WHERE public_number = '$NUMBER'")
echo "order $NUMBER; stored total_minor $DB; app shows: ${SHOWN:-<not captured>}"
[ "$DB" = "9000" ] || { echo "FAIL: unexpected stored total $DB"; exit 1; }
echo "$SHOWN" | grep -qE 'EGP.90\.00' || { echo "FAIL: app total differs from the stored total"; exit 1; }
echo "PASS: iOS basket replacement asks first (keep/replace); terminated during submit, reopened offline with the order unconfirmed, recovered online as exactly one order with the server's total"
