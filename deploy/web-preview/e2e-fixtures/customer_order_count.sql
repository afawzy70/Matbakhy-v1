-- params: subject
SELECT count(*) FROM customer_order o JOIN app_user u ON u.id = o.customer_id WHERE u.subject = :'subject';
