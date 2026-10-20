# Core owner acceptance suite

Ordered end-to-end journeys (Customer → Kitchen → Admin/Finance). Run in order on one synthetic run id; every case is NOT_RUN until executed with evidence.

## J0 — Baseline, public access and identity

Record the deployed source SHA, prove public discovery and sign-in/out work and that administration surfaces are not exposed. Run first; later cases cite the SHA it records.

| # | Case | Title | Readiness | Status |
|---|---|---|---|---|
| 1 | CFG-001 | Public realm discovery is served; Keycloak admin console and master realm are not published on the customer identity host id.<PREVIEW_DOMAIN> (the owner-approved console lives on the separate kc-admin host and is covered by docs/operations/14-keycloak-admin-console.md; absorbs CFG-035) | Ready to run manually now | NOT_RUN |
| 2 | E2E-DISC-001 | Anonymous visitor discovers published meals and kitchens in Arabic and English without signing in | Ready to run manually now | NOT_RUN |
| 3 | CFG-002 | Customer web sign-in uses authorization code + PKCE S256 and a real logout ends the server session | Ready to run manually now | NOT_RUN |
| 4 | E2E-DISC-003 | Customer signs in and out; private data and the session are gone after sign-out | Ready to run manually now | NOT_RUN |
| 5 | CFG-034 | Network classes: customer hosts follow CUSTOMER_ALLOWED_IPS, ops host and /api/v1/ops, /api/v1/cook follow STAFF_ALLOWED_IPS; unauthenticated staff API from an allowed IP is 401/403 | Ready to run manually now | NOT_RUN |

## J1 — Pickup + cash on receipt: Customer → Kitchen → Operations/Finance/Admin

The core order journey: basket and quote, placement, the kitchen accepts/prepares/readies, pickup handover with the customer's code, then operations, finance and the administrator inspect the same synthetic order.

| # | Case | Title | Readiness | Status |
|---|---|---|---|---|
| 1 | E2E-BASKET-009 | Basket lines can be added, edited and removed; estimates use integer piastres and survive reload | Ready to run manually now | NOT_RUN |
| 2 | E2E-BASKET-019 | Changing fulfilment, address or payment method discards the shown total and the server recalculates | Ready to run manually now | NOT_RUN |
| 3 | E2E-ORDER-101 | Core journey 1: pickup + cash on receipt, from placement to completion and operations inspection | Ready after the extra synthetic fixtures are provisioned | NOT_RUN |
| 4 | E2E-OPS-103 | Journey end: after a customer's order is completed by the kitchen, operations and finance inspect it - totals, timeline, cash state, zero platform commission and masked contact | Ready after the extra synthetic fixtures are provisioned | NOT_RUN |

## J2 — Delivery + cash on receipt

Same journey with delivery to Test zone A: fee in the quote, courier hand-off, failure/complete paths, cash due and its verification.

| # | Case | Title | Readiness | Status |
|---|---|---|---|---|
| 1 | E2E-ORDER-102 | Core journey 2: delivery + cash on receipt, from placement through courier dispatch to completion | Ready after the extra synthetic fixtures are provisioned | NOT_RUN |
| 2 | E2E-FIN-038 | Cash on receipt (delivery order): completion leaves cash due; the kitchen's report and finance verification are separate steps with the exact amount | Ready after the extra synthetic fixtures are provisioned | NOT_RUN |

## J3 — Pickup + 50% direct deposit

Manual deposit to the approved (fake) recipient: customer reports transfer, kitchen reports receipt (gate before preparation), remainder in cash, reconciliation.

| # | Case | Title | Readiness | Status |
|---|---|---|---|---|
| 1 | E2E-FIN-039 | Core journey 3: pickup + 50% deposit, from deposit report to verified remaining cash | Needs the updated application deployment | NOT_RUN |
| 2 | E2E-FIN-106 | A deposit order cannot move to PREPARING until the kitchen reports receipt (acceptance stays separate) | Ready to run manually now | NOT_RUN |
| 3 | E2E-FIN-045 | Reconciliation invariants hold: balanced ledger, one cash obligation per order, payments match history, deposit never in ledger | Ready after the extra synthetic fixtures are provisioned | NOT_RUN |

## J4 — Delivery + 50% direct deposit, zero platform charges

Deposit with delivery and a courier; no commission, service fee or tax anywhere.

| # | Case | Title | Readiness | Status |
|---|---|---|---|---|
| 1 | E2E-FIN-101 | Core journey 4: delivery + 50% deposit with a courier, to verified remaining cash | Needs the updated application deployment | NOT_RUN |
| 2 | E2E-FIN-046 | Zero platform charges: no commission, service fee or tax on any quote, order, ledger or statement | Ready after the extra synthetic fixtures are provisioned | NOT_RUN |

## J5 — Guards on the journey: prices, repeats, isolation

Server-owned price after a change, immutable placed prices, duplicate submit, a guest basket through sign-in, and isolation between customers and kitchens.

| # | Case | Title | Readiness | Status |
|---|---|---|---|---|
| 1 | E2E-BASKET-014 | A price changed by the kitchen after the quote is shown, explained and must be accepted before placing | Ready to run manually now | NOT_RUN |
| 2 | E2E-BASKET-015 | A placed order keeps its agreed prices after the kitchen changes the price | Ready to run manually now | NOT_RUN |
| 3 | E2E-BASKET-030 | A guest keeps the basket through sign-in and returns to checkout | Ready to run manually now | NOT_RUN |
| 4 | E2E-ORDER-022 | Replaying the same placement request with the same Idempotency-Key returns the same order and does not reserve twice | Ready to run manually now | NOT_RUN |
| 5 | E2E-ORDER-037 | A cook cannot read or command another kitchen's order; customers and unprivileged staff cannot call cook/ops endpoints | Ready to run manually now | NOT_RUN |
| 6 | E2E-ORDER-120 | One customer cannot read, cancel, pay-report or review another customer's order | Ready to run manually now | NOT_RUN |

## J6 — Kitchen workspace and operations control

Cook publishes a meal and opens a slot; cross-kitchen access is refused; staff see only what they are granted; a customer cannot make themselves staff; kitchen approval and permission grants (these two need the updated deployment and staff MFA).

| # | Case | Title | Readiness | Status |
|---|---|---|---|---|
| 1 | E2E-COOK-051 | Cook creates a meal draft with an uploaded photo and publishes it; customers see it only after publication | Ready to run manually now | NOT_RUN |
| 2 | E2E-COOK-052 | Cook creates a slot in the ops web, opens it, and it becomes orderable for customers | Ready to run manually now | NOT_RUN |
| 3 | E2E-COOK-054 | A cook cannot read or change another cook's meals, slots, orders or uploads; the other kitchen's data stays unchanged | Ready after the extra synthetic fixtures are provisioned | NOT_RUN |
| 4 | E2E-OPS-055 | Each staff account sees and can call exactly its granted sections; the administrator is not a superuser; denials are audited | Ready after the extra synthetic fixtures are provisioned | NOT_RUN |
| 5 | E2E-OPS-058 | A customer cannot make themselves staff: role fields, staff endpoints, forged tokens and grant requests are all refused server-side | Ready to run manually now | NOT_RUN |
| 6 | E2E-COOK-049 | Staff approval creates the kitchen; the applicant becomes a cook only after START_REVIEW and APPROVE with fresh MFA | Needs the updated application deployment | NOT_RUN |
| 7 | E2E-OPS-059 | Granting a permission needs fresh MFA, a reason and is audited with actor, time, target and request id and no secrets | Needs the updated application deployment | NOT_RUN |

## J7 — Two-step verification (after the updated deployment and the owner-approved realm migration)

Optional MFA end to end on the web: enrol, step up, policy authorization, staff high-risk actions. Policies stay OPTIONAL.

| # | Case | Title | Readiness | Status |
|---|---|---|---|---|
| 1 | CFG-102 | Web step-up and first-time enrolment through Keycloak: level-1 sign-in never asks OTP; stepUp=true asks OTP or leads to TOTP enrolment | Needs the updated application deployment | NOT_RUN |
| 2 | CFG-008 | Own two-step preference under OPTIONAL: enable only on a verified session; disable needs a recent factor, ends sessions, and is audited (absorbs CFG-017) | Needs the updated application deployment | NOT_RUN |
| 3 | CFG-016 | Policy mutation authorization ladder (refusals only): guest, customer, ungranted staff, bootstrap admin, and granted staff without recent MFA; no state change | Needs the updated application deployment | NOT_RUN |
| 4 | CFG-104 | Staff high-risk actions (grants) require recent MFA regardless of policy mode; a password-only staff session is refused and the step-up path works (isolated, separate synthetic staff accounts) | Needs the updated application deployment | NOT_RUN |

## J8 — Physical devices

Account switch and step-up on a real iPhone/Android build.

| # | Case | Title | Readiness | Status |
|---|---|---|---|---|
| 1 | CFG-046 | Physical iPhone build identity and baseline: install from TestFlight, record build/version, reach the API, sign in and out (device plan A1, A2, B1, B4, B5, D2) | Needs a physical iOS/Android build | NOT_RUN |
| 2 | CFG-024 | iPhone step-up for the same customer: banner, system-browser verification, tokens replaced, refused order placement re-sent only by explicit retry with the same idempotency key (device plan C2, C3) | Needs a physical iOS/Android build | NOT_RUN |
| 3 | CFG-025 | iPhone step-up that signs in as a DIFFERENT person is an account switch: previous profile, basket, orders and caches are gone before the new person is shown (device plan B5/C3) | Needs a physical iOS/Android build | NOT_RUN |
| 4 | E2E-BASKET-027 | Native account switch clears the previous person's basket, pending order and screens | Needs a physical iOS/Android build | NOT_RUN |
