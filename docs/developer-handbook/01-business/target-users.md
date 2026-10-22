# Target users

> **Owner role:** Business documentation
> **Status:** PARTIALLY IMPLEMENTED — customer and cook journeys are implemented; catering as a separate product is PLANNED only.
> **Language / اللغة:** English · [العربية](target-users-ar.md)

This page describes who the product serves and which of their needs the code meets today. For the technical roles and permissions see [actors and permissions](actors-and-permissions.md).

## Customer segments

The product has one ordering flow; the segments differ in *what* they order and *how much*. The home screen of the customer web app offers three "intent" tiles (`apps/customer-web/src/Browse.tsx`, labels in `apps/customer-web/src/i18n.ts`):

| Intent tile (English label) | Arabic label | Typical customer | What the code actually does |
|---|---|---|---|
| A meal for me or us | وجبة ليا أو لينا | One or two people at lunch or dinner | Reveals a budget (max price) field; the meal list query is the same |
| Complete my table | أكمل سفرتي | Someone who has a main dish and wants a side, dessert or salad | Same as above |
| Prepare a gathering | أجهز عزومة | A family get-together or small hosting occasion | Same as above |

Important: the three tiles are a **presentation hint**. The `intent` is not sent to the server and no separate "catering" or "party order" product exists. A gathering is simply a basket of larger quantities (for example, 2 family trays) from one kitchen and one slot, limited by the per-line quantity limit (the preview policy allows 10 per line; the value comes from the server policy, `max_item_quantity` in `commerce_policy`). Real catering (large events, custom menus, quotations) is **PLANNED** at most: it appears only as a candidate paid-growth idea in `docs/strategy/free-core-sponsored-discovery.md`, not in the specification or the code.

### Worked examples

1. **Individual meal.** Karim, living alone in a served zone, searches "koshary", sorts by price, and orders 1 portion for pickup with cash on receipt. Pickup is only offered if the kitchen enabled it (`pickup_enabled` on `kitchen`).
2. **Family order.** A family of five orders 2 trays of molokhia and 1 rice dish for Friday lunch with delivery. The server checks that the kitchen delivers to the family's saved address zone and adds the kitchen's zone delivery fee. Because the total is higher, the kitchen's COD cap may apply and the family may be offered the deposit option instead (see [pricing and payments](pricing-and-payments.md#3-payment-methods)).
3. **Gathering.** A host orders 10 portions for a Saturday gathering. If the slot has fewer than 10 free portions the quote is refused with `STOCK_CHANGED` and `INSUFFICIENT_PORTIONS`; the customer picks another slot. There is no partial fulfilment and no negotiation.

## Home cooks / kitchen owners

Target: people who already cook well at home and want to sell a *limited, scheduled* amount of food, not a restaurant that serves walk-ins.

What the product gives a cook (all IMPLEMENTED, see [kitchen management](kitchen-management.md)):

- an application with identity, contact, private production location and documents, reviewed by staff;
- a public kitchen page (private location and phone are never public);
- meals with ingredients, allergens, photos and a price, versioned by revision;
- dated slots with per-meal and total capacity and an automatic cutoff;
- an order queue with accept/reject, preparation, ready, dispatch and handover steps;
- payment settings (deposit percentage, optional COD cap, deposit recipient account);
- a statement and free insights about their own kitchen.

A person can be both a customer and a cook with the same login (the same `app_user` row). Being a cook adds nothing about staff privileges.

## Platform staff

| Persona | Job | Implemented as |
|---|---|---|
| Operations agent | Approve cooks, handle overdue/failed orders and exceptions | Grants `cook.review`, `order.ops`, `order.exception_cancel`, `order.handover_override`, `content.moderate` |
| Support agent | Resolve missing/late/incorrect order complaints | Grant `support.case` |
| Finance operator | Verify cash and deposits, refunds, settlement | Grants `finance.read`, `finance.verify`, `finance.refund_approve`, `finance.settlement` |
| Administrator | Manage grants, configuration, audit, MFA policy | Grants `staff.grant`, `audit.read`, `config.read`, `config.write`, `security.mfa_policy`, `security.mfa_reset` |

In a small pilot one person may hold several grants. The system still blocks some combinations (for example, a person cannot approve their own cook application, verify cash they reported, or approve their own kitchen's payment account; owner case E2E-OPS-108).

## Guests

Anyone can browse meals, kitchens, slots, reviews and public policies without an account (public `GET` routes are listed in `backend/app/src/main/java/com/matbakhy/app/security/SecurityConfiguration.java`, `PUBLIC_GET`). An account with verified e-mail and a verified Egyptian phone is required to get a quote or place an order.

## Geography and scale assumptions

- Egypt only. Service zones are configured data (`service_zone`, `kitchen_zone`); the repository holds only synthetic dev zones in `backend/app/src/main/resources/db/devdata/R__dev_service_zones.sql`. The real launch city and zones are an open owner decision (O01).
- A controlled pilot is assumed; there is no capacity or load claim beyond what the performance stories measured (see [test execution](../05-developer-workflow/test-execution.md)).

## Known limitations and documentation gaps

- `docs/product/02-product-specification.md` lists "Cook native app" as later; there is no cook mobile app. Cooks use the responsive operations web (`apps/operations-web`).
- The three intent tiles were designed in `docs/design/approved/DESIGN.md`; whether they should eventually filter server-side (for example by minimum portions) is undecided. D11 only deferred the portion-size filter.
