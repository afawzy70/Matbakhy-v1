# Deposit payment states (MB-051, owner decision D16)

Status: server parts of MB-051 and MB-052 implemented and tested locally (self-review). Customer screens are MB-053; cook and ops screens are the rest of MB-052. Development and test data only: **no live money** until the D16 gates clear (legal/consumer, food business, and recipient-account/provider review).

## Terms

- **Kitchen payment profile** (`kitchen_payment_profile`):
  - `deposit_bps` (0 means no deposit is offered);
  - `cod_max_total_minor` (empty means COD for any total);
  - the recipient (InstaPay, mobile wallet or bank transfer: identifier and name). The recipient must be **approved** before a deposit can be quoted. Staff approval is MB-052; for now, test fixtures set it.
- **Quote:** the customer chooses `COD` (the default) or `DEPOSIT`.
  - The server computes the deposit, rounding half up to whole piastres, at least 1, and the remainder in integer EGP.
  - COD above the kitchen's cap is refused (`COD_NOT_ELIGIBLE`). A deposit is refused when none is offered (`DEPOSIT_NOT_OFFERED`).
  - Changed terms make an old quote stale: 409 `PRICE_CHANGED` with reason `PAYMENT_CHANGED`, the same protocol as D13.
- **Order:** `order_payment` holds an immutable snapshot of the method, amounts, recipient and deposit deadline. Only its state and version move. Cash due at hand-over is the remainder.

## States (separate from the order state)

```
COD:      NOT_REQUIRED
DEPOSIT:  WAITING_FOR_DEPOSIT ──customer reports (API-100)──▶ CUSTOMER_TRANSFER_REPORTED
                 │                                                   │
                 └──────────kitchen reports receipt (API-101)────────┴──▶ KITCHEN_RECEIPT_REPORTED
kitchen reports a problem (API-105): WAITING | CUSTOMER_TRANSFER_REPORTED | RECONCILE_PENDING ─▶ DISPUTED
kitchen finds the money after all (API-101, order still active): DISPUTED ─▶ KITCHEN_RECEIPT_REPORTED
order rejected / cancelled / expired:
  WAITING_FOR_DEPOSIT ─▶ EXPIRED
  CUSTOMER_TRANSFER_REPORTED | KITCHEN_RECEIPT_REPORTED | DISPUTED ─▶ RECONCILE_PENDING
staff decision on an ended order (API-107, finance.verify, recent MFA, reason, audited):
  DISPUTED | RECONCILE_PENDING ─▶ REFUND_OWED | CLOSED_NO_FUNDS        (never "received")
kitchen reports the refund it sent (API-108, reference required):
  REFUND_OWED | RECONCILE_PENDING ─▶ REFUNDED
```

MB-052 also adds the kitchen's own payment profile (API-102/103). A new or changed recipient goes back to NONE until staff approve it (API-104: finance.verify, recent MFA, reason, audited, never for the approver's own kitchen). The ops queue (API-106) lists DISPUTED, RECONCILE_PENDING and REFUND_OWED payments.

- The customer's report is idempotent. It is information for the kitchen, **not payment**.
- The kitchen's report needs the exact deposit amount and the current version. It is recorded as the kitchen's statement, never as a bank confirmation. "Not received", a different amount and disputes are MB-052.
- **Preparation** of a deposit order is refused (`DEPOSIT_NOT_RECEIVED`) until the kitchen has reported receipt. Acceptance stays a separate kitchen action.
- Every change is written to the append-only `payment_event` (actor, amount, reference, reason, request ID).

## Known limits (proposals; owner review)

- The deposit is due by the order's acceptance deadline (at most 15 minutes, synthetic policy). A longer deposit window for orders far in the future is a proposal, not implemented.
- If the kitchen accepts before the deposit arrives and it never arrives, nothing expires the order automatically. The kitchen can cancel it, and the payment then goes to EXPIRED or RECONCILE_PENDING. An automatic deposit timeout for accepted orders is a proposal.
- The amount of the deposit (`deposit_bps`) and the COD cap are kitchen settings without approved values. The tests use 50% and EGP 200.00 as synthetic data.
- A dispute or refund obligation does not yet open a support case automatically. Ops see it in the payment queue, and the customer can open a case from the order.
- No customer notification is sent for payment state changes yet. The order view shows the state.
