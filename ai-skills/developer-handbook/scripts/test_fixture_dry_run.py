#!/usr/bin/env python3
"""Isolated behavioural dry run of the skill's tooling against the fixture project (never against a real or live system).

Copies the fixture to a temporary directory, then checks that discovery finds exactly the technologies the fixture contains, that a
faithful English/Arabic pair passes the parity validator, that several injected defects fail it, and that the scanner lists the
secret-like template without reading it. This proves the bundled tools behave on a representative project; it does not prove that an
AI session follows SKILL.md perfectly.
"""
from __future__ import annotations

import json
import shutil
import subprocess
import sys
import tempfile
import unittest
from pathlib import Path

HERE = Path(__file__).resolve().parent
FIXTURE = HERE.parent / "examples" / "fixture-project"
VALIDATOR = HERE / "validate_bilingual_parity.py"
SCANNER = HERE / "scan_handbook_facts.py"


def run(*args: str, cwd: Path | None = None) -> subprocess.CompletedProcess:
    return subprocess.run([sys.executable, *args], capture_output=True, text=True, cwd=cwd)


class FixtureDryRun(unittest.TestCase):
    def setUp(self) -> None:
        self._t = tempfile.TemporaryDirectory()
        self.addCleanup(self._t.cleanup)
        self.repo = Path(self._t.name) / "project"
        shutil.copytree(FIXTURE, self.repo)

    def validate(self, *extra: str) -> subprocess.CompletedProcess:
        return run(str(VALIDATOR), "--root", str(self.repo), "--docs-dir", "docs/handbook", "--require-metadata", *extra)

    def test_scanner_finds_only_what_the_fixture_contains(self) -> None:
        r = run(str(SCANNER), "--root", str(self.repo), "--json")
        self.assertEqual(r.returncode, 0, r.stderr)
        data = json.loads(r.stdout)
        self.assertIn("Python", data["technologies"])
        self.assertIn("Docker", data["technologies"])
        self.assertIn("GitHub Actions", data["ci"])
        for absent in ("Java / Maven", "Kubernetes / Helm", "Terraform", "Android", "iOS / Xcode"):
            self.assertNotIn(absent, data["technologies"], f"{absent} must not be invented")
        self.assertEqual(data["secret_like_files_listed_by_name_only"], [])  # .env.example is a template

    def test_scanner_lists_secret_like_files_by_name_only(self) -> None:
        (self.repo / ".env").write_text("NOTES_API_TOKEN=do-not-print-me\n")
        r = run(str(SCANNER), "--root", str(self.repo))
        self.assertIn(".env", r.stdout)
        self.assertNotIn("do-not-print-me", r.stdout)

    def test_faithful_pairs_pass(self) -> None:
        r = self.validate()
        self.assertEqual(r.returncode, 1, "the reviewed-pair record is missing, so the first run must ask for it")
        self.assertIn("[sync]", r.stdout)
        self.assertEqual(run(str(VALIDATOR), "--root", str(self.repo), "--docs-dir", "docs/handbook", "--update-sync").returncode, 0)
        r = self.validate()
        self.assertEqual(r.returncode, 0, r.stdout)
        self.assertIn("PARITY RESULT: PASS (2 English page(s)", r.stdout)

    def test_injected_defects_fail(self) -> None:
        run(str(VALIDATOR), "--root", str(self.repo), "--docs-dir", "docs/handbook", "--update-sync")
        ar = self.repo / "docs/handbook/run-guide-ar.md"
        original = ar.read_text(encoding="utf-8")
        cases = {
            "missing mirror": lambda: ar.unlink(),
            "changed command": lambda: ar.write_text(original.replace("flask --app app.main run", "flask run"), encoding="utf-8"),
            "dropped table row": lambda: ar.write_text(original.replace("| التشغيل | `flask --app app.main run` | لأ |\n", ""), encoding="utf-8"),
            "dropped checklist item": lambda: ar.write_text(original.replace("- [ ] الرد فيه `UP`.", "الرد فيه `UP`."), encoding="utf-8"),
            "changed number": lambda: ar.write_text(original.replace("3.12", "3.11"), encoding="utf-8"),
            "broken link": lambda: ar.write_text(original.replace("(run-guide.md)", "(missing.md)"), encoding="utf-8"),
        }
        for name, break_it in cases.items():
            with self.subTest(name):
                ar.write_text(original, encoding="utf-8")
                break_it()
                self.assertEqual(self.validate().returncode, 1, f"{name} must fail the validator")

    def test_other_mirror_suffix_is_supported(self) -> None:
        for p in sorted((self.repo / "docs/handbook").glob("*.md")):
            text = p.read_text(encoding="utf-8").replace("-ar.md", "-fr.md")
            target = p.with_name(p.name.replace("-ar.md", "-fr.md"))
            target.write_text(text, encoding="utf-8")
            if target != p:
                p.unlink()
        run(str(VALIDATOR), "--root", str(self.repo), "--docs-dir", "docs/handbook", "--suffix=-fr", "--update-sync")
        r = self.validate("--suffix=-fr")
        self.assertEqual(r.returncode, 0, r.stdout)


if __name__ == "__main__":
    unittest.main()
