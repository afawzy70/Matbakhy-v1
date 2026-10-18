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


if __name__ == "__main__":
    unittest.main()
