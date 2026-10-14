# MB-022 review (self-review, not independent)

Reviewer: Claude Code implementation agent (self-review).

## Scope
Cook dashboard (API-045), settings (API-065, V15 `paused_by`), statement (API-064), web login `returnTo`, operations-web cook workspace, `scripts/dev.sh e2e` with a Playwright workflow, synthetic `cook-a` realm user.

## Findings
1. Defect found by the new integration test and fixed before commit: a resume request on a SUSPENDED kitchen returned 200 without change instead of 409. Pause/resume now allow only ACTIVE/PAUSED (pause) and ACTIVE or a cook pause (resume).
2. Security: `returnTo` accepts only an exact configured origin with an absolute path (no user-info, no `//` path, no look-alike hosts); otherwise the default landing page. Identity test covers six cases against real Keycloak; a mutation accepting any origin failed it.
3. A staff pause stays a staff pause: ops PAUSE records `STAFF`; cooks cannot resume it. Existing PAUSED rows are treated as staff pauses.
4. The statement states what it covers (`includes`); refunds, reversals and remittances are MB-025 and are not shown as zero. The UI repeats this.
5. UI never assumes a transition: every command sends the server version (and idempotency key), shows the server error and reloads. Deadlines use the dashboard's server time offset.
6. The TC-103 brand guard flagged the API client factory name in app source; the factory was renamed to `createApiClient` instead of weakening the guard.
7. Mutations: UI sending a stale version failed the browser workflow; the returnTo mutation failed the identity test.

## Residual risks
- Cook approval in the browser workflow is inserted by synthetic SQL because staff approval requires MFA, which local Keycloak does not provide (approval workflow is covered by CookWorkflowIntegrationTest).
- XSRF cookie is read by the operations web from the API's cookie; this works on localhost (cookies are not port-scoped). Deployed domains need the API and web apps under one site or a token endpoint; decide with hosting (P09).
- Customer phone verification in the browser workflow is set by SQL (fake SMS code is not exposed).
- The browser workflow covers Chromium only; the cook application editing UI is not part of the operations web (application state is shown read-only).
- Browser workflows run locally (`scripts/dev.sh e2e`); not yet in GitHub CI.
