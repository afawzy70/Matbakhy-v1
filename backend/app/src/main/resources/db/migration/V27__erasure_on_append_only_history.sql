-- Account erasure (MB-039, BR-055) on append-only history, found 2026-10-13. A customer's own support replies and the
-- transfer reference a customer typed for a deposit (D16) are personal data in tables that are otherwise append-only.
-- Exactly one change is allowed on each, and only for erasure; every other update, any delete and truncate still fail.

-- case_message: a CUSTOMER message's body may be replaced by the erasure marker, nothing else.
CREATE OR REPLACE FUNCTION case_message_immutable() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    IF TG_OP = 'UPDATE' AND OLD.author_role = 'CUSTOMER' AND NEW.body = '[erased on account deletion]'
       AND NEW.id = OLD.id AND NEW.case_id = OLD.case_id AND NEW.author_id IS NOT DISTINCT FROM OLD.author_id
       AND NEW.author_role = OLD.author_role AND NEW.visibility = OLD.visibility AND NEW.created_at = OLD.created_at THEN
        RETURN NEW;
    END IF;
    RAISE EXCEPTION 'case_message is append-only';
END $$;

-- payment_event: a CUSTOMER event's reference may be cleared, nothing else (kitchen and staff history stays).
CREATE FUNCTION payment_event_append_only() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    IF TG_OP = 'UPDATE' AND OLD.actor_type = 'CUSTOMER' AND OLD.reference IS NOT NULL AND NEW.reference IS NULL
       AND NEW.id = OLD.id AND NEW.order_id = OLD.order_id AND NEW.from_state IS NOT DISTINCT FROM OLD.from_state
       AND NEW.to_state = OLD.to_state AND NEW.actor_type = OLD.actor_type AND NEW.actor_id IS NOT DISTINCT FROM OLD.actor_id
       AND NEW.amount_minor IS NOT DISTINCT FROM OLD.amount_minor AND NEW.reason_code IS NOT DISTINCT FROM OLD.reason_code
       AND NEW.note IS NOT DISTINCT FROM OLD.note AND NEW.request_id IS NOT DISTINCT FROM OLD.request_id
       AND NEW.occurred_at = OLD.occurred_at THEN
        RETURN NEW;
    END IF;
    RAISE EXCEPTION 'payment_event is append-only';
END $$;
DROP TRIGGER payment_event_append_only ON payment_event;
CREATE TRIGGER payment_event_append_only BEFORE UPDATE OR DELETE ON payment_event FOR EACH ROW EXECUTE FUNCTION payment_event_append_only();
CREATE TRIGGER payment_event_no_truncate BEFORE TRUNCATE ON payment_event FOR EACH STATEMENT EXECUTE FUNCTION payment_event_append_only();
