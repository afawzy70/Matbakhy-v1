# RQ-004: an unexpired quote after a fee or policy change

Raised by independent QA (`qa/independent-cross-role-4eaee3a-20261012`, evidence `f76664c`, case S-CONFIG-QUOTES, BLOCKED on the oracle). Recorded 2026-10-12 by the implementation agent. **Decided by the owner on 2026-10-13 (decision D13, session prompt 09:47 Asia/Kuwait)**; the analysis below is kept as history.

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

- **What the customer sees:** the old quote, with its old total, stays on screen until they press Place order; it can therefore show a total the server will no longer accept. At placement the server revalidates and refuses it (409 `PRICE_CHANGED`, reason `TOTAL_CHANGED` or `POLICY_CHANGED`); no order is created and placed orders keep their snapshots. The web and native clients then remove the quote, show "The price changed. Calculate the total again." and require a new quote and a fresh terms acceptance; the new total is never accepted silently. *(Corrected 2026-10-13: an earlier version said the screen never presents a total the server would not accept, which was wrong. Client-level tests of this path do not exist yet; server tests do: QuoteIntegrationTest, OrderPlacementIntegrationTest.)*
- **The cost:** one extra step when a change lands during the 5-minute quote window.

## Owner decision D13 (2026-10-13) — replaces the open question

The owner confirmed refusal and re-quotation, and added the customer experience:
- before an order is created, a displayed price or unexpired quote does not guarantee the old price; the server revalidates at placement;
- the customer is told what changed (the kitchen, only when a kitchen-managed meal price changed; otherwise the delivery fee, tax or policy component that actually changed), sees the previous and new affected prices and the new total from a fresh server quote, and must explicitly accept it before placing;
- no silent charge, no silent resubmission, and the original Place order click is never acceptance of an unseen price; the basket and fulfilment details stay, and the customer can edit or leave;
- after an order is committed its snapshots never change; concurrent updates and placements are serialized; a committed order with a lost response is recovered at its original price.

Implementation and verification: backlog MB-049, scenarios TC-154–TC-156. No confirmation request remains open.
