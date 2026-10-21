#!/usr/bin/env python3
"""Tests for scripts/validate_handbook_parity.py. Each test builds a throw-away handbook (English page plus Arabic mirror).

They prove the structural checks detect the defects listed in the task: missing or orphan mirrors, heading, code-fence, table,
list, image, link and identifier drift, broken links, unsynchronised changes. They do not (and the validator does not) prove
semantic equivalence; that needs the separate human/independent review recorded in translation-parity-report.md.
"""
from __future__ import annotations

import contextlib
import io
import json
import subprocess
import sys
import tempfile
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
import validate_handbook_parity as v  # noqa: E402

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
