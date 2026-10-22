-- params: subject
-- Total and unit price of the customer's latest order, as "total|unit".
SELECT o.total_minor || '|' || i.unit_price_minor FROM customer_order o JOIN order_item i ON i.order_id = o.id JOIN app_user u ON u.id = o.customer_id
WHERE u.subject = :'subject' ORDER BY o.created_at DESC LIMIT 1;
