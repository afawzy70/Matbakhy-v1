# Owner acceptance catalog (reworked PR #12)

**Everything here is a test specification. Every case is `NOT_RUN` (or `BLOCKED` with a reason). Nothing is PASS until a run records evidence.**

| File | What it is |
|---|---|
| `cases/*.json` | The only hand-edited sources: 199 cases (discovery/basket 45, orders/finance 62, cook/ops 39, config/security 53). Each has exact steps, expected UI/API/database outcomes, fixtures, traceability and readiness flags. |
| `core-suite.json` | The ordered core owner acceptance journeys J0–J8 (Customer → Kitchen → Operations/Finance/Admin). |
| `coverage-notes.json` | Planning scenarios (TC) that no manual case covers, each classified (`PROCESS_GATE`, `AUTOMATED_ONLY`, `OWNER_UAT`, `GAP`) with the reason. |
| `fixtures.json`, `fixtures.md` | The synthetic actors and how to provision them without exposing credentials. |
| `generated/` | Rendered from the sources by `python3 scripts/owner_e2e_catalog.py render`: per-domain case documents, `core-owner-acceptance-suite.md`, `readiness-report.md`, `traceability-matrix.md`, `traceability.json`. `check` fails when they are stale, a case references an unknown TC/BR/fixture, carries boilerplate, looks like a credential or has a status other than NOT_RUN/BLOCKED. |

## Where to start
1. Read `generated/readiness-report.md`: it says which cases can be run now, which need extra fixtures, the updated deployment (PR #9 + owner-approved realm migration) or a physical device, and which only an isolated stack may run.
2. Provision fixtures (`fixtures.md`), then run `generated/core-owner-acceptance-suite.md` in order.
3. Record each result (template in `../owner-e2e-execution-guide.md`) under `verification/runs/<run-id>/owner-results.md` with sanitized evidence. A case is PASS only with the source SHA, the deployed image digest and the evidence of that run.

## What the rework changed
See `generated/` for the cases and `generated/review-findings.md` for the defects found in the original PR #12 text (unsupported features, wrong expectations, unsafe steps, duplicates, missing coverage) and what was done about each.
