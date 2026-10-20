# Project overview

> **Owner role:** Business documentation
> **Status:** PARTIALLY IMPLEMENTED — describes what Matbakhy is and what exists today; the future items are labelled PLANNED.

Read this page first. It explains the product to someone who has never heard of it, then points to the pages that go deeper.

## What Matbakhy is

Matbakhy ("my kitchen" in Arabic) is a marketplace for **homemade food in Egypt**. Approved home cooks publish meals and time slots with limited portions; customers browse, pick a slot, and order. The cook accepts, prepares and hands over the food (pickup, or delivery arranged by the cook). Prices are in Egyptian pounds (EGP), stored as integer piastres, and all scheduling is in the `Africa/Cairo` time zone.

Matbakhy is a **marketplace, not a restaurant and not a courier company**. The platform:

- decides who may sell (operational approval of cooks, not a food-safety certificate);
- computes prices, capacity and totals on the server;
- keeps an auditable record of every order and payment event;
- gives staff the tools to handle exceptions (support cases, disputes, refunds, reconciliation).

The cook cooks, arranges delivery and receives the money directly. Matbakhy never holds customer money (see [pricing and payments](../01-business/pricing-and-payments.md)).

## A day in the life (worked example)

Um Ahmed runs a home kitchen in a neighbourhood the platform serves. On Monday she creates a slot for Friday lunch (11:00–14:00) with 12 portions, offering her stuffed vine leaves (EGP 85.00) and a family tray of molokhia (EGP 240.00).

Sara opens the customer web app on Wednesday, filters to her zone, picks Friday's slot, and adds 2 trays of molokhia. The server computes a **quote** valid for 5 minutes: items EGP 480.00 + that kitchen's delivery fee for Sara's zone. Sara chooses cash on receipt, accepts the terms and places the order. In one database transaction the server reserves the portions, snapshots the prices and meal content, and creates the order in `PENDING_ACCEPTANCE`.

Um Ahmed has until the acceptance deadline to accept or reject. She accepts; on Friday she starts preparation, marks it ready, and a six-digit handover code appears for Sara. When the courier or Um Ahmed delivers the food, Sara reads out the code, the cook enters it, and the order becomes `COMPLETED`. Afterwards Um Ahmed reports the cash she collected and a finance operator verifies it. Sara can review the meal once.

Every step above maps to code described in [order lifecycle](../01-business/order-lifecycle.md) and traced end to end in [the complete order journey](../02-user-journeys/complete-order-journey.md).

## Problems it solves

| Problem | How the product addresses it | State |
|---|---|---|
| Home cooks have no trusted channel and oversell on social media | Slots with hard per-meal and per-slot portion capacity; reservation is transactional (no overselling, see [capacity rules](../01-business/order-lifecycle.md#step-3--availability-and-capacity)) | IMPLEMENTED |
| Customers do not know the final cost | Server quote shows items, delivery fee, tax and total before confirmation; client-sent prices are rejected | IMPLEMENTED |
| Price changes between browsing and ordering cause disputes | Stale quote is refused; customer must see and accept the new total; placed orders keep their price | IMPLEMENTED |
| Trust in an unknown cook | Operational approval workflow with a reviewer, reason and audit trail; reviews only from completed orders | IMPLEMENTED |
| Cash and deposits are hard to track | Cook-reported cash with separate finance verification; deposit states with kitchen-reported receipt and dispute handling | IMPLEMENTED (synthetic money only) |
| Customers want the food at a time they choose | Dated slots with cutoff; acceptance deadline; handover code | IMPLEMENTED |

## Who uses it

See [target users](../01-business/target-users.md) for segments and [actors and permissions](../01-business/actors-and-permissions.md) for what each role can do.

- **Guest / customer**: browse, order, track, review, open support cases.
- **Cook (kitchen owner)**: apply, manage meals/slots/payment settings, run the order queue.
- **Staff**: not a single role. Support, operations, finance and administrators are *permission grants* on an ordinary account (`backend/modules/identity/src/main/java/com/matbakhy/identity/api/Permission.java`).

## Applications and code

One Spring Boot backend serves three front ends. See [system overview](../03-architecture/system-overview.md) and [repository map](../04-codebase/repository-map.md).

| Surface | Directory | Audience |
|---|---|---|
| Customer web (React) | `apps/customer-web` | Guests and customers |
| Operations web (React; cook workspace + staff backoffice in one app) | `apps/operations-web` | Cooks and staff |
| Customer mobile (Expo / React Native, Android and iOS) | `apps/customer-mobile` | Customers |
| Backend (Spring Boot modular monolith, PostgreSQL) | `backend/` | All |
| Shared TypeScript packages | `packages/api-client`, `packages/design-tokens` | Front ends |

Backend modules (Maven, under `backend/modules/`): `identity`, `cooks`, `catalog`, `availability`, `orders`, `finance`, `support`, `notifications`, `operations`. Business logic for ordering lives in `orders`; see [backend module guide](../04-codebase/backend-module-guide.md).

## Scope: MVP versus future

This section is deliberately explicit. Implementation state is judged from the code and tests, not from backlog status.

### In the MVP (IMPLEMENTED in the repository, exercised with synthetic data)

- Public browsing of meals and kitchens, server-side search and filters, Arabic and English.
- Customer accounts via OpenID Connect (Keycloak), phone verification before first checkout, address book.
- Cook application, staff review, kitchen creation, meal publishing with revisions and price history, slots and capacity.
- One-cook, one-slot baskets; server quotes; order placement with idempotency; accept / reject / prepare / ready / dispatch / handover.
- Payment: cash on receipt (where the kitchen allows it) and a manual deposit sent directly to the kitchen's approved account, with kitchen-reported receipt.
- Cash reports and finance verification, refunds, settlement tooling (dormant under zero commission), support cases, reviews, favorites, in-app notifications.
- Staff grants with per-permission checks, audit log, configurable MFA policy (`DISABLED`, `OPTIONAL`, `REQUIRED` per population).
- **Zero Matbakhy commission and no customer platform fee.** The commission capability exists only as an inactive technical option.

### Gated or not live

- **Live money is gated** by legal, consumer-protection and recipient-account review. All current environments use synthetic accounts, synthetic policies and fake payment recipients. See `docs/strategy/mvp-direct-deposit-payment.md`.
- Real SMS/e-mail/push providers are not connected in the repository (fake adapters in development/test; production provider selection is an open owner decision, O07 in `docs/product/01-context-and-decisions.md`).
- Native iOS/Android store release is a separate, owner-authorized step.

### Future (PLANNED, not available)

- Paid growth tools, sponsored ranking or campaigns for kitchens (owner direction: only after real demand, never at launch; see `docs/strategy/free-core-sponsored-discovery.md`).
- Platform wallet, escrow or automated payment gateway.
- AI concierge, social feed/follows/comments, coupons/loyalty, multi-cook baskets, courier dispatch app, live driver map.
- Portion-size filter (one person / two people) on discovery: deferred by owner decision D11.

## Where to go next

- Business domain: [product vision](../01-business/product-vision.md), [marketplace model](../01-business/marketplace-model.md), [kitchen management](../01-business/kitchen-management.md), [business rules digest](../01-business/business-rules.md).
- Terms: [glossary](glossary.md).
- Setting up and navigating: [learning roadmap](learning-roadmap.md), [repository navigation](repository-navigation.md).
- Authoritative product documents live in `docs/product/` (start with `docs/product/01-context-and-decisions.md`).
