#!/usr/bin/env python3
"""Run bounded configured checks. Never equates unconfigured or missing checks with PASS."""
import argparse
import datetime as dt
import json
import subprocess
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]


def run_check(check, root, out):
    result = {"id": check["id"], "name": check["name"], "required": check["required"], "kind": "command"}
    command = check.get("command")
    if not check.get("configured") or not isinstance(command, list) or not command or not all(isinstance(x, str) for x in command):
        return {**result, "status": "BLOCKED", "reason": "Required real check is not configured", "exit_code": None}
    timeout = check.get("timeout_seconds", 1200)
    if not isinstance(timeout, int) or timeout < 1 or timeout > 7200:
        return {**result, "status": "BLOCKED", "reason": "Invalid bounded timeout", "exit_code": None}
    log = out / (check["id"] + ".log")
    try:
        with log.open("w", encoding="utf-8") as stream:
            process = subprocess.run(command, cwd=root, stdout=stream, stderr=subprocess.STDOUT,
                                     timeout=timeout, check=False, text=True, shell=False)
        return {**result, "command": command, "exit_code": process.returncode,
                "status": "PASS" if process.returncode == 0 else "FAIL", "log_path": str(log.relative_to(root))}
    except subprocess.TimeoutExpired:
        return {**result, "command": command, "exit_code": None, "status": "FAIL", "reason": "Timeout", "log_path": str(log.relative_to(root))}
    except OSError as e:
        return {**result, "command": command, "exit_code": None, "status": "BLOCKED", "reason": str(e)}


def summarize(checks):
    required = [c for c in checks if c.get("required") is True]
    if not required:
        return "BLOCKED"
    if any(c["status"] == "FAIL" for c in required):
        return "FAIL"
    if any(c["status"] != "PASS" for c in required):
        return "BLOCKED"
    return "PASS"


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--phase", required=True)
    args = parser.parse_args()
    gates = json.loads((ROOT / "planning/gates.json").read_text())["gates"]
    gate = next((g for g in gates if g["id"] == args.phase), None)
    if gate is None:
        print("BLOCKED: unknown phase")
        return 2
    stamp = dt.datetime.now(dt.timezone.utc).strftime("%Y%m%dT%H%M%S%fZ")
    out = ROOT / "verification/local" / (args.phase + "-" + stamp)
    out.mkdir(parents=True)
    sha_result = subprocess.run(["git", "rev-parse", "HEAD"], cwd=ROOT, text=True, capture_output=True)
    sha = sha_result.stdout.strip() if sha_result.returncode == 0 else None
    dirty_result = subprocess.run(["git", "status", "--porcelain"], cwd=ROOT, text=True, capture_output=True)
    source_dirty = dirty_result.returncode != 0 or bool(dirty_result.stdout.strip())
    results = [run_check(c, ROOT, out) for c in gate["checks"]]
    status = summarize(results)
    if sha is None or source_dirty:
        status = "BLOCKED"
    report = {"scope": "PHASE_COMMANDS_ONLY", "phase": args.phase, "source_commit": sha,
              "started_at": stamp, "finished_at": dt.datetime.now(dt.timezone.utc).isoformat(),
              "status": status, "checks": results, "source_dirty": source_dirty,
              "note": "Command results only. Requires clean tested source, observed scenario results, review and applicable approvals for promotion. Inspect/redact logs before committing."}
    (out / "report.json").write_text(json.dumps(report, indent=2) + "\n")
    for r in results:
        print(r["id"] + ": " + r["status"] + (" — " + r["reason"] if r.get("reason") else ""))
    print(status + ": command checks only; report " + str((out / "report.json").relative_to(ROOT)))
    return {"PASS": 0, "FAIL": 1, "BLOCKED": 2}[status]


if __name__ == "__main__":
    sys.exit(main())
