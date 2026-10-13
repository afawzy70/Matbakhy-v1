# QA candidate E: price-change reconfirmation (D13) and kitchen price history (D14), 2026-10-13

Implementation-agent handoff. Every result below is self-review by the implementation agent, not independent review.

## Candidate

- **Immutable candidate: `bc305c642442f1bdeaf0f1c92f30bc9976dcddc5`** on `implementation/mvp`. The six local checks and native run 39 ran at this same SHA.
- Two earlier attempts are **not** candidates:
  - `31025fc` (C): its secret scan failed on a runbook command line, a false positive since fixed ([record](../../verification/runs/20261013-CANDIDATE-C/README.md)).
  - `cc52fa4` (D): the brand guard found the brand name hard-coded in new text, and native run 38 failed at sign-in ([record](../../verification/runs/20261013-CANDIDATE-D/README.md), [native](../../verification/runs/20261013-PRICE-CHANGE/NATIVE-cc52fa4-FAIL.log)). Both are fixed.
- The earlier candidate `4555f599411da9683453bee209559c8d1f11cded` stays valid for its own scope. MBQA-003, MBQA-004, MBQA-005 and OBS-001 stay **FIXED_PENDING_INDEPENDENT_RETEST**; candidate E does not change them, and they can be retested on either SHA.

## Delta since `4555f59`

| Area | Change | Commits |
|---|---|---|
| D13 server | Placement locks the active policy, the meal rows and the zone rows before validating. A price, fee or policy update and a placement therefore serialize. Update first: the placement is refused with the existing 409 `PRICE_CHANGED` (reason `MEAL_CHANGED`, `POLICY_CHANGED` or `TOTAL_CHANGED`), with no order, no stock and no obligation. Placement first: the order keeps its price. No new error code or protocol | `1c49473` |
| D14 price history | `GET /api/v1/cook/meals/{mealId}/price-history` (API-099), owner-scoped (another kitchen gets 404). Derived from immutable meal revisions; edits that do not change the price add no entry. Cook UI: "Price history" section in the meal card with "Active price", the previous prices with Cairo date and time, a note that new prices apply only to orders not yet placed, and a simple price change | `d877a15` |
| D13 web | After `PRICE_CHANGED` the page fetches a fresh quote and compares it. It names the kitchen only for a meal price change and the fee or tax otherwise, and shows the old and new prices and totals. "Place order" needs "Accept the new total" and the terms again. Editing the basket or leaving keeps the basket and the fulfilment details | `e5f43c2` |
| D13 Android and iOS | Same flow and wording in Egyptian Arabic and English. The form stays mounted during the re-quote, so the state survives | `f5c8f50` |
| MB-039 (backend and preview, not customer-facing) | The API runs as a least-privilege database role. A denial whose audit row cannot be written still answers 403. Prometheus metrics are only on the management port. Gauges no longer block a scrape during a DB outage | `d11a4ef` … `e5cfb41` |
| MB-031 fix: token refresh (Android and iOS) | Found in native runs 36 and 37. The app never used its refresh token, so about 5 minutes after sign-in the next request got 401, which ended the session and cleared the basket. Requests now refresh an expired access token first. A refused refresh, or another person's token, ends the session; an unreachable provider keeps it | `c4b1598` |
| MB-033 notification permission | Account shows "Order alerts on this phone". Development builds offer "Allow alerts", never on launch. After a refusal the app explains that every update stays in Notifications and offers Settings. Other builds do not ask, because push delivery is O07; no device token is requested | `16e4cd7` |
| MB-033 fixes from run 38 | Android reports a never-asked notification permission as "denied"; the app now treats it as askable. The alerts card moved below the account actions. The refusal text takes the app name from the brand tokens. iOS harness: dismisses "Save Password?" | `10c8fad`, `23b7ddd` |
| MB-033 native phases | Largest text size in English and Arabic for discover, meal, cart, checkout, orders, order, notifications and support; notification refusal on Android and iOS; screenshots kept as CI artifacts for 7 days | `8097e9e` |
| Secret scan | Worktree scans cover history and fail on zero commits (`ed2ac56`). One reviewed historical false positive was added to `.gitleaksignore` | `ed2ac56`, `f6edcdc` |

## Test scope per platform (implementation agent)

| Platform | What ran | Result | Evidence |
|---|---|---|---|
| Backend (PostgreSQL) | PriceChangeIntegrationTest: increase, decrease, multi-line, fee-only, both race orders, lost response recovered at the original price with an unchanged commission and cash obligation. PriceHistoryIntegrationTest: ordering, active price, an unrelated edit, cross-kitchen 404, non-cook 403. Full backend verify | PASS | `verification/runs/20261013-PRICE-CHANGE/GREEN-*`; candidate run `20261013-CANDIDATE-C` (BACKEND-VERIFY) |
| Customer web (component) | Kitchen increase/decrease, multiple lines, fee-only, explicit acceptance, edit after the notice, refresh failure, second change, Arabic RTL | PASS (red first) | `RED-web-D13.log`; WORKSPACE-CLEAN-CHECKOUT |
| Browser on the real stack | The cook changes a price in the price-history UI; the customer's placement is refused; the Arabic notice is shown, with no order, no overflow at 320 and 390 px and no axe violation; the order is placed at the new price after acceptance | PASS | BROWSER-E2E in the candidate run |
| Cook web (component) | Price-history section, labels, Cairo times, change action | PASS | WORKSPACE-CLEAN-CHECKOUT |
| Android emulator (API 35) | Native run 36 at `f5c8f50`. Quote at EGP 120.00, the price changed to 130.00 in the database, then Place. Notice "The kitchen updated the price of … EGP 120.00 to EGP 130.00." with the old and new totals. No order was created at the stale price, and Place stayed disabled until acceptance. The order was then stored as 13000\|13000\|1 | PASS | `ANDROID-NATIVE-f5c8f50.log` |
| iOS simulator | Runs 36 and 37: phase H blocked by the session-expiry defect. Run 39 at the candidate: phase H PASS | PASS | `NATIVE-bc305c6.log` |
| Same-session lowered limit (Q1, MB-033) | Android: the same process (PID checked), limit 10 → 5 while running; reopening the cart flags the line. iOS phase G: the same running app, the cart flags the line | PASS on both (run 36) | `ANDROID-NATIVE-f5c8f50.log`, `IOS-NATIVE-f5c8f50-FAIL.log` (phase G passed; H failed) |

Not covered by the implementation agent: physical devices (BLK-004), screen readers (BLK-003), and a cross-device race between a real cook and a real customer. Native price changes are applied by SQL (`e2e-native/price-change.sql`, which mirrors the server's own update), not through the cook UI.

## Native results

Native run 39 ([38375863664](https://github.com/afawzy70/Matbakhy-v1/actions/runs/38375863664)) at the candidate:

| Phase | Android emulator (API 35) | iOS simulator |
|---|---|---|
| Sign-in round trip (MB-031) | PASS | PASS |
| MB-032 regression; delivery; quantity limit; deep links | PASS | PASS (phases A–F) |
| Same-session lowered limit | PASS: same process, limit 10 → 5, the line is flagged | PASS (phase G) |
| D13 price change (MB-049) | PASS: no order at the stale price; notice with old and new prices and totals; placed after acceptance; stored 13000\|13000\|1 | PASS (phase H) after a relaunch about 10 minutes after sign-in. The session stayed alive, which confirms the token-refresh fix natively |
| Largest text size (MB-033) | **FAIL**: harness (Place order needs a quote first) | **FAIL**: harness, plus a real overflow: the Pickup choice ran past the screen edge |
| Notification refusal (MB-033) | NOT_RUN (after the failure) | NOT_RUN |

The workflow conclusion is therefore **failure**. Every D13, D14, regression and session check passed before the failing MB-033 phase. MB-033 is not part of this candidate's scope and stays IN_PROGRESS; its fixes come after the candidate.

Evidence: `verification/runs/20261013-PRICE-CHANGE/NATIVE-bc305c6.log` and `verification/runs/20261013-MB-049/`.

## Fixtures

- Dev-profile commerce policy version 1 (synthetic; limit 10 is **not** an approved value, O04), tax 0, commission 10%.
- Zones: Test zone A (fee 3000) and B.
- Native: `apps/customer-mobile/e2e-native/seed.sql` (Native Kitchen One and Two; Native Dish One at 12000) and `delivery-address.sql`. `price-change.sql` (psql variables `meal` and `price`) changes a meal price the way the kitchen edit does: it locks the row, increments revision and version, and inserts the next `meal_revision`.
- Browser: `apps/operations-web/e2e/customer-journey.e2e.ts`. The cook changes "كشري تجريبي" in the UI.
- Notification prompt: on in development builds only (`extra.notificationPrompt`, or `EXPO_PUBLIC_NOTIFICATION_PROMPT`); push delivery is not set up (O07).

## Retest instructions

1. Check out the candidate SHA above in a new worktree with a new Compose project and ports, then start the stack with `scripts/dev.sh up`. Identities are the synthetic realm users, with passwords from `infra/.env`.
2. **Price increase:** customer-a quotes a meal and accepts the terms. cook-a changes that meal's price under Meals → Price history → New price → Change price. The customer presses Place order.
   - Expect: no order; the notice names the kitchen, the meal and the old and new prices, then the old and new totals.
   - Place is disabled until "Accept the new total" and the terms are both done; the order is then created at the new price.
3. **Price decrease:** same steps; the notice shows the lower price, and acceptance is still required.
4. **Fee-only change:** ops-a changes Test zone A's fee in configuration during checkout. The notice must name the delivery fee, not the kitchen.
5. **Multiple lines:** two meals from one kitchen; change one price. Only that line is listed as changed.
6. **Edit or leave:** after the notice, change quantities or leave and return. The basket and fulfilment details are kept, and a new quote is needed.
7. **Second change:** change the price again after the notice is shown. Placing is refused again and the notice updates; the earlier acceptance does not carry over.
8. **Existing orders:** an order placed before the change keeps its prices in the customer, cook and finance views.
9. **Price history:** cook-a sees "Active price" and the previous prices newest first, with Cairo times. A description-only edit adds no entry. cook-b gets not found for cook-a's meal (`GET /api/v1/cook/meals/{id}/price-history`).
10. **Languages and widths:** Arabic RTL and English LTR at 320 and 390 px on web; Android and iOS in both languages.
11. **Session past 5 minutes (native):** sign in on the app, wait more than 5 minutes (the access-token lifetime), then open Orders or the cart. You should still be signed in and the basket kept. Then end the provider session in Keycloak (admin console: Users → the user → Sessions → Sign out) and open Orders again: the app should be signed out cleanly, with the private data cleared.
12. **Notification permission (development build):** Account → "Order alerts on this phone". The app must not ask on launch. Press "Allow alerts" and refuse in the system dialog: the explanation and "Open Settings" appear, and Notifications still lists updates. Allowing shows the allowed text. A build with `EXPO_PUBLIC_NOTIFICATION_PROMPT=false` must never ask.
13. **Largest text size:** Android font size at maximum, iOS Larger Accessibility Sizes at maximum. Check every customer area in both languages: wrapping, no clipped labels, reachable actions, RTL order. CI screenshots are in run 39's `android-screenshots` and `ios-screenshots` artifacts (kept 7 days).

## Limitations

- The race tests run in one JVM against one PostgreSQL. Multi-instance deployment is not tested.
- The quote screen does not refresh on its own. The customer learns of a change only when placing (no WebSockets, push or polling, per D13).
- Native price changes come from SQL rather than the cook UI; the browser journey covers the cook UI.
- Dynamic text: the native phases check that content shows and that the main actions can be reached. Clipping and overlap are judged from screenshots by a person; nothing measures them automatically.
- Screen readers (BLK-003) and physical devices (BLK-004): not run.
