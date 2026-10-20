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
        self.fail_clone = False

    def ref_sha(self, ref): return sh(self.origin, "git", "rev-parse", f"refs/heads/{ref}")
    def open_prs(self, base): return self.prs
    def comments(self, n): return self._comments.get(n, [])
    def clone_url(self): return str(self.origin) if not self.fail_clone else "/nonexistent/https://x-access-token:SECRET@example.test"
    def git_env(self): return {}
    def snapshot(self, n):
        pr = next(p for p in self.prs if p["number"] == n)
        return pr["head_sha"], pr["body"]
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

    def test_errors_fail_closed_without_leaking_credentials_and_do_not_stop_other_prs(self) -> None:
        good = self.pr(6, {f"{HB}/README.md": "\nsentence six\n"})
        bad = self.pr(7, {f"{HB}/README.md": "\nsentence seven\n"})
        gh = FakeGitHub(self.origin, [bad, good], {})
        gh.git_env = lambda: {"GIT_CONFIG_COUNT": "1", "GIT_CONFIG_KEY_0": "http.extraheader",
                               "GIT_CONFIG_VALUE_0": "AUTHORIZATION: basic c2VjcmV0LXRva2Vu"}
        real = gh.clone_url
        gh.clone_url = lambda: str(self.origin / "missing") if gh.fail_clone else real()
        gh.fail_clone = True
        gh.posted.append((bad["head_sha"], "success", "old green", "olddigest"))  # a stale success must be replaced
        out = v.verify_all(gh, "develop", "owner", dry_run=False)
        self.assertEqual(out, [(7, "failure"), (6, "failure")])  # both fail closed, the scan continued past the first error
        latest = [p for p in gh.posted if p[0] == bad["head_sha"]][-1]
        self.assertEqual(latest[1], "failure")
        self.assertNotIn("c2VjcmV0LXRva2Vu", "".join(p[2] for p in gh.posted))
        self.assertNotIn("SECRET", "".join(p[2] for p in gh.posted))

    def test_shared_head_commit_needs_every_pr_to_pass(self) -> None:
        pr = self.pr(8, {".github/workflows/doc001.yml": "# touched\n", f"{HB}/05-developer-workflow/ci-cd.md": "\nshared head\n"},
                     pages=f"{HB}/05-developer-workflow/ci-cd.md")
        other = {**pr, "number": 9}
        sh(self.origin, "git", "update-ref", "refs/pull/9/head", pr["head_sha"])
        auth = {"user": "owner", "author_association": "OWNER", "body": f"OWNER-AUTHORIZES-GOVERNANCE {pr['head_sha'][:12]}"}
        gh = FakeGitHub(self.origin, [pr, other], {9: [auth]})  # only PR 9 carries the owner's authorization
        out = v.verify_all(gh, "develop", "owner", dry_run=False)
        self.assertEqual(out, [(8, "failure"), (9, "success")])
        self.assertEqual([p[1] for p in gh.posted], ["failure"])  # the shared commit's check is failure, not the last PR's success

    def test_authorization_edited_during_verification_fails_closed(self) -> None:
        pr = self.pr(10, {".github/workflows/doc001.yml": "# touched\n", f"{HB}/05-developer-workflow/ci-cd.md": "\nx\n"},
                     pages=f"{HB}/05-developer-workflow/ci-cd.md")
        auth = {"user": "owner", "author_association": "OWNER", "body": f"OWNER-AUTHORIZES-GOVERNANCE {pr['head_sha'][:12]}"}
        gh = FakeGitHub(self.origin, [pr], {10: [auth]})
        calls = {"n": 0}
        base_comments = gh.comments

        def flapping(n):
            calls["n"] += 1
            return base_comments(n) if calls["n"] % 2 else []  # deleted between the judging read and the final snapshot
        gh.comments = flapping
        self.assertEqual(v.verify_all(gh, "develop", "owner", dry_run=False), [(10, "failure")])
        self.assertIn("kept changing", gh.posted[0][2])

    def test_verdict_is_published_before_a_slower_pr_is_evaluated_and_lookup_errors_do_not_suppress_it(self) -> None:
        a = self.pr(11, {f"{HB}/README.md": "\nsentence eleven\n"})
        b = self.pr(12, {f"{HB}/README.md": "\nsentence twelve\n"})
        gh = FakeGitHub(self.origin, [a, b], {})
        order = []
        real_post, real_comments = gh.post_check, gh.comments
        gh.post_check = lambda sha, *r: (order.append(("post", sha)), real_post(sha, *r))
        gh.comments = lambda n: (order.append(("eval", n)), real_comments(n))[1]
        gh.latest_result = lambda sha: (_ for _ in ()).throw(RuntimeError("lookup failed"))
        v.verify_all(gh, "develop", "owner", dry_run=False)
        self.assertEqual([o for o in order if o[0] == "post"][0][1], a["head_sha"])
        self.assertLess(order.index(("post", a["head_sha"])), order.index(("eval", 12)))  # PR 11 is published before PR 12 is judged
        self.assertEqual(len(gh.posted), 2)

    def test_pagination_follows_every_page(self) -> None:
        app = v.GitHubApp.__new__(v.GitHubApp)
        pages = {1: [{"i": n} for n in range(100)], 2: [{"i": 100}]}
        app._get = lambda path: pages[int(path.rsplit("page=", 1)[1])]
        self.assertEqual(len(app._get_all("/x?state=open")), 101)

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
