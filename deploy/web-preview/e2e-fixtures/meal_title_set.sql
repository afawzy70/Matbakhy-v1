-- params: from_title to_title
-- Operations-layout journey: one meal gets a very long synthetic title to exercise long values in lists and tables.
UPDATE meal SET title_ar = :'to_title' WHERE id = (SELECT id FROM meal WHERE title_ar = :'from_title');
