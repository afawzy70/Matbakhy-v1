# Defect register (machine-readable: defect-register.json)

| ID | Sev | Component | Status | Title |
|---|---|---|---|---|
| CR-001 | High | Deposit and cash accounting | VERIFIED_CI_AND_INDEPENDENT_REVIEW | Cash reporting and verification demand the full total after a deposit |
| CR-002 | Medium | Staff authentication | CLOSED_IN_CODE_OPEN_FOR_LIVE_REALM | Staff grants become usable without MFA |
| CR-003 | High | Private document upload integrity | VERIFIED_CI_AND_INDEPENDENT_REVIEW | Reusable upload URL can replace an approved private document |
| QA-001 | High | Deposit/cash integration | VERIFIED_CI_AND_INDEPENDENT_REVIEW | Cash reporting and verification charge the full total after a deposit |
| QA-002 | Medium | Permanent order idempotency | VERIFIED_CI_AND_INDEPENDENT_REVIEW | Permanent request recovery accepts a changed payload when its quote is unchanged |
| QA-003 | Medium | Deposit concurrency coverage | VERIFIED_CI_AND_INDEPENDENT_REVIEW | Deposit expiry coverage does not exercise transfer/receipt races |
| QA-004 | Medium | Cross-kitchen authorization coverage | VERIFIED_CI_AND_INDEPENDENT_REVIEW | Receipt authorization test uses a non-cook instead of a foreign kitchen owner |
| QA-005 | Medium | Revoked-session authorization coverage | WITHDRAWN_ALREADY_COVERED | Revocation test reauthenticates instead of reusing the existing authentication |
| QA-006 | Medium | Pricing integration coverage | VERIFIED_CI_AND_INDEPENDENT_REVIEW | Price-race tests replace the production price-update command with handwritten SQL |
| QA-007 | Low | Contract and browser coverage | OPEN | Route and mocked component checks do not validate real response payload contracts |
| CR2-001 | Medium | Media storage | VERIFIED_CI_AND_INDEPENDENT_REVIEW | Upload cleanup failure cannot be recovered by re-completion |
| QA2-001 | Informational | tooling/environment | DOCUMENTED |  |
| QA2-002 | Informational | tooling/environment | DOCUMENTED |  |
| QA2-003 | Informational | tooling/environment | DOCUMENTED |  |
| QA2-004 | Informational | tooling/environment | DOCUMENTED |  |
| QA2-005 | Informational | tooling/environment | DOCUMENTED |  |
| SEC-001 | Medium | Media/PDF | VERIFIED_CI_AND_INDEPENDENT_REVIEW | PDF active-content validation bypass via escaped names |
| SEC-002 | High | Media | VERIFIED_CI_AND_INDEPENDENT_REVIEW | Approved private document mutable through outstanding upload ticket (= CR-003) |
| SEC-003 | Low | scripts/rehearsal/load.py | VERIFIED_CI_AND_INDEPENDENT_REVIEW | Rehearsal helper disables TLS verification |
| SEC-004 | Low | deploy/web-preview/Caddyfile | MITIGATED_REPORT_ONLY_FOLLOWUP_DOCUMENTED | No CSP on web HTML |
| DEP-TOMCAT | High (scanner label Critical) | backend deps | VERIFIED_CI_AND_INDEPENDENT_REVIEW | tomcat-embed-core 11.0.24 advisories |
| DEP-JACKSON | High (scanner label) | backend deps | VERIFIED_CI_AND_INDEPENDENT_REVIEW | jackson 3.1.5 advisories |
| DEP-NODE | High/Moderate | mobile tooling | OPEN_DOCUMENTED | node-forge, braces, uuid, sprintf-js transitive dev-tool advisories |
| D18-REVIEWS | High | MFA policy | VERIFIED_CI_AND_INDEPENDENT_REVIEW | Independent review rounds 1-4 of the D18 configurable MFA (CR7-*, SECR7-*, CR8-*, SECR8-*, CR9-*, CR10, SECR10, QA7-9): profile writes, reset epoch, application sessions, DISABLED step-up, audit ordering, dev secrets |
| D18-RESIDUAL-1 | Medium | Keycloak live realm | OPEN_OPERATOR_ACTION | Existing Contabo preview realm lacks the D18 flow, levels and admin client until migrate-mfa.sh is run |
| D18-RESIDUAL-2 | Medium | Mobile MFA | OPEN_DEVICE_VERIFICATION | Mobile step-up banner implemented and jest/simulator-CI covered; no physical device run; no in-app Security screen |
| D18-RESIDUAL-3 | Low | DISABLED policy | ACCEPTED_DOCUMENTED | A user can still add an authenticator directly in Keycloak account console under DISABLED; it has no effect in the application |
| D18-RESIDUAL-4 | Low | Tokens | ACCEPTED_DOCUMENTED | Access tokens issued before a reset or switch-off stay valid up to 5 minutes for non-MFA functions; they no longer count as MFA proof for the policy or high-risk actions |
| CR12-1 | High | Customer mobile | VERIFIED_CI_AND_INDEPENDENT_REVIEW | Account switch during step-up left the previous account's screens, basket or pending order reachable |
| CR12-2 | Medium | Customer mobile | VERIFIED_CI_AND_INDEPENDENT_REVIEW | Step-up did not recover the refused request; order placement must not be replayed by itself |
| CR12-3 | Medium | Migration tooling | VERIFIED_CI_AND_INDEPENDENT_REVIEW | Rollback state was shell-sourced from an untrusted flow name |
| CR12-4 | Medium | Migration tooling | VERIFIED_LOCAL_REHEARSAL_AND_REVIEW | Verification could report PASS from configuration alone |
| CR12-5 | Medium | Migration tooling | VERIFIED_LOCAL_TEST_AND_REVIEW | Fixture cleanup could report success while leaving users; apply turned INCOMPLETE verification into exit 0 |
