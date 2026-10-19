# Matbakhy owner acceptance testing — execution playbook

**Status: PLAN / NOT_RUN.** This catalog is a test design only. No outcome may be marked PASS without executing and retaining evidence.

## Start here
- Catalog: [owner-acceptance/README.md](owner-acceptance/README.md) → `generated/core-owner-acceptance-suite.md` (run first), `generated/readiness-report.md` (what can be run now), `generated/traceability-matrix.md`.
- Fixtures: [owner-acceptance/fixtures.md](owner-acceptance/fixtures.md).
- Existing sources: [scenario catalog](../../planning/scenarios.json), [roles](../product/06-permissions.md), [state machines](../product/04-state-machines.md), [MFA policy](../security/mfa-policy.md), [preview deployment](../operations/06-web-preview.md).

## Test environments
| Environment | Location | Scope |
|---|---|---|
| Preview web/API/identity | `https://{app,ops,api,id}.<PREVIEW_DOMAIN>` (the current host is in the server `.env`; do not copy it into reports) | Deny-by-default: only the allow-listed customer/staff IPs reach it. Synthetic data only. Non-destructive cases. |
| Keycloak administration | Not published; only through the container (`kcadm-in-container.sh`) or an authenticated SSH tunnel | **Never** expose `/admin` or the master realm. |
| Isolated stack | Disposable Docker Compose namespace | Races, fault injection, REQUIRED/DISABLED policy changes, MFA resets, destructive and reboot drills. |
| Physical iPhone/Android | Signed build | Native step-up, account switch, background/return. CI simulator runs are not equivalent. |

The preview may be older than the repository: read the deployed release with `./deploy.sh status`, and run `./migrate-mfa.sh status` before any MFA case. Cases flagged *needs updated deploy* require the PR #9 release **and** the owner-approved realm migration.

## Order of execution
1. Provision fixtures; run J0 (records the source SHA and image digest used everywhere else).
2. Run J1–J4 (the order journeys) and J5–J6, then the remaining cases by readiness bucket.
3. After the updated deployment and migration: J7 (MFA). Isolated-stack cases on the isolated stack only.
4. Physical devices: J8.

## Single test execution template
Record one result per case in `verification/runs/<run-id>/owner-results.md` or your QA tracking system. Example:

```text
TEST_ID=E2E-ORDER-101
STATUS=NOT_RUN|PASS|FAIL|BLOCKED
SOURCE_SHA=<40-hex SHA>
DEPLOYED_IMAGE_DIGEST=<sha256>
ENV=isolated|preview|physical-iphone
ACTOR=synthetic-customer-a
PRECONDITIONS=<fixture reference, no credentials>
STEPS_RUN=<exact clicks/API requests and inputs>
EXPECTED=<from case>
ACTUAL=<observed>
EVIDENCE=<sanitized screenshot/log path>
DEFECT=<GitHub issue or none>
TESTED_AT=<ISO8601>
```

## Result discipline and regression
- Never claim "all scenarios passed" from successful CI alone. Link actual test IDs to CI jobs or manual evidence.
- Any FAIL opens a tracked defect with reproduction, actual versus expected, source SHA, severity, and an automated regression where feasible.
- Re-run relevant tests after fixes, and run the full core journey suite again before owner approval.
- Tests that require missing signing, unavailable paid integrations, or unperformed live realm migration remain BLOCKED/NOT_RUN.
- Before any live configuration change take a verified backup, record previous policy, and use the documented rollback procedure. Never disable authorization or expose Keycloak admin publicly to simplify tests.

## Completeness review
The authored catalog covers primary positive flows, negative boundary tests, role-based access, payment and pricing integrity, order state machine edges, config combinations and rollback/recovery. It cannot truthfully enumerate every possible input combination; QA should extend cases as new branches, configurations and defects are discovered. Coverage is measured against the canonical product requirements and `planning/scenarios.json`, not by case count alone.
