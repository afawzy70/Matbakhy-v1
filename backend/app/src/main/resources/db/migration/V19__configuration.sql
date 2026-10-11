-- MB-026: feature flags and an append-only record of every operating-configuration change.
CREATE TABLE feature_flag (
    key        text        PRIMARY KEY CHECK (key ~ '^[a-z][a-z0-9-]{2,60}$'),
    enabled    boolean     NOT NULL,
    updated_at timestamptz NOT NULL DEFAULT now(),
    version    bigint      NOT NULL DEFAULT 0
);
-- Global kill switch for new quotes and placements; orders already placed are unaffected.
INSERT INTO feature_flag (key, enabled) VALUES ('ordering-enabled', true);

CREATE TABLE config_change (
    id                 uuid        PRIMARY KEY,
    config_key         text        NOT NULL CHECK (length(config_key) BETWEEN 3 AND 120),
    resulting_version  bigint      NOT NULL,
    value              jsonb       NOT NULL,
    reason             text        NOT NULL CHECK (length(reason) BETWEEN 3 AND 1000),
    approval_reference text        CHECK (approval_reference IS NULL OR length(approval_reference) BETWEEN 3 AND 200),
    actor_id           uuid        NOT NULL REFERENCES app_user (id),
    created_at         timestamptz NOT NULL
);
CREATE INDEX config_change_key_idx ON config_change (config_key, created_at DESC);

CREATE FUNCTION config_change_immutable() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    RAISE EXCEPTION 'config_change is append-only';
END $$;
CREATE TRIGGER config_change_append_only BEFORE UPDATE OR DELETE ON config_change FOR EACH ROW EXECUTE FUNCTION config_change_immutable();
CREATE TRIGGER config_change_no_truncate BEFORE TRUNCATE ON config_change FOR EACH STATEMENT EXECUTE FUNCTION config_change_immutable();
