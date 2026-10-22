# Day 1 - Business and domain

> **Owner role:** Business documentation
> **Status:** REFERENCE — lab for understanding what Matbakhy is and the rules every change must respect.
> **Language / اللغة:** English · [العربية](day-1-business-ar.md)

## Objectives

By the end of the day you can explain, without notes:

- who the actors are and what each may do;
- how an order moves from basket to completion, and who can cause each transition;
- how money works in the MVP (integer piastres, server-owned prices, zero commission, cash on receipt or a manual deposit reported by the kitchen);
- why one order has exactly one cook and one slot.

## Reading list (about 4 hours)

1. [Project overview](../00-getting-started/project-overview.md), [glossary](../00-getting-started/glossary.md).
2. [Product vision](../01-business/product-vision.md), [target users](../01-business/target-users.md), [marketplace model](../01-business/marketplace-model.md).
3. [Actors and permissions](../01-business/actors-and-permissions.md), [order lifecycle](../01-business/order-lifecycle.md), [pricing and payments](../01-business/pricing-and-payments.md), [kitchen management](../01-business/kitchen-management.md), [business rules](../01-business/business-rules.md).
4. [Complete order journey](../02-user-journeys/complete-order-journey.md).
5. Canonical sources the handbook summarises: [context and decisions](../../product/01-context-and-decisions.md), [business rules](../../product/03-business-rules.md), [state machines](../../product/04-state-machines.md), [permissions](../../product/06-permissions.md).

## Hands-on tasks

1. Read the rule text directly from the planning data and note its owner story:
   ```bash
   python3 - <<'PY'
   import json
   rules = {r["id"]: r for r in json.load(open("planning/rules.json"))["rules"]}
   for k in ("BR-018", "BR-019", "BR-020"):
       print(k, rules[k]["text"])
   PY
   ```
   Explain in your own words why BR-018 (one cook and one slot per basket) and BR-019 (server calculates integer amounts) exist.
2. Draw the order state machine from [state machines](../../product/04-state-machines.md) on paper. Mark, for each edge, the actor (customer, cook, staff, system job such as expiry).
3. List the roles and the grants staff need for finance, support and audit operations from [permissions](../../product/06-permissions.md).
4. Read the owner journey J0 and J1 in `docs/quality/owner-acceptance/generated/core-owner-acceptance-suite.md`, then list which of the cases `CFG-001`, `E2E-DISC-001`, `E2E-BASKET-009` and `E2E-ORDER-101` exercise discovery, basket and placement. Open the case JSON in `docs/quality/owner-acceptance/cases/` and note its `expected` fields.
5. Open `planning/backlog.json` and find the stories `MB-017` (quote) and `MB-018` (order placement). Write down their acceptance lines. Remember the backlog status is a hint; the code decides what exists.
6. Money exercise: a meal costs 7500 piastres (EGP 75.00), quantity 3, with a 50% deposit. Compute the item total and deposit in integer piastres, and say who computes it, where it is shown to the customer and what the client is forbidden to do. Check your answer against [pricing and payments](../01-business/pricing-and-payments.md).

## Deliverable

A one-page note (kept outside the repository, or as a draft PR description you do not open) answering: the actors; the happy-path order flow with states; three ways an order ends other than completion; the payment options and what "kitchen-reported deposit receipt" means; five invariants that no change may break.

## Self-check

- [ ] I can name the six invariants from the [roadmap](../00-getting-started/learning-roadmap.md) ground rules from memory.
- [ ] I can say why "bank verified" must never appear in UI text.
- [ ] I know what is MVP and what is deferred (growth billing, AI Concierge, online card payments), and where that is recorded ([implementation status](../07-reference/implementation-status.md)).
- [ ] I can explain why a cook's approval is operational verification, not a food-safety guarantee.
- [ ] I can find any business rule by ID and the scenarios that cover it.

Next: [Day 2](day-2-applications.md).
