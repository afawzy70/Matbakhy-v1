"""Render review documents from independent definitions, without editing product registry."""
import json
from pathlib import Path

root = Path(__file__).resolve().parents[2]
base = root / 'docs/quality/test-cases'
cases = json.loads((base / 'cases.json').read_text())
assert len(cases) > 0 and len({c['id'] for c in cases}) == len(cases)
required = {'id','title','purpose','requirementReferences','group','roles','platforms','locales','priority','preconditions','steps','expected','invariants','cleanup','automation','reviewStatus','questions'}
for c in cases:
    assert required <= c.keys(), c['id']
    assert all(c[x] for x in required) and c['steps'], c['id']
    assert c['reviewStatus'] in {'DRAFT','SELF_REVIEWED','OWNER_REVIEWED','NEEDS_REQUIREMENT_DECISION'}
    assert c['reviewStatus'] != 'OWNER_REVIEWED', 'No owner review has occurred for this suite'
for group in ['customer-web','kitchen','backoffice','cross-cutting','native-android','native-ios']:
    selected = [c for c in cases if c['group'] == ('native' if group.startswith('native-') else group)]
    parts = [f'# {group} test definitions\n\nDefinitions, not execution results. Generated from cases.json; SELF_REVIEWED, never owner-approved. Canonical product acceptance remains planning/scenarios.json. Read the eligibility matrix and run report for the executed boundary of each variation.\n']
    for c in selected:
        steps = '\n'.join(f'{i}. {s}' for i, s in enumerate(c['steps'], 1))
        parts.append(f"## {c['id']} — {c['title']}\n\nPurpose: {c['purpose']}.\n\nReferences: {c['requirementReferences']}. Roles: {c['roles']}. Platforms: {group if group.startswith('native-') else c['platforms']}. Locales: {c['locales']}. Priority: {c['priority']}.\n\nPreconditions/fixtures: {c['preconditions']}.\n\n{steps}\n\nExpected: {c['expected']}\n\nPersisted/API invariants: {c['invariants']}\n\nCleanup/reset: {c['cleanup']}\n\nAutomation/manual reference: {c['automation']}.\n\nReview: {c['reviewStatus']}. Questions: {c['questions']}\n")
    (base / (group + '.md')).write_text('\n'.join(parts))
print(f'Validated and rendered {len(cases)} unique independent definitions; no owner approval inferred')
