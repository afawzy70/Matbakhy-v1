# Requirements traceability matrix

> **Owner role:** Testing and QA
> **Status:** PARTIALLY IMPLEMENTED — matrix for 13 critical paths with real file references; coverage gaps are marked GAP and listed at the end
> **Language / اللغة:** English · [العربية](requirements-traceability-ar.md)

Chain traced per row: **requirement** (`BR-nnn` in `planning/rules.json`, decision `Dnn` in `planning/decisions.json`) -> **user journey page** ->
**API operation** (id in `contracts/endpoints.json`) -> **implementation file** -> **DB entity / migration** -> **automated test** -> **owner E2E case**
(`docs/quality/owner-acceptance/cases/*.json`) -> **handbook page**.

Conventions used in the cells:

- Backend tests are written `<area>/<Class>`, meaning `backend/app/src/test/java/com/matbakhy/app/<area>/<Class>.java` (all existence-checked by `find`).
  Scenario ids (`TC-nnn`) are the ones written inside those files; `planning/scenarios.json` itself does not name test files.
- Frontend tests: `customer-web` = `apps/customer-web/src/App.test.tsx`, `operations-web` = `apps/operations-web/src/App.test.tsx`,
  `mobile` = `apps/customer-mobile/src/App.test.tsx` and its siblings, `e2e` = `apps/operations-web/e2e/*.e2e.ts`.
- Implementation paths are relative to `backend/modules/<module>/src/main/java/com/matbakhy/<module>/` unless a full path is given.
- Owner E2E cases are manual/hybrid cases whose results are NOT_RUN or BLOCKED in `docs/quality/owner-acceptance/generated/traceability.json`; citing one means "a case is
  defined", not "it passed". `GAP` = no automated test or no owner case was found for that cell.
- Journey pages: [authentication and MFA](../02-user-journeys/authentication-and-mfa.md), [customer web](../02-user-journeys/customer-web.md),
  [customer mobile](../02-user-journeys/customer-mobile.md), [cook operations](../02-user-journeys/cook-operations.md),
  [backoffice admin](../02-user-journeys/backoffice-admin.md), [finance and support](../02-user-journeys/finance-and-support.md),
  [complete order journey](../02-user-journeys/complete-order-journey.md).

## 1. Authentication

| Requirement | Journey | API operation | Implementation | DB | Automated test | Owner E2E | Handbook |
|---|---|---|---|---|---|---|---|
| BR-001 only authenticated active accounts do private operations (TC-001, TC-109) | authentication-and-mfa | API-009 `GET /api/v1/me` and every non-public route | `backend/app/src/main/java/com/matbakhy/app/security/SecurityConfiguration.java`, `AccountMapper.java`, `WebAccountFilter.java` | `app_user` (V1) | `identity/IdentityIntegrationTest`, `security/PreviewSecurityConfigurationTest` | CFG-002, CFG-004, E2E-DISC-003, E2E-OPS-062 | [identity and security](../03-architecture/identity-and-security.md) |
| BR-002 web CSRF + native PKCE (TC-002, TC-131) | authentication-and-mfa | OIDC routes (framework-managed, not in registry); API-010 `PATCH /api/v1/me` as a CSRF-protected mutation | `SecurityConfiguration.webChain`, `csrfTokenRepository`; `apps/customer-mobile/src/auth.ts` | web sessions (V2) | `identity/IdentityIntegrationTest`, `security/CsrfCookieDefaultIntegrationTest`, `security/CsrfCookieDomainIntegrationTest`; mobile `apps/customer-mobile/src/auth.test.ts`, `signIn.test.ts` | CFG-002, CFG-003, CFG-036, CFG-037 | [identity and security](../03-architecture/identity-and-security.md) |
| Session expiry and server-side revocation | authentication-and-mfa | API-009 | `spring.session.timeout` in `backend/app/src/main/resources/application.yaml`; Spring Session JDBC | web sessions (V2) | `identity/IdentityIntegrationTest` | CFG-004 | [identity and security](../03-architecture/identity-and-security.md) |
| BR-048 native tokens only in OS secure storage; logout clears private data (TC-048) | customer-mobile | n/a (client) | `apps/customer-mobile/src/tokenStore.ts`, `session.ts` | n/a | mobile `auth.test.ts`, `App.test.tsx`; native runs in `verification/runs/` | CFG-025, E2E-BASKET-027 | [mobile code guide](../04-codebase/mobile-code-guide.md) |
| Brute-force/rate limits at login (BR-054, TC-054) | authentication-and-mfa | n/a (Keycloak) | Keycloak `bruteForceProtected` in `infra/identity/matbakhy-realm.json` | n/a | GAP (TC-054 NOT_RUN; no test of the Keycloak lockout) | CFG-039 | [identity and security](../03-architecture/identity-and-security.md) |

## 2. Authorization (roles, permissions, object scope)

| Requirement | Journey | API operation | Implementation | DB | Automated test | Owner E2E | Handbook |
|---|---|---|---|---|---|---|---|
| BR-006 every object read checks scope; foreign objects answer 404 (TC-006) | complete-order-journey | API-025 `GET /api/v1/orders/{orderId}`, API-054, API-037 | `security/ScopedAccessPolicy.java`; `OrderService.requireOwned` in `backend/modules/orders/src/main/java/com/matbakhy/orders/internal/OrderService.java:297`; scoped SQL in services | all domain tables carry owner/kitchen keys | `identity/StaffAccessIntegrationTest` (kitchen scope matrix), `orders/OrderPlacementIntegrationTest` (own orders only, lookup by request), `support/cases/SupportIntegrationTest` | E2E-ORDER-120, CFG-005, E2E-OPS-055 | [actors and permissions](../01-business/actors-and-permissions.md) |
| BR-007 staff privileges server-side; roles from claims ignored (TC-007) | backoffice-admin | API-091 `GET /api/v1/ops/staff`; `@PreAuthorize` on every `/ops/**` route | `identity/api/Permission.java`, `identity/internal/GrantService.java`, `AccountMapper` | `user_grant` (V4) | `identity/StaffAccessIntegrationTest` (role fields in customer requests rejected; revoked grant stops on next request) | E2E-OPS-058, E2E-OPS-102, CFG-005 | [actors and permissions](../01-business/actors-and-permissions.md) |
| Permission vs contract (`x-permission`) | n/a | all | `contracts/openapi.yaml` `x-permission` | n/a | GAP: no test compares `x-permission` with the controller annotation | GAP | [API contracts](../04-codebase/api-contracts.md) |
| BR-043 no self-escalation, last administrator protected (TC-043) | backoffice-admin | API-092 `POST /api/v1/ops/staff/{userId}/grants`, API-093 | `GrantService` | `user_grant` (V4), `audit_event` | `identity/StaffAccessIntegrationTest.self_escalation_unknown_permissions_and_last_admin_are_refused` | E2E-OPS-060, E2E-OPS-101, CFG-105 | [backoffice admin journey](../02-user-journeys/backoffice-admin.md) |

## 3. Customer account separation

| Requirement | Journey | API operation | Implementation | DB | Automated test | Owner E2E | Handbook |
|---|---|---|---|---|---|---|---|
| One customer cannot read, cancel, pay-report or review another's order (BR-006, BR-001) | complete-order-journey | API-025, API-026, API-027, API-100, API-029 | `OrderService` (`customer_id = ?` scoping), `OrderLifecycleService.lock`, `PaymentService.lockForCustomer` | `customer_order` (V11) | `orders/OrderPlacementIntegrationTest.customers_see_only_their_own_orders_with_paging_and_events`, `...another_customer_cannot_find_an_order_by_its_client_request_id` | E2E-ORDER-120 | [order implementation](../04-codebase/order-implementation.md) |
| Web account switch leaves no trace (BR-044, BR-006) | customer-web | API-009 | `apps/customer-web/src/App.tsx` session handling, `cart.ts` | n/a | `customer-web` unit tests (CFG-006 lists them); `e2e` customer journey | CFG-006, E2E-DISC-004 | [frontend code guide](../04-codebase/frontend-code-guide.md) |
| Native account switch clears basket, pending order, tokens (BR-048) | customer-mobile | API-009 | `apps/customer-mobile/src/AppContext.tsx` (`accountEpoch`), `basket.ts` | n/a | `mobile` tests (TC-048, TC-049, TC-148); `apps/customer-mobile/src/ordering.test.ts` | E2E-BASKET-027, CFG-025 | [mobile code guide](../04-codebase/mobile-code-guide.md) |
| Account deletion request disables account at once (BR-055) | customer-web | API-017 `POST /api/v1/me/deletion-requests` | `identity/internal/AccountLifecycleService.java`, `AccountErasureService.java` | `deletion_request` (V3), V21, V27 | `privacy/AccountErasureIntegrationTest` | E2E-DISC-114, E2E-COOK-110 | [customer web journey](../02-user-journeys/customer-web.md) |

## 4. Kitchen approval

| Requirement | Journey | API operation | Implementation | DB | Automated test | Owner E2E | Handbook |
|---|---|---|---|---|---|---|---|
| BR-008 approval before selling; complete revision; reviewer + reason (TC-008, TC-112) | cook-operations / backoffice-admin | API-042..044 application, API-067 list, API-068 detail, API-069 `POST /api/v1/ops/cooks/{applicationId}/decision` | `backend/modules/cooks/src/main/java/com/matbakhy/cooks/internal/CookApplicationService.java`, `CookReviewService.java`, `web/CookReviewController.java` | `cook_application`, `cook_application_revision`, `kitchen` (V5) | `cooks/CookWorkflowIntegrationTest` (decision needs scoped reviewer + recent MFA; no self-review; idempotent decisions) | E2E-COOK-049, E2E-COOK-053 | [kitchen management](../01-business/kitchen-management.md) |
| BR-009 pause/suspend stops new sales; staff pause cannot be undone by the cook (TC-009) | cook-operations | API-065 `PATCH /api/v1/cook/settings`, API-070 `POST /api/v1/ops/kitchens/{kitchenId}/operating-state` | `cooks/internal/KitchenService.java` | `kitchen` (V5) | `cooks/CookWorkflowIntegrationTest`, `cooks/CookWorkspaceIntegrationTest` | E2E-COOK-107, E2E-OPS-108 | [kitchen management](../01-business/kitchen-management.md) |
| BR-017 only approved active kitchens' published meals are purchasable (TC-017) | customer-web | API-003, API-005, API-021 | `catalog/internal/discovery/DiscoveryService.java`, `QuoteService.terms` (`seller.canSell()`) | `meal`, `kitchen` | `catalog/DiscoveryIntegrationTest`, `orders/QuoteIntegrationTest` | E2E-DISC-005, E2E-DISC-104 | [kitchen management](../01-business/kitchen-management.md) |
| Operations UI for approval | backoffice-admin | API-067..069 | `apps/operations-web/src/ops/Reviews.tsx` | n/a | `operations-web` (`ops/cooks` requests); `e2e` | E2E-COOK-049 | [frontend code guide](../04-codebase/frontend-code-guide.md) |

## 5. Order placement

| Requirement | Journey | API operation | Implementation | DB | Automated test | Owner E2E | Handbook |
|---|---|---|---|---|---|---|---|
| BR-022 atomic placement: validate quote, reserve stock, write snapshots, events, outbox, obligations (TC-022, TC-118) | complete-order-journey | API-022 `POST /api/v1/orders` | `backend/modules/orders/src/main/java/com/matbakhy/orders/internal/OrderService.java:155` (`placeOnce`) | `customer_order`, `order_item`, `stock_allocation`, `order_event`, `outbox_event`, `cash_obligation` (V11) | `orders/OrderPlacementIntegrationTest` (atomicity, injected-failure rollback) | E2E-ORDER-021, E2E-BASKET-018 | [order implementation](../04-codebase/order-implementation.md) |
| BR-023 permanent `(customer, clientRequestId)`; fingerprint (TC-023) | complete-order-journey | API-022, API-024 `GET /api/v1/orders/by-request/{clientRequestId}` | `OrderService.fingerprint` (`:140`) | `request_fingerprint` (V29), unique `customer_order_request_uq` (V11) | `orders/OrderPlacementIntegrationTest`, unit `orders/internal/OrderFingerprintTest` (path `backend/app/src/test/java/com/matbakhy/orders/internal/OrderFingerprintTest.java`) | E2E-ORDER-105, E2E-ORDER-106, E2E-ORDER-123 | [order implementation](../04-codebase/order-implementation.md) |
| BR-024 idempotency scope actor + command + key (TC-024) | complete-order-journey | API-022 (`Idempotency-Key`) | `backend/app/src/main/java/com/matbakhy/app/idempotency/JdbcIdempotentCommands.java:49` | `idempotency_record` (V1) | `orders/OrderPlacementIntegrationTest`; no test of expired-record purge: GAP | E2E-ORDER-022, E2E-ORDER-023, E2E-BASKET-028 | [order implementation](../04-codebase/order-implementation.md) |
| BR-044 web stores keys before sending; unknown response resolves by lookup | customer-web / customer-mobile | API-022, API-024 | `apps/customer-web/src/Checkout.tsx:263`, `apps/customer-mobile/src/placement.ts` | n/a | `customer-web`, `mobile` tests (TC-044, `ordering.test.ts`); `e2e` | E2E-ORDER-122 (native pickup) | [frontend code guide](../04-codebase/frontend-code-guide.md) |

## 6. Pricing and stale quotes

| Requirement | Journey | API operation | Implementation | DB | Automated test | Owner E2E | Handbook |
|---|---|---|---|---|---|---|---|
| BR-019 server computes integer EGP; client prices rejected (TC-019) | customer-web | API-021 `POST /api/v1/quotes` | `orders/internal/QuoteService.java:92`, `:206` | `quote`, `quote_item` (V10) | `orders/QuoteIntegrationTest`, `web/StrictJsonIntegrationTest` | E2E-BASKET-020, E2E-ORDER-113 | [pricing implementation](../04-codebase/pricing-implementation.md) |
| BR-020 quote TTL, no stock hold, snapshots versions (TC-020) | customer-web | API-021, API-022 | `QuoteService.create`, `requireValid` (`:146`) | `commerce_policy` (V10) | `orders/QuoteIntegrationTest` | E2E-BASKET-013, E2E-BASKET-029, E2E-ORDER-111 | [pricing implementation](../04-codebase/pricing-implementation.md) |
| D13 stale quote refused; informed reconfirmation (TC-154, TC-155, TC-156) | customer-web / customer-mobile | API-022 -> 409 `PRICE_CHANGED`; API-021 re-quote | `QuoteService.requireValid`, `MealCatalog.lockPricing`, `packages/api-client/src/priceChanges.ts`, `apps/customer-web/src/Checkout.tsx:444` | `meal_revision` (V7) | `orders/PriceChangeIntegrationTest`; `packages/api-client/test/priceChanges.test.ts`; `customer-web`, `mobile` tests | E2E-BASKET-014, E2E-BASKET-015, E2E-BASKET-022, E2E-ORDER-112, E2E-BASKET-026 | [pricing implementation](../04-codebase/pricing-implementation.md) |
| BR-012 / D14 price history from immutable revisions (TC-153) | cook-operations | API-099 `GET /api/v1/cook/meals/{mealId}/price-history`, API-048 | `catalog/internal/meal/MealService.java:186` | `meal_revision` (V7) | `catalog/PriceHistoryIntegrationTest` | E2E-COOK-103 | [pricing implementation](../04-codebase/pricing-implementation.md) |
| D10 zero commission and no customer fee (TC-157) | all | API-021, API-089 | `OrderService.java:194`, V23 CHECK | `commerce_policy.commission_rate_bps` (V23) | `finance/ZeroCommissionIntegrationTest` | E2E-FIN-046, E2E-FIN-111 | [pricing and payments](../01-business/pricing-and-payments.md) |

## 7. Capacity reservation

| Requirement | Journey | API operation | Implementation | DB | Automated test | Owner E2E | Handbook |
|---|---|---|---|---|---|---|---|
| BR-013 capacity bounded at meal-slot and cook-slot (TC-013) | cook-operations | API-051 `POST /api/v1/cook/slots`, API-052 | `backend/modules/availability/src/main/java/com/matbakhy/availability/internal/SlotService.java` | `cook_slot`, `meal_slot` (V8) | `availability/SlotIntegrationTest` | E2E-COOK-106 | [kitchen management](../01-business/kitchen-management.md) |
| BR-025 concurrent last portion never oversells (TC-025) | complete-order-journey | API-022 | `availability/internal/JdbcSlotInventory.java:34` (`reserve`) | `cook_slot`, `meal_slot`, `stock_allocation` | `orders/OrderPlacementIntegrationTest.twenty_customers_racing_for_the_last_portion_produce_exactly_one_order`, `...a_two_meal_order_is_refused_without_partial_reservation_...` | E2E-BASKET-016, E2E-BASKET-017, E2E-ORDER-114 | [order implementation](../04-codebase/order-implementation.md) |
| BR-029 release exactly once; consumed stays consumed (TC-029) | complete-order-journey | API-027, API-056, API-062 | `OrderLifecycleService.release`, `move` | `stock_allocation` | `orders/OrderLifecycleIntegrationTest`, `reliability/RestartRecoveryIntegrationTest` (TC-058/137) | E2E-ORDER-027, E2E-ORDER-110, E2E-ORDER-028 | [order implementation](../04-codebase/order-implementation.md) |
| BR-014/015 Cairo scheduling, cutoff | cook-operations | API-051 | `availability/internal/CairoTime.java` | `cook_slot` (V8) | `availability/SlotIntegrationTest` | E2E-BASKET-023 | [kitchen management](../01-business/kitchen-management.md) |

## 8. Order cancellation

| Requirement | Journey | API operation | Implementation | DB | Automated test | Owner E2E | Handbook |
|---|---|---|---|---|---|---|---|
| BR-028 customer cancels only pending orders; later a request (TC-028) | complete-order-journey | API-027 `POST /api/v1/orders/{orderId}/cancel`, API-028 `POST .../cancellation-requests` | `OrderLifecycleService.customerCancel` (`:84`); `support/internal/SupportService.java` | `customer_order`, `support_case` (V17) | `orders/OrderLifecycleIntegrationTest`, `support/cases/SupportIntegrationTest` | E2E-ORDER-027, E2E-ORDER-035 | [order lifecycle](../01-business/order-lifecycle.md) |
| Cook cancels before preparation; reject; expiry (TC-027, TC-029) | cook-operations | API-056 reject, API-062 cancel | `OrderLifecycleService.cook` (`:104`), `expireOne` (`:237`), `OrderExpiryJob` | `customer_order` | `orders/OrderLifecycleIntegrationTest` | E2E-ORDER-110, E2E-ORDER-028 | [order lifecycle](../01-business/order-lifecycle.md) |
| Ops exception cancel after preparation (BR-028) | backoffice-admin | API-076 | `OrderLifecycleService.opsExceptionCancel` (`:188`) | `customer_order`, `audit_event` | `orders/OrderLifecycleIntegrationTest`; operations-web UI test for the action: GAP | E2E-ORDER-109 | [order lifecycle](../01-business/order-lifecycle.md) |
| Deposit consequences of cancellation | finance-and-support | API-105, API-107, API-108 | `PaymentService.orderClosed` | `order_payment` | `payments/DepositPaymentIntegrationTest` (TC-160), `payments/PaymentReconciliationIntegrationTest` | E2E-FIN-107, E2E-FIN-115 | [payment implementation](../04-codebase/payment-implementation.md) |

## 9. Pickup and delivery

| Requirement | Journey | API operation | Implementation | DB | Automated test | Owner E2E | Handbook |
|---|---|---|---|---|---|---|---|
| BR-021 delivery needs zone coverage and own address; pickup only if enabled (TC-021) | customer-web | API-021 | `QuoteService.terms` | `kitchen_zone`, `service_zone`, `address` (V3, V5) | `orders/QuoteIntegrationTest` | E2E-DISC-007, E2E-DISC-101 | [order lifecycle](../01-business/order-lifecycle.md) |
| BR-030 pickup completes from READY; delivery dispatches first (TC-030) | cook-operations | API-058 ready, API-059 dispatch, API-060 handover, API-061 delivery-failure | `OrderLifecycleService.cook` cases `dispatch`, `delivery_failure` | `customer_order` (V11, V12) | `orders/OrderLifecycleIntegrationTest` | E2E-ORDER-031, E2E-ORDER-032, E2E-ORDER-034, E2E-ORDER-101, E2E-ORDER-102 | [order lifecycle](../01-business/order-lifecycle.md) |
| BR-031 single-use handover code, bounded attempts, ops override with fresh MFA (TC-031) | cook-operations | API-060, API-077 | `backend/modules/orders/src/main/java/com/matbakhy/orders/internal/HandoverCodes.java`, `OrderLifecycleService.handover` (`:387`) | `handover_challenge`, `handover_attempt` (V12) | `orders/OrderLifecycleIntegrationTest` | E2E-ORDER-030, E2E-ORDER-033, E2E-ORDER-108 | [order lifecycle](../01-business/order-lifecycle.md) |
| Delivery on native apps | customer-mobile | API-021, API-022 | `apps/customer-mobile/src/screens/Cart.tsx` | n/a | `mobile` tests; native delivery fixture `apps/customer-mobile/e2e-native/delivery-address.sql` (native-run coverage NOT VERIFIED here) | GAP (only pickup is named: E2E-ORDER-122) | [mobile code guide](../04-codebase/mobile-code-guide.md) |

## 10. Deposits

| Requirement | Journey | API operation | Implementation | DB | Automated test | Owner E2E | Handbook |
|---|---|---|---|---|---|---|---|
| D16 deposit terms in quote and immutable payment snapshot (TC-158) | customer-web | API-021 (`paymentMethod`), API-102/103 profile | `PaymentService.terms` (`:205`), `createFor` (`:220`) | `kitchen_payment_profile`, `order_payment` (V24) | `payments/DepositPaymentIntegrationTest` | E2E-FIN-039, E2E-FIN-041, E2E-FIN-104, E2E-FIN-105 | [payment implementation](../04-codebase/payment-implementation.md) |
| Recipient must be approved by staff with recent MFA | finance-and-support | API-104 `POST /api/v1/ops/kitchens/{kitchenId}/payment-recipient/approval`, API-109 | `PaymentService.approveRecipient` (`:167`) | `kitchen_payment_profile.recipient_status` | `payments/DepositPaymentIntegrationTest` | E2E-COOK-108, E2E-FIN-103 | [payment implementation](../04-codebase/payment-implementation.md) |
| Customer transfer report is not payment; no preparation before kitchen receipt (TC-159) | customer-web | API-100, API-101, API-057 | `PaymentService.reportTransfer`, `kitchenReceipt`, `requireReadyForPreparation` | `order_payment`, `payment_event` (V24) | `payments/DepositPaymentIntegrationTest` | E2E-FIN-106 | [payment implementation](../04-codebase/payment-implementation.md) |
| Not received / mismatch; refund obligation (TC-161, TC-162) | finance-and-support | API-105..108 | `PaymentService.reportProblem`, `decide`, `reportRefund` | V25, V28 | `payments/PaymentReconciliationIntegrationTest` | E2E-FIN-107, E2E-FIN-108 | [payment implementation](../04-codebase/payment-implementation.md) |
| Honest payment wording on web and native (TC-163) | customer-web / customer-mobile | API-100 | `apps/customer-web/src/Payment.tsx`, `apps/customer-mobile/src/screens/Payment.tsx` | n/a | `customer-web`, `mobile` tests | E2E-FIN-113, E2E-FIN-101 | [customer web journey](../02-user-journeys/customer-web.md) |

## 11. Cash reconciliation

| Requirement | Journey | API operation | Implementation | DB | Automated test | Owner E2E | Handbook |
|---|---|---|---|---|---|---|---|
| BR-032 completion and collection independent; cook report vs finance verification (TC-032) | finance-and-support | API-063 `POST /api/v1/cook/orders/{orderId}/cash-reports`, API-082, API-083 | `backend/modules/finance/src/main/java/com/matbakhy/finance/internal/CashService.java:72`, `:125` | `cash_obligation` (V11), `cash_report` (V13) | `finance/CashAndLedgerIntegrationTest` | E2E-FIN-038, E2E-FIN-043, E2E-FIN-102, E2E-ORDER-101 | [payment implementation](../04-codebase/payment-implementation.md) |
| Separation of duties: reporter cannot verify own report | finance-and-support | API-083 | `CashService.refuseSelfVerification` (`:115`) | `cash_report` | `finance/CashAndLedgerIntegrationTest` | E2E-FIN-114 | [payment implementation](../04-codebase/payment-implementation.md) |
| BR-033/034 balanced append-only ledger posted once; commission receivable (TC-033, TC-034) | finance-and-support | API-086 statements | `finance/internal/LedgerService.java:31`, `LedgerPostingJob.java` | `ledger_group`, `ledger_entry` (V13) | `finance/CashAndLedgerIntegrationTest`, `finance/FinanceOperationsIntegrationTest` | E2E-FIN-045 | [payment implementation](../04-codebase/payment-implementation.md) |
| BR-035/036 refunds bounded by verified collection; settlements immutable (TC-035, TC-036) | finance-and-support | API-084, API-085, API-087, API-088 | `finance/internal/FinanceOpsService.java:67`, `:184` | `refund`, `settlement_batch`, `settlement_line` (V18) | `finance/FinanceOperationsIntegrationTest` | E2E-FIN-044 | [payment implementation](../04-codebase/payment-implementation.md) |
| Cook statement shows no commission (D10) | cook-operations | API-064 | `finance/internal/StatementService.java` | ledger tables | `finance/ZeroCommissionIntegrationTest` | E2E-FIN-111 | [pricing and payments](../01-business/pricing-and-payments.md) |
| Cash-report UI covered by a UI unit test | cook-operations | API-063 | `apps/operations-web/src/cook/Orders.tsx` | n/a | GAP: no `cash-reports` string in `apps/operations-web/src/App.test.tsx` (flow appears in `e2e/cook-workspace.e2e.ts`) | E2E-FIN-038 | [frontend code guide](../04-codebase/frontend-code-guide.md) |

## 12. MFA

| Requirement | Journey | API operation | Implementation | DB | Automated test | Owner E2E | Handbook |
|---|---|---|---|---|---|---|---|
| D18 / BR-007 policy per category: DISABLED, OPTIONAL, REQUIRED; most protective wins; missing row fails safe to REQUIRED | authentication-and-mfa | API-112 `GET /api/v1/me/security`, API-114, API-115 | `backend/app/src/main/java/com/matbakhy/app/mfa/MfaPolicyService.java` (`Mode`, resolve) | `mfa_policy`, `user_mfa_preference` (V31) | `mfa/MfaPolicyResolveTest`, `mfa/MfaPolicyIntegrationTest`; no `TC-nnn` exists for D18: GAP in `planning/scenarios.json` | CFG-101, CFG-011, CFG-012, CFG-014, CFG-015, CFG-033 | [identity and security](../03-architecture/identity-and-security.md) |
| Enforcement on protected calls; step-up with Keycloak | authentication-and-mfa | all protected routes; `STEP_UP_REQUIRED` | `security/MfaEnforcementFilter.java`, `security/StaffAssurance.java` | n/a | `identity/MfaIdpTest` (real Keycloak), `security/StaffAssuranceTest`; `customer-web` step-up link tests; `mobile` step-up tests | CFG-102, CFG-024, CFG-108, CFG-109 | [authentication and MFA journey](../02-user-journeys/authentication-and-mfa.md) |
| User preference (can add, never remove, a requirement) | authentication-and-mfa | API-113 `PUT /api/v1/me/security/mfa` | `mfa/MfaController.java` | `user_mfa_preference` (V31) | `mfa/MfaPolicyIntegrationTest` | CFG-008 | [authentication and MFA journey](../02-user-journeys/authentication-and-mfa.md) |
| Admin reset removes factor, ends sessions, audited | backoffice-admin | API-116 `POST /api/v1/ops/accounts/{accountId}/mfa-reset` | `mfa/MfaAdminController.java`, `mfa/KeycloakAdmin.java` | `user_mfa_reset` (V31), `audit_event` | `identity/MfaResetIntegrationTest`, `mfa/MfaResetAuditIntegrationTest` | CFG-018, CFG-019, CFG-020, CFG-021, CFG-023 | [authentication and MFA journey](../02-user-journeys/authentication-and-mfa.md) |
| Android step-up on a physical device | customer-mobile | API-113 | `apps/customer-mobile/src/auth.ts` (`acr_values`) | n/a | GAP (device tests not run; BLK-004) | CFG-112 (case text: no plan in repository yet) | [mobile code guide](../04-codebase/mobile-code-guide.md) |
| Realm migration for the MFA flow | infrastructure | n/a | `deploy/web-preview/identity/mfa-migration.kcadm.sh`, `deploy/web-preview/migrate-mfa.sh` | n/a | `scripts/test_keycloak_mfa_migration.py` | CFG-027, CFG-028, CFG-029, CFG-031 | [infrastructure and deployment](../03-architecture/infrastructure-and-deployment.md) |

## 13. Administrative permissions and configuration

| Requirement | Journey | API operation | Implementation | DB | Automated test | Owner E2E | Handbook |
|---|---|---|---|---|---|---|---|
| BR-042 fee/rate/zone/policy changes versioned, reasoned, audited; only new quotes use them (TC-042) | backoffice-admin | API-089 `GET /api/v1/ops/configuration`, API-090 `POST /api/v1/ops/configuration/{key}/versions` | `orders/internal/ConfigurationService.java:85` | `commerce_policy`, `config_change`, `feature_flag` (V19) | `orders/ConfigurationIntegrationTest`, `orders/PublicConfigurationIntegrationTest` | E2E-OPS-061, E2E-OPS-106, E2E-BASKET-024 | [backoffice admin journey](../02-user-journeys/backoffice-admin.md) |
| Staff grants need reason + recent MFA, audited (BR-043, TC-111) | backoffice-admin | API-092, API-093 | `identity/web/StaffController.java`, `GrantService` | `user_grant`, `audit_event` | `identity/StaffAccessIntegrationTest` (`grant_changes_require_recent_mfa`); grant UI test: GAP (only in `e2e/cook-workspace.e2e.ts`, `operations-layout.e2e.ts`) | E2E-OPS-059, E2E-OPS-102, CFG-104 | [actors and permissions](../01-business/actors-and-permissions.md) |
| Section visibility follows server grants | backoffice-admin | API-067, 071, 078, 074, 086, 089, 091, 112 | `apps/operations-web/src/App.tsx` (`checkSession`) | n/a | `operations-web` tests; `e2e/operations-layout.e2e.ts` | E2E-OPS-055, E2E-FIN-112 | [frontend code guide](../04-codebase/frontend-code-guide.md) |
| Audit log read; export | backoffice-admin | API-094 (IMPLEMENTED), API-095 (PLANNED) | `operations/web/AuditController.java` | `audit_event` (V4) | `observability/AuditAndCorrelationReadinessIntegrationTest`, `identity/StaffAccessIntegrationTest.audit_is_append_only_and_redacted`; export: GAP (not implemented) | E2E-OPS-110 | [identity and security](../03-architecture/identity-and-security.md) |
| Integration health and outbox retry | backoffice-admin | API-096, API-097 | `notifications/web/NotificationController.java` | `outbox_event` (V11) | `notifications/NotificationIntegrationTest` | GAP (no owner case found by operation search) | [integration and events](../03-architecture/integration-and-events.md) |

## Coverage-gap list

Every GAP above, plus gaps observed while verifying:

1. No scenario id exists for D18 (MFA policy/enforcement/reset). Tests exist (`mfa/*`, `identity/MfaResetIntegrationTest`), but `planning/scenarios.json` has no `TC-nnn` for them, so rule/scenario traceability for BR-007's D18 part is by owner cases only.
2. `x-permission` in `contracts/openapi.yaml` is not checked against controller `@PreAuthorize` annotations; only registry-vs-OpenAPI equality and route sets are tested.
3. Login throttling relies on Keycloak `bruteForceProtected`; no test in this repository exercises it (TC-054 NOT_RUN, MB-036 TODO).
4. Idempotency record purge: no code or test removes expired `idempotency_record` rows.
5. UI-level unit coverage missing for: cash report submission (`apps/operations-web/src/cook/Orders.tsx`), ops exception cancel, staff grant/revocation screens (covered only by Playwright journeys).
6. Native Android step-up and native delivery-mode ordering have no automated test named in the repository (device tests, BLK-004; E2E-ORDER-122 names pickup only).
7. Integration health / outbox retry have a backend test but no owner E2E case in `docs/quality/owner-acceptance/cases/*.json`.
8. 161 of 200 owner cases have no scenario link in `docs/quality/owner-acceptance/generated/traceability.json` (`case_to_scenarios`), and all 200 are NOT_RUN or BLOCKED: owner acceptance of these paths is **not** demonstrated; only automated tests and self-reported verification records exist.
9. Accessibility (BR-047): TC-047 BLOCKED (screen reader); keyboard tests exist (`e2e/keyboard.e2e.ts`).
10. Performance (BR-059, TC-059), recovery on staging (BR-057, TC-057) and pilot operations (BR-061..064) are NOT_RUN.

## Known limitations and documentation gaps

- Scenario `status: PASS` values in `planning/scenarios.json` are self-reported (see `docs/status/latest.md`: "not an independent review certificate"); this matrix does not re-run them.
- Rows list the most relevant tests, not every test touching the behaviour.
