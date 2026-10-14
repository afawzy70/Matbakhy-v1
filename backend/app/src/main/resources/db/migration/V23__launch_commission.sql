-- Owner decision D10 (2026-10-13): no Matbakhy commission at MVP launch. A live (non-synthetic) commerce policy must
-- have a zero commission rate; only synthetic test fixtures may exercise the dormant commission capability. The API
-- also refuses non-zero rates for every policy. NOT VALID: existing rows (development data only) are not rechecked.
ALTER TABLE commerce_policy
    ADD CONSTRAINT commerce_policy_launch_commission_ck CHECK (synthetic OR commission_rate_bps = 0) NOT VALID;
