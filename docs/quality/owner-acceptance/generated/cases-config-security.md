# Owner E2E cases — config-security

Generated from `cases/*.json` by `scripts/owner_e2e_catalog.py`; do not edit. Every case is NOT_RUN or BLOCKED until a run records evidence.

### CFG-001 — Public realm discovery is served; Keycloak admin console and master realm are never published (absorbs CFG-035)

- **Status:** NOT_RUN · **Priority:** P0 · **Readiness:** Ready to run manually now
- **Surface:** identity, deployment · **Actors:** FX-GUEST
- **Traces to:** rules: BR-001, BR-060; operations: deploy.sh smoke, deploy/web-preview/Caddyfile id. site block; partial: TC-001, TC-060, TC-140
- **Prerequisites:**
  - Deployed release recorded: on the server, in the deploy/web-preview directory, run `./deploy.sh status` and note RELEASE_COMMIT, both image digests and the schema version
  - Tester's source IP is inside CUSTOMER_ALLOWED_IPS (and STAFF_ALLOWED_IPS for ops.<PREVIEW_DOMAIN> and /api/v1/ops, /api/v1/cook): `curl -s -o /dev/null -w '%{http_code}' https://app.<PREVIEW_DOMAIN>/` returns 200 (a 403 body 'Not available' means the IP is not allow-listed; the preview is deny-by-default, not public, unless the owner set CUSTOMER_ALLOWED_IPS=0.0.0.0/0)
  - No SSH tunnel is open from the test machine (the check is about the public path)
- **Steps:**
  1. (FX-GUEST) curl -s https://id.<PREVIEW_DOMAIN>/realms/matbakhy/.well-known/openid-configuration — data: Read the JSON fields issuer, authorization_endpoint, token_endpoint, jwks_uri
  2. (FX-GUEST) For each path run `curl -s -o /dev/null -w '%{http_code}\n' https://id.<PREVIEW_DOMAIN><path>` — data: /admin, /admin/, /admin/master/console/, /admin/realms/matbakhy, /realms/master, /realms/master/protocol/openid-connect/auth, /realms/master/.well-known/openid-configuration
  3. (FX-GUEST) curl -s -o /dev/null -w '%{http_code}\n' https://api.<PREVIEW_DOMAIN>/actuator/health ; same for /actuator/prometheus — data: Both paths on the API host
  4. (FX-GUEST) curl -sI http://id.<PREVIEW_DOMAIN>/realms/matbakhy/.well-known/openid-configuration — data: Plain HTTP request: expect a redirect to https (Caddy automatic HTTPS)
- **Expected:**
  - UI: Browser address bar never reaches a Keycloak admin login page on any of the listed paths
  - API: Discovery: HTTP 200, issuer equals exactly https://id.<PREVIEW_DOMAIN>/realms/matbakhy, every endpoint URL starts with https://id.<PREVIEW_DOMAIN>/
  - API: Every /admin* and /realms/master* path returns exactly 404 (Caddyfile: `respond @admin 404`, same assertion as deploy/web-preview/smoke.sh), not 401/302/403
  - API: /actuator/health and /actuator/prometheus on api. return 404
  - API: Plain http request answers 301/308 to https, never serves content
  - DB: none
- **Also covered by automated tests:** deploy/web-preview/smoke.sh (identity section); scripts/test_proxy_access.py
- **Unverified against code (confirm while executing):** Keycloak paths other than /admin and /realms/master (for example /metrics, /health, /resources) are proxied unfiltered by Caddy; their content was not enumerated

### CFG-002 — Customer web sign-in uses authorization code + PKCE S256 and a real logout ends the server session

- **Status:** NOT_RUN · **Priority:** P0 · **Readiness:** Ready to run manually now
- **Surface:** customer-web, identity, api · **Actors:** FX-CUST-A
- **Traces to:** rules: BR-001, BR-002; operations: getMe, POST /api/v1/auth/logout, GET /oauth2/authorization/keycloak; partial: TC-001, TC-002, TC-109
- **Prerequisites:**
  - Deployed release recorded: on the server, in the deploy/web-preview directory, run `./deploy.sh status` and note RELEASE_COMMIT, both image digests and the schema version
  - Tester's source IP is inside CUSTOMER_ALLOWED_IPS (and STAFF_ALLOWED_IPS for ops.<PREVIEW_DOMAIN> and /api/v1/ops, /api/v1/cook): `curl -s -o /dev/null -w '%{http_code}' https://app.<PREVIEW_DOMAIN>/` returns 200 (a 403 body 'Not available' means the IP is not allow-listed; the preview is deny-by-default, not public, unless the owner set CUSTOMER_ALLOWED_IPS=0.0.0.0/0)
  - Private browser window, no prior cookies for app./api./id.
  - Password for preview-customer-1 comes from the private fixture sheet (server .env PREVIEW_USER_PASSWORD); do not type it into any repository file
- **Steps:**
  1. (FX-CUST-A) Open https://app.<PREVIEW_DOMAIN>/ with DevTools Network 'Preserve log' on, click the sign-in link (Arabic UI by default) — data: Observe the navigation to https://api.<PREVIEW_DOMAIN>/oauth2/authorization/keycloak
  2. (FX-CUST-A) In the redirect to the id. host, read the query string of the /realms/matbakhy/protocol/openid-connect/auth request — data: Check client_id, response_type, redirect_uri, scope, state, code_challenge, code_challenge_method
  3. (FX-CUST-A) Sign in as preview-customer-1 with the fixture-sheet password. Do NOT expect or enter an OTP — data: Normal sign-in must not ask for a second factor under the default OPTIONAL policy
  4. (FX-CUST-A) Browser DevTools console on the signed-in app origin, using the session cookie: `fetch('https://api.<PREVIEW_DOMAIN>/api/v1/me',{credentials:'include',method:'GET',headers:{'Content-Type':'application/json','X-XSRF-TOKEN':document.cookie.match(/XSRF-TOKEN=([^;]+)/)[1]}})` (the XSRF-TOKEN cookie is set for the parent domain and readable; the session cookie MATBAKHY_SESSION is HttpOnly) — data: GET /api/v1/me
  5. (FX-CUST-A) In the app click Sign out (calls POST /api/v1/auth/logout with the X-XSRF-TOKEN header)
  6. (FX-CUST-A) Repeat GET /api/v1/me from the same tab, then press Browser Back and open https://app.<PREVIEW_DOMAIN>/#/account (the web app uses hash routes) — data: Also inspect DevTools > Application > Cookies for MATBAKHY_SESSION
- **Expected:**
  - UI: Sign-in page is on id.<PREVIEW_DOMAIN> in the matbakhy realm and Arabic by default (defaultLocale ar)
  - UI: After sign-in the app shows the customer as signed in; no OTP screen appeared
  - UI: After sign-out the app shows the signed-out state; Back/refresh does not restore private data
  - API: Authorize request: client_id=matbakhy-web, response_type=code, redirect_uri=https://api.<PREVIEW_DOMAIN>/login/oauth2/code/keycloak, code_challenge_method=S256 with a code_challenge present, state present, no implicit/hybrid response_type
  - API: GET /api/v1/me while signed in: 200 with the customer's own profile; no password or token in the body
  - API: After logout GET /api/v1/me: 401 and the MATBAKHY_SESSION cookie is deleted/expired (SecurityConfiguration deleteCookies)
  - DB: Read-only: spring_session row for the principal exists while signed in and is gone after logout (isolated stack only; the preview DB is not queried by the tester)
- **Also covered by automated tests:** IdentityIntegrationTest; MfaIdpTest (normal login without OTP); deploy/web-preview/smoke.sh (sign-in redirect)
- **Unverified against code (confirm while executing):** Exact HTTP status of POST /api/v1/auth/logout (204 vs 200) was not confirmed in code; Whether the Keycloak SSO session also ends on app logout (post.logout.redirect.uris is registered; RP-initiated logout wiring not read)

### CFG-003 — Hostile OIDC inputs: unregistered redirect_uri, forged callback state/code, and returnTo open-redirect are all refused

- **Status:** NOT_RUN · **Priority:** P0 · **Readiness:** Ready to run manually now
- **Surface:** identity, api, customer-web · **Actors:** FX-GUEST
- **Traces to:** rules: BR-001, BR-002; operations: GET /oauth2/authorization/{registrationId}, GET /login/oauth2/code/{registrationId}; partial: TC-002, TC-109
- **Prerequisites:**
  - Deployed release recorded: on the server, in the deploy/web-preview directory, run `./deploy.sh status` and note RELEASE_COMMIT, both image digests and the schema version
  - Tester's source IP is inside CUSTOMER_ALLOWED_IPS (and STAFF_ALLOWED_IPS for ops.<PREVIEW_DOMAIN> and /api/v1/ops, /api/v1/cook): `curl -s -o /dev/null -w '%{http_code}' https://app.<PREVIEW_DOMAIN>/` returns 200 (a 403 body 'Not available' means the IP is not allow-listed; the preview is deny-by-default, not public, unless the owner set CUSTOMER_ALLOWED_IPS=0.0.0.0/0)
- **Steps:**
  1. (FX-GUEST) Open https://id.<PREVIEW_DOMAIN>/realms/matbakhy/protocol/openid-connect/auth?client_id=matbakhy-web&response_type=code&scope=openid&redirect_uri=https%3A%2F%2Fevil.example%2Fcb&state=x — data: Unregistered redirect_uri (matbakhy-web only registers https://api.<D>/login/oauth2/code/keycloak)
  2. (FX-GUEST) curl -s -o /dev/null -w '%{http_code} %{redirect_url}\n' 'https://api.<PREVIEW_DOMAIN>/login/oauth2/code/keycloak?code=forged&state=forged' — data: Callback with no preceding authorization request in this session
  3. (FX-GUEST) curl -s -o /dev/null -D - 'https://api.<PREVIEW_DOMAIN>/oauth2/authorization/keycloak?returnTo=https://evil.example/' ; then complete a normal sign-in as FX-CUST-A in a browser using the same URL — data: returnTo origin not in WEB_ALLOWED_ORIGINS (app., ops.)
  4. (FX-GUEST) Start a normal sign-in at https://app.<PREVIEW_DOMAIN>/, sign in, and when Keycloak redirects back to /login/oauth2/code/keycloak?code=..&state=.. stop (use a fresh private window and edit the state value before loading that callback) — data: Tampered state
- **Expected:**
  - UI: Step 1: Keycloak error page ('Invalid parameter: redirect_uri'); the browser is never redirected to evil.example
  - UI: Step 3: after login the user lands on the default app page, not on evil.example
  - API: Step 1: HTTP 400 from id. with no Location header to evil.example
  - API: Step 2: no session is created: redirect to the login/error page or 401; a following GET /api/v1/me without a session is 401
  - API: Step 4: callback rejected (authorization_request_not_found / invalid state); no authenticated session
  - DB: none
- **Also covered by automated tests:** IdentityIntegrationTest; WebReturnTo logic in backend/app/src/main/java/com/matbakhy/app/security/WebReturnTo.java (no dedicated test class located)
- **Unverified against code (confirm while executing):** Exact error page text/status of Keycloak and of the Spring callback failure were not confirmed against a running stack; Registered redirect URIs of the LIVE realm were not inspected (only the repository realm file)

### CFG-004 — Web session expiry and server-side revocation: idle timeout and deleted session row both force re-authentication

- **Status:** NOT_RUN · **Priority:** P1 · **Readiness:** Isolated disposable stack only
- **Surface:** api, identity · **Actors:** FX-CUST-A · **Needs:** isolated stack · **Destructive**
- **Traces to:** rules: BR-001; operations: getMe; partial: TC-001, TC-109
- **Prerequisites:**
  - FX-ISOLATED exists: a disposable compose stack with its own PREVIEW_DOMAIN and fresh volumes built from the PR #9 head images, realm imported, `./deploy.sh up` healthy, `./deploy.sh seed` run, and `./migrate-mfa.sh status` run on it prints flows=complete
  - The API container is started with WEB_SESSION_TIMEOUT=2m (default is 30m, application.yaml server.servlet.session.timeout) so the idle test is minutes, not half an hour
  - Operator has psql access to the isolated `matbakhy` database only (never the preview database)
- **Steps:**
  1. (FX-CUST-A) Sign in on the isolated customer web; run GET /api/v1/me (expect 200)
  2. (FX-CUST-A) Wait 2 minutes 30 seconds without any request, then GET /api/v1/me — data: Idle expiry
  3. (FX-CUST-A) Sign in again; operator runs `docker compose exec -T postgres psql -U matbakhy -d matbakhy -c "DELETE FROM spring_session"` on the isolated stack, then GET /api/v1/me — data: Server-side revocation (table name per Spring Session JDBC default; confirm with \dt spring_session*)
  4. (FX-CUST-A) Click Sign in again on the app — data: A new Keycloak SSO session may sign in silently or ask for a password; record which
- **Expected:**
  - UI: After steps 2 and 3 the app shows the signed-out state and offers sign-in; no private data is shown from cache
  - API: Steps 2 and 3: GET /api/v1/me returns 401
  - API: A replayed old MATBAKHY_SESSION cookie value returns 401
  - DB: Read-only: `SELECT count(*) FROM spring_session` is 0 after step 3 before re-login
- **Also covered by automated tests:** IdentityIntegrationTest
- **Unverified against code (confirm while executing):** Spring Session table names (spring_session / spring_session_attributes) not confirmed in migrations; Access-token lifetime (300 s, realm accessTokenLifespan) governs bearer/mobile calls only; the web session is server-side

### CFG-005 — Role boundary: customer, cook and unprivileged staff sessions cannot reach staff or other-owner APIs by URL, forged header or client-side role

- **Status:** NOT_RUN · **Priority:** P0 · **Readiness:** Needs the updated application deployment
- **Surface:** api, operations-web, customer-web · **Actors:** FX-GUEST, FX-CUST-A, FX-COOK-A, FX-STAFF-NOGRANT · **Needs:** updated deploy
- **Traces to:** rules: BR-001, BR-006, BR-007; operations: getOpsStaff, getOpsSecurityMfaPolicy; partial: TC-001, TC-006, TC-007
- **Prerequisites:**
  - Deployed release recorded: on the server, in the deploy/web-preview directory, run `./deploy.sh status` and note RELEASE_COMMIT, both image digests and the schema version
  - Tester's source IP is inside CUSTOMER_ALLOWED_IPS (and STAFF_ALLOWED_IPS for ops.<PREVIEW_DOMAIN> and /api/v1/ops, /api/v1/cook): `curl -s -o /dev/null -w '%{http_code}' https://app.<PREVIEW_DOMAIN>/` returns 200 (a 403 body 'Not available' means the IP is not allow-listed; the preview is deny-by-default, not public, unless the owner set CUSTOMER_ALLOWED_IPS=0.0.0.0/0)
  - Allow-listed for ops.<PREVIEW_DOMAIN> (STAFF_ALLOWED_IPS), otherwise Caddy answers 403 before the API is reached and the test measures the proxy, not the API
- **Steps:**
  1. (FX-GUEST) curl -s -w ' %{http_code}\n' https://api.<PREVIEW_DOMAIN>/api/v1/ops/staff ; then /api/v1/me ; then /api/v1/cook/dashboard — data: No session
  2. (FX-CUST-A) Browser DevTools console on the signed-in app origin, using the session cookie: `fetch('https://api.<PREVIEW_DOMAIN>/api/v1/ops/staff',{credentials:'include',method:'GET',headers:{'Content-Type':'application/json','X-XSRF-TOKEN':document.cookie.match(/XSRF-TOKEN=([^;]+)/)[1]}})` (the XSRF-TOKEN cookie is set for the parent domain and readable; the session cookie MATBAKHY_SESSION is HttpOnly) — data: As preview-customer-1 on app.<D>; repeat for /api/v1/ops/security/mfa-policy and /api/v1/cook/dashboard
  3. (FX-CUST-A) Repeat step 2 adding headers 'X-Roles: ADMIN' and 'Authorization: Bearer not-a-token' — data: Forged role header and malformed bearer
  4. (FX-STAFF-NOGRANT) Sign in at https://ops.<PREVIEW_DOMAIN>/ as preview-ops-2 and open every visible tab; also GET /api/v1/ops/staff and /api/v1/ops/security/mfa-policy — data: Staff-style account with no grants
  5. (FX-COOK-A) Sign in at https://ops.<PREVIEW_DOMAIN>/ as preview-cook-1; GET /api/v1/ops/staff — data: Cook is not staff
- **Expected:**
  - UI: Customer/NOGRANT: no staff tabs (operations-web only shows tabs for held permissions)
  - UI: No screen shows another owner's kitchen or order
  - API: Guest: 401 on /api/v1/me and cook/ops paths
  - API: Customer, cook and NOGRANT calling /api/v1/ops/*: 403 FORBIDDEN (no data in body); forged X-Roles header changes nothing; malformed bearer with a valid cookie does not elevate
  - API: Cook calling ops paths: 403
  - DB: Read-only: audit_event has DENIED rows (outcome='DENIED') where the endpoint audits denials; absence is not a failure for plain @PreAuthorize denials
- **Also covered by automated tests:** StaffAccessIntegrationTest; IdentityIntegrationTest; PreviewSecurityConfigurationTest
- **Unverified against code (confirm while executing):** Exact status code for an authenticated but ungranted caller (403 vs 404) per endpoint was not enumerated

### CFG-006 — Web account switch on a shared browser leaves no trace of the previous customer

- **Status:** NOT_RUN · **Priority:** P1 · **Readiness:** Ready to run manually now
- **Surface:** customer-web, api · **Actors:** FX-CUST-A, FX-CUST-B
- **Traces to:** rules: BR-001, BR-006; operations: getMe; partial: TC-006
- **Prerequisites:**
  - Deployed release recorded: on the server, in the deploy/web-preview directory, run `./deploy.sh status` and note RELEASE_COMMIT, both image digests and the schema version
  - Tester's source IP is inside CUSTOMER_ALLOWED_IPS (and STAFF_ALLOWED_IPS for ops.<PREVIEW_DOMAIN> and /api/v1/ops, /api/v1/cook): `curl -s -o /dev/null -w '%{http_code}' https://app.<PREVIEW_DOMAIN>/` returns 200 (a 403 body 'Not available' means the IP is not allow-listed; the preview is deny-by-default, not public, unless the owner set CUSTOMER_ALLOWED_IPS=0.0.0.0/0)
  - FX-CUST-A has at least one basket line or order created in a previous journey (use only synthetic data)
- **Steps:**
  1. (FX-CUST-A) Sign in at https://app.<PREVIEW_DOMAIN>/ as preview-customer-1, add a meal to the basket, open Account, note display name and the order list
  2. (FX-CUST-A) Click Sign out
  3. (FX-CUST-B) Sign in as preview-customer-2 in the SAME browser window (not a new profile); open Basket, Orders, Account — data: Do not reload manually before looking
  4. (FX-CUST-B) DevTools > Application: inspect localStorage/sessionStorage keys of app.<D> — data: Record keys that contain identity-scoped data
  5. (FX-CUST-B) Browser DevTools console on the signed-in app origin, using the session cookie: `fetch('https://api.<PREVIEW_DOMAIN>/api/v1/orders',{credentials:'include',method:'GET',headers:{'Content-Type':'application/json','X-XSRF-TOKEN':document.cookie.match(/XSRF-TOKEN=([^;]+)/)[1]}})` (the XSRF-TOKEN cookie is set for the parent domain and readable; the session cookie MATBAKHY_SESSION is HttpOnly) — data: List must contain only B's orders
- **Expected:**
  - UI: B sees an empty basket and only B's profile/orders; no flash of A's name or lines
  - API: B's order list contains no order whose customer is A
  - API: GET /api/v1/me returns B's subject
  - DB: none
- **Also covered by automated tests:** customer-web unit tests (apps/customer-web/src)
- **Unverified against code (confirm while executing):** Whether any basket state is intentionally kept in browser storage across accounts in the web app was not confirmed; if it is, record it as a defect against BR-006

### CFG-101 — MFA decision table (ISOLATED STACK ONLY): effective mode, required and available for every category combination, user preference and missing row (replaces CFG-007, 009, 010, 012-matrix, 013)

- **Status:** NOT_RUN · **Priority:** P0 · **Readiness:** Needs the updated application deployment
- **Surface:** api, customer-web, operations-web · **Actors:** FX-STAFF-ADMIN-B, FX-CUST-A, FX-COOK-A, FX-STAFF-SUPPORT, FX-MULTIROLE · **Needs:** updated deploy, isolated stack · **Destructive**
- **Fixtures to provision:** FX-STAFF-ADMIN-B, FX-STAFF-SUPPORT, FX-MULTIROLE
- **Traces to:** rules: BR-001, BR-007, BR-042; decisions: D18; operations: getMeSecurity, putOpsSecurityMfaPolicyByCategory, getOpsSecurityMfaPolicy, patchMe; partial: TC-007
- **Prerequisites:**
  - ISOLATED STACK ONLY (needs_isolated_stack): every row changes policy for all users of a category and R13 deletes a policy row; never run on the shared preview
  - Deployed release recorded: on the server, in the deploy/web-preview directory, run `./deploy.sh status` and note RELEASE_COMMIT, both image digests and the schema version
  - Deployed API contains PR #9 (D18): `./deploy.sh status` shows schema version >= 31 and RELEASE_COMMIT is the PR #9 head or later; origin/develop does NOT contain V31/MfaController, so on the current deployment GET /api/v1/me/security is not a 200
  - FX-ISOLATED exists: a disposable compose stack with its own PREVIEW_DOMAIN and fresh volumes built from the PR #9 head images, realm imported, `./deploy.sh up` healthy, `./deploy.sh seed` run, and `./migrate-mfa.sh status` run on it prints flows=complete
  - FX-STAFF-ADMIN-B holds active grants security.mfa_policy and security.mfa_reset and has an enrolled authenticator (TOTP secret kept in the private fixture sheet). Provision per docs/quality/owner-acceptance/fixtures.md (the bootstrap administrator preview-ops-1 holds only staff.grant and audit.read, so these permissions are absent by default)
  - Four account shapes exist on the isolated stack: customer-only (FX-CUST-A: category CUSTOMER), cook (FX-COOK-A owns an approved kitchen: CUSTOMER+KITCHEN), staff (FX-STAFF-SUPPORT holds any active grant, no kitchen: CUSTOMER+STAFF), multi-role (FX-MULTIROLE: kitchen + active grant: all three). Every account is always in CUSTOMER (MfaPolicyService.decide)
  - Reset all policies first: three PUTs setting CUSTOMER, KITCHEN, STAFF to OPTIONAL and every fixture preference off (PUT /api/v1/me/security/mfa {"enabled":false})
- **Steps:**
  1. (FX-STAFF-ADMIN-B) For each row of the table in `data` run the policy PUTs listed in the row, in order (GET /api/v1/ops/security/mfa-policy first for the versions). Each PUT needs an MFA-asserted session (step-up at https://api.<PREVIEW_DOMAIN>/oauth2/authorization/keycloak?stepUp=true&returnTo=https://app.<PREVIEW_DOMAIN>/, ops host variant https://api.<PREVIEW_DOMAIN>/oauth2/authorization/keycloak?stepUp=true&returnTo=https://ops.<PREVIEW_DOMAIN>/) — data: Row notation: C/K/S = CUSTOMER/KITCHEN/STAFF mode (D=DISABLED, O=OPTIONAL, R=REQUIRED); pref = the account's own preference; effective = most protective applicable mode (R>O>D)
  2. (FX-CUST-A) For the account shape in the row, set its preference as the row says (turning ON needs a step-up session; row with pref=on under D cannot be set via UI, so set the preference BEFORE switching the category to D), then GET /api/v1/me/security as that account — data: Compare JSON fields effectiveMode, enabled, required, available, categories, policy
  3. (FX-CUST-A) Rows marked 'enforce' additionally: with a level-1 (password-only) session call PATCH /api/v1/me body {"displayName":"E2E","expectedVersion":<current>}, then GET /api/v1/me — data: Profile write is protected, profile read is open
  4. (FX-STAFF-ADMIN-B) Missing-row row only (run LAST, isolated DB): FIRST restore CUSTOMER, KITCHEN and STAFF to OPTIONAL (three PUTs) and set every fixture preference off (PUT /api/v1/me/security/mfa {"enabled":false} per account), because R12 left CUSTOMER=DISABLED. THEN `DELETE FROM mfa_policy WHERE category='KITCHEN'` via psql, repeat step 2 for FX-COOK-A and FX-CUST-A; afterwards restore with `INSERT INTO mfa_policy(category,mode) VALUES('KITCHEN','OPTIONAL')` — data: Fail-safe rule: a missing row counts as REQUIRED for categories the account belongs to
- **Expected:**
  - UI: Customer web Account > Security and ops web Security tab labels follow `mode` : DISABLED='Not available', OPTIONAL='Optional', REQUIRED='Required'; when required=true and no MFA in session the text 'Two-step verification has not been completed in this session.' and the 'Complete two-step verification' link show; when available=false the text 'Two-step verification is not available for your account right now.' shows and there is no 'Turn on' button; when effective=REQUIRED the 'Turn off' button is absent/refused and 'required for your account and cannot be turned off' shows
  - API: TABLE (account | C K S | pref | => effectiveMode, enabled, required, available):
  - API: R1 customer | O O O | off | OPTIONAL, false, false, true
  - API: R2 customer | O O O | on | OPTIONAL, true, true, true  [enforce: PATCH /api/v1/me with level-1 session => 403 STEP_UP_REQUIRED; GET /api/v1/me => 200]
  - API: R3 customer | D O O | off | DISABLED, false, false, false
  - API: R4 customer | D O O | on (set before switching) | DISABLED, true (preference kept), false, false  [enforce: PATCH succeeds, no step-up asked]
  - API: R5 customer | R O O | off | REQUIRED, false, true, true  [enforce: 403 STEP_UP_REQUIRED]
  - API: R6 customer | R D D | on | REQUIRED, true, true, true
  - API: R7 cook | D O O | on | OPTIONAL (KITCHEN wins over CUSTOMER), true, true, true
  - API: R8 cook | R D D | off | REQUIRED, false, true, true
  - API: R9 staff | O D D | off | OPTIONAL (every staff account is also CUSTOMER, so STAFF=D alone cannot disable), false, false, true
  - API: R10 staff | D D R | off | REQUIRED, false, true, true
  - API: R11 multi-role | D D D | on | DISABLED, true, false, false
  - API: R12 multi-role | D D R | off | REQUIRED, false, true, true
  - API: R13 (after resetting all three categories to OPTIONAL and all preferences off) missing KITCHEN row: cook => REQUIRED, required=true, available=true; customer-only => OPTIONAL, required=false (KITCHEN does not apply to it)
  - API: In every row `categories` lists exactly the account's applicable categories (customer-only: [CUSTOMER]; cook: [CUSTOMER, KITCHEN]; staff: [CUSTOMER, STAFF]; multi-role: all three) and `policy` maps only those
  - API: Recomputed on every request: after each PUT the next GET shows the new values without re-login
  - DB: Read-only: `SELECT category, mode, version FROM mfa_policy ORDER BY category` matches the row after each PUT; version increases by 1 per successful PUT
  - DB: Read-only: `SELECT action, details FROM audit_event WHERE action='mfa.policy.update' ORDER BY occurred_at DESC` has one row per PUT with details {category, from, to}
- **Also covered by automated tests:** MfaPolicyResolveTest (precedence matrix, pure); MfaPolicyIntegrationTest.category_policies_follow_the_account_role_and_the_most_protective_one_applies; MfaPolicyIntegrationTest.a_disabled_policy_offers_no_enrolment_and_does_not_silently_remove_an_existing_preference; MfaPolicyIntegrationTest.the_users_preference_can_never_override_a_required_policy
- **Unverified against code (confirm while executing):** Whether PATCH /api/v1/me is guarded by the filter exactly as described is inferred from MfaEnforcementFilter (only GET /api/v1/me is open); the response body was not seen; The UI sets the preference via PUT /api/v1/me/security/mfa; R4's 'on while DISABLED' state can only be produced by ordering the steps as written

### CFG-102 — Web step-up and first-time enrolment through Keycloak: level-1 sign-in never asks OTP; stepUp=true asks OTP or leads to TOTP enrolment

- **Status:** NOT_RUN · **Priority:** P0 · **Readiness:** Needs the updated application deployment
- **Surface:** customer-web, identity, api · **Actors:** FX-CUST-A, FX-CUST-NEW · **Needs:** updated deploy
- **Traces to:** rules: BR-001, BR-002, BR-007; decisions: D18; operations: getMeSecurity, GET /oauth2/authorization/keycloak?stepUp=true; partial: TC-109
- **Prerequisites:**
  - Deployed release recorded: on the server, in the deploy/web-preview directory, run `./deploy.sh status` and note RELEASE_COMMIT, both image digests and the schema version
  - Deployed API contains PR #9 (D18): `./deploy.sh status` shows schema version >= 31 and RELEASE_COMMIT is the PR #9 head or later; origin/develop does NOT contain V31/MfaController, so on the current deployment GET /api/v1/me/security is not a 200
  - Live realm migrated (owner-gated): on the server `./migrate-mfa.sh status` prints flows=complete, browserFlow=matbakhy browser and client.matbakhy-admin=present (NOT true on the live realm today: migration has never been run there)
  - Tester's source IP is inside CUSTOMER_ALLOWED_IPS (and STAFF_ALLOWED_IPS for ops.<PREVIEW_DOMAIN> and /api/v1/ops, /api/v1/cook): `curl -s -o /dev/null -w '%{http_code}' https://app.<PREVIEW_DOMAIN>/` returns 200 (a 403 body 'Not available' means the IP is not allow-listed; the preview is deny-by-default, not public, unless the owner set CUSTOMER_ALLOWED_IPS=0.0.0.0/0)
  - Authenticator app available to the tester for a new TOTP enrolment (secret/QR is shown by Keycloak and stored only in the private fixture sheet, never in the repository)
- **Steps:**
  1. (FX-CUST-A) Sign in at https://app.<PREVIEW_DOMAIN>/ (password only). Then GET /api/v1/me/security in the console — data: Baseline: verifiedInSession false
  2. (FX-CUST-A) Open https://api.<PREVIEW_DOMAIN>/oauth2/authorization/keycloak?stepUp=true&returnTo=https://app.<PREVIEW_DOMAIN>/ and watch the redirect to id.<D> in DevTools Network — data: Read acr_values, max_age and prompt on the authorize request
  3. (FX-CUST-A) Account has no authenticator yet: Keycloak shows the 'configure authenticator' page. Try to leave via Back to the app without enrolling, then GET /api/v1/me/security — data: Enrolment is mandatory in the level-2 flow (CONFIGURE_TOTP)
  4. (FX-CUST-A) Enrol the authenticator, enter the current 6-digit code, return to the app, GET /api/v1/me/security
  5. (FX-CUST-A) Open https://api.<PREVIEW_DOMAIN>/oauth2/authorization/keycloak?stepUp=true&returnTo=https://app.<PREVIEW_DOMAIN>/ again (user already enrolled) — data: Keycloak asks only for the OTP, not for the password again when the SSO session exists
  6. (FX-CUST-A) Enter an incorrect 6-digit code (000000 unless it happens to be valid), then the correct code — data: Wrong code first
- **Expected:**
  - UI: Step 2: Keycloak prompts for authentication again (max_age=0)
  - UI: Step 3: sign-in cannot complete without a factor
  - UI: Step 6: Keycloak form shows an invalid-code error and stays on the OTP page; the correct code returns to https://app.<D>/
  - UI: Account > Security shows 'Verified in this session.'
  - API: Authorize request carries acr_values=mfa 2 (or equivalent encoding), max_age=0 (documented in mfa-policy.md); a plain sign-in carries neither
  - API: Before step-up GET /api/v1/me/security: verifiedInSession=false; after: true
  - API: The session claims after enrolment contain acr in {mfa,2} (amr otp may be absent after enrolment: documented residual risk; the API accepts either)
  - DB: Read-only: no credential data lives in the application database; enrolment is visible only in Keycloak (do not query it on the preview)
- **Also covered by automated tests:** MfaIdpTest (real Keycloak: normal login, step-up, wrong code, enrolment, SSO session); deploy/web-preview/identity/verify-mfa-flow.py
- **Unverified against code (confirm while executing):** Whether the OTP-only second prompt (no password) appears on the live realm depends on the SSO session lifetime; Exact Keycloak page strings (Arabic default locale) not captured

### CFG-008 — Own two-step preference under OPTIONAL: enable only on a verified session; disable needs a recent factor, ends sessions, and is audited (absorbs CFG-017)

- **Status:** NOT_RUN · **Priority:** P0 · **Readiness:** Needs the updated application deployment
- **Surface:** customer-web, api, identity · **Actors:** FX-CUST-A · **Needs:** updated deploy
- **Traces to:** rules: BR-001, BR-002; decisions: D18; operations: putMeSecurityMfa, getMeSecurity; partial: TC-002
- **Prerequisites:**
  - Deployed release recorded: on the server, in the deploy/web-preview directory, run `./deploy.sh status` and note RELEASE_COMMIT, both image digests and the schema version
  - Deployed API contains PR #9 (D18): `./deploy.sh status` shows schema version >= 31 and RELEASE_COMMIT is the PR #9 head or later; origin/develop does NOT contain V31/MfaController, so on the current deployment GET /api/v1/me/security is not a 200
  - Live realm migrated (owner-gated): on the server `./migrate-mfa.sh status` prints flows=complete, browserFlow=matbakhy browser and client.matbakhy-admin=present (NOT true on the live realm today: migration has never been run there)
  - Tester's source IP is inside CUSTOMER_ALLOWED_IPS (and STAFF_ALLOWED_IPS for ops.<PREVIEW_DOMAIN> and /api/v1/ops, /api/v1/cook): `curl -s -o /dev/null -w '%{http_code}' https://app.<PREVIEW_DOMAIN>/` returns 200 (a 403 body 'Not available' means the IP is not allow-listed; the preview is deny-by-default, not public, unless the owner set CUSTOMER_ALLOWED_IPS=0.0.0.0/0)
  - CUSTOMER policy is OPTIONAL (GET /api/v1/ops/security/mfa-policy as an admin shows mode OPTIONAL for CUSTOMER)
  - FX-CUST-A has completed CFG-102 (authenticator enrolled), preference currently off
  - OIDC_ADMIN_CLIENT_SECRET is set in the stack's .env (`grep -c '^OIDC_ADMIN_CLIENT_SECRET=.' .env` prints 1; never print the value)
- **Steps:**
  1. (FX-CUST-A) Sign in password-only at app.<D> > Account > Security > click 'Turn on two-step verification' — data: Expect refusal because the session has no MFA
  2. (FX-CUST-A) Open the 'Verify now' link (https://api.<PREVIEW_DOMAIN>/oauth2/authorization/keycloak?stepUp=true&returnTo=https://app.<PREVIEW_DOMAIN>/), enter the OTP, return, then click 'Turn on two-step verification'
  3. (FX-CUST-A) Sign out and sign in password-only, then call PATCH /api/v1/me body {"displayName":"E2E","expectedVersion":<current>} — data: Preference is on => protected writes need MFA
  4. (FX-CUST-A) In a second private window sign in as FX-CUST-A, do step-up with OTP, keep this window open (window W2). In window W1 (MFA-verified) click 'Turn off two-step verification' — data: Disable with a recent factor
  5. (FX-CUST-A) In W2 reload any page and GET /api/v1/me; also GET /api/v1/me/security in W1 — data: Disable ends the user's application web sessions
  6. (FX-CUST-A) Sign in password-only again; turn the preference on once more and then wait 11 minutes (OIDC_STEP_UP_MAX_AGE default 10m) in the MFA session; click 'Turn off two-step verification' — data: Disable with a stale factor
- **Expected:**
  - UI: Step 1: banner 'This action needs additional verification.' with link 'Verify now'
  - UI: Step 6: same banner/link; preference stays On
  - UI: After step 4 'Security settings updated.' and status Off
  - API: Step 1: PUT /api/v1/me/security/mfa {"enabled":true} => 403 STEP_UP_REQUIRED
  - API: Step 2: 200 with enabled=true, required=true, verifiedInSession=true
  - API: Step 3: 403 STEP_UP_REQUIRED
  - API: Step 4: 200 enabled=false, required=false
  - API: Step 5: W2 GET /api/v1/me => 401 (application sessions of the account end); W1 session also ended, so a new sign-in is needed; provider sessions end too only when OIDC_ADMIN_CLIENT_SECRET is configured (best effort)
  - API: Step 6: 403 STEP_UP_REQUIRED (auth_time older than step-up-max-age)
  - DB: Read-only: `SELECT enabled FROM user_mfa_preference WHERE user_id=<A>` goes true then false
  - DB: Read-only: `SELECT action, outcome FROM audit_event WHERE actor_id=<A> AND action LIKE 'mfa.preference.%' ORDER BY occurred_at` shows mfa.preference.enable then mfa.preference.disable (ALLOWED), details {effectiveMode: OPTIONAL}; denied attempts are not audited
- **Also covered by automated tests:** MfaPolicyIntegrationTest.under_optional_a_user_can_enable_mfa_only_after_proving_it_and_can_disable_only_after_a_recent_second_factor; MfaPolicyIntegrationTest.turning_mfa_off_ends_the_application_web_sessions_of_the_account
- **Unverified against code (confirm while executing):** Whether W1 itself is also ended by the disable (sessions.endAllFor(subject) ends every application session of the subject, including the caller's) is inferred from code, expectation recorded as such; Preview-safe: only FX-CUST-A's own preference changes, but it needs a real TOTP; acceptable on the shared preview once the migration exists

### CFG-011 — Category set to REQUIRED: enforcement reaches the next request, the user cannot turn it off, open endpoints stay reachable, policy change is audited

- **Status:** NOT_RUN · **Priority:** P0 · **Readiness:** Needs the updated application deployment
- **Surface:** api, customer-web, operations-web · **Actors:** FX-STAFF-ADMIN-B, FX-CUST-A, FX-COOK-A · **Needs:** updated deploy, isolated stack · **Destructive**
- **Fixtures to provision:** FX-STAFF-ADMIN-B
- **Traces to:** rules: BR-001, BR-007, BR-042; decisions: D18; operations: putOpsSecurityMfaPolicyByCategory, putMeSecurityMfa, patchMe; partial: TC-007
- **Prerequisites:**
  - Deployed release recorded: on the server, in the deploy/web-preview directory, run `./deploy.sh status` and note RELEASE_COMMIT, both image digests and the schema version
  - Deployed API contains PR #9 (D18): `./deploy.sh status` shows schema version >= 31 and RELEASE_COMMIT is the PR #9 head or later; origin/develop does NOT contain V31/MfaController, so on the current deployment GET /api/v1/me/security is not a 200
  - FX-ISOLATED exists: a disposable compose stack with its own PREVIEW_DOMAIN and fresh volumes built from the PR #9 head images, realm imported, `./deploy.sh up` healthy, `./deploy.sh seed` run, and `./migrate-mfa.sh status` run on it prints flows=complete
  - FX-STAFF-ADMIN-B holds active grants security.mfa_policy and security.mfa_reset and has an enrolled authenticator (TOTP secret kept in the private fixture sheet). Provision per docs/quality/owner-acceptance/fixtures.md (the bootstrap administrator preview-ops-1 holds only staff.grant and audit.read, so these permissions are absent by default)
  - Policies are all OPTIONAL and all preferences off before starting; FX-CUST-A and FX-COOK-A are enrolled (CFG-102) so they can pass the step-up
  - Do not run on the shared preview: mfa-policy.md step 5 says REQUIRED starts with STAFF and CUSTOMER/KITCHEN must not be REQUIRED until the mobile app is verified (this case sets CUSTOMER and KITCHEN to REQUIRED on the isolated stack only)
- **Steps:**
  1. (FX-CUST-A) Sign in password-only (level 1) and keep the tab open
  2. (FX-STAFF-ADMIN-B) After step-up on ops.<D> open Security tab > 'Two-step verification policy per category'; set Customers to Required with reason 'E2E required', click Apply (UI warns 'REQUIRED makes every user in the category verify with a second factor on their next request.') — data: Or PUT /api/v1/ops/security/mfa-policy/CUSTOMER {mode:REQUIRED, expectedVersion:<n>, reason:'E2E required'}
  3. (FX-CUST-A) Without re-login, call each: GET /api/v1/me ; GET /api/v1/me/security ; GET /api/v1/meals ; PATCH /api/v1/me ; POST /api/v1/orders (any valid-looking body) ; POST /api/v1/auth/logout — data: Protected, open and public paths
  4. (FX-CUST-A) PUT /api/v1/me/security/mfa {"enabled":false} on a step-up session
  5. (FX-COOK-A) Repeat step 3 for GET /api/v1/cook/dashboard with a level-1 session while only CUSTOMER is REQUIRED — data: Cook is also CUSTOMER so it is affected
  6. (FX-STAFF-ADMIN-B) Set Customers back to Optional (PUT mode OPTIONAL with the current expectedVersion) — data: Rollback
- **Expected:**
  - UI: Customer web shows 'This action needs additional verification.' / 'Verify now' for refused actions, and 'Two-step verification is required for your account and cannot be turned off.' on Security
  - UI: After step 2 the cook workspace shows 'Your account requires two-step verification before these screens can be used.'
  - API: Step 3 with level-1 session: GET /api/v1/me 200; GET /api/v1/me/security 200; GET /api/v1/meals 200 (public); PATCH /api/v1/me 403 STEP_UP_REQUIRED; POST /api/v1/orders 403 STEP_UP_REQUIRED; POST /api/v1/auth/logout allowed
  - API: After a step-up the same PATCH/POST succeed (no new policy needed)
  - API: Step 4: 409 STATE_CONFLICT with field enabled=MFA_REQUIRED_BY_POLICY
  - API: Step 5: GET /api/v1/cook/dashboard 403 STEP_UP_REQUIRED
  - DB: Read-only: mfa_policy CUSTOMER row version +2 after step 2 and step 6; two mfa.policy.update audit rows with from/to OPTIONAL->REQUIRED->OPTIONAL and the reason text
- **Also covered by automated tests:** MfaPolicyIntegrationTest.a_required_policy_blocks_a_session_without_the_second_factor_everywhere_but_the_settings_endpoints; MfaPolicyIntegrationTest.under_required_the_profile_can_be_read_but_not_changed_without_the_second_factor
- **Unverified against code (confirm while executing):** POST /api/v1/orders body validity was not hand-built; use the UI checkout to trigger it if a hand-built request returns 400 before the filter (the filter runs first, so expect 403 first)

### CFG-012 — Category DISABLED: enrolment is refused, existing preference and authenticator are not silently removed, staff high-risk step-up still applies

- **Status:** NOT_RUN · **Priority:** P1 · **Readiness:** Needs the updated application deployment
- **Surface:** api, customer-web, operations-web, identity · **Actors:** FX-STAFF-ADMIN-B, FX-CUST-A, FX-CUST-NEW · **Needs:** updated deploy, isolated stack · **Destructive**
- **Fixtures to provision:** FX-STAFF-ADMIN-B, FX-STAFF-SUPPORT, FX-CUST-NEW
- **Traces to:** rules: BR-001, BR-007; decisions: D18; operations: putMeSecurityMfa, putOpsSecurityMfaPolicyByCategory; partial: TC-007
- **Prerequisites:**
  - Deployed release recorded: on the server, in the deploy/web-preview directory, run `./deploy.sh status` and note RELEASE_COMMIT, both image digests and the schema version
  - Deployed API contains PR #9 (D18): `./deploy.sh status` shows schema version >= 31 and RELEASE_COMMIT is the PR #9 head or later; origin/develop does NOT contain V31/MfaController, so on the current deployment GET /api/v1/me/security is not a 200
  - FX-ISOLATED exists: a disposable compose stack with its own PREVIEW_DOMAIN and fresh volumes built from the PR #9 head images, realm imported, `./deploy.sh up` healthy, `./deploy.sh seed` run, and `./migrate-mfa.sh status` run on it prints flows=complete
  - FX-STAFF-ADMIN-B holds active grants security.mfa_policy and security.mfa_reset and has an enrolled authenticator (TOTP secret kept in the private fixture sheet). Provision per docs/quality/owner-acceptance/fixtures.md (the bootstrap administrator preview-ops-1 holds only staff.grant and audit.read, so these permissions are absent by default)
  - FX-CUST-A is enrolled with preference ON (CFG-008 step 2) before the category is changed
  - FX-STAFF-SUPPORT holds support.case
- **Steps:**
  1. (FX-STAFF-ADMIN-B) Set CUSTOMER, KITCHEN and STAFF all to DISABLED (three PUTs, reason 'E2E disabled'; step-up session) — data: FX-CUST-A (enrolled, preference ON) and FX-CUST-NEW (never enabled) exist
  2. (FX-CUST-A) GET /api/v1/me/security — data: Preference must be retained
  3. (FX-CUST-NEW) PUT /api/v1/me/security/mfa {"enabled":true} — data: Enabling while DISABLED
  4. (FX-CUST-A) With a password-only session call PATCH /api/v1/me; then click 'Manage authenticator app (add or remove)' in Security — data: Keycloak account console is outside the API's control
  5. (FX-STAFF-ADMIN-B) With a password-only session (no fresh step-up) PUT /api/v1/ops/security/mfa-policy/STAFF {mode:OPTIONAL, expectedVersion:<n>, reason:'E2E restore'} — data: STAFF=DISABLED does not remove the staff high-risk control
  6. (FX-STAFF-ADMIN-B) Step up (OTP) and repeat the PUT to restore OPTIONAL; restore CUSTOMER and KITCHEN to OPTIONAL — data: Rollback
- **Expected:**
  - UI: Security shows 'Two-step verification is not available for your account right now.' and no 'Turn on' button
  - UI: Keycloak account console still allows adding an authenticator (documented residual risk, not a defect)
  - API: FX-CUST-A GET /api/v1/me/security: effectiveMode DISABLED, enabled true (preference kept), required false, available false
  - API: PUT enabled=true: 409 STATE_CONFLICT with field enabled=MFA_UNAVAILABLE
  - API: PATCH /api/v1/me with a password-only session: 200 (no step-up asked)
  - API: Staff policy PUT without recent MFA: 403 STEP_UP_REQUIRED even though STAFF=DISABLED
  - DB: Read-only: `SELECT enabled FROM user_mfa_preference WHERE user_id=<A>` is still true after the category change
- **Also covered by automated tests:** MfaPolicyIntegrationTest.a_disabled_policy_offers_no_enrolment_and_does_not_silently_remove_an_existing_preference; MfaPolicyIntegrationTest.disabled_never_removes_the_staff_high_risk_step_up_and_keeps_the_stepup_available_to_staff
- **Unverified against code (confirm while executing):** Step 2 wording is awkward: simplest executable form is to use FX-CUST-NEW (never enabled) for the PUT enabled=true and FX-CUST-A to observe the retained preference

### CFG-014 — Missing mfa_policy row fails safe to REQUIRED (isolated database only)

- **Status:** NOT_RUN · **Priority:** P1 · **Readiness:** Needs the updated application deployment
- **Surface:** api · **Actors:** FX-CUST-A, FX-COOK-A · **Needs:** updated deploy, isolated stack · **Destructive**
- **Traces to:** rules: BR-001; decisions: D18; operations: getMeSecurity; partial: TC-060
- **Prerequisites:**
  - Deployed release recorded: on the server, in the deploy/web-preview directory, run `./deploy.sh status` and note RELEASE_COMMIT, both image digests and the schema version
  - Deployed API contains PR #9 (D18): `./deploy.sh status` shows schema version >= 31 and RELEASE_COMMIT is the PR #9 head or later; origin/develop does NOT contain V31/MfaController, so on the current deployment GET /api/v1/me/security is not a 200
  - FX-ISOLATED exists: a disposable compose stack with its own PREVIEW_DOMAIN and fresh volumes built from the PR #9 head images, realm imported, `./deploy.sh up` healthy, `./deploy.sh seed` run, and `./migrate-mfa.sh status` run on it prints flows=complete
  - Operator has psql on the ISOLATED database only; this case would lock out accounts on a shared environment
- **Steps:**
  1. (FX-CUST-A) As operator: `docker compose exec -T postgres psql -U matbakhy -d matbakhy -c "DELETE FROM mfa_policy WHERE category='KITCHEN'"`
  2. (FX-COOK-A) Password-only session: GET /api/v1/me/security then PATCH /api/v1/me — data: Cook is CUSTOMER+KITCHEN
  3. (FX-CUST-A) Password-only session of the customer-only account: GET /api/v1/me/security — data: KITCHEN does not apply
  4. (FX-CUST-A) Restore: `INSERT INTO mfa_policy(category,mode) VALUES ('KITCHEN','OPTIONAL')` (version column defaults to 0)
  5. (FX-COOK-A) Check API logs of the API container for the period for tokens, OTP or secrets: `docker compose logs api --since 10m | grep -i -E 'secret|otp|token' | wc -l` — data: Expect only non-sensitive words
- **Expected:**
  - UI: Cook sees the 'requires two-step verification' screen until the row is restored
  - API: Cook: effectiveMode REQUIRED, required true; PATCH => 403 STEP_UP_REQUIRED
  - API: Customer-only: unaffected (OPTIONAL)
  - API: No stack trace or secret in logs
  - DB: Read-only: `SELECT * FROM mfa_policy` has 2 rows during the test and 3 after restore
- **Also covered by automated tests:** MfaPolicyResolveTest (missing row => REQUIRED)
- **Unverified against code (confirm while executing):** There is no API to delete a policy row; the situation only arises from data damage

### CFG-015 — Policy update uses optimistic versioning and a mandatory reason

- **Status:** NOT_RUN · **Priority:** P1 · **Readiness:** Needs the updated application deployment
- **Surface:** api, operations-web · **Actors:** FX-STAFF-ADMIN-B · **Needs:** updated deploy, isolated stack · **Destructive**
- **Fixtures to provision:** FX-STAFF-ADMIN-B
- **Traces to:** rules: BR-042; decisions: D18; operations: putOpsSecurityMfaPolicyByCategory; partial: TC-042
- **Prerequisites:**
  - Deployed release recorded: on the server, in the deploy/web-preview directory, run `./deploy.sh status` and note RELEASE_COMMIT, both image digests and the schema version
  - Deployed API contains PR #9 (D18): `./deploy.sh status` shows schema version >= 31 and RELEASE_COMMIT is the PR #9 head or later; origin/develop does NOT contain V31/MfaController, so on the current deployment GET /api/v1/me/security is not a 200
  - FX-ISOLATED exists: a disposable compose stack with its own PREVIEW_DOMAIN and fresh volumes built from the PR #9 head images, realm imported, `./deploy.sh up` healthy, `./deploy.sh seed` run, and `./migrate-mfa.sh status` run on it prints flows=complete
  - FX-STAFF-ADMIN-B holds active grants security.mfa_policy and security.mfa_reset and has an enrolled authenticator (TOTP secret kept in the private fixture sheet). Provision per docs/quality/owner-acceptance/fixtures.md (the bootstrap administrator preview-ops-1 holds only staff.grant and audit.read, so these permissions are absent by default)
- **Steps:**
  1. (FX-STAFF-ADMIN-B) After step-up: GET /api/v1/ops/security/mfa-policy, note STAFF.version = v
  2. (FX-STAFF-ADMIN-B) PUT /api/v1/ops/security/mfa-policy/STAFF {"mode":"OPTIONAL","expectedVersion":v,"reason":"E2E first"}
  3. (FX-STAFF-ADMIN-B) Repeat the exact same PUT (still expectedVersion v) — data: Stale version
  4. (FX-STAFF-ADMIN-B) PUT with expectedVersion v+1 and reason "ab" (2 characters) — data: Reason shorter than 3
  5. (FX-STAFF-ADMIN-B) PUT with expectedVersion v+1 and no reason field — data: Missing reason
- **Expected:**
  - UI: Ops web shows 'Policy saved.' once and a refreshed-data message for the stale attempt
  - API: Step 2: 200 with version v+1
  - API: Step 3: 409 VERSION_CONFLICT; the stored mode and version are unchanged
  - API: Step 4: 400 VALIDATION_FAILED (body constraint @Size min 3 or service check)
  - API: Step 5: 400 VALIDATION_FAILED field reason
  - DB: Read-only: exactly one new audit_event mfa.policy.update row for steps 2 (none for 3-5); mfa_policy.version = v+1
- **Also covered by automated tests:** MfaPolicyIntegrationTest.only_the_policy_permission_with_a_recent_second_factor_can_change_a_policy
- **Unverified against code (confirm while executing):** Original CFG-015 assumed two admins (Admin A/B) racing; one admin issuing the same stale version proves the same rule deterministically. Real concurrency is not needed

### CFG-016 — Policy mutation authorization ladder (refusals only): guest, customer, ungranted staff, bootstrap admin, and granted staff without recent MFA; no state change

- **Status:** NOT_RUN · **Priority:** P0 · **Readiness:** Needs the updated application deployment
- **Surface:** api, operations-web · **Actors:** FX-GUEST, FX-CUST-A, FX-STAFF-NOGRANT, FX-STAFF-ADMIN, FX-STAFF-ADMIN-B · **Needs:** updated deploy
- **Fixtures to provision:** FX-STAFF-ADMIN-B
- **Traces to:** rules: BR-001, BR-007, BR-043; decisions: D18; operations: getOpsSecurityMfaPolicy, putOpsSecurityMfaPolicyByCategory; partial: TC-007, TC-043
- **Prerequisites:**
  - Deployed release recorded: on the server, in the deploy/web-preview directory, run `./deploy.sh status` and note RELEASE_COMMIT, both image digests and the schema version
  - Deployed API contains PR #9 (D18): `./deploy.sh status` shows schema version >= 31 and RELEASE_COMMIT is the PR #9 head or later; origin/develop does NOT contain V31/MfaController, so on the current deployment GET /api/v1/me/security is not a 200
  - Tester's source IP is inside CUSTOMER_ALLOWED_IPS (and STAFF_ALLOWED_IPS for ops.<PREVIEW_DOMAIN> and /api/v1/ops, /api/v1/cook): `curl -s -o /dev/null -w '%{http_code}' https://app.<PREVIEW_DOMAIN>/` returns 200 (a 403 body 'Not available' means the IP is not allow-listed; the preview is deny-by-default, not public, unless the owner set CUSTOMER_ALLOWED_IPS=0.0.0.0/0)
  - FX-STAFF-ADMIN-B holds active grants security.mfa_policy and security.mfa_reset and has an enrolled authenticator (TOTP secret kept in the private fixture sheet). Provision per docs/quality/owner-acceptance/fixtures.md (the bootstrap administrator preview-ops-1 holds only staff.grant and audit.read, so these permissions are absent by default)
  - Every PUT in this case is expected to be REFUSED; if any returns 2xx the case FAILS and the policy must be restored from the version/mode read in step 1 (GET /api/v1/ops/security/mfa-policy). Safe on the shared preview only because nothing is expected to change
- **Steps:**
  1. (FX-GUEST) curl -s -w ' %{http_code}\n' -X PUT -H 'Content-Type: application/json' -d '{"mode":"OPTIONAL","expectedVersion":0,"reason":"E2E guest"}' https://api.<PREVIEW_DOMAIN>/api/v1/ops/security/mfa-policy/STAFF — data: Body mode value equals the current STAFF mode read beforehand; no CSRF, no credentials
  2. (FX-CUST-A) Same PUT from a customer session (use the console with X-XSRF-TOKEN)
  3. (FX-STAFF-NOGRANT) Same PUT from preview-ops-2 (no grants); also GET /api/v1/ops/security/mfa-policy
  4. (FX-STAFF-ADMIN) Same PUT and GET as preview-ops-1 (holds staff.grant, audit.read only) — data: The bootstrap admin does NOT hold security.mfa_policy
  5. (FX-STAFF-ADMIN-B) With a PASSWORD-ONLY session: GET /api/v1/ops/security/mfa-policy (record all modes and versions), then PUT /api/v1/ops/security/mfa-policy/STAFF with the CURRENT mode and current expectedVersion — data: Read needs the permission only; write needs recent MFA, so this PUT must be refused
- **Expected:**
  - UI: Ops web: only accounts holding security.mfa_policy or security.mfa_reset see the Security > policy controls (App.tsx adds the tab for those flags)
  - API: Guest: 401 (or 403 on missing CSRF token before authentication; record which)
  - API: Customer, NOGRANT, preview-ops-1: 403 FORBIDDEN
  - API: FX-STAFF-ADMIN-B password-only: GET 200; PUT 403 STEP_UP_REQUIRED
  - API: GET /api/v1/me/security canManagePolicy is true only for ADMIN-B
  - DB: Read-only: mfa_policy rows (mode, version) identical before and after; no new mfa.policy.update audit_event row
- **Also covered by automated tests:** MfaPolicyIntegrationTest.only_the_policy_permission_with_a_recent_second_factor_can_change_a_policy; MfaPolicyIntegrationTest.enforcement_and_settings_are_for_signed_in_accounts_only; MfaPolicyIntegrationTest.the_settings_report_which_security_administration_the_account_may_do
- **Unverified against code (confirm while executing):** Guest PUT order of CSRF vs authentication rejection not confirmed

### CFG-018 — MFA reset guard ladder: permission, recent MFA, reason length, self-reset, unknown account

- **Status:** NOT_RUN · **Priority:** P0 · **Readiness:** Needs the updated application deployment
- **Surface:** api, operations-web · **Actors:** FX-STAFF-SUPPORT, FX-STAFF-ADMIN-B, FX-CUST-B · **Needs:** updated deploy, isolated stack
- **Fixtures to provision:** FX-STAFF-ADMIN-B, FX-STAFF-SUPPORT
- **Traces to:** rules: BR-007, BR-043; decisions: D18; operations: postOpsAccountsMfaReset; partial: TC-007, TC-043
- **Prerequisites:**
  - Deployed release recorded: on the server, in the deploy/web-preview directory, run `./deploy.sh status` and note RELEASE_COMMIT, both image digests and the schema version
  - Deployed API contains PR #9 (D18): `./deploy.sh status` shows schema version >= 31 and RELEASE_COMMIT is the PR #9 head or later; origin/develop does NOT contain V31/MfaController, so on the current deployment GET /api/v1/me/security is not a 200
  - FX-STAFF-ADMIN-B holds active grants security.mfa_policy and security.mfa_reset and has an enrolled authenticator (TOTP secret kept in the private fixture sheet). Provision per docs/quality/owner-acceptance/fixtures.md (the bootstrap administrator preview-ops-1 holds only staff.grant and audit.read, so these permissions are absent by default)
  - OIDC_ADMIN_CLIENT_SECRET is set in the stack's .env (`grep -c '^OIDC_ADMIN_CLIENT_SECRET=.' .env` prints 1; never print the value)
  - FX-CUST-B account id known (SELECT id FROM app_user WHERE subject=... on the isolated DB, or from the ops web account search)
- **Steps:**
  1. (FX-STAFF-SUPPORT) POST /api/v1/ops/accounts/<FX-CUST-B id>/mfa-reset {"reason":"E2E reset attempt long"} — data: Support has no security.mfa_reset
  2. (FX-STAFF-ADMIN-B) Password-only session: same POST
  3. (FX-STAFF-ADMIN-B) After step-up: POST with {"reason":"short"} (5 chars)
  4. (FX-STAFF-ADMIN-B) After step-up: POST to its OWN account id with a valid reason
  5. (FX-STAFF-ADMIN-B) After step-up: POST to a random UUID 00000000-0000-4000-8000-000000000001 with a valid reason
- **Expected:**
  - UI: Ops web Security > 'Reset a user's two-step verification' requires Account id and a Reason of at least 10 characters
  - API: Step 1: 403 FORBIDDEN
  - API: Step 2: 403 STEP_UP_REQUIRED
  - API: Step 3: 400 VALIDATION_FAILED (reason min 10)
  - API: Step 4: 403 FORBIDDEN 'an administrator cannot reset their own MFA'
  - API: Step 5: 404 NOT_FOUND
  - API: No credential is removed in any step
  - DB: Read-only: step 4 writes audit_event action='mfa.reset' outcome='DENIED' reason 'self reset refused'; steps 1-3 and 5 write no mfa.reset.attempt row; user_mfa_reset has no row for FX-CUST-B
- **Also covered by automated tests:** MfaPolicyIntegrationTest.the_reset_is_guarded_by_permission_a_recent_factor_a_reason_and_never_applies_to_oneself; MfaResetAuditIntegrationTest
- **Unverified against code (confirm while executing):** Validation order (permission/recent MFA before body validation) follows @PreAuthorize then @Valid; step 3 status assumes MFA passes first

### CFG-019 — Authorized MFA reset removes the OTP credential, ends sessions, forces re-enrolment and is audited without exposing any secret

- **Status:** NOT_RUN · **Priority:** P0 · **Readiness:** Needs the updated application deployment
- **Surface:** api, operations-web, identity, customer-web · **Actors:** FX-STAFF-ADMIN-B, FX-CUST-B · **Needs:** updated deploy, isolated stack · **Destructive**
- **Fixtures to provision:** FX-STAFF-ADMIN-B
- **Traces to:** rules: BR-007; decisions: D18; operations: postOpsAccountsMfaReset; partial: TC-007
- **Prerequisites:**
  - Deployed release recorded: on the server, in the deploy/web-preview directory, run `./deploy.sh status` and note RELEASE_COMMIT, both image digests and the schema version
  - Deployed API contains PR #9 (D18): `./deploy.sh status` shows schema version >= 31 and RELEASE_COMMIT is the PR #9 head or later; origin/develop does NOT contain V31/MfaController, so on the current deployment GET /api/v1/me/security is not a 200
  - Live realm migrated (owner-gated): on the server `./migrate-mfa.sh status` prints flows=complete, browserFlow=matbakhy browser and client.matbakhy-admin=present (NOT true on the live realm today: migration has never been run there)
  - FX-STAFF-ADMIN-B holds active grants security.mfa_policy and security.mfa_reset and has an enrolled authenticator (TOTP secret kept in the private fixture sheet). Provision per docs/quality/owner-acceptance/fixtures.md (the bootstrap administrator preview-ops-1 holds only staff.grant and audit.read, so these permissions are absent by default)
  - OIDC_ADMIN_CLIENT_SECRET is set in the stack's .env (`grep -c '^OIDC_ADMIN_CLIENT_SECRET=.' .env` prints 1; never print the value)
  - FX-CUST-B enrolled an authenticator (CFG-102 for preview-customer-2) and has preference ON; FX-CUST-B is signed in in a separate browser window WB with an MFA session
  - This changes the live Keycloak credential of FX-CUST-B: run on FX-ISOLATED first; on the shared preview only after the owner agrees FX-CUST-B may lose its authenticator
- **Steps:**
  1. (FX-STAFF-ADMIN-B) Step-up on ops.<D>, Security tab > 'Reset a user's two-step verification': Account id = FX-CUST-B id, Reason = 'E2E reset authorised', click Reset — data: Or POST /api/v1/ops/accounts/<id>/mfa-reset
  2. (FX-CUST-B) In WB click anything that calls the API (GET /api/v1/me)
  3. (FX-CUST-B) Sign in password-only and GET /api/v1/me/security; then PATCH /api/v1/me — data: Preference is still ON so MFA is required
  4. (FX-CUST-B) Open https://api.<PREVIEW_DOMAIN>/oauth2/authorization/keycloak?stepUp=true&returnTo=https://app.<PREVIEW_DOMAIN>/ — data: Keycloak must lead to authenticator enrolment (no old authenticator works)
  5. (FX-CUST-B) Try the OLD authenticator code at the enrolment/OTP prompt (if an OTP form shows) — data: Old factor is gone
- **Expected:**
  - UI: Reset result text: 'Removed 1 verification factor(s) and ended the sessions.'
  - UI: WB is signed out
  - UI: Old authenticator codes no longer accepted
  - API: Reset: 200 {accountId, removedFactors:1, sessionsEnded:true}; response has no secret/OTP field
  - API: WB: 401
  - API: Password-only session: GET /api/v1/me 200, PATCH 403 STEP_UP_REQUIRED
  - API: The response of GET /api/v1/ops/audit never contains a credential
  - DB: Read-only: audit_event actions in order: mfa.reset.attempt (ALLOWED, reason) then mfa.reset (ALLOWED, details {removedFactors:1, sessionsEnded:true}); `SELECT reset_at, reset_by FROM user_mfa_reset WHERE user_id=<B>` has a row
- **Also covered by automated tests:** MfaResetIntegrationTest (real Keycloak + API); MfaResetAuditIntegrationTest
- **Unverified against code (confirm while executing):** Whether old authenticator codes are rejected at an OTP form after credential deletion is Keycloak behaviour; with no credential Keycloak should route to enrolment, not an OTP form

### CFG-020 — MFA reset while Keycloak admin access fails midway: attempt audited first, partial reset recorded, retry converges

- **Status:** NOT_RUN · **Priority:** P1 · **Readiness:** Needs the updated application deployment
- **Surface:** api, identity · **Actors:** FX-STAFF-ADMIN-B, FX-CUST-B · **Needs:** updated deploy, isolated stack · **Destructive**
- **Fixtures to provision:** FX-STAFF-ADMIN-B
- **Traces to:** rules: BR-007; decisions: D18; operations: postOpsAccountsMfaReset; partial: TC-007
- **Prerequisites:**
  - Deployed release recorded: on the server, in the deploy/web-preview directory, run `./deploy.sh status` and note RELEASE_COMMIT, both image digests and the schema version
  - Deployed API contains PR #9 (D18): `./deploy.sh status` shows schema version >= 31 and RELEASE_COMMIT is the PR #9 head or later; origin/develop does NOT contain V31/MfaController, so on the current deployment GET /api/v1/me/security is not a 200
  - FX-ISOLATED exists: a disposable compose stack with its own PREVIEW_DOMAIN and fresh volumes built from the PR #9 head images, realm imported, `./deploy.sh up` healthy, `./deploy.sh seed` run, and `./migrate-mfa.sh status` run on it prints flows=complete
  - FX-STAFF-ADMIN-B holds active grants security.mfa_policy and security.mfa_reset and has an enrolled authenticator (TOTP secret kept in the private fixture sheet). Provision per docs/quality/owner-acceptance/fixtures.md (the bootstrap administrator preview-ops-1 holds only staff.grant and audit.read, so these permissions are absent by default)
  - OIDC_ADMIN_CLIENT_SECRET is set in the stack's .env (`grep -c '^OIDC_ADMIN_CLIENT_SECRET=.' .env` prints 1; never print the value)
  - Operator can stop/start the identity container of the ISOLATED stack
- **Steps:**
  1. (FX-STAFF-ADMIN-B) On the isolated stack: `docker compose stop identity`, then POST /api/v1/ops/accounts/<FX-CUST-B id>/mfa-reset {"reason":"E2E outage reset"} — data: Keycloak down (unconfigured case is CFG-023)
  2. (FX-STAFF-ADMIN-B) `docker compose start identity`, wait until `./deploy.sh status` shows identity healthy, step-up again, repeat the same POST
  3. (FX-CUST-B) With a bearer/session issued BEFORE the first POST: call PATCH /api/v1/me — data: Pre-reset assertion must not count
- **Expected:**
  - UI: Ops web shows an error for step 1 (not 'Removed N factors')
  - API: Step 1: non-2xx (5xx or 503 DEPENDENCY_UNAVAILABLE), no sessionsEnded:true
  - API: Step 2: 200 removedFactors equals the credentials that actually existed (0 if none, 1 if enrolled); idempotent
  - API: Step 3: 403 STEP_UP_REQUIRED or 401, never success
  - DB: Read-only: audit_event has mfa.reset.attempt (ALLOWED) then mfa.reset (DENIED, reason starting 'reset incomplete: ', details {removedFactors, completed:false}) for step 1, and an ALLOWED mfa.reset for step 2; user_mfa_reset.reset_at set from step 1 (stays in force)
- **Also covered by automated tests:** MfaResetAuditIntegrationTest (partial failure audit)
- **Unverified against code (confirm while executing):** HTTP status returned when Keycloak is unreachable (RuntimeException rethrown) not confirmed; record the observed one

### CFG-021 — After a reset a still-valid pre-reset token or session cannot perform a protected or staff high-risk action

- **Status:** NOT_RUN · **Priority:** P0 · **Readiness:** Needs the updated application deployment
- **Surface:** api, identity · **Actors:** FX-STAFF-ADMIN-B, FX-CUST-B · **Needs:** updated deploy, isolated stack · **Destructive**
- **Traces to:** rules: BR-007; decisions: D18; operations: postOpsAccountsMfaReset, patchMe; partial: TC-007
- **Prerequisites:**
  - Deployed release recorded: on the server, in the deploy/web-preview directory, run `./deploy.sh status` and note RELEASE_COMMIT, both image digests and the schema version
  - Deployed API contains PR #9 (D18): `./deploy.sh status` shows schema version >= 31 and RELEASE_COMMIT is the PR #9 head or later; origin/develop does NOT contain V31/MfaController, so on the current deployment GET /api/v1/me/security is not a 200
  - Live realm migrated (owner-gated): on the server `./migrate-mfa.sh status` prints flows=complete, browserFlow=matbakhy browser and client.matbakhy-admin=present (NOT true on the live realm today: migration has never been run there)
  - FX-STAFF-ADMIN-B holds active grants security.mfa_policy and security.mfa_reset and has an enrolled authenticator (TOTP secret kept in the private fixture sheet). Provision per docs/quality/owner-acceptance/fixtures.md (the bootstrap administrator preview-ops-1 holds only staff.grant and audit.read, so these permissions are absent by default)
  - OIDC_ADMIN_CLIENT_SECRET is set in the stack's .env (`grep -c '^OIDC_ADMIN_CLIENT_SECRET=.' .env` prints 1; never print the value)
  - FX-CUST-B has preference ON and an enrolled authenticator; a mobile or scripted client holds an access token issued before the reset (web sessions are ended by the reset, so only the isolated stack with a bearer harness or the iPhone can show the token case: see CFG-109)
- **Steps:**
  1. (FX-CUST-B) Obtain an MFA-asserted access token A1 (acr mfa) via the isolated stack's test harness (identity/verify-mfa-flow.py style flow on a throwaway user, NOT a preview account); confirm PATCH /api/v1/me with A1 is 200
  2. (FX-STAFF-ADMIN-B) Reset that user's MFA (CFG-019 step 1)
  3. (FX-CUST-B) Within the 5 minute token lifetime repeat the PATCH with A1 — data: Token has not expired
  4. (FX-CUST-B) Re-authenticate with step-up after the reset; repeat PATCH with the new token A2 — data: Assertion newer than reset_at counts
- **Expected:**
  - UI: n/a (API-level)
  - API: Step 1: 200
  - API: Step 3: 403 STEP_UP_REQUIRED (satisfiedBy requires auth_time >= reset_at); the same A1 also fails StaffAssurance.recentMfa for a staff user
  - API: Step 4: 200
  - DB: Read-only: user_mfa_reset.reset_at later than A1's auth_time
- **Also covered by automated tests:** MfaPolicyIntegrationTest.an_administrative_reset_invalidates_assertions_made_before_it; MfaPolicyIntegrationTest.after_a_reset_a_still_valid_pre_reset_token_cannot_perform_a_sensitive_operation; StaffAssuranceTest
- **Unverified against code (confirm while executing):** There is no browser-only way to hold a pre-reset bearer token; the automated test is the primary evidence and the manual check is limited to the harness/device

### CFG-022 — Invalid and reused TOTP codes at the Keycloak OTP prompt are rejected and Keycloak brute-force protection applies

- **Status:** NOT_RUN · **Priority:** P1 · **Readiness:** Needs the updated application deployment
- **Surface:** identity · **Actors:** FX-CUST-A · **Needs:** updated deploy, isolated stack · **Destructive**
- **Traces to:** rules: BR-054; decisions: D18; operations: GET /oauth2/authorization/keycloak?stepUp=true; partial: TC-054
- **Prerequisites:**
  - Deployed release recorded: on the server, in the deploy/web-preview directory, run `./deploy.sh status` and note RELEASE_COMMIT, both image digests and the schema version
  - Live realm migrated (owner-gated): on the server `./migrate-mfa.sh status` prints flows=complete, browserFlow=matbakhy browser and client.matbakhy-admin=present (NOT true on the live realm today: migration has never been run there)
  - FX-ISOLATED exists: a disposable compose stack with its own PREVIEW_DOMAIN and fresh volumes built from the PR #9 head images, realm imported, `./deploy.sh up` healthy, `./deploy.sh seed` run, and `./migrate-mfa.sh status` run on it prints flows=complete
  - FX-CUST-A enrolled on the isolated stack (a lockout test must not hit preview-customer-1)
- **Steps:**
  1. (FX-CUST-A) Start step-up (https://api.<PREVIEW_DOMAIN>/oauth2/authorization/keycloak?stepUp=true&returnTo=https://app.<PREVIEW_DOMAIN>/); enter a wrong 6-digit code once
  2. (FX-CUST-A) Enter the correct current code; sign out of Keycloak; start step-up again within the same 30-second window and enter the SAME code — data: Replay of a used code
  3. (FX-CUST-A) Enter wrong codes consecutively until Keycloak shows a temporary lockout message; record the count of attempts — data: Realm has bruteForceProtected=true; thresholds not set in the realm file
- **Expected:**
  - UI: Wrong code: invalid authenticator code message, stays on OTP page
  - UI: Lockout: account temporarily disabled message
  - API: No token is issued on a wrong or replayed code; no STEP_UP satisfaction results (GET /api/v1/me/security verifiedInSession=false)
  - DB: none (credentials live in Keycloak)
- **Also covered by automated tests:** MfaIdpTest (wrong code)
- **Unverified against code (confirm while executing):** Whether Keycloak rejects a replayed TOTP depends on the OTP policy 'reusable' setting; not configured in matbakhy-realm.preview.json, so replay acceptance is an observation to record, not an assumed pass; Lockout threshold uses Keycloak defaults (not in the realm file)

### CFG-023 — Reset and provider-session end return a safe 503 when the Keycloak admin client secret is not configured; policy and preference keep working

- **Status:** NOT_RUN · **Priority:** P1 · **Readiness:** Needs the updated application deployment
- **Surface:** api · **Actors:** FX-STAFF-ADMIN-B, FX-CUST-A · **Needs:** updated deploy, isolated stack · **Destructive**
- **Fixtures to provision:** FX-STAFF-ADMIN-B
- **Traces to:** rules: BR-060; decisions: D18; operations: postOpsAccountsMfaReset, putMeSecurityMfa; partial: TC-060
- **Prerequisites:**
  - Deployed release recorded: on the server, in the deploy/web-preview directory, run `./deploy.sh status` and note RELEASE_COMMIT, both image digests and the schema version
  - Deployed API contains PR #9 (D18): `./deploy.sh status` shows schema version >= 31 and RELEASE_COMMIT is the PR #9 head or later; origin/develop does NOT contain V31/MfaController, so on the current deployment GET /api/v1/me/security is not a 200
  - FX-ISOLATED exists: a disposable compose stack with its own PREVIEW_DOMAIN and fresh volumes built from the PR #9 head images, realm imported, `./deploy.sh up` healthy, `./deploy.sh seed` run, and `./migrate-mfa.sh status` run on it prints flows=complete
  - FX-STAFF-ADMIN-B holds active grants security.mfa_policy and security.mfa_reset and has an enrolled authenticator (TOTP secret kept in the private fixture sheet). Provision per docs/quality/owner-acceptance/fixtures.md (the bootstrap administrator preview-ops-1 holds only staff.grant and audit.read, so these permissions are absent by default)
  - Isolated API started with OIDC_ADMIN_CLIENT_SECRET blank (compose env override), nothing else changed
- **Steps:**
  1. (FX-STAFF-ADMIN-B) After step-up: POST /api/v1/ops/accounts/<FX-CUST-A id>/mfa-reset {"reason":"E2E no secret"}
  2. (FX-STAFF-ADMIN-B) PUT /api/v1/ops/security/mfa-policy/STAFF with a valid body (policy still works)
  3. (FX-CUST-A) Enable then disable own MFA (CFG-008 steps 2 and 4) and read the API logs: `docker compose logs api --since 5m | grep 'provider admin access not configured'` — data: Disable still ends application sessions but not provider sessions
- **Expected:**
  - UI: Ops web shows a dependency-unavailable error for the reset
  - API: Step 1: 503 DEPENDENCY_UNAVAILABLE with field mfa=ADMIN_ACCESS_NOT_CONFIGURED
  - API: Step 2: 200
  - API: Step 3: 200, log line 'MFA turned off; provider admin access not configured, provider sessions were not ended' present
  - DB: Read-only: no mfa.reset.attempt and no user_mfa_reset row for FX-CUST-A (the guard runs before the attempt is audited)
- **Also covered by automated tests:** MfaPolicyIntegrationTest (reset guards); MfaResetAuditIntegrationTest

### CFG-104 — Staff high-risk actions (grants) require recent MFA regardless of policy mode; a password-only staff session is refused and the step-up path works (isolated, separate synthetic staff accounts)

- **Status:** NOT_RUN · **Priority:** P0 · **Readiness:** Needs the updated application deployment
- **Surface:** api, operations-web · **Actors:** FX-STAFF-ADMIN-B, FX-STAFF-SUPPORT · **Needs:** updated deploy, isolated stack · **Destructive**
- **Fixtures to provision:** FX-STAFF-ADMIN-B, FX-STAFF-SUPPORT
- **Traces to:** rules: BR-007, BR-043; operations: postOpsStaffGrants, getOpsStaff; partial: TC-007, TC-111
- **Prerequisites:**
  - Deployed release recorded: on the server, in the deploy/web-preview directory, run `./deploy.sh status` and note RELEASE_COMMIT, both image digests and the schema version
  - ISOLATED STACK ONLY: uses separate synthetic staff accounts (fixtures.md), NOT the password-only preview-ops-* accounts, and changes grants. FX-STAFF-ADMIN-B holds staff.grant and has an enrolled authenticator; FX-STAFF-SUPPORT has no support.case grant yet
  - FX-ISOLATED exists and `./migrate-mfa.sh status` there prints flows=complete (the step-up half needs the migrated realm; the refusal half works on any build with StaffAssurance)
  - Fixture variant (ordinary fixtures.json grants differ): FX-STAFF-SUPPORT normally holds support.case, so BEFORE the case revoke its support.case grant (POST /api/v1/ops/staff/<id>/grant-revocations) so the probe grant can be added; FX-STAFF-ADMIN-B keeps its ordinary grants (staff.grant, audit.read, config.read, config.write, security.mfa_policy, security.mfa_reset) and is not changed. AFTER the case, restore: re-grant support.case to FX-STAFF-SUPPORT (it must end with exactly its ordinary grant list ['support.case'], verify via GET /api/v1/ops/staff)
- **Steps:**
  1. (FX-STAFF-ADMIN-B) Sign in at ops.<PREVIEW_DOMAIN> password-only; open the Staff tab
  2. (FX-STAFF-ADMIN-B) POST /api/v1/ops/staff/<FX-STAFF-SUPPORT account id>/grants {"permission":"support.case","reason":"E2E grant probe"} with the password-only session — data: Confirm body field names in contracts/openapi.yaml postOpsStaffGrants first
  3. (FX-STAFF-ADMIN-B) Open https://api.<PREVIEW_DOMAIN>/oauth2/authorization/keycloak?stepUp=true&returnTo=https://ops.<PREVIEW_DOMAIN>/, enter the TOTP, repeat step 2
  4. (FX-STAFF-ADMIN-B) Cleanup/restore: revoke the probe support.case grant if still active, then ensure FX-STAFF-SUPPORT holds exactly one active support.case grant again (re-grant with step-up if needed), then GET /api/v1/ops/staff — data: End state equals fixtures.json: FX-STAFF-SUPPORT grants = [support.case]
- **Expected:**
  - UI: Ops web shows 'This action needs additional verification (MFA).' with a step-up link /oauth2/authorization/keycloak?stepUp=true&returnTo=... for the refused action
  - API: Step 2: 403 STEP_UP_REQUIRED (token has no acr/amr MFA evidence or auth_time older than OIDC_STEP_UP_MAX_AGE)
  - API: Step 3: 201/200 with the new grant
  - API: GET /api/v1/ops/staff still 200 without MFA
  - DB: Read-only: no user_grant row for FX-STAFF-SUPPORT after step 2; one active support.case row after step 3 (audit_event staff.grant ALLOWED); revoked_at set after step 4 (audit_event staff.revoke ALLOWED)
- **Also covered by automated tests:** StaffAssuranceTest; StaffAccessIntegrationTest
- **Unverified against code (confirm while executing):** Request body field names for postOpsStaffGrants (permission, reason, expiresAt) taken from GrantService signature, schema not read; confirm in contracts/openapi.yaml before running

### CFG-105 — Staff grants: no self-escalation, no duplicate active grant, last staff.grant holder cannot be revoked

- **Status:** NOT_RUN · **Priority:** P0 · **Readiness:** Needs the updated application deployment
- **Surface:** api, operations-web · **Actors:** FX-STAFF-ADMIN, FX-STAFF-ADMIN-B · **Needs:** updated deploy, isolated stack · **Destructive**
- **Fixtures to provision:** FX-STAFF-ADMIN-B
- **Traces to:** scenarios: TC-043; rules: BR-007, BR-043; operations: postOpsStaffGrants, postOpsStaffGrantRevocations; partial: TC-007, TC-126
- **Prerequisites:**
  - Deployed release recorded: on the server, in the deploy/web-preview directory, run `./deploy.sh status` and note RELEASE_COMMIT, both image digests and the schema version
  - Deployed API contains PR #9 (D18): `./deploy.sh status` shows schema version >= 31 and RELEASE_COMMIT is the PR #9 head or later; origin/develop does NOT contain V31/MfaController, so on the current deployment GET /api/v1/me/security is not a 200
  - FX-ISOLATED exists: a disposable compose stack with its own PREVIEW_DOMAIN and fresh volumes built from the PR #9 head images, realm imported, `./deploy.sh up` healthy, `./deploy.sh seed` run, and `./migrate-mfa.sh status` run on it prints flows=complete
  - Isolated stack with exactly one holder of staff.grant (preview-ops-1 bootstrap), another account FX-STAFF-ADMIN-B present, and step-up done for each admin before its calls
  - Fixture variant: needs exactly ONE active staff.grant holder. FX-STAFF-ADMIN-B (ordinary: staff.grant, audit.read, config.read, config.write, security.mfa_policy, security.mfa_reset) is NOT used as the second holder initially; use preview-ops-1/FX-STAFF-ADMIN as the sole staff.grant holder: if ADMIN-B exists on the stack, revoke its staff.grant first (needs another holder, so do this before revoking anyone else) and RESTORE it at the end. Step 2 grants security.mfa_policy to ADMIN-B, which ordinarily already holds it: duplicate-grant 409 is then the expected first answer, or revoke that grant before and restore after. Step 4 adds staff.grant to ADMIN-B and revokes the original holder's: re-grant the original holder's staff.grant at the end. Final state must equal fixtures.json grants for both accounts
- **Steps:**
  1. (FX-STAFF-ADMIN) POST /api/v1/ops/staff/<own id>/grants {"permission":"security.mfa_policy","reason":"E2E self"} — data: Self grant
  2. (FX-STAFF-ADMIN) POST /api/v1/ops/staff/<ADMIN-B id>/grants security.mfa_policy, then the identical request again
  3. (FX-STAFF-ADMIN) POST /api/v1/ops/staff/<own id>/grant-revocations {"grantId":"<own staff.grant id>","reason":"E2E last admin"} while it is the only active staff.grant holder — data: Last administrator
  4. (FX-STAFF-ADMIN) POST /api/v1/ops/staff/<ADMIN-B id>/grants staff.grant, then revoke preview-ops-1's own staff.grant — data: Allowed once a second holder exists (restore afterwards)
- **Expected:**
  - UI: Staff tab shows the refusal messages mapped from FORBIDDEN / STATE_CONFLICT
  - API: Step 1: 403 FORBIDDEN
  - API: Step 2: first 201/200, second 409 STATE_CONFLICT 'grant already active'
  - API: Step 3: 409 STATE_CONFLICT 'last administrator'
  - API: Step 4: grant 200; revoke 200
  - DB: Read-only: audit_event staff.grant DENIED reason 'self-escalation refused'; staff.revoke DENIED reason 'last administrator protected'; user_grant keeps one active staff.grant row until step 4
- **Also covered by automated tests:** StaffAccessIntegrationTest; StaffAssuranceTest
- **Unverified against code (confirm while executing):** Request body field names (grantId) not confirmed in the OpenAPI schema; read postOpsStaffGrantRevocations before running

### CFG-106 — Operator reference: staff and cook fixtures needed by the MFA cases are provisioned as in fixtures.md and carry the intended grants

- **Status:** NOT_RUN · **Priority:** P1 · **Readiness:** Needs the updated application deployment
- **Surface:** api, deployment · **Actors:** FX-STAFF-ADMIN · **Needs:** updated deploy, isolated stack · **Destructive**
- **Fixtures to provision:** FX-STAFF-ADMIN-B, FX-STAFF-SUPPORT, FX-STAFF-OPS, FX-STAFF-FINANCE, FX-MULTIROLE
- **Traces to:** rules: BR-007; operations: postOpsStaffGrants, getMeSecurity
- **Prerequisites:**
  - Deployed release recorded: on the server, in the deploy/web-preview directory, run `./deploy.sh status` and note RELEASE_COMMIT, both image digests and the schema version
  - FX-ISOLATED exists (see CFG-101 prerequisites); provisioning itself is described only in docs/quality/owner-acceptance/fixtures.md (Keycloak user ids are not preserved by `kcadm create users`; do not rely on fixed ids)
- **Steps:**
  1. (FX-STAFF-ADMIN) Provision FX-STAFF-ADMIN-B, FX-STAFF-SUPPORT, FX-STAFF-OPS, FX-STAFF-FINANCE and FX-MULTIROLE exactly as docs/quality/owner-acceptance/fixtures.md says — data: No provisioning commands are duplicated here
  2. (FX-STAFF-ADMIN-B) After enrolling an authenticator (CFG-102) call GET /api/v1/me/security — data: canManagePolicy and canResetMfa must be true
  3. (FX-STAFF-ADMIN) GET /api/v1/ops/staff to list grants — data: Compare with the grant list in fixtures.md
- **Expected:**
  - UI: Ops web Staff tab shows each account with the granted permissions
  - API: ADMIN-B settings report canManagePolicy=true, canResetMfa=true
  - API: Staff list shows exactly the grants fixtures.md lists for each fixture
  - DB: Read-only: `SELECT user_id, permission FROM user_grant WHERE revoked_at IS NULL` lists exactly the intended rows
- **Also covered by automated tests:** deploy/web-preview/e2e-fixtures (SQL fixtures for cook/staff grants); scripts/test_e2e_fixtures.py
- **Unverified against code (confirm while executing):** Permission code strings finance.verify, order.ops, cook.review taken from the V31 CHECK list; role semantics per docs/product/06-permissions.md not re-read

### CFG-024 — iPhone step-up for the same customer: banner, system-browser verification, tokens replaced, refused order placement re-sent only by explicit retry with the same idempotency key (device plan C2, C3)

- **Status:** NOT_RUN · **Priority:** P0 · **Readiness:** Needs a physical iOS/Android build
- **Surface:** customer-mobile, identity, api · **Actors:** FX-CUST-A, FX-DEVICE-IOS · **Needs:** updated deploy, physical device
- **Traces to:** rules: BR-048, BR-052; decisions: D18; operations: putMeSecurityMfa, POST /api/v1/orders; partial: TC-048, TC-052, TC-131
- **Prerequisites:**
  - Deployed release recorded: on the server, in the deploy/web-preview directory, run `./deploy.sh status` and note RELEASE_COMMIT, both image digests and the schema version
  - Deployed API contains PR #9 (D18): `./deploy.sh status` shows schema version >= 31 and RELEASE_COMMIT is the PR #9 head or later; origin/develop does NOT contain V31/MfaController, so on the current deployment GET /api/v1/me/security is not a 200
  - Live realm migrated (owner-gated): on the server `./migrate-mfa.sh status` prints flows=complete, browserFlow=matbakhy browser and client.matbakhy-admin=present (NOT true on the live realm today: migration has never been run there)
  - TestFlight/Preview build installed on FX-DEVICE-IOS (physical iPhone) and the mobile OIDC client registered (docs/mobile/ios-testflight-beta.md step 'register the mobile client'; `VERIFY_MOBILE=1 ./migrate-mfa.sh verify` no longer SKIPs mobile checks). These documents (ios-beta-device-test-plan.md, ios-testflight-beta.md) exist on branch beta/ios-testflight-prep, NOT on PR #9 or develop
  - Preview allow-list decision made so the phone's network is inside CUSTOMER_ALLOWED_IPS (device test plan expects no 403 'Not available')
  - Customer web (app.<D>) and the phone use the same synthetic customer FX-CUST-A, preference currently off
- **Steps:**
  1. (FX-DEVICE-IOS) C1 first: on the phone sign in as FX-CUST-A, browse, add to basket, request a quote. No OTP is asked anywhere
  2. (FX-CUST-A) On the web (app.<D> > Account > Security) turn on two-step verification (CFG-008 steps 1-2) — data: Now MFA is required for this account
  3. (FX-DEVICE-IOS) On the phone with the basket still filled press the checkout/place-order button — data: Placement is refused 403 STEP_UP_REQUIRED
  4. (FX-DEVICE-IOS) Observe the step-up banner; do NOT tap anything for 10 seconds; check that no order exists (web Orders list) — data: The refused placement must not be re-sent automatically
  5. (FX-DEVICE-IOS) Tap the banner 'Verify'; in the system browser sheet enter the password (prompt=login) and the OTP; return to the app — data: acr_values=mfa 2 is sent by the app
  6. (FX-DEVICE-IOS) Pull to refresh; verify basket and account are unchanged; tap the explicit retry/place-order again exactly once — data: Same pending placement
  7. (FX-CUST-A) Count orders on the web Orders list and in the DB (isolated) / read-only SQL: `SELECT count(*) FROM customer_order WHERE customer_id=<A> AND created_at > <test start>`
- **Expected:**
  - UI: Banner testID step-up-banner with a Verify button appears after the refused call and disappears after verification
  - UI: Basket and account unchanged after return; screens work after pull-to-refresh
  - API: Refused placement: 403 STEP_UP_REQUIRED, no order created
  - API: After verification the explicit retry returns 201 (or the idempotent replay of the same Idempotency-Key); exactly one order in total
  - API: Tokens replaced for the same subject (GET /api/v1/me returns the same account id)
  - DB: Read-only: exactly one customer_order row created after the test start for the customer; idempotency_record has one row for (actor_id, idempotency_key) of the placement (V1__baseline_identity_and_idempotency.sql)
- **Also covered by automated tests:** apps/customer-mobile/src/signIn.test.ts, auth.test.ts, App.test.tsx (jest: params, banner, cancel, other 403s); native CI builds (no MFA journey)
- **Unverified against code (confirm while executing):** mfa-policy.md states no physical-device or simulator MFA run exists: this case is the first real evidence; Commit 89ec820 says a refused placement is re-sent only by explicit retry: confirm the visible control name on device

### CFG-025 — iPhone step-up that signs in as a DIFFERENT person is an account switch: previous profile, basket, orders and caches are gone before the new person is shown (device plan B5/C3)

- **Status:** NOT_RUN · **Priority:** P0 · **Readiness:** Needs a physical iOS/Android build
- **Surface:** customer-mobile, identity · **Actors:** FX-CUST-A, FX-CUST-B, FX-DEVICE-IOS · **Needs:** updated deploy, physical device
- **Traces to:** scenarios: TC-048; rules: BR-048, BR-006; decisions: D18; operations: getMe; partial: TC-052, TC-131
- **Prerequisites:**
  - Deployed release recorded: on the server, in the deploy/web-preview directory, run `./deploy.sh status` and note RELEASE_COMMIT, both image digests and the schema version
  - Deployed API contains PR #9 (D18): `./deploy.sh status` shows schema version >= 31 and RELEASE_COMMIT is the PR #9 head or later; origin/develop does NOT contain V31/MfaController, so on the current deployment GET /api/v1/me/security is not a 200
  - Live realm migrated (owner-gated): on the server `./migrate-mfa.sh status` prints flows=complete, browserFlow=matbakhy browser and client.matbakhy-admin=present (NOT true on the live realm today: migration has never been run there)
  - TestFlight/Preview build installed on FX-DEVICE-IOS (physical iPhone) and the mobile OIDC client registered (docs/mobile/ios-testflight-beta.md step 'register the mobile client'; `VERIFY_MOBILE=1 ./migrate-mfa.sh verify` no longer SKIPs mobile checks). These documents (ios-beta-device-test-plan.md, ios-testflight-beta.md) exist on branch beta/ios-testflight-prep, NOT on PR #9 or develop
  - Preview allow-list decision made so the phone's network is inside CUSTOMER_ALLOWED_IPS (device test plan expects no 403 'Not available')
  - Customer web (app.<D>) and the phone use the same synthetic customer FX-CUST-A, preference currently off
- **Steps:**
  1. (FX-DEVICE-IOS) Phone signed in as FX-CUST-A with a non-empty basket; make A require MFA (CFG-024 steps 2-3) so the banner shows
  2. (FX-DEVICE-IOS) Tap Verify; at the Keycloak page use 'Sign in as another user' / enter preview-customer-2 credentials from the private fixture sheet and pass its OTP (B enrolled) — data: Different subject than the one that started the step-up
  3. (FX-DEVICE-IOS) After return open Basket, Orders, Account, Favorites, Addresses
  4. (FX-DEVICE-IOS) Cancel the browser sheet during a fresh step-up (device plan C4): app returns signed in as A, banner stays
- **Expected:**
  - UI: After step 2 the app shows B's data only; every screen remounts; no flash of A's name or basket
  - UI: Step 4: banner remains, still signed in as A
  - API: GET /api/v1/me after return is B's account
  - API: No A order or address appears in any list
  - DB: none
- **Also covered by automated tests:** jest AppContext/App tests for account-switch cleanup (commit 5e0ce79, 89ec820)
- **Unverified against code (confirm while executing):** Keycloak's 'sign in as another user' wording differs by theme; the equivalent is signing in with different credentials

### CFG-026 — iPhone returns from the system browser, background/kill/airplane cases around sign-in and step-up recover without stale loading or duplicate order (device plan B2, B3, B6, B7)

- **Status:** NOT_RUN · **Priority:** P1 · **Readiness:** Needs a physical iOS/Android build
- **Surface:** customer-mobile, identity · **Actors:** FX-CUST-A, FX-DEVICE-IOS · **Needs:** updated deploy, physical device
- **Traces to:** rules: BR-048; operations: getMe, POST /api/v1/orders; partial: TC-048, TC-052
- **Prerequisites:**
  - Deployed release recorded: on the server, in the deploy/web-preview directory, run `./deploy.sh status` and note RELEASE_COMMIT, both image digests and the schema version
  - Deployed API contains PR #9 (D18): `./deploy.sh status` shows schema version >= 31 and RELEASE_COMMIT is the PR #9 head or later; origin/develop does NOT contain V31/MfaController, so on the current deployment GET /api/v1/me/security is not a 200
  - Live realm migrated (owner-gated): on the server `./migrate-mfa.sh status` prints flows=complete, browserFlow=matbakhy browser and client.matbakhy-admin=present (NOT true on the live realm today: migration has never been run there)
  - TestFlight/Preview build installed on FX-DEVICE-IOS (physical iPhone) and the mobile OIDC client registered (docs/mobile/ios-testflight-beta.md step 'register the mobile client'; `VERIFY_MOBILE=1 ./migrate-mfa.sh verify` no longer SKIPs mobile checks). These documents (ios-beta-device-test-plan.md, ios-testflight-beta.md) exist on branch beta/ios-testflight-prep, NOT on PR #9 or develop
  - Preview allow-list decision made so the phone's network is inside CUSTOMER_ALLOWED_IPS (device test plan expects no 403 'Not available')
  - Customer web (app.<D>) and the phone use the same synthetic customer FX-CUST-A, preference currently off
- **Steps:**
  1. (FX-DEVICE-IOS) Start sign-in, background the app (home swipe) while the system browser sheet is open, return after 30 seconds, finish sign-in — data: B1/B6 variant
  2. (FX-DEVICE-IOS) Kill the app from the switcher and reopen (B2); then wait 6 minutes (access token lifespan 300 s) and open Orders (B3) — data: Refresh token path
  3. (FX-DEVICE-IOS) Cancel the browser sheet during sign-in (B6)
  4. (FX-DEVICE-IOS) Enable airplane mode right after tapping place order, disable it, wait for recovery (B7)
- **Expected:**
  - UI: No endless spinner; signed-in state restored from Keychain after kill; cancel returns to the app signed out without a crash
  - API: Refresh works without a new login after 5 minutes
  - API: Exactly one order after the airplane-mode submit
  - DB: Read-only: exactly one customer_order row for the attempted placement
- **Also covered by automated tests:** apps/customer-mobile/src/placement.ts tests (stored placement with fixed idempotency key)
- **Unverified against code (confirm while executing):** Device plan rows B2/B3/B6/B7 are owned by the mobile domain author too; keep only one execution record

### CFG-108 — iPhone with MFA turned on later by the user: C2 banner appears on the next protected screen and nothing is broken; C4 cancel keeps banner and sign-in

- **Status:** NOT_RUN · **Priority:** P1 · **Readiness:** Needs a physical iOS/Android build
- **Surface:** customer-mobile · **Actors:** FX-CUST-A, FX-DEVICE-IOS · **Needs:** updated deploy, physical device
- **Traces to:** rules: BR-048; decisions: D18; operations: getMeSecurity; partial: TC-048, TC-052
- **Prerequisites:**
  - Deployed release recorded: on the server, in the deploy/web-preview directory, run `./deploy.sh status` and note RELEASE_COMMIT, both image digests and the schema version
  - Deployed API contains PR #9 (D18): `./deploy.sh status` shows schema version >= 31 and RELEASE_COMMIT is the PR #9 head or later; origin/develop does NOT contain V31/MfaController, so on the current deployment GET /api/v1/me/security is not a 200
  - Live realm migrated (owner-gated): on the server `./migrate-mfa.sh status` prints flows=complete, browserFlow=matbakhy browser and client.matbakhy-admin=present (NOT true on the live realm today: migration has never been run there)
  - TestFlight/Preview build installed on FX-DEVICE-IOS (physical iPhone) and the mobile OIDC client registered (docs/mobile/ios-testflight-beta.md step 'register the mobile client'; `VERIFY_MOBILE=1 ./migrate-mfa.sh verify` no longer SKIPs mobile checks). These documents (ios-beta-device-test-plan.md, ios-testflight-beta.md) exist on branch beta/ios-testflight-prep, NOT on PR #9 or develop
  - Preview allow-list decision made so the phone's network is inside CUSTOMER_ALLOWED_IPS (device test plan expects no 403 'Not available')
  - Customer web (app.<D>) and the phone use the same synthetic customer FX-CUST-A, preference currently off
- **Steps:**
  1. (FX-DEVICE-IOS) C1: signed in as FX-CUST-A, browse/basket/quote/order screens, record that no OTP is requested
  2. (FX-CUST-A) On the web turn on two-step verification
  3. (FX-DEVICE-IOS) C2: navigate to Orders then Account; note banner and screens
  4. (FX-DEVICE-IOS) C4: tap Verify then cancel the sheet; reopen the app
  5. (FX-CUST-A) Cleanup on web after step-up: turn two-step verification off (needs recent factor)
- **Expected:**
  - UI: Banner with Verify on the next protected screen; no raw error text; no blank screens
  - API: Open endpoints (meals, config) keep answering 200 while protected ones answer 403 STEP_UP_REQUIRED
  - DB: none
- **Also covered by automated tests:** jest App.test.tsx banner tests

### CFG-109 — iPhone after an administrator reset and after CUSTOMER=REQUIRED: next protected call asks for verification again (device plan C5, C6)

- **Status:** BLOCKED — Requires an isolated stack reachable from a physical iPhone over TLS; no such environment is defined in the repository · **Priority:** P0 · **Readiness:** Needs a physical iOS/Android build
- **Surface:** customer-mobile, api, operations-web · **Actors:** FX-CUST-A, FX-STAFF-ADMIN-B, FX-DEVICE-IOS · **Needs:** updated deploy, physical device, isolated stack · **Destructive**
- **Fixtures to provision:** FX-STAFF-ADMIN-B
- **Traces to:** rules: BR-048, BR-042; decisions: D18; operations: postOpsAccountsMfaReset, putOpsSecurityMfaPolicyByCategory; partial: TC-048, TC-052
- **Prerequisites:**
  - Deployed release recorded: on the server, in the deploy/web-preview directory, run `./deploy.sh status` and note RELEASE_COMMIT, both image digests and the schema version
  - Deployed API contains PR #9 (D18): `./deploy.sh status` shows schema version >= 31 and RELEASE_COMMIT is the PR #9 head or later; origin/develop does NOT contain V31/MfaController, so on the current deployment GET /api/v1/me/security is not a 200
  - Live realm migrated (owner-gated): on the server `./migrate-mfa.sh status` prints flows=complete, browserFlow=matbakhy browser and client.matbakhy-admin=present (NOT true on the live realm today: migration has never been run there)
  - TestFlight/Preview build installed on FX-DEVICE-IOS (physical iPhone) and the mobile OIDC client registered (docs/mobile/ios-testflight-beta.md step 'register the mobile client'; `VERIFY_MOBILE=1 ./migrate-mfa.sh verify` no longer SKIPs mobile checks). These documents (ios-beta-device-test-plan.md, ios-testflight-beta.md) exist on branch beta/ios-testflight-prep, NOT on PR #9 or develop
  - Preview allow-list decision made so the phone's network is inside CUSTOMER_ALLOWED_IPS (device test plan expects no 403 'Not available')
  - Customer web (app.<D>) and the phone use the same synthetic customer FX-CUST-A, preference currently off
  - FX-STAFF-ADMIN-B holds active grants security.mfa_policy and security.mfa_reset and has an enrolled authenticator (TOTP secret kept in the private fixture sheet). Provision per docs/quality/owner-acceptance/fixtures.md (the bootstrap administrator preview-ops-1 holds only staff.grant and audit.read, so these permissions are absent by default)
  - Preferred: FX-ISOLATED exists (CUSTOMER=REQUIRED must not be set on the shared preview before the mobile app is device-verified, mfa-policy.md step 5): a disposable compose stack with its own PREVIEW_DOMAIN and fresh volumes built from the PR #9 head images, realm imported, `./deploy.sh up` healthy, `./deploy.sh seed` run, and `./migrate-mfa.sh status` run on it prints flows=complete
- **Steps:**
  1. (FX-DEVICE-IOS) Phone signed in as A with an MFA session (after CFG-024); screens work
  2. (FX-STAFF-ADMIN-B) C5: reset FX-CUST-A's MFA (CFG-019 step 1)
  3. (FX-DEVICE-IOS) Open Orders
  4. (FX-DEVICE-IOS) Verify again (re-enrolment); then on the web turn A's preference off
  5. (FX-STAFF-ADMIN-B) C6: set CUSTOMER to REQUIRED, then on the phone open Orders; afterwards set CUSTOMER back to OPTIONAL and reopen Orders
- **Expected:**
  - UI: After step 3 banner or sign-in prompt, never stale data
  - UI: After REQUIRED the phone shows the banner on the next protected call; after OPTIONAL the journey works as C1
  - API: Step 3: 401 (sessions ended) or 403 STEP_UP_REQUIRED; old token no longer counts
  - API: C6: 403 STEP_UP_REQUIRED then 200 after verification, and 200 again with no prompt after policy back to OPTIONAL and preference off
  - DB: Read-only: user_mfa_reset row for A; mfa_policy CUSTOMER version +2
- **Also covered by automated tests:** MfaPolicyIntegrationTest.an_administrative_reset_invalidates_assertions_made_before_it
- **Unverified against code (confirm while executing):** Isolated stack must be reachable from the phone (needs public hostname/TLS) or the case stays BLOCKED

### CFG-027 — Realm migration status is read-only and reports the real state (flow, browser binding, clients, admin client) without disclosing secrets

- **Status:** NOT_RUN · **Priority:** P0 · **Readiness:** Needs the updated application deployment
- **Surface:** identity, deployment · **Actors:** FX-ISOLATED · **Needs:** updated deploy, isolated stack
- **Traces to:** rules: BR-060; decisions: D18; operations: ./migrate-mfa.sh status; partial: TC-060, TC-140
- **Prerequisites:**
  - Deployed release recorded: on the server, in the deploy/web-preview directory, run `./deploy.sh status` and note RELEASE_COMMIT, both image digests and the schema version
  - Operator shell on the server in the deploy/web-preview directory (CD uses /srv/matbakhy-staging/deploy/web-preview; docs/operations/06 mentions /srv/matbakhy-preview/src/deploy/web-preview; use whichever `./deploy.sh status` works in)
  - `.env` present with KEYCLOAK_ADMIN_USER, KEYCLOAK_ADMIN_PASSWORD, PREVIEW_DOMAIN
- **Steps:**
  1. (FX-ISOLATED) `./migrate-mfa.sh status | tee /tmp/status.txt` on the stack still holding the pre-D18 realm (import `git show origin/develop:deploy/web-preview/identity/matbakhy-realm.preview.json`) — data: Expect pre-migration values
  2. (FX-ISOLATED) `grep -c -E 'PASSWORD|SECRET' /tmp/status.txt` — data: Count of lines mentioning secrets
  3. (FX-ISOLATED) Run `./migrate-mfa.sh status` twice and diff the outputs — data: Read-only: no change between runs
- **Expected:**
  - UI: n/a (operator output)
  - API: Output lines include flows=absent (or incomplete/complete), browserFlow=<name>, client.<id>=present|absent, client.matbakhy-admin=present|absent
  - API: Pre-D18 realm: flows=absent, browserFlow=browser, client.matbakhy-admin=absent; two runs identical (read-only)
  - API: No password or client secret in the output
  - DB: none (identity store only)
- **Also covered by automated tests:** scripts/test_keycloak_mfa_migration.py
- **Unverified against code (confirm while executing):** The preview realm today is believed to be pre-D18 (docs/operations/13 says NOT yet run on the Contabo preview): confirm with `status` on the real server before assuming, it is read-only and safe there

### CFG-028 — Apply the MFA realm migration: verified backup first, flow bound last, verify gate prints RESULT (owner-gated, live-realm change)

- **Status:** NOT_RUN · **Priority:** P0 · **Readiness:** Needs the updated application deployment
- **Surface:** identity, deployment · **Actors:** FX-ISOLATED · **Needs:** updated deploy, isolated stack · **Destructive**
- **Traces to:** rules: BR-057, BR-060; decisions: D18; operations: ./migrate-mfa.sh apply, ./migrate-mfa.sh verify; partial: TC-057, TC-060, TC-140
- **Prerequisites:**
  - Deployed release recorded: on the server, in the deploy/web-preview directory, run `./deploy.sh status` and note RELEASE_COMMIT, both image digests and the schema version
  - Operator shell on the server in the deploy/web-preview directory (CD uses /srv/matbakhy-staging/deploy/web-preview; docs/operations/06 mentions /srv/matbakhy-preview/src/deploy/web-preview; use whichever `./deploy.sh status` works in)
  - Deployed API contains PR #9 (D18): `./deploy.sh status` shows schema version >= 31 and RELEASE_COMMIT is the PR #9 head or later; origin/develop does NOT contain V31/MfaController, so on the current deployment GET /api/v1/me/security is not a 200
  - Rehearse on FX-ISOLATED first (realm imported from origin/develop's realm file, then `./migrate-mfa.sh apply`); the live run additionally requires owner authorization and a change window (3-5 minutes, no downtime)
  - `./migrate-mfa.sh status` shows flows=absent and `./deploy.sh status` shows the PR #9 release
  - Free space for backups under $BACKUP_DIR (default /srv/matbakhy-backups)
- **Steps:**
  1. (FX-ISOLATED) `./migrate-mfa.sh status` (record) then `./migrate-mfa.sh apply 2>&1 | tee apply.log`
  2. (FX-ISOLATED) `ls -l $BACKUP_DIR/keycloak/` and `ls -l $BACKUP_DIR/db-*/` — data: Backups exist, mode 600 for keycloak files
  3. (FX-ISOLATED) `./migrate-mfa.sh status` — data: After
  4. (FX-ISOLATED) Last line of apply.log and exit code: `tail -n1 apply.log; echo exit=$?`
  5. (FX-CUST-A) Normal sign-in of preview-customer-1 and preview-ops-1 at the web apps: no OTP asked — data: Users unaffected
  6. (FX-ISOLATED) Optional deeper check, operator only: via `./kcadm-in-container.sh` after `config credentials --config /tmp/kcadm-mfa.cfg`, inspect the service-account roles of client matbakhy-admin (kcadm 'get-roles'); expect only view-users and manage-users — data: Only view-users and manage-users
- **Expected:**
  - UI: Normal sign-in unchanged for existing users
  - API: apply.log shows the order: database backup, Keycloak DB dump, realm export, migration, verify; the final line is `RESULT: PASS` (exit 0), or `RESULT: INCOMPLETE` (exit 3, 'applied, not proven') or FAIL (exit 1); PASS requires verify-mfa-flow.py to have exercised acr/amr and the API
  - API: After: flows=complete, browserFlow=matbakhy browser, client.matbakhy-admin=present
  - API: Service account holds exactly view-users and manage-users
  - DB: Read-only: files keycloak-db-<stamp>.dump, realm-before-<stamp>.json and mfa-migration-state.env under $BACKUP_DIR/keycloak/; the deploy backup was verified (deploy.sh prints 'backup verified')
- **Also covered by automated tests:** scripts/test_keycloak_mfa_migration.py; deploy/web-preview/identity/verify-mfa-flow.py (rehearsal PASS on a throwaway Keycloak 26.8.0 with a stand-in API only)
- **Unverified against code (confirm while executing):** Step 6 command line for the role listing was not run; kcadm syntax must be confirmed by the operator; Live run on the Contabo preview has never happened: all evidence is local rehearsal (docs/operations/13)

### CFG-029 — Re-running apply on a complete realm changes nothing (idempotent) and does not alter policy or users

- **Status:** NOT_RUN · **Priority:** P1 · **Readiness:** Needs the updated application deployment
- **Surface:** identity, deployment · **Actors:** FX-ISOLATED · **Needs:** updated deploy, isolated stack · **Destructive**
- **Traces to:** rules: BR-060; decisions: D18; operations: ./migrate-mfa.sh apply; partial: TC-060
- **Prerequisites:**
  - Deployed release recorded: on the server, in the deploy/web-preview directory, run `./deploy.sh status` and note RELEASE_COMMIT, both image digests and the schema version
  - Operator shell on the server in the deploy/web-preview directory (CD uses /srv/matbakhy-staging/deploy/web-preview; docs/operations/06 mentions /srv/matbakhy-preview/src/deploy/web-preview; use whichever `./deploy.sh status` works in)
  - FX-ISOLATED exists: a disposable compose stack with its own PREVIEW_DOMAIN and fresh volumes built from the PR #9 head images, realm imported, `./deploy.sh up` healthy, `./deploy.sh seed` run, and `./migrate-mfa.sh status` run on it prints flows=complete
  - One successful apply already done (CFG-028)
- **Steps:**
  1. (FX-ISOLATED) `./migrate-mfa.sh status > before.txt; ./migrate-mfa.sh apply 2>&1 | tee again.log; ./migrate-mfa.sh status > after.txt; diff before.txt after.txt`
  2. (FX-ISOLATED) Read the mfa_policy rows: `docker compose exec -T postgres psql -U matbakhy -d matbakhy -c 'SELECT category,mode,version FROM mfa_policy ORDER BY 1'` before and after — data: Policy must be untouched
  3. (FX-ISOLATED) `sh -c '. ./identity/mfa-state.sh; state_flow "$BACKUP_DIR/keycloak/mfa-migration-state.env"'` — data: Rollback baseline must still be the ORIGINAL flow `browser`, not `matbakhy browser` (fix in commit ff64f32)
- **Expected:**
  - UI: n/a
  - API: again.log contains 'already complete'; diff of status is empty; ends with a RESULT line
  - API: state_flow prints `browser`
  - DB: mfa_policy rows identical before and after; no duplicate flows/clients (status shows one of each)
- **Also covered by automated tests:** scripts/test_keycloak_mfa_migration.py (repeated apply keeps rollback baseline)
- **Unverified against code (confirm while executing):** state_flow function name taken from backup() in migrate-mfa.sh; confirm in identity/mfa-state.sh

### CFG-030 — Assurance proof: verify-mfa-flow.py issues real tokens and proves level-1 has no MFA evidence while step-up carries acr/amr and the API accepts/rejects accordingly

- **Status:** NOT_RUN · **Priority:** P0 · **Readiness:** Needs the updated application deployment
- **Surface:** identity, api, deployment · **Actors:** FX-ISOLATED · **Needs:** updated deploy, isolated stack · **Destructive**
- **Traces to:** rules: BR-001, BR-002; decisions: D18; operations: ./migrate-mfa.sh verify; partial: TC-002, TC-109
- **Prerequisites:**
  - Deployed release recorded: on the server, in the deploy/web-preview directory, run `./deploy.sh status` and note RELEASE_COMMIT, both image digests and the schema version
  - Operator shell on the server in the deploy/web-preview directory (CD uses /srv/matbakhy-staging/deploy/web-preview; docs/operations/06 mentions /srv/matbakhy-preview/src/deploy/web-preview; use whichever `./deploy.sh status` works in)
  - Deployed API contains PR #9 (D18): `./deploy.sh status` shows schema version >= 31 and RELEASE_COMMIT is the PR #9 head or later; origin/develop does NOT contain V31/MfaController, so on the current deployment GET /api/v1/me/security is not a 200
  - Live realm migrated (owner-gated): on the server `./migrate-mfa.sh status` prints flows=complete, browserFlow=matbakhy browser and client.matbakhy-admin=present (NOT true on the live realm today: migration has never been run there)
  - OIDC_ADMIN_CLIENT_SECRET is set in the stack's .env (`grep -c '^OIDC_ADMIN_CLIENT_SECRET=.' .env` prints 1; never print the value)
  - Network path from the server to https://api.<D> and https://id.<D> through the proxy
- **Steps:**
  1. (FX-ISOLATED) `./migrate-mfa.sh verify 2>&1 | tee verify.log; echo exit=$?` — data: Creates a synthetic user verify-mfa-<random>, signs in with real flows, calls the real API
  2. (FX-ISOLATED) `grep -E 'PASS|FAIL|SKIP|RESULT' verify.log` — data: Record which checks are SKIP (mobile until VERIFY_MOBILE=1)
  3. (FX-ISOLATED) Mutation probe on the isolated realm only: delete the `amr` mapper from the matbakhy-web client, rerun verify, then rerun `./migrate-mfa.sh apply` to restore — data: Expected FAIL
  4. (FX-ISOLATED) `./migrate-mfa.sh status` and confirm no verify-mfa-* user remains (`./migrate-mfa.sh fixture-clean`)
- **Expected:**
  - UI: n/a
  - API: Step 1: final line `RESULT: PASS` and exit 0 only when: level-1 token has the API audience and no acr/amr MFA evidence; OTP step-up token has acr in the API's values and amr otp; the API accepts that token when the fixture enables two-step verification, rejects the level-1 token with STEP_UP_REQUIRED afterwards, accepts the elevated token; `RESULT: INCOMPLETE` exit 3 if the fixture could not be created (never reported as PASS)
  - API: Step 3: `RESULT: FAIL` exit 1 on the OTP step-up token (amr missing)
  - DB: Read-only: no verify-mfa-* account remains in Keycloak; the application database holds one inactive synthetic customer record created by the fixture's first API call (document it)
- **Also covered by automated tests:** deploy/web-preview/identity/verify-mfa-flow.py; scripts/test_keycloak_mfa_migration.py; MfaIdpTest
- **Unverified against code (confirm while executing):** The real-API acceptance half has only been rehearsed against a stand-in API (docs/operations/13 rehearsal evidence); the first true run is this case

### CFG-031 — Rollback restores the previous browser flow even with unusual flow names; refuses an unreadable state file; PURGE removes admin client and mappers

- **Status:** NOT_RUN · **Priority:** P1 · **Readiness:** Needs the updated application deployment
- **Surface:** identity, deployment · **Actors:** FX-ISOLATED · **Needs:** updated deploy, isolated stack · **Destructive**
- **Traces to:** rules: BR-060; decisions: D18; operations: ./migrate-mfa.sh rollback; partial: TC-060
- **Prerequisites:**
  - Deployed release recorded: on the server, in the deploy/web-preview directory, run `./deploy.sh status` and note RELEASE_COMMIT, both image digests and the schema version
  - Operator shell on the server in the deploy/web-preview directory (CD uses /srv/matbakhy-staging/deploy/web-preview; docs/operations/06 mentions /srv/matbakhy-preview/src/deploy/web-preview; use whichever `./deploy.sh status` works in)
  - FX-ISOLATED exists: a disposable compose stack with its own PREVIEW_DOMAIN and fresh volumes built from the PR #9 head images, realm imported, `./deploy.sh up` healthy, `./deploy.sh seed` run, and `./migrate-mfa.sh status` run on it prints flows=complete
  - State file mfa-migration-state.env exists from an apply
- **Steps:**
  1. (FX-ISOLATED) `./migrate-mfa.sh rollback` — data: Default rollback
  2. (FX-ISOLATED) `./migrate-mfa.sh status`
  3. (FX-ISOLATED) Corrupt the state file copy (truncate it) and run `./migrate-mfa.sh rollback`; then `PREVIOUS_FLOW='browser' ./migrate-mfa.sh rollback` — data: Unreadable state must be refused; explicit PREVIOUS_FLOW accepted
  4. (FX-ISOLATED) Set the previous flow to a name with spaces/quotes/`$(touch /tmp/pwned)` in a throwaway realm copy and run rollback — data: The name is data, never sourced or evaluated: /tmp/pwned must not exist afterwards
  5. (FX-ISOLATED) `PURGE=1 ./migrate-mfa.sh rollback` then `status` — data: Admin client, amr mappers and acr.loa.map removed
- **Expected:**
  - UI: Sign-in still works for preview users after each rollback
  - API: After step 1: flows absent, browserFlow equals the baseline recorded before apply (browser)
  - API: Step 3: unreadable file refused with a non-zero exit and a message; explicit PREVIOUS_FLOW works
  - API: Step 4: no /tmp/pwned file
  - API: Step 5: client.matbakhy-admin=absent and no amr mapper; users and credentials untouched (count unchanged)
  - DB: none
- **Also covered by automated tests:** scripts/test_keycloak_mfa_migration.py; docs/operations/13 rehearsal (PURGE=1 rollback restores browserFlow=browser)
- **Unverified against code (confirm while executing):** Step 4 depends on a throwaway realm copy; the test_keycloak_mfa_migration.py cases may already cover the injection path

### CFG-032 — Interrupted migration leaves the old flow bound; rerun recovers; an incomplete bound flow is refused until rollback

- **Status:** NOT_RUN · **Priority:** P1 · **Readiness:** Needs the updated application deployment
- **Surface:** identity, deployment · **Actors:** FX-ISOLATED · **Needs:** updated deploy, isolated stack · **Destructive**
- **Traces to:** rules: BR-057, BR-060; decisions: D18; operations: ./migrate-mfa.sh apply; partial: TC-060, TC-057
- **Prerequisites:**
  - Deployed release recorded: on the server, in the deploy/web-preview directory, run `./deploy.sh status` and note RELEASE_COMMIT, both image digests and the schema version
  - Operator shell on the server in the deploy/web-preview directory (CD uses /srv/matbakhy-staging/deploy/web-preview; docs/operations/06 mentions /srv/matbakhy-preview/src/deploy/web-preview; use whichever `./deploy.sh status` works in)
  - FX-ISOLATED exists: a disposable compose stack with its own PREVIEW_DOMAIN and fresh volumes built from the PR #9 head images, realm imported, `./deploy.sh up` healthy, `./deploy.sh seed` run, and realm is still pre-D18 (`status` prints flows=absent)
- **Steps:**
  1. (FX-ISOLATED) Start `./migrate-mfa.sh apply`; when the log reaches the migration phase (after 'Keycloak database dump' and 'realm export') run `docker compose kill identity` from a second shell, wait for apply to fail — data: Interrupt before the binding
  2. (FX-ISOLATED) `docker compose start identity`; wait healthy; `./migrate-mfa.sh status` — data: Old flow must still be bound
  3. (FX-ISOLATED) Sign in as preview-customer-1 and preview-ops-1 (password only)
  4. (FX-ISOLATED) `./migrate-mfa.sh apply` — data: Next run removes the incomplete attempt and rebuilds
- **Expected:**
  - UI: Sign-in works after the interruption
  - API: Step 2: browserFlow=browser (previous), flows=absent or incomplete (not complete)
  - API: Step 4: ends complete with a RESULT line
  - API: User count unchanged; no credentials lost
  - DB: none
- **Also covered by automated tests:** scripts/test_keycloak_mfa_migration.py (incomplete-flow handling)
- **Unverified against code (confirm while executing):** Exact timing of the interruption window; kcadm starts a JVM per call so there are many seconds to hit

### CFG-033 — Default MFA state after deploy: all three categories OPTIONAL, nobody required, sign-in never asks OTP for customer, cook or staff

- **Status:** NOT_RUN · **Priority:** P0 · **Readiness:** Needs the updated application deployment
- **Surface:** api, identity, customer-web, operations-web · **Actors:** FX-STAFF-ADMIN-B, FX-CUST-A, FX-COOK-A, FX-STAFF-ADMIN · **Needs:** updated deploy
- **Fixtures to provision:** FX-STAFF-ADMIN-B
- **Traces to:** rules: BR-042; decisions: D18; operations: getOpsSecurityMfaPolicy, getMeSecurity
- **Prerequisites:**
  - Deployed release recorded: on the server, in the deploy/web-preview directory, run `./deploy.sh status` and note RELEASE_COMMIT, both image digests and the schema version
  - Deployed API contains PR #9 (D18): `./deploy.sh status` shows schema version >= 31 and RELEASE_COMMIT is the PR #9 head or later; origin/develop does NOT contain V31/MfaController, so on the current deployment GET /api/v1/me/security is not a 200
  - Tester's source IP is inside CUSTOMER_ALLOWED_IPS (and STAFF_ALLOWED_IPS for ops.<PREVIEW_DOMAIN> and /api/v1/ops, /api/v1/cook): `curl -s -o /dev/null -w '%{http_code}' https://app.<PREVIEW_DOMAIN>/` returns 200 (a 403 body 'Not available' means the IP is not allow-listed; the preview is deny-by-default, not public, unless the owner set CUSTOMER_ALLOWED_IPS=0.0.0.0/0)
  - FX-STAFF-ADMIN-B holds active grants security.mfa_policy and security.mfa_reset and has an enrolled authenticator (TOTP secret kept in the private fixture sheet). Provision per docs/quality/owner-acceptance/fixtures.md (the bootstrap administrator preview-ops-1 holds only staff.grant and audit.read, so these permissions are absent by default)
- **Steps:**
  1. (FX-STAFF-ADMIN-B) GET /api/v1/ops/security/mfa-policy (needs security.mfa_policy; password-only is enough for GET) — data: Three rows
  2. (FX-CUST-A) Sign in password-only at app.<D>; GET /api/v1/me/security
  3. (FX-COOK-A) Sign in password-only at ops.<D> as preview-cook-1; GET /api/v1/me/security
  4. (FX-STAFF-ADMIN) Sign in password-only at ops.<D> as preview-ops-1; GET /api/v1/me/security
- **Expected:**
  - UI: No OTP page in any of the three sign-ins
  - API: Policy: CUSTOMER, KITCHEN, STAFF each mode OPTIONAL (version 0 on a fresh deployment)
  - API: Each /me/security: effectiveMode OPTIONAL, enabled false, required false, available true; cook categories [CUSTOMER,KITCHEN]; ops-1 [CUSTOMER,STAFF]
  - DB: Read-only: `SELECT category, mode, version FROM mfa_policy` returns three OPTIONAL rows; no audit mfa.policy.update rows on a fresh deploy
- **Also covered by automated tests:** MfaPolicyIntegrationTest.the_migration_seeds_optional_for_every_category_and_the_live_state_matches_it; MfaPolicyIntegrationTest.the_preview_defaults_are_optional_for_every_category_and_nobody_is_forced
- **Unverified against code (confirm while executing):** The live preview's mfa_policy may differ if anyone changed it; the test records actual values

### CFG-034 — Network classes: customer hosts follow CUSTOMER_ALLOWED_IPS, ops host and /api/v1/ops, /api/v1/cook follow STAFF_ALLOWED_IPS; unauthenticated staff API from an allowed IP is 401/403

- **Status:** NOT_RUN · **Priority:** P0 · **Readiness:** Ready to run manually now
- **Surface:** deployment, api, operations-web · **Actors:** FX-GUEST
- **Traces to:** rules: BR-001, BR-060; operations: deploy.sh smoke (SMOKE_OPS=skip), deploy/web-preview/Caddyfile staff_access / customer_access; partial: TC-001, TC-140
- **Prerequisites:**
  - Deployed release recorded: on the server, in the deploy/web-preview directory, run `./deploy.sh status` and note RELEASE_COMMIT, both image digests and the schema version
  - Know the two lists: on the server `grep -E '^(CUSTOMER|STAFF)_ALLOWED_IPS|^ALLOWED_CLIENT_IPS' .env` (values are addresses, not secrets; do not paste into the repository)
  - Three vantage points: A = source IP in BOTH CUSTOMER_ALLOWED_IPS and STAFF_ALLOWED_IPS; B = IP in the customer list only; C = IP in neither list. A staff-only IP would get proxy 403 on customer hosts and the backend 401 would be unreachable, so do not use one
- **Steps:**
  1. (FX-GUEST) From B: `curl -s -w ' %{http_code}\n' https://ops.<PREVIEW_DOMAIN>/` and `https://api.<PREVIEW_DOMAIN>/api/v1/ops/staff` and `https://api.<PREVIEW_DOMAIN>/api/v1/cook/dashboard` — data: Staff-class paths from a customer-only IP
  2. (FX-GUEST) From B: `curl -s -o /dev/null -w '%{http_code}\n' https://app.<PREVIEW_DOMAIN>/` and `https://api.<PREVIEW_DOMAIN>/api/v1/meals` — data: Customer-class paths
  3. (FX-GUEST) From A: `curl -s -o /dev/null -w '%{http_code}\n' https://ops.<PREVIEW_DOMAIN>/` and `curl -s -w ' %{http_code}\n' https://api.<PREVIEW_DOMAIN>/api/v1/ops/staff` with no session — data: Reaches the API
  4. (FX-GUEST) From C: `curl -s -w ' %{http_code}\n'` against https://app.<PREVIEW_DOMAIN>/, https://id.<PREVIEW_DOMAIN>/realms/matbakhy/.well-known/openid-configuration, https://media.<PREVIEW_DOMAIN>/ and https://api.<PREVIEW_DOMAIN>/api/v1/meals — data: All customer-class hosts
  5. (FX-GUEST) `curl -s -w ' %{http_code}\n' https://api.<PREVIEW_DOMAIN>/nonexistent` and `https://api.<PREVIEW_DOMAIN>/actuator/health` from B (customer-allowed) and again from C (customer-disallowed) — data: Unrouted paths on api.
- **Expected:**
  - UI: n/a (HTTP)
  - API: Step 1: body 'Not available' with status 403 for all three (Caddy staff_access), whatever credentials are sent
  - API: Step 2: 200 from app. and /api/v1/meals
  - API: Step 3: ops. serves HTML 200; /api/v1/ops/staff answers 401 (no session), not 200
  - API: Step 4: 403 'Not available' on every customer-class host
  - API: Step 5: from B both paths return 404; from C both return 403 'Not available' (customer_access runs before the 404 fallthrough, Caddyfile api. block)
  - DB: none
- **Also covered by automated tests:** scripts/test_proxy_access.py (runs the Caddyfile); deploy/web-preview/smoke.sh
- **Unverified against code (confirm while executing):** Original CFG-034 expected ops 'HTML accessible' to anyone; the Caddyfile makes ops. staff-class only, so that expectation is false for non-staff IPs; If the owner has set CUSTOMER_ALLOWED_IPS=0.0.0.0/0 the customer-class steps from any IP are 200; record the active value

### CFG-036 — CORS and CSRF for the web session API: only app./ops. origins are allowed with credentials; unsafe requests without a valid XSRF token are refused

- **Status:** NOT_RUN · **Priority:** P0 · **Readiness:** Ready to run manually now
- **Surface:** api, customer-web · **Actors:** FX-CUST-A, FX-GUEST
- **Traces to:** rules: BR-002; operations: patchMe, SecurityConfiguration corsConfigurationSource; partial: TC-002
- **Prerequisites:**
  - Deployed release recorded: on the server, in the deploy/web-preview directory, run `./deploy.sh status` and note RELEASE_COMMIT, both image digests and the schema version
  - Tester's source IP is inside CUSTOMER_ALLOWED_IPS (and STAFF_ALLOWED_IPS for ops.<PREVIEW_DOMAIN> and /api/v1/ops, /api/v1/cook): `curl -s -o /dev/null -w '%{http_code}' https://app.<PREVIEW_DOMAIN>/` returns 200 (a 403 body 'Not available' means the IP is not allow-listed; the preview is deny-by-default, not public, unless the owner set CUSTOMER_ALLOWED_IPS=0.0.0.0/0)
- **Steps:**
  1. (FX-GUEST) curl -s -i -X OPTIONS -H 'Origin: https://evil.example' -H 'Access-Control-Request-Method: POST' -H 'Access-Control-Request-Headers: content-type,x-xsrf-token' https://api.<PREVIEW_DOMAIN>/api/v1/orders — data: Preflight from a foreign origin
  2. (FX-GUEST) Same preflight with Origin: https://app.<PREVIEW_DOMAIN> — data: Allowed origin
  3. (FX-CUST-A) Signed in at app.<D>: in the console `fetch('https://api.<PREVIEW_DOMAIN>/api/v1/me',{method:'PATCH',credentials:'include',headers:{'Content-Type':'application/json'},body:JSON.stringify({displayName:'E2E',expectedVersion:0})})` (no X-XSRF-TOKEN header) — data: Session cookie present, no CSRF token
  4. (FX-CUST-A) Repeat with header X-XSRF-TOKEN set to 'tampered'
  5. (FX-CUST-A) Repeat with the real token from document.cookie XSRF-TOKEN
  6. (FX-GUEST) From a shell: `curl -s -i -H 'Origin: http://localhost:5173' --cookie 'MATBAKHY_SESSION=<value copied from DevTools of your OWN test session>' https://api.<D>/api/v1/me` — data: Foreign origin: response must carry no Access-Control-Allow-Origin (a browser would then block the page from reading it). Do not paste the cookie value anywhere permanent
- **Expected:**
  - UI: n/a
  - API: Step 1: no Access-Control-Allow-Origin for evil.example (403 'Invalid CORS request')
  - API: Step 2: Access-Control-Allow-Origin: https://app.<D>, Access-Control-Allow-Credentials: true, methods GET,POST,PUT,PATCH,DELETE, headers Content-Type, X-XSRF-TOKEN, Idempotency-Key, If-Match, X-Request-Id, Authorization, Max-Age 600
  - API: Steps 3 and 4: 403 (CSRF); no change to the profile
  - API: Step 5: 200 or a business validation answer, not a CSRF refusal
  - API: Step 6: response lacks Access-Control-Allow-Origin for the foreign origin
  - DB: Read-only: profile display name unchanged after steps 3-4
- **Also covered by automated tests:** CsrfCookieDefaultIntegrationTest; CsrfCookieDomainIntegrationTest; PreviewSecurityConfigurationTest
- **Unverified against code (confirm while executing):** The bearer chain disables CSRF by design (no ambient credentials): a Bearer-only request without a cookie needs no token; this is correct and not a defect; Exact status/body of a CSRF refusal not confirmed (403 expected)

### CFG-037 — Cookie attributes: session cookie is Secure, HttpOnly, SameSite=Lax; XSRF-TOKEN is readable and scoped to the parent domain

- **Status:** NOT_RUN · **Priority:** P1 · **Readiness:** Ready to run manually now
- **Surface:** api, customer-web · **Actors:** FX-CUST-A
- **Traces to:** rules: BR-002; operations: SecurityConfiguration sessionCookie / csrfTokenRepository; partial: TC-002
- **Prerequisites:**
  - Deployed release recorded: on the server, in the deploy/web-preview directory, run `./deploy.sh status` and note RELEASE_COMMIT, both image digests and the schema version
  - Tester's source IP is inside CUSTOMER_ALLOWED_IPS (and STAFF_ALLOWED_IPS for ops.<PREVIEW_DOMAIN> and /api/v1/ops, /api/v1/cook): `curl -s -o /dev/null -w '%{http_code}' https://app.<PREVIEW_DOMAIN>/` returns 200 (a 403 body 'Not available' means the IP is not allow-listed; the preview is deny-by-default, not public, unless the owner set CUSTOMER_ALLOWED_IPS=0.0.0.0/0)
- **Steps:**
  1. (FX-CUST-A) Sign in at https://app.<PREVIEW_DOMAIN>/, then DevTools > Application > Cookies > https://api.<PREVIEW_DOMAIN> and https://app.<PREVIEW_DOMAIN> — data: Record every cookie name and attributes
  2. (FX-CUST-A) `curl -s -D - -o /dev/null https://api.<PREVIEW_DOMAIN>/api/v1/me | grep -i set-cookie` (unauthenticated) — data: Header-level check
  3. (FX-CUST-A) Sign out and re-inspect
- **Expected:**
  - UI: n/a
  - API: MATBAKHY_SESSION: Secure, HttpOnly, SameSite=Lax, Path=/ (deployed profile sets useSecureCookie)
  - API: XSRF-TOKEN: not HttpOnly (the web app copies it into X-XSRF-TOKEN), Domain=<PREVIEW_DOMAIN> so app./ops./api. share it, Secure over HTTPS
  - API: After logout MATBAKHY_SESSION is expired
  - DB: none
- **Also covered by automated tests:** CsrfCookieDomainIntegrationTest; CsrfCookieDefaultIntegrationTest
- **Unverified against code (confirm while executing):** Whether the XSRF-TOKEN cookie carries Secure and SameSite on the deployed build was not read (CookieCsrfTokenRepository defaults); Keycloak's own cookies (KEYCLOAK_*, AUTH_SESSION_ID) on id.<D> not enumerated

### CFG-038 — Content-Security-Policy is Report-Only (not enforced) on app. and ops.; baseline header set on every host

- **Status:** NOT_RUN · **Priority:** P1 · **Readiness:** Ready to run manually now
- **Surface:** deployment, customer-web, operations-web · **Actors:** FX-GUEST, FX-CUST-A, FX-STAFF-ADMIN
- **Traces to:** rules: BR-053; operations: docs/security/csp-rollout.md, deploy/web-preview/Caddyfile (web_csp, headers); partial: TC-053, TC-140
- **Prerequisites:**
  - Deployed release recorded: on the server, in the deploy/web-preview directory, run `./deploy.sh status` and note RELEASE_COMMIT, both image digests and the schema version
  - Tester's source IP is inside CUSTOMER_ALLOWED_IPS (and STAFF_ALLOWED_IPS for ops.<PREVIEW_DOMAIN> and /api/v1/ops, /api/v1/cook): `curl -s -o /dev/null -w '%{http_code}' https://app.<PREVIEW_DOMAIN>/` returns 200 (a 403 body 'Not available' means the IP is not allow-listed; the preview is deny-by-default, not public, unless the owner set CUSTOMER_ALLOWED_IPS=0.0.0.0/0)
  - Chrome or Edge desktop with DevTools
- **Steps:**
  1. (FX-GUEST) `for h in app ops api id media; do echo == $h; curl -sI https://$h.<PREVIEW_DOMAIN>/ | grep -i -E 'strict-transport|x-content-type|referrer-policy|x-robots|x-frame|content-security|server'; done`
  2. (FX-CUST-A) Open https://app.<PREVIEW_DOMAIN>/ with DevTools Console open, run: sign in, browse meals, basket, quote, checkout up to the order screen; copy every `[Report Only]` console message — data: Violations are logged, not blocked
  3. (FX-STAFF-ADMIN) Open https://ops.<PREVIEW_DOMAIN>/ as preview-ops-1 and visit Staff, Security, Configuration, Finance tabs; copy `[Report Only]` messages
  4. (FX-GUEST) In the Console of app.<D> run `document.body.appendChild(Object.assign(document.createElement('script'),{textContent:'window.__csp=1'})); window.__csp` — data: Inline script injection probe
- **Expected:**
  - UI: No page is broken; any violation appears only as a console report
  - API: app./ops. carry `Content-Security-Policy-Report-Only` (default-src 'self'; script-src 'self'; style-src 'self'; style-src-attr 'unsafe-inline'; img-src 'self' data: https://media.<D>; connect-src 'self' https://api.<D>; frame-ancestors 'none') and NO `Content-Security-Policy` header
  - API: Step 4 returns 1: inline script runs, which proves CSP is NOT enforced; record exactly this and do not claim enforcement (SEC-004 open)
  - API: All hosts: Strict-Transport-Security max-age=31536000 (no includeSubDomains), X-Content-Type-Options: nosniff, Referrer-Policy: strict-origin-when-cross-origin, X-Robots-Tag: noindex, nofollow, no Server header; X-Frame-Options: DENY only on app. and ops.
  - DB: none
- **Also covered by automated tests:** scripts/test_proxy_access.py; deploy/web-preview/smoke.sh (HSTS, nosniff, frame-deny)
- **Unverified against code (confirm while executing):** This case is evidence gathering for CSP promotion; Step 2/3 violation lists are inputs for the follow-up task, not pass criteria; Promotion to enforced CSP (rename header, prove injected inline script blocked) is a different, later, owner-visible change and is not part of this case

### CFG-039 — Brute-force and rate limiting: Keycloak lockout is the only login throttle; application-level rate limits for login/quote/order/search are not implemented

- **Status:** NOT_RUN · **Priority:** P2 · **Readiness:** Isolated disposable stack only
- **Surface:** identity, api · **Actors:** FX-CUST-NEW · **Needs:** isolated stack · **Destructive**
- **Traces to:** rules: BR-054; operations: postMePhoneChallenges; partial: TC-054
- **Prerequisites:**
  - Deployed release recorded: on the server, in the deploy/web-preview directory, run `./deploy.sh status` and note RELEASE_COMMIT, both image digests and the schema version
  - FX-ISOLATED exists: a disposable compose stack with its own PREVIEW_DOMAIN and fresh volumes built from the PR #9 head images, realm imported, `./deploy.sh up` healthy, `./deploy.sh seed` run, and `./migrate-mfa.sh status` run on it prints flows=complete
  - A disposable throwaway Keycloak user FX-CUST-NEW on the isolated realm (never preview-customer-*)
- **Steps:**
  1. (FX-CUST-NEW) Submit a wrong password on the Keycloak login form repeatedly (record count until the lockout message; realm has bruteForceProtected=true with default thresholds)
  2. (FX-CUST-NEW) Then log in with the right password during lockout and after waiting out the lockout — data: Legitimate user recovery
  3. (FX-CUST-NEW) Send 60 sequential `curl -s -o /dev/null -w '%{http_code}\n' https://api.<isolated>/api/v1/meals` and 60 POST quote calls from one session; count non-200 results — data: Bounded, isolated stack only
  4. (FX-CUST-NEW) Phone verification: request more than 3 codes within an hour or within the resend interval (/api/v1/me/phone/challenges) — data: The implemented limiter (PhoneVerificationService)
- **Expected:**
  - UI: Lockout message shows after N failures
  - API: Step 1: temporary lockout appears; the user is not enumerated (generic message)
  - API: Step 3: if all 60 return 200, record 'no application-level limit on this path' (BR-054 not met for quote/search) as a finding rather than a pass
  - API: Step 4: 429 RATE_LIMITED 'resend interval' / 'hourly send limit'
  - DB: Read-only: none
- **Also covered by automated tests:** ContactIntegrationTest (phone limiter)
- **Unverified against code (confirm while executing):** grep found RATE_LIMITED only in PhoneVerificationService, ReviewService and OrderLifecycleService; no generic login/OTP/quote/upload/search limiter was located, so TC-054/BR-054 likely cannot pass as written; Keycloak lockout thresholds are defaults, not configured in the realm file

### CFG-040 — No secrets, tokens or OTP material in the repository history, API logs or deployment logs

- **Status:** NOT_RUN · **Priority:** P0 · **Readiness:** Needs the updated application deployment
- **Surface:** deployment, api · **Actors:** FX-ISOLATED · **Needs:** updated deploy
- **Traces to:** rules: BR-056, BR-060; operations: scripts/check_secrets.sh; partial: TC-056, TC-060
- **Prerequisites:**
  - Deployed release recorded: on the server, in the deploy/web-preview directory, run `./deploy.sh status` and note RELEASE_COMMIT, both image digests and the schema version
  - Repository checkout at the release commit with full history
  - Isolated or preview log access (docker compose logs on the server)
- **Steps:**
  1. (FX-ISOLATED) `scripts/check_secrets.sh` (the same gitleaks run as the CI job) from the repository root — data: History and tree
  2. (FX-ISOLATED) After running CFG-002, CFG-008, CFG-019 on the same stack: `docker compose logs --since 2h api identity proxy | grep -i -E 'secret|password|otp|totp|bearer|access_token|refresh_token|client_secret' | grep -v -i -E 'redacted|not configured'` — data: Review each hit; none may show a value
  3. (FX-ISOLATED) `grep -rn -i -E 'client.secret|PREVIEW_USER_PASSWORD' deploy/web-preview/compose.yaml deploy/web-preview/.env.example` and `git check-ignore -v deploy/web-preview/.env` — data: Only variable references; .env is ignored
  4. (FX-ISOLATED) `stat -c '%a %n' deploy/web-preview/.env $BACKUP_DIR/keycloak/*` — data: File modes
- **Expected:**
  - UI: n/a
  - API: Step 1 exit 0 (no findings)
  - API: Step 2: zero lines containing an actual secret/OTP/token value; request IDs only
  - API: Step 4: .env mode 600; Keycloak backups mode 600
  - DB: Read-only: audit_event details for mfa.* rows contain only category/mode/removedFactors/sessionsEnded, never a credential
- **Also covered by automated tests:** CI job 'Secret scan' (.github/workflows/ci.yml, scripts/check_secrets.sh); observability tests for log redaction
- **Unverified against code (confirm while executing):** Any observed value must be rotated and recorded as a defect without copying the value

### CFG-041 — Develop CD: push to develop runs build, digest-pinned deploy, health and smoke; record the deployed SHA and digests

- **Status:** NOT_RUN · **Priority:** P1 · **Readiness:** Ready to run manually now
- **Surface:** deployment · **Actors:** FX-ISOLATED · **Destructive**
- **Traces to:** rules: BR-060, BR-057; operations: gh workflow deploy-preview.yml, ./deploy.sh up, ./deploy.sh smoke; partial: TC-060, TC-140, TC-057
- **Prerequisites:**
  - Owner authorizes the merge/push to develop and a deploy window; this case would change the shared preview, so do NOT trigger it as a test: observe an already-planned deploy, or run on a fork/isolated runner
  - GitHub CLI authenticated: `gh run list --workflow deploy-preview.yml --limit 3`
  - Repository secrets for the `preview` environment exist (not inspectable by testers)
- **Steps:**
  1. (FX-ISOLATED) After the push: `gh run watch <run-id>` for workflow 'Deploy Matbakhy preview to Contabo' (.github/workflows/deploy-preview.yml, triggers: push to develop, workflow_dispatch)
  2. (FX-ISOLATED) Read the build job: 'Wait for passing Product CI at this exact commit' must succeed before images are built — data: Gate on CI
  3. (FX-ISOLATED) On the server: `./deploy.sh status` and compare RELEASE_COMMIT, API_IMAGE, FRONT_IMAGE with the workflow output; `./deploy.sh smoke`
  4. (FX-ISOLATED) On the server: `tail -n 3 $BACKUP_DIR/releases.log` — data: Release history line
- **Expected:**
  - UI: After the deploy `https://app.<D>/` serves the new build
  - API: Workflow concludes success; remote script prints `DEPLOYED <sha>`
  - API: status: RELEASE_COMMIT equals the pushed SHA; images are name@sha256:<digest> (deploy.sh refuses tags, short SHAs, placeholders)
  - API: smoke ends with `PASS: web preview smoke test for <D>`
  - API: releases.log has a new line: time, commit, both digests, schema before -> after, backup path
  - DB: Read-only: schema version in status increases only when the release has migrations
- **Also covered by automated tests:** scripts/test_preview_release.py; scripts/test_staging_profile.py
- **Unverified against code (confirm while executing):** CD deploys to /srv/matbakhy-staging while docs/operations/06 and 12 refer to /srv/matbakhy-preview: confirm which directory the live stack runs from; Workflow name says Contabo while docs/operations/11 describes Hetzner: the target host is unclear; D-time: develop currently has no V31 or MFA code, so the first CD after PR #9 merges changes the schema to 31

### CFG-042 — Failed deploy handling: what the CD failure path really does and what the operator must do (backup preserved, no automatic DB rollback)

- **Status:** NOT_RUN · **Priority:** P1 · **Readiness:** Isolated disposable stack only
- **Surface:** deployment · **Actors:** FX-ISOLATED · **Needs:** isolated stack · **Destructive**
- **Traces to:** rules: BR-060, BR-057; operations: ./deploy.sh up, ./deploy.sh rollback-plan; partial: TC-060, TC-057
- **Prerequisites:**
  - Deployed release recorded: on the server, in the deploy/web-preview directory, run `./deploy.sh status` and note RELEASE_COMMIT, both image digests and the schema version
  - Operator shell on the server in the deploy/web-preview directory (CD uses /srv/matbakhy-staging/deploy/web-preview; docs/operations/06 mentions /srv/matbakhy-preview/src/deploy/web-preview; use whichever `./deploy.sh status` works in)
  - FX-ISOLATED exists: a disposable compose stack with its own PREVIEW_DOMAIN and fresh volumes built from the PR #9 head images, realm imported, `./deploy.sh up` healthy, `./deploy.sh seed` run, and `./migrate-mfa.sh status` run on it prints flows=complete
  - A deliberately bad image reference ready: valid digest of a container that exits (for example a digest of an unrelated small image) put in a copy of .env as API_IMAGE
- **Steps:**
  1. (FX-ISOLATED) `cp .env .env.good`; set API_IMAGE to the bad digest; `./deploy.sh up; echo exit=$?` — data: Health gate
  2. (FX-ISOLATED) `./deploy.sh status` and `docker compose ps` — data: Which images run vs configured
  3. (FX-ISOLATED) Reproduce the CD failure handler: `cp .env.good .env` (what deploy-preview.yml does on failure) and run `./deploy.sh status` again WITHOUT `up` — data: The workflow restores .env but does not restart containers
  4. (FX-ISOLATED) `./deploy.sh rollback-plan` then `./deploy.sh up` with the good .env; `./deploy.sh smoke`
  5. (FX-ISOLATED) `ls -d /backups/db-*` inside the postgres container or `$BACKUP_DIR/db-*` — data: Pre-deploy backup preserved
- **Expected:**
  - UI: n/a
  - API: Step 1: non-zero exit with a health failure message; pre-deploy backup 'backup verified' printed before containers changed
  - API: Step 3: status shows configured (good) != running (bad) until `up` is run: record this as a documented gap of the CD failure path (restores .env only, 'no automatic DB rollback')
  - API: Step 4: stack healthy and smoke PASS; data row counts unchanged
  - API: Step 5: backup directory with .dump + checksum exists
  - DB: Read-only: `SELECT count(*) FROM customer_order` equal before/after; schema version unchanged by the failed deploy (if the bad image never ran migrations)
- **Also covered by automated tests:** scripts/test_preview_release.py; scripts/test_preview_backup.py
- **Unverified against code (confirm while executing):** Whether the failure path leaves the new containers running was derived from deploy-preview.yml (cp "$old" .env; exit 1) and should be confirmed by this drill

### CFG-043 — Backup is accepted only after restore + reconcile: deploy.sh backup and restore-db on an isolated database

- **Status:** NOT_RUN · **Priority:** P1 · **Readiness:** Isolated disposable stack only
- **Surface:** deployment · **Actors:** FX-ISOLATED · **Needs:** isolated stack
- **Traces to:** rules: BR-057; operations: ./deploy.sh backup, ./deploy.sh restore-db; partial: TC-057
- **Prerequisites:**
  - Deployed release recorded: on the server, in the deploy/web-preview directory, run `./deploy.sh status` and note RELEASE_COMMIT, both image digests and the schema version
  - Operator shell on the server in the deploy/web-preview directory (CD uses /srv/matbakhy-staging/deploy/web-preview; docs/operations/06 mentions /srv/matbakhy-preview/src/deploy/web-preview; use whichever `./deploy.sh status` works in)
  - FX-ISOLATED exists: a disposable compose stack with its own PREVIEW_DOMAIN and fresh volumes built from the PR #9 head images, realm imported, `./deploy.sh up` healthy, `./deploy.sh seed` run, and `./migrate-mfa.sh status` run on it prints flows=complete
  - Isolated database contains at least the seed data and one order
- **Steps:**
  1. (FX-ISOLATED) `./deploy.sh backup 2>&1 | tee backup.log` — data: Writes db dump + checksum + manifest, restores into matbakhy_restore_check, reconciles, then archives storage
  2. (FX-ISOLATED) `grep -E 'backup verified|backups in' backup.log; cat $BACKUP_DIR/metrics/matbakhy_backup.prom`
  3. (FX-ISOLATED) `./deploy.sh restore-db /backups/db-<stamp>/matbakhy-<stamp>.dump` — data: Use the dump named in backup.log; restores into matbakhy_restore
  4. (FX-ISOLATED) `docker compose exec -T postgres psql -U matbakhy -d matbakhy_restore -c 'SELECT count(*) FROM customer_order'` vs the same on matbakhy — data: Counts equal
  5. (FX-ISOLATED) Corrupt a copy of the dump (truncate 1 KB) and run `restore-db` with it — data: Must fail, metric must not advance
- **Expected:**
  - UI: n/a
  - API: 'backup verified: <dump> restored and reconciled'; the prom file timestamp advances only on success
  - API: restore-db leaves the live matbakhy database untouched; customer_order, allocations and ledger reconcile
  - API: Corrupt dump: non-zero exit, no metric update
  - DB: Read-only: row counts equal; reconcile query (infra/db/reconcile.sql) reports no imbalance
- **Also covered by automated tests:** scripts/test_preview_backup.py; scripts/db-restore.sh
- **Unverified against code (confirm while executing):** Measured RPO/RTO must be written down by the operator; no target is defined in the repository

### CFG-044 — Server reboot drill: services return in dependency order and the stack passes smoke (operator, change window)

- **Status:** BLOCKED — Needs an owner-approved change window on the real host; do not reboot the shared preview as a test · **Priority:** P2 · **Readiness:** Ready to run manually now
- **Surface:** deployment · **Actors:** FX-ISOLATED · **Destructive**
- **Traces to:** rules: BR-057; operations: ./deploy.sh status, ./deploy.sh smoke; partial: TC-057, TC-140
- **Prerequisites:**
  - Deployed release recorded: on the server, in the deploy/web-preview directory, run `./deploy.sh status` and note RELEASE_COMMIT, both image digests and the schema version
  - Operator shell on the server in the deploy/web-preview directory (CD uses /srv/matbakhy-staging/deploy/web-preview; docs/operations/06 mentions /srv/matbakhy-preview/src/deploy/web-preview; use whichever `./deploy.sh status` works in)
  - Owner-approved change window; a verified backup taken just before (CFG-043)
  - No tester session or interrupted e2e-reset in progress
- **Steps:**
  1. (FX-ISOLATED) `./deploy.sh status > before.txt`; then `sudo reboot` on the server
  2. (FX-ISOLATED) After SSH returns: `docker compose ps` repeatedly until all services are healthy; note the time taken — data: compose restart policies
  3. (FX-ISOLATED) `./deploy.sh status > after.txt; diff before.txt after.txt; ./deploy.sh smoke`
  4. (FX-ISOLATED) Sign in and run CFG-002
- **Expected:**
  - UI: Sign-in works after reboot
  - API: All containers healthy within the documented ~133 s cold start (4 GB profile rehearsal) or the observed time recorded
  - API: status identical (same commit, images); smoke PASS; no data loss (order count unchanged)
  - DB: Read-only: order count unchanged
- **Unverified against code (confirm while executing):** The original text 'interrupted agent tasks resumed only from evidence' is about development agents, not the product, and is dropped; Rehearsal of reboot on the real host does not exist (docs/operations/11 section 6: SIGKILL of API only)

### CFG-045 — Synthetic money guarantee: demo deposit and COD orders produce no outbound provider traffic and no real transfer or message

- **Status:** NOT_RUN · **Priority:** P0 · **Readiness:** Ready to run manually now
- **Surface:** customer-web, api, deployment · **Actors:** FX-CUST-A, FX-COOK-A
- **Traces to:** rules: BR-060; operations: deploy/web-preview/smoke.sh (config reports PREVIEW demo true); partial: TC-060, TC-140
- **Prerequisites:**
  - Deployed release recorded: on the server, in the deploy/web-preview directory, run `./deploy.sh status` and note RELEASE_COMMIT, both image digests and the schema version
  - Tester's source IP is inside CUSTOMER_ALLOWED_IPS (and STAFF_ALLOWED_IPS for ops.<PREVIEW_DOMAIN> and /api/v1/ops, /api/v1/cook): `curl -s -o /dev/null -w '%{http_code}' https://app.<PREVIEW_DOMAIN>/` returns 200 (a 403 body 'Not available' means the IP is not allow-listed; the preview is deny-by-default, not public, unless the owner set CUSTOMER_ALLOWED_IPS=0.0.0.0/0)
  - `curl -s https://api.<D>/api/v1/config` (or the public configuration endpoint used by smoke.sh) reports PREVIEW and demo true
  - Server operator can list outbound connections of the api container
- **Steps:**
  1. (FX-CUST-A) Place a deposit order and a COD order through the UI (synthetic basket); report the transfer in the app
  2. (FX-COOK-A) Acknowledge receipt in the kitchen workspace
  3. (FX-CUST-A) Inspect all text on the deposit screens — data: No claim of bank verification
  4. (FX-CUST-A) Operator during the test: `docker compose logs api --since 10m | grep -i -E 'sms|smtp|push|payment provider|http client'`; if the host has egress counters or a firewall log, list new outbound destinations during the window — data: Look for any outbound call to a payment, SMS, mail or push provider
- **Expected:**
  - UI: Deposit recipient shown is the clearly fake demo recipient; demo notice visible in Arabic and English; no text claims bank verification
  - API: Config endpoint reports demo true and no provider is configured
  - API: Order and ledger rows exist; no external provider identifier in payment event rows
  - DB: Read-only: payment event rows carry synthetic markers only (no provider reference); notification outbox, if present, has no delivered-to-provider status
- **Also covered by automated tests:** apps/operations-web/e2e-staging (deposit simulated order)
- **Unverified against code (confirm while executing):** Config endpoint path was not looked up; use the one smoke.sh calls; No egress-capture tooling is defined; Step 4 evidence is best-effort, so the case may stay partially BLOCKED

### CFG-046 — Physical iPhone build identity and baseline: install from TestFlight, record build/version, reach the API, sign in and out (device plan A1, A2, B1, B4, B5, D2)

- **Status:** NOT_RUN · **Priority:** P0 · **Readiness:** Needs a physical iOS/Android build
- **Surface:** customer-mobile, deployment · **Actors:** FX-DEVICE-IOS, FX-CUST-A, FX-CUST-B · **Needs:** updated deploy, physical device
- **Traces to:** rules: BR-048, BR-052; operations: getMe, GET /api/v1/meals; partial: TC-048, TC-052, TC-145
- **Prerequisites:**
  - Deployed release recorded: on the server, in the deploy/web-preview directory, run `./deploy.sh status` and note RELEASE_COMMIT, both image digests and the schema version
  - Deployed API contains PR #9 (D18): `./deploy.sh status` shows schema version >= 31 and RELEASE_COMMIT is the PR #9 head or later; origin/develop does NOT contain V31/MfaController, so on the current deployment GET /api/v1/me/security is not a 200
  - Live realm migrated (owner-gated): on the server `./migrate-mfa.sh status` prints flows=complete, browserFlow=matbakhy browser and client.matbakhy-admin=present (NOT true on the live realm today: migration has never been run there)
  - TestFlight/Preview build installed on FX-DEVICE-IOS (physical iPhone) and the mobile OIDC client registered (docs/mobile/ios-testflight-beta.md step 'register the mobile client'; `VERIFY_MOBILE=1 ./migrate-mfa.sh verify` no longer SKIPs mobile checks). These documents (ios-beta-device-test-plan.md, ios-testflight-beta.md) exist on branch beta/ios-testflight-prep, NOT on PR #9 or develop
  - Preview allow-list decision made so the phone's network is inside CUSTOMER_ALLOWED_IPS (device test plan expects no 403 'Not available')
  - Customer web (app.<D>) and the phone use the same synthetic customer FX-CUST-A, preference currently off
- **Steps:**
  1. (FX-DEVICE-IOS) A1: install from TestFlight; open Settings > Matbakhy Beta and record version and build number — data: Bundle com.matbakhy.customer.beta
  2. (FX-DEVICE-IOS) A2: Discover on cellular and then Wi-Fi
  3. (FX-DEVICE-IOS) B1: Account > sign in with FX-CUST-A; B4: sign out; B5: sign in as FX-CUST-B
  4. (FX-DEVICE-IOS) D2: open a deep link to an order while signed out
  5. (FX-DEVICE-IOS) Record device model, iOS version, build, date, tester; no tokens or codes in screenshots
- **Expected:**
  - UI: Name 'Matbakhy Beta'; Arabic default or device language; system browser sheet (not embedded) for sign-in; no data of A after switch to B; deep link asks to sign in and shows nothing private
  - API: Discover returns meals (no 403 'Not available')
  - API: Server-side: build is accepted by the beta OIDC client (matbakhy-beta registered)
  - DB: none
- **Also covered by automated tests:** native CI builds IOS-NATIVE logs (build only, no sign-in/MFA journey)
- **Unverified against code (confirm while executing):** Bundle id and client id taken from the commit message of 8ddc7b5 (beta branch), not present in PR #9; Android has no equivalent device plan in the repository: add a Android physical-device case when the plan exists

### CFG-112 — Android physical device sign-in and MFA step-up (no plan in repository yet)

- **Status:** BLOCKED — No Android device test plan, build or registered client exists in the repository; CLAUDE.md requires native verification on both platforms · **Priority:** P1 · **Readiness:** Needs a physical iOS/Android build
- **Surface:** customer-mobile · **Actors:** FX-DEVICE-ANDROID, FX-CUST-A · **Needs:** updated deploy, physical device
- **Traces to:** rules: BR-048, BR-052; decisions: D18; operations: putMeSecurityMfa; partial: TC-048, TC-052
- **Prerequisites:**
  - Deployed release recorded: on the server, in the deploy/web-preview directory, run `./deploy.sh status` and note RELEASE_COMMIT, both image digests and the schema version
  - Deployed API contains PR #9 (D18): `./deploy.sh status` shows schema version >= 31 and RELEASE_COMMIT is the PR #9 head or later; origin/develop does NOT contain V31/MfaController, so on the current deployment GET /api/v1/me/security is not a 200
  - Live realm migrated (owner-gated): on the server `./migrate-mfa.sh status` prints flows=complete, browserFlow=matbakhy browser and client.matbakhy-admin=present (NOT true on the live realm today: migration has never been run there)
  - Signed Android build installed on FX-DEVICE-ANDROID
  - Mobile OIDC client registered for the Android redirect scheme
- **Steps:**
  1. (FX-DEVICE-ANDROID) Repeat CFG-046 steps B1/B4/B5 and CFG-024 steps 1-6 on Android
- **Expected:**
  - UI: Same expectations as the iOS cases; Custom Tab used for sign-in
  - API: Same as CFG-024
  - DB: Same as CFG-024
- **Unverified against code (confirm while executing):** Android redirect scheme and client id unknown

### CFG-115 — fixture-clean removes only leftover verify-mfa-* synthetic users

- **Status:** NOT_RUN · **Priority:** P2 · **Readiness:** Needs the updated application deployment
- **Surface:** identity, deployment · **Actors:** FX-ISOLATED · **Needs:** updated deploy, isolated stack · **Destructive**
- **Traces to:** rules: BR-060; decisions: D18; operations: ./migrate-mfa.sh fixture-clean; partial: TC-060
- **Prerequisites:**
  - Deployed release recorded: on the server, in the deploy/web-preview directory, run `./deploy.sh status` and note RELEASE_COMMIT, both image digests and the schema version
  - Operator shell on the server in the deploy/web-preview directory (CD uses /srv/matbakhy-staging/deploy/web-preview; docs/operations/06 mentions /srv/matbakhy-preview/src/deploy/web-preview; use whichever `./deploy.sh status` works in)
  - FX-ISOLATED exists: a disposable compose stack with its own PREVIEW_DOMAIN and fresh volumes built from the PR #9 head images, realm imported, `./deploy.sh up` healthy, `./deploy.sh seed` run, and `./migrate-mfa.sh status` run on it prints flows=complete
- **Steps:**
  1. (FX-ISOLATED) Create two users via kcadm: `verify-mfa-leftover1` and `keep-me-e2e` (no passwords in the repo; set via the private fixture sheet)
  2. (FX-ISOLATED) `./migrate-mfa.sh fixture-clean`
  3. (FX-ISOLATED) List users: `./kcadm-in-container.sh get users -r matbakhy --fields username` after `config credentials`
- **Expected:**
  - UI: n/a
  - API: verify-mfa-leftover1 is gone; keep-me-e2e and preview-* users remain
  - DB: none
- **Also covered by automated tests:** scripts/test_keycloak_mfa_migration.py
- **Unverified against code (confirm while executing):** kcadm invocation needs credentials flags; follow kcadm-in-container.sh header

### CFG-118 — Rollback plan is read-only and tells the truth about the schema: image-only vs restore

- **Status:** NOT_RUN · **Priority:** P1 · **Readiness:** Needs the updated application deployment
- **Surface:** deployment · **Actors:** FX-ISOLATED · **Needs:** updated deploy, isolated stack
- **Traces to:** rules: BR-057; operations: ./deploy.sh rollback-plan; partial: TC-057, TC-060
- **Prerequisites:**
  - Deployed release recorded: on the server, in the deploy/web-preview directory, run `./deploy.sh status` and note RELEASE_COMMIT, both image digests and the schema version
  - Operator shell on the server in the deploy/web-preview directory (CD uses /srv/matbakhy-staging/deploy/web-preview; docs/operations/06 mentions /srv/matbakhy-preview/src/deploy/web-preview; use whichever `./deploy.sh status` works in)
  - FX-ISOLATED exists: a disposable compose stack with its own PREVIEW_DOMAIN and fresh volumes built from the PR #9 head images, realm imported, `./deploy.sh up` healthy, `./deploy.sh seed` run, and `./migrate-mfa.sh status` run on it prints flows=complete
  - releases.log has at least two entries (two successful `up` runs)
- **Steps:**
  1. (FX-ISOLATED) `./deploy.sh rollback-plan` and `./deploy.sh rollback-plan 2`
  2. (FX-ISOLATED) `./deploy.sh rollback-plan 0` and with only one logged release `./deploy.sh rollback-plan` — data: Bad inputs
  3. (FX-ISOLATED) `python3 scripts/check_migration_compat.py --list --baseline 28` — data: Class per migration V29..V31
- **Expected:**
  - UI: n/a
  - API: Verdict 'schema has not moved ... Image-only rollback' when equal; otherwise lists migrations V<n>..V<m> and the backup to restore
  - API: Bad inputs: usage error / 'only N release(s) recorded'
  - API: V31 is classed tightening (the check constraint on user_grant) per its first-line comment, so an image-only rollback must be rehearsed first
  - DB: Read-only: nothing changed (releases.log line count unchanged)
- **Also covered by automated tests:** scripts/test_migration_compat.py; scripts/test_preview_release.py

### CFG-119 — E2E reset guard: ./deploy.sh e2e-reset refuses without the marker, with public customer hosts, or without typed confirmation (no data is wiped)

- **Status:** NOT_RUN · **Priority:** P1 · **Readiness:** Isolated disposable stack only
- **Surface:** deployment · **Actors:** FX-ISOLATED · **Needs:** isolated stack
- **Traces to:** rules: BR-060; operations: ./deploy.sh e2e-reset; partial: TC-060
- **Prerequisites:**
  - Deployed release recorded: on the server, in the deploy/web-preview directory, run `./deploy.sh status` and note RELEASE_COMMIT, both image digests and the schema version
  - Operator shell on the server in the deploy/web-preview directory (CD uses /srv/matbakhy-staging/deploy/web-preview; docs/operations/06 mentions /srv/matbakhy-preview/src/deploy/web-preview; use whichever `./deploy.sh status` works in)
  - FX-ISOLATED exists: a disposable compose stack with its own PREVIEW_DOMAIN and fresh volumes built from the PR #9 head images, realm imported, `./deploy.sh up` healthy, `./deploy.sh seed` run, and `./migrate-mfa.sh status` run on it prints flows=complete
  - `test ! -f .e2e-enabled` (marker absent) and CUSTOMER_ALLOWED_IPS is not 0.0.0.0/0 on the isolated stack
- **Steps:**
  1. (FX-ISOLATED) `./deploy.sh e2e-reset; echo exit=$?` — data: No marker
  2. (FX-ISOLATED) `touch .e2e-enabled; CUSTOMER_ALLOWED_IPS='0.0.0.0/0 ::/0' ./deploy.sh e2e-reset; echo exit=$?; rm -f .e2e-enabled` — data: Public customer hosts (adjust .env on the isolated stack)
  3. (FX-ISOLATED) `touch .e2e-enabled; ./deploy.sh e2e-reset; echo exit=$?; rm -f .e2e-enabled` — data: No typed confirmation
  4. (FX-ISOLATED) `SELECT count(*) FROM customer_order` before and after steps 1-3 — data: Unchanged
- **Expected:**
  - UI: n/a
  - API: Step 1: dies 'e2e is not enabled on this host'; step 2: dies 'customer hosts are public'; step 3: dies 'set E2E_RESET_CONFIRM=<domain>'; all non-zero
  - API: No backup taken, no container stopped, counts unchanged
  - DB: Read-only: customer_order count unchanged
- **Also covered by automated tests:** scripts/test_e2e_fixtures.py
- **Unverified against code (confirm while executing):** NEVER run the confirmed form (E2E_RESET_CONFIRM=<domain>) on the shared preview: it wipes the application database

### CFG-120 — Account console link and residual risk: Manage authenticator opens the Keycloak account console; DISABLED cannot stop adding an authenticator there

- **Status:** NOT_RUN · **Priority:** P2 · **Readiness:** Needs the updated application deployment
- **Surface:** customer-web, identity · **Actors:** FX-CUST-A · **Needs:** updated deploy
- **Traces to:** rules: BR-001; decisions: D18; operations: getMeSecurity
- **Prerequisites:**
  - Deployed release recorded: on the server, in the deploy/web-preview directory, run `./deploy.sh status` and note RELEASE_COMMIT, both image digests and the schema version
  - Deployed API contains PR #9 (D18): `./deploy.sh status` shows schema version >= 31 and RELEASE_COMMIT is the PR #9 head or later; origin/develop does NOT contain V31/MfaController, so on the current deployment GET /api/v1/me/security is not a 200
  - Live realm migrated (owner-gated): on the server `./migrate-mfa.sh status` prints flows=complete, browserFlow=matbakhy browser and client.matbakhy-admin=present (NOT true on the live realm today: migration has never been run there)
  - Tester's source IP is inside CUSTOMER_ALLOWED_IPS (and STAFF_ALLOWED_IPS for ops.<PREVIEW_DOMAIN> and /api/v1/ops, /api/v1/cook): `curl -s -o /dev/null -w '%{http_code}' https://app.<PREVIEW_DOMAIN>/` returns 200 (a 403 body 'Not available' means the IP is not allow-listed; the preview is deny-by-default, not public, unless the owner set CUSTOMER_ALLOWED_IPS=0.0.0.0/0)
- **Steps:**
  1. (FX-CUST-A) Account > Security > click 'Manage authenticator app (add or remove)' — data: accountManagementUrl from GET /api/v1/me/security
  2. (FX-CUST-A) Check the URL opened equals https://id.<PREVIEW_DOMAIN>/realms/matbakhy/account/#/security/signingin unless OIDC_ACCOUNT_CONSOLE_URL overrides it
  3. (FX-CUST-A) Confirm the master realm account page is still 404: `curl -s -o /dev/null -w '%{http_code}' https://id.<D>/realms/master/account/`
- **Expected:**
  - UI: Keycloak account console 'Signing in' page of the matbakhy realm opens in the same account
  - API: accountManagementUrl is an https URL on id.<D>
  - API: Master realm path 404
  - DB: none
- **Also covered by automated tests:** MfaPolicyIntegrationTest.the_settings_report_which_security_administration_the_account_may_do
- **Unverified against code (confirm while executing):** The account console client/theme is enabled by default in Keycloak 26; not verified on the live realm

### CFG-121 — Smoke and release read-only gate: ./deploy.sh smoke covers headers, boundary, issuer, admin console 404, unauthenticated refusals

- **Status:** NOT_RUN · **Priority:** P1 · **Readiness:** Ready to run manually now
- **Surface:** deployment · **Actors:** FX-GUEST
- **Traces to:** rules: BR-060; operations: ./deploy.sh smoke; partial: TC-140, TC-060
- **Prerequisites:**
  - Deployed release recorded: on the server, in the deploy/web-preview directory, run `./deploy.sh status` and note RELEASE_COMMIT, both image digests and the schema version
  - Tester's source IP is inside CUSTOMER_ALLOWED_IPS (and STAFF_ALLOWED_IPS for ops.<PREVIEW_DOMAIN> and /api/v1/ops, /api/v1/cook): `curl -s -o /dev/null -w '%{http_code}' https://app.<PREVIEW_DOMAIN>/` returns 200 (a 403 body 'Not available' means the IP is not allow-listed; the preview is deny-by-default, not public, unless the owner set CUSTOMER_ALLOWED_IPS=0.0.0.0/0)
- **Steps:**
  1. (FX-GUEST) On the server (or a machine in both classes): `./deploy.sh smoke; echo exit=$?`; from outside the staff class: `SMOKE_OPS=skip sh smoke.sh <PREVIEW_DOMAIN>`
  2. (FX-GUEST) Read every `ok:` line and the final line
- **Expected:**
  - UI: n/a
  - API: Exit 0 and last line `PASS: web preview smoke test for <PREVIEW_DOMAIN>`; ok lines cover: customer web, operations web (or refusal), API reachable PREVIEW/demo, discovery, profile/staff/kitchen refuse without session, actuator and metrics not published, issuer, admin console and master realm 404, sign-in redirect
  - DB: none
- **Also covered by automated tests:** scripts/test_proxy_access.py; scripts/test_preview_release.py
- **Unverified against code (confirm while executing):** Smoke does not assert any MFA behaviour

### CFG-122 — Authorized MFA policy change by an administrator with recent MFA is applied, audited and restored to the exact previous mode

- **Status:** NOT_RUN · **Priority:** P0 · **Readiness:** Needs the updated application deployment
- **Surface:** api, operations-web · **Actors:** FX-STAFF-ADMIN-B · **Needs:** updated deploy, isolated stack · **Destructive**
- **Fixtures to provision:** FX-STAFF-ADMIN-B
- **Traces to:** rules: BR-001, BR-007, BR-043; decisions: D18; operations: getOpsSecurityMfaPolicy, putOpsSecurityMfaPolicyByCategory; partial: TC-007
- **Prerequisites:**
  - Deployed release recorded: on the server, in the deploy/web-preview directory, run `./deploy.sh status` and note RELEASE_COMMIT, both image digests and the schema version
  - Deployed API contains PR #9 (D18): `./deploy.sh status` shows schema version >= 31 and RELEASE_COMMIT is the PR #9 head or later; origin/develop does NOT contain V31/MfaController, so on the current deployment GET /api/v1/me/security is not a 200
  - Tester's source IP is inside CUSTOMER_ALLOWED_IPS (and STAFF_ALLOWED_IPS for ops.<PREVIEW_DOMAIN> and /api/v1/ops, /api/v1/cook): `curl -s -o /dev/null -w '%{http_code}' https://app.<PREVIEW_DOMAIN>/` returns 200 (a 403 body 'Not available' means the IP is not allow-listed; the preview is deny-by-default, not public, unless the owner set CUSTOMER_ALLOWED_IPS=0.0.0.0/0)
  - FX-STAFF-ADMIN-B holds active grants security.mfa_policy and security.mfa_reset and has an enrolled authenticator (TOTP secret kept in the private fixture sheet). Provision per docs/quality/owner-acceptance/fixtures.md (the bootstrap administrator preview-ops-1 holds only staff.grant and audit.read, so these permissions are absent by default)
  - ISOLATED STACK ONLY: this case really changes policy (REQUIRED->OPTIONAL weakens, DISABLED->OPTIONAL changes behaviour); run only on FX-ISOLATED
- **Steps:**
  1. (FX-STAFF-ADMIN-B) GET /api/v1/ops/security/mfa-policy and write down mode and version of STAFF (prior)
  2. (FX-STAFF-ADMIN-B) Step up with the enrolled TOTP, then PUT /api/v1/ops/security/mfa-policy/STAFF {"mode":"<a mode different from prior>","expectedVersion":<version>,"reason":"E2E authorised change"}
  3. (FX-STAFF-ADMIN-B) GET /api/v1/me/security for a STAFF-category account and GET /api/v1/ops/security/mfa-policy — data: Effect on the next request
  4. (FX-STAFF-ADMIN-B) Restore: PUT STAFF back to the prior mode with the new version and reason 'E2E restore' — data: Rollback
- **Expected:**
  - UI: Ops web Security tab shows 'Policy saved.'
  - API: Step 2: 200 with version prior+1 and the new mode
  - API: Step 3: the staff account's effectiveMode follows the new value without re-login
  - API: Step 4: 200, mode equals prior, version prior+2
  - DB: Read-only: two mfa.policy.update audit rows with details {category:STAFF, from, to} in both directions and the reasons given; mfa_policy.mode equals the prior mode at the end
- **Also covered by automated tests:** MfaPolicyIntegrationTest.only_the_policy_permission_with_a_recent_second_factor_can_change_a_policy; MfaPolicyIntegrationTest.enforcement_and_settings_are_for_signed_in_accounts_only; MfaPolicyIntegrationTest.the_settings_report_which_security_administration_the_account_may_do
- **Unverified against code (confirm while executing):** Guest PUT order of CSRF vs authentication rejection not confirmed
