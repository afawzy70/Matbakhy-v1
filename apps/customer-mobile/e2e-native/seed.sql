-- MB-032 native ordering checks (CI only, local stack): two synthetic approved kitchens, each with one published
-- meal and one open pickup slot tomorrow (Africa/Cairo). Synthetic names only; inserted directly as the backend test
-- fixtures do, because meal publication through the API needs a moderated photo upload.
INSERT INTO app_user (id, issuer, subject, display_name) VALUES
  ('00000000-0000-4000-8000-00000000a0e1', 'native-e2e', 'native-reviewer', 'Native reviewer'),
  ('00000000-0000-4000-8000-00000000a0c1', 'native-e2e', 'native-cook-1', 'Native cook one'),
  ('00000000-0000-4000-8000-00000000a0c2', 'native-e2e', 'native-cook-2', 'Native cook two');

INSERT INTO cook_application (id, user_id, state, public_name, bio, contact_phone, private_location, pickup_enabled, current_revision) VALUES
  ('00000000-0000-4000-8000-00000000a0a1', '00000000-0000-4000-8000-00000000a0c1', 'APPROVED', 'Native Kitchen One', 'Synthetic', '+201001234561', 'Synthetic location 1', true, 1),
  ('00000000-0000-4000-8000-00000000a0a2', '00000000-0000-4000-8000-00000000a0c2', 'APPROVED', 'Native Kitchen Two', 'Synthetic', '+201001234562', 'Synthetic location 2', true, 1);
INSERT INTO cook_application_revision (application_id, revision, public_name, bio, contact_phone, private_location, pickup_enabled, zone_ids,
                                       submitted_at, decision, decision_reason, reviewer_id, decided_at) VALUES
  ('00000000-0000-4000-8000-00000000a0a1', 1, 'Native Kitchen One', 'Synthetic', '+201001234561', 'Synthetic location 1', true, '{}', now(), 'APPROVED', 'native e2e fixture', '00000000-0000-4000-8000-00000000a0e1', now()),
  ('00000000-0000-4000-8000-00000000a0a2', 1, 'Native Kitchen Two', 'Synthetic', '+201001234562', 'Synthetic location 2', true, '{}', now(), 'APPROVED', 'native e2e fixture', '00000000-0000-4000-8000-00000000a0e1', now());
INSERT INTO kitchen (id, owner_id, application_id, approved_revision, public_slug, display_name, description, pickup_enabled, contact_phone, private_location) VALUES
  ('00000000-0000-4000-8000-00000000a0d1', '00000000-0000-4000-8000-00000000a0c1', '00000000-0000-4000-8000-00000000a0a1', 1, 'native-kitchen-one', 'Native Kitchen One', 'Synthetic', true, '+201001234561', 'Synthetic location 1'),
  ('00000000-0000-4000-8000-00000000a0d2', '00000000-0000-4000-8000-00000000a0c2', '00000000-0000-4000-8000-00000000a0a2', 1, 'native-kitchen-two', 'Native Kitchen Two', 'Synthetic', true, '+201001234562', 'Synthetic location 2');

INSERT INTO meal (id, kitchen_id, status, title_ar, title_en, description_ar, description_en, portion_ar, ingredients_ar, allergen_declaration, allergen_codes, price_minor, published_at) VALUES
  ('00000000-0000-4000-8000-00000000a0b1', '00000000-0000-4000-8000-00000000a0d1', 'PUBLISHED', 'طبق تجريبي واحد', 'Native Dish One', 'وصف تجريبي', 'Synthetic description', 'علبة', 'مكونات', 'CONTAINS', '{GLUTEN}', 12000, now()),
  ('00000000-0000-4000-8000-00000000a0b2', '00000000-0000-4000-8000-00000000a0d2', 'PUBLISHED', 'طبق تجريبي اثنان', 'Native Dish Two', 'وصف تجريبي', 'Synthetic description', 'علبة', 'مكونات', 'CONTAINS', '{GLUTEN}', 9000, now());
INSERT INTO meal_revision (meal_id, revision, snapshot, price_minor) VALUES
  ('00000000-0000-4000-8000-00000000a0b1', 1, '{"titleAr":"طبق تجريبي واحد","titleEn":"Native Dish One","allergenDeclaration":"CONTAINS","allergenCodes":["GLUTEN"],"priceMinor":12000,"currency":"EGP"}', 12000),
  ('00000000-0000-4000-8000-00000000a0b2', 1, '{"titleAr":"طبق تجريبي اثنان","titleEn":"Native Dish Two","allergenDeclaration":"CONTAINS","allergenCodes":["GLUTEN"],"priceMinor":9000,"currency":"EGP"}', 9000);

WITH s AS (SELECT date_trunc('minute', now() + interval '1 day') AS starts)
INSERT INTO cook_slot (id, kitchen_id, local_date, starts_at, ends_at, lead_minutes, cutoff_at, max_portions, state)
SELECT v.id, v.kitchen, (s.starts AT TIME ZONE 'Africa/Cairo')::date, s.starts, s.starts + interval '1 hour', 120, s.starts - interval '2 hours', 10, 'OPEN'
FROM s, (VALUES ('00000000-0000-4000-8000-00000000a051'::uuid, '00000000-0000-4000-8000-00000000a0d1'::uuid),
                ('00000000-0000-4000-8000-00000000a052'::uuid, '00000000-0000-4000-8000-00000000a0d2'::uuid)) AS v(id, kitchen);
INSERT INTO meal_slot (id, cook_slot_id, meal_id, capacity) VALUES
  ('00000000-0000-4000-8000-00000000a061', '00000000-0000-4000-8000-00000000a051', '00000000-0000-4000-8000-00000000a0b1', 10),
  ('00000000-0000-4000-8000-00000000a062', '00000000-0000-4000-8000-00000000a052', '00000000-0000-4000-8000-00000000a0b2', 10);
