# User journeys: customer web

> **Owner role:** Frontend and mobile
> **Status:** IMPLEMENTED — the customer web app (`apps/customer-web`) covers browsing, sign-in, basket, quote, placement, tracking, account and MFA preference; cook application and payment-by-card are not part of it.
> **Language / اللغة:** English · [العربية](customer-web-ar.md)

This page documents only the screens and routes that exist in `apps/customer-web/src`. The app is a single-page React app with **hash routing** (`window.location.hash`, see `apps/customer-web/src/App.tsx`). Cross-cutting facts are in [Frontend architecture](../03-architecture/frontend-architecture.md), [Identity and security](../03-architecture/identity-and-security.md) and [API contracts](../04-codebase/api-contracts.md). The same journeys on a phone are in [Customer mobile](customer-mobile.md); the whole chain is in [Complete order journey](complete-order-journey.md).

## Routes that exist

| Hash route | Component | Needs session | Notes |
|---|---|---|---|
| `#/` | `Browse` (`apps/customer-web/src/Browse.tsx`) | No | Home: intent tiles, search, filters, dishes/kitchens switch |
| `#/meal/<uuid>` | `Meal` (`apps/customer-web/src/Meal.tsx`) | No to view; yes for favourites and ordering | Slot choice, quantity, add to basket, reviews |
| `#/kitchen/<slug>` | `Kitchen` (`apps/customer-web/src/Kitchen.tsx`) | No | Public kitchen profile and its meals |
| `#/checkout` | `Checkout` (`apps/customer-web/src/Checkout.tsx`) | Yes to quote/place | Basket (labelled "cart" in the top bar), phone verification, address, quote, placement |
| `#/orders` | `OrderList` (`apps/customer-web/src/Orders.tsx`) | Yes | Own orders |
| `#/orders/<uuid>` | `OrderDetail` | Yes | Timeline, handover code, cancel, review, payment status |
| `#/account` | `Account` (`apps/customer-web/src/Account.tsx`) | Yes (shows a sign-in link otherwise) | Profile, favourites, notifications, support, addresses, security, deletion |
| `#/support/<uuid>` | `SupportCaseDetail` | Yes | Own support case conversation |

The bottom navigation shows Browse always and Orders/Account only when `GET /api/v1/me` returned a profile. There is **no** route for applying as a cook, no card payment screen, and no staff screen in this app (`NOT PRESENT` in code; verified by searching `apps/customer-web/src` for `cook/application`).

Common to every journey below:

* **Actor / permissions.** Guest (no session) or Customer. The API grants every active account the `ACCOUNT_ACTIVE` authority; customer endpoints additionally check object ownership in the service layer (404 for another customer's object). No staff permission is involved. See [Actors and permissions](../01-business/actors-and-permissions.md).
* **Transport.** Browser session cookie `MATBAKHY_SESSION` (HttpOnly, SameSite=Lax) plus CSRF header `X-XSRF-TOKEN` on every non-GET (`apps/customer-web/src/api.ts`, `backend/app/src/main/java/com/matbakhy/app/security/SecurityConfiguration.java`). Every request carries `Accept-Language` from the language toggle.
* **Typed client.** `createApi` wraps `createApiClient` from `packages/api-client/src/index.ts`, generated from `contracts/openapi.yaml`.
* **Existing tests (all journeys).** Component tests with a faked `fetch`: `apps/customer-web/src/App.test.tsx` (describe blocks: customer web, basket rules, MBQA-002 exhausted slot, MBQA-001 language, CW05 basket editing, Q1 quantity limits, MBQA-003 sign-out, MBQA-004 support case, OBS-001 recovery lookup, D13 changed prices, MB-053 deposit checkout, MB-054 activity, demo notice, account security D18). Real-API browser tests live in the operations-web project: `apps/operations-web/e2e/customer-journey.e2e.ts` (delivery and pickup orders followed by cook actions), `apps/operations-web/e2e/keyboard.e2e.ts`; run through `scripts/dev.sh e2e`. Test status of the current commit: NOT VERIFIED here (not executed while writing this page).

## CW-1 Guest discovery, search and filtering

1. **Business goal.** Let anyone see what home kitchens offer before creating an account (BR-018 basket rules start only at "add").
2. **Actor / permissions.** Guest or Customer; public endpoints (`PUBLIC_GET` in `SecurityConfiguration`).
3. **Prerequisites.** At least one approved, active kitchen with a published, visible meal to be listed. Without an eligible open slot the meal can still be listed but is marked `UNAVAILABLE` and cannot be ordered; only a date or slot filter requires an orderable slot to match (`DiscoveryService`, `MealOffers`).
4. **Starting screen.** `#/` (`Browse`).
5. **User actions.** Open the app; optionally pick an intent tile (self / complement / gathering) which reveals a budget filter; type in the search box; choose a category chip; pick zone; change sort (`NEWEST`, `PRICE_ASC`, `PRICE_DESC`); clear filters; switch between "dishes" and "kitchens"; open a meal or a kitchen.
6. **Expected responses.** Stacked food cards update from the server result; price shown in EGP formatted with `Intl.NumberFormat` (`formatMoney` in `apps/customer-web/src/api.ts`); empty and error states come from `Status` in `apps/customer-web/src/shared.tsx`; an offline banner appears when `navigator.onLine` is false. Filters are server query parameters; the client never filters, ranks or prices (comment in `Browse.tsx`). Portion-size filters are deferred (decision D11).
7. **API.** `GET /api/v1/zones` (API-002), `GET /api/v1/meals` with `q, zoneId, categoryId, maxPriceMinor, sort, limit` (API-003), `GET /api/v1/meals/{mealId}` (API-004), `GET /api/v1/kitchens/{slug}` (API-005), `GET /api/v1/kitchens/{kitchenId}/slots` (API-006, used by the meal page), `GET /api/v1/configuration/public` (API-001), `POST /api/v1/activity` (API-110, anonymous impression counting, fire-and-forget).
8. **Backend.** `backend/modules/catalog/src/main/java/com/matbakhy/catalog/web/DiscoveryController.java` → `backend/modules/catalog/src/main/java/com/matbakhy/catalog/internal/discovery/DiscoveryService.java`; meal detail `MealController.java` / `MealService.java`; kitchen `backend/modules/cooks/src/main/java/com/matbakhy/cooks/web/KitchenPublicController.java`; slots `backend/modules/availability/src/main/java/com/matbakhy/availability/web/SlotController.java`; activity `backend/modules/catalog/src/main/java/com/matbakhy/catalog/web/ActivityController.java`.
9. **DB.** `meal`, `meal_revision`, `meal_media`, `media_asset` (V6, V7), `meal_slot`, `cook_slot` (V8), `kitchen`, `kitchen_zone` (V5), `service_zone` (V3), `activity_daily` (V26), `review` (V20). Migrations: `backend/app/src/main/resources/db/migration`.
10. **State transitions.** None for the customer. Visibility depends on meal `status = PUBLISHED`, `moderation_state = VISIBLE` (V16), kitchen `operating_state = ACTIVE` and slot `state = OPEN`.
11. **Errors / boundaries.** Cursor limit out of range is rejected (E2E-DISC-103); paused kitchens, draft meals and suspended kitchens are not listed (E2E-DISC-005, E2E-DISC-104); network failure shows the generic error with a retry button.
12. **Tests.** `backend/app/src/test/java/com/matbakhy/app/catalog/DiscoveryIntegrationTest.java`, `backend/app/src/test/java/com/matbakhy/app/catalog/MealIntegrationTest.java`, `backend/app/src/test/java/com/matbakhy/app/insights/ActivityInsightsIntegrationTest.java`, web `apps/customer-web/src/App.test.tsx`, `apps/operations-web/e2e/customer-journey.e2e.ts`.
13. **Owner E2E.** E2E-DISC-001, E2E-DISC-006, E2E-DISC-101, E2E-DISC-102, E2E-DISC-103, E2E-DISC-008, E2E-DISC-112, E2E-DISC-113, E2E-DISC-117; core suite J0 (see [Complete order journey](complete-order-journey.md#owner-acceptance-mapping)).
14. **Status.** IMPLEMENTED.
15. **Limitations.** No pagination UI: the web asks `limit: 50` once (`Browse.tsx`), so more than 50 matches are not reachable from the web (NOT VERIFIED whether a "more" control exists elsewhere; none found in `Browse.tsx`). Intent tiles only reveal a budget filter, they do not change the query otherwise.

## CW-2 Sign-in, sign-out and account switch

1. **Goal.** Authenticate through the identity provider (Keycloak) and end the session cleanly (BR-048).
2. **Actor.** Guest becoming Customer.
3. **Prerequisites.** A Keycloak account in realm `matbakhy` (self-registration is disabled in both realm files: `registrationAllowed: false`; E2E-DISC-002 covers the owner-facing registration decision). Details: [Authentication and MFA](authentication-and-mfa.md).
4. **Start.** Any screen, "Sign in" in the top bar.
5. **Actions.** Press Sign in → browser navigates to `/oauth2/authorization/keycloak?returnTo=<current URL>` on the API origin → Keycloak login (password; no OTP unless the MFA policy requires) → callback `/login/oauth2/code/keycloak` → redirected back to the validated `returnTo`. Sign out: press Sign out; if an unconfirmed order key is stored in this browser a dialog warns first (`signOutPending`).
6. **Responses.** After return, `GET /api/v1/me` fills the profile and the bottom nav shows Orders and Account. Sign-out calls the API logout, clears basket and pending-order keys from `localStorage`, then navigates to the identity provider's end-session URL returned in `idpLogoutUrl` so the next sign-in asks for credentials. If the server cannot be reached, nothing is cleared and the customer stays signed in (`signOutState = "failed"`).
7. **API.** `GET /api/v1/me` (API-009); `POST /api/v1/auth/logout` (Spring Security logout handler configured in `SecurityConfiguration`, framework-managed and **not** in `contracts/endpoints.json` — the client calls it with a cast, see `App.tsx`); `GET /oauth2/authorization/keycloak`, `GET /login/oauth2/code/keycloak` (framework routes).
8. **Backend.** `SecurityConfiguration` (webChain), `WebReturnTo`, `AccountMapper`, `WebAccountFilter` under `backend/app/src/main/java/com/matbakhy/app/security/`; `backend/modules/identity/src/main/java/com/matbakhy/identity/web/ProfileController.java`.
9. **DB.** `app_user` (V1), `SPRING_SESSION`, `SPRING_SESSION_ATTRIBUTES` (V2).
10. **Transitions.** Session: none → active → invalidated. Account status `ACTIVE` / `DELETION_REQUESTED` (see CW-6).
11. **Errors.** Hostile `returnTo` or `redirect_uri` is refused (CFG-003); expired/deleted session returns 401 and the UI falls back to signed-out (CFG-004); a page restored from the back-forward cache after sign-out is reloaded (`pageshow` handler, MBQA-003).
12. **Tests.** `apps/customer-web/src/App.test.tsx` (MBQA-003 sign-out block), `backend/app/src/test/java/com/matbakhy/app/identity/IdentityIntegrationTest.java`, `backend/app/src/test/java/com/matbakhy/app/security/PreviewSecurityConfigurationTest.java`.
13. **Owner E2E.** CFG-002, CFG-003, CFG-004, CFG-006, E2E-DISC-003, E2E-DISC-004; core suite J0.
14. **Status.** IMPLEMENTED.
15. **Limitations.** The 30-minute default session timeout (`WEB_SESSION_TIMEOUT` in `backend/app/src/main/resources/application.yaml`) has no on-screen warning. Application-level login throttling does not exist; only Keycloak brute-force protection (CFG-039).

## CW-3 Meal selection and basket

1. **Goal.** Build a single-kitchen, single-slot basket (BR-018).
2. **Actor.** Guest or Customer (the basket needs no session).
3. **Prerequisites.** A meal with an open slot that still has portions.
4. **Start.** `#/meal/<uuid>`.
5. **Actions.** Choose a slot, set a quantity (bounded by the smaller of the server's published per-line limit and the slot's remaining portions; fallback 20, `apps/customer-web/src/cart.ts`), press add. Adding a meal from another kitchen or slot opens a replacement prompt: keep or replace. Edit quantities or remove lines at `#/checkout`.
6. **Responses.** Basket persists in `localStorage` key `matbakhy.cart`; an estimate is computed from the unit price seen when added with integer piastres (`estimateMinor`), explicitly not the price. Favourites toggle needs a session.
7. **API.** `GET /api/v1/meals/{mealId}`, `GET /api/v1/kitchens/{kitchenId}/slots`, `GET /api/v1/configuration/public` (limit, re-read when the meal or cart route opens), `PUT/DELETE /api/v1/me/favorites/{mealId}` (API-019/020), `GET /api/v1/me/favorites` (API-018), `GET /api/v1/kitchens/{kitchenId}/reviews` (API-007), `POST /api/v1/reviews/{reviewId}/reports` (API-030).
8. **Backend.** `PublicConfigurationController.java`, `CommercePolicies.java` (`backend/modules/orders/src/main/java/com/matbakhy/orders/`), `FavoriteController.java` / `FavoriteService.java` (`backend/modules/catalog/...`), `ReviewController.java` / `ReviewService.java`.
9. **DB.** `commerce_policy` (V10), `favorite`, `review`, `review_report` (V20).
10. **Transitions.** None server-side; the basket is client-only. One kitchen, one slot enforced both in the UI and again at quote time (E2E-BASKET-021).
11. **Errors.** Exhausted slot cannot be added (MBQA-002, E2E-BASKET-012); a line above a lowered limit blocks the quote until reduced (E2E-BASKET-024); storage unavailable (private mode) keeps the flow working within the page (`cart.ts`).
12. **Tests.** `apps/customer-web/src/App.test.tsx` (basket rules, CW05, Q1 blocks), `backend/app/src/test/java/com/matbakhy/app/orders/ReviewsAndFavoritesIntegrationTest.java`, `backend/app/src/test/java/com/matbakhy/app/orders/PublicConfigurationIntegrationTest.java`.
13. **Owner E2E.** E2E-BASKET-009, E2E-BASKET-010, E2E-BASKET-011, E2E-BASKET-012, E2E-BASKET-030, E2E-DISC-107; J1, J5.
14. **Status.** IMPLEMENTED.
15. **Limitations.** The basket is per browser, not synchronised with the mobile app or other devices.

## CW-4 Quote, phone verification and order placement

1. **Goal.** Turn the basket into one server-priced order exactly once (BR-019, BR-023).
2. **Actor.** Customer with a verified Egyptian phone.
3. **Prerequisites.** Signed in; phone verified (`CONTACT_VERIFICATION_REQUIRED` otherwise); for delivery a saved address in a zone the kitchen serves; accepted terms and cancellation policy versions.
4. **Start.** `#/checkout`.
5. **Actions.** (a) If signed out, a sign-in link keeps the basket. (b) Verify phone: enter `+20…` number, request a code, enter the code. (c) Choose pickup or delivery (only modes the basket's kitchen offers), pick or add an address, choose payment method COD or DEPOSIT. (d) "Calculate total" → quote. (e) Tick acceptance → "Place order". (f) If a previous placement was interrupted, "Retry order".
6. **Responses.** The quote panel shows lines, delivery fee, total, acceptance deadline preview and `PaymentTerms` (`apps/customer-web/src/Payment.tsx`): for DEPOSIT it shows the deposit, remainder and the kitchen's approved recipient with a plain statement that Matbakhy does not hold the money; no commission or service fee line exists. On success the app navigates to `#/orders/<id>`. If the server returns `PRICE_CHANGED`, nothing is ordered; the app re-quotes and shows old versus new totals (and attributes a delivery-fee-only change to the fee) and requires explicit acceptance (decision D13, `packages/api-client/src/priceChanges.ts`).
7. **API.** `POST /api/v1/me/phone/challenges` (API-011), `POST /api/v1/me/phone/challenges/{challengeId}/verify` (API-012), `GET/POST /api/v1/me/addresses` (API-013/014), `GET /api/v1/zones`, `POST /api/v1/quotes` (API-021), `POST /api/v1/orders` with header `Idempotency-Key` and body `{quoteId, clientRequestId, acceptedPolicyVersions}` (API-022), `GET /api/v1/orders/by-request/{clientRequestId}` (API-024, recovery after a lost response).
8. **Backend.** `QuoteController.java` → `QuoteService.java`; `OrderController.java` → `OrderService.place`; `PaymentService.java` (terms and initial payment row); identity `ContactController.java`, `PhoneVerificationService.java`, `AddressService.java`. All under `backend/modules/orders/src/main/java/com/matbakhy/orders/` and `backend/modules/identity/src/main/java/com/matbakhy/identity/`. Idempotency helper in the shared module (`idempotency_record` table).
9. **DB.** `quote`, `quote_item` (V10), `customer_order`, `order_item`, `stock_allocation`, `order_event`, `outbox_event`, `cash_obligation` (V11), `order_payment`, `payment_event` (V24), `phone_challenge`, `address` (V3), `idempotency_record` (V1), request fingerprint (V29).
10. **Transitions.** Quote (5-minute validity, immutable, reserves nothing, E2E-BASKET-029/013) → order `PENDING_ACCEPTANCE` with stock allocation `RESERVED`; deposit orders start payment state `WAITING_FOR_DEPOSIT`, COD `NOT_REQUIRED`. Full machine: `docs/product/04-state-machines.md` and [Order lifecycle](../01-business/order-lifecycle.md).
11. **Errors.** `QUOTE_EXPIRED`, `PRICE_CHANGED`, `STOCK_CHANGED`, `SLOT_CLOSED`, `KITCHEN_UNAVAILABLE`, `IDEMPOTENCY_CONFLICT`, `STATE_CONFLICT` with `QUOTE_ALREADY_USED` (codes in `backend/shared/src/main/java/com/matbakhy/shared/error/ErrorCode.java`; localised texts in `apps/customer-web/src/i18n.ts`). A 5xx or network error after sending keeps `matbakhy.pendingPlacement` (same `clientRequestId` and `Idempotency-Key`) so a retry cannot duplicate; a definitive 4xx clears it. Changing mode, address or payment method discards the shown quote (E2E-BASKET-019).
12. **Tests.** Backend: `backend/app/src/test/java/com/matbakhy/app/orders/QuoteIntegrationTest.java`, `OrderPlacementIntegrationTest.java`, `PriceChangeIntegrationTest.java`, `backend/app/src/test/java/com/matbakhy/app/payments/DepositPaymentIntegrationTest.java`, `backend/app/src/test/java/com/matbakhy/app/identity/ContactIntegrationTest.java`. Web: `apps/customer-web/src/App.test.tsx` (D13, OBS-001, MB-053). Browser: `apps/operations-web/e2e/customer-journey.e2e.ts`.
13. **Owner E2E.** E2E-DISC-105, E2E-DISC-106, E2E-DISC-007, E2E-BASKET-013, E2E-BASKET-014, E2E-BASKET-015, E2E-BASKET-019, E2E-BASKET-020, E2E-BASKET-028, E2E-ORDER-021, E2E-ORDER-022, E2E-ORDER-024, E2E-ORDER-123, E2E-FIN-103, E2E-FIN-104, E2E-FIN-105; J1 to J5.
14. **Status.** IMPLEMENTED. Real money is gated: the deposit is a manual transfer outside Matbakhy; the app never claims bank verification.
15. **Limitations.** The cancellation-policy and terms version strings come from the quote response; there is no separate policy page (`GET /api/v1/policies/{key}` API-008 is marked not implemented in `contracts/endpoints.json`).

## CW-5 Order tracking, payment report, cancellation and review

1. **Goal.** Follow an order and act on it where the server allows.
2. **Actor.** Order owner (Customer).
3. **Prerequisites.** A placed order.
4. **Start.** `#/orders` then `#/orders/<uuid>`.
5. **Actions.** Read the timeline; for a deposit order press "I sent the deposit" and optionally give a reference; cancel while `PENDING_ACCEPTANCE`; after acceptance ask for cancellation; show the handover code at pickup or delivery; review a completed order; open "order again"; open a support case from the account page.
6. **Responses.** The detail view re-reads every 10 seconds (`window.setInterval(reload, 10_000)` in `Orders.tsx`) — there is no push. Buttons are rendered only if the server lists them in `allowedActions` (`CANCEL`, `REQUEST_CANCELLATION`). The handover code is shown only when the server returns it (state `READY` or `OUT_FOR_DELIVERY`, owner only). Payment text says the kitchen still has to confirm receipt; nothing says "verified by bank".
7. **API.** `GET /api/v1/orders` (API-023), `GET /api/v1/orders/{orderId}` (API-025), `POST /api/v1/orders/{orderId}/cancel` (API-027), `POST /api/v1/orders/{orderId}/cancellation-requests` (API-028), `POST /api/v1/orders/{orderId}/payment/transfer-report` (API-100), `POST /api/v1/orders/{orderId}/reviews` (API-029). `GET /api/v1/orders/{orderId}/events` (API-026) exists in the contract; the web reads events embedded in the order view (NOT VERIFIED whether the web calls API-026 — no call found).
8. **Backend.** `OrderController.java`, `OrderLifecycleService.customerCancel`, `OrderService.get/list`, `PaymentController.java` / `PaymentService.reportTransfer`, `ReviewController.java` / `ReviewService.java`, `backend/modules/support/src/main/java/com/matbakhy/support/web/SupportController.java` (cancellation request creates a support case).
9. **DB.** `customer_order`, `order_event`, `handover_challenge`, `handover_attempt` (V12), `order_payment`, `payment_event`, `review` (V20), `support_case` (V17), `notification` (V14).
10. **Transitions.** `PENDING_ACCEPTANCE → CANCELLED` (customer), `→ EXPIRED` (system at the acceptance deadline via `OrderExpiryJob`), later states are cook/staff driven. Payment `WAITING_FOR_DEPOSIT → CUSTOMER_TRANSFER_REPORTED`. Terminal order states: `COMPLETED, REJECTED, EXPIRED, CANCELLED, DELIVERY_FAILED` (`OrderLifecycleService.TERMINAL`).
11. **Errors.** Another customer's order id returns 404 (E2E-ORDER-120); a stale `expectedVersion` returns `VERSION_CONFLICT`; the cancel button disappears if the order moved on (the UI treats `STATE_CONFLICT` as "already done"); repeating the transfer report is idempotent and informational (E2E-FIN-042); a second review gives 409 (E2E-DISC-110).
12. **Tests.** `backend/app/src/test/java/com/matbakhy/app/orders/OrderLifecycleIntegrationTest.java`, `backend/app/src/test/java/com/matbakhy/app/payments/DepositPaymentIntegrationTest.java`, `backend/app/src/test/java/com/matbakhy/app/orders/ReviewsAndFavoritesIntegrationTest.java`, `apps/customer-web/src/App.test.tsx`.
13. **Owner E2E.** E2E-ORDER-027, E2E-ORDER-030, E2E-ORDER-031, E2E-ORDER-033, E2E-ORDER-035, E2E-ORDER-117, E2E-ORDER-120, E2E-FIN-039, E2E-FIN-042, E2E-DISC-110; J1 to J4.
14. **Status.** IMPLEMENTED.
15. **Limitations.** Polling every 10 s means the customer may see a state up to ~10 s late. In-app notifications are listed in the account page; no email/SMS/push is delivered by the app (provider decision pending — see [Integration and events](../03-architecture/integration-and-events.md)).

## CW-6 Account: profile, addresses, favourites, notifications, support, deletion

1. **Goal.** Manage personal data and contact support.
2. **Actor.** Customer.
3. **Prerequisites.** Signed in.
4. **Start.** `#/account`.
5. **Actions.** Edit display name and language (sends `expectedVersion`); add/delete addresses; remove favourites; mark notifications read; open a support case about an order (categories `LATE, MISSING_ITEM, QUALITY, FOOD_SAFETY, CASH, HANDOVER, OTHER`) and reply at `#/support/<uuid>`; request account deletion.
6. **Responses.** Each section loads independently with its own error and retry. Deletion disables the account immediately and is idempotent; the UI then signs out.
7. **API.** `PATCH /api/v1/me` (API-010), `GET/POST/PATCH/DELETE /api/v1/me/addresses…` (API-013 to API-016), `GET /api/v1/me/favorites`, `GET /api/v1/notifications` and `PUT /api/v1/notifications/{id}/read` (API-031/032), `GET/POST /api/v1/support/cases` and `GET/POST …/{caseId}[/messages]` (API-035 to API-038), `POST /api/v1/me/deletion-requests` (API-017).
8. **Backend.** `ProfileController.java`, `ContactController.java`, `AccountLifecycleService.java`, `AccountErasureJob.java` (identity), `NotificationController.java`, `SupportController.java` / `SupportService.java`.
9. **DB.** `app_user`, `address`, `deletion_request` (V3, V21), `favorite`, `notification`, `support_case`, `case_message` (V17).
10. **Transitions.** Account `ACTIVE → DELETION_REQUESTED → erased`; support case `OPEN → IN_PROGRESS / WAITING_ON_CUSTOMER → RESOLVED → CLOSED`, and a customer reply reopens a resolved case (E2E-OPS-105).
11. **Errors.** Profile `VERSION_CONFLICT` (E2E-DISC-109); privileged fields in the profile body are rejected; addresses of another customer are not visible (E2E-DISC-106).
12. **Tests.** `backend/app/src/test/java/com/matbakhy/app/privacy/AccountErasureIntegrationTest.java`, `backend/app/src/test/java/com/matbakhy/app/support/cases/SupportIntegrationTest.java`, `backend/app/src/test/java/com/matbakhy/app/notifications/NotificationIntegrationTest.java`, `apps/customer-web/src/App.test.tsx` (MBQA-004).
13. **Owner E2E.** E2E-DISC-106, E2E-DISC-107, E2E-DISC-108, E2E-DISC-109, E2E-DISC-114, E2E-ORDER-118.
14. **Status.** IMPLEMENTED. The deletion request sends the hard-coded policy version string `retention-policy-draft` (see `Account.tsx`), i.e. the retention policy is still a draft.
15. **Limitations.** Final legal retention text is not approved (see `docs/operations/04-privacy-and-recovery.md`).

## CW-7 Two-step verification preference (MFA) on the web

1. **Goal.** Let a customer turn optional MFA on or off under the administrator's policy.
2. **Actor.** Customer (any category policy value).
3. **Prerequisites.** Signed in; policy for the CUSTOMER category not DISABLED to change anything.
4. **Start.** `#/account` → security section (`apps/customer-web/src/Security.tsx`).
5. **Actions.** Read policy mode and status; if OPTIONAL and off: "turn on" (needs a session with a valid MFA assertion, otherwise a link to a step-up sign-in); if on: "turn off" (needs recent MFA, ends the user's sessions); "manage authenticator" opens Keycloak's account console link from the API.
6. **Responses.** Panel shows effective mode (`OPTIONAL`/`REQUIRED`/`DISABLED`), whether MFA is required, whether this session is verified. `STEP_UP_REQUIRED` shows a "verify" button linking to `/oauth2/authorization/keycloak?stepUp=true&returnTo=…`. Under `REQUIRED`, a notice says it cannot be turned off.
7. **API.** `GET /api/v1/me/security` (API-112), `PUT /api/v1/me/security/mfa` body `{enabled}` (API-113).
8. **Backend.** `backend/app/src/main/java/com/matbakhy/app/mfa/MfaController.java`, `MfaPolicyService.java`, `MfaEnforcementFilter.java` (`backend/app/src/main/java/com/matbakhy/app/security/`).
9. **DB.** `mfa_policy`, `user_mfa_preference`, `user_mfa_reset` (V31).
10. **Transitions.** Preference off ↔ on; enforcement decision recomputed from the database on every request.
11. **Errors.** `MFA_REQUIRED_BY_POLICY`, `MFA_UNAVAILABLE` (turning on while DISABLED), `STEP_UP_REQUIRED` (HTTP 403). Full policy: [Authentication and MFA](authentication-and-mfa.md).
12. **Tests.** `backend/app/src/test/java/com/matbakhy/app/mfa/MfaPolicyIntegrationTest.java`, `MfaPolicyResolveTest.java`, `backend/app/src/test/java/com/matbakhy/app/identity/MfaIdpTest.java` (real Keycloak), `apps/customer-web/src/App.test.tsx` ("account security (D18)").
13. **Owner E2E.** CFG-102, CFG-008, CFG-011, CFG-012; core suite J7.
14. **Status.** IMPLEMENTED in code; live Keycloak realm migration for step-up is an owner-gated deployment step (CFG-028). Whether the deployed preview currently has it applied: NOT VERIFIED.
15. **Limitations.** TOTP only; no recovery codes (docs/security/mfa-policy.md).

## CW-8 Errors and recovery (cross-cutting)

| Situation | Behaviour (source) |
|---|---|
| Offline | Banner `t.offline`; `online` state from `navigator.onLine` events (`App.tsx`) |
| Lost response after "Place order" | `matbakhy.pendingPlacement` retained; on `#/checkout` the app asks `GET /orders/by-request/{id}`; if found it clears the basket and opens the order; lookup failure keeps the "unconfirmed" screen with a retry (OBS-001) |
| 401 on a protected call | `onSignedOut` clears the profile; sign-in link appears (`shared.tsx`) |
| 403 `STEP_UP_REQUIRED` | Message pointing to the account page (`i18n.ts`, `shared.tsx`) |
| Stale version | `VERSION_CONFLICT` message with reload |
| Demo/preview environment | Persistent notice from `GET /api/v1/configuration/public` `environment.demo` or build flag (`EnvironmentNotice.tsx`; E2E-DISC-117) |

## Known limitations and documentation gaps

* `docs/product/05-screen-specification.md` describes planned screens; this page lists only what `apps/customer-web/src` renders. Where they differ, trust the code.
* `contracts/endpoints.json` lists `POST /api/v1/auth/logout` nowhere; it is a framework route (note in the file's header). Treat `SecurityConfiguration.java` as the source.
* No Arabic/English parity test for all strings was found beyond the tests listed; parity of `apps/customer-web/src/i18n.ts` keys: NOT VERIFIED.
