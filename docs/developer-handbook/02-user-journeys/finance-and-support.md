# User journeys: finance and support

> **Owner role:** Business documentation
> **Status:** PARTIALLY IMPLEMENTED — deposit reconciliation, refunds, settlements, statements and support cases have API and screens; cash-report verification has an API but no screen; all money is synthetic and live money is gated.

Money rules to keep in mind: zero Matbakhy commission and no customer platform fee in the MVP; Matbakhy never holds customer money; a deposit is a manual direct transfer from the customer to the kitchen's approved recipient and the kitchen **reports** receipt (never "bank verified"). Background: [Pricing and payments](../01-business/pricing-and-payments.md), `docs/product/09-deposit-payment-states.md`, [Payment implementation](../04-codebase/payment-implementation.md). Staff permissions: [Backoffice administration](backoffice-admin.md).

Common items. **Actor:** staff with the permission named per journey; cook and customer for their own halves. **Transport:** web session + CSRF. **Audit:** every staff decision writes `audit_event`. **Tests (all):** `backend/app/src/test/java/com/matbakhy/app/finance/CashAndLedgerIntegrationTest.java`, `FinanceOperationsIntegrationTest.java`, `ZeroCommissionIntegrationTest.java`, `backend/app/src/test/java/com/matbakhy/app/payments/DepositPaymentIntegrationTest.java`, `PaymentReconciliationIntegrationTest.java`, `backend/app/src/test/java/com/matbakhy/app/support/cases/SupportIntegrationTest.java`, `apps/operations-web/src/App.test.tsx`, `apps/operations-web/e2e/money-reflow.e2e.ts`. Results for the current commit: NOT VERIFIED here.

## FS-1 Cash on receipt: report and verification

1. **Goal.** Record that the kitchen collected cash due at handover and let finance verify the exact amount.
2. **Actor.** Cook reports; staff with `finance.verify` verifies; `finance.read` may list.
3. **Prerequisites.** Order `COMPLETED`, cash obligation `DUE`.
4. **Start.** Cook: order card ("cash report"). Finance: **no screen** (API only).
5. **Actions.** Cook posts amount, currency, collection time, method and external reference once per order. Finance lists reports and verifies or disputes with the verified amount.
6. **Responses.** VERIFY must equal the amount due, otherwise it must be a DISPUTE with a reason of at least 3 characters (`REQUIRED_FOR_EXCEPTIONS`, `MUST_EQUAL_AMOUNT_DUE_OR_DISPUTE`). The person who reported can never verify (`FORBIDDEN separation of duties`, denial audited).
7. **API.** `POST /api/v1/cook/orders/{orderId}/cash-reports` (063), `GET /api/v1/ops/finance/cash-reports` (082), `POST /api/v1/ops/finance/cash-reports/{reportId}/verification` (083).
8. **Backend.** `backend/modules/finance/src/main/java/com/matbakhy/finance/web/CashController.java` → `internal/CashService.java` (`report`, `verify`, `refuseSelfVerification`).
9. **DB.** `cash_obligation` (V11), `cash_report` (V13).
10. **Transitions.** Cash obligation `DUE → REPORTED → VERIFIED | DISPUTED`; `NOT_DUE` for rejected, cancelled or expired orders.
11. **Errors.** `STATE_CONFLICT` (report before handover, already reported, already decided), `VERSION_CONFLICT`.
12. **Tests.** `CashAndLedgerIntegrationTest.java`, `FinanceOperationsIntegrationTest.java`.
13. **Owner E2E.** E2E-FIN-038, E2E-FIN-043, E2E-FIN-102, E2E-FIN-114; J2.
14. **Status.** API IMPLEMENTED; finance verification UI absent (searched `apps/operations-web/src/ops`: no call to `cash-reports`).
15. **Limitations.** Owner cases that verify cash must use the API.

## FS-2 Deposit payments and reconciliation

1. **Goal.** Handle a manual 50% (kitchen-configured) deposit from placement to reconciliation.
2. **Actor.** Customer (reports transfer), cook (reports receipt, problem, refund), finance staff `finance.verify` (approves recipient, decides disputes).
3. **Prerequisites.** Kitchen has an **approved** payment recipient; otherwise deposits are not offered and cash still works (E2E-FIN-105). A changed recipient stays hidden until staff approve (E2E-FIN-103).
4. **Start.** Customer order page; cook order card and settings (`apps/operations-web/src/cook/Payments.tsx`); finance tab payment panels (`apps/operations-web/src/ops/Payments.tsx`).
5. **Actions.** Customer: "I sent the deposit" with an optional reference. Cook: "received" (amount must equal the server deposit), "not received", "different amount", later "refund sent" with a reference. Finance: approve recipient with a reason; list queue by state `DISPUTED | RECONCILE_PENDING | REFUND_OWED`; decide `REFUND_OWED` or `CLOSED_NO_FUNDS` once the order has ended.
6. **Responses.** A deposit order cannot move to `PREPARING` until the kitchen reports receipt (acceptance is separate, E2E-FIN-106). "Not received" opens one PAYMENT support case per order (V28) and leaves the order unchanged.
7. **API.** 100, 101, 102, 103, 104, 105, 106, 107, 108, 109 (`POST /api/v1/orders/{orderId}/payment/transfer-report`, `POST /api/v1/cook/orders/{orderId}/payment/receipt|problem|refund`, `GET/PUT /api/v1/cook/payment-profile`, `POST /api/v1/ops/kitchens/{kitchenId}/payment-recipient/approval`, `GET /api/v1/ops/payments`, `POST /api/v1/ops/orders/{orderId}/payment/decision`, `GET /api/v1/ops/payment-recipients`).
8. **Backend.** `backend/modules/orders/src/main/java/com/matbakhy/orders/web/PaymentController.java` → `internal/PaymentService.java` (`reportTransfer`, `kitchenReceipt`, `reportProblem`, `decide`, `reportRefund`, `requireReadyForPreparation`, `approveRecipient`).
9. **DB.** `kitchen_payment_profile`, `order_payment`, `payment_event` (V24), reconciliation additions (V25), `support_case` link (V28).
10. **Transitions.** `NOT_REQUIRED` (COD) or `WAITING_FOR_DEPOSIT` → `CUSTOMER_TRANSFER_REPORTED` → `KITCHEN_RECEIPT_REPORTED`; problems → `DISPUTED`; ended orders → `RECONCILE_PENDING`, `EXPIRED`, `REFUND_OWED`, `REFUNDED`, `CLOSED_NO_FUNDS`. Exact list: V24 CHECK and `docs/product/09-deposit-payment-states.md` (the doc is context; the migration plus `PaymentService` are authoritative).
11. **Errors.** `ORDER_ENDED`, `NO_DEPOSIT_WAITING`, `NOTHING_TO_DISPUTE`, `NOTHING_TO_DECIDE`, `ORDER_ACTIVE`, recipient `MISSING`; clients cannot dictate amounts (E2E-FIN-041).
12. **Tests.** `DepositPaymentIntegrationTest.java`, `PaymentReconciliationIntegrationTest.java`, web `App.test.tsx` (MB-053 blocks in customer web and mobile).
13. **Owner E2E.** E2E-FIN-039 to E2E-FIN-042, E2E-FIN-045, E2E-FIN-101, E2E-FIN-103, E2E-FIN-106 to E2E-FIN-109, E2E-FIN-113, E2E-FIN-115, E2E-COOK-108; J3, J4.
14. **Status.** IMPLEMENTED with synthetic recipients; real money DISABLED by owner gate (E2E-FIN-047).
15. **Limitations.** No bank API, no automatic matching: the human reconciles.

## FS-3 Refunds, settlements and statements (finance)

1. **Goal.** Refund verified platform-collected cash in full, record settlements, read statements.
2. **Actor.** `finance.refund_approve`, `finance.settlement`, `finance.read`.
3. **Prerequisites.** Refund: cash state `VERIFIED` with a verified amount; requester and approver differ.
4. **Start.** Tab Finance: enter a kitchen UUID to load its statement; refund and settlement forms under it (`apps/operations-web/src/ops/Finance.tsx`).
5. **Actions.** Request a full refund (order id, case reference, amount, reason); then APPROVE, PAYMENT_RECORDED (evidence reference), VERIFY or REJECT; settlement batches REVIEW, PAYMENT_RECORDED, RECONCILE, CANCEL.
6. **Responses.** Only the exact refundable amount is accepted (`FULL_REFUND_REQUIRED`, `OVER_REFUND`); statements show no commission under the zero-commission policy (`hasCommission` in `apps/operations-web/src/api.ts`); under zero commission settlements are dormant, zero-amount entries cannot be batched (E2E-FIN-110).
7. **API.** 084, 085, 086, 087, 088 (`/api/v1/ops/finance/refunds`, `…/refunds/{refundId}/action`, `…/statements`, `…/settlements`, `…/settlements/{batchId}/action`); cook side `GET /api/v1/cook/statements` (064).
8. **Backend.** `FinanceOpsController.java` → `FinanceOpsService.java`; `StatementController.java` → `StatementService.java`; `LedgerService.java`, `LedgerPostingJob.java`.
9. **DB.** `refund`, `settlement_batch`, `settlement_line` (V18), `ledger_group` (kind `COMMISSION` only), `ledger_entry` (V13), `cash_report`.
10. **Transitions.** Refund `REQUESTED → APPROVED → PAID → VERIFIED` or `REJECTED` (approver ≠ requester; verifier ≠ payer). Settlement `DRAFT → REVIEWED → PAYMENT_RECORDED → RECONCILED | CANCELLED`.
11. **Errors.** `VERSION_CONFLICT`, `STATE_CONFLICT`, `FORBIDDEN` (separation), `VALIDATION_FAILED`.
12. **Tests.** `FinanceOperationsIntegrationTest.java`, `ZeroCommissionIntegrationTest.java`, `apps/operations-web/e2e/money-reflow.e2e.ts`.
13. **Owner E2E.** E2E-FIN-044, E2E-FIN-045, E2E-FIN-046, E2E-FIN-110, E2E-FIN-111, E2E-FIN-112.
14. **Status.** IMPLEMENTED (dormant ledger under zero commission).
15. **Limitations.** Statement lookup needs a kitchen UUID typed by hand; no kitchen search in the finance UI. Finance tab visibility is probe-based (see [Backoffice administration](backoffice-admin.md)).

## FS-4 Support cases (customer side and staff side)

1. **Goal.** Resolve a customer's problem about an order with a traceable conversation.
2. **Actor.** Customer (own cases); staff with `support.case` (all cases).
3. **Prerequisites.** A customer order; categories `LATE, MISSING_ITEM, QUALITY, FOOD_SAFETY, CASH, HANDOVER, OTHER`; cancellation requests after acceptance create category `CANCELLATION_REQUEST`.
4. **Start.** Customer: account page / order page; staff: tab Cases (`apps/operations-web/src/ops/Cases.tsx`).
5. **Actions.** Customer opens a case and replies. Staff filters (`state, category, severity, mine, overdue`), opens a case (every staff view is audited), posts a PUBLIC or STAFF_ONLY message, assigns to self, changes state, sets a resolution code (`INFORMATION_PROVIDED`, `ORDER_CANCELLED`, `CANCELLATION_DECLINED`, `REFUND_REQUESTED`, `KITCHEN_FOLLOW_UP`, `DUPLICATE`, `NO_ACTION`, `OTHER`) with a reason.
6. **Responses.** Customers see only public messages; internal notes stay hidden (E2E-OPS-057). A customer reply reopens a resolved case; only resolved cases can be closed; closed is final (E2E-OPS-105).
7. **API.** Customer 035 to 038 plus `POST /api/v1/orders/{orderId}/cancellation-requests` (028); staff 078 to 081.
8. **Backend.** `backend/modules/support/src/main/java/com/matbakhy/support/web/SupportController.java`, `StaffCaseController.java` (class-level `PERM_support.case`) → `internal/SupportService.java`.
9. **DB.** `support_case`, `case_message` (V17); payment link V28.
10. **Transitions.** `OPEN → IN_PROGRESS | WAITING_ON_CUSTOMER → RESOLVED → CLOSED`.
11. **Errors.** Another customer's case → 404; attachments are not accepted (`attachmentIds` max size 0); wrong state or version → conflicts.
12. **Tests.** `SupportIntegrationTest.java`, customer web `App.test.tsx` (MBQA-004).
13. **Owner E2E.** E2E-ORDER-118, E2E-OPS-057, E2E-OPS-105, E2E-ORDER-109.
14. **Status.** IMPLEMENTED. 15. **Limitations.** No attachments; no SLA notifications beyond the `overdue` filter.

## Known limitations and documentation gaps

* `docs/product/06-permissions.md` names `finance.settlement_verify`; code uses `finance.settlement`.
* The ledger only supports kind `COMMISSION`; with zero commission it carries no customer money. Deposits are never in the ledger (E2E-FIN-045).
* Cash-report verification UI is missing (FS-1).
