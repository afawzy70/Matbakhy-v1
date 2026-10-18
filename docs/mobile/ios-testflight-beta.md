# iPhone beta through TestFlight (release engineering, not a feature)

**Status: prepared, nothing built, uploaded or submitted.** No Apple account, signing material or paid subscription is involved yet.
Every statement marked *not verified* below needs a physical iPhone, an Apple Developer account and the owner's go.
This work is deliberately separate from the stabilization PR (#9). It depends on it: the mobile step-up for MFA (`STEP_UP_REQUIRED`) and the
D18 Keycloak flow come from there.

## 1. What exists today (reviewed)
| Area | State | Evidence |
|---|---|---|
| App | Expo SDK 57, React Native 0.86, `expo-auth-session` (authorization code + PKCE in the system browser), `expo-secure-store` (Keychain, `WHEN_UNLOCKED_THIS_DEVICE_ONLY`), `expo-notifications` not used for delivery (O07) | `apps/customer-mobile/package.json`, `src/auth.ts`, `src/tokenStore.ts` |
| Identity | `app.json` is the *development* identity: bundle id `com.matbakhy.customer.dev`, scheme `matbakhy-dev` | `app.json` |
| Config | `loadConfig` takes API / issuer / client id from `EXPO_PUBLIC_*`; outside development it **refuses non-HTTPS URLs**; the redirect URI is `<scheme>://auth/callback` | `src/config.ts`, `src/App.test.tsx`, `auth.test.ts` |
| Native CI | Unsigned iOS **simulator** build (Xcode, Debug and Release with the JS bundle embedded), launch, then an XCUITest sign-in round trip: system browser, PKCE, secure storage, sign-out, **account switch**, and the ordering journey with app termination and offline recovery (`.github/workflows/native-build.yml`, job *iOS simulator build*) | green on commit 240cf0c |
| Not covered by that CI | a physical device, real Keychain/Secure Enclave behaviour, cellular networking, Apple signing and provisioning, TestFlight processing, App Transport Security against the real TLS host, push | — |

## 2. What this change adds
* `apps/customer-mobile/app.config.cjs` — `APP_VARIANT=preview` switches the identity; default (unset) leaves `app.json` untouched, so the native CI still builds the development identity.
  * bundle id **`com.matbakhy.customer.beta`**, URL scheme **`matbakhy-beta`**, display name *Matbakhy Beta*, marketing version `0.1.0`, `environment: staging`, `ITSAppUsesNonExemptEncryption: false` (**owner must confirm**: the app uses HTTPS only).
  * The build number is owned by EAS (`appVersionSource: remote`, `autoIncrement`); `buildNumber: "1"` only seeds the first build. To change the marketing version edit `PREVIEW.version`; each upload gets the next build number automatically.
* `apps/customer-mobile/eas.json` — profile **`testflight`** (`distribution: store`) with the Contabo Preview endpoints `https://api.167-86-90-174.sslip.io` and `https://id.167-86-90-174.sslip.io/realms/matbakhy`, client `matbakhy-mobile`. Public values only. If the preview domain changes, change it here (and `PREVIEW_DOMAIN` on the server).
* `deploy/web-preview/identity/matbakhy-realm.preview.json` — the public PKCE client `matbakhy-mobile` with the single redirect URI `matbakhy-beta://auth/callback`, API audience mapper and `amr` mapper; `deploy/web-preview/register-mobile-client.sh` applies the same to the **existing** realm (idempotent; `--import-realm` skips an existing realm).
* `.github/workflows/ios-beta.yml` — manual and owner-gated (see §6). `scripts/test_ios_beta_config.py` guards identity, endpoints, redirect registration, workflow gating and that no signing material is tracked.

## 3. The Preview API must be reachable from the phone (decision needed)
The preview proxy is deny-by-default: `CUSTOMER_ALLOWED_IPS` (class used by `api.`, `id.`, `media.`) defaults to the owner's allow-list. A tester's iPhone on cellular or home Wi-Fi will get `403 Not available` until either (a) the customer class is opened (`CUSTOMER_ALLOWED_IPS="0.0.0.0/0 ::/0"`, synthetic data only, staff class stays closed) or (b) testers connect through an address on the allow-list (VPN). **Owner decision; not changed here.** The staff class (`ops.`, `/api/v1/ops`, `/api/v1/cook`) must stay restricted.

## 4. Keycloak and OIDC (verify before the first device test)
1. Apply the D18 realm migration first if MFA step-up should work: `docs/operations/13-keycloak-mfa-migration.md`.
2. `./register-mobile-client.sh apply` on the server, then `VERIFY_MOBILE=1 ./migrate-mfa.sh verify` (or the Python script directly): it proves from outside that the redirect URI `matbakhy-beta://auth/callback` shows the login page, an unregistered one is refused, a normal sign-in issues a code without OTP, and a step-up request asks for OTP/enrolment.
3. The app opens the system browser (ASWebAuthenticationSession, ephemeral session, `prompt=login`) and receives `matbakhy-beta://auth/callback`; `checkRedirect` accepts only that exact URI and state.
Rehearsed on a local Keycloak 26.8.0 (registration idempotent, redirect checks, sign-in, step-up); **not run on the Contabo realm**.

## 5. Apple, signing, App Store Connect and TestFlight — what the owner must provide
| Need | Detail | Cost / owner action |
|---|---|---|
| Apple Developer Program membership | Individual or Organization (Organization needs a D-U-N-S number and takes longer). The Account Holder must accept the agreements in App Store Connect. | **Paid annual membership — owner purchase, not made here** |
| App identifier | `com.matbakhy.customer.beta` (EAS can register it during the first build) | none |
| App Store Connect app record | Name *Matbakhy Beta* (names are unique; a variant may be needed), primary language, SKU, bundle id above | owner action |
| Signing | Distribution certificate + App Store provisioning profile. **Managed by EAS**: the owner runs `eas build --profile testflight` (or `eas credentials`) once interactively and signs in with the Apple ID (2FA). The private keys live in EAS, **never in Git or GitHub secrets**. | owner action (interactive) |
| Non-interactive upload | An App Store Connect API key (role *App Manager* or *Admin*) stored in EAS via `eas credentials` / `eas submit` setup. The `.p8` file is never committed. | owner action |
| Expo account | EAS Build/Submit. The free plan has limited, queued iOS builds; a paid plan is optional. An access token (`EXPO_TOKEN`) is stored as an environment secret (§6). | **any paid plan = owner decision** |
| TestFlight testers | *Internal* testers (App Store Connect users, up to 100) get builds after processing, no Apple review. *External* testers need Beta App Review, test information, a privacy policy URL and App Privacy answers; use synthetic demo accounts only. A build expires after 90 days. | owner action |
| Export compliance | `ITSAppUsesNonExemptEncryption=false` is set (HTTPS only). Confirm the answer; otherwise App Store Connect asks on every upload. | owner confirmation |
| Devices | TestFlight needs no UDID registration. iOS minimum follows Expo SDK 57 / React Native 0.86. | — |

## 6. Repeatable workflow (inert until the owner activates it)
`ios-beta.yml` never runs on push or schedule.
* **config-check** (automatic on pull requests touching the mobile app, or manual): the guard tests and `expo config` for the preview variant. No secrets.
* **build** (manual): needs GitHub environment `ios-beta` with required reviewers and the secret `EXPO_TOKEN`, and the confirmation text `BUILD-IOS-BETA`. Starts `eas build --platform ios --profile testflight --non-interactive --no-wait` (EAS cloud, pinned `eas-cli@24.10.0`). EAS then signs with the credentials it holds. The first build must be run **interactively once from the owner's machine** so EAS can create the credentials.
* **submit** (manual): environment `ios-beta-submit` with required reviewers, confirmation text `SUBMIT-TO-TESTFLIGHT`; `eas submit … --latest` uploads the newest finished build.
Activation checklist: create both environments with the owner as required reviewer; add `EXPO_TOKEN` (an Expo robot token) to them; do the first interactive build; only then run *build* / *submit* from the Actions tab. Rotating or revoking: delete the Expo token and the EAS credentials.

## 7. MFA and `STEP_UP_REQUIRED` on the phone
The API answers `403 STEP_UP_REQUIRED` when the effective MFA policy needs a second factor the token lacks (a customer who turned MFA on in the web app under OPTIONAL, or a category set to REQUIRED). Before the stabilization change the app showed a broken screen. Now (in PR #9) the app shows a *Verify* banner that repeats the system-browser sign-in with `acr_values=mfa 2`, keeps the account and basket, and replaces the tokens. **Evidence is jest tests and the simulator CI only; there is no device run.** Treat MFA-enabled mobile journeys as *unverified* until `ios-beta-device-test-plan.md` section C is executed on an iPhone with a Keycloak that has the D18 flow. Keep CUSTOMER and KITCHEN policies on OPTIONAL; there is no in-app screen to turn MFA on or off.

## 8. Verified vs not verified
| Claim | Status |
|---|---|
| Beta identity differs from development; endpoints HTTPS; redirect registered exactly; workflow gated; no signing files tracked | verified by `scripts/test_ios_beta_config.py` (CI) |
| Expo config resolves for the preview variant | verified (`expo config`) |
| Redirect registration, PKCE login, step-up prompt against Keycloak | rehearsed locally; Contabo realm **not verified** |
| Sign-in, SecureStore, sign-out, account switch | simulator CI only; **device not verified** |
| EAS build, signing, upload, TestFlight install | **not run** (needs owner credentials) |
| MFA-enabled mobile journey | **not verified on a device** |
| Preview reachable from a phone network | **not verified; allow-list decision pending** |
