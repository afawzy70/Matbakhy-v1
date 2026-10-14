# Secret scan fix and re-execution (2026-10-13)

Self-review by the implementation agent.

| Item | Value |
|---|---|
| Problem 1 | Product CI Gitleaks job FAIL on `implementation/mvp` from run 38288423579 (`1d21c00`) onward; earlier runs on later pushes were FAIL or CANCELLED (superseded by newer pushes). Cause: 20 generic-api-key matches in the independent QA branch commit `8707b23` (synthetic `ops-<uuid>` Idempotency-Key values); CI fetches all branches |
| Problem 2 | Local SECRET-SCAN from a git worktree scanned 0 commits and still printed PASS (runs MB-032, CANDIDATE-B, CANDIDATE-B2, QA-REPAIR-2-CHECKS; correction notes in each) |
| Fix | `ed2ac56`: `.gitleaksignore` with 20 reviewed fingerprints (no file or rule exclusions); `scripts/check_secrets.sh` mounts the common git directory for worktrees and fails when no commit was scanned |
| Re-execution | `scripts/check_secrets.sh` in the main checkout at `ed2ac56` (all fetched refs, 214 commits): PASS, log `SECRET-SCAN-ed2ac56.log`; also run from a temporary worktree: 214 commits scanned, PASS; the new guard rejects the old zero-commit output |
| Guardrails | `validate_blueprint.py` PASS; `check_scope.py` PASS after registering `.gitleaksignore`; `python3 -m unittest discover -s scripts -p 'test_*.py'` OK |
| Product CI | To be confirmed on the push of this evidence (see Actions history) |

Coverage note: a full-history scan includes every ancestor of the scanned head, so the source commits of the four affected runs (`cdc592a`, `a8132aa`, `f5d2ba0`, `4555f59`) are now covered by a real history scan with no findings in `implementation/mvp`. That does not change what those runs recorded at the time.
