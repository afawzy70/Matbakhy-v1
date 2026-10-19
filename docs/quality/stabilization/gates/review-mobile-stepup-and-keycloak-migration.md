# Code and security review of the mobile step-up and the Keycloak preview-realm migration (independent, static)

Agent: independent Codex CLI 0.163.0 read-only, stdin </dev/null. Commit inspected: `fef001052ee52fdbbcb616460ecb45f10287acc5`.

Summary (agent): Found two actionable defects. HEAD matches the required SHA. Static migration checks passed; mobile/native, backend integration and live Keycloak checks were not run. No additional evidence-backed finding in response cloning, cancellation, token logging or the new reset-proof test. Review was read-only.

## Findings

### CR11-1 [High] Step-up accepts another subject without clearing shopping or displayed private state
`apps/customer-mobile/src/AppContext.tsx:164`
- Scenario: Customer A presses Verify now and authenticates as customer B in the fresh browser login.
- Suggested fix: Compare subjects across verification. On a change, clear shopping and pending placement, reset mounted private screens, and load the new profile before displaying the signed-in account. Preserve shopping only for the same subject.
- Disposition: fixed in the next commit (see git log)

### CR11-2 [Medium] Repeated apply overwrites the rollback baseline and writes an invalid shell assignment
`deploy/web-preview/migrate-mfa.sh:50`
- Scenario: Run apply successfully, then repeat apply as documented, or run backup afterward, and subsequently request rollback.
- Suggested fix: Preserve an immutable original migration baseline across repeats, serialize aliases safely without sourcing unescaped data, and reject rollback targets equal to the flow being deleted.
- Disposition: fixed in the next commit (see git log)
