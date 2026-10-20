# Matbakhy Developer Handbook

> **Owner role:** Handbook navigation
> **Status:** REFERENCE — single entry point for developers; implementation state is labelled per page

Welcome. This handbook lets a new developer understand Matbakhy (مطبخي) — the business, the applications, the architecture, the code, the operations and the contribution workflow — **from the repository alone**, without an AI assistant or undocumented knowledge. Arabic orientation: [README-ar.md](README-ar.md).

**Rules of this handbook**

- The code, migrations, contracts and tests are the source of implementation truth; the handbook explains and links to them. Every page says who owns it and its implementation status (IMPLEMENTED, PARTIALLY IMPLEMENTED, PLANNED, DISABLED, DEPRECATED, NOT VERIFIED; REFERENCE for process pages).
- It is a living deliverable under **[DOC-001](05-developer-workflow/documentation-policy.md)**: every change updates the affected pages in the same PR, and the required check `Documentation Consistency / DOC-001` validates structure and references. Found a mistake? Fix the canonical page and record it ([maintenance](05-developer-workflow/documentation-maintenance.md)).
- The repository is public: never add credentials, customer data or private logs.

## Start here (first day)

1. [Project overview](00-getting-started/project-overview.md) → [glossary](00-getting-started/glossary.md) → [learning roadmap](00-getting-started/learning-roadmap.md).
2. Follow the [five-day onboarding](#onboarding-labs) and finish with the [final assessment](06-onboarding-labs/final-assessment.md).
3. Before your first PR read the [DOC-001 policy](05-developer-workflow/documentation-policy.md) and the [Git and PR workflow](05-developer-workflow/git-and-pr-workflow.md).

## Contents

### 00 Getting started
[Project overview](00-getting-started/project-overview.md) · [Learning roadmap](00-getting-started/learning-roadmap.md) · [Repository navigation](00-getting-started/repository-navigation.md) · [Glossary](00-getting-started/glossary.md)

### 01 Business
[Product vision](01-business/product-vision.md) · [Target users](01-business/target-users.md) · [Marketplace model](01-business/marketplace-model.md) · [Actors and permissions](01-business/actors-and-permissions.md) · [Order lifecycle](01-business/order-lifecycle.md) · [Pricing and payments](01-business/pricing-and-payments.md) · [Kitchen management](01-business/kitchen-management.md) · [Business rules](01-business/business-rules.md)

### 02 User journeys
[Customer web](02-user-journeys/customer-web.md) · [Customer mobile](02-user-journeys/customer-mobile.md) · [Cook operations](02-user-journeys/cook-operations.md) · [Back-office](02-user-journeys/backoffice-admin.md) · [Finance and support](02-user-journeys/finance-and-support.md) · [Authentication and MFA](02-user-journeys/authentication-and-mfa.md) · [Complete order journey](02-user-journeys/complete-order-journey.md)

### 03 Architecture
[System overview](03-architecture/system-overview.md) · [Backend](03-architecture/backend-architecture.md) · [Frontend](03-architecture/frontend-architecture.md) · [Mobile](03-architecture/mobile-architecture.md) · [Database](03-architecture/database-architecture.md) · [Identity and security](03-architecture/identity-and-security.md) · [Integration and events](03-architecture/integration-and-events.md) · [Infrastructure and deployment](03-architecture/infrastructure-and-deployment.md) · [**Diagrams (Mermaid)**](03-architecture/diagrams.md)

### 04 Codebase
[Repository map](04-codebase/repository-map.md) · [Backend module guide](04-codebase/backend-module-guide.md) · [Frontend code guide](04-codebase/frontend-code-guide.md) · [Mobile code guide](04-codebase/mobile-code-guide.md) · [API contracts](04-codebase/api-contracts.md) · [Order implementation](04-codebase/order-implementation.md) · [Pricing implementation](04-codebase/pricing-implementation.md) · [Payment implementation](04-codebase/payment-implementation.md) · [Shared libraries](04-codebase/shared-libraries.md)

### 05 Developer workflow
[Prerequisites](05-developer-workflow/prerequisites.md) · [Local environment](05-developer-workflow/local-environment.md) · [Run and debug](05-developer-workflow/run-and-debug.md) · [Test execution](05-developer-workflow/test-execution.md) · [Git and PR workflow](05-developer-workflow/git-and-pr-workflow.md) · [CI/CD](05-developer-workflow/ci-cd.md) · [Preview environment](05-developer-workflow/preview-environment.md) · [Troubleshooting](05-developer-workflow/troubleshooting.md) · [**DOC-001 documentation policy**](05-developer-workflow/documentation-policy.md) · [DOC-001 trusted enforcement](05-developer-workflow/doc001-trusted-enforcement.md) · [Branch maintenance](05-developer-workflow/branch-maintenance.md) · [Documentation maintenance](05-developer-workflow/documentation-maintenance.md)

### Onboarding labs
[Day 1 business](06-onboarding-labs/day-1-business.md) · [Day 2 applications](06-onboarding-labs/day-2-applications.md) · [Day 3 backend, database, security](06-onboarding-labs/day-3-backend-security.md) · [Day 4 development workflow](06-onboarding-labs/day-4-development.md) · [Day 5 first contribution](06-onboarding-labs/day-5-first-contribution.md) · [Final assessment](06-onboarding-labs/final-assessment.md)

### 07 Reference
[Architecture decisions](07-reference/architecture-decisions.md) · [Implementation status](07-reference/implementation-status.md) · [Requirements traceability](07-reference/requirements-traceability.md) · [Documentation map](07-reference/documentation-map.md) · [FAQ](07-reference/frequently-asked-questions.md) · [Inventory and gap assessment](07-reference/inventory-and-gaps.md) · [Fresh-developer simulation](07-reference/fresh-developer-simulation.md) · [Independent review record](07-reference/independent-reviews.md) · [Branch cleanup audit](07-reference/branch-cleanup-audit-2026-10-20.md)

Machine-readable: [doc001-matrix.json](doc001-matrix.json) (which code areas require which documentation).

## Quick answers

| Question | Go to |
|---|---|
| What is the product and who uses it? | [Project overview](00-getting-started/project-overview.md), [actors](01-business/actors-and-permissions.md) |
| Where is X in the code? | [Repository navigation](00-getting-started/repository-navigation.md) |
| How does an order flow end to end? | [Complete order journey](02-user-journeys/complete-order-journey.md), [order lifecycle](01-business/order-lifecycle.md) |
| How do I run and test it? | [Prerequisites](05-developer-workflow/prerequisites.md), [run and debug](05-developer-workflow/run-and-debug.md), [tests](05-developer-workflow/test-execution.md) |
| How does a PR reach the preview? | [CI/CD](05-developer-workflow/ci-cd.md), [preview](05-developer-workflow/preview-environment.md) |
| Which owner E2E cases cover my feature? | [Requirements traceability](07-reference/requirements-traceability.md), `docs/quality/owner-acceptance/README.md` |
| What must my PR say about documentation? | [DOC-001 policy](05-developer-workflow/documentation-policy.md) |
