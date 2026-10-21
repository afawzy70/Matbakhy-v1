# Integration and events

> **Owner role:** Backend architecture
> **Status:** PARTIALLY IMPLEMENTED — the transactional outbox, in-app inbox and retry machinery work; external email, SMS and push providers are fake or disabled, and there is no payment provider integration
> **Language / اللغة:** English · [العربية](integration-and-events-ar.md)

This page covers how work leaves a transaction (events), how notifications reach people, and which external systems the backend really talks to. The system context is in [system overview](system-overview.md#external-integrations-what-really-exists); the table and column definitions are in [database architecture](database-architecture.md).

## Event model

There is **no in-process event bus** (no `ApplicationEventPublisher`, `@EventListener` or message broker in the code). Cross-module reactions use three mechanisms:

1. **Direct calls through `api` interfaces** inside one transaction (for example `OrderLifecycleService` calls `SlotInventory.move` and `PaymentService.orderClosed`).
2. **Transactional outbox** (`outbox_event`) for anything that must happen *after* the transaction and may fail independently: notifications.
3. **Read models over append-only history**: the ledger poster reads completion rows from `order_event` rather than listening to anything (`LedgerService.unpostedCompletions`, `LedgerPostingJob`).

## Transactional outbox

Producers insert an `outbox_event` row in the same transaction as the state change:

| Producer | Event types | Source |
|---|---|---|
| Order placement | `ORDER_PLACED` (payload: `orderId`, `kitchenId`) | `OrderService.placeOnce` |
| Every order transition | `ORDER_<new state>` such as `ORDER_ACCEPTED`, `ORDER_READY`, `ORDER_COMPLETED` (payload: `orderId`, `state`) | `OrderLifecycleService.transition` |
| Selected payment states | `PAYMENT_CUSTOMER_TRANSFER_REPORTED`, `PAYMENT_KITCHEN_RECEIPT_REPORTED`, `PAYMENT_DISPUTED`, `PAYMENT_REFUND_OWED`, `PAYMENT_REFUNDED` | `PaymentService.event` (`NOTIFIED` set) |

Columns: `aggregate_type`, `aggregate_id`, `event_type`, `payload jsonb`, `status` (`PENDING`, `SENT`, `FAILED`), `attempts`, `available_at`, lease columns (`lease_owner`, `leased_until`), `last_error`, `processed_at`, `version`, and `request_id` (the originating `X-Request-Id`, so a failed delivery can be traced to the request in logs; V22).

### Dispatch: `OutboxDispatcher`

`NotificationJobs` runs every 5 seconds (`matbakhy.notifications.poll-interval`) and calls `dispatchOnce` then `deliverOnce`. `dispatchOnce`:

1. Claims up to 100 due events with `UPDATE ... WHERE id IN (SELECT ... FOR UPDATE SKIP LOCKED) RETURNING`, setting a lease (`matbakhy.notifications.lease`, 60 s). Several workers can run; a crashed worker's events become claimable again when the lease expires.
2. Processes in creation order, **each event in its own transaction** (`TransactionTemplate`): fan-out then mark `SENT` only if this worker still holds the lease.
3. Fan-out (`fanOut`): looks up the order's parties through `OrderDirectory.parties` and decides recipients: `CUSTOMER_EVENTS` (order progress states and the payment states meant for the customer) go to the customer; `COOK_EVENTS` (`ORDER_PLACED`, `ORDER_CANCELLED`, `ORDER_EXPIRED`, `PAYMENT_CUSTOMER_TRANSFER_REPORTED`) go to the kitchen owner. For each recipient it inserts a `notification` row (the in-app inbox) with `ON CONFLICT (recipient_id, dedup_key) DO NOTHING`, so replays and duplicates add nothing, then one `EMAIL` delivery row and, only if push is enabled, one `PUSH` delivery row per registered device.
4. On an exception: increments `attempts`, reschedules with exponential backoff (`initial-backoff` 30 s, doubling, capped at 2^10 times), and after `max-attempts` (5) marks the event `FAILED`.

### Delivery: `DeliveryWorker`

Claims `notification_delivery` rows the same way (lease, `SKIP LOCKED`), calls the channel port **outside** any database transaction, then records the result only if it still holds the lease. Ports are `EmailSender` and `PushSender` in `backend/modules/notifications/src/main/java/com/matbakhy/notifications/api/NotificationChannels.java`; each send carries a stable `deliveryId` which a real provider must use as its idempotency key. A thrown exception is transient (retried); `Result.permanentFailure` is not. Rows reaching the retry limit become `DEAD` and are visible to operations. Provider error text is kept only if it matches a plain code pattern (so provider-echoed personal data is never stored). A recipient whose account is not `ACTIVE` gets a permanent failure.

Order of guarantees: at-least-once delivery to the channel, at-most-once notification rows per recipient and logical event, no effect on order state when delivery fails.

### Operating the outbox

* Staff with `integration.ops` can read integration health (`GET /api/v1/ops/integrations`: pending and failed counts, oldest pending age, dead deliveries) and replay a `FAILED` event (`POST /api/v1/ops/outbox/{eventId}/retry` with `expectedVersion` and a reason; audited as `outbox.retry`). Only `FAILED` events can be retried (`NotificationService.retryOutbox`).
* Gauges for alerting: `matbakhy.outbox.oldest.pending.age`, `matbakhy.outbox.failed`, `matbakhy.notifications.dead`, `matbakhy.notifications.oldest.pending.age` (`OperationalMetrics`), with alert rules in `deploy/web-preview/observability/alerts.yml`.
* Tests: `backend/app/src/test/java/com/matbakhy/app/notifications/NotificationIntegrationTest.java`, `PushFlagIntegrationTest.java`, `backend/app/src/test/java/com/matbakhy/app/reliability/RestartRecoveryIntegrationTest.java`.

## Notification channels today

| Channel | State | Behaviour |
|---|---|---|
| In-app inbox | IMPLEMENTED | `notification` rows; `GET /api/v1/notifications`, `PUT /api/v1/notifications/{notificationId}/read`. The mobile app has a Notifications screen; the customer web shows state through order pages (NOT VERIFIED that the web has an inbox screen). |
| Email | PLANNED (fake only) | `ProviderConfiguration.emailSender` returns a logging adapter in LOCAL/CI/PREVIEW and throws in STAGING/PRODUCTION: "No email notification provider is configured". The adapter logs only delivery id and template, never recipients. |
| Push | DISABLED | `PUSH_DELIVERY_ENABLED` defaults to `false`. While off, no `PUSH` delivery rows are created and `pushDisabled()` fails any stray delivery permanently with `PUSH_DISABLED`. Device registration endpoints exist (`PUT/DELETE /api/v1/me/devices/{installationId}`; tokens stored as hash plus AES-GCM ciphertext via `TokenVault`), but the mobile app does not register tokens. Enabling needs a real provider, device tests and owner approval (owner decision O07). |
| SMS | PLANNED (fake only) | Phone verification uses `PhoneVerificationSender`; only `FakePhoneVerificationSender` exists. |

## Other asynchronous jobs

Not outbox-based, but part of the integration picture (also listed in [backend architecture](backend-architecture.md#background-jobs)):

* `OrderExpiryJob` expires pending orders by server time.
* `LedgerPostingJob` posts commission ledger groups from completion events, idempotent through the unique `(kind, source_event_id)`.
* `MediaCleanupJob` deletes unreferenced objects recorded in `media_cleanup`.
* `AccountErasureJob` erases personal data after the deletion grace period.

## External systems the backend calls

| System | How | Notes |
|---|---|---|
| Keycloak | JWKS fetch and token endpoints through `OIDC_BACKCHANNEL_ISSUER` (internal URL, so the issuer in tokens stays the public one); Admin REST in `KeycloakAdmin` | Admin calls only for MFA reset and ending sessions |
| Object storage | AWS SDK v2 S3 client (`S3ObjectStorage`); clients upload and read through **presigned URLs** rooted at `OBJECT_STORAGE_CLIENT_ENDPOINT` | Private bucket for uploads and documents, public bucket for approved variants (`matbakhy-public`); malware scan gate `MalwareScanner` fails closed |
| Email/SMS/push | Ports only; fakes | See above |
| Payments | None. Deposit and cash flows are internal state machines fed by user reports | `PaymentService`; live money gated by owner decision D16 |

The API makes no outbound calls to payment, map, analytics or ad services (none found in `backend/` source or `application.yaml`).

## Inbound contract

All inbound integration is the HTTP API in `contracts/openapi.yaml`. Web clients use cookies and CSRF; mobile uses bearer tokens; idempotent commands expect `Idempotency-Key`. There are no webhooks from third parties.

## Known limitations and documentation gaps

* Event types are strings built at runtime (`"ORDER_" + to`), not an enum or schema; the set of recipients is a hard-coded pair of `Set<String>` in `OutboxDispatcher`. Adding a transition or payment state does not automatically notify anyone.
* `ORDER_PLACED` notifies the cook, but the customer-facing set starts at `ORDER_ACCEPTED`; there is no "order received" notification to the customer (by design or omission: NOT VERIFIED against product rules).
* Provider adapters for email, SMS, push and malware scanning are all outstanding for any non-preview environment (owner decision O07).
* `docs/engineering/07-integrations.md` is the older plan; this page reflects the code.
