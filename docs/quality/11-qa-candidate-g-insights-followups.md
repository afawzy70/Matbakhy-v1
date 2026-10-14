# QA candidate G: organic activity and cook insights (D17), payment and commission follow-ups, 2026-10-13

Implementation-agent handoff. Every result below is self-review by the implementation agent, not independent review.

## Candidate

- **Immutable candidate: `c056e4a3444d89251e55ff8b843928b15592e37a`** on `implementation/mvp`.
  - The six local checks ran at this SHA from a clean worktree with fresh synthetic data ([evidence](../../verification/runs/20261013-CANDIDATE-G/evidence.json)).
  - BACKEND-VERIFY, WORKSPACE-CLEAN-CHECKOUT, LOCAL-STACK, BROWSER-E2E (6/6 workflows), SECRET-SCAN (329 commits plus the committable tree) and GUARDRAIL-TESTS: all PASS.
- **Candidate F** (`ec68cb9`) stays the reference for D10/D16 deposits and MBQA-006; candidate G includes all of it.
- MBQA-003, MBQA-004, MBQA-005, MBQA-006 and OBS-001 stay **FIXED_PENDING_INDEPENDENT_RETEST**.

## Delta since candidate F

| Area | Change | Commits |
|---|---|---|
| MB-054 (D17 Stage 2) | Organic activity: impressions, meal and kitchen views, add-to-cart. Counted once per visitor and Cairo day; only a per-day keyed hash is stored; crawlers and unknown subjects are ignored. The cook's "Insights" tab («الأداء») shows activity, orders, portions, gross sales, paid refunds and the known net, with a "not profit" note. No ranking, campaign or billing | `a89151f`, native `d83d4b3` ([product note](../product/10-organic-activity-and-cook-insights.md)) |
| MB-050 follow-up | Zero-amount commission entries are no longer listed for the cook or finance, and a settlement batch refuses them (`ZERO_AMOUNT`) | `a1cd997` |
| MB-052 follow-ups | The kitchen is notified in-app when the customer reports a transfer. The customer is notified when the kitchen reports receipt, a problem, a refund owed or a refund sent. Read-only finance staff no longer see payment error cards | `b8006db`, `ec68cb9` |
| iOS UI-test harness | Dismisses the iOS "Save Password?" prompt reliably (test code only) | `5efc139`, `1db23f0`, `7ce20da` |

## What to retest independently

1. **Insights (TC-164):**
   - Browse dishes, open a dish and add it to the cart as a signed-out visitor in two browser tabs. Then open the cook's Insights tab.
   - Expected: each tab counts once per kind and day, and refreshes add nothing. Counts belong to that kitchen only, and other kitchens see 0.
   - Gross, refunds and net are separate, and nothing is called profit.
   - No customer name, phone or account appears anywhere in the report.
2. **Zero commission:** the cook statement and the finance settlements list show no 0.00 commission lines.
3. **Payment notifications:** in a deposit order, after the kitchen reports "not received", the customer's notifications show «العربون: المطبخ لم يجد التحويل…». After the customer reports a transfer, the kitchen gets a notification.
4. Everything in [candidate F's handoff](10-qa-candidate-f-deposit-payments.md) still applies.

## Native

Native results are recorded in `docs/status/latest.md` and `verification/runs/`. Android passed every phase, including the MB-053 deposit phase, in runs 43 and 46. Run 47 (`7ce20da`, same app source as this candidate) is the iOS rerun.

## Known gaps

- Native activity has component tests only; no device run checks the stored rows.
- Rotating-bot inflation of activity counts needs edge rate limiting.
- Activity retention is decided under O06.
- There is no automatic support case for a disputed deposit.
- Live money is not approved.
- Physical devices (BLK-004), screen readers (BLK-003) and push (O07) are open.
