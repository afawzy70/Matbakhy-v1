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

All commands below ran from the worktree root unless an explicit `cd` appears. Full exact commands, exit codes and log paths: [commands.json](../phase2-evidence/commands.json). Counts are taken from actual Vitest/Jest JSON reports; repeated runs are not added to unique test totals. The unmodified pnpm commands all exited 1 due to engine enforcement; direct expansions verify their underlying tasks. A temporary `/tmp/qa2-bin/pnpm` wrapper executed `/home/aiagent/.npm-global/bin/pnpm --config.engine-strict=false "$@"`; it did not resolve nested root-script enforcement and was not installed in the repository.

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

QA2-001 through QA2-005 describe engine, invocation and environment failures with exact reproduction details in [findings.json](../phase2-evidence/findings.json). These are execution limitations, not demonstrated product defects. Initial Python module import failures were corrected using discovery; the scope failure is retained as BLOCKED. Secret/proxy checks must be rerun with Docker.

Recommendations: rerun the blocked scope fixture on a clean checkout, and run backend/secret/proxy suites on a JDK/Docker runner; repeat supported checks with pinned Node for release evidence. No approvals, merges or release-readiness claims follow from this run.


## Round 4 — 2026-10-18, isolated offline worktree

Tested source / verified HEAD: `d3e22ca081dc0ede9ee02f496642de4d1a86d63a`. Node v22.23.3, pnpm 10.34.6, JDK `/home/aiagent/tools/jdk-21.0.12.1+1`. No application changes; no delegation.

| Executed check | Result | Counts |
|---|---|---|
| `python3 scripts/validate_blueprint.py` | PASS, exit 0 | 1 validator |
| `python3 scripts/check_scope.py` | PASS, exit 0 | 1 validator |
| `python3 scripts/check_migration_compat.py` | PASS, exit 0 | 1 validator |
| `python3 -m unittest discover -s scripts -p 'test_*.py'` | FAIL, exit 1 | 73 run: 71 passed, 1 failed, 1 skipped |
| design-tokens Vitest | PASS, exit 0 | 47 tests, 1 file |
| api-client Vitest | PASS, exit 0 | 34 tests, 4 files |
| customer-web Vitest | PASS, exit 0 | 48 tests, 1 file |
| operations-web Vitest | PASS, exit 0 | 26 tests, 1 file |
| customer-mobile Jest | PASS, exit 0 | 55 tests, 4 suites |
| Requested backend unit selection | PASS, exit 0 | 15 tests, 4 classes; no failures/errors/skips |

Each client command was `npm_config_engine_strict=false pnpm --dir <package-path> test`; 210 client tests total. Mobile emitted overlapping React act() warnings despite passing. Backend command, from backend with the JDK above: `./mvnw -o -B -pl app -am test -Dtest='StaffAssuranceTest,AccountMapperStaffMfaTest,MediaInspectorPdfTest,OrderFingerprintTest' -Dsurefire.failIfNoSpecifiedTests=false`. Counts: StaffAssurance 3, AccountMapperStaffMfa 4, MediaInspectorPdf 5, OrderFingerprint 3. Raw client/backend logs are in `/tmp/qa4/` for this session.

The known scope environment failure is CONFIRMED: `test_scope.ScopeGuards.test_current_repository_passes` reports exactly `inventory: unlisted top-level entry .aws` and `.codex`. `scripts/test_scope.py:19` copies the repository without excluding these host directories; the copy lacks Git metadata, so `scripts/check_scope.py:45` falls back to filesystem listing. A controlled rerun of that single test with only `.aws`/`.codex` additionally excluded passed (1 test, exit 0). No host directory contents were printed. The migration failure message for V29__test.sql during unittest discovery is expected negative-test output, not an additional suite failure.

### Changed-test review against 164a831

- StaffMfaIdpTest:126-147: meaningful real-provider SSO regressions. Reusing each login's browser retains cookies; staff must receive an OTP form without a code, customers must receive a code without another prompt. No sleeps. Initial OTP uses wall-clock time before browser login; recommendation: calculate OTP immediately before form submission to reduce expiry exposure during slow CI. This is a review recommendation, not a reproduced flake. Docker execution NOT_RUN.
- MediaCleanupIntegrationTest:58-64: the injected clock starts one day ahead and advances explicitly. Cleanup rows created by this instance are not due to the real-clock scheduler within the bounded test run. Existing assertions exercise retries, backoff, bounded failure, and preservation of bytes on delete failure. Docker execution NOT_RUN.
- MediaIntegrationTest:318-328: meaningful before/after cleanup checks preserve approved document bytes and referenced-object protection. SQL makes the cleanup due without a sleep; it does not actually expire the upload ticket. Recommendation: additionally assert persisted next_attempt_at is at least ticket expiry plus the configured safety margin; the current assertion proves deferral but not its full duration. Docker execution NOT_RUN.
- OrderFingerprintTest:20-40: all 3 tests executed and passed. Fixed request identities, whitespace/null normalization, delimiter/boundary collision regressions and changed material fields are meaningful. Recommendation: replace random quote UUID with a second fixed UUID and add changed clientRequestId coverage. No observed nondeterministic failure. These unit tests verify the fingerprint function, not database retention recovery.
- AccountMapperStaffMfaTest:60-62: the added assertion verifies the documented YAML placeholder exists; it does not exercise actual environment-property binding. All 4 class tests passed.

No new evidence-based product defects were established in this bounded round; Round 4 findings array is `qa4-findings.json` (empty). Historical findings are preserved and are not re-adjudicated by this round. Docker-dependent SSO/media/integration tests are NOT_RUN locally by owner instruction; CI results at this exact source SHA remain required and were not fetched (no network).
