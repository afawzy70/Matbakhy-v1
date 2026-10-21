# دليل المطوّر لمطبخي (Matbakhy)

> **Owner role:** Handbook navigation
> **Status:** REFERENCE — نقطة الدخول الوحيدة للمطوّرين؛ حالة التنفيذ موسومة في كل صفحة
> **Language / اللغة:** [English](README.md) · العربية

أهلًا بيك. الدليل ده بيخلّي أي مطوّر جديد يفهم مطبخي (Matbakhy) — البيزنس، والتطبيقات، والمعمارية، والكود، والتشغيل، ومسار المساهمة — **من الـ Repository لوحده**، من غير مساعد ذكاء اصطناعي ولا معرفة غير موثّقة. فيه نسخة كاملة بالعربي المصري لكل صفحة: ابدأ من [README-ar.md](README-ar.md).

**قواعد الدليل ده**

- الكود والـ Migrations والعقود والاختبارات هي مصدر الحقيقة لحالة التنفيذ؛ والدليل بيشرحها وبيربط بيها. كل صفحة بتقول مين مسؤول عنها وحالة تنفيذها (IMPLEMENTED، PARTIALLY IMPLEMENTED، PLANNED، DISABLED، DEPRECATED، NOT VERIFIED؛ وREFERENCE لصفحات العمليات).
- الدليل بيتحدّث باستمرار مع تطور المشروع تحت **[DOC-001](05-developer-workflow/documentation-policy-ar.md)**: كل تغيير لازم يحدّث الصفحات المتأثرة في نفس الـ PR، والفحص المطلوب `Documentation Consistency / DOC-001` بيتحقق من البنية والمراجع. لقيت غلطة؟ صلّح الصفحة الأساسية وسجّلها ([الصيانة](05-developer-workflow/documentation-maintenance-ar.md)).
- الـ Repository عام: ماتضيفش أبدًا Credentials أو بيانات عملاء أو Logs خاصة.
- **لغتين، دليل واحد.** كل صفحة إنجليزي `X.md` ليها نسخة مطابقة بالعربي المصري `X-ar.md` في نفس المجلد. الإنجليزي هو المرجع، والملفين بيتغيروا مع بعض في نفس الـ PR، وفيه فحص CI (`scripts/validate_handbook_parity.py`) بيفشل لو النسخة العربية ناقصة أو بنيتها مختلفة. التفاصيل: [صيانة التوثيق](05-developer-workflow/documentation-maintenance-ar.md)؛ والتدقيق الحالي: [تقرير تطابق الترجمة](translation-parity-report.md).

## ابدأ من هنا (أول يوم)

1. [نظرة عامة على المشروع](00-getting-started/project-overview-ar.md) → [المصطلحات](00-getting-started/glossary-ar.md) → [خريطة التعلّم](00-getting-started/learning-roadmap-ar.md).
2. اتبع [التدريب العملي في خمسة أيام](#التدريب-العملي-labs) وخلّص بـ [التقييم النهائي](06-onboarding-labs/final-assessment-ar.md).
3. قبل أول PR ليك اقرا [سياسة DOC-001](05-developer-workflow/documentation-policy-ar.md) و[مسار Git والـ PR](05-developer-workflow/git-and-pr-workflow-ar.md).

## المحتويات

### 00 البداية
[نظرة عامة على المشروع](00-getting-started/project-overview-ar.md) · [خريطة التعلّم](00-getting-started/learning-roadmap-ar.md) · [التنقّل في الـ Repository](00-getting-started/repository-navigation-ar.md) · [المصطلحات](00-getting-started/glossary-ar.md)

### 01 البيزنس
[رؤية المنتج](01-business/product-vision-ar.md) · [المستخدمين المستهدفين](01-business/target-users-ar.md) · [نموذج الـ Marketplace](01-business/marketplace-model-ar.md) · [الأطراف والصلاحيات](01-business/actors-and-permissions-ar.md) · [دورة حياة الأوردر](01-business/order-lifecycle-ar.md) · [التسعير والدفع](01-business/pricing-and-payments-ar.md) · [إدارة المطبخ](01-business/kitchen-management-ar.md) · [قواعد العمل](01-business/business-rules-ar.md)

### 02 رحلات المستخدم
[الويب بتاع العميل](02-user-journeys/customer-web-ar.md) · [موبايل العميل](02-user-journeys/customer-mobile-ar.md) · [عمليات الطبّاخ](02-user-journeys/cook-operations-ar.md) · [الـ Back-office](02-user-journeys/backoffice-admin-ar.md) · [المالية والدعم](02-user-journeys/finance-and-support-ar.md) · [الـ Authentication والـ MFA](02-user-journeys/authentication-and-mfa-ar.md) · [رحلة الأوردر الكاملة](02-user-journeys/complete-order-journey-ar.md)

### 03 المعمارية
[نظرة عامة على السيستم](03-architecture/system-overview-ar.md) · [الـ Backend](03-architecture/backend-architecture-ar.md) · [الـ Frontend](03-architecture/frontend-architecture-ar.md) · [الموبايل](03-architecture/mobile-architecture-ar.md) · [قاعدة البيانات](03-architecture/database-architecture-ar.md) · [الهوية والأمان](03-architecture/identity-and-security-ar.md) · [التكامل والأحداث](03-architecture/integration-and-events-ar.md) · [البنية التحتية والـ Deployment](03-architecture/infrastructure-and-deployment-ar.md) · [**الرسومات (Mermaid)**](03-architecture/diagrams-ar.md)

### 04 الكود
[خريطة الـ Repository](04-codebase/repository-map-ar.md) · [دليل موديولات الـ Backend](04-codebase/backend-module-guide-ar.md) · [دليل كود الـ Frontend](04-codebase/frontend-code-guide-ar.md) · [دليل كود الموبايل](04-codebase/mobile-code-guide-ar.md) · [عقود الـ API](04-codebase/api-contracts-ar.md) · [تنفيذ الأوردر](04-codebase/order-implementation-ar.md) · [تنفيذ التسعير](04-codebase/pricing-implementation-ar.md) · [تنفيذ الدفع](04-codebase/payment-implementation-ar.md) · [المكتبات المشتركة](04-codebase/shared-libraries-ar.md)

### 05 مسار عمل المطوّر
[المتطلبات المسبقة](05-developer-workflow/prerequisites-ar.md) · [البيئة المحلية](05-developer-workflow/local-environment-ar.md) · [التشغيل والـ Debug](05-developer-workflow/run-and-debug-ar.md) · [تشغيل الاختبارات](05-developer-workflow/test-execution-ar.md) · [مسار Git والـ PR](05-developer-workflow/git-and-pr-workflow-ar.md) · [CI/CD](05-developer-workflow/ci-cd-ar.md) · [بيئة المعاينة](05-developer-workflow/preview-environment-ar.md) · [حل المشاكل](05-developer-workflow/troubleshooting-ar.md) · [**سياسة التوثيق DOC-001**](05-developer-workflow/documentation-policy-ar.md) · [صيانة الفروع](05-developer-workflow/branch-maintenance-ar.md) · [صيانة التوثيق](05-developer-workflow/documentation-maintenance-ar.md)

### 08 البنية التحتية
[جرد سيرفر Contabo](08-infrastructure/contabo-server-inventory-ar.md) · [التحقق للقراءة فقط للأدمن](08-infrastructure/admin-readonly-verification-ar.md) · [دليل إعادة بناء السيرفر](08-infrastructure/server-rebuild-guide-ar.md) · [مسار الـ Deployment](08-infrastructure/deployment-pipeline-ar.md) · [الـ DNS والـ HTTPS وsslip.io](08-infrastructure/dns-https-sslip-io-ar.md) · [معمارية التشغيل](08-infrastructure/runtime-architecture-ar.md) · [النسخ الاحتياطي والاسترجاع والتعافي من الكوارث](08-infrastructure/backup-restore-disaster-recovery-ar.md) · [حل مشاكل البنية التحتية](08-infrastructure/infrastructure-troubleshooting-ar.md) · [الوصول لبيئة المعاينة الحية والرحلات](05-developer-workflow/live-preview-access-ar.md)

### التدريب العملي (Labs)
[اليوم 1 البيزنس](06-onboarding-labs/day-1-business-ar.md) · [اليوم 2 التطبيقات](06-onboarding-labs/day-2-applications-ar.md) · [اليوم 3 الـ Backend وقاعدة البيانات والأمان](06-onboarding-labs/day-3-backend-security-ar.md) · [اليوم 4 مسار التطوير](06-onboarding-labs/day-4-development-ar.md) · [اليوم 5 أول مساهمة](06-onboarding-labs/day-5-first-contribution-ar.md) · [التقييم النهائي](06-onboarding-labs/final-assessment-ar.md) · مسار البنية التحتية: [اليوم 1 الـ Topology والحسابات](06-onboarding-labs/infra-day-1-topology-accounts-ar.md) · [اليوم 2 خدمات Docker](06-onboarding-labs/infra-day-2-docker-services-ar.md) · [اليوم 3 الـ DNS والـ HTTPS والـ Keycloak](06-onboarding-labs/infra-day-3-dns-https-keycloak-ar.md) · [اليوم 4 الـ CI/CD والتشغيل](06-onboarding-labs/infra-day-4-cicd-operations-ar.md) · [اليوم 5 إعادة البناء والاسترجاع](06-onboarding-labs/infra-day-5-rebuild-recovery-ar.md)

### 07 المراجع
[قرارات المعمارية](07-reference/architecture-decisions-ar.md) · [حالة التنفيذ](07-reference/implementation-status-ar.md) · [تتبّع المتطلبات](07-reference/requirements-traceability-ar.md) · [خريطة التوثيق](07-reference/documentation-map-ar.md) · [الأسئلة الشائعة](07-reference/frequently-asked-questions-ar.md) · [الجرد وتقييم الفجوات](07-reference/inventory-and-gaps-ar.md) · [محاكاة المطوّر الجديد](07-reference/fresh-developer-simulation-ar.md) · [سجل المراجعات المستقلة](07-reference/independent-reviews-ar.md) · [تدقيق تنظيف الفروع](07-reference/branch-cleanup-audit-2026-10-20-ar.md) · [تغطية توثيق البنية التحتية والانحراف](07-reference/infrastructure-documentation-coverage-ar.md)

ملفات قابلة للقراءة آليًا: [doc001-matrix.json](doc001-matrix.json) (أي مناطق كود محتاجة أي توثيق)، و[translation-sync.json](translation-sync.json) (أزواج الصفحات الإنجليزي والعربي اللي اتراجعت)، و[translation-exceptions.json](translation-exceptions.json) (استثناءات التطابق الموثّقة).

## إجابات سريعة

| السؤال | روح فين |
|---|---|
| إيه هو المنتج ومين بيستخدمه؟ | [نظرة عامة على المشروع](00-getting-started/project-overview-ar.md)، [الأطراف](01-business/actors-and-permissions-ar.md) |
| فين X في الكود؟ | [التنقّل في الـ Repository](00-getting-started/repository-navigation-ar.md) |
| الأوردر بيمشي إزاي من أوله لآخره؟ | [رحلة الأوردر الكاملة](02-user-journeys/complete-order-journey-ar.md)، [دورة حياة الأوردر](01-business/order-lifecycle-ar.md) |
| أشغّله وأختبره إزاي؟ | [المتطلبات المسبقة](05-developer-workflow/prerequisites-ar.md)، [التشغيل والـ Debug](05-developer-workflow/run-and-debug-ar.md)، [الاختبارات](05-developer-workflow/test-execution-ar.md) |
| الـ PR بيوصل لبيئة المعاينة إزاي؟ | [CI/CD](05-developer-workflow/ci-cd-ar.md)، [المعاينة](05-developer-workflow/preview-environment-ar.md) |
| أنهي حالات الـ E2E بتاعة المالك بتغطي الـ Feature بتاعتي؟ | [تتبّع المتطلبات](07-reference/requirements-traceability-ar.md)، `docs/quality/owner-acceptance/README.md` |
| الـ PR بتاعي لازم يقول إيه عن التوثيق؟ | [سياسة DOC-001](05-developer-workflow/documentation-policy-ar.md) |
