#!/usr/bin/env python3
"""Owner E2E catalog: validate the case sources and render the Markdown + traceability outputs.

Sources (the only hand-edited files)   docs/quality/owner-acceptance/cases/*.json   case definitions
                                        docs/quality/owner-acceptance/core-suite.json  the ordered owner acceptance journeys
                                        docs/quality/owner-acceptance/coverage-notes.json  TC ids that no manual case covers, with the reason
Generated (do not edit)                 docs/quality/owner-acceptance/generated/*.md, traceability.json

  python3 scripts/owner_e2e_catalog.py check     validate sources and fail when the generated files are stale
  python3 scripts/owner_e2e_catalog.py render    validate, then rewrite the generated files

A case can only be NOT_RUN or BLOCKED here. A PASS never lives in the catalog: results are recorded per run, with evidence,
under verification/runs/<run-id>/ (see docs/quality/owner-acceptance/README.md).
"""
import json
import re
import sys
from collections import defaultdict
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
BASE = ROOT / "docs" / "quality" / "owner-acceptance"
CASES_DIR = BASE / "cases"
GENERATED = BASE / "generated"
FIXTURE_KEYS = {
    "FX-GUEST", "FX-CUST-A", "FX-CUST-B", "FX-CUST-NEW", "FX-COOK-A", "FX-COOK-B", "FX-COOK-PENDING", "FX-COOK-SUSPENDED",
    "FX-STAFF-ADMIN", "FX-STAFF-NOGRANT", "FX-STAFF-SUPPORT", "FX-STAFF-OPS", "FX-STAFF-FINANCE", "FX-STAFF-ADMIN-B", "FX-MULTIROLE",
    "FX-ISOLATED", "FX-DEVICE-IOS", "FX-DEVICE-ANDROID",
}
EXISTING_FIXTURES = {"FX-GUEST", "FX-CUST-A", "FX-CUST-B", "FX-CUST-NEW", "FX-COOK-A", "FX-STAFF-ADMIN", "FX-STAFF-NOGRANT"}
STATUSES = {"NOT_RUN", "BLOCKED"}
PRIORITIES = {"P0", "P1", "P2"}
BOILERPLATE = (
    "Record the current build SHA, selected account/role and isolate the synthetic fixture for this case",
    "Check both visible UI state and the relevant protected API result",
    "Inspect authoritative DB/audit/capacity/finance evidence",
)
SECRET_PATTERNS = (
    re.compile(r"(?i)\b(password|passwd|secret|token|otp[_ ]?seed|api[_-]?key)\s*[:=]\s*['\"]?[A-Za-z0-9+/_\-]{8,}"),
    re.compile(r"eyJ[A-Za-z0-9_\-]{10,}\.[A-Za-z0-9_\-]{10,}\."),
    re.compile(r"(?i)bearer\s+[A-Za-z0-9._\-]{20,}"),
)


FORBIDDEN_KEY_PARTS = ("password", "passwd", "secret", "token", "otp", "apikey", "credential", "cookie", "bearer", "privatekey")
RESULT_KEYS = {"result", "results", "outcome", "verdict", "actual", "actualresult", "observed", "executionresult", "testresult", "lastresult", "passed"}
PASS_CLAIM = re.compile(r"(?i)\b(status|execution result|test result|actual( result)?|observed|outcome|verdict)\b\s*[:=\-]+\s*\**pass(ed)?\b|\bpass(ed)?\s+on\s+\d{4}-|\ball\s+(checks|tests|steps|cases|scenarios)\s+pass(ed)?\b|^\W*pass(ed)?\W*$")
SHORT_ASSIGNMENT = re.compile(r"(?i)\b(access[_-]?token|refresh[_-]?token|id[_-]?token|client[_-]?secret|password|passwd|api[_-]?key)\b\s*[:=]\s*['\"]?[A-Za-z0-9+/_\-.]{6,}")


def forbidden_key(key):
    norm = re.sub(r"[^a-z0-9]", "", key.lower())
    return any(part in norm for part in FORBIDDEN_KEY_PARTS)


def suspicious(where, kind, text):
    """Problems in one key or string value of a source document (credential-like or a recorded PASS)."""
    out = []
    if kind == "key":
        if forbidden_key(text):
            out.append(f"credential-bearing field {where}")
        return out
    parent = where.rsplit("/", 1)[-1].lower()
    if re.sub(r"[^a-z]", "", parent) in RESULT_KEYS and re.match(r"(?i)^\W*pass(ed)?\b", text):
        out.append(f"a result field claims PASS at {where}")
    if PASS_CLAIM.search(text):
        out.append(f"claims a PASS result at {where} (results live in run records, not in the catalog)")
    if SHORT_ASSIGNMENT.search(text) or any(p.search(text) for p in SECRET_PATTERNS):
        out.append(f"looks like a credential/secret/token at {where}")
    return out


def walk_strings(node, path=""):
    """Every key and string value of a parsed JSON document, with its path."""
    if isinstance(node, dict):
        for k, v in node.items():
            yield path + "/" + k, "key", k
            yield from walk_strings(v, path + "/" + k)
    elif isinstance(node, list):
        for i, v in enumerate(node):
            yield from walk_strings(v, f"{path}[{i}]")
    elif isinstance(node, str):
        yield path, "value", node


def load_json(path):
    with open(path, encoding="utf-8") as f:
        return json.load(f)


SOURCE_PROBLEMS = []


def load_cases():
    cases, files = [], sorted(CASES_DIR.glob("*.json"))
    SOURCE_PROBLEMS.clear()
    for f in files:
        data = load_json(f)
        # The whole document (conventions, findings, dispositions ... as well as the cases) is rendered or kept, so all of it is scanned.
        for where, kind, text in walk_strings(data):
            for problem in suspicious(where, kind, text):
                SOURCE_PROBLEMS.append(f"{f.name}: {problem}")
        for c in data["cases"]:
            c["_domain"] = data.get("domain", f.stem)
            cases.append(c)
    return cases, files


def validate(cases):
    errors = []
    scenarios = {s["id"]: s for s in load_json(ROOT / "planning" / "scenarios.json")["scenarios"]}
    rules = {r["id"] for r in load_json(ROOT / "planning" / "rules.json")["rules"]}
    seen = set()
    errors.extend(SOURCE_PROBLEMS)
    for c in cases:
        cid = c.get("id", "<missing id>")
        if cid in seen:
            errors.append(f"{cid}: duplicate id")
        seen.add(cid)
        for key in ("title", "priority", "surface", "actors", "prerequisites", "steps", "expected", "refs", "readiness", "status"):
            if key not in c or c[key] in (None, "", []):
                if not (key == "prerequisites" and c.get(key) == []):
                    errors.append(f"{cid}: missing {key}")
        if c.get("status") not in STATUSES:
            errors.append(f"{cid}: status {c.get('status')!r} (only NOT_RUN or BLOCKED may be authored; a PASS needs a recorded run)")
        if c.get("status") == "BLOCKED" and not c.get("blocked_reason"):
            errors.append(f"{cid}: BLOCKED without blocked_reason")
        if c.get("priority") not in PRIORITIES:
            errors.append(f"{cid}: priority {c.get('priority')!r}")
        for a in c.get("actors", []) + c.get("readiness", {}).get("needs_fixtures", []):
            if a not in FIXTURE_KEYS:
                errors.append(f"{cid}: unknown fixture key {a}")
        r = c.get("readiness", {})
        for flag in ("needs_updated_deploy", "needs_device", "needs_isolated_stack"):
            if not isinstance(r.get(flag), bool):
                errors.append(f"{cid}: readiness.{flag} must be true/false")
        steps = c.get("steps", [])
        if len(steps) < (1 if c.get("status") == "BLOCKED" else 2):
            errors.append(f"{cid}: needs at least 2 concrete steps")
        for i, s in enumerate(steps, 1):
            if s.get("n") != i or not s.get("action"):
                errors.append(f"{cid}: step {i} malformed")
        exp = c.get("expected", {})
        if not (exp.get("ui") or exp.get("api")):
            errors.append(f"{cid}: expected needs ui and/or api outcomes")
        if "database" not in exp:
            errors.append(f"{cid}: expected.database missing (use ['none'] when not applicable)")
        refs = c.get("refs", {})
        if not (refs.get("scenarios") or refs.get("partial") or refs.get("rules") or refs.get("decisions")):
            errors.append(f"{cid}: no traceability (scenarios/rules/decisions)")
        for tc in refs.get("scenarios", []) + refs.get("partial", []):
            if tc not in scenarios:
                errors.append(f"{cid}: unknown scenario {tc}")
        for br in refs.get("rules", []):
            if br not in rules:
                errors.append(f"{cid}: unknown rule {br}")
        blob = json.dumps(c, ensure_ascii=False)
        for b in BOILERPLATE:
            if b in blob:
                errors.append(f"{cid}: generic boilerplate step '{b[:40]}...'")
        if re.search(r"\bPASS(ED)?\b", json.dumps({k: v for k, v in c.items() if k in ('status',)})):
            errors.append(f"{cid}: PASS in status")
    return errors, scenarios, rules


def validate_suite(cases):
    errors, ids = [], {c["id"] for c in cases}
    for name in ("core-suite.json", "coverage-notes.json", "fixtures.json"):
        path = BASE / name
        if path.exists():
            for where, kind, text in walk_strings(load_json(path)):
                errors.extend(f"{name}: {p}" for p in suspicious(where, kind, text))
    suite_path = BASE / "core-suite.json"
    if not suite_path.exists():
        return ["core-suite.json missing"], None
    suite = load_json(suite_path)
    for j in suite["journeys"]:
        for cid in j["cases"]:
            if cid not in ids:
                errors.append(f"core suite {j['id']}: unknown case {cid}")
    return errors, suite


def readiness_bucket(c):
    r = c["readiness"]
    if r["needs_device"]:
        return "C_DEVICE"
    if r["needs_updated_deploy"]:
        return "B_UPDATED_DEPLOY"
    if r["needs_isolated_stack"]:
        return "D_ISOLATED"
    if r["needs_fixtures"]:
        return "A2_FIXTURES"
    return "A1_NOW"


BUCKET_TITLES = {
    "A1_NOW": "Ready to run manually now (deployed preview, existing seed accounts only)",
    "A2_FIXTURES": "Ready after the extra synthetic fixtures are provisioned (deployed app is enough)",
    "B_UPDATED_DEPLOY": "Needs the updated application deployment (PR #9 / D18, and for MFA cases the owner-approved realm migration)",
    "C_DEVICE": "Needs a physical iOS/Android build",
    "D_ISOLATED": "Isolated disposable stack only (races, fault injection, destructive)",
}


def md_escape(t):
    return str(t).replace("|", "\\|")


def render_case(c):
    out = [f"### {c['id']} — {c['title']}", ""]
    r = c["readiness"]
    flags = [k for k, v in (("updated deploy", r["needs_updated_deploy"]), ("physical device", r["needs_device"]), ("isolated stack", r["needs_isolated_stack"])) if v]
    out.append(f"- **Status:** {c['status']}" + (f" — {c['blocked_reason']}" if c.get("blocked_reason") else "") + f" · **Priority:** {c['priority']} · **Readiness:** {BUCKET_TITLES[readiness_bucket(c)].split(' (')[0]}")
    out.append(f"- **Surface:** {', '.join(c['surface'])} · **Actors:** {', '.join(c['actors'])}" + (f" · **Needs:** {', '.join(flags)}" if flags else "") + (" · **Destructive**" if c.get("destructive") else ""))
    if r["needs_fixtures"]:
        out.append(f"- **Fixtures to provision:** {', '.join(r['needs_fixtures'])}")
    refs = c["refs"]
    out.append("- **Traces to:** " + "; ".join(f"{k}: {', '.join(v)}" for k, v in refs.items() if v))
    out.append("- **Prerequisites:**")
    out += [f"  - {p}" for p in c["prerequisites"]] or ["  - none"]
    out.append("- **Steps:**")
    for s in c["steps"]:
        data = f" — data: {s['data']}" if s.get("data") else ""
        out.append(f"  {s['n']}. ({s['actor']}) {s['action']}{data}")
    out.append("- **Expected:**")
    for k in ("ui", "api", "database"):
        for e in c["expected"].get(k, []):
            out.append(f"  - {k.upper() if k != 'database' else 'DB'}: {e}")
    if c.get("automated_coverage"):
        out.append("- **Also covered by automated tests:** " + "; ".join(c["automated_coverage"]))
    if c.get("unverified"):
        out.append("- **Unverified against code (confirm while executing):** " + "; ".join(c["unverified"]))
    out.append("")
    return "\n".join(out)


def build(cases, scenarios, rules, suite):
    by_tc, by_br, by_partial = defaultdict(list), defaultdict(list), defaultdict(list)
    for c in cases:
        for tc in c["refs"].get("scenarios", []):
            by_tc[tc].append(c["id"])
        for tc in c["refs"].get("partial", []):
            by_partial[tc].append(c["id"])
        for br in c["refs"].get("rules", []):
            by_br[br].append(c["id"])
    notes_path = BASE / "coverage-notes.json"
    notes = load_json(notes_path) if notes_path.exists() else {}
    files = {}
    # per-domain case documents
    domains = defaultdict(list)
    for c in cases:
        domains[c["_domain"]].append(c)
    for d, cs in domains.items():
        body = [f"# Owner E2E cases — {d}", "", "Generated from `cases/*.json` by `scripts/owner_e2e_catalog.py`; do not edit. Every case is NOT_RUN or BLOCKED until a run records evidence.", ""]
        conv = next((load_json(f).get("conventions") for f in sorted(CASES_DIR.glob("*.json")) if load_json(f).get("domain", f.stem) == d and load_json(f).get("conventions")), None)
        if conv:
            body += ["## Shared conventions used by the cases below", "", "```json", json.dumps(conv, indent=1, ensure_ascii=False), "```", ""]
        body += [render_case(c) for c in cs]
        files[f"cases-{d}.md"] = "\n".join(body)
    # core suite
    by_id = {c["id"]: c for c in cases}
    core = ["# Core owner acceptance suite", "",
            "Ordered end-to-end journeys (Customer → Kitchen → Admin/Finance). Run in order on one synthetic run id; every case is NOT_RUN until executed with evidence.", ""]
    for j in suite["journeys"]:
        core += [f"## {j['id']} — {j['title']}", "", j["purpose"], "", "| # | Case | Title | Readiness | Status |", "|---|---|---|---|---|"]
        for i, cid in enumerate(j["cases"], 1):
            c = by_id[cid]
            core.append(f"| {i} | {cid} | {md_escape(c['title'])} | {BUCKET_TITLES[readiness_bucket(c)].split(' (')[0]} | {c['status']} |")
        core.append("")
    files["core-owner-acceptance-suite.md"] = "\n".join(core)
    # readiness
    buckets = defaultdict(list)
    for c in cases:
        buckets[readiness_bucket(c)].append(c)
    rd = ["# Readiness report", "", f"{len(cases)} cases. Counts by what the tester needs; a case appears in exactly one bucket (precedence: device, then updated deployment, then isolated stack, then fixtures), and every entry lists ALL its requirements.", ""]
    for b in sorted(BUCKET_TITLES):
        cs = buckets.get(b, [])
        rd += [f"## {BUCKET_TITLES[b]} — {len(cs)}", ""]
        def needs(c):
            r = c["readiness"]
            return ", ".join(x for x, f in (("updated deploy", r["needs_updated_deploy"]), ("device", r["needs_device"]), ("isolated stack", r["needs_isolated_stack"])) if f)
        rd += [f"- {c['id']} ({c['priority']}) {c['title']}" + (f" — needs: {needs(c)}" if needs(c) else "") + (f" — fixtures: {', '.join(c['readiness']['needs_fixtures'])}" if c['readiness']['needs_fixtures'] else "") for c in cs]
        rd.append("")
    files["readiness-report.md"] = "\n".join(rd)
    # traceability
    tr = ["# Requirements-to-test traceability matrix", "",
          "Manual owner cases per planning scenario (TC) and business rule (BR). `AUTOMATED_ONLY` = the scenario is verified by CI at a level an owner cannot perform by hand (see its level); `UNCOVERED` rows carry the reason.", "",
          "## Scenarios", "", "| TC | Level | Rule | Title | Owner cases | Coverage |", "|---|---|---|---|---|---|"]
    uncovered = []
    for tc, s in scenarios.items():
        cs = by_tc.get(tc, [])
        note = notes.get(tc)
        if cs:
            cov = "MANUAL"
        elif by_partial.get(tc):
            cov = "PARTIAL"
            cs = by_partial[tc]
        elif note:
            cov = note["classification"]
        else:
            cov = "UNCOVERED"
            uncovered.append(tc)
        reason = f" — {note['reason']}" if (cov not in ("MANUAL", "PARTIAL") and note) else (" — a relevant part is exercised; the full given/when/then is not" if cov == "PARTIAL" else "")
        tr.append(f"| {tc} | {s['level']} | {', '.join(s['rule_ids'])} | {md_escape(s['title'])} | {', '.join(cs) or '—'} | {cov}{md_escape(reason)} |")
    tr += ["", "## Business rules", "", "| BR | Domain | Owner cases |", "|---|---|---|"]
    rule_rows = {r["id"]: r for r in load_json(ROOT / "planning" / "rules.json")["rules"]}
    for br, r in rule_rows.items():
        tr.append(f"| {br} | {r['domain']} | {', '.join(by_br.get(br, [])) or '—'} |")
    tr += ["", "## Case to requirement (reverse)", "", "| Case | Scenarios | Rules | Decisions | Operations |", "|---|---|---|---|---|"]
    for c in cases:
        r = c["refs"]
        tr.append(f"| {c['id']} | {', '.join(r.get('scenarios', []))} | {', '.join(r.get('rules', []))} | {', '.join(r.get('decisions', []))} | {md_escape(', '.join(r.get('operations', [])))} |")
    files["traceability-matrix.md"] = "\n".join(tr)
    rf = ["# Review of the original PR #12 catalog", "",
          "Original: 108 case headings (62 functional + 46 configuration/security), not the 121 the PR text claims; none cited a TC or BR; every functional case repeated the same generic steps. Findings and per-case dispositions below come from the domain authors' source review (file:line evidence inside each finding).", ""]
    for f in sorted(CASES_DIR.glob("*.json")):
        data = load_json(f)
        rf += [f"## {data.get('domain', f.stem)}", "", "### Findings", ""]
        rf += [f"- {x}" for x in data.get("findings", [])] or ["- none"]
        rf += ["", "### Dispositions of the original cases", "", "| Original | Action | Reason |", "|---|---|---|"]
        rf += [f"| {d['original']} | {md_escape(d['action'])} | {md_escape(d['reason'])} |" for d in data.get("dispositions", [])]
        rf.append("")
    files["review-findings.md"] = "\n".join(rf)
    summary = {
        "cases": len(cases),
        "by_status": {s: sum(1 for c in cases if c["status"] == s) for s in sorted(STATUSES)},
        "by_priority": {p: sum(1 for c in cases if c["priority"] == p) for p in sorted(PRIORITIES)},
        "by_readiness": {b: len(buckets.get(b, [])) for b in sorted(BUCKET_TITLES)},
        "scenarios_total": len(scenarios),
        "scenarios_manual": sum(1 for tc in scenarios if by_tc.get(tc)),
        "scenarios_partial_only": sum(1 for tc in scenarios if not by_tc.get(tc) and by_partial.get(tc)),
        "scenarios_uncovered": uncovered,
        "case_to_scenarios": {c["id"]: c["refs"].get("scenarios", []) for c in cases},
    }
    files["traceability.json"] = json.dumps(summary, indent=2, ensure_ascii=False) + "\n"
    return files, uncovered


def main():
    mode = sys.argv[1] if len(sys.argv) > 1 else "check"
    cases, _ = load_cases()
    errors, scenarios, rules = validate(cases)
    suite_errors, suite = validate_suite(cases)
    errors += suite_errors
    if errors:
        print("\n".join(errors[:200]))
        print(f"\n{len(errors)} problem(s)")
        return 1
    files, uncovered = build(cases, scenarios, rules, suite)
    GENERATED.mkdir(parents=True, exist_ok=True)
    if mode == "render":
        for name, text in files.items():
            (GENERATED / name).write_text(text if text.endswith("\n") else text + "\n", encoding="utf-8")
        print(f"rendered {len(files)} files, {len(cases)} cases; scenarios without a manual case: {len(uncovered)}")
        return 0
    stale = [n for n, t in files.items() if not (GENERATED / n).exists() or (GENERATED / n).read_text(encoding="utf-8") != (t if t.endswith("\n") else t + "\n")]
    if stale:
        print("generated files are stale (run render):", ", ".join(stale))
        return 1
    if uncovered:
        print("scenarios neither covered by a manual case nor classified in coverage-notes.json:", ", ".join(uncovered))
        return 1
    print(f"ok: {len(cases)} cases, generated files current, every scenario covered or classified")
    return 0


if __name__ == "__main__":
    sys.exit(main())
