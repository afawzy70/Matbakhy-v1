# Local runbook rehearsal (MB-039, OPS-03 local part), 2026-10-13

Self-review on the local observability stack (not the preview host). Deliberately failed synthetic event: a rehearsal-only trigger made dispatch fail for one outbox event (starting at attempt 4), so it moved to FAILED; the trigger was then dropped. An earlier event for a missing order was handled as nothing-to-send (SENT), not a failure.

```
## Runbook rehearsal (section 3 and 1), event 5c8845ae-c6e0-43c6-b3c5-2654f3b90de2, request ID rehearsal-runbook-0002
1. Alert at the test sink:
ALERT_RECEIVED {"received": "Tue, 13 Oct 2026 09:16:34 GMT", "status": "firing", "alertname": "OutboxFailed", "severity": "ops", "summary": "Outbox events exhau
2. Gauge on the management port (runbook 4):
matbakhy_outbox_failed_events 1.0
3. Event record (read-only query; the ops UI/API shows the same through integration.ops):
FAILED|5|UncategorizedSQLException|rehearsal-runbook-0002
4. Log search by request ID (Loki, as Grafana Explore does):
   2026-10-13T09:16:04.197682617Z WARN rehearsal-runbook-0002 outbox event 5c8845ae-c6e0-43c6-b3c5-2654f3b90de2 dispatch failed on attempt 5: UncategorizedSQLException (moved to FAILED)
5. Cause removed (rehearsal trigger dropped). Retry through POST /api/v1/ops/outbox/{id}/retry (staff, integration.ops, reason): NOT_RUN here, no staff session in this rehearsal; the retry itself is covered by NotificationIntegrationTest.
## Missing telemetry (runbook 4): collector stopped at 09:16:46
LogPipelineDown received 09:19:07; 'docker compose up -d alloy' per runbook
LogPipelineDown resolved 09:20:08
```

Result: an operator following docs/operations/08-troubleshooting-runbook.md sections 1, 3 and 4 found the alert, the gauge, the event's error code and request ID, and the failure log line by request ID, and restored the log pipeline. NOT_RUN here: the staff retry API (no staff session in this rehearsal) and every step on the deployed preview (OPS-03 deployed).
