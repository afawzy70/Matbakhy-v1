-- MB-015: dated cook slots and per-meal capacity. Instants are UTC; the local date and zone record how the cook
-- scheduled them (Africa/Cairo). Counters: reserved (placed), allocated (accepted), consumed (in preparation).

CREATE TABLE cook_slot (
    id            uuid        PRIMARY KEY,
    kitchen_id    uuid        NOT NULL REFERENCES kitchen (id),
    local_date    date        NOT NULL,
    starts_at     timestamptz NOT NULL,
    ends_at       timestamptz NOT NULL,
    timezone      text        NOT NULL DEFAULT 'Africa/Cairo' CHECK (timezone = 'Africa/Cairo'),
    lead_minutes  integer     NOT NULL CHECK (lead_minutes BETWEEN 30 AND 1440),
    cutoff_at     timestamptz NOT NULL,
    max_portions  integer     NOT NULL CHECK (max_portions BETWEEN 1 AND 500),
    reserved      integer     NOT NULL DEFAULT 0 CHECK (reserved >= 0),
    allocated     integer     NOT NULL DEFAULT 0 CHECK (allocated >= 0),
    consumed      integer     NOT NULL DEFAULT 0 CHECK (consumed >= 0),
    state         text        NOT NULL DEFAULT 'DRAFT' CHECK (state IN ('DRAFT', 'OPEN', 'PAUSED', 'CLOSED', 'CANCELLED')),
    created_at    timestamptz NOT NULL DEFAULT now(),
    updated_at    timestamptz NOT NULL DEFAULT now(),
    version       bigint      NOT NULL DEFAULT 0,
    CONSTRAINT cook_slot_order_ck CHECK (ends_at > starts_at AND ends_at - starts_at <= interval '24 hours'),
    CONSTRAINT cook_slot_cutoff_ck CHECK (cutoff_at = starts_at - make_interval(mins => lead_minutes)),
    CONSTRAINT cook_slot_capacity_ck CHECK (reserved + allocated + consumed <= max_portions)
);
CREATE UNIQUE INDEX cook_slot_identity_uq ON cook_slot (kitchen_id, starts_at) WHERE state <> 'CANCELLED';
CREATE INDEX cook_slot_kitchen_time_idx ON cook_slot (kitchen_id, starts_at, state);

CREATE TABLE meal_slot (
    id           uuid    PRIMARY KEY,
    cook_slot_id uuid    NOT NULL REFERENCES cook_slot (id),
    meal_id      uuid    NOT NULL REFERENCES meal (id),
    capacity     integer NOT NULL CHECK (capacity BETWEEN 0 AND 500),
    reserved     integer NOT NULL DEFAULT 0 CHECK (reserved >= 0),
    allocated    integer NOT NULL DEFAULT 0 CHECK (allocated >= 0),
    consumed     integer NOT NULL DEFAULT 0 CHECK (consumed >= 0),
    version      bigint  NOT NULL DEFAULT 0,
    UNIQUE (cook_slot_id, meal_id),
    CONSTRAINT meal_slot_capacity_ck CHECK (reserved + allocated + consumed <= capacity)
);

-- A meal can only be offered in a slot of its own kitchen.
CREATE FUNCTION meal_slot_same_kitchen() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    IF (SELECT kitchen_id FROM meal WHERE id = NEW.meal_id) IS DISTINCT FROM
       (SELECT kitchen_id FROM cook_slot WHERE id = NEW.cook_slot_id) THEN
        RAISE EXCEPTION 'meal_slot meal and slot belong to different kitchens';
    END IF;
    RETURN NEW;
END $$;

CREATE TRIGGER meal_slot_same_kitchen BEFORE INSERT OR UPDATE OF meal_id, cook_slot_id ON meal_slot
    FOR EACH ROW EXECUTE FUNCTION meal_slot_same_kitchen();
