-- Matbakhy owner E2E fixtures: EXTRA synthetic actors on top of seed-preview.sql (run that first). Never real people or data.
-- Apply (operator, owner-approved, never against production):
--   docker compose --env-file .env -f compose.yaml exec -T postgres psql -U matbakhy -d matbakhy -v ON_ERROR_STOP=1 \
--     -v issuer="https://id.$PREVIEW_DOMAIN/realms/matbakhy" < seed-owner-e2e.sql
-- The matching Keycloak users are created from the private files that scripts/owner_e2e_fixtures.py renders (passwords are never in this repository).
-- Idempotent: re-running changes nothing. The Keycloak user ids below are fixed so the first sign-in finds the app_user row.
BEGIN;
-- FX-COOK-B: the seeded second kitchen becomes loginable by pointing its app_user at the new Keycloak user.
UPDATE app_user SET issuer = :'issuer', subject = '7d3f0a52-1c0e-4b8e-9a3e-0000000000b1', display_name = 'Owner E2E Cook B', email_verified = true,
       phone_e164 = '+201000000202', phone_verified_at = now()
 WHERE id = '00000000-0000-4000-8000-0000000b00a2' AND issuer = 'preview-seed';

INSERT INTO app_user (id, issuer, subject, display_name, email_verified, phone_e164, phone_verified_at) VALUES
  ('00000000-0000-4000-8000-0000000b01b2', :'issuer', '7d3f0a52-1c0e-4b8e-9a3e-0000000000b2', 'Owner E2E Cook Pending', true, '+201000000203', now()),
  ('00000000-0000-4000-8000-0000000b01c1', :'issuer', '7d3f0a52-1c0e-4b8e-9a3e-0000000000c1', 'Owner E2E Support', true, NULL, NULL),
  ('00000000-0000-4000-8000-0000000b01c2', :'issuer', '7d3f0a52-1c0e-4b8e-9a3e-0000000000c2', 'Owner E2E Operations', true, NULL, NULL),
  ('00000000-0000-4000-8000-0000000b01c3', :'issuer', '7d3f0a52-1c0e-4b8e-9a3e-0000000000c3', 'Owner E2E Finance', true, NULL, NULL),
  ('00000000-0000-4000-8000-0000000b01c4', :'issuer', '7d3f0a52-1c0e-4b8e-9a3e-0000000000c4', 'Owner E2E Admin B', true, NULL, NULL),
  ('00000000-0000-4000-8000-0000000b01c5', :'issuer', '7d3f0a52-1c0e-4b8e-9a3e-0000000000c5', 'Owner E2E Multirole', true, '+201000000205', now())
ON CONFLICT (issuer, subject) DO NOTHING;

-- FX-COOK-PENDING: application submitted, waiting for review.
INSERT INTO cook_application (id, user_id, state, public_name, bio, contact_phone, private_location, pickup_enabled, current_revision) VALUES
  ('00000000-0000-4000-8000-0000000ba1b2', '00000000-0000-4000-8000-0000000b01b2', 'SUBMITTED', 'مطبخ تحت المراجعة', 'Synthetic pending kitchen', '+201000000203', 'Synthetic location 3', true, 1)
ON CONFLICT (id) DO NOTHING;
INSERT INTO cook_application_revision (application_id, revision, public_name, bio, contact_phone, private_location, pickup_enabled, zone_ids, submitted_at) VALUES
  ('00000000-0000-4000-8000-0000000ba1b2', 1, 'مطبخ تحت المراجعة', 'Synthetic pending kitchen', '+201000000203', 'Synthetic location 3', true, '{}', now())
ON CONFLICT DO NOTHING;

-- FX-MULTIROLE: an approved application and an active kitchen (a customer, cook and support agent in one identity).
INSERT INTO cook_application (id, user_id, state, public_name, bio, contact_phone, private_location, pickup_enabled, current_revision) VALUES
  ('00000000-0000-4000-8000-0000000ba1c5', '00000000-0000-4000-8000-0000000b01c5', 'APPROVED', 'مطبخ متعدد الأدوار', 'Synthetic multirole kitchen', '+201000000205', 'Synthetic location 5', true, 1)
ON CONFLICT (id) DO NOTHING;
INSERT INTO cook_application_revision (application_id, revision, public_name, bio, contact_phone, private_location, pickup_enabled, zone_ids, submitted_at, decision, decision_reason, reviewer_id, decided_at) VALUES
  ('00000000-0000-4000-8000-0000000ba1c5', 1, 'مطبخ متعدد الأدوار', 'Synthetic multirole kitchen', '+201000000205', 'Synthetic location 5', true, '{}', now(), 'APPROVED', 'owner e2e seed', '00000000-0000-4000-8000-0000000b00e1', now())
ON CONFLICT DO NOTHING;
INSERT INTO kitchen (id, owner_id, application_id, approved_revision, public_slug, display_name, description, operating_state, state_reason, pickup_enabled, contact_phone, private_location) VALUES
  ('00000000-0000-4000-8000-0000000bd1c5', '00000000-0000-4000-8000-0000000b01c5', '00000000-0000-4000-8000-0000000ba1c5', 1, 'owner-e2e-multirole', 'مطبخ متعدد الأدوار', 'Synthetic multirole kitchen', 'ACTIVE', NULL, true, '+201000000205', 'Synthetic location 5')
ON CONFLICT (id) DO NOTHING;

-- Staff grants (granted_by is the seeded reviewer row, as in seed-preview.sql). Baseline-compatible permission codes only: the two MFA
-- permissions exist from migration V31 and are granted by seed-owner-e2e-mfa.sql (a V4 database rejects them and would roll this file back).
INSERT INTO user_grant (id, user_id, permission, granted_by, grant_reason)
SELECT gen_random_uuid(), g.user_id::uuid, g.permission, '00000000-0000-4000-8000-0000000b00e1', 'owner e2e fixture'
FROM (VALUES
  ('00000000-0000-4000-8000-0000000b01c1', 'support.case'),
  ('00000000-0000-4000-8000-0000000b01c2', 'cook.review'), ('00000000-0000-4000-8000-0000000b01c2', 'content.moderate'),
  ('00000000-0000-4000-8000-0000000b01c2', 'order.ops'), ('00000000-0000-4000-8000-0000000b01c2', 'order.exception_cancel'),
  ('00000000-0000-4000-8000-0000000b01c2', 'order.handover_override'),
  ('00000000-0000-4000-8000-0000000b01c3', 'finance.read'), ('00000000-0000-4000-8000-0000000b01c3', 'finance.verify'),
  ('00000000-0000-4000-8000-0000000b01c3', 'finance.refund_approve'), ('00000000-0000-4000-8000-0000000b01c3', 'finance.settlement'),
  ('00000000-0000-4000-8000-0000000b01c3', 'config.read'),
  ('00000000-0000-4000-8000-0000000b01c4', 'staff.grant'), ('00000000-0000-4000-8000-0000000b01c4', 'audit.read'),
  ('00000000-0000-4000-8000-0000000b01c4', 'config.read'), ('00000000-0000-4000-8000-0000000b01c4', 'config.write'),
  ('00000000-0000-4000-8000-0000000b01c5', 'support.case')
) AS g(user_id, permission)
WHERE NOT EXISTS (SELECT 1 FROM user_grant ug WHERE ug.user_id = g.user_id::uuid AND ug.permission = g.permission AND ug.revoked_at IS NULL);
COMMIT;
