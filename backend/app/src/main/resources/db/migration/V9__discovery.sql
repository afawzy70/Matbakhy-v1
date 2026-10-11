-- MB-016: discovery. Meal category (fixed proposed list) and Arabic-aware search normalization.

ALTER TABLE meal ADD COLUMN category text CHECK (category IN ('MAIN_DISHES', 'SIDES_SALADS', 'SOUPS', 'BAKED_GOODS', 'DESSERTS',
                                                             'BREAKFAST', 'READY_TO_COOK'));

-- Lower-case, strip Arabic diacritics and tatweel, unify alef/yaa/taa marbuta forms so "كشرى" finds "كُشَري".
CREATE FUNCTION matbakhy_search_normalize(value text) RETURNS text LANGUAGE sql IMMUTABLE PARALLEL SAFE AS $$
    SELECT translate(lower(coalesce(value, '')),
                     'أإآٱىة' || 'ًٌٍَُِّْـ',
                     'اااايه')
$$;

CREATE INDEX meal_discovery_idx ON meal (status, published_at DESC, id) WHERE status = 'PUBLISHED';
CREATE INDEX meal_price_idx ON meal (price_minor, id) WHERE status = 'PUBLISHED';
CREATE INDEX meal_slot_meal_idx ON meal_slot (meal_id);
CREATE INDEX kitchen_zone_zone_idx ON kitchen_zone (zone_id, kitchen_id);
