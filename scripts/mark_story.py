#!/usr/bin/env python3
"""Record a story outcome from an evidence file: sets story/scenario status and links evidence.

Refuses DONE unless the evidence passes, covers every mapped scenario as observed PASS and
carries a PASS review. Run validate_blueprint.py afterwards; this tool is bookkeeping only.
Usage: mark_story.py MB-xxx verification/runs/<run>/evidence.json [--note TEXT]
"""
import argparse
import json
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]


def main():
    p = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    p.add_argument("story")
    p.add_argument("evidence")
    p.add_argument("--note", default=None)
    a = p.parse_args()
    ev = json.loads((ROOT / a.evidence).read_text(encoding="utf-8"))
    backlog = json.loads((ROOT / "planning/backlog.json").read_text(encoding="utf-8"))
    scen = json.loads((ROOT / "planning/scenarios.json").read_text(encoding="utf-8"))
    story = next(s for s in backlog["stories"] if s["id"] == a.story)
    results = {r["id"]: r for r in ev["scenario_results"]}
    missing = [t for t in story["scenario_ids"] if results.get(t, {}).get("status") != "PASS" or not results[t].get("observed")]
    if ev.get("status") != "PASS" or ev.get("review", {}).get("status") != "PASS" or missing:
        print(f"REFUSED: evidence not passing/reviewed or scenarios missing: {missing}")
        return 2
    for t in scen["scenarios"]:
        if t["id"] in story["scenario_ids"]:
            t["status"] = "PASS"
            t["evidence"] = sorted(set(t["evidence"]) | {a.evidence})
    story["status"] = "DONE"
    story["review_status"] = "PASS"
    story["evidence"] = sorted(set(story["evidence"]) | {a.evidence})
    story["completion_note"] = a.note or f"Evidence at source {ev['source_commit']} ({ev['review'].get('type')})"
    for path, data in (("planning/backlog.json", backlog), ("planning/scenarios.json", scen)):
        (ROOT / path).write_text(json.dumps(data, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")
    print(f"{a.story} DONE; scenarios {story['scenario_ids']} PASS")
    return 0


if __name__ == "__main__":
    sys.exit(main())
