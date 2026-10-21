# اليوم 2 - التطبيقات ورحلات المستخدم

> **Owner role:** Frontend and mobile
> **Status:** REFERENCE — Lab لتشغيل التطبيقات والمشي في كل رحلة مستخدم.
> **Language / اللغة:** [English](day-2-applications.md) · العربية

## الأهداف

- شغّل الـ Stack المحلي كامل وتطبيقي الويب.
- امشي في رحلة العميل ورحلات الطبّاخ والـ Back Office بمستخدمين تجريبيين.
- اعرف تطبيق الموبايل ده إيه، وكوده فين، وإزاي بيتم التحقق منه بشكل Native.

## قايمة القراءة

1. [ويب العميل](../02-user-journeys/customer-web-ar.md)، [موبايل العميل](../02-user-journeys/customer-mobile-ar.md)، [عمليات الطبّاخ](../02-user-journeys/cook-operations-ar.md)، [أدمن الـ Back Office](../02-user-journeys/backoffice-admin-ar.md)، [المالية والدعم](../02-user-journeys/finance-and-support-ar.md)، [الـ Authentication والـ MFA](../02-user-journeys/authentication-and-mfa-ar.md).
2. [معمارية الـ Frontend](../03-architecture/frontend-architecture-ar.md)، [معمارية الموبايل](../03-architecture/mobile-architecture-ar.md)، [دليل كود الـ Frontend](../04-codebase/frontend-code-guide-ar.md)، [دليل كود الموبايل](../04-codebase/mobile-code-guide-ar.md).
3. [المتطلبات المسبقة](../05-developer-workflow/prerequisites-ar.md) و[البيئة المحلية](../05-developer-workflow/local-environment-ar.md).

## مهام عملية

1. تأكد من الأدوات: `scripts/dev.sh doctor`. صلّح أي حاجة MISSING قبل ما تكمّل.
2. شغّل الـ Stack وأثبت إنه شغّال:
   ```bash
   scripts/dev.sh up
   scripts/dev.sh smoke      # must end with: PASS: local stack smoke checks
   ```
   لو فشل، استخدم [حل المشاكل](../05-developer-workflow/troubleshooting-ar.md) وسجّل ملاحظات باللي قابلك.
3. شغّل تطبيقي الويب (في Terminalين):
   ```bash
   pnpm install --frozen-lockfile
   VITE_API_BASE_URL=http://localhost:8080 pnpm --filter @matbakhy/customer-web dev
   VITE_API_BASE_URL=http://localhost:8080 pnpm --filter @matbakhy/operations-web dev
   ```
4. اعمل أول تسجيلات دخول (ده اللي بيخلّي المستخدمين التجريبيين يتوجدوا في الـ Database). اقرا كلمة السر المحلية من ملف env المتولّد عندك والمتجاهَل من git في مجلد infra (المتغير `DEV_USER_PASSWORD`؛ وماتنسخهاش في أي مكان تاني أبدًا). سجّل دخول مرة على `http://localhost:5173` بـ `customer-a` ومرة على `http://localhost:5174` بـ `cook-a` وبـ `ops-a` (استخدم Profiles متصفح منفصلة أو نوافذ خاصة). المتوقع: كل تسجيل دخول بيوصّل للتطبيق؛ و`cook-a` بيشوف "no kitchen" و"no application". صفوف `app_user` بتتعمل عند أول تسجيل دخول، والخطوات الخاصة بالـ Fixtures اللي تحت محتاجاها.
5. حالة الـ Fixtures المحلية. أي Stack اشتغل لتوّه فيه مناطق وسياسة تجارية (`backend/app/src/main/resources/db/devdata/R__dev_service_zones.sql`، `R__dev_commerce_policy.sql`) لكن مفيش مطبخ موافق عليه، ولا وجبة، ولا Slot، ولا تليفون متحقق منه، ولا صلاحيات موظفين. مجموعة اختبارات الـ E2E في المتصفح بتبني الحالة دي عن طريق Fixtures محمية ومعمولها Commit (`deploy/web-preview/e2e-fixture.sh` بيشغّل `deploy/web-preview/e2e-fixtures/<name>.sql`؛ وبيرفض يشتغل إلا لو ملف Marker موجود). تقدر تشغّل نفس الـ Fixtures بإيدك على الـ Stack المحلي بتاعك انت بس (مش مشترك أبدًا). من جذر الـ Repository:
   ```bash
   set -a; . infra/.env; set +a
   export E2E_ENABLED_MARKER="${TMPDIR:-/tmp}/matbakhy-local-e2e-enabled"   # the guard marker outside the repository; create it deliberately, delete it afterwards
   export E2E_PSQL_PREFIX="docker compose --env-file infra/.env -f infra/compose.yaml exec -T postgres psql -U $POSTGRES_USER -d $POSTGRES_DB"
   touch "$E2E_ENABLED_MARKER"
   fx() { printf '%s\n' "$@" | sh deploy/web-preview/e2e-fixture.sh; }
   fx cook_approved_with_kitchen cook_subject=00000000-0000-4000-8000-0000000000c1
   fx customer_phone_verified subject=00000000-0000-4000-8000-0000000000a1 phone=+201001112223
   fx staff_review_grants subject=00000000-0000-4000-8000-0000000000d2
   ```
   الـ Subjects هي التجريبية الثابتة اللي في `apps/operations-web/e2e/fixtures.ts` (`LOCAL_ACCOUNTS`) والـ realm في `infra/identity/matbakhy-realm.json`. المتوقع: مفيش خطأ؛ وpsql مابيطبعش حاجة لعمليات الـ Insert دي. الـ Fixture اسمه `cook_approved_with_kitchen` بيستخدم IDs ثابتة ومقصود بيه Database فاضية، فشغّله مرة واحدة (التشغيل التاني بيفشل بسبب Duplicate keys؛ و`scripts/dev.sh reset` بيديك بداية نضيفة). أعد تحميل مساحة عمل الطبّاخ: المفروض دلوقتي تشوف المطبخ "مطبخ تجريبي". امسح الـ Marker لما تخلص: `rm "$E2E_ENABLED_MARKER"`. NOT VERIFIED: الأوامر دي بالظبط اتكتبت من السكريبتات وماتنفّذتش وقت كتابة الصفحة دي؛ لو واحد فشل، سجّل الخطأ والملف اللي بيشاور عليه، وصلّح الصفحة دي (السياسة في [صيانة التوثيق](../05-developer-workflow/documentation-maintenance-ar.md)).
6. اعمل الكتالوج من خلال الواجهة، زي ما بتعمل مجموعة E2E (`apps/operations-web/e2e/cook-workspace.e2e.ts`، الخطوتين "meal with an uploaded photo" و"slot tomorrow"): كـ `cook-a` افتح تبويب Meals، واملا حقول العنوان العربي والسعر والحصة، وارفق صورة مش أقل من 640 x 480 بكسل (أي صورة تجريبية)، وأنشئ، وبعدين انشر؛ وافتح تبويب Slots، وأنشئ Slot لبكرة بمهلة تحضير تسمح بكده، وادّي الوجبة سعة، وافتح الـ Slot. المتوقع: كارت الوجبة بيعرض PUBLISHED وكارت الـ Slot بيعرض OPEN. NOT VERIFIED يدويًا؛ وتسميات الحقول موجودة في ملفات i18n بتاعت التطبيق.
7. رحلة العميل على `http://localhost:5173` بـ `customer-a`: تصفح الوجبة المنشورة، وضيفها للسلة، واطلب عرض سعر وراقب الإجماليات (عرض السعر محتاج وسيلة تواصل متحقق منها، وده اللي وفّرته الخطوة 5؛ وعرض سعر التوصيل محتاج كمان عنوان محفوظ في منطقة مزروعة). بدّل بين العربي والإنجليزي وتأكد إن الاتجاه بيتقلب (RTL/LTR).
8. رحلات الطبّاخ والـ Back Office على `http://localhost:5174`: كـ `cook-a` دوّر على لوحة الطبّاخ وقايمة الأوردرات؛ وكـ `ops-a` (اللي دلوقتي معاه `content.moderate` و`cook.review` و`support.case` و`order.ops` و`order.exception_cancel`) افتح شاشات الموظفين. سجّل أي عناصر قايمة بتظهر لكل دور وإيه اللي بيحصل لما `cook-a` يفتح URL للموظفين بس. إجراءات الموظفين اللي محتاجة MFA حديث (مثلًا الموافقة على طبّاخ) بتطلب خطوة العامل التاني الخاصة بالـ realm (`infra/identity/matbakhy-realm.json` بيعرّف الـ browser flow المخصص بنموذج OTP، و`SecurityConfiguration` بيطلبه بـ `max_age=0` لرفع التحقق على الويب؛ شوف [الـ Authentication والـ MFA](../02-user-journeys/authentication-and-mfa-ar.md)). التسجيل ورفع التحقق بشكل تفاعلي في الـ realm المحلي: NOT VERIFIED هنا؛ والخطوة 5 بتوافق على المطبخ عن طريق الـ Fixture بس عشان الإعداد يتكرر من غير تسجيل تفاعلي.
9. قارن اللي عملته بالنسخة الآلية: اقرا `apps/operations-web/e2e/customer-journey.e2e.ts` و`apps/operations-web/e2e/cook-workspace.e2e.ts`. اعمل قايمة بالخطوات اللي بيعملوها آليًا.
10. Mailpit على `http://localhost:8025`: فعّل أي حاجة بتبعت إيميل، لو في، ودوّر عليه. لاحظ إن مفيش حاجة بتخرج من جهازك.
11. الموبايل: افتح `apps/customer-mobile/src/App.tsx` و`apps/customer-mobile/src/config.ts`؛ واعمل قايمة بالشاشات تحت `apps/customer-mobile/src/screens`؛ واقرا `apps/customer-mobile/scripts/order-roundtrip-android.sh` عشان تشوف إيه السلوكيات الـ Native اللي الـ CI بيثبتها. شغّل `pnpm --filter @matbakhy/customer-mobile test`. لو معندكش Emulator، قول إن التحقق الـ Native NOT RUN من ناحيتك.
12. الإيقاف: `scripts/dev.sh down` (بيحتفظ بالبيانات) أو `scripts/dev.sh reset` (بيمسح الـ Volume المحلي).

## المُخرَج

شرح مكتوب مختصر للمشي في الرحلات (لقطات شاشة لبيانات تجريبية بس، من غير كلمات سر) بيعدّد، لكل سطح، أهم تلات شاشات، ونداءات الـ API اللي بتعملها كل شاشة (من تبويب Network في المتصفح) والدور اللي يقدر يشوفها.

## فحص ذاتي

- [ ] `scripts/dev.sh smoke` طبع PASS على جهازي، أو أقدر أقول بالظبط أنهي خطوة فشلت وليه.
- [ ] أقدر أشرح ليه تطبيقي الويب لازم يشتغلوا على البورتين 5173 و 5174.
- [ ] عارف الفرق بين ويب العميل وويب العمليات (مساحة عمل الطبّاخ + الـ Back Office) وتطبيق الموبايل، وإن مفيش تطبيقات Native منفصلة للطبّاخ أو السواق في الـ MVP.
- [ ] أقدر أقول ليه حزمة JS أو Expo Go مش بتتحسب تحقق Native.
- [ ] أقدر أقول أنهي أسعار وإجماليات بيحسبها السيرفر.

الخطوة الجاية: [اليوم 3](day-3-backend-security-ar.md).
