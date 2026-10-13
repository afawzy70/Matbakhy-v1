-- MB-052 (owner decision D16): reconciliation outcomes for deposits. REFUND_OWED is a staff decision with a reason (the
-- kitchen owes the customer); REFUNDED and CLOSED_NO_FUNDS record what the kitchen or staff stated. No outcome marks
-- money as received by Matbakhy or as bank verified.
ALTER TABLE order_payment DROP CONSTRAINT order_payment_state_check;
ALTER TABLE order_payment ADD CONSTRAINT order_payment_state_check CHECK (state IN (
    'NOT_REQUIRED', 'WAITING_FOR_DEPOSIT', 'CUSTOMER_TRANSFER_REPORTED', 'KITCHEN_RECEIPT_REPORTED', 'DISPUTED', 'RECONCILE_PENDING', 'EXPIRED',
    'REFUND_OWED', 'REFUNDED', 'CLOSED_NO_FUNDS'));
ALTER TABLE payment_event ADD COLUMN note text CHECK (note IS NULL OR length(note) BETWEEN 3 AND 500);
