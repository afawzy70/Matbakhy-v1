-- Kane CLI QA pilot: synthetic discovery/basket fixture. LOCAL THROWAWAY STACK ONLY.
-- Direct database setup (not a tested behaviour): two approved, active kitchens in synthetic "Test zone A", four
-- meals and three open future slots. Every name, phone and location is invented. Fixed QA UUIDs (prefix ...0e...).
-- Re-runnable: rows are inserted once; the UPDATE block puts states and counters back to the known baseline.
BEGIN;

INSERT INTO app_user (id, issuer, subject, email_verified, display_name) VALUES
  ('00000000-0000-4000-8000-0000000e0001', 'qa-fixture', 'qa-cook-a',   true, 'QA Cook A'),
  ('00000000-0000-4000-8000-0000000e0002', 'qa-fixture', 'qa-cook-b',   true, 'QA Cook B'),
  ('00000000-0000-4000-8000-0000000e0003', 'qa-fixture', 'qa-reviewer', true, 'QA Reviewer')
ON CONFLICT (id) DO NOTHING;

INSERT INTO cook_application (id, user_id, state, public_name, bio, contact_phone, private_location, pickup_enabled, zone_ids, current_revision) VALUES
  ('00000000-0000-4000-8000-0000000e0a01', '00000000-0000-4000-8000-0000000e0001', 'APPROVED', 'QA Kitchen A', 'Synthetic QA kitchen A',
   '+201000000001', 'Synthetic location A', true, '{00000000-0000-4000-8000-00000000a001}', 1),
  ('00000000-0000-4000-8000-0000000e0a02', '00000000-0000-4000-8000-0000000e0002', 'APPROVED', 'QA Kitchen B', 'Synthetic QA kitchen B',
   '+201000000002', 'Synthetic location B', true, '{00000000-0000-4000-8000-00000000a001}', 1)
ON CONFLICT (id) DO NOTHING;

INSERT INTO cook_application_revision (application_id, revision, public_name, bio, contact_phone, private_location, pickup_enabled, zone_ids,
                                       submitted_at, decision, decision_reason, reviewer_id, decided_at) VALUES
  ('00000000-0000-4000-8000-0000000e0a01', 1, 'QA Kitchen A', 'Synthetic QA kitchen A', '+201000000001', 'Synthetic location A', true,
   '{00000000-0000-4000-8000-00000000a001}', now(), 'APPROVED', 'QA fixture', '00000000-0000-4000-8000-0000000e0003', now()),
  ('00000000-0000-4000-8000-0000000e0a02', 1, 'QA Kitchen B', 'Synthetic QA kitchen B', '+201000000002', 'Synthetic location B', true,
   '{00000000-0000-4000-8000-00000000a001}', now(), 'APPROVED', 'QA fixture', '00000000-0000-4000-8000-0000000e0003', now())
ON CONFLICT DO NOTHING;

INSERT INTO kitchen (id, owner_id, application_id, approved_revision, public_slug, display_name, description, pickup_enabled, contact_phone, private_location) VALUES
  ('00000000-0000-4000-8000-0000000e0b01', '00000000-0000-4000-8000-0000000e0001', '00000000-0000-4000-8000-0000000e0a01', 1,
   'qa-kitchen-a', 'مطبخ اختبار أ', 'مطبخ اختبار اصطناعي أ', true, '+201000000001', 'Synthetic location A'),
  ('00000000-0000-4000-8000-0000000e0b02', '00000000-0000-4000-8000-0000000e0002', '00000000-0000-4000-8000-0000000e0a02', 1,
   'qa-kitchen-b', 'مطبخ اختبار ب', 'مطبخ اختبار اصطناعي ب', true, '+201000000002', 'Synthetic location B')
ON CONFLICT (id) DO NOTHING;

INSERT INTO kitchen_zone (kitchen_id, zone_id) VALUES
  ('00000000-0000-4000-8000-0000000e0b01', '00000000-0000-4000-8000-00000000a001'),
  ('00000000-0000-4000-8000-0000000e0b02', '00000000-0000-4000-8000-00000000a001')
ON CONFLICT DO NOTHING;

-- A1: Arabic + English description. A2: Arabic description only (a published meal must carry description_ar, so a
-- fully description-less published meal cannot exist; in English the API falls back to the Arabic text).
-- B1: other kitchen. X1: PAUSED, must not be purchasable (BR-017).
INSERT INTO meal (id, kitchen_id, status, title_ar, title_en, description_ar, description_en, portion_ar, portion_en, ingredients_ar,
                  allergen_declaration, price_minor, category, published_at) VALUES
  ('00000000-0000-4000-8000-0000000e0c01', '00000000-0000-4000-8000-0000000e0b01', 'PUBLISHED', 'ملوخية اختبار', 'QA Molokhia',
   'ملوخية خضراء بالثوم وصف اختباري', 'Green molokhia with garlic, QA description', 'طبق لشخص', 'One plate', 'ملوخية، ثوم، مرق',
   'NONE_DECLARED', 8500, 'MAIN_DISHES', now() - interval '3 minutes'),
  ('00000000-0000-4000-8000-0000000e0c02', '00000000-0000-4000-8000-0000000e0b01', 'PUBLISHED', 'فتة اختبار', 'QA Fatta',
   'فتة باللحمة وصف عربي فقط', NULL, 'طبق لشخص', NULL, 'عيش، أرز، لحم',
   'NONE_DECLARED', 6000, 'MAIN_DISHES', now() - interval '2 minutes'),
  ('00000000-0000-4000-8000-0000000e0c03', '00000000-0000-4000-8000-0000000e0b02', 'PUBLISHED', 'محشي اختبار', 'QA Mahshi',
   'محشي كرنب وصف اختباري للمطبخ ب', 'Stuffed cabbage, QA description for kitchen B', 'طبق لشخصين', 'Serves two', 'كرنب، أرز',
   'NONE_DECLARED', 12000, 'MAIN_DISHES', now() - interval '1 minute'),
  ('00000000-0000-4000-8000-0000000e0c09', '00000000-0000-4000-8000-0000000e0b01', 'PAUSED', 'وجبة متوقفة اختبار', 'QA Paused Meal',
   'وجبة متوقفة لا يجب بيعها', 'Paused meal that must not be sold', 'طبق لشخص', 'One plate', 'مكونات اختبار',
   'NONE_DECLARED', 5000, 'MAIN_DISHES', now() - interval '4 minutes')
ON CONFLICT (id) DO NOTHING;

-- Slots: tomorrow (Cairo) S1 13:00 and S2 19:00 for kitchen A, SB 14:00 for kitchen B; 60-minute lead time.
WITH d AS (SELECT (now() AT TIME ZONE 'Africa/Cairo')::date + 1 AS day)
INSERT INTO cook_slot (id, kitchen_id, local_date, starts_at, ends_at, lead_minutes, cutoff_at, max_portions, state)
SELECT id, kitchen_id, d.day, (d.day + t) AT TIME ZONE 'Africa/Cairo', (d.day + t + interval '1 hour') AT TIME ZONE 'Africa/Cairo',
       60, (d.day + t - interval '60 minutes') AT TIME ZONE 'Africa/Cairo', 50, 'OPEN'
FROM d, (VALUES ('00000000-0000-4000-8000-0000000e0d01'::uuid, '00000000-0000-4000-8000-0000000e0b01'::uuid, time '13:00'),
                ('00000000-0000-4000-8000-0000000e0d02'::uuid, '00000000-0000-4000-8000-0000000e0b01'::uuid, time '19:00'),
                ('00000000-0000-4000-8000-0000000e0d03'::uuid, '00000000-0000-4000-8000-0000000e0b02'::uuid, time '14:00')) s(id, kitchen_id, t)
ON CONFLICT (id) DO NOTHING;

INSERT INTO meal_slot (id, cook_slot_id, meal_id, capacity) VALUES
  ('00000000-0000-4000-8000-0000000e0e01', '00000000-0000-4000-8000-0000000e0d01', '00000000-0000-4000-8000-0000000e0c01', 10), -- A1 @ S1
  ('00000000-0000-4000-8000-0000000e0e02', '00000000-0000-4000-8000-0000000e0d02', '00000000-0000-4000-8000-0000000e0c01', 10), -- A1 @ S2
  ('00000000-0000-4000-8000-0000000e0e03', '00000000-0000-4000-8000-0000000e0d02', '00000000-0000-4000-8000-0000000e0c02', 10), -- A2 @ S2
  ('00000000-0000-4000-8000-0000000e0e04', '00000000-0000-4000-8000-0000000e0d03', '00000000-0000-4000-8000-0000000e0c03', 10), -- B1 @ SB
  ('00000000-0000-4000-8000-0000000e0e05', '00000000-0000-4000-8000-0000000e0d01', '00000000-0000-4000-8000-0000000e0c09', 10)  -- X1 @ S1
ON CONFLICT (id) DO NOTHING;

-- Baseline reset (known state for every case).
UPDATE kitchen SET operating_state = 'ACTIVE', paused_by = NULL WHERE id IN ('00000000-0000-4000-8000-0000000e0b01', '00000000-0000-4000-8000-0000000e0b02');
UPDATE cook_slot SET state = 'OPEN', reserved = 0, allocated = 0, consumed = 0 WHERE id::text LIKE '00000000-0000-4000-8000-0000000e0d0%';
UPDATE meal_slot SET reserved = 0, allocated = 0, consumed = 0, capacity = 10 WHERE id::text LIKE '00000000-0000-4000-8000-0000000e0e0%';

COMMIT;
