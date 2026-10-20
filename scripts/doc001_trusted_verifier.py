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

    def open_prs(self, base_branch: str) -> list[dict]:
        out = []
        for pr in self._get(f"/repos/{self.repo}/pulls?state=open&base={base_branch}&per_page=100"):
            out.append({"number": pr["number"], "head_sha": pr["head"]["sha"], "base_ref": pr["base"]["ref"],
                        "body": pr.get("body") or "", "draft": pr.get("draft", False)})
        return out

    def ref_sha(self, ref: str) -> str:
        return self._get(f"/repos/{self.repo}/git/ref/heads/{ref}")["object"]["sha"]

    def comments(self, number: int) -> list[dict]:
        return [{"user": c["user"]["login"], "author_association": c["author_association"], "body": c.get("body") or ""}
                for c in self._get(f"/repos/{self.repo}/issues/{number}/comments?per_page=100")]

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

    def git_auth_args(self) -> list[str]:
        basic = base64.b64encode(f"x-access-token:{self.token}".encode()).decode()
        return ["-c", f"http.extraheader=AUTHORIZATION: basic {basic}"]


# --------------------------------------------------------------------------- verification
def git(cwd: Path, *args: str, check: bool = True) -> str:
    r = subprocess.run(["git", *args], cwd=cwd, capture_output=True, text=True)
    if check and r.returncode:
        raise RuntimeError(f"git {' '.join(a for a in args if not a.startswith('AUTHORIZATION'))}: {r.stderr.strip()}")
    return r.stdout


def evaluate(pr: dict, base_sha: str, comments: list[dict], owner: str, clone_url: str, auth_args: list[str]) -> tuple[bool, str]:
    """Run the BASE branch's gate against the PR as data. Returns (passed, report)."""
    with tempfile.TemporaryDirectory() as tmp:
        work = Path(tmp) / "pr"
        trusted = Path(tmp) / "base"
        work.mkdir()
        git(work, "init", "-q")
        git(work, *auth_args, "fetch", "-q", "--no-tags", clone_url, f"{base_sha}:refs/trusted/base",
            f"refs/pull/{pr['number']}/head:refs/pull/head")
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


def verify_all(gh, base_branch: str, owner: str, dry_run: bool) -> list[tuple[int, str]]:
    results = []
    base_sha = gh.ref_sha(base_branch)
    for pr in gh.open_prs(base_branch):
        passed, report = evaluate(pr, base_sha, gh.comments(pr["number"]), owner, gh.clone_url(), gh.git_auth_args())
        conclusion = "success" if passed else "failure"
        # Re-post only when the outcome for this head changed (the digest covers verdict, report and base tip).
        digest = hashlib.sha256(f"{conclusion}\n{base_sha}\n{report}".encode()).hexdigest()[:40]
        results.append((pr["number"], conclusion))
        if not dry_run and gh.latest_result(pr["head_sha"]) != digest:
            gh.post_check(pr["head_sha"], conclusion, f"Gate from {base_branch}@{base_sha[:12]}\n\n{report}", digest)
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
