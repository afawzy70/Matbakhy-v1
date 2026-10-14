-- MB-052 follow-up: a disputed deposit, a payment awaiting reconciliation or a refund obligation opens a support case
-- (category PAYMENT) so staff own the human reconciliation. At most one open PAYMENT case per order, also under retries
-- and concurrent transitions.
ALTER TABLE support_case DROP CONSTRAINT support_case_category_check;
ALTER TABLE support_case ADD CONSTRAINT support_case_category_check CHECK (category IN ('LATE', 'MISSING_ITEM', 'QUALITY', 'FOOD_SAFETY', 'CASH',
    'HANDOVER', 'OTHER', 'CANCELLATION_REQUEST', 'PAYMENT'));
CREATE UNIQUE INDEX support_case_open_payment_uq ON support_case (order_id)
    WHERE category = 'PAYMENT' AND state NOT IN ('RESOLVED', 'CLOSED');
