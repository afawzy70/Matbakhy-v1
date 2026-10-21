# رحلات المستخدم: ويب العميل

> **Owner role:** Frontend and mobile
> **Status:** IMPLEMENTED — تطبيق ويب العميل (`apps/customer-web`) بيغطي التصفح، وتسجيل الدخول، والسلة، وعرض السعر، وتسجيل الأوردر، والتتبّع، والحساب، وتفضيل الـ MFA؛ أما تقديم الطبّاخ والدفع بالكارت فمش جزء منه.
> **Language / اللغة:** [English](customer-web.md) · العربية

الصفحة دي بتوثّق بس الشاشات والمسارات الموجودة فعلًا في `apps/customer-web/src`. التطبيق هو React Single-page بـ **Hash Routing** (`window.location.hash`، شوف `apps/customer-web/src/App.tsx`). الحقائق المشتركة موجودة في [معمارية الـ Frontend](../03-architecture/frontend-architecture-ar.md) و[الهوية والأمان](../03-architecture/identity-and-security-ar.md) و[عقود الـ API](../04-codebase/api-contracts-ar.md). نفس الرحلات على الموبايل موجودة في [موبايل العميل](customer-mobile-ar.md)؛ والسلسلة كلها في [رحلة الأوردر الكاملة](complete-order-journey-ar.md).

## المسارات الموجودة

| مسار الـ Hash | المكوّن | محتاج Session | ملاحظات |
|---|---|---|---|
| `#/` | `Browse` (`apps/customer-web/src/Browse.tsx`) | لأ | الرئيسية: كروت النية (Intent)، البحث، الفلاتر، التبديل بين الأطباق/المطابخ |
| `#/meal/<uuid>` | `Meal` (`apps/customer-web/src/Meal.tsx`) | لأ للعرض؛ أيوه للمفضلة والطلب | اختيار الـ Slot، الكمية، الإضافة للسلة، المراجعات |
| `#/kitchen/<slug>` | `Kitchen` (`apps/customer-web/src/Kitchen.tsx`) | لأ | ملف المطبخ العام ووجباته |
| `#/checkout` | `Checkout` (`apps/customer-web/src/Checkout.tsx`) | أيوه لعرض السعر/التسجيل | السلة (مكتوب عليها "cart" في الشريط العلوي)، التحقق من التليفون، العنوان، عرض السعر، تسجيل الأوردر |
| `#/orders` | `OrderList` (`apps/customer-web/src/Orders.tsx`) | أيوه | أوردراته هو |
| `#/orders/<uuid>` | `OrderDetail` | أيوه | الجدول الزمني، كود التسليم، الإلغاء، المراجعة، حالة الدفع |
| `#/account` | `Account` (`apps/customer-web/src/Account.tsx`) | أيوه (بيعرض رابط تسجيل دخول غير كده) | الملف الشخصي، المفضلة، الإشعارات، الدعم، العناوين، الأمان، الحذف |
| `#/support/<uuid>` | `SupportCaseDetail` | أيوه | محادثة حالة الدعم الخاصة بيه |

شريط التنقل السفلي بيعرض Browse دايمًا، وOrders/Account بس لما `GET /api/v1/me` يرجّع ملف شخصي. **مفيش** مسار للتقديم كطبّاخ، ولا شاشة دفع بالكارت، ولا شاشة Staff في التطبيق ده (`NOT PRESENT` in code؛ اتأكدنا بالبحث في `apps/customer-web/src` عن `cook/application`).

المشترك في كل رحلة تحت:

* **الطرف / الصلاحيات.** زائر (من غير Session) أو عميل. الـ API بيدي كل حساب فعّال سلطة `ACCOUNT_ACTIVE`؛ ونقاط نهاية العميل بتفحص كمان ملكية الكائن في طبقة الـ Service (404 لكائن عميل تاني). مفيش أي صلاحية Staff داخلة. شوف [الأطراف والصلاحيات](../01-business/actors-and-permissions-ar.md).
* **وسيلة النقل.** Cookie الـ Session بتاع المتصفح `MATBAKHY_SESSION` (HttpOnly، SameSite=Lax) بالإضافة لـ Header الـ CSRF `X-XSRF-TOKEN` على كل طلب مش GET (`apps/customer-web/src/api.ts`، `backend/app/src/main/java/com/matbakhy/app/security/SecurityConfiguration.java`). كل طلب بيشيل `Accept-Language` من زرار اللغة.
* **الـ Client المُنمَّط (Typed).** `createApi` بيلف `createApiClient` من `packages/api-client/src/index.ts`، والمتولّد من `contracts/openapi.yaml`.
* **الاختبارات الموجودة (لكل الرحلات).** اختبارات المكوّنات بـ `fetch` مزيّف: `apps/customer-web/src/App.test.tsx` (مجموعات describe: customer web، basket rules، MBQA-002 exhausted slot، MBQA-001 language، CW05 basket editing، Q1 quantity limits، MBQA-003 sign-out، MBQA-004 support case، OBS-001 recovery lookup، D13 changed prices، MB-053 deposit checkout، MB-054 activity، demo notice، account security D18). اختبارات المتصفح على API حقيقي موجودة في مشروع operations-web: `apps/operations-web/e2e/customer-journey.e2e.ts` (أوردرات توصيل واستلام وبعدها إجراءات الطبّاخ)، `apps/operations-web/e2e/keyboard.e2e.ts`؛ بتتشغّل من خلال `scripts/dev.sh e2e`. حالة الاختبارات للـ Commit الحالي: NOT VERIFIED هنا (ماتنفذتش وقت كتابة الصفحة دي).

## CW-1 اكتشاف الزائر، البحث، والفلترة

1. **هدف البيزنس.** أي حد يشوف المطابخ المنزلية بتقدم إيه قبل ما يعمل حساب (قواعد السلة BR-018 بتبدأ عند "إضافة" بس).
2. **الطرف / الصلاحيات.** زائر أو عميل؛ Endpoints عامة (`PUBLIC_GET` في `SecurityConfiguration`).
3. **المتطلبات المسبقة.** مطبخ واحد على الأقل موافَق عليه وفعّال وفيه وجبة منشورة وظاهرة عشان تتعرض. من غير Slot مفتوح مؤهل الوجبة ممكن تتعرض لكنها بتتعلّم `UNAVAILABLE` ومايتطلبش منها؛ فلتر التاريخ أو الـ Slot بس هو اللي بيحتاج Slot قابل للطلب عشان يطابق (`DiscoveryService`، `MealOffers`).
4. **شاشة البداية.** `#/` (`Browse`).
5. **إجراءات المستخدم.** فتح التطبيق؛ اختيار كارت نية اختياريًا (لنفسي / مكمّل / تجمّع) واللي بيظهر فلتر ميزانية؛ الكتابة في خانة البحث؛ اختيار شريحة تصنيف؛ اختيار المنطقة؛ تغيير الترتيب (`NEWEST`، `PRICE_ASC`، `PRICE_DESC`)؛ مسح الفلاتر؛ التبديل بين "dishes" و"kitchens"؛ فتح وجبة أو مطبخ.
6. **الاستجابات المتوقعة.** كروت الأكل المتراصة بتتحدّث من نتيجة السيرفر؛ السعر معروض بالجنيه (EGP) منسّق بـ `Intl.NumberFormat` (`formatMoney` في `apps/customer-web/src/api.ts`)؛ حالات الفراغ والخطأ جاية من `Status` في `apps/customer-web/src/shared.tsx`؛ وبانر عدم الاتصال بيظهر لما `navigator.onLine` يبقى false. الفلاتر هي Query Parameters على السيرفر؛ والعميل عمره ما بيفلتر ولا بيرتّب ولا بيسعّر (تعليق في `Browse.tsx`). فلاتر حجم الحصة مؤجلة (قرار D11).
7. **الـ API.** `GET /api/v1/zones` (API-002)، `GET /api/v1/meals` مع `q, zoneId, categoryId, maxPriceMinor, sort, limit` (API-003)، `GET /api/v1/meals/{mealId}` (API-004)، `GET /api/v1/kitchens/{slug}` (API-005)، `GET /api/v1/kitchens/{kitchenId}/slots` (API-006، بتستخدمها صفحة الوجبة)، `GET /api/v1/configuration/public` (API-001)، `POST /api/v1/activity` (API-110، عدّ المشاهدات المجهولة، أرسل وانسَ).
8. **الـ Backend.** `backend/modules/catalog/src/main/java/com/matbakhy/catalog/web/DiscoveryController.java` → `backend/modules/catalog/src/main/java/com/matbakhy/catalog/internal/discovery/DiscoveryService.java`؛ تفاصيل الوجبة `MealController.java` / `MealService.java`؛ المطبخ `backend/modules/cooks/src/main/java/com/matbakhy/cooks/web/KitchenPublicController.java`؛ الـ Slots `backend/modules/availability/src/main/java/com/matbakhy/availability/web/SlotController.java`؛ النشاط `backend/modules/catalog/src/main/java/com/matbakhy/catalog/web/ActivityController.java`.
9. **قاعدة البيانات.** `meal`، `meal_revision`، `meal_media`، `media_asset` (V6، V7)، `meal_slot`، `cook_slot` (V8)، `kitchen`، `kitchen_zone` (V5)، `service_zone` (V3)، `activity_daily` (V26)، `review` (V20). الـ Migrations: `backend/app/src/main/resources/db/migration`.
10. **انتقالات الحالة.** مفيش للعميل. الظهور بيعتمد على الوجبة `status = PUBLISHED`، و`moderation_state = VISIBLE` (V16)، والمطبخ `operating_state = ACTIVE`، والـ Slot `state = OPEN`.
11. **الأخطاء / الحدود.** حد الـ Cursor خارج النطاق بيترفض (E2E-DISC-103)؛ المطابخ الموقوفة مؤقتًا والوجبات المسودة والمطابخ المعلّقة مابتتعرضش (E2E-DISC-005، E2E-DISC-104)؛ فشل الشبكة بيعرض الخطأ العام مع زرار إعادة المحاولة.
12. **الاختبارات.** `backend/app/src/test/java/com/matbakhy/app/catalog/DiscoveryIntegrationTest.java`، `backend/app/src/test/java/com/matbakhy/app/catalog/MealIntegrationTest.java`، `backend/app/src/test/java/com/matbakhy/app/insights/ActivityInsightsIntegrationTest.java`، في الويب `apps/customer-web/src/App.test.tsx`، `apps/operations-web/e2e/customer-journey.e2e.ts`.
13. **اختبارات المالك E2E.** E2E-DISC-001، E2E-DISC-006، E2E-DISC-101، E2E-DISC-102، E2E-DISC-103، E2E-DISC-008، E2E-DISC-112، E2E-DISC-113، E2E-DISC-117؛ المجموعة الأساسية J0 (شوف [رحلة الأوردر الكاملة](complete-order-journey-ar.md#ربط-قبول-المالك)).
14. **الحالة.** IMPLEMENTED.
15. **القيود.** مفيش واجهة تقسيم صفحات: الويب بيطلب `limit: 50` مرة واحدة (`Browse.tsx`)، فأكتر من 50 نتيجة مش ممكن توصلها من الويب (NOT VERIFIED هل فيه زرار "المزيد" في مكان تاني؛ مالقيناش في `Browse.tsx`). كروت النية بتظهر فلتر ميزانية بس، ومابتغيّرش الاستعلام غير كده.

## CW-2 تسجيل الدخول والخروج وتبديل الحساب

1. **الهدف.** الـ Authentication من خلال مزوّد الهوية (Keycloak) وإنهاء الـ Session بشكل سليم (BR-048).
2. **الطرف.** زائر بيبقى عميل.
3. **المتطلبات المسبقة.** حساب Keycloak في الـ Realm `matbakhy` (التسجيل الذاتي مقفول في ملفي الـ Realm: `registrationAllowed: false`؛ وE2E-DISC-002 بتغطي قرار التسجيل الموجّه للمالك). التفاصيل: [المصادقة وMFA](authentication-and-mfa-ar.md).
4. **البداية.** أي شاشة، "Sign in" في الشريط العلوي.
5. **الإجراءات.** دوس Sign in ← المتصفح بيروح على `/oauth2/authorization/keycloak?returnTo=<current URL>` على Origin الـ API ← تسجيل دخول Keycloak (Password؛ من غير OTP إلا لو سياسة الـ MFA بتطلبه) ← الـ Callback `/login/oauth2/code/keycloak` ← تحويل راجع لـ `returnTo` بعد التحقق منه. تسجيل الخروج: دوس Sign out؛ ولو فيه مفتاح أوردر غير مؤكد متخزّن في المتصفح ده بيظهر حوار تحذير الأول (`signOutPending`).
6. **الاستجابات.** بعد الرجوع، `GET /api/v1/me` بيملا الملف الشخصي والتنقل السفلي بيعرض Orders وAccount. تسجيل الخروج بينادي logout بتاع الـ API، وبيمسح مفاتيح السلة والأوردر المعلّق من `localStorage`، وبعدين بيروح لرابط إنهاء الـ Session عند مزوّد الهوية الراجع في `idpLogoutUrl` عشان تسجيل الدخول الجاي يطلب بيانات الدخول. لو السيرفر مش متاح، مفيش حاجة بتتمسح والعميل بيفضل مسجّل دخول (`signOutState = "failed"`).
7. **الـ API.** `GET /api/v1/me` (API-009)؛ `POST /api/v1/auth/logout` (معالج تسجيل الخروج في Spring Security المضبوط في `SecurityConfiguration`، بيديره الـ Framework و**مش** موجود في `contracts/endpoints.json` — الـ Client بينادي عليه بـ Cast، شوف `App.tsx`)؛ `GET /oauth2/authorization/keycloak`، `GET /login/oauth2/code/keycloak` (مسارات الـ Framework).
8. **الـ Backend.** `SecurityConfiguration` (webChain)، `WebReturnTo`، `AccountMapper`، `WebAccountFilter` تحت `backend/app/src/main/java/com/matbakhy/app/security/`؛ `backend/modules/identity/src/main/java/com/matbakhy/identity/web/ProfileController.java`.
9. **قاعدة البيانات.** `app_user` (V1)، `SPRING_SESSION`، `SPRING_SESSION_ATTRIBUTES` (V2).
10. **الانتقالات.** الـ Session: مفيش ← فعّالة ← منتهية. حالة الحساب `ACTIVE` / `DELETION_REQUESTED` (شوف CW-6).
11. **الأخطاء.** `returnTo` أو `redirect_uri` عدائي بيترفض (CFG-003)؛ الـ Session المنتهية/المحذوفة بترجّع 401 والواجهة بترجع لحالة مش مسجّل دخول (CFG-004)؛ الصفحة المستعادة من Cache الرجوع-الأمام بعد تسجيل الخروج بتتعمل لها Reload (معالج `pageshow`، MBQA-003).
12. **الاختبارات.** `apps/customer-web/src/App.test.tsx` (بلوك MBQA-003 sign-out)، `backend/app/src/test/java/com/matbakhy/app/identity/IdentityIntegrationTest.java`، `backend/app/src/test/java/com/matbakhy/app/security/PreviewSecurityConfigurationTest.java`.
13. **اختبارات المالك E2E.** CFG-002، CFG-003، CFG-004، CFG-006، E2E-DISC-003، E2E-DISC-004؛ المجموعة الأساسية J0.
14. **الحالة.** IMPLEMENTED.
15. **القيود.** مهلة الـ Session الافتراضية 30 دقيقة (`WEB_SESSION_TIMEOUT` في `backend/app/src/main/resources/application.yaml`) مفيهاش تحذير على الشاشة. الحد من محاولات تسجيل الدخول على مستوى التطبيق مش موجود؛ الحماية من التخمين عند Keycloak بس (CFG-039).

## CW-3 اختيار الوجبة والسلة

1. **الهدف.** بناء سلة لمطبخ واحد وSlot واحد (BR-018).
2. **الطرف.** زائر أو عميل (السلة مابتحتاجش Session).
3. **المتطلبات المسبقة.** وجبة ليها Slot مفتوح لسه فيه حصص.
4. **البداية.** `#/meal/<uuid>`.
5. **الإجراءات.** اختيار Slot، وضبط الكمية (محدودة بالأصغر بين حد السطر الواحد المنشور من السيرفر والحصص المتبقية في الـ Slot؛ الاحتياطي 20، `apps/customer-web/src/cart.ts`)، ودوس إضافة. إضافة وجبة من مطبخ أو Slot تاني بتفتح رسالة استبدال: احتفظ أو استبدل. تعديل الكميات أو إزالة الأسطر في `#/checkout`.
6. **الاستجابات.** السلة بتفضل في مفتاح `localStorage` اسمه `matbakhy.cart`؛ بيتحسب تقدير من سعر الوحدة اللي اتشاف وقت الإضافة بالقروش (piastres) الصحيحة (`estimateMinor`)، وده صراحةً مش السعر. زرار المفضلة محتاج Session.
7. **الـ API.** `GET /api/v1/meals/{mealId}`، `GET /api/v1/kitchens/{kitchenId}/slots`، `GET /api/v1/configuration/public` (الحد، بيتقرا تاني لما مسار الوجبة أو السلة يتفتح)، `PUT/DELETE /api/v1/me/favorites/{mealId}` (API-019/020)، `GET /api/v1/me/favorites` (API-018)، `GET /api/v1/kitchens/{kitchenId}/reviews` (API-007)، `POST /api/v1/reviews/{reviewId}/reports` (API-030).
8. **الـ Backend.** `PublicConfigurationController.java`، `CommercePolicies.java` (`backend/modules/orders/src/main/java/com/matbakhy/orders/`)، `FavoriteController.java` / `FavoriteService.java` (`backend/modules/catalog/...`)، `ReviewController.java` / `ReviewService.java`.
9. **قاعدة البيانات.** `commerce_policy` (V10)، `favorite`، `review`، `review_report` (V20).
10. **الانتقالات.** مفيش على السيرفر؛ السلة على العميل بس. مطبخ واحد وSlot واحد متفروضين في الواجهة وتاني وقت عرض السعر (E2E-BASKET-021).
11. **الأخطاء.** الـ Slot المستنفد مايتضافش (MBQA-002، E2E-BASKET-012)؛ سطر فوق الحد المخفّض بيمنع عرض السعر لحد ما يتقلل (E2E-BASKET-024)؛ التخزين غير المتاح (الوضع الخاص) بيخلي المسار شغّال جوه الصفحة (`cart.ts`).
12. **الاختبارات.** `apps/customer-web/src/App.test.tsx` (بلوكات basket rules، CW05، Q1)، `backend/app/src/test/java/com/matbakhy/app/orders/ReviewsAndFavoritesIntegrationTest.java`، `backend/app/src/test/java/com/matbakhy/app/orders/PublicConfigurationIntegrationTest.java`.
13. **اختبارات المالك E2E.** E2E-BASKET-009، E2E-BASKET-010، E2E-BASKET-011، E2E-BASKET-012، E2E-BASKET-030، E2E-DISC-107؛ J1، J5.
14. **الحالة.** IMPLEMENTED.
15. **القيود.** السلة لكل متصفح، ومش متزامنة مع تطبيق الموبايل أو أجهزة تانية.

## CW-4 عرض السعر، التحقق من التليفون، وتسجيل الأوردر

1. **الهدف.** تحويل السلة لأوردر واحد بسعر السيرفر مرة واحدة بالظبط (BR-019، BR-023).
2. **الطرف.** عميل بتليفون مصري متحقق منه.
3. **المتطلبات المسبقة.** مسجّل دخول؛ التليفون متحقق منه (غير كده `CONTACT_VERIFICATION_REQUIRED`)؛ للتوصيل عنوان محفوظ في منطقة المطبخ بيخدمها؛ ونسخ الشروط وسياسة الإلغاء المقبولة.
4. **البداية.** `#/checkout`.
5. **الإجراءات.** (a) لو مش مسجّل دخول، رابط تسجيل دخول بيحافظ على السلة. (b) التحقق من التليفون: أدخل رقم `+20…`، اطلب كود، أدخل الكود. (c) اختيار الاستلام أو التوصيل (الأوضاع اللي مطبخ السلة بيقدمها بس)، اختيار أو إضافة عنوان، اختيار طريقة الدفع COD أو DEPOSIT. (d) "Calculate total" ← عرض سعر. (e) علّم القبول ← "Place order". (f) لو تسجيل سابق اتقطع، "Retry order".
6. **الاستجابات.** لوحة عرض السعر بتعرض الأسطر ورسوم التوصيل والإجمالي ومعاينة الموعد النهائي للقبول و`PaymentTerms` (`apps/customer-web/src/Payment.tsx`): للـ DEPOSIT بتعرض العربون والباقي ومستلم المطبخ المعتمد مع بيان واضح إن مطبخي مابيمسكش الفلوس؛ ومفيش سطر عمولة أو رسوم خدمة. عند النجاح التطبيق بيروح لـ `#/orders/<id>`. لو السيرفر رجّع `PRICE_CHANGED`، مفيش حاجة بتتطلب؛ والتطبيق بيعيد عرض السعر وبيعرض الإجماليات القديمة مقابل الجديدة (وبينسب التغيير في رسوم التوصيل بس للرسوم) وبيطلب قبول صريح (قرار D13، `packages/api-client/src/priceChanges.ts`).
7. **الـ API.** `POST /api/v1/me/phone/challenges` (API-011)، `POST /api/v1/me/phone/challenges/{challengeId}/verify` (API-012)، `GET/POST /api/v1/me/addresses` (API-013/014)، `GET /api/v1/zones`، `POST /api/v1/quotes` (API-021)، `POST /api/v1/orders` مع الـ Header `Idempotency-Key` والجسم `{quoteId, clientRequestId, acceptedPolicyVersions}` (API-022)، `GET /api/v1/orders/by-request/{clientRequestId}` (API-024، الاسترجاع بعد ضياع الاستجابة).
8. **الـ Backend.** `QuoteController.java` → `QuoteService.java`؛ `OrderController.java` → `OrderService.place`؛ `PaymentService.java` (الشروط وصف الدفع الأولي)؛ الهوية `ContactController.java`، `PhoneVerificationService.java`، `AddressService.java`. كلها تحت `backend/modules/orders/src/main/java/com/matbakhy/orders/` و`backend/modules/identity/src/main/java/com/matbakhy/identity/`. مساعد الـ Idempotency في الموديول المشترك (جدول `idempotency_record`).
9. **قاعدة البيانات.** `quote`، `quote_item` (V10)، `customer_order`، `order_item`، `stock_allocation`، `order_event`، `outbox_event`، `cash_obligation` (V11)، `order_payment`، `payment_event` (V24)، `phone_challenge`، `address` (V3)، `idempotency_record` (V1)، بصمة الطلب (V29).
10. **الانتقالات.** عرض السعر (صلاحية 5 دقايق، غير قابل للتعديل، مابيحجزش حاجة، E2E-BASKET-029/013) ← الأوردر `PENDING_ACCEPTANCE` مع تخصيص مخزون `RESERVED`؛ أوردرات العربون بتبدأ بحالة الدفع `WAITING_FOR_DEPOSIT`، والـ COD بـ `NOT_REQUIRED`. الـ State Machine الكاملة: `docs/product/04-state-machines.md` و[دورة حياة الأوردر](../01-business/order-lifecycle-ar.md).
11. **الأخطاء.** `QUOTE_EXPIRED`، `PRICE_CHANGED`، `STOCK_CHANGED`، `SLOT_CLOSED`، `KITCHEN_UNAVAILABLE`، `IDEMPOTENCY_CONFLICT`، `STATE_CONFLICT` مع `QUOTE_ALREADY_USED` (الأكواد في `backend/shared/src/main/java/com/matbakhy/shared/error/ErrorCode.java`؛ النصوص المترجمة في `apps/customer-web/src/i18n.ts`). خطأ 5xx أو شبكة بعد الإرسال بيحتفظ بـ `matbakhy.pendingPlacement` (نفس `clientRequestId` و`Idempotency-Key`) عشان إعادة المحاولة ماتعملش تكرار؛ و4xx نهائي بيمسحه. تغيير الوضع أو العنوان أو طريقة الدفع بيلغي عرض السعر المعروض (E2E-BASKET-019).
12. **الاختبارات.** الـ Backend: `backend/app/src/test/java/com/matbakhy/app/orders/QuoteIntegrationTest.java`، `OrderPlacementIntegrationTest.java`، `PriceChangeIntegrationTest.java`، `backend/app/src/test/java/com/matbakhy/app/payments/DepositPaymentIntegrationTest.java`، `backend/app/src/test/java/com/matbakhy/app/identity/ContactIntegrationTest.java`. الويب: `apps/customer-web/src/App.test.tsx` (D13، OBS-001، MB-053). المتصفح: `apps/operations-web/e2e/customer-journey.e2e.ts`.
13. **اختبارات المالك E2E.** E2E-DISC-105، E2E-DISC-106، E2E-DISC-007، E2E-BASKET-013، E2E-BASKET-014، E2E-BASKET-015، E2E-BASKET-019، E2E-BASKET-020، E2E-BASKET-028، E2E-ORDER-021، E2E-ORDER-022، E2E-ORDER-024، E2E-ORDER-123، E2E-FIN-103، E2E-FIN-104، E2E-FIN-105؛ من J1 لـ J5.
14. **الحالة.** IMPLEMENTED. الفلوس الحقيقية مقفولة بشروط: العربون تحويل يدوي برّه مطبخي؛ والتطبيق عمره ما بيدّعي تحقق بنكي.
15. **القيود.** نصوص نسخ سياسة الإلغاء والشروط جاية من استجابة عرض السعر؛ ومفيش صفحة سياسات منفصلة (`GET /api/v1/policies/{key}` API-008 معلّم إنه مش متنفّذ في `contracts/endpoints.json`).

## CW-5 تتبّع الأوردر، تقرير الدفع، الإلغاء، والمراجعة

1. **الهدف.** متابعة الأوردر والتصرف فيه حيث السيرفر يسمح.
2. **الطرف.** صاحب الأوردر (العميل).
3. **المتطلبات المسبقة.** أوردر متسجّل.
4. **البداية.** `#/orders` وبعدين `#/orders/<uuid>`.
5. **الإجراءات.** قراءة الجدول الزمني؛ لأوردر العربون دوس "I sent the deposit" واديله مرجع اختياريًا؛ الإلغاء وهو `PENDING_ACCEPTANCE`؛ بعد القبول اطلب إلغاء؛ اعرض كود التسليم عند الاستلام أو التوصيل؛ راجع أوردر مكتمل؛ افتح "order again"؛ افتح حالة دعم من صفحة الحساب.
6. **الاستجابات.** عرض التفاصيل بيعيد القراءة كل 10 ثواني (`window.setInterval(reload, 10_000)` في `Orders.tsx`) — مفيش Push. الأزرار بتتعرض بس لو السيرفر ذكرها في `allowedActions` (`CANCEL`، `REQUEST_CANCELLATION`). كود التسليم بيتعرض بس لما السيرفر يرجّعه (الحالة `READY` أو `OUT_FOR_DELIVERY`، للمالك بس). نص الدفع بيقول إن المطبخ لسه لازم يؤكد الاستلام؛ ومفيش حاجة بتقول "verified by bank".
7. **الـ API.** `GET /api/v1/orders` (API-023)، `GET /api/v1/orders/{orderId}` (API-025)، `POST /api/v1/orders/{orderId}/cancel` (API-027)، `POST /api/v1/orders/{orderId}/cancellation-requests` (API-028)، `POST /api/v1/orders/{orderId}/payment/transfer-report` (API-100)، `POST /api/v1/orders/{orderId}/reviews` (API-029). `GET /api/v1/orders/{orderId}/events` (API-026) موجود في العقد؛ والويب بيقرا الأحداث المضمّنة في عرض الأوردر (NOT VERIFIED هل الويب بينادي API-026 — مالقيناش نداء).
8. **الـ Backend.** `OrderController.java`، `OrderLifecycleService.customerCancel`، `OrderService.get/list`، `PaymentController.java` / `PaymentService.reportTransfer`، `ReviewController.java` / `ReviewService.java`، `backend/modules/support/src/main/java/com/matbakhy/support/web/SupportController.java` (طلب الإلغاء بيعمل حالة دعم).
9. **قاعدة البيانات.** `customer_order`، `order_event`، `handover_challenge`، `handover_attempt` (V12)، `order_payment`، `payment_event`، `review` (V20)، `support_case` (V17)، `notification` (V14).
10. **الانتقالات.** `PENDING_ACCEPTANCE → CANCELLED` (العميل)، `→ EXPIRED` (النظام عند الموعد النهائي للقبول عن طريق `OrderExpiryJob`)، والحالات اللي بعد كده بيحركها الطبّاخ/الـ Staff. الدفع `WAITING_FOR_DEPOSIT → CUSTOMER_TRANSFER_REPORTED`. حالات الأوردر النهائية: `COMPLETED, REJECTED, EXPIRED, CANCELLED, DELIVERY_FAILED` (`OrderLifecycleService.TERMINAL`).
11. **الأخطاء.** معرّف أوردر عميل تاني بيرجّع 404 (E2E-ORDER-120)؛ `expectedVersion` قديم بيرجّع `VERSION_CONFLICT`؛ زرار الإلغاء بيختفي لو الأوردر اتحرك (الواجهة بتعتبر `STATE_CONFLICT` "اتعمل قبل كده")؛ تكرار تقرير التحويل Idempotent وإعلامي (E2E-FIN-042)؛ المراجعة التانية بتدي 409 (E2E-DISC-110).
12. **الاختبارات.** `backend/app/src/test/java/com/matbakhy/app/orders/OrderLifecycleIntegrationTest.java`، `backend/app/src/test/java/com/matbakhy/app/payments/DepositPaymentIntegrationTest.java`، `backend/app/src/test/java/com/matbakhy/app/orders/ReviewsAndFavoritesIntegrationTest.java`، `apps/customer-web/src/App.test.tsx`.
13. **اختبارات المالك E2E.** E2E-ORDER-027، E2E-ORDER-030، E2E-ORDER-031، E2E-ORDER-033، E2E-ORDER-035، E2E-ORDER-117، E2E-ORDER-120، E2E-FIN-039، E2E-FIN-042، E2E-DISC-110؛ من J1 لـ J4.
14. **الحالة.** IMPLEMENTED.
15. **القيود.** الاستعلام الدوري كل 10 ثواني معناه إن العميل ممكن يشوف حالة متأخرة لحد ~10 ثواني. الإشعارات داخل التطبيق بتتعرض في صفحة الحساب؛ ومفيش إيميل أو SMS أو Push (email/SMS/push) بيتم تسليمه من التطبيق (قرار المزوّد معلّق — شوف [التكامل والأحداث](../03-architecture/integration-and-events-ar.md)).

## CW-6 الحساب: الملف الشخصي، العناوين، المفضلة، الإشعارات، الدعم، الحذف

1. **الهدف.** إدارة البيانات الشخصية والتواصل مع الدعم.
2. **الطرف.** العميل.
3. **المتطلبات المسبقة.** مسجّل دخول.
4. **البداية.** `#/account`.
5. **الإجراءات.** تعديل الاسم المعروض واللغة (بيبعت `expectedVersion`)؛ إضافة/حذف عناوين؛ إزالة مفضلات؛ تعليم الإشعارات كمقروءة؛ فتح حالة دعم عن أوردر (التصنيفات `LATE, MISSING_ITEM, QUALITY, FOOD_SAFETY, CASH, HANDOVER, OTHER`) والرد في `#/support/<uuid>`؛ طلب حذف الحساب.
6. **الاستجابات.** كل قسم بيتحمّل لوحده بخطأه وإعادة محاولته. الحذف بيعطّل الحساب فورًا وهو Idempotent؛ وبعدها الواجهة بتسجّل الخروج.
7. **الـ API.** `PATCH /api/v1/me` (API-010)، `GET/POST/PATCH/DELETE /api/v1/me/addresses…` (من API-013 لـ API-016)، `GET /api/v1/me/favorites`، `GET /api/v1/notifications` و`PUT /api/v1/notifications/{id}/read` (API-031/032)، `GET/POST /api/v1/support/cases` و`GET/POST …/{caseId}[/messages]` (من API-035 لـ API-038)، `POST /api/v1/me/deletion-requests` (API-017).
8. **الـ Backend.** `ProfileController.java`، `ContactController.java`، `AccountLifecycleService.java`، `AccountErasureJob.java` (الهوية)، `NotificationController.java`، `SupportController.java` / `SupportService.java`.
9. **قاعدة البيانات.** `app_user`، `address`، `deletion_request` (V3، V21)، `favorite`، `notification`، `support_case`، `case_message` (V17).
10. **الانتقالات.** الحساب `ACTIVE → DELETION_REQUESTED → erased`؛ حالة الدعم `OPEN → IN_PROGRESS / WAITING_ON_CUSTOMER → RESOLVED → CLOSED`، ورد العميل بيعيد فتح حالة محلولة (E2E-OPS-105).
11. **الأخطاء.** `VERSION_CONFLICT` في الملف الشخصي (E2E-DISC-109)؛ الحقول ذات الامتياز في جسم الملف الشخصي بتترفض؛ عناوين عميل تاني مش ظاهرة (E2E-DISC-106).
12. **الاختبارات.** `backend/app/src/test/java/com/matbakhy/app/privacy/AccountErasureIntegrationTest.java`، `backend/app/src/test/java/com/matbakhy/app/support/cases/SupportIntegrationTest.java`، `backend/app/src/test/java/com/matbakhy/app/notifications/NotificationIntegrationTest.java`، `apps/customer-web/src/App.test.tsx` (MBQA-004).
13. **اختبارات المالك E2E.** E2E-DISC-106، E2E-DISC-107، E2E-DISC-108، E2E-DISC-109، E2E-DISC-114، E2E-ORDER-118.
14. **الحالة.** IMPLEMENTED. طلب الحذف بيبعت نص نسخة السياسة المكتوب ثابت `retention-policy-draft` (شوف `Account.tsx`)، يعني سياسة الاحتفاظ لسه مسودة.
15. **القيود.** نص الاحتفاظ القانوني النهائي لسه ماتوافقش عليه (شوف `docs/operations/04-privacy-and-recovery.md`).

## CW-7 تفضيل التحقق بخطوتين (MFA) على الويب

1. **الهدف.** العميل يشغّل أو يوقّف الـ MFA الاختياري تحت سياسة الأدمن.
2. **الطرف.** العميل (أي قيمة لسياسة الفئة).
3. **المتطلبات المسبقة.** مسجّل دخول؛ سياسة فئة CUSTOMER مش DISABLED عشان يغيّر أي حاجة.
4. **البداية.** `#/account` ← قسم الأمان (`apps/customer-web/src/Security.tsx`).
5. **الإجراءات.** قراءة وضع السياسة وحالتها؛ لو OPTIONAL ومقفول: "turn on" (محتاج Session فيها إثبات MFA صالح، وإلا رابط لتسجيل دخول Step-up)؛ لو مفعّل: "turn off" (محتاج MFA حديث، وبينهي Sessions المستخدم)؛ "manage authenticator" بيفتح رابط لوحة حساب Keycloak من الـ API.
6. **الاستجابات.** اللوحة بتعرض الوضع الفعّال (`OPTIONAL`/`REQUIRED`/`DISABLED`)، وهل الـ MFA مطلوب، وهل الـ Session دي متحقق منها. `STEP_UP_REQUIRED` بيعرض زرار "verify" مربوط بـ `/oauth2/authorization/keycloak?stepUp=true&returnTo=…`. تحت `REQUIRED`، إشعار بيقول إنه مايتقفلش.
7. **الـ API.** `GET /api/v1/me/security` (API-112)، `PUT /api/v1/me/security/mfa` والجسم `{enabled}` (API-113).
8. **الـ Backend.** `backend/app/src/main/java/com/matbakhy/app/mfa/MfaController.java`، `MfaPolicyService.java`، `MfaEnforcementFilter.java` (`backend/app/src/main/java/com/matbakhy/app/security/`).
9. **قاعدة البيانات.** `mfa_policy`، `user_mfa_preference`، `user_mfa_reset` (V31).
10. **الانتقالات.** التفضيل مقفول ↔ مفعّل؛ قرار التطبيق بيتحسب من جديد من قاعدة البيانات مع كل طلب.
11. **الأخطاء.** `MFA_REQUIRED_BY_POLICY`، `MFA_UNAVAILABLE` (التشغيل وهو DISABLED)، `STEP_UP_REQUIRED` (HTTP 403). السياسة الكاملة: [المصادقة وMFA](authentication-and-mfa-ar.md).
12. **الاختبارات.** `backend/app/src/test/java/com/matbakhy/app/mfa/MfaPolicyIntegrationTest.java`، `MfaPolicyResolveTest.java`، `backend/app/src/test/java/com/matbakhy/app/identity/MfaIdpTest.java` (Keycloak حقيقي)، `apps/customer-web/src/App.test.tsx` ("account security (D18)").
13. **اختبارات المالك E2E.** CFG-102، CFG-008، CFG-011، CFG-012؛ المجموعة الأساسية J7.
14. **الحالة.** IMPLEMENTED في الكود؛ الـ Migration بتاع الـ Realm الحي في Keycloak للـ Step-up خطوة Deployment محتاجة موافقة المالك (CFG-028). هل المعاينة المنشورة حاليًا مطبّق عليها: NOT VERIFIED.
15. **القيود.** TOTP بس؛ مفيش Recovery Codes (docs/security/mfa-policy.md).

## CW-8 الأخطاء والاسترجاع (شامل)

| الموقف | السلوك (المصدر) |
|---|---|
| عدم الاتصال | البانر `t.offline`؛ حالة `online` من أحداث `navigator.onLine` (`App.tsx`) |
| ضياع الاستجابة بعد "Place order" | `matbakhy.pendingPlacement` بيتحتفظ بيه؛ في `#/checkout` التطبيق بيسأل `GET /orders/by-request/{id}`؛ لو لقاه بيمسح السلة وبيفتح الأوردر؛ وفشل البحث بيسيب شاشة "غير مؤكد" مع إعادة محاولة (OBS-001) |
| 401 على نداء محمي | `onSignedOut` بيمسح الملف الشخصي؛ ورابط تسجيل الدخول بيظهر (`shared.tsx`) |
| 403 `STEP_UP_REQUIRED` | رسالة بتشاور على صفحة الحساب (`i18n.ts`، `shared.tsx`) |
| Version قديم | رسالة `VERSION_CONFLICT` مع Reload |
| بيئة Demo/preview (العرض/المعاينة) | إشعار دائم من `GET /api/v1/configuration/public` `environment.demo` أو علم البناء (`EnvironmentNotice.tsx`؛ E2E-DISC-117) |

## القيود المعروفة وفجوات التوثيق

* `docs/product/05-screen-specification.md` بيوصف شاشات مخططة؛ والصفحة دي بتسرد بس اللي بيرسمه `apps/customer-web/src`. لو فيه اختلاف، اعتمد على الكود.
* `contracts/endpoints.json` مابيسردش `POST /api/v1/auth/logout` خالص؛ هو مسار Framework (ملحوظة في Header الملف). اعتبر `SecurityConfiguration.java` هو المصدر.
* مالقيناش اختبار تطابق Arabic/English لكل النصوص غير الاختبارات المذكورة؛ تطابق مفاتيح `apps/customer-web/src/i18n.ts`: NOT VERIFIED.
