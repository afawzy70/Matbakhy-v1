"""Offline structural coverage and secret/link guardrails, not product acceptance."""
import json
from pathlib import Path
import re
import subprocess
import unittest

from testing_workspace import ROOT, build, outputs


class TestingWorkspaceTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.data = build()

    def test_all_canonical_cases_have_steps_outcomes_and_fixture_mapping(self):
        cases = self.data['cases']
        self.assertEqual(len(cases), 200)
        self.assertEqual(len({c['id'] for c in cases}), 200)
        actors = {a['key'] for a in self.data['actors']}
        for c in cases:
            with self.subTest(case=c['id']):
                self.assertTrue(c['steps'])
                self.assertTrue(c['prerequisites'])
                self.assertTrue(any(v for v in c['expected'].values()))
                self.assertTrue(all(s['action'].strip() for s in c['steps']))
                self.assertTrue(set(c['actors']).issubset(actors))
                self.assertTrue(set(c['readiness']['needs_fixtures']).issubset(actors))
                if c['guidance_complete']:
                    self.assertTrue(c['guidance']['reviewed'])
                    self.assertTrue(c['guidance']['source_refs'])
                    self.assertFalse(c['guidance_gaps'])

    def test_supplement_steps_preserve_ids_and_numbering(self):
        for c in self.data['cases']:
            with self.subTest(case=c['id']):
                g = c['guidance']
                self.assertTrue(g.get('title'), 'Missing Arabic title')
                self.assertEqual([s['n'] for s in g.get('steps', [])], [s['n'] for s in c['steps']])
                self.assertTrue(all(s.get('instruction') and s.get('expected') for s in g['steps']))

    def test_journey_links_and_source_refs(self):
        ids = {c['id'] for c in self.data['cases']}
        self.assertEqual([j['id'] for j in self.data['journeys']], ['J'+str(n) for n in range(9)])
        for j in self.data['journeys']:
            self.assertTrue(set(j['cases']).issubset(ids))
        for c in self.data['cases']:
            self.assertTrue((ROOT / c['source']).is_file())
        for doc in self.data['documents'].values():
            self.assertTrue((ROOT / doc['path']).is_file())

    def test_generated_outputs_are_current(self):
        for p, text in outputs(self.data).items():
            self.assertEqual(p.read_text(), text, str(p))

    def test_no_secret_patterns_or_live_verification_claims(self):
        text = json.dumps(self.data)
        patterns = [r'-----BEGIN [A-Z ]*PRIVATE KEY-----', r'gh[pousr]_[A-Za-z0-9]{20,}', r'github_pat_[A-Za-z0-9_]{20,}', r'AKIA[0-9A-Z]{16}', r'eyJ[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+']
        for pattern in patterns:
            self.assertIsNone(re.search(pattern, text))
        self.assertFalse(self.data['live_verified'])
        self.assertTrue(all(c['status'] == 'NOT_RUN' for c in self.data['cases']))

    def test_flow_preserves_all_canonical_steps_and_required_tutorial_fields(self):
        self.assertEqual(sum(len(c['steps']) for c in self.data['cases']), 1005)
        self.assertEqual(sum(sum(s['kind'] == 'canonical' for s in c['flow']) for c in self.data['cases']), 1005)
        for c in self.data['cases']:
            with self.subTest(case=c['id']):
                self.assertEqual([s['canonical_n'] for s in c['flow'] if s['kind'] == 'canonical'], [s['n'] for s in c['steps']])
                self.assertEqual(len({s['key'] for s in c['flow']}), len(c['flow']))
                self.assertTrue(all(s['actor'] and s['url'] and s['instruction'] and s['expected'] for s in c['flow']))
                if c['guidance_complete']:
                    self.assertTrue(c['tutorial_reviewed'])
                    for step in c['flow']:
                        for field in ('goal','configuration','data','verification','troubleshooting'):
                            self.assertTrue(step[field], (c['id'], step['key'], field))

    def test_approved_reference_is_exact_and_canonical_catalog_unchanged(self):
        import hashlib
        ref = ROOT / 'docs/quality/testing-workspace/reference'
        manifest = json.loads((ref / 'provenance.json').read_text())
        self.assertEqual(hashlib.sha256((ref / 'approved-prototype.html').read_bytes()).hexdigest(), manifest['sha256'])
        for path in (ROOT / 'docs/quality/owner-acceptance/cases').glob('*.json'):
            original = subprocess.check_output(['git','show',self.data['source_sha']+':'+str(path.relative_to(ROOT))],cwd=ROOT)
            self.assertEqual(path.read_bytes(), original)

    def test_frontend_logic(self):
        subprocess.run(['node', '--test', 'scripts/testing-workspace/core.test.mjs'], cwd=ROOT, check=True, capture_output=True)


if __name__ == '__main__':
    unittest.main()
