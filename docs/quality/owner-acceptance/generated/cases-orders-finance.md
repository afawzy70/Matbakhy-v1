# Owner E2E cases — orders-finance

Generated from `cases/*.json` by `scripts/owner_e2e_catalog.py`; do not edit. Every case is NOT_RUN or BLOCKED until a run records evidence.

### E2E-ORDER-021 — Placement stores an immutable snapshot, locked prices and reserves capacity in one transaction (pickup, COD)

- **Status:** NOT_RUN · **Priority:** P0 · **Readiness:** Ready to run manually now
- **Surface:** customer-web, api · **Actors:** FX-CUST-A
- **Traces to:** rules: BR-019, BR-020, BR-022, BR-027; decisions: D05, D10; operations: postQuotes, postOrders, getOrdersById, getOrdersEvents; partial: TC-022, TC-117, TC-118, TC-157
- **Prerequisites:**
  - Preview stack reachable: customer web https://app.<PREVIEW_DOMAIN>, cook/staff web https://ops.<PREVIEW_DOMAIN>, API https://api.<PREVIEW_DOMAIN>. seed-preview.sql is applied: on the customer web home, kitchen 'مطبخ المعاينة' lists 'Preview koshari' (EGP 75.00) and 'Preview mahshi' (EGP 180.00), and kitchen 'مطبخ تجريبي تاني' lists 'Preview molokhia' (EGP 120.25).
  - FX-CUST-A signs in (password from the private fixture sheet) and GET /api/v1/me returns phoneVerified=true.
  - Kitchen A (00000000-0000-4000-8000-0000000bd001) has OPEN 13:00 Africa/Cairo slots for the next 3 days: in the DevTools Console on app.<PREVIEW_DOMAIN> `(await api('GET','/api/v1/kitchens/00000000-0000-4000-8000-0000000bd001/slots')).body.items` has >= 3 items and items[0].availablePortions >= 6 (seed: 20 portions per slot, 10 per meal).
  - In the DevTools Console of the signed-in tab, define the helper `api` from conventions.api_helper (needed for the API steps).
  - A read-only database method approved by the operator is available (psql through the operator channel); replace :'order' with the order UUID.
  - Record BEFORE placing, from SQL: SELECT cs.reserved, ms.reserved FROM cook_slot cs JOIN meal_slot ms ON ms.cook_slot_id = cs.id WHERE cs.id = '<slot id>' AND ms.meal_id = '00000000-0000-4000-8000-0000000bb001'; (call these R_slot0 and R_meal0).
- **Steps:**
  1. (FX-CUST-A) Customer web: add 2 × 'Preview koshari' (13:00 tomorrow) to the cart, mode 'Pickup from the kitchen', payment 'Cash on receipt', press 'Calculate total'. Read the quote panel, then tick the terms box and press 'Place order' once. — data: slot = items[0] of kitchen A
  2. (FX-CUST-A) On the order page copy the order id from the URL (#/orders/<uuid>). In the Console run GET /api/v1/orders/<id> and GET /api/v1/orders/<id>/events. — data: api('GET','/api/v1/orders/<id>'), api('GET','/api/v1/orders/<id>/events')
  3. (operator) Run the read-only SQL listed under expected.database with :'order' = the order id.
- **Expected:**
  - UI: Quote panel: Meals 150.00, Delivery fee 0.00, Tax 0.00, Total 150.00, text 'Pay in cash on receipt.', notice 'Synthetic policy (test environment)'. Order page: '<MB-number> · Waiting for the kitchen', '2 × Preview koshari — 150.00', timeline with one line, no handover code, no deposit panel.
  - API: GET order 200: state PENDING_ACCEPTANCE, version 0 (or the initial version returned by POST), items[0].quantity 2, unitPrice.amountMinor 7500, lineTotal 15000, subtotal 15000, deliveryFee 0, tax 0, total 15000 (currency EGP), cashState DUE, payment.method COD, payment.state NOT_REQUIRED, handoverCode null, contact null, allowedActions ['CANCEL'], acceptBy about 15 minutes after the placement time.
  - API: GET events 200: exactly one event {sequence 1, fromState null, toState PENDING_ACCEPTANCE, actorType CUSTOMER}.
  - DB: SELECT state, version, subtotal_minor, delivery_fee_minor, tax_minor, total_minor, commission_bps_snapshot, commission_minor, accept_by, terminal_at FROM customer_order WHERE id = :'order'; -> state PENDING_ACCEPTANCE, total_minor 15000, commission_bps_snapshot 0, commission_minor 0.
  - DB: SELECT quantity, unit_price_minor, line_total_minor, meal_snapshot->>'titleEn', meal_snapshot->>'priceMinor' FROM order_item WHERE order_id = :'order'; -> one row 2, 7500, 15000, 'Preview koshari', '7500'.
  - DB: SELECT state, quantity, released_at FROM stock_allocation WHERE order_id = :'order'; -> one row RESERVED quantity 2, released_at null. cook_slot.reserved = R_slot0 + 2 and meal_slot.reserved = R_meal0 + 2 (SELECT cs.reserved, cs.allocated, cs.consumed, cs.max_portions FROM cook_slot cs JOIN customer_order o ON o.slot_id = cs.id WHERE o.id = :'order';).
  - DB: SELECT amount_minor, state FROM cash_obligation WHERE order_id = :'order'; -> amount_minor 15000 DUE.  SELECT method, state, total_minor, deposit_minor, remainder_minor, deposit_expires_at, version FROM order_payment WHERE order_id = :'order'; -> COD, NOT_REQUIRED, deposit_minor 0, remainder_minor 15000.
  - DB: SELECT event_type, status FROM outbox_event WHERE aggregate_id = :'order' ORDER BY created_at; -> one ORDER_PLACED row.  SELECT sequence, from_state, to_state, actor_type, reason_code FROM order_event WHERE order_id = :'order' ORDER BY sequence; -> one row.
- **Also covered by automated tests:** OrderPlacementIntegrationTest; QuoteIntegrationTest; ZeroCommissionIntegrationTest#a_launch_order_carries_zero_commission_from_quote_to_ledger_and_statement
- **Unverified against code (confirm while executing):** acceptBy is min(createdAt+15 min, slot cutoff) per QuoteService.terms; the UI does not show acceptBy to the customer, so it is asserted through the API only.

### E2E-ORDER-022 — Replaying the same placement request with the same Idempotency-Key returns the same order and does not reserve twice

- **Status:** NOT_RUN · **Priority:** P0 · **Readiness:** Ready to run manually now
- **Surface:** api · **Actors:** FX-CUST-A
- **Traces to:** rules: BR-023, BR-024; decisions: D05; operations: postOrders, getOrdersByRequestById, getOrders; partial: TC-023, TC-024, TC-044, TC-118
- **Prerequisites:**
  - Preview stack reachable: customer web https://app.<PREVIEW_DOMAIN>, cook/staff web https://ops.<PREVIEW_DOMAIN>, API https://api.<PREVIEW_DOMAIN>. seed-preview.sql is applied: on the customer web home, kitchen 'مطبخ المعاينة' lists 'Preview koshari' (EGP 75.00) and 'Preview mahshi' (EGP 180.00), and kitchen 'مطبخ تجريبي تاني' lists 'Preview molokhia' (EGP 120.25).
  - FX-CUST-A signs in (password from the private fixture sheet) and GET /api/v1/me returns phoneVerified=true.
  - Kitchen A (00000000-0000-4000-8000-0000000bd001) has OPEN 13:00 Africa/Cairo slots for the next 3 days: in the DevTools Console on app.<PREVIEW_DOMAIN> `(await api('GET','/api/v1/kitchens/00000000-0000-4000-8000-0000000bd001/slots')).body.items` has >= 3 items and items[0].availablePortions >= 6 (seed: 20 portions per slot, 10 per meal).
  - In the DevTools Console of the signed-in tab, define the helper `api` from conventions.api_helper (needed for the API steps).
  - A read-only database method approved by the operator is available (psql through the operator channel); replace :'order' with the order UUID.
  - Generate once in the console: `const K=crypto.randomUUID(), C=crypto.randomUUID();` (Idempotency-Key must match [A-Za-z0-9-]{8,128}, JdbcIdempotentCommands).
- **Steps:**
  1. (FX-CUST-A) Create one quote (2 × koshari, PICKUP, COD) as in conventions.recipes.place_pickup_cod_x2 but do NOT place yet. Record SQL: cook_slot.reserved before (R0). — data: q = quote body
  2. (FX-CUST-A) POST /api/v1/orders with Idempotency-Key K and body {quoteId:q.quoteId, clientRequestId:C, acceptedPolicyVersions:{terms:q.termsVersion, cancellation:q.cancellationPolicyVersion}}. Save the response as r1. — data: key=K
  3. (FX-CUST-A) Repeat exactly the same request with the same K (r2), then a third time (r3). — data: identical body and key
  4. (FX-CUST-A) GET /api/v1/orders?limit=50 and GET /api/v1/orders/by-request/<C>.
- **Expected:**
  - UI: Customer web 'My orders' lists the order once (open https://app.<PREVIEW_DOMAIN>/#/orders).
  - API: r1.status 201. r2.status and r3.status 201 with the same body.id and same publicNumber as r1 (no new MB- number).
  - API: by-request returns 200 with that id; the orders list contains exactly one order with this id.
  - DB: SELECT count(*) FROM customer_order WHERE customer_id = (SELECT id FROM app_user WHERE display_name = 'Preview Customer 1') AND client_request_id = '<C>'; -> 1.
  - DB: SELECT count(*) FROM stock_allocation WHERE order_id = '<id>'; -> 1 and cook_slot.reserved = R0 + 2 (not R0 + 6).
  - DB: SELECT count(*) FROM outbox_event WHERE aggregate_id = '<id>' AND event_type = 'ORDER_PLACED'; -> 1.
  - DB: SELECT count(*) FROM idempotency_record WHERE command = 'order.place' AND idempotency_key = '<K>'; -> 1 (expires_at >= created_at + 24 h).
- **Also covered by automated tests:** OrderPlacementIntegrationTest
- **Unverified against code (confirm while executing):** A replay returns the CURRENT view of the order (state may have advanced); this case keeps the order pending so the body is identical.

### E2E-ORDER-023 — Reusing an Idempotency-Key with a different request is refused; keys are scoped to actor + command

- **Status:** NOT_RUN · **Priority:** P1 · **Readiness:** Ready to run manually now
- **Surface:** api · **Actors:** FX-CUST-A
- **Traces to:** scenarios: TC-024; rules: BR-024; decisions: D05; operations: postOrders, postOrdersCancel
- **Prerequisites:**
  - Preview stack reachable: customer web https://app.<PREVIEW_DOMAIN>, cook/staff web https://ops.<PREVIEW_DOMAIN>, API https://api.<PREVIEW_DOMAIN>. seed-preview.sql is applied: on the customer web home, kitchen 'مطبخ المعاينة' lists 'Preview koshari' (EGP 75.00) and 'Preview mahshi' (EGP 180.00), and kitchen 'مطبخ تجريبي تاني' lists 'Preview molokhia' (EGP 120.25).
  - FX-CUST-A signs in (password from the private fixture sheet) and GET /api/v1/me returns phoneVerified=true.
  - Kitchen A (00000000-0000-4000-8000-0000000bd001) has OPEN 13:00 Africa/Cairo slots for the next 3 days: in the DevTools Console on app.<PREVIEW_DOMAIN> `(await api('GET','/api/v1/kitchens/00000000-0000-4000-8000-0000000bd001/slots')).body.items` has >= 3 items and items[0].availablePortions >= 6 (seed: 20 portions per slot, 10 per meal).
  - In the DevTools Console of the signed-in tab, define the helper `api` from conventions.api_helper (needed for the API steps).
  - A read-only database method approved by the operator is available (psql through the operator channel); replace :'order' with the order UUID.
- **Steps:**
  1. (FX-CUST-A) Create quote q1 (2 × koshari) and quote q2 (1 × koshari), both PICKUP/COD. Place q1 with Idempotency-Key K and clientRequestId C1. — data: K=crypto.randomUUID()
  2. (FX-CUST-A) POST /api/v1/orders with the SAME Idempotency-Key K but quoteId q2 and clientRequestId C2. — data: different body
  3. (FX-CUST-A) POST /api/v1/orders/<order1 id>/cancel with the SAME key K as the Idempotency-Key and body {expectedVersion:<version>, reasonCode:'CHANGED_MIND'}. — data: key=K, command differs
  4. (operator) Run the read-only SQL.
- **Expected:**
  - UI: Not applicable (API only).
  - API: Step 2: 409 with body.code IDEMPOTENCY_CONFLICT (message key error.idempotency_conflict), retryable false.
  - API: Step 3: 200 (state CANCELLED): the same key is accepted for a different command, so the scope is actor + command + key.
  - DB: No customer_order exists for client_request_id = C2. idempotency_record has one row for (actor, 'order.place', K) and one for (actor, 'order.customer_cancel', K).
  - DB: Order 1 is CANCELLED after step 3 and its allocation RELEASED (cleanup).
- **Also covered by automated tests:** OrderPlacementIntegrationTest

### E2E-ORDER-024 — After an unknown outcome (offline during submit), retrying creates exactly one order

- **Status:** NOT_RUN · **Priority:** P1 · **Readiness:** Ready to run manually now
- **Surface:** customer-web, api · **Actors:** FX-CUST-A
- **Traces to:** rules: BR-044, BR-023; decisions: D13; operations: postOrders, getOrdersByRequestById; partial: TC-023, TC-044
- **Prerequisites:**
  - Preview stack reachable: customer web https://app.<PREVIEW_DOMAIN>, cook/staff web https://ops.<PREVIEW_DOMAIN>, API https://api.<PREVIEW_DOMAIN>. seed-preview.sql is applied: on the customer web home, kitchen 'مطبخ المعاينة' lists 'Preview koshari' (EGP 75.00) and 'Preview mahshi' (EGP 180.00), and kitchen 'مطبخ تجريبي تاني' lists 'Preview molokhia' (EGP 120.25).
  - FX-CUST-A signs in (password from the private fixture sheet) and GET /api/v1/me returns phoneVerified=true.
  - Kitchen A (00000000-0000-4000-8000-0000000bd001) has OPEN 13:00 Africa/Cairo slots for the next 3 days: in the DevTools Console on app.<PREVIEW_DOMAIN> `(await api('GET','/api/v1/kitchens/00000000-0000-4000-8000-0000000bd001/slots')).body.items` has >= 3 items and items[0].availablePortions >= 6 (seed: 20 portions per slot, 10 per meal).
  - In the DevTools Console of the signed-in tab, define the helper `api` from conventions.api_helper (needed for the API steps).
  - A read-only database method approved by the operator is available (psql through the operator channel); replace :'order' with the order UUID.
  - Chrome/Edge DevTools available (Network > throttling 'Offline').
- **Steps:**
  1. (FX-CUST-A) Customer web: add 1 × 'Preview koshari' (13:00 tomorrow), open 'Cart', choose 'Pickup from the kitchen', 'Calculate total', tick terms.
  2. (FX-CUST-A) DevTools > Network > set 'Offline'. Press 'Place order'.
  3. (FX-CUST-A) Set throttling back to 'No throttling'. Reload the page (F5), open 'Cart'.
  4. (FX-CUST-A) Press 'Retry the same order'.
  5. (FX-CUST-A) API variant (post-commit loss): in the console place an order with key K and clientRequestId C, ignore the response; then GET /api/v1/orders/by-request/<C>, then POST the identical placement again with K.
- **Expected:**
  - UI: Step 2: red/notice text 'We could not confirm your order. Retry the same order; it will not be duplicated.' and a button 'Retry the same order'; the basket quantity/remove buttons are disabled (basket locked).
  - UI: Step 3-4: after the retry the browser navigates to the order page '#/orders/<uuid>' showing 'Waiting for the kitchen'; 'My orders' lists ONE new order.
  - API: Step 5: by-request returns 200 with the order that the first call committed; the second POST returns 201 with the same id.
  - DB: SELECT count(*) FROM customer_order WHERE customer_id = <FX-CUST-A id> AND created_at > now() - interval '15 minutes'; -> 1 for the UI part, +1 for the API part (2 in total, never 3).
  - DB: cook_slot.reserved increased once per order (1 + 2 portions).
- **Also covered by automated tests:** apps/customer-web/src/App.test.tsx (stable key persisted before request)
- **Unverified against code (confirm while executing):** Offline click does not prove whether the request reached the server; the assertion is 'one order after retry', not which path happened.; Exact data-testid of the pending notice on web (none found; the mobile screen has testID 'pending-order').

### E2E-ORDER-025 — Cook accepts a pending order once: reservation becomes allocation, customer gets contact and a notification

- **Status:** NOT_RUN · **Priority:** P0 · **Readiness:** Ready to run manually now
- **Surface:** operations-web, customer-web, api · **Actors:** FX-COOK-A, FX-CUST-A
- **Traces to:** rules: BR-026, BR-027, BR-029; decisions: D05; operations: postCookOrdersAccept, getCookOrdersById, getNotifications; partial: TC-006, TC-119, TC-121
- **Prerequisites:**
  - Preview stack reachable: customer web https://app.<PREVIEW_DOMAIN>, cook/staff web https://ops.<PREVIEW_DOMAIN>, API https://api.<PREVIEW_DOMAIN>. seed-preview.sql is applied: on the customer web home, kitchen 'مطبخ المعاينة' lists 'Preview koshari' (EGP 75.00) and 'Preview mahshi' (EGP 180.00), and kitchen 'مطبخ تجريبي تاني' lists 'Preview molokhia' (EGP 120.25).
  - FX-COOK-A signs in at https://ops.<PREVIEW_DOMAIN> and the tab bar shows 'Orders' (ar: الطلبات); GET /api/v1/cook/dashboard in that session returns 200.
  - In the DevTools Console of the signed-in tab, define the helper `api` from conventions.api_helper (needed for the API steps).
  - A read-only database method approved by the operator is available (psql through the operator channel); replace :'order' with the order UUID.
  - A PENDING_ACCEPTANCE pickup/COD order O1 of FX-CUST-A exists (create it with conventions.recipes.place_pickup_cod_x2 in the FX-CUST-A console; keep o.body.id and o.body.version).
  - Acceptance deadline not passed (placed less than 15 minutes ago).
  - Each actor uses a SEPARATE browser profile (or private window/cookie store): customer web, ops web and the API share one session cookie (SecurityConfiguration), so signing in as a second actor in the same profile silently replaces the first. Before each actor's steps run `await api('GET','/api/v1/me')` (helper in conventions.api_helper) and confirm the displayName is that actor.
- **Steps:**
  1. (FX-COOK-A) Ops web > 'Orders' > 'Active': locate O1. Before accepting note that no customer name/phone is shown. Press 'Accept'.
  2. (FX-CUST-A) Reload the order page of O1.
  3. (FX-COOK-A) In the cook console: replay the accept with the SAME Idempotency-Key and expectedVersion (POST /api/v1/cook/orders/<O1>/accept {expectedVersion:v0}); then send accept with a NEW key and the OLD expectedVersion; then with a NEW key and the CURRENT version. — data: v0 = version before accept
  4. (operator) Run the read-only SQL.
- **Expected:**
  - UI: Cook card after Accept: badge 'Accepted', 'Customer: Preview Customer 1 · +201000000101', buttons 'Start preparing' and 'Cancel'. Customer page: '<MB-number> · Accepted', 'Kitchen contact: مطبخ المعاينة · +201000000201 · Pickup location: Synthetic location 1', button 'Request cancellation (reviewed by support)' (no 'Cancel order').
  - API: Replay with the same key: 200 and the same CookOrderView. New key + old version: 409 VERSION_CONFLICT. New key + current version: 409 STATE_CONFLICT with fieldErrors [{state,'ACCEPTED'}].
  - DB: SELECT state, quantity, released_at FROM stock_allocation WHERE order_id = :'order'; -> RESERVED became ALLOCATED (quantity 2); SELECT cs.reserved, cs.allocated, cs.consumed, cs.max_portions FROM cook_slot cs JOIN customer_order o ON o.slot_id = cs.id WHERE o.id = :'order'; reserved decreased by 2 and allocated increased by 2.
  - DB: SELECT sequence, from_state, to_state, actor_type, reason_code FROM order_event WHERE order_id = :'order' ORDER BY sequence; -> sequence 2: PENDING_ACCEPTANCE→ACCEPTED actor COOK. customer_order.version increased by exactly 1.
  - DB: SELECT n.type, n.recipient_id, n.params->>'state' AS state, n.read_at FROM notification n WHERE n.order_id = :'order' ORDER BY n.created_at; -> a notification type ORDER_ACCEPTED for the customer with params.state 'ACCEPTED' within about 5-10 s (dispatch poll 5 s).
- **Also covered by automated tests:** OrderLifecycleIntegrationTest#accept_cancel_and_expire_racing_produce_one_legal_outcome_and_at_most_one_release; OrderLifecycleIntegrationTest#acceptance_is_allowed_one_millisecond_before_the_deadline_and_refused_exactly_at_it

### E2E-ORDER-026 — Cook rejection releases the reservation exactly once and ends the order

- **Status:** NOT_RUN · **Priority:** P1 · **Readiness:** Ready to run manually now
- **Surface:** operations-web, customer-web, api · **Actors:** FX-COOK-A, FX-CUST-A
- **Traces to:** rules: BR-029, BR-026; decisions: D05, D10; operations: postCookOrdersReject; partial: TC-029, TC-119
- **Prerequisites:**
  - Preview stack reachable: customer web https://app.<PREVIEW_DOMAIN>, cook/staff web https://ops.<PREVIEW_DOMAIN>, API https://api.<PREVIEW_DOMAIN>. seed-preview.sql is applied: on the customer web home, kitchen 'مطبخ المعاينة' lists 'Preview koshari' (EGP 75.00) and 'Preview mahshi' (EGP 180.00), and kitchen 'مطبخ تجريبي تاني' lists 'Preview molokhia' (EGP 120.25).
  - FX-COOK-A signs in at https://ops.<PREVIEW_DOMAIN> and the tab bar shows 'Orders' (ar: الطلبات); GET /api/v1/cook/dashboard in that session returns 200.
  - In the DevTools Console of the signed-in tab, define the helper `api` from conventions.api_helper (needed for the API steps).
  - A read-only database method approved by the operator is available (psql through the operator channel); replace :'order' with the order UUID.
  - A PENDING_ACCEPTANCE pickup/COD order O1 of FX-CUST-A exists (create it with conventions.recipes.place_pickup_cod_x2 in the FX-CUST-A console; keep o.body.id and o.body.version).
  - Each actor uses a SEPARATE browser profile (or private window/cookie store): customer web, ops web and the API share one session cookie (SecurityConfiguration), so signing in as a second actor in the same profile silently replaces the first. Before each actor's steps run `await api('GET','/api/v1/me')` (helper in conventions.api_helper) and confirm the displayName is that actor.
- **Steps:**
  1. (FX-COOK-A) Orders > 'Active': on O1 choose Reason 'Kitchen busy' (default) and press 'Reject'.
  2. (FX-CUST-A) Reload the order page.
  3. (FX-COOK-A) Cook console: POST /api/v1/cook/orders/<O1>/reject with a new key, current version and reasonCode 'BAD'; then with a valid reason on the now-terminal order. — data: reasonCode='BAD' then 'OTHER'
  4. (operator) Run the read-only SQL.
- **Expected:**
  - UI: Customer page: '<MB-number> · Rejected by the kitchen', no cancel button; the card disappears from the cook 'Active' list after reload.
  - API: Invalid reason on a terminal order: 409 STATE_CONFLICT (terminal check precedes the reason check). Invalid reason on a pending order (use a fresh order): 422 VALIDATION_FAILED fieldErrors reasonCode 'ALLOWED_INGREDIENT_UNAVAILABLE|KITCHEN_BUSY|OTHER|OUTSIDE_CAPACITY'.
  - DB: SELECT sequence, from_state, to_state, actor_type, reason_code FROM order_event WHERE order_id = :'order' ORDER BY sequence; -> PENDING_ACCEPTANCE→REJECTED actor COOK reason_code KITCHEN_BUSY.
  - DB: SELECT state, quantity, released_at FROM stock_allocation WHERE order_id = :'order'; -> state RELEASED with released_at set; cook_slot.reserved and meal_slot.reserved back to the pre-order values (released once, never negative).
  - DB: SELECT amount_minor, state FROM cash_obligation WHERE order_id = :'order'; -> state NOT_DUE.  SELECT n.type, n.recipient_id, n.params->>'state' AS state, n.read_at FROM notification n WHERE n.order_id = :'order' ORDER BY n.created_at; -> one ORDER_REJECTED notification for the customer (ORDER_REJECTED is a customer event only; the rejecting cook gets none).
- **Also covered by automated tests:** OrderLifecycleIntegrationTest#terminal_orders_and_stale_versions_are_rejected_without_changing_history

### E2E-ORDER-027 — Customer cancels a pending order directly; capacity is released once

- **Status:** NOT_RUN · **Priority:** P1 · **Readiness:** Ready to run manually now
- **Surface:** customer-web, operations-web, api · **Actors:** FX-CUST-A, FX-COOK-A
- **Traces to:** rules: BR-028, BR-029; decisions: D05; operations: postOrdersCancel; partial: TC-028, TC-029
- **Prerequisites:**
  - Preview stack reachable: customer web https://app.<PREVIEW_DOMAIN>, cook/staff web https://ops.<PREVIEW_DOMAIN>, API https://api.<PREVIEW_DOMAIN>. seed-preview.sql is applied: on the customer web home, kitchen 'مطبخ المعاينة' lists 'Preview koshari' (EGP 75.00) and 'Preview mahshi' (EGP 180.00), and kitchen 'مطبخ تجريبي تاني' lists 'Preview molokhia' (EGP 120.25).
  - FX-CUST-A signs in (password from the private fixture sheet) and GET /api/v1/me returns phoneVerified=true.
  - FX-COOK-A signs in at https://ops.<PREVIEW_DOMAIN> and the tab bar shows 'Orders' (ar: الطلبات); GET /api/v1/cook/dashboard in that session returns 200.
  - In the DevTools Console of the signed-in tab, define the helper `api` from conventions.api_helper (needed for the API steps).
  - A read-only database method approved by the operator is available (psql through the operator channel); replace :'order' with the order UUID.
  - A PENDING_ACCEPTANCE pickup/COD order O1 of FX-CUST-A exists (create it with conventions.recipes.place_pickup_cod_x2 in the FX-CUST-A console; keep o.body.id and o.body.version).
  - Each actor uses a SEPARATE browser profile (or private window/cookie store): customer web, ops web and the API share one session cookie (SecurityConfiguration), so signing in as a second actor in the same profile silently replaces the first. Before each actor's steps run `await api('GET','/api/v1/me')` (helper in conventions.api_helper) and confirm the displayName is that actor.
- **Steps:**
  1. (FX-CUST-A) Open the order page of O1 and press 'Cancel order' (ar: إلغاء الطلب). — data: UI sends reasonCode CHANGED_MIND
  2. (FX-COOK-A) Reload Orders > 'Active'.
  3. (FX-CUST-A) Console: cancel again with a new key and the current version.
  4. (operator) Run the read-only SQL.
- **Expected:**
  - UI: Customer page '<MB-number> · Cancelled', no buttons. Cook 'Active' list no longer contains O1.
  - API: Second cancel: 409 STATE_CONFLICT fieldErrors [{state,'CANCELLED'}].
  - DB: SELECT sequence, from_state, to_state, actor_type, reason_code FROM order_event WHERE order_id = :'order' ORDER BY sequence; -> PENDING_ACCEPTANCE→CANCELLED actor CUSTOMER reason_code CHANGED_MIND.
  - DB: SELECT state, quantity, released_at FROM stock_allocation WHERE order_id = :'order'; -> RELEASED once; counters restored.
  - DB: SELECT amount_minor, state FROM cash_obligation WHERE order_id = :'order'; -> NOT_DUE.
  - DB: Notifications: cook (kitchen owner) and customer both receive ORDER_CANCELLED (dedup key type:order per recipient) - SELECT recipient_id, type FROM notification WHERE order_id = :'order';
- **Also covered by automated tests:** OrderLifecycleIntegrationTest#customers_cancel_pending_orders_directly_but_not_accepted_ones

### E2E-ORDER-028 — Accept racing customer cancel (and expiry): exactly one transition wins, release happens at most once

- **Status:** NOT_RUN · **Priority:** P1 · **Readiness:** Isolated disposable stack only
- **Surface:** api · **Actors:** FX-CUST-A, FX-COOK-A · **Needs:** isolated stack
- **Traces to:** rules: BR-026, BR-029, BR-027; decisions: D05; operations: postCookOrdersAccept, postOrdersCancel; partial: TC-029, TC-119
- **Prerequisites:**
  - FX-ISOLATED disposable stack (parallel writes). Never on the shared preview.
  - In the DevTools Console of the signed-in tab, define the helper `api` from conventions.api_helper (needed for the API steps).
  - A read-only database method approved by the operator is available (psql through the operator channel); replace :'order' with the order UUID.
  - Run 20 iterations; each iteration needs a fresh pending order O (recipe) and its version v.
  - Each actor uses a SEPARATE browser profile (or private window/cookie store): customer web, ops web and the API share one session cookie (SecurityConfiguration), so signing in as a second actor in the same profile silently replaces the first. Before each actor's steps run `await api('GET','/api/v1/me')` (helper in conventions.api_helper) and confirm the displayName is that actor.
- **Steps:**
  1. (FX-CUST-A / FX-COOK-A) For each iteration fire POST /api/v1/cook/orders/<O>/accept {expectedVersion:v} (cook session) and POST /api/v1/orders/<O>/cancel {expectedVersion:v, reasonCode:'CHANGED_MIND'} (customer session) at the same instant (two consoles synchronised by a shared start time or a script with two sessions). — data: same version v
  2. (operator) After all iterations run infra/db/reconcile.sql and the SQL for each order.
- **Expected:**
  - UI: Not applicable.
  - API: Per iteration exactly one 200 and one 409 (STATE_CONFLICT or VERSION_CONFLICT) carrying the current state; never two 200.
  - DB: Final state ACCEPTED (allocation ALLOCATED) or CANCELLED (allocation RELEASED once); order_event has exactly one transition after sequence 1; reconcile.sql all zero; no negative counters.
- **Also covered by automated tests:** OrderLifecycleIntegrationTest#accept_cancel_and_expire_racing_produce_one_legal_outcome_and_at_most_one_release

### E2E-ORDER-029 — An order not accepted by its deadline expires by server time and cannot be accepted afterwards

- **Status:** NOT_RUN · **Priority:** P1 · **Readiness:** Ready to run manually now
- **Surface:** operations-web, customer-web, api · **Actors:** FX-CUST-A, FX-COOK-A
- **Traces to:** rules: BR-027, BR-029; decisions: D05; operations: postCookOrdersAccept; partial: TC-029, TC-119, TC-027
- **Prerequisites:**
  - Preview stack reachable: customer web https://app.<PREVIEW_DOMAIN>, cook/staff web https://ops.<PREVIEW_DOMAIN>, API https://api.<PREVIEW_DOMAIN>. seed-preview.sql is applied: on the customer web home, kitchen 'مطبخ المعاينة' lists 'Preview koshari' (EGP 75.00) and 'Preview mahshi' (EGP 180.00), and kitchen 'مطبخ تجريبي تاني' lists 'Preview molokhia' (EGP 120.25).
  - FX-COOK-A signs in at https://ops.<PREVIEW_DOMAIN> and the tab bar shows 'Orders' (ar: الطلبات); GET /api/v1/cook/dashboard in that session returns 200.
  - In the DevTools Console of the signed-in tab, define the helper `api` from conventions.api_helper (needed for the API steps).
  - A read-only database method approved by the operator is available (psql through the operator channel); replace :'order' with the order UUID.
  - A PENDING_ACCEPTANCE pickup/COD order O1 of FX-CUST-A exists (create it with conventions.recipes.place_pickup_cod_x2 in the FX-CUST-A console; keep o.body.id and o.body.version).
  - No clock control is available on the preview: the test waits the real acceptance window (about 15 minutes after placement, plus up to 30 s for the expiry job, matbakhy.orders.expiry-job.interval=30s). The disposable stack may shorten it.
  - Each actor uses a SEPARATE browser profile (or private window/cookie store): customer web, ops web and the API share one session cookie (SecurityConfiguration), so signing in as a second actor in the same profile silently replaces the first. Before each actor's steps run `await api('GET','/api/v1/me')` (helper in conventions.api_helper) and confirm the displayName is that actor.
- **Steps:**
  1. (FX-COOK-A) Open Orders > 'Active' and note on O1 'Accept by <time> (N min left)'. Do not act.
  2. (FX-COOK-A) After the deadline press 'Accept' if the card is still shown, and in the console POST /api/v1/cook/orders/<O1>/accept with current version.
  3. (FX-CUST-A) Reload the order page.
  4. (operator) Run the read-only SQL.
- **Expected:**
  - UI: Before the deadline: 'N min left'; after: 'Deadline passed'. Customer page: '<MB-number> · Not accepted in time'.
  - API: Accept after the deadline: 409 STATE_CONFLICT with fieldErrors [{acceptBy,'DEADLINE_PASSED'}] (job has not run yet) or [{state,'EXPIRED'}] (job ran).
  - DB: SELECT sequence, from_state, to_state, actor_type, reason_code FROM order_event WHERE order_id = :'order' ORDER BY sequence; -> PENDING_ACCEPTANCE→EXPIRED actor SYSTEM reason_code ACCEPTANCE_DEADLINE, occurred_at >= accept_by.
  - DB: SELECT state, quantity, released_at FROM stock_allocation WHERE order_id = :'order'; -> RELEASED once.
  - DB: SELECT amount_minor, state FROM cash_obligation WHERE order_id = :'order'; -> NOT_DUE.
  - DB: Notifications ORDER_EXPIRED for both the customer and the kitchen owner.
- **Also covered by automated tests:** OrderLifecycleIntegrationTest#the_expiry_job_expires_overdue_orders_once_by_server_time

### E2E-ORDER-030 — Preparation consumes stock; READY issues the handover code, visible only to the order owner

- **Status:** NOT_RUN · **Priority:** P0 · **Readiness:** Ready to run manually now
- **Surface:** operations-web, customer-web, api · **Actors:** FX-COOK-A, FX-CUST-A, FX-CUST-B
- **Traces to:** rules: BR-029, BR-031, BR-026; decisions: D05; operations: postCookOrdersPrepare, postCookOrdersReady, getCookOrdersById, getOrdersById; partial: TC-006, TC-031, TC-119
- **Prerequisites:**
  - Preview stack reachable: customer web https://app.<PREVIEW_DOMAIN>, cook/staff web https://ops.<PREVIEW_DOMAIN>, API https://api.<PREVIEW_DOMAIN>. seed-preview.sql is applied: on the customer web home, kitchen 'مطبخ المعاينة' lists 'Preview koshari' (EGP 75.00) and 'Preview mahshi' (EGP 180.00), and kitchen 'مطبخ تجريبي تاني' lists 'Preview molokhia' (EGP 120.25).
  - FX-COOK-A signs in at https://ops.<PREVIEW_DOMAIN> and the tab bar shows 'Orders' (ar: الطلبات); GET /api/v1/cook/dashboard in that session returns 200.
  - In the DevTools Console of the signed-in tab, define the helper `api` from conventions.api_helper (needed for the API steps).
  - A read-only database method approved by the operator is available (psql through the operator channel); replace :'order' with the order UUID.
  - An ACCEPTED pickup/COD order O1 of FX-CUST-A (place per recipe, then FX-COOK-A presses 'Accept').
  - Each actor uses a SEPARATE browser profile (or private window/cookie store): customer web, ops web and the API share one session cookie (SecurityConfiguration), so signing in as a second actor in the same profile silently replaces the first. Before each actor's steps run `await api('GET','/api/v1/me')` (helper in conventions.api_helper) and confirm the displayName is that actor.
- **Steps:**
  1. (FX-COOK-A) On the card press 'Start preparing'. Then press 'Ready'.
  2. (FX-CUST-A) Reload the order page after each press and read the state/code line.
  3. (FX-COOK-A) In the cook console GET /api/v1/cook/orders/<O1> and inspect the JSON keys.
  4. (FX-CUST-B) Console: GET /api/v1/orders/<O1>.
  5. (operator) Run the read-only SQL (do not select the ciphertext column into logs).
- **Expected:**
  - UI: After 'Start preparing': customer state 'Preparing', no code. After 'Ready': 'Ready' and 'Handover code (give it to the cook or courier on receipt)' with 6 digits. The cook card never shows the code.
  - API: Cook JSON has no property 'handoverCode'. FX-CUST-B gets 404 NOT_FOUND. Owner JSON handoverCode matches ^[0-9]{6}$.
  - DB: SELECT state, quantity, released_at FROM stock_allocation WHERE order_id = :'order'; -> CONSUMED; SELECT cs.reserved, cs.allocated, cs.consumed, cs.max_portions FROM cook_slot cs JOIN customer_order o ON o.slot_id = cs.id WHERE o.id = :'order'; allocated decreased by 2, consumed increased by 2.
  - DB: SELECT attempts, expires_at, locked_at, consumed_at, code_hash ~ '^[0-9a-f]{64}$' AS hashed FROM handover_challenge WHERE order_id = :'order'; -> attempts 0, hashed true, locked/consumed null, expires_at = slot ends_at + 2 h (16:00 Cairo on the slot day).
  - DB: SELECT sequence, from_state, to_state, actor_type, reason_code FROM order_event WHERE order_id = :'order' ORDER BY sequence; -> ACCEPTED→PREPARING→READY actor COOK.
- **Also covered by automated tests:** OrderLifecycleIntegrationTest#pickup_order_moves_through_every_stage_with_stock_and_events_in_step

### E2E-ORDER-031 — Pickup handover with the customer's code completes the order once and consumes the code

- **Status:** NOT_RUN · **Priority:** P0 · **Readiness:** Ready to run manually now
- **Surface:** operations-web, customer-web, api · **Actors:** FX-COOK-A, FX-CUST-A
- **Traces to:** rules: BR-031, BR-032, BR-033; decisions: D10; operations: postCookOrdersHandover; partial: TC-031, TC-032, TC-157, TC-030
- **Prerequisites:**
  - Preview stack reachable: customer web https://app.<PREVIEW_DOMAIN>, cook/staff web https://ops.<PREVIEW_DOMAIN>, API https://api.<PREVIEW_DOMAIN>. seed-preview.sql is applied: on the customer web home, kitchen 'مطبخ المعاينة' lists 'Preview koshari' (EGP 75.00) and 'Preview mahshi' (EGP 180.00), and kitchen 'مطبخ تجريبي تاني' lists 'Preview molokhia' (EGP 120.25).
  - FX-COOK-A signs in at https://ops.<PREVIEW_DOMAIN> and the tab bar shows 'Orders' (ar: الطلبات); GET /api/v1/cook/dashboard in that session returns 200.
  - In the DevTools Console of the signed-in tab, define the helper `api` from conventions.api_helper (needed for the API steps).
  - A read-only database method approved by the operator is available (psql through the operator channel); replace :'order' with the order UUID.
  - A READY pickup/COD order O1 (per E2E-ORDER-030). Record the customer's 6-digit code from the order page.
  - Each actor uses a SEPARATE browser profile (or private window/cookie store): customer web, ops web and the API share one session cookie (SecurityConfiguration), so signing in as a second actor in the same profile silently replaces the first. Before each actor's steps run `await api('GET','/api/v1/me')` (helper in conventions.api_helper) and confirm the displayName is that actor.
- **Steps:**
  1. (FX-COOK-A) On the card verify the button 'Confirm handover' is disabled until 6 digits are typed; type the code and press it.
  2. (FX-COOK-A) Console: POST /api/v1/cook/orders/<O1>/handover again with a NEW key and the correct code. — data: code = same digits
  3. (FX-CUST-A) Reload the order page.
  4. (operator) Run the read-only SQL.
- **Expected:**
  - UI: Customer page '<MB-number> · Delivered' (English COMPLETED label also for pickup; ar 'تم التسليم'), handover line gone, 'Rate your order' shown.
  - API: First handover 200 state COMPLETED. Replay with a new key: 409 STATE_CONFLICT (terminal, fieldErrors state COMPLETED); with the SAME key: 200 original result.
  - DB: SELECT consumed_at IS NOT NULL FROM handover_challenge WHERE order_id = :'order'; -> true. SELECT success FROM handover_attempt WHERE order_id = :'order'; -> one row true.
  - DB: SELECT sequence, from_state, to_state, actor_type, reason_code FROM order_event WHERE order_id = :'order' ORDER BY sequence; -> READY→COMPLETED actor COOK.
  - DB: SELECT amount_minor, state FROM cash_obligation WHERE order_id = :'order'; -> DUE (completion is not collection).
  - DB: Within about 30 s: SELECT g.kind, g.rate_bps, g.base_minor, e.account, e.direction, e.amount_minor FROM ledger_group g JOIN ledger_entry e ON e.group_id = g.id WHERE g.order_id = :'order' ORDER BY g.posted_at, e.direction; -> one kind COMMISSION group, rate_bps 0, both entries amount_minor 0 (no commission under D10).
- **Also covered by automated tests:** OrderLifecycleIntegrationTest#a_valid_code_completes_once_and_cannot_be_replayed; ZeroCommissionIntegrationTest
- **Unverified against code (confirm while executing):** The ledger group is posted by LedgerPostingJob every 30 s (matbakhy.finance.posting-job.interval); a zero-amount group still exists although the commission is 0.

### E2E-ORDER-032 — Delivery mode: dispatch needs a courier reference, handover works only from OUT_FOR_DELIVERY, wrong-mode commands are refused

- **Status:** NOT_RUN · **Priority:** P1 · **Readiness:** Ready to run manually now
- **Surface:** operations-web, api · **Actors:** FX-COOK-A, FX-CUST-A
- **Traces to:** rules: BR-030, BR-031; decisions: D05; operations: postCookOrdersDispatch, postCookOrdersHandover; partial: TC-031, TC-119
- **Prerequisites:**
  - Preview stack reachable: customer web https://app.<PREVIEW_DOMAIN>, cook/staff web https://ops.<PREVIEW_DOMAIN>, API https://api.<PREVIEW_DOMAIN>. seed-preview.sql is applied: on the customer web home, kitchen 'مطبخ المعاينة' lists 'Preview koshari' (EGP 75.00) and 'Preview mahshi' (EGP 180.00), and kitchen 'مطبخ تجريبي تاني' lists 'Preview molokhia' (EGP 120.25).
  - FX-COOK-A signs in at https://ops.<PREVIEW_DOMAIN> and the tab bar shows 'Orders' (ar: الطلبات); GET /api/v1/cook/dashboard in that session returns 200.
  - In the DevTools Console of the signed-in tab, define the helper `api` from conventions.api_helper (needed for the API steps).
  - A read-only database method approved by the operator is available (psql through the operator channel); replace :'order' with the order UUID.
  - Two READY orders of FX-CUST-A: D1 DELIVERY (address in Test zone A, total includes fee 30.00) and P1 PICKUP.
  - Each actor uses a SEPARATE browser profile (or private window/cookie store): customer web, ops web and the API share one session cookie (SecurityConfiguration), so signing in as a second actor in the same profile silently replaces the first. Before each actor's steps run `await api('GET','/api/v1/me')` (helper in conventions.api_helper) and confirm the displayName is that actor.
- **Steps:**
  1. (FX-COOK-A) Console: POST /api/v1/cook/orders/<D1>/handover {expectedVersion:v, code:'123456'} while D1 is READY.
  2. (FX-COOK-A) Console: POST /api/v1/cook/orders/<P1>/dispatch {expectedVersion:v, deliveryReference:'COURIER-TEST-01'}.
  3. (FX-COOK-A) Console: dispatch D1 with a blank deliveryReference, then with 'COURIER-TEST-01'.
  4. (FX-COOK-A) On D1 (now OUT_FOR_DELIVERY) confirm handover with the code.
- **Expected:**
  - UI: Cook card of D1 at READY shows 'Hand to courier'; the card of P1 at READY shows 'Confirm handover' only.
  - API: 1: 409 STATE_CONFLICT fieldErrors [{fulfillmentMode,'WRONG_FULFILLMENT_MODE'}]. 2: 409 STATE_CONFLICT WRONG_FULFILLMENT_MODE. 3: blank -> 422 VALIDATION_FAILED deliveryReference REQUIRED; valid -> 200 state OUT_FOR_DELIVERY. 4: 200 COMPLETED.
  - DB: customer_order.dispatch_reference = 'COURIER-TEST-01' for D1 and NULL for P1. Events for D1 end READY→OUT_FOR_DELIVERY→COMPLETED.
- **Also covered by automated tests:** OrderLifecycleIntegrationTest#wrong_fulfillment_paths_are_denied_and_the_correct_path_keeps_history

### E2E-ORDER-033 — Five wrong handover codes lock the challenge; the correct code is then refused and the customer's code disappears

- **Status:** NOT_RUN · **Priority:** P1 · **Readiness:** Ready to run manually now
- **Surface:** operations-web, customer-web, api · **Actors:** FX-COOK-A, FX-CUST-A
- **Traces to:** rules: BR-031; decisions: D05; operations: postCookOrdersHandover; partial: TC-031, TC-119
- **Prerequisites:**
  - Preview stack reachable: customer web https://app.<PREVIEW_DOMAIN>, cook/staff web https://ops.<PREVIEW_DOMAIN>, API https://api.<PREVIEW_DOMAIN>. seed-preview.sql is applied: on the customer web home, kitchen 'مطبخ المعاينة' lists 'Preview koshari' (EGP 75.00) and 'Preview mahshi' (EGP 180.00), and kitchen 'مطبخ تجريبي تاني' lists 'Preview molokhia' (EGP 120.25).
  - FX-COOK-A signs in at https://ops.<PREVIEW_DOMAIN> and the tab bar shows 'Orders' (ar: الطلبات); GET /api/v1/cook/dashboard in that session returns 200.
  - In the DevTools Console of the signed-in tab, define the helper `api` from conventions.api_helper (needed for the API steps).
  - A read-only database method approved by the operator is available (psql through the operator channel); replace :'order' with the order UUID.
  - A READY pickup/COD order O1 (E2E-ORDER-030). Note the true code X from the customer page and choose a wrong code W that differs from X (e.g. 000000).
  - Each actor uses a SEPARATE browser profile (or private window/cookie store): customer web, ops web and the API share one session cookie (SecurityConfiguration), so signing in as a second actor in the same profile silently replaces the first. Before each actor's steps run `await api('GET','/api/v1/me')` (helper in conventions.api_helper) and confirm the displayName is that actor.
- **Steps:**
  1. (FX-COOK-A) Type W in 'Customer handover code' and press 'Confirm handover'. Repeat until five attempts were made (watch the response each time in the Network tab). — data: W=000000
  2. (FX-COOK-A) Now enter the CORRECT code X and press 'Confirm handover'.
  3. (FX-CUST-A) Reload the order page.
  4. (operator) Run the read-only SQL.
- **Expected:**
  - UI: Customer page: state still 'Ready' and the handover code line is NOT shown any more (locked challenge).
  - API: Attempts 1-4: 422 VERIFICATION_FAILED fieldErrors [{code,'WRONG_CODE'}]. Attempt 5: 422 VERIFICATION_FAILED fieldErrors [{code,'CHALLENGE_LOCKED'}]. Correct code afterwards: 409 STATE_CONFLICT fieldErrors [{code,'CHALLENGE_LOCKED'}].
  - DB: SELECT attempts, locked_at IS NOT NULL, consumed_at IS NULL FROM handover_challenge WHERE order_id = :'order'; -> 5, true, true. SELECT count(*) FILTER (WHERE NOT success) FROM handover_attempt WHERE order_id = :'order'; -> 5.
  - DB: Order state READY, no COMPLETED event. Recovery is E2E-ORDER-108.
- **Also covered by automated tests:** OrderLifecycleIntegrationTest#wrong_codes_are_bounded_valid_codes_work_once_and_locked_challenges_need_an_audited_override; OrderLifecycleIntegrationTest#handover_attempts_are_rate_limited_per_actor
- **Unverified against code (confirm while executing):** Cook UI text for VERIFICATION_FAILED (the ops-web errors dictionary was not inspected); assert the API codes in the Network tab.; Per-actor limit (30 attempts per hour per actor) is covered by automated tests only.

### E2E-ORDER-034 — Failed delivery ends the order as DELIVERY_FAILED without automatic refund, payment or commission

- **Status:** NOT_RUN · **Priority:** P1 · **Readiness:** Ready after the extra synthetic fixtures are provisioned
- **Surface:** operations-web, customer-web, api · **Actors:** FX-COOK-A, FX-CUST-A, FX-STAFF-OPS
- **Fixtures to provision:** FX-STAFF-OPS
- **Traces to:** rules: BR-029, BR-032; decisions: D05; operations: postCookOrdersDeliveryFailure, getCookOrders, getOpsOrders; partial: TC-119, TC-124
- **Prerequisites:**
  - Preview stack reachable: customer web https://app.<PREVIEW_DOMAIN>, cook/staff web https://ops.<PREVIEW_DOMAIN>, API https://api.<PREVIEW_DOMAIN>. seed-preview.sql is applied: on the customer web home, kitchen 'مطبخ المعاينة' lists 'Preview koshari' (EGP 75.00) and 'Preview mahshi' (EGP 180.00), and kitchen 'مطبخ تجريبي تاني' lists 'Preview molokhia' (EGP 120.25).
  - FX-COOK-A signs in at https://ops.<PREVIEW_DOMAIN> and the tab bar shows 'Orders' (ar: الطلبات); GET /api/v1/cook/dashboard in that session returns 200.
  - A read-only database method approved by the operator is available (psql through the operator channel); replace :'order' with the order UUID.
  - A delivery/COD order O1 in OUT_FOR_DELIVERY (E2E-ORDER-102 up to the courier step). FX-STAFF-OPS holds order.ops.
  - Each actor uses a SEPARATE browser profile (or private window/cookie store): customer web, ops web and the API share one session cookie (SecurityConfiguration), so signing in as a second actor in the same profile silently replaces the first. Before each actor's steps run `await api('GET','/api/v1/me')` (helper in conventions.api_helper) and confirm the displayName is that actor.
- **Steps:**
  1. (FX-COOK-A) On the card (OUT_FOR_DELIVERY) choose Reason 'Customer unreachable' and press 'Delivery failed'.
  2. (FX-CUST-A) Reload the order page.
  3. (FX-COOK-A) Look for O1 in Orders: try the filters 'Active' and 'Completed'.
  4. (FX-STAFF-OPS) Ops 'Order monitoring' > filter 'Delivery failed'.
- **Expected:**
  - UI: Customer page '<MB-number> · Delivery failed'. The cook UI shows O1 in neither 'Active' nor 'Completed' (the filter list in Orders.tsx has only those two). Ops filter 'Delivery failed' lists O1 with badge 'Delivery failed'.
  - API: Console GET /api/v1/cook/orders?state=DELIVERY_FAILED returns O1.
  - DB: SELECT sequence, from_state, to_state, actor_type, reason_code FROM order_event WHERE order_id = :'order' ORDER BY sequence; -> OUT_FOR_DELIVERY→DELIVERY_FAILED actor COOK reason_code CUSTOMER_UNREACHABLE. terminal_at set.
  - DB: SELECT state, quantity, released_at FROM stock_allocation WHERE order_id = :'order'; -> CONSUMED stays (no automatic restoration).
  - DB: SELECT count(*) FROM refund WHERE order_id = :'order'; -> 0. SELECT count(*) FROM ledger_group WHERE order_id = :'order'; -> 0 (commission posts only for COMPLETED).
  - DB: SELECT amount_minor, state FROM cash_obligation WHERE order_id = :'order'; -> amount_minor 28500 and state remains DUE (the delivery_failure branch does not call cash(); finance review is manual).
- **Also covered by automated tests:** OrderLifecycleIntegrationTest
- **Unverified against code (confirm while executing):** Docs 04-state-machines.md say DELIVERY_FAILED creates a 'support task'; the code opens no support case for cash orders, only the ops exception flag DELIVERY_FAILED (and a PAYMENT case for deposit orders, E2E-FIN-107). Owner decision whether a task is required.; Whether a COD order left in cash state DUE after DELIVERY_FAILED is intended.

### E2E-ORDER-035 — After acceptance the customer can only request cancellation; the order itself does not change

- **Status:** NOT_RUN · **Priority:** P1 · **Readiness:** Ready after the extra synthetic fixtures are provisioned
- **Surface:** customer-web, operations-web, api · **Actors:** FX-CUST-A, FX-COOK-A, FX-STAFF-SUPPORT
- **Fixtures to provision:** FX-STAFF-SUPPORT
- **Traces to:** rules: BR-028, BR-041; decisions: D05; operations: postOrdersCancellationRequests, postOrdersCancel, getOpsCases; partial: TC-028, TC-041, TC-124
- **Prerequisites:**
  - Preview stack reachable: customer web https://app.<PREVIEW_DOMAIN>, cook/staff web https://ops.<PREVIEW_DOMAIN>, API https://api.<PREVIEW_DOMAIN>. seed-preview.sql is applied: on the customer web home, kitchen 'مطبخ المعاينة' lists 'Preview koshari' (EGP 75.00) and 'Preview mahshi' (EGP 180.00), and kitchen 'مطبخ تجريبي تاني' lists 'Preview molokhia' (EGP 120.25).
  - FX-CUST-A signs in (password from the private fixture sheet) and GET /api/v1/me returns phoneVerified=true.
  - In the DevTools Console of the signed-in tab, define the helper `api` from conventions.api_helper (needed for the API steps).
  - A read-only database method approved by the operator is available (psql through the operator channel); replace :'order' with the order UUID.
  - An ACCEPTED pickup/COD order O1 of FX-CUST-A and a PENDING one P1. FX-STAFF-SUPPORT holds support.case (PROVISION).
  - Each actor uses a SEPARATE browser profile (or private window/cookie store): customer web, ops web and the API share one session cookie (SecurityConfiguration), so signing in as a second actor in the same profile silently replaces the first. Before each actor's steps run `await api('GET','/api/v1/me')` (helper in conventions.api_helper) and confirm the displayName is that actor.
- **Steps:**
  1. (FX-CUST-A) Open O1: the only cancel-related control is 'Request cancellation (reviewed by support)'. Press it. — data: UI sends reasonCode CHANGED_MIND
  2. (FX-CUST-A) Console: POST /api/v1/orders/<O1>/cancel {expectedVersion:v, reasonCode:'CHANGED_MIND'}; POST /api/v1/orders/<O1>/cancellation-requests again; POST /api/v1/orders/<P1>/cancellation-requests.
  3. (FX-STAFF-SUPPORT) Ops web > 'Support': find the case (category 'Cancellation request').
  4. (operator) Run the read-only SQL.
- **Expected:**
  - UI: Step 1: text 'Cancellation request sent.' and a link 'Support cases for this order'; the order still says 'Accepted'. Step 3: the case appears with severity 'High'.
  - API: Direct cancel: 409 STATE_CONFLICT fieldErrors [{state,'CANCELLATION_REQUEST_REQUIRED'}]. Second request: 409 STATE_CONFLICT fieldErrors [{orderId,'OPEN_CANCELLATION_REQUEST'}]. Request on a pending order: 409 STATE_CONFLICT fieldErrors [{state,'PENDING_ACCEPTANCE'}].
  - DB: SELECT category, severity, cancellation_reason, state FROM support_case WHERE order_id = :'order'; -> CANCELLATION_REQUEST, HIGH, CHANGED_MIND, OPEN (exactly one open row: unique index support_case_open_cancellation_uq).
  - DB: SELECT state, quantity, released_at FROM stock_allocation WHERE order_id = :'order'; -> ALLOCATED unchanged; customer_order.state ACCEPTED, version unchanged.
- **Also covered by automated tests:** OrderLifecycleIntegrationTest#customers_cancel_pending_orders_directly_but_not_accepted_ones; SupportIntegrationTest

### E2E-ORDER-036 — Out-of-order, stale and replayed commands are refused without changing history

- **Status:** NOT_RUN · **Priority:** P1 · **Readiness:** Ready to run manually now
- **Surface:** api · **Actors:** FX-COOK-A, FX-CUST-A
- **Traces to:** rules: BR-026; decisions: D05; operations: postCookOrdersPrepare, postCookOrdersAccept, postCookOrdersReady, postOrdersCancel; partial: TC-119, TC-026
- **Prerequisites:**
  - Preview stack reachable: customer web https://app.<PREVIEW_DOMAIN>, cook/staff web https://ops.<PREVIEW_DOMAIN>, API https://api.<PREVIEW_DOMAIN>. seed-preview.sql is applied: on the customer web home, kitchen 'مطبخ المعاينة' lists 'Preview koshari' (EGP 75.00) and 'Preview mahshi' (EGP 180.00), and kitchen 'مطبخ تجريبي تاني' lists 'Preview molokhia' (EGP 120.25).
  - In the DevTools Console of the signed-in tab, define the helper `api` from conventions.api_helper (needed for the API steps).
  - A read-only database method approved by the operator is available (psql through the operator channel); replace :'order' with the order UUID.
  - Four orders of FX-CUST-A at kitchen A: P PENDING, A ACCEPTED, R READY pickup, Z COMPLETED. Record order_event counts and customer_order.version for each before starting.
  - Each actor uses a SEPARATE browser profile (or private window/cookie store): customer web, ops web and the API share one session cookie (SecurityConfiguration), so signing in as a second actor in the same profile silently replaces the first. Before each actor's steps run `await api('GET','/api/v1/me')` (helper in conventions.api_helper) and confirm the displayName is that actor.
- **Steps:**
  1. (FX-COOK-A) Send as cook (current version each time, fresh key): prepare on P; ready on A; accept on A; prepare on Z; accept on Z.
  2. (FX-COOK-A) Send accept on P with a stale expectedVersion (v-1) and with expectedVersion omitted; send accept without any Idempotency-Key header.
  3. (FX-CUST-A) Send customer cancel on Z with the current version.
  4. (operator) Re-read event counts/versions.
- **Expected:**
  - UI: Not applicable.
  - API: prepare@P, ready@A, accept@A: 409 STATE_CONFLICT with fieldErrors [{state, <current state>}]. Anything on Z: 409 STATE_CONFLICT with state COMPLETED (terminal check comes before the version check). Stale version: 409 VERSION_CONFLICT. Missing expectedVersion: 422 VALIDATION_FAILED. Missing/malformed key: 400 MALFORMED_REQUEST ('Idempotency-Key missing or malformed').
  - DB: For all four orders order_event count and customer_order.version equal the values recorded before; no new outbox_event rows.
- **Also covered by automated tests:** OrderLifecycleIntegrationTest#terminal_orders_and_stale_versions_are_rejected_without_changing_history
- **Unverified against code (confirm while executing):** Whether an omitted expectedVersion yields 422 (bean validation @NotNull) or 400; confirm in the Network tab.

### E2E-ORDER-037 — A cook cannot read or command another kitchen's order; customers and unprivileged staff cannot call cook/ops endpoints

- **Status:** NOT_RUN · **Priority:** P0 · **Readiness:** Ready to run manually now
- **Surface:** api, operations-web · **Actors:** FX-COOK-A, FX-CUST-A, FX-STAFF-NOGRANT
- **Traces to:** rules: BR-006, BR-001; decisions: D05; operations: getCookOrdersById, postCookOrdersAccept, postCookOrdersPaymentReceipt, getOpsOrders; partial: TC-001, TC-006
- **Prerequisites:**
  - Preview stack reachable: customer web https://app.<PREVIEW_DOMAIN>, cook/staff web https://ops.<PREVIEW_DOMAIN>, API https://api.<PREVIEW_DOMAIN>. seed-preview.sql is applied: on the customer web home, kitchen 'مطبخ المعاينة' lists 'Preview koshari' (EGP 75.00) and 'Preview mahshi' (EGP 180.00), and kitchen 'مطبخ تجريبي تاني' lists 'Preview molokhia' (EGP 120.25).
  - In the DevTools Console of the signed-in tab, define the helper `api` from conventions.api_helper (needed for the API steps).
  - Order OK2 of FX-CUST-A at kitchen B (seed kitchen 'مطبخ تجريبي تاني', 1 × 'Preview molokhia', slot from GET /api/v1/kitchens/00000000-0000-4000-8000-0000000bd002/slots; kitchen B's owner has no login, so FX-COOK-B is not needed) and order OK1 at kitchen A. Both PENDING.
  - FX-COOK-A is a cook of kitchen A only.
  - Each actor uses a SEPARATE browser profile (or private window/cookie store): customer web, ops web and the API share one session cookie (SecurityConfiguration), so signing in as a second actor in the same profile silently replaces the first. Before each actor's steps run `await api('GET','/api/v1/me')` (helper in conventions.api_helper) and confirm the displayName is that actor.
- **Steps:**
  1. (FX-COOK-A) Ops web > 'Orders': confirm OK1 is listed and OK2 is not. In the console: GET /api/v1/cook/orders/<OK2>; POST /api/v1/cook/orders/<OK2>/accept {expectedVersion:0}, /reject {expectedVersion:0, reasonCode:'OTHER'} and /handover {expectedVersion:0, code:'123456'} (each with a fresh Idempotency-Key, XSRF header from the helper); POST /api/v1/cook/orders/<OK2>/payment/receipt {expectedVersion:0, amountMinor:1} (schema-valid body so the 404 comes from the ownership check, not validation).
  2. (FX-CUST-A) Console: GET /api/v1/cook/orders and POST /api/v1/cook/orders/<OK1>/accept {expectedVersion:0} (fresh Idempotency-Key) as the customer.
  3. (FX-COOK-A) Console: POST /api/v1/orders/<OK1>/cancel {expectedVersion:0, reasonCode:'OTHER'} (fresh Idempotency-Key) as the cook (not the owning customer).
  4. (FX-STAFF-NOGRANT) Sign in at ops.<PREVIEW_DOMAIN>: note the visible tabs; console: GET /api/v1/ops/orders and GET /api/v1/ops/payments?state=DISPUTED.
  5. (FX-CUST-A) Cleanup: as FX-CUST-A press 'Cancel order' on OK1 and OK2 (both still pending).
- **Expected:**
  - UI: FX-STAFF-NOGRANT sees no Finance, Order monitoring or Support tabs; the cook's list never contains OK2.
  - API: Cook on OK2: 404 NOT_FOUND for every call (no data about the order). Customer on cook endpoints: 403 FORBIDDEN. Cook cancelling a customer order: 404 NOT_FOUND. No-grant staff: 403 FORBIDDEN.
  - DB: OK2 still PENDING_ACCEPTANCE (version unchanged) until cleanup; order_event for OK2 has one row; audit_event shows no ALLOWED order action by FX-COOK-A for OK2.
- **Also covered by automated tests:** OrderLifecycleIntegrationTest#cooks_act_only_on_their_kitchen_and_suspension_blocks_acceptance_but_not_fulfillment; OrderLifecycleIntegrationTest#the_ordering_customer_cannot_run_cook_commands_and_the_cook_cannot_use_the_customer_cancel; DepositPaymentIntegrationTest#another_approved_kitchen_cannot_receipt_report_a_problem_or_refund_a_foreign_deposit

### E2E-ORDER-101 — Core journey 1: pickup + cash on receipt, from placement to completion and operations inspection

- **Status:** NOT_RUN · **Priority:** P0 · **Readiness:** Ready after the extra synthetic fixtures are provisioned
- **Surface:** customer-web, operations-web · **Actors:** FX-CUST-A, FX-COOK-A, FX-STAFF-OPS
- **Fixtures to provision:** FX-STAFF-OPS
- **Traces to:** scenarios: TC-032; rules: BR-022, BR-026, BR-030, BR-031, BR-032; decisions: D05, D10; operations: postQuotes, postOrders, getOrdersById, postCookOrdersAccept, postCookOrdersPrepare, postCookOrdersReady, postCookOrdersHandover, getOpsOrdersById; partial: TC-022, TC-031, TC-117, TC-119, TC-124
- **Prerequisites:**
  - Preview stack reachable: customer web https://app.<PREVIEW_DOMAIN>, cook/staff web https://ops.<PREVIEW_DOMAIN>, API https://api.<PREVIEW_DOMAIN>. seed-preview.sql is applied: on the customer web home, kitchen 'مطبخ المعاينة' lists 'Preview koshari' (EGP 75.00) and 'Preview mahshi' (EGP 180.00), and kitchen 'مطبخ تجريبي تاني' lists 'Preview molokhia' (EGP 120.25).
  - FX-CUST-A signs in (password from the private fixture sheet) and GET /api/v1/me returns phoneVerified=true.
  - FX-COOK-A signs in at https://ops.<PREVIEW_DOMAIN> and the tab bar shows 'Orders' (ar: الطلبات); GET /api/v1/cook/dashboard in that session returns 200.
  - Kitchen A (00000000-0000-4000-8000-0000000bd001) has OPEN 13:00 Africa/Cairo slots for the next 3 days: in the DevTools Console on app.<PREVIEW_DOMAIN> `(await api('GET','/api/v1/kitchens/00000000-0000-4000-8000-0000000bd001/slots')).body.items` has >= 3 items and items[0].availablePortions >= 6 (seed: 20 portions per slot, 10 per meal).
  - FX-STAFF-OPS holds the active grant order.ops (verify: it sees the 'Order monitoring' tab at ops.<PREVIEW_DOMAIN>).
  - FX-CUST-A basket is empty (the 'Cart' page shows 'Your cart is empty.').
  - Slot assignment: use the seeded slot of day 1 (items[0]) (GET /api/v1/kitchens/00000000-0000-4000-8000-0000000bd001/slots, same index each run). Preparation consumes capacity permanently and each meal-slot has only 10 portions: before the case read the AGGREGATE slot availablePortions (max 20) from the kitchen slots response (SlotAvailability exposes aggregate availability only) AND the meal-specific availability from the meal's nextSlots[].availablePortions (GET /api/v1/meals/<mealId>; the owning cook's slot response also lists per-meal capacity) and switch to another seeded day if fewer portions than the case orders remain. E2E-ORDER-114/115 own the day-3 slot; do not use it here.
  - Each actor uses a SEPARATE browser profile (or private window/cookie store): customer web, ops web and the API share one session cookie (SecurityConfiguration), so signing in as a second actor in the same profile silently replaces the first. Before each actor's steps run `await api('GET','/api/v1/me')` (helper in conventions.api_helper) and confirm the displayName is that actor.
- **Steps:**
  1. (FX-CUST-A) Open https://app.<PREVIEW_DOMAIN>, sign in, press the language button until labels are English. Open meal 'Preview koshari', under 'Cooking slots' pick the day-1 slot (13:00 Africa/Cairo on today+1, state the Cairo date in the evidence), set quantity 2, press 'Add to cart'. — data: quantity=2
  2. (FX-CUST-A) Open nav 'Cart'. Select the 'Pickup from the kitchen' button, leave payment radio 'Cash on receipt', press 'Calculate total'. Tick 'I accept the terms and the cancellation policy' and press 'Place order' once. — data: mode=Pickup, payment=Cash on receipt
  3. (FX-CUST-A) On the order page note the order number (MB-…) and the heading state. Then open 'My orders' and confirm the order is listed.
  4. (FX-COOK-A) At https://ops.<PREVIEW_DOMAIN> open tab 'Orders', filter 'Active'. Find the card with the same MB- number: it shows badge 'Waiting for acceptance', 'Pickup', 'Accept by <time> (N min left)'. Press 'Accept'.
  5. (FX-COOK-A) On the same card press 'Start preparing', then 'Ready'.
  6. (FX-CUST-A) Reload the order page. Read the 'Handover code (give it to the cook or courier on receipt)' value (6 digits) and the 'Kitchen contact'/'Pickup location' line.
  7. (FX-COOK-A) On the card, type the 6-digit code in 'Customer handover code' and press 'Confirm handover'. — data: code = the digits read in step 6
  8. (FX-CUST-A) Reload the order page; check state, code and the review form. Open the nav 'My account' > 'Notifications'.
  9. (FX-COOK-A) Tab 'Orders', filter 'Completed': the card shows 'Cash due' and a 'Report cash collected' form (finance verification of the cash is covered by E2E-FIN-038).
  10. (FX-STAFF-OPS) At ops.<PREVIEW_DOMAIN> open 'Order monitoring' (filter 'All'), click the MB- number.
- **Expected:**
  - UI: Step 3: heading reads '<MB-number> · Waiting for the kitchen'; line '2 × Preview koshari — 150.00'; 'Total: 150.00'; no 'Payment' (deposit) panel because the order is cash.
  - UI: Step 4: card badges as stated; after Accept the badge becomes 'Accepted', customer name 'Preview Customer 1' and phone +201000000101 appear, buttons 'Start preparing' and 'Cancel'. After Ready the badge is 'Ready' and the only action is 'Confirm handover'.
  - UI: Step 6: customer page shows 'Ready', a 6-digit handover code, 'Kitchen contact: مطبخ المعاينة · +201000000201 · Pickup location: Synthetic location 1'.
  - UI: Step 8: customer page heading '<MB-number> · Delivered' (English label for COMPLETED, also for pickup), the code line is gone, the 'Rate your order' form is shown; the timeline has 5 lines in order: Waiting for the kitchen, Accepted, Preparing, Ready, Delivered. Notifications list contains (newest first) Delivered, Ready, Preparing, Accepted, each with the MB- link and 'Mark read'.
  - UI: Step 9: completion did not mark cash paid: the card shows 'Cash due'.
  - UI: Step 10: operations timeline lists the first row 'Waiting for acceptance' (no 'from' state) followed by 'Waiting for acceptance → Accepted', 'Accepted → Preparing', 'Preparing → Ready', 'Ready → Completed' rows with Africa/Cairo times; customer phone/name are NOT shown for the completed order ('Contact details are not shown in this state.'); no handover code anywhere.
  - API: POST /api/v1/orders 201 (OrderView state PENDING_ACCEPTANCE, total.amountMinor 15000, payment.method COD, cashState DUE, allowedActions ['CANCEL']).
  - API: Cook commands 200 CookOrderView with version +1 each; final GET /api/v1/orders/{id} state COMPLETED, handoverCode null, cashState DUE, events length 5.
  - API: GET /api/v1/ops/orders/{id} 200 with order.handoverCode null and customerContact null.
  - DB: Run SQL_ORDER: state COMPLETED, subtotal=total=15000, delivery_fee_minor 0, tax_minor 0, commission_bps_snapshot 0, commission_minor 0, terminal_at not null. SELECT state, version, subtotal_minor, delivery_fee_minor, tax_minor, total_minor, commission_bps_snapshot, commission_minor, accept_by, terminal_at FROM customer_order WHERE id = :'order';
  - DB: SQL_EVENTS shows sequences 1..5: NULL→PENDING_ACCEPTANCE (CUSTOMER), →ACCEPTED, →PREPARING, →READY, →COMPLETED (COOK). SELECT sequence, from_state, to_state, actor_type, reason_code FROM order_event WHERE order_id = :'order' ORDER BY sequence;
  - DB: stock_allocation: one row state CONSUMED quantity 2 (not released). SELECT state, quantity, released_at FROM stock_allocation WHERE order_id = :'order';
  - DB: cash_obligation: amount_minor 15000, state DUE (completion is independent of payment). SELECT amount_minor, state FROM cash_obligation WHERE order_id = :'order';
  - DB: audit_event has action 'access.order_contact' ONLY for views made while the order was non-terminal (none for step 10): SELECT action, object_id FROM audit_event WHERE object_id = :'order';
- **Also covered by automated tests:** backend/app/src/test/java/com/matbakhy/app/orders/OrderLifecycleIntegrationTest#pickup_order_moves_through_every_stage_with_stock_and_events_in_step; apps/customer-web/src/App.test.tsx (component level only)
- **Unverified against code (confirm while executing):** Exact currency string produced by Intl en-EG (e.g. 'EGP 150.00' vs '150.00 EGP'); assert the numeric amount.; Whether the notifications list refreshes without a page reload (the inbox has no polling; reload the page).; Arabic labels are the mirror strings in apps/customer-web/src/i18n.ts; run the same journey once in Arabic (RTL) and compare against ar strings (e.g. 'بانتظار قبول المطبخ', 'تم القبول', 'قيد التحضير', 'جاهز', 'تم التسليم').

### E2E-ORDER-102 — Core journey 2: delivery + cash on receipt, from placement through courier dispatch to completion

- **Status:** NOT_RUN · **Priority:** P0 · **Readiness:** Ready after the extra synthetic fixtures are provisioned
- **Surface:** customer-web, operations-web · **Actors:** FX-CUST-A, FX-COOK-A, FX-STAFF-OPS
- **Fixtures to provision:** FX-STAFF-OPS
- **Traces to:** rules: BR-021, BR-030, BR-031; decisions: D05, D10; operations: postQuotes, postOrders, postCookOrdersDispatch, postCookOrdersHandover; partial: TC-021, TC-031, TC-117, TC-119
- **Prerequisites:**
  - Preview stack reachable: customer web https://app.<PREVIEW_DOMAIN>, cook/staff web https://ops.<PREVIEW_DOMAIN>, API https://api.<PREVIEW_DOMAIN>. seed-preview.sql is applied: on the customer web home, kitchen 'مطبخ المعاينة' lists 'Preview koshari' (EGP 75.00) and 'Preview mahshi' (EGP 180.00), and kitchen 'مطبخ تجريبي تاني' lists 'Preview molokhia' (EGP 120.25).
  - FX-CUST-A signs in (password from the private fixture sheet) and GET /api/v1/me returns phoneVerified=true.
  - FX-COOK-A signs in at https://ops.<PREVIEW_DOMAIN> and the tab bar shows 'Orders' (ar: الطلبات); GET /api/v1/cook/dashboard in that session returns 200.
  - Kitchen A (00000000-0000-4000-8000-0000000bd001) has OPEN 13:00 Africa/Cairo slots for the next 3 days: in the DevTools Console on app.<PREVIEW_DOMAIN> `(await api('GET','/api/v1/kitchens/00000000-0000-4000-8000-0000000bd001/slots')).body.items` has >= 3 items and items[0].availablePortions >= 6 (seed: 20 portions per slot, 10 per meal).
  - FX-CUST-A has NO saved address yet or has one in 'Test zone A' (check: nav 'My account' > addresses). Kitchen A delivers only to 'Test zone A' (fee EGP 30.00).
  - FX-STAFF-OPS holds order.ops.
  - Slot assignment: use the seeded slot of day 2 (items[1]) (GET /api/v1/kitchens/00000000-0000-4000-8000-0000000bd001/slots, same index each run). Preparation consumes capacity permanently and each meal-slot has only 10 portions: before the case read the AGGREGATE slot availablePortions (max 20) from the kitchen slots response (SlotAvailability exposes aggregate availability only) AND the meal-specific availability from the meal's nextSlots[].availablePortions (GET /api/v1/meals/<mealId>; the owning cook's slot response also lists per-meal capacity) and switch to another seeded day if fewer portions than the case orders remain. E2E-ORDER-114/115 own the day-3 slot; do not use it here.
  - Each actor uses a SEPARATE browser profile (or private window/cookie store): customer web, ops web and the API share one session cookie (SecurityConfiguration), so signing in as a second actor in the same profile silently replaces the first. Before each actor's steps run `await api('GET','/api/v1/me')` (helper in conventions.api_helper) and confirm the displayName is that actor.
- **Steps:**
  1. (FX-CUST-A) Meal 'Preview koshari' (qty 1) then 'Preview mahshi' (qty 1) from the day-2 slot (13:00 Africa/Cairo on today+2; state the Cairo date in the evidence): press 'Add to cart' on each. — data: koshari x1 (75.00) + mahshi x1 (180.00)
  2. (FX-CUST-A) Open 'Cart'. Default mode is 'Delivery'. Under 'New address' fill: Address name 'Home test', Recipient 'Preview Customer 1', Governorate 'Test governorate', City 'Test city', Street 'Test street 1', Building '1', phone '+201000000101', Your area 'Test zone A'; press 'Save address' and select the new address radio.
  3. (FX-CUST-A) Leave 'Cash on receipt', press 'Calculate total', tick the terms box, press 'Place order'.
  4. (FX-COOK-A) Orders > 'Active': press 'Accept', then 'Start preparing', then 'Ready'. After 'Ready' the card shows the action 'Hand to courier' and the field 'Delivery reference (courier name or tracking number)'. Leave the field empty and note whether the button is enabled; then type 'COURIER-TEST-01' and press 'Hand to courier'. — data: deliveryReference=COURIER-TEST-01
  5. (FX-CUST-A) Reload the order page and read the handover code and state.
  6. (FX-COOK-A) On the card (badge 'Out for delivery') type the customer's 6-digit code in 'Customer handover code' and press 'Confirm handover'.
  7. (FX-CUST-A) Reload the order page.
  8. (FX-STAFF-OPS) Ops 'Order monitoring' > open the MB- number and read the timeline.
- **Expected:**
  - UI: Step 3 quote panel: Meals 255.00, Delivery fee 30.00, Tax 0.00, Total 285.00; order heading 'Waiting for the kitchen'.
  - UI: Step 4: after Accept the cook card shows the customer name/phone and the address line 'Address: Test street 1، 1'; the 'Hand to courier' button is disabled while the reference is empty.
  - UI: Step 5: customer state 'On the way' with the handover code visible. Step 7: 'Delivered'. Timeline order: Waiting for the kitchen, Accepted, Preparing, Ready, On the way, Delivered.
  - UI: Step 8: operations timeline shows READY → OUT_FOR_DELIVERY → COMPLETED with Cairo times; no customer contact (terminal).
  - API: POST /api/v1/quotes 201 total.amountMinor 28500, deliveryFee 3000, tax 0; order total 28500, fulfillmentMode DELIVERY.
  - API: POST /api/v1/cook/orders/{id}/dispatch with an empty/blank deliveryReference returns 422 VALIDATION_FAILED fieldErrors deliveryReference REQUIRED (verify via console).
  - API: GET /api/v1/orders/{id} after dispatch: state OUT_FOR_DELIVERY and handoverCode is the 6-digit value; after handover state COMPLETED, handoverCode null.
  - DB: customer_order.dispatch_reference = 'COURIER-TEST-01'; delivery_fee_minor 3000; total_minor 28500; address_snapshot not null. SELECT state, version, subtotal_minor, delivery_fee_minor, tax_minor, total_minor, commission_bps_snapshot, commission_minor, accept_by, terminal_at FROM customer_order WHERE id = :'order';
  - DB: order_event sequences 1..6 including READY→OUT_FOR_DELIVERY (COOK) and OUT_FOR_DELIVERY→COMPLETED (COOK). SELECT sequence, from_state, to_state, actor_type, reason_code FROM order_event WHERE order_id = :'order' ORDER BY sequence;
  - DB: cash_obligation amount_minor 28500 state DUE. SELECT amount_minor, state FROM cash_obligation WHERE order_id = :'order';
- **Also covered by automated tests:** OrderLifecycleIntegrationTest#wrong_fulfillment_paths_are_denied_and_the_correct_path_keeps_history
- **Unverified against code (confirm while executing):** Exact text of the validation error shown in the cook UI for an empty reference (the button is disabled by the UI, the API rule is from OrderLifecycleService.dispatch).; Whether the customer address radio is preselected after 'Save address' (steps assume you select it).

### E2E-ORDER-105 — A reused clientRequestId with a different quote is a conflict even with a fresh Idempotency-Key

- **Status:** NOT_RUN · **Priority:** P1 · **Readiness:** Ready to run manually now
- **Surface:** api · **Actors:** FX-CUST-A
- **Traces to:** rules: BR-023, BR-044; decisions: D05; operations: postOrders, getOrdersByRequestById; partial: TC-023, TC-044
- **Prerequisites:**
  - Preview stack reachable: customer web https://app.<PREVIEW_DOMAIN>, cook/staff web https://ops.<PREVIEW_DOMAIN>, API https://api.<PREVIEW_DOMAIN>. seed-preview.sql is applied: on the customer web home, kitchen 'مطبخ المعاينة' lists 'Preview koshari' (EGP 75.00) and 'Preview mahshi' (EGP 180.00), and kitchen 'مطبخ تجريبي تاني' lists 'Preview molokhia' (EGP 120.25).
  - FX-CUST-A signs in (password from the private fixture sheet) and GET /api/v1/me returns phoneVerified=true.
  - Kitchen A (00000000-0000-4000-8000-0000000bd001) has OPEN 13:00 Africa/Cairo slots for the next 3 days: in the DevTools Console on app.<PREVIEW_DOMAIN> `(await api('GET','/api/v1/kitchens/00000000-0000-4000-8000-0000000bd001/slots')).body.items` has >= 3 items and items[0].availablePortions >= 6 (seed: 20 portions per slot, 10 per meal).
  - In the DevTools Console of the signed-in tab, define the helper `api` from conventions.api_helper (needed for the API steps).
  - A read-only database method approved by the operator is available (psql through the operator channel); replace :'order' with the order UUID.
- **Steps:**
  1. (FX-CUST-A) Create quote q1 (2 × koshari) and place it with clientRequestId C and Idempotency-Key K1. Create quote q2 (1 × mahshi).
  2. (FX-CUST-A) POST /api/v1/orders with quoteId q2, the SAME clientRequestId C and a NEW Idempotency-Key K2. — data: this bypasses the 24 h idempotency cache and hits the permanent customer + clientRequestId rule
  3. (FX-CUST-A) GET /api/v1/orders/by-request/<C>.
- **Expected:**
  - UI: Not applicable.
  - API: Step 2: 409 IDEMPOTENCY_CONFLICT with fieldErrors [{field 'clientRequestId', code 'ALREADY_USED'}].
  - API: Step 3: 200 and the body is the ORIGINAL order (q1 items, total 15000).
  - DB: SELECT count(*) FROM customer_order WHERE client_request_id = '<C>'; -> 1. quote q2 has no order: SELECT count(*) FROM customer_order WHERE quote_id = '<q2>'; -> 0.
- **Also covered by automated tests:** OrderPlacementIntegrationTest
- **Unverified against code (confirm while executing):** fieldErrors shape {field, code} assumed from ApiException.FieldError; confirm the JSON property names against contracts/openapi.yaml components.schemas.FieldError.

### E2E-ORDER-106 — A reused clientRequestId with the same quote but a changed note/terms is a conflict (request fingerprint)

- **Status:** NOT_RUN · **Priority:** P1 · **Readiness:** Needs the updated application deployment
- **Surface:** api · **Actors:** FX-CUST-A · **Needs:** updated deploy
- **Traces to:** rules: BR-023, BR-044; decisions: D13; operations: postOrders; partial: TC-023, TC-044
- **Prerequisites:**
  - Preview stack reachable: customer web https://app.<PREVIEW_DOMAIN>, cook/staff web https://ops.<PREVIEW_DOMAIN>, API https://api.<PREVIEW_DOMAIN>. seed-preview.sql is applied: on the customer web home, kitchen 'مطبخ المعاينة' lists 'Preview koshari' (EGP 75.00) and 'Preview mahshi' (EGP 180.00), and kitchen 'مطبخ تجريبي تاني' lists 'Preview molokhia' (EGP 120.25).
  - FX-CUST-A signs in (password from the private fixture sheet) and GET /api/v1/me returns phoneVerified=true.
  - Kitchen A (00000000-0000-4000-8000-0000000bd001) has OPEN 13:00 Africa/Cairo slots for the next 3 days: in the DevTools Console on app.<PREVIEW_DOMAIN> `(await api('GET','/api/v1/kitchens/00000000-0000-4000-8000-0000000bd001/slots')).body.items` has >= 3 items and items[0].availablePortions >= 6 (seed: 20 portions per slot, 10 per meal).
  - In the DevTools Console of the signed-in tab, define the helper `api` from conventions.api_helper (needed for the API steps).
  - A read-only database method approved by the operator is available (psql through the operator channel); replace :'order' with the order UUID.
  - The deployed build includes migration V29 (verify: SELECT column_name FROM information_schema.columns WHERE table_name='customer_order' AND column_name='request_fingerprint'; returns one row). PR #9 only; origin/develop does not have it.
- **Steps:**
  1. (FX-CUST-A) Create quote q (2 × koshari). Place it with clientRequestId C, key K1, no fulfillmentNote.
  2. (FX-CUST-A) POST /api/v1/orders again with the same quoteId, same C, NEW key K2 and fulfillmentNote 'no onions'. — data: changed field = fulfillmentNote
  3. (FX-CUST-A) POST again with same quoteId, same C, new key K3, identical (empty) note. — data: identical request
- **Expected:**
  - UI: Not applicable.
  - API: Step 2: 409 IDEMPOTENCY_CONFLICT (fieldErrors clientRequestId ALREADY_USED). Step 3: 201 with the original order id (a true replay is allowed).
  - DB: SELECT request_fingerprint FROM customer_order WHERE client_request_id = '<C>'; -> 64 lower-case hex characters (sha256), not null.
  - DB: Orders placed before V29 keep request_fingerprint NULL and the old quote-only check (do not edit rows; observe only).
- **Also covered by automated tests:** backend/app/src/test/java/com/matbakhy/app/orders/OrderPlacementIntegrationTest#a_reused_request_reference_with_a_changed_field_conflicts_even_after_the_cache_is_gone; backend/app/src/test/java/com/matbakhy/orders/internal/OrderFingerprintTest
- **Unverified against code (confirm while executing):** On origin/develop step 2 is expected to return the original order (quote-only recovery); that is the defect fixed by PR #9, so a pass there is not evidence of conformance.

### E2E-ORDER-107 — Native app: killing the app during order submit and relaunching recovers the same order

- **Status:** BLOCKED — No signed iOS/Android build pointing at the preview is confirmed; native store setup is deferred (docs/operations/06-web-preview.md). · **Priority:** P1 · **Readiness:** Needs a physical iOS/Android build
- **Surface:** customer-mobile · **Actors:** FX-CUST-A, FX-DEVICE-IOS, FX-DEVICE-ANDROID · **Needs:** physical device
- **Traces to:** rules: BR-049, BR-044; decisions: D13; operations: postOrders, getOrdersByRequestById; partial: TC-049, TC-132
- **Prerequisites:**
  - Signed iOS and Android builds pointing at the preview API (docs/operations/06-web-preview.md says Apple/Google setup is deferred); FX-CUST-A signed in on the device.
  - Preview stack reachable: customer web https://app.<PREVIEW_DOMAIN>, cook/staff web https://ops.<PREVIEW_DOMAIN>, API https://api.<PREVIEW_DOMAIN>. seed-preview.sql is applied: on the customer web home, kitchen 'مطبخ المعاينة' lists 'Preview koshari' (EGP 75.00) and 'Preview mahshi' (EGP 180.00), and kitchen 'مطبخ تجريبي تاني' lists 'Preview molokhia' (EGP 120.25).
  - Kitchen A (00000000-0000-4000-8000-0000000bd001) has OPEN 13:00 Africa/Cairo slots for the next 3 days: in the DevTools Console on app.<PREVIEW_DOMAIN> `(await api('GET','/api/v1/kitchens/00000000-0000-4000-8000-0000000bd001/slots')).body.items` has >= 3 items and items[0].availablePortions >= 6 (seed: 20 portions per slot, 10 per meal).
  - Each actor uses a SEPARATE browser profile (or private window/cookie store): customer web, ops web and the API share one session cookie (SecurityConfiguration), so signing in as a second actor in the same profile silently replaces the first. Before each actor's steps run `await api('GET','/api/v1/me')` (helper in conventions.api_helper) and confirm the displayName is that actor.
- **Steps:**
  1. (FX-CUST-A) On the device open Discover > 'Preview koshari' > slot 13:00 tomorrow > add 1; open the basket, 'Calculate total', accept terms.
  2. (FX-CUST-A) Turn on airplane mode, press 'Place order', then force-quit the app, turn airplane mode off and relaunch.
  3. (FX-CUST-A) On the basket screen read the pending notice and press 'Check and retry' (testID retry-order).
- **Expected:**
  - UI: Pending notice (testID 'pending-order'): "We couldn't confirm whether your order arrived. Retrying will not create a second order." (offline variant: "You're offline. We couldn't confirm whether your order arrived; retry when you're back online. It will not be duplicated."). After 'Check and retry' the Orders tab shows ONE new order in 'Waiting for the kitchen'.
  - API: Same as ORDER-024 step 5 (one order per clientRequestId).
  - DB: Exactly one customer_order row for the new clientRequestId.
- **Also covered by automated tests:** apps/customer-mobile/src/ordering.test.ts; apps/customer-mobile/src/App.test.tsx (jest, not device)
- **Unverified against code (confirm while executing):** Whether airplane-mode submit reaches the pendingOffline or pendingUncertain message on each platform.

### E2E-ORDER-108 — Operations handover override completes a locked order with fresh MFA, reason and evidence, fully audited

- **Status:** NOT_RUN · **Priority:** P1 · **Readiness:** Needs the updated application deployment
- **Surface:** operations-web, api · **Actors:** FX-STAFF-OPS, FX-COOK-A, FX-STAFF-NOGRANT · **Needs:** updated deploy
- **Fixtures to provision:** FX-STAFF-OPS
- **Traces to:** rules: BR-031; decisions: D05, D18; operations: postOpsOrdersHandoverOverride; partial: TC-031, TC-119
- **Prerequisites:**
  - Preview stack reachable: customer web https://app.<PREVIEW_DOMAIN>, cook/staff web https://ops.<PREVIEW_DOMAIN>, API https://api.<PREVIEW_DOMAIN>. seed-preview.sql is applied: on the customer web home, kitchen 'مطبخ المعاينة' lists 'Preview koshari' (EGP 75.00) and 'Preview mahshi' (EGP 180.00), and kitchen 'مطبخ تجريبي تاني' lists 'Preview molokhia' (EGP 120.25).
  - Staff accounts for this case are PROVISIONED fixtures: grants are written by the operator (DB insert into user_grant) or by FX-STAFF-ADMIN in the ops 'Staff permissions' screen; granting through the API requires staff.grant AND recent MFA (StaffController), which needs the PR #9 Keycloak step-up flow.
  - FX-STAFF-OPS holds order.ops and order.handover_override and can complete a Keycloak MFA step-up (PR #9 realm flow applied; on origin/develop the realm has no OTP/acr flow, so this step ends in 403 STEP_UP_REQUIRED).
  - A READY order O1 whose challenge is locked (result of E2E-ORDER-033). PRE_DB.
  - Each actor uses a SEPARATE browser profile (or private window/cookie store): customer web, ops web and the API share one session cookie (SecurityConfiguration), so signing in as a second actor in the same profile silently replaces the first. Before each actor's steps run `await api('GET','/api/v1/me')` (helper in conventions.api_helper) and confirm the displayName is that actor.
- **Steps:**
  1. (FX-STAFF-OPS) Ops web > 'Order monitoring' > open O1. In the form 'Handover override' leave Reason empty and press the button: note it is disabled. Type a Reason of 5 characters: still disabled.
  2. (FX-STAFF-OPS) Without a fresh step-up press the button with Reason 'Challenge locked after 5 wrong codes; customer verified by phone' and Evidence reference 'TEST-EVID-001'.
  3. (FX-STAFF-OPS) Press 'Verify again' (the step-up link), pass the second factor, return and submit the same form.
  4. (FX-COOK-A) Console (ops origin): POST /api/v1/ops/orders/<O1>/handover-override as the cook.
  5. (FX-STAFF-NOGRANT) Same call from the no-grant staff account.
- **Expected:**
  - UI: Step 2: message 'This action needs additional verification (MFA).' with link 'Verify again'. Step 3: order shows '<MB-number> · Completed'; the Delivered state appears for the customer.
  - API: Step 2: 403 STEP_UP_REQUIRED. Step 3: 200 OrderView state COMPLETED, handoverCode null, no customer contact. Direct API call with reason of 9 chars: 422 VALIDATION_FAILED fieldErrors reason MIN_10_CHARS. Steps 4-5: 403 FORBIDDEN.
  - DB: order_event last row READY→COMPLETED actor_type STAFF reason_code HANDOVER_OVERRIDE; handover_challenge.consumed_at set.
  - DB: SELECT action, permission, outcome, reason, details FROM audit_event WHERE action = 'order.handover_override' AND object_id = :'order'; -> one ALLOWED row with the reason text and details {evidenceRef:'TEST-EVID-001', mode:'PICKUP'}.
- **Also covered by automated tests:** OrderLifecycleIntegrationTest#wrong_codes_are_bounded_valid_codes_work_once_and_locked_challenges_need_an_audited_override; backend/app/src/test/java/com/matbakhy/app/security/StaffAssuranceTest
- **Unverified against code (confirm while executing):** Step-up round trip in the ops UI (link target) was not exercised; verify against docs/security/mfa-policy.md web step-up route.; Exact order of the two button-enable rules (reason >= 10 characters and non-empty evidence) taken from OpsOrders.tsx.

### E2E-ORDER-109 — Operations exception-cancel of a PREPARING order keeps consumed capacity and links the support case

- **Status:** NOT_RUN · **Priority:** P1 · **Readiness:** Ready after the extra synthetic fixtures are provisioned
- **Surface:** operations-web, api · **Actors:** FX-STAFF-OPS, FX-STAFF-SUPPORT, FX-CUST-A
- **Fixtures to provision:** FX-STAFF-OPS, FX-STAFF-SUPPORT
- **Traces to:** rules: BR-028, BR-029, BR-041; decisions: D05; operations: postOpsOrdersExceptionCancel, patchOpsCasesById; partial: TC-028, TC-029, TC-041
- **Prerequisites:**
  - Preview stack reachable: customer web https://app.<PREVIEW_DOMAIN>, cook/staff web https://ops.<PREVIEW_DOMAIN>, API https://api.<PREVIEW_DOMAIN>. seed-preview.sql is applied: on the customer web home, kitchen 'مطبخ المعاينة' lists 'Preview koshari' (EGP 75.00) and 'Preview mahshi' (EGP 180.00), and kitchen 'مطبخ تجريبي تاني' lists 'Preview molokhia' (EGP 120.25).
  - Staff accounts for this case are PROVISIONED fixtures: grants are written by the operator (DB insert into user_grant) or by FX-STAFF-ADMIN in the ops 'Staff permissions' screen; granting through the API requires staff.grant AND recent MFA (StaffController), which needs the PR #9 Keycloak step-up flow.
  - A read-only database method approved by the operator is available (psql through the operator channel); replace :'order' with the order UUID.
  - A PREPARING pickup/COD order O1 with an open CANCELLATION_REQUEST case reference CS-XXXXXXXX (E2E-ORDER-035). FX-STAFF-OPS holds order.ops + order.exception_cancel; FX-STAFF-SUPPORT holds support.case.
  - Note: the exception-cancel endpoint requires the permission only (no step-up in OpsOrderController); the UI still renders a step-up hint.
  - Each actor uses a SEPARATE browser profile (or private window/cookie store): customer web, ops web and the API share one session cookie (SecurityConfiguration), so signing in as a second actor in the same profile silently replaces the first. Before each actor's steps run `await api('GET','/api/v1/me')` (helper in conventions.api_helper) and confirm the displayName is that actor.
- **Steps:**
  1. (FX-STAFF-SUPPORT) In 'Support' try to resolve the case with resolution 'Order cancelled' BEFORE the order is cancelled (reason text >= 3 characters).
  2. (FX-STAFF-OPS) Order monitoring > O1 > form 'Exception cancellation': Reason OPERATIONAL, 'Case reference' = CS-XXXXXXXX, Note 'Ingredient shortage'; press 'Exception cancellation'.
  3. (FX-STAFF-OPS) Console: repeat the call on an ACCEPTED order, and with a blank caseId.
  4. (FX-STAFF-SUPPORT) Resolve the case now with 'Order cancelled'.
  5. (FX-CUST-A) Reload the order page.
- **Expected:**
  - UI: Customer page '<MB-number> · Cancelled'.
  - API: Step 1: 409 STATE_CONFLICT fieldErrors [{resolutionCode,'ORDER_NOT_CANCELLED'}]. Step 3: 409 STATE_CONFLICT ('exception cancel only from PREPARING or READY'); blank caseId: 422 VALIDATION_FAILED. Step 4: 200 case RESOLVED.
  - DB: SELECT state, quantity, released_at FROM stock_allocation WHERE order_id = :'order'; -> state CONSUMED stays; cook_slot.consumed unchanged (not released). SELECT sequence, from_state, to_state, actor_type, reason_code FROM order_event WHERE order_id = :'order' ORDER BY sequence; -> PREPARING→CANCELLED actor STAFF reason_code OPERATIONAL.
  - DB: SELECT action, details FROM audit_event WHERE action = 'order.exception_cancel' AND object_id = :'order'; -> ALLOWED with details.caseRef = the case reference.
  - DB: SELECT amount_minor, state FROM cash_obligation WHERE order_id = :'order'; -> state unchanged DUE (manual finance review, BR-029).
- **Also covered by automated tests:** OrderLifecycleIntegrationTest#prepared_orders_cancelled_by_ops_keep_consumed_stock_and_never_release_twice
- **Unverified against code (confirm while executing):** Docs list order.exception_cancel among high-risk permissions; the controller does not require recent MFA for it (only the handover override does).

### E2E-ORDER-110 — Cook cancels an ACCEPTED order before preparation: allocation released once; cancel is unavailable once PREPARING

- **Status:** NOT_RUN · **Priority:** P1 · **Readiness:** Ready to run manually now
- **Surface:** operations-web, customer-web, api · **Actors:** FX-COOK-A, FX-CUST-A
- **Traces to:** rules: BR-029, BR-028; decisions: D05; operations: postCookOrdersCancel; partial: TC-029, TC-119
- **Prerequisites:**
  - Preview stack reachable: customer web https://app.<PREVIEW_DOMAIN>, cook/staff web https://ops.<PREVIEW_DOMAIN>, API https://api.<PREVIEW_DOMAIN>. seed-preview.sql is applied: on the customer web home, kitchen 'مطبخ المعاينة' lists 'Preview koshari' (EGP 75.00) and 'Preview mahshi' (EGP 180.00), and kitchen 'مطبخ تجريبي تاني' lists 'Preview molokhia' (EGP 120.25).
  - FX-COOK-A signs in at https://ops.<PREVIEW_DOMAIN> and the tab bar shows 'Orders' (ar: الطلبات); GET /api/v1/cook/dashboard in that session returns 200.
  - In the DevTools Console of the signed-in tab, define the helper `api` from conventions.api_helper (needed for the API steps).
  - A read-only database method approved by the operator is available (psql through the operator channel); replace :'order' with the order UUID.
  - ACCEPTED order A1 and PREPARING order B1 (both pickup/COD of FX-CUST-A).
  - Each actor uses a SEPARATE browser profile (or private window/cookie store): customer web, ops web and the API share one session cookie (SecurityConfiguration), so signing in as a second actor in the same profile silently replaces the first. Before each actor's steps run `await api('GET','/api/v1/me')` (helper in conventions.api_helper) and confirm the displayName is that actor.
- **Steps:**
  1. (FX-COOK-A) On A1 choose Reason 'Ingredient unavailable' (default) and press 'Cancel'.
  2. (FX-COOK-A) On B1 look at the buttons; then console POST /api/v1/cook/orders/<B1>/cancel {expectedVersion:v, reasonCode:'OTHER'}.
  3. (FX-CUST-A) Reload A1.
  4. (operator) SQL for A1.
- **Expected:**
  - UI: A1 customer page '<MB-number> · Cancelled'. B1 card shows only 'Ready' (no 'Cancel').
  - API: B1 cancel: 409 STATE_CONFLICT fieldErrors [{state,'PREPARING'}].
  - DB: SELECT sequence, from_state, to_state, actor_type, reason_code FROM order_event WHERE order_id = :'order' ORDER BY sequence; -> ACCEPTED→CANCELLED actor COOK reason_code INGREDIENT_UNAVAILABLE.
  - DB: SELECT state, quantity, released_at FROM stock_allocation WHERE order_id = :'order'; -> ALLOCATED→RELEASED once; allocated counter decreased by 2.
  - DB: SELECT amount_minor, state FROM cash_obligation WHERE order_id = :'order'; -> NOT_DUE.
- **Also covered by automated tests:** OrderLifecycleIntegrationTest

### E2E-ORDER-111 — An expired quote (older than 5 minutes) cannot be placed and holds no capacity

- **Status:** NOT_RUN · **Priority:** P1 · **Readiness:** Ready to run manually now
- **Surface:** customer-web, api · **Actors:** FX-CUST-A
- **Traces to:** rules: BR-020; decisions: D13; operations: postQuotes, postOrders; partial: TC-020, TC-117
- **Prerequisites:**
  - Preview stack reachable: customer web https://app.<PREVIEW_DOMAIN>, cook/staff web https://ops.<PREVIEW_DOMAIN>, API https://api.<PREVIEW_DOMAIN>. seed-preview.sql is applied: on the customer web home, kitchen 'مطبخ المعاينة' lists 'Preview koshari' (EGP 75.00) and 'Preview mahshi' (EGP 180.00), and kitchen 'مطبخ تجريبي تاني' lists 'Preview molokhia' (EGP 120.25).
  - FX-CUST-A signs in (password from the private fixture sheet) and GET /api/v1/me returns phoneVerified=true.
  - Kitchen A (00000000-0000-4000-8000-0000000bd001) has OPEN 13:00 Africa/Cairo slots for the next 3 days: in the DevTools Console on app.<PREVIEW_DOMAIN> `(await api('GET','/api/v1/kitchens/00000000-0000-4000-8000-0000000bd001/slots')).body.items` has >= 3 items and items[0].availablePortions >= 6 (seed: 20 portions per slot, 10 per meal).
  - In the DevTools Console of the signed-in tab, define the helper `api` from conventions.api_helper (needed for the API steps).
  - A read-only database method approved by the operator is available (psql through the operator channel); replace :'order' with the order UUID.
  - Test window: this case needs a 5-minute wait.
- **Steps:**
  1. (FX-CUST-A) Record cook_slot.reserved (R0). In the console create a quote (2 × koshari PICKUP COD) and note expiresAt (about 5 minutes ahead). Re-read cook_slot.reserved immediately.
  2. (FX-CUST-A) Wait until the clock passes expiresAt (about 5 minutes + 5 s).
  3. (FX-CUST-A) POST /api/v1/orders with that quoteId.
  4. (FX-CUST-A) UI variant: in the customer web press 'Calculate total', wait 5 minutes, tick terms, press 'Place order'.
- **Expected:**
  - UI: UI variant: error line "The quote expired. Calculate the total again." (ar: انتهت صلاحية العرض. احسب الإجمالي مرة أخرى.); no order created.
  - API: Step 3: 409 QUOTE_EXPIRED with fieldErrors [{quoteId, QUOTE_EXPIRED}].
  - DB: cook_slot.reserved equals R0 both before and after the quote (a quote holds no stock): SELECT cs.reserved, cs.allocated, cs.consumed, cs.max_portions FROM cook_slot cs JOIN customer_order o ON o.slot_id = cs.id WHERE o.id = :'order';
  - DB: SELECT count(*) FROM customer_order WHERE quote_id = '<quoteId>'; -> 0.
- **Also covered by automated tests:** QuoteIntegrationTest

### E2E-ORDER-112 — A kitchen price change after quoting forces explicit re-acceptance; a committed order keeps its original price

- **Status:** NOT_RUN · **Priority:** P1 · **Readiness:** Ready to run manually now
- **Surface:** customer-web, operations-web · **Actors:** FX-CUST-A, FX-COOK-A · **Destructive**
- **Traces to:** scenarios: TC-020; rules: BR-012, BR-020; decisions: D13, D14; operations: postQuotes, postOrders, getCookMealsPriceHistory; partial: TC-042, TC-156
- **Prerequisites:**
  - Preview stack reachable: customer web https://app.<PREVIEW_DOMAIN>, cook/staff web https://ops.<PREVIEW_DOMAIN>, API https://api.<PREVIEW_DOMAIN>. seed-preview.sql is applied: on the customer web home, kitchen 'مطبخ المعاينة' lists 'Preview koshari' (EGP 75.00) and 'Preview mahshi' (EGP 180.00), and kitchen 'مطبخ تجريبي تاني' lists 'Preview molokhia' (EGP 120.25).
  - FX-CUST-A signs in (password from the private fixture sheet) and GET /api/v1/me returns phoneVerified=true.
  - FX-COOK-A signs in at https://ops.<PREVIEW_DOMAIN> and the tab bar shows 'Orders' (ar: الطلبات); GET /api/v1/cook/dashboard in that session returns 200.
  - Kitchen A (00000000-0000-4000-8000-0000000bd001) has OPEN 13:00 Africa/Cairo slots for the next 3 days: in the DevTools Console on app.<PREVIEW_DOMAIN> `(await api('GET','/api/v1/kitchens/00000000-0000-4000-8000-0000000bd001/slots')).body.items` has >= 3 items and items[0].availablePortions >= 6 (seed: 20 portions per slot, 10 per meal).
  - SHARED DATA WARNING: this case changes the price of 'Preview koshari' in the shared preview catalogue and MUST restore EGP 75.00 at the end (steps 7-8).
  - Each actor uses a SEPARATE browser profile (or private window/cookie store): customer web, ops web and the API share one session cookie (SecurityConfiguration), so signing in as a second actor in the same profile silently replaces the first. Before each actor's steps run `await api('GET','/api/v1/me')` (helper in conventions.api_helper) and confirm the displayName is that actor.
- **Steps:**
  1. (FX-CUST-A) Add 2 × 'Preview koshari' (pickup, cash), press 'Calculate total' (total 150.00). Do NOT place.
  2. (FX-COOK-A) Ops web > tab 'Meals' > 'Preview koshari' > 'Price history' > field 'New price (EGP)' = 80.00 > 'Change price'. — data: 80.00
  3. (FX-CUST-A) Within 5 minutes of step 1: tick the terms box and press 'Place order'.
  4. (FX-CUST-A) Read the notice (data-testid price-change), press 'Accept the new total', tick the terms box again (the re-quote clears it), then press 'Place order'.
  5. (FX-COOK-A) Change the price of 'Preview koshari' to 90.00 via the same screen. — data: 90.00
  6. (FX-CUST-A) Open the placed order and compare unit price/total; also GET /api/v1/orders/<id>.
  7. (FX-COOK-A) Restore the price to 75.00 via the same screen. — data: 75.00
  8. (FX-CUST-A) Reload the meal page and confirm 75.00 is shown.
- **Expected:**
  - UI: Step 3-4: notice titled 'The price changed' with "The kitchen updated the price of “Preview koshari” from … 75.00 to … 80.00." and "The total was … 150.00 and is now … 160.00. Review the new total before confirming your order."; 'Place order' stays disabled until 'Accept the new total' is pressed, then 'You accepted the new total. Confirm the order to send it.'.
  - UI: Step 6: the order page still shows '2 × Preview koshari — 160.00' and total 160.00 although the kitchen now charges 90.00.
  - API: Step 3 server answer: 409 PRICE_CHANGED with fieldErrors [{quoteId, MEAL_CHANGED}]. Order total.amountMinor 16000, items[0].unitPrice.amountMinor 8000 after step 4 and unchanged after step 5.
  - DB: SELECT unit_price_minor, line_total_minor, meal_revision FROM order_item WHERE order_id = :'order'; -> 8000, 16000 and the revision that existed at placement.
  - DB: SELECT count(*) FROM customer_order WHERE quote_id = '<old quote>'; -> 0 (the stale quote never became an order).
- **Also covered by automated tests:** PriceChangeIntegrationTest; PriceHistoryIntegrationTest
- **Unverified against code (confirm while executing):** The Meals screen in ops web is used for the edit (apps/operations-web/src/cook/Meals.tsx 'Change price'); whether a published meal price edit requires moderation in the preview is not confirmed.; Operation id for price history (API-099) is inferred as getCookMealsPriceHistory; confirm in contracts/openapi.yaml.

### E2E-ORDER-113 — The server rejects forged amounts, mixed baskets, illegal quantities and un-serviceable delivery

- **Status:** NOT_RUN · **Priority:** P1 · **Readiness:** Ready to run manually now
- **Surface:** api · **Actors:** FX-CUST-A, FX-CUST-B
- **Traces to:** scenarios: TC-018; rules: BR-018, BR-019, BR-021; decisions: D05; operations: postQuotes, postOrders; partial: TC-006, TC-021, TC-117
- **Prerequisites:**
  - Preview stack reachable: customer web https://app.<PREVIEW_DOMAIN>, cook/staff web https://ops.<PREVIEW_DOMAIN>, API https://api.<PREVIEW_DOMAIN>. seed-preview.sql is applied: on the customer web home, kitchen 'مطبخ المعاينة' lists 'Preview koshari' (EGP 75.00) and 'Preview mahshi' (EGP 180.00), and kitchen 'مطبخ تجريبي تاني' lists 'Preview molokhia' (EGP 120.25).
  - FX-CUST-A signs in (password from the private fixture sheet) and GET /api/v1/me returns phoneVerified=true.
  - Kitchen A (00000000-0000-4000-8000-0000000bd001) has OPEN 13:00 Africa/Cairo slots for the next 3 days: in the DevTools Console on app.<PREVIEW_DOMAIN> `(await api('GET','/api/v1/kitchens/00000000-0000-4000-8000-0000000bd001/slots')).body.items` has >= 3 items and items[0].availablePortions >= 6 (seed: 20 portions per slot, 10 per meal).
  - In the DevTools Console of the signed-in tab, define the helper `api` from conventions.api_helper (needed for the API steps).
  - FX-CUST-A has an address in 'Test zone A' (addrA) and one in 'Test zone B' (addrB, create with POST /api/v1/me/addresses {label, recipientName, governorate, city, street, building, zoneId:'00000000-0000-4000-8000-00000000a002', phone:'+201000000101'}). Kitchen A serves only zone A.
  - FX-CUST-B signs in in a second browser profile.
  - Each actor uses a SEPARATE browser profile (or private window/cookie store): customer web, ops web and the API share one session cookie (SecurityConfiguration), so signing in as a second actor in the same profile silently replaces the first. Before each actor's steps run `await api('GET','/api/v1/me')` (helper in conventions.api_helper) and confirm the displayName is that actor.
- **Steps:**
  1. (FX-CUST-A) POST /api/v1/quotes with a forged extra field: {items:[{mealId:koshari,quantity:1}], slotId, fulfillmentMode:'PICKUP', totalMinor:1}. — data: totalMinor=1
  2. (FX-CUST-A) Quote with quantity 11, then with quantity 0. — data: 11 and 0
  3. (FX-CUST-A) Quote with items [koshari x1, molokhia x1] and the kitchen A slot id. — data: molokhia belongs to kitchen B
  4. (FX-CUST-A) Quote with fulfillmentMode PICKUP and addressId addrA; then DELIVERY without addressId; then DELIVERY with addrB; then two lines for the same meal.
  5. (FX-CUST-B) POST /api/v1/orders using a quoteId that FX-CUST-A created.
- **Expected:**
  - UI: Not applicable (API only).
  - API: 1: 400 MALFORMED_REQUEST (strict JSON, QuoteController comment). 2: 422 VALIDATION_FAILED fieldErrors items.quantity RANGE_1_TO_10 (the policy max is 10 in the dev policy) for both.
  - API: 3: 422 VALIDATION_FAILED fieldErrors items MIXED_KITCHENS_OR_SLOT. 4: 422 addressId NOT_ALLOWED_FOR_PICKUP; 422 addressId REQUIRED_FOR_DELIVERY; 422 addressId NOT_SERVICEABLE; 422 items DUPLICATE_OR_MISSING_MEAL.
  - API: 5: 404 NOT_FOUND (a foreign quote is indistinguishable from a missing one).
  - DB: SELECT count(*) FROM quote WHERE customer_id = <FX-CUST-A id> AND created_at > <test start> ; -> equals the number of 201 quotes only (rejected requests leave no quote). No customer_order rows were created by this case.
- **Also covered by automated tests:** QuoteIntegrationTest
- **Unverified against code (confirm while executing):** Whether the unknown-field rejection returns code MALFORMED_REQUEST (400) for this endpoint at runtime (derived from the controller comment and ErrorCode, not observed).; RANGE_1_TO_10 depends on the active policy max_item_quantity (dev policy 10).

### E2E-ORDER-114 — Capacity is bounded per meal-slot and per kitchen-slot, and released portions become orderable again

- **Status:** NOT_RUN · **Priority:** P1 · **Readiness:** Ready to run manually now
- **Surface:** customer-web, api · **Actors:** FX-CUST-A, FX-CUST-B
- **Traces to:** rules: BR-013, BR-025, BR-029; decisions: D05; operations: postQuotes, postOrders, postOrdersCancel; partial: TC-013, TC-029, TC-115
- **Prerequisites:**
  - Preview stack reachable: customer web https://app.<PREVIEW_DOMAIN>, cook/staff web https://ops.<PREVIEW_DOMAIN>, API https://api.<PREVIEW_DOMAIN>. seed-preview.sql is applied: on the customer web home, kitchen 'مطبخ المعاينة' lists 'Preview koshari' (EGP 75.00) and 'Preview mahshi' (EGP 180.00), and kitchen 'مطبخ تجريبي تاني' lists 'Preview molokhia' (EGP 120.25).
  - FX-CUST-A signs in (password from the private fixture sheet) and GET /api/v1/me returns phoneVerified=true.
  - Kitchen A (00000000-0000-4000-8000-0000000bd001) has OPEN 13:00 Africa/Cairo slots for the next 3 days: in the DevTools Console on app.<PREVIEW_DOMAIN> `(await api('GET','/api/v1/kitchens/00000000-0000-4000-8000-0000000bd001/slots')).body.items` has >= 3 items and items[0].availablePortions >= 6 (seed: 20 portions per slot, 10 per meal).
  - In the DevTools Console of the signed-in tab, define the helper `api` from conventions.api_helper (needed for the API steps).
  - A read-only database method approved by the operator is available (psql through the operator channel); replace :'order' with the order UUID.
  - FX-CUST-B signs in in a second browser profile; use the THIRD seeded slot (items[2], day+3) so the case does not collide with other cases. Its free portions are untouched: meal_slot capacity 10 koshari, 10 mahshi; cook_slot max_portions 20.
  - Sequential case only; the parallel last-portion race is E2E-ORDER-115 (isolated stack).
  - Each actor uses a SEPARATE browser profile (or private window/cookie store): customer web, ops web and the API share one session cookie (SecurityConfiguration), so signing in as a second actor in the same profile silently replaces the first. Before each actor's steps run `await api('GET','/api/v1/me')` (helper in conventions.api_helper) and confirm the displayName is that actor.
- **Steps:**
  1. (FX-CUST-A) Place an order for 10 × koshari (the per-line limit is 10) in slot items[2], PICKUP/COD. — data: quantity 10
  2. (FX-CUST-B) Quote 1 × koshari in the same slot.
  3. (FX-CUST-B) Quote and place 10 × mahshi in the same slot. — data: now the kitchen slot is 20/20
  4. (FX-CUST-B) Quote 1 × mahshi and 1 × koshari in that slot; then open the kitchen page in the customer web and look at the slot.
  5. (FX-CUST-A) Cancel the 10 × koshari order (order page > 'Cancel order').
  6. (FX-CUST-B) Quote 1 × koshari again.
  7. (FX-CUST-B) Cleanup: cancel the 10 × mahshi order.
- **Expected:**
  - UI: Step 4: the slot shows sold out ("This time is sold out. Choose another time." when trying to add) and 'Calculate total' fails. Step 5: order shows 'Cancelled'.
  - API: Step 2: 409 STOCK_CHANGED fieldErrors items.quantity INSUFFICIENT_PORTIONS (koshari meal-slot 10/10). Step 4: 409 STOCK_CHANGED (kitchen slot 20/20). Step 6: 201.
  - DB: Between steps 3 and 5: SELECT cs.reserved, cs.allocated, cs.consumed, cs.max_portions FROM cook_slot cs JOIN customer_order o ON o.slot_id = cs.id WHERE o.id = :'order'; -> reserved 20, max_portions 20; meal_slot reserved 10/10. After step 5: koshari meal_slot.reserved 0, cook_slot.reserved 10; stock_allocation of the cancelled order RELEASED once.
  - DB: Run infra/db/reconcile.sql: all violations 0.
- **Also covered by automated tests:** OrderPlacementIntegrationTest; QuoteIntegrationTest
- **Unverified against code (confirm while executing):** Whether the web meal page greys out a sold-out slot or only fails at 'Calculate total' (slotSoldOut text exists in i18n).

### E2E-ORDER-115 — Two customers racing for the last portion: exactly one order, no oversell

- **Status:** NOT_RUN · **Priority:** P1 · **Readiness:** Isolated disposable stack only
- **Surface:** api · **Actors:** FX-CUST-A, FX-CUST-B · **Needs:** isolated stack
- **Traces to:** rules: BR-025, BR-013; decisions: D05; operations: postOrders; partial: TC-013, TC-118, TC-138
- **Prerequisites:**
  - FX-ISOLATED disposable stack (seed-preview.sql + dev policy). Destructive/race case: never on the shared preview.
  - In the DevTools Console of the signed-in tab, define the helper `api` from conventions.api_helper (needed for the API steps).
  - A read-only database method approved by the operator is available (psql through the operator channel); replace :'order' with the order UUID.
  - Reduce the target slot so that exactly 1 koshari portion remains (order 9 first with FX-CUST-A, or edit capacity in the disposable DB).
  - Each actor uses a SEPARATE browser profile (or private window/cookie store): customer web, ops web and the API share one session cookie (SecurityConfiguration), so signing in as a second actor in the same profile silently replaces the first. Before each actor's steps run `await api('GET','/api/v1/me')` (helper in conventions.api_helper) and confirm the displayName is that actor.
- **Steps:**
  1. (FX-CUST-A) Create quote qa (1 × koshari). In the second browser profile FX-CUST-B creates quote qb (1 × koshari). Both quotes succeed because quotes hold no stock.
  2. (FX-CUST-A) Release both placements simultaneously (two consoles, countdown or a script) with distinct clientRequestIds and keys.
  3. (operator) Run infra/db/reconcile.sql and the slot counter SQL.
- **Expected:**
  - UI: Not applicable.
  - API: Exactly one 201 and one 409 STOCK_CHANGED (INSUFFICIENT_PORTIONS).
  - DB: meal_slot reserved + allocated + consumed <= capacity (check constraint meal_slot_capacity_ck); cook_slot likewise; reconcile.sql violations 0; exactly one RESERVED allocation was added.
- **Also covered by automated tests:** OrderPlacementIntegrationTest

### E2E-ORDER-117 — Every order transition produces exactly one in-app notification for the right recipient, in order and deduplicated

- **Status:** NOT_RUN · **Priority:** P1 · **Readiness:** Ready to run manually now
- **Surface:** customer-web, api · **Actors:** FX-CUST-A, FX-COOK-A
- **Traces to:** rules: BR-037, BR-038; decisions: D05; operations: getNotifications, putNotificationsRead; partial: TC-037, TC-038, TC-121
- **Prerequisites:**
  - Preview stack reachable: customer web https://app.<PREVIEW_DOMAIN>, cook/staff web https://ops.<PREVIEW_DOMAIN>, API https://api.<PREVIEW_DOMAIN>. seed-preview.sql is applied: on the customer web home, kitchen 'مطبخ المعاينة' lists 'Preview koshari' (EGP 75.00) and 'Preview mahshi' (EGP 180.00), and kitchen 'مطبخ تجريبي تاني' lists 'Preview molokhia' (EGP 120.25).
  - FX-CUST-A signs in (password from the private fixture sheet) and GET /api/v1/me returns phoneVerified=true.
  - FX-COOK-A signs in at https://ops.<PREVIEW_DOMAIN> and the tab bar shows 'Orders' (ar: الطلبات); GET /api/v1/cook/dashboard in that session returns 200.
  - In the DevTools Console of the signed-in tab, define the helper `api` from conventions.api_helper (needed for the API steps).
  - A read-only database method approved by the operator is available (psql through the operator channel); replace :'order' with the order UUID.
  - A pickup/COD order O1 driven through placement, accept, prepare, ready, handover (E2E-ORDER-101). Wait 10 s after the last step (outbox dispatch every 5 s).
  - Each actor uses a SEPARATE browser profile (or private window/cookie store): customer web, ops web and the API share one session cookie (SecurityConfiguration), so signing in as a second actor in the same profile silently replaces the first. Before each actor's steps run `await api('GET','/api/v1/me')` (helper in conventions.api_helper) and confirm the displayName is that actor.
- **Steps:**
  1. (FX-CUST-A) Customer web > nav 'My account' > section 'Notifications': read the list. Press 'Mark read' on the newest entry.
  2. (FX-CUST-A) Console: GET /api/v1/notifications?limit=20, then GET /api/v1/notifications?unreadOnly=true&limit=20.
  3. (FX-COOK-A) Console on ops origin: GET /api/v1/notifications?limit=20 (the cook workspace has no inbox screen).
  4. (operator) Run the read-only SQL.
- **Expected:**
  - UI: Inbox lines '<Cairo time> · <state label> <MB-number link> [Mark read]' for Accepted, Preparing, Ready, Delivered (newest first); the entry you marked becomes muted and loses its button.
  - API: Customer notifications types, oldest to newest: ORDER_ACCEPTED, ORDER_PREPARING, ORDER_READY, ORDER_COMPLETED (4 items, each params.state = the new state, params.orderNumber = MB-…). No ORDER_PLACED for the customer. Cook notifications for the order: ORDER_PLACED only (1 item). unreadOnly excludes the item marked read (readAt set).
  - DB: SELECT n.type, n.recipient_id, n.params->>'state' AS state, n.read_at FROM notification n WHERE n.order_id = :'order' ORDER BY n.created_at; -> 4 customer rows + 1 cook row; UNIQUE (recipient_id, dedup_key) means no duplicates.
  - DB: SELECT channel, status, count(*) FROM notification_delivery d JOIN notification n ON n.id = d.notification_id WHERE n.order_id = :'order' GROUP BY 1,2; -> EMAIL rows only, no PUSH rows (push flag off by default, PUSH_DELIVERY_ENABLED=false); no real e-mail is sent in PREVIEW.
- **Also covered by automated tests:** NotificationIntegrationTest; PushFlagIntegrationTest
- **Unverified against code (confirm while executing):** Status the PREVIEW no-op e-mail provider writes into notification_delivery (SENT vs SKIPPED) is not confirmed.; Provider-failure and multi-worker behaviour (TC-121) needs a fault-injection hook on the isolated stack; none is documented.

### E2E-ORDER-118 — A customer opens a support case from an order; staff reply publicly and privately; the customer sees only public messages

- **Status:** NOT_RUN · **Priority:** P1 · **Readiness:** Ready after the extra synthetic fixtures are provisioned
- **Surface:** customer-web, operations-web, api · **Actors:** FX-CUST-A, FX-CUST-B, FX-STAFF-SUPPORT
- **Fixtures to provision:** FX-STAFF-SUPPORT
- **Traces to:** rules: BR-041; decisions: D05; operations: postSupportCases, getSupportCases, getOpsCases, postOpsCasesMessages; partial: TC-006, TC-041, TC-124
- **Prerequisites:**
  - Preview stack reachable: customer web https://app.<PREVIEW_DOMAIN>, cook/staff web https://ops.<PREVIEW_DOMAIN>, API https://api.<PREVIEW_DOMAIN>. seed-preview.sql is applied: on the customer web home, kitchen 'مطبخ المعاينة' lists 'Preview koshari' (EGP 75.00) and 'Preview mahshi' (EGP 180.00), and kitchen 'مطبخ تجريبي تاني' lists 'Preview molokhia' (EGP 120.25).
  - FX-CUST-A signs in (password from the private fixture sheet) and GET /api/v1/me returns phoneVerified=true.
  - In the DevTools Console of the signed-in tab, define the helper `api` from conventions.api_helper (needed for the API steps).
  - A read-only database method approved by the operator is available (psql through the operator channel); replace :'order' with the order UUID.
  - A completed or active order O1 of FX-CUST-A; FX-STAFF-SUPPORT holds support.case (PROVISION).
  - Each actor uses a SEPARATE browser profile (or private window/cookie store): customer web, ops web and the API share one session cookie (SecurityConfiguration), so signing in as a second actor in the same profile silently replaces the first. Before each actor's steps run `await api('GET','/api/v1/me')` (helper in conventions.api_helper) and confirm the displayName is that actor.
- **Steps:**
  1. (FX-CUST-A) Customer web > 'My account' > 'Get help with an order': Order = O1's MB- number, Problem type 'Late', 'Describe the problem' = 'The order is late by test', press the button 'Get help with an order'.
  2. (FX-CUST-A) Open O1: link 'Support cases for this order'.
  3. (FX-STAFF-SUPPORT) Ops web > 'Support': open the case; press 'Assign to me'; send an 'Internal note' and then a 'Message to customer'. — data: internal: 'checking with kitchen'; public: 'We are checking with the kitchen'
  4. (FX-CUST-A) Open the case from the order page and read the messages.
  5. (FX-CUST-A) Console: POST /api/v1/support/cases {orderId:O1, category:'PAYMENT', description:'a long enough description'}, then category 'CANCELLATION_REQUEST', then category 'LATE' with description 'short'.
  6. (FX-CUST-B) Console: POST /api/v1/support/cases {orderId:O1,...} and GET /api/v1/support/cases/<caseId>.
- **Expected:**
  - UI: Customer sees the case reference (CS-XXXXXXXX), category 'Late', and only the message 'We are checking with the kitchen'; the internal note is not visible. Order page lists the case link.
  - API: Case creation 201 category LATE severity HIGH. PAYMENT / CANCELLATION_REQUEST: 422 VALIDATION_FAILED fieldErrors category 'ALLOWED_CASH|FOOD_SAFETY|HANDOVER|LATE|MISSING_ITEM|OTHER|QUALITY'. short description: 422 description LENGTH_10_4000. FX-CUST-B: 404 NOT_FOUND on both.
  - DB: SELECT category, severity, state, sla_due_at FROM support_case WHERE order_id = :'order'; -> LATE, HIGH, OPEN. SELECT visibility, author_role FROM case_message WHERE case_id = '<case>'; -> an internal row (visibility not public) and a public row. audit_event contains case.update when state/assignee changed.
- **Also covered by automated tests:** SupportIntegrationTest
- **Unverified against code (confirm while executing):** Names of the internal-note visibility values in case_message (V17) were not read; assert behaviour through the customer API.; Exact severity mapping applies only to categories in SupportService.insertCase (LATE/HANDOVER/CANCELLATION_REQUEST HIGH, FOOD_SAFETY CRITICAL, others NORMAL).

### E2E-ORDER-119 — Operations order monitoring shows exceptions and the timeline; opening customer contact is audited; no handover code is exposed

- **Status:** NOT_RUN · **Priority:** P1 · **Readiness:** Ready after the extra synthetic fixtures are provisioned
- **Surface:** operations-web, api · **Actors:** FX-STAFF-OPS, FX-STAFF-NOGRANT
- **Fixtures to provision:** FX-STAFF-OPS
- **Traces to:** rules: BR-006, BR-041; decisions: D05; operations: getOpsOrders, getOpsOrdersById; partial: TC-006, TC-124
- **Prerequisites:**
  - Preview stack reachable: customer web https://app.<PREVIEW_DOMAIN>, cook/staff web https://ops.<PREVIEW_DOMAIN>, API https://api.<PREVIEW_DOMAIN>. seed-preview.sql is applied: on the customer web home, kitchen 'مطبخ المعاينة' lists 'Preview koshari' (EGP 75.00) and 'Preview mahshi' (EGP 180.00), and kitchen 'مطبخ تجريبي تاني' lists 'Preview molokhia' (EGP 120.25).
  - Staff accounts for this case are PROVISIONED fixtures: grants are written by the operator (DB insert into user_grant) or by FX-STAFF-ADMIN in the ops 'Staff permissions' screen; granting through the API requires staff.grant AND recent MFA (StaffController), which needs the PR #9 Keycloak step-up flow.
  - A read-only database method approved by the operator is available (psql through the operator channel); replace :'order' with the order UUID.
  - One active order O1 (ACCEPTED) and one terminal order T1 exist; FX-STAFF-OPS holds order.ops.
  - Each actor uses a SEPARATE browser profile (or private window/cookie store): customer web, ops web and the API share one session cookie (SecurityConfiguration), so signing in as a second actor in the same profile silently replaces the first. Before each actor's steps run `await api('GET','/api/v1/me')` (helper in conventions.api_helper) and confirm the displayName is that actor.
- **Steps:**
  1. (FX-STAFF-OPS) Ops web > 'Order monitoring': press each filter ('All', 'Acceptance deadline near or past', 'Delivery failed', 'Cash disputed', 'Open support case').
  2. (FX-STAFF-OPS) Open O1 then T1 (click the MB- number).
  3. (FX-STAFF-NOGRANT) Open ops web and try GET /api/v1/ops/orders/<O1> from the console.
  4. (operator) Run the audit SQL.
- **Expected:**
  - UI: O1 detail shows the customer name and phone; T1 detail shows 'Contact details are not shown in this state.'; neither shows a handover code.
  - API: GET /api/v1/ops/orders/<O1> 200: order.handoverCode null, customerContact present for active states; T1 customerContact null. No-grant staff: 403.
  - DB: SELECT action, actor_id, object_id FROM audit_event WHERE action = 'access.order_contact' AND object_id = '<O1>'; -> one ALLOWED row per detail view of O1 (request_id set); none for T1.
- **Also covered by automated tests:** OrderLifecycleIntegrationTest

### E2E-ORDER-120 — One customer cannot read, cancel, pay-report or review another customer's order

- **Status:** NOT_RUN · **Priority:** P0 · **Readiness:** Ready to run manually now
- **Surface:** api, customer-web · **Actors:** FX-CUST-A, FX-CUST-B
- **Traces to:** scenarios: TC-001, TC-006; rules: BR-006, BR-001; decisions: D05; operations: getOrdersById, getOrdersEvents, postOrdersCancel, postOrdersPaymentTransferReport, postSupportCases
- **Prerequisites:**
  - Preview stack reachable: customer web https://app.<PREVIEW_DOMAIN>, cook/staff web https://ops.<PREVIEW_DOMAIN>, API https://api.<PREVIEW_DOMAIN>. seed-preview.sql is applied: on the customer web home, kitchen 'مطبخ المعاينة' lists 'Preview koshari' (EGP 75.00) and 'Preview mahshi' (EGP 180.00), and kitchen 'مطبخ تجريبي تاني' lists 'Preview molokhia' (EGP 120.25).
  - In the DevTools Console of the signed-in tab, define the helper `api` from conventions.api_helper (needed for the API steps).
  - Order OA (pending, COD) and deposit order OD (pending) of FX-CUST-A; FX-CUST-B signs in in a separate browser profile; a guest tab without session.
  - Each actor uses a SEPARATE browser profile (or private window/cookie store): customer web, ops web and the API share one session cookie (SecurityConfiguration), so signing in as a second actor in the same profile silently replaces the first. Before each actor's steps run `await api('GET','/api/v1/me')` (helper in conventions.api_helper) and confirm the displayName is that actor.
- **Steps:**
  1. (FX-CUST-B) Console: GET /api/v1/orders/<OA>; GET /api/v1/orders/<OA>/events; GET /api/v1/orders/by-request/<clientRequestId of OA>; POST /api/v1/orders/<OA>/cancel {expectedVersion:0, reasonCode:'OTHER'} (fresh Idempotency-Key); POST /api/v1/orders/<OD>/payment/transfer-report {}; POST /api/v1/orders/<OA>/cancellation-requests {expectedVersion:0, reasonCode:'OTHER'} (fresh Idempotency-Key); POST /api/v1/orders/<OA>/reviews {rating:5, text:'foreign order review attempt'}; POST /api/v1/support/cases {orderId:<OA>, category:'OTHER', description:'cross access attempt test'}.
  2. (FX-CUST-B) Browser: open https://app.<PREVIEW_DOMAIN>/#/orders/<OA>.
  3. (FX-CUST-B) GET /api/v1/orders?limit=100.
  4. (guest) Without signing in: GET https://api.<PREVIEW_DOMAIN>/api/v1/orders (curl or a private window).
- **Expected:**
  - UI: Step 2: the page shows the not-found error ('Not found.'), never order data.
  - API: Every call in step 1: 404 NOT_FOUND. Step 3: list excludes OA and OD. Step 4: 401 UNAUTHENTICATED.
  - DB: OA and OD unchanged (version, state); no support_case row for OA created by FX-CUST-B.
- **Also covered by automated tests:** OrderPlacementIntegrationTest; IdentityIntegrationTest

### E2E-ORDER-121 — Order, payment and ledger history cannot be altered or deleted, even by the application database role

- **Status:** NOT_RUN · **Priority:** P1 · **Readiness:** Isolated disposable stack only
- **Surface:** api · **Actors:** FX-ISOLATED · **Needs:** isolated stack · **Destructive**
- **Fixtures to provision:** FX-ISOLATED
- **Traces to:** rules: BR-012, BR-026, BR-033; decisions: D05; partial: TC-156
- **Prerequisites:**
  - FX-ISOLATED disposable stack with the runtime role (matbakhy_app) credentials known to the operator; at least one COMPLETED order O1 with ledger group, one deposit order with payment events.
  - These statements are attempted writes: never on the shared preview.
- **Steps:**
  1. (operator) As matbakhy_app run in separate transactions: UPDATE customer_order SET total_minor = 1 WHERE id = '<O1>'; DELETE FROM customer_order WHERE id = '<O1>'; UPDATE order_item SET unit_price_minor = 1 WHERE order_id = '<O1>'; DELETE FROM order_event WHERE order_id = '<O1>'; UPDATE payment_event SET amount_minor = 1; UPDATE order_payment SET deposit_minor = 1 WHERE order_id = '<deposit order>'; DELETE FROM ledger_entry; UPDATE audit_event SET reason = 'x';
  2. (operator) Compare row counts and checksums of the touched tables before and after.
- **Expected:**
  - UI: Not applicable.
  - API: Not applicable.
  - DB: Every statement fails (trigger exceptions such as 'terminal order … is immutable', 'order_event is append-only', 'ledger is append-only', 'order payment terms are immutable', 'audit_event is append-only', or permission denied); no row or checksum changes.
- **Also covered by automated tests:** BackupRestoreIntegrationTest; backend/app/src/main/resources/db/migration/afterMigrate.sql (grants)
- **Unverified against code (confirm while executing):** Exact exception texts; the contract is 'rejected and unchanged'. V21/V27 allow only the personal-data-erasure change on some tables.

### E2E-ORDER-122 — Native apps: place and track a pickup order with identical totals and states on iOS and Android

- **Status:** BLOCKED — No signed iOS/Android build pointing at the preview is confirmed; native verification is required by project rules but store setup is deferred. · **Priority:** P1 · **Readiness:** Needs a physical iOS/Android build
- **Surface:** customer-mobile · **Actors:** FX-CUST-A, FX-DEVICE-IOS, FX-DEVICE-ANDROID, FX-COOK-A · **Needs:** physical device
- **Traces to:** rules: BR-049, BR-019; decisions: D05; operations: postQuotes, postOrders; partial: TC-132, TC-163
- **Prerequisites:**
  - Signed iOS and Android builds pointing at the preview API; FX-CUST-A signed in; Preview stack reachable: customer web https://app.<PREVIEW_DOMAIN>, cook/staff web https://ops.<PREVIEW_DOMAIN>, API https://api.<PREVIEW_DOMAIN>. seed-preview.sql is applied: on the customer web home, kitchen 'مطبخ المعاينة' lists 'Preview koshari' (EGP 75.00) and 'Preview mahshi' (EGP 180.00), and kitchen 'مطبخ تجريبي تاني' lists 'Preview molokhia' (EGP 120.25).
  - FX-COOK-A signs in at https://ops.<PREVIEW_DOMAIN> and the tab bar shows 'Orders' (ar: الطلبات); GET /api/v1/cook/dashboard in that session returns 200.
  - Each actor uses a SEPARATE browser profile (or private window/cookie store): customer web, ops web and the API share one session cookie (SecurityConfiguration), so signing in as a second actor in the same profile silently replaces the first. Before each actor's steps run `await api('GET','/api/v1/me')` (helper in conventions.api_helper) and confirm the displayName is that actor.
- **Steps:**
  1. (FX-CUST-A) On each device: Discover > 'Preview koshari' > slot 13:00 tomorrow > add 2 > basket > 'Calculate total' > accept terms > 'Place order'.
  2. (FX-COOK-A) Accept, 'Start preparing' and 'Ready' on the web workspace.
  3. (FX-CUST-A) On each device open Orders > the order and read the state and 'Handover code'; pull to refresh once. Then cook confirms handover.
  4. (FX-CUST-A) Repeat the placement once in Arabic (RTL) and once in English (LTR) on each device.
- **Expected:**
  - UI: Both platforms: quote Food 150.00 / Delivery 0.00 / Tax 0.00 / Total 150.00; order states 'Waiting for the kitchen', 'Accepted', 'Preparing', 'Ready' (with 'Handover code'), 'Delivered'; layout mirrors correctly in RTL.
  - API: Same API responses as the web (order total 15000, version increments).
  - DB: One customer_order row per placement.
- **Also covered by automated tests:** apps/customer-mobile/src/App.test.tsx (jest); .github/workflows/native-build.yml (CI build only)

### E2E-ORDER-123 — Parallel duplicate submit of the same placement creates exactly one order and one reservation

- **Status:** NOT_RUN · **Priority:** P1 · **Readiness:** Isolated disposable stack only
- **Surface:** api · **Actors:** FX-CUST-A · **Needs:** isolated stack
- **Traces to:** rules: BR-023, BR-024, BR-025; decisions: D05; operations: postOrders; partial: TC-023, TC-058, TC-118
- **Prerequisites:**
  - FX-ISOLATED disposable stack with synthetic data (do NOT run on the shared preview: it fires parallel writes).
  - FX-CUST-A signs in (password from the private fixture sheet) and GET /api/v1/me returns phoneVerified=true.
  - In the DevTools Console of the signed-in tab, define the helper `api` from conventions.api_helper (needed for the API steps).
  - A read-only database method approved by the operator is available (psql through the operator channel); replace :'order' with the order UUID.
  - A valid quote q for 2 × koshari exists (recipe) and K/C are generated as in E2E-ORDER-022.
- **Steps:**
  1. (FX-CUST-A) Fire 10 identical POST /api/v1/orders requests (same K, same C, same body) at once. — data: await Promise.all(Array.from({length:10},()=>api('POST','/api/v1/orders',body,K)))
  2. (operator) Run the read-only SQL after the burst.
- **Expected:**
  - UI: Not applicable (API only).
  - API: Every response is 201 with the same order id, or a retryable 409/503 that is not a second order; no response carries a different order id; no 500.
  - DB: SELECT count(*) FROM customer_order WHERE client_request_id = '<C>'; -> 1.
  - DB: stock_allocation rows for that order -> 1; cook_slot.reserved increased by 2 only.
  - DB: Run infra/db/reconcile.sql: every check returns violations = 0.
- **Also covered by automated tests:** OrderPlacementIntegrationTest
- **Unverified against code (confirm while executing):** Exact status mix of the losing parallel requests (the code serialises on a pg advisory lock and replays; asserting 'no second order' is the contract).

### E2E-FIN-038 — Cash on receipt (delivery order): completion leaves cash due; the kitchen's report and finance verification are separate steps with the exact amount

- **Status:** NOT_RUN · **Priority:** P0 · **Readiness:** Ready after the extra synthetic fixtures are provisioned
- **Surface:** operations-web, api · **Actors:** FX-COOK-A, FX-STAFF-FINANCE
- **Fixtures to provision:** FX-STAFF-FINANCE
- **Traces to:** rules: BR-032, BR-033; decisions: D05, D10; operations: postCookOrdersCashReports, getOpsFinanceCashReports, postOpsFinanceCashReportsVerification, getOpsFinanceStatements, getCookStatements; partial: TC-032, TC-120
- **Prerequisites:**
  - Preview stack reachable: customer web https://app.<PREVIEW_DOMAIN>, cook/staff web https://ops.<PREVIEW_DOMAIN>, API https://api.<PREVIEW_DOMAIN>. seed-preview.sql is applied: on the customer web home, kitchen 'مطبخ المعاينة' lists 'Preview koshari' (EGP 75.00) and 'Preview mahshi' (EGP 180.00), and kitchen 'مطبخ تجريبي تاني' lists 'Preview molokhia' (EGP 120.25).
  - FX-COOK-A signs in at https://ops.<PREVIEW_DOMAIN> and the tab bar shows 'Orders' (ar: الطلبات); GET /api/v1/cook/dashboard in that session returns 200.
  - A read-only database method approved by the operator is available (psql through the operator channel); replace :'order' with the order UUID.
  - FX-STAFF-FINANCE (PROVISION) holds finance.read, finance.verify, finance.refund_approve and finance.settlement.
  - Each actor uses a SEPARATE browser profile (or private window/cookie store): customer web, ops web and the API share one session cookie (SecurityConfiguration), so signing in as a second actor in the same profile silently replaces the first. Before each actor's steps run `await api('GET','/api/v1/me')` (helper in conventions.api_helper) and confirm the displayName is that actor.
  - A COMPLETED delivery/COD order O2 (total 28500 = 25500 meals + 3000 delivery) from E2E-ORDER-102 whose cash is still DUE. Cash verification has no ops UI: use the console on https://ops.<PREVIEW_DOMAIN> with the helper `api` (conventions.api_helper).
- **Steps:**
  1. (operator) Right after completion read SQL_CASH for O2: cash_obligation. — data: SELECT amount_minor, state FROM cash_obligation WHERE order_id = :'order';
  2. (FX-STAFF-FINANCE) Ops web > 'Finance': kitchen id 00000000-0000-4000-8000-0000000bd001 > 'Show'. Record the statement value 'Cash verified' as S0 (it sums ALL verified cash of the kitchen in the Cairo month-to-date period, not only this order). — data: S0
  3. (FX-COOK-A) Ops web > 'Orders' > 'Completed': on O2 the card says 'Cash due' and shows 'Report cash collected'. Amount (EGP) is pre-filled 285.00; Reference 'TEST-CASH-001'; press 'Submit'. — data: 28500, EGP, CASH
  4. (FX-STAFF-FINANCE) Console: GET /api/v1/ops/finance/cash-reports?state=REPORTED&limit=20; pick the item of O2 (publicNumber) and its report {id, version}.
  5. (FX-STAFF-FINANCE) POST /api/v1/ops/finance/cash-reports/<report id>/verification with a fresh Idempotency-Key K and {decision:'VERIFY', verifiedAmountMinor:28500, reference:'TEST-VER-1', expectedVersion:<report.version>}.
  6. (FX-STAFF-FINANCE) Repeat the call (a) with the same key K and same body; (b) with a NEW key and the ORIGINAL expectedVersion; (c) with a NEW key and the CURRENT version (original + 1).
  7. (FX-STAFF-FINANCE) Ops web > 'Finance' > 'Show' again (reload) and read 'Cash verified' (S1) and the row of O2.
  8. (FX-COOK-A) Ops web > tab 'Statement' and read the same figures.
- **Expected:**
  - UI: Step 3: after Submit the card text reads 'Reported' and the form disappears.
  - UI: Step 7: statement lists '<MB-number> · 285.00 · Verified'; 'Cash verified' S1 = S0 + 285.00 (increase asserted, not an absolute value); banner 'No platform commission: the whole order amount is yours.' (the text '(owner decision D10)' belongs to the operating-configuration screen only).
  - UI: Step 8: cook statement shows the same row and the same banner; no commission column.
  - API: Report 201: amount 28500 EGP, discrepancy 0, state REPORTED, version 0. Verification 200: state VERIFIED, verifiedAmount 28500. 6a: same key and body -> 200 identical. 6b: 409 VERSION_CONFLICT (the version check precedes the state check, CashService.verify). 6c: 409 STATE_CONFLICT fieldErrors [{state,'VERIFIED'}].
  - DB: Before step 3: cash_obligation 28500 state DUE although the order is COMPLETED (BR-032).
  - DB: SELECT amount_minor, discrepancy_minor, state, verified_amount_minor, verified_by, reporter_id FROM cash_report WHERE order_id = :'order'; -> 28500, 0, VERIFIED, 28500; verified_by is the finance user and differs from reporter_id (cash_report_separation_ck).
  - DB: cash_obligation -> VERIFIED. SELECT action, outcome, details FROM audit_event WHERE action = 'cash.verify' AND object_id = '<report id>'; -> one ALLOWED row, details {decision:'VERIFY', verifiedAmountMinor:28500}.
- **Also covered by automated tests:** CashAndLedgerIntegrationTest; FinanceOperationsIntegrationTest
- **Unverified against code (confirm while executing):** Whether the finance staff browser session can call the API from the ops origin console (XSRF cookie scope).; Statement cashVerified = SUM(cash_obligation.amount_minor) of VERIFIED obligations whose COMPLETED event falls in the Cairo date range (StatementService); other orders verified in the same month change S0.

### E2E-FIN-039 — Core journey 3: pickup + 50% deposit, from deposit report to verified remaining cash

- **Status:** NOT_RUN · **Priority:** P0 · **Readiness:** Needs the updated application deployment
- **Surface:** customer-web, operations-web, api · **Actors:** FX-CUST-A, FX-COOK-A, FX-STAFF-FINANCE · **Needs:** updated deploy
- **Fixtures to provision:** FX-STAFF-FINANCE
- **Traces to:** rules: BR-019, BR-022, BR-026, BR-032; decisions: D16, D10, D05; operations: postQuotes, postOrders, postOrdersPaymentTransferReport, postCookOrdersPaymentReceipt, postCookOrdersCashReports, postOpsFinanceCashReportsVerification, getOpsFinanceStatements; partial: TC-032, TC-157, TC-158, TC-159, TC-163
- **Prerequisites:**
  - Preview stack reachable: customer web https://app.<PREVIEW_DOMAIN>, cook/staff web https://ops.<PREVIEW_DOMAIN>, API https://api.<PREVIEW_DOMAIN>. seed-preview.sql is applied: on the customer web home, kitchen 'مطبخ المعاينة' lists 'Preview koshari' (EGP 75.00) and 'Preview mahshi' (EGP 180.00), and kitchen 'مطبخ تجريبي تاني' lists 'Preview molokhia' (EGP 120.25).
  - FX-CUST-A signs in (password from the private fixture sheet) and GET /api/v1/me returns phoneVerified=true.
  - FX-COOK-A signs in at https://ops.<PREVIEW_DOMAIN> and the tab bar shows 'Orders' (ar: الطلبات); GET /api/v1/cook/dashboard in that session returns 200.
  - Kitchen A (00000000-0000-4000-8000-0000000bd001) has OPEN 13:00 Africa/Cairo slots for the next 3 days: in the DevTools Console on app.<PREVIEW_DOMAIN> `(await api('GET','/api/v1/kitchens/00000000-0000-4000-8000-0000000bd001/slots')).body.items` has >= 3 items and items[0].availablePortions >= 6 (seed: 20 portions per slot, 10 per meal).
  - Kitchen A payment profile is the seeded one: deposit 50% (deposit_bps 5000), no COD cap, recipient INSTAPAY 'demo-kitchen@instapay.invalid' / 'Demo kitchen (not a real account)' with recipient_status APPROVED (verify: GET /api/v1/cook/payment-profile as FX-COOK-A).
  - A read-only database method approved by the operator is available (psql through the operator channel); replace :'order' with the order UUID.
  - In the DevTools Console of the signed-in tab, define the helper `api` from conventions.api_helper (needed for the API steps).
  - FX-STAFF-FINANCE (PROVISION) holds finance.read, finance.verify, finance.refund_approve and finance.settlement.
  - On origin/develop the cash-report step produces a false DISPUTED (discrepancy = -deposit) because CashService compared the report with the order total; PR #9 compares with the cash obligation. Run this case against a PR #9 deployment.
  - Slot assignment: use the seeded slot of day 2 (items[1]) (GET /api/v1/kitchens/00000000-0000-4000-8000-0000000bd001/slots, same index each run). Preparation consumes capacity permanently and each meal-slot has only 10 portions: before the case read the AGGREGATE slot availablePortions (max 20) from the kitchen slots response (SlotAvailability exposes aggregate availability only) AND the meal-specific availability from the meal's nextSlots[].availablePortions (GET /api/v1/meals/<mealId>; the owning cook's slot response also lists per-meal capacity) and switch to another seeded day if fewer portions than the case orders remain. E2E-ORDER-114/115 own the day-3 slot; do not use it here.
  - Each actor uses a SEPARATE browser profile (or private window/cookie store): customer web, ops web and the API share one session cookie (SecurityConfiguration), so signing in as a second actor in the same profile silently replaces the first. Before each actor's steps run `await api('GET','/api/v1/me')` (helper in conventions.api_helper) and confirm the displayName is that actor.
- **Steps:**
  1. (FX-CUST-A) Customer web: add 2 × 'Preview koshari' (day-2 slot: 13:00 Africa/Cairo on today+2; state the Cairo date in the evidence), open 'Cart', mode 'Pickup from the kitchen', pick the radio 'Deposit sent straight to the kitchen', press 'Calculate total'. Read the quote including the box data-testid="deposit-terms".
  2. (FX-CUST-A) Tick the terms box and press 'Place order'. On the order page read the box data-testid="payment-status".
  3. (FX-CUST-A) Type 'TEST-REF-0001' in 'Transfer reference (optional)' and press 'I sent the deposit'. Nothing is actually transferred in the preview. — data: reference=TEST-REF-0001
  4. (FX-COOK-A) Ops web > 'Orders' > 'Active': on the card read the 'Deposit' panel, then press 'Accept'.
  5. (FX-COOK-A) In the Deposit panel press 'The deposit arrived (EGP 75.00)' (the kitchen states that it saw the money in its own account).
  6. (FX-COOK-A) Press 'Start preparing', then 'Ready'.
  7. (FX-CUST-A) Reload the order page, read the 6-digit 'Handover code'.
  8. (FX-COOK-A) Enter the code in 'Customer handover code' and press 'Confirm handover'.
  9. (FX-COOK-A) Orders > 'Completed': on the card the 'Report cash collected' form shows Amount (EGP) 75.00 (the remainder). Reference 'TEST-CASH-DEP-1', press 'Submit'. — data: 75.00
  10. (FX-STAFF-FINANCE) Console on ops origin: GET /api/v1/ops/finance/cash-reports?state=REPORTED, take the report id/version for this order; POST /api/v1/ops/finance/cash-reports/<reportId>/verification {decision:'VERIFY', verifiedAmountMinor:7500, expectedVersion:<version>} with a fresh Idempotency-Key. — data: 7500
  11. (FX-STAFF-FINANCE) Ops web > 'Finance': enter kitchen id 00000000-0000-4000-8000-0000000bd001, press 'Show', read the statement.
  12. (FX-CUST-A) Reload the order page and 'My account' > 'Notifications'.
- **Expected:**
  - UI: Step 1 box: 'Deposit now: 75.00 · Rest on receipt: 75.00', 'InstaPay: demo-kitchen@instapay.invalid · Demo kitchen (not a real account)', 'Send the deposit before <acceptBy time>.', 'You pay the kitchen's account directly; the platform never receives or holds the money. The kitchen confirms after checking its own account; your report alone is not proof.'
  - UI: Step 2: 'Payment: Waiting for your deposit' + the reference field and 'I sent the deposit'. Step 3: 'Payment: You reported the transfer; waiting for the kitchen to confirm' and 'Preparation starts only after the kitchen confirms the deposit reached its account.' (no bank-verified wording anywhere).
  - UI: Step 4: cook panel 'Deposit: 75.00 · rest on hand-over 75.00 · Customer reported a transfer' and the notice 'Check your own account before reporting; what you record is your statement, not a bank confirmation.' Step 5: badge 'Kitchen reported receipt'.
  - UI: Step 12: customer page 'Delivered', Payment panel 'The kitchen reported receiving the deposit'; inbox contains 'Deposit: the kitchen reported receiving it'. Step 11: statement row '<MB-number> · 150.00 · Verified'; 'Cash verified' rises by 75.00 compared with the value recorded before step 10 (the figure sums all verified cash of the kitchen in the month); no commission lines; banner 'No platform commission: the whole order amount is yours.'
  - API: Quote 201: total 15000, payment {method DEPOSIT, depositBps 5000, deposit 7500, remainder 7500, recipient INSTAPAY …}. Order payment {state WAITING_FOR_DEPOSIT, version 0, depositExpiresAt = acceptBy}; after the customer report CUSTOMER_TRANSFER_REPORTED; after the kitchen report KITCHEN_RECEIPT_REPORTED.
  - API: POST /api/v1/cook/orders/<id>/cash-reports 201: amount 7500, discrepancy 0, state REPORTED. Verification 200 state VERIFIED verifiedAmount 7500.
  - DB: SELECT method, state, total_minor, deposit_minor, remainder_minor, deposit_expires_at, version FROM order_payment WHERE order_id = :'order'; -> DEPOSIT, KITCHEN_RECEIPT_REPORTED, total 15000, deposit 7500, remainder 7500 (terms immutable).
  - DB: SELECT from_state, to_state, actor_type, amount_minor, reference, reason_code FROM payment_event WHERE order_id = :'order' ORDER BY occurred_at, id; -> NULL→WAITING_FOR_DEPOSIT (CUSTOMER, amount 7500), →CUSTOMER_TRANSFER_REPORTED (CUSTOMER, amount NULL, reference TEST-REF-0001), →KITCHEN_RECEIPT_REPORTED (COOK, amount 7500).
  - DB: SELECT amount_minor, state FROM cash_obligation WHERE order_id = :'order'; -> amount_minor 7500; SELECT amount_minor, discrepancy_minor, state, verified_amount_minor, verified_by, reporter_id FROM cash_report WHERE order_id = :'order'; -> 7500, 0, VERIFIED, 7500, verified_by <> reporter_id.
  - DB: SELECT count(*) FROM support_case WHERE order_id = :'order' AND category = 'PAYMENT'; -> 0 (nothing disputed). SELECT count(*) FROM refund WHERE order_id = :'order'; -> 0.
  - DB: SELECT g.kind, g.rate_bps, g.base_minor, e.account, e.direction, e.amount_minor FROM ledger_group g JOIN ledger_entry e ON e.group_id = g.id WHERE g.order_id = :'order' ORDER BY g.posted_at, e.direction; -> poll (bounded, up to 90 s; LedgerPostingJob runs every 30 s, asynchronously) until the COMPLETED event has its kind COMMISSION group, then assert exactly two entries (COOK_COMMISSION_RECEIVABLE DEBIT and PLATFORM_COMMISSION_REVENUE CREDIT), both amount_minor 0, balanced: deposits never enter the ledger.
- **Also covered by automated tests:** DepositPaymentIntegrationTest; CashAndLedgerIntegrationTest
- **Unverified against code (confirm while executing):** Exact format of the deposit button label amount ('EGP 75.00') depends on Intl en-EG.; There is no ops UI to verify a cash report; the API call needs a session with the finance staff cookies (console on the ops origin).; Record 'Cash verified' before step 10 to assert the +75.00 increase; absolute values depend on other verified orders in the month.

### E2E-FIN-040 — Kitchen reports 'Not received': payment becomes DISPUTED, a PAYMENT support case opens, the order is not changed

- **Status:** NOT_RUN · **Priority:** P0 · **Readiness:** Ready after the extra synthetic fixtures are provisioned
- **Surface:** operations-web, customer-web, api · **Actors:** FX-CUST-A, FX-COOK-A, FX-STAFF-FINANCE
- **Fixtures to provision:** FX-STAFF-FINANCE
- **Traces to:** rules: BR-026, BR-041; decisions: D16; operations: postCookOrdersPaymentProblem, getOpsPayments, postSupportCases; partial: TC-041, TC-159, TC-161
- **Prerequisites:**
  - Preview stack reachable: customer web https://app.<PREVIEW_DOMAIN>, cook/staff web https://ops.<PREVIEW_DOMAIN>, API https://api.<PREVIEW_DOMAIN>. seed-preview.sql is applied: on the customer web home, kitchen 'مطبخ المعاينة' lists 'Preview koshari' (EGP 75.00) and 'Preview mahshi' (EGP 180.00), and kitchen 'مطبخ تجريبي تاني' lists 'Preview molokhia' (EGP 120.25).
  - FX-COOK-A signs in at https://ops.<PREVIEW_DOMAIN> and the tab bar shows 'Orders' (ar: الطلبات); GET /api/v1/cook/dashboard in that session returns 200.
  - A read-only database method approved by the operator is available (psql through the operator channel); replace :'order' with the order UUID.
  - In the DevTools Console of the signed-in tab, define the helper `api` from conventions.api_helper (needed for the API steps).
  - Kitchen A payment profile is the seeded one: deposit 50% (deposit_bps 5000), no COD cap, recipient INSTAPAY 'demo-kitchen@instapay.invalid' / 'Demo kitchen (not a real account)' with recipient_status APPROVED (verify: GET /api/v1/cook/payment-profile as FX-COOK-A).
  - FX-STAFF-FINANCE (PROVISION) holds finance.read, finance.verify, finance.refund_approve and finance.settlement.
  - An ACCEPTED deposit order D1 (pickup, total 15000) whose customer transfer is reported (CUSTOMER_TRANSFER_REPORTED).
  - Each actor uses a SEPARATE browser profile (or private window/cookie store): customer web, ops web and the API share one session cookie (SecurityConfiguration), so signing in as a second actor in the same profile silently replaces the first. Before each actor's steps run `await api('GET','/api/v1/me')` (helper in conventions.api_helper) and confirm the displayName is that actor.
- **Steps:**
  1. (FX-COOK-A) Check your own account (nothing arrived in the preview). In the Deposit panel of D1 press 'Not received'.
  2. (FX-CUST-A) Reload the order page and 'My account' > 'Notifications'.
  3. (FX-CUST-A) Open the link 'Support cases for this order'.
  4. (FX-CUST-A) Console: POST /api/v1/support/cases {orderId:D1, category:'PAYMENT', description:'trying to open a payment case'}.
  5. (FX-COOK-A) Press 'Start preparing'.
  6. (FX-STAFF-FINANCE) Ops web > 'Finance' > 'Payments needing attention' > filter 'Payment disputed'.
  7. (FX-STAFF-FINANCE) Console: POST /api/v1/ops/orders/<D1>/payment/decision {expectedVersion:<payment version>, outcome:'REFUND_OWED', reason:'attempt while the order is active'} (needs a recent MFA proof).
- **Expected:**
  - UI: Step 1: Deposit panel badge 'Payment disputed'. Step 2: 'Payment: The kitchen did not find the transfer or found a different amount' plus 'Contact support from the order page with your transfer reference.'; inbox entry 'Deposit: the kitchen did not find the transfer or found a different amount'; order still 'Accepted'. Step 3: a case with category 'Deposit review' (High). Step 6: D1 listed with the line 'The order is still active; waiting for the kitchen's report.' (no decision form while the order is active).
  - API: Step 1: 200 PaymentView state DISPUTED. Step 4: 422 VALIDATION_FAILED (customers cannot open PAYMENT cases). Step 5: 409 STATE_CONFLICT payment DEPOSIT_NOT_RECEIVED. Step 7: 409 STATE_CONFLICT fieldErrors [{orderId,'ORDER_ACTIVE'}] (a decision is possible only after the order ended; without MFA 403 STEP_UP_REQUIRED comes first).
  - DB: SELECT from_state, to_state, actor_type, amount_minor, reference, reason_code FROM payment_event WHERE order_id = :'order' ORDER BY occurred_at, id; -> last row CUSTOMER_TRANSFER_REPORTED→DISPUTED actor COOK reason_code NOT_RECEIVED.
  - DB: SELECT category, severity, state FROM support_case WHERE order_id = :'order' ORDER BY created_at; -> exactly one PAYMENT/HIGH/OPEN case (partial unique index support_case_open_payment_uq); its description is system text with no account, reference or amount.
  - DB: SELECT state, version, subtotal_minor, delivery_fee_minor, tax_minor, total_minor, commission_bps_snapshot, commission_minor, accept_by, terminal_at FROM customer_order WHERE id = :'order'; -> still ACCEPTED, version unchanged by the dispute.
  - DB: SELECT n.type, n.recipient_id, n.params->>'state' AS state, n.read_at FROM notification n WHERE n.order_id = :'order' ORDER BY n.created_at; -> a customer notification PAYMENT_DISPUTED.
- **Also covered by automated tests:** DepositPaymentIntegrationTest; PaymentReconciliationIntegrationTest

### E2E-FIN-041 — Clients cannot dictate payment amounts; the kitchen receipt must equal the server-computed deposit

- **Status:** NOT_RUN · **Priority:** P1 · **Readiness:** Ready to run manually now
- **Surface:** api · **Actors:** FX-CUST-A, FX-COOK-A
- **Traces to:** rules: BR-019; decisions: D16; operations: postQuotes, postCookOrdersPaymentReceipt, putCookPaymentProfile; partial: TC-117, TC-158, TC-161
- **Prerequisites:**
  - Preview stack reachable: customer web https://app.<PREVIEW_DOMAIN>, cook/staff web https://ops.<PREVIEW_DOMAIN>, API https://api.<PREVIEW_DOMAIN>. seed-preview.sql is applied: on the customer web home, kitchen 'مطبخ المعاينة' lists 'Preview koshari' (EGP 75.00) and 'Preview mahshi' (EGP 180.00), and kitchen 'مطبخ تجريبي تاني' lists 'Preview molokhia' (EGP 120.25).
  - A read-only database method approved by the operator is available (psql through the operator channel); replace :'order' with the order UUID.
  - In the DevTools Console of the signed-in tab, define the helper `api` from conventions.api_helper (needed for the API steps).
  - Kitchen A payment profile is the seeded one: deposit 50% (deposit_bps 5000), no COD cap, recipient INSTAPAY 'demo-kitchen@instapay.invalid' / 'Demo kitchen (not a real account)' with recipient_status APPROVED (verify: GET /api/v1/cook/payment-profile as FX-COOK-A).
  - A pending deposit order D1 (total 15000, deposit 7500) with the customer's transfer reported; cook session on ops origin; D1 payment version v from GET /api/v1/cook/orders/<D1> (payment.version).
  - Each actor uses a SEPARATE browser profile (or private window/cookie store): customer web, ops web and the API share one session cookie (SecurityConfiguration), so signing in as a second actor in the same profile silently replaces the first. Before each actor's steps run `await api('GET','/api/v1/me')` (helper in conventions.api_helper) and confirm the displayName is that actor.
- **Steps:**
  1. (FX-CUST-A) POST /api/v1/quotes with an extra field {…, depositMinor: 20000} and another with {…, paymentMethod:'DEPOSIT', totalMinor: 1}.
  2. (FX-COOK-A) POST /api/v1/cook/orders/<D1>/payment/receipt {expectedVersion:v, amountMinor:15000}; then 7000; then 0. — data: amounts above, below, zero
  3. (FX-COOK-A) PUT /api/v1/cook/payment-profile with depositBps 10001; then with codMaxTotalMinor -1 (use the profile's current version).
  4. (FX-COOK-A) POST the receipt with amountMinor 7500 and a stale version (v-1).
- **Expected:**
  - UI: Not applicable.
  - API: 1: 400 MALFORMED_REQUEST (unknown field). 2: 15000 and 7000 -> 422 VALIDATION_FAILED fieldErrors [{amountMinor,'DOES_NOT_MATCH_DEPOSIT'}]; 0 -> 422 VALIDATION_FAILED (must be positive). 3: 422 depositBps RANGE_0_10000 and 422 codMaxTotalMinor NOT_NEGATIVE; the profile is unchanged. 4: 409 VERSION_CONFLICT.
  - DB: SELECT method, state, total_minor, deposit_minor, remainder_minor, deposit_expires_at, version FROM order_payment WHERE order_id = :'order'; -> state still CUSTOMER_TRANSFER_REPORTED, deposit_minor 7500, version unchanged; no KITCHEN_RECEIPT_REPORTED event. quote_payment_ck (deposit_minor <= total_minor) means no stored quote can ever exceed its total: SELECT count(*) FROM quote WHERE deposit_minor > total_minor; -> 0.
- **Also covered by automated tests:** DepositPaymentIntegrationTest
- **Unverified against code (confirm while executing):** Whether the receipt body with amountMinor 0 returns 422 (bean validation @Positive) as expected.; Rounding half-up of the deposit (e.g. 1 piastre) cannot be hit with seeded prices at 50%; covered by DepositPaymentIntegrationTest.

### E2E-FIN-042 — Repeated customer transfer reports are idempotent and are information only

- **Status:** NOT_RUN · **Priority:** P1 · **Readiness:** Ready to run manually now
- **Surface:** customer-web, api · **Actors:** FX-CUST-A, FX-CUST-B, FX-COOK-A
- **Traces to:** rules: BR-024, BR-019; decisions: D16; operations: postOrdersPaymentTransferReport; partial: TC-159, TC-163
- **Prerequisites:**
  - Preview stack reachable: customer web https://app.<PREVIEW_DOMAIN>, cook/staff web https://ops.<PREVIEW_DOMAIN>, API https://api.<PREVIEW_DOMAIN>. seed-preview.sql is applied: on the customer web home, kitchen 'مطبخ المعاينة' lists 'Preview koshari' (EGP 75.00) and 'Preview mahshi' (EGP 180.00), and kitchen 'مطبخ تجريبي تاني' lists 'Preview molokhia' (EGP 120.25).
  - FX-CUST-A signs in (password from the private fixture sheet) and GET /api/v1/me returns phoneVerified=true.
  - A read-only database method approved by the operator is available (psql through the operator channel); replace :'order' with the order UUID.
  - In the DevTools Console of the signed-in tab, define the helper `api` from conventions.api_helper (needed for the API steps).
  - Kitchen A payment profile is the seeded one: deposit 50% (deposit_bps 5000), no COD cap, recipient INSTAPAY 'demo-kitchen@instapay.invalid' / 'Demo kitchen (not a real account)' with recipient_status APPROVED (verify: GET /api/v1/cook/payment-profile as FX-COOK-A).
  - A pending deposit order D1 and a pending COD order P1 of FX-CUST-A.
  - Each actor uses a SEPARATE browser profile (or private window/cookie store): customer web, ops web and the API share one session cookie (SecurityConfiguration), so signing in as a second actor in the same profile silently replaces the first. Before each actor's steps run `await api('GET','/api/v1/me')` (helper in conventions.api_helper) and confirm the displayName is that actor.
- **Steps:**
  1. (FX-CUST-A) On D1 press 'I sent the deposit' once; in the console POST /api/v1/orders/<D1>/payment/transfer-report {reference:'TEST-REF-0002'} twice more. (The payment endpoints take no Idempotency-Key header.)
  2. (FX-CUST-A) POST the report with a 65-character reference; with an extra field {amountMinor:1}; on the COD order P1.
  3. (FX-CUST-B) POST the report on D1.
  4. (FX-COOK-A) Console: GET /api/v1/notifications?limit=20.
- **Expected:**
  - UI: After the first press the form disappears and the status reads 'You reported the transfer; waiting for the kitchen to confirm'. No text claims the money arrived.
  - API: Repeat reports: 200 with the same payment state CUSTOMER_TRANSFER_REPORTED and the same version. 65-char reference: 422 VALIDATION_FAILED; extra field: 400 MALFORMED_REQUEST; COD order: 409 STATE_CONFLICT fieldErrors [{payment,'NO_DEPOSIT_WAITING'}]; FX-CUST-B: 404 NOT_FOUND.
  - DB: SELECT count(*) FROM payment_event WHERE order_id = '<D1>' AND to_state = 'CUSTOMER_TRANSFER_REPORTED'; -> 1. order_payment.version = initial + 1. SELECT count(*) FROM notification WHERE order_id = '<D1>' AND type = 'PAYMENT_CUSTOMER_TRANSFER_REPORTED'; -> 1 (cook).
- **Also covered by automated tests:** DepositPaymentIntegrationTest; PaymentReconciliationIntegrationTest
- **Unverified against code (confirm while executing):** A race of the report with the kitchen receipt/expiry needs the isolated stack and is covered by DepositPaymentIntegrationTest#a_transfer_report_racing_expiry_is_reconciled_or_refused_never_lost.

### E2E-FIN-043 — A cash shortfall becomes a dispute for finance; the original order snapshot is untouched

- **Status:** NOT_RUN · **Priority:** P1 · **Readiness:** Ready after the extra synthetic fixtures are provisioned
- **Surface:** operations-web, api · **Actors:** FX-COOK-A, FX-STAFF-FINANCE, FX-STAFF-OPS
- **Fixtures to provision:** FX-STAFF-FINANCE, FX-STAFF-OPS
- **Traces to:** rules: BR-032; decisions: D05; operations: postCookOrdersCashReports, postOpsFinanceCashReportsVerification, getOpsOrders; partial: TC-032, TC-061, TC-120
- **Prerequisites:**
  - Preview stack reachable: customer web https://app.<PREVIEW_DOMAIN>, cook/staff web https://ops.<PREVIEW_DOMAIN>, API https://api.<PREVIEW_DOMAIN>. seed-preview.sql is applied: on the customer web home, kitchen 'مطبخ المعاينة' lists 'Preview koshari' (EGP 75.00) and 'Preview mahshi' (EGP 180.00), and kitchen 'مطبخ تجريبي تاني' lists 'Preview molokhia' (EGP 120.25).
  - FX-COOK-A signs in at https://ops.<PREVIEW_DOMAIN> and the tab bar shows 'Orders' (ar: الطلبات); GET /api/v1/cook/dashboard in that session returns 200.
  - A read-only database method approved by the operator is available (psql through the operator channel); replace :'order' with the order UUID.
  - FX-STAFF-FINANCE (PROVISION) holds finance.read, finance.verify, finance.refund_approve and finance.settlement.
  - A COMPLETED pickup/COD order O1 (total 15000, cash DUE). FX-STAFF-OPS holds order.ops.
  - Each actor uses a SEPARATE browser profile (or private window/cookie store): customer web, ops web and the API share one session cookie (SecurityConfiguration), so signing in as a second actor in the same profile silently replaces the first. Before each actor's steps run `await api('GET','/api/v1/me')` (helper in conventions.api_helper) and confirm the displayName is that actor.
- **Steps:**
  1. (FX-COOK-A) Orders > 'Completed' > O1: set Amount (EGP) to 140.00, Reference 'short-test', press 'Submit'. — data: 14000
  2. (FX-STAFF-FINANCE) Console: GET /api/v1/ops/finance/cash-reports?state=DISPUTED; take the report {id, version}.
  3. (FX-STAFF-FINANCE) POST …/verification {decision:'VERIFY', verifiedAmountMinor:14000, expectedVersion:v} without a reason; then WITH reason 'Cook reported 140.00 against 150.00 due'.
  4. (FX-STAFF-FINANCE) POST …/verification {decision:'DISPUTE', verifiedAmountMinor:14000, reason:'Cook reported EGP 140.00 against EGP 150.00 due', expectedVersion:v}.
  5. (FX-STAFF-OPS) Ops web > 'Order monitoring' > filter 'Cash disputed'.
  6. (FX-COOK-A) Reload Orders > 'Completed'.
- **Expected:**
  - UI: Step 1: card text 'Under review'. Step 5: O1 listed with badge 'Cash disputed'; its total still reads 150.00 and state Completed.
  - API: Step 1 report 201: discrepancy.amountMinor -1000, state REPORTED. Step 3a: 422 VALIDATION_FAILED fieldErrors reason REQUIRED_FOR_EXCEPTIONS; 3b: 422 verifiedAmountMinor MUST_EQUAL_AMOUNT_DUE_OR_DISPUTE. Step 4: 200 state DISPUTED.
  - DB: SELECT amount_minor, discrepancy_minor, state, verified_amount_minor, verified_by, reporter_id FROM cash_report WHERE order_id = :'order'; -> 14000, -1000, DISPUTED (after step 4), verified_amount_minor 14000.
  - DB: SELECT amount_minor, state FROM cash_obligation WHERE order_id = :'order'; -> DISPUTED (already DISPUTED from step 1, before staff action).
  - DB: SELECT state, version, subtotal_minor, delivery_fee_minor, tax_minor, total_minor, commission_bps_snapshot, commission_minor, accept_by, terminal_at FROM customer_order WHERE id = :'order'; -> state COMPLETED, total_minor 15000 unchanged.
  - DB: Variant: an over-report of 16000 gives discrepancy_minor +1000 and the same DISPUTED handling.
- **Also covered by automated tests:** CashAndLedgerIntegrationTest
- **Unverified against code (confirm while executing):** No API/UI moves a DISPUTED cash obligation to a final state; resolution outside the system is assumed (not found in code).

### E2E-FIN-044 — Platform refund of verified cash: only finance.refund_approve, full amount only, requester and approver differ

- **Status:** NOT_RUN · **Priority:** P1 · **Readiness:** Needs the updated application deployment
- **Surface:** api · **Actors:** FX-STAFF-FINANCE, FX-STAFF-OPS, FX-STAFF-ADMIN-B, FX-STAFF-NOGRANT, FX-STAFF-SUPPORT, FX-CUST-A · **Needs:** updated deploy
- **Fixtures to provision:** FX-STAFF-FINANCE, FX-STAFF-OPS, FX-STAFF-ADMIN-B, FX-STAFF-SUPPORT
- **Traces to:** rules: BR-035, BR-006; decisions: D05, D10; operations: postOpsFinanceRefunds, postOpsFinanceRefundsAction; partial: TC-006, TC-035
- **Prerequisites:**
  - Preview stack reachable: customer web https://app.<PREVIEW_DOMAIN>, cook/staff web https://ops.<PREVIEW_DOMAIN>, API https://api.<PREVIEW_DOMAIN>. seed-preview.sql is applied: on the customer web home, kitchen 'مطبخ المعاينة' lists 'Preview koshari' (EGP 75.00) and 'Preview mahshi' (EGP 180.00), and kitchen 'مطبخ تجريبي تاني' lists 'Preview molokhia' (EGP 120.25).
  - A read-only database method approved by the operator is available (psql through the operator channel); replace :'order' with the order UUID.
  - Staff accounts for this case are PROVISIONED fixtures: grants are written by the operator (DB insert into user_grant) or by FX-STAFF-ADMIN in the ops 'Staff permissions' screen; granting through the API requires staff.grant AND recent MFA (StaffController), which needs the PR #9 Keycloak step-up flow.
  - Console on the ops origin. There is no customer refund request in the product: customers use a support case or cancellation request.
  - Each actor uses a SEPARATE browser profile (or private window/cookie store): customer web, ops web and the API share one session cookie (SecurityConfiguration), so signing in as a second actor in the same profile silently replaces the first. Before each actor's steps run `await api('GET','/api/v1/me')` (helper in conventions.api_helper) and confirm the displayName is that actor.
  - FX-STAFF-FINANCE holds finance.refund_approve. A second, different holder is needed (four-eyes): FX-STAFF-ADMIN-B (staff.grant, recent MFA, PR #9) temporarily grants finance.refund_approve to FX-STAFF-OPS (fixtures.json gives OPS order.* and ADMIN-B no finance permission) and revokes it at the end; FX-STAFF-SUPPORT holds only support.case.
- **Steps:**
  1. (FX-STAFF-NOGRANT) POST /api/v1/ops/finance/refunds {orderId:O1, caseId:'CS-TEST-1', fullAmountMinor:15000, reason:'test refund'} (fresh key). Repeat as FX-STAFF-SUPPORT and as FX-CUST-A.
  2. (FX-STAFF-FINANCE) Same request with fullAmountMinor 10000, then 20000, then 15000.
  3. (FX-STAFF-FINANCE) POST /api/v1/ops/finance/refunds/<refundId>/action {action:'APPROVE', expectedVersion:0}.
  4. (FX-STAFF-OPS) Same action APPROVE; then action VERIFY immediately; then PAYMENT_RECORDED with evidenceRef 'TEST-REF-PAY-1' and the new version.
  5. (FX-STAFF-OPS) VERIFY (the same person recorded payment). Then FX-STAFF-FINANCE: VERIFY.
  6. (FX-STAFF-FINANCE) Request a second refund for O1.
- **Expected:**
  - UI: Not applicable (API only; the ops Finance statement offers 'Request full refund' and per-state buttons Approve/Reject/Record payment/Verify).
  - API: 1: 403 FORBIDDEN each. 2: 10000 -> 422 fieldErrors fullAmountMinor FULL_REFUND_REQUIRED; 20000 -> OVER_REFUND; 15000 -> 200 state REQUESTED. 3: 403 FORBIDDEN (separation of duties, requester = approver). 4: APPROVE 200 state APPROVED; VERIFY -> 409 STATE_CONFLICT fieldErrors [{state,'APPROVED'}]; PAYMENT_RECORDED 200 state PAID. 5: same payer VERIFY -> 403; other person VERIFY -> 200 VERIFIED. 6: 409 STATE_CONFLICT fieldErrors [{fullAmountMinor,'OVER_REFUND'}].
  - DB: SELECT state, amount_minor, requested_by, approved_by, paid_by, verified_by, payment_reference FROM refund WHERE order_id = '<O1>'; -> one row VERIFIED amount 15000 with approved_by <> requested_by and verified_by <> paid_by (DB checks refund_approver_ck, refund_verifier_ck).
  - DB: SELECT kind, rate_bps FROM ledger_group WHERE order_id = '<O1>' ORDER BY posted_at; -> COMMISSION then one COMMISSION_REVERSAL (both with 0 entries under D10).
  - DB: audit_event rows refund.request, refund.approve, refund.payment_recorded, refund.verify, all ALLOWED, each with actor and reason.
- **Also covered by automated tests:** FinanceOperationsIntegrationTest
- **Unverified against code (confirm while executing):** The refund path predates D10: it moves no money in the platform (state machine only) and with zero commission the reversal group carries zero amounts.; Whether the owner still wants the platform refund workflow next to the kitchen-owed deposit refund (FIN-107).

### E2E-FIN-045 — Reconciliation invariants hold: balanced ledger, one cash obligation per order, payments match history, deposit never in ledger

- **Status:** NOT_RUN · **Priority:** P0 · **Readiness:** Ready after the extra synthetic fixtures are provisioned
- **Surface:** api · **Actors:** FX-STAFF-FINANCE
- **Fixtures to provision:** FX-STAFF-FINANCE
- **Traces to:** rules: BR-032, BR-033, BR-034; decisions: D05, D10; operations: getOpsFinanceStatements; partial: TC-058, TC-120, TC-157
- **Prerequisites:**
  - Preview stack reachable: customer web https://app.<PREVIEW_DOMAIN>, cook/staff web https://ops.<PREVIEW_DOMAIN>, API https://api.<PREVIEW_DOMAIN>. seed-preview.sql is applied: on the customer web home, kitchen 'مطبخ المعاينة' lists 'Preview koshari' (EGP 75.00) and 'Preview mahshi' (EGP 180.00), and kitchen 'مطبخ تجريبي تاني' lists 'Preview molokhia' (EGP 120.25).
  - A read-only database method approved by the operator is available (psql through the operator channel); replace :'order' with the order UUID.
  - At least one COMPLETED COD order (E2E-ORDER-101/102) and one COMPLETED deposit order (E2E-FIN-039/101) exist. E2E-FIN-107 is NOT required: run it separately for cancelled/disputed-deposit coverage. A read-only SQL channel; infra/db/reconcile.sql from the release commit. There is no ledger export endpoint (getOpsAuditExport is x-implemented: false).
- **Steps:**
  1. (operator) Run infra/db/reconcile.sql read-only against the preview database. — data: psql -f infra/db/reconcile.sql
  2. (operator) Run the extra checks listed in expected.database.
  3. (FX-STAFF-FINANCE) Open ops 'Finance' for kitchen 00000000-0000-4000-8000-0000000bd001 (default period = current Cairo calendar month through today) and compare 'Cash verified' with the SQL sum in expected.database.
- **Expected:**
  - UI: Finance statement totals equal the SQL sums.
  - API: GET /api/v1/ops/finance/statements?kitchenId=00000000-0000-4000-8000-0000000bd001 summary.cashVerified.amountMinor equals SUM(cash_obligation.amount_minor) WHERE state = 'VERIFIED' for the kitchen's orders whose COMPLETED event falls in the statement's Cairo date range (default: first day of the current Cairo month through today).
  - DB: reconcile.sql returns every check_name with violations = 0: meal_slot_counters_match_allocations, cook_slot_counters_match_meal_slots, terminal_orders_hold_no_live_stock, order_items_match_subtotal, order_state_matches_latest_event, one_cash_obligation_per_order, ledger_groups_balanced, ledger_only_for_completed_orders, migrations_all_successful, payment_state_matches_history.
  - DB: SELECT count(*) FROM (SELECT kind, source_event_id FROM ledger_group GROUP BY 1,2 HAVING count(*) > 1) d; -> 0. SELECT count(*) FROM order_payment p JOIN customer_order o ON o.id = p.order_id WHERE p.deposit_minor + p.remainder_minor <> o.total_minor; -> 0.
  - DB: SELECT count(*) FROM ledger_entry WHERE account NOT IN ('COOK_COMMISSION_RECEIVABLE','PLATFORM_COMMISSION_REVENUE'); -> 0 (no entry for deposits or cash). SELECT count(*) FROM ledger_entry e JOIN ledger_group g ON g.id = e.group_id JOIN customer_order o ON o.id = g.order_id WHERE o.commission_bps_snapshot = 0 AND e.amount_minor <> 0; -> 0.
  - DB: SELECT coalesce(sum(c.amount_minor),0) FROM cash_obligation c JOIN customer_order o ON o.id = c.order_id JOIN order_event e ON e.order_id = o.id AND e.to_state = 'COMPLETED' WHERE o.kitchen_id = '00000000-0000-4000-8000-0000000bd001' AND c.state = 'VERIFIED' AND e.occurred_at >= date_trunc('month', now() AT TIME ZONE 'Africa/Cairo') AT TIME ZONE 'Africa/Cairo' AND e.occurred_at < (date_trunc('day', now() AT TIME ZONE 'Africa/Cairo') + interval '1 day') AT TIME ZONE 'Africa/Cairo'; -> equals the statement 'Cash verified' (not SUM(cash_report.verified_amount_minor)).
- **Also covered by automated tests:** CashAndLedgerIntegrationTest; BackupRestoreIntegrationTest (runs reconcile.sql); infra/db/reconcile.sql
- **Unverified against code (confirm while executing):** Run immediately after the finance statement read so no new verification changes the sum between the two reads.

### E2E-FIN-046 — Zero platform charges: no commission, service fee or tax on any quote, order, ledger or statement

- **Status:** NOT_RUN · **Priority:** P0 · **Readiness:** Ready after the extra synthetic fixtures are provisioned
- **Surface:** customer-web, operations-web, api · **Actors:** FX-CUST-A, FX-COOK-A, FX-STAFF-FINANCE
- **Fixtures to provision:** FX-STAFF-FINANCE
- **Traces to:** rules: BR-033, BR-019; decisions: D10, D05; operations: postQuotes, getOpsConfiguration, getCookStatements; partial: TC-117, TC-157
- **Prerequisites:**
  - Preview stack reachable: customer web https://app.<PREVIEW_DOMAIN>, cook/staff web https://ops.<PREVIEW_DOMAIN>, API https://api.<PREVIEW_DOMAIN>. seed-preview.sql is applied: on the customer web home, kitchen 'مطبخ المعاينة' lists 'Preview koshari' (EGP 75.00) and 'Preview mahshi' (EGP 180.00), and kitchen 'مطبخ تجريبي تاني' lists 'Preview molokhia' (EGP 120.25).
  - FX-CUST-A signs in (password from the private fixture sheet) and GET /api/v1/me returns phoneVerified=true.
  - Kitchen A (00000000-0000-4000-8000-0000000bd001) has OPEN 13:00 Africa/Cairo slots for the next 3 days: in the DevTools Console on app.<PREVIEW_DOMAIN> `(await api('GET','/api/v1/kitchens/00000000-0000-4000-8000-0000000bd001/slots')).body.items` has >= 3 items and items[0].availablePortions >= 6 (seed: 20 portions per slot, 10 per meal).
  - In the DevTools Console of the signed-in tab, define the helper `api` from conventions.api_helper (needed for the API steps).
  - A read-only database method approved by the operator is available (psql through the operator channel); replace :'order' with the order UUID.
  - FX-STAFF-FINANCE holds config.read (see fixtures.json; FX-STAFF-OPS does not) - needed to open 'Operating configuration'; alternatively read the policy by SQL.
  - Each actor uses a SEPARATE browser profile (or private window/cookie store): customer web, ops web and the API share one session cookie (SecurityConfiguration), so signing in as a second actor in the same profile silently replaces the first. Before each actor's steps run `await api('GET','/api/v1/me')` (helper in conventions.api_helper) and confirm the displayName is that actor.
- **Steps:**
  1. (FX-CUST-A) Create four quotes for the same kitchen A slot: pickup/COD (2 × koshari), pickup/DEPOSIT, delivery/COD and delivery/DEPOSIT (1 koshari + 1 mahshi, address Test zone A). Record the full JSON of each.
  2. (FX-CUST-A) Place any one and open it. In the customer web look for any charge line other than Meals, Delivery fee, Tax, Total.
  3. (FX-STAFF-FINANCE) Ops web > 'Operating configuration': read 'Current pricing policy'.
  4. (FX-COOK-A) Ops web > 'Statement'.
  5. (operator) Run the SQL.
- **Expected:**
  - UI: Quote panel shows only Meals, Delivery fee, Tax, Total. Policy screen reads 'no platform commission (owner decision D10)' and the synthetic-policy label. Cook statement: 'No platform commission: the whole order amount is yours.' and no commission column.
  - API: Every quote: tax.amountMinor 0; total = subtotal + deliveryFee (pickup deliveryFee 0, zone A 3000); JSON has no service-fee or commission property. Order JSON likewise.
  - DB: SELECT policy_version, commission_bps_snapshot, commission_minor, tax_minor FROM customer_order; -> policy_version >= 1 (the active synthetic policy version, not 0) and commission_bps_snapshot, commission_minor and tax_minor all 0 on every preview order. SELECT version, commission_rate_bps, tax_rate_bps, synthetic FROM commerce_policy WHERE status = 'ACTIVE'; -> commission_rate_bps 0, tax_rate_bps 0. SELECT count(*) FROM ledger_entry WHERE amount_minor <> 0; -> 0.
- **Also covered by automated tests:** ZeroCommissionIntegrationTest; ConfigurationIntegrationTest
- **Unverified against code (confirm while executing):** Operation id of the configuration read (getOpsConfiguration) inferred from the route used by the ops UI.; The delivery fee is a kitchen/zone delivery arrangement fee, not a Matbakhy fee (owner rule); the product has no paid ads or growth billing in this scope.

### E2E-FIN-047 — No real money or notification leaves the preview: only simulated bookkeeping and fake recipients

- **Status:** NOT_RUN · **Priority:** P1 · **Readiness:** Ready to run manually now
- **Surface:** customer-web, operations-web, api · **Actors:** FX-CUST-A, FX-COOK-A, FX-STAFF-FINANCE
- **Traces to:** rules: BR-019; decisions: D16, D10; partial: TC-157, TC-163
- **Prerequisites:**
  - Preview stack reachable: customer web https://app.<PREVIEW_DOMAIN>, cook/staff web https://ops.<PREVIEW_DOMAIN>, API https://api.<PREVIEW_DOMAIN>. seed-preview.sql is applied: on the customer web home, kitchen 'مطبخ المعاينة' lists 'Preview koshari' (EGP 75.00) and 'Preview mahshi' (EGP 180.00), and kitchen 'مطبخ تجريبي تاني' lists 'Preview molokhia' (EGP 120.25).
  - Browser DevTools Network panel with 'Preserve log' on; E2E-FIN-039 is executed while the log is recording.
  - Each actor uses a SEPARATE browser profile (or private window/cookie store): customer web, ops web and the API share one session cookie (SecurityConfiguration), so signing in as a second actor in the same profile silently replaces the first. Before each actor's steps run `await api('GET','/api/v1/me')` (helper in conventions.api_helper) and confirm the displayName is that actor.
- **Steps:**
  1. (FX-CUST-A) With the Network panel open execute the customer part of E2E-FIN-039 (quote, place, 'I sent the deposit').
  2. (FX-COOK-A) Execute the cook part (receipt, cash report) and finance verification.
  3. (FX-CUST-A) In the Network panel list all distinct hostnames. Read the demo notice and the deposit recipient text on the quote.
  4. (operator) Check the API's configured providers (APP_ENV=PREVIEW: SMS, e-mail, push and malware scanner are fake per docs/operations/06-web-preview.md) and outbound notifications.
- **Expected:**
  - UI: Persistent banner 'Demo environment — Test data only. Orders, deposits and payments here are simulated, and no real notification is sent.' on customer web and ops web. Recipient shows 'demo-kitchen@instapay.invalid' (non-routable '.invalid') and 'Demo kitchen (not a real account)'.
  - API: The contract (contracts/openapi.yaml) has no payment-provider, webhook or payout operation; the deposit and cash endpoints only record statements.
  - DB: Only hosts under app./ops./api./id./media.<PREVIEW_DOMAIN> appear in the Network log (no card/wallet/bank/PSP host). SELECT count(*) FROM notification_delivery WHERE channel = 'PUSH'; -> 0 (push flag off). No table stores card data or provider charge ids.
- **Also covered by automated tests:** PushFlagIntegrationTest; NotificationIntegrationTest
- **Unverified against code (confirm while executing):** Which notification_delivery.status the fake e-mail provider records; whether a server-side egress log is available to the tester.

### E2E-FIN-101 — Core journey 4: delivery + 50% deposit with a courier, to verified remaining cash

- **Status:** NOT_RUN · **Priority:** P0 · **Readiness:** Needs the updated application deployment
- **Surface:** customer-web, operations-web, api · **Actors:** FX-CUST-A, FX-COOK-A, FX-STAFF-FINANCE · **Needs:** updated deploy
- **Fixtures to provision:** FX-STAFF-FINANCE
- **Traces to:** rules: BR-019, BR-021, BR-030; decisions: D16, D10; operations: postQuotes, postOrders, postCookOrdersDispatch, postCookOrdersPaymentReceipt, postCookOrdersCashReports; partial: TC-119, TC-158, TC-159, TC-163
- **Prerequisites:**
  - Preview stack reachable: customer web https://app.<PREVIEW_DOMAIN>, cook/staff web https://ops.<PREVIEW_DOMAIN>, API https://api.<PREVIEW_DOMAIN>. seed-preview.sql is applied: on the customer web home, kitchen 'مطبخ المعاينة' lists 'Preview koshari' (EGP 75.00) and 'Preview mahshi' (EGP 180.00), and kitchen 'مطبخ تجريبي تاني' lists 'Preview molokhia' (EGP 120.25).
  - FX-CUST-A signs in (password from the private fixture sheet) and GET /api/v1/me returns phoneVerified=true.
  - FX-COOK-A signs in at https://ops.<PREVIEW_DOMAIN> and the tab bar shows 'Orders' (ar: الطلبات); GET /api/v1/cook/dashboard in that session returns 200.
  - Kitchen A (00000000-0000-4000-8000-0000000bd001) has OPEN 13:00 Africa/Cairo slots for the next 3 days: in the DevTools Console on app.<PREVIEW_DOMAIN> `(await api('GET','/api/v1/kitchens/00000000-0000-4000-8000-0000000bd001/slots')).body.items` has >= 3 items and items[0].availablePortions >= 6 (seed: 20 portions per slot, 10 per meal).
  - Kitchen A payment profile is the seeded one: deposit 50% (deposit_bps 5000), no COD cap, recipient INSTAPAY 'demo-kitchen@instapay.invalid' / 'Demo kitchen (not a real account)' with recipient_status APPROVED (verify: GET /api/v1/cook/payment-profile as FX-COOK-A).
  - A read-only database method approved by the operator is available (psql through the operator channel); replace :'order' with the order UUID.
  - In the DevTools Console of the signed-in tab, define the helper `api` from conventions.api_helper (needed for the API steps).
  - FX-STAFF-FINANCE (PROVISION) holds finance.read, finance.verify, finance.refund_approve and finance.settlement.
  - FX-CUST-A has an address in 'Test zone A' (see E2E-ORDER-102 step 2). PR #9 behaviour for the cash step as in E2E-FIN-039.
  - Slot assignment: use the seeded slot of day 1 (items[0]) (GET /api/v1/kitchens/00000000-0000-4000-8000-0000000bd001/slots, same index each run). Preparation consumes capacity permanently and each meal-slot has only 10 portions: before the case read the AGGREGATE slot availablePortions (max 20) from the kitchen slots response (SlotAvailability exposes aggregate availability only) AND the meal-specific availability from the meal's nextSlots[].availablePortions (GET /api/v1/meals/<mealId>; the owning cook's slot response also lists per-meal capacity) and switch to another seeded day if fewer portions than the case orders remain. E2E-ORDER-114/115 own the day-3 slot; do not use it here.
  - Each actor uses a SEPARATE browser profile (or private window/cookie store): customer web, ops web and the API share one session cookie (SecurityConfiguration), so signing in as a second actor in the same profile silently replaces the first. Before each actor's steps run `await api('GET','/api/v1/me')` (helper in conventions.api_helper) and confirm the displayName is that actor.
- **Steps:**
  1. (FX-CUST-A) Add 1 × 'Preview koshari' and 1 × 'Preview mahshi' (same 13:00 slot), 'Cart', mode 'Delivery', select the Test-zone-A address, radio 'Deposit sent straight to the kitchen', 'Calculate total', terms, 'Place order'.
  2. (FX-CUST-A) On the order page press 'I sent the deposit' without a reference.
  3. (FX-COOK-A) 'Accept', then 'The deposit arrived (EGP 142.50)', 'Start preparing', 'Ready', then type 'COURIER-TEST-02' in the delivery reference field and press 'Hand to courier'.
  4. (FX-CUST-A) Reload: read the code. State must read 'On the way'.
  5. (FX-COOK-A) Enter the code, 'Confirm handover'. Then in 'Completed' submit 'Report cash collected' with the pre-filled Amount (EGP) 142.50. — data: 14250
  6. (FX-STAFF-FINANCE) Verify the report via the API with verifiedAmountMinor 14250.
- **Expected:**
  - UI: Quote: Meals 255.00, Delivery fee 30.00, Tax 0.00, Total 285.00; 'Deposit now: 142.50 · Rest on receipt: 142.50'. Cook card shows the delivery address line and the Deposit panel. Final customer state 'Delivered'.
  - API: Quote total.amountMinor 28500 (subtotal 25500 + deliveryFee 3000), payment.deposit.amountMinor 14250, remainder 14250. Cash report amount 14250 discrepancy 0 -> VERIFIED.
  - DB: SELECT method, state, total_minor, deposit_minor, remainder_minor, deposit_expires_at, version FROM order_payment WHERE order_id = :'order'; -> total 28500, deposit 14250, remainder 14250.
  - DB: SELECT amount_minor, state FROM cash_obligation WHERE order_id = :'order'; -> 14250 VERIFIED after step 6.
  - DB: SELECT from_state, to_state, actor_type, amount_minor, reference, reason_code FROM payment_event WHERE order_id = :'order' ORDER BY occurred_at, id; -> amounts in transition order: 14250 (WAITING_FOR_DEPOSIT), NULL (customer report, reference NULL), 14250 (kitchen receipt).
  - DB: customer_order.dispatch_reference = 'COURIER-TEST-02', delivery_fee_minor 3000; the delivery fee is part of the total the kitchen collects, no separate Matbakhy fee exists.
- **Also covered by automated tests:** DepositPaymentIntegrationTest; CashAndLedgerIntegrationTest
- **Unverified against code (confirm while executing):** The delivery fee (EGP 30.00 by zone) is shown as 'Delivery fee'; the product states it is an external delivery fee, not platform monetisation. No code path adds a platform share to it.

### E2E-FIN-102 — Duplicate or invalid cash reports are refused; one report per order

- **Status:** NOT_RUN · **Priority:** P1 · **Readiness:** Ready to run manually now
- **Surface:** api · **Actors:** FX-COOK-A, FX-CUST-A
- **Traces to:** rules: BR-032, BR-024; decisions: D05; operations: postCookOrdersCashReports; partial: TC-032, TC-058, TC-120
- **Prerequisites:**
  - Preview stack reachable: customer web https://app.<PREVIEW_DOMAIN>, cook/staff web https://ops.<PREVIEW_DOMAIN>, API https://api.<PREVIEW_DOMAIN>. seed-preview.sql is applied: on the customer web home, kitchen 'مطبخ المعاينة' lists 'Preview koshari' (EGP 75.00) and 'Preview mahshi' (EGP 180.00), and kitchen 'مطبخ تجريبي تاني' lists 'Preview molokhia' (EGP 120.25).
  - A read-only database method approved by the operator is available (psql through the operator channel); replace :'order' with the order UUID.
  - In the DevTools Console of the signed-in tab, define the helper `api` from conventions.api_helper (needed for the API steps).
  - Two COMPLETED COD orders C1 and C2 (cash DUE, total 15000) at kitchen A, plus one PREPARING order P1; cook session on ops origin. Record customer_order.version of each (v).
  - Each actor uses a SEPARATE browser profile (or private window/cookie store): customer web, ops web and the API share one session cookie (SecurityConfiguration), so signing in as a second actor in the same profile silently replaces the first. Before each actor's steps run `await api('GET','/api/v1/me')` (helper in conventions.api_helper) and confirm the displayName is that actor.
- **Steps:**
  1. (FX-COOK-A) On C1 build `const body={amountMinor:15000,currency:'EGP',collectedAt:new Date().toISOString(),method:'CASH',externalReference:'TEST-CASH-002',expectedVersion:v}` and POST /api/v1/cook/orders/<C1>/cash-reports with key K.
  2. (FX-COOK-A) Repeat the same POST with the same key K and body; then with K and amountMinor 14000.
  3. (FX-COOK-A) POST again with a NEW key K2.
  4. (FX-COOK-A) On C2 send: currency 'USD'; then collectedAt one day in the future; then a stale expectedVersion (v-1).
  5. (FX-COOK-A) On P1 send a valid report.
  6. (FX-CUST-A) Send a report on C2 as the customer.
- **Expected:**
  - UI: Not applicable.
  - API: 1: 201 state REPORTED. 2: same key and body -> 201 with the same report id; changed body -> 409 IDEMPOTENCY_CONFLICT. 3: 409 STATE_CONFLICT fieldErrors [{cashState,'REPORTED'}]. 4: 422 VALIDATION_FAILED fieldErrors method CASH_EGP_ONLY; 422 collectedAt BETWEEN_ORDER_AND_NOW; 409 VERSION_CONFLICT. 5: 409 STATE_CONFLICT fieldErrors [{state,'PREPARING'}]. 6: 403 FORBIDDEN.
  - DB: SELECT count(*) FROM cash_report WHERE order_id = '<C1>'; -> 1 (UNIQUE order_id). C2 has no cash_report row and cash_obligation still DUE.
- **Also covered by automated tests:** CashAndLedgerIntegrationTest

### E2E-FIN-103 — A changed kitchen payment account is hidden from customers until staff approve it, and stale quotes are re-priced

- **Status:** NOT_RUN · **Priority:** P1 · **Readiness:** Needs the updated application deployment
- **Surface:** operations-web, customer-web, api · **Actors:** FX-COOK-A, FX-CUST-A, FX-STAFF-FINANCE · **Needs:** updated deploy · **Destructive**
- **Fixtures to provision:** FX-STAFF-FINANCE
- **Traces to:** rules: BR-019, BR-020; decisions: D16, D13, D18; operations: putCookPaymentProfile, getOpsPaymentRecipients, postOpsKitchensPaymentRecipientApproval, postQuotes, postOrders; partial: TC-042, TC-158, TC-163
- **Prerequisites:**
  - Preview stack reachable: customer web https://app.<PREVIEW_DOMAIN>, cook/staff web https://ops.<PREVIEW_DOMAIN>, API https://api.<PREVIEW_DOMAIN>. seed-preview.sql is applied: on the customer web home, kitchen 'مطبخ المعاينة' lists 'Preview koshari' (EGP 75.00) and 'Preview mahshi' (EGP 180.00), and kitchen 'مطبخ تجريبي تاني' lists 'Preview molokhia' (EGP 120.25).
  - FX-CUST-A signs in (password from the private fixture sheet) and GET /api/v1/me returns phoneVerified=true.
  - FX-COOK-A signs in at https://ops.<PREVIEW_DOMAIN> and the tab bar shows 'Orders' (ar: الطلبات); GET /api/v1/cook/dashboard in that session returns 200.
  - Kitchen A (00000000-0000-4000-8000-0000000bd001) has OPEN 13:00 Africa/Cairo slots for the next 3 days: in the DevTools Console on app.<PREVIEW_DOMAIN> `(await api('GET','/api/v1/kitchens/00000000-0000-4000-8000-0000000bd001/slots')).body.items` has >= 3 items and items[0].availablePortions >= 6 (seed: 20 portions per slot, 10 per meal).
  - Kitchen A payment profile is the seeded one: deposit 50% (deposit_bps 5000), no COD cap, recipient INSTAPAY 'demo-kitchen@instapay.invalid' / 'Demo kitchen (not a real account)' with recipient_status APPROVED (verify: GET /api/v1/cook/payment-profile as FX-COOK-A).
  - FX-STAFF-FINANCE (PROVISION) holds finance.read, finance.verify, finance.refund_approve and finance.settlement; the ops web shows its 'Finance' tab (the tab appears only for accounts that may read statements, so finance.verify alone is not enough). Payment recipient approval and payment decisions also need a recent Keycloak MFA step-up (PR #9 realm flow); cash verification, refunds and settlements do not.
  - Staff accounts for this case are PROVISIONED fixtures: grants are written by the operator (DB insert into user_grant) or by FX-STAFF-ADMIN in the ops 'Staff permissions' screen; granting through the API requires staff.grant AND recent MFA (StaffController), which needs the PR #9 Keycloak step-up flow.
  - SHARED DATA WARNING: this changes kitchen A's recipient in the shared preview; steps 7-8 restore it. Run on a PR #9 deployment (MFA step-up).
  - Each actor uses a SEPARATE browser profile (or private window/cookie store): customer web, ops web and the API share one session cookie (SecurityConfiguration), so signing in as a second actor in the same profile silently replaces the first. Before each actor's steps run `await api('GET','/api/v1/me')` (helper in conventions.api_helper) and confirm the displayName is that actor.
- **Steps:**
  1. (FX-CUST-A) Customer web: cart 2 × koshari, Pickup, radio 'Deposit sent straight to the kitchen', 'Calculate total' (quote Q1). Do not place yet.
  2. (FX-COOK-A) Ops web > 'Settings' > section 'Payment': change 'Account address or number' to 'demo-kitchen-2@instapay.invalid', press 'Save'. Read the status line.
  3. (FX-CUST-A) Place Q1 (tick terms, 'Place order') within 5 minutes.
  4. (FX-CUST-A) Calculate the total again with the deposit radio; then switch to 'Cash on receipt' and calculate.
  5. (FX-STAFF-FINANCE) Ops web > 'Finance' > 'Accounts waiting for approval': row 'مطبخ المعاينة · InstaPay: demo-kitchen-2@instapay.invalid …'. Reason 'Checked against approved application (test)'; press 'Approve account'. (Needs recent MFA.)
  6. (FX-CUST-A) Calculate the total with the deposit radio again.
  7. (FX-COOK-A) Restore 'Account address or number' to 'demo-kitchen@instapay.invalid', Save.
  8. (FX-STAFF-FINANCE) Approve the restored account the same way.
- **Expected:**
  - UI: Step 2: 'Waiting for staff approval; deposits are not offered to customers until then.' Step 3: the placement is refused (nothing is ordered); the page tries a new quote, which fails because deposits are no longer offered, so it shows "We couldn't calculate the new total. Calculate the total again." and no order exists. Step 4: 'This kitchen does not take deposits at the moment. Choose cash on receipt.' for deposit, cash quote works. Step 5: the approved row disappears from the waiting list. Step 6: deposit quote works and shows 'demo-kitchen-2@instapay.invalid'. Step 8 leaves 'Staff approved this account.'
  - API: Step 3: 409 PRICE_CHANGED fieldErrors [{quoteId,'PAYMENT_CHANGED'}]. Step 4 deposit: 422 VALIDATION_FAILED fieldErrors [{paymentMethod,'DEPOSIT_NOT_OFFERED'}]. Step 5 without MFA: 403 STEP_UP_REQUIRED; reason shorter than 10 characters: 422 VALIDATION_FAILED on field reason. After approval 200 ProfileView recipientStatus APPROVED.
  - DB: SELECT recipient_status, recipient_identifier, approved_by, approved_at FROM kitchen_payment_profile WHERE kitchen_id = '00000000-0000-4000-8000-0000000bd001'; -> NONE after step 2, APPROVED with approved_by = FX-STAFF-FINANCE after step 5.
  - DB: audit_event rows payment.profile_update (details recipientChanged true) and payment.recipient_approve (reason text) exist; no order has the unapproved identifier in order_payment.recipient_identifier.
- **Also covered by automated tests:** DepositPaymentIntegrationTest; PriceChangeIntegrationTest
- **Unverified against code (confirm while executing):** Step 3 outcome in the UI: Checkout.place() maps PRICE_CHANGED to a re-quote; the UI also falls back to the generic text if the re-quote fails.; A staff member who also owns the kitchen cannot approve it (403) - needs a multi-role fixture and is not exercised here.

### E2E-FIN-104 — Cash-on-receipt eligibility: a kitchen's COD cap refuses higher totals exactly above the cap

- **Status:** NOT_RUN · **Priority:** P1 · **Readiness:** Ready to run manually now
- **Surface:** operations-web, customer-web, api · **Actors:** FX-COOK-A, FX-CUST-A · **Destructive**
- **Traces to:** rules: BR-019; decisions: D16, D05; operations: putCookPaymentProfile, postQuotes; partial: TC-158, TC-163
- **Prerequisites:**
  - Preview stack reachable: customer web https://app.<PREVIEW_DOMAIN>, cook/staff web https://ops.<PREVIEW_DOMAIN>, API https://api.<PREVIEW_DOMAIN>. seed-preview.sql is applied: on the customer web home, kitchen 'مطبخ المعاينة' lists 'Preview koshari' (EGP 75.00) and 'Preview mahshi' (EGP 180.00), and kitchen 'مطبخ تجريبي تاني' lists 'Preview molokhia' (EGP 120.25).
  - FX-CUST-A signs in (password from the private fixture sheet) and GET /api/v1/me returns phoneVerified=true.
  - FX-COOK-A signs in at https://ops.<PREVIEW_DOMAIN> and the tab bar shows 'Orders' (ar: الطلبات); GET /api/v1/cook/dashboard in that session returns 200.
  - Kitchen A (00000000-0000-4000-8000-0000000bd001) has OPEN 13:00 Africa/Cairo slots for the next 3 days: in the DevTools Console on app.<PREVIEW_DOMAIN> `(await api('GET','/api/v1/kitchens/00000000-0000-4000-8000-0000000bd001/slots')).body.items` has >= 3 items and items[0].availablePortions >= 6 (seed: 20 portions per slot, 10 per meal).
  - Kitchen A payment profile is the seeded one: deposit 50% (deposit_bps 5000), no COD cap, recipient INSTAPAY 'demo-kitchen@instapay.invalid' / 'Demo kitchen (not a real account)' with recipient_status APPROVED (verify: GET /api/v1/cook/payment-profile as FX-COOK-A).
  - In the DevTools Console of the signed-in tab, define the helper `api` from conventions.api_helper (needed for the API steps).
  - SHARED DATA WARNING: changes the COD cap of kitchen A; step 6 restores 'no cap'. The recipient is not edited, so its APPROVED status is kept (PaymentService.updateProfile keeps the status for an identical recipient).
  - Each actor uses a SEPARATE browser profile (or private window/cookie store): customer web, ops web and the API share one session cookie (SecurityConfiguration), so signing in as a second actor in the same profile silently replaces the first. Before each actor's steps run `await api('GET','/api/v1/me')` (helper in conventions.api_helper) and confirm the displayName is that actor.
- **Steps:**
  1. (FX-COOK-A) Settings > 'Payment': set 'Highest total for cash on delivery (EGP; leave empty for any total)' to 255.00, keep the recipient fields, 'Save'. — data: 25500
  2. (FX-CUST-A) Cart: 1 × koshari + 1 × mahshi (total 255.00), Pickup, 'Cash on receipt', 'Calculate total'. — data: total 25500 = cap
  3. (FX-COOK-A) Set the cap to 254.99 and Save. — data: 25499
  4. (FX-CUST-A) Calculate the same basket again with 'Cash on receipt'; then with 'Deposit sent straight to the kitchen'.
  5. (FX-CUST-A) Console: POST /api/v1/quotes with paymentMethod 'COD' for the same basket.
  6. (FX-COOK-A) Clear the cap field and Save.
- **Expected:**
  - UI: Step 2: quote accepted (total exactly at the cap is eligible). Step 4: message 'Cash on receipt is not available for this total at this kitchen. Choose the deposit.' for cash; the deposit quote works with 'Deposit now: 127.50 · Rest on receipt: 127.50'.
  - API: Step 5: 422 VALIDATION_FAILED fieldErrors [{paymentMethod,'COD_NOT_ELIGIBLE'}].
  - DB: SELECT cod_max_total_minor, recipient_status, version FROM kitchen_payment_profile WHERE kitchen_id = '00000000-0000-4000-8000-0000000bd001'; -> 25500, then 25499, then NULL; recipient_status APPROVED throughout.
  - DB: No customer_order was created by this case.
- **Also covered by automated tests:** DepositPaymentIntegrationTest
- **Unverified against code (confirm while executing):** A quote created before the cap changed is refused at placement with 409 PRICE_CHANGED/PAYMENT_CHANGED; add that observation if the case is extended.

### E2E-FIN-105 — A kitchen without an approved deposit account does not offer deposits; cash on receipt still works

- **Status:** NOT_RUN · **Priority:** P1 · **Readiness:** Ready to run manually now
- **Surface:** customer-web, api · **Actors:** FX-CUST-A
- **Traces to:** rules: BR-019; decisions: D16; operations: postQuotes; partial: TC-158, TC-163
- **Prerequisites:**
  - Preview stack reachable: customer web https://app.<PREVIEW_DOMAIN>, cook/staff web https://ops.<PREVIEW_DOMAIN>, API https://api.<PREVIEW_DOMAIN>. seed-preview.sql is applied: on the customer web home, kitchen 'مطبخ المعاينة' lists 'Preview koshari' (EGP 75.00) and 'Preview mahshi' (EGP 180.00), and kitchen 'مطبخ تجريبي تاني' lists 'Preview molokhia' (EGP 120.25).
  - FX-CUST-A signs in (password from the private fixture sheet) and GET /api/v1/me returns phoneVerified=true.
  - In the DevTools Console of the signed-in tab, define the helper `api` from conventions.api_helper (needed for the API steps).
  - Kitchen B (00000000-0000-4000-8000-0000000bd002) has no kitchen_payment_profile row in the seed; its open slot from GET /api/v1/kitchens/00000000-0000-4000-8000-0000000bd002/slots.
- **Steps:**
  1. (FX-CUST-A) Customer web: open kitchen 'مطبخ تجريبي تاني' > meal 'Preview molokhia' (EGP 120.25) > slot > 'Add to cart'. In 'Cart' choose Pickup, radio 'Deposit sent straight to the kitchen', press 'Calculate total'.
  2. (FX-CUST-A) Switch to 'Cash on receipt' and calculate; read the total.
- **Expected:**
  - UI: Step 1: 'This kitchen does not take deposits at the moment. Choose cash on receipt.' and no quote. Step 2: total 120.25 (odd piastres kept: 12025), text 'Pay in cash on receipt.'
  - API: Step 1 API: 422 VALIDATION_FAILED fieldErrors [{paymentMethod,'DEPOSIT_NOT_OFFERED'}]. Step 2: 201, total.amountMinor 12025, payment.method COD.
  - DB: No quote row with payment_method DEPOSIT for kitchen B: SELECT count(*) FROM quote WHERE kitchen_id = '00000000-0000-4000-8000-0000000bd002' AND payment_method = 'DEPOSIT'; -> 0.
- **Also covered by automated tests:** DepositPaymentIntegrationTest

### E2E-FIN-106 — A deposit order cannot move to PREPARING until the kitchen reports receipt (acceptance stays separate)

- **Status:** NOT_RUN · **Priority:** P0 · **Readiness:** Ready to run manually now
- **Surface:** operations-web, api · **Actors:** FX-CUST-A, FX-COOK-A
- **Traces to:** rules: BR-026; decisions: D16; operations: postCookOrdersPrepare, postCookOrdersPaymentReceipt, postOrdersPaymentTransferReport; partial: TC-119, TC-159
- **Prerequisites:**
  - Preview stack reachable: customer web https://app.<PREVIEW_DOMAIN>, cook/staff web https://ops.<PREVIEW_DOMAIN>, API https://api.<PREVIEW_DOMAIN>. seed-preview.sql is applied: on the customer web home, kitchen 'مطبخ المعاينة' lists 'Preview koshari' (EGP 75.00) and 'Preview mahshi' (EGP 180.00), and kitchen 'مطبخ تجريبي تاني' lists 'Preview molokhia' (EGP 120.25).
  - FX-COOK-A signs in at https://ops.<PREVIEW_DOMAIN> and the tab bar shows 'Orders' (ar: الطلبات); GET /api/v1/cook/dashboard in that session returns 200.
  - A read-only database method approved by the operator is available (psql through the operator channel); replace :'order' with the order UUID.
  - In the DevTools Console of the signed-in tab, define the helper `api` from conventions.api_helper (needed for the API steps).
  - Kitchen A payment profile is the seeded one: deposit 50% (deposit_bps 5000), no COD cap, recipient INSTAPAY 'demo-kitchen@instapay.invalid' / 'Demo kitchen (not a real account)' with recipient_status APPROVED (verify: GET /api/v1/cook/payment-profile as FX-COOK-A).
  - Slot assignment: use the seeded slot of day 2 (items[1]) (GET /api/v1/kitchens/00000000-0000-4000-8000-0000000bd001/slots, same index each run). Preparation consumes capacity permanently and each meal-slot has only 10 portions: before the case read the AGGREGATE slot availablePortions (max 20) from the kitchen slots response (SlotAvailability exposes aggregate availability only) AND the meal-specific availability from the meal's nextSlots[].availablePortions (GET /api/v1/meals/<mealId>; the owning cook's slot response also lists per-meal capacity) and switch to another seeded day if fewer portions than the case orders remain. E2E-ORDER-114/115 own the day-3 slot; do not use it here.
  - Each actor uses a SEPARATE browser profile (or private window/cookie store): customer web, ops web and the API share one session cookie (SecurityConfiguration), so signing in as a second actor in the same profile silently replaces the first. Before each actor's steps run `await api('GET','/api/v1/me')` (helper in conventions.api_helper) and confirm the displayName is that actor.
  - A FRESH pending deposit order D1 (pickup, 2 × koshari, deposit 7500) in payment state WAITING_FOR_DEPOSIT, created by executing ONLY E2E-FIN-039 steps 1-2 on the day-2 slot (do NOT do step 3, which reports the transfer and moves the payment to CUSTOMER_TRANSFER_REPORTED). Confirm payment.state = WAITING_FOR_DEPOSIT (GET /api/v1/orders/<D1>) before step 1. Never reuse the order that E2E-FIN-039 completes.
- **Steps:**
  1. (FX-COOK-A) Press 'Accept' on D1 (no deposit reported yet).
  2. (FX-COOK-A) Press 'Start preparing'.
  3. (FX-CUST-A) On the order page press 'I sent the deposit'.
  4. (FX-COOK-A) Press 'Start preparing' again.
  5. (FX-COOK-A) Press 'The deposit arrived (EGP 75.00)', then 'Start preparing'.
- **Expected:**
  - UI: Step 1: the order becomes 'Accepted' (acceptance is allowed without a deposit). Steps 2 and 4: the card stays 'Accepted' and an error line appears (ops web error text for STATE_CONFLICT). Step 5: badge 'Preparing'.
  - API: Steps 2 and 4: 409 STATE_CONFLICT with fieldErrors [{payment,'DEPOSIT_NOT_RECEIVED'}]. Step 5: 200 PREPARING.
  - DB: SELECT state, version, subtotal_minor, delivery_fee_minor, tax_minor, total_minor, commission_bps_snapshot, commission_minor, accept_by, terminal_at FROM customer_order WHERE id = :'order'; -> state ACCEPTED between steps 1-4; SELECT state, quantity, released_at FROM stock_allocation WHERE order_id = :'order'; -> ALLOCATED (not CONSUMED) until step 5; no handover_challenge row.
  - DB: SELECT from_state, to_state, actor_type, amount_minor, reference, reason_code FROM payment_event WHERE order_id = :'order' ORDER BY occurred_at, id; -> the customer report (CUSTOMER_TRANSFER_REPORTED) does not change the gate; only KITCHEN_RECEIPT_REPORTED opens it.
- **Also covered by automated tests:** DepositPaymentIntegrationTest
- **Unverified against code (confirm while executing):** Exact message the cook UI shows for this 409 (not read from the ops-web errors dictionary).

### E2E-FIN-107 — Order ends after a reported transfer: reconciliation, staff decision 'kitchen owes a refund', kitchen reports the refund it sent

- **Status:** NOT_RUN · **Priority:** P0 · **Readiness:** Needs the updated application deployment
- **Surface:** operations-web, customer-web, api · **Actors:** FX-CUST-A, FX-COOK-A, FX-STAFF-FINANCE · **Needs:** updated deploy
- **Fixtures to provision:** FX-STAFF-FINANCE
- **Traces to:** scenarios: TC-162; rules: BR-027, BR-029, BR-035; decisions: D16, D18; operations: postCookOrdersCancel, getOpsPayments, postOpsOrdersPaymentDecision, postCookOrdersPaymentRefund; partial: TC-160, TC-163
- **Prerequisites:**
  - Preview stack reachable: customer web https://app.<PREVIEW_DOMAIN>, cook/staff web https://ops.<PREVIEW_DOMAIN>, API https://api.<PREVIEW_DOMAIN>. seed-preview.sql is applied: on the customer web home, kitchen 'مطبخ المعاينة' lists 'Preview koshari' (EGP 75.00) and 'Preview mahshi' (EGP 180.00), and kitchen 'مطبخ تجريبي تاني' lists 'Preview molokhia' (EGP 120.25).
  - FX-COOK-A signs in at https://ops.<PREVIEW_DOMAIN> and the tab bar shows 'Orders' (ar: الطلبات); GET /api/v1/cook/dashboard in that session returns 200.
  - A read-only database method approved by the operator is available (psql through the operator channel); replace :'order' with the order UUID.
  - In the DevTools Console of the signed-in tab, define the helper `api` from conventions.api_helper (needed for the API steps).
  - Kitchen A payment profile is the seeded one: deposit 50% (deposit_bps 5000), no COD cap, recipient INSTAPAY 'demo-kitchen@instapay.invalid' / 'Demo kitchen (not a real account)' with recipient_status APPROVED (verify: GET /api/v1/cook/payment-profile as FX-COOK-A).
  - FX-STAFF-FINANCE (PROVISION) holds finance.read, finance.verify, finance.refund_approve and finance.settlement; the ops web shows its 'Finance' tab (the tab appears only for accounts that may read statements, so finance.verify alone is not enough). Payment recipient approval and payment decisions also need a recent Keycloak MFA step-up (PR #9 realm flow); cash verification, refunds and settlements do not.
  - Staff accounts for this case are PROVISIONED fixtures: grants are written by the operator (DB insert into user_grant) or by FX-STAFF-ADMIN in the ops 'Staff permissions' screen; granting through the API requires staff.grant AND recent MFA (StaffController), which needs the PR #9 Keycloak step-up flow.
  - Run on a PR #9 deployment with the Keycloak step-up flow applied (steps 5-6 need a recent MFA proof). A pending deposit order D1 with the customer's transfer reported.
  - Each actor uses a SEPARATE browser profile (or private window/cookie store): customer web, ops web and the API share one session cookie (SecurityConfiguration), so signing in as a second actor in the same profile silently replaces the first. Before each actor's steps run `await api('GET','/api/v1/me')` (helper in conventions.api_helper) and confirm the displayName is that actor.
- **Steps:**
  1. (FX-COOK-A) 'Accept' D1, press 'The deposit arrived (EGP 75.00)', then choose Reason 'Ingredient unavailable' and press 'Cancel'.
  2. (FX-CUST-A) Reload D1; 'My account' > 'Notifications'; open the case link.
  3. (FX-STAFF-FINANCE) Ops web > 'Finance' > 'Payments needing attention' > filter 'Needs reconciliation': D1 is listed with the deposit and the order state 'Cancelled'.
  4. (FX-STAFF-FINANCE) Before deciding, console: POST /api/v1/ops/orders/<D1>/payment/decision {expectedVersion:v, outcome:'RECEIVED', reason:'no such outcome exists'}; then outcome 'REFUND_OWED' with reason 'short'.
  5. (FX-STAFF-FINANCE) In the form pick Decision 'Kitchen owes a refund', Reason 'Kitchen cancelled after receiving deposit (test)', press 'Record decision'. If it answers 'This action needs additional verification (MFA).', use 'Verify again' and repeat.
  6. (FX-COOK-A) Orders (reload): the Deposit panel on D1 shows 'Kitchen owes a refund' with the field 'Refund transfer reference'. Type 'REFUND-TEST-1' and press 'Refund sent'.
  7. (FX-CUST-A) Reload D1 and the notifications.
- **Expected:**
  - UI: Step 2: customer order 'Cancelled'; Payment panel 'Needs reconciliation' with 'The order ended after a reported transfer; support will follow up the refund or confirmation.'; a case 'Deposit review' (High). Step 5-6: customer Payment panel 'The kitchen owes you a refund' + 'The kitchen is responsible for returning your deposit. Contact support if it is late.'; inbox 'Deposit: the kitchen owes you a refund'. Step 7 (after the cook's report): 'The kitchen reported sending the refund' and inbox 'Deposit: the kitchen reported sending the refund'. The ops queue shows no decision that marks money received (only the two outcomes).
  - API: Step 4: invalid outcome 'RECEIVED' -> 400 MALFORMED_REQUEST (no outcome marks money as received); short reason -> 422 VALIDATION_FAILED on field reason (bean-validation size rule, 10..500). Without a recent MFA proof the call returns 403 STEP_UP_REQUIRED (on origin/develop the realm has no OTP flow, so MFA-gated steps cannot complete there). Step 5: 200 PaymentView state REFUND_OWED. Step 6: 200 state REFUNDED.
  - DB: SELECT from_state, to_state, actor_type, amount_minor, reference, reason_code FROM payment_event WHERE order_id = :'order' ORDER BY occurred_at, id; -> …→RECONCILE_PENDING (actor COOK, reason_code INGREDIENT_UNAVAILABLE, the ending reason of the order), →REFUND_OWED (STAFF, reason_code REFUND_OWED), →REFUNDED (COOK, amount 7500, reference REFUND-TEST-1).
  - DB: SELECT category, severity, state FROM support_case WHERE order_id = :'order' ORDER BY created_at; -> exactly one PAYMENT case created at RECONCILE_PENDING (not duplicated by REFUND_OWED).
  - DB: SELECT action, outcome, reason, details FROM audit_event WHERE action = 'payment.decision' AND object_id = '<D1>'; -> ALLOWED with details {outcome:'REFUND_OWED', from:'RECONCILE_PENDING'} and the reason text.
  - DB: SELECT count(*) FROM refund WHERE order_id = '<D1>'; -> 0 (this deposit path does not use the legacy platform refund table). SELECT n.type, n.recipient_id, n.params->>'state' AS state, n.read_at FROM notification n WHERE n.order_id = :'order' ORDER BY n.created_at;
- **Also covered by automated tests:** PaymentReconciliationIntegrationTest; DepositPaymentIntegrationTest
- **Unverified against code (confirm while executing):** Exact reason_code stored on the RECONCILE_PENDING event (the ending reason code of the order is passed through orderClosed).; Notification for RECONCILE_PENDING does not exist by design (only DISPUTED, REFUND_OWED, REFUNDED, KITCHEN_RECEIPT_REPORTED, CUSTOMER_TRANSFER_REPORTED are notified).

### E2E-FIN-108 — A disputed deposit on an active order can still be settled when the kitchen finds the money

- **Status:** NOT_RUN · **Priority:** P2 · **Readiness:** Ready to run manually now
- **Surface:** operations-web, api · **Actors:** FX-COOK-A, FX-STAFF-FINANCE
- **Traces to:** rules: BR-026; decisions: D16; operations: postCookOrdersPaymentReceipt; partial: TC-159, TC-161
- **Prerequisites:**
  - Preview stack reachable: customer web https://app.<PREVIEW_DOMAIN>, cook/staff web https://ops.<PREVIEW_DOMAIN>, API https://api.<PREVIEW_DOMAIN>. seed-preview.sql is applied: on the customer web home, kitchen 'مطبخ المعاينة' lists 'Preview koshari' (EGP 75.00) and 'Preview mahshi' (EGP 180.00), and kitchen 'مطبخ تجريبي تاني' lists 'Preview molokhia' (EGP 120.25).
  - FX-COOK-A signs in at https://ops.<PREVIEW_DOMAIN> and the tab bar shows 'Orders' (ar: الطلبات); GET /api/v1/cook/dashboard in that session returns 200.
  - A read-only database method approved by the operator is available (psql through the operator channel); replace :'order' with the order UUID.
  - D1 from E2E-FIN-040 (ACCEPTED, payment DISPUTED, one open PAYMENT case).
  - Each actor uses a SEPARATE browser profile (or private window/cookie store): customer web, ops web and the API share one session cookie (SecurityConfiguration), so signing in as a second actor in the same profile silently replaces the first. Before each actor's steps run `await api('GET','/api/v1/me')` (helper in conventions.api_helper) and confirm the displayName is that actor.
- **Steps:**
  1. (FX-COOK-A) In the Deposit panel of D1 press 'The deposit arrived (EGP 75.00)'.
  2. (FX-COOK-A) Press 'Start preparing'.
  3. (operator) Check the support case.
- **Expected:**
  - UI: Panel badge 'Kitchen reported receipt'; 'Start preparing' now works.
  - API: Receipt 200 state KITCHEN_RECEIPT_REPORTED.
  - DB: SELECT from_state, to_state, actor_type, amount_minor, reference, reason_code FROM payment_event WHERE order_id = :'order' ORDER BY occurred_at, id; -> DISPUTED→KITCHEN_RECEIPT_REPORTED actor COOK amount 7500.
  - DB: SELECT category, severity, state FROM support_case WHERE order_id = :'order' ORDER BY created_at; -> the PAYMENT case is still OPEN (nothing closes it automatically; staff resolve it by hand).
- **Also covered by automated tests:** DepositPaymentIntegrationTest
- **Unverified against code (confirm while executing):** That the case stays open is derived from the code (no auto-close); confirm whether the owner wants auto-resolution.

### E2E-FIN-109 — Kitchen reports a different amount arrived: DISPUTED with the observed amount recorded

- **Status:** NOT_RUN · **Priority:** P2 · **Readiness:** Ready to run manually now
- **Surface:** operations-web, api · **Actors:** FX-COOK-A
- **Traces to:** rules: BR-026; decisions: D16; operations: postCookOrdersPaymentProblem; partial: TC-161
- **Prerequisites:**
  - Preview stack reachable: customer web https://app.<PREVIEW_DOMAIN>, cook/staff web https://ops.<PREVIEW_DOMAIN>, API https://api.<PREVIEW_DOMAIN>. seed-preview.sql is applied: on the customer web home, kitchen 'مطبخ المعاينة' lists 'Preview koshari' (EGP 75.00) and 'Preview mahshi' (EGP 180.00), and kitchen 'مطبخ تجريبي تاني' lists 'Preview molokhia' (EGP 120.25).
  - FX-COOK-A signs in at https://ops.<PREVIEW_DOMAIN> and the tab bar shows 'Orders' (ar: الطلبات); GET /api/v1/cook/dashboard in that session returns 200.
  - A read-only database method approved by the operator is available (psql through the operator channel); replace :'order' with the order UUID.
  - In the DevTools Console of the signed-in tab, define the helper `api` from conventions.api_helper (needed for the API steps).
  - Kitchen A payment profile is the seeded one: deposit 50% (deposit_bps 5000), no COD cap, recipient INSTAPAY 'demo-kitchen@instapay.invalid' / 'Demo kitchen (not a real account)' with recipient_status APPROVED (verify: GET /api/v1/cook/payment-profile as FX-COOK-A).
  - A pending deposit order D1 (deposit 7500) with the transfer reported.
- **Steps:**
  1. (FX-COOK-A) In the Deposit panel type Amount that arrived (EGP) = 50.00 and press 'A different amount arrived'. — data: 5000
  2. (FX-COOK-A) Console: POST …/payment/problem {expectedVersion:v, kind:'AMOUNT_MISMATCH'} (no amount); and {kind:'RECEIVED'}.
- **Expected:**
  - UI: Panel badge 'Payment disputed'.
  - API: Step 1 200 state DISPUTED. Missing amount: 422 VALIDATION_FAILED fieldErrors [{amountMinor,'REQUIRED_FOR_MISMATCH'}]. Unknown kind: 400 MALFORMED_REQUEST (there is no way to record 'received' through the problem endpoint).
  - DB: SELECT from_state, to_state, actor_type, amount_minor, reference, reason_code FROM payment_event WHERE order_id = :'order' ORDER BY occurred_at, id; -> →DISPUTED actor COOK amount_minor 5000 reason_code AMOUNT_MISMATCH.
  - DB: SELECT category, severity, state FROM support_case WHERE order_id = :'order' ORDER BY created_at; -> one PAYMENT case.
- **Also covered by automated tests:** DepositPaymentIntegrationTest

### E2E-FIN-110 — Settlements are dormant under zero commission: zero-amount entries cannot be batched

- **Status:** NOT_RUN · **Priority:** P2 · **Readiness:** Ready after the extra synthetic fixtures are provisioned
- **Surface:** api · **Actors:** FX-STAFF-FINANCE
- **Fixtures to provision:** FX-STAFF-FINANCE
- **Traces to:** rules: BR-036, BR-033; decisions: D10; operations: postOpsFinanceSettlements; partial: TC-036, TC-157
- **Prerequisites:**
  - Preview stack reachable: customer web https://app.<PREVIEW_DOMAIN>, cook/staff web https://ops.<PREVIEW_DOMAIN>, API https://api.<PREVIEW_DOMAIN>. seed-preview.sql is applied: on the customer web home, kitchen 'مطبخ المعاينة' lists 'Preview koshari' (EGP 75.00) and 'Preview mahshi' (EGP 180.00), and kitchen 'مطبخ تجريبي تاني' lists 'Preview molokhia' (EGP 120.25).
  - A read-only database method approved by the operator is available (psql through the operator channel); replace :'order' with the order UUID.
  - FX-STAFF-FINANCE (PROVISION) holds finance.read, finance.verify, finance.refund_approve and finance.settlement; the ops web shows its 'Finance' tab (the tab appears only for accounts that may read statements, so finance.verify alone is not enough). Payment recipient approval and payment decisions also need a recent Keycloak MFA step-up (PR #9 realm flow); cash verification, refunds and settlements do not.
  - A COMPLETED order O1 at kitchen A with its COMMISSION ledger group id G (SELECT id FROM ledger_group WHERE order_id = '<O1>' AND kind = 'COMMISSION';).
- **Steps:**
  1. (FX-STAFF-FINANCE) Ops web > 'Finance' > kitchen 00000000-0000-4000-8000-0000000bd001: open the 'Settlements' card and read 'Unsettled entries'.
  2. (FX-STAFF-FINANCE) Console: POST /api/v1/ops/finance/settlements {kitchenId:'00000000-0000-4000-8000-0000000bd001', period:{from:<today Cairo>,to:<today Cairo>}, entryIds:[G]} (fresh key). Then with a random UUID, then with an empty list.
- **Expected:**
  - UI: 'Unsettled entries' is empty (zero-amount groups are not listed).
  - API: Zero-amount entry: 422 VALIDATION_FAILED fieldErrors [{entryIds,'ZERO_AMOUNT'}]. Random UUID: 422 entryIds UNKNOWN_ENTRY. Empty list: 422 entryIds SIZE_1_500.
  - DB: SELECT count(*) FROM settlement_batch; unchanged (the draft is rolled back with the failed request).
- **Also covered by automated tests:** FinanceOperationsIntegrationTest; ZeroCommissionIntegrationTest
- **Unverified against code (confirm while executing):** That the failed request leaves no draft batch (inserted inside the same transaction as the failing line).

### E2E-FIN-111 — Cook statement shows completed orders, cash states and no commission; only the owner kitchen's data

- **Status:** NOT_RUN · **Priority:** P1 · **Readiness:** Ready to run manually now
- **Surface:** operations-web, api · **Actors:** FX-COOK-A, FX-CUST-A
- **Traces to:** rules: BR-032, BR-033; decisions: D10; operations: getCookStatements; partial: TC-120, TC-157
- **Prerequisites:**
  - Preview stack reachable: customer web https://app.<PREVIEW_DOMAIN>, cook/staff web https://ops.<PREVIEW_DOMAIN>, API https://api.<PREVIEW_DOMAIN>. seed-preview.sql is applied: on the customer web home, kitchen 'مطبخ المعاينة' lists 'Preview koshari' (EGP 75.00) and 'Preview mahshi' (EGP 180.00), and kitchen 'مطبخ تجريبي تاني' lists 'Preview molokhia' (EGP 120.25).
  - FX-COOK-A signs in at https://ops.<PREVIEW_DOMAIN> and the tab bar shows 'Orders' (ar: الطلبات); GET /api/v1/cook/dashboard in that session returns 200.
  - In the DevTools Console of the signed-in tab, define the helper `api` from conventions.api_helper (needed for the API steps).
  - At least one completed order with cash REPORTED/VERIFIED and one with cash DUE at kitchen A today (Africa/Cairo).
  - Each actor uses a SEPARATE browser profile (or private window/cookie store): customer web, ops web and the API share one session cookie (SecurityConfiguration), so signing in as a second actor in the same profile silently replaces the first. Before each actor's steps run `await api('GET','/api/v1/me')` (helper in conventions.api_helper) and confirm the displayName is that actor.
- **Steps:**
  1. (FX-COOK-A) Ops web > 'Statement': leave the default range (month to date), press 'Show'. Read the summary and the table.
  2. (FX-COOK-A) Set 'From' after today and press 'Show'.
  3. (FX-COOK-A) Console: GET /api/v1/cook/statements?from=2020-01-01&to=2026-12-31 (range over 366 days).
  4. (FX-CUST-A) Console: GET /api/v1/cook/statements as the customer.
- **Expected:**
  - UI: Summary rows 'Completed orders', 'Order value', 'Cash not reported', 'Cash reported', 'Cash verified'; notice 'The cash you collect is yours. Includes refunds and settlements.'; table columns #, Date, Total, 'Report cash collected' (cash state text) and no commission column. Future range: zero completed orders.
  - API: Range over 366 days: 422 VALIDATION_FAILED fieldErrors [{to,'RANGE_MAX_366_DAYS'}]. Customer: 403 FORBIDDEN.
  - DB: Statement totals equal SQL over completed orders of kitchen A in the Cairo date range (events with to_state = 'COMPLETED' inside [from 00:00, to+1 00:00) Africa/Cairo).
- **Also covered by automated tests:** CashAndLedgerIntegrationTest; ZeroCommissionIntegrationTest

### E2E-FIN-112 — Finance and payment endpoints are limited to the granted staff; Finance tab visibility follows the grants

- **Status:** NOT_RUN · **Priority:** P0 · **Readiness:** Ready after the extra synthetic fixtures are provisioned
- **Surface:** operations-web, api · **Actors:** FX-CUST-A, FX-COOK-A, FX-STAFF-NOGRANT, FX-STAFF-SUPPORT, FX-STAFF-OPS, FX-STAFF-FINANCE
- **Fixtures to provision:** FX-STAFF-OPS, FX-STAFF-SUPPORT, FX-STAFF-FINANCE
- **Traces to:** rules: BR-006, BR-001; decisions: D05, D18; operations: getOpsPayments, getOpsPaymentRecipients, getOpsFinanceCashReports, getOpsFinanceStatements, postOpsOrdersPaymentDecision, postOpsFinanceRefunds, postOpsFinanceSettlements; partial: TC-001, TC-006, TC-125
- **Prerequisites:**
  - Preview stack reachable: customer web https://app.<PREVIEW_DOMAIN>, cook/staff web https://ops.<PREVIEW_DOMAIN>, API https://api.<PREVIEW_DOMAIN>. seed-preview.sql is applied: on the customer web home, kitchen 'مطبخ المعاينة' lists 'Preview koshari' (EGP 75.00) and 'Preview mahshi' (EGP 180.00), and kitchen 'مطبخ تجريبي تاني' lists 'Preview molokhia' (EGP 120.25).
  - FX-STAFF-FINANCE (PROVISION) holds finance.read, finance.verify, finance.refund_approve and finance.settlement; the ops web shows its 'Finance' tab (the tab appears only for accounts that may read statements, so finance.verify alone is not enough). Payment recipient approval and payment decisions also need a recent Keycloak MFA step-up (PR #9 realm flow); cash verification, refunds and settlements do not.
  - Staff accounts for this case are PROVISIONED fixtures: grants are written by the operator (DB insert into user_grant) or by FX-STAFF-ADMIN in the ops 'Staff permissions' screen; granting through the API requires staff.grant AND recent MFA (StaffController), which needs the PR #9 Keycloak step-up flow.
  - A deposit order D1 in any payment state and a completed order O1; ids known.
  - Each actor uses a SEPARATE browser profile (or private window/cookie store): customer web, ops web and the API share one session cookie (SecurityConfiguration), so signing in as a second actor in the same profile silently replaces the first. Before each actor's steps run `await api('GET','/api/v1/me')` (helper in conventions.api_helper) and confirm the displayName is that actor.
- **Steps:**
  1. (guest) Without a session call: GET https://api.<PREVIEW_DOMAIN>/api/v1/ops/payments?state=DISPUTED.
  2. (FX-CUST-A) Console (any tab with the helper): GET /api/v1/ops/payments?state=DISPUTED; GET /api/v1/ops/payment-recipients; GET /api/v1/ops/finance/cash-reports; GET /api/v1/ops/finance/statements?kitchenId=00000000-0000-4000-8000-0000000bd001.
  3. (FX-COOK-A) Same four calls, then POST /api/v1/ops/orders/<D1>/payment/decision {expectedVersion:0, outcome:'REFUND_OWED', reason:'cook tries staff decision'}.
  4. (FX-STAFF-NOGRANT / FX-STAFF-SUPPORT / FX-STAFF-OPS) Sign in to ops web and note the tabs; repeat the four GETs and POST /api/v1/ops/finance/cash-reports/<any uuid>/verification, POST /api/v1/ops/finance/refunds and POST /api/v1/ops/finance/settlements.
  5. (FX-STAFF-FINANCE) Sign in: 'Finance' tab present; the same GETs.
- **Expected:**
  - UI: No-grant, support and ops-only staff do not see the 'Finance' tab; finance staff do.
  - API: Guest: 401 UNAUTHENTICATED. Customer, cook, no-grant/support/ops-only staff: 403 FORBIDDEN on every finance/payment call (the statements probe returns 403 instead of 404). Finance staff: 200 on the GETs. The payment decision additionally returns 403 STEP_UP_REQUIRED without a recent MFA proof.
  - DB: audit_event shows DENIED/absent entries for the forbidden attempts; no order_payment, cash_report, refund or settlement_batch row changed.
- **Also covered by automated tests:** FinanceOperationsIntegrationTest; IdentityIntegrationTest
- **Unverified against code (confirm while executing):** Whether permission denials are written to audit_event (cash.verify self-verification denial is; generic @PreAuthorize denials may not be).

### E2E-FIN-113 — Native apps: deposit checkout and honest payment wording on iOS and Android

- **Status:** BLOCKED — No signed iOS/Android build pointing at the preview is confirmed; native verification is mandatory but store/TestFlight setup is deferred. · **Priority:** P1 · **Readiness:** Needs a physical iOS/Android build
- **Surface:** customer-mobile · **Actors:** FX-CUST-A, FX-DEVICE-IOS, FX-DEVICE-ANDROID, FX-COOK-A · **Needs:** physical device
- **Traces to:** rules: BR-019, BR-049; decisions: D16; operations: postOrdersPaymentTransferReport; partial: TC-132, TC-163
- **Prerequisites:**
  - Signed iOS and Android builds pointing at the preview API; Preview stack reachable: customer web https://app.<PREVIEW_DOMAIN>, cook/staff web https://ops.<PREVIEW_DOMAIN>, API https://api.<PREVIEW_DOMAIN>. seed-preview.sql is applied: on the customer web home, kitchen 'مطبخ المعاينة' lists 'Preview koshari' (EGP 75.00) and 'Preview mahshi' (EGP 180.00), and kitchen 'مطبخ تجريبي تاني' lists 'Preview molokhia' (EGP 120.25).
  - Kitchen A payment profile is the seeded one: deposit 50% (deposit_bps 5000), no COD cap, recipient INSTAPAY 'demo-kitchen@instapay.invalid' / 'Demo kitchen (not a real account)' with recipient_status APPROVED (verify: GET /api/v1/cook/payment-profile as FX-COOK-A).
  - Each actor uses a SEPARATE browser profile (or private window/cookie store): customer web, ops web and the API share one session cookie (SecurityConfiguration), so signing in as a second actor in the same profile silently replaces the first. Before each actor's steps run `await api('GET','/api/v1/me')` (helper in conventions.api_helper) and confirm the displayName is that actor.
- **Steps:**
  1. (FX-CUST-A) On each device: add 2 × 'Preview koshari' (13:00 tomorrow), basket, choose 'Deposit sent straight to the kitchen', 'Calculate total'.
  2. (FX-CUST-A) Accept terms, 'Place order', open the order, type reference 'TEST-REF-0003', press 'I sent the deposit'. Press it a second time if the button is still visible.
  3. (FX-COOK-A) On the web workspace press 'The deposit arrived (EGP 75.00)'.
  4. (FX-CUST-A) Pull to refresh the order on the device.
- **Expected:**
  - UI: Quote shows 'Deposit now: 75.00', 'Rest on receipt: 75.00', the InstaPay demo recipient and the note that the platform never receives or holds the money. Status texts: 'Waiting for your deposit' -> 'You reported the transfer; waiting for the kitchen to confirm' -> 'The kitchen reported receiving the deposit'. No wording says 'verified by the bank' or 'paid'. Arabic (RTL) variant uses the ar strings.
  - API: Same API calls as E2E-FIN-039 steps 1-5; the second report is idempotent (200).
  - DB: SELECT from_state, to_state, actor_type, amount_minor, reference, reason_code FROM payment_event WHERE order_id = :'order' ORDER BY occurred_at, id; -> exactly one CUSTOMER_TRANSFER_REPORTED event per order.
- **Also covered by automated tests:** apps/customer-mobile/src/App.test.tsx (jest, deposit screens)

### E2E-FIN-114 — A person who reported cash can never verify it (separation of duties), and the denial is audited

- **Status:** NOT_RUN · **Priority:** P2 · **Readiness:** Needs the updated application deployment
- **Surface:** api · **Actors:** FX-MULTIROLE · **Needs:** updated deploy
- **Fixtures to provision:** FX-MULTIROLE, FX-STAFF-FINANCE, FX-STAFF-ADMIN-B
- **Traces to:** rules: BR-032, BR-006; decisions: D05; operations: postOpsFinanceCashReportsVerification; partial: TC-006, TC-120
- **Prerequisites:**
  - Preview stack reachable: customer web https://app.<PREVIEW_DOMAIN>, cook/staff web https://ops.<PREVIEW_DOMAIN>, API https://api.<PREVIEW_DOMAIN>. seed-preview.sql is applied: on the customer web home, kitchen 'مطبخ المعاينة' lists 'Preview koshari' (EGP 75.00) and 'Preview mahshi' (EGP 180.00), and kitchen 'مطبخ تجريبي تاني' lists 'Preview molokhia' (EGP 120.25).
  - A read-only database method approved by the operator is available (psql through the operator channel); replace :'order' with the order UUID.
  - FX-MULTIROLE (seed-owner-e2e.sql: customer + approved cook of kitchen 'مطبخ متعدد الأدوار' + support.case). finance.verify is NOT granted by the fixture: FX-STAFF-ADMIN-B (staff.grant, recent MFA) grants it temporarily and revokes it afterwards. The seeded multirole kitchen has no meal or slot: the cook creates one, then FX-CUST-A orders and the order is completed so cash is DUE.
  - Console helper on the ops origin.
- **Steps:**
  1. (FX-MULTIROLE) As cook: POST /api/v1/cook/orders/<O>/cash-reports with the exact amount.
  2. (FX-MULTIROLE) As staff in the same account: POST /api/v1/ops/finance/cash-reports/<reportId>/verification {decision:'VERIFY', verifiedAmountMinor:<amount>, expectedVersion:0}.
  3. (FX-STAFF-FINANCE) Verify the same report as a different person.
- **Expected:**
  - UI: Not applicable.
  - API: Step 2: 403 FORBIDDEN (message 'separation of duties'). Step 3: 200 VERIFIED.
  - DB: SELECT action, outcome, reason FROM audit_event WHERE action = 'cash.verify' AND object_id = '<reportId>' ORDER BY occurred_at; -> a DENIED row 'reporter cannot verify own report' followed by the ALLOWED row. Database check cash_report_separation_ck prevents verified_by = reporter_id.
- **Also covered by automated tests:** CashAndLedgerIntegrationTest
- **Unverified against code (confirm while executing):** A second approved kitchen with meals and slots is not in seed-preview.sql; the fixture must be built.; Which account in the final fixture set holds both kitchen ownership and finance.verify.

### E2E-FIN-115 — Deposit orders that end without a reported transfer expire without a case; a disputed deposit can be closed without funds

- **Status:** NOT_RUN · **Priority:** P1 · **Readiness:** Needs the updated application deployment
- **Surface:** operations-web, customer-web, api · **Actors:** FX-CUST-A, FX-COOK-A, FX-STAFF-FINANCE · **Needs:** updated deploy
- **Fixtures to provision:** FX-STAFF-FINANCE
- **Traces to:** rules: BR-027, BR-029; decisions: D16, D18; operations: postCookOrdersReject, postOpsOrdersPaymentDecision; partial: TC-160, TC-161, TC-162
- **Prerequisites:**
  - Preview stack reachable: customer web https://app.<PREVIEW_DOMAIN>, cook/staff web https://ops.<PREVIEW_DOMAIN>, API https://api.<PREVIEW_DOMAIN>. seed-preview.sql is applied: on the customer web home, kitchen 'مطبخ المعاينة' lists 'Preview koshari' (EGP 75.00) and 'Preview mahshi' (EGP 180.00), and kitchen 'مطبخ تجريبي تاني' lists 'Preview molokhia' (EGP 120.25).
  - FX-COOK-A signs in at https://ops.<PREVIEW_DOMAIN> and the tab bar shows 'Orders' (ar: الطلبات); GET /api/v1/cook/dashboard in that session returns 200.
  - A read-only database method approved by the operator is available (psql through the operator channel); replace :'order' with the order UUID.
  - In the DevTools Console of the signed-in tab, define the helper `api` from conventions.api_helper (needed for the API steps).
  - Kitchen A payment profile is the seeded one: deposit 50% (deposit_bps 5000), no COD cap, recipient INSTAPAY 'demo-kitchen@instapay.invalid' / 'Demo kitchen (not a real account)' with recipient_status APPROVED (verify: GET /api/v1/cook/payment-profile as FX-COOK-A).
  - FX-STAFF-FINANCE (PROVISION) holds finance.read, finance.verify, finance.refund_approve and finance.settlement; the ops web shows its 'Finance' tab (the tab appears only for accounts that may read statements, so finance.verify alone is not enough). Payment recipient approval and payment decisions also need a recent Keycloak MFA step-up (PR #9 realm flow); cash verification, refunds and settlements do not.
  - PR #9 realm step-up for step 4. Two deposit orders: E1 (pending, nothing reported) and E2 (ACCEPTED, customer transfer reported, kitchen already pressed 'Not received' so the payment is DISPUTED).
  - Each actor uses a SEPARATE browser profile (or private window/cookie store): customer web, ops web and the API share one session cookie (SecurityConfiguration), so signing in as a second actor in the same profile silently replaces the first. Before each actor's steps run `await api('GET','/api/v1/me')` (helper in conventions.api_helper) and confirm the displayName is that actor.
- **Steps:**
  1. (FX-COOK-A) Reject E1 with Reason 'Kitchen busy'.
  2. (FX-CUST-A) Reload E1.
  3. (FX-COOK-A) On E2 (payment DISPUTED, order ACCEPTED) press 'Cancel'.
  4. (FX-STAFF-FINANCE) Finance > 'Payments needing attention' > 'Needs reconciliation' > E2: Decision 'Closed without funds', Reason 'Transfer never arrived (test)', 'Record decision'.
  5. (FX-CUST-A) Reload E2.
- **Expected:**
  - UI: E1: Payment 'Deposit time ran out', order 'Rejected by the kitchen', no 'Deposit review' case. E2: Payment 'Closed without funds'; no refund input appears on the cook card.
  - API: E1 payment state EXPIRED. E2: REFUND_OWED never entered; decision 200 state CLOSED_NO_FUNDS. Cook refund endpoint on E2 -> 409 STATE_CONFLICT fieldErrors [{payment,'NO_REFUND_DUE'}].
  - DB: E1: SELECT from_state, to_state, actor_type, amount_minor, reference, reason_code FROM payment_event WHERE order_id = :'order' ORDER BY occurred_at, id; -> WAITING_FOR_DEPOSIT→EXPIRED; SELECT count(*) FROM support_case WHERE order_id = '<E1>'; -> 0. E2: …DISPUTED→RECONCILE_PENDING→CLOSED_NO_FUNDS (STAFF, audit_event payment.decision outcome CLOSED_NO_FUNDS); one PAYMENT case.
- **Also covered by automated tests:** PaymentReconciliationIntegrationTest
