# Audit and log readiness: local results (MB-039)

Work package: [05-observability-readiness.md](05-observability-readiness.md) (owner direction, commit `6052587`). This file records the implementation agent's **local** results. It is self-review, not independent review. Deployed checks belong to MB-040 and are **NOT_RUN**. No preview check (AUD-01, LOG-01, OPS-01..03) passes on local evidence.

Status: IN_PROGRESS. Evidence: `verification/runs/20261012-MB-039-LOCAL/`.

## 1. Audit coverage matrix

Audit writes go through `AuditLog.record` (`JdbcAuditLog`). It joins the caller's transaction and redacts the reason and details before the insert. Business history (order events, ledger) and diagnostic logs are kept separate from audit; this matrix covers audit only.

| Area | Operation | Event (action) | Actor / target | Outcome and reason | Test evidence |
|---|---|---|---|---|---|
| Staff grants | grant, revoke; self-escalation; last administrator | `staff.grant`, `staff.revoke`, `staff.bootstrap` | staff / user | ALLOWED with a required reason (3–500); DENIED kept on refusal | StaffAccessIntegrationTest, IdentityIntegrationTest |
| Any privileged endpoint | access denied to a signed-in account | `denied <METHOD> <path>` (bounded to 120 characters; path bounded to 512) | account / endpoint | DENIED, error code as reason | StaffAccessIntegrationTest; long path: AuditAndCorrelationReadinessIntegrationTest |
| Cook applications | submit, view (protected data), decision; self-review refused | `cook.application.*` | applicant or reviewer / application | reason required for decisions | CookWorkflowIntegrationTest |
| Kitchens | suspend, pause, resume; own kitchen refused; settings | `kitchen.operating_state`, `kitchen.settings` | staff or cook / kitchen | reason required for staff state changes | CookWorkflowIntegrationTest, CookWorkspaceIntegrationTest |
| Catalogue | meal moderation; review moderation | `meal.moderation`, `review.moderation` | staff / meal, review | reason required | ModerationIntegrationTest, ReviewsAndFavoritesIntegrationTest |
| Media | rejected upload (malware, active content, type mismatch); staff access to another account's media | `media.rejected`, `access.media` | owner or staff / media | DENIED or ALLOWED | MediaIntegrationTest |
| Orders (exceptional) | exception cancel; handover override (recent MFA) | `order.exception_cancel`, `order.handover_override` | staff / order | reason code and case; reason ≥ 10 plus evidence | OrderLifecycleIntegrationTest |
| Configuration | policy, zones, feature flags | `config.change` | staff / config key | reason required; approval reference for a real policy | ConfigurationIntegrationTest |
| Finance | refunds; settlements; cash verification; own report refused | `refund.*`, `settlement.*`, `cash.verify` | staff / refund, batch, report | reason or evidence depending on action | FinanceOperationsIntegrationTest, CashAndLedgerIntegrationTest |
| Audit | audit trail read | `audit.read` | staff / looked-up object or actor | ALLOWED, filter in details | StaffAccessIntegrationTest |
| Notifications | dead-letter retry | `outbox.retry` | staff / outbox event | reason required | NotificationIntegrationTest |
| Orders (protected data) | staff view disclosing customer contact | `access.order_contact` | staff / order | ALLOWED, fixed reason, request ID | SupportIntegrationTest |
| Support (protected data) | staff view of a case | `access.support_case` | staff / case | ALLOWED, fixed reason, request ID | SupportIntegrationTest |
| Support | staff message, case update | `case.message`, `case.update` | staff / case | reason for some transitions | SupportIntegrationTest |
| Privacy | account erasure (job); legal hold | `account.erasure` | system / account | counts only, no personal data | AccountErasureIntegrationTest |

### Gaps (proposals; not implemented today)

Not every read should be audited. These are the ones that expose protected data or change sensitive state:

1. **Staff view of an order with customer contact** (`GET /api/v1/ops/orders/{id}` returns name, phone and address): **closed in this session.** Each view that discloses contact now writes `access.order_contact` (actor, order, request ID; no contact data). Test first: `RED-staff-order-contact-audit.log`; assertion in SupportIntegrationTest.
2. **Audit log reads** (`GET /api/v1/ops/audit`): **closed in this session.** Each read writes `audit.read`: the reader, the object or actor looked up (in `object_id`), and the other filters in details. It is written after the query, so a page never lists its own read. Test first: `RED-audit-read-audited.log`; assertion in StaffAccessIntegrationTest.
3. **Staff case view** (`GET /api/v1/ops/cases/{id}`): **closed in this session.** Each view writes `access.support_case` (actor, case, request ID; no case content). Test first: `RED-staff-case-view-audit.log`; assertion in SupportIntegrationTest.
4. **Account deletion request** (customer self-service): only the later erasure job is audited, with no actor. Proposal: record the request with the customer as actor.
5. **Cash report submission by a cook**: business record only. Probably sufficient (it is business history); listed for the owner's view.
6. **Request IDs missing** on `case.message`: **closed in this session** (red first: `RED-case-message-request-id.log`). System jobs (`staff.bootstrap`, `media.rejected`, `account.erasure`) have none by design.
7. **Not-found as denial:** object-level authorization that answers 404 (to hide existence) writes no audit row. This is intended; it is listed so that it is not mistaken for coverage.
8. **Settlement audit rows** have no direct test assertion.

## 2. Audit reliability

| Check | Result (local, PostgreSQL in Testcontainers) |
|---|---|
| Authorized and unauthorized audit reads | PASS: `audit.read` required; a customer gets 403; revoked or expired grants stop working (existing tests) |
| UPDATE and DELETE rejected | PASS (existing test) |
| TRUNCATE rejected; append-only triggers enabled | PASS (new test) |
| Business-success row rolls back with its transaction | PASS (new test: a row written in a rolled-back transaction does not survive) |
| Denial rows survive the rejected request | PASS indirectly: services that write DENIED then fail use `noRollbackFor` and the DENIED counts are asserted (existing tests) |
| Denial on a long path | **Defect found and fixed:** the 120-character action limit made the insert fail, so a long path produced an error instead of a recorded 403. Red log: `RED-audit-long-path-denial.log` |
| Mutation rejection **as the runtime database role** | **PASS locally (2026-10-13).** Flyway migrates as the owner; the callback `afterMigrate.sql` grants the runtime role only what it needs. RuntimeRoleIntegrationTest connects as that role: ordinary writes succeed; 12 forbidden statements fail with 42501 (UPDATE/DELETE/TRUNCATE audit, DISABLE/DROP TRIGGER, `session_replication_role`, ledger UPDATE, order item DELETE, meal revision UPDATE, order DELETE, Flyway history UPDATE, CREATE TABLE). The preview package now uses this split (`matbakhy_app`); a rehearsal with the real init script and API jar started healthy as the runtime role and refused the same kinds of statement (`PREVIEW-RUNTIME-ROLE-REHEARSAL.txt`). **Deployed: NOT_RUN** (MB-040). |
| Audit-write failure on a critical operation | Behaviour today: the audit insert is in the business transaction, so a failed audit write rolls the operation back (no phantom success, no unaudited change). A failed **denial** audit write (outside a transaction) surfaces as an error response rather than a 403; the request is still refused. Not separately fault-injected. |

## 3. Application logs

- **Format:** ECS JSON on the console when `LOGGING_STRUCTURED_FORMAT_CONSOLE=ecs` (set in `deploy/web-preview/compose.yaml`); plain text locally.
- **Stable fields:** Spring Boot's ECS format writes `@timestamp`, `log.level`, `log.logger` (component), `process.thread.name`, `message`, the MDC `requestId` when there is one, and `error.*` with a redacted stack trace on failures. Tests assert `message` and `requestId` only; the other field names are the framework's and were not separately checked. `service.name` (`matbakhy-api`), `service.environment` (from `APP_ENV`) and `service.version` (from `MATBAKHY_VERSION`; the preview sets it to the source SHA) are now emitted and asserted (AuditAndCorrelationReadinessIntegrationTest). Order and event IDs appear in the message text, not as separate fields.
- **Correlation:**
  - request → committed order → notification delivery failure under one request ID: PASS (LogRedactionIntegrationTest);
  - request → outbox dispatch failure under the request ID stored with the event: PASS (new test, failure forced by a test-only trigger);
  - the worker's own MDC is restored after a dispatch failure (kept when it had one, empty when it had none): PASS (new test);
  - the request's MDC entry is removed when the request ends: PASS (new test).
- **Scheduled jobs** without a request get no invented request ID.

## 4. Data protection in logs

**Audit details redaction (found while testing, fixed in this session):** the phone pattern in `Redaction` also matched UUIDs, so identifiers in audit `details` were masked. A value that is exactly a UUID is now kept. A UUID or phone number inside free text is still masked, and sensitive keys still mask any value. Red first: `RED-redaction-uuid.log`.

Existing redaction evidence (TC-056, LogRedactionIntegrationTest) with synthetic canaries: name, e-mail, phone, street, recipient, push token, bearer token, one-time and handover codes are absent from captured logs across OTP, address, order, handover, provider failure and an unexpected error. Request and response bodies are not logged. Limitation: this covers the flows exercised, not every possible log statement.

## 5–7. Log access, metrics and alerts, runbook

Not done locally in this session:
- **Log access and lifecycle:** proposal is Docker json-file rotation (already capped at 10 MB × 3 per container in the preview package) plus a small searchable store on the same host (for example Loki with a disk cap), operator access only through the private network. Needs a test of retention across restart/redeploy and of disk pressure. Retention periods are O06; none is invented here.
- **Metrics and alerts:** operational gauges exist (OperationalMetricsIntegrationTest); actuator is not exposed through the preview proxy. Collection, dashboards and alert delivery to a test sink are NOT_RUN.
- **Runbook:** draft at [08-troubleshooting-runbook.md](08-troubleshooting-runbook.md) (order failure by request/order ID, audit trail, dead letters, missing telemetry, configuration/retention/restore dependencies); not yet exercised on a deployment.

## Preview checks

| Check | Status |
|---|---|
| AUD-01 | NOT_RUN (deployed). Local: coverage matrix and reliability above, including the runtime-role check |
| LOG-01 | NOT_RUN (deployed). Local: correlation and MDC checks PASS |
| OPS-01 | NOT_RUN |
| OPS-02 | NOT_RUN |
| OPS-03 | NOT_RUN |
