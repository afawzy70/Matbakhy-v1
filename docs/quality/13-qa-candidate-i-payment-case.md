# QA candidate I: automatic payment support case, outbox retry rehearsal, 2026-10-14

Implementation-agent handoff. Every result below is self-review by the implementation agent, not independent review.

## Candidate

- **Immutable candidate: `be33bc2de0d30366f50767dfe1ee35af2737d434`** on `implementation/mvp`.
- The six local checks ran at this SHA from a clean worktree with fresh synthetic data ([evidence](../../verification/runs/20261014-CANDIDATE-I/evidence.json)).
  - BACKEND-VERIFY, WORKSPACE-CLEAN-CHECKOUT, LOCAL-STACK, BROWSER-E2E (6/6), SECRET-SCAN (356 commits plus the committable tree) and GUARDRAIL-TESTS: all PASS.
- Candidate I includes everything in candidate H.
- MBQA-003, MBQA-004, MBQA-005, MBQA-006 and OBS-001 stay **FIXED_PENDING_INDEPENDENT_RETEST**.

## Delta since candidate H (`1850f7b`)

| Area | Change | Commits |
|---|---|---|
| MB-052 follow-up | A deposit the kitchen reports as not received (DISPUTED), a payment left to reconcile after the order ended (RECONCILE_PENDING), or a refund obligation (REFUND_OWED) opens a HIGH support case of category PAYMENT for the order. At most one is open per order, also under retries and concurrent changes. The case text names no account, transfer reference or amount. Customers cannot open PAYMENT cases themselves. Staff resolve them by hand | `863edf0` (migration V28) |
| MB-039 | Staff retry of a dead-lettered outbox event: denied without `integration.ops` (audited), audited with the reason when allowed, one request ID in the response, audit row and a new log line; same-key replay writes nothing; second retry 409 | `22b5f73` |
| Docs | TC-139 coverage review; preview runtime-role note for V27 | `a923d96` |
| Native harness | Report-only `EDGE-REPORT` lines on every large-text screenshot (test code only) | `d9b0873` |

## What to retest independently

1. **Payment case:**
   - Use a deposit order with a synthetic approved recipient. The customer reports the transfer, and the kitchen reports "not received".
   - Expected: the customer's support list shows one case «مراجعة العربون» / "Deposit review". Support staff see it in the case queue. Another customer does not.
   - Then the kitchen rejects the order, and finance records "refund owed". Expected: still one open case for that order.
   - Resolve the case as staff. A later dispute on the same order opens a new one.
2. Everything in the handoffs for [H](12-qa-candidate-h-erasure-native.md), [G](11-qa-candidate-g-insights-followups.md) and [F](10-qa-candidate-f-deposit-payments.md).

## Known gaps

- TC-057, TC-139 and the deployed checks AUD-01, LOG-01 and OPS-01..03 need an authorized preview or staging environment (MB-040).
- Retention periods are O06.
- Push (O07), screen readers (BLK-003) and physical devices (BLK-004) are open.
- The large-text screenshots have no visual review yet: the artifacts cannot be downloaded from this environment.
- Live money is not approved.
