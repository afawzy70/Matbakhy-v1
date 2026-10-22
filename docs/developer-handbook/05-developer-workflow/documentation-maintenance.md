# Documentation maintenance system

> **Owner role:** Handbook navigation
> **Status:** REFERENCE — ownership, change matrix, decision history and improvement loop that keep the handbook current
> **Language / اللغة:** English · [العربية](documentation-maintenance-ar.md)

## Ownership (role-based)

| Documentation | Owner role | Typical code areas |
|---|---|---|
| Business rules, order lifecycle, pricing and payments, kitchen management, product pages in `docs/product/` | Business documentation | `backend/modules/orders`, `catalog`, `finance`, `cooks`, `planning/` |
| Backend architecture, module guide, database, API contracts | Backend architecture | `backend/`, `contracts/`, `backend/app/src/main/resources/db/` |
| Web and mobile architecture, journeys, code guides | Frontend and mobile | `apps/`, `packages/` |
| Identity, MFA, Keycloak, admin console | Security and Keycloak | `backend/modules/identity`, `infra/identity`, `deploy/web-preview/identity` |
| Infrastructure, deployment, CI/CD, preview | Infrastructure and deployment | `infra/`, `deploy/`, `.github/workflows/` |
| Test execution, owner acceptance catalog, evidence | Testing and QA | `docs/quality/`, `verification/`, `**/src/test`, `e2e*` |
| Handbook navigation, policy, matrix, onboarding | Handbook navigation | `docs/developer-handbook/` |

Roles are initially role-based, not named people. The reviewer of a PR is responsible for the role(s) listed for each area in [doc001-matrix.json](../doc001-matrix.json).

## Documentation change matrix

The machine-readable matrix is [doc001-matrix.json](../doc001-matrix.json): each area lists code globs, the documentation that must change with them, and the owner role. Examples (full list in the file):

| Change in | Update at least one of |
|---|---|
| `backend/modules/orders/**` | `04-codebase/order-implementation.md`, `01-business/order-lifecycle.md`, `02-user-journeys/complete-order-journey.md`, `docs/product/04-state-machines.md` |
| `apps/customer-mobile/**` | `02-user-journeys/customer-mobile.md`, `03-architecture/mobile-architecture.md`, `04-codebase/mobile-code-guide.md` |
| `backend/app/src/main/resources/db/**` | `03-architecture/database-architecture.md`, `docs/engineering/02-data-model.md` |
| `infra/identity/**`, `deploy/web-preview/identity/**` | `03-architecture/identity-and-security.md`, `02-user-journeys/authentication-and-mfa.md`, `docs/security/mfa-policy.md` |
| `.github/workflows/**` | `05-developer-workflow/ci-cd.md`, `05-developer-workflow/preview-environment.md` |

"At least one" is the deterministic floor. The author must update **every** page the change makes inaccurate; the reviewer checks that.

## Decision history

Decisions live in `planning/decisions.json` and [docs/product/01-context-and-decisions.md](../../product/01-context-and-decisions.md); the index is [architecture-decisions](../07-reference/architecture-decisions.md). When a rule changes: (1) add a new decision that names its predecessor, (2) mark the old text as superseded with a link to the new decision, (3) update implementation, tests and the handbook, (4) keep the historical context. Never silently rewrite decision history.

## Continuous improvement

When anyone finds missing, confusing or wrong documentation: record it as a GitHub issue (title prefix `docs:`), fix the canonical page in the PR that discovered it, and add a validation to `scripts/doc001_check.py` when the defect class is checkable (and a test in `scripts/test_doc001_gate.py`). Mohamed's first-week findings feed this loop ([final assessment](../06-onboarding-labs/final-assessment.md)).

## Arabic mirror synchronisation

The English handbook is the source of truth. Every English page `X.md` has exactly one Egyptian Arabic mirror `X-ar.md` in the same directory (`README.md` and `README-ar.md` are the two entry points). The mirror is a complete translation, not a summary: all paragraphs, warnings, tables, examples, commands, checklists and links are present, while code, commands, identifiers, paths, versions and URLs stay exactly as in English. The only English handbook file without a mirror is the generated [translation parity report](../translation-parity-report.md); the machine-readable [exceptions](../translation-exceptions.json) list it and every other justified difference.

**Review policy.** English and Arabic change together in the same pull request. A change to an English page without the matching Arabic change is not complete, and neither is an Arabic-only edit that changes meaning. Do not paste machine translation: translate by hand, keep the structure identical, and have the Arabic compared with the English side by side before the pair is recorded. Semantic accuracy is a human or independent-reviewer duty; the tooling below checks structure only and never proves that a sentence means the same thing.

| Situation | What to do |
|---|---|
| New English page | Create `X-ar.md` next to it, add the language-switch line to both files, link both from the navigation pages (`README.md` and `README-ar.md`). |
| Edited English page | Make the equivalent edit in `X-ar.md` in the same PR. |
| Renamed or removed English page | Rename or remove `X-ar.md` in the same PR and fix the links in both languages. |
| Arabic wording fix | Edit `X-ar.md` only if the English meaning is unchanged; run the validator. |
| A justified language-specific difference | Add an entry with a reason and a reviewer note to `docs/developer-handbook/translation-exceptions.json`. Unused entries fail the check. |

**Commands** (run from the repository root; the validator needs `markdown-it-py`, installed with `python3 -m pip install 'markdown-it-py==3.0.0'`):

```bash
python3 scripts/validate_handbook_parity.py                         # every page
python3 scripts/validate_handbook_parity.py --file 01-business/order-lifecycle.md   # one English page, path relative to the handbook
python3 scripts/validate_handbook_parity.py --fix-anchors           # rewrite English-style link fragments to the Arabic heading slugs
python3 scripts/validate_handbook_parity.py --base origin/develop   # also require same-PR Arabic changes
python3 scripts/validate_handbook_parity.py --update-sync           # record the pair as reviewed together
python3 scripts/test_validate_handbook_parity.py                    # validator unit tests
```

The validator compares, for every pair: heading levels, code blocks (language and exact content; Mermaid block structure with labels masked), tables (rows and columns), lists and checklist items, blockquotes, paragraphs, images, link destinations (internal links must point to the Arabic page and to the equivalent Arabic heading), inline-code identifiers, technical tokens and numbers, the `Owner role` and `Status` metadata, and the language-switch line. It also reports English pages without a mirror and Arabic pages without a source. `docs/developer-handbook/translation-sync.json` records a hash of each pair when it was last reviewed together; the check fails if the English changed and the Arabic did not. Run `--update-sync` only after you have really compared both files.

The CI workflow `.github/workflows/handbook-parity.yml` (job `Handbook Arabic parity (structural)`) runs the tests, the full validation, the same-PR rule and `python3 scripts/doc001_check.py docs` when handbook files change. It is not part of the required checks on `develop` (the ruleset is unchanged); a failing run is still a signal to fix before merging. The current audit, including the semantic review and remaining exceptions, is in the [translation parity report](../translation-parity-report.md).

## Branch protection status

**Enforced since 2026-10-20 by a repository ruleset** (id 25447480, "develop protection (PR + required checks + DOC-001)", enforcement active, target `develop` only; `main` is untouched).

| Rule | Setting |
|---|---|
| Deletion, force push | blocked |
| Pull request required | yes; approving reviews required: 0 (see limitations); stale reviews dismissed on push; Code Owners review option on |
| Required status checks | `Documentation Consistency / DOC-001`; `Workspace from clean checkout (P01-CHECK-1/2)`; `Local stack bootstrap and smoke (P01-CHECK-3)`; `Design tokens, specimens and prototype journeys (P00-CHECK-2)`; `Secret scan`; `blueprint-integrity`; `Android debug APK (Gradle)`; `iOS simulator build (Xcode, unsigned)`; branch need not be up to date (avoids rebase deadlocks) |
| Bypass actors | none |

Not required on purpose: `Migrate, seed, re-seed (idempotent), reconcile` (path-filtered, would never report on most PRs) and the deploy jobs (post-merge). Deployment is unaffected: `deploy-preview.yml` and the owner E2E provisioning workflow run on pushes created by merging a PR, and no workflow pushes to `develop` itself.

**Verified on GitHub (2026-10-20), using disposable PRs against `develop`:** the workflow triggers on pull requests to `develop` (a first attempt with `pull_request_target` never ran because GitHub reads that event's workflow from `main`; the trigger was changed in PR #26). Results of the check:

| Disposable PR | Case | DOC-001 result |
|---|---|---|
| #25 | code change, no declaration | fail ("exactly one DOC-001 declaration is required") |
| #27 | `impact: none` with a generic justification | fail |
| #28 | code change plus a real handbook update and valid declaration | pass |
| #29 | `impact: none` with a specific justification citing existing pages | pass, flagged for reviewer evaluation |
| #30 | documentation-only change with a broken link | fail (broken relative link) |
| #33 | PR replaces the DOC-001 job with a no-op step | **pass (2 s): the check is neutralised** — see the first limitation |
| #26 (real PR) | governance change without a second identity's approval; first run also failed for missing area documentation | fail, as designed |

With the ruleset active, `gh pr merge` was refused for the failing disposable PRs and GitHub reported their merge state as blocked.

**Limitations (honest status):**

- **A PR can neutralise DOC-001 by editing the workflow — DEFERRED by owner decision (2026-10-20), severity High, still open.** With the `pull_request` event GitHub runs the workflow file from the PR itself, so disposable PR #33, which replaced the job with a no-op step, passed the required check in 2 seconds. DOC-001 is therefore enforced against honest or careless changes and agent mistakes, **not** against a change that also rewrites `.github/workflows/doc001.yml`. Mohamed joins active development only after the first release, so remediation is postponed; nothing was installed. Interim control: the owner reads every diff that touches `.github/workflows/`. Remediation options for later (none selected): (1) an external GitHub App that posts the required check from the base branch's gate, with the ruleset pinned to the App's `integration_id` (design, verifier code, 10 tests and two Codex review rounds are preserved on the unmerged branch `governance/doc001-single-account-model`, closed PR #36; needs an App and a small separate repository, both owner actions); (2) a `pull_request_target` workflow on `main`, which is excluded while `main` stays untouched; (3) a second human GitHub account plus Code Owners review (not possible with the single shared account); (4) accept the limitation and rely on diff review. Rejected or unavailable: ruleset "required workflows" (not available for personal-account repositories), push rulesets on governance paths (block legitimate proposals), environments with required reviewers (a PR can remove them).
- **Independent review is not enforced.** One GitHub account authors and could approve everything, so "approval by someone other than the author" can never be satisfied by the current identity; the gate also accepts an approval from any non-author login without checking that the reviewer is a collaborator (a fix and a single-account owner-authorization model were drafted in PR #34/#36, both closed unmerged; deferred with the item above). Governance changes (workflow, gate script, matrix, policy, agent files, PR template, CODEOWNERS) fail DOC-001 and cannot merge while the ruleset has no bypass actor, unless they also neutralise the check as above. The owner must edit the ruleset (Settings → Rules → Rulesets) for the specific change and restore it afterwards, or add a second reviewer identity. No bypass actor was configured because adding one weakens enforcement and was not approved.
- **Code Owners review is configured but not demonstrated as enforcing**: with zero required approvals GitHub reported no review decision on a disposable PR touching a Code Owners file (#31). Raising the count to 1 would deadlock every PR while the account is the only author. Treat DOC-001's governance rule, not Code Owners, as the effective control.
- **Merge restrictions were verified with the owner-level token only**; no unprivileged or non-bypass test identity exists, so enforcement against other contributors is PARTIALLY VERIFIED. Direct pushes to `develop` were not attempted (it would change `develop` if the rule failed); the rule's presence is confirmed through the rules API.
- Approval dismissal after a green DOC-001 run does not re-run the check; a check on a stale head is replaced when new commits arrive.
- The check proves structure and reference integrity only; semantic review remains a human or independent-reviewer duty ([documentation policy](documentation-policy.md)).

To change or recreate the ruleset: Settings → Rules → Rulesets, or `gh api repos/afawzy70/Matbakhy-v1/rulesets`. Keep the existing checks required whenever editing it.
