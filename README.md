# Matbakhy / مطبخي — MVP implementation blueprint

An Egypt-focused marketplace for discovering and ordering homemade meals from approved home cooks.

**Current state: implementation in progress on branch `implementation/mvp`.** Minimal backend, web and Expo shells exist; no product feature, deployed environment, payment integration or native mobile build is claimed. See [latest status](docs/status/latest.md) and [toolchain](docs/engineering/toolchain.md).

## Start here

1. Read [confirmed requirements and decisions](docs/product/01-context-and-decisions.md).
2. Read the [product scope](docs/product/02-product-specification.md), [business rules](docs/product/03-business-rules.md), and [order lifecycle](docs/product/04-state-machines.md).
3. Follow the [delivery roadmap](docs/delivery/01-roadmap.md) and machine-readable [backlog](planning/backlog.json).
4. Give Claude Code the complete [start prompt](docs/delivery/03-claude-start-prompt.md). Later use the [resume/recovery prompts](docs/delivery/04-resume-and-recovery.md).
5. Run `python3 scripts/validate_blueprint.py` to validate this planning pack. This does **not** verify the product.

Pack contents: 46 implementation stories, 64 business rules, 110 planned verification scenarios, 97 planned API operations, and 11 delivery phases. See the [planning-pack validation report](docs/quality/03-blueprint-validation.md).

## Planned product surfaces

| Surface | Responsibility |
|---|---|
| Customer web | Discovery, cook/meal details, basket, checkout, orders, reviews, account/support |
| Customer mobile | Native Android and iOS customer journeys with the same backend rules |
| Cook workspace | Onboarding, meals, dated availability, orders, fulfillment, earnings/statements |
| Platform backoffice | Cook approval, moderation, order exceptions, support, cash reconciliation, policy/configuration, audit |
| Shared backend | Identity integration, access control, catalogue, capacity, quotes, orders, ledger, notifications, operations |

Cook workspace and platform backoffice are role-restricted areas of one operations web application. Dedicated cook/driver native apps are outside the proposed MVP.

## Working architecture

Java 21 + Spring Boot modular monolith, PostgreSQL, React/TypeScript customer and operations web apps, React Native/Expo customer mobile app, OpenAPI contracts, container-based local development. These are proposed implementation defaults except the previously requested Spring Boot backend and Android/iOS targets. Exact supported dependency versions must be selected and pinned in Phase P01.

The proposed initial commercial path is one cook and fulfillment slot per order, cash at delivery/pickup, cook-arranged delivery or pickup, and manual audited reconciliation. Provider-based online payments, courier integration, and social-feed functionality are specified as conditional extensions, not silently assumed to be approved.

## Documentation map

| Area | Files |
|---|---|
| Product | [Context/decisions](docs/product/01-context-and-decisions.md), [scope/personas](docs/product/02-product-specification.md), [rules](docs/product/03-business-rules.md), [states](docs/product/04-state-machines.md), [screens](docs/product/05-screen-specification.md), [roles](docs/product/06-permissions.md), [branding/content](docs/product/07-brand-and-content.md), [traceability matrix](docs/product/08-traceability.md) |
| Engineering | [Architecture](docs/engineering/01-architecture.md), [data](docs/engineering/02-data-model.md), [API](docs/engineering/03-api-contract.md), [security](docs/engineering/04-security.md), [mobile](docs/engineering/05-mobile.md), [engineering standards](docs/engineering/06-standards.md), [integration extensions](docs/engineering/07-integrations.md) |
| Delivery | [Roadmap](docs/delivery/01-roadmap.md), [autonomous protocol](docs/delivery/02-execution-protocol.md), [start](docs/delivery/03-claude-start-prompt.md), [resume/recovery](docs/delivery/04-resume-and-recovery.md) |
| Quality | [Verification strategy](docs/quality/01-verification.md), [scenario catalogue](planning/scenarios.json), [release gates](docs/quality/02-release-gates.md), [gate definitions](planning/gates.json) |
| Operations | [Local/staging/production](docs/operations/01-environments.md), [runbooks](docs/operations/02-runbooks.md), [operating model](docs/operations/03-business-operations.md) |
| Execution records | [Stories](planning/backlog.json), [decisions](planning/decisions.json), [current checkpoint](planning/execution-state.json), [evidence template](verification/templates/evidence.json), [review template](verification/templates/review.md) |
| Sources | [Provenance and technical references](docs/reference/sources.md) |

## Important distinction

`CONFIRMED` means supported by an owner request. `PROPOSED_DEFAULT` means an explicit recommendation used to make implementation concrete. `OPEN` means the owner or an external dependency must resolve it before the stated gate. Nothing in this repository turns an unanswered question into owner approval.

Routine implementation, tests, repair, documentation and local verification should continue within the execution instructions. Real-money integrations, public release, store submission and unresolved launch policies require the relevant recorded decision. Missing launch information must not prevent unrelated engineering work.

Repository baseline inspected: `82968ee4fe10192a23e5912bfb6ecc05e33eb757` on 2026-10-11; only the initial README existed.
