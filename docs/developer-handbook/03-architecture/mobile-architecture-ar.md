# معمارية الموبايل (تطبيق العميل)

> **Owner role:** Frontend and mobile
> **Status:** PARTIALLY IMPLEMENTED — تطبيق Expo للعميل بس بمعرّفات تطوير؛ فيه دليل من الـ Emulator/Simulator في الـ CI، لكن مفيش معرّفات للمتاجر ولا Push ولا تحقق على أجهزة فعلية
> **Language / اللغة:** [English](mobile-architecture.md) · العربية

فيه تطبيق موبايل واحد: `apps/customer-mobile` ("Matbakhy Dev"، الـ Package والـ Bundle id هو `com.matbakhy.customer.dev`). الطبّاخين والموظفين بيستخدموا ويب العمليات؛ ومفيش تطبيق موبايل للطبّاخ أو الموظفين. دليل على مستوى الملفات: [دليل كود الموبايل](../04-codebase/mobile-code-guide-ar.md). الرسم: [معمارية الموبايل](diagrams-ar.md#5-معمارية-الموبايل). الرحلات من ناحية المستخدم: [رحلة موبايل العميل](../02-user-journeys/customer-mobile-ar.md).

## الـ Stack

| العنصر | القيمة | المصدر |
|---|---|---|
| الـ Framework | Expo 57 (Managed workflow مع Config plugins)، React Native 0.86.3، React 19.2.3 | `apps/customer-mobile/package.json` |
| التنقّل | React Navigation 7: تابات سفلية جوه Native stack | `apps/customer-mobile/src/App.tsx`, `apps/customer-mobile/src/screens/navigation.ts` |
| الـ Authentication | `expo-auth-session` (PKCE) و`expo-web-browser` | `apps/customer-mobile/src/auth.ts` |
| التخزين الآمن | `expo-secure-store` | `apps/customer-mobile/src/tokenStore.ts` |
| الاختبارات | Jest (`jest-expo`) مع Testing Library لـ React Native: `src/App.test.tsx` و`src/auth.test.ts` و`src/signIn.test.ts` و`src/ordering.test.ts` | `apps/customer-mobile/package.json` |
| الكود المشترك | حزم الـ Workspace اسمها `@matbakhy/api-client` و`@matbakhy/design-tokens` | نفس المصدر |

الأوامر (جذر الـ Repository): `pnpm --filter @matbakhy/customer-mobile start` (سيرفر Expo للتطوير)، و`pnpm --filter @matbakhy/customer-mobile test`، و`pnpm --filter @matbakhy/customer-mobile typecheck`، و`pnpm --filter @matbakhy/customer-mobile bundle:native` (Hermes bundles للمنصتين، بيستخدمها `scripts/check_workspace.sh`). الـ Repository مفيهوش مشروع `android/` أو `ios/` متعمل له Commit: الـ CI بيولّدهم بـ `expo prebuild`.

## الكود المشترك مقابل الكود الخاص بكل منصة

كل كود التطبيق هو TypeScript مشترك بين المنصتين. الأجزاء الخاصة بكل منصة هي:

* الـ Android config plugin اسمه `apps/customer-mobile/plugins/withLocalhostCleartext.cjs`، وبيشتغل بس لما `extra.environment` تكون `development`: بيسمح بـ HTTP من غير تشفير (cleartext) لـ `localhost` و`10.0.2.2` عشان الـ Emulator يقدر يوصل لمزوّد الهوية والـ API المحليين. إعدادات الإصدار للبيئات التانية عمرها ما بتاخده.
* iOS: جلسة Authentication من نوع `ephemeral` (`preferEphemeralSession: true`) عشان متصفح النظام ميعيدش استخدام Cookie المزوّد بتاعة الشخص اللي قبله من غير ما يبان.
* أصول الاختبار الـ Native (في الـ CI بس، مش بتتشحن): `apps/customer-mobile/scripts/*.sh` (رحلات كاملة للتشغيل والـ Authentication والأوردر على Android وiOS)، و`apps/customer-mobile/e2e-ios` (مشروع XcodeGen فيه تطبيق مضيف وحزمة XCUITest بتشغّل التطبيق المتسطّب)، و`apps/customer-mobile/e2e-native` (SQL للـ Seed التجريبي، وSQL لتغيّر السعر والعربون، و`slow_proxy.py`، وهو TCP proxy بيأخّر الاتصال بيستخدم لمحاكاة "السيرفر عمل الأوردر بس التطبيق مسمعش رد").

## الإعدادات

الملف `apps/customer-mobile/src/config.ts` بيبني `AppConfig` من متغيّرات `EXPO_PUBLIC_*` مع قيم افتراضية للتطوير من `app.json`: `EXPO_PUBLIC_API_URL` (الافتراضي `http://localhost:8080`) و`EXPO_PUBLIC_OIDC_ISSUER` و`EXPO_PUBLIC_OIDC_CLIENT_ID` (الافتراضي `matbakhy-mobile`) و`EXPO_PUBLIC_ENVIRONMENT`. وعنوان الـ Redirect هو `<scheme>://auth/callback` (الـ Scheme هو `matbakhy-dev`). خارج `development` لازم الـ API والـ Issuer يكونوا `https://` وإلا `loadConfig` بترمي `ConfigError`. مفيش أي سر في التطبيق: هو OIDC client عام بيستخدم PKCE.

## التنقّل والـ Deep links

* التابات: Discover وFavorites وOrders وAccount. شاشات الـ Stack: Meal وCart وOrder وNotifications وSupport (`screens/navigation.ts`).
* الـ Deep links (`src/linking.ts`) بتستخدم الـ Scheme بتاع التطبيق: `matbakhy-dev://orders/<orderId>` و`.../meals/<mealId>` و`.../cart` و`.../orders`. ومسار الـ Callback بتاع `auth/` بيتفلتر وعمره ما بيتعامل معاه كتنقّل. الرابط بيسمّي شاشة بس؛ والسيرفر هو اللي بيقرر الظهور (أوردر حساب تاني هيبقى "مش موجود").
* الـ `NavigationContainer` بيتحدد بمفتاح `accountEpoch`، فتغيير الحساب بيعيد تركيب التنقّل وبيرمي State الشاشات بتاع الشخص اللي قبله.

## طبقة الـ State والـ Session والشبكة

* الملف `AppContext.tsx` بيملك اللغة ومرحلة الـ Authentication (`loading` و`signedOut` و`signedIn`) وعلم الـ Step-up وStore التسوّق. و`useServer.ts` هو Hook القراءة؛ و`ui.tsx` فيه الـ Components المشتركة؛ و`ErrorBoundary.tsx` بيمسك أخطاء الـ Render.
* **الـ Session** (`session.ts`): كائن `Session` في الذاكرة مدعوم بـ `tokenStore.ts`. الـ Tokens بتتحفظ في الـ Secure store بتاع النظام بس (`WHEN_UNLOCKED_THIS_DEVICE_ONLY`)؛ والقيمة المخزّنة التالفة بتتمسح وبتتعامل كأنك مسجّل خروج. و`freshAccessToken()` بتجدد الـ Access token (مدته 5 دقايق) قبلها بـ 30 ثانية باستخدام Refresh grant بتاع المزوّد (تجديد مشترك واحد للنداءات المتزامنة)؛ وخطأ OAuth زي `invalid_grant` بينهي الـ Session، أما فشل النقل (Transport) فبيخليها. والـ Session بتمسح كل Cache خاص لما الـ Subject يتغيّر أو عند تسجيل الخروج (قاعدة تبديل الحساب، السيناريو TC-048).
* **الشبكة** (`mobileApi.ts`): الدالة `createMobileApi` بتبني الـ Client المكتوب بأنواع؛ و Middleware للطلبات بيحدد `Accept-Language` و`Authorization: Bearer <token>`؛ و Middleware للردود بيحوّل `401` لتسجيل خروج محلي و`403` بكود `STEP_UP_REQUIRED` لبانر الـ Step-up (بينسخ الرد عشان المنادين يفضلوا يقدروا يقرأوا الجسم). الـ Middleware عن قصد بيرجّع لا حاجة، لإن إرجاع الكائنات الأصلية كسر فحص `instanceof Response` في `openapi-fetch` على iOS (تعليق في الملف، التشغيل الـ Native رقم 21).
* **الأوردر** (`basket.ts` و`placement.ts`): السلة والتسجيل المعلّق (client request id بالإضافة لـ idempotency key) بيتحفظوا في الـ Secure store لكل Subject، وبيتمسحوا عند تسجيل الخروج. والدالة `place()` بترجّع `placed` أو `uncertain` (مفيش إجابة قاطعة؛ المفاتيح محفوظة، وإعادة المحاولة مينفعش تكرر) أو `rejected(code)` أو `stepUp`. بعد نتيجة uncertain الشاشة بتعرض "اتأكد وحاول تاني" اللي بتتحل عن طريق `GET /api/v1/orders/by-request/{clientRequestId}` (السيناريو TC-049، ومتجرّب بسكريبت الـ CI اسمه `apps/customer-mobile/scripts/order-roundtrip-android.sh`).
* **الفلوس والوقت** بيتبعوا نفس قواعد الويب: قروش (piastres) صحيحة، و`Africa/Cairo`.

## مسار الـ Authentication (OIDC، Native)

Authorization code مع PKCE (S256) في متصفح النظام (ASWebAuthenticationSession / Custom Tabs، وعمره ما يكون Web view مدمج)، و`prompt=login`، والـ Scopes هي `openid profile email`. الدالة `checkRedirect` بتتحقق بصرامة من URL الـ Callback والـ State قبل ما الكود يتبدل. والـ Tokens الناتجة بتروح للتخزين الآمن. والـ API بيتحقق من الـ Bearer token على السلسلة اللي من غير Session (الـ Issuer، والـ Audience `matbakhy-api`، والتوقيع، وانتهاء الصلاحية). وتسجيل الخروج بيلغي الـ Refresh token لو المزوّد بيدعم ده، وبعدين بيمسح الحالة المحلية. والـ Step-up بيعيد تسجيل الدخول بـ `acr_values=mfa 2`. رسومات التسلسل: [OIDC](diagrams-ar.md#6-الـ-authentication-oidc) و[Step-up للـ MFA](diagrams-ar.md#7-رفع-الـ-mfa-step-up).

## إشعارات الـ Push

DISABLED. الملف `notificationPermission.ts` ممكن يطلب من النظام إذن الإشعارات (في بيلدات التطوير بس افتراضيًا، `notificationPrompt`) لكن مفيش Device token بيتطلب أو بيتسجّل، وعلم الـ Push في السيرفر قيمته الافتراضية مقفولة ووراه Adapter بيكتب في الـ Log بس. الإشعارات داخل التطبيق (الـ Inbox على السيرفر) هي القناة الشغّالة.

## أصول الـ Native build واختبار الأجهزة

| الأصل | بيعمل إيه | الحالة |
|---|---|---|
| `.github/workflows/native-build.yml` | Android: `expo prebuild` وAPK Debug وRelease بـ Gradle وفحص تشغيل على Emulator ورحلة تسجيل دخول كاملة ورحلة أوردر كاملة. iOS: Prebuild وبناء Xcode وتشغيل على Simulator واختبارات UI لتسجيل الدخول والأوردر ولقطات شاشة | IMPLEMENTED (دليل CI على Runners مستضافة على GitHub؛ موقّع بـ Debug-keystore، مش بيلدات متاجر) |
| `apps/customer-mobile/scripts/launch-android.sh` و`launch-ios.sh` و`auth-roundtrip-*.sh` و`order-roundtrip-*.sh` | الفحوصات اللي فوق، وتشتغل بس بالشكل ده في الـ CI (بتشغّل الـ Stack المحلي عن طريق `scripts/dev.sh up`) | IMPLEMENTED للـ CI |
| الاختبار على الأجهزة الفعلية | ماتعملش؛ و`docs/security/mfa-policy.md` بيقول صراحةً إن مفيش تشغيل على جهاز فعلي أو Simulator لرحلة موبايل مفعّل فيها الـ MFA | NOT VERIFIED |
| معرّفات المتاجر، والتوقيع، وEAS/store submission | قرار المالك O08 لسه مفتوح؛ و`app.json` بيقول "Development identifiers only" | PLANNED |

## القيود المعروفة وفجوات التوثيق

* الـ Realm بتاع Keycloak للمعاينة (`deploy/web-preview/identity/matbakhy-realm.preview.json`) مفيهوش Client اسمه `matbakhy-mobile` (فيه `matbakhy-web` و`matbakhy-admin` بس)، بينما الـ Realm المحلي (`infra/identity/matbakhy-realm.json`) فيه، وسكريبت Migration الـ MFA بيذكر الاتنين. وهل تطبيق الموبايل يقدر يسجّل دخول على المعاينة المنشورة هو NOT VERIFIED.
* مفيش شاشة أمان (Security) في الموبايل (شغّل أو اقفل الـ MFA من الويب أو من Account console بتاع Keycloak)، حسب `docs/security/mfa-policy.md`.
* الـ RTL في الموبايل بيتطبّق لكل Component بـ `textAlign`/`writingDirection` (`apps/customer-mobile/src/ui.tsx`) بدل `I18nManager.forceRTL`؛ ومتلقاش أي استخدام لـ `I18nManager`، فانعكاس تخطيط عناصر التنقّل الـ Native هو NOT VERIFIED على الأجهزة.
* `docs/security/mfa-policy.md` بيشير لخطة اختبار أجهزة iOS beta في docs/mobile/ios-beta-device-test-plan.md (مكتوبة من غير لينك عن قصد)؛ والملف ده مش موجود في الـ Repository.
* اختبارات الوحدة للموبايل بتغطي مساعدات تسجيل الدخول والـ Session والأوردر؛ ومعظم الشاشات (Favorites وSupport وPayment وNotifications) مفيهاش ملف اختبار مخصص (`src/App.test.tsx` بس هو اللي بيعمل Render للتطبيق).
