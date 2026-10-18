# Matbakhy owner E2E test cases — E2E

**Basis:** current stabilization candidate; executable tests must reference a tested commit. **Every case below is NOT_RUN** until separately executed. These are test specifications, not verified results.

## Execution rules

- Only synthetic identities/data in Preview. Never use real payment credentials, phones or customer data. Do not reset shared staging DB.
- Setup via approved fixture/seed script with unique run identifier and known baseline. If prerequisite cannot be met, mark BLOCKED, not PASS.
- Capture account role, platform/browser, exact commit + deployed image digest, timestamp, request IDs, sanitized screenshots/logs, expected versus observed and defect ID.
- Public web access does not mean public privileges. Do not publish Keycloak /admin or /realms/master to the Internet. For administration use an authenticated localhost SSH tunnel, strong admin MFA, and least privilege.
- Execute destructive/race/fault-injection cases only against isolated disposable stack with synthetic data; do not cause disruption to publicly reachable shared preview.


## Discovery and customer account

### E2E-DISC-001 — Anonymous discovery

- **Initial status:** NOT_RUN · **Role:** Guest · **Prerequisite:** Published kitchens A/B, meals M1/M2.
- **Steps:**
  1. Record the current build SHA, selected account/role and isolate the synthetic fixture for this case.
  2. Open customer URL; then select Arabic; then open kitchens; then open M1; then switch to English.
  3. Check both visible UI state and the relevant protected API result; refresh/reopen where applicable.
  4. Inspect authoritative DB/audit/capacity/finance evidence with an approved read-only operator method, without exposing secrets.
- **Expected:** Published items visible; draft/suspended items hidden; RTL/LTR correct.
- **Evidence to capture:** request/correlation ID, sanitized screenshot or CLI output, authoritative state, test timestamp, PASS/FAIL/BLOCKED and defect link.

### E2E-DISC-002 — Customer signup and verification

- **Initial status:** NOT_RUN · **Role:** New customer C · **Prerequisite:** Unused synthetic identity.
- **Steps:**
  1. Record the current build SHA, selected account/role and isolate the synthetic fixture for this case.
  2. Open login; then register C through approved IdP flow; then perform verification if enabled; then return to web.
  3. Check both visible UI state and the relevant protected API result; refresh/reopen where applicable.
  4. Inspect authoritative DB/audit/capacity/finance evidence with an approved read-only operator method, without exposing secrets.
- **Expected:** Only customer privileges granted; no staff/kitchen privileges.
- **Evidence to capture:** request/correlation ID, sanitized screenshot or CLI output, authoritative state, test timestamp, PASS/FAIL/BLOCKED and defect link.

### E2E-DISC-003 — Customer sign-in/logout

- **Initial status:** NOT_RUN · **Role:** Customer A · **Prerequisite:** Active synthetic A.
- **Steps:**
  1. Record the current build SHA, selected account/role and isolate the synthetic fixture for this case.
  2. Sign in via IdP; then open profile; then log out; then use browser back to protected order.
  3. Check both visible UI state and the relevant protected API result; refresh/reopen where applicable.
  4. Inspect authoritative DB/audit/capacity/finance evidence with an approved read-only operator method, without exposing secrets.
- **Expected:** Protected data unavailable after sign-out.
- **Evidence to capture:** request/correlation ID, sanitized screenshot or CLI output, authoritative state, test timestamp, PASS/FAIL/BLOCKED and defect link.

### E2E-DISC-004 — Switch customer identity

- **Initial status:** NOT_RUN · **Role:** Customers A and B · **Prerequisite:** A has basket/order, B has distinct data.
- **Steps:**
  1. Record the current build SHA, selected account/role and isolate the synthetic fixture for this case.
  2. Sign in A; then populate basket/open orders; then sign out; then sign in B; then inspect basket/orders and browser history.
  3. Check both visible UI state and the relevant protected API result; refresh/reopen where applicable.
  4. Inspect authoritative DB/audit/capacity/finance evidence with an approved read-only operator method, without exposing secrets.
- **Expected:** No A data visible; caches and persisted state separated.
- **Evidence to capture:** request/correlation ID, sanitized screenshot or CLI output, authoritative state, test timestamp, PASS/FAIL/BLOCKED and defect link.

### E2E-DISC-005 — Browse unpublished/suspended

- **Initial status:** NOT_RUN · **Role:** Guest · **Prerequisite:** Draft and suspended kitchens.
- **Steps:**
  1. Record the current build SHA, selected account/role and isolate the synthetic fixture for this case.
  2. Search draft meal ID/name; then open direct URL; then query API if available.
  3. Check both visible UI state and the relevant protected API result; refresh/reopen where applicable.
  4. Inspect authoritative DB/audit/capacity/finance evidence with an approved read-only operator method, without exposing secrets.
- **Expected:** No unapproved listing exposed.
- **Evidence to capture:** request/correlation ID, sanitized screenshot or CLI output, authoritative state, test timestamp, PASS/FAIL/BLOCKED and defect link.

### E2E-DISC-006 — Search and filters

- **Initial status:** NOT_RUN · **Role:** Customer A · **Prerequisite:** Mixed categories, availability and zones.
- **Steps:**
  1. Record the current build SHA, selected account/role and isolate the synthetic fixture for this case.
  2. Search keyword; then combine category, date, price and kitchen filters; then reset; then paginate.
  3. Check both visible UI state and the relevant protected API result; refresh/reopen where applicable.
  4. Inspect authoritative DB/audit/capacity/finance evidence with an approved read-only operator method, without exposing secrets.
- **Expected:** All result sets honor filters and scope; counts accurate.
- **Evidence to capture:** request/correlation ID, sanitized screenshot or CLI output, authoritative state, test timestamp, PASS/FAIL/BLOCKED and defect link.

### E2E-DISC-007 — Unavailable service zone

- **Initial status:** NOT_RUN · **Role:** Customer A · **Prerequisite:** Address outside coverage.
- **Steps:**
  1. Record the current build SHA, selected account/role and isolate the synthetic fixture for this case.
  2. Select outside-zone address; then build quote; then attempt checkout.
  3. Check both visible UI state and the relevant protected API result; refresh/reopen where applicable.
  4. Inspect authoritative DB/audit/capacity/finance evidence with an approved read-only operator method, without exposing secrets.
- **Expected:** Checkout blocked with explainable serviceability feedback.
- **Evidence to capture:** request/correlation ID, sanitized screenshot or CLI output, authoritative state, test timestamp, PASS/FAIL/BLOCKED and defect link.

### E2E-DISC-008 — Language and accessibility

- **Initial status:** NOT_RUN · **Role:** Customer A · **Prerequisite:** Arabic/English, long product names.
- **Steps:**
  1. Record the current build SHA, selected account/role and isolate the synthetic fixture for this case.
  2. Change locale, 200% dynamic text, keyboard and screen reader navigation.
  3. Check both visible UI state and the relevant protected API result; refresh/reopen where applicable.
  4. Inspect authoritative DB/audit/capacity/finance evidence with an approved read-only operator method, without exposing secrets.
- **Expected:** Layouts usable; accessible labels and predictable focus.
- **Evidence to capture:** request/correlation ID, sanitized screenshot or CLI output, authoritative state, test timestamp, PASS/FAIL/BLOCKED and defect link.


## Basket, quote, prices and inventory

### E2E-BASKET-009 — Add and edit basket

- **Initial status:** NOT_RUN · **Role:** Customer A · **Prerequisite:** Meal with sufficient capacity.
- **Steps:**
  1. Record the current build SHA, selected account/role and isolate the synthetic fixture for this case.
  2. Select meal/slot; then add quantity 2; then change to 3; then remove; then refresh.
  3. Check both visible UI state and the relevant protected API result; refresh/reopen where applicable.
  4. Inspect authoritative DB/audit/capacity/finance evidence with an approved read-only operator method, without exposing secrets.
- **Expected:** Line, totals and persistence consistent.
- **Evidence to capture:** request/correlation ID, sanitized screenshot or CLI output, authoritative state, test timestamp, PASS/FAIL/BLOCKED and defect link.

### E2E-BASKET-010 — One kitchen/slot per order

- **Initial status:** NOT_RUN · **Role:** Customer A · **Prerequisite:** Meals from two kitchens or slots.
- **Steps:**
  1. Record the current build SHA, selected account/role and isolate the synthetic fixture for this case.
  2. Add kitchen A item; then attempt item from kitchen B or another slot; then inspect replace/confirmation.
  3. Check both visible UI state and the relevant protected API result; refresh/reopen where applicable.
  4. Inspect authoritative DB/audit/capacity/finance evidence with an approved read-only operator method, without exposing secrets.
- **Expected:** Enforced single kitchen/slot; no silent merging.
- **Evidence to capture:** request/correlation ID, sanitized screenshot or CLI output, authoritative state, test timestamp, PASS/FAIL/BLOCKED and defect link.

### E2E-BASKET-011 — Quantity upper bound

- **Initial status:** NOT_RUN · **Role:** Customer A · **Prerequisite:** Meal max quantity configured.
- **Steps:**
  1. Record the current build SHA, selected account/role and isolate the synthetic fixture for this case.
  2. Select max permitted; then attempt max+1 through UI and API.
  3. Check both visible UI state and the relevant protected API result; refresh/reopen where applicable.
  4. Inspect authoritative DB/audit/capacity/finance evidence with an approved read-only operator method, without exposing secrets.
- **Expected:** Valid max works; over limit rejected server-side.
- **Evidence to capture:** request/correlation ID, sanitized screenshot or CLI output, authoritative state, test timestamp, PASS/FAIL/BLOCKED and defect link.

### E2E-BASKET-012 — Sold out and cutoff

- **Initial status:** NOT_RUN · **Role:** Customer A · **Prerequisite:** Zero-capacity, expired slot.
- **Steps:**
  1. Record the current build SHA, selected account/role and isolate the synthetic fixture for this case.
  2. Open sold-out and cutoff meals; then attempt quote and placement.
  3. Check both visible UI state and the relevant protected API result; refresh/reopen where applicable.
  4. Inspect authoritative DB/audit/capacity/finance evidence with an approved read-only operator method, without exposing secrets.
- **Expected:** No order; actionable validation; capacities unchanged.
- **Evidence to capture:** request/correlation ID, sanitized screenshot or CLI output, authoritative state, test timestamp, PASS/FAIL/BLOCKED and defect link.

### E2E-BASKET-013 — Quote expiry

- **Initial status:** NOT_RUN · **Role:** Customer A · **Prerequisite:** Quote lifespan 5 minutes.
- **Steps:**
  1. Record the current build SHA, selected account/role and isolate the synthetic fixture for this case.
  2. Quote meal; then wait beyond expiry or advance test clock; then submit old quote.
  3. Check both visible UI state and the relevant protected API result; refresh/reopen where applicable.
  4. Inspect authoritative DB/audit/capacity/finance evidence with an approved read-only operator method, without exposing secrets.
- **Expected:** Stale quote rejected; requires new quote.
- **Evidence to capture:** request/correlation ID, sanitized screenshot or CLI output, authoritative state, test timestamp, PASS/FAIL/BLOCKED and defect link.

### E2E-BASKET-014 — Price changed before submit

- **Initial status:** NOT_RUN · **Role:** Customer A + Cook A · **Prerequisite:** Versioned price, unsent quote.
- **Steps:**
  1. Record the current build SHA, selected account/role and isolate the synthetic fixture for this case.
  2. Get quote at old price; then cook changes price; then submit original quote; then refresh and explicitly accept.
  3. Check both visible UI state and the relevant protected API result; refresh/reopen where applicable.
  4. Inspect authoritative DB/audit/capacity/finance evidence with an approved read-only operator method, without exposing secrets.
- **Expected:** Old terms rejected; updated total shown and consent required.
- **Evidence to capture:** request/correlation ID, sanitized screenshot or CLI output, authoritative state, test timestamp, PASS/FAIL/BLOCKED and defect link.

### E2E-BASKET-015 — Price changed after order

- **Initial status:** NOT_RUN · **Role:** Customer A + Cook A · **Prerequisite:** Confirmed order.
- **Steps:**
  1. Record the current build SHA, selected account/role and isolate the synthetic fixture for this case.
  2. Place order; then kitchen changes listed price; then reopen existing order and receipt.
  3. Check both visible UI state and the relevant protected API result; refresh/reopen where applicable.
  4. Inspect authoritative DB/audit/capacity/finance evidence with an approved read-only operator method, without exposing secrets.
- **Expected:** Order line totals remain immutable at original committed price.
- **Evidence to capture:** request/correlation ID, sanitized screenshot or CLI output, authoritative state, test timestamp, PASS/FAIL/BLOCKED and defect link.

### E2E-BASKET-016 — Racing final portion

- **Initial status:** NOT_RUN · **Role:** Customers A and B · **Prerequisite:** One remaining portion.
- **Steps:**
  1. Record the current build SHA, selected account/role and isolate the synthetic fixture for this case.
  2. Use synchronized requests from A/B to submit matching quotes.
  3. Check both visible UI state and the relevant protected API result; refresh/reopen where applicable.
  4. Inspect authoritative DB/audit/capacity/finance evidence with an approved read-only operator method, without exposing secrets.
- **Expected:** Exactly one order succeeds; no oversell or negative capacity.
- **Evidence to capture:** request/correlation ID, sanitized screenshot or CLI output, authoritative state, test timestamp, PASS/FAIL/BLOCKED and defect link.

### E2E-BASKET-017 — Cross-meal kitchen capacity

- **Initial status:** NOT_RUN · **Role:** Customers A/B · **Prerequisite:** Same cook slot aggregate capacity 1.
- **Steps:**
  1. Record the current build SHA, selected account/role and isolate the synthetic fixture for this case.
  2. Simultaneously place two different meals from same kitchen slot.
  3. Check both visible UI state and the relevant protected API result; refresh/reopen where applicable.
  4. Inspect authoritative DB/audit/capacity/finance evidence with an approved read-only operator method, without exposing secrets.
- **Expected:** Aggregate cap enforced atomically.
- **Evidence to capture:** request/correlation ID, sanitized screenshot or CLI output, authoritative state, test timestamp, PASS/FAIL/BLOCKED and defect link.

### E2E-BASKET-018 — Partial reservation rollback

- **Initial status:** NOT_RUN · **Role:** Customer A · **Prerequisite:** Two lines, one insufficient.
- **Steps:**
  1. Record the current build SHA, selected account/role and isolate the synthetic fixture for this case.
  2. Attempt placement where only one line fits; then inspect stock and orders.
  3. Check both visible UI state and the relevant protected API result; refresh/reopen where applicable.
  4. Inspect authoritative DB/audit/capacity/finance evidence with an approved read-only operator method, without exposing secrets.
- **Expected:** All-or-nothing rollback: zero reservations/orders.
- **Evidence to capture:** request/correlation ID, sanitized screenshot or CLI output, authoritative state, test timestamp, PASS/FAIL/BLOCKED and defect link.

### E2E-BASKET-019 — Quote recalculation

- **Initial status:** NOT_RUN · **Role:** Customer A · **Prerequisite:** Different zone/fulfillment fee.
- **Steps:**
  1. Record the current build SHA, selected account/role and isolate the synthetic fixture for this case.
  2. Compare pickup vs delivery; then change address; then obtain new quote.
  3. Check both visible UI state and the relevant protected API result; refresh/reopen where applicable.
  4. Inspect authoritative DB/audit/capacity/finance evidence with an approved read-only operator method, without exposing secrets.
- **Expected:** Server controls charges; total breakdown matches selected terms.
- **Evidence to capture:** request/correlation ID, sanitized screenshot or CLI output, authoritative state, test timestamp, PASS/FAIL/BLOCKED and defect link.

### E2E-BASKET-020 — Client amount manipulation

- **Initial status:** NOT_RUN · **Role:** Customer A · **Prerequisite:** Valid quote.
- **Steps:**
  1. Record the current build SHA, selected account/role and isolate the synthetic fixture for this case.
  2. Intercept request and replace price/total with discount; then submit.
  3. Check both visible UI state and the relevant protected API result; refresh/reopen where applicable.
  4. Inspect authoritative DB/audit/capacity/finance evidence with an approved read-only operator method, without exposing secrets.
- **Expected:** Server ignores/rejects client financial fields; no underpayment.
- **Evidence to capture:** request/correlation ID, sanitized screenshot or CLI output, authoritative state, test timestamp, PASS/FAIL/BLOCKED and defect link.


## Order placement and fulfillment

### E2E-ORDER-021 — Customer order placement

- **Initial status:** NOT_RUN · **Role:** Customer A · **Prerequisite:** Valid quote, approved cook, available capacity.
- **Steps:**
  1. Record the current build SHA, selected account/role and isolate the synthetic fixture for this case.
  2. Select kitchen+slot; then review terms; then place once; then open order details.
  3. Check both visible UI state and the relevant protected API result; refresh/reopen where applicable.
  4. Inspect authoritative DB/audit/capacity/finance evidence with an approved read-only operator method, without exposing secrets.
- **Expected:** PENDING_ACCEPTANCE with locked prices, capacity reserved, visible timeline.
- **Evidence to capture:** request/correlation ID, sanitized screenshot or CLI output, authoritative state, test timestamp, PASS/FAIL/BLOCKED and defect link.

### E2E-ORDER-022 — Duplicate submit same key

- **Initial status:** NOT_RUN · **Role:** Customer A · **Prerequisite:** Valid quote and idempotency key K.
- **Steps:**
  1. Record the current build SHA, selected account/role and isolate the synthetic fixture for this case.
  2. Send same placement twice with K, including after timeout.
  3. Check both visible UI state and the relevant protected API result; refresh/reopen where applicable.
  4. Inspect authoritative DB/audit/capacity/finance evidence with an approved read-only operator method, without exposing secrets.
- **Expected:** Same order/result; one reservation and financial event.
- **Evidence to capture:** request/correlation ID, sanitized screenshot or CLI output, authoritative state, test timestamp, PASS/FAIL/BLOCKED and defect link.

### E2E-ORDER-023 — Same key altered payload

- **Initial status:** NOT_RUN · **Role:** Customer A · **Prerequisite:** Idempotency key K with prior order.
- **Steps:**
  1. Record the current build SHA, selected account/role and isolate the synthetic fixture for this case.
  2. Reuse K with different quantity/address before and after cache expiration.
  3. Check both visible UI state and the relevant protected API result; refresh/reopen where applicable.
  4. Inspect authoritative DB/audit/capacity/finance evidence with an approved read-only operator method, without exposing secrets.
- **Expected:** Deterministic conflict; original order unchanged.
- **Evidence to capture:** request/correlation ID, sanitized screenshot or CLI output, authoritative state, test timestamp, PASS/FAIL/BLOCKED and defect link.

### E2E-ORDER-024 — Unknown network response recovery

- **Initial status:** NOT_RUN · **Role:** Customer A · **Prerequisite:** Injected response loss after DB commit.
- **Steps:**
  1. Record the current build SHA, selected account/role and isolate the synthetic fixture for this case.
  2. Submit then drop client response; then relaunch; then recover status with same request id.
  3. Check both visible UI state and the relevant protected API result; refresh/reopen where applicable.
  4. Inspect authoritative DB/audit/capacity/finance evidence with an approved read-only operator method, without exposing secrets.
- **Expected:** No duplicate order, correct existing order recovered.
- **Evidence to capture:** request/correlation ID, sanitized screenshot or CLI output, authoritative state, test timestamp, PASS/FAIL/BLOCKED and defect link.

### E2E-ORDER-025 — Cook acceptance

- **Initial status:** NOT_RUN · **Role:** Cook A · **Prerequisite:** Own pending order before deadline.
- **Steps:**
  1. Record the current build SHA, selected account/role and isolate the synthetic fixture for this case.
  2. Open kitchen dashboard; then accept order; then check customer timeline.
  3. Check both visible UI state and the relevant protected API result; refresh/reopen where applicable.
  4. Inspect authoritative DB/audit/capacity/finance evidence with an approved read-only operator method, without exposing secrets.
- **Expected:** ACCEPTED exactly once, assigned to owning kitchen.
- **Evidence to capture:** request/correlation ID, sanitized screenshot or CLI output, authoritative state, test timestamp, PASS/FAIL/BLOCKED and defect link.

### E2E-ORDER-026 — Cook rejection

- **Initial status:** NOT_RUN · **Role:** Cook A · **Prerequisite:** Own pending order.
- **Steps:**
  1. Record the current build SHA, selected account/role and isolate the synthetic fixture for this case.
  2. Reject with required reason; then inspect customer status and capacity.
  3. Check both visible UI state and the relevant protected API result; refresh/reopen where applicable.
  4. Inspect authoritative DB/audit/capacity/finance evidence with an approved read-only operator method, without exposing secrets.
- **Expected:** REJECTED; reservation released exactly once.
- **Evidence to capture:** request/correlation ID, sanitized screenshot or CLI output, authoritative state, test timestamp, PASS/FAIL/BLOCKED and defect link.

### E2E-ORDER-027 — Customer cancellation pending

- **Initial status:** NOT_RUN · **Role:** Customer A · **Prerequisite:** Own pending order.
- **Steps:**
  1. Record the current build SHA, selected account/role and isolate the synthetic fixture for this case.
  2. Cancel before acceptance; then refresh on kitchen dashboard.
  3. Check both visible UI state and the relevant protected API result; refresh/reopen where applicable.
  4. Inspect authoritative DB/audit/capacity/finance evidence with an approved read-only operator method, without exposing secrets.
- **Expected:** CANCELLED; one capacity release.
- **Evidence to capture:** request/correlation ID, sanitized screenshot or CLI output, authoritative state, test timestamp, PASS/FAIL/BLOCKED and defect link.

### E2E-ORDER-028 — Accept vs cancel race

- **Initial status:** NOT_RUN · **Role:** Cook A + Customer A · **Prerequisite:** Pending order.
- **Steps:**
  1. Record the current build SHA, selected account/role and isolate the synthetic fixture for this case.
  2. Trigger synchronized accept and cancel; then inspect final state.
  3. Check both visible UI state and the relevant protected API result; refresh/reopen where applicable.
  4. Inspect authoritative DB/audit/capacity/finance evidence with an approved read-only operator method, without exposing secrets.
- **Expected:** Exactly one transition; loser gets conflict/current state.
- **Evidence to capture:** request/correlation ID, sanitized screenshot or CLI output, authoritative state, test timestamp, PASS/FAIL/BLOCKED and defect link.

### E2E-ORDER-029 — Order expiration

- **Initial status:** NOT_RUN · **Role:** Clock/system · **Prerequisite:** Pending order at acceptBy boundary.
- **Steps:**
  1. Record the current build SHA, selected account/role and isolate the synthetic fixture for this case.
  2. Advance controlled clock; then process expiry; then try cook accept.
  3. Check both visible UI state and the relevant protected API result; refresh/reopen where applicable.
  4. Inspect authoritative DB/audit/capacity/finance evidence with an approved read-only operator method, without exposing secrets.
- **Expected:** EXPIRED if deadline wins; capacity released once.
- **Evidence to capture:** request/correlation ID, sanitized screenshot or CLI output, authoritative state, test timestamp, PASS/FAIL/BLOCKED and defect link.

### E2E-ORDER-030 — Preparation to ready

- **Initial status:** NOT_RUN · **Role:** Cook A · **Prerequisite:** ACCEPTED own order.
- **Steps:**
  1. Record the current build SHA, selected account/role and isolate the synthetic fixture for this case.
  2. Start preparation; then mark READY; then inspect code presentation.
  3. Check both visible UI state and the relevant protected API result; refresh/reopen where applicable.
  4. Inspect authoritative DB/audit/capacity/finance evidence with an approved read-only operator method, without exposing secrets.
- **Expected:** PREPARING then READY; capacity consumed; code only visible to owner.
- **Evidence to capture:** request/correlation ID, sanitized screenshot or CLI output, authoritative state, test timestamp, PASS/FAIL/BLOCKED and defect link.

### E2E-ORDER-031 — Pickup handover

- **Initial status:** NOT_RUN · **Role:** Cook A + Customer A · **Prerequisite:** READY pickup order.
- **Steps:**
  1. Record the current build SHA, selected account/role and isolate the synthetic fixture for this case.
  2. Customer views code; then cook enters code; then mark completed.
  3. Check both visible UI state and the relevant protected API result; refresh/reopen where applicable.
  4. Inspect authoritative DB/audit/capacity/finance evidence with an approved read-only operator method, without exposing secrets.
- **Expected:** COMPLETED once; code consumed; auditable receipt.
- **Evidence to capture:** request/correlation ID, sanitized screenshot or CLI output, authoritative state, test timestamp, PASS/FAIL/BLOCKED and defect link.

### E2E-ORDER-032 — Delivery handover

- **Initial status:** NOT_RUN · **Role:** Cook A + Customer A · **Prerequisite:** READY delivery order.
- **Steps:**
  1. Record the current build SHA, selected account/role and isolate the synthetic fixture for this case.
  2. Dispatch; then show handover code; then deliver; then verify and complete.
  3. Check both visible UI state and the relevant protected API result; refresh/reopen where applicable.
  4. Inspect authoritative DB/audit/capacity/finance evidence with an approved read-only operator method, without exposing secrets.
- **Expected:** OUT_FOR_DELIVERY then COMPLETED, correct mode enforced.
- **Evidence to capture:** request/correlation ID, sanitized screenshot or CLI output, authoritative state, test timestamp, PASS/FAIL/BLOCKED and defect link.

### E2E-ORDER-033 — Invalid handover attempts

- **Initial status:** NOT_RUN · **Role:** Cook A · **Prerequisite:** READY order code.
- **Steps:**
  1. Record the current build SHA, selected account/role and isolate the synthetic fixture for this case.
  2. Try wrong code five times and then correct code.
  3. Check both visible UI state and the relevant protected API result; refresh/reopen where applicable.
  4. Inspect authoritative DB/audit/capacity/finance evidence with an approved read-only operator method, without exposing secrets.
- **Expected:** Challenge locked/rate-limited; secure recovery required.
- **Evidence to capture:** request/correlation ID, sanitized screenshot or CLI output, authoritative state, test timestamp, PASS/FAIL/BLOCKED and defect link.

### E2E-ORDER-034 — Delivery failure

- **Initial status:** NOT_RUN · **Role:** Cook A · **Prerequisite:** OUT_FOR_DELIVERY.
- **Steps:**
  1. Record the current build SHA, selected account/role and isolate the synthetic fixture for this case.
  2. Record failed delivery with reason/evidence; then inspect support workflow.
  3. Check both visible UI state and the relevant protected API result; refresh/reopen where applicable.
  4. Inspect authoritative DB/audit/capacity/finance evidence with an approved read-only operator method, without exposing secrets.
- **Expected:** DELIVERY_FAILED; no silent refund or commission.
- **Evidence to capture:** request/correlation ID, sanitized screenshot or CLI output, authoritative state, test timestamp, PASS/FAIL/BLOCKED and defect link.

### E2E-ORDER-035 — Cancel after preparation

- **Initial status:** NOT_RUN · **Role:** Customer A + Ops · **Prerequisite:** PREPARING order.
- **Steps:**
  1. Record the current build SHA, selected account/role and isolate the synthetic fixture for this case.
  2. Customer requests cancel; then ops handles with reason/case.
  3. Check both visible UI state and the relevant protected API result; refresh/reopen where applicable.
  4. Inspect authoritative DB/audit/capacity/finance evidence with an approved read-only operator method, without exposing secrets.
- **Expected:** Customer cannot force state; audited exception, capacity not auto-restored.
- **Evidence to capture:** request/correlation ID, sanitized screenshot or CLI output, authoritative state, test timestamp, PASS/FAIL/BLOCKED and defect link.

### E2E-ORDER-036 — Out-of-order transitions

- **Initial status:** NOT_RUN · **Role:** Cook A · **Prerequisite:** READY/COMPLETED order.
- **Steps:**
  1. Record the current build SHA, selected account/role and isolate the synthetic fixture for this case.
  2. Attempt PREPARING after READY, accept after COMPLETED.
  3. Check both visible UI state and the relevant protected API result; refresh/reopen where applicable.
  4. Inspect authoritative DB/audit/capacity/finance evidence with an approved read-only operator method, without exposing secrets.
- **Expected:** 409 or documented conflict; history unchanged.
- **Evidence to capture:** request/correlation ID, sanitized screenshot or CLI output, authoritative state, test timestamp, PASS/FAIL/BLOCKED and defect link.

### E2E-ORDER-037 — Cross-kitchen order isolation

- **Initial status:** NOT_RUN · **Role:** Cook B · **Prerequisite:** Cook A order.
- **Steps:**
  1. Record the current build SHA, selected account/role and isolate the synthetic fixture for this case.
  2. Attempt reading/accepting/completing A order using B session.
  3. Check both visible UI state and the relevant protected API result; refresh/reopen where applicable.
  4. Inspect authoritative DB/audit/capacity/finance evidence with an approved read-only operator method, without exposing secrets.
- **Expected:** Denied at server; no customer private data.
- **Evidence to capture:** request/correlation ID, sanitized screenshot or CLI output, authoritative state, test timestamp, PASS/FAIL/BLOCKED and defect link.


## Deposits, cash and reconciliation

### E2E-FIN-038 — Cash on delivery

- **Initial status:** NOT_RUN · **Role:** Customer A + Cook A · **Prerequisite:** Order COD, no deposit.
- **Steps:**
  1. Record the current build SHA, selected account/role and isolate the synthetic fixture for this case.
  2. Place and fulfill; then record expected cash received.
  3. Check both visible UI state and the relevant protected API result; refresh/reopen where applicable.
  4. Inspect authoritative DB/audit/capacity/finance evidence with an approved read-only operator method, without exposing secrets.
- **Expected:** Outstanding amount equals agreed total; no Matbakhy fee.
- **Evidence to capture:** request/correlation ID, sanitized screenshot or CLI output, authoritative state, test timestamp, PASS/FAIL/BLOCKED and defect link.

### E2E-FIN-039 — Deposit partially paid

- **Initial status:** NOT_RUN · **Role:** Customer A + Cook A · **Prerequisite:** Deposit supported in synthetic preview.
- **Steps:**
  1. Record the current build SHA, selected account/role and isolate the synthetic fixture for this case.
  2. Create obligation; then cook marks receipt; then complete order; then inspect remaining cash.
  3. Check both visible UI state and the relevant protected API result; refresh/reopen where applicable.
  4. Inspect authoritative DB/audit/capacity/finance evidence with an approved read-only operator method, without exposing secrets.
- **Expected:** Outstanding cash = total minus acknowledged deposit, no dispute false positive.
- **Evidence to capture:** request/correlation ID, sanitized screenshot or CLI output, authoritative state, test timestamp, PASS/FAIL/BLOCKED and defect link.

### E2E-FIN-040 — Deposit not acknowledged

- **Initial status:** NOT_RUN · **Role:** Customer A + Cook A · **Prerequisite:** Pending direct deposit report.
- **Steps:**
  1. Record the current build SHA, selected account/role and isolate the synthetic fixture for this case.
  2. Customer reports payment; then kitchen disagrees; then view status.
  3. Check both visible UI state and the relevant protected API result; refresh/reopen where applicable.
  4. Inspect authoritative DB/audit/capacity/finance evidence with an approved read-only operator method, without exposing secrets.
- **Expected:** Not treated as provider-verified; dispute workflow visible.
- **Evidence to capture:** request/correlation ID, sanitized screenshot or CLI output, authoritative state, test timestamp, PASS/FAIL/BLOCKED and defect link.

### E2E-FIN-041 — Deposit greater than total

- **Initial status:** NOT_RUN · **Role:** Customer A · **Prerequisite:** Synthetic order.
- **Steps:**
  1. Record the current build SHA, selected account/role and isolate the synthetic fixture for this case.
  2. Attempt deposit > payable through interface/API.
  3. Check both visible UI state and the relevant protected API result; refresh/reopen where applicable.
  4. Inspect authoritative DB/audit/capacity/finance evidence with an approved read-only operator method, without exposing secrets.
- **Expected:** Invalid; no negative residual.
- **Evidence to capture:** request/correlation ID, sanitized screenshot or CLI output, authoritative state, test timestamp, PASS/FAIL/BLOCKED and defect link.

### E2E-FIN-042 — Duplicate payment reporting

- **Initial status:** NOT_RUN · **Role:** Cook A · **Prerequisite:** Same cash collection source event.
- **Steps:**
  1. Record the current build SHA, selected account/role and isolate the synthetic fixture for this case.
  2. Submit receipt/collection twice with same event key.
  3. Check both visible UI state and the relevant protected API result; refresh/reopen where applicable.
  4. Inspect authoritative DB/audit/capacity/finance evidence with an approved read-only operator method, without exposing secrets.
- **Expected:** No duplicate ledger entries.
- **Evidence to capture:** request/correlation ID, sanitized screenshot or CLI output, authoritative state, test timestamp, PASS/FAIL/BLOCKED and defect link.

### E2E-FIN-043 — Cash shortfall dispute

- **Initial status:** NOT_RUN · **Role:** Cook A + Finance · **Prerequisite:** Order cash due > collected.
- **Steps:**
  1. Record the current build SHA, selected account/role and isolate the synthetic fixture for this case.
  2. Record lower cash with evidence and reason; then finance reviews.
  3. Check both visible UI state and the relevant protected API result; refresh/reopen where applicable.
  4. Inspect authoritative DB/audit/capacity/finance evidence with an approved read-only operator method, without exposing secrets.
- **Expected:** DISPUTED/exception records preserve original order snapshot.
- **Evidence to capture:** request/correlation ID, sanitized screenshot or CLI output, authoritative state, test timestamp, PASS/FAIL/BLOCKED and defect link.

### E2E-FIN-044 — Refund approval authorization

- **Initial status:** NOT_RUN · **Role:** Finance + Customer · **Prerequisite:** Refund request in scope.
- **Steps:**
  1. Record the current build SHA, selected account/role and isolate the synthetic fixture for this case.
  2. Customer requests refund; then unauthorized staff approve attempt; then finance handles.
  3. Check both visible UI state and the relevant protected API result; refresh/reopen where applicable.
  4. Inspect authoritative DB/audit/capacity/finance evidence with an approved read-only operator method, without exposing secrets.
- **Expected:** Granular permission enforced and audited.
- **Evidence to capture:** request/correlation ID, sanitized screenshot or CLI output, authoritative state, test timestamp, PASS/FAIL/BLOCKED and defect link.

### E2E-FIN-045 — Ledger and reconciliation invariant

- **Initial status:** NOT_RUN · **Role:** Finance · **Prerequisite:** Synthetic mix of deposits/refunds.
- **Steps:**
  1. Record the current build SHA, selected account/role and isolate the synthetic fixture for this case.
  2. View ledger export for known orders; then compare expected debits/credits.
  3. Check both visible UI state and the relevant protected API result; refresh/reopen where applicable.
  4. Inspect authoritative DB/audit/capacity/finance evidence with an approved read-only operator method, without exposing secrets.
- **Expected:** Balanced ledger; no duplicate source events; correct EGP minor units.
- **Evidence to capture:** request/correlation ID, sanitized screenshot or CLI output, authoritative state, test timestamp, PASS/FAIL/BLOCKED and defect link.

### E2E-FIN-046 — Zero platform charges

- **Initial status:** NOT_RUN · **Role:** Customer A · **Prerequisite:** Default free preview policy.
- **Steps:**
  1. Record the current build SHA, selected account/role and isolate the synthetic fixture for this case.
  2. Quote multiple orders in pickup and delivery modes.
  3. Check both visible UI state and the relevant protected API result; refresh/reopen where applicable.
  4. Inspect authoritative DB/audit/capacity/finance evidence with an approved read-only operator method, without exposing secrets.
- **Expected:** Matbakhy commissions and platform fees = 0.
- **Evidence to capture:** request/correlation ID, sanitized screenshot or CLI output, authoritative state, test timestamp, PASS/FAIL/BLOCKED and defect link.

### E2E-FIN-047 — No external money movement

- **Initial status:** NOT_RUN · **Role:** Finance · **Prerequisite:** Preview fake providers.
- **Steps:**
  1. Record the current build SHA, selected account/role and isolate the synthetic fixture for this case.
  2. Trigger finance journeys; then inspect external provider network/events.
  3. Check both visible UI state and the relevant protected API result; refresh/reopen where applicable.
  4. Inspect authoritative DB/audit/capacity/finance evidence with an approved read-only operator method, without exposing secrets.
- **Expected:** Only synthetic bookkeeping; no real payment, payout, SMS, email.
- **Evidence to capture:** request/correlation ID, sanitized screenshot or CLI output, authoritative state, test timestamp, PASS/FAIL/BLOCKED and defect link.


## Cook onboarding and operations

### E2E-COOK-048 — Kitchen application

- **Initial status:** NOT_RUN · **Role:** Customer A · **Prerequisite:** No kitchen assigned.
- **Steps:**
  1. Record the current build SHA, selected account/role and isolate the synthetic fixture for this case.
  2. Open apply-as-cook; then submit kitchen details and permitted documents.
  3. Check both visible UI state and the relevant protected API result; refresh/reopen where applicable.
  4. Inspect authoritative DB/audit/capacity/finance evidence with an approved read-only operator method, without exposing secrets.
- **Expected:** Pending application, not automatically approved.
- **Evidence to capture:** request/correlation ID, sanitized screenshot or CLI output, authoritative state, test timestamp, PASS/FAIL/BLOCKED and defect link.

### E2E-COOK-049 — Kitchen approval

- **Initial status:** NOT_RUN · **Role:** Ops reviewer · **Prerequisite:** Pending cook and evidence.
- **Steps:**
  1. Record the current build SHA, selected account/role and isolate the synthetic fixture for this case.
  2. Review application; then approve with reason; then inspect listing.
  3. Check both visible UI state and the relevant protected API result; refresh/reopen where applicable.
  4. Inspect authoritative DB/audit/capacity/finance evidence with an approved read-only operator method, without exposing secrets.
- **Expected:** Cook becomes eligible for publishing only after approval.
- **Evidence to capture:** request/correlation ID, sanitized screenshot or CLI output, authoritative state, test timestamp, PASS/FAIL/BLOCKED and defect link.

### E2E-COOK-050 — Kitchen suspension

- **Initial status:** NOT_RUN · **Role:** Ops + Cook · **Prerequisite:** Approved kitchen with prior orders.
- **Steps:**
  1. Record the current build SHA, selected account/role and isolate the synthetic fixture for this case.
  2. Suspend kitchen; then attempt new quote/order, then fulfill previously accepted order.
  3. Check both visible UI state and the relevant protected API result; refresh/reopen where applicable.
  4. Inspect authoritative DB/audit/capacity/finance evidence with an approved read-only operator method, without exposing secrets.
- **Expected:** No new sales/acceptance; existing obligations handled safely.
- **Evidence to capture:** request/correlation ID, sanitized screenshot or CLI output, authoritative state, test timestamp, PASS/FAIL/BLOCKED and defect link.

### E2E-COOK-051 — Meal publishing

- **Initial status:** NOT_RUN · **Role:** Cook A · **Prerequisite:** Approved kitchen.
- **Steps:**
  1. Record the current build SHA, selected account/role and isolate the synthetic fixture for this case.
  2. Create draft meal; then submit required allergens/prices/portion limits; then publish.
  3. Check both visible UI state and the relevant protected API result; refresh/reopen where applicable.
  4. Inspect authoritative DB/audit/capacity/finance evidence with an approved read-only operator method, without exposing secrets.
- **Expected:** Incomplete draft cannot publish; public listing appears only when valid.
- **Evidence to capture:** request/correlation ID, sanitized screenshot or CLI output, authoritative state, test timestamp, PASS/FAIL/BLOCKED and defect link.

### E2E-COOK-052 — Slot management

- **Initial status:** NOT_RUN · **Role:** Cook A · **Prerequisite:** Future slot and lead time.
- **Steps:**
  1. Record the current build SHA, selected account/role and isolate the synthetic fixture for this case.
  2. Create slot; then update capacity; then attempt cutoff/negative capacity.
  3. Check both visible UI state and the relevant protected API result; refresh/reopen where applicable.
  4. Inspect authoritative DB/audit/capacity/finance evidence with an approved read-only operator method, without exposing secrets.
- **Expected:** Only valid future availability accepted.
- **Evidence to capture:** request/correlation ID, sanitized screenshot or CLI output, authoritative state, test timestamp, PASS/FAIL/BLOCKED and defect link.

### E2E-COOK-053 — Kitchen image/document security

- **Initial status:** NOT_RUN · **Role:** Cook A + Ops · **Prerequisite:** Approved private doc and upload ticket.
- **Steps:**
  1. Record the current build SHA, selected account/role and isolate the synthetic fixture for this case.
  2. Upload document; then approve; then try replaying old presigned upload URL.
  3. Check both visible UI state and the relevant protected API result; refresh/reopen where applicable.
  4. Inspect authoritative DB/audit/capacity/finance evidence with an approved read-only operator method, without exposing secrets.
- **Expected:** Approved document immutable; old ticket invalid; access scoped.
- **Evidence to capture:** request/correlation ID, sanitized screenshot or CLI output, authoritative state, test timestamp, PASS/FAIL/BLOCKED and defect link.

### E2E-COOK-054 — Cross-kitchen editing

- **Initial status:** NOT_RUN · **Role:** Cook B · **Prerequisite:** Meal and slot belonging to A.
- **Steps:**
  1. Record the current build SHA, selected account/role and isolate the synthetic fixture for this case.
  2. Attempt A meal price/slot mutation as B.
  3. Check both visible UI state and the relevant protected API result; refresh/reopen where applicable.
  4. Inspect authoritative DB/audit/capacity/finance evidence with an approved read-only operator method, without exposing secrets.
- **Expected:** Denied; data unchanged.
- **Evidence to capture:** request/correlation ID, sanitized screenshot or CLI output, authoritative state, test timestamp, PASS/FAIL/BLOCKED and defect link.


## Admin, moderation and support

### E2E-OPS-055 — Staff permission boundary

- **Initial status:** NOT_RUN · **Role:** Support/Ops/Finance · **Prerequisite:** Distinct grants.
- **Steps:**
  1. Record the current build SHA, selected account/role and isolate the synthetic fixture for this case.
  2. Perform finance/admin actions with unrelated staff roles.
  3. Check both visible UI state and the relevant protected API result; refresh/reopen where applicable.
  4. Inspect authoritative DB/audit/capacity/finance evidence with an approved read-only operator method, without exposing secrets.
- **Expected:** Denied, audited; UI/API consistent.
- **Evidence to capture:** request/correlation ID, sanitized screenshot or CLI output, authoritative state, test timestamp, PASS/FAIL/BLOCKED and defect link.

### E2E-OPS-056 — Cook moderation

- **Initial status:** NOT_RUN · **Role:** Ops · **Prerequisite:** Reported kitchen/meal.
- **Steps:**
  1. Record the current build SHA, selected account/role and isolate the synthetic fixture for this case.
  2. Hide suspicious meal, record reason, re-open permitted review.
  3. Check both visible UI state and the relevant protected API result; refresh/reopen where applicable.
  4. Inspect authoritative DB/audit/capacity/finance evidence with an approved read-only operator method, without exposing secrets.
- **Expected:** Public visibility changes according to moderation, audited.
- **Evidence to capture:** request/correlation ID, sanitized screenshot or CLI output, authoritative state, test timestamp, PASS/FAIL/BLOCKED and defect link.

### E2E-OPS-057 — Customer case

- **Initial status:** NOT_RUN · **Role:** Support · **Prerequisite:** Own/assigned case.
- **Steps:**
  1. Record the current build SHA, selected account/role and isolate the synthetic fixture for this case.
  2. Open case, respond, assign, inspect private fields.
  3. Check both visible UI state and the relevant protected API result; refresh/reopen where applicable.
  4. Inspect authoritative DB/audit/capacity/finance evidence with an approved read-only operator method, without exposing secrets.
- **Expected:** Least-privilege views; masked data for unrelated staff.
- **Evidence to capture:** request/correlation ID, sanitized screenshot or CLI output, authoritative state, test timestamp, PASS/FAIL/BLOCKED and defect link.

### E2E-OPS-058 — Role elevation prevention

- **Initial status:** NOT_RUN · **Role:** Customer A · **Prerequisite:** Ordinary customer.
- **Steps:**
  1. Record the current build SHA, selected account/role and isolate the synthetic fixture for this case.
  2. Edit client role claim; then attempt staff endpoints; then request staff grant.
  3. Check both visible UI state and the relevant protected API result; refresh/reopen where applicable.
  4. Inspect authoritative DB/audit/capacity/finance evidence with an approved read-only operator method, without exposing secrets.
- **Expected:** Denied server-side, no self-escalation.
- **Evidence to capture:** request/correlation ID, sanitized screenshot or CLI output, authoritative state, test timestamp, PASS/FAIL/BLOCKED and defect link.

### E2E-OPS-059 — Privileged audit event

- **Initial status:** NOT_RUN · **Role:** Admin · **Prerequisite:** Staff action requiring reason.
- **Steps:**
  1. Record the current build SHA, selected account/role and isolate the synthetic fixture for this case.
  2. Perform permitted action after fresh MFA; then inspect audit.
  3. Check both visible UI state and the relevant protected API result; refresh/reopen where applicable.
  4. Inspect authoritative DB/audit/capacity/finance evidence with an approved read-only operator method, without exposing secrets.
- **Expected:** Actor, timestamp, target, reason recorded; no tokens/secrets.
- **Evidence to capture:** request/correlation ID, sanitized screenshot or CLI output, authoritative state, test timestamp, PASS/FAIL/BLOCKED and defect link.

### E2E-OPS-060 — Last admin protection

- **Initial status:** NOT_RUN · **Role:** Admin · **Prerequisite:** One remaining recoverable admin.
- **Steps:**
  1. Record the current build SHA, selected account/role and isolate the synthetic fixture for this case.
  2. Attempt remove last admin grant.
  3. Check both visible UI state and the relevant protected API result; refresh/reopen where applicable.
  4. Inspect authoritative DB/audit/capacity/finance evidence with an approved read-only operator method, without exposing secrets.
- **Expected:** Rejected without destroying recovery path.
- **Evidence to capture:** request/correlation ID, sanitized screenshot or CLI output, authoritative state, test timestamp, PASS/FAIL/BLOCKED and defect link.

### E2E-OPS-061 — Global config version conflict

- **Initial status:** NOT_RUN · **Role:** Admin A/B · **Prerequisite:** Same config version v.
- **Steps:**
  1. Record the current build SHA, selected account/role and isolate the synthetic fixture for this case.
  2. Both edit policy using expectedVersion=v.
  3. Check both visible UI state and the relevant protected API result; refresh/reopen where applicable.
  4. Inspect authoritative DB/audit/capacity/finance evidence with an approved read-only operator method, without exposing secrets.
- **Expected:** First succeeds; second conflict and refresh prompt.
- **Evidence to capture:** request/correlation ID, sanitized screenshot or CLI output, authoritative state, test timestamp, PASS/FAIL/BLOCKED and defect link.

### E2E-OPS-062 — Public operations login boundary

- **Initial status:** NOT_RUN · **Role:** Guest · **Prerequisite:** Public operations URL.
- **Steps:**
  1. Record the current build SHA, selected account/role and isolate the synthetic fixture for this case.
  2. Open ops page; then attempt privileged API without sign-in.
  3. Check both visible UI state and the relevant protected API result; refresh/reopen where applicable.
  4. Inspect authoritative DB/audit/capacity/finance evidence with an approved read-only operator method, without exposing secrets.
- **Expected:** Page accessible, protected data/actions not accessible.
- **Evidence to capture:** request/correlation ID, sanitized screenshot or CLI output, authoritative state, test timestamp, PASS/FAIL/BLOCKED and defect link.

