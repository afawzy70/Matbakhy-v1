# التنقّل في الـ Repository: ألاقيه فين؟

> **Owner role:** Handbook navigation
> **Status:** REFERENCE — جدول بحث موجّه بالمهام، من السؤال للملف اللي بيجاوب عليه
> **Language / اللغة:** [English](repository-navigation.md) · العربية

استخدم الصفحة دي لما تكون عارف انت بتدوّر على إيه ("السعر بيتـ Validate فين؟") بس مش عارف مكانه.
لتوزيع المجلدات شوف [خريطة الـ Repository](../04-codebase/repository-map-ar.md). المسارات اللي تحت اتراجعت
على الشجرة الموجودة في الـ Branch ده.

## 1. "بيبدأ منين؟"

| التطبيق | ملف البداية | بيحصل إيه هناك |
|---|---|---|
| الـ Backend | `backend/app/src/main/java/com/matbakhy/app/MatbakhyApplication.java` | `SpringApplication.run`؛ فحص الـ Components في `com.matbakhy`؛ الـ Controllers والـ Services بتاعة كل موديول بتتلاقي من هنا |
| ويب العميل | `apps/customer-web/src/main.tsx` | بيحمّل خطوط Cairo + الـ Design Tokens، ويرسم `App` من `apps/customer-web/src/App.tsx` في `StrictMode` |
| ويب العمليات (مساحة الطبّاخ + الـ Backoffice) | `apps/operations-web/src/main.tsx` | نفس النمط؛ و`apps/operations-web/src/App.tsx` هو اللي بيقرّر الأقسام اللي تظهر |
| موبايل العميل | `apps/customer-mobile/index.ts` | `registerRootComponent` لـ `App` من `apps/customer-mobile/src/App.tsx` (نقطة دخول الـ Production، من غير Test doubles) |

## 2. سطح الـ API

| السؤال | الإجابة |
|---|---|
| الـ Routes متعرّفة فين في الكود؟ | كلاسات `@RestController` في `backend/modules/<module>/src/main/java/com/matbakhy/<module>/web/*.java`؛ بالإضافة لـ `backend/app/src/main/java/com/matbakhy/app/mfa/MfaController.java` و`MfaAdminController.java` |
| العقد (Contract) فين؟ | `contracts/openapi.yaml` (بأنواع) و`contracts/endpoints.json` (سجل الـ Routes بمعرّفات `API-001`..`API-116`، والصلاحية، وعلامة `implemented`) |
| إيه اللي بيفشل لو الكود والسجل والعقد اختلفوا؟ | `packages/api-client/test/registry.test.ts` (السجل مقابل OpenAPI: الـ Method، المسار، الصلاحية، `x-implemented`، وقيم أكواد الأخطاء مقابل `ErrorCode.java`)، و`backend/app/src/test/java/com/matbakhy/app/web/ContractRoutesTest.java` (مجموعة `implemented: true` في السجل لازم تساوي الـ Routes اللي التطبيق الشغّال بيخدمها)، و`pnpm --filter @matbakhy/api-client run check:drift` (ملف `packages/api-client/src/generated/schema.ts` المتولّد لازم يطابق توليد جديد) |
| التلات تطبيقات بيشاركوه إزاي؟ | بيستوردوا `@matbakhy/api-client` (`packages/api-client/src/index.ts`، `createApiClient` فوق `openapi-fetch`)؛ المسارات والـ Bodies بتتفحص أنواعها مقابل الـ Schema المتولّد |

شوف [عقود الـ API](../04-codebase/api-contracts-ar.md) لمسار العمل لإضافة أو تغيير Endpoint.

## 3. قواعد العمل والـ Authorization والتسعير

| السؤال | فين |
|---|---|
| نص قاعدة العمل | `docs/product/03-business-rules.md` والنسخة المقروءة آليًا `planning/rules.json` (`BR-001`..`BR-064`)؛ شوف [قواعد العمل](../01-business/business-rules-ar.md) |
| القواعد بتتنفّذ فين في الكود؟ | في خدمات `internal` بتاعة الموديول المالك، ودايمًا من ناحية السيرفر. Quotes/orders/payments: `backend/modules/orders/src/main/java/com/matbakhy/orders/internal/` (`QuoteService.java`، `OrderService.java`، `OrderLifecycleService.java`، `PaymentService.java`)؛ المخزون: `backend/modules/availability/src/main/java/com/matbakhy/availability/internal/JdbcSlotInventory.java`؛ الكاش والـ Ledger: `backend/modules/finance/src/main/java/com/matbakhy/finance/internal/` |
| فلاتر وسلاسل الـ Authentication | `backend/app/src/main/java/com/matbakhy/app/security/SecurityConfiguration.java` (سلسلة Bearer للـ Native، وسلسلة OIDC Session مع CSRF للويب) |
| صلاحيات الموظفين | الـ enum `backend/modules/identity/src/main/java/com/matbakhy/identity/api/Permission.java`؛ بتتفحص بـ `@PreAuthorize("hasAuthority('PERM_...')")` على الـ Controllers؛ وفحص الـ MFA الحديث بـ `@staffAssurance.recentMfa(authentication)` (`backend/app/src/main/java/com/matbakhy/app/security/StaffAssurance.java`) |
| الـ Authorization على مستوى الـ Object (المالك / المطبخ / نطاق الحالة) | تحديد النطاق في SQL داخل الخدمات (مثلًا `customer_id = ?`، `kitchen_id = ?` في استعلامات القفل) و`AccessPolicy` (`backend/modules/identity/src/main/java/com/matbakhy/identity/api/AccessPolicy.java`) اللي ينفّذها `backend/app/src/main/java/com/matbakhy/app/security/ScopedAccessPolicy.java`؛ أي Object بتاع حد تاني بيرجّع 404 |
| تطبيق سياسة الـ MFA | `backend/app/src/main/java/com/matbakhy/app/security/MfaEnforcementFilter.java` + `backend/app/src/main/java/com/matbakhy/app/mfa/MfaPolicyService.java` |
| السعر بيتـ Validate فين؟ | وقت الـ Quote بيعمل `QuoteService.terms` (`backend/modules/orders/src/main/java/com/matbakhy/orders/internal/QuoteService.java:206`) إعادة حساب من الوجبات المنشورة؛ وعند تسجيل الأوردر `QuoteService.requireValid` (`:146`) بيعيد الحساب تاني وبيرفض الـ Quote القديمة بـ `PRICE_CHANGED`. أسعار العميل عمرها ما بتتقرا. الشرح التفصيلي: [تنفيذ التسعير](../04-codebase/pricing-implementation-ar.md) |
| الأوردر بيتسجّل فين؟ | `OrderService.place` (`backend/modules/orders/src/main/java/com/matbakhy/orders/internal/OrderService.java:131`) واللي بيناديها `OrderController.place` (`backend/modules/orders/src/main/java/com/matbakhy/orders/web/OrderController.java:61`). الشرح التفصيلي: [تنفيذ الأوردر](../04-codebase/order-implementation-ar.md) |
| العربون / الكاش / الـ Ledger فين؟ | [تنفيذ الدفع](../04-codebase/payment-implementation-ar.md) |

## 4. إعدادات البيانات والهوية

| السؤال | فين |
|---|---|
| الـ Database Migrations | `backend/app/src/main/resources/db/migration/V<n>__<name>.sql` (V1..V31 وقت الكتابة)؛ الـ Flyway بيشغّلهم عند الـ Startup. فحوصات التوافق: `scripts/check_migration_compat.py` |
| دور الـ Database بأقل صلاحيات | `backend/app/src/main/resources/db/migration/afterMigrate.sql`؛ وإنشاء الدور لبيئة المعاينة في `deploy/web-preview/postgres/20-runtime-role.sh` |
| بيانات التطوير بس | `backend/app/src/main/resources/db/devdata/` (بتتحمّل بس حيث الـ Profile اسمه `dev` مسموح: LOCAL وCI وPREVIEW؛ و`EnvironmentGuard` بيرفضها في STAGING/PRODUCTION) |
| الـ Keycloak Realm (محلي) | `infra/identity/matbakhy-realm.json` (الـ Clients `matbakhy-web`، `matbakhy-mobile`، `matbakhy-admin`؛ وـ Browser Flow مخصّص فيه "level 1"/"level 2" للـ MFA) |
| الـ Keycloak Realm (المعاينة/الـ Staging) | `deploy/web-preview/identity/matbakhy-realm.preview.json`، `deploy/web-preview/identity/mfa-migration.kcadm.sh`، `deploy/web-preview/identity/verify-mfa-flow.py` |
| الـ Keycloak Realm المستخدم في اختبارات الـ Backend | `backend/app/src/test/resources/keycloak/test-realm.json` عن طريق `backend/app/src/test/java/com/matbakhy/app/support/KeycloakTestServer.java` |
| إعدادات OIDC في الـ Backend | `matbakhy.identity.*` و`matbakhy.mfa.*` في `backend/app/src/main/resources/application.yaml`، ومربوطين بـ `backend/app/src/main/java/com/matbakhy/app/security/IdentityProperties.java` |
| أمان البيئة: حماية الـ Fixtures والـ Profile اسمه `dev` (LOCAL/CI/PREVIEW بس) | `backend/app/src/main/java/com/matbakhy/app/config/EnvironmentGuard.java` |
| قيود المزوّدين (SMS/scanner/notifications وهمية في LOCAL/CI/PREVIEW؛ أسرار التطوير والتخزين المحلي في LOCAL/CI بس؛ STAGING/PRODUCTION بترفض تشتغل من غير Adapters حقيقية) | `backend/app/src/main/java/com/matbakhy/app/providers/ProviderConfiguration.java` |

شوف كمان [الهوية والأمان](../03-architecture/identity-and-security-ar.md).

## 5. الشاشات

| السطح | الملفات |
|---|---|
| ويب العميل | التصفّح والفلاتر `apps/customer-web/src/Browse.tsx`؛ الوجبة `Meal.tsx`؛ المطبخ `Kitchen.tsx`؛ السلة + الـ Quote + تسجيل الأوردر `Checkout.tsx`؛ حالة العربون `Payment.tsx`؛ الأوردرات `Orders.tsx`؛ الملف الشخصي والعناوين والمفضّلة والصندوق وحالات الدعم `Account.tsx` (التحقق من الموبايل بيحصل جوه `Checkout.tsx`)؛ التحقق بخطوتين `Security.tsx`؛ الـ Hash Routes في `App.tsx` (`/`، `/meal/:id`، `/kitchen/:slug`، `/checkout`، `/orders`، `/orders/:id`، `/account`، `/support/:id`) |
| مساحة الطبّاخ | `apps/operations-web/src/cook/` — `Dashboard.tsx`، `Orders.tsx`، `Meals.tsx`، `Slots.tsx`، `Payments.tsx`، `Statement.tsx`، `Insights.tsx`، `Settings.tsx`، `Onboarding.tsx` |
| الـ Backoffice للموظفين | `apps/operations-web/src/ops/` — `Cases.tsx`، `Catalogue.tsx`، `Configuration.tsx`، `Finance.tsx`، `OpsOrders.tsx`، `Payments.tsx`، `Reviews.tsx`، `Security.tsx`، `Staff.tsx`؛ الأقسام اللي تظهر بيقرّرها `apps/operations-web/src/App.tsx` عن طريق استكشاف نداءات الـ API اللي السيرفر بيسمح بيها |
| موبايل العميل | التابات Discover/Favorites/Orders/Account والـ Stack Meal/Cart/Order/Notifications/Support، `apps/customer-mobile/src/screens/*.tsx`، وأنواع التنقّل `apps/customer-mobile/src/screens/navigation.ts` |
| تخزين الـ Session في الموبايل | `apps/customer-mobile/src/tokenStore.ts` (المخزن الآمن في نظام التشغيل، المفتاح `matbakhy.session.v1`)، `apps/customer-mobile/src/session.ts`، `apps/customer-mobile/src/basket.ts` (السلة وتسجيل الأوردر المعلّق، برضه في المخزن الآمن) |
| الـ Session في الويب | Session على السيرفر في PostgreSQL، والـ Cookie اسمه `MATBAKHY_SESSION`؛ وعلى ناحية العميل بس صدى الـ CSRF في `apps/customer-web/src/api.ts` |

## 6. "ألاقي اختبار موجود إزاي؟"

1. ابدأ من المتطلب: `planning/rules.json` (`BR-nnn`) → `scenario_ids` → `planning/scenarios.json` (`TC-nnn`).
2. دوّر على المعرّف: `grep -rn "TC-118" backend/app/src/test apps packages`. أغلب اختبارات التكامل في الـ Backend بتسمّي
   سيناريوهاتها في Javadoc الكلاس (مثلًا `OrderPlacementIntegrationTest` شايل TC-022..025 وTC-118).
   الملف `planning/scenarios.json` نفسه **مبيذكرش** ملفات الاختبارات؛ [مصفوفة التتبّع](../07-reference/requirements-traceability-ar.md)
   بتسجّل الربط اللي اتلاقى بالبحث ده.
3. أو ابدأ من الـ API: دوّر على نص الـ Route أو العملية في `backend/app/src/test/java/com/matbakhy/app/`.
4. حالات قبول المالك (هجين يدوي/آلي): `docs/quality/owner-acceptance/cases/*.json`؛ والحقل
   `automated_coverage` فيها بيسمّي كلاسات الاختبار المتداخلة معاها.
5. تقسيم الاختبارات: اختبارات التكامل في الـ Backend `backend/app/src/test/java/com/matbakhy/app/<area>/*IntegrationTest.java`
   (PostgreSQL حقيقي عن طريق Testcontainers، `backend/app/src/test/java/com/matbakhy/app/support/IntegrationTest.java`)؛
   اختبارات الوحدة/الـ Components للويب `apps/*/src/*.test.tsx`؛ الموبايل `apps/customer-mobile/src/*.test.ts(x)`؛ رحلات المتصفح
   `apps/operations-web/e2e/*.e2e.ts`؛ اختبارات العقد `packages/api-client/test/*.test.ts`.
   تشغيلها: [تشغيل الاختبارات](../05-developer-workflow/test-execution-ar.md).

## 7. "أنفّذ تغييري فين؟"

| التغيير | ابدأ من هنا | لمس كمان |
|---|---|---|
| Endpoint جديد أو متغيّر | الـ Controller في حزمة `web/` بتاعة الموديول المالك؛ والـ Service في `internal/` | `contracts/endpoints.json`، `contracts/openapi.yaml`، أعد توليد `packages/api-client/src/generated/schema.ts`، أضف Annotation الصلاحية، أضف اختبار تكامل، وصفحات الدليل حسب [سياسة التوثيق](../05-developer-workflow/documentation-policy-ar.md) |
| قاعدة تسعير أو Quote أو تسجيل أوردر | `QuoteService` / `OrderService` | `PriceChangeIntegrationTest`، `QuoteIntegrationTest`، `OrderPlacementIntegrationTest`؛ بيحتاج مراجعة معمارية (فلوس) |
| جدول أو عمود جديد | `V32__<name>.sql` جديد (عمرك ما تعدّل Migration اتطبّقت) | كود الـ Repository، `MigrationIntegrationTest`، `scripts/check_migration_compat.py`؛ شوف [معمارية قاعدة البيانات](../03-architecture/database-architecture-ar.md) |
| صلاحية موظف جديدة | الـ enum `Permission.java` + واجهة المنح في `apps/operations-web/src/ops/Staff.tsx` | `docs/product/06-permissions.md`، `StaffAccessIntegrationTest` |
| نص واجهة العميل / اللغة | `apps/customer-web/src/i18n.ts`، `apps/customer-mobile/src/i18n.ts`، `apps/operations-web/src/i18n.ts` | العربي والإنجليزي الاتنين |
| Design Token / لون | `packages/design-tokens/tokens.json` | `packages/design-tokens/test/tokens.test.ts`، `scripts/check_design.sh` |
| إصلاح عيب | اكتب الاختبار الفاشل الأول (في نفس مجلد المنطقة)، وصلّح في كود `internal` بتاع الموديول المالك | لو السلوك اتغيّر، حدّث صفحة الدليل وبتتطبّق قواعد `doc001-matrix.json` |

قاعدة صارمة: ممنوع أي تغيير في الـ Frontend يقرّر السعر أو السعة أو الدور أو انتقال الحالة؛ دي كلها ملك السيرفر
(شوف [دليل موديولات الـ Backend](../04-codebase/backend-module-guide-ar.md) لمناطق "ماتغيّرهاش من غير مراجعة معمارية").

## القيود المعروفة وفجوات التوثيق

- الملف `planning/scenarios.json` مفيهوش حقل لملف الاختبار؛ ربط السيناريو بالاختبار موجود بس كمعرّفات مكتوبة جوه ملفات الاختبارات.
- قائمة الـ Hash Routes لويب العميل واخدة من الـ Regular Expressions في `apps/customer-web/src/App.tsx`
  (السطور 150-153 و 213-222)؛ مفيش مستند جدول Routing.
