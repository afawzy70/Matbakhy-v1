# Configurable multi-factor authentication (owner decision D18, 2026-10-18)

Supersedes the earlier decision to make staff MFA universally mandatory. **Keycloak performs MFA** (OIDC step-up, TOTP). Spring Boot has no OTP implementation; it stores the administrator's policy and the user's preference, requires the identity provider's MFA assertion where the effective policy says so, and audits every change.

## Policy model
| Mode | Meaning |
|---|---|
| `DISABLED` | MFA enrolment and use are not offered for the category. Existing enrolled factors are **not** removed. Password sign-in and every other control stay as they are. |
| `OPTIONAL` | Each user decides for their own account (preference). |
| `REQUIRED` | The user must complete enrolment and authentication before any protected function and cannot turn it off. A user preference never overrides it. |

Categories (administrator-set, `mfa_policy` table, **PREVIEW default: OPTIONAL for all three**): `CUSTOMER` (every account), `KITCHEN` (owns an approved kitchen), `STAFF` (holds an active staff grant). An account in several categories gets the **most protective applicable mode** (REQUIRED > OPTIONAL > DISABLED); a missing policy row fails safe to REQUIRED, never to DISABLED. `required = REQUIRED || (OPTIONAL && user preference on)`; `available = effective mode != DISABLED`.

## How it works
1. **Authentication (Keycloak).** Browser flow `matbakhy browser` = cookie / password at level 1; a second level adds `auth-otp-form` and is entered when the client requests `acr_values=mfa` (mapped to level 2 by `acr.loa.map`). A normal sign-in never asks for OTP. A step-up on an existing SSO session asks only for the OTP; an account with no factor is led into TOTP enrolment (`CONFIGURE_TOTP`) and cannot pass without it. The API's web step-up (`/oauth2/authorization/keycloak?stepUp=true`) sends `acr_values=mfa 2` and `max_age=0`.
2. **Enforcement (API, server-side).** `MfaEnforcementFilter` runs on both security chains. For an active account whose effective decision is `required`, every protected `/api/**` call must carry the provider's MFA assertion (`acr` in `mfa-acr-values`, or `amr` containing `otp`/`mfa`/`hwk`); otherwise `403 STEP_UP_REQUIRED`. Reachable without MFA so the user can recover: `GET /api/v1/me`, `/api/v1/me/security[/**]`, logout, public reads. The decision is recomputed from the database on every request, so a policy change takes effect on the next call (no cached grant of access).
3. **Own settings** (`GET /api/v1/me/security`, `PUT /api/v1/me/security/mfa`): enabling is accepted only on a session that has just passed MFA (no self lock-out); disabling needs a **recent** second factor (`step-up-max-age`, default 10 min), is refused under REQUIRED (`MFA_REQUIRED_BY_POLICY`), is unavailable under DISABLED (`MFA_UNAVAILABLE`), and **ends the user's provider sessions** (needs the admin client; best effort, logged). Enrolling and removing the authenticator use Keycloak's own pages (the account console link in the settings response); if a REQUIRED user removes their factor there, the next request demands step-up and enrolment again.
4. **Administration** (staff permissions, recent MFA, reason, audit): `GET/PUT /api/v1/ops/security/mfa-policy[/{category}]` (`security.mfa_policy`, optimistic `expectedVersion`), `POST /api/v1/ops/accounts/{accountId}/mfa-reset` (`security.mfa_reset`).
5. **Recovery.** The reset deletes the account's OTP credentials at Keycloak through a service account and ends its sessions; the user then enrols a new factor at next step-up. Guards: permission, recent MFA of the administrator, reason of at least 10 characters, never one's own account, audited (`mfa.reset`, including denials). **No administrator can read an OTP secret**: the admin API used here lists only credential id and type (verified: the credentials response has no secret fields) and nothing in the application requests or stores a factor secret.

## Configuration
| Setting | Purpose |
|---|---|
| `OIDC_MFA_ACR_VALUES` (default `mfa,2`) | acr values that count as MFA; `amr` otp/mfa/hwk also counts |
| `OIDC_STEP_UP_MAX_AGE` (default `10m`) | "recent" window for weakening changes and staff high-risk actions |
| `OIDC_ADMIN_CLIENT_ID` / `OIDC_ADMIN_CLIENT_SECRET` | Keycloak service account (`matbakhy-admin`, realm roles `view-users`, `manage-users`) used only for the reset and for ending sessions; blank secret = those two operations return `503` while policy and preference keep working |
| `OIDC_ACCOUNT_CONSOLE_URL` | overrides the account-console link (default: issuer + `/account/#/security/signingin`) |

The Keycloak admin API is reachable on the internal network only (the proxy never publishes `/admin`); the secret is generated per environment (`deploy.sh`, `scripts/dev.sh`) and never committed.

## Migration and rollout (no mandatory MFA, no session invalidation until verified)
1. Deploy code with V31 (`mfa_policy` seeded OPTIONAL, `user_mfa_preference`, two new permission codes). Behaviour is unchanged for everyone: nobody is required and nobody's session is touched.
2. Apply the realm flow to the **running** Keycloak realm (`--import-realm` skips an existing realm): create the `matbakhy browser` flow, bind it as the browser flow, set the realm/client `acr.loa.map` to `{"mfa":"2"}`, add the `amr` mapper to the clients, and create the `matbakhy-admin` service-account client with `view-users`/`manage-users`. Needs someone with Docker/`kcadm` access on the host (the agent account has none). Until then step-up asks for nothing extra and enabling MFA cannot complete — the API stays safe (it never trusts a missing assertion).
3. Verify on the live realm with synthetic accounts: normal login has no OTP; step-up asks OTP or enrolment; token carries `acr=mfa`; reset removes the credential.
4. Create dedicated synthetic accounts for the preview: a customer, a kitchen owner, a staff administrator holding `security.mfa_policy` and `security.mfa_reset`. Keep `preview-ops-*` password-only for the E2E suite.
5. Only then may an administrator switch a category to REQUIRED, starting with STAFF. Every affected user is asked for step-up on their next request; plan for a test account that is already enrolled. Do **not** set CUSTOMER or KITCHEN to REQUIRED until the customer mobile app handles `403 STEP_UP_REQUIRED` (it does not today: it would show a generic error).

## Residual risks (recorded)
* Live Keycloak (preview/staging) has not been changed or verified by the agent (no Docker/SSH access); verified in CI against the pinned Keycloak 26.8.0 image and locally on the same version.
* Mobile: no step-up handling and no Security screen; keep CUSTOMER/KITCHEN on OPTIONAL until added. A mobile user who turned MFA on in the web app would get `STEP_UP_REQUIRED` in the mobile app.
* Access tokens already issued stay valid up to their lifetime (≤ 5 min) after a reset or a switch-off; the session end blocks refresh tokens.
* Enrolment through Keycloak raises `acr=mfa` but not `amr=otp`; the API accepts either assertion.
* TOTP only (no WebAuthn/recovery codes) in this scope.
* The Keycloak service account can manage users; its blast radius is limited by the internal-only admin path and the secret handling above, and the application code uses only credential listing/removal and logout.

## Tests
`MfaPolicyResolveTest` (precedence matrix), `MfaPolicyIntegrationTest` (defaults, permissions and step-up for policy changes, REQUIRED enforcement, preference vs REQUIRED, enable/disable and recent authentication, DISABLED, role-based categories, reset guards), `MfaIdpTest` (real Keycloak: normal login, step-up, wrong code, enrolment, SSO session), `MfaResetIntegrationTest` (real Keycloak + API over bearer tokens: enforcement and the audited reset), web tests for the Security sections and the administrator controls. `StaffAssuranceTest` is unchanged: high-risk staff actions keep requiring recent MFA whatever the policy.
