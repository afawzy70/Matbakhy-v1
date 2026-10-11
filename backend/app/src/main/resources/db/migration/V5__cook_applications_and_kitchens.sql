-- MB-012: cook applications with immutable submitted revisions, kitchens and their zones.

CREATE TABLE cook_application (
    id                 uuid        PRIMARY KEY,
    user_id            uuid        NOT NULL UNIQUE REFERENCES app_user (id),
    state              text        NOT NULL DEFAULT 'DRAFT'
                                   CHECK (state IN ('DRAFT', 'SUBMITTED', 'UNDER_REVIEW', 'APPROVED', 'CHANGES_REQUESTED', 'REJECTED')),
    -- Working draft, editable only in DRAFT or CHANGES_REQUESTED. REJECTED is final (reapplication policy: owner decision O06).
    public_name        text        CHECK (public_name IS NULL OR length(public_name) BETWEEN 2 AND 80),
    bio                text        CHECK (bio IS NULL OR length(bio) BETWEEN 1 AND 1000),
    contact_phone      text        CHECK (contact_phone IS NULL OR contact_phone ~ '^\+201[0125][0-9]{8}$'),
    private_location   text        CHECK (private_location IS NULL OR length(private_location) BETWEEN 5 AND 300),
    pickup_enabled     boolean     NOT NULL DEFAULT false,
    zone_ids           uuid[]      NOT NULL DEFAULT '{}',
    current_revision   integer     NOT NULL DEFAULT 0 CHECK (current_revision >= 0),
    created_at         timestamptz NOT NULL DEFAULT now(),
    updated_at         timestamptz NOT NULL DEFAULT now(),
    version            bigint      NOT NULL DEFAULT 0
);
CREATE INDEX cook_application_queue_idx ON cook_application (state, created_at, id);

-- Immutable snapshot per submission; decisions are recorded on the revision they concern.
CREATE TABLE cook_application_revision (
    application_id   uuid        NOT NULL REFERENCES cook_application (id),
    revision         integer     NOT NULL CHECK (revision >= 1),
    public_name      text        NOT NULL,
    bio              text        NOT NULL,
    contact_phone    text        NOT NULL,
    private_location text        NOT NULL,
    pickup_enabled   boolean     NOT NULL,
    zone_ids         uuid[]      NOT NULL,
    submitted_at     timestamptz NOT NULL,
    decision         text        CHECK (decision IN ('APPROVED', 'CHANGES_REQUESTED', 'REJECTED')),
    decision_reason  text        CHECK (decision_reason IS NULL OR length(decision_reason) BETWEEN 3 AND 1000),
    reviewer_id      uuid        REFERENCES app_user (id),
    decided_at       timestamptz,
    PRIMARY KEY (application_id, revision),
    CONSTRAINT cook_revision_decision_ck CHECK ((decision IS NULL) = (reviewer_id IS NULL) AND (decision IS NULL) = (decided_at IS NULL)
                                                 AND (decision IS NULL) = (decision_reason IS NULL)),
    CONSTRAINT cook_revision_serviceable_ck CHECK (pickup_enabled OR cardinality(zone_ids) > 0)
);

CREATE TABLE kitchen (
    id                 uuid        PRIMARY KEY,
    owner_id           uuid        NOT NULL UNIQUE REFERENCES app_user (id),
    application_id     uuid        NOT NULL UNIQUE REFERENCES cook_application (id),
    approved_revision  integer     NOT NULL,
    public_slug        text        NOT NULL UNIQUE CHECK (public_slug ~ '^[a-z0-9-]{3,80}$'),
    display_name       text        NOT NULL,
    description        text        NOT NULL,
    operating_state    text        NOT NULL DEFAULT 'ACTIVE' CHECK (operating_state IN ('ACTIVE', 'PAUSED', 'SUSPENDED', 'CLOSED')),
    state_reason       text,
    timezone           text        NOT NULL DEFAULT 'Africa/Cairo' CHECK (timezone = 'Africa/Cairo'),
    pickup_enabled     boolean     NOT NULL,
    contact_phone      text        NOT NULL,
    private_location   text        NOT NULL,
    created_at         timestamptz NOT NULL DEFAULT now(),
    updated_at         timestamptz NOT NULL DEFAULT now(),
    version            bigint      NOT NULL DEFAULT 0,
    FOREIGN KEY (application_id, approved_revision) REFERENCES cook_application_revision (application_id, revision)
);

CREATE TABLE kitchen_zone (
    kitchen_id         uuid    NOT NULL REFERENCES kitchen (id),
    zone_id            uuid    NOT NULL REFERENCES service_zone (id),
    delivery_enabled   boolean NOT NULL DEFAULT true,
    fee_override_minor bigint  CHECK (fee_override_minor IS NULL OR fee_override_minor >= 0),
    version            bigint  NOT NULL DEFAULT 0,
    PRIMARY KEY (kitchen_id, zone_id)
);

-- Submitted snapshots are immutable; only the decision columns may be set, once.
CREATE FUNCTION cook_revision_guard() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    IF TG_OP = 'DELETE' THEN
        RAISE EXCEPTION 'cook_application_revision is append-only';
    END IF;
    IF OLD.decision IS NOT NULL
       OR (NEW.application_id, NEW.revision, NEW.public_name, NEW.bio, NEW.contact_phone, NEW.private_location,
           NEW.pickup_enabled, NEW.zone_ids, NEW.submitted_at)
          IS DISTINCT FROM
          (OLD.application_id, OLD.revision, OLD.public_name, OLD.bio, OLD.contact_phone, OLD.private_location,
           OLD.pickup_enabled, OLD.zone_ids, OLD.submitted_at) THEN
        RAISE EXCEPTION 'cook_application_revision snapshot is immutable';
    END IF;
    RETURN NEW;
END $$;

CREATE TRIGGER cook_revision_immutable BEFORE UPDATE OR DELETE ON cook_application_revision
    FOR EACH ROW EXECUTE FUNCTION cook_revision_guard();
