# Repository inventory and documentation gap assessment

> **Owner role:** Handbook navigation
> **Status:** REFERENCE — snapshot taken while writing the handbook (2026-10-19, `develop` at `d5d6ff5`); each finding names the file to fix
> **Language / اللغة:** English · [العربية](inventory-and-gaps-ar.md)

Method: the writers read source, migrations, contracts, configs and tests and compared them with the existing Markdown. Where documentation and code disagreed the code was trusted. Findings marked *handbook fixed* are already reflected correctly in the handbook; the *source document* still needs correcting in its own PR (DOC-001 applies to that PR). Nothing here was executed unless stated: no pnpm, Java, Docker or Maven exist on the authoring machine, so build/test commands are **NOT VERIFIED** (read from `package.json`, workflows and scripts).

## 1. Inventory

| Area | Actual content | Handbook page |
|---|---|---|
| Backend | Maven multi-module Java 21 / Spring Boot: `backend/modules/{availability,catalog,cooks,finance,identity,notifications,operations,orders,support}`, `backend/shared`, `backend/app` (assembly, Flyway V1–V31, 80 integration test files) | [backend-architecture](../03-architecture/backend-architecture.md), [backend-module-guide](../04-codebase/backend-module-guide.md) |
| Web | `apps/customer-web`, `apps/operations-web` (React/TypeScript/Vite), `packages/api-client`, `packages/design-tokens` | [frontend-architecture](../03-architecture/frontend-architecture.md) |
| Mobile | `apps/customer-mobile` (Expo/React Native; `e2e-native`, `e2e-ios`, scripts) | [mobile-architecture](../03-architecture/mobile-architecture.md) |
| Contracts | `contracts/openapi.yaml`, `contracts/endpoints.json` (API-nnn) | [api-contracts](../04-codebase/api-contracts.md) |
| Identity | Keycloak realm templates in `infra/identity`, preview realm and scripts in `deploy/web-preview/identity` | [identity-and-security](../03-architecture/identity-and-security.md) |
| Deployment | `infra/compose.yaml`, `deploy/web-preview/*` (Caddy, deploy.sh, admin-console, owner-e2e provisioning) | [infrastructure-and-deployment](../03-architecture/infrastructure-and-deployment.md) |
| CI/CD | `.github/workflows/`: `ci.yml`, `blueprint-integrity.yml`, `native-build.yml`, `deploy-preview.yml`, `preview-images.yml`, `owner-e2e-fixtures.yml`, `owner-e2e-provision.yml`, `doc001.yml` (new) | [ci-cd](../05-developer-workflow/ci-cd.md) |
| Planning | `planning/*.json` (backlog, rules, scenarios, decisions, gates) | [business-rules](../01-business/business-rules.md) |
| Quality | `docs/quality/`, 200-case `docs/quality/owner-acceptance/`, `verification/runs/` | [test-execution](../05-developer-workflow/test-execution.md) |

## 2. Conflicts between documentation and code (source documents still to correct)

| # | Finding | Where | Trusted |
|---|---|---|---|
| 1 | `docs/product/06-permissions.md` describes roles; the code has permission grants (`Permission.java`, 17 values), and Support/Ops/Finance/Admin are grant bundles. The doc names `finance.settlement_verify`; the code permission is `finance.settlement`. Kitchen owner = cook (`kitchen.owner_id` unique; no kitchen staff). | `docs/product/06-permissions.md` | code |
| 2 | `docs/product/04-state-machines.md` lets "cook/ops" dispatch and complete; in code only the owning cook dispatches/hands over, ops can exception-cancel from PREPARING/READY or override a handover. Cash states differ (`COLLECTION_REPORTED` vs `cash_obligation` DUE/REPORTED/VERIFIED/DISPUTED/NOT_DUE). | `docs/product/04-state-machines.md` | code |
| 3 | A PAUSED kitchen can still accept pending orders; SUSPENDED cannot. No code path sets a kitchen to CLOSED. | docs/product, `KitchenService` | code |
| 4 | BR-054 (rate limits for login, quote, order, search) is only partly implemented (`RATE_LIMITED` exists for phone-verification sends, handover attempts, review reports, uploads). Owner case CFG-039 agrees. | `planning/rules.json` | code |
| 5 | Every rule in `planning/rules.json` still has status PROPOSED_DEFAULT although decisions were confirmed. Deposits have no BR number (D16, MB-051..053). | `planning/rules.json` | code + decisions |
| 6 | Owner acceptance README says 199 cases (config-security 53); the catalog has 200 (54). | `docs/quality/owner-acceptance/README.md` | cases/*.json |
| 7 | `docs/operations/06-web-preview.md` ("prepared, not deployed") and `deploy/web-preview/README.md` ("Nothing here has been deployed") are stale: `deploy-preview.yml` deploys every push to `develop`. | those two files | workflow |
| 8 | Hosting provider: workflows say Contabo; `docs/operations/09-staging-hetzner-preparation.md`, `docs/operations/12-owner-server-setup.md`, `deploy/web-preview/README.md`, `compose.4gb.yaml` say Hetzner. CD deploys to `/srv/matbakhy-staging`; `.env.example` says `/srv/matbakhy-preview`. Provider **NOT VERIFIED**; the workflow is what runs. | docs/operations | workflow |
| 9 | Root `README.md` said "no product feature exists" (updated by this PR). Several docs call `implementation/mvp` the active branch; practice is `develop`; `native-build.yml` still filters pushes on `implementation/mvp`. | README, docs, workflow | git history |
| 10 | Node: repo pins 24.21.0 (`.node-version`, engine-strict); the authoring sandbox had Node 22 (unsupported). `toolchain.md` lists LOCAL/CI/STAGING/PRODUCTION, the enum in `MatbakhyProperties` also has PREVIEW; `application.yaml` requires `APP_ENV` (no default for `matbakhy.environment`). | `docs/engineering/toolchain.md` | code |
| 11 | `docs/security/mfa-policy.md` step 5 says mobile does not handle 403 STEP_UP_REQUIRED, but `AppContext.tsx`/`mobileApi.ts` show a step-up banner; it also cites an iOS beta device test plan under a mobile-docs directory, which does not exist. | mfa-policy.md | code |
| 12 | `apps/customer-mobile/src/auth.ts` comment says ephemeral browser session "on iOS"; code passes `preferEphemeralSession: true` on both platforms (Android behaviour NOT VERIFIED). | auth.ts | code |
| 13 | `matbakhy-realm.preview.json` has no `matbakhy-mobile` client (local template does). Mobile sign-in against the deployed preview is NOT VERIFIED. | `deploy/web-preview/identity` | file |
| 14 | `POST /api/v1/auth/logout` is a framework route that web apps call with a type cast; not in `endpoints.json`. | contracts | code |
| 15 | OrderController comment says API-028 (cancellation requests) arrives later; it is implemented. API-095 (audit export), ops dashboard metrics (API-066) and policies (API-008) are in the contract but not implemented. | code comment, contract | code |
| 16 | `packages/design-tokens/package.json` still says "draft pending O05" though the Cairo/terracotta direction is approved. | package.json | design docs |
| 17 | Intent tiles ("a meal for me/us", "complete my table", "prepare a gathering") are UI hints; `intent` is never sent to the server. No catering product exists. | UI | code |
| 18 | The delivery fee comes from `service_zone.delivery_fee_minor` with optional `kitchen_zone.fee_override_minor`. | docs | code |

| 19 | `contracts/endpoints.json` has `"status": "DESIGN_NOT_IMPLEMENTED"` and `planning/backlog.json` has `"status": "PLANNED_NOT_IMPLEMENTED"` at file level; the code serves 113 of 116 routes (not served: API-008, API-066, API-095) and most stories are DONE. | those two files | code |
| 20 | `docs/status/latest.md` is stale: newest entry 2026-10-14, story table says "Flyway V1–V20" (migrations reach V31), no D18/MFA, says "No preview deployed" while `docs/quality/owner-acceptance/run-2026-10-19.md` records a deployed release. | `docs/status/latest.md` | git history, run record |
| 21 | D18 (MFA) is implemented but has no TC-nnn scenario and no backlog story of its own; D05 still reads "cash only"; D01–D09 stay PROPOSED_DEFAULT without approval references; `SUPERSEDED` is never used; `docs/product/01-context-and-decisions.md` line 82 says D10 is proposed while its table says ACCEPTED. | `planning/decisions.json` and the decisions document | decisions JSON + code |
| 22 | ADR-001 in `docs/engineering/01-architecture.md` mentions JPA; the backend uses `JdbcTemplate` and has no JPA dependency. ADR-002 says to share translation keys; there are three separate `i18n.ts` files. | `docs/engineering/01-architecture.md` | code |
| 23 | `docs/quality/owner-acceptance/generated/traceability.json` reports no uncovered scenarios, but only 39 of 200 owner cases link to a planning scenario. | generated traceability | cases/*.json |
| 24 | Fakes (SMS, malware scan, notifications) are allowed in LOCAL, CI and PREVIEW (`ProviderConfiguration`, `MatbakhyProperties.DeploymentEnvironment`); some comments say "LOCAL/CI only". | code comments, docs | code |

## 3. Functional gaps seen while reading the code (not documentation defects)

No UI for the cook application form (API only); no UI for cash-report verification (API-082/083); the Finance tab is detected by probing `/ops/finance/statements` and treating 404 as allowed, so a staff account holding only `finance.verify` may not see the payments panel (derived from code, untested); Staff tab sends no `expiresAt`; mobile has no MFA settings screen and does not call device registration (API-033); browse requests limit 50 with no "load more"; platform refunds are full-only; no purge job for expired `idempotency_record` rows was found (NOT VERIFIED); `x-permission` in `contracts/openapi.yaml` is never compared with controller `@PreAuthorize`.

## 4. Areas with weak automated coverage

- Backend modules have no isolated tests: all 80 files are integration tests in `backend/app/src/test`; Spring configuration beyond `EnvironmentGuardTest` is untested.
- No test for application-level rate limits (BR-054), kitchen CLOSED, partial refunds, or the `ACCEPTANCE_WINDOW_TOO_SHORT` edge (NOT VERIFIED).
- Mobile unit tests cover App, auth, sign-in and ordering only; no MFA-enabled native run and no physical-device run exist. Native flows run only in CI.
- Operations web: ~17 screens share one `App.test.tsx` plus Playwright E2E; the customer-journey Playwright test lives in `apps/operations-web/e2e` but drives customer web; customer-web has no E2E of its own.
- Preview deploy scripts are covered by Python rehearsals, not by tests against the real host; proxy tests skip when the caddy/python images are absent.
- All 200 owner acceptance cases are NOT_RUN or BLOCKED except the few executed on 2026-10-19 (see `docs/quality/owner-acceptance/run-2026-10-19.md`).

## 5. NOT VERIFIED across the handbook

Every build/test command (not run here); Windows/macOS procedures; IDE debugging of the containerised API; live Keycloak realm state (MFA migration, `matbakhy-mobile` on the preview); alert channels (Alertmanager posts only to a local sink); off-host backups; a generic API rate limiter (none found); Mermaid rendering (no renderer available here; syntax hand-checked and re-checked in review).

## 6. Duplication

Business behaviour appears in `docs/product/*`, `planning/rules.json` and code. The handbook links to the canonical file ([documentation-map](documentation-map.md)) instead of copying, to avoid new drift.
