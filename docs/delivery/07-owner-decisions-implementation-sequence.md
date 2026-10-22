# Matbakhy — approved direction translated into execution sequence
Date: 2026-10-13
Authority: product owner directions in docs/product/01-context-and-decisions.md.
Status: PLANNING/EXECUTION INSTRUCTIONS; nothing in this document claims code was implemented, tests passed, or production legality approved.

## Principle
Do not interrupt active Claude execution, redo completed work, or blindly reorder existing dependency-based stories. Inspect actual working branch, changed files, active work, latest evidence and status before merging this plan into local branch. Reconcile any drift without overwriting another writer. Changes to docs on default branch alone do not mean that executing Claude session pulled them.

## Stage 0: reconcile decisions (next eligible planning/checkpoint, not a destructive reset)
- Preserve free-at-launch product: ZERO platform commission/customer service fee, ZERO paid advertising/billing, no hidden monetization; transparent third-party delivery/transfer costs only.
- Update currently stale D10/O04, examples, commissions/revenue language, machine-readable decisions, scoped backlog stories, checkout/cook/ops screens, finance/ledger expected amounts and regression tests. All platform-commission ledger entries must remain 0/inactive at launch, rather than just hiding fees in UI. Preserve technical flexibility without enabling fee charges.
- MVP payment: eligible COD/pickup + manual direct customer-to-approved-cook recipient transfer/deposit by existing Egyptian payment instruments. No platform custody. Customer 'transferred' report is not evidence; cook-reported acknowledgement after checking their account is NOT independent bank verification.
- Broader protection fund, automated gateway, AI concierge and paid kitchen growth remain DEFERRED. No financial terms or production launch without explicit review.

## Stage 1: implement payment/ordering essentials at related existing backlog dependency stage
Prefer append/update mapped stories and scenarios instead of creating duplicate parallel workflows. 
- Quote item/total/fees/deposit due/remainder/recipient/method, snapshot displayed policy, order ID and immutable price/cost; re-quote price/capacity changes before placement.
- State machine for WAITING_FOR_DEPOSIT, CUSTOMER_TRANSFER_REPORTED, KITCHEN_RECEIPT_REPORTED, DISPUTED, EXPIRED/RECONCILE_PENDING, KITCHEN_ACCEPTED, PREPARING; separate payment-state from order and fulfillment state.
- Receipt acknowledgement identity, timestamps, amount/reference, reason, actor and audit trail; never label 'bank verified' or guarantee funds; enforce object authorization and protect beneficiary private data.
- No preparation for deposit-required orders before kitchen acknowledgment plus separate acceptance; expiry and capacity claims race-safe; verify amount/recipient mismatch, false receipts and pending transfers.
- Cook and ops interfaces: verify receipt externally, report acceptance/rejection, inspect disputes and refunds; customer web/iOS/Android checkout and status shared contract; acceptance restrictions apply regardless of conversational interface.
- Handling of direct refunds and cancellation must display seller's responsibility and support/escalation actions. No automatic legal waiver; audit external refunds and pending obligations truthfully.
- Use dev/test fixtures; all real-money processing and live account details BLOCKED until payment provider/cook commercial eligibility, Egyptian consumer/privacy/legal and kitchen licensing gates clear.

## Stage 2: free core and trustworthy growth instrumentation during relevant existing web/ops stages
- Record privacy-safe organic impressions, search/filter context (aggregate, no sensitive targeting), meal/cook detail views, add-to-cart, quote, order creation, fulfilled orders, cancellations/no-shows, repeat orders, kitchen slot capacity/stock, and order GMV.
- Provide essential free cook reporting (order counts, gross sales, refund adjustments, net amount received where genuinely known), show clear measurement definitions and time windows; **do not claim profit** unless kitchen voluntarily supplies accurate costs, and then label estimated margin.
- Event instrumentation must be deduped, consent/privacy-compliant, authorization scoped, robust to bot/refresh abuse, and not inflate analytics.
- No sponsored rankings, advertising payments, billing, paid campaign UX, popups or commercial thresholds in MVP. This stage creates capability to evaluate demand, NOT a monetization system.

## Stage 3: post-MVP growth readiness research and controlled opt-in pilots ONLY after explicit owner go
- Per-kitchen readiness: enough completed orders, reliable service quality, meaningful *estimated* unit economics and demand/capacity opportunity; no universal fixed threshold until real evidence.
- Free insight shown first; kitchens can explicitly request optional growth services. Then show cost/budget cap, duration, target service zone and relevant products, risks, non-guaranteed likely impact, organic baseline, user consent.
- Candidates: promote available bestseller; fill quiet slots; relevant catering exposure; photography/content optimization; consent-based reactivation.
- Sponsored placements clearly labelled, separate organic ranking, maintain fair free discovery. Attribute only credible incremental fulfilled orders, avoid paid boosts when kitchen can't fulfil demand.
- Pilot one feature with a few opted-in kitchens and evaluate contribution margin after promo costs, not impressions alone. Legal/tax, advertiser disclosures, invoice/refund permissions before any real sale.
- No automated triggering of paid charges and no degradation of free core ever.

## Stage 4: future AI Concierge and voice (Issue #4) / stronger protection (Issue #3)
- Text shopping assistant with read-only true catalog, location/availability, safe basket tools, explicit checkout confirmation; voice notes later then duplex voice only after benchmarks.
- Customer-controlled taste preferences and deletion; special care around allergies. Assistant cannot invent pricing/food or bypass kitchen acknowledgement/deposit policies.
- Automated payment verification/guarantee model only after pilot evidence and legal/provider readiness; never represent direct transfers as bank verified.

## Minimum dedicated regression scenarios
- No commission or platform service fee in all web, iOS, Android, cook and ops screens, server calculation and financial ledger.
- Eligible COD + deposit-required checkout with correct EGP amounts, recipients and remaining balance, matched immutable quote snapshot.
- Fake receipt, mismatched amount, concurrent last portion, delayed bank receipt claim, duplicate transfer report, user cancellation during payment uncertainty, cook unable to fulfil after transfer, manual refund, contact unreachable and no-show; audit and compensation boundaries.
- No prep until receipt acknowledged and accepted; no unwarranted status 'bank verified'.
- Analytics accurately distinguishes gross sales from net earnings and estimates; no hidden sponsored results/billing.
- Authorizations prevent cross-kitchen payment and growth data leakage; Egypt Arabic RTL and mobile parity.

## Work protocol for Claude
At start: fetch these latest decisions into the active working branch safely, inspect existing implementation and current task; identify modifications to story IDs, existing tests, docs and data migrations. Prepare a compact change matrix. Execute next dependency-eligible story, not every future feature at once. Work through normal verify-review-repair-checkpoint sequence. Do not mark any untested scenario PASS. Commit/push only as repo authorization allows, record exact SHA, and explain any blockers in Egyptian Arabic while implementation details stay English.

## Owner approval boundaries
Approved NOW: free core at launch; direct-to-kitchen manual transfer option in MVP alongside eligible COD; data gathering necessary for measuring growth readiness.
NOT approved NOW: launch of live-money flow without qualified review, live ads or fees, paid campaign products, a payment wallet/gateway, AI concierge implementation, spending, public release.
