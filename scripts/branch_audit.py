#!/usr/bin/env python3
"""Report-only Git branch audit. It NEVER deletes, moves or modifies anything.

Classifies remote (and optionally local) branches against a base branch so a human can decide:
  PROTECTED        main, develop, the base itself, anything in --protect
  ACTIVE           open PR (head or base), checked out in a worktree, or referenced by a workflow
  UNMERGED_WORK    commits whose patches are not in the base
  SAFE_TO_DELETE   every commit is an ancestor of the base and nothing above applies
  NEEDS_REVIEW     not an ancestor but no unique patch (merge commits / rebased copies), or classification unclear

SAFE_TO_DELETE is a candidate list, not an instruction: record name and SHA, then delete deliberately.
Usage: python3 scripts/branch_audit.py [--base origin/develop] [--prs-file prs.json | --gh] [--local] [--json]
"""
from __future__ import annotations

import argparse
import json
import re
import subprocess
import sys
from pathlib import Path

PROTECTED_DEFAULT = ("main", "develop")


def git(root: Path, *args: str) -> str:
    return subprocess.run(["git", *args], cwd=root, capture_output=True, text=True, check=True).stdout.strip()


def load_prs(root: Path, args: argparse.Namespace) -> list[dict]:
    if args.prs_file:
        return json.loads(Path(args.prs_file).read_text())
    if args.gh:
        out = subprocess.run(["gh", "pr", "list", "--state", "all", "--limit", "500", "--json",
                              "number,state,headRefName,baseRefName"], cwd=root, capture_output=True, text=True, check=True)
        return json.loads(out.stdout)
    return []


def worktree_branches(root: Path) -> set[str]:
    return {line.split("refs/heads/", 1)[1] for line in git(root, "worktree", "list", "--porcelain").splitlines()
            if line.startswith("branch ")}


def workflow_refs(root: Path) -> str:
    text = ""
    for f in (root / ".github/workflows").glob("*.yml") if (root / ".github/workflows").is_dir() else []:
        text += f.read_text(encoding="utf-8", errors="replace")
    return text


def classify(root: Path, base: str, prs: list[dict], protect: set[str], include_local: bool) -> list[dict]:
    wt, wf = worktree_branches(root), workflow_refs(root)
    refs = [(r.split("/", 1)[1], r) for r in git(root, "for-each-ref", "refs/remotes/origin", "--format=%(refname:short)").split()
            if r not in ("origin", "origin/HEAD")]
    if include_local:
        refs += [(r, r) for r in git(root, "for-each-ref", "refs/heads", "--format=%(refname:short)").split()]
    open_names = {p["headRefName"] for p in prs if p.get("state") == "OPEN"} | {p["baseRefName"] for p in prs if p.get("state") == "OPEN"}
    rows = []
    for name, ref in refs:
        sha = git(root, "rev-parse", ref)
        ahead = int(git(root, "rev-list", "--count", f"{base}..{ref}"))
        unique = sum(1 for l in git(root, "cherry", base, ref).splitlines() if l.startswith("+"))
        merged_anc = subprocess.run(["git", "merge-base", "--is-ancestor", sha, base], cwd=root).returncode == 0
        reasons = []
        if name in protect or ref == base or name in PROTECTED_DEFAULT:
            cls = "PROTECTED"
        elif name in open_names:
            cls, reasons = "ACTIVE", ["open pull request (as head or base)"]
        elif name in wt:
            cls, reasons = "ACTIVE", ["checked out in a worktree"]
        elif re.search(r"(?<![\w/.-])" + re.escape(name) + r"(?![\w/.-])", wf):
            cls, reasons = "ACTIVE", ["referenced by a workflow"]
        elif unique > 0:
            cls, reasons = "UNMERGED_WORK", [f"{unique} commit patch(es) not in {base}"]
        elif merged_anc:
            cls, reasons = "SAFE_TO_DELETE", [f"all commits are ancestors of {base}"]
        else:
            cls, reasons = "NEEDS_REVIEW", [f"{ahead} commit(s) not ancestors of {base} but no unique patch (merge commit or rebased copy)"]
        pr = ",".join(f"#{p['number']}:{p['state']}" for p in prs if p["headRefName"] == name)
        rows.append({"branch": name, "ref": ref, "sha": sha, "class": cls, "ahead": ahead, "unique_patches": unique,
                     "prs": pr, "reasons": reasons})
    return sorted(rows, key=lambda r: (r["class"], r["branch"]))


def main() -> int:
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--root", default=str(Path(__file__).resolve().parent.parent))
    ap.add_argument("--base", default="origin/develop")
    ap.add_argument("--prs-file")
    ap.add_argument("--gh", action="store_true", help="read pull requests with the gh CLI")
    ap.add_argument("--local", action="store_true", help="also classify local branches")
    ap.add_argument("--protect", action="append", default=[])
    ap.add_argument("--json", action="store_true")
    a = ap.parse_args()
    root = Path(a.root)
    rows = classify(root, a.base, load_prs(root, a), set(a.protect), a.local)
    if a.json:
        print(json.dumps(rows, indent=2))
    else:
        print(f"{'CLASS':14} {'BRANCH':58} {'SHA':9} AHEAD UNIQUE PRS / REASON")
        for r in rows:
            print(f"{r['class']:14} {r['branch'][:58]:58} {r['sha'][:9]} {r['ahead']:5} {r['unique_patches']:6} {r['prs']} {'; '.join(r['reasons'])}")
        print("REPORT ONLY: nothing was deleted or changed.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
