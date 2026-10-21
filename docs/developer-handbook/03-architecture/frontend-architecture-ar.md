# معمارية الـ Frontend (ويب العميل وويب العمليات)

> **Owner role:** Frontend and mobile
> **Status:** IMPLEMENTED — تطبيقات الويب الاتنين شغّالين على الـ API؛ والـ Content Security Policy في وضع Report-Only، مش مفروضة
> **Language / اللغة:** [English](frontend-architecture.md) · العربية

تطبيقين Single-page بيشاركوا Stack واحد وحزمتين مشتركتين. تطبيق الموبايل متغطّي في [معمارية الموبايل](mobile-architecture-ar.md)؛ ودليل قراءة ملف بملف هو [دليل كود الـ Frontend](../04-codebase/frontend-code-guide-ar.md). الرسم: [معمارية الويب](diagrams-ar.md#4-معمارية-الويب).

## الـ Stack

| العنصر | القيمة | المصدر |
|---|---|---|
| اللغة / الـ UI | TypeScript 6.0.3, React 19.2.3 | `apps/customer-web/package.json` |
| الـ Bundler / سيرفر التطوير | Vite 8.3.4, `@vitejs/plugin-react`؛ ويب العميل على بورت 5173، وويب العمليات على 5174 (القيمة الافتراضية لـ `WEB_ALLOWED_ORIGINS` في الـ API بتسرد الاتنين) | `apps/customer-web/vite.config.ts`, `backend/app/src/main/resources/application.yaml` |
| الاختبارات | Vitest 5 مع jsdom وTesting Library؛ وويب العمليات بيضيف Playwright (`apps/operations-web/e2e`, `apps/operations-web/e2e-staging`) مع فحوصات إمكانية الوصول بـ axe | `apps/operations-web/package.json` |
| الخطوط | Cairo (مجموعات الحروف العربي واللاتيني بتتستورد في `main.tsx`) | `apps/customer-web/src/main.tsx` |
| الـ Router | **مفيش.** ويب العميل بيستخدم `window.location.hash` و Listener لـ `hashchange`؛ وويب العمليات بيستخدم State للتابات، مش URLs | `apps/customer-web/src/App.tsx` |
| إدارة الـ State | React state والـ Hooks بس؛ مفيش Redux/Zustand/React Query | `apps/customer-web/src/shared.tsx` (`useServer`) |
| الـ i18n | جداول نصوص `ar` و`en` مكتوبة بإيد ومعمول لها Types في `i18n.ts`؛ ومفيش مكتبة i18n | `apps/customer-web/src/i18n.ts`, `apps/operations-web/src/i18n.ts` |

الأوامر (من جذر الـ Repository، مع Node >= 24.21 وpnpm 10.34.6 حسب `package.json`): `pnpm install --frozen-lockfile`، `pnpm --filter @matbakhy/customer-web dev`، `pnpm --filter @matbakhy/operations-web dev`، `pnpm typecheck`، `pnpm lint`، `pnpm test`، `pnpm build`. شوف [التشغيل والـ Debug](../05-developer-workflow/run-and-debug-ar.md).

## الحزم المشتركة

* **`packages/api-client`** (`@matbakhy/api-client`): الدالة `createApiClient(options)` بتغلّف `openapi-fetch` بأنواع `paths` المتولّدة من `packages/api-client/src/generated/schema.ts`. وبتصدّر كمان `parseMoney` (بترفض الكسور العشرية والـ Strings والحقول الزيادة، و`EGP` بس) و`priceChanges(previousQuote, nextQuote)`، اللي بتشرح تغيّر السعر كفروقات في الوجبة / رسوم التوصيل / الضريبة عشان الـ UI عمره ما يلوم المطبخ على رسم منصة. الـ Schema بتتولّد من `contracts/openapi.yaml`؛ و`pnpm contract:check` بيفشل لو فيه Drift.
* **`packages/design-tokens`**: الملف `tokens.json` هو المصدر الوحيد لاسم البراند واللوحة اللونية والطباعة؛ و`packages/design-tokens/scripts/generate-css.mjs` بيكتب `src/generated/tokens.css` (اللي التطبيقين بيستوردوه)؛ و`direction(locale)` بترجّع `rtl` للعربي. التطبيقات بتقرا اسم البراند من هنا (`brand.name[locale]`)، و`vite.config.ts` بيحقنه في `index.html`. الحالة في `package.json`: مسودة في انتظار موافقة المالك O05 (الاتجاه البصري المعتمد موجود في `docs/design/approved/DESIGN.md`).

## طبقة الوصول للـ API

كل تطبيق ليه `src/api.ts` بتاعه فيه `createApi()`، وهم شبه متطابقين في التطبيقين:

* الـ Base URL من `VITE_API_BASE_URL` (الافتراضي `http://localhost:8080`). وفي صورة المعاينة بيتثبّت وقت الـ Build على `https://api.<preview domain>` (`deploy/web-preview/front.Dockerfile`).
* `credentials: "include"`: الـ Authentication بتتم عن طريق **الـ Cookie من نوع HttpOnly** اسمها `MATBAKHY_SESSION`؛ والتطبيق عمره ما بيشوف Token أو بيخزّنه.
* Middleware للطلبات بيحدد `Accept-Language` من متغيّر داخلي بتحدّثه `setLanguage` **في نفس الـ Event handler** اللي بيغيّر اللغة (مش من `document.lang`، اللي بيتحدّث بعد الـ Render؛ وده صلّح ملاحظة الـ QA اسمها MBQA-001)، وبينسخ Cookie اسمها `XSRF-TOKEN` (مقروءة) في `X-XSRF-TOKEN` مع كل طلب مش GET (حماية CSRF بطريقة double-submit؛ وجانب الـ API هو `csrf.spa()` في `SecurityConfiguration.webChain`).
* النداءات اللي بتغيّر البيانات بتمرّر `Idempotency-Key` بتتعمل بـ `newIdempotencyKey()` (`web-<uuid>` أو `ops-<uuid>`).
* `errorCode(error)` بترجّع كود الخطأ الثابت بتاع الـ API أو `"NETWORK"`.
* عرض الفلوس بيستخدم `Intl.NumberFormat` مع `ar-EG` / `en-EG` و`EGP`، وبيقسم القروش الصحيحة على 100 عند حافة العرض بس (`formatMoney`). والأوقات بتتنسّق بـ `Africa/Cairo`.

### التحميل والأخطاء

الـ Hook اسمه `useServer(ctx, load, key)` (`apps/customer-web/src/shared.tsx`) هو الـ Hook القياسي للقراءة: بيرجّع `loading` أو `error(code)` أو `ok(data)`؛ والـ `401` بينادي `ctx.onSignedOut`؛ والـ Fetch اللي بيرمي Exception بيتحول لكود `NETWORK`. والـ `<Status>` بيعرض الحالات التلاتة وبيعرض لينك Step-up لما الكود يكون `STEP_UP_REQUIRED`. و`useMutation` في نفس الملف بيعرض `errorText(t, code)` عند الفشل. و`errorText` بيحوّل أكواد الـ API (`VERSION_CONFLICT` و`PRICE_CHANGED` و`QUOTE_EXPIRED` و`STEP_UP_REQUIRED` وغيرها) لجمل بالعربي والإنجليزي. الـ UI عمره ما بيحسب أي صلاحية: بيعرض حالة السيرفر، والإجراءات المسموحة (`allowedActions` على الأوردرات)، والمواعيد النهائية.

## ويب العميل (`apps/customer-web`)

نقطة الدخول: `src/main.tsx` -> `src/App.tsx`.

| مسار الـ Hash | الـ Component | ملاحظات |
|---|---|---|
| `/` | `Browse.tsx` | الاكتشاف (Discovery)؛ مربعات النيّة (intent tiles)، والفلاتر، والترتيب. عام. |
| `/meal/<uuid>` | `Meal.tsx` | اختيار الـ Slot والكمية، والإضافة للسلة. بمفتاح لكل وجبة. |
| `/kitchen/<slug>` | `Kitchen.tsx` | صفحة المطبخ العامة |
| `/checkout` | `Checkout.tsx` | النمط (توصيل/استلام)، والـ Quote، ومراجعة تغيّر السعر، والشروط، وتسجيل الأوردر، واختيار العربون (`Payment.tsx`) |
| `/orders`, `/orders/<uuid>` | `Orders.tsx` | القايمة والتفاصيل مع الأحداث، وكود التسليم، ولوحة الدفع، ونقطة دخول الدعم |
| `/account` | `Account.tsx` | البروفايل، والتحقق من التليفون، والعناوين، وطلب الحذف، والأمان (`Security.tsx`)، وحالات الدعم |
| `/support/<uuid>` | `SupportCaseDetail` في `Account.tsx` | محادثة الحالة |

سلوكيات تستاهل تعرفها:

* **التصفّح عام**؛ أما الطلب فمحتاج Session وتليفون متحقق منه (السيرفر بيرجّع `CONTACT_VERIFICATION_REQUIRED`). و`App.tsx` بينادي `GET /api/v1/me` عند التحميل عشان يعرف لو فيه Session.
* **السلة** (`src/cart.ts`): محفوظة في `localStorage` تحت `matbakhy.cart`، مطبخ واحد وSlot واحد. الأسعار المخزّنة هناك تقديرات للعرض؛ والسيرفر هو اللي بيعمل Quote للإجمالي المعتمد. و**تسجيل أوردر معلّق** (`matbakhy.pendingPlacement`) بيخزّن `clientRequestId` و`idempotencyKey` *قبل* ما الطلب يتبعت، فإعادة المحاولة بعد رد ضايع مينفعش تلاقي أو تكرر غير نفس الأوردر (`GET /api/v1/orders/by-request/{clientRequestId}` موجود لده).
* **تغيّر السعر:** عند `409 PRICE_CHANGED` وقت التسجيل، `Checkout.tsx` بيعمل Quote جديد، وبيعرض `PriceChangeNotice` المبني من `priceChanges(...)`، وبيطلب من العميل يعلّم على "قبول الإجمالي الجديد" قبل ما الـ Quote الجديد يتسجّل. الرسم: [تغيّر السعر](diagrams-ar.md#10-تغير-السعر-وإعادة-تأكيد-الـ-quote-القديم).
* **تسجيل الخروج** (MBQA-003): بينادي `POST /api/v1/auth/logout`، وبيمسح السلة والأوردر المعلّق، وبعدين بيروح للينك إنهاء الـ Session عند مزوّد الهوية اللي راجع كـ `idpLogoutUrl`؛ وبيحذّر الأول لو فيه أوردر مش متأكد. و Handler لـ `pageshow` بيعيد تحميل الصفحات المسترجعة من الـ back-forward cache.
* **النشاط العضوي** (`activity.ts`): `POST /api/v1/activity` من نوع fire-and-forget بـ id زائر عشوائي لكل تاب؛ والسيرفر بيخزّن Hash يومي بمفتاح بس.
* **إشعار الديمو:** `EnvironmentNotice` بيظهر لما التطبيق يتبني بـ `VITE_DEMO_NOTICE=true` أو لما `GET /api/v1/configuration/public` يقول `environment.demo`.

## ويب العمليات (`apps/operations-web`)

تطبيق واحد بيخدم جمهورين. بعد التحميل، الدالة `checkSession()` في `src/App.tsx` بتنادي `GET /api/v1/me` وبعدها بتجرّب تمان Endpoints بالتوازي (لوحة الطبّاخ، وops cooks، والكتالوج، والحالات، والأوردرات، وكشوف المالية بـ kitchen id فاضي، والإعدادات، والموظفين) بالإضافة لـ `GET /api/v1/me/security`. **التابات اللي بتظهر بيحددها أنهي Probes السيرفر سمح بيها** (`allowedTabs`)، مش دور على جانب الـ Client: 200 معناها مسموح؛ وProbe المالية بيستخدم الفرق بين 404 و403. ولو أي Probe رد بـ `STEP_UP_REQUIRED` التطبيق بيعرض شاشة Step-up فيها لينك لـ `stepUpUrl(...)` بدل بيانات ناقصة.

| الجمهور | التابات | مجلد المصدر |
|---|---|---|
| الطبّاخ (مطبخ موافق عليه) | dashboard، orders، meals، slots، statement، insights، settings؛ والمدفوعات جوه orders وsettings | `apps/operations-web/src/cook` |
| الموظفين (بالـ Grant) | reviews (طلبات انضمام الطبّاخين)، وإشراف الكتالوج، والحالات، وأوردرات العمليات، والمالية (بما فيها مدفوعات العربون)، والإعدادات، وصلاحيات الموظفين | `apps/operations-web/src/ops` |
| كل واحد مسجّل دخول | الأمان (تفضيل الـ MFA بتاعه؛ والسياسة وإعادة الضبط للأدمن) | `apps/operations-web/src/ops/Security.tsx` |

المتقدّم من غير مطبخ بيشوف شاشة الـ Onboarding (`cook/Onboarding.tsx`). ولوحة الطبّاخ بتوفّر `serverTime`، واللي التطبيق بيحسب منه فرق الساعة (`serverOffsetMs`) عشان العدّ التنازلي لـ `acceptBy` يتبع وقت السيرفر مش ساعة الجهاز.

### الفروق بين التطبيقين

| الجانب | ويب العميل | ويب العمليات |
|---|---|---|
| الجمهور | عملاء مجهولين ومسجّلين دخول | الطبّاخين والموظفين بس (الـ Proxy بيحصره في فئة شبكة الموظفين على المعاينة) |
| التنقّل | مسارات Hash، وشريط تنقّل سفلي | تابات مشتقة من الـ Endpoints اللي السيرفر سمح بيها |
| الصفحات العامة | أيوه (التصفّح، الوجبة، المطبخ) | مفيش؛ كل حاجة محتاجة Session |
| التخزين المحلي | السلة، والأوردر المعلّق، وid الزائر في `sessionStorage` | مفيش حاجة اتلقت غير React state (NOT VERIFIED بشكل شامل) |
| بادئة Idempotency key | `web-` | `ops-` |
| طبقة اختبار إضافية | Vitest بس | Vitest بالإضافة لـ Playwright E2E وSmoke على الـ Staging |
| الـ Hosts على المعاينة | `app.<domain>` | `ops.<domain>` |

## الـ RTL والتوطين (Localisation)

الملف `index.html` بيتسلّم بـ `lang="ar" dir="rtl"`؛ و Effects في `App.tsx` بتحدد `document.documentElement.lang` و`dir` من `direction(locale)` وبتحدد العنوان من Tokens البراند. العربي هو اللغة الافتراضية؛ وزرار بيبدّل للإنجليزي. التخطيط بيستخدم CSS (`app.css`) مع Tokens؛ وكود الـ Components مبيكتبش left/right بشكل ثابت (NOT VERIFIED بمراجعة CSS كاملة؛ ودليل الـ RTL البصري موجود في مستندات الجودة تحت `docs/quality`). والنصوص اللي محتاجة أرقام بتستخدم دوال، مثلًا `kitchenChangedPrice(title, before, after)`.

## Headers الأمان والـ CSP

التطبيقات بتتقدّم من Caddy مع HSTS و`X-Content-Type-Options` و`Referrer-Policy` و`X-Frame-Options: DENY` و`X-Robots-Tag: noindex`، و Header اسمه `Content-Security-Policy-Report-Only`. السياسة **لسه مش مفروضة**؛ وخطوات الترقية في `docs/security/csp-rollout.md`. التفاصيل في [الهوية والأمان](identity-and-security-ar.md#تأمين-المتصفح-والـ-proxy).

## القيود المعروفة وفجوات التوثيق

* `packages/design-tokens/package.json` لسه بيسمّي الـ Tokens مسودة في انتظار O05 بينما `CLAUDE.md` مسجّل الاتجاه المعتمد terracotta/Cairo؛ والكود بيستورد خط Cairo والـ Tokens، فاعتبر وصف الحزمة قديم.
* مفيش مكتبة Routing في الـ Frontend، فالروابط العميقة (Deep links) في ويب العميل هي URLs بـ Hash (`#/orders/<id>`).
* ويب العمليات بيجرّب لحد تسع Endpoints مع كل تحميل؛ ده بسيط بس كتير الكلام، وإضافة منطقة موظفين جديدة محتاجة إضافة Probe وتاب في `App.tsx`.
