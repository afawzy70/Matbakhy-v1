# محاكاة المطوّر الجديد (الجاهزية للأسبوع الأول)

> **Owner role:** Handbook navigation
> **Status:** REFERENCE — دليل على مراجعة توثيقية بس؛ بيثبت إن التوثيق بيوصّل للأماكن الصح، مش إن الـ Build شغّال هنا
> **Language / اللغة:** [English](fresh-developer-simulation.md) · العربية

**المنهج.** Subagent من غير أي سياق عن المشروع استلم نسخة نضيفة من الـ Branch عند الـ Commit `6c94a7c` واتقاله يستخدم توثيق الـ Repository بس. جهاز التأليف عليه python3 وNode 22 بس من غير pnpm ولا Java ولا Maven ولا Docker daemon، فأي حاجة محتاجاهم **BLOCKED** ومش PASS أبدًا. الـ PASS تحت معناه "اتنفّذ من التوثيق، مع فحص دليل الملف/السطر"، مش "اتشغّل في الـ Production". دي محاكاة بالذكاء الاصطناعي مش Onboarding بشري؛ أول أسبوع حقيقي عند Mohamed (شوف [التقييم النهائي](../06-onboarding-labs/final-assessment-ar.md)) هو الاختبار المعتمد.

| # | المهمة | النتيجة | الدليل / ملاحظة |
|---|---|---|---|
| 1 | شرح أهداف المنتج | PASS | `00-getting-started/project-overview.md`، `01-business/product-vision.md` |
| 2 | شرح الأدوار وإزاي بتشتغل الصلاحيات | PASS | مفيش Role Objects؛ 17 قيمة `Permission` بتتمنح عن طريق `user_grant` وبتتقرا من جديد مع كل Request؛ التحديد على مستوى الـ Object بيرجّع 404 |
| 3 | تحديد مكان Customer Web | PASS | `apps/customer-web/src` |
| 4 | تحديد مكان الموبايل | PASS | `apps/customer-mobile` |
| 5 | تحديد مكان المطبخ والـ Back-office | PASS | `apps/operations-web/src/cook`، `src/ops` |
| 6 | تحديد مكان تسجيل الأوردر | PASS | `OrderController.place` → `OrderService.place` → `placeOnce` → `QuoteService.requireValid` (أرقام السطور اتفحصت) |
| 7 | شرح التسعير والـ Snapshots وعرض السعر اللي مبقاش مواكب للأسعار أو الشروط الحالية | PASS | `QuoteService.create/requireValid`: `QUOTE_EXPIRED`، `PRICE_CHANGED`؛ الـ Client لازم يقبل التغيير صراحةً |
| 8 | شرح التكامل مع Keycloak | PASS | الـ Clients، وفئات MFA وهي CUSTOMER/KITCHEN/STAFF بقيم DISABLED/OPTIONAL/REQUIRED، و V31 بيزرع OPTIONAL، والصف الناقص بيفشل بأمان إلى REQUIRED |
| 9 | تحديد الأدوات المطلوبة | PASS | JDK 21، Maven wrapper، Node 24.21.0، pnpm 10.34.6، Docker Compose v2، Python 3 |
| 10 | متابعة الإعداد المحلي | BLOCKED | `scripts/dev.sh doctor` بلّغ صح عن JDK وNode 24 وpnpm وDocker daemon الناقصين |
| 11 | تشغيل Build واختبارات مختارة | PARTIAL / BLOCKED | اختبارات Python gate وفحوصات الـ Catalog والـ Fixtures اتشغّلت ونجحت (21 اختبار gate؛ 200 حالة)؛ الـ `verify` بتاع الـ Backend وpnpm lint/test/build و`dev.sh e2e` BLOCKED (مفيش Toolchain) |
| 12 | إيجاد مسار العمل لـ Issue جديدة | PASS (thin) | كان فيه Lab اليوم الخامس وصفحة مسار الـ PR بس → اتضاف بعد كده قسم "Starting on an issue" |
| 13 | تجهيز تقييم DOC-001 | PASS with matrix gap | افتراض "مدة عرض السعر 5 → 4 دقايق" لقى 11 صفحة بتذكر 5 دقايق؛ والـ Matrix ماكانتش بتفرض صفحات التسعير → اتضافت المنطقة `quotes-pricing` |
| 14 | شرح إزاي الـ PR بيوصل لبيئة المعاينة | PASS | الـ PR عمره ما بيوصل؛ بس Merge من المالك على `develop` هو اللي بيشغّل `deploy-preview.yml` (انتظار الـ CI، Images بـ SHA، Deploy بـ SSH، Smoke، تفعيل Admin Console) |
| 15 | اختيار حالات E2E للمالك لإعادة تأكيد تغيّر السعر | PASS | E2E-BASKET-014، E2E-BASKET-015، E2E-BASKET-022، E2E-ORDER-112، E2E-OPS-106 اتحقق منهم في `docs/quality/owner-acceptance/cases/` |

## عيوب التوثيق اللي اتلاقت واللي اتعمل فيها

| العيب | المعالجة |
|---|---|
| README بتاع الدليل كان بيربط بالصفحة دي (وكانت وقتها مش موجودة)، وده كسر `doc001_check` و`validate_blueprint` و`check_scope` على نسخة نضيفة | الصفحة اتكتبت؛ والـ Scripts اتشغّلت تاني |
| Lab اليوم التالت ذكر مجلد ناتج Build كأنه مسار في الـ Repository | اتعدّلت الصياغة لتوضّح إنه حاجة الـ Build هو اللي بينشئها |
| مسارات Truncated/nonexistent في صفحة الفجوات | اتصلّحت |
| صفحة مسار Git ادّعت إن قالب الـ PR مفيهوش Block الـ DOC-001 | اتصلّحت |
| README بتاع قبول المالك قال 199 حالة / 53 | اتصلّح إلى 200 / 54 |
| الـ README الرئيسي كان لسه بيوصف Planning Pack | نص الحالة اتحدّث؛ والأعداد التاريخية اتوسمت |
| "5 دقايق" اتعرضت كقاعدة ثابتة مع إنها `commerce_policy.quote_ttl_seconds` | اتضاف قسم "Changing the quote lifetime" في [تنفيذ التسعير](../04-codebase/pricing-implementation-ar.md) |
| الـ Realm بتاع المعاينة مفيهوش Client اسمه `matbakhy-mobile`؛ وتسمية مزوّد الاستضافة مش متسقة؛ وسلاسل حالة قديمة في `backlog.json`/`endpoints.json`/`docs/status/latest.md` | اتسجّلت في [الجرد والفجوات](inventory-and-gaps-ar.md)؛ ومستندات المصدر لسه محتاجة تتصلّح في PRs خاصة بيها |

## المتطلبات المسبقة الخفية اللي اتكشفت
Node بالظبط 24.21.0 وpnpm 10.34.6 (محتاجين شبكة)؛ وDocker daemon شغّال لاختبارات الـ Backend (Testcontainers)؛ وJDK 21؛ و`gh` متوثّق؛ وIP عليه allow-list من المالك مع بيانات دخول لبيئة المعاينة؛ و macOS لـ iOS.

## اللي المحاكاة دي ماغطتوش
مفيش تثبيت Dependencies، ولا Build، ولا اختبار Backend، ولا Playwright، ولا تشغيل Native (مفيش Toolchain)، ولا إعداد Windows/macOS، ولا جهاز فعلي، ولا وصول لبيئة المعاينة. اوسم الحاجات دي بـ **NOT VERIFIED** لحد ما إنسان يخلّص Lab اليوم الرابع ويسجّل الدليل.
