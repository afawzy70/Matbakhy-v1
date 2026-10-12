-- MB-039 / TC-057: reconciliation run after every restore (and usable on a live database). Each row is one check and
-- its number of violations; a restored backup is accepted only when every count is 0. Read-only.
SELECT check_name, violations FROM (
    -- Stock: each meal slot's counters equal the allocations held against it, stage by stage.
    SELECT 1 AS ord, 'meal_slot_counters_match_allocations' AS check_name, count(*) AS violations
    FROM meal_slot ms
    LEFT JOIN LATERAL (
        SELECT coalesce(sum(quantity) FILTER (WHERE state = 'RESERVED'), 0)  AS reserved,
               coalesce(sum(quantity) FILTER (WHERE state = 'ALLOCATED'), 0) AS allocated,
               coalesce(sum(quantity) FILTER (WHERE state = 'CONSUMED'), 0)  AS consumed
        FROM stock_allocation sa WHERE sa.meal_slot_id = ms.id) a ON true
    WHERE (ms.reserved, ms.allocated, ms.consumed) IS DISTINCT FROM (a.reserved::int, a.allocated::int, a.consumed::int)
    UNION ALL
    -- Stock: each cook slot's counters equal the sum of its meal slots.
    SELECT 2, 'cook_slot_counters_match_meal_slots', count(*)
    FROM cook_slot cs
    LEFT JOIN LATERAL (
        SELECT coalesce(sum(reserved), 0) AS reserved, coalesce(sum(allocated), 0) AS allocated, coalesce(sum(consumed), 0) AS consumed
        FROM meal_slot ms WHERE ms.cook_slot_id = cs.id) m ON true
    WHERE (cs.reserved, cs.allocated, cs.consumed) IS DISTINCT FROM (m.reserved::int, m.allocated::int, m.consumed::int)
    UNION ALL
    -- Stock: terminal orders hold no live reservation or allocation.
    SELECT 3, 'terminal_orders_hold_no_live_stock', count(*)
    FROM stock_allocation sa JOIN customer_order o ON o.id = sa.order_id
    WHERE o.state IN ('REJECTED', 'EXPIRED', 'CANCELLED') AND sa.state IN ('RESERVED', 'ALLOCATED')
    UNION ALL
    -- Orders: every order has items whose lines add up to its subtotal.
    SELECT 4, 'order_items_match_subtotal', count(*)
    FROM customer_order o
    LEFT JOIN LATERAL (SELECT coalesce(sum(line_total_minor), 0) AS lines, count(*) AS n FROM order_item i WHERE i.order_id = o.id) i ON true
    WHERE i.n = 0 OR i.lines <> o.subtotal_minor
    UNION ALL
    -- Orders: the latest recorded event matches the current state.
    SELECT 5, 'order_state_matches_latest_event', count(*)
    FROM customer_order o
    LEFT JOIN LATERAL (SELECT to_state FROM order_event e WHERE e.order_id = o.id ORDER BY sequence DESC LIMIT 1) e ON true
    WHERE e.to_state IS DISTINCT FROM o.state
    UNION ALL
    -- Cash: exactly one cash obligation per order, for the order total.
    SELECT 6, 'one_cash_obligation_per_order', count(*)
    FROM customer_order o
    LEFT JOIN LATERAL (SELECT count(*) AS n, min(amount_minor) AS amount FROM cash_obligation c WHERE c.order_id = o.id) c ON true
    WHERE c.n <> 1 OR c.amount <> o.total_minor
    UNION ALL
    -- Ledger: every group has entries and its debits equal its credits.
    SELECT 7, 'ledger_groups_balanced', count(*)
    FROM ledger_group g
    LEFT JOIN LATERAL (
        SELECT count(*) AS n,
               coalesce(sum(amount_minor) FILTER (WHERE direction = 'DEBIT'), 0)  AS debits,
               coalesce(sum(amount_minor) FILTER (WHERE direction = 'CREDIT'), 0) AS credits
        FROM ledger_entry e WHERE e.group_id = g.id) e ON true
    WHERE e.n = 0 OR e.debits <> e.credits
    UNION ALL
    -- Ledger: commission is posted only for completed orders.
    SELECT 8, 'ledger_only_for_completed_orders', count(*)
    FROM ledger_group g JOIN customer_order o ON o.id = g.order_id
    WHERE g.kind = 'COMMISSION' AND o.state <> 'COMPLETED'
    UNION ALL
    -- Schema: every applied migration succeeded.
    SELECT 9, 'migrations_all_successful', count(*)
    FROM flyway_schema_history WHERE NOT success
) checks
ORDER BY ord;
