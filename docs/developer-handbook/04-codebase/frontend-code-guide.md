# Frontend code guide (customer web and operations web)

> **Owner role:** Frontend and mobile
> **Status:** IMPLEMENTED — the two React/Vite web apps as they exist in `apps/`
> **Language / اللغة:** English · [العربية](frontend-code-guide-ar.md)

Two web apps share the same stack and conventions: React 19, TypeScript 6, Vite 8, Vitest, the typed client
`@matbakhy/api-client`, brand tokens from `@matbakhy/design-tokens`, Cairo font, Arabic RTL default with English LTR
(versions from `apps/customer-web/package.json` and `apps/operations-web/package.json`).
Architecture view: [frontend architecture](../03-architecture/frontend-architecture.md). User flows:
[customer web journey](../02-user-journeys/customer-web.md), [cook operations](../02-user-journeys/cook-operations.md),
[backoffice admin](../02-user-journeys/backoffice-admin.md).

## Shared conventions

- The UI renders server decisions. It never computes the authoritative price, capacity, role or allowed transition. A
  client-side subtotal is labelled an estimate and replaced by the server quote (see [pricing implementation](pricing-implementation.md)).
- Authentication is the browser session: an HttpOnly cookie (`MATBAKHY_SESSION`) set by the API after the OIDC login, plus a
  readable `XSRF-TOKEN` cookie that every non-GET request echoes in `X-XSRF-TOKEN`. See `apps/customer-web/src/api.ts:21-41`
  (`createApi`). The operations app has the same wrapper in `apps/operations-web/src/api.ts`.
- Every request sets `Accept-Language` from the chosen language (set in the same event as the state change, MBQA-001).
- There is no client router library: `apps/customer-web/src/App.tsx` reads the URL hash and matches with regular expressions
  (`/meal/<uuid>`, `/orders/<uuid>`, `/kitchen/<slug>`, `/support/<uuid>`, `/checkout`, `/orders`, `/account`, `/`).
- Strings live in `i18n.ts` per app (Arabic and English tables); money is formatted from integer minor units by
  `formatMoney` (`apps/customer-web/src/api.ts:64`) and parsed by `packages/api-client/src/money.ts`.
- Idempotency keys: `newIdempotencyKey()` (`apps/customer-web/src/api.ts:54`) and `crypto.randomUUID()` for `clientRequestId`.

## Customer web — `apps/customer-web/`

| File | Responsibility |
|---|---|
| `src/main.tsx` | Mounts `App` in `StrictMode`; imports fonts, `@matbakhy/design-tokens/tokens.css`, `app.css` |
| `src/App.tsx` | Shell, language/direction, hash routing, session probe (`GET /api/v1/me`), navigation, support case route |
| `src/Browse.tsx` | Discovery: search, zone, price ceiling, sort, server-side paging (`GET /api/v1/meals`) |
| `src/Meal.tsx`, `src/Kitchen.tsx` | Meal detail with slots; public kitchen page |
| `src/cart.ts` | Basket persisted in `localStorage` (`matbakhy.cart`) and one-kitchen-one-slot rule UI (server re-checks) |
| `src/Checkout.tsx` | Phone verification at checkout (`/api/v1/me/phone/challenges`), quote request, price-change review, placement with stable keys, recovery by `clientRequestId` (see [order implementation](order-implementation.md)) |
| `src/Payment.tsx` | Deposit instructions and "I sent it" report; honest wording (customer report is not payment) |
| `src/Orders.tsx` | `OrderList`, `OrderDetail` (timeline, handover code, cancellation, support entry) and `ReviewForm` |
| `src/Account.tsx` | Profile, favorites, notification inbox, support cases (`SupportCaseDetail`), addresses, deletion request |
| `src/Security.tsx` | Two-step verification preference and step-up link (`/oauth2/authorization/keycloak?stepUp=true`) |
| `src/activity.ts` | Anonymous, deduplicated browsing activity (`POST /api/v1/activity`) |
| `src/EnvironmentNotice.tsx` | Banner for synthetic/preview environments |
| `src/App.test.tsx` | Vitest + Testing Library tests with a fake `fetch` (route table `"METHOD /path": () => response`) |

Placement details worth knowing: the pending placement `{clientRequestId, idempotencyKey, quoteId, terms, cancellation}` is
written to `localStorage` (`matbakhy.pendingPlacement`) before the request is sent, so reload or retry reuses the same keys
(`apps/customer-web/src/Checkout.tsx:263-299`). A `PRICE_CHANGED` answer discards the pending keys, re-quotes and shows the
difference computed by `priceChanges()` from `packages/api-client/src/priceChanges.ts`; it never resubmits silently.

## Operations web — `apps/operations-web/`

One app serves both the **cook workspace** and the **staff backoffice**. `src/App.tsx` decides what to show by probing the
API once after sign-in (`checkSession`, `src/App.tsx:~55-100`): `GET /api/v1/cook/dashboard` (a 403 means no approved
kitchen), `GET /api/v1/ops/cooks`, `/ops/catalogue`, `/ops/cases`, `/ops/orders`, `/ops/finance/statements` (a 404 for an
unknown kitchen means the caller has finance access), `/ops/configuration`, `/ops/staff`, `/me/security`. The visible tabs
are exactly what the server allowed (`allowedTabs`). Hiding a tab is cosmetic; the authority is the API.

| Area | Files |
|---|---|
| Cook | `src/cook/Dashboard.tsx`, `Orders.tsx` (accept/reject/prepare/ready/dispatch/handover/cancel/cash report), `Meals.tsx` (CRUD, photo upload, price history), `Slots.tsx`, `Payments.tsx` (deposit receipt, problem, refund, payment profile), `Statement.tsx`, `Insights.tsx`, `Settings.tsx`, `Onboarding.tsx` (application), shared helpers `shared.tsx` |
| Staff | `src/ops/Reviews.tsx` (cook applications, moderation of reviews), `Catalogue.tsx`, `Cases.tsx`, `OpsOrders.tsx`, `Finance.tsx` (cash verification, refunds, settlements, statements), `Payments.tsx` (deposit reconciliation queue and recipient approval), `Configuration.tsx`, `Staff.tsx`, `Security.tsx` (MFA policy and reset) |
| Step-up | when the server answers `STEP_UP_REQUIRED` the app shows a step-up screen instead of data |
| Tests | `src/App.test.tsx` (Vitest); Playwright journeys in `apps/operations-web/e2e/` (`cook-workspace.e2e.ts`, `customer-journey.e2e.ts`, `keyboard.e2e.ts`, `money-reflow.e2e.ts`, `operations-layout.e2e.ts`), config `apps/operations-web/playwright.config.ts`; staging smoke `apps/operations-web/e2e-staging/smoke.staging.ts` |

The Playwright journeys also exercise the customer web (`customer-journey.e2e.ts`), which is why they live here. They run
against a real local stack started by `scripts/dev.sh` (see [run and debug](../05-developer-workflow/run-and-debug.md)).

## Where to implement

- New customer screen: component under `apps/customer-web/src/`, route regexp in `App.tsx`, strings in `i18n.ts` (both languages), test in `App.test.tsx`.
- New cook/staff section: component under `apps/operations-web/src/cook/` or `ops/`, probe + tab in `App.tsx`, strings in `i18n.ts`.
- A field you need is missing from a response: change the backend and `contracts/openapi.yaml` first, regenerate the client ([API contracts](api-contracts.md)); do not cast around the generated types.
- Styling: tokens from `packages/design-tokens/tokens.json` (see [shared libraries](shared-libraries.md)); `scripts/check_design.sh` guards the brand.

## Known limitations and documentation gaps

- Accessibility conformance is not complete: TC-047 (keyboard/focus/screen reader) is BLOCKED and MB-029 is BLOCKED in `planning/backlog.json`; a keyboard Playwright test exists (`apps/operations-web/e2e/keyboard.e2e.ts`) but screen-reader verification is not done.
- Line ranges quoted for `apps/operations-web/src/App.tsx` are approximate (`~`); locate `checkSession` by name.
