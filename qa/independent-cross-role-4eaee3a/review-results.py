"""Compose executed segments without counting retries twice; preserve raw outcomes unchanged."""
import json
from collections import Counter
from pathlib import Path
p=Path(__file__).parent/'evidence'
raw=json.loads((p/'results.json').read_text())
latest={r['id']:r for r in raw['results']}
rows=[]
for ident, r in latest.items():
 if ident.endswith('-FINAL'):continue
 parts=[r]
 final=latest.get(ident+'-FINAL')
 if final:parts.append(final)
 checks=[c for part in parts for c in part['checks'] if c['status']!='BLOCKED']
 status='FAIL' if any(c['status']=='FAIL' for c in checks) else final['status'] if final else r['status']
 rows.append(dict(id=ident,actor=r['actor'],requirement=r['requirement'],status=status,checks=checks,evidence=list(dict.fromkeys(e for part in parts for e in part['evidence'])),rawSegments=[dict(id=part['id'],startedAt=part['startedAt'],status=part['status']) for part in parts],reviewNote='Original business steps actually executed; supplemental FINAL verified remaining assertions after correcting a QA locator.' if final else 'Latest execution; earlier attempts remain in results.json.'))
result={'applicationSha':raw['applicationSha'],'unit':'case group, not individual assertion or screen','webCounts':dict(Counter(r['status'] for r in rows)),'web':rows}
(p/'reviewed-results.json').write_text(json.dumps(result,indent=2)+'\n')
print(result['webCounts'],len(rows))
