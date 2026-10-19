# User journeys: backoffice (operations, support, finance view, administrator)

> **Owner role:** Frontend and mobile
> **Status:** PARTIALLY IMPLEMENTED — most staff sections exist in the operations web; the audit trail, integration/outbox operations, cash-report verification, the operations dashboard and audit export have no screen or no endpoint.

The backoffice is the second group of tabs of `apps/operations-web` (same app as the [cook workspace](cook-operations.md)). Money-specific flows are on [Finance and support](finance-and-support.md); sign-in and MFA on [Authentication and MFA](authentication-and-mfa.md).

## There are no staff "roles", only permission grants

The names Operations, Support, Finance and Administrator are job descriptions. In code the only authorisation unit is a **permission** granted to an account in table `user_grant` (`backend/app/src/main/resources/db/migration/V4__grants_and_audit.sql`, extended by V31). The enum is `backend/modules/identity/src/main/java/com/matbakhy/identity/api/Permission.java`; `AccountMapper` (`backend/app/src/main/java/com/matbakhy/app/security/AccountMapper.java`) reads active grants **on every request** and turns each into the Spring authority `PERM_<code>`. Token role claims are ignored. The administrator is not a superuser: a bootstrap administrator receives only `staff.grant` and `audit.read` (`GrantService.bootstrapAdministratorIfNone`, driven by the configured bootstrap subject in `IdentityProperties`). Product intent: `docs/product/06-permissions.md`.

High-risk actions additionally require **recent MFA** via the SpEL guard `@staffAssurance.recentMfa(authentication)` (`backend/app/src/main/java/com/matbakhy/app/security/StaffAssurance.java`): an MFA-level `acr` (or `amr` containing `mfa`/`otp`/`hwk`) and `auth_time` within `OIDC_STEP_UP_MAX_AGE` (default 10 minutes), and not older than the account's last administrative MFA reset. Missing proof gives `403 STEP_UP_REQUIRED`.

### Permission matrix (verified against the `@PreAuthorize` annotations and the UI probes)

| Permission code | Endpoints it opens (API id) | Needs recent MFA | Operations-web tab |
|---|---|---|---|
| `cook.review` | list/detail applications (067, 068); decision (069); kitchen operating state (070) | decision and operating state: yes | Reviews (`ops/Reviews.tsx`) |
| `content.moderate` | catalogue list (071), meal moderation (072), review list (098), review moderation (073) | no | Catalogue (`ops/Catalogue.tsx`, includes review moderation) |
| `order.ops` | ops order list/detail (074, 075) | no | Orders (`ops/OpsOrders.tsx`) |
| `order.exception_cancel` | exception cancel (076) | no (annotation shows permission only) | Orders |
| `order.handover_override` | handover override (077) | yes | Orders |
| `support.case` | case queue, detail, message, update (078 to 081) | no | Cases (`ops/Cases.tsx`) |
| `finance.read` | cash-report list (082), statements (086) | no | Finance (`ops/Finance.tsx`) |
| `finance.verify` | cash-report verification (083); payment recipients and payment queue (104, 106, 107, 109) | 104 and 107: yes; 083: annotation shows no MFA guard | Finance → payments panel (`ops/Payments.tsx`) |
| `finance.refund_approve` | refund request/action (084, 085), statements | no | Finance |
| `finance.settlement` | settlements (087, 088), statements | no | Finance |
| `config.read` / `config.write` | configuration view (089); new version (090, write only) | write: yes | Configuration (`ops/Configuration.tsx`) |
| `staff.grant` | staff list (091); grant (092); revoke (093) | grant and revoke: yes | Staff (`ops/Staff.tsx`) |
| `audit.read` | audit trail (094) | no | **none** (API only) |
| `integration.ops` | integrations list (096), outbox retry (097) | no | **none** (API only) |
| `security.mfa_policy` | MFA policy read (114) and update (115) | update: yes | Security tab (`ops/Security.tsx`) |
| `security.mfa_reset` | administrative MFA reset (116) | yes | Security tab |

Sources: `ConfigurationController`, `OpsOrderController`, `PaymentController`, `ReviewController`, `AuditController` under `backend/modules/*/src/main/java/com/matbakhy/*/web/`, plus `backend/app/src/main/java/com/matbakhy/app/mfa/MfaAdminController.java`, `backend/modules/identity/src/main/java/com/matbakhy/identity/web/StaffController.java`, `backend/modules/cooks/src/main/java/com/matbakhy/cooks/web/CookReviewController.java`, `backend/modules/finance/src/main/java/com/matbakhy/finance/web/CashController.java` and `FinanceOpsController.java`, `backend/modules/support/src/main/java/com/matbakhy/support/web/StaffCaseController.java`, `backend/modules/notifications/src/main/java/com/matbakhy/notifications/web/NotificationController.java`.

### How the UI decides which tabs to show

`checkSession` in `apps/operations-web/src/App.tsx` does **not** read permissions. It probes one endpoint per section with `limit: 1` and shows a tab only if the call returns 2xx: `GET /api/v1/ops/cooks`, `/ops/catalogue`, `/ops/cases`, `/ops/orders`, `/ops/configuration`, `/ops/staff`. For Finance it calls `GET /api/v1/ops/finance/statements` with an all-zero kitchen id and treats **404 as allowed** (403 = not allowed). The Security tab appears when `GET /api/v1/me/security` says `canManagePolicy` or `canResetMfa`. UI gating is convenience only; the API checks every call (E2E-OPS-055, E2E-FIN-112, E2E-OPS-058).

Consequences found while tracing this (derived from code, not from a test):

* The Finance probe is accepted for `finance.read`, `finance.settlement` or `finance.refund_approve` (annotation on API-086). An account holding **only** `finance.verify` gets 403 on the probe, so it sees no Finance tab although the payments panel inside that tab needs `finance.verify`. NOT VERIFIED by a test; check `FinanceOpsController.java` line ~72 and `App.tsx`.
* `audit.read` and `integration.ops` holders see no tab for them.

## Common journey items

**Actor.** Staff account with the permission shown in each journey; the same account may also be a customer or cook. **Transport.** Web session cookie and CSRF header. **Audit.** Staff reads and writes record `audit_event` rows (append-only by trigger, V4). **Tests (all).** `apps/operations-web/src/App.test.tsx` (staff sections are exercised inside "operations-web cook workspace" and "two-step verification policy (D18)"); backend `backend/app/src/test/java/com/matbakhy/app/identity/StaffAccessIntegrationTest.java`, `backend/app/src/test/java/com/matbakhy/app/security/StaffAssuranceTest.java`; Playwright `apps/operations-web/e2e/operations-layout.e2e.ts` and the journey end of `customer-journey.e2e.ts`. Results for the current commit: NOT VERIFIED here.

## BO-1 Cook review (operations)

1. Goal: verify cook applications and manage kitchen availability. 2. Actor: `cook.review`; decisions and state changes need recent MFA. 3. Prereq: submitted application.
4. Start: tab Reviews. 5. Actions: open queue (filter by state/name), open an application, START_REVIEW, decide, then if needed pause / suspend / restore a kitchen with a reason. 6. Responses: queue refresh, decision recorded with reviewer; the kitchen panel shows active-order impact. 7. API: 067 to 070 plus `GET /api/v1/uploads/{uploadId}` (private documents for the reviewer). 8. Backend: `CookReviewService`, `KitchenService` (details in [Cook operations](cook-operations.md) CO-2, CO-7). 9. DB: `cook_application*`, `kitchen`, `audit_event`. 10. Transitions: application and kitchen state machines in CO-1/CO-7. 11. Errors: self-review `FORBIDDEN`, stale revision/version, `STEP_UP_REQUIRED` (the UI shows the verify link from the session check and error lines). 12. Tests: `backend/app/src/test/java/com/matbakhy/app/cooks/CookWorkflowIntegrationTest.java`. 13. Owner E2E: E2E-COOK-049, E2E-OPS-108, E2E-OPS-109, E2E-COOK-050; J6. 14. IMPLEMENTED. 15. Limitation: the reviewer must read documents through the operations web; no document viewer inside the repo is described beyond the fetch call.

## BO-2 Catalogue and review moderation

1. Goal: hide inappropriate meals or reported reviews without altering cook state. 2. Actor: `content.moderate`. 3. Prereq: published meals / reported reviews.
4. Start: tab Catalogue. 5. Actions: filter the catalogue, hide a meal with a reason, restore it; list reported reviews, hide or restore a review with a reason. 6. Responses: the meal leaves customer sale at once; the cook's own status is unchanged; a hidden review leaves the public kitchen page.
7. API: `GET /api/v1/ops/catalogue`, `POST /api/v1/ops/meals/{mealId}/moderation`, `GET /api/v1/ops/reviews`, `POST /api/v1/ops/reviews/{reviewId}/moderation` (071 to 073, 098). 8. Backend: `backend/modules/catalog/src/main/java/com/matbakhy/catalog/web/ModerationController.java`, `backend/modules/orders/src/main/java/com/matbakhy/orders/web/ReviewController.java`. 9. DB: `meal.moderation_state` (V16), `review`, `review_report` (V20). 10. `VISIBLE ↔ HIDDEN`. 11. Errors: reason required; version conflict. 12. Tests: `backend/app/src/test/java/com/matbakhy/app/catalog/ModerationIntegrationTest.java`, `ReviewsAndFavoritesIntegrationTest.java`. 13. E2E-OPS-056, E2E-OPS-104. 14. IMPLEMENTED. 15. Limitation: no bulk actions.

## BO-3 Order monitoring and exceptions

1. Goal: inspect any order and resolve exceptions without bypassing the state machine. 2. Actor: `order.ops` (read); `order.exception_cancel`; `order.handover_override` + recent MFA. 3. Prereq: order exists; an exception cancel needs a linked support case id and a state of `PREPARING` or `READY`; a handover override needs evidence and a locked or stuck handover.
4. Start: tab Orders (`ops/OpsOrders.tsx`). 5. Actions: filter/list, open an order (timeline, totals, masked contact; opening customer contact is audited), exception-cancel with reason (`SAFETY`, `OPERATIONAL`, `OTHER`) and case id, or override handover with evidence reference.
6. Responses: no handover code is ever exposed to staff (`OrderService.staffView`). 7. API: `GET /api/v1/ops/orders`, `GET /api/v1/ops/orders/{orderId}`, `POST …/exception-cancel`, `POST …/handover-override` (074 to 077). 8. Backend: `OpsOrderController.java` → `OpsOrderService.java`, `OrderLifecycleService.opsExceptionCancel / opsHandoverOverride`. 9. DB: `customer_order`, `order_event`, `handover_challenge`, `stock_allocation`, `audit_event`. 10. `PREPARING | READY → CANCELLED` (consumed capacity is kept, E2E-ORDER-109); locked handover `→ COMPLETED` with reason `HANDOVER_OVERRIDE`. 11. Errors: `VALIDATION_FAILED` (`caseId`, `evidenceRef` required), `STATE_CONFLICT`, `STEP_UP_REQUIRED`. 12. Tests: `backend/app/src/test/java/com/matbakhy/app/orders/OrderLifecycleIntegrationTest.java`. 13. E2E-ORDER-108, E2E-ORDER-109, E2E-ORDER-119, E2E-OPS-103; J1. 14. IMPLEMENTED. 15. Limitation: API-066 operations dashboard metrics are NOT IMPLEMENTED (`contracts/endpoints.json`; E2E-OPS-112).

## BO-4 Support queue

See [Finance and support](finance-and-support.md#fs-4-support-cases-customer-side-and-staff-side).

## BO-5 Configuration and commerce policy

1. Goal: change commerce policy (delivery fee, order limits, commission fixed at zero) and feature flags with versioning. 2. Actor: `config.read` (view) / `config.write` + recent MFA. 3. Prereq: current version number.
4. Start: tab Configuration. 5. Actions: read keys and versions; submit a new version with value, reason, approval reference and `expectedVersion`. 6. Responses: new version appended, never edited in place; two sessions from the same version produce a version conflict and a refreshed form (E2E-OPS-061). New fee applies to new quotes only (E2E-OPS-106). Commission must stay zero; a real policy needs an approval reference; scheduled changes are refused (E2E-OPS-107).
7. API: `GET /api/v1/ops/configuration`, `POST /api/v1/ops/configuration/{key}/versions` (089, 090). 8. Backend: `ConfigurationController.java` → `backend/modules/orders/src/main/java/com/matbakhy/orders/internal/ConfigurationService.java`, `CommercePolicies.java`. 9. DB: `commerce_policy` (V10), `feature_flag`, `config_change` (V19). 10. Policy versions append-only. 11. Errors: `VERSION_CONFLICT`, `VALIDATION_FAILED`, `STEP_UP_REQUIRED` (the UI hides the plain error line and relies on the verify link). 12. Tests: `backend/app/src/test/java/com/matbakhy/app/orders/ConfigurationIntegrationTest.java`, `backend/app/src/test/java/com/matbakhy/app/config/CommerceConfigurationTest.java`. 13. E2E-OPS-061, E2E-OPS-106, E2E-OPS-107. 14. IMPLEMENTED. 15. Limitation: `GET /api/v1/policies/{key}` (API-008) not implemented.

## BO-6 Staff grants (administrator)

1. Goal: give and take permissions with accountability. 2. Actor: `staff.grant`; grant and revoke need recent MFA. 3. Prereq: target account active; a reason of 3 to 500 characters.
4. Start: tab Staff. 5. Actions: view members and their grants; choose a permission and enter a reason to grant; revoke a grant with a reason. 6. Responses: effect on the target's very next request (grants are re-read each request; revoked or expired grants stop working immediately, E2E-OPS-102).
7. API: `GET /api/v1/ops/staff`, `POST /api/v1/ops/staff/{userId}/grants`, `POST …/grant-revocations` (091 to 093). 8. Backend: `StaffController.java` → `GrantService.grant / revoke`. 9. DB: `user_grant`, `audit_event`. 10. Grant active → revoked; unique active grant per user and permission (partial unique index). 11. Errors: self-escalation `FORBIDDEN` (audited), unknown permission, past expiry, duplicate active grant (`STATE_CONFLICT`), revoking the last `staff.grant` holder refused (`last administrator`). 12. Tests: `backend/app/src/test/java/com/matbakhy/app/identity/StaffAccessIntegrationTest.java`. 13. E2E-OPS-055, E2E-OPS-058, E2E-OPS-059, E2E-OPS-060, E2E-OPS-101, E2E-OPS-102, CFG-104, CFG-105; J6, J7. 14. IMPLEMENTED. 15. Limitations: the Staff list shows only accounts that already hold a grant (`GrantService.staff()`), so granting to a fresh account needs its user id from another source; the grant UI sends only `permission` and `reason` (no `expiresAt` occurs in `apps/operations-web/src/ops/Staff.tsx`), although the API accepts an expiry.

## BO-7 Security administration (MFA policy and reset)

1. Goal: set OPTIONAL / REQUIRED / DISABLED per population and reset a user's authenticator. 2. Actor: `security.mfa_policy`, `security.mfa_reset`, both with recent MFA for writes. 3. Prereq: Keycloak admin client configured for reset, else 503 (`docs/security/mfa-policy.md`).
4. Start: tab Security (`ops/Security.tsx`), shown to every signed-in account for its own settings. 5. Actions: read the three category policies, change one with `expectedVersion` and a reason; reset an account by id with a reason of at least 10 characters.
6–15: see [Authentication and MFA](authentication-and-mfa.md) (AM-5, AM-6). API 114 to 116; backend `MfaAdminController.java`, `MfaPolicyService.java`, `KeycloakAdmin.java`; tests `MfaPolicyIntegrationTest.java`, `MfaResetIntegrationTest.java`, `MfaResetAuditIntegrationTest.java`; E2E CFG-015, CFG-016, CFG-018 to CFG-023, CFG-122, E2E-OPS-111; J7. IMPLEMENTED.

## BO-8 Audit trail and integrations

1. Goal: read who did what; retry stuck outbox events. 2. Actor: `audit.read`; `integration.ops`. 3. Prereq: grant.
4. Start: **no screen**. 5. Actions: API calls only. 6. Responses: audit page with filters `actorId, objectType, objectId, action`, cursor paging, limit clamped to 1 to 100; reading the audit trail is itself audited. 7. API: `GET /api/v1/ops/audit` (094), `GET /api/v1/ops/integrations` (096), `POST /api/v1/ops/outbox/{eventId}/retry` (097); `GET /api/v1/ops/audit/export` (095) is NOT IMPLEMENTED. 8. Backend: `backend/modules/operations/src/main/java/com/matbakhy/operations/web/AuditController.java`, `NotificationController.java`, `OutboxDispatcher.java`. 9. DB: `audit_event`, `outbox_event`. 10. Outbox `PENDING → SENT | FAILED`. 11. Errors: 403 without grant (denial audited). 12. Tests: `backend/app/src/test/java/com/matbakhy/app/observability/AuditAndCorrelationReadinessIntegrationTest.java`, `backend/app/src/test/java/com/matbakhy/app/notifications/NotificationIntegrationTest.java`. 13. E2E-OPS-110, E2E-OPS-112. 14. PARTIALLY IMPLEMENTED (API only). 15. Limitation: no UI; export not built.

## Owner acceptance coverage

| Area | Owner E2E ids |
|---|---|
| Role boundaries | E2E-OPS-055, E2E-OPS-058, E2E-OPS-062, CFG-005 |
| Grants | E2E-OPS-059, E2E-OPS-060, E2E-OPS-101, E2E-OPS-102, CFG-105 |
| Operations journey end | E2E-OPS-103 (core suite J1) |
| Unimplemented items recorded as cases | E2E-OPS-112 |

## Known limitations and documentation gaps

* `docs/product/06-permissions.md` mentions role columns and `finance.settlement_verify`; code has no role objects and the permission is `finance.settlement`. Code wins.
* `user_grant` CHECK in V4 lists 15 permission codes; V31 adds the two security codes (see `backend/app/src/main/resources/db/migration/V31__mfa_policy.sql`).
* Section tab visibility is probe-based (above); a purpose-built "my permissions" endpoint does not exist.
