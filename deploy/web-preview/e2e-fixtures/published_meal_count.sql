-- params: title
SELECT count(*) FROM meal WHERE title_ar = :'title' AND status = 'PUBLISHED' AND moderation_state = 'VISIBLE';
