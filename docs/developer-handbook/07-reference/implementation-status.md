# Implementation status

> **Owner role:** Handbook navigation
> **Status:** PARTIALLY IMPLEMENTED — a feature-by-feature state table derived from code and tests, plus the conflicts found between the code and the planning/status documents
> **Language / اللغة:** English · [العربية](implementation-status-ar.md)

Labels follow the handbook convention (IMPLEMENTED, PARTIALLY IMPLEMENTED, PLANNED, DISABLED, DEPRECATED, NOT VERIFIED).
**IMPLEMENTED means code exists and an automated test exercising it was found in the repository.** It does not mean the
test passed in this session (the handbook writers did not run the suites) or that an independent reviewer accepted it, and
it never means "live": no production environment exists, live money is gated (D16), and only a synthetic PREVIEW
deployment has been used (see the conflicts below). Story state in `planning/backlog.json` and scenario state in
`planning/scenarios.json` are hints that were cross-checked, not trusted.

How the state was derived: controller exists and route is in `contracts/endpoints.json` with `implemented: true` (113 of 116
routes); service code read; test class found by scenario id or by area name in `backend/app/src/test/java/com/matbakhy/app/`,
`apps/*/src/*.test.tsx`, `packages/*/test/`; migration present. Test counts or pass results are **not** asserted here.

## Backend capabilities

| Capability | State | Evidence (code / test / story) |
|---|---|---|
| OIDC web login, PostgreSQL sessions, CSRF, CORS allowlist, logout | IMPLEMENTED | `SecurityConfiguration` webChain; `IdentityIntegrationTest`, `CsrfCookie*IntegrationTest`; MB-009 DONE |
| Native bearer JWT validation (issuer, audience) | IMPLEMENTED | `SecurityConfiguration` bearerChain; `IdentityIntegrationTest` |
| Account mapping, suspended-account denial, roles from server state only | IMPLEMENTED | `AccountMapper`, `AccountLifecycleService` |
| Profile, verified Egyptian phone (hashed single-use codes, limits), addresses | IMPLEMENTED | `ContactIntegrationTest`, TC-003..005; real SMS provider **PLANNED** (O07; only a fake sender exists) |
| Staff grants, last-admin and self-escalation protection, audit log | IMPLEMENTED | `StaffAccessIntegrationTest`, TC-006/007/043 |
| Configurable MFA policy (Keycloak performs MFA; DISABLED/OPTIONAL/REQUIRED per category), enforcement filter, admin reset, user preference | IMPLEMENTED | `MfaPolicyService`, `MfaEnforcementFilter`, V31; `MfaPolicyIntegrationTest`, `MfaPolicyResolveTest`, `MfaIdpTest`, `MfaResetIntegrationTest`, `MfaResetAuditIntegrationTest`; D18. Not a separate backlog story (see conflicts) |
| Cook applications, reviewer decision with step-up, kitchen creation, pause/suspend | IMPLEMENTED | `CookWorkflowIntegrationTest`, TC-008/009; MB-012 DONE |
| Media pipeline (signed upload, validation, scan, private documents, cleanup) | IMPLEMENTED with fakes | `MediaIntegrationTest`, `MediaCleanupIntegrationTest`; real malware scanner **PLANNED** (O07) |
| Meals, revisions, publication guards, moderation, price history | IMPLEMENTED | `MealIntegrationTest`, `ModerationIntegrationTest`, `PriceHistoryIntegrationTest`; MB-014, MB-048 |
| Slots and capacity (Cairo scheduling) | IMPLEMENTED | `SlotIntegrationTest`, TC-013..015 |
| Server-side discovery and zones | IMPLEMENTED | `DiscoveryIntegrationTest` |
| Quotes (server pricing, 5-minute policy TTL, one cook + one slot) | IMPLEMENTED | `QuoteIntegrationTest`; [pricing implementation](../04-codebase/pricing-implementation.md) |
| Stale-quote refusal and reconfirmation (D13) | IMPLEMENTED | `PriceChangeIntegrationTest` (TC-154..156); web and mobile notices |
| Atomic order placement, two-layer idempotency, snapshots, capacity reservation | IMPLEMENTED | `OrderPlacementIntegrationTest`, V29; [order implementation](../04-codebase/order-implementation.md) |
| Order lifecycle, handover codes, expiry job, ops exception cancel and handover override | IMPLEMENTED | `OrderLifecycleIntegrationTest`, TC-026..031 |
| Cash on receipt: cook report, finance verification, separation of duties | IMPLEMENTED | `CashAndLedgerIntegrationTest` |
| Ledger (balanced, append-only), refunds, settlements, statements | IMPLEMENTED | `CashAndLedgerIntegrationTest`, `FinanceOperationsIntegrationTest`; commission is 0 at launch (`ZeroCommissionIntegrationTest`, D10) |
| Platform commission and customer platform fee | DISABLED | Dormant capability: the database refuses a non-zero rate for non-synthetic policies (V23); D10 |
| Deposit payments (kitchen recipient approval, customer report, kitchen receipt, disputes, reconciliation, refund report) | IMPLEMENTED for synthetic/preview use | `DepositPaymentIntegrationTest`, `PaymentReconciliationIntegrationTest`; **live money gated** (D16, legal/provider review) |
| Automated payment gateway, bank verification, escrow, guarantee fund | PLANNED (future research only) | `docs/strategy/mvp-direct-deposit-payment.md` marks them deferred |
| In-app notification inbox, outbox dispatcher with leases/retries/dead letters | IMPLEMENTED | `NotificationIntegrationTest` |
| External push delivery (FCM/APNs) | DISABLED | `matbakhy.notifications.push-enabled` defaults to false; no real adapter exists (O07) |
| External email delivery | PLANNED | only `LoggingNotificationChannels` exists (sends nothing) |
| Support cases, staff replies, private notes, cancellation requests, automatic payment case | IMPLEMENTED | `SupportIntegrationTest`, V17, V28 |
| Reviews, review reports and moderation; favorites | IMPLEMENTED | `ReviewsAndFavoritesIntegrationTest` |
| Account deletion request and personal-data erasure | IMPLEMENTED with a proposed retention default | `AccountErasureIntegrationTest`; 14-day grace is a PROPOSED value (O06 open) |
| Organic activity counters and cook insights (no ranking, campaigns or billing) | IMPLEMENTED | `ActivityInsightsIntegrationTest`, D17 |
| Commerce configuration versions, feature flags, ordering switch | IMPLEMENTED | `ConfigurationIntegrationTest`, `PublicConfigurationIntegrationTest` |
| Audit read API | IMPLEMENTED | `AuditController`; export (API-095) **PLANNED** |
| Ops dashboard (API-066), policy documents (API-008) | PLANNED | routes registered with `implemented: false`, no controller |
| Observability: request ids, redaction, management-port metrics, runtime DB role | IMPLEMENTED locally | `OperationalMetricsIntegrationTest`, `ManagementPortIntegrationTest`, `LogRedactionIntegrationTest`; deployed readiness (AUD-01/LOG-01/OPS-01..03) NOT VERIFIED |
| Backup/restore tooling | PARTIALLY IMPLEMENTED | `scripts/db-backup.sh`, `scripts/db-restore.sh`, `BackupRestoreIntegrationTest`; TC-057 is still NOT_RUN in `planning/scenarios.json` (needs staging) |
| Rate limiting | PARTIALLY IMPLEMENTED | Per-feature limits exist (phone codes, handover attempts, review reports, upload tickets: `ErrorCode.RATE_LIMITED` uses); no general limiter for quote/order/search was found; TC-054 NOT_RUN. Keycloak lockout covers login |
| Performance budgets | NOT VERIFIED | TC-059 NOT_RUN (MB-038 TODO) |
| Social feed, follows, comments | PLANNED | D07 deferral, O10 open; no code |
| Portion-size filtering | PLANNED/deferred | D11 — controls intentionally hidden (`apps/customer-web/src/Browse.tsx:93`) |
| AI Concierge, sponsored discovery, growth billing | PLANNED (research) | `docs/strategy/free-core-sponsored-discovery.md`; no code |

## Applications

| Application | State | Evidence |
|---|---|---|
| Customer web (browse, basket, checkout, payment status, orders, account, security) | IMPLEMENTED | `apps/customer-web/src/App.test.tsx`; Playwright `apps/operations-web/e2e/customer-journey.e2e.ts`; MB-027/028 DONE |
| Cook workspace (dashboard, orders, meals, slots, payments, statement, insights, settings, onboarding) | IMPLEMENTED | `apps/operations-web/e2e/cook-workspace.e2e.ts`; MB-022 DONE |
| Staff backoffice (reviews, catalogue, cases, orders, finance, payments, configuration, staff, security) | IMPLEMENTED | `apps/operations-web/src/App.test.tsx`; MB-023..026 DONE |
| Accessibility and responsive verification | PARTIALLY IMPLEMENTED | keyboard/RTL tests exist; screen-reader testing not done: TC-047 BLOCKED, MB-029 BLOCKED (BLK-003); MB-030 (automated complete journeys) TODO |
| Customer mobile: sign-in (PKCE, secure store), discovery, cart, ordering, deposit checkout, notifications inbox, support | IMPLEMENTED on emulator/simulator | `apps/customer-mobile/src/*.test.ts(x)`; native runs recorded under `verification/runs/`; MB-031/032 DONE |
| Mobile lifecycle push, deep-link push routing, dynamic text and screen readers | PARTIALLY IMPLEMENTED | MB-033 IN_PROGRESS; push disabled (O07) |
| Android and iOS release candidates, store submission | PLANNED | MB-034, MB-035, MB-045 TODO; TC-051/052/063 NOT_RUN |
| Physical-device verification | NOT VERIFIED | BLK-004 in `docs/status/latest.md` |

## Environments and process

| Item | State | Evidence |
|---|---|---|
| Local stack (`scripts/dev.sh`, `infra/compose.yaml`) | IMPLEMENTED | MB-008 DONE |
| Preview (synthetic data, `APP_ENV=PREVIEW`) | IMPLEMENTED (deployed once; current state NOT VERIFIED) | `deploy/web-preview/`; `docs/quality/owner-acceptance/run-2026-10-19.md` records fixtures applied on a preview release |
| Owner acceptance suite (200 cases) | NOT VERIFIED | `docs/quality/owner-acceptance/generated/traceability.json`: 192 NOT_RUN, 8 BLOCKED |
| Staging and production environments | PLANNED | MB-040, MB-044 TODO; `APP_ENV=STAGING` refuses to start without real providers |
| Evidence gates (release candidate) | PLANNED | MB-043 TODO |

## Conflicts found between documents and code

| # | Document says | Code or other source says | Trusted |
|---|---|---|---|
| 1 | `contracts/endpoints.json` `"status": "DESIGN_NOT_IMPLEMENTED"` | 113 of 116 routes `implemented: true` and served (`ContractRoutesTest`) | code |
| 2 | `planning/backlog.json` file-level `"status": "PLANNED_NOT_IMPLEMENTED"` | stories MB-001..MB-028, 031, 032, 037, 048..054 are DONE | per-story status plus code |
| 3 | `docs/status/latest.md` story table: "Flyway V1-V20" and newest entry dated 2026-10-14 | migrations go to `V31__mfa_policy.sql`; commits through 2026-10-19 | code; latest.md is stale for MFA (it never mentions D18) and for the owner E2E work |
| 4 | `docs/status/latest.md` (2026-10-14): "No preview deployed" | `docs/quality/owner-acceptance/run-2026-10-19.md`: a preview release was deployed and E2E fixtures applied | the run record |
| 5 | D18 (configurable MFA) is an accepted decision with a policy document | no story in `planning/backlog.json` carries D18 as its subject (MB-009/012/026/036/052 only mention it) | code exists; traceability by backlog story is missing |
| 6 | TC-057 and TC-139 are NOT_RUN in `planning/scenarios.json` | `BackupRestoreIntegrationTest` (TC-057) and `LogRedactionIntegrationTest` (TC-056) exist; TC-139 groups MB-039 tooling still in progress | scenario status is conservative: partial coverage exists |
| 7 | `docs/engineering/01-architecture.md` ADR-001 mentions JPA | no JPA dependency; `JdbcTemplate` everywhere | code |
| 8 | ADR-002: share "translation keys" | three separate `i18n.ts` files | code |
| 9 | `planning/decisions.json` D05 "Cash at delivery or pickup" | register document + D16 + code: deposits exist | register + code |
| 10 | `docs/product/01-context-and-decisions.md` line 82: "D01-D10 remain PROPOSED_DEFAULT" | same file's table and `planning/decisions.json`: D10 ACCEPTED | the table and JSON |
| 11 | `docs/quality/owner-acceptance/generated/traceability.json` `scenarios_uncovered: []` | only 39 of 200 owner cases list scenario ids in `case_to_scenarios`; the rest have `[]` | counting is by scenario, not by case; do not read it as "every case is covered" |
| 12 | `deploy/web-preview/identity/matbakhy-realm.preview.json` (clients `matbakhy-web`, `matbakhy-admin`) | local realm also defines `matbakhy-mobile`; `apps/customer-mobile/src/config.ts` defaults the client id to `matbakhy-mobile` | NOT VERIFIED how native builds authenticate to the preview |

## Where to confirm current state yourself

`python3 scripts/next_task.py` (eligible work), `python3 scripts/verify.py --phase Pxx`, the backend suite through Maven, `pnpm test`,
`pnpm contract:check`; see [test execution](../05-developer-workflow/test-execution.md). Re-check this page whenever a story changes state
(rule: [documentation maintenance](../05-developer-workflow/documentation-maintenance.md)).
