# MB-027 review (self-review, not independent)

Reviewer: Claude Code implementation agent (self-review).

## Scope
Customer web (`apps/customer-web`): discovery, meal detail, cart, sign-in return path, phone verification, addresses, quote, stable-key placement with retry and reload recovery, order tracking; e2e harness serves the customer web; Playwright customer journey.

## Findings
1. Stable keys: client request id and idempotency key are stored before the request; a lost response is retried with the same keys (browser journey intercepts the first committed response; exactly one order exists). A mutation generating fresh keys on retry failed the journey. Reload recovery uses the by-request lookup (component test).
2. No client-side prices: the cart stores ids, titles and quantities only; totals come from the server quote; a definitive 4xx clears the stored keys and shows the server reason.
3. States: loading, empty, error with retry, offline banner, signed-out prompt with return address (component tests for empty/error/retry and sign-in return).
4. The order page refreshes every 10 s; the journey shows ACCEPTED, the handover code at READY, and COMPLETED after the cook enters the code in the operations web.

## Residual
- Visual design uses draft tokens; final branded UI depends on O05 (pending), so the P06 gate cannot pass yet.
- Phone verification in the browser journey is synthetic SQL (fake SMS code not exposed); the verification UI itself is exercised only up to the code request.
- Favorites, reviews and account management are MB-028; accessibility audit is MB-029.
