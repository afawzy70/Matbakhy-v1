-- MB-020: cash collection reports, finance verification and the immutable commission ledger.
-- Completion never implies payment: the cash obligation moves only through report and verification.

CREATE TABLE cash_report (
    id                     uuid        PRIMARY KEY,
    order_id               uuid        NOT NULL UNIQUE REFERENCES customer_order (id),
    reporter_id            uuid        NOT NULL REFERENCES app_user (id),
    amount_minor           bigint      NOT NULL CHECK (amount_minor >= 0),
    currency               text        NOT NULL DEFAULT 'EGP' CHECK (currency = 'EGP'),
    method                 text        NOT NULL DEFAULT 'CASH' CHECK (method = 'CASH'),
    collected_at           timestamptz NOT NULL,
    external_reference     text        CHECK (external_reference IS NULL OR length(external_reference) BETWEEN 1 AND 128),
    reported_at            timestamptz NOT NULL,
    discrepancy_minor      bigint      NOT NULL,
    state                  text        NOT NULL CHECK (state IN ('REPORTED', 'VERIFIED', 'DISPUTED')),
    verified_by            uuid        REFERENCES app_user (id),
    verified_amount_minor  bigint      CHECK (verified_amount_minor IS NULL OR verified_amount_minor >= 0),
    verification_reference text        CHECK (verification_reference IS NULL OR length(verification_reference) BETWEEN 1 AND 100),
    verification_reason    text        CHECK (verification_reason IS NULL OR length(verification_reason) BETWEEN 3 AND 1000),
    verified_at            timestamptz,
    version                bigint      NOT NULL DEFAULT 0,
    CONSTRAINT cash_report_verification_ck CHECK ((verified_by IS NULL) = (verified_at IS NULL)),
    CONSTRAINT cash_report_separation_ck CHECK (verified_by IS NULL OR verified_by <> reporter_id)
);
CREATE INDEX cash_report_state_idx ON cash_report (state, reported_at);

-- Double-entry groups; each posts once per source event and must balance.
CREATE TABLE ledger_group (
    id               uuid        PRIMARY KEY,
    source_event_id  uuid        NOT NULL,
    kind             text        NOT NULL CHECK (kind IN ('COMMISSION')),
    order_id         uuid        NOT NULL REFERENCES customer_order (id),
    kitchen_id       uuid        NOT NULL REFERENCES kitchen (id),
    rate_bps         integer     NOT NULL CHECK (rate_bps BETWEEN 0 AND 10000),
    base_minor       bigint      NOT NULL CHECK (base_minor >= 0),
    currency         text        NOT NULL DEFAULT 'EGP' CHECK (currency = 'EGP'),
    posted_at        timestamptz NOT NULL,
    CONSTRAINT ledger_group_source_uq UNIQUE (kind, source_event_id)
);

CREATE TABLE ledger_entry (
    id            uuid    PRIMARY KEY,
    group_id      uuid    NOT NULL REFERENCES ledger_group (id),
    account       text    NOT NULL CHECK (account IN ('COOK_COMMISSION_RECEIVABLE', 'PLATFORM_COMMISSION_REVENUE')),
    direction     text    NOT NULL CHECK (direction IN ('DEBIT', 'CREDIT')),
    amount_minor  bigint  NOT NULL CHECK (amount_minor >= 0)
);
CREATE INDEX ledger_entry_group_idx ON ledger_entry (group_id);

CREATE FUNCTION ledger_append_only() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    RAISE EXCEPTION 'ledger is append-only';
END $$;

CREATE TRIGGER ledger_group_append_only BEFORE UPDATE OR DELETE ON ledger_group FOR EACH ROW EXECUTE FUNCTION ledger_append_only();
CREATE TRIGGER ledger_entry_append_only BEFORE UPDATE OR DELETE ON ledger_entry FOR EACH ROW EXECUTE FUNCTION ledger_append_only();

-- Checked at commit: every group touched by the transaction balances and has entries.
CREATE FUNCTION ledger_group_balanced() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE
    debits bigint;
    credits bigint;
BEGIN
    SELECT coalesce(sum(amount_minor) FILTER (WHERE direction = 'DEBIT'), 0),
           coalesce(sum(amount_minor) FILTER (WHERE direction = 'CREDIT'), 0)
      INTO debits, credits FROM ledger_entry WHERE group_id = NEW.group_id;
    IF debits <> credits THEN
        RAISE EXCEPTION 'ledger group % unbalanced: debits % credits %', NEW.group_id, debits, credits;
    END IF;
    RETURN NULL;
END $$;

CREATE CONSTRAINT TRIGGER ledger_entry_balanced AFTER INSERT ON ledger_entry DEFERRABLE INITIALLY DEFERRED
    FOR EACH ROW EXECUTE FUNCTION ledger_group_balanced();
