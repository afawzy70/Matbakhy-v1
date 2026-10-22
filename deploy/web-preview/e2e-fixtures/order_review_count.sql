-- params: number
SELECT count(*) FROM review r JOIN customer_order o ON o.id = r.order_id WHERE o.public_number = :'number';
