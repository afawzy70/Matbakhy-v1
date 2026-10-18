# Defect register (machine-readable: defect-register.json)

| ID | Sev | Component | Status | Title |
|---|---|---|---|---|
| CR-001 | High | Deposit and cash accounting | FIXED_PENDING_RETEST | Cash reporting and verification demand the full total after a deposit |
| CR-002 | Medium | Staff authentication | OPEN_DEPLOYMENT_VERIFICATION | Staff grants become usable without MFA |
| CR-003 | High | Private document upload integrity | FIXED_PENDING_RETEST | Reusable upload URL can replace an approved private document |
| QA-001 | High | Deposit/cash integration | FIXED_PENDING_RETEST | Cash reporting and verification charge the full total after a deposit |
| QA-002 | Medium | Permanent order idempotency | FIXED_PENDING_RETEST | Permanent request recovery accepts a changed payload when its quote is unchanged |
| QA-003 | Medium | Deposit concurrency coverage | TEST_ADDED_PENDING_CI | Deposit expiry coverage does not exercise transfer/receipt races |
| QA-004 | Medium | Cross-kitchen authorization coverage | TEST_ADDED_PENDING_CI | Receipt authorization test uses a non-cook instead of a foreign kitchen owner |
| QA-005 | Medium | Revoked-session authorization coverage | WITHDRAWN_ALREADY_COVERED | Revocation test reauthenticates instead of reusing the existing authentication |
| QA-006 | Medium | Pricing integration coverage | TEST_ADDED_PENDING_CI | Price-race tests replace the production price-update command with handwritten SQL |
| QA-007 | Low | Contract and browser coverage | OPEN | Route and mocked component checks do not validate real response payload contracts |
| CR2-001 | Medium | Media storage | FIXED_PENDING_RETEST | Upload cleanup failure cannot be recovered by re-completion |
| QA2-001 | Informational | tooling/environment | DOCUMENTED |  |
| QA2-002 | Informational | tooling/environment | DOCUMENTED |  |
| QA2-003 | Informational | tooling/environment | DOCUMENTED |  |
| QA2-004 | Informational | tooling/environment | DOCUMENTED |  |
| QA2-005 | Informational | tooling/environment | DOCUMENTED |  |
| SEC-001 | Medium | Media/PDF | FIXED_PENDING_RETEST | PDF active-content validation bypass via escaped names |
| SEC-002 | High | Media | FIXED_PENDING_RETEST | Approved private document mutable through outstanding upload ticket (= CR-003) |
| SEC-003 | Low | scripts/rehearsal/load.py | FIXED_PENDING_RETEST | Rehearsal helper disables TLS verification |
| SEC-004 | Low | deploy/web-preview/Caddyfile | MITIGATED_REPORT_ONLY | No CSP on web HTML |
| DEP-TOMCAT | High (scanner label Critical) | backend deps | FIXED_PENDING_RETEST | tomcat-embed-core 11.0.24 advisories |
| DEP-JACKSON | High (scanner label) | backend deps | FIXED_PENDING_RETEST | jackson 3.1.5 advisories |
| DEP-NODE | High/Moderate | mobile tooling | OPEN_DOCUMENTED | node-forge, braces, uuid, sprintf-js transitive dev-tool advisories |
