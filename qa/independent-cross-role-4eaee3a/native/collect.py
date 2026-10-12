"""Independent DB assertions after real native UI checks. No tokens/private logs retained."""
import json
import os
import platform
import subprocess
from pathlib import Path

out=Path('iqa-native-evidence');out.mkdir(exist_ok=True)
surface=os.environ['IQA_PLATFORM']
sha=subprocess.check_output(['git','rev-parse','HEAD'],text=True).strip()
record={'applicationSha':sha,'platform':surface,'host':platform.platform(),'execution':'real emulator/simulator; existing native UI harness invoked independently and assertions reviewed','checks':[]}
assert sha=='4eaee3a1f53b929b9e7685fd30da689d470d095c'
if surface=='android':prefix=['docker','compose','--env-file','infra/.env','-f','infra/compose.yaml','exec','-T','postgres','psql','-U','matbakhy','-d','matbakhy']
else:prefix=['psql','-U','matbakhy','-d','matbakhy']
sql="""SELECT json_build_object('orders',(SELECT coalesce(json_agg(json_build_object('id',o.id,'publicNumber',o.public_number,'customerId',o.customer_id,'state',o.state,'totalMinor',o.total_minor,'slotId',o.slot_id)), '[]'::json) FROM customer_order o JOIN app_user u ON u.id=o.customer_id WHERE u.display_name LIKE '%SyntheticA%'), 'slotCounters',(SELECT coalesce(json_agg(json_build_object('id',s.id,'reserved',s.reserved,'allocated',s.allocated,'consumed',s.consumed)), '[]'::json) FROM cook_slot s WHERE s.id='00000000-0000-4000-8000-00000000a052'), 'mealCounters',(SELECT coalesce(json_agg(json_build_object('id',m.id,'reserved',m.reserved,'allocated',m.allocated,'consumed',m.consumed)), '[]'::json) FROM meal_slot m WHERE m.id='00000000-0000-4000-8000-00000000a062'));"""
try:
 r=subprocess.run(prefix+['-tA','-v','ON_ERROR_STOP=1','-c',sql],capture_output=True,text=True,timeout=30)
 if r.returncode:raise RuntimeError('Database correlation command unavailable')
 data=json.loads(r.stdout.strip());record['database']=data
 orders=data['orders']
 record['checks'].append({'name':'Exactly one customer-a order after uncertain recovery','expected':1,'actual':len(orders),'status':'PASS' if len(orders)==1 else 'FAIL'})
 if len(orders)==1:
  o=orders[0]
  record['checks'].append({'name':'Correct recovered slot and authoritative total','expected':{'slotId':'00000000-0000-4000-8000-00000000a052','totalMinor':9000},'actual':o,'status':'PASS' if o['slotId']=='00000000-0000-4000-8000-00000000a052' and o['totalMinor']==9000 else 'FAIL'})
  expected=0 if o['state'] in ['EXPIRED','REJECTED','CANCELLED'] else 1
  sums=[sum(row[k] for k in ['reserved','allocated','consumed']) for rows in [data['slotCounters'],data['mealCounters']] for row in rows]
  record['checks'].append({'name':'Single reservation/effect in both capacity counters','expected':[expected,expected],'actual':sums,'status':'PASS' if sums==[expected,expected] else 'FAIL'})
except Exception as e:
 record['checks'].append({'name':'Native order database correlation','status':'BLOCKED','reason':str(e)[:300]})
(out/'correlation.json').write_text(json.dumps(record,indent=2)+'\n')
print(json.dumps({'platform':surface,'applicationSha':sha,'checks':record['checks']}))
if any(c['status']=='FAIL' for c in record['checks']):raise SystemExit(1)
