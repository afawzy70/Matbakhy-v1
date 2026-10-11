-- MB-021: leased outbox dispatch, persistent inbox, channel deliveries with retry/dead-letter, device registrations.

ALTER TABLE outbox_event ADD COLUMN lease_owner text;
ALTER TABLE outbox_event ADD COLUMN leased_until timestamptz;
ALTER TABLE outbox_event ADD COLUMN last_error text CHECK (last_error IS NULL OR length(last_error) <= 500);
ALTER TABLE outbox_event ADD COLUMN processed_at timestamptz;
ALTER TABLE outbox_event ADD COLUMN version bigint NOT NULL DEFAULT 0;

-- Inbox: one row per recipient and logical event (type + aggregate), whatever the number of duplicate deliveries.
CREATE TABLE notification (
    id              uuid        PRIMARY KEY,
    recipient_id    uuid        NOT NULL REFERENCES app_user (id),
    dedup_key       text        NOT NULL CHECK (length(dedup_key) BETWEEN 1 AND 200),
    source_event_id uuid        NOT NULL REFERENCES outbox_event (id),
    type            text        NOT NULL,
    order_id        uuid        REFERENCES customer_order (id),
    params          jsonb       NOT NULL,
    created_at      timestamptz NOT NULL,
    read_at         timestamptz,
    CONSTRAINT notification_dedup_uq UNIQUE (recipient_id, dedup_key)
);
CREATE INDEX notification_inbox_idx ON notification (recipient_id, created_at DESC, id DESC);

CREATE TABLE notification_delivery (
    id              uuid        PRIMARY KEY,
    notification_id uuid        NOT NULL REFERENCES notification (id),
    channel         text        NOT NULL CHECK (channel IN ('EMAIL', 'PUSH')),
    target          text        NOT NULL CHECK (length(target) BETWEEN 1 AND 200),
    status          text        NOT NULL DEFAULT 'PENDING' CHECK (status IN ('PENDING', 'SENT', 'DEAD', 'SKIPPED')),
    attempts        integer     NOT NULL DEFAULT 0 CHECK (attempts >= 0),
    next_attempt_at timestamptz NOT NULL,
    lease_owner     text,
    leased_until    timestamptz,
    last_error      text        CHECK (last_error IS NULL OR length(last_error) <= 500),
    provider_ref    text,
    sent_at         timestamptz,
    CONSTRAINT notification_delivery_uq UNIQUE (notification_id, channel, target)
);
CREATE INDEX notification_delivery_due_idx ON notification_delivery (status, next_attempt_at);

-- Push installations. Tokens are stored encrypted (AES-GCM) with a keyed hash for uniqueness, never in plain text.
CREATE TABLE device_registration (
    id               uuid        PRIMARY KEY,
    user_id          uuid        NOT NULL REFERENCES app_user (id),
    installation_id  text        NOT NULL CHECK (installation_id ~ '^[A-Za-z0-9-]{8,64}$'),
    platform         text        NOT NULL CHECK (platform IN ('ANDROID', 'IOS')),
    environment      text        NOT NULL CHECK (environment IN ('PRODUCTION', 'DEVELOPMENT')),
    app_version      text        NOT NULL CHECK (length(app_version) BETWEEN 1 AND 40),
    token_hash       text        NOT NULL CHECK (token_hash ~ '^[0-9a-f]{64}$'),
    token_ciphertext bytea       NOT NULL,
    token_nonce      bytea       NOT NULL,
    created_at       timestamptz NOT NULL,
    updated_at       timestamptz NOT NULL,
    revoked_at       timestamptz
);
CREATE UNIQUE INDEX device_registration_installation_uq ON device_registration (installation_id) WHERE revoked_at IS NULL;
CREATE UNIQUE INDEX device_registration_token_uq ON device_registration (token_hash) WHERE revoked_at IS NULL;
CREATE INDEX device_registration_user_idx ON device_registration (user_id) WHERE revoked_at IS NULL;
