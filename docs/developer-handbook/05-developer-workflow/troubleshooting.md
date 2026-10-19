# Troubleshooting

> **Owner role:** Testing and QA
> **Status:** REFERENCE — failure modes taken from scripts, CI workflows and the operations runbook; none were reproduced while writing this page.

Order, audit, dead-letter and telemetry diagnosis on the preview is in [08-troubleshooting-runbook.md](../../operations/08-troubleshooting-runbook.md) (itself a draft that was not exercised on a deployed environment). This page covers developer-side failures.

## Setup and toolchain

| Symptom | Cause (source) | Fix |
|---|---|---|
| `pnpm install` fails with an engine error | `.npmrc` has `engine-strict=true`; Node must satisfy `>=24.21.0 <25` | install Node 24.21.0 (`.node-version`), `corepack enable` |
| `scripts/check_workspace.sh` says "Node ... required" or "pnpm ... required" | exact version comparison | use the pinned versions ([prerequisites.md](prerequisites.md)) |
| `scripts/dev.sh doctor` prints `MISSING: JDK 21` | `java.specification.version` is not 21 | put a JDK 21 first on `PATH` / `JAVA_HOME` |
| Maven wrapper cannot download, or HTTP 429 from Maven Central | seen intermittently in the original dev container ([toolchain.md](../../engineering/toolchain.md)) | retry; set `MVNW_REPOURL` to a Central mirror for the wrapper; do not change `backend/pom.xml` repositories |
| `docker info` fails / Testcontainers cannot start | integration tests need a running Docker daemon; there is no H2 fallback | start Docker; in WSL2 enable Docker Desktop integration |
| `scripts/check_secrets.sh` exits 2 | Docker daemon unavailable, the scan needs the Gitleaks image | start Docker |
| `scripts/check_design.sh` cannot find a browser | needs Chromium (`CHROMIUM_PATH`) | install Chrome/Chromium and export `CHROMIUM_PATH` |

## Local stack

| Symptom | Cause | Fix |
|---|---|---|
| `refusing` / "not marked MATBAKHY_ENV_MARKER=local" | `ensure_env` in `scripts/dev.sh` only accepts local env files | never point `MATBAKHY_ENV_FILE` at a deployed `.env` |
| Port already in use (5432, 8080, 8180, 9090, 8025, 1025) | defaults in `infra/.env.example`; compose publishes on `127.0.0.1` | stop the other process, or edit the port values in your local env file (also keep the web apps' API URL in step) |
| `up` hangs then reports unhealthy | compose waits up to 300 s for health | `docker compose --env-file infra/.env -f infra/compose.yaml ps` and `logs --tail 100 api identity postgres` |
| Smoke: "Flyway baseline not applied" | API did not start or migrations failed | read API logs; a failed migration shows `success = false` in `flyway_schema_history` |
| Smoke: "Keycloak realm discovery failed" | realm import failed; the rendered realm is in infra/.generated/identity (git-ignored) | `logs identity`; `scripts/dev.sh reset` then `up` (regenerates the realm) |
| Realm secrets seem stale | `OIDC_*_CLIENT_SECRET` in the env file differ from the rendered realm | `scripts/dev.sh reset` then `up` |
| Web app: login redirects to the wrong host or loops | apps must run on `localhost:5173` / `5174` (`strictPort`; API CORS and redirect are fixed to those) | free the ports; open the app at `localhost` |
| API refuses to start: missing `APP_ENV` | `matbakhy.environment: ${APP_ENV}` has no default in `application.yaml` | set `APP_ENV=LOCAL` (compose does) |
| "The dev profile is not allowed in ..." | `EnvironmentGuard` blocks the `dev` profile and devdata in STAGING/PRODUCTION | remove `SPRING_PROFILES_ACTIVE=dev` from non-synthetic environments |
| Direct upload fails, signed URL points to the wrong port | signed URLs use the host-published port (`OBJECT_STORAGE_CLIENT_ENDPOINT`) while API-to-storage uses the compose network | keep `OBJECT_STORAGE_PORT` consistent in the env file |

## Tests

| Symptom | Cause | Fix |
|---|---|---|
| `scripts/check_backend_suite.sh` prints `FAIL: X did not run` | class renamed, wrong name, or filtered out | use the simple class name; check `backend/**/target/surefire-reports` |
| Integration test hangs at start | container pull, slow Docker, or a stale Testcontainers resource | check Docker, free disk, rerun; never replace the container with a mock |
| Flaky race test | a sleep instead of a barrier | fix the test to use deterministic coordination ([01-verification.md](../../quality/01-verification.md)); do not just rerun until green |
| Contract check fails with drift | `contracts/openapi.yaml` changed without regenerating `packages/api-client/src/generated/schema.ts` | `pnpm --filter @matbakhy/api-client run generate`, commit, rerun `pnpm contract:check` |
| `scripts/dev.sh e2e` fails to start webs | ports 5173/5174 busy | free the ports; logs are in the temporary directory printed on failure |
| Playwright cannot launch a browser | no browser downloaded | set `E2E_CHROMIUM` to an installed Chromium |
| Owner catalog `check` fails "stale" | edited a generated file or a case without rendering | `python3 scripts/owner_e2e_catalog.py render`; edit only `cases/*.json`, `core-suite.json`, `coverage-notes.json` |
| Fixture check fails | manifest, SQL seed and cases disagree | `python3 scripts/owner_e2e_fixtures.py check` names the mismatch |
| Migration compat test fails | new migration has a risky statement without `-- rollback-compat: <class> <reason>` (reason at least ten characters) | add the annotation honestly; if breaking, say so in the PR |
| `unittest discover` proxy tests skipped | Docker images `caddy` and `python` absent | `docker pull caddy:2.10.2-alpine python:3.13-alpine` as `blueprint-integrity.yml` does |

## Git, PR and docs

| Symptom | Fix |
|---|---|
| `doc001_check.py docs` fails on a link or `path` | the path in backticks must exist; use `<placeholders>` or a fenced block for future paths |
| `doc001_check.py pr` reports "area ... changed but none of its required documentation was updated" | update a page listed for that area in `docs/developer-handbook/doc001-matrix.json` and list it in `handbook_pages` |
| Declaration rejected: justification generic | name the unchanged documented behaviour and cite an existing page by path ([documentation-policy.md](documentation-policy.md)) |
| CI fails on a branch with a green local run | CI uses a clean clone of committed `HEAD` and exact pinned versions; uncommitted or untracked files, a different Node, or a missing lockfile update explain most differences |
| Push rejected | fetch and merge or rebase your own branch; never force-push a shared one |

## Preview

| Symptom | Fix |
|---|---|
| HTTP 403 "Not available" | your IP is outside the allow-list for that host class; ask the owner ([preview-environment.md](preview-environment.md)) |
| `kc-admin` answers 404 | the console switch is closed; an operator runs `./deploy.sh admin-console status` |
| Deployment workflow stuck "Wait for passing Product CI" | Product CI at that commit is pending or failed; fix CI first |
| Deploy failed, previous `.env` restored | the database was **not** rolled back; operator uses `./deploy.sh rollback-plan` and the decision table in [11-staging-deployment-and-rollback.md](../../operations/11-staging-deployment-and-rollback.md) |
| Home page empty | seeded slots expired; `refresh-slots.sql` (part of the owner E2E `apply`) |
| `owner-e2e-provision` workflow refuses | request file first word must be `plan` or `apply`; `apply` also needs API `APP_ENV=PREVIEW` and schema at least V31 |
| Need to trace a failed order | request id from the error body or `X-Request-Id`, then logs/audit as in [08-troubleshooting-runbook.md](../../operations/08-troubleshooting-runbook.md) |

## When stuck

Retry a defect at most three materially different ways, then write down the exact blocker (command, output, what you tried) in the PR or issue and move to independent work ([CLAUDE.md](../../../CLAUDE.md)). A documentation mistake found on the way is a defect: fix the handbook page in the same PR.
