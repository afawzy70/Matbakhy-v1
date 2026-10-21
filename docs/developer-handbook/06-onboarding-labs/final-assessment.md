# Final assessment

> **Owner role:** Handbook navigation
> **Status:** REFERENCE — checklist and scoring rubric that decide whether a new developer is ready to work independently.
> **Language / اللغة:** English · [العربية](final-assessment-ar.md)

The assessor is a maintainer (not the candidate). The candidate works without an AI assistant, using only the repository and the handbook. Every item needs the evidence listed; claims without evidence score 0. Use synthetic data only.

## Scoring

Each of the ten criteria is scored 0-3.

| Score | Meaning |
|---|---|
| 0 | not demonstrated, or evidence missing, or an invariant was violated |
| 1 | partly correct; needed significant help or corrections |
| 2 | correct with minor gaps fixed during review |
| 3 | correct, complete and independently verifiable |

**Pass:** total at least 24 of 30, no criterion below 2, and criteria 6 (write and run tests), 8 (compliant PR) and 10 (DOC-001) each at least 2. **Conditional:** 20-23 with no zeros: repeat the failed items within a week. **Not yet:** anything else. A violated invariant (committed secret, edited `main`, unreviewed merge, PASS claimed without a run) is an automatic Not yet.

## Criteria

### 1. Domain understanding

Pass criteria (score 3): states the actors and what each may do; recites the invariants (EGP integer piastres, server-owned price/capacity/roles/transitions, one cook and one slot per order, immutable snapshots, idempotency, object-level authorization, zero commission and no platform fee in the MVP, cash on receipt or manual kitchen-reported deposit, never "bank verified", Arabic RTL and English LTR); correctly separates MVP from deferred scope.
Evidence: 30-minute oral Q&A recorded in the assessor's notes; Day-1 note; answers match [business rules](../01-business/business-rules.md) and [implementation status](../07-reference/implementation-status.md).

### 2. Explaining the customer, kitchen, mobile and back-office flows

Pass: walks one order from discovery to completion naming each screen/surface, actor, state change and the API calls behind it, and states what differs on mobile and in the back office (refunds, support cases, cash reconciliation, audit).
Evidence: live walkthrough on a local stack or the preview with synthetic users; consistent with [complete order journey](../02-user-journeys/complete-order-journey.md).

### 3. Codebase navigation

Pass: within 10 minutes locates, for three assessor-chosen features, the contract operation in `contracts/openapi.yaml`, the controller, the service, the migration/table and the tests.
Evidence: file paths written down for each of the three, checked by the assessor with `grep`.

### 4. Run and debug

Pass: starts the stack from scratch (`scripts/dev.sh up`, `smoke`), runs both web apps, reads API logs by request id, queries the database for a migration state and a given order, and resolves one injected fault (for example a busy port or a wrong env file).
Evidence: terminal transcript or screen recording; `scripts/dev.sh smoke` ends with PASS; the fault is explained with the right command.

### 5. Locate the implementation of a business requirement

Pass: given a rule ID (for example BR-018, BR-019 or BR-020) finds the rule text in `planning/rules.json`, the owning story, the covering scenarios, the code that enforces it and the test that proves it.
Evidence: a table rule to story to scenario to code path to test, verified by the assessor; flags any rule whose enforcement is only in the client.

### 6. Write and run tests

Pass: adds a failing test then a passing one for an assessor-chosen behaviour at the right level (unit, component, integration); uses real PostgreSQL for backend integration; runs the named test with `scripts/check_backend_suite.sh` or the package test command; explains why the test would catch a regression.
Evidence: commit with the test; output showing red then green; no mocks of the database, no weakened assertions.

### 7. Handle a defect independently

Pass: from an issue written with the defect template (`.github/ISSUE_TEMPLATE/defect.md`) reproduces, isolates the cause, fixes it, adds a regression test, reruns neighbouring suites, and records evidence; stops after three materially different failed attempts and writes a specific blocker instead of guessing.
Evidence: issue or practice branch history; reproduction steps; regression test; list of commands run with results.

### 8. Submit a compliant PR

Pass: branch from `develop` with a conventional name; scoped commits; PR uses every section of `.github/pull_request_template.md`; verification lists exact commands, results and NOT RUN items; no independent-review claim; CI checks read and acted on; no merge by the candidate; no `main` changes.
Evidence: the Day-5 PR; `gh pr checks` output; assessor checklist against [git and PR workflow](../05-developer-workflow/git-and-pr-workflow.md).

### 9. Understand preview deployment

Pass: explains the path develop to Product CI to image build by commit SHA to digest-pinned deploy to smoke; what the preview is and is not; the IP allow-list classes; what is rolled back automatically (configuration) and what is not (database); how backups and `rollback-plan` work; how secrets are kept out of the repository; what owner E2E provisioning does and why it uses a request file.
Evidence: oral or written answers checked against [CI/CD](../05-developer-workflow/ci-cd.md) and [preview environment](../05-developer-workflow/preview-environment.md); candidate runs `deploy/web-preview/smoke.sh` read-only (or states why not allow-listed).

### 10. Follow DOC-001

Pass: identifies the handbook pages affected by the PR using `docs/developer-handbook/doc001-matrix.json`; updates them accurately (labels IMPLEMENTED/PLANNED/NOT VERIFIED used correctly, paths real); `python3 scripts/doc001_check.py docs` passes; the declaration block is valid and honest; knows the script proves structure, not accuracy.
Evidence: diff of handbook pages; checker output; declaration text; assessor spot-checks three claims in the changed pages against code.

## Score sheet

| # | Criterion | Score 0-3 | Evidence reference | Assessor notes |
|---|---|---|---|---|
| 1 | Domain understanding | | | |
| 2 | Explain the flows | | | |
| 3 | Codebase navigation | | | |
| 4 | Run and debug | | | |
| 5 | Locate a requirement | | | |
| 6 | Write and run tests | | | |
| 7 | Handle a defect | | | |
| 8 | Compliant PR | | | |
| 9 | Preview deployment | | | |
| 10 | DOC-001 | | | |
| | Total (max 30) | | | |

Record the outcome with the assessor's name and the date in the team's tracker (not in the repository if it contains personal information). Documentation gaps found during the assessment become issues or handbook fixes under DOC-001.
