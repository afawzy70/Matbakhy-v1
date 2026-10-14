# MB-026 review (self-review, not independent)

Reviewer: Claude Code implementation agent (self-review).

## Scope
V19 feature flag and configuration change log, configuration API (API-089/090) for commerce policy, zones and the ordering switch, operations web configuration and staff permission screens.

## Findings
1. Only new quotes use a change: existing quote revalidation already compares policy version and totals; the test proves a placed order keeps its fee, a stale quote fails with TOTAL_CHANGED / POLICY_CHANGED and a new quote uses the new values.
2. Live approval guard: a real policy cannot be activated without an approval reference (service) and the database already refuses an unapproved active real policy. Mutation removing the guard failed the test.
3. Ordering switch is enforced where the active policy is loaded (quotes and placement); fulfilment of placed orders continues. Mutation ignoring the flag failed the test.
4. The browser client percent-encodes keys such as `zone:<id>`; a backend test sends the encoded path to prove the server accepts it.
5. Staff controls (self-escalation refusal, last administrator protection, MFA for grant changes) are the MB-011 implementation, re-observed through StaffAccessIntegrationTest in the backend verify; MB-026 adds the UI.

## Residual / proposed defaults
- Scheduled (future-dated) configuration changes are refused, not implemented.
- Real policy values (commission, tax, cutoffs) remain owner decision O04; the approval reference is recorded but not verified against an external system.
- Configuration and staff screens are covered by component tests; not in the browser workflow (MFA cannot be satisfied locally).
