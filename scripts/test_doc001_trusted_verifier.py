#!/usr/bin/env python3
"""Tests for scripts/doc001_trusted_verifier.py against a local copy of this repository (no network, no GitHub).

They prove the verifier's LOGIC: the gate comes from the base branch, so a pull request that rewrites the DOC-001 workflow or the
gate script cannot change its own verdict (the PR #33 scenario). They do NOT prove the GitHub App, the ruleset pin or the hosting
exist; that is verified live and recorded in doc001-trusted-enforcement.md.
"""
from __future__ import annotations

import subprocess
import sys
import tempfile
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
import doc001_trusted_verifier as v  # noqa: E402

REPO = Path(__file__).resolve().parent.parent
HB = "docs/developer-handbook"
DECL = """<!-- DOC-001:BEGIN -->
impact: updated
summary: reword the handbook entry page
handbook_pages: {pages}
consistency: compared the page with the files it links
governance_review: {gov}
<!-- DOC-001:END -->
"""
GOV = "author role: agent session; reviewer role: owner; Codex review: NOT_RUN; GitHub cannot verify identities"


def sh(cwd: Path, *args: str) -> str:
    r = subprocess.run(args, cwd=cwd, capture_output=True, text=True)
    assert r.returncode == 0, (args, r.stderr)
    return r.stdout.strip()


class FakeGitHub:
    def __init__(self, origin: Path, prs: list[dict], comments: dict[int, list[dict]]) -> None:
        self.origin, self.prs, self._comments, self.posted = origin, prs, comments, []

    def ref_sha(self, ref): return sh(self.origin, "git", "rev-parse", f"refs/heads/{ref}")
    def open_prs(self, base): return self.prs
    def comments(self, n): return self._comments.get(n, [])
    def clone_url(self): return str(self.origin)
    def git_auth_args(self): return []
    def latest_result(self, sha):
        mine = [p for p in self.posted if p[0] == sha]
        return mine[-1][3] if mine else None
    def post_check(self, sha, conclusion, summary, digest): self.posted.append((sha, conclusion, summary, digest))


class TrustedVerifierTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls) -> None:
        cls._t = tempfile.TemporaryDirectory()
        cls.origin = Path(cls._t.name) / "origin"
        cls.origin.mkdir()
        files = subprocess.run(["git", "ls-files", "-z"], cwd=REPO, capture_output=True).stdout.split(b"\0")
        paths = [f.decode() for f in files if f and (REPO / f.decode()).is_file()]
        subprocess.run(["rsync", "-a", "--files-from=-", "--from0", f"{REPO}/", f"{cls.origin}/"], input=b"\0".join(
            p.encode() for p in paths), check=True)
        sh(cls.origin, "git", "init", "-q", "-b", "develop")
        sh(cls.origin, "git", "config", "user.email", "t@example.test")
        sh(cls.origin, "git", "config", "user.name", "t")
        sh(cls.origin, "git", "add", "-A")
        sh(cls.origin, "git", "commit", "-qm", "base")
        cls.base = sh(cls.origin, "git", "rev-parse", "HEAD")

    @classmethod
    def tearDownClass(cls) -> None:
        cls._t.cleanup()

    def pr(self, number: int, edits: dict[str, str], pages: str = f"{HB}/README.md", gov: str = GOV, append: bool = True):
        """Create refs/pull/<n>/head with the edits; edits maps path -> text appended (or replacing the file)."""
        sh(self.origin, "git", "checkout", "-q", "--detach", self.base)
        for rel, text in edits.items():
            f = self.origin / rel
            f.write_text((f.read_text() if append and f.exists() else "") + text)
        sh(self.origin, "git", "add", "-A")
        sh(self.origin, "git", "commit", "-qm", f"pr {number}")
        head = sh(self.origin, "git", "rev-parse", "HEAD")
        sh(self.origin, "git", "update-ref", f"refs/pull/{number}/head", head)
        sh(self.origin, "git", "checkout", "-q", "develop")
        return {"number": number, "head_sha": head, "base_ref": "develop", "draft": False,
                "body": DECL.format(pages=pages, gov=gov)}

    def run_one(self, pr, comments=None):
        gh = FakeGitHub(self.origin, [pr], {pr["number"]: comments or []})
        return v.verify_all(gh, "develop", "owner", dry_run=False), gh

    def test_ordinary_documentation_pr_passes(self) -> None:
        pr = self.pr(1, {f"{HB}/README.md": "\nA reworded sentence for the verifier test.\n"})
        (res, _), = [self.run_one(pr)]
        self.assertEqual(res, [(1, "success")])

    def test_pr33_scenario_workflow_replaced_by_noop_is_blocked(self) -> None:
        noop = "name: Documentation Consistency\non: [pull_request]\njobs:\n  doc001:\n    name: Documentation Consistency / DOC-001\n" \
               "    runs-on: ubuntu-24.04\n    steps:\n      - run: echo ok\n"
        pr = self.pr(2, {".github/workflows/doc001.yml": noop, f"{HB}/README.md": "\nx change\n"}, append=False)
        res, gh = self.run_one(pr)
        self.assertEqual(res, [(2, "failure")])
        self.assertIn("OWNER-AUTHORIZES-GOVERNANCE", gh.posted[0][2])

    def test_pr_that_rewrites_the_gate_script_cannot_pass_itself(self) -> None:
        pr = self.pr(3, {"scripts/doc001_check.py": "import sys\nsys.exit(0)\n", f"{HB}/README.md": "\nx change\n"}, append=False)
        res, _ = self.run_one(pr)
        self.assertEqual(res, [(3, "failure")])

    def test_owner_authorization_for_the_exact_head_unblocks_governance_change(self) -> None:
        ci = f"{HB}/05-developer-workflow/ci-cd.md"
        pr = self.pr(4, {".github/workflows/doc001.yml": "# touched\n", ci: "\nx change\n"}, pages=ci)
        auth = {"user": "owner", "author_association": "OWNER", "body": f"OWNER-AUTHORIZES-GOVERNANCE {pr['head_sha'][:12]}"}
        self.assertEqual(self.run_one(pr, [auth])[0], [(4, "success")])
        stale = {**auth, "body": "OWNER-AUTHORIZES-GOVERNANCE " + "1" * 12}
        self.assertEqual(self.run_one(pr, [stale])[0], [(4, "failure")])

    def test_result_is_posted_once_per_unchanged_outcome(self) -> None:
        pr = self.pr(5, {f"{HB}/README.md": "\nanother sentence\n"})
        gh = FakeGitHub(self.origin, [pr], {})
        v.verify_all(gh, "develop", "owner", dry_run=False)
        v.verify_all(gh, "develop", "owner", dry_run=False)
        self.assertEqual(len(gh.posted), 1)
        v.verify_all(gh, "develop", "owner", dry_run=True)
        self.assertEqual(len(gh.posted), 1)


if __name__ == "__main__":
    unittest.main()
