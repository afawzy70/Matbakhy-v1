-- D13 native checks (CI only, local stack): the kitchen edit's database effect for a price change (MealService.update):
-- the meal row locked for update, a new price, revision and version, and an appended immutable revision. Test-only.
WITH m AS (SELECT id, revision FROM meal WHERE id = :'meal' FOR UPDATE),
     u AS (UPDATE meal SET price_minor = :price, revision = m.revision + 1, version = version + 1, updated_at = now()
           FROM m WHERE meal.id = m.id RETURNING meal.id, meal.revision)
INSERT INTO meal_revision (meal_id, revision, snapshot, price_minor)
SELECT r.meal_id, u.revision, jsonb_set(r.snapshot, '{priceMinor}', to_jsonb(:price::bigint)), :price
FROM u JOIN meal_revision r ON r.meal_id = u.id AND r.revision = u.revision - 1;
