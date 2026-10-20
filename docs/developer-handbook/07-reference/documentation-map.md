# Documentation map and canonical sources

> **Owner role:** Handbook navigation
> **Status:** REFERENCE — which document is authoritative for what, and how the handbook relates to the older documents

The handbook explains and links; it does not replace the authoritative documents below. When two documents disagree, the order of trust is: **running code, migrations, contracts and tests** (implementation truth) → the owner decision record (`planning/decisions.json`, `docs/product/01-context-and-decisions.md`, `docs/delivery/07-owner-decisions-implementation-sequence.md`) for intent → the topic documents → status reports (historical snapshots).

| Topic | Authoritative source | Handbook page that explains it |
|---|---|---|
| Owner decisions D01.. | `planning/decisions.json`, `docs/product/01-context-and-decisions.md`, `docs/delivery/07-owner-decisions-implementation-sequence.md` | [architecture-decisions](architecture-decisions.md) |
| Business rules BR-nnn | `planning/rules.json`, `docs/product/03-business-rules.md` | [business-rules](../01-business/business-rules.md) |
| State machines | `docs/product/04-state-machines.md` (intent), `backend/modules/orders` (implementation) | [order-lifecycle](../01-business/order-lifecycle.md) |
| Roles and permissions | `backend/modules/identity` (`Permission.java`), `docs/product/06-permissions.md` (intent) | [actors-and-permissions](../01-business/actors-and-permissions.md) |
| Deposits | `docs/product/09-deposit-payment-states.md`, `docs/strategy/mvp-direct-deposit-payment.md` | [pricing-and-payments](../01-business/pricing-and-payments.md), [payment-implementation](../04-codebase/payment-implementation.md) |
| Stale quote / price change | `docs/product/rq-004-stale-quote.md` | [pricing-implementation](../04-codebase/pricing-implementation.md) |
| API contract | `contracts/openapi.yaml`, `contracts/endpoints.json` | [api-contracts](../04-codebase/api-contracts.md) |
| Data model | `backend/app/src/main/resources/db/migration`, `docs/engineering/02-data-model.md` | [database-architecture](../03-architecture/database-architecture.md) |
| Security and MFA | `docs/security/mfa-policy.md`, `docs/engineering/04-security.md`, `docs/security/csp-rollout.md` | [identity-and-security](../03-architecture/identity-and-security.md) |
| Keycloak admin console | `docs/operations/14-keycloak-admin-console.md` | [authentication-and-mfa](../02-user-journeys/authentication-and-mfa.md) |
| Preview operations | `docs/operations/06-web-preview.md`, `docs/operations/11-staging-deployment-and-rollback.md`, `deploy/web-preview/` | [preview-environment](../05-developer-workflow/preview-environment.md) |
| Owner acceptance testing | `docs/quality/owner-acceptance/` | [test-execution](../05-developer-workflow/test-execution.md), [requirements-traceability](requirements-traceability.md) |
| Verification strategy and gates | `docs/quality/01-verification.md`, `docs/quality/02-release-gates.md`, `planning/gates.json` | [test-execution](../05-developer-workflow/test-execution.md) |
| Toolchain versions | `docs/engineering/toolchain.md`, `package.json`, `backend/pom.xml` | [prerequisites](../05-developer-workflow/prerequisites.md) |
| Design system | `docs/design/approved/DESIGN.md` | [frontend-architecture](../03-architecture/frontend-architecture.md) |
| Delivery protocol, progress reports | `docs/delivery/`, `docs/status/` | [git-and-pr-workflow](../05-developer-workflow/git-and-pr-workflow.md) |
| Documentation policy DOC-001 | [documentation-policy](../05-developer-workflow/documentation-policy.md) | — |

## Historical or point-in-time documents (do not treat as current)

`docs/status/latest.md` and `docs/status/daily/*` (reports), `docs/quality/0*-qa-*.md` (QA candidate hand-offs), `docs/quality/stabilization/` (consolidation evidence), `verification/runs/` (evidence of specific runs). They describe what was true at a commit; use [implementation-status](implementation-status.md) for the present picture.

## Known duplication

Business logic is described in `docs/product/*` (intent), `planning/rules.json` (machine-readable) and the code. The handbook intentionally links instead of copying; see [inventory-and-gaps](inventory-and-gaps.md) for the contradictions found between them.
