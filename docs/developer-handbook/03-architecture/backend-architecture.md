# Backend architecture

> **Owner role:** Backend architecture
> **Status:** IMPLEMENTED — modular monolith as described; planned provider adapters are called out in the text
> **Language / اللغة:** English · [العربية](backend-architecture-ar.md)

The backend lives in `backend/`: Java 21, Spring Boot 4.1.1 (`backend/pom.xml`), Maven wrapper `backend/mvnw`. It is one deployable (`backend/app`) assembled from nine domain modules plus a small shared kernel. Diagram: [backend components](diagrams.md#3-backend-components). A module-by-module reading guide is in [backend module guide](../04-codebase/backend-module-guide.md).

## Maven layout

| Maven module | Path | Role |
|---|---|---|
| `matbakhy-shared` | `backend/shared` | Kernel with no Spring web or domain code: `Money`, `ErrorCode`, `ApiException`, `IdempotentCommands` (port), `RequestContext`, `Languages`, `PersonalDataEraser` (port), `RuntimeEnvironment`, the `@DomainModule` annotation |
| `matbakhy-identity` | `backend/modules/identity` | Accounts, profile, addresses, phone verification, staff grants, account erasure |
| `matbakhy-cooks` | `backend/modules/cooks` | Cook applications, review, kitchens, kitchen settings, public kitchen pages |
| `matbakhy-catalog` | `backend/modules/catalog` | Meals and revisions, discovery, service zones, media upload and moderation, favourites, organic activity counters |
| `matbakhy-availability` | `backend/modules/availability` | Cook slots and per-meal capacity (`SlotInventory`) |
| `matbakhy-orders` | `backend/modules/orders` | Commerce policy and configuration, quotes, order placement and lifecycle, handover codes, payments (deposit tracking), reviews, ops order tools |
| `matbakhy-finance` | `backend/modules/finance` | Cash reports and verification, commission ledger, settlement, refunds, statements, insights |
| `matbakhy-support` | `backend/modules/support` | Support cases and messages |
| `matbakhy-notifications` | `backend/modules/notifications` | Outbox dispatcher, inbox, delivery worker, device registration |
| `matbakhy-operations` | `backend/modules/operations` | Audit log port and implementation, redaction, audit read API |
| `matbakhy-app` | `backend/app` | Composition layer and the only Spring Boot application (`MatbakhyApplication`): security, MFA, exception handling, provider adapters, idempotency store, metrics, configuration, resources and migrations, and all integration tests |

The parent `backend/pom.xml` lists modules in dependency order and manages versions (AWS SDK BOM, PDFBox, test-container images pinned by digest). It also pins Tomcat 11.0.26 and Jackson 3.1.7 above the Boot BOM as dependency-advisory overrides.

## Package convention inside a module

Every module uses the same three sub-packages under `com.matbakhy.<module>`:

```
web/       @RestController classes (package-private) + request records with Bean Validation
api/       the module's public surface: interfaces and records other modules may use
internal/  services, JDBC adapters implementing api interfaces, jobs, properties (everything else)
```

Example for orders: `backend/modules/orders/src/main/java/com/matbakhy/orders/web/OrderController.java` calls `internal/OrderService.java`, which calls other modules only through their `api` types such as `SlotInventory`, `MealCatalog`, `KitchenDirectory`, `AddressDirectory` (see the imports at the top of `OrderService.java`). The module's root `package-info.java` carries `@DomainModule(value = "orders", stories = {...})`.

### Boundary enforcement (two layers)

1. **Maven dependencies.** A module can only see modules declared in its own `pom.xml`. For example `backend/modules/availability/pom.xml` depends on `shared` and `identity` only, so it cannot import orders classes.
2. **ArchUnit tests** in `backend/app/src/test/java/com/matbakhy/app/architecture/`: `ModuleBoundaryTest` runs rules from `ModuleRules` (`production_modules_only_cross_through_api`, `production_modules_are_free_of_cycles`, `shared_depends_on_no_module_or_app`, modules do not depend on the app package, and every module package is declared with `@DomainModule`). The rules are parameterised and also run against deliberately violating fixture packages to prove they fail. Run: `cd backend && ./mvnw -B -ntp -pl app -am test -Dtest=ModuleBoundaryTest` (command shape not run in this session; see [test execution](../05-developer-workflow/test-execution.md) for the verified commands).

The `app` package is deliberately not a module: it may use `internal` classes of modules (for example `ProviderConfiguration` imports `notifications.internal.NotificationProperties`). New code in a module must not do that.

### Cross-module dependency summary

From each `pom.xml`: identity needs operations (audit); cooks needs identity, catalog, operations; catalog needs identity, operations, availability; availability needs identity; orders needs identity, catalog, cooks, operations, availability; finance needs identity, cooks, operations; support needs identity, orders, operations; notifications needs identity, orders, cooks, operations; operations needs only shared. Orders is therefore the central module and nothing below it may import it (the cycle rule).

## Layers and data access

* **Controller (web).** Thin: bind and validate the request record, read the caller from `@AuthenticationPrincipal CurrentAccount`, call a service, return a view record. Controllers are package-private classes with constructor injection. Example: `OrderController.place` reads `Idempotency-Key` and `Accept-Language` headers and returns `201` with `Location`.
* **Service (internal).** Holds the business rules and the transaction boundary (`@Transactional` from Spring; 81 uses in main code). Services return plain Java records; there is no separate DTO mapping layer.
* **Persistence.** **Plain JDBC through Spring `JdbcTemplate` with hand-written SQL**; there is no JPA/Hibernate (`@Entity` does not occur in the codebase) and no Spring Data repositories. "Repositories" are either SQL inside a service or `Jdbc*` classes that implement an `api` interface (for example `JdbcSlotInventory`, `JdbcMealCatalog`, `JdbcAccountDirectory`, `JdbcOrderDirectory`, `JdbcAuditLog`). Rows are mapped by lambdas or `queryForMap`. Consequences: no lazy-loading surprises, every query is visible in review, but column renames need a grep through the services.
* **Schema.** Owned by Flyway SQL migrations in `backend/app/src/main/resources/db/migration` (see [database architecture](database-architecture.md)). The application never creates tables.

## Transactions and concurrency

* A command service method is `@Transactional` and writes everything it needs atomically. Order placement (`OrderService.placeOnce`) writes the order, item snapshots, stock reservations, first event, outbox message, payment terms and cash obligation in one transaction. See diagram [order placement](diagrams.md#8-order-placement-sequence).
* **Pessimistic locks** are used for stock and state: `SELECT ... FOR UPDATE` on `cook_slot` then `meal_slot` rows in id order (deterministic order avoids deadlocks) in `JdbcSlotInventory.reserve`; the order row is locked first in every lifecycle command (`OrderLifecycleService.lock`). Inventory methods are annotated `@Transactional(propagation = MANDATORY)` so they cannot be called outside a caller's transaction.
* **Advisory locks** serialise duplicate placement attempts (`pg_advisory_xact_lock` on customer + `clientRequestId`).
* **Optimistic concurrency** uses a `version` column and `expectedVersion` in request bodies; a mismatch is `409 VERSION_CONFLICT` (for example `PaymentService`, `MfaPolicyService.update`).
* **Idempotency.** `IdempotentCommands.execute(actor, command, key, request, resultType, action)` (port in `backend/shared`, implementation `backend/app/src/main/java/com/matbakhy/app/idempotency/JdbcIdempotentCommands.java`) stores a record per `(actor, command, key)` with a request hash in `idempotency_record`. The same key with the same body returns the stored result; with a different body it fails with `IDEMPOTENCY_CONFLICT`. It runs inside the caller's transaction so record and effects commit together. `OrderService` adds a request fingerprint (`V29__order_request_fingerprint.sql`) so reuse of a `clientRequestId` with different content is rejected even if the header record is removed (it carries a 24-hour `expires_at` that is recorded but not enforced; no purge exists).
* **Rollback rules.** Handover verification uses `@Transactional(noRollbackFor = ApiException.class)` so that a *wrong code* attempt is still counted and can lock the challenge (`OrderLifecycleService.cook`).
* **Workers** (`OutboxDispatcher`, `DeliveryWorker`) use `TransactionTemplate` and `FOR UPDATE SKIP LOCKED` leases so several instances can run safely. See [integration and events](integration-and-events.md).

## Validation and error handling

* Request bodies are Java records with Jakarta Bean Validation annotations (`@NotNull`, `@Size`, `@Valid`). Jackson is configured to **fail on unknown properties** (`backend/app/src/main/java/com/matbakhy/app/config/StrictJsonConfiguration.java`), so a misspelled field is rejected rather than ignored.
* Business rule failures throw `ApiException(ErrorCode, message, List<FieldError>)` (`backend/shared/src/main/java/com/matbakhy/shared/error`). `ErrorCode` fixes HTTP status and retryability (for example `PRICE_CHANGED` 409, `RATE_LIMITED` 429, `STEP_UP_REQUIRED` 403, `DEPENDENCY_UNAVAILABLE` 503).
* `backend/app/src/main/java/com/matbakhy/app/web/ApiExceptionHandler.java` (`@RestControllerAdvice`) maps `ApiException`, validation errors, malformed JSON, missing headers, unknown routes, wrong methods, access denial and everything else to one `ApiError` body that carries the code, localisable message key, field errors and the request ID. Stack traces and internal messages are never returned (`server.error.include-*: never` in `application.yaml`). Unexpected exceptions are logged through `RedactedThrowable`, which strips messages that could quote personal data. Spring's problem-details support is disabled (`spring.mvc.problemdetails.enabled: false`) so this body is the only shape.
* Security failures (401/403 before a controller runs) use `ApiSecurityErrors` so they have the same body, and denials are audited.
* Object-level authorization: services call `AccessPolicy.requireOwnerOrPermitted(ownerId, permission, objectType, objectId)` (`ScopedAccessPolicy`) or filter by owner in SQL (`lock(c.orderId(), "o.customer_id = ?", customerId)`). Anything not visible to the caller is **404**, never 403, so existence is not revealed (`OrderController` javadoc: "others' orders are 404"). Staff reading someone else's resource is audited.

## Authentication and authorization in the backend

Two filter chains (`SecurityConfiguration`): a stateless bearer-JWT chain (mobile) and a cookie-session OIDC chain (web). Both end in `AccountMapper`, which builds an `AccountAuthentication` whose authorities come from the database (`ACCOUNT_ACTIVE` plus `PERM_<code>` per active `user_grant`), re-read on every request. Controllers use `@PreAuthorize("hasAuthority('PERM_...')")`; high-risk staff endpoints add `and @staffAssurance.recentMfa(authentication)`. Full detail in [identity and security](identity-and-security.md).

## Background jobs

`@EnableScheduling` is on in `PlatformConfiguration`. Job failures are logged without exception messages (redacted scheduler error handler). Each job can be switched off by a property for tests.

| Job class | Interval default | Property | Purpose |
|---|---|---|---|
| `OrderExpiryJob` (orders) | 30s | `matbakhy.orders.expiry-job.enabled` | Expire pending orders past `accept_by`, release stock |
| `LedgerPostingJob` (finance) | 30s | `matbakhy.finance.posting-job.enabled` | Post one `COMMISSION` ledger group per completion event using the order's snapshotted commission (`LedgerService.postCommission`); with the zero-commission launch rule the amounts are 0 (see `backend/app/src/test/java/com/matbakhy/app/finance/ZeroCommissionIntegrationTest.java`) |
| `NotificationJobs` (notifications) | 5s | `matbakhy.notifications.jobs-enabled` | Run `OutboxDispatcher` and `DeliveryWorker` |
| `MediaCleanupJob` (catalog) | 30s | `matbakhy.media.cleanup-job.*` | Delete orphaned object-storage files recorded in `media_cleanup` |
| `AccountErasureJob` (identity) | 15m | `matbakhy.privacy.erasure-job.enabled` | Erase personal data after the deletion grace period (default `P14D`, a proposed default pending owner decision O06) |

## Configuration and startup guards

`backend/app/src/main/resources/application.yaml` holds all properties with environment-variable overrides. `matbakhy.environment` (`APP_ENV`) has **no default** so a deployment cannot start unconfigured. `EnvironmentGuard` refuses the `dev` profile or the `devdata` Flyway location outside LOCAL/CI/PREVIEW; `ProviderConfiguration` refuses development secrets (names starting `local-dev-only-`) outside LOCAL/CI and refuses to start STAGING/PRODUCTION without real SMS, email, push and malware-scanner adapters (none exist yet). Commerce rules (zero commission) are enforced in `CommerceConfiguration` and by the V23 check constraint.

The API uses two database roles in deployed environments: Flyway migrates as the owner (`SPRING_FLYWAY_USER`), requests run as the least-privilege runtime role (`DATABASE_USERNAME`), which `afterMigrate.sql` grants data access to but not DDL or `UPDATE`/`DELETE` on append-only tables.

## Observability

* Every log line carries `[requestId]` (`logging.pattern.level`); deployed environments switch to ECS JSON (`LOGGING_STRUCTURED_FORMAT_CONSOLE=ecs`) with service name `matbakhy-api`, environment and version from `MATBAKHY_VERSION`.
* Actuator exposes `health` on the API port (details hidden). The Prometheus endpoint is served only on a separate management port (8081 in the preview) that no proxy forwards (`SecurityConfiguration.managementScrape`).
* `OperationalMetrics` publishes database-derived gauges such as `matbakhy.outbox.oldest.pending.age`, `matbakhy.outbox.failed`, `matbakhy.notifications.dead` and `matbakhy.ledger.unbalanced.groups`, which back the alert rules in `deploy/web-preview/observability/alerts.yml`.
* The audit trail is a business feature (`audit_event` table, `GET /api/v1/ops/audit` in `backend/modules/operations/src/main/java/com/matbakhy/operations/web/AuditController.java`), separate from logs.

## How to find your way: practical examples

| Question | Start here |
|---|---|
| Which class handles `POST /api/v1/orders`? | grep the path: `grep -rn '"/api/v1/orders"' backend/modules`. Result: `OrderController.place` in orders `web`. The path list is also in `contracts/endpoints.json`. |
| Where is the price calculated? | `QuoteService.terms(...)` in orders `internal`; re-checked in `requireValid`. |
| Where does a state change happen? | `OrderLifecycleService.transition(...)`; every state change also writes an `order_event` row and an `outbox_event` row. |
| Where do I add a permission? | `identity/api/Permission.java`, the `user_grant_permission_check` constraint in a **new** migration (see V31 for how two codes were added), then `@PreAuthorize` on the endpoint. |
| Where is SQL for table X? | `grep -rn "FROM x\b" backend/modules backend/app/src/main`. |
| How do I add a cross-module call? | Add a method to the callee's `api` interface, implement it in its `internal` package (usually a `Jdbc*` class), add the Maven dependency if the pom does not have it, run `ModuleBoundaryTest`. |

## Known limitations and documentation gaps

* Modules contain no tests of their own (`backend/modules/*/src` has only `main`). All 80 Java test files (tests and helpers) live in `backend/app/src/test` and are Testcontainers-based integration tests against real PostgreSQL and S3 emulator, plus a few unit tests; there is no module-level unit-test isolation.
* `docs/engineering/01-architecture.md` describes ADR-001 as the module rule; the executable form is `ModuleRules.java`. If they diverge, the test wins.
