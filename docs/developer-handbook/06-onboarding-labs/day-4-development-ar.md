# اليوم 4 - مسار التطوير

> **Owner role:** Testing and QA
> **Status:** REFERENCE — Lab للبناء والاختبار والـ Debug لعيب وتجهيز PR محليًا.
> **Language / اللغة:** [English](day-4-development.md) · العربية

## الأهداف

- شغّل نفس الفحوصات اللي بيشغّلها الـ CI، محليًا.
- اكتب اختبار وحدة واختبار تكامل وشوفهم بيفشلوا وبعدين بينجحوا.
- اعمل Reproduce لعيب تدريبي وصلّحه وضيف له اختبار Regression على Branch مؤقت.
- افهم الـ CI والـ Deployment لبيئة المعاينة وبوابة DOC-001.

## قايمة القراءة

[تشغيل الاختبارات](../05-developer-workflow/test-execution-ar.md)، [التشغيل والـ Debug](../05-developer-workflow/run-and-debug-ar.md)، [مسار Git والـ PR](../05-developer-workflow/git-and-pr-workflow-ar.md)، [CI/CD](../05-developer-workflow/ci-cd-ar.md)، [بيئة المعاينة](../05-developer-workflow/preview-environment-ar.md)، [سياسة التوثيق](../05-developer-workflow/documentation-policy-ar.md)، [صيانة التوثيق](../05-developer-workflow/documentation-maintenance-ar.md)، [المكتبات المشتركة](../04-codebase/shared-libraries-ar.md).

## مهام عملية

1. الـ Branch: `git fetch origin && git switch -c fix/day4-practice origin/develop`. الـ Branch ده للتدريب وعمره ما بيتعمل له Push عشان يتدمج.
2. شغّل الفحوصات المكافئة للـ CI وسجّل نتيجة كل واحد (PASS بس لو شفته بينجح فعلًا):
   ```bash
   pnpm install --frozen-lockfile
   pnpm typecheck && pnpm lint && pnpm test && pnpm build
   pnpm contract:check
   (cd backend && ./mvnw -B -ntp verify)
   python3 scripts/validate_blueprint.py
   python3 -m unittest discover -s scripts -p 'test_*.py'
   python3 scripts/doc001_check.py docs
   ```
   استخدم `scripts/check_workspace.sh` لتشغيل على Clone نضيف من الـ HEAD اللي عملتله Commit.
3. اختبار وحدة. في `packages/api-client/test/money.test.ts` شوف إزاي تنسيق الفلوس بيتختبر. ضيف حالة جديدة واحدة لقيمة تختارها (مثلًا صفر قرش ومبلغ كبير)، وشغّل `pnpm --filter @matbakhy/api-client test`، وتأكد إنها بتنجح. وبعدين اكسر التنفيذ عن قصد، وشوف الاختبار بيفشل، ورجّع التنفيذ لحالته السليمة.
4. اختبار Backend. اختار Class واحد من `backend/app/src/test/java/com/matbakhy/app/orders`. شغّله لوحده بـ `scripts/check_backend_suite.sh <ClassName>`. اقرا المخرجات "PASS: <class> (n tests)". غيّر وسيط اسم الـ Class لحاجة غلط واقرا رسالة الفشل.
5. العيب التدريبي. أدخل Bug صغير ومقصود في مكان ليه اختبارات (مثلًا غيّر مقارنة في مكوّن ويب تحت `apps/customer-web/src` يغطيه `*.test.tsx`). شغّل الاختبارات، ودوّر على أنهي اختبار مسكه، واشرح ليه، وصلّحه. لو مفيش اختبار مسكه، اكتب الاختبار الناقص الأول وورّي إنه بيفشل.
6. E2E في المتصفح: اقرا `apps/operations-web/e2e/money-reflow.e2e.ts` ولو Docker وChromium متاحين، شغّل `scripts/dev.sh e2e` (طويل؛ بيستخدم Stack مؤقت). غير كده سجّله NOT RUN.
7. قراءة الـ CI: افتح `.github/workflows/ci.yml` و`.github/workflows/deploy-preview.yml`. جاوب كتابةً: أنهي Job بيثبت إن الـ Stack المحلي بيقوم؟ إيه اللي لازم يتحقق قبل ما الـ Docker Images تتبني؟ إيه اللي الـ Pipeline مابيعملوش له Rollback؟
8. بيئة المعاينة: اطلب من المالك يضيفك في القايمة المسموحة لو احتجت، وبعدين شغّل `sh deploy/web-preview/smoke.sh <PREVIEW_DOMAIN>` على بيئة المعاينة (للقراءة بس). سجّل أنهي فحوصات بيغطيها.
9. تجربة DOC-001: عدّل جملة واحدة في الدليل على الـ Branch التدريبي بتاعك، وشغّل `python3 scripts/doc001_check.py docs`، وبعدين اكتب (من غير ما تقدّم) كتلة إقرار الـ PR من صفحة السياسة.
10. تخلّص من الشغل التدريبي: `git switch develop && git branch -D fix/day4-practice` (الـ Branch بتاعك انت بس).

## المُخرَج

جدول نتايج: الأمر، والنتيجة (PASS / FAIL / NOT RUN)، وملاحظات. بالإضافة للاختبار اللي كتبته وهو بيفشل وبعدين ينجح، وشرح من فقرتين للعيب التدريبي (السبب، والاكتشاف، والإصلاح، واختبار الـ Regression).

## فحص ذاتي

- [ ] عمري ما بلّغت بـ PASS لحاجة ماشغّلتهاش.
- [ ] أفهم ليه اختبارات التكامل بتستخدم PostgreSQL حقيقي وليه الـ Mocks مش بديل مقبول.
- [ ] أقدر أشرح ليه التشغيل المحلي عندي ممكن يختلف عن الـ CI (Clone نضيف، وإصدارات مثبّتة).
- [ ] أقدر أقرا Log الـ CI وألاقي أول خطوة فشلت.
- [ ] عارف إيه اللي DOC-001 بيطلبه في الـ PR وفين بيتحط الإقرار.

الخطوة الجاية: [اليوم 5](day-5-first-contribution-ar.md).
