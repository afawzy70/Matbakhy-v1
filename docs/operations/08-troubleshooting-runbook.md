# Troubleshooting runbook: orders, audit, dead letters, telemetry (draft)

Part of the OPS-03 preparation for MB-039 ([05-observability-readiness.md](05-observability-readiness.md)). It is **not exercised on a deployed environment**: OPS-03 is NOT_RUN until an operator follows it on the preview (MB-040). The commands match the preview package (`deploy/web-preview`). Use synthetic data only.

## 1. Locate an order failure

The customer or staff member usually has one of these:
- **a request ID:** every API error body has `requestId`, and the `X-Request-Id` response header carries the same value;
- **an order number** (`MB-…`);
- **a client request ID**, for an unconfirmed order.

| Starting point | Step |
|---|---|
| Request ID | Search the log store: open Grafana through an SSH tunnel (`ssh -L 3000:127.0.0.1:3000 <host>`, then http://localhost:3000, user `ops-admin`), Explore → Loki, query `{container=~".*api.*"} \|= "<id>"`. Logs survive API redeploys there. Fallback for the last 30 MB only: `docker compose logs api \| grep '"requestId":"<id>"'`. Each JSON line has `service.environment`, `service.version` (release commit), `log.logger` and `message`. Failure logs from the outbox dispatcher and the notification worker carry the request ID stored with the event, so the order request and its later delivery failures share it. |
| Order number | Operations web → Orders (`GET /api/v1/ops/orders`, `order.ops`). The order detail shows state, exceptions and case IDs. Viewing customer contact writes an `access.order_contact` audit row. |
| Client request ID (customer's unconfirmed order) | The customer's app looks it up itself (`GET /api/v1/orders/by-request/{id}`; another customer gets 404). Staff can confirm it with a read-only query of `customer_order.client_request_id`. |

Business history is the order's timeline (`order_event`, append-only). Diagnostic detail is in the logs. Who did what is in the audit trail. Keep the three apart in reports.

## 2. Check the audit trail

- Operations web → audit (`GET /api/v1/ops/audit?objectId=<id>` or `?actorId=`; requires `audit.read`).
- Each read of the trail is itself audited (`audit.read`).
- Rows hold the actor, action, outcome, reason and request ID. Details are redacted.
- Rows cannot be updated, deleted or truncated (database triggers).
- The API connects as `matbakhy_app`, which cannot change or disable the triggers. The owner role `matbakhy` (migrations, backups, operator shell) still can: use it only for those tasks ([07](07-audit-and-log-readiness.md)).

## 3. Inspect and retry dead letters

1. Look at integration health: operations web → integrations (`GET /api/v1/ops/integrations`, `integration.ops`).
   - It shows failed outbox events and dead notification deliveries.
   - The gauges are `matbakhy.outbox.failed` and `matbakhy.notifications.dead`.
2. Read the event's `last_error` code (never provider text) and its request ID, then search the logs for that request ID (section 1).
3. Fix the cause, then retry: `POST /api/v1/ops/outbox/{eventId}/retry` with the current version and a reason.
   - The retry is idempotent and audited (`outbox.retry`).
   - Find it afterwards by request ID: the response header `X-Request-Id`, the audit row's `request_id` and the log line `outbox event <id> reset for retry by staff <id>` carry the same value. The reason is in the audit row only, not in the log.
   - Without `integration.ops` the call returns 403 and the denial is audited (`DENIED`, same request ID). A second retry of an event that is no longer FAILED returns 409; a replay with the same `Idempotency-Key` returns the first result and writes nothing.
   - In the preview, providers are fakes: nothing is sent.

## 4. Missing or suspicious telemetry

| Symptom | Check |
|---|---|
| No logs for a request ID | The request reached the API (proxy logs in Grafana: `{container=~".*proxy.*"}`); its `X-Request-Id` was well-formed (malformed IDs are replaced, so use the ID from the response) |
| Recent lines missing in Grafana | Alert LogPipelineDown; `docker compose ps alloy loki`. Restart the collector: `docker compose up -d alloy`. It resumes from its saved position; lines older than Docker's cap (10 MB × 3 per container) are lost |
| Logs not JSON | `LOGGING_STRUCTURED_FORMAT_CONSOLE=ecs` on the api service |
| `service.version` is `unknown` | `RELEASE_COMMIT` not set in `.env` |
| ApiDown | `docker compose ps api`; health on the management port (8081): `docker compose exec api wget -qO- http://127.0.0.1:8081/actuator/health` |
| DatabaseUnavailable | `docker compose ps postgres`; `docker compose logs --tail 50 postgres`. Data alerts (OutboxFailed and similar) may show "resolved" while the data is missing; recheck them after recovery |
| BackupStale | Run `./deploy.sh backup`; it records the time of the last good backup for the alert |
| DiskPressure | `df -h`; `docker system df`; prune unused images. Do not delete volumes (database, logs, metrics) without the owner |
| AuditWriteFailed | The audit store is failing: denials are still refused, but critical changes fail. Check the database; escalate |
| Ledger alarm | `matbakhy.ledger.unbalanced.groups > 0`: stop and escalate; do not edit ledger rows |

Alerts go only to the local test sink (`docker compose logs alert-sink`) until the owner approves a real recipient (O07).

## 5. Configuration, access, retention, storage and restore

- **Configuration:** `deploy/web-preview/.env` (mode 600, never committed): `RELEASE_COMMIT`, image digests, domain, access list.
- **Operator access:** SSH key to the host. The private network (recommended in [06](06-web-preview.md)) is not set up.
- **Retention:** log retention and audit archival periods are owner decisions (O06). Proposed staging defaults in `.env` (2026-10-14, not approved policies): `LOG_RETENTION=720h` (Loki, 30 days), `DEBUG_LOG_RETENTION=168h` (debug level, 7 days), `METRICS_RETENTION=15d` (Prometheus, also capped at 2 GB), `QA_ARTIFACT_RETENTION_DAYS=90`; Docker keeps 30 MB per container. See [09](09-staging-hetzner-preparation.md).
- **Storage budget:** see "Sizing" in [06](06-web-preview.md). Backups live in `deploy/web-preview/backups` until copied off the host.
- **Restore dependency:** `./deploy.sh restore-db <dump>` restores into an empty database and runs the reconciliation query. Audit rows restore with the database. Logs and metrics are not part of the backup.

## Not covered yet (owner or MB-040)

Rehearsal of this runbook on the preview against a deliberately failed synthetic event (OPS-03); a full-disk drill; real alert routing (O07); retention periods (O06); dashboards beyond Grafana's Explore view.
