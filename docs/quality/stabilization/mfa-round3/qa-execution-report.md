# D18 round 3 final independent QA

FINAL source SHA under test: `7e61db71e8a089e52d3bae9ffacdf7022907b2cd`. HEAD verified equal before execution and during reporting. Date: 2026-10-18. Reports are uncommitted to preserve the requested source HEAD. No application/test source modified; all task artifacts are under this directory.

Environment: isolated workspace-write, no network; Node 22.23.3 (repository pin is 24.21.0), pnpm 10.34.6 with engine-strict=false; JDK `/home/aiagent/tools/jdk-21.0.12.1+1`; Maven offline. Existing dependencies only. The runtime-status tool requested by the cloud skill was not exposed; no credentials inspected or network readiness inferred.

Evidence: [exact commands and exit codes](commands.json), [logs](logs), [requirement 1–12 matrix](../qa-test-plan.md), [machine-readable findings](../qa-findings.json), and log hashes (digest manifest omitted: Gitleaks false positive). Commands ran in up to four concurrent local processes, without agent delegation.

Three validators PASS. JavaScript: api-client 34, design-tokens 47, customer-web 53, operations-web 31, customer-mobile 55 tests PASS (220 total). All five package typechecks and root ESLint PASS. Mobile JS tests do not establish native verification. Available Node differs from the pin, so these results do not establish a clean pinned-toolchain build.

Python discovery: exit 1, 73 reported tests, 71 passed, one failed, one skipped Docker class. The failure is `test_scope.ScopeGuards.test_current_repository_passes`, scripts/test_scope.py:43: the copied host fixture includes top-level `.aws` and `.codex`. Direct `python3 scripts/check_scope.py` passes. This is an environment-induced BLOCKED check, not an MFA defect; no secret directory contents were inspected or recorded. Docker proxy setUpClass is skipped because Docker is unavailable. Expected negative-migration-fixture FAIL text is asserted by passing tests; the ResourceWarning in rehearsal TLS is non-fatal. No filtering/retry used to conceal the original discovery failure.

Offline backend suite: PASS, 25 tests, zero failures/errors/skips: MfaPolicyResolveTest 6; StaffAssuranceTest 4; ModuleBoundaryTest 7; MediaInspectorPdfTest 5; OrderFingerprintTest 3. Maven exits 0 (BUILD SUCCESS).

## Final disposition

No demonstrated application defect from the executed checks. QA9-001 is a Low test-coverage deficiency: the reset attempt audit is asserted after failure and does not prove ordering/durability before destructive work. Production ordering is currently correct by inspection. Remaining requirement gaps and their risks are detailed per row in the matrix, including account-console lifecycle, real cookie/refresh invalidation, fresh migration isolation and rollout/rollback rehearsal. They are recommendations for additional evidence, not changed owner requirements or demonstrated runtime failures.

QA8-001 is substantially addressed by P:317 (DISABLED retains high-risk controls) and A:116 (provider double sees no deletion); real provider/admin-transition preservation remains a coverage recommendation. QA8-002's omitted-logout-call gap is addressed by A:116; real provider semantics and cookie reuse after disable/reset remain integration gaps. These new container tests have not been executed locally. The new StaffAssuranceTest:56 reset-proof unit test is part of the requested offline execution.

NOT_RUN locally: MfaPolicyIntegrationTest, MfaResetAuditIntegrationTest, MfaIdpTest, MfaResetIntegrationTest, IdentityIntegrationTest, other container-dependent database/provider/backup suites, full browser E2E and native Android/iOS checks. CI is the evidence source for container suites. `.github/workflows/ci.yml` invokes scripts/check_workspace.sh, which runs backend Maven verify; configuration proves test routing, not a successful run. No exact-SHA CI run ID/result was supplied or available offline. Require that evidence before full D18 acceptance; compilation of integration tests does not count as execution. Accepted residual risks named by the owner are not repeated as findings.

No commit, merge, push, deployment or live realm change performed. Next action: attach successful CI results at the FINAL source SHA for the mapped container suites and review the remaining coverage risks. Local QA completion does not establish release approval.
