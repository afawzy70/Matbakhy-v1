# Audit and application-log readiness — owner direction

Recorded: 2026-10-12, 19:00 Asia/Kuwait.
Status: QUEUED / NOT VERIFIED. This records authorized work, not implementation completion.

## Placement and execution order

The owner requested that audit and application-log readiness be completed at the correct point in delivery. Track implementation under existing **MB-039**, and deployed verification under **MB-040**. Do not create a duplicate feature or interrupt the active MB-032 mobile recovery work or independent QA campaign.

At the next eligible MB-039 checkpoint, inspect the current implementation, reuse existing controls, close the gaps below, and record exact-SHA evidence. Prepare infrastructure locally without waiting for paid hosting. Before inviting external testers to an early web preview, demonstrate the preview checks below in that environment. This does not require completing native store delivery first, and does not waive the existing full MB-040 dependencies or release gates.

Hosting/spend/provider activation still requires O07 authorization. Retention durations and deletion/archive policies remain subject to O06; do not invent approved periods or silently weaken append-only protection. A blocked external integration must not stop unrelated development.

## Existing baseline to preserve

At inspected commit cdc592a2492a691de714a3d0bf23b31f25599480:
- PostgreSQL audit_event stores actor/action/object/outcome/reason/request ID; database triggers reject UPDATE, DELETE and TRUNCATE.
- Audit reads require audit.read and support filters/pagination; privileged database administration is outside trigger-based tamper protection.
- Audit redaction, request-ID MDC, selectable ECS JSON console logs, safe exception handling, operational gauges and local integration tests exist.
- OutboxDispatcher and DeliveryWorker already propagate stored request IDs into failure logging. Verify this end-to-end; do not reimplement it on the assumption it is absent.
- See 04-privacy-and-recovery.md for existing local evidence and remaining deployed checks. Existing code/tests are not proof of staging readiness.

## Work package for Claude

1. **Audit coverage:** create a matrix of completed sensitive customer/cook/staff operations, required event, actor, target, outcome and evidence. Include staff grants, kitchen/catalog moderation, exceptional order actions, configuration, finance and protected-data access. Keep business history, audit and diagnostic logs distinct. Identify genuine gaps without logging every read indiscriminately.
2. **Audit reliability:** verify authorized/unauthorized reads, mutation rejection using the actual runtime DB role, required reasons and safe details. Test rollback behavior: business-success events must match committed changes, while security-denial events must survive rejected transactions where required. Define and test audit-write failure behavior for critical operations without allowing unauthorized actions or phantom successes.
3. **Application logs:** enable structured JSON in deployed configuration; document stable safe fields (timestamp, severity, component, environment, version, request ID, applicable order/event IDs, error code and duration). Use contextual fields where meaningful; scheduled jobs need not invent HTTP request IDs. Verify request -> committed order -> outbox -> worker/provider-failure correlation and MDC cleanup between jobs/requests.
4. **Data protection:** exercise existing redaction tests plus relevant gaps with synthetic canaries; inspect normal/error/background logs for tokens, cookies, OTP/handover codes, addresses and other PII. Do not log full request/response bodies by default. Record limitations rather than claiming universal redaction.
5. **Log access and lifecycle:** choose a resource-bounded searchable log collection/access solution for the approved staging host. Protect operator access, set rotation/storage caps, and demonstrate logs remain available across app restart/redeploy for the configured window. Propose log retention and audit archival separately for O06. Test collector outage/disk-pressure behavior and document recovery. No Kafka/Redis/WebFlux or specific monitoring vendor is required by this task.
6. **Metrics and alerts:** expose metrics only through restricted management access, wire collection/dashboards, and exercise a failing outbox/job, API/DB failure and stale-backup signal. Distinguish instrumentation from an operational alert. Reuse documented proposed thresholds; record tuning as provisional. Rehearse delivery to a local test sink; sending to real recipients requires their applicable authorization.
7. **Runbook:** document how an operator locates an order failure by request/order ID, checks the audit trail, inspects dead letters, and diagnoses missing telemetry. Include config, access, retention, storage budget and restore dependencies.

## Preview checks and completion evidence

Before external tester access, record PASS/FAIL/BLOCKED for:
- AUD-01: required audit coverage and permissions; runtime-role mutation rejection; transaction/failure cases.
- LOG-01: deployed structured logs, synthetic-data redaction, cross-worker correlation and no MDC leakage.
- OPS-01: restricted searchable log access, bounded disk usage and restart/redeploy retention.
- OPS-02: restricted metrics collection and exercised alerts to an authorized destination or test sink, with real-routing gaps explicit.
- OPS-03: tested troubleshooting runbook, documented retention configuration and unresolved owner decisions.

Store results in the established verification/runs structure, with source SHA, environment, commands, exit status and sanitized evidence. Extend the existing TC-056/TC-139 evidence and add mapped scenarios where needed before claiming full coverage. Local checks can support implementation progress but cannot pass deployed preview checks. Do not mark MB-039/MB-040 or any release gate complete merely because this document or configuration exists.
