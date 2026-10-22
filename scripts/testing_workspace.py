#!/usr/bin/env python3
"""Deterministic, offline catalog projection. No credentials or live verification."""
import argparse
import hashlib
import json
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
SOURCE = ROOT / 'docs/quality/owner-acceptance'
GUIDANCE = ROOT / 'docs/quality/testing-workspace/guidance'
OUT = ROOT / 'scripts/testing-workspace'


def build():
    actors = json.loads((SOURCE / 'fixtures.json').read_text())['actors']
    actor_keys = {a['key'] for a in actors}
    cases, inputs = [], {}
    for path in sorted((SOURCE / 'cases').glob('*.json')):
        raw = path.read_bytes()
        inputs[str(path.relative_to(ROOT))] = hashlib.sha256(raw).hexdigest()
        source = json.loads(raw)
        supplement_path = GUIDANCE / path.name
        supplement = json.loads(supplement_path.read_text()) if supplement_path.exists() else {}
        if supplement_path.exists():
            inputs[str(supplement_path.relative_to(ROOT))] = hashlib.sha256(supplement_path.read_bytes()).hexdigest()
        for original in source['cases']:
            case = dict(original)
            case['catalog_design_status'] = original.get('status', 'NOT_RUN')
            case['status'] = 'NOT_RUN'  # This workspace delivery has executed no product cases.
            guide = supplement.get(case['id'], {})
            missing = []
            for key in ('steps', 'expected', 'prerequisites', 'actors'):
                if not case.get(key):
                    missing.append('Missing canonical ' + key)
            refs = set(case.get('actors', [])) | set(case.get('readiness', {}).get('needs_fixtures', []))
            missing.extend('Unmapped actor: ' + a for a in sorted(refs - actor_keys) if a.startswith('FX-'))
            translated = {s['n']: s for s in guide.get('steps', [])}
            if not guide.get('title') or not guide.get('purpose'):
                missing.append('Egyptian Arabic title/purpose not supplied')
            for step in case['steps']:
                item = translated.get(step['n'], {})
                if not item.get('instruction') or not item.get('expected'):
                    missing.append('Arabic execution/expected guidance missing for step ' + str(step['n']))
            # A human-readable review decision is mandatory, never inferred from fields.
            if guide.get('reviewed') is not True or not guide.get('source_refs'):
                missing.append('Case-specific source/configuration/fixture review is pending')
            for ref in guide.get('source_refs', []):
                if not (ROOT / ref).is_file():
                    missing.append('Missing source reference: ' + ref)
            missing.extend(guide.get('blockers', []))
            case.update(domain=source['domain'], source=str(path.relative_to(ROOT)),
                        guidance=guide, conventions=source.get('conventions', {}),
                        guidance_gaps=missing, guidance_complete=not missing)
            cases.append(case)
    ids = [c['id'] for c in cases]
    if len(ids) != 200 or len(set(ids)) != 200:
        raise ValueError('Expected exactly 200 unique canonical cases')
    journeys = json.loads((SOURCE / 'core-suite.json').read_text())['journeys']
    assert all(i in ids for j in journeys for i in j['cases'])
    # Embed reusable source material, so no repository search is required mid-run.
    documents = {}
    for name, rel in {
        'fixtures': 'docs/quality/owner-acceptance/fixtures.md',
        'readiness': 'docs/quality/owner-acceptance/generated/readiness-report.md',
        'ownerGuide': 'docs/quality/owner-acceptance/owner-guide-ar.md',
        'execution': 'docs/quality/owner-e2e-execution-guide.md',
        'keycloak': 'docs/operations/14-keycloak-admin-console.md',
        'mfa': 'docs/operations/13-keycloak-mfa-migration.md',
        'local': 'docs/developer-handbook/05-developer-workflow/local-environment-ar.md',
    }.items():
        documents[name] = {'path': rel, 'text': (ROOT / rel).read_text()}
        inputs[rel] = hashlib.sha256((ROOT / rel).read_bytes()).hexdigest()
    for rel in ['core-suite.json', 'fixtures.json']:
        inputs[str((SOURCE / rel).relative_to(ROOT))] = hashlib.sha256((SOURCE / rel).read_bytes()).hexdigest()
    source_sha = json.loads((ROOT / 'docs/quality/testing-workspace/source-baseline.json').read_text())['commit']
    # The base SHA identifies source-code links, never the generated/supplement content.
    catalog_digest = hashlib.sha256(json.dumps(inputs, sort_keys=True).encode()).hexdigest()
    return dict(schema=1, source_sha=source_sha, live_verified=False, inputs=inputs,
                catalog_digest=catalog_digest, provenance='Source links: develop base; content: per-file SHA256 input manifest',
                cases=cases, actors=actors, journeys=journeys, documents=documents)


def outputs(data):
    rows = ['# Testing Workspace coverage matrix', '',
            'Representation is not guidance completeness or execution evidence. All product results start NOT_RUN.', '',
            '| Case | Domain | Steps | Guidance | Gaps |', '|---|---|---:|---|---|']
    for case in data['cases']:
        rows.append('| ' + ' | '.join([case['id'], case['domain'], str(len(case['steps'])),
                    'COMPLETE' if case['guidance_complete'] else 'NOT VERIFIED',
                    '; '.join(case['guidance_gaps']).replace('|', '/')]) + ' |')
    return {
        OUT / 'catalog.json': json.dumps(data, ensure_ascii=False, indent=2) + '\n',
        ROOT / 'docs/quality/testing-workspace/coverage-matrix.md': '\n'.join(rows) + '\n',
    }


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--check', action='store_true')
    args = parser.parse_args()
    data = build()
    for path, content in outputs(data).items():
        if args.check:
            if not path.exists() or path.read_text() != content:
                raise SystemExit('STALE: ' + str(path.relative_to(ROOT)))
        else:
            path.parent.mkdir(parents=True, exist_ok=True)
            path.write_text(content)
    print(f"200 represented; {sum(c['guidance_complete'] for c in data['cases'])} guidance complete; live verification NOT_RUN")
