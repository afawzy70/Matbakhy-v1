-- Matbakhy preview: add the next three days of open 13:00 Cairo slots (ten portions per seeded meal) for the seeded kitchens.
-- Repeatable and additive (existing slots are left alone, so counters of used slots are never reset); seed-preview.sql is NOT repeatable.
--   docker compose --env-file .env -f compose.yaml exec -T postgres psql -U matbakhy -d matbakhy -v ON_ERROR_STOP=1 < refresh-slots.sql
BEGIN;
INSERT INTO cook_slot (id, kitchen_id, local_date, starts_at, ends_at, lead_minutes, cutoff_at, max_portions, state)
SELECT gen_random_uuid(), k.id, d::date, (d::date + time '13:00') AT TIME ZONE 'Africa/Cairo',
       (d::date + time '14:00') AT TIME ZONE 'Africa/Cairo', 120, (d::date + time '11:00') AT TIME ZONE 'Africa/Cairo', 20, 'OPEN'
FROM (VALUES ('00000000-0000-4000-8000-0000000bd001'::uuid), ('00000000-0000-4000-8000-0000000bd002'::uuid)) AS k(id),
     generate_series((now() AT TIME ZONE 'Africa/Cairo')::date + 1, (now() AT TIME ZONE 'Africa/Cairo')::date + 3, interval '1 day') AS d
WHERE NOT EXISTS (SELECT 1 FROM cook_slot cs WHERE cs.kitchen_id = k.id AND cs.starts_at = (d::date + time '13:00') AT TIME ZONE 'Africa/Cairo' AND cs.state <> 'CANCELLED');
INSERT INTO meal_slot (id, cook_slot_id, meal_id, capacity)
SELECT gen_random_uuid(), cs.id, m.id, 10 FROM cook_slot cs JOIN meal m ON m.kitchen_id = cs.kitchen_id
WHERE m.id IN ('00000000-0000-4000-8000-0000000bb001', '00000000-0000-4000-8000-0000000bb002', '00000000-0000-4000-8000-0000000bb003')
  AND cs.state = 'OPEN' AND cs.starts_at > now()
ON CONFLICT (cook_slot_id, meal_id) DO NOTHING;
COMMIT;
