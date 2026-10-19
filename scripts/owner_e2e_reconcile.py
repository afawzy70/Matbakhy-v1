#!/usr/bin/env python3
"""Read-only reconciliation of the owner E2E fixtures against a seeded PostgreSQL (CI and operator).

Builds the expected users and grants from docs/quality/owner-acceptance/fixtures.json, runs
deploy/web-preview/reconcile-owner-e2e.sql through psql (connection from the usual PG* environment variables)
and exits 1 when any check has violations. Changes nothing; holds no credentials.
"""
import json
import os
import pathlib
import shlex
import subprocess
import sys

ROOT = pathlib.Path(__file__).resolve().parents[1]
MANIFEST = ROOT / "docs/quality/owner-acceptance/fixtures.json"
SQL = ROOT / "deploy/web-preview/reconcile-owner-e2e.sql"


def expected():
    actors = json.loads(MANIFEST.read_text(encoding="utf-8"))["actors"]
    users = [a["app"] for a in actors if a.get("app")]
    grants = [f'{a["app"]}:{p}' for a in actors if a.get("app") for p in a.get("grants", [])]
    return users, grants


def main():
    users, grants = expected()
    # PSQL_CMD (operator, e.g. "docker compose ... exec -T postgres psql -U matbakhy -d matbakhy") runs psql elsewhere; the SQL then travels on stdin.
    base = shlex.split(os.environ["PSQL_CMD"]) if os.environ.get("PSQL_CMD") else ["psql"]
    cmd = base + ["-X", "-At", "-F", "|", "-v", "ON_ERROR_STOP=1",
                  "-v", "expected_users=" + ",".join(users), "-v", "expected_grants=" + ",".join(grants)]
    if os.environ.get("PSQL_CMD"):
        out = subprocess.run(cmd + ["-f", "-"], input=SQL.read_text(encoding="utf-8"), capture_output=True, text=True)
    else:
        out = subprocess.run(cmd + ["-f", str(SQL)], capture_output=True, text=True)
    if out.returncode != 0:
        print(out.stderr, file=sys.stderr)
        return 1
    bad = 0
    for line in out.stdout.splitlines():
        name, _, n = line.partition("|")
        ok = n == "0"
        bad += not ok
        print(("ok   " if ok else "FAIL ") + name + " = " + n)
    print(f"{len(users)} fixture users, {len(grants)} fixture grants checked")
    return 1 if bad else 0


if __name__ == "__main__":
    sys.exit(main())
