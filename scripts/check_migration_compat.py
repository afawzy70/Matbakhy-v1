#!/usr/bin/env python3
"""Rollback compatibility of database migrations (owner direction 2026-10-14).

Flyway migrations are forward-only. A release can be rolled back by putting the previous images back (no data restore) only
if the previous API keeps working against the newer schema. So every migration after BASELINE must be expand-only, or say
why it is not:

    -- rollback-compat: breaking <reason>      the previous API will not work on this schema: rollback needs the pre-deploy backup
    -- rollback-compat: tightening <reason>    new constraints/indexes can reject writes the previous API still makes
    -- rollback-compat: expand-only            optional assertion for migrations without any risky statement

Risky statements are found by pattern after comments and string literals are removed; the annotation, with a reason of at
least ten characters, is required for each class that occurs. This is a review aid, not a proof: the release notes still list
the migrations and the operator rehearses on a copy (docs/operations/11-staging-deployment-and-rollback.md).

Usage: check_migration_compat.py [--dir DIR] [--baseline N] [--all] [--list]
Exit 0 when every checked migration is acceptable, 1 otherwise.
"""
import argparse
import pathlib
import re
import sys

ROOT = pathlib.Path(__file__).resolve().parents[1]
DEFAULT_DIR = ROOT / "backend" / "app" / "src" / "main" / "resources" / "db" / "migration"
BASELINE = 28  # Migrations up to and including V28 were written before this policy.

BREAKING = [
    (r"\bDROP\s+(TABLE|COLUMN|VIEW|TYPE|SCHEMA)\b", "drops an object the previous API may read or write"),
    (r"\bRENAME\b", "renames an object the previous API refers to by name"),
    (r"\bALTER\s+COLUMN\b[^;]*\bTYPE\b", "changes a column type"),
    (r"^\s*TRUNCATE\b", "truncates a table"),
    (r"\bDELETE\s+FROM\b", "deletes rows"),
    (r"\bADD\s+COLUMN\b(?![^;,]*\bDEFAULT\b)[^;,]*\bNOT\s+NULL\b", "adds a NOT NULL column without a default: the previous API's inserts fail"),
]
TIGHTENING = [
    (r"\bSET\s+NOT\s+NULL\b", "makes a column NOT NULL"),
    (r"\bCREATE\s+UNIQUE\s+INDEX\b", "adds a unique index"),
    (r"\bADD\s+CONSTRAINT\b[^;]*\b(UNIQUE|CHECK|FOREIGN\s+KEY|EXCLUDE)\b", "adds a constraint"),
    (r"\bADD\s+(CHECK|UNIQUE|FOREIGN\s+KEY)\b", "adds a constraint"),
]
ANNOTATION = re.compile(r"^--\s*rollback-compat:\s*(expand-only|breaking|tightening)\b\s*(.*)$", re.I | re.M)


def strip(sql):
    sql = re.sub(r"--[^\n]*", "", sql)
    sql = re.sub(r"\$\$.*?\$\$", "''", sql, flags=re.S)
    return re.sub(r"'(?:[^']|'')*'", "''", sql)


def target_table(statement):
    m = re.search(r"\bALTER\s+TABLE\s+(?:ONLY\s+)?(?:IF\s+EXISTS\s+)?(\w+)", statement, re.I) or re.search(r"\bON\s+(?:ONLY\s+)?(\w+)", statement, re.I)
    return m.group(1).lower() if m else None


def classify(path):
    text = path.read_text(encoding="utf-8")
    statements = [x.strip() for x in strip(text).split(";") if x.strip()]
    created = {m.group(1).lower() for x in statements for m in [re.match(r"CREATE\s+TABLE\s+(?:IF\s+NOT\s+EXISTS\s+)?(\w+)", x, re.I)] if m}
    found = {"breaking": [], "tightening": []}
    for statement in statements:
        for cls, rules in (("breaking", BREAKING), ("tightening", TIGHTENING)):
            for pattern, reason in rules:
                if re.search(pattern, statement, re.I) and reason not in found[cls]:
                    # Constraints and indexes on a table this migration creates cannot affect the previous API: it does not know the table.
                    if cls == "tightening" and target_table(statement) in created:
                        continue
                    found[cls].append(reason)
    notes = {m.group(1).lower(): m.group(2).strip() for m in ANNOTATION.finditer(text)}
    problems = []
    for cls, reasons in found.items():
        if reasons and (cls not in notes or len(notes[cls]) < 10):
            problems.append(f"{cls}: {'; '.join(reasons)}, but there is no '-- rollback-compat: {cls} <reason of at least 10 characters>'")
    if "expand-only" in notes and any(found.values()):
        problems.append("annotated expand-only, but it contains risky statements")
    verdict = "breaking" if found["breaking"] else "tightening" if found["tightening"] else "expand-only"
    return verdict, problems


def migrations(directory):
    out = []
    for p in directory.glob("V*__*.sql"):
        m = re.match(r"V(\d+)__", p.name)
        if m:
            out.append((int(m.group(1)), p))
    return sorted(out)


def main(argv=None):
    ap = argparse.ArgumentParser()
    ap.add_argument("--dir", type=pathlib.Path, default=DEFAULT_DIR)
    ap.add_argument("--baseline", type=int, default=BASELINE)
    ap.add_argument("--all", action="store_true", help="check every migration, not only those after the baseline")
    ap.add_argument("--list", action="store_true", help="print the classification of the checked migrations")
    args = ap.parse_args(argv)
    bad = 0
    for version, path in migrations(args.dir):
        if not args.all and version <= args.baseline:
            continue
        verdict, problems = classify(path)
        if args.list:
            print(f"V{version:<3} {verdict:<11} {path.name}")
        for problem in problems:
            bad += 1
            print(f"FAIL {path.name}: {problem}", file=sys.stderr)
    if bad:
        return 1
    print("PASS: every checked migration is expand-only or says why not")
    return 0


if __name__ == "__main__":
    sys.exit(main())
