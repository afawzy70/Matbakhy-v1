# System overview

> **Owner role:** Backend architecture
> **Status:** PARTIALLY IMPLEMENTED — core ordering platform runs on a synthetic-data preview; real providers, push, live money and production deployment do not exist

This page is the map. It names every deployable piece, what talks to what, which integrations are real and which are not. Details are in the sibling pages; business meaning is in [marketplace model](../01-business/marketplace-model.md).

## What the system is

Matbakhy is a home-cooked-food marketplace for Egypt (EGP, `Africa/Cairo`). Customers browse kitchens and meals, request a quote, place an order for one kitchen and one time slot, and the cook accepts and fulfils it. The platform does not take a commission and does not custody money in the MVP; deposits are paid directly to the kitchen and only *reported* in the system (see [pricing and payments](../01-business/pricing-and-payments.md)).

It is a **modular monolith**: one Spring Boot process, one PostgreSQL database, three clients that all use the same HTTP API. See diagrams 1 and 2 in [diagrams](diagrams.md#1-system-context).

## Containers

| Container | Technology | Source | State |
|---|---|---|---|
| Customer web | React 19, Vite 8, TypeScript, hash routing | `apps/customer-web` | IMPLEMENTED |
| Operations web (cook workspace and staff back office in one app) | React 19, Vite 8 | `apps/operations-web` | IMPLEMENTED |
| Customer mobile (Android and iOS) | Expo 57 / React Native 0.86, React Navigation | `apps/customer-mobile` | PARTIALLY IMPLEMENTED (customer app only; no cook or staff mobile app; native verification evidence is CI emulator/simulator runs, see [mobile architecture](mobile-architecture.md)) |
| Backend API | Spring Boot 4.1.1, Java 21, Maven multi-module | `backend/` | IMPLEMENTED |
| Database | PostgreSQL 16.15, Flyway migrations V1 to V31 | `backend/app/src/main/resources/db/migration` | IMPLEMENTED |
| Identity provider | Keycloak 26.8.0, realm `matbakhy` | `infra/identity/matbakhy-realm.json`, `deploy/web-preview/identity/matbakhy-realm.preview.json` | IMPLEMENTED (config-as-code; live realm changes are applied by scripts, see [identity and security](identity-and-security.md)) |
| Object storage | S3 API; Adobe s3mock locally, SeaweedFS on the preview | `infra/compose.yaml`, `deploy/web-preview/compose.yaml`, `backend/app/src/main/java/com/matbakhy/app/providers/S3ObjectStorage.java` | IMPLEMENTED for local/preview; no production provider chosen |
| Reverse proxy | Caddy 2.10 inside the "front" image | `deploy/web-preview/Caddyfile`, `deploy/web-preview/front.Dockerfile` | IMPLEMENTED (preview only) |
| Monitoring and logging | Prometheus, Alertmanager, Loki, Alloy, node-exporter, Grafana (optional) | `deploy/web-preview/observability` | IMPLEMENTED on the preview stack; not a production monitoring design |
| Shared TypeScript packages | `@matbakhy/api-client` (generated from OpenAPI), `@matbakhy/design-tokens` | `packages/api-client`, `packages/design-tokens` | IMPLEMENTED |

There is **no** message broker, cache server, search engine or separate worker process. Background work (order expiry, outbox dispatch, notification delivery, ledger posting, media cleanup, account erasure) runs as `@Scheduled` jobs inside the API process and coordinates through database locks. See [integration and events](integration-and-events.md).

## Contract between clients and server

`contracts/openapi.yaml` is the API contract; `contracts/endpoints.json` is the endpoint registry used by checks. `packages/api-client/src/generated/schema.ts` is generated from the OpenAPI file (`pnpm --filter @matbakhy/api-client run generate`), and `pnpm contract:check` lints the contract and fails on generator drift. All three clients import that package, so a contract change shows up as a TypeScript error in the apps. More in [api contracts](../04-codebase/api-contracts.md).

## Who is authoritative for what

| Concern | Authority | Where |
|---|---|---|
| Prices, totals, delivery fee, tax | Server, recomputed at quote and again at placement | `backend/modules/orders/src/main/java/com/matbakhy/orders/internal/QuoteService.java` |
| Capacity of a slot / meal | Server, transactional counters with row locks | `backend/modules/availability/src/main/java/com/matbakhy/availability/internal/JdbcSlotInventory.java` |
| Roles and permissions | Server database (`user_grant`, kitchen ownership); token role claims are ignored | `backend/app/src/main/java/com/matbakhy/app/security/AccountMapper.java` |
| Order state transitions | Server; terminal states also frozen by a database trigger | `backend/modules/orders/src/main/java/com/matbakhy/orders/internal/OrderLifecycleService.java` |
| MFA requirement | Server database policy plus Keycloak assertions | `backend/app/src/main/java/com/matbakhy/app/mfa/MfaPolicyService.java` |
| UI text and RTL layout | Clients (Arabic RTL default, English LTR) | `apps/*/src/i18n.ts`, `packages/design-tokens` |

Clients show server values; the basket's remembered unit price is an estimate only (`apps/customer-web/src/cart.ts`).

## External integrations: what really exists

| Integration | Reality | Evidence |
|---|---|---|
| Keycloak (OIDC) | IMPLEMENTED | `backend/app/src/main/java/com/matbakhy/app/security/SecurityConfiguration.java` |
| Keycloak Admin REST (MFA reset, end sessions) | IMPLEMENTED; needs the `matbakhy-admin` service-account secret, otherwise those two operations answer 503 | `backend/app/src/main/java/com/matbakhy/app/mfa/KeycloakAdmin.java`, `docs/security/mfa-policy.md` |
| S3-compatible object storage | IMPLEMENTED (presigned upload/read) | `backend/modules/catalog/src/main/java/com/matbakhy/catalog/api/ObjectStorage.java` |
| Malware scanner | DISABLED outside LOCAL/CI/PREVIEW: the only implementation is a fake; any other environment refuses to start | `backend/app/src/main/java/com/matbakhy/app/providers/FakeMalwareScanner.java`, `ProviderConfiguration.malwareScanner` |
| SMS phone verification | PLANNED: only `FakePhoneVerificationSender`; non-synthetic environments throw at startup | `backend/app/src/main/java/com/matbakhy/app/providers/FakePhoneVerificationSender.java` |
| Email notifications | PLANNED: only a logging adapter | `backend/app/src/main/java/com/matbakhy/app/providers/LoggingNotificationChannels.java` |
| Push notifications | DISABLED: flag `PUSH_DELIVERY_ENABLED` defaults to false; even when true only a logging adapter exists; the mobile app asks for the OS permission but does not register a device token | `backend/modules/notifications/src/main/java/com/matbakhy/notifications/internal/OutboxDispatcher.java`, `apps/customer-mobile/src/notificationPermission.ts` |
| Payment provider / bank API / InstaPay | NOT INTEGRATED. Deposits are manual transfers customer-to-kitchen, reported by the parties | `backend/modules/orders/src/main/java/com/matbakhy/orders/internal/PaymentService.java` |
| Maps / geocoding | NOT VERIFIED (addresses hold optional latitude and longitude columns, no provider call was found) | `backend/app/src/main/resources/db/migration/V21__personal_data_erasure.sql` |
| Paid ads, commission billing | Out of MVP scope; commission must be zero | `backend/app/src/main/resources/db/migration/V23__launch_commission.sql` |

## Environments

| `APP_ENV` | Meaning | Providers | Where defined |
|---|---|---|---|
| `LOCAL` | Developer machine via `scripts/dev.sh up` and `infra/compose.yaml` | fakes, development-only keys allowed, `dev` profile adds synthetic migrations | `backend/app/src/main/java/com/matbakhy/app/config/MatbakhyProperties.java` |
| `CI` | Test runs | same as LOCAL | same |
| `PREVIEW` | The access-restricted web preview with synthetic data | fakes allowed, secrets must be real, demo notice shown | `deploy/web-preview/compose.yaml` |
| `STAGING`, `PRODUCTION` | Declared in the enum; the application refuses to start without real providers | none exist | `backend/app/src/main/java/com/matbakhy/app/providers/ProviderConfiguration.java` |

So today the only deployed environment is the preview. No production environment, real money, real SMS/email/push or app-store release exists. Treat any statement of "staging" in older documents as the preview; see [infrastructure and deployment](infrastructure-and-deployment.md).

## Cross-cutting behaviour worth knowing early

* **Request ID.** `RequestIdFilter` accepts a well-formed `X-Request-Id` or generates one, puts it into the log MDC, error bodies and the response header, and stores it on outbox events so asynchronous work can be correlated.
* **Errors.** One error body for everything (`ApiError`) with stable `ErrorCode` values such as `PRICE_CHANGED` or `STEP_UP_REQUIRED`; clients localise the code. See [backend architecture](backend-architecture.md#validation-and-error-handling).
* **Idempotency.** Mutating commands take an `Idempotency-Key` header and are replay-safe for 24 hours; order placement additionally has a permanent unique `(customer_id, client_request_id)`.
* **Time.** Instants are UTC in the database; business scheduling and display use `Africa/Cairo`.
* **Money.** `bigint` piastres everywhere (`*_minor` columns, `Money` in `backend/shared`); no floating point.
* **Append-only history.** Orders' events, audit, ledger, meal revisions and config changes are protected by database triggers and by revoking `UPDATE`/`DELETE` from the runtime database role (`backend/app/src/main/resources/db/migration/afterMigrate.sql`).

## Where to read next

* Source layout: [repository map](../04-codebase/repository-map.md).
* Backend internals: [backend architecture](backend-architecture.md); data: [database architecture](database-architecture.md).
* Auth and MFA: [identity and security](identity-and-security.md).
* Running it: [local environment](../05-developer-workflow/local-environment.md).
* Older architecture decisions (ADR-001 to ADR-005): `docs/engineering/01-architecture.md`.

## Known limitations and documentation gaps

* `.github/workflows/deploy-preview.yml` is titled "Deploy Matbakhy preview to Contabo" and the E2E workflow also says Contabo, while `docs/operations/09-staging-hetzner-preparation.md` and `docs/operations/12-owner-server-setup.md` describe a Hetzner server. The workflows (which deploy over SSH to a host taken from repository secrets, with the domain built into the workflow) are the executable truth; which provider hosts the preview is NOT VERIFIED from the repository.
* `STAGING` and `PRODUCTION` exist as enum values and as fail-fast guards, but nothing deploys to them.
