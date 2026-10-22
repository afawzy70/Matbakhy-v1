-- SYNTHETIC development commerce policy (dev profile only; EnvironmentGuard forbids devdata outside LOCAL/CI).
-- Values are placeholders pending owner decision O04, never real commercial terms. Commission is 0 (owner decision D10).
INSERT INTO commerce_policy (version, label, synthetic, status, tax_rate_bps, commission_rate_bps, max_item_quantity, quote_ttl_seconds,
                             acceptance_window_minutes, min_acceptance_minutes, cancellation_policy_version, terms_version)
VALUES (1, 'SYNTHETIC development policy (not for live use)', true, 'ACTIVE', 0, 0, 10, 300, 15, 5, 'synthetic-cancel-v1', 'synthetic-terms-v1')
ON CONFLICT (version) DO NOTHING;
-- Databases created before D10 keep version 1 from the INSERT above; bring its commission to 0 as well.
UPDATE commerce_policy SET commission_rate_bps = 0 WHERE version = 1 AND synthetic AND commission_rate_bps <> 0;
