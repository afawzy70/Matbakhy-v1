# Troubleshooting runbook: orders, audit, dead letters, telemetry (draft)

Part of the OPS-03 preparation for MB-039 ([05-observability-readiness.md](05-observability-readiness.md)). It is **not exercised on a deployed environment**: OPS-03 is NOT_RUN until an operator follows it on the preview (MB-040). The commands match the preview package (`deploy/web-preview`). Use synthetic data only.

## 1. Locate an order failure

The customer or staff member usually has one of these:
- **a request ID:** every API error body has `requestId`, and the `X-Request-Id` response header carries the same value;
- **an order number** (`MB-…`);
- **a client request ID**, for an unconfirmed order.

| Starting point | Step |
|---|---|
| Request ID | Search the API logs for it: `docker compose logs api \| grep '"requestId":"<id>"'`. Each JSON line has `service.environment`, `service.version` (release commit), `log.logger` and `message`. Failure logs from the outbox dispatcher and the notification worker carry the request ID stored with the event, so the order request and its later delivery failures share it. |
| Order number | Operations web → Orders (`GET /api/v1/ops/orders`, `order.ops`). The order detail shows state, exceptions and case IDs. Viewing customer contact writes an `access.order_contact` audit row. |
| Client request ID (customer's unconfirmed order) | The customer's app looks it up itself (`GET /api/v1/orders/by-request/{id}`; another customer gets 404). Staff can confirm it with a read-only query of `customer_order.client_request_id`. |

Business history is the order's timeline (`order_event`, append-only). Diagnostic detail is in the logs. Who did what is in the audit trail. Keep the three apart in reports.

## 2. Check the audit trail

- Operations web → audit (`GET /api/v1/ops/audit?objectId=<id>` or `?actorId=`; requires `audit.read`).
- Each read of the trail is itself audited (`audit.read`).
- Rows hold the actor, action, outcome, reason and request ID. Details are redacted.
- Rows cannot be updated, deleted or truncated (database triggers).
- **Caveat:** the database owner role can disable the triggers. A separate runtime role is a proposal for MB-040 ([07](07-audit-and-log-readiness.md)).

## 3. Inspect and retry dead letters

1. Look at integration health: operations web → integrations (`GET /api/v1/ops/integrations`, `integration.ops`).
   - It shows failed outbox events and dead notification deliveries.
   - The gauges are `matbakhy.outbox.failed` and `matbakhy.notifications.dead`.
2. Read the event's `last_error` code (never provider text) and its request ID, then search the logs for that request ID (section 1).
3. Fix the cause, then retry: `POST /api/v1/ops/outbox/{eventId}/retry` with the current version and a reason.
   - The retry is idempotent and audited (`outbox.retry`).
   - In the preview, providers are fakes: nothing is sent.

## 4. Missing or suspicious telemetry

| Symptom | Check |
|---|---|
| No logs for a request ID | The request reached the API (`docker compose logs proxy`); its `X-Request-Id` was well-formed (malformed IDs are replaced, so use the ID from the response) |
| Logs not JSON | `LOGGING_STRUCTURED_FORMAT_CONSOLE=ecs` on the api service |
| `service.version` is `unknown` | `RELEASE_COMMIT` not set in `.env` |
| Logs missing after a restart | Docker json-file keeps 3 × 10 MB per container and is removed with the container. A searchable store with retention is not set up yet (OPS-01 open) |
| Gauges look stale | Actuator is not exposed through the proxy. Read it inside the host network (`docker compose exec api wget -qO- localhost:8080/actuator/metrics/<name>`, when the metrics endpoint is enabled). Collection and alerts are OPS-02, not yet set up |
| Ledger alarm | `matbakhy.ledger.unbalanced.groups > 0`: stop and escalate; do not edit ledger rows |

## 5. Configuration, access, retention, storage and restore

- **Configuration:** `deploy/web-preview/.env` (mode 600, never committed): `RELEASE_COMMIT`, image digests, domain, access list.
- **Operator access:** SSH key to the host. The private network (recommended in [06](06-web-preview.md)) is not set up.
- **Retention:** log retention and audit archival periods are owner decisions (O06). None is configured beyond Docker's 30 MB per container cap.
- **Storage budget:** see "Sizing" in [06](06-web-preview.md). Backups live in `deploy/web-preview/backups` until copied off the host.
- **Restore dependency:** `./deploy.sh restore-db <dump>` restores into an empty database and runs the reconciliation query. Audit rows restore with the database. Logs are not part of the backup.

## Not covered yet (owner or MB-040)

Searchable log store and its access control; metric collection, dashboards and alert routing to a test sink; disk-pressure and collector-outage drills; deployed rehearsal of this runbook.
