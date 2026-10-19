# Database architecture

> **Owner role:** Backend architecture
> **Status:** IMPLEMENTED — 31 forward-only Flyway migrations define the whole schema; no ORM, SQL is hand-written in services

The database is **PostgreSQL 16.15** (pinned image digest in `backend/pom.xml` and the compose files). The schema is owned by Flyway SQL migrations in `backend/app/src/main/resources/db/migration` (`V1` to `V31`, plus the callback `afterMigrate.sql`). Access is plain JDBC; see [backend architecture](backend-architecture.md#layers-and-data-access). Entity relationship diagram: [database relationships](diagrams.md#12-database-relationships). The older design document is `docs/engineering/02-data-model.md` (context only; the SQL below is the truth).

## Conventions (visible in every migration)

* Identifiers are `uuid` assigned by the application (`UUID.randomUUID()`); the only sequence is `order_public_number_seq` (public order numbers `MB-<n>`, start 100001).
* Instants are `timestamptz` (UTC). Local dates are stored beside them where the business needs them (`cook_slot.local_date`, timezone fixed to `Africa/Cairo` by a check constraint).
* Money is `bigint` piastres in `*_minor` columns; currency columns are constrained to `EGP`. Totals are checked by constraints: `total_minor = subtotal_minor + delivery_fee_minor + tax_minor` on `quote` and `customer_order`; `line_total_minor = unit_price_minor * quantity` on `order_item`; `deposit_minor + remainder_minor = total_minor` on `order_payment`.
* State machines are `text` columns with `CHECK (state IN (...))`, not database enums. Adding a state means a migration that drops and re-adds the check (V25 and V28 did this).
* Rows that must not be edited have `BEFORE UPDATE OR DELETE` triggers that raise an exception; the same tables also lose `UPDATE`/`DELETE` privilege for the runtime role (see below).
* Optimistic concurrency: a `version bigint` column on mutable aggregates, compared with `expectedVersion` from the client.
* Personal data lives in a few known columns that the erasure job can clear (V21, V27).

## Migrations

| Migration | Adds | Story / decision |
|---|---|---|
| `V1__baseline_identity_and_idempotency.sql` | `app_user` (unique issuer+subject, phone, status, locale), `idempotency_record` | MB-006 |
| `V2__web_sessions.sql` | Spring Session JDBC tables `SPRING_SESSION`, `SPRING_SESSION_ATTRIBUTES` | MB-009 |
| `V3__zones_addresses_phone_verification.sql` | `service_zone` (delivery fee per zone), `address`, `phone_challenge`, `deletion_request` | MB-010 |
| `V4__grants_and_audit.sql` | `user_grant` (staff permissions), append-only `audit_event` | MB-011 |
| `V5__cook_applications_and_kitchens.sql` | `cook_application`, `cook_application_revision`, `kitchen`, `kitchen_zone` | MB-012 |
| `V6__media_assets.sql` | `media_asset` (upload state authority) | MB-013 |
| `V7__meals.sql` | `meal`, append-only `meal_revision`, `meal_media` | MB-014 |
| `V8__slots.sql` | `cook_slot`, `meal_slot` with counters | MB-015 |
| `V9__discovery.sql` | meal category and search normalisation | MB-016 |
| `V10__quotes.sql` | `commerce_policy`, `quote`, `quote_item` | MB-017 |
| `V11__orders.sql` | `customer_order`, `order_item`, `stock_allocation`, append-only `order_event`, `outbox_event`, `cash_obligation`, `order_public_number_seq` | MB-018 |
| `V12__order_lifecycle.sql` | terminal-state guard trigger, `handover_challenge`, `handover_attempt` | MB-019 |
| `V13__cash_and_ledger.sql` | `cash_report`, `ledger_group`, `ledger_entry` with balance trigger | MB-020 |
| `V14__notifications.sql` | outbox leases, `notification`, `notification_delivery`, `device_registration` | MB-021 |
| `V15__cook_workspace.sql` | `kitchen.paused_by` | MB-022 |
| `V16__moderation.sql` | `meal.moderation_state` | MB-023 |
| `V17__support.sql` | `support_case`, `case_message` | MB-024 |
| `V18__finance_operations.sql` | `refund`, `settlement_batch`, `settlement_line`, ledger kind `COMMISSION_REVERSAL` | MB-025 |
| `V19__configuration.sql` | `feature_flag` (`ordering-enabled` kill switch), append-only `config_change` | MB-026 |
| `V20__favorites_and_reviews.sql` | `favorite`, `review`, `review_report` | MB-028 |
| `V21__personal_data_erasure.sql` | erasure markers on addresses and related | MB-039 |
| `V22__outbox_request_id.sql` | `outbox_event.request_id` | MB-039 |
| `V23__launch_commission.sql` | check: live policies must have zero commission | owner decision D10 |
| `V24__deposit_payments.sql` | `kitchen_payment_profile`, `order_payment`, append-only `payment_event`, payment terms on `quote` | MB-051, D16 |
| `V25__payment_reconciliation.sql` | reconciliation states, `payment_event.note` | MB-052 |
| `V26__organic_activity.sql` | `activity_daily` (hashed visitor, per day) | MB-054, D17 |
| `V27__erasure_on_append_only_history.sql` | triggers allowing the one erasure edit on `case_message` / `payment_event` | MB-039 |
| `V28__payment_support_case.sql` | `PAYMENT` support-case category, one open payment case per order | MB-052 |
| `V29__order_request_fingerprint.sql` | `customer_order.request_fingerprint` | QA-002 |
| `V30__media_cleanup.sql` | `media_cleanup` (durable object deletion queue) | CR2-001 |
| `V31__mfa_policy.sql` | `mfa_policy`, `user_mfa_preference`, `user_mfa_reset`, two new permission codes | D18 |

Rules for new migrations: never edit an applied migration (add `V32` and later); start a migration that can break the previous API with a `-- rollback-compat:` annotation. `scripts/check_migration_compat.py` enforces the annotation for migrations after V28 (`BASELINE = 28`); see [CI/CD](../05-developer-workflow/ci-cd.md). Synthetic development data are repeatable migrations in `backend/app/src/main/resources/db/devdata` (`R__dev_commerce_policy.sql`, `R__dev_service_zones.sql`), loaded only by the `dev` profile, which `EnvironmentGuard` forbids outside LOCAL/CI/PREVIEW.

## Main entities and relationships

**Identity and access.** `app_user` is keyed by `(issuer, subject)` from the identity provider; it holds profile, verified phone, `status` (`ACTIVE`, `SUSPENDED`, `DELETION_REQUESTED`, `DELETED`). There is no role table for customers or cooks: a cook is an `app_user` that owns a `kitchen` (`kitchen.owner_id UNIQUE`). Staff power is `user_grant` rows (permission code, reason, optional expiry, revoke columns; a partial unique index allows one active grant per user and permission). `mfa_policy` holds one row per category (`CUSTOMER`, `KITCHEN`, `STAFF`).

**Supply side.** `cook_application` (+ immutable `cook_application_revision`) leads to `kitchen` (with `kitchen_zone` for serviceable zones and the approved revision as a foreign key). `meal` belongs to a kitchen; every content or price edit appends an immutable `meal_revision(meal_id, revision, snapshot, price_minor)`; meals are archived, never deleted (trigger). `kitchen_payment_profile` (one row per kitchen) holds deposit percentage, COD ceiling and the staff-approved recipient.

**Capacity.** `cook_slot` (kitchen, start/end, lead time, `max_portions`, counters) and `meal_slot` (capacity per meal per slot, counters). Counters `reserved`, `allocated`, `consumed` move together with `stock_allocation` rows: placement increments `reserved`, acceptance moves to `allocated`, preparation to `consumed`, release decrements. Constraints `reserved + allocated + consumed <= max_portions` (slot) and `<= capacity` (meal slot) make overselling impossible even if application code were wrong. A trigger forbids a meal slot whose meal belongs to a different kitchen. The reconciliation script `infra/db/reconcile.sql` checks that counters equal the sum of allocations; it runs after every backup restore.

**Pricing snapshots.** `commerce_policy` is versioned (tax rate, quote TTL, acceptance window, per-line maximum, terms and cancellation policy versions; exactly one `ACTIVE` row through a unique partial index). A `quote` stores the policy version and the totals it was computed with, plus payment terms; it holds no stock and expires. `customer_order` copies totals, `policy_version`, `commission_bps_snapshot`, terms versions and JSON `address_snapshot` / `contact_snapshot`; `order_item` copies the quantity, unit price and the whole meal revision as `meal_snapshot jsonb`, and has a foreign key to `(meal_id, revision)`. Later price or address edits therefore never change a placed order. `customer_order.quote_id` is `UNIQUE`, so a quote can be used once.

**Orders and history.** `customer_order.state` has ten values (see [order states](diagrams.md#9-order-fulfilment-states)). Constraints: unique `(customer_id, client_request_id)`; a delivery order must have an address snapshot and a pickup order must have a zero delivery fee. Every transition inserts an `order_event` (`sequence` unique per order, actor type and id, reason). `order_event` and `order_item` cannot be updated or deleted (trigger), orders cannot be deleted, and a terminal order cannot be updated (V12 trigger), with one narrow privacy exception: V21 (`backend/app/src/main/resources/db/migration/V21__personal_data_erasure.sql`) replaced `customer_order_terminal_guard` so that, once per order, the contact and address snapshots may become `{"erased": true}`, `fulfillment_note` is cleared and `personal_data_erased_at` is set, while every other column must stay unchanged. Any other update raises `terminal order ... is immutable`. `handover_challenge` stores only a keyed hash plus an AES-GCM ciphertext of the pickup/delivery code; `handover_attempt` rows support rate limiting.

**Payments and money records.**

| Table | Purpose | Immutability |
|---|---|---|
| `order_payment` | One row per order: method `COD` or `DEPOSIT`, deposit/remainder, recipient snapshot, state | Terms immutable by trigger; only `state`, `version`, `updated_at` change |
| `payment_event` | Timeline of who reported what (customer, cook, staff, system), amount, reference | Append-only (one erasure exception, V27) |
| `cash_obligation` | Cash due at hand-over (`total - deposit`), states `DUE`, `REPORTED`, `VERIFIED`, `DISPUTED`, `NOT_DUE` | Versioned |
| `cash_report` | Kitchen's report of cash collected; verifier must differ from reporter (`cash_report_separation_ck`) | Versioned |
| `refund` | Refund obligation workflow `REQUESTED`, `APPROVED`, `PAID`, `VERIFIED`, `REJECTED` | Versioned |
| `ledger_group`, `ledger_entry` | Double-entry commission postings (accounts `COOK_COMMISSION_RECEIVABLE`, `PLATFORM_COMMISSION_REVENUE`), unique per source event; a deferred constraint trigger requires debits = credits | Append-only |
| `settlement_batch`, `settlement_line` | Reviewed remittance of commission per kitchen and period; a ledger group can be in only one active batch | Lines guarded; nothing deleted |

With the zero-commission rule (V23 check constraint plus API validation), live ledger amounts are zero and settlement is dormant; the structures remain for a future decision. `order_payment` states are statements by parties, never "bank verified".

**Audit and configuration.** `audit_event` (actor, action, permission, object, outcome `ALLOWED`/`DENIED`, request id, redacted JSON details) is append-only including a `TRUNCATE` trigger. `config_change` records every operating-configuration change with reason and actor. `feature_flag` holds flags such as the `ordering-enabled` global kill switch. `idempotency_record` stores command results with an `expires_at >= created_at + 24h` check (a minimum retention timestamp; nothing expires or purges rows yet).

**Notifications.** `outbox_event` (PENDING, SENT, FAILED, with lease columns and `request_id`) is written in the same transaction as the domain change. `notification` is the per-recipient inbox row (unique `(recipient_id, dedup_key)`); `notification_delivery` is one row per channel/target (`EMAIL`, `PUSH`) with attempts and `DEAD` as the visible dead letter; `device_registration` stores hashed and encrypted push tokens. See [integration and events](integration-and-events.md).

**Support, reviews, media, activity.** `support_case` (order-scoped, with `CS-XXXXXXXX` reference, categories including `PAYMENT`) and `case_message` (public messages and staff notes); `review` (one per order, `order_id` unique) and `review_report`; `favorite`; `media_asset` and `meal_media`; `media_cleanup` (rows created in the same transaction that unreferences an object); `activity_daily` (day, kind, subject, kitchen, HMAC of visitor; primary key makes counts distinct visitors per day).

## Transaction boundaries

* One service call is one transaction (`@Transactional`); there is no distributed transaction. The order placement transaction touches `customer_order`, `order_item`, `stock_allocation`, `cook_slot`, `meal_slot`, `order_event`, `outbox_event`, `order_payment`, `cash_obligation` and `idempotency_record` together (diagram: [order placement](diagrams.md#8-order-placement-sequence)).
* Lock order is fixed: policy/pricing rows (share) in quote revalidation, then `cook_slot`, then `meal_slot` in id order; the order row is locked first in every lifecycle command. This prevents deadlocks between concurrent placements, accepts and expiries.
* External effects (email, push) never happen inside these transactions: they are queued through `outbox_event` and delivered by workers.

## Runtime database role

Deployed environments run Flyway as the owner and the API as `matbakhy_app` (`deploy/web-preview/postgres/20-runtime-role.sh`, `DATABASE_RUNTIME_ROLE`). `afterMigrate.sql` runs after every migrate: grants `SELECT/INSERT/UPDATE/DELETE` on all tables, revokes DDL, revokes `UPDATE, DELETE` on the fully append-only tables (`audit_event`, `config_change`, `ledger_entry`, `ledger_group`, `meal_revision`, `order_event`, `order_item`) and `DELETE` on guarded tables (`customer_order`, `cook_application_revision`, `settlement_line`, `order_payment`, `case_message`, `payment_event`). Without `DATABASE_RUNTIME_ROLE` (local development, tests) the callback does nothing. Test: `backend/app/src/test/java/com/matbakhy/app/db/RuntimeRoleIntegrationTest.java`.

## Indexes worth knowing

Hot paths have composite indexes, for example `customer_order_customer_idx (customer_id, created_at DESC, id DESC)` for the customer's list with cursor pagination, `customer_order_kitchen_idx (kitchen_id, state, created_at, id)` for the cook queue, `customer_order_accept_by_idx (state, accept_by)` for the expiry job, `outbox_event_pending_idx (status, available_at)`, `notification_delivery_due_idx (status, next_attempt_at)`, `audit_event_*` by time, actor and object, and partial unique indexes such as `cook_slot_identity_uq (kitchen_id, starts_at) WHERE state <> 'CANCELLED'`, `commerce_policy_one_active_uq`, `user_grant_active_uk` and `support_case_open_payment_uq`.

## Backups, restore, reconciliation

`scripts/db-backup.sh` writes a custom-format `pg_dump` with a SHA-256 checksum and a manifest of row counts and refuses to write inside a Git working tree; `scripts/db-restore.sh` restores and runs `infra/db/reconcile.sql`, and a backup is accepted only when every reconciliation count is zero. The preview wraps these in `deploy/web-preview/deploy.sh backup` and `restore-db`, and takes a backup before every deploy. Tests: `backend/app/src/test/java/com/matbakhy/app/reliability/BackupRestoreIntegrationTest.java`. Operations detail: [infrastructure and deployment](infrastructure-and-deployment.md#backup-restore-and-rollback).

## Known limitations and documentation gaps

* No table-level documentation exists beyond migration comments and `docs/engineering/02-data-model.md`; this page does not list every column. Read the migration for the table you touch.
* `V23` adds its commission check as `NOT VALID`, so rows created before it are not rechecked (comment in the file).
* Retention for `activity_daily` and other personal-data-adjacent tables is undecided (owner decision O06); no deletion job exists for `activity_daily`.
* The reconcile script and trigger-based protections are tested against PostgreSQL through Testcontainers; the preview's actual database was not inspected for this page (NOT VERIFIED that the deployed role grants match).
