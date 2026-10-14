"""Meaningful negative tests for the verification machinery, not product tests."""
import json
import shutil
import sys
import tempfile
import unittest
from pathlib import Path

from validate_blueprint import ROOT, validate
from verify import run_check, summarize


class BlueprintGuards(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.root = Path(self.temp.name) / "repo"
        shutil.copytree(ROOT, self.root, ignore=shutil.ignore_patterns(".git", "__pycache__", "local", "node_modules", "target", "dist", ".expo", "android", "ios"))

    def tearDown(self):
        self.temp.cleanup()

    def change(self, path, fn):
        p = self.root / path
        data = json.loads(p.read_text())
        fn(data)
        p.write_text(json.dumps(data))

    def test_valid_initial_blueprint(self):
        self.assertEqual(validate(self.root), [])

    def test_dependency_cycle_is_rejected(self):
        self.change("planning/backlog.json", lambda d: d["stories"][0]["depends_on"].append("MB-002"))
        self.assertTrue(any("cycle" in x for x in validate(self.root)))

    def test_done_without_evidence_cannot_pass(self):
        def mark(d):
            # MB-003 is TODO and its dependency is DONE, so the only defect is the missing evidence.
            story = next(s for s in d["stories"] if s["id"] == "MB-003")
            story.update(status="DONE", evidence=[], review_status="NOT_RUN")
        self.change("planning/backlog.json", mark)
        self.assertTrue(any("DONE without evidence" in x for x in validate(self.root)))

    def test_scenario_pass_without_evidence_is_rejected(self):
        # Clear evidence explicitly so the check is exercised whatever the scenario's real status is.
        self.change("planning/scenarios.json", lambda d: d["scenarios"][0].update(status="PASS", evidence=[]))
        self.assertTrue(any("PASS without observed" in x for x in validate(self.root)))

    def test_rule_losing_coverage_is_rejected(self):
        self.change("planning/rules.json", lambda d: d["rules"][0].update(scenario_ids=[]))
        self.assertTrue(any("coverage" in x for x in validate(self.root)))

    def test_initial_phase_cannot_be_promoted(self):
        errors = validate(self.root, "P09", "a" * 40)
        self.assertTrue(any("unresolved decision" in x for x in errors))
        self.assertTrue(any("missing approval" in x for x in errors))
        self.assertTrue(any("is NOT_RUN" in x for x in errors))

    def test_command_only_report_cannot_be_product_evidence(self):
        report = self.root / "verification/runs/fake.json"
        report.parent.mkdir(parents=True, exist_ok=True)
        report.write_text(json.dumps({"scope": "PHASE_COMMANDS_ONLY", "status": "PASS"}))
        self.change("planning/scenarios.json", lambda d: d["scenarios"][0].update(status="PASS", evidence=["verification/runs/fake.json"]))
        self.assertTrue(any("not product verification" in x for x in validate(self.root)))

    def test_broken_document_link_is_rejected(self):
        (self.root / "docs/broken.md").write_text("[missing](no-such-file.md)")
        self.assertTrue(any("Broken link" in x for x in validate(self.root)))

    def test_duplicate_endpoint_is_rejected(self):
        self.change("contracts/endpoints.json", lambda d: d["endpoints"].append(d["endpoints"][0]))
        self.assertTrue(any("Duplicate API" in x for x in validate(self.root)))


class RunnerGuards(unittest.TestCase):
    def execute(self, command, configured=True, timeout=5):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            out = root / "output"
            out.mkdir()
            return run_check({"id": "TEST", "name": "Synthetic guardrail test", "required": True,
                              "configured": configured, "command": command, "timeout_seconds": timeout}, root, out)

    def test_unconfigured_is_blocked(self):
        self.assertEqual(self.execute(None, False)["status"], "BLOCKED")

    def test_missing_executable_is_blocked(self):
        self.assertEqual(self.execute(["/nonexistent-matbakhy-test-executable"])["status"], "BLOCKED")

    def test_failing_command_cannot_pass(self):
        r = self.execute([sys.executable, "-c", "raise SystemExit(7)"])
        self.assertEqual((r["status"], r["exit_code"]), ("FAIL", 7))

    def test_timeout_is_failure(self):
        r = self.execute([sys.executable, "-c", "import time; time.sleep(5)"], timeout=1)
        self.assertEqual((r["status"], r["reason"]), ("FAIL", "Timeout"))

    def test_real_zero_exit_records_log(self):
        r = self.execute([sys.executable, "-c", "print('synthetic guardrail fixture')"])
        self.assertEqual((r["status"], r["exit_code"]), ("PASS", 0))
        self.assertTrue(r["log_path"])

    def test_empty_or_blocked_required_set_cannot_pass(self):
        self.assertEqual(summarize([]), "BLOCKED")
        self.assertEqual(summarize([{"required": True, "status": "BLOCKED"}, {"required": False, "status": "PASS"}]), "BLOCKED")


if __name__ == "__main__":
    unittest.main()
