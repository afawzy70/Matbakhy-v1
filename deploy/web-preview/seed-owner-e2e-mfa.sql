-- Matbakhy owner E2E fixtures, part 2: the MFA permissions for the second administrator (FX-STAFF-ADMIN-B).
-- Apply ONLY after the release that contains migration V31 is deployed (the permission codes do not exist before it), and after seed-owner-e2e.sql:
--   docker compose --env-file .env -f compose.yaml exec -T postgres psql -U matbakhy -d matbakhy -v ON_ERROR_STOP=1 < seed-owner-e2e-mfa.sql
-- Idempotent. Contains no secrets.
BEGIN;
INSERT INTO user_grant (id, user_id, permission, granted_by, grant_reason)
SELECT gen_random_uuid(), g.user_id::uuid, g.permission, '00000000-0000-4000-8000-0000000b00e1', 'owner e2e fixture'
FROM (VALUES
  ('00000000-0000-4000-8000-0000000b01c4', 'security.mfa_policy'), ('00000000-0000-4000-8000-0000000b01c4', 'security.mfa_reset')
) AS g(user_id, permission)
WHERE NOT EXISTS (SELECT 1 FROM user_grant ug WHERE ug.user_id = g.user_id::uuid AND ug.permission = g.permission AND ug.revoked_at IS NULL);
COMMIT;
