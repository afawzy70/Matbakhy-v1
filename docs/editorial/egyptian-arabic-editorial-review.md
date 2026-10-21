# مراجعة تحريرية مستقلة للدليل بالعربي المصري

تاريخ المراجعة: 2026-10-21. النتيجة المحلية: **PASS** لمراجعة التغطية والتحرير والفحوصات، مع حدود المراجعة المكتوبة تحت. تجهيز GitHub ومتابعة فحوصاته بيتسجّلوا في آخر التقرير؛ نجاح الأدوات مش شهادة بكمال اللغة أو المعنى.

## الـ baseline والعزل

PR [#38](https://github.com/afawzy70/Matbakhy-v1/pull/38) اتفحص قبل التحرير من خلال GitHub: حالته MERGED على develop، والـ Head الأصلي f0d19f0490629fe9c1e11fb97659ef949365b67e. أحدث develop وقت إنشاء الفرع هو 39b84202b3cb91b641f611e5b87cc7552fef2d5c، وهو merge commit بتاع #38. مصدر المحتوى هو develop عند الـ SHA ده. فرع التسليم: docs/egyptian-arabic-editorial-audit؛ هدف الـ PR هو develop.

اتعمل Clone مستقل وworktree للتحرير، وكل مراجع كتب في worktree وفرع منفصلين. مفيش تعديل في worktree بتاع Claude، ولا تغيير في تاريخ #38، ولا تعديل لشغل Infrastructure Handbook الجديد، ولا Merge أو Force Push أو تدخل في Deployment أو تشغيلات CI موجودة. صفحة البنية التحتية العربية الموجودة في baseline اتراجعت كجزء من الـ 64 زوج.

تقييم DOC-001 قبل التنفيذ: صياغة عربية وتقارير تحرير فقط؛ المصدر الإنجليزي وسلوك المنتج والعقود والتطبيقات والبنية التحتية وضوابط التحقق ثابتين. سجل translation-sync اتحدّث بالطريقة الرسمية بعد المقارنة.

التقرير موجود خارج جرد الترجمة في docs/editorial بدل المسار المقترح داخل developer-handbook. الاستثناء الوحيد للـ Markdown غير المترجم داخل الدليل محدد بالاسم في الـ validator، ومفيش تسجيل ديناميكي لاستثناء تقرير جديد. عشان نحافظ على الأداة والإنجليزي كما هما، التقرير والـ glossary والأدلة اتجمعوا هنا؛ مفيش استثناء جديد ولا صفحة ترجمة ناقصة مستخبية.

## التغطية وطريقة المراجعة

اتقرت **64 صفحة عربية كاملة و64 أصل إنجليزي كامل**، قسم بقسم، في كل المجلدات recursively. اتعدّلت **63 صفحة عربية**؛ صفحة الرسومات اتراجعت بالكامل واتساب المحتوى المحمي فيها زي ما هو. اتعملت مقارنة نهائية للتعديلات، مش اعتماد على نجاح الـ validator.

| المراجع المحرر | النطاق | الأزواج | المراجع المستقل للتعديلات |
|---|---|---|---|
| business agent | README والمجلدات 00 و01 و02 | 20 | architecture agent |
| architecture agent | المجلدان 03 و04 | 18 | business agent |
| workflow agent | المجلدان 05 و06 | 17 | Root |
| Root | المجلد 07 | 9 | workflow agent |

المراجعة التانية متقاطعة بين Agents مختلفين، وهي مراجعة AI مستقلة عن مؤلف التعديل، مش موافقة بشرية ولا هوية GitHub مستقلة. المراجعة شملت كل الأقسام المعدّلة مع قراءة المصدر الإنجليزي الكامل. أي تعديل متابعة اتفحص تاني. أدلة النطاق والملاحظات والإصلاحات موجودة في [مجلد الأدلة](evidence/)، ومعها [سجل التصحيحات المنظّم](editorial-corrections.json).

## تصنيف الملاحظات

| التصنيف | عدد سجلات التصحيح |
|---|---:|
| CRITICAL | 0 |
| MAJOR | 61 |
| MINOR | 252 |
| SUGGESTION غير منفّذة | 0 |
| الإجمالي | 313 |

الأعداد دي لسجلات تصحيح محددة؛ بتشمل إصلاحات المتابعة وتنقيح صياغة اتعدّلت في الجولة الأولى، ومش عدد جذور مشاكل فريدة ولا عدد كل تكرارات المصطلح. بعض السجلات بتصلّح أكتر من ظهور لنفس التعبير في الملف. كل سجل تحت فيه الأصل والتصحيح والسبب والقسم ومخاطر المعنى. مفيش ملاحظة CRITICAL مؤكدة في المراجعة دي، ومفيش ملاحظة MAJOR أو MINOR مؤكدة متروكة في النطاق اللي اتراجع؛ ده مش ادعاء إن المراجعة اكتشفت كل عيب ممكن.

أهم المخاطر اللي اتصلّحت: مهلة الطلب مقابل وقت التحضير، مطابقة الإفادات مقابل التسويات المالية، وجود Security Headers مقابل حذفها، نطاق صلاحيات الـ Objects، MFA reset بواسطة الأدمن، سياسة REQUIRED عند غياب صف السياسة، Docker Images مقابل صور الميديا، Application Rollback مقابل Restore، وRecovery للأوردر مقابل Refund. اتجاه ردّ المبالغ وحفظ الأسعار التاريخية وشروط عدم تفعيل التعاملات الحقيقية فضلوا مطابقين للمصدر.

## المصطلحات

[الـ glossary القابل لإعادة الاستخدام](egyptian-arabic-engineering-glossary.md) بيوثّق Convention حسب السياق، مع فصل Preview عن Staging وعن Production، وSynthetic Data عن بيانات العملاء، وReconciliation عن Settlement، وRollback عن Restore وTransaction Rollback. أوردر وطلب مسموحين حسب السياق من غير تغيير النصوص المنقولة من الواجهة. العناوين القديمة اللي تغييرها هيكسر Anchors اتساب بعضها ثابت، مع توضيح المعنى في النص؛ الرسومات المحمية فضلت حرفيًا. مفيش استبدال شامل بدون قراءة السياق.

مسار المهارة ai-skills/developer-handbook مش موجود في baseline. مفيش Skill جديدة اتعملت ولا ملفات Skill اتعدّلت. التوصيات لمسؤول المسار موجودة في آخر الـ glossary: المصطلحات السياقية، المقارنة الكاملة، سجل قبل/بعد، مراجعة مستقلة، وفحوصات النص المحمي.

## الفحوصات والحدود

| الفحص | النتيجة |
|---|---|
| اختبارات parity الحالية | PASS — 20 اختبار |
| parity الكامل ومعه نفس-PR مقابل origin/develop | PASS — 64 زوج، صفر مشاكل |
| اختبارات DOC-001 الحالية | PASS — 32 اختبار |
| DOC-001 docs، روابط وAnchors ومسارات وMetadata | PASS — صفر مشاكل |
| مقارنة النص المحمي مع baseline | PASS — كل Code Fences بما فيها Mermaid، وكل Inline Code والروابط والأسهم محفوظة حرفيًا في الـ 64 صفحة |
| الإنجليزي والـ validators والـ exceptions والتطبيقات والـ workflows | بدون تغيير |
| علامات Bidi جديدة / git diff --check | مفيش علامات اتجاه مخفية جديدة؛ PASS لفحص الـ diff |
| GitHub PR declaration وCI | قيد المتابعة بعد نشر الفرع؛ النتيجة النهائية تتسجّل تحت |
| العرض البصري على GitHub وRTL وMermaid renderer | NOT_RUN |
| اختبارات التطبيق أو النشر الفعلي | NOT_RUN؛ مفيش تغيير تطبيق أو نشر مطلوب في المهمة |

حدود المراجعة: كل المراجعين AI، ومفيش native-speaker بشري راجع المحتوى. مفيش إثبات لكمال المعنى أو طبيعية اللغة بنسبة 100%. المصدر الإنجليزي نفسه فيه نقاط تاريخية أو اختصارات موثّقة؛ ماتعدّلش في المهمة دي. مثال: خلية integer EGP اتوضّحت بالقروش اعتمادًا على قاعدة الفلوس في الدليل كامل. أوصاف الحالة التاريخية مااتحوّلتش لادعاء عن تشغيل حالي.

صياغة «Product CI أخضر» فضلت داخل Mermaid محمي في diagrams-ar؛ القاعدة الصارمة لحفظ الـ Fences اتقدّمت على تحسين اللابل. الفحص البرمجي للروابط والـ Anchors اتعمل، لكن شكل الـ Markdown المختلط عربي/إنجليزي على GitHub، بما فيه الجداول والأقواس، مااتختبرش بصريًا؛ مفيش HTML اتجاه هش أو علامات Bidi جديدة اتضافت.

## جرد كل الأزواج

| الأصل الإنجليزي | النسخة العربية | المراجعة الكاملة | اتعدّلت؟ |
|---|---|---|---|
| [docs/developer-handbook/00-getting-started/glossary.md](../../docs/developer-handbook/00-getting-started/glossary.md) | [docs/developer-handbook/00-getting-started/glossary-ar.md](../../docs/developer-handbook/00-getting-started/glossary-ar.md) | قراءة كاملة ومقارنة الأقسام والمراجعة النهائية | أيوه |
| [docs/developer-handbook/00-getting-started/learning-roadmap.md](../../docs/developer-handbook/00-getting-started/learning-roadmap.md) | [docs/developer-handbook/00-getting-started/learning-roadmap-ar.md](../../docs/developer-handbook/00-getting-started/learning-roadmap-ar.md) | قراءة كاملة ومقارنة الأقسام والمراجعة النهائية | أيوه |
| [docs/developer-handbook/00-getting-started/project-overview.md](../../docs/developer-handbook/00-getting-started/project-overview.md) | [docs/developer-handbook/00-getting-started/project-overview-ar.md](../../docs/developer-handbook/00-getting-started/project-overview-ar.md) | قراءة كاملة ومقارنة الأقسام والمراجعة النهائية | أيوه |
| [docs/developer-handbook/00-getting-started/repository-navigation.md](../../docs/developer-handbook/00-getting-started/repository-navigation.md) | [docs/developer-handbook/00-getting-started/repository-navigation-ar.md](../../docs/developer-handbook/00-getting-started/repository-navigation-ar.md) | قراءة كاملة ومقارنة الأقسام والمراجعة النهائية | أيوه |
| [docs/developer-handbook/01-business/actors-and-permissions.md](../../docs/developer-handbook/01-business/actors-and-permissions.md) | [docs/developer-handbook/01-business/actors-and-permissions-ar.md](../../docs/developer-handbook/01-business/actors-and-permissions-ar.md) | قراءة كاملة ومقارنة الأقسام والمراجعة النهائية | أيوه |
| [docs/developer-handbook/01-business/business-rules.md](../../docs/developer-handbook/01-business/business-rules.md) | [docs/developer-handbook/01-business/business-rules-ar.md](../../docs/developer-handbook/01-business/business-rules-ar.md) | قراءة كاملة ومقارنة الأقسام والمراجعة النهائية | أيوه |
| [docs/developer-handbook/01-business/kitchen-management.md](../../docs/developer-handbook/01-business/kitchen-management.md) | [docs/developer-handbook/01-business/kitchen-management-ar.md](../../docs/developer-handbook/01-business/kitchen-management-ar.md) | قراءة كاملة ومقارنة الأقسام والمراجعة النهائية | أيوه |
| [docs/developer-handbook/01-business/marketplace-model.md](../../docs/developer-handbook/01-business/marketplace-model.md) | [docs/developer-handbook/01-business/marketplace-model-ar.md](../../docs/developer-handbook/01-business/marketplace-model-ar.md) | قراءة كاملة ومقارنة الأقسام والمراجعة النهائية | أيوه |
| [docs/developer-handbook/01-business/order-lifecycle.md](../../docs/developer-handbook/01-business/order-lifecycle.md) | [docs/developer-handbook/01-business/order-lifecycle-ar.md](../../docs/developer-handbook/01-business/order-lifecycle-ar.md) | قراءة كاملة ومقارنة الأقسام والمراجعة النهائية | أيوه |
| [docs/developer-handbook/01-business/pricing-and-payments.md](../../docs/developer-handbook/01-business/pricing-and-payments.md) | [docs/developer-handbook/01-business/pricing-and-payments-ar.md](../../docs/developer-handbook/01-business/pricing-and-payments-ar.md) | قراءة كاملة ومقارنة الأقسام والمراجعة النهائية | أيوه |
| [docs/developer-handbook/01-business/product-vision.md](../../docs/developer-handbook/01-business/product-vision.md) | [docs/developer-handbook/01-business/product-vision-ar.md](../../docs/developer-handbook/01-business/product-vision-ar.md) | قراءة كاملة ومقارنة الأقسام والمراجعة النهائية | أيوه |
| [docs/developer-handbook/01-business/target-users.md](../../docs/developer-handbook/01-business/target-users.md) | [docs/developer-handbook/01-business/target-users-ar.md](../../docs/developer-handbook/01-business/target-users-ar.md) | قراءة كاملة ومقارنة الأقسام والمراجعة النهائية | أيوه |
| [docs/developer-handbook/02-user-journeys/authentication-and-mfa.md](../../docs/developer-handbook/02-user-journeys/authentication-and-mfa.md) | [docs/developer-handbook/02-user-journeys/authentication-and-mfa-ar.md](../../docs/developer-handbook/02-user-journeys/authentication-and-mfa-ar.md) | قراءة كاملة ومقارنة الأقسام والمراجعة النهائية | أيوه |
| [docs/developer-handbook/02-user-journeys/backoffice-admin.md](../../docs/developer-handbook/02-user-journeys/backoffice-admin.md) | [docs/developer-handbook/02-user-journeys/backoffice-admin-ar.md](../../docs/developer-handbook/02-user-journeys/backoffice-admin-ar.md) | قراءة كاملة ومقارنة الأقسام والمراجعة النهائية | أيوه |
| [docs/developer-handbook/02-user-journeys/complete-order-journey.md](../../docs/developer-handbook/02-user-journeys/complete-order-journey.md) | [docs/developer-handbook/02-user-journeys/complete-order-journey-ar.md](../../docs/developer-handbook/02-user-journeys/complete-order-journey-ar.md) | قراءة كاملة ومقارنة الأقسام والمراجعة النهائية | أيوه |
| [docs/developer-handbook/02-user-journeys/cook-operations.md](../../docs/developer-handbook/02-user-journeys/cook-operations.md) | [docs/developer-handbook/02-user-journeys/cook-operations-ar.md](../../docs/developer-handbook/02-user-journeys/cook-operations-ar.md) | قراءة كاملة ومقارنة الأقسام والمراجعة النهائية | أيوه |
| [docs/developer-handbook/02-user-journeys/customer-mobile.md](../../docs/developer-handbook/02-user-journeys/customer-mobile.md) | [docs/developer-handbook/02-user-journeys/customer-mobile-ar.md](../../docs/developer-handbook/02-user-journeys/customer-mobile-ar.md) | قراءة كاملة ومقارنة الأقسام والمراجعة النهائية | أيوه |
| [docs/developer-handbook/02-user-journeys/customer-web.md](../../docs/developer-handbook/02-user-journeys/customer-web.md) | [docs/developer-handbook/02-user-journeys/customer-web-ar.md](../../docs/developer-handbook/02-user-journeys/customer-web-ar.md) | قراءة كاملة ومقارنة الأقسام والمراجعة النهائية | أيوه |
| [docs/developer-handbook/02-user-journeys/finance-and-support.md](../../docs/developer-handbook/02-user-journeys/finance-and-support.md) | [docs/developer-handbook/02-user-journeys/finance-and-support-ar.md](../../docs/developer-handbook/02-user-journeys/finance-and-support-ar.md) | قراءة كاملة ومقارنة الأقسام والمراجعة النهائية | أيوه |
| [docs/developer-handbook/03-architecture/backend-architecture.md](../../docs/developer-handbook/03-architecture/backend-architecture.md) | [docs/developer-handbook/03-architecture/backend-architecture-ar.md](../../docs/developer-handbook/03-architecture/backend-architecture-ar.md) | قراءة كاملة ومقارنة الأقسام والمراجعة النهائية | أيوه |
| [docs/developer-handbook/03-architecture/database-architecture.md](../../docs/developer-handbook/03-architecture/database-architecture.md) | [docs/developer-handbook/03-architecture/database-architecture-ar.md](../../docs/developer-handbook/03-architecture/database-architecture-ar.md) | قراءة كاملة ومقارنة الأقسام والمراجعة النهائية | أيوه |
| [docs/developer-handbook/03-architecture/diagrams.md](../../docs/developer-handbook/03-architecture/diagrams.md) | [docs/developer-handbook/03-architecture/diagrams-ar.md](../../docs/developer-handbook/03-architecture/diagrams-ar.md) | قراءة كاملة ومقارنة الأقسام والمراجعة النهائية | لأ |
| [docs/developer-handbook/03-architecture/frontend-architecture.md](../../docs/developer-handbook/03-architecture/frontend-architecture.md) | [docs/developer-handbook/03-architecture/frontend-architecture-ar.md](../../docs/developer-handbook/03-architecture/frontend-architecture-ar.md) | قراءة كاملة ومقارنة الأقسام والمراجعة النهائية | أيوه |
| [docs/developer-handbook/03-architecture/identity-and-security.md](../../docs/developer-handbook/03-architecture/identity-and-security.md) | [docs/developer-handbook/03-architecture/identity-and-security-ar.md](../../docs/developer-handbook/03-architecture/identity-and-security-ar.md) | قراءة كاملة ومقارنة الأقسام والمراجعة النهائية | أيوه |
| [docs/developer-handbook/03-architecture/infrastructure-and-deployment.md](../../docs/developer-handbook/03-architecture/infrastructure-and-deployment.md) | [docs/developer-handbook/03-architecture/infrastructure-and-deployment-ar.md](../../docs/developer-handbook/03-architecture/infrastructure-and-deployment-ar.md) | قراءة كاملة ومقارنة الأقسام والمراجعة النهائية | أيوه |
| [docs/developer-handbook/03-architecture/integration-and-events.md](../../docs/developer-handbook/03-architecture/integration-and-events.md) | [docs/developer-handbook/03-architecture/integration-and-events-ar.md](../../docs/developer-handbook/03-architecture/integration-and-events-ar.md) | قراءة كاملة ومقارنة الأقسام والمراجعة النهائية | أيوه |
| [docs/developer-handbook/03-architecture/mobile-architecture.md](../../docs/developer-handbook/03-architecture/mobile-architecture.md) | [docs/developer-handbook/03-architecture/mobile-architecture-ar.md](../../docs/developer-handbook/03-architecture/mobile-architecture-ar.md) | قراءة كاملة ومقارنة الأقسام والمراجعة النهائية | أيوه |
| [docs/developer-handbook/03-architecture/system-overview.md](../../docs/developer-handbook/03-architecture/system-overview.md) | [docs/developer-handbook/03-architecture/system-overview-ar.md](../../docs/developer-handbook/03-architecture/system-overview-ar.md) | قراءة كاملة ومقارنة الأقسام والمراجعة النهائية | أيوه |
| [docs/developer-handbook/04-codebase/api-contracts.md](../../docs/developer-handbook/04-codebase/api-contracts.md) | [docs/developer-handbook/04-codebase/api-contracts-ar.md](../../docs/developer-handbook/04-codebase/api-contracts-ar.md) | قراءة كاملة ومقارنة الأقسام والمراجعة النهائية | أيوه |
| [docs/developer-handbook/04-codebase/backend-module-guide.md](../../docs/developer-handbook/04-codebase/backend-module-guide.md) | [docs/developer-handbook/04-codebase/backend-module-guide-ar.md](../../docs/developer-handbook/04-codebase/backend-module-guide-ar.md) | قراءة كاملة ومقارنة الأقسام والمراجعة النهائية | أيوه |
| [docs/developer-handbook/04-codebase/frontend-code-guide.md](../../docs/developer-handbook/04-codebase/frontend-code-guide.md) | [docs/developer-handbook/04-codebase/frontend-code-guide-ar.md](../../docs/developer-handbook/04-codebase/frontend-code-guide-ar.md) | قراءة كاملة ومقارنة الأقسام والمراجعة النهائية | أيوه |
| [docs/developer-handbook/04-codebase/mobile-code-guide.md](../../docs/developer-handbook/04-codebase/mobile-code-guide.md) | [docs/developer-handbook/04-codebase/mobile-code-guide-ar.md](../../docs/developer-handbook/04-codebase/mobile-code-guide-ar.md) | قراءة كاملة ومقارنة الأقسام والمراجعة النهائية | أيوه |
| [docs/developer-handbook/04-codebase/order-implementation.md](../../docs/developer-handbook/04-codebase/order-implementation.md) | [docs/developer-handbook/04-codebase/order-implementation-ar.md](../../docs/developer-handbook/04-codebase/order-implementation-ar.md) | قراءة كاملة ومقارنة الأقسام والمراجعة النهائية | أيوه |
| [docs/developer-handbook/04-codebase/payment-implementation.md](../../docs/developer-handbook/04-codebase/payment-implementation.md) | [docs/developer-handbook/04-codebase/payment-implementation-ar.md](../../docs/developer-handbook/04-codebase/payment-implementation-ar.md) | قراءة كاملة ومقارنة الأقسام والمراجعة النهائية | أيوه |
| [docs/developer-handbook/04-codebase/pricing-implementation.md](../../docs/developer-handbook/04-codebase/pricing-implementation.md) | [docs/developer-handbook/04-codebase/pricing-implementation-ar.md](../../docs/developer-handbook/04-codebase/pricing-implementation-ar.md) | قراءة كاملة ومقارنة الأقسام والمراجعة النهائية | أيوه |
| [docs/developer-handbook/04-codebase/repository-map.md](../../docs/developer-handbook/04-codebase/repository-map.md) | [docs/developer-handbook/04-codebase/repository-map-ar.md](../../docs/developer-handbook/04-codebase/repository-map-ar.md) | قراءة كاملة ومقارنة الأقسام والمراجعة النهائية | أيوه |
| [docs/developer-handbook/04-codebase/shared-libraries.md](../../docs/developer-handbook/04-codebase/shared-libraries.md) | [docs/developer-handbook/04-codebase/shared-libraries-ar.md](../../docs/developer-handbook/04-codebase/shared-libraries-ar.md) | قراءة كاملة ومقارنة الأقسام والمراجعة النهائية | أيوه |
| [docs/developer-handbook/05-developer-workflow/branch-maintenance.md](../../docs/developer-handbook/05-developer-workflow/branch-maintenance.md) | [docs/developer-handbook/05-developer-workflow/branch-maintenance-ar.md](../../docs/developer-handbook/05-developer-workflow/branch-maintenance-ar.md) | قراءة كاملة ومقارنة الأقسام والمراجعة النهائية | أيوه |
| [docs/developer-handbook/05-developer-workflow/ci-cd.md](../../docs/developer-handbook/05-developer-workflow/ci-cd.md) | [docs/developer-handbook/05-developer-workflow/ci-cd-ar.md](../../docs/developer-handbook/05-developer-workflow/ci-cd-ar.md) | قراءة كاملة ومقارنة الأقسام والمراجعة النهائية | أيوه |
| [docs/developer-handbook/05-developer-workflow/documentation-maintenance.md](../../docs/developer-handbook/05-developer-workflow/documentation-maintenance.md) | [docs/developer-handbook/05-developer-workflow/documentation-maintenance-ar.md](../../docs/developer-handbook/05-developer-workflow/documentation-maintenance-ar.md) | قراءة كاملة ومقارنة الأقسام والمراجعة النهائية | أيوه |
| [docs/developer-handbook/05-developer-workflow/documentation-policy.md](../../docs/developer-handbook/05-developer-workflow/documentation-policy.md) | [docs/developer-handbook/05-developer-workflow/documentation-policy-ar.md](../../docs/developer-handbook/05-developer-workflow/documentation-policy-ar.md) | قراءة كاملة ومقارنة الأقسام والمراجعة النهائية | أيوه |
| [docs/developer-handbook/05-developer-workflow/git-and-pr-workflow.md](../../docs/developer-handbook/05-developer-workflow/git-and-pr-workflow.md) | [docs/developer-handbook/05-developer-workflow/git-and-pr-workflow-ar.md](../../docs/developer-handbook/05-developer-workflow/git-and-pr-workflow-ar.md) | قراءة كاملة ومقارنة الأقسام والمراجعة النهائية | أيوه |
| [docs/developer-handbook/05-developer-workflow/local-environment.md](../../docs/developer-handbook/05-developer-workflow/local-environment.md) | [docs/developer-handbook/05-developer-workflow/local-environment-ar.md](../../docs/developer-handbook/05-developer-workflow/local-environment-ar.md) | قراءة كاملة ومقارنة الأقسام والمراجعة النهائية | أيوه |
| [docs/developer-handbook/05-developer-workflow/prerequisites.md](../../docs/developer-handbook/05-developer-workflow/prerequisites.md) | [docs/developer-handbook/05-developer-workflow/prerequisites-ar.md](../../docs/developer-handbook/05-developer-workflow/prerequisites-ar.md) | قراءة كاملة ومقارنة الأقسام والمراجعة النهائية | أيوه |
| [docs/developer-handbook/05-developer-workflow/preview-environment.md](../../docs/developer-handbook/05-developer-workflow/preview-environment.md) | [docs/developer-handbook/05-developer-workflow/preview-environment-ar.md](../../docs/developer-handbook/05-developer-workflow/preview-environment-ar.md) | قراءة كاملة ومقارنة الأقسام والمراجعة النهائية | أيوه |
| [docs/developer-handbook/05-developer-workflow/run-and-debug.md](../../docs/developer-handbook/05-developer-workflow/run-and-debug.md) | [docs/developer-handbook/05-developer-workflow/run-and-debug-ar.md](../../docs/developer-handbook/05-developer-workflow/run-and-debug-ar.md) | قراءة كاملة ومقارنة الأقسام والمراجعة النهائية | أيوه |
| [docs/developer-handbook/05-developer-workflow/test-execution.md](../../docs/developer-handbook/05-developer-workflow/test-execution.md) | [docs/developer-handbook/05-developer-workflow/test-execution-ar.md](../../docs/developer-handbook/05-developer-workflow/test-execution-ar.md) | قراءة كاملة ومقارنة الأقسام والمراجعة النهائية | أيوه |
| [docs/developer-handbook/05-developer-workflow/troubleshooting.md](../../docs/developer-handbook/05-developer-workflow/troubleshooting.md) | [docs/developer-handbook/05-developer-workflow/troubleshooting-ar.md](../../docs/developer-handbook/05-developer-workflow/troubleshooting-ar.md) | قراءة كاملة ومقارنة الأقسام والمراجعة النهائية | أيوه |
| [docs/developer-handbook/06-onboarding-labs/day-1-business.md](../../docs/developer-handbook/06-onboarding-labs/day-1-business.md) | [docs/developer-handbook/06-onboarding-labs/day-1-business-ar.md](../../docs/developer-handbook/06-onboarding-labs/day-1-business-ar.md) | قراءة كاملة ومقارنة الأقسام والمراجعة النهائية | أيوه |
| [docs/developer-handbook/06-onboarding-labs/day-2-applications.md](../../docs/developer-handbook/06-onboarding-labs/day-2-applications.md) | [docs/developer-handbook/06-onboarding-labs/day-2-applications-ar.md](../../docs/developer-handbook/06-onboarding-labs/day-2-applications-ar.md) | قراءة كاملة ومقارنة الأقسام والمراجعة النهائية | أيوه |
| [docs/developer-handbook/06-onboarding-labs/day-3-backend-security.md](../../docs/developer-handbook/06-onboarding-labs/day-3-backend-security.md) | [docs/developer-handbook/06-onboarding-labs/day-3-backend-security-ar.md](../../docs/developer-handbook/06-onboarding-labs/day-3-backend-security-ar.md) | قراءة كاملة ومقارنة الأقسام والمراجعة النهائية | أيوه |
| [docs/developer-handbook/06-onboarding-labs/day-4-development.md](../../docs/developer-handbook/06-onboarding-labs/day-4-development.md) | [docs/developer-handbook/06-onboarding-labs/day-4-development-ar.md](../../docs/developer-handbook/06-onboarding-labs/day-4-development-ar.md) | قراءة كاملة ومقارنة الأقسام والمراجعة النهائية | أيوه |
| [docs/developer-handbook/06-onboarding-labs/day-5-first-contribution.md](../../docs/developer-handbook/06-onboarding-labs/day-5-first-contribution.md) | [docs/developer-handbook/06-onboarding-labs/day-5-first-contribution-ar.md](../../docs/developer-handbook/06-onboarding-labs/day-5-first-contribution-ar.md) | قراءة كاملة ومقارنة الأقسام والمراجعة النهائية | أيوه |
| [docs/developer-handbook/06-onboarding-labs/final-assessment.md](../../docs/developer-handbook/06-onboarding-labs/final-assessment.md) | [docs/developer-handbook/06-onboarding-labs/final-assessment-ar.md](../../docs/developer-handbook/06-onboarding-labs/final-assessment-ar.md) | قراءة كاملة ومقارنة الأقسام والمراجعة النهائية | أيوه |
| [docs/developer-handbook/07-reference/architecture-decisions.md](../../docs/developer-handbook/07-reference/architecture-decisions.md) | [docs/developer-handbook/07-reference/architecture-decisions-ar.md](../../docs/developer-handbook/07-reference/architecture-decisions-ar.md) | قراءة كاملة ومقارنة الأقسام والمراجعة النهائية | أيوه |
| [docs/developer-handbook/07-reference/branch-cleanup-audit-2026-10-20.md](../../docs/developer-handbook/07-reference/branch-cleanup-audit-2026-10-20.md) | [docs/developer-handbook/07-reference/branch-cleanup-audit-2026-10-20-ar.md](../../docs/developer-handbook/07-reference/branch-cleanup-audit-2026-10-20-ar.md) | قراءة كاملة ومقارنة الأقسام والمراجعة النهائية | أيوه |
| [docs/developer-handbook/07-reference/documentation-map.md](../../docs/developer-handbook/07-reference/documentation-map.md) | [docs/developer-handbook/07-reference/documentation-map-ar.md](../../docs/developer-handbook/07-reference/documentation-map-ar.md) | قراءة كاملة ومقارنة الأقسام والمراجعة النهائية | أيوه |
| [docs/developer-handbook/07-reference/frequently-asked-questions.md](../../docs/developer-handbook/07-reference/frequently-asked-questions.md) | [docs/developer-handbook/07-reference/frequently-asked-questions-ar.md](../../docs/developer-handbook/07-reference/frequently-asked-questions-ar.md) | قراءة كاملة ومقارنة الأقسام والمراجعة النهائية | أيوه |
| [docs/developer-handbook/07-reference/fresh-developer-simulation.md](../../docs/developer-handbook/07-reference/fresh-developer-simulation.md) | [docs/developer-handbook/07-reference/fresh-developer-simulation-ar.md](../../docs/developer-handbook/07-reference/fresh-developer-simulation-ar.md) | قراءة كاملة ومقارنة الأقسام والمراجعة النهائية | أيوه |
| [docs/developer-handbook/07-reference/implementation-status.md](../../docs/developer-handbook/07-reference/implementation-status.md) | [docs/developer-handbook/07-reference/implementation-status-ar.md](../../docs/developer-handbook/07-reference/implementation-status-ar.md) | قراءة كاملة ومقارنة الأقسام والمراجعة النهائية | أيوه |
| [docs/developer-handbook/07-reference/independent-reviews.md](../../docs/developer-handbook/07-reference/independent-reviews.md) | [docs/developer-handbook/07-reference/independent-reviews-ar.md](../../docs/developer-handbook/07-reference/independent-reviews-ar.md) | قراءة كاملة ومقارنة الأقسام والمراجعة النهائية | أيوه |
| [docs/developer-handbook/07-reference/inventory-and-gaps.md](../../docs/developer-handbook/07-reference/inventory-and-gaps.md) | [docs/developer-handbook/07-reference/inventory-and-gaps-ar.md](../../docs/developer-handbook/07-reference/inventory-and-gaps-ar.md) | قراءة كاملة ومقارنة الأقسام والمراجعة النهائية | أيوه |
| [docs/developer-handbook/07-reference/requirements-traceability.md](../../docs/developer-handbook/07-reference/requirements-traceability.md) | [docs/developer-handbook/07-reference/requirements-traceability-ar.md](../../docs/developer-handbook/07-reference/requirements-traceability-ar.md) | قراءة كاملة ومقارنة الأقسام والمراجعة النهائية | أيوه |
| [docs/developer-handbook/README.md](../../docs/developer-handbook/README.md) | [docs/developer-handbook/README-ar.md](../../docs/developer-handbook/README-ar.md) | قراءة كاملة ومقارنة الأقسام والمراجعة النهائية | أيوه |

## سجل التصحيحات بالتفصيل

القيم الأصلية هنا تاريخية؛ لو نفس النص اتنقّح على مراحل، آخر تصحيح هو الموجود في الملف النهائي.

### EA-001 — MINOR

الملف: [docs/developer-handbook/README-ar.md](../../docs/developer-handbook/README-ar.md). القسم: # دليل المطوّر لمطبخي (Matbakhy).

| قبل | بعد |
|---|---|
| الدليل مخرَج حيّ تحت | الدليل بيتحدّث باستمرار مع تطور المشروع، وبيخضع لـ |

السبب: Replace literal living-deliverable metaphor with natural Egyptian explanation.

مخاطر المعنى: Low: wording clarified; English requirement and protected technical text preserved.

### EA-002 — MINOR

الملف: [docs/developer-handbook/README-ar.md](../../docs/developer-handbook/README-ar.md). القسم: ### 07 المراجع.

| قبل | بعد |
|---|---|
| مقروء بالآلة: | ملفات قابلة للقراءة آليًا: |

السبب: Describe machine-readable files naturally.

مخاطر المعنى: Low: wording clarified; English requirement and protected technical text preserved.

### EA-003 — MINOR

الملف: [docs/developer-handbook/README-ar.md](../../docs/developer-handbook/README-ar.md). القسم: # دليل المطوّر لمطبخي (Matbakhy).

| قبل | بعد |
|---|---|
| حالة التنفيذ موسومة في كل صفحة | حالة التنفيذ موضّحة في كل صفحة |

السبب: Avoid unnatural metadata wording.

مخاطر المعنى: Low: wording clarified; English requirement and protected technical text preserved.

### EA-004 — MINOR

الملف: [docs/developer-handbook/00-getting-started/glossary-ar.md](../../docs/developer-handbook/00-getting-started/glossary-ar.md). القسم: ## مصطلحات الـ Domain والمصطلحات التقنية.

| قبل | بعد |
|---|---|
| سياسة اصطناعية مسموحة بس | سياسة تجريبية غير حقيقية مسموحة بس |

السبب: Clarify synthetic policy without changing allowed environments.

مخاطر المعنى: Low: wording clarified; English requirement and protected technical text preserved.

### EA-005 — MAJOR

الملف: [docs/developer-handbook/00-getting-started/glossary-ar.md](../../docs/developer-handbook/00-getting-started/glossary-ar.md). القسم: ## مصطلحات الـ Domain والمصطلحات التقنية.

| قبل | بعد |
|---|---|
| السجلات الغريبة بترجّع 404 | السجلات الخاصة بمستخدم تاني بترجّع 404 |

السبب: Foreign records means ownership scope, not malformed records.

مخاطر المعنى: Low: wording clarified; English requirement and protected technical text preserved.

### EA-006 — MINOR

الملف: [docs/developer-handbook/00-getting-started/learning-roadmap-ar.md](../../docs/developer-handbook/00-getting-started/learning-roadmap-ar.md). القسم: ## الخريطة.

| قبل | بعد |
|---|---|
| وأسطح الموبايل | وواجهات الموبايل |

السبب: Surface means application interface, not literal surface.

مخاطر المعنى: Low: wording clarified; English requirement and protected technical text preserved.

### EA-007 — MINOR

الملف: [docs/developer-handbook/00-getting-started/learning-roadmap-ar.md](../../docs/developer-handbook/00-getting-started/learning-roadmap-ar.md). القسم: ## ترتيب القراءة.

| قبل | بعد |
|---|---|
| الصفحات الخاصة بكل سطح | الصفحات الخاصة بكل واجهة |

السبب: Use natural interface terminology.

مخاطر المعنى: Low: wording clarified; English requirement and protected technical text preserved.

### EA-008 — MINOR

الملف: [docs/developer-handbook/00-getting-started/learning-roadmap-ar.md](../../docs/developer-handbook/00-getting-started/learning-roadmap-ar.md). القسم: ## قواعد أساسية أثناء التعلّم.

| قبل | بعد |
|---|---|
| استخدم بيانات اصطناعية (Synthetic) بس | استخدم بيانات تجريبية غير حقيقية (Synthetic Data) بس |

السبب: Make fake fixture data explicit.

مخاطر المعنى: Low: wording clarified; English requirement and protected technical text preserved.

### EA-009 — MINOR

الملف: [docs/developer-handbook/00-getting-started/learning-roadmap-ar.md](../../docs/developer-handbook/00-getting-started/learning-roadmap-ar.md). القسم: ## لو اتعطّلت.

| قبل | بعد |
|---|---|
| الاختبار اللي اسمه على اسم السلوك هو أحسن مواصفات | الاختبار اللي اسمه بيوصف السلوك هو أوضح مرجع للسلوك المتوقع |

السبب: Remove literal specification phrase.

مخاطر المعنى: Low: wording clarified; English requirement and protected technical text preserved.

### EA-010 — MINOR

الملف: [docs/developer-handbook/00-getting-started/repository-navigation-ar.md](../../docs/developer-handbook/00-getting-started/repository-navigation-ar.md). القسم: # التنقّل في الـ Repository: ألاقيه فين؟.

| قبل | بعد |
|---|---|
| السعر بيتـ Validate فين؟ | التحقق من السعر بيحصل فين؟ |

السبب: Avoid artificial Arabic/English verb hybrid.

مخاطر المعنى: Low: wording clarified; English requirement and protected technical text preserved.

### EA-011 — MINOR

الملف: [docs/developer-handbook/00-getting-started/project-overview-ar.md](../../docs/developer-handbook/00-getting-started/project-overview-ar.md). القسم: ## التطبيقات والكود.

| قبل | بعد |
|---|---|
| \| السطح \| المجلد \| الجمهور \| | \| الواجهة \| المجلد \| الجمهور \| |

السبب: Surface means application interface.

مخاطر المعنى: Low: wording clarified; English requirement and protected technical text preserved.

### EA-012 — MINOR

الملف: [docs/developer-handbook/00-getting-started/project-overview-ar.md](../../docs/developer-handbook/00-getting-started/project-overview-ar.md). القسم: ### مقفول بشروط أو مش شغّال.

| قبل | بعد |
|---|---|
| **الفلوس الحقيقية مقفولة** بمراجعة قانونية ومراجعة حماية المستهلك ومراجعة حساب المستلم | **التعاملات المالية الحقيقية مش مفعّلة حاليًا**، وتفعيلها مشروط بمراجعة قانونية ومراجعة حماية المستهلك ومراجعة حساب المستلم |

السبب: State financial gate clearly without weakening prerequisites.

مخاطر المعنى: Low: wording clarified; English requirement and protected technical text preserved.

### EA-013 — MINOR

الملف: [docs/developer-handbook/00-getting-started/project-overview-ar.md](../../docs/developer-handbook/00-getting-started/project-overview-ar.md). القسم: ## المشاكل اللي بيحلّها.

| قبل | بعد |
|---|---|
| فلوس اصطناعية (Synthetic) بس | مبالغ تجريبية غير حقيقية (Synthetic) بس |

السبب: Avoid unnatural synthetic money wording.

مخاطر المعنى: Low: wording clarified; English requirement and protected technical text preserved.

### EA-014 — MINOR

الملف: [docs/developer-handbook/01-business/marketplace-model-ar.md](../../docs/developer-handbook/01-business/marketplace-model-ar.md). القسم: # نموذج الـ Marketplace.

| قبل | بعد |
|---|---|
| الـ Marketplace المعاملاتي | عمليات البيع والطلب في الـ Marketplace |

السبب: Clarify transactional marketplace.

مخاطر المعنى: Low: wording clarified; English requirement and protected technical text preserved.

### EA-015 — MAJOR

الملف: [docs/developer-handbook/01-business/marketplace-model-ar.md](../../docs/developer-handbook/01-business/marketplace-model-ar.md). القسم: # نموذج الـ Marketplace.

| قبل | بعد |
|---|---|
| أي تسييل (Monetization) | أي تحقيق دخل (Monetization) |

السبب: Monetization is revenue generation, not asset liquidation.

مخاطر المعنى: Low: wording clarified; English requirement and protected technical text preserved.

### EA-016 — MINOR

الملف: [docs/developer-handbook/01-business/marketplace-model-ar.md](../../docs/developer-handbook/01-business/marketplace-model-ar.md). القسم: ## الهيكل التلاتي الأطراف.

| قبل | بعد |
|---|---|
| الحقيقة البيزنس الأساسية | الحقيقة الأساسية في نموذج العمل |

السبب: Natural business phrasing.

مخاطر المعنى: Low: wording clarified; English requirement and protected technical text preserved.

### EA-017 — MINOR

الملف: [docs/developer-handbook/01-business/marketplace-model-ar.md](../../docs/developer-handbook/01-business/marketplace-model-ar.md). القسم: ## المنصة مسؤولة عن إيه.

| قبل | بعد |
|---|---|
| بوابة جودة العرض | ضوابط جودة المطابخ المعروضة |

السبب: Clarify supply-side approval gate.

مخاطر المعنى: Low: wording clarified; English requirement and protected technical text preserved.

### EA-018 — MINOR

الملف: [docs/developer-handbook/01-business/marketplace-model-ar.md](../../docs/developer-handbook/01-business/marketplace-model-ar.md). القسم: ## المنصة مسؤولة عن إيه.

| قبل | بعد |
|---|---|
| حقيقة التوافر | المرجع الفعلي للتوافر |

السبب: Avoid literal truth metaphor.

مخاطر المعنى: Low: wording clarified; English requirement and protected technical text preserved.

### EA-019 — MINOR

الملف: [docs/developer-handbook/01-business/marketplace-model-ar.md](../../docs/developer-handbook/01-business/marketplace-model-ar.md). القسم: ## المنصة مسؤولة عن إيه.

| قبل | بعد |
|---|---|
| حقيقة السعر | المرجع الفعلي للسعر |

السبب: Avoid literal truth metaphor.

مخاطر المعنى: Low: wording clarified; English requirement and protected technical text preserved.

### EA-020 — MINOR

الملف: [docs/developer-handbook/01-business/actors-and-permissions-ar.md](../../docs/developer-handbook/01-business/actors-and-permissions-ar.md). القسم: # الأطراف والصلاحيات.

| قبل | بعد |
|---|---|
| حزم من المنح بيحملها إنسان | مجموعات صلاحيات ممنوحة لشخص |

السبب: Avoid literal grant-bundle phrase.

مخاطر المعنى: Low: wording clarified; English requirement and protected technical text preserved.

### EA-021 — MINOR

الملف: [docs/developer-handbook/01-business/actors-and-permissions-ar.md](../../docs/developer-handbook/01-business/actors-and-permissions-ar.md). القسم: ## الأطراف.

| قبل | بعد |
|---|---|
| **متقدّم للطبخ** | **متقدّم للعمل كطبّاخ** |

السبب: Application is for becoming a seller, not cooking an individual meal.

مخاطر المعنى: Low: wording clarified; English requirement and protected technical text preserved.

### EA-022 — MAJOR

الملف: [docs/developer-handbook/01-business/kitchen-management-ar.md](../../docs/developer-handbook/01-business/kitchen-management-ar.md). القسم: ## 4. الوجبات.

| قبل | بعد |
|---|---|
| علامة مراجعة محتوى من الموظفين متعامدة | علامة مستقلة لمراجعة المحتوى من الموظفين |

السبب: Orthogonal means independent state, not geometric perpendicularity.

مخاطر المعنى: Low: wording clarified; English requirement and protected technical text preserved.

### EA-023 — MINOR

الملف: [docs/developer-handbook/01-business/kitchen-management-ar.md](../../docs/developer-handbook/01-business/kitchen-management-ar.md). القسم: ## 2. سجل المطبخ وحالات التشغيل بتاعته.

| قبل | بعد |
|---|---|
| استئناف الطبّاخ، استرجاع الموظفين | استئناف البيع من الطبّاخ، إعادة تفعيل المطبخ من الموظفين |

السبب: Avoid implying staff accounts are restored.

مخاطر المعنى: Low: wording clarified; English requirement and protected technical text preserved.

### EA-024 — MINOR

الملف: [docs/developer-handbook/01-business/kitchen-management-ar.md](../../docs/developer-handbook/01-business/kitchen-management-ar.md). القسم: ## 8. حذف الحساب للطبّاخ.

| قبل | بعد |
|---|---|
| **بيتحجز لمراجعة الموظفين** | **حذفه بيتأجّل لحد مراجعة الموظفين** |

السبب: Account is held for review, not person detained.

مخاطر المعنى: Low: wording clarified; English requirement and protected technical text preserved.

### EA-025 — MAJOR

الملف: [docs/developer-handbook/01-business/order-lifecycle-ar.md](../../docs/developer-handbook/01-business/order-lifecycle-ar.md). القسم: ## الخطوة 3 — التوافر والسعة.

| قبل | بعد |
|---|---|
| بداية الـ Slot − وقت التجهيز | بداية الـ Slot − مهلة الطلب المسبق (lead time) |

السبب: Lead time is ordering cutoff interval, not cooking preparation duration.

مخاطر المعنى: Low: wording clarified; English requirement and protected technical text preserved.

### EA-026 — MAJOR

الملف: [docs/developer-handbook/01-business/order-lifecycle-ar.md](../../docs/developer-handbook/01-business/order-lifecycle-ar.md). القسم: ## الخطوة 3 — التوافر والسعة.

| قبل | بعد |
|---|---|
| الـ Slot فيه 3 وجبات متبقية | الـ Slot فيه 3 حصص متبقية |

السبب: Portions are capacity units; meal types are distinct.

مخاطر المعنى: Low: wording clarified; English requirement and protected technical text preserved.

### EA-027 — MAJOR

الملف: [docs/developer-handbook/01-business/order-lifecycle-ar.md](../../docs/developer-handbook/01-business/order-lifecycle-ar.md). القسم: ## الخطوة 3 — التوافر والسعة.

| قبل | بعد |
|---|---|
| على آخر وجبة | على آخر حصة |

السبب: Preserve distinction between portion capacity and meal type.

مخاطر المعنى: Low: wording clarified; English requirement and protected technical text preserved.

### EA-028 — MAJOR

الملف: [docs/developer-handbook/01-business/order-lifecycle-ar.md](../../docs/developer-handbook/01-business/order-lifecycle-ar.md). القسم: ## الخطوة 5 — العرض القديم والانتهاء.

| قبل | بعد |
|---|---|
| العرض أقدم من موعد انتهائه | مدة صلاحية العرض انتهت |

السبب: Quote expiry condition must describe past expiry, not being older than a date.

مخاطر المعنى: Low: wording clarified; English requirement and protected technical text preserved.

### EA-029 — MINOR

الملف: [docs/developer-handbook/01-business/order-lifecycle-ar.md](../../docs/developer-handbook/01-business/order-lifecycle-ar.md). القسم: ## الخطوة 7 — المطبخ بيقبل أو يرفض.

| قبل | بعد |
|---|---|
| المخزون بيتفرج عنه مرة واحدة | الحصص المحجوزة بترجع متاحة مرة واحدة |

السبب: Use natural stock-release explanation; no consumed stock restored.

مخاطر المعنى: Low: wording clarified; English requirement and protected technical text preserved.

### EA-030 — MINOR

الملف: [docs/developer-handbook/01-business/order-lifecycle-ar.md](../../docs/developer-handbook/01-business/order-lifecycle-ar.md). القسم: ## الخطوة 8 — التحضير.

| قبل | بعد |
|---|---|
| بيفرّج عن التخصيص مرة واحدة | بيرجع الحصص المخصّصة متاحة مرة واحدة |

السبب: Natural allocation-release explanation before preparation.

مخاطر المعنى: Low: wording clarified; English requirement and protected technical text preserved.

### EA-031 — MINOR

الملف: [docs/developer-handbook/01-business/order-lifecycle-ar.md](../../docs/developer-handbook/01-business/order-lifecycle-ar.md). القسم: ## نظرة عامة.

| قبل | بعد |
|---|---|
| هي عايشة في سجلات منفصلة | بتتسجّل في سجلات منفصلة |

السبب: Avoid anthropomorphic literal translation.

مخاطر المعنى: Low: wording clarified; English requirement and protected technical text preserved.

### EA-032 — MINOR

الملف: [docs/developer-handbook/01-business/pricing-and-payments-ar.md](../../docs/developer-handbook/01-business/pricing-and-payments-ar.md). القسم: # التسعير والدفع.

| قبل | بعد |
|---|---|
| منفّذة ومختبرة بفلوس اصطناعية (Synthetic)؛ الفلوس الحقيقية متقفّلة (Gated) | منفّذة ومختبرة بمبالغ تجريبية غير حقيقية (Synthetic)؛ التعاملات المالية الحقيقية مش مفعّلة حاليًا ومشروطة بالمراجعات المطلوبة (Gated) |

السبب: Clarify synthetic amounts and gated real payments.

مخاطر المعنى: Low: wording clarified; English requirement and protected technical text preserved.

### EA-033 — MINOR

الملف: [docs/developer-handbook/01-business/pricing-and-payments-ar.md](../../docs/developer-handbook/01-business/pricing-and-payments-ar.md). القسم: # التسعير والدفع.

| قبل | بعد |
|---|---|
| **الفلوس الحقيقية متقفّلة.** | **التعاملات المالية الحقيقية مش مفعّلة حاليًا، وتفعيلها مشروط بالمراجعات المطلوبة.** |

السبب: State real-money gate clearly and retain following required reviews.

مخاطر المعنى: Low: wording clarified; English requirement and protected technical text preserved.

### EA-034 — MINOR

الملف: [docs/developer-handbook/01-business/pricing-and-payments-ar.md](../../docs/developer-handbook/01-business/pricing-and-payments-ar.md). القسم: ## 1. إزاي السعر بيتبني.

| قبل | بعد |
|---|---|
| فالـ Overflow بيفشل بصوت عالي | فلو حصل Overflow العملية بتفشل بخطأ واضح |

السبب: Fail loudly means explicit error, not sound.

مخاطر المعنى: Low: wording clarified; English requirement and protected technical text preserved.

### EA-035 — MAJOR

الملف: [docs/developer-handbook/01-business/pricing-and-payments-ar.md](../../docs/developer-handbook/01-business/pricing-and-payments-ar.md). القسم: ### إصدارات السعر والـ Snapshots.

| قبل | بعد |
|---|---|
| صف واحد لكل حساب فيه | صف واحد لكل عملية حساب للسعر، فيه |

السبب: Calculation is distinct from user account.

مخاطر المعنى: Low: wording clarified; English requirement and protected technical text preserved.

### EA-036 — MINOR

الملف: [docs/developer-handbook/01-business/pricing-and-payments-ar.md](../../docs/developer-handbook/01-business/pricing-and-payments-ar.md). القسم: ## 2. تغييرات السعر قبل التسجيل (قرار المالك D13).

| قبل | بعد |
|---|---|
| مفيش خصم صامت ولا إعادة إرسال صامتة | مفيش خصم ولا إعادة إرسال من غير علم العميل |

السبب: Natural explanation of silent resubmission prohibition.

مخاطر المعنى: Low: wording clarified; English requirement and protected technical text preserved.

### EA-037 — MAJOR

الملف: [docs/developer-handbook/01-business/pricing-and-payments-ar.md](../../docs/developer-handbook/01-business/pricing-and-payments-ar.md). القسم: ## 7. المطابقة والثوابت المالية.

| قبل | بعد |
|---|---|
| \| تقرير الكاش ≠ المتحقِّق \| | \| مقدّم تقرير الكاش ≠ المتحقِّق منه \| |

السبب: Separation of duties compares people, not report to person.

مخاطر المعنى: Low: wording clarified; English requirement and protected technical text preserved.

### EA-038 — MAJOR

الملف: [docs/developer-handbook/01-business/product-vision-ar.md](../../docs/developer-handbook/01-business/product-vision-ar.md). القسم: ## مقاييس النجاح (أهداف، مش نتايج).

| قبل | بعد |
|---|---|
| زي تحويل الـ Checkout | زي نسبة إتمام الطلبات في الـ Checkout |

السبب: Checkout conversion is a conversion rate, not transfer.

مخاطر المعنى: Low: wording clarified; English requirement and protected technical text preserved.

### EA-039 — MINOR

الملف: [docs/developer-handbook/01-business/target-users-ar.md](../../docs/developer-handbook/01-business/target-users-ar.md). القسم: ## القيود المعروفة وفجوات التوثيق.

| قبل | بعد |
|---|---|
| البلاطات التلاتة للنية | الاختيارات التلاتة حسب غرض الطلب |

السبب: Intent tiles are user choices, not literal tiles.

مخاطر المعنى: Low: wording clarified; English requirement and protected technical text preserved.

### EA-040 — MAJOR

الملف: [docs/developer-handbook/01-business/business-rules-ar.md](../../docs/developer-handbook/01-business/business-rules-ar.md). القسم: ## الـ Authorization وفصل الحسابات.

| قبل | بعد |
|---|---|
| الـ Objects الغريبة بترجع 404 | الـ Objects الخاصة بمستخدم أو مطبخ تاني بترجع 404 |

السبب: Foreign means ownership scope, not malformed object.

مخاطر المعنى: Low: wording clarified; English requirement and protected technical text preserved.

### EA-041 — MINOR

الملف: [docs/developer-handbook/01-business/business-rules-ar.md](../../docs/developer-handbook/01-business/business-rules-ar.md). القسم: ## الـ Authorization وفصل الحسابات.

| قبل | بعد |
|---|---|
| الصلاحيات الملغاة بتوقف شغل | الصلاحيات اللي اتسحبت بتبطل تشتغل |

السبب: Natural revoked-grant phrasing.

مخاطر المعنى: Low: wording clarified; English requirement and protected technical text preserved.

### EA-042 — MAJOR

الملف: [docs/developer-handbook/01-business/business-rules-ar.md](../../docs/developer-handbook/01-business/business-rules-ar.md). القسم: ## حجز السعة (Capacity).

| قبل | بعد |
|---|---|
| آخر وجبة مبتتباعش زيادة عن اللازم أبدًا | آخر حصة مينفعش تتباع لأكتر من عميل |

السبب: Capacity applies to portions, not meal types.

مخاطر المعنى: Low: wording clarified; English requirement and protected technical text preserved.

### EA-043 — MINOR

الملف: [docs/developer-handbook/02-user-journeys/authentication-and-mfa-ar.md](../../docs/developer-handbook/02-user-journeys/authentication-and-mfa-ar.md). القسم: # رحلات المستخدم: المصادقة وMFA.

| قبل | بعد |
|---|---|
| نص السياسة (هو المرجع في النيّة) | نص السياسة (هو المرجع للهدف المقصود) |

السبب: Intent is policy purpose, not personal intention.

مخاطر المعنى: Low: wording clarified; English requirement and protected technical text preserved.

### EA-044 — MINOR

الملف: [docs/developer-handbook/02-user-journeys/authentication-and-mfa-ar.md](../../docs/developer-handbook/02-user-journeys/authentication-and-mfa-ar.md). القسم: ## مكونات الهوية الأساسية.

| قبل | بعد |
|---|---|
| SSL مطلوب للخارج | SSL مطلوب للاتصالات الخارجية |

السبب: Clarify external connection requirement.

مخاطر المعنى: Low: wording clarified; English requirement and protected technical text preserved.

### EA-045 — MINOR

الملف: [docs/developer-handbook/02-user-journeys/backoffice-admin-ar.md](../../docs/developer-handbook/02-user-journeys/backoffice-admin-ar.md). القسم: ## مفيش "أدوار" للـ Staff، فيه صلاحيات ممنوحة بس.

| قبل | بعد |
|---|---|
| لسلطة Spring اسمها | لصلاحية Spring اسمها |

السبب: Use established authority context instead of literal power.

مخاطر المعنى: Low: wording clarified; English requirement and protected technical text preserved.

### EA-046 — MINOR

الملف: [docs/developer-handbook/02-user-journeys/complete-order-journey-ar.md](../../docs/developer-handbook/02-user-journeys/complete-order-journey-ar.md). القسم: # رحلات المستخدم: رحلة الأوردر الكاملة.

| قبل | بعد |
|---|---|
| هذه الصفحة بتربط | الصفحة دي بتربط |

السبب: Consistent professional Egyptian prose.

مخاطر المعنى: Low: wording clarified; English requirement and protected technical text preserved.

### EA-047 — MINOR

الملف: [docs/developer-handbook/02-user-journeys/complete-order-journey-ar.md](../../docs/developer-handbook/02-user-journeys/complete-order-journey-ar.md). القسم: ## السيناريو A: الاستلام، الدفع كاش عند الاستلام (الرحلة الأساسية 1).

| قبل | بعد |
|---|---|
| جهة اتصال مقنّعة | بيانات تواصل مخفية جزئيًا |

السبب: Masked contact is partially hidden data, not disguised contact.

مخاطر المعنى: Low: wording clarified; English requirement and protected technical text preserved.

### EA-048 — MINOR

الملف: [docs/developer-handbook/02-user-journeys/complete-order-journey-ar.md](../../docs/developer-handbook/02-user-journeys/complete-order-journey-ar.md). القسم: ## السيناريو A: الاستلام، الدفع كاش عند الاستلام (الرحلة الأساسية 1).

| قبل | بعد |
|---|---|
| \| 18 \| العميل \| المراجعة \| | \| 18 \| العميل \| التقييم \| |

السبب: Review here is customer rating, not editorial or operational review.

مخاطر المعنى: Low: wording clarified; English requirement and protected technical text preserved.

### EA-049 — MINOR

الملف: [docs/developer-handbook/02-user-journeys/complete-order-journey-ar.md](../../docs/developer-handbook/02-user-journeys/complete-order-journey-ar.md). القسم: ## السيناريو A: الاستلام، الدفع كاش عند الاستلام (الرحلة الأساسية 1).

| قبل | بعد |
|---|---|
| \| مراجعة واحدة \| | \| تقييم واحد \| |

السبب: Customer review terminology consistency.

مخاطر المعنى: Low: wording clarified; English requirement and protected technical text preserved.

### EA-050 — MINOR

الملف: [docs/developer-handbook/02-user-journeys/complete-order-journey-ar.md](../../docs/developer-handbook/02-user-journeys/complete-order-journey-ar.md). القسم: ## السيناريو C: عربون مباشر 50% (الرحلتين الأساسيتين 3 و4).

| قبل | بعد |
|---|---|
| الفلوس الحية مقفولة بشروط | التعاملات المالية الحقيقية مش مفعّلة حاليًا، وتفعيلها مشروط بالمراجعات المطلوبة |

السبب: Clarify real-money gate.

مخاطر المعنى: Low: wording clarified; English requirement and protected technical text preserved.

### EA-051 — MINOR

الملف: [docs/developer-handbook/02-user-journeys/cook-operations-ar.md](../../docs/developer-handbook/02-user-journeys/cook-operations-ar.md). القسم: ## CO-1 التسجيل: التقديم، الإرسال، انتظار القرار.

| قبل | بعد |
|---|---|
| علم الاستلام | إعداد إتاحة الاستلام |

السبب: Flag is a boolean setting, not a literal flag.

مخاطر المعنى: Low: wording clarified; English requirement and protected technical text preserved.

### EA-052 — MINOR

الملف: [docs/developer-handbook/02-user-journeys/cook-operations-ar.md](../../docs/developer-handbook/02-user-journeys/cook-operations-ar.md). القسم: ## CO-6 تقارير الكاش والعربون من المطبخ.

| قبل | بعد |
|---|---|
| الفلوس الحية مقفولة بشروط | التعاملات المالية الحقيقية مش مفعّلة حاليًا، وتفعيلها مشروط بالمراجعات المطلوبة |

السبب: Clarify real-money gate.

مخاطر المعنى: Low: wording clarified; English requirement and protected technical text preserved.

### EA-053 — MAJOR

الملف: [docs/developer-handbook/02-user-journeys/customer-mobile-ar.md](../../docs/developer-handbook/02-user-journeys/customer-mobile-ar.md). القسم: ### الـ Authentication والـ Redirect والـ Deep Links.

| قبل | بعد |
|---|---|
| والمزوّد غير المتاح بيحافظ عليها | ولو المزوّد مش متاح، التطبيق بيحتفظ بالـ Session |

السبب: Unreachable provider does not actively preserve session; app does.

مخاطر المعنى: Low: wording clarified; English requirement and protected technical text preserved.

### EA-054 — MINOR

الملف: [docs/developer-handbook/02-user-journeys/customer-mobile-ar.md](../../docs/developer-handbook/02-user-journeys/customer-mobile-ar.md). القسم: ### CM-3 عرض السعر، التسجيل، واسترجاع الأوردر غير المؤكد.

| قبل | بعد |
|---|---|
| حتى لو التطبيق مات | حتى لو التطبيق اتقفل فجأة |

السبب: Professional Egyptian description of app termination.

مخاطر المعنى: Low: wording clarified; English requirement and protected technical text preserved.

### EA-055 — MINOR

الملف: [docs/developer-handbook/02-user-journeys/customer-mobile-ar.md](../../docs/developer-handbook/02-user-journeys/customer-mobile-ar.md). القسم: ### CM-4 تتبّع الأوردر، تقرير العربون، الإلغاء، التقييم.

| قبل | بعد |
|---|---|
| Deep Link دافئ وبارد | Deep Link والتطبيق شغّال أو مقفول |

السبب: Warm/cold links describe application lifecycle, not temperature.

مخاطر المعنى: Low: wording clarified; English requirement and protected technical text preserved.

### EA-056 — MINOR

الملف: [docs/developer-handbook/02-user-journeys/customer-mobile-ar.md](../../docs/developer-handbook/02-user-journeys/customer-mobile-ar.md). القسم: ## أندرويد مقابل iOS.

| قبل | بعد |
|---|---|
| مُحترَم؛ بيمنع | بيتطبّق؛ وبيمنع |

السبب: Honoured option means applied, not respected.

مخاطر المعنى: Low: wording clarified; English requirement and protected technical text preserved.

### EA-057 — MINOR

الملف: [docs/developer-handbook/02-user-journeys/customer-web-ar.md](../../docs/developer-handbook/02-user-journeys/customer-web-ar.md). القسم: ## CW-3 اختيار الوجبة والسلة.

| قبل | بعد |
|---|---|
| الـ Slot المستنفد مايتضافش | الـ Slot اللي حصصه خلصت مايتضافش |

السبب: Natural exhausted-capacity wording.

مخاطر المعنى: Low: wording clarified; English requirement and protected technical text preserved.

### EA-058 — MINOR

الملف: [docs/developer-handbook/02-user-journeys/customer-web-ar.md](../../docs/developer-handbook/02-user-journeys/customer-web-ar.md). القسم: ## CW-4 عرض السعر، التحقق من التليفون، وتسجيل الأوردر.

| قبل | بعد |
|---|---|
| الفلوس الحقيقية مقفولة بشروط | التعاملات المالية الحقيقية مش مفعّلة حاليًا، وتفعيلها مشروط بالمراجعات المطلوبة |

السبب: Clarify real-money gate.

مخاطر المعنى: Low: wording clarified; English requirement and protected technical text preserved.

### EA-059 — MINOR

الملف: [docs/developer-handbook/02-user-journeys/customer-web-ar.md](../../docs/developer-handbook/02-user-journeys/customer-web-ar.md). القسم: ## CW-5 تتبّع الأوردر، تقرير الدفع، الإلغاء، والتقييم.

| قبل | بعد |
|---|---|
| تكرار تقرير التحويل Idempotent وإعلامي | تكرار تقرير التحويل Idempotent، وهو للإبلاغ بس |

السبب: Informational is not media-related.

مخاطر المعنى: Low: wording clarified; English requirement and protected technical text preserved.

### EA-060 — MINOR

الملف: [docs/developer-handbook/02-user-journeys/customer-web-ar.md](../../docs/developer-handbook/02-user-journeys/customer-web-ar.md). القسم: ## CW-5 تتبّع الأوردر، تقرير الدفع، الإلغاء، والتقييم.

| قبل | بعد |
|---|---|
| المراجعة التانية بتدي 409 | التقييم التاني بيرجّع 409 |

السبب: Review means customer rating here.

مخاطر المعنى: Low: wording clarified; English requirement and protected technical text preserved.

### EA-061 — MINOR

الملف: [docs/developer-handbook/02-user-journeys/customer-web-ar.md](../../docs/developer-handbook/02-user-journeys/customer-web-ar.md). القسم: ## CW-8 الأخطاء والاسترجاع (شامل).

| قبل | بعد |
|---|---|
| علم البناء | إعداد الـ Build |

السبب: Build flag is a configuration switch, not literal flag.

مخاطر المعنى: Low: wording clarified; English requirement and protected technical text preserved.

### EA-062 — MINOR

الملف: [docs/developer-handbook/02-user-journeys/finance-and-support-ar.md](../../docs/developer-handbook/02-user-journeys/finance-and-support-ar.md). القسم: # رحلات المستخدم: المالية والدعم.

| قبل | بعد |
|---|---|
| كل الفلوس بيانات اصطناعية (Synthetic) والفلوس الحية مقفولة بشروط | كل المبالغ تجريبية وغير حقيقية (Synthetic)، والتعاملات المالية الحقيقية مش مفعّلة حاليًا وتفعيلها مشروط بالمراجعات المطلوبة |

السبب: Clarify synthetic money and real-money gate.

مخاطر المعنى: Low: wording clarified; English requirement and protected technical text preserved.

### EA-063 — MINOR

الملف: [docs/developer-handbook/02-user-journeys/finance-and-support-ar.md](../../docs/developer-handbook/02-user-journeys/finance-and-support-ar.md). القسم: # رحلات المستخدم: المالية والدعم.

| قبل | بعد |
|---|---|
| والطبّاخ والعميل لنصفهم هم | والطبّاخ والعميل كل واحد في الجزء الخاص بيه |

السبب: Natural explanation of each actor’s part.

مخاطر المعنى: Low: wording clarified; English requirement and protected technical text preserved.

### EA-064 — MAJOR

الملف: [docs/developer-handbook/02-user-journeys/finance-and-support-ar.md](../../docs/developer-handbook/02-user-journeys/finance-and-support-ar.md). القسم: ## FS-3 الاستردادات والتسويات وكشوف الحساب (المالية).

| قبل | بعد |
|---|---|
| تسجيل استرداد المطبخ للعميل بالكامل للكاش المتحقق منه والمحصّل عند المطبخ | تسجيل ردّ المطبخ للعميل مبلغ الكاش بالكامل، بعد التحقق من تحصيل المطبخ ليه |

السبب: Clarify kitchen-to-customer refund direction without implying kitchen receives refund.

مخاطر المعنى: Low: wording clarified; English requirement and protected technical text preserved.

### EA-065 — MINOR

الملف: [docs/developer-handbook/02-user-journeys/finance-and-support-ar.md](../../docs/developer-handbook/02-user-journeys/finance-and-support-ar.md). القسم: ## FS-3 الاستردادات والتسويات وكشوف الحساب (المالية).

| قبل | بعد |
|---|---|
| القيود ذات المبلغ صفر مايتحطش في دفعات | القيود اللي مبلغها صفر ماينفعش تتحط في دفعات |

السبب: Fix plural agreement and readability.

مخاطر المعنى: Low: wording clarified; English requirement and protected technical text preserved.

### EA-066 — MAJOR

الملف: [docs/developer-handbook/01-business/actors-and-permissions-ar.md](../../docs/developer-handbook/01-business/actors-and-permissions-ar.md). القسم: ## الـ Authorization على مستوى الـ Object (الملكية والعزل بين المطابخ).

| قبل | بعد |
|---|---|
| فالسجل الغريب ببساطة مش موجود | فالسجل الخاص بحد تاني مش موجود |

السبب: Clarify foreign-record ownership scope.

مخاطر المعنى: Low: wording clarified; English requirement and protected technical text preserved.

### EA-067 — MAJOR

الملف: [docs/developer-handbook/01-business/actors-and-permissions-ar.md](../../docs/developer-handbook/01-business/actors-and-permissions-ar.md). القسم: ## الـ Authorization على مستوى الـ Object (الملكية والعزل بين المطابخ).

| قبل | بعد |
|---|---|
| المعرّف الغريب بيرجّع | معرّف السجل الخاص بحد تاني بيرجّع |

السبب: Clarify cross-owner id rather than malformed id.

مخاطر المعنى: Low: wording clarified; English requirement and protected technical text preserved.

### EA-068 — MINOR

الملف: [docs/developer-handbook/01-business/actors-and-permissions-ar.md](../../docs/developer-handbook/01-business/actors-and-permissions-ar.md). القسم: ## القيود المعروفة وفجوات التوثيق.

| قبل | بعد |
|---|---|
| جدول أدوار خشن | جدول عام للأدوار من غير تفاصيل الصلاحيات |

السبب: Coarse means broad-grained, not rough texture.

مخاطر المعنى: Low: wording clarified; English requirement and protected technical text preserved.

### EA-069 — MINOR

الملف: [docs/developer-handbook/00-getting-started/repository-navigation-ar.md](../../docs/developer-handbook/00-getting-started/repository-navigation-ar.md). القسم: ## 5. الشاشات.

| قبل | بعد |
|---|---|
| \| السطح \| الملفات \| | \| الواجهة \| الملفات \| |

السبب: Use application-interface terminology.

مخاطر المعنى: Low: wording clarified; English requirement and protected technical text preserved.

### EA-070 — MINOR

الملف: [docs/developer-handbook/00-getting-started/repository-navigation-ar.md](../../docs/developer-handbook/00-getting-started/repository-navigation-ar.md). القسم: ## 7. "أنفّذ تغييري فين؟".

| قبل | بعد |
|---|---|
| \| التغيير \| ابدأ من هنا \| لمس كمان \| | \| التغيير \| ابدأ من هنا \| حدّث كمان \| |

السبب: Natural wording for files also affected.

مخاطر المعنى: Low: wording clarified; English requirement and protected technical text preserved.

### EA-071 — MINOR

الملف: [docs/developer-handbook/01-business/target-users-ar.md](../../docs/developer-handbook/01-business/target-users-ar.md). القسم: ## شرائح العملاء.

| قبل | بعد |
|---|---|
| تلات بلاطات "نية" | تلات اختيارات حسب غرض الطلب |

السبب: Avoid literal intent-tile terminology.

مخاطر المعنى: Low: wording clarified; English requirement and protected technical text preserved.

### EA-072 — MINOR

الملف: [docs/developer-handbook/01-business/target-users-ar.md](../../docs/developer-handbook/01-business/target-users-ar.md). القسم: ## شرائح العملاء.

| قبل | بعد |
|---|---|
| بلاطة النية (النص بالإنجليزي) | اختيار غرض الطلب (النص بالإنجليزي) |

السبب: Use consistent natural intent terminology.

مخاطر المعنى: Low: wording clarified; English requirement and protected technical text preserved.

### EA-073 — MINOR

الملف: [docs/developer-handbook/01-business/target-users-ar.md](../../docs/developer-handbook/01-business/target-users-ar.md). القسم: ## شرائح العملاء.

| قبل | بعد |
|---|---|
| البلاطات التلاتة **تلميح عرض** | الاختيارات التلاتة **بتوجّه طريقة العرض بس** |

السبب: Clarify presentation-only behaviour without implying server filtering.

مخاطر المعنى: Low: wording clarified; English requirement and protected technical text preserved.

### EA-074 — MINOR

الملف: [docs/developer-handbook/02-user-journeys/backoffice-admin-ar.md](../../docs/developer-handbook/02-user-journeys/backoffice-admin-ar.md). القسم: ## BO-3 متابعة الأوردرات والاستثناءات.

| قبل | بعد |
|---|---|
| جهة اتصال مقنّعة | بيانات تواصل مخفية جزئيًا |

السبب: Masked contact means partially hidden personal data.

مخاطر المعنى: Low: wording clarified; English requirement and protected technical text preserved.

### EA-075 — MINOR

الملف: [docs/developer-handbook/02-user-journeys/backoffice-admin-ar.md](../../docs/developer-handbook/02-user-journeys/backoffice-admin-ar.md). القسم: ## BO-3 متابعة الأوردرات والاستثناءات.

| قبل | بعد |
|---|---|
| وفتح جهة اتصال العميل | وفتح بيانات تواصل العميل |

السبب: Use accurate contact-data wording.

مخاطر المعنى: Low: wording clarified; English requirement and protected technical text preserved.

### EA-076 — MINOR

الملف: [docs/developer-handbook/01-business/business-rules-ar.md](../../docs/developer-handbook/01-business/business-rules-ar.md). القسم: # ملخص قواعد العمل.

| قبل | بعد |
|---|---|
| في صيغة مقروءة للآلة | في صيغة قابلة للقراءة آليًا |

السبب: Natural machine-readable terminology.

مخاطر المعنى: Low: wording clarified; English requirement and protected technical text preserved.

### EA-077 — MINOR

الملف: [docs/developer-handbook/01-business/business-rules-ar.md](../../docs/developer-handbook/01-business/business-rules-ar.md). القسم: ## الـ Authentication.

| قبل | بعد |
|---|---|
| رقم تليفون مصري متطبَّع ومتحقَّق منه | رقم تليفون مصري بصيغة موحّدة ومتحقَّق منه |

السبب: Normalized phone means standard format, not literal normalization.

مخاطر المعنى: Low: wording clarified; English requirement and protected technical text preserved.

### EA-078 — MINOR

الملف: [docs/developer-handbook/02-user-journeys/customer-web-ar.md](../../docs/developer-handbook/02-user-journeys/customer-web-ar.md). القسم: ## المسارات الموجودة.

| قبل | بعد |
|---|---|
| كروت النية | اختيارات غرض الطلب |

السبب: Natural intent choices terminology.

مخاطر المعنى: Low: wording clarified; English requirement and protected technical text preserved.

### EA-079 — MINOR

الملف: [docs/developer-handbook/02-user-journeys/customer-web-ar.md](../../docs/developer-handbook/02-user-journeys/customer-web-ar.md). القسم: ## CW-1 اكتشاف الزائر، البحث، والفلترة.

| قبل | بعد |
|---|---|
| كارت نية اختياريًا | اختيار غرض الطلب اختياريًا |

السبب: Natural intent selection wording.

مخاطر المعنى: Low: wording clarified; English requirement and protected technical text preserved.

### EA-080 — MAJOR

الملف: [docs/developer-handbook/02-user-journeys/customer-web-ar.md](../../docs/developer-handbook/02-user-journeys/customer-web-ar.md). القسم: ## CW-1 اكتشاف الزائر، البحث، والفلترة.

| قبل | بعد |
|---|---|
| عدّ المشاهدات المجهولة، أرسل وانسَ | عدّ مرات الظهور من غير تحديد هوية الزائر، وإرسال الطلب من غير انتظار نتيجته |

السبب: Impressions differ from views; explain fire-and-forget precisely.

مخاطر المعنى: Low: wording clarified; English requirement and protected technical text preserved.

### EA-081 — MINOR

الملف: [docs/developer-handbook/03-architecture/backend-architecture-ar.md](../../docs/developer-handbook/03-architecture/backend-architecture-ar.md). القسم: # معمارية الـ Backend.

| قبل | بعد |
|---|---|
| تسع موديولات | تسعة موديولات |

السبب: صياغة مصرية أوضح وأدق مع الحفاظ على معنى المصدر الإنجليزي.

مخاطر المعنى: لا تغيير في المتطلبات أو السلوك التقني.

### EA-082 — MINOR

الملف: [docs/developer-handbook/03-architecture/backend-architecture-ar.md](../../docs/developer-handbook/03-architecture/backend-architecture-ar.md). القسم: ## الطبقات والوصول للبيانات.

| قبل | بعد |
|---|---|
| رفيع: بيربط | مسؤوليته محدودة: بيربط |

السبب: Thin Controller معناها إن مسؤوليته محدودة، مش وصف حجمه.

مخاطر المعنى: لا تغيير في المتطلبات أو السلوك التقني.

### EA-083 — MINOR

الملف: [docs/developer-handbook/03-architecture/backend-architecture-ar.md](../../docs/developer-handbook/03-architecture/backend-architecture-ar.md). القسم: ## الطبقات والوصول للبيانات.

| قبل | بعد |
|---|---|
| SQL مكتوب بإيد | SQL مكتوب يدويًا |

السبب: صياغة مصرية أوضح وأدق مع الحفاظ على معنى المصدر الإنجليزي.

مخاطر المعنى: لا تغيير في المتطلبات أو السلوك التقني.

### EA-084 — MAJOR

الملف: [docs/developer-handbook/03-architecture/backend-architecture-ar.md](../../docs/developer-handbook/03-architecture/backend-architecture-ar.md). القسم: ## تركيب الـ Maven.

| قبل | بعد |
|---|---|
| فوق الـ Boot BOM كـ Overrides لتحذيرات الاعتماديات (Dependency advisory) | متجاوزًا إصدارات الـ Boot BOM لمعالجة التنبيهات الأمنية الخاصة بالاعتماديات (Dependency advisories) |

السبب: Above the BOM يوضح تجاوز الإصدارات المُدارة استجابةً لتنبيهات أمنية؛ الصياغة القديمة مبهمة.

مخاطر المعنى: لا تغيير في المتطلبات أو السلوك التقني.

### EA-085 — MINOR

الملف: [docs/developer-handbook/03-architecture/database-architecture-ar.md](../../docs/developer-handbook/03-architecture/database-architecture-ar.md). القسم: # معمارية قاعدة البيانات.

| قبل | بعد |
|---|---|
| SQL مكتوب بإيد | SQL مكتوب يدويًا |

السبب: صياغة مصرية أوضح وأدق مع الحفاظ على معنى المصدر الإنجليزي.

مخاطر المعنى: لا تغيير في المتطلبات أو السلوك التقني.

### EA-086 — MINOR

الملف: [docs/developer-handbook/03-architecture/database-architecture-ar.md](../../docs/developer-handbook/03-architecture/database-architecture-ar.md). القسم: ## الـ Migrations.

| قبل | بعد |
|---|---|
| البيانات الاصطناعية (Synthetic) للتطوير | البيانات التجريبية غير الحقيقية (Synthetic Data) المستخدمة في التطوير |

السبب: صياغة مصرية أوضح وأدق مع الحفاظ على معنى المصدر الإنجليزي.

مخاطر المعنى: لا تغيير في المتطلبات أو السلوك التقني.

### EA-087 — MINOR

الملف: [docs/developer-handbook/03-architecture/database-architecture-ar.md](../../docs/developer-handbook/03-architecture/database-architecture-ar.md). القسم: # معمارية قاعدة البيانات.

| قبل | بعد |
|---|---|
| الـ Schema مملوكة لـ Flyway SQL migrations | بنية الـ Database Schema بتتحدد من خلال Flyway SQL migrations |

السبب: صياغة مصرية أوضح وأدق مع الحفاظ على معنى المصدر الإنجليزي.

مخاطر المعنى: لا تغيير في المتطلبات أو السلوك التقني.

### EA-088 — MINOR

الملف: [docs/developer-handbook/03-architecture/database-architecture-ar.md](../../docs/developer-handbook/03-architecture/database-architecture-ar.md). القسم: ## الـ Migrations.

| قبل | بعد |
|---|---|
| صاحب القرار في حالة الرفع | المرجع المعتمد لحالة الرفع |

السبب: state authority وصف لمصدر الحالة المعتمد، مش لطرف بياخد قرار.

مخاطر المعنى: لا تغيير في المتطلبات أو السلوك التقني.

### EA-089 — MINOR

الملف: [docs/developer-handbook/03-architecture/database-architecture-ar.md](../../docs/developer-handbook/03-architecture/database-architecture-ar.md). القسم: ## الـ Migrations.

| قبل | بعد |
|---|---|
| السياسات الحيّة | السياسات الفعلية |

السبب: صياغة مصرية أوضح وأدق مع الحفاظ على معنى المصدر الإنجليزي.

مخاطر المعنى: لا تغيير في المتطلبات أو السلوك التقني.

### EA-090 — MINOR

الملف: [docs/developer-handbook/03-architecture/database-architecture-ar.md](../../docs/developer-handbook/03-architecture/database-architecture-ar.md). القسم: ## الكيانات الأساسية والعلاقات.

| قبل | بعد |
|---|---|
| مبالغ الـ Ledger الحيّة صفر والتسوية خامدة | مبالغ الـ Ledger للمعاملات الفعلية صفر والتسوية غير مفعّلة |

السبب: صياغة مصرية أوضح وأدق مع الحفاظ على معنى المصدر الإنجليزي.

مخاطر المعنى: لا تغيير في المتطلبات أو السلوك التقني.

### EA-091 — MINOR

الملف: [docs/developer-handbook/03-architecture/database-architecture-ar.md](../../docs/developer-handbook/03-architecture/database-architecture-ar.md). القسم: ## الـ Indexes اللي تستاهل تعرفها.

| قبل | بعد |
|---|---|
| المسارات الساخنة | المسارات الأكثر استخدامًا (Hot paths) |

السبب: صياغة مصرية أوضح وأدق مع الحفاظ على معنى المصدر الإنجليزي.

مخاطر المعنى: لا تغيير في المتطلبات أو السلوك التقني.

### EA-092 — MINOR

الملف: [docs/developer-handbook/03-architecture/frontend-architecture-ar.md](../../docs/developer-handbook/03-architecture/frontend-architecture-ar.md). القسم: ## الحزم المشتركة.

| قبل | بعد |
|---|---|
| اللوحة اللونية والطباعة | الألوان والخطوط (Typography) |

السبب: صياغة مصرية أوضح وأدق مع الحفاظ على معنى المصدر الإنجليزي.

مخاطر المعنى: لا تغيير في المتطلبات أو السلوك التقني.

### EA-093 — MINOR

الملف: [docs/developer-handbook/03-architecture/frontend-architecture-ar.md](../../docs/developer-handbook/03-architecture/frontend-architecture-ar.md). القسم: ## طبقة الوصول للـ API.

| قبل | بعد |
|---|---|
| عند حافة العرض بس | وقت العرض بس |

السبب: صياغة مصرية أوضح وأدق مع الحفاظ على معنى المصدر الإنجليزي.

مخاطر المعنى: لا تغيير في المتطلبات أو السلوك التقني.

### EA-094 — MINOR

الملف: [docs/developer-handbook/03-architecture/frontend-architecture-ar.md](../../docs/developer-handbook/03-architecture/frontend-architecture-ar.md). القسم: ## ويب العميل (`apps/customer-web`).

| قبل | بعد |
|---|---|
| مربعات النيّة (intent tiles) | مربعات اختيارات المستخدم (intent tiles) |

السبب: صياغة مصرية أوضح وأدق مع الحفاظ على معنى المصدر الإنجليزي.

مخاطر المعنى: لا تغيير في المتطلبات أو السلوك التقني.

### EA-095 — MAJOR

الملف: [docs/developer-handbook/03-architecture/frontend-architecture-ar.md](../../docs/developer-handbook/03-architecture/frontend-architecture-ar.md). القسم: ## ويب العميل (`apps/customer-web`).

| قبل | بعد |
|---|---|
| أوردر مش متأكد | أوردر لسه تسجيله مش مؤكّد |

السبب: unconfirmed order هنا محاولة تسجيل نتيجتها غير مؤكدة، مش حالة قبول الأوردر من المطبخ.

مخاطر المعنى: لا تغيير في المتطلبات أو السلوك التقني.

### EA-096 — MINOR

الملف: [docs/developer-handbook/03-architecture/frontend-architecture-ar.md](../../docs/developer-handbook/03-architecture/frontend-architecture-ar.md). القسم: ## القيود المعروفة وفجوات التوثيق.

| قبل | بعد |
|---|---|
| ده بسيط بس كتير الكلام | ده بسيط بس بيولّد طلبات شبكة كتير |

السبب: صياغة مصرية أوضح وأدق مع الحفاظ على معنى المصدر الإنجليزي.

مخاطر المعنى: لا تغيير في المتطلبات أو السلوك التقني.

### EA-097 — MAJOR

الملف: [docs/developer-handbook/03-architecture/identity-and-security-ar.md](../../docs/developer-handbook/03-architecture/identity-and-security-ar.md). القسم: ## المبادئ (زي ما هي متنفّذة).

| قبل | بعد |
|---|---|
| للكائنات الغريبة | للموارد اللي تخص حسابات تانية |

السبب: foreign objects في Object Authorization معناها موارد حسابات تانية؛ غريبة مبهمة.

مخاطر المعنى: لا تغيير في المتطلبات أو السلوك التقني.

### EA-098 — MINOR

الملف: [docs/developer-handbook/03-architecture/identity-and-security-ar.md](../../docs/developer-handbook/03-architecture/identity-and-security-ar.md). القسم: ## الـ Claims المستخدمة من الـ Token.

| قبل | بعد |
|---|---|
| Claims الضمان | Claims مستوى التحقق (Assurance) |

السبب: صياغة مصرية أوضح وأدق مع الحفاظ على معنى المصدر الإنجليزي.

مخاطر المعنى: لا تغيير في المتطلبات أو السلوك التقني.

### EA-099 — MINOR

الملف: [docs/developer-handbook/03-architecture/identity-and-security-ar.md](../../docs/developer-handbook/03-architecture/identity-and-security-ar.md). القسم: ## مسارين للـ Authentication.

| قبل | بعد |
|---|---|
| بأقصى جهد | بأفضل محاولة ممكنة (best effort) |

السبب: صياغة مصرية أوضح وأدق مع الحفاظ على معنى المصدر الإنجليزي.

مخاطر المعنى: لا تغيير في المتطلبات أو السلوك التقني.

### EA-100 — MINOR

الملف: [docs/developer-handbook/03-architecture/identity-and-security-ar.md](../../docs/developer-handbook/03-architecture/identity-and-security-ar.md). القسم: ## سياسة الـ MFA: التخزين والفرض.

| قبل | بعد |
|---|---|
| بأقصى جهد | بأفضل محاولة ممكنة (best effort) |

السبب: صياغة مصرية أوضح وأدق مع الحفاظ على معنى المصدر الإنجليزي.

مخاطر المعنى: لا تغيير في المتطلبات أو السلوك التقني.

### EA-101 — MINOR

الملف: [docs/developer-handbook/03-architecture/identity-and-security-ar.md](../../docs/developer-handbook/03-architecture/identity-and-security-ar.md). القسم: ## سياسة الـ MFA: التخزين والفرض.

| قبل | بعد |
|---|---|
| بتفشل بشكل مقفول (fail closed) | بترفض العملية لو المتطلبات مش متاحة (fail closed) |

السبب: صياغة مصرية أوضح وأدق مع الحفاظ على معنى المصدر الإنجليزي.

مخاطر المعنى: لا تغيير في المتطلبات أو السلوك التقني.

### EA-102 — MAJOR

الملف: [docs/developer-handbook/03-architecture/identity-and-security-ar.md](../../docs/developer-handbook/03-architecture/identity-and-security-ar.md). القسم: ## ضوابط أمان تانية تستاهل تعرفها.

| قبل | بعد |
|---|---|
| بوابة فحص بتفشل بشكل مقفول | بوابة فحص بترفض إتاحة الملف لو الفحص مش متاح أو مش ناجح (fail closed) |

السبب: توضيح المعنى الأمني لـ fail closed من غير تخفيف شرط الفحص.

مخاطر المعنى: لا تغيير في المتطلبات أو السلوك التقني.

### EA-103 — MINOR

الملف: [docs/developer-handbook/03-architecture/identity-and-security-ar.md](../../docs/developer-handbook/03-architecture/identity-and-security-ar.md). القسم: ## تأمين المتصفح والـ Proxy.

| قبل | بعد |
|---|---|
| السكريبتات والستايلات من النفس بس | السكريبتات والستايلات من نفس الـ Origin بس |

السبب: صياغة مصرية أوضح وأدق مع الحفاظ على معنى المصدر الإنجليزي.

مخاطر المعنى: لا تغيير في المتطلبات أو السلوك التقني.

### EA-104 — MAJOR

الملف: [docs/developer-handbook/03-architecture/identity-and-security-ar.md](../../docs/developer-handbook/03-architecture/identity-and-security-ar.md). القسم: ## تأمين المتصفح والـ Proxy.

| قبل | بعد |
|---|---|
| والصور من النفس و | والصور من نفس الـ Origin و |

السبب: self في CSP معناها نفس الـ Origin، مش ترجمة حرفية للنفس.

مخاطر المعنى: لا تغيير في المتطلبات أو السلوك التقني.

### EA-105 — MAJOR

الملف: [docs/developer-handbook/03-architecture/identity-and-security-ar.md](../../docs/developer-handbook/03-architecture/identity-and-security-ar.md). القسم: ## تأمين المتصفح والـ Proxy.

| قبل | بعد |
|---|---|
| و`connect-src` النفس وHost الـ API | و`connect-src` بيسمح بنفس الـ Origin وHost الـ API |

السبب: توضيح مصادر الاتصالات المسموحة في CSP.

مخاطر المعنى: لا تغيير في المتطلبات أو السلوك التقني.

### EA-106 — MINOR

الملف: [docs/developer-handbook/03-architecture/identity-and-security-ar.md](../../docs/developer-handbook/03-architecture/identity-and-security-ar.md). القسم: ## الـ Sessions.

| قبل | بعد |
|---|---|
| Sessions الويب هي صفوف في `SPRING_SESSION*` (V2)، فبتعدّي إعادة تشغيل الـ API وتعدد الـ Instances. | Sessions الويب هي صفوف في `SPRING_SESSION*` (V2)، فبتفضل محفوظة بعد إعادة تشغيل الـ API وبتشتغل مع أكتر من Instance. |

السبب: صياغة مصرية أوضح وأدق مع الحفاظ على معنى المصدر الإنجليزي.

مخاطر المعنى: لا تغيير في المتطلبات أو السلوك التقني.

### EA-107 — MINOR

الملف: [docs/developer-handbook/03-architecture/infrastructure-and-deployment-ar.md](../../docs/developer-handbook/03-architecture/infrastructure-and-deployment-ar.md). القسم: # البنية التحتية والـ Deployment.

| قبل | بعد |
|---|---|
| ببيانات اصطناعية (Synthetic) | ببيانات تجريبية غير حقيقية (Synthetic Data) |

السبب: صياغة مصرية أوضح وأدق مع الحفاظ على معنى المصدر الإنجليزي.

مخاطر المعنى: لا تغيير في المتطلبات أو السلوك التقني.

### EA-108 — MINOR

الملف: [docs/developer-handbook/03-architecture/infrastructure-and-deployment-ar.md](../../docs/developer-handbook/03-architecture/infrastructure-and-deployment-ar.md). القسم: ## البيئات في نظرة سريعة.

| قبل | بعد |
|---|---|
| Fixtures اصطناعية | Fixtures ببيانات تجريبية غير حقيقية |

السبب: صياغة مصرية أوضح وأدق مع الحفاظ على معنى المصدر الإنجليزي.

مخاطر المعنى: لا تغيير في المتطلبات أو السلوك التقني.

### EA-109 — MINOR

الملف: [docs/developer-handbook/03-architecture/infrastructure-and-deployment-ar.md](../../docs/developer-handbook/03-architecture/infrastructure-and-deployment-ar.md). القسم: ## البيئات في نظرة سريعة.

| قبل | بعد |
|---|---|
| اصطناعية بس، وعربون محاكى | بيانات تجريبية غير حقيقية بس، وعربون محاكى |

السبب: صياغة مصرية أوضح وأدق مع الحفاظ على معنى المصدر الإنجليزي.

مخاطر المعنى: لا تغيير في المتطلبات أو السلوك التقني.

### EA-110 — MAJOR

الملف: [docs/developer-handbook/03-architecture/infrastructure-and-deployment-ar.md](../../docs/developer-handbook/03-architecture/infrastructure-and-deployment-ar.md). القسم: ## النسخ الاحتياطي والاسترجاع والـ Rollback.

| قبل | بعد |
|---|---|
| لو نسخة الـ Schema ماتحركتش، الـ Rollback للصور بس آمن | لو إصدار الـ Database Schema متغيّرش، الرجوع لإصدار Docker Images سابق بس آمن |

السبب: تمييز Docker Images عن صور الميديا وتوضيح معيار إصدار Schema من المصدر.

مخاطر المعنى: لا تغيير في المتطلبات أو السلوك التقني.

### EA-111 — MAJOR

الملف: [docs/developer-handbook/03-architecture/infrastructure-and-deployment-ar.md](../../docs/developer-handbook/03-architecture/infrastructure-and-deployment-ar.md). القسم: ## النسخ الاحتياطي والاسترجاع والـ Rollback.

| قبل | بعد |
|---|---|
| ولو اتحركت، الـ Rollback للصور بس آمن بس لو | ولو إصدارها اتغيّر، الرجوع لإصدار Docker Images سابق بس آمن بشرط إن |

السبب: الإبقاء على شرط expand-only لكل Migration والتمييز عن Restore للبيانات.

مخاطر المعنى: لا تغيير في المتطلبات أو السلوك التقني.

### EA-112 — MAJOR

الملف: [docs/developer-handbook/03-architecture/infrastructure-and-deployment-ar.md](../../docs/developer-handbook/03-architecture/infrastructure-and-deployment-ar.md). القسم: ## حدود جاهزية الـ Production (إيه اللي مش موجود).

| قبل | بعد |
|---|---|
| الفلوس الحقيقية مقفولة بالامتثال | التعاملات المالية الحقيقية مش مفعّلة حاليًا، وتفعيلها مشروط باستيفاء متطلبات الامتثال |

السبب: توضيح إن التفعيل ممنوع قبل متطلبات الامتثال، بدل التعبير المبهم عن قفل الفلوس.

مخاطر المعنى: لا تغيير في المتطلبات أو السلوك التقني.

### EA-113 — MINOR

الملف: [docs/developer-handbook/03-architecture/infrastructure-and-deployment-ar.md](../../docs/developer-handbook/03-architecture/infrastructure-and-deployment-ar.md). القسم: ## القيود المعروفة وفجوات التوثيق.

| قبل | بعد |
|---|---|
| بيئة اصطناعية | بيئة بيانات تجريبية غير حقيقية |

السبب: صياغة مصرية أوضح وأدق مع الحفاظ على معنى المصدر الإنجليزي.

مخاطر المعنى: لا تغيير في المتطلبات أو السلوك التقني.

### EA-114 — MINOR

الملف: [docs/developer-handbook/03-architecture/integration-and-events-ar.md](../../docs/developer-handbook/03-architecture/integration-and-events-ar.md). القسم: ### التسليم: `DeliveryWorker`.

| قبل | بعد |
|---|---|
| كل إرسال بيشيل | كل إرسال بيحمل |

السبب: صياغة مصرية أوضح وأدق مع الحفاظ على معنى المصدر الإنجليزي.

مخاطر المعنى: لا تغيير في المتطلبات أو السلوك التقني.

### EA-115 — MINOR

الملف: [docs/developer-handbook/03-architecture/integration-and-events-ar.md](../../docs/developer-handbook/03-architecture/integration-and-events-ar.md). القسم: ### تشغيل الـ Outbox.

| قبل | بعد |
|---|---|
| التسليمات الميتة | عمليات التسليم اللي استنفدت محاولاتها (Dead deliveries) |

السبب: صياغة مصرية أوضح وأدق مع الحفاظ على معنى المصدر الإنجليزي.

مخاطر المعنى: لا تغيير في المتطلبات أو السلوك التقني.

### EA-116 — MINOR

الملف: [docs/developer-handbook/03-architecture/integration-and-events-ar.md](../../docs/developer-handbook/03-architecture/integration-and-events-ar.md). القسم: ## الأنظمة الخارجية اللي الـ Backend بينادي عليها.

| قبل | بعد |
|---|---|
| الفلوس الحقيقية مقفولة بقرار المالك D16 | التعاملات المالية الحقيقية مش مفعّلة حاليًا، وتفعيلها مشروط بقرار المالك D16 |

السبب: صياغة مصرية أوضح وأدق مع الحفاظ على معنى المصدر الإنجليزي.

مخاطر المعنى: لا تغيير في المتطلبات أو السلوك التقني.

### EA-117 — MAJOR

الملف: [docs/developer-handbook/03-architecture/integration-and-events-ar.md](../../docs/developer-handbook/03-architecture/integration-and-events-ar.md). القسم: ## الأنظمة الخارجية اللي الـ Backend بينادي عليها.

| قبل | بعد |
|---|---|
| بتفشل بشكل مقفول | بترفض إتاحة الملف لو الفحص مش متاح أو مش ناجح (fail closed) |

السبب: توضيح ضابط malware fail closed، مع بقاء منع إتاحة الملف.

مخاطر المعنى: لا تغيير في المتطلبات أو السلوك التقني.

### EA-118 — MINOR

الملف: [docs/developer-handbook/03-architecture/mobile-architecture-ar.md](../../docs/developer-handbook/03-architecture/mobile-architecture-ar.md). القسم: ## طبقة الـ State والـ Session والشبكة.

| قبل | بعد |
|---|---|
| وعلم الـ Step-up | وعلامة الـ Step-up (Flag) |

السبب: صياغة مصرية أوضح وأدق مع الحفاظ على معنى المصدر الإنجليزي.

مخاطر المعنى: لا تغيير في المتطلبات أو السلوك التقني.

### EA-119 — MINOR

الملف: [docs/developer-handbook/03-architecture/mobile-architecture-ar.md](../../docs/developer-handbook/03-architecture/mobile-architecture-ar.md). القسم: ## إشعارات الـ Push.

| قبل | بعد |
|---|---|
| وعلم الـ Push | وإعداد تفعيل الـ Push (Flag) |

السبب: صياغة مصرية أوضح وأدق مع الحفاظ على معنى المصدر الإنجليزي.

مخاطر المعنى: لا تغيير في المتطلبات أو السلوك التقني.

### EA-120 — MINOR

الملف: [docs/developer-handbook/03-architecture/mobile-architecture-ar.md](../../docs/developer-handbook/03-architecture/mobile-architecture-ar.md). القسم: ## الكود المشترك مقابل الكود الخاص بكل منصة.

| قبل | بعد |
|---|---|
| SQL للـ Seed الاصطناعي (Synthetic) | SQL لبيانات التهيئة التجريبية غير الحقيقية (Synthetic Data) |

السبب: صياغة مصرية أوضح وأدق مع الحفاظ على معنى المصدر الإنجليزي.

مخاطر المعنى: لا تغيير في المتطلبات أو السلوك التقني.

### EA-121 — MINOR

الملف: [docs/developer-handbook/03-architecture/mobile-architecture-ar.md](../../docs/developer-handbook/03-architecture/mobile-architecture-ar.md). القسم: ## طبقة الـ State والـ Session والشبكة.

| قبل | بعد |
|---|---|
| فشل النقل (Transport) فبيخليها | فشل الاتصال (Transport) فبيسيب الـ Session شغّالة |

السبب: صياغة مصرية أوضح وأدق مع الحفاظ على معنى المصدر الإنجليزي.

مخاطر المعنى: لا تغيير في المتطلبات أو السلوك التقني.

### EA-122 — MINOR

الملف: [docs/developer-handbook/03-architecture/system-overview-ar.md](../../docs/developer-handbook/03-architecture/system-overview-ar.md). القسم: # نظرة عامة على السيستم.

| قبل | بعد |
|---|---|
| ببيانات اصطناعية (Synthetic) | ببيانات تجريبية غير حقيقية (Synthetic Data) |

السبب: صياغة مصرية أوضح وأدق مع الحفاظ على معنى المصدر الإنجليزي.

مخاطر المعنى: لا تغيير في المتطلبات أو السلوك التقني.

### EA-123 — MINOR

الملف: [docs/developer-handbook/03-architecture/system-overview-ar.md](../../docs/developer-handbook/03-architecture/system-overview-ar.md). القسم: ## البيئات.

| قبل | بعد |
|---|---|
| المعاينة المحدودة الوصول ببيانات اصطناعية | المعاينة المحدودة الوصول ببيانات تجريبية غير حقيقية |

السبب: صياغة مصرية أوضح وأدق مع الحفاظ على معنى المصدر الإنجليزي.

مخاطر المعنى: لا تغيير في المتطلبات أو السلوك التقني.

### EA-124 — MINOR

الملف: [docs/developer-handbook/03-architecture/system-overview-ar.md](../../docs/developer-handbook/03-architecture/system-overview-ar.md). القسم: ## مين صاحب القرار في إيه.

| قبل | بعد |
|---|---|
| سعر الوحدة اللي السلة فاكراه | سعر الوحدة المحفوظ في السلة |

السبب: صياغة مصرية أوضح وأدق مع الحفاظ على معنى المصدر الإنجليزي.

مخاطر المعنى: لا تغيير في المتطلبات أو السلوك التقني.

### EA-125 — MINOR

الملف: [docs/developer-handbook/04-codebase/api-contracts-ar.md](../../docs/developer-handbook/04-codebase/api-contracts-ar.md). القسم: # عقود الـ API.

| قبل | بعد |
|---|---|
| مستند OpenAPI مكتوب بأنواع (Typed) | مستند OpenAPI بأنواع بيانات محددة (Typed) |

السبب: صياغة مصرية أوضح وأدق مع الحفاظ على معنى المصدر الإنجليزي.

مخاطر المعنى: لا تغيير في المتطلبات أو السلوك التقني.

### EA-126 — MINOR

الملف: [docs/developer-handbook/04-codebase/api-contracts-ar.md](../../docs/developer-handbook/04-codebase/api-contracts-ar.md). القسم: ## الاتفاقيات اللي كل Endpoint بيمشي عليها.

| قبل | بعد |
|---|---|
| `cursor` معتّم (Opaque) | `cursor` قيمته مش مخصّصة للتفسير عند الـ Client (Opaque) |

السبب: صياغة مصرية أوضح وأدق مع الحفاظ على معنى المصدر الإنجليزي.

مخاطر المعنى: لا تغيير في المتطلبات أو السلوك التقني.

### EA-127 — MINOR

الملف: [docs/developer-handbook/04-codebase/backend-module-guide-ar.md](../../docs/developer-handbook/04-codebase/backend-module-guide-ar.md). القسم: ## `shared` — `backend/shared`.

| قبل | بعد |
|---|---|
| Hook مسح الخصوصية | Hook لمسح البيانات الشخصية |

السبب: صياغة مصرية أوضح وأدق مع الحفاظ على معنى المصدر الإنجليزي.

مخاطر المعنى: لا تغيير في المتطلبات أو السلوك التقني.

### EA-128 — MINOR

الملف: [docs/developer-handbook/04-codebase/backend-module-guide-ar.md](../../docs/developer-handbook/04-codebase/backend-module-guide-ar.md). القسم: ## `catalog` — الوجبات والميديا والاكتشاف والمفضلة والنشاط.

| قبل | بعد |
|---|---|
| حراس النشر (Publication Guards) | ضوابط السماح بالنشر (Publication Guards) |

السبب: صياغة مصرية أوضح وأدق مع الحفاظ على معنى المصدر الإنجليزي.

مخاطر المعنى: لا تغيير في المتطلبات أو السلوك التقني.

### EA-129 — MINOR

الملف: [docs/developer-handbook/04-codebase/backend-module-guide-ar.md](../../docs/developer-handbook/04-codebase/backend-module-guide-ar.md). القسم: ## `identity` — الحسابات وبيانات التواصل والـ Grants.

| قبل | بعد |
|---|---|
| البيئات الاصطناعية (Synthetic) | بيئات البيانات التجريبية غير الحقيقية (Synthetic Data) |

السبب: صياغة مصرية أوضح وأدق مع الحفاظ على معنى المصدر الإنجليزي.

مخاطر المعنى: لا تغيير في المتطلبات أو السلوك التقني.

### EA-130 — MINOR

الملف: [docs/developer-handbook/04-codebase/frontend-code-guide-ar.md](../../docs/developer-handbook/04-codebase/frontend-code-guide-ar.md). القسم: ## ويب العميل — `apps/customer-web/`.

| قبل | بعد |
|---|---|
| بانر لبيئات البيانات الاصطناعية/المعاينة | بانر لبيئات البيانات التجريبية غير الحقيقية/المعاينة |

السبب: صياغة مصرية أوضح وأدق مع الحفاظ على معنى المصدر الإنجليزي.

مخاطر المعنى: لا تغيير في المتطلبات أو السلوك التقني.

### EA-131 — MINOR

الملف: [docs/developer-handbook/04-codebase/frontend-code-guide-ar.md](../../docs/developer-handbook/04-codebase/frontend-code-guide-ar.md). القسم: ## ويب العمليات — `apps/operations-web/`.

| قبل | بعد |
|---|---|
| طابور مطابقة العرابين | طابور مراجعة وتسوية مدفوعات العربون |

السبب: صياغة مصرية أوضح وأدق مع الحفاظ على معنى المصدر الإنجليزي.

مخاطر المعنى: لا تغيير في المتطلبات أو السلوك التقني.

### EA-132 — MINOR

الملف: [docs/developer-handbook/04-codebase/frontend-code-guide-ar.md](../../docs/developer-handbook/04-codebase/frontend-code-guide-ar.md). القسم: ## تنفّذ فين.

| قبل | بعد |
|---|---|
| ماتعملش Cast حوالين الأنواع المتولّدة | ماتستخدمش Type cast عشان تتجاوز الأنواع المتولّدة |

السبب: صياغة مصرية أوضح وأدق مع الحفاظ على معنى المصدر الإنجليزي.

مخاطر المعنى: لا تغيير في المتطلبات أو السلوك التقني.

### EA-133 — MINOR

الملف: [docs/developer-handbook/04-codebase/mobile-code-guide-ar.md](../../docs/developer-handbook/04-codebase/mobile-code-guide-ar.md). القسم: # دليل كود الموبايل (تطبيق العميل).

| قبل | بعد |
|---|---|
| قبول الجهاز الفعلي وقارئ الشاشة | اختبارات القبول على أجهزة فعلية وبقارئ الشاشة |

السبب: صياغة مصرية أوضح وأدق مع الحفاظ على معنى المصدر الإنجليزي.

مخاطر المعنى: لا تغيير في المتطلبات أو السلوك التقني.

### EA-134 — MINOR

الملف: [docs/developer-handbook/04-codebase/mobile-code-guide-ar.md](../../docs/developer-handbook/04-codebase/mobile-code-guide-ar.md). القسم: ## القيود المعروفة وفجوات التوثيق.

| قبل | بعد |
|---|---|
| قبول الجهاز الفعلي وقارئ الشاشة | اختبارات القبول على أجهزة فعلية وبقارئ الشاشة |

السبب: صياغة مصرية أوضح وأدق مع الحفاظ على معنى المصدر الإنجليزي.

مخاطر المعنى: لا تغيير في المتطلبات أو السلوك التقني.

### EA-135 — MINOR

الملف: [docs/developer-handbook/04-codebase/mobile-code-guide-ar.md](../../docs/developer-handbook/04-codebase/mobile-code-guide-ar.md). القسم: ## الـ Authentication وتخزين الـ Session.

| قبل | بعد |
|---|---|
| حالة شاشة تفضل عايشة | حالة شاشة تفضل محفوظة من الحساب السابق |

السبب: صياغة مصرية أوضح وأدق مع الحفاظ على معنى المصدر الإنجليزي.

مخاطر المعنى: لا تغيير في المتطلبات أو السلوك التقني.

### EA-136 — MINOR

الملف: [docs/developer-handbook/04-codebase/mobile-code-guide-ar.md](../../docs/developer-handbook/04-codebase/mobile-code-guide-ar.md). القسم: ## كود الطلب.

| قبل | بعد |
|---|---|
| حاجز الانهيار (Crash Boundary) | مكوّن احتواء أخطاء العرض (Crash Boundary) |

السبب: صياغة مصرية أوضح وأدق مع الحفاظ على معنى المصدر الإنجليزي.

مخاطر المعنى: لا تغيير في المتطلبات أو السلوك التقني.

### EA-137 — MINOR

الملف: [docs/developer-handbook/04-codebase/order-implementation-ar.md](../../docs/developer-handbook/04-codebase/order-implementation-ar.md). القسم: ## طبقتين Idempotency (ماتخلطش بينهم).

| قبل | بعد |
|---|---|
| بيتختم قدّام بـ 24 ساعة | بيتحدد بوقت بعد 24 ساعة |

السبب: صياغة مصرية أوضح وأدق مع الحفاظ على معنى المصدر الإنجليزي.

مخاطر المعنى: لا تغيير في المتطلبات أو السلوك التقني.

### EA-138 — MINOR

الملف: [docs/developer-handbook/04-codebase/order-implementation-ar.md](../../docs/developer-handbook/04-codebase/order-implementation-ar.md). القسم: ## خطوة بخطوة.

| قبل | بعد |
|---|---|
| المفتاح الناقص أو الغلط شكله | المفتاح الناقص أو المكتوب بصيغة غير صحيحة |

السبب: صياغة مصرية أوضح وأدق مع الحفاظ على معنى المصدر الإنجليزي.

مخاطر المعنى: لا تغيير في المتطلبات أو السلوك التقني.

### EA-139 — MINOR

الملف: [docs/developer-handbook/04-codebase/order-implementation-ar.md](../../docs/developer-handbook/04-codebase/order-implementation-ar.md). القسم: ## خطوة بخطوة.

| قبل | بعد |
|---|---|
| حقول بادئتها الطول عشان مفيش قيمة تقلّد فاصل | حقول بيسبق كل واحد فيها طوله، عشان محتوى أي حقل ميتفهمش على إنه فاصل |

السبب: صياغة مصرية أوضح وأدق مع الحفاظ على معنى المصدر الإنجليزي.

مخاطر المعنى: لا تغيير في المتطلبات أو السلوك التقني.

### EA-140 — MINOR

الملف: [docs/developer-handbook/04-codebase/order-implementation-ar.md](../../docs/developer-handbook/04-codebase/order-implementation-ar.md). القسم: ## خطوة بخطوة.

| قبل | بعد |
|---|---|
| للـ Fixtures الاصطناعية (Synthetic) | للـ Fixtures ببيانات تجريبية غير حقيقية (Synthetic Data) |

السبب: صياغة مصرية أوضح وأدق مع الحفاظ على معنى المصدر الإنجليزي.

مخاطر المعنى: لا تغيير في المتطلبات أو السلوك التقني.

### EA-141 — MAJOR

الملف: [docs/developer-handbook/04-codebase/order-implementation-ar.md](../../docs/developer-handbook/04-codebase/order-implementation-ar.md). القسم: ## خطوة بخطوة.

| قبل | بعد |
|---|---|
| شايل رقم الطلب الحالي | شايل معرّف طلب الـ API الحالي (Request ID) |

السبب: current request id يشير لمعرّف HTTP request، مش رقم الأوردر.

مخاطر المعنى: لا تغيير في المتطلبات أو السلوك التقني.

### EA-142 — MINOR

الملف: [docs/developer-handbook/04-codebase/payment-implementation-ar.md](../../docs/developer-handbook/04-codebase/payment-implementation-ar.md). القسم: # شرح تنفيذ الدفع والكاش والـ Ledger.

| قبل | بعد |
|---|---|
| الفلوس الحقيقية متقفّلة بـ Gate | التعاملات المالية الحقيقية مش مفعّلة حاليًا وتفعيلها مرتبط بشروط الـ Gate |

السبب: صياغة مصرية أوضح وأدق مع الحفاظ على معنى المصدر الإنجليزي.

مخاطر المعنى: لا تغيير في المتطلبات أو السلوك التقني.

### EA-143 — MINOR

الملف: [docs/developer-handbook/04-codebase/payment-implementation-ar.md](../../docs/developer-handbook/04-codebase/payment-implementation-ar.md). القسم: # شرح تنفيذ الدفع والكاش والـ Ledger.

| قبل | بعد |
|---|---|
| الإطار المنتجي | منظور المنتج |

السبب: صياغة مصرية أوضح وأدق مع الحفاظ على معنى المصدر الإنجليزي.

مخاطر المعنى: لا تغيير في المتطلبات أو السلوك التقني.

### EA-144 — MINOR

الملف: [docs/developer-handbook/04-codebase/payment-implementation-ar.md](../../docs/developer-handbook/04-codebase/payment-implementation-ar.md). القسم: ## الـ State Machine بتاعة العربون — `PaymentService`.

| قبل | بعد |
|---|---|
| العربونات | مدفوعات العربون |

السبب: صياغة مصرية أوضح وأدق مع الحفاظ على معنى المصدر الإنجليزي.

مخاطر المعنى: لا تغيير في المتطلبات أو السلوك التقني.

### EA-145 — MINOR

الملف: [docs/developer-handbook/04-codebase/payment-implementation-ar.md](../../docs/developer-handbook/04-codebase/payment-implementation-ar.md). القسم: ## الـ Gate بتاع الفلوس الحقيقية.

| قبل | بعد |
|---|---|
| العربونات | مدفوعات العربون |

السبب: صياغة مصرية أوضح وأدق مع الحفاظ على معنى المصدر الإنجليزي.

مخاطر المعنى: لا تغيير في المتطلبات أو السلوك التقني.

### EA-146 — MINOR

الملف: [docs/developer-handbook/04-codebase/payment-implementation-ar.md](../../docs/developer-handbook/04-codebase/payment-implementation-ar.md). القسم: ## الـ State Machine بتاعة العربون — `PaymentService`.

| قبل | بعد |
|---|---|
| بيعلّم الفلوس إنها اتستلمت | بيسجّل إن الفلوس اتستلمت |

السبب: صياغة مصرية أوضح وأدق مع الحفاظ على معنى المصدر الإنجليزي.

مخاطر المعنى: لا تغيير في المتطلبات أو السلوك التقني.

### EA-147 — MINOR

الملف: [docs/developer-handbook/04-codebase/payment-implementation-ar.md](../../docs/developer-handbook/04-codebase/payment-implementation-ar.md). القسم: ## الـ Gate بتاع الفلوس الحقيقية.

| قبل | بعد |
|---|---|
| حارس المعاينة/البيانات الاصطناعية (Synthetic) | ضابط بيئات المعاينة/البيانات التجريبية غير الحقيقية (Synthetic Data) |

السبب: صياغة مصرية أوضح وأدق مع الحفاظ على معنى المصدر الإنجليزي.

مخاطر المعنى: لا تغيير في المتطلبات أو السلوك التقني.

### EA-148 — MINOR

الملف: [docs/developer-handbook/04-codebase/pricing-implementation-ar.md](../../docs/developer-handbook/04-codebase/pricing-implementation-ar.md). القسم: ## المبادئ في الكود.

| قبل | بعد |
|---|---|
| السيرفر هو اللي بيملك كل مبلغ | السيرفر هو اللي بيحدد كل مبلغ |

السبب: صياغة مصرية أوضح وأدق مع الحفاظ على معنى المصدر الإنجليزي.

مخاطر المعنى: لا تغيير في المتطلبات أو السلوك التقني.

### EA-149 — MINOR

الملف: [docs/developer-handbook/04-codebase/pricing-implementation-ar.md](../../docs/developer-handbook/04-codebase/pricing-implementation-ar.md). القسم: ## المبادئ في الكود.

| قبل | بعد |
|---|---|
| الأسعار بتتنسخ في الأوردرات، مبتتشاورش عليها | الأسعار بتتنسخ جوه الأوردرات، ومبتتخزّنش كمجرد مراجع لقيمها الحالية |

السبب: صياغة مصرية أوضح وأدق مع الحفاظ على معنى المصدر الإنجليزي.

مخاطر المعنى: لا تغيير في المتطلبات أو السلوك التقني.

### EA-150 — MAJOR

الملف: [docs/developer-handbook/04-codebase/pricing-implementation-ar.md](../../docs/developer-handbook/04-codebase/pricing-implementation-ar.md). القسم: ## إعادة تأكيد الـ Quote القديم في جهة العميل (D13).

| قبل | بعد |
|---|---|
| \| النسب \| | \| تحديد مصدر التغيير \| |

السبب: Attribution هنا تحديد المسؤول عن تغيّر السعر، مش نسب حسابية.

مخاطر المعنى: لا تغيير في المتطلبات أو السلوك التقني.

### EA-151 — MINOR

الملف: [docs/developer-handbook/04-codebase/pricing-implementation-ar.md](../../docs/developer-handbook/04-codebase/pricing-implementation-ar.md). القسم: # شرح تنفيذ التسعير.

| قبل | بعد |
|---|---|
| القيم التجارية (الضريبة، الحدود) اصطناعية (Synthetic) | القيم التجارية (الضريبة، الحدود) تجريبية وغير حقيقية (Synthetic) |

السبب: صياغة مصرية أوضح وأدق مع الحفاظ على معنى المصدر الإنجليزي.

مخاطر المعنى: لا تغيير في المتطلبات أو السلوك التقني.

### EA-152 — MINOR

الملف: [docs/developer-handbook/04-codebase/pricing-implementation-ar.md](../../docs/developer-handbook/04-codebase/pricing-implementation-ar.md). القسم: ## السعر بييجي منين.

| قبل | بعد |
|---|---|
| للسياسات الاصطناعية بس | للسياسات التجريبية غير الحقيقية بس |

السبب: صياغة مصرية أوضح وأدق مع الحفاظ على معنى المصدر الإنجليزي.

مخاطر المعنى: لا تغيير في المتطلبات أو السلوك التقني.

### EA-153 — MINOR

الملف: [docs/developer-handbook/04-codebase/pricing-implementation-ar.md](../../docs/developer-handbook/04-codebase/pricing-implementation-ar.md). القسم: ## القيود المعروفة وفجوات التوثيق.

| قبل | بعد |
|---|---|
| السياسة الاصطناعية | السياسة التجريبية غير الحقيقية |

السبب: صياغة مصرية أوضح وأدق مع الحفاظ على معنى المصدر الإنجليزي.

مخاطر المعنى: لا تغيير في المتطلبات أو السلوك التقني.

### EA-154 — MINOR

الملف: [docs/developer-handbook/04-codebase/pricing-implementation-ar.md](../../docs/developer-handbook/04-codebase/pricing-implementation-ar.md). القسم: ## القيود المعروفة وفجوات التوثيق.

| قبل | بعد |
|---|---|
| سياسة التطوير الاصطناعية | سياسة التطوير التجريبية غير الحقيقية |

السبب: صياغة مصرية أوضح وأدق مع الحفاظ على معنى المصدر الإنجليزي.

مخاطر المعنى: لا تغيير في المتطلبات أو السلوك التقني.

### EA-155 — MINOR

الملف: [docs/developer-handbook/04-codebase/repository-map-ar.md](../../docs/developer-handbook/04-codebase/repository-map-ar.md). القسم: ## المستوى الأعلى.

| قبل | بعد |
|---|---|
| حزمة التخطيط المقروءة آليًا | ملفات التخطيط القابلة للقراءة آليًا |

السبب: صياغة مصرية أوضح وأدق مع الحفاظ على معنى المصدر الإنجليزي.

مخاطر المعنى: لا تغيير في المتطلبات أو السلوك التقني.

### EA-156 — MINOR

الملف: [docs/developer-handbook/04-codebase/shared-libraries-ar.md](../../docs/developer-handbook/04-codebase/shared-libraries-ar.md). القسم: ## `packages/api-client` — `@matbakhy/api-client`.

| قبل | بعد |
|---|---|
| والحداثة بيفحصها | ومطابقة الكود لآخر توليد بيفحصها |

السبب: صياغة مصرية أوضح وأدق مع الحفاظ على معنى المصدر الإنجليزي.

مخاطر المعنى: لا تغيير في المتطلبات أو السلوك التقني.

### EA-157 — MINOR

الملف: [docs/developer-handbook/04-codebase/shared-libraries-ar.md](../../docs/developer-handbook/04-codebase/shared-libraries-ar.md). القسم: ## `packages/design-tokens` — `@matbakhy/design-tokens`.

| قبل | بعد |
|---|---|
| وهدف اللمس 44px | ومساحة التفاعل باللمس 44px |

السبب: صياغة مصرية أوضح وأدق مع الحفاظ على معنى المصدر الإنجليزي.

مخاطر المعنى: لا تغيير في المتطلبات أو السلوك التقني.

### EA-158 — MINOR

الملف: [docs/developer-handbook/04-codebase/shared-libraries-ar.md](../../docs/developer-handbook/04-codebase/shared-libraries-ar.md). القسم: ## المشاركة بين الويب والموبايل: إيه المشترك وإيه مش مشترك.

| قبل | بعد |
|---|---|
| تحليل الفلوس (Parsing) | قراءة القيم المالية والتحقق من صيغتها (Parsing) |

السبب: صياغة مصرية أوضح وأدق مع الحفاظ على معنى المصدر الإنجليزي.

مخاطر المعنى: لا تغيير في المتطلبات أو السلوك التقني.

### EA-159 — MINOR

الملف: [docs/developer-handbook/03-architecture/identity-and-security-ar.md](../../docs/developer-handbook/03-architecture/identity-and-security-ar.md). القسم: مسارين للـ Authentication.

| قبل | بعد |
|---|---|
| التطبيق بيلغي الـ Refresh token (بأقصى جهد) | التطبيق بيحاول يلغي الـ Refresh token بأفضل محاولة ممكنة (best effort) |

السبب: الإبقاء على best effort بوضوح ومن غير أقواس متداخلة.

مخاطر المعنى: لا تغيير في السلوك.

### EA-160 — MAJOR

الملف: [docs/developer-handbook/03-architecture/integration-and-events-ar.md](../../docs/developer-handbook/03-architecture/integration-and-events-ar.md). القسم: Independent semantic follow-up.

| قبل | بعد |
|---|---|
| عمليات التسليم اللي استنفدت محاولاتها (Dead deliveries) | عمليات التسليم المتوقفة نهائيًا (Dead deliveries) |

السبب: Independent reviewer: DEAD also includes permanentFailure and inactive-recipient outcomes; do not narrow it to retry exhaustion.

مخاطر المعنى: Corrects ambiguity introduced during initial editorial revision; English unchanged.

### EA-161 — MAJOR

الملف: [docs/developer-handbook/04-codebase/frontend-code-guide-ar.md](../../docs/developer-handbook/04-codebase/frontend-code-guide-ar.md). القسم: Independent semantic follow-up.

| قبل | بعد |
|---|---|
| طابور مراجعة وتسوية مدفوعات العربون | طابور مطابقة مدفوعات العربون |

السبب: Independent reviewer: preserve distinction of deposit reconciliation from commission settlement.

مخاطر المعنى: Corrects ambiguity introduced during initial editorial revision; English unchanged.

### EA-162 — MAJOR

الملف: [docs/developer-handbook/04-codebase/order-implementation-ar.md](../../docs/developer-handbook/04-codebase/order-implementation-ar.md). القسم: Two idempotency layers / Step by step.

| قبل | بعد |
|---|---|
| إعادة التشغيل (Replay) | إعادة تنفيذ نفس الأمر (Replay) |

السبب: Independent reviewer: distinguish command replay from process restart, and recovery lookup from money refund.

مخاطر المعنى: No change to idempotency or permanent fingerprint guarantees.

### EA-163 — MAJOR

الملف: [docs/developer-handbook/04-codebase/order-implementation-ar.md](../../docs/developer-handbook/04-codebase/order-implementation-ar.md). القسم: Two idempotency layers / Step by step.

| قبل | بعد |
|---|---|
| **فحص الاسترداد.** | **فحص استرجاع الأوردر الموجود.** |

السبب: Independent reviewer: distinguish command replay from process restart, and recovery lookup from money refund.

مخاطر المعنى: No change to idempotency or permanent fingerprint guarantees.

### EA-164 — MINOR

الملف: [docs/developer-handbook/05-developer-workflow/local-environment-ar.md](../../docs/developer-handbook/05-developer-workflow/local-environment-ar.md). القسم: # البيئة المحلية.

| قبل | بعد |
|---|---|
| بيانات اصطناعية (Synthetic) | بيانات تجريبية غير حقيقية (Synthetic) |

السبب: Clarify synthetic fixtures, distinct from actual data.

مخاطر المعنى: None; technical requirement and protected text unchanged.

### EA-165 — MINOR

الملف: [docs/developer-handbook/05-developer-workflow/local-environment-ar.md](../../docs/developer-handbook/05-developer-workflow/local-environment-ar.md). القسم: ## اللي بيوفّره `scripts/dev.sh`.

| قبل | بعد |
|---|---|
| بيلف حوالين | بيسهّل تشغيل وإدارة الخدمات المتعرّفة في |

السبب: Replace literal wraps with script purpose.

مخاطر المعنى: None; technical requirement and protected text unchanged.

### EA-166 — MINOR

الملف: [docs/developer-handbook/05-developer-workflow/local-environment-ar.md](../../docs/developer-handbook/05-developer-workflow/local-environment-ar.md). القسم: ## اللي بيوفّره `scripts/dev.sh`.

| قبل | بعد |
|---|---|
| مصرف بريد | خدمة بتستقبل رسائل البريد التجريبية محليًا |

السبب: Mail sink is capture service, not an Arabic drain.

مخاطر المعنى: None; technical requirement and protected text unchanged.

### EA-167 — MAJOR

الملف: [docs/developer-handbook/05-developer-workflow/local-environment-ar.md](../../docs/developer-handbook/05-developer-workflow/local-environment-ar.md). القسم: ## اللي بيوفّره `scripts/dev.sh`.

| قبل | بعد |
|---|---|
| بمستخدم وهمي، ودورة رفع خاص موقّع | بمستخدم تجريبي، واختبار دورة رفع ملف خاص باستخدام رابط موقّع |

السبب: Clarify signed private upload round trip.

مخاطر المعنى: Reduces ambiguity between signed file and signed upload URL; original round-trip test remains.

### EA-168 — MINOR

الملف: [docs/developer-handbook/05-developer-workflow/local-environment-ar.md](../../docs/developer-handbook/05-developer-workflow/local-environment-ar.md). القسم: ## المستخدمين الوهميين.

| قبل | بعد |
|---|---|
| وسياسة تجارة اصطناعية | وسياسة تجارية ببيانات تجريبية غير حقيقية |

السبب: Clarify synthetic commerce policy.

مخاطر المعنى: None; technical requirement and protected text unchanged.

### EA-169 — MINOR

الملف: [docs/developer-handbook/05-developer-workflow/local-environment-ar.md](../../docs/developer-handbook/05-developer-workflow/local-environment-ar.md). القسم: ## المستخدمين الوهميين.

| قبل | بعد |
|---|---|
| بيتحسبوا بيئات اصطناعية | بيتحسبوا بيئات ببيانات تجريبية غير حقيقية |

السبب: Clarify synthetic environment classification.

مخاطر المعنى: None; technical requirement and protected text unchanged.

### EA-170 — MINOR

الملف: [docs/developer-handbook/05-developer-workflow/local-environment-ar.md](../../docs/developer-handbook/05-developer-workflow/local-environment-ar.md). القسم: ## الموبايل (تطبيق العميل).

| قبل | بعد |
|---|---|
| التدفقات الـ Native المتسكربتة | خطوات الـ Native المؤتمتة بالسكريبتات |

السبب: Remove unnatural translated adjective.

مخاطر المعنى: None; technical requirement and protected text unchanged.

### EA-171 — MINOR

الملف: [docs/developer-handbook/05-developer-workflow/local-environment-ar.md](../../docs/developer-handbook/05-developer-workflow/local-environment-ar.md). القسم: ## تشغيل الـ API برّه Docker.

| قبل | بعد |
|---|---|
| أبسط طريقة مدعومة للتكرار على الـ Backend | أبسط طريقة مدعومة لتطوير الـ Backend وتجربة التعديلات |

السبب: Replace literal iterate with development cycle.

مخاطر المعنى: None; technical requirement and protected text unchanged.

### EA-172 — MINOR

الملف: [docs/developer-handbook/05-developer-workflow/prerequisites-ar.md](../../docs/developer-handbook/05-developer-workflow/prerequisites-ar.md). القسم: # المتطلبات المسبقة.

| قبل | بعد |
|---|---|
| ولو الصفحة دي والملفات اختلفوا، صدّق الملفات | ولو الصفحة دي والملفات اختلفوا، اعتمد على الملفات |

السبب: Professional source-of-truth instruction.

مخاطر المعنى: None; technical requirement and protected text unchanged.

### EA-173 — MINOR

الملف: [docs/developer-handbook/05-developer-workflow/prerequisites-ar.md](../../docs/developer-handbook/05-developer-workflow/prerequisites-ar.md). القسم: ## الأدوات المطلوبة.

| قبل | بعد |
|---|---|
| حتى لو شوية أوامر بدت شغّالة | حتى لو بعض الأوامر باين إنها شغّالة |

السبب: Natural Egyptian tense.

مخاطر المعنى: None; technical requirement and protected text unchanged.

### EA-174 — MINOR

الملف: [docs/developer-handbook/05-developer-workflow/prerequisites-ar.md](../../docs/developer-handbook/05-developer-workflow/prerequisites-ar.md). القسم: ## أنظمة التشغيل.

| قبل | بعد |
|---|---|
| وشغل iOS الـ Native | وتشغيل Job بناء iOS الـ Native |

السبب: Clarify CI job evidence.

مخاطر المعنى: None; technical requirement and protected text unchanged.

### EA-175 — MINOR

الملف: [docs/developer-handbook/05-developer-workflow/ci-cd-ar.md](../../docs/developer-handbook/05-developer-workflow/ci-cd-ar.md). القسم: ## جرد الـ Workflows.

| قبل | بعد |
|---|---|
| بيستنى Product CI أخضر | بيستنى نجاح فحوصات Product CI |

السبب: Explain CI success without color shorthand.

مخاطر المعنى: None; technical requirement and protected text unchanged.

### EA-176 — MINOR

الملف: [docs/developer-handbook/05-developer-workflow/ci-cd-ar.md](../../docs/developer-handbook/05-developer-workflow/ci-cd-ar.md). القسم: ## جرد الـ Workflows.

| قبل | بعد |
|---|---|
| مالوش Product CI أخضر | فحوصات Product CI مانجحتش عليه |

السبب: Explain missing successful CI.

مخاطر المعنى: None; technical requirement and protected text unchanged.

### EA-177 — MAJOR

الملف: [docs/developer-handbook/05-developer-workflow/ci-cd-ar.md](../../docs/developer-handbook/05-developer-workflow/ci-cd-ar.md). القسم: ## الـ Rollback والنسخ الاحتياطية.

| قبل | بعد |
|---|---|
| الـ Rollback للصور بس آمن لما الـ Schema ماتحركتش | الرجوع لإصدار Docker Images سابق بس آمن لما مفيش تغيير في إصدار أو بنية الـ Database Schema |

السبب: Disambiguate Docker image rollback and schema condition.

مخاطر المعنى: Preserves alternative expand-only condition and database restore warning.

### EA-178 — MINOR

الملف: [docs/developer-handbook/05-developer-workflow/ci-cd-ar.md](../../docs/developer-handbook/05-developer-workflow/ci-cd-ar.md). القسم: ## من الدمج لبيئة المعاينة.

| قبل | بعد |
|---|---|
| صورتي `matbakhy-api` و`matbakhy-preview-front` | الـ Docker Images بتاعة `matbakhy-api` و`matbakhy-preview-front` |

السبب: Distinguish Docker artifacts from media images.

مخاطر المعنى: None; technical requirement and protected text unchanged.

### EA-179 — MINOR

الملف: [docs/developer-handbook/05-developer-workflow/ci-cd-ar.md](../../docs/developer-handbook/05-developer-workflow/ci-cd-ar.md). القسم: ## حدود الجاهزية للـ Production.

| قبل | بعد |
|---|---|
| بيئة المعاينة اصطناعية (Synthetic) | بيئة المعاينة بتستخدم بيانات تجريبية غير حقيقية (Synthetic) |

السبب: Clarify synthetic preview data.

مخاطر المعنى: None; technical requirement and protected text unchanged.

### EA-180 — MINOR

الملف: [docs/developer-handbook/05-developer-workflow/ci-cd-ar.md](../../docs/developer-handbook/05-developer-workflow/ci-cd-ar.md). القسم: ## حدود الجاهزية للـ Production.

| قبل | بعد |
|---|---|
| ماتوصفش Deployment أخضر | ماتوصفش Deployment نجح |

السبب: Replace color shorthand while preserving warning.

مخاطر المعنى: None; technical requirement and protected text unchanged.

### EA-181 — MINOR

الملف: [docs/developer-handbook/05-developer-workflow/ci-cd-ar.md](../../docs/developer-handbook/05-developer-workflow/ci-cd-ar.md). القسم: ## من الدمج لبيئة المعاينة.

| قبل | بعد |
|---|---|
| والـ Database مابتترجعش تلقائيًا | والـ Database مابيتعملهاش Rollback تلقائيًا |

السبب: Specify rollback rather than vague return.

مخاطر المعنى: None; technical requirement and protected text unchanged.

### EA-182 — MINOR

الملف: [docs/developer-handbook/05-developer-workflow/git-and-pr-workflow-ar.md](../../docs/developer-handbook/05-developer-workflow/git-and-pr-workflow-ar.md). القسم: ## قبل ما تفتح PR.

| قبل | بعد |
|---|---|
| عشان توصل للأخضر | عشان الفحوصات تنجح |

السبب: Preserve prohibition in clear technical language.

مخاطر المعنى: None; technical requirement and protected text unchanged.

### EA-183 — MINOR

الملف: [docs/developer-handbook/05-developer-workflow/git-and-pr-workflow-ar.md](../../docs/developer-handbook/05-developer-workflow/git-and-pr-workflow-ar.md). القسم: ## وصف الـ PR.

| قبل | بعد |
|---|---|
| العقود/الـ Migrations والأسطح | العقود/الـ Migrations وواجهات التطبيق |

السبب: Replace literal product surfaces.

مخاطر المعنى: None; technical requirement and protected text unchanged.

### EA-184 — MINOR

الملف: [docs/developer-handbook/05-developer-workflow/git-and-pr-workflow-ar.md](../../docs/developer-handbook/05-developer-workflow/git-and-pr-workflow-ar.md). القسم: ## قواعد الدمج.

| قبل | بعد |
|---|---|
| فحوصات مطلوبة خضرا | الفحوصات المطلوبة تكون ناجحة |

السبب: Describe successful required checks.

مخاطر المعنى: None; technical requirement and protected text unchanged.

### EA-185 — MINOR

الملف: [docs/developer-handbook/05-developer-workflow/branch-maintenance-ar.md](../../docs/developer-handbook/05-developer-workflow/branch-maintenance-ar.md). القسم: ## المبادئ.

| قبل | بعد |
|---|---|
| الشغل الفريد عمره ما بيتحذف | الشغل اللي لسه مش متدمج في الـ Branch المستهدف عمره ما بيتحذف |

السبب: Explain unique work in safe-cleanup rule.

مخاطر المعنى: None; technical requirement and protected text unchanged.

### EA-186 — MINOR

الملف: [docs/developer-handbook/05-developer-workflow/branch-maintenance-ar.md](../../docs/developer-handbook/05-developer-workflow/branch-maintenance-ar.md). القسم: ## المبادئ.

| قبل | بعد |
|---|---|
| بقى آمن. | بقى آمن للحذف. |

السبب: State safe operation explicitly.

مخاطر المعنى: None; technical requirement and protected text unchanged.

### EA-187 — MINOR

الملف: [docs/developer-handbook/05-developer-workflow/documentation-maintenance-ar.md](../../docs/developer-handbook/05-developer-workflow/documentation-maintenance-ar.md). القسم: ## الملكية (على أساس الدور).

| قبل | بعد |
|---|---|
| الأدوار في البداية على أساس الدور مش أشخاص بالاسم. | المسؤولية في البداية مرتبطة بالأدوار الوظيفية، مش بأشخاص بالاسم. |

السبب: Remove circular role-based translation.

مخاطر المعنى: None; technical requirement and protected text unchanged.

### EA-188 — MINOR

الملف: [docs/developer-handbook/05-developer-workflow/documentation-maintenance-ar.md](../../docs/developer-handbook/05-developer-workflow/documentation-maintenance-ar.md). القسم: ## مزامنة النسخة العربي.

| قبل | بعد |
|---|---|
| [ملف الاستثناءات](../translation-exceptions.json) المقروء بالآلة | [ملف الاستثناءات](../translation-exceptions.json) القابل للقراءة آليًا |

السبب: Natural machine-readable terminology.

مخاطر المعنى: None; technical requirement and protected text unchanged.

### EA-189 — MINOR

الملف: [docs/developer-handbook/05-developer-workflow/documentation-maintenance-ar.md](../../docs/developer-handbook/05-developer-workflow/documentation-maintenance-ar.md). القسم: ## مصفوفة تغيير التوثيق.

| قبل | بعد |
|---|---|
| "واحد على الأقل" هو الحد الأدنى الحتمي. | "واحد على الأقل" هو الحد الأدنى اللي الفحص الآلي بيفرضه. |

السبب: Clarify deterministic validation floor.

مخاطر المعنى: None; technical requirement and protected text unchanged.

### EA-190 — MINOR

الملف: [docs/developer-handbook/05-developer-workflow/documentation-maintenance-ar.md](../../docs/developer-handbook/05-developer-workflow/documentation-maintenance-ar.md). القسم: ## حالة حماية الـ Branch.

| قبل | بعد |
|---|---|
| المراجعة الدلالية فضلت واجب بشري أو مراجع مستقل | مراجعة المعنى لسه مسؤولية إنسان أو مراجع مستقل |

السبب: Correct tense and unnatural duty wording.

مخاطر المعنى: None; technical requirement and protected text unchanged.

### EA-191 — MINOR

الملف: [docs/developer-handbook/05-developer-workflow/preview-environment-ar.md](../../docs/developer-handbook/05-developer-workflow/preview-environment-ar.md). القسم: # بيئة المعاينة.

| قبل | بعد |
|---|---|
| ببيانات اصطناعية (Synthetic) | ببيانات تجريبية غير حقيقية (Synthetic) |

السبب: Clarify synthetic preview classification.

مخاطر المعنى: None; technical requirement and protected text unchanged.

### EA-192 — MINOR

الملف: [docs/developer-handbook/05-developer-workflow/preview-environment-ar.md](../../docs/developer-handbook/05-developer-workflow/preview-environment-ar.md). القسم: ## هي إيه.

| قبل | بعد |
|---|---|
| وبياناتها اصطناعية | وبياناتها تجريبية غير حقيقية |

السبب: Consistent synthetic data wording.

مخاطر المعنى: None; technical requirement and protected text unchanged.

### EA-193 — MINOR

الملف: [docs/developer-handbook/05-developer-workflow/preview-environment-ar.md](../../docs/developer-handbook/05-developer-workflow/preview-environment-ar.md). القسم: ## هي إيه.

| قبل | بعد |
|---|---|
| والكود بيعتبره بيئة اصطناعية | والكود بيعتبره بيئة ببيانات تجريبية غير حقيقية |

السبب: Clarify environment treatment.

مخاطر المعنى: None; technical requirement and protected text unchanged.

### EA-194 — MINOR

الملف: [docs/developer-handbook/05-developer-workflow/preview-environment-ar.md](../../docs/developer-handbook/05-developer-workflow/preview-environment-ar.md). القسم: ## استخدامها.

| قبل | بعد |
|---|---|
| الصور الشغّالة، وإصدار الـ Schema | الـ Docker Images الشغّالة، وإصدار الـ Schema |

السبب: Avoid confusion with media images.

مخاطر المعنى: None; technical requirement and protected text unchanged.

### EA-195 — MINOR

الملف: [docs/developer-handbook/05-developer-workflow/preview-environment-ar.md](../../docs/developer-handbook/05-developer-workflow/preview-environment-ar.md). القسم: ## استخدامها.

| قبل | بعد |
|---|---|
| لو في Deployment واقف في الطابور | لو في Deployment مستني دوره في التنفيذ |

السبب: Professional natural queue wording.

مخاطر المعنى: None; technical requirement and protected text unchanged.

### EA-196 — MINOR

الملف: [docs/developer-handbook/05-developer-workflow/run-and-debug-ar.md](../../docs/developer-handbook/05-developer-workflow/run-and-debug-ar.md). القسم: ## النداء على الـ API.

| قبل | بعد |
|---|---|
| الـ Client بتاع TypeScript المتولّد | الـ Client المتولّد بـ TypeScript |

السبب: Natural attribution wording.

مخاطر المعنى: None; technical requirement and protected text unchanged.

### EA-197 — MINOR

الملف: [docs/developer-handbook/05-developer-workflow/run-and-debug-ar.md](../../docs/developer-handbook/05-developer-workflow/run-and-debug-ar.md). القسم: ## الـ Debug على الـ Database.

| قبل | بعد |
|---|---|
| الـ Migrations ملفات في اتجاه واحد بس | الـ Migrations ملفات بتطبّق تغييرات لقدّام بس |

السبب: Explain forward-only migrations.

مخاطر المعنى: None; technical requirement and protected text unchanged.

### EA-198 — MINOR

الملف: [docs/developer-handbook/05-developer-workflow/run-and-debug-ar.md](../../docs/developer-handbook/05-developer-workflow/run-and-debug-ar.md). القسم: ## الـ Debug على الـ Database.

| قبل | بعد |
|---|---|
| عادة القراءة بس: استخدم | خلّي التحقيق للقراءة بس: استخدم |

السبب: Natural diagnostic instruction.

مخاطر المعنى: None; technical requirement and protected text unchanged.

### EA-199 — MINOR

الملف: [docs/developer-handbook/05-developer-workflow/test-execution-ar.md](../../docs/developer-handbook/05-developer-workflow/test-execution-ar.md). القسم: ## قواعد اختبارات الـ Backend (من الكود).

| قبل | بعد |
|---|---|
| عشان تجيب أخضر | عشان الفحوصات تنجح |

السبب: Remove CI color shorthand.

مخاطر المعنى: None; technical requirement and protected text unchanged.

### EA-200 — MINOR

الملف: [docs/developer-handbook/05-developer-workflow/test-execution-ar.md](../../docs/developer-handbook/05-developer-workflow/test-execution-ar.md). القسم: ## الأدلة والتعامل مع العيوب.

| قبل | بعد |
|---|---|
| الـ CI الأخضر مش معناه | نجاح فحوصات الـ CI مش معناه |

السبب: Precise verification limitation.

مخاطر المعنى: None; technical requirement and protected text unchanged.

### EA-201 — MINOR

الملف: [docs/developer-handbook/05-developer-workflow/test-execution-ar.md](../../docs/developer-handbook/05-developer-workflow/test-execution-ar.md). القسم: ## كتالوج قبول المالك (200 حالة).

| قبل | بعد |
|---|---|
| فصدّق 200 | فاعتمد العدد 200 |

السبب: Professional source-of-truth wording.

مخاطر المعنى: None; technical requirement and protected text unchanged.

### EA-202 — MINOR

الملف: [docs/developer-handbook/05-developer-workflow/test-execution-ar.md](../../docs/developer-handbook/05-developer-workflow/test-execution-ar.md). القسم: ## الـ Fixtures الاصطناعية (Synthetic).

| قبل | بعد |
|---|---|
| مفيش غير أطراف وهمية | مفيش غير حسابات تجريبية غير حقيقية |

السبب: Clarify synthetic actors.

مخاطر المعنى: None; technical requirement and protected text unchanged.

### EA-203 — MINOR

الملف: [docs/developer-handbook/05-developer-workflow/test-execution-ar.md](../../docs/developer-handbook/05-developer-workflow/test-execution-ar.md). القسم: ## الأدلة والتعامل مع العيوب.

| قبل | بعد |
|---|---|
| معرّفات الـ Fixtures الاصطناعية | معرّفات الـ Fixtures ذات البيانات التجريبية غير الحقيقية |

السبب: Consistent data terminology.

مخاطر المعنى: None; technical requirement and protected text unchanged.

### EA-204 — MINOR

الملف: [docs/developer-handbook/05-developer-workflow/troubleshooting-ar.md](../../docs/developer-handbook/05-developer-workflow/troubleshooting-ar.md). القسم: ## الـ Stack المحلي.

| قبل | بعد |
|---|---|
| بيستنى لحد 300 ثانية للصحة | بيستنى لحد 300 ثانية عشان فحوصات الصحة تنجح |

السبب: Remove literal health wait.

مخاطر المعنى: None; technical requirement and protected text unchanged.

### EA-205 — MAJOR

الملف: [docs/developer-handbook/05-developer-workflow/troubleshooting-ar.md](../../docs/developer-handbook/05-developer-workflow/troubleshooting-ar.md). القسم: ## الاختبارات.

| قبل | بعد |
|---|---|
| تنسيق محدد | تنسيق حتمي بين العمليات المتزامنة |

السبب: Translate deterministic coordination accurately.

مخاطر المعنى: Makes race-test synchronization precise without prescribing a new implementation.

### EA-206 — MINOR

الملف: [docs/developer-handbook/05-developer-workflow/troubleshooting-ar.md](../../docs/developer-handbook/05-developer-workflow/troubleshooting-ar.md). القسم: ## الاختبارات.

| قبل | بعد |
|---|---|
| لحد ما يبقى أخضر | لحد ما ينجح |

السبب: Clear success wording.

مخاطر المعنى: None; technical requirement and protected text unchanged.

### EA-207 — MINOR

الملف: [docs/developer-handbook/05-developer-workflow/troubleshooting-ar.md](../../docs/developer-handbook/05-developer-workflow/troubleshooting-ar.md). القسم: ## Git والـ PR والتوثيق.

| قبل | بعد |
|---|---|
| رغم إن التشغيل المحلي أخضر | رغم إن الفحوصات المحلية نجحت |

السبب: Clear local validation result.

مخاطر المعنى: None; technical requirement and protected text unchanged.

### EA-208 — MINOR

الملف: [docs/developer-handbook/06-onboarding-labs/day-1-business-ar.md](../../docs/developer-handbook/06-onboarding-labs/day-1-business-ar.md). القسم: ## الأهداف.

| قبل | بعد |
|---|---|
| مين الأطراف وإيه اللي يسمح لكل واحد يعمله | مين الأطراف وإيه اللي مسموح لكل واحد يعمله |

السبب: Correct permission grammar.

مخاطر المعنى: None; technical requirement and protected text unchanged.

### EA-209 — MINOR

الملف: [docs/developer-handbook/06-onboarding-labs/day-1-business-ar.md](../../docs/developer-handbook/06-onboarding-labs/day-1-business-ar.md). القسم: ## الأهداف.

| قبل | بعد |
|---|---|
| والأسعار بيملكها السيرفر | والسيرفر هو المسؤول عن تحديد الأسعار |

السبب: Clarify server authority without literal ownership.

مخاطر المعنى: None; technical requirement and protected text unchanged.

### EA-210 — MINOR

الملف: [docs/developer-handbook/06-onboarding-labs/day-1-business-ar.md](../../docs/developer-handbook/06-onboarding-labs/day-1-business-ar.md). القسم: ## مهام عملية.

| قبل | بعد |
|---|---|
| علّم على كل ضلع الطرف المسؤول | علّم على كل سهم انتقال الطرف المسؤول |

السبب: Use state-machine edge terminology.

مخاطر المعنى: None; technical requirement and protected text unchanged.

### EA-211 — MINOR

الملف: [docs/developer-handbook/06-onboarding-labs/day-1-business-ar.md](../../docs/developer-handbook/06-onboarding-labs/day-1-business-ar.md). القسم: ## مهام عملية.

| قبل | بعد |
|---|---|
| شغل نظام زي انتهاء الصلاحية | مهمة بيشغّلها النظام زي انتهاء الصلاحية |

السبب: Clarify system job actor.

مخاطر المعنى: None; technical requirement and protected text unchanged.

### EA-212 — MINOR

الملف: [docs/developer-handbook/06-onboarding-labs/day-2-applications-ar.md](../../docs/developer-handbook/06-onboarding-labs/day-2-applications-ar.md). القسم: ## المُخرَج.

| قبل | بعد |
|---|---|
| شرح مكتوب مختصر للمشي في الرحلات | شرح مكتوب مختصر لخطوات الرحلات |

السبب: Natural written walkthrough.

مخاطر المعنى: None; technical requirement and protected text unchanged.

### EA-213 — MINOR

الملف: [docs/developer-handbook/06-onboarding-labs/day-2-applications-ar.md](../../docs/developer-handbook/06-onboarding-labs/day-2-applications-ar.md). القسم: ## المُخرَج.

| قبل | بعد |
|---|---|
| لكل سطح، أهم تلات شاشات | لكل واجهة تطبيق، أهم تلات شاشات |

السبب: Replace literal product surface.

مخاطر المعنى: None; technical requirement and protected text unchanged.

### EA-214 — MINOR

الملف: [docs/developer-handbook/06-onboarding-labs/day-2-applications-ar.md](../../docs/developer-handbook/06-onboarding-labs/day-2-applications-ar.md). القسم: ## مهام عملية.

| قبل | بعد |
|---|---|
| أي صورة اصطناعية | أي صورة تجريبية غير حقيقية |

السبب: Consistent synthetic example.

مخاطر المعنى: None; technical requirement and protected text unchanged.

### EA-215 — MINOR

الملف: [docs/developer-handbook/06-onboarding-labs/day-3-backend-security-ar.md](../../docs/developer-handbook/06-onboarding-labs/day-3-backend-security-ar.md). القسم: ## الأهداف.

| قبل | بعد |
|---|---|
| تتبّع Request واحد من الـ Controller للـ Database والرجوع. | تتبّع Request واحد من الـ Controller للـ Database، وبعدها تتبّع رجوع النتيجة. |

السبب: Natural full round-trip tracing.

مخاطر المعنى: None; technical requirement and protected text unchanged.

### EA-216 — MINOR

الملف: [docs/developer-handbook/06-onboarding-labs/day-4-development-ar.md](../../docs/developer-handbook/06-onboarding-labs/day-4-development-ar.md). القسم: ## مهام عملية.

| قبل | بعد |
|---|---|
| ارجع عن الكسر | رجّع التنفيذ لحالته السليمة |

السبب: Remove literal undo break.

مخاطر المعنى: None; technical requirement and protected text unchanged.

### EA-217 — MINOR

الملف: [docs/developer-handbook/06-onboarding-labs/day-4-development-ar.md](../../docs/developer-handbook/06-onboarding-labs/day-4-development-ar.md). القسم: ## مهام عملية.

| قبل | بعد |
|---|---|
| قبل ما الصور تتبني | قبل ما الـ Docker Images تتبني |

السبب: Distinguish deployment artifacts from media.

مخاطر المعنى: None; technical requirement and protected text unchanged.

### EA-218 — MINOR

الملف: [docs/developer-handbook/06-onboarding-labs/day-5-first-contribution-ar.md](../../docs/developer-handbook/06-onboarding-labs/day-5-first-contribution-ar.md). القسم: ## الخطوات.

| قبل | بعد |
|---|---|
| ومحصور في موديول أو سطح واحد | ومحصور في موديول أو واجهة تطبيق واحدة |

السبب: Replace literal surface.

مخاطر المعنى: None; technical requirement and protected text unchanged.

### EA-219 — MINOR

الملف: [docs/developer-handbook/06-onboarding-labs/day-5-first-contribution-ar.md](../../docs/developer-handbook/06-onboarding-labs/day-5-first-contribution-ar.md). القسم: ## الخطوات.

| قبل | بعد |
|---|---|
| خلّي القواعد اللي يملكها السيرفر على السيرفر | خلّي القواعد اللي السيرفر مسؤول عنها على السيرفر |

السبب: Natural server authority wording.

مخاطر المعنى: None; technical requirement and protected text unchanged.

### EA-220 — MINOR

الملف: [docs/developer-handbook/06-onboarding-labs/day-5-first-contribution-ar.md](../../docs/developer-handbook/06-onboarding-labs/day-5-first-contribution-ar.md). القسم: ## المُخرَج.

| قبل | بعد |
|---|---|
| فحوصات مطلوبة خضرا | فحوصات مطلوبة ناجحة |

السبب: Clear success wording.

مخاطر المعنى: None; technical requirement and protected text unchanged.

### EA-221 — MINOR

الملف: [docs/developer-handbook/06-onboarding-labs/day-5-first-contribution-ar.md](../../docs/developer-handbook/06-onboarding-labs/day-5-first-contribution-ar.md). القسم: ## فحص ذاتي.

| قبل | بعد |
|---|---|
| ماحطيتش إيدي على `main` | ماعدّلتش `main` |

السبب: Professional precise change boundary.

مخاطر المعنى: None; technical requirement and protected text unchanged.

### EA-222 — MINOR

الملف: [docs/developer-handbook/06-onboarding-labs/final-assessment-ar.md](../../docs/developer-handbook/06-onboarding-labs/final-assessment-ar.md). القسم: # التقييم النهائي.

| قبل | بعد |
|---|---|
| بيانات مصطنعة (Synthetic) | بيانات تجريبية غير حقيقية (Synthetic) |

السبب: Consistent synthetic-data terminology.

مخاطر المعنى: None; technical requirement and protected text unchanged.

### EA-223 — MINOR

الملف: [docs/developer-handbook/06-onboarding-labs/final-assessment-ar.md](../../docs/developer-handbook/06-onboarding-labs/final-assessment-ar.md). القسم: ### 4. التشغيل والـ Debug.

| قبل | بعد |
|---|---|
| ويحل عطل واحد محقون | ويحل عطل واحد المقيّم أدخله عمدًا |

السبب: Remove literal injected fault.

مخاطر المعنى: None; technical requirement and protected text unchanged.

### EA-224 — MINOR

الملف: [docs/developer-handbook/06-onboarding-labs/final-assessment-ar.md](../../docs/developer-handbook/06-onboarding-labs/final-assessment-ar.md). القسم: ### 4. التشغيل والـ Debug.

| قبل | بعد |
|---|---|
| نسخة من الـ Terminal | سجل مخرجات الـ Terminal |

السبب: Specify terminal transcript.

مخاطر المعنى: None; technical requirement and protected text unchanged.

### EA-225 — MINOR

الملف: [docs/developer-handbook/06-onboarding-labs/final-assessment-ar.md](../../docs/developer-handbook/06-onboarding-labs/final-assessment-ar.md). القسم: ### 6. كتابة الاختبارات وتشغيلها.

| قبل | بعد |
|---|---|
| مخرجات بتوري أحمر وبعدين أخضر | مخرجات بتوري فشل الاختبار وبعدين نجاحه |

السبب: Clarify test evidence.

مخاطر المعنى: None; technical requirement and protected text unchanged.

### EA-226 — MINOR

الملف: [docs/developer-handbook/06-onboarding-labs/final-assessment-ar.md](../../docs/developer-handbook/06-onboarding-labs/final-assessment-ar.md). القسم: ### 9. فهم الـ Deployment لبيئة المعاينة.

| قبل | بعد |
|---|---|
| لبناء الصور بـ commit SHA | لبناء الـ Docker Images بـ commit SHA |

السبب: Distinguish Docker artifacts.

مخاطر المعنى: None; technical requirement and protected text unchanged.

### EA-227 — MINOR

الملف: [docs/developer-handbook/05-developer-workflow/ci-cd-ar.md](../../docs/developer-handbook/05-developer-workflow/ci-cd-ar.md). القسم: ## من الدمج لبيئة المعاينة.

| قبل | بعد |
|---|---|
| وبيستنى صحة الـ API | وبيستنى فحوصات صحة الـ API تنجح |

السبب: Clarify literal technical wording in its full English section context.

مخاطر المعنى: None; original requirement and protected content preserved.

### EA-228 — MINOR

الملف: [docs/developer-handbook/06-onboarding-labs/day-2-applications-ar.md](../../docs/developer-handbook/06-onboarding-labs/day-2-applications-ar.md). القسم: ## المُخرَج.

| قبل | بعد |
|---|---|
| لقطات شاشة لبيانات اصطناعية بس | لقطات شاشة لبيانات تجريبية غير حقيقية بس |

السبب: Clarify literal technical wording in its full English section context.

مخاطر المعنى: None; original requirement and protected content preserved.

### EA-229 — MINOR

الملف: [docs/developer-handbook/06-onboarding-labs/final-assessment-ar.md](../../docs/developer-handbook/06-onboarding-labs/final-assessment-ar.md). القسم: ### 2. شرح مسارات العميل والمطبخ والموبايل والـ Back Office.

| قبل | بعد |
|---|---|
| كل شاشة/سطح | كل شاشة/واجهة تطبيق |

السبب: Clarify literal technical wording in its full English section context.

مخاطر المعنى: None; original requirement and protected content preserved.

### EA-230 — MINOR

الملف: [docs/developer-handbook/05-developer-workflow/test-execution-ar.md](../../docs/developer-handbook/05-developer-workflow/test-execution-ar.md). القسم: ## تصنيف الاختبارات (إيه بيثبت إيه).

| قبل | بعد |
|---|---|
| الصحة الساكنة | سلامة الأنواع والكود بالفحص الساكن |

السبب: Clarify literal technical wording in its full English section context.

مخاطر المعنى: None; original requirement and protected content preserved.

### EA-231 — MINOR

الملف: [docs/developer-handbook/07-reference/architecture-decisions-ar.md](../../docs/developer-handbook/07-reference/architecture-decisions-ar.md). القسم: # فهرس القرارات المعمارية وقرارات المنتج.

| قبل | بعد |
|---|---|
| القرارات موجودة في أربع أماكن | القرارات موجودة في أربعة أماكن |

السبب: تصحيح العدد مع أماكن.

مخاطر المعنى: Preserve English meaning; no executable or identifier change.

### EA-232 — MINOR

الملف: [docs/developer-handbook/07-reference/architecture-decisions-ar.md](../../docs/developer-handbook/07-reference/architecture-decisions-ar.md). القسم: # فهرس القرارات المعمارية وقرارات المنتج.

| قبل | بعد |
|---|---|
| السجل المقروء آليًا | السجل القابل للقراءة آليًا |

السبب: صياغة واضحة لـ machine-readable.

مخاطر المعنى: Preserve English meaning; no executable or identifier change.

### EA-233 — MINOR

الملف: [docs/developer-handbook/07-reference/architecture-decisions-ar.md](../../docs/developer-handbook/07-reference/architecture-decisions-ar.md). القسم: # فهرس القرارات المعمارية وقرارات المنتج.

| قبل | بعد |
|---|---|
| السجل البشري | السجل المكتوب للقراءة البشرية |

السبب: Human register يصف جمهور المستند مش نوع السجل.

مخاطر المعنى: Preserve English meaning; no executable or identifier change.

### EA-234 — MINOR

الملف: [docs/developer-handbook/07-reference/architecture-decisions-ar.md](../../docs/developer-handbook/07-reference/architecture-decisions-ar.md). القسم: # فهرس القرارات المعمارية وقرارات المنتج.

| قبل | بعد |
|---|---|
| مستندات موضوعية فيها تفاصيل قرارات بعينها | مستندات متخصصة فيها تفاصيل قرارات بعينها |

السبب: Topic documents مش مستندات محايدة أو موضوعية.

مخاطر المعنى: Preserve English meaning; no executable or identifier change.

### EA-235 — MINOR

الملف: [docs/developer-handbook/07-reference/architecture-decisions-ar.md](../../docs/developer-handbook/07-reference/architecture-decisions-ar.md). القسم: ## سجل القرارات (`planning/decisions.json`).

| قبل | بعد |
|---|---|
| القرار `ACCEPTED` لازم يشيل مرجع موافقة؛ والـ `OPEN` ميكونش ليه قيمة وليه Gate | القرار `ACCEPTED` لازم يكون معاه مرجع موافقة؛ والقرار `OPEN` لازم يكون من غير قيمة وله Gate |

السبب: إزالة التركيب الملتبس مع إبقاء الشرطين الصريحين.

مخاطر المعنى: Preserve English meaning; no executable or identifier change.

### EA-236 — MINOR

الملف: [docs/developer-handbook/07-reference/architecture-decisions-ar.md](../../docs/developer-handbook/07-reference/architecture-decisions-ar.md). القسم: ## سجل القرارات (`planning/decisions.json`).

| قبل | بعد |
|---|---|
| اتحل محله جزئيًا بـ D16 | D16 حلّ محل جزء منه |

السبب: تصحيح تركيب الاستبدال.

مخاطر المعنى: Preserve English meaning; no executable or identifier change.

### EA-237 — MINOR

الملف: [docs/developer-handbook/07-reference/architecture-decisions-ar.md](../../docs/developer-handbook/07-reference/architecture-decisions-ar.md). القسم: ## سجل القرارات (`planning/decisions.json`).

| قبل | بعد |
|---|---|
| والفلوس الحقيقية متقفّلة بـ Gate | والتعاملات المالية الحقيقية مش مفعّلة لحد استيفاء شروط التفعيل |

السبب: توضيح live money gated مع إبقاء شرط التفعيل.

مخاطر المعنى: Preserve English meaning; no executable or identifier change.

### EA-238 — MINOR

الملف: [docs/developer-handbook/07-reference/architecture-decisions-ar.md](../../docs/developer-handbook/07-reference/architecture-decisions-ar.md). القسم: ## سجل القرارات (`planning/decisions.json`).

| قبل | بعد |
|---|---|
| أيوه (اصطناعي (Synthetic)/معاينة بس) | أيوه (للاستخدام التجريبي غير الحقيقي (Synthetic)/بيئة المعاينة بس) |

السبب: إزالة تداخل الأقواس والتمييز عن الاستخدام الحقيقي.

مخاطر المعنى: Preserve English meaning; no executable or identifier change.

### EA-239 — MINOR

الملف: [docs/developer-handbook/07-reference/architecture-decisions-ar.md](../../docs/developer-handbook/07-reference/architecture-decisions-ar.md). القسم: ## سجل القرارات (`planning/decisions.json`).

| قبل | بعد |
|---|---|
| قيم اصطناعية في الكود | قيم تجريبية غير حقيقية (Synthetic) في الكود |

السبب: توضيح synthetic policy values دون وصفها ببيانات عملاء.

مخاطر المعنى: Preserve English meaning; no executable or identifier change.

### EA-240 — MINOR

الملف: [docs/developer-handbook/07-reference/architecture-decisions-ar.md](../../docs/developer-handbook/07-reference/architecture-decisions-ar.md). القسم: ## الـ ADRs في `docs/engineering/01-architecture.md`.

| قبل | بعد |
|---|---|
| موديولات الـ Domain بتتكلم عن طريق حزم | موديولات الـ Domain بتتواصل عن طريق حزم |

السبب: صياغة هندسية طبيعية للاتصال بين الموديولات.

مخاطر المعنى: Preserve English meaning; no executable or identifier change.

### EA-241 — MAJOR

الملف: [docs/developer-handbook/07-reference/architecture-decisions-ar.md](../../docs/developer-handbook/07-reference/architecture-decisions-ar.md). القسم: ## روابط الإحلال والاستبدال اللي ممكن يتحقق منها.

| قبل | بعد |
|---|---|
| "MFA للموظفين إلزامي عالميًا" | "MFA إلزامي لكل الموظفين" |

السبب: Universally mandatory تعني كل الموظفين مش النطاق الجغرافي.

مخاطر المعنى: Preserve English meaning; no executable or identifier change.

### EA-242 — MINOR

الملف: [docs/developer-handbook/07-reference/branch-cleanup-audit-2026-10-20-ar.md](../../docs/developer-handbook/07-reference/branch-cleanup-audit-2026-10-20-ar.md). القسم: ## اللي اتمسح (كل الـ Commits أسلاف لـ `develop` وقتها؛ الاسترجاع بـ `git branch <name> <sha>`).

| قبل | بعد |
|---|---|
| الـ Branches السبعة `test/doc001-*` كانت Branches اختبار فرض مؤقتة | الـ Branches السبعة `test/doc001-*` كانت Branches مؤقتة لاختبار تطبيق القواعد |

السبب: توضيح enforcement tests.

مخاطر المعنى: Preserve English meaning; no executable or identifier change.

### EA-243 — MINOR

الملف: [docs/developer-handbook/07-reference/branch-cleanup-audit-2026-10-20-ar.md](../../docs/developer-handbook/07-reference/branch-cleanup-audit-2026-10-20-ar.md). القسم: ## اللي اتمسح (كل الـ Commits أسلاف لـ `develop` وقتها؛ الاسترجاع بـ `git branch <name> <sha>`).

| قبل | بعد |
|---|---|
| وفضلت رؤوس الـ PRs على GitHub | وفضلت نسخ الـ Head بتاعة الـ PRs على GitHub |

السبب: إزالة الترجمة الحرفية لكلمة heads.

مخاطر المعنى: Preserve English meaning; no executable or identifier change.

### EA-244 — MINOR

الملف: [docs/developer-handbook/07-reference/documentation-map-ar.md](../../docs/developer-handbook/07-reference/documentation-map-ar.md). القسم: # خريطة التوثيق والمصادر المرجعية.

| قبل | بعد |
|---|---|
| للنية → المستندات الموضوعية | لمعرفة السلوك المقصود → المستندات المتخصصة |

السبب: توضيح intent وtopic documents في ترتيب المصادر.

مخاطر المعنى: Preserve English meaning; no executable or identifier change.

### EA-245 — MINOR

الملف: [docs/developer-handbook/07-reference/documentation-map-ar.md](../../docs/developer-handbook/07-reference/documentation-map-ar.md). القسم: # خريطة التوثيق والمصادر المرجعية.

| قبل | بعد |
|---|---|
| `docs/product/04-state-machines.md` (النية) | `docs/product/04-state-machines.md` (السلوك المقصود) |

السبب: توضيح دور مرجع state machines.

مخاطر المعنى: Preserve English meaning; no executable or identifier change.

### EA-246 — MINOR

الملف: [docs/developer-handbook/07-reference/documentation-map-ar.md](../../docs/developer-handbook/07-reference/documentation-map-ar.md). القسم: # خريطة التوثيق والمصادر المرجعية.

| قبل | بعد |
|---|---|
| `docs/product/06-permissions.md` (النية) | `docs/product/06-permissions.md` (الصلاحيات المقصودة) |

السبب: توضيح دور مرجع الصلاحيات.

مخاطر المعنى: Preserve English meaning; no executable or identifier change.

### EA-247 — MINOR

الملف: [docs/developer-handbook/07-reference/documentation-map-ar.md](../../docs/developer-handbook/07-reference/documentation-map-ar.md). القسم: ## التكرار المعروف.

| قبل | بعد |
|---|---|
| `docs/product/*` (النية) | `docs/product/*` (السلوك المقصود) |

السبب: توضيح توثيق intent.

مخاطر المعنى: Preserve English meaning; no executable or identifier change.

### EA-248 — MINOR

الملف: [docs/developer-handbook/07-reference/documentation-map-ar.md](../../docs/developer-handbook/07-reference/documentation-map-ar.md). القسم: ## .

| قبل | بعد |
|---|---|
| مستندات تاريخية أو لحظية (ماتعتبرهاش حالية) | مستندات تاريخية أو بتسجّل الحالة في وقت معيّن (ماتعتبرهاش حالية) |

السبب: Point-in-time مش لحظي أو realtime.

مخاطر المعنى: Preserve English meaning; no executable or identifier change.

### EA-249 — MINOR

الملف: [docs/developer-handbook/07-reference/frequently-asked-questions-ar.md](../../docs/developer-handbook/07-reference/frequently-asked-questions-ar.md). القسم: ## الإعداد والأدوات.

| قبل | بعد |
|---|---|
| بيتولّدوا في ملف env بتاعك انت والمتجاهل من git بواسطة | بتتولّد في ملف env خاص بيك وGit بيتجاهله، عن طريق |

السبب: تصحيح المطابقة وصياغة git-ignored.

مخاطر المعنى: Preserve English meaning; no executable or identifier change.

### EA-250 — MINOR

الملف: [docs/developer-handbook/07-reference/frequently-asked-questions-ar.md](../../docs/developer-handbook/07-reference/frequently-asked-questions-ar.md). القسم: ## المنتج والكود.

| قبل | بعد |
|---|---|
| الفلوس الحقيقية لسه متقفّل عليها بـ Gates | التعاملات المالية الحقيقية لسه مش مفعّلة لحد استيفاء شروط التفعيل |

السبب: توضيح تعطيل التعاملات مع إبقاء الـ gates.

مخاطر المعنى: Preserve English meaning; no executable or identifier change.

### EA-251 — MINOR

الملف: [docs/developer-handbook/07-reference/frequently-asked-questions-ar.md](../../docs/developer-handbook/07-reference/frequently-asked-questions-ar.md). القسم: ## الاختبارات والأدلة.

| قبل | بعد |
|---|---|
| **الـ CI الأخضر بيثبت إيه؟** بيثبت إن الـ Jobs المذكورة في | **نجاح فحوصات الـ CI بيثبت إيه؟** بيثبت إن الـ Jobs المذكورة في |

السبب: توضيح green CI بلغة مباشرة.

مخاطر المعنى: Preserve English meaning; no executable or identifier change.

### EA-252 — MINOR

الملف: [docs/developer-handbook/07-reference/frequently-asked-questions-ar.md](../../docs/developer-handbook/07-reference/frequently-asked-questions-ar.md). القسم: ## الاختبارات والأدلة.

| قبل | بعد |
|---|---|
| ببيانات اصطناعية (Synthetic) وLogs متنضّفة | ببيانات تجريبية غير حقيقية (Synthetic Data) وLogs بعد إزالة المعلومات الحساسة منها |

السبب: توضيح synthetic وsanitized logs دون تخفيف التحذير.

مخاطر المعنى: Preserve English meaning; no executable or identifier change.

### EA-253 — MAJOR

الملف: [docs/developer-handbook/07-reference/frequently-asked-questions-ar.md](../../docs/developer-handbook/07-reference/frequently-asked-questions-ar.md). القسم: ## Git والـ PR والـ CI.

| قبل | بعد |
|---|---|
| وبعدها الـ Images بـ Commit SHA | وبعدها Docker Images متوسمة بالـ Commit SHA |

السبب: التمييز بين Docker Images وصور الميديا.

مخاطر المعنى: Preserve English meaning; no executable or identifier change.

### EA-254 — MINOR

الملف: [docs/developer-handbook/07-reference/frequently-asked-questions-ar.md](../../docs/developer-handbook/07-reference/frequently-asked-questions-ar.md). القسم: ## Git والـ PR والـ CI.

| قبل | بعد |
|---|---|
| الـ Database مابترجعش لورا أوتوماتيك | مفيش Rollback تلقائي للـ Database |

السبب: إزالة التعبير المبهم مع إبقاء عدم الرجوع التلقائي.

مخاطر المعنى: Preserve English meaning; no executable or identifier change.

### EA-255 — MINOR

الملف: [docs/developer-handbook/07-reference/frequently-asked-questions-ar.md](../../docs/developer-handbook/07-reference/frequently-asked-questions-ar.md). القسم: ## المعاينة والتشغيل.

| قبل | بعد |
|---|---|
| لا: بيانات اصطناعية، وبانر Demo | لا: بيانات تجريبية غير حقيقية (Synthetic Data)، وبانر Demo |

السبب: تمييز بيانات المعاينة عن بيانات العملاء.

مخاطر المعنى: Preserve English meaning; no executable or identifier change.

### EA-256 — MAJOR

الملف: [docs/developer-handbook/07-reference/fresh-developer-simulation-ar.md](../../docs/developer-handbook/07-reference/fresh-developer-simulation-ar.md). القسم: # محاكاة المطوّر الجديد (الجاهزية للأسبوع الأول).

| قبل | بعد |
|---|---|
| والصف الناقص بيفشل بأمان إلى REQUIRED | ولو صف السياسة مش موجود، النظام بيطبّق REQUIRED كاختيار آمن |

السبب: fail safe يصف اختيار REQUIRED مش فشل العملية.

مخاطر المعنى: Preserve English meaning; no executable or identifier change.

### EA-257 — MINOR

الملف: [docs/developer-handbook/07-reference/fresh-developer-simulation-ar.md](../../docs/developer-handbook/07-reference/fresh-developer-simulation-ar.md). القسم: # محاكاة المطوّر الجديد (الجاهزية للأسبوع الأول).

| قبل | بعد |
|---|---|
| CI، Images بـ SHA | CI، Docker Images متوسمة بالـ SHA |

السبب: توضيح نوع الصور.

مخاطر المعنى: Preserve English meaning; no executable or identifier change.

### EA-258 — MINOR

الملف: [docs/developer-handbook/07-reference/fresh-developer-simulation-ar.md](../../docs/developer-handbook/07-reference/fresh-developer-simulation-ar.md). القسم: ## عيوب التوثيق اللي اتلاقت واللي اتعمل فيها.

| قبل | بعد |
|---|---|
| مسارات Truncated/nonexistent في صفحة الفجوات | مسارات ناقصة أو مش موجودة في صفحة الفجوات |

السبب: إزالة خلط إنجليزي غير ضروري.

مخاطر المعنى: Preserve English meaning; no executable or identifier change.

### EA-259 — MINOR

الملف: [docs/developer-handbook/07-reference/fresh-developer-simulation-ar.md](../../docs/developer-handbook/07-reference/fresh-developer-simulation-ar.md). القسم: ## المتطلبات المسبقة الخفية اللي اتكشفت.

| قبل | بعد |
|---|---|
| و`gh` متوثّق | و`gh` مسجّل دخول بحساب مصرح له |

السبب: Authenticated مش موثّق كمستند.

مخاطر المعنى: Preserve English meaning; no executable or identifier change.

### EA-260 — MINOR

الملف: [docs/developer-handbook/07-reference/independent-reviews-ar.md](../../docs/developer-handbook/07-reference/independent-reviews-ar.md). القسم: # سجل المراجعات المستقلة (الدليل وDOC-001).

| قبل | بعد |
|---|---|
| مقدروش يشغّلوا Stack بتاع Java/Docker | مقدروش يشغّلوا الـ Stack بتاع Java/Docker |

السبب: إضافة أداة التعريف الناقصة.

مخاطر المعنى: Preserve English meaning; no executable or identifier change.

### EA-261 — MAJOR

الملف: [docs/developer-handbook/07-reference/independent-reviews-ar.md](../../docs/developer-handbook/07-reference/independent-reviews-ar.md). القسم: ## الجولة 1 (الـ Commit `6c94a7c`).

| قبل | بعد |
|---|---|
| اقتباسات من غير المستندات | الاستشهاد بملفات مش مستندات |

السبب: non-doc citations تعني مراجع غير توثيقية.

مخاطر المعنى: Preserve English meaning; no executable or identifier change.

### EA-262 — MINOR

الملف: [docs/developer-handbook/07-reference/independent-reviews-ar.md](../../docs/developer-handbook/07-reference/independent-reviews-ar.md). القسم: ## الجولة 2 (الـ Commit `5238465`).

| قبل | بعد |
|---|---|
| سحب موافقة المراجعة بعد تشغيل أخضر مش بيتعاد تقييمه | سحب موافقة المراجعة بعد نجاح الفحص مش بيتعاد تقييمه |

السبب: توضيح green run.

مخاطر المعنى: Preserve English meaning; no executable or identifier change.

### EA-263 — MINOR

الملف: [docs/developer-handbook/07-reference/independent-reviews-ar.md](../../docs/developer-handbook/07-reference/independent-reviews-ar.md). القسم: ## الجولة 2 (الـ Commit `5238465`).

| قبل | بعد |
|---|---|
| فشل لسبب ده | فشل للسبب ده |

السبب: تصحيح نحوي.

مخاطر المعنى: Preserve English meaning; no executable or identifier change.

### EA-264 — MINOR

الملف: [docs/developer-handbook/07-reference/inventory-and-gaps-ar.md](../../docs/developer-handbook/07-reference/inventory-and-gaps-ar.md). القسم: ## 2. تعارضات بين التوثيق والكود (مستندات المصدر لسه محتاجة تتصلّح).

| قبل | بعد |
|---|---|
| بلاطات النية ("وجبة ليا/لينا"، "كمّل سفرتي"، "جهّز تجمّع") | بطاقات اختيار الغرض من الطلب ("وجبة ليا/لينا"، "كمّل سفرتي"، "جهّز عزومة") |

السبب: Intent tiles ترجمة حرفية مربكة، وgathering عزومة في سياق المنتج.

مخاطر المعنى: Preserve English meaning; no executable or identifier change.

### EA-265 — MAJOR

الملف: [docs/developer-handbook/07-reference/inventory-and-gaps-ar.md](../../docs/developer-handbook/07-reference/inventory-and-gaps-ar.md). القسم: ## 2. تعارضات بين التوثيق والكود (مستندات المصدر لسه محتاجة تتصلّح).

| قبل | بعد |
|---|---|
| مفيش منتج تموين (Catering) | مفيش منتج مخصص لتجهيز أكل المناسبات (Catering) |

السبب: تموين قد تفهم كتوريد سلع مدعمة.

مخاطر المعنى: Preserve English meaning; no executable or identifier change.

### EA-266 — MINOR

الملف: [docs/developer-handbook/07-reference/inventory-and-gaps-ar.md](../../docs/developer-handbook/07-reference/inventory-and-gaps-ar.md). القسم: ## 4. مناطق التغطية الآلية فيها ضعيفة.

| قبل | بعد |
|---|---|
| واختبارات الـ Proxy بتتخطّى لما صور caddy/python مش موجودة | واختبارات الـ Proxy بتتخطّى لما Docker Images الخاصة بـ caddy/python مش موجودة |

السبب: تمييز Docker Images عن الميديا.

مخاطر المعنى: Preserve English meaning; no executable or identifier change.

### EA-267 — MAJOR

الملف: [docs/developer-handbook/07-reference/inventory-and-gaps-ar.md](../../docs/developer-handbook/07-reference/inventory-and-gaps-ar.md). القسم: ## 5. اللي NOT VERIFIED في الدليل كله.

| قبل | بعد |
|---|---|
| وتصحيح الـ IDE لـ API الموجود في Container | وتتبّع أخطاء الـ API اللي شغّال في Container باستخدام الـ IDE |

السبب: Debugging مش تصحيح الـ IDE نفسه.

مخاطر المعنى: Preserve English meaning; no executable or identifier change.

### EA-268 — MINOR

الملف: [docs/developer-handbook/07-reference/inventory-and-gaps-ar.md](../../docs/developer-handbook/07-reference/inventory-and-gaps-ar.md). القسم: ## 6. التكرار.

| قبل | بعد |
|---|---|
| عشان نتجنب انجراف جديد | عشان نتجنب اختلاف النسخ عن بعض مع الوقت |

السبب: إزالة الترجمة الحرفية لـ drift.

مخاطر المعنى: Preserve English meaning; no executable or identifier change.

### EA-269 — MINOR

الملف: [docs/developer-handbook/07-reference/implementation-status-ar.md](../../docs/developer-handbook/07-reference/implementation-status-ar.md). القسم: # حالة التنفيذ.

| قبل | بعد |
|---|---|
| ومعناها أبدًا مش "شغّال فعليًا": مفيش بيئة Production، والفلوس الحقيقية متقفّل عليها بـ Gate (D16)، وبيئة PREVIEW ببيانات اصطناعية (Synthetic) بس هي اللي اتنشرت | وعمرها ما تعني "شغّال في بيئة الإنتاج": مفيش بيئة Production، والتعاملات المالية الحقيقية مش مفعّلة لحد استيفاء شروط التفعيل (D16)، وبيئة PREVIEW ببيانات تجريبية غير حقيقية (Synthetic Data) بس هي اللي اتنشرت |

السبب: توضيح live وتمييز التنفيذ عن نجاح الاختبارات أو التشغيل الحقيقي.

مخاطر المعنى: Preserve English meaning; no executable or identifier change.

### EA-270 — MINOR

الملف: [docs/developer-handbook/07-reference/implementation-status-ar.md](../../docs/developer-handbook/07-reference/implementation-status-ar.md). القسم: ## قدرات الـ Backend.

| قبل | بعد |
|---|---|
| وـ Job الانتهاء | والـ Job الخاص بانتهاء المهلة |

السبب: تصحيح تركيب التعريف وتوضيح expiry job.

مخاطر المعنى: Preserve English meaning; no executable or identifier change.

### EA-271 — MINOR

الملف: [docs/developer-handbook/07-reference/implementation-status-ar.md](../../docs/developer-handbook/07-reference/implementation-status-ar.md). القسم: ## قدرات الـ Backend.

| قبل | بعد |
|---|---|
| وـ Outbox Dispatcher | والـ Outbox Dispatcher |

السبب: تصحيح أداة التعريف.

مخاطر المعنى: Preserve English meaning; no executable or identifier change.

### EA-272 — MINOR

الملف: [docs/developer-handbook/07-reference/implementation-status-ar.md](../../docs/developer-handbook/07-reference/implementation-status-ar.md). القسم: ## قدرات الـ Backend.

| قبل | بعد |
|---|---|
| وـ Feature Flags | والـ Feature Flags |

السبب: تصحيح أداة التعريف.

مخاطر المعنى: Preserve English meaning; no executable or identifier change.

### EA-273 — MINOR

الملف: [docs/developer-handbook/07-reference/implementation-status-ar.md](../../docs/developer-handbook/07-reference/implementation-status-ar.md). القسم: ## قدرات الـ Backend.

| قبل | بعد |
|---|---|
| قدرة نايمة: | إمكانية موجودة تقنيًا لكنها مش مفعّلة: |

السبب: توضيح dormant capability.

مخاطر المعنى: Preserve English meaning; no executable or identifier change.

### EA-274 — MINOR

الملف: [docs/developer-handbook/07-reference/implementation-status-ar.md](../../docs/developer-handbook/07-reference/implementation-status-ar.md). القسم: ## قدرات الـ Backend.

| قبل | بعد |
|---|---|
| **الفلوس الحقيقية متقفّل عليها** | **التعاملات المالية الحقيقية مش مفعّلة لحد استيفاء شروط التفعيل** |

السبب: توضيح التحذير مع الحفاظ على منع التفعيل.

مخاطر المعنى: Preserve English meaning; no executable or identifier change.

### EA-275 — MINOR

الملف: [docs/developer-handbook/07-reference/implementation-status-ar.md](../../docs/developer-handbook/07-reference/implementation-status-ar.md). القسم: ## البيئات والعمليات.

| قبل | بعد |
|---|---|
| المعاينة (بيانات اصطناعية، | المعاينة (بيانات تجريبية غير حقيقية (Synthetic Data)، |

السبب: توحيد معنى بيانات المعاينة.

مخاطر المعنى: Preserve English meaning; no executable or identifier change.

### EA-276 — MINOR

الملف: [docs/developer-handbook/07-reference/implementation-status-ar.md](../../docs/developer-handbook/07-reference/implementation-status-ar.md). القسم: ## التعارضات اللي اتلاقت بين المستندات والكود.

| قبل | بعد |
|---|---|
| NOT VERIFIED إزاي بيعمل الـ Native Builds Authentication على المعاينة | NOT VERIFIED إزاي الـ Native Builds بتعمل Authentication على المعاينة |

السبب: تصحيح ترتيب الفاعل والفعل.

مخاطر المعنى: Preserve English meaning; no executable or identifier change.

### EA-277 — MAJOR

الملف: [docs/developer-handbook/07-reference/requirements-traceability-ar.md](../../docs/developer-handbook/07-reference/requirements-traceability-ar.md). القسم: ## 2. الـ Authorization (الأدوار والصلاحيات ونطاق الـ Object).

| قبل | بعد |
|---|---|
| والـ Objects الغريبة بترد 404 | والـ Objects اللي خارج نطاق صلاحية المستخدم بترجّع 404 |

السبب: Foreign objects تعني خارج نطاق صلاحية المستخدم مش غريبة.

مخاطر المعنى: Preserve English meaning; no executable or identifier change.

### EA-278 — MINOR

الملف: [docs/developer-handbook/07-reference/requirements-traceability-ar.md](../../docs/developer-handbook/07-reference/requirements-traceability-ar.md). القسم: ## 2. الـ Authorization (الأدوار والصلاحيات ونطاق الـ Object).

| قبل | بعد |
|---|---|
| والأدوار في الـ Claims بتتتجاهل | والأدوار في الـ Claims بيتم تجاهلها |

السبب: تصحيح صياغة مبنية للمجهول.

مخاطر المعنى: Preserve English meaning; no executable or identifier change.

### EA-279 — MAJOR

الملف: [docs/developer-handbook/07-reference/requirements-traceability-ar.md](../../docs/developer-handbook/07-reference/requirements-traceability-ar.md). القسم: ## 6. التسعير وعروض الأسعار اللي مبقتش مواكبة للشروط الحالية (Stale quotes).

| قبل | بعد |
|---|---|
| BR-019 السيرفر بيحسب الجنيه كأرقام صحيحة | BR-019 السيرفر بيحسب مبالغ EGP بوحدات صحيحة |

السبب: إبقاء integer EGP كما في المصدر دون الإيحاء بتقريب لجنيه كامل.

مخاطر المعنى: Preserve English meaning; no executable or identifier change.

### EA-280 — MAJOR

الملف: [docs/developer-handbook/07-reference/requirements-traceability-ar.md](../../docs/developer-handbook/07-reference/requirements-traceability-ar.md). القسم: ## 7. حجز السعة.

| قبل | بعد |
|---|---|
| BR-025 آخر حصة متزامنة مابتتباعش زيادة | BR-025 الطلبات المتزامنة على آخر حصة ماينفعش تبيع أكتر من المتاح |

السبب: الحصة نفسها مش متزامنة؛ الطلبات هي المتزامنة.

مخاطر المعنى: Preserve English meaning; no executable or identifier change.

### EA-281 — MINOR

الملف: [docs/developer-handbook/07-reference/requirements-traceability-ar.md](../../docs/developer-handbook/07-reference/requirements-traceability-ar.md). القسم: ## 7. حجز السعة.

| قبل | بعد |
|---|---|
| BR-029 الإفراج مرة واحدة بالظبط؛ والمستهلك يفضل مستهلك | BR-029 تحرير المخزون المحجوز مرة واحدة بالظبط؛ والمخزون المستهلك يفضل مستهلك |

السبب: تمييز release عن الإفراج وتوضيح حالة المخزون.

مخاطر المعنى: Preserve English meaning; no executable or identifier change.

### EA-282 — MAJOR

الملف: [docs/developer-handbook/07-reference/requirements-traceability-ar.md](../../docs/developer-handbook/07-reference/requirements-traceability-ar.md). القسم: ## 12. MFA.

| قبل | بعد |
|---|---|
| الصف الناقص بيفشل بأمان إلى REQUIRED | لو صف السياسة مش موجود، النظام بيطبّق REQUIRED كاختيار آمن |

السبب: fail safe تعني اختيار السياسة الأشد، مش فشل مجهول.

مخاطر المعنى: Preserve English meaning; no executable or identifier change.

### EA-283 — MAJOR

الملف: [docs/developer-handbook/07-reference/requirements-traceability-ar.md](../../docs/developer-handbook/07-reference/requirements-traceability-ar.md). القسم: ## 12. MFA.

| قبل | بعد |
|---|---|
| إعادة ضبط الأدمن بتشيل العامل، وتنهي الجلسات، ومتدقَّقة | إعادة ضبط MFA بواسطة الأدمن بتشيل عامل التحقق، وبتنهي الجلسات، وبتتسجّل في سجل التدقيق |

السبب: تمييز إجراء الأدمن عن إعادة ضبط حساب الأدمن وتوضيح audited.

مخاطر المعنى: Preserve English meaning; no executable or identifier change.

### EA-284 — MINOR

الملف: [docs/developer-handbook/07-reference/requirements-traceability-ar.md](../../docs/developer-handbook/07-reference/requirements-traceability-ar.md). القسم: ## 5. تسجيل الأوردر.

| قبل | بعد |
|---|---|
| والرد المجهول بيتحلّ بالبحث | ولو نتيجة الطلب مش معروفة، بيتحقق منها بالبحث |

السبب: Unknown response مش رد مجهول الهوية.

مخاطر المعنى: Preserve English meaning; no executable or identifier change.

### EA-285 — MAJOR

الملف: [docs/developer-handbook/07-reference/implementation-status-ar.md](../../docs/developer-handbook/07-reference/implementation-status-ar.md). القسم: ## قدرات الـ Backend.

| قبل | بعد |
|---|---|
| حماية آخر أدمن والتصعيد الذاتي | حماية آخر أدمن ومنع التصعيد الذاتي للصلاحيات |

السبب: Independent review: avoid security-control polarity ambiguity.

مخاطر المعنى: English technical meaning preserved; protected text unchanged.

### EA-286 — MAJOR

الملف: [docs/developer-handbook/07-reference/implementation-status-ar.md](../../docs/developer-handbook/07-reference/implementation-status-ar.md). القسم: ## قدرات الـ Backend.

| قبل | بعد |
|---|---|
| وإعادة ضبط الأدمن | وإعادة ضبط MFA بواسطة الأدمن |

السبب: Independent review: clarify actor and target of reset.

مخاطر المعنى: English technical meaning preserved; protected text unchanged.

### EA-287 — MAJOR

الملف: [docs/developer-handbook/07-reference/requirements-traceability-ar.md](../../docs/developer-handbook/07-reference/requirements-traceability-ar.md). القسم: ## 6. التسعير وعروض الأسعار اللي مبقتش مواكبة للشروط الحالية (Stale quotes).

| قبل | بعد |
|---|---|
| مبالغ EGP بوحدات صحيحة | مبالغ EGP بالقروش كأرقام صحيحة |

السبب: Independent review: full handbook specifies integer piastres.

مخاطر المعنى: English technical meaning preserved; protected text unchanged.

### EA-288 — MINOR

الملف: [docs/developer-handbook/07-reference/architecture-decisions-ar.md](../../docs/developer-handbook/07-reference/architecture-decisions-ar.md). القسم: ## سجل القرارات (`planning/decisions.json`).

| قبل | بعد |
|---|---|
| أيوه (للاستخدام التجريبي غير الحقيقي (Synthetic)/بيئة المعاينة بس) | أيوه (للاستخدام ببيانات تجريبية غير حقيقية (Synthetic Data) أو في بيئة المعاينة بس) |

السبب: Independent review: natural synthetic/preview restriction.

مخاطر المعنى: English technical meaning preserved; protected text unchanged.

### EA-289 — MINOR

الملف: [docs/developer-handbook/05-developer-workflow/documentation-maintenance-ar.md](../../docs/developer-handbook/05-developer-workflow/documentation-maintenance-ar.md). القسم: ## حالة حماية الـ Branch.

| قبل | بعد |
|---|---|
| إلغاء الموافقة بعد تشغيل DOC-001 أخضر | إلغاء الموافقة بعد نجاح فحص DOC-001 |

السبب: Use successful check instead of color shorthand.

مخاطر المعنى: English technical meaning preserved; protected text unchanged.

### EA-290 — MINOR

الملف: [docs/developer-handbook/05-developer-workflow/documentation-policy-ar.md](../../docs/developer-handbook/05-developer-workflow/documentation-policy-ar.md). القسم: ## 4. التحقق من التوثيق (بيشتغل دايمًا).

| قبل | بعد |
|---|---|
| عمرك ما تقرا الفحص الأخضر على إنه شهادة دقة | عمرك ما تعتبر نجاح الفحص شهادة على دقة المحتوى |

السبب: Explain green check without color shorthand.

مخاطر المعنى: English technical meaning preserved; protected text unchanged.

### EA-291 — MINOR

الملف: [docs/developer-handbook/05-developer-workflow/documentation-policy-ar.md](../../docs/developer-handbook/05-developer-workflow/documentation-policy-ar.md). القسم: ## 5. حوكمة البوابة نفسها.

| قبل | بعد |
|---|---|
| فالنتيجة الخضرا ممكن تعيش بعد إلغاء لاحق للموافقة | فنتيجة النجاح ممكن تفضل ظاهرة حتى بعد إلغاء الموافقة |

السبب: Natural rendering of green result outliving approval dismissal.

مخاطر المعنى: English technical meaning preserved; protected text unchanged.

### EA-292 — MINOR

الملف: [docs/developer-handbook/01-business/target-users-ar.md](../../docs/developer-handbook/01-business/target-users-ar.md). القسم: ## افتراضات الجغرافيا والحجم.

| قبل | بعد |
|---|---|
| ببيانات اصطناعية (Synthetic) | ببيانات تجريبية غير حقيقية (Synthetic Data) |

السبب: Glossary: synthetic development zones are not real service areas.

مخاطر المعنى: English technical meaning preserved; protected text unchanged.

### EA-293 — MINOR

الملف: [docs/developer-handbook/02-user-journeys/customer-mobile-ar.md](../../docs/developer-handbook/02-user-journeys/customer-mobile-ar.md). القسم: ## الرحلات.

| قبل | بعد |
|---|---|
| (بيانات اصطناعية (Synthetic)، للـ CI بس) | (بيانات تجريبية غير حقيقية (Synthetic Data)، للـ CI بس) |

السبب: Glossary: distinguish synthetic native seed data.

مخاطر المعنى: English technical meaning preserved; protected text unchanged.

### EA-294 — MINOR

الملف: [docs/developer-handbook/01-business/product-vision-ar.md](../../docs/developer-handbook/01-business/product-vision-ar.md). القسم: # رؤية المنتج.

| قبل | بعد |
|---|---|
| ببيانات اصطناعية (Synthetic) | ببيانات تجريبية غير حقيقية (Synthetic Data) |

السبب: Glossary: synthetic MVP data.

مخاطر المعنى: English technical meaning preserved; protected text unchanged.

### EA-295 — MINOR

الملف: [docs/developer-handbook/01-business/product-vision-ar.md](../../docs/developer-handbook/01-business/product-vision-ar.md). القسم: ## مراحل الإصدار.

| قبل | بعد |
|---|---|
| المسارات الأساسية ببيانات اصطناعية؛ | المسارات الأساسية ببيانات تجريبية غير حقيقية؛ |

السبب: Glossary: synthetic scope before live-money gates.

مخاطر المعنى: English technical meaning preserved; protected text unchanged.

### EA-296 — MINOR

الملف: [docs/developer-handbook/02-user-journeys/cook-operations-ar.md](../../docs/developer-handbook/02-user-journeys/cook-operations-ar.md). القسم: ## CO-6 تقارير الكاش والعربون من المطبخ.

| قبل | بعد |
|---|---|
| IMPLEMENTED (بيانات اصطناعية (Synthetic)؛ | IMPLEMENTED (بيانات تجريبية غير حقيقية (Synthetic Data)؛ |

السبب: Glossary: synthetic implementation status preserved.

مخاطر المعنى: English technical meaning preserved; protected text unchanged.

### EA-297 — MINOR

الملف: [docs/developer-handbook/02-user-journeys/cook-operations-ar.md](../../docs/developer-handbook/02-user-journeys/cook-operations-ar.md). القسم: ## CO-2 موافقة الـ Staff وإنشاء المطبخ.

| قبل | بعد |
|---|---|
| `FORBIDDEN` مراجعة النفس | `FORBIDDEN` لما الشخص يراجع طلبه بنفسه |

السبب: Clarify self-review refusal.

مخاطر المعنى: English technical meaning preserved; protected text unchanged.

### EA-298 — MINOR

الملف: [docs/developer-handbook/05-developer-workflow/local-environment-ar.md](../../docs/developer-handbook/05-developer-workflow/local-environment-ar.md). القسم: # البيئة المحلية.

| قبل | بعد |
|---|---|
| بيانات تجريبية غير حقيقية (Synthetic) | بيانات تجريبية غير حقيقية (Synthetic Data) |

السبب: Glossary: canonical Synthetic Data label.

مخاطر المعنى: English technical meaning preserved; protected text unchanged.

### EA-299 — MINOR

الملف: [docs/developer-handbook/05-developer-workflow/preview-environment-ar.md](../../docs/developer-handbook/05-developer-workflow/preview-environment-ar.md). القسم: # بيئة المعاينة.

| قبل | بعد |
|---|---|
| بيانات تجريبية غير حقيقية (Synthetic) | بيانات تجريبية غير حقيقية (Synthetic Data) |

السبب: Glossary: canonical Synthetic Data label.

مخاطر المعنى: English technical meaning preserved; protected text unchanged.

### EA-300 — MINOR

الملف: [docs/developer-handbook/05-developer-workflow/ci-cd-ar.md](../../docs/developer-handbook/05-developer-workflow/ci-cd-ar.md). القسم: ## حدود الجاهزية للـ Production.

| قبل | بعد |
|---|---|
| بيانات تجريبية غير حقيقية (Synthetic) | بيانات تجريبية غير حقيقية (Synthetic Data) |

السبب: Glossary: canonical Synthetic Data label.

مخاطر المعنى: English technical meaning preserved; protected text unchanged.

### EA-301 — MINOR

الملف: [docs/developer-handbook/06-onboarding-labs/final-assessment-ar.md](../../docs/developer-handbook/06-onboarding-labs/final-assessment-ar.md). القسم: # التقييم النهائي.

| قبل | بعد |
|---|---|
| بيانات تجريبية غير حقيقية (Synthetic) | بيانات تجريبية غير حقيقية (Synthetic Data) |

السبب: Glossary: canonical Synthetic Data label.

مخاطر المعنى: English technical meaning preserved; protected text unchanged.

### EA-302 — MAJOR

الملف: [docs/developer-handbook/07-reference/implementation-status-ar.md](../../docs/developer-handbook/07-reference/implementation-status-ar.md). القسم: ## قدرات الـ Backend.

| قبل | بعد |
|---|---|
| استلام المطبخ، النزاعات، التسوية، تبليغ الاسترداد | استلام المطبخ، النزاعات، المطابقة، تبليغ الاسترداد |

السبب: Reconciliation of deposit reports is distinct from commission Settlement.

مخاطر المعنى: Preserve deposit dispute workflow; do not imply money settlement.

### EA-303 — MAJOR

الملف: [docs/developer-handbook/03-architecture/database-architecture-ar.md](../../docs/developer-handbook/03-architecture/database-architecture-ar.md). القسم: القسم المقابل للتعبير في الملف.

| قبل | بعد |
|---|---|
| حالات التسوية (Reconciliation) | حالات المطابقة (Reconciliation) |

السبب: Payment reconciliation differs from commission settlement.

مخاطر المعنى: Clarifies distinct technical concepts without changing behaviour or executable text.

### EA-304 — MAJOR

الملف: [docs/developer-handbook/03-architecture/database-architecture-ar.md](../../docs/developer-handbook/03-architecture/database-architecture-ar.md). القسم: القسم المقابل للتعبير في الملف.

| قبل | بعد |
|---|---|
| وقت التجهيز المسبق | مهلة الطلب المسبق (lead time) |

السبب: Lead time sets cutoff before slot; not cooking preparation time.

مخاطر المعنى: Clarifies distinct technical concepts without changing behaviour or executable text.

### EA-305 — MAJOR

الملف: [docs/developer-handbook/03-architecture/database-architecture-ar.md](../../docs/developer-handbook/03-architecture/database-architecture-ar.md). القسم: القسم المقابل للتعبير في الملف.

| قبل | بعد |
|---|---|
| سكريبت التسوية | سكريبت مطابقة البيانات |

السبب: Counter reconciliation checks allocations; no financial settlement occurs.

مخاطر المعنى: Clarifies distinct technical concepts without changing behaviour or executable text.

### EA-306 — MAJOR

الملف: [docs/developer-handbook/03-architecture/database-architecture-ar.md](../../docs/developer-handbook/03-architecture/database-architecture-ar.md). القسم: القسم المقابل للتعبير في الملف.

| قبل | بعد |
|---|---|
| السكريبت `scripts/db-backup.sh` بيكتب | المقصود بالتسوية في عنوان القسم هو مطابقة البيانات (Reconciliation)، مش التسوية المالية (Settlement). السكريبت `scripts/db-backup.sh` بيكتب |

السبب: Clarify legacy heading in existing paragraph while preserving its anchor and structural parity.

مخاطر المعنى: Clarifies distinct technical concepts without changing behaviour or executable text.

### EA-307 — MAJOR

الملف: [docs/developer-handbook/03-architecture/database-architecture-ar.md](../../docs/developer-handbook/03-architecture/database-architecture-ar.md). القسم: القسم المقابل للتعبير في الملف.

| قبل | بعد |
|---|---|
| كل أعداد التسوية تكون صفر | كل أعداد الاختلافات اللي فحص المطابقة بيطلعها تكون صفر |

السبب: Zero applies to reconciliation discrepancies, not settlement counts.

مخاطر المعنى: Clarifies distinct technical concepts without changing behaviour or executable text.

### EA-308 — MAJOR

الملف: [docs/developer-handbook/03-architecture/infrastructure-and-deployment-ar.md](../../docs/developer-handbook/03-architecture/infrastructure-and-deployment-ar.md). القسم: القسم المقابل للتعبير في الملف.

| قبل | بعد |
|---|---|
| وتسوية قراءة بس | وفحص مطابقة البيانات للقراءة بس |

السبب: Provisioning performs read-only reconciliation, not settlement.

مخاطر المعنى: Clarifies distinct technical concepts without changing behaviour or executable text.

### EA-309 — MAJOR

الملف: [docs/developer-handbook/03-architecture/infrastructure-and-deployment-ar.md](../../docs/developer-handbook/03-architecture/infrastructure-and-deployment-ar.md). القسم: القسم المقابل للتعبير في الملف.

| قبل | بعد |
|---|---|
| وبيعمل له تسوية | وبيفحص مطابقة بياناته |

السبب: Restore reconciliation verifies data rather than financial settlement.

مخاطر المعنى: Clarifies distinct technical concepts without changing behaviour or executable text.

### EA-310 — MINOR

الملف: [docs/developer-handbook/02-user-journeys/customer-web-ar.md](../../docs/developer-handbook/02-user-journeys/customer-web-ar.md). القسم: Independent follow-up.

| قبل | بعد |
|---|---|
| اختيار اختيار غرض الطلب اختياريًا | اختيار كارت لغرض الطلب اختياريًا |

السبب: Introduced duplicated wording.

مخاطر المعنى: Ambiguous presence/removal of security fields; repaired and independently rechecked.

### EA-311 — MAJOR

الملف: [docs/developer-handbook/02-user-journeys/customer-mobile-ar.md](../../docs/developer-handbook/02-user-journeys/customer-mobile-ar.md). القسم: Independent follow-up.

| قبل | بعد |
|---|---|
| ويشيل الـ `state` بتاعنا وكود `code` | ويحتوي على الـ `state` بتاعنا وكود `code` |

السبب: carry must not be read as remove in redirect security check.

مخاطر المعنى: Ambiguous presence/removal of security fields; repaired and independently rechecked.

### EA-312 — MAJOR

الملف: [docs/developer-handbook/02-user-journeys/customer-web-ar.md](../../docs/developer-handbook/02-user-journeys/customer-web-ar.md). القسم: Independent follow-up.

| قبل | بعد |
|---|---|
| كل طلب بيشيل `Accept-Language` | كل طلب بيحتوي على `Accept-Language` |

السبب: Preserve required header presence.

مخاطر المعنى: Ambiguous presence/removal of security fields; repaired and independently rechecked.

### EA-313 — MAJOR

الملف: [docs/developer-handbook/02-user-journeys/complete-order-journey-ar.md](../../docs/developer-handbook/02-user-journeys/complete-order-journey-ar.md). القسم: Independent follow-up.

| قبل | بعد |
|---|---|
| كل `POST` بيغيّر أوردر بيشيل `Idempotency-Key` و`expectedVersion` | كل `POST` بيغيّر أوردر بيحتوي على `Idempotency-Key` و`expectedVersion` |

السبب: Preserve required idempotency and concurrency fields.

مخاطر المعنى: Ambiguous presence/removal of security fields; repaired and independently rechecked.

## تسليم GitHub والحالة النهائية

PENDING — يتحدّث بعد نشر الفرع وفتح الـ PR وفحص التشغيلات. مفيش طلب مراجعة أو Merge تلقائي.
