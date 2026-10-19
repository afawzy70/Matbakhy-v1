"""Guards for the owner acceptance catalog (docs/quality/owner-acceptance): sources valid, generated files current, fixtures consistent."""
import subprocess
import sys
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]


class OwnerCatalogGuards(unittest.TestCase):
    def run_script(self, name):
        return subprocess.run([sys.executable, str(ROOT / "scripts" / name), "check"], capture_output=True, text=True)

    def test_catalog_is_valid_traceable_and_rendered(self):
        r = self.run_script("owner_e2e_catalog.py")
        self.assertEqual(r.returncode, 0, r.stdout + r.stderr)

    def test_fixtures_manifest_sql_and_cases_agree(self):
        r = self.run_script("owner_e2e_fixtures.py")
        self.assertEqual(r.returncode, 0, r.stdout + r.stderr)


class ValidatorRejects(unittest.TestCase):
    """The validator must catch a recorded PASS, credential-bearing fields and token-like values anywhere in a source document."""

    @classmethod
    def setUpClass(cls):
        import importlib.util

        spec = importlib.util.spec_from_file_location("owner_e2e_catalog", ROOT / "scripts" / "owner_e2e_catalog.py")
        cls.mod = importlib.util.module_from_spec(spec)
        spec.loader.exec_module(cls.mod)

    def flagged(self, doc):
        return [p for w, k, t in self.mod.walk_strings(doc) for p in self.mod.suspicious(w, k, t)]

    def test_credential_fields_and_values(self):
        for doc in ({"accessToken": "x"}, {"credentials": {"value": "x"}}, {"conventions": {"client_secret": "x"}}, {"a": ["password: Abcdef123"]},
                    {"a": "access_token=abc123def"}, {"a": "Bearer abcdefghijklmnopqrstuvwxyz"}):
            self.assertTrue(self.flagged(doc), doc)

    def test_recorded_pass_claims(self):
        for doc in ({"result": "PASS"}, {"expected": {"ui": ["PASS"]}}, {"a": "Status: PASS"}, {"a": "All checks passed."}, {"outcome": "passed on 2026-10-18"}):
            self.assertTrue(self.flagged(doc), doc)

    def test_legitimate_text_is_not_flagged(self):
        for doc in ({"a": "verify prints RESULT: PASS only when every assurance check passes"}, {"a": "password from the private fixture sheet"},
                    {"findings": ["PASS requires evidence"]}, {"a": "Passwords are never printed"}):
            self.assertEqual(self.flagged(doc), [], doc)


if __name__ == "__main__":
    unittest.main()
