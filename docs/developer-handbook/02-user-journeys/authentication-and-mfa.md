# User journeys: authentication and MFA

> **Owner role:** Security and Keycloak
> **Status:** PARTIALLY IMPLEMENTED — OIDC sign-in, policy-driven TOTP step-up, preference, reset and session invalidation are implemented; applying the realm migration to the live realm and phone-device verification are owner-gated and NOT VERIFIED.

Keycloak (realm `matbakhy`) authenticates people; Spring Boot authorises. The backend has no password or OTP code. Design: [Identity and security](../03-architecture/identity-and-security.md). Policy text (authoritative for intent): `docs/security/mfa-policy.md`. Keycloak admin console: `docs/operations/14-keycloak-admin-console.md`.

## Identity building blocks

| Piece | Where | Facts (from code and config) |
|---|---|---|
| Realm templates | `infra/identity/matbakhy-realm.json` (local), `deploy/web-preview/identity/matbakhy-realm.preview.json` (preview) | Realm `matbakhy`; self-registration off; email verification on; brute-force protection on; access token lifespan 300 s; SSL required external |
| Web client | `matbakhy-web`, confidential, authorization code only, direct grants off, PKCE `S256`, `acr.loa.map {"mfa":"2"}`, mappers `api-audience` and `amr` | Redirect URI is the **API** callback `/login/oauth2/code/keycloak` (local `http://localhost:${API_PORT}/…`, preview `https://api.${PREVIEW_DOMAIN}/…`); post-logout redirects to the web hosts |
| Mobile client | `matbakhy-mobile`, public, PKCE `S256`, same ACR map and mappers | Redirect `matbakhy-dev://auth/callback` (dev). Present only in the local template; the preview template has none (NOT VERIFIED on the live realm) |
| Admin client | `matbakhy-admin`, service account, no interactive flow | Used only by the backend for MFA reset and ending provider sessions (`backend/app/src/main/java/com/matbakhy/app/mfa/KeycloakAdmin.java`); secret is generated per environment and never committed |
| Realm roles | none defined in either template | Authorisation is not based on Keycloak roles |
| Synthetic users | local: `customer-a`, `customer-b`, `cook-a`, `ops-a`; preview: `preview-customer-1/2`, `preview-cook-1`, `preview-ops-1/2` | Passwords are generated outside the repository |

### Token claims the backend uses

Two security chains in `backend/app/src/main/java/com/matbakhy/app/security/SecurityConfiguration.java`:

* **Bearer chain** (header `Authorization: Bearer`, mobile and tools): stateless JWT validated for signature (JWKS), issuer, expiry and **audience** (`apiAudience`). No cookies, no CSRF.
* **Web chain** (browser): OIDC authorization code login, server session in PostgreSQL (`SPRING_SESSION*`, V2), cookie `MATBAKHY_SESSION` (HttpOnly, SameSite=Lax, Secure when deployed), CSRF token cookie `XSRF-TOKEN` readable by the SPA, CORS allow-list. Idle timeout default 30 minutes (`WEB_SESSION_TIMEOUT`). `returnTo` is accepted only when its origin is in the allow-list (`WebReturnTo.java`).

Claims read by `AccountMapper`: `iss` + `sub` (the account key; creates or finds `app_user`), `email_verified`, `name` or `preferred_username`, `locale`, and the assurance claims `acr`, `amr`, `auth_time`. **Role claims are ignored.** Application permissions come from `user_grant` rows; "cook" means owning an approved kitchen. See [Backoffice administration](backoffice-admin.md#there-are-no-staff-roles-only-permission-grants).

### MFA model (summary; full text in `docs/security/mfa-policy.md`)

* Modes per population: `OPTIONAL`, `REQUIRED`, `DISABLED`. Populations: `CUSTOMER` (every account), `KITCHEN` (owner of an approved kitchen), `STAFF` (active grant). Stored in `mfa_policy` (V31, seeded OPTIONAL for all three). An account in several populations gets the most protective mode (REQUIRED over OPTIONAL over DISABLED); a missing row fails safe to REQUIRED.
* `required = REQUIRED or (OPTIONAL and the user turned it on)`; `available = mode != DISABLED`.
* Enforcement: `MfaEnforcementFilter` (both chains) returns `403 STEP_UP_REQUIRED` for protected `/api/**` calls when required and the token or session lacks an MFA assertion (`acr` in the configured `mfa-acr-values`, default `mfa,2`, or `amr` containing `otp`/`mfa`/`hwk`). Always reachable: `/api/v1/me/security[/**]`, `/api/v1/auth/logout`, public reads and the profile read.
* Staff high-risk actions need recent MFA **regardless of mode** (`StaffAssurance.recentMfa`, window `OIDC_STEP_UP_MAX_AGE` default 10 minutes).

## Common items for the journeys

**Backend classes.** `backend/app/src/main/java/com/matbakhy/app/mfa/` (`MfaController`, `MfaAdminController`, `MfaPolicyService`, `KeycloakAdmin`, `ApplicationSessions`) and `backend/app/src/main/java/com/matbakhy/app/security/`. **Tables.** `app_user`, `user_grant`, `mfa_policy`, `user_mfa_preference`, `user_mfa_reset`, `SPRING_SESSION`, `audit_event`. **Tests (all).** `backend/app/src/test/java/com/matbakhy/app/mfa/MfaPolicyResolveTest.java`, `MfaPolicyIntegrationTest.java`, `MfaResetAuditIntegrationTest.java`; real-Keycloak tests `backend/app/src/test/java/com/matbakhy/app/identity/MfaIdpTest.java`, `MfaResetIntegrationTest.java`, `IdentityIntegrationTest.java` (test realm `backend/app/src/test/resources/keycloak/test-realm.json`); `backend/app/src/test/java/com/matbakhy/app/security/StaffAssuranceTest.java`; UI tests in `apps/customer-web/src/App.test.tsx`, `apps/operations-web/src/App.test.tsx`, `apps/customer-mobile/src/signIn.test.ts`. Results for the current commit: NOT VERIFIED here.

## AM-1 Public sign-in (web and mobile)

1. **Goal.** Sign in with the authorization-code flow and PKCE; never see or store passwords in the apps.
2. **Actor.** Any person with a Keycloak account.
3. **Prerequisites.** Account exists (no self-registration in the templates); web hosts in the CORS and `returnTo` allow-lists.
4. **Start.** Web: "Sign in" link → `{API}/oauth2/authorization/keycloak?returnTo=<url>`. Mobile: Account tab "Sign in".
5. **Actions.** Authenticate at Keycloak (password; no OTP at level 1 for a normal sign-in).
6. **Responses.** Web: callback `/login/oauth2/code/keycloak`, session created, redirect to the validated `returnTo` (or the configured post-login URL). Mobile: app receives `<scheme>://auth/callback?code&state`, strict check, token exchange, tokens in secure storage.
7. **API / endpoints.** Framework routes `/oauth2/authorization/keycloak`, `/login/oauth2/code/keycloak`; `GET /api/v1/me`.
8. **Handlers.** `SecurityConfiguration` (`webChain`, `bearerChain`), `AccountMapper`, `WebAccountFilter`, `WebReturnTo`; mobile `apps/customer-mobile/src/auth.ts`, `redirect.ts`.
9. **DB.** `app_user`, `SPRING_SESSION`.
10. **Transitions.** anonymous → authenticated; account `status` must be `ACTIVE` for the `ACCOUNT_ACTIVE` authority (a deletion-requested account may only repeat its deletion request).
11. **Errors.** Unregistered redirect URI or forged `state`/code refused (CFG-003, TC-131); expired access token → 401; disabled account → `ACCOUNT_DISABLED`.
12. **Tests.** `IdentityIntegrationTest.java`, `apps/customer-mobile/src/auth.test.ts`, `signIn.test.ts`.
13. **Owner E2E.** CFG-001, CFG-002, CFG-003, CFG-004, CFG-006, CFG-036, CFG-037, E2E-DISC-003; J0.
14. **Status.** IMPLEMENTED.
15. **Limitations.** Only Keycloak lockout throttles logins (CFG-039); CSP is report-only (CFG-038).

## AM-2 Sign-out and session invalidation

1. Goal: end the app session and the provider session. 2. Actor: signed-in person. 3. Prereq: session.
4. Start: "Sign out". 5. Actions: web posts `/api/v1/auth/logout` with CSRF; mobile revokes the refresh token (best effort) and clears secure storage.
6. Responses: web gets `{"idpLogoutUrl": …}` and navigates there so the next login asks for credentials; mobile keeps no private cache.
7. API: `/api/v1/auth/logout` (framework logout; not in `contracts/endpoints.json`). 8. Handler: logout config in `SecurityConfiguration` (`idpLogout`), `AppContext.tsx` `endSession`. 9. DB: session rows deleted.
10. Transitions: session active → invalidated. Other invalidations: turning one's own MFA off, an administrative reset (`ApplicationSessions` finds Spring Session rows by the provider subject), deleting a session row (CFG-004).
11. Errors: server unreachable → web does not clear local state. 12. Tests: customer web MBQA-003 block; `MfaPolicyIntegrationTest.java`. 13. CFG-002, CFG-004, E2E-DISC-003, E2E-DISC-004. 14. IMPLEMENTED. 15. Access tokens already issued remain valid up to their lifetime (≤ 5 minutes) after a reset or switch-off (`docs/security/mfa-policy.md`, residual risks).

## AM-3 Enrol TOTP and step up

1. Goal: obtain an MFA-level assertion, enrolling an authenticator the first time.
2. Actor: any account that must or wants MFA.
3. Prerequisites: policy not DISABLED for the account (or the account holds a staff grant: the staff step-up stays available); the realm has the `matbakhy browser` flow with the OTP level and `acr.loa.map` (owner-gated migration `deploy/web-preview/identity/mfa-migration.kcadm.sh`, cases CFG-028 to CFG-032).
4. Start: web `Account` → security panel ("verify now"/"turn on"), operations web verify link, or an automatic prompt after `403 STEP_UP_REQUIRED`. Mobile: the step-up banner.
5. Actions: open `/oauth2/authorization/keycloak?stepUp=true&returnTo=…`; the resolver adds `acr_values` (from `OIDC_MFA_ACR_VALUES`) and `max_age=0`. Mobile adds `acr_values=mfa 2` and `prompt=login`. Keycloak asks for the OTP, or leads an account without a factor into TOTP enrolment (`CONFIGURE_TOTP`).
6. Responses: new session/token with `acr=mfa`; protected calls succeed.
7. API: `/oauth2/authorization/keycloak?stepUp=true`, `GET /api/v1/me/security` (112).
8. Handlers: `stepUpResolver` in `SecurityConfiguration`, `MfaPolicyService.decide`, `StaffAssurance`. 9. DB: `mfa_policy`, `user_mfa_preference`.
10. Transitions: assurance level 1 → 2 within the session. 11. Errors: wrong or reused OTP rejected by Keycloak with brute-force protection (CFG-022); DISABLED category: no second factor requested by the API (CFG-012).
12. Tests: `MfaIdpTest.java` (normal login, step-up, wrong code, enrolment, SSO session).
13. Owner E2E: CFG-102, CFG-030, CFG-033, CFG-104, CFG-024 to CFG-026, CFG-108, CFG-109; J7, J8.
14. Status: IMPLEMENTED in code; live-realm migration and device runs NOT VERIFIED (checked only by the owner suite).
15. Limitations: TOTP only; no recovery codes or WebAuthn. Mobile has no enrolment screen: enrolment happens in the system browser during step-up.

## AM-4 Own MFA preference (OPTIONAL policy)

1. Goal: a person turns MFA on or off for their account. 2. Actor: any signed-in account. 3. Prereq: mode OPTIONAL.
4. Start: web account page, operations web Security tab. 5. Actions: turn on (needs a session with a valid MFA assertion; refused with `MFA_UNAVAILABLE` when the mode is DISABLED); turn off (needs recent MFA).
6. Responses: turning off also ends the user's application sessions and, if the admin client is configured, provider sessions (provider part best effort).
7. API: `GET /api/v1/me/security` (112), `PUT /api/v1/me/security/mfa` body `{enabled}` (113). 8. `MfaController`, `MfaPolicyService`, `ApplicationSessions`, `KeycloakAdmin`. 9. `user_mfa_preference`, `audit_event`.
10. Transitions: preference false ↔ true. 11. Errors: field codes `MFA_REQUIRED_BY_POLICY` (REQUIRED), `MFA_UNAVAILABLE` (turning ON while DISABLED), `STEP_UP_REQUIRED` (HTTP 403).
12. Tests: `MfaPolicyIntegrationTest.java`. 13. CFG-008, CFG-011, CFG-012, CFG-120; J7. 14. IMPLEMENTED (web only). 15. No mobile preference screen.

## AM-5 Administrator sets the MFA policy

1. Goal: change a population between OPTIONAL, REQUIRED and DISABLED. 2. Actor: `security.mfa_policy` + recent MFA. 3. Prereq: current `version`; reason.
4. Start: operations web Security tab. 5. Actions: choose category and mode, reason, save.
6. Responses: effect on the next request of every affected account (the decision is recomputed from the database each time); audited.
7. API: `GET /api/v1/ops/security/mfa-policy` (114), `PUT /api/v1/ops/security/mfa-policy/{category}` (115). 8. `MfaAdminController`, `MfaPolicyService`. 9. `mfa_policy`, `audit_event`. 10. Mode and version change. 11. Errors: `VERSION_CONFLICT`, missing reason, `STEP_UP_REQUIRED`, refusal ladder for guests, customers, ungranted staff and staff without recent MFA (CFG-016).
12. Tests: `MfaPolicyIntegrationTest.java`. 13. CFG-014, CFG-015, CFG-016, CFG-122, E2E-OPS-111; J7. 14. IMPLEMENTED. 15. Do not set CUSTOMER or KITCHEN to REQUIRED until the mobile step-up has device evidence (`docs/security/mfa-policy.md`).

## AM-6 Administrative MFA reset

1. Goal: recover an account that lost its authenticator. 2. Actor: `security.mfa_reset` + recent MFA. 3. Prereq: reason of at least 10 characters; not one's own account; admin client configured, else 503 (CFG-023).
4. Start: Security tab. 5. Actions: enter account id and reason, confirm.
6. Responses: a reset time is recorded first so older MFA proofs stop counting; OTP credentials are deleted at Keycloak, provider sessions ended, application web sessions ended; the user re-enrols at next step-up. An `mfa.reset.attempt` audit entry is written before anything destructive; a partial failure is audited and retry converges (CFG-020).
7. API: `POST /api/v1/ops/accounts/{accountId}/mfa-reset` (116). 8. `MfaAdminController`, `KeycloakAdmin`, `ApplicationSessions`. 9. `user_mfa_reset`, `audit_event`. 10. Reset time monotonic per account.
11. Errors: permission, recent MFA, reason length, self-reset, unknown account (CFG-018). No administrator can read an OTP secret.
12. Tests: `MfaResetIntegrationTest.java`, `MfaResetAuditIntegrationTest.java`. 13. CFG-018 to CFG-021, CFG-023; J7. 14. IMPLEMENTED. 15. Pre-reset access tokens (≤ 5 minutes) stay valid for non-MFA purposes.

## AM-7 Keycloak admin console (operators, high level)

Since the owner approval of 2026-10-19 the Keycloak administration console is published on a dedicated public hostname, `kc-admin.<preview domain>`, **in Preview only** (not approved for staging or production). `deploy/web-preview/Caddyfile` forwards only `/admin`, `/realms/master` and static assets there; the `matbakhy` realm, health and metrics answer 404, and `id.*` still answers 404 for `/admin` and `/realms/master`. The whole host is fail-closed: it answers 404 unless the switch file `admin-console-public` exists, which `admin-console.sh enable` creates only after hardening and live verification (the deploy workflow runs it after each release; `./deploy.sh admin-console disable` removes it at once).

Two different MFA regimes exist and must not be confused. Application accounts (CUSTOMER, KITCHEN, STAFF) follow the database policy in `mfa_policy`, initially **OPTIONAL** for all three (V31, AM-5). Master-realm administrators of the console have **mandatory password plus OTP**: the `matbakhy master browser` flow sets both the password form and the OTP form to REQUIRED (`deploy/web-preview/identity/admin_console.py`, checked by `scripts/test_keycloak_admin_console.py`), there is no public OTP enrolment, and the password grant is off for master clients.

Operators use it to inspect users and the realm flow, never to share passwords in the repository. Procedure, tooling and verification scripts: `docs/operations/14-keycloak-admin-console.md`; MFA migration steps: `docs/operations/13-keycloak-mfa-migration.md`; scripts under `deploy/web-preview/identity`. Status: IMPLEMENTED as operator tooling (Preview only); live changes are owner-gated. Whether the switch is enabled on the running server: NOT VERIFIED.

## Known limitations and documentation gaps

* Case-text drift: owner cases CFG-001 and CFG-121 (`docs/quality/owner-acceptance/cases/config-security.json`) still describe the older boundary ("Keycloak admin console and master realm are never published"). The code, `docs/operations/14-keycloak-admin-console.md` and the Caddyfile follow the 2026-10-19 owner approval of the public Preview hostname. Trust the code and operations document; the case files are not edited from the handbook and need an owner-acceptance update.
* `docs/security/mfa-policy.md` rollout step 5 says the mobile app does not handle `STEP_UP_REQUIRED`; the residual-risk section and the code (`apps/customer-mobile/src/AppContext.tsx`) show a banner exists. It also cites an iOS device test plan file under a mobile docs directory that does not exist in the repository.
* The preview realm template lacks the `matbakhy-mobile` client.
* No Keycloak realm roles are used, despite generic "role" wording in older documents; permissions live in `user_grant`.
