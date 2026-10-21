# رحلات المستخدم: موبايل العميل (أندرويد وiOS)

> **Owner role:** Frontend and mobile
> **Status:** PARTIALLY IMPLEMENTED — تطبيق Expo (`apps/customer-mobile`) بينفّذ التصفح، وتسجيل الدخول، والسلة، وتسجيل الأوردر، والتتبّع، والإشعارات، والدعم، والـ Step-up؛ مفيهوش شاشة إعدادات MFA، ولا مميزات للطبّاخ، ومفيش تحقق على جهاز فعلي متسجّل.
> **Language / اللغة:** [English](customer-mobile.md) · العربية

تطبيق العميل هو Codebase واحد بـ React Native (Expo) للمنصتين (`apps/customer-mobile/package.json`: Expo 57، React Navigation 7). **مفيش تفرّع بـ `Platform.OS` في `apps/customer-mobile/src`** (اتدوّر)؛ فاختلافات أندرويد مقابل iOS جاية من جلسة المتصفح في نظام التشغيل، والتخزين الآمن، وأدوات الـ CI، وهي مسرودة في [أندرويد مقابل iOS](#أندرويد-مقابل-ios). المعمارية: [معمارية الموبايل](../03-architecture/mobile-architecture-ar.md)، جولة الكود: [دليل كود الموبايل](../04-codebase/mobile-code-guide-ar.md). المقابل على الويب: [ويب العميل](customer-web-ar.md).

## الشاشات والتنقل (`apps/customer-mobile/src/App.tsx`، `apps/customer-mobile/src/screens/navigation.ts`)

| الـ Navigator | الشاشة | الملف | الغرض |
|---|---|---|---|
| تبويبات سفلية | Discover | `apps/customer-mobile/src/screens/Discover.tsx` | بحث وتصفح الوجبات (Search/browse) |
| تبويبات سفلية | Favorites | `apps/customer-mobile/src/screens/Favorites.tsx` | المفضلة الخاصة بيه |
| تبويبات سفلية | Orders | `apps/customer-mobile/src/screens/Orders.tsx` | قايمة الأوردرات (`OrdersScreen`) |
| تبويبات سفلية | Account | `apps/customer-mobile/src/screens/Account.tsx` | تسجيل دخول/خروج، تبديل الحساب، روابط الإشعارات والدعم، اللغة، كارت إذن تنبيهات التليفون |
| Stack | Meal | `screens/Meal.tsx` | الـ Slot، الكمية، الإضافة للسلة |
| Stack | Cart | `screens/Cart.tsx` | السلة، التحقق من التليفون، عرض السعر، التسجيل، الاسترجاع |
| Stack | Order | `screens/Orders.tsx` (`OrderScreen`) | التتبّع، الإلغاء، التقييم، حالة الدفع |
| Stack | Notifications | `screens/Notifications.tsx` | صندوق الوارد داخل التطبيق |
| Stack | Support | `screens/Support.tsx` | حالات عن أوردر |
| (عناصر الدفع) | `screens/Payment.tsx` | شروط/حالة العربون جوه Cart وOrder |

العربي هو الافتراضي إلا لو لغة الجهاز إنجليزي (`initialLocale` في `App.tsx`). الـ `StepUpBanner` بيتعرض فوق الـ Navigator كل ما أي رد من الـ API يبقى `403 STEP_UP_REQUIRED`.

## سلوك الموبايل الشامل

### الـ Authentication والـ Redirect والـ Deep Links

* **الـ Flow.** `signIn` في `apps/customer-mobile/src/auth.ts`: OIDC Authorization Code + PKCE (S256) في **متصفح النظام** (عمره ما بيبقى Web View مضمّن)، و`prompt=login` دايمًا، و`preferEphemeralSession: true`، والـ Scopes هي `openid profile email`، والعميل العام `matbakhy-mobile` من غير سرّ (`apps/customer-mobile/src/config.ts`). لازم الـ URL الراجع يساوي الـ Redirect URI بالظبط، ويشيل الـ `state` بتاعنا وكود `code` ومن غير `error` (`apps/customer-mobile/src/redirect.ts`، `checkRedirect`) قبل تبادل الـ Token؛ وإلا `INVALID_REDIRECT` أو `STATE_MISMATCH` أو `PROVIDER_ERROR` أو `MISSING_CODE`.
* **الـ Redirect URI.** `<scheme>://auth/callback`؛ والـ Scheme في التطوير هو `matbakhy-dev` (`apps/customer-mobile/app.json`؛ وعميل Keycloak في `infra/identity/matbakhy-realm.json` بيسجّل `matbakhy-dev://auth/callback`). معرّفات وتوقيع Staging/production قرار المالك O08 (`app.json` `extra.note`): PLANNED. خارج التطوير لازم رابط الـ API والـ Issuer يبقوا HTTPS (وإلا `ConfigError`).
* **الـ Deep Links.** `apps/customer-mobile/src/linking.ts`: البادئة `<scheme>://`؛ المسارات `orders` (تبويب)، `orders/:orderId`، `meals/:mealId`، `cart`؛ وأي URL بيبدأ بـ `<scheme>://auth/` بيتفلتر عشان الـ Callback بتاع تسجيل الدخول مايتعاملش كأنه تنقل. الرابط بيسمّي شاشة بس: والسيرفر هو اللي بيقرر الوصول (أوردر حساب تاني بيظهر "not found"). الـ Scheme المخصص بس هو المتنفّذ؛ وUniversal Links / App Links (https) PLANNED / مش موجودة.
* **استمرار الـ Session.** `apps/customer-mobile/src/tokenStore.ts` بيحتفظ بسجل JSON واحد (Access Token، Refresh Token، ID Token، الانتهاء، الـ Subject) في `expo-secure-store` بـ `WHEN_UNLOCKED_THIS_DEVICE_ONLY` (تخزين مدعوم بـ iOS Keychain / Android Keystore)؛ السجلات التالفة أو المنتهية من غير Refresh بتتحذف وبتتعامل كأنه مسجّل خروج. القراءة الأولى الفاشلة بتتعاد مرة واحدة بعد 300 ms عشان الـ Keystore اللي مش جاهز (اتشاف في تشغيل أندرويد البارد) مايبانش كأنه تسجيل خروج. `apps/customer-mobile/src/session.ts` بيجدد الـ Access Token قبل الانتهاء بـ 30 s، وتجديد واحد مشترك للمتصلين المتزامنين؛ ورفض المزوّد (`invalid_grant`) أو Token لـ Subject تاني بينهي الـ Session؛ ولو المزوّد مش متاح، التطبيق بيحتفظ بالـ Session.
* **طبقة الطلبات.** `apps/customer-mobile/src/mobileApi.ts`: Bearer Token من الـ Session، و`Accept-Language`؛ `401` مع Token → تسجيل خروج محلي؛ `403` والجسم `code = STEP_UP_REQUIRED` → `onStepUpRequired`. الـ Backend بيخدم الـ Bearer Chain (`bearerChain` في `backend/app/src/main/java/com/matbakhy/app/security/SecurityConfiguration.java`): تحقق JWT بدون حالة (الـ Issuer، الـ Audience `apiAudience`، التوقيع، الانتهاء)، من غير Cookies ولا CSRF Token.

### السلة وإعادة محاولات الأوردر الآمنة (`basket.ts`، `placement.ts`)

* السلة والتسجيل المعلّق بيتخزنوا لكل شخص في التخزين الآمن تحت المفاتيح `matbakhy.basket.v1.<subject>` / `matbakhy.pendingPlacement.v1.<subject>`؛ وهو مسجّل خروج المفاتيح من غير لاحقة (سلة الزائر). عند تسجيل الدخول سلة الزائر بتنتقل للشخص بس لو ماعندوش سلة خاصة بيه (`bindShopping`).
* مقبض `Shopping` مربوط بصاحبه بيتاخد لما الطلب يبدأ، فالرد المتأخر بعد تبديل الحساب مايقدرش يلمس غير مفاتيح الشخص السابق.
* الدالة `place()` بتبعت `POST /api/v1/orders` بـ `Idempotency-Key` و`clientRequestId` المخزنين. النتايج: `placed` (السلة والمفاتيح اتمسحوا)، `uncertain` (5xx أو 401 أو شبكة: المفاتيح محفوظة؛ الرسالة بتختلف في حالة عدم الاتصال)، `rejected` (4xx نهائي: المفاتيح اتشالت والسلة اتحفظت)، `stepUp` (403 `STEP_UP_REQUIRED`: المفاتيح محفوظة مع `awaitingExplicitRetry`؛ بتتبعت تاني بإعادة المحاولة الصريحة من العميل بس، مش عند التركيز ولا إعادة التشغيل).
* الدالة `recover()` بعد إعادة التشغيل بتسأل `GET /api/v1/orders/by-request/{clientRequestId}`؛ لقاه → placed؛ 404 → إعادة إرسال بنفس المفاتيح؛ أي حاجة تانية → uncertain، من غير أي ادّعاء عن الأوردر أو المخزون.

### تبديل الحساب والـ Step-up

* تبديل الحساب (`switchAccount` في `apps/customer-mobile/src/AppContext.tsx`) = تسجيل خروج (إلغاء Refresh Token بأفضل جهد، ومسح التخزين الخاص) وبعدين تسجيل دخول تفاعلي جديد. تغيير الـ Subject بيمسح كل الـ Caches الخاصة وبيزوّد `accountEpoch`، واللي بيعيد تركيب شجرة التنقل كلها فمفيش حالة شاشة للشخص السابق بتفضل.
* الـ Step-up (`startStepUp`) بيكرر تسجيل الدخول في متصفح النظام بـ `acr_values=mfa 2`. نفس الـ Subject: الـ Tokens بتتبدل، والسلة والـ Caches بتتحفظ، والبيانات بتتحمل تاني. Subject مختلف: بيتعامل كتبديل حساب.
* **مفيش شاشة إعدادات MFA في الموبايل** (`docs/security/mfa-policy.md` بيقول نفس الكلام): العميل بيفعّل الـ MFA على الويب أو في لوحة حساب Keycloak، وبعدين التطبيق بيتفاعل مع الـ 403 بتاع السيرفر.

### الأخطاء في عدم الاتصال والشبكة

* التطبيق مفيهوش وضع Offline؛ بيقرا من الـ API. نص عدم الاتصال بيقول إن اللي معروض مش حالة الطبخ ولا المخزون الحاليين (`offline` في `apps/customer-mobile/src/i18n.ts`). شاشة الأوردر بتستعلم كل 10 s (`setInterval(reload, 10_000)` في `screens/Orders.tsx`).
* الأوردر غير المؤكد بيعرض إشعار فيه "Check and retry"؛ والصياغة بتختلف بين عدم الاتصال وعدم يقين السيرفر (`pendingOffline`، `pendingUncertain`).
* إذن الإشعارات: `notificationPrompt` بتبقى true في بناءات التطوير بس؛ والبناءات التانية بتقول إن التحديثات بتظهر في شاشة Notifications داخل التطبيق. تسليم الـ Push لسه مش متقرر (قرار المالك O07): PLANNED.

## أندرويد مقابل iOS

| الجانب | أندرويد | iOS | الدليل |
|---|---|---|---|
| متصفح النظام المستخدم لتسجيل الدخول | Chrome Custom Tabs | `ASWebAuthenticationSession` | تعليق في `apps/customer-mobile/src/auth.ts` |
| خيار الـ Session المؤقتة | `preferEphemeralSession: true` بيتبعت على الاتنين؛ الأثر على أندرويد: NOT VERIFIED | بيتطبّق؛ وبيمنع إعادة استخدام Cookie Keycloak السابقة بصمت | `auth.ts`، `signIn.test.ts` |
| HTTP العادي على localhost في التطوير | مسموح من خلال Config Plugin لمعرّفات التطوير بس | مش محتاج | `apps/customer-mobile/plugins/withLocalhostCleartext.cjs` |
| التخزين الآمن | `expo-secure-store` مدعوم بـ Keystore (اتلاحظ فشل قراءة في التشغيل البارد، ومن هنا إعادة المحاولة) | Keychain | `basket.ts`، `tokenStore.ts` |
| معرّفات الـ Bundle | `com.matbakhy.customer.dev` | `com.matbakhy.customer.dev` | `app.json` |
| التشغيل الآلي Native | محاكي، Shell + uiautomator: `apps/customer-mobile/scripts/auth-roundtrip-android.sh`، `order-roundtrip-android.sh` | Simulator، XCUITest: `apps/customer-mobile/e2e-ios/UITests/SignInRoundTripTests.swift`، `OrderingTests.swift` بتتحرك بـ `apps/customer-mobile/scripts/auth-roundtrip-ios.sh`، `apps/customer-mobile/scripts/order-roundtrip-ios.sh` | الـ Workflow بتاع الـ CI هو `.github/workflows/native-build.yml` |
| جهاز فعلي | مفيش متسجّل (CFG-112 بتقول إن مفيش خطة لأندرويد) | خطة المالك عن طريق TestFlight (CFG-046)؛ مش متسجّل إنها اتشغّلت | `docs/quality/owner-acceptance/cases/config-security.json` |

## الرحلات

عناصر مشتركة لكل الرحلات اللي تحت. **الطرف:** زائر أو عميل (Bearer Token؛ سلطة `ACCOUNT_ACTIVE`؛ ملكية على مستوى الكائن). **الـ Backend والجداول:** مطابقة لرحلات الويب؛ شوف الرحلات المرقمة في [ويب العميل](customer-web-ar.md) (من CW-1 لـ CW-7) بدل تكرارها. **الاختبارات (كلها):** jest `apps/customer-mobile/src/App.test.tsx`، `apps/customer-mobile/src/auth.test.ts`، `apps/customer-mobile/src/signIn.test.ts`، `apps/customer-mobile/src/ordering.test.ts`؛ وأداة الـ CI الـ Native فوق؛ وبيانات البذر `apps/customer-mobile/e2e-native/seed.sql` (بيانات تجريبية غير حقيقية (Synthetic Data)، للـ CI بس). نتايج المجموعات دي للـ Commit الحالي: NOT VERIFIED هنا.

### CM-1 تسجيل الدخول، إعادة التشغيل، تسجيل الخروج، تبديل الحساب
1. الهدف: Session موبايل مصادَق عليها مابتتسربش بين الناس (BR-048، TC-048).
2. الطرف: العميل. 3. المتطلب المسبق: حساب عميل في Keycloak؛ والـ Issuer متاح.
4. البداية: تبويب Account → "Sign in". 5. الإجراءات: تسجيل الدخول في متصفح النظام؛ إيقاف التطبيق وإعادة فتحه؛ "Switch account"؛ "Sign out".
6. الاستجابات: اسم الملف الشخصي بيظهر (`signedInAs`)؛ بعد إعادة الفتح الـ Session بتتسترجع من التخزين الآمن؛ بعد تسجيل الخروج إعادة التشغيل بتظهر مسجّل خروج؛ تسجيل الدخول الجاي بيعرض نموذج الدخول تاني.
7. الـ API: `GET /api/v1/me` (API-009)؛ نقاط المزوّد: discovery، authorize، token، refresh، revoke (Keycloak).
8. الـ Backend: الـ Bearer Chain، `AccountMapper`، `ProfileController`. 9. قاعدة البيانات: `app_user`.
10. الانتقالات: loading → signedIn / signedOut (حالة `auth` في `AppContext.tsx`).
11. الأخطاء: المتصفح اتقفل → `SignInCancelled`، ومفيش حاجة اتخزنت؛ redirect/state غير صالح → بيترفض؛ Token من غير `sub` بيترفض؛ 401 → تسجيل خروج محلي.
12. الاختبارات: `auth.test.ts`، `signIn.test.ts`، `App.test.tsx` ("customer app shell"، "an expired access token is refreshed…")، `e2e-ios/UITests/SignInRoundTripTests.swift`، `apps/customer-mobile/scripts/auth-roundtrip-android.sh`.
13. اختبارات المالك E2E: CFG-046، E2E-BASKET-027، E2E-DISC-004؛ J8 (الأجهزة الفعلية).
14. الحالة: IMPLEMENTED في الكود وأداة الـ CI على المحاكي/الـ Simulator؛ الجهاز الفعلي: NOT VERIFIED. 15. القيود: شوف الجدول فوق.

### CM-2 الاكتشاف، الإضافة للسلة، استبدال السلة
1. الهدف: التصفح وبناء سلة لمطبخ واحد (BR-018، TC-148). 2. الطرف: زائر/عميل (Guest/Customer). 3. المتطلب المسبق: وجبات منشورة.
4. البداية: تبويب Discover. 5. الإجراءات: بحث، فتح وجبة، اختيار Slot وكمية، إضافة؛ إضافة وجبة مطبخ تاني بتسأل احتفظ ولا استبدل؛ تعديل الكميات في Cart.
6. الاستجابات: الكمية محدودة بـ `min(server limit, slot portions)`؛ الـ Slot المستنفد مايتضافش؛ التقدير بالقروش (piastres) الصحيحة.
7. الـ API: `GET /api/v1/meals`، `GET /api/v1/meals/{mealId}`، `GET /api/v1/zones`، `GET /api/v1/configuration/public`، `PUT/DELETE /api/v1/me/favorites/{mealId}`، `GET /api/v1/me/favorites`، `POST /api/v1/activity`.
8–9. زي CW-1/CW-3. 10. مفيش على السيرفر.
11. الأخطاء: رسالة عدم الاتصال (E2E-DISC-115)؛ فشل قراءة التخزين بيتعاد مرة واحدة.
12. الاختبارات: `App.test.tsx` ("native discovery and ordering"، "native basket editing"، "Q1 on native")، `ordering.test.ts`، `apps/customer-mobile/scripts/order-roundtrip-android.sh`، `OrderingTests.swift` (testD، testE، testG).
13. اختبارات المالك E2E: E2E-BASKET-026، E2E-BASKET-009، E2E-BASKET-010، E2E-DISC-115. 14. IMPLEMENTED. 15. مفيش مزامنة سلة الزائر مع الجهاز.

### CM-3 عرض السعر، التسجيل، واسترجاع الأوردر غير المؤكد
1. الهدف: تسجيل أوردر واحد بالظبط حتى لو التطبيق اتقفل فجأة (BR-023، TC-049، TC-132). 2. الطرف: العميل. 3. المتطلب المسبق: تليفون متحقق منه، عنوان للتوصيل.
4. البداية: Cart. 5. الإجراءات: التحقق من التليفون، اختيار الوضع والدفع (COD أو DEPOSIT)، "Calculate total"، تأكيد، تسجيل؛ إيقاف إجباري أثناء الإرسال؛ إعادة الفتح وهو Offline؛ إعادة الفتح Online ودوس "Check and retry".
6. الاستجابات: شاشة تغيّر السعر بالإجماليات القديمة والجديدة وقبول صريح (D13)؛ إشعار غير مؤكد؛ بعد الاسترجاع بيتفتح الأوردر الوحيد.
7. الـ API: `POST /api/v1/me/phone/challenges[/…/verify]`، `GET/POST /api/v1/me/addresses`، `POST /api/v1/quotes`، `POST /api/v1/orders`، `GET /api/v1/orders/by-request/{clientRequestId}`.
8–9. زي CW-4. 10. الأوردر `PENDING_ACCEPTANCE`. 11. `PRICE_CHANGED`، `QUOTE_EXPIRED`، `STOCK_CHANGED`، `SLOT_CLOSED`، `STEP_UP_REQUIRED`، `NETWORK`.
12. الاختبارات: `ordering.test.ts` (بلوك placement)، `App.test.tsx` ("the unconfirmed order stays reachable…"، "repeated Check and retry"، "D13 on native")، الـ CI: `OrderingTests.swift` testA/B/C/H، `order-roundtrip-android.sh` (إيقاف أثناء استجابة بطيئة باستخدام `e2e-native/slow_proxy.py`، وعدّ في قاعدة البيانات لأوردر واحد بالظبط).
13. اختبارات المالك E2E: E2E-ORDER-107، E2E-ORDER-122، E2E-ORDER-024، E2E-BASKET-026؛ J8. 14. IMPLEMENTED (محاكي/Simulator)؛ الجهاز NOT VERIFIED. 15. مفيش إشعار Push عند تغيّر الحالة.

### CM-4 تتبّع الأوردر، تقرير العربون، الإلغاء، التقييم
1. الهدف: متابعة الأوردر والإبلاغ عن العربون بأمانة. 2. الطرف: صاحب الأوردر. 3. المتطلب المسبق: أوردر.
4. البداية: تبويب Orders أو Deep Link `<scheme>://orders/<id>`. 5. الإجراءات: فتح الأوردر؛ "I sent the deposit"؛ إلغاء / طلب إلغاء؛ تقييم أوردر مكتمل.
6. الاستجابات: التحديث كل 10 ثواني؛ كود التسليم بيظهر لما السيرفر يرجّعه؛ نص العربون بيذكر إن المطبخ لازم يؤكد الاستلام.
7. الـ API: `GET /api/v1/orders`، `GET /api/v1/orders/{orderId}`، `POST …/cancel`، `POST …/cancellation-requests`، `POST …/payment/transfer-report`، `POST …/reviews`.
8–9. زي CW-5. 10. زي CW-5. 11. معرّف أوردر حساب تاني → شاشة "not found".
12. الاختبارات: `App.test.tsx` ("deep links (MB-033)"، "MB-053 on native")، `OrderingTests.swift` testF (Deep Link والتطبيق شغّال أو مقفول) و testK (العربون)، `order-roundtrip-android.sh` (Deep Link والتطبيق شغّال أو مقفول؛ تقرير تحويل العربون).
13. اختبارات المالك E2E: E2E-ORDER-122، E2E-FIN-113، E2E-ORDER-120. 14. IMPLEMENTED. 15. تغطية الـ Deep Link على أندرويد بـ `adb am start` بس.

### CM-5 الـ Step-up للـ MFA (D18)
1. الهدف: إكمال العامل التاني لما السيرفر يطلبه. 2. الطرف: عميل سياسة فئته REQUIRED أو فعّل الـ MFA على الويب. 3. المتطلب المسبق: تفضيل من جهة الويب أو سياسة REQUIRED؛ والـ Migration بتاع الـ Realm الحي متطبّق (CFG-028).
4. البداية: أي شاشة بعد 403 `STEP_UP_REQUIRED`. 5. الإجراءات: دوس Verify في البانر → متصفح النظام بـ `acr_values=mfa 2` → أدخل OTP أو سجّل TOTP → ارجع.
6. الاستجابات: البانر بيختفي؛ الشاشات المرفوضة بتتحمل تاني على `dataEpoch`؛ تسجيل الأوردر المرفوض بيتبعت تاني بإعادة محاولة صريحة بنفس المفاتيح بس. الإلغاء بيسيب البانر والحساب.
7. الـ API: أي Endpoint محمي بيرجّع 403 `STEP_UP_REQUIRED` (`backend/app/src/main/java/com/matbakhy/app/security/MfaEnforcementFilter.java`)؛ والتطبيق مفيهوش نداء لـ `/api/v1/me/security`.
8. الـ Backend: `MfaEnforcementFilter`، `MfaPolicyService`. 9. قاعدة البيانات: `mfa_policy`، `user_mfa_preference`، `user_mfa_reset`.
10. الانتقالات: البانر شغّال/مقفول. 11. الأخطاء: المتصفح اتقفل؛ شخص غلط سجّل دخول → تبديل حساب.
12. الاختبارات: `App.test.tsx` ("two-step verification step-up (D18)")، `ordering.test.ts` (إعادة محاولة الـ Step-up)، `signIn.test.ts`؛ مفيش تشغيل Native بـ MFA مفعّل (`docs/security/mfa-policy.md`).
13. اختبارات المالك E2E: CFG-024، CFG-025، CFG-026، CFG-108، CFG-109 (iPhone)، CFG-112 (أندرويد)؛ J8. 14. PARTIALLY IMPLEMENTED: كود وjest بس؛ مفيش دليل من جهاز. 15. مفيش شاشة Security؛ سيب سياسة CUSTOMER وKITCHEN على OPTIONAL لحد ما تتجرّب على أجهزة.

### CM-6 الإشعارات والدعم
1. الهدف: قراءة تحديثات الأوردر والتواصل مع الدعم. 2. الطرف: العميل. 3. المتطلب المسبق: Session.
4. البداية: Account → Notifications / Support. 5. الإجراءات: فتح الصندوق، تعليم كمقروء؛ إنشاء حالة دعم عن أوردر، إضافة رسائل؛ السماح اختياريًا بتنبيهات التليفون (بناءات التطوير بس).
6. الاستجابات: قايمة داخل التطبيق؛ صياغة إذن صادقة؛ الرفض آمن.
7. الـ API: `GET /api/v1/notifications`، `PUT /api/v1/notifications/{id}/read`، `GET/POST /api/v1/support/cases`، `GET/POST /api/v1/support/cases/{caseId}[/messages]`. التطبيق **مابيناديش** `PUT /api/v1/me/devices/{installationId}` (API-033)؛ تسجيل الجهاز مش موصّل: PLANNED مع O07.
8–9. زي CW-6. 12. الاختبارات: `App.test.tsx` ("MB-033 notification permission")، `OrderingTests.swift` testJ. 13. E2E-DISC-108، E2E-ORDER-117، E2E-ORDER-118. 14. IMPLEMENTED (داخل التطبيق بس). 15. الـ Push PLANNED.

## القيود المعروفة وفجوات التوثيق

* `docs/security/mfa-policy.md` الخطوة 5 من خطة الإطلاق فيه بتقول إن تطبيق الموبايل مابيتعاملش مع `403 STEP_UP_REQUIRED` وهيعرض خطأ عام، بينما قسم المخاطر المتبقية فيه (والكود في `AppContext.tsx` / `mobileApi.ts`) بيقول إن البانر موجود. اعتمد على الكود: البانر موجود؛ وسلوك الجهاز NOT VERIFIED.
* `docs/security/mfa-policy.md` بيشير لخطة اختبار أجهزة iOS Beta تحت مجلد مستندات موبايل؛ والمجلد ده مش موجود في الـ Repository ده (اتفحص). يبقى خطة الأجهزة ناقصة من الـ Repository.
* تعليقات `apps/customer-mobile/src/auth.ts` بتقول إن الـ Session المؤقتة بتتستخدم "on iOS"؛ لكن الكود بيمرر `preferEphemeralSession: true` من غير شرط.
* ملف الـ Realm بتاع المعاينة `deploy/web-preview/identity/matbakhy-realm.preview.json` مفيهوش عميل `matbakhy-mobile` (الـ Realm بتاع التطوير فيه)؛ و`deploy/web-preview/identity/mfa-migration.kcadm.sh` بيشير للعميل. هل الـ Realm المنشور للمعاينة فيه: NOT VERIFIED.
