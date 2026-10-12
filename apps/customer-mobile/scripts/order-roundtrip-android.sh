#!/usr/bin/env bash
# MB-032 on a real Android emulator (CI only), after auth-roundtrip-android.sh, against the local stack with synthetic
# data (e2e-native/seed.sql) and synthetic users:
#   TC-148  adding a meal from another kitchen asks first; "Keep cart" leaves the basket, "Replace cart" replaces it.
#   TC-049  the app is killed while the order response is held back (the server created the order); reopened offline
#           it says the order is unconfirmed and that it is offline; back online, "Check and retry" shows the order;
#           the database holds exactly one order for the customer.
#   TC-132  the total the app shows for the order equals the server's stored total.
set -euo pipefail
PKG=com.matbakhy.customer.dev
export PATH="$ANDROID_HOME/platform-tools:$PATH"
HERE="$(cd "$(dirname "$0")/.." && pwd)"
ROOT="$(cd "$HERE/../.." && pwd)"
: "${USER_PASSWORD:?synthetic user password}"
OUT="${RUNNER_TEMP:-/tmp}/mb032-android"
mkdir -p "$OUT"
UI="$OUT/ui.xml"
STEP=0
psql_q() { docker compose --env-file "$ROOT/infra/.env" -f "$ROOT/infra/compose.yaml" exec -T postgres psql -U matbakhy -d matbakhy -tA -v ON_ERROR_STOP=1 "$@"; }

dump() {
  adb shell uiautomator dump /sdcard/ui.xml >/dev/null 2>&1 || true
  adb exec-out cat /sdcard/ui.xml > "$UI" 2>/dev/null || : > "$UI"
}
center_of() {
  python3 - "$UI" "$1" <<'P'
import re, sys
xml = open(sys.argv[1], encoding="utf-8", errors="replace").read()
for node in re.findall(r"<node [^>]*>", xml):
    if re.search(sys.argv[2], node):
        m = re.search(r'bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"', node)
        if m:
            x1, y1, x2, y2 = map(int, m.groups())
            print((x1 + x2) // 2, (y1 + y2) // 2)
            break
P
}
texts() { grep -o '\(text\|content-desc\)="[^"]*"' "$UI" | sort -u | tr '\n' ' '; echo; }
shot() {
  STEP=$((STEP + 1))
  adb exec-out screencap -p > "$OUT/$STEP-$1.png"
  echo "screenshot $STEP-$1: $(sha256sum < "$OUT/$STEP-$1.png" | cut -c1-16)"
}
fail() {
  dump; echo "FAIL: $*"; echo "visible: $(texts)"; shot failure
  echo "app log:"; adb logcat -d -s ReactNativeJS:V ReactNative:W | tail -40
  exit 1
}
wait_for() {
  local deadline=$((SECONDS + $2)) pos
  while [ "$SECONDS" -lt "$deadline" ]; do
    dump
    pos=$(center_of "$1")
    if [ -n "$pos" ]; then echo "$pos"; return 0; fi
    sleep 2
  done
  return 1
}
tap() { local pos; pos=$(wait_for "$1" "${2:-60}") || fail "not found: $1"; adb shell input tap $pos; }
# Scrolls the current screen up until the element is on screen (long forms).
# Below-the-fold controls (the cart button sits under the meal details; native run 23): scroll up until visible.
# Swipes relative to the real screen: the AVD has no device profile, so fixed coordinates
# (y=1500, native run 24) fell outside the default screen and never scrolled.
SCREEN=$(adb shell wm size | tr -d '\r' | sed -n 's/.*: \([0-9]*\)x\([0-9]*\).*/\1 \2/p' | tail -1)
[ -n "$SCREEN" ] || fail "could not read the emulator screen size"
SCREEN_W=${SCREEN% *}
SCREEN_H=${SCREEN#* }
tap_scrolled() {
  local pos
  for _ in 1 2 3 4 5 6 7 8; do
    pos=$(wait_for "$1" 4) && { adb shell input tap $pos; return 0; }
    adb shell input swipe $((SCREEN_W / 2)) $((SCREEN_H * 3 / 4)) $((SCREEN_W / 2)) $((SCREEN_H / 4)) 400
  done
  fail "not found after scrolling: $1"
}
# Waits for content that appears below the fold (the quote under "Calculate total" on the small default AVD screen,
# native run 25): checks what is visible, then scrolls on, until the deadline.
wait_scrolled() {
  local deadline=$((SECONDS + $2))
  while [ "$SECONDS" -lt "$deadline" ]; do
    wait_for "$1" 4 >/dev/null && return 0
    adb shell input swipe $((SCREEN_W / 2)) $((SCREEN_H * 3 / 4)) $((SCREEN_W / 2)) $((SCREEN_H / 4)) 400
  done
  return 1
}
# Content above the fold after scrolling down (the basket line above the quote button, native run 32): scroll back up.
wait_scrolled_up() {
  local deadline=$((SECONDS + $2))
  while [ "$SECONDS" -lt "$deadline" ]; do
    wait_for "$1" 4 >/dev/null && return 0
    adb shell input swipe $((SCREEN_W / 2)) $((SCREEN_H / 4)) $((SCREEN_W / 2)) $((SCREEN_H * 3 / 4)) 400
  done
  return 1
}
absent() { dump; if [ -n "$(center_of "$1")" ]; then fail "unexpectedly visible: $1"; fi; }
launch() {
  adb shell am force-stop "$PKG"
  adb shell am start -W -n "$PKG/.MainActivity" >/dev/null
}

echo "== seed synthetic kitchens and meals"
psql_q < "$HERE/e2e-native/seed.sql" >/dev/null
CUSTOMER="customer-a"
psql_q -c "UPDATE app_user SET phone_e164 = '+201005550001', phone_verified_at = now() WHERE display_name LIKE '%SyntheticA%'" >/dev/null
# One saved delivery address in the synthetic "Test zone A" for the MB-033 delivery checkout below.
psql_q -v phone=+201005550001 < "$HERE/e2e-native/delivery-address.sql" >/dev/null

echo "== TC-148: basket replacement asks first"
launch
tap 'content-desc="Native Dish One[^"]*"' 90
tap 'resource-id="slot-0"'
tap_scrolled 'resource-id="add-to-cart"'
wait_for 'text="Added to cart."' 30 >/dev/null || fail "first meal not added"
adb shell input keyevent 4
tap 'content-desc="Native Dish Two[^"]*"' 60
tap 'resource-id="slot-0"'
tap_scrolled 'resource-id="add-to-cart"'
wait_for 'text="Replace your cart\?"' 30 >/dev/null || fail "no replacement prompt"
shot replace-prompt
tap 'text="Keep cart"|text="KEEP CART"'
wait_for 'text="Your cart is unchanged."' 30 >/dev/null || fail "keep did not keep"
tap_scrolled 'resource-id="go-to-cart"'
wait_for 'text="1 × Native Dish One[^"]*"' 30 >/dev/null || fail "basket changed after keep"
adb shell input keyevent 4
tap_scrolled 'resource-id="add-to-cart"'
tap 'text="Replace cart"|text="REPLACE CART"' 30
wait_for 'text="Added to cart."' 30 >/dev/null || fail "replace did not add"
tap_scrolled 'resource-id="go-to-cart"'
wait_for 'text="1 × Native Dish Two[^"]*"' 30 >/dev/null || fail "basket not replaced"
absent 'text="1 × Native Dish One[^"]*"'
shot cart-replaced
echo "ok: keep leaves the basket; replace replaces it"

echo "== sign in from the cart"
# The cart is longer since basket editing, so "Sign in" sits at the bottom edge of the small default AVD screen and a
# tap there did not open the login (native run 26): scroll it into full view first, and tap once more if no form came.
adb shell input swipe $((SCREEN_W / 2)) $((SCREEN_H * 3 / 4)) $((SCREEN_W / 2)) $((SCREEN_H / 4)) 400
tap 'text="Sign in"'
# Fresh login (prompt=login) in Chrome Custom Tabs; the form fields are the provider's.
if ! wait_for 'resource-id="username"|hint="Username or email"|text="Username or email"' 60 >/dev/null; then
  pos=$(wait_for 'text="Sign in"' 5) && adb shell input tap $pos
  wait_for 'resource-id="username"|hint="Username or email"|text="Username or email"' 90 >/dev/null || fail "login form missing"
fi
pos=$(wait_for 'resource-id="username"|hint="Username or email"|text="Username or email"' 5); adb shell input tap $pos; sleep 2; adb shell input text "$CUSTOMER"
pos=$(wait_for 'resource-id="password"|hint="Password"|text="Password"' 10); adb shell input tap $pos; sleep 2; adb shell input text "$USER_PASSWORD"
tap 'resource-id="kc-login"|text="Sign In"'
wait_for 'resource-id="order-form"|resource-id="mode-PICKUP"' 90 >/dev/null || fail "checkout not shown after sign-in"

echo "== TC-049: kill the app while the order response is held back"
python3 "$HERE/e2e-native/slow_proxy.py" 8081 8080 8 > "$OUT/proxy.log" 2>&1 &
PROXY=$!
trap 'kill $PROXY 2>/dev/null || true' EXIT
sleep 1
# All API traffic goes through the slow proxy from a fresh app process: the app keeps its connections open, so a
# redirect made just before "Place order" did not apply to the order request (native runs 27-29: the response arrived,
# the app confirmed the order and cleared the basket before the kill, as it should).
adb reverse tcp:8080 tcp:8081 >/dev/null
launch
tap 'resource-id="open-cart"' 90
wait_for 'resource-id="order-form"|resource-id="mode-PICKUP"' 90 >/dev/null || fail "checkout not shown after relaunch"
tap 'resource-id="mode-PICKUP"'
tap_scrolled 'resource-id="get-quote"'
wait_scrolled 'text="Total: [^"]*"' 60 || fail "no quote"
SHOWN_QUOTE=$(grep -o 'text="Total: [^"]*"' "$UI" | head -1)
tap_scrolled 'resource-id="accept-terms"'
tap_scrolled 'resource-id="place-order"'
sleep 3
# Precondition of TC-049: the answer must still be outstanding when the app is killed.
absent 'text="MB-[0-9]+"'
adb shell am force-stop "$PKG"
echo "app killed while the response was held back"
sleep 8
kill $PROXY 2>/dev/null || true
COUNT=$(psql_q -c "SELECT count(*) FROM customer_order o JOIN app_user u ON u.id = o.customer_id WHERE u.phone_e164 = '+201005550001'")
echo "orders in the database after the kill: $COUNT"
[ "$COUNT" = "1" ] || fail "expected the server to have created exactly one order, found $COUNT"

echo "== reopen offline"
adb reverse --remove tcp:8080 >/dev/null
launch
# Native run 27: after the kill and an offline relaunch, Discover showed no cart shortcut. Before failing, record
# whether the app still considers the customer signed in (a session end clears the basket) for diagnosis.
if ! pos=$(wait_for 'resource-id="open-cart"' 60); then
  echo "diagnostic: no cart shortcut after the offline relaunch; Account tab follows"
  pos=$(wait_for 'resource-id="tab-account"|content-desc="Account, tab, 4 of 4"' 10) && adb shell input tap $pos && sleep 3
  fail "cart shortcut missing after the offline relaunch"
fi
adb shell input tap $pos
wait_scrolled 'text="You.re offline. We couldn.t confirm whether your order arrived[^"]*"' 60 || fail "offline uncertainty not stated"
absent 'resource-id="place-order"'
absent 'text="MB-[0-9]+"'
shot reopened-offline
echo "ok: offline and unconfirmed, nothing claimed"

echo "== back online: recover the one order"
adb reverse tcp:8080 tcp:8080 >/dev/null
tap_scrolled 'resource-id="retry-order"'
wait_for 'text="MB-[^"]*"' 60 >/dev/null || fail "recovered order not shown"
NUMBER=$(grep -o 'text="MB-[^"]*"' "$UI" | head -1 | sed 's/text="\(.*\)"/\1/')
shot recovered-order
COUNT=$(psql_q -c "SELECT count(*) FROM customer_order o JOIN app_user u ON u.id = o.customer_id WHERE u.phone_e164 = '+201005550001'")
[ "$COUNT" = "1" ] || fail "duplicate order: $COUNT orders"
# No duplicated business side effect either: one stock allocation, one portion reserved on the meal and cook slots,
# one cash obligation, one ORDER_PLACED outbox event and one order event.
SIDE=$(psql_q -v phone=+201005550001 < "$HERE/e2e-native/side-effects.sql")
echo "side effects (orders|allocations|meal-slot reserved|cook-slot reserved|cash obligations|ORDER_PLACED events|order events): $SIDE"
[ "$SIDE" = "1|1|1|1|1|1|1" ] || { echo "FAIL: duplicated or missing side effect: $SIDE"; exit 1; }
DB=$(psql_q -c "SELECT public_number || '|' || total_minor FROM customer_order WHERE public_number = '$NUMBER'")
wait_for 'text="Total: [^"]*"' 10 >/dev/null || true
SHOWN_TOTAL=$(grep -o 'text="Total: [^"]*"' "$UI" | head -1)
echo "order $NUMBER; database (number|total_minor): $DB; app shows ${SHOWN_TOTAL:-<scrolled>}; quote showed $SHOWN_QUOTE"
[ "${DB#*|}" = "9000" ] || fail "unexpected stored total ${DB#*|}"
# Intl puts a non-breaking space after the currency code; compare the digits.
for shown in "$SHOWN_QUOTE" "$SHOWN_TOTAL"; do
  [ -z "$shown" ] && continue
  echo "$shown" | grep -qE 'EGP.90\.00' || fail "app total ($shown) differs from the server's stored total 9000"
done
[ -n "$SHOWN_QUOTE$SHOWN_TOTAL" ] || fail "no total visible"
echo "== MB-033 Q1: the quantity control stops at min(policy limit, availability)"
# Native Dish One's slot has 15 portions; the synthetic policy allows 10 per line (fixture value, not an owner decision: O04).
launch
tap 'content-desc="Native Dish One[^"]*"' 90
tap 'resource-id="slot-0"'
pos=$(wait_for 'content-desc="Increase"' 30) || fail "no increase control"
for _ in $(seq 14); do adb shell input tap $pos; sleep 0.4; done
wait_for 'text="10"' 20 >/dev/null || fail "quantity did not stop at the policy limit of 10"
dump; grep -qE 'content-desc="Increase"[^>]*enabled="false"' "$UI" || fail "increase still enabled at the limit"
shot quantity-at-limit
tap_scrolled 'resource-id="add-to-cart"'
wait_for 'text="Added to cart."' 30 >/dev/null || fail "ten portions not added"
echo "ok: the control stopped at 10 of 15 available portions and is disabled there"

echo "== MB-033 Q1: a lowered limit makes the saved basket invalid and blocks the quote"
QUOTES_BEFORE=$(psql_q -c "SELECT count(*) FROM quote q JOIN app_user u ON u.id = q.customer_id WHERE u.phone_e164 = '+201005550001'")
# Test-only change of the synthetic CI policy row; the server reads the active policy on every request.
psql_q -c "UPDATE commerce_policy SET max_item_quantity = 5 WHERE status = 'ACTIVE' AND synthetic" >/dev/null
trap 'kill $PROXY 2>/dev/null || true; psql_q -c "UPDATE commerce_policy SET max_item_quantity = 10 WHERE status = '"'"'ACTIVE'"'"' AND synthetic" >/dev/null || true' EXIT
launch
tap 'resource-id="open-cart"' 90
wait_scrolled 'text="At most 5 per meal can be ordered now. Reduce the quantity to continue."' 60 || fail "over-limit basket line not flagged"
wait_scrolled 'resource-id="get-quote"' 30 || fail "no quote button"
grep -qE 'resource-id="get-quote"[^>]*enabled="false"|enabled="false"[^>]*resource-id="get-quote"' "$UI" || fail "quote still possible with an over-limit quantity"
shot over-limit-blocked
QUOTES_AFTER=$(psql_q -c "SELECT count(*) FROM quote q JOIN app_user u ON u.id = q.customer_id WHERE u.phone_e164 = '+201005550001'")
[ "$QUOTES_BEFORE" = "$QUOTES_AFTER" ] || fail "a quote was created for an over-limit basket ($QUOTES_BEFORE -> $QUOTES_AFTER)"
pos=$(wait_scrolled_up 'resource-id="qty-dec-00000000-0000-4000-8000-00000000a0b1"' 30 && center_of 'resource-id="qty-dec-00000000-0000-4000-8000-00000000a0b1"') || fail "no decrease control on the basket line"
for _ in $(seq 5); do adb shell input tap $pos; sleep 0.5; done
wait_for 'text="5 × Native Dish One[^"]*"' 20 >/dev/null || fail "basket line not reduced to 5"
absent 'text="At most 5 per meal[^"]*"'
echo "ok: the saved basket was flagged, no quote was requested, and reducing to 5 cleared it"

echo "== MB-033 delivery checkout: saved address, zone fee, total parity, persisted fulfilment"
tap_scrolled 'resource-id="mode-DELIVERY"'
wait_scrolled 'text="Home · Synthetic street 1"|content-desc="Home · Synthetic street 1[^"]*"' 30 || fail "saved delivery address not offered"
tap_scrolled 'resource-id="get-quote"'
wait_scrolled 'text="Total: [^"]*"' 60 || fail "no delivery quote"
DELIVERY_QUOTE=$(grep -o 'text="Total: [^"]*"' "$UI" | head -1)
grep -qE 'text="Delivery: EGP.30\.00"' "$UI" || fail "zone fee (Delivery: EGP 30.00) not shown with the quote"
echo "$DELIVERY_QUOTE" | grep -qE 'EGP.630\.00' || fail "delivery quote $DELIVERY_QUOTE is not 5 x 120.00 + 30.00 fee"
shot delivery-quote
tap_scrolled 'resource-id="accept-terms"'
tap_scrolled 'resource-id="place-order"'
wait_for 'text="MB-[^"]*"' 60 >/dev/null || fail "delivery order not confirmed"
DNUMBER=$(grep -o 'text="MB-[^"]*"' "$UI" | head -1 | sed 's/text="\(.*\)"/\1/')
[ "$DNUMBER" != "$NUMBER" ] || fail "delivery order shows the earlier order number"
shot delivery-order
DROW=$(psql_q -c "SELECT o.fulfillment_mode || '|' || o.delivery_fee_minor || '|' || o.total_minor || '|' || (o.address_snapshot IS NOT NULL) || '|' || o.kitchen_id || '|' || o.state FROM customer_order o WHERE o.public_number = '$DNUMBER'")
echo "delivery order $DNUMBER (mode|fee|total|address snapshot|kitchen|state): $DROW"
case "$DROW" in
  "DELIVERY|3000|63000|true|00000000-0000-4000-8000-00000000a0d1|"*) ;;
  *) fail "delivery order not persisted as expected: $DROW" ;;
esac
psql_q -c "UPDATE commerce_policy SET max_item_quantity = 10 WHERE status = 'ACTIVE' AND synthetic" >/dev/null
echo "ok: delivery order persisted with the zone fee, the address snapshot and the quoted total, in kitchen one's queue"

echo "== MB-033 deep link to the order: warm (app running on Discover) and cold (process stopped)"
DID=$(psql_q -c "SELECT id FROM customer_order WHERE public_number = '$DNUMBER'")
launch
wait_for 'content-desc="Native Dish One[^"]*"|resource-id="open-cart"|resource-id="tab-discover"' 60 >/dev/null || fail "Discover not shown before the warm link"
adb shell am start -W -a android.intent.action.VIEW -d "matbakhy-dev://orders/$DID" "$PKG" >/dev/null
wait_for "text=\"$DNUMBER\"" 60 >/dev/null || fail "warm deep link did not open order $DNUMBER"
shot deep-link-warm
adb shell am force-stop "$PKG"
adb shell am start -W -a android.intent.action.VIEW -d "matbakhy-dev://orders/$DID" "$PKG" >/dev/null
wait_for "text=\"$DNUMBER\"" 90 >/dev/null || fail "cold deep link did not open order $DNUMBER"
shot deep-link-cold
echo "ok: the order link opens order $DNUMBER warm and cold"

if adb logcat -d | grep -E "FATAL EXCEPTION"; then fail "fatal exception"; fi
echo "PASS: Android basket replacement asks first (keep/replace); killed during submit, reopened offline with the order unconfirmed, recovered online as exactly one order with the server's total; quantity limited to min(policy, availability), a lowered limit blocks the quote until corrected; delivery order with zone fee and matching total; order deep link warm and cold"
