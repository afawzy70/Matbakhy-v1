-- params: title
SELECT public_slug FROM kitchen k JOIN meal m ON m.kitchen_id = k.id WHERE m.title_ar = :'title';
