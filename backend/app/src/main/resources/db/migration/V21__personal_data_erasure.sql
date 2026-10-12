-- MB-039: account erasure (TC-055) under the proposed retention default (O06 open). Personal data is removed or
-- replaced by an erasure marker; order amounts, items, events and the ledger are kept.

-- Erased addresses keep their row (quotes reference them) without personal fields.
ALTER TABLE address ADD COLUMN erased_at timestamptz;
ALTER TABLE address ALTER COLUMN recipient_name DROP NOT NULL;
ALTER TABLE address ALTER COLUMN phone_e164 DROP NOT NULL;
ALTER TABLE address ALTER COLUMN street DROP NOT NULL;
ALTER TABLE address ALTER COLUMN building DROP NOT NULL;
ALTER TABLE address ADD CONSTRAINT address_personal_fields_ck CHECK (
    erased_at IS NOT NULL OR (recipient_name IS NOT NULL AND phone_e164 IS NOT NULL AND street IS NOT NULL AND building IS NOT NULL));
ALTER TABLE address ADD CONSTRAINT address_erased_empty_ck CHECK (
    erased_at IS NULL OR (recipient_name IS NULL AND phone_e164 IS NULL AND street IS NULL AND building IS NULL
                          AND floor IS NULL AND unit IS NULL AND landmark IS NULL AND latitude IS NULL AND longitude IS NULL));

-- Terminal orders stay immutable except for one narrow erasure: contact and address snapshots become an erasure
-- marker, the free-text note is cleared and personal_data_erased_at is set once. Every other column must be unchanged.
ALTER TABLE customer_order ADD COLUMN personal_data_erased_at timestamptz;

CREATE OR REPLACE FUNCTION customer_order_terminal_guard() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE
    keep text[] := ARRAY['contact_snapshot', 'address_snapshot', 'fulfillment_note', 'personal_data_erased_at'];
BEGIN
    IF OLD.state IN ('COMPLETED', 'REJECTED', 'EXPIRED', 'CANCELLED', 'DELIVERY_FAILED') THEN
        IF OLD.personal_data_erased_at IS NULL AND NEW.personal_data_erased_at IS NOT NULL
           AND NEW.contact_snapshot = '{"erased": true}'::jsonb
           AND (NEW.address_snapshot IS NULL AND OLD.address_snapshot IS NULL OR NEW.address_snapshot = '{"erased": true}'::jsonb)
           AND NEW.fulfillment_note IS NULL
           AND (to_jsonb(NEW) - keep) = (to_jsonb(OLD) - keep) THEN
            RETURN NEW;
        END IF;
        RAISE EXCEPTION 'terminal order % is immutable', OLD.id;
    END IF;
    RETURN NEW;
END $$;
