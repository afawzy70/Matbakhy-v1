# Shared libraries

> **Owner role:** Frontend and mobile
> **Status:** IMPLEMENTED — two workspace packages and one backend shared module are in use by every application
> **Language / اللغة:** English · [العربية](shared-libraries-ar.md)

Code reused across applications. Everything here is `private` (never published to a registry) and consumed by `workspace:*`
dependencies (`pnpm-workspace.yaml` lists `apps/*` and `packages/*`).

## `packages/api-client` — `@matbakhy/api-client`

| Item | Detail |
|---|---|
| Purpose | One typed client for the API contract, used by `apps/customer-web`, `apps/operations-web` and `apps/customer-mobile` |
| Entry | `packages/api-client/src/index.ts`: `createApiClient(options)` (wraps `openapi-fetch`), type re-exports (`components`, `operations`, `paths`, `ApiError`), `parseMoney`, `MalformedMoneyError`, `priceChanges` |
| Generated code | `packages/api-client/src/generated/schema.ts` from `contracts/openapi.yaml` (`pnpm --filter @matbakhy/api-client run generate`); freshness checked by `check:drift` |
| Money helper | `packages/api-client/src/money.ts` — `parseMoney` accepts exactly `{amountMinor, currency}` with a safe-integer amount and currency `EGP`; rejects decimals, numeric strings and extra fields |
| Price-change helper | `packages/api-client/src/priceChanges.ts` — compares two quotes for the D13 reconfirmation notice ([pricing implementation](pricing-implementation.md)) |
| Tests | `packages/api-client/test/client.test.ts` (typed paths compile/fail), `money.test.ts` (TC-107), `priceChanges.test.ts`, `registry.test.ts` (contract vs registry vs `ErrorCode`) |
| Authentication | Not in this package: each app builds its own fetch settings (cookie + CSRF for web, bearer for mobile) and passes them as `ClientOptions` |

Change rules: do not hand-edit `schema.ts`; change `contracts/openapi.yaml`, regenerate and commit both. See [API contracts](api-contracts.md).

## `packages/design-tokens` — `@matbakhy/design-tokens`

| Item | Detail |
|---|---|
| Purpose | Brand name/slogan, colors (light and a derived dark draft), typography (Cairo) and spacing in one JSON source; direction helper |
| Source of truth | `packages/design-tokens/tokens.json` (status `DRAFT_PENDING_OWNER_APPROVAL`; owner decision O05 on final brand approval is open) |
| Exports | `packages/design-tokens/src/index.ts`: `brand`, `tokens`, `direction(locale)`, `contrastRatio`, `luminance`, `renderCss`, `wordmarkSvg`; CSS file `@matbakhy/design-tokens/tokens.css` generated into `packages/design-tokens/src/generated/tokens.css` by `pnpm --filter @matbakhy/design-tokens run generate` (the generated file is committed) |
| Consumers | web `main.tsx` imports `@matbakhy/design-tokens/tokens.css`; mobile reads token values in `apps/customer-mobile/src/ui.tsx` |
| Tests | `packages/design-tokens/test/tokens.test.ts` (TC-103: contrast pairs meet WCAG ratios in both themes, committed `tokens.css` equals a fresh render, 44px touch target, apps do not hard-code the brand name or hex colors) |
| Gate | `scripts/check_design.sh` (token tests, brand specimens, prototype journeys in Arabic/English; visual approval itself is recorded separately in `docs/design/approval.md`) |
| Visual rules | `docs/design/approved/DESIGN.md` (terracotta + Cairo direction owner-selected 2026-10-11) |

## Backend `shared` module

`backend/shared` (Maven artifact `matbakhy-shared`) holds the only code every backend module may use without an `api` package:
`Money`, `ApiException`/`ErrorCode`, `IdempotentCommands`, `PersonalDataEraser`, `RuntimeEnvironment`, `RequestContext`, `Languages`, `DomainModule`.
Details: [backend module guide](backend-module-guide.md). It must not depend on any domain module or on `app` (rule `sharedDependsOnNoModuleOrApp` in `ModuleRules.java`).

## Sharing across web and mobile: what is and is not shared

| Shared | Not shared |
|---|---|
| API types and client, money parsing, price-change comparison, brand/design tokens | UI components (web uses DOM React, mobile uses React Native), translation tables (each app has its own `i18n.ts`), session handling (cookie vs secure-store tokens) |

ADR-002 (`docs/engineering/01-architecture.md`) says web and native should share "validation primitives and translation keys"; in the code read for this page translation keys are **not** shared (three separate `i18n.ts` files).

## Known limitations and documentation gaps

- Translation keys are duplicated per app, contradicting the intent in ADR-002; there is no shared i18n package.
