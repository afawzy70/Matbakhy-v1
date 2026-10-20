# Run and debug

> **Owner role:** Testing and QA
> **Status:** REFERENCE — how to run the pieces, call the API, read logs and inspect the database and Keycloak. Commands were read from scripts and workflows; they were not executed in the environment that wrote this page (no Docker, Java or pnpm there), so treat them as NOT VERIFIED until you run them.

Set up first: [local-environment.md](local-environment.md). Shorthand used below:

```bash
DC="docker compose --env-file infra/.env -f infra/compose.yaml"
set -a; . infra/.env; set +a     # loads ports, POSTGRES_*, DEV_USER_PASSWORD into your shell only
```

## What runs where

| Piece | Start | Check it is alive |
|---|---|---|
| Whole local stack | `scripts/dev.sh up` | `scripts/dev.sh smoke` |
| API | part of the stack (`api` service) | `curl -fsS http://127.0.0.1:8080/actuator/health` returns `"status":"UP"` |
| Customer web | `pnpm --filter @matbakhy/customer-web dev` | http://localhost:5173 |
| Operations web (cook workspace + back office) | `pnpm --filter @matbakhy/operations-web dev` | http://localhost:5174 |
| Mobile | `pnpm --filter @matbakhy/customer-mobile start` | Expo dev server output |
| PostgreSQL | `postgres` service | `$DC ps` shows `healthy` |
| Keycloak | `identity` service | `curl -fsS http://localhost:8180/realms/matbakhy/.well-known/openid-configuration` |

## Calling the API

The contract is `contracts/openapi.yaml` (operation list in `contracts/endpoints.json`); the generated TypeScript client is in `packages/api-client`. See [api-contracts.md](../04-codebase/api-contracts.md).

- Public endpoints need no session, for example `GET /api/v1/configuration/public` and `GET /api/v1/meals?limit=5` (the preview smoke test calls both).
- Everything private needs the session cookie from the OIDC web login and, for state-changing calls, a CSRF header: `scripts/dev.sh smoke` shows the working sequence with curl (cookie jar, `XSRF-TOKEN` cookie copied into `X-XSRF-TOKEN`). Read the `smoke` function in `scripts/dev.sh` and reuse it rather than guessing.
- An anonymous call to a private path must return 401 (`/api/v1/me`); the smoke test asserts it.
- Error bodies have `{code, messageKey, fieldErrors, requestId, retryable}`; every response carries `X-Request-Id`. Quote that id when you look for the cause in logs ([toolchain.md](../../engineering/toolchain.md), section on MB-006).
- Mutating endpoints that create orders or reservations take an `Idempotency-Key`; replaying the same key returns the first result. Do not retry a failed order call with a new key until you know whether the first one succeeded (`GET /api/v1/orders/by-request/{id}` looks it up for the owner).
- Money is integer piastres in every payload. Never send or compute prices in the client: the server quote is authoritative ([pricing-and-payments.md](../01-business/pricing-and-payments.md)).

A browser-based way to see calls: open the browser dev tools Network tab on the web app and copy the failing request's `X-Request-Id`.

## Logs

```bash
$DC logs --tail 200 api            # API: plain text lines locally, each with the request id in brackets, e.g. "INFO [<request-id>] ..."
$DC logs --tail 100 identity       # Keycloak
$DC logs --tail 100 postgres
$DC logs api | grep -F '[<id>]'     # local default text format (logging.pattern.level in application.yaml)
```

Local Compose does not set `LOGGING_STRUCTURED_FORMAT_CONSOLE`, so local API logs are text. Only deployments that set it (`deploy/web-preview/compose.yaml` sets `ecs`) emit ECS JSON lines (fields `service.environment`, `service.version`, `log.logger`, `message`, and the request id as the MDC field `requestId`); there filter with `grep '"requestId":"<id>"'` or in Grafana/Loki.

Backend test logs: `backend/**/target/surefire-reports/` (the files `scripts/check_backend_suite.sh` reads). Browser test traces and screenshots on failure: through `scripts/dev.sh e2e` the default output directory is `$VERIFY_TMP/results` inside a temporary directory that the script deletes on exit, so nothing survives by default. Set a persistent, ignored location before the run, for example `E2E_OUTPUT_DIR="$PWD/test-results/e2e" scripts/dev.sh e2e` (`test-results/` is git-ignored, `.gitignore`), then open a trace with `pnpm exec playwright show-trace <trace.zip>` from `apps/operations-web`. Running Playwright directly (outside `dev.sh`) writes `test-results/` under `apps/operations-web`.

On the preview the same request-id technique applies through Grafana/Loki; see [troubleshooting.md](troubleshooting.md) and `docs/operations/08-troubleshooting-runbook.md`.

## Database debugging

```bash
$DC exec postgres psql -U "$POSTGRES_USER" -d "$POSTGRES_DB"
\dt                                   -- tables
SELECT version, description, success FROM flyway_schema_history ORDER BY installed_rank DESC LIMIT 10;
```

- Migrations are forward-only files `backend/app/src/main/resources/db/migration/V<n>__*.sql`. Never edit an applied migration; add a new one. Migrations after the baseline must be expand-only or carry a `-- rollback-compat:` annotation, enforced by `scripts/check_migration_compat.py` ([ci-cd.md](ci-cd.md)).
- Order rows are immutable snapshots and `order_event` is append-only; audit rows cannot be updated or deleted (database triggers, per `docs/operations/08-troubleshooting-runbook.md`). Do not "fix" data by editing those tables; find the cause in code.
- `scripts/db-backup.sh` and `scripts/db-restore.sh` take and verify a logical backup; the output directory must be outside any git working tree because dumps hold personal data.
- Read-only habit: use `SELECT` while investigating. If you must change local data for a reproduction, do it on a throwaway stack (`scripts/dev.sh verify` style) or after `scripts/dev.sh reset`.

## Keycloak debugging

- Local admin console: `http://localhost:8180` with the admin user from your env file.
- Realm file: `infra/identity/matbakhy-realm.json` (template, secrets filled by `render_realm` in `scripts/dev.sh`). Clients seen in the scripts: `matbakhy-web` (confidential web client), `matbakhy-mobile` (public, PKCE required, redirect `matbakhy-dev://auth/callback`), `matbakhy-admin` (service account for audited MFA reset).
- Typical symptoms: redirect loops or "invalid redirect" mean the apps were opened on a different host or port than `WEB_POST_LOGIN_REDIRECT` and `KC_HOSTNAME` expect; login succeeds but `/api/v1/me` is 401 means the session cookie was not sent (wrong origin) or the issuer differs between browser and API (`OIDC_ISSUER` vs `OIDC_BACKCHANNEL_ISSUER` in `infra/compose.yaml`).
- MFA policy values OPTIONAL / REQUIRED / DISABLED per population are described in [authentication-and-mfa.md](../02-user-journeys/authentication-and-mfa.md) and `docs/security/mfa-policy.md`.

## Debugging the code

- Backend: tests are the fastest loop. Run one class (`(cd backend && ./mvnw -B -ntp verify -Dtest=<ClassName> -Dsurefire.failIfNoSpecifiedTests=false)`; the subshell keeps your shell in the repository root). Integration tests use annotation `IntegrationTest` (`backend/app/src/test/java/com/matbakhy/app/support/IntegrationTest.java`) with real PostgreSQL and S3-emulator containers, so Docker must be running. Attaching an IDE debugger to the containerised API: NOT VERIFIED (no debug port is published in `infra/compose.yaml`); debug through a test instead.
- Web: `pnpm --filter @matbakhy/customer-web test` (Vitest, jsdom) and the browser dev tools; source maps are on in the Vite dev server.
- Mobile: Jest tests in `apps/customer-mobile/src/*.test.ts(x)`; native behaviour only through the scripts in `apps/customer-mobile/scripts/`.

## Common failure modes

See [troubleshooting.md](troubleshooting.md).

## Repository hygiene

`python3 scripts/branch_audit.py --gh --local` prints a report-only classification of branches (it never deletes). The cleanup policy and its rules are in [branch maintenance](branch-maintenance.md).
