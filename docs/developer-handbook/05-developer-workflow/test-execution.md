# Test execution

> **Owner role:** Testing and QA
> **Status:** PARTIALLY IMPLEMENTED — unit, integration, contract, browser and native suites are IMPLEMENTED and run in CI; owner acceptance cases are specifications that are NOT_RUN until a run records evidence; commands here are NOT VERIFIED in the environment that wrote this page.
> **Language / اللغة:** English · [العربية](test-execution-ar.md)

Background: [01-verification.md](../../quality/01-verification.md) (levels and what each proves), [02-release-gates.md](../../quality/02-release-gates.md).

## Testing taxonomy (what proves what)

| Level | Command / location | Needs | Proves | Does not prove |
|---|---|---|---|---|
| Blueprint / planning | `python3 scripts/validate_blueprint.py`, `python3 scripts/check_scope.py` | Python | planning files are consistent | any product behaviour (its own output says so) |
| Script guardrails | `python3 -m unittest discover -s scripts -p 'test_*.py' -v` | Python (Docker images optional: proxy tests skip when absent) | deploy/guard scripts behave as tested | the deployed server |
| Backend unit + integration | `(cd backend && ./mvnw -B -ntp verify)` | JDK 21, Docker | domain rules against real PostgreSQL 16 and an S3 emulator (no H2, no mock DB) | browser or native usability |
| Architecture | `ModuleBoundaryTest` in `backend/app/src/test/java/com/matbakhy/app/architecture` | part of `verify` | module boundaries (other modules use only an `api` package; no cycles) | runtime behaviour |
| API contract | `pnpm contract:check` | Node, pnpm | `contracts/openapi.yaml` lints (Redocly) and the generated client has no drift | server really implements it (`ContractRoutesTest` in `backend/app/src/test/java/com/matbakhy/app/web` covers routes) |
| Web / package unit | `pnpm test` (Vitest, Jest for mobile) | Node, pnpm | component and logic behaviour | real API integration |
| Types and lint | `pnpm typecheck`, `pnpm lint` | Node, pnpm | static correctness | behaviour |
| Browser E2E (real API) | `scripts/dev.sh e2e` | Docker, JDK, Node, Chromium | customer, cook and ops workflows against a throwaway stack | native apps, real providers |
| Staging browser smoke | `pnpm --filter @matbakhy/operations-web e2e:staging` | a deployed preview and your allow-listed IP | a simulated deposit order on the preview | production readiness |
| Native | `.github/workflows/native-build.yml`, `apps/customer-mobile/scripts/*.sh` | Android SDK / macOS | compile + launch + sign-in + ordering recovery on emulator/simulator | physical-device behaviour, store readiness |
| Owner acceptance | `docs/quality/owner-acceptance/` | a preview or isolated stack, fixtures | specified manual cases, once executed and recorded | anything while NOT_RUN |

## Everyday commands

```bash
# JavaScript/TypeScript workspace
pnpm install --frozen-lockfile
pnpm typecheck && pnpm lint && pnpm test && pnpm build
pnpm contract:check

# Single package
pnpm --filter @matbakhy/customer-web test
pnpm --filter @matbakhy/operations-web test
pnpm --filter @matbakhy/api-client test
pnpm --filter @matbakhy/design-tokens test
pnpm --filter @matbakhy/customer-mobile test      # jest --ci

# Backend
(cd backend && ./mvnw -B -ntp verify)
(cd backend && ./mvnw -B -ntp verify -Dtest=ModuleBoundaryTest -Dsurefire.failIfNoSpecifiedTests=false)
```

`scripts/check_workspace.sh` does all of the above from a fresh clone of your committed `HEAD` (backend verify, contract check, typecheck, lint, tests, builds, Android and iOS Hermes bundles) and fails on a version mismatch. Uncommitted changes are not tested by it.

## Documentation gate tests

`python3 scripts/test_doc001_gate.py` exercises the DOC-001 gate (`scripts/doc001_check.py`) through its command line against throw-away git repositories: the 12 required positive/negative cases plus hardening cases (declaration hiding, whitespace-only edits, comment/approval handling, matrix coverage of every tracked file). It runs with the other `scripts/test_*.py` in the Blueprint integrity workflow. Run `python3 scripts/doc001_check.py docs` for the documentation validation alone.

`python3 scripts/test_validate_handbook_parity.py` exercises the Arabic-mirror validator (`scripts/validate_handbook_parity.py`) on throw-away handbooks: missing and orphan mirrors, heading, code-block, table, list, image, link and identifier drift, Mermaid label translation versus structural change, broken anchors, documented exceptions, the reviewed-pair record and the same-PR rule. It needs `markdown-it-py` (`python3 -m pip install 'markdown-it-py==3.0.0'`). It proves the structural checks work; it does not prove that any translation is semantically correct ([documentation maintenance](documentation-maintenance.md#arabic-mirror-synchronisation)).

`python3 scripts/test_branch_audit.py` tests the report-only branch audit (`scripts/branch_audit.py`, see [branch maintenance](branch-maintenance.md)).

## Proving that named tests really ran

```bash
scripts/check_backend_suite.sh ClassA ClassB
```

The script deletes old surefire reports, runs `verify -Dtest=...`, then fails if any named class did not run, ran zero tests, or had failures/errors. Use it when a story or defect names specific test classes: a renamed class is a failure, never a silent pass.

## Backend test conventions (from the code)

- Full-context tests use `@IntegrationTest`; helpers in `backend/app/src/test/java/com/matbakhy/app/support/` (`TestKitchens`, `TestCommerce`, `TestUploads`, `MutableClock`, `OidcBrowser`, `MfaBrowser`). Background jobs are disabled in that annotation so tests control time with `MutableClock`.
- Test packages mirror domains: `orders`, `payments`, `catalog`, `availability`, `cooks`, `identity`, `mfa`, `finance`, `support`, `notifications`, `privacy`, `security`, `reliability`, `observability`, `media`, `insights`, `providers`, `db`, `config`, `web`.
- Race conditions (last portion, capacity, duplicate submit) must use real concurrency against PostgreSQL, not sleeps ([01-verification.md](../../quality/01-verification.md)).
- Do not weaken an assertion, mock the database or delete a failing test to get green. This is a project rule ([CLAUDE.md](../../../CLAUDE.md)).

## Browser E2E (Playwright)

Specs: `apps/operations-web/e2e/*.e2e.ts` (`customer-journey`, `cook-workspace`, `money-reflow`, `keyboard`, `operations-layout`), helper `apps/operations-web/e2e/fixtures.ts`, config `apps/operations-web/playwright.config.ts` (one worker, trace and screenshot kept on failure). Run only through `scripts/dev.sh e2e`; it prepares a throwaway stack with random secrets and passes `E2E_*` variables. Never aim it at a shared or production-like environment; the suite resets and seeds data. Set `E2E_CHROMIUM` to use an installed Chromium. The script deletes its temporary directory (including the default Playwright output `$VERIFY_TMP/results` and the web server logs) on exit; to keep traces and screenshots set `E2E_OUTPUT_DIR` to a persistent, ignored path first, e.g. `E2E_OUTPUT_DIR="$PWD/test-results/e2e" scripts/dev.sh e2e`.

Staging smoke: `apps/operations-web/e2e-staging/smoke.staging.ts` with `playwright.staging.config.ts`; needs `STAGING_APP_URL`, `STAGING_OPS_URL`, `STAGING_API_URL` and `STAGING_USER_PASSWORD` (from the operator; never commit). It creates one demo order per run.

## Native tests

`apps/customer-mobile/scripts/` holds `launch-android.sh`, `auth-roundtrip-android.sh`, `order-roundtrip-android.sh` and iOS equivalents (XCUITest sources under `apps/customer-mobile/e2e-ios`, seed SQL under `apps/customer-mobile/e2e-native`). They are written for CI runners; running them locally needs an emulator/simulator and the local stack: NOT VERIFIED. Android and iOS evidence are separate and neither is replaced by a JS bundle.

## Owner acceptance catalog (200 cases)

`docs/quality/owner-acceptance/cases/*.json` hold 200 case definitions (counted: config-security 54, cook-ops 39, discovery-basket 45, orders-finance 62). The catalog README says 199 and 53 for config-security; the JSON is the source, so trust 200. Every case is NOT_RUN or BLOCKED; a PASS lives only in a run record.

```bash
python3 scripts/owner_e2e_catalog.py check     # sources valid and generated files not stale
python3 scripts/owner_e2e_catalog.py render    # regenerate docs/quality/owner-acceptance/generated
python3 scripts/owner_e2e_fixtures.py check    # manifest, SQL seed and case files agree
```

Read in this order: [owner-acceptance README](../../quality/owner-acceptance/README.md), `docs/quality/owner-acceptance/generated/readiness-report.md`, the ordered journeys J0-J8 in `docs/quality/owner-acceptance/generated/core-owner-acceptance-suite.md`, [fixtures.md](../../quality/owner-acceptance/fixtures.md), [owner-e2e-execution-guide.md](../../quality/owner-e2e-execution-guide.md). The owner guide in Egyptian Arabic is [owner-guide-ar.md](../../quality/owner-acceptance/owner-guide-ar.md); the latest recorded run is `docs/quality/owner-acceptance/run-2026-10-19.md`.

## Synthetic fixtures

Only synthetic actors exist: `customer-a/b`, `cook-a`, `ops-a` locally; `preview-customer-1/2`, `preview-cook-1`, `preview-ops-1/2` and the `owner-e2e-*` accounts on the preview. Passwords are never in the repository; generated files go to a private directory outside the checkout. Do not use real phone numbers, addresses or names anywhere, including screenshots.

## Evidence and defect handling

- Per-story evidence: `verification/runs/<date>-<story>/evidence.json` written by `scripts/record_evidence.py` against a clean committed source; `scripts/mark_story.py` refuses DONE without passing evidence and a PASS review. Schema checks: `python3 scripts/validate_blueprint.py`.
- Result vocabulary: PASS only for something executed with output; otherwise NOT_RUN, BLOCKED or FAIL. CI green does not mean "all scenarios passed" ([owner-e2e-execution-guide.md](../../quality/owner-e2e-execution-guide.md)).
- Report a defect with the GitHub issue template `.github/ISSUE_TEMPLATE/defect.md` (story/rule/scenario IDs, source commit, severity, synthetic fixture IDs, steps, expected, observed, sanitized logs, affected invariants).
- Fixing a defect: reproduce with a failing automated test first where feasible, repair, rerun the affected suite and the neighbouring ones, then cite the test in the PR. Regression coverage is part of the fix, not optional.

## Known limitations and documentation gaps

- Catalog README count (199) disagrees with the case files (200).
- No single command runs "everything"; `scripts/check_workspace.sh` skips browser, native, design and secret checks.
