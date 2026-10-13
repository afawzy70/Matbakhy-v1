"""Fail on missing/duplicate/zero executed cases; summarize assertions, never repair outcomes."""
import json,sys
from pathlib import Path
root=Path(__file__).resolve().parents[2];run=root/'verification/runs/20261013-IQA-d877a15/evidence'
r=json.loads((run/'results.json').read_text())['results'];assert r,'zero cases executed'
cases=json.loads((root/'docs/quality/test-cases/cases.json').read_text())
expected={c['id'] for c in cases if c['group']!='native' and c['id'] not in {'M-SCREENREADER','M-SMS','M-DEPLOYED','M-SECRETS','P-PRECOMMIT','P-REFRESH-FAIL'}}
ids=[c['id'] for c in r];missing=sorted(expected-set(ids));duplicate=sorted({i for i in ids if ids.count(i)>1});unknown=sorted(set(ids)-expected)
summary={'expectedCases':len(expected),'recordedCases':len(r),'missing':missing,'duplicates':duplicate,'unexpected':unknown,'results':{s:sum(c['status']==s for c in r) for s in ['PASS','FAIL','BLOCKED','NOT_RUN']},'assertions':sum(len(c['checks']) for c in r)}
(run/'validation.json').write_text(json.dumps(summary,indent=2)+'\n');print(json.dumps(summary))
assert not missing and not duplicate and not unknown,'suite silently skipped or duplicated required cases'
assert all(c.get('applicationSha')=='d877a1576cd942200fe0a53b5a19fee76b996025' and c.get('testSuiteSha') and c['checks'] for c in r)
if any(c['status']!='PASS' for c in r):sys.exit(1)
