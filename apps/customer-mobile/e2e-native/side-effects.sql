-- MB-032 / TC-049: business side effects of the one order the synthetic customer (psql variable :phone) placed.
-- Expected after a kill during submit and recovery: 1 order | 1 stock allocation | 1 portion reserved on the meal slot
-- | 1 portion reserved on the cook slot | 1 cash obligation | 1 ORDER_PLACED outbox event | 1 order event.
WITH o AS (
    SELECT o.id FROM customer_order o JOIN app_user u ON u.id = o.customer_id WHERE u.phone_e164 = :'phone'
), a AS (
    SELECT sa.* FROM stock_allocation sa WHERE sa.order_id IN (SELECT id FROM o)
)
SELECT (SELECT count(*) FROM o)
    || '|' || (SELECT count(*) FROM a)
    || '|' || (SELECT coalesce(sum(ms.reserved), 0) FROM meal_slot ms WHERE ms.id IN (SELECT meal_slot_id FROM a))
    || '|' || (SELECT coalesce(sum(cs.reserved), 0) FROM cook_slot cs WHERE cs.id IN (SELECT ms.cook_slot_id FROM meal_slot ms WHERE ms.id IN (SELECT meal_slot_id FROM a)))
    || '|' || (SELECT count(*) FROM cash_obligation c WHERE c.order_id IN (SELECT id FROM o))
    || '|' || (SELECT count(*) FROM outbox_event e WHERE e.event_type = 'ORDER_PLACED' AND e.aggregate_id IN (SELECT id FROM o))
    || '|' || (SELECT count(*) FROM order_event ev WHERE ev.order_id IN (SELECT id FROM o));
