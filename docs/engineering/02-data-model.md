# Data dictionary and persistence invariants

PostgreSQL is the system of record. IDs are UUIDs (version chosen and pinned during implementation); public order numbers are separate human-readable unique identifiers. `created_at`/`updated_at` use UTC `timestamptz`; scheduling also stores IANA zone and intended local date/time. Mutable aggregates have a numeric `version`. Money uses checked 64-bit integer minor units and ISO currency; EGP has two decimal places. No binary floating point for amounts.

## Entities

| Entity | Essential fields | Constraints / access |
|---|---|---|
| app_user | id, issuer, subject, email_verified, display_name, locale, phone_e164, phone_verified_at, status, terms_version | unique issuer+subject; email/phone changes invalidate associated verification; no password in application tables |
| user_grant | user_id, permission/role, scope, granted_by, expires_at, revoked_at | grants require staff permission; history retained |
| address | id, user_id, label, recipient_name, phone, governorate, city, zone_id, street, building, floor, unit, landmark, optional lat/lon, version | user scope; coordinates optional; no public exposure; soft archive when referenced |
| service_zone | id, localized_name, parent_city, active, delivery_fee_minor, currency, policy_version | inactive zone cannot quote new delivery; list-based serviceability initially; polygon mapping later |
| cook_application | id, user_id, revision, state, public_name, bio, contact_ref, private_location_ref, submitted_at, decision_reason, reviewer | all approval transitions audited; no document bytes in public DTO |
| verification_document | implemented as `document_ids` on `cook_application` and its revisions, referencing `media_asset` rows of purpose COOK_DOCUMENT (MB-013); document kind and retention follow O06 | short-lived signed reads with scoped permission; never public bucket |
| kitchen | id, owner_id, public_slug, display_names, description, operating_state, approval_ref, timezone, pickup_enabled, private_location_ref, version | one kitchen per cook for MVP; unique slug; approved+active required to sell |
| kitchen_zone | kitchen_id, zone_id, delivery_enabled, fee_override_minor, version | unique pair; explicit serviceability |
| meal | id, kitchen_id, localized title/description, category, portion_text, ingredients_text, allergen_codes, allergen_statement, price_minor, currency, status, revision | required ingredient/allergen declaration before publish; allergens `NONE_DECLARED`/`UNKNOWN` explicit; price >0 |
| meal_media | meal_id, media_id, position, alt_text | ordered bounded list; only approved media publicly visible |
| media_asset | id, owner_id, object_key, content_type, bytes, sha256, scan_state, public_variant_key | size/type checks; metadata stripped; cannot attach another cook's private media |
| cook_slot | id, kitchen_id, local_date, starts_at, ends_at, timezone, lead_minutes, max_portions, reserved, allocated, consumed, state, version | end>start; nonnegative counters; reserved+allocated+consumed≤capacity; exclusive semantic slot identity |
| meal_slot | id, meal_id, cook_slot_id, capacity, reserved, allocated, consumed, version | unique meal+slot; meal kitchen must match cook slot; same nonnegative bound |
| quote | id, customer_id, kitchen_id, slot_id, fulfillment_mode, address_id/version or pickup_ref, policy versions, totals, expires_at, payload_hash | server immutable quote; own customer; expires; no stock reserved |
| quote_item | quote_id, meal_id, meal_revision, quantity, unit_price_minor, line_total_minor | quantity integer 1..configured max; no client-authoritative prices |
| customer_order | id, public_number, customer_id, kitchen_id, slot_id, state, fulfillment_mode, contact/address snapshot, subtotal, fee, tax, total, commission_bps_snapshot, commission_minor, currency, quote_id, client_request_id, accept_by, version | unique customer+client_request_id permanently; immutable amounts/snapshots; legal transitions only |
| order_item | id, order_id, meal_id, meal_snapshot, allergen_snapshot, quantity, unit_price_minor, line_total_minor | historical truth unaffected by meal edit/delete; sum matches order subtotal |
| stock_allocation | id, order_id, meal_slot_id, quantity, state, released_at | unique order+meal_slot; one release/consume action; linked cook-slot aggregate updates same transaction |
| order_event | id, order_id, from_state, to_state, actor, reason, occurred_at, metadata, source_request_id | append-only; sanitized metadata; deterministic ordering sequence |
| handover_challenge | order_id, code_hash, encrypted_display_value, encryption_key_version, expires_at, failed_attempts, consumed_at | encrypted customer-only retrieval; no plaintext in logs/ops DTO; code consumed once |
| idempotency_record | actor_id, command, key, request_hash, result_status, resource_id, response_snapshot, created_at, expires_at | unique actor+command+key; conflicting payload rejected; at least 24h response retention; permanent order client_request_id prevents duplicates after expiry |
| cash_collection | id, order_id, reporter_id, expected_minor, reported_minor, method, reference, state, verified_by, version | corrections append events; no overwriting verified amounts |
| finance_entry | id, order_id, account_code, debit_minor, credit_minor, currency, transaction_group, policy_version, source_event_id | immutable balanced groups; unique source_event+account/line; no double posting |
| refund | id, order_id, case_id, requested_minor, approved_minor, payer, payee, state, reason, evidence_ref, reviewer | total verified refunds≤verified collections; duplicate reference prevention; compensating entries |
| settlement_batch/line | id, kitchen_id, period, state, totals; order_id, amount, entry_refs | unique eligible entry membership; immutable reviewed batch; adjustments in later batch |
| favorite | user_id, meal_id, created_at | unique pair; archived meal stays visibly unavailable or can be removed |
| review | id, order_id, customer_id, kitchen_id, rating, text, visibility, moderation_reason | unique order; customer owns completed order; integer rating 1..5 |
| support_case/message | id, order_id, category, severity, state, assigned_to, timestamps; author, body, attachments | role-scoped; private staff notes distinct from customer-visible messages |
| notification | id, recipient_id, event_id, type, localized payload keys, read_at, created_at | unique recipient+event+type; server inbox authoritative |
| device_registration | id, user_id, installation_id, platform, push_token_encrypted, enabled, last_seen | rotate/update; disable on logout/unregister; no token logs |
| outbox_event/delivery_attempt | id, aggregate_id, event_type, payload, status, available_at, lease_until, attempts; provider/result metadata | lease/skip-locked workers; retry bounded; payload avoids unnecessary PII |
| notification / notification_delivery / device_registration (V14) | inbox row unique per (recipient, type + order); per-channel deliveries with status PENDING/SENT/DEAD/SKIPPED, attempts, lease; installations with keyed token hash and AES-GCM token ciphertext | inbox survives provider failure; retries bounded (5) then DEAD; one active registration per installation and per token |
| kitchen.paused_by (V15) | COOK or STAFF while PAUSED | cooks resume only their own pause; staff pause/suspension need staff |
| meal moderation columns (V16) | moderation_state VISIBLE/HIDDEN, reason, moderated_at/by | hidden meals are never public or sold; separate from cook publication status |
| support_case / case_message (V17) | order-scoped cases with category, severity, state, SLA due, first response, escalation, resolution; append-only messages with PUBLIC/STAFF_ONLY visibility | one open cancellation request per order; customer messages always public; resolved/closed need a resolution code |
| policy_version | id, key, value_json, effective_at, created_by, reason, approved_reference | append version; live environment rejects unapproved commercial settings |
| audit_event | id, actor, permission, action, object_type/id, before/after redacted, reason, request_id, timestamp | append-only; scoped reads; no OTP/token/document contents |
| deletion_request | user_id, requested_at, state, legal_hold_reason, completed_at, policy_version | disable access promptly; anonymize/purge according to approved retention policy |

## Indexes and transaction checks

Create indexes driven by queries: meals `(status,kitchen_id)` and normalized search/category; slots `(kitchen_id,starts_at,state)`; orders `(customer_id,created_at,id)`, `(kitchen_id,state,created_at,id)`, `(state,accept_by)`; outbox `(status,available_at)`; audit/cases by scope+date; finance by kitchen+period+state. Use stable keyset pagination for large order/event lists and stable tie-breakers for all sorting. Validate with EXPLAIN on realistic fixtures.

Check monetary sums, positive quantities, currency homogeneity, stock nonnegativity, balanced ledger groups, unique reviews, unique source events and request IDs. Some multi-row invariants require transactional service checks or deferred database mechanisms; do not pretend a single-row CHECK protects an entire ledger group.

## Price and commission examples (synthetic)

Two portions at EGP 120.00: `2 * 12000 = 24000` minor units. Delivery `3000`; sample configured tax `0`; total `27000` (EGP 270.00). At a **sample only** 10% commission (`1000` basis points), commission on item subtotal is `2400`; delivery is excluded. Commission rounding: half-up once on eligible order item subtotal using checked integer/decimal arithmetic. This sample rate/tax is not a live policy.

Cook-collected cash creates no platform cash receipt. At completion: debit cook commission receivable 2400 / credit commission revenue 2400. When actual remittance is verified: debit platform cash clearing 2400 / credit cook commission receivable 2400. Refund/commission reversal uses compensating entries under approved policy. Record delivery income/cost as cook obligations if appropriate; never count gross merchandise value as platform revenue.

MVP supports full-order customer refunds; partial refunds are a conditional extension requiring line allocation/rounding design. A full refunded order reverses its original commission amount exactly once if the approved policy calls for reversal. Refund record may remain pending even when order is cancelled.

## Migration and privacy

Create forward-only versioned Flyway migrations; never edit an applied migration. Test blank database and previous supported release → current. Use expand/backfill/contract across releases when needed. Take a verified backup before destructive migration; prefer forward repair; database rollback is a separately tested procedure.

Seed fixtures are idempotent, environment-gated and synthetic. Real identity documents, precise addresses and production database dumps never enter source control. Retention durations and legal holds are open launch policy O06; configure them explicitly before live data collection. Test deletion across database, object storage, search/cache, notification providers and backups under that policy.
