-- params: title
-- MB-054: which anonymous activity kinds were counted for the meal.
SELECT string_agg(DISTINCT a.kind, ',' ORDER BY a.kind) FROM activity_daily a JOIN meal m ON m.id = a.subject_id WHERE m.title_ar = :'title';
