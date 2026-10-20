# Product vision

> **Owner role:** Business documentation
> **Status:** PARTIALLY IMPLEMENTED — the vision is owner-confirmed; the MVP slice is implemented with synthetic data, growth and live money are PLANNED or gated.

## Vision in one paragraph

Help a customer in Egypt find homemade food from a nearby, approved home cook, understand the total price and availability up front, place a trustworthy order, receive it, and get support when something goes wrong. Help home cooks sell a *manageable* number of portions in defined time windows without overselling, and give operators enough control to prevent unserviceable orders and settle exceptions. (Source: `docs/product/02-product-specification.md`, "Outcome and operating model".)

## Principles that shape the code

These are the non-negotiable product decisions in `docs/product/01-context-and-decisions.md` (decision register D01–D18) that every developer must keep in mind.

| Principle | What it means in practice | Where it is enforced |
|---|---|---|
| Egypt, EGP, `Africa/Cairo` | Currency is always `EGP`; money is an integer number of piastres (`*_minor` columns, `Money(amountMinor, currency)` records); slot times are stored in UTC and scheduled in Cairo time | `backend/modules/availability/src/main/java/com/matbakhy/availability/internal/CairoTime.java`; DB `CHECK (currency = 'EGP')` in `backend/app/src/main/resources/db/migration/V13__cash_and_ledger.sql` |
| The server owns truth | Price, capacity, roles and state transitions are computed or decided by the server; client-sent amounts are rejected | `QuoteService`, `OrderLifecycleService` in `backend/modules/orders/src/main/java/com/matbakhy/orders/internal/` |
| One cook, one slot per order | A basket cannot mix kitchens or time slots; the client must ask before replacing a basket | `QuoteService.terms` (`MIXED_KITCHENS_OR_SLOT`) |
| Immutable snapshots | A placed order keeps the item content, price, policy versions and address as they were | `customer_order`, `order_item` (V11) with append-only triggers |
| Free core marketplace | Zero Matbakhy commission, no customer platform fee, no paid ads at launch (D10, D17) | Policy constraint in `backend/app/src/main/resources/db/migration/V23__launch_commission.sql` |
| Payments stay with the kitchen | Cash on receipt plus a manual direct deposit to the kitchen's account; Matbakhy records what the kitchen *reports*, never "bank verified" (D16) | `PaymentService` |
| Arabic first, English available | Arabic RTL and English LTR on web, mobile and notifications | `apps/customer-web/src/i18n.ts`, `apps/customer-mobile/src/i18n.ts`, `apps/operations-web/src/i18n.ts` |
| Cook approval is operational | "Approved" means staff reviewed the application; it is not a food-safety guarantee and the UI must not claim otherwise | Public kitchen label `OPERATIONALLY_VERIFIED` in `backend/modules/cooks/src/main/java/com/matbakhy/cooks/internal/KitchenService.java` |

## Commercial direction (owner decisions)

- **Launch is free.** No Matbakhy commission on orders, no customer platform fee, no paid placement. Any delivery or transfer fee a customer sees belongs to the kitchen's delivery arrangement or the customer's bank/wallet; it is not a Matbakhy fee. IMPLEMENTED: the quote contains only items, the kitchen's delivery-fee for the zone, and policy tax (0 in the synthetic policy); test `backend/app/src/test/java/com/matbakhy/app/finance/ZeroCommissionIntegrationTest.java`, owner case E2E-FIN-046.
- **Commission is an inactive option.** A `commission_rate_bps` exists on the commerce policy and is snapshotted on each order, but a non-synthetic policy must have rate `0` (database check) and the API refuses non-zero rates (owner case E2E-OPS-107).
- **Future opt-in growth tools (PLANNED, no implementation).** The owner intends that, only after real demand exists and a kitchen asks for it, transparent paid visibility could be offered per kitchen. Today the code only gathers privacy-safe organic activity and a free "kitchen performance" view for cooks (`backend/modules/catalog/src/main/java/com/matbakhy/catalog/internal/activity/ActivityService.java`, `backend/modules/finance/src/main/java/com/matbakhy/finance/internal/InsightsService.java`; owner case E2E-COOK-109). There is no campaign, billing or sponsored-ranking code. See `docs/strategy/free-core-sponsored-discovery.md`.

## Success measures (targets, not results)

`docs/product/02-product-specification.md` lists pilot measures such as checkout conversion, acceptance latency, rejection/expiry rate, stock conflicts, support cases per order and cash variance. They are *targets*. No pilot has been run, and no figure in the repository is a real result.

## Release stages

The owner's staged implementation sequence is in `docs/delivery/07-owner-decisions-implementation-sequence.md`. In short: build and verify the core flows with synthetic data; independent QA; staged preview; only then, with written legal and provider review, any live money. This handbook does not authorize any public deployment or live payment.

## What is deliberately out of the MVP

| Item | Status | Reason |
|---|---|---|
| Matbakhy wallet, escrow, automated payment gateway | PLANNED (not started) | Needs legal/provider approval; MVP uses kitchen-direct deposit |
| Paid campaigns, sponsored ranking, kitchen billing | PLANNED (not started) | Owner: only opt-in and later |
| AI Concierge | PLANNED (research only) | Owner: future-stage research |
| Social feed, follows, comments, chat | PLANNED (deferred, decision O10) | Not rejected; deferred |
| Multi-cook basket, coupons, loyalty, subscriptions | PLANNED | Not in `docs/product/02-product-specification.md` MVP table |
| Courier dispatch app, live driver map | PLANNED | Delivery is cook-arranged; ops only record a courier reference |
| Portion-size filter on discovery | PLANNED (deferred by owner decision D11; controls hidden) | Portion stays free text |

## Known limitations and documentation gaps

- `docs/product/01-context-and-decisions.md` marks several early decisions (D01–D09) as "proposed defaults"; later sections record owner approvals. When a statement there conflicts with the code, the code and the later owner-decision sections win; this handbook follows that rule.
- The pilot city, service zones, tax treatment, refund policy and legal entity are open owner decisions (O01–O06). The repository contains only synthetic zones and a synthetic commerce policy (`backend/app/src/main/resources/db/devdata/R__dev_commerce_policy.sql`).
