# Translation parity report: English Developer Handbook and Egyptian Arabic mirror

> **Owner role:** Handbook navigation
> **Status:** REFERENCE — audit of the complete Egyptian Arabic mirror: counts, structural validation, semantic review rounds, fixed defects, exceptions and limits

This report is the audit record for the Arabic mirror of the handbook. It is generated and maintained with `scripts/validate_handbook_parity.py` (structural section) plus the human-readable review record below. It is intentionally not mirrored into Arabic (see [translation-exceptions.json](translation-exceptions.json)). Procedure and policy: [documentation maintenance](05-developer-workflow/documentation-maintenance.md#arabic-mirror-synchronisation); entry points: [README.md](README.md) and [README-ar.md](README-ar.md).

## 1. Overall result

**PASS, with disclosed limitations.** Every English handbook page has exactly one Arabic counterpart; the structural validator reports no problem for any of the 64 pairs; five rounds of independent AI review reported 72 material and 20 minor findings (many share one root cause, for example 26 were the same terminology slip), all fixed, and the final full round found no unresolved material mismatch. This is **not** proof of perfect semantic equivalence and **not** a human review: see section 8 for what was not done.

| Item | Value |
|---|---|
| Source branch | docs/egyptian-arabic-handbook-mirror, based on `develop` at `badbaad` (merge of PR #37) |
| Content reviewed | all English and Arabic handbook files at the commit that introduced the final review fixes; the commit SHA of this branch is in the pull request |
| English documents (mirrored) | 64 (`README.md` plus 63 pages in the numbered sections) |
| Arabic documents | 64 (`README-ar.md` plus 63 `*-ar.md` pages) |
| Newly translated | 63 |
| Existing Arabic file corrected | 1 (`README-ar.md` was an adapted orientation guide; it is now a full mirror of `README.md`) |
| English files without a mirror | 1 by design: this generated report |
| Structural validation | PASS: 64 of 64 pairs, 0 problems; validator tests: 20 of 20 pass |
| Semantic review | 5 rounds by independent Codex sessions (AI reviewers, not the translators): final full round 61 of 64 PASS before the last fixes, 64 of 64 PASS after them (the five pages that still had findings were re-reviewed in round 5 and passed) |

## 2. Method

1. Inventory: every `*.md` under `docs/developer-handbook/` (recursive) except `*-ar.md` and this report; the exclusions are documented in [translation-exceptions.json](translation-exceptions.json).
2. Translation: 10 parallel translator sessions, each with a written brief (tone, terminology, preserved content, link and Mermaid rules) and a finished example page; Mermaid human labels were translated in a second pass once the validator masked labels safely.
3. Structural validation with a Markdown parser (markdown-it-py 3.0.0), see section 3.
4. Independent semantic review: each English page was read side by side with its Arabic page by a separate Codex session that did not translate it. Rounds used different page groupings so no page was only ever seen by one reviewer. Findings were fixed and the affected material was re-reviewed.
5. Navigation and link checks: language-switch line on every page pair, internal links and anchors resolved in both languages, `python3 scripts/doc001_check.py docs` passes.

## 3. Structural validation (`scripts/validate_handbook_parity.py`)

Checks per pair: missing or orphan mirror; heading levels; code blocks (language and exact content; Mermaid structure with labels masked); tables (rows and columns); lists and checklist items; blockquotes, paragraphs, horizontal rules, HTML blocks; image destinations; link destinations (internal links must target the Arabic page and the equivalent Arabic heading); inline-code identifiers; protected technical tokens and numbers; arrow characters (direction); `Owner role` and `Status` metadata; language-switch line; reviewed-pair record and same-PR rule; justified exceptions. Result: **PASS for all 64 pairs**. Unit tests (`scripts/test_validate_handbook_parity.py`): 20 pass. CI: the workflow `Handbook Arabic parity (structural)` passed on pull request #38, and failed as designed on disposable pull request #39 (a mirror page removed and an English-only edit: missing counterpart, broken links, paragraph mismatch, English changed without Arabic); #39 was closed unmerged and its branch deleted.

<!-- STRUCTURAL:BEGIN -->
| English page | Arabic page | Structural result |
|---|---|---|
| `docs/developer-handbook/00-getting-started/glossary.md` | `docs/developer-handbook/00-getting-started/glossary-ar.md` | PASS |
| `docs/developer-handbook/00-getting-started/learning-roadmap.md` | `docs/developer-handbook/00-getting-started/learning-roadmap-ar.md` | PASS |
| `docs/developer-handbook/00-getting-started/project-overview.md` | `docs/developer-handbook/00-getting-started/project-overview-ar.md` | PASS |
| `docs/developer-handbook/00-getting-started/repository-navigation.md` | `docs/developer-handbook/00-getting-started/repository-navigation-ar.md` | PASS |
| `docs/developer-handbook/01-business/actors-and-permissions.md` | `docs/developer-handbook/01-business/actors-and-permissions-ar.md` | PASS |
| `docs/developer-handbook/01-business/business-rules.md` | `docs/developer-handbook/01-business/business-rules-ar.md` | PASS |
| `docs/developer-handbook/01-business/kitchen-management.md` | `docs/developer-handbook/01-business/kitchen-management-ar.md` | PASS |
| `docs/developer-handbook/01-business/marketplace-model.md` | `docs/developer-handbook/01-business/marketplace-model-ar.md` | PASS |
| `docs/developer-handbook/01-business/order-lifecycle.md` | `docs/developer-handbook/01-business/order-lifecycle-ar.md` | PASS |
| `docs/developer-handbook/01-business/pricing-and-payments.md` | `docs/developer-handbook/01-business/pricing-and-payments-ar.md` | PASS |
| `docs/developer-handbook/01-business/product-vision.md` | `docs/developer-handbook/01-business/product-vision-ar.md` | PASS |
| `docs/developer-handbook/01-business/target-users.md` | `docs/developer-handbook/01-business/target-users-ar.md` | PASS |
| `docs/developer-handbook/02-user-journeys/authentication-and-mfa.md` | `docs/developer-handbook/02-user-journeys/authentication-and-mfa-ar.md` | PASS |
| `docs/developer-handbook/02-user-journeys/backoffice-admin.md` | `docs/developer-handbook/02-user-journeys/backoffice-admin-ar.md` | PASS |
| `docs/developer-handbook/02-user-journeys/complete-order-journey.md` | `docs/developer-handbook/02-user-journeys/complete-order-journey-ar.md` | PASS |
| `docs/developer-handbook/02-user-journeys/cook-operations.md` | `docs/developer-handbook/02-user-journeys/cook-operations-ar.md` | PASS |
| `docs/developer-handbook/02-user-journeys/customer-mobile.md` | `docs/developer-handbook/02-user-journeys/customer-mobile-ar.md` | PASS |
| `docs/developer-handbook/02-user-journeys/customer-web.md` | `docs/developer-handbook/02-user-journeys/customer-web-ar.md` | PASS |
| `docs/developer-handbook/02-user-journeys/finance-and-support.md` | `docs/developer-handbook/02-user-journeys/finance-and-support-ar.md` | PASS |
| `docs/developer-handbook/03-architecture/backend-architecture.md` | `docs/developer-handbook/03-architecture/backend-architecture-ar.md` | PASS |
| `docs/developer-handbook/03-architecture/database-architecture.md` | `docs/developer-handbook/03-architecture/database-architecture-ar.md` | PASS |
| `docs/developer-handbook/03-architecture/diagrams.md` | `docs/developer-handbook/03-architecture/diagrams-ar.md` | PASS |
| `docs/developer-handbook/03-architecture/frontend-architecture.md` | `docs/developer-handbook/03-architecture/frontend-architecture-ar.md` | PASS |
| `docs/developer-handbook/03-architecture/identity-and-security.md` | `docs/developer-handbook/03-architecture/identity-and-security-ar.md` | PASS |
| `docs/developer-handbook/03-architecture/infrastructure-and-deployment.md` | `docs/developer-handbook/03-architecture/infrastructure-and-deployment-ar.md` | PASS |
| `docs/developer-handbook/03-architecture/integration-and-events.md` | `docs/developer-handbook/03-architecture/integration-and-events-ar.md` | PASS |
| `docs/developer-handbook/03-architecture/mobile-architecture.md` | `docs/developer-handbook/03-architecture/mobile-architecture-ar.md` | PASS |
| `docs/developer-handbook/03-architecture/system-overview.md` | `docs/developer-handbook/03-architecture/system-overview-ar.md` | PASS |
| `docs/developer-handbook/04-codebase/api-contracts.md` | `docs/developer-handbook/04-codebase/api-contracts-ar.md` | PASS |
| `docs/developer-handbook/04-codebase/backend-module-guide.md` | `docs/developer-handbook/04-codebase/backend-module-guide-ar.md` | PASS |
| `docs/developer-handbook/04-codebase/frontend-code-guide.md` | `docs/developer-handbook/04-codebase/frontend-code-guide-ar.md` | PASS |
| `docs/developer-handbook/04-codebase/mobile-code-guide.md` | `docs/developer-handbook/04-codebase/mobile-code-guide-ar.md` | PASS |
| `docs/developer-handbook/04-codebase/order-implementation.md` | `docs/developer-handbook/04-codebase/order-implementation-ar.md` | PASS |
| `docs/developer-handbook/04-codebase/payment-implementation.md` | `docs/developer-handbook/04-codebase/payment-implementation-ar.md` | PASS |
| `docs/developer-handbook/04-codebase/pricing-implementation.md` | `docs/developer-handbook/04-codebase/pricing-implementation-ar.md` | PASS |
| `docs/developer-handbook/04-codebase/repository-map.md` | `docs/developer-handbook/04-codebase/repository-map-ar.md` | PASS |
| `docs/developer-handbook/04-codebase/shared-libraries.md` | `docs/developer-handbook/04-codebase/shared-libraries-ar.md` | PASS |
| `docs/developer-handbook/05-developer-workflow/branch-maintenance.md` | `docs/developer-handbook/05-developer-workflow/branch-maintenance-ar.md` | PASS |
| `docs/developer-handbook/05-developer-workflow/ci-cd.md` | `docs/developer-handbook/05-developer-workflow/ci-cd-ar.md` | PASS |
| `docs/developer-handbook/05-developer-workflow/documentation-maintenance.md` | `docs/developer-handbook/05-developer-workflow/documentation-maintenance-ar.md` | PASS |
| `docs/developer-handbook/05-developer-workflow/documentation-policy.md` | `docs/developer-handbook/05-developer-workflow/documentation-policy-ar.md` | PASS |
| `docs/developer-handbook/05-developer-workflow/git-and-pr-workflow.md` | `docs/developer-handbook/05-developer-workflow/git-and-pr-workflow-ar.md` | PASS |
| `docs/developer-handbook/05-developer-workflow/local-environment.md` | `docs/developer-handbook/05-developer-workflow/local-environment-ar.md` | PASS |
| `docs/developer-handbook/05-developer-workflow/prerequisites.md` | `docs/developer-handbook/05-developer-workflow/prerequisites-ar.md` | PASS |
| `docs/developer-handbook/05-developer-workflow/preview-environment.md` | `docs/developer-handbook/05-developer-workflow/preview-environment-ar.md` | PASS |
| `docs/developer-handbook/05-developer-workflow/run-and-debug.md` | `docs/developer-handbook/05-developer-workflow/run-and-debug-ar.md` | PASS |
| `docs/developer-handbook/05-developer-workflow/test-execution.md` | `docs/developer-handbook/05-developer-workflow/test-execution-ar.md` | PASS |
| `docs/developer-handbook/05-developer-workflow/troubleshooting.md` | `docs/developer-handbook/05-developer-workflow/troubleshooting-ar.md` | PASS |
| `docs/developer-handbook/06-onboarding-labs/day-1-business.md` | `docs/developer-handbook/06-onboarding-labs/day-1-business-ar.md` | PASS |
| `docs/developer-handbook/06-onboarding-labs/day-2-applications.md` | `docs/developer-handbook/06-onboarding-labs/day-2-applications-ar.md` | PASS |
| `docs/developer-handbook/06-onboarding-labs/day-3-backend-security.md` | `docs/developer-handbook/06-onboarding-labs/day-3-backend-security-ar.md` | PASS |
| `docs/developer-handbook/06-onboarding-labs/day-4-development.md` | `docs/developer-handbook/06-onboarding-labs/day-4-development-ar.md` | PASS |
| `docs/developer-handbook/06-onboarding-labs/day-5-first-contribution.md` | `docs/developer-handbook/06-onboarding-labs/day-5-first-contribution-ar.md` | PASS |
| `docs/developer-handbook/06-onboarding-labs/final-assessment.md` | `docs/developer-handbook/06-onboarding-labs/final-assessment-ar.md` | PASS |
| `docs/developer-handbook/07-reference/architecture-decisions.md` | `docs/developer-handbook/07-reference/architecture-decisions-ar.md` | PASS |
| `docs/developer-handbook/07-reference/branch-cleanup-audit-2026-10-20.md` | `docs/developer-handbook/07-reference/branch-cleanup-audit-2026-10-20-ar.md` | PASS |
| `docs/developer-handbook/07-reference/documentation-map.md` | `docs/developer-handbook/07-reference/documentation-map-ar.md` | PASS |
| `docs/developer-handbook/07-reference/frequently-asked-questions.md` | `docs/developer-handbook/07-reference/frequently-asked-questions-ar.md` | PASS |
| `docs/developer-handbook/07-reference/fresh-developer-simulation.md` | `docs/developer-handbook/07-reference/fresh-developer-simulation-ar.md` | PASS |
| `docs/developer-handbook/07-reference/implementation-status.md` | `docs/developer-handbook/07-reference/implementation-status-ar.md` | PASS |
| `docs/developer-handbook/07-reference/independent-reviews.md` | `docs/developer-handbook/07-reference/independent-reviews-ar.md` | PASS |
| `docs/developer-handbook/07-reference/inventory-and-gaps.md` | `docs/developer-handbook/07-reference/inventory-and-gaps-ar.md` | PASS |
| `docs/developer-handbook/07-reference/requirements-traceability.md` | `docs/developer-handbook/07-reference/requirements-traceability-ar.md` | PASS |
| `docs/developer-handbook/README.md` | `docs/developer-handbook/README-ar.md` | PASS |
<!-- STRUCTURAL:END -->

## 4. Semantic review rounds

Reviewer: Codex (read-only, independent sessions; AI review, not a human review). Counts are material (M) and minor (m) findings.

| Round | Scope | Pages passing | Material | Minor | Notes |
|---|---|---|---|---|---|
| 1 | all 64 pages, 8 sessions | 45 of 64 | 24 | 13 | all fixed |
| 2 | all 64 pages, new grouping and a list of known defect classes | 52 of 64 | 16 | 6 | all fixed |
| 3 | 56 of 64 pages (one session failed with "model at capacity" and was not re-run; its 8 pages are covered by round 4) | 30 of 56 | 29 | 0 | 26 of the 29 were one terminology defect ("synthetic" rendered as "experimental"); fixed globally; the others fixed individually |
| 4 | all 64 pages after the fixes | 61 of 64 | 3 | 1 | all fixed |
| 5 | the 5 pages touched by round 4, targeted | 5 of 5 | 0 | 0 | no remaining material finding |

Per-document record (each document is classified PASS, FAIL or BLOCKED; a pair is PASS only when it passed the structural validation and no material finding was left open after the last review that covered it). `NOT_RUN` marks the one session that did not complete.

| English page | Arabic page | Structural | Round 1 | Round 2 | Round 3 | Round 4 | Round 5 (targeted) | Result |
|---|---|---|---|---|---|---|---|---|
| `docs/developer-handbook/00-getting-started/glossary.md` | `docs/developer-handbook/00-getting-started/glossary-ar.md` | PASS | FAIL (1 M, 0 m) | PASS | FAIL (1 M, 0 m) | PASS | — | PASS |
| `docs/developer-handbook/00-getting-started/learning-roadmap.md` | `docs/developer-handbook/00-getting-started/learning-roadmap-ar.md` | PASS | PASS | PASS | FAIL (1 M, 0 m) | PASS | — | PASS |
| `docs/developer-handbook/00-getting-started/project-overview.md` | `docs/developer-handbook/00-getting-started/project-overview-ar.md` | PASS | FAIL (1 M, 0 m) | PASS (0 M, 1 m) | NOT_RUN | PASS | — | PASS |
| `docs/developer-handbook/00-getting-started/repository-navigation.md` | `docs/developer-handbook/00-getting-started/repository-navigation-ar.md` | PASS | PASS | PASS | PASS | PASS | — | PASS |
| `docs/developer-handbook/01-business/actors-and-permissions.md` | `docs/developer-handbook/01-business/actors-and-permissions-ar.md` | PASS | FAIL (1 M, 0 m) | PASS | PASS | PASS | — | PASS |
| `docs/developer-handbook/01-business/business-rules.md` | `docs/developer-handbook/01-business/business-rules-ar.md` | PASS | PASS | FAIL (1 M, 0 m) | PASS | PASS | — | PASS |
| `docs/developer-handbook/01-business/kitchen-management.md` | `docs/developer-handbook/01-business/kitchen-management-ar.md` | PASS | FAIL (1 M, 0 m) | FAIL (2 M, 0 m) | FAIL (1 M, 0 m) | PASS (0 M, 1 m) | PASS | PASS |
| `docs/developer-handbook/01-business/marketplace-model.md` | `docs/developer-handbook/01-business/marketplace-model-ar.md` | PASS | FAIL (1 M, 0 m) | PASS | FAIL (1 M, 0 m) | PASS | — | PASS |
| `docs/developer-handbook/01-business/order-lifecycle.md` | `docs/developer-handbook/01-business/order-lifecycle-ar.md` | PASS | PASS (0 M, 2 m) | PASS | FAIL (1 M, 0 m) | PASS | — | PASS |
| `docs/developer-handbook/01-business/pricing-and-payments.md` | `docs/developer-handbook/01-business/pricing-and-payments-ar.md` | PASS | FAIL (2 M, 0 m) | FAIL (2 M, 0 m) | FAIL (1 M, 0 m) | FAIL (1 M, 0 m) | PASS | PASS |
| `docs/developer-handbook/01-business/product-vision.md` | `docs/developer-handbook/01-business/product-vision-ar.md` | PASS | FAIL (1 M, 0 m) | PASS | FAIL (1 M, 0 m) | PASS | — | PASS |
| `docs/developer-handbook/01-business/target-users.md` | `docs/developer-handbook/01-business/target-users-ar.md` | PASS | FAIL (1 M, 0 m) | PASS | FAIL (1 M, 0 m) | PASS | — | PASS |
| `docs/developer-handbook/02-user-journeys/authentication-and-mfa.md` | `docs/developer-handbook/02-user-journeys/authentication-and-mfa-ar.md` | PASS | PASS | PASS | PASS | PASS | — | PASS |
| `docs/developer-handbook/02-user-journeys/backoffice-admin.md` | `docs/developer-handbook/02-user-journeys/backoffice-admin-ar.md` | PASS | PASS | PASS | PASS | FAIL (1 M, 0 m) | PASS | PASS |
| `docs/developer-handbook/02-user-journeys/complete-order-journey.md` | `docs/developer-handbook/02-user-journeys/complete-order-journey-ar.md` | PASS | PASS (0 M, 1 m) | PASS | PASS | PASS | — | PASS |
| `docs/developer-handbook/02-user-journeys/cook-operations.md` | `docs/developer-handbook/02-user-journeys/cook-operations-ar.md` | PASS | PASS | PASS | FAIL (1 M, 0 m) | PASS | — | PASS |
| `docs/developer-handbook/02-user-journeys/customer-mobile.md` | `docs/developer-handbook/02-user-journeys/customer-mobile-ar.md` | PASS | PASS (0 M, 2 m) | FAIL (1 M, 0 m) | NOT_RUN | PASS | — | PASS |
| `docs/developer-handbook/02-user-journeys/customer-web.md` | `docs/developer-handbook/02-user-journeys/customer-web-ar.md` | PASS | PASS | PASS | FAIL (1 M, 0 m) | PASS | — | PASS |
| `docs/developer-handbook/02-user-journeys/finance-and-support.md` | `docs/developer-handbook/02-user-journeys/finance-and-support-ar.md` | PASS | FAIL (1 M, 1 m) | PASS | FAIL (1 M, 0 m) | PASS | — | PASS |
| `docs/developer-handbook/03-architecture/backend-architecture.md` | `docs/developer-handbook/03-architecture/backend-architecture-ar.md` | PASS | PASS | PASS | PASS | PASS | — | PASS |
| `docs/developer-handbook/03-architecture/database-architecture.md` | `docs/developer-handbook/03-architecture/database-architecture-ar.md` | PASS | PASS | PASS | FAIL (1 M, 0 m) | PASS | — | PASS |
| `docs/developer-handbook/03-architecture/diagrams.md` | `docs/developer-handbook/03-architecture/diagrams-ar.md` | PASS | FAIL (2 M, 0 m) | FAIL (1 M, 1 m) | NOT_RUN | PASS | — | PASS |
| `docs/developer-handbook/03-architecture/frontend-architecture.md` | `docs/developer-handbook/03-architecture/frontend-architecture-ar.md` | PASS | FAIL (1 M, 0 m) | PASS | NOT_RUN | PASS | — | PASS |
| `docs/developer-handbook/03-architecture/identity-and-security.md` | `docs/developer-handbook/03-architecture/identity-and-security-ar.md` | PASS | PASS | FAIL (1 M, 0 m) | FAIL (1 M, 0 m) | PASS | — | PASS |
| `docs/developer-handbook/03-architecture/infrastructure-and-deployment.md` | `docs/developer-handbook/03-architecture/infrastructure-and-deployment-ar.md` | PASS | PASS | PASS | FAIL (1 M, 0 m) | PASS | — | PASS |
| `docs/developer-handbook/03-architecture/integration-and-events.md` | `docs/developer-handbook/03-architecture/integration-and-events-ar.md` | PASS | PASS | PASS | NOT_RUN | PASS | — | PASS |
| `docs/developer-handbook/03-architecture/mobile-architecture.md` | `docs/developer-handbook/03-architecture/mobile-architecture-ar.md` | PASS | PASS | PASS | FAIL (1 M, 0 m) | PASS | — | PASS |
| `docs/developer-handbook/03-architecture/system-overview.md` | `docs/developer-handbook/03-architecture/system-overview-ar.md` | PASS | PASS | PASS | FAIL (1 M, 0 m) | PASS | — | PASS |
| `docs/developer-handbook/04-codebase/api-contracts.md` | `docs/developer-handbook/04-codebase/api-contracts-ar.md` | PASS | PASS | PASS | PASS | PASS | — | PASS |
| `docs/developer-handbook/04-codebase/backend-module-guide.md` | `docs/developer-handbook/04-codebase/backend-module-guide-ar.md` | PASS | FAIL (1 M, 1 m) | PASS | NOT_RUN | PASS | — | PASS |
| `docs/developer-handbook/04-codebase/frontend-code-guide.md` | `docs/developer-handbook/04-codebase/frontend-code-guide-ar.md` | PASS | PASS (0 M, 1 m) | FAIL (1 M, 0 m) | PASS | PASS | — | PASS |
| `docs/developer-handbook/04-codebase/mobile-code-guide.md` | `docs/developer-handbook/04-codebase/mobile-code-guide-ar.md` | PASS | PASS | PASS | PASS | PASS | — | PASS |
| `docs/developer-handbook/04-codebase/order-implementation.md` | `docs/developer-handbook/04-codebase/order-implementation-ar.md` | PASS | PASS | PASS (0 M, 2 m) | NOT_RUN | PASS | — | PASS |
| `docs/developer-handbook/04-codebase/payment-implementation.md` | `docs/developer-handbook/04-codebase/payment-implementation-ar.md` | PASS | PASS | PASS | PASS | PASS | — | PASS |
| `docs/developer-handbook/04-codebase/pricing-implementation.md` | `docs/developer-handbook/04-codebase/pricing-implementation-ar.md` | PASS | PASS | PASS | FAIL (1 M, 0 m) | PASS | — | PASS |
| `docs/developer-handbook/04-codebase/repository-map.md` | `docs/developer-handbook/04-codebase/repository-map-ar.md` | PASS | FAIL (1 M, 0 m) | PASS | PASS | PASS | — | PASS |
| `docs/developer-handbook/04-codebase/shared-libraries.md` | `docs/developer-handbook/04-codebase/shared-libraries-ar.md` | PASS | PASS | FAIL (1 M, 0 m) | PASS | PASS | — | PASS |
| `docs/developer-handbook/05-developer-workflow/branch-maintenance.md` | `docs/developer-handbook/05-developer-workflow/branch-maintenance-ar.md` | PASS | PASS | PASS | PASS | PASS | — | PASS |
| `docs/developer-handbook/05-developer-workflow/ci-cd.md` | `docs/developer-handbook/05-developer-workflow/ci-cd-ar.md` | PASS | PASS | PASS | PASS | PASS | — | PASS |
| `docs/developer-handbook/05-developer-workflow/documentation-maintenance.md` | `docs/developer-handbook/05-developer-workflow/documentation-maintenance-ar.md` | PASS | PASS | PASS | PASS | PASS | — | PASS |
| `docs/developer-handbook/05-developer-workflow/documentation-policy.md` | `docs/developer-handbook/05-developer-workflow/documentation-policy-ar.md` | PASS | PASS | PASS | PASS | PASS | — | PASS |
| `docs/developer-handbook/05-developer-workflow/git-and-pr-workflow.md` | `docs/developer-handbook/05-developer-workflow/git-and-pr-workflow-ar.md` | PASS | PASS | PASS | PASS | PASS | — | PASS |
| `docs/developer-handbook/05-developer-workflow/local-environment.md` | `docs/developer-handbook/05-developer-workflow/local-environment-ar.md` | PASS | PASS | PASS | FAIL (1 M, 0 m) | PASS | — | PASS |
| `docs/developer-handbook/05-developer-workflow/prerequisites.md` | `docs/developer-handbook/05-developer-workflow/prerequisites-ar.md` | PASS | PASS | PASS | PASS | PASS | — | PASS |
| `docs/developer-handbook/05-developer-workflow/preview-environment.md` | `docs/developer-handbook/05-developer-workflow/preview-environment-ar.md` | PASS | PASS | PASS | FAIL (1 M, 0 m) | PASS | — | PASS |
| `docs/developer-handbook/05-developer-workflow/run-and-debug.md` | `docs/developer-handbook/05-developer-workflow/run-and-debug-ar.md` | PASS | FAIL (1 M, 0 m) | PASS | PASS | PASS | — | PASS |
| `docs/developer-handbook/05-developer-workflow/test-execution.md` | `docs/developer-handbook/05-developer-workflow/test-execution-ar.md` | PASS | PASS | PASS | NOT_RUN | PASS | — | PASS |
| `docs/developer-handbook/05-developer-workflow/troubleshooting.md` | `docs/developer-handbook/05-developer-workflow/troubleshooting-ar.md` | PASS | PASS (0 M, 1 m) | PASS | FAIL (1 M, 0 m) | PASS | PASS | PASS |
| `docs/developer-handbook/06-onboarding-labs/day-1-business.md` | `docs/developer-handbook/06-onboarding-labs/day-1-business-ar.md` | PASS | PASS | PASS | FAIL (1 M, 0 m) | PASS | — | PASS |
| `docs/developer-handbook/06-onboarding-labs/day-2-applications.md` | `docs/developer-handbook/06-onboarding-labs/day-2-applications-ar.md` | PASS | PASS | PASS | FAIL (1 M, 0 m) | FAIL (1 M, 0 m) | PASS | PASS |
| `docs/developer-handbook/06-onboarding-labs/day-3-backend-security.md` | `docs/developer-handbook/06-onboarding-labs/day-3-backend-security-ar.md` | PASS | PASS | PASS (0 M, 1 m) | FAIL (1 M, 0 m) | PASS | — | PASS |
| `docs/developer-handbook/06-onboarding-labs/day-4-development.md` | `docs/developer-handbook/06-onboarding-labs/day-4-development-ar.md` | PASS | PASS (0 M, 1 m) | PASS | PASS | PASS | — | PASS |
| `docs/developer-handbook/06-onboarding-labs/day-5-first-contribution.md` | `docs/developer-handbook/06-onboarding-labs/day-5-first-contribution-ar.md` | PASS | PASS | PASS | PASS | PASS | — | PASS |
| `docs/developer-handbook/06-onboarding-labs/final-assessment.md` | `docs/developer-handbook/06-onboarding-labs/final-assessment-ar.md` | PASS | PASS (0 M, 1 m) | FAIL (1 M, 0 m) | PASS | PASS | — | PASS |
| `docs/developer-handbook/07-reference/architecture-decisions.md` | `docs/developer-handbook/07-reference/architecture-decisions-ar.md` | PASS | FAIL (1 M, 0 m) | PASS | PASS | PASS | — | PASS |
| `docs/developer-handbook/07-reference/branch-cleanup-audit-2026-10-20.md` | `docs/developer-handbook/07-reference/branch-cleanup-audit-2026-10-20-ar.md` | PASS | PASS | PASS | PASS | PASS | — | PASS |
| `docs/developer-handbook/07-reference/documentation-map.md` | `docs/developer-handbook/07-reference/documentation-map-ar.md` | PASS | PASS (0 M, 1 m) | PASS | PASS | PASS | — | PASS |
| `docs/developer-handbook/07-reference/frequently-asked-questions.md` | `docs/developer-handbook/07-reference/frequently-asked-questions-ar.md` | PASS | PASS | PASS | FAIL (1 M, 0 m) | PASS | — | PASS |
| `docs/developer-handbook/07-reference/fresh-developer-simulation.md` | `docs/developer-handbook/07-reference/fresh-developer-simulation-ar.md` | PASS | FAIL (1 M, 0 m) | PASS | PASS | PASS | — | PASS |
| `docs/developer-handbook/07-reference/implementation-status.md` | `docs/developer-handbook/07-reference/implementation-status-ar.md` | PASS | FAIL (2 M, 1 m) | FAIL (3 M, 0 m) | FAIL (3 M, 0 m) | PASS | — | PASS |
| `docs/developer-handbook/07-reference/independent-reviews.md` | `docs/developer-handbook/07-reference/independent-reviews-ar.md` | PASS | FAIL (1 M, 0 m) | FAIL (1 M, 0 m) | PASS | PASS | — | PASS |
| `docs/developer-handbook/07-reference/inventory-and-gaps.md` | `docs/developer-handbook/07-reference/inventory-and-gaps-ar.md` | PASS | PASS | PASS | PASS | PASS | — | PASS |
| `docs/developer-handbook/07-reference/requirements-traceability.md` | `docs/developer-handbook/07-reference/requirements-traceability-ar.md` | PASS | FAIL (3 M, 0 m) | FAIL (1 M, 1 m) | FAIL (2 M, 0 m) | PASS | — | PASS |
| `docs/developer-handbook/README.md` | `docs/developer-handbook/README-ar.md` | PASS | PASS | PASS | PASS | PASS | — | PASS |

## 5. Defect classes found and fixed

| Class | Examples fixed |
|---|---|
| Wrong sense of a polysemous English word | revision/review/rating (cook-application and meal revisions, customer ratings, staff review), dismissal vs rejection, typed vs typo, stale vs expired quote, commit vs commitment, audit vs review, caller, candidate builds, hashed vs split, replay vs restart, seeded vs cultivated |
| Defined term rendered inconsistently | "synthetic" (artificial data, users, money, policies, zones, fixtures) rendered as "experimental"; now اصطناعي / وهمي with the English word at first use |
| Reversed or altered direction | `->` rendered as a left arrow in 9 pages (transition order reversed); who reports, approves or refunds a deposit; effect of an action vs effect on active orders |
| Dropped or weakened qualifiers and identifiers | `(API-108)` missing on two transitions; "balanced" missing from the ledger row; "10+ characters" turned into letters; "takes no address" turned into "does not require an address"; "authenticated" turned into "registered" |
| Lexical slips | typos in two phrases, a doubled prefix, a wrong negation suffix |

## 6. Exceptions

| Exception | Reason | State |
|---|---|---|
| `05-developer-workflow/documentation-policy.md`: no language-switch line on the English page | It is a DOC-001 governance file; editing it needs the governance approval described in the page. The Arabic page links to the English source and `README-ar.md` lists it. | Proposed in this PR; owner review pending |
| `translation-parity-report.md` is not mirrored | Generated audit artifact written for the owner and reviewers; both READMEs link to it | Documented in [translation-exceptions.json](translation-exceptions.json) |

No parity check is waived for any page. Mermaid labels were translated except identifiers (state names, class and method names, endpoints, product names).

## 7. Suspected problems in the English source (not corrected)

Translators and reviewers were told never to fix the English silently. These observations are preserved for owner review; none was changed.

- `payment-implementation.md`: heading says "Three separate things" but the table has five rows.
- `inventory-and-gaps.md`: a blank line splits the section 2 table (rows 19 to 24 render without a header); row 6 says 199 cases (config-security 53) while other places say 200.
- `implementation-status.md`: conflict 6 cites TC-056 as evidence for TC-057 and TC-139 being `NOT_RUN`; the "Quotes" row hard-codes a 5-minute TTL while another page says the value is `commerce_policy.quote_ttl_seconds`.
- `order-lifecycle.md` step 4 and `pricing-and-payments.md` give the tax formula with the parenthesis placed differently.
- `ci-cd.md`, limitations: the sentence about the `build` job running on every push to `develop` reads as inconsistent with the table.
- `customer-mobile.md`: the CFG-112 row says no Android plan exists while CM-5 lists CFG-112 as the Android owner case.
- Hard-coded line numbers (`repository-navigation.md`, `backend-module-guide.md`, `requirements-traceability.md`, one diagram caption) will drift as the code changes.
- Claims about code the translators did not re-verify (for example `RATE_LIMITED` applied in four places, `expires_at` not enforced, `check_migration_compat.py --list`, `documentation` counts) remain as written in English; the English handbook, not the translation, is the place to correct them.

## 8. Not done and remaining limits

- **No human review.** Neither the Arabic wording nor the semantic comparison was reviewed by a human; the reviewers were AI sessions (Codex) and the translators were AI sessions. A native Egyptian-Arabic engineer should read the pages before they are treated as final.
- **Structural validation is not semantic proof.** A passing validator never shows that a sentence means the same in both languages.
- **Mermaid rendering NOT_RUN.** No Mermaid renderer was available; diagrams were checked structurally (graph skeleton identical to English, Arabic labels quoted) but not rendered.
- **Right-to-left rendering NOT_RUN** in GitHub's web view; Markdown structure and code blocks are preserved, which is what the validator can check.
- **External links:** all URLs in the handbook are local (`localhost`, `127.0.0.1`) or placeholders (`<PREVIEW_DOMAIN>`); none can be checked for reachability. They are identical in both languages.
- Round 3 had one reviewer session fail for capacity; its pages were reviewed in round 4 instead.
- The CI job `Handbook Arabic parity (structural)` is **not** in the required checks of the `develop` ruleset (the ruleset was not changed). It runs on pull requests that touch the handbook; making it required is an owner decision.
- Future edits: the check `--base` rule and `translation-sync.json` force English and Arabic to change together; they cannot judge the quality of the new Arabic.
