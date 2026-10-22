#!/usr/bin/env python3
"""Tests for validate_bilingual_parity.py (the developer-handbook skill's parity validator). Each test builds a throw-away handbook (English page plus Arabic mirror).

They prove the structural checks detect the defects listed in the task: missing or orphan mirrors, heading, code-fence, table,
list, image, link and identifier drift, broken links, unsynchronised changes. They do not (and the validator does not) prove
semantic equivalence; that needs the separate human/independent review recorded in translation-parity-report.md.
"""
from __future__ import annotations

import contextlib
import io
import json
import shutil
import subprocess
import sys
import tempfile
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
try:
    import validate_bilingual_parity as v  # noqa: E402
except SystemExit as exc:  # markdown-it-py is not installed (for example in the generic script-test job)
    raise unittest.SkipTest(f"markdown-it-py is required for the parity validator tests: {exc}")

META = "> **Owner role:** Handbook navigation\n> **Status:** REFERENCE — {m}\n> **Language / اللغة:** {lang}\n"
EN = """# Title

""" + META.format(m="meaning", lang="English · [العربية](a-ar.md)") + """
Intro paragraph with `code_id` and TC-001 and 42 items. See [other](b.md) and [section](#second-part).

## Second part

| Name | Value |
|---|---|
| one | 1 |
| two | 2 |

- [ ] first check
- [x] second check

1. step one
2. step two

```bash
echo "hello"
```

```mermaid
graph TD
  A[Start] --> B[End]
```

![diagram](img/x.png)

> Note: be careful.

[external](https://example.com/x)
"""
AR = """# العنوان

""" + META.format(m="المعنى", lang="[English](a.md) · العربية") + """
فقرة افتتاحية فيها `code_id` و TC-001 و 42 عنصر. شوف [التاني](b-ar.md) و[القسم](#الجزء-التاني).

## الجزء التاني

| الاسم | القيمة |
|---|---|
| واحد | 1 |
| اتنين | 2 |

- [ ] الفحص الأول
- [x] الفحص التاني

1. الخطوة الأولى
2. الخطوة التانية

```bash
echo "hello"
```

```mermaid
graph TD
  A["البداية"] --> B["النهاية"]
```

![رسم](img/x.png)

> ملحوظة: خلّي بالك.

[خارجي](https://example.com/x)
"""
B_EN = "# B\n\n" + META.format(m="b", lang="English · [العربية](b-ar.md)") + "\nText.\n"
B_AR = "# ب\n\n" + META.format(m="ب", lang="[English](b.md) · العربية") + "\nنص.\n"


# synthetic values split so secret scanners do not mistake the test fixtures for credentials
QUOTED = "Quoted" + "Secret" + "1234567"
LEAK = "Synthetic" + "Leak" + "42"

class Fixture:
    def __init__(self) -> None:
        self._t = tempfile.TemporaryDirectory()
        self.root = Path(self._t.name)
        self.hb = self.root / v.HB
        self.hb.mkdir(parents=True)
        self.write("a.md", EN), self.write("a-ar.md", AR), self.write("b.md", B_EN), self.write("b-ar.md", B_AR)
        (self.hb / "img").mkdir()
        (self.hb / "img/x.png").write_bytes(b"x")

    def write(self, rel: str, text: str) -> None:
        (self.hb / rel).write_text(text, encoding="utf-8")

    def edit(self, rel: str, old: str, new: str) -> None:
        t = (self.hb / rel).read_text(encoding="utf-8")
        assert old in t, old
        self.write(rel, t.replace(old, new, 1))

    def check(self, record: bool = True) -> dict[str, list[str]]:
        if record:
            with contextlib.redirect_stdout(io.StringIO()):
                v.Validator(self.root).update_sync()
        val = v.Validator(self.root)
        val.run()
        return val.problems


class ParityTests(unittest.TestCase):
    def setUp(self) -> None:
        v.configure("docs/handbook", "-ar", True, ("08-infrastructure/",), None)
        self.f = Fixture()
        self.addCleanup(self.f._t.cleanup)

    def assertProblem(self, problems, check: str, page: str | None = None) -> None:
        flat = [(p, m) for p, ms in problems.items() for m in ms]
        self.assertTrue(any(f"[{check}]" in m and (page is None or p == page) for p, m in flat), flat)

    def test_matching_pair_passes(self) -> None:
        self.assertEqual(self.f.check(), {})

    def test_missing_arabic_counterpart(self) -> None:
        (self.f.hb / "b-ar.md").unlink()
        self.assertProblem(self.f.check(), "missing", "b.md")

    def test_orphan_arabic_page(self) -> None:
        self.f.write("ghost-ar.md", B_AR)
        self.assertProblem(self.f.check(), "orphan", "ghost-ar.md")

    def test_heading_level_and_count(self) -> None:
        self.f.edit("a-ar.md", "## الجزء التاني", "### الجزء التاني")
        self.assertProblem(self.f.check(), "headings")

    def test_code_fence_content_language_and_count(self) -> None:
        self.f.edit("a-ar.md", 'echo "hello"', 'echo "مرحبا"')
        self.assertProblem(self.f.check(), "fences")
        self.f.edit("a-ar.md", "```bash", "```sh")
        self.assertProblem(self.f.check(), "fences")
        self.f.edit("a-ar.md", "```sh\necho", "echo")
        self.assertProblem(self.f.check(), "fences")

    def test_mermaid_labels_may_be_translated_but_structure_may_not(self) -> None:
        self.assertEqual(self.f.check(), {})  # fixture already translates the labels
        self.f.edit("a-ar.md", 'A["البداية"] --> B["النهاية"]', 'A["البداية"] --> C["النهاية"]')
        self.assertProblem(self.f.check(), "fences")

    def test_table_rows_and_columns(self) -> None:
        self.f.edit("a-ar.md", "| اتنين | 2 |\n", "")
        self.assertProblem(self.f.check(), "tables")

    def test_lists_and_checklists(self) -> None:
        self.f.edit("a-ar.md", "- [x] الفحص التاني\n", "")
        self.assertProblem(self.f.check(), "lists")
        self.f.edit("a-ar.md", "- [ ] الفحص الأول", "- الفحص الأول")
        self.assertProblem(self.f.check(), "lists")

    def test_images_links_and_admonitions(self) -> None:
        self.f.edit("a-ar.md", "![رسم](img/x.png)", "رسم")
        self.assertProblem(self.f.check(), "images")
        self.f.edit("a-ar.md", "[خارجي](https://example.com/x)", "خارجي")
        self.assertProblem(self.f.check(), "links")
        self.f.edit("a-ar.md", "> ملحوظة: خلّي بالك.", "ملحوظة: خلّي بالك.")
        self.assertProblem(self.f.check(), "blocks")

    def test_internal_link_must_point_to_the_arabic_page(self) -> None:
        self.f.edit("a-ar.md", "(b-ar.md)", "(b.md)")
        self.assertProblem(self.f.check(), "links", "a.md")

    def test_broken_internal_link_and_anchor(self) -> None:
        self.f.edit("a-ar.md", "(b-ar.md)", "(nope-ar.md)")
        self.assertProblem(self.f.check(), "anchors")
        self.f.edit("a-ar.md", "(nope-ar.md)", "(b-ar.md)")
        self.f.edit("a-ar.md", "(#الجزء-التاني)", "(#second-part)")
        self.assertProblem(self.f.check(), "anchors")

    def test_fix_anchors_rewrites_english_slugs(self) -> None:
        self.f.edit("a-ar.md", "(#الجزء-التاني)", "(#second-part)")
        self.assertEqual(v.Validator(self.f.root).fix_anchors("a.md"), 1)
        self.assertEqual(self.f.check(), {})

    def test_arrow_direction_is_preserved(self) -> None:
        self.f.edit("a.md", "Intro paragraph", "Intro A → B paragraph")
        self.f.edit("a-ar.md", "فقرة افتتاحية", "فقرة A → B افتتاحية")
        self.assertEqual(self.f.check(), {})
        self.f.edit("a-ar.md", "A → B", "A ← B")
        self.assertProblem(self.f.check(), "arrows")

    def test_credentials_are_rejected_in_either_language(self) -> None:
        self.f.edit("a.md", "Intro paragraph", "Intro paragraph with STORAGE_SECRET_KEY=REPLACE_ME and DB_PASSWORD=<set-on-server>")
        self.f.edit("a-ar.md", "فقرة افتتاحية", "فقرة افتتاحية STORAGE_SECRET_KEY=REPLACE_ME و DB_PASSWORD=<set-on-server>")
        self.assertEqual(self.f.check(), {})
        self.f.edit("a-ar.md", "DB_PASSWORD=<set-on-server>", "DB_PASSWORD=Sup3rSecretValue99")
        self.assertProblem(self.f.check(), "secrets")
        self.f.edit("a-ar.md", "DB_PASSWORD=Sup3rSecretValue99", "DB_PASSWORD=<set-on-server>")
        self.f.edit("a.md", "REPLACE_ME", "ghp_" + "a" * 30)
        self.assertProblem(self.f.check(), "secrets")

    def test_infrastructure_pages_need_evidence_labels(self) -> None:
        (self.f.hb / "08-infrastructure").mkdir()
        body = "# T\n\n" + META.format(m="x", lang="English · [العربية](p-ar.md)") + "\nClaim without a label.\n"
        body_ar = "# ت\n\n" + META.format(m="س", lang="[English](p.md) · العربية") + "\nادعاء من غير وسم.\n"
        self.f.write("08-infrastructure/p.md", body), self.f.write("08-infrastructure/p-ar.md", body_ar)
        self.assertProblem(self.f.check(), "evidence", "08-infrastructure/p.md")
        for name, text in (("p.md", "Claim (VERIFIED IN CODE)."), ("p-ar.md", "ادعاء (VERIFIED IN CODE).")):
            self.f.write("08-infrastructure/" + name, (self.f.hb / "08-infrastructure" / name).read_text().replace("Claim without a label.", text).replace("ادعاء من غير وسم.", text))
        self.assertEqual(self.f.check(), {})

    def test_links_that_leave_the_repository_are_rejected_not_read(self) -> None:
        self.f.edit("a-ar.md", "(b-ar.md)", "(../../../../../../etc/passwd)")
        self.assertProblem(self.f.check(), "anchors")
        self.f.edit("a-ar.md", "(../../../../../../etc/passwd)", "(../../../../../../etc/hostname#x)")
        self.assertProblem(self.f.check(), "anchors")

    def test_setext_headings_provide_anchors(self) -> None:
        t = v.heading_slugs
        p = self.f.hb / "setext.md"
        p.write_text("Title\n=====\n\nSecond part\n-----------\n", encoding="utf-8")
        self.assertEqual(t(p), ["title", "second-part"])

    def test_configure_resets_a_custom_language_marker(self) -> None:
        v.configure("docs/handbook", "-ar", True, (), "Lang")
        v.configure("docs/handbook", "-ar", True, (), None)
        self.assertIsNotNone(v.LANG_LINE_RE.search("> **Language / اللغة:** English · [x](y.md)"))

    def test_missing_directory_and_empty_set_never_pass(self) -> None:
        import subprocess as sp
        base = [sys.executable, str(Path(v.__file__)), "--root", str(self.f.root)]
        r = sp.run(base + ["--docs-dir", "does/not/exist"], capture_output=True, text=True)
        self.assertEqual(r.returncode, 2, r.stdout)
        self.assertNotIn("PARITY RESULT: PASS", r.stdout)
        empty = self.f.root / "docs" / "empty"
        empty.mkdir(parents=True)
        r = sp.run(base + ["--docs-dir", "docs/empty"], capture_output=True, text=True)
        self.assertEqual(r.returncode, 2, r.stdout)
        self.assertNotIn("PARITY RESULT: PASS", r.stdout)

    def test_symlinked_pages_outside_the_repository_are_not_read(self) -> None:
        outside = Path(tempfile.mkdtemp()) / "secret-ar.md"
        outside.write_text("# x\n\nDB_PASSWORD=Sup3rSecretValue99\n", encoding="utf-8")
        self.addCleanup(lambda: shutil.rmtree(outside.parent, ignore_errors=True))
        (self.f.hb / "b-ar.md").unlink()
        (self.f.hb / "b-ar.md").symlink_to(outside)
        problems = self.f.check(record=False)
        self.assertProblem(problems, "outside", "b.md")
        self.assertNotIn("Sup3rSecretValue99", json.dumps(problems))

    def test_external_symlinks_are_never_opened_by_sync_or_repair(self) -> None:
        import os
        outside_dir = Path(tempfile.mkdtemp())
        outside = outside_dir / "x-ar.md"
        outside.write_text("# x\n", encoding="utf-8")
        self.addCleanup(lambda: (os.chmod(outside, 0o600), shutil.rmtree(outside_dir, ignore_errors=True)))
        os.chmod(outside, 0)  # any attempt to open it raises PermissionError instead of silently passing
        (self.f.hb / "b-ar.md").unlink()
        (self.f.hb / "b-ar.md").symlink_to(outside)
        val = v.Validator(self.f.root)
        with contextlib.redirect_stdout(io.StringIO()):
            val.run()
            val.fix_anchors("b.md")
            val.update_sync()
        self.assertTrue(any("[outside]" in m for ms in val.problems.values() for m in ms))
        (self.f.hb / "b-ar.md").unlink()
        (self.f.hb / "b-ar.md").write_text(B_AR, encoding="utf-8")
        (self.f.root / v.EXCEPTIONS).symlink_to(outside)  # an external exceptions file must not crash initialisation
        self.assertTrue(any("[outside]" in m for ms in v.Validator(self.f.root).problems.values() for m in ms))

    def test_english_only_and_quoted_credentials_are_masked_in_every_diagnostic(self) -> None:
        self.f.edit("a.md", "`code_id`", "`code_id` and DB_PASSWORD=Sup3rSecretValue99")
        self.f.edit("a-ar.md", "فقرة افتتاحية", "فقرة افتتاحية و API_TOKEN='" + QUOTED + "'")
        problems = self.f.check()
        flat = json.dumps(problems, ensure_ascii=False)
        self.assertNotIn("Sup3rSecretValue99", flat)
        self.assertNotIn(QUOTED, flat)
        self.assertProblem(problems, "secrets")

    def test_update_sync_never_writes_through_an_external_symlink(self) -> None:
        outside = Path(tempfile.mkdtemp()) / "translation-sync.json"
        outside.write_text("{}", encoding="utf-8")
        self.addCleanup(lambda: shutil.rmtree(outside.parent, ignore_errors=True))
        (self.f.root / v.SYNC).symlink_to(outside)
        with contextlib.redirect_stdout(io.StringIO()):
            self.assertEqual(v.Validator(self.f.root).update_sync(), 2)
        self.assertEqual(outside.read_text(encoding="utf-8"), "{}")

    def test_quoted_credentials_with_spaces_and_extra_numbers(self) -> None:
        self.f.edit("a-ar.md", "فقرة افتتاحية", 'فقرة DB_PASSWORD="Synthetic Secret42" افتتاحية')
        problems = self.f.check()
        flat = json.dumps(problems, ensure_ascii=False)
        self.assertNotIn("Secret42", flat)
        self.assertNotIn("Synthetic", flat.replace("Synthetic Data", ""))
        self.f.edit("a-ar.md", 'DB_PASSWORD="Synthetic Secret42" ', "")
        self.assertEqual(self.f.check(), {})

    def test_punctuated_credential_fragments_never_reach_diagnostics(self) -> None:
        self.f.edit("a.md", "`code_id`", "`code_id` DB_PASSWORD=\"SomeSecret42!\"")
        problems = self.f.check()
        flat = json.dumps(problems, ensure_ascii=False)
        self.assertNotIn("SomeSecret42", flat)
        self.assertProblem(problems, "secrets")

    def test_credential_in_a_heading_never_appears_in_anchor_diagnostics(self) -> None:
        self.f.edit("a-ar.md", "## الجزء التاني", "## DB_PASSWORD=SomeSecret42!")
        self.f.edit("a-ar.md", "(#الجزء-التاني)", "(#wrong-anchor)")
        problems = self.f.check()
        flat = json.dumps(problems, ensure_ascii=False).lower()
        self.assertNotIn("somesecret42", flat)

    def test_bare_credential_names_are_detected_and_masked(self) -> None:
        for name in ("PASSWORD", "SECRET", "TOKEN", "KEY"):
            f = Fixture()
            self.addCleanup(f._t.cleanup)
            f.edit("a.md", "`code_id`", f"`code_id` {name}=SyntheticValue42")
            problems = f.check()
            self.assertIn("secrets", json.dumps(problems), name)
            self.assertNotIn("SyntheticValue42", json.dumps(problems), name)

    def test_credential_forms_with_spacing_case_and_json_are_detected_and_masked(self) -> None:
        forms = ["PASSWORD = SyntheticValue42", "password=SyntheticValue42", '"password": "SyntheticValue42"', "API_KEY='SyntheticValue42 x'"]
        for text in forms:
            f = Fixture()
            self.addCleanup(f._t.cleanup)
            f.edit("a.md", "`code_id`", f"`code_id` {text}")
            problems = f.check()
            self.assertIn("secrets", json.dumps(problems), text)
            self.assertNotIn("SyntheticValue42", json.dumps(problems), text)
        for harmless in ("key=value", "Secret: see section 3", "DB_PASSWORD=<set-on-server>", "STORAGE_SECRET_KEY=REPLACE_ME"):
            f = Fixture()
            self.addCleanup(f._t.cleanup)
            f.edit("a.md", "`code_id`", f"`code_id` {harmless}")
            f.edit("a-ar.md", "`code_id`", f"`code_id` {harmless}")
            self.assertNotIn("secrets", json.dumps(f.check()), harmless)

    def test_unused_exception_reasons_are_masked_in_printed_output(self) -> None:
        import subprocess as sp
        exc = self.f.root / v.EXCEPTIONS
        exc.write_text(json.dumps({"files": {"a.md": {"checks": {"tables": {"reason": "DB_PASSWORD=" + LEAK, "reviewed": "owner"}}}}}))
        r = sp.run([sys.executable, str(Path(v.__file__)), "--root", str(self.f.root), "--docs-dir", v.HB], capture_output=True, text=True)
        self.assertIn("unused exception", r.stdout)
        self.assertNotIn(LEAK, r.stdout + r.stderr)

    def test_escaped_and_unclosed_quotes_are_fully_masked(self) -> None:
        for text in ['"password": "Synthetic\\"LeakedSecret42"', 'API_KEY="Synthetic\\"LeakedSecret42 more', "PASSWORD='it\\'s LeakedSecret42'"]:
            f = Fixture()
            self.addCleanup(f._t.cleanup)
            f.edit("a.md", "`code_id`", f"`code_id` {text}")
            problems = f.check()
            self.assertNotIn("LeakedSecret42", json.dumps(problems), text)
            self.assertIn("secrets", json.dumps(problems), text)

    def test_repair_with_traversal_argument_writes_nothing(self) -> None:
        import subprocess as sp
        victim = self.f.root / "outside-ar.md"
        victim.write_text("# x\n[a](a.md#intro)\n", encoding="utf-8")
        (self.f.root / "outside.md").write_text("# x\n", encoding="utf-8")
        before = victim.read_text(encoding="utf-8")
        r = sp.run([sys.executable, str(Path(v.__file__)), "--root", str(self.f.root), "--docs-dir", v.HB, "--file", "../../outside.md", "--fix-anchors"],
                   capture_output=True, text=True)
        self.assertEqual(r.returncode, 2)
        self.assertEqual(victim.read_text(encoding="utf-8"), before)

    def test_headings_with_entities_slug_by_rendered_text(self) -> None:
        p = self.f.hb / "entities.md"
        p.write_text("# Build &amp; deploy\n\n## Build & deploy\n", encoding="utf-8")
        self.assertEqual(v.heading_slugs(p)[0], v.heading_slugs(p)[1].rsplit("-", 1)[0] if v.heading_slugs(p)[1].endswith("-1") else v.heading_slugs(p)[1])

    def test_file_option_rejects_traversal(self) -> None:
        val = v.Validator(self.f.root)
        val.run("../../etc/passwd.md")
        self.assertTrue(any("[outside]" in m for ms in val.problems.values() for m in ms))

    def test_credentials_in_inline_code_and_destinations_are_redacted_everywhere(self) -> None:
        self.f.edit("a.md", "`code_id`", "`DB_PASSWORD=Sup3rSecretValue99`")
        problems = self.f.check()
        self.assertProblem(problems, "inline-code")
        self.assertNotIn("Sup3rSecretValue99", json.dumps(problems, ensure_ascii=False))

    def test_credential_values_are_redacted_in_diagnostics(self) -> None:
        self.f.edit("a-ar.md", "فقرة افتتاحية", "فقرة DB_PASSWORD=Sup3rSecretValue99 افتتاحية")
        problems = self.f.check()
        self.assertProblem(problems, "secrets")
        self.assertNotIn("Sup3rSecretValue99", json.dumps(problems, ensure_ascii=False))

    def test_base_ref_cannot_inject_git_options(self) -> None:
        val = v.Validator(self.f.root)
        for bad in ("--output=probe", "-x", "a b", "main;rm"):
            with self.assertRaises(SystemExit):
                val.with_base(bad)

    def test_repeated_numbers_must_keep_their_count(self) -> None:
        self.f.edit("a.md", "and 42 items", "and 25 items, 25 more and 30 extra")
        self.f.edit("a-ar.md", "و 42 عنصر", "و 25 عنصر و 30 زيادة و 30 كمان")
        self.assertProblem(self.f.check(), "tokens")

    def test_require_metadata_rejects_missing_metadata_in_both_pages(self) -> None:
        for name in ("a.md", "a-ar.md"):
            t = (self.f.hb / name).read_text(encoding="utf-8")
            self.f.write(name, t.replace("> **Owner role:** Handbook navigation\n", "").replace("> **Status:** REFERENCE", "> Note"))
        self.assertProblem(self.f.check(), "metadata")

    def test_non_ascii_file_names_are_seen_by_the_same_pr_rule(self) -> None:
        r = self.f.root
        run = lambda *a: subprocess.run(["git", *a], cwd=r, check=True, capture_output=True, text=True)
        run("init", "-q", "-b", "main"), run("config", "user.email", "t@e"), run("config", "user.name", "t")
        self.f.write("دليل.md", B_EN.replace("b-ar.md", "دليل-ar.md")); self.f.write("دليل-ar.md", B_AR.replace("(b.md)", "(دليل.md)"))
        run("add", "-A"), run("commit", "-qm", "base"), run("checkout", "-qb", "feature")
        self.f.edit("دليل.md", "Text.", "Changed text.")
        run("commit", "-qam", "english only")
        val = v.Validator(r)
        val.with_base("main")
        self.assertProblem(val.problems, "same-pr", "دليل.md")

    def test_inline_code_identifier_drift(self) -> None:
        self.f.edit("a-ar.md", "`code_id`", "`codeId`")
        self.assertProblem(self.f.check(), "inline-code")

    def test_technical_token_and_number_drift(self) -> None:
        self.f.edit("a-ar.md", "TC-001", "TC-002")
        self.assertProblem(self.f.check(), "tokens")
        self.f.edit("a-ar.md", "TC-002", "TC-001")
        self.f.edit("a-ar.md", "42", "٤٢")  # Arabic-Indic digits are normalised, so this is still the same number
        self.assertEqual(self.f.check(), {})
        self.f.edit("a-ar.md", "٤٢", "24")
        self.assertProblem(self.f.check(), "tokens")

    def test_metadata_and_language_link(self) -> None:
        self.f.edit("a-ar.md", "REFERENCE — المعنى", "PLANNED — المعنى")
        self.assertProblem(self.f.check(), "metadata")
        self.f.edit("a-ar.md", "PLANNED — المعنى", "REFERENCE — المعنى")
        self.f.edit("a-ar.md", "[English](a.md) · ", "")
        self.assertProblem(self.f.check(), "language-link")

    def test_exceptions_need_reason_and_review_and_must_be_used(self) -> None:
        self.f.edit("a-ar.md", "| اتنين | 2 |\n", "")
        exc = self.f.root / v.EXCEPTIONS
        exc.write_text(json.dumps({"files": {"a.md": {"checks": {"tables": {"reason": "rendering", "reviewed": ""}, "tokens": {"reason": "x", "reviewed": "owner"}}}}}))
        self.assertProblem(self.f.check(), "tables")
        exc.write_text(json.dumps({"files": {"a.md": {"checks": {"tables": {"reason": "rendering", "reviewed": "owner 2026-10-21"}, "tokens": {"reason": "row removed", "reviewed": "owner 2026-10-21"}}}}}))
        self.assertEqual(self.f.check(), {})
        self.f.edit("a-ar.md", "| الاسم | القيمة |\n|---|---|\n| واحد | 1 |\n", "| الاسم | القيمة |\n|---|---|\n| واحد | 1 |\n| اتنين | 2 |\n")
        self.assertProblem(self.f.check(), "exceptions")  # the exception is now unused

    def test_sync_record_detects_english_change_without_arabic_update(self) -> None:
        self.assertEqual(self.f.check(), {})
        self.f.edit("a.md", "Intro paragraph", "Changed intro paragraph")
        problems = self.f.check(record=False)
        self.assertProblem(problems, "sync", "a.md")
        self.assertIn("English changed", " ".join(problems["a.md"]))
        with contextlib.redirect_stdout(io.StringIO()):
            refused = v.Validator(self.f.root).update_sync()
        self.assertEqual(refused, 1)  # refuses to bless an untranslated change
        self.f.edit("a-ar.md", "فقرة افتتاحية", "فقرة افتتاحية متغيّرة")
        self.assertEqual(self.f.check(), {})

    def test_sync_record_required_for_new_pairs(self) -> None:
        self.assertProblem(self.f.check(record=False), "sync")

    def test_same_pr_rule_uses_git_diff(self) -> None:
        r = self.f.root
        run = lambda *a: subprocess.run(["git", *a], cwd=r, check=True, capture_output=True, text=True)
        run("init", "-q", "-b", "main"), run("config", "user.email", "t@e"), run("config", "user.name", "t")
        run("add", "-A"), run("commit", "-qm", "base")
        run("checkout", "-qb", "feature")
        self.f.edit("b.md", "Text.", "Changed text.")
        run("commit", "-qam", "english only")
        val = v.Validator(r)
        val.with_base("main")
        self.assertProblem(val.problems, "same-pr", "b.md")
        self.f.edit("b-ar.md", "نص.", "نص متغيّر.")
        run("commit", "-qam", "arabic too")
        val = v.Validator(r)
        val.with_base("main")
        self.assertEqual(val.problems, {})


if __name__ == "__main__":
    unittest.main()
