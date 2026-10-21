# ملخص قواعد العمل

> **Owner role:** Business documentation
> **Status:** PARTIALLY IMPLEMENTED — ملخص سهل التصفّح للقواعد الأهم؛ نص القاعدة نفسه بيفضل في المستندات الأصلية.
> **Language / اللغة:** [English](business-rules.md) · العربية

الصفحة دي **مش** بتنسخ الـ 64 قاعدة. هي بتجمّعهم حسب الـ Domain، وللقواعد المهمة بتقولك فين تلاقي المتطلب، والكود اللي بينفّذه، وأثره على الـ Database، والاختبار الآلي، وحالة الـ End-to-End الخاصة بالمالك.

المصادر الأصلية (اقرأها عشان تعرف النص بالظبط):

- نص القواعد: `docs/product/03-business-rules.md`
- القواعد في صيغة مقروءة للآلة: `planning/rules.json` (كل قاعدة ليها `story_id` و`scenario_ids` و`status`؛ كل القواعد حاليًا `PROPOSED_DEFAULT` إلا لو قرار لاحق من المالك قال غير كده)
- السيناريوهات: `planning/scenarios.json`
- قرارات المالك اللي بتلغي القواعد أو بتوسّعها: `docs/product/01-context-and-decisions.md` (D10، D13، D14، D16، D17، D18)
- حالات الـ E2E بتاعة المالك: `docs/quality/owner-acceptance/cases/*.json` (`discovery-basket.json` و`orders-finance.json` و`cook-ops.json` و`config-security.json`)

الاصطلاحات في الجداول اللي تحت: **Impl** هو الملف المنفّذ؛ **Tx / API** بيسمّي الـ Transaction أو الـ Endpoint (أرقام الـ API من `contracts/endpoints.json`)؛ **DB** بيسمّي الجدول أو الـ Migration؛ **Test** هو ملف JUnit حقيقي تحت `backend/app/src/test/java/com/matbakhy/app/`؛ **E2E** هو رقم حالة القبول الخاصة بالمالك. أسماء دوال الاختبار مكتوبة بين علامات اقتباس عشان تقدر تعمل لها grep. القاعدة المكتوب عليها "not covered" معندهاش اختبار مخصّص لها لقيته.

صفحات الدليل المرتبطة: [دورة حياة الأوردر](order-lifecycle-ar.md)، [التسعير والدفع](pricing-and-payments-ar.md)، [الأطراف والصلاحيات](actors-and-permissions-ar.md)، [تتبّع المتطلبات](../07-reference/requirements-traceability-ar.md).

## فهرس كل القواعد حسب الـ Domain

| الـ Domain | القواعد |
|---|---|
| الهوية والوصول والخصوصية | BR-001, BR-002, BR-003, BR-004, BR-005, BR-006, BR-007, BR-043, BR-055 |
| الطبّاخ والميديا والكتالوج | BR-008, BR-009, BR-010, BR-011, BR-012 |
| التوافر والاستكشاف والسلة | BR-013, BR-014, BR-015, BR-016, BR-017, BR-018 |
| التسعير والعرض والتنفيذ | BR-019, BR-020, BR-021, BR-042 |
| الأوردر والمخزون ودورة الحياة | BR-022, BR-023, BR-024, BR-025, BR-026, BR-027, BR-028, BR-029, BR-030, BR-031 |
| الفلوس | BR-032, BR-033, BR-034, BR-035, BR-036 |
| الإشعارات والتقييمات والدعم | BR-037, BR-038, BR-039, BR-040, BR-041 |
| سلوك العميل (الـ Client) | BR-044 to BR-052 |
| الأمان والمراقبة والتشغيل والإصدار | BR-053, BR-054, BR-056 to BR-064 |

مدفوعات العربون **ملهاش رقم BR**. هي جاية من قرار المالك D16 ومن القصص MB-051 وMB-052 وMB-053؛ سيناريوهاتها TC-159 وTC-161 وTC-162 وقواعدها في `docs/product/09-deposit-payment-states.md`.

## الـ Authentication

- **BR-001 — بس الحسابات النشطة اللي مسجّلة دخولها هي اللي تعمل العمليات الخاصة؛ التصفّح مفتوح للجميع من غير تسجيل.**
  - Impl: `backend/app/src/main/java/com/matbakhy/app/security/SecurityConfiguration.java` (`PUBLIC_GET`, `.requestMatchers("/api/**").hasAuthority(AccountMapper.ACTIVE)`), `backend/app/src/main/java/com/matbakhy/app/security/AccountMapper.java`.
  - Tx / API: كل نداء على `/api/**`. DB: `app_user.status` (V1).
  - Test: `identity/IdentityIntegrationTest.java` (`anonymous_private_calls_get_401_without_private_data`, `public_browsing_stays_anonymous`, `suspended_account_is_denied_with_scoped_error_and_no_profile`).
  - E2E: E2E-DISC-001, E2E-DISC-003, E2E-OPS-062.
- **BR-002 — العمليات اللي بتغيّر بيانات في الويب محتاجة حماية CSRF؛ والتطبيقات الـ Native بتستخدم authorization code مع PKCE؛ ومفيش client secret.**
  - Impl: `SecurityConfiguration.java` (cookie CSRF repository لسلسلة الـ Session الخاصة بالويب، وسلسلة الـ bearer من غير credentials ضمنية)، `backend/app/src/main/java/com/matbakhy/app/security/WebAccountFilter.java`.
  - DB: `SPRING_SESSION`, `SPRING_SESSION_ATTRIBUTES` (V2).
  - Test: `identity/IdentityIntegrationTest.java` (`web_login_creates_server_session_and_mutations_require_csrf`, `native_authorization_without_pkce_is_refused_by_the_provider`, `code_exchange_with_wrong_pkce_verifier_fails`, `cors_allows_only_configured_web_origins`), `security/CsrfCookieDefaultIntegrationTest.java`.
  - E2E: CFG-002, CFG-003, CFG-004, CFG-036, CFG-037.
- **BR-003 — أول Checkout محتاج إيميل متحقَّق منه ورقم تليفون مصري متطبَّع ومتحقَّق منه؛ تغيير التليفون بيلغي التحقق.**
  - Impl: `backend/modules/identity/src/main/java/com/matbakhy/identity/internal/AccountLifecycleService.java` (بينفّذ `CheckoutContactPolicy`)، `backend/modules/identity/src/main/java/com/matbakhy/identity/api/EgyptianPhone.java`؛ بيتنادى من `QuoteService.create`.
  - API: API-011, API-012. DB: `phone_challenge` (الـ check هو `^\+201[0125][0-9]{8}$`)، V3.
  - Test: `identity/ContactIntegrationTest.java` (`changing_a_verified_phone_requires_verifying_the_new_number`, `invalid_numbers_are_rejected_without_sending`), `orders/QuoteIntegrationTest.java` (`contact_verification_kitchen_state_and_cutoff_are_enforced_by_server_time`), `identity/EgyptianPhoneTest.java`.
  - E2E: E2E-DISC-105.
- **BR-004 — الـ OTP بيتستخدم مرة واحدة، ومتخزّن Hash، وصالح 5 دقايق، وبحد أقصى 5 محاولات، وإعادة الإرسال كل 60 ثانية، وعدد الإرسال في الساعة محدود.**
  - Impl: `backend/modules/identity/src/main/java/com/matbakhy/identity/internal/PhoneVerificationService.java`, `PhoneVerificationProperties.java` (القيم الافتراضية في `backend/app/src/main/resources/application.yaml`: `code-ttl: 5m` و`max-attempts: 5` و`resend-interval: 60s`). DB: `phone_challenge.code_hmac` (Hash بمفتاح، مش الكود نفسه أبدًا).
  - Test: `identity/ContactIntegrationTest.java` (`correct_code_verifies_once_and_replay_fails`, `expired_code_fails_and_does_not_verify`, `challenge_locks_after_five_failed_attempts`, `resend_interval_and_hourly_limits_are_enforced`).
  - E2E: E2E-DISC-105.

## الـ Authorization وفصل الحسابات

- **BR-006 — كل مطبخ وأوردر وحالة وقايمة وعدّاد وتصدير بيتشيّك على نطاق الـ Object؛ الـ id اللي مينفعش تخمينه مش Authorization.**
  - Impl: تحديد النطاق على مستوى الـ Query في الـ Services (مثلًا `OrderLifecycleService.lock` و`PaymentService.lockForCustomer`) و`AccessPolicy` / `backend/app/src/main/java/com/matbakhy/app/security/ScopedAccessPolicy.java`. الـ Objects الغريبة بترجع 404.
  - DB: أعمدة المالك/المطبخ (`customer_order.customer_id` و`kitchen_id` و`kitchen.owner_id`).
  - Test: `identity/StaffAccessIntegrationTest.java` (`kitchen_scope_matrix`), `orders/OrderPlacementIntegrationTest.java` (`customers_see_only_their_own_orders_with_paging_and_events`, `another_customer_cannot_find_an_order_by_its_client_request_id`), `orders/OrderLifecycleIntegrationTest.java` (`cooks_act_only_on_their_kitchen_and_suspension_blocks_acceptance_but_not_fulfillment`).
  - E2E: E2E-ORDER-037, E2E-ORDER-120, E2E-COOK-054, E2E-DISC-106.
- **فصل الحسابات (العميل والطبّاخ والـ Staff نطاقات مختلفة لحساب واحد).** مغطّى بـ BR-006 وBR-007 وBR-008. العميل ميقدرش ينفّذ أوامر الطبّاخ، والطبّاخ ميقدرش يستخدم أوامر العميل؛ مفيش مسار تسجيل عام بيدّي موافقة طبّاخ أو صلاحية Staff؛ وتبديل الحسابات على الجهاز بيمسح بيانات الشخص السابق (BR-048).
  - Test: `orders/OrderLifecycleIntegrationTest.java` (`the_ordering_customer_cannot_run_cook_commands_and_the_cook_cannot_use_the_customer_cancel`), `identity/StaffAccessIntegrationTest.java` (`customer_cannot_grant_privileges_and_the_attempt_is_audited`, `role_fields_in_customer_requests_are_rejected`, `provider_role_claims_grant_no_staff_permission` في `IdentityIntegrationTest.java`).
  - E2E: E2E-OPS-058, CFG-005, CFG-006, E2E-DISC-004.
- **BR-007 — صلاحيات الـ Staff بتتحدد من ناحية السيرفر؛ الصلاحيات الملغاة بتوقف شغل؛ والـ MFA بيتبع سياسة الأدمن لكل فئة (D18).**
  - Impl: `backend/modules/identity/src/main/java/com/matbakhy/identity/internal/GrantService.java`, `backend/modules/identity/src/main/java/com/matbakhy/identity/api/Permission.java`, `AccountMapper` (الصلاحيات بتتقرا من جديد مع كل Request).
  - API: API-091..API-093. DB: `user_grant` بـ unique index على صلاحية نشطة واحدة لكل مستخدم وصلاحية (V4)، و`audit_event` للإضافة بس (V4).
  - Test: `identity/StaffAccessIntegrationTest.java` (`revoked_grant_stops_working_on_the_next_request`, `expired_grants_confer_nothing`, `grant_changes_require_recent_mfa`, `audit_is_append_only_and_redacted`).
  - E2E: E2E-OPS-055, E2E-OPS-059, E2E-OPS-102.
- **BR-043 — مفيش تصعيد ذاتي للصلاحيات؛ آخر أدمن ممكن استرجاع حسابه ميتشالش؛ والتغييرات عالية الخطورة محتاجة Step-up.**
  - Impl: `GrantService.grant` (التصعيد الذاتي بيترفض وبيتسجّل في الـ Audit) و`GrantService.revoke` (آخر حامل لـ `staff.grant` محمي).
  - Test: `identity/StaffAccessIntegrationTest.java` (`self_escalation_unknown_permissions_and_last_admin_are_refused`).
  - E2E: E2E-OPS-060, E2E-OPS-101, CFG-105.

## الـ MFA (القرار D18، مشار له من BR-007)

- أوضاع السياسة `DISABLED` و`OPTIONAL` و`REQUIRED` لكل فئة `CUSTOMER` و`KITCHEN` و`STAFF`؛ تفضيل المستخدم مبيخفّفش `REQUIRED` أبدًا؛ ولو صف السياسة مش موجود بيرجع للأمان على `REQUIRED`.
  - Impl: `backend/app/src/main/java/com/matbakhy/app/mfa/MfaPolicyService.java` (`resolve`), `backend/app/src/main/java/com/matbakhy/app/security/MfaEnforcementFilter.java`, `backend/app/src/main/java/com/matbakhy/app/security/StaffAssurance.java`, `backend/app/src/main/java/com/matbakhy/app/mfa/MfaAdminController.java`.
  - API: API-112..API-116. DB: `mfa_policy`, `user_mfa_preference`, `user_mfa_reset` (V31).
  - Test: `mfa/MfaPolicyResolveTest.java` (قواعد الأسبقية البحتة)، `mfa/MfaPolicyIntegrationTest.java`, `identity/MfaResetIntegrationTest.java`, `security/StaffAssuranceTest.java`.
  - E2E: CFG-101, CFG-011, CFG-012, CFG-014, CFG-016, CFG-018, CFG-019, CFG-033, CFG-104, E2E-OPS-111. مستند السياسة: `docs/security/mfa-policy.md`.

## صلاحيات الأدمن

مغطّاة بـ BR-007 وBR-043 اللي فوق وبجدول الصلاحية-للقدرة في [الأطراف والصلاحيات](actors-and-permissions-ar.md#خريطة-الصلاحية-للقدرة-الموظفين). الأدمن مش Superuser: الأدمن الأولي (bootstrap) بياخد `staff.grant` و`audit.read` بس (`GrantService.bootstrapAdministratorIfNone`). E2E: E2E-OPS-055.

## الموافقة على المطبخ

- **BR-008 — لازم موافقة على الطبّاخ قبل ما يبيع؛ والموافقة محتاجة Revision كاملة وسجل بالمراجع/السبب.**
  - Impl: `backend/modules/cooks/src/main/java/com/matbakhy/cooks/internal/CookApplicationService.java`, `backend/modules/cooks/src/main/java/com/matbakhy/cooks/internal/CookReviewService.java`.
  - API: API-042..API-044 (المتقدّم)، API-067..API-069 (المراجع، `cook.review` مع MFA حديث). DB: `cook_application`, `cook_application_revision` (Trigger يمنع التعديل)، `kitchen` (V5).
  - Test: `cooks/CookWorkflowIntegrationTest.java` (`incomplete_application_cannot_be_submitted_and_creates_no_revision`, `changes_requested_then_resubmitted_then_approved_preserves_every_revision`, `only_a_scoped_reviewer_with_recent_mfa_can_decide`, `a_reviewer_cannot_approve_their_own_application_and_the_attempt_is_audited`, `concurrent_approvals_create_exactly_one_kitchen`).
  - E2E: E2E-COOK-048, E2E-COOK-049, E2E-COOK-111, E2E-OPS-108, E2E-OPS-109.
- **BR-009 — الإيقاف المؤقت (Pause) بيوقف البيع الجديد؛ والتعليق (Suspension) بيوقف القبول كمان لكن بيسيب الأوردرات المقبولة قابلة للتنفيذ.**
  - Impl: `backend/modules/cooks/src/main/java/com/matbakhy/cooks/internal/KitchenService.java`, `KitchenDirectory.KitchenStatus` في `backend/modules/cooks/src/main/java/com/matbakhy/cooks/api/KitchenDirectory.java`.
  - API: API-070, API-065. DB: `kitchen.operating_state`.
  - Test: `cooks/CookWorkflowIntegrationTest.java` (`pause_stops_new_sales_suspension_stops_acceptance_and_accepted_orders_stay_fulfillable`, `only_scoped_staff_change_operating_state_and_never_for_their_own_kitchen`).
  - E2E: E2E-COOK-050, E2E-COOK-107, E2E-DISC-104.
- الكتالوج (BR-011 وBR-012): متطلبات النشر والـ Revisions في `backend/modules/catalog/src/main/java/com/matbakhy/catalog/internal/meal/MealService.java`؛ الـ DB هي `meal` و`meal_revision` (V7). Test: `catalog/MealIntegrationTest.java` (`every_publication_requirement_is_named_and_invalid_declarations_are_refused`, `edits_create_new_revisions_while_earlier_snapshots_stay_unchanged`, `archive_removes_the_meal_from_sale_without_touching_history`). E2E: E2E-COOK-101, E2E-COOK-103, E2E-COOK-104. التفاصيل في [إدارة المطبخ](kitchen-management-ar.md).

## تسجيل الأوردر

- **BR-018 — طبّاخ واحد وSlot واحد لكل سلة.** Impl: `QuoteService.terms` (`MIXED_KITCHENS_OR_SLOT`). Test: `orders/QuoteIntegrationTest.java` (`mixed_kitchens_unoffered_meals_and_invalid_baskets_are_rejected`). E2E: E2E-BASKET-010, E2E-BASKET-021.
- **BR-021 — التوصيل محتاج تغطية المنطقة وعنوان كامل خاص بالعميل؛ والاستلام بس لو متفعّل، وبرسوم توصيل صفر.** Impl: `QuoteService.terms`, `JdbcMealCatalog.deliveryFee`. DB: `kitchen_zone`, `address`. Test: `orders/QuoteIntegrationTest.java` (`unserviceable_delivery_and_disabled_pickup_are_denied_without_conversion`). E2E: E2E-DISC-007.
- **BR-022 — التسجيل (Placement) بيتحقق من العرض (Quote) وبيحجز كل المخزون وبيكتب الـ Snapshots والـ Idempotency والأحداث والـ Outbox وسجلات الدفع/الكاش، كله مرة واحدة (Atomically).**
  - Impl: `backend/modules/orders/src/main/java/com/matbakhy/orders/internal/OrderService.java` (`placeOnce`).
  - API: API-022. DB: `customer_order`, `order_item`, `stock_allocation`, `order_event`, `outbox_event`, `cash_obligation` (V11), `order_payment` (V24).
  - Test: `orders/OrderPlacementIntegrationTest.java` (`placement_writes_order_snapshots_reservation_event_outbox_and_cash_obligation_together`, `an_injected_failure_after_the_stock_update_rolls_everything_back_and_a_retry_completes_once`).
  - E2E: E2E-ORDER-021, E2E-ORDER-101, E2E-ORDER-102.
- **BR-023 — القيد الفريد الدائم (العميل + `clientRequestId`) بيمنع الأوردرات المكرّرة حتى بعد ما رد الـ Idempotency المتخزّن في الـ Cache ينتهي.** Impl: `OrderService.placeOnce` (advisory lock وfingerprint). DB: `customer_order_request_uq` (V11)، `request_fingerprint` (V29). Test: `OrderPlacementIntegrationTest` (`retries_after_timeout_and_after_idempotency_retention_create_exactly_one_order`, `a_reused_request_reference_with_a_changed_field_conflicts_even_after_the_cache_is_gone`)، `backend/app/src/test/java/com/matbakhy/orders/internal/OrderFingerprintTest.java`. E2E: E2E-ORDER-022, E2E-ORDER-024, E2E-ORDER-105, E2E-ORDER-106, E2E-ORDER-123.
- **BR-024 — نطاق الـ Idempotency هو الفاعل (actor) + الأمر (command) + المفتاح (key)؛ محتوى مختلف بنفس المفتاح بيعمل Conflict.** Impl: `backend/app/src/main/java/com/matbakhy/app/idempotency/JdbcIdempotentCommands.java`. DB: `idempotency_record` (V1). Test: `OrderPlacementIntegrationTest` (`reusing_a_key_or_request_reference_with_another_body_conflicts_and_leaves_the_order_unchanged`). E2E: E2E-ORDER-023.

## التسعير والعروض القديمة (Stale Quotes)

- **BR-019 — السيرفر هو اللي بيحسب المبالغ بالجنيه كأرقام صحيحة؛ أسعار العميل ورسومه وأدواره بتترفض.** Impl: `QuoteService`, `backend/app/src/main/java/com/matbakhy/app/config/StrictJsonConfiguration.java`. Test: `orders/QuoteIntegrationTest.java` (`the_server_computes_exact_totals_and_forged_amounts_are_rejected`, `tax_is_applied_with_half_up_rounding_from_the_policy_snapshot`)، `web/StrictJsonIntegrationTest.java`. E2E: E2E-BASKET-020, E2E-ORDER-113.
- **BR-020 — العرض (Quote) بيفضل صالح 5 دقايق، ومبيحجزش مخزون، وبياخد Snapshot للإصدارات، وبيعرض التكلفة الكاملة والسياسة.** Impl: `QuoteService.create`. API: API-021. DB: `quote`, `quote_item`, `commerce_policy` (V10؛ Trigger يمنع التعديل). Test: `QuoteIntegrationTest` (`quotes_hold_no_stock_and_cannot_exceed_remaining_portions`, `quotes_are_immutable`). E2E: E2E-BASKET-013, E2E-BASKET-029, E2E-ORDER-111.
- **BR-042 (مع RQ-004 وD13) — تغييرات الرسوم والنِّسَب والسياسة بتتعمل لها إصدارات وبتتسجّل في الـ Audit؛ العرض القديم بيترفض وبيتقبل من جديد؛ والأوردرات المسجّلة بتحتفظ بالـ Snapshots بتاعتها.**
  - Impl: `QuoteService.requireValid`, `backend/modules/orders/src/main/java/com/matbakhy/orders/internal/ConfigurationService.java`. API: API-090. DB: `config_change` (V19).
  - Test: `orders/PriceChangeIntegrationTest.java`, `orders/QuoteIntegrationTest.java` (`a_stale_quote_is_refused_with_a_recoverable_reason`), `orders/ConfigurationIntegrationTest.java` (`a_fee_change_keeps_placed_orders_invalidates_stale_quotes_and_applies_to_new_quotes`). نص المتطلب: `docs/product/rq-004-stale-quote.md`.
  - E2E: E2E-BASKET-014, E2E-BASKET-015, E2E-BASKET-022, E2E-ORDER-112, E2E-OPS-106, E2E-OPS-107.

## حجز السعة (Capacity)

- **BR-013 / BR-025 — السعة محدودة لكل وجبة-في-Slot ولكل طبّاخ-في-Slot؛ آخر وجبة مبتتباعش زيادة عن اللازم أبدًا.** Impl: `backend/modules/availability/src/main/java/com/matbakhy/availability/internal/JdbcSlotInventory.java`, `SlotService`. DB: `cook_slot`, `meal_slot` (V8)، `stock_allocation` (V11). Test: `availability/SlotIntegrationTest.java` (`the_last_portion_is_bounded_by_both_the_meal_and_the_slot_aggregate`, `capacity_cannot_be_reduced_below_what_orders_already_hold`)، `OrderPlacementIntegrationTest` (`twenty_customers_racing_for_the_last_portion_produce_exactly_one_order`, `a_two_meal_order_is_refused_without_partial_reservation_once_the_kitchen_slot_total_is_used_up`). E2E: E2E-ORDER-115, E2E-BASKET-016, E2E-BASKET-017, E2E-BASKET-018, E2E-ORDER-114, E2E-COOK-106.
- **BR-014 / BR-015 — التخزين بـ UTC، والجدولة بتوقيت Africa/Cairo، والـ Cutoff قبل البداية.** Impl: `CairoTime`, `SlotService.create`. Test: `availability/SlotIntegrationTest.java` (`ordinary_slots_store_utc_instants_for_cairo_wall_clock_times`, `a_local_time_skipped_by_the_spring_change_is_rejected`, `server_time_reaching_cutoff_closes_ordering_and_capacity_edits`). E2E: E2E-COOK-105, E2E-BASKET-023.

## الإلغاء والانتهاء والإفراج

- **BR-026 — انتقالات قانونية بس ومحدّدة بالفاعل ومعاها الإصدار المتوقع؛ الأوردرات النهائية (Terminal) مبتتعدّلش.** Impl: `backend/modules/orders/src/main/java/com/matbakhy/orders/internal/OrderLifecycleService.java` (`guard` و`require` و`transition`). DB: Trigger `customer_order_terminal_guard` (V12)، و`order_event` للإضافة بس. Test: `orders/OrderLifecycleIntegrationTest.java` (`terminal_orders_and_stale_versions_are_rejected_without_changing_history`). E2E: E2E-ORDER-036, E2E-ORDER-121.
- **BR-027 — موعد القبول النهائي هو `min(created + window, slot cutoff)` مع حد أدنى للوقت المتبقي؛ والانتهاء بيستخدم وقت السيرفر.** Impl: `QuoteService.terms`, `backend/modules/orders/src/main/java/com/matbakhy/orders/internal/OrderExpiryJob.java`. Test: `OrderLifecycleIntegrationTest` (`the_expiry_job_expires_overdue_orders_once_by_server_time`, `acceptance_is_allowed_one_millisecond_before_the_deadline_and_refused_exactly_at_it`). E2E: E2E-ORDER-029.
- **BR-028 — العميل بيلغي الأوردرات المعلّقة مباشرة؛ بعد القبول يقدر يقدّم طلب إلغاء بس؛ وإلغاء الأوردر في التحضير/الجاهز محتاج العمليات (ops) مع سبب.** Impl: `OrderLifecycleService.customerCancel`, `opsExceptionCancel`, `backend/modules/support/src/main/java/com/matbakhy/support/internal/SupportService.java` (طلب الإلغاء). API: API-027, API-028, API-076. Test: `OrderLifecycleIntegrationTest` (`customers_cancel_pending_orders_directly_but_not_accepted_ones`, `prepared_orders_cancelled_by_ops_keep_consumed_stock_and_never_release_twice`)، `support/cases/SupportIntegrationTest.java` (`a_cancellation_request_never_changes_the_order_and_is_resolved_by_an_audited_exception_cancel`). E2E: E2E-ORDER-027, E2E-ORDER-035, E2E-ORDER-109, E2E-ORDER-110.
- **BR-029 — الرفض والانتهاء والإلغاء قبل التحضير بيفرجوا عن المخزون مرة واحدة بالظبط؛ بعد التحضير السعة بتفضل مستهلَكة.** Impl: `OrderLifecycleService.release` و`move`. DB: `stock_allocation.state` (`RESERVED` و`ALLOCATED` و`CONSUMED` و`RELEASED`). Test: `OrderLifecycleIntegrationTest` (`accept_cancel_and_expire_racing_produce_one_legal_outcome_and_at_most_one_release`). E2E: E2E-ORDER-026, E2E-ORDER-028.

## الاستلام والتوصيل

- **BR-030 — الاستلام بيكتمل من READY بس؛ التوصيل بيروح من READY لـ OUT_FOR_DELIVERY الأول؛ والأوامر الغلط بالنسبة للوضع بتترفض.** Impl: `OrderLifecycleService.cook` (`dispatch` و`handover` و`requireHandoverState`). API: API-059, API-060. Test: `OrderLifecycleIntegrationTest` (`pickup_order_moves_through_every_stage_with_stock_and_events_in_step`, `wrong_fulfillment_paths_are_denied_and_the_correct_path_keeps_history`). E2E: E2E-ORDER-031, E2E-ORDER-032, E2E-ORDER-034.
- **BR-031 — الإكمال محتاج كود تسليم للعميل يُستخدم مرة واحدة بعدد محاولات محدود؛ والتجاوز من العمليات (ops override) محتاج MFA حديث وسبب ودليل.** Impl: `OrderLifecycleService.handover`, `opsHandoverOverride`, `backend/modules/orders/src/main/java/com/matbakhy/orders/internal/HandoverCodes.java`, `HandoverProperties.java`. API: API-060, API-077. DB: `handover_challenge`, `handover_attempt` (V12). Test: `OrderLifecycleIntegrationTest` (`wrong_codes_are_bounded_valid_codes_work_once_and_locked_challenges_need_an_audited_override`, `a_valid_code_completes_once_and_cannot_be_replayed`, `handover_attempts_are_rate_limited_per_actor`). E2E: E2E-ORDER-030, E2E-ORDER-031, E2E-ORDER-033, E2E-ORDER-108.

## العربون (D16؛ من غير رقم BR)

- مصدر القاعدة: `docs/product/09-deposit-payment-states.md`، السيناريوهات TC-159 وTC-161 وTC-162.
  - Impl: `backend/modules/orders/src/main/java/com/matbakhy/orders/internal/PaymentService.java`, `backend/modules/orders/src/main/java/com/matbakhy/orders/web/PaymentController.java`.
  - API: API-100..API-109. DB: `kitchen_payment_profile`, `order_payment`, `payment_event` (V24)، إضافات المطابقة (V25)، فهرس حالة الدعم PAYMENT (V28).
  - Test: `payments/DepositPaymentIntegrationTest.java` (`deposit_terms_are_quoted_and_snapshotted_and_cod_follows_the_kitchen_limit`, `a_transfer_report_is_not_payment_and_preparation_waits_for_the_kitchen_report`)، `payments/PaymentReconciliationIntegrationTest.java` (`the_kitchen_reports_not_received_or_a_wrong_amount_and_staff_can_never_mark_money_received`, `a_dispute_or_refund_obligation_opens_one_payment_support_case_for_the_order`).
  - E2E: E2E-FIN-039 to E2E-FIN-042, E2E-FIN-101, E2E-FIN-103 to E2E-FIN-109, E2E-FIN-115, E2E-COOK-108.

## مطابقة الكاش والاسترداد وعمولة الصفر

- **BR-032 — إكمال الأوردر وتحصيل الفلوس مستقلين؛ تقرير الطبّاخ وتحقق المالية حدثين منفصلين.** Impl: `backend/modules/finance/src/main/java/com/matbakhy/finance/internal/CashService.java`. API: API-063, API-083. DB: `cash_obligation` (V11)، `cash_report` مع `cash_report_separation_ck` (V13). Test: `finance/CashAndLedgerIntegrationTest.java` (`a_completed_order_without_a_report_stays_due_with_no_invented_receipt`, `exact_report_then_independent_verification`, `short_or_zero_reports_are_exceptions_and_need_a_reasoned_decision`, `reports_need_a_completed_own_order_and_reporters_cannot_verify_themselves`). E2E: E2E-FIN-038, E2E-FIN-043, E2E-FIN-102, E2E-FIN-114.
- **BR-033 / BR-034 — Snapshot للعمولة، ودفتر (Ledger) متوازن لا يتعدّل بيتسجّل مرة واحدة لكل حدث مصدر؛ الكاش اللي بيحصّله الطبّاخ بيعمل مستحقات (Receivable)، مش كاش للمنصة. تحت D10 النسبة 0.** Impl: `backend/modules/finance/src/main/java/com/matbakhy/finance/internal/LedgerService.java`, `LedgerPostingJob.java`. DB: `ledger_group`, `ledger_entry` (V13)، فحص الإطلاق في V23. Test: `finance/CashAndLedgerIntegrationTest.java` (`commission_posts_once_per_completion_event_as_a_balanced_group_from_the_snapshot`, `two_posting_instances_racing_post_each_completion_exactly_once_and_balanced`)، `finance/ZeroCommissionIntegrationTest.java`, `finance/FinanceOperationsIntegrationTest.java` (`cook_collected_cash_creates_only_a_commission_receivable`). E2E: E2E-FIN-045, E2E-FIN-046, E2E-FIN-110, E2E-FIN-111, E2E-OPS-107.
- **BR-035 / BR-036 — الاسترداد الكامل محدود بالتحصيل المتحقَّق منه؛ بنود التسوية فريدة، والدفعات المراجَعة لا تتعدّل.** Impl: `backend/modules/finance/src/main/java/com/matbakhy/finance/internal/FinanceOpsService.java`. API: API-084, API-085, API-087, API-088. DB: `refund`, `settlement_batch`, `settlement_line` (V18). Test: `finance/FinanceOperationsIntegrationTest.java` (`refunds_are_full_bounded_by_verified_collection_and_reverse_commission_once`, `settlement_lines_are_unique_and_reviewed_batches_immutable`). E2E: E2E-FIN-044, E2E-FIN-110.

## قواعد تانية تستاهل تعرفها

| القاعدة | المعنى في سطر | Impl / test / E2E |
|---|---|---|
| BR-037, BR-038 | الأوردرات والـ Inbox بتنجو من فشل المزوّد؛ Outbox معاملاتي مع Leases ومنع التكرار | `backend/modules/notifications/src/main/java/com/matbakhy/notifications/internal/OutboxDispatcher.java`; `notifications/NotificationIntegrationTest.java` (`two_workers_and_duplicate_events_produce_one_inbox_record_and_one_send`); E2E-ORDER-117 |
| BR-039 | تقييم واحد بس، من صاحب أوردر COMPLETED بس، من 1 لـ 5 | `backend/modules/orders/src/main/java/com/matbakhy/orders/internal/ReviewService.java`; `orders/ReviewsAndFavoritesIntegrationTest.java`; E2E-DISC-110 |
| BR-041 | حالات الدعم مربوطة بالأوردر؛ ملاحظات الـ Staff الخاصة مبتوصلش للعميل ولا للطبّاخ | `SupportService`; `support/cases/SupportIntegrationTest.java` (`staff_notes_stay_private_and_safety_cases_are_escalated_first`); E2E-OPS-057, E2E-ORDER-118 |
| BR-055 | الحذف بيسحب الوصول وبيمسح البيانات الشخصية مع الحفاظ على سلامة البيانات المالية | `backend/modules/identity/src/main/java/com/matbakhy/identity/internal/AccountErasureService.java`; `privacy/AccountErasureIntegrationTest.java`; E2E-DISC-114, E2E-COOK-110 |
| BR-056 | الـ Logs والـ Metrics مفيهاش Tokens ولا OTP ولا بيانات اتصال ولا عنوان | `privacy/LogRedactionIntegrationTest.java`; CFG-040 |
| BR-060 | البيئات منفصلة؛ الـ Fake Adapters مينفعش تشتغل في الـ Production | `backend/app/src/main/java/com/matbakhy/app/config/EnvironmentGuard.java`; `config/EnvironmentGuardTest.java`; CFG-045 |

## القيود المعروفة وفجوات التوثيق

- كل قاعدة في `planning/rules.json` حالتها `PROPOSED_DEFAULT`؛ ده بيعكس حزمة التخطيط. قرارات المالك اللاحقة (D10، D13، D14، D16، D17، D18) هي الحاسمة لما تتعارض.
- BR-054 (الـ Rate Limits على الدخول والـ OTP والعرض والأوردر والرفع والبحث) منفّذة جزئيًا بس؛ شوف القيود في [الأطراف والصلاحيات](actors-and-permissions-ar.md). حالة المالك CFG-039 بتسجّل ده.
- BR-033 وBR-034 بيوصفوا آليات عمولة خامدة تحت سياسة عمولة الصفر.
- قواعد سلوك العميل (BR-044 to BR-052) بيتم التحقق منها باختبارات الـ Frontend والـ Native، واللي الملخص ده مبيربطهاش. شوف [تشغيل الاختبارات](../05-developer-workflow/test-execution-ar.md).
