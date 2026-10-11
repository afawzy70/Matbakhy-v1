# Provider interfaces and conditional extensions

Baseline production provider choices remain O07; no vendor contract or credential exists by implication. Each adapter implements timeouts, redaction, deterministic errors, retries only where safe and isolated sandbox fixtures. Required fake adapter in production is a startup error.

## Baseline adapters

| Adapter | Interface responsibilities | Acceptance |
|---|---|---|
| Identity | OIDC discovery/JWKS, authorization code/PKCE, sessions, logout, MFA assurance | Real provider in staging; invalid audience/state/expired/revoked credentials denied |
| Phone verification | Send one-time contact code with destination/rate context and delivery reference | Test sender never emits real SMS; live sender selected before pilot; expired/replayed code denied |
| Email | Transactional template/version/locale, recipient, stable event ID | Provider failure retry/dead-letter; no duplicate effective order notices |
| Push | Installation token lifecycle, event hint, receipts/invalid-token cleanup | Android/iOS device tests; denial safe; no private payload previews |
| Object storage | Private upload/download tickets, approved public variants, delete/retention | Scope/expiry/size/type verified; no private-document public URL |
| Scanner | Quarantine scan result with version/reason | Real scanner or approved equivalent validation before live uploads; fail-closed on unknown result |

## Online payment extension, if O02 selects it

Do not silently activate online payments or choose an Egypt provider by name without checking merchant eligibility, settlement model, fees, sandbox/API documentation and support. A provider choice may change who legally/operationally collects funds; update finance architecture first.

Required design: payment intent per order attempt; states CREATED → PENDING → AUTHORIZED/CAPTURED/FAILED/CANCELLED with refunds separate. Decide authorization/capture timing relative to cook acceptance and capacity reservation. Handle payment success after order expiry: never resurrect stale stock; reconcile and refund/resolve under recorded policy. Signed webhooks plus authoritative provider reconciliation determine payment status, never browser redirect. Use provider idempotency keys, unique event IDs, raw-body signature validation and bounded replay handling.

Refunds need provider IDs, async pending/failure/retry, full/partial allocation policy, amount caps and financial postings. Test callback duplication, reordering, signature failure, lost response after capture, timeout, abandonment, decline, duplicate click, currency mismatch, delayed refund and settlement mismatch. Add provider-specific contract tests and sandbox end-to-end evidence. Keep card data on provider-hosted/tokenized surfaces; no PAN/CVV logging/storage.

This is an extra implementation tranche with new stories, estimates and gates. Cash-first completion cannot be reported as online-payment completion. Conversely, choosing online payments as launch-critical makes the provider tranche mandatory before P09.

## Courier extension, if selected

Define serviceability/cost quote, dispatch request, assignment, pickup, delivery, failure, cancellation and proof of handover. Decide cash collector explicitly. Provider status must map through allowed internal transitions, with dedup/signature verification, out-of-order handling, manual exception and reconciliation. A courier timestamp or GPS point alone does not authorize payment verification. Add driver data minimization and operational outage fallback. No invented live driver location.

## Social extension, if O10 makes it required

Add follows/feed/comments only through explicit requirement intake. Define visibility, report/block/moderation, spam limits, privacy and pagination. Meal discovery/favorites are already baseline. Do not migrate an earlier mock social prototype into production without backend persistence and moderation acceptance. Chat requires its own recipient scope, retention, abuse controls and notification tests.
