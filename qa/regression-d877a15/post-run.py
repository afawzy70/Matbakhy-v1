"""Read-only final reconciliation on the dedicated, known IQA database. Fail zero/invalid scope."""
import json, subprocess
from datetime import datetime, timezone
from pathlib import Path

root = Path(__file__).resolve().parents[2]
env = Path('/workspace/qa-tools/regression-d877a15/stack/.env')
assert 'COMPOSE_PROJECT_NAME=matbakhy-regression-d877a15' in env.read_text()
prefix = ['docker','compose','--env-file',str(env),'-f',str(root/'infra/compose.yaml'),'-f','/workspace/qa-tools/regression-d877a15/stack/override.yaml','exec','-T','postgres','psql','-U','matbakhy','-d','matbakhy','-tA','-v','ON_ERROR_STOP=1','-c']
def db(sql):
    return json.loads(subprocess.check_output(prefix+[sql],text=True))
data = db("""SELECT json_build_object(
 'migrations',(SELECT json_agg(json_build_object('version',version,'success',success)) FROM flyway_schema_history),
 'orders',(SELECT json_agg(json_build_object('id',id,'number',public_number,'state',state,'kitchenId',kitchen_id,'slotId',slot_id,'subtotalMinor',subtotal_minor,'feeMinor',delivery_fee_minor,'taxMinor',tax_minor,'totalMinor',total_minor,'commissionMinor',commission_minor)) FROM customer_order),
 'duplicateRequests',(SELECT count(*) FROM (SELECT customer_id,client_request_id FROM customer_order GROUP BY customer_id,client_request_id HAVING count(*)>1)x),
 'ledgerGroups',(SELECT json_agg(json_build_object('groupId',group_id,'balance',balance)) FROM (SELECT group_id,sum(CASE WHEN direction='DEBIT' THEN amount_minor ELSE -amount_minor END) AS balance FROM ledger_entry GROUP BY group_id)x),
 'quantityPolicy',(SELECT max_item_quantity FROM commerce_policy ORDER BY version DESC LIMIT 1))""")
assert data['orders'] and len(data['orders']) >= 10, 'zero/incomplete journey order denominator'
checks = db("""SELECT json_build_object(
 'mealCounterMismatches',(SELECT count(*) FROM meal_slot m WHERE m.reserved<>(SELECT coalesce(sum(quantity),0) FROM stock_allocation a WHERE a.meal_slot_id=m.id AND a.state='RESERVED') OR m.allocated<>(SELECT coalesce(sum(quantity),0) FROM stock_allocation a WHERE a.meal_slot_id=m.id AND a.state='ALLOCATED') OR m.consumed<>(SELECT coalesce(sum(quantity),0) FROM stock_allocation a WHERE a.meal_slot_id=m.id AND a.state='CONSUMED')),
 'kitchenCounterMismatches',(SELECT count(*) FROM cook_slot s WHERE s.reserved<>(SELECT coalesce(sum(reserved),0) FROM meal_slot m WHERE m.cook_slot_id=s.id) OR s.allocated<>(SELECT coalesce(sum(allocated),0) FROM meal_slot m WHERE m.cook_slot_id=s.id) OR s.consumed<>(SELECT coalesce(sum(consumed),0) FROM meal_slot m WHERE m.cook_slot_id=s.id)),
 'moneyMismatches',(SELECT count(*) FROM customer_order o WHERE total_minor<>subtotal_minor+delivery_fee_minor+tax_minor OR subtotal_minor<>(SELECT sum(unit_price_minor*quantity) FROM order_item i WHERE i.order_id=o.id) OR commission_minor<>round(subtotal_minor::numeric*commission_bps_snapshot/10000)),
 'lineMoneyMismatches',(SELECT count(*) FROM order_item WHERE line_total_minor<>unit_price_minor*quantity),
 'missingCash',(SELECT count(*) FROM customer_order o WHERE (SELECT count(*) FROM cash_obligation c WHERE c.order_id=o.id)<>1),
 'missingOrDuplicatePlacedEvents',(SELECT count(*) FROM customer_order o WHERE (SELECT count(*) FROM outbox_event e WHERE e.aggregate_id=o.id AND e.event_type='ORDER_PLACED')<>1),
 'negativeCapacity',(SELECT count(*) FROM meal_slot WHERE reserved<0 OR allocated<0 OR consumed<0 OR reserved+allocated+consumed>capacity))""")
meta = {'applicationSha':'d877a1576cd942200fe0a53b5a19fee76b996025','testSuiteSha':subprocess.check_output(['git','rev-parse','HEAD'],cwd=root,text=True).strip(),'observedAt':datetime.now(timezone.utc).isoformat(),'environment':'matbakhy-regression-d877a15','scope':'all synthetic campaign orders; commission oracle applies to configured tax0,1000bps HALF_UP excluding delivery'}
out = root/'verification/runs/20261013-IQA-d877a15/evidence'
(out/'final-database-invariants.json').write_text(json.dumps({**meta,**data},indent=2)+'\n')
(out/'reconciled-business-invariants.json').write_text(json.dumps({**meta,'checks':checks},indent=2)+'\n')
assert all(v == 0 for v in checks.values()), checks
assert data['duplicateRequests'] == 0 and data['quantityPolicy'] == 10
assert data['ledgerGroups'] and all(v['balance'] == 0 for v in data['ledgerGroups'])
assert data['migrations'] and all(v['success'] for v in data['migrations'])
print(json.dumps({'status':'PASS','orders':len(data['orders']),'checks':checks}))
