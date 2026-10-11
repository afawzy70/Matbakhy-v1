"""Negative tests for the P00 scope/traceability check (planning scope, not product tests)."""
import json
import shutil
import tempfile
import unittest
from pathlib import Path

from check_scope import MATRIX, check, render_matrix
from validate_blueprint import ROOT

IGNORE = shutil.ignore_patterns(".git", "__pycache__", "local", "node_modules", "target", "dist", "build", ".expo")


class ScopeGuards(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.root = Path(self.temp.name) / "repo"
        shutil.copytree(ROOT, self.root, ignore=IGNORE)

    def tearDown(self):
        self.temp.cleanup()

    def change(self, path, fn):
        p = self.root / path
        data = json.loads(p.read_text())
        fn(data)
        p.write_text(json.dumps(data))

    def regenerate(self):
        (self.root / MATRIX).write_text(render_matrix(self.root), encoding="utf-8")

    def test_current_repository_passes(self):
        self.assertEqual(check(self.root), [])

    def test_unlisted_top_level_path_is_rejected(self):
        (self.root / "mystery").mkdir()
        (self.root / "mystery/file.txt").write_text("x")
        self.assertTrue(any("unlisted top-level entry mystery" in e for e in check(self.root)))

    def test_planned_path_that_exists_is_rejected(self):
        (self.root / "infra").mkdir()
        self.assertTrue(any("infra exists but is marked PLANNED" in e for e in check(self.root)))

    def test_present_path_that_is_missing_is_rejected(self):
        shutil.rmtree(self.root / "contracts")
        self.assertTrue(any("contracts marked PRESENT but missing" in e for e in check(self.root)))

    def test_fabricated_acceptance_is_rejected(self):
        self.change("planning/decisions.json", lambda d: d["decisions"][10].update(status="ACCEPTED"))
        self.assertTrue(any("ACCEPTED without approval reference" in e for e in check(self.root)))

    def test_open_decision_with_value_is_rejected(self):
        self.change("planning/decisions.json", lambda d: d["decisions"][10].update(value="Cairo"))
        self.assertTrue(any("OPEN but has a value" in e for e in check(self.root)))

    def test_decision_missing_from_register_is_rejected(self):
        self.change("planning/decisions.json", lambda d: d["decisions"].append(
            {"id": "O99", "topic": "x", "status": "OPEN", "value": None, "required_before": "P09",
             "blocking_scope": "x", "approval_reference": None}))
        self.assertTrue(any("O99 in decisions.json but not in register" in e for e in check(self.root)))

    def test_lost_client_target_is_rejected(self):
        def drop(d):
            for s in d["stories"]:
                s["artifacts"] = [a for a in s["artifacts"] if a != "apps/customer-mobile"] or ["docs"]
        self.change("planning/backlog.json", drop)
        self.regenerate()
        self.assertTrue(any("no story owns customer mobile" in e for e in check(self.root)))

    def test_merged_native_verification_is_rejected(self):
        self.change("planning/backlog.json", lambda d: d["stories"][34].update(title="Verify native release candidates"))
        self.regenerate()
        self.assertTrue(any("no separate iOS native verification" in e for e in check(self.root)))

    def test_later_phase_dependency_is_rejected(self):
        self.change("planning/backlog.json", lambda d: d["stories"][4]["depends_on"].append("MB-009"))
        self.regenerate()
        self.assertTrue(any("MB-005 depends on later-phase MB-009" in e for e in check(self.root)))

    def test_stale_matrix_is_rejected(self):
        self.change("planning/backlog.json", lambda d: d["stories"][0].update(title="Renamed"))
        self.assertTrue(any("is stale" in e for e in check(self.root)))

    def test_removed_deferred_extension_is_rejected(self):
        p = self.root / "docs/engineering/07-integrations.md"
        p.write_text(p.read_text().replace("## Social extension", "## Something else"))
        self.assertTrue(any("Social extension" in e for e in check(self.root)))


if __name__ == "__main__":
    unittest.main()
