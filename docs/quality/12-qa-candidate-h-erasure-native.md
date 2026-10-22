# QA candidate H: erasure on append-only history, verified backups, full native pass, 2026-10-13

Implementation-agent handoff. Every result below is self-review by the implementation agent, not independent review.

## Candidate

- **Immutable candidate: `1850f7b8ba8e19315753b0776ee3c0be5d21035b`** on `implementation/mvp`.
- The six local checks ran at this SHA from a clean worktree with fresh synthetic data ([evidence](../../verification/runs/20261013-CANDIDATE-H/evidence.json)).
  - BACKEND-VERIFY, WORKSPACE-CLEAN-CHECKOUT, LOCAL-STACK, BROWSER-E2E (6/6), SECRET-SCAN (348 commits plus the committable tree) and GUARDRAIL-TESTS: all PASS.
- **Native run 48** ([38421819591](https://github.com/afawzy70/Matbakhy-v1/actions/runs/38421819591)) at `f05e3d6` has the same app source: **Android and iOS every phase PASS**, including the MB-053 deposit journey on both. iOS covered phases A–K, among them sign-in, recovery, limits, delivery, deep links, D13, largest text in English and Arabic, and notification refusal.
- Candidate H includes everything in candidates F and G.
- MBQA-003, MBQA-004, MBQA-005, MBQA-006 and OBS-001 stay **FIXED_PENDING_INDEPENDENT_RETEST**.

## Delta since candidate G (`c056e4a`)

| Area | Change | Commits |
|---|---|---|
| Account erasure (defect found today) | A customer who had replied on a support case could not be erased: the append-only trigger aborted the whole erasure. A deposit transfer reference typed by the customer stayed in the payment history. V27 allows exactly these two erasure changes. Every other update, any delete and truncate still fail, and the kitchen's and staff's history is untouched | `241a166` (migration V27) |
| Backups (BR-057) | The web-preview backup counts as "last good backup" only after a restore into a scratch database and reconciliation. The alert reads "No verified database backup in 24 hours" | `063bda6` (`deploy.sh`, guardrail `scripts/test_preview_backup.py`) |
| TC-055 / TC-056 | Erasure keeps a completed order's ledger balanced and unchanged. Logs and metrics hold no document content, deposit recipient, transfer reference or activity visitor ID | `86de26c`, `0a94978` |
| MB-053 | DONE: the deposit journey passed on the Android emulator and the iOS simulator | evidence `20261013-MB-053` |
| iOS UI-test harness | BLK-005 resolved (XCTest interruption monitor for "Save Password?"; sign-in real) | `f05e3d6` |

## What to retest independently

1. **Erasure:**
   - Use a synthetic customer with a support case who wrote a reply, and a deposit order with a transfer reference.
   - Request account deletion and let the erasure run (local: move `deletion_request.requested_at` back past the grace period).
   - Expected: erasure completes. The customer's replies read «[erased on account deletion]», and their transfer reference is gone. The kitchen's receipt reference and the staff notes stay.
2. **Backup gate (operator):** `deploy/web-preview/deploy.sh backup` against a preview stack. A failed restore leaves no backup-time metric.
3. Everything in the handoffs for [F](10-qa-candidate-f-deposit-payments.md) and [G](11-qa-candidate-g-insights-followups.md).

## Known gaps

- TC-057 (staging restore with measured RPO/RTO) and the deployed checks AUD-01, LOG-01 and OPS-01..03 need an authorized preview (MB-040).
- Retention periods are O06; the fixture policy is not approved.
- Push (O07), screen readers (BLK-003) and physical devices (BLK-004) are open.
- Live money is not approved.
