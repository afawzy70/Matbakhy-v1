#!/usr/bin/env python3
"""P00 scope/traceability check (MB-001, MB-002).

Checks that the repository inventory matches the filesystem, the decision register
in Markdown and JSON agree without fabricated approvals, every rule is traceable to an
owning story and measurable scenario, the dependency graph is acyclic and phase-ordered,
all four client targets have owning stories, deferred extensions stay documented, and
the generated traceability matrix is current. It does not verify product behavior.

Usage: check_scope.py [--write-matrix]
"""
import argparse
import json
import re
import subprocess
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
MATRIX = "docs/product/08-traceability.md"
DECISION_STATES = {"PROPOSED_DEFAULT", "OPEN", "ACCEPTED", "REJECTED", "SUPERSEDED"}
CLIENT_TARGETS = {
    "customer web": "apps/customer-web",
    "operations web (cook workspace and platform backoffice)": "apps/operations-web",
    "customer mobile source": "apps/customer-mobile",
}
DEFERRED_SECTIONS = ["## Online payment extension", "## Courier extension", "## Social extension"]


def load(root, path):
    return json.loads((root / path).read_text(encoding="utf-8"))


def top_level_entries(root):
    """Top-level names of tracked and untracked non-ignored files; falls back to listing."""
    result = subprocess.run(["git", "ls-files", "--cached", "--others", "--exclude-standard"],
                            cwd=root, text=True, capture_output=True)
    if result.returncode == 0 and result.stdout.strip():
        return {line.split("/", 1)[0] for line in result.stdout.splitlines() if line}
    return {p.name for p in root.iterdir() if p.name != ".git"}


def check_inventory(root):
    errors = []
    entries = load(root, "planning/inventory.json")["entries"]
    listed = {}
    for e in entries:
        path = e.get("path")
        if not path or path in listed:
            errors.append(f"inventory: missing or duplicate path {path}")
            continue
        listed[path] = e
        exists = (root / path).exists()
        if e.get("state") == "PRESENT" and not exists:
            errors.append(f"inventory: {path} marked PRESENT but missing")
        elif e.get("state") == "PLANNED" and exists:
            errors.append(f"inventory: {path} exists but is marked PLANNED")
        elif e.get("state") not in {"PRESENT", "PLANNED"}:
            errors.append(f"inventory: {path} has invalid state {e.get('state')}")
        if not e.get("owner_story") or not e.get("purpose"):
            errors.append(f"inventory: {path} lacks owner story or purpose")
    for name in sorted(top_level_entries(root) - set(listed)):
        errors.append(f"inventory: unlisted top-level entry {name}")
    return errors


def check_decisions(root):
    errors = []
    decisions = load(root, "planning/decisions.json")["decisions"]
    md = (root / "docs/product/01-context-and-decisions.md").read_text(encoding="utf-8")
    md_ids = set(re.findall(r"^\| (C\d\d|D\d\d|O\d\d)\b", md, flags=re.M))
    json_ids = {d["id"] for d in decisions}
    for i in sorted({x for x in md_ids if not x.startswith("C")} - json_ids):
        errors.append(f"decisions: {i} in register document but not in decisions.json")
    for i in sorted(json_ids - md_ids):
        errors.append(f"decisions: {i} in decisions.json but not in register document")
    if not {f"C0{n}" for n in range(1, 8)} <= md_ids:
        errors.append("decisions: confirmed requirements C01-C07 are not all recorded")
    for d in decisions:
        status = d.get("status")
        if status not in DECISION_STATES:
            errors.append(f"decisions: {d['id']} invalid status {status}")
        if status == "ACCEPTED" and not d.get("approval_reference"):
            errors.append(f"decisions: {d['id']} ACCEPTED without approval reference")
        if status == "OPEN" and d.get("value") is not None:
            errors.append(f"decisions: {d['id']} OPEN but has a value")
        if status == "OPEN" and (not d.get("required_before") or not d.get("blocking_scope")):
            errors.append(f"decisions: {d['id']} OPEN without gate or blocking scope")
        if status != "ACCEPTED" and d.get("approval_reference"):
            errors.append(f"decisions: {d['id']} has approval reference but is {status}")
    return errors


def check_traceability(root):
    sys.path.insert(0, str(Path(__file__).resolve().parent))
    from validate_blueprint import validate

    errors = ["blueprint: " + e for e in validate(root)]
    stories = load(root, "planning/backlog.json")["stories"]
    by_id = {s["id"]: s for s in stories}
    for s in stories:
        for dep in s["depends_on"]:
            if dep in by_id and by_id[dep]["phase"] > s["phase"]:
                errors.append(f"traceability: {s['id']} depends on later-phase {dep}")
    artifacts = [(s, a) for s in stories for a in s["artifacts"]]
    for name, path in CLIENT_TARGETS.items():
        if not any(a == path for _, a in artifacts):
            errors.append(f"traceability: no story owns {name} ({path})")
    titles = " ".join(s["title"] for s in stories)
    for platform in ("Android", "iOS"):
        if not re.search(rf"Verify {platform} native", titles):
            errors.append(f"traceability: no separate {platform} native verification story")
    integrations = (root / "docs/engineering/07-integrations.md").read_text(encoding="utf-8")
    for section in DEFERRED_SECTIONS:
        if section not in integrations:
            errors.append(f"traceability: deferred extension section missing: {section}")
    return errors


def topological(stories):
    by_id = {s["id"]: s for s in stories}
    order, seen = [], set()

    def visit(sid):
        if sid in seen:
            return
        seen.add(sid)
        for dep in by_id[sid]["depends_on"]:
            visit(dep)
        order.append(sid)

    for s in stories:
        visit(s["id"])
    return order


def render_matrix(root):
    stories = load(root, "planning/backlog.json")["stories"]
    scenarios = {t["id"]: t for t in load(root, "planning/scenarios.json")["scenarios"]}
    decisions = load(root, "planning/decisions.json")["decisions"]
    phases = sorted({s["phase"] for s in stories})
    lines = [
        "# MVP traceability matrix",
        "",
        "Generated by `python3 scripts/check_scope.py --write-matrix` from planning JSON; do not edit by hand.",
        "`python3 scripts/check_scope.py` fails when this file is stale. Story and scenario status live in "
        "planning/backlog.json and planning/scenarios.json, not here.",
        "",
        "## Stories, rules and scenarios",
        "",
        "| Story | Phase | Title | Depends on | Rules | Scenarios (level) |",
        "|---|---|---|---|---|---|",
    ]
    for s in stories:
        scen = ", ".join(f"{t} ({scenarios[t]['level']})" for t in s["scenario_ids"] if t in scenarios)
        lines.append(f"| {s['id']} | {s['phase']} | {s['title']} | {', '.join(s['depends_on']) or '—'} | "
                     f"{', '.join(s['rule_ids']) or '—'} | {scen} |")
    lines += ["", "## Client targets", "", "| Target | Owning stories |", "|---|---|"]
    targets = dict(CLIENT_TARGETS)
    for name, path in targets.items():
        owners = [s["id"] for s in stories if path in s["artifacts"]]
        lines.append(f"| {name} | {', '.join(owners)} |")
    for platform in ("Android", "iOS"):
        owners = [s["id"] for s in stories if f"Verify {platform} native" in s["title"]]
        lines.append(f"| {platform} native verification | {', '.join(owners)} |")
    lines += ["", "## Dependency order", "",
              "One valid topological order (the dependency graph is acyclic and no story depends on a later phase):", ""]
    order = topological(stories)
    for p in phases:
        lines.append(f"- {p}: " + " → ".join(i for i in order if next(s for s in stories if s['id'] == i)["phase"] == p))
    lines += ["", "## Launch blockers (open decisions)", "",
              "| Decision | Topic | Required before | Blocking scope |", "|---|---|---|---|"]
    for d in decisions:
        if d["status"] == "OPEN":
            lines.append(f"| {d['id']} | {d['topic']} | {d['required_before']} | {d['blocking_scope']} |")
    lines += ["", "Open decisions block only their dependent live/launch/design actions; "
              "independent implementation continues with clearly labelled development defaults.", ""]
    return "\n".join(lines)


def check_matrix(root):
    path = root / MATRIX
    if not path.is_file():
        return [f"traceability: {MATRIX} missing; run with --write-matrix"]
    if path.read_text(encoding="utf-8") != render_matrix(root):
        return [f"traceability: {MATRIX} is stale; run with --write-matrix"]
    return []


def check(root=ROOT):
    return check_inventory(root) + check_decisions(root) + check_traceability(root) + check_matrix(root)


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--write-matrix", action="store_true")
    args = parser.parse_args()
    if args.write_matrix:
        (ROOT / MATRIX).write_text(render_matrix(ROOT), encoding="utf-8")
    errors = check()
    if errors:
        print(f"FAIL: {len(errors)} scope/traceability issue(s)")
        print("\n".join("- " + e for e in errors))
        return 1
    print("PASS: inventory, decision register, traceability and dependency order consistent (planning scope only)")
    return 0


if __name__ == "__main__":
    sys.exit(main())

# DOC-001 disposable test: no assessment
