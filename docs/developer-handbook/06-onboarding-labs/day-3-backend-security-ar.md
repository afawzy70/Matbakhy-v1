# اليوم 3 - الـ Backend وقاعدة البيانات والأمان

> **Owner role:** Backend architecture
> **Status:** REFERENCE — Lab لتتبّع Request جوّه الـ Backend وفهم الـ Authorization وقواعد البيانات.
> **Language / اللغة:** [English](day-3-backend-security.md) · العربية

## الأهداف

- اتنقّل في الـ Modular Monolith: `backend/app` و`backend/shared` و`backend/modules/*`.
- تتبّع Request واحد من الـ Controller للـ Database والرجوع.
- افهم الـ Authentication (OIDC عن طريق Keycloak) والـ Authorization على مستوى الـ Object والـ Idempotency والـ Snapshots غير القابلة للتعديل.

## قايمة القراءة

1. [نظرة عامة على السيستم](../03-architecture/system-overview-ar.md)، [معمارية الـ Backend](../03-architecture/backend-architecture-ar.md)، [معمارية قاعدة البيانات](../03-architecture/database-architecture-ar.md)، [الهوية والأمان](../03-architecture/identity-and-security-ar.md)، [التكامل والأحداث](../03-architecture/integration-and-events-ar.md).
2. [دليل موديولات الـ Backend](../04-codebase/backend-module-guide-ar.md)، [تنفيذ الأوردر](../04-codebase/order-implementation-ar.md)، [تنفيذ التسعير](../04-codebase/pricing-implementation-ar.md)، [تنفيذ الدفع](../04-codebase/payment-implementation-ar.md)، [عقود الـ API](../04-codebase/api-contracts-ar.md).
3. [الـ Authentication والـ MFA](../02-user-journeys/authentication-and-mfa-ar.md)؛ والمعتمد: [سياسة الأمان](../../engineering/04-security.md)، [سياسة الـ MFA](../../security/mfa-policy.md).

## مهام عملية

1. خريطة الموديولات. اعمل قايمة بـ `backend/modules`. وبالنسبة لـ `orders`، افتح الحزم التلاتة: `web` (الـ Controllers زي `backend/modules/orders/src/main/java/com/matbakhy/orders/web/OrderController.java`) و`api` (الحزمة الوحيدة اللي مسموح للموديولات التانية تستخدمها) و`internal` (زي `backend/modules/orders/src/main/java/com/matbakhy/orders/internal/OrderService.java`). وبعدين اقرا `backend/app/src/test/java/com/matbakhy/app/architecture/ModuleBoundaryTest.java` واشرح القواعد اللي بيفرضها.
2. تتبّع طلب عرض السعر. دوّر على العملية في `contracts/openapi.yaml` (دوّر على `quote`)، وبعدين الـ Controller `backend/modules/orders/src/main/java/com/matbakhy/orders/web/QuoteController.java`، وبعدين `backend/modules/orders/src/main/java/com/matbakhy/orders/internal/QuoteService.java`. اكتب سلسلة النداءات، والجداول اللي بتتقري (اتبع الـ SQL أو نداءات الـ Repository)، وفين بتتفرض القاعدة BR-020 (عرض السعر بيفضل 5 دقايق ومابيحجزش مخزون).
3. تتبّع تسجيل الأوردر بنفس الطريقة من خلال `OrderController` و`OrderService`. دوّر على المكان اللي بيتفحص فيه الـ Idempotency (ابدأ من `backend/app/src/main/java/com/matbakhy/app/idempotency`)، والمكان اللي بتتحجز فيه السعة بشكل Transactional، والمكان اللي بيتكتب فيه الـ Snapshot غير القابل للتعديل.
4. الـ Authorization. اقرا `backend/app/src/main/java/com/matbakhy/app/security` واختبار زي اللي تحت `backend/app/src/test/java/com/matbakhy/app/security`. جاوب: إزاي الكود بيمنع العميل B من إنه يقرا أوردر العميل A؟ أنهي اختبار بيثبت ده؟ (شغّله بـ `cd backend && ./mvnw -B -ntp verify -Dtest=<TestClass> -Dsurefire.failIfNoSpecifiedTests=false`؛ ولازم Docker يكون شغّال.)
5. قاعدة البيانات. في psql (شوف [التشغيل والـ Debug](../05-developer-workflow/run-and-debug-ar.md)) اعمل قايمة بالجداول، وبعدين اقرا أول الـ Migrations في `backend/app/src/main/resources/db/migration`. دوّر على جدول للإضافة بس والـ Trigger اللي بيفرض ده. اعرف أنهي Annotations للـ Migrations موجودة بـ `python3 scripts/check_migration_compat.py --list`.
6. Keycloak. افتح لوحة الإدارة المحلية، ودوّر على الـ realm `matbakhy` والـ Clients (`matbakhy-web` و`matbakhy-mobile`) والمستخدمين التجريبيين الأربعة. اشرح ليه Client الموبايل بيستخدم PKCE ومن غير Secret.
7. تمرين فشل: نادي `curl -i http://127.0.0.1:8080/api/v1/me` من غير Session واقرا الـ Status وشكل الـ Body؛ وخد `requestId` من خطأ ودوّر عليه في `docker compose ... logs api`.
8. شغّل Class اختبار تكامل حقيقي واحد من `backend/app/src/test/java/com/matbakhy/app/orders` واقرا مخرجاته في مجلد `target/surefire-reports` اللي بيعمله بناء Maven تحت `backend/app/`.

## المُخرَج

ملاحظة بسلسلة النداءات لـ "العميل بيطلب عرض سعر" و"العميل بيسجّل أوردر": الملف، والـ Class، وأسماء الـ Methods، والجداول المتأثرة، والقواعد المفروضة (BR ids)، والاختبارات اللي بتغطيها. وبعدين تلات نقط عن إزاي هتضيف حقل جديد واحد في Response الأوردر (العقد، والـ Migration، والـ Service، وإعادة توليد الـ Client، والاختبارات، والتوثيق).

## فحص ذاتي

- [ ] أقدر أشرح ليه الموديولات مينفعش تعتمد على بعض إلا من خلال حزم `api` بتاعتهم وأنهي اختبار بيفرض ده.
- [ ] أقدر أقول فين بيتحسب السعر وليه السعر المبعوت من الـ Client بيترفض.
- [ ] أقدر أشرح مفاتيح الـ Idempotency وإيه اللي بيحصل عند إعادة الإرسال.
- [ ] أقدر أشرح الـ Authorization على مستوى الـ Object بمثال اختبار واحد حقيقي.
- [ ] عارف إن الـ Migrations في اتجاه واحد بس ومعنى "expand-only" بالنسبة للـ Rollback.
- [ ] عارف أنهي بيئات مسموح لها تحمّل Fixtures التطوير (`EnvironmentGuard`).

الخطوة الجاية: [اليوم 4](day-4-development-ar.md).
