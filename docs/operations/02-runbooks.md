# Operational runbooks

Every action records actor, time, affected IDs, observed issue, command/action, outcome and follow-up. Do not put PII into a public incident report. Escalation contacts/hours come from O09.

## Order stuck pending / cook unavailable

Check server state/deadline and cook status. If deadline passed, inspect expiry job/lease and execute the same idempotent expiry command; verify stock release once. If cook accepted, route operational cancellation/fulfillment through authorized flow. Notify through approved transactional channel. Do not manually edit database status or tell customer an order is accepted without event evidence.

## Unknown checkout outcome

Use customer/request ID lookup and inspect idempotency/order transaction. Return existing order or retry original identifiers. Never create a new order as the first recovery step. Reconcile stock/outbox if any invariant alarm exists; suspend new ordering only for affected scope when necessary.

## Delayed/failed delivery or pickup no-show

Confirm current state and scheduled window. Contact cook/customer using authorized operator process. Record actual dispatch/attempt and reason; use DELIVERY_FAILED for delivery. Pickup no-show stays READY until ops cancellation/exception decision after configured window; it is not silently COMPLETED. Determine refund/fees under approved policy, record case and communicate decision. No automatic penalty policy is invented by the agent.

## Food/allergen complaint

Create high-severity case immediately, preserve order/ingredient snapshot and relevant batch/meal/cook references, alert designated operator. Pause affected supply when authorized and assess other active orders. Do not give medical advice, claim a safety certification or remove complaint evidence. Follow owner's reviewed emergency/regulatory process and record resolution. Platform approval is not a food-safety guarantee.

## Cash discrepancy / refund

Compare order total snapshot, cook collection report, customer complaint and evidence. Mark disputed rather than overwrite verified data. Finance approves adjustment/refund obligation, records actual payment separately, verifies evidence and posts balanced compensating entries. Confirm payer/payee and direction. Stop duplicate action through idempotency/reference constraints. Reconcile statement after resolution.

## Provider or outbox outage

Confirm affected adapter/health without exposing credentials. Orders remain committed; inbox/status remain readable. Inspect oldest event/retry/lease; restore provider/config; replay specific failed events through idempotent command; verify receipt/deduplication. Do not mass-send arbitrary payloads or retry permanent validation failures indefinitely.

## Stock or ledger invariant alarm

Disable new transactions for affected kitchen/feature if needed; preserve evidence; identify last good source/schema/config and related request/event IDs. Reconcile using immutable order/allocation/finance history. Repair with audited compensating action or tested code fix; no undocumented SQL overwrite. Reproduce and add regression before re-enabling. Severity critical until integrity demonstrated.

## Deployment regression

Check exact artifact/config/migration and health/business smoke. Stop rollout, assess schema compatibility, roll back application or apply tested forward fix, then verify order placement, fulfillment, outbox and finance. Avoid replaying real payments/notifications. Record affected orders and communicate through approved incident process.

## Backup restoration drill

Restore to isolated environment with outbound providers disabled, never over live data for a drill. Verify schema, row counts/hashes where useful, pending orders, allocations, finance balances and outbox. Measure recovery time and data age. Rotate/restage credentials and enable providers only after duplicate-side-effect review. A file existing in a bucket is not a successful backup test. Tooling: `scripts/db-backup.sh`, `scripts/db-restore.sh` and `infra/db/reconcile.sql`; see `04-privacy-and-recovery.md`.

## Account compromise / lost staff access

Revoke affected identity sessions and grants, rotate compromised secrets, preserve audit, review suspicious actions and contain affected features. Use documented recoverable-admin procedure; never create a public default administrator. Restore least privilege and verify. Escalate privacy/legal notification assessment to owner.

## Database unavailable (MB-037)

- **What happens:** while PostgreSQL is unreachable, API requests fail within bounded waits and return a retryable `503 DEPENDENCY_UNAVAILABLE`, with no database detail. Clients retry; order placement is safe to retry with the same keys. When the database returns, the running application recovers without a restart. This is verified by `DatabaseOutageRecoveryIntegrationTest` on real PostgreSQL.
- **Timeouts:**
  - Connect timeout is 5 s.
  - Pool wait is `DATABASE_CONNECTION_TIMEOUT_MS`, default 5000.
  - Socket timeout is `DATABASE_SOCKET_TIMEOUT_SECONDS`, default 30.
  - Raise the socket timeout only for a known long one-off operation, such as a large migration run separately.
- **Operator response:**
  1. Confirm with the database provider.
  2. Watch the 503 rate and outbox age.
  3. After recovery, check that pending outbox events drain and that the ledger and stock reconciliation queries show no imbalance.
