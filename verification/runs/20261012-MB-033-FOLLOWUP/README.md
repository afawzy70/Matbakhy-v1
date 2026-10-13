# MB-033 native follow-up: delivery checkout, quantity limit, retry review (2026-10-12)

Self-review by the implementation agent; not independent verification.

## Native coverage gaps closed (both platforms)

The phases run after the MB-032 recovery checks, in `scripts/order-roundtrip-android.sh` and in `e2e-ios/UITests/OrderingTests.swift` (phases D and E) with `scripts/order-roundtrip-ios.sh`. They are added at `e3ba61a`.

Fixture changes (`e2e-native/seed.sql`, `e2e-native/delivery-address.sql`):
- kitchen one now delivers to the synthetic "Test zone A" (fee 3000);
- its slot has 15 portions;
- each synthetic customer has one saved address in that zone.

The limit of 10 is the synthetic CI policy, not an owner decision (O04 open). The phases change it to 5 and back with a test-only update of the synthetic policy row.

| Requirement | Android check | iOS check |
|---|---|---|
| Effective maximum = min(policy 10, availability 15); the control respects it | `Increase` pressed 14 times stops at 10 and is disabled | testD: same, `isEnabled` false |
| Persisted basket becomes visibly invalid when the limit drops to 5 | Notice "At most 5 per meal…" after relaunch | testE: same |
| No quote with an invalid quantity | `get-quote` disabled; quote rows for the customer unchanged | `get-quote` disabled; exactly one new quote row in phase E (the corrected one) |
| Correcting the quantity restores quoting and checkout | 5 decreases; notice gone; quote and order succeed | same |
| Delivery: saved address offered and used | "Home · Synthetic street 1" shown, delivery mode | same |
| Zone fee and total parity | "Delivery: EGP 30.00", "Total: EGP 630.00" (5 × 120.00 + 30.00) | fee and total printed and compared |
| Order persisted with fulfilment details | `DELIVERY`, fee 3000, total 63000, address snapshot present, kitchen one | same |
| Staff visibility | Kitchen one's order row checked in the database only; the cook and staff queue APIs are covered by backend tests (OrderLifecycleIntegrationTest), not by the native run | same |
| Direct API requests still enforced | Backend: QuoteIntegrationTest asserts a quantity of 11 under the policy limit of 10 is refused with 422 ("above policy maximum"); `QuoteService` checks the active policy on every quote | same (server-side) |

Native result: see "Native runs" below.

## Retry review: iOS automatic recovery versus Android "Check and retry"

Both platforms run the same code (`placement.ts`, `screens/Cart.tsx`). The difference seen in native runs 30–31 comes from the test sequence, not the platforms:
- **Automatic check:** when the cart opens with a pending order and a signed-in customer, the app asks the server at once (`GET /orders/by-request/{clientRequestId}`).
- **iOS phase C** relaunches the app with the API already reachable, so this automatic check finds the order.
- **Android** goes back online without relaunching. The automatic check had run while offline, so the customer uses "Check and retry".
- Both paths call the same `recover()`.

Review findings:
- **Reachable:** the button is shown with the pending-order notice whenever an order is pending. It is disabled while a check runs or when signed out (sign-in is offered instead).
- **Understandable:** the notice says plainly that the order is unconfirmed, and whether the app is offline. It never shows the order as placed or failed until the server answers.
- **Safe under repeated retries:**
  - `recover()` looks the order up first and re-sends only on a definite 404, always with the same idempotency key and client request ID.
  - The server returns the same order for a repeated key (OrderPlacementIntegrationTest, `retries_after_timeout_and_after_idempotency_retention_create_exactly_one_order`).
- **Defect found (client, minor):**
  - Two presses in the same frame, before the button re-rendered as disabled, started two checks, and could re-send twice.
  - The server's idempotency would still have produced one order, but the client sent redundant requests.
  - Test first: `RED-native-repeated-retry.log`.
  - Fix: one check at a time (an in-flight guard in `recheck`). A second press, or the check on focus, waits for the running one.
- **No duplicates:** the native runs assert one order and one set of side effects (`side-effects.sql`: `1|1|1|1|1|1|1`).

## Android incident (unchanged record)

- Native runs 27–29 were invalid for TC-049 on Android. The harness redirected the API port only after the app had opened its connections, so the order response was not held back.
- Runs 30 and 31 are valid: the whole checkout goes through the slow proxy from a fresh process, and the absence of an order number is checked before the kill.
- `9dd6fa2` (cart shortcut while an order is pending; one retry of a failed storage read) is defensive. It was not the root cause.

## Native runs

| Run | Source | Android emulator | iOS simulator |
|---|---|---|---|
| 32 ([38283912106](https://github.com/afawzy70/Matbakhy-v1/actions/runs/38283912106)) | `e3ba61a` | **FAIL (harness):** quantity cap at 10 and the lowered-limit block passed; the script could not reach the basket line's decrease control (scrolled below it). Delivery not reached. [log](ANDROID-NATIVE-e3ba61a-FAIL.log) | **PASS:** phases D and E (cap 10; lowered limit blocks the quote, one quote after correction; delivery MB-100002 stored DELIVERY/3000/63000 with address snapshot, kitchen one; app EGP 630.00 and fee EGP 30.00). [log](IOS-NATIVE-e3ba61a.log) |
| 33 ([38288198395](https://github.com/afawzy70/Matbakhy-v1/actions/runs/38288198395)) | `a8132aa` | **PASS:** MB-031/MB-032 regression; cap 10; lowered limit flagged, no quote, corrected to 5; delivery MB-100002 stored DELIVERY/3000/63000 with address snapshot, kitchen one, app EGP 630.00 and fee EGP 30.00; order deep link warm and cold. [log](ANDROID-NATIVE-a8132aa.log) | **FAIL (test build):** `XCUIApplication.open` needs an iOS 16.4 availability guard; no iOS check ran. [log](IOS-NATIVE-a8132aa-FAIL.log) |
| 34 ([38291984205](https://github.com/afawzy70/Matbakhy-v1/actions/runs/38291984205)) | `f5d2ba0` | **PASS** (job 114953998714; same phases as run 33) | **PASS:** all phases A–F: regression, cap 10, lowered limit (one quote after correction), delivery MB-100002 DELIVERY/3000/63000, deep link warm and cold. [log](IOS-NATIVE-f5d2ba0.log) |

**Result:** at `f5d2ba0` both platforms pass the native delivery, quantity-limit and deep-link checks on emulator/simulator. Six local checks also PASS at `f5d2ba0` (`verification/runs/20261012-CANDIDATE-B2`). Physical devices: not run (BLK-004).
| 35 ([38296110431](https://github.com/afawzy70/Matbakhy-v1/actions/runs/38296110431)) | `4555f59` | Workflow success (job conclusions read via the API; step logs not excerpted) | Workflow success (same) |

**Current result (2026-10-13):**
- The latest step-level evidence for each phase is run 34 at `f5d2ba0`, with excerpts for both platforms.
- Run 35 at `4555f59` adds the mobile mid-session limit refresh. Its job conclusions are success, but no native phase lowers the limit while the cart stays open, so that refresh is covered by component tests only.
- Physical devices: NOT_RUN (BLK-004). Screen readers: NOT_RUN (BLK-003).

