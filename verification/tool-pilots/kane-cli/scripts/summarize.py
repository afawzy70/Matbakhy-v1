#!/usr/bin/env python3
"""Print status, summary, final_state, credits and test_url from a Kane --agent NDJSON file (no credentials involved)."""
import json, sys
for l in open(sys.argv[1]):
    d = json.loads(l)
    if d.get("type") == "run_end":
        print(json.dumps({k: d.get(k) for k in ("status", "summary", "reason", "final_state", "duration", "credits_consumed", "test_url")}, ensure_ascii=False, indent=1))
