# DOC-001 — سياسة المزامنة الإلزامية للتوثيق

> **Owner role:** Handbook navigation
> **Status:** REFERENCE — قاعدة حوكمة دائمة وموافق عليها من المالك؛ بوابة الـ CI المذكورة تحت بتفرض الجزء الهيكلي منها
> **Language / اللغة:** [English](documentation-policy.md) · العربية

**مفيش تقييم للتوثيق — مفيش دمج. توثيق مطلوب قديم أو متناقض — مفيش دمج.**

الـ Developer Handbook جزء حي وإلزامي من المنتج البرمجي اللي بيتسلّم. التغيير مش مكتمل لحد ما الكود والاختبارات والعقود والمعمارية وصفحات الدليل المتأثرة يتطابقوا. ده بينطبق على كل مطوّر وكل Agent برمجي وكل مراجع وكل مساهم في المستقبل.

## 1. إيه اللي بيتغطّى

كل PR: ميزات، تحسينات، إصلاح عيوب، إصلاحات عاجلة، Refactoring، معمارية، Backend، Frontend، Android/iOS، أمان، Keycloak، API، Migrations للـ Database، قواعد العمل، التسعير والمنطق المالي، UI/UX، بنية تحتية، CI/CD، إعدادات، ترقية Dependencies، تغييرات الاختبارات، وتغييرات التوثيق فقط. مفيش فئة مستثناة.

## 2. الإجراء

1. **قبل الكتابة** — لاقي المناطق المتأثرة في [doc001-matrix.json](../doc001-matrix.json) (المنطقة ← مسارات الكود ← التوثيق اللي لازم يتغيّر؛ والأدوار في [documentation-maintenance.md](documentation-maintenance-ar.md)). اقرا الصفحات دي والكود اللي بتوصفه.
2. **أثناء الكتابة** — حدّث الصفحات، والرسومات (`../03-architecture/diagrams.md`)، وعقود الـ API (`contracts/openapi.yaml`, `contracts/endpoints.json`)، وحالات الاختبار (`docs/quality/owner-acceptance/cases/`)، وصفوف التتبّع ([تتبّع المتطلبات](../07-reference/requirements-traceability-ar.md)) في نفس الـ PR. خلّي وسوم الحالة دقيقة في [حالة التنفيذ](../07-reference/implementation-status-ar.md). لو قاعدة اتغيّرت، ضيف قرار جديد بيشير للقرار اللي قبله ([قرارات المعمارية](../07-reference/architecture-decisions-ar.md))؛ وماتعيدش كتابة التاريخ بصمت أبدًا.
3. **قبل الـ PR** — شغّل `python3 scripts/doc001_check.py docs` و`python3 scripts/test_doc001_gate.py`؛ وقارن التوثيق بالكود والاختبارات بنفسك.
4. **في الـ PR** — املا قسم *Documentation Impact* في قالب الـ PR، بما فيه الإقرار المقروء آليًا.
5. **المراجعة** — المراجع بيقارن التوثيق بالكود والاختبارات والمتطلبات وبيرفض أي نص مضلّل أو متناقض. ممكن تُستخدم مراجعة Codex مستقلة، لكن عمرها ما بتتقدّم على إنها موافقة بشرية.

## 3. إقرار الـ PR

وصف الـ PR فيه بلوك واحد بالظبط:

```
<!-- DOC-001:BEGIN -->
impact: updated
summary: what changed and why
handbook_pages: docs/developer-handbook/<page>.md, ...
other_docs: docs/product/..., ...
diagrams: ...
contracts: ...
tests: ...
traceability: ...
consistency: what you compared (code, contract, tests) to verify agreement
justification: (only for impact none)
governance_review: (only when the gate, policy, agent files or PR template change)
<!-- DOC-001:END -->
```

قيمة `impact` تكون `updated` أو `none`؛ والقيم ماينفعش تحمل تعليقات داخل السطر. اكتب كل مفتاح في سطر واحد (الأسطر التكميلية المزاحة مسموحة)؛ وحط البلوك في وصف الـ PR الظاهر، مش جوه Code Fence ولا جوه تعليق HTML تاني (الاتنين بيترفضوا).

القواعد اللي بيفرضها `scripts/doc001_check.py pr`:

- المفاتيح المجهولة، والمفاتيح المكررة، وزوج علامات ناقص أو متكرر أو غير متوازن، أو `impact` غير `updated`/`none` كلها بتفشل.
- `updated`: محتاج `summary` و`consistency` وواحد على الأقل من `handbook_pages`/`other_docs`؛ وكل مسار مذكور لازم يكون موجود **وفيه تغيير حقيقي في الـ PR** (الملفات المحذوفة والتعديلات اللي مسافات بيضا بس مابتتحسبش؛ و`handbook_pages`/`other_docs`/`diagrams`/`traceability` لازم تكون ملفات توثيق، و`contracts` ملفات عقود، و`tests` ملفات مش توثيق)؛ و`handbook_pages` لازم تكون تحت `docs/developer-handbook/`؛ ولكل منطقة في الـ Matrix كودها اتغيّر، لازم واحد على الأقل من ملفات توثيق المنطقة دي يتغيّر.
- `none`: بس للـ PRs اللي بتغيّر ملفات مش توثيق ومبتغيّرش أي ملف توثيق؛ و`justification` لازم يكون 80 حرف على الأقل، مش عام، وبيستشهد بصفحة توثيق موجودة على الأقل تحت `docs/` أو دليل في المستوى الأعلى أو عقد الـ API (بمسار نسبي للـ Repository) فضلت دقيقة؛ والاستشهاد بـ `package.json` أو بكود المصدر مابيتحسبش. النص العام ("No docs needed", "Minor change", "Internal only", "Bug fix", "No user impact") بيفشل. و`none` صالح هيكليًا **بيتقبل للتقييم من المراجع بس** — البوابة بتطبع `REVIEWER MUST EVALUATE`.
- الملفات الخارجة عن كل مناطق الـ Matrix عمرها ما بتعدّي بصمت: لازم تغيير في التوثيق أو تبرير محدد.
- الـ PR اللي توثيق بس لازم يعلن `updated`.

## 4. التحقق من التوثيق (بيشتغل دايمًا)

الروابط النسبية والـ Anchors المكسورة؛ الروابط اللي بتخرج من الـ Repository؛ مراجع الـ Inline-code لمسارات في الـ Repository مش موجودة (`path` أو `path:line`)؛ الـ Metadata الناقصة (`Owner role` و`Status` بوسم مسموح) في صفحات الدليل؛ صفحات الدليل اللي مش ممكن توصلها من `README.md`؛ نقاط الدخول المطلوبة الناقصة (README وREADME-ar والسياسة دي والـ Matrix وقالب الـ PR)؛ معرّفات `TC-`/`MB-`/`BR-` ومعرّفات حالات E2E للمالك اللي مش موجودة في `planning/` أو `docs/quality/owner-acceptance/cases/`؛ Matrix غير صالحة.

**اللي البوابة ماتقدرش تعمله:** تثبت إن جملة صحيحة. نجاح البوابة معناه "الهيكل والمراجع سليمين". لما توثّق حاجة مش موجودة (ملف ناقص، مسار مخطط)، ماتكتبهوش كـ Inline code (البوابة بتعامل مسارات الـ Repository المكتوبة كـ Code على إنها ادعاء إن الملف موجود). الدقة الدلالية محتاجة مقارنة المراجع (القسم 2، الخطوة 5). عمرك ما تقرا الفحص الأخضر على إنه شهادة دقة.

## 5. حوكمة البوابة نفسها

التغييرات في `.github/workflows/doc001.yml` أو `scripts/doc001_check.py` أو `scripts/test_doc001_gate.py` أو الـ Matrix أو السياسة دي أو `AGENTS.md` أو `CLAUDE.md` أو `.github/pull_request_template.md` أو `.github/CODEOWNERS` بتحتاج `governance_review` في الإقرار **و** مراجعة GitHub بحالة APPROVED على **الـ Head Commit الحالي** من حد غير صاحب الـ PR (التعليق عمره ما بيلغي موافقة؛ وموافقة على Commit أقدم، أو موافقة اتلغت، مابتتحسبش). GitHub مابيعيدش تشغيل الفحص لما مراجعة تتقدّم أو تتلغي، فالنتيجة الخضرا ممكن تعيش بعد إلغاء لاحق للموافقة (قيد معروف؛ الفحص دفاع متعدد الطبقات). الرقابة الحاسمة لملفات الحوكمة هي قاعدة الـ Ruleset "Require review from Code Owners" (`.github/CODEOWNERS`)، اللي GitHub بيقيّمها وقت الدمج. بعد الموافقة، أعد تشغيل Workflow `Documentation Consistency` (Actions ← re-run) وفعّل "Dismiss stale pull request approvals when new commits are pushed" في الـ Ruleset.

الـ Workflow بيستخدم الحدث `pull_request`. نسخة `pull_request_target` اتجرّبت الأول ومااشتغلتش على الـ Pull Requests على `develop`، لأن GitHub بيقرا Workflow الحدث ده من الـ Branch الافتراضي (`main`)، اللي المشروع ده مابيعدّلوش (اتأكدنا 2026-10-20 على PR #25). ومع `pull_request` ملف الـ Workflow بييجي من الـ PR، فسكريبت البوابة اللي بيتنفّذ هو دايمًا اللي متعمل له Checkout على الـ **Base** SHA، ومفيش حاجة من الـ PR بتتنفّذ، وملفات الـ PR بتتقرا كبيانات بس. لو الـ Branch الأساسي مفيهوش بوابة، الـ Job بيفشل بشكل آمن (fails closed). وبالتالي أي PR بيعدّل `.github/workflows/doc001.yml` ممكن يحاول يعطّل الفحص؛ والدفاعات هي (1) إن البوابة بتعتبر الملف ده ملف حوكمة (إقرار مع موافقة من حد غير صاحب الـ PR مطلوبين) و(2) قاعدة Code Owners في الـ Ruleset. حذف الـ Workflow مابيعدّيش بصمت: الفحص المطلوب عمره ما هيبلّغ.

الاستثناءات الطارئة: مالك الـ Repository بس هو اللي يقدر يفوّض بواحد، كتابةً على الـ PR؛ والاستثناء مع Issue معالجة لاحقة بيتسجّلوا في الـ PR. المطوّر ماينفعش يوافق على استثناء لتغيير بتاعه هو.

**قيد (هوية GitHub واحدة):** لو نفس حساب GitHub هو اللي بيكتب وهو اللي هيوافق، قاعدة "حد غير صاحب الـ PR" مستحيل تتحقق ولازم المالك يستخدم حساب تاني أو قرار Ruleset يديره المالك. البوابة مابتخبّيش ده؛ هي بتفشل.

## 6. حالة حماية الـ Branch

سياق الفحص هو **`Documentation Consistency / DOC-001`** (اسم الـ Job في `.github/workflows/doc001.yml`؛ ومتغطّى باختبار Regression في `scripts/test_doc001_gate.py`). مابيبقاش مفروض على الدمج إلا لما الـ Ruleset/حماية الـ Branch على `develop` تذكر السياق ده كمطلوب. شوف [documentation-maintenance.md](documentation-maintenance-ar.md#حالة-حماية-الـ-branch) للحالة الحالية المتحقق منها وخطوات الإعداد بالظبط.

## 7. مرتبط

[مسار Git والـ PR](git-and-pr-workflow-ar.md) · [CI/CD](ci-cd-ar.md) · [خريطة التوثيق](../07-reference/documentation-map-ar.md) · `AGENTS.md` · `CLAUDE.md` · `.github/pull_request_template.md`
