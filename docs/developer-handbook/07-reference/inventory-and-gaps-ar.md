# جرد الـ Repository وتقييم فجوات التوثيق

> **Owner role:** Handbook navigation
> **Status:** REFERENCE — لقطة اتاخدت وقت كتابة الدليل (2026-10-19، `develop` عند `d5d6ff5`)؛ كل ملاحظة بتذكر الملف اللي لازم يتصلّح
> **Language / اللغة:** [English](inventory-and-gaps.md) · العربية

المنهج: الكتّاب قرأوا الكود المصدري والـ Migrations والعقود والإعدادات والاختبارات وقارنوها بملفات Markdown الموجودة. لما التوثيق والكود اختلفوا، وثقنا في الكود. الملاحظات الموسومة *handbook fixed* منعكسة صح في الدليل بالفعل؛ أما *source document* فلسه محتاج يتصلّح في PR خاص بيه (DOC-001 بتنطبق على الـ PR ده). مفيش حاجة هنا اتنفّذت إلا لو اتذكر كده: مفيش pnpm ولا Java ولا Docker ولا Maven على جهاز التأليف، فأوامر الـ Build/الاختبار **NOT VERIFIED** (اتقرت من `package.json` والـ Workflows والـ Scripts).

## 1. الجرد

| المنطقة | المحتوى الفعلي | صفحة الدليل |
|---|---|---|
| الـ Backend | Maven متعدد الموديولات Java 21 / Spring Boot: `backend/modules/{availability,catalog,cooks,finance,identity,notifications,operations,orders,support}`، `backend/shared`، `backend/app` (التجميع، Flyway V1–V31، 80 ملف اختبار تكامل) | [معمارية الـ Backend](../03-architecture/backend-architecture-ar.md)، [دليل موديولات الـ Backend](../04-codebase/backend-module-guide-ar.md) |
| الويب | `apps/customer-web`، `apps/operations-web` (React/TypeScript/Vite)، `packages/api-client`، `packages/design-tokens` | [معمارية الـ Frontend](../03-architecture/frontend-architecture-ar.md) |
| الموبايل | `apps/customer-mobile` (Expo/React Native؛ `e2e-native`، `e2e-ios`، Scripts) | [معمارية الموبايل](../03-architecture/mobile-architecture-ar.md) |
| العقود | `contracts/openapi.yaml`، `contracts/endpoints.json` (API-nnn) | [عقود الـ API](../04-codebase/api-contracts-ar.md) |
| الهوية | قوالب Realm الخاصة بـ Keycloak في `infra/identity`، وRealm المعاينة والـ Scripts في `deploy/web-preview/identity` | [الهوية والأمان](../03-architecture/identity-and-security-ar.md) |
| الـ Deployment | `infra/compose.yaml`، `deploy/web-preview/*` (Caddy، deploy.sh، admin-console، تجهيز owner-e2e) | [البنية التحتية والـ Deployment](../03-architecture/infrastructure-and-deployment-ar.md) |
| CI/CD | `.github/workflows/`: `ci.yml`، `blueprint-integrity.yml`، `native-build.yml`، `deploy-preview.yml`، `preview-images.yml`، `owner-e2e-fixtures.yml`، `owner-e2e-provision.yml`، `doc001.yml` (جديد) | [CI/CD](../05-developer-workflow/ci-cd-ar.md) |
| التخطيط | `planning/*.json` (الـ Backlog، القواعد، السيناريوهات، القرارات، الـ Gates) | [قواعد العمل](../01-business/business-rules-ar.md) |
| الجودة | `docs/quality/`، و`docs/quality/owner-acceptance/` (200 حالة)، و`verification/runs/` | [تشغيل الاختبارات](../05-developer-workflow/test-execution-ar.md) |

## 2. تعارضات بين التوثيق والكود (مستندات المصدر لسه محتاجة تتصلّح)

| # | الملاحظة | فين | المصدر الموثوق |
|---|---|---|---|
| 1 | `docs/product/06-permissions.md` بيوصف أدوار؛ أما الكود فيه منح صلاحيات (`Permission.java`، 17 قيمة)، و Support/Ops/Finance/Admin هما حزم منح. المستند بيسمّي `finance.settlement_verify`؛ وصلاحية الكود هي `finance.settlement`. مالك المطبخ = الطبّاخ (`kitchen.owner_id` فريد؛ ومفيش موظفين للمطبخ). | `docs/product/06-permissions.md` | الكود |
| 2 | `docs/product/04-state-machines.md` بيخلّي "الطبّاخ/العمليات" يرسلوا ويكمّلوا؛ في الكود الطبّاخ المالك بس هو اللي بيرسل/بيسلّم، والعمليات تقدر تلغي استثناءً من PREPARING/READY أو تتجاوز تسليم. حالات الكاش مختلفة (`COLLECTION_REPORTED` مقابل `cash_obligation` بقيم DUE/REPORTED/VERIFIED/DISPUTED/NOT_DUE). | `docs/product/04-state-machines.md` | الكود |
| 3 | المطبخ الـ PAUSED لسه يقدر يقبل الأوردرات المعلّقة؛ أما SUSPENDED فلأ. مفيش مسار في الكود بيحط المطبخ في CLOSED. | docs/product، `KitchenService` | الكود |
| 4 | BR-054 (حدود المعدّل للدخول والـ Quote والأوردر والبحث) متنفذة جزئيًا بس (`RATE_LIMITED` موجود لإرسال تحقق الموبايل ومحاولات التسليم وبلاغات التقييمات والرفع). حالة المالك CFG-039 متفقة. | `planning/rules.json` | الكود |
| 5 | كل قاعدة في `planning/rules.json` لسه حالتها PROPOSED_DEFAULT مع إن القرارات اتأكدت. العربون ملوش رقم BR (D16، MB-051..053). | `planning/rules.json` | الكود + القرارات |
| 6 | README بتاع قبول المالك بيقول 199 حالة (config-security 53)؛ والـ Catalog فيه 200 (54). | `docs/quality/owner-acceptance/README.md` | cases/*.json |
| 7 | `docs/operations/06-web-preview.md` ("prepared, not deployed") و`deploy/web-preview/README.md` ("Nothing here has been deployed") قديمين: `deploy-preview.yml` بينشر كل Push على `develop`. | الملفين دول | الـ Workflow |
| 8 | مزوّد الاستضافة: الـ Workflows بتقول Contabo؛ و`docs/operations/09-staging-hetzner-preparation.md` و`docs/operations/12-owner-server-setup.md` و`deploy/web-preview/README.md` و`compose.4gb.yaml` بيقولوا Hetzner. الـ CD بينشر على `/srv/matbakhy-staging`؛ و`.env.example` بيقول `/srv/matbakhy-preview`. المزوّد **NOT VERIFIED**؛ والـ Workflow هو اللي بيشتغل. | docs/operations | الـ Workflow |
| 9 | الـ `README.md` الرئيسي كان بيقول "no product feature exists" (اتحدّث بالـ PR ده). كذا مستند بيسمّي `implementation/mvp` الـ Branch النشط؛ والممارسة هي `develop`؛ و`native-build.yml` لسه بيفلتر الـ Pushes على `implementation/mvp`. | README، docs، Workflow | تاريخ git |
| 10 | Node: الـ Repository بيثبّت 24.21.0 (`.node-version`، engine-strict)؛ بيئة التأليف كان عليها Node 22 (مش مدعوم). `toolchain.md` بيسرد LOCAL/CI/STAGING/PRODUCTION، والـ enum في `MatbakhyProperties` فيه كمان PREVIEW؛ و`application.yaml` بيطلب `APP_ENV` (مفيش قيمة افتراضية لـ `matbakhy.environment`). | `docs/engineering/toolchain.md` | الكود |
| 11 | `docs/security/mfa-policy.md` الخطوة 5 بتقول إن الموبايل مابيتعاملش مع 403 STEP_UP_REQUIRED، بينما `AppContext.tsx`/`mobileApi.ts` بيظهروا بانر Step-up؛ وكمان بيستشهد بخطة اختبار أجهزة iOS Beta تحت مجلد مستندات الموبايل، وده مش موجود. | mfa-policy.md | الكود |
| 12 | تعليق `apps/customer-mobile/src/auth.ts` بيقول جلسة المتصفح المؤقتة "على iOS"؛ والكود بيمرّر `preferEphemeralSession: true` على المنصتين (سلوك Android NOT VERIFIED). | auth.ts | الكود |
| 13 | `matbakhy-realm.preview.json` مفيهوش Client اسمه `matbakhy-mobile` (القالب المحلي فيه). تسجيل دخول الموبايل على المعاينة المنشورة NOT VERIFIED. | `deploy/web-preview/identity` | الملف |
| 14 | `POST /api/v1/auth/logout` هو Route تبع الـ Framework بيناديه تطبيقات الويب بـ Type Cast؛ مش موجود في `endpoints.json`. | العقود | الكود |
| 15 | تعليق في OrderController بيقول إن API-028 (طلبات الإلغاء) هييجي بعدين؛ وهو متنفذ. أما API-095 (تصدير التدقيق) ومقاييس لوحة العمليات (API-066) والسياسات (API-008) فهم في العقد بس مش متنفذين. | تعليق في الكود، العقد | الكود |
| 16 | `packages/design-tokens/package.json` لسه بيقول "draft pending O05" مع إن اتجاه Cairo/terracotta معتمد. | package.json | مستندات التصميم |
| 17 | بلاطات النية ("وجبة ليا/لينا"، "كمّل سفرتي"، "جهّز تجمّع") هي تلميحات واجهة؛ و`intent` عمره ما بيتبعت للسيرفر. مفيش منتج تموين (Catering). | الواجهة | الكود |
| 18 | رسوم التوصيل بتيجي من `service_zone.delivery_fee_minor` مع `kitchen_zone.fee_override_minor` اختياري. | docs | الكود |

| 19 | `contracts/endpoints.json` فيه `"status": "DESIGN_NOT_IMPLEMENTED"` و`planning/backlog.json` فيه `"status": "PLANNED_NOT_IMPLEMENTED"` على مستوى الملف؛ والكود بيخدّم 113 من 116 Route (اللي مش متخدَّمة: API-008، API-066، API-095) ومعظم الـ Stories DONE. | الملفين دول | الكود |
| 20 | `docs/status/latest.md` قديم: أحدث بند 2026-10-14، وجدول الـ Stories بيقول "Flyway V1–V20" (الـ Migrations بتوصل V31)، ومفيهوش D18/MFA، وبيقول "No preview deployed" بينما `docs/quality/owner-acceptance/run-2026-10-19.md` بيسجّل إصدار منشور. | `docs/status/latest.md` | تاريخ git، سجل التشغيل |
| 21 | D18 (MFA) متنفذ لكن ملوش سيناريو TC-nnn ولا Story خاصة في الـ Backlog؛ و D05 لسه بيقول "cash only"؛ و D01–D09 لسه PROPOSED_DEFAULT من غير مراجع موافقة؛ و`SUPERSEDED` عمره ما اتستخدم؛ و`docs/product/01-context-and-decisions.md` سطر 82 بيقول إن D10 مقترح بينما جدوله بيقول ACCEPTED. | `planning/decisions.json` ومستند القرارات | JSON القرارات + الكود |
| 22 | ADR-001 في `docs/engineering/01-architecture.md` بيذكر JPA؛ والـ Backend بيستخدم `JdbcTemplate` ومفيهوش Dependency على JPA. ADR-002 بيقول نشارك مفاتيح الترجمة؛ وفيه تلات ملفات `i18n.ts` منفصلة. | `docs/engineering/01-architecture.md` | الكود |
| 23 | `docs/quality/owner-acceptance/generated/traceability.json` بيبلّغ إن مفيش سيناريوهات غير مغطّاة، لكن 39 بس من 200 حالة للمالك مربوطة بسيناريو تخطيط. | التتبّع المتولّد | cases/*.json |
| 24 | الـ Fakes (SMS، فحص البرمجيات الخبيثة، الإشعارات) مسموحة في LOCAL وCI وPREVIEW (`ProviderConfiguration`، `MatbakhyProperties.DeploymentEnvironment`)؛ وبعض التعليقات بتقول "LOCAL/CI only". | تعليقات الكود، docs | الكود |

## 3. فجوات وظيفية اتشافت أثناء قراءة الكود (مش عيوب توثيق)

مفيش واجهة لفورم طلب الطبّاخ (API بس)؛ ومفيش واجهة لتحقق تبليغ الكاش (API-082/083)؛ وتبويب المالية بيتكشف عن طريق فحص `/ops/finance/statements` ومعاملة 404 على إنه مسموح، فحساب موظف معاه `finance.verify` بس ممكن ميشوفش لوحة المدفوعات (مستخرج من الكود، ماتختبرش)؛ وتبويب الموظفين مابيبعتش `expiresAt`؛ والموبايل مفيهوش شاشة إعدادات MFA ومابينادّيش تسجيل الجهاز (API-033)؛ وطلبات التصفح بحد 50 من غير "تحميل المزيد"؛ واستردادات المنصة كاملة بس؛ ومالقيناش Job بيمسح صفوف `idempotency_record` المنتهية (NOT VERIFIED)؛ و`x-permission` في `contracts/openapi.yaml` عمره ما بيتقارن بـ `@PreAuthorize` الخاص بالـ Controller.

## 4. مناطق التغطية الآلية فيها ضعيفة

- موديولات الـ Backend ملهاش اختبارات معزولة: الـ 80 ملف كلهم اختبارات تكامل في `backend/app/src/test`؛ وإعدادات Spring غير `EnvironmentGuardTest` مش متختبرة.
- مفيش اختبار لحدود المعدّل على مستوى التطبيق (BR-054)، ولا لحالة CLOSED للمطبخ، ولا للاستردادات الجزئية، ولا للحالة الحدّية `ACCEPTANCE_WINDOW_TOO_SHORT` (NOT VERIFIED).
- اختبارات الوحدة للموبايل بتغطي App وauth وتسجيل الدخول والأوردر بس؛ ومفيش تشغيل Native مع تفعيل MFA ولا تشغيل على جهاز فعلي. التدفقات الـ Native بتشتغل في CI بس.
- ويب العمليات: حوالي 17 شاشة بتشترك في `App.test.tsx` واحد زائد Playwright E2E؛ واختبار Playwright لرحلة العميل موجود في `apps/operations-web/e2e` لكنه بيقود ويب العميل؛ وويب العميل مفيهوش E2E خاص بيه.
- Scripts نشر المعاينة مغطّاة ببروفات Python، مش باختبارات على الـ Host الحقيقي؛ واختبارات الـ Proxy بتتخطّى لما صور caddy/python مش موجودة.
- كل حالات قبول المالك الـ 200 NOT_RUN أو BLOCKED ما عدا القليل اللي اتنفّذ في 2026-10-19 (شوف `docs/quality/owner-acceptance/run-2026-10-19.md`).

## 5. اللي NOT VERIFIED في الدليل كله

كل أوامر الـ Build/الاختبار (ماتشغّلتش هنا)؛ وإجراءات Windows/macOS؛ وتصحيح الـ IDE لـ API الموجود في Container؛ وحالة Keycloak Realm الحية (ترحيل MFA، و`matbakhy-mobile` على المعاينة)؛ وقنوات التنبيه (Alertmanager بيبعت لـ Sink محلي بس)؛ والنسخ الاحتياطية خارج الـ Host؛ ومحدّد معدّل عام للـ API (مالقيناش)؛ وعرض Mermaid (مفيش Renderer متاح هنا؛ الصياغة اتفحصت يدويًا واتفحصت تاني في المراجعة).

## 6. التكرار

السلوك البيزنسي موجود في `docs/product/*` و`planning/rules.json` والكود. الدليل بيربط بالملف المرجعي ([خريطة التوثيق](documentation-map-ar.md)) بدل ما ينسخ، عشان نتجنب انجراف جديد.
