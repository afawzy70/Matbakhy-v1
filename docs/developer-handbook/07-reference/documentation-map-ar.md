# خريطة التوثيق والمصادر المرجعية

> **Owner role:** Handbook navigation
> **Status:** REFERENCE — أنهي مستند هو المرجع في إيه، وعلاقة الدليل بالمستندات الأقدم
> **Language / اللغة:** [English](documentation-map.md) · العربية

الدليل بيشرح وبيربط؛ لكنه مش بديل للمستندات المرجعية تحت. لما مستندين يختلفوا، ترتيب الثقة هو: **الكود الشغّال والـ Migrations والعقود والاختبارات** (حقيقة التنفيذ) → سجل قرارات المالك (`planning/decisions.json`، `docs/product/01-context-and-decisions.md`، `docs/delivery/07-owner-decisions-implementation-sequence.md`) للنية → المستندات الموضوعية → تقارير الحالة (لقطات تاريخية).

| الموضوع | المصدر المرجعي | صفحة الدليل اللي بتشرحه |
|---|---|---|
| قرارات المالك D01.. | `planning/decisions.json`، `docs/product/01-context-and-decisions.md`، `docs/delivery/07-owner-decisions-implementation-sequence.md` | [القرارات المعمارية](architecture-decisions-ar.md) |
| قواعد العمل BR-nnn | `planning/rules.json`، `docs/product/03-business-rules.md` | [قواعد العمل](../01-business/business-rules-ar.md) |
| الـ State Machines | `docs/product/04-state-machines.md` (النية)، `backend/modules/orders` (التنفيذ) | [دورة حياة الأوردر](../01-business/order-lifecycle-ar.md) |
| الأدوار والصلاحيات | `backend/modules/identity` (`Permission.java`)، `docs/product/06-permissions.md` (النية) | [الأطراف والصلاحيات](../01-business/actors-and-permissions-ar.md) |
| العربونات | `docs/product/09-deposit-payment-states.md`، `docs/strategy/mvp-direct-deposit-payment.md` | [التسعير والدفع](../01-business/pricing-and-payments-ar.md)، [تنفيذ الدفع](../04-codebase/payment-implementation-ar.md) |
| الـ Quote القديم / تغيّر السعر | `docs/product/rq-004-stale-quote.md` | [تنفيذ التسعير](../04-codebase/pricing-implementation-ar.md) |
| عقد الـ API | `contracts/openapi.yaml`، `contracts/endpoints.json` | [عقود الـ API](../04-codebase/api-contracts-ar.md) |
| نموذج البيانات | `backend/app/src/main/resources/db/migration`، `docs/engineering/02-data-model.md` | [معمارية قاعدة البيانات](../03-architecture/database-architecture-ar.md) |
| الأمان والـ MFA | `docs/security/mfa-policy.md`، `docs/engineering/04-security.md`، `docs/security/csp-rollout.md` | [الهوية والأمان](../03-architecture/identity-and-security-ar.md) |
| لوحة إدارة Keycloak | `docs/operations/14-keycloak-admin-console.md` | [الـ Authentication والـ MFA](../02-user-journeys/authentication-and-mfa-ar.md) |
| عمليات المعاينة | `docs/operations/06-web-preview.md`، `docs/operations/11-staging-deployment-and-rollback.md`، `deploy/web-preview/` | [بيئة المعاينة](../05-developer-workflow/preview-environment-ar.md) |
| اختبار قبول المالك | `docs/quality/owner-acceptance/` | [تشغيل الاختبارات](../05-developer-workflow/test-execution-ar.md)، [تتبّع المتطلبات](requirements-traceability-ar.md) |
| استراتيجية التحقق والـ Gates | `docs/quality/01-verification.md`، `docs/quality/02-release-gates.md`، `planning/gates.json` | [تشغيل الاختبارات](../05-developer-workflow/test-execution-ar.md) |
| إصدارات الـ Toolchain | `docs/engineering/toolchain.md`، `package.json`، `backend/pom.xml` | [المتطلبات المسبقة](../05-developer-workflow/prerequisites-ar.md) |
| نظام التصميم | `docs/design/approved/DESIGN.md` | [معمارية الـ Frontend](../03-architecture/frontend-architecture-ar.md) |
| بروتوكول التسليم، تقارير التقدم | `docs/delivery/`، `docs/status/` | [مسار Git والـ PR](../05-developer-workflow/git-and-pr-workflow-ar.md) |
| سياسة التوثيق DOC-001 | [سياسة التوثيق](../05-developer-workflow/documentation-policy-ar.md) | — |

## مستندات تاريخية أو لحظية (ماتعتبرهاش حالية)

`docs/status/latest.md` و`docs/status/daily/*` (التقارير)، و`docs/quality/0*-qa-*.md` (مستندات تسليم النسخ المرشّحة لاختبارات الـ QA)، و`docs/quality/stabilization/` (أدلة التجميع)، و`verification/runs/` (أدلة تشغيلات بعينها). بتوصف اللي كان صح عند Commit معيّن؛ استخدم [حالة التنفيذ](implementation-status-ar.md) للصورة الحالية.

## التكرار المعروف

منطق البيزنس متوصّف في `docs/product/*` (النية) و`planning/rules.json` (مقروء آليًا) والكود. الدليل بيربط عمدًا بدل ما ينسخ؛ شوف [الجرد والفجوات](inventory-and-gaps-ar.md) للتناقضات اللي اتلاقت بينهم.
