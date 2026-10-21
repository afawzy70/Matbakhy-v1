#!/usr/bin/env python3
"""Read-only repository fact scanner for the developer-handbook skill (Stage A).

Lists what is detectable from files alone: technology markers, CI/CD definitions, container and infrastructure-as-code files, mobile
projects, test locations, documentation trees and git conventions. Every finding carries its evidence file, so the output can be pasted
into the discovery notes as VERIFIED IN CODE. It never reads file CONTENTS of secrets (files named like .env, *.pem, *.key, id_*,
credentials are listed by name only), never runs project code, never touches the network or a running system.

Usage: python3 scan_handbook_facts.py [--root .] [--json]
"""
from __future__ import annotations

import argparse
import json
import os
import re
import subprocess
from pathlib import Path

SKIP_DIRS = {".git", "node_modules", "dist", "build", "target", ".venv", "venv", "__pycache__", ".gradle", "Pods", ".next", ".idea"}
SECRET_NAME_RE = re.compile(r"(^|/)(\.env(\..*)?|.*\.(pem|key|p12|pfx|keystore|jks)|id_(rsa|ed25519|ecdsa)|credentials(\..*)?)$", re.I)
MARKERS = {
    "Java / Maven": ("pom.xml",), "Java / Gradle": ("build.gradle", "build.gradle.kts", "settings.gradle", "settings.gradle.kts"),
    "Node.js / npm family": ("package.json",), "TypeScript": ("tsconfig.json",), "Python": ("pyproject.toml", "requirements.txt", "setup.py", "Pipfile"),
    "Go": ("go.mod",), "Rust": ("Cargo.toml",), ".NET": ("*.csproj", "*.sln"), "PHP / Composer": ("composer.json",), "Ruby": ("Gemfile",),
    "Android": ("AndroidManifest.xml",), "iOS / Xcode": ("*.xcodeproj", "Podfile", "*.xcworkspace"), "Flutter": ("pubspec.yaml",),
    "Docker": ("Dockerfile", "Dockerfile.*", "*.Dockerfile"), "Docker Compose": ("docker-compose.yml", "docker-compose.yaml", "compose.yml", "compose.yaml"),
    "Kubernetes / Helm": ("Chart.yaml", "kustomization.yaml", "*.k8s.yaml"), "Terraform": ("*.tf",), "Ansible": ("ansible.cfg", "playbook.yml"),
    "Serverless": ("serverless.yml", "serverless.yaml", "template.yaml", "sam.yaml"), "Caddy": ("Caddyfile",), "nginx": ("nginx.conf",),
    "Database migrations (Flyway/Liquibase)": ("V*__*.sql", "changelog*.xml", "changelog*.yaml"), "OpenAPI": ("openapi.yaml", "openapi.yml", "openapi.json", "swagger.json"),
}
CI_PATTERNS = {"GitHub Actions": ".github/workflows/*.y*ml", "GitLab CI": ".gitlab-ci.yml", "Jenkins": "Jenkinsfile", "CircleCI": ".circleci/config.yml",
               "Azure Pipelines": "azure-pipelines.yml", "Bitbucket Pipelines": "bitbucket-pipelines.yml"}


def walk(root: Path):
    for dirpath, dirs, files in os.walk(root):
        dirs[:] = [d for d in dirs if d not in SKIP_DIRS]
        for f in files:
            yield (Path(dirpath) / f).relative_to(root).as_posix()


def match(path: str, pattern: str) -> bool:
    from fnmatch import fnmatchcase
    return fnmatchcase(path.rsplit("/", 1)[-1], pattern) if "/" not in pattern else fnmatchcase(path, pattern)


def scan(root: Path) -> dict:
    files = sorted(walk(root))
    out: dict = {"root": str(root), "file_count": len(files), "technologies": {}, "ci": {}, "secret_like_files_listed_by_name_only": [],
                 "documentation": {}, "tests": [], "git": {}}
    for tech, pats in MARKERS.items():
        hits = [f for f in files if any(match(f, p) for p in pats)]
        if hits:
            out["technologies"][tech] = hits[:8]
    for ci, pat in CI_PATTERNS.items():
        hits = [f for f in files if match(f, pat) or (pat.startswith(".github") and re.fullmatch(r"\.github/workflows/[^/]+\.ya?ml", f))]
        if hits:
            out["ci"][ci] = hits
    out["secret_like_files_listed_by_name_only"] = [f for f in files if SECRET_NAME_RE.search(f) and not f.endswith((".example", ".sample", ".template"))][:50]
    md = [f for f in files if f.endswith(".md")]
    out["documentation"] = {"markdown_files": len(md), "top_level": [f for f in md if "/" not in f][:20],
                            "doc_dirs": sorted({f.split("/")[0] for f in md if "/" in f and f.split("/")[0].lower() in ("docs", "doc", "documentation", "wiki")}),
                            "mirror_candidates": [f for f in md if re.search(r"-(ar|fr|de|es)\.md$", f)][:10]}
    out["tests"] = sorted({str(Path(f).parent) for f in files if re.search(r"(^|/)(test|tests|__tests__|spec|e2e)(/|$)|\.(test|spec)\.[jt]sx?$|Test\.java$|_test\.(py|go)$", f)})[:20]
    try:
        run = lambda *a: subprocess.run(["git", *a], cwd=root, capture_output=True, text=True, timeout=20).stdout.strip()
        out["git"] = {"default_branch_hint": run("symbolic-ref", "--short", "refs/remotes/origin/HEAD") or "unknown",
                      "branches_sample": run("branch", "-r", "--format=%(refname:short)").splitlines()[:10],
                      "has_codeowners": any(f in ("CODEOWNERS", ".github/CODEOWNERS", "docs/CODEOWNERS") for f in files),
                      "has_pr_template": any("pull_request_template" in f.lower() for f in files)}
    except Exception:  # noqa: BLE001 - discovery must not fail on a non-git directory
        out["git"] = {"note": "not a git repository or git unavailable"}
    return out


def main() -> int:
    ap = argparse.ArgumentParser(description=__doc__)
    ap.add_argument("--root", default=".")
    ap.add_argument("--json", action="store_true")
    a = ap.parse_args()
    res = scan(Path(a.root).resolve())
    if a.json:
        print(json.dumps(res, indent=1, ensure_ascii=False))
        return 0
    print(f"# Repository facts (VERIFIED IN CODE) for {res['root']} ({res['file_count']} files)")
    for k, v in res["technologies"].items():
        print(f"- {k}: {', '.join(v[:4])}")
    for k, v in res["ci"].items():
        print(f"- CI/CD {k}: {', '.join(v[:6])}")
    print(f"- secret-like files (names only, contents never read): {res['secret_like_files_listed_by_name_only'] or 'none'}")
    print(f"- documentation: {res['documentation']}")
    print(f"- test locations: {res['tests'] or 'none detected'}")
    print(f"- git: {res['git']}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
