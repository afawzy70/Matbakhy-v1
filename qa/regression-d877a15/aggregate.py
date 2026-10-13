"""Aggregate reviewed executed boundaries. Never import historical PASS or infer native coverage."""
import csv,json
from collections import Counter
from pathlib import Path

root=Path(__file__).resolve().parents[2];run=root/'verification/runs/20261013-IQA-d877a15'
cases={c['id']:c for c in json.loads((root/'docs/quality/test-cases/cases.json').read_text())}
sha='d877a1576cd942200fe0a53b5a19fee76b996025';rows=[]
def add(id,variation,platform,locale,status,suite,time,evidence,actual,role='customer',viewport='1440x1000',mode='FULL REGRESSION',eligible=True,defect=None):
 assert id in cases and status in {'PASS','FAIL','BLOCKED','NOT_RUN'}
 rows.append(dict(caseId=id,variation=variation,applicationSha=sha,testSuiteSha=suite,runTimestamp=time,platform=platform,locale=locale,viewport=viewport,actor=role,runType=mode,status=status,eligible=eligible,actualResult=actual,evidence=evidence,defectOrBlocker=defect,requirement=cases[id]['requirementReferences']))
raw=json.loads((run/'evidence/results.json').read_text())['results'];assert len(raw)==47
for r in raw:
 assert r['applicationSha']==sha and r['checks']
 if r['id']=='W-A11Y':
  for a in json.loads((run/'evidence/a11y.json').read_text()):
   assert not a['violations'] and r['status']=='PASS'
   add(r['id'],a['role']+'/'+a['route']+'/'+str(a['width']), 'Chromium web',a['lang'],'PASS',r['testSuiteSha'],r['startedAt']+'..'+r['finishedAt'],['evidence/a11y.json','evidence/results.json'],'Executed axe serious/critical, overflow, focus/table assertions; incomplete manual checks and screen reader NOT closed.',a['role'],str(a['width'])+'x1000')
 elif r['id']=='W-MONEY-REFLOW':
  for v in json.loads((run/'evidence/money-reflow.json').read_text()):
   add(r['id'],v['role']+'/financial readability','Chromium web',v['locale'],v['status'],v['testSuiteSha'],v['observedAt'],['evidence/'+e for e in v['evidence']],'Numeric-token geometry and actual screenshots inspected; no backend calculation failure.',v['role'],str(v['viewport']['width'])+'x1000',defect='MBQA-006' if v['status']=='FAIL' else None)
 else:
  for locale in (['en','ar'] if r['id'] in ['C-BROWSE','P-HISTORY'] else ['en']):
   add(r['id'],'baseline','Chromium web/API/database',locale,r['status'],r['testSuiteSha'],r['startedAt']+'..'+r['finishedAt'],['evidence/results.json']+['evidence/'+e for e in r['evidence']],'Verified '+str(len(r['checks']))+' assertions: '+'; '.join(c['name'] for c in r['checks']),r['actor'])
for folder,file in [('targeted-support-layout','variations.json'),('targeted-additional-attempt1','history-variations.json'),('targeted-anonymous','variations.json')]:
 parent=json.loads((run/folder/'results.json').read_text())['results']
 for v in json.loads((run/folder/file).read_text()):
  p=next(r for r in parent if r['id']==v['caseId']);assert p['status']=='PASS' and all(c['status']=='PASS' for c in p['checks'])
  source=folder if folder=='targeted-support-layout' else 'evidence'
  add(v['caseId'],v['variation'],'Chromium web',v['locale'],v['status'],v['testSuiteSha'],v['observedAt'],[folder+'/'+file]+[source+'/'+e for e in v['evidence']],'Actual scoped layout/description/thread assertions verified.',viewport=str(v['viewport']['width'])+'x1000',mode='TARGETED')
for folder,id,variation in [('targeted-additional-attempt1','C-INBOX-SHARE-REORDER','actual UI current-price reorder quote'),('targeted-additional','C-PROFILE','historical contact/address snapshot invariant')]:
 r=next(x for x in json.loads((run/folder/'results.json').read_text())['results'] if x['id']==id);assert r['status']=='PASS'
 add(id,variation,'Chromium web/API/database','en','PASS',r['testSuiteSha'],r['finishedAt'],[folder+'/results.json'],'All scoped assertions verified; additional synthetic fixtures legally cancelled/restored.',mode='TARGETED')
scan=json.loads((run/'secret-scan.json').read_text());assert scan['commitsScanned']>0 and scan['status']=='PASS'
add('M-SECRETS','nonzero history and sanitized committable evidence','Git/Gitleaks','n/a','PASS',scan['testSuiteSha'],scan['observedAt'],['secret-scan.json'],'History scanned '+str(scan['commitsScanned'])+' commits; extracted prearchive and current committable-tree scopes explicitly recorded.',role='QA',viewport='n/a',mode='MANUAL')
for id,status,reason,eligible in [('M-SCREENREADER','BLOCKED','No independently operable screen reader; MB-029/MB-030 gate stays open.',True),('M-SMS','BLOCKED','Development SMS adapter has no deliverable sandbox OTP; verified-phone seed is not verification execution.',True),('M-DEPLOYED','NOT_RUN','No authorized deployed environment; MB-040/runtime roles/log sinks/alerts unfinished.',False),('P-PRECOMMIT','NOT_RUN','Approved D13 old/new attribution and explicit customer reconfirmation client UX unfinished.',False),('P-REFRESH-FAIL','NOT_RUN','Approved repeated repricing/failed-refresh client UX unfinished.',False)]:
 add(id,'delivery/tool prerequisite','web','n/a',status,scan['testSuiteSha'],scan['observedAt'],['report.md','../../../docs/quality/test-cases/coverage-matrix.md'],reason,viewport='n/a',mode='GAP',eligible=eligible,defect='environment/unfinished')
add('C-BROWSE','unmocked absent card description','Chromium web','en/ar','BLOCKED',scan['testSuiteSha'],scan['observedAt'],['report.md'],'D12 optional preview absence has no legal published no-description fixture under current BR-011/database constraint; no fabricated live response PASS.',viewport='n/a',mode='GAP',defect='D12 fixture/requirement boundary')
native=json.loads((run/'native-results.json').read_text())
for r in native:
 assert r['applicationSha']==sha and r['caseId'] in cases
 rows.append(r)
keys=[(r['caseId'],r['variation'],r['platform'],r['locale'],r['viewport']) for r in rows];assert len(set(keys))==len(keys),'duplicated variation denominator'
assert all(r['testSuiteSha'] and r['runTimestamp'] and r['actualResult'] for r in rows)
(run/'results.json').write_text(json.dumps({'applicationSha':sha,'definitionCount':len(cases),'counting':'one reviewed concrete variation; historical reruns not added; narrower native PASS boundaries have separate gaps','results':rows},indent=2,ensure_ascii=False)+'\n')
with (run/'results.csv').open('w') as f:
 writer=csv.DictWriter(f,fieldnames=rows[0].keys());writer.writeheader()
 for r in rows:writer.writerow({**r,'evidence':'; '.join(r['evidence'])})
summary={'applicationSha':sha,'definedCases':len(cases),'definedNativeCases':sum(c['group']=='native' for c in cases.values()),'concreteVariationRows':len(rows),'executedVariations':sum(r['status'] in ['PASS','FAIL'] for r in rows),'allRows':dict(Counter(r['status'] for r in rows)),'eligibleRows':dict(Counter(r['status'] for r in rows if r['eligible'])),'byPlatform':{p:dict(Counter(r['status'] for r in rows if r['platform']==p)) for p in sorted({r['platform'] for r in rows})},'freshFullWebGroups':{s:sum(r['status']==s for r in raw) for s in ['PASS','FAIL','BLOCKED','NOT_RUN']},'freshFullWebAssertions':sum(len(r['checks']) for r in raw),'note':'Latest full native attempt is not passed; original successful same-application campaign executions are explicitly sourced, with later tooling blockers retained. No release gate approved.'}
(run/'summary.json').write_text(json.dumps(summary,indent=2)+'\n');print(json.dumps(summary))
