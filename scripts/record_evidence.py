#!/usr/bin/env python3
"""Execute a story's verification commands at a clean committed source and write evidence.

Input is a JSON spec (kept in the run directory) with: run_id, story_id, phase, checks
[{id, name, command[], required, timeout_seconds}], scenarios [{id, observed}], review
{status, reviewer, type, findings[]}, open_defects[] and optional manual_checks.
Output: verification/runs/<run_id>/evidence.json plus one log per command check, in the
schema validated by scripts/validate_blueprint.py. Scenario results are PASS only when every
required check passed; this tool never edits backlog or scenario status.
"""
import argparse
import datetime as dt
import json
import platform
import subprocess
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
ALLOWED_DIRTY_PREFIX = "verification/runs/"


def git(*args):
    return subprocess.run(["git", *args], cwd=ROOT, text=True, capture_output=True, check=True).stdout.strip()


def dirty_outside_runs(porcelain):
    """Paths changed outside verification/runs, from `git status --porcelain` printed as is (status columns kept)."""
    return [line[3:] for line in porcelain.splitlines() if line and not line[3:].startswith(ALLOWED_DIRTY_PREFIX)]


def tool_version(cmd):
    try:
        out = subprocess.run(cmd, text=True, capture_output=True, timeout=60)
        text = (out.stdout or out.stderr).strip().splitlines()
        lines = [x for x in text if "JAVA_TOOL_OPTIONS" not in x]
        return lines[0] if lines else None
    except (OSError, subprocess.TimeoutExpired):
        return None


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("spec")
    args = parser.parse_args()
    spec_path = Path(args.spec).resolve()
    spec = json.loads(spec_path.read_text(encoding="utf-8"))
    # Not git(): it strips the output, which would cut the leading status space of the first line.
    dirty = dirty_outside_runs(subprocess.run(["git", "status", "--porcelain", "--untracked-files=all"], cwd=ROOT, text=True,
                                              capture_output=True, check=True).stdout)
    if dirty:
        print("BLOCKED: source tree is dirty outside verification/runs: " + ", ".join(dirty[:10]))
        return 2
    sha = git("rev-parse", "HEAD")
    out = ROOT / "verification/runs" / spec["run_id"]
    out.mkdir(parents=True, exist_ok=True)
    started = dt.datetime.now(dt.timezone.utc).isoformat()
    checks = []
    for c in spec["checks"]:
        log = out / (c["id"] + ".log")
        with log.open("w", encoding="utf-8") as stream:
            try:
                p = subprocess.run(c["command"], cwd=ROOT, stdout=stream, stderr=subprocess.STDOUT, text=True,
                                   timeout=c.get("timeout_seconds", 1200), check=False)
                code, status = p.returncode, "PASS" if p.returncode == 0 else "FAIL"
            except subprocess.TimeoutExpired:
                code, status = None, "FAIL"
            except OSError as e:
                stream.write(str(e))
                code, status = None, "BLOCKED"
        checks.append({"id": c["id"], "name": c["name"], "kind": "command", "required": c.get("required", True),
                       "command": c["command"], "exit_code": code, "status": status,
                       "log_path": str(log.relative_to(ROOT))})
        print(f"{c['id']}: {status} (exit {code})")
    checks += spec.get("manual_checks", [])
    required_ok = all(c["status"] == "PASS" for c in checks if c.get("required") is True)
    failed = any(c["status"] == "FAIL" for c in checks if c.get("required") is True)
    status = "PASS" if required_ok else ("FAIL" if failed else "BLOCKED")
    scenario_status = status
    environment = {
        "host": "cloud development container (not a supported-platform claim)",
        "os": platform.platform(),
        "python": platform.python_version(),
        "java": tool_version(["java", "-version"]),
        "maven": tool_version(["mvn", "-v"]),
        "node": tool_version(["node", "--version"]),
        "pnpm": tool_version(["pnpm", "--version"]),
        "docker": tool_version(["docker", "--version"]),
    }
    evidence = {
        "schema_version": 1,
        "scope": "PRODUCT_VERIFICATION",
        "story_id": spec["story_id"],
        "phase": spec["phase"],
        "source_commit": sha,
        "environment": environment,
        "started_at": started,
        "finished_at": dt.datetime.now(dt.timezone.utc).isoformat(),
        "status": status,
        "checks": checks,
        "scenario_results": [{"id": s["id"], "status": scenario_status, "observed": s["observed"] if required_ok else
                              "Required check did not pass; see check logs"} for s in spec["scenarios"]],
        "artifacts": [str(Path(c["log_path"])) for c in checks if c.get("log_path")] + spec.get("artifacts", []),
        "review": spec["review"],
        "approvals": spec.get("approvals", []),
        "open_defects": spec.get("open_defects", []),
    }
    (out / "evidence.json").write_text(json.dumps(evidence, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")
    print(f"{status}: evidence verification/runs/{spec['run_id']}/evidence.json at {sha}")
    return {"PASS": 0, "FAIL": 1, "BLOCKED": 2}[status]


if __name__ == "__main__":
    sys.exit(main())
