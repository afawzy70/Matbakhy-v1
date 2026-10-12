# RQ-004: an unexpired quote after a fee or policy change

Raised by independent QA (`qa/independent-cross-role-4eaee3a-20261012`, evidence `f76664c`, case S-CONFIG-QUOTES, BLOCKED on the oracle). Recorded 2026-10-12 by the implementation agent. This is analysis, not an owner decision.

## Observed behavior (QA, at `4eaee3a`; unchanged at the current candidate)

1. A customer holds an unexpired quote with zone fee 3000.
2. Staff change the zone fee to 3125.
3. A new quote shows 3125.
4. Placing the old quote returns 409 `PRICE_CHANGED` (reason `TOTAL_CHANGED`). No order is created and the old quote is not rewritten.

## What the requirements say

| Source | Text | Reading |
|---|---|---|
| TC-020 (BR-020, verification scenario) | "Valid quote exists and price or address/zone changes → customer submits the stale quote → 409 with a recoverable reason; fresh quote and explicit reconfirmation required" | Refuse the old quote |
| TC-042 (BR-042) | "Fee changes while an order and quote already exist → order unchanged; stale quote invalidated as specified; new quote uses new version" | Refuse the old quote; orders keep snapshots |
| `docs/engineering/03-api-contract.md` | "Only new quotes use a change: stale quotes fail revalidation (`POLICY_CHANGED`, `TOTAL_CHANGED`) and placed orders keep their snapshots" | Refuse the old quote |
| `docs/quality/01-verification.md` | Integration test list includes "stale quote after price/address/zone/policy update" | Refusal is tested |
| BR-042, MB-026 acceptance, staff UI before this change | "only new quotes use new policy" / "Changes apply to new quotes only" | Ambiguous on its own: can be read as "old quotes keep old values" |

**Conclusion:** the requirements settle the behavior. TC-020, TC-042 and the API contract all say a stale quote is refused and must be re-quoted. BR-042's short wording means the new policy reaches customers only through new quotes; it does not mean an old quote is honored. The implementation matches this. Only the wording was ambiguous.

## Changes in this repair

- The staff UI text now states that a quote not yet placed is refused and must be recalculated, and that placed orders keep their values. This is in Arabic and English.
- The contract description of `PostOpsConfigurationVersionsRequest` now states the same.
- BR-042 carries a clarification note pointing here.

No pricing rule changed.

## Customer impact

- **What the customer sees:** the old total until they press Place order. They then get "The price changed. Calculate the total again." and the quote is removed, so the screen never presents a total the server would not accept at placement.
- **The cost:** one extra step when a change lands during the 5-minute quote window.

## Owner question (confirmation only; work continues)

> Confirm that when a fee, rate or zone changes, a customer's unplaced quote (still within its 5 minutes) must be refused and recalculated, rather than honored at the old price.

**Recommendation:** confirm. It matches TC-020, TC-042 and the contract, and it never charges a total the current policy does not support.

**The alternative:** honor unexpired quotes at their snapshot. That would need a rule change (BR-020/BR-042), new tests, and a decision on who absorbs the difference.
