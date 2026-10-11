#!/usr/bin/env python3
"""Read-only eligible work display. Does not auto-complete or authorize work."""
import json
from pathlib import Path

root = Path(__file__).resolve().parents[1]
stories = json.loads((root / "planning/backlog.json").read_text())["stories"]
done = {s["id"] for s in stories if s["status"] == "DONE"}
for s in stories:
    if s["status"] in {"TODO", "IN_PROGRESS"} and set(s["depends_on"]) <= done:
        print(f"{s['id']} [{s['phase']}] {s['title']}")
        print("  Acceptance: " + "; ".join(s["acceptance"]))
        print("  Check decisions, evidence and environment before starting.")
        break
else:
    remaining = [s for s in stories if s["status"] != "DONE"]
    print("No eligible task. Inspect blockers/dependencies." if remaining else "All stories marked DONE; validate evidence and release gates before claiming completion.")
