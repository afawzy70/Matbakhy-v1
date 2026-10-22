# نظام صيانة التوثيق

> **Owner role:** Handbook navigation
> **Status:** REFERENCE — الملكية، ومصفوفة التغيير، وتاريخ القرارات، ودورة التحسين اللي بتخلّي الدليل محدّث
> **Language / اللغة:** [English](documentation-maintenance.md) · العربية

## الملكية (على أساس الدور)

| التوثيق | دور المالك | مناطق الكود المعتادة |
|---|---|---|
| قواعد العمل، ودورة حياة الأوردر، والتسعير والدفع، وإدارة المطبخ، وصفحات المنتج في `docs/product/` | Business documentation | `backend/modules/orders`, `catalog`, `finance`, `cooks`, `planning/` |
| معمارية الـ Backend، ودليل الموديولات، والـ Database، وعقود الـ API | Backend architecture | `backend/`, `contracts/`, `backend/app/src/main/resources/db/` |
| معمارية الويب والموبايل، والرحلات، وأدلة الكود | Frontend and mobile | `apps/`, `packages/` |
| الهوية، والـ MFA، وKeycloak، وكونسول الأدمن | Security and Keycloak | `backend/modules/identity`, `infra/identity`, `deploy/web-preview/identity` |
| البنية التحتية، والـ Deployment، والـ CI/CD، وبيئة المعاينة | Infrastructure and deployment | `infra/`, `deploy/`, `.github/workflows/` |
| تشغيل الاختبارات، وكتالوج قبول المالك، والأدلة | Testing and QA | `docs/quality/`, `verification/`, `**/src/test`, `e2e*` |
| التنقّل في الدليل، والسياسة، والـ Matrix، والـ Onboarding | Handbook navigation | `docs/developer-handbook/` |

المسؤولية في البداية مرتبطة بالأدوار الوظيفية، مش بأشخاص بالاسم. المراجع بتاع أي PR مسؤول عن الدور (أو الأدوار) المكتوبة لكل منطقة في [doc001-matrix.json](../doc001-matrix.json).

## مصفوفة تغيير التوثيق

المصفوفة المقروءة آليًا هي [doc001-matrix.json](../doc001-matrix.json): كل منطقة فيها قائمة Globs للكود، والتوثيق اللي لازم يتغيّر معاها، ودور المالك. أمثلة (القائمة الكاملة في الملف):

| التغيير في | حدّث واحد على الأقل من |
|---|---|
| `backend/modules/orders/**` | `04-codebase/order-implementation.md`, `01-business/order-lifecycle.md`, `02-user-journeys/complete-order-journey.md`, `docs/product/04-state-machines.md` |
| `apps/customer-mobile/**` | `02-user-journeys/customer-mobile.md`, `03-architecture/mobile-architecture.md`, `04-codebase/mobile-code-guide.md` |
| `backend/app/src/main/resources/db/**` | `03-architecture/database-architecture.md`, `docs/engineering/02-data-model.md` |
| `infra/identity/**`, `deploy/web-preview/identity/**` | `03-architecture/identity-and-security.md`, `02-user-journeys/authentication-and-mfa.md`, `docs/security/mfa-policy.md` |
| `.github/workflows/**` | `05-developer-workflow/ci-cd.md`, `05-developer-workflow/preview-environment.md` |

"واحد على الأقل" هو الحد الأدنى اللي الفحص الآلي بيفرضه. لكن صاحب التغيير لازم يحدّث **كل** صفحة بيخلّيها التغيير غير دقيقة؛ والمراجع بيتأكد من ده.

## تاريخ القرارات

القرارات موجودة في `planning/decisions.json` و[docs/product/01-context-and-decisions.md](../../product/01-context-and-decisions.md)؛ والفهرس هو [قرارات المعمارية](../07-reference/architecture-decisions-ar.md). لما قاعدة تتغيّر: (1) ضيف قرار جديد بيسمّي القرار اللي قبله، (2) علّم النص القديم إنه اتحل محله قرار تاني مع رابط للقرار الجديد، (3) حدّث التنفيذ والاختبارات والدليل، (4) احتفظ بالسياق التاريخي. ماتعيدش كتابة تاريخ القرارات بصمت أبدًا.

## التحسين المستمر

لما أي حد يلاقي توثيق ناقص أو مش واضح أو غلط: سجّله كـ GitHub Issue (العنوان يبدأ بـ `docs:`)، وصلّح الصفحة الأساسية في الـ PR اللي اكتشفه، وضيف تحقق في `scripts/doc001_check.py` لو نوع العيب ممكن يتفحص (واختبار في `scripts/test_doc001_gate.py`). ملاحظات Mohamed في أول أسبوع بتغذّي الدورة دي ([التقييم النهائي](../06-onboarding-labs/final-assessment-ar.md)).

## مزامنة النسخة العربي

الدليل الإنجليزي هو مصدر الحقيقة. كل صفحة إنجليزي `X.md` ليها نسخة واحدة بالظبط بالعربي المصري `X-ar.md` في نفس المجلد (`README.md` و`README-ar.md` هما نقطتا الدخول). النسخة العربي ترجمة كاملة مش ملخص: كل الفقرات والتحذيرات والجداول والأمثلة والأوامر والـ Checklists والروابط موجودة، والكود والأوامر والـ Identifiers والمسارات والإصدارات والـ URLs بتفضل زي ما هي بالإنجليزي. ملف الدليل الإنجليزي الوحيد من غير نسخة هو [تقرير تطابق الترجمة](../translation-parity-report.md) المتولّد؛ و[ملف الاستثناءات](../translation-exceptions.json) القابل للقراءة آليًا بيذكره وبيذكر كل اختلاف تاني متبرَّر.

**سياسة المراجعة.** الإنجليزي والعربي بيتغيروا مع بعض في نفس الـ Pull Request. تغيير صفحة إنجليزي من غير التغيير المقابل بالعربي يبقى تغيير ناقص، وكمان أي تعديل عربي بس بيغيّر المعنى. ماتلزقش ترجمة آلية: ترجم بإيدك، وخلّي البنية مطابقة، وخلّي حد يقارن العربي بالإنجليزي جنب بعض قبل ما الزوج يتسجّل. دقة المعنى مسؤولية إنسان أو مراجع مستقل؛ والأدوات اللي تحت بتفحص البنية بس ومابتثبتش أبدًا إن الجملة معناها واحد في اللغتين.

| الحالة | تعمل إيه |
|---|---|
| صفحة إنجليزي جديدة | اعمل `X-ar.md` جنبها، وضيف سطر تبديل اللغة في الملفين، واربط الاتنين من صفحات التنقّل (`README.md` و`README-ar.md`). |
| صفحة إنجليزي اتعدّلت | اعمل التعديل المكافئ في `X-ar.md` في نفس الـ PR. |
| صفحة إنجليزي اتغيّر اسمها أو اتمسحت | غيّر اسم `X-ar.md` أو امسحه في نفس الـ PR وصلّح الروابط باللغتين. |
| تحسين صياغة عربي | عدّل `X-ar.md` بس لو المعنى الإنجليزي ماتغيّرش؛ وشغّل الـ Validator. |
| اختلاف متبرَّر خاص باللغة | ضيف سجل فيه السبب وملحوظة المراجع في `docs/developer-handbook/translation-exceptions.json`. السجلات اللي مش مستخدمة بتفشّل الـ Check. |

**الأوامر** (تتشغّل من جذر الـ Repository؛ الـ Validator محتاج `markdown-it-py` وبيتسطّب بـ `python3 -m pip install 'markdown-it-py==3.0.0'`):

```bash
python3 scripts/validate_handbook_parity.py                         # every page
python3 scripts/validate_handbook_parity.py --file 01-business/order-lifecycle.md   # one English page, path relative to the handbook
python3 scripts/validate_handbook_parity.py --fix-anchors           # rewrite English-style link fragments to the Arabic heading slugs
python3 scripts/validate_handbook_parity.py --base origin/develop   # also require same-PR Arabic changes
python3 scripts/validate_handbook_parity.py --update-sync           # record the pair as reviewed together
python3 scripts/test_validate_handbook_parity.py                    # validator unit tests
```

الـ Validator بيقارن في كل زوج: مستويات العناوين، وبلوكات الكود (اللغة والمحتوى بالظبط؛ وبنية بلوكات Mermaid مع إخفاء الـ Labels)، والجداول (الصفوف والأعمدة)، والقوايم وعناصر الـ Checklist، والـ Blockquotes، والفقرات، والصور، ووجهات الروابط (الروابط الداخلية لازم تروح للصفحة العربي وللعنوان العربي المكافئ)، والـ Identifiers اللي في الـ Inline Code، والـ Tokens التقنية والأرقام، وبيانات `Owner role` و`Status`، وسطر تبديل اللغة. وكمان بيبلّغ عن صفحات إنجليزي من غير نسخة وصفحات عربي من غير مصدر. الملف `docs/developer-handbook/translation-sync.json` بيسجّل Hash لكل زوج لما اتراجع آخر مرة مع بعض؛ والـ Check بيفشل لو الإنجليزي اتغيّر والعربي لأ. شغّل `--update-sync` بعد ما تقارن الملفين فعلًا بس.

الـ CI Workflow ‏`.github/workflows/handbook-parity.yml` (الـ Job اسمه `Handbook Arabic parity (structural)`) بيشغّل الاختبارات والفحص الكامل وقاعدة نفس الـ PR والأمر `python3 scripts/doc001_check.py docs` لما ملفات الدليل تتغيّر. هو مش ضمن الـ Checks المطلوبة على `develop` (الـ Ruleset ماتغيّرش)؛ لكن أي تشغيل فاشل لسه إشارة لازم تتصلّح قبل الدمج. المراجعة الحالية، بما فيها المراجعة الدلالية والاستثناءات المتبقية، موجودة في [تقرير تطابق الترجمة](../translation-parity-report.md).

## حالة حماية الـ Branch

**مفروضة من 2026-10-20 بواسطة Ruleset على الـ Repository** (id 25447480، "develop protection (PR + required checks + DOC-001)"، الإنفاذ نشط، الهدف `develop` بس؛ و`main` ماتلمسش).

| القاعدة | الإعداد |
|---|---|
| الحذف، الـ Force push | ممنوعين |
| الـ Pull Request مطلوب | أيوه؛ عدد الموافقات المطلوبة: 0 (شوف القيود)؛ المراجعات القديمة بتتلغي عند الـ Push؛ خيار مراجعة Code Owners شغّال |
| فحوصات الحالة المطلوبة | `Documentation Consistency / DOC-001`؛ `Workspace from clean checkout (P01-CHECK-1/2)`؛ `Local stack bootstrap and smoke (P01-CHECK-3)`؛ `Design tokens, specimens and prototype journeys (P00-CHECK-2)`؛ `Secret scan`؛ `blueprint-integrity`؛ `Android debug APK (Gradle)`؛ `iOS simulator build (Xcode, unsigned)`؛ الـ Branch مش لازم يكون محدّث (عشان نتجنب تعطّل الـ Rebase) |
| جهات التجاوز (Bypass actors) | مفيش |

مش مطلوبة عن قصد: `Migrate, seed, re-seed (idempotent), reconcile` (مفلترة بالمسار، وهتفضل مابتبلّغش على أغلب الـ PRs) وجوبات الـ Deploy (بعد الدمج). الـ Deployment مش متأثر: `deploy-preview.yml` وWorkflow تجهيز E2E للمالك بيشتغلوا على Pushes ناتجة عن دمج PR، ومفيش Workflow بيعمل Push على `develop` بنفسه.

**اتأكدنا على GitHub (2026-10-20)، باستخدام PRs مؤقتة على `develop`:** الـ Workflow بيشتغل على Pull Requests على `develop` (المحاولة الأولى بـ `pull_request_target` عمرها ما اشتغلت لأن GitHub بيقرا Workflow الحدث ده من `main`؛ والـ Trigger اتغيّر في PR #26). نتائج الفحص:

| PR مؤقت | الحالة | نتيجة DOC-001 |
|---|---|---|
| #25 | تغيير في الكود، من غير إقرار | فشل ("exactly one DOC-001 declaration is required") |
| #27 | `impact: none` مع تبرير عام | فشل |
| #28 | تغيير في الكود مع تحديث حقيقي للدليل وإقرار صالح | نجاح |
| #29 | `impact: none` مع تبرير محدد بيذكر صفحات موجودة | نجاح، متعلّم للتقييم من المراجع |
| #30 | تغيير توثيق بس فيه رابط مكسور | فشل (رابط نسبي مكسور) |
| #33 | الـ PR بيستبدل Job الـ DOC-001 بخطوة فاضية | **نجاح (ثانيتين): الفحص اتعطّل** — شوف القيد الأول |
| #26 (PR حقيقي) | تغيير حوكمة من غير موافقة هوية تانية؛ والتشغيل الأول فشل كمان بسبب نقص توثيق المنطقة | فشل، زي ما هو مقصود |

ومع تفعيل الـ Ruleset، الأمر `gh pr merge` اتقابل بالرفض للـ PRs المؤقتة الفاشلة، وGitHub بلّغ إن حالة الدمج بتاعتها محظورة.

**القيود (الحالة بصراحة):**

- **أي PR يقدر يعطّل DOC-001 بتعديل الـ Workflow — اتأجّل بقرار من المالك (2026-10-20)، الخطورة High، ولسه مفتوح.** مع الحدث `pull_request` GitHub بيشغّل ملف الـ Workflow من الـ PR نفسه، فالـ PR المؤقت #33 اللي استبدل الـ Job بخطوة فاضية عدّى الفحص المطلوب في ثانيتين. يعني DOC-001 مفروض ضد التغييرات الصادقة أو الإهمال وأخطاء الـ Agents، **مش** ضد تغيير بيعيد كتابة `.github/workflows/doc001.yml` كمان. Mohamed هينضم للتطوير النشط بعد الإصدار الأول بس، فالمعالجة اتأجّلت؛ ومفيش حاجة اتركّبت. الرقابة المؤقتة: المالك بيقرا كل Diff بيلمس `.github/workflows/`. خيارات المعالجة بعدين (ولا واحد منها اتختار): (1) GitHub App خارجي بينشر الفحص المطلوب من بوابة الـ Branch الأساسي، والـ Ruleset مثبّت على `integration_id` بتاع الـ App (التصميم وكود المتحقق و10 اختبارات وجولتين مراجعة Codex محفوظين على الـ Branch غير المدموج `governance/doc001-single-account-model`، الـ PR #36 المقفول؛ محتاج App وRepository صغير منفصل، والاتنين إجراءات على المالك)؛ (2) Workflow بـ `pull_request_target` على `main`، وده مستبعد طول ما `main` مالمسوش؛ (3) حساب GitHub بشري تاني مع مراجعة Code Owners (مش ممكن مع الحساب الواحد المشترك)؛ (4) نقبل القيد ونعتمد على مراجعة الـ Diff. مرفوض أو غير متاح: "required workflows" في الـ Ruleset (مش متاحة للـ Repositories على حسابات شخصية)، وPush rulesets على مسارات الحوكمة (بتمنع مقترحات مشروعة)، والـ Environments بمراجعين مطلوبين (الـ PR يقدر يشيلها).
- **المراجعة المستقلة مش مفروضة.** حساب GitHub واحد بيكتب وممكن يوافق على كل حاجة، فـ "موافقة من حد غير صاحب الـ PR" عمرها ما تتحقق بالهوية الحالية؛ والبوابة كمان بتقبل موافقة من أي Login غير صاحب الـ PR من غير ما تتأكد إن المراجع Collaborator (إصلاح ونموذج تفويض المالك بحساب واحد اتكتبوا مسودة في PR #34/#36، والاتنين اتقفلوا من غير دمج؛ واتأجّلوا مع البند اللي فوق). تغييرات الحوكمة (الـ Workflow وسكريبت البوابة والـ Matrix والسياسة وملفات الـ Agents وقالب الـ PR وCODEOWNERS) بتفشل في DOC-001 وماينفعش تتدمج طول ما الـ Ruleset مفيهوش Bypass actor، إلا لو عطّلت الفحص كمان زي ما فوق. المالك لازم يعدّل الـ Ruleset (Settings → Rules → Rulesets) للتغيير المحدد ويرجّعه بعدها، أو يضيف هوية مراجع تانية. ماتضافش أي Bypass actor لأن إضافته بتضعف الإنفاذ ومتوافقش عليها.
- **مراجعة Code Owners متظبطة بس مش متثبت إنها بتفرض**: مع صفر موافقات مطلوبة GitHub مابلّغش عن أي قرار مراجعة على PR مؤقت بيلمس ملف Code Owners (#31). رفع العدد لـ 1 هيعطّل كل الـ PRs طول ما الحساب هو الكاتب الوحيد. اعتبر قاعدة الحوكمة في DOC-001، مش Code Owners، هي الرقابة الفعلية.
- **قيود الدمج اتأكدنا منها بـ Token على مستوى المالك بس**؛ ومفيش هوية اختبار غير مميزة أو من غير تجاوز، فالإنفاذ ضد المساهمين التانيين PARTIALLY VERIFIED. الـ Pushes المباشرة على `develop` ماجرّبناهاش (كانت هتغيّر `develop` لو القاعدة فشلت)؛ ووجود القاعدة متأكد منه من خلال الـ Rules API.
- إلغاء الموافقة بعد نجاح فحص DOC-001 مابيعيدش تشغيل الفحص؛ والفحص على Head قديم بيتبدّل لما Commits جديدة توصل.
- الفحص بيثبت الهيكل وسلامة المراجع بس؛ مراجعة المعنى لسه مسؤولية إنسان أو مراجع مستقل ([سياسة التوثيق](documentation-policy-ar.md)).

عشان تغيّر الـ Ruleset أو تعيد إنشاءه: Settings → Rules → Rulesets، أو `gh api repos/afawzy70/Matbakhy-v1/rulesets`. سيب الفحوصات الموجودة مطلوبة في كل مرة تعدّله.
