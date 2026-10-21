# رحلات المستخدم: عمليات الطبّاخ (من التسجيل لحد الإيقاف)

> **Owner role:** Frontend and mobile
> **Status:** PARTIALLY IMPLEMENTED — مساحة عمل الطبّاخ (الأوردرات، الوجبات، الـ Slots، الإعدادات، ملف الدفع، كشف الحساب، الإحصائيات) ومسار موافقة الـ Staff شغّالين من خلال الـ API والويب؛ لكن نموذج طلب الطبّاخ له API ومفيش له شاشة.
> **Language / اللغة:** [English](cook-operations.md) · العربية

مساحة عمل الطبّاخ هي المجموعة الأولى من تبويبات **تطبيق ويب العمليات** (`apps/operations-web`)، ومشتركة مع تبويبات الـ Staff ([إدارة الـ Backoffice](backoffice-admin-ar.md)). مين هو الطبّاخ بيتقرر على السيرفر: حساب **بيملك مطبخ موافَق عليه** (`KitchenService.ownedBy` في `backend/modules/cooks/src/main/java/com/matbakhy/cooks/internal/KitchenService.java`). مفيش Claim للطبّاخ كدور في الـ Tokens (`AccountMapper` بيتجاهل أدوار الـ Token). الخلفية البيزنس: [إدارة المطبخ](../01-business/kitchen-management-ar.md) و[دورة حياة الأوردر](../01-business/order-lifecycle-ar.md).

## خريطة الشاشات (`apps/operations-web/src/App.tsx`)

عند التحميل `checkSession` بينادي `GET /api/v1/me`، وبعدين بيجرّب `GET /api/v1/cook/dashboard` وعدة Endpoints للـ Staff بالتوازي. الرد `403` من الـ Dashboard معناه "مفيش مطبخ موافَق عليه". تبويبات الطبّاخ هي `dashboard, orders, meals, slots, statement, insights, settings` (الثابت `COOK_TABS`)؛ و`security` بيتضاف لكل حد عنده قسم واحد على الأقل. ولو مفيش مطبخ ولا صلاحية Staff الحساب بيشوف لوحة التسجيل بدالها (`apps/operations-web/src/cook/Onboarding.tsx`)، واللي بتعرض حالة الطلب بس (`GET /api/v1/cook/application`؛ 404 = "مفيش طلب").

| التبويب | الملف | بيعمل إيه |
|---|---|---|
| dashboard | `apps/operations-web/src/cook/Dashboard.tsx` | أعداد الطابور، ومواعيد القبول النهائية، وإجماليات الـ Slots الجاية (`GET /api/v1/cook/dashboard`) |
| orders | `cook/Orders.tsx` | الأوردرات الفعّالة / المكتملة؛ الإجراءات الموجودة في `allowedActions` اللي جاية من السيرفر بس؛ قوايم اختيار الأسباب؛ خانة كود التسليم (6 أرقام)؛ مرجع التوصيل (Dispatch)؛ تقرير الكاش بعد الاكتمال |
| meals | `cook/Meals.tsx` | عرض، إنشاء، تعديل، رفع صورة، نشر / إيقاف مؤقت / أرشفة، سجل الأسعار |
| slots | `cook/Slots.tsx` | إنشاء، تعديل السعة، تغيير الحالة |
| statement | `cook/Statement.tsx` | الأوردرات المكتملة، حالات الكاش، من غير عمولة (`GET /api/v1/cook/statements`) |
| insights | `cook/Insights.tsx` | أعداد الزيارات/المشاهدات/الإضافة للسلة العضوية (Organic) لمطبخه هو (`GET /api/v1/cook/insights`) |
| settings | `cook/Settings.tsx`, `cook/Payments.tsx` | الوصف، الاستلام، مناطق التوصيل، إيقاف/استئناف، ملف دفع العربون |

المشترك في كل الرحلات في الصفحة دي: **الطرف** حساب بمطبخ موافَق عليه (طبّاخ) إلا لو اتذكر غير كده؛ وكل نداء `/api/v1/cook/**` بيحمّل مطبخ المتصل وبيرفض أي كائن بتاع مطبخ تاني (`FORBIDDEN "not a cook"` أو 404؛ E2E-COOK-054). **وسيلة النقل** هي Cookie الـ Session على الويب + Header الـ CSRF، زي ويب العميل (`apps/operations-web/src/api.ts`). **الاختبارات (كلها):** `apps/operations-web/src/App.test.tsx` (مجموعة "operations-web cook workspace")، واختبارات Playwright على API حقيقي `apps/operations-web/e2e/cook-workspace.e2e.ts` و`money-reflow.e2e.ts` و`keyboard.e2e.ts` و`operations-layout.e2e.ts` (بتتشغّل من خلال `scripts/dev.sh e2e`؛ النتايج للـ Commit الحالي: NOT VERIFIED هنا). أسماء حالات الـ Domain في [دورة حياة الأوردر](../01-business/order-lifecycle-ar.md).

## CO-1 التسجيل: التقديم، الإرسال، انتظار القرار

1. **الهدف.** حساب عميل بيطلب يبقى طبّاخ؛ والـ Staff بيتحققوا تشغيليًا (مش شهادة سلامة غذاء).
2. **الطرف / الصلاحيات.** أي حساب فعّال (`COOK_APPLICANT` في `contracts/endpoints.json`)؛ وجانب الـ Staff محتاج `cook.review`.
3. **المتطلبات المسبقة.** مسجّل دخول. تليفون الطلب بيستخدم النمط `+20 1[0125]xxxxxxxx` (قيد CHECK في V5).
4. **شاشة البداية.** مفيش شاشة بتبدأ الطلب. API بس: `PUT /api/v1/cook/application`. ويب العمليات بيعرض الحالة بعد كده (`Onboarding.tsx`). حالة النموذج: واجهة PLANNED (التعليق في `Onboarding.tsx` بيقول إن التعديل "a later customer-web/mobile flow"؛ ومفيش نداء لـ `/cook/application` في `apps/customer-web/src` ولا `apps/customer-mobile/src`).
5. **إجراءات المستخدم.** حفظ مسودة (الاسم، النبذة، تليفون التواصل، الموقع الخاص، علم الاستلام، معرّفات المناطق، معرّفات رفع المستندات، `expectedVersion`)؛ رفع مستندات خاصة من خلال `POST /api/v1/uploads` + `POST /api/v1/uploads/{uploadId}/complete`؛ الإرسال.
6. **الاستجابات.** المسودة بترجّع الحالة والتاريخ؛ والإرسال بيعمل Revision رقم 1؛ والمسودات الناقصة بترجّع `VALIDATION_FAILED` بتسمّي الحقول الناقصة؛ والمتقدّم بيفضل غير طبّاخ لحد الموافقة (E2E-COOK-048).
7. **الـ API.** `GET/PUT /api/v1/cook/application` (API-042، API-043)، `POST /api/v1/cook/application/submit` (API-044)، الرفع (من API-039 لـ API-041).
8. **الـ Backend.** `backend/modules/cooks/src/main/java/com/matbakhy/cooks/web/CookApplicationController.java` → `internal/CookApplicationService.java`؛ المستندات `internal/ApplicationDocumentAccess.java`؛ الرفع `backend/modules/catalog/src/main/java/com/matbakhy/catalog/web/UploadController.java`.
9. **قاعدة البيانات.** `cook_application`، `cook_application_revision` (V5)، `media_asset` (V6).
10. **الانتقالات.** `DRAFT → SUBMITTED` (الحالات القابلة للتعديل: المسودة وطلب التعديلات)؛ الـ Staff: `SUBMITTED → UNDER_REVIEW → APPROVED | CHANGES_REQUESTED | REJECTED`؛ وإعادة الإرسال بتعمل الـ Revision اللي بعده (E2E-COOK-111).
11. **الأخطاء.** `VERSION_CONFLICT` على `expectedVersion` قديم؛ `STATE_CONFLICT` لما مش قابل للتعديل؛ المستندات الخاصة مقروءة للمالك والـ Staff المراجعين بس (E2E-COOK-053).
12. **الاختبارات.** `backend/app/src/test/java/com/matbakhy/app/cooks/CookWorkflowIntegrationTest.java`، `backend/app/src/test/java/com/matbakhy/app/media/MediaIntegrationTest.java`.
13. **اختبارات المالك E2E.** E2E-COOK-048، E2E-COOK-053، E2E-COOK-111؛ J6 للموافقة.
14. **الحالة.** الـ API IMPLEMENTED؛ واجهة المتقدّم PLANNED / مش موجودة.
15. **القيود.** المتقدّمين مايقدروش يتسجلوا من غير وصول للـ API؛ وبالتالي حالة المالك E2E-COOK-048 هي حالة على مستوى الـ API.

## CO-2 موافقة الـ Staff وإنشاء المطبخ

1. **الهدف.** المراجع بيوافق على الطلب؛ وبيتعمل مطبخ بـ Slug عام.
2. **الطرف / الصلاحيات.** الـ Staff اللي معاهم `cook.review`؛ والقرارات محتاجة كمان **MFA حديث** (`@staffAssurance.recentMfa`). المراجع مايقدرش يقرر في طلبه هو (الرفض متسجّل في الـ Audit).
3. **المتطلبات المسبقة.** طلب مُرسل؛ MFA المراجع ضمن `OIDC_STEP_UP_MAX_AGE` (الافتراضي 10 دقايق).
4. **البداية.** تبويب "reviews" في ويب العمليات (`apps/operations-web/src/ops/Reviews.tsx`).
5. **الإجراءات.** فتح الطابور، وفتح طلب وRevision (المستندات الخاصة من خلال `GET /api/v1/uploads/{uploadId}`)، وSTART_REVIEW، وبعدين APPROVE / REQUEST_CHANGES / REJECT مع سبب (من 3 لـ 1000 حرف).
6. **الاستجابات.** المراجع بيشوف الـ Revisions والقرارات؛ وعند الموافقة بيظهر مطبخ ونداء `GET /api/v1/cook/dashboard` الجاي للمتقدّم بينجح.
7. **الـ API.** `GET /api/v1/ops/cooks` (API-067)، `GET /api/v1/ops/cooks/{applicationId}` (API-068، متسجّل في الـ Audit)، `POST /api/v1/ops/cooks/{applicationId}/decision` (API-069، `Idempotency-Key`، `expectedVersion`، `revision`).
8. **الـ Backend.** `CookReviewController.java` → `CookReviewService.java` (`refuseSelfReview`، `decide`، `createFromApproval` في `KitchenService`).
9. **قاعدة البيانات.** `cook_application`، `cook_application_revision`، `kitchen`، `kitchen_zone` (V5)، `audit_event` (V4).
10. **الانتقالات.** زي CO-1؛ `START_REVIEW` من `SUBMITTED` بس؛ والقرارات من `UNDER_REVIEW` بس؛ والمطبخ `operating_state = ACTIVE` عند الإنشاء.
11. **الأخطاء.** `FORBIDDEN` مراجعة النفس؛ `VERSION_CONFLICT`؛ `STATE_CONFLICT` Revision قديم؛ `STEP_UP_REQUIRED` من غير MFA حديث.
12. **الاختبارات.** `backend/app/src/test/java/com/matbakhy/app/cooks/CookWorkflowIntegrationTest.java`، `backend/app/src/test/java/com/matbakhy/app/security/StaffAssuranceTest.java`، `apps/operations-web/src/App.test.tsx`.
13. **اختبارات المالك E2E.** E2E-COOK-049، E2E-OPS-108، E2E-OPS-109، E2E-COOK-111؛ J6 (محتاج الـ Deployment المحدّث وMFA الـ Staff).
14. **الحالة.** IMPLEMENTED.
15. **القيود.** واجهة المراجعة معتمدة على إن الـ MFA بتاع حساب الـ Staff يبقى قابل للاستخدام عند Keycloak (الـ Step-up في المعاينة بيعتمد على الـ Migration بتاع الـ Realm، CFG-028).

## CO-3 الوجبات والتسعير

1. **الهدف.** إنشاء وجبة، وتسعيرها، ونشرها؛ وكل تغيير سعر بيتسجّل.
2. **الطرف.** الطبّاخ (مطبخه هو).
3. **المتطلبات المسبقة.** مطبخ موافَق عليه وفعّال. النشر محتاج إقرار مسببات الحساسية كامل (`CONTAINS` بيطلب أكواد)، وسعر من 1 لـ 1,000,000 قرش (piastres)، وصورة واحدة على الأقل موافَق عليها (بعد الإشراف).
4. **البداية.** تبويب "meals".
5. **الإجراءات.** وجبة جديدة (العنوان والوصف بالعربي والإنجليزي (Arabic/English)، التصنيف، السعر بالجنيه (EGP) متحوّل لوحدات صغرى صحيحة بواسطة `egpToMinor` في `apps/operations-web/src/cook/shared.tsx`، مسببات الحساسية، الصور)؛ رفع صورة (تذكرة ← رفع ← إكمال)؛ نشر (Publish)، إيقاف مؤقت (Pause)، أرشفة (Archive)؛ عرض سجل الأسعار.
6. **الاستجابات.** كل متطلب نشر ناقص بيتسمّى (E2E-COOK-101)؛ والعميل بيشوف الوجبة بعد النشر وبعد ما الإشراف يسمح بس (E2E-COOK-051).
7. **الـ API.** `GET/POST /api/v1/cook/meals` (API-046، API-047)، `PATCH /api/v1/cook/meals/{mealId}` (API-048)، `POST /api/v1/cook/meals/{mealId}/publication` والجسم `{action: PUBLISH|PAUSE|ARCHIVE, expectedVersion}` (API-049)، `GET /api/v1/cook/meals/{mealId}/price-history` (API-099)، الرفع (API-039/040).
8. **الـ Backend.** `backend/modules/catalog/src/main/java/com/matbakhy/catalog/web/MealController.java` → `catalog/internal/meal/MealService.java`؛ الميديا `catalog/internal/media/MediaService.java`، `MediaInspector.java`.
9. **قاعدة البيانات.** `meal`، `meal_revision`، `meal_media` (V7)، `media_asset` (V6)، `media_cleanup` (V30)، عمود الإشراف (V16).
10. **الانتقالات.** الوجبة `DRAFT → PUBLISHED ↔ PAUSED → ARCHIVED` (المؤرشفة للقراءة بس)؛ والـ Staff يقدروا كمان يضبطوا `moderation_state` على `VISIBLE | HIDDEN` من غير ما يلمسوا حالة الطبّاخ (E2E-OPS-056).
11. **الأخطاء.** `VALIDATION_FAILED` بأكواد الحقول (`PHOTO_NOT_APPROVED`، `ALLERGEN…`)؛ `KITCHEN_UNAVAILABLE` لما المطبخ مايقدرش يبيع؛ معرّف وجبة طبّاخ تاني ← 404 (E2E-COOK-054)؛ أنواع الملفات الغلط والمحتوى المزوّر بيترفضوا (E2E-COOK-102).
12. **الاختبارات.** `backend/app/src/test/java/com/matbakhy/app/catalog/MealIntegrationTest.java`، `PriceHistoryIntegrationTest.java`، `ModerationIntegrationTest.java`، `MediaIntegrationTest.java`، `apps/operations-web/src/App.test.tsx`.
13. **اختبارات المالك E2E.** E2E-COOK-051، E2E-COOK-101، E2E-COOK-102، E2E-COOK-103، E2E-COOK-104؛ J6.
14. **الحالة.** IMPLEMENTED.
15. **القيود.** تغيير السعر بيأثر على عروض الأسعار الجديدة بس: عرض سعر أقدم بيترفض بـ `PRICE_CHANGED` والأوردر اللي اتسجّل بيحتفظ بالـ Snapshot بتاعه (E2E-BASKET-014/015). السيرفر هو اللي بيملك السعر؛ والويب بيحوّل النص لقروش (piastres) للإدخال بس.

## CO-4 الإتاحة والـ Slots والسعة

1. **الهدف.** عرض Slots زمنية مؤرخة بسعة حصص (BR-018 طبّاخ واحد وSlot واحد لكل أوردر).
2. **الطرف.** الطبّاخ.
3. **المتطلبات المسبقة.** وجبات منشورة تتربط بيها.
4. **البداية.** تبويب "slots".
5. **الإجراءات.** إنشاء Slot (التاريخ المحلي بتوقيت القاهرة، البداية/النهاية، `maxPortions`، دقايق المهلة (Lead)، سعات كل وجبة)؛ فتحه؛ تغيير السعة؛ إيقاف مؤقت / إغلاق / إلغاء.
6. **الاستجابات.** الـ Slot بيبقى قابل للطلب لما يكون `OPEN` وقبل موعد القطع (Cutoff)؛ والسعة مايصحش تنزل عن الحصص المحجوزة في الأوردرات (E2E-COOK-106)؛ موعد قطع في الماضي أو تداخل بيترفض (E2E-COOK-105).
7. **الـ API.** `GET/POST /api/v1/cook/slots` (API-050، API-051)، `PATCH /api/v1/cook/slots/{slotId}` (API-052)، والعام `GET /api/v1/kitchens/{kitchenId}/slots` (API-006).
8. **الـ Backend.** `backend/modules/availability/src/main/java/com/matbakhy/availability/web/SlotController.java` → `internal/SlotService.java`، `JdbcSlotInventory.java` (أقفال الحجز)، `CairoTime.java`.
9. **قاعدة البيانات.** `cook_slot`، `meal_slot` (V8)، `stock_allocation` (V11).
10. **الانتقالات.** الـ Slot `DRAFT → OPEN → PAUSED / CLOSED / CANCELLED`؛ تخصيص المخزون `RESERVED → ALLOCATED → CONSUMED | RELEASED` بيتحرك بأوامر الأوردر.
11. **الأخطاء.** `SLOT_CLOSED` (`CUTOFF_PASSED`)، `STATE_CONFLICT` (تداخل، سعة أقل من الالتزامات، الـ Slot عليه التزامات)، `VERSION_CONFLICT`.
12. **الاختبارات.** `backend/app/src/test/java/com/matbakhy/app/availability/SlotIntegrationTest.java`، `apps/operations-web/e2e/cook-workspace.e2e.ts`.
13. **اختبارات المالك E2E.** E2E-COOK-052، E2E-COOK-105، E2E-COOK-106، E2E-BASKET-016، E2E-BASKET-017، E2E-ORDER-114؛ J6.
14. **الحالة.** IMPLEMENTED.
15. **القيود.** التزامن على السعة متختبر في اختبارات تكامل الـ Backend؛ والسلوك تحت الحمل: NOT VERIFIED.

## CO-5 الأوردرات الواردة: قبول، رفض، تحضير، جاهز، توصيل (Dispatch)، تسليم

1. **الهدف.** تحريك أوردر العميل في خطوات المطبخ بانتقالات السيرفر بيتحقق منها.
2. **الطرف.** طبّاخ مطبخ الأوردر.
3. **المتطلبات المسبقة.** الأوردر في الحالة الصح؛ المطبخ قادر يقبل (`canAcceptOrders`) أو ينفّذ؛ وفي أوردرات العربون بوابة الاستلام (تحت).
4. **البداية.** تبويب "orders" (فلاتر ACTIVE / COMPLETED)، أو اختصار الـ Dashboard.
5. **الإجراءات.** القبول قبل الموعد النهائي للقبول (عدّاد من وقت السيرفر عبر `serverOffsetMs`)؛ أو الرفض بسبب (`KITCHEN_BUSY`، `INGREDIENT_UNAVAILABLE`، `OUTSIDE_CAPACITY`، `OTHER`)؛ التحضير (Prepare)؛ جاهز (Ready)؛ للاستلام: التسليم (Handover) بكود العميل المكوّن من 6 أرقام؛ للتوصيل: Dispatch بمرجع المندوب، وبعدين Handover بالكود أو فشل التوصيل (Delivery failure)؛ الإلغاء وهو `ACCEPTED` بس.
6. **الاستجابات.** الإجراءات الموجودة في `allowedActions` بس هي اللي بتتعرض؛ الطبّاخ بيشوف اسم العميل وتليفونه وعنوانه للأوردر الفعّال بس (`CookOrderView`)، وعمره ما بيشوف كود التسليم.
7. **الـ API.** `GET /api/v1/cook/orders`، `GET /api/v1/cook/orders/{orderId}` (API-053/054)، وPOST على `…/accept`، `…/reject`، `…/prepare`، `…/ready`، `…/dispatch`، `…/handover`، `…/delivery-failure`، `…/cancel` (من API-055 لـ API-062)، وكل واحد بـ `Idempotency-Key` و`expectedVersion`.
8. **الـ Backend.** `backend/modules/orders/src/main/java/com/matbakhy/orders/web/CookOrderController.java` → `OrderLifecycleService.cook` (accept, reject, prepare, ready, dispatch, handover, delivery_failure, cancel)؛ `OrderService.cookOrders`؛ `PaymentService.requireReadyForPreparation` (البوابة)؛ `HandoverCodes.java`.
9. **قاعدة البيانات.** `customer_order`، `order_event`، `stock_allocation` (V11)، `handover_challenge`، `handover_attempt` (V12)، `outbox_event`، `cash_obligation`، `order_payment` (V24).
10. **الانتقالات.** `PENDING_ACCEPTANCE → ACCEPTED | REJECTED | EXPIRED`؛ `ACCEPTED → PREPARING | CANCELLED`؛ `PREPARING → READY`؛ `READY → COMPLETED` (استلام، عن طريق التسليم) أو `READY → OUT_FOR_DELIVERY → COMPLETED | DELIVERY_FAILED`. المخزون: القبول بيحوّل `RESERVED → ALLOCATED`؛ التحضير `→ CONSUMED`؛ الرفض/الإلغاء/الانتهاء `→ RELEASED`. خمس أكواد تسليم غلط بتقفل التحدي (Challenge)؛ وبعدها لازم تدخل العمليات (E2E-ORDER-033). التزام الكاش بيتضبط `NOT_DUE` عند الرفض والانتهاء وإلغاء الطبّاخ (قبل التحضير)؛ وإلغاء العمليات الاستثنائي بيسيبه من غير تغيير لمراجعة المالية.
11. **الأخطاء.** `STATE_CONFLICT` (حالة غلط أو `DEADLINE_PASSED`، `WRONG_FULFILLMENT_MODE`)، `KITCHEN_UNAVAILABLE` (المطبخ الموقوف (Suspended) أو المغلق مايقدرش يقبل؛ والمطبخ المتوقف مؤقتًا (Paused) لسه يقدر يقبل الأوردرات المعلّقة اللي اتسجّلت قبل كده، `KitchenStatus.canAcceptOrders`)، العربون ماتبلّغش باستلامه (`STATE_CONFLICT` من بوابة التحضير، E2E-FIN-106)، `VERSION_CONFLICT`، `VALIDATION_FAILED` للأسباب خارج المجموعات المسموحة.
12. **الاختبارات.** `backend/app/src/test/java/com/matbakhy/app/orders/OrderLifecycleIntegrationTest.java`، `backend/app/src/test/java/com/matbakhy/app/payments/DepositPaymentIntegrationTest.java`، `apps/operations-web/e2e/cook-workspace.e2e.ts`، `apps/operations-web/src/App.test.tsx`.
13. **اختبارات المالك E2E.** E2E-ORDER-025، E2E-ORDER-026، E2E-ORDER-028، E2E-ORDER-029، E2E-ORDER-030، E2E-ORDER-031، E2E-ORDER-032، E2E-ORDER-034، E2E-ORDER-036، E2E-ORDER-037، E2E-ORDER-110، E2E-ORDER-101، E2E-ORDER-102؛ J1، J2.
14. **الحالة.** IMPLEMENTED.
15. **القيود.** التعامل مع المندوب مرجع نصي حر؛ ومفيش تكامل مع مندوبين. الطبّاخين مفيش عندهم تنبيه Push لأوردر جديد في تطبيق الويب (لازم يفتحوا الـ Dashboard أو يعملوا Refresh).

## CO-6 تقارير الكاش والعربون من المطبخ

1. **الهدف.** المطبخ بيقول اللي استلمه؛ مطبخي عمره ما بيمسك الفلوس وعمره ما بيدّعي تحقق بنكي.
2. **الطرف.** الطبّاخ؛ والتحقق من موظفي المالية (شوف [المالية والدعم](finance-and-support-ar.md)).
3. **المتطلبات المسبقة.** الكاش: الأوردر `COMPLETED` وحالة الكاش `DUE`. العربون: حالة الدفع منتظرة أو مبلّغ عنها.
4. **البداية.** كارت الأوردر في تبويب orders؛ وإعدادات العربون في تبويب settings.
5. **الإجراءات.** حفظ ملف الدفع (نسبة العربون، سقف اختياري للدفع كاش عند الاستلام، المستلم)؛ الإبلاغ عن استلام العربون (المبلغ لازم يساوي العربون المحسوب من السيرفر)، أو الإبلاغ بـ "لم يُستلم" / "عدم تطابق في المبلغ"؛ بعد الإلغاء الإبلاغ عن استرداد اتبعت بمرجع؛ الإبلاغ عن الكاش المحصّل مرة واحدة لكل أوردر.
6. **الاستجابات.** كارت حالة الدفع بيتحدّث؛ والمستلم اللي اتغيّر بيفضل مخفي عن العملاء لحد ما موظفي المالية يوافقوا (E2E-COOK-108).
7. **الـ API.** `GET/PUT /api/v1/cook/payment-profile` (API-102/103)، `POST /api/v1/cook/orders/{orderId}/payment/receipt|problem|refund` (API-101، API-105، API-108)، `POST /api/v1/cook/orders/{orderId}/cash-reports` (API-063)، `GET /api/v1/cook/statements` (API-064).
8. **الـ Backend.** `PaymentController.java`، `PaymentService.java` (موديول orders)، `backend/modules/finance/src/main/java/com/matbakhy/finance/web/CashController.java` → `CashService.report`، `StatementController.java`.
9. **قاعدة البيانات.** `kitchen_payment_profile`، `order_payment`، `payment_event` (V24)، `cash_obligation` (V11)، `cash_report` (V13)، `ledger_group`، `ledger_entry` (V13).
10. **الانتقالات.** الدفع: `WAITING_FOR_DEPOSIT → CUSTOMER_TRANSFER_REPORTED → KITCHEN_RECEIPT_REPORTED`، أو `→ DISPUTED`، `RECONCILE_PENDING`، `EXPIRED` (القايمة الكاملة في `docs/product/09-deposit-payment-states.md`). الكاش: `DUE → REPORTED → VERIFIED | DISPUTED`.
11. **الأخطاء.** `ORDER_ENDED` (بلّغ عن استرداد بدلها)، `NO_DEPOSIT_WAITING`، تقرير كاش واحد لكل أوردر (E2E-FIN-102)، لازم مبلغ الاستلام يتطابق (E2E-FIN-041).
12. **الاختبارات.** `backend/app/src/test/java/com/matbakhy/app/payments/DepositPaymentIntegrationTest.java`، `PaymentReconciliationIntegrationTest.java`، `backend/app/src/test/java/com/matbakhy/app/finance/CashAndLedgerIntegrationTest.java`، `ZeroCommissionIntegrationTest.java`، `apps/operations-web/e2e/money-reflow.e2e.ts`.
13. **اختبارات المالك E2E.** E2E-FIN-038، E2E-FIN-039، E2E-FIN-040، E2E-FIN-043، E2E-FIN-106، E2E-FIN-107، E2E-FIN-108، E2E-FIN-109، E2E-FIN-111، E2E-FIN-102، E2E-COOK-108؛ من J2 لـ J4.
14. **الحالة.** IMPLEMENTED (بيانات تجريبية؛ الفلوس الحية مقفولة بشروط).
15. **القيود.** تفاصيل تحويل العربون إفادة من المطبخ، مش تأكيد بنكي.

## CO-7 إعدادات المطبخ، الإيقاف المؤقت، والتعليق

1. **الهدف.** الطبّاخ بيوقف/بيستأنف البيع مؤقتًا؛ والـ Staff بيوقفوا أو يعلّقوا مطبخ.
2. **الطرف.** الطبّاخ (الإعدادات: الوصف، الاستلام، المناطق، الإيقاف المؤقت)؛ الـ Staff اللي معاهم `cook.review` + MFA حديث (حالة التشغيل).
3. **المتطلبات المسبقة.** مطبخ موافَق عليه.
4. **البداية.** تبويب الإعدادات (الطبّاخ)؛ تبويب Reviews، لوحة المطبخ (الـ Staff).
5. **الإجراءات.** الطبّاخ: تعديل الوصف، تبديل الاستلام، تبديل مناطق التوصيل (المناطق الموافَق عليها بس)، إيقاف مؤقت / استئناف. الـ Staff: إيقاف مؤقت، تعليق، استعادة بسبب.
6. **الاستجابات.** المطبخ الموقوف مؤقتًا أو المعلّق بيختفي من الاكتشاف ومايتعملوش له عرض سعر (E2E-DISC-005، E2E-DISC-104)؛ والتعليق بيوقف المبيعات الجديدة وقبول الأوردرات المعلّقة لكن الأوردرات اللي اتقبلت قبل كده ممكن تتنفّذ (E2E-COOK-050).
7. **الـ API.** `PATCH /api/v1/cook/settings` (API-065)، `POST /api/v1/ops/kitchens/{kitchenId}/operating-state` (API-070).
8. **الـ Backend.** `CookSettingsController.java` → `KitchenService.updateSettings`؛ `CookReviewController.java` → `KitchenService.changeOperatingState` (+ `KitchenOrderImpact` بيوضح أثر الأوردرات الفعّالة).
9. **قاعدة البيانات.** `kitchen`، `kitchen_zone` (V5).
10. **الانتقالات.** `ACTIVE → PAUSED` (بالطبّاخ أو الـ Staff)، `PAUSED | ACTIVE → SUSPENDED`، `PAUSED | SUSPENDED → ACTIVE` (RESTORE من الـ Staff)؛ الطبّاخ يقدر يستأنف فقط الإيقاف المؤقت اللي هو عمله؛ و`CLOSED` مخفي من استعلامات الملف العام.
11. **الأخطاء.** `STATE_CONFLICT` "kitchen is PAUSED by staff"؛ `VERSION_CONFLICT`؛ الإعدادات بترفض اللي بيخلي المطبخ غير قادر على الخدمة (قيد CHECK `cook_revision_serviceable_ck` في V5 للطلبات، وE2E-COOK-112 للإعدادات).
12. **الاختبارات.** `backend/app/src/test/java/com/matbakhy/app/cooks/CookWorkspaceIntegrationTest.java`، `CookWorkflowIntegrationTest.java`، `apps/operations-web/src/App.test.tsx`.
13. **اختبارات المالك E2E.** E2E-COOK-050، E2E-COOK-107، E2E-COOK-112، E2E-DISC-005، E2E-DISC-104.
14. **الحالة.** IMPLEMENTED.
15. **القيود.** مفيش واجهة لضبط حالة المطبخ `CLOSED` (مفيش إجراء في الـ API بيوصل لها: `KitchenService.Action` هي PAUSE، SUSPEND، RESTORE).

## القيود المعروفة وفجوات التوثيق

* `docs/product/06-permissions.md` بيسرد "Support" و"Ops" و"Finance" و"Admin" كأدوار؛ في الكود فيه **صلاحيات ممنوحة** بس (الـ Enum اسمه `Permission` في `backend/modules/identity/src/main/java/com/matbakhy/identity/api/Permission.java`). وبيذكر كمان `finance.settlement_verify`؛ وصلاحية الكود هي `finance.settlement`. اعتمد على الكود.
* طلب الطبّاخ مالوش واجهة مستخدم (شوف CO-1). المستندات الموجودة اللي بتوصف شاشة تسجيل بتوصف خطة.
* الطبّاخ اللي معاه كمان صلاحيات Staff قواعد فصل المهام متفروضة عليه لطلبه ومطبخه وحساب الدفع بتاعه (E2E-OPS-108)؛ نقاط الفرض بالظبط ماتتتبعتش واحدة واحدة هنا (NOT VERIFIED غير `CookReviewService.refuseSelfReview`).
