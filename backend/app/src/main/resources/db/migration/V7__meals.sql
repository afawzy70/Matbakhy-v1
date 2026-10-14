-- MB-014: meals, immutable content revisions and ordered photos.
-- Allergen declaration is explicit: CONTAINS (with codes), NONE_DECLARED or UNKNOWN; never implied by absence.

CREATE TABLE meal (
    id                   uuid        PRIMARY KEY,
    kitchen_id           uuid        NOT NULL REFERENCES kitchen (id),
    status               text        NOT NULL DEFAULT 'DRAFT' CHECK (status IN ('DRAFT', 'PUBLISHED', 'PAUSED', 'ARCHIVED')),
    title_ar             text        CHECK (title_ar IS NULL OR length(title_ar) BETWEEN 2 AND 120),
    title_en             text        CHECK (title_en IS NULL OR length(title_en) BETWEEN 2 AND 120),
    description_ar       text        CHECK (description_ar IS NULL OR length(description_ar) BETWEEN 10 AND 2000),
    description_en       text        CHECK (description_en IS NULL OR length(description_en) BETWEEN 10 AND 2000),
    portion_ar           text        CHECK (portion_ar IS NULL OR length(portion_ar) BETWEEN 2 AND 120),
    portion_en           text        CHECK (portion_en IS NULL OR length(portion_en) BETWEEN 2 AND 120),
    ingredients_ar       text        CHECK (ingredients_ar IS NULL OR length(ingredients_ar) BETWEEN 3 AND 2000),
    ingredients_en       text        CHECK (ingredients_en IS NULL OR length(ingredients_en) BETWEEN 3 AND 2000),
    allergen_declaration text        CHECK (allergen_declaration IN ('CONTAINS', 'NONE_DECLARED', 'UNKNOWN')),
    allergen_codes       text[]      NOT NULL DEFAULT '{}',
    allergen_note        text        CHECK (allergen_note IS NULL OR length(allergen_note) <= 500),
    price_minor          bigint      CHECK (price_minor IS NULL OR price_minor BETWEEN 1 AND 1000000),
    currency             text        NOT NULL DEFAULT 'EGP' CHECK (currency = 'EGP'),
    revision             integer     NOT NULL DEFAULT 1 CHECK (revision >= 1),
    published_at         timestamptz,
    archived_at          timestamptz,
    created_at           timestamptz NOT NULL DEFAULT now(),
    updated_at           timestamptz NOT NULL DEFAULT now(),
    version              bigint      NOT NULL DEFAULT 0,
    CONSTRAINT meal_allergen_codes_ck CHECK ((allergen_declaration = 'CONTAINS') = (cardinality(allergen_codes) > 0)),
    -- A visible (published or paused) meal always carries a complete declaration.
    CONSTRAINT meal_publishable_ck CHECK (status NOT IN ('PUBLISHED', 'PAUSED') OR (
        title_ar IS NOT NULL AND description_ar IS NOT NULL AND portion_ar IS NOT NULL AND ingredients_ar IS NOT NULL
        AND allergen_declaration IS NOT NULL AND price_minor IS NOT NULL AND published_at IS NOT NULL)),
    CONSTRAINT meal_archived_ck CHECK ((status = 'ARCHIVED') = (archived_at IS NOT NULL))
);
CREATE INDEX meal_kitchen_status_idx ON meal (kitchen_id, status, created_at, id);
CREATE INDEX meal_status_idx ON meal (status, kitchen_id);

CREATE TABLE meal_media (
    meal_id   uuid    NOT NULL REFERENCES meal (id),
    media_id  uuid    NOT NULL REFERENCES media_asset (id),
    position  integer NOT NULL CHECK (position BETWEEN 0 AND 5),
    alt_ar    text    CHECK (alt_ar IS NULL OR length(alt_ar) <= 200),
    alt_en    text    CHECK (alt_en IS NULL OR length(alt_en) <= 200),
    PRIMARY KEY (meal_id, position),
    UNIQUE (meal_id, media_id)
);

-- Content snapshot per revision. Quotes and orders reference (meal_id, revision); rows are never changed or deleted.
CREATE TABLE meal_revision (
    meal_id     uuid        NOT NULL REFERENCES meal (id),
    revision    integer     NOT NULL CHECK (revision >= 1),
    snapshot    jsonb       NOT NULL,
    price_minor bigint      CHECK (price_minor IS NULL OR price_minor > 0),
    created_at  timestamptz NOT NULL DEFAULT now(),
    PRIMARY KEY (meal_id, revision)
);

CREATE FUNCTION meal_revision_immutable() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    RAISE EXCEPTION 'meal_revision is append-only';
END $$;

CREATE TRIGGER meal_revision_append_only BEFORE UPDATE OR DELETE ON meal_revision
    FOR EACH ROW EXECUTE FUNCTION meal_revision_immutable();
CREATE TRIGGER meal_revision_no_truncate BEFORE TRUNCATE ON meal_revision
    FOR EACH STATEMENT EXECUTE FUNCTION meal_revision_immutable();

-- Meals are archived, never deleted, so historical references stay valid.
CREATE FUNCTION meal_no_delete() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    RAISE EXCEPTION 'meals are archived, not deleted';
END $$;

CREATE TRIGGER meal_no_delete BEFORE DELETE ON meal FOR EACH ROW EXECUTE FUNCTION meal_no_delete();
