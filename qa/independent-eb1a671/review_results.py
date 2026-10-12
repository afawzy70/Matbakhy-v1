"""Adjudicate assertion meaning and incomplete coverage; never turn failures into PASS."""
import json
from pathlib import Path

p = Path(__file__).parent / 'evidence'
raw = json.loads((p / 'results.json').read_text())
reviewed = []
for row in raw['results']:
    result = dict(row)
    result['automatedStatus'] = row['status']
    if row['id'] in ['IQA-05', 'IQA-06', 'IQA-07', 'IQA-08']:
        result['status'] = 'BLOCKED'
        result['reviewNote'] = ('Replacement kitchen/meal/slot/quantity assertions PASS. The initial case also '
                                'requires the resulting basket total, which cannot be checked because CW05 '
                                'subtotal is absent (FG-001). Do not promote the core-path PASS to whole-case PASS.')
    if row['id'] == 'IQA-09':
        result['status'] = 'FAIL'
        result['reviewNote'] = ('Main run PASS covered unoffered capacity=0, quantity cap, closed/past exclusion, '
                                'paused kitchen, empty and error/retry. Supplementary real API reproduction '
                                'shows an exhausted selected slot can still be added when another slot has '
                                'stock (MBQA-002). True all-slots SOLD_OUT and controlled loading verified separately.')
        result['evidence'] += ['supplementary.json', 'MBQA-002-exhausted-selection.png',
                               'IQA-09-true-soldout.png', 'IQA-09-controlled-loading.png']
    if row['id'] == 'IQA-10':
        result['status'] = 'BLOCKED'
        result['reviewNote'] = ('Layout/direction and prompt entry/Escape/restoration PASS. Supplementary '
                                'focus-containment assertion FAIL is observable but not a confirmed defect: '
                                'approved requirements do not specify modal vs inline prompt (RQ-002). '
                                'Root-font enlargement in the main run did not enlarge px-sized text, so it '
                                'is not zoom evidence. Supplementary computed font enlargement is verified '
                                '(20px -> 40px; scrollWidth=320), but native browser zoom/full keyboard/AT review '
                                'remain NOT_RUN. Scope-limited accessibility acceptance is unresolved.')
        result['evidence'] += ['supplementary.json', 'IQA-10-text-enlargement.png']
    reviewed.append(result)
counts = {s: sum(r['status'] == s for r in reviewed) for s in ['PASS','FAIL','BLOCKED','NOT_RUN']}
out = dict(applicationSha=raw['applicationSha'], scope='INDEPENDENT_REPORT_ONLY_BROWSE_BASKET',
           counts=counts, cases=reviewed, releaseApproval=False)
(p / 'reviewed-results.json').write_text(json.dumps(out,ensure_ascii=False,indent=2)+'\n')
print(counts)
