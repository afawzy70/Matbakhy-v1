-- MB-025: full refunds bounded by verified collection, commission reversal, reviewed settlements with remittance evidence.

ALTER TABLE ledger_group DROP CONSTRAINT ledger_group_kind_check;
ALTER TABLE ledger_group ADD CONSTRAINT ledger_group_kind_check CHECK (kind IN ('COMMISSION', 'COMMISSION_REVERSAL'));

-- Refund obligations. Approval does not mean paid; PAID needs an external payment reference; VERIFIED by another person.
CREATE TABLE refund (
    id               uuid        PRIMARY KEY,
    order_id         uuid        NOT NULL REFERENCES customer_order (id),
    kitchen_id       uuid        NOT NULL REFERENCES kitchen (id),
    case_reference   text        NOT NULL CHECK (length(case_reference) BETWEEN 3 AND 100),
    amount_minor     bigint      NOT NULL CHECK (amount_minor > 0),
    state            text        NOT NULL CHECK (state IN ('REQUESTED', 'APPROVED', 'PAID', 'VERIFIED', 'REJECTED')),
    reason           text        NOT NULL CHECK (length(reason) BETWEEN 3 AND 1000),
    requested_by     uuid        NOT NULL REFERENCES app_user (id),
    approved_by      uuid        REFERENCES app_user (id),
    paid_by          uuid        REFERENCES app_user (id),
    payment_reference text       CHECK (payment_reference IS NULL OR length(payment_reference) BETWEEN 3 AND 200),
    verified_by      uuid        REFERENCES app_user (id),
    decision_reason  text,
    created_at       timestamptz NOT NULL,
    updated_at       timestamptz NOT NULL,
    version          bigint      NOT NULL DEFAULT 0,
    CONSTRAINT refund_approver_ck CHECK (approved_by IS NULL OR approved_by <> requested_by),
    CONSTRAINT refund_verifier_ck CHECK (verified_by IS NULL OR verified_by <> paid_by),
    CONSTRAINT refund_paid_ck CHECK (state NOT IN ('PAID', 'VERIFIED') OR payment_reference IS NOT NULL)
);
CREATE INDEX refund_order_idx ON refund (order_id);
CREATE INDEX refund_state_idx ON refund (state, created_at);

-- Settlement batches: the cook remits commission to the platform. Lines are unique per ledger group among active batches.
CREATE TABLE settlement_batch (
    id                uuid        PRIMARY KEY,
    kitchen_id        uuid        NOT NULL REFERENCES kitchen (id),
    period_from       date        NOT NULL,
    period_to         date        NOT NULL,
    state             text        NOT NULL CHECK (state IN ('DRAFT', 'REVIEWED', 'PAYMENT_RECORDED', 'RECONCILED', 'CANCELLED')),
    total_minor       bigint      NOT NULL,
    created_by        uuid        NOT NULL REFERENCES app_user (id),
    reviewed_by       uuid        REFERENCES app_user (id),
    payment_reference text        CHECK (payment_reference IS NULL OR length(payment_reference) BETWEEN 3 AND 200),
    payment_recorded_by uuid      REFERENCES app_user (id),
    reconciled_by     uuid        REFERENCES app_user (id),
    created_at        timestamptz NOT NULL,
    updated_at        timestamptz NOT NULL,
    version           bigint      NOT NULL DEFAULT 0,
    CONSTRAINT settlement_period_ck CHECK (period_to >= period_from),
    CONSTRAINT settlement_reviewer_ck CHECK (reviewed_by IS NULL OR reviewed_by <> created_by),
    CONSTRAINT settlement_reconciler_ck CHECK (reconciled_by IS NULL OR reconciled_by <> payment_recorded_by),
    CONSTRAINT settlement_payment_ck CHECK (state NOT IN ('PAYMENT_RECORDED', 'RECONCILED') OR payment_reference IS NOT NULL)
);

CREATE TABLE settlement_line (
    batch_id        uuid    NOT NULL REFERENCES settlement_batch (id),
    ledger_group_id uuid    NOT NULL REFERENCES ledger_group (id),
    amount_minor    bigint  NOT NULL,
    active          boolean NOT NULL DEFAULT true,
    PRIMARY KEY (batch_id, ledger_group_id)
);
CREATE UNIQUE INDEX settlement_line_once_uq ON settlement_line (ledger_group_id) WHERE active;

-- Lines never change except being released when their batch is cancelled; nothing is ever deleted.
CREATE FUNCTION settlement_line_guard() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE
    batch_state text;
BEGIN
    IF TG_OP = 'DELETE' THEN
        RAISE EXCEPTION 'settlement_line is append-only';
    END IF;
    SELECT state INTO batch_state FROM settlement_batch WHERE id = NEW.batch_id;
    IF TG_OP = 'INSERT' AND batch_state <> 'DRAFT' THEN
        RAISE EXCEPTION 'settlement batch % is %, lines are immutable', NEW.batch_id, batch_state;
    END IF;
    IF TG_OP = 'UPDATE' AND NOT (batch_state = 'CANCELLED' AND OLD.active AND NOT NEW.active
            AND NEW.ledger_group_id = OLD.ledger_group_id AND NEW.amount_minor = OLD.amount_minor) THEN
        RAISE EXCEPTION 'settlement lines are immutable';
    END IF;
    RETURN NEW;
END $$;
CREATE TRIGGER settlement_line_guard BEFORE INSERT OR UPDATE OR DELETE ON settlement_line FOR EACH ROW EXECUTE FUNCTION settlement_line_guard();
