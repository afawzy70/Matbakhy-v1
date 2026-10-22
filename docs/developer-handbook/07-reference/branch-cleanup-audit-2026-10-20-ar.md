# مراجعة تنضيف الـ Branches بتاريخ 2026-10-20

> **Owner role:** Handbook navigation
> **Status:** REFERENCE — مراجعة مؤرخة مع SHAs للاسترجاع؛ حالة الـ Repository بتتغيّر بعد التاريخ ده
> **Language / اللغة:** [English](branch-cleanup-audit-2026-10-20.md) · العربية

اتنفّذت بتفويض المالك بتاريخ 2026-10-20 (امسح الـ Branches والـ Worktrees اللي اتأكدنا بشكل قاطع إنها آمنة بس). الطريقة: `git fetch --prune`، وفحص النسب (Ancestry) لكل Branch مقابل `origin/develop` (`git merge-base --is-ancestor`)، وفحص الـ Patch (`git cherry`)، وقائمة الـ Pull Requests (المفتوحة والمقفولة، الـ Head والـ Base)، وقائمة الـ Worktrees، ومراجع الـ Workflows. الـ Branches اللي كل الـ Commits بتاعتها أسلاف (Ancestors) لـ `origin/develop` وملهاش PR مفتوح ولا Worktree ولا مرجع في Workflow ولا Base لـ PR مفتوح بس هي اللي اتمسحت. المسح استخدم `git push origin --delete` و`git branch -d` (مش `-D` أبدًا). مفيش Tags ولا Releases ولا نسخ احتياطية ولا `main` اتلمسوا.

## اللي اتمسح (كل الـ Commits أسلاف لـ `develop` وقتها؛ الاسترجاع بـ `git branch <name> <sha>`)

| الـ Branch | آخر SHA | الـ Pull Request / السبب | اتمسح من فين |
|---|---|---|---|
| chore/contabo-auto-cd | `9f2ad828eb8dbbcbc179e07a591191e961d9031c` | #8:MERGED | deleted-remote |
| ci/owner-e2e-provision-workflow | `821400fd59ad3042b5c44ee5c34d745c0245561a` | #15:MERGED | deleted-remote |
| design/approved-terracotta | `b83630faed3e9d81222fd01496246ed56f3fb919` | #2:MERGED | deleted-remote |
| docs/owner-e2e-run-record-2 | `098308352feab3ab7df2c871e71fdfeddce15fa5` | #23:MERGED | deleted-remote |
| fix/admin-console-rest | `0bbf4be2afe0f4504781bbf63391fbac9d3a5b1c` | #14:MERGED | deleted-remote |
| ops/owner-e2e-apply-1 | `b7c3fe824d09e6596ef7942f97e9fae50aa3dedf` | #20:MERGED | deleted-remote |
| ops/owner-e2e-import-output | `bc12d5b5178874bcf85854f8595efc045586144a` | #22:MERGED | deleted-remote |
| ops/owner-e2e-plan-1 | `8a12c5ad9fc0593d4ced3ceb0140be6cf10f2418` | #18:MERGED | deleted-remote |
| ops/owner-e2e-private-dir | `01d764bf21952dd42c321b11c099a7d4a9e78926` | #21:MERGED | deleted-remote |
| ops/ship-fixture-manifest | `0ceba2f8f6390720179c47aa1f5875cc800c79d3` | #19:MERGED | deleted-remote |
| security/kc-admin-console-preview | `55b96dd976038339391fbbe8df00c48eb48cf7e0` | #13:MERGED | deleted-remote |
| docs/owner-mvp-first-quality-sequence-2026-10-13 | `047f42eebfcfc904622a123bbb8bdf9b2a16cda8` | مفيش | deleted-remote |
| wip/mb-033-native-batch | `18d02602046661c7e3df32bd445307d2584364a5` | مفيش | deleted-remote |
| agent/claude-mvp | `0eaf755e0d4fb1ba60e2c596dd47a128e6722e7f` | - | deleted-local |
| agent/codex-review | `0eaf755e0d4fb1ba60e2c596dd47a128e6722e7f` | - | deleted-local |
| qa/codex-qa-2026-10-18 | `8a88a6466e9cd9a706b3b05b7f427eb1154c8601` | - | deleted-local |
| review/codex-reviewer-2026-10-18 | `8a88a6466e9cd9a706b3b05b7f427eb1154c8601` | - | deleted-local |
| ci/owner-e2e-provision-workflow | `821400fd59ad3042b5c44ee5c34d745c0245561a` | - | deleted-local |
| docs/owner-e2e-run-record-2 | `098308352feab3ab7df2c871e71fdfeddce15fa5` | - | deleted-local |
| fix/admin-console-rest | `0bbf4be2afe0f4504781bbf63391fbac9d3a5b1c` | - | deleted-local |
| ops/owner-e2e-apply-1 | `b7c3fe824d09e6596ef7942f97e9fae50aa3dedf` | - | deleted-local |
| ops/owner-e2e-import-output | `bc12d5b5178874bcf85854f8595efc045586144a` | - | deleted-local |
| ops/owner-e2e-plan-1 | `8a12c5ad9fc0593d4ced3ceb0140be6cf10f2418` | - | deleted-local |
| ops/owner-e2e-private-dir | `01d764bf21952dd42c321b11c099a7d4a9e78926` | - | deleted-local |
| ops/ship-fixture-manifest | `0ceba2f8f6390720179c47aa1f5875cc800c79d3` | - | deleted-local |
| security/kc-admin-console-preview | `55b96dd976038339391fbbe8df00c48eb48cf7e0` | - | deleted-local |
| stabilize/integration-2026-10-18 | `713631f4ada37f7565537cfd63b7dec2dc73bbdc` | - | deleted-local |
| qa/owner-e2e-master-catalog-2026-10-18 | `274c94d0a68fc92744861d2b5c88cecfafc3ef45` | #16,#12:MERGED | deleted-remote+local (الـ Worktree اتشال الأول) |
| test/doc001-negative-no-assessment | `02673c986d8a751100035da23292d30134ffa508` | PR تجريبي للاستخدام مرة واحدة (اتقفل من غير Merge) | deleted-remote |
| test/doc001-negative-generic-justification | `057212652bb787bdcfd806de4da49ba261aeccd5` | PR تجريبي للاستخدام مرة واحدة (اتقفل من غير Merge) | deleted-remote |
| test/doc001-positive-valid-update | `3b53c61c50a64b691604e30580f56d1e92b7caff` | PR تجريبي للاستخدام مرة واحدة (اتقفل من غير Merge) | deleted-remote |
| test/doc001-positive-specific-neutral | `addd5086961d1b50516196fd044d7ec59fa7f2d2` | PR تجريبي للاستخدام مرة واحدة (اتقفل من غير Merge) | deleted-remote |
| test/doc001-negative-docs-broken-link | `dee3861cdce42c0ee0a7e3561297f8abc8e5702c` | PR تجريبي للاستخدام مرة واحدة (اتقفل من غير Merge) | deleted-remote |
| test/doc001-governance-codeowner | `7634c720c12fc2e3885d7ee38192d55f23eb26c0` | PR تجريبي للاستخدام مرة واحدة (اتقفل من غير Merge) | deleted-remote |
| test/doc001-workflow-noop | `d88d6867aa02c5803c522fa2042762995ae47009` | PR تجريبي للاستخدام مرة واحدة (اتقفل من غير Merge) | deleted-remote |
| docs/developer-handbook-doc001 | `5404c42a0542e6958ada48a64319f0ba81744ef6` | PR اتعمله Merge | deleted-remote |
| fix/doc001-pull-request-trigger | `d8de1eb32da4c557378ec103fbceacb0b652811d` | PR اتعمله Merge | deleted-remote |
| docs/branch-maintenance | `994456625b58f00d3184980527128719a345305e` | PR اتعمله Merge | deleted-remote |

الـ Branches السبعة `test/doc001-*` كانت Branches اختبار فرض مؤقتة (Commit واحد للاختبار بس لكل واحد، والـ PRs اتقفلت من غير Merge، وفضلت رؤوس الـ PRs على GitHub)؛ نسخها المحلية احتاجت `git branch -D` لأنها عمرها ما اتعملها Merge — المسح الإجباري الوحيد، ومبرره ده.

## اللي اتحفظ

| الـ Branch | السبب |
|---|---|
| `main`، `develop` | محمية / طويلة العمر |
| `implementation/mvp` | PR #1 مفتوح (مذكور كمان في `native-build.yml`؛ وهو Base لـ PR #6 المفتوح) |
| `stabilize/integration-2026-10-18` | Base لـ PRs #10 و#11 المفتوحين؛ وآخره Commit دمج لـ PR #12 اللي اتوجّه غلط وهو مش في `develop` (NEEDS_REVIEW) |
| `agent/security-audit` (PR #10)، `beta/ios-testflight-prep` (PR #11)، `audit/ux-independent-implementation-2026-10-13` (PR #6) | PRs مفتوحة، Commits فريدة، وقفلها محتاج موافقة المالك |
| `ci/develop-pipeline` | اتعمله Merge بالكامل (PR #7)، بس متعمله Checkout في Worktree الـ Clone الأساسي؛ اتحفظ |
| `fix/doc001-reviewer-authority` | PR #34 مفتوح (إصلاح حوكمة مستني المالك) |
| `chore/cd-ssh-smoke` | Commit فريد واحد، من غير PR: UNMERGED_WORK |
| `claude/kane-cli-qa-pilot-*` (4 Branches) | Commits تجربة QA فريدة (1، 2، 4، 3)، من غير PR: UNMERGED_WORK / ممكن تكون مملوكة لجلسات خاملة |
| `qa/full-regression-d877a15-20261013` (34 Commit)، `qa/independent-browse-basket-eb1a671-20261012` (2)، `qa/independent-cross-role-4eaee3a-20261012` (6) | أدلة QA فريدة، من غير PR: UNMERGED_WORK |

## الـ Worktrees

اتشالت (نضيفة، محتواة في `develop`، مفيش عملية بتستخدمها، `git worktree remove` عادي): تلات Worktrees بتاعة Codex تحت `agents/` أسماؤهم `codex` و`codex-e2e` و`codex-qa` (ناتج البناء المتجاهَل بس اتمسح معاهم). اتبقّت: الـ Clone الأساسي (`ci/develop-pipeline`)، و`agents/claude` (الشغل ده)، و`agents/claude-beta` (PR #11)، و`agents/security` (PR #10)، و`agents/codex-sec` (Detached على `8ddc7b5`، متاح من `beta/ios-testflight-prep` بس؛ اتحفظ).

## اللي ماتنضفش (محتاج مراجعة المالك)

الـ Branches بتاعة UNMERGED_WORK والـ PRs المفتوحة فوق. المرشحين لقرار من المالك: Branches تجارب الـ QA المتروكة و`chore/cd-ssh-smoke`.

## التحقق النهائي (بعد كل عمليات المسح)

`git fetch --prune origin` بيسيب الـ Branches البعيدة دي: main, develop, implementation/mvp, stabilize/integration-2026-10-18, agent/security-audit, beta/ios-testflight-prep, audit/ux-independent-implementation-2026-10-13, ci/develop-pipeline, chore/cd-ssh-smoke، وأربع Branches claude/kane-cli-qa-pilot-*، وتلات Branches qa/* للأدلة، وfix/doc001-reviewer-authority. الـ Branches المحلية: agent/security-audit, beta/ios-testflight-prep, ci/develop-pipeline, develop, fix/doc001-reviewer-authority, implementation/mvp. الـ Worktrees: الـ Clone الأساسي، وclaude (Detached على develop)، وclaude-beta، وsecurity، وcodex-sec. أعد تشغيل `python3 scripts/branch_audit.py --gh --local` للتصنيف الحي.
