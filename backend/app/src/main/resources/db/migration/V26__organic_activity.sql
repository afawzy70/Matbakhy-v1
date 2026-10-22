-- MB-054 (owner decision D17, Stage 2): privacy-safe organic activity for the free cook report.
-- One row per (Cairo day, kind, subject, visitor): a refresh or a repeated event inserts nothing new, so counts are
-- distinct visitors per day. The visitor is stored only as an HMAC under a key derived per day from a server secret: it
-- cannot be reversed or linked across days, and no account, address, device or network data is kept.
-- Retention of these rows is decided under O06 (privacy and retention policy); there is no deletion job until then.
CREATE TABLE activity_daily (
    day           date  NOT NULL,
    kind          text  NOT NULL CHECK (kind IN ('MEAL_IMPRESSION', 'MEAL_VIEW', 'KITCHEN_VIEW', 'ADD_TO_CART')),
    subject_id    uuid  NOT NULL,
    kitchen_id    uuid  NOT NULL REFERENCES kitchen (id),
    visitor_hash  bytea NOT NULL CHECK (length(visitor_hash) = 32),
    PRIMARY KEY (day, kind, subject_id, visitor_hash)
);
CREATE INDEX activity_daily_kitchen_idx ON activity_daily (kitchen_id, day);
CREATE INDEX activity_daily_visitor_idx ON activity_daily (day, visitor_hash);
