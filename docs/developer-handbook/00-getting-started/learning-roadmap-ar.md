# خريطة التعلّم

> **Owner role:** Handbook navigation
> **Status:** REFERENCE — مسار الخمس أيام للمطوّر الجديد، مع روابط لكل Lab يومي.
> **Language / اللغة:** [English](learning-roadmap.md) · العربية

الهدف بعد خمس أيام: تبقى قادر تشرح المنتج، وتشغّل السيستم وتعمل Debug له، وتلاقي المكان اللي بيتنفّذ فيه أي Business Rule، وتكتب الاختبارات وتشغّلها، وتصلّح عيب وتقدّم Pull Request ماشي على DOC-001، من غير مساعد ذكاء اصطناعي. التقدّم بيتقيّم من خلال [التقييم النهائي](../06-onboarding-labs/final-assessment-ar.md).

## الخريطة

| اليوم | الموضوع | الـ Lab | هتخلّص بإيه |
|---|---|---|---|
| 1 | البيزنس والـ Domain | [day-1-business.md](../06-onboarding-labs/day-1-business-ar.md) | شرح مكتوب في صفحة واحدة لمسار الأوردر من العميل للطبّاخ وقواعد الفلوس |
| 2 | التطبيقات ورحلات المستخدم | [day-2-applications.md](../06-onboarding-labs/day-2-applications-ar.md) | الـ Stack شغّال على جهازك وجولة مسجّلة في الويب بتاع العميل والويب بتاع العمليات وأسطح الموبايل |
| 3 | الـ Backend وقاعدة البيانات والأمان | [day-3-backend-security.md](../06-onboarding-labs/day-3-backend-security-ar.md) | تتبّع Request كامل من الـ Controller للـ Service للـ Database للـ Authorization |
| 4 | مسار التطوير | [day-4-development.md](../06-onboarding-labs/day-4-development-ar.md) | اختبار ناجح انت كتبته، وتشغيل محلي مكافئ للـ CI، وعيب تدريبي اتعمله Reproduce واتصلّح |
| 5 | أول مساهمة | [day-5-first-contribution.md](../06-onboarding-labs/day-5-first-contribution-ar.md) | Pull Request حقيقي صغير فيه اختبارات وتحديث للدليل |

## ترتيب القراءة

1. [الـ README بتاع الدليل](../README-ar.md)، وبعدها [نظرة عامة على المشروع](project-overview-ar.md) و[التنقّل في الـ Repository](repository-navigation-ar.md) و[المصطلحات](glossary-ar.md).
2. البيزنس: [رؤية المنتج](../01-business/product-vision-ar.md)، [المستخدمين المستهدفين](../01-business/target-users-ar.md)، [نموذج الـ Marketplace](../01-business/marketplace-model-ar.md)، [الأطراف والصلاحيات](../01-business/actors-and-permissions-ar.md)، [دورة حياة الأوردر](../01-business/order-lifecycle-ar.md)، [التسعير والدفع](../01-business/pricing-and-payments-ar.md)، [إدارة المطبخ](../01-business/kitchen-management-ar.md)، [قواعد العمل](../01-business/business-rules-ar.md).
3. الرحلات: ابدأ بـ [رحلة الأوردر الكاملة](../02-user-journeys/complete-order-journey-ar.md)، وبعدها الصفحات الخاصة بكل سطح.
4. المعمارية: [نظرة عامة على السيستم](../03-architecture/system-overview-ar.md)، [الـ Backend](../03-architecture/backend-architecture-ar.md)، [قاعدة البيانات](../03-architecture/database-architecture-ar.md)، [الهوية والأمان](../03-architecture/identity-and-security-ar.md)، وبعدها صفحات الـ Frontend والموبايل.
5. الكود: [خريطة الـ Repository](../04-codebase/repository-map-ar.md)، [دليل موديولات الـ Backend](../04-codebase/backend-module-guide-ar.md)، [عقود الـ API](../04-codebase/api-contracts-ar.md)، [تنفيذ الأوردر](../04-codebase/order-implementation-ar.md).
6. مسار العمل: [المتطلبات المسبقة](../05-developer-workflow/prerequisites-ar.md)، [البيئة المحلية](../05-developer-workflow/local-environment-ar.md)، [التشغيل والـ Debug](../05-developer-workflow/run-and-debug-ar.md)، [تشغيل الاختبارات](../05-developer-workflow/test-execution-ar.md)، [مسار Git والـ PR](../05-developer-workflow/git-and-pr-workflow-ar.md)، [CI/CD](../05-developer-workflow/ci-cd-ar.md)، [بيئة المعاينة](../05-developer-workflow/preview-environment-ar.md)، [حل المشاكل](../05-developer-workflow/troubleshooting-ar.md)، [سياسة التوثيق](../05-developer-workflow/documentation-policy-ar.md).
7. مراجع وقت الحاجة: [حالة التنفيذ](../07-reference/implementation-status-ar.md)، [تتبّع المتطلبات](../07-reference/requirements-traceability-ar.md)، [الأسئلة الشائعة](../07-reference/frequently-asked-questions-ar.md).

## قواعد أساسية أثناء التعلّم

- الكود هو مصدر الحقيقة لما هو موجود فعلًا، والكلام المكتوب القديم ممكن يكون متأخر عنه. لما الدليل يقول IMPLEMENTED أو PLANNED أو DISABLED أو NOT VERIFIED، صدّق الوسم، وراجع الكود قبل ما تعتمد عليه.
- استخدم بيانات تجريبية بس. ماتكتبش أبدًا Credentials أو بيانات شخصية حقيقية في ملاحظاتك أو الـ Issues أو لقطات الشاشة.
- ثوابت المنتج اللي لازم تقدر تحفظها: Egypt / EGP / Africa/Cairo؛ الفلوس كأرقام صحيحة بالقرش (piastres)؛ السيرفر هو اللي بيملك السعر والسعة والأدوار وانتقالات الحالة؛ طبّاخ واحد وSlot واحد لكل أوردر؛ Snapshots غير قابلة للتعديل للأوردر؛ تسجيل الأوردر Idempotent؛ Authorization على مستوى الـ Object؛ صفر عمولة لمطبخي وصفر رسوم منصة على العميل في الـ MVP؛ الدفع كاش عند الاستلام أو عربون يدوي مباشر بيبلّغ عنه المطبخ (مش "متحقق منه بنكيًا" أبدًا)؛ العربي RTL والإنجليزي LTR.
- لو الدليل غلط أو ناقصه حاجة، ده عيب لازم يتصلّح تحت DOC-001، مش سبب إنك تخمّن.

## لو اتعطّلت

1. دوّر في الدليل، وبعدها في المستندات الأصلية اللي بيربط بيها.
2. دوّر في الكود (`grep -rn`، أو الـ IDE) وفي الاختبارات: الاختبار اللي اسمه على اسم السلوك هو أحسن مواصفات.
3. راجع [حل المشاكل](../05-developer-workflow/troubleshooting-ar.md) و[الأسئلة الشائعة](../07-reference/frequently-asked-questions-ar.md).
4. اسأل زميل ومعاك الأمر بالظبط والمخرجات واللي جرّبته قبل كده؛ وسجّل أي فجوات في التوثيق كـ Issues.
