# Candidate C attempt at 31025fc: FAIL (secret scan), not published as a candidate

- Five of six local checks PASS at `31025fc54836ded43daf586e10b3e204497c7bf5`: backend verify, clean-clone workspace,
  local stack, browser journeys (5/5, including the D13/D14 journey) and guardrail tests.
- **SECRET-SCAN FAIL:** 228 commits scanned, 1 finding: generic-api-key on a runbook command line (a health check run
  inside the api container against the management port) introduced at `a8b206e`, line 46 of
  `docs/operations/08-troubleshooting-runbook.md`. It is command text, not a credential (reviewed with a JSON report;
  the matched value is a host, port and path).
- Repair: the line is reworded so the working tree no longer matches, and only that one historical fingerprint is added
  to `.gitleaksignore` with its review note. No rule, file or path exclusion; the fail-on-zero guard is unchanged.
- Because the failed check belongs to this SHA, `31025fc` is **not** a QA candidate. The six checks are re-run at a
  new SHA (see `docs/quality/09-candidate-c-pricing-history.md`).
