# D18 round 2 independent QA execution

FINAL source SHA under test: `9ff290f732872c637b64132a3deeb52970148336`. `git rev-parse HEAD` equals this SHA before and after execution. Date: 2026-10-18. Isolated workspace-write; no network; Node 22.23.3 / pnpm 10.34.6 with engine-strict=false; JDK `/home/aiagent/tools/jdk-21.0.12.1+1`; offline Maven. No application/test source modified. Reports remain uncommitted so HEAD continues to identify the requested source.

Result: three validators PASS; 220 JavaScript tests PASS; five package typechecks and root ESLint PASS; selected offline backend suite 24 tests PASS (zero failures/errors/skips). Python discovery exits 1: 73 reported tests, 71 passed, one host-fixture failure and one skipped Docker class. The failure is BLOCKED/environment-induced, not an MFA defect. Container-dependent tests are NOT_RUN. No release or full D18 acceptance PASS claimed.

| Suite | Executed result |
|---|---|
| api-client | 34 passed |
| design-tokens | 47 passed |
| customer-web | 53 passed |
| operations-web | 31 passed |
| customer-mobile JS | 55 passed; native NOT_RUN |
| MfaPolicyResolveTest | 6 passed, including NEW reset-epoch boundary |
| StaffAssuranceTest | 3 passed |
| ModuleBoundaryTest | 7 passed |
| MediaInspectorPdfTest | 5 passed |
| OrderFingerprintTest | 3 passed |

Python failure: `test_scope.ScopeGuards.test_current_repository_passes`, scripts/test_scope.py:43, reports `inventory: unlisted top-level entry .aws` and `.codex` in its copied fixture. The direct check_scope command passes; host-injected directory names are not product scope changes. No contents of these directories were read or recorded. Proxy setUpClass skipped because Docker is unavailable. The migration negative-fixture FAIL text is expected and asserted by a passing test; it is not another failing test. ResourceWarning about an unclosed rehearsal helper file did not fail a test.

## Commands

Exact cwd, command, exit code and log paths are also stored in [commands.json](commands.json). `JAVA_HOME` and its bin directory were supplied to the command environment. No dependency installation or container startup attempted.

| cwd | Command | Exit | Evidence |
|---|---|---:|---|
| `.` | `python3 scripts/validate_blueprint.py` | 0 | [log](logs/validate_blueprint.txt) |
| `.` | `python3 scripts/check_scope.py` | 0 | [log](logs/check_scope.txt) |
| `.` | `python3 scripts/check_migration_compat.py` | 0 | [log](logs/check_migration_compat.txt) |
| `.` | `python3 -m unittest discover -s scripts -p 'test_*.py' -v` | 1 | [log](logs/python-unittest.txt) |
| `.` | `pnpm --config.engine-strict=false lint` | 0 | [log](logs/lint.txt) |
| `packages/api-client` | `pnpm --config.engine-strict=false test` | 0 | [log](logs/api-client-test.txt) |
| `packages/api-client` | `pnpm --config.engine-strict=false typecheck` | 0 | [log](logs/api-client-typecheck.txt) |
| `packages/design-tokens` | `pnpm --config.engine-strict=false test` | 0 | [log](logs/design-tokens-test.txt) |
| `packages/design-tokens` | `pnpm --config.engine-strict=false typecheck` | 0 | [log](logs/design-tokens-typecheck.txt) |
| `apps/customer-web` | `pnpm --config.engine-strict=false test` | 0 | [log](logs/customer-web-test.txt) |
| `apps/customer-web` | `pnpm --config.engine-strict=false typecheck` | 0 | [log](logs/customer-web-typecheck.txt) |
| `apps/operations-web` | `pnpm --config.engine-strict=false test` | 0 | [log](logs/operations-web-test.txt) |
| `apps/operations-web` | `pnpm --config.engine-strict=false typecheck` | 0 | [log](logs/operations-web-typecheck.txt) |
| `apps/customer-mobile` | `pnpm --config.engine-strict=false test` | 0 | [log](logs/customer-mobile-test.txt) |
| `apps/customer-mobile` | `pnpm --config.engine-strict=false typecheck` | 0 | [log](logs/customer-mobile-typecheck.txt) |
| `backend` | `./mvnw -o -B -pl app -am test -Dtest='MfaPolicyResolveTest,StaffAssuranceTest,ModuleBoundaryTest,MediaInspectorPdfTest,OrderFingerprintTest' -Dsurefire.failIfNoSpecifiedTests=false` | 0 | [log](logs/backend.txt) |

## Coverage and review

Updated requirement 1–12 NEW-test traceability and determinism review: [qa-test-plan.md](../../qa-test-plan.md). Findings: [qa-findings.json](../../qa-findings.json). QA8-001 and QA8-002 are Medium coverage deficiencies with concrete missed-regression scenarios, not demonstrated runtime production failures. All 12 requirements have some meaningful test coverage; required subrequirements lacking meaningful new tests are explicitly flagged (especially provider preservation/high-risk controls under DISABLED, provider logout on disable, actual factor removal/status UI, and running-realm upgrade/rollout/rollback).

The RANDOM_PORT correction and idempotent grant fixture address the previously identified test fixture issues statically. Reset refresh rejection, reset-epoch, profile-write, resolver, partial-reset-audit, session deletion/indexing and capability-panel tests improve coverage. Only resolver and JS tests among these were executed here. Shared policy mutation remains safe only under current sequential-method assumptions; no demonstrated parallel execution or race is claimed. TOTP retries/timing risks are recommendations, not observed flakes.

NOT_RUN: MfaPolicyIntegrationTest, MfaResetAuditIntegrationTest, MfaIdpTest, MfaResetIntegrationTest, IdentityIntegrationTest, backup/restore container suites, other container/API/browser/native checks. Required PostgreSQL/Keycloak/container environment is unavailable and owner explicitly excluded container execution here. Maven test compilation succeeding is not execution of these classes.

Next: execute mapped container suites against this exact source SHA on a suitable isolated runner, then add meaningful coverage for QA8-001/002 and the matrix's flagged subrequirements. No network publication, commit, merge or deployment performed.
