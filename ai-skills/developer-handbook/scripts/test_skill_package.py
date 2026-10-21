#!/usr/bin/env python3
"""Packaging tests for the developer-handbook skill: files, metadata, internal references, reusability, safety and bilingual rules.

They prove the skill is well-formed and reusable. They do not prove that an AI session will follow it; see test_fixture_dry_run.py for the
tooling dry run and the verification notes in the pull request for the invocation check.
"""
from __future__ import annotations

import py_compile
import re
import unittest
from pathlib import Path

SKILL = Path(__file__).resolve().parent.parent
LIBRARY = SKILL.parent
EXPECTED = {
    "references": ["discovery-methodology", "developer-handover", "infrastructure-handover", "deployment-and-recovery", "interactive-documentation",
                   "bilingual-parity", "quality-gates"],
    "templates": ["handbook-index", "infrastructure-inventory", "deployment-runbook", "disaster-recovery", "live-environments", "verification-report"],
}
DESCRIPTION = ("Create, audit, maintain, and validate a complete bilingual software engineering and infrastructure handover handbook for any "
               "software repository, including architecture, business rules, development workflow, CI/CD, deployment, infrastructure "
               "reconstruction, operational procedures, live application walkthroughs, and disaster recovery.")
PROJECT_SPECIFIC = re.compile(r"matbakhy|contabo|hetzner|sslip|167[-.]86[-.]90|afawzy|staging_ssh|/srv/matbakhy", re.I)
SECRET_SHAPES = re.compile(r"-----BEGIN [A-Z ]*PRIVATE KEY-----|\bgh[pousr]_[A-Za-z0-9]{20,}|\bAKIA[0-9A-Z]{16}\b")


def text_files(base: Path):
    for p in sorted(base.rglob("*")):
        if p.is_file() and p.suffix in {".md", ".py", ".yaml", ".yml", ".txt", ".json", ".example", ""} and "__pycache__" not in p.parts:
            yield p


class SkillPackage(unittest.TestCase):
    def test_required_files_exist(self) -> None:
        self.assertTrue((SKILL / "SKILL.md").is_file())
        for folder, names in EXPECTED.items():
            for n in names:
                self.assertTrue((SKILL / folder / f"{n}.md").is_file(), f"{folder}/{n}.md")
        for rel in ("scripts/validate_bilingual_parity.py", "scripts/test_bilingual_parity.py", "scripts/scan_handbook_facts.py",
                    "scripts/test_fixture_dry_run.py", "examples/fixture-project/README.md"):
            self.assertTrue((SKILL / rel).is_file(), rel)
        self.assertTrue((LIBRARY / "README.md").is_file() and (LIBRARY / "registry.yaml").is_file())

    def test_frontmatter_is_valid(self) -> None:
        text = (SKILL / "SKILL.md").read_text(encoding="utf-8")
        m = re.match(r"^---\n(.*?)\n---\n", text, re.S)
        self.assertIsNotNone(m, "SKILL.md must start with YAML frontmatter")
        fields = dict(re.findall(r"^([a-z][a-z-]*):\s*(.+)$", m.group(1), re.M))
        self.assertEqual(fields.get("name"), "developer-handbook")
        self.assertEqual(fields.get("name"), SKILL.name)
        self.assertEqual(fields.get("description"), DESCRIPTION)
        self.assertLessEqual(len(fields["description"]), 1024)
        self.assertTrue(re.fullmatch(r"[a-z0-9-]{1,64}", fields["name"]))
        try:
            import yaml  # optional
            self.assertEqual(yaml.safe_load(m.group(1))["name"], "developer-handbook")
        except ImportError:
            pass

    def test_skill_md_is_concise_and_references_exist(self) -> None:
        text = (SKILL / "SKILL.md").read_text(encoding="utf-8")
        self.assertLess(len(text.splitlines()), 120, "keep SKILL.md concise; put detail in references")
        for ref in set(re.findall(r"`((?:references|templates|scripts|examples)/[A-Za-z0-9_./-]+)`", text)):
            self.assertTrue((SKILL / ref.rstrip("/")).exists(), f"SKILL.md mentions a missing file: {ref}")
        for folder, names in EXPECTED.items():
            for n in names:
                self.assertIn(f"{folder}/{n}.md", text, f"SKILL.md should point to {folder}/{n}.md")

    def test_relative_links_inside_the_skill_resolve(self) -> None:
        for p in text_files(SKILL):
            if p.suffix != ".md" or "fixture-project" in p.parts or "templates" in p.parts:  # templates carry placeholder links
                continue
            for target in re.findall(r"\]\(([^)#\s]+)(?:#[^)]*)?\)", p.read_text(encoding="utf-8")):
                if re.match(r"^[a-z]+:", target) or target.startswith("<"):
                    continue
                self.assertTrue((p.parent / target).exists(), f"{p.relative_to(LIBRARY)} links to missing {target}")

    def test_no_project_specific_dependencies(self) -> None:
        for p in text_files(SKILL):
            if p.name == Path(__file__).name:
                continue  # this file holds the forbidden patterns themselves
            text = p.read_text(encoding="utf-8", errors="replace")
            self.assertIsNone(PROJECT_SPECIFIC.search(text), f"{p.relative_to(LIBRARY)} contains a project-specific value")
            self.assertIsNone(SECRET_SHAPES.search(text), f"{p.relative_to(LIBRARY)} contains something shaped like a credential")

    def test_safe_handling_of_inaccessible_infrastructure(self) -> None:
        skill = (SKILL / "SKILL.md").read_text(encoding="utf-8")
        discovery = (SKILL / "references/discovery-methodology.md").read_text(encoding="utf-8")
        for phrase in ("Documentation only", "Read-only discovery", "No sudo", "No secrets", "NOT VERIFIED", "never merge"):
            self.assertIn(phrase.lower(), skill.lower(), phrase)
        self.assertIn("read-only command sheet", skill.lower())
        for forbidden in ("restarts", "firewall", "destructive restore tests"):
            self.assertIn(forbidden, discovery)
        self.assertNotRegex(skill, r"(?i)\bdefault(?:s)? to (?:the )?`?develop`?")  # never assumes a branch name
        self.assertIn("do not assume `develop`", skill)

    def test_bilingual_and_quality_requirements_are_stated(self) -> None:
        skill = (SKILL / "SKILL.md").read_text(encoding="utf-8")
        parity = (SKILL / "references/bilingual-parity.md").read_text(encoding="utf-8")
        gates = (SKILL / "references/quality-gates.md").read_text(encoding="utf-8")
        self.assertIn("Egyptian Arabic", skill)
        self.assertIn("mirror", skill.lower())
        for phrase in ("exactly one mirror", "language-switch", "No summaries", "Preserve exactly", "Maintenance policy"):
            self.assertIn(phrase, parity, phrase)
        for phrase in ("never proves semantic equivalence", "Validate the validator", "negative tests", "Independent semantic review", "Handover simulation"):
            self.assertIn(phrase.lower(), gates.lower(), phrase)

    def test_non_destructive_defaults_and_owner_control(self) -> None:
        readme = (LIBRARY / "README.md").read_text(encoding="utf-8")
        registry = (LIBRARY / "registry.yaml").read_text(encoding="utf-8")
        self.assertIn("Only the owner authorizes a new skill", readme)
        self.assertIn("external_publication: not approved", registry)
        self.assertNotIn("status: validated", registry)  # stays unvalidated until the dependencies are complete
        self.assertIn("name: developer-handbook", registry)
        self.assertEqual(registry.count("- name:"), 1, "only developer-handbook is authorized")

    def test_scripts_compile(self) -> None:
        for p in (SKILL / "scripts").glob("*.py"):
            py_compile.compile(str(p), doraise=True, cfile=str(Path("/tmp") / f"{p.stem}.pyc"))

    def test_claude_code_discovery_adapter_points_at_the_single_copy(self) -> None:
        link = LIBRARY.parent / ".claude" / "skills" / "developer-handbook"
        if not link.exists() and not link.is_symlink():
            self.skipTest("no .claude/skills adapter in this checkout (the skill may be installed elsewhere)")
        self.assertTrue(link.is_symlink(), "the adapter must be a symlink, not a competing copy")
        self.assertEqual(link.resolve(), SKILL.resolve())
        self.assertTrue((link / "SKILL.md").is_file())


if __name__ == "__main__":
    unittest.main()
