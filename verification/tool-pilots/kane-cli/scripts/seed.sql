-- QA-ONLY SYNTHETIC SEED for the Kane pilot (TC-K1..K3). Direct database setup, NOT a tested behaviour.
-- Never run outside the throwaway local stack. All names/phones are synthetic (+2010000xxxxx test range).
-- Creates: Kitchen A (A1 with description, A2), Kitchen B (B1), paused meal X1, plus open slots tomorrow (Africa/Cairo).
-- NOTE: the schema CHECK meal_publishable_ck forbids a PUBLISHED/PAUSED meal without description_ar, so a visible meal
-- with an absent description cannot be created; see the pilot report (D12 absence branch not reachable by data).
BEGIN;
CREATE TEMP TABLE _z AS SELECT id FROM service_zone WHERE code LIKE 'test-zone-%' AND active ORDER BY code LIMIT 1;

INSERT INTO app_user (id, issuer, subject, email_verified, display_name) VALUES
 ('90000000-0000-4000-8000-0000000000a1','qa-seed','qa-cook-a',true,'QA Cook A'),
 ('90000000-0000-4000-8000-0000000000b1','qa-seed','qa-cook-b',true,'QA Cook B'),
 ('90000000-0000-4000-8000-0000000000e1','qa-seed','qa-reviewer',true,'QA reviewer');

INSERT INTO cook_application (id,user_id,state,public_name,bio,contact_phone,private_location,pickup_enabled,zone_ids,current_revision)
 SELECT v.aid::uuid, v.uid::uuid,'APPROVED',v.pn,'QA synthetic kitchen','+201000000001','Synthetic location',true,ARRAY[(SELECT id FROM _z)],1
 FROM (VALUES ('90000000-0000-4000-8000-0000000000a2','90000000-0000-4000-8000-0000000000a1','QA Kitchen A'),
              ('90000000-0000-4000-8000-0000000000b2','90000000-0000-4000-8000-0000000000b1','QA Kitchen B')) v(aid,uid,pn);
INSERT INTO cook_application_revision (application_id,revision,public_name,bio,contact_phone,private_location,pickup_enabled,zone_ids,submitted_at,decision,decision_reason,reviewer_id,decided_at)
 SELECT a.id,1,a.public_name,a.bio,a.contact_phone,a.private_location,true,a.zone_ids,now(),'APPROVED','QA synthetic approval','90000000-0000-4000-8000-0000000000e1',now()
 FROM cook_application a WHERE a.id IN ('90000000-0000-4000-8000-0000000000a2','90000000-0000-4000-8000-0000000000b2');
INSERT INTO kitchen (id,owner_id,application_id,approved_revision,public_slug,display_name,description,pickup_enabled,contact_phone,private_location) VALUES
 ('90000000-0000-4000-8000-0000000000a3','90000000-0000-4000-8000-0000000000a1','90000000-0000-4000-8000-0000000000a2',1,'qa-kitchen-a','QA Kitchen A','Synthetic kitchen A',true,'+201000000001','Synthetic location'),
 ('90000000-0000-4000-8000-0000000000b3','90000000-0000-4000-8000-0000000000b1','90000000-0000-4000-8000-0000000000b2',1,'qa-kitchen-b','QA Kitchen B','Synthetic kitchen B',true,'+201000000002','Synthetic location');
INSERT INTO kitchen_zone (kitchen_id,zone_id) SELECT k,(SELECT id FROM _z) FROM (VALUES ('90000000-0000-4000-8000-0000000000a3'::uuid),('90000000-0000-4000-8000-0000000000b3'::uuid)) t(k);

INSERT INTO meal (id,kitchen_id,status,title_ar,title_en,description_ar,description_en,portion_ar,portion_en,ingredients_ar,ingredients_en,allergen_declaration,price_minor,published_at) VALUES
 ('90000000-0000-4000-8000-0000000001a1','90000000-0000-4000-8000-0000000000a3','PUBLISHED','وجبة أ١ تجريبية','QA Meal A1','وصف تجريبي لوجبة أ١ مع تفاصيل','QA description for meal A1 with details','طبق واحد','One plate','أرز، خضار','Rice, vegetables','NONE_DECLARED',12000,now()),
 ('90000000-0000-4000-8000-0000000001a2','90000000-0000-4000-8000-0000000000a3','PUBLISHED','وجبة أ٢ تجريبية','QA Meal A2','وصف تجريبي لوجبة أ٢ مع تفاصيل','QA description for meal A2 with details','طبق واحد','One plate','عدس، مكرونة','Lentils, pasta','NONE_DECLARED',8500,now()),
 ('90000000-0000-4000-8000-0000000001b1','90000000-0000-4000-8000-0000000000b3','PUBLISHED','وجبة ب١ تجريبية','QA Meal B1','وصف تجريبي لوجبة ب١ مع تفاصيل','QA description for meal B1 with details','طبق واحد','One plate','دجاج، أرز','Chicken, rice','NONE_DECLARED',9500,now()),
 ('90000000-0000-4000-8000-0000000001c1','90000000-0000-4000-8000-0000000000a3','PAUSED','وجبة موقوفة تجريبية','QA Paused X1','وصف تجريبي لوجبة موقوفة','QA description for paused meal X1','طبق واحد','One plate','فول','Beans','NONE_DECLARED',7000,now());
INSERT INTO meal_revision (meal_id,revision,snapshot,price_minor)
 SELECT id,1,to_jsonb(meal),price_minor FROM meal WHERE id::text LIKE '90000000-0000-4000-8000-0000000001%';

-- Slots: tomorrow 13:00-15:00 and 18:00-20:00 Cairo (kitchen A); 13:00-15:00 (kitchen B); 60-minute lead.
INSERT INTO cook_slot (id,kitchen_id,local_date,starts_at,ends_at,lead_minutes,cutoff_at,max_portions,state)
 SELECT v.id::uuid,v.k::uuid,d,s,s+interval '2 hours',60,s-interval '60 minutes',10,'OPEN'
 FROM (VALUES ('90000000-0000-4000-8000-0000000002a1','90000000-0000-4000-8000-0000000000a3',time '13:00'),
              ('90000000-0000-4000-8000-0000000002a2','90000000-0000-4000-8000-0000000000a3',time '18:00'),
              ('90000000-0000-4000-8000-0000000002b1','90000000-0000-4000-8000-0000000000b3',time '13:00')) v(id,k,t),
      LATERAL (SELECT ((now() AT TIME ZONE 'Africa/Cairo')::date + 1) AS d) dd,
      LATERAL (SELECT ((dd.d + v.t) AT TIME ZONE 'Africa/Cairo') AS s) ss;
INSERT INTO meal_slot (id,cook_slot_id,meal_id,capacity) VALUES
 (gen_random_uuid(),'90000000-0000-4000-8000-0000000002a1','90000000-0000-4000-8000-0000000001a1',10),
 (gen_random_uuid(),'90000000-0000-4000-8000-0000000002a2','90000000-0000-4000-8000-0000000001a1',10),
 (gen_random_uuid(),'90000000-0000-4000-8000-0000000002a1','90000000-0000-4000-8000-0000000001a2',10),
 (gen_random_uuid(),'90000000-0000-4000-8000-0000000002a2','90000000-0000-4000-8000-0000000001a2',10),
 (gen_random_uuid(),'90000000-0000-4000-8000-0000000002b1','90000000-0000-4000-8000-0000000001b1',10),
 (gen_random_uuid(),'90000000-0000-4000-8000-0000000002a1','90000000-0000-4000-8000-0000000001c1',10);
COMMIT;
