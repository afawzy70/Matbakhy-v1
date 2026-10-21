# User journeys: cook operations (onboarding to suspension)

> **Owner role:** Frontend and mobile
> **Status:** PARTIALLY IMPLEMENTED — the cook workspace (orders, meals, slots, settings, payment profile, statement, insights) and the staff approval flow work through API and web; the cook application form has an API but no screen.
> **Language / اللغة:** English · [العربية](cook-operations-ar.md)

The cook workspace is the first group of tabs of the **operations web app** (`apps/operations-web`), shared with the staff tabs ([Backoffice administration](backoffice-admin.md)). Who is a cook is decided on the server: an account that **owns an approved kitchen** (`KitchenService.ownedBy` in `backend/modules/cooks/src/main/java/com/matbakhy/cooks/internal/KitchenService.java`). There is no cook role claim in tokens (`AccountMapper` ignores token roles). Business background: [Kitchen management](../01-business/kitchen-management.md) and [Order lifecycle](../01-business/order-lifecycle.md).

## Screen map (`apps/operations-web/src/App.tsx`)

On load `checkSession` calls `GET /api/v1/me`, then probes `GET /api/v1/cook/dashboard` and several staff endpoints in parallel. A `403` from the dashboard means "no approved kitchen". The cook tabs are `dashboard, orders, meals, slots, statement, insights, settings` (constant `COOK_TABS`); `security` is added for everyone with at least one section. With no kitchen and no staff permission the account sees the onboarding panel instead (`apps/operations-web/src/cook/Onboarding.tsx`), which only displays the application state (`GET /api/v1/cook/application`; 404 = "no application").

| Tab | File | What it does |
|---|---|---|
| dashboard | `apps/operations-web/src/cook/Dashboard.tsx` | queue counts, acceptance deadlines, upcoming slot totals (`GET /api/v1/cook/dashboard`) |
| orders | `cook/Orders.tsx` | active / completed orders; only the actions in the server's `allowedActions`; reason selects; handover code input (6 digits); dispatch reference; cash report after completion |
| meals | `cook/Meals.tsx` | list, create, edit, photo upload, publish / pause / archive, price history |
| slots | `cook/Slots.tsx` | create, edit capacity, change state |
| statement | `cook/Statement.tsx` | completed orders, cash states, no commission (`GET /api/v1/cook/statements`) |
| insights | `cook/Insights.tsx` | organic visit/view/add-to-basket counts for the own kitchen (`GET /api/v1/cook/insights`) |
| settings | `cook/Settings.tsx`, `cook/Payments.tsx` | description, pickup, delivery zones, pause/resume, deposit payment profile |

Common to all journeys on this page: **Actor** is an account with an approved kitchen (cook) unless stated; every `/api/v1/cook/**` call loads the kitchen of the caller and refuses another kitchen's object (`FORBIDDEN "not a cook"` or 404; E2E-COOK-054). **Transport** is the web session cookie + CSRF header, as for the customer web (`apps/operations-web/src/api.ts`). **Tests (all):** `apps/operations-web/src/App.test.tsx` (describe "operations-web cook workspace"), real-API Playwright `apps/operations-web/e2e/cook-workspace.e2e.ts`, `money-reflow.e2e.ts`, `keyboard.e2e.ts`, `operations-layout.e2e.ts` (run through `scripts/dev.sh e2e`; results for the current commit: NOT VERIFIED here). Domain state names are in [Order lifecycle](../01-business/order-lifecycle.md).

## CO-1 Onboarding: apply, submit, wait for the decision

1. **Goal.** A customer account asks to become a cook; staff verify operationally (not a food-safety certificate).
2. **Actor / permissions.** Any active account (`COOK_APPLICANT` in `contracts/endpoints.json`); staff side needs `cook.review`.
3. **Prerequisites.** Signed in. A phone for the application uses the pattern `+20 1[0125]xxxxxxxx` (CHECK in V5).
4. **Starting screen.** No screen starts the application. API-only: `PUT /api/v1/cook/application`. The operations web shows the state afterwards (`Onboarding.tsx`). Status of the form: PLANNED UI (the comment in `Onboarding.tsx` says editing is "a later customer-web/mobile flow"; no call to `/cook/application` exists in `apps/customer-web/src` or `apps/customer-mobile/src`).
5. **User actions.** Save draft (name, bio, contact phone, private location, pickup flag, zone ids, document upload ids, `expectedVersion`); upload private documents through `POST /api/v1/uploads` + `POST /api/v1/uploads/{uploadId}/complete`; submit.
6. **Responses.** The draft returns state and history; submit creates revision 1; incomplete drafts return `VALIDATION_FAILED` naming the missing fields; the applicant stays a non-cook until approval (E2E-COOK-048).
7. **API.** `GET/PUT /api/v1/cook/application` (API-042, API-043), `POST /api/v1/cook/application/submit` (API-044), uploads (API-039 to API-041).
8. **Backend.** `backend/modules/cooks/src/main/java/com/matbakhy/cooks/web/CookApplicationController.java` → `internal/CookApplicationService.java`; documents `internal/ApplicationDocumentAccess.java`; uploads `backend/modules/catalog/src/main/java/com/matbakhy/catalog/web/UploadController.java`.
9. **DB.** `cook_application`, `cook_application_revision` (V5), `media_asset` (V6).
10. **Transitions.** `DRAFT → SUBMITTED` (editable states: draft and changes-requested); staff: `SUBMITTED → UNDER_REVIEW → APPROVED | CHANGES_REQUESTED | REJECTED`; resubmission creates the next revision (E2E-COOK-111).
11. **Errors.** `VERSION_CONFLICT` on stale `expectedVersion`; `STATE_CONFLICT` when not editable; private documents readable only by owner and reviewing staff (E2E-COOK-053).
12. **Tests.** `backend/app/src/test/java/com/matbakhy/app/cooks/CookWorkflowIntegrationTest.java`, `backend/app/src/test/java/com/matbakhy/app/media/MediaIntegrationTest.java`.
13. **Owner E2E.** E2E-COOK-048, E2E-COOK-053, E2E-COOK-111; J6 for approval.
14. **Status.** API IMPLEMENTED; applicant UI PLANNED / absent.
15. **Limitations.** Applicants cannot onboard without API access; owner E2E E2E-COOK-048 is therefore an API-level case.

## CO-2 Approval by staff and kitchen creation

1. **Goal.** A reviewer approves the application; a kitchen with a public slug is created.
2. **Actor / permissions.** Staff with `cook.review`; decisions also need **recent MFA** (`@staffAssurance.recentMfa`). A reviewer cannot decide their own application (denial audited).
3. **Prerequisites.** Submitted application; reviewer MFA within `OIDC_STEP_UP_MAX_AGE` (default 10 minutes).
4. **Start.** Operations web tab "reviews" (`apps/operations-web/src/ops/Reviews.tsx`).
5. **Actions.** Open the queue, open an application and revision (private documents via `GET /api/v1/uploads/{uploadId}`), START_REVIEW, then APPROVE / REQUEST_CHANGES / REJECT with a reason (3 to 1000 characters).
6. **Responses.** Reviewer sees revisions and decisions; on approval a kitchen appears and the applicant's next `GET /api/v1/cook/dashboard` succeeds.
7. **API.** `GET /api/v1/ops/cooks` (API-067), `GET /api/v1/ops/cooks/{applicationId}` (API-068, audited), `POST /api/v1/ops/cooks/{applicationId}/decision` (API-069, `Idempotency-Key`, `expectedVersion`, `revision`).
8. **Backend.** `CookReviewController.java` → `CookReviewService.java` (`refuseSelfReview`, `decide`, `createFromApproval` in `KitchenService`).
9. **DB.** `cook_application`, `cook_application_revision`, `kitchen`, `kitchen_zone` (V5), `audit_event` (V4).
10. **Transitions.** As CO-1; `START_REVIEW` only from `SUBMITTED`; decisions only from `UNDER_REVIEW`; kitchen `operating_state = ACTIVE` at creation.
11. **Errors.** `FORBIDDEN` self review; `VERSION_CONFLICT`; `STATE_CONFLICT` stale revision; `STEP_UP_REQUIRED` without recent MFA.
12. **Tests.** `backend/app/src/test/java/com/matbakhy/app/cooks/CookWorkflowIntegrationTest.java`, `backend/app/src/test/java/com/matbakhy/app/security/StaffAssuranceTest.java`, `apps/operations-web/src/App.test.tsx`.
13. **Owner E2E.** E2E-COOK-049, E2E-OPS-108, E2E-OPS-109, E2E-COOK-111; J6 (needs the updated deployment and staff MFA).
14. **Status.** IMPLEMENTED.
15. **Limitations.** The reviewing UI relies on the staff account's MFA being usable at Keycloak (preview step-up depends on the realm migration, CFG-028).

## CO-3 Meals and pricing

1. **Goal.** Create a meal, price it, publish it; every price change is recorded.
2. **Actor.** Cook (own kitchen).
3. **Prerequisites.** Approved, active kitchen. Publishing needs a complete allergen declaration (`CONTAINS` requires codes), price 1 to 1,000,000 piastres, and at least one approved (moderated) photo.
4. **Start.** Tab "meals".
5. **Actions.** New meal (Arabic/English title and description, category, price in EGP converted to integer minor units by `egpToMinor` in `apps/operations-web/src/cook/shared.tsx`, allergens, photos); upload a photo (ticket → upload → complete); Publish, Pause, Archive; view price history.
6. **Responses.** Each missing publish requirement is named (E2E-COOK-101); the customer sees the meal only after publication and moderation allows it (E2E-COOK-051).
7. **API.** `GET/POST /api/v1/cook/meals` (API-046, API-047), `PATCH /api/v1/cook/meals/{mealId}` (API-048), `POST /api/v1/cook/meals/{mealId}/publication` body `{action: PUBLISH|PAUSE|ARCHIVE, expectedVersion}` (API-049), `GET /api/v1/cook/meals/{mealId}/price-history` (API-099), uploads (API-039/040).
8. **Backend.** `backend/modules/catalog/src/main/java/com/matbakhy/catalog/web/MealController.java` → `catalog/internal/meal/MealService.java`; media `catalog/internal/media/MediaService.java`, `MediaInspector.java`.
9. **DB.** `meal`, `meal_revision`, `meal_media` (V7), `media_asset` (V6), `media_cleanup` (V30), moderation column (V16).
10. **Transitions.** Meal `DRAFT → PUBLISHED ↔ PAUSED → ARCHIVED` (archived is read-only); staff can additionally set `moderation_state` `VISIBLE | HIDDEN` without touching cook state (E2E-OPS-056).
11. **Errors.** `VALIDATION_FAILED` with field codes (`PHOTO_NOT_APPROVED`, `ALLERGEN…`); `KITCHEN_UNAVAILABLE` when the kitchen cannot sell; another cook's meal id → 404 (E2E-COOK-054); wrong file types and spoofed content refused (E2E-COOK-102).
12. **Tests.** `backend/app/src/test/java/com/matbakhy/app/catalog/MealIntegrationTest.java`, `PriceHistoryIntegrationTest.java`, `ModerationIntegrationTest.java`, `MediaIntegrationTest.java`, `apps/operations-web/src/App.test.tsx`.
13. **Owner E2E.** E2E-COOK-051, E2E-COOK-101, E2E-COOK-102, E2E-COOK-103, E2E-COOK-104; J6.
14. **Status.** IMPLEMENTED.
15. **Limitations.** A price change affects only new quotes: an earlier quote is refused with `PRICE_CHANGED` and a placed order keeps its snapshot (E2E-BASKET-014/015). Server owns price; the web converts text to piastres only for input.

## CO-4 Availability, slots and capacity

1. **Goal.** Offer dated time slots with portion capacity (BR-018 single cook and slot per order).
2. **Actor.** Cook.
3. **Prerequisites.** Published meals to attach.
4. **Start.** Tab "slots".
5. **Actions.** Create a slot (Cairo local date, start/end, `maxPortions`, lead minutes, per-meal capacities); open it; change capacity; pause / close / cancel.
6. **Responses.** A slot becomes orderable when `OPEN` and before cutoff; capacity cannot drop below portions held by orders (E2E-COOK-106); cutoff in the past or overlap refused (E2E-COOK-105).
7. **API.** `GET/POST /api/v1/cook/slots` (API-050, API-051), `PATCH /api/v1/cook/slots/{slotId}` (API-052), public `GET /api/v1/kitchens/{kitchenId}/slots` (API-006).
8. **Backend.** `backend/modules/availability/src/main/java/com/matbakhy/availability/web/SlotController.java` → `internal/SlotService.java`, `JdbcSlotInventory.java` (reservation locks), `CairoTime.java`.
9. **DB.** `cook_slot`, `meal_slot` (V8), `stock_allocation` (V11).
10. **Transitions.** Slot `DRAFT → OPEN → PAUSED / CLOSED / CANCELLED`; stock allocation `RESERVED → ALLOCATED → CONSUMED | RELEASED` driven by order commands.
11. **Errors.** `SLOT_CLOSED` (`CUTOFF_PASSED`), `STATE_CONFLICT` (overlap, capacity below obligations, slot has obligations), `VERSION_CONFLICT`.
12. **Tests.** `backend/app/src/test/java/com/matbakhy/app/availability/SlotIntegrationTest.java`, `apps/operations-web/e2e/cook-workspace.e2e.ts`.
13. **Owner E2E.** E2E-COOK-052, E2E-COOK-105, E2E-COOK-106, E2E-BASKET-016, E2E-BASKET-017, E2E-ORDER-114; J6.
14. **Status.** IMPLEMENTED.
15. **Limitations.** Capacity concurrency is tested in backend integration tests; load behaviour: NOT VERIFIED.

## CO-5 Incoming orders: accept, reject, prepare, ready, dispatch, handover

1. **Goal.** Move a customer order through the kitchen steps with server-checked transitions.
2. **Actor.** Cook of the order's kitchen.
3. **Prerequisites.** Order in the right state; kitchen able to accept (`canAcceptOrders`) or fulfil; for deposit orders the receipt gate (below).
4. **Start.** Tab "orders" (filters ACTIVE / COMPLETED), or the dashboard shortcut.
5. **Actions.** Accept before the acceptance deadline (countdown from server time via `serverOffsetMs`); or reject with a reason (`KITCHEN_BUSY`, `INGREDIENT_UNAVAILABLE`, `OUTSIDE_CAPACITY`, `OTHER`); Prepare; Ready; for pickup: Handover with the customer's 6-digit code; for delivery: Dispatch with a courier reference, then Handover with the code or Delivery failure; Cancel while only `ACCEPTED`.
6. **Responses.** Only actions in `allowedActions` render; the cook sees the customer's name, phone and address only for the active order (`CookOrderView`), never the handover code.
7. **API.** `GET /api/v1/cook/orders`, `GET /api/v1/cook/orders/{orderId}` (API-053/054), and POST `…/accept`, `…/reject`, `…/prepare`, `…/ready`, `…/dispatch`, `…/handover`, `…/delivery-failure`, `…/cancel` (API-055 to API-062), each with `Idempotency-Key` and `expectedVersion`.
8. **Backend.** `backend/modules/orders/src/main/java/com/matbakhy/orders/web/CookOrderController.java` → `OrderLifecycleService.cook` (accept, reject, prepare, ready, dispatch, handover, delivery_failure, cancel); `OrderService.cookOrders`; `PaymentService.requireReadyForPreparation` (gate); `HandoverCodes.java`.
9. **DB.** `customer_order`, `order_event`, `stock_allocation` (V11), `handover_challenge`, `handover_attempt` (V12), `outbox_event`, `cash_obligation`, `order_payment` (V24).
10. **Transitions.** `PENDING_ACCEPTANCE → ACCEPTED | REJECTED | EXPIRED`; `ACCEPTED → PREPARING | CANCELLED`; `PREPARING → READY`; `READY → COMPLETED` (pickup, via handover) or `READY → OUT_FOR_DELIVERY → COMPLETED | DELIVERY_FAILED`. Stock: accept converts `RESERVED → ALLOCATED`; prepare `→ CONSUMED`; reject/cancel/expiry `→ RELEASED`. Five wrong handover codes lock the challenge; ops recovery is then required (E2E-ORDER-033). Cash obligation is set `NOT_DUE` on rejection, expiry and cook cancellation (before preparation); ops exception cancellation leaves it unchanged for finance review.
11. **Errors.** `STATE_CONFLICT` (wrong state or `DEADLINE_PASSED`, `WRONG_FULFILLMENT_MODE`), `KITCHEN_UNAVAILABLE` (a suspended or closed kitchen cannot accept; a paused kitchen can still accept already placed pending orders, `KitchenStatus.canAcceptOrders`), deposit not reported received (`STATE_CONFLICT` from the preparation gate, E2E-FIN-106), `VERSION_CONFLICT`, `VALIDATION_FAILED` for reasons outside the allowed sets.
12. **Tests.** `backend/app/src/test/java/com/matbakhy/app/orders/OrderLifecycleIntegrationTest.java`, `backend/app/src/test/java/com/matbakhy/app/payments/DepositPaymentIntegrationTest.java`, `apps/operations-web/e2e/cook-workspace.e2e.ts`, `apps/operations-web/src/App.test.tsx`.
13. **Owner E2E.** E2E-ORDER-025, E2E-ORDER-026, E2E-ORDER-028, E2E-ORDER-029, E2E-ORDER-030, E2E-ORDER-031, E2E-ORDER-032, E2E-ORDER-034, E2E-ORDER-036, E2E-ORDER-037, E2E-ORDER-110, E2E-ORDER-101, E2E-ORDER-102; J1, J2.
14. **Status.** IMPLEMENTED.
15. **Limitations.** Courier handling is a free-text reference; no courier integration. Cooks have no push alert for a new order in the web app (the dashboard must be opened or refreshed).

## CO-6 Cash and deposit reports by the kitchen

1. **Goal.** The kitchen states what it received; Matbakhy never holds the money and never claims bank verification.
2. **Actor.** Cook; verification by finance staff (see [Finance and support](finance-and-support.md)).
3. **Prerequisites.** Cash: order `COMPLETED` with cash state `DUE`. Deposit: payment state waiting or reported.
4. **Start.** Order card in the orders tab; deposit settings in the settings tab.
5. **Actions.** Save the payment profile (deposit percentage, optional cash-on-receipt cap, recipient); report deposit receipt (amount must equal the server-computed deposit), or report "not received" / "amount mismatch"; after cancellation report a refund sent with a reference; report collected cash once per order.
6. **Responses.** The payment status card updates; a changed recipient stays hidden from customers until finance staff approve it (E2E-COOK-108).
7. **API.** `GET/PUT /api/v1/cook/payment-profile` (API-102/103), `POST /api/v1/cook/orders/{orderId}/payment/receipt|problem|refund` (API-101, API-105, API-108), `POST /api/v1/cook/orders/{orderId}/cash-reports` (API-063), `GET /api/v1/cook/statements` (API-064).
8. **Backend.** `PaymentController.java`, `PaymentService.java` (orders module), `backend/modules/finance/src/main/java/com/matbakhy/finance/web/CashController.java` → `CashService.report`, `StatementController.java`.
9. **DB.** `kitchen_payment_profile`, `order_payment`, `payment_event` (V24), `cash_obligation` (V11), `cash_report` (V13), `ledger_group`, `ledger_entry` (V13).
10. **Transitions.** Payment: `WAITING_FOR_DEPOSIT → CUSTOMER_TRANSFER_REPORTED → KITCHEN_RECEIPT_REPORTED`, or `→ DISPUTED`, `RECONCILE_PENDING`, `EXPIRED` (full list in `docs/product/09-deposit-payment-states.md`). Cash: `DUE → REPORTED → VERIFIED | DISPUTED`.
11. **Errors.** `ORDER_ENDED` (report a refund instead), `NO_DEPOSIT_WAITING`, one cash report per order (E2E-FIN-102), receipt amount must match (E2E-FIN-041).
12. **Tests.** `backend/app/src/test/java/com/matbakhy/app/payments/DepositPaymentIntegrationTest.java`, `PaymentReconciliationIntegrationTest.java`, `backend/app/src/test/java/com/matbakhy/app/finance/CashAndLedgerIntegrationTest.java`, `ZeroCommissionIntegrationTest.java`, `apps/operations-web/e2e/money-reflow.e2e.ts`.
13. **Owner E2E.** E2E-FIN-038, E2E-FIN-039, E2E-FIN-040, E2E-FIN-043, E2E-FIN-106, E2E-FIN-107, E2E-FIN-108, E2E-FIN-109, E2E-FIN-111, E2E-FIN-102, E2E-COOK-108; J2 to J4.
14. **Status.** IMPLEMENTED (synthetic; live money is gated).
15. **Limitations.** Deposit transfer details are a kitchen statement, not a bank confirmation.

## CO-7 Kitchen settings, pause and suspension

1. **Goal.** The cook pauses/resumes selling; staff pause or suspend a kitchen.
2. **Actor.** Cook (settings: description, pickup, zones, pause); staff with `cook.review` + recent MFA (operating state).
3. **Prerequisites.** Approved kitchen.
4. **Start.** Settings tab (cook); Reviews tab, kitchen panel (staff).
5. **Actions.** Cook: edit description, toggle pickup, toggle delivery zones (only approved zones), pause / resume. Staff: pause, suspend, restore with a reason.
6. **Responses.** A paused or suspended kitchen disappears from discovery and cannot be quoted (E2E-DISC-005, E2E-DISC-104); suspension stops new sales and acceptance of pending orders but already accepted orders can be fulfilled (E2E-COOK-050).
7. **API.** `PATCH /api/v1/cook/settings` (API-065), `POST /api/v1/ops/kitchens/{kitchenId}/operating-state` (API-070).
8. **Backend.** `CookSettingsController.java` → `KitchenService.updateSettings`; `CookReviewController.java` → `KitchenService.changeOperatingState` (+ `KitchenOrderImpact` shows active-order impact).
9. **DB.** `kitchen`, `kitchen_zone` (V5).
10. **Transitions.** `ACTIVE → PAUSED` (by cook or staff), `PAUSED | ACTIVE → SUSPENDED`, `PAUSED | SUSPENDED → ACTIVE` (staff RESTORE); a cook may resume only a cook-made pause; `CLOSED` is hidden from public profile queries.
11. **Errors.** `STATE_CONFLICT` "kitchen is PAUSED by staff"; `VERSION_CONFLICT`; settings refuse making the kitchen unserviceable (CHECK `cook_revision_serviceable_ck` in V5 for applications, E2E-COOK-112 for settings).
12. **Tests.** `backend/app/src/test/java/com/matbakhy/app/cooks/CookWorkspaceIntegrationTest.java`, `CookWorkflowIntegrationTest.java`, `apps/operations-web/src/App.test.tsx`.
13. **Owner E2E.** E2E-COOK-050, E2E-COOK-107, E2E-COOK-112, E2E-DISC-005, E2E-DISC-104.
14. **Status.** IMPLEMENTED.
15. **Limitations.** There is no UI to set the kitchen `CLOSED` state (no API action maps to it: `KitchenService.Action` is PAUSE, SUSPEND, RESTORE).

## Known limitations and documentation gaps

* `docs/product/06-permissions.md` lists "Support", "Ops", "Finance", "Admin" as roles; in code only **permission grants** exist (`Permission` enum in `backend/modules/identity/src/main/java/com/matbakhy/identity/api/Permission.java`). It also names `finance.settlement_verify`; the code permission is `finance.settlement`. Trust the code.
* The cook application has no user interface (see CO-1). Existing docs describing an onboarding screen describe a plan.
* A cook who also holds staff grants has separation-of-duties rules enforced for own application, own kitchen and own payment account (E2E-OPS-108); the exact enforcement points were not individually traced here (NOT VERIFIED beyond `CookReviewService.refuseSelfReview`).
