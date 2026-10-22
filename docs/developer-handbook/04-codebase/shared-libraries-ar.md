# المكتبات المشتركة

> **Owner role:** Frontend and mobile
> **Status:** IMPLEMENTED — حزمتين Workspace وموديول Backend مشترك واحد مستخدمين في كل التطبيقات
> **Language / اللغة:** [English](shared-libraries.md) · العربية

كود بيتعاد استخدامه بين التطبيقات. كل حاجة هنا `private` (مبتتنشرش أبدًا على Registry) وبتتستهلك بالاعتماديات `workspace:*`
(الملف `pnpm-workspace.yaml` فيه `apps/*` و `packages/*`).

## `packages/api-client` — `@matbakhy/api-client`

| البند | التفصيل |
|---|---|
| الغرض | Client واحد بأنواع محددة (Typed) لعقد الـ API، بيستخدمه `apps/customer-web` و `apps/operations-web` و `apps/customer-mobile` |
| نقطة الدخول | `packages/api-client/src/index.ts`: `createApiClient(options)` (بيغلّف `openapi-fetch`)، وإعادة تصدير الأنواع (`components` و `operations` و `paths` و `ApiError`)، و `parseMoney` و `MalformedMoneyError` و `priceChanges` |
| الكود المتولّد | `packages/api-client/src/generated/schema.ts` من `contracts/openapi.yaml` (`pnpm --filter @matbakhy/api-client run generate`)؛ والحداثة بيفحصها `check:drift` |
| مساعد الفلوس | `packages/api-client/src/money.ts` — `parseMoney` بتقبل بالظبط `{amountMinor, currency}` بمبلغ Safe Integer وعملة `EGP`؛ وبترفض الكسور العشرية والنصوص الرقمية والحقول الزيادة |
| مساعد تغيّر السعر | `packages/api-client/src/priceChanges.ts` — بيقارن بين عرضين للسعر لإشعار إعادة التأكيد D13 ([تنفيذ التسعير](pricing-implementation-ar.md)) |
| الاختبارات | `packages/api-client/test/client.test.ts` (المسارات ذات الأنواع المحددة (Typed paths) بتتعمل Compile/بتفشل) و `money.test.ts` (TC-107) و `priceChanges.test.ts` و `registry.test.ts` (العقد مقابل الـ Registry مقابل `ErrorCode`) |
| الـ Authentication | مش في الحزمة دي: كل تطبيق بيبني إعدادات الـ fetch بتاعته (كوكي + CSRF للويب، و Bearer للموبايل) وبيمرّرها كـ `ClientOptions` |

قواعد التغيير: ماتعدّلش `schema.ts` بإيدك؛ غيّر `contracts/openapi.yaml` وولّد من جديد واعمل Commit للاتنين. شوف [عقود الـ API](api-contracts-ar.md).

## `packages/design-tokens` — `@matbakhy/design-tokens`

| البند | التفصيل |
|---|---|
| الغرض | اسم البراند/الشعار والألوان (الفاتح ومسودة داكنة مشتقة) والخطوط (Cairo) والمسافات في مصدر JSON واحد؛ ومساعد الاتجاه |
| مصدر الحقيقة | `packages/design-tokens/tokens.json` (الحالة `DRAFT_PENDING_OWNER_APPROVAL`؛ قرار المالك O05 بخصوص الموافقة النهائية على البراند لسه مفتوح) |
| الصادرات (Exports) | `packages/design-tokens/src/index.ts`: `brand` و `tokens` و `direction(locale)` و `contrastRatio` و `luminance` و `renderCss` و `wordmarkSvg`؛ وملف الـ CSS اسمه `@matbakhy/design-tokens/tokens.css` بيتولّد في `packages/design-tokens/src/generated/tokens.css` بالأمر `pnpm --filter @matbakhy/design-tokens run generate` (الملف المتولّد متعمله Commit) |
| المستهلكين | `main.tsx` في الويب بيستورد `@matbakhy/design-tokens/tokens.css`؛ والموبايل بيقرا قيم التوكنز في `apps/customer-mobile/src/ui.tsx` |
| الاختبارات | `packages/design-tokens/test/tokens.test.ts` (TC-103: أزواج التباين (Contrast) بتحقق نسب WCAG في الثيمين، وملف `tokens.css` المتعمله Commit بيساوي Render جديد، وهدف اللمس 44px، والتطبيقات مبتكتبش اسم البراند ولا ألوان hex بشكل ثابت) |
| البوابة (Gate) | `scripts/check_design.sh` (اختبارات التوكنز وعينات البراند ورحلات النموذج الأولي Arabic/English؛ والموافقة البصرية نفسها بتتسجّل منفصلة في `docs/design/approval.md`) |
| القواعد البصرية | `docs/design/approved/DESIGN.md` (اتجاه terracotta + Cairo اللي المالك اختاره في 2026-10-11) |

## موديول `shared` في الـ Backend

`backend/shared` (الـ Maven Artifact اسمه `matbakhy-shared`) فيه الكود الوحيد اللي أي موديول Backend يقدر يستخدمه من غير Package اسمه `api`:
`Money` و `ApiException`/`ErrorCode` و `IdempotentCommands` و `PersonalDataEraser` و `RuntimeEnvironment` و `RequestContext` و `Languages` و `DomainModule`.
التفاصيل: [دليل موديولات الـ Backend](backend-module-guide-ar.md). ممنوع يعتمد على أي موديول Domain ولا على `app` (القاعدة `sharedDependsOnNoModuleOrApp` في `ModuleRules.java`).

## المشاركة بين الويب والموبايل: إيه المشترك وإيه مش مشترك

| المشترك | مش مشترك |
|---|---|
| أنواع الـ API والـ Client، وتحليل الفلوس (Parsing)، ومقارنة تغيّر السعر، وتوكنز البراند/التصميم | مكونات الواجهة (الويب بيستخدم React بتاع الـ DOM، والموبايل بيستخدم React Native)، وجداول الترجمة (كل تطبيق عنده `i18n.ts` بتاعه)، والتعامل مع الـ Session (كوكي مقابل توكنز في المخزن الآمن) |

الـ ADR-002 (`docs/engineering/01-architecture.md`) بيقول إن الويب والـ Native المفروض يشتركوا في "validation primitives and translation keys"؛ في الكود اللي اتقرا لأجل الصفحة دي مفاتيح الترجمة **مش** مشتركة (تلات ملفات `i18n.ts` منفصلة).

## القيود المعروفة وفجوات التوثيق

- مفاتيح الترجمة متكررة في كل تطبيق، وده بيعارض نية الـ ADR-002؛ ومفيش حزمة i18n مشتركة.
