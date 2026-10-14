-- MB-006 baseline. Forward-only: never edit after it has been applied anywhere; add V2+ instead.
-- Identifiers are UUIDs assigned by the application. Timestamps are UTC timestamptz.

CREATE TABLE app_user (
    id                uuid        PRIMARY KEY,
    issuer            text        NOT NULL CHECK (length(issuer) BETWEEN 1 AND 512),
    subject           text        NOT NULL CHECK (length(subject) BETWEEN 1 AND 255),
    email_verified    boolean     NOT NULL DEFAULT false,
    display_name      text        CHECK (display_name IS NULL OR length(display_name) BETWEEN 1 AND 120),
    locale            text        NOT NULL DEFAULT 'ar' CHECK (locale IN ('ar', 'en')),
    phone_e164        text        CHECK (phone_e164 IS NULL OR phone_e164 ~ '^\+[1-9][0-9]{7,14}$'),
    phone_verified_at timestamptz CHECK (phone_verified_at IS NULL OR phone_e164 IS NOT NULL),
    status            text        NOT NULL DEFAULT 'ACTIVE'
                                  CHECK (status IN ('ACTIVE', 'SUSPENDED', 'DELETION_REQUESTED', 'DELETED')),
    terms_version     text,
    created_at        timestamptz NOT NULL DEFAULT now(),
    updated_at        timestamptz NOT NULL DEFAULT now(),
    version           bigint      NOT NULL DEFAULT 0 CHECK (version >= 0),
    CONSTRAINT app_user_issuer_subject_uk UNIQUE (issuer, subject)
);

-- Command idempotency (docs/engineering/03-api-contract.md): unique per actor + command + key.
-- Same key with a different canonical body hash is an IDEMPOTENCY_CONFLICT handled by the service.
CREATE TABLE idempotency_record (
    actor_id          uuid        NOT NULL REFERENCES app_user (id),
    command           text        NOT NULL CHECK (length(command) BETWEEN 1 AND 100),
    idempotency_key   text        NOT NULL CHECK (length(idempotency_key) BETWEEN 8 AND 128),
    request_hash      text        NOT NULL CHECK (request_hash ~ '^[0-9a-f]{64}$'),
    result_status     integer     CHECK (result_status IS NULL OR result_status BETWEEN 100 AND 599),
    resource_id       uuid,
    response_snapshot jsonb,
    created_at        timestamptz NOT NULL DEFAULT now(),
    expires_at        timestamptz NOT NULL,
    CONSTRAINT idempotency_record_pk PRIMARY KEY (actor_id, command, idempotency_key),
    CONSTRAINT idempotency_record_retention_ck CHECK (expires_at >= created_at + interval '24 hours')
);

CREATE INDEX idempotency_record_expires_idx ON idempotency_record (expires_at);
