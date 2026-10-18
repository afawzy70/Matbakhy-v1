# Fix verification (round 1 → round 2)

| Defect | Fix | Independent re-review | Executed checks | Result |
|---|---|---|---|---|
| CR-001 / QA-001 cash due after deposit | `CashService` uses `cash_obligation.amount_minor`; `Orders.tsx` defaults to `payment.remainder` | Codex reviewer round 2 @890bc87: corrected, zero-remainder and legacy null-payment OK | `mvnw test-compile` OK (JDK 21 in ~/tools); operations-web typecheck + 26 vitest PASS; new regression `cash_reporting_and_verification_use_the_obligation_after_a_deposit` **written, NOT_RUN locally (needs Docker) — authoritative run is GitHub Actions backend job** | FIXED, pending CI run |
| CR-003 approved document overwrite | approved COOK_DOCUMENT bytes sealed to `documents/<id>`, upload key deleted after commit; test assertion added in `MediaIntegrationTest` | round 2: no collision/rollback regression; found CR2-001 | compile OK; test NOT_RUN locally | FIXED, pending CI run |
| CR2-001 cleanup failure | post-commit delete exceptions caught + logged | not yet re-reviewed | compile OK | MITIGATED; durable cleanup job tracked |
| CR-002 staff MFA | none (reclassified) | round 2: not a code defect | — | OPEN: needs IdP evidence |
| QA-002 idempotency payload fingerprint after cache retention | none yet | — | — | OPEN (Medium) |

Honesty note: backend integration tests (Testcontainers) cannot run on this VPS; no backend test is claimed PASS from local execution.
