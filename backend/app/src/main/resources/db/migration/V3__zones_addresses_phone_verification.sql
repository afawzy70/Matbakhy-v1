-- MB-010: service zones (list-based serviceability), customer addresses, phone verification challenges
-- and account deletion requests. Synthetic zones for development live only in db/devdata.

CREATE TABLE service_zone (
    id                 uuid        PRIMARY KEY,
    code               text        NOT NULL UNIQUE CHECK (code ~ '^[a-z0-9-]{2,40}$'),
    name_ar            text        NOT NULL CHECK (length(name_ar) BETWEEN 1 AND 120),
    name_en            text        NOT NULL CHECK (length(name_en) BETWEEN 1 AND 120),
    city_ar            text        NOT NULL CHECK (length(city_ar) BETWEEN 1 AND 120),
    city_en            text        NOT NULL CHECK (length(city_en) BETWEEN 1 AND 120),
    active             boolean     NOT NULL DEFAULT true,
    delivery_fee_minor bigint      NOT NULL CHECK (delivery_fee_minor >= 0),
    currency           char(3)     NOT NULL DEFAULT 'EGP' CHECK (currency = 'EGP'),
    created_at         timestamptz NOT NULL DEFAULT now(),
    version            bigint      NOT NULL DEFAULT 0
);

CREATE TABLE address (
    id             uuid        PRIMARY KEY,
    user_id        uuid        NOT NULL REFERENCES app_user (id),
    label          text        NOT NULL CHECK (length(label) BETWEEN 1 AND 40),
    recipient_name text        NOT NULL CHECK (length(recipient_name) BETWEEN 1 AND 120),
    phone_e164     text        NOT NULL CHECK (phone_e164 ~ '^\+201[0125][0-9]{8}$'),
    governorate    text        NOT NULL CHECK (length(governorate) BETWEEN 1 AND 80),
    city           text        NOT NULL CHECK (length(city) BETWEEN 1 AND 80),
    zone_id        uuid        NOT NULL REFERENCES service_zone (id),
    street         text        NOT NULL CHECK (length(street) BETWEEN 1 AND 200),
    building       text        NOT NULL CHECK (length(building) BETWEEN 1 AND 40),
    floor          text        CHECK (floor IS NULL OR length(floor) BETWEEN 1 AND 20),
    unit           text        CHECK (unit IS NULL OR length(unit) BETWEEN 1 AND 20),
    landmark       text        CHECK (landmark IS NULL OR length(landmark) BETWEEN 1 AND 200),
    latitude       numeric(9, 6) CHECK (latitude IS NULL OR latitude BETWEEN -90 AND 90),
    longitude      numeric(9, 6) CHECK (longitude IS NULL OR longitude BETWEEN -180 AND 180),
    archived_at    timestamptz,
    created_at     timestamptz NOT NULL DEFAULT now(),
    updated_at     timestamptz NOT NULL DEFAULT now(),
    version        bigint      NOT NULL DEFAULT 0 CHECK (version >= 0),
    CONSTRAINT address_coordinates_pair_ck CHECK ((latitude IS NULL) = (longitude IS NULL))
);

CREATE INDEX address_user_idx ON address (user_id, created_at, id) WHERE archived_at IS NULL;

-- One row per issued code. Only an HMAC of the code is stored; the plaintext never touches the database.
CREATE TABLE phone_challenge (
    id          uuid        PRIMARY KEY,
    user_id     uuid        NOT NULL REFERENCES app_user (id),
    phone_e164  text        NOT NULL CHECK (phone_e164 ~ '^\+201[0125][0-9]{8}$'),
    code_hmac   text        NOT NULL CHECK (code_hmac ~ '^[0-9a-f]{64}$'),
    created_at  timestamptz NOT NULL,
    expires_at  timestamptz NOT NULL,
    attempts    integer     NOT NULL DEFAULT 0 CHECK (attempts >= 0),
    consumed_at timestamptz,
    CONSTRAINT phone_challenge_expiry_ck CHECK (expires_at > created_at)
);

CREATE INDEX phone_challenge_user_idx ON phone_challenge (user_id, created_at);
CREATE INDEX phone_challenge_phone_idx ON phone_challenge (phone_e164, created_at);

CREATE TABLE deletion_request (
    id                uuid        PRIMARY KEY,
    user_id           uuid        NOT NULL REFERENCES app_user (id),
    state             text        NOT NULL DEFAULT 'RECEIVED' CHECK (state IN ('RECEIVED', 'COMPLETED', 'ON_HOLD')),
    policy_version    text        NOT NULL CHECK (length(policy_version) BETWEEN 1 AND 40),
    requested_at      timestamptz NOT NULL DEFAULT now(),
    legal_hold_reason text,
    completed_at      timestamptz
);

-- At most one open request per account (idempotent intake).
CREATE UNIQUE INDEX deletion_request_open_uk ON deletion_request (user_id) WHERE state = 'RECEIVED';
