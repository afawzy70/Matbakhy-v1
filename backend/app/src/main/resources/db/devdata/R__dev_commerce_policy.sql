-- SYNTHETIC development commerce policy (dev profile only; EnvironmentGuard forbids devdata outside LOCAL/CI).
-- Values are placeholders pending owner decision O04, never real commercial terms.
INSERT INTO commerce_policy (version, label, synthetic, status, tax_rate_bps, commission_rate_bps, max_item_quantity, quote_ttl_seconds,
                             acceptance_window_minutes, min_acceptance_minutes, cancellation_policy_version)
VALUES (1, 'SYNTHETIC development policy (not for live use)', true, 'ACTIVE', 0, 1000, 10, 300, 15, 5, 'synthetic-cancel-v1')
ON CONFLICT (version) DO NOTHING;
