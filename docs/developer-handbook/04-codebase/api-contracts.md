# API contracts

> **Owner role:** Backend architecture
> **Status:** IMPLEMENTED — contract toolchain, registry and drift tests exist; 113 of 116 registered routes are served, 3 are designed only
> **Language / اللغة:** English · [العربية](api-contracts-ar.md)

One REST API (`/api/v1/...`), described twice on purpose: a typed OpenAPI document and a flat route registry. Both live in
`contracts/`. The three apps consume a client generated from the OpenAPI file. Background: `docs/engineering/03-api-contract.md`
(context only; this page describes what the code and tests enforce).

## Files

| File | Role |
|---|---|
| `contracts/openapi.yaml` | OpenAPI 3.1.1 document: paths, schemas, shared parameters (`Cursor`, `Limit`, `IdempotencyKey`), the standard `Error` response, security schemes `sessionCookie` (cookie `MATBAKHY_SESSION`) and `bearerAuth` (JWT). Every operation carries `x-matbakhy-id`, `x-permission`, `x-implemented` |
| `contracts/endpoints.json` | Registry: `API-001`..`API-116` with `method`, `path`, `permission`, `response_type`, a one-line `contract` and `implemented` |
| `packages/api-client/src/generated/schema.ts` | **Generated** by `openapi-typescript` from the YAML; committed; never edit by hand |
| `packages/api-client/src/index.ts` | `createApiClient(options)` — `openapi-fetch` client typed with the generated `paths` |
| `packages/api-client/redocly.yaml` | Lint rules (`recommended-strict`, `operation-summary`, `security-defined`, `no-unused-components` as errors) |

## What fails when they disagree

| Check | Command or test | Fails when |
|---|---|---|
| Registry vs OpenAPI | `packages/api-client/test/registry.test.ts` (`pnpm --filter @matbakhy/api-client test`) | an operation is missing or extra; `x-matbakhy-id`, `x-permission` or `x-implemented` differ from the registry; a public operation has security or a protected one lacks session/bearer security; an operation lacks the `4XX`/`5XX` standard error response; a typed object schema allows additional properties; the `ApiError.code` enum differs from `ErrorCode.java` |
| Registry vs running app | `backend/app/src/test/java/com/matbakhy/app/web/ContractRoutesTest.java` | the set of registry entries with `implemented: true` differs from the `/api/` routes Spring actually maps (path variables normalized) |
| Generated client freshness | `pnpm --filter @matbakhy/api-client run check:drift` (`packages/api-client/scripts/check-drift.mjs`) | `src/generated/schema.ts` differs from a fresh generation |
| OpenAPI lint | `pnpm --filter @matbakhy/api-client run lint:contract` | Redocly rule errors |
| Both of the last two | `pnpm contract:check` (root `package.json`; called by `scripts/check_workspace.sh`) | either fails |
| Response shapes | `packages/api-client/test/contract.ts` compiles every schema with Ajv for tests | an uncompilable schema |

Not machine-checked (see gaps): that `x-permission` matches the `@PreAuthorize` on the controller, and that a response body
produced by the backend validates against its schema.

## Conventions every endpoint follows

- Errors: JSON `ApiError` with a stable `code` from `backend/shared/src/main/java/com/matbakhy/shared/error/ErrorCode.java`
  (`MALFORMED_REQUEST`, `UNAUTHENTICATED`, `FORBIDDEN`, `STEP_UP_REQUIRED`, `NOT_FOUND`, `VALIDATION_FAILED`, `STATE_CONFLICT`,
  `VERSION_CONFLICT`, `IDEMPOTENCY_CONFLICT`, `STOCK_CHANGED`, `PRICE_CHANGED`, `QUOTE_EXPIRED`, `SLOT_CLOSED`, ...), the HTTP status is
  fixed per code, and the `X-Request-Id` header correlates with logs. Handler: `backend/app/src/main/java/com/matbakhy/app/web/ApiExceptionHandler.java`.
- Strict JSON: unknown properties are rejected (`backend/app/src/main/java/com/matbakhy/app/config/StrictJsonConfiguration.java`, `StrictJsonIntegrationTest`).
- Money: `{ "amountMinor": <integer piastres>, "currency": "EGP" }`; parsed on the client by `packages/api-client/src/money.ts`.
- Idempotent commands take an `Idempotency-Key` header (8-128 chars `[A-Za-z0-9-]`); optimistic concurrency uses `expectedVersion` in
  the body and answers `VERSION_CONFLICT`.
- Lists use opaque `cursor` + `limit` (1-100, default 20).
- Language: `Accept-Language` selects Arabic (default) or English content.
- Authentication: web = session cookie + `X-XSRF-TOKEN` on mutations; native = `Authorization: Bearer`. Public routes are the `GET`
  list in `SecurityConfiguration.PUBLIC_GET` plus `POST /api/v1/activity`.

## Permission vocabulary in the contract

`x-permission` values describe the access rule, for example `PUBLIC`, `SELF`, `CUSTOMER`, `ORDER_OWNER`, `COOK`, `COOK_OWNER`,
`COOK_APPLICANT`, `CASE_OWNER`, and staff scopes `COOK_REVIEW`, `CONTENT_MODERATE`, `ORDER_OPS`, `SUPPORT`, `FINANCE`,
`FINANCE_VERIFY`, `FINANCE_REFUND`, `FINANCE_SETTLEMENT`, `CONFIG_READ`, `CONFIG_WRITE_SCOPED`, `STAFF_GRANT`,
`AUDIT_READ_SCOPED`, `INTEGRATION_OPS`, `SECURITY_MFA_POLICY`, `SECURITY_MFA_RESET`. Staff scopes map to authorities named
`PERM_<code>` (enum `Permission.java`, for example `PERM_finance.verify`). Owner scopes (`ORDER_OWNER`, `COOK_OWNER`) are enforced in
service SQL, not by annotation.

## Not implemented routes

| Id | Route | Registry state |
|---|---|---|
| API-008 | `GET /api/v1/policies/{key}` | `implemented: false` (the path is allow-listed as public in `SecurityConfiguration` but no controller serves it) |
| API-066 | `GET /api/v1/ops/dashboard` | `implemented: false` |
| API-095 | `GET /api/v1/ops/audit/export` | `implemented: false` |

Schemas marked `x-schema-status: PENDING` (`OpsDashboard`, `PolicyDocument` and one more) are untyped placeholders owned by those stories.

## Workflow: add or change an endpoint

1. Write the failing integration test in `backend/app/src/test/java/com/matbakhy/app/<area>/`.
2. Implement controller (`web/`), service (`internal/`) and, if needed, a migration (`V32__...sql`).
3. Add or edit the operation in `contracts/openapi.yaml` (summary, `x-matbakhy-id`, `x-permission`, `x-implemented: true`, standard `4XX/5XX`).
4. Add or edit the entry in `contracts/endpoints.json` with the same id, permission and `implemented`.
5. Regenerate: `pnpm --filter @matbakhy/api-client run generate`, commit `packages/api-client/src/generated/schema.ts`.
6. Run `pnpm contract:check`, `pnpm --filter @matbakhy/api-client test`, and the backend `ContractRoutesTest`.
7. Update the handbook per [documentation policy](../05-developer-workflow/documentation-policy.md).

OIDC routes (`/oauth2/authorization/**`, `/login/oauth2/code/**`, `/api/v1/auth/logout`) are framework-managed and are **not**
in the registry (stated in the registry's own `note`).

## Known limitations and documentation gaps

- `contracts/endpoints.json` still says `"status": "DESIGN_NOT_IMPLEMENTED"` although 113 routes are served; the file-level status is stale.
- `x-permission` is documentation only; a wrong value would not fail any test. Authorization correctness is covered by integration tests (for example `StaffAccessIntegrationTest`), not by the contract.
- The OpenAPI description says the file was "generated from contracts/endpoints.json in MB-007 and maintained by hand since"; the two files are now kept in sync by tests, not by a generator.
