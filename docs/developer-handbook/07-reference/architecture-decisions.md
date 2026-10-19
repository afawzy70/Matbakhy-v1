# Architecture and product decisions index

> **Owner role:** Handbook navigation
> **Status:** REFERENCE — index of where decisions are recorded, their status and the supersession links that can be found in the repository

There is no `adr/` folder. Decisions live in four places, with different roles:

| Source | Role | Machine-checked? |
|---|---|---|
| `planning/decisions.json` | Machine-readable register: `D01`..`D18` (decided/proposed) and `O01`..`O10` (open) with status, value, owner, gate and approval reference | yes — `scripts/check_scope.py` (`check_decisions`) compares it with the register document |
| `docs/product/01-context-and-decisions.md` | Human register: confirmed requirements C01..C07, the same D/O ids with rationale, the dated "Owner decision/direction" sections | yes, same script (ids must match in both directions) |
| `docs/engineering/01-architecture.md` | `ADR-001`..`ADR-005` (module boundaries, clients and contracts, authentication, external services, dependency lock) | no |
| `docs/delivery/07-owner-decisions-implementation-sequence.md`, `docs/strategy/*.md`, `docs/product/rq-004-stale-quote.md`, `docs/product/q1-quantity-limit-decision.md`, `docs/security/mfa-policy.md` | Topic documents that carry the detail of specific decisions | no |

Rule from `CLAUDE.md`: the latest explicit owner direction governs; record it and reconcile the affected documentation; previously confirmed requirements outrank proposals; test results cannot authorize a scope change.

## Decision register (`planning/decisions.json`)

Status values allowed by `scripts/check_scope.py`: `PROPOSED_DEFAULT`, `OPEN`, `ACCEPTED`, `REJECTED`, `SUPERSEDED` (an `ACCEPTED` decision must carry an approval reference; an `OPEN` one must have no value and a gate).

| Id | Topic | Status in JSON | Implemented in code? | Detail |
|---|---|---|---|---|
| D01 | Spring Boot modular monolith, PostgreSQL | PROPOSED_DEFAULT | yes — see [backend module guide](../04-codebase/backend-module-guide.md) | ADR-001 |
| D02 | React + TypeScript, separate customer and operations web apps | PROPOSED_DEFAULT | yes — [frontend code guide](../04-codebase/frontend-code-guide.md) | ADR-002 |
| D03 | React Native + Expo for Android/iOS | PROPOSED_DEFAULT | yes — [mobile code guide](../04-codebase/mobile-code-guide.md) | ADR-002 |
| D04 | One cook and one dated slot per order | PROPOSED_DEFAULT | yes — `QuoteService.terms` | BR-018 |
| D05 | Payments: cash, manual reconciliation | PROPOSED_DEFAULT; register document says "Owner-updated 2026-10-13" to include deposits | partly superseded by D16 (see below) | `docs/strategy/mvp-direct-deposit-payment.md` |
| D06 | Cook-arranged delivery or pickup | PROPOSED_DEFAULT | yes | |
| D07 | Favorites and share links now; feed/follows/comments deferred | PROPOSED_DEFAULT | favorites yes; feed/follows/comments not implemented (O10 open) | |
| D08 | OIDC with local Keycloak; verified phone at checkout | PROPOSED_DEFAULT | yes | ADR-003; staff-MFA part reshaped by D18 |
| D09 | Arabic/English, EGP, Africa/Cairo | PROPOSED_DEFAULT | yes | |
| D10 | 0% commission, no customer platform fee, no paid ads at launch; commission stays as inactive technical capability | ACCEPTED | yes — V23 CHECK, `ZeroCommissionIntegrationTest` | owner commits of 2026-10-13 |
| D11 | Portion-size filtering deferred from MVP | ACCEPTED | yes (controls hidden) | |
| D12 | Meal-card description optional | ACCEPTED | yes | |
| D13 | Price changes before placement: refuse stale quote, explicit reconfirmation | ACCEPTED | yes — [pricing implementation](../04-codebase/pricing-implementation.md) | `docs/product/rq-004-stale-quote.md` |
| D14 | Kitchen price history | ACCEPTED | yes — `MealService.priceHistory` | |
| D15 | Daily execution window | ACCEPTED | process rule, not code | `docs/delivery/06-daily-execution.md` |
| D16 | MVP payments: eligible COD + manual direct deposit reported by the kitchen; never "bank verified"; live money gated | ACCEPTED | yes (synthetic/preview only) — [payment implementation](../04-codebase/payment-implementation.md) | `docs/strategy/mvp-direct-deposit-payment.md` |
| D17 | Free core; privacy-safe organic activity and kitchen performance instrumentation; no paid campaign functionality | ACCEPTED | yes — `ActivityController`, `InsightsController` | `docs/product/10-organic-activity-and-cook-insights.md` |
| D18 | Configurable MFA performed by Keycloak; policy DISABLED / OPTIONAL / REQUIRED per account category | ACCEPTED | yes — `MfaPolicyService`, V31 | `docs/security/mfa-policy.md` |
| O01..O10 | Open owner decisions: launch city/zones, cash vs prepayment at launch, delivery responsibility, refund/tax/invoice policy, brand clearance, food-business and privacy policy, providers/hosting, Apple/Google accounts, support contacts, social features | OPEN | n/a — they gate live operation, not code | `docs/product/01-context-and-decisions.md` "Open decisions" |

Open decisions that most often surprise a developer: O04 (tax and refund policy — synthetic values in code), O06 (retention — a 14-day erasure grace is a PROPOSED default in `application.yaml`), O07 (providers — fake adapters are refused outside LOCAL/CI, push disabled).

## ADRs in `docs/engineering/01-architecture.md`

| ADR | Decision | Code check |
|---|---|---|
| ADR-001 | Module boundaries; domain modules talk through `api` packages; PostgreSQL transactions for ordering invariants; Flyway owns schema; real PostgreSQL in tests | `ModuleBoundaryTest`; Testcontainers `PostgresTestConfiguration` |
| ADR-002 | Three apps, one contract; server owns business decisions; no WebView-wrapped web as "native" | `contracts/`, `packages/api-client`, `apps/customer-mobile` |
| ADR-003 | Two security chains: stateless bearer JWT for native, OIDC session + CSRF for web; roles from server-side state only; Staff MFA via provider assurance claims | `SecurityConfiguration`, `StaffAssurance` |
| ADR-004 | External services behind ports; fakes only in dev/test; production start fails if a required provider is fake | `EnvironmentGuard`, `ProviderConfigurationTest` |
| ADR-005 | Pin versions; no `latest` tags | `pom.xml`, `package.json` pinned versions |

## Supersession and replacement links that can be verified

| Older | Newer | Evidence |
|---|---|---|
| Kuwait geography, KNET, Kuwait vendors | Egypt / EGP | `docs/product/01-context-and-decisions.md` ("Egypt supersedes that geography") |
| Flutter/FlutterFlow, React/Material UI or Tailwind options; mock-data social prototype | D02, D03, D07 | same file, "Superseded ideas remain as listed above" |
| Cash-only payment (D05 baseline) | D16 manual direct deposit alongside COD | register row D05 and "Owner decision — 2026-10-13: familiar payment rails in MVP" ("replaces cash-only as the exclusive checkout path") |
| Commission-based model in earlier planning (configurable rate, ledger commission examples) | D10 zero commission (dormant capability retained) | `docs/delivery/07-owner-decisions-implementation-sequence.md` Stage 0; V23 |
| "Staff MFA universally mandatory" | D18 policy-driven MFA | first lines of `docs/security/mfa-policy.md` ("Supersedes the earlier decision..."); BR-007 text in `planning/rules.json` already follows D18 |
| Open question RQ-004 (stale quotes) | D13 | `docs/product/rq-004-stale-quote.md` ("replaces the open question") |
| Older green-accent / IBM Plex prototype direction | Terracotta / Cairo homepage | `CLAUDE.md` invariants ("supersedes"), `docs/design/approved/DESIGN.md` |
| Proposal to split native compilation out of MB-005 into a new P01 story | withdrawn (MB-005 closed under its original acceptance) | `docs/product/01-context-and-decisions.md`, section "Proposed planning change" |

`planning/decisions.json` uses the status `SUPERSEDED` nowhere; supersession is recorded only in prose. See the gaps below.

## How to record a new decision

1. Obtain the owner's explicit direction (conversation, commit, issue). A test result or an agent's preference is not a decision.
2. Add the entry to `planning/decisions.json`: next free `D<nn>` id, `topic`, `status` (`ACCEPTED` with `approval_reference`, or `PROPOSED_DEFAULT`, or `OPEN` with `required_before`), `value`, `owner`. Use `SUPERSEDED` and name the replacement when you replace one; update the old row's value text.
3. Add the same id to the table in `docs/product/01-context-and-decisions.md` and, for bigger decisions, a dated section "Owner decision — <date>" plus a topic document under `docs/strategy/` or `docs/product/`.
4. Reconcile affected rules (`planning/rules.json`, `docs/product/03-business-rules.md`), scenarios (`planning/scenarios.json`), backlog stories (`planning/backlog.json`), contracts and code, and add a regression scenario.
5. Run `python3 scripts/check_scope.py` and `python3 scripts/validate_blueprint.py`, then `python3 scripts/doc001_check.py docs`.
6. Update this page, [implementation status](implementation-status.md) and any business or architecture page that states the old rule (policy: [documentation policy](../05-developer-workflow/documentation-policy.md)).

## Known limitations and documentation gaps

- `planning/decisions.json` row D05 still reads "Cash at delivery or pickup; manual audited reconciliation" (PROPOSED_DEFAULT) while the register document says the owner updated D05 on 2026-10-13 and D16 carries the deposit scope. Trust the register document plus code (deposits exist).
- `docs/product/01-context-and-decisions.md` line 82 states "D01-D10 remain PROPOSED_DEFAULT" but the same file's table and `planning/decisions.json` mark D10 ACCEPTED; the sentence predates D10's approval. Trust the table.
- D01-D09 are still `PROPOSED_DEFAULT` although the system is built on them; no owner approval reference is recorded for them.
- No `SUPERSEDED` status or `superseded_by` field is used anywhere in `planning/decisions.json`, so supersession is found only in prose documents.
- The ADR text in `docs/engineering/01-architecture.md` mentions JPA; the backend uses JDBC (see [backend module guide](../04-codebase/backend-module-guide.md)).
