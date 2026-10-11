-- MB-018: atomic order placement. One transaction writes the order, item snapshots, stock reservations,
-- the first event, an outbox message and the cash obligation; a failure leaves none of them.

-- Terms version the customer accepts at placement (synthetic until O04/O06 provide real terms).
ALTER TABLE commerce_policy ADD COLUMN terms_version text CHECK (terms_version IS NULL OR length(terms_version) BETWEEN 1 AND 40);
UPDATE commerce_policy SET terms_version = 'synthetic-terms-v1' WHERE synthetic;
ALTER TABLE commerce_policy ALTER COLUMN terms_version SET NOT NULL;

CREATE SEQUENCE order_public_number_seq START 100001;

CREATE TABLE customer_order (
    id                          uuid        PRIMARY KEY,
    public_number               text        NOT NULL UNIQUE,
    customer_id                 uuid        NOT NULL REFERENCES app_user (id),
    client_request_id           uuid        NOT NULL,
    quote_id                    uuid        NOT NULL UNIQUE REFERENCES quote (id),
    kitchen_id                  uuid        NOT NULL REFERENCES kitchen (id),
    slot_id                     uuid        NOT NULL REFERENCES cook_slot (id),
    state                       text        NOT NULL CHECK (state IN ('PENDING_ACCEPTANCE', 'ACCEPTED', 'PREPARING', 'READY', 'OUT_FOR_DELIVERY',
                                                                  'COMPLETED', 'REJECTED', 'EXPIRED', 'CANCELLED', 'DELIVERY_FAILED')),
    fulfillment_mode            text        NOT NULL CHECK (fulfillment_mode IN ('PICKUP', 'DELIVERY')),
    address_snapshot            jsonb,
    contact_snapshot            jsonb       NOT NULL,
    fulfillment_note            text        CHECK (fulfillment_note IS NULL OR length(fulfillment_note) <= 500),
    subtotal_minor              bigint      NOT NULL CHECK (subtotal_minor > 0),
    delivery_fee_minor          bigint      NOT NULL CHECK (delivery_fee_minor >= 0),
    tax_minor                   bigint      NOT NULL CHECK (tax_minor >= 0),
    total_minor                 bigint      NOT NULL,
    currency                    text        NOT NULL DEFAULT 'EGP' CHECK (currency = 'EGP'),
    policy_version              integer     NOT NULL REFERENCES commerce_policy (version),
    commission_bps_snapshot     integer     NOT NULL CHECK (commission_bps_snapshot BETWEEN 0 AND 10000),
    commission_minor            bigint      NOT NULL CHECK (commission_minor >= 0),
    cancellation_policy_version text        NOT NULL,
    terms_version               text        NOT NULL,
    accept_by                   timestamptz NOT NULL,
    created_at                  timestamptz NOT NULL,
    updated_at                  timestamptz NOT NULL,
    version                     bigint      NOT NULL DEFAULT 0,
    -- Permanent: survives expiry of cached idempotency responses (BR-023).
    CONSTRAINT customer_order_request_uq UNIQUE (customer_id, client_request_id),
    CONSTRAINT customer_order_total_ck CHECK (total_minor = subtotal_minor + delivery_fee_minor + tax_minor),
    CONSTRAINT customer_order_mode_ck CHECK ((fulfillment_mode = 'DELIVERY') = (address_snapshot IS NOT NULL)),
    CONSTRAINT customer_order_pickup_free_ck CHECK (fulfillment_mode = 'DELIVERY' OR delivery_fee_minor = 0)
);
CREATE INDEX customer_order_customer_idx ON customer_order (customer_id, created_at DESC, id DESC);
CREATE INDEX customer_order_kitchen_idx ON customer_order (kitchen_id, state, created_at, id);
CREATE INDEX customer_order_accept_by_idx ON customer_order (state, accept_by);

CREATE TABLE order_item (
    id                uuid    PRIMARY KEY,
    order_id          uuid    NOT NULL REFERENCES customer_order (id),
    meal_id           uuid    NOT NULL REFERENCES meal (id),
    meal_revision     integer NOT NULL,
    meal_snapshot     jsonb   NOT NULL,
    quantity          integer NOT NULL CHECK (quantity BETWEEN 1 AND 99),
    unit_price_minor  bigint  NOT NULL CHECK (unit_price_minor > 0),
    line_total_minor  bigint  NOT NULL,
    UNIQUE (order_id, meal_id),
    FOREIGN KEY (meal_id, meal_revision) REFERENCES meal_revision (meal_id, revision),
    CONSTRAINT order_item_line_ck CHECK (line_total_minor = unit_price_minor * quantity)
);

CREATE TABLE stock_allocation (
    id            uuid        PRIMARY KEY,
    order_id      uuid        NOT NULL REFERENCES customer_order (id),
    meal_slot_id  uuid        NOT NULL REFERENCES meal_slot (id),
    quantity      integer     NOT NULL CHECK (quantity >= 1),
    state         text        NOT NULL CHECK (state IN ('RESERVED', 'ALLOCATED', 'CONSUMED', 'RELEASED')),
    created_at    timestamptz NOT NULL,
    released_at   timestamptz,
    UNIQUE (order_id, meal_slot_id),
    CONSTRAINT stock_allocation_release_ck CHECK ((state = 'RELEASED') = (released_at IS NOT NULL))
);

CREATE TABLE order_event (
    id           uuid        PRIMARY KEY,
    order_id     uuid        NOT NULL REFERENCES customer_order (id),
    sequence     integer     NOT NULL CHECK (sequence >= 1),
    from_state   text,
    to_state     text        NOT NULL,
    actor_type   text        NOT NULL CHECK (actor_type IN ('CUSTOMER', 'COOK', 'STAFF', 'SYSTEM')),
    actor_id     uuid        REFERENCES app_user (id),
    reason_code  text,
    occurred_at  timestamptz NOT NULL,
    UNIQUE (order_id, sequence)
);

CREATE FUNCTION order_event_append_only() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    RAISE EXCEPTION 'order_event is append-only';
END $$;

CREATE TRIGGER order_event_append_only BEFORE UPDATE OR DELETE ON order_event FOR EACH ROW EXECUTE FUNCTION order_event_append_only();
CREATE TRIGGER order_item_immutable BEFORE UPDATE OR DELETE ON order_item FOR EACH ROW EXECUTE FUNCTION order_event_append_only();

-- Transactional outbox: delivered later by a worker (no SMS/email inside the order transaction).
CREATE TABLE outbox_event (
    id             uuid        PRIMARY KEY,
    aggregate_type text        NOT NULL,
    aggregate_id   uuid        NOT NULL,
    event_type     text        NOT NULL,
    payload        jsonb       NOT NULL,
    status         text        NOT NULL DEFAULT 'PENDING' CHECK (status IN ('PENDING', 'SENT', 'FAILED')),
    attempts       integer     NOT NULL DEFAULT 0 CHECK (attempts >= 0),
    available_at   timestamptz NOT NULL,
    created_at     timestamptz NOT NULL
);
CREATE INDEX outbox_event_pending_idx ON outbox_event (status, available_at);

-- Cash-first obligation created at placement (D05/O02); collection reporting and verification are finance stories.
CREATE TABLE cash_obligation (
    id           uuid        PRIMARY KEY,
    order_id     uuid        NOT NULL UNIQUE REFERENCES customer_order (id),
    amount_minor bigint      NOT NULL CHECK (amount_minor >= 0),
    currency     text        NOT NULL DEFAULT 'EGP' CHECK (currency = 'EGP'),
    state        text        NOT NULL CHECK (state IN ('DUE', 'REPORTED', 'VERIFIED', 'DISPUTED', 'NOT_DUE')),
    created_at   timestamptz NOT NULL,
    version      bigint      NOT NULL DEFAULT 0
);
