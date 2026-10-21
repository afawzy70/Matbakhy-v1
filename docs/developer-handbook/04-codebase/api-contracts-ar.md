# عقود الـ API

> **Owner role:** Backend architecture
> **Status:** IMPLEMENTED — أدوات العقود والـ Registry واختبارات الانحراف (Drift) موجودة؛ 113 من 116 Route مسجّلة شغّالة، والباقي 3 متصمّمة بس
> **Language / اللغة:** [English](api-contracts.md) · العربية

REST API واحد (`/api/v1/...`)، متوصّف مرتين عن قصد: مستند OpenAPI بأنواع بيانات محددة (Typed) وسجل Routes مسطّح (Flat). الاتنين موجودين في
`contracts/`. التطبيقات التلاتة بتستهلك Client متولّد من ملف الـ OpenAPI. الخلفية: `docs/engineering/03-api-contract.md`
(للسياق بس؛ الصفحة دي بتوصف اللي الكود والاختبارات بيفرضوه).

## الملفات

| الملف | الدور |
|---|---|
| `contracts/openapi.yaml` | مستند OpenAPI 3.1.1: المسارات (Paths) والـ Schemas والبارامترات المشتركة (`Cursor` و`Limit` و`IdempotencyKey`) واستجابة `Error` القياسية، وسكيمات الأمان `sessionCookie` (الكوكي `MATBAKHY_SESSION`) و`bearerAuth` (JWT). كل عملية (Operation) فيها `x-matbakhy-id` و`x-permission` و`x-implemented` |
| `contracts/endpoints.json` | الـ Registry: من `API-001` لحد `API-116` ومعاها `method` و`path` و`permission` و`response_type` وسطر واحد `contract` و`implemented` |
| `packages/api-client/src/generated/schema.ts` | **متولّد** بـ `openapi-typescript` من ملف الـ YAML؛ متعمله Commit؛ ماتعدّلهوش بإيدك أبدًا |
| `packages/api-client/src/index.ts` | `createApiClient(options)` — Client من نوع `openapi-fetch` متكتّب بـ `paths` المتولّدة |
| `packages/api-client/redocly.yaml` | قواعد الـ Lint (`recommended-strict` و`operation-summary` و`security-defined` و`no-unused-components` كأخطاء) |

## إيه اللي بيفشل لما يختلفوا

| الفحص | الأمر أو الاختبار | بيفشل لما |
|---|---|---|
| الـ Registry مقابل OpenAPI | `packages/api-client/test/registry.test.ts` (`pnpm --filter @matbakhy/api-client test`) | عملية تكون ناقصة أو زيادة؛ أو `x-matbakhy-id` أو `x-permission` أو `x-implemented` يختلفوا عن الـ Registry؛ أو عملية عامة (Public) عليها أمان أو عملية محمية مفيهاش أمان Session/Bearer؛ أو عملية مفيهاش استجابة الخطأ القياسية `4XX`/`5XX`؛ أو Schema لـ Object مكتوب بأنواع تسمح بخصائص إضافية؛ أو الـ Enum بتاع `ApiError.code` يختلف عن `ErrorCode.java` |
| الـ Registry مقابل التطبيق الشغّال | `backend/app/src/test/java/com/matbakhy/app/web/ContractRoutesTest.java` | مجموعة عناصر الـ Registry اللي فيها `implemented: true` تختلف عن Routes الـ `/api/` اللي Spring بيعمل لها Mapping فعلًا (بعد توحيد شكل متغيرات المسار) |
| حداثة الـ Client المتولّد | `pnpm --filter @matbakhy/api-client run check:drift` (`packages/api-client/scripts/check-drift.mjs`) | `src/generated/schema.ts` يختلف عن توليد جديد |
| الـ Lint بتاع OpenAPI | `pnpm --filter @matbakhy/api-client run lint:contract` | أخطاء في قواعد Redocly |
| آخر اتنين مع بعض | `pnpm contract:check` (الـ `package.json` في الجذر؛ بيناديه `scripts/check_workspace.sh`) | أي واحد فيهم يفشل |
| أشكال الاستجابات | `packages/api-client/test/contract.ts` بيعمل Compile لكل Schema بـ Ajv للاختبارات | Schema ماينفعش يتعمل له Compile |

اللي مش بيتفحص آليًا (شوف الفجوات): إن `x-permission` يطابق الـ `@PreAuthorize` على الـ Controller، وإن جسم الاستجابة
اللي الـ Backend بيطلّعه يعدّي التحقق مقابل الـ Schema بتاعه.

## الاتفاقيات اللي كل Endpoint بيمشي عليها

- الأخطاء: `ApiError` بصيغة JSON فيه `code` ثابت من `backend/shared/src/main/java/com/matbakhy/shared/error/ErrorCode.java`
  (`MALFORMED_REQUEST` و`UNAUTHENTICATED` و`FORBIDDEN` و`STEP_UP_REQUIRED` و`NOT_FOUND` و`VALIDATION_FAILED` و`STATE_CONFLICT` و
  `VERSION_CONFLICT` و`IDEMPOTENCY_CONFLICT` و`STOCK_CHANGED` و`PRICE_CHANGED` و`QUOTE_EXPIRED` و`SLOT_CLOSED` و...)، وحالة الـ HTTP
  ثابتة لكل كود، والـ Header اسمه `X-Request-Id` بيربط الخطأ باللوجز. الـ Handler: `backend/app/src/main/java/com/matbakhy/app/web/ApiExceptionHandler.java`.
- JSON صارم (Strict): الخصائص المجهولة بتتّرفض (`backend/app/src/main/java/com/matbakhy/app/config/StrictJsonConfiguration.java` و`StrictJsonIntegrationTest`).
- الفلوس: `{ "amountMinor": <integer piastres>, "currency": "EGP" }`؛ بيتعمل لها Parse عند الـ Client بـ `packages/api-client/src/money.ts`.
- الأوامر الـ Idempotent بتاخد Header اسمه `Idempotency-Key` (من 8 لـ 128 حرف من `[A-Za-z0-9-]`)؛ والتحكم المتفائل في التزامن (Optimistic Concurrency) بيستخدم `expectedVersion` في
  الجسم وبيرد بـ `VERSION_CONFLICT`.
- القوائم بتستخدم `cursor` قيمته مش مخصّصة للتفسير عند الـ Client (Opaque) + `limit` (من 1 لـ 100، والافتراضي 20).
- اللغة: `Accept-Language` بيختار المحتوى بالعربي (الافتراضي) أو الإنجليزي.
- الـ Authentication: الويب = كوكي Session + `X-XSRF-TOKEN` في العمليات اللي بتعدّل؛ الموبايل (Native) = `Authorization: Bearer`. الـ Routes العامة هي قائمة الـ `GET`
  في `SecurityConfiguration.PUBLIC_GET` زائد `POST /api/v1/activity`.

## مفردات الصلاحيات في العقد

قيم `x-permission` بتوصف قاعدة الوصول، مثلًا `PUBLIC` و`SELF` و`CUSTOMER` و`ORDER_OWNER` و`COOK` و`COOK_OWNER` و
`COOK_APPLICANT` و`CASE_OWNER`، ونطاقات الموظفين `COOK_REVIEW` و`CONTENT_MODERATE` و`ORDER_OPS` و`SUPPORT` و`FINANCE` و
`FINANCE_VERIFY` و`FINANCE_REFUND` و`FINANCE_SETTLEMENT` و`CONFIG_READ` و`CONFIG_WRITE_SCOPED` و`STAFF_GRANT` و
`AUDIT_READ_SCOPED` و`INTEGRATION_OPS` و`SECURITY_MFA_POLICY` و`SECURITY_MFA_RESET`. نطاقات الموظفين بتتحوّل لـ Authorities اسمها
`PERM_<code>` (الـ Enum اسمه `Permission.java`، مثلًا `PERM_finance.verify`). نطاقات المالك (`ORDER_OWNER` و`COOK_OWNER`) بتتفرض في
الـ SQL بتاع الـ Service، مش بالـ Annotation.

## Routes مش متنفّذة

| الـ Id | الـ Route | حالة الـ Registry |
|---|---|---|
| API-008 | `GET /api/v1/policies/{key}` | `implemented: false` (المسار متحط في القائمة المسموحة كعام في `SecurityConfiguration` بس مفيش Controller بيخدمه) |
| API-066 | `GET /api/v1/ops/dashboard` | `implemented: false` |
| API-095 | `GET /api/v1/ops/audit/export` | `implemented: false` |

الـ Schemas المعلّمة `x-schema-status: PENDING` (`OpsDashboard` و`PolicyDocument` وواحدة كمان) هي Placeholders من غير أنواع، ومملوكة للـ Stories دي.

## مسار العمل: إضافة أو تغيير Endpoint

1. اكتب اختبار التكامل (Integration Test) اللي بيفشل الأول في `backend/app/src/test/java/com/matbakhy/app/<area>/`.
2. نفّذ الـ Controller (`web/`) والـ Service (`internal/`) و، لو لازم، Migration (`V32__...sql`).
3. ضيف أو عدّل العملية في `contracts/openapi.yaml` (الـ summary و`x-matbakhy-id` و`x-permission` و`x-implemented: true` و`4XX/5XX` القياسية).
4. ضيف أو عدّل العنصر في `contracts/endpoints.json` بنفس الـ id والـ permission والـ `implemented`.
5. ولّد من جديد: `pnpm --filter @matbakhy/api-client run generate`، واعمل Commit لـ `packages/api-client/src/generated/schema.ts`.
6. شغّل `pnpm contract:check` و`pnpm --filter @matbakhy/api-client test` و`ContractRoutesTest` بتاع الـ Backend.
7. حدّث الدليل حسب [سياسة التوثيق](../05-developer-workflow/documentation-policy-ar.md).

Routes الـ OIDC (`/oauth2/authorization/**` و`/login/oauth2/code/**` و`/api/v1/auth/logout`) بيديرها الـ Framework و**مش**
موجودة في الـ Registry (ده مكتوب في الـ `note` بتاع الـ Registry نفسه).

## القيود المعروفة وفجوات التوثيق

- `contracts/endpoints.json` لسه مكتوب فيه `"status": "DESIGN_NOT_IMPLEMENTED"` مع إن 113 Route شغّالة؛ فالحالة على مستوى الملف قديمة.
- `x-permission` للتوثيق بس؛ لو القيمة غلط مفيش اختبار هيفشل. صحة الـ Authorization بتتغطّى باختبارات التكامل (مثلًا `StaffAccessIntegrationTest`)، مش بالعقد.
- وصف الـ OpenAPI بيقول إن الملف "generated from contracts/endpoints.json in MB-007 and maintained by hand since"؛ الملفين دلوقتي متزامنين بالاختبارات، مش بـ Generator.
