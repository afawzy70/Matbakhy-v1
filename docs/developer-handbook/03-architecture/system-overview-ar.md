# نظرة عامة على السيستم

> **Owner role:** Backend architecture
> **Status:** PARTIALLY IMPLEMENTED — منصة الطلبات الأساسية شغّالة على بيئة معاينة ببيانات تجريبية؛ مفيش مزوّدين حقيقيين ولا Push ولا فلوس حقيقية ولا Deployment على الـ Production
> **Language / اللغة:** [English](system-overview.md) · العربية

الصفحة دي هي الخريطة. بتسمّي كل جزء بيتعمل له Deployment، وإيه اللي بيكلّم إيه، وأنهي Integrations حقيقية وأنهي لأ. التفاصيل في الصفحات المجاورة، ومعنى البيزنس في [نموذج الـ Marketplace](../01-business/marketplace-model-ar.md).

## السيستم ده إيه

مطبخي هو Marketplace للأكل البيتي في مصر (EGP، `Africa/Cairo`). العميل بيتصفّح المطابخ والوجبات، ويطلب عرض سعر (Quote)، ويعمل أوردر لمطبخ واحد وSlot واحد، والطبّاخ بيقبله وينفّذه. المنصة مبتاخدش عمولة ومبتحتفظش بالفلوس في عهدتها في الـ MVP؛ العربون بيتدفع مباشرةً للمطبخ ويتم *الإبلاغ* عنه بس في السيستم (راجع [التسعير والدفع](../01-business/pricing-and-payments-ar.md)).

هو **Modular Monolith**: عملية Spring Boot واحدة، وقاعدة بيانات PostgreSQL واحدة، وتلات Clients كلهم بيستخدموا نفس الـ HTTP API. شوف الرسمين 1 و 2 في [الرسومات](diagrams-ar.md#1-سياق-السيستم).

## الـ Containers

| الـ Container | التقنية | المصدر | الحالة |
|---|---|---|---|
| ويب العميل | React 19, Vite 8, TypeScript, hash routing | `apps/customer-web` | IMPLEMENTED |
| ويب العمليات (مساحة الطبّاخ والـ Back Office بتاع الموظفين في تطبيق واحد) | React 19, Vite 8 | `apps/operations-web` | IMPLEMENTED |
| موبايل العميل (Android و iOS) | Expo 57 / React Native 0.86, React Navigation | `apps/customer-mobile` | PARTIALLY IMPLEMENTED (تطبيق العميل بس؛ مفيش تطبيق موبايل للطبّاخ أو الموظفين؛ دليل التحقق الـ Native هو تشغيلات الـ Emulator/Simulator في الـ CI، شوف [معمارية الموبايل](mobile-architecture-ar.md)) |
| الـ Backend API | Spring Boot 4.1.1, Java 21, Maven multi-module | `backend/` | IMPLEMENTED |
| الـ Database | PostgreSQL 16.15, Flyway migrations V1 to V31 | `backend/app/src/main/resources/db/migration` | IMPLEMENTED |
| مزوّد الهوية (Identity provider) | Keycloak 26.8.0, realm `matbakhy` | `infra/identity/matbakhy-realm.json`, `deploy/web-preview/identity/matbakhy-realm.preview.json` | IMPLEMENTED (إعدادات كـ Code؛ تغييرات الـ Realm الحيّة بتتطبّق بـ Scripts، شوف [الهوية والأمان](identity-and-security-ar.md)) |
| تخزين الملفات (Object storage) | S3 API; Adobe s3mock محليًا، SeaweedFS على المعاينة | `infra/compose.yaml`, `deploy/web-preview/compose.yaml`, `backend/app/src/main/java/com/matbakhy/app/providers/S3ObjectStorage.java` | IMPLEMENTED للـ local/المعاينة؛ لسه ماتحدّدش مزوّد للـ Production |
| الـ Reverse proxy | Caddy 2.10 جوه صورة الـ "front" | `deploy/web-preview/Caddyfile`, `deploy/web-preview/front.Dockerfile` | IMPLEMENTED (المعاينة بس) |
| المراقبة والـ Logging | Prometheus, Alertmanager, Loki, Alloy, node-exporter, Grafana (اختياري) | `deploy/web-preview/observability` | IMPLEMENTED على Stack المعاينة؛ مش تصميم مراقبة للـ Production |
| حزم TypeScript المشتركة | `@matbakhy/api-client` (متولّدة من OpenAPI), `@matbakhy/design-tokens` | `packages/api-client`, `packages/design-tokens` | IMPLEMENTED |

**مفيش** Message Broker ولا Cache Server ولا Search Engine ولا عملية Worker منفصلة. الشغل في الخلفية (انتهاء الأوردر، إرسال الـ Outbox، تسليم الإشعارات، ترحيل الـ Ledger، تنضيف الميديا، مسح الحساب) بيشتغل كـ Jobs من نوع `@Scheduled` جوه عملية الـ API وبيتنسّق عن طريق الـ Locks في الـ Database. شوف [التكامل والأحداث](integration-and-events-ar.md).

## العقد بين الـ Clients والسيرفر

`contracts/openapi.yaml` هو عقد الـ API؛ و`contracts/endpoints.json` هو سجل الـ Endpoints اللي بتستخدمه الفحوصات. `packages/api-client/src/generated/schema.ts` بيتولّد من ملف OpenAPI (`pnpm --filter @matbakhy/api-client run generate`)، و`pnpm contract:check` بيعمل Lint للعقد وبيفشل لو فيه Generator drift. التلات Clients كلهم بيستوردوا الحزمة دي، فأي تغيير في العقد بيظهر كـ TypeScript error في التطبيقات. تفاصيل أكتر في [عقود الـ API](../04-codebase/api-contracts-ar.md).

## مين صاحب القرار في إيه

| الموضوع | صاحب القرار | فين |
|---|---|---|
| الأسعار والإجماليات ورسوم التوصيل والضريبة | السيرفر، بيتحسبوا من جديد وقت الـ Quote ومرة تانية وقت تسجيل الأوردر | `backend/modules/orders/src/main/java/com/matbakhy/orders/internal/QuoteService.java` |
| السعة بتاعة الـ Slot / الوجبة | السيرفر، عدّادات Transactional مع Row locks | `backend/modules/availability/src/main/java/com/matbakhy/availability/internal/JdbcSlotInventory.java` |
| الأدوار والصلاحيات | قاعدة بيانات السيرفر (`user_grant`، ملكية المطبخ)؛ الـ Role claims في الـ Token بتتتجاهل | `backend/app/src/main/java/com/matbakhy/app/security/AccountMapper.java` |
| انتقالات حالة الأوردر | السيرفر؛ والحالات النهائية كمان متجمّدة بـ Database trigger | `backend/modules/orders/src/main/java/com/matbakhy/orders/internal/OrderLifecycleService.java` |
| متطلب الـ MFA | سياسة في قاعدة بيانات السيرفر بالإضافة لتأكيدات Keycloak | `backend/app/src/main/java/com/matbakhy/app/mfa/MfaPolicyService.java` |
| نصوص الواجهة وتخطيط الـ RTL | الـ Clients (العربي RTL هو الافتراضي، والإنجليزي LTR) | `apps/*/src/i18n.ts`, `packages/design-tokens` |

الـ Clients بتعرض القيم اللي جاية من السيرفر؛ وسعر الوحدة اللي السلة فاكراه هو تقدير بس (`apps/customer-web/src/cart.ts`).

## التكاملات الخارجية: إيه الموجود فعلًا

| التكامل | الواقع | الدليل |
|---|---|---|
| Keycloak (OIDC) | IMPLEMENTED | `backend/app/src/main/java/com/matbakhy/app/security/SecurityConfiguration.java` |
| Keycloak Admin REST (إعادة ضبط الـ MFA، إنهاء الـ Sessions) | IMPLEMENTED؛ محتاج سر الـ Service-account بتاع `matbakhy-admin`، وإلا العمليتين دول بيردّوا 503 | `backend/app/src/main/java/com/matbakhy/app/mfa/KeycloakAdmin.java`, `docs/security/mfa-policy.md` |
| تخزين Object متوافق مع S3 | IMPLEMENTED (رفع/قراءة بـ Presigned URLs) | `backend/modules/catalog/src/main/java/com/matbakhy/catalog/api/ObjectStorage.java` |
| فاحص الـ Malware | DISABLED خارج LOCAL/CI/PREVIEW: التنفيذ الوحيد الموجود وهمي (Fake)؛ وأي بيئة تانية بترفض تشتغل | `backend/app/src/main/java/com/matbakhy/app/providers/FakeMalwareScanner.java`, `ProviderConfiguration.malwareScanner` |
| التحقق من التليفون بـ SMS | PLANNED: فيه `FakePhoneVerificationSender` بس؛ والبيئات غير التجريبية بترمي Exception وقت الـ Startup | `backend/app/src/main/java/com/matbakhy/app/providers/FakePhoneVerificationSender.java` |
| إشعارات الإيميل | PLANNED: فيه Adapter بيكتب في الـ Log بس | `backend/app/src/main/java/com/matbakhy/app/providers/LoggingNotificationChannels.java` |
| إشعارات الـ Push | DISABLED: الـ Flag اسمه `PUSH_DELIVERY_ENABLED` وقيمته الافتراضية false؛ وحتى لو true فيه Adapter بيكتب في الـ Log بس؛ تطبيق الموبايل بيطلب إذن النظام بس مبيسجّلش Device token | `backend/modules/notifications/src/main/java/com/matbakhy/notifications/internal/OutboxDispatcher.java`, `apps/customer-mobile/src/notificationPermission.ts` |
| مزوّد دفع / API بنكي / InstaPay | NOT INTEGRATED. العربون تحويلات يدوية من العميل للمطبخ، والأطراف هي اللي بتبلّغ عنها | `backend/modules/orders/src/main/java/com/matbakhy/orders/internal/PaymentService.java` |
| الخرائط / Geocoding | NOT VERIFIED (العناوين فيها أعمدة اختيارية للـ latitude وlongitude، ومتلقاش أي نداء لمزوّد) | `backend/app/src/main/resources/db/migration/V21__personal_data_erasure.sql` |
| الإعلانات المدفوعة، فوترة العمولة | برّه نطاق الـ MVP؛ العمولة لازم تكون صفر | `backend/app/src/main/resources/db/migration/V23__launch_commission.sql` |

## البيئات

| `APP_ENV` | المعنى | المزوّدين | متعرّف فين |
|---|---|---|---|
| `LOCAL` | جهاز المطوّر عن طريق `scripts/dev.sh up` و`infra/compose.yaml` | Fakes، والمفاتيح الخاصة بالتطوير مسموحة، والـ Profile بتاع `dev` بيضيف Migrations تجريبية | `backend/app/src/main/java/com/matbakhy/app/config/MatbakhyProperties.java` |
| `CI` | تشغيلات الاختبارات | زي LOCAL | نفس المكان |
| `PREVIEW` | المعاينة المحدودة الوصول ببيانات تجريبية | الـ Fakes مسموحة، والأسرار لازم تكون حقيقية، وبيظهر إشعار الديمو | `deploy/web-preview/compose.yaml` |
| `STAGING`, `PRODUCTION` | متعرّفين في الـ Enum؛ والتطبيق بيرفض يشتغل من غير مزوّدين حقيقيين | مفيش | `backend/app/src/main/java/com/matbakhy/app/providers/ProviderConfiguration.java` |

يعني النهارده البيئة الوحيدة اللي متعمل لها Deployment هي المعاينة. مفيش بيئة Production ولا فلوس حقيقية ولا SMS/email/push حقيقي ولا إصدار على متاجر التطبيقات. أي كلام عن "staging" في المستندات القديمة اعتبره المعاينة؛ شوف [البنية التحتية والـ Deployment](infrastructure-and-deployment-ar.md).

## سلوكيات مشتركة تستاهل تعرفها من بدري

* **الـ Request ID.** `RequestIdFilter` بيقبل `X-Request-Id` لو شكله سليم أو بيولّد واحد، وبيحطه في الـ MDC بتاع الـ Log وفي أجسام الأخطاء وفي Header الرد، وبيخزّنه على أحداث الـ Outbox عشان الشغل غير المتزامن يتربط ببعضه.
* **الأخطاء.** جسم خطأ واحد لكل حاجة (`ApiError`) بقيم `ErrorCode` ثابتة زي `PRICE_CHANGED` أو `STEP_UP_REQUIRED`؛ والـ Clients بتترجم الكود. شوف [معمارية الـ Backend](backend-architecture-ar.md#الـ-validation-ومعالجة-الأخطاء).
* **الـ Idempotency.** الأوامر اللي بتغيّر البيانات بتاخد Header اسمه `Idempotency-Key` وبتتعاد بأمان طول ما صف `idempotency_record` موجود (قيمة `expires_at` بتاعته، ومدتها 24 ساعة، بتتسجّل بس مش بتتنفّذ)؛ وتسجيل الأوردر كمان ليه قيد Unique دائم على `(customer_id, client_request_id)`.
* **الوقت.** الـ Instants بتتخزّن UTC في الـ Database؛ والجدولة والعرض في البيزنس بيستخدموا `Africa/Cairo`.
* **الفلوس.** `bigint` بالقروش (piastres) في كل حتة (أعمدة `*_minor`، و`Money` في `backend/shared`)؛ ومفيش Floating point.
* **السجل اللي بيتضاف عليه بس (Append-only).** أحداث الأوردرات والـ Audit والـ Ledger ونسخ الوجبات وتغييرات الإعدادات محمية بـ Database triggers وبسحب `UPDATE`/`DELETE` من دور الـ Database بتاع وقت التشغيل (`backend/app/src/main/resources/db/migration/afterMigrate.sql`).

## اقرا إيه بعد كده

* تركيب الـ Source: [خريطة الـ Repository](../04-codebase/repository-map-ar.md).
* داخل الـ Backend: [معمارية الـ Backend](backend-architecture-ar.md)؛ والبيانات: [معمارية قاعدة البيانات](database-architecture-ar.md).
* الـ Authentication والـ MFA: [الهوية والأمان](identity-and-security-ar.md).
* تشغيله: [البيئة المحلية](../05-developer-workflow/local-environment-ar.md).
* قرارات المعمارية القديمة (ADR-001 إلى ADR-005): `docs/engineering/01-architecture.md`.

## القيود المعروفة وفجوات التوثيق

* ملف `.github/workflows/deploy-preview.yml` عنوانه "Deploy Matbakhy preview to Contabo" وكمان Workflow الـ E2E بيقول Contabo، بينما `docs/operations/09-staging-hetzner-preparation.md` و`docs/operations/12-owner-server-setup.md` بيوصفوا سيرفر Hetzner. الـ Workflows (اللي بتعمل Deployment عن طريق SSH لـ Host مأخوذ من أسرار الـ Repository، والدومين مكتوب جوه الـ Workflow) هي الحقيقة القابلة للتنفيذ؛ أما مين المزوّد اللي مستضيف المعاينة فهو NOT VERIFIED من الـ Repository.
* `STAGING` و`PRODUCTION` موجودين كقيم Enum وكـ Guards بتفشل بسرعة (fail-fast)، بس مفيش حاجة بتتعمل لها Deployment عليهم.
