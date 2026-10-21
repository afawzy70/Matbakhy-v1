# Mobile code guide (customer app)

> **Owner role:** Frontend and mobile
> **Status:** PARTIALLY IMPLEMENTED — customer discovery, ordering and deposit flows exist; push delivery, physical-device and screen-reader acceptance do not
> **Language / اللغة:** English · [العربية](mobile-code-guide-ar.md)

The customer Android/iOS app is `apps/customer-mobile/`: React Native 0.86 + Expo 57 with React Navigation
(versions from `apps/customer-mobile/package.json`). There is **no mobile app for cooks or staff**; those use the operations web.
Architecture view: [mobile architecture](../03-architecture/mobile-architecture.md); user flow:
[customer mobile journey](../02-user-journeys/customer-mobile.md).

## Startup and structure

1. `apps/customer-mobile/index.ts` — `registerRootComponent(() => createElement(App))` (production entry; no injected test doubles).
2. `apps/customer-mobile/src/App.tsx` — loads configuration (`src/config.ts`: `EXPO_PUBLIC_API_URL`, `EXPO_PUBLIC_OIDC_ISSUER`,
   `EXPO_PUBLIC_OIDC_CLIENT_ID` default `matbakhy-mobile`, redirect `<scheme>://auth/callback`; deployed builds must use TLS),
   builds the `Session`, mounts `AppProvider` (`src/AppContext.tsx`) and the navigators.
3. Navigation: bottom tabs `Discover`, `Favorites`, `Orders`, `Account`; stack `Meal`, `Cart`, `Order`, `Notifications`, `Support`
   (`src/screens/navigation.ts`). Deep links in `src/linking.ts` (`orders/:orderId`, `meals/:mealId`, `cart`); the auth callback path is never navigation.

## Authentication and session storage

| Concern | File | Behaviour |
|---|---|---|
| Sign-in | `src/auth.ts` `signIn` | Authorization-code flow with PKCE in the **system browser** (`expo-auth-session`), `prompt=login`, ephemeral browser session; optional step-up adds `acr_values: "mfa 2"` (D18) |
| Redirect validation | `src/redirect.ts` | Verifies state and redirect URI before the code is used |
| Token storage | `src/tokenStore.ts` | **OS secure store** (`expo-secure-store`, `WHEN_UNLOCKED_THIS_DEVICE_ONLY`), key `matbakhy.session.v1`; a malformed stored value is deleted and treated as signed out |
| Session lifecycle | `src/session.ts` | Access-token refresh before expiry; a rejected refresh ends the session |
| API client | `src/mobileApi.ts` | Adds `Authorization: Bearer`; 401 triggers refresh/sign-out, 403 `STEP_UP_REQUIRED` triggers the step-up banner |
| Basket and pending placement | `src/basket.ts` | Secure store per account; cleared on sign-out and account switch |
| Account switch | `src/AppContext.tsx` (`accountEpoch`) | Remounts everything below when the person changes so no screen state survives (TC-048) |

The backend validates these as a stateless bearer JWT chain (`SecurityConfiguration.bearerChain`, no cookies, CSRF disabled
for that chain by design).

## Ordering code

| File | Role |
|---|---|
| `src/screens/Cart.tsx` | Quote request, price-change review (`priceChanges` from the api-client), submit; keys fixed and stored **before** the request (`submit`, line ~325) |
| `src/placement.ts` | `place()` posts `/api/v1/orders` with the stored keys; `recover()` asks `GET /api/v1/orders/by-request/{clientRequestId}` after a restart or lost response and only re-sends on a definite 404 |
| `src/screens/Payment.tsx` | Deposit instructions and transfer report |
| `src/screens/Orders.tsx` | Order list and detail |
| `src/screens/Discover.tsx`, `Meal.tsx`, `Favorites.tsx` | Browsing |
| `src/screens/Notifications.tsx`, `Support.tsx`, `Account.tsx` | Inbox, support cases, profile/security |
| `src/activity.ts`, `src/notificationPermission.ts` | Anonymous activity; optional notification permission (push delivery itself is not live) |
| `src/useServer.ts`, `src/ui.tsx`, `src/i18n.ts`, `src/ErrorBoundary.tsx` | Data-loading hook, shared UI, strings, crash boundary |

## Tests and native verification

- Jest (`jest-expo`): `src/App.test.tsx`, `src/auth.test.ts`, `src/signIn.test.ts`, `src/ordering.test.ts`. Run: `pnpm --filter @matbakhy/customer-mobile test`.
- Native runs (emulator/simulator) use `apps/customer-mobile/scripts/*.sh`, SQL fixtures in `apps/customer-mobile/e2e-native/`,
  the iOS XCTest host in `apps/customer-mobile/e2e-ios/`, and the workflow `.github/workflows/native-build.yml`. Evidence is
  recorded under `verification/runs/`. Results are summarised in [implementation status](../07-reference/implementation-status.md).
- Cleartext HTTP for localhost development is enabled by the config plugin `apps/customer-mobile/plugins/withLocalhostCleartext.cjs`.

## Known limitations and documentation gaps

- Push delivery: PLANNED. The app can ask for notification permission in development builds only (`src/config.ts` `notificationPrompt`); no FCM/APNs adapter exists on the backend.
- Physical-device and screen-reader acceptance (BLK-003, BLK-004 in `docs/status/latest.md`) are not done; MB-034 and MB-035 (release candidates) are TODO in `planning/backlog.json`.
- The preview Keycloak realm `deploy/web-preview/identity/matbakhy-realm.preview.json` defines clients `matbakhy-web` and `matbakhy-admin` but no `matbakhy-mobile` client, whereas the local realm `infra/identity/matbakhy-realm.json` does; how native builds authenticate against the preview is NOT VERIFIED here.
