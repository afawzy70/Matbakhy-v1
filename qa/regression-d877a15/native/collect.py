"""Independent DB assertions after real native UI checks. No tokens/private logs retained."""
import json
import os
import platform
import hashlib,plistlib,shutil
import subprocess
from pathlib import Path

out=Path('iqa-native-evidence');out.mkdir(exist_ok=True)
surface=os.environ['IQA_PLATFORM']
sha=subprocess.check_output(['git','rev-parse','HEAD'],text=True).strip()
record={'applicationSha':sha,'platform':surface,'host':platform.platform(),'execution':'real emulator/simulator; existing native UI harness invoked independently and assertions reviewed','checks':[]}
assert sha=='d877a1576cd942200fe0a53b5a19fee76b996025'
if surface=='android':prefix=['docker','compose','--env-file','infra/.env','-f','infra/compose.yaml','exec','-T','postgres','psql','-U','matbakhy','-d','matbakhy']
else:
 pg=subprocess.check_output(['brew','--prefix','postgresql@16'],text=True).strip()
 prefix=[str(Path(pg)/'bin/psql'),'-h','localhost','-U','matbakhy','-d','matbakhy']
# Metadata is observational only; no build/UI PASS from inspection.
try:
 if surface=='android':
  shots=list((Path(os.environ['RUNNER_TEMP'])/'mb032-android').glob('*iqa-arabic-account.png'))
  if shots:shutil.copy(shots[0],out/'android-arabic-account.png')
  record['accountScreenshotPreserved']=(out/'android-arabic-account.png').exists()
  apk=Path('apps/customer-mobile/android/app/build/outputs/apk/release/app-release.apk')
  record['artifact']={'file':str(apk),'sha256':hashlib.sha256(apk.read_bytes()).hexdigest()}
  record['device']={'kind':'emulator','os':subprocess.check_output(['adb','shell','getprop','ro.build.version.release'],text=True).strip(),'sdk':subprocess.check_output(['adb','shell','getprop','ro.build.version.sdk'],text=True).strip()}
  shots=list((Path(os.environ['RUNNER_TEMP'])/'mb032-android').glob('*iqa-arabic-account.png'))
  if shots:shutil.copy(shots[0],out/'android-arabic-account.png')
 else:
  app=next(Path('apps/customer-mobile/ios/build-release/Build/Products').glob('*/*.app'))
  info=plistlib.loads((app/'Info.plist').read_bytes());exe=app/info['CFBundleExecutable']
  record['artifact']={'bundleId':info['CFBundleIdentifier'],'version':info['CFBundleShortVersionString'],'sha256':hashlib.sha256(exe.read_bytes()).hexdigest(),'signing':'ad-hoc simulator; no team/store certificate'}
  devices=json.loads(subprocess.check_output(['xcrun','simctl','list','devices','booted','-j'],text=True))
  record['device']=[{'kind':'simulator','runtime':runtime,'name':v['name']} for runtime,vs in devices['devices'].items() for v in vs if v['state']=='Booted']
  result=Path(os.environ['RUNNER_TEMP'])/'mb032-testI_IndependentLocaleLayout.xcresult'
  exported=Path(os.environ['RUNNER_TEMP'])/'iqa-account-attachments'
  if result.exists():
   r=subprocess.run(['xcrun','xcresulttool','export','attachments','--path',str(result),'--output-path',str(exported)],capture_output=True,text=True,timeout=30)
   if r.returncode==0:
    manifests=list(exported.rglob('manifest.json'))
    def walk(v):
     if isinstance(v,dict):
      if any(isinstance(x,str) and ('iqa-arabic-account' in x or 'iqa-english-account' in x) for x in v.values()):
       for x in v.values():
        if isinstance(x,str) and Path(x).suffix.lower() in ['.png','.jpg','.jpeg']:
         paths=list(exported.rglob(Path(x).name))
         if len(paths)==1:
          name='ios-arabic-account' if any(isinstance(y,str) and 'iqa-arabic-account' in y for y in v.values()) else 'ios-english-account'
          shutil.copy(paths[0],out/(name+Path(x).suffix))
      for x in v.values():walk(x)
     elif isinstance(v,list):
      for x in v:walk(x)
    for m in manifests:walk(json.loads(m.read_text()))
   record['accountScreenshotPreserved']=bool(list(out.glob('ios-*account.*')))
except Exception:
 record['metadataGap']='artifact/device or selected safe attachment unavailable; not an execution PASS'
sql="""SELECT json_build_object('orders',(SELECT coalesce(json_agg(json_build_object('id',o.id,'publicNumber',o.public_number,'customerId',o.customer_id,'state',o.state,'totalMinor',o.total_minor,'slotId',o.slot_id,'kitchenId',o.kitchen_id,'items',(SELECT json_agg(json_build_object('mealId',i.meal_id,'quantity',i.quantity,'unitPriceMinor',i.unit_price_minor)) FROM order_item i WHERE i.order_id=o.id))), '[]'::json) FROM customer_order o JOIN app_user u ON u.id=o.customer_id WHERE u.display_name LIKE '%SyntheticA%'), 'slotCounters',(SELECT coalesce(json_agg(json_build_object('id',s.id,'reserved',s.reserved,'allocated',s.allocated,'consumed',s.consumed)), '[]'::json) FROM cook_slot s WHERE s.id='00000000-0000-4000-8000-00000000a052'), 'mealCounters',(SELECT coalesce(json_agg(json_build_object('id',m.id,'reserved',m.reserved,'allocated',m.allocated,'consumed',m.consumed)), '[]'::json) FROM meal_slot m WHERE m.id='00000000-0000-4000-8000-00000000a062'));"""
try:
 r=subprocess.run(prefix+['-tA','-v','ON_ERROR_STOP=1','-c',sql],capture_output=True,text=True,timeout=30)
 if r.returncode:raise RuntimeError('Database correlation command unavailable')
 data=json.loads(r.stdout.strip());record['database']=data
 proof_sql="SELECT json_build_object('ownCases',(SELECT coalesce(json_agg(json_build_object('reference',c.reference,'orderId',c.order_id,'state',c.state,'category',c.category)),'[]'::json) FROM support_case c JOIN app_user u ON u.id=c.customer_id WHERE u.display_name LIKE '%SyntheticA%'),'publicFixtureMessages',(SELECT count(*) FROM case_message m JOIN support_case c ON c.id=m.case_id WHERE c.reference='CS-DA877001' AND m.visibility='PUBLIC'),'cancelledDeliveryAllocations',(SELECT count(*) FROM stock_allocation a JOIN customer_order o ON o.id=a.order_id JOIN app_user u ON u.id=o.customer_id WHERE u.display_name LIKE '%SyntheticA%' AND o.fulfillment_mode='DELIVERY' AND o.state='CANCELLED' AND a.state='RELEASED'),'cancelledDeliveryEvents',(SELECT count(*) FROM order_event e JOIN customer_order o ON o.id=e.order_id JOIN app_user u ON u.id=o.customer_id WHERE u.display_name LIKE '%SyntheticA%' AND o.fulfillment_mode='DELIVERY' AND e.to_state='CANCELLED'))"
 proof=subprocess.check_output(prefix+['-tA','-v','ON_ERROR_STOP=1','-c',proof_sql],text=True,timeout=30)
 record['trustInvariants']=json.loads(proof.strip())
 orders=data['orders']
 pickup=[o for o in orders if o['slotId']=='00000000-0000-4000-8000-00000000a052']
 delivery=[o for o in orders if o['slotId']=='00000000-0000-4000-8000-00000000a051']
 if delivery:
  record['deliveryFinalState']=delivery[0]['state']
 record['checks'].append({'name':'Exactly one recovered pickup and one later delivery order','expected':[1,1], 'actual':[len(pickup),len(delivery)],'status':'PASS' if len(pickup)==len(delivery)==1 and len(orders)==2 else 'FAIL'})
 record['checks'].append({'name':'Stored pickup and delivery totals','expected':[9000,63000], 'actual':[o['totalMinor'] for o in pickup+delivery],'status':'PASS' if [o['totalMinor'] for o in pickup+delivery]==[9000,63000] else 'FAIL'})
except Exception as e:
 record['checks'].append({'name':'Native order database correlation','status':'BLOCKED','reason':str(e)[:300]})
for candidate in [Path('apps/customer-mobile/e2e-ios/iqa-native-evidence/transport.jsonl'),out/'transport.jsonl']:
 if candidate.exists():
  observations=[]
  for line in candidate.read_text().splitlines():
   row=json.loads(line);allowed={'at','method','path','status','bearerPresent','exp','iat','expired'}
   assert set(row)<=allowed
   if 'path' in row:assert row['path'].startswith('/api/v1/') and '?' not in row['path']
   observations.append(row)
  assert observations,'zero transport observations; not a logging PASS'
  (out/'transport.jsonl').write_text(''.join(json.dumps(row)+'\n' for row in observations))
  record['transportScope']='response-holding proxy phases only; no claim of later direct API traffic'
  break
(out/'correlation.json').write_text(json.dumps(record,indent=2)+'\n')
print(json.dumps({'platform':surface,'applicationSha':sha,'checks':record['checks']}))
if any(c['status']!='PASS' for c in record['checks']):raise SystemExit(1)
