#!/usr/bin/env python3
"""Lightweight phrase checker for known recurring Egyptian Arabic documentation mistakes (part of the developer-handbook skill).

It lists WARNINGS for phrases catalogued in references/egyptian-arabic-anti-patterns.md, outside fenced code and inline code. It is a
reading aid, not a quality measure: a clean run does NOT mean the Arabic is natural, correct or faithful, and a warning is not
automatically wrong (judge it in context). It never rewrites anything. It exits 0 unless --strict is given and warnings exist.
Natural style, grammar, clarity and technical meaning need the native-style editorial review and the semantic review.

Usage: python3 check_arabic_style.py [--strict] <file-or-directory> [...]
"""
from __future__ import annotations

import argparse
import re
import sys
from pathlib import Path

# (id, anti-pattern category, regex, hint)
PHRASES = [
    ("A1-machine-readable", "A1", r"مقروء(?:ة)?\s+بالآلة", "word-for-word 'machine-readable'; prefer 'قابلة للقراءة آليًا'"),
    ("A1-living-deliverable", "A1", r"مخرَ?ج\s+حي", "word-for-word 'living deliverable'; say it is updated continuously as the project evolves"),
    ("A1-gated-money", "A1", r"(?:الفلوس|فلوس)\s+(?:الحقيقية\s+)?متقفّ?ل", "word-for-word 'money is gated'; say what is (not) enabled"),
    ("A1-green-ci", "A1", r"(?:الـ?\s*CI\s+أخضر|أخضر\s+الـ?\s*CI)", "word-for-word 'CI is green'; prefer 'كل فحوصات الـ CI نجحت'"),
    ("A2-image-rollback", "A2", r"الـ?\s*Rollback\s+للصور", "compressed phrase loses the contrast with a database restore"),
    ("A2-schema-did-not-move", "A2", r"ماتحركتش", "vague; say whether the structure or version of the schema changed"),
    ("A3-typed", "A3", r"متكتّ?ب", "'typed' means type-checked, not 'written'"),
    ("A3-hashed", "A3", r"مُ?جزّ?أ(?:ة)?", "'hashed' is a hash, not 'split into pieces'"),
    ("A3-seeded", "A3", r"مزروع(?:ة|ين|ات)?", "'seeded' means loaded from seed data"),
    ("A4-synthetic-wording", "A4", r"\bصناعي(?:ة|ين)?\b", "for synthetic test data prefer 'بيانات تجريبية غير حقيقية (Synthetic Data)'"),
    ("A6-negation-sh", "A6", r"مابين[ا]دي(?!ش)", "Egyptian negation needs the final ش"),
    ("A7-docker-images-bare", "A7", r"صور\s+(?:الـ?\s*)?Docker\b(?!\s+Images)", "'صور' alone does not tell the reader these are container images"),
    ("A7-decorative-english", "A7", r"الـ\s*Step\b|الـ\s*Check\b", "ordinary words should stay Arabic ('الخطوة'، 'الفحص')"),
    ("A9-doubled-prefix", "A9", r"وومن\b|ووفي\b", "doubled prefix"),
    ("A9-split-verb", "A9", r"اتر\s+فض|اتن\s+فذ", "verb split by a space"),
    ("A9-arabic-indic-digits", "A9", r"[٠-٩]", "use Western digits"),
    ("A9-digit-glued-to-prefix", "A9", r"(?<![\w\d])و\d", "number glued to the Arabic 'و'; separate or reword"),
    ("A5-left-arrow", "A5", r"←", "an arrow must keep the source direction (→)"),
    ("A10-fact-as-prohibition", "A10", r"ما(?:ت|ا)(?:جرّ?ب|نفّ?ذ|ختبر)ش\s+هنا", "check: does the English report a fact ('not tested/done here') or give an order? 'مااتجرّبش' states a fact"),
]
COMPILED = [(i, c, re.compile(p), h) for i, c, p, h in PHRASES]


def prose_lines(text: str):
    fence = None
    for n, line in enumerate(text.splitlines(), 1):
        m = re.match(r"^ {0,3}(`{3,}|~{3,})", line)
        if fence is None and m:
            fence = m.group(1)
            continue
        if fence is not None:
            if m and m.group(1)[0] == fence[0] and len(m.group(1)) >= len(fence):
                fence = None
            continue
        yield n, re.sub(r"`[^`\n]*`", " ", line)


def check(path: Path) -> list[tuple[int, str, str, str]]:
    out = []
    for n, line in prose_lines(path.read_text(encoding="utf-8")):
        for ident, cat, rx, hint in COMPILED:
            if rx.search(line):
                out.append((n, ident, cat, hint))
    return out


def main() -> int:
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("paths", nargs="+")
    ap.add_argument("--strict", action="store_true", help="exit 1 when any warning exists")
    a = ap.parse_args()
    files: list[Path] = []
    for p in map(Path, a.paths):
        files += sorted(p.rglob("*-ar.md")) if p.is_dir() else [p]
    total = 0
    for f in files:
        for n, ident, cat, hint in check(f):
            total += 1
            print(f"WARN  {f}:{n}: [{cat}] {ident}: {hint}")
    print(f"STYLE WARNINGS: {total} in {len(files)} file(s). Warnings only; a clean result is NOT evidence of natural, correct or faithful Arabic.")
    return 1 if (a.strict and total) else 0


if __name__ == "__main__":
    sys.exit(main())
