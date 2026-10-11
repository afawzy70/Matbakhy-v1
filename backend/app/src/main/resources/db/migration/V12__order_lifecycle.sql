-- MB-019: order lifecycle and secure handover.

ALTER TABLE customer_order ADD COLUMN dispatch_reference text CHECK (dispatch_reference IS NULL OR length(dispatch_reference) BETWEEN 1 AND 200);
ALTER TABLE customer_order ADD COLUMN terminal_at timestamptz;

-- Terminal orders are immutable: no column may change once the state is terminal.
CREATE FUNCTION customer_order_terminal_guard() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    IF OLD.state IN ('COMPLETED', 'REJECTED', 'EXPIRED', 'CANCELLED', 'DELIVERY_FAILED') THEN
        RAISE EXCEPTION 'terminal order % is immutable', OLD.id;
    END IF;
    RETURN NEW;
END $$;

CREATE TRIGGER customer_order_terminal_guard BEFORE UPDATE ON customer_order FOR EACH ROW EXECUTE FUNCTION customer_order_terminal_guard();
CREATE TRIGGER customer_order_no_delete BEFORE DELETE ON customer_order FOR EACH ROW EXECUTE FUNCTION order_event_append_only();

-- One handover challenge per order, created when it becomes READY. Only a keyed hash (verification) and an
-- AES-GCM ciphertext (display to the order owner) are stored; never the plain code.
CREATE TABLE handover_challenge (
    order_id     uuid        PRIMARY KEY REFERENCES customer_order (id),
    code_hash    text        NOT NULL CHECK (code_hash ~ '^[0-9a-f]{64}$'),
    ciphertext   bytea       NOT NULL,
    nonce        bytea       NOT NULL,
    attempts     integer     NOT NULL DEFAULT 0 CHECK (attempts BETWEEN 0 AND 5),
    expires_at   timestamptz NOT NULL,
    locked_at    timestamptz,
    consumed_at  timestamptz,
    created_at   timestamptz NOT NULL
);

-- Every code attempt, for per-actor rate limiting and investigation.
CREATE TABLE handover_attempt (
    id          uuid        PRIMARY KEY,
    order_id    uuid        NOT NULL REFERENCES customer_order (id),
    actor_id    uuid        NOT NULL REFERENCES app_user (id),
    success     boolean     NOT NULL,
    attempted_at timestamptz NOT NULL
);
CREATE INDEX handover_attempt_actor_idx ON handover_attempt (actor_id, attempted_at);
