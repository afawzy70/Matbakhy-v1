-- params: number
SELECT c.id FROM support_case c JOIN customer_order o ON o.id = c.order_id WHERE o.public_number = :'number' ORDER BY c.created_at DESC LIMIT 1;
