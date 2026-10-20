# Mobile architecture (customer app)

> **Owner role:** Frontend and mobile
> **Status:** PARTIALLY IMPLEMENTED — a customer-only Expo app with development identifiers; CI emulator/simulator evidence exists, store identifiers, push and physical-device verification do not

There is one mobile app: `apps/customer-mobile` ("Matbakhy Dev", package and bundle id `com.matbakhy.customer.dev`). Cooks and staff use the operations web; there is no cook or staff mobile app. File-level guide: [mobile code guide](../04-codebase/mobile-code-guide.md). Diagram: [mobile architecture](diagrams.md#5-mobile-architecture). User-facing journeys: [customer mobile journey](../02-user-journeys/customer-mobile.md).

## Stack

| Item | Value | Source |
|---|---|---|
| Framework | Expo 57 (managed workflow with config plugins), React Native 0.86.3, React 19.2.3 | `apps/customer-mobile/package.json` |
| Navigation | React Navigation 7: bottom tabs inside a native stack | `apps/customer-mobile/src/App.tsx`, `apps/customer-mobile/src/screens/navigation.ts` |
| Auth | `expo-auth-session` (PKCE) and `expo-web-browser` | `apps/customer-mobile/src/auth.ts` |
| Secure storage | `expo-secure-store` | `apps/customer-mobile/src/tokenStore.ts` |
| Tests | Jest (`jest-expo`) with Testing Library for React Native: `src/App.test.tsx`, `src/auth.test.ts`, `src/signIn.test.ts`, `src/ordering.test.ts` | `apps/customer-mobile/package.json` |
| Shared code | `@matbakhy/api-client` and `@matbakhy/design-tokens` workspace packages | same |

Commands (repository root): `pnpm --filter @matbakhy/customer-mobile start` (Expo dev server), `pnpm --filter @matbakhy/customer-mobile test`, `pnpm --filter @matbakhy/customer-mobile typecheck`, `pnpm --filter @matbakhy/customer-mobile bundle:native` (Hermes bundles for both platforms, used by `scripts/check_workspace.sh`). The repository has no `android/` or `ios/` project checked in: CI generates them with `expo prebuild`.

## Shared versus platform-specific code

All application code is TypeScript shared by both platforms. Platform-specific parts are:

* The Android config plugin `apps/customer-mobile/plugins/withLocalhostCleartext.cjs`, active only when `extra.environment` is `development`: it permits cleartext HTTP to `localhost` and `10.0.2.2` so the emulator can reach the local identity provider and API. Release configurations for other environments never get it.
* iOS: `ephemeral` authentication session (`preferEphemeralSession: true`) so the system browser does not silently reuse the previous person's provider cookie.
* Native test assets (CI only, not shipped): `apps/customer-mobile/scripts/*.sh` (launch, auth and order round trips for Android and iOS), `apps/customer-mobile/e2e-ios` (XcodeGen project with a host app and XCUITest bundle that drives the installed app), `apps/customer-mobile/e2e-native` (synthetic seed SQL, price-change and deposit SQL, and `slow_proxy.py`, a delaying TCP proxy used to simulate "server created the order but the app never heard back").

## Configuration

`apps/customer-mobile/src/config.ts` builds `AppConfig` from `EXPO_PUBLIC_*` variables with development defaults from `app.json`: `EXPO_PUBLIC_API_URL` (default `http://localhost:8080`), `EXPO_PUBLIC_OIDC_ISSUER`, `EXPO_PUBLIC_OIDC_CLIENT_ID` (default `matbakhy-mobile`), `EXPO_PUBLIC_ENVIRONMENT`. The redirect URI is `<scheme>://auth/callback` (scheme `matbakhy-dev`). Outside `development` the API and issuer must be `https://` or `loadConfig` throws `ConfigError`. No secret exists in the app: it is a public OIDC client using PKCE.

## Navigation and deep links

* Tabs: Discover, Favorites, Orders, Account. Stack screens: Meal, Cart, Order, Notifications, Support (`screens/navigation.ts`).
* Deep links (`src/linking.ts`) use the app scheme: `matbakhy-dev://orders/<orderId>`, `.../meals/<mealId>`, `.../cart`, `.../orders`. The `auth/` callback path is filtered out and never treated as navigation. A link only names a screen; the server decides visibility (another account's order is "not found").
* `NavigationContainer` is keyed by `accountEpoch`, so switching account remounts navigation and drops screen state from the previous person.

## State, session and network layer

* `AppContext.tsx` owns locale, the auth phase (`loading`, `signedOut`, `signedIn`), the step-up flag and the shopping store. `useServer.ts` is the read hook; `ui.tsx` holds shared components; `ErrorBoundary.tsx` catches render errors.
* **Session** (`session.ts`): in-memory `Session` backed by `tokenStore.ts`. Tokens are kept only in the OS secure store (`WHEN_UNLOCKED_THIS_DEVICE_ONLY`); a malformed stored value is deleted and treated as signed out. `freshAccessToken()` refreshes the 5-minute access token 30 s early using the provider's refresh grant (one shared refresh for concurrent calls); an OAuth error such as `invalid_grant` ends the session, a transport failure keeps it. The session clears every private cache when the subject changes or on sign-out (account-switch rule, scenario TC-048).
* **Network** (`mobileApi.ts`): `createMobileApi` builds the typed client; a request middleware sets `Accept-Language` and `Authorization: Bearer <token>`; a response middleware turns `401` into local sign-out and `403` with code `STEP_UP_REQUIRED` into the step-up banner (clones the response so callers still read the body). The middleware deliberately returns nothing, because returning the original objects broke `openapi-fetch`'s `instanceof Response` check on iOS (comment in the file, native run 21).
* **Ordering** (`basket.ts`, `placement.ts`): the basket and the pending placement (client request id plus idempotency key) are persisted in secure store per subject, cleared on sign-out. `place()` returns `placed`, `uncertain` (no definitive answer; keys kept, retry cannot duplicate), `rejected(code)` or `stepUp`. After an uncertain result the screen offers "check and retry" which resolves through `GET /api/v1/orders/by-request/{clientRequestId}` (scenario TC-049, exercised by the CI script `apps/customer-mobile/scripts/order-roundtrip-android.sh`).
* **Money and time** follow the same rules as the web: integer piastres, `Africa/Cairo`.

## Authentication flow (OIDC, native)

Authorization code with PKCE (S256) in the system browser (ASWebAuthenticationSession / Custom Tabs, never an embedded web view), `prompt=login`, scopes `openid profile email`. `checkRedirect` strictly validates the callback URL and state before the code is exchanged. The resulting tokens go to secure storage. The API validates the bearer token on its stateless chain (issuer, audience `matbakhy-api`, signature, expiry). Sign-out revokes the refresh token when the provider supports it, then clears local state. Step-up repeats sign-in with `acr_values=mfa 2`. Sequence diagrams: [OIDC](diagrams.md#6-authentication-oidc) and [MFA step-up](diagrams.md#7-mfa-step-up).

## Push notifications

DISABLED. `notificationPermission.ts` can ask the OS for notification permission (only in development builds by default, `notificationPrompt`) but no device token is requested or registered, and the server's push flag defaults to off with only a logging adapter behind it. In-app Notifications (the server inbox) is the working channel.

## Native build and device-test assets

| Asset | What it does | State |
|---|---|---|
| `.github/workflows/native-build.yml` | Android: `expo prebuild`, Gradle debug and release APK, emulator launch check, sign-in round trip, ordering round trip. iOS: prebuild, Xcode build, simulator launch, sign-in and ordering UI tests, screenshots | IMPLEMENTED (CI evidence on GitHub-hosted runners; debug-keystore signed, not store builds) |
| `apps/customer-mobile/scripts/launch-android.sh`, `launch-ios.sh`, `auth-roundtrip-*.sh`, `order-roundtrip-*.sh` | The checks above, runnable only in that CI shape (they start the local stack through `scripts/dev.sh up`) | IMPLEMENTED for CI |
| Physical-device testing | Not performed; `docs/security/mfa-policy.md` explicitly says no physical-device or simulator run of an MFA-enabled mobile journey exists | NOT VERIFIED |
| App store identifiers, signing, EAS/store submission | Owner decision O08 open; `app.json` says "Development identifiers only" | PLANNED |

## Known limitations and documentation gaps

* The preview Keycloak realm (`deploy/web-preview/identity/matbakhy-realm.preview.json`) defines no `matbakhy-mobile` client (only `matbakhy-web` and `matbakhy-admin`), while the local realm (`infra/identity/matbakhy-realm.json`) does and the MFA migration script mentions both. Whether the mobile app can sign in to the deployed preview is NOT VERIFIED.
* There is no mobile Security screen (turn MFA on or off from the web or Keycloak's account console), per `docs/security/mfa-policy.md`.
* Mobile RTL is applied per component with `textAlign`/`writingDirection` (`apps/customer-mobile/src/ui.tsx`) rather than `I18nManager.forceRTL`; no use of `I18nManager` was found, so layout mirroring of native navigation chrome is NOT VERIFIED on devices.
* `docs/security/mfa-policy.md` refers to an iOS beta device test plan at docs/mobile/ios-beta-device-test-plan.md (written without a link on purpose); that file does not exist in the repository.
* Mobile unit tests cover sign-in, session and ordering helpers; most screens (Favorites, Support, Payment, Notifications) have no dedicated test file (only `src/App.test.tsx` renders the app).
