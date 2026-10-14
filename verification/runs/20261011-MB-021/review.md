# MB-021 review (self-review, not independent)

Reviewer: Claude Code implementation agent (self-review).

## Scope
V14 migration, notifications module (OutboxDispatcher, DeliveryWorker, NotificationService, TokenVault, NotificationJobs, NotificationController), orders.api.OrderDirectory, LOCAL/CI logging channel stand-ins, contract API-031..034, 096, 097.

## Findings
1. Defect found by the full-suite run and fixed: the lease claim `UPDATE ... RETURNING` does not preserve the subquery order, so events were fanned out in arbitrary order and inbox timestamps followed processing order. Inbox rows now carry the event's occurrence time and claimed events are processed in creation order. The inbox test now dispatches the newest event first and asserts event order; restoring processing-time stamps fails it.
2. Hardening added: deployed environments refuse the development push-token keys (unit test), in addition to refusing the logging channel stand-ins (no real provider until O07).
3. Mutations, each detected by NotificationIntegrationTest: dedup key per event id instead of per logical event; unbounded delivery retry; unbounded outbox retry; ignoring a live lease; recording a delivery without lease ownership; inbox stamped with processing time.

## Residual risks and proposals
- Providers are logging stand-ins only (LOCAL/CI). Real email/push providers, templates and sender identities are O07; provider-side idempotency keys are the delivery id.
- A provider call that succeeds after the lease expired can be repeated by another worker (at-least-once to the provider); providers must deduplicate by delivery id. Recorded results are once-only.
- Proposed defaults: 5 attempts, 30 s initial backoff doubling, 60 s lease; inbox recipients are the customer for state changes and the kitchen owner for placement, cancellation and expiry. Retention of inbox rows and device registrations follows O06.
- Push payloads contain only order number and state.
