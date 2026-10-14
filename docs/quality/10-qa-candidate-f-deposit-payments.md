# QA candidate F: zero commission, direct deposits (D10/D16) and MBQA-006, 2026-10-13

Implementation-agent handoff. Every result below is self-review by the implementation agent, not independent review.

## Candidate

- **Immutable candidate: `ec68cb94dc9eaf749958510b587d48a810b38327`** on `implementation/mvp`. The six local checks ran at this SHA from a clean worktree with fresh synthetic data ([evidence](../../verification/runs/20261013-CANDIDATE-F/evidence.json)).
- Native evidence comes from neighbouring SHAs with the same app source:
  - Run 43 at `ae9fdbf`: between `ae9fdbf` and the candidate, nothing changed under `apps/customer-mobile`, `backend`, `contracts` or `packages`.
  - Run 44 at `5efc139`: differs from the candidate only in two iOS UI-test files (the harness).
- Candidate E `bc305c6` stays valid for its own scope (D13/D14). MBQA-003, MBQA-004, MBQA-005 and OBS-001 stay **FIXED_PENDING_INDEPENDENT_RETEST**; MBQA-006 joins them.

## Six local checks at the candidate

| Check | Result | Notes |
|---|---|---|
| BACKEND-VERIFY | PASS | Full Maven verify on PostgreSQL, including ZeroCommission, DepositPayment and PaymentReconciliation integration tests |
| WORKSPACE-CLEAN-CHECKOUT | PASS | Clean install, typecheck, lint, all workspace tests (incl. the brand guard TC-103) |
| LOCAL-STACK | PASS | `scripts/dev.sh verify` |
| BROWSER-E2E | PASS | 6/6 workflows on the real local stack, including `money-reflow.e2e.ts` (12 variations) |
| SECRET-SCAN | PASS | 312 commits scanned plus the committable tree; no leaks |
| GUARDRAIL-TESTS | PASS | `scripts/test_*.py` |

## Delta since candidate E (`bc305c6`)

| Area | Change | Commits |
|---|---|---|
| Owner decisions | `main` (D10 update, D16, D17) merged into the branch without rebase; change matrix in `docs/delivery/08-change-matrix-2026-10-13.md` | `39856f6` |
| MB-050 (D10) | Launch policy 0% commission. The server refuses a non-zero commission for any policy it accepts, and the database allows a non-zero commission only on synthetic test fixtures. Statements and ops finance show "no platform commission" | `50b056e` (planning), `07d7236` |
| MB-051 (D16) | Quotes carry payment terms: cash on receipt (if eligible) or a deposit paid directly to the kitchen's approved account. The order snapshots the terms (immutable). The payment state is separate from the order. The kitchen cannot start preparing before it reports receipt | `61d8dd5` |
| MB-052 (D16) | Staff approve a kitchen's recipient with recent MFA, and a changed recipient needs a new approval. The kitchen reports receipt, not received, or a wrong amount; problem reports go to the finance queue as DISPUTED. Refund owed and refunded. No staff action can mark money received. Cook and finance screens | `bd15329`, `9e3ed88`, `ec68cb9` |
| MB-053 (D16) | Customer web, Android and iOS: choose cash or deposit; the quote shows the deposit, remainder, recipient, deadline and that the platform never holds the money; the order shows the payment state and "I sent the deposit" | `6d5d2d0`, `6611e69`, `49a55f1` |
| MBQA-006 | Staff screens: money amounts no longer break inside the number at 320/390 px. CSS only | `f8a0087` ([record](../../verification/runs/20261013-MBQA-006/README.md)) |

## Native results (implementation agent)

| Phase | Android emulator, run 43 | iOS simulator |
|---|---|---|
| Sign-in round trip, MB-032 regression, delivery, quantity limits, deep links, same-session limit, D13 | PASS | Run 43: FAIL at the first sign-in (iOS "Save Password?" prompt, harness). Run 44 at `5efc139`: see `docs/status/latest.md` |
| Largest text (EN and AR), notification refusal (MB-033) | PASS | Run 44 |
| MB-053 deposit: cash refused above the kitchen's limit; deposit terms; order stored DEPOSIT/WAITING_FOR_DEPOSIT at half the total; transfer report shown as the customer's report and stored CUSTOMER_TRANSFER_REPORTED | PASS (order MB-100004) | Run 44 |

Record: [`verification/runs/20261013-NATIVE-43/README.md`](../../verification/runs/20261013-NATIVE-43/README.md).

## How to retest independently

1. `git checkout ec68cb94dc9eaf749958510b587d48a810b38327`; start the stack with `scripts/dev.sh up` (synthetic users only; passwords stay in the local `.env`, never in reports).
2. **MBQA-006 (W-MONEY-REFLOW):**
   - Sign in as `cook-a` and open the Statement (الكشف).
   - As a finance user, open Finance and show kitchen `00000000-0000-4000-8000-0000000000d1` after the cook workflow has completed orders (or use your own synthetic completed orders).
   - Run all 12 variations: roles × 320/390/1440 px × Arabic/English.
   - Expected: every amount is on one line; the statement table scrolls inside its region and the keyboard reaches its end; no page-level sideways scroll. The automated version is `apps/operations-web/e2e/money-reflow.e2e.ts`.
3. **Zero commission (TC-157):**
   - Cook statement and finance views show no platform commission.
   - A quote and an order carry commission 0.
4. **Deposit (TC-158..163)**, needing a kitchen with an approved recipient:
   - The cook enters the recipient in Settings; finance staff approve it (this needs MFA, so the local stack cannot complete it). The native fixture `apps/customer-mobile/e2e-native/deposit-profile.sql` sets the same state by SQL.
   - Cash above the cap is refused with an explanation.
   - The deposit quote shows the amount, remainder, recipient and the direct-payment note.
   - "I sent the deposit" changes the state to "reported by you"; preparation stays blocked until the kitchen reports receipt.
   - The kitchen's "not received" sends the payment to the finance queue.
   - Nothing anywhere says "bank verified".

## Known gaps and follow-ups (not defects of the candidate's claims)

- No automatic support case or customer notification when a deposit is disputed or a refund is owed (MB-052 follow-up).
- Finance "Settlements" still lists zero-amount COMMISSION entries under the 0% policy, which may mislead staff (MB-050 follow-up).
- Live money, fees, paid ads, wallets and gateways are not approved (D16 gates). All payment data here is synthetic.
- Physical devices (BLK-004), screen readers (BLK-003), push (O07): open.
