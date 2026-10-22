# مسار Git والـ Pull Request

> **Owner role:** Handbook navigation
> **Status:** REFERENCE — قواعد الـ Branch والـ Commit والـ PR والمراجعة والدمج زي ما بتتمارس في الـ Repository ده.
> **Language / اللغة:** [English](git-and-pr-workflow.md) · العربية

المرجعية: [CLAUDE.md](../../../CLAUDE.md)، [AGENTS.md](../../../AGENTS.md)، [عقد التقارير عن التقدّم](../../delivery/05-progress-reporting.md)، [بروتوكول التنفيذ](../../delivery/02-execution-protocol.md).

## الـ Branches

| الـ Branch | الدور |
|---|---|
| `develop` | Branch التكامل ومُشغّل الـ Deployment: أي Push بيشغّل Product CI وبعده الـ Deployment بتاع بيئة المعاينة على Contabo ([ci-cd.md](ci-cd-ar.md)). ابني شغلك عليه |
| `main` | ماينفعش مطوّر ولا Agent يعدّله. اعتبره للقراءة بس |
| `implementation/mvp` | Branch تنفيذ قديم طويل العمر بتشير له مستندات أقدم وفلاتر الـ Push في `.github/workflows/native-build.yml` |
| الـ Topic branches | أي حاجة تانية، واسمها `<kind>/<short-description>` |

البادئات اللي ظاهرة في قائمة الـ Branches على الـ Remote: `docs/` و`ci/` و`ops/` و`fix/` و`security/` و`qa/` و`design/` و`stabilize/` و`chore/` و`beta/` و`audit/` و`agent/`. استخدم اللي بتناسب تغييرك، مثلًا docs/developer-handbook-doc001 و`ci/owner-e2e-provision-workflow` و`fix/admin-console-rest`. خلّي الأسماء بحروف صغيرة وشرطات.

```bash
git fetch origin
git switch -c fix/<short-description> origin/develop
```

## الـ Commits

- Commits صغيرة ومحددة النطاق؛ كل واحد فيه تغيير منطقي واحد. رسالة الـ Commit: أول سطر بصيغة الأمر بيقول التغيير، وجسم بيشرح السبب لما مايكونش واضح (بص على `git log --oneline -20` عشان تشوف الأسلوب المتبع).
- ماتعملش Commit أبدًا لـ Secrets أو بيانات شخصية حقيقية أو روابط موقّعة أو Logs خاصة. الـ Repository عام. `scripts/check_secrets.sh` (Gitleaks على التاريخ والشجرة) بيشتغل في الـ CI.
- ماتعملش Force-push على Branches مشتركة، ولا تتخلّص من شغل غيرك، ولا تتجاوز حماية الـ Branch.
- جلسات الـ Agents بتضيف سطور الإسناد (attribution trailers) اللي تعليماتها بتطلبها؛ والبشر مش محتاجين.

## قبل ما تفتح PR

```bash
pnpm typecheck && pnpm lint && pnpm test      # JS/TS you touched
(cd backend && ./mvnw -B -ntp verify)         # backend you touched (Docker running); the subshell keeps you in the repository root
pnpm contract:check                            # if contracts/openapi.yaml or the client changed
python3 scripts/validate_blueprint.py          # if planning/ changed
python3 scripts/doc001_check.py docs           # always, when any Markdown or handbook page changed
python3 -m unittest discover -s scripts -p 'test_*.py'   # if scripts/ or deploy/ changed
```

ماتقلّلش معايير القبول، ولا تكتم الفشل، ولا تشيل فحوصات الأمان، ولا تستبدل اختبارات التكامل الحقيقية بـ Mocks عشان الفحوصات تنجح.

## وصف الـ PR

القالب هو `.github/pull_request_template.md` وفيه خمس أقسام: المشكلة والسلوك المقصود؛ التغييرات (معرّفات الـ Stories والسلوك والعقود/الـ Migrations وواجهات التطبيق)؛ التحقق (الـ Commit المصدر اللي اتختبر، والأوامر والنتايج الفعلية، ومسارات الأدلة، وNOT_RUN/BLOCKED مذكورة صراحةً)؛ المراجعة والمخاطر (النتايج، والإصلاحات، والمخاطر المتبقية، وقرارات المالك، وتأثيرات الـ Deploy/Rollback)؛ وقسم DOC-001 Documentation Impact. ماتدّعيش مراجعة مستقلة أو موافقة إلا لو حصلت فعلًا.

DOC-001 بيضيف **إقرار Documentation Impact** إلزامي بين `<!-- DOC-001:BEGIN -->` و`<!-- DOC-001:END -->`. المفاتيح المسموحة وقواعده بيفرضها `python3 scripts/doc001_check.py pr` (المفاتيح: `impact` و`summary` و`handbook_pages` و`other_docs` و`diagrams` و`contracts` و`tests` و`traceability` و`consistency` و`justification` و`governance_review`). الصيغة والأمثلة ومصفوفة التغيير: [documentation-policy.md](documentation-policy-ar.md). قالب الـ PR فيه البلوك أصلًا كنموذج فاضي: املاه، وخلّي بلوك واحد بالظبط، وماتلصقش نسخة تانية (البلوك الفاضي أو المكرر بيفشّل الفحص).

## البدء في Issue

1. اعرض الشغل المفتوح: `gh issue list --state open` (ضيف `--label <label>` بعد ما `gh label list` يوريك الموجود؛ مفيش اصطلاح للـ Labels غير قالب Issue العيوب في `.github/ISSUE_TEMPLATE`).
2. اكتب تعليق بخطة مختصرة على الـ Issue (المتطلب، والموديولات المتأثرة، والاختبارات، وصفحات الدليل من [doc001-matrix.json](../doc001-matrix.json)) قبل ما تبدأ كتابة.
3. افتح Branch من Branch التكامل: `git switch -c <kind>/<short-description> origin/develop` (`fix/` و`feat/` و`docs/` و`test/`).
4. اكتب الاختبار الفاشل الأول، وبعدين نفّذ، وحدّث التوثيق في نفس التغيير، وشغّل الفحوصات في [تشغيل الاختبارات](test-execution-ar.md) و`python3 scripts/doc001_check.py docs`.
5. افتح الـ PR على `develop` بـ `gh pr create --base develop`، واملا بلوك Documentation Impact، واربطه بالـ Issue. ماتدمجوش بنفسك من غير تفويض من المالك.

## أدوار المراجعة

- **المراجعة الذاتية** من صاحب التغيير أو الـ Agent المنفّذ مفيدة ومطلوبة، لكنها بتتسمّى مراجعة ذاتية في كل مكان. عمرها ما بتكون "مراجعة مستقلة".
- **الـ QA المستقل** اتعمل بواسطة Codex على SHAs مرشّحة ثابتة: سجلات التسليم والإصلاح هي [06](../../quality/06-codex-qa-handoff.md) و[07](../../quality/07-codex-qa-repair.md) و[08](../../quality/08-codex-qa-repair-cross-role.md). النتايج بتفضل FIXED_PENDING_INDEPENDENT_RETEST لحد ما المراجع يعيد الاختبار؛ وصاحب التغيير ماينفعش يقفلها.
- **المالك** هو اللي بيقرر النطاق، وبيوافق على المعاينات/الـ Deployments، وبيدمج في الـ Branches المحمية.
- **ملفات الحوكمة** (سياسة DOC-001 والـ Matrix والـ Checker والـ Workflow) محتاجة مراجعة APPROVED من حد غير صاحب الـ PR (`scripts/doc001_check.py`، القاعدة الخاصة بـ `governance_review`).
- المراجع الخارجي المجدول للقراءة بس: عمره ما بيغيّر كود ولا يعلّق ولا يدمج ولا ينشر ([05-progress-reporting.md](../../delivery/05-progress-reporting.md)).

## قواعد الدمج

- الـ Agents عمرهم ما بيدمجوا من غير تفويض صريح من المالك. المطوّر بيدمج بس لما سياسة الـ Repository والمالك يسمحوا.
- الـ PR محتاج الفحوصات المطلوبة تكون ناجحة، ووصف كامل، وتعليقات مراجعة اتحلّت. حالة الـ CI بتتقرا من الـ PR أو `gh run list`؛ والـ Push لوحده مابيثبتش إن الـ CI نجح.
- الدمج في `develop` بيشغّل Deployment لبيئة المعاينة بعد ما Product CI ينجح. اعتبر الدمج إصدار على بيئة المعاينة المشتركة: اتأكد إن الـ Migrations Expand-only (`scripts/check_migration_compat.py`)، وإن الـ PR بيذكر تأثيرات الـ Deploy/Rollback.
- ماتدمجش وماتلمسش `main`.

## أوامر مفيدة

```bash
gh pr create --base develop --fill            # then edit the body to the template + DOC-001 block
gh pr checks                                   # status of CI jobs on the current PR
gh run list --branch "$(git branch --show-current)" --limit 5
gh run view <run-id> --log-failed
```

## القيود المعروفة وفجوات التوثيق

- إعدادات حماية الـ Branch مش ظاهرة من الـ Repository؛ والمراجعين/الفحوصات اللي GitHub بيفرضها على `develop`: NOT VERIFIED.
- مستندات أقدم بتشير لـ `implementation/mvp` على إنه الـ Branch النشط؛ والممارسة الحالية (آخر Merge Commits وWorkflow الـ Deploy) هي `develop`.
