# MVP decision: direct-to-cook deposit using familiar Egyptian payment methods
Date: 2026-10-13
Status: OWNER APPROVED AS MVP PRODUCT SCOPE; IMPLEMENTATION NOT VERIFIED; REAL-MONEY PILOT BLOCKED UNTIL GATES CLEARED.
Related: docs/product/01-context-and-decisions.md, docs/strategy/order-protection-egypt-draft.md, issues #3 and #4.

## Commercial intent
Maximize cook/customer adoption, preserve familiar Egyptian payment behavior and protect cooks from costly no-shows. No invented proprietary wallet, Matbakhy escrow, custody, stored cards or mandatory bespoke payment rails.

## MVP methods
1. Cash on delivery / pickup when permitted by cook and operation policy.
2. Deposit paid directly by customer to the approved kitchen recipient via InstaPay, eligible regulated mobile wallet or permitted bank transfer. Display the correct account recipient and clear amount, expiry, refund/cancellation terms. Do not imply the platform verifies or guarantees payment.
3. An integration with a payment processor, automated reconciliation, insurance/guarantee fund or automated payouts is explicitly DEFERRED.

## Required user journey and acceptance
- Customer obtains server-calculated quote (immutable after order placement) showing total, required deposit, remaining due, actual recipient, currency EGP, method, expiry and cancellation/refund terms. No secret kitchen phone or account leaked except intentionally public approved recipient payment identifier.
- Order starts as WAITING_FOR_DEPOSIT if a deposit is required. Show 'I transferred' action with optional transfer reference, timestamp and privacy-safe proof; customer assertion is not confirmation. No screenshot alone proves payment.
- Kitchen checks actual bank/wallet incoming funds, then explicitly marks DEPOSIT_RECEIPT_REPORTED with timestamp, authenticated kitchen identity, amount and optional transaction reference. This is a **kitchen-reported acknowledgement**, not independently verified bank receipt. Kitchen separately accepts the order; preparation remains disabled until both states present.
- Kitchen can report not received or mismatched amount; the system pauses processing and notifies customer, allows timed reconciliation and ops dispute handling. Never demand second payment automatically if transfer state is uncertain. Timeouts do not silently retain any payment.
- Orders remain capacity-safe with bounded reservation and recheck on payment/acceptance. Any cancellation or expiry after an asserted transfer triggers reconciliation before closing; if kitchen declines after verified-by-kitchen receipt, refund obligation and clear owner are recorded.
- Cook dashboard differentiates payment pending, reported by cook, COD, disputed, refunded, and owed. Staff can see immutable timeline but cannot fabricate bank confirmation or mark funds paid absent evidence.
- Handover code verifies fulfillment; settlement/cash reports are separate from fulfillment. Existing order price snapshots cannot be repriced, and deposit changes before placement require re-quote.
- No automatic cook payment guarantee, no customer waiver of legal rights, no false 'escrow protected' badge. Consumer-facing parties, payment recipient, cancellation and dispute channels must be prominent in Arabic.

## Verification scenarios to add to implementation backlog
- COD normal happy path and COD no-show.
- Direct transfer confirmed by kitchen, order accepted, prepaid deposit applied once, remainder COD, settlement audit.
- Customer presses 'transferred' without paying; cook reports not received; preparation blocked.
- Forged receipt image; no auto acceptance.
- Amount/recipient/reference mismatch; under-review branch.
- Transfer received but kitchen refuses/unable to fulfill; refund obligation and support case.
- Transfer received just before expiry; asynchronous reconciliation, avoid false cancel/double pay.
- Repeat click, network failure, concurrent acceptance, last-portion conflicts, partial refund, disputed handover.
- Customer and cook never see each other's unnecessary private payment/contact data.
- Mobile (Android/iOS) and web use same states and server constraints; backoffice audit/permissions.

## Deployment / legal / operational stop gates
Owner has approved product direction, not unconditional lawful payment use. Before any live payment: qualified Egyptian counsel examines marketplace agency/consumer obligations, applicable remote-sale cancellations/refunds, food-kitchen licensing and privacy; payment providers/banks confirm cook account/wallet may receive **commercial** funds; platform discloses who collects, delivers, refunds, and handles disputes; support/reconciliation SLA, evidence retention, chargeback/refund handling and dispute processes are approved. No claim Matbakhy is wholly exempt from legal responsibility merely because funds bypass it.

## Deferred exploration
Issue #3 retains broader automated verification, payout processing, cook loss protection and guarantees. Issue #4 AI Concierge cannot imply payment is complete from a chat or create a financially binding order without explicit checkout confirmation.
