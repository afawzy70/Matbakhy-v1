# Correction (2026-10-13): SECRET-SCAN history part did not run

This run was executed from a git worktree. In a worktree, `.git` points to the main repository's git directory, which
was not mounted into the Gitleaks container. The history scan therefore reported "not a git repository" and **"0 commits
scanned"**, and `scripts/check_secrets.sh` still printed PASS. The working-tree scan in the same log is valid.

- Recorded result kept as written; the SECRET-SCAN **history** coverage of this run is **invalid (not executed)**.
- Meanwhile Product CI's Gitleaks job (full history, all fetched branches) was failing from the publication of the
  independent QA branch commit `8707b23`: 20 generic-api-key matches on synthetic `ops-<uuid>` Idempotency-Key values in
  QA evidence (not in `implementation/mvp`; reviewed false positives, listed by fingerprint in `.gitleaksignore`).
- Fix and re-execution: see `verification/runs/20261013-SECRET-SCAN-FIX/`. The script now mounts the worktree's git
  directory and fails when no commit was scanned. A full-history scan covering this run's source commit found no
  secret in `implementation/mvp` history.
