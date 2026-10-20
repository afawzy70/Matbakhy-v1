# Branch and worktree maintenance policy

> **Owner role:** Handbook navigation
> **Status:** REFERENCE — policy for naming, lifecycle and safe cleanup of Git branches and worktrees; deletion is manual, the audit is report-only

## Principles

1. **Unique work is never deleted.** A branch with commits whose patches are not in `develop` (or its PR base) is preserved, whatever its age or the state of its PR. A closed PR does not make its branch safe.
2. **Deletion is a deliberate, recorded act.** Record branch name, last SHA, merge status and reason in an audit report (example: [branch cleanup audit 2026-10-20](../07-reference/branch-cleanup-audit-2026-10-20.md)). The recorded SHA is the recovery handle: `git branch <name> <sha>` restores a branch while the commit still exists in the remote or a clone (GitHub keeps unreachable commits for a limited time; do not rely on it).
3. **Nothing automatic deletes branches.** `scripts/branch_audit.py` only reports. Any future auto-delete must be limited to branches verified as merged (all commits ancestors of the target), must skip protected branches, open-PR heads and bases, worktree-occupied branches and branches referenced by workflows, and needs an owner decision recorded in [architecture decisions](../07-reference/architecture-decisions.md).
4. Never use `git reset --hard`, `git clean -fd`, `git branch -D`, `git worktree remove --force` or `git push --force` as cleanup shortcuts. Never touch tags, releases, deployment artifacts or backups. `main` is not modified by this project; `develop` is the integration and deploy branch.

## Naming and lifecycle

| Kind | Pattern | Lifecycle |
|---|---|---|
| Feature or fix | `feat/<topic>`, `fix/<topic>` | branch from `origin/develop`; one PR; delete after the merge is verified (see below) |
| Documentation | `docs/<topic>` | same |
| Security, CI, ops | `security/<topic>`, `ci/<topic>`, `ops/<topic>` | same; ops request-file PRs are one-shot |
| QA or review evidence | `qa/<topic>`, `review/<topic>` | keep until the evidence is merged or archived in `verification/` or `docs/quality/` |
| Agent work | `agent/<topic>`, `claude/<session>` | keep until its work is merged or explicitly abandoned by the owner |
| Disposable test | `test/<topic>` | for enforcement experiments (for example DOC-001); delete once evidence is recorded and the PR is closed, after confirming no unique work |
| Long-lived | `main`, `develop`, a documented stabilisation or integration base | never deleted without an owner decision |

**After a PR merges:** confirm the merge commit is on `develop` and the deployment (if any) succeeded; confirm `git merge-base --is-ancestor <branch-sha> origin/develop`; then delete the remote branch and the local branch (`git branch -d`, which refuses unmerged work). Prune with `git fetch --prune`.

**Abandoned feature branches:** list with the audit; if a branch has unique patches and no open PR, ask the owner (or the author) to merge, open a PR, archive it as a tag/note with its SHA, or explicitly approve deletion. Silence is not approval.

**Stale worktrees:** a worktree may be removed only when it is clean (`git status --porcelain` empty), its HEAD is contained in `origin/develop` or in a preserved branch, no process has it as working directory and no active agent session owns it. Use plain `git worktree remove <path>` (no `--force`) then `git worktree prune`. Ignored build output (node_modules, target, dist) is regenerable and does not block removal. Detached HEADs that are not contained anywhere are unique work: keep them.

## Responsibilities

The author of a branch deletes it after the merge. The reviewer or supervising agent runs the audit before closing a work stream. The owner decides on abandoned branches and on any exception. Agents must not delete branches owned by another agent or session.

## Running the audit

```
git fetch --prune origin
python3 scripts/branch_audit.py --gh --local          # human-readable
python3 scripts/branch_audit.py --gh --json           # machine-readable
```

Classes: PROTECTED, ACTIVE (open PR as head or base, worktree, workflow reference), UNMERGED_WORK, SAFE_TO_DELETE (candidate list only), NEEDS_REVIEW. Tests: `scripts/test_branch_audit.py`.

## Related

[Git and PR workflow](git-and-pr-workflow.md) · [DOC-001 policy](documentation-policy.md) · [CI/CD](ci-cd.md)
