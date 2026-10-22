# سياسة صيانة الـ Branches والـ Worktrees

> **Owner role:** Handbook navigation
> **Status:** REFERENCE — سياسة تسمية الـ Branches والـ Worktrees بتاعة Git ودورة حياتها وتنضيفها بشكل آمن؛ الحذف يدوي، والـ Audit بيطلّع تقرير بس
> **Language / اللغة:** [English](branch-maintenance.md) · العربية

## المبادئ

1. **الشغل اللي لسه مش متدمج في الـ Branch المستهدف عمره ما بيتحذف.** أي Branch فيه Commits الـ Patches بتاعتها مش موجودة في `develop` (أو الـ Base بتاع الـ PR) بتتحفظ، مهما كان عمرها أو حالة الـ PR بتاعها. إقفال الـ PR مش معناه إن الـ Branch بتاعه بقى آمن للحذف.
2. **الحذف عملية مقصودة ومتسجّلة.** سجّل اسم الـ Branch وآخر SHA وحالة الدمج والسبب في تقرير Audit (مثال: [Audit تنضيف الـ Branches بتاريخ 2026-10-20](../07-reference/branch-cleanup-audit-2026-10-20-ar.md)). الـ SHA المسجّل هو وسيلة الاسترجاع: `git branch <name> <sha>` بيرجّع الـ Branch طول ما الـ Commit لسه موجود في الـ Remote أو في نسخة Clone (GitHub بيحتفظ بالـ Commits غير المتصلة بأي Branch لفترة محدودة؛ ماتعتمدش على ده).
3. **مفيش حاجة تلقائية بتحذف Branches.** `scripts/branch_audit.py` بيطلّع تقرير بس. أي حذف تلقائي في المستقبل لازم يتحصر في الـ Branches اللي اتأكدنا إنها اتدمجت (كل الـ Commits بتاعتها أسلاف للهدف)، ولازم يتخطى الـ Branches المحمية وأطراف الـ PR المفتوحة (Head وBase) والـ Branches المشغولة بـ Worktree والـ Branches اللي الـ Workflows بتشير لها، ولازم يكون ليه قرار من المالك متسجّل في [قرارات المعمارية](../07-reference/architecture-decisions-ar.md).
4. ماتستخدمش `git reset --hard` ولا `git clean -fd` ولا `git branch -D` ولا `git worktree remove --force` ولا `git push --force` كاختصارات للتنضيف. ماتلمسش الـ Tags ولا الـ Releases ولا ملفات الـ Deployment ولا النسخ الاحتياطية. المشروع ده مابيعدّلش `main`؛ و`develop` هو الـ Branch بتاع التكامل والـ Deploy.

## التسمية ودورة الحياة

| النوع | النمط | دورة الحياة |
|---|---|---|
| ميزة أو إصلاح | `feat/<topic>`, `fix/<topic>` | افتح الـ Branch من `origin/develop`؛ Pull Request واحد؛ احذفه بعد التأكد من الدمج (شوف تحت) |
| التوثيق | `docs/<topic>` | نفس الكلام |
| الأمان والـ CI والعمليات | `security/<topic>`, `ci/<topic>`, `ops/<topic>` | نفس الكلام؛ الـ PRs بتاعة ملفات طلبات العمليات (ops) بتتعمل مرة واحدة وخلاص |
| أدلة الـ QA أو المراجعة | `qa/<topic>`, `review/<topic>` | سيبه لحد ما الأدلة تتدمج أو تتأرشف في `verification/` أو `docs/quality/` |
| شغل الوكلاء (Agents) | `agent/<topic>`, `claude/<session>` | سيبه لحد ما شغله يتدمج أو المالك يتخلّى عنه صراحةً |
| اختبار مؤقت | `test/<topic>` | لتجارب الإنفاذ (مثلًا DOC-001)؛ احذفه بعد ما الدليل يتسجّل والـ PR يتقفل، وبعد التأكد إن مفيش شغل فريد |
| طويل العمر | `main`, `develop`, أو Base موثّق للاستقرار أو التكامل | ماتتحذفش من غير قرار من المالك |

**بعد ما الـ PR يتدمج:** اتأكد إن الـ Merge Commit موجود على `develop` وإن الـ Deployment (لو فيه) نجح؛ اتأكد من `git merge-base --is-ancestor <branch-sha> origin/develop`؛ وبعدين احذف الـ Branch من الـ Remote ومن الجهاز المحلي (`git branch -d`، وده بيرفض لو فيه شغل مش متدمج). نضّف المراجع القديمة بـ `git fetch --prune`.

**الـ Feature Branches المتروكة:** اعمل لها قائمة بالـ Audit؛ لو الـ Branch فيه Patches فريدة ومفيش PR مفتوح، اطلب من المالك (أو من صاحب الـ Branch) إنه يدمجه، أو يفتح PR، أو يأرشفه كـ Tag أو ملاحظة مع الـ SHA بتاعه، أو يوافق صراحةً على حذفه. السكوت مش موافقة.

**الـ Worktrees القديمة:** ممكن تشيل الـ Worktree بس لما تكون نضيفة (`git status --porcelain` فاضي)، والـ HEAD بتاعها متضمّن في `origin/develop` أو في Branch محفوظ، ومفيش أي Process شغّال وهي الـ Working Directory بتاعته، ومفيش جلسة Agent نشطة مالكاها. استخدم `git worktree remove <path>` العادي (من غير `--force`) وبعده `git worktree prune`. مخرجات الـ Build المتجاهلة (node_modules، target، dist) ممكن تتولّد تاني وماتمنعش الإزالة. الـ Detached HEADs اللي مش متضمّنة في أي مكان هي شغل فريد: احتفظ بيها.

## المسؤوليات

صاحب الـ Branch هو اللي بيحذفه بعد الدمج. المراجع أو الـ Agent المشرف بيشغّل الـ Audit قبل ما يقفل مسار شغل. المالك هو اللي بيقرر في الـ Branches المتروكة وفي أي استثناء. الـ Agents ماينفعش تحذف Branches مملوكة لـ Agent أو جلسة تانية.

## تشغيل الـ Audit

```
git fetch --prune origin
python3 scripts/branch_audit.py --gh --local          # human-readable
python3 scripts/branch_audit.py --gh --json           # machine-readable
```

التصنيفات: PROTECTED، ACTIVE (PR مفتوح كـ Head أو Base، أو Worktree، أو إشارة من Workflow)، UNMERGED_WORK، SAFE_TO_DELETE (قائمة مرشّحين بس)، NEEDS_REVIEW. الاختبارات: `scripts/test_branch_audit.py`.

## مرتبط

[مسار Git والـ PR](git-and-pr-workflow-ar.md) · [سياسة DOC-001](documentation-policy-ar.md) · [CI/CD](ci-cd-ar.md)
