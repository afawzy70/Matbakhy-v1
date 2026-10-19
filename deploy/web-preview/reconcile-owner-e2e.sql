-- Read-only reconciliation of the owner E2E fixtures (CI: .github/workflows/owner-e2e-fixtures.yml; also usable by the operator).
-- Each row is one check and its number of violations; every count must be 0. Contains no secrets and changes nothing.
-- Variables (set by scripts/owner_e2e_reconcile.py from docs/quality/owner-acceptance/fixtures.json):
--   expected_users   comma-separated app_user ids that must exist, one per fixture with an "app" id
--   expected_grants  comma-separated "<app_user id>:<permission>" pairs: exactly the active grants those staff fixtures must hold
WITH exp_users AS (
    SELECT u::uuid AS id FROM unnest(string_to_array(:'expected_users', ',')) AS u
), exp_grants AS (
    SELECT split_part(g, ':', 1)::uuid AS user_id, split_part(g, ':', 2) AS permission
    FROM unnest(string_to_array(:'expected_grants', ',')) AS g
), staff AS (
    SELECT DISTINCT user_id FROM exp_grants
), seeded_meals AS (
    SELECT unnest(ARRAY['00000000-0000-4000-8000-0000000bb001', '00000000-0000-4000-8000-0000000bb002',
                        '00000000-0000-4000-8000-0000000bb003']::uuid[]) AS id
)
SELECT check_name, violations FROM (
    SELECT 1 AS ord, 'fixture_app_user_exactly_once' AS check_name, count(*) AS violations
    FROM exp_users e WHERE (SELECT count(*) FROM app_user a WHERE a.id = e.id) <> 1
    UNION ALL
    SELECT 2, 'no_duplicate_issuer_subject', count(*)
    FROM (SELECT issuer, subject FROM app_user GROUP BY issuer, subject HAVING count(*) > 1) d
    UNION ALL
    SELECT 3, 'expected_grant_missing', count(*)
    FROM exp_grants g WHERE NOT EXISTS (SELECT 1 FROM user_grant ug WHERE ug.user_id = g.user_id AND ug.permission = g.permission AND ug.revoked_at IS NULL)
    UNION ALL
    SELECT 4, 'unexpected_active_grant_on_staff_fixture', count(*)
    FROM user_grant ug JOIN staff s ON s.user_id = ug.user_id
    WHERE ug.revoked_at IS NULL AND NOT EXISTS (SELECT 1 FROM exp_grants g WHERE g.user_id = ug.user_id AND g.permission = ug.permission)
    UNION ALL
    SELECT 5, 'duplicate_active_grant', count(*)
    FROM (SELECT user_id, permission FROM user_grant WHERE revoked_at IS NULL GROUP BY user_id, permission HAVING count(*) > 1) d
    UNION ALL
    SELECT 6, 'seeded_meal_not_published_with_open_future_slot', count(*)
    FROM seeded_meals sm
    WHERE NOT EXISTS (SELECT 1 FROM meal m WHERE m.id = sm.id AND m.status = 'PUBLISHED')
       OR NOT EXISTS (SELECT 1 FROM meal_slot ms JOIN cook_slot cs ON cs.id = ms.cook_slot_id
                      WHERE ms.meal_id = sm.id AND cs.state = 'OPEN' AND cs.starts_at > now())
    UNION ALL
    SELECT 7, 'duplicate_meal_slot_pair', count(*)
    FROM (SELECT cook_slot_id, meal_id FROM meal_slot GROUP BY cook_slot_id, meal_id HAVING count(*) > 1) d
    UNION ALL
    SELECT 8, 'cook_b_not_loginable', count(*)
    FROM (SELECT 1) one WHERE NOT EXISTS (SELECT 1 FROM app_user WHERE id = '00000000-0000-4000-8000-0000000b00a2' AND subject = '7d3f0a52-1c0e-4b8e-9a3e-0000000000b1')
    UNION ALL
    SELECT 9, 'pending_application_not_submitted_or_multirole_kitchen_missing', count(*)
    FROM (SELECT 1) one WHERE NOT EXISTS (SELECT 1 FROM cook_application WHERE id = '00000000-0000-4000-8000-0000000ba1b2' AND state = 'SUBMITTED')
       OR NOT EXISTS (SELECT 1 FROM kitchen WHERE id = '00000000-0000-4000-8000-0000000bd1c5' AND operating_state = 'ACTIVE')
) c ORDER BY ord;
