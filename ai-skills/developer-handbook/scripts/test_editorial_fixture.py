#!/usr/bin/env python3
"""Regression tests for the Egyptian Arabic editorial step.

- A literal translation and its corrected version both pass STRUCTURAL parity (so parity alone proves nothing about language quality),
  contain exactly the same code blocks, inline code, paths, commands and numbers, and differ only in wording.
- The phrase checker warns on the literal version and stays quiet on the corrected one; it has a positive and a negative sample for every
  phrase it knows, and the anti-pattern catalogue has a Bad and a Good example for every category.
- The checker never claims quality: it only warns and says so.
"""
from __future__ import annotations

import re
import shutil
import subprocess
import sys
import tempfile
import unittest
from pathlib import Path

HERE = Path(__file__).resolve().parent
SKILL = HERE.parent
FIX = SKILL / "examples" / "arabic-editorial-fixture"
sys.path.insert(0, str(HERE))
import check_arabic_style as style  # noqa: E402

SAMPLES = {  # id: (bad text that must warn, good text that must not)
    "A1-machine-readable": ("ملفات مقروء بالآلة", "ملفات قابلة للقراءة آليًا"),
    "A1-living-deliverable": ("الدليل مخرَج حيّ", "الدليل بيتحدّث باستمرار مع تطور المشروع"),
    "A1-gated-money": ("الفلوس الحقيقية متقفّلة", "التعاملات المالية الحقيقية مش مفعّلة حاليًا"),
    "A1-green-ci": ("الـ CI أخضر", "كل فحوصات الـ CI نجحت"),
    "A2-image-rollback": ("الـ Rollback للصور بس", "الرجوع لإصدار Docker Images سابق من غير استرجاع قاعدة البيانات"),
    "A2-schema-did-not-move": ("الـ Schema ماتحركتش", "مفيش تغيير في بنية أو إصدار الـ Database Schema"),
    "A3-typed": ("العميل المتكتّب", "Client بأنواع محددة (Typed)"),
    "A3-hashed": ("أكواد مُجزّأة", "أكواد متخزّنة كـ Hash"),
    "A3-seeded": ("منطقة مزروعة", "منطقة اتضافت بالـ Seed"),
    "A4-synthetic-wording": ("بيانات صناعية", "بيانات تجريبية غير حقيقية (Synthetic Data)"),
    "A6-negation-sh": ("التطبيق مابينادي الخدمة", "التطبيق مابيناديش الخدمة"),
    "A7-docker-images-bare": ("ارجع لصور Docker السابقة", "ارجع لصور الـ Container (Docker Images) السابقة"),
    "A7-decorative-english": ("الـ Step الأول", "الخطوة الأولى"),
    "A9-doubled-prefix": ("وومن غير ما تتعمل", "ومن غير ما تتعمل"),
    "A9-split-verb": ("الإقرار اتر فض", "الإقرار اترفض"),
    "A9-arabic-indic-digits": ("من ١ إلى ٣", "من 1 إلى 3"),
    "A9-digit-glued-to-prefix": ("الخطوات و3", "الخطوات من 1 إلى 3"),
    "A5-left-arrow": ("A ← B", "A → B"),
}


def protected(text: str):
    fences = re.findall(r"```.*?```", text, re.S)
    inline = re.findall(r"`[^`\n]+`", re.sub(r"```.*?```", "", text, flags=re.S))
    numbers = re.findall(r"\d+(?:\.\d+)*", re.sub(r"```.*?```|`[^`\n]+`", "", text, flags=re.S))
    return fences, inline, numbers


class EditorialFixture(unittest.TestCase):
    def read(self, rel: str) -> str:
        return (FIX / rel).read_text(encoding="utf-8")

    def structural(self, variant: str) -> subprocess.CompletedProcess:
        with tempfile.TemporaryDirectory() as t:
            docs = Path(t) / "docs" / "handbook"
            docs.mkdir(parents=True)
            shutil.copy(FIX / variant / "deploy-notes.md", docs)
            shutil.copy(FIX / variant / "deploy-notes-ar.md", docs)
            base = [sys.executable, str(HERE / "validate_bilingual_parity.py"), "--root", t, "--docs-dir", "docs/handbook"]
            subprocess.run(base + ["--update-sync"], capture_output=True, text=True)
            return subprocess.run(base, capture_output=True, text=True)

    def test_literal_and_corrected_versions_both_pass_structural_parity(self) -> None:
        for variant in ("before", "after"):
            r = self.structural(variant)
            self.assertEqual(r.returncode, 0, f"{variant}: {r.stdout}")
            self.assertIn("passing does not prove semantic equivalence", r.stdout)

    def test_the_correction_changes_wording_only(self) -> None:
        source, before, after = (self.read(p) for p in ("before/deploy-notes.md", "before/deploy-notes-ar.md", "after/deploy-notes-ar.md"))
        self.assertEqual(self.read("before/deploy-notes.md"), self.read("after/deploy-notes.md"))  # same English source in both variants
        self.assertNotEqual(before, after)
        self.assertEqual(protected(before), protected(after))
        self.assertEqual(protected(source)[0], protected(after)[0])  # identical code blocks
        self.assertEqual(protected(source)[1], protected(after)[1])  # identical inline code
        self.assertEqual(len(re.findall(r"\n\n", before)), len(re.findall(r"\n\n", after)))  # same block structure

    def test_checker_warns_on_the_literal_version_and_is_quiet_on_the_corrected_one(self) -> None:
        before = style.check(FIX / "before" / "deploy-notes-ar.md")
        after = style.check(FIX / "after" / "deploy-notes-ar.md")
        self.assertGreaterEqual(len({i for _, i, _, _ in before}), 6)
        self.assertEqual(after, [])
        self.assertEqual({c for _, _, c, _ in before} - {"A1", "A2", "A4", "A7"}, set())

    def test_every_phrase_has_a_positive_and_a_negative_sample(self) -> None:
        self.assertEqual(set(SAMPLES), {p[0] for p in style.PHRASES}, "add a Bad/Good sample for every phrase")
        for ident, (bad, good) in SAMPLES.items():
            with self.subTest(ident):
                rx = next(c for i, _, c, _ in style.COMPILED if i == ident)
                self.assertIsNotNone(rx.search(bad), f"{ident} should flag: {bad}")
                self.assertIsNone(rx.search(good), f"{ident} should not flag: {good}")

    def test_code_and_inline_code_are_ignored_by_the_checker(self) -> None:
        with tempfile.TemporaryDirectory() as t:
            p = Path(t) / "x-ar.md"
            p.write_text("```\nمقروء بالآلة\n```\n\nالأمر `مقروء بالآلة` ثابت.\n", encoding="utf-8")
            self.assertEqual(style.check(p), [])

    def test_checker_only_warns_unless_strict_and_never_claims_quality(self) -> None:
        script = str(HERE / "check_arabic_style.py")
        bad = str(FIX / "before")
        r = subprocess.run([sys.executable, script, bad], capture_output=True, text=True)
        self.assertEqual(r.returncode, 0)
        self.assertIn("NOT evidence of natural, correct or faithful Arabic", r.stdout)
        self.assertEqual(subprocess.run([sys.executable, script, "--strict", bad], capture_output=True, text=True).returncode, 1)
        self.assertEqual(subprocess.run([sys.executable, script, "--strict", str(FIX / "after")], capture_output=True, text=True).returncode, 0)

    def test_anti_pattern_catalogue_has_bad_and_good_for_every_category(self) -> None:
        text = (SKILL / "references" / "egyptian-arabic-anti-patterns.md").read_text(encoding="utf-8")
        sections = re.split(r"(?m)^## ", text)[1:]
        categories = [s for s in sections if re.match(r"A\d ", s)]
        self.assertGreaterEqual(len(categories), 9)
        for s in categories:
            title = s.splitlines()[0]
            self.assertRegex(s, r"(?m)^- Bad", title)
            self.assertRegex(s, r"(?m)^- Good", title)
            self.assertRegex(s, r"(?m)^- Why", title)
        for ident, _ in SAMPLES.items():
            cat = ident.split("-")[0]
            self.assertTrue(any(s.startswith(cat + " ") for s in categories), f"no catalogue category for {ident}")


if __name__ == "__main__":
    unittest.main()
