# D18 independent QA execution

Tested source: `c139bdf3c03705f042388ad5d8bd0c1ccf945a1d`; HEAD matched before execution. No application or existing test changes. Evidence-only files live in this qa7 directory; no commit/push performed.

Requirement-to-NEW-test mapping and quality review: [qa-test-plan.md](../../qa-test-plan.md). Findings: [qa-findings.json](../../qa-findings.json). QA disposition: changes requested for fixture reliability and false-negative gaps; requirement completion is not established by local unit results.

| Executed check | Result |
|---|---|
| python3 scripts/validate_blueprint.py | PASS (planning only) |
| python3 scripts/check_scope.py | PASS (planning only) |
| python3 scripts/check_migration_compat.py | PASS static compatibility check, not database migration execution |
| python3 -m unittest discover -s scripts | FAIL: 73 tests, 1 failure, 1 skip; host .aws/.codex fixture caveat below |
| customer-mobile pnpm test / typecheck | PASS: 55 tests; typecheck exit 0 |
| customer-web pnpm test / typecheck | PASS: 53 tests (five new D18); typecheck exit 0 |
| operations-web pnpm test / typecheck | PASS: 30 tests (four new D18); app and E2E typecheck exit 0 |
| api-client pnpm test / typecheck | PASS: 34 tests; typecheck exit 0 |
| design-tokens pnpm test / typecheck | PASS: 47 tests; typecheck exit 0 |
| pnpm --config.engine-strict=false exec eslint . | PASS exit 0 |
| Offline backend selected tests | PASS: 23, zero failures/errors/skips |
| MfaPolicyIntegrationTest, MfaIdpTest, MfaResetIntegrationTest | NOT_RUN: Docker/Testcontainers/Keycloak-container tests excluded here by owner/environment |

Each JS command ran from its package directory with `pnpm --config.engine-strict=false run test` / `run typecheck`, no install/network. Total JS: 219 passing. Node v22.23.3 differs from repository pinned v24.21.0; pnpm 10.34.6. This verifies the available runtime, not clean pinned-toolchain installation. ESLint ran at repository root. Full logs in [logs](logs).

Backend executed from backend with `JAVA_HOME=/home/aiagent/tools/jdk-21.0.12.1+1` and its bin prepended to PATH:

```
./mvnw -o -B -pl app -am test -Dtest='MfaPolicyResolveTest,StaffAssuranceTest,ModuleBoundaryTest,MediaInspectorPdfTest,OrderFingerprintTest' -Dsurefire.failIfNoSpecifiedTests=false
```

Counts: MfaPolicyResolveTest 5; StaffAssuranceTest 3; ModuleBoundaryTest 7; MediaInspectorPdfTest 5; OrderFingerprintTest 3. Maven BUILD SUCCESS, reactor 12 modules, 55.140 seconds. Surefire summary is saved in logs/backend-summary.txt (transcribed from command output; selected counts extracted from Surefire XML without retaining properties).

Python first run: 73 tests, one failure, one skip. ScopeGuards.test_current_repository_passes copies the host .aws and .codex directories (not read for this review), treating them as unlisted paths in its temporary repository. My initial qa/ evidence directory was also copied and reported; it was moved into the existing docs tree and empty directories removed before the final rerun. No host directories were removed or inventory exceptions added. Negative migration-fixture FAIL text is expected test output; final unittest status governs.

CI is the required execution venue for container coverage. No run URL/result for this source SHA was supplied, and no network is allowed; no CI PASS is asserted. .github/workflows/ci.yml invokes scripts/check_workspace.sh, which runs backend verify, but scheduling a suite is not proof it passed. Obtain exact-SHA CI Surefire results and investigate QA7-001 there.

The cloud runtime skill was read as required; no callable environment_status tool was exposed. No credential values were inspected or printed. No Docker/network operations attempted.

Next actions: isolate reset administrator fixtures; add fresh-migration defaults and real invalidation assertions; run exact-SHA container CI including the new tests. Other missing negatives are recommendations in the plan, not reproduced product defects.
