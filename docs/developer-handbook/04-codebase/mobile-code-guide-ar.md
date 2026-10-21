# دليل كود الموبايل (تطبيق العميل)

> **Owner role:** Frontend and mobile
> **Status:** PARTIALLY IMPLEMENTED — مسارات اكتشاف العميل والطلب والعربون موجودة؛ إرسال الـ Push وقبول الجهاز الفعلي وقارئ الشاشة مش موجودين
> **Language / اللغة:** [English](mobile-code-guide.md) · العربية

تطبيق العميل لـ Android/iOS هو `apps/customer-mobile/`: React Native 0.86 + Expo 57 مع React Navigation
(النسخ من `apps/customer-mobile/package.json`). **مفيش تطبيق موبايل للطبّاخين ولا للموظفين**؛ دول بيستخدموا ويب العمليات.
منظور المعمارية: [معمارية الموبايل](../03-architecture/mobile-architecture-ar.md)؛ مسار المستخدم:
[رحلة العميل على الموبايل](../02-user-journeys/customer-mobile-ar.md).

## البدء والهيكل

1. `apps/customer-mobile/index.ts` — `registerRootComponent(() => createElement(App))` (نقطة الدخول في الـ Production؛ من غير Test Doubles محقونة).
2. `apps/customer-mobile/src/App.tsx` — بيحمّل الإعدادات (`src/config.ts`: `EXPO_PUBLIC_API_URL` و `EXPO_PUBLIC_OIDC_ISSUER` و
   `EXPO_PUBLIC_OIDC_CLIENT_ID` والافتراضي `matbakhy-mobile`، وإعادة التوجيه `<scheme>://auth/callback`؛ النسخ المنشورة لازم تستخدم TLS)،
   وبيبني الـ `Session`، وبيعمل Mount لـ `AppProvider` (`src/AppContext.tsx`) والـ Navigators.
3. التنقل: تابات سفلية `Discover` و `Favorites` و `Orders` و `Account`؛ و Stack فيه `Meal` و `Cart` و `Order` و `Notifications` و `Support`
   (`src/screens/navigation.ts`). الروابط العميقة (Deep Links) في `src/linking.ts` (`orders/:orderId` و `meals/:mealId` و `cart`)؛ مسار رد الـ Auth مش تنقل أبدًا.

## الـ Authentication وتخزين الـ Session

| الموضوع | الملف | السلوك |
|---|---|---|
| تسجيل الدخول | `src/auth.ts` `signIn` | تدفق Authorization-code مع PKCE في **متصفح السيستم** (`expo-auth-session`)، و `prompt=login`، وجلسة متصفح مؤقتة؛ الـ Step-up الاختياري بيضيف `acr_values: "mfa 2"` (D18) |
| التحقق من إعادة التوجيه | `src/redirect.ts` | بيتحقق من الـ State وعنوان إعادة التوجيه قبل استخدام الكود |
| تخزين التوكنز | `src/tokenStore.ts` | **المخزن الآمن لنظام التشغيل** (`expo-secure-store`، `WHEN_UNLOCKED_THIS_DEVICE_ONLY`)، المفتاح `matbakhy.session.v1`؛ أي قيمة متخزنة مشوّهة بتتمسح وبتتعامل كأنك مسجّل خروج |
| دورة حياة الـ Session | `src/session.ts` | تجديد Access-token قبل انتهائه؛ رفض التجديد بينهي الـ Session |
| الـ API Client | `src/mobileApi.ts` | بيضيف `Authorization: Bearer`؛ الرد 401 بيشغّل التجديد/تسجيل الخروج، والرد 403 `STEP_UP_REQUIRED` بيشغّل بانر الـ Step-up |
| السلة والتسجيل المعلّق | `src/basket.ts` | مخزن آمن لكل حساب؛ بيتمسح عند تسجيل الخروج وتبديل الحساب |
| تبديل الحساب | `src/AppContext.tsx` (`accountEpoch`) | بيعيد Mount لكل اللي تحته لما الشخص يتغيّر عشان مفيش حالة شاشة تفضل عايشة (TC-048) |

الـ Backend بيتحقق من دول كسلسلة Bearer JWT من غير حالة (Stateless) (`SecurityConfiguration.bearerChain`، من غير كوكيز، وCSRF
معطّل للسلسلة دي بالتصميم).

## كود الطلب

| الملف | الدور |
|---|---|
| `src/screens/Cart.tsx` | طلب عرض السعر ومراجعة تغيّر السعر (`priceChanges` من الـ api-client) والإرسال؛ المفاتيح بتتثبّت وتتخزن **قبل** الطلب (`submit`، سطر ~325) |
| `src/placement.ts` | `place()` بتبعت `/api/v1/orders` بالمفاتيح المخزنة؛ و `recover()` بتسأل `GET /api/v1/orders/by-request/{clientRequestId}` بعد إعادة التشغيل أو ضياع الرد ومبتعيدش الإرسال إلا لو جالها 404 أكيد |
| `src/screens/Payment.tsx` | تعليمات العربون وتبليغ التحويل |
| `src/screens/Orders.tsx` | قائمة الأوردرات وتفاصيلها |
| `src/screens/Discover.tsx`, `Meal.tsx`, `Favorites.tsx` | التصفح |
| `src/screens/Notifications.tsx`, `Support.tsx`, `Account.tsx` | الصندوق وحالات الدعم والبروفايل/الأمان |
| `src/activity.ts`, `src/notificationPermission.ts` | النشاط المجهول؛ وصلاحية الإشعارات الاختيارية (إرسال الـ Push نفسه مش شغّال) |
| `src/useServer.ts`, `src/ui.tsx`, `src/i18n.ts`, `src/ErrorBoundary.tsx` | Hook تحميل البيانات والواجهة المشتركة والنصوص وحاجز الانهيار (Crash Boundary) |

## الاختبارات والتحقق على الجهاز (Native)

- Jest (`jest-expo`): `src/App.test.tsx` و `src/auth.test.ts` و `src/signIn.test.ts` و `src/ordering.test.ts`. التشغيل: `pnpm --filter @matbakhy/customer-mobile test`.
- التشغيلات على الجهاز (Emulator/Simulator) بتستخدم `apps/customer-mobile/scripts/*.sh`، وSQL Fixtures في `apps/customer-mobile/e2e-native/`،
  و XCTest Host بتاع iOS في `apps/customer-mobile/e2e-ios/`، والـ Workflow اسمه `.github/workflows/native-build.yml`. الأدلة
  بتتسجّل تحت `verification/runs/`. النتائج متلخّصة في [حالة التنفيذ](../07-reference/implementation-status-ar.md).
- الـ HTTP من غير تشفير (Cleartext) لتطوير localhost متفعّل بالـ Config Plugin اسمه `apps/customer-mobile/plugins/withLocalhostCleartext.cjs`.

## القيود المعروفة وفجوات التوثيق

- إرسال الـ Push: PLANNED. التطبيق يقدر يطلب صلاحية الإشعارات في نسخ التطوير بس (`src/config.ts` `notificationPrompt`)؛ ومفيش أدابتور FCM/APNs على الـ Backend.
- قبول الجهاز الفعلي وقارئ الشاشة (BLK-003 و BLK-004 في `docs/status/latest.md`) لسه ماتعملوش؛ و MB-034 و MB-035 (مرشحات الإصدار) حالتهم TODO في `planning/backlog.json`.
- Realm الـ Keycloak بتاع المعاينة `deploy/web-preview/identity/matbakhy-realm.preview.json` بيعرّف Clients اسمهم `matbakhy-web` و `matbakhy-admin` لكن مفيش Client اسمه `matbakhy-mobile`، في حين إن الـ Realm المحلي `infra/identity/matbakhy-realm.json` فيه؛ إزاي نسخ الموبايل بتعمل Authentication ضد المعاينة NOT VERIFIED هنا.
