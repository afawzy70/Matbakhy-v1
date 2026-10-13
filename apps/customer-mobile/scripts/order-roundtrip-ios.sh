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
    | tee "$RUNNER_TEMP/mb032-$name.log" | grep -E "Test Case|error:|MB0[3-5][0-9]-" 
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
# One saved delivery address in the synthetic "Test zone A" for the MB-033 delivery checkout (phase E).
psql_q -v phone=+201005550002 < "$HERE/e2e-native/delivery-address.sql" >/dev/null

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
# No duplicated business side effect either: one stock allocation, one portion reserved on the meal and cook slots,
# one cash obligation, one ORDER_PLACED outbox event and one order event.
SIDE=$(psql_q -v phone=+201005550002 < "$HERE/e2e-native/side-effects.sql")
echo "side effects (orders|allocations|meal-slot reserved|cook-slot reserved|cash obligations|ORDER_PLACED events|order events): $SIDE"
[ "$SIDE" = "1|1|1|1|1|1|1" ] || { echo "FAIL: duplicated or missing side effect: $SIDE"; exit 1; }
NUMBER=$(grep -o 'MB032-ORDER-NUMBER [^ ]*' "$RUNNER_TEMP/mb032-testC_BackOnlineRecoversTheOneOrder.log" | head -1 | cut -d' ' -f2)
SHOWN=$(grep -o 'MB032-ORDER-TOTAL .*' "$RUNNER_TEMP/mb032-testC_BackOnlineRecoversTheOneOrder.log" | head -1)
DB=$(psql_q -c "SELECT total_minor FROM customer_order WHERE public_number = '$NUMBER'")
echo "order $NUMBER; stored total_minor $DB; app shows: ${SHOWN:-<not captured>}"
[ "$DB" = "9000" ] || { echo "FAIL: unexpected stored total $DB"; exit 1; }
echo "$SHOWN" | grep -qE 'EGP.90\.00' || { echo "FAIL: app total differs from the stored total"; exit 1; }

echo "== phase D (MB-033 Q1): the quantity control stops at min(policy limit 10, 15 available portions)"
phase testD_QuantityStopsAtThePolicyLimit
quotes() { psql_q -c "SELECT count(*) FROM quote q JOIN app_user u ON u.id = q.customer_id WHERE u.phone_e164 = '+201005550002'"; }
QUOTES_BEFORE=$(quotes)

echo "== phase E (MB-033 Q1 + delivery): the limit drops to 5; the saved basket must be corrected before a delivery order"
# Test-only change of the synthetic CI policy row; the server reads the active policy on every request.
psql_q -c "UPDATE commerce_policy SET max_item_quantity = 5 WHERE status = 'ACTIVE' AND synthetic" >/dev/null
trap 'kill $PROXY $API 2>/dev/null || true; psql_q -c "UPDATE commerce_policy SET max_item_quantity = 10 WHERE status = '"'"'ACTIVE'"'"' AND synthetic" >/dev/null || true' EXIT
phase testE_LoweredLimitBlocksQuoteThenDeliveryOrder
LOG_E="$RUNNER_TEMP/mb032-testE_LoweredLimitBlocksQuoteThenDeliveryOrder.log"
DNUMBER=$(grep -o 'MB033-DELIVERY-ORDER-NUMBER [^ ]*' "$LOG_E" | head -1 | cut -d' ' -f2)
DSHOWN=$(grep -o 'MB033-DELIVERY-QUOTE-TOTAL .*' "$LOG_E" | head -1)
DFEE=$(grep -o 'MB033-DELIVERY-FEE .*' "$LOG_E" | head -1)
# Exactly one quote was created in phase E: the one after the correction (none while the line was over the limit).
QUOTES_AFTER=$(quotes)
echo "quotes for the customer: $QUOTES_BEFORE before phase E, $QUOTES_AFTER after"
[ "$QUOTES_AFTER" = "$((QUOTES_BEFORE + 1))" ] || { echo "FAIL: expected exactly one new quote (after the correction), found $((QUOTES_AFTER - QUOTES_BEFORE))"; exit 1; }
DROW=$(psql_q -c "SELECT o.fulfillment_mode || '|' || o.delivery_fee_minor || '|' || o.total_minor || '|' || (o.address_snapshot IS NOT NULL) || '|' || o.kitchen_id || '|' || o.state FROM customer_order o WHERE o.public_number = '$DNUMBER'")
echo "delivery order $DNUMBER (mode|fee|total|address snapshot|kitchen|state): $DROW; app showed: $DSHOWN; $DFEE"
case "$DROW" in
  "DELIVERY|3000|63000|true|00000000-0000-4000-8000-00000000a0d1|"*) ;;
  *) echo "FAIL: delivery order not persisted as expected: $DROW"; exit 1 ;;
esac
echo "$DSHOWN" | grep -qE 'EGP.630\.00' || { echo "FAIL: app delivery total differs from the stored total 63000"; exit 1; }
echo "$DFEE" | grep -qE 'EGP.30\.00' || { echo "FAIL: app delivery fee differs from the zone fee 3000"; exit 1; }
psql_q -c "UPDATE commerce_policy SET max_item_quantity = 10 WHERE status = 'ACTIVE' AND synthetic" >/dev/null

echo "== phase F (MB-033): the order link opens the order warm and cold"
export TEST_RUNNER_MB033_ORDER_ID="$(psql_q -c "SELECT id FROM customer_order WHERE public_number = '$DNUMBER'")"
export TEST_RUNNER_MB033_ORDER_NUMBER="$DNUMBER"
phase testF_OrderDeepLinkWarmAndCold

# A phase whose test prints a READY marker and then waits on the API while this script applies a change.
phase_with_change() {
  local name="$1" marker="$2" change="$3" log="$RUNNER_TEMP/mb032-$1.log" status
  : > "$log"
  set +e
  ( xcodebuild test-without-building -project MB031NativeChecks.xcodeproj -scheme SignInUITests -destination "id=$UDID" \
      -only-testing:"SignInUITests/OrderingTests/$name" -resultBundlePath "$RUNNER_TEMP/mb032-$name.xcresult" > "$log" 2>&1 ) &
  local pid=$!
  for _ in $(seq 1 240); do grep -q "$marker" "$log" && break; kill -0 $pid 2>/dev/null || break; sleep 1; done
  grep -q "$marker" "$log" && eval "$change"
  wait $pid; status=$?
  set -e
  grep -E "Test Case|error:|MB03[23]-|MB049-" "$log" || true
  if [ "$status" -ne 0 ]; then
    echo "FAIL: $name (xcodebuild exit $status)"
    sed -n '/MB031-HIERARCHY-BEGIN/,/MB031-HIERARCHY-END/p' "$log" | head -120
    exit 1
  fi
}

echo "== phase G (MB-033 Q1): the limit is lowered while the app keeps running"
phase_with_change testG_LimitLoweredInTheSameSession MB033-G-READY \
  "psql_q -c \"UPDATE commerce_policy SET max_item_quantity = 5 WHERE status = 'ACTIVE' AND synthetic\" >/dev/null"
psql_q -c "UPDATE commerce_policy SET max_item_quantity = 10 WHERE status = 'ACTIVE' AND synthetic" >/dev/null

echo "== phase H (MB-049, D13): the kitchen raises a price after the quote; reconfirmation required"
BEFORE=$(psql_q -c "SELECT count(*) FROM customer_order WHERE kitchen_id = '00000000-0000-4000-8000-00000000a0d1'")
phase_with_change testH_KitchenPriceChangeNeedsReconfirmation MB049-H-READY \
  "psql_q -v meal=00000000-0000-4000-8000-00000000a0b1 -v price=13000 < \"$HERE/e2e-native/price-change.sql\" >/dev/null"
LOG_H="$RUNNER_TEMP/mb032-testH_KitchenPriceChangeNeedsReconfirmation.log"
PNUMBER=$(grep -o 'MB049-ORDER-NUMBER [^ ]*' "$LOG_H" | head -1 | cut -d' ' -f2)
PROW=$(psql_q -c "SELECT o.total_minor || '|' || i.unit_price_minor || '|' || i.quantity FROM customer_order o JOIN order_item i ON i.order_id = o.id WHERE o.public_number = '$PNUMBER'")
AFTER=$(psql_q -c "SELECT count(*) FROM customer_order WHERE kitchen_id = '00000000-0000-4000-8000-00000000a0d1'")
echo "repriced order $PNUMBER (total|unit|quantity): $PROW; kitchen-one orders before phase H: $BEFORE, after: $AFTER"
[ "$PROW" = "13000|13000|1" ] || { echo "FAIL: repriced order not stored at the accepted price: $PROW"; exit 1; }
[ "$AFTER" = "$((BEFORE + 1))" ] || { echo "FAIL: expected exactly one new order (none at the stale price)"; exit 1; }
echo "== phase I (MB-033): largest accessibility text size, English then Arabic, every customer area"
export TEST_RUNNER_MB033_DT_ORDER="$PNUMBER"
xcrun simctl ui "$UDID" content_size accessibility-extra-extra-extra-large
phase testI_DynamicTextEnglishAndArabic
xcrun simctl ui "$UDID" content_size large
mkdir -p "$RUNNER_TEMP/mb033-ios-screens"
xcrun xcresulttool export attachments --path "$RUNNER_TEMP/mb032-testI_DynamicTextEnglishAndArabic.xcresult" \
  --output-path "$RUNNER_TEMP/mb033-ios-screens" >/dev/null 2>&1 || echo "screenshots not exported; the xcresult bundle keeps them"
echo "== phase J (MB-033): notification permission asked only on request; Don't Allow is safe"
phase testJ_NotificationRefusalIsSafe
echo "== phase K (MB-053): cash refused above the kitchen's limit; deposit terms; the customer reports the transfer"
KITCHEN=00000000-0000-4000-8000-00000000a0d1
psql_q -v kitchen=$KITCHEN < "$HERE/e2e-native/deposit-profile.sql" >/dev/null
trap 'kill $PROXY $API 2>/dev/null || true; psql_q -c "DELETE FROM kitchen_payment_profile WHERE kitchen_id = '"'$KITCHEN'"'" >/dev/null || true' EXIT
phase testK_DepositCheckoutAndTransferReport
LOG_K="$RUNNER_TEMP/mb032-testK_DepositCheckoutAndTransferReport.log"
QNUMBER=$(grep -o 'MB053-ORDER-NUMBER [^ ]*' "$LOG_K" | head -1 | cut -d' ' -f2)
QROW=$(psql_q -c "SELECT p.method || '|' || p.state || '|' || (p.deposit_minor * 2 - o.total_minor BETWEEN 0 AND 1) || '|' || p.recipient_identifier FROM order_payment p JOIN customer_order o ON o.id = p.order_id WHERE o.public_number = '$QNUMBER'")
echo "deposit order $QNUMBER (method|state|half|recipient): $QROW"
[ "$QROW" = "DEPOSIT|CUSTOMER_TRANSFER_REPORTED|true|synthetic-kitchen@instapay" ] || { echo "FAIL: deposit order or transfer report not stored: $QROW"; exit 1; }
psql_q -c "DELETE FROM kitchen_payment_profile WHERE kitchen_id = '$KITCHEN'" >/dev/null
echo "PASS: iOS basket replacement asks first (keep/replace); terminated during submit, reopened offline with the order unconfirmed, recovered online as exactly one order with the server's total; quantity limited to min(policy, availability), a lowered limit blocks the quote until corrected; delivery order with zone fee and matching total; order deep link warm and cold; limit lowered in the same session; kitchen price change reconfirmed; every customer area usable at the largest accessibility text size in English and Arabic; notification refusal safe; deposit checkout and transfer report shown honestly"
