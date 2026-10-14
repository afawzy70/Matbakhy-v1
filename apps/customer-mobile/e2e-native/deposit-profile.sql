-- MB-053 native checks (CI only, local stack): the synthetic kitchen takes a 50% deposit to an approved synthetic
-- recipient, and cash on receipt only up to 1 EGP, so the app meets both COD_NOT_ELIGIBLE and the deposit path.
-- The approver is any existing synthetic account: staff approval itself is covered by PaymentReconciliationIntegrationTest.
-- Test-only data; never a real account. Removed again at the end of the phase (deposit-profile-reset below).
INSERT INTO kitchen_payment_profile (kitchen_id, deposit_bps, cod_max_total_minor, recipient_method, recipient_identifier, recipient_name,
                                     recipient_status, approved_by, approved_at)
SELECT :'kitchen', 5000, 100, 'INSTAPAY', 'synthetic-kitchen@instapay', 'Synthetic Kitchen', 'APPROVED', (SELECT id FROM app_user ORDER BY id LIMIT 1), now()
ON CONFLICT (kitchen_id) DO UPDATE SET deposit_bps = EXCLUDED.deposit_bps, cod_max_total_minor = EXCLUDED.cod_max_total_minor,
    recipient_method = EXCLUDED.recipient_method, recipient_identifier = EXCLUDED.recipient_identifier, recipient_name = EXCLUDED.recipient_name,
    recipient_status = EXCLUDED.recipient_status, approved_by = EXCLUDED.approved_by, approved_at = EXCLUDED.approved_at;
