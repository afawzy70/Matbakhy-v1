-- MB-028: favorites and verified-order reviews with reports and staff moderation.
CREATE TABLE favorite (
    user_id    uuid        NOT NULL REFERENCES app_user (id),
    meal_id    uuid        NOT NULL REFERENCES meal (id),
    created_at timestamptz NOT NULL,
    PRIMARY KEY (user_id, meal_id)
);

-- One review per completed order, by its customer; only VISIBLE reviews count in ratings.
CREATE TABLE review (
    id                uuid        PRIMARY KEY,
    order_id          uuid        NOT NULL UNIQUE REFERENCES customer_order (id),
    customer_id       uuid        NOT NULL REFERENCES app_user (id),
    kitchen_id        uuid        NOT NULL REFERENCES kitchen (id),
    rating            integer     NOT NULL CHECK (rating BETWEEN 1 AND 5),
    text              text        CHECK (text IS NULL OR length(text) BETWEEN 1 AND 1000),
    visibility        text        NOT NULL DEFAULT 'VISIBLE' CHECK (visibility IN ('VISIBLE', 'HIDDEN')),
    moderation_reason text        CHECK (moderation_reason IS NULL OR length(moderation_reason) BETWEEN 3 AND 1000),
    created_at        timestamptz NOT NULL,
    version           bigint      NOT NULL DEFAULT 0,
    CONSTRAINT review_hidden_reason_ck CHECK (visibility = 'VISIBLE' OR moderation_reason IS NOT NULL)
);
CREATE INDEX review_kitchen_idx ON review (kitchen_id, visibility, created_at DESC, id DESC);

CREATE TABLE review_report (
    id          uuid        PRIMARY KEY,
    review_id   uuid        NOT NULL REFERENCES review (id),
    reporter_id uuid        NOT NULL REFERENCES app_user (id),
    reason_code text        NOT NULL CHECK (reason_code IN ('OFFENSIVE', 'PRIVATE_INFO', 'SPAM', 'NOT_ABOUT_ORDER', 'OTHER')),
    description text        CHECK (description IS NULL OR length(description) <= 500),
    created_at  timestamptz NOT NULL,
    CONSTRAINT review_report_once_uq UNIQUE (review_id, reporter_id)
);
CREATE INDEX review_report_time_idx ON review_report (reporter_id, created_at);
