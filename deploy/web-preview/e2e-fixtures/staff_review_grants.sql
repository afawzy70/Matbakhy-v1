-- params: subject
-- Synthetic staff grants for the operations journeys (granting is itself covered by the identity tests).
INSERT INTO user_grant (id, user_id, permission, grant_reason)
  SELECT gen_random_uuid(), id, p, 'e2e synthetic grant' FROM app_user,
         unnest(ARRAY['content.moderate', 'cook.review', 'support.case', 'order.ops', 'order.exception_cancel']) AS p
  WHERE subject = :'subject';
