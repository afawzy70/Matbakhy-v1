# Learning roadmap

> **Owner role:** Handbook navigation
> **Status:** REFERENCE — the five-day path for a new developer, with links to each day's lab.
> **Language / اللغة:** English · [العربية](learning-roadmap-ar.md)

The goal after five days: you can explain the product, run and debug the system, find where a business rule is implemented, write and run tests, fix a defect and submit a pull request that follows DOC-001, without an AI assistant. Progress is judged by the [final assessment](../06-onboarding-labs/final-assessment.md).

## Map

| Day | Theme | Lab | You finish with |
|---|---|---|---|
| 1 | Business and domain | [day-1-business.md](../06-onboarding-labs/day-1-business.md) | a written one-page explanation of the customer-to-cook order flow and money rules |
| 2 | Applications and user journeys | [day-2-applications.md](../06-onboarding-labs/day-2-applications.md) | the stack running locally and a recorded walk through customer web, operations web and mobile surfaces |
| 3 | Backend, database and security | [day-3-backend-security.md](../06-onboarding-labs/day-3-backend-security.md) | a traced request through controller, service, database and authorization |
| 4 | Development workflow | [day-4-development.md](../06-onboarding-labs/day-4-development.md) | a passing test you wrote, a CI-equivalent local run, a reproduced and fixed practice defect |
| 5 | First contribution | [day-5-first-contribution.md](../06-onboarding-labs/day-5-first-contribution.md) | a small real PR with tests and handbook update |

## Reading order

1. [Handbook README](../README.md), then [project overview](project-overview.md), [repository navigation](repository-navigation.md) and the [glossary](glossary.md).
2. Business: [product vision](../01-business/product-vision.md), [target users](../01-business/target-users.md), [marketplace model](../01-business/marketplace-model.md), [actors and permissions](../01-business/actors-and-permissions.md), [order lifecycle](../01-business/order-lifecycle.md), [pricing and payments](../01-business/pricing-and-payments.md), [kitchen management](../01-business/kitchen-management.md), [business rules](../01-business/business-rules.md).
3. Journeys: [complete order journey](../02-user-journeys/complete-order-journey.md) first, then the per-surface pages.
4. Architecture: [system overview](../03-architecture/system-overview.md), [backend](../03-architecture/backend-architecture.md), [database](../03-architecture/database-architecture.md), [identity and security](../03-architecture/identity-and-security.md), then frontend/mobile pages.
5. Codebase: [repository map](../04-codebase/repository-map.md), [backend module guide](../04-codebase/backend-module-guide.md), [API contracts](../04-codebase/api-contracts.md), [order implementation](../04-codebase/order-implementation.md).
6. Workflow: [prerequisites](../05-developer-workflow/prerequisites.md), [local environment](../05-developer-workflow/local-environment.md), [run and debug](../05-developer-workflow/run-and-debug.md), [test execution](../05-developer-workflow/test-execution.md), [git and PR workflow](../05-developer-workflow/git-and-pr-workflow.md), [CI/CD](../05-developer-workflow/ci-cd.md), [preview environment](../05-developer-workflow/preview-environment.md), [troubleshooting](../05-developer-workflow/troubleshooting.md), [documentation policy](../05-developer-workflow/documentation-policy.md).
7. Reference when needed: [implementation status](../07-reference/implementation-status.md), [requirements traceability](../07-reference/requirements-traceability.md), [FAQ](../07-reference/frequently-asked-questions.md).

## Ground rules while learning

- Code is the source of truth for what exists; older prose may lag. Where the handbook says IMPLEMENTED, PLANNED, DISABLED or NOT VERIFIED, believe the label and re-check against the code before relying on it.
- Use synthetic data only. Never write credentials or real personal data into notes, issues or screenshots.
- Product invariants you must be able to recite: Egypt / EGP / Africa/Cairo; money as integer piastres; the server owns price, capacity, roles and state transitions; one cook and one slot per order; immutable order snapshots; idempotent order placement; object-level authorization; zero Matbakhy commission and no customer platform fee in the MVP; cash on receipt or a manual direct deposit reported by the kitchen (never "bank verified"); Arabic RTL and English LTR.
- If the handbook is wrong or missing something, that is a defect to fix under DOC-001, not a reason to guess.

## Getting unstuck

1. Search the handbook, then the canonical docs it links.
2. Search the code (`grep -rn`, IDE) and the tests: a test named after the behaviour is the best specification.
3. Check [troubleshooting](../05-developer-workflow/troubleshooting.md) and the [FAQ](../07-reference/frequently-asked-questions.md).
4. Ask a teammate with the exact command, output and what you already tried; record documentation gaps as issues.
