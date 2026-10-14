# MB-037 review (self-review, not independent)

Reviewer: Claude Code implementation agent (self-review).

## Added today
- `CashAndLedgerIntegrationTest.two_posting_instances_racing_post_each_completion_exactly_once_and_balanced`
- `OrderLifecycleIntegrationTest.acceptance_is_allowed_one_millisecond_before_the_deadline_and_refused_exactly_at_it` (test clock can now freeze)
- `DatabaseOutageRecoveryIntegrationTest` (paused PostgreSQL container)
- `RestartRecoveryIntegrationTest` (two application instances on the same PostgreSQL)
- `NotificationIntegrationTest.a_crash_between_provider_send_and_recording_resends_with_the_same_delivery_id_and_records_once`

## Product changes
- Bounded database waits: connect 5 s, pool wait 5 s, socket timeout 30 s, all environment-overridable. The first outage run hung indefinitely without them.
- `DataAccessResourceFailureException` maps to `503 DEPENDENCY_UNAVAILABLE` (retryable, no internal detail).
- Runbook: "Database unavailable".

## Mutations (each failed the relevant test, then restored)
- Acceptance allowed at the deadline instant.
- 503 mapping removed.
- Live leases ignored by the dispatcher.

## Existing coverage reused
- Last-portion race (20 customers).
- Accept/cancel/expire race.
- Injected failure rollback.
- Two workers with duplicate events.
- Lease reclaim.
- Retries after timeout and after idempotency retention.

## Residuals
- External email and push sends are at-least-once; the provider receives the stable delivery id for de-duplication.
- An exception while recording one delivery ends that batch; the other leased deliveries wait until their lease expires (delay, not loss).
- Load-level concurrency, multi-node deployment and real provider behaviour belong to MB-038 and staging (O07).
