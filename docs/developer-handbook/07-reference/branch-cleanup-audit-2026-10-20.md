# Branch cleanup audit 2026-10-20

> **Owner role:** Handbook navigation
> **Status:** REFERENCE — dated audit with recovery SHAs; the repository state changes after this date
> **Language / اللغة:** English · [العربية](branch-cleanup-audit-2026-10-20-ar.md)

Executed under the owner's authorization of 2026-10-20 (delete only conclusively verified-safe branches and worktrees). Method: `git fetch --prune`, per-branch ancestry check against `origin/develop` (`git merge-base --is-ancestor`), patch check (`git cherry`), pull-request list (open and closed, head and base), worktree list, workflow references. Only branches whose every commit is an ancestor of `origin/develop` and that had no open PR, worktree, workflow reference or open-PR base were deleted. Deletion used `git push origin --delete` and `git branch -d` (never `-D`). No tags, releases, backups or `main` were touched.

## Deleted (all commits are ancestors of `develop` at the time; restore with `git branch <name> <sha>`)

| Branch | Last SHA | Pull request / reason | Where deleted |
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
| docs/owner-mvp-first-quality-sequence-2026-10-13 | `047f42eebfcfc904622a123bbb8bdf9b2a16cda8` | none | deleted-remote |
| wip/mb-033-native-batch | `18d02602046661c7e3df32bd445307d2584364a5` | none | deleted-remote |
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
| qa/owner-e2e-master-catalog-2026-10-18 | `274c94d0a68fc92744861d2b5c88cecfafc3ef45` | #16,#12:MERGED | deleted-remote+local (worktree removed first) |
| test/doc001-negative-no-assessment | `02673c986d8a751100035da23292d30134ffa508` | disposable test PR (closed unmerged) | deleted-remote |
| test/doc001-negative-generic-justification | `057212652bb787bdcfd806de4da49ba261aeccd5` | disposable test PR (closed unmerged) | deleted-remote |
| test/doc001-positive-valid-update | `3b53c61c50a64b691604e30580f56d1e92b7caff` | disposable test PR (closed unmerged) | deleted-remote |
| test/doc001-positive-specific-neutral | `addd5086961d1b50516196fd044d7ec59fa7f2d2` | disposable test PR (closed unmerged) | deleted-remote |
| test/doc001-negative-docs-broken-link | `dee3861cdce42c0ee0a7e3561297f8abc8e5702c` | disposable test PR (closed unmerged) | deleted-remote |
| test/doc001-governance-codeowner | `7634c720c12fc2e3885d7ee38192d55f23eb26c0` | disposable test PR (closed unmerged) | deleted-remote |
| test/doc001-workflow-noop | `d88d6867aa02c5803c522fa2042762995ae47009` | disposable test PR (closed unmerged) | deleted-remote |
| docs/developer-handbook-doc001 | `5404c42a0542e6958ada48a64319f0ba81744ef6` | merged PR | deleted-remote |
| fix/doc001-pull-request-trigger | `d8de1eb32da4c557378ec103fbceacb0b652811d` | merged PR | deleted-remote |
| docs/branch-maintenance | `994456625b58f00d3184980527128719a345305e` | merged PR | deleted-remote |

The seven `test/doc001-*` branches were disposable enforcement-test branches (one test-only commit each, PRs closed unmerged, their PR heads remain on GitHub); their local copies needed `git branch -D` because they were never merged — the single forced deletion, justified by that.

## Preserved

| Branch | Reason |
|---|---|
| `main`, `develop` | protected / long-lived |
| `implementation/mvp` | open PR #1 (also named in `native-build.yml`; base of open PR #6) |
| `stabilize/integration-2026-10-18` | base of open PRs #10 and #11; its tip is a merge commit of the mistargeted PR #12 not in `develop` (NEEDS_REVIEW) |
| `agent/security-audit` (PR #10), `beta/ios-testflight-prep` (PR #11), `audit/ux-independent-implementation-2026-10-13` (PR #6) | open PRs, unique commits, owner-approval needed to close |
| `ci/develop-pipeline` | fully merged (PR #7), but checked out in the primary clone worktree; preserved |
| `fix/doc001-reviewer-authority` | open PR #34 (governance fix awaiting the owner) |
| `chore/cd-ssh-smoke` | 1 unique commit, no PR: UNMERGED_WORK |
| `claude/kane-cli-qa-pilot-*` (4 branches) | unique QA pilot commits (1, 2, 4 and 3), no PR: UNMERGED_WORK / possibly owned by idle sessions |
| `qa/full-regression-d877a15-20261013` (34 commits), `qa/independent-browse-basket-eb1a671-20261012` (2), `qa/independent-cross-role-4eaee3a-20261012` (6) | unique QA evidence, no PR: UNMERGED_WORK |

## Worktrees

Removed (clean, contained in `develop`, no process using them, plain `git worktree remove`): the three Codex worktrees under `agents/` named `codex`, `codex-e2e` and `codex-qa` (only ignored build output was deleted with them). Retained: the primary clone (`ci/develop-pipeline`), `agents/claude` (this work), `agents/claude-beta` (PR #11), `agents/security` (PR #10), and `agents/codex-sec` (detached `8ddc7b5`, reachable from `beta/ios-testflight-prep` only; kept).

## Not cleaned (needs owner review)

The preserved UNMERGED_WORK and open-PR branches above. Candidates for an owner decision: abandoned QA pilot branches and `chore/cd-ssh-smoke`.

## Final verification (after all deletions)

`git fetch --prune origin` leaves these remote branches: main, develop, implementation/mvp, stabilize/integration-2026-10-18, agent/security-audit, beta/ios-testflight-prep, audit/ux-independent-implementation-2026-10-13, ci/develop-pipeline, chore/cd-ssh-smoke, the four claude/kane-cli-qa-pilot-* branches, the three qa/* evidence branches and fix/doc001-reviewer-authority. Local branches: agent/security-audit, beta/ios-testflight-prep, ci/develop-pipeline, develop, fix/doc001-reviewer-authority, implementation/mvp. Worktrees: primary clone, claude (detached at develop), claude-beta, security, codex-sec. Re-run `python3 scripts/branch_audit.py --gh --local` for the live classification.
