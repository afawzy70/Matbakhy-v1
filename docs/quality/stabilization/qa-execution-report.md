# Phase 2 QA execution report

Agent: independent QA engineer. Tested commit: `890bc872468085747c938dd3cb27b77ba036c713`. Date: 2026-10-18. Environment: isolated workspace-write sandbox; no network, Docker or JDK; installed dependencies; Node v22.23.3; pnpm 10.34.6. No application source modified.

Result: supported checks passed; full verification remains BLOCKED. Python: 69 passed, 1 environment-induced assertion failure, 0 test errors on corrected discovery invocations, 1 skipped class (0 proxy tests run). JavaScript: 210 passed, 0 failed, 0 skipped/pending. Earlier import attempts produced 2 loader errors; these are not extra executed tests. No product defects established by this execution. Phase 1 static findings remain separately recorded.

| Check | Pass | Fail | Other |
|---|---:|---:|---|
| Blueprint, scope, migration scripts | 3 | 0 | Planning/static checks only |
| Python bootstrap | 5 | 0 | |
| Python fixtures | 7 | 0 | |
| Python guardrails (discovery) | 15 | 0 | |
| Python migration | 8 | 0 | Expected negative-fixture FAIL text is asserted and suite passes |
| Python backup | 2 | 0 | |
| Python release | 8 | 0 | |
| Python proxy | 0 | 0 | BLOCKED: one class skipped, Docker unavailable |
| Python evidence | 2 | 0 | |
| Python scope (discovery) | 11 | 1 | BLOCKED: injected .aws/.codex directories in copied fixture |
| Python staging | 11 | 0 | |
| Secret scanner | 0 | 0 | BLOCKED: exit 2; Docker daemon not available |
| Typecheck | 5 projects | 0 | Expanded command with engine override |
| Lint | 1 check | 0 | Engine override |
| Web builds | 2 projects | 0 | Expanded command with engine override |
| Contract | 2 checks | 0 | Four ambiguous-path warnings; generated client matches |
| JS customer-web | 48 | 0 | 0 pending |
| JS operations-web | 26 | 0 | 0 pending |
| JS api-client | 34 | 0 | 0 pending |
| JS customer-mobile | 55 | 0 | 0 pending |
| JS design-tokens | 47 | 0 | 0 pending |


## Commands and evidence

All commands below ran from the worktree root unless an explicit `cd` appears. Full exact commands, exit codes and log paths: [commands.json](phase2-evidence/commands.json). Counts are taken from actual Vitest/Jest JSON reports; repeated runs are not added to unique test totals. The unmodified pnpm commands all exited 1 due to engine enforcement; direct expansions verify their underlying tasks. A temporary `/tmp/qa2-bin/pnpm` wrapper executed `/home/aiagent/.npm-global/bin/pnpm --config.engine-strict=false "$@"`; it did not resolve nested root-script enforcement and was not installed in the repository.

| Exact command | Exit |
|---|---:|
| `python3 scripts/validate_blueprint.py` | 0 |
| `python3 scripts/check_scope.py` | 0 |
| `python3 scripts/check_migration_compat.py` | 0 |
| `python3 -m unittest scripts.test_dev_bootstrap -v` | 0 |
| `python3 -m unittest scripts.test_e2e_fixtures -v` | 0 |
| `python3 -m unittest scripts.test_guardrails -v` | 1 |
| `python3 -m unittest scripts.test_migration_compat -v` | 0 |
| `python3 -m unittest scripts.test_preview_backup -v` | 0 |
| `python3 -m unittest scripts.test_preview_release -v` | 0 |
| `python3 -m unittest scripts.test_proxy_access -v` | 0 |
| `python3 -m unittest scripts.test_record_evidence -v` | 0 |
| `python3 -m unittest scripts.test_scope -v` | 1 |
| `python3 -m unittest scripts.test_staging_profile -v` | 0 |
| `bash scripts/check_secrets.sh` | 2 |
| `pnpm typecheck` | 1 |
| `pnpm lint` | 1 |
| `pnpm test` | 1 |
| `pnpm build` | 1 |
| `pnpm contract:check` | 1 |
| `npm_config_engine_strict=false pnpm typecheck` | 1 |
| `npm_config_engine_strict=false pnpm lint` | 0 |
| `npm_config_engine_strict=false pnpm test` | 1 |
| `npm_config_engine_strict=false pnpm build` | 1 |
| `npm_config_engine_strict=false pnpm contract:check` | 1 |
| `PATH="/tmp/qa2-bin:$PATH" pnpm test` | 1 |
| `PATH="/tmp/qa2-bin:$PATH" pnpm build` | 1 |
| `PATH="/tmp/qa2-bin:$PATH" pnpm contract:check` | 1 |
| `pnpm --config.engine-strict=false -r --if-present run typecheck` | 0 |
| `pnpm --config.engine-strict=false -r --if-present run test` | 0 |
| `pnpm --config.engine-strict=false -r --if-present run build` | 0 |
| `pnpm --config.engine-strict=false --filter @matbakhy/api-client run lint:contract` | 0 |
| `pnpm --config.engine-strict=false --filter @matbakhy/api-client run check:drift` | 0 |
| `(cd packages/api-client && ./node_modules/.bin/vitest run --reporter=json --outputFile=/tmp/qa2-results/api-client-tests.json)` | 0 |
| `(cd packages/design-tokens && ./node_modules/.bin/vitest run --reporter=json --outputFile=/tmp/qa2-results/design-tokens-tests.json)` | 0 |
| `(cd apps/customer-web && ./node_modules/.bin/vitest run --reporter=json --outputFile=/tmp/qa2-results/customer-web-tests.json)` | 0 |
| `(cd apps/operations-web && ./node_modules/.bin/vitest run --reporter=json --outputFile=/tmp/qa2-results/operations-web-tests.json)` | 0 |
| `python3 -m unittest discover -s scripts -p test_guardrails.py -v` | 0 |
| `python3 -m unittest discover -s scripts -p test_scope.py -v` | 1 |
| `pnpm --config.engine-strict=false typecheck` | 1 |
| `PATH="/tmp/qa2-bin:$PATH" pnpm typecheck` | 1 |
| `pnpm --config.engine-strict=false -r --if-present run typecheck` | 0 |
| `(cd packages/api-client && PATH="/tmp/qa2-bin:$PATH" node scripts/check-drift.mjs)` | 0 |
| `pnpm --config.engine-strict=false --reporter=append-only -r --if-present run test` | 0 |
| `pnpm --config.engine-strict=false --stream -r --if-present run test` | 0 |
| `(cd apps/customer-mobile && ./node_modules/.bin/jest --ci --json --outputFile=/tmp/qa2-results/customer-mobile-tests.json)` | 0 |


## NOT_RUN

- `(cd backend && ./mvnw -B -ntp verify)`: NOT_RUN; no JDK or Docker/Testcontainers in this sandbox; exit N/A, pass/fail counts N/A. All backend focused Maven/Testcontainers suites likewise NOT_RUN.
- Browser/local-stack and Android/iOS native/device checks: NOT_RUN; outside requested runnable list, required infrastructure unavailable. JavaScript results do not establish native or API/database acceptance.

## Findings

QA2-001 through QA2-005 describe engine, invocation and environment failures with exact reproduction details in [findings.json](phase2-evidence/findings.json). These are execution limitations, not demonstrated product defects. Initial Python module import failures were corrected using discovery; the scope failure is retained as BLOCKED. Secret/proxy checks must be rerun with Docker.

Recommendations: rerun the blocked scope fixture on a clean checkout, and run backend/secret/proxy suites on a JDK/Docker runner; repeat supported checks with pinned Node for release evidence. No approvals, merges or release-readiness claims follow from this run.
