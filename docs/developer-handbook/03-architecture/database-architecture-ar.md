# معمارية قاعدة البيانات

> **Owner role:** Backend architecture
> **Status:** IMPLEMENTED — 31 Flyway migration بتتحرك للأمام بس (forward-only) بتعرّف الـ Schema كلها؛ مفيش ORM، والـ SQL مكتوب بإيد جوه الـ Services
> **Language / اللغة:** [English](database-architecture.md) · العربية

الـ Database هي **PostgreSQL 16.15** (صورة مثبّتة بالـ Digest في `backend/pom.xml` وملفات الـ Compose). الـ Schema مملوكة لـ Flyway SQL migrations في `backend/app/src/main/resources/db/migration` (من `V1` لحد `V31`، بالإضافة للـ Callback اسمه `afterMigrate.sql`). الوصول بـ JDBC عادي؛ شوف [معمارية الـ Backend](backend-architecture-ar.md#الطبقات-والوصول-للبيانات). مخطط العلاقات بين الكيانات: [علاقات قاعدة البيانات](diagrams-ar.md#12-علاقات-قاعدة-البيانات). مستند التصميم الأقدم هو `docs/engineering/02-data-model.md` (للسياق بس؛ الـ SQL اللي تحت هو الحقيقة).

## الاصطلاحات (باينة في كل Migration)

* المعرّفات (Identifiers) من نوع `uuid` بيعيّنها التطبيق (`UUID.randomUUID()`)؛ الـ Sequence الوحيدة هي `order_public_number_seq` (أرقام الأوردرات العامة `MB-<n>`، بتبدأ من 100001).
* الـ Instants من نوع `timestamptz` (UTC). والتواريخ المحلية بتتخزّن جنبها لما البيزنس يحتاجها (`cook_slot.local_date`، والـ Timezone مثبّتة على `Africa/Cairo` بقيد Check).
* الفلوس `bigint` بالقروش (piastres) في أعمدة `*_minor`؛ وأعمدة العملة مقيّدة بـ `EGP`. والإجماليات متراجعة بقيود: `total_minor = subtotal_minor + delivery_fee_minor + tax_minor` على `quote` و`customer_order`؛ و`line_total_minor = unit_price_minor * quantity` على `order_item`؛ و`deposit_minor + remainder_minor = total_minor` على `order_payment`.
* الـ State machines هي أعمدة `text` بقيد `CHECK (state IN (...))`، مش Enums في الـ Database. إضافة حالة معناها Migration بتمسح قيد الـ Check وتضيفه تاني (V25 وV28 عملوا كده).
* الصفوف اللي ممنوع تتعدّل ليها Triggers من نوع `BEFORE UPDATE OR DELETE` بترمي Exception؛ ونفس الجداول كمان بتفقد صلاحية `UPDATE`/`DELETE` عن دور وقت التشغيل (شوف تحت).
* الـ Optimistic concurrency: عمود `version bigint` على الـ Aggregates القابلة للتعديل، بيتقارن مع `expectedVersion` اللي جاي من الـ Client.
* البيانات الشخصية موجودة في كام عمود معروف ممكن Job المسح يفضّيهم (V21 وV27).

## الـ Migrations

| الـ Migration | بتضيف إيه | الـ Story / القرار |
|---|---|---|
| `V1__baseline_identity_and_idempotency.sql` | `app_user` (الـ issuer+subject Unique، والتليفون، والحالة، واللغة)، `idempotency_record` | MB-006 |
| `V2__web_sessions.sql` | جداول Spring Session JDBC اللي هي `SPRING_SESSION` و`SPRING_SESSION_ATTRIBUTES` | MB-009 |
| `V3__zones_addresses_phone_verification.sql` | `service_zone` (رسوم التوصيل لكل منطقة)، `address`، `phone_challenge`، `deletion_request` | MB-010 |
| `V4__grants_and_audit.sql` | `user_grant` (صلاحيات الموظفين)، و`audit_event` اللي بيتضاف عليه بس | MB-011 |
| `V5__cook_applications_and_kitchens.sql` | `cook_application`، `cook_application_revision`، `kitchen`، `kitchen_zone` | MB-012 |
| `V6__media_assets.sql` | `media_asset` (صاحب القرار في حالة الرفع) | MB-013 |
| `V7__meals.sql` | `meal`، و`meal_revision` اللي بيتضاف عليه بس، و`meal_media` | MB-014 |
| `V8__slots.sql` | `cook_slot`، `meal_slot` مع العدّادات | MB-015 |
| `V9__discovery.sql` | تصنيف الوجبة وتطبيع (Normalisation) البحث | MB-016 |
| `V10__quotes.sql` | `commerce_policy`، `quote`، `quote_item` | MB-017 |
| `V11__orders.sql` | `customer_order`، `order_item`، `stock_allocation`، و`order_event` اللي بيتضاف عليه بس، `outbox_event`، `cash_obligation`، `order_public_number_seq` | MB-018 |
| `V12__order_lifecycle.sql` | Trigger حماية الحالة النهائية، `handover_challenge`، `handover_attempt` | MB-019 |
| `V13__cash_and_ledger.sql` | `cash_report`، `ledger_group`، `ledger_entry` مع Trigger الاتزان | MB-020 |
| `V14__notifications.sql` | Leases الـ Outbox، `notification`، `notification_delivery`، `device_registration` | MB-021 |
| `V15__cook_workspace.sql` | `kitchen.paused_by` | MB-022 |
| `V16__moderation.sql` | `meal.moderation_state` | MB-023 |
| `V17__support.sql` | `support_case`، `case_message` | MB-024 |
| `V18__finance_operations.sql` | `refund`، `settlement_batch`، `settlement_line`، ونوع الـ Ledger اسمه `COMMISSION_REVERSAL` | MB-025 |
| `V19__configuration.sql` | `feature_flag` (مفتاح الإيقاف الطارئ `ordering-enabled`)، و`config_change` اللي بيتضاف عليه بس | MB-026 |
| `V20__favorites_and_reviews.sql` | `favorite`، `review`، `review_report` | MB-028 |
| `V21__personal_data_erasure.sql` | علامات المسح على العناوين وما يتصل بيها | MB-039 |
| `V22__outbox_request_id.sql` | `outbox_event.request_id` | MB-039 |
| `V23__launch_commission.sql` | Check: السياسات الحيّة لازم عمولتها صفر | قرار المالك D10 |
| `V24__deposit_payments.sql` | `kitchen_payment_profile`، `order_payment`، و`payment_event` اللي بيتضاف عليه بس، وشروط الدفع على `quote` | MB-051, D16 |
| `V25__payment_reconciliation.sql` | حالات التسوية (Reconciliation)، `payment_event.note` | MB-052 |
| `V26__organic_activity.sql` | `activity_daily` (زائر متعمل له Hash، لكل يوم) | MB-054, D17 |
| `V27__erasure_on_append_only_history.sql` | Triggers بتسمح بتعديل المسح الوحيد على `case_message` / `payment_event` | MB-039 |
| `V28__payment_support_case.sql` | تصنيف حالة الدعم `PAYMENT`، وحالة دفع مفتوحة واحدة لكل أوردر | MB-052 |
| `V29__order_request_fingerprint.sql` | `customer_order.request_fingerprint` | QA-002 |
| `V30__media_cleanup.sql` | `media_cleanup` (طابور حذف الـ Objects الدائم) | CR2-001 |
| `V31__mfa_policy.sql` | `mfa_policy`، `user_mfa_preference`، `user_mfa_reset`، وكودين صلاحيات جداد | D18 |

قواعد الـ Migrations الجديدة: عمرك ما تعدّل Migration اتطبّقت (ضيف `V32` وما بعدها)؛ وابدأ أي Migration ممكن تكسر الـ API السابق بتعليق `-- rollback-compat:`. السكريبت `scripts/check_migration_compat.py` بيفرض التعليق ده للـ Migrations اللي بعد V28 (`BASELINE = 28`)؛ شوف [CI/CD](../05-developer-workflow/ci-cd-ar.md). البيانات التجريبية للتطوير هي Migrations قابلة للتكرار في `backend/app/src/main/resources/db/devdata` (`R__dev_commerce_policy.sql`، `R__dev_service_zones.sql`)، بتتحمّل بالـ Profile اسمه `dev` بس، اللي `EnvironmentGuard` بيمنعه برّه LOCAL/CI/PREVIEW.

## الكيانات الأساسية والعلاقات

**الهوية والوصول.** الجدول `app_user` مفتاحه `(issuer, subject)` جاي من مزوّد الهوية؛ وفيه البروفايل والتليفون المتحقق منه و`status` (`ACTIVE`، `SUSPENDED`، `DELETION_REQUESTED`، `DELETED`). مفيش جدول أدوار للعملاء أو الطبّاخين: الطبّاخ هو `app_user` بيملك `kitchen` (`kitchen.owner_id UNIQUE`). وصلاحية الموظفين هي صفوف `user_grant` (كود الصلاحية، والسبب، وتاريخ انتهاء اختياري، وأعمدة الإلغاء؛ وفيه Partial unique index بيسمح بـ Grant فعّال واحد لكل مستخدم وصلاحية). و`mfa_policy` فيه صف واحد لكل فئة (`CUSTOMER`، `KITCHEN`، `STAFF`).

**جانب العرض (Supply).** `cook_application` (+ `cook_application_revision` اللي مبيتعدلش) بتوصّل لـ `kitchen` (ومعاها `kitchen_zone` للمناطق اللي بيخدمها، والنسخة الموافق عليها كـ Foreign key). والـ `meal` بتتبع مطبخ؛ وكل تعديل في المحتوى أو السعر بيضيف `meal_revision(meal_id, revision, snapshot, price_minor)` مبيتعدلش؛ والوجبات بتتأرشف ومبتتمسحش أبدًا (Trigger). و`kitchen_payment_profile` (صف واحد لكل مطبخ) فيه نسبة العربون وحد الـ COD والمستلم الموافق عليه من الموظفين.

**السعة (Capacity).** `cook_slot` (المطبخ، البداية/النهاية، وقت التجهيز المسبق، `max_portions`، العدّادات) و`meal_slot` (السعة لكل وجبة لكل Slot، العدّادات). العدّادات `reserved` و`allocated` و`consumed` بتتحرك مع صفوف `stock_allocation`: التسجيل بيزوّد `reserved`، والقبول بينقل لـ `allocated`، والتحضير بينقل لـ `consumed`، والتحرير بينقّص. القيود `reserved + allocated + consumed <= max_portions` (في الـ Slot) و`<= capacity` (في meal slot) بتخلّي البيع الزيادة عن المتاح مستحيل حتى لو كود التطبيق غلط. وTrigger بيمنع meal slot لوجبة بتتبع مطبخ مختلف. سكريبت التسوية `infra/db/reconcile.sql` بيتأكد إن العدّادات بتساوي مجموع الـ Allocations؛ وبيشتغل بعد كل استرجاع (Restore) من Backup.

**Snapshots التسعير.** `commerce_policy` بنسخ (نسبة الضريبة، وصلاحية الـ Quote، ونافذة القبول، والحد الأقصى لكل بند، وإصدارات الشروط وسياسة الإلغاء؛ وصف `ACTIVE` واحد بالظبط عن طريق Unique partial index). والـ `quote` بتخزّن نسخة السياسة والإجماليات اللي اتحسبت بيها، بالإضافة لشروط الدفع؛ ومبتحجزش مخزون وبتنتهي. و`customer_order` بتنسخ الإجماليات و`policy_version` و`commission_bps_snapshot` وإصدارات الشروط و`address_snapshot` / `contact_snapshot` بصيغة JSON؛ و`order_item` بتنسخ الكمية وسعر الوحدة ونسخة الوجبة كلها كـ `meal_snapshot jsonb`، وليها Foreign key على `(meal_id, revision)`. فأي تعديل لاحق في السعر أو العنوان عمره ما بيغيّر أوردر اتسجّل. و`customer_order.quote_id` هو `UNIQUE`، فالـ Quote ممكن تتستخدم مرة واحدة.

**الأوردرات والتاريخ.** `customer_order.state` ليها عشر قيم (شوف [حالات الأوردر](diagrams-ar.md#9-حالات-تنفيذ-الأوردر)). القيود: `(customer_id, client_request_id)` Unique؛ وأوردر التوصيل لازم يبقى فيه Snapshot للعنوان، وأوردر الاستلام لازم رسوم التوصيل فيه صفر. كل انتقال بيضيف `order_event` (`sequence` فريد لكل أوردر، ونوع الفاعل والـ id، والسبب). `order_event` و`order_item` مينفعش يتعدّلوا أو يتمسحوا (Trigger)، والأوردرات مينفعش تتمسح، والأوردر في حالة نهائية مينفعش يتعدل (Trigger بتاع V12)، مع استثناء خصوصية ضيق واحد: V21 (`backend/app/src/main/resources/db/migration/V21__personal_data_erasure.sql`) استبدل `customer_order_terminal_guard` بحيث، مرة واحدة لكل أوردر، Snapshots الاتصال والعنوان ممكن تبقى `{"erased": true}`، و`fulfillment_note` يتفضّى و`personal_data_erased_at` يتحدد، وكل عمود تاني لازم يفضل زي ما هو. أي تعديل تاني بيرمي `terminal order ... is immutable`. و`handover_challenge` بيخزّن Hash بمفتاح (keyed hash) بس مع نص مشفّر بـ AES-GCM لكود الاستلام/التوصيل؛ وصفوف `handover_attempt` بتدعم الـ Rate limiting.

**الدفع وسجلات الفلوس.**

| الجدول | الغرض | عدم القابلية للتعديل |
|---|---|---|
| `order_payment` | صف واحد لكل أوردر: الطريقة `COD` أو `DEPOSIT`، العربون/المتبقي، Snapshot المستلم، الحالة | الشروط مبتتعدلش بـ Trigger؛ `state` و`version` و`updated_at` بس هم اللي بيتغيّروا |
| `payment_event` | Timeline لمين بلّغ بإيه (العميل، الطبّاخ، الموظف، السيستم)، والمبلغ، والمرجع | بيتضاف عليه بس (استثناء مسح واحد، V27) |
| `cash_obligation` | الكاش المستحق وقت التسليم (`total - deposit`)، الحالات `DUE` و`REPORTED` و`VERIFIED` و`DISPUTED` و`NOT_DUE` | بنسخ (Versioned) |
| `cash_report` | بلاغ المطبخ بالكاش اللي اتحصّل؛ المتحقق لازم يختلف عن المبلّغ (`cash_report_separation_ck`) | بنسخ (Versioned) |
| `refund` | مسار التزام الاسترداد `REQUESTED` و`APPROVED` و`PAID` و`VERIFIED` و`REJECTED` | بنسخ (Versioned) |
| `ledger_group`, `ledger_entry` | قيود عمولة بالقيد المزدوج (الحسابات `COOK_COMMISSION_RECEIVABLE` و`PLATFORM_COMMISSION_REVENUE`)، فريدة لكل حدث مصدر؛ وTrigger قيد مؤجّل (deferred) بيطلب إن المدين = الدائن | بيتضاف عليه بس |
| `settlement_batch`, `settlement_line` | تحويل العمولة بعد المراجعة لكل مطبخ وفترة؛ وLedger group ممكن يكون في دفعة فعّالة واحدة بس | البنود محمية؛ ومفيش حاجة بتتمسح |

مع قاعدة العمولة الصفرية (قيد Check في V23 بالإضافة لـ Validation في الـ API)، مبالغ الـ Ledger الحيّة صفر والتسوية خامدة؛ والهياكل باقية لقرار مستقبلي. وحالات `order_payment` هي أقوال الأطراف، وعمرها ما تكون "متحقق منها بنكيًا".

**الـ Audit والإعدادات.** `audit_event` (الفاعل، والإجراء، والصلاحية، والكائن، والنتيجة `ALLOWED`/`DENIED`، والـ Request ID، وتفاصيل JSON معمول لها Redaction) بيتضاف عليه بس بما في ذلك Trigger لـ `TRUNCATE`. و`config_change` بيسجّل كل تغيير في إعدادات التشغيل بالسبب والفاعل. و`feature_flag` فيه الـ Flags زي مفتاح الإيقاف الطارئ العام `ordering-enabled`. و`idempotency_record` بيخزّن نتائج الأوامر بقيد `expires_at >= created_at + 24h` (وقت حد أدنى للاحتفاظ؛ مفيش حاجة بتنتهي أو بتتمسح لحد دلوقتي).

**الإشعارات.** `outbox_event` (PENDING وSENT وFAILED، مع أعمدة الـ Lease و`request_id`) بيتكتب في نفس الـ Transaction بتاعة تغيير الـ Domain. و`notification` هو صف الـ Inbox لكل مستلم (`(recipient_id, dedup_key)` فريد)؛ و`notification_delivery` صف لكل قناة/هدف (`EMAIL`، `PUSH`) مع المحاولات و`DEAD` كـ Dead letter ظاهر؛ و`device_registration` بيخزّن Push tokens متعمل لها Hash ومشفّرة. شوف [التكامل والأحداث](integration-and-events-ar.md).

**الدعم والتقييمات والميديا والنشاط.** `support_case` (مرتبطة بأوردر، مع مرجع `CS-XXXXXXXX`، وتصنيفات منها `PAYMENT`) و`case_message` (رسائل عامة وملاحظات الموظفين)؛ و`review` (واحد لكل أوردر، `order_id` فريد) و`review_report`؛ و`favorite`؛ و`media_asset` و`meal_media`؛ و`media_cleanup` (صفوف بتتعمل في نفس الـ Transaction اللي بتشيل الإشارة لـ Object)؛ و`activity_daily` (اليوم، النوع، الموضوع، المطبخ، HMAC للزائر؛ والـ Primary key بيخلّي العدّ بيحسب زوّار مميزين لكل يوم).

## حدود الـ Transactions

* نداء Service واحد هو Transaction واحدة (`@Transactional`)؛ ومفيش Distributed transaction. الـ Transaction بتاعة تسجيل الأوردر بتلمس `customer_order` و`order_item` و`stock_allocation` و`cook_slot` و`meal_slot` و`order_event` و`outbox_event` و`order_payment` و`cash_obligation` و`idempotency_record` مع بعض (الرسم: [تسجيل الأوردر](diagrams-ar.md#8-تسجيل-الأوردر-sequence)).
* ترتيب الـ Locks ثابت: صفوف السياسة/التسعير (Share) في إعادة التحقق من الـ Quote، وبعدها `cook_slot`، وبعدها `meal_slot` بترتيب الـ id؛ وصف الأوردر بيتقفل الأول في كل Command من دورة الحياة. ده بيمنع الـ Deadlocks بين التسجيلات والقبول والانتهاء المتزامنة.
* التأثيرات الخارجية (الإيميل، الـ Push) عمرها ما بتحصل جوه الـ Transactions دي: بتتحط في طابور عن طريق `outbox_event` وبتتسلّم بالـ Workers.

## دور الـ Database وقت التشغيل

البيئات اللي متعمل لها Deployment بتشغّل Flyway بدور المالك والـ API بدور `matbakhy_app` (`deploy/web-preview/postgres/20-runtime-role.sh`، `DATABASE_RUNTIME_ROLE`). الملف `afterMigrate.sql` بيشتغل بعد كل Migrate: بيدّي `SELECT/INSERT/UPDATE/DELETE` على كل الجداول، وبيسحب الـ DDL، وبيسحب `UPDATE, DELETE` من الجداول اللي بيتضاف عليها بس بالكامل (`audit_event`، `config_change`، `ledger_entry`، `ledger_group`، `meal_revision`، `order_event`، `order_item`) وبيسحب `DELETE` من الجداول المحمية (`customer_order`، `cook_application_revision`، `settlement_line`، `order_payment`، `case_message`، `payment_event`). ومن غير `DATABASE_RUNTIME_ROLE` (التطوير المحلي، الاختبارات) الـ Callback مبيعملش حاجة. الاختبار: `backend/app/src/test/java/com/matbakhy/app/db/RuntimeRoleIntegrationTest.java`.

## الـ Indexes اللي تستاهل تعرفها

المسارات الساخنة ليها Indexes مركّبة، مثلًا `customer_order_customer_idx (customer_id, created_at DESC, id DESC)` لقايمة العميل مع الـ Cursor pagination، و`customer_order_kitchen_idx (kitchen_id, state, created_at, id)` لطابور الطبّاخ، و`customer_order_accept_by_idx (state, accept_by)` لـ Job الانتهاء، و`outbox_event_pending_idx (status, available_at)`، و`notification_delivery_due_idx (status, next_attempt_at)`، و`audit_event_*` بالوقت والفاعل والكائن، وPartial unique indexes زي `cook_slot_identity_uq (kitchen_id, starts_at) WHERE state <> 'CANCELLED'` و`commerce_policy_one_active_uq` و`user_grant_active_uk` و`support_case_open_payment_uq`.

## النسخ الاحتياطي والاسترجاع والتسوية

السكريبت `scripts/db-backup.sh` بيكتب `pg_dump` بصيغة Custom مع Checksum بـ SHA-256 وManifest بأعداد الصفوف وبيرفض الكتابة جوه Git working tree؛ و`scripts/db-restore.sh` بيسترجع وبيشغّل `infra/db/reconcile.sql`، والـ Backup بيتقبل بس لما كل أعداد التسوية تكون صفر. المعاينة بتغلّف دول في `deploy/web-preview/deploy.sh backup` و`restore-db`، وبتاخد Backup قبل كل Deployment. الاختبارات: `backend/app/src/test/java/com/matbakhy/app/reliability/BackupRestoreIntegrationTest.java`. تفاصيل التشغيل: [البنية التحتية والـ Deployment](infrastructure-and-deployment-ar.md#النسخ-الاحتياطي-والاسترجاع-والـ-rollback).

## القيود المعروفة وفجوات التوثيق

* مفيش توثيق على مستوى الجدول غير تعليقات الـ Migrations و`docs/engineering/02-data-model.md`؛ والصفحة دي مبتسردش كل عمود. اقرا الـ Migration بتاعة الجدول اللي هتلمسه.
* `V23` بيضيف قيد العمولة بتاعه كـ `NOT VALID`، فالصفوف اللي اتعملت قبله مبتتراجعش تاني (تعليق في الملف).
* الاحتفاظ (Retention) بـ `activity_daily` والجداول التانية القريبة من البيانات الشخصية لسه مش متقرر (قرار المالك O06)؛ ومفيش Job حذف لـ `activity_daily`.
* سكريبت التسوية والحمايات المبنية على الـ Triggers متختبرة على PostgreSQL عن طريق Testcontainers؛ وقاعدة بيانات المعاينة الفعلية ماتفحصتش للصفحة دي (NOT VERIFIED إن صلاحيات الدور المنشور مطابقة).
