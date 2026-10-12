# Customer Android and iOS delivery

Default: React Native with Expo, one source-controlled customer app, shared generated API client. Select a supported Expo SDK and its matching React Native version in P01. Earlier FlutterFlow/Flutter suggestions remain alternatives, not approved requirements; changing the default before P01 must update the architecture/backlog, not mix frameworks.

## Environments and identity

Use distinct development/staging/production app identifiers, API base URLs and push/provider settings. Never embed client secrets. All public native configuration must be safe to extract from the binary. Use provider-supported authorization code with PKCE in system browser, state/nonce checks and exact redirect allowlists. Secure tokens using maintained OS storage integration; clear credentials, query cache, locally persisted private data and device registrations on logout/account switch.

Define navigation link allowlist: public meal/cook pages and authenticated own order/support pages. Handle cold start, warm start, logged-out return and expired sessions. Validate remote link parameters; never accept arbitrary URLs as an in-app browser redirect. An order ID from push still requires backend object authorization.

## State and reliability

Persist safe cart draft and pending client request ID before submit; no offline order queue that silently purchases later. Offline content is labelled stale, with submission disabled. After foreground/resume, refresh availability and active order status. If app dies during submission, recover by request lookup/same-key retry. Coordinate one refresh-token attempt across concurrent requests and invalidate session on confirmed refresh failure; avoid infinite auth loops.

Push registers installation and token per account/environment, handles rotation and provider-invalid-token response, and unregisters on logout. Permission denial leaves in-app inbox and polling usable. Do not place address, phone or handover code in push preview. Notification tap fetches latest state; never trusts embedded status.

## Platform evidence matrix

| Dimension | Android | iOS |
|---|---|---|
| Local build | Supported Android toolchain/emulator | macOS + compatible Xcode for local iOS build/simulator |
| Cloud alternative | Approved cloud build after account/budget setup | Approved cloud build/signing; does not substitute for device testing |
| Test targets | Emulator and at least one physical Android device | Simulator and at least one physical iPhone/beta build |
| Install evidence | Package ID, version/build, artifact hash, OS/device, installation result | Bundle ID, version/build, signing profile/account reference without secrets, OS/device, install result |
| Journeys | Sign-in, browse, delivery+pickup checkout, unknown response, order, review/support, logout | Same journeys plus safe-area/system-browser return/notification routing |
| Accessibility | TalkBack, font scaling, focus, touch targets | VoiceOver, Dynamic Type, focus, touch targets |
| Release | Internal testing track before public rollout | TestFlight/approved beta before public release |

Expo Go or web preview is useful during development but does not satisfy native integration/release acceptance. If a device, macOS runner, signing account or provider credential is missing, record BLOCKED for that specific check, continue independent work and provide exact setup needed. Never report iOS verified from Android or browser tests.

## Store readiness

Owner supplies identifiers/accounts/signing permissions and approves spend/submission. Prepare icons/splash, screenshots in both locales as required, version/build numbering, support/privacy/deletion URLs, truthful data-use declarations, test/reviewer credentials in a private channel and production backend availability. Verify current Apple/Google policies when submitting; do not invent exemptions or claim policy approval in advance.

Record separate states for build created, installed/tested, submitted, in review, rejected, approved and released. TestFlight/Play internal acceptance does not equal public availability. Over-the-air updates, if later enabled, require compatible runtime/version targeting and rollback strategy; do not silently change reviewed native permissions or APIs.

## Implemented native shell and authentication (MB-031)

- **Configuration (`src/config.ts`):** API base URL, OIDC issuer and client id come from `EXPO_PUBLIC_*` build variables, with local development defaults. Outside development, plain HTTP is refused. The redirect is `<scheme>://auth/callback` (the development scheme is `matbakhy-dev`; production identifiers are O08).
- **Sign-in (`src/auth.ts`):** authorization code + PKCE (S256) through the system browser (`expo-auth-session` / `expo-web-browser`: ASWebAuthenticationSession on iOS, Custom Tabs on Android), never an embedded web view. Before any token exchange, `src/redirect.ts` requires all of the following, and refuses the response otherwise:
  - the exact redirect URI;
  - the generated `state`;
  - no provider error;
  - a code.
- **Tokens (`src/tokenStore.ts`):**
  - Stored only in `expo-secure-store` (Keychain / Keystore-backed), with `WHEN_UNLOCKED_THIS_DEVICE_ONLY`.
  - A stored value that is not a well-formed session, or that is expired with no refresh token, is deleted and treated as signed out.
  - Tokens are never logged.
- **Account switch and logout (`src/session.ts`):**
  - Private data is cached in memory per subject.
  - Signing in as a different subject, signing out, a 401 from the API or an invalid stored session clears every private cache.
  - Logout also revokes the refresh token when the provider supports revocation (best effort).
- **Crash boundary:** a rendering error shows a recoverable screen without technical detail.
- **Locale:** Arabic by default, or English when the device language is English, with a toggle.
- **Verification:**
  - Unit tests cover redirect rejection, secure-store handling, account switch and logout, configuration and the crash boundary.
  - The native workflow builds release-variant apps (JS bundle embedded, not store builds), launches them on an Android emulator and an iOS simulator, and records the result in the job log.
  - A full native sign-in round trip against the identity provider is not yet automated.
