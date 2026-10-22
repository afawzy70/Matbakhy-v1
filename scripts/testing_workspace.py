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


DOMAIN = '167-86-90-174.sslip.io'


def deployed(value):
    return value.replace('<PREVIEW_DOMAIN>', DOMAIN).replace('<D>', DOMAIN)


def actor_url(actor):
    host = 'ops' if any(x in actor for x in ('COOK', 'STAFF', 'MULTIROLE')) else 'app'
    return 'https://' + host + '.' + DOMAIN + '/'


def project_flow(case, guide):
    """Case-specific source projection, visibly unreviewed; never a completeness grant."""
    result = []
    for i, prerequisite in enumerate(case['prerequisites']):
        result.append(dict(key='pre:' + str(i), kind='setup', actor='المختبِر / مشغّل البيئة',
            title='تجهيز ' + str(i + 1), goal='تحقق من شرط الحالة قبل التنفيذ',
            url=actor_url(case['actors'][0]), configuration=deployed(prerequisite),
            data='', instruction='راجع الشرط والقيم المكتوبة هنا، ونفّذ التحقق المحدد فيه قبل استكمال الحالة.',
            expected='الشرط يتحقق كما هو مكتوب؛ لو أداة أو حساب أو قيمة ناقصة سجّل BLOCKED مع السبب.',
            verification='تعليمات الشرط أعلاه هي مرجع التحقق. الشرح العربي التفصيلي للتجهيز ده لسه محتاج مراجعة حالة بحالة.',
            troubleshooting='ماتستخدمش قيم بديلة بالتخمين أو تغيّر بيئة مشتركة من غير موافقة.', code=''))
    if case['readiness'].get('needs_fixtures'):
        result.append(dict(key='setup:fixtures',kind='setup',actor='مشغّل البيئة',
            title='راجع الحسابات الإضافية الخاصة بالحالة',goal='الهويات ومنح التطبيق تبقى متطابقة مع manifest.',
            url='https://ops.' + DOMAIN + '/',
            configuration='الأداة من deploy/web-preview؛ plan للقراءة، apply تعديل فعلي في Keycloak وقاعدة التطبيق، يحتاج موافقة المالك على Preview المشتركة. لا تنشئ User عشوائي بدل fixed-ID fixture.',
            data=', '.join(case['readiness']['needs_fixtures']),
            instruction='شغّل plan عند المشغّل. لو الحسابات ناقصة استخدم apply بعد التفويض فقط: الأداة تعمل backup وpartial import بنفس IDs وSQL للمنح وrefresh للمواعيد وreconciliation. existing users يحتفظوا بباسوردهم القديم. لا تعيد seed-preview.sql.',
            expected='reconciliation تنجح وتظهر الهوية وصلاحياتها المطلوبة. كلمات السر الجديدة في BACKUP_DIR/owner-e2e-private/<run>/credentials.private.json، صلاحيات600، عبر قناة خاصة فقط.',
            verification='المشغّل يقارن IDs والـgrants بالقيم المطلوبة في تجهيزات الحالة؛ عند اختلاف ID الأداة ترفض قبل SQL.',
            troubleshooting='الحساب المستهلك أو جهاز/بيئة FX-ISOLATED مش بيتصلح بإعادة apply؛ لو المطلوب غير متاح سجّل BLOCKED.',
            code='cd deploy/web-preview\n./owner-e2e-provision.sh plan\n# apply بعد موافقة مالك البيئة المشتركة فقط:\n./owner-e2e-provision.sh apply'))
    if case.get('conventions', {}).get('api_helper'):
        result.append(dict(key='setup:api-helper',kind='setup',actor='نفس حساب خطوة API التالية',
            title='جهّز مساعد API داخل تبويب التطبيق',goal='تنفيذ أوامر الحالة بنفس session الصحيحة.',
            url=actor_url(case['actors'][0]),configuration=deployed(case['conventions']['api_helper_usage']),
            data=deployed(case['conventions'].get('seed_money_facts','')),
            instruction='افتح DevTools → Console في تبويب التطبيق للحساب المحدد، ثم الصق التعريف أدناه. المساعد لا يرسل طلبات إلا لما تستدعيه. لا تدخل باسورد أو token في الكود.',
            expected='تعريف api متاح؛ GET /api/v1/me يثبت الهوية قبل أوامر التعديل.',
            verification=deployed(case['conventions'].get('browser_profiles','')),
            troubleshooting='لو XSRF-TOKEN غايب أعد تحميل التطبيق وأعد me. 401 جلسة؛403 افحص جسم الرد، لا تمنح صلاحيات عشوائية.',
            code=deployed(case['conventions']['api_helper'])))
    translated = {x['n']: x for x in guide.get('steps', [])}
    for original in case['steps']:
        t = translated.get(original['n'], {})
        result.append(dict(key='step:' + str(original['n']),kind='canonical',canonical_n=original['n'],
            title=t.get('instruction', 'الخطوة الأصلية ' + str(original['n'])),
            actor=original['actor'],url=actor_url(original['actor']),goal=t.get('instruction', original['action']),
            configuration='',data=deployed(original.get('data','')),
            instruction=t.get('instruction', original['action']),expected=t.get('expected','NOT VERIFIED — راجع النتايج الأصلية الظاهرة في خطوة التحقق النهائية.'),
            verification='قارن النتيجة الفعلية بالمتوقع لهذه الخطوة وبالنتايج الأصلية الكاملة الظاهرة في نهاية المسار؛ ربط النتائج غير المتراجع يفضل NOT VERIFIED.',
            troubleshooting='لو تعليمات المصدر ناقصة أو القيمة/الشاشة مختلفة، سجّل رقم النسخة والفرق وBLOCKED بدل التخمين.',code='',original=deployed(original['action'])))
    return result


def build():
    actors = json.loads((SOURCE / 'fixtures.json').read_text())['actors']
    actor_keys = {a['key'] for a in actors}
    cases, inputs = [], {}
    tutorial_path = GUIDANCE / 'tutorials.json'
    tutorials = json.loads(tutorial_path.read_text()) if tutorial_path.exists() else {}
    if tutorial_path.exists():
        inputs[str(tutorial_path.relative_to(ROOT))] = hashlib.sha256(tutorial_path.read_bytes()).hexdigest()
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
            tutorial = tutorials.get(case['id'])
            if tutorial:
                guide = {**guide, **{k:v for k,v in tutorial.items() if k != 'flow'}}
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
            case['conventions'] = source.get('conventions', {})
            flow = tutorial['flow'] if tutorial else project_flow(case, guide)
            if not tutorial or not tutorial.get('reviewed'):
                missing.append('Chronological standalone tutorial review pending')
            if tutorial:
                mapping = tutorial.get('prerequisite_map', {})
                flow_keys = {s['key'] for s in flow}
                if set(mapping) != {str(i) for i in range(len(case['prerequisites']))} or any(not keys or not set(keys).issubset(flow_keys) for keys in mapping.values()):
                    missing.append('Canonical prerequisite mapping is incomplete')
                numbers = [s['canonical_n'] for s in flow if s['kind'] == 'canonical']
                if numbers != [s['n'] for s in case['steps']]:
                    raise ValueError('Canonical flow mapping changed: ' + case['id'])
                for step in flow:
                    for field in ('actor','url','goal','configuration','data','instruction','expected','verification','troubleshooting'):
                        if not step.get(field):
                            missing.append('Tutorial field missing: ' + step['key'] + '/' + field)
            missing.extend(guide.get('blockers', []))
            case.update(domain=source['domain'], source=str(path.relative_to(ROOT)),
                        guidance=guide, flow=flow, tutorial_reviewed=bool(tutorial and tutorial.get('reviewed')), conventions=source.get('conventions', {}),
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
    baseline_path = ROOT / 'docs/quality/testing-workspace/source-baseline.json'
    inputs[str(baseline_path.relative_to(ROOT))] = hashlib.sha256(baseline_path.read_bytes()).hexdigest()
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
        OUT / 'catalog.json': json.dumps(data, ensure_ascii=False, separators=(',', ':')) + '\n',
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
