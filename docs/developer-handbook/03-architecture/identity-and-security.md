# Identity and security

> **Owner role:** Security and Keycloak
> **Status:** PARTIALLY IMPLEMENTED — authentication, authorization, MFA policy and proxy controls are implemented; CSP is Report-Only, live Keycloak changes are operator-applied, and no production hardening exists

Business view of roles: [actors and permissions](../01-business/actors-and-permissions.md). User-facing sign-in and MFA flows: [authentication and MFA journey](../02-user-journeys/authentication-and-mfa.md). Diagrams: [OIDC](diagrams.md#6-authentication-oidc), [MFA step-up](diagrams.md#7-mfa-step-up). Canonical MFA design: `docs/security/mfa-policy.md`; broader rules: `docs/engineering/04-security.md`.

## Principles (as implemented)

1. **The identity provider authenticates; the application authorizes.** Keycloak proves who the user is. What the user may do is decided only from the application database (`app_user`, `user_grant`, `kitchen`, `mfa_policy`). Token role claims are ignored (`SecurityConfiguration` javadoc).
2. **Deny by default.** Both filter chains end with `anyRequest().denyAll()`; `/api/**` requires the `ACCOUNT_ACTIVE` authority except an explicit public-GET list and the anonymous activity endpoint.
3. **Object-level checks in services, 404 for foreign objects.** See [backend architecture](backend-architecture.md#validation-and-error-handling).
4. **Server state is the only authority** for prices, capacity and transitions; clients never send them.

## Keycloak usage

Keycloak 26.8.0 (digest-pinned) runs as a container: `start-dev --import-realm` locally (`infra/compose.yaml`), `start --import-realm` on the preview with its own `keycloak` database (`deploy/web-preview/compose.yaml`). The realm `matbakhy` is configuration-as-code:

| File | Used by |
|---|---|
| `infra/identity/matbakhy-realm.json` | Local stack (rendered by `scripts/dev.sh` into a git-ignored generated directory with local secrets) |
| `deploy/web-preview/identity/matbakhy-realm.preview.json` | Preview (rendered by `deploy.sh render` into the server's generated directory) |

Realm facts verified in those files: registration disabled, brute-force protection on, `sslRequired: external`, access token lifespan 300 s, custom browser flow `matbakhy browser` with LoA levels (level 1 password, level 2 adds OTP when `acr` `mfa`/`2` is requested; `acr.loa.map` is `{"mfa": "2"}`).

| Client | Type | Purpose |
|---|---|---|
| `matbakhy-web` | confidential, authorization code, PKCE S256, direct grant off | The API itself logs web users in on behalf of both web apps (backend-for-frontend pattern) |
| `matbakhy-mobile` | public, authorization code, PKCE S256 | Customer mobile app. **Present in the local realm; absent from the preview realm file** (see gaps) |
| `matbakhy-admin` | confidential service account, no standard flow | Used only by the API for MFA reset and ending sessions (roles `view-users`, `manage-users`) |

An audience mapper adds `matbakhy-api` to access tokens; the API requires it.

Provider users are not the application's users: on first authenticated request `AccountMapper` calls `AccountDirectory.resolve(ExternalIdentity)` which creates or finds an `app_user` row by `(issuer, subject)`. A `bootstrap-admin-subject` setting (`STAFF_BOOTSTRAP_SUBJECT`) grants `staff.grant` and `audit.read` to that subject on first login while no administrator exists; otherwise staff are made only by existing administrators through `StaffController` (permission `staff.grant`, step-up required).

## Token claims used

From the validated token/ID token (`AccountMapper`, `StaffAssurance`): `sub`, `iss`, `email_verified`, `name`/`preferred_username`, `locale`, and assurance claims `acr`, `amr`, `auth_time`. Nothing else is trusted. The API checks signature (JWKS fetched from the back-channel issuer URL), issuer, expiry (with injectable `Clock`) and audience.

## Two authentication paths

| | Web (customer-web, operations-web) | Mobile |
|---|---|---|
| Chain | `webChain` (order 2) | `bearerChain` (order 1, selected by an `Authorization: Bearer` header) |
| Login | Spring Security `oauth2Login` against client registration `keycloak`; returns to the page given by `returnTo` only if its origin is in the allowed list (`WebReturnTo`, prevents open redirects) | App performs PKCE itself and sends the access token |
| Session | Server-side in PostgreSQL (Spring Session JDBC), cookie `MATBAKHY_SESSION`: HttpOnly, SameSite=Lax, Secure when deployed, 30 min timeout (`WEB_SESSION_TIMEOUT`) | Stateless; the app refreshes its own token |
| CSRF | Enabled (`csrf.spa()` + cookie repository): readable `XSRF-TOKEN` cookie, header `X-XSRF-TOKEN` on every mutation; set for the parent domain via `WEB_COOKIE_DOMAIN` so `app.` and `ops.` can read a cookie set by `api.` | Disabled (bearer tokens are not sent ambiently) |
| CORS | Exact allow-list `WEB_ALLOWED_ORIGINS`, credentials allowed, methods GET/POST/PUT/PATCH/DELETE, headers `Content-Type, X-XSRF-TOKEN, Idempotency-Key, If-Match, X-Request-Id, Authorization` | Same configuration applies |
| Logout | `POST /api/v1/auth/logout` invalidates the session, deletes the cookie and returns `idpLogoutUrl` so the client can end the Keycloak session too | App revokes the refresh token (best effort) and clears secure storage |
| Account state | `WebAccountFilter` rebuilds the authentication every request so a suspension takes effect immediately | `JwtAuthenticationConverter` calls `AccountMapper` every request |

Account status maps to authorities: `ACTIVE` gets `ACCOUNT_ACTIVE` plus one `PERM_<code>` per active, unexpired, unrevoked `user_grant`; `DELETION_REQUESTED` gets only the ability to repeat its deletion request; `SUSPENDED` and `DELETED` get nothing (`ACCOUNT_DISABLED` 403).

## Authorization model

* **Customer**: any active account. Ownership checks use `customer_id` in SQL.
* **Cook**: an account that owns a kitchen (`KitchenDirectory.ownedBy`); cook endpoints live under `/api/v1/cook/**` and check kitchen ownership in the service (`kitchen_id` in the `lock` predicate).
* **Staff**: permission codes in the `Permission` enum (`backend/modules/identity/src/main/java/com/matbakhy/identity/api/Permission.java`): `staff.grant`, `audit.read`, `cook.review`, `content.moderate`, `order.ops`, `order.exception_cancel`, `order.handover_override`, `support.case`, `finance.read`, `finance.verify`, `finance.refund_approve`, `finance.settlement`, `config.read`, `config.write`, `integration.ops`, `security.mfa_policy`, `security.mfa_reset`. There is no super-admin: "Admin is not an implicit superuser". The codes are also whitelisted by the check constraint on `user_grant.permission`, so a new code needs a migration (V31 is the model).
* Controllers use `@PreAuthorize("hasAuthority('PERM_<code>')")` (method security is enabled). Accessing someone else's resource as staff goes through `AccessPolicy.requireOwnerOrPermitted`, which audits the access.
* Denials: 401 `UNAUTHENTICATED`, 403 `FORBIDDEN`, 403 `ACCOUNT_DISABLED`, 403 `STEP_UP_REQUIRED`, all written by `ApiSecurityErrors` in the standard error body; denials are written to `audit_event` with outcome `DENIED`.

## MFA policy: storage and enforcement

Owner decision D18 replaced "staff MFA is mandatory" with configurable policy. Keycloak performs the authentication (TOTP); the application stores **policy and preference** and enforces *that an MFA assertion exists*.

* **Storage** (`backend/app/src/main/resources/db/migration/V31__mfa_policy.sql`): `mfa_policy(category, mode, reason, updated_by, version)` with categories `CUSTOMER`, `KITCHEN`, `STAFF` and modes `DISABLED`, `OPTIONAL`, `REQUIRED` (seeded `OPTIONAL` for all three: the preview default); `user_mfa_preference(user_id, enabled)`; `user_mfa_reset(user_id, reset_at, reset_by)`.
* **Decision** (`MfaPolicyService.decide`): categories of the account are computed per request (everyone is `CUSTOMER`; owning a kitchen adds `KITCHEN`; holding an active grant adds `STAFF`); the effective mode is the most protective applicable one (`REQUIRED` > `OPTIONAL` > `DISABLED`); a category with a missing policy row defaults to `REQUIRED`; `required = REQUIRED or (OPTIONAL and the user's preference on)`; `available = effective != DISABLED`.
* **Enforcement** (`MfaEnforcementFilter`, on both chains): for an active account whose decision is `required`, every protected `/api/**` call must show an assertion: `acr` in `OIDC_MFA_ACR_VALUES` (default `mfa,2`) or `amr` containing `mfa`, `otp` or `hwk`, and authentication not older than the account's last reset. Otherwise the filter throws `StepUpRequiredException` -> 403 `STEP_UP_REQUIRED`. Always reachable: `/api/v1/me/security/**`, logout, public GETs, `GET /api/v1/me`, the activity endpoint.
* **Step-up for staff high-risk actions** (`StaffAssurance.recentMfa`): endpoints such as staff grants, cook review decisions, configuration writes, handover override, deposit verification and MFA administration add `and @staffAssurance.recentMfa(authentication)`: MFA assertion **and** `auth_time` within `OIDC_STEP_UP_MAX_AGE` (default 10 minutes) **and** newer than the last reset. This applies whatever the policy mode, so a `DISABLED` STAFF category does not remove it (the web step-up resolver keeps requesting MFA for STAFF accounts).
* **Web step-up** (`stepUpResolver` in `SecurityConfiguration`): `/oauth2/authorization/keycloak?stepUp=true` adds `acr_values` and `max_age=0` to the authorization request, unless the account's categories are all `DISABLED`. The web apps expose this through `stepUpUrl(...)` in `api.ts`.
* **User settings**: `GET /api/v1/me/security`, `PUT /api/v1/me/security/mfa` (`MfaController`): enabling requires a session that just passed MFA; disabling needs recent MFA and is refused under `REQUIRED` (field error `MFA_REQUIRED_BY_POLICY`) and when unavailable (`MFA_UNAVAILABLE`); turning it off ends the user's web sessions.
* **Administration** (`MfaAdminController`): `GET/PUT /api/v1/ops/security/mfa-policy[/{category}]` (permission `security.mfa_policy`, optimistic `expectedVersion`, reason, audited) and `POST /api/v1/ops/accounts/{accountId}/mfa-reset` (permission `security.mfa_reset`, never one's own account, reason of 10+ characters, audit entry written before anything destructive, then OTP credentials deleted and Keycloak plus application sessions ended through `KeycloakAdmin`).
* The Keycloak admin client is optional: with a blank `OIDC_ADMIN_CLIENT_SECRET` the reset and session-ending operations answer 503 while policy and preference keep working.

State: IMPLEMENTED in code and integration tests (`backend/app/src/test/java/com/matbakhy/app/mfa/MfaPolicyIntegrationTest.java`, `backend/app/src/test/java/com/matbakhy/app/security/StaffAssuranceTest.java`, `backend/app/src/test/java/com/matbakhy/app/identity/MfaResetIntegrationTest.java`). Applying the realm flow to the *running* preview Keycloak is an operator task (`deploy/web-preview/migrate-mfa.sh`, `deploy/web-preview/identity/mfa-migration.kcadm.sh`; `docs/operations/13-keycloak-mfa-migration.md`). Whether the live realm currently has the flow applied is NOT VERIFIED from the repository.

## Sessions

Web sessions are rows in `SPRING_SESSION*` (V2), so they survive API restarts and several instances. Logout and MFA turn-off delete rows. After an administrative MFA reset, application sessions of that account are ended through `ApplicationSessions` (sessions indexed by provider subject). Mobile sessions are provider sessions plus refresh tokens held on the device.

## Browser and proxy hardening

Defined in `deploy/web-preview/Caddyfile` (tested by `scripts/test_proxy_access.py`):

* **Security headers** on every host: `Strict-Transport-Security max-age=31536000`, `X-Content-Type-Options: nosniff`, `Referrer-Policy: strict-origin-when-cross-origin`, `X-Robots-Tag: noindex, nofollow`, `Server` removed; the SPA hosts add `X-Frame-Options: DENY`.
* **CSP**: `Content-Security-Policy-Report-Only` on `app.` and `ops.`: `default-src 'self'`, scripts and styles from self only (`style-src-attr 'unsafe-inline'` for React inline style attributes), images from self, `data:` and the media host, `connect-src` self and the API host, `object-src 'none'`, `frame-ancestors 'none'`. **Not enforced yet**; promotion plan in `docs/security/csp-rollout.md` (SEC-004 is recorded as mitigated in part, open).
* **Network allow-lists (deny by default).** Two classes of client IPs: `CUSTOMER_ALLOWED_IPS` (app, API, id, media) and `STAFF_ALLOWED_IPS` (ops web, and on the API host the paths `/api/v1/ops/**` and `/api/v1/cook/**`). Both default to `ALLOWED_CLIENT_IPS`; nothing is public until the owner sets the customer class to `0.0.0.0/0 ::/0`. Privileged operations never follow the customer class. Outsiders get `403 Not available`.
* **API host exposure:** only `/api/*`, `/oauth2/*`, `/login/oauth2/*`, `/logout`; everything else 404. Actuator is not reachable through the proxy, and Prometheus runs on a separate management port that no proxy forwards.
* **Identity host:** `id.<domain>` serves the `matbakhy` realm and answers 404 for `/admin` and `/realms/master`.
* **Admin console boundary:** the Keycloak admin console is on `kc-admin.<domain>`, deliberately *not* IP-restricted (owner approval 2026-10-19, preview only), protected by master-realm password plus mandatory OTP, no password grant, brute-force protection; only `/admin`, `/realms/master` and static assets are forwarded; the whole host answers 404 unless the flag file `/srv/flags/admin-console-public` exists (fail closed). Operated by `deploy/web-preview/admin-console.sh`; rules in `docs/operations/14-keycloak-admin-console.md`.
* Containers: database, Keycloak, storage and API sit on a Docker network with `internal: true` (no route to the internet); only the proxy publishes ports.

## Secrets handling

* No secrets are committed. `scripts/check_secrets.sh` and the Gitleaks job in `.github/workflows/ci.yml` scan history and tree.
* Local development: `scripts/dev.sh up` generates random local secrets into the git-ignored file infra/.env and a generated realm; `application.yaml` defaults are prefixed `local-dev-only-` and `ProviderConfiguration`/`EnvironmentGuard` refuse them outside LOCAL/CI.
* Preview: `./deploy.sh init-secrets` creates the server's `.env` (mode 600) with generated values; `compose.yaml` marks required secrets with `${VAR:?set VAR}`; the realm and storage configuration containing secrets are rendered into a generated directory on the server, not into Git.
* Keys with a dedicated purpose: `PHONE_CODE_PEPPER` (phone verification codes are stored as HMAC), `HANDOVER_HASH_KEY` and `HANDOVER_ENCRYPTION_KEY` (handover code hash and AES-GCM ciphertext), `ACTIVITY_HASH_KEY` (daily visitor hash), `PUSH_TOKEN_HASH_KEY` and `PUSH_TOKEN_ENCRYPTION_KEY` (device tokens), `OIDC_WEB_CLIENT_SECRET`, `OIDC_ADMIN_CLIENT_SECRET`, object-storage keys.
* Sensitive values are redacted from audit details and logs (`Redaction`, `RedactedThrowable`, tests `LogRedactionIntegrationTest` and `AuditAndCorrelationReadinessIntegrationTest`).
* CI uses the repository secrets for SSH access to the preview and `GITHUB_TOKEN` for GHCR; deploy steps never print them (`.github/workflows/deploy-preview.yml`).

## Other security controls worth knowing

* **Handover code**: six-digit secret shown only to the order owner, verified with keyed hash, five attempts then lock, per-actor hourly limit (30), expiry two hours after slot end; staff override needs a note, evidence reference, permission and step-up (`OrderLifecycleService`).
* **Phone verification**: HMAC-stored codes, five-minute TTL, attempt and resend limits (`application.yaml`).
* **Uploads**: presigned PUT bound to type and size into a private bucket, scanning gate that fails closed, public variants only for approved media (`ObjectStorage`, `MalwareScanner`).
* **Strict input**: unknown JSON properties rejected, Bean Validation on records.
* **Append-only audit**: triggers plus revoked privileges for the runtime role (see [database architecture](database-architecture.md#runtime-database-role)).

## Known limitations and documentation gaps

* The preview realm file has no `matbakhy-mobile` client although the mobile app expects it; mobile sign-in against the deployed preview is NOT VERIFIED.
* `docs/security/mfa-policy.md` says the live Keycloak was not changed or verified by the agent; the repository cannot show current live state.
* CSP is Report-Only (SEC-004 open); there is no application-level rate limiting beyond the specific limits above (handover, phone code); a generic API rate limiter was not found (NOT VERIFIED exhaustively).
