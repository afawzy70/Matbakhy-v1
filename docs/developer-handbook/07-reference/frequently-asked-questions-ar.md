# الأسئلة الشائعة

> **Owner role:** Handbook navigation
> **Status:** REFERENCE — إجابات قصيرة للمطوّرين، وكل إجابة بتشاور على الصفحة أو الملف اللي فيه الحقيقة.
> **Language / اللغة:** [English](frequently-asked-questions.md) · العربية

## الإعداد والأدوات

**أنهي إصدار Node؟** بالظبط 24.21.0 (`.node-version`؛ و`package.json` بيسمح بـ `>=24.21.0 <25`، و`.npmrc` بيفرض الـ engines). Node 22 مش مدعوم. شوف [المتطلبات المسبقة](../05-developer-workflow/prerequisites-ar.md).

**ليه `./mvnw` مش `mvn`؟** الـ Wrapper بيثبّت Maven 3.9.11 (`backend/.mvn/wrapper/maven-wrapper.properties`).

**محتاج Docker عشان أشغّل اختبارات الـ Backend؟** أيوه. اختبارات التكامل بتشغّل PostgreSQL 16 و Emulator لـ S3 من خلال Testcontainers؛ مفيش بديل H2.

**بيشتغل على Windows؟** مش متحقق منه. استخدم WSL2؛ شوف [المتطلبات المسبقة](../05-developer-workflow/prerequisites-ar.md).

**أشغّل كل حاجة محليًا إزاي؟** `scripts/dev.sh up` وبعدها `scripts/dev.sh smoke`، ومعاهم Vite dev servers. شوف [البيئة المحلية](../05-developer-workflow/local-environment-ar.md).

**فين كلمات السر المحلية؟** بيتولّدوا في ملف env بتاعك انت والمتجاهل من git بواسطة `scripts/dev.sh`. ماتنسخهمش أبدًا في Issues أو شات أو Commits.

## المنتج والكود

**مين اللي بيحسب الأسعار؟** السيرفر بس. العملاء (Clients) بيعرضوا عروض الأسعار اللي جاية من السيرفر؛ وأي سعر مبعوت من الـ Client بيتم رفضه (BR-019). شوف [التسعير والدفع](../01-business/pricing-and-payments-ar.md) و[تنفيذ التسعير](../04-codebase/pricing-implementation-ar.md).

**ليه المبالغ أرقام صحيحة؟** الفلوس بتتخزّن وبتتبعت بالقرش (piastres) عشان نتجنب أخطاء التقريب.

**ينفع الأوردر يحتوي على طبّاخين اتنين؟** لا: طبّاخ واحد وSlot واحد لكل أوردر (BR-018). استبدال الطبّاخ أو الـ Slot في السلة محتاج تأكيد صريح.

**فيه عمولة أو رسوم للمنصة؟** لا، في الـ MVP: صفر عمولة لمطبخي ومفيش رسوم منصة على العميل. مزايا النمو المؤجلة هي بحث مش تنفيذ ([حالة التنفيذ](implementation-status-ar.md)).

**الدفع بيشتغل إزاي؟** كاش عند الاستلام لما يكون مؤهَّل، أو عربون يدوي مباشر من العميل للمطبخ والمطبخ هو اللي بيبلّغ إنه استلمه. المنصة عمرها ما بتدّعي تحقق بنكي. الفلوس الحقيقية لسه متقفّل عليها بـ Gates. شوف [تنفيذ الدفع](../04-codebase/payment-implementation-ar.md).

**فين الـ State Machine بتاع الأوردر؟** المرجع الأساسي: [04-state-machines.md](../../product/04-state-machines.md)؛ الملخص: [دورة حياة الأوردر](../01-business/order-lifecycle-ar.md)؛ الكود: [تنفيذ الأوردر](../04-codebase/order-implementation-ar.md).

**ألاقي منين الـ Story أو الاختبار اللي بيغطي قاعدة معيّنة؟** `planning/rules.json` (من القاعدة إلى الـ Story والسيناريوهات)، و`planning/scenarios.json`، و[تتبّع المتطلبات](requirements-traceability-ar.md).

**حالة الـ Backlog دليل إن الحاجة خلصت؟** لا. الـ Story بتبقى DONE بس لو معاها دليل على Commit اتعمله اختبار؛ راجع الكود دايمًا. وسوم الحالة في الدليل موجودة في [حالة التنفيذ](implementation-status-ar.md).

## الاختبارات والأدلة

**الـ CI الأخضر بيثبت إيه؟** بيثبت إن الـ Jobs المذكورة في [CI/CD](../05-developer-workflow/ci-cd-ar.md) عدّت على الـ Commit ده. مش بيثبت كل السيناريوهات، ولا سلوك الأجهزة الـ Native، ولا بيئة المعاينة المنشورة.

**فيه كام حالة قبول للمالك، وهل نجحت؟** مصادر الـ JSON فيها 200 حالة؛ وكل واحدة NOT_RUN أو BLOCKED لحد ما سجل تشغيل معاه دليل يقول غير كده ([تشغيل الاختبارات](../05-developer-workflow/test-execution-ar.md)). آخر ملاحظات التشغيل في `docs/quality/owner-acceptance/run-2026-10-19.md`.

**ينفع أكتب PASS في تقرير لو ماشغّلتش الاختبار؟** لا. استخدم NOT_RUN أو NOT VERIFIED.

**أبلّغ عن Bug فين؟** GitHub Issue باستخدام `.github/ISSUE_TEMPLATE/defect.md`، ببيانات اصطناعية (Synthetic) وLogs متنضّفة.

## Git والـ PR والـ CI

**أبني على أنهي Branch؟** `develop`. الـ `main` مابيتعدّلش. شوف [مسار Git والـ PR](../05-developer-workflow/git-and-pr-workflow-ar.md).

**أقدر أعمل Merge للـ PR بتاعي بنفسي؟** لأ، إلا لو المالك أو سياسة الـ Repository سمحت صراحةً؛ والـ Agents عمرهم ما بيعملوا كده.

**بيحصل إيه لما `develop` يتغيّر؟** الـ Product CI، وبعدها الـ Images بـ Commit SHA، وبعدها Deployment على بيئة المعاينة في Contabo وSmoke ([CI/CD](../05-developer-workflow/ci-cd-ar.md)). الـ Database مابترجعش لورا أوتوماتيك.

**إيه هو إقرار DOC-001؟** Block في وصف الـ PR بيوضّح أثر التغيير على التوثيق؛ القواعد في [سياسة التوثيق](../05-developer-workflow/documentation-policy-ar.md)، وبيتفحص بـ `python3 scripts/doc001_check.py`.

## المعاينة والتشغيل

**ليه بيجيلي 403 على بيئة المعاينة؟** الـ IP بتاعك مش في الـ allow-list الخاصة بفئة الـ Host دي ([بيئة المعاينة](../05-developer-workflow/preview-environment-ar.md)).

**بيئة المعاينة هي الـ Production؟** لا: بيانات اصطناعية، وبانر Demo، ومزوّدين وهميين، وسيرفر واحد.

**فين الـ Runbooks التشغيلية؟** `docs/operations/` (ابدأ بـ [08-troubleshooting-runbook.md](../../operations/08-troubleshooting-runbook.md) و[11-staging-deployment-and-rollback.md](../../operations/11-staging-deployment-and-rollback.md)).

## التوثيق

**الدليل مخالف للكود. أعمل إيه؟** صدّق الكود، وصلّح الصفحة في الـ PR بتاعك، واذكر ده في الإقرار. لو مقدرتش تتحقق، حط الوسم NOT VERIFIED.

**أكتب فين المسارات أو الأوامر المستقبلية اللي لسه مش موجودة؟** في Code Block أو باستخدام `<placeholders>`؛ مسارات الـ Repository اللي بين Backticks لازم تكون موجودة (بيتفحص بـ `scripts/doc001_check.py`).

**فيه دليل بالعربي؟** [README-ar.md](../README-ar.md) للمطوّرين؛ و[owner-guide-ar.md](../../quality/owner-acceptance/owner-guide-ar.md) لاختبار المالك لبيئة المعاينة.
