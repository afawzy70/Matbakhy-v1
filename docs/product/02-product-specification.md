# Product specification

## Outcome and operating model

Help a customer find a suitable homemade meal from a nearby approved cook, understand availability and total cost, place a trustworthy order, receive/pick up the meal and obtain support. Help cooks publish what they can actually prepare. Give operators enough control to prevent unserviceable orders and resolve exceptions.

Proposed launch is a controlled pilot in owner-selected Egyptian service zones. Do not invent a Cairo or Alexandria launch decision. Public browsing is allowed; a verified account and verified contact phone are required to place an order. Catalogue pictures and claims must be genuine or clearly marked development fixtures.

## Personas and primary jobs

| Persona | Job | Success |
|---|---|---|
| Customer | Find food suitable for location, budget, time and stated dietary constraints | Accurate availability, no surprise total, visible order status |
| Home cook | Sell a manageable number of portions in defined slots | No overselling, timely accept/reject, simple preparation queue |
| Operations agent | Approve supply and handle fulfillment exceptions | Auditable decisions and relevant customer context |
| Support agent | Resolve missing/late/incorrect orders and complaints | Case linked to order; safe escalation and visible resolution |
| Finance operator | Reconcile cash obligations, commission and refunds | Balanced ledger, documented settlement, no duplicate payout |
| Platform administrator | Manage access and operating configuration | Least privilege, step-up controls, audit trail |

## MVP scope / acceptance boundary

| Capability | MVP behavior | Explicitly later/conditional |
|---|---|---|
| Customer identity | OIDC sign-in/out, verified email, phone verification at checkout, recovery via IdP, address book, deletion request | Social login, guest checkout |
| Discovery | Browse by service zone, meal/cook name, category, price and available date/slot; sort; pagination | Recommendations, full social feed, video, complex personalization |
| Trust | Cook profile, operational approval label, ingredient/allergen declarations, reviews of completed orders | Claims of certified food safety, automatic verification |
| Catalogue | Meal title/description, photos, portion details, price, ingredients/allergens, prep/availability | Complex option bundles, subscriptions, nutrition computation |
| Basket/checkout | One cook/slot; server quote; address/serviceability; delivery/pickup; cash; order confirmation | Multi-cook basket, coupons, loyalty, wallets, tips |
| Order lifecycle | Pending acceptance, accepted, preparing, ready, delivery/pickup, completed; cancellations and failure reasons | Live GPS, courier dispatch optimizer |
| Cook work | Application, verification status, catalogue, capacity, order action queue, statement | Cook native app, advanced business intelligence |
| Backoffice | Approvals, moderation, queues, order timeline, exceptions, support, policies, audit, finance | Hidden impersonation, arbitrary SQL/report builder |
| Reviews/favorites | Favorite/unfavorite meal; one review per completed order; report/moderate | Open comments on meals, social follows/chat |
| Notifications | Persisted in-app events; transactional email adapter; customer mobile push adapter | Marketing campaigns, promotional SMS |
| Operations | Controlled rollout, logs/metrics, backup restore, reconciliation and incident procedures | Kubernetes/service mesh/multiple regions by default |

All four requested client targets (web, backoffice, Android, iOS) are part of MVP completion. A web-only milestone is intermediate.

## Journeys

### Customer: first successful order

1. Select language and service zone; decline location permission without losing manual selection.
2. Browse only visible published meals, identify cook and next availability; see unavailable items only when explicitly viewing favorites/history.
3. Open meal, read portion/ingredients/allergens, select dated slot and quantity; prices are EGP with clear units.
4. Add to basket. A different cook/slot produces a replace-basket confirmation before clearing anything.
5. Sign in, verify required contact details, select delivery address or pickup, request a server quote.
6. Show item subtotal, delivery fee, tax if configured, total, cash instructions, acceptance deadline and cancellation policy.
7. Submit with a stable idempotency key. On uncertain connection, show "Checking your order" and query by client request ID before retrying.
8. Display a real server order ID and PENDING_ACCEPTANCE. Acceptance is not assumed.
9. Track server events; use handover code for delivery/pickup. Denied notification permission does not stop ordering.
10. After completed order, review, re-order using current prices/availability, or open support case.

### Cook: onboard and fulfill

1. Register; complete identity/contact/profile, private pickup/production location, zones and required verification documents.
2. Submit application; ops requests changes/approves/rejects with a reason. Approval scope and limitations are displayed.
3. Create meal draft and photo; submit required content; publish only when profile and required fields pass validation.
4. Create dated fulfillment slots and portion capacities. Set daily pause/cutoff; already accepted orders remain visible.
5. Receive new-order queue; accept before deadline or reject with reason. Rejection atomically releases reserved portions.
6. Mark preparing/ready; record dispatch for delivery or verify pickup handover. Never mark an order paid merely by marking it complete.
7. Declare actual cash receipt as a separate event; view pending reconciliation and commission statement.

### Operations: exception and reconciliation

1. Work role-filtered queues with server pagination and saved filters.
2. Review cook application privately, record decision and evidence reference, publish no identity documents.
3. View an order's immutable quote, timestamps, actor events, capacity reservation, fulfillment and financial state.
4. Handle overdue acceptance, safety complaint, cancellation request, failed delivery, cash discrepancy and refund with reason and permission.
5. Reconcile completed orders and recorded collection; create settlement/refund obligations and record authorized external completion.
6. Maintain audit trail and support case. Never silently rewrite price, original order items or completed timestamps.

## Definition of product completion

Every required backlog story DONE with real evidence; all release-critical scenarios passed; live configurations approved; customer web and cook/ops work on desktop/mobile layouts; Android and iOS tested as native builds; no unresolved critical/high security or transaction defects; staging backup/restore and migration recovery demonstrated; business operators trained; production/store release separately authorized.

## Pilot measures (targets, not existing results)

Track checkout conversion, successful order submissions excluding validation failures, acceptance latency, rejection/expiry rate, fulfillment completion, stock conflicts, support cases per order, cash variance and repeat purchase. Define cohort/time window and exclude synthetic/staff accounts. Proposed operating targets: 99% successful valid order submissions, no overselling or duplicate orders, no unexplained ledger imbalance; other business KPI targets are owner decisions after pilot baseline.
