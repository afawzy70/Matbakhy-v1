#!/usr/bin/env python3
"""Trusted DOC-001 verifier: posts the required check "Documentation Consistency / DOC-001" as a GitHub App.

Why it exists: a workflow that lives in the pull request can be rewritten by that pull request (disposable PR #33 replaced the
job with a no-op and passed). This verifier runs OUTSIDE the repository's own workflows, in a separate private repository that
holds the App credentials, and it never executes anything from a pull request:

  1. the gate script is taken from the CURRENT tip of the PR's base branch (develop), which is protected;
  2. the pull request is fetched as data only;
  3. the result is published as a check run created by the App. The ruleset pins the required check to the App's id
     (`integration_id`), so a check with the same name created by the PR's own workflow (id of github-actions) does not satisfy it.

Design, owner steps and rollout: docs/developer-handbook/05-developer-workflow/doc001-trusted-enforcement.md
Environment (real mode): GITHUB_REPOSITORY (owner/name), DOC001_APP_ID, DOC001_APP_PRIVATE_KEY (PEM). Standard library plus the
`openssl` and `git` binaries only. `--dry-run` evaluates and prints without posting.
"""
from __future__ import annotations

import argparse
import base64
import hashlib
import json
import os
import subprocess
import sys
import tempfile
import time
import urllib.request
from pathlib import Path

CHECK_NAME = "Documentation Consistency / DOC-001"
API = "https://api.github.com"
GATE = "scripts/doc001_check.py"


# --------------------------------------------------------------------------- GitHub access
class GitHubApp:
    """Minimal REST client authenticated as the App installation."""

    def __init__(self, repo: str, app_id: str, private_key_pem: str) -> None:
        self.repo = repo
        self.app_id = int(app_id)
        self.owner = repo.split("/")[0]
        self.token = self._installation_token(app_id, private_key_pem)

    def _request(self, method: str, url: str, token: str, body: dict | None = None):
        req = urllib.request.Request(url if url.startswith("http") else API + url, method=method,
                                     data=json.dumps(body).encode() if body is not None else None,
                                     headers={"Authorization": f"Bearer {token}", "Accept": "application/vnd.github+json",
                                              "X-GitHub-Api-Version": "2022-11-28", "User-Agent": "matbakhy-doc001-verifier"})
        with urllib.request.urlopen(req, timeout=30) as resp:
            return json.loads(resp.read() or b"null")

    def _installation_token(self, app_id: str, pem: str) -> str:
        def b64(raw: bytes) -> str:
            return base64.urlsafe_b64encode(raw).rstrip(b"=").decode()
        now = int(time.time())
        unsigned = b64(json.dumps({"alg": "RS256", "typ": "JWT"}).encode()) + "." + b64(
            json.dumps({"iat": now - 60, "exp": now + 540, "iss": app_id}).encode())
        with tempfile.NamedTemporaryFile("w", suffix=".pem") as key:
            os.chmod(key.name, 0o600)
            key.write(pem)
            key.flush()
            sig = subprocess.run(["openssl", "dgst", "-sha256", "-sign", key.name], input=unsigned.encode(),
                                 capture_output=True, check=True).stdout
        jwt = unsigned + "." + b64(sig)
        inst = self._request("GET", f"/repos/{self.repo}/installation", jwt)
        return self._request("POST", f"/app/installations/{inst['id']}/access_tokens", jwt)["token"]

    def _get(self, path: str):
        return self._request("GET", path, self.token)

    def _get_all(self, path: str) -> list:
        """Follow pagination to the end (a PR or comment beyond the first page must not be skipped)."""
        out: list = []
        for page in range(1, 51):
            chunk = self._get(f"{path}{'&' if '?' in path else '?'}per_page=100&page={page}")
            out.extend(chunk)
            if len(chunk) < 100:
                return out
        raise RuntimeError("pagination limit reached; failing closed")

    def open_prs(self, base_branch: str) -> list[dict]:
        out = []
        for pr in self._get_all(f"/repos/{self.repo}/pulls?state=open&base={base_branch}"):
            out.append({"number": pr["number"], "head_sha": pr["head"]["sha"], "base_ref": pr["base"]["ref"],
                        "body": pr.get("body") or "", "draft": pr.get("draft", False)})
        return out

    def ref_sha(self, ref: str) -> str:
        return self._get(f"/repos/{self.repo}/git/ref/heads/{ref}")["object"]["sha"]

    def comments(self, number: int) -> list[dict]:
        return [{"user": c["user"]["login"], "author_association": c["author_association"], "body": c.get("body") or ""}
                for c in self._get_all(f"/repos/{self.repo}/issues/{number}/comments")]

    def snapshot(self, number: int) -> tuple[str, str]:
        pr = self._get(f"/repos/{self.repo}/pulls/{number}")
        return pr["head"]["sha"], pr.get("body") or ""

    def latest_result(self, head_sha: str) -> str | None:
        runs = self._get(f"/repos/{self.repo}/commits/{head_sha}/check-runs?check_name={CHECK_NAME.replace(' ', '%20')}")
        mine = [r for r in runs["check_runs"] if r.get("app", {}).get("id") == self.app_id]
        return (mine[0].get("external_id") if mine else None)

    def post_check(self, head_sha: str, conclusion: str, summary: str, digest: str) -> None:
        self._request("POST", f"/repos/{self.repo}/check-runs", self.token, {
            "name": CHECK_NAME, "head_sha": head_sha, "status": "completed", "conclusion": conclusion,
            "external_id": digest,
            "output": {"title": "DOC-001 " + conclusion, "summary": summary[:60000]}})

    def clone_url(self) -> str:
        return f"https://github.com/{self.repo}.git"

    def git_env(self) -> dict[str, str]:
        """Credentials travel in the environment of the git child only: never in argv, so never in an error message."""
        basic = base64.b64encode(f"x-access-token:{self.token}".encode()).decode()
        print(f"::add-mask::{self.token}\n::add-mask::{basic}")
        return {"GIT_CONFIG_COUNT": "1", "GIT_CONFIG_KEY_0": "http.extraheader",
                "GIT_CONFIG_VALUE_0": f"AUTHORIZATION: basic {basic}"}


# --------------------------------------------------------------------------- verification
def git(cwd: Path, *args: str, env: dict[str, str] | None = None) -> str:
    r = subprocess.run(["git", *args], cwd=cwd, capture_output=True, text=True, env={**os.environ, **(env or {})})
    if r.returncode:
        # Only the subcommand is reported; the remote URL and credentials are never echoed.
        raise RuntimeError(f"git {args[0]} failed (exit {r.returncode})")
    return r.stdout


def evaluate(pr: dict, base_sha: str, comments: list[dict], owner: str, clone_url: str, env: dict[str, str]) -> tuple[bool, str]:
    """Run the BASE branch's gate against the PR as data. Returns (passed, report)."""
    with tempfile.TemporaryDirectory() as tmp:
        work = Path(tmp) / "pr"
        trusted = Path(tmp) / "base"
        work.mkdir()
        git(work, "init", "-q")
        git(work, "fetch", "-q", "--no-tags", clone_url, f"{base_sha}:refs/trusted/base",
            f"refs/pull/{pr['number']}/head:refs/pull/head", env=env)
        head = git(work, "rev-parse", "refs/pull/head").strip()
        if head != pr["head_sha"]:
            return False, f"head moved during verification ({head[:12]} != {pr['head_sha'][:12]}); will re-run"
        git(work, "checkout", "-q", "--detach", head)
        # Trusted gate: exported from the base commit, never from the PR.
        trusted.mkdir()
        archive = subprocess.run(["git", "archive", "refs/trusted/base", "scripts", "docs/developer-handbook"], cwd=work,
                                 capture_output=True)
        if archive.returncode:
            return False, "the base branch has no scripts/ or handbook; install the gate through a separately reviewed change"
        subprocess.run(["tar", "-x", "-C", str(trusted)], input=archive.stdout, check=True)
        if not (trusted / GATE).is_file():
            return False, "the base branch has no DOC-001 gate script; failing closed"
        body = Path(tmp) / "body.md"
        body.write_text(pr["body"], encoding="utf-8")
        cfile = Path(tmp) / "comments.json"
        cfile.write_text(json.dumps(comments))
        r = subprocess.run([sys.executable, "-I", str(trusted / GATE), "pr", "--root", str(work), "--base", base_sha,
                            "--head", head, "--body-file", str(body), "--comments-file", str(cfile), "--owner", owner],
                           capture_output=True, text=True)
        return r.returncode == 0, (r.stdout + r.stderr).strip()


def verdict(gh, pr: dict, base_sha: str, owner: str) -> tuple[bool, str]:
    """Evaluate one PR; any error is a failure (never a silent keep-the-old-green)."""
    try:
        for _ in range(3):
            comments = gh.comments(pr["number"])
            passed, report = evaluate(pr, base_sha, comments, owner, gh.clone_url(), gh.git_env())
            # Final snapshot: the owner's comment, the description and the head must be what was judged.
            head_now, body_now = gh.snapshot(pr["number"])
            if (head_now, body_now, gh.comments(pr["number"])) == (pr["head_sha"], pr["body"], comments):
                return passed, report
            if head_now != pr["head_sha"]:
                return False, "head changed during verification; the next run judges the new head"
            pr = {**pr, "body": body_now}
        return False, "pull request description or comments kept changing during verification; failing closed"
    except Exception as exc:  # noqa: BLE001 - fail closed on every error class
        return False, f"verification error ({type(exc).__name__}): {exc}"


def verify_all(gh, base_branch: str, owner: str, dry_run: bool) -> list[tuple[int, str]]:
    """Judge and publish head by head, so no verdict waits behind another PR's slow evaluation."""
    results = []
    base_sha = gh.ref_sha(base_branch)
    by_head: dict[str, list[dict]] = {}
    for pr in gh.open_prs(base_branch):
        by_head.setdefault(pr["head_sha"], []).append(pr)
    for head_sha, prs in by_head.items():
        items = []
        for pr in prs:
            passed, report = verdict(gh, pr, base_sha, owner)
            items.append((pr, passed, report))
            results.append((pr["number"], "success" if passed else "failure"))
        # Several PRs can share one head commit but a commit has a single check: success only if every one of them passes.
        ok = all(p for _, p, _ in items)
        conclusion = "success" if ok else "failure"
        report = "\n\n".join(f"PR #{pr['number']}: {'PASS' if p else 'FAIL'}\n{r}" for pr, p, r in items)
        digest = hashlib.sha256(f"{conclusion}\n{base_sha}\n{report}".encode()).hexdigest()[:40]
        if dry_run:
            continue
        try:
            unchanged = gh.latest_result(head_sha) == digest
        except Exception:  # noqa: BLE001 - an unreadable previous result must not suppress a new verdict
            unchanged = False
        if not unchanged:
            gh.post_check(head_sha, conclusion, f"Gate from {base_branch}@{base_sha[:12]}\n\n{report}", digest)
    return results


def main() -> int:
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--base-branch", default="develop")
    ap.add_argument("--dry-run", action="store_true")
    a = ap.parse_args()
    repo = os.environ["GITHUB_REPOSITORY"]
    gh = GitHubApp(repo, os.environ["DOC001_APP_ID"], os.environ["DOC001_APP_PRIVATE_KEY"])
    for number, conclusion in verify_all(gh, a.base_branch, gh.owner, a.dry_run):
        print(f"PR #{number}: {conclusion}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
