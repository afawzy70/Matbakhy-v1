"""Compose executed segments without counting retries twice; preserve raw outcomes unchanged."""
import json
from collections import Counter
from pathlib import Path
p=Path(__file__).parent/'evidence'
raw=json.loads((p/'results.json').read_text())
latest={r['id']:r for r in raw['results']}
rows=[]
for ident, r in latest.items():
 if ident.endswith('-FINAL') and ident[:-6] in latest:continue
 parts=[r]
 final=latest.get(ident+'-FINAL')
 if final:parts.append(final)
 checks=[c for part in parts for c in part['checks'] if c['status']!='BLOCKED']
 status='FAIL' if any(c['status']=='FAIL' for c in checks) else final['status'] if final else r['status']
 note='Original business steps actually executed; supplemental FINAL verified remaining assertions after correcting a QA locator.' if final else 'Latest execution; earlier attempts remain in results.json.'
 if ident=='S-CONFIG-QUOTES' and any(c.get('name')=='Requirement conflict prevents product-defect verdict' for c in r['checks']):
  status='BLOCKED';note='Oracle conflict: BR-042/backlog/contract/UI new-quotes-only wording vs verification stale-zone/policy scenario and implementation. Initial failed expectation remains visible; not changed into a PASS. Owner clarification requested.'
 if ident=='S-SUPPORT-RESOLVE':
  assigned=r['checks'][0]['actual']['data'].get('assigneeId');expected=json.loads((p/'fixtures.json').read_text())['actors']['support-a'];checks.append(dict(name='Independent review: assigned staff identity equals actual support actor',status='PASS' if assigned==expected else 'FAIL',expected=expected,actual=assigned));status='FAIL' if assigned!=expected else status
 rows.append(dict(id=ident,actor=r['actor'],requirement=r['requirement'],status=status,checks=checks,evidence=list(dict.fromkeys(e for part in parts for e in part['evidence'])),rawSegments=[dict(id=part['id'],startedAt=part['startedAt'],status=part['status']) for part in parts],reviewNote=note))
result={'applicationSha':raw['applicationSha'],'unit':'case group, not individual assertion or screen','webCounts':dict(Counter(r['status'] for r in rows)),'web':rows}
(p/'reviewed-results.json').write_text(json.dumps(result,indent=2)+'\n')
print(result['webCounts'],len(rows))
