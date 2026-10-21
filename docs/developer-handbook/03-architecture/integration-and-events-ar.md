# التكامل والأحداث

> **Owner role:** Backend architecture
> **Status:** PARTIALLY IMPLEMENTED — الـ Transactional outbox والـ Inbox داخل التطبيق وآلية إعادة المحاولة شغّالين؛ ومزوّدين الإيميل والـ SMS والـ Push الخارجيين وهميين أو متعطّلين، ومفيش تكامل مع مزوّد دفع
> **Language / اللغة:** [English](integration-and-events.md) · العربية

الصفحة دي بتغطي إزاي الشغل بيخرج من الـ Transaction (الأحداث)، وإزاي الإشعارات بتوصل للناس، وأنهي أنظمة خارجية الـ Backend بيكلّمها فعلًا. سياق السيستم في [نظرة عامة على السيستم](system-overview-ar.md#التكاملات-الخارجية-إيه-الموجود-فعلا)؛ وتعريفات الجداول والأعمدة في [معمارية قاعدة البيانات](database-architecture-ar.md).

## نموذج الأحداث

**مفيش Event bus داخل العملية** (لا `ApplicationEventPublisher` ولا `@EventListener` ولا Message broker في الكود). ردود الفعل بين الموديولات بتستخدم تلات آليات:

1. **نداءات مباشرة عن طريق Interfaces الـ `api`** جوه Transaction واحدة (مثلًا `OrderLifecycleService` بينادي `SlotInventory.move` و`PaymentService.orderClosed`).
2. **الـ Transactional outbox** (`outbox_event`) لأي حاجة لازم تحصل *بعد* الـ Transaction وممكن تفشل بشكل مستقل: الإشعارات.
3. **Read models فوق تاريخ بيتضاف عليه بس**: اللي بيرحّل الـ Ledger بيقرا صفوف الإتمام من `order_event` بدل ما يسمع لأي حاجة (`LedgerService.unpostedCompletions` و`LedgerPostingJob`).

## الـ Transactional outbox

المنتجين (Producers) بيدخّلوا صف `outbox_event` في نفس الـ Transaction بتاعة تغيير الحالة:

| المنتج | أنواع الأحداث | المصدر |
|---|---|---|
| تسجيل الأوردر | `ORDER_PLACED` (الـ Payload: `orderId`، `kitchenId`) | `OrderService.placeOnce` |
| كل انتقال في الأوردر | `ORDER_<new state>` زي `ORDER_ACCEPTED` و`ORDER_READY` و`ORDER_COMPLETED` (الـ Payload: `orderId`، `state`) | `OrderLifecycleService.transition` |
| حالات دفع مختارة | `PAYMENT_CUSTOMER_TRANSFER_REPORTED` و`PAYMENT_KITCHEN_RECEIPT_REPORTED` و`PAYMENT_DISPUTED` و`PAYMENT_REFUND_OWED` و`PAYMENT_REFUNDED` | `PaymentService.event` (المجموعة `NOTIFIED`) |

الأعمدة: `aggregate_type` و`aggregate_id` و`event_type` و`payload jsonb` و`status` (`PENDING` و`SENT` و`FAILED`) و`attempts` و`available_at` وأعمدة الـ Lease (`lease_owner` و`leased_until`) و`last_error` و`processed_at` و`version` و`request_id` (الـ `X-Request-Id` الأصلي، عشان التسليم الفاشل يتتبّع للطلب في الـ Logs؛ V22).

### الإرسال: `OutboxDispatcher`

الكلاس `NotificationJobs` بيشتغل كل 5 ثواني (`matbakhy.notifications.poll-interval`) وبينادي `dispatchOnce` وبعدها `deliverOnce`. و`dispatchOnce`:

1. بياخد لحد 100 حدث مستحق بـ `UPDATE ... WHERE id IN (SELECT ... FOR UPDATE SKIP LOCKED) RETURNING`، وبيحط Lease (`matbakhy.notifications.lease`، 60 ثانية). ممكن كذا Worker يشتغلوا؛ وأحداث Worker وقع بتبقى قابلة للاستلام تاني لما الـ Lease ينتهي.
2. بيعالج بترتيب الإنشاء، **كل حدث في Transaction بتاعته** (`TransactionTemplate`): الـ Fan-out وبعدين يعلّم `SENT` بس لو الـ Worker ده لسه ماسك الـ Lease.
3. الـ Fan-out (`fanOut`): بيدوّر على أطراف الأوردر عن طريق `OrderDirectory.parties` وبيحدد المستلمين: `CUSTOMER_EVENTS` (حالات تقدّم الأوردر وحالات الدفع المقصود بيها العميل) بتروح للعميل؛ و`COOK_EVENTS` (`ORDER_PLACED` و`ORDER_CANCELLED` و`ORDER_EXPIRED` و`PAYMENT_CUSTOMER_TRANSFER_REPORTED`) بتروح لصاحب المطبخ. لكل مستلم بيدخّل صف `notification` (الـ Inbox داخل التطبيق) مع `ON CONFLICT (recipient_id, dedup_key) DO NOTHING`، فالإعادات والتكرارات مبتضيفش حاجة، وبعدين صف تسليم `EMAIL` واحد، ولو الـ Push مفعّل بس، صف تسليم `PUSH` واحد لكل جهاز مسجّل.
4. عند Exception: بيزوّد `attempts`، وبيعيد الجدولة بـ Exponential backoff (`initial-backoff` 30 ثانية، بيتضاعف، وسقفه 2^10 مرة)، وبعد `max-attempts` (5) بيعلّم الحدث `FAILED`.

### التسليم: `DeliveryWorker`

بياخد صفوف `notification_delivery` بنفس الطريقة (Lease، و`SKIP LOCKED`)، وبينادي Port القناة **برّه** أي Transaction في الـ Database، وبعدين بيسجّل النتيجة بس لو لسه ماسك الـ Lease. الـ Ports هي `EmailSender` و`PushSender` في `backend/modules/notifications/src/main/java/com/matbakhy/notifications/api/NotificationChannels.java`؛ وكل إرسال بيحمل `deliveryId` ثابت لازم المزوّد الحقيقي يستخدمه كمفتاح Idempotency بتاعه. الـ Exception المرمي بيعتبر مؤقت (بيتعاد)؛ أما `Result.permanentFailure` فلأ. الصفوف اللي بتوصل لحد إعادة المحاولة بتبقى `DEAD` وبتبان للعمليات. نص خطأ المزوّد بيتحفظ بس لو طابق نمط كود بسيط (فالبيانات الشخصية اللي المزوّد بيرددها عمرها ما بتتخزن). والمستلم اللي حسابه مش `ACTIVE` بياخد فشل دائم.

ترتيب الضمانات: تسليم مرة على الأقل (at-least-once) للقناة، وصفوف إشعار مرة على الأكتر (at-most-once) لكل مستلم وحدث منطقي، ومفيش تأثير على حالة الأوردر لما التسليم يفشل.

### تشغيل الـ Outbox

* الموظفين اللي معاهم `integration.ops` يقدروا يقرأوا صحة التكامل (`GET /api/v1/ops/integrations`: أعداد المعلّق والفاشل، وعمر أقدم معلّق، وعمليات التسليم اللي استنفدت محاولاتها (Dead deliveries)) ويعيدوا تشغيل حدث `FAILED` (`POST /api/v1/ops/outbox/{eventId}/retry` مع `expectedVersion` وسبب؛ ومسجّل في الـ Audit كـ `outbox.retry`). الأحداث `FAILED` بس هي اللي تتعاد (`NotificationService.retryOutbox`).
* الـ Gauges للتنبيه: `matbakhy.outbox.oldest.pending.age` و`matbakhy.outbox.failed` و`matbakhy.notifications.dead` و`matbakhy.notifications.oldest.pending.age` (`OperationalMetrics`)، مع قواعد تنبيه في `deploy/web-preview/observability/alerts.yml`.
* الاختبارات: `backend/app/src/test/java/com/matbakhy/app/notifications/NotificationIntegrationTest.java` و`PushFlagIntegrationTest.java` و`backend/app/src/test/java/com/matbakhy/app/reliability/RestartRecoveryIntegrationTest.java`.

## قنوات الإشعارات النهارده

| القناة | الحالة | السلوك |
|---|---|---|
| الـ Inbox داخل التطبيق | IMPLEMENTED | صفوف `notification`؛ و`GET /api/v1/notifications` و`PUT /api/v1/notifications/{notificationId}/read`. تطبيق الموبايل فيه شاشة إشعارات؛ وويب العميل بيعرض الحالة من خلال صفحات الأوردرات (NOT VERIFIED إن الويب فيه شاشة Inbox). |
| الإيميل | PLANNED (وهمي بس) | `ProviderConfiguration.emailSender` بترجّع Adapter بيكتب في الـ Log في LOCAL/CI/PREVIEW وبترمي Exception في STAGING/PRODUCTION: "No email notification provider is configured". الـ Adapter بيسجّل الـ Delivery id والـ Template بس، وعمره ما بيسجّل المستلمين. |
| الـ Push | DISABLED | `PUSH_DELIVERY_ENABLED` قيمته الافتراضية `false`. طول ما هو مقفول، مفيش صفوف تسليم `PUSH` بتتعمل، و`pushDisabled()` بتفشّل أي تسليم شارد فشل دائم بـ `PUSH_DISABLED`. Endpoints تسجيل الأجهزة موجودة (`PUT/DELETE /api/v1/me/devices/{installationId}`؛ والـ Tokens متخزنة كـ Hash بالإضافة لنص مشفّر بـ AES-GCM عن طريق `TokenVault`)، بس تطبيق الموبايل مبيسجّلش Tokens. التفعيل محتاج مزوّد حقيقي واختبارات على الأجهزة وموافقة المالك (قرار المالك O07). |
| الـ SMS | PLANNED (وهمي بس) | التحقق من التليفون بيستخدم `PhoneVerificationSender`؛ وفيه `FakePhoneVerificationSender` بس. |

## Jobs غير متزامنة تانية

مش مبنية على الـ Outbox، بس جزء من صورة التكامل (ومتسرودة كمان في [معمارية الـ Backend](backend-architecture-ar.md#الـ-jobs-اللي-بتشتغل-في-الخلفية)):

* `OrderExpiryJob` بيخلّي الأوردرات المعلّقة تنتهي بوقت السيرفر.
* `LedgerPostingJob` بيرحّل مجموعات Ledger العمولة من أحداث الإتمام، بشكل Idempotent عن طريق الـ Unique بتاع `(kind, source_event_id)`.
* `MediaCleanupJob` بيمسح الـ Objects غير المشار ليها المسجّلة في `media_cleanup`.
* `AccountErasureJob` بيمسح البيانات الشخصية بعد فترة السماح للحذف.

## الأنظمة الخارجية اللي الـ Backend بينادي عليها

| النظام | إزاي | ملاحظات |
|---|---|---|
| Keycloak | جلب JWKS وEndpoints الـ Token عن طريق `OIDC_BACKCHANNEL_ISSUER` (URL داخلي، فالـ Issuer جوه الـ Tokens يفضل العام)؛ والـ Admin REST في `KeycloakAdmin` | نداءات الـ Admin لإعادة ضبط الـ MFA وإنهاء الـ Sessions بس |
| تخزين الـ Objects | AWS SDK v2 S3 client (`S3ObjectStorage`)؛ والـ Clients بترفع وتقرا من خلال **روابط Presigned** جذرها `OBJECT_STORAGE_CLIENT_ENDPOINT` | Bucket خاص للرفع والمستندات، وBucket عام للنسخ الموافق عليها (`matbakhy-public`)؛ وبوابة فحص الـ Malware اسمها `MalwareScanner` بترفض إتاحة الملف لو الفحص مش متاح أو مش ناجح (fail closed) |
| Email/SMS/push | Ports بس؛ ووهميين | شوف فوق |
| المدفوعات | مفيش. تدفقات العربون والكاش هي State machines داخلية بتتغذى من بلاغات المستخدمين | `PaymentService`؛ والتعاملات المالية الحقيقية مش مفعّلة حاليًا، وتفعيلها مشروط بقرار المالك D16 |

الـ API مبيعملش أي نداءات خارجة لخدمات الدفع أو الخرائط أو التحليلات أو الإعلانات (مفيش حاجة اتلقت في مصدر `backend/` أو `application.yaml`).

## العقد الوارد (Inbound contract)

كل التكامل الوارد هو الـ HTTP API في `contracts/openapi.yaml`. عملاء الويب بيستخدموا Cookies وCSRF؛ والموبايل بيستخدم Bearer tokens؛ والأوامر الـ Idempotent بتتوقع `Idempotency-Key`. ومفيش Webhooks من أطراف تانية.

## القيود المعروفة وفجوات التوثيق

* أنواع الأحداث هي Strings بتتبني وقت التشغيل (`"ORDER_" + to`)، مش Enum ولا Schema؛ ومجموعة المستلمين هي زوج ثابت من `Set<String>` في `OutboxDispatcher`. إضافة انتقال أو حالة دفع مبتبلّغش حد تلقائيًا.
* `ORDER_PLACED` بيبلّغ الطبّاخ، بس المجموعة المخصصة للعميل بتبدأ من `ORDER_ACCEPTED`؛ ومفيش إشعار "تم استلام الأوردر" للعميل (بالتصميم أو بالسهو: NOT VERIFIED مقارنةً بقواعد المنتج).
* Adapters المزوّدين للإيميل والـ SMS والـ Push وفحص الـ Malware كلها لسه مطلوبة لأي بيئة مش معاينة (قرار المالك O07).
* `docs/engineering/07-integrations.md` هو الخطة الأقدم؛ والصفحة دي بتعكس الكود.
