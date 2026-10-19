#!/usr/bin/env python3
"""DOC-001 documentation consistency gate.

Subcommands
  docs   validate the Developer Handbook and changed Markdown (links, anchors, source references,
         metadata, navigation, traceability identifiers). Deterministic; needs no PR.
  pr     assess a pull request: declaration, impact against the change matrix, governance rules,
         then the same documentation validation.

Exit code 0 only when every check passes. This script proves structure and reference integrity,
not semantic accuracy; semantic review is a separate, human/independent-reviewer requirement
(docs/developer-handbook/05-developer-workflow/documentation-policy.md).
"""
from __future__ import annotations

import argparse
import fnmatch
import json
import re
import subprocess
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
HANDBOOK = "docs/developer-handbook"
MATRIX = f"{HANDBOOK}/doc001-matrix.json"
POLICY = f"{HANDBOOK}/05-developer-workflow/documentation-policy.md"
STATUS_LABELS = ("IMPLEMENTED", "PARTIALLY IMPLEMENTED", "PLANNED", "DISABLED", "DEPRECATED", "NOT VERIFIED", "REFERENCE")
REQUIRED_ENTRY_POINTS = (
    f"{HANDBOOK}/README.md",
    f"{HANDBOOK}/README-ar.md",
    POLICY,
    MATRIX,
    ".github/pull_request_template.md",
)
GOVERNANCE_PATHS = (
    ".github/workflows/doc001.yml",
    "scripts/doc001_check.py",
    "scripts/test_doc001_gate.py",
    MATRIX,
    POLICY,
    "AGENTS.md",
    "CLAUDE.md",
    ".github/pull_request_template.md",
    ".github/CODEOWNERS",
)
DECL_KEYS = ("impact", "summary", "handbook_pages", "other_docs", "diagrams", "contracts", "tests",
             "traceability", "consistency", "justification", "governance_review")
LIST_KEYS = ("handbook_pages", "other_docs", "diagrams", "contracts", "tests", "traceability")
GENERIC = (
    "no docs needed", "no documentation needed", "minor change", "internal only", "bug fix", "no user impact",
    "not applicable", "n/a", "none", "no change", "no changes", "docs not needed", "trivial", "small change",
    "no impact", "refactor", "refactoring", "cleanup", "typo", "no documentation impact", "tests only",
)
TOP_DIRS = ("apps", "backend", "packages", "contracts", "infra", "deploy", "planning", "verification", "docs",
            "scripts", ".github")
TOP_FILES = ("AGENTS.md", "CLAUDE.md", "README.md")
SRC_RE = re.compile(r"^((?:%s)/[^\s<>*{}|\"']+|(?:%s))$" % ("|".join(re.escape(d) for d in TOP_DIRS),
                                                           "|".join(re.escape(f) for f in TOP_FILES)))
ID_PATTERNS = {
    "TC": (re.compile(r"\bTC-\d{3}\b"), "planning/scenarios.json"),
    "MB": (re.compile(r"\bMB-\d{3}\b"), "planning/backlog.json"),
    "BR": (re.compile(r"\bBR-\d{3}\b"), "planning/rules.json"),
}
OWNER_CASE_RE = re.compile(r"\b(?:E2E|CFG|ORD|FIN|COOK|OPS)-[A-Z0-9]+-\d{3}\b|\bCFG-\d{3}\b")


class Report:
    def __init__(self) -> None:
        self.errors: list[str] = []
        self.notes: list[str] = []

    def fail(self, msg: str) -> None:
        self.errors.append(msg)

    def note(self, msg: str) -> None:
        self.notes.append(msg)

    def emit(self) -> int:
        for n in self.notes:
            print(f"NOTE  {n}")
        for e in self.errors:
            print(f"FAIL  {e}")
        print(f"DOC-001 RESULT: {'FAIL' if self.errors else 'PASS'} ({len(self.errors)} problem(s))")
        return 1 if self.errors else 0


# --------------------------------------------------------------------------- git helpers
def git(root: Path, *args: str) -> str:
    return subprocess.run(["git", *args], cwd=root, check=True, capture_output=True, text=True).stdout


def merge_base(root: Path, base: str, head: str) -> str:
    try:
        return git(root, "merge-base", base, head).strip()
    except subprocess.CalledProcessError:
        raise SystemExit("DOC-001: no merge base between base and head (history missing); fetch the full base history")


def substantive(root: Path, mb: str, head: str, path: str, status: str) -> bool:
    """A changed documentation file counts only if it survives and changes beyond whitespace."""
    if status == "D":
        return False
    if status == "A":
        return bool((root / path).read_text(encoding="utf-8", errors="replace").strip())
    r = subprocess.run(["git", "diff", "-w", "--ignore-blank-lines", "--quiet", mb, head, "--", path], cwd=root)
    return r.returncode == 1


def changed_files(root: Path, base: str, head: str) -> dict[str, str]:
    """path -> status letter (A/M/D/...), diffing against the merge base."""
    mb = merge_base(root, base, head)
    out = git(root, "diff", "--name-status", "--no-renames", mb, head)
    result: dict[str, str] = {}
    for line in out.splitlines():
        if not line.strip():
            continue
        status, _, path = line.partition("\t")
        result[path] = status[0]
    return result


# --------------------------------------------------------------------------- markdown validation
FENCE_RE = re.compile(r"^\s*(```|~~~)")
LINK_RE = re.compile(r"(?<!\!)\[[^\]]*\]\(([^)\s]+)(?:\s+\"[^\"]*\")?\)")
IMG_RE = re.compile(r"!\[[^\]]*\]\(([^)\s]+)\)")
CODE_RE = re.compile(r"`([^`\n]+)`")
HEAD_RE = re.compile(r"^(#{1,6})\s+(.*?)\s*#*\s*$")


FENCE_OPEN_RE = re.compile(r"^ {0,3}(`{3,}|~{3,})(.*)$")


def strip_fences(text: str) -> list[tuple[int, str]]:
    """Lines outside fenced code blocks. A fence closes only with the same character and at least the same length."""
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


def anchors_of(path: Path) -> set[str]:
    out: set[str] = set()
    text = path.read_text(encoding="utf-8", errors="replace")
    for _, line in strip_fences(text):
        m = HEAD_RE.match(line)
        if not m:
            continue
        base = slug(m.group(2))
        cand, n = base, 0
        while cand in out:
            n += 1
            cand = f"{base}-{n}"
        out.add(cand)
    for m in re.finditer(r"<a\s+(?:name|id)=[\"']([^\"']+)[\"']", text, re.I):
        out.add(m.group(1).lower())
    return out


def load_ids(root: Path) -> dict[str, set[str]]:
    ids: dict[str, set[str]] = {}
    for kind, (_, rel) in ID_PATTERNS.items():
        p = root / rel
        ids[kind] = set(re.findall(ID_PATTERNS[kind][0], p.read_text(encoding="utf-8"))) if p.exists() else set()
    owner: set[str] = set()
    cases = root / "docs/quality/owner-acceptance/cases"
    if cases.is_dir():
        for f in cases.glob("*.json"):
            for c in json.loads(f.read_text(encoding="utf-8")).get("cases", []):
                owner.add(c["id"])
    ids["OWNER"] = owner
    return ids


def validate_markdown(root: Path, rel: str, rep: Report, ids: dict[str, set[str]], check_ids: bool) -> None:
    path = root / rel
    if not path.is_file():
        rep.fail(f"{rel}: documentation file is missing")
        return
    text = path.read_text(encoding="utf-8", errors="replace")
    for ln, line in strip_fences(text):
        for pattern in (LINK_RE, IMG_RE):
            for m in pattern.finditer(line):
                target = m.group(1)
                if re.match(r"^[a-zA-Z][a-zA-Z0-9+.-]*:", target) or target.startswith("//"):
                    continue
                file_part, _, frag = target.partition("#")
                if not file_part:
                    dest = path
                elif file_part.startswith("/"):
                    dest = (root / file_part.lstrip("/")).resolve()
                else:
                    dest = (path.parent / file_part).resolve()
                try:
                    dest.relative_to(root.resolve())
                except ValueError:
                    rep.fail(f"{rel}:{ln}: link leaves the repository: {target}")
                    continue
                if not dest.exists():
                    rep.fail(f"{rel}:{ln}: broken relative link: {target}")
                    continue
                if frag and dest.suffix == ".md" and dest.is_file() and frag.lower() not in anchors_of(dest):
                    rep.fail(f"{rel}:{ln}: link anchor not found: {target}")
        for m in CODE_RE.finditer(line):
            token = m.group(1).strip()
            ref = re.sub(r"(:\d+(-\d+)?)?(#.*)?$", "", token)
            if not SRC_RE.match(ref) or "..." in ref:
                continue
            if not (root / ref).exists():
                rep.fail(f"{rel}:{ln}: referenced source path does not exist: {token}")
    if check_ids:
        for ln, line in strip_fences(text):
            for kind, (rx, src) in ID_PATTERNS.items():
                for tok in rx.findall(line):
                    if ids[kind] and tok not in ids[kind]:
                        rep.fail(f"{rel}:{ln}: {tok} is not defined in {src}")
            if ids["OWNER"]:
                for tok in OWNER_CASE_RE.findall(line):
                    if tok not in ids["OWNER"] and not tok.startswith("E2E-J"):
                        rep.fail(f"{rel}:{ln}: owner E2E case {tok} is not in docs/quality/owner-acceptance/cases")


def validate_handbook(root: Path, rep: Report) -> list[str]:
    hb = root / HANDBOOK
    for rel in REQUIRED_ENTRY_POINTS:
        if not (root / rel).is_file():
            rep.fail(f"{rel}: required DOC-001 entry point is missing")
    if not hb.is_dir():
        return []
    pages = sorted(str(p.relative_to(root)) for p in hb.rglob("*.md"))
    ids = load_ids(root)
    for rel in pages:
        validate_markdown(root, rel, rep, ids, check_ids=True)
        head = "\n".join((root / rel).read_text(encoding="utf-8").splitlines()[:16])
        if not re.search(r"^> \*\*Owner role:\*\* \S", head, re.M):
            rep.fail(f"{rel}: missing '> **Owner role:** ...' metadata line in the first 16 lines")
        m = re.search(r"^> \*\*Status:\*\* (.+)$", head, re.M)
        if not m:
            rep.fail(f"{rel}: missing '> **Status:** <label>' metadata line in the first 16 lines")
        elif not any(m.group(1).strip().upper().startswith(lbl) for lbl in STATUS_LABELS):
            rep.fail(f"{rel}: Status must start with one of {', '.join(STATUS_LABELS)}")
    # navigation: every handbook page must be reachable from the handbook README by relative links
    readme = f"{HANDBOOK}/README.md"
    if (root / readme).is_file():
        reach, todo = {readme}, [readme]
        while todo:
            cur = todo.pop()
            text = (root / cur).read_text(encoding="utf-8", errors="replace")
            for _, line in strip_fences(text):
                for m in LINK_RE.finditer(line):
                    t = m.group(1).partition("#")[0]
                    if not t or re.match(r"^[a-zA-Z][a-zA-Z0-9+.-]*:", t):
                        continue
                    dest = (root / t.lstrip("/")).resolve() if t.startswith("/") else (root / cur).parent.joinpath(t).resolve()
                    try:
                        r = str(dest.relative_to(root.resolve()))
                    except ValueError:
                        continue
                    if r.startswith(HANDBOOK) and r.endswith(".md") and r not in reach and dest.is_file():
                        reach.add(r)
                        todo.append(r)
        for rel in pages:
            if rel not in reach and rel != f"{HANDBOOK}/README-ar.md":
                rep.fail(f"{rel}: not reachable from {readme} (orphan page)")
        if f"{HANDBOOK}/README-ar.md" in pages and "README-ar.md" not in (root / readme).read_text(encoding="utf-8"):
            rep.fail(f"{readme}: does not link README-ar.md")
    validate_matrix(root, rep)
    return pages


def validate_matrix(root: Path, rep: Report) -> dict:
    p = root / MATRIX
    if not p.is_file():
        return {}
    try:
        data = json.loads(p.read_text(encoding="utf-8"))
    except json.JSONDecodeError as e:
        rep.fail(f"{MATRIX}: invalid JSON ({e})")
        return {}
    areas = data.get("areas")
    if not isinstance(areas, list) or not areas:
        rep.fail(f"{MATRIX}: 'areas' must be a non-empty list")
        return {}
    for a in areas:
        for k in ("id", "code", "docs", "owner_role"):
            if k not in a or not a[k]:
                rep.fail(f"{MATRIX}: area {a.get('id', '?')} lacks '{k}'")
        for d in a.get("docs", []):
            if not any(ch in d for ch in "*?[") and not (root / d).exists():
                rep.fail(f"{MATRIX}: area {a.get('id')} requires documentation that does not exist: {d}")
    return data


# --------------------------------------------------------------------------- declaration
def visible_body(body: str) -> str:
    """Drop fenced code blocks (nested fences included); the comment-hiding check follows in parse_declaration."""
    return "\n".join(line for _, line in strip_fences(body))


def parse_declaration(body: str, rep: Report) -> dict | None:
    body = visible_body(body)
    for m in re.finditer(r"<!--.*?-->", body, re.S):
        inner = m.group(0)
        if re.search(r"DOC-001:(BEGIN|END)", inner) and not re.fullmatch(r"<!--\s*DOC-001:(BEGIN|END)\s*-->", inner):
            rep.fail("DOC-001 declaration: a marker is hidden inside another HTML comment")
            return None
    begins = [m.start() for m in re.finditer(r"<!--\s*DOC-001:BEGIN\s*-->", body)]
    ends = [m.start() for m in re.finditer(r"<!--\s*DOC-001:END\s*-->", body)]
    if len(begins) != 1 or len(ends) != 1 or ends[0] < begins[0]:
        rep.fail("PR description: exactly one <!-- DOC-001:BEGIN --> ... <!-- DOC-001:END --> declaration is required")
        return None
    block = body[begins[0]:ends[0]]
    block = re.sub(r"^<!--\s*DOC-001:BEGIN\s*-->", "", block)
    decl: dict[str, str] = {}
    cur = None
    for raw in block.splitlines():
        if not raw.strip() or raw.strip().startswith("#") or raw.strip().startswith("<!--"):
            continue
        m = re.match(r"^([a-z_]+):\s*(.*)$", raw)
        if m and not raw.startswith((" ", "\t")):
            cur = m.group(1)
            if cur not in DECL_KEYS:
                rep.fail(f"DOC-001 declaration: unknown key '{cur}' (allowed: {', '.join(DECL_KEYS)})")
                cur = None
                continue
            if cur in decl:
                rep.fail(f"DOC-001 declaration: duplicate key '{cur}'")
                cur = None
                continue
            decl[cur] = m.group(2).strip()
        elif cur and raw.startswith((" ", "\t")):
            decl[cur] += " " + raw.strip()
        else:
            rep.fail(f"DOC-001 declaration: malformed line: {raw.strip()[:80]}")
    if decl.get("impact") not in ("updated", "none"):
        rep.fail("DOC-001 declaration: 'impact' must be 'updated' or 'none'")
        return None
    return decl


def split_list(value: str) -> list[str]:
    return [v.strip().strip("`") for v in re.split(r"[,\n]", value) if v.strip() and v.strip().lower() not in ("-", "none")]


def gmatch(path: str, glob: str) -> bool:
    return fnmatch.fnmatchcase(path, glob.replace("**", "*"))


def is_doc(path: str) -> bool:
    return path.endswith(".md") or path.startswith("docs/") or path == MATRIX


def generic_justification(text: str) -> bool:
    t = re.sub(r"[^a-z0-9 ]+", " ", text.lower())
    t = re.sub(r"\s+", " ", t).strip()
    for g in sorted(GENERIC, key=len, reverse=True):
        t = re.sub(r"\b" + re.escape(g) + r"\b", " ", t)
    t = re.sub(r"\b(this|is|a|an|the|it|and|only|just|as|of|to|no|not|change|changes|needed|required)\b", " ", t)
    return len(re.sub(r"\s+", " ", t).strip()) < 25


def doc_citation(path: str) -> bool:
    return (path.endswith(".md") and (path.startswith("docs/") or path in TOP_FILES)) or path in (
        "contracts/openapi.yaml", "contracts/endpoints.json")


def assess_pr(root: Path, base: str, head: str, body: str, reviews: list[dict] | None, author: str | None,
              rep: Report) -> None:
    mb = merge_base(root, base, head)
    head_sha = git(root, "rev-parse", head).strip()
    files = changed_files(root, base, head)
    if not files:
        rep.note("no changed files against the merge base")
        return
    matrix = validate_matrix(root, rep)
    decl = parse_declaration(body, rep)
    code_files = [f for f in files if not is_doc(f)]
    doc_files = [f for f in files if is_doc(f)]
    areas = matrix.get("areas", [])

    def matches(path: str, globs: list[str]) -> bool:
        return any(gmatch(path, g) for g in globs)

    triggered: dict[str, list[str]] = {}
    unclassified: list[str] = []
    for f in code_files:
        hit = False
        for a in areas:
            if matches(f, a["code"]):
                triggered.setdefault(a["id"], []).append(f)
                hit = True
        if not hit:
            unclassified.append(f)
    if unclassified:
        rep.note("files without a matrix area (any documentation change or a specific justification is required): "
                 + ", ".join(unclassified[:8]))

    gov = [f for f in files if f in GOVERNANCE_PATHS]
    if decl is not None:
        impact = decl["impact"]
        if impact == "none":
            just = decl.get("justification", "")
            if not code_files:
                rep.fail("DOC-001: impact 'none' is not valid for a documentation-only change; declare 'updated'")
            if doc_files:
                rep.fail("DOC-001: impact 'none' contradicts the documentation files changed in this PR: "
                         + ", ".join(doc_files[:5]))
            if len(just) < 80 or len(set(re.findall(r"[a-zA-Z]{3,}", just.lower()))) < 12:
                rep.fail("DOC-001: 'justification' is missing or too short; explain specifically why the existing "
                         "documentation remains accurate (at least 80 characters, naming the pages that stay correct)")
            elif generic_justification(just):
                rep.fail("DOC-001: 'justification' is generic; describe which documented behaviour is unchanged and why")
            else:
                refs = [r for r in re.findall(r"[\w./-]+\.(?:md|json|yaml|yml)\b", just)
                        if (root / r).exists() and doc_citation(r)]
                if not refs:
                    rep.fail("DOC-001: 'justification' must cite at least one existing documentation page or contract "
                             "(repo-relative path under docs/, a top-level guide, or the API contract) that stays accurate")
            rep.note("impact 'none' accepted structurally; a REVIEWER MUST EVALUATE the justification against the code")
        else:
            listed = {k: split_list(decl.get(k, "")) for k in LIST_KEYS}
            if not (listed["handbook_pages"] or listed["other_docs"]):
                rep.fail("DOC-001: impact 'updated' must list handbook_pages or other_docs")
            for k, vals in listed.items():
                for v in vals:
                    if not (root / v).exists():
                        rep.fail(f"DOC-001: declared {k} entry does not exist: {v}")
                    elif v not in files:
                        rep.fail(f"DOC-001: declared {k} entry is not changed in this PR: {v}")
                    elif k in ("handbook_pages", "other_docs", "diagrams", "traceability"):
                        if not is_doc(v):
                            rep.fail(f"DOC-001: declared {k} entry is not a documentation file: {v}")
                        elif not substantive(root, mb, head, v, files[v]):
                            rep.fail(f"DOC-001: declared {k} entry has no substantive change (whitespace-only or deleted): {v}")
                    elif k == "contracts" and not v.startswith(("contracts/", "packages/api-client/")):
                        rep.fail(f"DOC-001: declared contracts entry is not a contract file: {v}")
                    elif k == "tests" and is_doc(v):
                        rep.fail(f"DOC-001: declared tests entry is a documentation file: {v}")
            for v in listed["handbook_pages"]:
                if not v.startswith(HANDBOOK + "/"):
                    rep.fail(f"DOC-001: handbook_pages entry is outside {HANDBOOK}/: {v}")
            if not decl.get("consistency", "").strip():
                rep.fail("DOC-001: 'consistency' must state what was compared (code, contract, tests) to verify agreement")
            if not decl.get("summary", "").strip():
                rep.fail("DOC-001: 'summary' must say what changed and why")
            changed_docs = {d for d in doc_files if substantive(root, mb, head, d, files[d])}
            for aid, fs in triggered.items():
                area = next(a for a in areas if a["id"] == aid)
                if not any(gmatch(d, g) for d in changed_docs for g in area["docs"]):
                    rep.fail(f"DOC-001: area '{aid}' changed ({fs[0]}...) but none of its required documentation was "
                             f"updated: {', '.join(area['docs'])}")
            if unclassified and not changed_docs:
                rep.fail("DOC-001: unclassified files changed without any documentation update: " + ", ".join(unclassified[:5]))
        if gov:
            if not decl.get("governance_review", "").strip():
                rep.fail("DOC-001: governance files changed (" + ", ".join(gov) + ") but 'governance_review' is empty")
            elif reviews is None:
                rep.fail("DOC-001: governance files changed and no review information is available to verify approval")
            else:
                latest: dict[str, str] = {}
                for r in reviews:  # chronological; comments never change a decision; approvals bind to a commit
                    state = r.get("state", "")
                    if not r.get("user") or state == "COMMENTED" or state == "PENDING":
                        continue
                    if state == "APPROVED" and r.get("commit_id") != head_sha:
                        state = "STALE"
                    latest[r["user"]] = state
                ok = any(state == "APPROVED" and user != author for user, state in latest.items())
                if not ok:
                    rep.fail("DOC-001: governance files changed; an APPROVED review of the CURRENT head commit from someone "
                             "other than the PR author is required (no self-approval; re-run the check after approving)")
    elif gov:
        rep.fail("DOC-001: governance files changed and no valid declaration")
    validate_changed_markdown(root, files, rep)


def validate_changed_markdown(root: Path, files: dict[str, str], rep: Report) -> None:
    pages = validate_handbook(root, rep)
    ids = load_ids(root)
    for f, st in files.items():
        if f.endswith(".md") and st != "D" and f not in pages:
            validate_markdown(root, f, rep, ids, check_ids=False)


# --------------------------------------------------------------------------- CLI
def main() -> int:
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    sub = ap.add_subparsers(dest="cmd", required=True)
    d = sub.add_parser("docs")
    d.add_argument("--root", default=str(ROOT))
    d.add_argument("files", nargs="*", help="additional Markdown files to validate (repo-relative)")
    p = sub.add_parser("pr")
    p.add_argument("--root", default=str(ROOT))
    p.add_argument("--base", required=True)
    p.add_argument("--head", default="HEAD")
    p.add_argument("--body-file", required=True)
    p.add_argument("--reviews-file", help="JSON list of {user,state} from the GitHub reviews API")
    p.add_argument("--author")
    a = ap.parse_args()
    root = Path(a.root).resolve()
    rep = Report()
    if a.cmd == "docs":
        validate_handbook(root, rep)
        ids = load_ids(root)
        for f in a.files:
            validate_markdown(root, f, rep, ids, check_ids=False)
    else:
        body = Path(a.body_file).read_text(encoding="utf-8")
        reviews = json.loads(Path(a.reviews_file).read_text()) if a.reviews_file else None
        assess_pr(root, a.base, a.head, body, reviews, a.author, rep)
    return rep.emit()


if __name__ == "__main__":
    sys.exit(main())
