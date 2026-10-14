-- params: subject
-- Synthetic finance.read grant for the money-layout journey (MBQA-006); once per user.
INSERT INTO user_grant (id, user_id, permission, grant_reason)
  SELECT gen_random_uuid(), id, 'finance.read', 'e2e synthetic grant (MBQA-006)' FROM app_user WHERE subject = :'subject'
  AND NOT EXISTS (SELECT 1 FROM user_grant g WHERE g.user_id = app_user.id AND g.permission = 'finance.read' AND g.revoked_at IS NULL);
