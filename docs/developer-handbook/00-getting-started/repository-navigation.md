# Repository navigation: where do I find it?

> **Owner role:** Handbook navigation
> **Status:** REFERENCE — task-oriented lookup table from a question to the file that answers it

Use this page when you know what you are looking for ("where is the price validated?") but not where it lives.
For the directory layout see [repository map](../04-codebase/repository-map.md). Paths below were checked against
the tree on this branch.

## 1. "Where does it start?"

| Application | Startup file | What happens there |
|---|---|---|
| Backend | `backend/app/src/main/java/com/matbakhy/app/MatbakhyApplication.java` | `SpringApplication.run`; component scan of `com.matbakhy`; every module's controllers/services are discovered here |
| Customer web | `apps/customer-web/src/main.tsx` | loads Cairo fonts + design tokens, renders `App` from `apps/customer-web/src/App.tsx` in `StrictMode` |
| Operations web (cook workspace + backoffice) | `apps/operations-web/src/main.tsx` | same pattern; `apps/operations-web/src/App.tsx` decides which sections to show |
| Customer mobile | `apps/customer-mobile/index.ts` | `registerRootComponent` of `App` from `apps/customer-mobile/src/App.tsx` (production entry, no test doubles) |

## 2. API surface

| Question | Answer |
|---|---|
| Where are routes defined in code? | `@RestController` classes in `backend/modules/<module>/src/main/java/com/matbakhy/<module>/web/*.java`; plus `backend/app/src/main/java/com/matbakhy/app/mfa/MfaController.java` and `MfaAdminController.java` |
| Where is the contract? | `contracts/openapi.yaml` (typed) and `contracts/endpoints.json` (route registry with ids `API-001`..`API-116`, permission and `implemented` flag) |
| What fails if code, registry and contract disagree? | `packages/api-client/test/registry.test.ts` (registry vs OpenAPI: method, path, permission, `x-implemented`, error-code enum vs `ErrorCode.java`), `backend/app/src/test/java/com/matbakhy/app/web/ContractRoutesTest.java` (registry `implemented: true` set must equal the routes the running app serves), and `pnpm --filter @matbakhy/api-client run check:drift` (generated `packages/api-client/src/generated/schema.ts` must match a fresh generation) |
| How do the three apps share it? | They import `@matbakhy/api-client` (`packages/api-client/src/index.ts`, `createApiClient` over `openapi-fetch`); paths and bodies are type-checked against the generated schema |

See [API contracts](../04-codebase/api-contracts.md) for the workflow to add or change an endpoint.

## 3. Business rules, authorization and pricing

| Question | Where |
|---|---|
| Business rule text | `docs/product/03-business-rules.md` and machine-readable `planning/rules.json` (`BR-001`..`BR-064`); see [business rules](../01-business/business-rules.md) |
| Where are rules enforced in code? | In the owning module's `internal` services, always server-side. Quotes/orders/payments: `backend/modules/orders/src/main/java/com/matbakhy/orders/internal/` (`QuoteService.java`, `OrderService.java`, `OrderLifecycleService.java`, `PaymentService.java`); stock: `backend/modules/availability/src/main/java/com/matbakhy/availability/internal/JdbcSlotInventory.java`; cash and ledger: `backend/modules/finance/src/main/java/com/matbakhy/finance/internal/` |
| Authentication filters and chains | `backend/app/src/main/java/com/matbakhy/app/security/SecurityConfiguration.java` (bearer chain for native, OIDC session chain with CSRF for web) |
| Staff permissions | enum `backend/modules/identity/src/main/java/com/matbakhy/identity/api/Permission.java`; checked by `@PreAuthorize("hasAuthority('PERM_...')")` on controllers; recent-MFA by `@staffAssurance.recentMfa(authentication)` (`backend/app/src/main/java/com/matbakhy/app/security/StaffAssurance.java`) |
| Object-level authorization (owner / kitchen / case scope) | SQL scoping in services (for example `customer_id = ?`, `kitchen_id = ?` in lock queries) and `AccessPolicy` (`backend/modules/identity/src/main/java/com/matbakhy/identity/api/AccessPolicy.java`) implemented by `backend/app/src/main/java/com/matbakhy/app/security/ScopedAccessPolicy.java`; others' objects answer 404 |
| MFA policy enforcement | `backend/app/src/main/java/com/matbakhy/app/security/MfaEnforcementFilter.java` + `backend/app/src/main/java/com/matbakhy/app/mfa/MfaPolicyService.java` |
| Where is price validated? | At quote time `QuoteService.terms` (`backend/modules/orders/src/main/java/com/matbakhy/orders/internal/QuoteService.java:206`) recomputes from published meals; at placement `QuoteService.requireValid` (`:146`) recomputes again and refuses a stale quote with `PRICE_CHANGED`. Client prices are never read. Walkthrough: [pricing implementation](../04-codebase/pricing-implementation.md) |
| Where is an order placed? | `OrderService.place` (`backend/modules/orders/src/main/java/com/matbakhy/orders/internal/OrderService.java:131`) called by `OrderController.place` (`backend/modules/orders/src/main/java/com/matbakhy/orders/web/OrderController.java:61`). Walkthrough: [order implementation](../04-codebase/order-implementation.md) |
| Where are deposits / cash / ledger? | [payment implementation](../04-codebase/payment-implementation.md) |

## 4. Data and identity configuration

| Question | Where |
|---|---|
| Database migrations | `backend/app/src/main/resources/db/migration/V<n>__<name>.sql` (V1..V31 at the time of writing); Flyway runs them at startup. Compatibility checks: `scripts/check_migration_compat.py` |
| Least-privilege DB role | `backend/app/src/main/resources/db/migration/afterMigrate.sql`; role creation for the preview in `deploy/web-preview/postgres/20-runtime-role.sh` |
| Development-only data | `backend/app/src/main/resources/db/devdata/` (never loaded in deployed profiles) |
| Keycloak realm (local) | `infra/identity/matbakhy-realm.json` (clients `matbakhy-web`, `matbakhy-mobile`, `matbakhy-admin`; custom browser flow with "level 1"/"level 2" for MFA) |
| Keycloak realm (preview/staging) | `deploy/web-preview/identity/matbakhy-realm.preview.json`, `deploy/web-preview/identity/mfa-migration.kcadm.sh`, `deploy/web-preview/identity/verify-mfa-flow.py` |
| Keycloak realm used by backend tests | `backend/app/src/test/resources/keycloak/test-realm.json` via `backend/app/src/test/java/com/matbakhy/app/support/KeycloakTestServer.java` |
| Backend OIDC settings | `matbakhy.identity.*` and `matbakhy.mfa.*` in `backend/app/src/main/resources/application.yaml`, bound by `backend/app/src/main/java/com/matbakhy/app/security/IdentityProperties.java` |
| Environment safety (fake providers refused outside LOCAL/CI) | `backend/app/src/main/java/com/matbakhy/app/config/EnvironmentGuard.java` |

See also [identity and security](../03-architecture/identity-and-security.md).

## 5. Screens

| Surface | Files |
|---|---|
| Customer web | browse and filters `apps/customer-web/src/Browse.tsx`; meal `Meal.tsx`; kitchen `Kitchen.tsx`; basket + quote + placement `Checkout.tsx`; deposit status `Payment.tsx`; orders `Orders.tsx`; profile, addresses, favorites, inbox, support cases `Account.tsx` (phone verification happens inside `Checkout.tsx`); two-step verification `Security.tsx`; hash routes in `App.tsx` (`/`, `/meal/:id`, `/kitchen/:slug`, `/checkout`, `/orders`, `/orders/:id`, `/account`, `/support/:id`) |
| Cook workspace | `apps/operations-web/src/cook/` — `Dashboard.tsx`, `Orders.tsx`, `Meals.tsx`, `Slots.tsx`, `Payments.tsx`, `Statement.tsx`, `Insights.tsx`, `Settings.tsx`, `Onboarding.tsx` |
| Staff backoffice | `apps/operations-web/src/ops/` — `Cases.tsx`, `Catalogue.tsx`, `Configuration.tsx`, `Finance.tsx`, `OpsOrders.tsx`, `Payments.tsx`, `Reviews.tsx`, `Security.tsx`, `Staff.tsx`; which sections appear is decided in `apps/operations-web/src/App.tsx` by probing API calls the server allows |
| Customer mobile | tabs Discover/Favorites/Orders/Account and stack Meal/Cart/Order/Notifications/Support, `apps/customer-mobile/src/screens/*.tsx`, navigation types `apps/customer-mobile/src/screens/navigation.ts` |
| Mobile session storage | `apps/customer-mobile/src/tokenStore.ts` (OS secure store, key `matbakhy.session.v1`), `apps/customer-mobile/src/session.ts`, `apps/customer-mobile/src/basket.ts` (basket and pending placement, also secure store) |
| Web session | server-side session in PostgreSQL, cookie `MATBAKHY_SESSION`; client side only the CSRF echo in `apps/customer-web/src/api.ts` |

## 6. "How do I find an existing test?"

1. Start from the requirement: `planning/rules.json` (`BR-nnn`) → `scenario_ids` → `planning/scenarios.json` (`TC-nnn`).
2. Search for the id: `grep -rn "TC-118" backend/app/src/test apps packages`. Most backend integration tests name their
   scenarios in the class Javadoc (for example `OrderPlacementIntegrationTest` carries TC-022..025 and TC-118).
   `planning/scenarios.json` itself does **not** list test files; the [traceability matrix](../07-reference/requirements-traceability.md)
   records the mapping found by this search.
3. Or start from the API: search the route string or operation in `backend/app/src/test/java/com/matbakhy/app/`.
4. Owner acceptance cases (manual/automated hybrid): `docs/quality/owner-acceptance/cases/*.json`; their
   `automated_coverage` field names the test classes that overlap.
5. Test layout: backend integration tests `backend/app/src/test/java/com/matbakhy/app/<area>/*IntegrationTest.java`
   (real PostgreSQL via Testcontainers, `backend/app/src/test/java/com/matbakhy/app/support/IntegrationTest.java`);
   web unit/component tests `apps/*/src/*.test.tsx`; mobile `apps/customer-mobile/src/*.test.ts(x)`; browser journeys
   `apps/operations-web/e2e/*.e2e.ts`; contract tests `packages/api-client/test/*.test.ts`.
   Running them: [test execution](../05-developer-workflow/test-execution.md).

## 7. "Where do I implement my change?"

| Change | Start here | Also touch |
|---|---|---|
| New or changed endpoint | controller in the owning module's `web/` package; service in `internal/` | `contracts/endpoints.json`, `contracts/openapi.yaml`, regenerate `packages/api-client/src/generated/schema.ts`, add the permission annotation, add an integration test, handbook pages per [documentation policy](../05-developer-workflow/documentation-policy.md) |
| Pricing, quote or placement rule | `QuoteService` / `OrderService` | `PriceChangeIntegrationTest`, `QuoteIntegrationTest`, `OrderPlacementIntegrationTest`; needs architectural review (money) |
| New table or column | new `V32__<name>.sql` (never edit an applied migration) | repository code, `MigrationIntegrationTest`, `scripts/check_migration_compat.py`; see [database architecture](../03-architecture/database-architecture.md) |
| New staff permission | enum `Permission.java` + grant UI in `apps/operations-web/src/ops/Staff.tsx` | `docs/product/06-permissions.md`, `StaffAccessIntegrationTest` |
| Customer UI text / language | `apps/customer-web/src/i18n.ts`, `apps/customer-mobile/src/i18n.ts`, `apps/operations-web/src/i18n.ts` | Arabic and English both |
| Design token / color | `packages/design-tokens/tokens.json` | `packages/design-tokens/test/tokens.test.ts`, `scripts/check_design.sh` |
| Bug fix | write the failing test first (same area folder), fix in the owning module's `internal` code | if behaviour changes, update the handbook page and `doc001-matrix.json` rules apply |

Hard rule: no frontend change may decide price, capacity, role or state transition; those are server-owned
(see [backend module guide](../04-codebase/backend-module-guide.md) for the "do not change without architectural review" areas).

## Known limitations and documentation gaps

- `planning/scenarios.json` has no test-file field; scenario-to-test mapping exists only as ids written inside test files.
- Hash-route list for the customer web is taken from the regular expressions in `apps/customer-web/src/App.tsx`
  (lines 150-153 and 213-222); there is no routing table document.
