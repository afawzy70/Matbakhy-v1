# دليل كود الـ Frontend (ويب العميل وويب العمليات)

> **Owner role:** Frontend and mobile
> **Status:** IMPLEMENTED — تطبيقين الويب React/Vite زي ما هم موجودين في `apps/`
> **Language / اللغة:** [English](frontend-code-guide.md) · العربية

تطبيقين ويب بيشتركوا في نفس الـ Stack ونفس الاتفاقيات: React 19 و TypeScript 6 و Vite 8 و Vitest، والـ Typed Client
`@matbakhy/api-client`، وتوكنز البراند من `@matbakhy/design-tokens`، وخط Cairo، والعربي RTL هو الافتراضي مع الإنجليزي LTR
(النسخ من `apps/customer-web/package.json` و `apps/operations-web/package.json`).
منظور المعمارية: [معمارية الـ Frontend](../03-architecture/frontend-architecture-ar.md). مسارات المستخدم:
[رحلة ويب العميل](../02-user-journeys/customer-web-ar.md) و[عمليات الطبّاخ](../02-user-journeys/cook-operations-ar.md) و
[إدارة الـ Backoffice](../02-user-journeys/backoffice-admin-ar.md).

## الاتفاقيات المشتركة

- الواجهة بتعرض قرارات السيرفر. هي مبتحسبش أبدًا السعر المعتمد ولا السعة ولا الدور ولا الانتقال المسموح. أي
  إجمالي فرعي (Subtotal) على الـ Client بيتكتب عليه إنه تقدير ويتبدّل بعرض السعر (Quote) من السيرفر (شوف [تنفيذ التسعير](pricing-implementation-ar.md)).
- الـ Authentication هو Session المتصفح: كوكي HttpOnly (`MATBAKHY_SESSION`) بيحطه الـ API بعد تسجيل الدخول بـ OIDC، زائد كوكي
  `XSRF-TOKEN` مقروء بيرجّعه كل طلب مش GET في الـ Header `X-XSRF-TOKEN`. شوف `apps/customer-web/src/api.ts:21-41`
  (`createApi`). تطبيق العمليات فيه نفس الـ Wrapper في `apps/operations-web/src/api.ts`.
- كل طلب بيحط `Accept-Language` من اللغة المختارة (بتتحط في نفس الحدث اللي بيغيّر الحالة، MBQA-001).
- مفيش مكتبة Router على الـ Client: `apps/customer-web/src/App.tsx` بيقرا الـ Hash بتاع الـ URL ويطابقه بتعبيرات منتظمة (Regular Expressions)
  (`/meal/<uuid>` و `/orders/<uuid>` و `/kitchen/<slug>` و `/support/<uuid>` و `/checkout` و `/orders` و `/account` و `/`).
- النصوص موجودة في `i18n.ts` لكل تطبيق (جداول عربي وإنجليزي)؛ والفلوس بتتنسّق من الوحدات الصغرى الصحيحة بـ
  `formatMoney` (`apps/customer-web/src/api.ts:64`) وبيتعمل لها Parse بـ `packages/api-client/src/money.ts`.
- مفاتيح الـ Idempotency: `newIdempotencyKey()` (`apps/customer-web/src/api.ts:54`) و `crypto.randomUUID()` لـ `clientRequestId`.

## ويب العميل — `apps/customer-web/`

| الملف | المسؤولية |
|---|---|
| `src/main.tsx` | بيعمل Mount لـ `App` في `StrictMode`؛ بيستورد الخطوط و `@matbakhy/design-tokens/tokens.css` و `app.css` |
| `src/App.tsx` | الـ Shell واللغة/الاتجاه والـ Routing بالـ Hash وفحص الـ Session (`GET /api/v1/me`) والتنقل ومسار حالة الدعم |
| `src/Browse.tsx` | الاكتشاف (Discovery): البحث والمنطقة وسقف السعر والترتيب والـ Paging على السيرفر (`GET /api/v1/meals`) |
| `src/Meal.tsx`, `src/Kitchen.tsx` | تفاصيل الوجبة مع الـ Slots؛ وصفحة المطبخ العامة |
| `src/cart.ts` | السلة محفوظة في `localStorage` (`matbakhy.cart`) وواجهة قاعدة مطبخ واحد وSlot واحد (السيرفر بيعيد الفحص) |
| `src/Checkout.tsx` | التحقق من التليفون عند الدفع (`/api/v1/me/phone/challenges`) وطلب عرض السعر ومراجعة تغيّر السعر وتسجيل الأوردر بمفاتيح ثابتة والاسترجاع بـ `clientRequestId` (شوف [تنفيذ الأوردر](order-implementation-ar.md)) |
| `src/Payment.tsx` | تعليمات العربون وتبليغ "أنا بعتّه"؛ صياغة أمينة (تبليغ العميل مش دفع) |
| `src/Orders.tsx` | `OrderList` و `OrderDetail` (الخط الزمني وكود التسليم والإلغاء ونقطة دخول الدعم) و `ReviewForm` |
| `src/Account.tsx` | البروفايل والمفضلة وصندوق الإشعارات وحالات الدعم (`SupportCaseDetail`) والعناوين وطلب الحذف |
| `src/Security.tsx` | تفضيل التحقق بخطوتين ورابط الـ Step-up (`/oauth2/authorization/keycloak?stepUp=true`) |
| `src/activity.ts` | نشاط تصفح مجهول الهوية ومنزوع التكرار (`POST /api/v1/activity`) |
| `src/EnvironmentNotice.tsx` | بانر لبيئات البيانات التجريبية غير الحقيقية/المعاينة |
| `src/App.test.tsx` | اختبارات Vitest + Testing Library بـ `fetch` وهمي (جدول Routes بصيغة `"METHOD /path": () => response`) |

تفاصيل عن التسجيل تستاهل تعرفها: التسجيل المعلّق `{clientRequestId, idempotencyKey, quoteId, terms, cancellation}` بيتكتب
في `localStorage` (`matbakhy.pendingPlacement`) قبل ما الطلب يتبعت، فإعادة التحميل أو إعادة المحاولة بتستخدم نفس المفاتيح
(`apps/customer-web/src/Checkout.tsx:263-299`). لو الرد `PRICE_CHANGED` بيتم تجاهل المفاتيح المعلّقة وبيطلب Quote جديد وبيعرض
الفرق اللي بيحسبه `priceChanges()` من `packages/api-client/src/priceChanges.ts`؛ ومبيعيدش الإرسال بصمت أبدًا.

## ويب العمليات — `apps/operations-web/`

تطبيق واحد بيخدم **مساحة عمل الطبّاخ** و**الـ Backoffice بتاع الموظفين** مع بعض. `src/App.tsx` بيقرر يعرض إيه عن طريق فحص الـ
API مرة واحدة بعد تسجيل الدخول (`checkSession`، `src/App.tsx:~55-100`): `GET /api/v1/cook/dashboard` (الرد 403 معناه مفيش مطبخ
معتمد)، `GET /api/v1/ops/cooks` و `/ops/catalogue` و `/ops/cases` و `/ops/orders` و `/ops/finance/statements` (الرد 404 لمطبخ
مجهول معناه إن المتصل عنده وصول مالي)، `/ops/configuration` و `/ops/staff` و `/me/security`. التابات الظاهرة
هي بالظبط اللي السيرفر سمح بيها (`allowedTabs`). إخفاء تاب شكل بس؛ السلطة للـ API.

| المنطقة | الملفات |
|---|---|
| الطبّاخ | `src/cook/Dashboard.tsx` و `Orders.tsx` (قبول/رفض/تحضير/جاهز/إرسال/تسليم/إلغاء/تقرير الكاش) و `Meals.tsx` (CRUD ورفع الصور وتاريخ الأسعار) و `Slots.tsx` و `Payments.tsx` (استلام العربون والمشكلة والاسترداد وبروفايل الدفع) و `Statement.tsx` و `Insights.tsx` و `Settings.tsx` و `Onboarding.tsx` (الطلب)، والمساعدات المشتركة `shared.tsx` |
| الموظفين | `src/ops/Reviews.tsx` (طلبات الطبّاخين وإشراف المراجعات) و `Catalogue.tsx` و `Cases.tsx` و `OpsOrders.tsx` و `Finance.tsx` (التحقق من الكاش والاستردادات والتسويات وكشوف الحساب) و `Payments.tsx` (طابور مراجعة وتسوية مدفوعات العربون والموافقة على المستلم) و `Configuration.tsx` و `Staff.tsx` و `Security.tsx` (سياسة الـ MFA وإعادة الضبط) |
| الـ Step-up | لما السيرفر يرد بـ `STEP_UP_REQUIRED` التطبيق بيعرض شاشة Step-up بدل البيانات |
| الاختبارات | `src/App.test.tsx` (Vitest)؛ رحلات Playwright في `apps/operations-web/e2e/` (`cook-workspace.e2e.ts` و `customer-journey.e2e.ts` و `keyboard.e2e.ts` و `money-reflow.e2e.ts` و `operations-layout.e2e.ts`)، والإعدادات `apps/operations-web/playwright.config.ts`؛ و Smoke الـ Staging في `apps/operations-web/e2e-staging/smoke.staging.ts` |

رحلات Playwright بتختبر ويب العميل كمان (`customer-journey.e2e.ts`)، وعشان كده هي موجودة هنا. بتشتغل
على Stack محلي حقيقي بيبدأه `scripts/dev.sh` (شوف [التشغيل والـ Debug](../05-developer-workflow/run-and-debug-ar.md)).

## تنفّذ فين

- شاشة جديدة للعميل: Component تحت `apps/customer-web/src/`، وتعبير منتظم للـ Route في `App.tsx`، والنصوص في `i18n.ts` (باللغتين)، والاختبار في `App.test.tsx`.
- قسم جديد للطبّاخ/الموظفين: Component تحت `apps/operations-web/src/cook/` أو `ops/`، والفحص + التاب في `App.tsx`، والنصوص في `i18n.ts`.
- حقل محتاجه ناقص من الاستجابة: غيّر الـ Backend و `contracts/openapi.yaml` الأول، وولّد الـ Client من جديد ([عقود الـ API](api-contracts-ar.md))؛ ماتستخدمش Type cast عشان تتجاوز الأنواع المتولّدة.
- التنسيق (Styling): التوكنز من `packages/design-tokens/tokens.json` (شوف [المكتبات المشتركة](shared-libraries-ar.md))؛ و `scripts/check_design.sh` بيحرس البراند.

## القيود المعروفة وفجوات التوثيق

- مطابقة معايير إمكانية الوصول (Accessibility) مش كاملة: TC-047 (الكيبورد/التركيز/قارئ الشاشة) حالته BLOCKED وMB-029 حالته BLOCKED في `planning/backlog.json`؛ في اختبار Playwright للكيبورد (`apps/operations-web/e2e/keyboard.e2e.ts`) لكن التحقق بقارئ الشاشة لسه ماتعملش.
- نطاقات الأسطر المقتبسة لـ `apps/operations-web/src/App.tsx` تقريبية (`~`)؛ دوّر على `checkSession` بالاسم.
