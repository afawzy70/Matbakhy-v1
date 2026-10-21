#!/usr/bin/env python3
"""Structural parity validator for a bilingual documentation tree (default: English source, Arabic mirror `X-ar.md`).

Part of the `developer-handbook` skill. Every source page `X.md` under the documentation directory must have exactly one mirror
`X<suffix>.md` in the same directory with the same structure and the same protected technical content; the source language is
authoritative. Parsing uses markdown-it-py (python3 -m pip install 'markdown-it-py==3.0.0').

It is a STRUCTURAL check. Numbers are checked for omission and for a reduced count; extra numbers in the mirror are not flagged (spelled-out numbers in the source make that too noisy), so the semantic review must look for added values. It compares headings, code fences (content included), Mermaid structure with labels masked, tables, lists,
checklists, blockquotes, paragraphs, images, link destinations (internal links must target the mirror and the equivalent heading),
inline-code identifiers, protected technical tokens and numbers, arrow direction, the language-switch line, a reviewed-pair record and
justified exceptions. It can NOT prove that a sentence means the same in both languages: pair it with a side-by-side semantic review.

Usage (run from the repository root)
  python3 validate_bilingual_parity.py --docs-dir docs/handbook                  validate everything
  python3 validate_bilingual_parity.py --docs-dir docs/handbook --file a/b.md      one source page (path relative to the docs dir)
  ... --fix-anchors | --update-sync | --update-report | --base origin/<dev-branch>
Options: --suffix=-ar (mirror suffix; use the = form because the value starts with a dash), --require-metadata (Owner role / Status lines must match), --evidence-dir <subdir> (pages under it
must carry evidence-class labels), --lang-marker "<text>" (language-switch line prefix).
Files kept next to the docs: translation-exceptions.json (every exception needs a reason and a reviewer note; unused ones fail),
translation-sync.json (sha256 of both files when the pair was last reviewed together), translation-parity-report.md (generated block).
Exit code 0 only when every page passes.
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

HEAD_RE = re.compile(r"^(#{1,6})\s+(.*?)\s*#*\s*$")
FENCE_OPEN_RE = re.compile(r"^ {0,3}(`{3,}|~{3,})(.*)$")


def strip_fences(text: str) -> list[tuple[int, str]]:
    """Lines outside fenced code blocks (a fence closes only with the same character and at least the same length)."""
    out, opener = [], None
    for i, line in enumerate(text.splitlines(), 1):
        m = FENCE_OPEN_RE.match(line)
        if opener is None:
            if m and not (m.group(1)[0] == "`" and "`" in m.group(2)):
                opener = m.group(1)
                continue
            out.append((i, line))
        elif m and m.group(1)[0] == opener[0] and len(m.group(1)) >= len(opener) and not m.group(2).strip():
            opener = None
    return out


def slug(heading: str) -> str:
    heading = re.sub(r"(?<![\w])([*_]{1,3})(\S(?:.*?\S)?)\1(?![\w])", r"\2", heading)
    h = re.sub(r"<[^>]+>", "", heading)
    h = re.sub(r"\[([^\]]*)\]\([^)]*\)", r"\1", h)
    h = h.replace("`", "").strip().lower()
    h = re.sub(r"[^\w\- ]", "", h, flags=re.UNICODE)
    return h.replace(" ", "-")


def heading_texts(text: str) -> list[str]:
    """Heading contents as the Markdown parser sees them (ATX and Setext headings; never headings inside code fences)."""
    from markdown_it import MarkdownIt
    toks = MarkdownIt("commonmark").enable("table").parse(text)
    return [redact(toks[i + 1].content) for i, t in enumerate(toks) if t.type == "heading_open"]  # credentials never become slugs


def dedup_slugs(texts: list[str]) -> list[str]:
    out: list[str] = []
    seen: set[str] = set()
    for t in texts:
        base = slug(t)
        cand, n = base, 0
        while cand in seen:
            n += 1
            cand = f"{base}-{n}"
        seen.add(cand)
        out.append(cand)
    return out


def anchors_of(path: Path) -> set[str]:
    text = path.read_text(encoding="utf-8", errors="replace")
    out: set[str] = set(dedup_slugs(heading_texts(text)))
    for m in re.finditer(r"<a\s+(?:name|id)=[\"']([^\"']+)[\"']", text, re.I):
        out.add(m.group(1).lower())
    return out


HB = "docs/handbook"  # documentation directory relative to the repository root (set with --docs-dir)
LANG_SUFFIX = "-ar"  # mirror suffix before ".md" (set with --suffix)
REQUIRE_METADATA = False  # --require-metadata: "Owner role" and "Status" lines must match between languages
EVIDENCE_DIRS: tuple[str, ...] = ()  # --evidence-dir: pages under these sub-directories must label claims with evidence classes
EXCEPTIONS = f"{HB}/translation-exceptions.json"
SYNC = f"{HB}/translation-sync.json"
REPORT = f"{HB}/translation-parity-report.md"
EXCLUDED_FROM_MIRROR = {"translation-parity-report.md"}  # generated audit artifact, see translation-exceptions.json


def configure(docs_dir: str, suffix: str, require_metadata: bool, evidence_dirs: tuple[str, ...], lang_marker: str | None) -> None:
    global HB, LANG_SUFFIX, REQUIRE_METADATA, EVIDENCE_DIRS, EXCEPTIONS, SYNC, REPORT, LANG_LINE_RE
    HB, LANG_SUFFIX, REQUIRE_METADATA, EVIDENCE_DIRS = docs_dir.strip("/"), suffix, require_metadata, evidence_dirs
    EXCEPTIONS, SYNC, REPORT = f"{HB}/translation-exceptions.json", f"{HB}/translation-sync.json", f"{HB}/translation-parity-report.md"
    LANG_LINE_RE = re.compile(r"^> \*\*" + re.escape(lang_marker) + r"\*\*.*$", re.M) if lang_marker else DEFAULT_LANG_LINE_RE
DEFAULT_LANG_LINE_RE = re.compile(r"^> \*\*Language / اللغة:\*\*.*$", re.M)
LANG_LINE_RE = DEFAULT_LANG_LINE_RE
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
ARROW_RE = re.compile(r"→|←|↔|⇒|⇐|⇔|=>|->|<-")
EVIDENCE_RE = re.compile(r"VERIFIED LIVE|VERIFIED IN CODE|DOCUMENTED ONLY|NOT VERIFIED|OUTDATED / INCORRECT")
SECRET_PATTERNS = (
    re.compile(r"-----BEGIN [A-Z ]*PRIVATE KEY-----"),
    re.compile(r"\bgh[pousr]_[A-Za-z0-9]{20,}|\bgithub_pat_[A-Za-z0-9_]{20,}|\bAKIA[0-9A-Z]{16}\b"),
)
_CRED_NAME = r"(?:[A-Z0-9_]*(?:PASSWORD|SECRET|TOKEN|KEY|PEPPER)[A-Z0-9_]*|[\w-]*(?i:password|passwd|secret|token|api[_-]?key|private[_-]?key)[\w-]*)"
_CRED_VALUE = r"(\"[^\"\n]*\"|'[^'\n]*'|[^\s#`]+)"
# NAME=value, NAME = value (any spacing) and the JSON form "name": "value"; group 1 is the value in the first form, group 2 in the second
SECRET_ASSIGN_RE = re.compile(r"\b" + _CRED_NAME + r"[ \t]*=[ \t]*" + _CRED_VALUE + r"|\"" + _CRED_NAME + r"\"[ \t]*:[ \t]*(\"[^\"\n]*\")")
SAFE_SECRET_VALUE_RE = re.compile(r"^(?:<|\$|REPLACE|\.\.\.|\*{3}|changeme|x{3,}|\{|%|\[)", re.I)


def unsafe_secret_value(raw: str) -> str | None:
    """The assigned value (quotes stripped) when it looks like a real credential, else None (placeholders are fine)."""
    v = raw.strip("\"'")
    if not v or SAFE_SECRET_VALUE_RE.match(v) or v.lower() in {"no", "yes", "true", "false", "none", "off", "on", "null", "0", "1"}:
        return None  # placeholders and plain option values (for example a configuration flag set to "no") are not credentials
    return v
CHECKS = ("outside", "same-pr", "sync", "missing", "orphan", "exceptions", "evidence", "secrets", "arrows", "headings", "fences", "tables", "lists", "blocks", "images", "links", "inline-code", "tokens", "metadata", "language-link",
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
        self.text = redact(LANG_LINE_RE.sub("", text))  # credential values never enter the extracted features, so no diagnostic can print a fragment of one
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
        self.arrows: collections.Counter = collections.Counter()
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
        self.arrows.update(ARROW_RE.findall(" ".join(prose)))
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
                  if not p.name.endswith(LANG_SUFFIX + ".md") and p.name not in EXCLUDED_FROM_MIRROR)


def ar_pages(root: Path) -> list[str]:
    hb = root / HB
    return sorted(str(p.relative_to(hb)) for p in hb.rglob("*" + LANG_SUFFIX + ".md"))


def ar_name(rel: str) -> str:
    return rel[:-3] + LANG_SUFFIX + ".md"


def heading_slugs(path: Path) -> list[str]:
    return dedup_slugs(heading_texts(path.read_text(encoding="utf-8")))


def redact(text: str) -> str:
    """Diagnostics go to CI logs: never print credential-shaped values, whatever check found them."""
    for pat in SECRET_PATTERNS:
        text = pat.sub("<redacted>", text)
    return SECRET_ASSIGN_RE.sub(lambda m: re.sub(r"(=[ \t]*|:[ \t]*)(\"[^\"\n]*\"|'[^'\n]*'|[^\s#`]+)$", r"\1<redacted>", m.group(0)) if unsafe_secret_value(m.group(1) or m.group(2)) else m.group(0), text)


class Validator:
    def __init__(self, root: Path) -> None:
        self.root = root
        self.hb = root / HB
        self.used_exc: set[tuple[str, str]] = set()
        self.secret_values: set[str] = set()
        self.problems: dict[str, list[str]] = {}
        self.exc = self._load(EXCEPTIONS).get("files", {})

    def _load(self, rel: str) -> dict:
        p = self.root / rel
        if p.is_file() and not self.inside(p):
            self.problems.setdefault(rel, []).append("[outside] file resolves outside the repository and is not read")
            return {}
        return json.loads(p.read_text(encoding="utf-8")) if p.is_file() else {}

    def mask(self, msg: str) -> str:
        """Final masking applied to everything that is printed: pattern-based redaction plus every credential value seen in any page."""
        msg = redact(msg)
        for sv in self.secret_values:  # a value found in any page is masked wherever a diagnostic would print it (tokens, links, code, slugs)
            for variant in {sv, re.sub(r"[^\w]", "", sv)}:
                if len(variant) >= 4:
                    msg = re.sub(re.escape(variant), "<redacted>", msg, flags=re.I)
        return msg

    def fail(self, page: str, check: str, msg: str) -> None:
        allowed = self.exc.get(page, {}).get("checks", {})
        if check in allowed and allowed[check].get("reason", "").strip() and allowed[check].get("reviewed", "").strip():
            self.used_exc.add((page, check))
            return
        self.problems.setdefault(page, []).append(f"[{check}] {self.mask(msg)}")

    # ------------------------------------------------------------------ one pair
    def compare(self, rel: str) -> None:
        en_p, ar_p = self.hb / rel, self.hb / ar_name(rel)
        for p in (en_p, ar_p):
            if p.exists() and not self.inside(p):
                self.fail(rel, "outside", f"{p.name} resolves outside the repository (symlink or path traversal); it is not read")
                return
        if not ar_p.is_file():
            self.fail(rel, "missing", f"no Arabic counterpart {ar_name(rel)}")
            return
        en_text, ar_text = en_p.read_text(encoding="utf-8"), ar_p.read_text(encoding="utf-8")
        for t in (en_text, ar_text):
            for m in SECRET_ASSIGN_RE.finditer(t):
                sv = unsafe_secret_value(m.group(1) or m.group(2))
                if sv:  # mask the whole value and every fragment of it that a token diagnostic could print on its own
                    self.secret_values.add(sv)
                    self.secret_values.update(w for w in re.split(r"\s+", sv) if len(w) >= 4)
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
        if en.arrows != ar.arrows:
            self.fail(rel, "arrows", f"arrow characters differ (direction matters): {dict(en.arrows)} vs {dict(ar.arrows)}")
        if en.code != ar.code:
            miss, extra = en.code - ar.code, ar.code - en.code
            self.fail(rel, "inline-code", f"inline code identifiers differ; missing: {sorted(miss)[:6]}; unexpected: {sorted(extra)[:6]}")
        lost = [t for t, c in en.tokens.items() if (ar.tokens[t] < c if re.fullmatch(r"\d{2,}(?:[.,]\d+)*|\d+[.,]\d+(?:[.,]\d+)*", t) else t not in ar.tokens)]  # multi-digit numbers keep their count; single digits are often spelled out in Arabic
        if lost:
            self.fail(rel, "tokens", f"technical tokens/numbers from English missing in Arabic: {sorted(lost)[:10]}")
        if REQUIRE_METADATA:
            self.metadata(rel, en_text, ar_text)
        self.secrets(rel, en_text, ar_text)
        if EVIDENCE_DIRS and rel.startswith(EVIDENCE_DIRS):
            en_ev, ar_ev = collections.Counter(EVIDENCE_RE.findall(en_text)), collections.Counter(EVIDENCE_RE.findall(ar_text))
            if not en_ev:
                self.fail(rel, "evidence", "infrastructure pages must label their claims with the evidence classes (VERIFIED LIVE, VERIFIED IN CODE, DOCUMENTED ONLY, NOT VERIFIED, OUTDATED / INCORRECT)")
            elif en_ev != ar_ev:
                self.fail(rel, "evidence", f"evidence labels differ between languages (they stay in English): {dict(en_ev)} vs {dict(ar_ev)}")
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
        if r.endswith(".md") and not r.endswith(LANG_SUFFIX + ".md") and r not in EXCLUDED_FROM_MIRROR:
            return file_part[:-3] + LANG_SUFFIX + ".md"
        return file_part

    def secrets(self, rel: str, en_text: str, ar_text: str) -> None:
        """No credentials in the public repository: private keys, token shapes and NAME=value secret assignments with a real-looking value."""
        for lang, text in (("English", en_text), ("Arabic", ar_text)):
            kind = None
            if any(pat.search(text) for pat in SECRET_PATTERNS):
                kind = "a private key header or a token-shaped string"
            else:
                m = next((m for m in SECRET_ASSIGN_RE.finditer(text) if unsafe_secret_value(m.group(1) or m.group(2))), None)
                if m:
                    kind = "a real-looking value assigned to a credential-named variable or key"
            if kind:  # never echo the value: diagnostics are printed to CI logs
                self.fail(rel, "secrets", f"{lang} page contains something that looks like a credential ({kind}); value redacted")

    def metadata(self, rel: str, en_text: str, ar_text: str) -> None:
        def meta(t: str) -> tuple[str | None, str | None]:
            head = "\n".join(t.splitlines()[:16])
            role = re.search(r"^> \*\*Owner role:\*\* (.+)$", head, re.M)
            st = re.search(r"^> \*\*Status:\*\* (.+)$", head, re.M)
            label = next((l for l in sorted(STATUS_LABELS, key=len, reverse=True) if st and st.group(1).upper().startswith(l)), None)
            return (role.group(1).strip() if role else None, label)
        e, a = meta(en_text), meta(ar_text)
        for lang, m in (("English", e), ("Arabic", a)):
            if m[0] is None or m[1] is None:
                self.fail(rel, "metadata", f"{lang} page lacks a parsable 'Owner role' line or a recognised 'Status' label")
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

    def inside(self, p: Path) -> bool:
        """Link targets are untrusted input: only paths that stay inside the repository (after resolving symlinks) may be read."""
        try:
            p.resolve().relative_to(self.root.resolve())
            return True
        except ValueError:
            return False

    def anchors(self, rel: str, en_text: str, ar_text: str) -> None:
        """Every internal link in the Arabic page must resolve, and its anchor must be the SAME heading as the English link's."""
        ar_path = self.hb / ar_name(rel)
        en_links = [h for h in Features(en_text).links if not re.match(r"^[a-zA-Z][a-zA-Z0-9+.-]*:", h) and not h.startswith("//")]
        ar_links = [h for h in Features(ar_text).links if not re.match(r"^[a-zA-Z][a-zA-Z0-9+.-]*:", h) and not h.startswith("//")]
        for h in ar_links:
            file_part, _, frag = h.partition("#")
            dest = ar_path if not file_part else (ar_path.parent / file_part).resolve()
            if not self.inside(dest):
                self.fail(rel, "anchors", f"Arabic link leaves the repository: {h}")
            elif not dest.exists():
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
            if not self.inside(en_dest) or not en_dest.is_file() or en_dest.suffix != ".md":
                continue
            slugs = heading_slugs(en_dest)
            if frag.lower() not in slugs:
                continue
            ar_dest = ar_path if not file_part else (ar_path.parent / self.expected_link(rel, file_part)).resolve()
            if not self.inside(ar_dest) or not ar_dest.is_file():
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
        if not ar_path.is_file() or not self.inside(ar_path):
            return 0  # never read or rewrite a file that resolves outside the repository
        lines = ar_path.read_text(encoding="utf-8").split("\n")
        outside = {n for n, _ in strip_fences("\n".join(lines))}
        changed = 0

        def repl(m: re.Match, here: Path) -> str:
            nonlocal changed
            file_part, frag = m.group(1), m.group(2)
            if re.match(r"^[a-zA-Z][a-zA-Z0-9+.-]*:", file_part):
                return m.group(0)
            ar_dest = here if not file_part else (here.parent / file_part).resolve()
            if not self.inside(ar_dest) or not ar_dest.is_file() or ar_dest.suffix != ".md":
                return m.group(0)
            ar_slugs = heading_slugs(ar_dest)
            if frag.lower() in ar_slugs:
                return m.group(0)
            en_dest = ar_dest.with_name(ar_dest.name[:-(len(LANG_SUFFIX) + 3)] + ".md")
            if not self.inside(en_dest) or not en_dest.is_file():
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
        if only is not None and (Path(only).is_absolute() or ".." in Path(only).parts or not only.endswith(".md")):
            self.fail(only, "outside", "--file must be a relative .md path inside the documentation directory")
            return
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
            if not (self.inside(self.hb / rel) and self.inside(self.hb / ar_name(rel))):
                continue  # reported by compare(); never hash a file that resolves outside the repository
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
            if not (self.inside(self.hb / rel) and self.inside(self.hb / ar_name(rel))):
                print(f"SKIPPED {rel}: resolves outside the repository")
                continue
            now = {"en": sha(self.hb / rel), "ar": sha(self.hb / ar_name(rel))}
            if rel in old and old[rel]["en"] != now["en"] and old[rel]["ar"] == now["ar"]:
                print(f"REFUSED {rel}: English changed but the Arabic page did not; translate the change first")
                return 1
            pairs[rel] = now
        if not self.inside(self.root / SYNC):
            print(f"REFUSED {SYNC} resolves outside the repository; nothing was written")
            return 2
        (self.root / SYNC).write_text(json.dumps({"schema_version": 1, "note": "sha256 of both files when the pair was last reviewed together; "
                                                  "maintained by scripts/validate_handbook_parity.py --update-sync", "pairs": pairs},
                                                 indent=1, ensure_ascii=False, sort_keys=True) + "\n", encoding="utf-8")
        print(f"recorded {len(pairs)} pairs in {SYNC}")
        return 0

    def with_base(self, base: str) -> None:
        """Require Arabic to change whenever English changes in this PR (same-PR update rule)."""
        if not re.fullmatch(r"[A-Za-z0-9][A-Za-z0-9._/@^~-]*", base):
            raise SystemExit(f"--base must be a branch, tag or commit name (no leading dash or unusual characters): {base!r}")
        sha = subprocess.run(["git", "rev-parse", "--verify", "--end-of-options", base + "^{commit}"], cwd=self.root,
                             capture_output=True, text=True)
        if sha.returncode:
            raise SystemExit(f"--base does not name a commit: {base!r}")
        out = subprocess.run(["git", "diff", "--name-status", "--no-renames", "-z", f"{sha.stdout.strip()}...HEAD", "--", HB],
                             cwd=self.root, capture_output=True, text=True, check=True).stdout
        parts = [x for x in out.split("\0") if x]  # NUL-separated: status, path, status, path ... (no quoting of non-ASCII names)
        changed = {parts[i + 1]: parts[i][0] for i in range(0, len(parts) - 1, 2)}
        for path, st in changed.items():
            rel = path[len(HB) + 1:]
            if not rel.endswith(".md") or rel.endswith(LANG_SUFFIX + ".md") or rel in EXCLUDED_FROM_MIRROR:
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
    ap.add_argument("--root", default=".", help="repository root (default: current directory)")
    ap.add_argument("--docs-dir", default=HB, help="documentation directory relative to the root")
    ap.add_argument("--suffix", default=LANG_SUFFIX, help="mirror file suffix before .md, for example -ar")
    ap.add_argument("--require-metadata", action="store_true", help="'Owner role' and 'Status' lines must match in both languages")
    ap.add_argument("--evidence-dir", action="append", default=[], help="sub-directory whose pages must carry evidence-class labels")
    ap.add_argument("--lang-marker", help="language-switch line label (default: 'Language / اللغة:')")
    ap.add_argument("--file", help="source page relative to the documentation directory")
    ap.add_argument("--base", help="git base ref: require same-PR Arabic updates")
    ap.add_argument("--fix-anchors", action="store_true", help="rewrite English-style fragments in Arabic links to the Arabic heading slugs")
    ap.add_argument("--update-sync", action="store_true")
    ap.add_argument("--update-report", action="store_true")
    a = ap.parse_args()
    configure(a.docs_dir, a.suffix, a.require_metadata, tuple(a.evidence_dir), a.lang_marker)
    root = Path(a.root).resolve()
    if not (root / HB).is_dir():
        print(f"FAIL  documentation directory not found: {HB} (relative to {root})")
        print("PARITY RESULT: FAIL (nothing was validated)")
        return 2
    if not a.update_sync and not a.file and not en_pages(root):
        print(f"FAIL  no source pages found under {HB}; refusing to report PASS for an empty set")
        print("PARITY RESULT: FAIL (0 English page(s) checked)")
        return 2
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
        if not v.inside(p):
            print(f"FAIL  {REPORT} resolves outside the repository; not updated")
            return 2
        text = p.read_text(encoding="utf-8")
        block = f"<!-- STRUCTURAL:BEGIN -->\n{structural_report(v, en)}\n<!-- STRUCTURAL:END -->"
        text = re.sub(r"<!-- STRUCTURAL:BEGIN -->.*?<!-- STRUCTURAL:END -->", lambda _m: block, text, flags=re.S)
        p.write_text(text, encoding="utf-8")
    for page in sorted(v.problems):
        for msg in v.problems[page]:
            print(f"FAIL  {page}: {v.mask(msg)}")
    failing = len(v.problems)
    print(f"PARITY RESULT: {'FAIL' if failing else 'PASS'} ({len(en)} English page(s) checked, {failing} with problems). "
          "Structural only: passing does not prove semantic equivalence.")
    return 1 if failing else 0


if __name__ == "__main__":
    sys.exit(main())
