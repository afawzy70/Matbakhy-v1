#!/usr/bin/env python3
"""Synthetic actors for the owner E2E catalog (docs/quality/owner-acceptance/fixtures.json).

  python3 scripts/owner_e2e_fixtures.py plan                 what exists, what must be provisioned (prints no secret)
  python3 scripts/owner_e2e_fixtures.py check                consistency of the manifest, the SQL seed and the case files (CI)
  python3 scripts/owner_e2e_fixtures.py render --out DIR     writes the PRIVATE credential files for the actors to provision
  python3 scripts/owner_e2e_fixtures.py finalize --out DIR --result F --users F   after the import: keep passwords only for ADDED users, verify ids

`render` generates a fresh random password per actor into DIR/credentials.private.json and DIR/partial-import.json (mode 600, DIR mode 700).
DIR must be outside this repository and must not exist yet. Passwords are never printed or committed. Nothing here talks to Keycloak or a
database: applying the files is an operator step on the isolated stack, or on the shared preview only with the owner's approval
(docs/quality/owner-acceptance/fixtures.md).
"""
import json
import os
import re
import secrets
import subprocess
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
MANIFEST = ROOT / "docs" / "quality" / "owner-acceptance" / "fixtures.json"
SQL = ROOT / "deploy" / "web-preview" / "seed-owner-e2e.sql"
SQL_MFA = ROOT / "deploy" / "web-preview" / "seed-owner-e2e-mfa.sql"
CASES = ROOT / "docs" / "quality" / "owner-acceptance" / "cases"
INVALID_DOMAIN = "owner-e2e.invalid"


def actors():
    return json.loads(MANIFEST.read_text(encoding="utf-8"))["actors"]


def to_provision():
    """One entry per Keycloak user (an actor with `instances` expands to numbered users)."""
    out = []
    for a in actors():
        if not a.get("kc"):
            continue
        for n in range(1, a.get("instances", 0) + 1) if a.get("instances") else [None]:
            e = dict(a)
            if n:
                e["username"] = a["username"].replace("{n}", str(n))
                e["kc"] = a["kc"].replace("{n}", str(n))
            out.append(e)
    return out


def check():
    problems = []
    sql = (SQL.read_text(encoding="utf-8") if SQL.exists() else "") + (SQL_MFA.read_text(encoding="utf-8") if SQL_MFA.exists() else "")
    base_sql = SQL.read_text(encoding="utf-8") if SQL.exists() else ""
    for code in ("security.mfa_policy", "security.mfa_reset"):
        if f"'{code}'" in base_sql:
            problems.append(f"{code} must only be granted by seed-owner-e2e-mfa.sql (V31+), not by the baseline-compatible seed")
    if not sql:
        problems.append(f"{SQL.relative_to(ROOT)} missing")
    keys = {a["key"] for a in actors()}
    for a in to_provision():
        if a.get("app") and a["kc"] not in sql:
            problems.append(f"{a['key']}: Keycloak id {a['kc']} not in the SQL seed")
        if a.get("app") and a["app"] not in sql:
            problems.append(f"{a['key']}: app user id {a['app']} not in the SQL seed")
        for g in a.get("grants", []):
            if f"'{g}'" not in sql:
                problems.append(f"{a['key']}: grant {g} not in the SQL seed")
    if re.search(r"(?i)(password|secret)\s*[:=]\s*'?[A-Za-z0-9]{8,}", sql):
        problems.append("SQL seed looks like it contains a credential")
    if "owner-e2e.invalid" in sql:
        pass
    for f in sorted(CASES.glob("*.json")):
        text = f.read_text(encoding="utf-8")
        for k in set(re.findall(r"FX-[A-Z-]+", text)):
            if k not in keys:
                problems.append(f"{f.name}: unknown fixture {k}")
    return problems


def render(out):
    out = Path(out).expanduser().resolve()
    try:
        out.relative_to(ROOT)
        raise SystemExit("refusing to write credentials inside the repository")
    except ValueError:
        pass
    top = subprocess.run(["git", "-C", str(out.parent), "rev-parse", "--show-toplevel"], capture_output=True, text=True)
    if top.returncode == 0 and Path(top.stdout.strip()).resolve() == ROOT:
        raise SystemExit("refusing to write credentials inside the repository")
    if out.exists():
        raise SystemExit(f"{out} exists; choose a new directory")
    os.umask(0o077)
    out.mkdir(parents=True, mode=0o700)
    creds, users = {}, []
    for a in to_provision():
        password = secrets.token_urlsafe(18)
        creds[a["username"]] = {"actor": a["key"], "username": a["username"], "password": password}
        users.append({
            "id": a["kc"], "username": a["username"], "enabled": True, "emailVerified": True, "email": f"{a['username']}@{INVALID_DOMAIN}",
            "firstName": a["username"], "lastName": "Synthetic",
            "credentials": [{"type": "password", "value": password, "temporary": False}],
        })
    # Keycloak's partial import keeps the given user ids (a plain "create users" call does not), so the fixed ids in the SQL seed match.
    partial = {"ifResourceExists": "SKIP", "users": users}
    for name, payload in (("credentials.private.json", creds), ("partial-import.json", partial)):
        fd = os.open(out / name, os.O_WRONLY | os.O_CREAT | os.O_EXCL, 0o600)
        with os.fdopen(fd, "w") as f:
            json.dump(payload, f, indent=1)
    print(f"wrote {len(users)} synthetic users to {out} (mode 700; files mode 600). Passwords are not printed.")


def finalize(out, result_file, users_file):
    """After the Keycloak partial import: keep a password in credentials.private.json only for users the import actually ADDED, mark the others as
    pre-existing (Keycloak SKIPs an existing user, so a freshly generated password would not match), and verify that every expected username exists
    in Keycloak with exactly the fixed id the SQL seed refers to. Exit 1 on any mismatch. Prints no password."""
    out = Path(out)
    try:
        result = json.loads(Path(result_file).read_text(encoding="utf-8"))
    except ValueError:
        print("the import result is not JSON (the import may still have created users): nothing verified, nothing written to PostgreSQL")
        return 1
    added = {r["resourceName"] for r in result.get("results", []) if r.get("action") == "ADDED" and r.get("resourceType") == "USER"}
    existing = {u["username"]: u["id"] for u in json.loads(Path(users_file).read_text(encoding="utf-8"))}
    creds_path = out / "credentials.private.json"
    creds = json.loads(creds_path.read_text(encoding="utf-8"))
    problems = []
    for a in to_provision():
        name = a["username"]
        if existing.get(name) != a["kc"]:
            problems.append(f"{name}: Keycloak id {existing.get(name)!r} is not the fixed id {a['kc']!r}")
        if name not in added and name in creds:
            creds[name]["password"] = None
            creds[name]["note"] = "user already existed; its password was not changed by this run (look in the earlier run directories of owner-e2e-private)"
    creds_path.write_text(json.dumps(creds, indent=1), encoding="utf-8")
    os.chmod(creds_path, 0o600)
    print(f"import: {len(added)} users added, {len(creds) - len(added)} already existed (passwords unchanged)")
    for p in problems:
        print("MISMATCH " + p)
    return 1 if problems else 0


def plan():
    print("Actor                     Keycloak user              Provision?  Grants")
    for a in actors():
        print(f"{a['key']:<25} {str(a.get('username') or '-'):<26} {'no (exists)' if a.get('exists') else ('YES' if a.get('kc') else 'n/a'):<11} {', '.join(a.get('grants', []))}")


def main():
    cmd = sys.argv[1] if len(sys.argv) > 1 else "plan"
    if cmd == "plan":
        plan()
    elif cmd == "check":
        problems = check()
        print("\n".join(problems) or "ok: fixtures manifest, SQL seed and case files agree")
        return 1 if problems else 0
    elif cmd == "render" and len(sys.argv) == 4 and sys.argv[2] == "--out":
        render(sys.argv[3])
    elif cmd == "finalize" and len(sys.argv) == 8 and sys.argv[2] == "--out" and sys.argv[4] == "--result" and sys.argv[6] == "--users":
        return finalize(sys.argv[3], sys.argv[5], sys.argv[7])
    else:
        print(__doc__)
        return 2
    return 0


if __name__ == "__main__":
    sys.exit(main())
