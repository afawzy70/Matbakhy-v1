-- Matbakhy web preview: synthetic catalogue and accounts for the owner's test journeys. Never real people or data.
-- Run once after the first deploy:  ./deploy.sh seed   (psql variable :issuer = https://id.<domain>/realms/matbakhy)
-- Users match the preview realm's fixed Keycloak user ids, so the first sign-in finds them. Phones are pre-verified
-- because the preview never sends SMS. Rows are inserted directly, as the test fixtures do: publishing a meal through
-- the API needs a moderated photo, and approving a kitchen needs a staff step-up login.
BEGIN;
INSERT INTO app_user (id, issuer, subject, display_name, email_verified, phone_e164, phone_verified_at) VALUES
  ('00000000-0000-4000-8000-0000000b0001', :'issuer', '7d3f0a52-1c0e-4b8e-9a3e-000000000c01', 'Preview Customer 1', true, '+201000000101', now()),
  ('00000000-0000-4000-8000-0000000b0002', :'issuer', '7d3f0a52-1c0e-4b8e-9a3e-000000000c02', 'Preview Customer 2', true, '+201000000102', now()),
  ('00000000-0000-4000-8000-0000000b00a1', :'issuer', '7d3f0a52-1c0e-4b8e-9a3e-0000000000a1', 'Preview Cook 1', true, '+201000000201', now()),
  ('00000000-0000-4000-8000-0000000b00a2', 'preview-seed', 'preview-cook-2', 'Preview Cook 2 (no login)', false, NULL, NULL),
  ('00000000-0000-4000-8000-0000000b00e1', 'preview-seed', 'preview-reviewer', 'Preview reviewer (no login)', false, NULL, NULL)
ON CONFLICT (issuer, subject) DO NOTHING;

INSERT INTO cook_application (id, user_id, state, public_name, bio, contact_phone, private_location, pickup_enabled, current_revision) VALUES
  ('00000000-0000-4000-8000-0000000ba0a1', '00000000-0000-4000-8000-0000000b00a1', 'APPROVED', 'مطبخ المعاينة', 'Synthetic preview kitchen', '+201000000201', 'Synthetic location 1', true, 1),
  ('00000000-0000-4000-8000-0000000ba0a2', '00000000-0000-4000-8000-0000000b00a2', 'APPROVED', 'مطبخ تجريبي تاني', 'Synthetic preview kitchen', '+201000000202', 'Synthetic location 2', true, 1);
INSERT INTO cook_application_revision (application_id, revision, public_name, bio, contact_phone, private_location, pickup_enabled, zone_ids,
                                       submitted_at, decision, decision_reason, reviewer_id, decided_at) VALUES
  ('00000000-0000-4000-8000-0000000ba0a1', 1, 'مطبخ المعاينة', 'Synthetic preview kitchen', '+201000000201', 'Synthetic location 1', true,
   '{00000000-0000-4000-8000-00000000a001}', now(), 'APPROVED', 'preview seed', '00000000-0000-4000-8000-0000000b00e1', now()),
  ('00000000-0000-4000-8000-0000000ba0a2', 1, 'مطبخ تجريبي تاني', 'Synthetic preview kitchen', '+201000000202', 'Synthetic location 2', true,
   '{}', now(), 'APPROVED', 'preview seed', '00000000-0000-4000-8000-0000000b00e1', now());
INSERT INTO kitchen (id, owner_id, application_id, approved_revision, public_slug, display_name, description, pickup_enabled, contact_phone, private_location) VALUES
  ('00000000-0000-4000-8000-0000000bd001', '00000000-0000-4000-8000-0000000b00a1', '00000000-0000-4000-8000-0000000ba0a1', 1, 'preview-kitchen', 'مطبخ المعاينة', 'أكل بيتي تجريبي للمعاينة', true, '+201000000201', 'Synthetic location 1'),
  ('00000000-0000-4000-8000-0000000bd002', '00000000-0000-4000-8000-0000000b00a2', '00000000-0000-4000-8000-0000000ba0a2', 1, 'preview-kitchen-two', 'مطبخ تجريبي تاني', 'مطبخ تجريبي تاني للمعاينة', true, '+201000000202', 'Synthetic location 2');
-- The first kitchen also delivers to the synthetic "Test zone A" (db/devdata), so delivery checkout can be tried.
INSERT INTO kitchen_zone (kitchen_id, zone_id) VALUES ('00000000-0000-4000-8000-0000000bd001', '00000000-0000-4000-8000-00000000a001');

INSERT INTO meal (id, kitchen_id, status, title_ar, title_en, description_ar, description_en, portion_ar, ingredients_ar, allergen_declaration, allergen_codes, price_minor, published_at) VALUES
  ('00000000-0000-4000-8000-0000000bb001', '00000000-0000-4000-8000-0000000bd001', 'PUBLISHED', 'كشري تجريبي', 'Preview koshari', 'عدس وأرز ومكرونة', 'Lentils, rice and pasta', 'علبة كبيرة', 'عدس، أرز، مكرونة، صلصة', 'CONTAINS', '{GLUTEN}', 7500, now()),
  ('00000000-0000-4000-8000-0000000bb002', '00000000-0000-4000-8000-0000000bd001', 'PUBLISHED', 'محشي تجريبي', 'Preview mahshi', 'محشي كرنب وورق عنب', 'Stuffed cabbage and vine leaves', 'طبق لشخصين', 'أرز، كرنب، ورق عنب', 'NONE_DECLARED', '{}', 18000, now()),
  ('00000000-0000-4000-8000-0000000bb003', '00000000-0000-4000-8000-0000000bd002', 'PUBLISHED', 'ملوخية تجريبية', 'Preview molokhia', 'ملوخية بالفراخ', 'Molokhia with chicken', 'طبق لشخص', 'ملوخية، فراخ، توم', 'UNKNOWN', '{}', 12025, now());
INSERT INTO meal_revision (meal_id, revision, snapshot, price_minor)
SELECT id, 1, jsonb_build_object('titleAr', title_ar, 'titleEn', title_en, 'allergenDeclaration', allergen_declaration,
       'allergenCodes', to_jsonb(allergen_codes), 'priceMinor', price_minor, 'currency', 'EGP'), price_minor
FROM meal WHERE id IN ('00000000-0000-4000-8000-0000000bb001', '00000000-0000-4000-8000-0000000bb002', '00000000-0000-4000-8000-0000000bb003');

-- One open slot per kitchen at 13:00 Cairo time on each of the next three days, ten portions per meal.
INSERT INTO cook_slot (id, kitchen_id, local_date, starts_at, ends_at, lead_minutes, cutoff_at, max_portions, state)
SELECT gen_random_uuid(), k.id, d::date, (d::date + time '13:00') AT TIME ZONE 'Africa/Cairo',
       (d::date + time '14:00') AT TIME ZONE 'Africa/Cairo', 120, (d::date + time '11:00') AT TIME ZONE 'Africa/Cairo', 20, 'OPEN'
FROM (VALUES ('00000000-0000-4000-8000-0000000bd001'::uuid), ('00000000-0000-4000-8000-0000000bd002'::uuid)) AS k(id),
     generate_series((now() AT TIME ZONE 'Africa/Cairo')::date + 1, (now() AT TIME ZONE 'Africa/Cairo')::date + 3, interval '1 day') AS d;
INSERT INTO meal_slot (id, cook_slot_id, meal_id, capacity)
SELECT gen_random_uuid(), cs.id, m.id, 10 FROM cook_slot cs JOIN meal m ON m.kitchen_id = cs.kitchen_id
WHERE m.id IN ('00000000-0000-4000-8000-0000000bb001', '00000000-0000-4000-8000-0000000bb002', '00000000-0000-4000-8000-0000000bb003');
-- Simulated deposits (D16): the first kitchen takes a 50% deposit to a clearly fake recipient. The recipient is marked approved by the
-- seeded reviewer row; real approval needs staff MFA and is covered by the integration tests. Nothing is ever transferred in the preview:
-- the customer "reports" a transfer, the kitchen "reports" receipt, and the screens say so. Cash on receipt stays available for any total.
INSERT INTO kitchen_payment_profile (kitchen_id, deposit_bps, cod_max_total_minor, recipient_method, recipient_identifier, recipient_name,
                                     recipient_status, approved_by, approved_at)
VALUES ('00000000-0000-4000-8000-0000000bd001', 5000, NULL, 'INSTAPAY', 'demo-kitchen@instapay.invalid', 'Demo kitchen (not a real account)',
        'APPROVED', '00000000-0000-4000-8000-0000000b00e1', now())
ON CONFLICT (kitchen_id) DO NOTHING;
COMMIT;
