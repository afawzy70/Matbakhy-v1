-- MB-023: operations moderation of meals, separate from the cook's publication status. A hidden meal is not shown or
-- sold whatever its status; only staff with content.moderate hide or restore it, with a reason.
ALTER TABLE meal ADD COLUMN moderation_state text NOT NULL DEFAULT 'VISIBLE' CHECK (moderation_state IN ('VISIBLE', 'HIDDEN'));
ALTER TABLE meal ADD COLUMN moderation_reason text CHECK (moderation_reason IS NULL OR length(moderation_reason) BETWEEN 3 AND 1000);
ALTER TABLE meal ADD COLUMN moderated_at timestamptz;
ALTER TABLE meal ADD COLUMN moderated_by uuid REFERENCES app_user (id);
ALTER TABLE meal ADD CONSTRAINT meal_hidden_reason_ck CHECK (moderation_state = 'VISIBLE' OR moderation_reason IS NOT NULL);
CREATE INDEX meal_moderation_idx ON meal (moderation_state, status, updated_at, id);
