-- CR2-001: durable, idempotent storage cleanup. A row is written in the same transaction that makes an object
-- unreferenced; a job deletes it with bounded retries. FAILED rows stay visible (metric + log) for operators.
CREATE TABLE media_cleanup (
    id              uuid        PRIMARY KEY,
    bucket          text        NOT NULL CHECK (bucket IN ('PRIVATE', 'PUBLIC')),
    object_key      text        NOT NULL,
    reason          text        NOT NULL,
    state           text        NOT NULL DEFAULT 'PENDING' CHECK (state IN ('PENDING', 'DONE', 'FAILED', 'SKIPPED_REFERENCED')),
    attempts        integer     NOT NULL DEFAULT 0 CHECK (attempts >= 0),
    next_attempt_at timestamptz NOT NULL,
    last_error      text,
    created_at      timestamptz NOT NULL,
    updated_at      timestamptz NOT NULL,
    CONSTRAINT media_cleanup_object_uq UNIQUE (bucket, object_key)
);
CREATE INDEX media_cleanup_due_idx ON media_cleanup (next_attempt_at) WHERE state = 'PENDING';
