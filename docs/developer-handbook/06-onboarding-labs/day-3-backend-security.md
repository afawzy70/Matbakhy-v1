# Day 3 - Backend, database and security

> **Owner role:** Backend architecture
> **Status:** REFERENCE — lab for tracing a request through the backend and understanding authorization and data rules.

## Objectives

- Navigate the modular monolith: `backend/app`, `backend/shared`, `backend/modules/*`.
- Trace one request from the controller to the database and back.
- Understand authentication (OIDC via Keycloak), object-level authorization, idempotency and immutable snapshots.

## Reading list

1. [System overview](../03-architecture/system-overview.md), [backend architecture](../03-architecture/backend-architecture.md), [database architecture](../03-architecture/database-architecture.md), [identity and security](../03-architecture/identity-and-security.md), [integration and events](../03-architecture/integration-and-events.md).
2. [Backend module guide](../04-codebase/backend-module-guide.md), [order implementation](../04-codebase/order-implementation.md), [pricing implementation](../04-codebase/pricing-implementation.md), [payment implementation](../04-codebase/payment-implementation.md), [API contracts](../04-codebase/api-contracts.md).
3. [Authentication and MFA](../02-user-journeys/authentication-and-mfa.md); canonical: [security policy](../../engineering/04-security.md), [MFA policy](../../security/mfa-policy.md).

## Hands-on tasks

1. Module map. List `backend/modules`. For `orders`, open the three packages: `web` (controllers such as `backend/modules/orders/src/main/java/com/matbakhy/orders/web/OrderController.java`), `api` (the only package other modules may use) and `internal` (such as `backend/modules/orders/src/main/java/com/matbakhy/orders/internal/OrderService.java`). Then read `backend/app/src/test/java/com/matbakhy/app/architecture/ModuleBoundaryTest.java` and explain the rules it enforces.
2. Trace the quote request. Find the operation in `contracts/openapi.yaml` (search `quote`), then the controller `backend/modules/orders/src/main/java/com/matbakhy/orders/web/QuoteController.java`, then `backend/modules/orders/src/main/java/com/matbakhy/orders/internal/QuoteService.java`. Write the call chain, the tables read (follow the SQL or repository calls), and where rule BR-020 (quote lasts 5 minutes, reserves no stock) is enforced.
3. Trace order placement the same way through `OrderController` and `OrderService`. Find where idempotency is checked (start in `backend/app/src/main/java/com/matbakhy/app/idempotency`), where capacity is reserved transactionally, and where the immutable snapshot is written.
4. Authorization. Read `backend/app/src/main/java/com/matbakhy/app/security` and a test such as those under `backend/app/src/test/java/com/matbakhy/app/security`. Answer: how does the code stop customer B reading customer A's order? Which test proves it? (Run it with `cd backend && ./mvnw -B -ntp verify -Dtest=<TestClass> -Dsurefire.failIfNoSpecifiedTests=false`; Docker must be running.)
5. Database. In psql (see [run-and-debug](../05-developer-workflow/run-and-debug.md)) list tables, then read the first migrations in `backend/app/src/main/resources/db/migration`. Find a table that is append-only and the trigger that enforces it. Check which migration annotations exist with `python3 scripts/check_migration_compat.py --list`.
6. Keycloak. Open the local admin console, find realm `matbakhy`, clients (`matbakhy-web`, `matbakhy-mobile`) and the four synthetic users. Explain why the mobile client uses PKCE and no secret.
7. Failure drill: call `curl -i http://127.0.0.1:8080/api/v1/me` without a session and read the status and body shape; take the `requestId` from an error and find it in `docker compose ... logs api`.
8. Run one real integration test class from `backend/app/src/test/java/com/matbakhy/app/orders` and read its output in the `target/surefire-reports` directory that the Maven build creates under `backend/app/`.

## Deliverable

A call-chain note for "customer requests a quote" and "customer places an order": file, class, method names, tables touched, rules enforced (BR ids), tests that cover them. Then three bullet points on how you would add one new field to an order response (contract, migration, service, client regeneration, tests, docs).

## Self-check

- [ ] I can explain why modules may only depend on each other's `api` packages and which test enforces it.
- [ ] I can say where a price is computed and why a client-sent price is rejected.
- [ ] I can explain idempotency keys and what happens on a replay.
- [ ] I can explain object-level authorization with one concrete test.
- [ ] I know migrations are forward-only and what "expand-only" means for rollback.
- [ ] I know which environments may load development fixtures (`EnvironmentGuard`).

Next: [Day 4](day-4-development.md).
