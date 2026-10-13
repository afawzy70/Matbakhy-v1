-- MB-051 (owner decision D16, 2026-10-13): eligible cash on delivery plus manual deposits paid directly by the customer
-- to the kitchen's approved recipient. No platform custody. The payment is tracked separately from the order and its
-- fulfilment: a customer's transfer report is not payment, and the kitchen's acknowledgement is recorded as reported by
-- the kitchen, never as bank verification. Development and test data only; live money is blocked (D16 gates).

-- The kitchen's payment terms. deposit_bps = 0: no deposit offered. cod_max_total_minor NULL: COD for any total.
-- The recipient must be approved (by staff, MB-052) before a deposit can be quoted.
CREATE TABLE kitchen_payment_profile (
    kitchen_id            uuid        PRIMARY KEY REFERENCES kitchen (id),
    deposit_bps           integer     NOT NULL DEFAULT 0 CHECK (deposit_bps BETWEEN 0 AND 10000),
    cod_max_total_minor   bigint      CHECK (cod_max_total_minor IS NULL OR cod_max_total_minor >= 0),
    recipient_method      text        CHECK (recipient_method IN ('INSTAPAY', 'MOBILE_WALLET', 'BANK_TRANSFER')),
    recipient_identifier  text        CHECK (recipient_identifier IS NULL OR length(recipient_identifier) BETWEEN 3 AND 64),
    recipient_name        text        CHECK (recipient_name IS NULL OR length(recipient_name) BETWEEN 2 AND 80),
    recipient_status      text        NOT NULL DEFAULT 'NONE' CHECK (recipient_status IN ('NONE', 'APPROVED')),
    approved_by           uuid        REFERENCES app_user (id),
    approved_at           timestamptz,
    version               bigint      NOT NULL DEFAULT 0,
    CONSTRAINT kitchen_payment_recipient_ck CHECK (
        recipient_status = 'NONE' OR (recipient_method IS NOT NULL AND recipient_identifier IS NOT NULL AND recipient_name IS NOT NULL
                                      AND approved_by IS NOT NULL AND approved_at IS NOT NULL))
);

-- Quotes carry the payment terms (quotes stay immutable).
ALTER TABLE quote ADD COLUMN payment_method text NOT NULL DEFAULT 'COD' CHECK (payment_method IN ('COD', 'DEPOSIT'));
ALTER TABLE quote ADD COLUMN deposit_bps integer NOT NULL DEFAULT 0 CHECK (deposit_bps BETWEEN 0 AND 10000);
ALTER TABLE quote ADD COLUMN deposit_minor bigint NOT NULL DEFAULT 0 CHECK (deposit_minor >= 0);
ALTER TABLE quote ADD COLUMN recipient_method text;
ALTER TABLE quote ADD COLUMN recipient_identifier text;
ALTER TABLE quote ADD COLUMN recipient_name text;
ALTER TABLE quote ADD CONSTRAINT quote_payment_ck CHECK (
    (payment_method = 'COD' AND deposit_minor = 0 AND recipient_identifier IS NULL)
    OR (payment_method = 'DEPOSIT' AND deposit_minor > 0 AND deposit_minor <= total_minor AND recipient_identifier IS NOT NULL));

-- One payment record per order: immutable terms (snapshot) and a separate, versioned state.
CREATE TABLE order_payment (
    order_id              uuid        PRIMARY KEY REFERENCES customer_order (id),
    method                text        NOT NULL CHECK (method IN ('COD', 'DEPOSIT')),
    total_minor           bigint      NOT NULL CHECK (total_minor >= 0),
    deposit_minor         bigint      NOT NULL CHECK (deposit_minor >= 0),
    remainder_minor       bigint      NOT NULL CHECK (remainder_minor >= 0),
    recipient_method      text,
    recipient_identifier  text,
    recipient_name        text,
    deposit_expires_at    timestamptz,
    state                 text        NOT NULL CHECK (state IN ('NOT_REQUIRED', 'WAITING_FOR_DEPOSIT', 'CUSTOMER_TRANSFER_REPORTED',
                                                                'KITCHEN_RECEIPT_REPORTED', 'DISPUTED', 'RECONCILE_PENDING', 'EXPIRED')),
    version               bigint      NOT NULL DEFAULT 0,
    updated_at            timestamptz NOT NULL,
    CONSTRAINT order_payment_amounts_ck CHECK (deposit_minor + remainder_minor = total_minor),
    CONSTRAINT order_payment_method_ck CHECK (
        (method = 'COD' AND deposit_minor = 0 AND state = 'NOT_REQUIRED' AND recipient_identifier IS NULL)
        OR (method = 'DEPOSIT' AND deposit_minor > 0 AND state <> 'NOT_REQUIRED' AND recipient_identifier IS NOT NULL
            AND deposit_expires_at IS NOT NULL))
);

-- The terms never change after placement; only state, version and updated_at may move.
CREATE FUNCTION order_payment_terms_immutable() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    IF TG_OP = 'DELETE' THEN
        RAISE EXCEPTION 'order_payment rows are never deleted';
    END IF;
    IF (to_jsonb(NEW) - ARRAY['state', 'version', 'updated_at']) <> (to_jsonb(OLD) - ARRAY['state', 'version', 'updated_at']) THEN
        RAISE EXCEPTION 'order payment terms are immutable';
    END IF;
    RETURN NEW;
END $$;
CREATE TRIGGER order_payment_terms_immutable BEFORE UPDATE OR DELETE ON order_payment FOR EACH ROW EXECUTE FUNCTION order_payment_terms_immutable();

-- Append-only payment timeline: who reported what, when, with which amount and reference.
CREATE TABLE payment_event (
    id            uuid        PRIMARY KEY,
    order_id      uuid        NOT NULL REFERENCES order_payment (order_id),
    from_state    text,
    to_state      text        NOT NULL,
    actor_type    text        NOT NULL CHECK (actor_type IN ('CUSTOMER', 'COOK', 'STAFF', 'SYSTEM')),
    actor_id      uuid,
    amount_minor  bigint      CHECK (amount_minor IS NULL OR amount_minor >= 0),
    reference     text        CHECK (reference IS NULL OR length(reference) BETWEEN 1 AND 64),
    reason_code   text        CHECK (reason_code IS NULL OR length(reason_code) BETWEEN 1 AND 64),
    request_id    text,
    occurred_at   timestamptz NOT NULL
);
CREATE INDEX payment_event_order_idx ON payment_event (order_id, occurred_at);
CREATE TRIGGER payment_event_append_only BEFORE UPDATE OR DELETE ON payment_event FOR EACH ROW EXECUTE FUNCTION order_event_append_only();
