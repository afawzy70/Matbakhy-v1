# Git and pull request workflow

> **Owner role:** Handbook navigation
> **Status:** REFERENCE — branch, commit, PR, review and merge rules as practised in this repository.
> **Language / اللغة:** English · [العربية](git-and-pr-workflow-ar.md)

Authority: [CLAUDE.md](../../../CLAUDE.md), [AGENTS.md](../../../AGENTS.md), [progress reporting contract](../../delivery/05-progress-reporting.md), [execution protocol](../../delivery/02-execution-protocol.md).

## Branches

| Branch | Role |
|---|---|
| `develop` | integration branch and deployment trigger: a push runs Product CI and then the Contabo preview deployment ([ci-cd.md](ci-cd.md)). Base your work on it |
| `main` | must not be modified by a developer or agent. Treat it as read-only |
| `implementation/mvp` | earlier long-lived implementation branch referenced by older docs and by `.github/workflows/native-build.yml` push filters |
| topic branches | everything else, named `<kind>/<short-description>` |

Prefixes seen in the remote branch list: `docs/`, `ci/`, `ops/`, `fix/`, `security/`, `qa/`, `design/`, `stabilize/`, `chore/`, `beta/`, `audit/`, `agent/`. Use the one that matches your change, for example docs/developer-handbook-doc001, `ci/owner-e2e-provision-workflow`, `fix/admin-console-rest`. Keep names lowercase with hyphens.

```bash
git fetch origin
git switch -c fix/<short-description> origin/develop
```

## Commits

- Small, scoped commits; one logical change each. Commit message: imperative first line stating the change, a body explaining why when it is not obvious (look at `git log --oneline -20` for the house style).
- Never commit secrets, real personal data, signed URLs or private logs. The repository is public. `scripts/check_secrets.sh` (Gitleaks over history and tree) runs in CI.
- Do not force-push shared branches, discard other people's work, or bypass branch protection.
- Agent sessions add the attribution trailers their instructions require; humans do not need to.

## Before opening a PR

```bash
pnpm typecheck && pnpm lint && pnpm test      # JS/TS you touched
(cd backend && ./mvnw -B -ntp verify)         # backend you touched (Docker running); the subshell keeps you in the repository root
pnpm contract:check                            # if contracts/openapi.yaml or the client changed
python3 scripts/validate_blueprint.py          # if planning/ changed
python3 scripts/doc001_check.py docs           # always, when any Markdown or handbook page changed
python3 -m unittest discover -s scripts -p 'test_*.py'   # if scripts/ or deploy/ changed
```

Do not reduce acceptance criteria, suppress failures, remove security checks or replace real integration tests with mocks to get green.

## The PR description

The template is `.github/pull_request_template.md` with five sections: Problem and intended behavior; Changes (story IDs, behavior, contracts/migrations, surfaces); Verification (tested source commit, actual commands and results, evidence paths, NOT_RUN/BLOCKED listed explicitly); Review and risks (findings, repairs, residual risks, owner decisions, deploy/rollback implications); and the DOC-001 Documentation Impact section. Do not claim independent review or approval unless it happened.

DOC-001 adds a mandatory **Documentation Impact declaration** between `<!-- DOC-001:BEGIN -->` and `<!-- DOC-001:END -->`. Its allowed keys and rules are enforced by `python3 scripts/doc001_check.py pr` (keys: `impact`, `summary`, `handbook_pages`, `other_docs`, `diagrams`, `contracts`, `tests`, `traceability`, `consistency`, `justification`, `governance_review`). Format, examples and the change matrix: [documentation-policy.md](documentation-policy.md). The PR template already contains the block as an empty form: fill it in, keep exactly one block, and do not paste a second copy (an empty or duplicated block fails the check).

## Starting on an issue

1. List open work: `gh issue list --state open` (add `--label <label>` after `gh label list` shows what exists; there is no label convention beyond the defect issue template in `.github/ISSUE_TEMPLATE`).
2. Comment a short plan on the issue (requirement, affected modules, tests, handbook pages from [doc001-matrix.json](../doc001-matrix.json)) before coding.
3. Branch from the integration branch: `git switch -c <kind>/<short-description> origin/develop` (`fix/`, `feat/`, `docs/`, `test/`).
4. Write the failing test first, implement, update the documentation in the same change, run the checks in [test-execution](test-execution.md) and `python3 scripts/doc001_check.py docs`.
5. Open the PR against `develop` with `gh pr create --base develop`, fill the Documentation Impact block, and link the issue. Do not merge it yourself without owner authorization.

## Review roles

- **Self-review** by the author or implementation agent is useful and required, but is labelled self-review everywhere. It is never "independent review".
- **Independent QA** was performed by Codex against fixed candidate SHAs: handoff and repair records are [06](../../quality/06-codex-qa-handoff.md), [07](../../quality/07-codex-qa-repair.md), [08](../../quality/08-codex-qa-repair-cross-role.md). Findings are FIXED_PENDING_INDEPENDENT_RETEST until the reviewer retests; the author cannot close them.
- **Owner** decides scope, approves previews/deployments and merges to protected branches.
- **Governance files** (DOC-001 policy, matrix, checker, workflow) need an APPROVED review from someone other than the PR author (`scripts/doc001_check.py`, rule on `governance_review`).
- The scheduled external reviewer is read-only: it never changes code, comments, merges or deploys ([05-progress-reporting.md](../../delivery/05-progress-reporting.md)).

## Merge rules

- Agents never merge without explicit owner authorization. A developer merges only where repository policy and the owner allow it.
- A PR needs green required checks, a complete description and resolved review comments. CI status is read from the PR or `gh run list`; a push alone does not prove CI passed.
- Merging to `develop` triggers a preview deployment after Product CI passes. Treat a merge as a release to the shared preview: check migrations are expand-only (`scripts/check_migration_compat.py`), and that the PR states deploy/rollback implications.
- Do not merge and do not touch `main`.

## Useful commands

```bash
gh pr create --base develop --fill            # then edit the body to the template + DOC-001 block
gh pr checks                                   # status of CI jobs on the current PR
gh run list --branch "$(git branch --show-current)" --limit 5
gh run view <run-id> --log-failed
```

## Known limitations and documentation gaps

- Branch protection settings are not visible from the repository; what reviewers/checks GitHub enforces on `develop` is NOT VERIFIED.
- Older documents refer to `implementation/mvp` as the active branch; current practice (recent merge commits, deploy workflow) is `develop`.
