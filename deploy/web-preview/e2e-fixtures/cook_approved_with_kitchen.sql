-- params: cook_subject
-- Synthetic approval of the cook's application and kitchen (the staff review needs MFA, which the test identity provider does
-- not offer; the review workflow has its own tests). Fixed synthetic ids of the browser suite; run on an empty test database.
INSERT INTO app_user (id, issuer, subject, display_name) VALUES ('00000000-0000-4000-8000-0000000000e1', 'e2e-setup', 'e2e-reviewer', 'E2E reviewer');
INSERT INTO cook_application (id, user_id, state, public_name, bio, contact_phone, private_location, pickup_enabled, zone_ids, current_revision)
  SELECT '00000000-0000-4000-8000-0000000000f1', id, 'APPROVED', 'مطبخ تجريبي', 'أكل بيتي تجريبي', '+201001234567', 'Synthetic location', true,
         '{00000000-0000-4000-8000-00000000a001}', 1
  FROM app_user WHERE subject = :'cook_subject';
INSERT INTO cook_application_revision (application_id, revision, public_name, bio, contact_phone, private_location, pickup_enabled, zone_ids,
                                       submitted_at, decision, decision_reason, reviewer_id, decided_at)
  VALUES ('00000000-0000-4000-8000-0000000000f1', 1, 'مطبخ تجريبي', 'أكل بيتي تجريبي', '+201001234567', 'Synthetic location', true,
          '{00000000-0000-4000-8000-00000000a001}', now(), 'APPROVED', 'e2e synthetic approval', '00000000-0000-4000-8000-0000000000e1', now());
INSERT INTO kitchen (id, owner_id, application_id, approved_revision, public_slug, display_name, description, pickup_enabled, contact_phone, private_location)
  SELECT '00000000-0000-4000-8000-0000000000d1', id, '00000000-0000-4000-8000-0000000000f1', 1, 'e2e-kitchen', 'مطبخ تجريبي', 'أكل بيتي تجريبي', true,
         '+201001234567', 'Synthetic location' FROM app_user WHERE subject = :'cook_subject';
INSERT INTO kitchen_zone (kitchen_id, zone_id) VALUES ('00000000-0000-4000-8000-0000000000d1', '00000000-0000-4000-8000-00000000a001');
