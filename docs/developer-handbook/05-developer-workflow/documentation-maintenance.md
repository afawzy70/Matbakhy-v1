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

Verified on 2026-10-19 with the repository API: `develop` is not protected and no rulesets exist (`gh api repos/afawzy70/Matbakhy-v1/rulesets` returned `[]`). **DOC-001 is therefore NOT a required merge check yet.** See the section at the end of this page for the follow-up state after the PR merges.

To enforce it (repository admin): Settings → Rules → Rulesets → New branch ruleset → target `develop` → enable *Require status checks to pass* → add **`Documentation Consistency / DOC-001`** (the check must have run once on a PR before it can be selected) → enable *Require a pull request before merging* and *Require review from Code Owners* (so governance files in `.github/CODEOWNERS` need the owner's review). Keep the existing checks (Product CI, Blueprint integrity, native builds) required as well; do not remove them.
