# Change matrix: owner decisions of 2026-10-13 (main) → stories → tests

Source: owner commits on `main` (2026-10-13 11:05–11:59 Kuwait), merged into `implementation/mvp` at `39856f6`. See [07-owner-decisions-implementation-sequence.md](07-owner-decisions-implementation-sequence.md), [direct deposit](../strategy/mvp-direct-deposit-payment.md) and [free core](../strategy/free-core-sponsored-discovery.md). Decisions are recorded as D10 (updated), D16 and D17 in [the register](../product/01-context-and-decisions.md).

This is a planning record, not a claim that anything is implemented. The work already in flight (D13/D14 at QA candidate E, MB-033, MB-039) is kept. Nothing was reset or rewritten.

## Requirements to stories and scenarios

| Requirement (owner) | Story | Scenarios | Status |
|---|---|---|---|
| Stage 0: 0% commission, no customer platform/service fee, no paid ads; commission kept only as inactive capability; ledger entries 0 at launch, not just hidden in UI | MB-050 | TC-157 | DONE (evidence `20261013-MB-050`) |
| Stage 1: quote with deposit due, remainder, recipient, method, expiry, terms; immutable snapshot; payment state separate from order and fulfilment; no preparation before kitchen-reported receipt **and** separate acceptance | MB-051 | TC-158, TC-159, TC-160 | DONE (evidence `20261013-MB-051`) |
| Stage 1: kitchen acknowledgement (identity, time, amount, reference, reason, audit), not-received and mismatch, disputes, refunds with a clear owner; never "bank verified" | MB-052 | TC-161, TC-162 | DONE (evidence `20261013-MB-052`) |
| Stage 1: customer web, Android and iOS deposit checkout and honest status, with a shared contract | MB-053 | TC-163 | DONE (evidence `20261013-MB-053`; native run 48 Android and iOS) |
| Stage 2: privacy-safe, deduplicated organic metrics; free cook reporting without profit claims; no sponsored ranking or billing | MB-054 | TC-164 | DONE at `a89151f` (evidence `20261013-MB-054`, [product note](../product/10-organic-activity-and-cook-insights.md)) |
| Stage 3–4: growth pilots, AI Concierge, automated payment verification, protection fund | none (deferred; owner go required) | none | Not planned now |
| Live money | none | none | BLOCKED by legal/consumer, food-business and recipient-account/provider gates (D16) |

## Effect on existing work

| Area | Current state | Change |
|---|---|---|
| Commerce policy (MB-026) | Synthetic development policy has commission 1000 bps (10%) | MB-050: development policy 0%. The server refuses non-zero commission for any policy it accepts. A database check keeps non-zero only for synthetic test fixtures that exercise the dormant capability |
| Quotes and orders (MB-017/018) | Commission snapshot stored per order | Kept (0). Existing development orders keep their historical snapshots; there are no production orders |
| Ledger, refunds, settlements, statements (MB-020/025) | Commission receivable and revenue, reversals, settlement lines | Posted at 0, so groups stay balanced. Statements and ops finance must not present a platform commission to cooks or customers (MB-050 screens) |
| Cash reports (`cash_report.method = 'CASH'`) | Cash only | MB-051/052 add a deposit method and payment states. Cash on delivery stays for eligible orders |
| Checkout UI (web, native) | Cash only, pickup/delivery | MB-053 adds the deposit path. D13 reconfirmation also applies to deposit amounts |
| Acceptance (MB-019) | The kitchen accepts PENDING_ACCEPTANCE | MB-051: deposit-required orders need kitchen-reported receipt before preparation; acceptance stays a separate action |
| Tests that assert a 10% commission | CashAndLedger, FinanceOperations, OrderPlacement, PriceChange, Quote, Configuration, CookWorkspace, ops-web | MB-050 updates the launch expectation to 0. Dormant arithmetic stays tested only through synthetic fixtures |
| Scenarios mentioning commission (TC around BR-033..036) | PASS with synthetic 10% | Kept as tests of the dormant capability. TC-157 adds the launch guarantee |
| D13 (MB-049) text "commission unchanged" | Lost-response recovery keeps the commission snapshot | Unchanged: the snapshot is immutable, whatever its value |

## Order of work

1. MB-050, next: a bounded change to existing code and tests.
2. MB-051, then MB-052 and MB-053: in dependency order, each verified and checkpointed. Development and test fixtures only.
3. MB-054 at P08.

## Not approved now (owner boundaries)

Live-money flow; live ads or fees; paid campaign products; wallet or gateway; AI concierge implementation; spending; public release.
