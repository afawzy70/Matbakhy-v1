# Documentation maintenance system

> **Owner role:** Handbook navigation
> **Status:** REFERENCE — ownership, change matrix, decision history and improvement loop that keep the handbook current

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

- **A PR can neutralise DOC-001 by editing the workflow (demonstrated, severity High; STILL OPEN).** With the `pull_request` event GitHub runs the workflow file from the PR itself, so disposable PR #33, which replaced the job with a no-op, passed the required check in 2 seconds. DOC-001 is therefore enforced against honest or careless changes and agent mistakes, **not** against a change that also rewrites `.github/workflows/doc001.yml`. Owner decision (2026-10-20): `main` is not modified, so the fix is an external trusted check: a GitHub App posts the required check from the protected base branch's gate and the ruleset pins the check to the App id. The design, verifier code (`scripts/doc001_trusted_verifier.py`) and logic tests exist; the App, its hosting and the pin are **not installed**, and the hole will not be called fixed until a copy of PR #33 is observed BLOCKED. Steps and status: [trusted enforcement](doc001-trusted-enforcement.md). Interim control: the owner reviews every diff that touches `.github/workflows/`.
- **Single GitHub account (owner decision 2026-10-20).** Mohamed uses the owner's account, so no separate reviewer identity exists and none is required. Governance changes need the owner's `OWNER-AUTHORIZES-GOVERNANCE <head sha>` comment plus a `governance_review` recording the actual author/reviewer roles; Codex code/security reviews are supporting evidence and never equivalent to a separate human approval. GitHub cannot verify who is who, so this is an auditable procedure, not identity enforcement. No bypass actor is configured. The first governance PR under this model is judged by the previously merged gate and needs the owner to relax the ruleset once (disable, merge, re-enable); see [trusted enforcement](doc001-trusted-enforcement.md#5-governance-changes-in-the-single-account-model).
- **Code Owners review is configured but cannot enforce anything** with one account and zero required approvals (a disposable PR touching a Code Owners file, #31, got no review decision). Raising the count to 1 would deadlock every PR. The effective controls are the DOC-001 governance rule and, once installed, the pinned external check.
- **Merge restrictions were verified with the owner-level token only**; no unprivileged or non-bypass test identity exists, so enforcement against other contributors is PARTIALLY VERIFIED. Direct pushes to `develop` were not attempted (it would change `develop` if the rule failed); the rule's presence is confirmed through the rules API.
- Approval dismissal after a green DOC-001 run does not re-run the check; a check on a stale head is replaced when new commits arrive.
- The check proves structure and reference integrity only; semantic review remains a human or independent-reviewer duty ([documentation policy](documentation-policy.md)).

To change or recreate the ruleset: Settings → Rules → Rulesets, or `gh api repos/afawzy70/Matbakhy-v1/rulesets`. Keep the existing checks required whenever editing it.
