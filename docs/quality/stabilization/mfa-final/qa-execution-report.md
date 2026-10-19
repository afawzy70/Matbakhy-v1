# FINAL QA10 result

FINAL source SHA under test: `240cf0c08fef1fa92bb65f2886a938cf9401d04a`. HEAD verified equal before execution and after checks on 2026-10-18. Independent QA role; no delegation or application/test changes. Evidence remains uncommitted to preserve requested HEAD.

Three validators PASS. JavaScript tests PASS: api-client 34, design-tokens 47, customer-web 53, operations-web 31, customer-mobile 55 (220 total). Five per-package typechecks and root ESLint PASS. Specified offline backend unit suite PASS: 25 tests, zero failures/errors/skips (resolver 6, staff assurance 4, boundaries 7, PDF 5, fingerprint 3); Maven BUILD SUCCESS.

Python discovery exits 1: 73 tests reported, one failure, one skipped Docker class. `ScopeGuards.test_current_repository_passes` fails at scripts/test_scope.py:33 because the copied host fixture contains unlisted `.aws` and `.codex` directories. Direct check_scope passes. Discovery is BLOCKED by this documented host caveat, not PASS or an MFA defect. No secret contents inspected or printed; no filtering or retry to conceal failure. Negative migration-fixture FAIL output is expected by passing tests.

Environment: isolated workspace-write, no network; existing dependencies; Node 22.23.3 versus repository pin 24.21.0, pnpm 10.34.6 with engine-strict=false; JDK `/home/aiagent/tools/jdk-21.0.12.1+1`; Maven offline. These results do not establish verification on the pinned Node version. Cloud runtime status tool was unavailable.

Container tests NOT_RUN locally as instructed, including policy/provider/reset/session integration. Exact-SHA CI results were not supplied or checked offline. No full acceptance or native-device PASS is claimed.

No new evidence-based defect found (qa-findings.json is empty). Prior QA9-001 audit ordering coverage was updated: the provider double now queries attempt rows at its first call; source inspection only, container execution NOT_RUN. Existing documented residual risks are not repeated as findings.

Exact commands, working directories, exit codes and durations: commands.json. Raw execution evidence: logs/. No hash manifests written. Next action: obtain exact-SHA container/CI evidence and resolve the host fixture caveat before claiming complete verification.
