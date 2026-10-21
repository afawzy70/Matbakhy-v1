# User journeys: customer mobile (Android and iOS)

> **Owner role:** Frontend and mobile
> **Status:** PARTIALLY IMPLEMENTED — the Expo app (`apps/customer-mobile`) implements browsing, sign-in, basket, ordering, tracking, notifications, support and step-up; it has no MFA settings screen, no cook features, and no physical-device verification is recorded.
> **Language / اللغة:** English · [العربية](customer-mobile-ar.md)

The customer app is one React Native (Expo) code base for both platforms (`apps/customer-mobile/package.json`: Expo 57, React Navigation 7). **There is no `Platform.OS` branching in `apps/customer-mobile/src`** (searched); Android versus iOS differences come from the operating system's browser session, secure store and the CI harnesses, listed in [Android versus iOS](#android-versus-ios). Architecture: [Mobile architecture](../03-architecture/mobile-architecture.md), code walk-through: [Mobile code guide](../04-codebase/mobile-code-guide.md). Web equivalent: [Customer web](customer-web.md).

## Screens and navigation (`apps/customer-mobile/src/App.tsx`, `apps/customer-mobile/src/screens/navigation.ts`)

| Navigator | Screen | File | Purpose |
|---|---|---|---|
| Bottom tabs | Discover | `apps/customer-mobile/src/screens/Discover.tsx` | Search/browse meals |
| Bottom tabs | Favorites | `apps/customer-mobile/src/screens/Favorites.tsx` | Own favourites |
| Bottom tabs | Orders | `apps/customer-mobile/src/screens/Orders.tsx` | Order list (`OrdersScreen`) |
| Bottom tabs | Account | `apps/customer-mobile/src/screens/Account.tsx` | Sign in/out, switch account, notifications and support links, language, phone-alert permission card |
| Stack | Meal | `screens/Meal.tsx` | Slot, quantity, add to basket |
| Stack | Cart | `screens/Cart.tsx` | Basket, phone verification, quote, placement, recovery |
| Stack | Order | `screens/Orders.tsx` (`OrderScreen`) | Tracking, cancel, review, payment status |
| Stack | Notifications | `screens/Notifications.tsx` | In-app inbox |
| Stack | Support | `screens/Support.tsx` | Cases about an order |
| (payment widgets) | `screens/Payment.tsx` | Deposit terms/status inside Cart and Order |

Arabic is the default unless the device language is English (`initialLocale` in `App.tsx`). A `StepUpBanner` is rendered above the navigator whenever any API answer is `403 STEP_UP_REQUIRED`.

## Cross-cutting mobile behaviour

### Authentication, redirect and deep links

* **Flow.** `apps/customer-mobile/src/auth.ts` `signIn`: OIDC authorization code + PKCE (S256) in the **system browser** (never an embedded web view), `prompt=login` always, `preferEphemeralSession: true`, scopes `openid profile email`, public client `matbakhy-mobile` without a secret (`apps/customer-mobile/src/config.ts`). The result URL must equal the redirect URI exactly, carry our `state`, a `code` and no `error` (`apps/customer-mobile/src/redirect.ts`, `checkRedirect`) before the token exchange; otherwise `INVALID_REDIRECT`, `STATE_MISMATCH`, `PROVIDER_ERROR` or `MISSING_CODE`.
* **Redirect URI.** `<scheme>://auth/callback`; the dev scheme is `matbakhy-dev` (`apps/customer-mobile/app.json`; the Keycloak client in `infra/identity/matbakhy-realm.json` registers `matbakhy-dev://auth/callback`). Staging/production identifiers and signing are owner decision O08 (`app.json` `extra.note`): PLANNED. Outside development the API URL and issuer must be HTTPS (`ConfigError` otherwise).
* **Deep links.** `apps/customer-mobile/src/linking.ts`: prefix `<scheme>://`; routes `orders` (tab), `orders/:orderId`, `meals/:mealId`, `cart`; any URL beginning `<scheme>://auth/` is filtered out so the sign-in callback is never treated as navigation. A link only names a screen: the server decides access (another account's order shows "not found"). Only the custom scheme is implemented; universal/app links (https) are PLANNED / not present.
* **Session persistence.** `apps/customer-mobile/src/tokenStore.ts` keeps one JSON record (access token, refresh token, id token, expiry, subject) in `expo-secure-store` with `WHEN_UNLOCKED_THIS_DEVICE_ONLY` (iOS Keychain / Android Keystore-backed storage); malformed or expired-without-refresh records are deleted and treated as signed out. A failed first read is retried once after 300 ms so a keystore that is not ready (seen on Android cold start) does not look like a sign-out. `apps/customer-mobile/src/session.ts` refreshes the access token 30 s before expiry, one shared refresh for concurrent callers; a provider refusal (`invalid_grant`) or a token for another subject ends the session; an unreachable provider keeps it.
* **Request layer.** `apps/customer-mobile/src/mobileApi.ts`: bearer token from the session, `Accept-Language`; `401` with a token → local sign-out; `403` with body `code = STEP_UP_REQUIRED` → `onStepUpRequired`. The backend serves the bearer chain (`bearerChain` in `backend/app/src/main/java/com/matbakhy/app/security/SecurityConfiguration.java`): stateless JWT validation (issuer, audience `apiAudience`, signature, expiry), no cookies, no CSRF token.

### Basket and safe order retries (`basket.ts`, `placement.ts`)

* Basket and the pending placement are stored per person in secure storage under keys `matbakhy.basket.v1.<subject>` / `matbakhy.pendingPlacement.v1.<subject>`; signed out the keys have no suffix (guest basket). On sign-in the guest basket moves to the person only if they have none of their own (`bindShopping`).
* A `Shopping` handle bound to its owner is taken when a request starts, so a late answer after an account switch can only touch the previous person's keys.
* `place()` sends `POST /api/v1/orders` with the stored `Idempotency-Key` and `clientRequestId`. Outcomes: `placed` (basket and keys cleared), `uncertain` (5xx, 401 or network: keys kept; message differs for offline), `rejected` (definitive 4xx: keys dropped, basket kept), `stepUp` (403 `STEP_UP_REQUIRED`: keys kept with `awaitingExplicitRetry`; re-sent only on the customer's explicit retry, never on focus or restart).
* `recover()` after a restart asks `GET /api/v1/orders/by-request/{clientRequestId}`; found → placed; 404 → resend with the same keys; anything else → uncertain, with no claim about the order or stock.

### Account switching and step-up

* Switch account (`switchAccount` in `apps/customer-mobile/src/AppContext.tsx`) = sign out (best-effort refresh-token revocation, clear own storage) then a fresh interactive sign-in. Subject changes clear all private caches and bump `accountEpoch`, which remounts the whole navigation tree so no screen state of the previous person survives.
* Step-up (`startStepUp`) repeats the system-browser sign-in with `acr_values=mfa 2`. Same subject: tokens replaced, basket and caches kept, data reloaded. Different subject: treated as an account switch.
* There is **no mobile MFA settings screen** (`docs/security/mfa-policy.md` says the same): a customer enables MFA on the web or in Keycloak's account console, then the app reacts to the server's 403.

### Offline and network errors

* The app has no offline mode; it reads from the API. The offline text says what is shown is not the current cooking status or stock (`offline` in `apps/customer-mobile/src/i18n.ts`). The order screen polls every 10 s (`setInterval(reload, 10_000)` in `screens/Orders.tsx`).
* An unconfirmed order shows a notice with "Check and retry"; wording differs offline versus server-uncertain (`pendingOffline`, `pendingUncertain`).
* Notification permission: `notificationPrompt` is true only in development builds; other builds say updates appear in the in-app Notifications screen. Push delivery is undecided (owner decision O07): PLANNED.

## Android versus iOS

| Aspect | Android | iOS | Evidence |
|---|---|---|---|
| System browser used for sign-in | Chrome Custom Tabs | `ASWebAuthenticationSession` | comment in `apps/customer-mobile/src/auth.ts` |
| Ephemeral session option | `preferEphemeralSession: true` is passed on both; effect on Android: NOT VERIFIED | Honoured; prevents silently re-using the previous Keycloak cookie | `auth.ts`, `signIn.test.ts` |
| Plain-HTTP localhost in dev | Allowed through a config plugin only for development identifiers | Not needed | `apps/customer-mobile/plugins/withLocalhostCleartext.cjs` |
| Secure storage | Keystore-backed `expo-secure-store` (cold-start read failures observed, hence the retry) | Keychain | `basket.ts`, `tokenStore.ts` |
| Bundle ids | `com.matbakhy.customer.dev` | `com.matbakhy.customer.dev` | `app.json` |
| Automated native run | Emulator, shell + uiautomator: `apps/customer-mobile/scripts/auth-roundtrip-android.sh`, `order-roundtrip-android.sh` | Simulator, XCUITest: `apps/customer-mobile/e2e-ios/UITests/SignInRoundTripTests.swift`, `OrderingTests.swift` driven by `apps/customer-mobile/scripts/auth-roundtrip-ios.sh`, `apps/customer-mobile/scripts/order-roundtrip-ios.sh` | CI workflow `.github/workflows/native-build.yml` |
| Physical device | none recorded (CFG-112 notes that no plan exists for Android) | owner plan via TestFlight (CFG-046); not recorded as run | `docs/quality/owner-acceptance/cases/config-security.json` |

## Journeys

Common items for all journeys below. **Actor:** Guest or Customer (bearer token; `ACCOUNT_ACTIVE` authority; object-level ownership). **Backend and tables:** identical to the web journeys; see the numbered journeys in [Customer web](customer-web.md) (CW-1 to CW-7) rather than repeating them. **Tests (all):** jest `apps/customer-mobile/src/App.test.tsx`, `apps/customer-mobile/src/auth.test.ts`, `apps/customer-mobile/src/signIn.test.ts`, `apps/customer-mobile/src/ordering.test.ts`; native CI harness above; seed data `apps/customer-mobile/e2e-native/seed.sql` (synthetic, CI only). Results of these suites for the current commit: NOT VERIFIED here.

### CM-1 Sign in, restart, sign out, switch account
1. Goal: authenticated mobile session that never leaks between people (BR-048, TC-048).
2. Actor: Customer. 3. Prerequisite: Keycloak customer account; reachable issuer.
4. Start: Account tab → "Sign in". 5. Actions: sign in in the system browser; kill and reopen the app; "Switch account"; "Sign out".
6. Responses: profile name appears (`signedInAs`); after reopening the session is restored from secure storage; after sign-out a restart shows signed out; the next sign-in shows the login form again.
7. API: `GET /api/v1/me` (API-009); provider endpoints: discovery, authorize, token, refresh, revoke (Keycloak).
8. Backend: bearer chain, `AccountMapper`, `ProfileController`. 9. DB: `app_user`.
10. Transitions: loading → signedIn / signedOut (`auth` state in `AppContext.tsx`).
11. Errors: dismissed browser → `SignInCancelled`, nothing stored; invalid redirect/state → refused; token without `sub` rejected; 401 → local sign-out.
12. Tests: `auth.test.ts`, `signIn.test.ts`, `App.test.tsx` ("customer app shell", "an expired access token is refreshed…"), `e2e-ios/UITests/SignInRoundTripTests.swift`, `apps/customer-mobile/scripts/auth-roundtrip-android.sh`.
13. Owner E2E: CFG-046, E2E-BASKET-027, E2E-DISC-004; J8 (physical devices).
14. Status: IMPLEMENTED in code and emulator/simulator CI harness; physical device: NOT VERIFIED. 15. Limitations: see the table above.

### CM-2 Discover, add to basket, replace basket
1. Goal: browse and build a one-kitchen basket (BR-018, TC-148). 2. Actor: Guest/Customer. 3. Prereq: published meals.
4. Start: Discover tab. 5. Actions: search, open a meal, pick slot and quantity, add; adding another kitchen's meal asks keep or replace; edit quantities in Cart.
6. Responses: quantity bounded by `min(server limit, slot portions)`; exhausted slot cannot be added; estimate in integer piastres.
7. API: `GET /api/v1/meals`, `GET /api/v1/meals/{mealId}`, `GET /api/v1/zones`, `GET /api/v1/configuration/public`, `PUT/DELETE /api/v1/me/favorites/{mealId}`, `GET /api/v1/me/favorites`, `POST /api/v1/activity`.
8–9. As CW-1/CW-3. 10. None server-side.
11. Errors: offline message (E2E-DISC-115); storage read failure retried once.
12. Tests: `App.test.tsx` ("native discovery and ordering", "native basket editing", "Q1 on native"), `ordering.test.ts`, `apps/customer-mobile/scripts/order-roundtrip-android.sh`, `OrderingTests.swift` (testD, testE, testG).
13. Owner E2E: E2E-BASKET-026, E2E-BASKET-009, E2E-BASKET-010, E2E-DISC-115. 14. IMPLEMENTED. 15. No guest-to-device sync.

### CM-3 Quote, placement and unconfirmed-order recovery
1. Goal: place exactly one order even if the app dies (BR-023, TC-049, TC-132). 2. Actor: Customer. 3. Prereq: verified phone, address for delivery.
4. Start: Cart. 5. Actions: verify phone, choose mode and payment (COD or DEPOSIT), "Calculate total", confirm, place; force-kill during submit; reopen offline; reopen online and press "Check and retry".
6. Responses: price-change screen with old and new totals and explicit acceptance (D13); unconfirmed notice; after recovery the single order opens.
7. API: `POST /api/v1/me/phone/challenges[/…/verify]`, `GET/POST /api/v1/me/addresses`, `POST /api/v1/quotes`, `POST /api/v1/orders`, `GET /api/v1/orders/by-request/{clientRequestId}`.
8–9. As CW-4. 10. Order `PENDING_ACCEPTANCE`. 11. `PRICE_CHANGED`, `QUOTE_EXPIRED`, `STOCK_CHANGED`, `SLOT_CLOSED`, `STEP_UP_REQUIRED`, `NETWORK`.
12. Tests: `ordering.test.ts` (placement block), `App.test.tsx` ("the unconfirmed order stays reachable…", "repeated Check and retry", "D13 on native"), CI: `OrderingTests.swift` testA/B/C/H, `order-roundtrip-android.sh` (kill during slow response using `e2e-native/slow_proxy.py`, DB count of exactly one order).
13. Owner E2E: E2E-ORDER-107, E2E-ORDER-122, E2E-ORDER-024, E2E-BASKET-026; J8. 14. IMPLEMENTED (emulator/simulator); device NOT VERIFIED. 15. No push notification on state change.

### CM-4 Track order, deposit report, cancel, review
1. Goal: follow the order and report a deposit honestly. 2. Actor: order owner. 3. Prereq: an order.
4. Start: Orders tab or deep link `<scheme>://orders/<id>`. 5. Actions: open order; "I sent the deposit"; cancel / request cancellation; review a completed order.
6. Responses: refreshed every 10 s; handover code shown when the server returns it; deposit copy states the kitchen must confirm receipt.
7. API: `GET /api/v1/orders`, `GET /api/v1/orders/{orderId}`, `POST …/cancel`, `POST …/cancellation-requests`, `POST …/payment/transfer-report`, `POST …/reviews`.
8–9. As CW-5. 10. As CW-5. 11. Another account's order id → "not found" screen.
12. Tests: `App.test.tsx` ("deep links (MB-033)", "MB-053 on native"), `OrderingTests.swift` testF (deep link warm and cold) and testK (deposit), `order-roundtrip-android.sh` (deep link warm and cold; deposit transfer report).
13. Owner E2E: E2E-ORDER-122, E2E-FIN-113, E2E-ORDER-120. 14. IMPLEMENTED. 15. Android deep-link coverage is by `adb am start` only.

### CM-5 MFA step-up (D18)
1. Goal: complete a second factor when the server requires it. 2. Actor: Customer whose category policy is REQUIRED or who enabled MFA on the web. 3. Prereq: web-side preference or REQUIRED policy; live realm migration applied (CFG-028).
4. Start: any screen after a 403 `STEP_UP_REQUIRED`. 5. Actions: press Verify in the banner → system browser with `acr_values=mfa 2` → enter OTP or enrol TOTP → return.
6. Responses: banner disappears; refused screens reload on `dataEpoch`; refused order placement is re-sent only by explicit retry with the same keys. Cancelling keeps the banner and the account.
7. API: any protected endpoint returns 403 `STEP_UP_REQUIRED` (`backend/app/src/main/java/com/matbakhy/app/security/MfaEnforcementFilter.java`); the app has no call to `/api/v1/me/security`.
8. Backend: `MfaEnforcementFilter`, `MfaPolicyService`. 9. DB: `mfa_policy`, `user_mfa_preference`, `user_mfa_reset`.
10. Transitions: banner on/off. 11. Errors: browser dismissed; wrong person signs in → account switch.
12. Tests: `App.test.tsx` ("two-step verification step-up (D18)"), `ordering.test.ts` (step-up retry), `signIn.test.ts`; no native MFA-enabled run exists (`docs/security/mfa-policy.md`).
13. Owner E2E: CFG-024, CFG-025, CFG-026, CFG-108, CFG-109 (iPhone), CFG-112 (Android); J8. 14. PARTIALLY IMPLEMENTED: code and jest only; no device evidence. 15. No Security screen; keep CUSTOMER and KITCHEN policy on OPTIONAL until device-tested.

### CM-6 Notifications and support
1. Goal: read order updates and contact support. 2. Actor: Customer. 3. Prereq: session.
4. Start: Account → Notifications / Support. 5. Actions: open inbox, mark read; create a support case for an order, add messages; optionally allow phone alerts (development builds only).
6. Responses: in-app list; honest permission wording; refusal is safe.
7. API: `GET /api/v1/notifications`, `PUT /api/v1/notifications/{id}/read`, `GET/POST /api/v1/support/cases`, `GET/POST /api/v1/support/cases/{caseId}[/messages]`. The app does **not** call `PUT /api/v1/me/devices/{installationId}` (API-033); device registration is not wired: PLANNED with O07.
8–9. As CW-6. 12. Tests: `App.test.tsx` ("MB-033 notification permission"), `OrderingTests.swift` testJ. 13. E2E-DISC-108, E2E-ORDER-117, E2E-ORDER-118. 14. IMPLEMENTED (in-app only). 15. Push PLANNED.

## Known limitations and documentation gaps

* `docs/security/mfa-policy.md` step 5 of its rollout says the mobile app does not handle `403 STEP_UP_REQUIRED` and would show a generic error, while its residual-risk section (and the code in `AppContext.tsx` / `mobileApi.ts`) says the banner exists. Trust the code: the banner exists; device behaviour is NOT VERIFIED.
* `docs/security/mfa-policy.md` refers to an iOS beta device test plan under a mobile docs directory; that directory does not exist in this repository (checked). The device plan is therefore missing from the repo.
* `apps/customer-mobile/src/auth.ts` comments say the ephemeral session is used "on iOS"; the code passes `preferEphemeralSession: true` unconditionally.
* The preview realm file `deploy/web-preview/identity/matbakhy-realm.preview.json` contains no `matbakhy-mobile` client (the dev realm does); `deploy/web-preview/identity/mfa-migration.kcadm.sh` references the client. Whether the deployed preview realm has it: NOT VERIFIED.
