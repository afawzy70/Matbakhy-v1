# الدخول على الـ Live Preview ورحلات الاستخدام التفاعلية

> **Owner role:** Infrastructure and deployment
> **Status:** PARTIALLY IMPLEMENTED — الستة عناوين بتاعة الـ Preview بترد زي ما هو موضّح تحت (اتلاحظت من الـ VPS نفسه)؛ أما تجهيز الحسابات وقوائم السماح للشبكات والرحلات من شبكات تانية فهي NOT VERIFIED LIVE.
> **Language / اللغة:** [English](live-preview-access.md) · العربية

**دليل الوسوم (Evidence legend).** VERIFIED LIVE: اتلاحظ يوم 2026-10-21 بواسطة مستخدم معندوش صلاحيات إدارية على الـ Contabo VPS نفسه، للقراءة فقط (سجل الحقائق الحيّة بتاع الدليل ده). VERIFIED IN CODE: اتقرأ في الـ Repository ده. DOCUMENTED ONLY: مستند في الـ Repository بيقوله من غير ما يتأكد. OUTDATED / INCORRECT: بيتعارض مع دليل أحدث. NOT VERIFIED: محدش أكّده؛ والنص بيوضّح إزاي شخص مصرّح له يقدر يأكّده.

الصفحة دي للمطوّر أو المختبِر أو المالك اللي محتاج يفتح الـ Preview الشغّال ويمشي رحلة بيزنس عليه. هي مش بتكرر تفاصيل الشاشات اللي في صفحات الرحلات؛ هي بتضيف العناوين الحيّة، وردود الـ HTTP المتوقعة، ولكل رحلة الحساب ونقطة الدخول والضغطات بالظبط. الـ Preview بيئة ببيانات تجريبية غير حقيقية (Synthetic Data) وعليها علامة Demo، مش Staging ومش Production (شوف [بيئة المعاينة](preview-environment-ar.md)). تخطيط السيرفر موجود في [جرد سيرفر Contabo](../08-infrastructure/contabo-server-inventory-ar.md) و[معمارية التشغيل](../08-infrastructure/runtime-architecture-ar.md).

## الستة عناوين الحيّة

الدومين هو اسم sslip.io الموثّق `167-86-90-174.sslip.io`، وبيتحل (Resolve) لعنوان الـ VPS (VERIFIED LIVE). الـ DNS والشهادات والـ Routing متشرحين في [DNS وHTTPS وsslip.io](../08-infrastructure/dns-https-sslip-io-ar.md).

| الاسم | الـ URL | الغرض | الرد الملاحَظ (من الـ VPS نفسه) | الحالة | الـ Authentication والشبكة | مين المفروض يستخدمه |
|---|---|---|---|---|---|---|
| Customer Web | `https://app.167-86-90-174.sslip.io/` | تطبيق العميل (Single-Page App) للتصفّح والسلة والـ Checkout والأوردرات | 200 | VERIFIED LIVE | التصفّح من غير تسجيل دخول؛ والأوردر محتاج تسجيل دخول Keycloak. فئة IP بتاعة العملاء (`CUSTOMER_ALLOWED_IPS`) | العملاء، المختبِرين، المطوّرين |
| Operations Web | `https://ops.167-86-90-174.sslip.io/` | مساحة عمل الطبّاخ والـ Back Office (تطبيق واحد، والتابات حسب الحساب) | 200 | VERIFIED LIVE | تسجيل الدخول مطلوب للبيانات. فئة IP بتاعة الموظفين (`STAFF_ALLOWED_IPS`) وفئة العملاء كمان (تسجيل الدخول وطلبات الـ API بتعدّي على Hosts بتتبع فئة العملاء) | الطبّاخين، الموظفين، المختبِرين |
| Public API configuration | `https://api.167-86-90-174.sslip.io/api/v1/configuration/public` | إعدادات الـ Preview العامة اللي بيقراها تطبيقا الويب | 200 مع JSON فيه `orderingAvailable` و`orderLimits` و`environment` باسم `PREVIEW` و`demo` بقيمة true | VERIFIED LIVE | من غير تسجيل دخول. فئة IP بتاعة العملاء؛ والمسارات تحت `/api/v1/ops` و`/api/v1/cook` محتاجة فئة الموظفين | المطوّرين، الـ Smoke Tests |
| Keycloak | `https://id.167-86-90-174.sslip.io/` | تسجيل الدخول للـ Realm `matbakhy` | 302 مع `Location` بقيمة `https://kc-admin.167-86-90-174.sslip.io/admin/` | VERIFIED LIVE | فئة IP بتاعة العملاء؛ صفحات تسجيل الدخول بتاعة الـ Realm بتتفتح من خلال التطبيقات، مش بكتابة الـ Root ده | محدش بيكتبه؛ التطبيقات هي اللي بتعمل Redirect ليه |
| Keycloak administration | `https://kc-admin.167-86-90-174.sslip.io/` | كونسول إدارة Keycloak، للـ Preview بس | 302 مع `Location` بقيمة `/admin/master/console/` | VERIFIED LIVE | كلمة سر وكلمة سر لمرة واحدة، من غير تقييد بـ IP | الأدمنز المصرّح لهم بس |
| Media | `https://media.167-86-90-174.sslip.io/` | صور الوجبات العامة من الـ Object Store | 403، جسم XML، والـ Header `x-amz-request-id` | VERIFIED LIVE | فئة IP بتاعة العملاء؛ والـ Objects تحت `/matbakhy-public/` مقروءة من غير تسجيل دخول | المتصفّحات بتحمّل الصور؛ محدش بيفتح الـ Root |

حقائق تانية عن نفس الـ Hosts:

* `https://api.167-86-90-174.sslip.io/actuator/health` بيرد 404 (VERIFIED LIVE). ده مقصود: الـ Proxy بيمرّر بس `/api/*` و`/oauth2/*` و`/login/oauth2/*` و`/logout` (VERIFIED IN CODE: `deploy/web-preview/Caddyfile`).
* شهادة `app.*` صادرة من Let's Encrypt وصالحة من 2026-10-18 لحد 2027-01-16 (VERIFIED LIVE). التجديد التلقائي DOCUMENTED ONLY؛ أما التجديد نفسه فهو NOT VERIFIED.
* كل الطلبات اللي فوق اتعملت من عنوان السيرفر نفسه. الردود بتتغيّر حسب فئة IP بتاعة المتصل (شوف تحت)، فشبكة تانية ممكن تشوف نتايج مختلفة (VERIFIED IN CODE للقاعدة؛ أما قيم قوائم السماح فهي NOT VERIFIED وموجودة في ملف `.env` الخاص على السيرفر).

### إزاي تقرا كل رد

| الرد | معناه | هل ده عطل؟ |
|---|---|---|
| 403 مع النص العادي "Not available" | سياسة الدخول بالـ IP في Caddy: الـ IP العام بتاعك مش في الفئة اللي الـ Host ده محتاجها (`respond ... "Not available" 403` في `deploy/web-preview/Caddyfile`). اطلب من المالك يضيف الـ IP بتاعك للفئة الصح؛ وبالنسبة لـ Operations Web وشغل الطبّاخ أو الموظفين ده معناه فئة الموظفين وفئة العملاء الاتنين. | لا |
| 302 على `id.*` ناحية `kc-admin.*` | Keycloak بيبعت الـ Root بتاعه لـ URL الأدمن لأن اسم الـ Host الخاص بالأدمن متظبّط على الـ Host بتاع kc-admin. الـ Caddyfile بيرد على `/admin` و`/realms/master` في `id.*` بـ 404، فالـ Host بتاع العملاء مابيقدّمش الكونسول أبدًا. ده سلوك Keycloak المتوقع (VERIFIED LIVE للـ Redirect؛ وإعداد الـ Hostname هو VERIFIED IN CODE في `deploy/web-preview/compose.yaml`). | لا |
| 302 على `kc-admin.*` ناحية `/admin/master/console/` | الـ Caddyfile بيعمل Redirect للـ Root هناك. الـ Host بيرد 404 "Not available" طول ما ملف التفعيل (Flag) `/srv/flags/admin-console-public` مش موجود، فلو جه 302 يبقى الكونسول منشور حاليًا (VERIFIED IN CODE؛ أما مكان الـ Flag جوه الـ Container بتاع Caddy فهو NOT VERIFIED). | لا |
| 403 على Root بتاع `media.*` | الطلب وصل لـ SeaweedFS (الرد فيه `x-amz-request-id` وجسم XML، فمش هو نص "Not available" بتاع Caddy). الهوية المجهولة مسموح لها بـ `Read` بس على الـ Bucket `matbakhy-public` (VERIFIED IN CODE: `deploy/web-preview/storage/s3.json.template`)، فعرض محتويات الـ Root مرفوض بينما رابط Object زي `.../matbakhy-public/<object>` مقروء. كود الخطأ بالظبط في الجسم ماتخزّنش: ماتقولش أكتر من "XML بشكل رد رفض الوصول (Access Denied) بتاع S3". | لا |
| 404 على `api.*/actuator/health` | الـ Actuator مش بيتمرّر عن طريق الـ Proxy بالتصميم. | لا |
| 404 على `id.*` لـ `/admin` | كونسول الأدمن مابيتقدّمش أبدًا على الـ Host بتاع العملاء. | لا |
| Timeout أو خطأ في الشهادة | مشكلة حقيقية: شوف [حل مشاكل البنية التحتية](../08-infrastructure/infrastructure-troubleshooting-ar.md). | ممكن |

**تحذير بخصوص `kc-admin`.** كونسول إدارة Keycloak للأدمنز المصرّح لهم بس. تسجيل الدخول محتاج كلمة سر الـ master realm وكلمة سر لمرة واحدة، ومحاولات الدخول الفاشلة بتتحدّد سرعتها (Throttled)، والـ Host مش مقيّد بـ IP عن قصد (VERIFIED IN CODE: تعليق في الـ Caddyfile). ماتجرّبهوش عشوائيًا، وماتحاولش تسجّل دخول "عشان تشوف شغّال ولا لأ"، وماتنشرش عنوانه ومعاه بيانات دخول (Credentials). الإجراء: [كونسول أدمن Keycloak](../../operations/14-keycloak-admin-console.md).

## فئات الدخول والحسابات

| الفئة | الـ Hosts والمسارات | المصدر |
|---|---|---|
| العميل (Customer) | `app.*`، ومسارات العميل في `api.*`، و`id.*`، و`media.*` | `CUSTOMER_ALLOWED_IPS` (VERIFIED IN CODE) |
| الموظف (Staff) | `ops.*`، و`/api/v1/ops/**`، و`/api/v1/cook/**` | `STAFF_ALLOWED_IPS` (VERIFIED IN CODE) |
| كونسول الأدمن (Administrator console) | `kc-admin.*` | كلمة سر Keycloak نفسه زائد كلمة سر لمرة واحدة (VERIFIED IN CODE) |

القائمتين الاتنين بيرجعوا افتراضيًا لـ `ALLOWED_CLIENT_IPS`؛ ومفيش حاجة في فئتي العملاء والـ Staff مفتوحة للعالم لحد ما المالك يفتحها. الاستثناء هو `kc-admin.*`: مش داخل في أي من الفئتين، والـ Caddyfile مابيقيّدوش بالـ IP، والتحكم في ظهوره بملف التبديل `admin-console-public` بس (VERIFIED IN CODE). الـ 302 الحي بيوضح إن التبديل ده شغّال حاليًا، فالـ Console منشور ومحمي بكلمة السر وكود المرور لمرة واحدة.

**المختبِر من الموظفين لازم يبقى في القائمتين.** `ops.*` ومسارات `/api/v1/ops/**` و`/api/v1/cook/**` بتتحقق من `STAFF_ALLOWED_IPS`، لكن نفس طلبات الـ API بتعدّي بعد كده على `customer_access` (`CUSTOMER_ALLOWED_IPS`)، وتسجيل الدخول بيعدّي على `api.*` (`/oauth2/*`) و`id.*`، وهما Hosts بتتبع فئة العملاء (VERIFIED IN CODE: `deploy/web-preview/Caddyfile`، الـ Host `api.*` بيستورد `customer_access` بعد فحص الموظفين، والـ Host `id.*` بيستورد `customer_access`). مختبِر عنوانه في قائمة الموظفين بس ممكن يفتح Operations Web ومع كده يفشل عند تسجيل الدخول أو عند طلبات الـ API بتاعة الطبّاخ والموظفين. لو مختبِر من الطبّاخين أو الموظفين شاف تسجيل دخول فاشل أو تابات ناقصة، اتأكد إن الـ IP العام بتاعه في القائمتين قبل ما تشك في الحسابات أو الصلاحيات. العناوين الموجودة في كل قائمة NOT VERIFIED LIVE. القيم الحالية: NOT VERIFIED (ملف `.env` الخاص؛ أدمن مصرّح له يقدر يقراها باتباع [التحقق بأوامر للقراءة فقط بواسطة الأدمن](../08-infrastructure/admin-readonly-verification-ar.md)).

أسماء حسابات تجريبية غير حقيقية موثّقة للـ Realm `matbakhy` بتاع الـ Preview (DOCUMENTED ONLY: `docs/operations/06-web-preview.md` و`deploy/web-preview/seed-preview.sql` و`docs/quality/owner-acceptance/fixtures.json`). هل كل حساب موجود فعلًا على الـ Realm الحي ولا لأ فهو NOT VERIFIED LIVE.

| الحساب | الدور عمليًا | ملاحظات |
|---|---|---|
| `preview-customer-1`, `preview-customer-2` | عميل | رقم التليفون متحقَّق منه مسبقًا في الـ Seed |
| `preview-cook-1` | طبّاخ المطبخ اللي اتضاف بالـ Seed "مطبخ المعاينة" | استلام وتوصيل لـ Test zone A؛ ملف دفع بعربون 50%؛ وجبتين |
| `preview-ops-1` | أول أدمن موظفين | بياخد بس `staff.grant` و`audit.read` من إعداد الـ Bootstrap |
| `preview-ops-2` | حساب من نوع حسابات الموظفين، من غير أي صلاحيات ممنوحة | مابيشوفش أي تاب للموظفين |
| `owner-e2e-support`, `owner-e2e-ops`, `owner-e2e-finance`, `owner-e2e-admin-b`, `owner-e2e-cook-b`, `owner-e2e-cook-pending`, `owner-e2e-multirole`, `owner-e2e-cust-new-<n>` | حسابات اختبار إضافية للمالك | موجودة بس بعد ما مشغّل يجهّزها بالخطوات في [fixtures.md](../../quality/owner-acceptance/fixtures.md)؛ حالة التجهيز على الـ Preview الحي NOT VERIFIED |

**كلمات السر مابتتكتبش أبدًا في الـ Repository ده.** المستخدمين اللي في الـ Seed بيشتركوا في قيمة `PREVIEW_USER_PASSWORD` في ملف `.env` الخاص على السيرفر (VERIFIED IN CODE: `deploy/web-preview/deploy.sh` بيولّدها بـ `init-secrets`). المستخدمين المصرّح لهم بياخدوها من المالك. حسابات owner-e2e ليها كلمات سر متولّدة لكل حساب لوحده وبتتسلّم بنفس الطريقة.

## إزاي تقرا جداول الرحلات

كل رحلة تحت ليها نفس الحقول. قوائم الخطوات التفصيلية وجداول الأخطاء والاختبارات موجودة في صفحة الرحلة المربوطة؛ الصفحة دي بتضيف بس نقاط الدخول الحيّة.

* Customer Web بيستخدم Hash Routing (`window.location.hash`، VERIFIED IN CODE: `apps/customer-web/src/App.tsx`). المسارات اللي اتحقق منها: `#/` و`#/meal/<uuid>` و`#/kitchen/<slug>` و`#/checkout` و`#/orders` و`#/orders/<uuid>` و`#/account` و`#/support/<uuid>`. الـ Ids بتاعة الوجبات والأوردرات مش ثابتة بين البيئات، فابدأ من الصفحة الرئيسية واضغط.
* Operations Web مفيهوش مسارات URL: بعد تسجيل الدخول بيعرض تابات بتتحدد من حالة التطبيق (VERIFIED IN CODE: `apps/operations-web/src/App.tsx`). التنقّل دايمًا: افتح `https://ops.167-86-90-174.sslip.io/`، سجّل دخول، اضغط على التاب. التابات اللي بتظهر بتعتمد على اللي السيرفر بيسمح بيه للحساب: التطبيق بيبعت طلبات فحص (Probe Requests) بعد تسجيل الدخول، وبيعرض التاب بس لما الفحص بتاعه ينجح (تاب Finance بيظهر على رد 404 لمطبخ مش موجود). فالتاب الناقص غالبًا معناه إن الحساب ناقصه الصلاحية، لكن لو طلب فحص فشل بسبب السيرفر أو الشبكة هيختفي التاب برضه. قبل ما تبلّغ إن الصلاحية ناقصة، حمّل الصفحة تاني واقرا الـ Status والـ Body بتوع طلبات الفحص في أدوات المطوّر بالمتصفح: 401 أو 403 معاه كود خطأ ده رد Authorization؛ أما 5xx أو Timeout أو 403 بنص "Not available" العادي (فئة IP بتاعة الموظفين) فمش مشكلة صلاحيات.
* الشريط العلوي في Customer Web فيه Cart (وSign in) وزرار اللغة؛ أما التنقّل السفلي فبيعرض Home، وبس لما تكون مسجّل دخول My orders وMy account، بالإنجليزي (VERIFIED IN CODE: `apps/customer-web/src/App.tsx`؛ والنصوص في `apps/customer-web/src/i18n.ts`). زرار اللغة بيبدّل بين العربي RTL والإنجليزي LTR.

### J1 تسجيل العميل وتسجيل الدخول

| الحقل | المحتوى |
|---|---|
| الميزة وغرضها في العمل | يخلّي العميل ياخد هوية ويستخدمها قبل الأوردر. التسجيل الذاتي متعطّل في الـ Realm بتاع الـ Preview (`registrationAllowed` بقيمة false في `deploy/web-preview/identity/matbakhy-realm.preview.json`، VERIFIED IN CODE)، فـ "التسجيل" معناه إن الأدمن ينشئ الحساب مسبقًا. |
| الدور والحساب | عميل. `preview-customer-1` لتسجيل الدخول. حالة العميل الجديد محتاجة `owner-e2e-cust-new-<n>` (متجهّز، NOT VERIFIED LIVE). |
| الـ URL | `https://app.167-86-90-174.sslip.io/` |
| التنقّل | Home، وبعدها "Sign in" في الشريط العلوي. |
| الخطوات | 1. افتح الـ URL. 2. اضغط "Sign in". 3. المتصفّح بيروح للـ Origin بتاع الـ API على `/oauth2/authorization/keycloak`، وبعدها لصفحة تسجيل الدخول في `id.*`. 4. اكتب اسم المستخدم وكلمة السر اللي خدتها من المالك. 5. بترجع للصفحة اللي جيت منها. 6. لإنهاء الـ Session اضغط "Sign out". |
| النتيجة المتوقعة | التنقّل السفلي دلوقتي بيعرض "My orders" و"My account"؛ وبعد تسجيل الخروج بتتمسح مفاتيح السلة والأوردر المعلّق وتسجيل الدخول الجاي بيطلب Credentials تاني. |
| الـ Backend APIs | `GET /api/v1/me`, `POST /api/v1/auth/logout` (framework route), `GET /oauth2/authorization/keycloak`, `GET /login/oauth2/code/keycloak` |
| التنفيذ | `apps/customer-web/src/App.tsx`, `backend/app/src/main/java/com/matbakhy/app/security/SecurityConfiguration.java`, `backend/modules/identity/src/main/java/com/matbakhy/identity/web/ProfileController.java` |
| الاختبارات المرتبطة | حالات المالك `E2E-DISC-002` (قرار التسجيل) و`E2E-DISC-003` و`E2E-DISC-004` و`CFG-002` و`CFG-006`؛ الآلية: `apps/customer-web/src/App.test.tsx`, `backend/app/src/test/java/com/matbakhy/app/identity/IdentityIntegrationTest.java` |
| القيود | مفيش فورم تسجيل (بالتصميم في الـ Preview). الـ Session الافتراضية للويب مدتها 30 دقيقة من غير تحذير على الشاشة. التفاصيل: [ويب العميل](../02-user-journeys/customer-web-ar.md)، [الـ Authentication وMFA](../02-user-journeys/authentication-and-mfa-ar.md). |

### J2 اكتشاف الوجبات

| الحقل | المحتوى |
|---|---|
| الميزة والغرض | أي حد يقدر يشوف المطابخ المعتمدة بتقدّم إيه قبل ما يعمل حساب. |
| الدور والحساب | زائر (Guest)، من غير حساب. |
| الـ URL | `https://app.167-86-90-174.sslip.io/#/` |
| التنقّل | الصفحة الرئيسية هي صفحة الاكتشاف. |
| الخطوات | 1. افتح الـ URL. 2. لو حابب، اختار كارت حسب اللي بتدور عليه (Intent Tile)، اكتب في خانة البحث، اختار تصنيف (Category Chip) أو منطقة، غيّر الترتيب. 3. بدّل بين الأطباق والمطابخ. 4. افتح كارت. |
| النتيجة المتوقعة | الكروت بتتحدّث من نتيجة السيرفر مع أسعار بالجنيه (EGP). الوجبة غير المتاحة بتتعلّم بعلامة وماينفعش تتطلب. إشعار الـ Demo ظاهر (الصفحة بتقرا `environment.demo` من الإعدادات العامة). |
| الـ Backend APIs | `GET /api/v1/meals`, `GET /api/v1/zones`, `GET /api/v1/configuration/public`, `GET /api/v1/kitchens/{slug}` |
| التنفيذ | `apps/customer-web/src/Browse.tsx`, `backend/modules/catalog/src/main/java/com/matbakhy/catalog/internal/discovery/DiscoveryService.java` |
| الاختبارات المرتبطة | `E2E-DISC-001`, TC-016, TC-017؛ `backend/app/src/test/java/com/matbakhy/app/catalog/DiscoveryIntegrationTest.java` |
| القيود | الويب بيطلب 50 نتيجة مرة واحدة؛ ومفيش زرار "more" (شوف [ويب العميل](../02-user-journeys/customer-web-ar.md)). الـ Seed فيه مطبخين؛ التاني مالوش تسجيل دخول. |

### J3 تفاصيل الوجبة

| الحقل | المحتوى |
|---|---|
| الميزة والغرض | يعرض وجبة واحدة وSlots بتاعتها والحصص المتبقية عشان العميل يختار قبل ما يضيف. |
| الدور والحساب | الزائر للعرض؛ وعميل مسجّل دخول للتفضيل أو الأوردر. |
| الـ URL | `#/meal/<uuid>` مسار متحقَّق منه، لكن الـ uuid بيانات بيئة: ماتكتبهوش ثابت في الكود (Hard-coded). |
| التنقّل | Home، اضغط على كارت وجبة. |
| الخطوات | 1. افتح كارت. 2. شوف الصورة واقرا الوصف والسعر والتقييمات. 3. اختار Slot؛ الكمية محدودة بالأصغر بين حد السيرفر والحصص المتبقية. |
| النتيجة المتوقعة | الصفحة بتعرض بس الـ Slots المفتوحة اللي لسه فيها حصص. الـ Slot المستنفد مينفعش يتضاف. |
| الـ Backend APIs | `GET /api/v1/meals/{mealId}`, `GET /api/v1/kitchens/{kitchenId}/slots`, `GET /api/v1/kitchens/{kitchenId}/reviews` |
| التنفيذ | `apps/customer-web/src/Meal.tsx`, `backend/modules/catalog/src/main/java/com/matbakhy/catalog/web/MealController.java` |
| الاختبارات المرتبطة | `E2E-BASKET-012`, `E2E-DISC-107`؛ `backend/app/src/test/java/com/matbakhy/app/catalog/MealIntegrationTest.java` |
| القيود | الصور بتتحمّل من `media.*`؛ لو الصور فاضية، افحص فئة IP بتاعة العملاء لـ `media.*` الأول. |

### J4 السلة

| الحقل | المحتوى |
|---|---|
| الميزة والغرض | بناء سلة لمطبخ واحد وSlot واحد (قاعدة العمل BR-018). |
| الدور والحساب | زائر أو عميل؛ السلة مش محتاجة Session. |
| الـ URL | `#/checkout` ("Cart" في الشريط العلوي). |
| التنقّل | صفحة الوجبة، اختار الـ Slot والكمية، اضغط إضافة؛ وبعدها "Cart". |
| الخطوات | 1. ضيف وجبة. 2. ضيف وجبة من مطبخ أو Slot تاني: بتظهر رسالة استبدال-أو-إبقاء. 3. عدّل الكميات أو شيل السطور في صفحة السلة. |
| النتيجة المتوقعة | السلة بتفضل في المتصفّح (مفتاح `localStorage` اسمه `matbakhy.cart`) وبتعرض تقدير مكتوب صراحةً إنه مش السعر. |
| الـ Backend APIs | `GET /api/v1/meals/{mealId}`, `GET /api/v1/configuration/public` (حدود السطور) |
| التنفيذ | `apps/customer-web/src/cart.ts`, `apps/customer-web/src/Checkout.tsx`, `backend/modules/orders/src/main/java/com/matbakhy/orders/internal/QuoteService.java` (بيعيد فحص مطبخ واحد وSlot واحد وقت الـ Quote) |
| الاختبارات المرتبطة | TC-018, TC-147, TC-149؛ `E2E-BASKET-021`, `E2E-BASKET-024` |
| القيود | السلة لكل متصفّح؛ ومش مشتركة مع تطبيقات الموبايل. |

### J5 الـ Checkout والـ Quote

| الحقل | المحتوى |
|---|---|
| الميزة والغرض | تحويل السلة لـ Quote بسعر محسوب من السيرفر. |
| الدور والحساب | عميل برقم تليفون متحقَّق منه: `preview-customer-1` (التليفون متحقَّق منه مسبقًا في الـ Seed، DOCUMENTED ONLY). |
| الـ URL | `#/checkout` |
| التنقّل | سجّل دخول، وبعدها "Cart". |
| الخطوات | 1. لو التليفون مش متحقَّق منه، اطلب كود واكتبه (الكود مش بيظهر على الـ Preview للمستخدمين الجدد؛ شوف القيد). 2. اختار استلام أو توصيل وعنوان. 3. اختار الدفع كاش عند الاستلام أو عربون. 4. اضغط "Calculate total". |
| النتيجة المتوقعة | لوحة الـ Quote بتعرض السطور ورسوم التوصيل والإجمالي من غير أي سطر عمولة أو رسوم خدمة؛ وللعربون بتعرض المبلغ والباقي والمستلم المعتمد للمطبخ. الـ Quote صالح 5 دقايق ومابيحجزش حاجة. |
| الـ Backend APIs | `POST /api/v1/me/phone/challenges`, `POST /api/v1/quotes`, `GET/POST /api/v1/me/addresses` |
| التنفيذ | `apps/customer-web/src/Checkout.tsx`, `apps/customer-web/src/Payment.tsx`, `backend/modules/orders/src/main/java/com/matbakhy/orders/internal/QuoteService.java` |
| الاختبارات المرتبطة | TC-020, TC-158؛ `E2E-BASKET-013`, `E2E-DISC-105`؛ `backend/app/src/test/java/com/matbakhy/app/orders/QuoteIntegrationTest.java` |
| القيود | الـ Preview مفيهوش مزوّد SMS، فرقم التليفون ممكن يتأكَّد منه بس لو الـ Seed أكّده مسبقًا (السبب موثّق في [fixtures.md](../../quality/owner-acceptance/fixtures.md)). التعاملات المالية الحقيقية مش مفعّلة حاليًا: العربون تحويل يدوي خارج مطبخي. |

### J6 إنشاء الأوردر

| الحقل | المحتوى |
|---|---|
| الميزة والغرض | تسجيل أوردر واحد بالظبط من Quote لسه صالح (ماخلصتش مدته)، حتى لو الشبكة قطعت. |
| الدور والحساب | عميل (`preview-customer-1`). |
| الـ URL | `#/checkout` |
| التنقّل | بعد J5. |
| الخطوات | 1. علّم على الموافقة على الشروط وسياسة الإلغاء. 2. اضغط "Place order". 3. لو الرد ضاع، حمّل `#/checkout` تاني واستخدم إعادة المحاولة اللي بتظهر للأوردر غير المؤكَّد. |
| النتيجة المتوقعة | التطبيق بيفتح `#/orders/<id>`؛ الأوردر في حالة `PENDING_ACCEPTANCE`، والمخزون محجوز، والأسعار منسوخة في Snapshots غير قابلة للتعديل (VERIFIED IN CODE: `order_item.meal_snapshot`، وأسعار الوحدة والسطر بتتكتب في `backend/modules/orders/src/main/java/com/matbakhy/orders/internal/OrderService.java`). إعادة إرسال نفس الطلب (Replay) مابتعملش أوردر تاني. |
| الـ Backend APIs | `POST /api/v1/orders` مع الـ Header `Idempotency-Key`, `GET /api/v1/orders/by-request/{clientRequestId}` |
| التنفيذ | `OrderController.java`, `OrderService.java` تحت `backend/modules/orders/src/main/java/com/matbakhy/orders/` |
| الاختبارات المرتبطة | TC-022, TC-023, TC-024, TC-044؛ `E2E-ORDER-021`؛ `backend/app/src/test/java/com/matbakhy/app/orders/OrderPlacementIntegrationTest.java` |
| القيود | الأوردر بيقعد في طابور الطبّاخ؛ ولو ماحدش اتصرّف فيه بتنتهي صلاحيته عند موعد القبول (Job بتاع السيستم). ماتعملش أوردرات على الـ Preview المشترك لاختبار تحميل (Load Testing) أو إساءة استخدام. |

### J7 تتبّع الأوردر

| الحقل | المحتوى |
|---|---|
| الميزة والغرض | يخلّي العميل يتابع الأوردر ويتصرّف في الحالات اللي السيرفر بيسمح بيها. |
| الدور والحساب | صاحب الأوردر. |
| الـ URL | `#/orders` و`#/orders/<uuid>` |
| التنقّل | التنقّل السفلي "My orders"، وبعدها اضغط على أوردر. |
| الخطوات | 1. اقرا الـ Timeline (بتتحدّث كل 10 ثواني). 2. طول ما الأوردر معلّق تقدر تلغيه مباشرة. 3. لأوردر بعربون اضغط "I sent the deposit". 4. اعرض كود التسليم لما الأوردر يبقى جاهز. |
| النتيجة المتوقعة | الأزرار بتظهر بس لو السيرفر حطّها ضمن الإجراءات المسموحة؛ وكود التسليم بيظهر بس في حالة READY أو OUT_FOR_DELIVERY وبس لصاحب الأوردر. Id أوردر عميل تاني بيرد 404. |
| الـ Backend APIs | `GET /api/v1/orders`, `GET /api/v1/orders/{orderId}`, `POST /api/v1/orders/{orderId}/cancel`, `POST /api/v1/orders/{orderId}/payment/transfer-report` |
| التنفيذ | `apps/customer-web/src/Orders.tsx`, `backend/modules/orders/src/main/java/com/matbakhy/orders/web/OrderController.java` |
| الاختبارات المرتبطة | TC-026, TC-028, TC-031؛ `E2E-ORDER-027`, `E2E-ORDER-120`؛ `backend/app/src/test/java/com/matbakhy/app/orders/OrderLifecycleIntegrationTest.java` |
| القيود | Polling من غير Push؛ ومفيش إيميل أو SMS بيتبعت. |

### J8 عمليات المطبخ (اللوحة، الوجبات، الـ Slots، الإعدادات)

| الحقل | المحتوى |
|---|---|
| الميزة والغرض | يخلّي طبّاخ معتمد يدير الوجبات والـ Slots المحددة بتواريخ وسعة وإعدادات المطبخ. |
| الدور والحساب | طبّاخ: `preview-cook-1` (المطبخ اللي في الـ Seed). |
| الـ URL | `https://ops.167-86-90-174.sslip.io/` (محتاج فئة IP بتاعة الموظفين، وفئة العملاء كمان لأن تسجيل الدخول والـ API بيعدّوا على Hosts بتتبع فئة العملاء) |
| التنقّل | افتح الـ URL، سجّل دخول، وبعدها التابات Home وOrders وMeals وSlots وStatement وInsights وSettings. |
| الخطوات | 1. سجّل دخول. 2. "Meals": أنشئ أو عدّل وجبة، انشر أو أوقف مؤقتًا أو أرشف، واقرا تاريخ الأسعار. 3. "Slots": أنشئ Slot وحدد السعة. 4. "Settings": الوصف، الاستلام، مناطق التوصيل، إيقاف مؤقت أو استئناف، بروفايل دفع العربون. |
| النتيجة المتوقعة | بيظهر بس مطبخ المتصل نفسه. الوجبات الموقوفة مؤقتًا أو المسودة بتختفي من الاكتشاف. مستلم الدفع اللي اتغيّر بيفضل مخفي عن العملاء لحد ما الموظفين يوافقوا. |
| الـ Backend APIs | `GET /api/v1/cook/dashboard`, `/api/v1/cook/meals`, `/api/v1/cook/slots`, `GET/PUT /api/v1/cook/payment-profile` |
| التنفيذ | `apps/operations-web/src/cook/Meals.tsx`, `Slots.tsx`, `Settings.tsx`, `Payments.tsx` (نفس المجلد)، والـ Backend `backend/modules/cooks/src/main/java/com/matbakhy/cooks/` |
| الاختبارات المرتبطة | TC-008, TC-011, TC-153؛ `E2E-COOK-054`, `E2E-FIN-103`؛ `apps/operations-web/e2e/cook-workspace.e2e.ts` |
| القيود | حساب من غير مطبخ معتمد بيشوف بس لوحة حالة طلب التقديم. التفاصيل: [عمليات الطبّاخ](../02-user-journeys/cook-operations-ar.md). |

### J9 معالجة أوردرات المطبخ

| الحقل | المحتوى |
|---|---|
| الميزة والغرض | تحريك أوردر العميل خلال القبول والتحضير والجاهزية والإرسال والتسليم بانتقالات حالة السيرفر هو اللي بيفحصها. |
| الدور والحساب | طبّاخ (`preview-cook-1`) وعميل (`preview-customer-1`) عمل أوردر لنفس المطبخ. |
| الـ URL | `https://ops.167-86-90-174.sslip.io/` |
| التنقّل | سجّل دخول، التاب "Orders" (أو الاختصار في Home). |
| الخطوات | 1. افتح الأوردر المعلّق قبل موعده واضغط قبول (أو ارفض مع سبب). 2. لأوردر بعربون، بلّغ إن العربون اتستلم قبل التحضير. 3. حضّر، وبعدها Ready. 4. الاستلام: اكتب كود التسليم بتاع العميل المكوّن من 6 أرقام. التوصيل: ابعت الأوردر (Dispatch) مع مرجع المندوب، وبعدها الكود. |
| النتيجة المتوقعة | صفحة تتبّع العميل بتلحق في حدود 10 ثواني تقريبًا. خمس أكواد غلط بتقفل تحدّي التسليم وبتحتاج إجراء استرجاع (Recovery) من فريق العمليات. |
| الـ Backend APIs | `GET /api/v1/cook/orders`, `POST /api/v1/cook/orders/{orderId}/accept`, `.../prepare`, `.../ready`, `.../dispatch`, `.../handover`, `.../payment/receipt` |
| التنفيذ | `apps/operations-web/src/cook/Orders.tsx`, `backend/modules/orders/src/main/java/com/matbakhy/orders/web/CookOrderController.java` |
| الاختبارات المرتبطة | TC-026, TC-027, TC-029, TC-031, TC-159؛ `E2E-ORDER-025`, `E2E-ORDER-033`؛ `backend/app/src/test/java/com/matbakhy/app/payments/DepositPaymentIntegrationTest.java` |
| القيود | تطبيق ويب الطبّاخ مفيهوش تنبيه Push للأوردرات الجديدة. بيانات المندوب بتتكتب كنص حر. التفاصيل: [عمليات الطبّاخ](../02-user-journeys/cook-operations-ar.md). |

### J10 إدارة العمليات

| الحقل | المحتوى |
|---|---|
| الميزة والغرض | الموظفين بيوافقوا على الطبّاخين، وبيشرفوا على المحتوى (Moderation)، وبيراقبوا الأوردرات، وبيمنحوا الصلاحيات. في الكود مفيش أدوار موظفين، فيه بس منح صلاحيات. |
| الدور والحساب | `preview-ops-1` معاه بس `staff.grant` و`audit.read`، فبيشوف تاب Staff permissions وتاب Security (إعداد التحقق بخطوتين الخاص بالحساب نفسه؛ وأدوات السياسة والـ Reset محتاجة صلاحيات MFA، واللي الحساب ده مش معاه). مراجعة الطبّاخين والإشراف على المحتوى ومراقبة الأوردرات محتاجين `owner-e2e-ops` أو حساب اتمنح الصلاحيات دي (التجهيز NOT VERIFIED LIVE). |
| الـ URL | `https://ops.167-86-90-174.sslip.io/` |
| التنقّل | سجّل دخول، وبعدها التابات: Cook applications وCatalogue moderation وOrder monitoring وOperating configuration وStaff permissions وSecurity (كل واحد بس لو مسموح). |
| الخطوات | 1. سجّل دخول كموظف. 2. "Cook applications": ابدأ المراجعة، وبعدها وافق أو اطلب تعديلات أو ارفض، دايمًا بسبب. 3. "Staff permissions": امنح أو اسحب صلاحية من حساب تاني. |
| النتيجة المتوقعة | الإجراءات عالية المخاطر (قرار الطبّاخ، المنح والسحب، كتابة الإعدادات) بتترفض من غير MFA حديث والصفحة بتعرض تسجيل دخول بخطوة إضافية (Step-up). كل منح بيتسجّل في الـ Audit. |
| الـ Backend APIs | `GET /api/v1/ops/cooks`, `/api/v1/ops/catalogue`, `/api/v1/ops/orders`, `/api/v1/ops/staff` والـ Endpoints بتاعة القرار والمنح بتاعتهم |
| التنفيذ | `apps/operations-web/src/ops/` (Reviews, Catalogue, OpsOrders, Configuration, Staff)، `backend/modules/identity/src/main/java/com/matbakhy/identity/web/StaffController.java`, `backend/app/src/main/java/com/matbakhy/app/security/StaffAssurance.java` |
| الاختبارات المرتبطة | TC-111, TC-123, TC-126؛ `E2E-OPS-105`؛ `apps/operations-web/src/App.test.tsx` |
| القيود | `audit.read` و`integration.ops` ليهم API Endpoints لكن من غير تاب. مصفوفة الصلاحيات ومنطق التابات: [رحلات الـ Backoffice والأدمن](../02-user-journeys/backoffice-admin-ar.md). |

### J11 المالية والدعم

| الحقل | المحتوى |
|---|---|
| الميزة والغرض | موظفو الدعم بيردوا على حالات العملاء؛ وموظفو المالية بيوافقوا على مستلمي الدفع، ويبتّوا في نزاعات العربون، ويتعاملوا مع الاسترداد والكشوف. |
| الدور والحساب | الدعم: `owner-e2e-support`. المالية: `owner-e2e-finance`. جانب العميل في الدعم: `preview-customer-1`. تجهيز حسابات المالك NOT VERIFIED LIVE. |
| الـ URL | العميل: `#/account` وبعدها `#/support/<uuid>`. الموظفين: `https://ops.167-86-90-174.sslip.io/` |
| التنقّل | العميل: "My account"، قسم الدعم. الموظفين: التاب "Support" أو "Finance". |
| الخطوات | العميل يفتح حالة عن أوردر. الدعم يقرا الطابور ويرد ويقفل بكود وسبب. المالية تفتح لوحات الدفع جوه "Finance" وتوافق على مستلم أو تقرر في نزاع. |
| النتيجة المتوقعة | الحالة المحلولة بتتقفل بس بكود حل وسبب؛ ورد العميل بيعيد فتحها. أوردر العربون مينفعش يتحضّر قبل ما المطبخ يبلّغ بالاستلام. |
| الـ Backend APIs | `GET/POST /api/v1/support/cases`, `/api/v1/ops/cases`. المدفوعات: `POST /api/v1/cook/orders/{orderId}/payment/receipt`, `GET /api/v1/ops/payment-recipients`, `POST /api/v1/ops/kitchens/{kitchenId}/payment-recipient/approval`, `GET /api/v1/ops/payments`, `POST /api/v1/ops/orders/{orderId}/payment/decision`. الكشوف والاسترداد والتسويات: `/api/v1/ops/finance/**` |
| التنفيذ | `apps/operations-web/src/ops/Cases.tsx`, `Finance.tsx`, `Payments.tsx` (نفس المجلد)، `backend/modules/support/src/main/java/com/matbakhy/support/web/SupportController.java`, `backend/modules/orders/src/main/java/com/matbakhy/orders/web/PaymentController.java` |
| الاختبارات المرتبطة | TC-041, TC-032, TC-159, TC-160؛ `E2E-OPS-105`, `E2E-FIN-103` |
| القيود | شاشة التحقق من الكاش بتاعة المالية مش موجودة في الويب (API بس). مفيش API بنك؛ وفيه شخص بيطابق العربونات يدويًا (Reconciliation). التفاصيل: [المالية والدعم](../02-user-journeys/finance-and-support-ar.md). |

### J12 الـ Authentication والمصادقة متعددة العوامل (MFA)

| الحقل | المحتوى |
|---|---|
| الميزة والغرض | حماية الحسابات بكلمة سر لمرة واحدة اختيارية (للعميل) أو مطلوبة بالسياسة (للمطبخ والموظفين). |
| الدور والحساب | العميل: `preview-customer-1`. إدارة السياسة محتاجة أدمن تاني معاه `security.mfa_policy` (زي `owner-e2e-admin-b`، NOT VERIFIED LIVE). |
| الـ URL | Customer Web `#/account` (قسم الأمان)؛ Operations Web التاب "Security". |
| التنقّل | العميل: "My account"، الأمان. الموظفين: تاب Security. |
| الخطوات | 1. افتح قسم الأمان. 2. اقرا وضع السياسة وحالتها. 3. لو اختياري ومقفول، شغّله؛ الصفحة بتوديك لتسجيل الدخول الآمن عشان تسجّل تطبيق Authenticator لو لزم. 4. الإيقاف محتاج Step-up حديث. |
| النتيجة المتوقعة | أوضاع السياسة هي disabled وoptional وrequired. من غير إثبات MFA حديث الـ API بيرد 403 `STEP_UP_REQUIRED`. |
| الـ Backend APIs | `GET /api/v1/me/security`, `PUT /api/v1/me/security/mfa`؛ Endpoints بتاعة سياسة الموظفين والـ Reset |
| التنفيذ | `apps/customer-web/src/Security.tsx`, `apps/operations-web/src/ops/Security.tsx`, `backend/app/src/main/java/com/matbakhy/app/mfa/MfaController.java` |
| الاختبارات المرتبطة | `CFG-008`, `CFG-102`؛ `backend/app/src/test/java/com/matbakhy/app/mfa/MfaPolicyIntegrationTest.java` |
| القيود | TOTP بس، من غير أكواد استرجاع. هل الـ Realm الحي بتاع Keycloak اتطبّق عليه الـ Migration بتاع تسجيل الدخول بخطوة إضافية (Step-up) ولا لأ فهو NOT VERIFIED. كلمة السر لمرة واحدة بتاعة الـ master realm في Keycloak حاجة مختلفة (كونسول الأدمن، شوف التحذير فوق). التفاصيل: [الـ Authentication وMFA](../02-user-journeys/authentication-and-mfa-ar.md). |

### J13 تغيّر الأسعار عند الـ Checkout

القاعدة: الأوردر المكتمل أو اللي اتسجّل بيحتفظ بالسعر اللي اتعمل بيه؛ والسعر اللي اتغيّر بين الـ Quote والتسجيل بيترفض ويتطلب Quote جديد. ده اتأكّد في الكود والاختبارات.

| الحقل | المحتوى |
|---|---|
| الميزة والغرض | العميل مايتحاسبش أبدًا بسعر ماشافهوش، وسعر أوردر اتسجّل فعلًا مايتغيّرش أبدًا (القرار D13). |
| الدور والحساب | العميل `preview-customer-1` ومعاه طبّاخ المطبخ `preview-cook-1` بيغيّر سعر وجبة في متصفّح تاني. |
| الـ URL | Customer Web `#/checkout`؛ Operations Web التاب "Meals". |
| التنقّل | العميل يحسب Quote. الطبّاخ يعدّل سعر الوجبة. بعدها العميل يضغط "Place order". |
| الخطوات | 1. العميل: Quote ويقف قبل التسجيل. 2. الطبّاخ: Meals، يعدّل السعر، يحفظ. 3. العميل: يضغط "Place order". |
| النتيجة المتوقعة | السيرفر بيرد 409 بكود `PRICE_CHANGED` ومابيسجّلش حاجة؛ تطبيق الويب بيطلب Quote جديد ويعرض اللي اتغيّر؛ والعميل لازم يقبل الإجمالي الجديد صراحةً. الأوردر اللي اتسجّل فعلًا بيحتفظ بأسعاره الأصلية. |
| الـ Backend APIs | `POST /api/v1/quotes`, `POST /api/v1/orders` |
| التنفيذ | VERIFIED IN CODE: الدالة `requireValid` في `backend/modules/orders/src/main/java/com/matbakhy/orders/internal/QuoteService.java` بترمي `PRICE_CHANGED` بتفصيلة `POLICY_CHANGED` أو `MEAL_CHANGED` (نسخة الوجبة (Revision) أو سعر الوحدة اختلف) أو `TOTAL_CHANGED` (رسوم التوصيل أو الإجمالي اختلف) أو `PAYMENT_CHANGED` (شروط العربون أو المستلم اختلفت)؛ وبتقفل صفوف التسعير الأول عشان أي تعديل متزامن يتنفّذ بالترتيب. `OrderService` بينسخ الـ Snapshot وسعر الوحدة والسطر لكل صنف في الأوردر وقت التسجيل. رد فعل الويب في `apps/customer-web/src/Checkout.tsx` (عند `PRICE_CHANGED` بيعمل Quote جديد بدل ما يعيد الإرسال). |
| الاختبارات المرتبطة | TC-154, TC-155, TC-156, TC-153؛ حالات المالك `E2E-BASKET-014`, `E2E-BASKET-015`؛ `backend/app/src/test/java/com/matbakhy/app/orders/PriceChangeIntegrationTest.java`, `backend/app/src/test/java/com/matbakhy/app/orders/QuoteIntegrationTest.java`, `apps/customer-web/src/App.test.tsx` (الـ Block "D13: changed prices need informed reconfirmation") |
| القيود | تغيّر منطقة العنوان بيظهر كـ `STATE_CONFLICT` مع `ADDRESS_CHANGED`، مش `PRICE_CHANGED`. هل الـ Preview الحي شغّال بالـ Build اللي فيه السلوك ده: أكّد من خلال [مسار الـ Deployment](../08-infrastructure/deployment-pipeline-ar.md)؛ الفحص ده ماحصلش هنا. |

## فحوصات سريعة آمنة

| الأمر | الجهاز والمستخدم | للقراءة فقط | المتوقع | لو فشل |
|---|---|---|---|---|
| `curl -s -o /dev/null -w "%{http_code}\n" https://app.167-86-90-174.sslip.io/` | اللابتوب بتاعك، أي مستخدم | أيوه | 200 من IP في قائمة السماح، و 403 من غيره | 403: الـ IP بتاعك خارج فئة العملاء، اسأل المالك. Timeout: شوف حل المشاكل |
| `curl -s https://api.167-86-90-174.sslip.io/api/v1/configuration/public` | اللابتوب بتاعك، أي مستخدم | أيوه | JSON فيه `environment.name` بقيمة PREVIEW | زي اللي فوق |
| `curl -s -o /dev/null -w "%{http_code} %{redirect_url}\n" https://id.167-86-90-174.sslip.io/` | اللابتوب بتاعك، أي مستخدم | أيوه | 302 وعنوان kc-admin | مش خطأ؛ ماتتبعش الـ Redirect وماتسجّلش دخول إلا لو انت أدمن |

الفحص الخاص بالـ Repository نفسه هو `deploy/web-preview/smoke.sh <preview-domain>` (VERIFIED IN CODE: التعليق في أول الملف بيقول إنه للقراءة فقط ومابينشئش حساب ولا أوردر ولا رفع ملف؛ ومااتشغّلش لأجل الصفحة دي). استخدم الأمر المناسب للجهاز اللي معاك:

* جهاز في فئة العملاء وفئة الموظفين الاتنين (زي الـ VPS نفسه): `sh deploy/web-preview/smoke.sh 167-86-90-174.sslip.io`. السكريبت بيحمّل تطبيق العمليات ويفشل لو `ops.*` رد 403.
* جهاز في فئة العملاء وخارج فئة الموظفين: `SMOKE_OPS=skip sh deploy/web-preview/smoke.sh 167-86-90-174.sslip.io`. مع `SMOKE_OPS=skip` السكريبت بيتوقع 403 من `ops.*` بدل ما يحمّل تطبيق العمليات.

## القيود المعروفة وفجوات التوثيق

* حسابات owner-e2e وأرقام التليفون المتحقَّق منها مسبقًا بتتجهّز في كل Run؛ وجودها على الـ Preview الحي NOT VERIFIED LIVE. أدمن مصرّح له يقدر يعرض مستخدمي الـ Realm بأدوات أدمن Keycloak الموصوفة في [كونسول أدمن Keycloak](../../operations/14-keycloak-admin-console.md).
* قيم `CUSTOMER_ALLOWED_IPS` و`STAFF_ALLOWED_IPS`، وهل `ufw` شغّال، كلها NOT VERIFIED LIVE؛ شوف [التحقق بأوامر للقراءة فقط بواسطة الأدمن](../08-infrastructure/admin-readonly-verification-ar.md).
* المستندات الموجودة (`docs/operations/06-web-preview.md` و`deploy/web-preview/README.md`) اتكتبت لـ Host Hetzner مقترح؛ والـ Host الشغّال هو Contabo. اعتبر الكلام عن تحديد حجم الـ Host في المستندات دي OUTDATED / INCORRECT للـ Preview الحي.
* صفحة [بيئة المعاينة](preview-environment-ar.md) الموجودة بتسرد `kc-admin` على إنه بيتفتح بـ `deploy.sh admin-console enable`؛ والـ 302 الحي بيأكّد إنه منشور حاليًا، لكن مين فعّله وإمتى فهو NOT VERIFIED.
* مفيش Deep Links لصفحات العمليات؛ التنقّل بالتابات بس (VERIFIED IN CODE).
