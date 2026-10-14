-- params:
SELECT json_build_object('terms', terms_version, 'cancellation', cancellation_policy_version) FROM commerce_policy WHERE status = 'ACTIVE';
