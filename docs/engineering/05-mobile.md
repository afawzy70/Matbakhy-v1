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
