-- params: subject phone
-- The test identity provider sends no SMS: the customer's phone counts as verified (synthetic number).
UPDATE app_user SET phone_e164 = :'phone', phone_verified_at = now(), email_verified = true WHERE subject = :'subject';
