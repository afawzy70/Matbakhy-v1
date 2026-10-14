-- params: title
-- One kitchen and one slot per basket: a second open slot (the next day) for the kitchen of the meal, with the same meals.
WITH s AS (SELECT * FROM cook_slot WHERE kitchen_id = (SELECT kitchen_id FROM meal WHERE title_ar = :'title') AND state = 'OPEN'
           ORDER BY starts_at LIMIT 1),
     n AS (INSERT INTO cook_slot (id, kitchen_id, local_date, starts_at, ends_at, lead_minutes, cutoff_at, max_portions, state)
           SELECT gen_random_uuid(), kitchen_id, local_date + 1, starts_at + interval '1 day', ends_at + interval '1 day', lead_minutes,
                  cutoff_at + interval '1 day', max_portions, 'OPEN' FROM s RETURNING id)
INSERT INTO meal_slot (id, cook_slot_id, meal_id, capacity)
  SELECT gen_random_uuid(), n.id, ms.meal_id, ms.capacity FROM n, meal_slot ms JOIN s ON ms.cook_slot_id = s.id;
