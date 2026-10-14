-- MB-024: order-scoped support cases with public messages and staff-only notes, SLA and safety escalation.
CREATE TABLE support_case (
    id                 uuid        PRIMARY KEY,
    reference          text        NOT NULL UNIQUE CHECK (reference ~ '^CS-[0-9A-F]{8}$'),
    order_id           uuid        NOT NULL REFERENCES customer_order (id),
    customer_id        uuid        NOT NULL REFERENCES app_user (id),
    category           text        NOT NULL CHECK (category IN ('LATE', 'MISSING_ITEM', 'QUALITY', 'FOOD_SAFETY', 'CASH', 'HANDOVER', 'OTHER',
                                                                'CANCELLATION_REQUEST')),
    severity           text        NOT NULL CHECK (severity IN ('NORMAL', 'HIGH', 'CRITICAL')),
    state              text        NOT NULL DEFAULT 'OPEN' CHECK (state IN ('OPEN', 'IN_PROGRESS', 'WAITING_ON_CUSTOMER', 'RESOLVED', 'CLOSED')),
    description        text        NOT NULL CHECK (length(description) BETWEEN 10 AND 4000),
    cancellation_reason text       CHECK (cancellation_reason IS NULL OR category = 'CANCELLATION_REQUEST'),
    assignee_id        uuid        REFERENCES app_user (id),
    sla_due_at         timestamptz NOT NULL,
    first_response_at  timestamptz,
    escalated_at       timestamptz,
    resolution_code    text,
    resolved_at        timestamptz,
    created_at         timestamptz NOT NULL,
    updated_at         timestamptz NOT NULL,
    version            bigint      NOT NULL DEFAULT 0,
    CONSTRAINT support_case_resolution_ck CHECK (state NOT IN ('RESOLVED', 'CLOSED') OR resolution_code IS NOT NULL),
    CONSTRAINT support_case_safety_ck CHECK (category <> 'FOOD_SAFETY' OR (severity = 'CRITICAL' AND escalated_at IS NOT NULL))
);
CREATE INDEX support_case_customer_idx ON support_case (customer_id, created_at DESC, id DESC);
CREATE INDEX support_case_queue_idx ON support_case (state, severity, sla_due_at);
CREATE INDEX support_case_order_idx ON support_case (order_id);
-- At most one open cancellation request per order.
CREATE UNIQUE INDEX support_case_open_cancellation_uq ON support_case (order_id)
    WHERE category = 'CANCELLATION_REQUEST' AND state NOT IN ('RESOLVED', 'CLOSED');

-- Messages are append-only. Customers only ever write PUBLIC messages; STAFF_ONLY notes are never shown to them.
CREATE TABLE case_message (
    id          uuid        PRIMARY KEY,
    case_id     uuid        NOT NULL REFERENCES support_case (id),
    author_id   uuid        NOT NULL REFERENCES app_user (id),
    author_role text        NOT NULL CHECK (author_role IN ('CUSTOMER', 'STAFF')),
    visibility  text        NOT NULL CHECK (visibility IN ('PUBLIC', 'STAFF_ONLY')),
    body        text        NOT NULL CHECK (length(body) BETWEEN 1 AND 4000),
    created_at  timestamptz NOT NULL,
    CONSTRAINT case_message_customer_public_ck CHECK (author_role = 'STAFF' OR visibility = 'PUBLIC')
);
CREATE INDEX case_message_case_idx ON case_message (case_id, created_at, id);

CREATE FUNCTION case_message_immutable() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    RAISE EXCEPTION 'case_message is append-only';
END $$;
CREATE TRIGGER case_message_append_only BEFORE UPDATE OR DELETE ON case_message FOR EACH ROW EXECUTE FUNCTION case_message_immutable();
CREATE TRIGGER case_message_no_truncate BEFORE TRUNCATE ON case_message FOR EACH STATEMENT EXECUTE FUNCTION case_message_immutable();
