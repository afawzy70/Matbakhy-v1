-- MB-022: who paused a kitchen. A cook may resume only a pause they set; a staff pause or a suspension needs staff.
ALTER TABLE kitchen ADD COLUMN paused_by text CHECK (paused_by IS NULL OR paused_by IN ('COOK', 'STAFF'));
ALTER TABLE kitchen ADD CONSTRAINT kitchen_paused_by_ck CHECK (paused_by IS NULL OR operating_state = 'PAUSED');
UPDATE kitchen SET paused_by = 'STAFF' WHERE operating_state = 'PAUSED';
