#!/usr/bin/env python3
"""Structural parity validator for the English Developer Handbook and its Egyptian Arabic mirror.

Every English page `X.md` under docs/developer-handbook/ must have exactly one `X-ar.md` in the same directory with the same
structure and the same protected technical content. The English page is the source of truth.

This is a STRUCTURAL check. It parses both files with markdown-it-py and compares headings, code fences (content included),
tables, lists, checklists, blockquotes, paragraphs, images, link destinations, inline-code identifiers and technical tokens.
It cannot prove that a sentence means the same thing in both languages; a passing run is never evidence of semantic parity
(see docs/developer-handbook/translation-parity-report.md for the separate human/independent semantic review).

Usage
  python3 scripts/validate_handbook_parity.py                       validate everything
  python3 scripts/validate_handbook_parity.py --file 01-business/order-lifecycle.md   one English page (self-check while translating)
  python3 scripts/validate_handbook_parity.py --fix-anchors          rewrite English-style link fragments to Arabic heading slugs
  python3 scripts/validate_handbook_parity.py --update-sync          record the reviewed English/Arabic pair hashes
  python3 scripts/validate_handbook_parity.py --update-report        rewrite the structural section of the parity report
  python3 scripts/validate_handbook_parity.py --base origin/develop  also require Arabic to change when English changes

Exceptions: docs/developer-handbook/translation-exceptions.json (each entry needs a reason and a reviewer note; unused entries fail).
Synchronisation record: docs/developer-handbook/translation-sync.json (sha256 of both files when the pair was last reviewed
together). Exit code 0 only when every page passes.
"""
from __future__ import annotations

import argparse
import collections
import hashlib
import json
import re
import subprocess
import sys
import urllib.parse
from pathlib import Path

try:
    from markdown_it import MarkdownIt
except ImportError:  # pragma: no cover
    sys.exit("markdown-it-py is required: python3 -m pip install 'markdown-it-py==3.0.0'")

sys.path.insert(0, str(Path(__file__).resolve().parent))
from doc001_check import anchors_of, slug, strip_fences, HEAD_RE  # noqa: E402

ROOT = Path(__file__).resolve().parent.parent
HB = "docs/developer-handbook"
EXCEPTIONS = f"{HB}/translation-exceptions.json"
SYNC = f"{HB}/translation-sync.json"
REPORT = f"{HB}/translation-parity-report.md"
EXCLUDED_FROM_MIRROR = {"translation-parity-report.md"}  # generated audit artifact, see translation-exceptions.json
LANG_LINE_RE = re.compile(r"^> \*\*Language / اللغة:\*\*.*$", re.M)
STATUS_LABELS = ("IMPLEMENTED", "PARTIALLY IMPLEMENTED", "PLANNED", "DISABLED", "DEPRECATED", "NOT VERIFIED", "REFERENCE")
STATUS_WORDS = {"IMPLEMENTED", "PLANNED", "DISABLED", "DEPRECATED", "VERIFIED", "REFERENCE", "PARTIALLY", "NOT_RUN", "BLOCKED"}
KNOWN_IDENTIFIERS = {"Africa/Cairo"}
ARABIC_DIGITS = str.maketrans("٠١٢٣٤٥٦٧٨٩", "0123456789")
TOKEN_RE = re.compile(r"(?<![A-Za-z0-9])(?:[A-Za-z][A-Za-z0-9]*(?:[-_./:#][A-Za-z0-9]+)+|[A-Za-z][A-Za-z0-9]*|\d+(?:[.,]\d+)*)(?![A-Za-z0-9])")


def protected_token(tok: str) -> bool:
    """Identifier-like text that must survive translation unchanged (not ordinary hyphenated or slashed prose)."""
    if re.fullmatch(r"\d+(?:[.,]\d+)*", tok):
        return True
    if "_" in tok:
        return True
    parts = [p for p in re.split(r"[-./:#]", tok) if p]
    if len(parts) > 1:
        if tok in KNOWN_IDENTIFIERS or all(re.fullmatch(r"[A-Z0-9]+", p) or re.search(r"\d", p) for p in parts):
            return True
        return bool("." in tok and re.search(r"\d", tok))
    return tok in STATUS_WORDS or bool(re.search(r"[a-z][A-Z]", tok)) or bool(re.search(r"[A-Za-z]\d|\d[A-Za-z]", tok))
CHECKS = ("headings", "fences", "tables", "lists", "blocks", "images", "links", "inline-code", "tokens", "metadata", "language-link",
          "anchors")


def md_parser() -> MarkdownIt:
    return MarkdownIt("commonmark").enable("table")


def norm(text: str) -> str:
    return "\n".join(line.rstrip() for line in text.replace("\r\n", "\n").split("\n")).strip() + "\n"


def sha(path: Path) -> str:
    return hashlib.sha256(norm(path.read_text(encoding="utf-8")).encode()).hexdigest()


class Features:
    """Everything the comparison needs from one parsed page."""

    def __init__(self, text: str) -> None:
        self.text = LANG_LINE_RE.sub("", text)
        toks = md_parser().parse(self.text)
        self.headings: list[tuple[int, str]] = []
        self.fences: list[tuple[str, str]] = []
        self.tables: list[tuple[int, int]] = []
        self.counts: collections.Counter = collections.Counter()
        self.images: list[str] = []
        self.links: list[str] = []
        self.code: collections.Counter = collections.Counter()
        self.tokens: collections.Counter = collections.Counter()
        self.checklist = 0
        rows = cols = 0
        in_table = False
        for i, t in enumerate(toks):
            if t.type == "heading_open":
                self.headings.append((int(t.tag[1]), toks[i + 1].content))
            elif t.type == "fence":
                self.fences.append((t.info.strip(), t.content))
            elif t.type == "code_block":
                self.fences.append(("<indented>", t.content))
            elif t.type == "table_open":
                in_table, rows, cols = True, 0, 0
            elif t.type == "tr_open" and in_table:
                rows += 1
            elif t.type in ("th_open", "td_open") and in_table and rows == 1:
                cols += 1
            elif t.type == "table_close":
                self.tables.append((rows, cols))
                in_table = False
            elif t.type in ("bullet_list_open", "ordered_list_open", "list_item_open", "blockquote_open", "paragraph_open",
                            "hr", "html_block"):
                self.counts[t.type] += 1
            elif t.type == "inline":
                if i >= 2 and toks[i - 2].type == "list_item_open" and re.match(r"\[[ xX]\]\s", t.content):
                    self.checklist += 1
                self._inline(t)
        self.tokens = self.tokens

    def _inline(self, t) -> None:
        prose = []
        for c in t.children or []:
            if c.type == "link_open":
                self.links.append(urllib.parse.unquote(c.attrGet("href") or ""))
            elif c.type == "image":
                self.images.append(urllib.parse.unquote(c.attrGet("src") or ""))
                prose.append(c.content)
            elif c.type == "code_inline":
                self.code[c.content] += 1
            elif c.type == "text":
                prose.append(c.content)
        for tok in TOKEN_RE.findall(" ".join(prose).translate(ARABIC_DIGITS)):
            if protected_token(tok):
                self.tokens[tok] += 1


def mermaid_skeleton(src: str) -> str:
    """Mermaid with human-readable labels removed, so labels may be translated while the graph stays identical."""
    kind = next((ln.split()[0] for ln in src.splitlines() if ln.strip() and not ln.strip().startswith("%%")), "")
    s = re.sub(r'"[^"\n]*"', '""', src)
    for _ in range(3):  # node shapes, innermost first
        s = re.sub(r"\[[^\[\]\n]*\]", "[]", s)
        s = re.sub(r"\(\([^()\n]*\)\)", "(())", s)
        s = re.sub(r"\([^()\n]*\)", "()", s)
        s = re.sub(r"\{[^{}\n]*\}", "{}", s)
    s = re.sub(r"\|[^|\n]*\|", "||", s)
    s = re.sub(r"(--|==|-\.)[ \t]+[^\n>=]*?[ \t]+(-->|--->|==>|\.->|---)", r"\1 \2", s)  # flowchart text on an edge
    s = re.sub(r"(?m)^(\s*subgraph\s+\S+)\s*\[\]", r"\1", s)
    s = re.sub(r"(?m)^(\s*(?:participant|actor)\s+\S+\s+as)\s.*$", r"\1", s)
    s = re.sub(r"(?m)^(\s*(?:title|section|alt|else|opt|loop|par|and|critical|option|break|rect))\b[^\n]*$", r"\1", s)
    if kind in ("sequenceDiagram", "stateDiagram", "stateDiagram-v2", "erDiagram"):
        s = re.sub(r"(?m)^(?!\s*%%)([^:\n]*):.*$", r"\1:", s)  # message / transition / relationship / note text after the colon
    return s


def en_pages(root: Path) -> list[str]:
    hb = root / HB
    return sorted(str(p.relative_to(hb)) for p in hb.rglob("*.md")
                  if not p.name.endswith("-ar.md") and p.name not in EXCLUDED_FROM_MIRROR)


def ar_pages(root: Path) -> list[str]:
    hb = root / HB
    return sorted(str(p.relative_to(hb)) for p in hb.rglob("*-ar.md"))


def ar_name(rel: str) -> str:
    return rel[:-3] + "-ar.md"


def heading_slugs(path: Path) -> list[str]:
    out: list[str] = []
    seen: set[str] = set()
    for _, line in strip_fences(path.read_text(encoding="utf-8")):
        m = HEAD_RE.match(line)
        if not m:
            continue
        base = slug(m.group(2))
        cand, n = base, 0
        while cand in seen:
            n += 1
            cand = f"{base}-{n}"
        seen.add(cand)
        out.append(cand)
    return out


class Validator:
    def __init__(self, root: Path) -> None:
        self.root = root
        self.hb = root / HB
        self.exc = self._load(EXCEPTIONS).get("files", {})
        self.used_exc: set[tuple[str, str]] = set()
        self.problems: dict[str, list[str]] = {}

    def _load(self, rel: str) -> dict:
        p = self.root / rel
        return json.loads(p.read_text(encoding="utf-8")) if p.is_file() else {}

    def fail(self, page: str, check: str, msg: str) -> None:
        allowed = self.exc.get(page, {}).get("checks", {})
        if check in allowed and allowed[check].get("reason", "").strip() and allowed[check].get("reviewed", "").strip():
            self.used_exc.add((page, check))
            return
        self.problems.setdefault(page, []).append(f"[{check}] {msg}")

    # ------------------------------------------------------------------ one pair
    def compare(self, rel: str) -> None:
        en_p, ar_p = self.hb / rel, self.hb / ar_name(rel)
        if not ar_p.is_file():
            self.fail(rel, "missing", f"no Arabic counterpart {ar_name(rel)}")
            return
        en_text, ar_text = en_p.read_text(encoding="utf-8"), ar_p.read_text(encoding="utf-8")
        en, ar = Features(en_text), Features(ar_text)
        if not ar_text.strip() or len(ar_text.strip()) < 0.2 * len(en_text.strip()):
            self.fail(rel, "headings", "Arabic file is empty or implausibly short")
        # headings
        if [h[0] for h in en.headings] != [h[0] for h in ar.headings]:
            self.fail(rel, "headings", f"heading levels differ: English {[h[0] for h in en.headings]} vs Arabic {[h[0] for h in ar.headings]}")
        # code fences: language identifiers and content
        if len(en.fences) != len(ar.fences):
            self.fail(rel, "fences", f"code block count differs: {len(en.fences)} vs {len(ar.fences)}")
        else:
            for n, ((ei, ec), (ai, ac)) in enumerate(zip(en.fences, ar.fences), 1):
                if ei != ai:
                    self.fail(rel, "fences", f"code block {n}: language '{ei}' vs '{ai}'")
                elif ei == "mermaid":
                    if mermaid_skeleton(ec) != mermaid_skeleton(ac):
                        self.fail(rel, "fences", f"mermaid block {n}: graph structure differs beyond translated labels")
                elif norm(ec) != norm(ac):
                    self.fail(rel, "fences", f"code block {n} ({ei or 'no language'}) differs from the English source")
        if en.tables != ar.tables:
            self.fail(rel, "tables", f"tables differ (rows, columns): {en.tables} vs {ar.tables}")
        for k in ("bullet_list_open", "ordered_list_open", "list_item_open"):
            if en.counts[k] != ar.counts[k]:
                self.fail(rel, "lists", f"{k}: {en.counts[k]} vs {ar.counts[k]}")
        if en.checklist != ar.checklist:
            self.fail(rel, "lists", f"checklist items: {en.checklist} vs {ar.checklist}")
        for k in ("blockquote_open", "paragraph_open", "hr", "html_block"):
            if en.counts[k] != ar.counts[k]:
                self.fail(rel, "blocks", f"{k}: {en.counts[k]} vs {ar.counts[k]}")
        if en.images != ar.images:
            self.fail(rel, "images", f"image destinations differ: {en.images} vs {ar.images}")
        # links: destinations map to the Arabic counterpart when the target is a mirrored handbook page
        exp = collections.Counter(self.expected_link(rel, h) for h in en.links)
        got = collections.Counter(self.base_link(h) for h in ar.links)
        if exp != got:
            miss, extra = exp - got, got - exp
            self.fail(rel, "links", f"link destinations differ; missing in Arabic: {sorted(miss)[:6]}; unexpected: {sorted(extra)[:6]}")
        if en.code != ar.code:
            miss, extra = en.code - ar.code, ar.code - en.code
            self.fail(rel, "inline-code", f"inline code identifiers differ; missing: {sorted(miss)[:6]}; unexpected: {sorted(extra)[:6]}")
        lost = [t for t in en.tokens if t not in ar.tokens]
        if lost:
            self.fail(rel, "tokens", f"technical tokens/numbers from English missing in Arabic: {sorted(lost)[:10]}")
        self.metadata(rel, en_text, ar_text)
        self.language_link(rel, en_text, ar_text)
        self.anchors(rel, en_text, ar_text)

    def base_link(self, href: str) -> str:
        return href.partition("#")[0] if not re.match(r"^[a-zA-Z][a-zA-Z0-9+.-]*:", href) else href

    def expected_link(self, rel: str, href: str) -> str:
        if re.match(r"^[a-zA-Z][a-zA-Z0-9+.-]*:", href) or href.startswith("//"):
            return href
        file_part = href.partition("#")[0]
        if not file_part:
            return ""
        dest = (self.hb / rel).parent.joinpath(file_part).resolve()
        try:
            r = str(dest.relative_to(self.hb.resolve()))
        except ValueError:
            return file_part
        if r.endswith(".md") and not r.endswith("-ar.md") and r not in EXCLUDED_FROM_MIRROR:
            return file_part[:-3] + "-ar.md"
        return file_part

    def metadata(self, rel: str, en_text: str, ar_text: str) -> None:
        def meta(t: str) -> tuple[str | None, str | None]:
            head = "\n".join(t.splitlines()[:16])
            role = re.search(r"^> \*\*Owner role:\*\* (.+)$", head, re.M)
            st = re.search(r"^> \*\*Status:\*\* (.+)$", head, re.M)
            label = next((l for l in sorted(STATUS_LABELS, key=len, reverse=True) if st and st.group(1).upper().startswith(l)), None)
            return (role.group(1).strip() if role else None, label)
        e, a = meta(en_text), meta(ar_text)
        if e[0] != a[0]:
            self.fail(rel, "metadata", f"Owner role differs: {e[0]!r} vs {a[0]!r}")
        if e[1] != a[1]:
            self.fail(rel, "metadata", f"Status label differs: {e[1]!r} vs {a[1]!r}")

    def language_link(self, rel: str, en_text: str, ar_text: str) -> None:
        en_name, ar_n = Path(rel).name, Path(ar_name(rel)).name
        en_line, ar_line = LANG_LINE_RE.search(en_text), LANG_LINE_RE.search(ar_text)
        if not ar_line or f"]({en_name})" not in ar_line.group(0):
            self.fail(rel, "language-link", f"Arabic page needs a '> **Language / اللغة:**' line linking to {en_name}")
        if not en_line or f"]({ar_n})" not in en_line.group(0):
            self.fail(rel, "language-link", f"English page needs a '> **Language / اللغة:**' line linking to {ar_n}")

    def anchors(self, rel: str, en_text: str, ar_text: str) -> None:
        """Every internal link in the Arabic page must resolve, and its anchor must be the SAME heading as the English link's."""
        ar_path = self.hb / ar_name(rel)
        en_links = [h for h in Features(en_text).links if not re.match(r"^[a-zA-Z][a-zA-Z0-9+.-]*:", h) and not h.startswith("//")]
        ar_links = [h for h in Features(ar_text).links if not re.match(r"^[a-zA-Z][a-zA-Z0-9+.-]*:", h) and not h.startswith("//")]
        for h in ar_links:
            file_part, _, frag = h.partition("#")
            dest = ar_path if not file_part else (ar_path.parent / file_part).resolve()
            if not dest.exists():
                self.fail(rel, "anchors", f"Arabic link does not resolve: {h}")
            elif frag and dest.suffix == ".md" and frag.lower() not in anchors_of(dest):
                self.fail(rel, "anchors", f"Arabic link anchor not found: {h}")
        en_path = self.hb / rel
        want: collections.Counter = collections.Counter()
        for h in en_links:
            file_part, _, frag = h.partition("#")
            if not frag:
                continue
            en_dest = en_path if not file_part else (en_path.parent / file_part).resolve()
            if not en_dest.is_file() or en_dest.suffix != ".md":
                continue
            slugs = heading_slugs(en_dest)
            if frag.lower() not in slugs:
                continue
            ar_dest = ar_path if not file_part else (ar_path.parent / self.expected_link(rel, file_part)).resolve()
            if not ar_dest.is_file():
                continue
            ar_slugs = heading_slugs(ar_dest)
            idx = slugs.index(frag.lower())
            if idx < len(ar_slugs):
                want[(self.expected_link(rel, file_part), ar_slugs[idx])] += 1
        have: collections.Counter = collections.Counter()
        for h in ar_links:
            file_part, _, frag = h.partition("#")
            if frag:
                have[(file_part, frag.lower())] += 1
        for key in want - have:
            self.fail(rel, "anchors", f"anchor should point to the equivalent Arabic heading: expected #{key[1]} in {key[0] or '(same page)'}")

    def fix_anchors(self, rel: str) -> int:
        """Rewrite fragments that still use the English heading slug to the slug of the equivalent Arabic heading."""
        ar_path = self.hb / ar_name(rel)
        if not ar_path.is_file():
            return 0
        lines = ar_path.read_text(encoding="utf-8").split("\n")
        outside = {n for n, _ in strip_fences("\n".join(lines))}
        changed = 0

        def repl(m: re.Match, here: Path) -> str:
            nonlocal changed
            file_part, frag = m.group(1), m.group(2)
            if re.match(r"^[a-zA-Z][a-zA-Z0-9+.-]*:", file_part):
                return m.group(0)
            ar_dest = here if not file_part else (here.parent / file_part).resolve()
            if not ar_dest.is_file() or ar_dest.suffix != ".md":
                return m.group(0)
            ar_slugs = heading_slugs(ar_dest)
            if frag.lower() in ar_slugs:
                return m.group(0)
            en_dest = ar_dest.with_name(ar_dest.name[:-6] + ".md")
            if not en_dest.is_file():
                return m.group(0)
            en_slugs = heading_slugs(en_dest)
            if frag.lower() in en_slugs and en_slugs.index(frag.lower()) < len(ar_slugs):
                changed += 1
                return f"]({file_part}#{ar_slugs[en_slugs.index(frag.lower())]})"
            return m.group(0)

        for i, line in enumerate(lines, 1):
            if i in outside:
                lines[i - 1] = re.sub(r"\]\(([^)\s#]*)#([^)\s]+)\)", lambda m: repl(m, ar_path), line)
        if changed:
            ar_path.write_text("\n".join(lines), encoding="utf-8")
        return changed

    # ------------------------------------------------------------------ whole tree
    def run(self, only: str | None = None) -> None:
        en, ar = en_pages(self.root), ar_pages(self.root)
        targets = [only] if only else en
        for rel in targets:
            self.compare(rel)
        if only is None:
            expected = {ar_name(r) for r in en}
            for a in ar:
                if a not in expected:
                    self.fail(a, "orphan", "Arabic page without an English source (renamed or removed English page?)")
            self.sync(en)
            for page, spec in self.exc.items():
                for check, why in spec.get("checks", {}).items():
                    if check not in CHECKS:
                        self.fail(page, "exceptions", f"unknown check '{check}' in the exception list")
                    elif (page, check) not in self.used_exc:
                        self.problems.setdefault(page, []).append(f"[exceptions] unused exception '{check}' (remove it): {why.get('reason', '')[:60]}")

    def sync(self, en: list[str]) -> None:
        rec = self._load(SYNC).get("pairs", {})
        for rel in en:
            if not (self.hb / ar_name(rel)).is_file():
                continue
            now = {"en": sha(self.hb / rel), "ar": sha(self.hb / ar_name(rel))}
            old = rec.get(rel)
            if not old:
                self.fail(rel, "sync", "pair is not recorded in translation-sync.json; review both files together, then run --update-sync")
            elif old["en"] != now["en"] and old["ar"] == now["ar"]:
                self.fail(rel, "sync", "English changed after the Arabic was last reviewed; update the Arabic page in the same PR")
            elif old != now:
                self.fail(rel, "sync", "pair changed since translation-sync.json was recorded; re-review and run --update-sync")
        for rel in rec:
            if rel not in en:
                self.problems.setdefault(rel, []).append("[sync] recorded in translation-sync.json but the English page no longer exists")

    def update_sync(self) -> int:
        old = self._load(SYNC).get("pairs", {})
        pairs = {}
        for rel in en_pages(self.root):
            if not (self.hb / ar_name(rel)).is_file():
                continue
            now = {"en": sha(self.hb / rel), "ar": sha(self.hb / ar_name(rel))}
            if rel in old and old[rel]["en"] != now["en"] and old[rel]["ar"] == now["ar"]:
                print(f"REFUSED {rel}: English changed but the Arabic page did not; translate the change first")
                return 1
            pairs[rel] = now
        (self.root / SYNC).write_text(json.dumps({"schema_version": 1, "note": "sha256 of both files when the pair was last reviewed together; "
                                                  "maintained by scripts/validate_handbook_parity.py --update-sync", "pairs": pairs},
                                                 indent=1, ensure_ascii=False, sort_keys=True) + "\n", encoding="utf-8")
        print(f"recorded {len(pairs)} pairs in {SYNC}")
        return 0

    def with_base(self, base: str) -> None:
        """Require Arabic to change whenever English changes in this PR (same-PR update rule)."""
        out = subprocess.run(["git", "diff", "--name-status", "--no-renames", f"{base}...HEAD", "--", HB], cwd=self.root,
                             capture_output=True, text=True, check=True).stdout
        changed = {line.split("\t", 1)[1]: line[0] for line in out.splitlines() if "\t" in line}
        for path, st in changed.items():
            rel = path[len(HB) + 1:]
            if not rel.endswith(".md") or rel.endswith("-ar.md") or rel in EXCLUDED_FROM_MIRROR:
                continue
            if st != "D" and f"{HB}/{ar_name(rel)}" not in changed:
                self.fail(rel, "same-pr", "English page changed without a change to its Arabic counterpart in the same PR")
            if st == "D" and f"{HB}/{ar_name(rel)}" not in changed:
                self.fail(rel, "same-pr", "English page removed but its Arabic counterpart was not removed in the same PR")


def structural_report(v: Validator, en: list[str]) -> str:
    rows = ["| English page | Arabic page | Structural result |", "|---|---|---|"]
    for rel in en:
        ok = rel not in v.problems
        rows.append(f"| `{HB}/{rel}` | `{HB}/{ar_name(rel)}` | {'PASS' if ok else 'FAIL'} |")
    return "\n".join(rows)


def main() -> int:
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--root", default=str(ROOT))
    ap.add_argument("--file", help="English page relative to the handbook directory")
    ap.add_argument("--base", help="git base ref: require same-PR Arabic updates")
    ap.add_argument("--fix-anchors", action="store_true", help="rewrite English-style fragments in Arabic links to the Arabic heading slugs")
    ap.add_argument("--update-sync", action="store_true")
    ap.add_argument("--update-report", action="store_true")
    a = ap.parse_args()
    root = Path(a.root).resolve()
    v = Validator(root)
    if a.update_sync:
        return v.update_sync()
    if a.fix_anchors:
        n = sum(v.fix_anchors(r) for r in ([a.file] if a.file else en_pages(root)))
        print(f"rewrote {n} link anchor(s)")
    v.run(a.file)
    if a.base:
        v.with_base(a.base)
    en = [a.file] if a.file else en_pages(root)
    if a.update_report:
        p = root / REPORT
        text = p.read_text(encoding="utf-8")
        block = f"<!-- STRUCTURAL:BEGIN -->\n{structural_report(v, en)}\n<!-- STRUCTURAL:END -->"
        text = re.sub(r"<!-- STRUCTURAL:BEGIN -->.*?<!-- STRUCTURAL:END -->", lambda _m: block, text, flags=re.S)
        p.write_text(text, encoding="utf-8")
    for page in sorted(v.problems):
        for msg in v.problems[page]:
            print(f"FAIL  {page}: {msg}")
    failing = len(v.problems)
    print(f"PARITY RESULT: {'FAIL' if failing else 'PASS'} ({len(en)} English page(s) checked, {failing} with problems). "
          "Structural only: passing does not prove semantic equivalence.")
    return 1 if failing else 0


if __name__ == "__main__":
    sys.exit(main())
