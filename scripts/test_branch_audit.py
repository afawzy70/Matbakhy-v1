#!/usr/bin/env python3
"""Tests for the report-only branch audit (scripts/branch_audit.py)."""
import json
import subprocess
import sys
import tempfile
import unittest
from pathlib import Path

AUDIT = Path(__file__).resolve().parent / "branch_audit.py"


class AuditTests(unittest.TestCase):
    def setUp(self):
        self.t = tempfile.TemporaryDirectory()
        self.addCleanup(self.t.cleanup)
        self.o = Path(self.t.name) / "origin.git"
        self.w = Path(self.t.name) / "work"
        subprocess.run(["git", "init", "-q", "--bare", "-b", "develop", str(self.o)], check=True)
        subprocess.run(["git", "clone", "-q", str(self.o), str(self.w)], check=True, capture_output=True)
        for k, v in (("user.email", "t@example.invalid"), ("user.name", "t")):
            self.g("config", k, v)
        (self.w / ".github/workflows").mkdir(parents=True)
        (self.w / ".github/workflows/ci.yml").write_text("on:\n  push:\n    branches: [develop, pinned/by-workflow]\n")
        self.commit("a.txt", "base", "base")
        self.g("push", "-q", "origin", "HEAD:develop")

    def g(self, *a):
        return subprocess.run(["git", *a], cwd=self.w, check=True, capture_output=True, text=True).stdout.strip()

    def commit(self, f, text, msg):
        (self.w / f).write_text(text)
        self.g("add", "-A")
        self.g("commit", "-qm", msg)

    def branch(self, name, start="develop", change=None):
        self.g("checkout", "-q", "-b", name, start)
        if change:
            self.commit(*change)
        self.g("push", "-q", "origin", name)
        self.g("checkout", "-q", "develop")

    def audit(self, prs=None):
        pf = Path(self.t.name) / "prs.json"
        pf.write_text(json.dumps(prs or []))
        self.g("fetch", "-q", "origin")
        r = subprocess.run([sys.executable, str(AUDIT), "--root", str(self.w), "--base", "origin/develop", "--prs-file", str(pf), "--json"],
                           capture_output=True, text=True)
        self.assertEqual(r.returncode, 0, r.stderr)
        return {x["branch"]: x for x in json.loads(r.stdout)}

    def test_classification(self):
        self.branch("merged/old")                                     # no commits beyond develop
        self.branch("work/unmerged", change=("u.txt", "x", "unique"))  # unique commit
        self.branch("open/pr", change=("p.txt", "x", "pr"))
        self.branch("pinned/by-workflow")
        rows = self.audit(prs=[{"number": 7, "state": "OPEN", "headRefName": "open/pr", "baseRefName": "develop"},
                               {"number": 3, "state": "CLOSED", "headRefName": "work/unmerged", "baseRefName": "develop"}])
        self.assertEqual(rows["develop"]["class"], "PROTECTED")
        self.assertEqual(rows["merged/old"]["class"], "SAFE_TO_DELETE")
        self.assertEqual(rows["work/unmerged"]["class"], "UNMERGED_WORK")   # a closed PR does not make it safe
        self.assertEqual(rows["open/pr"]["class"], "ACTIVE")
        self.assertEqual(rows["pinned/by-workflow"]["class"], "ACTIVE")

    def test_open_pr_base_branch_is_active_and_merge_commit_needs_review(self):
        self.branch("stack/base")
        self.branch("stack/head", start="stack/base", change=("s.txt", "x", "s"))
        self.g("checkout", "-q", "-b", "mergey", "develop")
        self.g("merge", "-q", "--no-ff", "-m", "merge", "stack/head")
        self.g("push", "-q", "origin", "mergey")
        self.g("checkout", "-q", "develop")
        rows = self.audit(prs=[{"number": 9, "state": "OPEN", "headRefName": "stack/head", "baseRefName": "stack/base"}])
        self.assertEqual(rows["stack/base"]["class"], "ACTIVE")
        self.assertEqual(rows["mergey"]["class"], "UNMERGED_WORK")  # contains stack/head's patch, not in develop

    def test_audit_never_modifies_the_repository(self):
        self.branch("merged/old")
        self.g("fetch", "-q", "origin")  # the helper fetches; take the snapshot after it so only the audit is measured
        self.g("remote", "set-head", "origin", "-a")
        before = self.g("for-each-ref", "refs", "--format=%(refname) %(objectname)")
        self.audit()
        self.assertEqual(before, self.g("for-each-ref", "refs", "--format=%(refname) %(objectname)"))
        self.assertNotIn("branch -d", AUDIT.read_text().replace('"""', ""))
        self.assertNotRegex(AUDIT.read_text(), r"\"(branch|push|worktree|update-ref|reset|clean)\"\s*,\s*\"(-d|-D|--delete|remove|prune)")


if __name__ == "__main__":
    unittest.main()
