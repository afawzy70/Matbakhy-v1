-- MB-017: versioned commerce policy and authoritative, immutable quotes. A quote holds no stock.

CREATE TABLE commerce_policy (
    version                     integer     PRIMARY KEY CHECK (version >= 1),
    label                       text        NOT NULL CHECK (length(label) BETWEEN 3 AND 120),
    synthetic                   boolean     NOT NULL,
    status                      text        NOT NULL CHECK (status IN ('DRAFT', 'ACTIVE', 'RETIRED')),
    tax_rate_bps                integer     NOT NULL CHECK (tax_rate_bps BETWEEN 0 AND 10000),
    commission_rate_bps         integer     NOT NULL CHECK (commission_rate_bps BETWEEN 0 AND 10000),
    max_item_quantity           integer     NOT NULL CHECK (max_item_quantity BETWEEN 1 AND 99),
    quote_ttl_seconds           integer     NOT NULL CHECK (quote_ttl_seconds BETWEEN 60 AND 3600),
    acceptance_window_minutes   integer     NOT NULL CHECK (acceptance_window_minutes BETWEEN 1 AND 1440),
    min_acceptance_minutes      integer     NOT NULL CHECK (min_acceptance_minutes BETWEEN 1 AND 120),
    cancellation_policy_version text        NOT NULL CHECK (length(cancellation_policy_version) BETWEEN 1 AND 40),
    approved_by                 uuid        REFERENCES app_user (id),
    approved_at                 timestamptz,
    created_at                  timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT commerce_policy_approval_ck CHECK ((approved_by IS NULL) = (approved_at IS NULL)),
    -- A real (non-synthetic) policy can only be active once approved.
    CONSTRAINT commerce_policy_active_approved_ck CHECK (status <> 'ACTIVE' OR synthetic OR approved_by IS NOT NULL)
);
CREATE UNIQUE INDEX commerce_policy_one_active_uq ON commerce_policy ((true)) WHERE status = 'ACTIVE';

CREATE TABLE quote (
    id                   uuid        PRIMARY KEY,
    customer_id          uuid        NOT NULL REFERENCES app_user (id),
    kitchen_id           uuid        NOT NULL REFERENCES kitchen (id),
    slot_id              uuid        NOT NULL REFERENCES cook_slot (id),
    fulfillment_mode     text        NOT NULL CHECK (fulfillment_mode IN ('PICKUP', 'DELIVERY')),
    address_id           uuid        REFERENCES address (id),
    address_version      bigint,
    zone_id              uuid        REFERENCES service_zone (id),
    policy_version       integer     NOT NULL REFERENCES commerce_policy (version),
    subtotal_minor       bigint      NOT NULL CHECK (subtotal_minor > 0),
    delivery_fee_minor   bigint      NOT NULL CHECK (delivery_fee_minor >= 0),
    tax_minor            bigint      NOT NULL CHECK (tax_minor >= 0),
    total_minor          bigint      NOT NULL,
    currency             text        NOT NULL DEFAULT 'EGP' CHECK (currency = 'EGP'),
    accept_by_preview    timestamptz NOT NULL,
    expires_at           timestamptz NOT NULL,
    created_at           timestamptz NOT NULL,
    CONSTRAINT quote_total_ck CHECK (total_minor = subtotal_minor + delivery_fee_minor + tax_minor),
    CONSTRAINT quote_mode_ck CHECK ((fulfillment_mode = 'DELIVERY') = (address_id IS NOT NULL)
                                    AND (address_id IS NULL) = (address_version IS NULL) AND (address_id IS NULL) = (zone_id IS NULL)),
    CONSTRAINT quote_pickup_free_ck CHECK (fulfillment_mode = 'DELIVERY' OR delivery_fee_minor = 0),
    CONSTRAINT quote_expiry_ck CHECK (expires_at > created_at)
);
CREATE INDEX quote_customer_idx ON quote (customer_id, created_at DESC);

CREATE TABLE quote_item (
    quote_id         uuid    NOT NULL REFERENCES quote (id),
    meal_id          uuid    NOT NULL REFERENCES meal (id),
    meal_revision    integer NOT NULL,
    quantity         integer NOT NULL CHECK (quantity BETWEEN 1 AND 99),
    unit_price_minor bigint  NOT NULL CHECK (unit_price_minor > 0),
    line_total_minor bigint  NOT NULL,
    PRIMARY KEY (quote_id, meal_id),
    FOREIGN KEY (meal_id, meal_revision) REFERENCES meal_revision (meal_id, revision),
    CONSTRAINT quote_item_line_ck CHECK (line_total_minor = unit_price_minor * quantity)
);

CREATE FUNCTION quote_immutable() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    RAISE EXCEPTION 'quotes are immutable';
END $$;

CREATE TRIGGER quote_immutable BEFORE UPDATE OR DELETE ON quote FOR EACH ROW EXECUTE FUNCTION quote_immutable();
CREATE TRIGGER quote_item_immutable BEFORE UPDATE OR DELETE ON quote_item FOR EACH ROW EXECUTE FUNCTION quote_immutable();
