# Matbakhy owner acceptance testing — execution playbook

**Status: PLAN / NOT_RUN.** This catalog is a test design only. No outcome may be marked PASS without executing and retaining evidence.

## Start here
- [Functional customer/cook/ops journeys](owner-e2e-functional-cases.md)
- [Keycloak, MFA, security and deployment configurations](owner-e2e-configuration-security-cases.md)
- Existing [scenario acceptance catalog](../../planning/scenarios.json), [role matrix](../product/06-permissions.md), [state machines](../product/04-state-machines.md), [MFA policy](../security/mfa-policy.md), [preview deployment](../operations/06-web-preview.md).

## Test environments
| Environment | URL/location | Permitted scope |
|---|---|---|
| Public customer | https://app.167-86-90-174.sslip.io | Read-only browsing, synthetic account UI tests and approved non-destructive demo orders |
| Public operations | https://ops.167-86-90-174.sslip.io | Public login surface; only authenticated authorized staff can mutate business state |
| Public API | https://api.167-86-90-174.sslip.io | API contract/auth checks, conservative traffic |
| Public realm | https://id.167-86-90-174.sslip.io/realms/matbakhy | OIDC login, discovery, step-up, permitted account actions |
| Keycloak administration | Restricted; authenticated SSH tunnel to operator endpoint | **Never publish** /admin or master realm to public Internet |
| Isolated test stack | CI or dedicated disposable Docker Compose namespace | Race conditions, destructive rollback, outage injection, MFA policy matrices, synthetic state reset |
| iOS/Android devices | Signed device build only | Physical-device testing; CI simulator evidence is not equivalent |

Do not assume the stabilization PR or its MFA realm migration is deployed. Read the deployed release SHA from `deploy.sh status` and verify Keycloak with `migrate-mfa.sh status` before attempting MFA tests.

## Synthetic fixture matrix
| Actor | Data needed | Permissions |
|---|---|---|
| Guest | No session | Public discovery only |
| Customer A / Customer B | Different baskets, orders, delivery zones | Own records only |
| Cook A / Cook B | Two separately approved kitchens, slots, meals | Own kitchen only |
| Pending and suspended cook | Distinct applications, existing accepted orders | Must not gain new commerce permissions |
| Support | Assigned/unassigned cases | Case-scoped support |
| Operations | Moderation/cook approval | Only explicitly granted ops permissions |
| Finance | Synthetic cash/deposit/refund cases | Finance grants, no implicit admin |
| Admin A / Admin B | Distinct grants and TOTP factors | Policy management/reset permissions and recent MFA |
| Multi-role identity | Customer + cook + staff grants | Strongest applicable MFA policy |

Never put fixture passwords, OTP seeds, cookies, tokens or private financial data into GitHub. An operator should share test credentials by a secure private channel.

## Five-pass recommended manual test order
1. **Read-only baseline:** Record deployed SHA and successful HTTPS access; exercise guest browsing and role boundaries (no mutations).
2. **Primary happy path:** Sign in Customer A → browse and quote → place synthetic pickup/delivery order → Cook A accepts/prepares/completes → Customer sees final order → Finance/Admin inspects synthetic reconciliation.
3. **Business invariants:** Stale quote after repricing, immutable placed price, quantity/capacity limits, cancel/reject/expire, deposit remaining cash, same request ID retry.
4. **Identity/configuration matrix:** OPTIONAL/DISABLED/REQUIRED across customer/kitchen/staff, own MFA on/off, cross-role priority, recent step-up, admin reset, identity switching; **run configuration mutations in isolated environment first**.
5. **Reliability and real devices:** Browser sessions (mobile Safari + desktop Chrome), native iOS/Android physical builds when available; network interruption, state recovery, safe retry; staging release/backup drills.

## Single test execution template
Record one result per case in `verification/runs/<run-id>/owner-results.md` or your QA tracking system. Example:

```text
TEST_ID=E2E-ORDER-001
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
