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

Filled in after the runs complete.
