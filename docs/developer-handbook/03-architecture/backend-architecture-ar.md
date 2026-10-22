# معمارية الـ Backend

> **Owner role:** Backend architecture
> **Status:** IMPLEMENTED — Modular Monolith زي ما هو موصوف؛ وأي Provider adapters مخطّط لها متوضّحة في النص
> **Language / اللغة:** [English](backend-architecture.md) · العربية

الـ Backend موجود في `backend/`: Java 21 وSpring Boot 4.1.1 (`backend/pom.xml`) وMaven wrapper اسمه `backend/mvnw`. هو وحدة Deployment واحدة (`backend/app`) متجمّعة من تسعة موديولات Domain بالإضافة لـ Shared kernel صغير. الرسم: [مكوّنات الـ Backend](diagrams-ar.md#3-مكونات-الـ-backend). ودليل قراءة موديول بموديول موجود في [دليل موديولات الـ Backend](../04-codebase/backend-module-guide-ar.md).

## تركيب الـ Maven

| الـ Maven module | المسار | الدور |
|---|---|---|
| `matbakhy-shared` | `backend/shared` | Kernel من غير Spring web ولا كود Domain: `Money`, `ErrorCode`, `ApiException`, `IdempotentCommands` (port), `RequestContext`, `Languages`, `PersonalDataEraser` (port), `RuntimeEnvironment`، والـ Annotation اللي اسمه `@DomainModule` |
| `matbakhy-identity` | `backend/modules/identity` | الحسابات، والبروفايل، والعناوين، والتحقق من التليفون، وصلاحيات الموظفين (Grants)، ومسح الحساب |
| `matbakhy-cooks` | `backend/modules/cooks` | طلبات الانضمام كطبّاخ، والمراجعة، والمطابخ، وإعدادات المطبخ، وصفحات المطبخ العامة |
| `matbakhy-catalog` | `backend/modules/catalog` | الوجبات ونسخها (Revisions)، والاكتشاف (Discovery)، ومناطق الخدمة، ورفع الميديا ومراجعتها، والمفضّلة، وعدّادات النشاط العضوي |
| `matbakhy-availability` | `backend/modules/availability` | Slots الطبّاخ والسعة لكل وجبة (`SlotInventory`) |
| `matbakhy-orders` | `backend/modules/orders` | سياسة التجارة وإعداداتها، وعروض الأسعار (Quotes)، وتسجيل الأوردر ودورة حياته، وأكواد التسليم، والدفع (تتبّع العربون)، والتقييمات، وأدوات العمليات للأوردرات |
| `matbakhy-finance` | `backend/modules/finance` | تقارير الكاش والتحقق منها، وسجل العمولة (Commission ledger)، والتسوية، والاستردادات، والكشوف، والـ Insights |
| `matbakhy-support` | `backend/modules/support` | حالات الدعم والرسائل |
| `matbakhy-notifications` | `backend/modules/notifications` | الـ Outbox dispatcher، والـ Inbox، وWorker التسليم، وتسجيل الأجهزة |
| `matbakhy-operations` | `backend/modules/operations` | الـ Audit log port وتنفيذه، والـ Redaction، وAPI قراءة الـ Audit |
| `matbakhy-app` | `backend/app` | طبقة التجميع (Composition) وتطبيق Spring Boot الوحيد (`MatbakhyApplication`): الأمان، والـ MFA، ومعالجة الاستثناءات، وProvider adapters، وStore الـ Idempotency، والـ Metrics، والإعدادات، والـ Resources والـ Migrations، وكل اختبارات التكامل |

ملف `backend/pom.xml` الأب بيسرد الموديولات بترتيب الاعتماد وبيدير الإصدارات (AWS SDK BOM، وPDFBox، وصور الـ Test-container المثبّتة بالـ Digest). وكمان بيثبّت Tomcat 11.0.26 وJackson 3.1.7 متجاوزًا إصدارات الـ Boot BOM لمعالجة التنبيهات الأمنية الخاصة بالاعتماديات (Dependency advisories).

## اصطلاح الـ Packages جوه الموديول

كل موديول بيستخدم نفس التلات Sub-packages تحت `com.matbakhy.<module>`:

```
web/       @RestController classes (package-private) + request records with Bean Validation
api/       the module's public surface: interfaces and records other modules may use
internal/  services, JDBC adapters implementing api interfaces, jobs, properties (everything else)
```

مثال للـ orders: الكلاس `backend/modules/orders/src/main/java/com/matbakhy/orders/web/OrderController.java` بيستدعي `internal/OrderService.java`، اللي بيستدعي الموديولات التانية من خلال أنواع الـ `api` بتاعتها بس زي `SlotInventory` و`MealCatalog` و`KitchenDirectory` و`AddressDirectory` (شوف الـ Imports في أول `OrderService.java`). وملف `package-info.java` في جذر الموديول فيه `@DomainModule(value = "orders", stories = {...})`.

### فرض الحدود (طبقتين)

1. **اعتماديات الـ Maven.** الموديول مبيشوفش غير الموديولات المعلنة في الـ `pom.xml` بتاعه. مثلًا `backend/modules/availability/pom.xml` معتمد على `shared` و`identity` بس، فمينفعش يستورد كلاسات الـ orders.
2. **اختبارات ArchUnit** في `backend/app/src/test/java/com/matbakhy/app/architecture/`: الاختبار `ModuleBoundaryTest` بيشغّل قواعد من `ModuleRules` (`production_modules_only_cross_through_api`، `production_modules_are_free_of_cycles`، `shared_depends_on_no_module_or_app`، والموديولات متعتمدش على الـ Package بتاع الـ app، وكل Package موديول لازم يتعلن بـ `@DomainModule`). القواعد دي Parameterised وبتشتغل كمان على Packages تجريبية مخالفة عمدًا عشان نثبت إنها بتفشل. التشغيل: `cd backend && ./mvnw -B -ntp -pl app -am test -Dtest=ModuleBoundaryTest` (شكل الأمر ماتجرّبش في الـ Session دي؛ شوف [تشغيل الاختبارات](../05-developer-workflow/test-execution-ar.md) للأوامر اللي اتتحقق منها).

الـ Package اسمه `app` مش موديول عن قصد: ممكن يستخدم كلاسات `internal` بتاعة الموديولات (مثلًا `ProviderConfiguration` بيستورد `notifications.internal.NotificationProperties`). الكود الجديد في أي موديول ميعملش كده.

### ملخص الاعتماديات بين الموديولات

من كل `pom.xml`: identity محتاج operations (للـ Audit)؛ cooks محتاج identity وcatalog وoperations؛ catalog محتاج identity وoperations وavailability؛ availability محتاج identity؛ orders محتاج identity وcatalog وcooks وoperations وavailability؛ finance محتاج identity وcooks وoperations؛ support محتاج identity وorders وoperations؛ notifications محتاج identity وorders وcooks وoperations؛ operations محتاج shared بس. فالـ orders هو الموديول المركزي ومفيش حاجة تحته تقدر تستورده (قاعدة منع الدورات).

## الطبقات والوصول للبيانات

* **الـ Controller (web).** مسؤوليته محدودة: بيربط الـ Request record ويعمل له Validation، وبيقرا المتصل من `@AuthenticationPrincipal CurrentAccount`، وبيستدعي Service، وبيرجّع View record. الـ Controllers كلاسات Package-private بحقن عن طريق الـ Constructor. مثال: `OrderController.place` بيقرا الـ Headers `Idempotency-Key` و`Accept-Language` وبيرجّع `201` مع `Location`.
* **الـ Service (internal).** فيه قواعد العمل وحدود الـ Transaction (`@Transactional` بتاعة Spring؛ 81 استخدام في الكود الرئيسي). الـ Services بترجّع Java records عادية؛ ومفيش طبقة DTO mapping منفصلة.
* **الـ Persistence.** **JDBC عادي عن طريق Spring `JdbcTemplate` بـ SQL مكتوب يدويًا**؛ مفيش JPA/Hibernate (`@Entity` مش موجودة في الكود خالص) ومفيش Spring Data repositories. الـ "Repositories" يا إما SQL جوه Service يا إما كلاسات `Jdbc*` بتنفّذ Interface من الـ `api` (مثلًا `JdbcSlotInventory` و`JdbcMealCatalog` و`JdbcAccountDirectory` و`JdbcOrderDirectory` و`JdbcAuditLog`). الصفوف بتتحوّل بـ Lambdas أو `queryForMap`. النتيجة: مفيش مفاجآت Lazy-loading وكل Query باينة في المراجعة، بس تغيير اسم عمود محتاج grep في الـ Services.
* **الـ Schema.** مملوك لـ Flyway SQL migrations في `backend/app/src/main/resources/db/migration` (شوف [معمارية قاعدة البيانات](database-architecture-ar.md)). التطبيق عمره ما بيعمل جداول.

## الـ Transactions والتزامن (Concurrency)

* ميثود الـ Command في الـ Service بتكون `@Transactional` وبتكتب كل اللي محتاجاه بشكل Atomic. تسجيل الأوردر (`OrderService.placeOnce`) بيكتب الأوردر، وSnapshots الأصناف، وحجوزات المخزون، وأول حدث، ورسالة الـ Outbox، وشروط الدفع، والتزام الكاش في Transaction واحدة. شوف الرسم [تسجيل الأوردر](diagrams-ar.md#8-تسجيل-الأوردر-sequence).
* **الـ Pessimistic locks** بتتستخدم للمخزون والحالة: `SELECT ... FOR UPDATE` على صفوف `cook_slot` وبعدها `meal_slot` بترتيب الـ id (الترتيب الثابت بيمنع الـ Deadlocks) في `JdbcSlotInventory.reserve`؛ وصف الأوردر بيتقفل الأول في كل Command من دورة الحياة (`OrderLifecycleService.lock`). ميثودات المخزون عليها `@Transactional(propagation = MANDATORY)` عشان متتنادش برّه Transaction بتاعة المتصل.
* **الـ Advisory locks** بتسلسل محاولات التسجيل المكرّرة (`pg_advisory_xact_lock` على العميل + `clientRequestId`).
* **الـ Optimistic concurrency** بتستخدم عمود `version` و`expectedVersion` في أجسام الطلبات؛ وعدم التطابق بيرجّع `409 VERSION_CONFLICT` (مثلًا `PaymentService` و`MfaPolicyService.update`).
* **الـ Idempotency.** الميثود `IdempotentCommands.execute(actor, command, key, request, resultType, action)` (الـ Port في `backend/shared`، والتنفيذ `backend/app/src/main/java/com/matbakhy/app/idempotency/JdbcIdempotentCommands.java`) بتخزّن سجل لكل `(actor, command, key)` مع Hash للطلب في `idempotency_record`. نفس المفتاح مع نفس الجسم بيرجّع النتيجة المخزّنة؛ ومع جسم مختلف بيفشل بـ `IDEMPOTENCY_CONFLICT`. وبتشتغل جوه Transaction بتاعة المتصل عشان السجل والتأثيرات يتعملوا Commit مع بعض. و`OrderService` بيضيف Fingerprint للطلب (`V29__order_request_fingerprint.sql`) عشان إعادة استخدام `clientRequestId` بمحتوى مختلف تترفض حتى لو سجل الـ Header اتشال (فيه `expires_at` مدته 24 ساعة بيتسجّل بس مش بيتنفّذ؛ ومفيش Purge).
* **قواعد الـ Rollback.** التحقق من التسليم (Handover) بيستخدم `@Transactional(noRollbackFor = ApiException.class)` عشان محاولة *الكود الغلط* تتحسب برضو وممكن تقفل الـ Challenge (`OrderLifecycleService.cook`).
* **الـ Workers** (`OutboxDispatcher` و`DeliveryWorker`) بيستخدموا `TransactionTemplate` وLeases من نوع `FOR UPDATE SKIP LOCKED` عشان كذا Instance يشتغلوا بأمان. شوف [التكامل والأحداث](integration-and-events-ar.md).

## الـ Validation ومعالجة الأخطاء

* أجسام الطلبات هي Java records عليها Jakarta Bean Validation annotations (`@NotNull` و`@Size` و`@Valid`). و Jackson متظبّط **يفشل لو لقى Properties مش معروفة** (`backend/app/src/main/java/com/matbakhy/app/config/StrictJsonConfiguration.java`)، فالحقل المكتوب غلط بيترفض بدل ما يتتجاهل.
* فشل قواعد العمل بيرمي `ApiException(ErrorCode, message, List<FieldError>)` (`backend/shared/src/main/java/com/matbakhy/shared/error`). الـ `ErrorCode` بيثبّت حالة الـ HTTP وإمكانية إعادة المحاولة (مثلًا `PRICE_CHANGED` 409 و`RATE_LIMITED` 429 و`STEP_UP_REQUIRED` 403 و`DEPENDENCY_UNAVAILABLE` 503).
* الكلاس `backend/app/src/main/java/com/matbakhy/app/web/ApiExceptionHandler.java` (`@RestControllerAdvice`) بيحوّل `ApiException` وأخطاء الـ Validation والـ JSON المشوّه والـ Headers الناقصة والمسارات غير المعروفة والـ Methods الغلط ورفض الوصول وأي حاجة تانية لجسم `ApiError` واحد فيه الكود ومفتاح الرسالة القابل للترجمة وأخطاء الحقول والـ Request ID. الـ Stack traces والرسائل الداخلية عمرها ما بترجع (`server.error.include-*: never` في `application.yaml`). والاستثناءات غير المتوقعة بتتسجّل عن طريق `RedactedThrowable`، اللي بيشيل الرسائل اللي ممكن تقتبس بيانات شخصية. ودعم problem-details بتاع Spring متعطّل (`spring.mvc.problemdetails.enabled: false`) عشان الجسم ده يبقى الشكل الوحيد.
* أخطاء الأمان (401/403 قبل ما أي Controller يشتغل) بتستخدم `ApiSecurityErrors` عشان يبقى ليها نفس الجسم، وحالات الرفض بتتسجّل في الـ Audit.
* الـ Authorization على مستوى الـ Object: الـ Services بتنادي `AccessPolicy.requireOwnerOrPermitted(ownerId, permission, objectType, objectId)` (`ScopedAccessPolicy`) أو بتفلتر بالمالك في الـ SQL (`lock(c.orderId(), "o.customer_id = ?", customerId)`). أي حاجة مش ظاهرة للمتصل بترجع **404**، عمرها ما ترجع 403، عشان وجودها ميتكشفش (الـ Javadoc بتاع `OrderController`: "others' orders are 404"). ولما موظف يقرا مورد بتاع حد تاني بيتسجّل في الـ Audit.

## الـ Authentication والـ Authorization في الـ Backend

فيه سلسلتين Filters (`SecurityConfiguration`): سلسلة Bearer-JWT من غير Session (للموبايل) وسلسلة OIDC بـ Cookie-session (للويب). الاتنين بيخلصوا في `AccountMapper`، اللي بيبني `AccountAuthentication` صلاحياته (Authorities) جاية من الـ Database (`ACCOUNT_ACTIVE` بالإضافة لـ `PERM_<code>` لكل `user_grant` فعّال)، وبتتقرا من جديد مع كل طلب. الـ Controllers بتستخدم `@PreAuthorize("hasAuthority('PERM_...')")`؛ وEndpoints الموظفين عالية الخطورة بتضيف `and @staffAssurance.recentMfa(authentication)`. التفاصيل الكاملة في [الهوية والأمان](identity-and-security-ar.md).

## الـ Jobs اللي بتشتغل في الخلفية

`@EnableScheduling` شغّال في `PlatformConfiguration`. فشل الـ Jobs بيتسجّل من غير رسائل الاستثناءات (Scheduler error handler معمول له Redaction). كل Job ممكن يتقفل بـ Property عشان الاختبارات.

| كلاس الـ Job | الفاصل الافتراضي | الـ Property | الغرض |
|---|---|---|---|
| `OrderExpiryJob` (orders) | 30s | `matbakhy.orders.expiry-job.enabled` | بيخلّي الأوردرات المعلّقة اللي عدّت `accept_by` تنتهي وبيحرّر المخزون |
| `LedgerPostingJob` (finance) | 30s | `matbakhy.finance.posting-job.enabled` | بيرحّل مجموعة Ledger واحدة من نوع `COMMISSION` لكل حدث إتمام باستخدام العمولة اللي متسجّل لها Snapshot في الأوردر (`LedgerService.postCommission`)؛ ومع قاعدة العمولة الصفرية وقت الإطلاق المبالغ بتكون 0 (شوف `backend/app/src/test/java/com/matbakhy/app/finance/ZeroCommissionIntegrationTest.java`) |
| `NotificationJobs` (notifications) | 5s | `matbakhy.notifications.jobs-enabled` | بيشغّل `OutboxDispatcher` و`DeliveryWorker` |
| `MediaCleanupJob` (catalog) | 30s | `matbakhy.media.cleanup-job.*` | بيمسح ملفات الـ Object storage اليتيمة المسجّلة في `media_cleanup` |
| `AccountErasureJob` (identity) | 15m | `matbakhy.privacy.erasure-job.enabled` | بيمسح البيانات الشخصية بعد فترة السماح للحذف (الافتراضي `P14D`، وهو افتراضي مقترح في انتظار قرار المالك O06) |

## الإعدادات وحمايات الـ Startup

الملف `backend/app/src/main/resources/application.yaml` فيه كل الـ Properties مع Overrides بمتغيرات البيئة. والـ `matbakhy.environment` (`APP_ENV`) **ملوش قيمة افتراضية** عشان أي Deployment ميقدرش يشتغل من غير ضبط. `EnvironmentGuard` بيرفض الـ Profile اسمه `dev` أو مكان Flyway اسمه `devdata` برّه LOCAL/CI/PREVIEW؛ و`ProviderConfiguration` بيرفض الأسرار الخاصة بالتطوير (الأسماء اللي بتبدأ بـ `local-dev-only-`) برّه LOCAL/CI، وبيرفض يشغّل STAGING/PRODUCTION من غير Adapters حقيقية للـ SMS والإيميل والـ Push وفاحص الـ Malware (ومفيش أي منها لسه). قواعد التجارة (العمولة صفر) بتتفرض في `CommerceConfiguration` وبقيد الـ Check في V23.

الـ API بيستخدم دورين Database في البيئات اللي متعمل لها Deployment: Flyway بيعمل Migration بدور المالك (`SPRING_FLYWAY_USER`)، والطلبات بتشتغل بدور وقت التشغيل الأقل صلاحية (`DATABASE_USERNAME`)، اللي `afterMigrate.sql` بيديله صلاحية الوصول للبيانات بس مش DDL ولا `UPDATE`/`DELETE` على الجداول الـ Append-only.

## المراقبة (Observability)

* كل سطر Log فيه `[requestId]` (`logging.pattern.level`)؛ والبيئات اللي متعمل لها Deployment بتتحول لـ ECS JSON (`LOGGING_STRUCTURED_FORMAT_CONSOLE=ecs`) باسم خدمة `matbakhy-api` والبيئة والإصدار من `MATBAKHY_VERSION`.
* الـ Actuator بيعرض `health` على بورت الـ API (التفاصيل مخفية). وEndpoint الـ Prometheus بيتقدّم على بورت إدارة منفصل (8081 في المعاينة) مفيش Proxy بيوجّه له (`SecurityConfiguration.managementScrape`).
* `OperationalMetrics` بينشر Gauges مشتقة من الـ Database زي `matbakhy.outbox.oldest.pending.age` و`matbakhy.outbox.failed` و`matbakhy.notifications.dead` و`matbakhy.ledger.unbalanced.groups`، اللي عليها قواعد التنبيه في `deploy/web-preview/observability/alerts.yml`.
* سجل الـ Audit هو ميزة بيزنس (جدول `audit_event`، و`GET /api/v1/ops/audit` في `backend/modules/operations/src/main/java/com/matbakhy/operations/web/AuditController.java`)، ومنفصل عن الـ Logs.

## إزاي تلاقي طريقك: أمثلة عملية

| السؤال | ابدأ من هنا |
|---|---|
| أنهي كلاس بيتعامل مع `POST /api/v1/orders`؟ | اعمل grep على المسار: `grep -rn '"/api/v1/orders"' backend/modules`. النتيجة: `OrderController.place` في `web` بتاع الـ orders. وقايمة المسارات موجودة كمان في `contracts/endpoints.json`. |
| السعر بيتحسب فين؟ | `QuoteService.terms(...)` في `internal` بتاع الـ orders؛ وبيتراجع تاني في `requireValid`. |
| تغيير الحالة بيحصل فين؟ | `OrderLifecycleService.transition(...)`؛ وكل تغيير حالة بيكتب كمان صف `order_event` وصف `outbox_event`. |
| أضيف صلاحية فين؟ | `identity/api/Permission.java`، وقيد `user_grant_permission_check` في Migration **جديدة** (شوف V31 لمعرفة إزاي اتضافوا كودين)، وبعدها `@PreAuthorize` على الـ Endpoint. |
| الـ SQL بتاع جدول X فين؟ | `grep -rn "FROM x\b" backend/modules backend/app/src/main`. |
| أضيف نداء بين موديولين إزاي؟ | ضيف ميثود في Interface الـ `api` بتاع الموديول المنادَى عليه، ونفّذها في الـ Package `internal` بتاعه (غالبًا كلاس `Jdbc*`)، وضيف اعتماد الـ Maven لو الـ pom مفيهوش، وشغّل `ModuleBoundaryTest`. |

## القيود المعروفة وفجوات التوثيق

* الموديولات مفيهاش اختبارات خاصة بيها (`backend/modules/*/src` فيه `main` بس). كل الـ 80 ملف اختبار Java (اختبارات ومساعدات) موجودين في `backend/app/src/test` وهي اختبارات تكامل بـ Testcontainers على PostgreSQL حقيقي ومحاكي S3، بالإضافة لكام Unit test؛ ومفيش عزل لاختبارات الوحدة على مستوى الموديول.
* `docs/engineering/01-architecture.md` بيوصف ADR-001 كقاعدة للموديولات؛ والشكل القابل للتنفيذ هو `ModuleRules.java`. ولو اختلفوا، الاختبار هو اللي يكسب.
