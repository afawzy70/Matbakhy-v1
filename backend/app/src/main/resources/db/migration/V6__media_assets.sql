-- MB-013: uploaded media. Bytes live in object storage; this table is the authority on state and scope.
-- Public variants exist only for approved public-purpose media; private documents never get one.

CREATE TABLE media_asset (
    id                     uuid        PRIMARY KEY,
    owner_id               uuid        NOT NULL REFERENCES app_user (id),
    purpose                text        NOT NULL CHECK (purpose IN ('MEAL_PHOTO', 'COOK_DOCUMENT')),
    state                  text        NOT NULL DEFAULT 'PENDING_UPLOAD'
                                       CHECK (state IN ('PENDING_UPLOAD', 'QUARANTINED', 'APPROVED', 'REJECTED', 'DELETED')),
    original_filename      text        NOT NULL CHECK (length(original_filename) BETWEEN 1 AND 120),
    declared_content_type  text        NOT NULL,
    declared_bytes         bigint      NOT NULL CHECK (declared_bytes > 0),
    declared_sha256        text        NOT NULL CHECK (declared_sha256 ~ '^[0-9a-f]{64}$'),
    private_key            text        NOT NULL UNIQUE,
    content_type           text,
    bytes                  bigint      CHECK (bytes IS NULL OR bytes > 0),
    width                  integer     CHECK (width IS NULL OR width > 0),
    height                 integer     CHECK (height IS NULL OR height > 0),
    public_key             text        UNIQUE,
    scan_verdict           text        CHECK (scan_verdict IN ('CLEAN', 'INFECTED', 'UNKNOWN')),
    scan_engine            text,
    rejection_reason       text        CHECK (rejection_reason IN ('SIZE_MISMATCH', 'CHECKSUM_MISMATCH', 'TYPE_MISMATCH', 'UNSUPPORTED_TYPE',
                                                                   'DIMENSIONS', 'UNREADABLE', 'ACTIVE_CONTENT', 'MALWARE', 'MISSING_OBJECT')),
    ticket_expires_at      timestamptz NOT NULL,
    created_at             timestamptz NOT NULL DEFAULT now(),
    updated_at             timestamptz NOT NULL DEFAULT now(),
    version                bigint      NOT NULL DEFAULT 0,
    CONSTRAINT media_public_only_approved_ck CHECK (public_key IS NULL OR (state = 'APPROVED' AND purpose = 'MEAL_PHOTO')),
    CONSTRAINT media_approved_clean_ck CHECK (state <> 'APPROVED' OR (scan_verdict = 'CLEAN' AND content_type IS NOT NULL AND bytes IS NOT NULL)),
    CONSTRAINT media_rejected_reason_ck CHECK ((state = 'REJECTED') = (rejection_reason IS NOT NULL))
);
CREATE INDEX media_asset_owner_idx ON media_asset (owner_id, state, created_at);

-- Cook verification documents attached to the working draft and frozen into each submitted revision.
ALTER TABLE cook_application ADD COLUMN document_ids uuid[] NOT NULL DEFAULT '{}';
ALTER TABLE cook_application_revision ADD COLUMN document_ids uuid[] NOT NULL DEFAULT '{}';

-- Extend the revision immutability guard to the frozen document list.
CREATE OR REPLACE FUNCTION cook_revision_guard() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    IF TG_OP = 'DELETE' THEN
        RAISE EXCEPTION 'cook_application_revision is append-only';
    END IF;
    IF OLD.decision IS NOT NULL
       OR (NEW.application_id, NEW.revision, NEW.public_name, NEW.bio, NEW.contact_phone, NEW.private_location,
           NEW.pickup_enabled, NEW.zone_ids, NEW.submitted_at, NEW.document_ids)
          IS DISTINCT FROM
          (OLD.application_id, OLD.revision, OLD.public_name, OLD.bio, OLD.contact_phone, OLD.private_location,
           OLD.pickup_enabled, OLD.zone_ids, OLD.submitted_at, OLD.document_ids) THEN
        RAISE EXCEPTION 'cook_application_revision snapshot is immutable';
    END IF;
    RETURN NEW;
END $$;
