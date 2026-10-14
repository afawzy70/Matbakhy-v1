-- params: case_id customer_subject
-- Staff messages on a case as test-only rows with a synthetic staff author (the staff reply UI has its own tests): one
-- STAFF_ONLY note that must never reach the customer and one PUBLIC reply.
INSERT INTO case_message (id, case_id, author_id, author_role, visibility, body, created_at)
  SELECT gen_random_uuid(), :'case_id', u.id, 'STAFF', v.visibility, v.body, now() + v.delay
  FROM (SELECT id FROM app_user WHERE subject <> :'customer_subject' ORDER BY created_at LIMIT 1) u,
       (VALUES ('STAFF_ONLY', 'E2E PRIVATE NOTE NEVER FOR CUSTOMER', interval '1 second'),
               ('PUBLIC', 'E2E PUBLIC REPLY FOR CUSTOMER', interval '2 seconds')) AS v(visibility, body, delay);
