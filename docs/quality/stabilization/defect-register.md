# Defect register (machine-readable: defect-register.json)

Found at commit `b7ec0e9052a612f7a8aab38f00519107b1dce213`. Fix commit filled after push.

| ID | Sev | Component | Status | Title |
|---|---|---|---|---|
| CR-001 | High | Deposit and cash accounting | FIXED_PENDING_RETEST | Cash reporting and verification demand the full total after a deposit |
| CR-002 | High | Staff authentication | OPEN_OWNER_DECISION | Staff grants become usable without MFA |
| CR-003 | High | Private document upload integrity | FIXED_PENDING_RETEST | Reusable upload URL can replace an approved private document |
| QA-001 | High | Deposit/cash integration | FIXED_PENDING_RETEST | Cash reporting and verification charge the full total after a deposit |
| QA-002 | Medium | Permanent order idempotency | OPEN | Permanent request recovery accepts a changed payload when its quote is unchanged |
| QA-003 | Medium | Deposit concurrency coverage | OPEN | Deposit expiry coverage does not exercise transfer/receipt races |
| QA-004 | Medium | Cross-kitchen authorization coverage | OPEN | Receipt authorization test uses a non-cook instead of a foreign kitchen owner |
| QA-005 | Medium | Revoked-session authorization coverage | OPEN | Revocation test reauthenticates instead of reusing the existing authentication |
| QA-006 | Medium | Pricing integration coverage | OPEN | Price-race tests replace the production price-update command with handwritten SQL |
| QA-007 | Low | Contract and browser coverage | OPEN | Route and mocked component checks do not validate real response payload contracts |
