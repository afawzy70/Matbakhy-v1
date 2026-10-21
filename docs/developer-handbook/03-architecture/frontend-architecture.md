# Frontend architecture (customer web and operations web)

> **Owner role:** Frontend and mobile
> **Status:** IMPLEMENTED — both web apps run against the API; the Content Security Policy is Report-Only, not enforced
> **Language / اللغة:** English · [العربية](frontend-architecture-ar.md)

Two single-page apps share one stack and two shared packages. The mobile app is covered in [mobile architecture](mobile-architecture.md); a file-by-file reading guide is [frontend code guide](../04-codebase/frontend-code-guide.md). Diagram: [web architecture](diagrams.md#4-web-architecture).

## Stack

| Item | Value | Source |
|---|---|---|
| Language / UI | TypeScript 6.0.3, React 19.2.3 | `apps/customer-web/package.json` |
| Bundler / dev server | Vite 8.3.4, `@vitejs/plugin-react`; customer web on port 5173, operations web on 5174 (the API's default `WEB_ALLOWED_ORIGINS` lists both) | `apps/customer-web/vite.config.ts`, `backend/app/src/main/resources/application.yaml` |
| Tests | Vitest 5 with jsdom and Testing Library; operations web adds Playwright (`apps/operations-web/e2e`, `apps/operations-web/e2e-staging`) with axe accessibility checks | `apps/operations-web/package.json` |
| Fonts | Cairo (Arabic and Latin subsets imported in `main.tsx`) | `apps/customer-web/src/main.tsx` |
| Router | **None.** Customer web uses `window.location.hash` and a `hashchange` listener; operations web uses a tab state, not URLs | `apps/customer-web/src/App.tsx` |
| State management | React state and hooks only; no Redux/Zustand/React Query | `apps/customer-web/src/shared.tsx` (`useServer`) |
| i18n | Hand-written typed string tables `ar` and `en` in `i18n.ts`; no i18n library | `apps/customer-web/src/i18n.ts`, `apps/operations-web/src/i18n.ts` |

Commands (from the repository root, Node >= 24.21 and pnpm 10.34.6 per `package.json`): `pnpm install --frozen-lockfile`, `pnpm --filter @matbakhy/customer-web dev`, `pnpm --filter @matbakhy/operations-web dev`, `pnpm typecheck`, `pnpm lint`, `pnpm test`, `pnpm build`. See [run and debug](../05-developer-workflow/run-and-debug.md).

## Shared packages

* **`packages/api-client`** (`@matbakhy/api-client`): `createApiClient(options)` wraps `openapi-fetch` typed with the generated `paths` from `packages/api-client/src/generated/schema.ts`. Also exports `parseMoney` (rejects decimals, strings and extra fields, only `EGP`) and `priceChanges(previousQuote, nextQuote)`, which explains a price change as meal / delivery-fee / tax deltas so the UI never blames the kitchen for a platform fee. The schema is generated from `contracts/openapi.yaml`; `pnpm contract:check` fails on drift.
* **`packages/design-tokens`**: `tokens.json` is the single source of brand name, palette and typography; `packages/design-tokens/scripts/generate-css.mjs` writes `src/generated/tokens.css` (imported by both apps); `direction(locale)` returns `rtl` for Arabic. Apps read the brand name from here (`brand.name[locale]`), and `vite.config.ts` injects it into `index.html`. Status in `package.json`: draft pending owner approval O05 (the approved visual direction is in `docs/design/approved/DESIGN.md`).

## API access layer

Each app has its own `src/api.ts` with `createApi()`, nearly identical in the two apps:

* Base URL from `VITE_API_BASE_URL` (default `http://localhost:8080`). In the preview image it is fixed at build time to `https://api.<preview domain>` (`deploy/web-preview/front.Dockerfile`).
* `credentials: "include"`: authentication is the **HttpOnly session cookie** `MATBAKHY_SESSION`; the app never sees or stores a token.
* A request middleware sets `Accept-Language` from an internal variable updated by `setLanguage` **in the same event handler** that changes the locale (not from `document.lang`, which updates after render; this fixed QA finding MBQA-001), and copies the readable `XSRF-TOKEN` cookie into `X-XSRF-TOKEN` on every non-GET request (CSRF double-submit; the API side is `csrf.spa()` in `SecurityConfiguration.webChain`).
* Mutating calls pass an `Idempotency-Key` created by `newIdempotencyKey()` (`web-<uuid>` or `ops-<uuid>`).
* `errorCode(error)` returns the API's stable error code or `"NETWORK"`.
* Money display uses `Intl.NumberFormat` with `ar-EG` / `en-EG` and `EGP`, dividing integer piastres by 100 only at the display edge (`formatMoney`). Times are formatted in `Africa/Cairo`.

### Loading and errors

`useServer(ctx, load, key)` (`apps/customer-web/src/shared.tsx`) is the standard read hook: it returns `loading`, `error(code)` or `ok(data)`; a `401` calls `ctx.onSignedOut`; a thrown fetch becomes code `NETWORK`. `<Status>` renders the three states and shows a step-up link when the code is `STEP_UP_REQUIRED`. `useMutation` in the same file shows `errorText(t, code)` for failures. `errorText` maps API codes (`VERSION_CONFLICT`, `PRICE_CHANGED`, `QUOTE_EXPIRED`, `STEP_UP_REQUIRED` and so on) to Arabic and English sentences. The UI never computes authority: it renders server state, allowed actions (`allowedActions` on orders) and deadlines.

## Customer web (`apps/customer-web`)

Entry: `src/main.tsx` -> `src/App.tsx`.

| Hash route | Component | Notes |
|---|---|---|
| `/` | `Browse.tsx` | Discovery; intent tiles, filters, sort. Public. |
| `/meal/<uuid>` | `Meal.tsx` | Slot and quantity choice, add to basket. Keyed per meal. |
| `/kitchen/<slug>` | `Kitchen.tsx` | Public kitchen page |
| `/checkout` | `Checkout.tsx` | Mode (delivery/pickup), quote, price-change review, terms, placement, deposit choice (`Payment.tsx`) |
| `/orders`, `/orders/<uuid>` | `Orders.tsx` | List and detail with events, handover code, payment panel, support entry |
| `/account` | `Account.tsx` | Profile, phone verification, addresses, deletion request, security (`Security.tsx`), support cases |
| `/support/<uuid>` | `SupportCaseDetail` in `Account.tsx` | Case thread |

Behaviours worth knowing:

* **Browsing is public**; ordering needs a session and a verified phone (server returns `CONTACT_VERIFICATION_REQUIRED`). `App.tsx` calls `GET /api/v1/me` on load to learn whether a session exists.
* **Basket** (`src/cart.ts`): kept in `localStorage` under `matbakhy.cart`, one kitchen and one slot. Prices stored there are display estimates; the server quotes the authoritative total. A **pending placement** (`matbakhy.pendingPlacement`) stores `clientRequestId` and `idempotencyKey` *before* the request is sent, so a retry after a lost response can only find or repeat the same order (`GET /api/v1/orders/by-request/{clientRequestId}` exists for this).
* **Price change:** on `409 PRICE_CHANGED` at placement, `Checkout.tsx` re-quotes, shows `PriceChangeNotice` built from `priceChanges(...)`, and requires the customer to tick "accept the new total" before the new quote can be placed. Diagram: [price change](diagrams.md#10-price-change-and-stale-quote-reconfirmation).
* **Sign-out** (MBQA-003): calls `POST /api/v1/auth/logout`, clears basket and pending placement, then navigates to the identity provider's end-session URL returned as `idpLogoutUrl`; warns first if an unconfirmed order exists. A `pageshow` handler reloads pages restored from the back-forward cache.
* **Organic activity** (`activity.ts`): fire-and-forget `POST /api/v1/activity` with a random per-tab visitor id; the server stores only a daily keyed hash.
* **Demo notice:** `EnvironmentNotice` shows when built with `VITE_DEMO_NOTICE=true` or when `GET /api/v1/configuration/public` says `environment.demo`.

## Operations web (`apps/operations-web`)

One app serves two audiences. After load, `checkSession()` in `src/App.tsx` calls `GET /api/v1/me` and then probes eight endpoints in parallel (cook dashboard, ops cooks, catalogue, cases, orders, finance statements with a nil kitchen id, configuration, staff) plus `GET /api/v1/me/security`. **Which tabs appear is decided by which probes the server allows** (`allowedTabs`), not by a client-side role: 200 means allowed; the finance probe uses the 404-versus-403 difference. If any probe answers `STEP_UP_REQUIRED` the app shows a step-up screen linking to `stepUpUrl(...)` instead of partial data.

| Audience | Tabs | Source folder |
|---|---|---|
| Cook (approved kitchen) | dashboard, orders, meals, slots, statement, insights, settings; payments inside orders and settings | `apps/operations-web/src/cook` |
| Staff (by grant) | reviews (cook applications), catalogue moderation, cases, ops orders, finance (including deposit payments), configuration, staff grants | `apps/operations-web/src/ops` |
| Everyone signed in | security (own MFA preference; policy and reset for admins) | `apps/operations-web/src/ops/Security.tsx` |

An applicant without a kitchen sees the onboarding screen (`cook/Onboarding.tsx`). The cook dashboard supplies `serverTime`, from which the app computes a clock offset (`serverOffsetMs`) so countdowns to `acceptBy` follow server time rather than the device clock.

### Differences between the two apps

| Aspect | Customer web | Operations web |
|---|---|---|
| Audience | Anonymous and signed-in customers | Cooks and staff only (proxy limits it to the staff network class on the preview) |
| Navigation | Hash routes, bottom nav | Tabs derived from server-permitted endpoints |
| Public pages | Yes (browse, meal, kitchen) | None; everything needs a session |
| Local storage | Basket, pending placement, `sessionStorage` visitor id | None found beyond React state (NOT VERIFIED exhaustively) |
| Idempotency key prefix | `web-` | `ops-` |
| Extra test layer | Vitest only | Vitest plus Playwright E2E and staging smoke |
| Hosts on the preview | `app.<domain>` | `ops.<domain>` |

## RTL and localisation

`index.html` ships `lang="ar" dir="rtl"`; `App.tsx` effects set `document.documentElement.lang` and `dir` from `direction(locale)` and the title from the brand tokens. Arabic is the default locale; a button toggles to English. Layout uses CSS (`app.css`) with tokens; component code does not hard-code left/right (NOT VERIFIED by a full CSS audit; visual RTL evidence is in the quality docs under `docs/quality`). Strings that need numbers use functions, for example `kitchenChangedPrice(title, before, after)`.

## Security headers and CSP

The SPAs are served by Caddy with HSTS, `X-Content-Type-Options`, `Referrer-Policy`, `X-Frame-Options: DENY`, `X-Robots-Tag: noindex`, and a `Content-Security-Policy-Report-Only` header. The policy is **not enforced** yet; promotion steps are in `docs/security/csp-rollout.md`. Details in [identity and security](identity-and-security.md#browser-and-proxy-hardening).

## Known limitations and documentation gaps

* `packages/design-tokens/package.json` still calls the tokens a draft pending O05 while `CLAUDE.md` records the approved terracotta/Cairo direction; the code imports the Cairo font and tokens, so treat the package description as stale.
* There is no frontend routing library, so deep links in the customer web are hash URLs (`#/orders/<id>`).
* The operations web probes up to nine endpoints on every load; this is simple but chatty, and a new staff area requires adding a probe and a tab in `App.tsx`.
