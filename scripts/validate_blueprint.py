#!/usr/bin/env python3
"""Validate planning integrity; optionally validate evidence-based phase promotion."""
import argparse
import json
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
SHA = re.compile(r"[0-9a-f]{40}\Z")


def read(root, path):
    return json.loads((root / path).read_text(encoding="utf-8"))


def inside(root, path):
    p = (root / path).resolve()
    if not p.is_relative_to(root.resolve()):
        raise ValueError("Path escapes repository: " + str(path))
    return p


def validate(root=ROOT, phase=None, commit=None):
    errors = []
    try:
        stories = read(root, "planning/backlog.json")["stories"]
        rules = read(root, "planning/rules.json")["rules"]
        scenarios = read(root, "planning/scenarios.json")["scenarios"]
        gates = read(root, "planning/gates.json")["gates"]
        decisions = read(root, "planning/decisions.json")["decisions"]
        endpoints = read(root, "contracts/endpoints.json")["endpoints"]
        state = read(root, "planning/execution-state.json")
    except (ValueError, OSError, KeyError) as e:
        return ["Unreadable planning input: " + str(e)]

    def index(items, name):
        result = {}
        for item in items:
            key = item.get("id")
            if not key or key in result:
                errors.append(f"{name}: missing or duplicate id {key}")
            result[key] = item
        return result

    si, ri, ti, gi, di = (index(items, name) for items, name in
                         [(stories, "stories"), (rules, "rules"),
                          (scenarios, "scenarios"), (gates, "gates"),
                          (decisions, "decisions")])
    if state.get("current_phase") not in gi:
        errors.append("Checkpoint refers to unknown phase")
    if state.get("current_story") is not None and state["current_story"] not in si:
        errors.append("Checkpoint refers to unknown story")

    evidence_cache = {}

    def evidence(path):
        if path in evidence_cache:
            return evidence_cache[path]
        try:
            e = json.loads(inside(root, path).read_text(encoding="utf-8"))
            if e.get("scope") != "PRODUCT_VERIFICATION":
                raise ValueError("Evidence is not product verification")
            if not SHA.fullmatch(e.get("source_commit") or ""):
                raise ValueError("Missing exact source commit")
            if not e.get("environment") or not e.get("started_at") or not e.get("finished_at"):
                raise ValueError("Missing environment/timestamps")
            checks = e.get("checks", [])
            if e.get("status") != "PASS" or not checks or not any(c.get("required") is True for c in checks):
                raise ValueError("Evidence is not a passing nonempty required check set")
            if len({c.get("id") for c in checks}) != len(checks) or any(not c.get("id") for c in checks):
                raise ValueError("Missing/duplicate evidence check IDs")
            for c in checks:
                if c.get("required") is True and c.get("status") != "PASS":
                    raise ValueError("Mandatory check did not pass")
                if c.get("kind") == "command" and c.get("status") == "PASS":
                    if c.get("exit_code") != 0 or not c.get("command") or not c.get("log_path"):
                        raise ValueError("Command PASS requires actual command, exit 0 and log")
                    if not inside(root, c["log_path"]).is_file():
                        raise ValueError("Command log missing")
                elif c.get("kind") == "manual" and c.get("status") == "PASS":
                    if not c.get("reviewer") or not c.get("reference"):
                        raise ValueError("Manual PASS requires reviewer and reference")
                elif c.get("kind") not in {"command", "manual"}:
                    raise ValueError("Unknown check kind")
            if not e.get("scenario_results"):
                raise ValueError("No observed scenario outcomes")
            for a in e.get("artifacts", []):
                if not inside(root, a).is_file():
                    raise ValueError("Missing evidence artifact: " + a)
            review = e.get("review", {})
            if review.get("status") != "PASS" or not review.get("reviewer"):
                raise ValueError("Missing passing attributable review")
            if any(d.get("severity") in {"critical", "high"} and d.get("status") != "CLOSED"
                   for d in e.get("open_defects", [])):
                raise ValueError("Unresolved critical/high defect")
            evidence_cache[path] = e
            return e
        except (ValueError, OSError, KeyError, TypeError) as ex:
            errors.append(f"Invalid evidence {path}: {ex}")
            evidence_cache[path] = None
            return None

    for s in stories:
        sid = s["id"]
        if s.get("status") not in {"TODO", "IN_PROGRESS", "BLOCKED", "DONE"}:
            errors.append(f"{sid}: invalid status")
        if s.get("phase") not in gi:
            errors.append(f"{sid}: unknown phase")
        for field in ("implementation_steps", "artifacts", "acceptance", "scenario_ids"):
            if not s.get(field):
                errors.append(f"{sid}: empty {field}")
        for dep in s.get("depends_on", []):
            if dep not in si or dep == sid:
                errors.append(f"{sid}: invalid dependency {dep}")
            elif s["status"] == "DONE" and si[dep]["status"] != "DONE":
                errors.append(f"{sid}: DONE with incomplete dependency {dep}")
        for rid in s.get("rule_ids", []):
            if rid not in ri or ri[rid].get("story_id") != sid:
                errors.append(f"{sid}: inconsistent rule {rid}")
        for tid in s.get("scenario_ids", []):
            if tid not in ti or ti[tid].get("story_id") != sid:
                errors.append(f"{sid}: inconsistent scenario {tid}")
        if s["status"] == "DONE":
            if s.get("review_status") != "PASS" or not s.get("evidence"):
                errors.append(f"{sid}: DONE without evidence/review")
            if any(ti.get(t, {}).get("status") != "PASS" for t in s["scenario_ids"]):
                errors.append(f"{sid}: DONE with unpassed scenarios")
            for p in s.get("evidence", []):
                evidence(p)
    visiting, visited = set(), set()

    def visit(sid):
        if sid in visiting:
            errors.append("Dependency cycle at " + sid)
            return
        if sid in visited or sid not in si:
            return
        visiting.add(sid)
        for dep in si[sid].get("depends_on", []):
            visit(dep)
        visiting.remove(sid)
        visited.add(sid)

    for sid in si:
        visit(sid)
    for r in rules:
        if r.get("story_id") not in si or r["id"] not in si.get(r.get("story_id"), {}).get("rule_ids", []):
            errors.append(f"{r['id']}: no owning story")
        if not r.get("scenario_ids") or any(t not in ti or r["id"] not in ti[t].get("rule_ids", []) for t in r.get("scenario_ids", [])):
            errors.append(f"{r['id']}: missing/inconsistent scenario coverage")
    for t in scenarios:
        if t.get("status") not in {"NOT_RUN", "PASS", "FAIL", "BLOCKED"}:
            errors.append(f"{t['id']}: invalid result")
        if t.get("story_id") not in si or t.get("phase") != si.get(t.get("story_id"), {}).get("phase"):
            errors.append(f"{t['id']}: invalid story/phase")
        for rid in t.get("rule_ids", []):
            if rid not in ri:
                errors.append(f"{t['id']}: unknown rule {rid}")
        if any(not t.get(f) for f in ("given", "when", "then")):
            errors.append(f"{t['id']}: missing observable acceptance")
        if t["status"] == "PASS":
            found = False
            for path in t.get("evidence", []):
                e = evidence(path)
                if e and any(x.get("id") == t["id"] and x.get("status") == "PASS" and x.get("observed")
                             for x in e["scenario_results"]):
                    found = True
            if not found:
                errors.append(f"{t['id']}: PASS without observed attributable evidence")
    routes = set()
    for e in endpoints:
        key = (e.get("method"), e.get("path"))
        if key in routes:
            errors.append("Duplicate API operation: " + str(key))
        routes.add(key)
        if not e.get("permission") or not e.get("contract") or not e.get("response_type"):
            errors.append("Incomplete endpoint: " + e.get("id", "unknown"))
    for g in gates:
        if not g.get("checks") or not any(c.get("required") is True for c in g["checks"]):
            errors.append(f"{g['id']}: no required checks")
        for d in g.get("required_decisions", []):
            if d not in di:
                errors.append(f"{g['id']}: unknown decision {d}")
        for c in g.get("checks", []):
            if c.get("configured") and (not isinstance(c.get("command"), list) or not c["command"]):
                errors.append(f"{c['id']}: configured without command array")
        if len({c.get("id") for c in g.get("checks", [])}) != len(g.get("checks", [])):
            errors.append(f"{g['id']}: duplicate check IDs")
    # Check real Markdown file links; external links and anchors are outside this structural check.
    for md in root.rglob("*.md"):
        if any(x in md.parts for x in (".git", "node_modules", "verification/local")):
            continue
        content = re.sub(r"```.*?```", "", md.read_text(encoding="utf-8"), flags=re.S)
        for link in re.findall(r"\[[^\]]*\]\(([^)]+)\)", content):
            link = link.split("#")[0]
            if not link or "://" in link or link.startswith(("mailto:", "sandbox:")):
                continue
            if not (md.parent / link).exists():
                errors.append(f"Broken link in {md.relative_to(root)}: {link}")
    if phase:
        if phase not in gi or not SHA.fullmatch(commit or ""):
            errors.append("Phase promotion requires known phase and exact --commit SHA")
        else:
            g = gi[phase]
            # A release candidate must re-establish all runtime-phase evidence at
            # its source SHA, not just pass a release checklist over stale tests.
            included = {phase} if phase not in {"P09", "P10"} else {p for p in gi if "P01" <= p <= phase}
            phase_stories = [s for s in stories if s["phase"] in included]
            phase_scenarios = [t for t in scenarios if t["phase"] in included]
            for s in phase_stories:
                if s["status"] != "DONE":
                    errors.append(f"Promotion blocked: {s['id']} is {s['status']}")
            for t in phase_scenarios:
                if t["status"] != "PASS":
                    errors.append(f"Promotion blocked: {t['id']} is {t['status']}")
                elif not any((e := evidence(p)) and e["source_commit"] == commit and
                             any(x.get("id") == t["id"] and x.get("status") == "PASS" for x in e["scenario_results"])
                             for p in t.get("evidence", [])):
                    errors.append(f"Promotion blocked: {t['id']} lacks evidence at candidate commit")
            for d in g["required_decisions"]:
                if di[d]["status"] != "ACCEPTED" or not di[d].get("approval_reference"):
                    errors.append("Promotion blocked: unresolved decision " + d)
            current = [e for e in evidence_cache.values() if e and e["source_commit"] == commit and e.get("phase") in included]
            for included_phase in included:
                for c in gi[included_phase]["checks"]:
                    if c["required"] and not any(any(x.get("id") == c["id"] and x.get("status") == "PASS" for x in e["checks"]) for e in current):
                        errors.append("Promotion blocked: missing passing check " + c["id"])
            for approval in g["required_approvals"]:
                if not any(any(a.get("type") == approval and a.get("actor") and a.get("reference") and a.get("source_commit") == commit for a in e.get("approvals", [])) for e in current):
                    errors.append("Promotion blocked: missing approval " + approval)
    return errors


def main():
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument("--phase")
    p.add_argument("--commit")
    args = p.parse_args()
    errors = validate(phase=args.phase, commit=args.commit)
    if errors:
        print("BLOCKED/INVALID: " + str(len(errors)) + " issue(s)")
        print("\n".join("- " + e for e in errors))
        return 2
    print("PASS: " + ("phase promotion prerequisites" if args.phase else "blueprint structure only; product remains unverified"))
    return 0


if __name__ == "__main__":
    sys.exit(main())
