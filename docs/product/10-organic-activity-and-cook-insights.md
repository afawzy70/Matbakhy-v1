# Organic activity and the cook's free report (MB-054, owner decision D17 Stage 2)

Measurement only. It ranks, targets, charges and promotes nothing. Sponsored placement, campaigns, billing and growth pilots are Stage 3–4 and need a separate owner go.

## What is recorded

| Event | Sent when | Subject |
|---|---|---|
| `MEAL_IMPRESSION` | A dish appears in the customer web's dish list | Meal |
| `MEAL_VIEW` | A dish page loads | Meal |
| `KITCHEN_VIEW` | A kitchen page loads | Kitchen |
| `ADD_TO_CART` | A dish is added to the basket | Meal |

- **Once per visitor, kind, subject and Cairo day.** A refresh, a repeated click or a burst from the same visitor counts once (database primary key).
- **Visitor.** The visitor is a random ID kept only for the browser tab's session (`sessionStorage`). The server stores an HMAC of it, under a key derived per day from `ACTIVITY_HASH_KEY`, so it cannot be reversed or followed across days.
- **No personal data.** No account, address, device, network or advertising identifier is stored.
- **What is never counted:**
  - declared crawlers;
  - unknown or archived subjects;
  - events beyond 300 per visitor per day.
  The response is always `202`, so nobody learns whether an event counted.
- **Not instrumented:** the native apps do not send activity yet, and search terms and filters are not recorded.

## The cook's report (operations web → "Insights" / «الأداء»)

The report covers the cook's own kitchen only, for a Cairo date window (default: the last 30 days; at most 366 days).

| Figure | Definition |
|---|---|
| Interest | Distinct visitor-days per event kind |
| Totals calculated | Quotes created for the kitchen in the window |
| Orders placed | Orders created in the window |
| Completed, cancelled, rejected, expired, delivery failed | Orders whose event of that kind falls in the window |
| Repeat customers | Customers with two or more completed orders in the window |
| Portions offered / taken | Slots starting in the window (excluding drafts and cancelled slots): maximum portions, and reserved + allocated + consumed |
| Gross sales | Totals of orders completed in the window |
| Refunds paid | Refunds in state PAID or VERIFIED created in the window (finance workflow) |
| Known net | Gross sales minus refunds paid. **Not profit**: the platform does not know the kitchen's costs |

## Open points

- **Retention** of activity rows is decided under O06. There is no deletion job until then.
- **Rotating bots:** a bot that changes its visitor ID on every request is bounded only by the request size. Edge rate limiting belongs to the deployed environment.
- **Deposit refunds:** refunds that kitchens report under D16 (`order_payment` REFUNDED) are not yet included in "Refunds paid".
- **Privacy notice:** a notice for customers about aggregate measurement is part of the O06 privacy review.
