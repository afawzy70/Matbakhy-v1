# مصفوفة تتبّع المتطلبات

> **Owner role:** Testing and QA
> **Status:** PARTIALLY IMPLEMENTED — مصفوفة لـ 13 مسار حرج مع مراجع ملفات حقيقية؛ فجوات التغطية موسومة GAP ومسرودة في الآخر
> **Language / اللغة:** [English](requirements-traceability.md) · العربية

السلسلة المتتبَّعة في كل صف: **المتطلب** (`BR-nnn` في `planning/rules.json`، والقرار `Dnn` في `planning/decisions.json`) -> **صفحة رحلة المستخدم** ->
**عملية الـ API** (المعرّف في `contracts/endpoints.json`) -> **ملف التنفيذ** -> **كيان الـ Database / الـ Migration** -> **الاختبار الآلي** -> **حالة E2E للمالك**
(`docs/quality/owner-acceptance/cases/*.json`) -> **صفحة الدليل**.

الاصطلاحات المستخدمة في الخلايا:

- اختبارات الـ Backend بتتكتب `<area>/<Class>`، يعني `backend/app/src/test/java/com/matbakhy/app/<area>/<Class>.java` (وجودها كله اتفحص بـ `find`).
  معرّفات السيناريوهات (`TC-nnn`) هي اللي مكتوبة جوه الملفات دي؛ و`planning/scenarios.json` نفسه مابيسمّيش ملفات الاختبار.
- اختبارات الـ Frontend: `customer-web` = `apps/customer-web/src/App.test.tsx`، `operations-web` = `apps/operations-web/src/App.test.tsx`،
  `mobile` = `apps/customer-mobile/src/App.test.tsx` وإخوته، `e2e` = `apps/operations-web/e2e/*.e2e.ts`.
- مسارات التنفيذ نسبية إلى `backend/modules/<module>/src/main/java/com/matbakhy/<module>/` إلا لو اتذكر مسار كامل.
- حالات E2E للمالك هي حالات يدوية/هجينة نتايجها NOT_RUN أو BLOCKED في `docs/quality/owner-acceptance/generated/traceability.json`؛ والاستشهاد بواحدة معناه "فيه حالة معرَّفة"، مش "نجحت". `GAP` = ما اتلاقاش اختبار آلي أو حالة للمالك للخلية دي.
- صفحات الرحلات: [الـ Authentication والـ MFA](../02-user-journeys/authentication-and-mfa-ar.md)، [ويب العميل](../02-user-journeys/customer-web-ar.md)،
  [موبايل العميل](../02-user-journeys/customer-mobile-ar.md)، [عمليات الطبّاخ](../02-user-journeys/cook-operations-ar.md)،
  [أدمن الـ Backoffice](../02-user-journeys/backoffice-admin-ar.md)، [المالية والدعم](../02-user-journeys/finance-and-support-ar.md)،
  [رحلة الأوردر الكاملة](../02-user-journeys/complete-order-journey-ar.md).

## 1. الـ Authentication

| المتطلب | الرحلة | عملية الـ API | التنفيذ | الـ DB | الاختبار الآلي | E2E المالك | الدليل |
|---|---|---|---|---|---|---|---|
| BR-001 الحسابات النشطة الموثّقة بس هي اللي تعمل عمليات خاصة (TC-001, TC-109) | authentication-and-mfa | API-009 `GET /api/v1/me` وكل Route مش عام | `backend/app/src/main/java/com/matbakhy/app/security/SecurityConfiguration.java`, `AccountMapper.java`, `WebAccountFilter.java` | `app_user` (V1) | `identity/IdentityIntegrationTest`, `security/PreviewSecurityConfigurationTest` | CFG-002, CFG-004, E2E-DISC-003, E2E-OPS-062 | [الهوية والأمان](../03-architecture/identity-and-security-ar.md) |
| BR-002 CSRF للويب + PKCE للـ Native (TC-002, TC-131) | authentication-and-mfa | Routes الـ OIDC (بيديرها الـ Framework، مش في السجل)؛ API-010 `PATCH /api/v1/me` كعملية تعديل محمية بـ CSRF | `SecurityConfiguration.webChain`, `csrfTokenRepository`; `apps/customer-mobile/src/auth.ts` | جلسات الويب (V2) | `identity/IdentityIntegrationTest`, `security/CsrfCookieDefaultIntegrationTest`, `security/CsrfCookieDomainIntegrationTest`; الموبايل `apps/customer-mobile/src/auth.test.ts`, `signIn.test.ts` | CFG-002, CFG-003, CFG-036, CFG-037 | [الهوية والأمان](../03-architecture/identity-and-security-ar.md) |
| انتهاء الجلسة والإلغاء من جهة السيرفر | authentication-and-mfa | API-009 | `spring.session.timeout` في `backend/app/src/main/resources/application.yaml`; Spring Session JDBC | جلسات الويب (V2) | `identity/IdentityIntegrationTest` | CFG-004 | [الهوية والأمان](../03-architecture/identity-and-security-ar.md) |
| BR-048 توكنات الـ Native في التخزين الآمن لنظام التشغيل بس؛ وتسجيل الخروج بيمسح البيانات الخاصة (TC-048) | customer-mobile | n/a (Client) | `apps/customer-mobile/src/tokenStore.ts`, `session.ts` | n/a | الموبايل `auth.test.ts`, `App.test.tsx`; تشغيلات Native في `verification/runs/` | CFG-025, E2E-BASKET-027 | [دليل كود الموبايل](../04-codebase/mobile-code-guide-ar.md) |
| حدود Brute-force/rate عند تسجيل الدخول (BR-054, TC-054) | authentication-and-mfa | n/a (Keycloak) | Keycloak `bruteForceProtected` في `infra/identity/matbakhy-realm.json` | n/a | GAP (TC-054 NOT_RUN؛ مفيش اختبار لقفل Keycloak) | CFG-039 | [الهوية والأمان](../03-architecture/identity-and-security-ar.md) |

## 2. الـ Authorization (الأدوار والصلاحيات ونطاق الـ Object)

| المتطلب | الرحلة | عملية الـ API | التنفيذ | الـ DB | الاختبار الآلي | E2E المالك | الدليل |
|---|---|---|---|---|---|---|---|
| BR-006 كل قراءة لـ Object بتفحص النطاق؛ والـ Objects الغريبة بترد 404 (TC-006) | complete-order-journey | API-025 `GET /api/v1/orders/{orderId}`, API-054, API-037 | `security/ScopedAccessPolicy.java`; `OrderService.requireOwned` في `backend/modules/orders/src/main/java/com/matbakhy/orders/internal/OrderService.java:297`; SQL محدد النطاق في الـ Services | كل جداول الدومين فيها مفاتيح المالك/المطبخ | `identity/StaffAccessIntegrationTest` (مصفوفة نطاق المطبخ), `orders/OrderPlacementIntegrationTest` (أوردراته هو بس، والبحث بالـ Request), `support/cases/SupportIntegrationTest` | E2E-ORDER-120, CFG-005, E2E-OPS-055 | [الأطراف والصلاحيات](../01-business/actors-and-permissions-ar.md) |
| BR-007 صلاحيات الموظفين من جهة السيرفر؛ والأدوار في الـ Claims بتتتجاهل (TC-007) | backoffice-admin | API-091 `GET /api/v1/ops/staff`; `@PreAuthorize` على كل Route `/ops/**` | `identity/api/Permission.java`, `identity/internal/GrantService.java`, `AccountMapper` | `user_grant` (V4) | `identity/StaffAccessIntegrationTest` (حقول الدور في طلبات العميل بتترفض؛ والمنحة الملغاة بتقف مع أول Request بعدها) | E2E-OPS-058, E2E-OPS-102, CFG-005 | [الأطراف والصلاحيات](../01-business/actors-and-permissions-ar.md) |
| الصلاحية مقابل العقد (`x-permission`) | n/a | الكل | `contracts/openapi.yaml` `x-permission` | n/a | GAP: مفيش اختبار بيقارن `x-permission` بـ Annotation الـ Controller | GAP | [عقود الـ API](../04-codebase/api-contracts-ar.md) |
| BR-043 مفيش تصعيد ذاتي، وآخر أدمن محمي (TC-043) | backoffice-admin | API-092 `POST /api/v1/ops/staff/{userId}/grants`, API-093 | `GrantService` | `user_grant` (V4), `audit_event` | `identity/StaffAccessIntegrationTest.self_escalation_unknown_permissions_and_last_admin_are_refused` | E2E-OPS-060, E2E-OPS-101, CFG-105 | [رحلة أدمن الـ Backoffice](../02-user-journeys/backoffice-admin-ar.md) |

## 3. فصل حسابات العملاء

| المتطلب | الرحلة | عملية الـ API | التنفيذ | الـ DB | الاختبار الآلي | E2E المالك | الدليل |
|---|---|---|---|---|---|---|---|
| عميل واحد مايقدرش يقرا أو يلغي أو يبلّغ بدفع أو يقيّم أوردر عميل تاني (BR-006, BR-001) | complete-order-journey | API-025, API-026, API-027, API-100, API-029 | `OrderService` (تحديد النطاق بـ `customer_id = ?`), `OrderLifecycleService.lock`, `PaymentService.lockForCustomer` | `customer_order` (V11) | `orders/OrderPlacementIntegrationTest.customers_see_only_their_own_orders_with_paging_and_events`, `...another_customer_cannot_find_an_order_by_its_client_request_id` | E2E-ORDER-120 | [تنفيذ الأوردر](../04-codebase/order-implementation-ar.md) |
| تبديل الحساب على الويب مابيسيبش أثر (BR-044, BR-006) | customer-web | API-009 | معالجة الجلسة في `apps/customer-web/src/App.tsx`, `cart.ts` | n/a | اختبارات وحدة `customer-web` (CFG-006 بيسردها)؛ رحلة العميل في `e2e` | CFG-006, E2E-DISC-004 | [دليل كود الـ Frontend](../04-codebase/frontend-code-guide-ar.md) |
| تبديل الحساب على الـ Native بيمسح السلة والأوردر المعلّق والتوكنات (BR-048) | customer-mobile | API-009 | `apps/customer-mobile/src/AppContext.tsx` (`accountEpoch`), `basket.ts` | n/a | اختبارات `mobile` (TC-048, TC-049, TC-148); `apps/customer-mobile/src/ordering.test.ts` | E2E-BASKET-027, CFG-025 | [دليل كود الموبايل](../04-codebase/mobile-code-guide-ar.md) |
| طلب حذف الحساب بيعطّل الحساب فورًا (BR-055) | customer-web | API-017 `POST /api/v1/me/deletion-requests` | `identity/internal/AccountLifecycleService.java`, `AccountErasureService.java` | `deletion_request` (V3), V21, V27 | `privacy/AccountErasureIntegrationTest` | E2E-DISC-114, E2E-COOK-110 | [رحلة ويب العميل](../02-user-journeys/customer-web-ar.md) |

## 4. موافقة المطبخ

| المتطلب | الرحلة | عملية الـ API | التنفيذ | الـ DB | الاختبار الآلي | E2E المالك | الدليل |
|---|---|---|---|---|---|---|---|
| BR-008 الموافقة قبل البيع؛ نسخة طلب كاملة (Revision)؛ مراجع + سبب (TC-008, TC-112) | cook-operations / backoffice-admin | API-042..044 الطلب، API-067 القائمة، API-068 التفاصيل، API-069 `POST /api/v1/ops/cooks/{applicationId}/decision` | `backend/modules/cooks/src/main/java/com/matbakhy/cooks/internal/CookApplicationService.java`, `CookReviewService.java`, `web/CookReviewController.java` | `cook_application`, `cook_application_revision`, `kitchen` (V5) | `cooks/CookWorkflowIntegrationTest` (القرار محتاج مراجع محدد النطاق + MFA حديث؛ مفيش مراجعة ذاتية؛ القرارات Idempotent) | E2E-COOK-049, E2E-COOK-053 | [إدارة المطبخ](../01-business/kitchen-management-ar.md) |
| BR-009 الإيقاف المؤقت/التعليق بيوقف المبيعات الجديدة؛ والطبّاخ مايقدرش يلغي إيقاف الموظفين (TC-009) | cook-operations | API-065 `PATCH /api/v1/cook/settings`, API-070 `POST /api/v1/ops/kitchens/{kitchenId}/operating-state` | `cooks/internal/KitchenService.java` | `kitchen` (V5) | `cooks/CookWorkflowIntegrationTest`, `cooks/CookWorkspaceIntegrationTest` | E2E-COOK-107, E2E-OPS-108 | [إدارة المطبخ](../01-business/kitchen-management-ar.md) |
| BR-017 الوجبات المنشورة للمطابخ المعتمدة النشطة بس هي القابلة للشراء (TC-017) | customer-web | API-003, API-005, API-021 | `catalog/internal/discovery/DiscoveryService.java`, `QuoteService.terms` (`seller.canSell()`) | `meal`, `kitchen` | `catalog/DiscoveryIntegrationTest`, `orders/QuoteIntegrationTest` | E2E-DISC-005, E2E-DISC-104 | [إدارة المطبخ](../01-business/kitchen-management-ar.md) |
| واجهة العمليات للموافقة | backoffice-admin | API-067..069 | `apps/operations-web/src/ops/Reviews.tsx` | n/a | `operations-web` (طلبات `ops/cooks`)؛ `e2e` | E2E-COOK-049 | [دليل كود الـ Frontend](../04-codebase/frontend-code-guide-ar.md) |

## 5. تسجيل الأوردر

| المتطلب | الرحلة | عملية الـ API | التنفيذ | الـ DB | الاختبار الآلي | E2E المالك | الدليل |
|---|---|---|---|---|---|---|---|
| BR-022 التسجيل الذري: التحقق من عرض السعر، وحجز المخزون، وكتابة الـ Snapshots والأحداث والـ Outbox والالتزامات (TC-022, TC-118) | complete-order-journey | API-022 `POST /api/v1/orders` | `backend/modules/orders/src/main/java/com/matbakhy/orders/internal/OrderService.java:155` (`placeOnce`) | `customer_order`, `order_item`, `stock_allocation`, `order_event`, `outbox_event`, `cash_obligation` (V11) | `orders/OrderPlacementIntegrationTest` (الذرية، والـ Rollback عند فشل محقون) | E2E-ORDER-021, E2E-BASKET-018 | [تنفيذ الأوردر](../04-codebase/order-implementation-ar.md) |
| BR-023 `(customer, clientRequestId)` دائم؛ البصمة (Fingerprint) (TC-023) | complete-order-journey | API-022, API-024 `GET /api/v1/orders/by-request/{clientRequestId}` | `OrderService.fingerprint` (`:140`) | `request_fingerprint` (V29)، الفريد `customer_order_request_uq` (V11) | `orders/OrderPlacementIntegrationTest`, اختبار وحدة `orders/internal/OrderFingerprintTest` (المسار `backend/app/src/test/java/com/matbakhy/orders/internal/OrderFingerprintTest.java`) | E2E-ORDER-105, E2E-ORDER-106, E2E-ORDER-123 | [تنفيذ الأوردر](../04-codebase/order-implementation-ar.md) |
| BR-024 نطاق الـ Idempotency هو الفاعل + الأمر + المفتاح (TC-024) | complete-order-journey | API-022 (`Idempotency-Key`) | `backend/app/src/main/java/com/matbakhy/app/idempotency/JdbcIdempotentCommands.java:49` | `idempotency_record` (V1) | `orders/OrderPlacementIntegrationTest`؛ مفيش اختبار لمسح السجلات المنتهية: GAP | E2E-ORDER-022, E2E-ORDER-023, E2E-BASKET-028 | [تنفيذ الأوردر](../04-codebase/order-implementation-ar.md) |
| BR-044 الويب بيخزّن المفاتيح قبل الإرسال؛ والرد المجهول بيتحلّ بالبحث | customer-web / customer-mobile | API-022, API-024 | `apps/customer-web/src/Checkout.tsx:263`, `apps/customer-mobile/src/placement.ts` | n/a | اختبارات `customer-web`, `mobile` (TC-044, `ordering.test.ts`)؛ `e2e` | E2E-ORDER-122 (استلام Native) | [دليل كود الـ Frontend](../04-codebase/frontend-code-guide-ar.md) |

## 6. التسعير وعروض الأسعار اللي أسعارها اتغيّرت

| المتطلب | الرحلة | عملية الـ API | التنفيذ | الـ DB | الاختبار الآلي | E2E المالك | الدليل |
|---|---|---|---|---|---|---|---|
| BR-019 السيرفر بيحسب الجنيه كأرقام صحيحة؛ وأسعار الـ Client بتترفض (TC-019) | customer-web | API-021 `POST /api/v1/quotes` | `orders/internal/QuoteService.java:92`, `:206` | `quote`, `quote_item` (V10) | `orders/QuoteIntegrationTest`, `web/StrictJsonIntegrationTest` | E2E-BASKET-020, E2E-ORDER-113 | [تنفيذ التسعير](../04-codebase/pricing-implementation-ar.md) |
| BR-020 مدة صلاحية عرض السعر، من غير حجز مخزون، وبيحتفظ بـ Snapshot من الإصدارات (TC-020) | customer-web | API-021, API-022 | `QuoteService.create`, `requireValid` (`:146`) | `commerce_policy` (V10) | `orders/QuoteIntegrationTest` | E2E-BASKET-013, E2E-BASKET-029, E2E-ORDER-111 | [تنفيذ التسعير](../04-codebase/pricing-implementation-ar.md) |
| D13 عرض السعر اللي سعره اتغيّر بيترفض؛ وإعادة تأكيد عن علم (TC-154, TC-155, TC-156) | customer-web / customer-mobile | API-022 -> 409 `PRICE_CHANGED`; API-021 عرض سعر جديد | `QuoteService.requireValid`, `MealCatalog.lockPricing`, `packages/api-client/src/priceChanges.ts`, `apps/customer-web/src/Checkout.tsx:444` | `meal_revision` (V7) | `orders/PriceChangeIntegrationTest`; `packages/api-client/test/priceChanges.test.ts`; اختبارات `customer-web`, `mobile` | E2E-BASKET-014, E2E-BASKET-015, E2E-BASKET-022, E2E-ORDER-112, E2E-BASKET-026 | [تنفيذ التسعير](../04-codebase/pricing-implementation-ar.md) |
| BR-012 / D14 تاريخ الأسعار من نسخ (Revisions) غير قابلة للتعديل (TC-153) | cook-operations | API-099 `GET /api/v1/cook/meals/{mealId}/price-history`, API-048 | `catalog/internal/meal/MealService.java:186` | `meal_revision` (V7) | `catalog/PriceHistoryIntegrationTest` | E2E-COOK-103 | [تنفيذ التسعير](../04-codebase/pricing-implementation-ar.md) |
| D10 صفر عمولة ومفيش رسوم على العميل (TC-157) | all | API-021, API-089 | `OrderService.java:194`, V23 CHECK | `commerce_policy.commission_rate_bps` (V23) | `finance/ZeroCommissionIntegrationTest` | E2E-FIN-046, E2E-FIN-111 | [التسعير والدفع](../01-business/pricing-and-payments-ar.md) |

## 7. حجز السعة

| المتطلب | الرحلة | عملية الـ API | التنفيذ | الـ DB | الاختبار الآلي | E2E المالك | الدليل |
|---|---|---|---|---|---|---|---|
| BR-013 السعة محدودة على مستوى الوجبة-Slot والطبّاخ-Slot (TC-013) | cook-operations | API-051 `POST /api/v1/cook/slots`, API-052 | `backend/modules/availability/src/main/java/com/matbakhy/availability/internal/SlotService.java` | `cook_slot`, `meal_slot` (V8) | `availability/SlotIntegrationTest` | E2E-COOK-106 | [إدارة المطبخ](../01-business/kitchen-management-ar.md) |
| BR-025 آخر حصة متزامنة مابتتباعش زيادة (TC-025) | complete-order-journey | API-022 | `availability/internal/JdbcSlotInventory.java:34` (`reserve`) | `cook_slot`, `meal_slot`, `stock_allocation` | `orders/OrderPlacementIntegrationTest.twenty_customers_racing_for_the_last_portion_produce_exactly_one_order`, `...a_two_meal_order_is_refused_without_partial_reservation_...` | E2E-BASKET-016, E2E-BASKET-017, E2E-ORDER-114 | [تنفيذ الأوردر](../04-codebase/order-implementation-ar.md) |
| BR-029 الإفراج مرة واحدة بالظبط؛ والمستهلك يفضل مستهلك (TC-029) | complete-order-journey | API-027, API-056, API-062 | `OrderLifecycleService.release`, `move` | `stock_allocation` | `orders/OrderLifecycleIntegrationTest`, `reliability/RestartRecoveryIntegrationTest` (TC-058/137) | E2E-ORDER-027, E2E-ORDER-110, E2E-ORDER-028 | [تنفيذ الأوردر](../04-codebase/order-implementation-ar.md) |
| BR-014/015 جدولة بتوقيت القاهرة، وموعد القفل | cook-operations | API-051 | `availability/internal/CairoTime.java` | `cook_slot` (V8) | `availability/SlotIntegrationTest` | E2E-BASKET-023 | [إدارة المطبخ](../01-business/kitchen-management-ar.md) |

## 8. إلغاء الأوردر

| المتطلب | الرحلة | عملية الـ API | التنفيذ | الـ DB | الاختبار الآلي | E2E المالك | الدليل |
|---|---|---|---|---|---|---|---|
| BR-028 العميل بيلغي الأوردرات المعلّقة بس؛ وبعد كده بيبقى طلب (TC-028) | complete-order-journey | API-027 `POST /api/v1/orders/{orderId}/cancel`, API-028 `POST .../cancellation-requests` | `OrderLifecycleService.customerCancel` (`:84`); `support/internal/SupportService.java` | `customer_order`, `support_case` (V17) | `orders/OrderLifecycleIntegrationTest`, `support/cases/SupportIntegrationTest` | E2E-ORDER-027, E2E-ORDER-035 | [دورة حياة الأوردر](../01-business/order-lifecycle-ar.md) |
| الطبّاخ بيلغي قبل التحضير؛ الرفض؛ الانتهاء (TC-027, TC-029) | cook-operations | API-056 رفض، API-062 إلغاء | `OrderLifecycleService.cook` (`:104`), `expireOne` (`:237`), `OrderExpiryJob` | `customer_order` | `orders/OrderLifecycleIntegrationTest` | E2E-ORDER-110, E2E-ORDER-028 | [دورة حياة الأوردر](../01-business/order-lifecycle-ar.md) |
| إلغاء الأوردر استثنائيًا بواسطة العمليات بعد بدء التحضير (BR-028) | backoffice-admin | API-076 | `OrderLifecycleService.opsExceptionCancel` (`:188`) | `customer_order`, `audit_event` | `orders/OrderLifecycleIntegrationTest`؛ اختبار واجهة operations-web للإجراء: GAP | E2E-ORDER-109 | [دورة حياة الأوردر](../01-business/order-lifecycle-ar.md) |
| نتايج الإلغاء على العربون | finance-and-support | API-105, API-107, API-108 | `PaymentService.orderClosed` | `order_payment` | `payments/DepositPaymentIntegrationTest` (TC-160), `payments/PaymentReconciliationIntegrationTest` | E2E-FIN-107, E2E-FIN-115 | [تنفيذ الدفع](../04-codebase/payment-implementation-ar.md) |

## 9. الاستلام والتوصيل

| المتطلب | الرحلة | عملية الـ API | التنفيذ | الـ DB | الاختبار الآلي | E2E المالك | الدليل |
|---|---|---|---|---|---|---|---|
| BR-021 التوصيل محتاج تغطية المنطقة وعنوان العميل نفسه؛ والاستلام بس لو مفعّل (TC-021) | customer-web | API-021 | `QuoteService.terms` | `kitchen_zone`, `service_zone`, `address` (V3, V5) | `orders/QuoteIntegrationTest` | E2E-DISC-007, E2E-DISC-101 | [دورة حياة الأوردر](../01-business/order-lifecycle-ar.md) |
| BR-030 الاستلام بيكتمل من READY؛ والتوصيل بيتم إرساله الأول (TC-030) | cook-operations | API-058 جاهز، API-059 إرسال، API-060 تسليم، API-061 فشل التوصيل | `OrderLifecycleService.cook` الحالات `dispatch`, `delivery_failure` | `customer_order` (V11, V12) | `orders/OrderLifecycleIntegrationTest` | E2E-ORDER-031, E2E-ORDER-032, E2E-ORDER-034, E2E-ORDER-101, E2E-ORDER-102 | [دورة حياة الأوردر](../01-business/order-lifecycle-ar.md) |
| BR-031 كود تسليم لاستخدام واحد، ومحاولات محدودة، وتجاوز العمليات بـ MFA جديد (TC-031) | cook-operations | API-060, API-077 | `backend/modules/orders/src/main/java/com/matbakhy/orders/internal/HandoverCodes.java`, `OrderLifecycleService.handover` (`:387`) | `handover_challenge`, `handover_attempt` (V12) | `orders/OrderLifecycleIntegrationTest` | E2E-ORDER-030, E2E-ORDER-033, E2E-ORDER-108 | [دورة حياة الأوردر](../01-business/order-lifecycle-ar.md) |
| التوصيل على تطبيقات الـ Native | customer-mobile | API-021, API-022 | `apps/customer-mobile/src/screens/Cart.tsx` | n/a | اختبارات `mobile`؛ Fixture التوصيل الـ Native `apps/customer-mobile/e2e-native/delivery-address.sql` (تغطية التشغيل الـ Native NOT VERIFIED هنا) | GAP (الاستلام بس هو المذكور: E2E-ORDER-122) | [دليل كود الموبايل](../04-codebase/mobile-code-guide-ar.md) |

## 10. العربون

| المتطلب | الرحلة | عملية الـ API | التنفيذ | الـ DB | الاختبار الآلي | E2E المالك | الدليل |
|---|---|---|---|---|---|---|---|
| D16 شروط العربون في عرض السعر وSnapshot الدفع غير القابل للتعديل (TC-158) | customer-web | API-021 (`paymentMethod`)، API-102/103 للـ Payment profile | `PaymentService.terms` (`:205`), `createFor` (`:220`) | `kitchen_payment_profile`, `order_payment` (V24) | `payments/DepositPaymentIntegrationTest` | E2E-FIN-039, E2E-FIN-041, E2E-FIN-104, E2E-FIN-105 | [تنفيذ الدفع](../04-codebase/payment-implementation-ar.md) |
| المستلم لازم يتوافق عليه من الموظفين بـ MFA حديث | finance-and-support | API-104 `POST /api/v1/ops/kitchens/{kitchenId}/payment-recipient/approval`, API-109 | `PaymentService.approveRecipient` (`:167`) | `kitchen_payment_profile.recipient_status` | `payments/DepositPaymentIntegrationTest` | E2E-COOK-108, E2E-FIN-103 | [تنفيذ الدفع](../04-codebase/payment-implementation-ar.md) |
| تبليغ العميل بالتحويل مش دفع؛ ومفيش تحضير قبل استلام المطبخ (TC-159) | customer-web | API-100, API-101, API-057 | `PaymentService.reportTransfer`, `kitchenReceipt`, `requireReadyForPreparation` | `order_payment`, `payment_event` (V24) | `payments/DepositPaymentIntegrationTest` | E2E-FIN-106 | [تنفيذ الدفع](../04-codebase/payment-implementation-ar.md) |
| لم يُستلم / عدم تطابق؛ التزام الاسترداد (TC-161, TC-162) | finance-and-support | API-105..108 | `PaymentService.reportProblem`, `decide`, `reportRefund` | V25, V28 | `payments/PaymentReconciliationIntegrationTest` | E2E-FIN-107, E2E-FIN-108 | [تنفيذ الدفع](../04-codebase/payment-implementation-ar.md) |
| صياغة دفع صادقة على الويب والـ Native (TC-163) | customer-web / customer-mobile | API-100 | `apps/customer-web/src/Payment.tsx`, `apps/customer-mobile/src/screens/Payment.tsx` | n/a | اختبارات `customer-web`, `mobile` | E2E-FIN-113, E2E-FIN-101 | [رحلة ويب العميل](../02-user-journeys/customer-web-ar.md) |

## 11. تسوية الكاش

| المتطلب | الرحلة | عملية الـ API | التنفيذ | الـ DB | الاختبار الآلي | E2E المالك | الدليل |
|---|---|---|---|---|---|---|---|
| BR-032 الإتمام والتحصيل مستقلين؛ تبليغ الطبّاخ مقابل تحقق المالية (TC-032) | finance-and-support | API-063 `POST /api/v1/cook/orders/{orderId}/cash-reports`, API-082, API-083 | `backend/modules/finance/src/main/java/com/matbakhy/finance/internal/CashService.java:72`, `:125` | `cash_obligation` (V11), `cash_report` (V13) | `finance/CashAndLedgerIntegrationTest` | E2E-FIN-038, E2E-FIN-043, E2E-FIN-102, E2E-ORDER-101 | [تنفيذ الدفع](../04-codebase/payment-implementation-ar.md) |
| فصل المهام: اللي بلّغ مايقدرش يتحقق من تبليغه | finance-and-support | API-083 | `CashService.refuseSelfVerification` (`:115`) | `cash_report` | `finance/CashAndLedgerIntegrationTest` | E2E-FIN-114 | [تنفيذ الدفع](../04-codebase/payment-implementation-ar.md) |
| BR-033/034 دفتر أستاذ متوازن للإضافة فقط بيتسجّل مرة واحدة؛ عمولة مستحقة (TC-033, TC-034) | finance-and-support | API-086 كشوف الحساب | `finance/internal/LedgerService.java:31`, `LedgerPostingJob.java` | `ledger_group`, `ledger_entry` (V13) | `finance/CashAndLedgerIntegrationTest`, `finance/FinanceOperationsIntegrationTest` | E2E-FIN-045 | [تنفيذ الدفع](../04-codebase/payment-implementation-ar.md) |
| BR-035/036 الاستردادات محدودة بالتحصيل المتحقق منه؛ والتسويات غير قابلة للتعديل (TC-035, TC-036) | finance-and-support | API-084, API-085, API-087, API-088 | `finance/internal/FinanceOpsService.java:67`, `:184` | `refund`, `settlement_batch`, `settlement_line` (V18) | `finance/FinanceOperationsIntegrationTest` | E2E-FIN-044 | [تنفيذ الدفع](../04-codebase/payment-implementation-ar.md) |
| كشف حساب الطبّاخ مابيعرضش عمولة (D10) | cook-operations | API-064 | `finance/internal/StatementService.java` | جداول الدفتر | `finance/ZeroCommissionIntegrationTest` | E2E-FIN-111 | [التسعير والدفع](../01-business/pricing-and-payments-ar.md) |
| واجهة تبليغ الكاش مغطّاة باختبار وحدة للواجهة | cook-operations | API-063 | `apps/operations-web/src/cook/Orders.tsx` | n/a | GAP: مفيش نص `cash-reports` في `apps/operations-web/src/App.test.tsx` (التدفق ظاهر في `e2e/cook-workspace.e2e.ts`) | E2E-FIN-038 | [دليل كود الـ Frontend](../04-codebase/frontend-code-guide-ar.md) |

## 12. MFA

| المتطلب | الرحلة | عملية الـ API | التنفيذ | الـ DB | الاختبار الآلي | E2E المالك | الدليل |
|---|---|---|---|---|---|---|---|
| D18 / BR-007 سياسة لكل فئة: DISABLED, OPTIONAL, REQUIRED؛ الأكثر حماية هو اللي بيكسب؛ الصف الناقص بيفشل بأمان إلى REQUIRED | authentication-and-mfa | API-112 `GET /api/v1/me/security`, API-114, API-115 | `backend/app/src/main/java/com/matbakhy/app/mfa/MfaPolicyService.java` (`Mode`, resolve) | `mfa_policy`, `user_mfa_preference` (V31) | `mfa/MfaPolicyResolveTest`, `mfa/MfaPolicyIntegrationTest`؛ مفيش `TC-nnn` موجود لـ D18: GAP في `planning/scenarios.json` | CFG-101, CFG-011, CFG-012, CFG-014, CFG-015, CFG-033 | [الهوية والأمان](../03-architecture/identity-and-security-ar.md) |
| الإنفاذ على الاستدعاءات المحمية؛ وStep-up مع Keycloak | authentication-and-mfa | كل الـ Routes المحمية؛ `STEP_UP_REQUIRED` | `security/MfaEnforcementFilter.java`, `security/StaffAssurance.java` | n/a | `identity/MfaIdpTest` (Keycloak حقيقي)، `security/StaffAssuranceTest`; اختبارات رابط Step-up في `customer-web`؛ اختبارات Step-up في `mobile` | CFG-102, CFG-024, CFG-108, CFG-109 | [رحلة الـ Authentication والـ MFA](../02-user-journeys/authentication-and-mfa-ar.md) |
| تفضيل المستخدم (يقدر يضيف شرط، مايشيلوش أبدًا) | authentication-and-mfa | API-113 `PUT /api/v1/me/security/mfa` | `mfa/MfaController.java` | `user_mfa_preference` (V31) | `mfa/MfaPolicyIntegrationTest` | CFG-008 | [رحلة الـ Authentication والـ MFA](../02-user-journeys/authentication-and-mfa-ar.md) |
| إعادة ضبط الأدمن بتشيل العامل، وتنهي الجلسات، ومتدقَّقة | backoffice-admin | API-116 `POST /api/v1/ops/accounts/{accountId}/mfa-reset` | `mfa/MfaAdminController.java`, `mfa/KeycloakAdmin.java` | `user_mfa_reset` (V31), `audit_event` | `identity/MfaResetIntegrationTest`, `mfa/MfaResetAuditIntegrationTest` | CFG-018, CFG-019, CFG-020, CFG-021, CFG-023 | [رحلة الـ Authentication والـ MFA](../02-user-journeys/authentication-and-mfa-ar.md) |
| Step-up على Android في جهاز فعلي | customer-mobile | API-113 | `apps/customer-mobile/src/auth.ts` (`acr_values`) | n/a | GAP (اختبارات الأجهزة ماتشغلتش؛ BLK-004) | CFG-112 (نص الحالة: مفيش خطة في الـ Repository لحد دلوقتي) | [دليل كود الموبايل](../04-codebase/mobile-code-guide-ar.md) |
| ترحيل الـ Realm لتدفق MFA | infrastructure | n/a | `deploy/web-preview/identity/mfa-migration.kcadm.sh`, `deploy/web-preview/migrate-mfa.sh` | n/a | `scripts/test_keycloak_mfa_migration.py` | CFG-027, CFG-028, CFG-029, CFG-031 | [البنية التحتية والـ Deployment](../03-architecture/infrastructure-and-deployment-ar.md) |

## 13. الصلاحيات الإدارية والإعدادات

| المتطلب | الرحلة | عملية الـ API | التنفيذ | الـ DB | الاختبار الآلي | E2E المالك | الدليل |
|---|---|---|---|---|---|---|---|
| BR-042 تغييرات الرسوم/النسب/المناطق/السياسات متعمل لها إصدارات، ومبرَّرة، ومتدقَّقة؛ وعروض الأسعار الجديدة بس هي اللي بتستخدمها (TC-042) | backoffice-admin | API-089 `GET /api/v1/ops/configuration`, API-090 `POST /api/v1/ops/configuration/{key}/versions` | `orders/internal/ConfigurationService.java:85` | `commerce_policy`, `config_change`, `feature_flag` (V19) | `orders/ConfigurationIntegrationTest`, `orders/PublicConfigurationIntegrationTest` | E2E-OPS-061, E2E-OPS-106, E2E-BASKET-024 | [رحلة أدمن الـ Backoffice](../02-user-journeys/backoffice-admin-ar.md) |
| صلاحيات الموظفين محتاجة سبب + MFA حديث، ومتدقَّقة (BR-043, TC-111) | backoffice-admin | API-092, API-093 | `identity/web/StaffController.java`, `GrantService` | `user_grant`, `audit_event` | `identity/StaffAccessIntegrationTest` (`grant_changes_require_recent_mfa`)؛ اختبار واجهة المنح: GAP (موجود في `e2e/cook-workspace.e2e.ts`, `operations-layout.e2e.ts` بس) | E2E-OPS-059, E2E-OPS-102, CFG-104 | [الأطراف والصلاحيات](../01-business/actors-and-permissions-ar.md) |
| ظهور الأقسام بيتبع منح السيرفر | backoffice-admin | API-067, 071, 078, 074, 086, 089, 091, 112 | `apps/operations-web/src/App.tsx` (`checkSession`) | n/a | اختبارات `operations-web`; `e2e/operations-layout.e2e.ts` | E2E-OPS-055, E2E-FIN-112 | [دليل كود الـ Frontend](../04-codebase/frontend-code-guide-ar.md) |
| قراءة سجل التدقيق؛ التصدير | backoffice-admin | API-094 (IMPLEMENTED), API-095 (PLANNED) | `operations/web/AuditController.java` | `audit_event` (V4) | `observability/AuditAndCorrelationReadinessIntegrationTest`, `identity/StaffAccessIntegrationTest.audit_is_append_only_and_redacted`؛ التصدير: GAP (مش متنفذ) | E2E-OPS-110 | [الهوية والأمان](../03-architecture/identity-and-security-ar.md) |
| صحة التكاملات وإعادة محاولة الـ Outbox | backoffice-admin | API-096, API-097 | `notifications/web/NotificationController.java` | `outbox_event` (V11) | `notifications/NotificationIntegrationTest` | GAP (مالقيناش حالة للمالك بالبحث بالعملية) | [التكامل والأحداث](../03-architecture/integration-and-events-ar.md) |

## قائمة فجوات التغطية

كل GAP فوق، زائد فجوات اتلاحظت أثناء التحقق:

1. مفيش معرّف سيناريو لـ D18 (سياسة MFA/الإنفاذ/إعادة الضبط). الاختبارات موجودة (`mfa/*`، `identity/MfaResetIntegrationTest`)، لكن `planning/scenarios.json` مفيهوش `TC-nnn` ليهم، فتتبّع القاعدة/السيناريو لجزء D18 في BR-007 بيتم بحالات المالك بس.
2. `x-permission` في `contracts/openapi.yaml` مش بيتقارن بـ Annotations `@PreAuthorize` في الـ Controllers؛ المتختبر بس هو تساوي السجل مع OpenAPI ومجموعات الـ Routes.
3. تحديد محاولات الدخول معتمد على Keycloak `bruteForceProtected`؛ ومفيش اختبار في الـ Repository ده بيشغّله (TC-054 NOT_RUN، MB-036 TODO).
4. مسح سجلات الـ Idempotency: مفيش كود أو اختبار بيشيل صفوف `idempotency_record` المنتهية.
5. تغطية اختبارات الوحدة على مستوى الواجهة ناقصة لـ: إرسال تبليغ الكاش (`apps/operations-web/src/cook/Orders.tsx`)، والإلغاء الاستثنائي للأوردر بواسطة العمليات، وشاشات منح/سحب صلاحيات الموظفين (مغطّاة برحلات Playwright بس).
6. Step-up على Android الـ Native والأوردر بنمط التوصيل على الـ Native مفيش لهم اختبار آلي مسمّى في الـ Repository (اختبارات أجهزة، BLK-004؛ E2E-ORDER-122 بيسمّي الاستلام بس).
7. صحة التكاملات / إعادة محاولة الـ Outbox ليهم اختبار Backend لكن مفيش حالة E2E للمالك في `docs/quality/owner-acceptance/cases/*.json`.
8. 161 من 200 حالة للمالك ملهاش ربط بسيناريو في `docs/quality/owner-acceptance/generated/traceability.json` (`case_to_scenarios`)، والـ 200 كلهم NOT_RUN أو BLOCKED: قبول المالك للمسارات دي **مش** متثبت؛ الموجود اختبارات آلية وسجلات تحقق مبلّغ عنها ذاتيًا بس.
9. إمكانية الوصول (BR-047): TC-047 BLOCKED (قارئ الشاشة)؛ اختبارات لوحة المفاتيح موجودة (`e2e/keyboard.e2e.ts`).
10. الأداء (BR-059, TC-059)، والاستعادة على الـ Staging (BR-057, TC-057)، وعمليات التجربة الأولى (BR-061..064) كلها NOT_RUN.

## القيود المعروفة وفجوات التوثيق

- قيم `status: PASS` للسيناريوهات في `planning/scenarios.json` مبلّغ عنها ذاتيًا (شوف `docs/status/latest.md`: "not an independent review certificate")؛ والمصفوفة دي مابتعيدش تشغيلها.
- الصفوف بتسرد أنسب الاختبارات، مش كل اختبار بيلمس السلوك.
