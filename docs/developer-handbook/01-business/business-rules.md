# Business rules digest

> **Owner role:** Business documentation
> **Status:** PARTIALLY IMPLEMENTED — a navigable digest of the rules that matter most; the rule text itself stays in the canonical documents.

This page does **not** copy the 64 rules. It groups them by domain and, for the significant ones, tells you where to find the requirement, the implementing code, the database effect, the automated test and the owner end-to-end case.

Canonical sources (read these for the exact wording):

- Rule text: `docs/product/03-business-rules.md`
- Machine-readable rules: `planning/rules.json` (each rule has `story_id`, `scenario_ids` and a `status`; every rule is currently `PROPOSED_DEFAULT` unless a later owner decision says otherwise)
- Scenarios: `planning/scenarios.json`
- Owner decisions that override or extend the rules: `docs/product/01-context-and-decisions.md` (D10, D13, D14, D16, D17, D18)
- Owner E2E cases: `docs/quality/owner-acceptance/cases/*.json` (`discovery-basket.json`, `orders-finance.json`, `cook-ops.json`, `config-security.json`)

Conventions in the tables below: **Impl** is the implementing file; **Tx / API** names the transaction or endpoint (API ids from `contracts/endpoints.json`); **DB** names the table or migration; **Test** is a real JUnit file under `backend/app/src/test/java/com/matbakhy/app/`; **E2E** is an owner acceptance case id. Test method names are quoted so you can grep them. A rule marked "not covered" has no dedicated test that I found.

Related handbook pages: [order lifecycle](order-lifecycle.md), [pricing and payments](pricing-and-payments.md), [actors and permissions](actors-and-permissions.md), [requirements traceability](../07-reference/requirements-traceability.md).

## Index of all rules by domain

| Domain | Rules |
|---|---|
| Identity, access, privacy | BR-001, BR-002, BR-003, BR-004, BR-005, BR-006, BR-007, BR-043, BR-055 |
| Cook, media, catalogue | BR-008, BR-009, BR-010, BR-011, BR-012 |
| Availability, discovery, basket | BR-013, BR-014, BR-015, BR-016, BR-017, BR-018 |
| Pricing, quote, fulfilment | BR-019, BR-020, BR-021, BR-042 |
| Order, stock, lifecycle | BR-022, BR-023, BR-024, BR-025, BR-026, BR-027, BR-028, BR-029, BR-030, BR-031 |
| Money | BR-032, BR-033, BR-034, BR-035, BR-036 |
| Notification, reviews, support | BR-037, BR-038, BR-039, BR-040, BR-041 |
| Client behaviour | BR-044 to BR-052 |
| Security, observability, operations, release | BR-053, BR-054, BR-056 to BR-064 |

Deposit payments have **no BR number**. They come from owner decision D16 and stories MB-051, MB-052 and MB-053; their scenarios are TC-159, TC-161 and TC-162 and their rules are in `docs/product/09-deposit-payment-states.md`.

## Authentication

- **BR-001 — only authenticated, active accounts perform private operations; browsing is anonymous.**
  - Impl: `backend/app/src/main/java/com/matbakhy/app/security/SecurityConfiguration.java` (`PUBLIC_GET`, `.requestMatchers("/api/**").hasAuthority(AccountMapper.ACTIVE)`), `backend/app/src/main/java/com/matbakhy/app/security/AccountMapper.java`.
  - Tx / API: every `/api/**` call. DB: `app_user.status` (V1).
  - Test: `identity/IdentityIntegrationTest.java` (`anonymous_private_calls_get_401_without_private_data`, `public_browsing_stays_anonymous`, `suspended_account_is_denied_with_scoped_error_and_no_profile`).
  - E2E: E2E-DISC-001, E2E-DISC-003, E2E-OPS-062.
- **BR-002 — web mutations need CSRF protection; native apps use authorization code with PKCE; no client secret.**
  - Impl: `SecurityConfiguration.java` (cookie CSRF repository for the web session chain, bearer chain without ambient credentials), `backend/app/src/main/java/com/matbakhy/app/security/WebAccountFilter.java`.
  - DB: `SPRING_SESSION`, `SPRING_SESSION_ATTRIBUTES` (V2).
  - Test: `identity/IdentityIntegrationTest.java` (`web_login_creates_server_session_and_mutations_require_csrf`, `native_authorization_without_pkce_is_refused_by_the_provider`, `code_exchange_with_wrong_pkce_verifier_fails`, `cors_allows_only_configured_web_origins`), `security/CsrfCookieDefaultIntegrationTest.java`.
  - E2E: CFG-002, CFG-003, CFG-004, CFG-036, CFG-037.
- **BR-003 — first checkout needs a verified e-mail and a verified normalised Egyptian phone; changing the phone invalidates verification.**
  - Impl: `backend/modules/identity/src/main/java/com/matbakhy/identity/internal/AccountLifecycleService.java` (implements `CheckoutContactPolicy`), `backend/modules/identity/src/main/java/com/matbakhy/identity/api/EgyptianPhone.java`; called from `QuoteService.create`.
  - API: API-011, API-012. DB: `phone_challenge` (check `^\+201[0125][0-9]{8}$`), V3.
  - Test: `identity/ContactIntegrationTest.java` (`changing_a_verified_phone_requires_verifying_the_new_number`, `invalid_numbers_are_rejected_without_sending`), `orders/QuoteIntegrationTest.java` (`contact_verification_kitchen_state_and_cutoff_are_enforced_by_server_time`), `identity/EgyptianPhoneTest.java`.
  - E2E: E2E-DISC-105.
- **BR-004 — OTP is single use, hashed, valid 5 minutes, at most 5 attempts, 60 s resend, bounded hourly sends.**
  - Impl: `backend/modules/identity/src/main/java/com/matbakhy/identity/internal/PhoneVerificationService.java`, `PhoneVerificationProperties.java` (defaults in `backend/app/src/main/resources/application.yaml`: `code-ttl: 5m`, `max-attempts: 5`, `resend-interval: 60s`). DB: `phone_challenge.code_hmac` (keyed hash, never the code).
  - Test: `identity/ContactIntegrationTest.java` (`correct_code_verifies_once_and_replay_fails`, `expired_code_fails_and_does_not_verify`, `challenge_locks_after_five_failed_attempts`, `resend_interval_and_hourly_limits_are_enforced`).
  - E2E: E2E-DISC-105.

## Authorization and account separation

- **BR-006 — every kitchen, order, case, list, count and export checks object scope; an unguessable id is not authorization.**
  - Impl: query-level scoping in services (for example `OrderLifecycleService.lock`, `PaymentService.lockForCustomer`) and `AccessPolicy` / `backend/app/src/main/java/com/matbakhy/app/security/ScopedAccessPolicy.java`. Foreign objects return 404.
  - DB: owner/kitchen columns (`customer_order.customer_id`, `kitchen_id`, `kitchen.owner_id`).
  - Test: `identity/StaffAccessIntegrationTest.java` (`kitchen_scope_matrix`), `orders/OrderPlacementIntegrationTest.java` (`customers_see_only_their_own_orders_with_paging_and_events`, `another_customer_cannot_find_an_order_by_its_client_request_id`), `orders/OrderLifecycleIntegrationTest.java` (`cooks_act_only_on_their_kitchen_and_suspension_blocks_acceptance_but_not_fulfillment`).
  - E2E: E2E-ORDER-037, E2E-ORDER-120, E2E-COOK-054, E2E-DISC-106.
- **Account separation (customer, cook, staff are different scopes of one account).** Covered by BR-006, BR-007 and BR-008. A customer cannot run cook commands and a cook cannot use customer commands; no public registration path assigns cook approval or a staff grant; switching accounts on a device clears the previous person's data (BR-048).
  - Test: `orders/OrderLifecycleIntegrationTest.java` (`the_ordering_customer_cannot_run_cook_commands_and_the_cook_cannot_use_the_customer_cancel`), `identity/StaffAccessIntegrationTest.java` (`customer_cannot_grant_privileges_and_the_attempt_is_audited`, `role_fields_in_customer_requests_are_rejected`, `provider_role_claims_grant_no_staff_permission` in `IdentityIntegrationTest.java`).
  - E2E: E2E-OPS-058, CFG-005, CFG-006, E2E-DISC-004.
- **BR-007 — staff privileges are assigned server side; revoked grants stop working; MFA follows the administrator's per-population policy (D18).**
  - Impl: `backend/modules/identity/src/main/java/com/matbakhy/identity/internal/GrantService.java`, `backend/modules/identity/src/main/java/com/matbakhy/identity/api/Permission.java`, `AccountMapper` (grants re-read on every request).
  - API: API-091..API-093. DB: `user_grant` with a unique index on one active grant per user and permission (V4), `audit_event` append-only (V4).
  - Test: `identity/StaffAccessIntegrationTest.java` (`revoked_grant_stops_working_on_the_next_request`, `expired_grants_confer_nothing`, `grant_changes_require_recent_mfa`, `audit_is_append_only_and_redacted`).
  - E2E: E2E-OPS-055, E2E-OPS-059, E2E-OPS-102.
- **BR-043 — no self-escalation; the last recoverable administrator cannot be removed; high-risk changes need step-up.**
  - Impl: `GrantService.grant` (self-escalation refused and audited) and `GrantService.revoke` (last `staff.grant` holder protected).
  - Test: `identity/StaffAccessIntegrationTest.java` (`self_escalation_unknown_permissions_and_last_admin_are_refused`).
  - E2E: E2E-OPS-060, E2E-OPS-101, CFG-105.

## MFA (decision D18, referenced from BR-007)

- Policy modes `DISABLED`, `OPTIONAL`, `REQUIRED` per population `CUSTOMER`, `KITCHEN`, `STAFF`; the user's preference never relaxes `REQUIRED`; a missing policy row fails safe to `REQUIRED`.
  - Impl: `backend/app/src/main/java/com/matbakhy/app/mfa/MfaPolicyService.java` (`resolve`), `backend/app/src/main/java/com/matbakhy/app/security/MfaEnforcementFilter.java`, `backend/app/src/main/java/com/matbakhy/app/security/StaffAssurance.java`, `backend/app/src/main/java/com/matbakhy/app/mfa/MfaAdminController.java`.
  - API: API-112..API-116. DB: `mfa_policy`, `user_mfa_preference`, `user_mfa_reset` (V31).
  - Test: `mfa/MfaPolicyResolveTest.java` (pure precedence rules), `mfa/MfaPolicyIntegrationTest.java`, `identity/MfaResetIntegrationTest.java`, `security/StaffAssuranceTest.java`.
  - E2E: CFG-101, CFG-011, CFG-012, CFG-014, CFG-016, CFG-018, CFG-019, CFG-033, CFG-104, E2E-OPS-111. Policy document: `docs/security/mfa-policy.md`.

## Admin permissions

Covered by BR-007 and BR-043 above and by the permission-to-capability table in [actors and permissions](actors-and-permissions.md#permission-to-capability-map-staff). The administrator is not a superuser: the bootstrap administrator receives only `staff.grant` and `audit.read` (`GrantService.bootstrapAdministratorIfNone`). E2E: E2E-OPS-055.

## Kitchen approval

- **BR-008 — cook approval is required before selling; approval needs a complete revision and a reviewer/reason record.**
  - Impl: `backend/modules/cooks/src/main/java/com/matbakhy/cooks/internal/CookApplicationService.java`, `backend/modules/cooks/src/main/java/com/matbakhy/cooks/internal/CookReviewService.java`.
  - API: API-042..API-044 (applicant), API-067..API-069 (reviewer, `cook.review` plus recent MFA). DB: `cook_application`, `cook_application_revision` (immutable trigger), `kitchen` (V5).
  - Test: `cooks/CookWorkflowIntegrationTest.java` (`incomplete_application_cannot_be_submitted_and_creates_no_revision`, `changes_requested_then_resubmitted_then_approved_preserves_every_revision`, `only_a_scoped_reviewer_with_recent_mfa_can_decide`, `a_reviewer_cannot_approve_their_own_application_and_the_attempt_is_audited`, `concurrent_approvals_create_exactly_one_kitchen`).
  - E2E: E2E-COOK-048, E2E-COOK-049, E2E-COOK-111, E2E-OPS-108, E2E-OPS-109.
- **BR-009 — pause stops new sales; suspension also stops acceptance but keeps accepted orders fulfillable.**
  - Impl: `backend/modules/cooks/src/main/java/com/matbakhy/cooks/internal/KitchenService.java`, `KitchenDirectory.KitchenStatus` in `backend/modules/cooks/src/main/java/com/matbakhy/cooks/api/KitchenDirectory.java`.
  - API: API-070, API-065. DB: `kitchen.operating_state`.
  - Test: `cooks/CookWorkflowIntegrationTest.java` (`pause_stops_new_sales_suspension_stops_acceptance_and_accepted_orders_stay_fulfillable`, `only_scoped_staff_change_operating_state_and_never_for_their_own_kitchen`).
  - E2E: E2E-COOK-050, E2E-COOK-107, E2E-DISC-104.
- Catalogue (BR-011, BR-012): publication requirements and revisions in `backend/modules/catalog/src/main/java/com/matbakhy/catalog/internal/meal/MealService.java`; DB `meal`, `meal_revision` (V7). Test: `catalog/MealIntegrationTest.java` (`every_publication_requirement_is_named_and_invalid_declarations_are_refused`, `edits_create_new_revisions_while_earlier_snapshots_stay_unchanged`, `archive_removes_the_meal_from_sale_without_touching_history`). E2E: E2E-COOK-101, E2E-COOK-103, E2E-COOK-104. Detail in [kitchen management](kitchen-management.md).

## Order placement

- **BR-018 — one cook and one slot per basket.** Impl: `QuoteService.terms` (`MIXED_KITCHENS_OR_SLOT`). Test: `orders/QuoteIntegrationTest.java` (`mixed_kitchens_unoffered_meals_and_invalid_baskets_are_rejected`). E2E: E2E-BASKET-010, E2E-BASKET-021.
- **BR-021 — delivery needs zone coverage and a complete own address; pickup only if enabled, zero delivery fee.** Impl: `QuoteService.terms`, `JdbcMealCatalog.deliveryFee`. DB: `kitchen_zone`, `address`. Test: `orders/QuoteIntegrationTest.java` (`unserviceable_delivery_and_disabled_pickup_are_denied_without_conversion`). E2E: E2E-DISC-007.
- **BR-022 — placement atomically validates the quote, reserves all stock, writes snapshots, idempotency, events, outbox and the payment/cash records.**
  - Impl: `backend/modules/orders/src/main/java/com/matbakhy/orders/internal/OrderService.java` (`placeOnce`).
  - API: API-022. DB: `customer_order`, `order_item`, `stock_allocation`, `order_event`, `outbox_event`, `cash_obligation` (V11), `order_payment` (V24).
  - Test: `orders/OrderPlacementIntegrationTest.java` (`placement_writes_order_snapshots_reservation_event_outbox_and_cash_obligation_together`, `an_injected_failure_after_the_stock_update_rolls_everything_back_and_a_retry_completes_once`).
  - E2E: E2E-ORDER-021, E2E-ORDER-101, E2E-ORDER-102.
- **BR-023 — permanent unique customer + `clientRequestId` prevents duplicate orders even after the cached idempotency response expires.** Impl: `OrderService.placeOnce` (advisory lock, fingerprint). DB: `customer_order_request_uq` (V11), `request_fingerprint` (V29). Test: `OrderPlacementIntegrationTest` (`retries_after_timeout_and_after_idempotency_retention_create_exactly_one_order`, `a_reused_request_reference_with_a_changed_field_conflicts_even_after_the_cache_is_gone`), `backend/app/src/test/java/com/matbakhy/orders/internal/OrderFingerprintTest.java`. E2E: E2E-ORDER-022, E2E-ORDER-024, E2E-ORDER-105, E2E-ORDER-106, E2E-ORDER-123.
- **BR-024 — idempotency scope is actor + command + key; a different payload with the same key conflicts.** Impl: `backend/app/src/main/java/com/matbakhy/app/idempotency/JdbcIdempotentCommands.java`. DB: `idempotency_record` (V1). Test: `OrderPlacementIntegrationTest` (`reusing_a_key_or_request_reference_with_another_body_conflicts_and_leaves_the_order_unchanged`). E2E: E2E-ORDER-023.

## Pricing and stale quotes

- **BR-019 — server calculates integer EGP amounts; client prices, fees and roles are rejected.** Impl: `QuoteService`, `backend/app/src/main/java/com/matbakhy/app/config/StrictJsonConfiguration.java`. Test: `orders/QuoteIntegrationTest.java` (`the_server_computes_exact_totals_and_forged_amounts_are_rejected`, `tax_is_applied_with_half_up_rounding_from_the_policy_snapshot`), `web/StrictJsonIntegrationTest.java`. E2E: E2E-BASKET-020, E2E-ORDER-113.
- **BR-020 — a quote lasts 5 minutes, reserves no stock, snapshots versions and shows full cost and policy.** Impl: `QuoteService.create`. API: API-021. DB: `quote`, `quote_item`, `commerce_policy` (V10; immutable trigger). Test: `QuoteIntegrationTest` (`quotes_hold_no_stock_and_cannot_exceed_remaining_portions`, `quotes_are_immutable`). E2E: E2E-BASKET-013, E2E-BASKET-029, E2E-ORDER-111.
- **BR-042 (with RQ-004 and D13) — fee, rate and policy changes are versioned and audited; a stale quote is refused and re-accepted; placed orders keep their snapshots.**
  - Impl: `QuoteService.requireValid`, `backend/modules/orders/src/main/java/com/matbakhy/orders/internal/ConfigurationService.java`. API: API-090. DB: `config_change` (V19).
  - Test: `orders/PriceChangeIntegrationTest.java`, `orders/QuoteIntegrationTest.java` (`a_stale_quote_is_refused_with_a_recoverable_reason`), `orders/ConfigurationIntegrationTest.java` (`a_fee_change_keeps_placed_orders_invalidates_stale_quotes_and_applies_to_new_quotes`). Requirement text: `docs/product/rq-004-stale-quote.md`.
  - E2E: E2E-BASKET-014, E2E-BASKET-015, E2E-BASKET-022, E2E-ORDER-112, E2E-OPS-106, E2E-OPS-107.

## Capacity reservation

- **BR-013 / BR-025 — capacity is bounded per meal-slot and per cook-slot; the last portion never oversells.** Impl: `backend/modules/availability/src/main/java/com/matbakhy/availability/internal/JdbcSlotInventory.java`, `SlotService`. DB: `cook_slot`, `meal_slot` (V8), `stock_allocation` (V11). Test: `availability/SlotIntegrationTest.java` (`the_last_portion_is_bounded_by_both_the_meal_and_the_slot_aggregate`, `capacity_cannot_be_reduced_below_what_orders_already_hold`), `OrderPlacementIntegrationTest` (`twenty_customers_racing_for_the_last_portion_produce_exactly_one_order`, `a_two_meal_order_is_refused_without_partial_reservation_once_the_kitchen_slot_total_is_used_up`). E2E: E2E-ORDER-115, E2E-BASKET-016, E2E-BASKET-017, E2E-BASKET-018, E2E-ORDER-114, E2E-COOK-106.
- **BR-014 / BR-015 — UTC storage, Africa/Cairo scheduling, cutoff before start.** Impl: `CairoTime`, `SlotService.create`. Test: `availability/SlotIntegrationTest.java` (`ordinary_slots_store_utc_instants_for_cairo_wall_clock_times`, `a_local_time_skipped_by_the_spring_change_is_rejected`, `server_time_reaching_cutoff_closes_ordering_and_capacity_edits`). E2E: E2E-COOK-105, E2E-BASKET-023.

## Cancellation, expiry and release

- **BR-026 — only legal, actor-scoped transitions with the expected version; terminal orders are immutable.** Impl: `backend/modules/orders/src/main/java/com/matbakhy/orders/internal/OrderLifecycleService.java` (`guard`, `require`, `transition`). DB: `customer_order_terminal_guard` trigger (V12), `order_event` append-only. Test: `orders/OrderLifecycleIntegrationTest.java` (`terminal_orders_and_stale_versions_are_rejected_without_changing_history`). E2E: E2E-ORDER-036, E2E-ORDER-121.
- **BR-027 — acceptance deadline is `min(created + window, slot cutoff)` with a minimum remaining time; expiry uses server time.** Impl: `QuoteService.terms`, `backend/modules/orders/src/main/java/com/matbakhy/orders/internal/OrderExpiryJob.java`. Test: `OrderLifecycleIntegrationTest` (`the_expiry_job_expires_overdue_orders_once_by_server_time`, `acceptance_is_allowed_one_millisecond_before_the_deadline_and_refused_exactly_at_it`). E2E: E2E-ORDER-029.
- **BR-028 — the customer cancels pending orders directly; after acceptance only a request; preparing/ready cancellation needs ops with a reason.** Impl: `OrderLifecycleService.customerCancel`, `opsExceptionCancel`, `backend/modules/support/src/main/java/com/matbakhy/support/internal/SupportService.java` (cancellation request). API: API-027, API-028, API-076. Test: `OrderLifecycleIntegrationTest` (`customers_cancel_pending_orders_directly_but_not_accepted_ones`, `prepared_orders_cancelled_by_ops_keep_consumed_stock_and_never_release_twice`), `support/cases/SupportIntegrationTest.java` (`a_cancellation_request_never_changes_the_order_and_is_resolved_by_an_audited_exception_cancel`). E2E: E2E-ORDER-027, E2E-ORDER-035, E2E-ORDER-109, E2E-ORDER-110.
- **BR-029 — reject, expire and pre-preparation cancel release stock exactly once; after preparation capacity stays consumed.** Impl: `OrderLifecycleService.release` and `move`. DB: `stock_allocation.state` (`RESERVED`, `ALLOCATED`, `CONSUMED`, `RELEASED`). Test: `OrderLifecycleIntegrationTest` (`accept_cancel_and_expire_racing_produce_one_legal_outcome_and_at_most_one_release`). E2E: E2E-ORDER-026, E2E-ORDER-028.

## Pickup and delivery

- **BR-030 — pickup completes only from READY; delivery goes READY to OUT_FOR_DELIVERY first; wrong-mode commands are rejected.** Impl: `OrderLifecycleService.cook` (`dispatch`, `handover`, `requireHandoverState`). API: API-059, API-060. Test: `OrderLifecycleIntegrationTest` (`pickup_order_moves_through_every_stage_with_stock_and_events_in_step`, `wrong_fulfillment_paths_are_denied_and_the_correct_path_keeps_history`). E2E: E2E-ORDER-031, E2E-ORDER-032, E2E-ORDER-034.
- **BR-031 — completion needs a single-use customer handover code with bounded attempts; ops override needs fresh MFA, reason and evidence.** Impl: `OrderLifecycleService.handover`, `opsHandoverOverride`, `backend/modules/orders/src/main/java/com/matbakhy/orders/internal/HandoverCodes.java`, `HandoverProperties.java`. API: API-060, API-077. DB: `handover_challenge`, `handover_attempt` (V12). Test: `OrderLifecycleIntegrationTest` (`wrong_codes_are_bounded_valid_codes_work_once_and_locked_challenges_need_an_audited_override`, `a_valid_code_completes_once_and_cannot_be_replayed`, `handover_attempts_are_rate_limited_per_actor`). E2E: E2E-ORDER-030, E2E-ORDER-031, E2E-ORDER-033, E2E-ORDER-108.

## Deposits (D16; no BR number)

- Rule source: `docs/product/09-deposit-payment-states.md`, scenarios TC-159, TC-161, TC-162.
  - Impl: `backend/modules/orders/src/main/java/com/matbakhy/orders/internal/PaymentService.java`, `backend/modules/orders/src/main/java/com/matbakhy/orders/web/PaymentController.java`.
  - API: API-100..API-109. DB: `kitchen_payment_profile`, `order_payment`, `payment_event` (V24), reconciliation additions (V25), PAYMENT support case index (V28).
  - Test: `payments/DepositPaymentIntegrationTest.java` (`deposit_terms_are_quoted_and_snapshotted_and_cod_follows_the_kitchen_limit`, `a_transfer_report_is_not_payment_and_preparation_waits_for_the_kitchen_report`), `payments/PaymentReconciliationIntegrationTest.java` (`the_kitchen_reports_not_received_or_a_wrong_amount_and_staff_can_never_mark_money_received`, `a_dispute_or_refund_obligation_opens_one_payment_support_case_for_the_order`).
  - E2E: E2E-FIN-039 to E2E-FIN-042, E2E-FIN-101, E2E-FIN-103 to E2E-FIN-109, E2E-FIN-115, E2E-COOK-108.

## Cash reconciliation, refunds, zero commission

- **BR-032 — completion and collection are independent; cook report and finance verification are separate events.** Impl: `backend/modules/finance/src/main/java/com/matbakhy/finance/internal/CashService.java`. API: API-063, API-083. DB: `cash_obligation` (V11), `cash_report` with `cash_report_separation_ck` (V13). Test: `finance/CashAndLedgerIntegrationTest.java` (`a_completed_order_without_a_report_stays_due_with_no_invented_receipt`, `exact_report_then_independent_verification`, `short_or_zero_reports_are_exceptions_and_need_a_reasoned_decision`, `reports_need_a_completed_own_order_and_reporters_cannot_verify_themselves`). E2E: E2E-FIN-038, E2E-FIN-043, E2E-FIN-102, E2E-FIN-114.
- **BR-033 / BR-034 — commission snapshot, balanced immutable ledger posted once per source event; cook-collected cash creates a receivable, not platform cash. Under D10 the rate is 0.** Impl: `backend/modules/finance/src/main/java/com/matbakhy/finance/internal/LedgerService.java`, `LedgerPostingJob.java`. DB: `ledger_group`, `ledger_entry` (V13), launch check in V23. Test: `finance/CashAndLedgerIntegrationTest.java` (`commission_posts_once_per_completion_event_as_a_balanced_group_from_the_snapshot`, `two_posting_instances_racing_post_each_completion_exactly_once_and_balanced`), `finance/ZeroCommissionIntegrationTest.java`, `finance/FinanceOperationsIntegrationTest.java` (`cook_collected_cash_creates_only_a_commission_receivable`). E2E: E2E-FIN-045, E2E-FIN-046, E2E-FIN-110, E2E-FIN-111, E2E-OPS-107.
- **BR-035 / BR-036 — full refunds bounded by verified collection; settlement lines unique, reviewed batches immutable.** Impl: `backend/modules/finance/src/main/java/com/matbakhy/finance/internal/FinanceOpsService.java`. API: API-084, API-085, API-087, API-088. DB: `refund`, `settlement_batch`, `settlement_line` (V18). Test: `finance/FinanceOperationsIntegrationTest.java` (`refunds_are_full_bounded_by_verified_collection_and_reverse_commission_once`, `settlement_lines_are_unique_and_reviewed_batches_immutable`). E2E: E2E-FIN-044, E2E-FIN-110.

## Other rules worth knowing

| Rule | One-line meaning | Impl / test / E2E |
|---|---|---|
| BR-037, BR-038 | Orders and inbox survive provider failure; transactional outbox with leases and dedupe | `backend/modules/notifications/src/main/java/com/matbakhy/notifications/internal/OutboxDispatcher.java`; `notifications/NotificationIntegrationTest.java` (`two_workers_and_duplicate_events_produce_one_inbox_record_and_one_send`); E2E-ORDER-117 |
| BR-039 | One review, only by the owner of a COMPLETED order, rating 1 to 5 | `backend/modules/orders/src/main/java/com/matbakhy/orders/internal/ReviewService.java`; `orders/ReviewsAndFavoritesIntegrationTest.java`; E2E-DISC-110 |
| BR-041 | Support cases are order-scoped; private staff notes never reach customer or cook | `SupportService`; `support/cases/SupportIntegrationTest.java` (`staff_notes_stay_private_and_safety_cases_are_escalated_first`); E2E-OPS-057, E2E-ORDER-118 |
| BR-055 | Deletion revokes access and erases personal data while keeping financial integrity | `backend/modules/identity/src/main/java/com/matbakhy/identity/internal/AccountErasureService.java`; `privacy/AccountErasureIntegrationTest.java`; E2E-DISC-114, E2E-COOK-110 |
| BR-056 | Logs and metrics exclude tokens, OTP, contact, address | `privacy/LogRedactionIntegrationTest.java`; CFG-040 |
| BR-060 | Environments are separated; fake adapters cannot start in production | `backend/app/src/main/java/com/matbakhy/app/config/EnvironmentGuard.java`; `config/EnvironmentGuardTest.java`; CFG-045 |

## Known limitations and documentation gaps

- Every rule in `planning/rules.json` has status `PROPOSED_DEFAULT`; this reflects the planning pack. Later owner decisions (D10, D13, D14, D16, D17, D18) are authoritative where they conflict.
- BR-054 (rate limits on login, OTP, quote, order, upload, search) is only partly implemented; see the limitations in [actors and permissions](actors-and-permissions.md). Owner case CFG-039 records this.
- BR-033 and BR-034 describe commission mechanics that are dormant under zero commission.
- Client-behaviour rules (BR-044 to BR-052) are verified by front-end and native tests, which this digest does not map. See [test execution](../05-developer-workflow/test-execution.md).
