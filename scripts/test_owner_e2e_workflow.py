"""The owner E2E fixture CI workflow and its reconciliation exist and are wired together (structure only; the run itself is the CI job)."""
import pathlib
import re
import unittest

ROOT = pathlib.Path(__file__).resolve().parents[1]
WF = ROOT / ".github/workflows/owner-e2e-fixtures.yml"


class OwnerE2eWorkflow(unittest.TestCase):
    def test_files_exist(self):
        for p in (WF, ROOT / "scripts/owner_e2e_reconcile.py", ROOT / "deploy/web-preview/reconcile-owner-e2e.sql"):
            self.assertTrue(p.is_file(), p)

    def test_workflow_wiring(self):
        text = WF.read_text(encoding="utf-8")
        for needle in ("postgres:16.15-alpine", "seed-preview.sql", "seed-owner-e2e.sql", "seed-owner-e2e-mfa.sql",
                       "refresh-slots.sql", "owner_e2e_reconcile.py", "ON_ERROR_STOP=1"):
            self.assertIn(needle, text)
        for line in text.splitlines():
            m = re.search(r"uses:\s*(\S+)", line)
            if m:
                self.assertRegex(m.group(1), r"@[0-9a-f]{40}$")

    def test_reconcile_is_read_only(self):
        sql = re.sub(r"--[^\n]*", "", (ROOT / "deploy/web-preview/reconcile-owner-e2e.sql").read_text(encoding="utf-8"))
        self.assertIsNone(re.search(r"\b(INSERT|UPDATE|DELETE|DROP|ALTER|CREATE|TRUNCATE|GRANT)\b", sql, re.I))


if __name__ == "__main__":
    unittest.main()
